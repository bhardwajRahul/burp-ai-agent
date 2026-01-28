# Burp MCP + Codex CLI — Operational Notes

## 1. Purpose
Connect Codex CLI (or any MCP-compatible client) to Burp AI Agent's MCP server so the agent can access live Burp data and tools.

## 2. Components
- Burp Suite with this extension enabled
- Built-in MCP server (SSE + optional stdio bridge)
- Codex CLI configuration (config.toml or `codex mcp ...`)

## 3. Expected topology
- SSE: Codex CLI <-> (SSE) <-> Burp AI Agent MCP Server
- Stdio (optional): client connects directly to Burp's built-in stdio bridge

## 4. Enable MCP Server
- Open the AI Agent tab and enable **MCP Server**.
- Default endpoint: `http://127.0.0.1:9876/sse`
- For external access, enable **TLS** and use the generated token via `Authorization: Bearer <token>`.
- Localhost access does not require a token.

## 5. Stdio usage (optional)
- Enable the stdio bridge in the MCP settings.
- Use this mode only when your client expects a stdio MCP server.

## 6. SSE troubleshooting
- Ensure the token is passed as `Authorization: Bearer <token>` when external access is enabled.
- Verify the endpoint is reachable:
  - Local: `curl -v http://127.0.0.1:9876/sse`
  - External (TLS + token): `curl -v -H "Authorization: Bearer <token>" https://<host>:<port>/sse`

## 6a. Claude Desktop MCP
- Add a custom MCP server in Claude Desktop with the SSE URL above.
- If TLS is enabled, switch to https and include the Bearer token.

## 7. Codex CLI configuration
Use the MCP configuration mechanism supported by your Codex CLI version and point it at the SSE endpoint:

- **SSE URL**: `http://127.0.0.1:9876/sse` (local)
- **External access**: enable TLS and send `Authorization: Bearer <token>`
- **STDIO**: enable the STDIO bridge in settings if your client requires stdio transport

## 8. Security considerations
- Localhost-only by default
- TLS required for external access
- Privacy mode redacts MCP tool outputs when enabled
- Tool outputs are capped by the configured max body size (default 2 MB)
- Avoid sharing tokens

## 9. Troubleshooting checklist
- Burp MCP server not running
- Wrong endpoint path
- SSE returns 403 (Origin or Host restriction)
- Java not found / wrong JRE
- Codex CLI not seeing MCP server
Include specific commands:
- `curl -v .../sse`
- checking listening ports

## 10. How this integrates with this extension
- MCP server is built into the extension
- Configuration lives in the Settings panel (MCP section)

## 11. Available MCP Tools
The extension exposes the following tools via MCP:

### Read-only tools (always available):
- `status` - Check Burp status
- `proxy_http_history` - Search proxy history
- `site_map` - Query site map
- `scope_check` - Check if URL is in scope
- `params_extract` - Extract parameters from request
- `find_reflected` - Find reflected values in response
- `scanner_issues` - Get scanner findings (Pro only)

### Write tools (require Unsafe Mode):
- `http1_request` / `http2_request` - Send HTTP requests
- `repeater_tab` / `repeater_tab_with_payload` - Create Repeater tabs
- `intruder` - Send to Intruder
- `collaborator_generate` / `collaborator_poll` - Collaborator interactions
- `issue_create` - Create Burp issues programmatically

## 12. AI Passive Scanner
The extension includes an AI Passive Scanner that:
- Automatically analyzes proxy traffic in background
- Uses the selected AI backend for analysis
- Creates Burp issues with `[AI Passive]` prefix when confidence >= 85%
- Configurable via Settings → AI Passive Scanner section

Settings:
- Enable/disable toggle
- In-scope only filter
- Rate limit (minimum seconds between analyses)
- Max response size to analyze

Issues created by the passive scanner should be manually verified before use in reports.
