package com.six2dez.burp.aiagent.backends

data class BackendLaunchConfig(
    val backendId: String,
    val displayName: String,
    val command: List<String> = emptyList(), // for CLI backends
    val baseUrl: String? = null,             // for HTTP backends
    val model: String? = null,
    val requestTimeoutSeconds: Long? = null,
    val embeddedMode: Boolean = false,
    val sessionId: String? = null,
    val determinismMode: Boolean = false,
    val env: Map<String, String> = emptyMap()
)

interface AgentConnection {
    fun isAlive(): Boolean
    fun send(text: String, onChunk: (String) -> Unit, onComplete: (Throwable?) -> Unit)
    fun stop()
}

interface DiagnosableConnection {
    fun exitCode(): Int?
    fun lastOutputTail(): String?
}

interface AiBackend {
    val id: String
    val displayName: String
    fun launch(config: BackendLaunchConfig): AgentConnection
}

interface AiBackendFactory {
    fun create(): AiBackend
}
