package com.six2dez.burp.aiagent.mcp.tools

import burp.api.montoya.MontoyaApi
import burp.api.montoya.core.BurpSuiteEdition
import burp.api.montoya.http.message.HttpHeader
import com.six2dez.burp.aiagent.mcp.McpRequestLimiter
import com.six2dez.burp.aiagent.mcp.McpToolContext
import com.six2dez.burp.aiagent.redact.PrivacyMode
import com.six2dez.burp.aiagent.redact.Redaction
import com.six2dez.burp.aiagent.redact.RedactionPolicy
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.Answers
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.nio.file.Path

/**
 * Behavioural tests for the pure helpers in `McpToolHelpers.kt`.
 *
 * Phase 26 plan 26-02 (QUAL-06 / SC2). The four helpers covered here carry privacy and trust
 * decisions on the MCP tool-RESULT path, which is a second redaction path independent of
 * `Redaction.redact`:
 *
 *  - `sanitizeHeaders` — the header redactor (T-26-02-01).
 *  - `maybeAnonymizeUrl` — STRICT-mode host anonymisation of tool-result URLs (T-26-02-02).
 *  - `resolveReportPath` — the containment check between a model-supplied report path and an
 *    arbitrary filesystem write location (T-26-02-03).
 *  - `applyReplacements` — the payload substitution used by the repeater payload tools.
 *
 * Header fixtures are mockito mocks rather than `HttpHeader.httpHeader(...)`: the Montoya static
 * factory dereferences `burp.api.montoya.internal.ObjectFactoryLocator.FACTORY`, which is null
 * outside the Burp runtime, so the factory NPEs in a unit test. This mirrors the existing
 * `InjectionPointExtractorTest`.
 */
class McpToolHelpersTest {
    // ── sanitizeHeaders ──────────────────────────────────────────────────────────────────

    @Nested
    inner class SanitizeHeaders {
        @Test
        fun strictModeMatchesItsRedactionPolicy() {
            assertHeadersMatchPolicy(PrivacyMode.STRICT)
        }

        @Test
        fun balancedModeMatchesItsRedactionPolicy() {
            assertHeadersMatchPolicy(PrivacyMode.BALANCED)
        }

        @Test
        fun offModeMatchesItsRedactionPolicyAndPassesEveryHeaderThrough() {
            assertHeadersMatchPolicy(PrivacyMode.OFF)
        }

        @Test
        fun headerNameMatchingIsCaseInsensitive() {
            // PRIV-05 was a matcher that missed a real-world header spelling. Upper-case
            // `COOKIE:` / `AUTHORIZATION:` / `HOST:` are legal on the wire and must be caught.
            val salt = "case-insensitive-salt"
            val headers =
                listOf(
                    stubHeader("COOKIE", "sid=abc"),
                    stubHeader("SET-COOKIE", "sid=abc; Path=/"),
                    stubHeader("AUTHORIZATION", "Bearer token"),
                    stubHeader("Proxy-AUTHORIZATION", "Basic dXNlcg=="),
                    stubHeader("x-api-KEY", "k1"),
                    stubHeader("API-key", "k2"),
                    stubHeader("HOST", "api.example.com"),
                )

            val sanitized = sanitizeHeaders(headers, contextWith(PrivacyMode.STRICT, salt))

            assertEquals(stripped, sanitized["COOKIE"])
            assertEquals(stripped, sanitized["SET-COOKIE"])
            assertEquals(redacted, sanitized["AUTHORIZATION"])
            assertEquals(redacted, sanitized["Proxy-AUTHORIZATION"])
            assertEquals(redacted, sanitized["x-api-KEY"])
            assertEquals(redacted, sanitized["API-key"])
            assertEquals(Redaction.anonymizeHost("api.example.com", salt), sanitized["HOST"])
        }

        @Test
        fun preservesOriginalHeaderNameCasingAndInputOrder() {
            // Sanitisation must not silently rewrite the header block it is describing.
            val headers =
                listOf(
                    stubHeader("HoSt", "api.example.com"),
                    stubHeader("CoOkIe", "sid=abc"),
                    stubHeader("X-Custom-Header", "kept"),
                    stubHeader("AuThOrIzAtIoN", "Bearer token"),
                )

            val sanitized = sanitizeHeaders(headers, contextWith(PrivacyMode.STRICT, "order-salt"))

            assertEquals(
                listOf("HoSt", "CoOkIe", "X-Custom-Header", "AuThOrIzAtIoN"),
                sanitized.keys.toList(),
                "sanitizeHeaders must preserve original header-name casing and input order",
            )
        }

        @Test
        fun ordinaryHeadersAreNeverModifiedInAnyMode() {
            PrivacyMode.entries.forEach { mode ->
                val sanitized =
                    sanitizeHeaders(
                        listOf(stubHeader("Accept", "application/json")),
                        contextWith(mode, "ordinary-salt"),
                    )
                assertEquals("application/json", sanitized["Accept"], "mode=$mode")
            }
        }

        @Test
        fun emptyHeaderListYieldsEmptyMap() {
            val sanitized = sanitizeHeaders(emptyList(), contextWith(PrivacyMode.STRICT, "empty-salt"))

            assertTrue(sanitized.isEmpty())
        }

        /**
         * Derives the expectation from `RedactionPolicy.fromMode` rather than re-encoding the
         * mode-to-flag table, so a change to the policy table moves this assertion with it
         * instead of leaving a stale copy behind.
         */
        private fun assertHeadersMatchPolicy(mode: PrivacyMode) {
            val salt = "policy-salt-$mode"
            val policy = RedactionPolicy.fromMode(mode)
            val host = "api.example.com"
            val headers =
                listOf(
                    stubHeader("Cookie", "sid=abc"),
                    stubHeader("Set-Cookie", "sid=abc; Path=/"),
                    stubHeader("Authorization", "Bearer token"),
                    stubHeader("Proxy-Authorization", "Basic dXNlcg=="),
                    stubHeader("X-API-Key", "k1"),
                    stubHeader("Api-Key", "k2"),
                    stubHeader("Host", host),
                    stubHeader("Accept", "application/json"),
                )

            val sanitized = sanitizeHeaders(headers, contextWith(mode, salt))

            assertEquals(
                if (policy.stripCookies) stripped else "sid=abc",
                sanitized["Cookie"],
                "Cookie under $mode",
            )
            assertEquals(
                if (policy.stripCookies) stripped else "sid=abc; Path=/",
                sanitized["Set-Cookie"],
                "Set-Cookie under $mode",
            )
            assertEquals(
                if (policy.redactTokens) redacted else "Bearer token",
                sanitized["Authorization"],
                "Authorization under $mode",
            )
            assertEquals(
                if (policy.redactTokens) redacted else "Basic dXNlcg==",
                sanitized["Proxy-Authorization"],
                "Proxy-Authorization under $mode",
            )
            assertEquals(
                if (policy.redactTokens) redacted else "k1",
                sanitized["X-API-Key"],
                "X-API-Key under $mode",
            )
            assertEquals(
                if (policy.redactTokens) redacted else "k2",
                sanitized["Api-Key"],
                "Api-Key under $mode",
            )
            assertEquals(
                if (policy.anonymizeHosts) Redaction.anonymizeHost(host, salt) else host,
                sanitized["Host"],
                "Host under $mode",
            )
            assertEquals("application/json", sanitized["Accept"], "ordinary header under $mode")
        }
    }

    // ── maybeAnonymizeUrl ────────────────────────────────────────────────────────────────

    @Nested
    inner class MaybeAnonymizeUrl {
        @Test
        fun returnsInputUnchangedOutsideStrict() {
            val url = "https://api.example.com:8443/v1/items?q=1&p=2#frag"

            listOf(PrivacyMode.BALANCED, PrivacyMode.OFF).forEach { mode ->
                assertEquals(url, maybeAnonymizeUrl(url, contextWith(mode, "non-strict-salt")), "mode=$mode")
            }
        }

        @Test
        fun strictReplacesTheHostAndPreservesSchemePortPathQueryAndFragment() {
            val salt = "anon-url-salt"
            val anonHost = Redaction.anonymizeHost("api.example.com", salt)

            val result =
                maybeAnonymizeUrl(
                    "https://api.example.com:8443/v1/items?q=1&p=2#frag",
                    contextWith(PrivacyMode.STRICT, salt),
                )

            assertEquals("https://$anonHost:8443/v1/items?q=1&p=2#frag", result)
        }

        @Test
        fun strictLeavesAnUnparseableStringUnchanged() {
            // A malformed URL must fall back to the input rather than throwing into the tool result.
            val malformed = "not a url at all"

            assertEquals(malformed, maybeAnonymizeUrl(malformed, contextWith(PrivacyMode.STRICT, "malformed-salt")))
        }

        @Test
        fun strictLeavesAUriWithoutAHostUnchanged() {
            val hostless = "mailto:someone@example.com"

            assertEquals(hostless, maybeAnonymizeUrl(hostless, contextWith(PrivacyMode.STRICT, "hostless-salt")))
        }
    }

    // ── resolveReportPath ────────────────────────────────────────────────────────────────

    @Nested
    inner class ResolveReportPath {
        @Test
        fun plainRelativeNameResolvesUnderUserHome() {
            val home = userHome()

            val resolved = resolveReportPath("scan-report.html")

            assertEquals(home.resolve("scan-report.html").normalize(), resolved)
            assertTrue(resolved.startsWith(home))
        }

        @Test
        fun nestedRelativePathResolvesUnderUserHomeAndIsTrimmed() {
            val home = userHome()

            val resolved = resolveReportPath("  reports/scan.html  ")

            assertEquals(home.resolve("reports/scan.html").normalize(), resolved)
        }

        @Test
        fun absolutePathAlreadyUnderUserHomeIsReturnedNormalised() {
            val home = userHome()
            val raw =
                home
                    .resolve("reports")
                    .resolve("..")
                    .resolve("reports/scan.html")

            val resolved = resolveReportPath(raw.toString())

            assertEquals(home.resolve("reports/scan.html").normalize(), resolved)
        }

        @Test
        fun relativeParentSegmentsThatEscapeUserHomeAreRejected() {
            // T-26-02-03: the containment check is the ONLY thing between a model-supplied path
            // and an arbitrary write location. Asserted as a REJECTION, not only as an acceptance.
            val error =
                assertThrows(IllegalArgumentException::class.java) {
                    resolveReportPath("../gsd-26-02-escape/report.html")
                }

            assertTrue(
                error.message!!.contains("must be under"),
                "expected the containment rejection message; got: ${error.message}",
            )
        }

        @Test
        fun deeplyNestedParentSegmentsThatEscapeUserHomeAreRejected() {
            assertThrows(IllegalArgumentException::class.java) {
                resolveReportPath("../../../../../../../../etc/gsd-26-02-passwd")
            }
        }

        @Test
        fun absolutePathOutsideUserHomeIsRejected() {
            // Derived from the real home at runtime: a SIBLING of the home directory can never be
            // under it, whatever the machine's home layout is. Nothing is created on disk.
            val outside =
                userHome()
                    .parent
                    .resolve("gsd-26-02-outside-home")
                    .resolve("report.html")

            assertThrows(IllegalArgumentException::class.java) {
                resolveReportPath(outside.toString())
            }
        }

        @Test
        fun blankPathIsRejected() {
            val error =
                assertThrows(IllegalArgumentException::class.java) {
                    resolveReportPath("   ")
                }

            assertEquals("Report path is empty", error.message)
        }

        private fun userHome(): Path =
            Path
                .of(System.getProperty("user.home"))
                .normalize()
    }

    // ── applyReplacements ────────────────────────────────────────────────────────────────

    @Nested
    inner class ApplyReplacements {
        @Test
        fun emptyMapReturnsTheInputInstanceUnchanged() {
            val content = "GET /a HTTP/1.1"

            assertSame(content, applyReplacements(content, emptyMap()))
        }

        @Test
        fun singleMappingIsApplied() {
            assertEquals(
                "GET /admin HTTP/1.1",
                applyReplacements("GET §PATH§ HTTP/1.1", mapOf("§PATH§" to "/admin")),
            )
        }

        @Test
        fun everyMappingIsApplied() {
            val result =
                applyReplacements(
                    "§METHOD§ §PATH§ HTTP/1.1\r\nHost: §HOST§",
                    linkedMapOf("§METHOD§" to "POST", "§PATH§" to "/login", "§HOST§" to "example.com"),
                )

            assertEquals("POST /login HTTP/1.1\r\nHost: example.com", result)
        }

        @Test
        fun everyOccurrenceOfAKeyIsReplaced() {
            assertEquals("bbb", applyReplacements("aaa", mapOf("a" to "b")))
        }

        @Test
        fun aMappingWhoseKeyIsAbsentLeavesContentUnchanged() {
            val content = "GET /a HTTP/1.1"

            assertEquals(content, applyReplacements(content, mapOf("§MISSING§" to "x")))
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────────────────

    private val stripped = "[STRIPPED]"
    private val redacted = "[REDACTED]"

    private fun stubHeader(
        name: String,
        value: String,
    ): HttpHeader {
        val header = mock<HttpHeader>()
        whenever(header.name()).thenReturn(name)
        whenever(header.value()).thenReturn(value)
        return header
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
}
