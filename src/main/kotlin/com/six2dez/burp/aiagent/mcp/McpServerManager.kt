package com.six2dez.burp.aiagent.mcp

import com.six2dez.burp.aiagent.config.McpSettings
import com.six2dez.burp.aiagent.redact.PrivacyMode

interface McpServerManager {
    fun start(settings: McpSettings, privacyMode: PrivacyMode, determinismMode: Boolean, callback: (McpServerState) -> Unit)
    fun stop(callback: (McpServerState) -> Unit)
    fun shutdown()
}
