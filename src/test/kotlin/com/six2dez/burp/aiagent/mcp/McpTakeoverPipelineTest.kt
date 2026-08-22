package com.six2dez.burp.aiagent.mcp

import com.six2dez.burp.aiagent.config.McpSettings
import okhttp3.Request
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * SEC-07 / SC1 — the legitimate half of the bind-conflict takeover, proven end to end.
 *
 * `McpTakeoverSquatterTest` proves the attacker gets nothing. This class proves the operator does not
 * lose the feature in the process: a REAL [KtorMcpServerManager] on a loopback port must still accept
 * the new proof credential and actually stop, otherwise every ordinary extension reload that hits a
 * bind conflict would leave the MCP server permanently down. That failure mode would be invisible to
 * a mock-based test, which is why this binds a real Netty server.
 *
 * Bounded waiting here is ATTEMPT-bounded, never a fixed sleep and never a wall-clock threshold: this
 * repo has a recorded wall-clock flake class (a 50ms deadline in `RedactionTest` that fails under CPU
 * load), and a parallel-executed suite is exactly the load that reproduces it.
 *
 * Naming note, inherited verbatim from `McpTestServerSupport.kt`: test classes in this package MUST
 * NOT be named `*IntegrationTest`, `*ConcurrencyTest`, `*BackpressureTest`, `*RestartPolicyTest` or
 * `*SupervisionTest`. The `excludeHeavyTests` filter block inside `tasks.test` in `build.gradle.kts`
 * excludes exactly those globs under `-PexcludeHeavyTests=true`, so a security gate wearing one of
 * those suffixes would be silently skipped in a fast PR gate and prove nothing.
 */
class McpTakeoverPipelineTest {
    private val api = McpTestServerSupport.deepStubApi()
    private val supervisor = McpSupervisor(api)

    @AfterEach
    fun tearDown() {
        // Releases the supervisor's scheduler thread and its default (never started) server manager.
        supervisor.shutdown()
    }

    @Test
    fun theProofCredentialTakesOverOurOwnRunningServer() {
        val port = McpTestServerSupport.freePort()
        val settings = McpTestServerSupport.localSettings(port)
        val manager = KtorMcpServerManager(McpTestServerSupport.deepStubApi())
        try {
            McpTestServerSupport.startAndAwaitRunning(manager, settings)

            assertTrue(
                requestRemoteShutdown(settings),
                "The takeover client must be accepted by our own server through the proof header alone. " +
                    "If this is red, the client and server halves of McpTakeoverProof have drifted: the " +
                    "MCP server would stay down after every bind conflict, which is the reliability " +
                    "regression Option A was rejected for.",
            )

            assertTrue(
                awaitServerStopped(port),
                "A 2xx on /__mcp/shutdown is not proof of shutdown — the route responds before it submits " +
                    "the stop. The server must actually stop listening, otherwise the retry that follows a " +
                    "bind conflict would hit the same busy port forever.",
            )
        } finally {
            manager.shutdown()
        }
    }

    @Test
    fun aBlankTokenMakesNoRequestAtAll() {
        // A blank configured token cannot key a meaningful proof. The client must fail closed BEFORE
        // opening a connection rather than presenting an empty credential to the port holder.
        val port = McpTestServerSupport.freePort()
        val settings = McpTestServerSupport.localSettings(port, token = "")
        val manager = KtorMcpServerManager(McpTestServerSupport.deepStubApi())
        try {
            McpTestServerSupport.startAndAwaitRunning(manager, settings)

            assertTrue(
                !requestRemoteShutdown(settings),
                "With a blank token the takeover must be refused client-side",
            )
            assertTrue(
                !awaitServerStopped(port, maxAttempts = SHORT_ATTEMPTS),
                "The server must still be listening: a blank-token takeover must not have been attempted",
            )
        } finally {
            manager.shutdown()
        }
    }

    /**
     * Reaches the private shutdown client by reflection, exactly as `McpSupervisorProbeTest.probe`
     * reaches `probeExistingServer`. The name is `requestRemoteShutdown` and not the Phase-22-era
     * `requestRemoteShutdownWithToken`: the token is no longer on the wire, so the old name was a lie.
     */
    private fun requestRemoteShutdown(settings: McpSettings): Boolean {
        val method = supervisor.javaClass.getDeclaredMethod("requestRemoteShutdown", McpSettings::class.java)
        method.isAccessible = true
        return method.invoke(supervisor, settings) as Boolean
    }

    /**
     * Polls `/__mcp/health` until the connection fails, bounded by an ATTEMPT COUNT rather than by a
     * wall-clock deadline. Returns true once the port stops answering.
     */
    private fun awaitServerStopped(
        port: Int,
        maxAttempts: Int = DEFAULT_ATTEMPTS,
    ): Boolean {
        val client = McpTestServerSupport.plainClient()
        val request =
            Request
                .Builder()
                .url("${McpTestServerSupport.baseUrl(port, tls = false)}$HEALTH_PATH")
                .get()
                .build()
        repeat(maxAttempts) {
            val stillListening =
                try {
                    client.newCall(request).execute().use { true }
                } catch (_: Exception) {
                    false
                }
            if (!stillListening) return true
            Thread.sleep(POLL_INTERVAL_MS)
        }
        return false
    }

    private companion object {
        private const val DEFAULT_ATTEMPTS = 200
        private const val SHORT_ATTEMPTS = 5
        private const val POLL_INTERVAL_MS = 50L
    }
}
