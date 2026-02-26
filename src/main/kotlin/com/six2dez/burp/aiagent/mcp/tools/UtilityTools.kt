package com.six2dez.burp.aiagent.mcp.tools

import com.six2dez.burp.aiagent.mcp.McpToolContext
import io.modelcontextprotocol.kotlin.sdk.server.Server

internal fun Server.registerUtilityTools(context: McpToolContext) {
    McpToolRegistrations.utility.forEach { registerToolHandler(it, context) }
}
