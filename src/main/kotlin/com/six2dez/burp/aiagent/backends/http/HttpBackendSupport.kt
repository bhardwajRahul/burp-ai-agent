package com.six2dez.burp.aiagent.backends.http

import com.six2dez.burp.aiagent.backends.HealthCheckResult
import com.six2dez.burp.aiagent.config.Defaults
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.EOFException
import java.net.Proxy
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedDeque

object HttpBackendSupport {
    const val CIRCUIT_FAILURE_THRESHOLD: Int = 5
    const val CIRCUIT_RESET_TIMEOUT_MS: Long = 30_000
    const val CIRCUIT_HALF_OPEN_MAX_ATTEMPTS: Int = 1

    private data class ClientKey(
        val baseUrl: String,
        val timeoutSeconds: Long
    )

    private val sharedClients = ConcurrentHashMap<ClientKey, OkHttpClient>()

    fun buildClient(timeoutSeconds: Long): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(java.time.Duration.ofSeconds(10))
            .writeTimeout(java.time.Duration.ofSeconds(30))
            .readTimeout(java.time.Duration.ofSeconds(timeoutSeconds))
            .callTimeout(java.time.Duration.ofSeconds(timeoutSeconds))
            .proxy(Proxy.NO_PROXY)
            .build()
    }

    fun sharedClient(baseUrl: String?, timeoutSeconds: Long): OkHttpClient {
        val safeTimeout = timeoutSeconds.coerceIn(5L, 3600L)
        val key = ClientKey(
            baseUrl = baseUrl.orEmpty().trim().lowercase(),
            timeoutSeconds = safeTimeout
        )
        return sharedClients.computeIfAbsent(key) { buildClient(safeTimeout) }
    }

    fun shutdownSharedClients() {
        val clients = sharedClients.values.toList()
        sharedClients.clear()
        clients.forEach { client ->
            client.dispatcher.executorService.shutdown()
            client.connectionPool.evictAll()
            client.cache?.close()
        }
    }

    fun healthCheckGet(
        url: String,
        headers: Map<String, String>,
        timeoutSeconds: Long = 3L
    ): HealthCheckResult {
        return try {
            val client = sharedClient(url, timeoutSeconds.coerceAtLeast(1L))
            val request = Request.Builder()
                .url(url)
                .apply { headers.forEach { (name, value) -> header(name, value) } }
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                when {
                    response.isSuccessful -> HealthCheckResult.Healthy
                    response.code == 401 || response.code == 403 ->
                        HealthCheckResult.Degraded("Endpoint reachable but authentication failed (HTTP ${response.code}).")
                    else -> HealthCheckResult.Unavailable("HTTP ${response.code}.")
                }
            }
        } catch (e: Exception) {
            HealthCheckResult.Unavailable(e.message ?: "Request failed")
        }
    }

    fun isRetryableConnectionError(e: Exception): Boolean {
        if (e is EOFException) return true
        if (e is java.net.ConnectException || e is java.net.SocketTimeoutException) return true
        if (e is java.net.SocketException) return true
        val msg = e.message?.lowercase().orEmpty()
        return msg.contains("failed to connect") ||
            msg.contains("connection refused") ||
            msg.contains("timeout") ||
            msg.contains("unexpected end of stream") ||
            msg.contains("stream was reset") ||
            msg.contains("end of input")
    }

    fun retryDelayMs(attempt: Int): Long {
        return when (attempt) {
            0 -> 500
            1 -> 1000
            2 -> 1500
            3 -> 2000
            4 -> 3000
            else -> 4000
        }
    }

    fun newCircuitBreaker(): CircuitBreaker {
        return CircuitBreaker(
            failureThreshold = CIRCUIT_FAILURE_THRESHOLD,
            resetTimeoutMs = CIRCUIT_RESET_TIMEOUT_MS,
            halfOpenMaxAttempts = CIRCUIT_HALF_OPEN_MAX_ATTEMPTS
        )
    }

    fun openCircuitError(backendDisplayName: String, retryAfterMs: Long): IllegalStateException {
        val retryDelay = retryAfterMs.coerceAtLeast(1L)
        return IllegalStateException(
            "$backendDisplayName backend is temporarily unavailable (circuit open). Retry in ${retryDelay}ms."
        )
    }
}

class ConversationHistory(
    private val maxMessages: Int = Defaults.MAX_HISTORY_MESSAGES,
    private val maxTotalChars: Int = Defaults.MAX_HISTORY_TOTAL_CHARS
) {
    private val history = ConcurrentLinkedDeque<Map<String, String>>()

    fun addUser(content: String) {
        history.addLast(mapOf("role" to "user", "content" to content))
        trim()
    }

    fun addAssistant(content: String) {
        history.addLast(mapOf("role" to "assistant", "content" to content))
        trim()
    }

    fun snapshot(): List<Map<String, String>> = history.toList()

    fun setHistory(newHistory: List<com.six2dez.burp.aiagent.backends.ChatMessage>) {
        history.clear()
        newHistory.forEach { msg ->
            history.addLast(mapOf("role" to msg.role, "content" to msg.content))
        }
        trim()
    }

    private fun trim() {
        while (history.size > maxMessages) {
            history.pollFirst()
        }
        while (history.size > MIN_MESSAGES_TO_KEEP && totalChars() > maxTotalChars) {
            history.pollFirst()
        }
    }

    private fun totalChars(): Int {
        return history.sumOf { entry ->
            val role = entry["role"].orEmpty()
            val content = entry["content"].orEmpty()
            role.length + content.length + 2
        }
    }

    private companion object {
        private const val MIN_MESSAGES_TO_KEEP = 2
    }
}
