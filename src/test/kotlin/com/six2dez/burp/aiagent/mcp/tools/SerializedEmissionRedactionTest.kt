package com.six2dez.burp.aiagent.mcp.tools

import burp.api.montoya.MontoyaApi
import burp.api.montoya.core.BurpSuiteEdition
import com.six2dez.burp.aiagent.mcp.McpRequestLimiter
import com.six2dez.burp.aiagent.mcp.McpToolContext
import com.six2dez.burp.aiagent.mcp.schema.AuditIssueConfidence
import com.six2dez.burp.aiagent.mcp.schema.AuditIssueDefinition
import com.six2dez.burp.aiagent.mcp.schema.AuditIssueSeverity
import com.six2dez.burp.aiagent.mcp.schema.HttpRequestResponse
import com.six2dez.burp.aiagent.mcp.schema.HttpService
import com.six2dez.burp.aiagent.mcp.schema.IssueDetails
import com.six2dez.burp.aiagent.mcp.schema.SiteMapEntry
import com.six2dez.burp.aiagent.redact.PrivacyMode
import com.six2dez.burp.aiagent.redact.Redaction
import com.six2dez.burp.aiagent.redact.RedactionPolicy
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.Answers
import org.mockito.kotlin.mock

/**
 * (PRIV-05) Phase 27 plan 27-04 — the red probe the phase verifier named as missing, plus the
 * hazard family for the rule shape that closes it.
 *
 * WHAT THIS FILE ASSERTS ON, and why it is a different shape from `RedactionTest`'s cookie
 * fixtures: the MCP tool-RESULT path for `proxy_http_history`, `proxy_http_history_regex`,
 * `site_map`, `site_map_regex` and `scanner_issues` embeds a FULL raw HTTP message — cookie headers
 * included — into a JSON string via `Serialization.kt`'s `toSerializableForm()` / `toSiteMapEntry()`,
 * and `toolJson.encodeToString` then escapes every CRLF into the two literal characters
 * backslash-r / backslash-n. The emitted payload therefore contains NO real newline. Both cookie
 * rules were line-anchored `(?im)^…$`, so neither could fire, and the canonical `Cookie:` and
 * `Set-Cookie:` values reached the configured AI backend verbatim under STRICT and BALANCED.
 *
 * SCOPE OF THE CLAIM THIS FILE SUPPORTS: the serialized emission path, and the cookie-header class.
 * Nothing here says redaction is complete, and nothing here is evidence about any other header
 * class on this shape — see `Redaction.kt`'s KDoc above the two cookie rules for what is
 * deliberately still blind.
 *
 * FIXTURES ARE BUILT FROM THE REAL SERIALIZERS' TYPES plus `toolJson`, never from a hand-typed
 * escaped string: if the serialized shape changes, these tests move with it instead of quietly
 * asserting against a shape that is no longer emitted. THE TWO `aRealMultiLine…` FIXTURES ARE THE
 * DELIBERATE EXCEPTION — they are REAL multi-line strings that never pass through `toolJson`,
 * because their whole purpose is to pin the OTHER branch of the rule, the one that shipped. That
 * exception is intent, not drift from the rule above it.
 *
 * SENTINEL DISCIPLINE (the reason an absence assertion here cannot pass for some other rule's
 * reason). Every sentinel is a bare lowercase alphabetic word, and every cookie NAME in front of it
 * is a nonsense token carrying no `SENSITIVE_KEY_EXPR` word, so ONLY the two cookie header rules can
 * remove it:
 *  - the sentinel contains no `=`, so `formBodyParamRegex` (`(^|[?&])KEY=…`), `urlTokenParamRegex`
 *    (`[?&]KEY=…`) and `cookieTypedParamRegex` (`NAME=VALUE (type)`) cannot claim it, and the cookie
 *    name in front of the `=` is not a sensitive key either;
 *  - it carries no `Bearer ` / `Basic ` prefix and no dotted `eyJ` segment, so `bearerRegex`,
 *    `basicAuthRegex` and `jwtRegex` cannot claim it;
 *  - it is a header VALUE, not a quoted JSON key, so `jsonSecretKeyRegex` (`"KEY"\s*:\s*VALUE`)
 *    cannot claim it — and the enclosing JSON keys are `request` / `response` / `notes` / `url`,
 *    none of which is in `SENSITIVE_WORDS`;
 *  - the payload carries no `=== COOKIES ===` section, so `redactCookieSections` never runs on it.
 *
 * Header mocking is not needed here: unlike `McpToolHelpersTest`, this file never touches a Montoya
 * `HttpHeader`. The raw message is a plain string, which is exactly what `request()?.toString()`
 * hands to the serializer.
 */
class SerializedEmissionRedactionTest {
    @BeforeEach
    fun clearCustomPatterns() {
        // Redaction is a singleton object: custom patterns left behind by another test class in the
        // same JVM would bleed in here and could remove a sentinel for the wrong reason, letting an
        // absence assertion pass without the cookie rule doing anything. Precedent: McpToolHelpersTest.
        Redaction.setCustomPatterns(emptyList())
    }

    @Test
    fun everySentinelInThisFileIsDistinct() {
        // Distinctness is asserted IN KOTLIN over the list the fixtures actually draw from, not by
        // comparing two greps that count different populations. [ALL_SENTINELS] is DERIVED from the
        // [Sentinel] enum, so a sentinel cannot exist in this file without appearing here: adding a
        // fixture value means adding an enum entry, and `entries` picks it up with no second edit.
        //
        // The substring half is load-bearing in its own right. Every gate in this file is an
        // `assertFalse(output.contains(sentinel))`, and a sentinel that is a SUBSTRING of another
        // defeats that gate exactly as a duplicate does — the surviving longer value keeps the
        // shorter one "present" and a real leak reads as a pass. BENIGN_CONTROL is included for the
        // same reason: if the negative control collided with a sentinel, the control assertion and
        // the leak assertion would contradict each other and one of them would be meaningless.
        assertEquals(
            ALL_SENTINELS.size,
            ALL_SENTINELS.toSet().size,
            "sentinels must be distinct, or one rule's success masks another's failure: $ALL_SENTINELS",
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

    // ── carrier 1: HttpRequestResponse, the proxy_http_history / scanner_issues shape ─────

    @Nested
    inner class ProxyHistoryCarrier {
        @Test
        fun canonicalCookieDoesNotSurviveTheSerializedProxyHistoryShapeUnderStrict() {
            val serialized = toolJson.encodeToString(proxyHistoryFixture())

            val redacted = contextWith(PrivacyMode.STRICT, "serialized-strict-salt").redactIfNeeded(serialized)

            assertFalse(
                redacted.contains(Sentinel.COOKIE_STRICT.value),
                "a canonical Cookie value must not survive STRICT redaction of the serialized " +
                    "proxy_http_history shape (got: $redacted)",
            )
        }

        @Test
        fun redactedSerializedOutputStillParsesAsJsonWithTheSameKeySet() {
            val serialized = toolJson.encodeToString(proxyHistoryFixture())

            val redacted = contextWith(PrivacyMode.STRICT, "serialized-parse-salt").redactIfNeeded(serialized)

            // This is the gate on the tool CONTRACT, and it is what catches a value tail that runs
            // past the closing quote of the JSON string: a greedy `.+$` on a single-line payload
            // consumes the closing quote and the closing brace and emits invalid JSON — a break
            // worse than the leak it was meant to fix.
            assertSameJsonShape(serialized, redacted)
        }

        @Test
        fun headerNameAndBenignControlSurviveTheSerializedShape() {
            val serialized = toolJson.encodeToString(proxyHistoryFixture())

            val redacted = contextWith(PrivacyMode.STRICT, "serialized-control-salt").redactIfNeeded(serialized)

            assertTrue(
                redacted.contains("Cookie"),
                "the header NAME must survive — only the VALUE is replaced (T-21-WA2) (got: $redacted)",
            )
            assertTrue(
                redacted.contains(Sentinel.BENIGN_CONTROL.value),
                "negative control: a value in a non-cookie header must survive, so a pass cannot be " +
                    "produced by blanket destruction of the payload (got: $redacted)",
            )
        }

        @Test
        fun canonicalCookieDoesNotSurviveTheSerializedShapeUnderBalanced() {
            val serialized =
                toolJson.encodeToString(
                    requestOnly("Cookie: wibble=${Sentinel.COOKIE_BALANCED.value}"),
                )

            val redacted = contextWith(PrivacyMode.BALANCED, "serialized-balanced-salt").redactIfNeeded(serialized)

            assertFalse(
                redacted.contains(Sentinel.COOKIE_BALANCED.value),
                "BALANCED sets stripCookies too, so the canonical Cookie value must not survive (got: $redacted)",
            )
            assertSameJsonShape(serialized, redacted)
        }

        @Test
        fun canonicalSetCookieDoesNotSurviveTheSerializedShapeInBothRedactingModes() {
            val perMode =
                mapOf(
                    PrivacyMode.STRICT to Sentinel.SET_COOKIE_STRICT,
                    PrivacyMode.BALANCED to Sentinel.SET_COOKIE_BALANCED,
                )

            perMode.forEach { (mode, sentinel) ->
                val serialized =
                    toolJson.encodeToString(
                        HttpRequestResponse(
                            request = "GET /basket HTTP/1.1\r\nAccept: text/html\r\n\r\n",
                            response =
                                "HTTP/1.1 200 OK\r\n" +
                                    "Set-Cookie: wobble=${sentinel.value}; Path=/\r\n" +
                                    "X-Request-Id: ${Sentinel.BENIGN_CONTROL.value}\r\n\r\n",
                            notes = null,
                        ),
                    )

                val redacted = contextWith(mode, "set-cookie-salt-$mode").redactIfNeeded(serialized)

                assertFalse(
                    redacted.contains(sentinel.value),
                    "$mode: a canonical Set-Cookie value must not survive the serialized shape (got: $redacted)",
                )
                assertTrue(
                    redacted.contains("Set-Cookie"),
                    "$mode: the Set-Cookie header NAME must survive (T-21-WA2) (got: $redacted)",
                )
                assertSameJsonShape(serialized, redacted)
            }
        }

        @Test
        fun everyCookieNameVariantCarriesADistinctSentinelAndNoneSurvives() {
            // Distinct sentinels per name are what stop one rule's success masking another's
            // failure: with one shared value a matcher that catches four of five still passes, and
            // that is the exact class of miss this phase exists to repair.
            val variants =
                listOf(
                    "X-Cookie" to Sentinel.VARIANT_X_COOKIE,
                    "Cookie2" to Sentinel.VARIANT_COOKIE2,
                    "Set-Cookie2" to Sentinel.VARIANT_SET_COOKIE2,
                    "X-Original-Cookie" to Sentinel.VARIANT_X_ORIGINAL_COOKIE,
                    "X-Forwarded-Cookie" to Sentinel.VARIANT_X_FORWARDED_COOKIE,
                )
            val headerBlock = variants.joinToString("") { (name, sentinel) -> "$name: ${sentinel.value}\r\n" }
            val serialized =
                toolJson.encodeToString(
                    requestOnly(headerBlock.removeSuffix("\r\n")),
                )

            listOf(PrivacyMode.STRICT, PrivacyMode.BALANCED).forEach { mode ->
                val redacted = contextWith(mode, "variant-salt-$mode").redactIfNeeded(serialized)

                variants.forEach { (name, sentinel) ->
                    assertFalse(
                        redacted.contains(sentinel.value),
                        "$mode: the value of $name must not survive the serialized shape (got: $redacted)",
                    )
                    assertTrue(
                        redacted.contains("$name: [STRIPPED]"),
                        "$mode: $name must keep its OWN name, not be renamed to Cookie/Set-Cookie (got: $redacted)",
                    )
                }
                assertTrue(
                    redacted.contains(Sentinel.BENIGN_CONTROL.value),
                    "$mode: a header with no cookie token must survive byte-identical (got: $redacted)",
                )
                assertSameJsonShape(serialized, redacted)
            }
        }

        @Test
        fun offModeLeavesTheSerializedShapeByteIdentical() {
            // The negative control that proves this is a POLICY-GATED strip and not unconditional
            // mangling. Byte equality against the un-redacted input, deliberately not a `contains`
            // check: a `contains` assertion would still pass if OFF mutated some other part of the
            // payload.
            val serialized =
                toolJson.encodeToString(
                    requestOnly("Cookie: wibble=${Sentinel.OFF_MODE_CONTROL.value}"),
                )

            val redacted = contextWith(PrivacyMode.OFF, "off-mode-salt").redactIfNeeded(serialized)

            assertEquals(serialized, redacted, "PrivacyMode.OFF must leave the serialized payload byte-identical")
        }
    }

    // ── carrier 2: SiteMapEntry, the site_map / site_map_regex shape ──────────────────────

    @Nested
    inner class SiteMapCarrier {
        @Test
        fun siteMapEntryCarrierStripsCookiesInBothRedactingModes() {
            val serialized =
                toolJson.encodeToString(
                    SiteMapEntry(
                        url = "https://shop.example/basket",
                        request =
                            "GET /basket HTTP/1.1\r\n" +
                                "Cookie: wibble=${Sentinel.SITE_MAP_CARRIER.value}\r\n" +
                                "X-Request-Id: ${Sentinel.BENIGN_CONTROL.value}\r\n\r\n",
                        response = "HTTP/1.1 200 OK\r\nContent-Type: text/html\r\n\r\n<html></html>",
                    ),
                )

            listOf(PrivacyMode.STRICT, PrivacyMode.BALANCED).forEach { mode ->
                val redacted = contextWith(mode, "site-map-salt-$mode").redactIfNeeded(serialized)

                assertFalse(
                    redacted.contains(Sentinel.SITE_MAP_CARRIER.value),
                    "$mode: the site_map carrier must strip the cookie value (got: $redacted)",
                )
                assertTrue(
                    redacted.contains(Sentinel.BENIGN_CONTROL.value),
                    "$mode: negative control on the site_map carrier (got: $redacted)",
                )
                assertSameJsonShape(serialized, redacted)
            }
        }
    }

    // ── carrier 3: IssueDetails.requestResponses, the scanner_issues shape ────────────────

    @Nested
    inner class IssueDetailsCarrier {
        @Test
        fun issueDetailsCarrierStripsCookiesInBothRedactingModes() {
            val serialized = toolJson.encodeToString(issueDetailsFixture())

            listOf(PrivacyMode.STRICT, PrivacyMode.BALANCED).forEach { mode ->
                val redacted = contextWith(mode, "issue-salt-$mode").redactIfNeeded(serialized)

                assertFalse(
                    redacted.contains(Sentinel.ISSUE_DETAILS_CARRIER.value),
                    "$mode: the scanner_issues carrier must strip the cookie value (got: $redacted)",
                )
                assertTrue(
                    redacted.contains(Sentinel.BENIGN_CONTROL.value),
                    "$mode: negative control on the scanner_issues carrier (got: $redacted)",
                )
                assertSameJsonShape(serialized, redacted)
            }
        }
    }

    // ── carrier 4: a header at the OPEN of a JSON string, the third logical-line start ────

    /**
     * (PRIV-05) Phase 27 plan 27-11. The start boundary the round-3 verification measured as
     * missing — and the one gap in this phase where the CANONICAL spelling, not a variant and not an
     * underscore name, defeated the strongest privacy mode.
     *
     * `logicalLineHeaderRule`'s escaped branch required a PRECEDING escaped newline. A header that is
     * the FIRST content of a JSON string value has neither a real `^` nor a preceding escaped
     * newline, so no branch of the composed rules could reach it at all. Measured on the shipped
     * classes in round 3:
     *
     * ```
     * {"notes":"Cookie: a=SECRET1\r\nX: y"}  STRICT ->  unchanged            (the gap)
     * {"notes":"X: y\r\nCookie: a=SECRET9"}  STRICT ->  Cookie: [STRIPPED]   (control fired)
     * ```
     *
     * `HttpRequestResponse.notes` is what makes this reachable rather than theoretical: it carries
     * analyst annotations, so its first characters are whatever the analyst typed. The `request`
     * field always begins `GET / HTTP/1.1`, which is why the 14 pinned emission sites' PRIMARY
     * payload was never affected — and why this survived three rounds of verification unseen.
     *
     * SCOPE OF WHAT THIS NESTED CLASS PROVES: that the composed rules now REACH a header at a JSON
     * string open, and that reaching it does not let a match run past that string's closing quote.
     * It says nothing about the FOURTH start — a leading-whitespace or obs-folded header line —
     * which is MEASURED still unmatched and filed as `AR-27-09`. See `Redaction.kt`'s rationale
     * block for that bound stated where a reader meets the rule.
     */
    @Nested
    inner class JsonStringOpenBoundary {
        @Test
        fun aCanonicalCookieAtTheOpenOfAJsonStringDoesNotSurviveStrict() {
            val serialized = toolJson.encodeToString(notesCarrier(cookieFirstNotes(Sentinel.JSON_STRING_OPEN_STRICT)))

            assertTrue(
                serialized.contains("\"notes\":\"Cookie:"),
                "fixture guard: the cookie header must really be the FIRST content of the notes " +
                    "string. If it is not, this probe silently tests the escaped-newline branch that " +
                    "already shipped and proves nothing (got: $serialized)",
            )

            val redacted = contextWith(PrivacyMode.STRICT, "json-string-open-strict-salt").redactIfNeeded(serialized)

            assertFalse(
                redacted.contains(Sentinel.JSON_STRING_OPEN_STRICT.value),
                "a canonical Cookie value at the OPEN of a JSON string must not survive STRICT (got: $redacted)",
            )
            assertTrue(
                redacted.contains(Sentinel.BENIGN_CONTROL.value),
                "negative control: the non-cookie header in the same notes value must survive, so a " +
                    "pass cannot be produced by blanket destruction of the field (got: $redacted)",
            )
            assertSameJsonShape(serialized, redacted)
        }

        @Test
        fun aCanonicalCookieAtTheOpenOfAJsonStringDoesNotSurviveBalanced() {
            val serialized = toolJson.encodeToString(notesCarrier(cookieFirstNotes(Sentinel.JSON_STRING_OPEN_BALANCED)))

            assertTrue(
                serialized.contains("\"notes\":\"Cookie:"),
                "fixture guard: the cookie header must really be the FIRST content of the notes string (got: $serialized)",
            )

            val redacted = contextWith(PrivacyMode.BALANCED, "json-string-open-balanced-salt").redactIfNeeded(serialized)

            assertFalse(
                redacted.contains(Sentinel.JSON_STRING_OPEN_BALANCED.value),
                "BALANCED sets stripCookies too, so the value must not survive there either (got: $redacted)",
            )
            assertTrue(
                redacted.contains(Sentinel.BENIGN_CONTROL.value),
                "negative control on the BALANCED probe (got: $redacted)",
            )
            assertSameJsonShape(serialized, redacted)
        }

        /**
         * THE POSITIVE CONTROL, and the reason the two probes above are a statement about the rule's
         * REACH rather than a dead fixture. Same header, same field, same modes — only the POSITION
         * differs: here it follows an escaped newline, the boundary the escaped branch already had.
         *
         * EXPECTED GREEN BOTH BEFORE AND AFTER the boundary change, and that is the whole point. A
         * red run in which this ALSO failed would mean the fixture was wrong and the probes proved
         * nothing; a red run in which ONLY the probes failed — which is what was recorded — means the
         * rule could not REACH that position. Without this test the two probes above are
         * unfalsifiable, so it is not redundant coverage of the shipped branch.
         */
        @Test
        fun theSameCookieHeaderAfterAnEscapedNewlineIsStrippedInBothRedactingModes() {
            val notes =
                "X-Request-Id: ${Sentinel.BENIGN_CONTROL.value}\r\n" +
                    "Cookie: wibble=${Sentinel.JSON_STRING_OPEN_CONTROL.value}"
            val serialized = toolJson.encodeToString(notesCarrier(notes))

            assertTrue(
                serialized.contains("\\r\\nCookie:"),
                "fixture guard: the control's cookie header must follow an ESCAPED newline, or it is " +
                    "not testing the branch that shipped (got: $serialized)",
            )

            listOf(PrivacyMode.STRICT, PrivacyMode.BALANCED).forEach { mode ->
                val redacted = contextWith(mode, "json-string-open-control-salt-$mode").redactIfNeeded(serialized)

                assertFalse(
                    redacted.contains(Sentinel.JSON_STRING_OPEN_CONTROL.value),
                    "$mode: the escaped-newline branch shipped working — if THIS fails the fixture is " +
                        "wrong and the probes above prove nothing (got: $redacted)",
                )
                assertTrue(
                    redacted.contains("Cookie: [STRIPPED]"),
                    "$mode: the control must produce the name-preserving replacement (got: $redacted)",
                )
                assertSameJsonShape(serialized, redacted)
            }
        }

        /**
         * THE OVER-MATCH BOUND — the cost side of the new start boundary, gated rather than reasoned
         * about. A start that can fire at a double quote could let the value tail run PAST the JSON
         * string's closing quote and into the next field, corrupting the tool result. That is a
         * correctness break worse than the leak the boundary was added to close, so it gets an
         * assertion rather than an argument.
         *
         * THE CARRIER IS THE scanner_issues SHAPE for a structural reason, not an aesthetic one:
         * `HttpRequestResponse` declares `notes` LAST, so on that carrier there is no sibling field
         * AFTER the one under test and this assertion would have nothing to bite on. `IssueDetails`
         * carries the same `notes` one level deeper, followed by `collaboratorInteractions` and
         * `definition` — a real emission shape, not one invented to make the assertion possible.
         *
         * `notes` here ends IMMEDIATELY after the cookie value, so the tail's only available
         * terminator is the closing quote itself. That is the hardest form of this case.
         */
        @Test
        fun aMatchBeginningAtAJsonStringOpenStopsAtThatStringsClosingQuote() {
            val serialized = toolJson.encodeToString(overMatchFixture())

            assertTrue(
                serialized.contains("wibble=${Sentinel.JSON_STRING_OPEN_STRICT.value}\"}"),
                "fixture guard: the cookie value must be the LAST content of its JSON string, so the " +
                    "closing quote is the only terminator available to the tail (got: $serialized)",
            )

            val redacted = contextWith(PrivacyMode.STRICT, "json-string-open-overmatch-salt").redactIfNeeded(serialized)

            assertFalse(
                redacted.contains(Sentinel.JSON_STRING_OPEN_STRICT.value),
                "the cookie value must still be stripped on this carrier (got: $redacted)",
            )
            assertEquals(
                definitionBackgroundOf(serialized),
                definitionBackgroundOf(redacted),
                "the sibling field AFTER the carrier string must be BYTE-IDENTICAL — a match that ran " +
                    "across the closing quote would have consumed into it (got: $redacted)",
            )
            assertSameJsonShape(serialized, redacted)
        }

        /**
         * THE HEADER-MAP NON-REGRESSION, the other side of the same bound. `ParsedRequest` — the shape
         * `request_parse` and `response_parse` emit — carries headers as JSON OBJECT MEMBERS, not as
         * lines. `McpToolHelpersTest` records the MEASURED fact that this shape carries no line
         * boundary of ANY kind, and the new start boundary must not quietly falsify that by making the
         * composer begin matching at the open of a member's key or value.
         *
         * THE ASSERTION IS A NEGATIVE ABOUT THE MARKER, deliberately, and never a positive about a
         * sensitive value surviving a redacting policy. `[STRIPPED]` is produced by exactly two
         * things in `Redaction.kt` — `cookieHeaderRegex` and `setCookieHeaderRegex` — so its absence
         * IS the proof that the composer did not start matching a JSON object member. An assertion
         * that some value survived STRICT would instead be a green pin on a leak, which is the
         * artifact class this round exists to remove.
         *
         * `sanitizeHeaders`, NOT the composer, is the control on this shape for the cookie-header
         * class, and `McpToolHelpersTest$SanitizeHeaders` is what gates that control end to end. This
         * test is about the composer's REACH, not about that control.
         */
        @Test
        fun theHeaderMapShapeIsStillOutOfTheComposersReach() {
            val serialized =
                toolJson.encodeToString(
                    headerMapPayload(
                        mapOf(
                            "Cookie" to "wibble=harmless",
                            "Authorization" to "Bearer ${Sentinel.JSON_STRING_OPEN_CONTROL.value}",
                        ),
                    ),
                )

            assertTrue(
                serialized.contains("\"Cookie\":"),
                "fixture guard: the header map must really carry a cookie-named KEY (got: $serialized)",
            )
            assertFalse(
                serialized.contains("\\r") || serialized.contains("\\n"),
                "fixture guard: the header-map shape must carry no line boundary at all — that, and " +
                    "nothing cookie-specific, is why the composer cannot fire here (got: $serialized)",
            )

            val redacted = contextWith(PrivacyMode.STRICT, "header-map-reach-salt").redactIfNeeded(serialized)

            // Non-vacuity, in the register McpToolHelpersTest uses on this same shape: redactIfNeeded
            // really ran under a redacting policy, so the negative below is not standing in for a call
            // that no-opped. bearerRegex is un-anchored, so it fires whatever line boundary the
            // payload has — or does not have.
            assertFalse(
                redacted.contains(Sentinel.JSON_STRING_OPEN_CONTROL.value),
                "redactIfNeeded must really have run under a redacting policy (got: $redacted)",
            )
            assertFalse(
                redacted.contains("[STRIPPED]"),
                "the composer must NOT have begun matching a JSON object member: no cookie-rule " +
                    "marker may appear on the header-map shape (got: $redacted)",
            )
            assertSameJsonShape(serialized, redacted)
        }

        private fun cookieFirstNotes(sentinel: Sentinel): String = "Cookie: wibble=${sentinel.value}\r\nX-Request-Id: ${Sentinel.BENIGN_CONTROL.value}"

        /**
         * The scanner_issues carrier with the cookie header at the open of `notes` and the whole
         * `notes` string ending there, so the fields that follow are the over-match target.
         * `background` carries the benign control, which is what the byte-identity assertion reads.
         */
        private fun overMatchFixture(): IssueDetails =
            IssueDetails(
                name = "Reflected input",
                detail = "detail",
                remediation = "remediation",
                httpService = HttpService(host = "shop.example", port = 443, secure = true),
                baseUrl = "https://shop.example/",
                severity = AuditIssueSeverity.HIGH,
                confidence = AuditIssueConfidence.FIRM,
                requestResponses =
                    listOf(
                        HttpRequestResponse(
                            request = "GET /basket HTTP/1.1\r\nAccept: text/html\r\n\r\n",
                            response = null,
                            notes = "Cookie: wibble=${Sentinel.JSON_STRING_OPEN_STRICT.value}",
                        ),
                    ),
                collaboratorInteractions = emptyList(),
                definition =
                    AuditIssueDefinition(
                        id = "reflected_input",
                        background = Sentinel.BENIGN_CONTROL.value,
                        remediation = "remediation",
                        typeIndex = 1,
                    ),
            )

        private fun definitionBackgroundOf(payload: String): String {
            val root = toolJson.parseToJsonElement(payload).jsonObject
            val definition = root.getValue("definition").jsonObject
            return definition.getValue("background").jsonPrimitive.content
        }

        /** The `request_parse` envelope: only `headers` varies, so every other field is fixed here. */
        private fun headerMapPayload(headers: Map<String, String>): ParsedRequest =
            ParsedRequest(
                method = "GET",
                path = "/basket",
                url = "https://shop.example/basket",
                headers = headers,
                parameters = emptyList(),
                body = null,
                bodyLength = 0,
            )
    }

    // ── the named hazards of this rule shape, each gated rather than reasoned about ───────

    @Nested
    inner class Hazards {
        /**
         * H1a. The shape a "quote not preceded by a backslash" terminator silently destroys: the last
         * content before the closing quote is a cookie header whose value ends in exactly ONE
         * backslash. JSON encoding doubles it, so the character immediately before the closing quote
         * is a literal backslash, a one-character negative lookbehind suppresses the terminator, and
         * the match runs to end-of-input — swallowing the closing quote and the closing brace.
         */
        @Test
        fun aCookieValueEndingInOneBackslashAtTheEndOfThePayloadStillParsesAsJson() {
            assertBackslashTailIsSafe(Sentinel.ONE_BACKSLASH_HAZARD.value, backslashes = 1)
        }

        /**
         * H1b. The same with TWO trailing backslashes. Both cases are required and specifying only
         * the odd-length one would be a gate narrower than the defect: encoding doubles every
         * backslash, so the character before the closing quote is a backslash either way and the
         * lookbehind fails in both. Parity over the backslash run is the real predicate, and a
         * one-character look-back cannot count it — which is why the shipped tail consumes escape
         * PAIRS atomically instead.
         */
        @Test
        fun aCookieValueEndingInTwoBackslashesAtTheEndOfThePayloadStillParsesAsJson() {
            assertBackslashTailIsSafe(Sentinel.TWO_BACKSLASH_HAZARD.value, backslashes = 2)
        }

        /**
         * H1c. The REAL `truncateIfNeeded` output shape (`McpToolHelpers.kt:206`) — the encoded
         * payload cut by BYTES mid-escape, with `... (truncated N bytes to M bytes)` appended.
         * Redaction runs AFTER truncation, so this, and not a synthetic cut ending in a bare quote,
         * is what the rule actually meets.
         *
         * WHY THIS ONE CARRIES NO JSON-PARSE ASSERTION, stated rather than quietly omitted: the
         * input is ALREADY invalid JSON before redaction ever runs — truncation leaves the string
         * unterminated — so "the output parses" is not a property any rule here could deliver, and
         * asserting it would just pin a failure that predates this change. The test asserts what IS
         * this rule's responsibility instead: the cookie is still stripped, and the match does not
         * run away and lengthen the payload. The invalidity of the input is itself asserted, so the
         * omission is justified by measurement rather than by convenience.
         */
        @Test
        fun theRealTruncateIfNeededOutputShapeIsStrippedAndNotLengthened() {
            val sentinel = Sentinel.TRUNCATION_HAZARD.value
            val serialized =
                toolJson.encodeToString(
                    requestOnly(
                        "Cookie: wibble=$sentinel\r\n" + "X-Filler: 0123456789012345678901234567890123456789\r\n".repeat(6),
                    ),
                )
            // Cut immediately AFTER a backslash that begins an encoded newline well past the cookie
            // header, so the sentinel is fully present in the truncated input (an absence assertion
            // must not be able to pass because truncation removed the sentinel itself) and the cut
            // lands mid-escape.
            val afterCookie = serialized.indexOf(sentinel) + sentinel.length
            val cutIndex = serialized.indexOf("\\r", serialized.indexOf("X-Filler", afterCookie)) + 1
            val truncated = truncateIfNeeded(serialized, cutIndex)

            assertTrue(truncated.contains(sentinel), "fixture guard: the sentinel must survive truncation itself")
            assertTrue(truncated.endsWith("bytes)"), "fixture guard: this must be the real truncateIfNeeded suffix shape")
            assertTrue(
                runCatching { toolJson.parseToJsonElement(truncated) }.isFailure,
                "fixture guard: the truncated input is already invalid JSON, which is why no parse gate applies here",
            )

            val redacted = contextWith(PrivacyMode.STRICT, "truncate-salt").redactIfNeeded(truncated)

            assertFalse(
                redacted.contains(sentinel),
                "the cookie value must still be stripped on the real truncated shape (got: $redacted)",
            )
            assertTrue(
                redacted.length <= truncated.length,
                "the match must not run away: ${redacted.length} > ${truncated.length}",
            )
        }

        /**
         * H2. The under-redaction direction. A cookie value containing a double quote encodes to an
         * ESCAPED quote, and a tail that stops at any quote would stop there and leak everything
         * after it. The escape pair steps over it instead.
         */
        @Test
        fun anEscapedQuoteInsideACookieValueDoesNotEndTheRedactionEarly() {
            val sentinel = Sentinel.ESCAPED_QUOTE_HAZARD.value
            val serialized = toolJson.encodeToString(requestOnly("Cookie: a=\"q\"; snork=$sentinel"))

            assertTrue(serialized.contains("\\\""), "fixture guard: the value must really carry an ESCAPED quote")

            val redacted = contextWith(PrivacyMode.STRICT, "escaped-quote-salt").redactIfNeeded(serialized)

            assertFalse(
                redacted.contains(sentinel),
                "no fragment of the value after the escaped quote may survive (got: $redacted)",
            )
            assertSameJsonShape(serialized, redacted)
        }

        /**
         * H3, the degenerate case. A cookie header with an EMPTY value. Measured and recorded rather
         * than discovered later: the escaped branch requires at least one unit after `:\s*`, so it
         * consumes the `\r` half of the encoded CRLF and the payload is mutated slightly — the line
         * becomes `Cookie: [STRIPPED]` followed by a lone `\n`. That is acceptable and fail-safe in
         * direction, but it must be a GATED fact rather than a surprise, so the contract assertion
         * is here.
         */
        @Test
        fun anEmptyCookieValueOnTheSerializedShapeStillParsesAsJson() {
            val serialized = toolJson.encodeToString(requestOnly("Cookie:"))

            val redacted = contextWith(PrivacyMode.STRICT, "empty-value-salt").redactIfNeeded(serialized)

            assertSameJsonShape(serialized, redacted)
        }

        /**
         * M1, byte-identity on REAL multi-line input. RFC 6265 permits a DQUOTE-wrapped cookie value,
         * and `RedactionTest` has no fixture that uses one. Measured: a single shared tail carrying a
         * quote terminator stops at the first quote here and LEAKS the rest — an under-redaction
         * regression on the PRIMARY path that would have shipped green. The expectation is the output
         * the SHIPPED rule produced, captured by running `Redaction.apply` on this exact fixture
         * against the pre-change `Redaction.kt`, not a value typed by hand to match whatever the new
         * rule happens to do.
         */
        @Test
        fun aRealMultiLineCookieValueContainingAQuoteIsStillStrippedWhole() {
            val fixture = realMultiLine("Cookie: a=\"q\"; session=${Sentinel.REAL_MULTILINE_QUOTE.value}")

            val output = Redaction.apply(fixture, STRICT_POLICY, stableHostSalt = "real-multiline-salt")

            assertEquals(SHIPPED_REAL_MULTILINE_OUTPUT, output, "multi-line behaviour must be byte-identical to what shipped")
            assertFalse(output.contains(Sentinel.REAL_MULTILINE_QUOTE.value), "no fragment of a quoted value may survive")
        }

        /**
         * M2, the other real-multi-line shape no shipped fixture covers: a cookie value ENDING in a
         * backslash at end of line. Measured: an escape-pair tail applied to the REAL-LINE branch
         * fails to match this at all — a total under-redaction. It is why the escape-pair tail lives
         * on the escaped branch only and the real-line branch keeps the shipped end-of-line tail.
         */
        @Test
        fun aRealMultiLineCookieValueEndingInABackslashIsStillStrippedWhole() {
            val fixture = realMultiLine("Cookie: wibble=${Sentinel.REAL_MULTILINE_BACKSLASH.value}\\")

            val output = Redaction.apply(fixture, STRICT_POLICY, stableHostSalt = "real-multiline-salt")

            assertEquals(SHIPPED_REAL_MULTILINE_OUTPUT, output, "multi-line behaviour must be byte-identical to what shipped")
            assertFalse(output.contains(Sentinel.REAL_MULTILINE_BACKSLASH.value), "a value ending in a backslash must be stripped")
        }

        private fun assertBackslashTailIsSafe(
            sentinel: String,
            backslashes: Int,
        ) {
            val serialized =
                toolJson.encodeToString(
                    HttpRequestResponse(
                        request = "GET /basket HTTP/1.1\r\nCookie: wibble=$sentinel" + "\\".repeat(backslashes),
                        response = null,
                        notes = null,
                    ),
                )

            assertTrue(
                serialized.contains("\\\\".repeat(backslashes) + "\""),
                "fixture guard: the encoded value must end in $backslashes doubled backslash(es) before the closing quote",
            )

            val redacted = contextWith(PrivacyMode.STRICT, "backslash-salt-$backslashes").redactIfNeeded(serialized)

            assertSameJsonShape(serialized, redacted)
            assertFalse(redacted.contains(sentinel), "the cookie value must still be stripped (got: $redacted)")
        }
    }

    // ── the auth-header class on the same shape (27-05 / D-27-12) ────────────────────────

    /**
     * (PRIV-05) Phase 27 plan 27-05. `authHeaderRegex` carried the IDENTICAL defect the two cookie
     * rules did: `(?im)^…:\s*.+$`, line-anchored, unable to fire on a payload whose newlines are
     * JSON escapes.
     *
     * MEASURED before the change, on the compiled shipped classes, against the serialized shape:
     *
     * ```
     * STRICT   APIKEY  SURVIVES        BALANCED APIKEY  SURVIVES
     * STRICT   BEARER  STRIPPED        BALANCED BEARER  STRIPPED
     * ```
     *
     * `Authorization: Bearer …` survived only BY LUCK — the un-anchored [Redaction] `bearerRegex`
     * happened to claim its value while the header rule missed the line entirely. A plain-token
     * `X-API-Key` has no such luck: it is not bearer-, basic- or JWT-shaped, and it is a header VALUE
     * rather than a quoted JSON key so `jsonSecretKeyRegex` cannot reach it either. It left the
     * process VERBATIM under the strongest privacy mode — and an API key outlives a session cookie.
     *
     * The fix recomposes `authHeaderRegex` through the SAME `logicalLineHeaderRule` composer plan
     * 27-04 proved, so all 16 names in its alternation are covered rather than just the measured one.
     * Which names it matches is unchanged.
     */
    @Nested
    inner class AuthHeaderCredentials {
        @Test
        fun credentialBearingAuthHeaderValueDoesNotSurviveTheSerializedShapeUnderStrict() {
            val serialized =
                toolJson.encodeToString(
                    requestOnly("X-API-Key: wibble=${Sentinel.AUTH_API_KEY_STRICT.value}"),
                )

            val redacted = contextWith(PrivacyMode.STRICT, "auth-strict-salt").redactIfNeeded(serialized)

            assertFalse(
                redacted.contains(Sentinel.AUTH_API_KEY_STRICT.value),
                "a plain-token X-API-Key value must not survive STRICT redaction of the serialized " +
                    "shape. It is not bearer-, basic- or JWT-shaped, so no un-anchored token rule " +
                    "rescues it and authHeaderRegex is the only control (got: $redacted)",
            )
            assertTrue(
                redacted.contains("X-API-Key"),
                "the header NAME must survive — only the VALUE is replaced (T-21-WA2) (got: $redacted)",
            )
        }

        @Test
        fun credentialBearingAuthHeaderValueDoesNotSurviveTheSerializedShapeUnderBalanced() {
            val serialized =
                toolJson.encodeToString(
                    requestOnly("X-API-Key: wibble=${Sentinel.AUTH_API_KEY_BALANCED.value}"),
                )

            val redacted = contextWith(PrivacyMode.BALANCED, "auth-balanced-salt").redactIfNeeded(serialized)

            assertFalse(
                redacted.contains(Sentinel.AUTH_API_KEY_BALANCED.value),
                "BALANCED sets redactTokens too, so the API-key value must not survive (got: $redacted)",
            )
            assertSameJsonShape(serialized, redacted)
        }

        /**
         * The one sentinel in this file deliberately reachable by a SECOND rule. Its point is the
         * name-preservation invariant, not the absence: whichever rule claims the value —
         * `authHeaderRegex`'s name-preserving lambda after this change, `bearerRegex` before it — the
         * header name must still read `Authorization` in the analyst's view of the traffic.
         */
        @Test
        fun bearerShapedAuthorizationIsStillRedactedAndTheHeaderNameSurvives() {
            val serialized =
                toolJson.encodeToString(
                    requestOnly("Authorization: Bearer ${Sentinel.AUTH_BEARER_SHAPED.value}"),
                )

            listOf(PrivacyMode.STRICT, PrivacyMode.BALANCED).forEach { mode ->
                val redacted = contextWith(mode, "auth-bearer-salt-$mode").redactIfNeeded(serialized)

                assertFalse(
                    redacted.contains(Sentinel.AUTH_BEARER_SHAPED.value),
                    "$mode: a bearer token must not survive the serialized shape (got: $redacted)",
                )
                assertTrue(
                    redacted.contains("Authorization"),
                    "$mode: the Authorization header NAME must survive (got: $redacted)",
                )
                assertTrue(
                    redacted.contains(Sentinel.BENIGN_CONTROL.value),
                    "$mode: negative control — a non-auth header must survive (got: $redacted)",
                )
                assertSameJsonShape(serialized, redacted)
            }
        }

        @Test
        fun redactedAuthBearingSerializedOutputStillParsesAsJsonWithTheSameKeySet() {
            // The tool CONTRACT gate. The same greedy-tail hazard applies to this rule as to the
            // cookie rules: a `.+$` tail on a single-line payload consumes the closing quote and the
            // closing brace and emits invalid JSON — a break worse than the leak it was meant to fix.
            val serialized =
                toolJson.encodeToString(
                    requestOnly("X-API-Key: wibble=${Sentinel.AUTH_JSON_PARSE.value}"),
                )

            val redacted = contextWith(PrivacyMode.STRICT, "auth-parse-salt").redactIfNeeded(serialized)

            assertSameJsonShape(serialized, redacted)
        }

        @Test
        fun offModeLeavesTheAuthBearingSerializedShapeByteIdentical() {
            val serialized =
                toolJson.encodeToString(
                    requestOnly("X-API-Key: wibble=${Sentinel.AUTH_OFF_MODE.value}"),
                )

            val redacted = contextWith(PrivacyMode.OFF, "auth-off-mode-salt").redactIfNeeded(serialized)

            assertEquals(
                serialized,
                redacted,
                "PrivacyMode.OFF sets redactTokens=false, so the auth-bearing payload must be byte-identical",
            )
        }

        /**
         * INHERITED BLOCKER 1 (plan 27-05 test 6). `authHeaderRegex` now consumes
         * `JSON_ESCAPED_HEADER_VALUE` verbatim, so it inherits the shape a "quote not preceded by a
         * backslash" terminator silently destroys: an auth value ending in a backslash as the last
         * content in the JSON string. Encoding doubles the backslash, a one-character negative
         * lookbehind suppresses the terminator, and the match runs to end-of-input — swallowing the
         * closing quote and the closing brace.
         *
         * This test exists because the inheritance must be VERIFIED, not assumed. If it fails, the
         * fragment is wrong for BOTH rules and the fix belongs in the fragment, not here.
         */
        @Test
        fun anAuthHeaderValueEndingInABackslashAtTheEndOfThePayloadStillParsesAsJson() {
            val sentinel = Sentinel.AUTH_BACKSLASH_TAIL.value
            val serialized =
                toolJson.encodeToString(
                    HttpRequestResponse(
                        request = "GET /basket HTTP/1.1\r\nX-API-Key: wibble=$sentinel\\",
                        response = null,
                        notes = null,
                    ),
                )

            assertTrue(
                serialized.contains("\\\\\""),
                "fixture guard: the encoded value must end in a doubled backslash before the closing quote",
            )

            val redacted = contextWith(PrivacyMode.STRICT, "auth-backslash-salt").redactIfNeeded(serialized)

            assertSameJsonShape(serialized, redacted)
            assertFalse(redacted.contains(sentinel), "the auth header value must still be redacted (got: $redacted)")
        }

        /**
         * INHERITED BLOCKER 2 (plan 27-05 test 7), the other half. A REAL multi-line `X-API-Key`
         * value containing an UNESCAPED `"` must still be stripped whole and character-identical to
         * what shipped — which is what the two-branch shape delivers by keeping the quote terminator
         * off the real-line branch. `RedactionTest`'s auth fixtures contain no quote in a value, so
         * this is the assertion that proves the two-branch shape carried across to this rule.
         *
         * The expectation is [SHIPPED_REAL_MULTILINE_AUTH_OUTPUT], captured from the PRE-CHANGE
         * compiled classes, so it cannot have been typed to match the new rule's behaviour.
         */
        @Test
        fun aRealMultiLineAuthHeaderValueContainingAQuoteIsStillStrippedWhole() {
            val fixture = realMultiLine("X-API-Key: a=\"q\"; snork=${Sentinel.AUTH_REAL_MULTILINE_QUOTE.value}")

            val output = Redaction.apply(fixture, STRICT_POLICY, stableHostSalt = "real-multiline-salt")

            assertEquals(
                SHIPPED_REAL_MULTILINE_AUTH_OUTPUT,
                output,
                "multi-line auth-header behaviour must be byte-identical to what shipped",
            )
            assertFalse(
                output.contains(Sentinel.AUTH_REAL_MULTILINE_QUOTE.value),
                "no fragment of a quoted auth value may survive",
            )
        }
    }

    // ── fixtures and shared assertions ────────────────────────────────────────────────────

    /**
     * Mirrors what `ProxyHttpRequestResponse.toSerializableForm()` produces: the raw request and
     * response as `request()?.toString()` renders them, CRLF-terminated, with `notes` from the
     * annotations. The Montoya types themselves are not constructible outside the Burp runtime, so
     * the schema type they return is built directly — the shape under test is the SERIALIZED one, and
     * that is what `toolJson` sees either way.
     */
    private fun proxyHistoryFixture(): HttpRequestResponse =
        HttpRequestResponse(
            request =
                "GET /basket HTTP/1.1\r\n" +
                    "Host: shop.example\r\n" +
                    "Cookie: wibble=${Sentinel.COOKIE_STRICT.value}\r\n" +
                    "X-Request-Id: ${Sentinel.BENIGN_CONTROL.value}\r\n" +
                    "Accept: text/html\r\n\r\n",
            response = "HTTP/1.1 200 OK\r\nContent-Type: text/html\r\n\r\n<html></html>",
            notes = null,
        )

    /** Mirrors `AuditIssue.toSerializableForm()`: the same raw message, one carrier deeper. */
    private fun issueDetailsFixture(): IssueDetails =
        IssueDetails(
            name = "Reflected input",
            detail = "detail",
            remediation = "remediation",
            httpService = HttpService(host = "shop.example", port = 443, secure = true),
            baseUrl = "https://shop.example/",
            severity = AuditIssueSeverity.HIGH,
            confidence = AuditIssueConfidence.FIRM,
            requestResponses =
                listOf(
                    HttpRequestResponse(
                        request =
                            "GET /basket HTTP/1.1\r\n" +
                                "Cookie: wibble=${Sentinel.ISSUE_DETAILS_CARRIER.value}\r\n" +
                                "X-Request-Id: ${Sentinel.BENIGN_CONTROL.value}\r\n\r\n",
                        response = "HTTP/1.1 200 OK\r\n\r\n",
                        notes = null,
                    ),
                ),
            collaboratorInteractions = emptyList(),
            definition =
                AuditIssueDefinition(
                    id = "reflected_input",
                    background = "background",
                    remediation = "remediation",
                    typeIndex = 1,
                ),
        )

    private fun requestOnly(headerBlock: String): HttpRequestResponse =
        HttpRequestResponse(
            request =
                "GET /basket HTTP/1.1\r\n" +
                    "$headerBlock\r\n" +
                    "X-Request-Id: ${Sentinel.BENIGN_CONTROL.value}\r\n\r\n",
            response = null,
            notes = null,
        )

    /**
     * The `notes` carrier. `toSerializableForm()` fills this field from `annotations().notes()`, so
     * unlike `request` its first characters are whatever an ANALYST typed rather than a request
     * line — which is what makes a header at the open of a JSON string reachable rather than
     * theoretical. It is the field the round-3 verification named.
     */
    private fun notesCarrier(notes: String): HttpRequestResponse =
        HttpRequestResponse(
            request = "GET /basket HTTP/1.1\r\nAccept: text/html\r\n\r\n",
            response = null,
            notes = notes,
        )

    /**
     * A REAL multi-line message. This deliberately never touches `toolJson`: its purpose is to pin
     * the branch that shipped, the one that only ever sees genuine CRLFs. No `Host:` header, so the
     * expectation is a plain literal rather than a salt-dependent anonymised host.
     */
    private fun realMultiLine(cookieLine: String): String = "GET /a HTTP/1.1\r\n$cookieLine\r\nAccept: text/html\r\n\r\n"

    private fun assertSameJsonShape(
        before: String,
        after: String,
    ) {
        val beforeObject = toolJson.parseToJsonElement(before).jsonObject
        val afterObject = toolJson.parseToJsonElement(after).jsonObject
        assertEquals(beforeObject.keys, afterObject.keys, "the redacted tool result must keep the same key set")
        assertEquals(beforeObject.size, afterObject.size, "the redacted tool result must keep the same field count")
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
        COOKIE_STRICT("sentinelalfa"),
        COOKIE_BALANCED("sentinelbravo"),
        SET_COOKIE_STRICT("sentinelcharlie"),
        SET_COOKIE_BALANCED("sentineldelta"),
        VARIANT_X_COOKIE("sentinelecho"),
        VARIANT_COOKIE2("sentinelfoxtrot"),
        VARIANT_SET_COOKIE2("sentinelgolf"),
        VARIANT_X_ORIGINAL_COOKIE("sentinelhotel"),
        VARIANT_X_FORWARDED_COOKIE("sentinelindia"),
        SITE_MAP_CARRIER("sentineljuliett"),
        ISSUE_DETAILS_CARRIER("sentinelkilo"),
        OFF_MODE_CONTROL("sentinellima"),
        ONE_BACKSLASH_HAZARD("sentinelmike"),
        TWO_BACKSLASH_HAZARD("sentinelnovember"),
        TRUNCATION_HAZARD("sentineloscar"),
        ESCAPED_QUOTE_HAZARD("sentinelpapa"),
        REAL_MULTILINE_QUOTE("sentinelquebec"),
        REAL_MULTILINE_BACKSLASH("sentinelromeo"),

        // (PRIV-05) 27-05 / D-27-12. The auth family. Each is a PLAIN ALPHANUMERIC TOKEN carrying no
        // `Bearer `/`Basic ` prefix and no dotted `eyJ` segment, so bearerRegex, basicAuthRegex and
        // jwtRegex cannot claim it and an absence assertion can only pass because authHeaderRegex
        // fired. AUTH_BEARER_SHAPED is the deliberate exception, and its test says so.
        AUTH_API_KEY_STRICT("sentinelsierra"),
        AUTH_API_KEY_BALANCED("sentineltango"),
        AUTH_BEARER_SHAPED("sentineluniform"),
        AUTH_JSON_PARSE("sentinelvictor"),
        AUTH_OFF_MODE("sentinelwhiskey"),
        AUTH_BACKSLASH_TAIL("sentinelxray"),
        AUTH_REAL_MULTILINE_QUOTE("sentinelyankee"),

        // (PRIV-05) 27-11. The JSON-string-open family. Each is a bare lowercase alphabetic word
        // like every entry above, and each was checked NON-SUBSTRING against every other entry —
        // `everySentinelInThisFileIsDistinct` enforces both halves, because a sentinel contained in
        // a longer one lets an absence assertion pass while the longer value leaks.
        JSON_STRING_OPEN_STRICT("sentinelzulu"),
        JSON_STRING_OPEN_BALANCED("sentinelnorth"),
        JSON_STRING_OPEN_CONTROL("sentinelsouth"),
        BENIGN_CONTROL("benignidcontrolvalue"),
    }

    private companion object {
        val ALL_SENTINELS: List<String> = Sentinel.entries.map { it.value }

        val STRICT_POLICY: RedactionPolicy = RedactionPolicy.fromMode(PrivacyMode.STRICT)

        /**
         * MEASURED, not typed: the output `Redaction.apply` produced for both `realMultiLine`
         * fixtures against the PRE-CHANGE `Redaction.kt` (commit 20569be~1), captured before the rule
         * was recomposed. The shipped rule replaces the whole logical line from the header name to
         * the end-of-line anchor, so the quoted value of M1 and the trailing backslash of M2 both
         * disappear into the same replacement.
         */
        const val SHIPPED_REAL_MULTILINE_OUTPUT = "GET /a HTTP/1.1\r\nCookie: [STRIPPED]\r\nAccept: text/html\r\n\r\n"

        /**
         * The same discipline for the auth rule (27-05). MEASURED against the compiled classes with
         * `authHeaderRegex` still line-anchored, BEFORE it was recomposed — so a recomposition that
         * changed real multi-line behaviour would fail this rather than redefine it. The shipped rule
         * replaces the whole logical line, so the quoted value disappears into the replacement.
         */
        const val SHIPPED_REAL_MULTILINE_AUTH_OUTPUT = "GET /a HTTP/1.1\r\nX-API-Key: [REDACTED]\r\nAccept: text/html\r\n\r\n"
    }
}
