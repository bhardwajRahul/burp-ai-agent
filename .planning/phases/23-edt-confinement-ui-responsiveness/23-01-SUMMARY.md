---
phase: 23-edt-confinement-ui-responsiveness
plan: 01
subsystem: ui
tags: [swing, edt, threading, mcp, audit, sec-06, rel-05]

requires:
  - phase: 22-sec-06-tool-approval-gate
    provides: "ToolApprovalGate / ToolApprovalOutcome.Run, ToolDecisionReporter's SC3 record, ChatPanelTestHarness, ChatPanelToolGateTest, and the select-by-trace-id lesson (commit ab55ff5)"
provides:
  - "OffEdtDispatch — the one off-EDT dispatch + marshal helper: named daemon thread, Throwable-safe body, one invokeLater tail, and two test-visible observers (dispatch record and settle record)"
  - "ChatPanel.RunningToolTracker + RunningToolToken — the compare-and-set supersede cell for a running tool call"
  - "ChatPanel.ToolCallCapture + finishApprovedToolCall — the EDT snapshot and the extracted marshalled tail"
  - "ChatPanel.ToolCallOutcome.EXECUTING — the fourth outcome, discharged from the worker's tail"
  - "ChatPanel.SUPERSEDED_RUN_STATUS ('cancelled') — the fourth SC3 run status"
  - "A live Cancel in UI-SPEC state S3, with Rule C-1's honest tool-cancel transcript line"
  - "ChatPanelTestHarness.awaitToolSettled / settledLabels / dispatchedLabels / installSettledObserver / releaseSettledObserver — worker-aware test synchronisation every later Phase 23 scenario depends on"
  - "ChatPanelEdtConfinementTest — the SC1/SC2/SC3/S-04 acceptance suite"
affects: [23-02, 23-03, 23-04, 23-05, chatpanel, mcp-tools, settings]

actuals:
  tokens: 64142
  tasks: 3
  commits: 3

tech-stack:
  added: []
  patterns:
    - "Off-EDT dispatch: EDT snapshot -> named daemon Thread -> exactly one SwingUtilities.invokeLater tail. Never offload-and-block."
    - "Compare-and-set supersede token instead of a cancelled flag or generation counter"
    - "Two test observers on the production dispatch helper — a settle record and a separate dispatch record — because absence in the settle record does not prove absence of work"
    - "Deadlock-failsafe timeouts, never elapsed-millisecond comparisons"

key-files:
  created:
    - src/main/kotlin/com/six2dez/burp/aiagent/ui/OffEdtDispatch.kt
    - src/test/kotlin/com/six2dez/burp/aiagent/ui/ChatPanelEdtConfinementTest.kt
  modified:
    - src/main/kotlin/com/six2dez/burp/aiagent/ui/ChatPanel.kt
    - src/test/kotlin/com/six2dez/burp/aiagent/ui/ChatPanelTestHarness.kt
    - src/test/kotlin/com/six2dez/burp/aiagent/ui/ChatPanelToolGateTest.kt

key-decisions:
  - "The superseded branch emits its own audit pair rather than falling through to reportFailedToolCall, so 'the pair fires on every exit' and 'exactly one pair per exit' hold together without widening reportFailedToolCall past detekt's nine-parameter ceiling"
  - "finishApprovedToolCall discharges onCompleted with ToolApprovalGate.DENIAL_RESULT on both the failure and the superseded exits, unifying what the two former consumers did differently"
  - "ToolCallCapture carries nine fields, not fifteen: approved is carried whole, and backendId / canonicalId / knownTool are re-derived in the tail — which is on the EDT and may read guarded state"
  - "RunningToolToken carries the tool name so Rule C-1's line names the run that was actually cancelled; identity semantics are unchanged because compareAndSet compares references"
  - "S-02 drives proxy_http_history rather than http1_request — measured, not assumed: http1_request cannot complete headlessly"
  - "The SC3 handshake asserts the recorded RUN STATUS, not just the latch count — the latch count alone passes vacuously against the pre-fix tree"

patterns-established:
  - "Dispatch record vs settle record: a test claiming NO work happened must read the record written synchronously with the dispatch, because an unfinished worker is missing from the settle record too"
  - "Red-before-green by surgical behaviour reversal: flip the single dispatch statement to inline execution rather than checking out the pre-fix tree, so the new tests still compile and the failure is attributable to exactly one change"

requirements-completed: [REL-05]

coverage:
  - id: D1
    description: "The SEC-06-gated chain call site reaches McpToolExecutor.executeTool on a thread that is not the EDT, is a daemon, and is named burp-ai-tool-exec — captured from the deep-stub MontoyaApi rather than inferred"
    requirement: "REL-05"
    verification:
      - kind: integration
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/ui/ChatPanelEdtConfinementTest.kt#anApprovedChainToolExecutesOnANamedDaemonThread"
        status: pass
    human_judgment: false
  - id: D2
    description: "A Montoya double that refuses the EDT is never called on the EDT (SC2)"
    requirement: "REL-05"
    verification:
      - kind: integration
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/ui/ChatPanelEdtConfinementTest.kt#aToolThatRefusesTheEdtCompletesNormally"
        status: pass
    human_judgment: false
  - id: D3
    description: "While a tool double is blocked mid-call, a runnable queued to the EDT runs before the tool returns — asserted as a mutual-latch handshake in which a blocked EDT is a deadlock, never as an elapsed-millisecond comparison"
    requirement: "REL-05"
    verification:
      - kind: integration
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/ui/ChatPanelEdtConfinementTest.kt#theEdtRunsQueuedWorkWhileAToolCallIsMidFlight"
        status: pass
    human_judgment: false
  - id: D4
    description: "An 8-iteration auto-chain produces 8 tool results in submission order, selected by trace id and never by list position, and the panel finishes in UI-SPEC state S0"
    requirement: "REL-05"
    verification:
      - kind: integration
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/ui/ChatPanelEdtConfinementTest.kt#aFullAutoChainProducesEightResultsInSubmissionOrder"
        status: pass
    human_judgment: false
  - id: D5
    description: "Cancelling a running tool returns the panel to S0 immediately, appends Rule C-1's honest line rather than 'Request cancelled.', discards the result, sends no followup turn, refunds no chain iteration, and still audits the call"
    requirement: "REL-05"
    verification:
      - kind: integration
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/ui/ChatPanelEdtConfinementTest.kt#cancellingARunningToolDiscardsItsResultAndStillAuditsTheCall"
        status: pass
    human_judgment: false
  - id: D6
    description: "ChatPanelToolGateTest stays fully green across three consecutive runs because the ten tests riding the moved call site were made worker-aware in the same commit"
    requirement: "REL-05"
    verification:
      - kind: integration
        ref: "JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test --tests '*ChatPanelToolGateTest' (three consecutive runs)"
        status: pass
    human_judgment: false
  - id: D7
    description: "The ~160-character tool-cancel transcript line wraps rather than clips in a live Burp window, and no sub-frame Send-Cancel flicker is objectionable on the auto-approved chain path"
    verification: []
    human_judgment: true
    rationale: "Both are rendering properties of a live Burp Look-and-Feel. The transcript is a JEditorPane that wraps at 75% of viewport width, but a headless JList/JEditorPane never paints, so no assertion can observe the wrap. The flicker is sub-frame between two invokeLater blocks and has no deterministic assertion. Routed to 23-HUMAN-UAT.md by plan 23-05 (FLAG-23-04)."
  - id: D8
    description: "Burp really does throw 'Extensions should not make HTTP requests in the Swing event dispatch thread' when an extension calls its HTTP API from the EDT"
    verification: []
    human_judgment: true
    rationale: "Research assumption A1 — corroborated by this repo's own #80 workaround and third-party reports, but not live-confirmed. S-02 deliberately pins the invariant against a double this suite controls so the test does not depend on it. Live confirmation is a UAT item routed by 23-05."

duration: 42 min
completed: 2026-08-20
status: complete
---

# Phase 23 Plan 01: EDT Confinement Tracer Summary

**An approved chain tool call now runs on a named daemon thread and marshals back through exactly one `invokeLater`, with a compare-and-set supersede cell, a live Cancel that tells the user their request was already sent, and the worker-aware test synchronisation the rest of Phase 23 is built on.**

## Performance

- **Duration:** 42 min
- **Started:** 2026-08-20T18:03:38Z
- **Completed:** 2026-08-20T18:45:52Z
- **Tasks:** 3
- **Files modified:** 5 (2 created, 3 modified)

## Accomplishments

- `OffEdtDispatch` — one dispatch seam for the whole phase: a named daemon `Thread`, a `runCatching` body, one `SwingUtilities.invokeLater` tail, and error routing for both the work and the tail. Nothing after the dispatch statement waits on the worker, which is the single property separating this from `MontoyaHttpTransport`'s offload-and-block.
- `executeApprovedToolCall` restructured into capture → dispatch → marshalled tail, returning the new `ToolCallOutcome.EXECUTING`. The tail (`finishApprovedToolCall`) owns the audit pair, the transcript row, the busy state and the continuation discharge.
- The supersede path (`RunningToolTracker` + `RunningToolToken`) makes a returning worker discover it no longer owns the panel, and land in UI-SPEC state S4: no row, no followup, no refunded iteration — **but still one audit pair**.
- Cancel is live in S3 and honest: it takes the tool token before the backend connection, returns the panel to S0 at once, and writes Rule C-1's line instead of the false `"Request cancelled."`.
- `ChatPanelTestHarness` gained `awaitToolSettled` plus separate settle and dispatch records — the Wave 0 prerequisite every remaining Phase 23 scenario depends on.
- The ten `ChatPanelToolGateTest` tests that ride the moved call site were repaired **in the same commit as the move**, so the suite never spent a commit racy.

## Task Commits

1. **Task 1: End-to-end "an approved chain tool runs off the EDT"** — `9347c14` (feat)
2. **Task 2: SC2, the EDT-liveness handshake, and eight results in trace-id order** — `e55dc42` (test)
3. **Task 3: The cancel path** — `2fd120e` (feat)

## Files Created/Modified

- `src/main/kotlin/com/six2dez/burp/aiagent/ui/OffEdtDispatch.kt` (new, 113 lines) — the dispatch + marshal helper and its two observers.
- `src/main/kotlin/com/six2dez/burp/aiagent/ui/ChatPanel.kt` — `RunningToolTracker`, `RunningToolToken`, `ToolCallCapture`, `finishApprovedToolCall`, `discardSupersededToolResult`, `toolCancelLine`, `ToolCallOutcome.EXECUTING`, `SUPERSEDED_RUN_STATUS`, and the reworked `cancelInFlightRequest`.
- `src/test/kotlin/com/six2dez/burp/aiagent/ui/ChatPanelTestHarness.kt` — `awaitToolSettled`, `settledLabels`, `dispatchedLabels`, `installSettledObserver`, `releaseSettledObserver`.
- `src/test/kotlin/com/six2dez/burp/aiagent/ui/ChatPanelEdtConfinementTest.kt` (new, 529 lines) — five tests: S-01, S-02, the E3 handshake, the SC3 ordering chain, and S-04.
- `src/test/kotlin/com/six2dez/burp/aiagent/ui/ChatPanelToolGateTest.kt` — the ten-test worker-aware repair.

## Verification Results

| Gate | Result |
|---|---|
| `./gradlew ktlintCheck detekt test` | exit 0 |
| `./gradlew test --tests '*ChatPanelEdtConfinementTest' --tests '*McpToolExecutorEdtGuardTest' --tests '*SettingsSaveAsyncTest' --tests '*ChatPanelToolGateTest'` | exit 0 |
| `./gradlew test --tests '*ChatPanelToolGateTest'` × 3 consecutive | exit 0, exit 0, exit 0 |
| `./gradlew test --tests '*ChatPanelEdtConfinementTest' --tests '*ChatPanelToolGateTest'` × 3 consecutive | exit 0, exit 0, exit 0 |
| `./gradlew test -PexcludeHeavyTests=true --tests '*ChatPanelEdtConfinementTest'` | exit 0, **5 tests executed, 0 failures** — the new suite really does run under the PR-gate filter |
| `grep -c '<ID>' detekt-baseline.xml` | **1096** — unmoved; `git diff --stat detekt-baseline.xml` empty |
| `grep -c 'assertEdt()' ChatPanel.kt` | **6** — not 5, not 7 |
| `awk '/private fun assertEdt\(\)/,/^    }$/' … \| grep -c 'off-EDT access is a data race (REL-01)'` | **1** — the body is byte-identical |
| `grep -c 'SwingUtilities.invokeLater' ChatPanel.kt` | **11** — identical to the measured HEAD baseline. **Zero additions to justify:** the new marshalling point lives in `OffEdtDispatch`, not in `ChatPanel`. Two KDoc sentences that would have inflated this count by prose alone were rewritten to describe the mechanism instead of naming it, so the number stays a measurement of code. |
| `grep -c 'Request cancelled.' ChatPanel.kt` | **1** — kept, not overwritten |
| `grep -vE '^\s*(//\|\*\|/\*)' OffEdtDispatch.kt \| grep -cE '\.get\(\|\.join\(\|\.await\(\|invokeAndWait'` | **0** — the helper does not offload-and-block |
| same grep over `executeApprovedToolCall`'s range | **0** |
| `grep -c 'awaitToolSettled' ChatPanelToolGateTest.kt` | **5** — one per test named in step 7(a) |
| `grep -c 'dispatchedLabels' ChatPanelToolGateTest.kt` | **5** — one per `never()` site; `grep -c 'never()).history()'` still **5**, so no negative assertion was deleted instead of licensed. `settledLabels` appears **0** times in that file. |
| `grep -c 'installSettledObserver' / 'releaseSettledObserver'` in ChatPanelToolGateTest.kt | **1** each — one lifecycle pair, inside the existing `@BeforeEach`/`@AfterEach` |

### SC3 ordering — the assertion expression, showing identity-based selection

```kotlin
val chainTraceId =
    requireNotNull(ChatPanelTestHarness.settledLabels().distinct().singleOrNull()) { … }
assertEquals(
    (1..ChatPanel.MAX_AUTO_TOOL_ITERATIONS).map { it.toString() },
    decisionsFor(chainTraceId).map { it["step"] },
    "SC3: eight results, each recorded at its own chain step, in submission order.",
)
```

`decisionsFor` filters on `it.second["traceId"] == traceId`. The selector is the chain's identity, taken from this test's own dispatch record; the ordering claim rides on the recorded `step` field, never on a list index. `distinct().singleOrNull()` is itself the assertion that exactly one chain ran.

## Red-before-Green Evidence

The demonstration was made by reverting the **single dispatch statement** in `OffEdtDispatch.run` to inline execution on the calling thread (`body.run()`), which is exactly HEAD's synchronous on-EDT behaviour. This is stronger than checking out the pre-fix tree: the new tests still compile, so each failure is attributable to that one change and nothing else. (`git stash` was not used — it is banned in this workspace.)

**Captured failures, run 2026-08-20 against inline-on-EDT execution:**

```
--- anApprovedChainToolExecutesOnANamedDaemonThread()
org.opentest4j.AssertionFailedError: SC1/SC2: an approved tool call must not execute on the EDT.
Burp refuses HTTP from the EDT and the whole Burp UI freezes for as long as the call runs there.
  ==> expected: not equal but was: <AWT-EventQueue-0>

--- aToolThatRefusesTheEdtCompletesNormally()
org.opentest4j.AssertionFailedError: SC2: the tool ran to completion, so it was never entered from
the EDT. A status of 'error' here means the double's EDT refusal fired — i.e. the call WAS made on
the EDT.  ==> expected: <ok> but was: <error>

--- theEdtRunsQueuedWorkWhileAToolCallIsMidFlight()
org.opentest4j.AssertionFailedError: SC3/E3: the tool returned normally, which is possible only if
the EDT ran the queued runnable while the tool was still mid-call. An 'error' status means the
tool's own await expired — the EDT was blocked inside the call.  ==> expected: <ok> but was: <error>
```

S-01 fails on **the first of its three assertions**, as the acceptance criterion requires.

**S-04 was demonstrated red separately**, against the pre-fix cancel ordering (`inFlightConnection.take() ?: return false` first):

```
--- cancellingARunningToolDiscardsItsResultAndStillAuditsTheCall()
org.opentest4j.AssertionFailedError: UI-SPEC S-5: Cancel returns the panel to S0 at once.
  ==> expected: <true> but was: <false>
```

That is precisely the inert-Cancel defect Rule S-5 names.

**`aFullAutoChainProducesEightResultsInSubmissionOrder` passes both before and after, by design.** Its claim is the SC3 ordering invariant, which a synchronous chain also satisfies; it is the ordering test's job to stay green while the dispatch becomes asynchronous. It is not offered as red-before-green evidence.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] The SC3 handshake test passed vacuously as first written**

- **Found during:** Task 2 (red-before-green capture)
- **Issue:** The test's final assertion was `probeRan.count == 0`. Against inline-on-EDT execution the click blocks until the tool's own await expires, the tool returns an error, the click returns — and only *then* does the queued runnable run, leaving the latch at zero. The test went **green with the defect fully present**. This is the exact vacuous-pass failure mode `ChatPanelToolGateTest` documents, reproduced in the test written to prove the opposite.
- **Fix:** the loud clause is now the recorded run status (`"ok"`), which only a free EDT can produce, asserted first; the latch count survives as corroboration with a comment saying why it cannot stand alone.
- **Files modified:** `ChatPanelEdtConfinementTest.kt`
- **Verification:** re-ran the inline-dispatch probe — the test now goes red with a message naming the blocked EDT.
- **Committed in:** `e55dc42`

**2. [Rule 3 - Blocking] `http1_request` is not headlessly drivable; S-02 uses `proxy_http_history`**

- **Found during:** Task 2
- **Issue:** the plan specifies an `http1_request`-shaped call for S-02. Measured headlessly, it records `status=error` with a 112-character error result *before any EDT question arises* — its body reaches Montoya's `HttpRequest.httpRequest` static factory, which `McpScopeFilter.deriveScopeUrl`'s own KDoc already records as "unavailable in pure-JVM unit tests". The S-02 assertion would have been red for a reason unrelated to the EDT, which is the one thing a red-before-green gate must never be.
- **Fix:** S-02 drives `proxy_http_history` — the same SEC-06 trust boundary through a seam that works — with the EDT-refusal double on `api.proxy().history()`. The substitution and its measurement are recorded in the test's KDoc.
- **Files modified:** `ChatPanelEdtConfinementTest.kt`
- **Verification:** measured empirically both ways before choosing; the chosen form is red against inline-on-EDT execution and green after.
- **Committed in:** `e55dc42`

**3. [Rule 3 - Blocking] `ToolCallCapture` carries nine fields, not the fifteen the plan lists**

- **Found during:** Task 1
- **Issue:** the plan's field list (`sessionId, userText, call, panel, context, backendId, chainStep, canonicalId, knownTool, tier, decision, origin, traceId, remainingToolIterations, startedAt`) is fifteen. `detekt.yml` sets `constructorThreshold: 10`, and — measured against `detekt-baseline.xml:141`, where `ChatPanel`'s own **ten**-parameter constructor is already an entry — the rule fires at ten, not above ten. A fifteen-field record would have added a baseline entry and broken the 1096 pin the plan itself gates on.
- **Fix:** `approved: ToolApprovalOutcome.Run` is carried whole (collapsing `tier` / `decision` / `origin` into one immutable value object), and `backendId` / `chainStep` / `canonicalId` / `knownTool` are re-derived in `finishApprovedToolCall`. That is sound because the tail runs **on the EDT**, where reading `sessionsById` through `backendIdFor` is legal — AI-SPEC E5 forbids those reads on the *worker*, and the worker touches only `captured.context` and `McpToolExecutor`. `reportFailedToolCall` already re-derived both values internally, so this also removed a duplication rather than adding one.
- **Files modified:** `ChatPanel.kt`
- **Verification:** `grep -c '<ID>' detekt-baseline.xml` returns 1096; `./gradlew detekt` exits 0.
- **Committed in:** `9347c14`

**4. [Rule 3 - Blocking] The superseded exit emits its own audit pair instead of falling through to `reportFailedToolCall`**

- **Found during:** Task 1
- **Issue:** the plan asks for the audit pair on **every** exit including supersede, for exactly **one** pair per exit, and for a superseded run to render **no** transcript row. `reportFailedToolCall` unconditionally calls `panel.addMessage`, so a superseded failure routed through it would render a row; suppressing that would need a tenth parameter, which its own KDoc records as impossible (detekt's ceiling is nine).
- **Fix:** the superseded check runs first and is terminal, in `discardSupersededToolResult`, which emits its own `report` → `log` pair carrying `SUPERSEDED_RUN_STATUS` and a `supersedeReason` key — merged onto the reporter's map exactly as `reportFailedToolCall` merges `errorClass`. Every exit emits exactly one pair; no exit emits two; the superseded exit renders nothing.
- **Files modified:** `ChatPanel.kt`
- **Verification:** S-04 asserts `decisionsFor(traceId).single()` — a *single* record for the cancelled call, with `status == "cancelled"`.
- **Committed in:** `9347c14`, `2fd120e`

**5. [Rule 2 - Missing Critical] `finishApprovedToolCall` clears the busy state on the failure exit**

- **Found during:** Task 1
- **Issue:** the plan lists `setSendingState(false)` only on the success exit. Before this plan, an approved tool that threw was handled synchronously inside a turn that would clear the busy state elsewhere. Once the call is asynchronous the chain **stops** at `reportFailedToolCall` with no followup turn, so without an explicit clear the panel stays in S3 forever with nothing running — a permanently frozen Send button after any tool failure.
- **Fix:** the failure exit calls `setSendingState(false)` before discharging the continuation.
- **Files modified:** `ChatPanel.kt`
- **Verification:** `ChatPanelToolGateTest.anApprovedToolThatThrowsStillDischargesTheParkedContinuation` remains green across three consecutive runs.
- **Committed in:** `9347c14`

**6. [Rule 3 - Blocking] `RunningToolTracker` is `internal`, and the token is a class rather than bare `Any`**

- **Found during:** Tasks 1 and 3
- **Issue:** the plan says "a private `RunningToolTracker` class" with an opaque `Any` token. A file-private top-level class would be unreachable from `ChatPanelConcurrencyTest`, whose existing `InFlightConnectionTracker` assertions this is explicitly meant to mirror; and Rule C-1's transcript line needs the tool's **name**, which a bare `Any` cannot supply.
- **Fix:** `RunningToolTracker` is `internal`, matching its analog `InFlightConnectionTracker` verbatim. The tracker's API stays `Any`-typed (opaque, as specified); the token is a private `RunningToolToken(val tool: String)` — a plain class, **not** a `data class`, because `AtomicReference.compareAndSet` compares references and a value-equal token would be a correctness trap.
- **Files modified:** `ChatPanel.kt`
- **Verification:** `grep -c 'class RunningToolTracker'` returns 1; `grep -c 'runningTool'` returns 4 (≥ 3); the supersede CAS is exercised by S-04.
- **Committed in:** `9347c14`, `2fd120e`

**7. [Rule 1 - Bug] Two KDoc sentences inflated the phase's own structural counters**

- **Found during:** Task 1
- **Issue:** prose that named `assertEdt()` and `SwingUtilities.invokeLater` pushed `grep -c 'assertEdt()'` to 7 and `grep -c 'SwingUtilities.invokeLater'` to 12 — the exact counters SC5 uses as evidence, moved by comments rather than by code. Left in, the plan's own gate would have failed for a false reason, and a later reader would have had to justify a phantom addition.
- **Fix:** both sentences rewritten to describe the mechanism rather than name the symbol. The comment in `ChatPanelToolGateTest` distinguishing the dispatch record from the settle record was rewritten the same way, for the same reason.
- **Files modified:** `ChatPanel.kt`, `ChatPanelToolGateTest.kt`
- **Verification:** counts back to 6 and 11 — both exactly at the measured HEAD baseline.
- **Committed in:** `9347c14`

**8. [Rule 3 - Blocking] `assertTimeoutPreemptively` runs its block on another thread**

- **Found during:** Tasks 1 and 2
- **Issue:** Kotlin rejects assigning a `val` from inside the lambda ("Captured values cannot be initialized because of possible reassignments"), and the block genuinely runs on a separate thread, so a plain `var` would also have a visibility hazard.
- **Fix:** trace ids cross the boundary through an `AtomicReference`, with a comment naming the reason.
- **Files modified:** `ChatPanelEdtConfinementTest.kt`, `ChatPanelToolGateTest.kt`
- **Verification:** compiles and passes across three consecutive runs.
- **Committed in:** `9347c14`, `e55dc42`

---

**Total deviations:** 8 auto-fixed (2 bugs, 1 missing critical, 5 blocking).
**Impact on plan:** No scope creep. Six of the eight are the plan's own constraints colliding with a measured fact — the detekt threshold firing at ten rather than above ten, `reportFailedToolCall`'s parameter ceiling, `http1_request`'s headless factory, `assertTimeoutPreemptively`'s threading. Two are genuine defects caught before commit: a vacuous test and a permanently frozen panel after a tool failure. Every prohibition held: no cancelled call became an unlogged call, and the cancel copy never claims a sent request was cancelled.

## Prohibitions — Held

| Prohibition | Evidence |
|---|---|
| A cancelled tool call must never become an unlogged call | `discardSupersededToolResult` emits the `report` → `log` pair on the superseded exit; S-04 asserts exactly one `mcp_tool_decision` record for the cancelled call's trace id, with `status == "cancelled"` and `decision == "approve_once"` intact. |
| The tool-cancel line must never claim the request was cancelled when it was sent | Rule C-1's line is used verbatim; S-04 asserts the transcript contains "was already sent to Burp and will finish" and does **not** contain `"Request cancelled."`. The `Cancel` label is unchanged and no confirmation dialog was added (Rule C-3). |
| No offload-then-block | `grep` over `OffEdtDispatch.kt` and `executeApprovedToolCall`'s range for `.get(` / `.join(` / `.await(` / `invokeAndWait` returns 0 in both. |
| No `SwingWorker`, no coroutines outside `mcp/` | Neither identifier appears anywhere in the files touched — including in comments, which 23-05's negative grep cannot distinguish from use. |
| `assertEdt()` and the detekt baseline byte-identical | 6 call sites, body unchanged, baseline at 1096. |

## Accepted Residual (carried forward, not resolved here)

The sub-frame Send↔Cancel flicker on the auto-approved / session-approved path remains, exactly as the plan's `<planner_assumptions>` states. `setSendingState(true)` is the first statement of the same `invokeLater` block that dispatches the worker (UI-SPEC Rule S-3), but two back-to-back EDT events can still be separated by a repaint. No timer or deferred repaint was added. It is confirmable only by live UAT and is routed by plan 23-05 as FLAG-23-04.

## Issues Encountered

None beyond the deviations above. `RedactionTest`'s known wall-clock flake did not surface in any of the full-suite runs.

## User Setup Required

None — no external service configuration, and no package was added to `build.gradle.kts` or any lockfile.

## Next Phase Readiness

- **Wave 0 is complete for the chat path.** `OffEdtDispatch`, `awaitToolSettled`, `settledLabels` and `dispatchedLabels` all exist and are exercised, which is what plans 23-02 through 23-05 were sequenced behind.
- **23-02** can now land the throwing door guard together with all three call-site moves in one commit, as D-04 requires. Note for it: the harness lifecycle pair is pinned at exactly one occurrence per test class, and `slashCommandPathIsNotDoublePrompted` (`ChatPanelToolGateTest`) is still on the EDT and is 23-02's to repair.
- **23-04** inherits the supersede cell. Three of the four teardown exits get it for free; `deleteSession` does not call `cancelInFlightRequest` and is still 23-04's to wire.
- **One extension point worth knowing:** `SUPERSEDED_RUN_STATUS` is a single value (`"cancelled"`) and `discardSupersededToolResult` writes a fixed `supersedeReason`. When 23-04 adds session-delete, project-change and unload supersedes, that reason should become a parameter so the four causes stay distinguishable in the audit log.

---
*Phase: 23-edt-confinement-ui-responsiveness*
*Completed: 2026-08-20*

## Self-Check: PASSED

- `OffEdtDispatch.kt` — FOUND (113 lines, ≥ 40 required, contains `internal object OffEdtDispatch`)
- `ChatPanelEdtConfinementTest.kt` — FOUND (529 lines, ≥ 80 required, contains `class ChatPanelEdtConfinementTest`)
- `ChatPanelTestHarness.kt` — FOUND (contains `fun awaitToolSettled`)
- Commits `9347c14`, `e55dc42`, `2fd120e` — all present in `git log`
- `ChatPanel.kt` → `OffEdtDispatch.run` key link — present in `executeApprovedToolCall`
- `ChatPanelTestHarness.kt` → `registerSettledObserver` key link — present
- `ChatPanel.runningTool` key link — present (4 occurrences)
