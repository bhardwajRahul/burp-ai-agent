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
- [ ] **Phase 23: EDT Confinement & UI Responsiveness** — Tool execution, backend HTTP and MCP stop() off the Swing EDT
- [ ] **Phase 24: Scheduler & Process Robustness** — Guard recurring tasks against death-by-exception; fix the CLI output race and unbounded resource use
- [ ] **Phase 25: Secondary Hardening** — Stop leaking the MCP token to unverified port holders; teach SsrfGuard the alternate IP notations
- [ ] **Phase 26: Coverage, Static-Analysis Debt & Docs** — Allowlist shell escaping, raise coverage on security paths, shrink the detekt baseline, publish the advisory

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

**Plans**: 5/5 plans executed in 4 waves

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

**Plans**: TBD

---

### Phase 25: Secondary Hardening

**Goal**: Close the two remaining findings where a control exists but can be sidestepped.
**Depends on**: Nothing
**Requirements**: SEC-07
**Success Criteria** (what must be TRUE):

1. The bind-conflict takeover path no longer sends `Authorization: Bearer <token>` to a listener identified only by a spoofable `X-Burp-AI-Agent: mcp` response header. Whatever replaces it (certificate-fingerprint pinning against our own keystore, a challenge/response, or dropping automatic takeover) is chosen deliberately and recorded.
2. A local process that squats the MCP port and echoes the probe header does not receive the token. Asserted by a test that stands up a fake listener.
3. `SsrfGuard.isPrivateOrLinkLocal` returns true for `http://2130706433/`, `http://0177.0.0.1/`, `http://0x7f.1/` and the decimal form of `169.254.169.254`, matching its behaviour on dotted-quad input.
4. `SsrfGuard` still returns false for loopback (Ollama / LM Studio local use must not start warning) and still performs no DNS resolution.
5. The `openConnection` loopback trust-all path is either scoped to exactly the certificate the extension generated, or its residual risk is documented — a blanket trust-all on loopback is what makes finding 7 exploitable.

**Plans**: TBD

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

**Plans**: TBD

---

## Progress

**Execution Order (v0.10.0):**

Phase 20 → 21 (live defects, disjoint files, 20 first on severity). Phase 22 → 23 strictly sequential (both rewrite `maybeExecuteToolCall`). Phases 24 and 25 independent, may run any time after 20. Phase 26 last.

| Phase | Plans Complete | Status | Completed |
|-------|----------------|--------|-----------|
| 20. MCP Access-Control Correctness | 10/10 | Complete    | 2026-08-10 |
| 21. Redaction Completeness | 19/19 | Complete    | 2026-08-13 |
| 22. Agent Tool-Call Trust Boundary | 9/9 | Complete    | 2026-08-20 |
| 23. EDT Confinement & UI Responsiveness | 5/5 | In Progress|  |
| 24. Scheduler & Process Robustness | 0/? | Not started | — |
| 25. Secondary Hardening | 0/? | Not started | — |
| 26. Coverage, Static-Analysis Debt & Docs | 0/? | Not started | — |

## Backlog

- Mega-file refactor: `ChatPanel.kt` (2237 lines), `ActiveAiScanner.kt` (1668), `AgentSettings.kt` (1438), `McpToolExecutorImpl.kt` (1291), `AgentSupervisor.kt` (1277). Deferred out of v0.10.0 so the security diffs stay legible.
- Anthropic native tool-use and prompt-caching (deferred from CAP-01, v0.9.0).
- Interactive custom-pattern sample tester (deferred from PRIV-04, v0.9.0).
- BApp Store submission #231 — stalled; revisit when it moves.
