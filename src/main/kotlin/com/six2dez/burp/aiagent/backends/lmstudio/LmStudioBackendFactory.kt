package com.six2dez.burp.aiagent.backends.lmstudio

import com.six2dez.burp.aiagent.backends.AiBackend
import com.six2dez.burp.aiagent.backends.AiBackendFactory

class LmStudioBackendFactory : AiBackendFactory {
    override fun create(): AiBackend = LmStudioBackend()
}
