# Phase 20: MCP Access-Control Correctness - Context

**Gathered:** 2026-08-05
**Status:** Ready for planning

<domain>
## Phase Boundary

Every MCP request passes through the extension's access-control checks **before any handler runs**,
in both local and external mode — and there are tests that would have caught the current bypass.

The defect: in `mcp/KtorMcpServerManager.kt` the security `intercept(ApplicationCallPipeline.Call)`
(line 176) is registered *after* `routing{}` (line 154). Ktor runs same-phase interceptors in
registration order and `RoutingRoot.interceptor` never calls `finish()`, so every route Ktor resolves
is served before the checks execute. The MCP SDK's `mcp{}` (line 220) calls `install(SSE)` then
`routing{}` on the same already-installed `RoutingRoot`, so its routes inherit the same position.

Reproduced against a live server — external mode, TLS on, token set, **no** `Authorization` header:

```
GET  /__mcp/health   -> 200 "ok"
POST /message        -> 400 "sessionId query parameter is not provided"   <-- handler ran; expected 401
GET  /unmatched      -> 401
```

**Why the existing tests are green:** `KtorMcpServerManagerSecurityTest` invokes `isAuthorized`,
`constantTimeEquals` and `parseExternalCorsHosts` by reflection, in isolation. The predicates are
correct. They are simply never called. `McpServerIntegrationTest` only exercises `/__mcp/shutdown`,
which validates the token *inside its own handler* and therefore survived the bypass. This phase's
tests must exercise the **pipeline**, not the predicates.

**In scope:** interceptor placement; local-mode Origin/Host/Referer/UA enforcement; external-mode
bearer enforcement; security headers on resolved routes; server version string; IPv6 host parsing;
blank-token rejection; blocked-request logging.

**Out of scope:** the `McpStdioBridge` — it shares `registerTools` but has no HTTP layer, so
Origin/auth do not apply. The takeover token-disclosure fix (Finding 7) belongs to Phase 25.

</domain>

<decisions>
## Implementation Decisions

### Route exemptions
- **D-01:** `/__mcp/health` stays **exempt from bearer auth in external mode** so
  `McpSupervisor.probeExistingServer` keeps working without putting the token on the wire during a
  probe. It responds `200 "ok"`.
- **D-02:** The identifying header `X-Burp-AI-Agent: mcp` is emitted **only in local mode**. In
  external mode an unauthenticated scan must not be able to confirm a Burp AI Agent is behind the
  port. This is the anti-fingerprinting half of D-01 — the exemption is for liveness, not identity.
- **D-03:** In **local** mode the Origin/Host/Referer/browser-UA heuristics **do apply to
  `/__mcp/health`**. Today `Origin: http://evil.example` gets `200` there, which lets any page the
  user visits fingerprint the extension. The takeover probe is server-side and sends no `Origin`, so
  applying the checks does not break it.
- **D-04:** `/__mcp/shutdown` **keeps its in-handler token check** in addition to the corrected gate.
  Defence in depth — that check is literally what saved this endpoint from the bypass, so removing it
  now would mean relying entirely on the mechanism that just failed. Double-validation in external
  mode is accepted.

### Boundary with Phase 25
- **D-05:** Phase 20 **preserves** takeover, Phase 25 **reworks** it. This phase does no more than
  guarantee the probe keeps functioning (via D-01). Every decision about establishing listener
  identity and about whether the token may be presented at all belongs to Phase 25 (SEC-07). Keeps
  the two security diffs separate and independently auditable.

### Blocked-request observability
- **D-06:** A blocked request emits **both** an `AuditLogger` event and an Output-tab line. The audit
  event reuses the established `MCP_TOOL_EVENT_BLOCKED` payload shape from
  `mcp/tools/McpTool.kt:135-158` (`reason` field: `origin_mismatch`, `host_mismatch`,
  `referer_mismatch`, `browser_no_origin`, `unauthorized`). Both destinations are required because
  audit logging is **disabled by default** in this project — an audit-only event would be invisible
  to most users.
- **D-06 AMENDED by ADR-13 (see repo-root `DECISIONS.md`):** per-occurrence audit emission is retained for the four local-mode reasons, but coalesced into one event per 60s per-reason window carrying a `suppressed` count for `unauthorized` and `blank_token`, which an unauthenticated external peer can trigger at will.
- **D-07:** Reflected header values (`Origin`, `Host`, `Referer`, `User-Agent`) are **sanitized and
  truncated** before reaching either destination: strip CR/LF and control characters (prevents log-line
  injection and ANSI escapes in Burp's Output tab), then cap at a fixed length. The current code
  interpolates the raw attacker-controlled value.
- **D-08:** The response to a blocked client is a **bare status with no body** — `401` in external
  mode without a valid token, `403` in local mode for an Origin/Host/Referer/UA violation. No reason
  string, no `WWW-Authenticate`. Diagnosis lives in the local log, not in the response. This matches
  what the code already does; it is now a deliberate decision rather than an accident.
- **D-09:** Blocked-request logging is **rate-limited by aggregation**: log the first block of each
  reason immediately, then aggregate ("N further blocks for origin_mismatch in the last 60s"). Reuse
  the existing project pattern — `maybeLogBackoff` in `scanner/PassiveAiScannerAnalysis.kt` and the
  `availabilityLogged` `AtomicBoolean` in `backends/cli/CliBackend.kt:28`. Prevents a request loop
  from flooding the Output tab and burying everything else.

### Post-research decisions (2026-08-06)

Added after `20-RESEARCH.md` surfaced them as open questions requiring maintainer input. Locked.

- **D-10:** Audit payload header values (`Origin`, `Host`, `Referer`, `User-Agent`) are **hashed by default,
  plaintext only under verbose**. This honours the CLAUDE.md constraint *"hashes only unless verbose is on"*. The
  Output-tab line still carries the sanitized, truncated value per D-07 so immediate diagnosis does not require
  turning audit on — the split is: transient local log gets the value, persistent auditable record gets the hash.
- **D-11:** `isValidOrigin` and `isValidReferer` are fixed **alongside** `isValidHost`. Research verified all three
  share the identical IPv6 bracket defect (`URI.toURL().host` returns `[::1]` *with* brackets, so the equality
  check fails). SEC-05's text names only `isValidHost`; this widens it deliberately. Use one shared helper for all
  three rather than three parallel fixes.
- **D-12:** `docs/mcp-hardening.md` is corrected **in this phase**, not deferred to Phase 26 / DOC-03. §"External
  Access" item 4 and §"Verification" item 2 both promise that every request is bearer-validated, which D-01 makes
  deliberately false for `/__mcp/health`. Leaving the user-facing runbook wrong for six phases is worse than the
  small overlap with DOC-03. The security *advisory* (SEC-04 / PRIV-05 impact notice) remains Phase 26.

### Claude's Discretion

Two gray areas were deliberately left to the implementer. Both carry a recommendation to be confirmed
by phase research — the planner should treat these as open, not settled:

- **Gate mechanism.** Hand-rolled interceptor moved to an earlier phase
  (`ApplicationCallPipeline.Plugins`), an `ApplicationPlugin` with an `onCall` hook, or Ktor's
  `Authentication` plugin (adds a `ktor-server-auth` dependency).
  *Recommendation:* an `ApplicationPlugin` with `onCall`. It is order-independent by construction, so
  the class of bug being fixed cannot recur through a future re-ordering of the `embeddedServer`
  block — which a simple "move the `intercept` call above `routing{}`" fix would leave possible.
  Research must confirm it composes correctly with the CORS plugin and with the MCP SDK's `mcp{}`.
- **Local-mode authentication.** Whether local (loopback) mode should also require the bearer token,
  not just the Origin/Host heuristics.
  *Recommendation:* do **not** add it in this phase. It would break existing Claude Desktop / Codex
  CLI configurations that do not send a token, which is a behaviour change beyond "make the existing
  controls run". Note the precedent that cuts the other way: `/__mcp/shutdown` already requires a
  token in local mode and nothing broke — because only the supervisor calls it. If research finds
  local-mode tokens are cheap and non-breaking for real MCP clients, raise it rather than assuming.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### The defect and its evidence
- `.planning/notes/2026-08-05-code-review.md` §"F1" — full reproduction, the decompilation findings
  for `RoutingRoot.interceptor` (no `finish()`) and `Application.mcp` (`install(SSE)` + `routing{}` on
  the same `RoutingRoot`), and the observed status codes in both modes. **Read this first.**
- `.planning/notes/2026-08-05-code-review.md` §"F11-F19" — the SEC-05 sub-items (hardcoded `0.6.0`,
  `isValidHost` IPv6, blank-token) with file:line.

### Requirements and success criteria
- `.planning/REQUIREMENTS.md` §"Access Control & Server Security (SEC)" — SEC-04, SEC-05.
- `.planning/ROADMAP.md` §"Phase 20" — the six success criteria. **SC4 is the acceptance gate: the new
  regression tests MUST fail against the pre-fix `KtorMcpServerManager`.** A test that passes both
  before and after has not tested the bypass.

### User-facing contract this phase must make true
- `docs/mcp-hardening.md` §"External Access" item 4 — *"Validate `Authorization: Bearer <token>` is
  sent on every request."* The doc already promises exactly what does not happen. §"Verification"
  item 2 likewise. Check whether the runbook needs updating once D-01/D-02 land (health is
  deliberately exempt).

### Prior locked decisions that constrain this phase
- `.planning/phases/16-external-mcp-client/16-CONTEXT.md` §decisions — external MCP tokens encrypted
  via `SecretCipher`; `ext:<server>:<tool>` namespacing; external SSE deliberately NOT routed through
  `MontoyaHttpTransport`. Do not disturb.
- `.planning/codebase/CONCERNS.md` §"MCP bearer token in preferences", §"MCP unsafe-tool gate" —
  documented residual risks; the unsafe-tool gate is a separate layer from transport auth and stays
  as-is.
- `.planning/codebase/ARCHITECTURE.md` §"MCP Tool Request Flow" — describes the intended flow as
  *"`KtorMcpServerManager` validates token; routes to dispatch"*. The architecture doc describes the
  intent; this phase makes the code match it.

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `mcp/tools/McpTool.kt:135-158` (`runTool`) — the established "blocked" telemetry shape:
  `emitToolTelemetry(MCP_TOOL_EVENT_BLOCKED, base + mapOf("reason" to …))`. D-06 mirrors this at the
  transport layer.
- `mcp/tools/McpTool.kt:229-242` `sanitizeErrorMessage` — existing precedent for scrubbing values before they
  surface (path/package redaction, whitespace collapse, length cap). Same discipline applies to D-07.
  *(Corrected 2026-08-06: this was misattributed to `McpToolExecutorImpl.kt` when this CONTEXT was written —
  that file has zero references to it. Verified by grep.)*
- `audit/Hashing.kt` `Hashing.sha256Hex(value: String)` — the hashing helper for D-10.
- `scanner/PassiveAiScannerAnalysis.kt` `maybeLogBackoff` and `backends/cli/CliBackend.kt:28`
  `availabilityLogged` — two existing rate-limited-logging patterns for D-09. Reuse, do not invent.
- `KtorMcpServerManager.isAuthorized` / `constantTimeEquals` — correct as written
  (`MessageDigest.isEqual`). They need to be *called*, not rewritten.
- `McpTls.resolve` — already fails closed when no keystore is configured, so external mode cannot
  start without TLS material. That invariant must survive.

### Established Patterns
- `McpServerManager` is an interface (`mcp/McpServerManager.kt`) with `KtorMcpServerManager` as the
  only implementation — tests can substitute a fake, but the pipeline bug is only observable against
  the real Netty server. SC4 therefore needs an **integration** test that binds a port, in the shape
  of the existing `McpServerIntegrationTest`.
- `McpSupervisor.applySettings` restarts the server whenever settings or privacy mode change, so
  the `McpToolContext` snapshot is never stale. Unchanged by this phase.
- Blocked/denied paths in this codebase fail **closed** and return a typed result rather than
  throwing. Keep that.

### Integration Points
- `mcp/KtorMcpServerManager.kt:129-225` — the `embeddedServer` module lambda: CORS install, `routing`
  block, security `intercept`, `mcp{}`, `registerTools`. This is the whole surface of the change.
- `mcp/McpSupervisor.kt:254-272` (`probeExistingServer`) — consumes `/__mcp/health` **and** the
  `X-Burp-AI-Agent: mcp` header. D-02 makes that header local-only, so the probe's header assertion
  must be reconciled: in external mode it can no longer rely on it. Phase 25 owns the redesign, but
  Phase 20 must not silently break the local-mode probe.
- `mcp/McpStdioBridge.kt:58` — also calls `registerTools`. No HTTP layer; explicitly out of scope.
- `src/test/kotlin/.../mcp/KtorMcpServerManagerSecurityTest.kt` — keep the predicate tests, but they
  are not sufficient. Their existence while the bypass was live is the argument for SC4.

</code_context>

<specifics>
## Specific Ideas

- The user's framing throughout: the fix must be structural, not a reordering that a future edit can
  silently undo. This drove the `ApplicationPlugin` recommendation over "move the line up".
- The health exemption was chosen specifically because the alternative (authenticating the probe)
  would have made Finding 7 worse by putting the token on the wire against an unverified listener.
  That interaction is the reason for D-01, and it should be preserved if the planner revisits it.
- Defence-in-depth was chosen for `/__mcp/shutdown` (D-04) with an explicit rationale: the in-handler
  check is the one control that survived the bypass.

</specifics>

<deferred>
## Deferred Ideas

- **Local-mode bearer token** — recommended against for this phase (see Claude's Discretion); revisit
  if research shows real MCP clients tolerate it. Would be its own change, not a correctness fix.
- **Takeover identity / token disclosure (Finding 7)** — Phase 25, SEC-07. D-05 is the explicit
  boundary.
- **`openConnection` loopback trust-all** — Phase 25 SC5.
- **A test that enforces mutation tools are marked `unsafeOnly`** — noted in
  `.planning/codebase/CONCERNS.md` as an untested invariant. Different layer (tool gating, not
  transport auth); belongs with the Phase 26 coverage work if picked up at all.

</deferred>

---

*Phase: 20-mcp-access-control-correctness*
*Context gathered: 2026-08-05*
