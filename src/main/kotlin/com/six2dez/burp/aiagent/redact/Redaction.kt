package com.six2dez.burp.aiagent.redact

import com.six2dez.burp.aiagent.config.Defaults
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

data class RedactionPolicy(
    val stripCookies: Boolean,
    val redactTokens: Boolean,
    val anonymizeHosts: Boolean,
) {
    companion object {
        fun default() =
            RedactionPolicy(
                stripCookies = true,
                redactTokens = true,
                anonymizeHosts = true,
            )

        fun fromMode(mode: PrivacyMode): RedactionPolicy =
            when (mode) {
                PrivacyMode.STRICT ->
                    RedactionPolicy(
                        stripCookies = true,
                        redactTokens = true,
                        anonymizeHosts = true,
                    )
                PrivacyMode.BALANCED ->
                    RedactionPolicy(
                        stripCookies = true,
                        redactTokens = true,
                        anonymizeHosts = false,
                    )
                PrivacyMode.OFF ->
                    RedactionPolicy(
                        stripCookies = false,
                        redactTokens = false,
                        anonymizeHosts = false,
                    )
            }
    }
}

enum class PrivacyMode {
    STRICT,
    BALANCED,
    OFF,
    ;

    companion object {
        fun fromString(raw: String?): PrivacyMode = entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: BALANCED
    }
}

object Redaction {
    private val authHeaderRegex =
        Regex(
            "(?im)^(" +
                "authorization|proxy-authorization|" +
                "x-api-key|api-key|x-api-secret|api-secret|x-client-secret|" +
                "x-auth-token|auth-token|x-access-token|access-token|" +
                "x-session-token|session-token|x-csrf-token|csrf-token|x-xsrf-token" +
                "):\\s*.+$",
        )

    // The trailing =* captures base64/base64url padding on the token. The token char-class
    // excludes '=', so =* greedily consumes ANY run of '=' immediately after the token — including
    // '=' that merely follow the credential (WR-04). This is intentional and fail-safe: it
    // over-redacts trailing '=' rather than risk leaking part of a padded token. The whole
    // Authorization header is already replaced by authHeaderRegex; bearerRegex additionally covers
    // bearer tokens embedded in bodies/JSON/free text, where a few trailing '=' being swallowed is
    // a benign over-redaction, not a leak.
    private val bearerRegex = Regex("(?i)bearer\\s+[A-Za-z0-9\\-\\._~\\+\\/]+=*")
    private val basicAuthRegex = Regex("(?i)basic\\s+[A-Za-z0-9\\+\\/=]+")
    private val cookieHeaderRegex = Regex("(?im)^cookie:\\s*.+$")
    private val setCookieHeaderRegex = Regex("(?im)^set-cookie:\\s*.+$")

    /**
     * The passive scanner's dedicated cookie-section header.
     *
     * Public, and owned HERE in the redactor rather than in the emitter, because
     * scanner/PassiveAiScannerPrompts.kt imports this constant instead of writing the literal
     * inline. A future rename of the section is then a COMPILE ERROR rather than the silent
     * disabling of a security control — which is the standing objection to any section-scoped
     * redaction rule, converted into a compile-time coupling. The parity half of that coupling is
     * PassiveAiScannerPromptRedactionTest.emittedBlobContainsTheSectionConstant_parity, which turns
     * a silent format change AROUND the constant into a test failure.
     */
    const val COOKIE_SECTION_HEADER = "=== COOKIES ==="

    // (PRIV-05) SC1 / D-09: one name=value pair inside a cookie section. [^=\r\n]+ stops at the
    // FIRST '=', so a value that itself contains '=' (base64 padding) is consumed whole by the
    // trailing .* and cannot survive as a fragment.
    private val cookieSectionPairRegex = Regex("(?m)^([^=\\r\\n]+)=(.*)$")

    // (PRIV-05) SC1: the start of the NEXT prompt section. One of the three span terminators used
    // to bound a cookie section; the other two are a blank line and end-of-text.
    private val nextSectionRegex = Regex("(?m)^=== ")

    // (PRIV-05) SC1 / D-09 / D-10: redacts the VALUE of every name=value pair inside EVERY
    // [COOKIE_SECTION_HEADER] span, preserving the names. The scanner splits the Cookie: header on
    // ';' into a dedicated section, dropping the prefix the two header rules above key on — that is
    // the PRIV-05 leak, and it is a pattern-reach problem, not a call-site problem.
    //
    // D-09 — EVERY value, not only sensitive-named ones. Cookies are near-universally
    // session-bearing, and name-based selectivity is precisely what produced PRIV-05. Names are
    // preserved deliberately: COOKIES_MAX_COUNT bounds the section to six lines, so this costs the
    // model at most six opaque values while keeping all six names, which is the analytically useful
    // part (ScanKnowledgeBase.recordAuthInfo already records authCookieNames separately for exactly
    // this reason). Full-line stripping in the style of the two header rules above would destroy
    // the names and is therefore wrong here.
    //
    // D-10 — EVERY occurrence of the header, never only the first. This is a SECURITY requirement
    // with a demonstrated exploit behind it, not a robustness nicety. ScanKnowledgeBase.recordTechStack
    // populates the technology list from the response's Server, X-Powered-By, X-AspNet-Version and
    // X-Generator headers — all attacker-controlled — and buildContextSummary emits that list into
    // the prior-knowledge block, which the emitter appends BEFORE the cookie section. A Server
    // header carrying the section header therefore plants a decoy section ahead of the real one,
    // and a first-occurrence-only indexOf was measured redacting the decoy while the genuine
    // JSESSIONID and abtest_bucket values leaked intact. The while loop below is that fix;
    // RedactionTest.cookieSectionDecoyDoesNotShieldRealSection is its named regression guard.
    //
    // ACCEPTED RESIDUAL: an attacker who injects the section header into a response header causes
    // EXTRA redaction of their own response content. That is an over-redaction nuisance, never a
    // leak, and it is strictly better than the alternative.
    private fun redactCookieSections(text: String): String {
        var out = text
        var from = 0
        while (true) {
            val h = out.indexOf(COOKIE_SECTION_HEADER, from)
            if (h < 0) return out
            val bodyStart = h + COOKIE_SECTION_HEADER.length
            var end = out.length
            val blankLine = out.indexOf("\n\n", bodyStart)
            if (blankLine >= 0) end = minOf(end, blankLine)
            val nextSection = nextSectionRegex.find(out, bodyStart)
            if (nextSection != null) end = minOf(end, nextSection.range.first)
            val section = out.substring(bodyStart, end)
            out =
                out.substring(0, bodyStart) +
                section.replace(cookieSectionPairRegex) { "${it.groupValues[1]}=[REDACTED]" } +
                out.substring(end)
            // Advance past this header only: bodyStart is strictly greater than h, so the loop
            // always terminates, and the prefix rewritten above keeps every index below it valid.
            from = bodyStart
        }
    }

    // (PRIV-05) SC2 / D-09: a COOKIE-typed parameter line — the second entry point of the same
    // leak, reached through request.parameters(). Parameters are emitted as "name=value (TYPE)"
    // where TYPE is the Montoya HttpParameterType name (formatParamLine in
    // scanner/PassiveAiScannerPrompts.kt), so keying on the semantic type label rather than on a
    // section header makes this rule CONTEXT-FREE: it survives a section rename and works wherever
    // the shape appears. The asymmetry with the section rule above is deliberate — that section has
    // no discriminator other than its header, whereas a parameter line carries one in its own text.
    //
    // The context-free "^name=value$" alternative was rejected on CAPABILITY, not merely on
    // over-redaction: the trailing type suffix defeats the end anchor, so it cannot satisfy SC2 at
    // all — and it additionally mangles x=1, DEBUG=true and AAAA== when they stand on their own
    // line. Group 3 is the suffix and is written back verbatim, so name and type label both survive.
    private val cookieTypedParamRegex = Regex("(?im)^([^=\\r\\n]+)=(.*?)(\\s\\(COOKIE\\))\\s*$")

    // very generic JWT-like pattern (not perfect by design)
    private val jwtRegex = Regex("\\beyJ[A-Za-z0-9_\\-]+\\.[A-Za-z0-9_\\-]+\\.[A-Za-z0-9_\\-]+\\b")

    // Sensitive parameter/key name vocabulary — shared by urlTokenParamRegex, formBodyParamRegex,
    // and jsonSecretKeyRegex so query-string and body coverage stay consistent (PRIV-02).
    // (PRIV-05) SC3 / D-11: the vocabulary is byte-identical to the v0.6.0 value and is
    // deliberately NOT widened. The 31-key SC3 must-redact corpus is satisfied by the boundary
    // rule below plus the vendor list, without adding a single word; every extra word multiplies
    // the false-positive surface across all THREE consumer regexes at once (Pitfall 10). Add a
    // word only with measured evidence in query-string, form-body and JSON contexts.
    private const val SENSITIVE_WORDS =
        "access_token|api_key|apikey|auth|token|key|secret|password|pwd|session|sid|code"

    // (PRIV-05) SC3 / D-11: vendor and framework session-cookie names that NO morphological rule
    // can catch, because the sensitive word is concatenated without a separator (JSESSIONID,
    // PHPSESSID, csrftoken) or is absent from the name entirely (remember_me). Whole-key match;
    // case-insensitive via each consumer's leading (?i). Dots are escaped.
    private const val KNOWN_SESSION_KEYS =
        "jsessionid|phpsessid|asp\\.net_sessionid|\\.aspxauth|aspxauth|csrftoken|" +
            "remember_me|remember_token|laravel_session|ci_session|_session_id|sessionid|sessid|" +
            "cfid|cftoken|xsrf-token|_csrf"

    // (PRIV-05) SC3 / D-11: the character class a key name may be built from. '[' and ']' cover
    // PHP array parameters of the a[b] form; '.' covers connect.sid and ASP.NET_SessionId.
    private const val KEY_CHARS = "[A-Za-z0-9_.\\-\\[\\]]"

    // (PRIV-05) SC3 / D-13: the token-boundary constructs placed immediately before and after the
    // matched word. First alternative = the separator rule ('_', '-', '.', '[', ']' and the string
    // boundaries delimit a whole token). Second alternative = the camelCase rule, so authToken,
    // accessToken and userSessionId match.
    // NON-OBVIOUS BLOCKER: the camelCase half CANNOT be written inline under the consumers' (?i) —
    // case-insensitivity makes [A-Z] match lowercase too, so every position would qualify and the
    // boundary would degrade to a substring match. Java's inline flag-off group (?-i:...) is what
    // makes it work; verified on JDK 21.
    // REVERT POINT (one deletion each): removing the (?-i:...) alternative from WORD_BEFORE and
    // WORD_AFTER reverts D-13 entirely. That loses nothing SC3 requires and removes the three
    // accepted over-redactions recorded below (codeName, keyName, tokenCount).
    private const val WORD_BEFORE = "(?:(?<![A-Za-z0-9])|(?-i:(?<=[a-z0-9])(?=[A-Z])))"
    private const val WORD_AFTER = "(?:(?![A-Za-z0-9])|(?-i:(?<=[a-z0-9])(?=[A-Z])))"

    // (PRIV-05) SC3 / D-11: the shared sensitive-KEY expression consumed by urlTokenParamRegex,
    // formBodyParamRegex and jsonSecretKeyRegex. It replaces the old exact-word alternation, which
    // demanded the key be EXACTLY one of the twelve words — that is why auth_token missed (auth was
    // followed by '_', not '='), and why only a cookie literally named "session" was ever caught.
    //
    // (a) A key is sensitive when it IS a known vendor name, or when it CONTAINS one of the words
    //     as a whole token, treating '_', '-', '.', '[', ']', the string boundaries and
    //     lower-to-upper case transitions as delimiters. So auth_token, api-key, X-Session-Id and
    //     connect.sid match, while keyboard_layout, codename, sidebar and keychain do not.
    // (b) Prefix and suffix are BOUNDED at 64 characters: an unbounded '*' measured 2.3x slower on
    //     adversarial input (21 ms vs 9 ms on 1 MB) and reintroduces exactly the unbounded-
    //     quantifier adjacency this file already warns about above formBodyParamRegex.
    // (c) EVERY internal group is non-capturing, so the group numbering of all three consumers is
    //     unchanged and their existing replacement expressions still reproduce the key exactly
    //     (Pitfall 7).
    //
    // D-12: there is deliberately NO benign-key denylist. The recommendation proposed one;
    // measurement found nothing to guard against — all 21 must-not-redact keys pass on the
    // boundary rule alone. Do not add one: every entry in such a list is a place where a real
    // credential could be accidentally allowlisted.
    //
    // Two deliberate residuals:
    //   - PLURAL forms (codes, tokens, keys) are NOT handled. Adding 's?' to the vocabulary would
    //     catch them at the cost of a second widening axis; SC3 does not require it.
    //   - ACCEPTED over-redactions, all fail-safe and all analytically low-value: under the
    //     separator rule token_bucket_size, session_timeout_seconds, auth_provider, key_size,
    //     code_version, secret_santa, password_hint_enabled; under the camelCase rule (D-13)
    //     codeName, keyName, tokenCount. auth_provider is the only one with real analytic value
    //     and redacting it is still the correct default for a key literally named auth_*.
    private const val SENSITIVE_KEY_EXPR =
        "(?:(?:$KNOWN_SESSION_KEYS)|" +
            "$KEY_CHARS{0,64}$WORD_BEFORE(?:$SENSITIVE_WORDS)$WORD_AFTER$KEY_CHARS{0,64})"

    // Tokens/secrets in URL query strings, e.g. ?access_token=xyz or &api_key=xyz
    // (PRIV-05) SC3: the key side is the shared key expression, so compound and vendor keys
    // (auth_token, api-key, X-Session-Id, JSESSIONID) are caught here too. Group 1 is still the
    // whole "[?&]key=" prefix, so the "$1[REDACTED]" replacement in apply() is unchanged.
    private val urlTokenParamRegex =
        Regex(
            "(?i)([?&](?:$SENSITIVE_KEY_EXPR)=)[^&\\s\"'<>]+",
        )

    // (PRIV-02) x-www-form-urlencoded field ANYWHERE in a body, INCLUDING the leading field.
    // The (^|[?&]) anchor closes the documented gap: the old [?&]-only urlTokenParamRegex
    // missed "apikey=sk-abc123&user=bob" (no leading ? or &). (?im) = multiline+case-insensitive.
    // The value charclass [^&\s"'<>]+ is bounded — no trailing anchor that would backtrack (Pitfall 3).
    // (PRIV-05) SC3: group 1 is still the ^/?/& prefix and group 2 is still the WHOLE key, so the
    // "$1$2=[REDACTED]" lambda in apply() reproduces compound keys such as auth_token exactly.
    private val formBodyParamRegex =
        Regex(
            "(?im)(^|[?&])($SENSITIVE_KEY_EXPR)=[^&\\s\"'<>]+",
        )

    // (PRIV-02) JSON values for known-sensitive key names.
    // Key-scoped: only a value following a matching key name is redacted; "name":"alice" is untouched.
    // The value side (group 2) covers a quoted string OR an unquoted JSON scalar (boolean, null, or
    // number) so numeric/boolean secrets such as {"token":12345,"pin":123456} are not missed (WR-03).
    // Whatever the original value type, it is replaced with the quoted token "[REDACTED]", which keeps
    // the output valid JSON.
    // Limitation: a value containing an escaped quote (e.g. "token":"ab\"cd") will be partially
    // matched (stops at the backslash). This is an accepted limitation — real API tokens are
    // [A-Za-z0-9._-] and do not contain embedded quotes; use a JSON parser if full coverage is needed.
    // (PRIV-05) SC3: the key side is the shared key expression, which is already (?:...)-wrapped —
    // do not wrap it again here. Group 1 is still "key": including whitespace and group 2 is still
    // the value, so the "$1\"[REDACTED]\"" lambda in apply() is unchanged.
    private val jsonSecretKeyRegex =
        Regex(
            "(?i)(\"$SENSITIVE_KEY_EXPR\"\\s*:\\s*)(\"[^\"]*\"|true|false|null|-?\\d+(?:\\.\\d+)?)",
        )

    // (PRIV-02) Custom user patterns compiled by setCustomPatterns. Volatile so writes from the
    // EDT (save) are immediately visible to the redaction thread (apply) without full synchronization.
    @Volatile
    private var compiledCustomPatterns: List<Pattern> = emptyList()

    /**
     * Sets the list of user-supplied custom redaction patterns. Each string is compiled as a
     * java.util.regex.Pattern; entries that fail to compile (PatternSyntaxException) are silently
     * dropped. Passing an empty list clears all custom patterns.
     *
     * Call this from applyAndSaveSettings after the persisted list has been validated by
     * SafeRegex.isPatternSafe so the patterns in this list are already known-safe.
     */
    fun setCustomPatterns(patterns: List<String>) {
        compiledCustomPatterns =
            patterns.mapNotNull { raw ->
                try {
                    Pattern.compile(raw)
                } catch (_: PatternSyntaxException) {
                    null // silently skip uncompilable patterns
                }
            }
    }

    /**
     * (PRIV-06) D-03: optional sink for the truncation notice.
     *
     * Wired in App.initialize to api.logging()::logToOutput, beside the other diagnostics sinks. It
     * is null in tests and in headless contexts and the redaction pipeline never depends on it —
     * a missing sink costs the user visibility, never correctness.
     *
     * @Volatile because the write happens on the EDT at startup while the reads happen on scanner
     * threads and MCP tool threads. Modelled on backends/BackendDiagnostics.output, which is the
     * house idiom for a settable diagnostic sink on an otherwise stateless object.
     *
     * This callback is why `redact/` gains NO new dependency for D-03. Routing the notice through
     * the project's audit log was ruled out on two counts: this package is deliberately
     * dependency-light and free of any UI toolkit import, and audit logging is off by default here,
     * so an audit-only signal would be invisible to the very user it exists to warn.
     */
    @Volatile
    var truncationLogger: ((String) -> Unit)? = null

    // (PRIV-06) D-03: rate-limiter state for [maybeLogTruncation]. Two AtomicLongs rather than a
    // lock, so the read-then-CAS below stays allocation-free on the redaction hot path. These are
    // the ONLY object-level fields this phase adds — all window-loop state is local (T-21-23).
    private val lastTruncationLogMs = AtomicLong(0L)

    private val suppressedTruncations = AtomicLong(0L)

    // (PRIV-06) D-03: the notice window, the same length as PassiveAiScannerAnalysis's
    // BACKOFF_LOG_INTERVAL_MS so the two Output-tab limiters cannot drift apart.
    private const val TRUNCATION_LOG_INTERVAL_MS = 10_000L

    /**
     * (PRIV-06) D-03: emits at most one truncation notice per [TRUNCATION_LOG_INTERVAL_MS] window,
     * reporting how many further notices were suppressed since the previous one.
     *
     * [nowMs] is a PARAMETER and the system clock is never read inside, which is the convention
     * established by `PassiveAiScannerAnalysis.maybeLogBackoff(nowMs, untilMs)`: it makes the window
     * assertable without sleeping. That limiter is the model here, deliberately and not
     * `CliBackend.availabilityLogged`, whose semantics are once-ever rather than windowed.
     *
     * [droppedChars] is a count, never the dropped text. The emitted line carries counts only and
     * can therefore never echo attacker-controlled content into the Output tab (T-21-22).
     */
    internal fun maybeLogTruncation(
        nowMs: Long,
        droppedChars: Long,
    ) {
        val prev = lastTruncationLogMs.get()
        // Short-circuits so the CAS is only attempted once the window has actually elapsed. Losing
        // the CAS means a concurrent thread emitted for this window, so this call is a suppression.
        if (nowMs - prev < TRUNCATION_LOG_INTERVAL_MS || !lastTruncationLogMs.compareAndSet(prev, nowMs)) {
            suppressedTruncations.incrementAndGet()
            return
        }
        val suppressed = suppressedTruncations.getAndSet(0L)
        truncationLogger?.invoke(truncationLine(droppedChars, suppressed))
    }

    // (PRIV-06) D-03: the notice text. A constant sentence plus counts — no dropped content, and
    // no literal window duration that could drift away from TRUNCATION_LOG_INTERVAL_MS.
    private fun truncationLine(
        droppedChars: Long,
        suppressed: Long,
    ): String {
        val line = "[Redaction] Body redaction dropped $droppedChars characters; that content was NOT sent to the AI backend."
        return if (suppressed > 0L) "$line Further notices suppressed since the previous line: $suppressed." else line
    }

    // Internal test seam — clears the D-03 limiter window. NOT part of the public API; only
    // referenced from the test source set, in the style of the testHkdfExtract seam below. It
    // exists because Redaction is a singleton object, so the limiter state otherwise bleeds across
    // tests sharing a JVM and makes an injected-clock assertion order-dependent.
    internal fun resetTruncationWindowForTest() {
        lastTruncationLogMs.set(0L)
        suppressedTruncations.set(0L)
    }

    // (PRIV-06) D-02: nanoseconds per millisecond, used to turn System.nanoTime() deltas into the
    // millisecond deadlines SafeRegex takes. A named constant because detekt's MagicNumber rule
    // ignores only -1/0/1/2 and QUAL-07 forbids growing detekt-baseline.xml.
    private const val NANOS_PER_MS = 1_000_000L

    // (PRIV-06) D-01 / T-21-06: how many times a window that did not scan in time may be halved at
    // a line boundary and retried before it is dropped. Measured headroom at the 1 MB window width
    // is only ~2.2x on Apple Silicon (23 ms for dense form content against a 50 ms per-pattern
    // deadline), so a 2-3x slower machine would drop content that ships today. Two levels turn the
    // deadline into a pacing mechanism instead of a cliff.
    private const val WINDOW_RETRY_MAX_DEPTH = 2

    // (PRIV-06) D-03 / T-21-05: the marker left in the payload when the total budget is spent and
    // the remaining tail is dropped. Exactly ONE of these is emitted per call, never one per
    // window — a 200 MB input would otherwise produce ~200 markers and bloat the very prompt this
    // stage exists to bound.
    //
    // The wording is load-bearing in four ways and must keep all four:
    //   - it says the content was REMOVED, not passed through. A "TRUNCATED / NOT SCRUBBED" phrasing
    //     reads as "what follows is unfiltered", the opposite of what actually happened.
    //   - it is distinguishable from [REDACTED], so a reader can tell "removed for size" from
    //     "removed for secrecy" — the same reason [JWT_REDACTED] and [STRIPPED] are distinct today.
    //   - it is a constant shape plus one integer, so it carries ZERO attacker-controlled substring
    //     and is not a prompt-injection vector.
    //   - it is not phrased as an instruction to the model.
    // ASCII hyphen, never an em dash: the marker is prompt content that gets hashed into
    // sha256Hex(singlePrompt) and round-trips through backend transports.
    private fun budgetExceededMarker(droppedChars: Int): String = "[REDACTION BUDGET EXCEEDED - $droppedChars CHARS DROPPED AND NOT SENT]"

    // (PRIV-06) D-02 / D-03: the marker left in place of a single window that could not be fully
    // scanned inside its deadline. Same four wording properties as the budget marker above; a
    // distinct token so "the budget ran out" and "this window would not scan" stay tellable apart
    // in a prompt.
    private fun windowDroppedMarker(droppedChars: Int): String = "[REDACTION INCOMPLETE - $droppedChars CHARS DROPPED AND NOT SENT]"

    // (PRIV-06) D-01 AMENDED / D-02 / D-04 / D-05 / D-14: the body-redaction stage.
    //
    // Before Phase 21 this was an `if (out.length <= MAX_REDACTION_BODY_CHARS)` guard INSIDE the
    // redactTokens branch: an input above the cap had the form, JSON and custom-pattern rules
    // skipped entirely and the original passed through untouched. That is redaction failing open on
    // exactly the inputs most likely to carry a secret (T-21-02), and removing it is PRIV-06.
    //
    // D-01 AMENDED — the input is cut into windows at LINE BOUNDARIES and every window is scanned;
    // nothing is skipped. A line is never split: every built-in body rule is line-anchored or
    // line-local, so a mid-line cut is the only thing that can change their semantics, and it was
    // measured doing so in BOTH directions (creating a match at an artificial line start, and
    // truncating a match that spans the cut). The original decision's OVERLAP clause is
    // deliberately dropped rather than implemented: the value side of every built-in is
    // length-unbounded (measured single matches of 200 006 characters) and user patterns are
    // unbounded by construction, so no finite overlap constant is defensible. Line-boundary cutting
    // was proven byte-identical to whole-document processing, which is the property actually
    // needed. Matcher.region() has the right semantics but replaceAll() silently resets the region,
    // so region-scoped replacement is not available at all.
    //
    // D-02 / D-14 — fail CLOSED. Every rule above the window width runs through
    // SafeRegex.replaceAllSafeReporting and a window whose timedOut flag is set is DROPPED behind a
    // marker, never emitted. Assigning replaceAllSafe's return value there would be fail-OPEN at
    // exactly the moment D-02 demands fail-closed, because on timeout it returns the input
    // unchanged — byte-identical to "the pattern matched nothing" (T-21-03).
    @Suppress("ReturnCount")
    private fun bodyStage(
        input: String,
        builtinsEnabled: Boolean,
    ): String {
        val rules =
            buildList {
                if (builtinsEnabled) {
                    // The $n replacement strings are byte-for-byte equivalent to the Kotlin lambdas
                    // they replace (verified); SafeRegex takes a String replacement, not a lambda.
                    add(formBodyParamRegex.toPattern() to "\$1\$2=[REDACTED]")
                    add(jsonSecretKeyRegex.toPattern() to "\$1\"[REDACTED]\"")
                }
                // PRIV-06 / D-05: the custom-pattern loop sits OUTSIDE the redactTokens branch and
                // therefore applies in EVERY privacy mode, including OFF. A user's custom list is a
                // "never send this, ever" list and is independent of the privacy mode; OFF now
                // means "no built-in redaction", not "no redaction at all".
                compiledCustomPatterns.forEach { add(it to "[REDACTED]") }
            }

        // OFF with no custom patterns must be a byte-identical passthrough, so this check comes
        // BEFORE any windowing. Any marker, normalisation or trailing-newline difference introduced
        // here fails offModePreservesBodies and offModePreservesAllTokens.
        if (rules.isEmpty()) return input

        // PRIV-06 / D-04: at or below the window width this is a single pass whose cost and
        // behaviour match the pre-Phase-21 implementation for the overwhelming majority of
        // payloads. One genuine change even on this path: the two built-in body rules previously
        // ran with NO deadline at all (only custom patterns went through SafeRegex) and now all of
        // them do, so a rule that overruns here is skipped rather than allowed to run long.
        if (input.length <= Defaults.MAX_REDACTION_BODY_CHARS) {
            var out = input
            for ((pattern, replacement) in rules) {
                out = SafeRegex.replaceAllSafe(out, pattern, replacement)
            }
            return out
        }

        return windowedScan(input, rules)
    }

    // (PRIV-06) D-01 AMENDED / D-02: the windowed loop used above the window width. Windows are
    // processed IN ORDER, and each one is either fully scanned and appended or dropped behind a
    // marker. Once the total budget is spent, ALL remaining characters are coalesced into exactly
    // one tail marker and the loop breaks. Every piece of loop state is a local: apply() runs
    // concurrently on scanner threads and MCP tool threads, so object-level window state would
    // corrupt output across them (T-21-23).
    private fun windowedScan(
        input: String,
        rules: List<Pair<Pattern, String>>,
    ): String {
        val budgetDeadlineNanos = System.nanoTime() + Defaults.MAX_REDACTION_BUDGET_MS * NANOS_PER_MS
        val sink = StringBuilder(input.length)
        var index = 0
        while (index < input.length) {
            if (remainingBudgetMs(budgetDeadlineNanos) <= 0L) {
                val dropped = input.length - index
                sink.append(budgetExceededMarker(dropped))
                maybeLogTruncation(System.currentTimeMillis(), dropped.toLong())
                break
            }
            val end = windowEnd(input, index, Defaults.MAX_REDACTION_BODY_CHARS)
            scanWindow(input.substring(index, end), rules, budgetDeadlineNanos, 0, sink)
            index = end
        }
        return sink.toString()
    }

    // (PRIV-06) D-02: milliseconds left in the total body-stage budget; zero or negative once spent.
    private fun remainingBudgetMs(budgetDeadlineNanos: Long): Long = (budgetDeadlineNanos - System.nanoTime()) / NANOS_PER_MS

    // (PRIV-06) D-02 / D-14: applies every rule to one window under a bounded deadline and appends
    // the result ONLY when the whole window was scanned.
    //
    // The minOf() is what stops a per-pattern deadline outliving the total budget: a rule starting
    // with 12 ms of budget left gets a 12 ms deadline and REPORTS a timeout instead of overrunning.
    // replaceAllSafe's return value is never assigned here — on timeout it returns the input
    // unchanged, indistinguishable from "no matches".
    private fun scanWindow(
        window: String,
        rules: List<Pair<Pattern, String>>,
        budgetDeadlineNanos: Long,
        depth: Int,
        sink: StringBuilder,
    ) {
        var current = window
        for ((pattern, replacement) in rules) {
            val remainingMs = remainingBudgetMs(budgetDeadlineNanos)
            if (remainingMs <= 0L) {
                dropOrRetry(window, rules, budgetDeadlineNanos, depth, sink)
                return
            }
            val result =
                SafeRegex.replaceAllSafeReporting(
                    current,
                    pattern,
                    replacement,
                    minOf(SafeRegex.DEFAULT_TIMEOUT_MS, remainingMs),
                )
            if (result.timedOut) {
                dropOrRetry(window, rules, budgetDeadlineNanos, depth, sink)
                return
            }
            current = result.text
        }
        sink.append(current)
    }

    // (PRIV-06) D-01 / T-21-06: halve-and-retry before dropping. A window that did not scan in time
    // is split at a LINE boundary and each half retried, to WINDOW_RETRY_MAX_DEPTH levels, so a
    // slower machine paces instead of losing content outright. When the budget is already spent, or
    // the retry depth is exhausted, or the window has no interior line boundary to split on, the
    // window is dropped behind exactly one marker — fail closed, so its unscanned bytes never reach
    // a backend.
    private fun dropOrRetry(
        window: String,
        rules: List<Pair<Pattern, String>>,
        budgetDeadlineNanos: Long,
        depth: Int,
        sink: StringBuilder,
    ) {
        val retryable = depth < WINDOW_RETRY_MAX_DEPTH && remainingBudgetMs(budgetDeadlineNanos) > 0L
        val cut = if (retryable) splitPoint(window) else 0
        if (cut <= 0 || cut >= window.length) {
            sink.append(windowDroppedMarker(window.length))
            maybeLogTruncation(System.currentTimeMillis(), window.length.toLong())
            return
        }
        scanWindow(window.substring(0, cut), rules, budgetDeadlineNanos, depth + 1, sink)
        scanWindow(window.substring(cut), rules, budgetDeadlineNanos, depth + 1, sink)
    }

    // (PRIV-06) D-01: the line boundary nearest the middle of [window], or 0 when the window cannot
    // be split without cutting a line. The returned index is just past a newline, so both halves
    // stay line-aligned and (?m)^ keeps its meaning inside each of them.
    private fun splitPoint(window: String): Int {
        val mid = window.length / 2
        val backward = window.lastIndexOf('\n', mid)
        if (backward > 0) return backward + 1
        val forward = window.indexOf('\n', mid)
        return if (forward >= 0 && forward + 1 < window.length) forward + 1 else 0
    }

    // (PRIV-06) D-01 AMENDED: the end of the window starting at [start] — the index just past the
    // last newline at or before [start] + [width]. A single line longer than [width] becomes its own
    // oversized window rather than being split; the per-pattern deadline already bounds what that
    // costs, and a mid-line cut is the only thing that can change a line-anchored rule's semantics.
    //
    // JSON boundary safety: jsonSecretKeyRegex's whitespace class CAN span newlines (it matches a
    // pretty-printed key/colon/value spread over four lines), so a pair split exactly across a
    // window boundary would be missed. When the last line of the prospective window ends with a
    // colon or a double quote after a trailing-whitespace strip, one more line is pulled in.
    // formBodyParamRegex and urlTokenParamRegex cannot span newlines (their value classes exclude
    // \s) and need nothing.
    //
    // ACCEPTED RESIDUAL: a CUSTOM pattern whose match straddles a window boundary can still be
    // missed. There is no principled bound on a user regex's match length, so no window scheme can
    // close this; it is recorded rather than pretended away.
    @Suppress("ReturnCount")
    private fun windowEnd(
        s: String,
        start: Int,
        width: Int,
    ): Int {
        // Written this way rather than as start + width so the sum cannot overflow on a huge input.
        val hard = if (width >= s.length - start) s.length else start + width
        if (hard == s.length) return hard
        val lastNewline = s.lastIndexOf('\n', hard)
        if (lastNewline <= start) {
            // The line starting at [start] is longer than the window: keep that line whole.
            val next = s.indexOf('\n', start)
            return if (next < 0) s.length else next + 1
        }
        var end = lastNewline + 1
        val prevLineStart = s.lastIndexOf('\n', lastNewline - 1) + 1
        if (isJsonPairBoundaryRisk(s.substring(maxOf(start, prevLineStart), lastNewline))) {
            val following = s.indexOf('\n', end)
            if (following >= 0) end = following + 1
        }
        return end
    }

    // (PRIV-06) D-01: true when [line] ends where jsonSecretKeyRegex's newline-spanning whitespace
    // class could be sitting between a key and its value.
    private fun isJsonPairBoundaryRisk(line: String): Boolean {
        val trimmed = line.trimEnd()
        return trimmed.endsWith(":") || trimmed.endsWith("\"")
    }

    private val hostHeaderRegex = Regex("(?im)^host:\\s*([^\\s]+)\\s*$")

    // REL-02/SC5b: cap for the inner per-salt LRU maps. A few thousand entries (4096) is large
    // enough that a normal pentest session never evicts, but small enough to bound memory over
    // a long session (DoS mitigation). Forward/reverse eviction skew is benign: if forward evicts
    // host→anon but reverse still holds anon→host, de-anonymization still works (reverse is the
    // lookup path). Re-anonymizing an evicted host is deterministic (HKDF is pure, so the same
    // host+salt always produces the same anon value) and merely re-populates the forward entry.
    private const val HOST_MAP_CAP = 4096

    // Creates a bounded LRU map (synchronized, access-ordered LinkedHashMap with eldest-entry
    // eviction). Used for INNER per-salt maps only; the OUTER ConcurrentHashMap stays unbounded.
    private fun <K, V> boundedLru(maxEntries: Int): MutableMap<K, V> =
        java.util.Collections.synchronizedMap(
            // accessOrder = true (access-ordered LRU)
            object : LinkedHashMap<K, V>(16, 0.75f, true) {
                override fun removeEldestEntry(eldest: Map.Entry<K, V>): Boolean = size > maxEntries
            },
        )

    private val hostForwardMap = ConcurrentHashMap<String, MutableMap<String, String>>()
    private val hostReverseMap = ConcurrentHashMap<String, MutableMap<String, String>>()

    // HKDF constants (RFC 5869, https://www.rfc-editor.org/rfc/rfc5869).
    // App-specific context label binds the derivation to this use case (host anonymization).
    // L = 6 bytes → 12 hex chars, preserving the exact host-<12hex>.local output format.
    private const val HKDF_INFO = "burp-ai-agent:host"
    private const val HKDF_OKM_LEN = 6

    // RFC 5869 HMAC-SHA256 primitive.
    // If [key] is empty a single zero byte is substituted — SecretKeySpec rejects a 0-length key
    // (Pitfall 1: RFC 5869 allows an absent/all-zero salt but JCA requires >= 1 key byte).
    private fun hmacSha256(
        key: ByteArray,
        data: ByteArray,
    ): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        val keySpec = SecretKeySpec(if (key.isEmpty()) ByteArray(1) else key, "HmacSHA256")
        mac.init(keySpec)
        return mac.doFinal(data)
    }

    // RFC 5869 HKDF-Extract: PRK = HMAC-Hash(salt, IKM).
    private fun hkdfExtract(
        salt: ByteArray,
        ikm: ByteArray,
    ): ByteArray = hmacSha256(salt, ikm)

    // RFC 5869 HKDF-Expand: OKM = first [length] octets of T(1)|T(2)|...
    // T(i) = HMAC(PRK, T(i-1) | info | counter_byte), T(0) = empty.
    private fun hkdfExpand(
        prk: ByteArray,
        info: ByteArray,
        length: Int,
    ): ByteArray {
        val out = ByteArrayOutputStream()
        var t = ByteArray(0)
        var counter = 1
        while (out.size() < length) {
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(prk, "HmacSHA256"))
            mac.update(t)
            mac.update(info)
            mac.update(counter.toByte())
            t = mac.doFinal()
            out.write(t)
            counter++
        }
        return out.toByteArray().copyOf(length)
    }

    // Internal test seams — expose the HKDF helpers for RFC 5869 vector assertion in
    // RedactionTest.hkdfMatchesRfc5869Vector. NOT part of the public API; only referenced
    // from the test source set.
    internal fun testHkdfExtract(
        salt: ByteArray,
        ikm: ByteArray,
    ): ByteArray = hkdfExtract(salt, ikm)

    internal fun testHkdfExpand(
        prk: ByteArray,
        info: ByteArray,
        length: Int,
    ): ByteArray = hkdfExpand(prk, info, length)

    fun apply(
        raw: String,
        policy: RedactionPolicy,
        stableHostSalt: String,
        recordMapping: Boolean = true,
    ): String {
        var out = raw

        if (policy.stripCookies) {
            out = out.replace(cookieHeaderRegex, "Cookie: [STRIPPED]")
            out = out.replace(setCookieHeaderRegex, "Set-Cookie: [STRIPPED]")
            // (PRIV-05) SC1: the passive scanner re-emits cookies as bare name=value lines in a
            // dedicated section, stripped of the prefix the two header rules above key on.
            out = redactCookieSections(out)
            // (PRIV-05) SC2: the same values leak a second time as typed parameter lines. Only the
            // value is replaced; the name and the trailing type label are written back verbatim.
            // Both rules run BEFORE the body stage and are idempotent under it — re-matching
            // NAME=[REDACTED] reproduces NAME=[REDACTED] — so no ordering hazard exists.
            out =
                out.replace(cookieTypedParamRegex) { m ->
                    // Destructured rather than groupValues[3]: detekt's MagicNumber rule ignores
                    // only -1/0/1/2, and a named binding reads better than a bare group index.
                    val (name, _, typeSuffix) = m.destructured
                    "$name=[REDACTED]$typeSuffix"
                }
        }

        if (policy.redactTokens) {
            out =
                out.replace(authHeaderRegex) { m ->
                    val header = m.value.substringBefore(":")
                    "$header: [REDACTED]"
                }
            out = out.replace(bearerRegex, "Bearer [REDACTED]")
            out = out.replace(basicAuthRegex, "Basic [REDACTED]")
            out = out.replace(jwtRegex, "[JWT_REDACTED]")
            out = out.replace(urlTokenParamRegex, "$1[REDACTED]")
        }

        // (PRIV-06) D-01 AMENDED / D-02 / D-05: body-level redaction (form + JSON + custom
        // patterns), invoked UNCONDITIONALLY rather than from inside the redactTokens branch above.
        // The built-in body rules still follow policy.redactTokens, but the custom-pattern loop no
        // longer does — that move is the whole of D-05, and it is what lets a user's "never send
        // this, ever" list apply under PrivacyMode.OFF as well.
        //
        // The eight header-stage rules above deliberately stay OUTSIDE this budget: they run
        // unbounded on the full input exactly as they did before this phase. That is a pre-existing
        // condition outside D-01/D-02's scope, recorded rather than silently claimed as fixed.
        out = bodyStage(out, builtinsEnabled = policy.redactTokens)

        if (policy.anonymizeHosts) {
            out =
                out.replace(hostHeaderRegex) { m ->
                    val host = m.groupValues[1]
                    val anon = anonymizeHost(host, stableHostSalt, recordMapping)
                    "Host: $anon"
                }
        }

        return out
    }

    // Anonymizes [host] using RFC 5869 HKDF (HMAC-SHA256 extract-then-expand).
    // Signature and output format (host-<12hex>.local) are preserved from the previous
    // SHA-256 implementation so all ~10 call sites remain unchanged (Pitfall 6).
    // salt → HKDF extract salt; host → IKM (input keying material).
    fun anonymizeHost(
        host: String,
        salt: String,
        recordMapping: Boolean = true,
    ): String {
        val prk =
            hkdfExtract(
                salt.toByteArray(StandardCharsets.UTF_8),
                host.toByteArray(StandardCharsets.UTF_8),
            )
        val okm =
            hkdfExpand(
                prk,
                HKDF_INFO.toByteArray(StandardCharsets.UTF_8),
                HKDF_OKM_LEN,
            )
        val short = okm.joinToString("") { "%02x".format(it) } // 6 bytes → 12 hex chars
        val anon = "host-$short.local"
        if (recordMapping) {
            // REL-02/SC5b: inner map is bounded LRU (cap HOST_MAP_CAP); outer map + computeIfAbsent/
            // remove are unchanged so clearMappings() keeps working (Pitfall 5).
            hostForwardMap.computeIfAbsent(salt) { boundedLru(HOST_MAP_CAP) }[host] = anon
            hostReverseMap.computeIfAbsent(salt) { boundedLru(HOST_MAP_CAP) }[anon] = host
        }
        return anon
    }

    fun deAnonymizeHost(
        host: String,
        salt: String,
    ): String? = hostReverseMap[salt]?.get(host)

    fun clearMappings(salt: String? = null) {
        if (salt == null) {
            hostForwardMap.clear()
            hostReverseMap.clear()
            return
        }
        hostForwardMap.remove(salt)
        hostReverseMap.remove(salt)
    }
}
