# Phase 23: EDT Confinement & UI Responsiveness - Context

**Gathered:** 2026-08-20
**Status:** Ready for planning

<domain>
## Phase Boundary

The Burp UI stays responsive during an agent tool chain and during a Settings save. Requirement
REL-05 (Findings F4 and F8, **high / medium**). No new capability — the same operations, on a
different thread, with the EDT freed and the REL-01 confinement contract intact.

**The two defects:**

- **F4 — tool execution on the EDT.** `ui/ChatPanel.kt` calls `McpToolExecutor.executeTool`
  synchronously from three sites. Inside, `api.http().sendRequest(...)`
  (`mcp/tools/McpToolExecutorImpl.kt:191`, `:237`) and `runBlocking { manager.callTool(...) }` for
  external MCP servers (`:1126`) both block the calling thread. With `MAX_AUTO_TOOL_ITERATIONS = 8`
  the UI is frozen throughout. Phase 22 reported this four times and deliberately did not fix it
  (`22-07-SUMMARY.md:318`, `22-08-SUMMARY.md:231`, `22-09-SUMMARY.md:181`, `:215`), noting that the
  approval card makes the freeze *more* visible because the click that triggers it also comes from
  the EDT.

- **F8 — Settings save blocks for up to 10 seconds.** `ui/SettingsPanelSettingsIO.kt:456`
  (`applyAndSaveSettings`) runs on the EDT and calls `mcpSupervisor.applySettings` (`:465`) →
  `McpSupervisor.stop()` (`:132`) → `KtorMcpServerManager.stop()`'s `future.get(10, TimeUnit.SECONDS)`
  (`KtorMcpServerManager.kt:270`). The same method also does `settingsRepo.save()` (`:458`) and
  `backends.reload()` (`:462`) on the EDT.

**Measured while scouting, and it reframes SC2 — this is not only a responsiveness bug.** Burp
throws *"Extensions should not make HTTP requests in the Swing event dispatch thread"* when
`sendRequest` runs on the EDT; `MontoyaHttpTransport.kt:79-92` exists solely to dodge that
exception, and its comment records issue #80. So `http1_request` / `http2_request` invoked from the
chat chain today do not merely freeze the UI — they are expected to **fail outright**. Research
should confirm this against a running Burp; if it holds, SC2 has a defect to demonstrate red, not
just a latency to improve.

**In scope:** all three `ChatPanel` `executeTool` call sites; the off-EDT dispatch mechanism and its
supersede/teardown lifecycle; the busy-state and cancel semantics that follow; the `applyAndSaveSettings`
EDT boundary and the async feedback contract for both of its callers; a production-effective check
that tool execution is never on the EDT; the tests that prove SC1–SC6.

**Out of scope:** `MontoyaHttpTransport.execute`'s offload-and-block (deferred below); upgrading the
existing `assertEdt()` (`ChatPanel.kt:783`) — QUAL-07 / Phase 26 owns that; making
`KtorMcpServerManager.stop()` itself non-blocking (see D-13); the `ChatPanel.kt` mega-file split,
explicitly Out of Scope for v0.10.0 and now at 3024 lines; the SEC-06 gate's behaviour, which this
phase must preserve and not re-open.

</domain>

<decisions>
## Implementation Decisions

### Scope of the EDT move — which call sites (SC1, SC2)

- **D-01:** **All three `ChatPanel` `executeTool` call sites move**, not only the chat chain:

  | Line | Origin | Reached from |
  |---|---|---|
  | `ChatPanel.kt:1010` | `ToolCallOrigin.UserDialog` | `openToolDialog` → `ToolInvocationDialog` |
  | `ChatPanel.kt:2319` | `ToolCallOrigin.UserSlashCommand` | `/tool <name> <json>` |
  | `ChatPanel.kt:2856` | `approved.origin` | `executeApprovedToolCall`, the SEC-06-gated chain |

  SC1's text names only the chain, and the chain alone would satisfy it literally. Rejected on two
  grounds. First, the two user-originated sites are not merely slower — per the measurement above
  they are expected to **fail** for HTTP tools, so leaving them is leaving a bug, not an
  optimisation. Second, three call sites under two different threading rules is precisely the shape
  the maintainer has rejected in each of the last three phases: a fix that satisfies the stated
  criterion while the real path stays open is not a fix. **Accepted cost:** a wider diff than SC1
  strictly demands, in a file already at 3024 lines.

- **D-02:** **The guarantee lives in two places at once — a shared dispatch helper AND a fail-fast
  check at the executor's door.** The helper makes it work; the guard makes it stay. Direct
  precedent, same milestone: Phase 22's D-03 non-defaulted `secTier` and its SC5 required `origin`
  parameter both chose a mechanism a future edit cannot silently undo over one that merely happens
  to be correct today. Rejected: helper only (nothing stops a fourth parse-and-execute path calling
  `executeTool` directly — exactly the gap SC5 closed for the trust boundary, reopened for the
  threading one) and guard only (three threading idioms in one file, and the guard would be used to
  *find* the work rather than to hold the line after it).

- **D-03:** **The guard throws.** `IllegalStateException`, matching CONVENTIONS.md §Error Handling —
  *"fail loudly to the caller via exceptions; never swallow silently"*. This is the substantive
  answer to SC1's *"the existing `assertEdt()` is a no-op in production, so the new check must not
  rely on `-ea`"*: a throw fires in shipped Burp, where `assert()` does nothing. The chain site
  already wraps in `runCatching` (`:2856`), so a missed site surfaces as a tool error in the
  transcript rather than as a crash. Rejected: `logToError` and proceed (the no-op assert in a
  different costume — REL-05's *"no ... happens on the Swing EDT"* would degrade to "usually", and a
  log nobody reads is not a control) and throw-in-tests / log-in-production (a new tunable, two
  behaviours, and the field — where the real slow tools live — is the half that never enforces).
  **Accepted cost:** if a call site is ever missed, a tool that previously froze the UI now returns
  an error.

- **D-04:** **Placement recommendation for the guard: `McpToolExecutor.executeToolResult`
  (`McpToolExecutorImpl.kt:137`), not `executeTool` (`:1052`).** Verified by reading both:
  `executeTool` delegates to `executeToolResult`, and `executeToolResult` is also what the MCP-server
  path calls (`mcp/tools/McpToolHandlers.kt:129`). It is therefore the single convergence point for
  everything REL-05 names — the chat path, the server path, `routeExternalToolCall`'s `runBlocking`
  (`:1126`), and both `api.http().sendRequest` sites (`:191`, `:237`). One guard there covers the
  whole requirement; a guard on `executeTool` covers only the chat half. Free for the server path,
  which runs on Ktor coroutines and is already off the EDT.

  **The "confirm before locking" check has been run, and the answer is YES — one caller does.**
  `ChatPanelToolGateTest.slashCommandPathIsNotDoublePrompted` (`:350`) drives the real `ChatPanel`
  through `ChatPanelTestHarness.sendUserMessage`, which wraps the send in `SwingUtilities.invokeAndWait`
  (`ChatPanelTestHarness.kt:193`), so the `/tool` path reaches `executeToolResult` **on the EDT today**.
  Consequence for the plan, and it is a sequencing constraint rather than a blocker: **the throwing
  guard and D-01's three call-site moves must land in the same commit.** Guard first = that test goes
  red; moves first = the guard has nothing to catch. No production caller was found on the EDT other
  than the three D-01 sites.

- **D-05:** **Mechanism: a daemon `Thread` per call plus `SwingUtilities.invokeLater`**, following
  the `MainTab.kt:190-215` health-check idiom SC6 names. **Correction, measured 2026-08-20:**
  `MainTab.kt:193` is a bare `Thread { … }` and does **not** set `isDaemon`. The daemon flag must be
  set explicitly — `Thread(task, "…").apply { isDaemon = true }`, the form used at
  `MontoyaHttpTransport.kt:85`. This is load-bearing, not cosmetic: D-08's "a daemon thread never
  blocks unload" is only true if the flag is actually set. Name the thread too, so a stuck worker is
  identifiable in a thread dump. No executor, no lifecycle for `ChatPanel` to
  own, nothing to shut down — and ordering inside a chain is already structural, since one card is
  pending at a time and the next turn only starts from the previous result's callback. Rejected: a
  single-thread executor owned by `ChatPanel` (would serialise a `/tool` racing the chain and give a
  real cancellation handle, and `AgentSupervisor.kt:76` sets a precedent — but it is arguably the
  "new concurrency idiom" SC6 warns against, and it adds an unload lifecycle to get right) and a
  bounded pool (buys interleaving that SC3's ordering clause then has to defend against). Also
  binding here: **CONVENTIONS.md:95 — do not introduce coroutines outside the MCP package.**
  **Accepted cost:** unbounded in principle. A user firing `/tool` during a chain gets concurrent
  threads, and REL-07 / Phase 24 is concurrently deleting unbounded thread creation elsewhere
  (`newCachedThreadPool` at `App.kt:37`, `ActiveAiScanner.kt:73`). Judged acceptable because the
  reachable concurrency here is one chain plus one manual invocation, not scan-load fan-out.

### Chat behaviour while a tool runs (SC3, SC5)

- **D-06:** **The panel goes busy for the duration of the tool call — reuse `setSendingState(true)`
  (`ChatPanel.kt:941-946`).** Send button hidden, cancel shown, input disabled: the same state a
  backend turn already uses, because from the user's side a chain is one continuous turn. Grounded
  in a measurement: `setSendingState(false)` is queued at `:658` **before** `maybeExecuteToolCall`
  is queued at `:741`, so today the panel is already back to idle while the tool executes — it looks
  idle and is frozen. Freeing the EDT would make it idle **and live**, which creates a genuinely new
  race: Phase 22's D-08 retires a *parked* card when a new message is typed, but an *executing* call
  cannot be retired — it is already running against Burp. Going busy makes that case unreachable
  rather than handled. Rejected: stay idle (see the race) and a distinct third tool-running state (a
  third state in a panel that has two, plus new chrome and strings, for something that lasts
  seconds).

- **D-07:** **Cancel stops the chain and discards the result; the tool itself is not interrupted.**
  Montoya's `sendRequest` takes no cancellation, so the honest contract is: the worker runs to
  completion, its result is dropped, no followup turn is sent, the panel returns to idle. This is
  required by D-06 — `cancelBtn` (`:498`) calls `cancelInFlightRequest()`, which takes
  `inFlightConnection`, and that was already cleared at `:655` before the tool started, so a busy
  panel would otherwise show an **inert** cancel button. **Load-bearing corollary: the call did reach
  Burp, so Phase 22's SC3 audit event must still fire, with the cancellation recorded.** A cancelled
  call must not become an unlogged one — that would put a hole in SEC-06's record exactly where a
  user interrupted something. Rejected: hiding cancel during the run (a button that appears and
  vanishes between chain steps reads as a glitch, and a slow `scan_audit_start` leaves no way out)
  and `Thread.interrupt()` (works for the `runBlocking` external path, silently does nothing for
  Montoya — a cancel that works sometimes is worse than one that states its limits).

- **D-08:** **Session delete, project change and extension unload use the same supersede path as
  cancel.** One mechanism for all four exits: the worker finishes, its `invokeLater` body finds it
  has been superseded and does nothing, `onCompleted` is discharged, no followup turn is sent. The
  shape already exists in this file and is already tested — `InFlightConnectionTracker`'s
  `take()` / `clearIfMatches()` (`ChatPanelConcurrencyTest.kt:20-60`) does exactly this for the
  backend turn, and an analogous tracker for the running tool is the precedented form. A daemon
  thread never blocks unload. Rejected: joining the worker at unload (`shutdown()` at `:1443`
  already uses `invokeAndWait`, so it is tempting — but it puts a bounded blocking wait back on the
  EDT, which is the exact shape of `future.get(10s)` this phase exists to remove) and doing nothing
  (the result body can run against a disposed panel or a torn-down classloader; Phase 22 spent real
  effort making all five teardown paths discharge cleanly and this would be the sixth left open).

- **D-09:** **The busy state stays global to `ChatPanel`, not per session.** `sendBtn`, `cancelBtn`,
  `inputArea` and `isSending` are all `ChatPanel` fields (`:104-111`), so a backend turn already
  locks every session; a tool run behaving identically needs no new rule. Rejected: session-scoping
  it (consistent with D-08's stance that sessions stay independently usable, but `setSendingState`
  would have to become session-aware and be reapplied on every session switch — and that
  necessarily changes backend-turn behaviour, which REL-05 did not scope). Noted for the planner:
  `isSending` (`:110`) is written at `:942` and **never read** — dead `@Volatile` state; remove it or
  use it while in the file.

### Settings save (SC4)

- **D-10:** **Save disables while the work is in flight; every other field stays editable.** The
  existing `updateSaveFeedback` banner already renders "Saving settings…"
  (`SettingsPanelActions.kt:65`), so the state is visible with no new chrome, and disabling the
  button blocks the double-save race that going async introduces. Rejected: disabling the whole
  panel (reproduces the frozen feel this phase removes, and needs an enable/disable sweep over every
  tab) and changing nothing (two clicks queue two overlapping saves, each with its own MCP
  stop/start, and the second can finish first — last-writer-wins on a settings write that includes
  MCP bind state).

- **D-11:** **The whole `applyAndSaveSettings` body moves to one background worker; the Swing tail
  marshals back via `invokeLater`.** The tail is `onSettingsChanged?.invoke(updated)`,
  `refreshPassiveAiStatus()`, `refreshActiveAiStatus()`, `updateProfileWarnings()`,
  `updateRiskWarnings()`. One boundary a reader can see and a test can check. Rejected: moving only
  SC4's three named calls (`settingsRepo.save`, `backends.reload`, `mcpSupervisor.applySettings`) —
  three interleaved thread hops inside one function, and it leaves the next person adding a line to
  guess which half they are in, the same two-rules-in-one-function shape D-01 rejected; and moving
  everything with per-call `invokeLater` (five marshalling points instead of one, each a place to
  forget). Note: `currentSettings()` reads Swing components and is called by `saveSettings()`
  (`SettingsPanelActions.kt:66`), so the snapshot is already taken on the EDT before dispatch —
  preserve that ordering.

  **Recorded honestly, because the planner will meet it:** `PassiveAiScanner.kt:77-79`
  (`rateLimitSeconds`, `scopeOnly`, `maxSizeKb`) and `ActiveAiScanner.kt:113,119` (`maxConcurrent`,
  `scanMode`) are plain `var` while the fields immediately below them are `@Volatile`. Those writes
  already race with scanner threads today; moving the writer from the EDT to a worker makes it
  neither better nor worse. Do not fix it here — record it (see Deferred).

- **D-12:** **Failure keeps both surfaces, marshalled back to the EDT** — the same
  `"Save failed: …"` banner and the same `JOptionPane` as today (`SettingsPanelActions.kt:69-77`).
  A failed settings save on a security tool must not be missable, and the banner alone auto-resets
  after 5000 ms. Rejected: banner only (a 5-second banner is genuinely missable, and "my MCP server
  did not restart" becomes silent on the exact path SC4 is about) and modal-only-when-inconsistent
  (needs a definition of "inconsistent" the current code cannot compute — `applyAndSaveSettings`
  does not track how far it got). Worth stating in the plan so it is not mistaken for a regression:
  **partial failure is already possible today** in the same sequential code (repo saved, MCP restart
  threw); this change adds a thread, not a new failure class.

- **D-13:** **One async path, used by both callers.** `applyAndSaveSettings` takes a completion
  callback; `saveSettings()` and `restoreDefaultsWithConfirmation()` each supply their own success
  and failure message. D-10's disabled state covers both, so neither can start while the other is in
  flight. Motivated by a concrete lie the naive change would introduce:
  `restoreDefaultsWithConfirmation` (`SettingsPanelActions.kt:80-93`) reports
  `"Defaults restored and applied."` on the line *after* `applyAndSaveSettings` returns, which is
  false the moment that call is async. Rejected: keeping restore-defaults synchronous (SC4 says
  "Saving Settings does not block the EDT" and this path saves settings — it would be the one
  remaining 10-second freeze, reachable in two clicks) and fire-and-forget (discards D-12).

- **D-14:** **`KtorMcpServerManager.stop()` stays blocking; only the caller moves off the EDT.**
  Verified rather than assumed: `McpSupervisor.stop()` (`McpSupervisor.kt:132-141`) calls
  `serverManager.stop { … }` and then relies on it having completed before it resets
  `restartAttempts` / `takeoverAttempts`, stops `stdioBridge` and clears `ScannerTaskRegistry` /
  `CollaboratorRegistry`. `stop()`'s callback fires on the **calling** thread today, so making it
  non-blocking would reorder that teardown for every caller, including extension unload. SC4 only
  requires that the EDT not block on the wait — moving the caller achieves that without touching a
  bounded-shutdown guarantee REL-02 / Phase 17 deliberately installed
  (`KtorMcpServerManager.kt:250-283`, including its RESTART-SAFE comment on why the executor must
  not be shut down there).

### Claude's Discretion

Left to research and planning. Each carries a recommendation to be **confirmed, not assumed**.

- **Enforcement mechanism and its relationship to the existing `assertEdt()`.** D-03 locks *throw*;
  where the throwing helper lives and what it is called are open. *Recommendation:* a new, separate
  check rather than a change to `ChatPanel.assertEdt()` (`:783`). The existing assert enforces
  *must be on the EDT* for the REL-01 session maps and is owned by QUAL-07 / Phase 26; the new one
  enforces the **inverse** — *must not be on the EDT* — for tool execution, and lives in the MCP
  package next to what it guards (D-04). Conflating them would pull Phase 26's work forward and
  give one name two opposite meanings. SC5 is satisfied by leaving `assertEdt()` and its call sites
  byte-identical, which is also the cheapest way to prove no REL-01 regression — Phase 22 used
  exactly that evidence (`22-07-SUMMARY.md:95`, an `invokeLater` count of 10 → 10).

- **How SC3 is asserted deterministically.** *Recommendation:* do not measure wall-clock
  responsiveness. `.planning/codebase/CONCERNS.md` §"`RedactionTest` has a wall-clock flake"
  documents this project already paying for a timing-based assertion, repeatedly, in investigation
  time. Assert the **invariant** instead: that `executeToolResult` was entered on a non-EDT thread
  (capturable from the test double), that the EDT processed queued work while a blocking tool double
  was mid-call, and that eight results arrive in submission order. `ChatPanelToolGateTest` +
  `ChatPanelTestHarness` already drive a **real** `ChatPanel` headlessly with an EDT drain helper and
  a `sendChat` stub — Phase 22 built exactly the fixture this needs, and
  `CONCERNS.md` §"UI layer has no integration tests" records that the "high setup cost" objection was
  measured and is false.

- **Whether the transcript shows anything between approval and `Tool result:`.**
  *Recommendation:* nothing new beyond D-06's busy state. The approval card is already sitting in
  the transcript as the visible marker, and D-06 gives a panel-level signal; a per-call spinner is
  new chrome for a state that usually lasts under a second.

- ~~**`mcp/external/ExternalMcpClientManager.kt:408` and `:442`**~~ — **RESOLVED 2026-08-20, no longer open.**
  Both sites are inside `fun stop()` (`:395`), and nothing in `src/main` constructs an
  `ExternalMcpClientManager` at all — the only constructor calls are in
  `src/test/kotlin/…/ExternalMcpClientManagerTest.kt`. Not reachable from a Settings button or any
  other EDT path. Out of scope for REL-05; stays in Deferred.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### The defects and their evidence
- `.planning/notes/2026-08-05-code-review.md` §"F4" — MCP tool execution on the EDT, with file:line
  anchors. **Read this first.**
- `.planning/notes/2026-08-05-code-review.md` §"F8" — Settings save blocking the EDT for 10 s.
- `.planning/phases/22-agent-tool-call-trust-boundary/22-07-SUMMARY.md` §Issue 1 (`:318`) and
  §"For Phase 23" (`:362`) — the handoff. States that `executeTool` remains on the EDT at three call
  sites and that the approval card makes the freeze more visible, not less.
- `.planning/phases/22-agent-tool-call-trust-boundary/22-09-SUMMARY.md:215` — ADR-15 makes **no**
  claim about EDT behaviour, so this phase requires no ADR amendment.

### Requirements and success criteria
- `.planning/REQUIREMENTS.md` §"Reliability & Concurrency (REL)" — REL-05, and the ordering
  constraint at `:10` explaining why 22 and 23 are sequential on the same method.
- `.planning/ROADMAP.md` §"Phase 23" — the six success criteria.

### Prior locked decisions that constrain this phase
- `.planning/phases/22-agent-tool-call-trust-boundary/22-CONTEXT.md` §decisions — D-06 (the inline
  card, chosen partly *because* it forces the callback-driven shape REL-05 needs), D-08 (the
  no-timer pending-card lifecycle and its implicit-denial triggers, which D-06/D-08 here extend to a
  new *executing* state), D-11 to D-13 (denial semantics and the monotone iteration counter, which
  D-07's cancel must not perturb).
- `.planning/phases/17-reliability-concurrency-hardening/17-CONTEXT.md` §"REL-01 — EDT confinement" —
  the four `@GuardedBy("EDT")` session maps stay EDT-confined and are **not** converted to
  thread-safe collections; off-EDT mutation routes via `SwingUtilities.invokeLater`. SC5 protects
  this; it is the contract this phase must not regress.
- `.planning/phases/17-reliability-concurrency-hardening/17-CONTEXT.md` §"REL-02" — the bounded MCP
  shutdown that D-14 declines to re-open.
- `.planning/codebase/CONVENTIONS.md:92-95` — coroutines are used **only** in the MCP layer;
  everywhere else use `java.util.concurrent`. Binding on D-05.
- `.planning/codebase/CONVENTIONS.md` §"Swing UI Patterns (ADR-2)" and §"Error Handling" — all Swing
  mutation via `invokeLater` from non-EDT threads; fail loudly via exceptions. D-03 and D-11 follow.
- `.planning/codebase/CONCERNS.md` §"`RedactionTest` has a wall-clock flake that surfaces under CPU
  load" — why SC3 must not be asserted on wall-clock time.
- `.planning/codebase/CONCERNS.md` §"UI layer has no integration tests" (update dated 2026-08-14) —
  the headless-`ChatPanel` cost was measured and is low; do not cite setup cost to skip UI coverage.
  Also records that modal dialogs (`JOptionPane`) genuinely are not headless-testable, which bounds
  how D-12 can be tested.
- `CLAUDE.md` §Constraints — English only in code and comments; audit defaults unchanged.

### Files this phase changes
- `src/main/kotlin/com/six2dez/burp/aiagent/ui/ChatPanel.kt:1010`, `:2319`, `:2856` — the three
  `executeTool` call sites (D-01).
- `src/main/kotlin/com/six2dez/burp/aiagent/ui/ChatPanel.kt:941-946` — `setSendingState`, reused by
  D-06.
- `src/main/kotlin/com/six2dez/burp/aiagent/ui/ChatPanel.kt:956-968` — `cancelInFlightRequest`, whose
  contract D-07 extends.
- `src/main/kotlin/com/six2dez/burp/aiagent/ui/ChatPanel.kt:1443-1468` — `shutdown()`, and `:1475`
  `clearInMemorySessionState()` — the teardown paths D-08 joins.
- `src/main/kotlin/com/six2dez/burp/aiagent/ui/ChatPanel.kt:783` — `assertEdt()`. **Leave
  byte-identical**; it is Phase 26's (QUAL-07) and its unchanged state is SC5's evidence.
- `src/main/kotlin/com/six2dez/burp/aiagent/mcp/tools/McpToolExecutorImpl.kt:137` —
  `executeToolResult`, D-04's recommended guard site; `:1052` `executeTool` delegates to it.
- `src/main/kotlin/com/six2dez/burp/aiagent/ui/SettingsPanelSettingsIO.kt:456-505` —
  `applyAndSaveSettings` (D-11, D-13).
- `src/main/kotlin/com/six2dez/burp/aiagent/ui/SettingsPanelActions.kt:64-93` — `saveSettings` and
  `restoreDefaultsWithConfirmation` (D-10, D-12, D-13).
- `src/test/kotlin/com/six2dez/burp/aiagent/ui/ChatPanelTestHarness.kt` and
  `ChatPanelToolGateTest.kt` — the existing real-`ChatPanel` headless fixture SC3's test extends.
  **Two things in it break the moment execution goes async, both measured 2026-08-20:**
  (a) `drainEdt()` (`ChatPanelTestHarness.kt:211`) is `repeat(times) { SwingUtilities.invokeAndWait { } }`
  — it drains the EDT queue and knows nothing about a daemon worker, so it cannot remain the sole
  synchronisation point; (b) `ChatPanelToolGateTest.kt:358`'s
  `verify(h.api.proxy(), times(1)).history()` fires immediately after `drainEdt()` and becomes racy.
  The harness needs a worker-aware await before any SC3 assertion can be trusted. Budget for this —
  it is the mechanism SC3's "deterministic, not wall-clock" recommendation depends on.

  **Constraint discovered in phase research, 2026-08-20 — it rules out the obvious shape.**
  `McpToolExecutor` is an `object` singleton (`mcp/tools/McpToolExecutorImpl.kt:45`) with no
  interface, so **there is no executor test double to put a latch in** — phrasing this document and
  the AI-SPEC both used. The await must hang off the new dispatch helper (a completion observer the
  production code exposes for tests), not off a mock of the executor. `23-RESEARCH.md` recommends the
  `AuditLogger.registerGlobalEmitter` install/clear pattern, which has precedent in the same test
  class.

### Read-only, but do not disturb
- `src/main/kotlin/com/six2dez/burp/aiagent/mcp/KtorMcpServerManager.kt:250-283` — the bounded
  `stop()` and its RESTART-SAFE comment (D-14).
- `src/main/kotlin/com/six2dez/burp/aiagent/mcp/McpSupervisor.kt:132-141` — `stop()`'s ordering
  dependency on `serverManager.stop()` having completed (D-14).
- `src/main/kotlin/com/six2dez/burp/aiagent/mcp/ToolApprovalGate.kt` — the SEC-06 gate. This phase
  changes *where* an approved call runs, never *whether* it runs.

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `ui/MainTab.kt:190-215` — `Timer { Thread { work } → SwingUtilities.invokeLater { render } }`. The
  idiom SC6 names and D-05 adopts verbatim. Copy the shape, not a new one.
- `InFlightConnectionTracker` (`ChatPanel.kt:69`, instance at `:108`) — `take()` / `clearIfMatches()` / `current()`, already
  covered by `ChatPanelConcurrencyTest.kt:20-60`. The precedented form for D-08's supersede check.
- `ChatPanel.setSendingState` (`:941-946`) — the two-state busy toggle D-06 reuses unchanged.
- `SettingsPanel.updateSaveFeedback(msg, color, resetMs)` — the existing inline save banner;
  D-10 and D-12 need no new surface, only new call timing.
- `UiActions.refreshBountyPromptCache` (`SettingsPanelSettingsIO.kt:460`) — **already** dispatches
  off-thread (BApp #231 finding 2). Precedent inside the very function D-11 restructures, and a
  reminder to check it is not double-dispatched afterwards.
- `src/test/kotlin/.../ui/ChatPanelTestHarness.kt` — deep-stub `MontoyaApi` + `AgentSupervisor`, a
  `sendChat` stub emitting a caller-supplied model response, a depth-first component finder and an
  **EDT drain** helper. `tasks.test` already sets `-Djava.awt.headless=true`.

### Established Patterns
- Swing mutation from a non-EDT thread always goes through `SwingUtilities.invokeLater`; `ChatPanel`
  contains **11** of them at HEAD (lines 647, 658, 673, 715, 723, 741, 1909, 1926, 2190, 2268, 2283),
  measured 2026-08-20. **Note the correction:** Phase 22 used a 10 → 10 count as its regression
  evidence (`22-07-SUMMARY.md:95`) and that was true at the time; an eleventh site landed later in
  the phase. Use the measured 11 as this phase's baseline, not the figure quoted in the Phase 22
  summary. This phase will raise it — state the new count and what each addition is for.
- `ChatPanel` owns **no** executor today. All asynchrony arrives from `supervisor.sendChat`
  (`supervisor/AgentSupervisor.kt:435`), whose callbacks fire on backend threads and are marshalled
  back. D-05 keeps it that way — no new owned lifecycle.
- Blocked/denied paths fail **closed** and return a typed result rather than throwing (Phase 20/22).
  D-07's discarded result and D-08's superseded result follow that: no exception, no dangling
  continuation.
- Security-relevant invariants are enforced by a test, not by inspection — `McpToolParityTest`'s
  shape. SC1's *"asserted by a test, not only by inspection"* expects the same.
- **Test naming is load-bearing in this repo — verified at source 2026-08-20.**
  `.github/workflows/build.yml:47` runs `./gradlew test -PexcludeHeavyTests=true`, and
  `build.gradle.kts:206-213` excludes `*IntegrationTest`, `*ConcurrencyTest`, `*BackpressureTest`,
  `*RestartPolicyTest` and `*SupervisionTest` under that flag. **A new test named `*ConcurrencyTest`
  would not run on the PR gate at all** — only in `nightlyRegressionTest` — so this phase's entire
  SC1–SC5 suite could ship green without ever executing on the cross-platform matrix, which is the
  one place a platform EDT difference would surface. `ChatPanelConcurrencyTest` is already in the
  excluded set, which makes the wrong name the *natural* one to reach for here. Suggested names that
  actually run: `ChatPanelEdtConfinementTest`, `McpToolExecutorEdtGuardTest`, `SettingsSaveAsyncTest`.

### Integration Points
- `ChatPanel.kt:741` — the `invokeLater` block that calls `maybeExecuteToolCall` and branches on
  `ToolCallOutcome`. Its three-valued outcome (`CHAINED` / `NOT_CHAINED` / `AWAITING_DECISION`) is
  Phase 22's, and D-06/D-07 add a fourth situation — *executing* — that the branch must account for
  without re-opening the gate.
- `ChatPanel.kt:2856` → `sendMessage(...)` handoff — where D-07's "no followup turn" and Phase 22's
  D-13 monotone iteration counter meet. A cancelled call must not silently refund an iteration.
- `toolDecisionReporter.report(...)` + `supervisor.aiRequestLogger?.log(...)` (`:2878` and `:2890`) — SC3's
  audit emission. D-07 requires this still fires on the cancel path.
- `SettingsPanelSettingsIO.kt:456` `settings = updated` — the first line of `applyAndSaveSettings`
  and a `SettingsPanel` field write. Decide explicitly whether it stays on the EDT with the snapshot
  or moves with the body.

</code_context>

<specifics>
## Specific Ideas

- **The maintainer's framing, consistent across Phases 20–22 and applied again here:** a fix that
  only satisfies the stated criterion while the real path stays open is not a fix, and a control a
  future edit can silently undo is not a control. That produced D-01 (all three call sites, not the
  one SC1 names), D-02 (helper **and** door guard) and D-03 (throw, not log).
- **Burp throws on EDT `sendRequest`.** This is the single most useful thing scouting turned up: it
  means SC2 has a demonstrable defect, not just a latency. It also explains why
  `MontoyaHttpTransport.kt:79-92` exists — and why that code is a **tempting wrong template**: it
  offloads the request to a daemon thread and then blocks the EDT on `task.get(timeout + 5s)`
  anyway. Dodging Burp's exception is not the same as freeing the UI. Do not copy that shape.
- **`executeToolResult` is the real door, `executeTool` is a wrapper.** Reading both is what turned
  D-04 from a guess into a recommendation with a reason.
- **SC3 deserves the Phase 20 / 21 / 22 acceptance-gate treatment:** a test that passes both before
  and after has not tested the defect. The tool-chain test must fail against today's `ChatPanel`.
- **SC5's cheapest proof is an unchanged file.** `assertEdt()` and its call sites staying
  byte-identical, plus a stated `invokeLater` count, is exactly the evidence Phase 22 produced for
  the same contract.
- Note for the planner: `ChatPanel.kt` is now **3024 lines** (up from 2248 before Phase 22) and its
  split remains Out of Scope for v0.10.0. Add a threading seam; do not restructure the file.

</specifics>

<deferred>
## Deferred Ideas

- **`MontoyaHttpTransport.execute`'s offload-and-block** (`backends/http/MontoyaHttpTransport.kt:79-92`)
  — runs `sendRequest` on a daemon thread, then blocks the calling EDT on
  `task.get(timeoutMs + 5000)`. Reached via the pre-send LM Studio / Ollama health check (issue #80),
  so up to ~8 s of EDT block. REL-05's text does say "backend HTTP call", so this is arguably in the
  requirement's spirit; it was scoped out to keep this phase on the two paths the success criteria
  actually name. Candidate for Phase 24 (REL-07) or a v0.11.0 reliability item.
- **Non-`@Volatile` scanner settings fields** — `PassiveAiScanner.kt:77-79` and
  `ActiveAiScanner.kt:113,119` are plain `var` written by the settings path and read on scanner
  threads, while every neighbouring field is `@Volatile`. A pre-existing visibility race that D-11
  neither creates nor worsens. Fix belongs with REL-07 / Phase 24 or QUAL-06 / Phase 26, not here.
- **`isSending` is dead state** — `ChatPanel.kt:110`, written at `:942`, never read. Trivially
  removable; flagged so it is a deliberate call rather than an oversight.
- **`mcp/external/ExternalMcpClientManager.kt:408,442` `runBlocking`** — outside `executeToolResult`'s path,
  both inside `fun stop()` (`:395`). Reachability checked 2026-08-20: **not EDT-reachable** — no
  `src/main` code constructs the manager, only tests do. Confirmed deferred.
- **Upgrading `assertEdt()` from a production no-op** — QUAL-07 / Phase 26, unchanged from Phase 22's
  deferral. This phase adds an inverse check elsewhere and deliberately leaves `assertEdt()` alone.
- **`ChatPanel.kt` mega-file split** — explicitly Out of Scope for v0.10.0; carried to the backlog.
- **A per-call progress indicator in the transcript** — considered under D-06 and left out. Revisit
  if real-world tool calls turn out to run long enough that a panel-level busy state reads as a hang.
- **Chat-originated HTTP tool calls bypass Burp target scope entirely — NOT this phase's, but do
  not lose it.** Verified at source 2026-08-20: `McpToolContext.scopeOnly` defaults to `false`
  (`mcp/McpToolContext.kt:44`), and `ChatPanel.buildToolContext` (`ui/ChatPanel.kt:3005-3022`) never
  passes it, while the MCP-server path does (`McpRuntimeContextFactory.kt:55` — `scopeOnly =
  settings.scopeOnly`). `McpScopeFilter.rejectIfOutOfScope` is wired into all six request-sending
  branches of `McpToolExecutorImpl` but is gated on that flag, so for a chat-originated
  `http1_request` / `http2_request` / `repeater_tab` / intruder call the **SEC-06 approval card is
  the only scope control that runs**. This may well be deliberate — the chat path is user-driven and
  scope-limiting it could block legitimate testing — but the asymmetry between the two context
  factories is undocumented either way. **Triage as a candidate requirement** (SEC-family, v0.10.0
  backlog or v0.11.0); it is not a REL-05 concern and must not be fixed inside Phase 23.
- **Non-transactional settings application becomes observable mid-flight.** `applyAndSaveSettings`
  performs ~10 independent mutations with no transaction. Today EDT serialisation makes it mutually
  exclusive with chat tool execution by construction; D-05 + D-11 remove that edge, so a tool worker
  can observe a **half-applied** settings set (new privacy mode, old custom patterns). Every field
  involved is `@Volatile`, so this is an atomicity question, not a visibility one — an earlier
  claim that `AuditLogger.enabled` lacked `@Volatile` was checked and is **false** (the annotation is
  on `audit/AuditLogger.kt:34`). Decide in planning between a settings snapshot taken before
  dispatch, mutual exclusion, or an accepted-and-documented residual. Also: `Redaction`'s
  `compiledCustomPatterns` `@Volatile` comment justifies itself by "writes from the EDT (save)" —
  that rationale goes stale under D-11 even though the behaviour does not. Update the comment.
- ~~**`McpRequestLimiter` contention becomes reachable**~~ — **RETRACTED 2026-08-20, verified at
  source during phase research.** The premise was false. `ChatPanel.buildToolContext` constructs
  `limiter = McpRequestLimiter(settings.mcpSettings.maxConcurrentRequests)` **per call**
  (`ChatPanel.kt:3019`), so a `/tool` racing a chain builds its **own fresh semaphore** and the two
  can never contend. The MCP-server path has its own separate instance
  (`McpRuntimeContextFactory.kt:28`). `"Too many concurrent MCP requests."` is therefore **not**
  reachable from the chat path, before or after this phase.

  **Do not simply delete this — invert it.** It is now a useful *negative* guard: if that string
  ever appears in a chat transcript, something shared a limiter that must not be shared. The
  corresponding AI-SPEC dimension E10 has been rewritten as a negative dimension on the same basis.
  Left visible rather than removed so the claim is not independently rediscovered and re-acted on.

</deferred>

---

*Phase: 23-edt-confinement-ui-responsiveness*
*Context gathered: 2026-08-20*
