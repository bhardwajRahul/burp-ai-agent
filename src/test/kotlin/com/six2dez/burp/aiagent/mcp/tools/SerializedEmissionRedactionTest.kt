package com.six2dez.burp.aiagent.mcp.tools

import burp.api.montoya.MontoyaApi
import burp.api.montoya.core.BurpSuiteEdition
import com.six2dez.burp.aiagent.mcp.McpRequestLimiter
import com.six2dez.burp.aiagent.mcp.McpToolContext
import com.six2dez.burp.aiagent.mcp.schema.HttpRequestResponse
import com.six2dez.burp.aiagent.redact.PrivacyMode
import com.six2dez.burp.aiagent.redact.Redaction
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.Answers
import org.mockito.kotlin.mock

/**
 * (PRIV-05) Phase 27 plan 27-04 — the red probe the phase verifier named as missing.
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
 * asserting against a shape that is no longer emitted.
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

    @Nested
    inner class ProxyHistoryCarrier {
        @Test
        fun canonicalCookieDoesNotSurviveTheSerializedProxyHistoryShapeUnderStrict() {
            val serialized = toolJson.encodeToString(proxyHistoryFixture())

            val redacted = contextWith(PrivacyMode.STRICT, "serialized-strict-salt").redactIfNeeded(serialized)

            assertFalse(
                redacted.contains(COOKIE_STRICT_SENTINEL),
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
            val before = toolJson.parseToJsonElement(serialized).jsonObject
            val after = toolJson.parseToJsonElement(redacted).jsonObject
            assertEquals(before.keys, after.keys, "the redacted tool result must keep the same key set")
            assertEquals(before.size, after.size, "the redacted tool result must keep the same field count")
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
                redacted.contains(BENIGN_CONTROL),
                "negative control: a value in a non-cookie header must survive, so a pass cannot be " +
                    "produced by blanket destruction of the payload (got: $redacted)",
            )
        }
    }

    // ── fixtures ──────────────────────────────────────────────────────────────────────────

    /**
     * Mirrors what `ProxyHttpRequestResponse.toSerializableForm()` produces: the raw request and
     * response as `request()?.toString()` renders them, CRLF-terminated, with `notes` from the
     * annotations. The Montoya types themselves are not constructible outside the Burp runtime, so
     * the schema type it returns is built directly — the shape under test is the SERIALIZED one, and
     * that is what `toolJson` sees either way.
     */
    private fun proxyHistoryFixture(): HttpRequestResponse =
        HttpRequestResponse(
            request =
                "GET /basket HTTP/1.1\r\n" +
                    "Host: shop.example\r\n" +
                    "Cookie: wibble=$COOKIE_STRICT_SENTINEL\r\n" +
                    "X-Request-Id: $BENIGN_CONTROL\r\n" +
                    "Accept: text/html\r\n\r\n",
            response = "HTTP/1.1 200 OK\r\nContent-Type: text/html\r\n\r\n<html></html>",
            notes = null,
        )

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

    private companion object {
        const val COOKIE_STRICT_SENTINEL = "sentinelalfa"
        const val BENIGN_CONTROL = "benignidcontrolvalue"
    }
}
