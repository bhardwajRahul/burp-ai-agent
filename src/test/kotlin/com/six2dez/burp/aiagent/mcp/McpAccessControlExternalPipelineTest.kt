package com.six2dez.burp.aiagent.mcp

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

private const val HEALTH = "/__mcp/health"
private const val MESSAGE = "/message"
private const val SSE = "/sse"
private const val SHUTDOWN = "/__mcp/shutdown"

private const val VALID_TOKEN = "test-token"
private const val VALID_BEARER = "Bearer test-token"

/** The four SC3 headers, asserted individually so a partial regression cannot hide behind an aggregate. */
private val EXPECTED_SECURITY_HEADERS =
    listOf(
        "X-Frame-Options" to "DENY",
        "X-Content-Type-Options" to "nosniff",
        "Referrer-Policy" to "same-origin",
        "Content-Security-Policy" to "default-src 'none'",
    )

/**
 * SEC-04 / SC1 / SC5c / D-01 / D-02 regression gate for EXTERNAL mode, driven against a REAL
 * TLS Netty server started through [KtorMcpServerManager].
 *
 * Why this class exists: the pre-fix access-control block sat in the `Call` phase and was registered
 * after `routing {}`, so `RoutingRoot`'s own Call-phase interceptor served every resolved route
 * first. `POST /message` without a credential answered `400 "sessionId query parameter is not
 * provided"` — proof that the SDK handler RAN — and `GET /sse` answered `200 text/event-stream`,
 * minting a live MCP session for an unauthenticated peer. `KtorMcpServerManagerSecurityTest` was
 * green throughout because it invoked the token predicates in isolation, and they were always
 * correct; they were simply never called. Every assertion below is red against the pre-fix manager
 * (proven by plan `20-06`) and green against the `Plugins`-phase gate.
 *
 * TLS is not optional here: `KtorMcpServerManager.start` throws
 * `IllegalStateException("External MCP access requires TLS. Enable TLS to continue.")` when
 * `externalEnabled && !tlsEnabled`, so `testApplication {}` cannot cover SC1 at all — a real TLS
 * connector must be bound.
 *
 * Naming note: `McpAccessControlExternalPipelineTest` is deliberately NOT matched by the
 * `*IntegrationTest` / `*ConcurrencyTest` / `*BackpressureTest` / `*RestartPolicyTest` /
 * `*SupervisionTest` exclusion globs that the `excludeHeavyTests` filter block inside `tasks.test` in
 * `build.gradle.kts` applies under `-PexcludeHeavyTests=true`. Anchored on that symbol rather than a
 * line range: the earlier `:145-157` / `:154-166` citations both went stale as the file moved. A
 * security gate wearing one of those suffixes would be silently skipped in every fast PR gate and
 * would prove nothing. Do not rename this class into one of those patterns.
 *
 * Transport note: `message_withValidBearer_reachesHandlerAndCarriesSecurityHeaders` asserts
 * `response.protocol` is HTTP/2, so SC3's HTTP/2 half is a contract rather than coverage that merely
 * happened to hold — an ALPN change now fails this class instead of silently erasing the evidence.
 *
 * Client note: OkHttp, and NEVER the JDK's built-in URL-connection client (the one
 * `McpServerIntegrationTest` uses through its `httpRequest` helper). That client applies a
 * restricted-header list: it silently drops `Origin` and always overwrites `Host` with the connect
 * authority, which makes the sibling class's SC2 assertions unwritable and makes this class's header
 * handling unpredictable. The identifier is spelled out in `McpTestServerSupport`'s KDoc; it is
 * paraphrased here so this file's zero-count guard grep for it measures code and not prose.
 */
class McpAccessControlExternalPipelineTest {
    companion object {
        /**
         * Created ONCE per class so `keytool` auto-generation (measured at 1-2 s) runs once rather
         * than per test. A test must NEVER point this at `~/.burp-ai-agent`-rooted certificate
         * storage: that is the user's real keystore, and `McpTls.resolve` auto-generates into
         * whatever path it is handed, silently overwriting it. Always a temp directory.
         */
        private lateinit var keystoreDir: Path

        @JvmStatic
        @BeforeAll
        fun createKeystoreDir() {
            keystoreDir = Files.createTempDirectory("mcp-ac-ext-ks")
        }

        @JvmStatic
        @AfterAll
        fun deleteKeystoreDir() {
            // File.deleteRecursively rather than Path.deleteRecursively: the latter is still
            // @ExperimentalPathApi and would need an opt-in for no benefit here.
            keystoreDir.toFile().deleteRecursively()
        }
    }

    // ---------------------------------------------------------------------------------------
    // SC1 — no Authorization means no MCP transport, on both SDK routes
    // ---------------------------------------------------------------------------------------

    @Test
    fun message_withoutAuthorization_returns401() {
        withExternalServer { baseUrl, client ->
            assertEquals(
                401,
                statusOf(client, post(baseUrl, MESSAGE)),
                "POST $MESSAGE without Authorization must be 401. Pre-fix it was " +
                    "400 \"sessionId query parameter is not provided\", which is the proof that the SDK handler " +
                    "ran ahead of the token check",
            )
        }
    }

    @Test
    fun sse_withoutAuthorization_returns401() {
        withExternalServer { baseUrl, client ->
            // P5: assert the code and close. Pre-fix this was 200 text/event-stream and a session was
            // minted for an unauthenticated peer; never read this body.
            assertEquals(
                401,
                statusOf(client, get(baseUrl, SSE)),
                "GET $SSE without Authorization must be 401. Pre-fix it was 200 text/event-stream — a live " +
                    "MCP session for an unauthenticated peer",
            )
        }
    }

    // ---------------------------------------------------------------------------------------
    // D-01 + D-02 — the liveness exemption, without the identity signal
    // ---------------------------------------------------------------------------------------

    @Test
    fun health_withoutAuthorization_returns200WithoutAgentHeader() {
        withExternalServer { baseUrl, client ->
            client.newCall(get(baseUrl, HEALTH)).execute().use { response ->
                assertEquals(
                    200,
                    response.code,
                    "D-01: the liveness route stays token-free so McpSupervisor.probeExistingServer can " +
                        "diagnose a bind conflict without putting the bearer token on the wire",
                )
                assertEquals("ok", response.body.string(), "the health body is the liveness contract (D-01)")
                assertNull(
                    response.header("X-Burp-AI-Agent"),
                    "D-02: an unauthenticated scan must not be able to confirm that a Burp AI Agent sits " +
                        "behind this port. Pre-fix the header was present in external mode too",
                )
            }
        }
    }

    // ---------------------------------------------------------------------------------------
    // The allow path — a valid credential reaches the SDK handler, with SC3 headers
    // ---------------------------------------------------------------------------------------

    @Test
    fun message_withValidBearer_reachesHandlerAndCarriesSecurityHeaders() {
        withExternalServer { baseUrl, client ->
            client.newCall(post(baseUrl, MESSAGE, "Authorization" to VALID_BEARER)).execute().use { response ->
                // Asserted BEFORE the status and header assertions on purpose, so an ALPN regression is
                // reported as a protocol regression instead of surfacing as a confusing header failure.
                assertEquals(
                    Protocol.HTTP_2,
                    response.protocol,
                    "SC3 claims the four security headers are present deterministically over both HTTP/1.1 " +
                        "and HTTP/2, yet before this line nothing in the suite asserted the transport: the " +
                        "HTTP/2 half of SC3 held only because this TLS connector happened to negotiate h2 and " +
                        "OkHttp happened not to be protocol-pinned. A change to either would have removed " +
                        "SC3's evidence with no test going red",
                )
                assertEquals(
                    400,
                    response.code,
                    "a valid bearer must pass the gate and reach the SDK handler, which then complains " +
                        "about the missing sessionId — 401 here would mean the gate rejects a legitimate client",
                )
                assertAllFourSecurityHeaders(
                    response,
                    "SC3: appended from the pre-routing Plugins phase. Pre-fix these headers were present " +
                        "over TLS/HTTP-2 and ABSENT over cleartext HTTP/1.1 on the same code path (RESEARCH P3)",
                )
            }
        }
    }

    @Test
    fun invalidBearerForms_areRejected() {
        withExternalServer { baseUrl, client ->
            assertEquals(
                401,
                statusOf(client, post(baseUrl, MESSAGE, "Authorization" to "Bearer wrong-token")),
                "a wrong token must not authenticate",
            )
            assertEquals(
                401,
                statusOf(client, post(baseUrl, MESSAGE, "Authorization" to "bearer $VALID_TOKEN")),
                "the scheme comparison is byte-exact via MessageDigest.isEqual, so a lowercase scheme fails",
            )
            assertEquals(
                401,
                statusOf(client, post(baseUrl, MESSAGE, "Authorization" to "")),
                "an empty Authorization header must not authenticate",
            )
        }
    }

    // ---------------------------------------------------------------------------------------
    // SC6 non-regression
    // ---------------------------------------------------------------------------------------

    @Test
    fun shutdownWithoutToken_stillReturns401() {
        withExternalServer { baseUrl, client ->
            assertEquals(
                401,
                statusOf(client, post(baseUrl, SHUTDOWN)),
                "the in-handler token check on /__mcp/shutdown is the one control that survived the F1 " +
                    "bypass (D-04) and must not regress",
            )
            // The authorized 200 case is deliberately NOT tested here — it would stop the server
            // mid-class. McpServerIntegrationTest owns that pair.
        }
    }

    // ---------------------------------------------------------------------------------------
    // SC5c — a blank configured token can never authenticate
    // ---------------------------------------------------------------------------------------

    @Test
    fun blankConfiguredToken_rejectsEveryAuthenticatedPath() {
        withExternalServer(token = "") { baseUrl, client ->
            // Netty's HTTP decoder strips trailing optional whitespace from header values per RFC 7230,
            // so the exact "Bearer " string may arrive as "Bearer". The outcome is 401 either way, and
            // the exact-string case isAuthorizedBearer("Bearer ", "") is pinned at unit level in
            // KtorMcpServerManagerSecurityTest.
            assertEquals(
                401,
                statusOf(client, post(baseUrl, MESSAGE, "Authorization" to "Bearer ")),
                "SEC-05 5c / F19: a blank configured token must never authenticate. Pre-fix " +
                    "isAuthorized(\"Bearer \", \"\") returned true",
            )
            assertEquals(
                401,
                statusOf(client, post(baseUrl, MESSAGE)),
                "a blank configured token fails closed on every authenticated route, credential or not",
            )
        }
    }

    @Test
    fun blankConfiguredToken_stillAllowsHealth() {
        withExternalServer(token = "") { baseUrl, client ->
            assertEquals(
                200,
                statusOf(client, get(baseUrl, HEALTH)),
                "branch order in evaluateExternal is load-bearing: the D-01 health exemption is evaluated " +
                    "BEFORE the SEC-05 5c blank-token check, so a misconfigured server stays diagnosable",
            )
        }
    }

    // ---------------------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------------------

    /**
     * One manager and one port per test method (RESEARCH P6): `shutdown()` terminates the manager's
     * shared single-thread executor, so an instance can never be restarted and must not be shared
     * across tests. The keystore directory IS shared — it is written once by `keytool` and then read.
     */
    private fun withExternalServer(
        token: String = VALID_TOKEN,
        body: (String, OkHttpClient) -> Unit,
    ) {
        val port = McpTestServerSupport.freePort()
        val manager = KtorMcpServerManager(McpTestServerSupport.deepStubApi())
        try {
            McpTestServerSupport.startAndAwaitRunning(
                manager,
                McpTestServerSupport.externalTlsSettings(port, keystoreDir, token = token),
            )
            body(McpTestServerSupport.baseUrl(port, tls = true), McpTestServerSupport.trustAllClient())
        } finally {
            manager.shutdown()
        }
    }

    private fun assertAllFourSecurityHeaders(
        response: Response,
        why: String,
    ) {
        EXPECTED_SECURITY_HEADERS.forEach { (name, value) ->
            assertEquals(value, response.header(name), "missing or wrong $name — $why")
        }
    }

    /** Executes and returns the status code only, always closing the response (P5-safe for /sse). */
    private fun statusOf(
        client: OkHttpClient,
        request: Request,
    ): Int = client.newCall(request).execute().use { it.code }

    private fun get(
        baseUrl: String,
        path: String,
        vararg headers: Pair<String, String>,
    ): Request = builder(baseUrl, path, headers).get().build()

    private fun post(
        baseUrl: String,
        path: String,
        vararg headers: Pair<String, String>,
    ): Request = builder(baseUrl, path, headers).post("{}".toRequestBody("application/json".toMediaType())).build()

    private fun builder(
        baseUrl: String,
        path: String,
        headers: Array<out Pair<String, String>>,
    ): Request.Builder =
        Request
            .Builder()
            .url("$baseUrl$path")
            .apply { headers.forEach { (name, value) -> header(name, value) } }
}
