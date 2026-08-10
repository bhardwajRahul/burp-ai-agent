package com.six2dez.burp.aiagent.mcp

import okhttp3.Dns
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.net.InetAddress
import java.nio.file.Files
import java.nio.file.Path

private const val HEALTH = "/__mcp/health"
private const val MESSAGE = "/message"
private const val SSE = "/sse"

/** The DNS-rebinding authority. Resolved to 127.0.0.1 by the client's Dns override, never by real DNS. */
private const val FOREIGN_HOST = "evil.example"

private const val FOREIGN_REFERER = "http://evil.example/x"
private const val CHROME_UA =
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

/**
 * The sentinel `McpAccessControlPlugin.requestFacts` resolves to when the HTTP/2 connection point's
 * port getter throws. Duplicated as a literal here ON PURPOSE: asserting against the production
 * constant would make the assertion tautological, whereas a literal pins the contract independently.
 */
private const val UNRESOLVABLE_AUTHORITY = "<unresolved-authority>"

/**
 * SEC-04 / SC2 regression gate for LOCAL mode over TLS, where ALPN negotiates HTTP/2 — the transport
 * on which the DNS-rebinding check did not run at all.
 *
 * Why this class exists: `McpAccessControlPipelineTest` proves all three local-mode vectors over
 * cleartext HTTP/1.1, and every one of those assertions was green while a foreign authority reached
 * `200` on `/__mcp/health` and `200` on `/sse` over HTTP/2 in the same product. The cause was that
 * `requestFacts` read only the HTTP/1 `Host` header: Ktor 3.1.3 does not synthesise `Host` from
 * HTTP/2's `:authority`, and its Netty h2 request implementation filters every pseudo-header out of
 * the Ktor headers map by testing whether the name's first character is `:`. `facts.host` was
 * therefore null on every h2 request, and `evaluateLocal`'s guard is
 * `facts.host != null && !isLoopbackAuthority(...)`, so the branch was skipped entirely. This is the
 * same class of defect phase 20 exists to eliminate: a check that exists but does not run on a
 * reachable path.
 *
 * Local mode with TLS is user-reachable — the "Enable TLS" checkbox is independent of the external-access
 * toggle, and `KtorMcpServerManager.start` rejects only `externalEnabled && !tlsEnabled`.
 *
 * Client note — why the authority is forged through the URL and not through a header: OkHttp's
 * `Http2ExchangeCodec` carries `host` in its skipped-request-header set, so an explicitly-set `Host`
 * header is DROPPED on an HTTP/2 request and setting one proves nothing. The only way to control the
 * `:authority` pseudo-header is the request URL plus a [Dns] override resolving the forged name to
 * 127.0.0.1. The trust-all client's permissive hostname verifier already accepts the resulting SNI
 * mismatch. Never `HttpURLConnection`: it drops `Origin` and overwrites `Host`.
 *
 * Naming note: this class is deliberately NOT named with any of the `*IntegrationTest`,
 * `*ConcurrencyTest`, `*BackpressureTest`, `*RestartPolicyTest` or `*SupervisionTest` suffixes that the
 * `excludeHeavyTests` filter block inside `tasks.test` in `build.gradle.kts` excludes under
 * `-PexcludeHeavyTests=true`. A security gate wearing one of those suffixes would be silently skipped
 * in every fast PR gate and would prove nothing. Do not rename it into one of those patterns.
 */
class McpLocalTlsAuthorityPipelineTest {
    companion object {
        /** Created once per class so `keytool` auto-generation (1-2 s) runs once rather than per test. */
        private lateinit var keystoreDir: Path

        @JvmStatic
        @BeforeAll
        fun createKeystoreDir() {
            keystoreDir = Files.createTempDirectory("mcp-ac-localtls-ks")
        }

        @JvmStatic
        @AfterAll
        fun deleteKeystoreDir() {
            // Review finding IN-07: a leaked temp keystore is exactly what this must not do.
            keystoreDir.toFile().deleteRecursively()
        }
    }

    // ---------------------------------------------------------------------------------------
    // SC2, authority limb, over HTTP/2 — THREE SEPARATE METHODS, one per path.
    //
    // Do NOT fold these into one test looping the three paths through a shared helper, even though
    // McpAccessControlPipelineTest.foreignHost_isRejectedOnEveryPath (McpAccessControlPipelineTest.kt:126-133)
    // is the in-file precedent for doing so. A single looping test aborts at its FIRST failed
    // assertion and emits ONE <failure> element, which would defeat plan 20-08 Task 2's red-count
    // gate (`AssertionFailedError` occurrences >= 3) against a correct implementation. Three methods
    // emit three failures and give per-path granularity. Collapsing them back into a loop silently
    // breaks that gate.
    // ---------------------------------------------------------------------------------------

    @Test
    fun foreignAuthorityOverHttp2_isRejectedOnHealth() {
        withLocalTlsServer { port, client ->
            assertProtocolThenCode(
                client,
                get(foreignUrl(port, HEALTH)),
                403,
                "pre-fix this was 200: facts.host was null over h2, so the DNS-rebinding branch never ran",
            )
        }
    }

    @Test
    fun foreignAuthorityOverHttp2_isRejectedOnMessage() {
        withLocalTlsServer { port, client ->
            assertProtocolThenCode(
                client,
                post(foreignUrl(port, MESSAGE)),
                403,
                "pre-fix this was 400 \"sessionId query parameter is not provided\" — the SDK handler RUNNING " +
                    "is the proof of the bypass",
            )
        }
    }

    @Test
    fun foreignAuthorityOverHttp2_isRejectedOnSse() {
        withLocalTlsServer { port, client ->
            // P5: assert code and protocol inside use {} and never read a text/event-stream body.
            assertProtocolThenCode(
                client,
                get(foreignUrl(port, SSE)),
                403,
                "pre-fix this was 200 text/event-stream — a live MCP session minted for a rebinding peer",
            )
        }
    }

    // ---------------------------------------------------------------------------------------
    // Positive control — the fix must deny only FOREIGN authorities
    // ---------------------------------------------------------------------------------------

    @Test
    fun loopbackAuthorityOverHttp2_stillReachesHealth() {
        withLocalTlsServer { port, client ->
            assertProtocolThenCode(
                client,
                get(loopbackUrl(port, HEALTH)),
                200,
                "the same server and the same client with the REAL loopback authority must still be served: " +
                    "a 403 here would mean the h2 fallback breaks every legitimate h2 client",
            )
        }
    }

    // ---------------------------------------------------------------------------------------
    // SC2's other two local-mode vectors, over HTTP/2.
    //
    // Both use the LOOPBACK authority so the host branch does not fire. `Referer` and `User-Agent` are
    // ordinary headers that survive the engine's pseudo-header filter, so these two are expected to
    // pass BEFORE and AFTER the plugin fix — they are NOT part of the red-before-green evidence and
    // must not be counted among it. They exist so that a future h2 regression in the Referer or
    // browser-UA limb goes red instead of staying silent, rather than being covered only by accident.
    // ---------------------------------------------------------------------------------------

    @Test
    fun foreignRefererOverHttp2_isRejected() {
        withLocalTlsServer { port, client ->
            assertProtocolThenCode(
                client,
                get(loopbackUrl(port, HEALTH), "Referer" to FOREIGN_REFERER),
                403,
                "the Referer limb must hold over h2 too (green before and after the authority fix)",
            )
        }
    }

    @Test
    fun browserUserAgentWithoutOriginOverHttp2_isRejected() {
        withLocalTlsServer { port, client ->
            assertProtocolThenCode(
                client,
                get(loopbackUrl(port, HEALTH), "User-Agent" to CHROME_UA),
                403,
                "the browser-without-Origin limb must hold over h2 too (green before and after the fix)",
            )
        }
    }

    // ---------------------------------------------------------------------------------------
    // The guarded-fallback sentinel contract
    // ---------------------------------------------------------------------------------------

    @Test
    fun unresolvableAuthoritySentinel_isRejectedByTheSharedPredicate() {
        // requestFacts resolves to this sentinel when Http2LocalConnectionPoint.serverPort throws
        // NumberFormatException on an attacker-supplied malformed :authority (it calls Integer.parseInt
        // with no exception handler). The whole guard is only fail-closed if the shared authority
        // parser REJECTS the sentinel: were it ever accepted, a malformed authority would become an
        // ALLOW instead of a denial. parseAuthority's host character class excludes `<` and `>`.
        assertFalse(
            isLoopbackAuthority(UNRESOLVABLE_AUTHORITY, 9876),
            "the unresolvable-authority sentinel must never be treated as loopback — the guarded h2 port " +
                "read in McpAccessControlPlugin.requestFacts depends on this to fail closed",
        )
    }

    // ---------------------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------------------

    /**
     * One manager and one free port per test method (RESEARCH P6): `shutdown()` terminates the
     * manager's shared single-thread executor, so an instance can never be restarted. The keystore
     * directory IS shared — written once by `keytool`, then only read.
     */
    private fun withLocalTlsServer(body: (Int, OkHttpClient) -> Unit) {
        val port = McpTestServerSupport.freePort()
        val manager = KtorMcpServerManager(McpTestServerSupport.deepStubApi())
        try {
            McpTestServerSupport.startAndAwaitRunning(
                manager,
                McpTestServerSupport.localTlsSettings(port, keystoreDir),
            )
            body(port, rebindingClient())
        } finally {
            manager.shutdown()
        }
    }

    /** Trust-all client whose DNS resolves EVERY name to loopback, so a forged authority can be sent. */
    private fun rebindingClient(): OkHttpClient =
        McpTestServerSupport
            .trustAllClient()
            .newBuilder()
            .dns(Dns { listOf(InetAddress.getByName("127.0.0.1")) })
            .build()

    /**
     * Asserts the negotiated protocol FIRST and only then the status code. The order is load-bearing:
     * if ALPN ever stops negotiating h2, this class would otherwise silently re-test HTTP/1.1 — which
     * is already covered elsewhere — and pass for the wrong reason. That is exactly the
     * coverage-by-accident failure mode phase 20 was opened to remove.
     */
    private fun assertProtocolThenCode(
        client: OkHttpClient,
        request: Request,
        expectedCode: Int,
        why: String,
    ) {
        client.newCall(request).execute().use { response ->
            assertEquals(
                Protocol.HTTP_2,
                response.protocol,
                "this assertion pins the TRANSPORT: without h2 the rest of this test proves nothing new",
            )
            assertEquals(expectedCode, response.code, "${request.method} ${request.url.encodedPath} — $why")
        }
    }

    private fun foreignUrl(
        port: Int,
        path: String,
    ): String = "https://$FOREIGN_HOST:$port$path"

    private fun loopbackUrl(
        port: Int,
        path: String,
    ): String = "https://127.0.0.1:$port$path"

    private fun get(
        url: String,
        vararg headers: Pair<String, String>,
    ): Request = builder(url, headers).get().build()

    private fun post(
        url: String,
        vararg headers: Pair<String, String>,
    ): Request = builder(url, headers).post("{}".toRequestBody("application/json".toMediaType())).build()

    private fun builder(
        url: String,
        headers: Array<out Pair<String, String>>,
    ): Request.Builder =
        Request
            .Builder()
            .url(url)
            .apply { headers.forEach { (name, value) -> header(name, value) } }
}
