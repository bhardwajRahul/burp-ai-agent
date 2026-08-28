---
status: complete
phase: 20-mcp-access-control-correctness
source: [20-VERIFICATION.md]
started: 2026-08-10T10:15:00Z
updated: 2026-08-28T12:52:00Z
---

## Current Test

[testing complete]

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

result: pass
note: |
  Confirmed by the maintainer on 2026-08-28 against a live Burp with a real MCP client, in BOTH
  configurations (TLS off / HTTP/1.1 and TLS on / HTTP/2).

  WHAT THIS ESTABLISHES: documented residual 3 did not fire. The client's h2 `:authority` carried an
  explicit port, so it matched the bound loopback socket and the gate allowed it. The one plausible
  path by which this hardening could lock out a legitimate client is measured NOT to occur for this
  client.

  WHAT IT DOES NOT ESTABLISH: residual 3 is not closed, only unobserved for the client tested. A
  different MCP client that emits a PORTLESS `:authority` over h2 would still acquire the scheme
  default port (443), fail the authority comparison, and be denied. The residual remains documented
  in `McpAccessControlPlugin.requestFacts`; this result narrows its observed reach, it does not
  remove it.

### 2. External mode with bearer token over TLS

Connect a real MCP client in EXTERNAL mode with the bearer token configured, over TLS.

expected: Client connects over TLS and authenticates; no 401 for a correctly configured client.

why_human: Same — live third-party client plus TLS trust configuration.

result: pass
note: |
  Confirmed by the maintainer on 2026-08-28 against a live Burp with a real MCP client.

  WHAT THIS ESTABLISHES: the external-mode gate does not OVER-reject. A correctly configured client
  presenting the bearer token over TLS authenticates and connects — no spurious 401. This is the
  complement of SC1's automated half, which asserts that a request with NO Authorization header IS
  rejected with 401. Both directions of the gate are now evidenced: it rejects the unauthenticated
  and admits the authenticated.

  WHAT IT DOES NOT ESTABLISH: this exercises one client with one correct token over one TLS
  configuration. It is a negative-control on over-rejection, not a survey of token-handling edge
  cases (expiry, rotation, malformed headers), which remain covered — where covered at all — by the
  automated suite rather than by this test.

## Summary

total: 2
passed: 2
issues: 0
pending: 0
skipped: 0
blocked: 0

## Gaps
