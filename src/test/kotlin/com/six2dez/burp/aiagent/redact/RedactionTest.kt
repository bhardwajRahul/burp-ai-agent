package com.six2dez.burp.aiagent.redact

import com.six2dez.burp.aiagent.config.Defaults
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

// RFC 5869 Test Case 1 inputs/outputs for the HKDF vector test.
// Source: https://www.rfc-editor.org/rfc/rfc5869 Appendix A.1
private object Rfc5869TestCase1 {
    // IKM = 22 bytes of 0x0b
    val ikm: ByteArray = ByteArray(22) { 0x0b.toByte() }

    // salt = 0x000102...0c (13 bytes)
    val salt: ByteArray = ByteArray(13) { i -> i.toByte() }

    // info = 0xf0f1...f9 (10 bytes)
    val info: ByteArray = ByteArray(10) { i -> (0xf0 + i).toByte() }

    val l = 42

    // Expected PRK (HMAC-SHA256 of salt over IKM):
    // 077709362c2e32df0ddc3f0dc47bba6390b6c73bb50f9c3122ec844ad7c2b3e5
    val expectedPrk: ByteArray =
        byteArrayOf(
            0x07,
            0x77,
            0x09,
            0x36,
            0x2c,
            0x2e,
            0x32,
            0xdf.toByte(),
            0x0d,
            0xdc.toByte(),
            0x3f,
            0x0d,
            0xc4.toByte(),
            0x7b,
            0xba.toByte(),
            0x63,
            0x90.toByte(),
            0xb6.toByte(),
            0xc7.toByte(),
            0x3b,
            0xb5.toByte(),
            0x0f,
            0x9c.toByte(),
            0x31,
            0x22,
            0xec.toByte(),
            0x84.toByte(),
            0x4a,
            0xd7.toByte(),
            0xc2.toByte(),
            0xb3.toByte(),
            0xe5.toByte(),
        )

    // Expected OKM (first 42 bytes):
    // 3cb25f25faacd57a90434f64d0362f2a2d2d0a90cf1a5a4c5db02d56ecc4c5bf
    // 34007208d5b887185865
    val expectedOkm: ByteArray =
        byteArrayOf(
            0x3c,
            0xb2.toByte(),
            0x5f,
            0x25,
            0xfa.toByte(),
            0xac.toByte(),
            0xd5.toByte(),
            0x7a,
            0x90.toByte(),
            0x43,
            0x4f,
            0x64,
            0xd0.toByte(),
            0x36,
            0x2f,
            0x2a,
            0x2d,
            0x2d,
            0x0a,
            0x90.toByte(),
            0xcf.toByte(),
            0x1a,
            0x5a,
            0x4c,
            0x5d,
            0xb0.toByte(),
            0x2d,
            0x56,
            0xec.toByte(),
            0xc4.toByte(),
            0xc5.toByte(),
            0xbf.toByte(),
            0x34,
            0x00,
            0x72,
            0x08,
            0xd5.toByte(),
            0xb8.toByte(),
            0x87.toByte(),
            0x18,
            0x58,
            0x65,
        )
}

// (PRIV-05) SC3: the single sentinel value used by the whole sensitive-key corpus. It is chosen so
// it matches NO other redaction rule — not bearer-prefixed, not basic-prefixed, does not start with
// "eyJ" and contains no '=' — so its disappearance is attributable to the sensitive-key expression
// alone and to nothing else in the pipeline.
private const val SC3_SENTINEL = "SENTINEL-VALUE-9F2A7C"

class RedactionTest {
    @AfterEach
    fun resetCustomPatterns() {
        // Prevent custom-pattern bleed across tests: reset after each test.
        Redaction.setCustomPatterns(emptyList())
    }

    @Test
    fun strictModeStripsCookiesTokensAndHosts() {
        val input =
            """
            GET / HTTP/1.1
            Host: example.com
            Cookie: a=b
            Authorization: Bearer abc.def.ghi

            """.trimIndent()

        val policy = RedactionPolicy.fromMode(PrivacyMode.STRICT)
        val output = Redaction.apply(input, policy, stableHostSalt = "salt")

        assertTrue(output.contains("Cookie: [STRIPPED]"))
        assertTrue(output.contains("Authorization: [REDACTED]"))
        assertTrue(output.contains("Host: host-"))
    }

    @Test
    fun hostAnonymizationIsStablePerSalt() {
        val a = Redaction.anonymizeHost("example.com", "salt-a")
        val b = Redaction.anonymizeHost("example.com", "salt-a")
        val c = Redaction.anonymizeHost("example.com", "salt-b")

        assertEquals(a, b)
        assertTrue(a != c)
    }

    @Test
    fun balancedModeRedactsCustomAuthHeaders() {
        val input =
            """
            GET / HTTP/1.1
            Host: example.com
            X-Auth-Token: abc123
            X-Access-Token: xyz789
            X-CSRF-Token: csrf123
            X-Api-Secret: secret!
            Authorization: Basic dXNlcjpwYXNz

            """.trimIndent()

        val policy = RedactionPolicy.fromMode(PrivacyMode.BALANCED)
        val output = Redaction.apply(input, policy, stableHostSalt = "salt")

        assertTrue(output.contains("X-Auth-Token: [REDACTED]"), "X-Auth-Token must be redacted")
        assertTrue(output.contains("X-Access-Token: [REDACTED]"), "X-Access-Token must be redacted")
        assertTrue(output.contains("X-CSRF-Token: [REDACTED]"), "X-CSRF-Token must be redacted")
        assertTrue(output.contains("X-Api-Secret: [REDACTED]"), "X-Api-Secret must be redacted")
        assertTrue(output.contains("Authorization: [REDACTED]"), "Authorization header must be redacted")
        assertTrue(!output.contains("abc123") && !output.contains("xyz789") && !output.contains("dXNlcjpwYXNz"))
    }

    @Test
    fun balancedModeRedactsUrlTokensInQueryStrings() {
        val input =
            """
            GET /api/user?api_key=secret123&token=xyz987&name=alice HTTP/1.1
            Host: example.com
            Referer: https://example.com/callback?access_token=ABC.DEF.GHI&state=open

            """.trimIndent()

        val policy = RedactionPolicy.fromMode(PrivacyMode.BALANCED)
        val output = Redaction.apply(input, policy, stableHostSalt = "salt")

        assertTrue(output.contains("api_key=[REDACTED]"), "api_key query param must be redacted")
        assertTrue(output.contains("token=[REDACTED]"), "token query param must be redacted")
        assertTrue(output.contains("access_token=[REDACTED]"), "access_token query param must be redacted")
        assertTrue(output.contains("name=alice"), "non-sensitive params must not be touched")
        assertTrue(!output.contains("secret123") && !output.contains("xyz987") && !output.contains("ABC.DEF.GHI"))
    }

    @Test
    fun offModePreservesAllTokens() {
        val input =
            """
            GET /api?api_key=secret123 HTTP/1.1
            Authorization: Bearer TOKEN
            X-Auth-Token: abc

            """.trimIndent()
        val policy = RedactionPolicy.fromMode(PrivacyMode.OFF)
        val output = Redaction.apply(input, policy, stableHostSalt = "salt")

        assertTrue(output.contains("api_key=secret123"))
        assertTrue(output.contains("Bearer TOKEN"))
        assertTrue(output.contains("X-Auth-Token: abc"))
    }

    @Test
    fun clearMappings_removesOnlyRequestedSaltOrAll() {
        val anonA = Redaction.anonymizeHost("a.example", "salt-a")
        val anonB = Redaction.anonymizeHost("b.example", "salt-b")
        assertEquals("a.example", Redaction.deAnonymizeHost(anonA, "salt-a"))
        assertEquals("b.example", Redaction.deAnonymizeHost(anonB, "salt-b"))

        Redaction.clearMappings("salt-a")
        assertEquals(null, Redaction.deAnonymizeHost(anonA, "salt-a"))
        assertEquals("b.example", Redaction.deAnonymizeHost(anonB, "salt-b"))

        Redaction.clearMappings()
        assertEquals(null, Redaction.deAnonymizeHost(anonB, "salt-b"))
    }

    // PRIV-01: output format test — added for HKDF swap (Task 1 Wave 0 RED)
    @Test
    fun hostAnonymizationFormatIsStable() {
        val result = Redaction.anonymizeHost("example.com", "salt")
        // Assert format only — never hardcode the hex value so the crypto can evolve.
        assertTrue(
            result.matches(Regex("^host-[0-9a-f]{12}\\.local$")),
            "Expected format host-<12hex>.local but got: $result",
        )
    }

    // PRIV-01: RFC 5869 Test Case 1 HKDF vector — proves the HMAC-SHA256 extract/expand
    // math is correct against a published reference vector.
    // Source: https://www.rfc-editor.org/rfc/rfc5869 Appendix A.1
    @Test
    fun hkdfMatchesRfc5869Vector() {
        // Access the internal HKDF helpers via the test-internal seam exposed on Redaction.
        val prk = Redaction.testHkdfExtract(Rfc5869TestCase1.salt, Rfc5869TestCase1.ikm)
        assertEquals(
            Rfc5869TestCase1.expectedPrk.toList(),
            prk.toList(),
            "PRK must match RFC 5869 Test Case 1",
        )

        val okm = Redaction.testHkdfExpand(prk, Rfc5869TestCase1.info, Rfc5869TestCase1.l)
        assertEquals(
            Rfc5869TestCase1.expectedOkm.toList(),
            okm.toList(),
            "OKM must match RFC 5869 Test Case 1",
        )
    }

    // PRIV-02: Leading x-www-form-urlencoded field (no leading ?/&) must be redacted in STRICT
    // and BALANCED. This is the documented gap: the old [?&]-only urlTokenParamRegex missed the
    // first field of a body like apikey=sk-abc123&user=bob.
    @Test
    fun bodyFormLeadingFieldRedacted() {
        val body = "apikey=sk-abc123&user=bob"

        for (mode in listOf(PrivacyMode.STRICT, PrivacyMode.BALANCED)) {
            val policy = RedactionPolicy.fromMode(mode)
            val output = Redaction.apply(body, policy, stableHostSalt = "salt")
            assertTrue(output.contains("apikey=[REDACTED]"), "$mode: leading form field apikey must be redacted")
            assertTrue(output.contains("user=bob"), "$mode: non-sensitive param user must NOT be touched")
            assertFalse(output.contains("sk-abc123"), "$mode: original secret value must not appear")
        }
    }

    // PRIV-02: Known-sensitive JSON keys must be redacted (key-scoped — only the value under
    // a sensitive key name is replaced). Non-sensitive keys must be left untouched.
    @Test
    fun bodyJsonSecretKeysRedacted() {
        val body = """{"api_key":"sk-xyz","name":"alice","token":"abc"}"""

        for (mode in listOf(PrivacyMode.STRICT, PrivacyMode.BALANCED)) {
            val policy = RedactionPolicy.fromMode(mode)
            val output = Redaction.apply(body, policy, stableHostSalt = "salt")
            assertTrue(output.contains("\"api_key\":\"[REDACTED]\""), "$mode: api_key JSON value must be redacted")
            assertTrue(output.contains("\"token\":\"[REDACTED]\""), "$mode: token JSON value must be redacted")
            assertTrue(output.contains("\"name\":\"alice\""), "$mode: non-sensitive name key must NOT be touched")
            assertFalse(output.contains("sk-xyz"), "$mode: original api_key value must not appear")
            assertFalse(output.contains("\"abc\""), "$mode: original token value must not appear")
        }
    }

    // WR-03: JSON values under a sensitive key that are numeric / boolean / null (not quoted
    // strings) must also be redacted. The old "(key)":"[^"]*" pattern matched only quoted string
    // values, silently leaking numeric secrets such as {"token":12345}. Every redacted value is
    // normalized to the quoted token "[REDACTED]" so the output stays valid JSON.
    @Test
    fun bodyJsonUnquotedSecretValuesRedacted() {
        // token / api_key / secret / sid are in SENSITIVE_KEYS; name / balance are not.
        val body = """{"token":12345,"api_key":true,"secret":null,"sid":-42,"balance":99.5,"name":"alice"}"""

        for (mode in listOf(PrivacyMode.STRICT, PrivacyMode.BALANCED)) {
            val policy = RedactionPolicy.fromMode(mode)
            val output = Redaction.apply(body, policy, stableHostSalt = "salt")
            assertTrue(output.contains("\"token\":\"[REDACTED]\""), "$mode: numeric token value must be redacted")
            assertTrue(output.contains("\"api_key\":\"[REDACTED]\""), "$mode: boolean api_key value must be redacted")
            assertTrue(output.contains("\"secret\":\"[REDACTED]\""), "$mode: null secret value must be redacted")
            assertTrue(output.contains("\"sid\":\"[REDACTED]\""), "$mode: negative-int sid value must be redacted")
            assertTrue(output.contains("\"name\":\"alice\""), "$mode: non-sensitive name key must NOT be touched")
            assertTrue(output.contains("\"balance\":99.5"), "$mode: non-sensitive numeric balance must NOT be touched")
            assertFalse(output.contains("12345"), "$mode: original numeric token must not appear")
        }
    }

    // (PRIV-05) SC3 helpers. One key is rendered into each of the three consumer contexts, so a
    // single corpus exercises urlTokenParamRegex, formBodyParamRegex and jsonSecretKeyRegex —
    // all three are driven by the same shared key expression, so widening it widens all three.
    private fun sc3Query(key: String) = "https://example.com/a?$key=$SC3_SENTINEL&name=alice"

    private fun sc3Form(key: String) = "$key=$SC3_SENTINEL&user=bob"

    private fun sc3Json(key: String) = """{"$key":"$SC3_SENTINEL","name":"alice"}"""

    private fun redactWith(
        input: String,
        mode: PrivacyMode,
    ): String = Redaction.apply(input, RedactionPolicy.fromMode(mode), stableHostSalt = "salt")

    // Context label -> redacted output, so every assertion message can name the exact cell that
    // failed rather than reporting a bare "expected true".
    private fun sc3Contexts(
        key: String,
        mode: PrivacyMode,
    ): Map<String, String> =
        mapOf(
            "query string" to redactWith(sc3Query(key), mode),
            "form body" to redactWith(sc3Form(key), mode),
            "JSON body" to redactWith(sc3Json(key), mode),
        )

    // The benign companion parameter carried by each context. Asserting it survives in the SAME
    // pass that asserts the secret disappeared is what proves the widening stayed key-scoped
    // instead of degrading into a blanket rule (T-21-06).
    private val sc3Survivors =
        mapOf(
            "query string" to "name=alice",
            "form body" to "user=bob",
            "JSON body" to "\"name\":\"alice\"",
        )

    // (PRIV-05) SC3 / D-11: the 31 key names whose value MUST be redacted. The first 18 were
    // measured as passing through UNREDACTED in STRICT and BALANCED before the key expression
    // landed — that is the PRIV-05 defect class. The remaining 13 already redacted and are carried
    // here as the monotonicity half: the widening must not take any of them away.
    private val sc3MustRedactKeys =
        listOf(
            // Previously missed: compound, separator-delimited and vendor key names.
            "auth_token",
            "api-key",
            "X-Session-Id",
            "api.key",
            "session_id",
            "_csrf",
            "connect.sid",
            "remember_me",
            "JSESSIONID",
            "PHPSESSID",
            "csrftoken",
            "ASP.NET_SessionId",
            "laravel_session",
            "user_api_key",
            "x-auth-token",
            "auth-token",
            "access-token",
            "XSRF-TOKEN",
            // Already redacted before the change — must still redact after it.
            "access_token",
            "api_key",
            "apikey",
            "auth",
            "token",
            "key",
            "secret",
            "password",
            "pwd",
            "session",
            "sid",
            "code",
            "SESSION",
        )

    // (PRIV-05) SC3: the 21 benign key names whose value must survive untouched.
    private val sc3BenignKeys =
        listOf(
            "keyboard_layout",
            "codename",
            "sidebar",
            "keychain",
            "passwordless_enabled",
            "description",
            "codes",
            "tokenizer",
            "monkey",
            "broken",
            "secretary",
            "authority",
            "encoded",
            "decode_me",
            "username",
            "name",
            "email",
            "q",
            "page",
            "filename",
            "locale",
        )

    // (PRIV-05) SC3 / D-11: real-world sensitive key names must have their value redacted in all
    // three consumer contexts, under STRICT and BALANCED.
    //
    // Before the key expression, a key had to be EXACTLY one of the twelve vocabulary words. That
    // is why auth_token missed (auth was followed by '_', not '='), why only a cookie literally
    // named "session" was caught, and why JSESSIONID, PHPSESSID, connect.sid, csrftoken,
    // remember_me, api-key and X-Session-Id all reached the backend verbatim.
    //
    // Each iteration also asserts the context's benign companion survives, so a regression that
    // "fixes" this test by redacting everything fails it instead.
    @Test
    fun sensitiveKeyNamesRedacted() {
        assertEquals(31, sc3MustRedactKeys.size, "SC3 must-redact corpus must stay at 31 keys")

        for (mode in listOf(PrivacyMode.STRICT, PrivacyMode.BALANCED)) {
            for (key in sc3MustRedactKeys) {
                for ((context, output) in sc3Contexts(key, mode)) {
                    assertFalse(
                        output.contains(SC3_SENTINEL),
                        "$mode / $context: the value of sensitive key '$key' must be redacted",
                    )
                    assertTrue(
                        output.contains(sc3Survivors.getValue(context)),
                        "$mode / $context: benign companion must survive alongside '$key'",
                    )
                }
            }
        }
    }

    // (PRIV-05) SC3 / D-12: this is a REGRESSION GUARD on the new mechanism, NOT a fix.
    //
    // keyboard_layout and codename are NOT over-redacted today and never were: formBodyParamRegex's
    // (^|[?&])…= already delimits the word on both sides, so the exact-word alternation could not
    // match them either. These assertions are therefore green BEFORE and AFTER the key expression
    // landed, by design. Phase 20's SC4 discipline says a test green on both sides has tested
    // nothing, so this one is explicitly labelled a guard rather than counted as evidence of a fix;
    // a task framed as "stop over-redacting keyboard_layout" would have been a no-op.
    //
    // What it does buy: the widening is the moment these could START failing, and the 21 keys below
    // are the standing proof that it did not happen. This is also why no benign-key denylist exists
    // in Redaction.kt (D-12) — there was measurably nothing to guard against.
    @Test
    fun benignKeyNamesNotRedacted() {
        assertEquals(21, sc3BenignKeys.size, "SC3 must-not-redact corpus must stay at 21 keys")

        for (key in sc3BenignKeys) {
            for ((context, output) in sc3Contexts(key, PrivacyMode.STRICT)) {
                assertTrue(
                    output.contains(SC3_SENTINEL),
                    "STRICT / $context: the value of benign key '$key' must NOT be redacted",
                )
            }
        }
    }

    // (PRIV-05) SC3 / D-13: camelCase key matching.
    //
    // authToken, accessToken and userSessionId are extremely common modern JSON key names and are
    // NOT reachable by the separator rule alone (auth is followed by 'T', which is alphanumeric).
    // The camelCase boundary is written with Java's inline flag-off group (?-i:...) because under
    // the consumers' (?i) the class [A-Z] also matches lowercase.
    //
    // ACCEPTED OVER-REDACTIONS: codeName, keyName and tokenCount are asserted as REDACTED on
    // purpose. They are the exact, maintainer-confirmed price of D-13 — recorded here as deliberate
    // behaviour rather than discovered in the field. They over-redact (the fail-safe direction) and
    // are structurally identical to the already-accepted token_bucket_size case.
    // REVERT POINT: deleting the (?-i:...) alternative from WORD_BEFORE and WORD_AFTER in
    // Redaction.kt drops D-13 entirely — it loses nothing SC3 requires and removes these three.
    //
    // keyboardLayout and monkeyBars must still survive: they are what distinguishes a camelCase
    // boundary from a plain substring match. SC3's literal all-lowercase codename is covered by
    // benignKeyNamesNotRedacted and is unaffected by D-13.
    @Test
    fun camelCaseKeysRedactedWithAcceptedOverRedactions() {
        for (key in listOf("authToken", "accessToken", "userSessionId")) {
            for ((context, output) in sc3Contexts(key, PrivacyMode.STRICT)) {
                assertFalse(
                    output.contains(SC3_SENTINEL),
                    "STRICT / $context: camelCase key '$key' must be redacted (D-13 gain)",
                )
            }
        }

        for (key in listOf("codeName", "keyName", "tokenCount")) {
            for ((context, output) in sc3Contexts(key, PrivacyMode.STRICT)) {
                assertFalse(
                    output.contains(SC3_SENTINEL),
                    "STRICT / $context: '$key' is an ACCEPTED D-13 over-redaction and must stay redacted",
                )
            }
        }

        for (key in listOf("keyboardLayout", "monkeyBars")) {
            for ((context, output) in sc3Contexts(key, PrivacyMode.STRICT)) {
                assertTrue(
                    output.contains(SC3_SENTINEL),
                    "STRICT / $context: '$key' has no lower-to-upper boundary after the word and must NOT be redacted",
                )
            }
        }
    }

    // (PRIV-05) SC1 / D-09: every cookie value in the passive scanner's dedicated cookie section
    // must be absent in STRICT and in BALANCED, with the cookie NAME preserved.
    //
    // The scanner splits the Cookie: header on ';' into bare name=value lines, dropping the prefix
    // cookieHeaderRegex keys on — so the header-line rule never sees these and every value reached
    // the AI backend verbatim. Asserted PER NAME rather than on an aggregate: a single "no secret
    // survives" assertion would pass while four of the six leaked.
    //
    // abtest_bucket is the entry only the section rule saves. The widened key expression from plan
    // 21-04 does not recognise that name, which is why both mechanisms are kept rather than treated
    // as redundant.
    @Test
    fun cookieSectionValuesRedactedPerName() {
        val cookieValues =
            mapOf(
                "JSESSIONID" to "8F3A9C2B7E1D4A6F0B5C8E2D",
                "PHPSESSID" to "abc123def456",
                "connect.sid" to "s%3ARZxYqL9.opaquevalue",
                "auth_token" to "secretvalue123",
                "csrftoken" to "abcdef",
                "abtest_bucket" to "OPAQUE_VALUE_XYZ",
            )

        // The blank line and the following section exercise the span bound: the rule must stop at
        // the end of the cookie section rather than running on to the end of the blob.
        val blob =
            """
            === COOKIES ===
            JSESSIONID=8F3A9C2B7E1D4A6F0B5C8E2D
            PHPSESSID=abc123def456
            connect.sid=s%3ARZxYqL9.opaquevalue
            auth_token=secretvalue123
            csrftoken=abcdef
            abtest_bucket=OPAQUE_VALUE_XYZ

            === PARAMETERS ===
            q=red running shoes (URL)
            """.trimIndent()

        for (mode in listOf(PrivacyMode.STRICT, PrivacyMode.BALANCED)) {
            val output = redactWith(blob, mode)
            for ((name, value) in cookieValues) {
                assertFalse(
                    output.contains(value),
                    "$mode: the value of cookie '$name' must be absent from the redacted prompt",
                )
                assertTrue(
                    output.lines().contains("$name=[REDACTED]"),
                    "$mode: cookie '$name' must keep its name and lose only its value",
                )
            }
            assertTrue(
                output.lines().contains("q=red running shoes (URL)"),
                "$mode: a line past the cookie section's blank-line terminator must be untouched",
            )
        }
    }

    // (PRIV-05) SC2: request.parameters() surfaces COOKIE-typed parameters into the parameters
    // section as "name=value (TYPE)" — the same values leaking a second way, in a section the
    // section-scoped cookie rule deliberately does not cover.
    //
    // The (URL) and (BODY) survivors are the point of the test: they are what proves the rule is
    // type-discriminating rather than a blanket line rule. The rejected context-free "^name=value$"
    // alternative would have mangled both and still could not have satisfied SC2, because the
    // trailing type suffix defeats its end anchor.
    //
    // abtest_bucket is the DECISIVE line and was added after measurement, not from the spec. With
    // only JSESSIONID and remember_me present this test passed with the type-suffix rule unwired:
    // both names are already reachable by plan 21-04's key expression, so formBodyParamRegex
    // redacted them from the leading-field position and the assertions could not see the defect.
    // An unremarkable cookie name is the only input the type-suffix rule alone can save. Do not
    // remove it.
    @Test
    fun cookieTypedParametersRedacted() {
        val blob =
            """
            === PARAMETERS ===
            JSESSIONID=8F3A9C2B7E1D4A6F0B5C8E2D (COOKIE)
            remember_me=deadbeefcafe (COOKIE)
            abtest_bucket=OPAQUE_PARAM_XYZ (COOKIE)
            q=red running shoes (URL)
            quantity=2 (BODY)
            """.trimIndent()

        for (mode in listOf(PrivacyMode.STRICT, PrivacyMode.BALANCED)) {
            val lines = redactWith(blob, mode).lines()

            assertTrue(
                lines.contains("JSESSIONID=[REDACTED] (COOKIE)"),
                "$mode: a COOKIE-typed parameter must keep both its name and its type suffix",
            )
            assertTrue(
                lines.contains("remember_me=[REDACTED] (COOKIE)"),
                "$mode: EVERY COOKIE-typed parameter is redacted, not only the session-named ones",
            )
            assertTrue(
                lines.contains("abtest_bucket=[REDACTED] (COOKIE)"),
                "$mode: an unremarkably-named COOKIE-typed parameter is saved by the type suffix alone",
            )
            assertFalse(
                lines.any {
                    it.contains("8F3A9C2B7E1D4A6F0B5C8E2D") ||
                        it.contains("deadbeefcafe") ||
                        it.contains("OPAQUE_PARAM_XYZ")
                },
                "$mode: no COOKIE-typed parameter value may survive",
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

    // (PRIV-05) SC1 / D-10: the named security regression guard for section-header poisoning.
    //
    // This reproduces a DEMONSTRATED exploit, not a hypothetical one, so it is a security guard
    // rather than a robustness nicety. ScanKnowledgeBase.recordTechStack builds its technology list
    // from the response's Server / X-Powered-By / X-AspNet-Version / X-Generator headers — all
    // attacker-controlled — and buildContextSummary emits that list into the prior-knowledge block,
    // which the emitter appends BEFORE the cookie section. A first-occurrence-only indexOf was
    // measured redacting the decoy below while BOTH genuine cookie values leaked intact; only
    // iterating every occurrence of the header saves them.
    //
    // abtest_bucket is the load-bearing half. Its name is deliberately unreachable by the widened
    // key expression from plan 21-04, so nothing but the section rule can save it — swapping it for
    // a name the key expression already covers would make this test pass with the defect present.
    // The decoy's own value is over-redacted as a result; that residual is accepted and documented
    // in Redaction.redactCookieSections.
    @Test
    fun cookieSectionDecoyDoesNotShieldRealSection() {
        val blob =
            """
            === PRIOR KNOWLEDGE ===
            Detected technologies: === COOKIES ===
            decoy=DECOY_VALUE

            === COOKIES ===
            JSESSIONID=REAL_SESSION_SECRET
            abtest_bucket=OPAQUE_VALUE_XYZ
            """.trimIndent()

        val output = redactWith(blob, PrivacyMode.STRICT)

        assertFalse(
            output.contains("REAL_SESSION_SECRET"),
            "STRICT: a decoy section header must not shield the real session cookie value",
        )
        assertFalse(
            output.contains("OPAQUE_VALUE_XYZ"),
            "STRICT: a decoy section header must not shield an unremarkably-named real cookie",
        )
        assertTrue(
            output.lines().contains("abtest_bucket=[REDACTED]"),
            "STRICT: the real cookie section is redacted in place, with its names preserved",
        )
    }

    // PRIV-02: OFF mode must leave bodies completely untouched — no form-body or JSON redaction.
    @Test
    fun offModePreservesBodies() {
        val formBody = "apikey=sk-abc123&user=bob"
        val jsonBody = """{"api_key":"sk-xyz","name":"alice","token":"abc"}"""

        val policy = RedactionPolicy.fromMode(PrivacyMode.OFF)

        val formOutput = Redaction.apply(formBody, policy, stableHostSalt = "salt")
        assertEquals(formBody, formOutput, "OFF mode must not touch form body")

        val jsonOutput = Redaction.apply(jsonBody, policy, stableHostSalt = "salt")
        assertEquals(jsonBody, jsonOutput, "OFF mode must not touch JSON body")
    }

    // PRIV-02: User-supplied custom pattern applied in STRICT and BALANCED, inactive in OFF.
    // The test resets patterns in @AfterEach to avoid cross-test bleed.
    @Test
    fun customPatternRedactsInStrictAndBalanced() {
        Redaction.setCustomPatterns(listOf("\\bSECRET-\\d{4}\\b"))

        val input = "Content: SECRET-1234 is the value"

        for (mode in listOf(PrivacyMode.STRICT, PrivacyMode.BALANCED)) {
            val policy = RedactionPolicy.fromMode(mode)
            val output = Redaction.apply(input, policy, stableHostSalt = "salt")
            assertTrue(output.contains("[REDACTED]"), "$mode: custom pattern must redact SECRET-1234")
            assertFalse(output.contains("SECRET-1234"), "$mode: original value must not appear after redaction")
        }

        // (PRIV-06) D-05: this limb is DELIBERATELY INVERTED. It previously asserted
        // assertEquals(input, offOutput) because the custom-pattern loop lived inside the
        // redactTokens branch and was therefore inert under OFF. The loop now sits outside that
        // branch, so a user's custom patterns are a "never send this, ever" list that is
        // independent of the privacy mode: OFF means "no BUILT-IN redaction", not "no redaction at
        // all". 21-VALIDATION.md records this as one of SC6's two named exceptions, so it must not
        // be read as a regression. The method NAME is kept unchanged on purpose — 21-CONTEXT.md,
        // 21-VALIDATION.md and 21-VERIFICATION.md all refer to this test by name and a rename would
        // break that traceability.
        val offPolicy = RedactionPolicy.fromMode(PrivacyMode.OFF)
        val offOutput = Redaction.apply(input, offPolicy, stableHostSalt = "salt")
        assertTrue(offOutput.contains("[REDACTED]"), "OFF: a custom pattern must still redact SECRET-1234")
        assertFalse(offOutput.contains("SECRET-1234"), "OFF: the original custom-pattern value must not survive")
    }

    // PRIV-02 / CR-01: regression — custom patterns carried on a loaded settings object must
    // become active when seeded into the engine (the App.initialize startup step), NOT only
    // after a manual re-save. The previous bug was that App.initialize never called
    // setCustomPatterns(settings.customRedactionPatterns), so on every Burp launch the live
    // custom-pattern list silently reset to empty and configured secrets leaked to the backend.
    // This exercises the load -> seed -> apply contract that App.kt now relies on, using a
    // pattern sourced from a settings object rather than set directly inline.
    @Test
    fun customPatternsFromSettingsAreActiveAfterSeeding() {
        // Stands in for AgentSettings.customRedactionPatterns as returned by
        // AgentSettingsRepository.load() — the persisted, save-validated pattern list that
        // App.initialize must push into the engine at startup.
        val persistedPatterns = listOf("\\bINTERNAL-[A-Z0-9]{6}\\b")

        // Sanity: the engine starts with NO custom patterns active (simulating a fresh launch
        // before the seeding step). The pattern must NOT redact yet.
        Redaction.setCustomPatterns(emptyList())
        val input = "Leak check: INTERNAL-ABC123 must be stripped"
        val strict = RedactionPolicy.fromMode(PrivacyMode.STRICT)
        val beforeSeed = Redaction.apply(input, strict, stableHostSalt = "salt")
        assertTrue(beforeSeed.contains("INTERNAL-ABC123"), "Pre-seed: custom pattern must be inactive")

        // The App.initialize seeding step: push the loaded settings' patterns into the engine.
        // This is exactly Redaction.setCustomPatterns(settings.customRedactionPatterns).
        Redaction.setCustomPatterns(persistedPatterns)

        val afterSeed = Redaction.apply(input, strict, stableHostSalt = "salt")
        assertTrue(afterSeed.contains("[REDACTED]"), "Post-seed: loaded custom pattern must redact")
        assertFalse(afterSeed.contains("INTERNAL-ABC123"), "Post-seed: original secret must not appear")
    }

    // PRIV-02: A body larger than Defaults.MAX_REDACTION_BODY_CHARS must be short-circuited.
    // The body-stage redaction is skipped; the call must return promptly and not throw.
    // The over-cap secret may remain (documented size-cap behaviour).
    @Test
    fun oversizeBodySkippedSafely() {
        // Generate a body larger than the cap (cap is ~1 MB = 1_000_000 chars).
        val oversizeBody = "apikey=" + "x".repeat(Defaults.MAX_REDACTION_BODY_CHARS + 10)
        val policy = RedactionPolicy.fromMode(PrivacyMode.STRICT)

        // The primary assertion: the call must return without throwing or hanging.
        val start = System.currentTimeMillis()
        val output = Redaction.apply(oversizeBody, policy, stableHostSalt = "salt")
        val elapsed = System.currentTimeMillis() - start

        // Should return in well under a second (body stage is skipped entirely).
        assertTrue(elapsed < 5_000, "Oversize body must short-circuit quickly; took ${elapsed}ms")
        // The output must be a string (not null, not empty) — the call completed.
        assertTrue(output.isNotEmpty(), "Output must be non-empty even when oversize body is skipped")
    }
}
