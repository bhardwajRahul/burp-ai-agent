package com.six2dez.burp.aiagent.mcp.tools

import com.six2dez.burp.aiagent.mcp.McpToolContext
import io.modelcontextprotocol.kotlin.sdk.server.Server

internal fun Server.registerCollaboratorTools(context: McpToolContext) {
    McpToolRegistrations.collaborator.forEach { registerToolHandler(it, context) }
}
