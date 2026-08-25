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

**Extended 2026-08-25 (plan 27-09) to cover plans 27-07, 27-08 and 27-09, on the same reasoning and
for the same false-positive class.** Those three PLAN bodies name the `request_parse` and
`params_extract` MCP tools repeatedly, and 27-08 additionally names `scanner_issues` and five
Montoya accessors — all of which the seal-time detector re-reads. Every one of them is the same
already-running host API or the already-exposed MCP tool surface: 27-07 adds a type-keyed predicate
and a shared sanitizer in front of existing producers, 27-08 adds a source-scanning test and narrows
a comment, and 27-09 writes records only. **Nothing is integrated, wrapped, connected or consumed
for the first time by any of the three, so the declaration above stands unchanged and no matrix is
fabricated to fill the gap.**
