package com.six2dez.burp.aiagent.mcp

import burp.api.montoya.MontoyaApi
import com.six2dez.burp.aiagent.audit.AiRequestLogger
import com.six2dez.burp.aiagent.backends.BackendRegistry
import com.six2dez.burp.aiagent.config.McpSettings
import com.six2dez.burp.aiagent.mcp.tools.CollaboratorRegistry
import com.six2dez.burp.aiagent.mcp.tools.ResponsePreprocessorSettings
import com.six2dez.burp.aiagent.mcp.tools.ScannerTaskRegistry
import com.six2dez.burp.aiagent.redact.PrivacyMode
import com.six2dez.burp.aiagent.scanner.PassiveAiScanner
import com.six2dez.burp.aiagent.supervisor.AgentSupervisor
import java.net.BindException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

interface McpTakeoverClient {
    fun probe(settings: McpSettings): Boolean

    fun requestShutdown(settings: McpSettings): Boolean
}

class McpSupervisor(
    private val api: MontoyaApi,
    private val serverManager: McpServerManager = KtorMcpServerManager(api),
    private val stdioBridge: McpStdioBridge = McpStdioBridge(api),
    private val scheduler: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor(),
    private val takeoverClientOverride: McpTakeoverClient? = null,
    private val maxRestartAttempts: Int = DEFAULT_MAX_RESTART_ATTEMPTS,
    private val maxTakeoverAttempts: Int = DEFAULT_MAX_TAKEOVER_ATTEMPTS,
    private val restartDelayMs: Long = DEFAULT_RESTART_DELAY_MS,
    private val takeoverRetryDelayMs: Long = DEFAULT_TAKEOVER_RETRY_DELAY_MS,
) {
    private val stateRef = AtomicReference<McpServerState>(McpServerState.Stopped)
    private val settingsRef = AtomicReference<McpSettings?>(null)
    private val privacyRef = AtomicReference(PrivacyMode.STRICT)
    private val determinismRef = AtomicReference(false)
    private val preprocessRef = AtomicReference(ResponsePreprocessorSettings())
    private val restartAttempts = AtomicInteger(0)
    private val takeoverAttempts = AtomicInteger(0)
    private val takeoverClient: McpTakeoverClient =
        takeoverClientOverride ?: object : McpTakeoverClient {
            override fun probe(settings: McpSettings): Boolean = probeExistingServer(settings)

            override fun requestShutdown(settings: McpSettings): Boolean = requestRemoteShutdown(settings)
        }

    init {
        require(maxRestartAttempts >= 0) { "maxRestartAttempts must be >= 0" }
        require(maxTakeoverAttempts >= 0) { "maxTakeoverAttempts must be >= 0" }
        require(restartDelayMs > 0) { "restartDelayMs must be > 0" }
        require(takeoverRetryDelayMs > 0) { "takeoverRetryDelayMs must be > 0" }
    }

    fun setAiRequestLogger(logger: AiRequestLogger) {
        serverManager.setAiRequestLogger(logger)
        stdioBridge.setAiRequestLogger(logger)
    }

    fun setAiToolDependencies(
        supervisor: AgentSupervisor,
        passiveScanner: PassiveAiScanner,
        backendRegistry: BackendRegistry,
    ) {
        serverManager.setAiToolDependencies(supervisor, passiveScanner, backendRegistry)
    }

    fun applySettings(
        settings: McpSettings,
        privacyMode: PrivacyMode,
        determinismMode: Boolean,
        preprocessSettings: ResponsePreprocessorSettings,
    ) {
        val previousSettings = settingsRef.get()
        val previousPrivacy = privacyRef.get()
        val previousDeterminism = determinismRef.get()
        val previousPreprocess = preprocessRef.get()

        settingsRef.set(settings)
        privacyRef.set(privacyMode)
        determinismRef.set(determinismMode)
        preprocessRef.set(preprocessSettings)
        ScannerTaskRegistry.configureTtlMinutes(settings.scanTaskTtlMinutes)
        CollaboratorRegistry.configureTtlMinutes(settings.collaboratorClientTtlMinutes)
        ScannerTaskRegistry.setLogger { api.logging().logToOutput("[ScannerTaskRegistry] $it") }
        CollaboratorRegistry.setLogger { api.logging().logToOutput("[CollaboratorRegistry] $it") }

        if (!settings.enabled) {
            stop()
            return
        }

        val alreadyRunning = stateRef.get() is McpServerState.Running
        val settingsUnchanged =
            previousSettings == settings &&
                previousPrivacy == privacyMode &&
                previousDeterminism == determinismMode &&
                previousPreprocess == preprocessSettings
        if (alreadyRunning && settingsUnchanged) {
            if (settings.stdioEnabled) {
                stdioBridge.start(settings, privacyMode, determinismMode, preprocessSettings)
            } else {
                stdioBridge.stop()
            }
            return
        }

        restartAttempts.set(0)
        takeoverAttempts.set(0)
        startInternal(settings, privacyMode, determinismMode, preprocessSettings)

        if (settings.stdioEnabled) {
            stdioBridge.start(settings, privacyMode, determinismMode, preprocessSettings)
        } else {
            stdioBridge.stop()
        }
    }

    fun status(): McpServerState = stateRef.get()

    fun stop() {
        serverManager.stop { state ->
            stateRef.set(state)
        }
        restartAttempts.set(0)
        takeoverAttempts.set(0)
        stdioBridge.stop()
        ScannerTaskRegistry.clear()
        CollaboratorRegistry.clear()
    }

    fun shutdown() {
        scheduler.shutdown()
        try {
            scheduler.awaitTermination(5, TimeUnit.SECONDS)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
        serverManager.shutdown()
        stdioBridge.stop()
    }

    private fun startInternal(
        settings: McpSettings,
        privacyMode: PrivacyMode,
        determinismMode: Boolean,
        preprocessSettings: ResponsePreprocessorSettings,
    ) {
        serverManager.start(settings, privacyMode, determinismMode, preprocessSettings) { state ->
            stateRef.set(state)
            if (state is McpServerState.Running) {
                restartAttempts.set(0)
                takeoverAttempts.set(0)
            }
            if (state is McpServerState.Failed) {
                handleFailure(state.exception)
            }
        }
    }

    private fun handleFailure(exception: Throwable) {
        val settings = settingsRef.get() ?: return
        if (!settings.enabled) return

        if (isBindException(exception)) {
            handleBindFailure(settings)
            return
        }

        val attempt = restartAttempts.incrementAndGet()
        if (attempt > maxRestartAttempts) {
            api.logging().logToError("MCP server failed repeatedly. Giving up after $attempt attempts: ${exception.message}")
            return
        }

        api.logging().logToError("MCP server failed. Restarting in ${restartDelayMs}ms (attempt $attempt/$maxRestartAttempts).")
        scheduleStart(restartDelayMs)
    }

    private fun handleBindFailure(settings: McpSettings) {
        when (attemptTakeover(settings)) {
            BindTakeoverOutcome.SHUTDOWN_REQUESTED -> {
                val attempt = takeoverAttempts.incrementAndGet()
                if (attempt > maxTakeoverAttempts) {
                    api.logging().logToError(
                        "MCP bind conflict persists after $attempt takeover attempts. " +
                            "Port ${settings.host}:${settings.port} is still unavailable.",
                    )
                    return
                }
                api.logging().logToOutput(
                    "MCP bind conflict detected on ${settings.host}:${settings.port}. " +
                        "Shutdown requested from existing MCP server; retrying in ${takeoverRetryDelayMs}ms " +
                        "(attempt $attempt/$maxTakeoverAttempts).",
                )
                scheduleStart(takeoverRetryDelayMs)
            }

            BindTakeoverOutcome.NO_COMPATIBLE_SERVER -> {
                api.logging().logToError(
                    "MCP server failed to bind on ${settings.host}:${settings.port}. " +
                        "Port appears busy and no compatible MCP server was detected for takeover.",
                )
            }

            BindTakeoverOutcome.SHUTDOWN_REJECTED -> {
                api.logging().logToError(
                    "MCP bind conflict on ${settings.host}:${settings.port}. " +
                        "Existing MCP server did not accept shutdown request.",
                )
            }
        }
    }

    private fun attemptTakeover(settings: McpSettings): BindTakeoverOutcome {
        if (!takeoverClient.probe(settings)) {
            return BindTakeoverOutcome.NO_COMPATIBLE_SERVER
        }
        return if (takeoverClient.requestShutdown(settings)) {
            BindTakeoverOutcome.SHUTDOWN_REQUESTED
        } else {
            BindTakeoverOutcome.SHUTDOWN_REJECTED
        }
    }

    private fun scheduleStart(delayMs: Long) {
        scheduler.schedule({
            val current = settingsRef.get() ?: return@schedule
            if (!current.enabled) return@schedule
            startInternal(current, privacyRef.get(), determinismRef.get(), preprocessRef.get())
        }, delayMs, TimeUnit.MILLISECONDS)
    }

    private fun isBindException(exception: Throwable): Boolean {
        var current: Throwable? = exception
        while (current != null) {
            if (current is BindException) return true
            current = current.cause
        }
        return false
    }

    private fun probeExistingServer(settings: McpSettings): Boolean =
        try {
            val scheme = if (settings.tlsEnabled) "https" else "http"
            val url = URI.create("$scheme://${settings.host}:${settings.port}/__mcp/health").toURL()
            val conn = openConnection(url, settings.tlsEnabled)
            try {
                conn.requestMethod = "GET"
                conn.connectTimeout = 800
                conn.readTimeout = 800
                conn.connect()
                val alive = conn.responseCode in 200..299
                // SEC-04 / D-02 / D-05: the identity assertion below is mode-aware because the server
                // side is. Three facts, all load-bearing:
                // (1) KtorMcpServerManager appends `X-Burp-AI-Agent: mcp` to /__mcp/health ONLY when
                //     external access is disabled. Requiring it unconditionally would make
                //     attemptTakeover return NO_COMPATIBLE_SERVER on every external-mode bind
                //     conflict, and handleBindFailure would then schedule NO retry at all — the MCP
                //     server would stay down silently.
                // (2) The header was always trivially spoofable by whichever process holds the port,
                //     which is exactly Finding 7. Degrading external takeover to a liveness check
                //     therefore takes away no guarantee that previously held.
                // (3) Establishing REAL listener identity is Phase 25 / SEC-07 per D-05. Nothing in
                //     this probe may present the bearer token to an unverified port holder, so it
                //     sends no credential header of any kind — do not add one here.
                // (4) SEC-07 update: the signals inspected below are no longer load-bearing for
                //     credential disclosure, because the takeover path no longer has a secret to
                //     disclose — requestRemoteShutdown presents a proof of possession instead of the
                //     token. They remain as a cheap filter that avoids issuing shutdown requests at
                //     listeners that are obviously not ours.
                if (alive && settings.externalEnabled) {
                    api.logging().logToOutput(
                        "MCP probe on ${settings.host}:${settings.port} could not establish server identity: " +
                            "the identifying header is emitted in local mode only. " +
                            "Proceeding with takeover on liveness alone.",
                    )
                }
                alive && (settings.externalEnabled || conn.getHeaderField("X-Burp-AI-Agent") == "mcp")
            } finally {
                conn.disconnect()
            }
        } catch (e: Exception) {
            api.logging().logToOutput("MCP probe failed on ${settings.host}:${settings.port}: ${e.message}")
            false
        }

    /**
     * SEC-07 / T-25-01 — the Phase-22-era name was `requestRemoteShutdownWithToken`, and it stopped
     * being true here: the MCP bearer token is no longer sent to the port holder. It keys an HMAC that
     * proves possession instead, so a local process squatting the port collects nothing it can reuse.
     *
     * Fails closed on a blank token: without a key there is no meaningful proof, and the old code
     * would have presented a bare `Bearer ` to the holder. No request is issued at all in that case.
     */
    private fun requestRemoteShutdown(settings: McpSettings): Boolean {
        val proof = takeoverProof(settings) ?: return false
        return try {
            val scheme = if (settings.tlsEnabled) "https" else "http"
            val url = URI.create("$scheme://${settings.host}:${settings.port}/__mcp/shutdown").toURL()
            val conn = openConnection(url, settings.tlsEnabled)
            try {
                conn.requestMethod = "POST"
                conn.setRequestProperty(McpTakeoverProof.HEADER, proof)
                conn.connectTimeout = 500
                conn.readTimeout = 500
                conn.connect()
                conn.responseCode in 200..299
            } finally {
                conn.disconnect()
            }
        } catch (e: Exception) {
            api.logging().logToOutput("MCP remote shutdown request was not accepted: ${e.message}")
            false
        }
    }

    /**
     * The ONLY place in this file that touches the MCP token, and it uses it as an HMAC key rather
     * than as a header value. `McpTakeoverSquatterTest` asserts that structurally: the token property
     * is referenced exactly once in this file, on a line that also carries
     * `McpTakeoverProof.forTarget`. That assertion goes red the day a future edit puts the secret back
     * on the wire under any header name — so do not name that property anywhere else here, not even
     * in a comment.
     */
    private fun takeoverProof(settings: McpSettings): String? = settings.token.takeIf { it.isNotBlank() }?.let { McpTakeoverProof.forTarget(it, settings.host, settings.port, System.currentTimeMillis()) }

    private fun openConnection(
        url: URL,
        tlsEnabled: Boolean,
    ): HttpURLConnection {
        val conn = url.openConnection() as HttpURLConnection
        if (tlsEnabled && conn is HttpsURLConnection) {
            val isLoopback =
                url.host.equals("localhost", ignoreCase = true) ||
                    url.host.equals("127.0.0.1") ||
                    url.host.equals("::1")

            if (isLoopback) {
                val trustAll =
                    arrayOf<TrustManager>(
                        object : X509TrustManager {
                            override fun getAcceptedIssuers() = emptyArray<java.security.cert.X509Certificate>()

                            override fun checkClientTrusted(
                                chain: Array<java.security.cert.X509Certificate>,
                                authType: String,
                            ) = Unit

                            override fun checkServerTrusted(
                                chain: Array<java.security.cert.X509Certificate>,
                                authType: String,
                            ) = Unit
                        },
                    )
                val sslContext = SSLContext.getInstance("TLS")
                sslContext.init(null, trustAll, java.security.SecureRandom())
                conn.sslSocketFactory = sslContext.socketFactory
                conn.hostnameVerifier = HostnameVerifier { _, _ -> true }
            }
        }
        return conn
    }

    private enum class BindTakeoverOutcome {
        SHUTDOWN_REQUESTED,
        NO_COMPATIBLE_SERVER,
        SHUTDOWN_REJECTED,
    }

    private companion object {
        private const val DEFAULT_MAX_RESTART_ATTEMPTS = 4
        private const val DEFAULT_MAX_TAKEOVER_ATTEMPTS = 3
        private const val DEFAULT_RESTART_DELAY_MS = 2_000L
        private const val DEFAULT_TAKEOVER_RETRY_DELAY_MS = 1_000L
    }
}
