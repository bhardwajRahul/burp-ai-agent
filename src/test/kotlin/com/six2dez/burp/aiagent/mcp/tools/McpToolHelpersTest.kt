package com.six2dez.burp.aiagent.mcp.tools

import burp.api.montoya.MontoyaApi
import burp.api.montoya.core.BurpSuiteEdition
import burp.api.montoya.core.HighlightColor
import burp.api.montoya.http.message.HttpHeader
import burp.api.montoya.scanner.BuiltInAuditConfiguration
import com.six2dez.burp.aiagent.mcp.McpRequestLimiter
import com.six2dez.burp.aiagent.mcp.McpToolContext
import com.six2dez.burp.aiagent.redact.PrivacyMode
import com.six2dez.burp.aiagent.redact.Redaction
import com.six2dez.burp.aiagent.redact.RedactionPolicy
import kotlinx.serialization.encodeToString
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceLock
import org.junit.jupiter.api.parallel.Resources
import org.mockito.Answers
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.nio.file.Path
import java.util.Locale

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
    private var localeAtStart: Locale = Locale.getDefault()

    @BeforeEach
    fun captureDefaultLocale() {
        localeAtStart = Locale.getDefault()
    }

    @AfterEach
    fun defaultLocaleIsRestored() {
        // cookieNameMatchingSurvivesATurkishDefaultLocale swaps the JVM-wide default. Asserting the
        // restore here — rather than trusting the `finally` inside that one test — is what stops a
        // Turkish default leaking into every later test class in the shared JVM if that test is ever
        // edited carelessly. JUnit parallel execution is not enabled in this project, so the swap is
        // safe; @ResourceLock(Resources.LOCALE) on that test is insurance against that changing.
        assertEquals(localeAtStart, Locale.getDefault(), "the JVM default locale must be restored")
    }

    @BeforeEach
    fun clearCustomPatterns() {
        // Redaction is a singleton object: custom patterns left behind by another test class in the
        // same JVM would bleed in here and could remove a sentinel for the wrong reason, letting an
        // absence assertion pass without the cookie rule doing anything. The precedent (and the same
        // reasoning) is PassiveAiScannerPromptRedactionTest.
        Redaction.setCustomPatterns(emptyList())
    }

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
         * (PRIV-05) Phase 27 Task 1 — the requirement stated as an OUTCOME on the real flow:
         * `sanitizeHeaders` -> `toolJson.encodeToString(ParsedRequest)` -> `McpToolContext.redactIfNeeded`.
         *
         * Asserting on the FINAL string locks the result independently of which layer strips it, so a
         * future narrowing of `sanitizeHeaders` turns this red without the test caring which layer was
         * supposed to catch it.
         *
         * FIXTURE REACHABILITY, mirroring `RedactionTest.cookieHeaderNameVariantsAreStripped`: the
         * sentinel is a bare lowercase alphabetic word with no '=', no quotes, no `Bearer`/`Basic`/`eyJ`
         * prefix and no dotted segment, and its JSON key `X-Cookie` carries no word from
         * `SENSITIVE_WORDS` — so a cookie header rule is the only thing in the pipeline that can
         * remove it, and an absence assertion here cannot pass for some other rule's reason.
         */
        @Test
        fun cookieVariantsAreStrippedEndToEndThroughRedactIfNeeded() {
            val context = contextWith(PrivacyMode.STRICT, "end-to-end-salt")
            val rawHeaders =
                listOf(
                    stubHeader("X-Cookie", "sentinelxrayninezulu"),
                    stubHeader("Authorization", "Bearer sentineltokenoscarwhisky"),
                    stubHeader("Host", "api.example.com"),
                    stubHeader("X-Request-Id", "benignidcontrolvalue"),
                )

            val sanitizedJson = toolJson.encodeToString(parsedRequestOf(sanitizeHeaders(rawHeaders, context)))
            val finalText = context.redactIfNeeded(sanitizedJson)

            assertFalse(
                finalText.contains("sentinelxrayninezulu"),
                "the X-Cookie value must not survive the tool-result flow (got: $finalText)",
            )
            assertTrue(
                finalText.contains("X-Cookie"),
                "the header NAME must survive — only the VALUE is replaced (T-21-WA2) (got: $finalText)",
            )
            assertTrue(
                finalText.contains("benignidcontrolvalue"),
                "negative control: this must be stripping, not blanket header loss (got: $finalText)",
            )

            // AR-27-01 / AR-27-02, measured rather than assumed. The SAME STRICT context, handed the
            // RAW header list that never passed through sanitizeHeaders, CANNOT recover the cookie:
            // cookieHeaderRegex/setCookieHeaderRegex are line-anchored `(?im)^...$` while toolJson
            // emits single-line JSON, and `cookie` is absent from SENSITIVE_WORDS so jsonSecretKeyRegex
            // never fires on the key either. That is what makes sanitizeHeaders the LAST line of
            // defence on this path rather than the first of two.
            val rawJson = toolJson.encodeToString(parsedRequestOf(rawHeaders.associate { it.name() to it.value() }))
            val rawFinalText = context.redactIfNeeded(rawJson)

            assertTrue(
                rawFinalText.contains("sentinelxrayninezulu"),
                "pinned: redactIfNeeded under the strongest privacy mode cannot recover a header " +
                    "sanitizeHeaders missed (got: $rawFinalText)",
            )
            // Non-vacuity guard on the pin above: the very same call DID transform its input, so the
            // pin cannot be passing because redactIfNeeded silently no-opped on a wrong policy or a
            // wrong context. bearerRegex is NOT line-anchored, so it is the transformation that
            // actually fires on single-line JSON under a token-redacting policy.
            assertFalse(
                rawFinalText.contains("sentineltokenoscarwhisky"),
                "redactIfNeeded must really have run under a redacting policy (got: $rawFinalText)",
            )
            // Measured, and the reason the guard above does NOT use the host: hostHeaderRegex is
            // line-anchored too, so STRICT host anonymisation cannot fire on single-line JSON either.
            // This is AR-27-01 shown a second time, on a second rule.
            assertTrue(
                rawFinalText.contains("api.example.com"),
                "measured AR-27-01: the line-anchored host rule cannot fire on single-line JSON " +
                    "(got: $rawFinalText)",
            )
        }

        /**
         * (PRIV-05) The full variant matrix on the tool-result path, using the SAME seven names as
         * `RedactionTest.cookieHeaderNameVariantsAreStripped` so the two suites are comparable by eye.
         *
         * Each fixture carries its OWN sentinel. That is what makes a partial fix visible: with one
         * shared value a matcher that catches six of seven still passes, which is the exact class of
         * miss this phase exists to repair.
         */
        @Test
        fun cookieHeaderNameVariantsAreStrippedOnTheToolResultPath() {
            listOf(PrivacyMode.STRICT, PrivacyMode.BALANCED).forEach { mode ->
                val sanitized =
                    sanitizeHeaders(cookieVariantHeaders(), contextWith(mode, "variant-salt-$mode"))

                cookieVariants.forEach { (name, _) ->
                    assertEquals(
                        stripped,
                        sanitized[name],
                        "$mode: $name must be stripped under its OWN name, not renamed (T-21-WA2)",
                    )
                }
                assertEquals(
                    "benignidcontrolvalue",
                    sanitized["X-Request-Id"],
                    "$mode: a header with no cookie token must survive byte-identical",
                )
            }
        }

        @Test
        fun offModePassesEveryCookieNameVariantThrough() {
            // OFF is the user's explicit, pre-flight choice to see raw traffic. Overriding it with
            // "extra safety" would be a betrayal of the privacy UI, not a hardening.
            val sanitized =
                sanitizeHeaders(cookieVariantHeaders(), contextWith(PrivacyMode.OFF, "off-salt"))

            cookieVariants.forEach { (name, sentinel) ->
                assertEquals(sentinel, sanitized[name], "OFF must pass $name through unchanged")
            }
            assertEquals("benignidcontrolvalue", sanitized["X-Request-Id"])
        }

        /**
         * The ACCEPTED cost of D-27-02, asserted by name so it is a recorded decision in the suite
         * rather than a surprise in the field.
         *
         * `Cookie-Consent` and `X-Cookie-Policy` carry no cookie, and their values are stripped
         * anyway. This is the same cost the prompt path already accepts (T-21-WA3): any `*cookie*`
         * header is cookie-bearing by convention, and only the VALUE is removed. A tighter rule was
         * rejected because it would have to change on BOTH paths at once or the divergence reopens
         * immediately — which is precisely the failure being repaired here.
         */
        @Test
        fun headersMerelyContainingCookieAreStrippedByDesign() {
            val sanitized =
                sanitizeHeaders(
                    listOf(
                        stubHeader("Cookie-Consent", "analytics-declined"),
                        stubHeader("X-Cookie-Policy", "strictly-necessary"),
                    ),
                    contextWith(PrivacyMode.STRICT, "over-match-salt"),
                )

            assertEquals(stripped, sanitized["Cookie-Consent"])
            assertEquals(stripped, sanitized["X-Cookie-Policy"])
        }

        @Test
        fun benignHeaderNamesWithoutTheTokenSurvive() {
            // The predicate is a substring test on the WHOLE token, so a name that merely shares
            // letters with it is not caught. This is the boundary of the accepted over-match above.
            val sanitized =
                sanitizeHeaders(
                    listOf(
                        stubHeader("X-Cook", "roast"),
                        stubHeader("Cook-ie", "split-token"),
                        stubHeader("Accept", "application/json"),
                        stubHeader("X-Request-Id", "benignidcontrolvalue"),
                    ),
                    contextWith(PrivacyMode.STRICT, "benign-salt"),
                )

            assertEquals("roast", sanitized["X-Cook"])
            assertEquals("split-token", sanitized["Cook-ie"])
            assertEquals("application/json", sanitized["Accept"])
            assertEquals("benignidcontrolvalue", sanitized["X-Request-Id"])
        }

        /**
         * (PRIV-05) The `encoding` edge: header-name matching lowercases ASCII and must not depend on
         * the ambient JVM default locale.
         *
         * MEASURED BOUND, recorded so this test is not read as proving more than it does. Kotlin's
         * no-argument `String.lowercase()` is ALREADY locale-agnostic — it compiles to
         * `toLowerCase(Locale.ROOT)` — so under a `tr-TR` default `"COOKIE".lowercase()` yields
         * `cookie`, and this test would pass even with the explicit `Locale.ROOT` argument removed.
         * The dotless-i hazard belongs to the JAVA spelling: under the same default,
         * `"COOKIE".toLowerCase()` yields `cookıe`. So what this test actually guards is a future
         * switch to a locale-SENSITIVE spelling, and the first assertion below states the JVM's
         * behaviour explicitly so a reader can see which of the two cases they are in.
         *
         * All three comparisons are asserted, not only the cookie one: `sanitizeHeaders` computes a
         * single lowered name and feeds it to the cookie test, the token-header lookup and the host
         * compare, so the locale question cannot be answered for one of them alone (D-27-04).
         */
        @Test
        @ResourceLock(Resources.LOCALE)
        fun cookieNameMatchingSurvivesATurkishDefaultLocale() {
            val previous = Locale.getDefault()
            val salt = "turkish-salt"
            val host = "api.example.com"
            try {
                Locale.setDefault(Locale.forLanguageTag("tr-TR"))

                // Non-vacuity probe, asserted rather than assumed. This records WHICH hazard this JVM
                // exhibits: Kotlin's lowercase() is locale-agnostic, Java's toLowerCase() is not.
                assertTrue(
                    "COOKIE".lowercase().contains("cookie"),
                    "measured: Kotlin lowercase() is locale-agnostic, so the tr-TR assertions below " +
                        "are a guard against a future locale-sensitive spelling, not proof of a " +
                        "hazard this spelling exhibits",
                )
                assertFalse(
                    ("COOKIE" as java.lang.String).toLowerCase().contains("cookie"),
                    "measured: the dotless-i hazard is real for the JAVA spelling under tr-TR — that " +
                        "is the edit this test exists to catch",
                )

                val sanitized =
                    sanitizeHeaders(
                        listOf(
                            stubHeader("X-COOKIE", "sentinelturkishcookie"),
                            stubHeader("SET-COOKIE", "sentinelturkishsetcookie"),
                            stubHeader("AUTHORIZATION", "Bearer sentinelturkishtoken"),
                            stubHeader("HOST", host),
                        ),
                        contextWith(PrivacyMode.STRICT, salt),
                    )

                assertEquals(stripped, sanitized["X-COOKIE"])
                assertEquals(stripped, sanitized["SET-COOKIE"])
                assertEquals(redacted, sanitized["AUTHORIZATION"])
                assertEquals(Redaction.anonymizeHost(host, salt), sanitized["HOST"])
            } finally {
                // Restored in `finally`, not at the end of the happy path: a failed assertion above
                // must not leave a Turkish default locale behind for every later test class in the
                // shared JVM.
                Locale.setDefault(previous)
            }
        }

        @Test
        fun emptyValuedCookieHeaderIsStillRedacted() {
            // The `empty` edge. An empty-but-PRESENT value is never passed through as-is: the header
            // is reported as stripped, so the analyst sees that a cookie header was there at all.
            // `emptyHeaderListYieldsEmptyMap` above covers the other half of the same edge.
            val sanitized =
                sanitizeHeaders(
                    listOf(stubHeader("Set-Cookie", "")),
                    contextWith(PrivacyMode.STRICT, "empty-value-salt"),
                )

            assertEquals(stripped, sanitized["Set-Cookie"])
        }

        // The five measured variant names plus the two canonical ones, each with its OWN sentinel.
        // Same seven names as RedactionTest.cookieHeaderNameVariantsAreStripped, and the sentinel
        // scheme continues that test's (sentinelalphaone … sentinelechofive).
        private val cookieVariants =
            listOf(
                "Cookie" to "sentinelfoxtrotsix",
                "Set-Cookie" to "sentinelgolfseven",
                "Cookie2" to "sentinelalphaone",
                "X-Cookie" to "sentinelbravotwo",
                "Set-Cookie2" to "sentinelcharliethree",
                "X-Original-Cookie" to "sentineldeltafour",
                "X-Forwarded-Cookie" to "sentinelechofive",
            )

        private fun cookieVariantHeaders(): List<HttpHeader> =
            cookieVariants.map { (name, sentinel) -> stubHeader(name, sentinel) } +
                stubHeader("X-Request-Id", "benignidcontrolvalue")

        // Minimal ParsedRequest envelope for the end-to-end assertions: only `headers` varies, so
        // every other field is fixed here instead of being repeated at each call site.
        private fun parsedRequestOf(headers: Map<String, String>): ParsedRequest =
            ParsedRequest(
                method = "GET",
                path = "/",
                url = "https://api.example.com/",
                headers = headers,
                parameters = emptyList(),
                body = null,
                bodyLength = 0,
            )

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

    // ── withAiIssuePrefix ────────────────────────────────────────────────────────────────

    @Nested
    inner class WithAiIssuePrefix {
        @Test
        fun anUnprefixedNameGainsThePrefix() {
            assertEquals("[AI] SQL injection", withAiIssuePrefix("SQL injection"))
        }

        @Test
        fun aNameAlreadyCarryingEitherRecognisedPrefixIsReturnedUnchanged() {
            assertEquals("[AI] SQL injection", withAiIssuePrefix("[AI] SQL injection"))
            assertEquals("[AI Passive] SQL injection", withAiIssuePrefix("[AI Passive] SQL injection"))
        }

        @Test
        fun prefixRecognitionIsCaseInsensitive() {
            assertEquals("[ai] SQL injection", withAiIssuePrefix("[ai] SQL injection"))
            assertEquals("[AI PASSIVE] SQL injection", withAiIssuePrefix("[AI PASSIVE] SQL injection"))
        }

        @Test
        fun surroundingWhitespaceIsTrimmedOnBothBranches() {
            assertEquals("[AI] SQL injection", withAiIssuePrefix("   SQL injection   "))
            assertEquals("[AI] SQL injection", withAiIssuePrefix("  [AI] SQL injection  "))
        }
    }

    // ── truncateIfNeeded ─────────────────────────────────────────────────────────────────

    @Nested
    inner class TruncateIfNeeded {
        @Test
        fun aPayloadUnderTheLimitIsReturnedIdentically() {
            val payload = "short body"

            assertSame(payload, truncateIfNeeded(payload, 1024))
        }

        @Test
        fun aPayloadExactlyAtTheLimitIsReturnedIdentically() {
            val payload = "12345"

            assertSame(payload, truncateIfNeeded(payload, payload.toByteArray(Charsets.UTF_8).size))
        }

        @Test
        fun aPayloadOverTheLimitNamesTheOriginalByteCountAndTheLimit() {
            val payload = "abcdefghij"

            val truncated = truncateIfNeeded(payload, 4)

            assertTrue(truncated.startsWith("abcd"), "expected the first 4 bytes; got: $truncated")
            assertTrue(
                truncated.endsWith("... (truncated 10 bytes to 4 bytes)"),
                "suffix must name both the original byte count and the limit; got: $truncated",
            )
        }

        @Test
        fun theBoundIsOnBytesNotCharacters() {
            // Three 2-byte characters: 3 chars but 6 bytes. A char-length bound with limit 5
            // would return the input untouched; the byte bound must truncate it.
            val payload = "ééé"

            assertEquals(3, payload.length)
            assertEquals(6, payload.toByteArray(Charsets.UTF_8).size)

            val truncated = truncateIfNeeded(payload, 5)

            assertTrue(
                truncated.endsWith("... (truncated 6 bytes to 5 bytes)"),
                "a multi-byte payload must be bounded on bytes; got: $truncated",
            )
        }

        @Test
        fun aNonPositiveLimitIsCoercedUpToOneRatherThanProducingAnEmptyResult() {
            listOf(0, -1).forEach { limit ->
                val truncated = truncateIfNeeded("abc", limit)

                assertTrue(truncated.startsWith("a"), "limit=$limit must keep one byte; got: $truncated")
                assertTrue(
                    truncated.endsWith("... (truncated 3 bytes to 1 bytes)"),
                    "limit=$limit must be coerced up to 1; got: $truncated",
                )
            }
        }
    }

    // ── ensureAllowedProxyHistoryCount ───────────────────────────────────────────────────

    @Nested
    inner class EnsureAllowedProxyHistoryCount {
        @Test
        fun aRequestBelowTheLimitIsAllowed() {
            ensureAllowedProxyHistoryCount(1, 50)
        }

        @Test
        fun aRequestExactlyAtTheLimitIsAllowed() {
            ensureAllowedProxyHistoryCount(50, 50)
        }

        @Test
        fun aRequestAboveTheLimitIsRejectedAndTheMessageNamesBothCounts() {
            val error =
                assertThrows(IllegalArgumentException::class.java) {
                    ensureAllowedProxyHistoryCount(51, 50)
                }

            assertTrue(error.message!!.contains("51"), "message must name the requested count; got: ${error.message}")
            assertTrue(error.message!!.contains("50"), "message must name the allowed count; got: ${error.message}")
        }
    }

    // ── orderedProxyHistory ──────────────────────────────────────────────────────────────

    @Nested
    inner class OrderedProxyHistory {
        @Test
        fun determinismModeSortsByTheSuppliedKeyRegardlessOfInputOrder() {
            val items = listOf("c", "a", "b")

            val ordered =
                orderedProxyHistory(items, historyContext(determinism = true, newestFirst = false)) { it }.toList()

            assertEquals(listOf("a", "b", "c"), ordered)
        }

        @Test
        fun determinismOffWithNewestFirstReversesTheInput() {
            val items = listOf("c", "a", "b")

            val ordered =
                orderedProxyHistory(items, historyContext(determinism = false, newestFirst = true)) { it }.toList()

            assertEquals(listOf("b", "a", "c"), ordered)
        }

        @Test
        fun determinismOffWithNewestFirstUnsetPreservesInputOrder() {
            val items = listOf("c", "a", "b")

            val ordered =
                orderedProxyHistory(items, historyContext(determinism = false, newestFirst = false)) { it }.toList()

            assertEquals(items, ordered)
        }
    }

    // ── decodeJwt ────────────────────────────────────────────────────────────────────────

    @Nested
    inner class DecodeJwt {
        @Test
        fun aThreePartTokenYieldsHeaderPayloadAndSignatureLines() {
            val token = "${b64("""{"alg":"HS256"}""")}.${b64("""{"sub":"1234"}""")}.c2ln"

            val decoded = decodeJwt(token)

            assertEquals(
                """
                header={"alg":"HS256"}
                payload={"sub":"1234"}
                signature=c2ln
                """.trimIndent(),
                decoded,
            )
        }

        @Test
        fun aTwoPartTokenYieldsAnEmptySignatureLine() {
            val token = "${b64("""{"alg":"none"}""")}.${b64("""{"sub":"1234"}""")}"

            val decoded = decodeJwt(token)

            assertTrue(decoded.endsWith("signature="), "expected an empty signature line; got: $decoded")
        }

        @Test
        fun aOnePartTokenYieldsTheFixedInvalidMessage() {
            assertEquals("Invalid JWT: expected header.payload.signature", decodeJwt("notajwt"))
        }

        @Test
        fun anEmptyTokenYieldsTheFixedInvalidMessage() {
            assertEquals("Invalid JWT: expected header.payload.signature", decodeJwt(""))
        }

        @Test
        fun segmentsThatAreNotValidBase64UrlYieldPlaceholdersWithoutThrowing() {
            val decoded = decodeJwt("!!!.???.sig")

            assertTrue(decoded.contains("header=<invalid header>"), "got: $decoded")
            assertTrue(decoded.contains("payload=<invalid payload>"), "got: $decoded")
        }

        private fun b64(raw: String): String =
            java.util.Base64
                .getUrlEncoder()
                .withoutPadding()
                .encodeToString(raw.toByteArray(Charsets.UTF_8))
    }

    // ── normalizeHashAlgorithm ───────────────────────────────────────────────────────────

    @Nested
    inner class NormalizeHashAlgorithm {
        @Test
        fun eachShorthandSpellingMapsToItsHyphenatedForm() {
            assertEquals("SHA-1", normalizeHashAlgorithm("sha1"))
            assertEquals("SHA-256", normalizeHashAlgorithm("sha256"))
            assertEquals("SHA-512", normalizeHashAlgorithm("sha512"))
        }

        @Test
        fun anAlreadyHyphenatedValuePassesThrough() {
            assertEquals("SHA-256", normalizeHashAlgorithm("SHA-256"))
            assertEquals("SHA-256", normalizeHashAlgorithm("sha-256"))
        }

        @Test
        fun anUnknownValueIsUpperCasedAndReturned() {
            assertEquals("MD5", normalizeHashAlgorithm("md5"))
        }

        @Test
        fun surroundingWhitespaceIsTrimmed() {
            assertEquals("SHA-256", normalizeHashAlgorithm("  sha256  "))
        }
    }

    // ── diffLines ────────────────────────────────────────────────────────────────────────

    @Nested
    inner class DiffLines {
        @Test
        fun identicalInputsProduceOnlyContextLines() {
            val diff = diffLines("GET /a\r\nHost: x", "GET /a\nHost: x")

            assertEquals("--- request_a\n+++ request_b\n GET /a\n Host: x", diff)
        }

        @Test
        fun anAddedLineIsMarkedWithAPlus() {
            val diff = diffLines("a", "a\nb")

            assertEquals("--- request_a\n+++ request_b\n a\n+b", diff)
        }

        @Test
        fun aRemovedLineIsMarkedWithAMinus() {
            val diff = diffLines("a\nb", "a")

            assertEquals("--- request_a\n+++ request_b\n a\n-b", diff)
        }

        @Test
        fun aChangedLineIsMarkedAsRemovedThenAdded() {
            val diff = diffLines("a\nb", "a\nc")

            assertEquals("--- request_a\n+++ request_b\n a\n-b\n+c", diff)
        }

        @Test
        fun anEmptyRightSideRemovesEveryLine() {
            val diff = diffLines("a\nb", "")

            assertEquals("--- request_a\n+++ request_b\n-a\n+\n-b", diff)
        }
    }

    // ── countOccurrences ─────────────────────────────────────────────────────────────────

    @Nested
    inner class CountOccurrences {
        @Test
        fun aNeedleThatIsAbsentCountsZero() {
            assertEquals(0, countOccurrences("abcdef", "zz"))
        }

        @Test
        fun aSingleOccurrenceCountsOne() {
            assertEquals(1, countOccurrences("abcdef", "cd"))
        }

        @Test
        fun everyOccurrenceIsCounted() {
            assertEquals(3, countOccurrences("xaxaxa", "a"))
        }

        @Test
        fun overlappingCandidatesAreCountedNonOverlapping() {
            // "aaaa" contains three overlapping "aa" but the scan advances past each match,
            // so the implementation reports two. Pinned as observed.
            assertEquals(2, countOccurrences("aaaa", "aa"))
        }

        @Test
        fun anEmptyNeedleCountsZeroRatherThanLoopingForever() {
            assertEquals(0, countOccurrences("abcdef", ""))
        }
    }

    // ── parseHighlightColor ──────────────────────────────────────────────────────────────

    @Nested
    inner class ParseHighlightColor {
        @Test
        fun aValidColourNameInLowerCaseIsParsed() {
            assertEquals(HighlightColor.RED, parseHighlightColor("red"))
        }

        @Test
        fun aValidColourNameInUpperCaseIsParsed() {
            assertEquals(HighlightColor.MAGENTA, parseHighlightColor("MAGENTA"))
        }

        @Test
        fun surroundingWhitespaceIsTrimmedBeforeParsing() {
            assertEquals(HighlightColor.CYAN, parseHighlightColor("  cyan  "))
        }

        @Test
        fun aBlankStringYieldsNull() {
            assertNull(parseHighlightColor(""))
            assertNull(parseHighlightColor("   "))
        }

        @Test
        fun aNullOrUnrecognisedNameYieldsNull() {
            assertNull(parseHighlightColor(null))
            assertNull(parseHighlightColor("chartreuse"))
        }
    }

    // ── resolveAuditConfig ───────────────────────────────────────────────────────────────

    @Nested
    inner class ResolveAuditConfig {
        @Test
        fun theThreeActiveAliasesResolveToTheLegacyActiveConfiguration() {
            listOf("active", "active_checks", "legacy_active").forEach { alias ->
                assertEquals(
                    BuiltInAuditConfiguration.LEGACY_ACTIVE_AUDIT_CHECKS,
                    resolveAuditConfig(alias),
                    "alias=$alias",
                )
            }
        }

        @Test
        fun theThreePassiveAliasesResolveToTheLegacyPassiveConfiguration() {
            listOf("passive", "passive_checks", "legacy_passive").forEach { alias ->
                assertEquals(
                    BuiltInAuditConfiguration.LEGACY_PASSIVE_AUDIT_CHECKS,
                    resolveAuditConfig(alias),
                    "alias=$alias",
                )
            }
        }

        @Test
        fun aCanonicalEnumNameIsReachedThroughTheElseBranch() {
            assertEquals(
                BuiltInAuditConfiguration.LEGACY_ACTIVE_AUDIT_CHECKS,
                resolveAuditConfig("  legacy_active_audit_checks  "),
            )
            assertEquals(
                BuiltInAuditConfiguration.LEGACY_PASSIVE_AUDIT_CHECKS,
                resolveAuditConfig("LEGACY_PASSIVE_AUDIT_CHECKS"),
            )
        }

        @Test
        fun anUnrecognisedValuePropagatesTheEnumValueOfFailure() {
            // Observed behaviour, pinned: the `else` branch delegates to Enum.valueOf, which
            // throws. resolveAuditConfig does NOT catch it, so the caller sees the failure.
            assertThrows(IllegalArgumentException::class.java) {
                resolveAuditConfig("not_a_configuration")
            }
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
    ): McpToolContext = newContext(mode = mode, salt = salt)

    private fun historyContext(
        determinism: Boolean,
        newestFirst: Boolean,
    ): McpToolContext =
        newContext(
            mode = PrivacyMode.OFF,
            salt = "history-salt",
            determinism = determinism,
            newestFirst = newestFirst,
        )

    private fun newContext(
        mode: PrivacyMode,
        salt: String,
        determinism: Boolean = false,
        newestFirst: Boolean = false,
    ): McpToolContext {
        val api = mock<MontoyaApi>(defaultAnswer = Answers.RETURNS_DEEP_STUBS)
        return McpToolContext(
            api = api,
            privacyMode = mode,
            determinismMode = determinism,
            hostSalt = salt,
            toolToggles = emptyMap(),
            unsafeEnabled = false,
            unsafeTools = emptySet(),
            enabledUnsafeTools = emptySet(),
            limiter = McpRequestLimiter(4),
            edition = BurpSuiteEdition.PROFESSIONAL,
            maxBodyBytes = 1024,
            proxyHistoryNewestFirst = newestFirst,
        )
    }
}
