# Phase 20: MCP Access-Control Correctness - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-08-05
**Phase:** 20-mcp-access-control-correctness
**Areas offered:** Local-mode auth, Route exemptions & takeover, Gate mechanism, Blocked-request observability
**Areas discussed:** Route exemptions & takeover, Blocked-request observability

---

## Area selection

| Option | Description | Selected |
|--------|-------------|----------|
| Local-mode auth | Should local mode also require the bearer token, not just Origin/Host/Referer/UA heuristics? Would break existing Claude Desktop / Codex CLI configs. | |
| Route exemptions & takeover | `probeExistingServer` hits `/__mcp/health` unauthenticated; a blanket external-mode gate breaks BindException takeover. | ✓ |
| Gate mechanism | Hand-rolled interceptor moved earlier vs `ApplicationPlugin` with `onCall` vs Ktor `Authentication` plugin. | |
| Blocked-request observability | Where blocks are logged, and what the response reveals. | ✓ |

**Notes:** The two unselected areas were handed to Claude's discretion with written recommendations
in CONTEXT.md, flagged as open for phase research rather than settled.

---

## Route exemptions & takeover

### Q1 — In external mode, which routes are exempt from the auth gate?

| Option | Description | Selected |
|--------|-------------|----------|
| Health only, without fingerprint | `/__mcp/health` exempt, returns 200 "ok", but `X-Burp-AI-Agent: mcp` emitted only in local mode. Takeover probe keeps working without the token on the wire. | ✓ |
| Health only, as today | Exempt including the identifying header in both modes. Simpler, but leaves an unauthenticated fingerprint in external mode. | |
| No exemptions | Everything authenticated. Stricter, but forces the takeover probe to send the token to an unverified listener — worsens Finding 7. | |

**Notes:** The tension was surfaced before asking — authenticating the probe would have made
Finding 7 (SEC-07, Phase 25) worse by putting the token on the wire earlier against a listener whose
identity is not yet established. The chosen option keeps the token out of the probe entirely.

### Q2 — In LOCAL mode, do the Origin/Host/Referer/UA heuristics apply to `/__mcp/health`?

| Option | Description | Selected |
|--------|-------------|----------|
| Yes, apply them | Closes fingerprinting from a malicious page (today `Origin: http://evil.example` gets 200 on health). Probe is server-side, sends no Origin, so it does not break. | ✓ |
| No, health always free in local | Pure liveness with no checks on loopback. Simpler, but leaves the fingerprint reachable by any web page. | |

### Q3 — `/__mcp/shutdown` already validates the token in its own handler. Double-check in external mode?

| Option | Description | Selected |
|--------|-------------|----------|
| Keep both | Defence in depth. The in-handler check is what saved this endpoint from the bypass. | ✓ |
| Remove the in-handler check | Rely on the corrected gate alone. Less duplication, single point of failure on the most destructive endpoint. | |
| Keep both + require token in local | Also document local-mode token requirement as an explicit invariant (already true de facto). | |

**Notes:** The rationale offered and accepted — removing the in-handler check would mean relying
entirely on the mechanism that just failed.

### Q4 — Boundary with Phase 25 (SEC-07 takeover rework)

| Option | Description | Selected |
|--------|-------------|----------|
| 20 preserves, 25 reworks | Phase 20 only guarantees the probe keeps working; all listener-identity and token decisions belong to Phase 25. Separate, auditable security diffs. | ✓ |
| Pull the token fix forward into 20 | Closes Finding 7 sooner but mixes two security changes in one diff. | |
| 20 disables automatic takeover | Removes the problem rather than mitigating it, at the cost of a usability regression. | |

---

## Blocked-request observability

### Q1 — Where is a blocked request logged?

| Option | Description | Selected |
|--------|-------------|----------|
| Audit + Output tab | AuditLogger event reusing the `MCP_TOOL_EVENT_BLOCKED` shape, plus an Output-tab line so it is visible with audit off (the default). | ✓ |
| Only Output tab, as today | No change. An access-control block never reaches the auditable record even when audit is on. | |
| Only AuditLogger | Clean Output tab, but with audit disabled by default a block would go entirely unnoticed. | |

**Notes:** Presented with the constraint that audit logging is opt-in/disabled by default in this
project, which is what makes the dual destination necessary rather than redundant.

### Q2 — How to treat the reflected `Origin` / `Host` / `Referer` value?

| Option | Description | Selected |
|--------|-------------|----------|
| Sanitize and truncate | Strip CR/LF and control characters (prevents log-line injection and ANSI escapes in the Output tab), cap length. Keeps diagnostic value without letting the sender write into the log. | ✓ |
| Do not reflect the value | Log only the block and its reason. Removes the problem entirely but makes a legitimate misconfigured client hard to diagnose. | |
| Leave it raw | Keep the current interpolation; assumes Burp's Output tab is a trusted sink. | |

### Q3 — What does the server return to a blocked client?

| Option | Description | Selected |
|--------|-------------|----------|
| Bare status, no body | 401 external / 403 local, no reason string. Diagnosis lives in the local log. Gives a prober nothing. | ✓ |
| Status + reason in body | Much easier to debug a misconfigured MCP client, at the cost of telling an attacker which check fired. | |
| 401 with `WWW-Authenticate` | HTTP-correct and helps generic clients, but confirms to an unauthenticated caller that a bearer is expected. | |

### Q4 — Rate-limit the block logging?

| Option | Description | Selected |
|--------|-------------|----------|
| Yes, aggregate and limit | Log the first block per reason immediately, then aggregate. Existing precedent: `maybeLogBackoff` and `availabilityLogged`. | ✓ |
| No, one line per block | Simpler, loses nothing, but a request loop floods the Output tab. | |
| You decide | Leave to the planner; aggregate only if cheap. | |

---

## Claude's Discretion

- **Gate mechanism** — recommendation: `ApplicationPlugin` with an `onCall` hook, because it is
  order-independent by construction and therefore cannot regress through a future re-ordering of the
  `embeddedServer` block. Research must confirm composition with the CORS plugin and the MCP SDK's
  `mcp{}`.
- **Local-mode authentication** — recommendation: not in this phase; it is a behaviour change beyond
  "make the existing controls run" and would break clients that send no token. Raise it if research
  shows otherwise.

## Deferred Ideas

- Local-mode bearer token (see above).
- Takeover identity / token disclosure — Phase 25, SEC-07.
- `openConnection` loopback trust-all — Phase 25 SC5.
- A test enforcing that mutation MCP tools are marked `unsafeOnly` — flagged in
  `.planning/codebase/CONCERNS.md`; different layer, candidate for Phase 26.
