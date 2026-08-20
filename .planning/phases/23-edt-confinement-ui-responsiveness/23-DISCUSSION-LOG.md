# Phase 23: EDT Confinement & UI Responsiveness - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-08-20
**Phase:** 23-edt-confinement-ui-responsiveness
**Areas discussed:** Which call sites move, Chat UX while a tool runs, Settings save UX

**Areas offered but not selected:** Enforcement & proof — routed to Claude's Discretion in
CONTEXT.md with a recommendation (a new check separate from `assertEdt()`, and a deterministic
rather than wall-clock SC3 assertion).

---

## Which call sites move

### Q1 — How wide does the fix go?

| Option | Description | Selected |
|--------|-------------|----------|
| All 3 ChatPanel sites | Chain (`:2856`), `openToolDialog` (`:1010`), `/tool` (`:2319`). One shared off-EDT path with one lifecycle. Also fixes a real bug: Burp throws on EDT `sendRequest`, so HTTP tools from the dialog or `/tool` are broken today, not slow. | ✓ |
| Chain path only | Literal SC1. Smallest diff. Cost: two user-originated sites stay frozen and stay broken for HTTP tools; three call sites under two threading rules. | |
| All 3 + the transport | Adds `MontoyaHttpTransport.execute:79-92`, which offloads then blocks the EDT on `task.get(timeout + 5s)`. REL-05 does say "backend HTTP call". Cost: a second subsystem with its own callers. | |

**User's choice:** All 3 ChatPanel sites → **D-01**
**Notes:** The transport path was moved to Deferred Ideas rather than dropped.

### Q2 — Where does the guarantee live?

| Option | Description | Selected |
|--------|-------------|----------|
| Both: helper + door guard | Shared dispatch helper in `ChatPanel` AND a fail-fast EDT check inside the executor. Helper makes it work, guard makes it stay. Precedent: Phase 22's non-defaulted `secTier` and required `origin` parameter. | ✓ |
| Shared helper only | One `runToolOffEdt(...)` seam, three callers. Cost: nothing stops a fourth parse-and-execute path — the gap SC5 closed for the trust boundary, reopened for threading. | |
| Door guard only | Guard in the executor, each call site arranges its own dispatch. Cost: three threading idioms in one file; the guard finds the work instead of holding the line. | |

**User's choice:** Both → **D-02**

### Q3 — What does the guard do on an EDT caller in production?

| Option | Description | Selected |
|--------|-------------|----------|
| Throw | `IllegalStateException`, per CONVENTIONS.md "fail loudly to the caller via exceptions". Fires in shipped Burp where `assert()` does not — the substantive answer to SC1's no-`-ea` clause. Cost: a missed site returns an error instead of freezing. | ✓ |
| Log to error, then proceed | `logToError` and run anyway. Cost: the no-op assert in a different costume; "never" degrades to "usually". | |
| Throw in tests, log in production | Hard in CI, soft in the field. Cost: a new tunable, and the field never enforces. | |

**User's choice:** Throw → **D-03**

### Q4 — What actually runs the tool?

| Option | Description | Selected |
|--------|-------------|----------|
| Daemon `Thread` per call | Literally `MainTab.kt:190-215`'s idiom. SC6 satisfied by construction; no lifecycle to own. Ordering within a chain is already structural. Cost: unbounded in principle, while REL-07 deletes unbounded thread creation elsewhere. | ✓ |
| Single-thread executor in ChatPanel | Serialises everything, gives a real cancellation handle; `AgentSupervisor.kt:76` is precedent. Cost: arguably the new idiom SC6 warns against, plus an unload lifecycle. | |
| Bounded pool in ChatPanel | Lets a chain and a manual `/tool` overlap safely. Cost: buys interleaving SC3's ordering clause must then defend against. | |

**User's choice:** Daemon `Thread` per call → **D-05**
**Notes:** External-MCP `runBlocking` (`McpToolExecutorImpl.kt:1126`) is satisfied transitively —
it sits inside the executor. The two other `runBlocking` sites in `ExternalMcpClientManager.kt`
(`:408`, `:442`) went to Claude's Discretion for an EDT-reachability check.

---

## Chat UX while a tool runs

Grounding measurement presented before the questions: `setSendingState(false)` is queued at
`ChatPanel.kt:658`, **before** `maybeExecuteToolCall` is queued at `:741` — so today the panel is
already idle-looking while the tool freezes it. Freeing the EDT makes it idle *and* live.

### Q1 — What state is the panel in during a tool run?

| Option | Description | Selected |
|--------|-------------|----------|
| Busy — reuse `setSendingState(true)` | Send hidden, cancel shown, input disabled (`:941-946`) — the state a backend turn already uses. Closes the new-message-during-execution race D-08 has no answer for, since an executing call cannot be retired. | ✓ |
| Stay idle, as today | No new state machine. Cost: the race above becomes reachable once the freeze stops masking it. | |
| A distinct tool-running state | Own indicator naming the running tool. Most honest. Cost: a third state and new chrome in a 3024-line file. | |

**User's choice:** Busy → **D-06**

### Q2 — What does the (currently inert) cancel button do?

| Option | Description | Selected |
|--------|-------------|----------|
| Stop the chain, discard the result | The tool is not interruptible — Montoya's `sendRequest` takes no cancellation. Worker completes, result dropped, no followup turn. Corollary: the SC3 audit event must still fire, with the cancel recorded. | ✓ |
| Hide cancel during the tool run | No misleading UI. Cost: no way out of a slow `scan_audit_start`; the button appearing and vanishing reads as a glitch. | |
| Interrupt the worker thread | Would unblock the `runBlocking` external path. Cost: Montoya ignores interrupts — a cancel that works for one tool family and silently not for the rest. | |

**User's choice:** Stop the chain, discard the result → **D-07**
**Notes:** Raised because `cancelBtn` (`:498`) calls `cancelInFlightRequest()`, which takes
`inFlightConnection` — already cleared at `:654` before the tool starts.

### Q3 — Teardown of a mid-execution call

| Option | Description | Selected |
|--------|-------------|----------|
| Same as cancel — drop the result | One supersede mechanism for cancel, session delete, project change and unload; shaped on the existing `InFlightConnectionTracker` `take()`/`clearIfMatches()`, already tested. Daemon thread never blocks unload. | ✓ |
| Join the worker at unload | `shutdown()` already uses `invokeAndWait`. Cost: puts a bounded blocking wait back on the EDT — the exact shape of `future.get(10s)` this phase removes. | |
| Do nothing | Cheapest. Cost: the result body can run against a disposed panel or torn-down classloader; a sixth teardown path left open after Phase 22 closed five. | |

**User's choice:** Same as cancel → **D-08**

### Q4 — Global or per-session busy state?

| Option | Description | Selected |
|--------|-------------|----------|
| Accept the global lock | `setSendingState` already locks the whole panel for a backend turn — no new rule, no divergence between the two halves of one chain. | ✓ |
| Scope busy state to the session | Matches D-08's stance that sessions stay independently usable. Cost: `setSendingState` becomes session-aware and must be reapplied on switch — and it necessarily changes backend-turn behaviour, unscoped here. | |
| Global lock, but say why | Keep the lock, name the running tool in the placeholder. Cost: new chrome and strings for a state lasting seconds. | |

**User's choice:** Accept the global lock → **D-09**
**Notes:** Surfaced during the answer: `isSending` (`ChatPanel.kt:110`) is written at `:942` and
never read — dead `@Volatile` state, recorded in Deferred Ideas.

---

## Settings save UX

### Q1 — What can the user do during an async save?

| Option | Description | Selected |
|--------|-------------|----------|
| Disable Save, leave fields editable | The existing `updateSaveFeedback` banner already shows "Saving settings…", so no new chrome. Blocks the double-save race without freezing the panel. | ✓ |
| Disable the whole panel | Unambiguous; nothing edited into an inconsistent state mid-apply. Cost: reproduces the frozen feel this phase removes, and needs a sweep over every tab. | |
| Change nothing | Smallest diff. Cost: two clicks queue two overlapping saves, each with its own MCP stop/start; last-writer-wins on MCP bind state. | |

**User's choice:** Disable Save, leave fields editable → **D-10**

### Q2 — Where does the EDT boundary land inside `applyAndSaveSettings` (`:456-505`)?

| Option | Description | Selected |
|--------|-------------|----------|
| Whole body off, UI tail marshalled back | One worker; `onSettingsChanged`, `refreshPassiveAiStatus`, `refreshActiveAiStatus`, `updateProfileWarnings`, `updateRiskWarnings` return via `invokeLater`. One boundary a reader can see and a test can check. | ✓ |
| Only SC4's three named calls | Literal SC4, smallest behaviour change. Cost: three interleaved thread hops in one function; the next person adding a line has to guess which half they are in. | |
| Everything off, including the UI tail | Cost: five marshalling points instead of one, each a place to forget, for nothing. | |

**User's choice:** Whole body off, UI tail marshalled back → **D-11**
**Notes:** Presented with the measurement that `PassiveAiScanner.kt:77-79` and
`ActiveAiScanner.kt:113,119` are plain `var` while neighbouring fields are `@Volatile` — those
writes already race with scanner threads today, so moving the writer changes nothing either way.
Recorded in Deferred Ideas.

### Q3 — How does the user learn a background save failed?

| Option | Description | Selected |
|--------|-------------|----------|
| Keep both, marshalled back | Same banner and same `JOptionPane` as today. A failed settings save on a security tool must not be missable; the banner auto-resets after 5000 ms. Partial failure is already possible in today's sequential code — this adds a thread, not a failure class. | ✓ |
| Banner only for the async path | Nothing pops over a tab the user moved to. Cost: a 5-second banner is missable, on the exact path SC4 is about. | |
| Modal only when state is inconsistent | Most proportionate in principle. Cost: needs a definition of "inconsistent" `applyAndSaveSettings` cannot compute — it does not track how far it got. | |

**User's choice:** Keep both → **D-12**

### Q4 — `restoreDefaultsWithConfirmation` reports success before the work finishes

| Option | Description | Selected |
|--------|-------------|----------|
| One async path, both callers use it | `applyAndSaveSettings` takes a completion callback; each caller supplies its own success/failure message. D-10's disabled state covers both, so neither can start while the other is in flight. | ✓ |
| Keep restoreDefaults synchronous | It is behind a confirmation dialog, so the user opted into a pause. Cost: SC4 says saving Settings does not block the EDT, and this path saves settings — the one remaining 10-second freeze, two clicks away. | |
| Fire-and-forget, drop the completion message | Cost: discards the failure surfacing decided one question earlier. | |

**User's choice:** One async path, both callers use it → **D-13**

---

## Claude's Discretion

- **Enforcement mechanism and its relationship to `assertEdt()`** — recommendation: a separate check
  in the MCP package, leaving `ChatPanel.assertEdt()` (`:783`) byte-identical since QUAL-07 /
  Phase 26 owns it and it enforces the opposite direction.
- **How SC3 is asserted** — recommendation: assert the invariant (entered off-EDT, EDT processed
  queued work mid-call, eight results in submission order), not wall-clock responsiveness.
  `.planning/codebase/CONCERNS.md` records the project already paying for a timing-based assertion.
- **Whether the transcript shows anything between approval and `Tool result:`** — recommendation:
  nothing beyond D-06's busy state; the approval card is already the visible marker.
- **`ExternalMcpClientManager.kt:408,442` `runBlocking` EDT-reachability** — research to establish,
  planning to decide in-scope vs deferred.
- **`KtorMcpServerManager.stop()` blocking or not** — raised at wrap-up, recommendation confirmed by
  reading `McpSupervisor.kt:132-141`: keep it blocking, move only the caller. Locked as **D-14**.

## Deferred Ideas

- `MontoyaHttpTransport.execute:79-92` — offloads `sendRequest` then blocks the EDT on
  `task.get(timeout + 5s)`; ~8 s via the pre-send LM Studio / Ollama health check (#80).
- Non-`@Volatile` scanner settings fields — `PassiveAiScanner.kt:77-79`, `ActiveAiScanner.kt:113,119`.
- `isSending` (`ChatPanel.kt:110`) is dead state — written at `:942`, never read.
- `ExternalMcpClientManager.kt:408,442` `runBlocking`, pending the reachability check.
- Upgrading `assertEdt()` from a production no-op — QUAL-07 / Phase 26.
- `ChatPanel.kt` mega-file split — Out of Scope for v0.10.0.
- A per-call progress indicator in the transcript — considered under D-06, left out.
