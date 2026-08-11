package com.six2dez.burp.aiagent.scanner

import burp.api.montoya.http.message.params.HttpParameterType
import burp.api.montoya.http.message.params.ParsedHttpParameter
import com.six2dez.burp.aiagent.redact.PrivacyMode
import com.six2dez.burp.aiagent.redact.Redaction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

// (PRIV-05 / PRIV-06) Wave 0 seam for Phase 21. doAnalysis itself needs a MontoyaApi, a backend
// session and ScanKnowledgeBase state, so the PRIV-05-relevant logic was extracted into
// PassiveAiScannerPrompts.kt. This file asserts SC1 (the emitted section shapes), SC2 (the parameter
// line's Montoya type suffix) and D-06 (redactScanMetadata really reaches Redaction.apply) against
// the REAL emitted blob rather than a hand-written string — the gap that let PRIV-05 ship.
// Plans 21-05 and 21-06 extend this file. It deliberately asserts no current leak behaviour: an
// assertion of the defect would have to be deleted a wave later, when the cookie rules land.

// Mirrors PARAM_VALUE_MAX_CHARS, private in PassiveAiScannerAnalysis.kt:26. Truncation stays at the
// call site, so the test must apply it exactly as doAnalysis does.
private const val PARAM_VALUE_MAX_CHARS = 200
private const val HOST_SALT = "phase-21-wave-0-salt"

class PassiveAiScannerPromptRedactionTest {
    @BeforeEach
    fun clearCustomPatterns() {
        // Redaction is a singleton object: custom patterns set by another test class in the same JVM
        // would bleed in here. Under D-05 custom patterns apply even in OFF, so the byte-identity
        // assertion below is only meaningful with the list provably empty.
        Redaction.setCustomPatterns(emptyList())
    }

    @Test
    fun parameterLineShape_carriesTheMontoyaTypeSuffix() {
        val cookieParam = mock<ParsedHttpParameter>()
        whenever(cookieParam.type()).thenReturn(HttpParameterType.COOKIE)
        whenever(cookieParam.name()).thenReturn("JSESSIONID")
        whenever(cookieParam.value()).thenReturn("8F3A9C2B7E1D4A6F0B5C8E2D")

        val urlParam = mock<ParsedHttpParameter>()
        whenever(urlParam.type()).thenReturn(HttpParameterType.URL)
        whenever(urlParam.name()).thenReturn("q")
        whenever(urlParam.value()).thenReturn("red running shoes")

        // The type suffix comes from the real Montoya enum constant, never a hand-written string —
        // otherwise the test proves nothing about the shape the SC2 redaction rule keys on.
        assertEquals(
            "JSESSIONID=8F3A9C2B7E1D4A6F0B5C8E2D (COOKIE)",
            formatParamLine(cookieParam.name(), truncateWithEllipsis(cookieParam.value(), PARAM_VALUE_MAX_CHARS), cookieParam.type().name),
            "Cookie-typed parameters must render with the type suffix the cookie rule discriminates on",
        )
        assertEquals(
            "q=red running shoes (URL)",
            formatParamLine(urlParam.name(), truncateWithEllipsis(urlParam.value(), PARAM_VALUE_MAX_CHARS), urlParam.type().name),
            "A URL-typed parameter must carry its own suffix — the negative discriminator SC2 depends on",
        )
    }

    @Test
    fun buildScanMetadataText_emitsCookieAndParameterSections() {
        val cookieLines = listOf("JSESSIONID=SEED_A", "abtest_bucket=SEED_B")
        val paramLines = listOf("JSESSIONID=SEED_C (COOKIE)", "q=shoes (URL)")

        val blob = metadataBlob(cookies = cookieLines, params = paramLines)
        val lines = blob.lines()

        // Pins the emitter format that plan 21-05's section-scoped rule keys on.
        assertTrue(lines.contains("=== COOKIES ==="), "The cookie section header must be emitted verbatim on its own line")
        assertTrue(lines.contains("=== PARAMETERS ==="), "The parameter section header must be emitted verbatim on its own line")
        for (line in cookieLines) {
            assertTrue(lines.contains(line), "Cookie line '$line' must be emitted verbatim")
        }
        for (line in paramLines) {
            assertTrue(lines.contains(line), "Parameter line '$line' must be emitted verbatim")
        }
    }

    @Test
    fun redactScanMetadata_offModeIsByteIdentical() {
        val blob =
            metadataBlob(
                requestHeaders = listOf("Host: example.com", "User-Agent: burp-ai-agent-test"),
                cookies = listOf("JSESSIONID=SEED_A"),
                params = listOf("JSESSIONID=SEED_C (COOKIE)"),
            )

        // D-06 relies on the caller-side OFF branch having been behaviour-preserving: with no custom
        // patterns configured, RedactionPolicy.fromMode(OFF) leaves the blob untouched.
        assertEquals(
            blob,
            redactScanMetadata(blob, PrivacyMode.OFF, HOST_SALT),
            "OFF with no custom patterns must return the blob byte-identically",
        )
    }

    @Test
    fun redactScanMetadata_strictAnonymizesHosts() {
        val blob = metadataBlob(requestHeaders = listOf("Host: example.com"))

        val redacted = redactScanMetadata(blob, PrivacyMode.STRICT, HOST_SALT)

        // Proves the extracted seam really reaches Redaction.apply rather than returning its input.
        assertTrue(redacted.contains("Host: host-"), "STRICT must rewrite the Host header to the anonymized form")
        assertFalse(redacted.contains("Host: example.com"), "STRICT must not leave the real host in the Host header")
    }

    // (PRIV-05) SC1, end to end. Asserting only at the Redaction.apply level over a hand-written
    // blob would leave the emitter's exact format unasserted — and that unasserted gap is precisely
    // what let PRIV-05 ship. This test runs the REAL builder output through the REAL redaction step,
    // and asserts per cookie name rather than on the aggregate.
    @Test
    fun emittedCookieSectionValuesAreRedacted_sc1() {
        val cookieValues =
            mapOf(
                "JSESSIONID" to "8F3A9C2B7E1D4A6F0B5C8E2D",
                "PHPSESSID" to "abc123def456",
                "connect.sid" to "s%3ARZxYqL9.opaquevalue",
                "auth_token" to "secretvalue123",
                "csrftoken" to "abcdef",
                // Unreachable by the widened key expression from plan 21-04 — only the
                // section-scoped rule can save it, which is why both mechanisms are kept.
                "abtest_bucket" to "OPAQUE_VALUE_XYZ",
            )

        val blob = metadataBlob(cookies = cookieValues.map { (name, value) -> "$name=$value" })

        for (mode in listOf(PrivacyMode.STRICT, PrivacyMode.BALANCED)) {
            val redacted = redactScanMetadata(blob, mode, HOST_SALT)
            for ((name, value) in cookieValues) {
                assertFalse(
                    redacted.contains(value),
                    "$mode: the emitted value of cookie '$name' must not survive redaction",
                )
                assertTrue(
                    redacted.lines().contains("$name=[REDACTED]"),
                    "$mode: cookie '$name' must keep its name in the emitted blob",
                )
            }
        }
    }

    // (PRIV-05) SC2, end to end. The parameter lines are built through formatParamLine and the REAL
    // HttpParameterType constants, never hand-written strings: the whole point is that the shape the
    // emitter produces and the shape the redaction rule keys on are the same object.
    //
    // abtest_bucket is the decisive line. JSESSIONID alone would pass even with the type-suffix rule
    // unwired, because the key expression from plan 21-04 already redacts it from the leading-field
    // position; an unremarkable cookie name is what the type suffix alone has to save.
    @Test
    fun emittedCookieTypedParametersAreRedacted_sc2() {
        val blob =
            metadataBlob(
                params =
                    listOf(
                        formatParamLine("JSESSIONID", "8F3A9C2B7E1D4A6F0B5C8E2D", HttpParameterType.COOKIE.name),
                        formatParamLine("abtest_bucket", "OPAQUE_PARAM_XYZ", HttpParameterType.COOKIE.name),
                        formatParamLine("q", "red running shoes", HttpParameterType.URL.name),
                        formatParamLine("quantity", "2", HttpParameterType.BODY.name),
                    ),
            )

        for (mode in listOf(PrivacyMode.STRICT, PrivacyMode.BALANCED)) {
            val lines = redactScanMetadata(blob, mode, HOST_SALT).lines()

            assertTrue(
                lines.contains("JSESSIONID=[REDACTED] (COOKIE)"),
                "$mode: an emitted COOKIE-typed parameter keeps its name and its Montoya type suffix",
            )
            assertTrue(
                lines.contains("abtest_bucket=[REDACTED] (COOKIE)"),
                "$mode: the type suffix alone must save an unremarkably-named cookie parameter",
            )
            assertFalse(
                lines.any { it.contains("8F3A9C2B7E1D4A6F0B5C8E2D") || it.contains("OPAQUE_PARAM_XYZ") },
                "$mode: no emitted COOKIE-typed parameter value may reach the backend",
            )
            assertTrue(
                lines.contains("q=red running shoes (URL)"),
                "$mode: a URL-typed parameter line must survive byte-for-byte",
            )
            assertTrue(
                lines.contains("quantity=2 (BODY)"),
                "$mode: a BODY-typed parameter line must survive byte-for-byte",
            )
        }
    }

    // (PRIV-05 / T-21-20) The parity half of the shared-constant coupling, in the spirit of
    // McpToolParityTest.registeredToolIds_matchCatalog. The constant makes a RENAME of the section a
    // compile error; this assertion makes a silent FORMAT CHANGE around it a test failure, which is
    // the other way a section-scoped security control can be disabled without anyone noticing.
    @Test
    fun emittedBlobContainsTheSectionConstant_parity() {
        val blob = metadataBlob(cookies = listOf("JSESSIONID=SEED_A"))

        // Line-exact rather than String.contains, so a future edit that merges the header onto
        // another line fails here instead of passing on a substring match.
        assertTrue(
            blob.lines().contains(Redaction.COOKIE_SECTION_HEADER),
            "The emitted blob must carry the section header the redaction rule keys on, on its own line",
        )
    }

    private fun metadataBlob(
        requestHeaders: List<String> = listOf("User-Agent: burp-ai-agent-test"),
        cookies: List<String> = emptyList(),
        params: List<String> = emptyList(),
    ): String =
        buildScanMetadataText(
            kbSummary = null,
            displayUrl = "http://example.com/search",
            urlPath = "/search",
            method = "GET",
            statusCode = 200,
            mimeType = "HTML",
            potentialIds = emptyList(),
            requestHeaders = requestHeaders,
            responseHeaders = listOf("Content-Type: text/html"),
            authHeaders = emptyList(),
            cookies = cookies,
            params = params,
            requestBody = "",
            responseBody = "",
        )
}
