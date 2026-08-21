---
phase: 23-edt-confinement-ui-responsiveness
plan: 07
subsystem: ui
tags: [swing, edt, busy-state, audit, supersede, kotlin, mcp]

# Dependency graph
requires:
  - phase: 23-edt-confinement-ui-responsiveness
    provides: "OffEdtDispatch — the single named-daemon dispatch + one-invokeLater tail seam (23-01)"
  - phase: 23-edt-confinement-ui-responsiveness
    provides: "The S3 busy lifecycle and RunningToolTracker supersede token (23-01)"
  - phase: 23-edt-confinement-ui-responsiveness
    provides: "discardSupersededToolResult — the chain path's supersede treatment this mirrors (23-04)"
provides:
  - "A busy seam that holds at both doors: openToolDialog refuses re-entry, toolsBtn goes inert, and a 1 Hz status tick can no longer restore the idle affordances"
  - "An MCP_TOOL_CALL audit record for a superseded user-originated tool call (WR-04)"
  - "OffEdtDispatch sinks wrapped so a throwing logger cannot cost the EDT tail (CR-04)"
  - "OffEdtDispatchFailurePathTest — a PR-gate-legal suite for the dispatcher's failure path"
  - "ChatPanelTestHarness.shownErrors + single-match component finders (toolsButton / inputTextArea)"
  - "Residual D-23-07-1 — CR-03 / D-23-04-1 restated as still open, with its UI question"
affects: [23-08, phase-26-quality]

actuals:
  tokens: 14137
  tasks: 3
  commits: 4

tech-stack:
  added: []
  patterns:
    - "A busy-state rule stated textually identically at every site it is enforced, so one structural grep finds them all"
    - "Escape capture as the falsifier for a sink inside a try/finally: the finally runs either way, so the observable difference is that the throwable reaches the AWT event pump"
    - "Row COUNT rather than row TEXT for a transcript-absence claim, because ChatMessagePanel renders every non-user role as the literal 'AI'"
    - "Single-match component finders that assert the match set has exactly one element before returning"

key-files:
  created:
    - src/test/kotlin/com/six2dez/burp/aiagent/ui/OffEdtDispatchFailurePathTest.kt
  modified:
    - src/main/kotlin/com/six2dez/burp/aiagent/ui/ChatPanel.kt
    - src/main/kotlin/com/six2dez/burp/aiagent/ui/OffEdtDispatch.kt
    - src/test/kotlin/com/six2dez/burp/aiagent/ui/ChatPanelEdtConfinementTest.kt
    - src/test/kotlin/com/six2dez/burp/aiagent/ui/ChatPanelTestHarness.kt

key-decisions:
  - "setSendingState's new toolsBtn write reads the FIELD (`!isSending`) rather than the parameter (`!sending`), against CR-05's literal snippet — it makes the rule textually identical at both of its enforcement sites and makes the plan's own acceptance criterion (three occurrences) true without relaxing it"
  - "Task 2's supersede is driven through Cancel, not through a session delete as the plan specified: deleteConfirmedSession removes the panel from sessionPanels and from the card layout, so a wrongly-rendered row would land in a detached transcript and the row-count clause could never fail"
  - "The two OffEdtDispatch sinks that sit inside the try needed an ESCAPE assertion, not the settle assertion the plan specified — the finally runs whether or not they throw, so 'the observer fired' is true with the bug present. Measured, not reasoned: probes 2 and 3 came back red only on the added clause"
  - "The transcript-absence claim is a row COUNT — the row-TEXT form is false by construction and is the exact vacuity 23-04 shipped"
  - "No toolDecisionReporter.report and no durationMs in the new audit record: these paths are UNGATED by SC5 so there is no decision to report, and the user paths measure no duration, so either would be a fabricated field"
  - "dispatchedObserver deliberately left unguarded, with a negative control test that fails if a blanket wrap is applied"

patterns-established:
  - "Red probes run against a COMMITTED baseline, so `git checkout -- <file>` restores the correct implementation rather than discarding it"
  - "A probe whose target sits inside a try/finally must assert the escape, not the finally's own side effect"

requirements-completed: [REL-05]

coverage:
  - id: D1
    description: "A second tool call cannot be started from the UI while one is in flight — openToolDialog refuses re-entry as its first statement, before any session, dialog or token exists (CR-05, UI-SPEC Rule S-1). 23-01's must-have truth that S3 is global to ChatPanel is now true"
    requirement: "REL-05"
    verification:
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/ui/ChatPanelEdtConfinementTest.kt#aSecondToolCannotBeStartedWhileOneIsRunning"
        status: pass
    human_judgment: false
  - id: D2
    description: "The Tools button is inert for the whole tool run, so the second door is not merely uncontrolled but not offered (UI-SPEC Rule S-1)"
    requirement: "REL-05"
    verification:
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/ui/ChatPanelEdtConfinementTest.kt#theToolsButtonIsInertForTheWholeToolRun"
        status: pass
    human_judgment: false
  - id: D3
    description: "A status refresh arriving mid-run cannot silently restore the S0 affordances: updateChatAvailability respects isSending, so mcpStatusTimer's 1 Hz tick and 23-06's asynchronous renderStatus tail both leave the busy state intact (CR-05 timer limb, D-06, D-09)"
    requirement: "REL-05"
    verification:
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/ui/ChatPanelEdtConfinementTest.kt#aStatusRefreshMidRunDoesNotRestoreTheIdleAffordances"
        status: pass
    human_judgment: false
  - id: D4
    description: "A superseded user-originated tool call is no longer invisible on every surface: its EDT tail emits an MCP_TOOL_CALL record naming the discarded tool, its runStatus and the supersede reason, and renders no result row (WR-04, D-07's load-bearing corollary)"
    requirement: "REL-05"
    verification:
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/ui/ChatPanelEdtConfinementTest.kt#aSupersededUserOriginatedToolCallIsStillAudited"
        status: pass
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/ui/ChatPanelEdtConfinementTest.kt#aSupersededUserOriginatedToolCallRendersNoResultRow"
        status: pass
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/ui/ChatPanelEdtConfinementTest.kt#anUnsupersededUserOriginatedToolCallEmitsNoSupersedeRecord"
        status: pass
    human_judgment: false
  - id: D5
    description: "OffEdtDispatch cannot lose its EDT tail to a throwing error sink: a failed unit of work still clears the busy state, still discharges its completion callback and still records as settled, and the Result crosses the thread boundary carrying the ORIGINAL throwable (CR-04, T-23-07-06)"
    requirement: "REL-05"
    verification:
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/ui/OffEdtDispatchFailurePathTest.kt#aThrowingErrorSinkDoesNotCostTheEdtTail"
        status: pass
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/ui/OffEdtDispatchFailurePathTest.kt#aThrowingErrorSinkInTheTailDoesNotCostTheSettleRecord"
        status: pass
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/ui/OffEdtDispatchFailurePathTest.kt#aThrowingSettleObserverDoesNotEscapeTheTail"
        status: pass
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/ui/OffEdtDispatchFailurePathTest.kt#theDispatchObserverIsDeliberatelyNotGuarded"
        status: pass
    human_judgment: false
  - id: D6
    description: "The refusal copy 'A request is already running. Cancel it first.' reaches the user as a modal, in house style and legible in both themes"
    verification: []
    human_judgment: true
    rationale: "showError is routed to a JOptionPane by MainTab, and JOptionPane.getRootFrame() throws HeadlessException under -Djava.awt.headless=true, which tasks.test sets. The suite proves the production code passed exactly this string to its real showError sink; that it renders acceptably is a live-Burp observation. It is also a FOURTH new UI string, outside the UI-SPEC Copywriting Contract's enumeration — see Flagged Items."
  - id: D7
    description: "In live Burp, a multi-second tool run holds state S3 for its whole duration with the mcpStatusTimer ticking every second underneath it"
    verification: []
    human_judgment: true
    rationale: "The headless suite drives setMcpAvailable(true) directly, which is the production entry point the timer reaches, but the timer itself is a Swing Timer owned by MainTab and not constructed in the ChatPanel fixture. The 1 Hz arrival in a real Burp session is a live observation. Routed to 23-HUMAN-UAT.md."

# Metrics
duration: 27min
completed: 2026-08-21
status: complete
---

# Phase 23 Plan 07: CR-04 / CR-05 / WR-04 Gap Closure Summary

**A busy seam that actually holds at both doors — an `openToolDialog` entry guard plus `isSending`-aware affordance gating that a 1 Hz status tick can no longer undo — an audit record for a discarded user-originated tool call, and an `OffEdtDispatch` whose EDT tail cannot be lost to a throwing logger.**

## Performance

- **Duration:** 27 min
- **Started:** 2026-08-21T09:15:00Z
- **Completed:** 2026-08-21T09:42:00Z
- **Tasks:** 3
- **Files modified:** 5 (1 created, 4 modified)

## Accomplishments

- **23-01's must-have truth is now true rather than claimed.** *"S3 is global to `ChatPanel`, so a second dispatch cannot be started from the UI while the first is live"* was false in three independent ways: `toolsBtn` was gated purely on `mcpAvailable`, `openToolDialog` had no re-entrancy check at all, and `updateChatAvailability` re-enabled the input area and the tool button from `mcpAvailable` alone. All three are closed, and each is asserted by a test that goes red on its own limb.
- **The timer limb was the one that mattered, and it was live in shipped code.** `MainTab.mcpStatusTimer` fires every 1000 ms into `setMcpAvailable`, so one second into *any* tool run the S3 disabled input area and the tool button were both silently re-enabled — no second dialog, no exotic setup, just waiting. Plan 23-06 flagged this to us as a cross-plan hand-off; it reproduced against the tree as committed on the first attempt.
- **WR-04 closed as an inseparable consequence of CR-05.** A user-originated call that reached Burp and hit a real target used to vanish from every surface when superseded. It now emits an `MCP_TOOL_CALL` record with `runStatus` and `supersedeReason`, mirroring the chain path's `discardSupersededToolResult` — same reach, same restraint.
- **CR-04 closed with a negative control.** Every sink between a failed unit of work and the EDT tail is wrapped, so the contract is that the tail is unreachable only if `SwingUtilities.invokeLater` itself fails. `dispatchedObserver` stays deliberately unguarded, and a fourth test fails if anyone applies a blanket wrap.
- **Ten red probes executed, all RED, each on its intended clause** — and two of them caught assertions the plan itself had specified in a vacuous form. See the Deviations section: this is the third and fourth time in this phase that running a gate, rather than reading it, was what found the hole.
- **Every frozen invariant held.** `assertEdt` 6, `SwingUtilities.invokeLater` 11, `detekt-baseline.xml` 1096 with an empty diff, `build.gradle.kts` untouched, `README.md` in zero commits, no file under `src/main/.../mcp/` reached.

## Task Commits

1. **Task 1 (tracer): the busy seam at both doors** — `9e9231c` (fix)
2. **Task 2 (TDD): audit the superseded user-originated call**
   - `96a9831` (test) — RED: 23 tests, 1 failing on `captured []`
   - `f9e5d88` (feat) — GREEN: 23 tests, 0 failures
3. **Task 3: the dispatcher's tail cannot be lost to a sink** — `3722669` (fix)

## Red-Probe Ledger

Ten probes. Each reversed exactly ONE production statement — never `git stash` (banned in this repo), never an edit to a test — ran the target suite, and was restored to a byte-identical file.

**Protocol correction, recorded because it nearly cost work:** the first probe was run against *uncommitted* Task 1 edits, and `git checkout -- <file>` restored the file to HEAD, discarding them. Every subsequent probe was run against a **committed** baseline, which is what makes `git checkout --` a restore rather than a revert, and what makes "restored diff empty" a meaningful claim. The plan's phrasing presumes this; it is written down here so the next executor does not rediscover it the same way.

| # | Task | File | Statement reversed | Failing test | Failing line | Restored diff empty |
|---|---|---|---|---|---|---|
| 1 | 1 | `ChatPanel.kt` | `if (isSending) { showError(...); return }` deleted from `openToolDialog` | `aSecondToolCannotBeStartedWhileOneIsRunning` — failed with `java.awt.HeadlessException` from `ToolInvocationDialog.<init>` via `ChatPanel.kt:1128`, i.e. execution really reached the dialog | test `:1213` | ✓ (0 bytes) |
| 2 | 1 | `ChatPanel.kt` | `updateChatAvailability`'s two `&& !isSending` terms reverted | `aStatusRefreshMidRunDoesNotRestoreTheIdleAffordances` — failed on the **input-area** clause | test `:1304` | ✓ |
| **3** | 1 | `ChatPanel.kt` | **`setSendingState`'s `toolsBtn` line only** | **`theToolsButtonIsInertForTheWholeToolRun` — and `aStatusRefreshMidRun…` still PASSED (1 failure total), proving the two limbs are asserted independently** | test `:1257` | ✓ |
| 4 | 2 | `ChatPanel.kt` | The `aiRequestLogger?.log(...)` emission deleted, bare `return` restored | `aSupersededUserOriginatedToolCallIsStillAudited` — `captured []` | test `:1359` | ✓ |
| 5 | 2 | `ChatPanel.kt` | Emission hoisted ABOVE `clearIfMatches`, so it fires on every completion | `anUnsupersededUserOriginatedToolCallEmitsNoSupersedeRecord` — the negative control is not vacuous | test `:1458` | ✓ |
| **6** | 2 | `ChatPanel.kt` | **`if (!runningTool.clearIfMatches(token)) return` deleted entirely, so a superseded worker falls through and renders** | **`aSupersededUserOriginatedToolCallRendersNoResultRow` — failed on its row-COUNT clause** (`aSupersededUserOriginatedToolCallIsStillAudited` also failed collaterally) | test `:1417` | ✓ |
| 7 | 3 | `OffEdtDispatch.kt` | Failure-path `logError` unwrapped to a bare call | `aThrowingErrorSinkDoesNotCostTheEdtTail` — failed on the settle await: the worker died before `invokeLater` was reached | test `:100` | ✓ |
| **8** | 3 | `OffEdtDispatch.kt` | **`settledObserver?.invoke(label)` unwrapped** | **`aThrowingSettleObserverDoesNotEscapeTheTail` — failed ONLY on the escape clause; the "both dispatches settled" clause passed** | test `:212` | ✓ |
| **9** | 3 | `OffEdtDispatch.kt` | **Tail-handler `logError` unwrapped** | **`aThrowingErrorSinkInTheTailDoesNotCostTheSettleRecord` — failed ONLY on the escape clause; the settle clause passed** | test `:158` | ✓ |
| 10 | 3 | `OffEdtDispatch.kt` | `dispatchedObserver?.invoke(label)` WRAPPED in `runCatching` — the blanket edit | `theDispatchObserverIsDeliberatelyNotGuarded` — "Expected `InjectedObserverFailure` to be thrown, but nothing was thrown" | test `:237` | ✓ |

**Probes 8 and 9 are the ones that earned their keep.** Both targets sit *inside* the `try`, so the `finally` runs whether or not they throw and the settle observer fires either way. The plan's specified assertions — "assert the settle observer still fired" and "assert a subsequent dispatch still settles" — are therefore true with the defect fully present. Both probes confirm this directly: the settle clauses passed under the reversal, and only the escape clause discriminated. Had the tests been written as specified, two more vacuous assertions would have shipped in a phase that has already shipped six.

**Probe 6 is the anti-vacuity result the plan predicted.** It came back red on the row-COUNT clause, confirming the count form is falsifiable where the row-TEXT form (`contains("Tool result: …")`) is false by construction — `ChatMessagePanel` renders every non-user role as the literal `"AI"`, so the role string reaches no component at all.

## Files Created/Modified

- `src/main/kotlin/com/six2dez/burp/aiagent/ui/ChatPanel.kt` — `openToolDialog` entry guard; `setSendingState` gains the `toolsBtn` write; `updateChatAvailability` gains two `!isSending` terms and a KDoc naming the measured 1 Hz timer; `finishUserOriginatedToolCall` gains a `sessionId` parameter and an audit emission in its supersede branch; both call sites updated.
- `src/main/kotlin/com/six2dez/burp/aiagent/ui/OffEdtDispatch.kt` (129 lines) — `runCatching` around both `logError` sinks and around `settledObserver?.invoke`; KDoc states the resulting contract and the `dispatchedObserver` asymmetry as a decision.
- `src/test/kotlin/com/six2dez/burp/aiagent/ui/OffEdtDispatchFailurePathTest.kt` (**new**, 334 lines) — 4 scenarios including the negative control, plus `capturingEventPumpOutput`, the escape-capture helper.
- `src/test/kotlin/com/six2dez/burp/aiagent/ui/ChatPanelEdtConfinementTest.kt` (2173 lines) — 6 new scenarios (17 → 23), the `ActivityRecord` / `toolCallRecords` reader, `transcriptRowCount`, `harnessForUserOriginatedTool`.
- `src/test/kotlin/com/six2dez/burp/aiagent/ui/ChatPanelTestHarness.kt` — `Harness.shownErrors` recorder wired to the real `showError` constructor lambda; `findAll` / `findExactlyOne` / `toolsButton` / `inputTextArea`.

## Decisions Made

See `key-decisions` in the frontmatter. Three are worth restating in prose:

**Why `inputTextArea` is found by geometry rather than by `isEditable`.** The harness's existing input lookup uses `isEditable`, which is correct for typing into it. It is wrong for asserting the input is *disabled*: a disabled `JTextArea` is still editable, so the `isEditable` predicate keeps matching the `ToolApprovalCard` argument box too. The new finder keys off `rows == 3 && columns == 24` — the constructed geometry — and asserts a single match.

**Why the escape capture is the only available surface.** `SwingUtilities.invokeLater` wraps its runnable in an `InvocationEvent` built *without* `catchThrowables` (unlike `invokeAndWait`), so a throwable escaping the runnable propagates into `EventDispatchThread`, which has no installed handler and prints it. The pump then carries on dispatching — which is exactly why "a later dispatch still works" cannot carry those tests. Determinism comes from an `invokeAndWait` drain: the pump prints inline while dispatching the offending event, and an empty runnable queued afterwards cannot run until that dispatch has completed. Safe to swap `System.err` because `build.gradle.kts` configures neither parallel forks nor JUnit parallel execution.

**Why `!isSending` rather than CR-05's `!sending`.** The plan's `<action>` quotes the review's snippet (`mcpAvailable && !sending`) while its acceptance criterion counts `!isSending` at three sites, one of them in `setSendingState`. Reading the field is functionally identical — `isSending = sending` is the function's first statement — and it makes the rule *"the tool button is live iff MCP is up and the panel is not busy"* one fact stated one way at both of its enforcement sites, which is what a structural gate on it should be able to find. The ordering dependency is pinned in the KDoc.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Two Task 3 assertions were specified in a form that cannot fail**
- **Found during:** Task 3
- **Issue:** The plan specifies `aThrowingErrorSinkInTheTailDoesNotCostTheSettleRecord` as "assert the settle observer still fired" and `aThrowingSettleObserverDoesNotEscapeTheTail` as "assert the dispatch completes and a subsequent dispatch still settles". Both targets sit inside the `try`, so the `finally` runs regardless and the settle record appears with the defect fully present; and the AWT event pump survives an uncaught throwable and keeps dispatching, so a later dispatch settles either way. As specified, probes 8 and 9 would both have come back green against the unwrapped sinks.
- **Fix:** Kept the specified clauses (they are the claims a maintainer cares about, and they fail loudly if the pump ever stops being forgiving) and added the clause that actually discriminates: nothing escaped into the AWT event pump, read through a scoped `System.err` capture with an `invokeAndWait` happens-before.
- **Files modified:** `src/test/kotlin/com/six2dez/burp/aiagent/ui/OffEdtDispatchFailurePathTest.kt`
- **Verification:** Probes 8 and 9 came back RED **only** on the added clause, with the specified clauses passing — the vacuity confirmed by measurement rather than argued.
- **Committed in:** `3722669`

**2. [Rule 1 - Bug] The Task 2 supersede driver would have made the row-count clause vacuous**
- **Found during:** Task 2
- **Issue:** The plan's `<behavior>` specifies superseding via a session delete. `deleteConfirmedSession` removes the panel from `sessionPanels` and from the `CardLayout`, so a wrongly-rendered result row lands in a detached transcript that `allDescendants(h.panel.root)` cannot see. Probe 6 — which deletes the early return precisely to make that row render — would have come back green.
- **Fix:** Superseded through the real Cancel button instead. It is a first-class user-originated supersede (UI-SPEC Rule S-5), the panel survives it, and the Rule C-1 line Cancel appends is folded into the baseline count by snapshotting after the cancel and before the release.
- **Files modified:** `src/test/kotlin/com/six2dez/burp/aiagent/ui/ChatPanelEdtConfinementTest.kt`
- **Verification:** Probe 6 RED on the row-COUNT clause at `:1417`.
- **Committed in:** `96a9831`

**3. [Rule 1 - Bug] Plan-internal contradiction between Task 1's action and its acceptance criterion**
- **Found during:** Task 1
- **Issue:** The action says `toolsBtn.isEnabled = mcpAvailable && !sending`; the criterion requires three comment-filtered occurrences of `!isSending`, one of them in `setSendingState`. Writing the action verbatim yields 2, failing the gate.
- **Fix:** Resolved in favour of the criterion — the field read is functionally identical here and makes both enforcement sites textually identical. Recorded rather than relaxing the gate.
- **Files modified:** `src/main/kotlin/com/six2dez/burp/aiagent/ui/ChatPanel.kt`
- **Verification:** Filtered count is exactly 3; probe 3 still drives the `setSendingState` limb red independently.
- **Committed in:** `9e9231c`

**4. [Rule 3 - Blocking] A `val` assigned inside a lambda does not compile**
- **Found during:** Task 2 (RED phase)
- **Issue:** `val rowsAfterCancel: Int` assigned inside `assertTimeoutPreemptively`'s lambda — `Captured values cannot be initialized because of possible reassignments`.
- **Fix:** `AtomicInteger`.
- **Files modified:** `src/test/kotlin/com/six2dez/burp/aiagent/ui/ChatPanelEdtConfinementTest.kt`
- **Verification:** `compileTestKotlin` green; RED run then reported 23 tests, 1 failing as intended.
- **Committed in:** `96a9831`

---

**Total deviations:** 4 auto-fixed (3 bugs, 1 blocking).
**Impact on plan:** No scope creep. Deviations 1 and 2 are the substantive ones: both are instances of the exact failure mode this phase exists to guard against — an assertion that is green while the defect is present — and both were caught by *running* the probe rather than by reading the assertion. Deviation 1 in particular means the plan as written would have shipped two more vacuous tests.

## Flagged Items

**1. A fourth UI string, outside the Copywriting Contract.** `"A request is already running. Cancel it first."` is introduced by `openToolDialog`'s entry guard, beyond the three strings UI-SPEC's Copywriting Contract enumerates. Taken verbatim from CR-05's proposed fix; house style matched (sentence case, terminal period, ASCII only, no ellipsis). Flagged here so the UI checker sees it rather than discovering it. Every string in the "Copy that must NOT change" table is byte-unchanged — `"MCP server is not running."` still occurs exactly once.

**2. Review finding IN-01 is closed as an inseparable consequence, not as absorbed scope.** `isSending` was written at `:1012` and never read anywhere. `updateChatAvailability` now reads it, which is the whole of CR-05's timer limb — the finding could not have been closed separately without making the same change, and the change could not have been made without closing it.

## Residual Recorded

**`D-23-07-1` — CR-03 / D-23-04-1 remains OPEN and was NOT made a freebie by this plan.**

`clearChatState()` still does not supersede a running tool worker. Closing CR-05 does **not** reduce it to a one-line addition: CR-05's fix guards `openToolDialog`'s *entry*, which is a different path from `clearChatState`'s *teardown*. The two share no code and the guard has no effect on a worker that is already running when Clear Chat is pressed.

Its open UI question is unchanged and must be answered before anyone writes the line:

> *"whether Clear Chat should return the panel to S0 while a worker is still running, or leave the busy state alone as `discardSupersededToolResult` does."*

**Ledger ownership:** this text is handed over here for **plan 23-08 (wave 2)** to transcribe into `deferred-items.md`. That file was deliberately not edited by this plan — 23-08 is its single owner after wave 1, so that two wave-1 plans do not write the same file.

## Known Stubs

None. No hardcoded empty values, placeholder text, `TODO`/`FIXME` markers, skipped tests or unrun `<verify>` commands were introduced.

## Threat Flags

None. No file created or modified by this plan introduces security-relevant surface outside the plan's `<threat_model>`: no new network endpoint, no auth path, no file access pattern and no schema change at a trust boundary. All six dispositioned threats are `mitigate` and all six are discharged — `T-23-07-01` by Task 2, `T-23-07-02`/`T-23-07-03`/`T-23-07-04` by Task 1, `T-23-07-05`/`T-23-07-06` by Task 3.

## Verification Results

| # | Check | Result |
|---|---|---|
| 1 | `./gradlew ktlintCheck detekt test` | **exit 0** (2m 37s) |
| 2 | `./gradlew edtGuardWithoutAssertionsTest` | **exit 0**, `McpToolExecutorEdtGuardTest` **3** tests, 0 failures |
| 3 | `./gradlew test -PexcludeHeavyTests=true` | **exit 0**. `ChatPanelEdtConfinementTest` **23**/0, `McpToolExecutorEdtGuardTest` **3**/0, `SettingsSaveAsyncTest` **10**/0, `OffEdtDispatchFailurePathTest` **4**/0. **Control:** no result file for `ChatPanelConcurrencyTest`, so the filter demonstrably WAS applied |
| 4 | `ChatPanel.kt` `assertEdt` (UNFILTERED) · `SwingUtilities.invokeLater` | **6** · **11** — both unmoved. `git diff 2a0c703 -- ChatPanel.kt` contains **no** hunk touching the `assertEdt` declaration or any of its four invocations |
| 5 | `git diff --stat detekt-baseline.xml` · `grep -c '<ID>'` | empty · **1096**. No new detekt finding fired, so no new inline `@Suppress` was needed |
| 6 | `git diff --stat build.gradle.kts` | empty — plan 23-06 owns that file this wave |
| 7 | `git diff --stat src/main/.../mcp/` for **this plan's** four commits | empty. The `McpToolExecutorImpl.kt` change visible since `2a0c703` belongs to commit `b9e9748` (plan 23-02, D-04's inverse guard). **D-14 not reached** |
| 8 | `git status --short` | `README.md` still carries its pre-existing modification and appears in **0** commits of this plan |
| 9 | Ten red probes | all recorded above with failing test, failing line and a restored diff of 0 bytes |

**Structural counts (block-comment-filtered with the house filter, `ChatPanel.kt`):** `!isSending` = **3** (baseline 0), `supersedeReason` = **2** (baseline 1), `toolDecisionReporter.report` = **6** (unchanged), `durationMs` = **9** (unchanged), `finishUserOriginatedToolCall(` = **3** (declaration + two call sites, both passing `sessionId`).

**Structural counts (`OffEdtDispatch.kt`, filtered):** `runCatching { logError` = **2**, `runCatching { settledObserver` = **1**, `runCatching { dispatchedObserver` = **0**, `SwingUtilities.invokeLater` = **1**. The **unfiltered** `invokeLater` count moved 2 → **3** because this task's KDoc extension names the identifier a third time — exactly the trap the plan predicted, and the reason that criterion is block-filtered. `Thread(body, threadName).apply { isDaemon = true }` = **1**, so the SC6 idiom and the daemon flag are unchanged.

**No new assertion reads transcript text for the supersede claim:** `Tool result:` appears in `ChatPanelEdtConfinementTest.kt` only inside KDoc explaining why that form must not be used — never inside a test body.

## Issues Encountered

None beyond the four auto-fixed deviations. `RedactionTest` did not flake in either full-suite run.

## User Setup Required

None — no external service configuration required.

## Next Phase Readiness

- **CR-04, CR-05 and WR-04 are closed**, each with at least one assertion driven red on its own limb, and IN-01 is closed alongside CR-05.
- **Plan 23-08 (wave 2) must transcribe `D-23-07-1` into `deferred-items.md`** — the text is above, verbatim, including the UI question. It is the single owner of that ledger after wave 1.
- **Two live-Burp observations are routed to `23-HUMAN-UAT.md`**: the refusal modal's copy (coverage D6) and the 1 Hz timer arriving during a real multi-second tool run (coverage D7).
- **`FLAG-23-04` is untouched** — the sub-frame `Send`↔`Cancel` flicker on the auto-approved chain path is neither closed nor worsened, because this plan added no `invokeLater` to `ChatPanel.kt`.
- **For any plan that edits `ChatPanel.kt` next:** re-measure `assertEdt` at **6** *unfiltered* — the count is 1 declaration + 4 invocations + 1 comment, so the house block-comment filter returns 5 and would destroy SC5's evidence.

## Self-Check: PASSED

- All 5 files in `key-files` verified present on disk with `[ -f ]`.
- All 4 commit hashes verified with `git log --oneline --all`.
- All three phase gates re-run and green after the final production commit.
- All ten red probes restored to a 0-byte diff, verified individually.

---
*Phase: 23-edt-confinement-ui-responsiveness*
*Completed: 2026-08-21*
