package com.six2dez.burp.aiagent.backends.ollama

import com.six2dez.burp.aiagent.backends.AiBackend
import com.six2dez.burp.aiagent.backends.AiBackendFactory

class OllamaBackendFactory : AiBackendFactory {
    override fun create(): AiBackend = OllamaBackend()
}
