---
status: partial
phase: 20-mcp-access-control-correctness
source: [20-VERIFICATION.md]
started: 2026-08-10T10:15:00Z
updated: 2026-08-10T10:15:00Z
---

## Current Test

[awaiting human testing]

## Tests

### 1. Local SSE with a real MCP client — BOTH TLS off (HTTP/1.1) and TLS on (HTTP/2)

Connect a real MCP client (Claude Desktop / Codex CLI) over LOCAL SSE with the gate active — once with
TLS off, then again with the "Enable TLS" checkbox ON. List tools and call one read-only tool in each
configuration.

expected: Client connects and the tool call succeeds in BOTH configurations.

The TLS/h2 run is the new one and the one that matters. The gate now DENIES any local-mode request whose
resolved authority is not the bound loopback socket, and over HTTP/2 a **portless** `:authority` acquires
the scheme default port (443) and is therefore denied where HTTP/1.1 would allow it — documented residual
3 in `McpAccessControlPlugin.requestFacts`. A client that emits a portless `:authority` over h2 would be
locked out. This is the only plausible path by which this hardening could break a legitimate client.

why_human: Requires a live third-party MCP client and a running Burp instance. No automated seam exists,
and the h2 authority shape a given client emits cannot be predicted from this repo.

result: [pending]

### 2. External mode with bearer token over TLS

Connect a real MCP client in EXTERNAL mode with the bearer token configured, over TLS.

expected: Client connects over TLS and authenticates; no 401 for a correctly configured client.

why_human: Same — live third-party client plus TLS trust configuration.

result: [pending]

## Summary

total: 2
passed: 0
issues: 0
pending: 2
skipped: 0
blocked: 0

## Gaps
