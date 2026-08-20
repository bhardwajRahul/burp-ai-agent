---
phase: 23-edt-confinement-ui-responsiveness
plan: 04
subsystem: ui
tags: [swing, edt, threading, mcp, audit, teardown, rel-05]

requires:
  - phase: 23-edt-confinement-ui-responsiveness
    provides: "OffEdtDispatch, RunningToolTracker/RunningToolToken, finishApprovedToolCall + discardSupersededToolResult, ChatPanelTestHarness.awaitToolSettled / dispatchedLabels, ChatPanelEdtConfinementTest (23-01)"
  - phase: 23-edt-confinement-ui-responsiveness
    provides: "the executeToolResult door guard, finishUserOriginatedToolCall and the /tool + dialog call-site moves (23-02)"
  - phase: 22-sec-06-tool-approval-gate
    provides: "ToolApprovalGate tiers, ToolDecisionReporter's SC3 record, pendingDecisions / PendingToolDecision, ChatPanelToolGateTest and its parkContinuation fixture"
provides:
  - "ChatPanel.deleteConfirmedSession — the post-confirmation half of a session delete, internal so the teardown is drivable without JOptionPane, carrying the explicit runningTool.take() the exit inherits from nowhere"
  - "A conditional setSendingState(false) on the session-delete exit, so superseding a worker there does not leave the panel permanently busy"
  - "Teardown path 4 and 5 comments stating that their worker supersede is inherited, and that unload deliberately does not wait"
  - "ChatPanelEdtConfinementTest scenarios S-05, S-06, S-07, S-09, S-12, S-08 plus the E10 structural guard"
  - "assertOrderedAuditPair / loggedMetadataFor — E4 asserted as a data dependency between the reporter's return value and the AI-activity record"
  - "ChatPanelToolGateTest cancel and slash-command supersede budget variants (E6)"
  - "EMPTY_HISTORY_ROW — transcript rows identified by body text, after the header-based form was measured unfalsifiable"
affects: [23-05, chatpanel, mcp-tools, audit]

actuals:
  tokens: 75544
  tasks: 3
  commits: 3

tech-stack:
  added: []
  patterns:
    - "Split a modal-gated teardown into confirm + perform, and widen the perform half to internal, so the teardown is assertable while the modal stays in front of users"
    - "Rank a test's clauses by MEASURED loudness against the reverted tree, and say in the KDoc which clauses are corroboration and why"
    - "Assert an audit PAIR as a data dependency (the logged metadata IS the reporter's return value) rather than as call ordering"
    - "Prove a non-blocking teardown as a deadlock, and name the bounded-wait case the scenario cannot catch plus the structural guard that does"

key-files:
  created:
    - .planning/phases/23-edt-confinement-ui-responsiveness/deferred-items.md
  modified:
    - src/main/kotlin/com/six2dez/burp/aiagent/ui/ChatPanel.kt
    - src/test/kotlin/com/six2dez/burp/aiagent/ui/ChatPanelEdtConfinementTest.kt
    - src/test/kotlin/com/six2dez/burp/aiagent/ui/ChatPanelToolGateTest.kt

key-decisions:
  - "deleteSession was split into a modal half and an internal deleteConfirmedSession, because JOptionPane throws HeadlessException and every line of the teardown sits below it — the plan's own Task 2 requirement to drive the post-confirmation body directly is unsatisfiable without the split"
  - "No boolean `confirmed` parameter and no overload: a parameter whose only job is to say something at the declaration is the WR-03 anti-pattern this file's own resolvePending KDoc records"
  - "The session-delete exit clears the busy state CONDITIONALLY on a worker having been superseded — unconditionally would clobber a backend turn streaming in another session, and not at all would leave Send hidden forever"
  - "23-01's suggested per-cause supersedeReason parameter was NOT implemented: three of the four exits share cancelInFlightRequest, whose signature is pinned by a detekt-baseline ReturnCount key, so per-cause reasons were reachable for one exit only and a reason reading 'cancelled' for an unload is worse than one honest generic reason"
  - "S-09 uses scope_include with an unsafe-enabled settings override rather than substituting a CONFIRM tool, so the CONFIRM_EACH claim stays a claim about that tier"
  - "S-08 asserts the measured consequence (the /tool supersedes the chain step) instead of the plan's 'chain unharmed, still terminates at 8', which the panel-wide supersede cell makes unreachable"

patterns-established:
  - "Vacuity is established by probe, never by review: every new supersede scenario was run against a one-statement reversal of the mechanism before being accepted"
  - "Prose must not move a counter the phase gates on — describe the symbol instead of naming it (continued from 23-01)"

requirements-completed: [REL-05]

coverage:
  - id: D1
    description: "Deleting a session supersedes the tool worker its chain left running — the one teardown exit of four that routes through no shared cancel and inherits nothing"
    requirement: "REL-05"
    verification:
      - kind: integration
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/ui/ChatPanelEdtConfinementTest.kt#deletingASessionSupersedesItsRunningToolAndStillAuditsTheCall"
        status: pass
    human_judgment: false
  - id: D2
    description: "A Burp project change supersedes the running worker and no write from its tail reaches the disposed panel (E5)"
    requirement: "REL-05"
    verification:
      - kind: integration
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/ui/ChatPanelEdtConfinementTest.kt#aProjectChangeSupersedesTheRunningToolAndNoWriteReachesTheDisposedPanel"
        status: pass
    human_judgment: false
  - id: D3
    description: "Extension unload supersedes the worker and returns without waiting for it; the worker is a daemon, so not waiting is safe (SC6, T-23-12)"
    requirement: "REL-05"
    verification:
      - kind: integration
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/ui/ChatPanelEdtConfinementTest.kt#unloadSupersedesTheRunningToolWithoutWaitingForIt"
        status: pass
      - kind: other
        ref: "awk '/    fun shutdown\\(\\)/,/^    }$/' ChatPanel.kt | grep -vE '^\\s*(//|\\*|/\\*)' | grep -cE '\\.get\\(|\\.join\\(' -> 0"
        status: pass
    human_judgment: false
  - id: D4
    description: "An approved CONFIRM_EACH call superseded mid-flight reaches Burp exactly once — the compare-and-set has one winner (CFM 2, T-23-10)"
    requirement: "REL-05"
    verification:
      - kind: integration
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/ui/ChatPanelEdtConfinementTest.kt#aSupersededConfirmEachCallReachesBurpExactlyOnce"
        status: pass
    human_judgment: false
  - id: D5
    description: "Every superseded exit emits the ordered audit pair, asserted as a data dependency: the AI-activity metadata IS the map the decision reporter returned, plus supersedeReason (E4, T-23-11)"
    requirement: "REL-05"
    verification:
      - kind: integration
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/ui/ChatPanelEdtConfinementTest.kt#assertOrderedAuditPair (invoked by S-05, S-06, S-07, S-09, S-12)"
        status: pass
    human_judgment: false
  - id: D6
    description: "A throwing tool body reaches reportFailedToolCall with the Throwable intact, writes logToError, renders an Error row and returns the panel to S0 — nothing is swallowed (E9, S-12)"
    requirement: "REL-05"
    verification:
      - kind: integration
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/ui/ChatPanelEdtConfinementTest.kt#aThrowingToolBodyIsReportedRatherThanSwallowed"
        status: pass
    human_judgment: false
  - id: D7
    description: "A /tool racing a chain step never reports limiter exhaustion, and the per-call limiter construction is pinned structurally against the hoisting refactor (E10, S-08)"
    requirement: "REL-05"
    verification:
      - kind: integration
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/ui/ChatPanelEdtConfinementTest.kt#aSlashCommandRacingAChainStepNeverReportsLimiterExhaustion"
        status: pass
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/ui/ChatPanelEdtConfinementTest.kt#theChatToolLimiterIsConstructedPerCall"
        status: pass
    human_judgment: false
  - id: D8
    description: "Cancel and a slash-command supersede refund no chain iteration and send no further backend turn (E6)"
    requirement: "REL-05"
    verification:
      - kind: integration
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/ui/ChatPanelToolGateTest.kt#cancellingARunningToolRefundsNoChainIterationAndSendsNoFurtherTurn"
        status: pass
      - kind: integration
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/ui/ChatPanelToolGateTest.kt#aToolSupersededByASlashCommandRefundsNoChainIterationEither"
        status: pass
    human_judgment: false
  - id: D9
    description: "A real session delete in a live Burp — the confirmation modal appears, and deleting a session whose tool is still running leaves the panel usable rather than stuck with Send hidden"
    verification: []
    human_judgment: true
    rationale: "The split put the teardown behind an internal function the tests drive directly; nothing automated exercises `deleteSession`'s modal half, because `JOptionPane.showConfirmDialog` throws HeadlessException under `-Djava.awt.headless=true`. That the modal still appears, still says the session name, and still gates the teardown is a live-UI property. Routed to 23-HUMAN-UAT.md by plan 23-05."

duration: 61 min
completed: 2026-08-20
status: complete
---

# Phase 23 Plan 04: Teardown Supersede Coverage Summary

**Session delete now takes the running-tool token explicitly — the one teardown exit of four that routes through no shared cancel — and all four exits, the double-execution race, a throwing worker and the per-call limiter are each guarded by a scenario that was proven red against a one-statement reversal of the mechanism it tests.**

## Performance

- **Duration:** 61 min
- **Started:** 2026-08-20T20:09:00Z
- **Completed:** 2026-08-20T21:10:27Z
- **Tasks:** 3
- **Files modified:** 4 (1 created, 3 modified)

## Accomplishments

- **The gap D-08 named and nothing covered is closed.** `deleteSession` never called `cancelInFlightRequest` and never had, so plan 23-01's supersede cell reached three exits and skipped the fourth. It now takes the token explicitly, beside the existing `resolvePending` call and ahead of the `try`, with the comment that stops a future reader deleting the line as duplication.
- **All four exits are guarded, and each guard is attributable to one statement.** Reversing `cancelInFlightRequest`'s `take()` reddens exactly the three inheriting scenarios and leaves session-delete green; reversing session-delete's own `take()` reddens only that one; reversing `finishApprovedToolCall`'s compare-and-set reddens all seven supersede-dependent tests at once.
- **Two vacuous assertions were found by probing and fixed** — one of them shipped in 23-01. See "Vacuity found and closed" below; this is the third consecutive wave in which the dominant failure mode was an assertion that passes with the defect present, and the third in which review did not catch it.
- **A Rule 2 defect was caught before commit:** superseding a worker on the session-delete path without clearing the busy state would have left the Send button hidden forever, because the exit that supersedes is not the exit that returns the panel to S0.
- **E4 is asserted as a data dependency rather than as call order.** The AI-activity metadata must *be* the map the decision reporter returned; a `log` call that assembled its own map fails, and would have passed an ordering check.

## Task Commits

1. **Task 1: the session-delete supersede** — `089c940` (feat)
2. **Task 2: S-05, S-06, S-07, S-09 and the two budget variants** — `76635fb` (test)
3. **Task 3: S-12, S-08 and the E10 structural guard** — `ae009e9` (test)

## Files Created/Modified

- `src/main/kotlin/com/six2dez/burp/aiagent/ui/ChatPanel.kt` — `deleteConfirmedSession` extracted and made `internal`; the explicit `runningTool.take()` and its conditional `setSendingState(false)`; extended teardown path 2, 4 and 5 comments.
- `src/test/kotlin/com/six2dez/burp/aiagent/ui/ChatPanelEdtConfinementTest.kt` — 6 new scenarios (13 tests total, from 6 at wave 2), plus `assertOrderedAuditPair`, `loggedMetadataFor`, `parkContinuation`, `sessions` / `onlySession`, `functionBody`, and the two settings overrides.
- `src/test/kotlin/com/six2dez/burp/aiagent/ui/ChatPanelToolGateTest.kt` — the cancel and slash-command budget variants (20 tests total, from 18).
- `.planning/phases/23-edt-confinement-ui-responsiveness/deferred-items.md` — one out-of-scope finding, below.

## Red-before-Green Evidence

Every probe is a surgical reversal of ONE statement, so the new tests still compile and each failure is attributable to that change alone. `git stash` was not used — it is banned in this workspace.

### Probe 1 — the session-delete supersede (the line Task 1 exists to add)

`deleteConfirmedSession`: `runningTool.take()` → `runningTool.current()`. The busy-state clear is left in place, so only the supersede is reversed.

```
ChatPanelEdtConfinementTest > deletingASessionSupersedesItsRunningToolAndStillAuditsTheCall() FAILED
org.opentest4j.AssertionFailedError: D-08: a session delete must supersede the tool worker its chain
left running. This exit does not route through the in-flight cancel, so it inherits nothing and needs
its own take().  ==> expected: <cancelled> but was: <ok>
```

**Only that test failed** — 9 of 10 stayed green, which is what makes the failure attributable.

### Probe 2 — the inherited supersede (`cancelInFlightRequest`'s `take()` → `current()`)

```
ChatPanelEdtConfinementTest > unloadSupersedesTheRunningToolWithoutWaitingForIt() FAILED
ChatPanelEdtConfinementTest > aProjectChangeSupersedesTheRunningToolAndNoWriteReachesTheDisposedPanel() FAILED
ChatPanelEdtConfinementTest > cancellingARunningToolDiscardsItsResultAndStillAuditsTheCall() FAILED
ChatPanelEdtConfinementTest > aSupersededConfirmEachCallReachesBurpExactlyOnce() FAILED
```

Exactly the four exits that inherit — and S-05 stayed **green**, which is the positive evidence that its own `take()` is doing the work rather than riding on the shared one.

### Probe 3 — the mechanism itself (`val superseded = !runningTool.clearIfMatches(token)` → `false`)

All seven supersede-dependent tests across both suites went red, including both budget variants:

```
30 tests completed, 7 failed
```

### Probe 4 — the Throwable-intact claim (`errorClass` keyed off `failure.message` instead of `failure.javaClass.simpleName`)

`aThrowingToolBodyIsReportedRatherThanSwallowed` went red, and only that test.

### Probe 5 — the worker error log (`OffEdtDispatch`'s work-failure `logError` removed)

Same test red, and only that test.

### Probe 6 — the E10 refactor (limiter hoisted to a `ChatPanel` field)

The exact "avoid an allocation" refactor the dimension exists to catch. Both halves red:

```
ChatPanelEdtConfinementTest > aSlashCommandRacingAChainStepNeverReportsLimiterExhaustion() FAILED
org.opentest4j.AssertionFailedError: E10: each chat call site mints its own McpRequestLimiter, so two
user actions can never contend. ... Transcript: ... /tool proxy_http_history {"count":5}
Error: Too many concurrent MCP requests.  ==> expected: <false> but was: <true>

ChatPanelEdtConfinementTest > theChatToolLimiterIsConstructedPerCall() FAILED
```

After every probe, `git diff` confirmed the main sources byte-identical to their committed state before the next step.

## Vacuity found and closed

Two assertions were green against the defect and were caught only by probing.

**1. `contains("Tool result: ...")` can never be true — and it shipped in 23-01.** `ChatMessagePanel` renders every non-user role as the literal string `"AI"`; the role handed to `addMessage` reaches no component at all. So S-04's `assertFalse(transcript.contains("Tool result: proxy_http_history"))` is false by construction and passes just as happily against a superseded run that *did* render its row. Found because the **positive** form of the same assertion, written for S-08, failed against a row that was demonstrably on screen. Rows are now identified by their body text (`EMPTY_HISTORY_ROW`), which appears if and only if the row was rendered — and the fix was applied to 23-01's S-04 as well as to the new scenarios.

**2. The E10 negative clause was timing-dependent.** As first written, S-08 released the blocked chain step immediately after firing `/tool`, so the chain often dropped its permit inside the second call's 250 ms `tryAcquire` window and a *shared* limiter acquired anyway. Measured: under probe 6 the test stayed green. It now awaits the `/tool` worker's settle **before** releasing the chain, which puts the whole of the second call's acquisition inside the window where the first still holds the permit.

A third near-miss was avoided by measurement rather than repaired: `sendMessage` returns early when `sessionPanels[sessionId]` is null, so on the session-delete and project-change exits the "no followup turn" clause passes with the supersede removed. It is kept, labelled corroboration in both KDocs, and is a real clause only on the unload exit — where the panel survives. The plan's original budget variant B used the project change for exactly that reason and would have been vacuous; it was rewritten to supersede via a `/tool` command, which leaves the panel alive.

## Decisions Made

- **`deleteSession` split into a modal half and an `internal deleteConfirmedSession`.** `JOptionPane.showConfirmDialog` builds a `JDialog` and throws `HeadlessException` under `-Djava.awt.headless=true`, and every line of the teardown sits below it — so the plan's own Task 2 requirement to "drive the post-confirmation body directly" is unsatisfiable without the split. `internal` follows `clearChatState` and `MAX_AUTO_TOOL_ITERATIONS`, both widened for the same reason. No `assertEdt()` was added, because SC5's evidence is that the helper keeps exactly the call sites it had.
- **No `confirmed: Boolean` parameter and no overload.** That form would have satisfied the plan's `awk` acceptance criteria verbatim, and was rejected: a parameter whose only job is to state something at the declaration is exactly the WR-03 anti-pattern `resolvePending`'s own KDoc records this codebase burning itself on.
- **23-01's suggested per-cause `supersedeReason` parameter was not implemented.** Three of the four exits reach the supersede through `cancelInFlightRequest`, whose signature is a `detekt-baseline.xml` `ReturnCount` key and cannot gain a parameter without moving the pinned 1096. Per-cause reasons were therefore reachable for the session-delete exit alone, and a log that says `"cancelled"` for an unload is worse than one honest generic reason. Recorded here so 23-05 does not read the omission as an oversight.
- **S-08 asserts the measured consequence rather than the plan's wording.** The plan asks for "the chain is unharmed and still terminates at 8". The running-tool cell is panel-wide by design, so a `/tool` command takes the token and the chain's in-flight step lands in state S4 — no row, no followup. That is asserted explicitly, with the note that the interleave is barely reachable by a real user because the panel is in state S3 with the input disabled while a tool runs.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 2 - Missing Critical] Superseding on the session-delete exit left the panel permanently busy**

- **Found during:** Task 1
- **Issue:** the plan specifies one added call — `runningTool.take()`. Taking the token alone is a regression: `discardSupersededToolResult` deliberately does not touch the busy state, on the reasoning that "whatever superseded this run already returned the panel to S0". That is true for the other three exits, which all go through `cancelInFlightRequest`, and false for this one. Before the change the worker's success branch cleared the busy state; after it, nothing would — leaving the Send button hidden and the input disabled forever, across every session, with nothing running.
- **Fix:** `if (runningTool.take() != null) setSendingState(false)`. Conditional, because clearing unconditionally would clobber a backend turn still streaming in another session. No Rule C-1 transcript line is written, because the only transcript that could carry it is the one being deleted.
- **Files modified:** `ChatPanel.kt`
- **Verification:** S-05 drives the real teardown and the suite is green; the conditional is exercised on both branches across the 13 scenarios.
- **Committed in:** `089c940`

**2. [Rule 3 - Blocking] `deleteSession` had to be split, so three of Task 1's `awk` criteria target the extracted function**

- **Found during:** Task 1
- **Issue:** Task 1's criteria grep `awk '/private fun deleteSession\(/,/^    }$/'` for `runningTool` and `cancelInFlightRequest`, while Task 2 requires the post-confirmation body to be driven directly. Both cannot hold: the modal is the first statement of `deleteSession` and throws headlessly, so the teardown must move out of that awk range to be reachable at all.
- **Fix:** the equivalent property is verified on `deleteConfirmedSession` — `awk '/    internal fun deleteConfirmedSession\(/,/^    }$/' | grep -c 'runningTool'` returns 1, the same range mentions `cancelInFlightRequest` (twice, in the comment that names the exit it does *not* route through), and both the `runningTool` and `resolvePending` lines precede the `try {`. Every other Task 1 criterion passes verbatim.
- **Files modified:** `ChatPanel.kt`
- **Verification:** see the Verification Results table.
- **Committed in:** `089c940`

**3. [Rule 1 - Bug] Two new comments moved counters the phase gates on**

- **Found during:** Task 1 (acceptance-criteria gate)
- **Issue:** the path-4 and path-5 comments named `cancelInFlightRequest()`, pushing `awk clearInMemorySessionState | grep -c 'cancelInFlightRequest'` from 1 to 2 — a criterion that reads "one call, not two". The same class of defect as 23-01's deviation 7: a structural counter moved by prose rather than by code.
- **Fix:** both rewritten to describe the mechanism ("the in-flight cancel below") rather than name it, with a sentence saying why. The one place the symbol IS named is `deleteConfirmedSession`, where a criterion requires it.
- **Files modified:** `ChatPanel.kt`
- **Verification:** the criterion returns 1; `assertEdt()` is 6 and `SwingUtilities.invokeLater` is 11, both unmoved.
- **Committed in:** `089c940`

**4. [Rule 1 - Bug] The same defect in the test suite: a KDoc cross-reference moved the budget-test counter**

- **Found during:** Task 2
- **Issue:** the cancel variant's KDoc linked `[eightConsecutiveDenialsTerminateTheChainWithNoNinthTurn]`, pushing the criterion's `grep -c` from 1 to 2.
- **Fix:** rephrased to "the eight-denial budget test above".
- **Files modified:** `ChatPanelToolGateTest.kt`
- **Verification:** `grep -c` returns 1.
- **Committed in:** `76635fb`

**5. [Rule 1 - Bug] Budget variant B was vacuous as specified, and was rewritten**

- **Found during:** Task 2 (probe)
- **Issue:** the plan specifies a project-change supersede variant. With the supersede removed it stayed **green**: `clearInMemorySessionState` clears `sessionPanels`, and `sendMessage` returns early when a session's panel is null, so no second turn appears whether or not the worker was superseded.
- **Fix:** the variant now supersedes via a `/tool` command typed while the chain step is in flight — a different entry point that never touches `cancelInFlightRequest`, and one that leaves the panel alive so the un-superseded tail really does reach `sendChat`. Verified red under probe 3.
- **Files modified:** `ChatPanelToolGateTest.kt`
- **Verification:** probe 3 output above.
- **Committed in:** `76635fb`

**6. [Rule 1 - Bug] `contains("Tool result: ...")` is unfalsifiable; 23-01's S-04 carried it too**

- **Found during:** Task 3
- **Issue:** described in full under "Vacuity found and closed".
- **Fix:** rows identified by body text via the named `EMPTY_HISTORY_ROW` constant, whose KDoc records the measurement. Applied to S-04 as well as to S-05 and S-08.
- **Files modified:** `ChatPanelEdtConfinementTest.kt`
- **Verification:** S-08's positive form now passes against a rendered row and the negative form fails under probe 6.
- **Committed in:** `ae009e9`

**7. [Rule 3 - Blocking] `UnfinishedStubbingException` stubbing a void method on a deep stub**

- **Found during:** Task 2 (S-09)
- **Issue:** `doAnswer { }.whenever(h.api.scope()).includeInScope(...)` — leaving the deep-stub `h.api.scope()` call inside the `whenever(...)` argument makes Mockito read that call as the one being stubbed.
- **Fix:** the child mock is resolved to a local first; the comment names the cause.
- **Files modified:** `ChatPanelEdtConfinementTest.kt`
- **Verification:** S-09 passes.
- **Committed in:** `76635fb`

**8. [Rule 3 - Blocking] `InjectedToolFailure` could not be duplicated across the two suites**

- **Found during:** Task 3
- **Issue:** a file-private top-level *class* still produces a real JVM class in the package, so a second one of the same name is a redeclaration error — unlike the file-private consts and functions the two suites already duplicate (`NO_CARD`, `LONG_DRAIN`, `toolCall`, `click`, `pendingTraceId`, …).
- **Fix:** named `InjectedWorkerFailure` here, with a KDoc recording the constraint so the next person does not re-derive it.
- **Files modified:** `ChatPanelEdtConfinementTest.kt`
- **Verification:** compiles; S-12 passes.
- **Committed in:** `ae009e9`

**9. [Rule 3 - Blocking] The helper insertion orphaned a KDoc**

- **Found during:** Task 3 (ktlint gate)
- **Issue:** the insertion anchored on a `private fun` signature that sits below its KDoc, tripping `standard:no-consecutive-comments` — the identical mistake 23-02's deviation 4 records.
- **Fix:** the block moved below the function's closing brace; `ktlintFormat` for the residual blank line.
- **Files modified:** `ChatPanelEdtConfinementTest.kt`
- **Verification:** `ktlintCheck detekt` exits 0.
- **Committed in:** `ae009e9`

---

**Total deviations:** 9 auto-fixed (4 bugs, 1 missing critical, 4 blocking).
**Impact on plan:** No scope creep. One is a real production defect caught before commit (a permanently busy panel). Four are vacuous or counter-moving assertions — three of them mine, one inherited from 23-01 — every one caught by probing rather than by review. The remaining four are plan text or fixture assumptions colliding with a measured fact: a headless modal, a Mockito deep-stub rule, Kotlin's file-private class scoping, and a ktlint anchoring rule.

## Verification Results

| Gate | Result |
|---|---|
| `./gradlew ktlintCheck detekt test` | **exit 0** |
| `./gradlew test -PexcludeHeavyTests=true` | **exit 0** |
| `./gradlew edtGuardWithoutAssertionsTest` | **exit 0** — still green from wave 2 |
| `ChatPanelEdtConfinementTest` | **13 executed**, 0 failures (6 at wave 2; the plan asks for ≥ 11) |
| `ChatPanelToolGateTest` | **20 executed**, 0 failures (18 at wave 2) |
| `McpToolExecutorEdtGuardTest` | **3 executed**, 0 failures |
| `SettingsSaveAsyncTest` | **7 executed**, 0 failures |
| All four suites under `-PexcludeHeavyTests=true` | **non-zero executed counts** — they really do run on the PR gate |
| `grep -c '<ID>' detekt-baseline.xml` | **1096** — unmoved; `git diff --stat detekt-baseline.xml` empty |
| `grep -c 'assertEdt()' ChatPanel.kt` | **6** |
| `grep -c 'SwingUtilities.invokeLater' ChatPanel.kt` | **11** — no addition to justify; the new teardown adds no marshalling point |
| `grep -c 'Teardown path' ChatPanel.kt` | **5** |
| `awk shutdown \| grep -vE comments \| grep -cE '\.get\(\|\.join\('` | **0** — unload waits on nothing |
| `awk clearInMemorySessionState \| grep -c 'cancelInFlightRequest'` | **1** — one call, not two |
| `grep -c 'fun cancelInFlightRequest(): Boolean' ChatPanel.kt` | **1** — signature verbatim, so its baseline `ReturnCount` key still matches |
| `awk deleteConfirmedSession \| grep -c 'runningTool'` | **1**, and it precedes the `try {` |
| `grep -cE 'System\.currentTimeMillis\(\)\|System\.nanoTime\(\)' ChatPanelEdtConfinementTest.kt` | **0** — no wall-clock assertion anywhere in the suite |
| `grep -c 'eightConsecutiveDenialsTerminateTheChainWithNoNinthTurn' ChatPanelToolGateTest.kt` | **1**, with two new variants naming cancel and supersede |

`RedactionTest`'s known wall-clock flake did not surface in either full-suite run.

## Prohibitions — Held

| Prohibition | Evidence |
|---|---|
| A superseded call must never become an unlogged call | Every superseded exit emits exactly one `mcp_tool_decision` record and one AI-activity record, selected by trace id and asserted as `.single()` on both sides, in S-05, S-06, S-07 and S-09. |
| Unload must not gain a bounded blocking wait | `shutdown()`'s body contains no `.get(` or `.join(`; S-07 additionally proves the unbounded case as a deadlock. |
| `assertEdt()` byte-identical with 6 call sites | 6, and the new `deleteConfirmedSession` deliberately adds none. |
| `detekt-baseline.xml` unchanged | 1096, `git diff --stat` empty. No inline `@Suppress` was needed either. |
| No `git stash`, no destructive git | Every probe was a scripted forward-and-back edit with `git diff` verifying byte-identical restoration. |
| `README.md`'s pre-existing modification untouched | It appears in `git status` and in no commit of this plan. |

## Issues Encountered

None beyond the deviations. One out-of-scope finding was logged rather than fixed — see below.

## Deferred Issues

`.planning/phases/23-edt-confinement-ui-responsiveness/deferred-items.md` records **D-23-04-1**: `clearChatState()` (teardown path 3 of 5, the one D-08 does not list) does not supersede a running tool worker either, so a Clear Chat can be followed by a result row and a followup turn for the conversation the user just cleared. It is the same one-line shape as the session-delete fix, but it is new scope and raises a UI question this plan is not chartered to answer.

## User Setup Required

None — no external service configuration, and no package was added to `build.gradle.kts` or any lockfile. `build.gradle.kts` was not touched at all: `ChatPanel.kt` is already a declared `tasks.test` input, which is what makes the new source-text assertion re-run when its target changes.

## Next Phase Readiness

- **SC6 and the D-08 teardown clause are closed.** All four exits supersede, the audit pair survives every one, and unload returns without waiting.
- **For 23-05:** the frozen counters are unmoved — `assertEdt()` 6, `SwingUtilities.invokeLater` 11, `detekt-baseline.xml` 1096 — and `ChatPanel.kt` gained one new `internal` member (`deleteConfirmedSession`) that a structural scan will see.
- **For 23-05's UAT routing:** D9 above is a new human item — nothing automated exercises `deleteSession`'s modal half, by construction.
- **For 23-05's negative greps:** this plan added no `SwingWorker`, no coroutine, and no new marshalling point in `ChatPanel.kt`.
- **A note worth carrying:** three consecutive waves have shipped a test that passed with its defect fully present, each caught only by a red probe. The probe is not a formality in this phase; it is the only thing that has ever caught this class of defect here.

---
*Phase: 23-edt-confinement-ui-responsiveness*
*Completed: 2026-08-20*

## Self-Check: PASSED

- `.planning/phases/23-edt-confinement-ui-responsiveness/deferred-items.md` — FOUND
- `src/test/kotlin/com/six2dez/burp/aiagent/ui/ChatPanelEdtConfinementTest.kt` — FOUND (1445 lines, contains `class ChatPanelEdtConfinementTest`, 13 executed tests)
- `src/test/kotlin/com/six2dez/burp/aiagent/ui/ChatPanelToolGateTest.kt` — FOUND (1182 lines, 20 executed tests)
- `src/main/kotlin/com/six2dez/burp/aiagent/ui/ChatPanel.kt` — FOUND (contains `internal fun deleteConfirmedSession`)
- Commits `089c940`, `76635fb`, `ae009e9` — all present in `git log`
- Key link `ChatPanel.kt` → `RunningToolTracker` via `runningTool` in `deleteConfirmedSession` — present (1 occurrence, ahead of the `try`)
