# Roadmap: Burp AI Agent

## Shipped (Historical)

### v0.7.0 — Release Cut + Stabilization (shipped 2026-05-15)

Phases 1–8 closed. Features: Perplexity backend, AI scan on selected insertion point, custom prompt library UX, bug fixes #62/#66/#67/#68, proxy transport + MCP scope hardening (#69), BApp Store resubmission (#231).

### v0.8.0 — UI/UX Overhaul (shipped 2026-06-02)

Phases 9–11 closed. Features: design system foundation (UI-01), MCP tools tab redesign (UI-03/04/05/07), all settings tabs rebuilt on design system with light/dark theme (UI-02/06/07/08).

### v0.9.0 — Hardening, Quality & New Capabilities (shipped 2026-06-26)

Phases 12–19 closed, 22/22 requirements. Secrets encrypted at rest (AES-256-GCM + schema-v4 migration), redaction hardening (real HKDF, body-level patterns), pre-send secret tripwire, native Anthropic backend, token budgets, external MCP client (#41), reliability/EDT fixes (#71), detekt + blocking ktlint, mega-file split. Point releases 0.9.1 / 0.9.2 cut from this base.
→ Archive: [`milestones/v0.9.0-ROADMAP.md`](milestones/v0.9.0-ROADMAP.md) · [`milestones/v0.9.0-REQUIREMENTS.md`](milestones/v0.9.0-REQUIREMENTS.md)

---

## Active Milestone: v0.10.0 — Security Correctness & Agent Trust

**Status:** Planning — started 2026-08-05

**Goal:** Make the security and privacy controls the project already ships actually take effect on every path, and give the agent loop a trust boundary. v0.9.0 built the machinery; a deep review of v0.9.2 on 2026-08-05 found that several controls sit in source but never execute, or match patterns that real-world data does not have.

**Why this milestone, framed honestly:** two of the seventeen findings were confirmed by running the shipped code, not by reading it.

- The MCP server's auth / Origin / security-header interceptor is registered *after* `routing{}` in Ktor's `Call` phase. Ktor runs same-phase interceptors in registration order and `RoutingRoot.interceptor` never calls `finish()`, so any route that resolves is served **before** the checks run. In external mode an unauthenticated `POST /message` returns `400 "sessionId query parameter is not provided"` — the MCP handler ran — instead of `401`.
- The passive scanner extracts cookies into a dedicated prompt section as bare `name=value`, dropping the `Cookie:` prefix that `cookieHeaderRegex` keys on. `JSESSIONID`, `PHPSESSID`, `connect.sid`, `auth_token` and `csrftoken` therefore reach the AI backend unredacted in both STRICT and BALANCED — directly against the project's stated core value.

Everything else in this milestone is real but was found by reading: an agent loop that executes model-emitted tool calls with no user gate, EDT-blocking tool execution, three unguarded recurring schedulers, and a set of smaller hardening items.

**Ordering rationale:**

- Phases 20 and 21 lead — they are live defects in a published release. They touch disjoint files (`mcp/`, `redact/` + `scanner/`) and could run in parallel, but 20 goes first because it is the higher-severity of the two.
- Phase 22 (tool-call confirmation) and Phase 23 (EDT) both rewrite `ChatPanel.maybeExecuteToolCall`. Sequential, never parallel — same merge-friction reasoning that sequenced Phases 12 and 13 last milestone. 22 first: the confirmation gate changes the call's shape, and moving it off the EDT is cleaner once that shape is settled.
- Phases 24 and 25 are independent of everything above and of each other.
- Phase 26 lands last so its coverage work can cover the code the earlier phases produce, and so the detekt baseline is trimmed once, at the end.

## Phases

- [x] **Phase 20: MCP Access-Control Correctness** — Move the auth/Origin/header interceptor ahead of routing so it runs on every request; regression tests that fail against today's code (completed 2026-08-10 — 10 plans: 6 original + 4 gap closure. First verification `gaps_found` 5/6; re-verified 6/6 after gap closure. SEC-04 + SEC-05 satisfied. 2 human-UAT items open in `20-HUMAN-UAT.md`)
- [x] **Phase 21: Redaction Completeness** — Close the cookie-section leak, match real-world sensitive key names, stop redaction failing open on large bodies (19 plans: 7 original + 12 gap-closure across two review-driven rounds; `21-REVIEW.md` found 4 blockers incl. a reproduced live PRIV-05 cookie leak, `21-REVIEW-2.md` found 1 more; all closed and re-verified. 669 tests, 0 failures; detekt baseline unchanged. Verified 6/6 must-haves; 3 live-Burp items open in `21-HUMAN-UAT.md`) (completed 2026-08-13)
- [x] **Phase 22: Agent Tool-Call Trust Boundary** — User decision gate for model-emitted tool calls, plus the ADR that records the threat model (completed 2026-08-14)
- [x] **Phase 23: EDT Confinement & UI Responsiveness** — Tool execution, backend HTTP and MCP stop() off the Swing EDT (completed 2026-08-21)
- [x] **Phase 24: Scheduler & Process Robustness** — Guard recurring tasks against death-by-exception; fix the CLI output race and unbounded resource use (completed 2026-08-22)
- [x] **Phase 25: Secondary Hardening** — Stop leaking the MCP token to unverified port holders; teach SsrfGuard the alternate IP notations (completed 2026-08-22)
- [ ] **Phase 26: Coverage, Static-Analysis Debt & Docs** — Allowlist shell escaping, raise coverage on security paths, shrink the detekt baseline, publish the advisory
- [x] **Phase 27: PRIV-05 Gap Closure — sanitizeHeaders Cookie Parity** — Close gap: PRIV-05 — mirror the cookie name-variant fix into `sanitizeHeaders`, so the MCP tool path strips `X-Cookie` / `Cookie2` / `Set-Cookie2` / `X-Original-Cookie` / `X-Forwarded-Cookie` the way the prompt path already does (completed 2026-08-27)
- [ ] **Phase 28: The Issue-Detail Cookie Carrier — `AuditIssue.detail()` → `scanner_issues`** — Close `AR-27-08`: a COOKIE-typed injection point's value reaches the `scanner_issues` tool result through `AuditIssue.detail()` and survives STRICT and BALANCED (measured by plan 27-08 with a firing positive control; medium, latent behind three preconditions). Closes `InjectionPointExtractor.kt:29` in the same phase — the predicate is only meaningful as part of the route it feeds. Opened 2026-08-25 by plan 27-09 because phase 27 completes with PRIV-05 NOT satisfied and a deferral without an owner is round four, pre-arranged

## Phase Details

### Phase 20: MCP Access-Control Correctness

**Goal**: Every MCP request passes through the extension's access-control checks before any handler runs, in both local and external mode — and there are tests that would have caught the current bypass.
**Depends on**: Nothing (highest severity, leads the milestone)
**Requirements**: SEC-04, SEC-05
**Success Criteria** (what must be TRUE):

1. With `externalEnabled = true`, a request to `POST /message` with no `Authorization` header returns 401. Same for `GET /sse`. (Today both reach the MCP SDK handler and return 400 / 200 respectively.)
2. With `externalEnabled = false`, a request with a foreign `Host`, a foreign `Referer`, or a browser `User-Agent` and no `Origin` returns 403 — on `/__mcp/health`, on `/message`, and on `/sse`. A foreign `Origin` also returns 403, but see the SC4 note: Ktor's CORS plugin already produces that today.
3. `X-Frame-Options: DENY`, `X-Content-Type-Options: nosniff`, `Referrer-Policy: same-origin` and `Content-Security-Policy: default-src 'none'` are present on responses from routes that Ktor resolves — not only on 404s — and deterministically over both HTTP/1.1 and HTTP/2.
4. The regression tests added for SC1–SC3 **fail** when run against the pre-fix `KtorMcpServerManager` and pass after. This is the acceptance gate for the phase: a test that passes both before and after has not tested the bypass. The nine assertions that actually go red pre-fix are enumerated in `20-RESEARCH.md` §"Pre-Fix Behaviour Baseline" — build the gate from those. Notably **not** valid gates (they pass today): foreign `Origin` → 403, `GET /nope` → 401 in external mode, and the `/__mcp/shutdown` 401/200 pair.
5. The server advertises the real project version, not `0.6.0`; `isValidHost` accepts `[::1]:<port>` when the server is bound to `::1`; an external-mode request cannot authenticate with a blank token.
6. `/__mcp/shutdown`'s existing in-handler token check still returns 401 without a token and 200 with one — the fix must not regress the one path that was already correct.

**Plans**: 10 plans — 6 original in 4 waves, plus 4 gap-closure plans (20-07…20-10) in 1 parallel wave

Plans:

- [x] 20-01-PLAN.md — Gate decision core (pure `evaluate` + IPv6/blank-token predicates) and the blocked-request reporter (wave 1)
- [x] 20-02-PLAN.md — `BuildFlags.VERSION` Gradle seam and the shared real-Netty/OkHttp test support (wave 1)
- [x] 20-03-PLAN.md — `McpAccessControlPlugin` + `KtorMcpServerManager` rewiring: the SEC-04 fix (wave 2)
- [x] 20-04-PLAN.md — Local and external pipeline regression tests (wave 3)
- [x] 20-05-PLAN.md — `McpSupervisor` probe mode-mirror and the `docs/mcp-hardening.md` correction (wave 3)
- [x] 20-06-PLAN.md — SC4 red-before-green acceptance gate (wave 4, runs alone — it transiently rolls back `KtorMcpServerManager.kt`, which would corrupt any concurrent plan's test run)

Gap closure (`20-VERIFICATION.md` status `gaps_found`, score 5/6 — SEC-05 satisfied, SEC-04's foreign-`Host`
limb open over HTTP/2; plus `20-REVIEW.md` CR-01/WR-01/WR-02/WR-08). All four are file-disjoint and run as
one parallel wave:

- [x] 20-07-PLAN.md — decision core: deny when the authority is absent, reject an out-of-range port (gap 1 decision half, WR-01)
- [x] 20-08-PLAN.md — plugin: resolve the request authority over HTTP/2, with a red-before-green local-mode TLS gate (gap 1 transport half)
- [x] 20-09-PLAN.md — reporter: bound the audit sink for pre-auth denials, recorded as ADR-13 (CR-01)
- [x] 20-10-PLAN.md — `-PstoreBuild=true` build path, hardening-runbook accuracy, SC3 HTTP/2 contract (gap 2, WR-02, SC3 coverage warning)

**Implementation note** (resolved by `20-RESEARCH.md`, verified by decompilation + execution): use
`createApplicationPlugin("McpAccessControl", ::Config) { onCall { … } }`, installed **after** `install(CORS)` and
before `routing{}`. `onCall` attaches to `ApplicationCallPipeline.ApplicationPhase.Plugins`, which is fixed at
pipeline construction — so it is order-independent by design. Responding from it does short-circuit the route
handler (`RoutingNode.buildPipeline` wraps every `handle{}` in `if (call.isHandled) return`). Two load-bearing
details: the first statement must be `if (call.response.isCommitted) return@onCall` (a second same-phase
interceptor still runs after the first responds), and `finish()` is not callable from `onCall` and is not needed.

---

### Phase 21: Redaction Completeness

**Goal**: No path sends cookie values or other credentials to an AI backend under STRICT or BALANCED, and redaction never silently declines to run.
**Depends on**: Nothing (independent of Phase 20 — different files)
**Requirements**: PRIV-05, PRIV-06
**Success Criteria** (what must be TRUE):

1. A passive scan of a request carrying `Cookie: JSESSIONID=...; PHPSESSID=...; connect.sid=...; auth_token=...; csrftoken=...` produces a prompt in which **none** of those values appear, in both STRICT and BALANCED. Asserted per cookie name, not on the aggregate.
2. The same holds for cookies surfaced through `request.parameters()` as `COOKIE`-type params in the `=== PARAMETERS ===` section — the leak has two entry points and both are closed.
3. Sensitive-key matching recognises compound and vendor names (`auth_token`, `api-key`, `X-Session-Id`, `remember_me`) rather than only exact members of the `SENSITIVE_KEYS` alternation, without over-redacting benign keys like `keyboard_layout` or `codename`. Both directions are asserted.
4. A payload exceeding `MAX_REDACTION_BODY_CHARS` is truncated-and-redacted or refused; a test constructs an oversized body with an embedded secret and asserts the secret does not survive. (Today the body-level rules are skipped entirely above the cap.)
5. The interaction between user custom patterns and `PrivacyMode.OFF` is settled deliberately and documented in `DECISIONS.md` — whichever way it goes, it is a decision rather than a side effect of the `redactTokens` branch.
6. The existing `RedactionTest` suite including the RFC 5869 HKDF vector stays green; the fix must not perturb host anonymization.

**Plans**: 18 plans — 7 original in 4 waves, 5 gap-closure in 4 waves, plus 6 second-round gap-closure in 5 waves
Plans:
**Wave 1**

- [x] 21-01-PLAN.md — Wave 0: three Montoya-free extractions into `PassiveAiScannerPrompts.kt` + the new test seam; D-06 scanner half (wave 1)
- [x] 21-02-PLAN.md — `SafeRegex.replaceAllSafeReporting` (D-14) and the `Defaults` window/budget constants (D-04) (wave 1)
- [x] 21-03-PLAN.md — D-06 `McpToolContext` half and the four D-07 user-facing OFF strings (wave 1)
- [x] 21-04-PLAN.md — SC3: `SENSITIVE_KEY_EXPR` token-boundary matching + vendor list + camelCase, both directions asserted (wave 1)

**Wave 2** *(blocked on Wave 1 completion)*

- [x] 21-05-PLAN.md — SC1/SC2: the two cookie rules, the shared section constant, and D-10's decoy-poisoning regression test (wave 2)

**Wave 3** *(blocked on Wave 2 completion)*

- [x] 21-06-PLAN.md — SC4/SC5: the line-boundary windowed, budgeted, fail-closed body stage + D-03 truncation signal + the two deliberate SC6 inversions (wave 3)

**Wave 4** *(blocked on Wave 3 completion)*

- [x] 21-07-PLAN.md — SC4 red-before-green gate (runs alone: it transiently mutates `Redaction.kt`), ADR-14, and the CONCERNS.md residuals (wave 4)

**Gap-closure round** *(planned 2026-08-12 from `21-REVIEW.md`; run with `/gsd-execute-phase 21 --gaps-only`)*

The seven plans above all executed and merged — full suite green, ktlint and detekt clean, detekt baseline
unchanged — but the phase's own code-review gate found **4 BLOCKER findings**, all reproduced with
standalone JDK 21 probes. CR-01 is a **live PRIV-05 cookie leak**, the exact defect class this phase exists
to close, so the phase goal is not met and Phase 21 is not complete.

**Gap Wave 1**

- [x] 21-08-PLAN.md — CR-01 + CR-03: single-pass, line-bounded, deadline-bounded `redactCookieSections`; the span bound that cannot collapse on a blank entry; exports `sanitizeCookieSectionEntries` (gap wave 1)

**Gap Wave 2** *(blocked on Gap Wave 1; the two plans are file-disjoint and run in parallel)*

- [x] 21-09-PLAN.md — CR-02 + WR-06: loop the `windowEnd` JSON-boundary extension under `MAX_JSON_BOUNDARY_LOOKAHEAD_LINES`; the D-01 invariant sweep; correct the falsified byte-identity claim in source, ADR-14 and `CONCERNS.md` (gap wave 2)
- [x] 21-10-PLAN.md — CR-01 second trigger: wire the `redact/`-owned cookie-entry sanitizer into the emitter and producer so a cookie value cannot forge the section framing (gap wave 2)

**Gap Wave 3** *(blocked on Gap Wave 2)*

- [x] 21-11-PLAN.md — CR-04 + WR-05: `splitPoint` safe-cut fallback so a newline-free body is scanned instead of destroyed; retry depth 2 → 4; de-vacuum the two fail-closed tests; qualify ADR-14's line-boundary claim (gap wave 3)

**Gap Wave 4** *(blocked on Gap Wave 3; NOT autonomous — carries a blocking maintainer decision)*

- [x] 21-12-PLAN.md — WR-01 decision gate: is `SENSITIVE_KEY_EXPR`'s breadth correct, given it redacts `status_code`, `errorCode` and `token_type`? Pins the answer in the SC3 corpus. Also carries the phase-wide deferral record for WR-02, WR-03, WR-04 and WR-07 (gap wave 4)

**Second gap-closure round** *(planned 2026-08-12 from `21-REVIEW-2.md`; run with `/gsd-execute-phase 21 --gaps-only`)*

The five plans above all executed and merged, and a **deep re-review** drove the compiled shipped classes
from a JDK 21 harness rather than reading diffs. Three of the four original blockers are genuinely closed —
but **CR-02 is not**: `isJsonPairBoundaryRisk` sees only a cut in the whitespace *around the colon*, never
one inside an *open quoted value*, so a two-line JSON pair still leaks at 6 of 40 alignments of a 1 MB body
with `dropMarker=false` — a leak, not a fail-closed drop, reachable by default through
`McpToolContext.redactIfNeeded` at 2 MiB. Three records currently assert coverage that shape does not have.
The re-review also found 8 warnings and 4 info items, several of them latent traps that can silently reopen
a leak this phase just closed. PRIV-06 is not met while CR-01 (round 2) stands.

**Gap-2 Wave 1** *(the two plans are file-disjoint and run in parallel)*

- [x] 21-13-PLAN.md — CR-01 (round 2): `endsInsideOpenQuotedValue` makes the boundary predicate model the state `[^"]*` is actually in; a third boundary sweep guards the shape the existing two cannot reach; the source comment, ADR-14 and `CONCERNS.md` stop claiming coverage they lack (gap-2 wave 1)
- [x] 21-14-PLAN.md — WR-02: the OFF privacy banner reads the persisted, validated pattern list instead of unsaved `JTextArea` text; the composer becomes a top-level pure function so the defective source is out of scope rather than merely unused (gap-2 wave 1)

**Gap-2 Wave 2** *(blocked on Gap-2 Wave 1 — same file)*

- [x] 21-15-PLAN.md — the cookie-rule cluster: W-01 couples `COOKIES_MAX_COUNT` to `MAX_COOKIE_SECTION_LINES` so raising one cannot silently reopen PRIV-05; W-03 gives `sanitizeCookieSectionEntries` its first direct test; W-05 stops an expired budget destroying a prompt with no section left in it; W-02 and IN-04 dispositioned in source (gap-2 wave 2)

**Gap-2 Wave 3** *(blocked on Gap-2 Wave 2 — same file)*

- [x] 21-16-PLAN.md — W-04: the CI flake removed by an injected-budget seam, NOT by changing `MAX_REDACTION_BUDGET_MS`; IN-03 asserts the lookahead cap through a `windowEnd` seam; IN-02 and W-07's test half corrected; every remaining wall-clock assertion gets a measured headroom (gap-2 wave 3)

**Gap-2 Wave 4** *(blocked on Gap-2 Wave 3 — same file)*

- [x] 21-17-PLAN.md — W-06: the only guard on the hand-factored key vocabulary compares like with like across all three consumers and pins group counts (Pitfall 7); IN-01 stops shipping the naive expression as a public field (gap-2 wave 4)

**Gap-2 Wave 5** *(blocked on Gap-2 Wave 4 — same file)*

- [x] 21-18-PLAN.md — WR-03 deletes the fail-open `replaceAllSafe` façade; WR-07 widens the ReDoS probe to a corpus and re-validates persisted patterns at startup; WR-04/W-08 make a throwing truncation sink harmless and unwire it in `App.shutdown()` (gap-2 wave 5)

**SC6 note**: two existing `RedactionTest` assertions are *deliberately* inverted by locked decisions and are
not regressions — `oversizeBodySkippedSafely` (rewritten by D-01, it currently asserts the fail-open as
correct) and the OFF limb of `customPatternRedactsInStrictAndBalanced` (inverted by D-05). 13 of 15 stay
green untouched; `hkdfMatchesRfc5869Vector` is SC6's named vector and `Redaction.kt:167-227` is not touched.

---

### Phase 22: Agent Tool-Call Trust Boundary

**Goal**: A tool call the extension parsed out of model output cannot reach Burp without the user deciding, and the reasoning is written down so future tools inherit it.
**Depends on**: Nothing structurally, but scheduled before Phase 23 — both rewrite `ChatPanel.maybeExecuteToolCall`
**Requirements**: SEC-06
**Success Criteria** (what must be TRUE):

1. An ADR in `DECISIONS.md` records the threat model: model context contains attacker-controlled data (proxy traffic sent via "Send to AI", passive-scan findings, external MCP tool results), the model chooses tools, therefore tool selection is attacker-influenceable. The ADR states which tool classes require a decision and why the existing prompt-level `[EXTERNAL-TOOL-RESULT:...]` note is mitigation but not a control.
2. A model-emitted tool call surfaces a decision to the user before execution: approve once, approve-for-session for that tool, or deny. Denial returns a result to the model that lets the conversation continue rather than erroring the session.
3. Every decision is written to the audit log with the tool name, the decision, and the chain step — consistent with the existing `MCP_TOOL_CALL` telemetry shape.
4. The auto-chain still terminates: `MAX_AUTO_TOOL_ITERATIONS = 8` is respected, and a denied call does not consume the remaining budget in a loop.
5. A tool call the **user** initiated through `ToolInvocationDialog` is not double-prompted — the gate applies to model-originated calls, and the two paths are distinguishable in code.
6. Read-only tools versus state-mutating tools (`http1_request`, `http2_request`, `repeater_tab`, `scope_include`, `scope_exclude`, intruder) are treated per the ADR's classification, not with one blanket rule chosen at implementation time.

**Plans**: 9 plans in 6 waves
Plans:
**Wave 1**

- [x] 22-01-PLAN.md — Wave 0 test seam: headless `ChatPanel` guard, shared harness, and the pre-fix SEC-06 defect characterization (wave 1)
- [x] 22-02-PLAN.md — `SecTier` enum, the required non-defaulted `secTier` on all 59 catalog tools, and the tier parity test (wave 1)

**Wave 2** *(blocked on Wave 1 completion)*

- [x] 22-03-PLAN.md — Shared `canonicalToolId`, `ToolApprovalGate` types, `ext:`/unknown tier resolution and the display/audit sanitizers (wave 2)

**Wave 3** *(blocked on Wave 2 completion)*

- [x] 22-04-PLAN.md — Gate decision state machine: per-session memory, four actions, the D-12 denial constant, monotone budget helpers (wave 3)
- [x] 22-05-PLAN.md — `ToolDecisionReporter`: ordered `mcp_tool_decision` payload, hash-by-default, Output line, `MCP_TOOL_CALL` metadata (wave 3)
- [x] 22-06-PLAN.md — `ToolApprovalCard`: trust typography, tier badge, args disclosure, resolution and the compact resolved rows (wave 3)

**Wave 4** *(blocked on Wave 3 completion)*

- [x] 22-07-PLAN.md — The gate lands: required `origin` on `executeTool`, card wiring, parked continuation, the SC4 acceptance gate on a `CONFIRM`-tier tool (wave 4)

**Wave 5** *(blocked on Wave 4 completion)*

- [x] 22-08-PLAN.md — Pending-decision lifecycle across all five teardown paths, SC3 audit wiring, session-list marker (wave 5)

**Wave 6** *(blocked on Wave 5 completion)*

- [x] 22-09-PLAN.md — ADR-15, the ADR guard test, `CONCERNS.md` corrections and `22-HUMAN-UAT.md` (wave 6)

**Open question for `/gsd-discuss-phase`** (answered): whether the gate defaults to prompting for all tools or only for the mutating set. Resolved by `22-CONTEXT.md` D-01/D-02/D-05 — neither. SEC-06 gets its own three-tier classification independent of `unsafeOnly`, and `AUTO` requires read-only **and** bounded output, so bulk-read tools such as `proxy_http_history` prompt too.

---

### Phase 23: EDT Confinement & UI Responsiveness

**Goal**: The Burp UI stays responsive during an agent tool chain and during a Settings save.
**Depends on**: Phase 22 (same method — `maybeExecuteToolCall` — sequential to avoid merge friction)
**Requirements**: REL-05
**Success Criteria** (what must be TRUE):

1. `McpToolExecutor.executeTool` is never invoked on the EDT from the chat tool-chain path. Asserted by a test, not only by inspection — the existing `assertEdt()` is a no-op in production, so the new check must not rely on `-ea`.
2. `api.http().sendRequest(...)` inside MCP tools and `runBlocking { manager.callTool(...) }` for external MCP servers both execute off the EDT.
3. A tool chain of 8 iterations, each with a slow (multi-second) tool, leaves the UI repainting throughout; results still arrive in order and land on the EDT for rendering.
4. Saving Settings with MCP enabled→disabled does not block the EDT on `KtorMcpServerManager.stop()`'s bounded 10-second wait. `settingsRepo.save()` and `backends.reload()` are likewise not blocking the EDT.
5. No regression in the REL-01 EDT-confinement guarantees for `ChatPanel` session maps — those maps must still be touched only on the EDT while the *work* moves off it.
6. `MainTab`'s existing `Thread { … } + SwingUtilities.invokeLater` health-check pattern is reused rather than a new concurrency idiom being introduced.

**Plans**: 5/5 executed in 4 waves, verified 5/6 — plus 3 gap-closure plans in 2 waves

Plans:

**Wave 1** *(the tracer — one path end-to-end, deliberately without the guard)*

- [x] 23-01-PLAN.md — `OffEdtDispatch` helper, harness `awaitToolSettled`, the chain call site moved with its full lifecycle (S3 busy, supersede CAS, cancel, marshalled audit tail, `ToolCallOutcome.EXECUTING`), plus `ChatPanelEdtConfinementTest` carrying S-01/S-02/S-04 red-before-green (wave 1)

**Wave 2** *(23-02 and 23-03 run in parallel — zero shared files)*

- [x] 23-02-PLAN.md — the throwing door guard at `executeToolResult` **and** the two user-originated call sites, in one commit (D-04's sequencing constraint), plus Rule S-4's `/tool` echo and `McpToolExecutorEdtGuardTest` (wave 2, has a `checkpoint:decision`)
- [x] 23-03-PLAN.md — Settings: the A2 headless spike, the busy seam, `applyAndSaveSettings` off the EDT with one marshalled tail, D-13's shared async path for both callers, the E8 residual and `SettingsSaveAsyncTest` (wave 2, has a `checkpoint:decision`)

**Wave 3** *(blocked on 23-01 and 23-02)*

- [x] 23-04-PLAN.md — the remaining teardown exits, including the **explicit** supersede `deleteSession` does not inherit, plus S-05…S-09, S-12 and the rewritten negative E10 limiter dimension (wave 3)

**Wave 4** *(blocked on everything)*

- [x] 23-05-PLAN.md — SC5 regression evidence (`assertEdt()` byte-identity, the justified `invokeLater` count, no worker-side guarded-map read), SC6's stated evidence, `23-HUMAN-UAT.md`, the completed `23-VALIDATION.md` and the phase gate (wave 4)

**Gap closure** *(from `23-VERIFICATION.md`, status `gaps_found`, SC4 failed — run with `/gsd-execute-phase 23 --gaps-only`)*

**Gap wave 1** *(23-06 and 23-07 run in parallel — zero shared files)*

- [x] 23-06-PLAN.md — SC4's four `missing:` items plus all seven EDT `settingsRepo.save()` sites in `MainTab.kt`, through one generation-ordered `SettingsPersistQueue`; closes CR-02 on both halves, EDT-blocking and torn snapshot (wave 1)
- [x] 23-07-PLAN.md — CR-05's busy guard at `openToolDialog` and its 1 Hz `updateChatAvailability` limb, the audit record for a superseded user-originated call (WR-04), and CR-04's guarded dispatcher sinks (wave 1)

**Gap wave 2** *(blocked on 23-06 — shares the SettingsPanel files)*

- [x] 23-08-PLAN.md — CR-01: `SettingsPanel.shutdown()` supersedes the in-flight save worker so an unloaded extension cannot leave an MCP server listening on `127.0.0.1`; plus WR-11, wiring `edtGuardWithoutAssertionsTest` into the PR gate and nightly regression (wave 2)

**Explicitly out of scope for gap closure:** D-14 stays closed (`KtorMcpServerManager.stop()` remains blocking;
only its callers move), `assertEdt()` stays byte-identical (Phase 26 / QUAL-07 owns it), and CR-03 / D-23-04-1
(`clearChatState()` does not supersede) stays deferred with its UI question unanswered.

**Sequencing constraint that shapes the whole phase** (verified at source, `23-RESEARCH.md` Open Question 6):
`ChatPanelToolGateTest.slashCommandPathIsNotDoublePrompted` (`:350`) reaches `executeToolResult` **on the EDT today**
via `ChatPanelTestHarness.sendUserMessage`'s `invokeAndWait` (`ChatPanelTestHarness.kt:193`). Guard-first turns that
test red; moves-first leaves the guard with nothing to catch. The guard and all three call-site moves must therefore
land in one commit — which is why the tracer (23-01) carries the mechanism and 23-02 carries the guard.

**Two gates the standard `ktlintCheck detekt test` run does not cover, both phase-blocking:**
`./gradlew test -PexcludeHeavyTests=true` must show non-zero executed counts for all three new suites
(`build.gradle.kts:206-213` excludes five suffixes and `*ConcurrencyTest` is the natural wrong name to reach for here),
and `git diff --stat detekt-baseline.xml` must be empty (signature-keyed, pinned at 1096 as the v0.10.0 milestone metric).

---

### Phase 24: Scheduler & Process Robustness

**Goal**: A single exception cannot silently disable a background subsystem for the rest of the Burp session, and CLI output handling is thread-safe and bounded.
**Depends on**: Nothing
**Requirements**: REL-06, REL-07
**Success Criteria** (what must be TRUE):

1. `ActiveAiScanner.processQueue`, `ScannerTaskRegistry.cleanupExpired` and `CollaboratorRegistry.cleanupExpired` each survive a throw in their body and run again on the next tick. Asserted by injecting a throw — `scheduleWithFixedDelay` cancels the task permanently on an uncaught exception, which is exactly the failure mode being closed.
2. The active scanner keeps processing its queue after an induced failure on one target, and the failure is logged with enough context to identify the target.
3. CLI stdout capture is thread-safe: no unsynchronised `StringBuilder` is shared between the reader thread and the timeout path, where `readerThread.join(2000)` can time out while the reader is still appending.
4. CLI output is bounded — a CLI emitting far more than the 2000 characters ultimately used does not accumulate all of it in memory.
5. `deleteOnExit()` no longer registers one never-removed shutdown-hook entry per CLI invocation; temp-file cleanup still happens on the normal path and on crash.
6. `App.workerPool` and `ActiveAiScanner.requestExecutor` use bounded pools; an active scan with many injection points cannot spawn unbounded threads. Executors created by this phase carry named thread factories so a Burp thread dump is readable.

**Plans**: 5/5 executed in 4 waves (24-02 and 24-03 run in parallel in wave 2). Wave order encodes the measured SC1↔SC6 coupling: bounding an executor creates a `RejectedExecutionException` throw site on the scheduler thread at `ActiveAiScanner.kt:391`, so the guard must land first or the scanner becomes strictly worse than it is today.

Plans:

- [x] 24-01-PLAN.md — SC1: `runGuarded`/`scheduleGuarded` helper, three unguarded recurring sites migrated, structural allowlist gate, and the phase's shared `Defaults` constants plus `tasks.test` input declaration (wave 1)
- [x] 24-02-PLAN.md — SC2 + SC6 scanner half: per-target failure log carries `target.id`, `requestExecutor` bounded and named, rejected submit returns null (wave 2)
- [x] 24-03-PLAN.md — SC3 + SC4: `CliOutputBuffer` replaces the unsynchronised, unbounded CLI capture accumulator (wave 2)
- [x] 24-04-PLAN.md — SC5: `CliTempFileRegistry` per D-01…D-04, replacing the per-invocation JVM exit-hook registration (wave 3)
- [x] 24-05-PLAN.md — SC6 worker-pool half: service log pump moved to a named daemon thread, then `App.workerPool` bounded and named (wave 4)

---

### Phase 25: Secondary Hardening

**Goal**: Close the two remaining findings where a control exists but can be sidestepped.
**Depends on**: Nothing
**Requirements**: SEC-07
**Success Criteria** (what must be TRUE):

1. The bind-conflict takeover path no longer sends `Authorization: Bearer <token>` to a listener identified only by a spoofable `X-Burp-AI-Agent: mcp` response header. Whatever replaces it (certificate-fingerprint pinning against our own keystore, a challenge/response, or dropping automatic takeover) is chosen deliberately and recorded.
2. A local process that squats the MCP port and echoes the probe header does not receive the token. Asserted by a test that stands up a fake listener.
3. `SsrfGuard.isPrivateOrLinkLocal` **parses** IPv4 literals written in decimal, octal and hexadecimal form instead of rejecting them at the dotted-quad regex, and then classifies them **identically to their dotted-quad equivalent**. Concretely: `http://2130706433/`, `http://0177.0.0.1/` and `http://0x7f.1/` all denote `127.0.0.1` and therefore return **false** (loopback, per SC4 and D-01); the decimal form of `169.254.169.254` (`2852039166`) returns **true** (link-local). *(Clarified 2026-08-22: the original wording said all four return `true`, which contradicted SC4 and `SsrfGuard.kt:55`. Verified arithmetic — three of the four listed forms are loopback. The bypass being closed is that today `IPV4_REGEX = ^\d{1,3}(\.\d{1,3}){3}$` rejects these notations outright, so they skip classification entirely.)*
4. `SsrfGuard` still returns false for loopback (Ollama / LM Studio local use must not start warning) and still performs no DNS resolution.
5. The `openConnection` loopback trust-all path is either scoped to exactly the certificate the extension generated, or its residual risk is documented — a blanket trust-all on loopback is what makes finding 7 exploitable.

**Plans**: 3/3 plans executed in 2 waves. 25-01 and 25-02 are fully independent (disjoint files, disjoint success criteria) and run in parallel in wave 1. 25-03 is forced into wave 2 twice over: it modifies `McpSupervisor.kt`, which 25-01 also modifies, and its ADR-16 clause copies the SC1 decision verbatim out of `25-01-SUMMARY.md`, which does not exist until 25-01's blocking checkpoint has been answered by a human.

Plans:

- [x] 25-01-PLAN.md — SC1 + SC2: `McpTakeoverProof` proof-of-possession replaces the bearer token on the takeover path; fake-listener test proves a squatter receives nothing. Carries the phase's one blocking `checkpoint:decision` (wave 1, `autonomous: false`)
- [x] 25-02-PLAN.md — SC3 + SC4: `Ipv4Literal` parses decimal/octal/hex IPv4 literals so `SsrfGuard` classifies them identically to their dotted-quad equivalent, and a JVM-wide resolver counter proves the classifier resolves nothing (wave 1)
- [x] 25-03-PLAN.md — SC5: the loopback trust-all path is scoped to the extension's own certificate via `McpTls.pinnedLeafSha256` and fails closed when no pin can be read; ADR-16 and the operator runbook record the phase (wave 2)

---

### Phase 26: Coverage, Static-Analysis Debt & Docs

**Goal**: The milestone's code is covered by tests, the static-analysis baseline shrinks rather than grows, and users on affected versions are told.
**Depends on**: Phases 20–25 (covers the code they produce; trims the baseline once, at the end)
**Requirements**: QUAL-06, QUAL-07, DOC-03
**Success Criteria** (what must be TRUE):

1. `shellEscape` quotes by allowlist — an argument matching anything outside `[A-Za-z0-9._/-]` is quoted. `foo;id` and `$(cmd)` reach `sh -c` quoted. Asserted directly on the helper.
2. Line coverage on `redact`, `mcp` and `config` rises measurably against the recorded 2026-08-05 baseline (project-wide 34% line / 23% branch); the exact target is set at `/gsd-discuss-phase` once the earlier phases' diffs are known.
3. The detekt baseline has fewer entries than the 1096 it starts with, and no finding introduced by Phases 20–25 was added to it.
4. `assert()`-based EDT enforcement is either upgraded to something observable in production Burp or explicitly documented as a test-only mechanism — the current state reads as a runtime guarantee and is not one.
5. `SECURITY.md` carries an advisory for SEC-04 and PRIV-05 naming affected versions (0.9.0–0.9.2), impact, and the fixed version.
6. `README.md`, `SPEC.md`, `DECISIONS.md` and the GitBook repo (`burp-ai-agent-docs`) describe the tool-call confirmation flow and state `SecretCipher`'s at-rest guarantee accurately — the master key is stored beside the ciphertext in Burp Preferences, so this is obfuscation against casual inspection, not protection against a local attacker.

**Plans**: 7/7 plans executed in 3 waves. Wave 1 is four fully independent plans (disjoint files, disjoint criteria). Wave 2 holds the two documentation plans: 26-06 has two content dependencies — its ADR-16 residual asserts the mitigation 26-03 lands, and its ADR-17 clause copies 26-04's blocking-checkpoint selection verbatim out of a SUMMARY that does not exist until wave 1 closes — and 26-05 waits only because it and 26-04 both edit `build.gradle.kts`. 26-07 is wave 3 because it edits four files wave 1 owns, because it must confirm no earlier plan added a detekt finding, and because it carries the phase's coverage seal.

SC2's "rises measurably" was resolved by measurement rather than by asking: the planner ran the full suite with jacoco at `4f0ebd7` on 2026-08-22 (880 tests, 0 failures) and derived per-package floors from the result. The measurement showed that `redact` (96.13% line) and `config` (95.57% line) are already saturated against the 2026-08-05 bar of 34% line / 23% branch, and that the criterion's remaining headroom is entirely in the `mcp` tree (61.83%) and `backends/cli` (30.76%). Every floor and its provenance are recorded in each plan and sealed into `26-COVERAGE.md` by 26-07.

Plans:

- [x] 26-01-PLAN.md — SC1: `shellEscape` quotes by allowlist, extracted to a top-level `internal` helper and asserted directly plus end-to-end at the `sh -c` argv; five pure CLI helpers covered (wave 1)
- [x] 26-02-PLAN.md — SC2 for the `mcp` tree: `McpToolHelpers`' privacy and path-containment guards, the MCP wire schema and the model-supplied tool inputs; tree line coverage 61.83% → ≥ 65.0% (wave 1)
- [x] 26-03-PLAN.md — SC2 for `redact` and `config`, plus two Phase-25 follow-ups: W-2 (IPv4-mapped IPv6 notation evasion in `SsrfGuard`) and W-1b (`McpSettings.isTokenWeak` entropy floor) (wave 1)
- [x] 26-04-PLAN.md — SC4: the disposition of `ChatPanel`'s `assert()`-based EDT enforcement, taken by the developer at a blocking `checkpoint:decision` against a measured probe result (wave 1, `autonomous: false`)
- [x] 26-05-PLAN.md — SC5 + the user-facing half of SC6: the SEC-04 / PRIV-05 advisory in `SECURITY.md`, corrected at-rest claims and the tool-call confirmation flow across `README.md`, `SPEC.md` and three `docs/` pages, with the out-of-repo GitBook changes handed over as a prepared diff (wave 2, `autonomous: false`)
- [x] 26-06-PLAN.md — the `DECISIONS.md` half of SC6: ADR-16's seventh residual with a guard bound that can catch its deletion (W-1a / IN-02), ADR-17 for QUAL-07's three dispositions, and W-3's honest non-loopback TLS diagnostic (wave 2)
- [x] 26-07-PLAN.md — SC3: the detekt baseline shrinks from 1096 to ≤ 1045 across eleven named rule categories with a removals-only diff and `detekt.yml` untouched; carries the phase coverage seal (wave 3)

### Phase 27: PRIV-05 Gap Closure — sanitizeHeaders Cookie Parity

**Goal:** Close gap: PRIV-05 — mirror the cookie name-variant fix into `sanitizeHeaders`, so the MCP tool path strips `X-Cookie` / `Cookie2` / `Set-Cookie2` / `X-Original-Cookie` / `X-Forwarded-Cookie` the way the prompt path already does. The v0.10.0 milestone audit (2026-08-24) refuted PRIV-05's phase-level pass:
Phase 21 widened `cookieHeaderRegex` / `setCookieHeaderRegex` to name-contains-`cookie`
(`Redaction.kt:100-106`), but the second redaction path — `McpToolHelpers.sanitizeHeaders`
(`McpToolHelpers.kt:321`) — still tests exact names (`lowered == "cookie" || lowered == "set-cookie"`),
so cookie-bearing name variants pass through unstripped. `McpToolContext.redactIfNeeded` cannot
recover them: `sanitizeHeaders` emits single-line JSON while both cookie regexes are line-anchored
`(?im)^...$`, and `cookie` is absent from `SENSITIVE_WORDS` so `jsonSecretKeyRegex` never fires.
Reachable via the `request_parse` / `response_parse` MCP tools; confirmed by live probe against
`Custom-AI-Agent-full-1.0.0.jar`. Closing this makes PRIV-05's "by any path" wording true and
reopens `26-SECURITY.md` T-26-02-01, which had recorded it as closed.
**Requirements**: PRIV-05 (re-opened by `v0.10.0-MILESTONE-AUDIT.md`, gap F1, blocker)
**Depends on:** Phase 26
**Gap closure (2026-08-24):** `27-VERIFICATION.md` scored 7/9 and failed the two truths the goal rests on. `Serialization.kt` embeds a RAW HTTP message in a JSON string, `toolJson.encodeToString` escapes every CRLF to a literal two-character sequence, and both cookie rules are line-anchored `(?im)^…$` — so the CANONICAL `Cookie:` and `Set-Cookie:` headers leak verbatim in STRICT and BALANCED through `proxy_http_history`, `proxy_http_history_regex`, `site_map`, `site_map_regex` and `scanner_issues`, across 14 measured emission sites with no `sanitizeHeaders` in front. Strictly broader than the variant-spelling defect that created the phase. `AR-27-01` is reclassified from accepted residual to live finding. Plans 27-04 to 27-06 close it; the maintainer chose to fix rather than to scope PRIV-05 down.

**Plans:** 16/16 plans complete

Plans:
**Wave 1**

- [x] 27-01-PLAN.md — Shared `Redaction.isCookieHeaderName` predicate, `Locale.ROOT` compare, and the full variant matrix on the MCP tool-result path (wave 1)

**Wave 2** *(blocked on Wave 1 completion)*

- [x] 27-02-PLAN.md — `CookieHeaderNameParityTest` structural coupling, plus CP-27-02-01 on the tool-result header map shape and the ordering/duplicate edges (wave 2)

**Wave 3** *(blocked on Wave 2 completion)*

- [x] 27-03-PLAN.md — Reconcile the records: re-close `26-SECURITY.md` T-26-02-01 with source citations, amend `CONCERNS.md` W-A, note the closure in the milestone audit (wave 3)

**Wave 4** *(gap closure — blocked on Wave 3 completion)*

- [x] 27-04-PLAN.md — Teach the two cookie rules that a JSON-escaped newline is a line boundary; red-probe family over the real serialized emission shape; invert the green test that pinned the leak (wave 4)

**Wave 5** *(gap closure — blocked on Wave 4 completion)*

- [x] 27-05-PLAN.md — Pin the 14-site emission inventory and the single redacting choke point; close the measured credential-bearing auth-header leak on the same shape; pin which rules carry the new boundary and measure the one deliberately excluded (wave 5)

**Wave 6** *(gap closure — blocked on Wave 5 completion)*

- [x] 27-06-PLAN.md — Append-and-amend the records: T-26-02-01 clause (4), AR-27-01 reclassified, AR-27-02 superseded, AR-27-04 opened with quoted evidence, computed `threats_open`, milestone-audit heading qualified (wave 6)

**Gap closure round 3 (2026-08-25):** `27-VERIFICATION-2.md` scored 8/9. Waves 4-6 closed the carrier
they named, and PRIV-05's "by any path" wording is still not true: a THIRD carrier of the same cookie
bytes was never enumerated. Burp parses `Cookie:` into `HttpParameterType.COOKIE` **parameters**, and
`request_parse` returns those values verbatim three lines below the `sanitizeHeaders` call that had just
stripped the header — the control defeated on its own output, in one JSON object — while `params_extract`
has no cookie control at all. `cookieTypedParamRegex`, the rule written for this leak class, is bound to
the passive scanner's ` (COOKIE)` suffix and reaches neither MCP shape. Both tools take caller-supplied
content, so they ECHO a cookie the caller already holds; the maintainer chose to fix.

**Wave 7** *(gap closure round 3)*

- [x] 27-07-PLAN.md — Close the COOKIE-typed parameter carrier at the producer: one owned predicate, one shared TYPE-KEYED sanitizer, four MCP producers plus the bounty-prompt producer rewired, red probes through the real serializer (wave 7)

**Wave 8** *(gap closure round 3 — blocked on Wave 7)*

- [x] 27-08-PLAN.md — The carrier mechanism: enumerate what CARRIES cookie bytes by source accessor rather than by rendering; narrow a KDoc that claimed reach it never had; measure two neighbouring questions instead of assuming them (wave 8)

**Wave 9** *(gap closure round 3 — blocked on Wave 8)*

- [x] 27-09-PLAN.md — Records, third time: T-26-02-01 clause (5), AR-27-06 defined, computed `threats_open` with its population stated, standing rule (iv) on rendering-keyed versus source-keyed controls (wave 9)

**Gap closure round 4 (2026-08-26):** `27-VERIFICATION-3.md` scored **12/15 must-haves** and failed
three. The round-3 carrier work HELD — the COOKIE-typed parameter carrier is genuinely closed by a
TYPE-KEYED control — and three separate truths were still false. **(1) 27-02's "the predicate is
deliberately WIDER than the two regexes … wider on the redacting side is fail-safe" was measurably
false for one of the predicate's three consumers.** `PassiveAiScannerFilters.sanitizeHeadersForPrompt`
is an **ADMITTER**, not a redactor: a true result puts the header ONTO the outbound prompt, so wider
than the downstream rule is fail-OPEN there. `COOKIE_NAME_PART` excluded `_`, a legal RFC 9110 tchar,
and `my_cookie` / `X_Cookie` / `session_cookie` were measured leaking a cookie VALUE to a third-party
AI backend under **STRICT and BALANCED** — on the PROMPT path, the path this phase's goal line calls
the reference implementation. **It appeared in NO security record under `.planning/` at all**: not a
deferral with an owner, unrecorded, living in a source comment and a GREEN test whose failure message
told the next engineer not to fix it. Honest attribution: **the leak was PRE-EXISTING** — the
admitter's conjunct was already a bare contains before plan 27-01, and the name class dates from
Phase 21 — so phase 27 neither introduced nor widened it; what phase 27 did was mis-frame it and pin
it green. **(2) 27-05's "recorded, NEVER PINNED BY A GREEN TEST" was falsified by this phase's own
commit `09e9cae`**, which added two `assertTrue(… contains("api.example.com"))` assertions under
STRICT, against plan 27-05's own high-severity prohibition. **(3) 27-08's "no green test asserting
that a cookie value SURVIVES a redacting policy is committed anywhere under `src/`"** was falsified
by the wave-2 underscore pin — a must-have authored in wave 8 and verified by a search narrower than
the claim it made. A third boundary blind spot was measured in the same run: a CANONICAL `Cookie:`
header survives STRICT when it is the FIRST content of a JSON string value, with the positive control
firing on the same run. **The maintainer decided to FIX rather than accept, on BOTH axes** — widening
the REGEX side and never narrowing the predicate, and putting the JSON-string-open boundary in scope
for this round. Both decisions were made BEFORE planning and are recorded with their provenance in
`27-HUMAN-UAT.md`, deliberately distinguishable from `AR-27-04`'s harness-auto-selected disposition.
Plans 27-10 to 27-13 close them and repair the records.

**Wave 10** *(gap closure round 4 — blocked on Wave 9)*

- [x] 27-10-PLAN.md — The underscore name class: widen `COOKIE_NAME_PART` against a red probe, invert the green pin that asserted the leak, and state each consumer's polarity where a reader meets the shared predicate (wave 10)

**Wave 11** *(gap closure round 4 — blocked on Wave 10)*

- [x] 27-11-PLAN.md — The JSON-string-open boundary: teach the logical-line composer a third start, bound its over-match surface, and name the fourth start it still cannot see (wave 11)

**Wave 12** *(gap closure round 4 — blocked on Wave 11)*

- [x] 27-12-PLAN.md — No green survival pin: re-point the two prohibited STRICT host assertions at an OFF fixture, and replace plan 27-08's prose must-have with a repository-state sweep proven to fire on the real artifacts (wave 12)

**Wave 13** *(gap closure round 4 — blocked on Wave 12)*

- [x] 27-13-PLAN.md — Records, fourth time: T-26-02-01 clause (6), AR-27-09 and AR-27-10 defined from measurements, recomputed `threats_open` with its population restated, standing rule clauses (v) consumer polarity and (vi) green tests as evidence (wave 13)

**Gap closure round 5 (2026-08-26):** `27-VERIFICATION-4.md` scored **29/33 must-haves** and failed
four. **Round 4 closed all four things it targeted** — the underscore name class at the control, the
two prohibited STRICT host pins, the "no green survival pin" claim as a re-swept fact, and the
JSON-string-open carrier — and the verifier could falsify none of those closures. **What failed is two
defects wearing four costumes, and both are the same shape: a stated scope wider than the thing that
enforces it.** **(1) A NEW regression round 4 SHIPPED, not an inherited one.** `JSON_STRING_OPEN` is a
BARE DOUBLE QUOTE composed as a lookbehind, so all three composer-built rules — `authHeaderRegex`
included — now treat EVERY double quote as a logical-line start. On the primary serialized MCP
emission path an escaped quote is consumed atomically by the value tail, so the tail runs to the JSON
string's real closing quote: **1589 of 1714 characters destroyed on a realistic `proxy_http_history`
payload, all 40 content markers gone, and the JSON left structurally valid** so every existing shape
assertion passed. The same input produced NO MATCH in the round-3 state. It appeared in no source
comment, no summary and no security record, and no test gated it — the one gate that could have
caught it uses a fixture whose cookie value is the LAST content of its string. This is the first
defect in the series that fails SAFE for privacy and breaks CORRECTNESS instead. **(2) The mechanism
written to make round 4's third claim durable is blind on an axis it does not enumerate.**
`RedactingPolicySurvivalSweepTest.FUNCTION_DECLARATION` cannot see **133 of 1781 declaration lines
— 136 of 1784 on the paren-optional population `27-REVIEW-2` CR-01 counted — 67 of them backtick-named
`@Test` methods across 9 files** including one in the redaction package, so
"fails CI on the next such pin" is false for the more idiomatic Kotlin naming style — and
`26-SECURITY.md` clause (vi) cites that enumeration as the check's stated bound, so the register
itself carries a claim wider than its control, which is verbatim the failure clause (vi) exists to
prevent. `27-REVIEW-2.md`'s three blockers were all independently confirmed by the verifier; CR-02
(no positive gate on the `fileWalk` to `detect` composition) is folded in. Plans 27-14 to 27-16 close
them. **Round 5 does NOT close PRIV-05, and every plan in it says so.**

**What round 5 MEASURED (2026-08-26, recorded by plan 27-16 from `27-14-SUMMARY.md` and
`27-15-SUMMARY.md`, every number re-read against the tree rather than carried forward from this
note).** **(1) THE NARROWING.** `JSON_STRING_OPEN` went from `"\""` to `":\""` at `Redaction.kt:333`.
On the 1714-character `proxy_http_history` payload: OUT length **125 → 1714**, characters destroyed
**1589 → 0**, content markers **0 of 40 → 40 of 40**, byte-identical **false → true**. The pre-fix
output still parsed as JSON with its sibling `notes` field byte-identical, which is why every existing
shape assertion passed while 93% of the payload was gone. All five measured non-JSON false positives
went byte-identical after the edit **except probe shape 5**, whose fixture carries the literal
`Bearer ` and is claimed by the unrelated shipped `bearerRegex` independently of any boundary; plan
27-14 added shape 5b (the same prose minus that token) as the clean proof and reported shape 5
honestly rather than declaring it passed. Round 4's own target was NOT un-fixed: all three PROBE C
cases produce `Cookie: [STRIPPED]` in both columns and both modes. **(2) THE DECLARATION GATE.** The
six-shape survival pin went **1 of 6 → 6 of 6**; the pre-round historical corpus still reports
**EXACTLY 3** hits under the same three identifiers; the current tree still reports **0** qualified
over **151** files, and the unqualified arithmetic is unchanged BY the widening at 9 = 7 + 1 + 1 —
so the widening bought scope without buying noise, and nothing was narrowed to keep the hit set
empty. **(3) THE WALK COMPOSITION.** **1 / 2 / 0** hits across the shipped walk and its two
neutralisations, and in the blank-everything run that test was the ONLY failing test in the class
with the other 13 green — the silently-vacuous pass in full. All **151** files now walk without
throwing, so **0** end inside a raw string. **One count in the note above was CORRECTED IN PLACE
rather than left to stand beside its refutation:** it paired 136 invisible declarations with a
population of 1779; plan 27-15 re-measured on the tree with 27-14 landed and found **133 of 1781** on
the population that requires the opening parenthesis to follow the identifier and **136 of 1784** on
the paren-optional population CR-01 counted, the 3-line difference being extension-receiver
declarations. Both are recorded; see `.planning/WINDOWS.md`.

**Wave 14** *(gap closure round 5 — blocked on Wave 13)*

- [x] 27-14-PLAN.md — The content-destruction regression: narrow the third logical-line start to a JSON string VALUE open, state the cost of the start round 4 added where a reader meets the rule, repair the gate whose fixture could not observe it, and name the array-element residual it buys (wave 14)

**Wave 15** *(gap closure round 5 — blocked on Wave 14)*

- [x] 27-15-PLAN.md — The sweep's declaration-shape blindness: widen the gate to both name spellings and any modifier prefix, bind the walk to the detector in the flagging direction, make the unbalanced-file blindness loud, and restate the bound in the register in the same change (wave 15)

**Wave 16** *(gap closure round 5 — blocked on Wave 15)*

- [x] 27-16-PLAN.md — Records, fifth time: T-26-02-01 clause (7), AR-27-11 defined from a measurement, recomputed `threats_open` with its population restated, and standing rule clause (vii) — a residual list must enumerate what the round INTRODUCED, not only what it inherited (wave 16)

**PHASE 27 COMPLETES WITH PRIV-05 NOT SATISFIED (2026-08-25, recorded by plan 27-09).** Stated here,
in the phase record, rather than only in a SUMMARY. The goal line above is round-one text and is
deliberately **not rewritten** — it is qualified in place by this paragraph, on the same
append-and-amend terms `26-SECURITY.md` is kept under. **RE-CONFIRMED 2026-08-26 AFTER ROUND 4 (plan 27-13): THIS PARAGRAPH STILL HOLDS, UNCHANGED.**
Round 4 closed two boundary axes and repaired the records; it did NOT close PRIV-05. `AR-27-08` —
the transitive issue-detail carrier, the one finding in this series carrying Burp-held proxied
traffic and surviving STRICT — is untouched by plans 27-10 through 27-13 and is still owned by
Phase 28, together with `InjectionPointExtractor.kt:29`. A round that closes two carriers and leaves
the parent requirement refuted must say so, and this is the fourth round in which it does.

**What phase 27 DID close:** the COOKIE-typed parameter carrier, at the producer — one type-keyed
predicate (`Redaction.isCookieParameterType`) and one shared sanitizer
(`McpToolHelpers.sanitizeParameters`) now gate all four MCP producers plus the bounty-prompt
resolver — and an accessor-keyed carrier inventory now exists (`CookieCarrierInventoryTest`: 5
accessors, 72 sites, 11 files, four blind axes named in its own KDoc).

**What phase 27 leaves OPEN, by design and with an owner:** `AR-27-08`, the transitive issue-detail
carrier — a COOKIE-typed injection point's value reaching the `scanner_issues` tool result through
`AuditIssue.detail()`, **measured as surviving STRICT and BALANCED**, severity medium, latent behind
three preconditions — together with the one unconverted cookie-type predicate at
`InjectionPointExtractor.kt:29` whose value feeds it. **Both are owned by Phase 28 below.** The
deferral is deliberate: a fix without its own red probe and source-cited reachability analysis is the
same-day closure pattern that has now failed three times in this phase. **AMENDED 2026-08-26 (plan 27-13) — APPENDED, NOT REWRITTEN. Round 4 opened two more residuals, so
the full list is now SIX, and every one is named here with an owner.** **(1) `AR-27-04`** — `Host:`
and `SiteMapEntry.url` reaching an AI backend un-anonymised under STRICT on the serialized emission
shape. **OPEN at MEDIUM, and STILL OWED A HUMAN DECISION**: its disposition on record was
AUTO-SELECTED by `mode: yolo`, not maintainer-chosen. Plan 27-12 deleted the two green STRICT
assertions that pinned it and re-pointed their pass-through at a `PrivacyMode.OFF` byte-identity
fixture — **a test-artifact repair that supplies no human judgment and does not upgrade that
provenance.** **Owner: the maintainer**, carried as item 9 of the round-4 section of
`27-HUMAN-UAT.md`. **(2) `AR-27-08` and (3) `InjectionPointExtractor.kt:29`** — unchanged from the
paragraph above, **owned by Phase 28**, untouched by round 4. **(4) `AR-27-09`** — NEW this round:
the FOURTH logical-line start, a leading-whitespace or obs-folded header line, which
`logicalLineHeaderRule` still cannot recognise. **OPEN at LOW, MEASURED surviving BYTE-UNCHANGED
under STRICT *and* BALANCED** — one mode wider than `27-VERIFICATION-3.md` recorded, because plan
27-11 re-measured instead of copying the prediction forward. Bounded `low` because no measured
emission site in this repository indents a header line; open because reachability through
analyst-authored `HttpRequestResponse.notes` text is UNMEASURED. Its one-token fix (`^[ \t]*` for
`^`) is written down beside it. **Owner: the maintainer**, item 10 of `27-HUMAN-UAT.md`. **(5)
`AR-27-10`** — NEW this round: the **13** RFC 9110 tchars still outside the widened
`COOKIE_NAME_PART`, from a source-pinned partition of 77 = 64 + 13. **OPEN at LOW.** The partition
and the fail-open mechanism are MEASURED; the carry-over to those thirteen characters is INFERRED
and is labelled as inferred, and **no leak was measured for any of them.** **Owner: the maintainer**,
item 11 of `27-HUMAN-UAT.md`. **(6) The `CONCERNS.md` vendor auth-header class** — every vendor auth
header outside `authHeaderRegex`'s 16-name alternation (`x-shopify-access-token`,
`x-amz-security-token`, `stripe-signature` and whatever ships next quarter) is matched by no rule at
all. **OPEN BY PROHIBITION**, unchanged since 2026-08-13 and untouched by round 4: an open-ended
vendor list is never complete, which is the stated reason it stays deferred while the bounded cookie
class does not. **AND A SEVENTH THING THAT IS NOT A FINDING BUT IS A BOUND, named because omitting it
would be the error clause (vi) of `26-SECURITY.md` describes:** `RedactingPolicySurvivalSweepTest`,
the CI gate that now enforces "no green survival pin", is a **TRIPWIRE OVER A MEASURED VOCABULARY,
NOT A PROOF OF COVERAGE** — its own KDoc names eleven things it cannot see before its first
assertion. **SIX NAMED RESIDUALS IS NOT A COMPLETENESS CLAIM.** Naming what is known to be open says
nothing whatever about what is not yet known, and this phase has now been refuted four times by
exactly the thing no list contained. No sentence in this record may be read as implying otherwise.

***[SUPERSEDED 2026-08-26 by plan 27-17 — `AR-27-09` is CLOSED BY FIX, not open at LOW.]*** The paragraph above is preserved byte-for-byte as the record made while the finding was open; none of it is withdrawn. The LOW it carries rested on an explicitly UNMEASURED reachability claim, and the maintainer therefore decided the finding by FIX rather than by acceptance at UAT (`27-HUMAN-UAT.md` item 10, commit `ae3371a`). `Redaction.logicalLineHeaderRule`'s REAL-LINE branch now starts at `REAL_LINE_START = "^[ \t]*+"` instead of a bare `^`, so an indented header line and an obs-folded continuation line are recognised in STRICT and BALANCED across all three composed rules — measured before/after in the **"AR-27-09 — CLOSED BY FIX 2026-08-26"** section of `26-SECURITY.md`, gated by `IndentedLogicalLineStartTest` and by `LogicalLineBoundaryScopeTest.theRealLineStartRecognisesLeadingHorizontalWhitespace`, and mutation-proven in both directions. **This does NOT close PRIV-05**, which stays `[ ]`, and it leaves `AR-27-10` and `AR-27-11` open. `AR-27-09` is an `AR-` row, so it was always outside the `threats_open` population — the counter was recomputed and is unchanged at `0`.

**The `- [x] **PRIV-05**` tick at `REQUIREMENTS.md:23` is wrong for the third time and is NOT
corrected by this phase.** `REQUIREMENTS.md` is untouched (0 added, 0 removed). Re-deriving the tick
is the milestone owner's job, from the clauses of `26-SECURITY.md` T-26-02-01 rather than from any
sentence phase 27 wrote about itself — a phase that grades its own homework produces exactly the
record this phase spent nine plans repairing. **APPENDED 2026-08-26 (plan 27-13): the milestone owner has since REVERTED that tick, and
`REQUIREMENTS.md:23` now reads `- [ ] **PRIV-05**`.** That is the outcome this paragraph asked for,
so the paragraph is recorded as ANSWERED and is deliberately NOT deleted — a paragraph that produced
a correction is worth more on the record than a silence where the correction used to be needed.
`REQUIREMENTS.md` remains untouched by round 4 as it was by 27-03, 27-06 and 27-09 (0 added, 0
removed), and PRIV-05 stays unticked because `AR-27-08` is open and owned by Phase 28.

**AMENDED 2026-08-26 (plan 27-16) — APPENDED, NOT REWRITTEN, and SPLIT, because `26-SECURITY.md`
standing-rule clause (vii) now requires a residual list to enumerate what the round INTRODUCED and
not only what it INHERITED, with the two visibly separated. The paragraph above is round 4's list and
stands unedited; this is round 5's, and the reason the split exists is that round 4's list — six
entries, every one real and correctly owned — contained no residual round 4 itself created.**

***Residuals ROUND 5 INTRODUCED.*** **(1) `AR-27-11`** **[SUPERSEDED at LOW / one family — read the
dated CORRECTION immediately after this entry before relying on any of it]** — the
JSON-ARRAY-ELEMENT logical-line start,
created by plan 27-14's narrowing of `JSON_STRING_OPEN` to a colon-quote sequence. A header at the
open of an array-element string is no longer a recognised start. **OPEN at LOW, MEASURED in both
columns** (`{"tags":["Cookie: a=SECRET8"]}` was `Cookie: [STRIPPED]` under the bare quote and is
byte-unchanged after), with its REACHABILITY measured this round rather than assumed:
`mcp/schema/Serialization.kt` declares **zero** `List<String>` fields, multi-item results carry no
JSON array wrapper, the five `List<String>` models under `McpToolModels.kt` are input-only, and
exactly **one** carrier can emit an arbitrary array of strings through `Redaction.apply` — the D-03
outbound-privacy redaction of model-authored `argsJson` in
`McpToolExecutorImpl.routeExternalToolCall`, whose REMOTE schemas are not owned here and are
**UNMEASURED** and labelled as such. Bounded `low` because a realistic raw HTTP message inside an
array element is STILL stripped: its header follows an escaped newline, which IS a recognised start —
measured, with two positive controls firing in the same run. **Owner: the maintainer**, item 12 of
`27-HUMAN-UAT.md`.

**CORRECTION 2026-08-26 to entry (1) — RAISED to MEDIUM over FOUR families. Nothing above this marker
is edited.** Applied out-of-plan on maintainer authorisation after `27-REVIEW-3.md` CR-01 (`2ed1a12`),
and PROPAGATED HERE after `27-VERIFICATION-5.md` gap 2 found that `2ed1a12` had reached
`26-SECURITY.md`, `Redaction.kt` and the `THIRD_OPEN_FINDING` KDoc and had reached neither this list
nor `27-HUMAN-UAT.md` item 12. **Entry (1) reads, corrected: `AR-27-11` — the JSON-STRING-OPEN
logical-line start in EVERY spelling `:"` does not recognise, FOUR MEASURED families under one id,
OPEN at MEDIUM.** The mechanism rather than the example: `:"` is colon then quote LITERALLY, so any
shape that interposes a character between them — a space, or the backslash of an escaped quote — and
any shape with no colon before the quote at all, is not a start. The four, all measured matching
under the bare quote and byte-unchanged after, in STRICT and BALANCED and across all three composed
rules: **(1)** a NESTED / ESCAPED value open (`\"k\":\"Cookie: …`), which is what a captured JSON
RESPONSE BODY looks like once it is serialized into a tool result, **so this family sits on the
PRIMARY emission path**; **(2)** PRETTY-PRINTED JSON (`"k": "Cookie: …`); **(3)** a BARE TOP-LEVEL
JSON string; **(4)** the ARRAY ELEMENT this entry named alone. **The severity moved because the
reachability question moved:** the old LOW rested on "which serialized fields are `List<String>`?",
and the carrier is not a FIELD — it is the CONTENT of the `response` string this repository copies
verbatim from the target, so families 1, 2 and 4 are reachable with BURP-HELD traffic in the DEFAULT
posture with no opt-in precondition. **NOT `high`:** no live producer was measured. **The mitigating
bound the MEDIUM rests on, checked in all four:** only a header that is the FIRST CONTENT of its
string escapes; one that follows an escaped newline is still stripped. **Owner unchanged: the
maintainer, item 12 of `27-HUMAN-UAT.md`** — which is itself corrected in the same change as this
entry, behind its own SUPERSEDED marker, because it is a DECISION document and its Option B ("widen
… closes the residual at the control") in fact closes family 4 ONLY. Entries (2), (3) and (4) are
untouched and unaffected. Full re-derivation: the `AR-27-11` row and the correction section in
`26-SECURITY.md`.

**(2) The sweep's AXIS 9** — a declaration whose opening parenthesis does not
follow the identifier on its line, created by plan 27-15's widening of `FUNCTION_DECLARATION`, which
also requires that parenthesis. **3** extension-receiver declarations measured live on this tree, one
of them inside the sweep file itself, and **0** multi-line signatures — the plan anticipated the
multi-line shape and the measurement found the other one, so the axis names BOTH shapes with BOTH
counts. **Owner: the sweep's own KDoc**, inside the machine-checked `STATED_BLIND_AXES = 13`.
**(3) A RED `detekt` GATE, merged unseen — FIXED by plan 27-16.** Plan 27-15's three new raw-string
fixtures each tripped `MayBeConst`, so `./gradlew detekt` failed on the round-5 base. **Neither plan
27-15's own verification command nor the wave-9 post-merge gate ran `detekt`** — both ran
`ktlintCheck test` — so a red gate was merged and nobody saw it. Closed by making the three fixtures
`const val`: no behaviour change and no growth of `detekt-baseline.xml` (QUAL-07). **(4) A RED
`jacocoTestCoverageVerification` GATE — MEASURED, BISECTED and DELIBERATELY NOT FIXED.** The `redact`
package's BRANCH ratio is **0.9278** against a **0.930** floor. Bisected against the trees rather
than guessed: the pre-round-5 tree `c2d980f` **PASSES at 0.9330** (13 missed / 116 covered); the
round-5 base `87c1102` and the final 27-16 tree both **FAIL at 0.9278** (14 / 115). **Exactly ONE
branch flipped, and it is `if (remainingMs <= 0L)` at `Redaction.kt:1628`** — the WALL-CLOCK
budget-exhaustion guard, the same `SafeRegex` 50 ms deadline path as the documented `RedactionTest`
flake. Covered in 1 of 1 pre-round-5 runs, missed in 2 of 2 round-5 runs. **Whether the cause is
27-14's narrowing making the composed regexes cheap enough that the deadline stops firing
incidentally, or ambient CPU load, is NOT established by three samples and is NOT claimed here.**
The load-bearing fact either way: **the floor has ONE branch of headroom, and that branch is
timing-dependent** — a coverage gate partly met by a race. **NOT fixed by this plan, and the reason
is the reason:** the honest options are a DETERMINISTIC test for the budget-exhaustion branch, or
lowering a QUAL-06 floor to turn a red gate green. The second is exactly the laundering this phase
exists to prohibit, and the first is a test-design task that a records plan has no business doing
same-day — which is the closure pattern that has already failed four times here. **Owner: the
maintainer**, and both gates are in `.planning/WINDOWS.md`. **These two are in this list ONLY because
plan 27-16's acceptance criteria required `./gradlew check` to be run. Round 5 would otherwise have
closed with two red gates in its own tree and a residual list that named neither — which is clause
(vii)'s worked example happening again inside the round that wrote clause (vii).**

***Residuals ROUND 5 INHERITED, each carried forward with its owner UNCHANGED.*** **`AR-27-04`** —
`Host:` and `SiteMapEntry.url` un-anonymised under STRICT. OPEN at MEDIUM, **still owed a HUMAN
decision** (its recorded disposition was auto-selected by `mode: yolo`), **deliberately NOT
relitigated by round 5**. Owner: the maintainer, item 9. **`AR-27-08` and
`InjectionPointExtractor.kt:29`** — owned by Phase 28, untouched by round 5. **`AR-27-09`** — the
leading-whitespace / obs-folded fourth start, OPEN at LOW, one-token fix written down. Owner: the
maintainer, item 10. **`AR-27-10`** — the thirteen RFC 9110 tchars, OPEN at LOW, partition measured
and carry-over labelled inferred. Owner: the maintainer, item 11. **The `CONCERNS.md` vendor
auth-header class** — OPEN BY PROHIBITION, unchanged. **The sweep's vocabulary bound** — still a
TRIPWIRE OVER A MEASURED VOCABULARY AND NOT A PROOF OF COVERAGE, now stating **THIRTEEN** axes it
cannot see, machine-checked against its own enumeration rather than the eleven the paragraph above
transcribed by hand.

***[SUPERSEDED 2026-08-26 by plan 27-17 — `AR-27-09` is CLOSED BY FIX, not open at LOW.]*** The paragraph above is preserved byte-for-byte as the record made while the finding was open; none of it is withdrawn. The LOW it carries rested on an explicitly UNMEASURED reachability claim, and the maintainer therefore decided the finding by FIX rather than by acceptance at UAT (`27-HUMAN-UAT.md` item 10, commit `ae3371a`). `Redaction.logicalLineHeaderRule`'s REAL-LINE branch now starts at `REAL_LINE_START = "^[ \t]*+"` instead of a bare `^`, so an indented header line and an obs-folded continuation line are recognised in STRICT and BALANCED across all three composed rules — measured before/after in the **"AR-27-09 — CLOSED BY FIX 2026-08-26"** section of `26-SECURITY.md`, gated by `IndentedLogicalLineStartTest` and by `LogicalLineBoundaryScopeTest.theRealLineStartRecognisesLeadingHorizontalWhitespace`, and mutation-proven in both directions. **This does NOT close PRIV-05**, which stays `[ ]`, and it leaves `AR-27-10` and `AR-27-11` open. `AR-27-09` is an `AR-` row, so it was always outside the `threats_open` population — the counter was recomputed and is unchanged at `0`.

***What round 5 CLOSED, of round 4's six.*** **The sweep's declaration-shape blindness is CLOSED** at
the gate (1 of 6 → 6 of 6, with the historical corpus still reporting exactly 3 hits). **The
bare-quote logical-line start is CLOSED** — and it was never on round 4's list at all, which is the
defect clause (vii) now names. **The other four residuals and the vendor auth-header class are
UNCHANGED and keep their owners.** A shrinking count is not progress unless the record says which
entries moved and why, so it does.

**THIS LIST — SEVEN INHERITED ENTRIES AND FOUR INTRODUCED ONES, OF WHICH THREE REMAIN OPEN — IS
STILL NOT A COMPLETENESS CLAIM.** Naming what is known to be open says nothing about what is not yet
known, and this phase has now been refuted FIVE times by exactly the thing no list contained — the
fifth time by a residual the round itself manufactured. Two of the four INTRODUCED entries above were
found only because one plan in this round happened to run a gate the other two did not, which is a
statement about luck and not about method. No sentence in this record may be read as implying
otherwise.

**RE-CONFIRMED 2026-08-26 AFTER ROUND 5 (plan 27-16): THE "PHASE 27 COMPLETES WITH PRIV-05 NOT
SATISFIED" PARAGRAPH ABOVE STILL HOLDS, UNCHANGED, FOR THE FIFTH ROUND.** Round 5 closed a
correctness regression this phase itself shipped and a defect in its own security record. It closed
no carrier and no requirement. `REQUIREMENTS.md` is untouched across the whole round-5 commit range
(0 added, 0 removed) and `REQUIREMENTS.md:23` still reads `- [ ] **PRIV-05**`. `AR-27-08` — the one
finding in this series carrying Burp-held proxied traffic and surviving STRICT — is untouched by
plans 27-14 through 27-16 and is still owned by Phase 28, together with
`InjectionPointExtractor.kt:29`. A round that repairs its own regression and leaves the parent
requirement refuted must say so, and this is the fifth round in which it does.

### Phase 28: The Issue-Detail Cookie Carrier — `AuditIssue.detail()` → `scanner_issues`

**Goal:** Close `AR-27-08`. A COOKIE-typed injection point's value reaches the `scanner_issues` MCP
tool result through `AuditIssue.detail()` and **survives `Redaction.apply` in STRICT and in
BALANCED**, emitted verbatim — measured by plan 27-08 with a positive control that fired on the same
payload (a real `Cookie:` header in the same `IssueDetails` object became `Cookie: [STRIPPED]` in the
same STRICT output in which the detail-line sentinel survived). **This is the one finding in the
phase-27 series that carries BURP-HELD proxied traffic** — a real session cookie the operator's
browser sent — rather than caller-echoed content, and there is no privacy mode in which the field is
protected. **Mechanism, already measured so this phase does not have to re-derive it:**
`IssueUtils.formatIssueDetailHtml` (`util/IssueUtils.kt:51-63`) joins `detailLines` with `<br>`, so
the blob carries **no newline at all** and the logical-line cookie rules have nothing to bind to; the
rendered shape is `Original Value: <value>`, not `name=<value> (COOKIE)`, so `cookieTypedParamRegex`
cannot key on it; and the enclosing JSON key is `detail`, which is not in `SENSITIVE_WORDS`.
**Reachability, cited at source:** the write is unconditional and NOT privacy-mode gated
(`scanner/ActiveAiScanner.kt:1239`); a confirmation is required first (`:1172-1176`, `:1183`); the
mode is Active AI scanning, opt-in and defaulting to `false` (`config/AgentSettings.kt:127`); a
COOKIE-typed point CAN reach that line because the target loop filters on vuln CLASS only, never on
`point.type` (`:232-246`, `:1684`); and it leaves via `detail = detail()` at
`mcp/schema/Serialization.kt:14`.

**This phase closes `AR-27-08` AND `InjectionPointExtractor.kt:29` TOGETHER, and that pairing is the
point.** That file's line 29 writes its own cookie-parameter predicate,
`request.parameters().filter { it.type().name == "COOKIE" }`, and was left **byte-unchanged** by both
plan 27-07 (baseline B9) and plan 27-08 — deliberately, not by omission. Its two consumers differ:
`AdaptivePayloadEngine.kt:52` is CONTROLLED (it substitutes `[REDACTED_VALUE]` under any non-`OFF`
mode), while `ActiveAiScanner.kt:1239` is UNCONTROLLED and is this finding. **Converting the
predicate alone would produce a tidier file and an unchanged leak** — it would make the route LOOK
addressed, which is the exact failure mode `26-SECURITY.md` T-26-02-01 now records three times. The
predicate is only meaningful as part of the route it feeds.

**Why this was deferred out of phase 27 rather than fixed there:** plan 27-08's `T-27-08-06` was
dispositioned **TRANSFER, not mitigate** — that plan MEASURED the route and applied no control to it,
and calling a measurement a mitigation is the overclaim vocabulary phase 27 exists to correct. A fix
needs its own red probe and its own reachability analysis, which is closure-phase work.

**Requirements**: PRIV-05
**Depends on:** Phase 27
**Success Criteria** (what must be TRUE):

1. A COOKIE-typed injection point's `originalValue` does not appear in the `scanner_issues` tool
   result in STRICT or BALANCED. Cookie NAMES may remain; VALUES must not.

2. Under `OFF` the value still appears — so the fix is proven to be policy-driven and not an
   unconditional rewrite.

3. A red probe reverting the control turns a NAMED assertion red, and the specific assertion and its
   failure message are recorded — not "the suite went red".

4. `InjectionPointExtractor.kt:29` is resolved in the same phase as the route, with its two
   consumers' differing dispositions preserved (`AdaptivePayloadEngine.kt:52` already controls its
   own path and must not be double-redacted into a misleading prompt).

5. `26-SECURITY.md`'s `AR-27-08` row is amended — append-and-amend, prior text byte-prefix intact —
   and `threats_open` is recomputed rather than asserted.

6. `ResponseAnalyzer`'s narrow transitive tail is examined in the same pass: a MATCHED substring of a
   vuln-class signature, capped at 80 chars, can be written into `VulnConfirmation.evidence`, which
   `ActiveAiScanner.kt:1246` places in the SAME `AuditIssue` detail as this finding.

**Not in scope, named so it is not silently absorbed:** `AR-27-07` (non-cookie parameter types,
measured low) is a separate disposition and is routed to `27-HUMAN-UAT.md` test 8, not to this phase.

**Plans:** 8/8 plans executed — 6/6 executed (round 1 + gap-closure round 2); gap-closure round 3 planned, not executed

Plans:
**Wave 1**

- [x] 28-01-PLAN.md — control the write site: a COOKIE-typed `originalValue` is policy-stripped from `AuditIssue.detail` under STRICT/BALANCED, survives under OFF, with the red probe (SC1, SC2, SC3)

**Wave 2** *(blocked on Wave 1 completion)*

- [x] 28-02-PLAN.md — resolve `InjectionPointExtractor.kt:29` to the shared predicate and prove `AdaptivePayloadEngine`'s controlled path byte-unchanged; correction fan-out to five prose sites (SC4)

**Wave 3** *(blocked on Wave 2 completion)*

- [x] 28-03-PLAN.md — measure the `ResponseAnalyzer` evidence tail and file `AR-28-01`; amend `AR-27-08` append-and-amend; recompute `threats_open`; state the PRIV-05 judgement (SC5, SC6)

### Gap closure round 2 — SC1 measured FALSE by `28-VERIFICATION.md`

Round 1 controlled ONE of THREE detail lines at ONE of TWO producers. SC2-SC6 are VERIFIED and must
not regress. Waves below are numbered within the gap round (`/gsd-execute-phase 28 --gaps-only`).

**Gap wave 1**

- [x] 28-04-PLAN.md — TRACER: control `ScannerIssueSupport.kt:121` (`Payload Used:`) type-keyed on `InjectionType.COOKIE` per D-28-07; rebuild the fixture through `PayloadGenerator` so the assertion can see route 1; repair the difference enumeration; red probe (CR-01, SC1/SC2/SC3)

**Gap wave 2** *(blocked on gap wave 1)*

- [x] 28-05-PLAN.md — control `AiScanCheck.buildDetail`, the second producer, keyed on `AuditInsertionPointType.PARAM_COOKIE` (NOT the shared string-name predicate); the repository's first `AuditInsertionPoint` test fixture; a tripwire for the new predicate population; correct the `ONLY PRODUCER` claim; fix WR-05 (CR-02)

**Gap wave 3** *(blocked on gap wave 2)*

- [x] 28-06-PLAN.md — `checkpoint:decision` on the `AR-27-08` disposition; second append-and-amend supersession with a pre-computed byte-prefix digest; recompute `threats_open`; discharge the 28-03 RUN 2 recording failure; correct the carrier registry and add the `baseValue()` accessor that hid route 2 (D-28-08)

### Gap closure round 3 — the record repair (`D-28-09` / `D-28-10` / `D-28-11`)

Round 2 closed all seven round-1 gaps and the mechanism work is sound. `28-VERIFICATION.md` returned
`gaps_found` 5/6 with SC1 adjudicated **(b) not satisfied** on the write-time/read-time bound: both
controls decide once at issue construction and bake the result into an immutable `AuditIssue.detail()`,
so an issue built under `OFF` still emits the raw cookie value on a later STRICT read. The maintainer
**ACCEPTED that bound as a NAMED RESIDUAL on 2026-08-28** (`D-28-09`), conditional on the silence
being repaired (`D-28-10`). This round changes **no runtime behaviour** — it repairs the record and
earns the conditions the acceptance rests on. `AR-27-08` stays OPEN; PRIV-05 stays `- [ ]`.

**Gap wave 4**

- [x] 28-07-PLAN.md — TRACER: name the `WRITE-TIME/READ-TIME BOUND` at the privacy-mode tooltip and at `AiScanCheck.consolidateIssues`; make the `**Payload Used:**` probe claim TRUE with four named assertions; correct the false `type()` KDoc premise and NAME (not widen) the route-2 fail-open set (`D-28-10` conditions 2-3, `D-28-11`)

**Gap wave 5** *(blocked on gap wave 4)*

- [x] 28-08-PLAN.md — third append-and-amend supersession on `ISSUE_DETAIL_CARRIER_DISPOSITION` and on `26-SECURITY.md` row 315 clause (d), both behind pre-computed byte-prefix digests; recompute `threats_open`; then, LAST and gated on six machine-checked conditions, apply the SC1 override to `28-VERIFICATION.md` frontmatter (`D-28-10` conditions 1 and 4)

---

## Progress

**Execution Order (v0.10.0):**

Phase 20 → 21 (live defects, disjoint files, 20 first on severity). Phase 22 → 23 strictly sequential (both rewrite `maybeExecuteToolCall`). Phases 24 and 25 independent, may run any time after 20. Phase 26 last. Phase 27 added 2026-08-24 after the milestone audit reopened PRIV-05; it runs after 26.

| Phase | Plans Complete | Status | Completed |
|-------|----------------|--------|-----------|
| 20. MCP Access-Control Correctness | 10/10 | Complete    | 2026-08-10 |
| 21. Redaction Completeness | 19/19 | Complete    | 2026-08-13 |
| 22. Agent Tool-Call Trust Boundary | 9/9 | Complete    | 2026-08-20 |
| 23. EDT Confinement & UI Responsiveness | 8/8 | Complete    | 2026-08-21 |
| 24. Scheduler & Process Robustness | 5/5 | Complete    | 2026-08-22 |
| 25. Secondary Hardening | 3/3 | Complete    | 2026-08-22 |
| 26. Coverage, Static-Analysis Debt & Docs | 7/7 | In Progress|  |
| 27. PRIV-05 Gap Closure — sanitizeHeaders Cookie Parity | 16/16 | Complete    | 2026-08-27 |

## Backlog

- Mega-file refactor: `ChatPanel.kt` (2237 lines), `ActiveAiScanner.kt` (1668), `AgentSettings.kt` (1438), `McpToolExecutorImpl.kt` (1291), `AgentSupervisor.kt` (1277). Deferred out of v0.10.0 so the security diffs stay legible.
- Anthropic native tool-use and prompt-caching (deferred from CAP-01, v0.9.0).
- Interactive custom-pattern sample tester (deferred from PRIV-04, v0.9.0).
- BApp Store submission #231 — stalled; revisit when it moves.
- MCP tool-result header entry-list: `sanitizeHeaders` returns `Map<String, String>`, so byte-identically-named headers collapse to one entry (three `Set-Cookie` headers surface as one). Privacy-safe — every collapsed entry was already `[STRIPPED]` — but it costs analysis signal on a normal HTTP response. Deferred from Phase 27 as accepted residual `AR-27-03` via `CP-27-02-01` (one-way: changing it breaks the `request_parse` / `response_parse` result schema of the shipped 1.0.0, across 4 call sites and 2 models).
- **`AR-27-08` — the issue-detail cookie carrier, OWNED BY PHASE 28 (opened 2026-08-25 by plan 27-09).** A COOKIE-typed injection point's value reaches the `scanner_issues` tool result through `AuditIssue.detail()` and **survives `Redaction.apply` in STRICT and BALANCED**, measured by plan 27-08 with a positive control that fired on the same payload. Severity **medium**: it carries Burp-held proxied traffic and defeats STRICT outright (aggravating), but is latent behind three preconditions — an opt-in active scanner defaulting to `false`, a finding reaching `confirmed`, and a `scanner_issues` call (mitigating). **Closed together with `InjectionPointExtractor.kt:29`**, the one unconverted cookie-type predicate, byte-unchanged by plans 27-07 and 27-08 — fixing the predicate alone would produce a tidier file and an unchanged leak. **Why deferred rather than fixed in phase 27:** plan 27-08's `T-27-08-06` was dispositioned TRANSFER, not mitigate — it measured the route and applied no control, and a fix without its own red probe and reachability analysis is the same-day closure pattern that has failed three times. Full evidence: `26-SECURITY.md` AR-27-08 and `27-08-SUMMARY.md` measurement 2. **Phase 27 completes with PRIV-05 NOT satisfied; this is its named owner.**
- **`T-27-06-06` — the user-facing STRICT host-anonymisation overclaim, STILL UNACTIONED (restated here 2026-08-25 because it keeps nearly being lost).** `README.md:247` and `SPEC.md:80,86` state STRICT host anonymisation without the exclusion `AR-27-04` records: it does **not** apply to the raw HTTP message or the `url` field emitted by `proxy_http_history`, `proxy_http_history_regex`, `site_map`, `site_map_regex` and `scanner_issues`. **The loss risk, measured rather than assumed:** `.planning/BACKLOG.md` does not exist in this repository (re-checked 2026-08-25), so until this line the item lived only in a phase-26 register and phase-27 SUMMARYs — the two documents a docs maintainer is least likely to open. Also carried in `27-HUMAN-UAT.md` test 4. Plans 27-06 and 27-09 both scoped themselves to record files and both recorded it as deliberately unactioned: a change to what SHIPS is not a record repair. **Until it lands, `AR-27-04` is accepted AND the documentation still overclaims.**
