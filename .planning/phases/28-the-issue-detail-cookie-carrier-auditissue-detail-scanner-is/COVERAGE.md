# API Coverage — Phase 28

No external API integration: this phase changes two in-process write-time cookie gates
(`ScannerIssueSupport.sanitizeInjectionPointValue` and `AiScanCheck.buildDetail` through
`sanitizeCookiePointText`), one type predicate (`InjectionPointExtractor.kt`), their tests, and the
records that describe them. The only "API" named anywhere in the phase scope is the Burp Montoya
host API this extension already runs inside, and the MCP tool surface it has exposed since phase 26.
Nothing is integrated, wrapped, connected or consumed for the first time.

This declaration is recorded because the seal-time gate re-runs the deterministic detector over the
PLAN bodies, where it fires on a single signal — `{"verb": "wrap", "noun": "mcp"}` — the same
false-positive class closed with a reasoned declaration on Phase 23 (`wraps api`, `(surface) api`)
and on Phase 27 (rounds 3, 4 and 5). No matrix is fabricated to fill a gap that does not exist.

## What the seal-time detector will actually re-read from the nine plan bodies

Measured 2026-08-28 by `grep -oh <token> 28-NN-PLAN.md | wc -l` over each file, so this declaration
is checkable rather than assertive. Phase 27's round-4 extension records a case where the
instruction requesting the inventory described a different token set than the files on disk carried;
re-measuring is cheaper than repeating that.

| Symbol / token | 01 | 02 | 03 | 04 | 05 | 06 | 07 | 08 | 09 | What it is |
|---|---|---|---|---|---|---|---|---|---|---|
| `scanner_issues` | 3 | 1 | 3 | 1 | 3 | 1 | 3 | 2 | 1 | **An MCP tool NAME — the token class that made this declaration necessary, present in all nine bodies.** Every occurrence names the tool whose *already-shipped* output carries the residual being measured. The phase adds no tool, changes no tool schema, and registers no new tool. |
| `AuditIssue.detail` | 5 | 2 | 1 | 2 | 2 | 2 | 3 | 3 | 0 | The already-shipped Montoya issue accessor. It is the phase's *subject* — the immutable string the write-time gates bake their result into. A source read, not a connection. |
| `Montoya` | 2 | 7 | 1 | 0 | 10 | 0 | 1 | 0 | 0 | The Burp host API this extension already runs inside. 28-05's ten occurrences are the `javap` measurement of `AuditInsertionPoint.type()` against the resolved `montoya-api-2026.2.jar` — reading a shipped jar's bytecode, which is the opposite of integrating something new. |
| `redactIfNeeded` | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 5 | The already-shipped redaction choke point (`McpToolContext.kt:59`, in the tree since phase 26). All five occurrences are in 28-09, whose entire job was to *admit in the record* that this pre-existing call exists — it was previously denied. Naming a call the repository already makes is not integrating it. |
| `API` | 0 | 0 | 0 | 0 | 0 | 0 | 2 | 0 | 0 | Both occurrences are in `28-07-PLAN.md`, both in the phrase "Burp API" describing the host the extension runs inside. |
| `HttpRequestResponse` | 1 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | The already-exposed MCP result carrier, named once as a type. |
| `request_parse` | 0 | 0 | 1 | 0 | 0 | 0 | 0 | 0 | 0 | One occurrence, in 28-03, referring to the already-exposed tool. |
| `response_parse` / `params_extract` / `proxy_http_history` / `site_map` | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | **Zero occurrences across all nine files.** |

## Where this measurement diverges from Phase 27's, said rather than reconciled silently

Phase 27's round-4 extension recorded **zero** MCP tool names across its plan bodies, and its round-5
extension recorded two. **Phase 28 has `scanner_issues` in all nine bodies, eighteen occurrences
total** — strictly more tool-name tokens for the detector to trip on than either phase-27 round. This
declaration says so instead of reusing phase 27's "strictly less to trip on" sentence, which would be
false here.

`Montoya` also runs higher (21 occurrences vs. phase 27 round 5's zero), concentrated in 28-02 and
28-05 where the phase measured the host API's own behaviour with `javap`.

**The declaration stands anyway, and the reason is about what the plans DO, not how few tokens they
carry.** What the nine plans actually did:

- **28-01, 28-04, 28-05** — added type-keyed sanitization at two existing write sites and their tests.
- **28-02** — resolved one predicate (`InjectionPointExtractor.kt:29`) and proved the route it feeds.
- **28-03, 28-06** — measured an evidence tail and corrected records.
- **28-07** — comment lines and one operator-facing string constant; zero non-comment changed lines
  in `AiScanCheck.kt`.
- **28-08** — records and one conditional verification override; no source behaviour.
- **28-09** — retired a false claim from four record sites; comment and record lines only.

Every symbol in the table above is either a Burp Montoya host API type this extension already runs
inside, an MCP tool name used to identify *whose already-shipped output* carries the residual under
measurement, or a redaction call site that has been in the tree since phase 26. **Nothing is
integrated, wrapped, connected or consumed for the first time by any of the nine, so no matrix is
fabricated.**

## Coverage rows

None. A capability matrix enumerates the surface of an API being integrated. Phase 28 integrates no
API, so there is no surface to enumerate and every row would be invented. Per the capability
registry's own instruction for this case, the reasoned declaration above is recorded in place of a
matrix.
