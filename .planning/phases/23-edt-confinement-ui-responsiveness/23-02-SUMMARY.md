---
phase: 23-edt-confinement-ui-responsiveness
plan: 02
subsystem: ui
tags: [swing, edt, threading, mcp, rel-05, sec-06, sc5]

requires:
  - phase: 23-edt-confinement-ui-responsiveness
    provides: "OffEdtDispatch (named daemon thread + one invokeLater tail + dispatch/settle observers), RunningToolTracker/RunningToolToken, ChatPanelTestHarness.awaitToolSettled / dispatchedLabels, ChatPanelEdtConfinementTest"
  - phase: 22-sec-06-tool-approval-gate
    provides: "ToolApprovalGate / ToolApprovalOutcome, ToolCallOrigin, ChatPanelToolGateTest and its SC5 structural assertions"
provides:
  - "A throwing EDT precondition as the FIRST statement of McpToolExecutor.executeToolResult — the door every caller passes through, fires in shipped Burp where -ea does nothing"
  - "openToolDialog and handleToolCommand's /tool branch dispatched off the EDT through OffEdtDispatch.run, with UI-SPEC state S3 and a supersede token minted on the EDT first"
  - "ChatPanel.finishUserOriginatedToolCall — the shared EDT tail for the two ungated user-originated paths"
  - "The Rule S-4 /tool transcript echo: the typed command is visible (and sent to the model, FLAG-23-03) before the worker starts"
  - "McpToolExecutorEdtGuardTest — scenario S-10, including the F-4 ext: placement test"
  - "Gradle task edtGuardWithoutAssertionsTest — runs the guard suite with -da, demonstrating SC1's 'must not rely on -ea' clause"
  - "ChatPanelEdtConfinementTest S-03 — a Deny starts no worker, read from the dispatch record"
affects: [23-03, 23-04, 23-05, chatpanel, mcp-tools, settings]

actuals:
  tokens: 86548
  tasks: 4
  commits: 2

tech-stack:
  added: []
  patterns:
    - "Guard at the convergence point, not at each caller: one precondition on executeToolResult covers the chat path, the MCP-server path and the ext: branch"
    - "check(...) over the JVM assertion facility for any invariant that must hold in a shipped artifact — measured, not assumed (see the -da probe below)"
    - "A dedicated Gradle Test task with a scoped JVM flag, when the claim under test IS the flag"

key-files:
  created:
    - src/test/kotlin/com/six2dez/burp/aiagent/mcp/tools/McpToolExecutorEdtGuardTest.kt
  modified:
    - src/main/kotlin/com/six2dez/burp/aiagent/mcp/tools/McpToolExecutorImpl.kt
    - src/main/kotlin/com/six2dez/burp/aiagent/ui/ChatPanel.kt
    - src/test/kotlin/com/six2dez/burp/aiagent/ui/ChatPanelEdtConfinementTest.kt
    - src/test/kotlin/com/six2dez/burp/aiagent/ui/ChatPanelToolGateTest.kt
    - build.gradle.kts

key-decisions:
  - "Task 1's checkpoint resolved to `door-guard` (option A) — the placement D-03 and D-04 already lock; no locked decision was overridden"
  - "The -ea-off demonstration is a dedicated Gradle task (edtGuardWithoutAssertionsTest) rather than a scoped -da on tasks.test, because a task-wide -da would silently weaken every other suite's assertions to buy one suite's evidence"
  - "FLAG-23-03 answered YES: the /tool command is appended to session.messages as a user ChatMessage, removing the asymmetry with openToolDialog rather than preserving it"
  - "One shared EDT tail (finishUserOriginatedToolCall) for both ungated paths, while each executeTool call stays lexically inside its own function — the structural SC5 test reads that source text"
  - "The two user-originated tails emit no audit decision pair, because SC5 makes these paths ungated and there is no approval record to report; only the approved-chain tail has one"

patterns-established:
  - "Prove a flag-dependent claim by flipping the mechanism, not by reading the flag: swapping check(...) for the JVM assertion facility made the -da task report 'Thrown was: null' while the -ea task saw it throw"
  - "A placement test must fail when the statement moves one line: the ext: guard test goes red — and only that test — when the guard is moved below the external early return"

requirements-completed: [REL-05]

coverage:
  - id: D1
    description: "executeToolResult entered from the EDT throws IllegalStateException whose message names REL-05 and points the caller at OffEdtDispatch"
    requirement: "REL-05"
    verification:
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/mcp/tools/McpToolExecutorEdtGuardTest.kt#executingABuiltInToolOnTheEdtIsRefused"
        status: pass
    human_judgment: false
  - id: D2
    description: "The guard precedes the ext: early return, so routeExternalToolCall's runBlocking is covered (SC2, finding F-4)"
    requirement: "REL-05"
    verification:
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/mcp/tools/McpToolExecutorEdtGuardTest.kt#theGuardPrecedesTheExternalToolEarlyReturn"
        status: pass
    human_judgment: false
  - id: D3
    description: "The guard does not fire off the EDT — the MCP-server path, already on Ktor coroutines, reaches past it"
    requirement: "REL-05"
    verification:
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/mcp/tools/McpToolExecutorEdtGuardTest.kt#theSameCallOffTheEdtReachesPastTheGuard"
        status: pass
    human_judgment: false
  - id: D4
    description: "The guard fires in a JVM with assertions disabled — SC1's 'the new check must not rely on -ea', demonstrated rather than asserted"
    requirement: "REL-05"
    verification:
      - kind: other
        ref: "JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew edtGuardWithoutAssertionsTest (3 tests, 0 failures, jvmArgs -da)"
        status: pass
    human_judgment: false
  - id: D5
    description: "All three chat call sites dispatch through OffEdtDispatch; the /tool path still reaches Burp exactly once and is still not gated (SC5)"
    requirement: "REL-05"
    verification:
      - kind: integration
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/ui/ChatPanelToolGateTest.kt#slashCommandPathIsNotDoublePrompted"
        status: pass
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/ui/ChatPanelToolGateTest.kt#userDialogPathIsNotDoublePrompted"
        status: pass
    human_judgment: false
  - id: D6
    description: "A Deny produces zero executeToolResult invocations, captures no worker thread, leaves no entry in the dispatch record, and still returns the denial to the model (AI-SPEC E1, S-03)"
    requirement: "REL-05"
    verification:
      - kind: integration
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/ui/ChatPanelEdtConfinementTest.kt#aDeniedToolCallStartsNoWorkerAndStillContinuesTheConversation"
        status: pass
    human_judgment: false
  - id: D7
    description: "The /tool transcript shows the typed command before it shows the result, and both user-originated paths enter UI-SPEC state S3 (busy, Cancel live) before dispatch"
    verification: []
    human_judgment: true
    rationale: "No committed test asserts either property. The echo-before-dispatch ordering and the S3 entry were verified at execution time by source-order greps (`addMessage(\"You\"` and `setSendingState(true)` both precede `OffEdtDispatch.run` inside each function), which is a structural check made by the executor rather than a guard that re-runs on the PR gate. Whether the echo READS as the request that produced the result, and whether the disabled input is legible in a live Burp Look-and-Feel, are rendering properties a headless JEditorPane never paints. Routed to 23-HUMAN-UAT.md by plan 23-05."

duration: 25 min
completed: 2026-08-20
status: complete
---

# Phase 23 Plan 02: The Executor Door Guard Summary

**`McpToolExecutor.executeToolResult` now refuses the EDT with an `IllegalStateException` that fires in shipped Burp, and the two remaining user-originated tool call sites dispatch off the EDT with the transcript echoing the command before the worker starts.**

## Performance

- **Duration:** 25 min
- **Started:** 2026-08-20T18:55:30Z
- **Completed:** 2026-08-20T19:20:43Z
- **Tasks:** 4 (1 decision checkpoint, 2 implementation, 1 verification-only wave gate)
- **Files modified:** 6 (1 created, 5 modified)

## Task 1 — the decision, recorded

**Chosen option: `door-guard` (option A).**
**Chosen: 2026-08-20, auto-selected by the executor under the project's `mode: yolo` configuration** (`.planning/config.json`), on a checkpoint carrying `gate="blocking"` — the auto-approvable gate, not `gate="blocking-human"`.

**No locked decision was overridden.** `door-guard` is precisely what `23-CONTEXT.md` D-03 (*the guard throws*) and D-04 (*placement at `executeToolResult`, not `executeTool`*) already lock, and D-04's "confirm before locking" check had already been run and recorded in CONTEXT before this plan was written. The checkpoint existed to put the accepted cost — a missed call site turning a UI freeze into a tool error in the field, threat row T-23-06, disposition `accept` — in front of the maintainer, not to reopen the placement. Both alternatives were rejected in CONTEXT with reasons this execution did not disturb: `wrapper-guard` covers only the chat half (the MCP-server path calls `executeToolResult` directly), and `helper-only` reverts the guarantee from *enforced* to *correct today*.

No source file was edited by Task 1.

## Accomplishments

- **The door guard.** `check(!SwingUtilities.isEventDispatchThread())` is the first statement of `executeToolResult` (`McpToolExecutorImpl.kt:159`), ahead of `canonicalToolId` and ahead of the `ext:` early return at `:170`. One line covers the three chat call sites, the MCP-server path (`McpToolHandlers.kt:129`), `routeExternalToolCall`'s `runBlocking` and both `sendRequest` sites.
- **The `-ea` claim is demonstrated, not asserted** — see the probe below. This was the one part of the plan that could have been quietly satisfied by writing an assertion and declaring victory.
- **Both user-originated call sites moved off the EDT**, each keeping its `McpToolExecutor.executeTool(` call lexically inside its own function so the structural SC5 test still reads what it was written to read, and each entering UI-SPEC state S3 with a supersede token minted on the EDT before dispatch.
- **Rule S-4 honoured:** `/tool` echoes the typed command into the transcript *and* into `session.messages` (FLAG-23-03) before going async. Until now that command survived on screen only because the EDT was frozen mid-call — asynchronously the transcript would have shown an answer with no visible question.
- **`slashCommandPathIsNotDoublePrompted` made worker-aware in the same commit as the move**, so the suite never spent a commit racy — the D-04 sequencing constraint, satisfied by construction.

## Task Commits

1. **Task 2 (+ Task 3 step 1): the guard, both call-site moves, and the slash-command test repair** — `b9e9748` (feat)
2. **Task 3: `McpToolExecutorEdtGuardTest`, the `-da` Gradle task, and S-03** — `7c0276c` (test)
3. **Task 4: wave gate** — verification only; no file needed changing, so no commit. Its findings are recorded under "Wave Gate" below.

**Plan metadata:** see the `docs(23-02)` commit that carries this file.

## The `-ea`-off demonstration — how it was arranged, and the output

**Arrangement: a dedicated Gradle `Test` task, not a scoped `-da` on `tasks.test`.**

```kotlin
tasks.register<Test>("edtGuardWithoutAssertionsTest") {
    // `-da`, and no `-ea` anywhere: this is the flag the whole task exists for.
    jvmArgs("-da", "-Djava.awt.headless=true")
    filter { includeTestsMatching("*McpToolExecutorEdtGuardTest") }
}
```

The plan offered either a scoped `jvmArgs("-da:com.six2dez.burp.aiagent.mcp.tools...")` on `tasks.test` or a dedicated task. The dedicated task was chosen because the scoped form buys one suite's evidence by permanently weakening a JVM flag that `tasks.test`'s own comment says exists to make `ChatPanel`'s EDT assertion fire in CI (REL-01 SC1). Disabling assertions across a whole package for every test, forever, to prove one property once is the wrong trade. The task stays out of `check`: the fast PR gate already runs this class with `-ea` and would catch a regression (the test asserts the exception **type**, and the JVM assertion facility throws `AssertionError`, not `IllegalStateException`), so the dedicated task's unique contribution is the flag, not the coverage.

**Result with assertions disabled — `./gradlew edtGuardWithoutAssertionsTest`:**

```
<testsuite name="com.six2dez.burp.aiagent.mcp.tools.McpToolExecutorEdtGuardTest"
           tests="3" skipped="0" failures="0" errors="0" time="1.436">
   executingABuiltInToolOnTheEdtIsRefused()
   theGuardPrecedesTheExternalToolEarlyReturn()
   theSameCallOffTheEdtReachesPastTheGuard()
```

**And the probe that makes that number mean something.** Green under `-da` proves the guard fires; it does not prove the task's `-da` is *effective* rather than silently inherited. So the guard was temporarily swapped to the JVM assertion facility (`assert(...)` in place of `check(...)`) and both tasks were re-run:

| Task | JVM flag | Result with an `assert`-based guard |
|---|---|---|
| `tasks.test` | `-ea` | `Thrown was: java.lang.AssertionError: REL-05: MCP tool execution must not be entered on the Swing EDT…` — the check fired |
| `edtGuardWithoutAssertionsTest` | `-da` | `Thrown was: null. ==> Unexpected null value, expected: <java.lang.IllegalStateException> but was: <null>` — **the check never ran at all** |

`Thrown was: null` is the whole of SC1's objection, measured: with assertions off, an assert-based guard lets the call proceed onto the EDT with no signal of any kind. The `check(...)` form was restored immediately afterwards and `git diff` confirms `McpToolExecutorImpl.kt` is byte-identical to its committed state.

**The F-4 placement probe, run the same way.** Moving the guard one line down — below the `ext:` early return — turned **exactly one** test red:

```
McpToolExecutorEdtGuardTest > theGuardPrecedesTheExternalToolEarlyReturn() FAILED
```

`executingABuiltInToolOnTheEdtIsRefused` stayed green. The placement test therefore distinguishes the two candidate placements rather than passing under either, which is what finding F-4 asked for.

## Wave Gate (Task 4)

| Gate | Result |
|---|---|
| `./gradlew ktlintCheck detekt test` | **exit 0** |
| `./gradlew test -PexcludeHeavyTests=true` | **exit 0** — 742 tests, 0 failures, 0 errors |
| `McpToolExecutorEdtGuardTest` under the PR-gate filter | **3 executed**, 0 skipped — the new suite really does run on the cross-platform matrix |
| `ChatPanelEdtConfinementTest` under the PR-gate filter | **6 executed**, 0 skipped |
| `ChatPanelToolGateTest` | **18 executed**, 0 failures |
| `./gradlew edtGuardWithoutAssertionsTest` | **exit 0**, 3 tests with `-da` |
| `grep -c '<ID>' detekt-baseline.xml` | **1096** — unmoved; `git diff --stat detekt-baseline.xml` empty |
| `grep -c 'assertEdt()' ChatPanel.kt` | **6** — and the body is byte-identical (`grep -c 'off-EDT access is a data race (REL-01)'` over its range returns 1) |
| `grep -c 'OffEdtDispatch.run' ChatPanel.kt` | **3** — one per D-01 call site |
| `grep -c 'SC5: user-originated' ChatPanel.kt` | **2** — both comment blocks survive verbatim |
| Guard line 159 < `ext:` early return line 170 | **yes** |
| `awk '…executeToolResult…' \| grep -v comments \| head -8 \| grep -c 'isEventDispatchThread'` | **1** |

### `SwingUtilities.invokeLater` count in `ChatPanel.kt`: **11**

**Unchanged from the measured HEAD baseline of 11, so there is no addition to justify.** Both new dispatches marshal back through `OffEdtDispatch`, which owns the single `invokeLater` for the whole phase; neither call site queues one of its own. `finishUserOriginatedToolCall` is invoked from that helper's existing tail, on the EDT, and adds no marshalling point.

`grep -c 'assertEdt()'` likewise returns **6**, not 5 and not 7: the new tail deliberately adds no EDT assertion of its own, for the reason `finishApprovedToolCall`'s KDoc already records — a seventh call site would move the number that IS SC5's evidence.

## Files Created/Modified

- `src/main/kotlin/com/six2dez/burp/aiagent/mcp/tools/McpToolExecutorImpl.kt` — the guard plus its justification comment (why the door and not the wrapper, why before the `ext:` return, why a Swing import under `mcp/`), and the `javax.swing.SwingUtilities` import.
- `src/main/kotlin/com/six2dez/burp/aiagent/ui/ChatPanel.kt` — `openToolDialog` and `handleToolCommand`'s `/tool` branch dispatched off-EDT; the Rule S-4 echo; the new `finishUserOriginatedToolCall` tail.
- `src/test/kotlin/com/six2dez/burp/aiagent/mcp/tools/McpToolExecutorEdtGuardTest.kt` (new, 157 lines) — S-10 and F-4.
- `src/test/kotlin/com/six2dez/burp/aiagent/ui/ChatPanelEdtConfinementTest.kt` — S-03, plus the `sentPrompts` captor and a `LONG_DRAIN` constant.
- `src/test/kotlin/com/six2dez/burp/aiagent/ui/ChatPanelToolGateTest.kt` — `slashCommandPathIsNotDoublePrompted` waits for the worker instead of draining the EDT.
- `build.gradle.kts` — the `edtGuardWithoutAssertionsTest` task.

## Decisions Made

- **The two user-originated tails emit no audit decision pair.** `finishApprovedToolCall` emits one because an approved call has a `ToolApprovalOutcome.Run` carrying a tier and a decision to report. These two paths are ungated by SC5 and carry no such record, and inventing one would put a fabricated decision in the SEC-06 log. This matches HEAD's behaviour, which also audited neither.
- **The supersede rule is shared, the audit rule is not.** A user-originated worker whose token no longer matches lands in UI-SPEC state S4 and renders nothing — the same discipline as `discardSupersededToolResult`, minus the audit pair it exists to preserve.
- **`openToolDialog`'s assistant `ChatMessage` is appended via `sessionsById[sessionId]` in the tail rather than through the captured `ChatSession`**, mirroring `finishApprovedToolCall`'s re-derivation. The tail runs on the EDT, where that read is legal, and a session deleted while the tool ran must not be silently resurrected by a stale reference.
- **`edtGuardWithoutAssertionsTest` is not wired into `check`.** Deliberate, and its cost is bounded: the PR gate already runs the same class under `-ea` and catches an assert-for-check regression by exception type. Wiring it in would double the class's runtime on every build to re-prove a property about a flag.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] The plan's `times(1)).history()` acceptance count of 1 is a mis-measurement; the real figure is 3, and it is unmoved**

- **Found during:** Task 3 (acceptance-criteria gate)
- **Issue:** the criterion reads *"`grep -c 'times(1)).history()' <same file>` still returns `1`"*. Measured against `ChatPanelToolGateTest.kt` at the 23-01 tip (`c09d528`), before any edit of mine, that grep returns **3** — the pattern occurs at `:138`, `:184` and `:410`. The stated 1 was never true, so the criterion as literally written could not have passed at any point in this plan and would have forced a fabricated fix.
- **Fix:** verified the invariant the criterion actually encodes, scoped to the test it names: `awk '/fun slashCommandPathIsNotDoublePrompted/,/^    }$/' … | grep -c 'times(1)).history()'` returns **1**, and the file-level count is unchanged at 3 across my edit. The test still makes its full claim — `times(1)` on `history()` and `findApprovalCard` returning null — and neither assertion was weakened.
- **Files modified:** none (measurement correction).
- **Verification:** `git show c09d528:…ChatPanelToolGateTest.kt | grep -c 'times(1)).history()'` → 3; current → 3.
- **Committed in:** n/a

**2. [Rule 3 - Blocking] `LONG_DRAIN` did not exist in `ChatPanelEdtConfinementTest`**

- **Found during:** Task 3 (S-03)
- **Issue:** S-03 needs a long drain after the Deny click so the denial followup turn is actually issued before `sentPrompts` reads it. `LONG_DRAIN` is defined file-privately in `ChatPanelToolGateTest.kt:745` and is not visible from this file.
- **Fix:** a matching file-private `private const val LONG_DRAIN = 24` with a KDoc naming what under-draining would cost (an unsent followup turn, i.e. a vacuous pass on the "conversation continues" clause). Duplicated rather than hoisted to a shared location, matching how `NO_CARD` is already duplicated across the two suites.
- **Files modified:** `ChatPanelEdtConfinementTest.kt`
- **Verification:** the suite compiles and all 6 tests pass.
- **Committed in:** `7c0276c`

**3. [Rule 3 - Blocking] No prompt captor existed in `ChatPanelEdtConfinementTest`**

- **Found during:** Task 3 (S-03)
- **Issue:** the file had `verifySendChatCount` (a count) but no way to read prompt TEXT, which S-03's "the denial result was returned to the model" clause requires — a turn count alone would pass on a followup that carried anything at all.
- **Fix:** ported `sentPrompts` from `ChatPanelToolGateTest`, positionally matched against `sendChat`'s 13 parameters, and asserted the followup contains `ToolApprovalGate.DENIAL_RESULT` verbatim.
- **Files modified:** `ChatPanelEdtConfinementTest.kt`
- **Verification:** S-03 passes; removing the `DENIAL_RESULT` clause is what would make it vacuous, so it is asserted explicitly.
- **Committed in:** `7c0276c`

**4. [Rule 3 - Blocking] The first placement of `sentPrompts` split `verifySendChatCount` from its own KDoc**

- **Found during:** Task 3 (ktlint gate)
- **Issue:** the insertion anchored on the `private fun verifySendChatCount(` signature, which sits below its KDoc — leaving an orphaned doc comment and tripping `standard:no-consecutive-comments` and `standard:blank-line-before-declaration`.
- **Fix:** moved the whole block below `verifySendChatCount`'s closing brace; ran `ktlintFormat` for the import ordering.
- **Files modified:** `ChatPanelEdtConfinementTest.kt`
- **Verification:** `./gradlew ktlintCheck detekt` exits 0.
- **Committed in:** `7c0276c`

**5. [Rule 2 - Missing Critical] The shared tail returns early on a superseded run rather than clearing the busy state**

- **Found during:** Task 2
- **Issue:** the plan's tail description covers success and failure but not the third exit these paths inherit from 23-01 — a Cancel taking the token while the worker runs. Clearing `setSendingState(false)` unconditionally would clobber a turn the user started *after* cancelling, which is the exact reason `discardSupersededToolResult` leaves the busy state untouched.
- **Fix:** `finishUserOriginatedToolCall` returns immediately when `runningTool.clearIfMatches(token)` is false, rendering nothing and touching no state (UI-SPEC S4).
- **Files modified:** `ChatPanel.kt`
- **Verification:** `ChatPanelEdtConfinementTest.cancellingARunningToolDiscardsItsResultAndStillAuditsTheCall` stays green, and the full suite passes.
- **Committed in:** `b9e9748`

---

**Total deviations:** 5 auto-fixed (1 bug, 1 missing critical, 3 blocking).
**Impact on plan:** No scope creep. Four are plan text colliding with a measured fact — a mis-measured grep count and three helpers the plan assumed were already visible in the target file. The fifth is a real gap: an exit path the plan's tail description did not enumerate, inherited from 23-01's supersede cell. The prohibition held: the guard was never softened to a log-and-proceed and never made debug-time-only.

## Prohibitions — Held

| Prohibition | Evidence |
|---|---|
| The guard must never be softened to log-and-proceed | The only statement is `check(...)`, which throws. No `logToError` appears anywhere in `executeToolResult`'s guard region, and no caller-side swallow was added. |
| The guard must never be debug-time-only | `check(...)` is a Kotlin stdlib call, not the JVM assertion facility, and the `-da` task above proves the distinction empirically rather than by appeal to the language spec. No tunable, no second behaviour. |
| Baselined signatures survive verbatim | `fun openToolDialog()` → 1; `private fun handleToolCommand(` → 1, with all five parameters asserted positively; `detekt-baseline.xml` unchanged at 1096 entries. |
| Both SC5 comment blocks survive | `grep -c 'SC5: user-originated'` → 2, and `userDialogPathIsNotDoublePrompted`'s source-text assertions still pass. |

## Issues Encountered

None beyond the deviations. `RedactionTest`'s known wall-clock flake did not surface in any full-suite run.

## User Setup Required

None — no external service configuration, and no package was added to `build.gradle.kts` or any lockfile. The one build change is a Gradle task registration.

## Next Phase Readiness

- **SC1 is closed.** All three chat call sites dispatch through `OffEdtDispatch`, and the check that enforces it fires in shipped Burp rather than only under `-ea`.
- **SC2's `runBlocking` clause is closed by placement, and the placement is defended by a test that goes red when the statement moves one line.**
- **Carried residual for 23-05's UAT (D7 above):** no committed test asserts the Rule S-4 `/tool` echo or the S3 entry on the two user-originated paths. Both were verified structurally at execution time, but neither is guarded on the PR gate. A cheap closure exists if 23-05 wants it — the harness already drives `/tool` end to end in `slashCommandPathIsNotDoublePrompted`, so a transcript assertion there would cost a few lines.
- **Note for 23-03 and 23-04:** the guard is now live for **every** caller of `executeToolResult`. Any new path that reaches the executor must dispatch off the EDT first, or it will fail loudly rather than freeze — which is the intended trade (T-23-06, disposition `accept`), but it is a behaviour change those plans should expect rather than discover.
- **Note for 23-04:** `finishUserOriginatedToolCall` participates in the same supersede cell as the chain tail, so the teardown supersedes 23-04 adds will cover these two paths for free. It writes no audit record, so 23-04's parameterised `supersedeReason` work does not touch it.

---
*Phase: 23-edt-confinement-ui-responsiveness*
*Completed: 2026-08-20*

## Self-Check: PASSED

- `src/test/kotlin/com/six2dez/burp/aiagent/mcp/tools/McpToolExecutorEdtGuardTest.kt` — FOUND (157 lines, ≥ 40 required; contains `class McpToolExecutorEdtGuardTest`)
- `23-02-SUMMARY.md` — FOUND
- Commits `b9e9748`, `7c0276c`, `27b5b0a` — all present in `git log`
- Key link `McpToolExecutorImpl.kt` → `javax.swing.SwingUtilities` via `isEventDispatchThread` — present (1 occurrence, inside `executeToolResult`)
- Key link `ChatPanel.kt` → `OffEdtDispatch.run` — present (3 occurrences, one per D-01 call site)
