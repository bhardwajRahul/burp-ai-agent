package com.six2dez.burp.aiagent.mcp

import com.six2dez.burp.aiagent.config.McpSettings
import okhttp3.Request
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

/**
 * SEC-07 / SC5 — the loopback TLS takeover client trusts exactly one certificate.
 *
 * `McpSupervisorConnectionTest` proves the WIRING: that a socket factory and a hostname verifier are
 * installed on the loopback TLS branch and withheld everywhere else. Wiring is not a handshake, and a
 * pin that is installed but never discriminates looks identical to no pin at all in a green run.
 * Every test here therefore drives a REAL [KtorMcpServerManager] over TLS on a loopback port and lets
 * the JDK's TLS stack decide.
 *
 * This commit establishes the legitimate path end to end: a takeover against our own running server
 * still succeeds through the pin, so a bind conflict under TLS does not leave the MCP server
 * permanently down. The discriminating cases follow.
 *
 * Bounded waiting here is ATTEMPT-bounded. There is no `Thread.sleep` and no wall-clock threshold in
 * this file at all: this repo has a recorded wall-clock flake class (a 50 ms deadline in
 * `RedactionTest` that fails under CPU load) and a parallel-executed suite is exactly the load that
 * reproduces it. Pacing comes from the HTTP round trip each attempt performs.
 *
 * Naming note, inherited verbatim from `McpTestServerSupport.kt`: test classes in this package MUST
 * NOT be named `*IntegrationTest`, `*ConcurrencyTest`, `*BackpressureTest`, `*RestartPolicyTest` or
 * `*SupervisionTest`. The `excludeHeavyTests` filter block inside `tasks.test` in `build.gradle.kts`
 * excludes exactly those globs under `-PexcludeHeavyTests=true`, so a security gate wearing one of
 * those suffixes would be silently skipped in a fast PR gate and prove nothing.
 */
class McpTakeoverCertificatePinTest {
    companion object {
        /**
         * Generous on purpose: the shutdown route submits `server.stop(1000, 5000)`, so the port stays
         * bound for a grace period. Each attempt costs a TLS round trip, so the bound is a work budget
         * rather than a time budget and it tightens automatically on a fast machine.
         */
        private const val STOP_POLL_ATTEMPTS = 4000

        /**
         * Created once per class so `keytool` auto-generation (1-2 s) runs once rather than per test.
         * A test must NEVER point `tlsKeystorePath` at `~/.burp-ai-agent/certs`: that is the user's
         * real keystore and `McpTls.resolve` auto-generates into whatever path it is handed.
         */
        private lateinit var keystoreDir: Path

        @JvmStatic
        @BeforeAll
        fun createKeystoreDir() {
            keystoreDir = Files.createTempDirectory("mcp-pin-ks")
        }

        @JvmStatic
        @AfterAll
        fun deleteKeystoreDir() {
            // Review finding IN-07: a leaked temp keystore is exactly what this must not do.
            keystoreDir.toFile().deleteRecursively()
        }
    }

    private val supervisor = McpSupervisor(McpTestServerSupport.deepStubApi())

    @AfterEach
    fun tearDown() {
        // Releases the supervisor's scheduler thread and its default (never started) server manager.
        supervisor.shutdown()
    }

    @Test
    fun aLegitimateTlsTakeoverSucceedsAgainstThePinnedCertificate() {
        val port = McpTestServerSupport.freePort()
        val settings = McpTestServerSupport.localTlsSettings(port, keystoreDir)
        val manager = KtorMcpServerManager(McpTestServerSupport.deepStubApi())
        try {
            McpTestServerSupport.startAndAwaitRunning(manager, settings)

            assertTrue(
                requestRemoteShutdown(settings),
                "A TLS takeover against our OWN server must still succeed: the certificate it presents is " +
                    "the one in the keystore these settings name, so the pin matches. If this is red, a " +
                    "bind conflict under TLS leaves the MCP server permanently down — the reliability " +
                    "regression the fail-closed branch must never cause in the legitimate case.",
            )

            assertTrue(
                awaitServerStopped(port),
                "A 2xx on the shutdown route is not proof of shutdown — the route responds before it " +
                    "submits the stop. The server must actually stop listening, otherwise the retry that " +
                    "follows a bind conflict hits the same busy port forever.",
            )
        } finally {
            manager.shutdown()
        }
    }

    /**
     * Reaches the private shutdown client by reflection, exactly as `McpSupervisorProbeTest.probe`
     * reaches `probeExistingServer`.
     */
    private fun requestRemoteShutdown(settings: McpSettings): Boolean {
        val method = supervisor.javaClass.getDeclaredMethod("requestRemoteShutdown", McpSettings::class.java)
        method.isAccessible = true
        return method.invoke(supervisor, settings) as Boolean
    }

    /**
     * Polls the TLS health route until the connection fails, bounded by an ATTEMPT COUNT rather than
     * by a wall-clock deadline. Each attempt performs a real HTTP round trip, which is what paces the
     * loop; there is deliberately no sleep. Returns true once the port stops answering.
     */
    private fun awaitServerStopped(port: Int): Boolean {
        val client = McpTestServerSupport.trustAllClient()
        val request = healthRequest(port)
        repeat(STOP_POLL_ATTEMPTS) {
            val stillListening =
                try {
                    client.newCall(request).execute().use { true }
                } catch (_: Exception) {
                    false
                }
            if (!stillListening) return true
        }
        return false
    }

    private fun healthRequest(port: Int): Request =
        Request
            .Builder()
            .url("${McpTestServerSupport.baseUrl(port, tls = true)}$HEALTH_PATH")
            .get()
            .build()
}
