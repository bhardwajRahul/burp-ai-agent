package com.six2dez.burp.aiagent.redact

import com.six2dez.burp.aiagent.config.Defaults
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.CopyOnWriteArrayList

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

// (PRIV-06) D-03: the injected clock for the truncation-notice window. Named constants rather than
// inline literals so the relationship to the 10 s window is readable: T_INSIDE_WINDOW is 5 s after
// T0 and must be suppressed, T_AFTER_WINDOW is 11 s after T0 and must emit. Nothing here sleeps.
private const val T0 = 1_000_000L
private const val T_INSIDE_WINDOW = 1_005_000L
private const val T_AFTER_WINDOW = 1_011_000L

// (PRIV-06) CR-02 / WR-06: parameters of the shifted-fixture sweep below. Named rather than inlined
// so the relationship between them is the argument, not a coincidence of three literals.
//
// PAD_LINE_CHARS is the length of one filler line, and therefore the PERIOD of the pair's alignment
// against the deterministic window cut: shifting the pair by PAD_LINE_CHARS characters reproduces
// the same alignment one line later. BOUNDARY_SWEEP_SHIFTS is one full period plus slack, so every
// possible alignment is exercised at least once rather than an arbitrary number of them. The
// reviewer's reproduced counterexample sits at shift 7, well inside the first period.
private const val PAD_LINE_CHARS = 20
private const val BOUNDARY_SWEEP_SHIFTS = 24

// How far past the window width the fixture runs, so the pair is followed by a genuine second
// window rather than sitting at the very end of the input.
private const val SWEEP_TAIL_CHARS = 800

// (PRIV-06) CR-04 / T-21-33: parameters of the newline-free oversize fixture below.
//
// SIZING IS THE ARGUMENT, not a round number. A newline-free body becomes exactly ONE window at any
// size, because windowEnd gives an over-width line its own window and a body with no '\n' is a
// single line. The multiplier therefore does not control how many windows there are — it controls
// the cost of the ONE jsonSecretKeyRegex pass that has to exceed the 50 ms per-pattern deadline for
// the defect's precondition to be met at all. Dense newline-free JSON measures ~31 ms/MB on Apple
// Silicon / JDK 21, so 2x the window width is 62-66 ms: over the deadline, but only by 25%, and a
// margin that thin is how a fixture silently stops reproducing on faster hardware. 4x is ~124 ms,
// roughly 2.5x over, which holds on hardware materially faster than the reference machine.
private const val NEWLINE_FREE_WINDOW_MULTIPLIER = 4

// The repeating minified-JSON fragment. No '=', no whitespace, no newline — the exact shape
// toolJson.encodeToString(...) emits into McpToolContext.redactIfNeeded, which is what makes CR-04
// a default-configuration defect rather than a pathological one. Neither "id" nor "name" is in
// SENSITIVE_WORDS or KNOWN_SESSION_KEYS, so the filler cannot itself produce a "[REDACTED]" and
// create a false positive on the assertions below.
private const val NEWLINE_FREE_FRAGMENT = """{"id":123,"name":"alice"},"""

// The planted pair. "api_key" is in SENSITIVE_WORDS, so jsonSecretKeyRegex reaches it — and nothing
// else in either stage can (see the fixture-reachability note on the test itself).
private const val NEWLINE_FREE_SECRET_PAIR = """{"api_key":"SC4-NEWLINE-SECRET-9"},"""

// (PRIV-06) CR-04: the non-whitespace half of the value-terminating set splitPoint prefers to cut
// just after when a window has no line boundary at all. Mirrored here rather than reached into,
// because the source constant is private to Redaction; the comment on SAFE_CUT_SEARCH_CHARS derives
// this set from the value classes of the three built-in body rules ([^&\s"'<>]+ for the form and
// URL rules, and a '"'-delimited string or a scalar followed by ','/'}'/']' for JSON), and the test
// below asserts that the shipped cut actually lands on it.
private const val SAFE_CUT_TERMINATORS = "&,}]"

// Mirrors Redaction.isSafeCutTerminator so the anti-vacuity guards below can state a fixture's
// misalignment in the same terms the implementation uses, rather than by hand-picking a character.
private fun isSafeCutTerminatorForTest(c: Char): Boolean = c in SAFE_CUT_TERMINATORS || c.isWhitespace()

// (PRIV-06) CR-04: the minified-JSON fixture for splitPointPrefersASafeCutBoundaryInMinifiedJson.
//
// ITS LENGTH IS LOAD-BEARING, and that is not a theoretical worry — it is a defect this plan's own
// mutation check caught. The first version repeated a 10-character fragment whose exact midpoint
// landed immediately after a ',' by pure arithmetic coincidence. The test then passed against a
// mutation that deleted the forward search entirely and always returned the midpoint: a vacuous test
// of exactly the kind WR-05 exists to stamp out, very nearly shipped inside the plan that closes
// WR-05. Twelve characters times 167 repetitions puts the midpoint of the 2 004-character window
// after a ':' instead, which no terminator set contains, so only a real forward search can satisfy
// the assertion. The guard in the test asserts that misalignment rather than trusting this comment.
private const val SAFE_CUT_JSON_FRAGMENT = """{"kk":"vv"},"""
private const val SAFE_CUT_JSON_REPEATS = 167

// (PRIV-06) CR-04: the line-bearing fixture for splitPointStillCutsAtALineBoundaryWhenOneExists.
//
// THE SPACES AND THE COMMA ARE THE WHOLE POINT, and this too is a defect the mutation checks caught
// rather than a precaution. The first version was a run of hyphenated words with no spaces and no
// commas, so the ONLY safe-cut terminator anywhere in it was the newline itself — '\n' is
// whitespace, and safeCutPoint treats whitespace as a terminator. The test therefore passed against
// a mutation that deleted splitPoint's two line-boundary branches outright and always took the
// character cut, because the forward search then found the newline anyway and the assertion could
// not tell the two paths apart. That mutation is not benign: without the line branches, a window of
// ordinary prose or HTML would be cut at the first SPACE past the midpoint, which is the (?m)^ trap
// reopened in full.
//
// With spaces and a comma present, a naive forward search lands on one of THOSE first, so only the
// backward line search can satisfy the assertion. 26 characters times 41 repetitions also puts the
// midpoint (533) MID-LINE rather than on a line start, so the backward search is genuinely
// exercised instead of being handed a boundary it did not have to look for.
private const val SAFE_CUT_LINE_FIXTURE = "key value, more text here\n"
private const val SAFE_CUT_LINE_REPEATS = 41

// detekt LargeClass, suppressed on the declaration rather than baselined: QUAL-07 forbids growing
// detekt-baseline.xml, and the existing LargeClass entries there are all main-source classes. This
// is the single regression suite for redact/, and its size is the point — the CR-01/CR-03 guards
// added here are only meaningful sitting beside the SC1..SC6 corpus they must not regress. Splitting
// it would scatter the monotonicity canaries across files and make that relationship invisible.
@Suppress("LargeClass")
class RedactionTest {
    @AfterEach
    fun resetCustomPatterns() {
        // Prevent custom-pattern bleed across tests: reset after each test.
        Redaction.setCustomPatterns(emptyList())
    }

    @AfterEach
    fun resetTruncationSignal() {
        // Redaction is a singleton object: a capturing sink left registered here would keep firing
        // on every later test in the shared JVM, and the limiter's window would carry over and make
        // the injected-clock assertions order-dependent.
        Redaction.truncationLogger = null
        Redaction.resetTruncationWindowForTest()
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

    // (PRIV-05) WR-01: the 32 broad-word key names that MUST survive after the narrowing.
    //
    // These are a SEPARATE corpus from sc3BenignKeys on purpose. The 31/21/8 SC3 corpora are the
    // record of what plan 21-04 measured, and ADR-14 and CONCERNS.md both cite those sizes by name;
    // folding WR-01's names into them would rewrite a historical measurement rather than add to it.
    // Both corpora run through the same sc3Contexts helper, so the coverage is identical.
    //
    // The first 28 are the code-review's measured over-redaction list. The last four —
    // key_size, code_version, codeName and keyName — were four of the TEN accepted over-redactions
    // recorded in Redaction.kt before WR-01; they are broad-word driven, so the narrowing freed
    // them and they move from "accepted" to "must not redact".
    //
    // public_key and publicKey sit here because the maintainer-confirmed CREDENTIAL_PREFIXES set
    // does not contain 'public'. That is the deliberate reading of the ruling, not an executor
    // judgement call: a public key is publishable by definition. If that is ever revisited, the
    // change is visible here rather than silent.
    private val wr01BroadWordBenignKeys =
        listOf(
            // The names the analysis prompt cannot function without.
            "status_code",
            "error_code",
            "response_code",
            "http_code",
            "statusCode",
            "errorCode",
            // Ordinary business vocabulary that merely contains 'code'.
            "zip_code",
            "country_code",
            "postal_code",
            "currency_code",
            "language_code",
            "product_code",
            "promo_code",
            "coupon_code",
            "area_code",
            "qr_code",
            // Ordinary storage/indexing vocabulary that merely contains 'key'.
            "primary_key",
            "foreign_key",
            "sort_key",
            "partition_key",
            "cache_key",
            "idempotency_key",
            "row_key",
            "sortKey",
            "cacheKey",
            "zipCode",
            "public_key",
            "publicKey",
            // Formerly ACCEPTED over-redactions, freed by the narrowing.
            "key_size",
            "code_version",
            "codeName",
            "keyName",
        )

    // (PRIV-05) WR-01: the 24 broad-word key names that MUST STILL redact. This is the half that
    // matters — the narrowing is the dangerous direction, and under-redaction is the failure mode
    // that ships a leak. Two mechanisms are covered: whole-key equality ('key', 'code' — which have
    // no code of their own and rely entirely on each consumer's anchors) and the CREDENTIAL_PREFIXES
    // path in every separator shape the prefix rule admits, including the no-separator camelCase
    // and all-lowercase forms.
    private val wr01CredentialBroadWordKeys =
        listOf(
            // Whole-key equality. Also carried in sc3MustRedactKeys; asserted here too because this
            // is the corpus a future narrowing would be measured against.
            "key",
            "code",
            // 'api' prefix, every separator shape.
            "api_key",
            "api-key",
            "api.key",
            "apiKey",
            "apikey",
            "x_api_key",
            "user_api_key",
            // The remaining six confirmed prefixes.
            "access_code",
            "access-key",
            "accessKey",
            "secret_key",
            "secretKey",
            "auth_code",
            "authKey",
            "private_key",
            "privateKey",
            "signing_key",
            "signingKey",
            "enc_key",
            "encKey",
            // No separator at all: these two did NOT redact before WR-01, because 'key'/'code' were
            // preceded by an alphanumeric and D-11's boundary rejected them. They are a small gain
            // in the fail-safe direction rather than a cost.
            "accesscode",
            "authcode",
        )

    // (PRIV-05) WR-01: names the code review measured as over-redacted that the narrowing does NOT
    // reach, asserted AS ACCEPTED so the limit of the decision is visible rather than assumed.
    //
    // Every one of these is driven by 'auth', 'session' or 'token' — words the WR-01 ruling left
    // alone, because it narrowed 'key' and 'code' only. token_type and tokenType are the painful
    // pair: token_type: "Bearer" is benign OAuth metadata and the plan's own text expects it to
    // survive, but it cannot without either a suffix denylist (which D-12 rejects on principle, as
    // every entry is a place a real credential could be allowlisted) or a narrowing of 'token'
    // itself, which would put access-token and XSRF-TOKEN at risk. Both are maintainer decisions.
    // Pinned here so the gap is asserted, dated and discoverable instead of being rediscovered.
    private val wr01AcceptedOverRedactions =
        listOf(
            "token_type",
            "tokenType",
            "session_count",
            "auth_type",
            "auth_url",
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

    // (PRIV-05) WR-01: the narrowing decision, pinned in the direction it went.
    //
    // Plan 21-04's SENSITIVE_KEY_EXPR let the two broadest vocabulary words, 'key' and 'code',
    // take part in D-11's free containment rule. The Phase 21 code review drove the live
    // formBodyParamRegex and jsonSecretKeyRegex over a wider corpus and measured the resulting
    // class: 32 names, including status_code, error_code, statusCode and errorCode. That is not a
    // cosmetic over-redaction — {"statusCode": 401, "errorCode": "AUTH_FAILED"} reached the
    // analysis prompt as two [REDACTED] tokens while the model was being asked to find an
    // authentication flaw, which is a functional regression in a passive vulnerability scanner.
    //
    // The maintainer ruled on 2026-08-12 to narrow (option-b): 'key' and 'code' now require either
    // whole-key equality or one of the confirmed CREDENTIAL_PREFIXES. This test is the assertion of
    // that ruling. It is deliberately BOTH directions in one place, because the narrowing is a
    // LOOSENING of a security pattern under CONCERNS.md's tightening protocol, and a corpus that
    // only asserted the names that now survive would let the credential half rot silently.
    @Test
    fun wr01BroadWordKeysSurviveUnlessCredentialBearing() {
        assertEquals(32, wr01BroadWordBenignKeys.size, "WR-01 must-not-redact corpus must stay at 32 keys")
        assertEquals(24, wr01CredentialBroadWordKeys.size, "WR-01 must-redact corpus must stay at 24 keys")

        for (key in wr01BroadWordBenignKeys) {
            for ((context, output) in sc3Contexts(key, PrivacyMode.STRICT)) {
                assertTrue(
                    output.contains(SC3_SENTINEL),
                    "STRICT / $context: WR-01 narrowed the broad words, so '$key' must NOT be redacted",
                )
            }
        }

        // The direction that ships a leak if it regresses, so it runs in BALANCED as well as
        // STRICT, and asserts the benign companion survives in the same pass — a mutation that
        // "passes" this half by redacting everything fails it instead.
        for (mode in listOf(PrivacyMode.STRICT, PrivacyMode.BALANCED)) {
            for (key in wr01CredentialBroadWordKeys) {
                for ((context, output) in sc3Contexts(key, mode)) {
                    assertFalse(
                        output.contains(SC3_SENTINEL),
                        "$mode / $context: credential-bearing broad-word key '$key' must STILL be redacted",
                    )
                    assertTrue(
                        output.contains(sc3Survivors.getValue(context)),
                        "$mode / $context: benign companion must survive alongside '$key'",
                    )
                }
            }
        }
    }

    // (PRIV-05) WR-01 / T-21-38: the shipped SENSITIVE_KEY_EXPR is hand-factored by first letter for
    // a measured reason (see SENSITIVE_KEY_WORDS in Redaction.kt: the unfactored WR-01 vocabulary
    // cost +16% on the dominant JSON rule and exhausted the body stage's 2 s budget on the 4 MB
    // newline-free fixture). Hand-factoring a security-critical alternation is exactly the kind of
    // change that looks right and is subtly wrong, so it is CHECKED here rather than trusted.
    //
    // Redaction.NAIVE_KEY_EXPR_FOR_TEST is the same expression built straight from the readable
    // SENSITIVE_WORDS / CREDENTIAL_PREFIXES / BROAD_WORDS constants. This test drives both over
    // every key name any corpus in this file mentions and asserts they agree on all of them. A word
    // added to the readable spec but forgotten in the factored form fails here — which is the whole
    // reason the seam exists.
    @Test
    fun factoredKeyVocabularyMatchesItsReadableSpecification() {
        val naiveJsonRule =
            Regex(
                "(?i)(\"${Redaction.NAIVE_KEY_EXPR_FOR_TEST}\"\\s*:\\s*)" +
                    "(\"[^\"]*\"|true|false|null|-?\\d+(?:\\.\\d+)?)",
            )
        val corpus =
            sc3MustRedactKeys + sc3BenignKeys + wr01BroadWordBenignKeys +
                wr01CredentialBroadWordKeys + wr01AcceptedOverRedactions +
                listOf(
                    "authToken",
                    "accessToken",
                    "userSessionId",
                    "tokenCount",
                    "keyboardLayout",
                    "monkeyBars",
                    "token_bucket_size",
                    "session_timeout_seconds",
                    "auth_provider",
                    "secret_santa",
                    "password_hint_enabled",
                    "abtest_bucket",
                    "stripe_key",
                    "encrypted_key",
                    "myapi_key",
                )

        // Guard against the corpus silently emptying — an empty list would make every assertion
        // below vacuous, which is the failure mode that has bitten this phase five times.
        assertTrue(corpus.size > 100, "The equivalence corpus must be substantial; was ${corpus.size}")

        for (key in corpus.distinct()) {
            val doc = """{"$key":"$SC3_SENTINEL"}"""
            val naiveRedacted = !naiveJsonRule.replace(doc, "$1\"[REDACTED]\"").contains(SC3_SENTINEL)
            val shippedRedacted = !redactWith(doc, PrivacyMode.STRICT).contains(SC3_SENTINEL)
            assertEquals(
                naiveRedacted,
                shippedRedacted,
                "The factored vocabulary must classify '$key' exactly as the readable specification does",
            )
        }
    }

    // (PRIV-05) WR-01: the LIMIT of the narrowing, asserted rather than assumed.
    //
    // These five names were in the code review's measured over-redaction list and are still
    // redacted, because they are driven by 'auth', 'session' and 'token' — words the ruling left
    // alone. Asserting them as accepted is the same discipline the D-13 block below applies:
    // silently accepting a name is not the same as deciding it. See wr01AcceptedOverRedactions for
    // why token_type in particular cannot be freed without a separate maintainer decision.
    @Test
    fun wr01NonBroadWordOverRedactionsRemainAccepted() {
        assertEquals(5, wr01AcceptedOverRedactions.size, "WR-01 accepted-residual corpus must stay at 5 keys")

        for (key in wr01AcceptedOverRedactions) {
            for ((context, output) in sc3Contexts(key, PrivacyMode.STRICT)) {
                assertFalse(
                    output.contains(SC3_SENTINEL),
                    "STRICT / $context: '$key' is an ACCEPTED over-redaction WR-01 did not reach and must stay redacted",
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
    // ACCEPTED OVER-REDACTION: tokenCount is asserted as REDACTED on purpose. It is the price of
    // D-13 — recorded here as deliberate behaviour rather than discovered in the field. It
    // over-redacts (the fail-safe direction) and is structurally identical to the already-accepted
    // token_bucket_size case. codeName and keyName were asserted alongside it until WR-01 narrowed
    // the two broad words out of the containment rule; they now survive and are pinned in
    // wr01BroadWordBenignKeys, so this block names one case rather than three.
    // REVERT POINT: deleting the (?-i:...) alternative from WORD_BEFORE and WORD_AFTER in
    // Redaction.kt drops D-13 entirely — it loses nothing SC3 requires and removes tokenCount here
    // and tokenType in wr01AcceptedOverRedactions.
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

        for (key in listOf("tokenCount")) {
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

    // (PRIV-05) CR-01 / T-21-28: a BLANK cookie entry must not collapse or truncate the span.
    //
    // This is the live PRIV-05 leak the phase's own code review REPRODUCED, not a hypothetical.
    // COOKIE_SECTION_HEADER carries no trailing newline while the emitter uses appendLine, so
    // bodyStart lands on the header line's OWN newline. The pre-fix bound was
    // out.indexOf("\n\n", bodyStart), which therefore matched AT bodyStart whenever the first
    // emitted cookie entry was blank: the span became the empty string and EVERY cookie value in
    // the section reached the AI backend verbatim. A blank entry further down the list truncated
    // the span at that point instead, leaking every cookie below it.
    //
    // The emitter really does produce blank entries. PassiveAiScannerAnalysis splits the Cookie:
    // header with .split(";").map { c -> c.trim() } and applies no blank filter, so
    // "Cookie: ; JSESSIONID=..." — or any "a=b;;c=d" — yields one.
    //
    // FIXTURE REACHABILITY — what makes this reachable ONLY by redactCookieSections.
    // abtest_bucket=OPAQUE_VALUE_XYZ is the decisive line. The name's tokens are "abtest" and
    // "bucket", neither of which is a member of SENSITIVE_WORDS or KNOWN_SESSION_KEYS, so
    // SENSITIVE_KEY_EXPR and its three consumers cannot reach it. Its value contains no '=', is not
    // preceded by '?' or '&', does not start with "eyJ", is neither Bearer- nor Basic-prefixed,
    // sits inside no JSON key/value pair and carries no " (COOKIE)" suffix — so not one other rule
    // in Redaction.apply can touch it either. JSESSIONID and PHPSESSID are deliberately NOT
    // decisive: SENSITIVE_KEY_EXPR rescues both from the leading-field position even with the
    // section rule fully unwired, which is precisely why the pre-fix suite was green on this defect.
    // Do not swap abtest_bucket for a name the key expression already covers.
    //
    // The abtest_bucket line sits AFTER the mid-list blank on purpose. That is the half the code
    // review's own proposed one-line patch leaves leaking, so its position is what proves the span
    // bound was genuinely repaired rather than special-cased on the first line.
    @Test
    fun cookieSectionBlankEntriesDoNotCollapseSpan() {
        val cookieValues =
            mapOf(
                "JSESSIONID" to "8F3A9C2B7E1D4A6F0B5C8E2D",
                "PHPSESSID" to "abc123def456",
                "abtest_bucket" to "OPAQUE_VALUE_XYZ",
            )

        // First entry blank, fourth entry blank. trimIndent() maps interior blank lines to empty
        // strings rather than dropping them, so this shape is expressible directly.
        val blob =
            """
            === COOKIES ===

            JSESSIONID=8F3A9C2B7E1D4A6F0B5C8E2D
            PHPSESSID=abc123def456

            abtest_bucket=OPAQUE_VALUE_XYZ

            === PARAMETERS ===
            q=red running shoes (URL)
            """.trimIndent()

        // The fixture is asserted BEFORE the behaviour, so a future reformat that dropped either
        // blank line could not silently defuse the test into a duplicate of the sibling above.
        assertTrue(
            blob.contains(Redaction.COOKIE_SECTION_HEADER + "\n\n"),
            "The fixture must put a BLANK first entry directly after the header, producing the \"\\n\\n\" at bodyStart",
        )
        assertTrue(
            blob.contains("PHPSESSID=abc123def456\n\nabtest_bucket="),
            "The fixture must put a BLANK entry MID-LIST, immediately before the decisive abtest_bucket line",
        )

        for (mode in listOf(PrivacyMode.STRICT, PrivacyMode.BALANCED)) {
            val output = redactWith(blob, mode)
            for ((name, value) in cookieValues) {
                assertFalse(
                    output.contains(value),
                    "$mode: a blank cookie entry must not let the value of '$name' reach the backend",
                )
                assertTrue(
                    output.lines().contains("$name=[REDACTED]"),
                    "$mode: cookie '$name' must keep its name and lose only its value across the blank entries",
                )
            }
            assertTrue(
                output.lines().contains("q=red running shoes (URL)"),
                "$mode: skipping blank lines must not extend the span past the next section header",
            )
        }
    }

    // (PRIV-05) CR-01 / T-21-32: the DOCUMENTED RESIDUAL — a cookie element shaped like a section
    // header still terminates the span, so the cookie after it survives.
    //
    // This test asserts the residual AS IT STANDS. It is GREEN before this plan and GREEN after it,
    // BY DESIGN, and that is the whole point: the class is real, it is not closed here, and pinning
    // it makes the dependency visible instead of leaving it implicit in prose.
    //
    // Why no redactor-side rule can close it: this is IN-BAND SIGNALLING. The span terminator is
    // derived from content sitting INSIDE the region the span is supposed to protect, so the
    // redactor cannot tell a planted "=== FOO ===" cookie value from a genuine next prompt section.
    // Refusing to terminate on "=== " instead would hand an attacker the opposite primitive — one
    // planted line would swallow the remainder of the prompt.
    //
    // The class is therefore closed at the EMITTER and only at the emitter, by
    // Redaction.sanitizeCookieSectionEntries — exported from redact/ by this plan and wired into
    // PassiveAiScannerPrompts.buildScanMetadataText by plan 21-10, whose end-to-end guard is
    // PassiveAiScannerPromptRedactionTest.emittedSectionShapedCookieCannotTerminateSpan. If that
    // sanitizer is ever removed this test keeps passing while the end-to-end guard fails, which is
    // the correct division of labour between the two.
    //
    // FIXTURE REACHABILITY: abtest_bucket=OPAQUE_VALUE_XYZ again, for the reason given in full on
    // cookieSectionBlankEntriesDoNotCollapseSpan — nothing but the section rule can redact it, so
    // its SURVIVAL here is genuine evidence of the residual rather than an artefact of some other
    // rule declining to fire.
    @Test
    fun cookieSectionHeaderShapedEntryTerminatesSpan_documentedResidual() {
        val blob =
            """
            === COOKIES ===
            JSESSIONID=REALSECRET
            === FOO ===
            abtest_bucket=OPAQUE_VALUE_XYZ
            """.trimIndent()

        val output = redactWith(blob, PrivacyMode.STRICT)

        assertFalse(
            output.contains("REALSECRET"),
            "STRICT: the cookie ABOVE the planted section header is inside the span and must be redacted",
        )
        assertTrue(
            output.contains("OPAQUE_VALUE_XYZ"),
            "STRICT: RESIDUAL, asserted deliberately — a section-shaped cookie element ends the span, " +
                "and only Redaction.sanitizeCookieSectionEntries at the emitter (plan 21-10) can close it",
        )
    }

    // (PRIV-05) CR-03 / T-21-29: redactCookieSections must be O(n) in the input length, not O(k*n).
    //
    // Pre-fix the loop rebuilt the ENTIRE string once per occurrence of the header
    // (out.substring(0, bodyStart) + replaced + out.substring(end)), which is an O(n) copy per
    // occurrence. The occurrence count k is attacker-controlled: buildScanMetadataText emits
    // response headers and body verbatim, and McpToolContext.redactIfNeeded runs the same rule over
    // raw tool output. The reviewer measured a clean 4x per doubling on Apple Silicon / JDK 21 —
    // 65.3 ms at 64 KB, 187.7 ms at 128 KB, 682.4 ms at 256 KB, 2630.9 ms at 512 KB — extrapolating
    // to ~42 s at the MCP default maxBodyBytes of 2 MiB, on an uninterruptible worker thread with
    // no budget and no marker. This fixture is that 512 KB row exactly.
    //
    // THRESHOLD JUSTIFICATION: 2 631 ms is the pre-fix cost on the FASTEST hardware this project
    // targets, so a 1 000 ms bound is unreachable pre-fix on any machine, while the post-fix single
    // pass is a few tens of milliseconds. That leaves more than an order of magnitude of headroom in
    // both directions, so the test is neither flaky on slow hardware nor blind to a regression. Same
    // timing-assertion idiom as oversizeBodySecretDoesNotSurvive and oversizeBodyFailsClosed below.
    //
    // The marker assertion is load-bearing, not decoration: without it a future change could make
    // this test pass by tripping COOKIE_SECTION_BUDGET_MS immediately and dropping the tail, which
    // is fast but is fail-closed truncation rather than the linear pass under test.
    @Test
    fun redactCookieSectionsIsLinearInSectionCount() {
        // 32 768 x 16 chars ("=== COOKIES ===" plus a newline) = 524 288 chars, the 512 KB row.
        val input = (Redaction.COOKIE_SECTION_HEADER + "\n").repeat(32_768)
        assertEquals(
            524_288,
            input.length,
            "The fixture must stay at the reviewer's measured 512 KB / 32 768-occurrence point",
        )

        val policy = RedactionPolicy.fromMode(PrivacyMode.STRICT)
        val start = System.currentTimeMillis()
        val output = Redaction.apply(input, policy, stableHostSalt = "salt")
        val elapsed = System.currentTimeMillis() - start

        assertFalse(
            output.contains("REDACTION INCOMPLETE"),
            "The single pass must COMPLETE inside its budget, not reach 1 000 ms by failing closed early",
        )
        assertTrue(
            elapsed < 1_000,
            "redactCookieSections must be linear in input length; took ${elapsed}ms " +
                "(pre-fix this same fixture measured 2 631 ms and rose 4x per doubling)",
        )
    }

    // (PRIV-06) CR-03 / D-02 / T-21-29: when the cookie rule's wall-clock budget expires it must
    // FAIL CLOSED — drop the remainder behind a marker rather than pass it through unscanned.
    //
    // Pre-fix this rule was the only one the phase added that bypassed both SafeRegex and
    // MAX_REDACTION_BUDGET_MS entirely: no deadline, no marker, and no seam to assert either.
    //
    // WHY THE BUDGET IS A PARAMETER rather than a constant: reaching a 250 ms deadline through the
    // real entry point would need a tens-of-megabytes fixture, making the assertion slow and
    // machine-speed dependent — i.e. exactly the flakiness that an injected bound removes. The
    // project's established answer is the one maybeLogTruncation(nowMs, droppedChars) already uses:
    // pass the bound in, never read the clock inside the assertion. testRedactCookieSections is the
    // matching internal seam, in the style of resetTruncationWindowForTest and testHkdfExtract.
    //
    // FIXTURE REACHABILITY: abtest_bucket=OPAQUE_VALUE_XYZ once more, and here the seam makes it
    // airtight — the call bypasses Redaction.apply altogether, so no other rule even runs, and the
    // absence of the value can only be the deadline branch dropping the tail.
    @Test
    fun cookieSectionDeadlineFailsClosed() {
        val blob = Redaction.COOKIE_SECTION_HEADER + "\nabtest_bucket=OPAQUE_VALUE_XYZ"

        val output = Redaction.testRedactCookieSections(blob, budgetMs = 0L)

        assertTrue(
            output.contains("REDACTION INCOMPLETE"),
            "An expired cookie-section budget must leave the windowDroppedMarker, not silence",
        )
        assertFalse(
            output.contains("OPAQUE_VALUE_XYZ"),
            "An expired budget must DROP the unscanned remainder, never pass it through unredacted",
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

    // (PRIV-06) D-03: the truncation notice is rate-limited to one line per 10 s window, and the
    // line that ends a window reports how many notices were suppressed inside it.
    //
    // The clock is injected through maybeLogTruncation's nowMs parameter — the
    // PassiveAiScannerAnalysis.maybeLogBackoff convention — so the window is asserted exactly and
    // nothing sleeps. resetTruncationWindowForTest() is called first because Redaction is a
    // singleton object and a previous test in the same JVM may have opened the window already.
    @Test
    fun truncationSignalIsRateLimited() {
        Redaction.resetTruncationWindowForTest()
        // The sink fires on the caller's thread (a scanner or MCP tool thread in production) and is
        // read from the JUnit thread, so the capture list must be concurrent.
        val lines = CopyOnWriteArrayList<String>()
        Redaction.truncationLogger = { lines += it }

        Redaction.maybeLogTruncation(T0, 1_000L)
        assertEquals(1, lines.size, "The first truncation in a window must emit a notice")

        Redaction.maybeLogTruncation(T_INSIDE_WINDOW, 2_000L)
        assertEquals(1, lines.size, "A second truncation inside the same window must be suppressed")

        Redaction.maybeLogTruncation(T_AFTER_WINDOW, 3_000L)
        assertEquals(2, lines.size, "A truncation past the window must emit a second notice")
        assertTrue(
            lines[1].contains("Further notices suppressed since the previous line: 1"),
            "The notice that closes a window must report the suppressed count; got: ${lines[1]}",
        )
        assertFalse(
            lines[0].contains("suppressed"),
            "The first notice of a burst must not claim a suppressed count",
        )
    }

    // (PRIV-06) SC4 / T-21-02: a body above the old size cap must not smuggle a secret past the
    // body stage.
    //
    // This test is a DELIBERATE REWRITE, not an extension. It previously asserted the fail-open as
    // correct behaviour: its comment recorded that an over-cap secret was allowed to remain, and
    // its only substantive assertion was that the call returned quickly — which stayed true
    // precisely BECAUSE every body rule was skipped. That is the PRIV-06 defect asserted as a
    // contract. 21-VALIDATION.md records this rewrite as one of SC6's two named exceptions, so it
    // is not a regression; plan 21-07 proves it goes RED against the pre-fix body stage. The old
    // name would now misdescribe the contract, and 21-VALIDATION.md's automated selector
    // *RedactionTest.oversizeBody* matches both the old and the new name.
    //
    // Two properties make this a real gate rather than a smoke test:
    //   - the filler is MULTI-LINE, so line-boundary windowing genuinely produces more than one
    //     window and the secret sits past the old cut-off, inside the second one;
    //   - the secret is reachable by a body-stage rule ALONE. formBodyParamRegex catches it through
    //     its (^|[?&]) leading-field anchor, while urlTokenParamRegex — which runs unbounded in the
    //     header stage and would otherwise mask the defect — requires a '?' or '&' before the key
    //     and cannot reach it. The value is not bearer- or basic-prefixed and does not start with
    //     "eyJ", so no other rule can save it either. If the body stage skips, the secret survives.
    @Test
    fun oversizeBodySecretDoesNotSurvive() {
        // 10 001 lines of 99 'x' plus a newline is 1 000 100 characters, just over the window width.
        val filler = ("x".repeat(99) + "\n").repeat(10_001)
        val oversizeBody = filler + "api_key=SC4-SECRET-VALUE-7B3E\n"
        assertTrue(
            oversizeBody.length > Defaults.MAX_REDACTION_BODY_CHARS,
            "The fixture must exceed the window width or this test proves nothing",
        )

        val policy = RedactionPolicy.fromMode(PrivacyMode.STRICT)
        val start = System.currentTimeMillis()
        val output = Redaction.apply(oversizeBody, policy, stableHostSalt = "salt")
        val elapsed = System.currentTimeMillis() - start

        assertFalse(
            output.contains("SC4-SECRET-VALUE-7B3E"),
            "STRICT: a secret past the old size cap must not survive the body stage",
        )
        assertTrue(
            output.contains("api_key=[REDACTED]"),
            "STRICT: the over-cap field must be redacted in place, keeping its key",
        )
        assertTrue(elapsed < 5_000, "The windowed body stage must stay bounded; took ${elapsed}ms")
    }

    // (PRIV-06) SC4 / D-02 / D-14 / T-21-03: a pathological pattern on an oversized input must
    // produce a MARKER, never passthrough.
    //
    // This is the fail-CLOSED half of SC4 and the explicit guard against treating
    // SafeRegex.replaceAllSafe's return value as success: on timeout that facade returns the input
    // unchanged, byte-identical to "the pattern matched nothing", so a body stage built on it would
    // silently emit unscanned bytes while looking correct. Only the timedOut flag from
    // replaceAllSafeReporting can tell the two apart.
    //
    // (a+)+$ is the classic catastrophic-backtracking pattern and 2 000 'a' characters followed by
    // '!' is the input shape SafeRegexTest already proves trips the 50 ms deadline on JDK 21. It is
    // pushed in through setCustomPatterns rather than through the save path on purpose: isPatternSafe
    // rejects it at save time, and what is under test here is what the ENGINE does if such a pattern
    // ever reaches it. The @AfterEach resetCustomPatterns prevents any bleed.
    @Test
    fun oversizeBodyFailsClosed() {
        Redaction.setCustomPatterns(listOf("(a+)+\$"))

        val oversizeBody = ("a".repeat(2_000) + "!\n").repeat(600)
        assertTrue(
            oversizeBody.length > Defaults.MAX_REDACTION_BODY_CHARS,
            "The fixture must exceed the window width or no windowing happens",
        )

        val policy = RedactionPolicy.fromMode(PrivacyMode.STRICT)
        val start = System.currentTimeMillis()
        val output = Redaction.apply(oversizeBody, policy, stableHostSalt = "salt")
        val elapsed = System.currentTimeMillis() - start

        assertTrue(
            output.contains("REDACTION INCOMPLETE") || output.contains("REDACTION BUDGET EXCEEDED"),
            "A window that could not be fully scanned must be dropped behind a marker, not passed through",
        )
        // (PRIV-06) WR-05 — WHY THE PREVIOUS ASSERTION WAS REPLACED, recorded so the strengthening
        // reads as deliberate rather than as churn. This test used to assert only that the WHOLE
        // body did not survive verbatim beside the marker. That form is satisfied by *any*
        // single-character modification to the input: inserting one marker anywhere already makes
        // a whole-body containment check false, so it proved nothing at all about the property under
        // test. A mutation that emitted three windows unscanned and marked only the fourth would
        // have kept this test green while the fail-OPEN this phase exists to kill was back in place.
        //
        // The two replacements are EXACT rather than approximate, and that is a property of this
        // fixture specifically: it consists of nothing but 2 000-character 'a' runs and '!', every
        // window in it must be dropped, and a 2 000-'a' run can therefore appear in the output only
        // if some window was emitted UNSCANNED. The length bound is the second half of the same
        // statement — a body that collapsed to markers is orders of magnitude shorter than half its
        // input, while a body that passed through is longer than it.
        assertFalse(
            output.contains("a".repeat(2_000)),
            "No unscanned window may reach the output; only markers may remain",
        )
        assertTrue(
            output.length < oversizeBody.length / 2,
            "A wholly-unscannable body must collapse to markers, not pass through",
        )
        // Generous so a slow machine cannot make this flaky, tight enough to fail if it hangs.
        assertTrue(elapsed < 30_000, "The total budget must bound a pathological pattern; took ${elapsed}ms")
    }

    // (PRIV-06) SC4 / D-02 / D-14 / T-21-03: the fail-closed guarantee holds BELOW the window
    // width too, not only above it.
    //
    // The sibling above covers oversized input. This one covers the single-pass path, which plan
    // 21-06 shipped assigning SafeRegex.replaceAllSafe's return value: that facade returns its
    // input unchanged on timeout, byte-identical to "the pattern matched nothing", so a rule that
    // overran the 50 ms deadline was silently skipped and its unredacted content passed straight
    // through. That is fail-OPEN, one size class below the defect the phase exists to remove, and
    // it is what this test pins shut. The fix discards the partial result and re-scans the ORIGINAL
    // input through the windowed path, which already drops unscannable content behind a marker.
    //
    // FIXTURE STRENGTH (the 21-05 lesson: a test another rule also satisfies is vacuous). Two
    // properties make this reachable ONLY by the path under test:
    //   - the body is 800 800 characters, strictly BELOW MAX_REDACTION_BODY_CHARS, so the windowed
    //     path is not entered directly and the single-pass loop is what must fail closed. The
    //     assertion below pins that, so a future change to the constant cannot silently turn this
    //     into a duplicate of oversizeBodyFailsClosed;
    //   - the content is nothing but 'a' runs and '!' — no '=', no sensitive key name, no cookie
    //     section, no bearer/basic prefix, no "eyJ" and no host header. Not one built-in rule can
    //     match it, in either stage, so the marker asserted below can only have been produced by
    //     the fail-closed fallthrough. Verified by mutation, not by inspection.
    @Test
    fun subWindowBodyFailsClosed() {
        Redaction.setCustomPatterns(listOf("(a+)+\$"))

        // 400 lines of 2 000 'a' plus "!\n" is 800 800 characters — under the 1 MB window width.
        val body = ("a".repeat(2_000) + "!\n").repeat(400)
        assertTrue(
            body.length <= Defaults.MAX_REDACTION_BODY_CHARS,
            "The fixture must stay BELOW the window width or this test duplicates oversizeBodyFailsClosed",
        )

        val policy = RedactionPolicy.fromMode(PrivacyMode.STRICT)
        val start = System.currentTimeMillis()
        val output = Redaction.apply(body, policy, stableHostSalt = "salt")
        val elapsed = System.currentTimeMillis() - start

        assertTrue(
            output.contains("REDACTION INCOMPLETE") || output.contains("REDACTION BUDGET EXCEEDED"),
            "A sub-window body whose rule timed out must be dropped behind a marker, never passed through",
        )
        // (PRIV-06) WR-05 — the same strengthening as the sibling above, for the same reason. The
        // previous assertion checked only that the WHOLE body did not survive verbatim, which any
        // single-character modification satisfies, so a mutation emitting most of the input
        // unscanned and marking only the tail would have left this test green. This fixture is also
        // nothing but 2 000-character 'a' runs and '!', so the run-absence check below is exact:
        // there is no window in it that can legitimately be emitted.
        assertFalse(
            output.contains("a".repeat(2_000)),
            "No unscanned window may reach the output; only markers may remain",
        )
        assertTrue(
            output.length < body.length / 2,
            "A wholly-unscannable body must collapse to markers, not pass through",
        )
        // Bounds the documented composition: one single-pass sweep plus one full windowed budget.
        assertTrue(elapsed < 30_000, "The single-pass fallthrough must stay bounded; took ${elapsed}ms")
    }

    // (PRIV-06) CR-04 / T-21-33: splitPoint must be able to cut a window that has no newline in it
    // at all. THIS is the defect, reduced to a pure function.
    //
    // Returning 0 here is not a neutral "cannot split" answer: dropOrRetry's guard is
    // `if (cut <= 0 || cut >= window.length)`, so 0 routes straight into the total drop and the
    // whole window is replaced by a marker. A newline-free body is one window at any size, so that
    // single return value is what destroys a minified-JSON payload in its entirety.
    //
    // WHY A SEAM RATHER THAN ONLY THE END-TO-END TEST. Reaching this branch through
    // Redaction.apply requires a rule to genuinely exceed its 50 ms deadline, which needs a
    // multi-megabyte fixture and depends on machine speed, JIT warm-up and JaCoCo instrumentation.
    // The defect itself is a pure function of one string. Asserting it directly makes the core of
    // CR-04 deterministic and hardware-independent, and leaves the end-to-end sibling below to prove
    // the wiring rather than the arithmetic.
    @Test
    fun splitPointCutsNewlineFreeWindowsInsteadOfRefusing() {
        val window = "x".repeat(1_000)

        val cut = Redaction.testSplitPoint(window)

        assertTrue(
            cut > 0,
            "A newline-free window must be splittable; a 0 here is what makes dropOrRetry discard the whole window",
        )
        assertTrue(
            cut < window.length,
            "The cut must leave a non-empty second half, or dropOrRetry drops on its `cut >= window.length` limb",
        )
    }

    // (PRIV-06) CR-04: the line-boundary rule is UNCHANGED wherever a line boundary exists.
    //
    // GREEN BEFORE AND AFTER, BY DESIGN — this is a regression guard on the (?m)^ protection, not a
    // test of new behaviour, and Pitfall 1 says such a test must say so out loud. Its purpose is to
    // prove the character cut was added as a FALLBACK rather than as a replacement: the trap
    // 21-RESEARCH.md "Decision 3" proved in both directions — a mid-line cut can create a match at
    // an artificial line start and can truncate a match spanning the cut — is still avoided
    // everywhere a '\n' is available to cut on. If this test ever goes red, the fallback has
    // escaped its branch and the fix has traded the protection away rather than extending it.
    @Test
    fun splitPointStillCutsAtALineBoundaryWhenOneExists() {
        val window = SAFE_CUT_LINE_FIXTURE.repeat(SAFE_CUT_LINE_REPEATS)
        // ANTI-VACUITY GUARD — see SAFE_CUT_LINE_FIXTURE. Unless a NON-newline terminator sits
        // between the midpoint and the next line break, this test cannot tell a line-boundary cut
        // from a character cut that happened to land on a newline, and it would stay green against a
        // splitPoint whose line branches had been deleted outright.
        val firstTerminatorAtOrAfterMid =
            (window.length / 2 until window.length).first { isSafeCutTerminatorForTest(window[it]) }
        assertFalse(
            window[firstTerminatorAtOrAfterMid] == '\n',
            "The fixture must carry a non-newline terminator between its midpoint and the next line break",
        )

        val cut = Redaction.testSplitPoint(window)

        assertTrue(cut > 0, "A window full of line boundaries must be splittable")
        assertEquals(
            '\n',
            window[cut - 1],
            "The cut must land immediately after a newline, so both halves stay line-aligned and (?m)^ keeps its meaning",
        )
    }

    // (PRIV-06) CR-04 / T-21-34: on the dominant oversized shape, the character cut lands where no
    // built-in body rule's match can span it.
    //
    // The straddle residual is real and is recorded in ADR-14 rather than solved — D-01 AMENDED's
    // overlap clause stays dropped, because measured single matches of the built-in rules reach
    // 200 006 characters and no finite overlap constant is therefore sound. What CAN be done, and is
    // what this test pins, is to prefer a cut position that the built-in value classes cannot cross:
    // [^&\s"'<>]+ cannot span '&' or whitespace, and a JSON value is either '"'-delimited or a
    // scalar immediately followed by ',', '}' or ']' in minified JSON. Cutting just after one of
    // those therefore lands OUTSIDE any built-in match for exactly the payload shape CR-04 is about.
    @Test
    fun splitPointPrefersASafeCutBoundaryInMinifiedJson() {
        val window = SAFE_CUT_JSON_FRAGMENT.repeat(SAFE_CUT_JSON_REPEATS)
        assertFalse(
            window.contains('\n'),
            "The fixture must have no line boundary, or splitPoint never reaches the safe-cut branch",
        )
        // ANTI-VACUITY GUARD — see SAFE_CUT_JSON_FRAGMENT. If the exact midpoint were itself already
        // a safe cut, this test would pass against a splitPoint that never searched at all, which is
        // precisely what the first version of this fixture did.
        assertFalse(
            isSafeCutTerminatorForTest(window[window.length / 2 - 1]),
            "The fixture must be MISALIGNED with the midpoint, or the assertion below proves nothing",
        )

        val cut = Redaction.testSplitPoint(window)

        assertTrue(cut > 0, "A newline-free minified-JSON window must be splittable")
        assertTrue(cut < window.length, "The cut must leave a non-empty second half")
        val cutAfter = window[cut - 1]
        assertTrue(
            cutAfter in SAFE_CUT_TERMINATORS || cutAfter.isWhitespace(),
            "The cut must land just after a value-terminating character so it cannot fall inside a " +
                "built-in match; it landed after '$cutAfter'",
        )
    }

    // (PRIV-06) CR-04 / T-21-33: a newline-free body above the window width must be SCANNED in
    // pieces, not replaced in its entirety by a drop marker.
    //
    // THE DEFECT. windowEnd deliberately makes an over-width line its own window, so a body with no
    // '\n' is one window whatever its size. splitPoint then returned 0 for any window with no
    // interior newline, and dropOrRetry's `if (cut <= 0 …)` turned that into a total drop — so the
    // WINDOW_RETRY_MAX_DEPTH ladder, justified in source as existing so a 2-3x slower machine would
    // not lose content that ships today, was structurally inapplicable to the single most common
    // oversized payload shape there is.
    //
    // WHY THAT SHAPE IS THE COMMON ONE, not a corner case: McpToolContext.redactIfNeeded receives
    // serialized tool output capped at maxBodyBytes (default 2 MiB) and toolJson.encodeToString(...)
    // emits MINIFIED, newline-free JSON. A default-configuration 2 MiB tool response therefore
    // reached the model as "[REDACTION INCOMPLETE - 2097156 CHARS DROPPED AND NOT SENT]" and
    // nothing else. That is fail-CLOSED, so it is a capability regression rather than a leak — but
    // it is one this phase introduced, and an empty analysis for a default input is incorrect
    // behaviour, not a safe one.
    //
    // WHICH ASSERTION IS THE GATE. Secret-absence alone would be VACUOUS here, and in the most
    // deceptive way available: the pre-fix behaviour removes the secret too, by destroying the
    // entire body. A test asserting only that the secret is gone would have passed against the
    // defect it exists to catch. The length bound is what separates "scanned" from "dropped behind
    // a marker", and the surviving-key assertion is what proves the pair was redacted in place
    // rather than removed wholesale — the same three-legged shape as the CR-02 sweeps below.
    //
    // FIXTURE REACHABILITY (the 21-05 lesson: a test some OTHER rule also satisfies proves nothing).
    // SC4-NEWLINE-SECRET-9 is reachable by jsonSecretKeyRegex and by nothing else in either stage:
    //   - the fixture contains no '=' anywhere, so formBodyParamRegex cannot reach it, and
    //     urlTokenParamRegex — which runs UNBOUNDED in the header stage and would otherwise mask the
    //     defect entirely — additionally requires a leading '?' or '&';
    //   - the value is not Bearer- or Basic-prefixed and does not begin "eyJ", so bearerRegex,
    //     basicAuthRegex and jwtRegex cannot match it;
    //   - there is no "Cookie:"/"Set-Cookie:" header, no "=== COOKIES ===" section and no
    //     " (COOKIE)" type suffix, so neither cookie rule can reach it;
    //   - no custom pattern is registered, and @AfterEach resetCustomPatterns guarantees no bleed
    //     from oversizeBodyFailsClosed / subWindowBodyFailsClosed, which both install "(a+)+$".
    // Established by mutation, not by inspection.
    //
    // TIMING EXPOSURE, declared rather than discovered later. This assertion proves SUCCESSFUL
    // redaction on a multi-megabyte fixture, so it sits on the same wall-clock deadline that plan
    // 21-09 saw trip once under full-suite load: SafeRegex enforces its deadline in
    // DeadlineCharSequence.get(), which JaCoCo instruments once per character. The deterministic
    // half of CR-04 is therefore asserted separately and machine-independently by
    // splitPointCutsNewlineFreeWindowsInsteadOfRefusing. If this test ever goes red in CI while that
    // one stays green, the diagnosis is deadline pressure under instrumentation, not a CR-04
    // regression.
    @Test
    fun newlineFreeOversizeBodyIsScannedNotDestroyed() {
        val target = Defaults.MAX_REDACTION_BODY_CHARS * NEWLINE_FREE_WINDOW_MULTIPLIER
        val body =
            buildString {
                while (length < target / 2) append(NEWLINE_FREE_FRAGMENT)
                append(NEWLINE_FREE_SECRET_PAIR)
                while (length < target) append(NEWLINE_FREE_FRAGMENT)
            }
        // Both fixture properties are load-bearing and are asserted so a later edit cannot defuse
        // this test silently: below the window width there is no windowing at all, and one '\n'
        // anywhere would hand splitPoint the line boundary whose ABSENCE is the whole defect.
        assertTrue(
            body.length > Defaults.MAX_REDACTION_BODY_CHARS,
            "The fixture must exceed the window width or no windowing happens",
        )
        assertFalse(
            body.contains('\n'),
            "The fixture must contain NO newline, or splitPoint takes its line-boundary branch and CR-04 is not exercised",
        )

        val policy = RedactionPolicy.fromMode(PrivacyMode.STRICT)
        val start = System.currentTimeMillis()
        val output = Redaction.apply(body, policy, stableHostSalt = "salt")
        val elapsed = System.currentTimeMillis() - start

        assertFalse(
            output.contains("SC4-NEWLINE-SECRET-9"),
            "STRICT: a secret in a newline-free oversize body must not survive the body stage",
        )
        assertTrue(
            output.contains("\"api_key\":\"[REDACTED]\""),
            "STRICT: the pair must be redacted IN PLACE, keeping its key — not removed wholesale",
        )
        assertTrue(
            output.length > body.length / 2,
            "A newline-free body must be SCANNED, not collapsed behind a drop marker; " +
                "output was ${output.length} chars against a ${body.length}-char input",
        )
        // Same idiom and the same generosity as the two fail-closed siblings above.
        assertTrue(elapsed < 30_000, "The windowed body stage must stay bounded; took ${elapsed}ms")
    }

    // (PRIV-06) CR-02 / WR-06 / D-01 AMENDED: the windowing invariant D-01 always CLAIMED and never
    // asserted.
    //
    // D-01's central claim was that line-boundary cutting is equivalent to whole-document
    // processing. Nothing in the committed suite ever swept a fixture across a window cut, and that
    // is precisely the gap CR-02 fell through: windowEnd mitigated its own documented hazard by
    // pulling in exactly ONE following line and never re-checking, so a key/colon/value pair spread
    // over three lines was cut in half. The reviewer reproduced it at shift 7 —
    // "windowedLeak=true singlePassLeak=false", a secret the single-pass path redacts surviving the
    // windowed path. Response bodies are attacker-controlled and window boundaries are deterministic
    // given a known prefix length, so the alignment is craftable rather than accidental.
    //
    // THE ANTI-VACUITY ASSERTION IS LOAD-BEARING, not decoration. A DROPPED window also removes the
    // secret, so "the secret is absent" on its own would be satisfied by the fail-closed path and
    // this sweep would degrade into a second oversizeBodyFailsClosed — passing for entirely the
    // wrong reason while proving nothing about equivalence. Asserting that no drop marker was
    // emitted is what forces the pair to have been REDACTED. The surviving key is the third leg: it
    // proves the region was redacted in place rather than removed wholesale.
    //
    // FIXTURE STRENGTH (the 21-05 lesson: a test that some OTHER rule also satisfies is vacuous).
    // BOUNDARY-SECRET-7 is reachable by jsonSecretKeyRegex and by nothing else in either stage:
    //   - it contains no '=', so formBodyParamRegex cannot reach it, and urlTokenParamRegex — which
    //     runs unbounded in the header stage and would otherwise mask the defect — additionally
    //     requires a leading '?' or '&';
    //   - the value is not Bearer- or Basic-prefixed and does not begin "eyJ", so bearerRegex,
    //     basicAuthRegex and jwtRegex cannot match it;
    //   - there is no "Cookie:" or "Set-Cookie:" header, no "=== COOKIES ===" section and no
    //     " (COOKIE)" type suffix, so neither cookie rule can reach it;
    //   - no custom pattern is registered, and @AfterEach resetCustomPatterns guarantees no bleed
    //     from a sibling test.
    // If the JSON rule does not match across the cut, the value survives verbatim. Established by
    // mutation, not by inspection.
    @Test
    fun windowedScanRedactsJsonPairAcrossEveryBoundaryAlignment() {
        // Key, colon and value on three separate lines — the exact shape windowEnd's own comment
        // cited as the hazard and then failed to handle.
        val pair = "  \"token\"\n  :\n  \"BOUNDARY-SECRET-7\"\n"

        for (shift in 0 until BOUNDARY_SWEEP_SHIFTS) {
            val body = boundarySweepBody(shift, pair)
            assertTrue(
                body.length > Defaults.MAX_REDACTION_BODY_CHARS,
                "shift=$shift: the fixture must exceed the window width, or this sweeps the single-pass path",
            )

            val policy = RedactionPolicy.fromMode(PrivacyMode.STRICT)
            val output = Redaction.apply(body, policy, stableHostSalt = "salt")

            assertFalse(
                output.contains("BOUNDARY-SECRET-7"),
                "shift=$shift: a JSON pair straddling a window boundary must still be redacted",
            )
            assertFalse(
                output.contains("REDACTION INCOMPLETE") || output.contains("REDACTION BUDGET EXCEEDED"),
                "shift=$shift: the sweep must prove the pair was REDACTED, not that the window was DROPPED",
            )
            assertTrue(
                output.contains("\"token\""),
                "shift=$shift: the key must survive, so the pair was redacted in place, not removed wholesale",
            )
        }
    }

    // (PRIV-06) CR-02, second half: a whitespace-only line between the colon and the value must not
    // let the pair slip through.
    //
    // jsonSecretKeyRegex's \s* spans newlines, so "key" / ':' / blank / "value" is a single match on
    // the single-pass path — confirmed against the real pattern before this test was written, so it
    // asserts a property the engine genuinely has rather than one contorted into existence for it.
    // A loop that stops extending the window the moment it meets a blank line therefore still cuts
    // this shape in half, which is why isJsonPairBoundaryContinuation treats a blank line as a
    // continuation of risk while isJsonPairBoundaryRisk does not START one on it.
    //
    // Same three assertions and the same fixture-strength argument as the sibling above:
    // BLANK-GAP-SECRET-3 carries no '=', no bearer/basic prefix, no "eyJ" and no cookie context, so
    // only the JSON rule can reach it.
    @Test
    fun jsonPairWithBlankLineBetweenKeyAndValueIsRedacted() {
        val pair = "  \"token\"\n  :\n\n  \"BLANK-GAP-SECRET-3\"\n"

        for (shift in 0 until BOUNDARY_SWEEP_SHIFTS) {
            val body = boundarySweepBody(shift, pair)
            assertTrue(
                body.length > Defaults.MAX_REDACTION_BODY_CHARS,
                "shift=$shift: the fixture must exceed the window width, or this sweeps the single-pass path",
            )

            val policy = RedactionPolicy.fromMode(PrivacyMode.STRICT)
            val output = Redaction.apply(body, policy, stableHostSalt = "salt")

            assertFalse(
                output.contains("BLANK-GAP-SECRET-3"),
                "shift=$shift: a blank line between key and value must not carry the pair past a window cut",
            )
            assertFalse(
                output.contains("REDACTION INCOMPLETE") || output.contains("REDACTION BUDGET EXCEEDED"),
                "shift=$shift: the sweep must prove the pair was REDACTED, not that the window was DROPPED",
            )
            assertTrue(
                output.contains("\"token\""),
                "shift=$shift: the key must survive, so the pair was redacted in place, not removed wholesale",
            )
        }
    }

    // (PRIV-06) CR-02 / WR-06: one oversize body whose JSON [pair] sits [shift] characters past the
    // natural PAD_LINE_CHARS alignment. Sweeping [shift] walks the pair across the deterministic
    // window cut one character at a time, which is the whole technique: the defect is invisible at
    // most alignments and only appears when the cut lands between the key and its value.
    private fun boundarySweepBody(
        shift: Int,
        pair: String,
    ): String =
        buildString {
            val pad = "y".repeat(PAD_LINE_CHARS - 1) + "\n"
            while (length < Defaults.MAX_REDACTION_BODY_CHARS - PAD_LINE_CHARS - shift) append(pad)
            append("z".repeat(shift)).append('\n')
            append(pair)
            while (length < Defaults.MAX_REDACTION_BODY_CHARS + SWEEP_TAIL_CHARS) append(pad)
        }
}
