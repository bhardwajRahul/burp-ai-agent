package com.six2dez.burp.aiagent.mcp

import burp.api.montoya.MontoyaApi
import burp.api.montoya.core.BurpSuiteEdition
import com.six2dez.burp.aiagent.TestSettings
import com.six2dez.burp.aiagent.config.McpSettings
import com.six2dez.burp.aiagent.mcp.tools.ResponsePreprocessorSettings
import com.six2dez.burp.aiagent.redact.PrivacyMode
import okhttp3.OkHttpClient
import org.junit.jupiter.api.Assertions.assertTrue
import org.mockito.Answers
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.net.ServerSocket
import java.nio.file.Path
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * Shared lifecycle and HTTP-client helpers for the Phase 20 MCP access-control pipeline tests.
 * Binds a REAL Netty server through [KtorMcpServerManager] — these are not unit fakes.
 *
 * Naming note — two non-negotiables for anyone editing this file:
 *  1. Test classes built on this helper MUST NOT be named `*IntegrationTest`, `*ConcurrencyTest`,
 *     `*BackpressureTest`, `*RestartPolicyTest` or `*SupervisionTest`. `build.gradle.kts:145-157`
 *     excludes exactly those globs under `-PexcludeHeavyTests=true`, so an SC4 security gate wearing
 *     one of those suffixes would be silently skipped in a fast PR gate and prove nothing.
 *  2. The HTTP client here is OkHttp and MUST NEVER be `HttpURLConnection`. The JDK's
 *     restricted-header list silently drops `Origin` and always overwrites `Host` (unless
 *     `-Dsun.net.http.allowRestrictedHeaders=true`), which makes the SC2 foreign-Origin assertion
 *     physically unwritable — the header never leaves the client and the test passes for the wrong
 *     reason.
 *
 * This object carries no `@Test` method and its name does not end in `Test`, so no test-task filter
 * matches it.
 */
internal object McpTestServerSupport {
    /**
     * Bind-and-close then reuse is a TOCTOU race: another process can claim the port between the
     * close and the server's own bind. It is the accepted in-repo precedent
     * (`McpServerIntegrationTest.kt:96-100`, `McpShutdownBoundTest.kt:33`); if CI proves flaky, add a
     * single retry on a `Failed`/`BindException` terminal state rather than changing this helper.
     */
    fun freePort(): Int = ServerSocket(0).use { it.localPort }

    /** Deep-stub MontoyaApi reporting Professional edition, as in `McpServerIntegrationTest.kt:24-25`. */
    fun deepStubApi(): MontoyaApi {
        val api = mock<MontoyaApi>(defaultAnswer = Answers.RETURNS_DEEP_STUBS)
        whenever(api.burpSuite().version().edition()).thenReturn(BurpSuiteEdition.PROFESSIONAL)
        return api
    }

    /**
     * Loopback, cleartext, local-only settings. Built with `copy(...)` on the shared baseline rather
     * than a full `McpSettings(...)` literal so that a new field on `McpSettings` does not break
     * every Phase 20 test.
     */
    fun localSettings(
        port: Int,
        token: String = "test-token",
    ): McpSettings =
        TestSettings.baselineSettings().mcpSettings.copy(
            enabled = true,
            host = "127.0.0.1",
            port = port,
            externalEnabled = false,
            stdioEnabled = false,
            token = token,
            allowedOrigins = emptyList(),
            tlsEnabled = false,
        )

    /**
     * External-mode settings with TLS auto-generation. The caller supplies the keystore directory,
     * normally `Files.createTempDirectory("mcp-ac-ks")`.
     */
    fun externalTlsSettings(
        port: Int,
        keystoreDir: Path,
        token: String = "test-token",
    ): McpSettings =
        TestSettings.baselineSettings().mcpSettings.copy(
            enabled = true,
            host = "127.0.0.1",
            port = port,
            externalEnabled = true,
            stdioEnabled = false,
            token = token,
            allowedOrigins = emptyList(),
            tlsEnabled = true,
            tlsAutoGenerate = true,
            // A test must NEVER point this at ~/.burp-ai-agent/certs — that is the user's real
            // keystore, and McpTls.resolve auto-generates into whatever path it is handed, silently
            // overwriting it. Always a caller-supplied temp directory.
            tlsKeystorePath = keystoreDir.resolve("mcp-test.p12").toString(),
            tlsKeystorePassword = "test-pass",
        )

    /**
     * Starts [manager] and blocks until the lifecycle callback reaches a terminal state, asserting it
     * is [McpServerState.Running].
     *
     * The caller owns teardown: wrap the body in `try { ... } finally { manager.shutdown() }`.
     * `shutdown()` terminates the manager's shared single-thread executor, so an instance cannot be
     * restarted afterwards — use one manager per test (RESEARCH P6).
     *
     * The default timeout is 20 s rather than 10 s because external mode auto-generates a PKCS12 via
     * `keytool` on first start, measured at 1-2 s on top of the normal bind.
     */
    fun startAndAwaitRunning(
        manager: KtorMcpServerManager,
        settings: McpSettings,
        timeoutSeconds: Long = 20,
    ) {
        val terminalState = AtomicReference<McpServerState?>()
        val started = CountDownLatch(1)
        manager.start(
            settings,
            PrivacyMode.STRICT,
            determinismMode = false,
            preprocessSettings = ResponsePreprocessorSettings(),
        ) { state ->
            if (state is McpServerState.Running || state is McpServerState.Failed) {
                terminalState.set(state)
                started.countDown()
            }
        }
        assertTrue(
            started.await(timeoutSeconds, TimeUnit.SECONDS),
            "MCP server did not reach a terminal state within ${timeoutSeconds}s",
        )
        val state = terminalState.get()
        // Include the terminal state so a Failed(BindException) is diagnosable from the report alone.
        assertTrue(state is McpServerState.Running, "MCP server failed to start: $state")
    }

    /**
     * Cleartext client.
     *
     * P5: never call `.body.string()` on a `text/event-stream` 200 response — it blocks until the
     * read timeout because the server keeps the stream open. Assert on `response.code` and headers
     * inside `execute().use { }` and let `use` close it.
     */
    fun plainClient(): OkHttpClient =
        OkHttpClient
            .Builder()
            .callTimeout(4, TimeUnit.SECONDS)
            .readTimeout(2, TimeUnit.SECONDS)
            .build()

    /**
     * TLS client that accepts the self-signed certificate the server auto-generates, with hostname
     * verification disabled. Confined to the test source set and pointed only at a loopback server
     * the test itself started; it is never reachable from `src/main`.
     *
     * Same P5 caveat as [plainClient]: do not read an SSE body.
     */
    fun trustAllClient(): OkHttpClient {
        val tm =
            object : X509TrustManager {
                override fun checkClientTrusted(
                    chain: Array<out X509Certificate>?,
                    authType: String?,
                ) = Unit

                override fun checkServerTrusted(
                    chain: Array<out X509Certificate>?,
                    authType: String?,
                ) = Unit

                override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
            }
        val ctx = SSLContext.getInstance("TLS").apply { init(null, arrayOf<TrustManager>(tm), SecureRandom()) }
        return OkHttpClient
            .Builder()
            .callTimeout(4, TimeUnit.SECONDS)
            .readTimeout(2, TimeUnit.SECONDS)
            .sslSocketFactory(ctx.socketFactory, tm)
            .hostnameVerifier { _, _ -> true }
            .build()
    }

    fun baseUrl(
        port: Int,
        tls: Boolean,
    ): String = if (tls) "https://127.0.0.1:$port" else "http://127.0.0.1:$port"
}
