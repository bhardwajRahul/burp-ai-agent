package com.six2dez.burp.aiagent.mcp.tools

import com.six2dez.burp.aiagent.mcp.McpToolContext
import io.modelcontextprotocol.kotlin.sdk.server.Server

internal fun Server.registerRequestTools(context: McpToolContext) {
    McpToolRegistrations.request.forEach { registerToolHandler(it, context) }
}
