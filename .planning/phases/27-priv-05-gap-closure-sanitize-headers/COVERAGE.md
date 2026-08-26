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

**Extended 2026-08-26 (plan 27-13) to cover plans 27-10, 27-11, 27-12 and 27-13, on the same
reasoning — and with the symbol inventory MEASURED rather than projected, because plan 27-13's own
instruction for writing this extension turned out to describe a different set of plan bodies than
the ones on disk.**

**WHAT THE SEAL-TIME DETECTOR WILL ACTUALLY RE-READ FROM THESE FOUR PLAN BODIES.** Measured
2026-08-26 by `grep -ohc` over each file, so this extension is checkable rather than assertive:

| Symbol / token | 27-10 | 27-11 | 27-12 | 27-13 | What it is |
|---|---|---|---|---|---|
| `HttpRequestResponse` | 0 | **6** | 0 | 0 | The MCP tool-result carrier plan 27-11 drives its probe through. An ALREADY-EXPOSED MCP surface and an already-running Montoya type. |
| `toolJson.encodeToString` | 0 | 0 | **3** | 0 | The already-shipped serializer at the emission choke point. Not a new integration; the call has been in the tree since phase 26. |
| `ParsedRequest` | — | **2** (total across the set) | — | — | The already-exposed `request_parse` / `response_parse` result shape. |
| `SiteMapEntry` / `McpToolContext.redactIfNeeded` / `AuditIssue.detail()` | — | — | — | 1 each (total across the set) | Already-shipped emission shapes and the already-shipped redaction choke point. |
| `Montoya` | 0 | 0 | 0 | **1** | The single occurrence in the whole round-4 plan set — and it is inside the sentence of `27-13-PLAN.md` that instructs this extension to be written. |
| `API` | 0 | 0 | 0 | **3** (on 2 lines) | Both lines are in `27-13-PLAN.md`: one referencing this file by name, one being the "Montoya host API" phrase of that same instruction. |
| MCP tool NAMES (`request_parse`, `response_parse`, `params_extract`, `scanner_issues`, `proxy_http_history`, `proxy_http_history_regex`, `site_map`, `site_map_regex`) | 0 | 0 | 0 | 0 | **ZERO occurrences in all four files.** |

**THE MEASUREMENT DISAGREES WITH THE INSTRUCTION THAT ASKED FOR IT, AND THE MEASUREMENT IS WHAT IS
RECORDED.** `27-13-PLAN.md` part B states that "these four plan bodies name the passive-scan prompt
path, the MCP tool result shapes and the Montoya host API **repeatedly**". The MCP tool result SHAPES
are indeed named — `HttpRequestResponse` six times in 27-11, `toolJson.encodeToString` three times in
27-12, plus `ParsedRequest`, `SiteMapEntry`, `McpToolContext.redactIfNeeded` and `AuditIssue.detail()`
once or twice each. **But the MCP tool NAMES — the exact tokens that made this declaration necessary
for plans 27-07 and 27-08 — appear ZERO times in any of the four**, and `Montoya` and `API` appear
ONLY inside `27-13-PLAN.md`, in the instruction sentence itself. Writing "repeatedly" here would have
been filing a projection as a measurement, which is precisely the defect `WINDOWS.md` entries 26 and
29 already record for this phase. The divergence is filed as its own ledger entry.

**THE DECLARATION STANDS, AND ON THIS EVIDENCE IT STANDS MORE EASILY THAN IT DID IN 2026-08-25.** The
detector has strictly LESS to trip on across these four plan bodies than across 27-07 to 27-09. Every
symbol that IS present is either an already-running Burp Montoya host API type or an already-exposed
MCP result shape that this repository has emitted since phase 26. What the four plans actually do:
**27-10** widens one `const val` character class by a single token and adds a character-axis test;
**27-11** adds a second fixed-width lookbehind to an existing private regex composer;
**27-12** deletes two test assertions, adds one `PrivacyMode.OFF` byte-identity test, and adds a
repository-state source-scanning test; **27-13** writes records only and touches no code at all.
**Nothing is integrated, wrapped, connected or consumed for the first time by any of the four, so no
matrix is fabricated to fill a gap.**
