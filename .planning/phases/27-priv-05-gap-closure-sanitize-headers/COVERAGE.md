# API Coverage — Phase 27

No external API integration: this phase changes two in-process header-name matchers
(`Redaction` and `McpToolHelpers.sanitizeHeaders`) and their tests; the only "API" named anywhere in
the phase scope is the Burp Montoya host API this extension already runs inside, and the MCP tool
surface it already exposes. Nothing is integrated, wrapped, connected or consumed for the first time.

The deterministic detector was run at plan time over the ROADMAP Phase 27 section and returned
`{"detected": false, "signals": []}`. This declaration is recorded anyway because the seal-time gate
re-runs the detector over the PLAN bodies, which discuss the `request_parse` / `response_parse` MCP
tools by name — the same false-positive class that fired on Phase 23 (`wraps api`, `(surface) api`,
both tracing to the Montoya host API) and was closed there with a reasoned declaration rather than a
fabricated matrix.
