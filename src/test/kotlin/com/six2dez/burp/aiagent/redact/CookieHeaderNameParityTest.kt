package com.six2dez.burp.aiagent.redact

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

// (PRIV-05) Phase 27 plan 27-02. WHAT THIS FILE IS FOR: the v0.10.0 milestone audit found the
// prompt path (Redaction.apply's two cookie regexes) and the MCP tool-RESULT path
// (McpToolHelpers.sanitizeHeaders) disagreeing about what a cookie header is CALLED — the prompt
// path matched name-contains-"cookie" while the tool-result path compared against the two canonical
// spellings — and nothing in the repository was able to notice. Plan 27-01 made the two paths agree
// by promoting the rule to Redaction.isCookieHeaderName. This file is the mechanism that keeps them
// agreeing: it asserts, behaviourally, that every header name the prompt path strips is a name the
// shared predicate also matches, so a future one-sided widening fails a test instead of shipping.
//
// DIRECTION, stated once so it cannot be misread. The invariant is
//   promptStrips(N)  =>  isCookieHeaderName(N)
// and ONLY that direction. The converse is FALSE BY DESIGN and must never be added here: my_cookie
// is in the corpus precisely because the predicate matches it and the prompt path does not
// (COOKIE_NAME_PART is [A-Za-z0-9-]*, which excludes '_'). The predicate being WIDER than the two
// regexes is fail-safe — wider on the redacting side over-redacts a benign value at worst — and
// asserting the converse would create pressure to NARROW the predicate, which is the exact
// direction that reopens this phase's gap.
//
// WHICH GUARD COVERS WHICH MUTATION (measured by plan 27-02's red probes 1 and 2, not assumed):
//   - a NARROWING of Redaction.isCookieHeaderName turns THIS test red (probe 1);
//   - a NARROWING of either prompt-path regex leaves this test GREEN — shrinking an implication's
//     antecedent cannot falsify it — and is caught instead by
//     RedactionTest.cookieHeaderNameVariantsAreStripped (probe 2).
//
// FIXTURE REACHABILITY, following the same discipline as RedactionTest's comment block above
// cookieHeaderNameVariantsAreStripped. Every sentinel is a bare lowercase alphabetic word on an
// unquoted header line: no '=' (formBodyParamRegex, urlTokenParamRegex and cookieTypedParamRegex all
// need one), no quotes (jsonSecretKeyRegex needs them), no Bearer/Basic prefix and no dotted eyJ
// segment (bearerRegex, basicAuthRegex, jwtRegex), and no "=== COOKIES ===" header
// (redactCookieSections never runs). SENSITIVE_KEY_EXPR is only ever consulted as a KEY immediately
// followed by '=' or inside quotes, so it cannot reach a sentinel here either. A cookie header rule
// is therefore the only thing in the pipeline that can remove one.
//
// FOUR HEADER NAMES ARE DELIBERATELY EXCLUDED FROM THE CORPUS, each because a DIFFERENT redaction
// rule claims it — including them would make the implication fail for a reason that has nothing to
// do with cookies:
//   - Authorization        — claimed by Redaction.authHeaderRegex (its first alternative).
//   - Proxy-Authorization  — claimed by Redaction.authHeaderRegex (its second alternative).
//   - X-API-Key            — claimed by Redaction.authHeaderRegex (its x-api-key alternative).
//   - Host                 — claimed by Redaction.hostHeaderRegex, which rewrites the value under a
//                            host-anonymising policy.
// For each of the four, promptStrips(N) would be true while isCookieHeaderName(N) is false.
//
// This file uses NO reflection and does not widen the visibility of cookieHeaderRegex or
// setCookieHeaderRegex. Those stay private, and the assertion goes through Redaction.apply's
// observable output — the behaviour that actually ships.

// The parity corpus: header name -> its own unique sentinel value. Both polarities are present, and
// parityCorpusIsNonEmptyAndContainsBothPolarities asserts that fact so the two tests below cannot
// pass vacuously over an empty or one-sided list.
private val PARITY_CORPUS: List<Pair<String, String>> =
    listOf(
        // cookie-positive: predicate true AND the prompt path strips the value
        "Cookie" to "sentinelparitycookiebare",
        "Set-Cookie" to "sentinelparitysetcookiebare",
        "COOKIE" to "sentinelparitycookieupper",
        "set-cookie" to "sentinelparitysetcookielower",
        "Cookie2" to "sentinelparitycookietwo",
        "X-Cookie" to "sentinelparityxcookie",
        "Set-Cookie2" to "sentinelparitysetcookietwo",
        "X-Original-Cookie" to "sentinelparityoriginalcookie",
        "X-Forwarded-Cookie" to "sentinelparityforwardedcookie",
        "Cookie-Consent" to "sentinelparitycookieconsent",
        "X-Cookie-Policy" to "sentinelparitycookiepolicy",
        // predicate-only, the deliberate asymmetry: '_' is outside COOKIE_NAME_PART, so neither
        // prompt-path regex can match this name, while the predicate's bare contains() does.
        "my_cookie" to "sentinelparityunderscorecookie",
        // cookie-negative: predicate false AND the prompt path leaves the value alone
        "X-Request-Id" to "sentinelparityrequestid",
        "X-Cook" to "sentinelparityxcook",
        "Cook-ie" to "sentinelparitycookiehyphen",
        "Accept" to "sentinelparityaccept",
        "Content-Type" to "sentinelparitycontenttype",
    )

private const val MIN_CORPUS_SIZE = 16
private const val MIN_PREDICATE_POSITIVES = 10
private const val MIN_PREDICATE_NEGATIVES = 4
private const val PARITY_HOST_SALT = "phase-27-plan-02-parity-salt"

class CookieHeaderNameParityTest {
    @BeforeEach
    fun clearCustomPatterns() {
        // Redaction is a singleton object: a custom pattern left behind by another test class in the
        // same JVM could remove a sentinel for the wrong reason, letting promptStrips report true
        // without any cookie rule having run. Precedent: PassiveAiScannerPromptRedactionTest.
        Redaction.setCustomPatterns(emptyList())
    }

    @AfterEach
    fun clearCustomPatternsAfterEach() {
        // The @BeforeEach only protects THIS class; clearing on the way out as well keeps this file
        // from becoming the source of the same bleed it defends against.
        clearCustomPatterns()
    }

    // Runs as its own test so the two below cannot pass over an empty or one-sided corpus — a
    // forEach over an empty list is a passing test about nothing (Phase 23 recorded exactly that
    // failure class). Red probe 3 of plan 27-02 (isCookieHeaderName widened to return true
    // unconditionally) turns the negatives clause below red, which is what proves this guard is
    // load-bearing rather than decorative.
    @Test
    fun parityCorpusIsNonEmptyAndContainsBothPolarities() {
        val names = PARITY_CORPUS.map { it.first }
        val sentinels = PARITY_CORPUS.map { it.second }

        assertTrue(
            names.size >= MIN_CORPUS_SIZE,
            "the parity corpus must hold at least $MIN_CORPUS_SIZE names (got ${names.size})",
        )
        assertTrue(
            names.size == names.toSet().size,
            "corpus header names must be distinct (got: $names)",
        )
        assertTrue(
            sentinels.size == sentinels.toSet().size,
            "each corpus name needs its OWN sentinel, or an absence assertion can be satisfied by another name's rule",
        )

        val positives = names.filter { Redaction.isCookieHeaderName(it) }
        val negatives = names.filterNot { Redaction.isCookieHeaderName(it) }

        assertTrue(
            positives.size >= MIN_PREDICATE_POSITIVES,
            "at least $MIN_PREDICATE_POSITIVES corpus names must satisfy the predicate (got ${positives.size}: $positives)",
        )
        assertTrue(
            negatives.size >= MIN_PREDICATE_NEGATIVES,
            "at least $MIN_PREDICATE_NEGATIVES corpus names must NOT satisfy the predicate — a predicate " +
                "widened to always-true makes the parity implication trivially true " +
                "(got ${negatives.size}: $negatives)",
        )
    }

    // THE INVARIANT. For each corpus name N under STRICT and BALANCED: if the prompt path removed
    // N's value, the shared predicate must also claim N. A widening applied to the prompt path and
    // forgotten in the predicate turns this red, naming the offending header.
    @Test
    fun everyNameThePromptPathStripsIsMatchedByTheSharedPredicate() {
        for (mode in listOf(PrivacyMode.STRICT, PrivacyMode.BALANCED)) {
            for ((name, sentinel) in PARITY_CORPUS) {
                val output = redactHeaderBlob(mode, name, sentinel)
                val promptStrips = !output.contains(sentinel)
                val predicate = Redaction.isCookieHeaderName(name)

                if (promptStrips) {
                    assertTrue(
                        predicate,
                        "$mode: the prompt path strips '$name' but Redaction.isCookieHeaderName('$name') is " +
                            "false. The two redaction paths have drifted apart again: whatever widened the " +
                            "prompt-path cookie rule was not mirrored into the shared predicate, so the MCP " +
                            "tool-result path will emit this header's value verbatim (output: $output)",
                    )
                    // T-21-WA2: the prompt path keeps the header's OWN name rather than renaming it
                    // to the canonical spelling. A fixed replacement string would rewrite
                    // "X-Cookie: v" into "Cookie: [STRIPPED]" and silently relabel traffic.
                    assertTrue(
                        output.contains("$name: [STRIPPED]"),
                        "$mode: '$name' must keep its own name when stripped, not be renamed (output: $output)",
                    )
                }
            }
        }
    }

    // (PRIV-05) plan 27-10. The underscore name class, asserted in the SAFE direction. Before this
    // plan COOKIE_NAME_PART was [A-Za-z0-9-]*, which excludes '_', and this same fixture asserted
    // that my_cookie's value SURVIVED STRICT — a green test pinning a cookie disclosure. The
    // assertion is INVERTED rather than deleted, so the corpus entry keeps carrying its measurement.
    @Test
    fun theUnderscoreNameClassIsStrippedByBothTheRegexesAndThePredicate() {
        val name = "my_cookie"
        val sentinel = PARITY_CORPUS.first { it.first == name }.second

        assertTrue(
            Redaction.isCookieHeaderName(name),
            "the shared predicate is a bare contains() and must match '$name'",
        )

        for (mode in listOf(PrivacyMode.STRICT, PrivacyMode.BALANCED)) {
            val output = redactHeaderBlob(mode, name, sentinel)
            assertFalse(
                output.contains(sentinel),
                "$mode: the value of '$name' SURVIVED a redacting policy. '_' is a legal RFC 9110 tchar, so " +
                    "this is a real header name, and PassiveAiScannerFilters.sanitizeHeadersForPrompt ADMITS it " +
                    "into the outbound passive-scan prompt PRECISELY BECAUSE the shared predicate claims it. " +
                    "A value surviving here is therefore a cookie disclosure to a third-party AI backend, not " +
                    "an intended asymmetry. FIX THE DIRECTION THAT IS SAFE: widen COOKIE_NAME_PART so the two " +
                    "regexes reach every name the predicate admits. NEVER narrow the predicate to restore " +
                    "symmetry — that shrinks what McpToolHelpers.sanitizeHeaders strips on the MCP path, which " +
                    "is the direction that reopened this phase (output: $output)",
            )
            assertTrue(
                output.contains("$name: [STRIPPED]"),
                "$mode: '$name' must be rewritten to the stripped form and must keep its OWN name rather than " +
                    "be renamed to the canonical spelling (output: $output)",
            )
        }
    }

    // One corpus header line per invocation, so a sentinel can only be removed by a rule that claims
    // THAT name. The Host line is fixture scaffolding, not a corpus entry — see the exclusion list in
    // this file's header for why Host is not measured here.
    private fun redactHeaderBlob(
        mode: PrivacyMode,
        name: String,
        sentinel: String,
    ): String {
        val blob = "GET / HTTP/1.1\nHost: example.com\n$name: $sentinel\n"
        return Redaction.apply(blob, RedactionPolicy.fromMode(mode), stableHostSalt = PARITY_HOST_SALT)
    }
}
