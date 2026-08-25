package com.six2dez.burp.aiagent.mcp.tools

import burp.api.montoya.MontoyaApi
import burp.api.montoya.core.BurpSuiteEdition
import burp.api.montoya.http.message.params.HttpParameterType
import burp.api.montoya.http.message.params.ParsedHttpParameter
import com.six2dez.burp.aiagent.mcp.McpRequestLimiter
import com.six2dez.burp.aiagent.mcp.McpToolContext
import com.six2dez.burp.aiagent.redact.PrivacyMode
import com.six2dez.burp.aiagent.redact.Redaction
import kotlinx.serialization.encodeToString
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Answers
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.File

/**
 * (PRIV-05) Phase 27 plan 27-07 — the THIRD carrier of the same cookie bytes: COOKIE-typed HTTP
 * parameters.
 *
 * WHAT THIS FILE ASSERTS ON, and why it is a different shape from both `RedactionTest`'s cookie
 * fixtures and `SerializedEmissionRedactionTest`'s raw-message carrier: Burp parses the `Cookie:`
 * header into `HttpParameterType.COOKIE` parameters, and `HttpRequest.parameters()` hands those
 * VALUES to four live MCP producers — `request_parse` and `params_extract`, once per executor. In
 * `request_parse` the leak sat inside the same JSON object as its own control: `headers` was
 * cookie-stripped by `sanitizeHeaders` while `parameters` returned the identical value verbatim.
 *
 * THE CONTROL UNDER TEST IS TYPE-KEYED, NOT SHAPE-KEYED. Every probe here drives the REAL
 * [sanitizeParameters] and the REAL [toolJson] serializer — never a reimplementation of either. A
 * gate that does not exercise the code it guards is the second anti-pattern this phase paid for:
 * `Redaction.cookieTypedParamRegex` is keyed to the passive scanner's `name=value (COOKIE)`
 * rendering, was verified green at the site it was written for, and is measurably blind to both MCP
 * shapes.
 *
 * SCOPE OF THE CLAIM THIS FILE SUPPORTS: the MCP parameter carrier, and the cookie TYPE class.
 * Nothing here says PRIV-05 is closed, and nothing here is evidence about a URL- or BODY-typed
 * parameter whose VALUE carries a token — that is D-27-20's deliberate pass-through, measured by
 * plan 27-08 task 3, not asserted either way here.
 *
 * SENTINEL DISCIPLINE (the reason an absence assertion here cannot pass for some other rule's
 * reason). Every sentinel is a bare lowercase alphabetic word:
 *  - it contains no `=`, so `formBodyParamRegex`, `urlTokenParamRegex` and `cookieTypedParamRegex`
 *    cannot claim it;
 *  - it carries no `Bearer ` / `Basic ` prefix and no dotted `eyJ` segment, so `bearerRegex`,
 *    `basicAuthRegex` and `jwtRegex` cannot claim it;
 *  - the enclosing JSON key is the literal `value`, which is not in `SENSITIVE_WORDS`, so
 *    `jsonSecretKeyRegex` cannot claim it;
 *  - no `=== COOKIES ===` section is ever built, so `redactCookieSections` never runs.
 *
 * These probes additionally do NOT call `redactIfNeeded` at all — the control is at the PRODUCER,
 * so a pass here is attributable to [sanitizeParameters] and to nothing else in the rule set.
 *
 * FLAT, not `@Nested`, DELIBERATELY. JUnit writes each `@Nested` inner class to its OWN
 * `TEST-<outer>$<Inner>.xml`, so this plan's acceptance gate — "every test method present BY NAME in
 * `TEST-…ParameterCarrierRedactionTest.xml`" — would be unsatisfiable against a nested layout no
 * matter how green the suite was. That is the `WINDOWS.md` 11/13/14/15 class exactly: a criterion
 * that counts a population the artifact does not contain. One class, one XML, one decidable gate.
 */
class ParameterCarrierRedactionTest {
    @BeforeEach
    fun clearCustomPatterns() {
        // Redaction is a singleton object: custom patterns left behind by another test class in the
        // same JVM could remove a sentinel for the wrong reason and let an absence assertion pass
        // without this control doing anything. Precedent: SerializedEmissionRedactionTest.
        Redaction.setCustomPatterns(emptyList())
    }

    @Test
    fun everySentinelInThisFileIsDistinct() {
        // Distinctness asserted IN KOTLIN over the list the fixtures actually draw from, so a
        // copy-paste collision fails rather than flatters. The substring half is load-bearing in its
        // own right: every gate below is an `assertFalse(output.contains(sentinel))`, and a sentinel
        // that is a SUBSTRING of another defeats that gate exactly as a duplicate does.
        assertEquals(
            ALL_SENTINELS.size,
            ALL_SENTINELS.toSet().size,
            "sentinels must be distinct, or one probe's success masks another's failure: $ALL_SENTINELS",
        )
        for (a in ALL_SENTINELS) {
            for (b in ALL_SENTINELS) {
                if (a === b) continue
                assertFalse(
                    b.contains(a),
                    "'$a' is a substring of '$b' — an absence assertion on '$a' could then pass while '$b' leaks",
                )
            }
        }
    }

    // ── the sanitizer itself: type-keyed, order-preserving, policy-driven ────────────────

    @Test
    fun cookieTypedParameterValueIsStrippedUnderStrict() {
        val sanitized =
            sanitizeParameters(
                listOf(cookieParam("wibble", Sentinel.COOKIE_STRICT.value)),
                contextWith(PrivacyMode.STRICT, "param-strict-salt"),
            )

        assertEquals(1, sanitized.size, "arity must be preserved")
        assertEquals(STRIPPED_MARKER, sanitized[0].value, "a COOKIE-typed value must not survive STRICT")
        assertEquals("wibble", sanitized[0].name, "the parameter NAME is not the secret and must survive verbatim")
        assertEquals("COOKIE", sanitized[0].type, "the type label must survive verbatim")
    }

    @Test
    fun cookieTypedParameterValueIsStrippedUnderBalanced() {
        val sanitized =
            sanitizeParameters(
                listOf(cookieParam("wibble", Sentinel.COOKIE_BALANCED.value)),
                contextWith(PrivacyMode.BALANCED, "param-balanced-salt"),
            )

        assertEquals(STRIPPED_MARKER, sanitized[0].value, "BALANCED carries stripCookies too")
    }

    @Test
    fun cookieTypedParameterValuePassesThroughUnderOff() {
        // The OFF control. Its whole job is to prove this is a MEASUREMENT OF THE POLICY and not a
        // blanket strip — an absence assertion that would also pass with the value hard-coded away is
        // not evidence. Note the assertion is on pass-through under a NON-redacting policy:
        // 26-SECURITY.md records that a green assertion of SURVIVAL under STRICT is the artifact the
        // threat register exists to stop producing.
        val sanitized =
            sanitizeParameters(
                listOf(cookieParam("wibble", Sentinel.COOKIE_OFF.value)),
                contextWith(PrivacyMode.OFF, "param-off-salt"),
            )

        assertEquals(Sentinel.COOKIE_OFF.value, sanitized[0].value, "OFF must be OFF")
    }

    @Test
    fun urlTypedParameterValueIsUntouchedInEveryMode() {
        // Proves the key is the TYPE. A URL-typed parameter is byte-identical in all three modes,
        // including the two that strip cookies.
        for (mode in listOf(PrivacyMode.STRICT, PrivacyMode.BALANCED, PrivacyMode.OFF)) {
            val sanitized =
                sanitizeParameters(
                    listOf(paramOfType(HttpParameterType.URL, "search", Sentinel.URL_CONTROL.value)),
                    contextWith(mode, "param-url-salt-$mode"),
                )

            assertEquals(
                Sentinel.URL_CONTROL.value,
                sanitized[0].value,
                "a URL-typed parameter is outside PRIV-05's cookie wording (D-27-20) and must be untouched in $mode",
            )
        }
    }

    @Test
    fun bodyTypedParameterValueIsUntouchedInEveryMode() {
        for (mode in listOf(PrivacyMode.STRICT, PrivacyMode.BALANCED, PrivacyMode.OFF)) {
            val sanitized =
                sanitizeParameters(
                    listOf(paramOfType(HttpParameterType.BODY, "comment", Sentinel.BODY_CONTROL.value)),
                    contextWith(mode, "param-body-salt-$mode"),
                )

            assertEquals(Sentinel.BODY_CONTROL.value, sanitized[0].value, "a BODY-typed parameter must be untouched in $mode")
        }
    }

    @Test
    fun orderAndArityArePreservedAcrossMixedTypes() {
        val input =
            listOf(
                paramOfType(HttpParameterType.URL, "a", Sentinel.ORDER_URL.value),
                cookieParam("b", Sentinel.ORDER_COOKIE.value),
                paramOfType(HttpParameterType.BODY, "c", Sentinel.ORDER_BODY.value),
            )

        val sanitized = sanitizeParameters(input, contextWith(PrivacyMode.STRICT, "param-order-salt"))

        assertEquals(3, sanitized.size, "N parameters in, N out")
        assertEquals(listOf("a", "b", "c"), sanitized.map { it.name }, "sequence must be preserved")
        assertEquals(
            listOf(Sentinel.ORDER_URL.value, STRIPPED_MARKER, Sentinel.ORDER_BODY.value),
            sanitized.map { it.value },
            "only the cookie-typed entry may change, and only in place",
        )
    }

    // ── carrier 1: the request_parse serialized shape, through the REAL toolJson ─────────

    @Test
    fun cookieSentinelDoesNotSurviveTheSerializedRequestParseShapeUnderStrict() {
        val serialized = toolJson.encodeToString(parsedRequestFrom(PrivacyMode.STRICT, "rp-strict-salt", Sentinel.SERIALIZED_STRICT.value))

        assertFalse(
            serialized.contains(Sentinel.SERIALIZED_STRICT.value),
            "the cookie value reached the serialized tool result: $serialized",
        )
        assertTrue(serialized.contains(STRIPPED_MARKER), "the marker must be present in its place: $serialized")
    }

    @Test
    fun cookieSentinelDoesNotSurviveTheSerializedRequestParseShapeUnderBalanced() {
        val serialized =
            toolJson.encodeToString(parsedRequestFrom(PrivacyMode.BALANCED, "rp-balanced-salt", Sentinel.SERIALIZED_BALANCED.value))

        assertFalse(
            serialized.contains(Sentinel.SERIALIZED_BALANCED.value),
            "the cookie value reached the serialized tool result: $serialized",
        )
    }

    @Test
    fun cookieSentinelDoesSurviveTheSerializedRequestParseShapeUnderOff() {
        val serialized = toolJson.encodeToString(parsedRequestFrom(PrivacyMode.OFF, "rp-off-salt", Sentinel.SERIALIZED_OFF.value))

        assertTrue(
            serialized.contains(Sentinel.SERIALIZED_OFF.value),
            "OFF must leave the serialized shape untouched, or the two probes above prove nothing: $serialized",
        )
    }

    @Test
    fun theSerializedFieldSetIsUnchangedByThisControl() {
        // D-27-18: ParsedParam.value stays a non-null String and no field is added, removed or
        // reordered, so no MCP client's schema breaks. Asserted on the REAL serializer output.
        val stripped = toolJson.encodeToString(parsedRequestFrom(PrivacyMode.STRICT, "rp-shape-salt", Sentinel.SHAPE_STRICT.value))
        val passthrough = toolJson.encodeToString(parsedRequestFrom(PrivacyMode.OFF, "rp-shape-salt", Sentinel.SHAPE_STRICT.value))

        assertEquals(
            passthrough.replace(Sentinel.SHAPE_STRICT.value, STRIPPED_MARKER),
            stripped,
            "the only permitted difference between the two modes is the value substitution itself",
        )
    }

    // ── the producer pin: the behavioural probes above cannot reach the branch ───────────

    @Test
    fun theRequestParseProducerRoutesThroughTheSanitizer() {
        // WHY THIS IS A SOURCE SCAN AND NOT AN END-TO-END CALL. The `request_parse` branch begins
        // `HttpRequest.httpRequest(input.content)`, a Montoya STATIC FACTORY that needs Burp's
        // internal ObjectFactory and cannot run in a pure-JVM test — `McpToolScopeEnforcementTest`
        // records the same constraint at its own site. So the probes above prove the SANITIZER, and
        // this pin proves the PRODUCER is wired to it. Without this assertion the suite would stay
        // green with `sanitizeParameters` correct and never called, which is precisely the
        // "verified at the site it was written for" failure this phase exists to repair.
        val calls = codeLines(MODERN_EXECUTOR).filter { it.text.contains(SANITIZER_CALL) }

        assertTrue(
            calls.isNotEmpty(),
            "$MODERN_EXECUTOR no longer calls `$SANITIZER_CALL`. Its request_parse branch is emitting " +
                "parameter values with no cookie control — the exact state 27-VERIFICATION-2.md recorded.",
        )
        assertEquals(
            0,
            codeLines(MODERN_EXECUTOR).count { it.text.contains(PARSED_PARAM_CONSTRUCTION) },
            "$MODERN_EXECUTOR constructs `$PARSED_PARAM_CONSTRUCTION` itself. `sanitizeParameters` is the " +
                "sole producer of ParsedParam by design (D-27-17); a second producer is how the " +
                "control gets bypassed without anyone editing the sanitizer.",
        )
    }

    @Test
    fun theProducerScanIsNonVacuous() {
        // Copied from CookieHeaderRuleOwnershipTest.theOwnershipScanIsNonVacuous. A repository-state
        // test that goes green when it cannot find the repository is worse than the grep it replaced.
        val lines = codeLines(MODERN_EXECUTOR)
        assertTrue(
            lines.size >= MIN_EXPECTED_EXECUTOR_LINES,
            "the scan read only ${lines.size} code lines from $MODERN_EXECUTOR — it is not reaching the " +
                "repository, so the pin above proves nothing",
        )

        // Comment stripping proven live: this file's own KDoc and comments name every symbol the scan
        // looks for, and an unfiltered scan would count that prose as evidence.
        assertTrue(isCommentOnly("    // $SANITIZER_CALL"), "the comment filter no longer recognises a line comment")
        assertTrue(isCommentOnly("     * $SANITIZER_CALL"), "the comment filter no longer recognises a KDoc body line")
        assertTrue(
            lines.none { isCommentOnly(it.text) },
            "a comment-only line reached the producer scan",
        )

        // And the needle proven live against a known positive, so a count of zero cannot pass as
        // agreement with a pinned zero.
        assertTrue(
            "parameters = sanitizeParameters(request.parameters(), context),".contains(SANITIZER_CALL),
            "the scan needle `$SANITIZER_CALL` no longer matches its own known-positive fixture",
        )
        assertTrue(
            "ParsedParam(type = param.type().name, name = param.name(), value = param.value())"
                .contains(PARSED_PARAM_CONSTRUCTION),
            "the scan needle `$PARSED_PARAM_CONSTRUCTION` no longer matches its own known-positive fixture",
        )
    }

    // ── the scans ───────────────────────────────────────────────────────────────────────

    private data class SourceLine(
        val number: Int,
        val text: String,
    ) {
        override fun toString(): String = "$number:${text.trim()}"
    }

    /** Non-comment lines of [relPath], 1-based, so prose about a symbol never counts as the symbol. */
    private fun codeLines(relPath: String): List<SourceLine> =
        sourceFile(relPath)
            .readLines()
            .mapIndexed { index, text -> SourceLine(index + 1, text) }
            .filterNot { isCommentOnly(it.text) }

    private fun isCommentOnly(line: String): Boolean {
        val trimmed = line.trimStart()
        return trimmed.startsWith("//") || trimmed.startsWith("*") || trimmed.startsWith("/*")
    }

    private fun sourceFile(relPath: String): File = File(mainSourceRoot(), relPath)

    /** Resolved by walking up from the Gradle test working directory. FAILS rather than skips. */
    private fun mainSourceRoot(): File {
        var candidate: File? = File(System.getProperty("user.dir")).absoluteFile
        while (candidate != null) {
            val root = File(candidate, MAIN_SOURCE_ROOT)
            if (root.isDirectory) return root
            candidate = candidate.parentFile
        }
        throw AssertionError(
            "could not resolve $MAIN_SOURCE_ROOT from user.dir=${System.getProperty("user.dir")}. " +
                "Resolve the path rather than weakening this test into a skip.",
        )
    }

    // ── shared fixtures ─────────────────────────────────────────────────────────────────

    private fun parsedRequestFrom(
        mode: PrivacyMode,
        salt: String,
        sentinel: String,
    ): ParsedRequest =
        ParsedRequest(
            method = "GET",
            path = "/a",
            url = "http://example.com/a",
            headers = mapOf("Host" to "example.com"),
            parameters = sanitizeParameters(listOf(cookieParam("wibble", sentinel)), contextWith(mode, salt)),
            body = null,
            bodyLength = 0,
        )

    private fun cookieParam(
        name: String,
        value: String,
    ): ParsedHttpParameter = paramOfType(HttpParameterType.COOKIE, name, value)

    private fun paramOfType(
        type: HttpParameterType,
        name: String,
        value: String,
    ): ParsedHttpParameter {
        val param = mock<ParsedHttpParameter>()
        whenever(param.type()).thenReturn(type)
        whenever(param.name()).thenReturn(name)
        whenever(param.value()).thenReturn(value)
        return param
    }

    private fun contextWith(
        mode: PrivacyMode,
        salt: String,
    ): McpToolContext {
        val api = mock<MontoyaApi>(defaultAnswer = Answers.RETURNS_DEEP_STUBS)
        return McpToolContext(
            api = api,
            privacyMode = mode,
            determinismMode = false,
            hostSalt = salt,
            toolToggles = emptyMap(),
            unsafeEnabled = false,
            unsafeTools = emptySet(),
            enabledUnsafeTools = emptySet(),
            limiter = McpRequestLimiter(4),
            edition = BurpSuiteEdition.PROFESSIONAL,
            maxBodyBytes = 1024,
        )
    }

    /**
     * Every fixture value this file uses, declared ONCE. [ALL_SENTINELS] is derived from `entries`,
     * so the distinctness gate cannot drift from the fixtures: a new value has to be an entry here
     * to be usable at all.
     */
    private enum class Sentinel(
        val value: String,
    ) {
        COOKIE_STRICT("paramalfa"),
        COOKIE_BALANCED("parambravo"),
        COOKIE_OFF("paramcharlie"),
        URL_CONTROL("paramdelta"),
        BODY_CONTROL("paramecho"),
        ORDER_URL("paramfoxtrot"),
        ORDER_COOKIE("paramgolf"),
        ORDER_BODY("paramhotel"),
        SERIALIZED_STRICT("paramindia"),
        SERIALIZED_BALANCED("paramjuliett"),
        SERIALIZED_OFF("paramkilo"),
        SHAPE_STRICT("paramlima"),
    }

    private companion object {
        val ALL_SENTINELS: List<String> = Sentinel.entries.map { it.value }

        // The same literal sanitizeHeaders writes for a stripped cookie HEADER (D-27-18): one
        // vocabulary across both fields of one request_parse result.
        const val STRIPPED_MARKER = "[STRIPPED]"

        const val MAIN_SOURCE_ROOT = "src/main/kotlin"
        const val TOOLS_PACKAGE = "com/six2dez/burp/aiagent/mcp/tools"
        const val MODERN_EXECUTOR = "$TOOLS_PACKAGE/McpToolExecutorImpl.kt"

        /** Measured at ~1050 code lines. The floor catches a scan that reaches nothing, not drift. */
        const val MIN_EXPECTED_EXECUTOR_LINES = 600

        const val SANITIZER_CALL = "sanitizeParameters("
        const val PARSED_PARAM_CONSTRUCTION = "ParsedParam("
    }
}
