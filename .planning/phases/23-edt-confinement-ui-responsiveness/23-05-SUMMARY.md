---
phase: 23-edt-confinement-ui-responsiveness
plan: 05
subsystem: testing
tags: [swing, edt, threading, rel-05, sc5, sc6, validation, uat]

requires:
  - phase: 23-edt-confinement-ui-responsiveness
    provides: "OffEdtDispatch, RunningToolTracker, ToolCallCapture + finishApprovedToolCall, ChatPanelTestHarness.awaitToolSettled, ChatPanelEdtConfinementTest and its functionBody helper (23-01)"
  - phase: 23-edt-confinement-ui-responsiveness
    provides: "the executeToolResult door guard, McpToolExecutorEdtGuardTest, the edtGuardWithoutAssertionsTest -da task (23-02)"
  - phase: 23-edt-confinement-ui-responsiveness
    provides: "applyAndSaveSettingsAsync, the busy seam, SettingsSaveAsyncTest, the A2 and E8 answers (23-03)"
  - phase: 23-edt-confinement-ui-responsiveness
    provides: "deleteConfirmedSession, the four teardown supersedes, EMPTY_HISTORY_ROW, deferred-items.md (23-04)"
provides:
  - "Four SC5 assertions in ChatPanelEdtConfinementTest, each demonstrated red against a one-statement reversal: assertEdt() byte-identity, the frozen mention count, the invokeLater ledger, and E5 in both its structural and behavioural halves"
  - "CHAT_PANEL_INVOKE_LATER_SITES — the marshalling-point constant whose KDoc IS the per-addition justification ledger"
  - "CHAT_PANEL_ASSERT_EDT_MENTIONS + REL_01_DATA_RACE_MESSAGE — SC5's frozen counter and the contract sentence it guards"
  - "dispatchedWorkLambdas() / matchingCloser() — extraction of each OffEdtDispatch work argument, scoped so shutdown()'s EDT-marshalled block is not a false positive"
  - "sessionCards() / transcriptTextOf() — per-session transcript reading, which is what makes 'WHICH transcript did the row land in?' an answerable question"
  - "23-HUMAN-UAT.md — the four residuals a headless test cannot reach, each with an instruction and a stated pass condition"
  - "23-VALIDATION.md completed — all 15 per-task rows mapped, wave_0_complete and nyquist_compliant set, sign-off ticked with its evidence"
affects: [26-quality, verify-work, ship, chatpanel]

actuals:
  tokens: 30854
  tasks: 2
  commits: 2

tech-stack:
  added: []
  patterns:
    - "An absence claim is asserted, never narrated: SC5 and SC6 are both 'this did not happen', and each is written as something that can go red"
    - "Scope a structural assertion to the syntactic region that carries the claim (the dispatch's own `work =` argument), not to the file — a file-wide scan of the same identifier reports the EDT-marshalled block as a violation and gets deleted as a false alarm"
    - "Assert the extraction's site count BEFORE asserting anything about what it extracted: a `none { … }` over an empty list is a passing test about nothing"
    - "A frozen counter's constant carries its justification ledger in KDoc, so the number fails loudly and tells the next person where to write their reason"

key-files:
  created:
    - .planning/phases/23-edt-confinement-ui-responsiveness/23-HUMAN-UAT.md
  modified:
    - src/test/kotlin/com/six2dez/burp/aiagent/ui/ChatPanelEdtConfinementTest.kt
    - .planning/phases/23-edt-confinement-ui-responsiveness/23-VALIDATION.md
    - .planning/WINDOWS.md

key-decisions:
  - "SC5's evidence is a POSITIVE body assertion plus two frozen counters, not a diff — the working branch is main, so `git merge-base HEAD main` equals HEAD and the diff-based criterion the plan replaced was empty by construction"
  - "The E5 source assertion is scoped to each OffEdtDispatch call's own `work =` argument, because shutdown() declares a local named `work` that legitimately reads sessionPanels — it is handed to invokeAndWait and runs ON the EDT, so a file-wide scan would report it and be removed as noise"
  - "The E5 behavioural half drives New Session rather than a teardown path: every teardown exit supersedes the worker, so its tail renders nothing and there would be no row to locate. createSession mutates all four session maps, moves activeSessionId, and supersedes nothing"
  - "detekt LargeClass on the grown test class is answered with an inline @Suppress carrying its reason, never with a regenerated baseline — the suite name is pinned by the PR-gate filter so splitting is not free, and detekt-baseline.xml is the v0.10.0 milestone metric"
  - "The `assertEdt()` count of 6 is stated precisely as a MENTION count — one declaration, one comment, four invocations — rather than repeated as 'six call sites', which is what four Phase 23 artifacts say and is not what the file contains"

patterns-established:
  - "Vacuity is established by probe, never by review — the fourth consecutive wave in which this was the only thing that would have caught the class of defect it catches"
  - "Correct a mis-measured counter in the artifact that states it, rather than restating it: 'six call sites' cost nothing to fix and would have cost the next reader a hunt for two invocations that do not exist"

requirements-completed: [REL-05]

coverage:
  - id: D1
    description: "assertEdt() is byte-identical to HEAD — its EDT test and its REL-01 message both intact — and its mention count in ChatPanel.kt is unmoved at 6"
    requirement: "REL-05"
    verification:
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/ui/ChatPanelEdtConfinementTest.kt#theEdtConfinementAssertionIsByteIdenticalAndStillHasSixMentions"
        status: pass
    human_judgment: false
  - id: D2
    description: "The SwingUtilities.invokeLater count in ChatPanel.kt equals a named constant whose KDoc is the per-addition justification ledger (HEAD baseline 11, zero additions)"
    requirement: "REL-05"
    verification:
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/ui/ChatPanelEdtConfinementTest.kt#everyMarshallingPointInChatPanelIsAccountedFor"
        status: pass
    human_judgment: false
  - id: D3
    description: "No off-EDT worker lambda in ChatPanel.kt names any of the five @GuardedBy(\"EDT\") session maps — asserted over each OffEdtDispatch call's own work argument, with the site count asserted first so the check cannot inspect nothing (E5, T-23-13)"
    requirement: "REL-05"
    verification:
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/ui/ChatPanelEdtConfinementTest.kt#noOffEdtWorkerLambdaTouchesAGuardedSessionMap"
        status: pass
    human_judgment: false
  - id: D4
    description: "The marshalled tail renders through the SessionPanel frozen into ToolCallCapture, not through a live map lookup — proven by moving every guarded map underneath a blocked worker and locating the result row afterwards (E5)"
    requirement: "REL-05"
    verification:
      - kind: integration
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/ui/ChatPanelEdtConfinementTest.kt#theToolTailWritesIntoItsCapturedPanelWhileTheGuardedMapsMoveUnderneathIt"
        status: pass
    human_judgment: false
  - id: D5
    description: "SC6 — MainTab's Thread + SwingUtilities.invokeLater idiom was reused and no new concurrency idiom was introduced anywhere: no SwingWorker in main, no coroutine outside mcp/, no ChatPanel-owned executor"
    requirement: "REL-05"
    verification:
      - kind: other
        ref: "grep -rl SwingWorker src/main/kotlin/ | wc -l -> 0; grep -rl kotlinx.coroutines src/main/kotlin/ | grep -v /mcp/ -> no output; grep -c 'ExecutorService|Executors\\.' ChatPanel.kt -> 0"
        status: pass
    human_judgment: false
  - id: D6
    description: "All three new suites execute under the PR-gate filter that .github/workflows/build.yml runs on the macOS/Linux/Windows matrix — 17 / 3 / 7, while ChatPanelConcurrencyTest is absent from the same run"
    requirement: "REL-05"
    verification:
      - kind: other
        ref: "JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test -PexcludeHeavyTests=true (762 tests, 106 suites, 0 failures)"
        status: pass
    human_judgment: false
  - id: D7
    description: "The v0.10.0 milestone metrics are unmoved — detekt-baseline.xml still 1096 entries and byte-identical, ChatPanel.kt byte-identical across this plan"
    requirement: "REL-05"
    verification:
      - kind: other
        ref: "grep -c '<ID>' detekt-baseline.xml -> 1096; git diff --stat detekt-baseline.xml -> empty; git diff HEAD~2 HEAD --stat -- src/main/kotlin/ -> empty"
        status: pass
    human_judgment: false
  - id: D8
    description: "The four residuals no headless test can reach are recorded and awaiting a human: the D-12 save-failure modal, FLAG-23-01's disabled-Save legibility, FLAG-23-04's sub-frame flicker, and research assumption A1"
    verification: []
    human_judgment: true
    rationale: "By construction. Two are HeadlessException-bounded (JOptionPane.getRootFrame), one is a rendering property of Burp's live Look-and-Feel that a headless JButton never paints, one is a repaint between two invokeLater blocks with no deterministic assertion, and A1 needs a live Burp because http1_request is not headlessly drivable at all. Each is a scenario in 23-HUMAN-UAT.md with an instruction and a pass condition; ledger entries WINDOWS.md #1, #3 and #4."

duration: 27 min
completed: 2026-08-20
status: complete
---

# Phase 23 Plan 05: SC5, SC6 and the Phase Gate Summary

**SC5 and SC6 are now asserted rather than claimed — `assertEdt()`'s body and its frozen mention count, the marshalling-point ledger, and E5 in both a structural and a behavioural half, each demonstrated red against a one-statement reversal — and the phase gate is green with the three new suites confirmed to run on the cross-platform matrix and the milestone metrics unmoved.**

## Performance

- **Duration:** 27 min
- **Started:** 2026-08-20T21:14:00Z
- **Completed:** 2026-08-20T21:41:00Z
- **Tasks:** 2
- **Files modified:** 4 (1 created, 3 modified)

## Accomplishments

- **SC5 is an assertion, not a sentence in a summary.** Four new tests in `ChatPanelEdtConfinementTest`, and each was shown to go red against a surgical mutation before being accepted. That mattered: this phase has a measured history of absence proofs passing vacuously — five caught across the four prior plans, every one by a probe and none by review — and SC5 is the phase's purest absence claim.
- **The `assertEdt()` count is stated correctly for the first time.** Four Phase 23 artifacts call the six grep hits "six call sites". They are not: one declaration, one comment that names the method, four invocations. The number is still 6 and still unmoved, which is all SC5 needs; saying so precisely costs nothing and stops the next reader hunting for two invocations that do not exist.
- **The E5 source assertion is scoped, not grepped.** A file-wide scan for `work = {` reports `shutdown()`'s block — which reads `sessionPanels` and is *supposed* to, because it is handed to `invokeAndWait`. That false positive is how a real guard gets deleted as noise. The assertion reads each `OffEdtDispatch.run(` call's own `work =` argument, and asserts the site count first so it can never be a `none { … }` over an empty list.
- **The E5 behavioural half distinguishes two worlds that are otherwise identical.** While the worker is blocked, the user clicks *New Session*: from that instant every live map lookup answers *session 2* while the capture still says *session 1*. Then the worker is released and the only question is which transcript the row landed in.
- **The phase gate is green on both halves the standard run does not cover** — the PR-gate filter really executes all three new suites, and `detekt-baseline.xml` is unmoved at 1096.
- **The four residuals are recorded rather than dropped**, and D-23-04-1 was surfaced from `deferred-items.md` into the ship-blocking ledger.

## Task Commits

1. **Task 1: SC5 — evidence that can actually go red** — `100a6e8` (test)
2. **Task 2: the phase gate, the UAT residuals and the validation map** — `373fab8` (docs)

**Plan metadata:** see the `docs(23-05)` commit carrying this file.

## Files Created/Modified

- `src/test/kotlin/com/six2dez/burp/aiagent/ui/ChatPanelEdtConfinementTest.kt` — four SC5 tests (13 → 17), the `CHAT_PANEL_ASSERT_EDT_MENTIONS` / `CHAT_PANEL_INVOKE_LATER_SITES` / `GUARDED_SESSION_MAPS` / `OFF_EDT_DISPATCH_SITES` constants, `chatPanelSource`, `occurrencesOf`, `dispatchedWorkLambdas`, `matchingCloser`, `newSessionButton`, `sessionCards`, `transcriptTextOf`, and the inline `@Suppress("LargeClass")` with its rationale.
- `.planning/phases/23-edt-confinement-ui-responsiveness/23-HUMAN-UAT.md` (new, 140 lines) — four scenarios.
- `.planning/phases/23-edt-confinement-ui-responsiveness/23-VALIDATION.md` — all 15 per-task rows mapped; `wave_0_complete` and `nyquist_compliant` set; sign-off ticked with its evidence.
- `.planning/WINDOWS.md` — one entry (#5) for D-23-04-1.

**`ChatPanel.kt` was not modified by this plan.** `git diff HEAD~2 HEAD --stat -- src/main/kotlin/` is empty. That is the point: SC5 asks for evidence a file did *not* change, and the plan that produces that evidence adding a production symbol would be self-defeating.

## SC5 — the evidence, and how each half was proven able to fail

| Claim | Assertion | Probe that reddened it |
|---|---|---|
| `assertEdt()`'s message is intact | `functionBody("private fun assertEdt()")` contains `off-EDT access is a data race (REL-01)` | Message shortened → `expected: <true> but was: <false>`, with the mutated body printed in the failure |
| the mention count is unmoved | `occurrencesOf("assertEdt()", …) == 6` | One invocation deleted → `expected: <6> but was: <5>` |
| every marshalling point is accounted for | count `== CHAT_PANEL_INVOKE_LATER_SITES` | One comment naming the symbol added → `expected: <11> but was: <12>` — the *prose-drift* shape 23-01 hit, caught by the counter it moves |
| no worker reads a guarded map (structural) | each `work =` argument contains none of the five names | `sessionsById[sessionId]` threaded into the chain worker → red, naming `sessionsById` and printing the offending lambda |
| the tail uses the capture (behavioural) | the row lands in the transcript that asked the question | `captured.panel` → `activeSessionId?.let { sessionPanels[it] }` → red |

After every probe, `git diff` confirmed `ChatPanel.kt` byte-identical to its committed state before the next step. `git stash` was not used — it is banned in this workspace.

### The counters, re-measured independently at the gate

| Counter | Value | Meaning |
|---|---|---|
| `grep -c 'assertEdt()' ChatPanel.kt` | **6** | unmoved across all five plans |
| `awk` over `assertEdt()`'s range → `grep -c 'off-EDT access is a data race (REL-01)'` | **1** | body byte-identical |
| `grep -c 'SwingUtilities.invokeLater' ChatPanel.kt` | **11** | identical to the HEAD baseline; zero additions to justify |
| `grep -cE 'ConcurrentHashMap|Collections.synchronizedMap' ChatPanel.kt` | **0** | the guarded maps were not converted — Phase 17's REL-01 declined that explicitly |
| `grep -c '<ID>' detekt-baseline.xml` | **1096** | `git diff --stat` empty |

## SC6 — reuse of `MainTab`'s idiom, stated with its evidence

The claim is that no new concurrency idiom was introduced. Four independent measurements, all taken at the gate:

| Evidence | Measurement |
|---|---|
| No `SwingWorker` anywhere in main | `grep -rl 'SwingWorker' src/main/kotlin/ \| wc -l` → **0** |
| No coroutine outside the `mcp` package | `grep -rl 'kotlinx.coroutines' src/main/kotlin/ \| grep -v '/mcp/'` → **no output** |
| `ChatPanel` owns no `ExecutorService` | `grep -c 'ExecutorService\|Executors\.' ChatPanel.kt` → **0** |
| The shape is `MainTab`'s | `MainTab.kt:193` is `Thread { … SwingUtilities.invokeLater { … } }`; `OffEdtDispatch.kt:111` is `Thread(body, threadName).apply { isDaemon = true }.start()` with one `invokeLater` tail |

**The one correction on top of `MainTab`'s idiom is that `isDaemon` is set explicitly**, following `MontoyaHttpTransport.kt:85` rather than `MainTab.kt:193`, which does not set it. That is not tidiness: D-08's unload guarantee depends on it. `shutdown()` deliberately does not wait for a running worker — a bounded join there would put back on the EDT exactly the ten-second blocking wait plan 23-03 removed from the Settings save — and what makes *not* waiting safe is that a daemon thread never holds JVM exit open. The supersede is the other half: it is what stops the worker's tail writing into a panel whose classloader is being torn down.

**The structural evidence for "reuse rather than proliferation" is that the phase has ONE dispatch seam.** Plan 23-01 built `OffEdtDispatch`; 23-02's two user-originated call sites, 23-03's Settings save and 23-04's teardown supersedes all marshalled back through it rather than queueing their own `invokeLater`. That is why the `ChatPanel.kt` marshalling-point count is still 11 after four plans of new asynchronous work — the number is not a coincidence to be explained away, it is the shape of the design.

## Phase gate results

| Gate | Result |
|---|---|
| `./gradlew ktlintCheck detekt test` | **exit 0** — 774 tests, 1 skipped, 0 failures, 0 errors |
| `./gradlew test -PexcludeHeavyTests=true` | **exit 0** — 762 tests across 106 suites, 0 failures |
| `ChatPanelEdtConfinementTest` under the PR-gate filter | **17 executed** (13 at wave 3 + 4 new) |
| `McpToolExecutorEdtGuardTest` under the PR-gate filter | **3 executed** |
| `SettingsSaveAsyncTest` under the PR-gate filter | **7 executed** |
| `ChatPanelToolGateTest` under the PR-gate filter | **20 executed** |
| `ChatPanelConcurrencyTest` under the PR-gate filter | **absent** — the control that makes the four counts above mean something |
| `./gradlew edtGuardWithoutAssertionsTest` | **exit 0** — still green from wave 2 |
| `grep -c '<ID>' detekt-baseline.xml` | **1096**, `git diff --stat` empty |

**The `ChatPanelConcurrencyTest` absence is deliberate evidence, not trivia.** Three suites reporting non-zero counts is equally consistent with a filter that was never applied. An excluded suite that is *missing* from the same run is what proves the filter was active — and `ChatPanelConcurrencyTest` is precisely the name the validation contract warns is "the natural — and silently fatal — one to reach for here".

`RedactionTest`'s known wall-clock flake did not surface in either full-suite run.

## The four decisions, consolidated

Recorded here so a reader does not have to open four SUMMARYs.

**1. A2 — is `SettingsPanel` headlessly constructible? Resolved by execution: YES.** Plan 23-03's Task 1 spike constructed a real `SettingsPanel` in roughly ten minutes, well inside its thirty-minute timebox, so Tasks 3 and 4 built on the real panel and the documented structural fallback was not taken for the behavioural assertions. Two collaborator-stub gaps surfaced on the way and **neither was headless-related** — a nested Mockito stubbing and a deep-stubbed `List` returning `null` from `toTypedArray()`. One of them looked exactly like an A2 failure for a run. A research risk resolved by running the thing rather than by inferring from the absence of a `Toolkit` call.

**2. E8 — settings atomicity: `documented-residual`.** Auto-selected under the project's `mode: yolo` against a `gate="blocking"` checkpoint, and the reasoning stands independently: option B (a lock across `applyAndSaveSettings`) would hold it across `KtorMcpServerManager.stop()`'s bounded ten-second wait, so a tool dispatch either waits on it — on the EDT, which is the shape this phase exists to delete — or fails closed with a message the user cannot act on. Option C has nowhere to go; the `McpToolContext` snapshot is already taken at the last EDT statement before dispatch.

The residual, in fail-closed language: **exactly two values can come from different sides of a save** — the `privacyMode` in a tool worker's immutable `McpToolContext` snapshot, and the custom-pattern list current in `Redaction.compiledCustomPatterns` when that worker reads it. **Both halves are always fully published**: `setCustomPatterns` assigns a whole new `List<Pattern>` to a `@Volatile` field, so the list is never partially compiled, and every `PrivacyMode` value is a valid mode. **There is no state in which a call is redacted under no rules.** The pairing is not transactional; each half is. It is pinned by a test that goes red if the tool path ever reads live settings instead of its snapshot.

**3. FLAG-23-01 — the disabled-Save recolor: IMPLEMENTED, deliberately.** While busy, Save goes `outlineVariant` / `onSurfaceVariant`; `primary` / `onPrimary` return when it is not. The escape hatch is left open and routed to UAT rather than taken silently: if live testing shows plain `isEnabled = false` already reads correctly on Burp's Look-and-Feel, the recolor may be dropped — but that has to be a recorded choice. `isEnabled = false` itself stays mandatory either way; it is what closes the double-save race, and the recolor is only about legibility.

**4. FLAG-23-03 — does the `/tool` command reach the model? YES.** The typed command is appended to `session.messages` as a user `ChatMessage`, removing the asymmetry with `openToolDialog` rather than preserving it. Until this phase that command survived on screen only because the EDT was frozen mid-call; asynchronously the transcript would have shown an answer with no visible question.

## The four residuals, recorded

| # | Residual | Where it lives | Why it is not closed here |
|---|---|---|---|
| 1 | No committed test asserts the Rule S-4 `/tool` transcript echo or the S3 busy-state entry on either user-originated path (23-02 D7) | `WINDOWS.md` #2 | Both were verified at execution time by source-order greps — a check the executor made, not a guard that re-runs on the PR gate. 23-02 notes a cheap closure exists: the harness already drives `/tool` end to end in `slashCommandPathIsNotDoublePrompted`. Left as scope for a later plan rather than taken here, where the charter is SC5/SC6 and the gate. |
| 2 | The E8 pairing window — a worker's snapshot `privacyMode` from before a save with the custom-pattern list from after it | 23-03 §Task 2, and the E8 block above | An owner decision, auto-selected as `documented-residual`. Both halves are always fully published; the residual is the pairing, and the alternatives put a lock or a wait back on the EDT. |
| 3 | **D-23-04-1** — `clearChatState()` (teardown path 3 of 5, the one D-08 never listed) does not supersede a running tool worker, so a Clear Chat can be followed by a result row and a followup turn for the conversation just cleared | `deferred-items.md`, and now `WINDOWS.md` #5 | New scope. The fix is one line of the same shape as the session-delete supersede, but it raises a UI question 23-04 was not chartered to answer: should Clear Chat return the panel to S0 while a worker still runs, or leave the busy state alone as `discardSupersededToolResult` does? Surfaced into the ledger by this gate so it is visible at ship time rather than only in a phase-local file. |
| 4 | 23-01's suggested per-cause `supersedeReason` parameter was deliberately NOT implemented | 23-04 §Decisions Made | Three of the four exits reach the supersede through `cancelInFlightRequest`, whose signature is a `detekt-baseline.xml` `ReturnCount` key and cannot gain a parameter without moving the pinned 1096. Per-cause reasons were therefore reachable for the session-delete exit alone, and a log reading `"cancelled"` for an unload is worse than one honest generic reason. Recorded so the omission does not read as an oversight. |

Residuals 1, and the two UAT-bounded ones from 23-01 and 23-03, are the four scenarios in `23-HUMAN-UAT.md`: D-12, FLAG-23-01, FLAG-23-04 and A1.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] `detekt` `LargeClass` fired on the grown test class**

- **Found during:** Task 1 (verification gate)
- **Issue:** four new scenarios pushed `ChatPanelEdtConfinementTest` past detekt's `LargeClass` threshold. `detekt.yml` excludes `**/test/**` from `FunctionNaming` only, not from complexity rules, and `detekt-baseline.xml` is pinned at 1096 as the v0.10.0 milestone metric — so a baseline entry was not available, and the plan's own gate is the 1096 count.
- **Fix:** an inline `@Suppress("LargeClass")` on the class, with the reasoning in the class KDoc: splitting is not a free move because a second suite name can land on one of the five PR-gate-excluded suffixes and never run on the cross-platform matrix, and a baseline entry would move a milestone metric the wrong way to silence a finding about a test file. Same convention as `ExternalMcpClientManager` and plan 23-03's two-layer `finally`.
- **Files modified:** `ChatPanelEdtConfinementTest.kt`
- **Verification:** `./gradlew ktlintCheck detekt test` exits 0; `grep -c '<ID>' detekt-baseline.xml` returns 1096 and `git diff --stat` is empty.
- **Committed in:** `100a6e8`

**2. [Rule 1 - Bug] "`assertEdt()` and its six call sites" is a mis-measurement — there are four invocations**

- **Found during:** Task 1
- **Issue:** the plan, `23-VALIDATION.md`, `23-UI-SPEC.md` and three prior SUMMARYs all describe the six `grep -c 'assertEdt()'` hits as six call sites. Measured: `ChatPanel.kt:810` is the declaration, `:2544` is a comment that names the method, and `:1027`, `:1455`, `:2545`, `:2755` are the four invocations. A future reader taking the phrase literally would go looking for two invocations that do not exist, and would find the comment and reasonably conclude the counter had been gamed.
- **Fix:** the count of 6 is asserted unchanged — that is what SC5 needs and it is unmoved — but it is named a *mention* count wherever this plan states it, with the breakdown, in the test's KDoc, in `CHAT_PANEL_ASSERT_EDT_MENTIONS`'s KDoc and in the corrected `23-VALIDATION.md` row.
- **Files modified:** `ChatPanelEdtConfinementTest.kt`, `23-VALIDATION.md`
- **Verification:** `grep -n 'assertEdt()' ChatPanel.kt` — six lines, one of them a comment.
- **Committed in:** `100a6e8`, `373fab8`

**3. [Rule 3 - Blocking] The plan's E5 source assertion names the wrong function, and the obvious form has a false positive**

- **Found during:** Task 1
- **Issue:** the plan asks for a source assertion that *"`finishApprovedToolCall`'s worker-side lambda body references none of the five map names"*. `finishApprovedToolCall` **is** the EDT tail — it has no worker-side lambda, and it legitimately reads `sessionsById` through `backendIdFor`, on the EDT, where that is legal. The worker-side lambda is the `work =` argument passed to `OffEdtDispatch.run` in `executeApprovedToolCall`. And the obvious wide form is worse than useless: `shutdown()` also declares a local named `work` which reads `sessionPanels` and is *supposed* to, because it is handed to `SwingUtilities.invokeAndWait`.
- **Fix:** `dispatchedWorkLambdas()` extracts the `work =` argument of each `OffEdtDispatch.run(` call by paren- and brace-matching, covering all three dispatch sites rather than the one the plan names, and asserts the site count (3) **before** asserting anything about the contents — otherwise a stale extraction makes the test a passing assertion about nothing.
- **Files modified:** `ChatPanelEdtConfinementTest.kt`
- **Verification:** threading `sessionsById[sessionId]` into the chain worker reddens the test, naming the map and printing the offending lambda; `shutdown()`'s block is untouched and produces no false positive.
- **Committed in:** `100a6e8`

**4. [Rule 3 - Blocking] The E5 behavioural half cannot "assert from the worker thread that no read occurred"**

- **Found during:** Task 1
- **Issue:** the plan's first phrasing asks the tool double to assert, from the worker, that no guarded-map read happened. Nothing observable from that thread can establish it: the maps are private fields of the panel and a worker looking at them proves nothing about what the production code did.
- **Fix:** implemented the plan's own stated practical form — *"the values the tail uses came from the `ToolCallCapture` record rather than from a map lookup"* — as a scenario in which the two worlds are made distinguishable: while the worker is blocked, a New Session click moves every guarded map and `activeSessionId`, and the assertion is which transcript the row landed in. `sessionCards()` / `transcriptTextOf()` were needed because the existing `transcriptText` joins every `JEditorPane` under `ChatPanel.root` into one string and so cannot answer "which transcript".
- **Files modified:** `ChatPanelEdtConfinementTest.kt`
- **Verification:** `captured.panel` → a live `sessionPanels` lookup reddens the test; the two-card clause is asserted first so a click that silently did nothing fails loudly instead of passing.
- **Committed in:** `100a6e8`

**5. [Rule 1 - Bug] The acceptance criterion `grep -c 'nyquist_compliant: true' <file>` returns 1 cannot hold**

- **Found during:** Task 2 (acceptance-criteria gate)
- **Issue:** `23-VALIDATION.md`'s seeded Validation Sign-Off contains the checklist line ``- [ ] `nyquist_compliant: true` set in frontmatter``, which the plan itself instructs be ticked. So the file necessarily contains the string twice once the criterion is satisfied, and the count is 2 the moment the work is done correctly. Same shape as 23-02's deviation 1 and 23-03's deviation 5 — a grep count that a sibling artifact's own text invalidates.
- **Fix:** asserted the anchored form the criterion actually encodes: `grep -c '^nyquist_compliant: true$'` returns **1**, and `grep -c '^wave_0_complete: true$'` returns **1**. Both hold.
- **Files modified:** none (measurement correction).
- **Verification:** anchored greps above; the frontmatter field reads `true` and the `# audit-milestone §5.5` comment two lines above it, which also contains the field name, was left untouched.
- **Committed in:** n/a

**6. [Rule 1 - Bug] `23-VALIDATION.md`'s SC5 row records wave 5; the phase has four waves**

- **Found during:** Task 2
- **Issue:** the seeded row reads `Wave 5`. `23-05-PLAN.md`'s frontmatter is `wave: 4`, and the phase ran in four waves.
- **Fix:** corrected to 4 in the same edit that filled the row's `Task ID` and `Plan` columns.
- **Files modified:** `23-VALIDATION.md`
- **Verification:** the row now reads `| 23-05 T1 | 23-05 | 4 | SC5 · E5 |`.
- **Committed in:** `373fab8`

---

**Total deviations:** 6 auto-fixed (3 bugs, 3 blocking).
**Impact on plan:** No scope creep and no production code touched. Three are plan or seeded-artifact text colliding with a measured fact — a mis-described call-site count, an unsatisfiable grep, a stale wave number. Two are the plan's E5 instructions being unimplementable as literally written, both fixed toward the stronger form rather than the convenient one: the source assertion covers all three dispatch sites instead of the one named, and asserts its own extraction is non-empty. One is a detekt finding answered without moving the pinned baseline.

## Prohibitions — Held

| Prohibition | Evidence |
|---|---|
| `assertEdt()` must not be touched | `git diff HEAD~2 HEAD --stat -- src/main/kotlin/` is empty — this plan modified no production file at all. Phase 26 / QUAL-07 still owns the upgrade. |
| The guarded maps must not be converted to concurrent collections | `grep -cE 'ConcurrentHashMap\|Collections.synchronizedMap' ChatPanel.kt` → **0**. Phase 17's REL-01 declined this explicitly; doing it here would replace the contract rather than preserve it. |
| `detekt-baseline.xml` must not be regenerated | 1096 entries, `git diff --stat` empty. The one new finding was answered with an inline `@Suppress` carrying its reason. |
| A new suite must never be exempted from the PR-gate filter to make it pass | No exemption was added. All three suites report non-zero executed counts under `-PexcludeHeavyTests=true`, and the excluded `ChatPanelConcurrencyTest` is absent from the same run. |
| No `git stash`, no destructive git | Every probe was a forward-and-back edit through a scratchpad copy, with `git diff` verifying byte-identical restoration before the next step. |
| `README.md`'s pre-existing modification untouched | It appears in `git status` and in no commit of this plan. |

## Issues Encountered

None beyond the deviations. `RedactionTest`'s known wall-clock flake did not surface in any run.

## Threat Flags

None. This plan added no network endpoint, auth path, file-access pattern or trust-boundary schema change. T-23-13 (a later edit reopening the `@GuardedBy("EDT")` maps to worker access, severity `high`, disposition `mitigate`) is mitigated as planned and in both halves the register asks for: the structural assertion over every dispatch's worker lambda, and the behavioural assertion that the tail uses its capture — plus `assertEdt()` and its mention count byte-identical, and no conversion of the maps to concurrent collections.

## User Setup Required

None — no external service configuration, and no package was added to `build.gradle.kts` or any lockfile. `build.gradle.kts` was not touched: `ChatPanel.kt` is already a declared `tasks.test` input, which is what makes the new source-text assertions re-run when their target changes.

## Next Phase Readiness

- **Phase 23 is code-complete.** All six success criteria are closed: SC1 and SC2 by the door guard and the three call-site moves, SC3 by the liveness handshake and the trace-id ordering chain, SC4 by the Settings save, SC5 and SC6 here.
- **Four human-UAT items are open** in `23-HUMAN-UAT.md` and need a live Burp. Two of them (`FLAG-23-01`'s escape hatch, `A1`'s framing of SC2) can change a recorded decision or an ADR sentence; none can change a classification or a control.
- **`WINDOWS.md` carries five open entries for this phase**, so `/gsd-ship` will block until they are resolved or waived with a reason. Four are UAT-bounded and close when `23-HUMAN-UAT.md` is answered; #5 (D-23-04-1) is a real one-line defect awaiting a plan that can also answer the UI question it raises.
- **For Phase 26 / QUAL-07:** `assertEdt()` is untouched and still relies on the JVM assertion facility, so it does nothing in shipped Burp. This phase deliberately left it and built the enforcing check at a different seam — the throwing `check(...)` on `executeToolResult`, demonstrated under `-da`. When QUAL-07 upgrades `assertEdt()`, the frozen counter in `CHAT_PANEL_ASSERT_EDT_MENTIONS` will go red by design; move the constant and record the reason in its KDoc.
- **A note worth carrying, now at four consecutive waves:** every wave of this phase shipped or nearly shipped an assertion that passed with its defect fully present, and every one was caught by a red probe rather than by review. The probe is not a formality here.

---
*Phase: 23-edt-confinement-ui-responsiveness*
*Completed: 2026-08-20*

## Self-Check: PASSED

- `.planning/phases/23-edt-confinement-ui-responsiveness/23-HUMAN-UAT.md` — FOUND (140 lines, ≥ 30 required; contains `D-12`, `FLAG-23-01`, `FLAG-23-04`, `A1`, each with an instruction and a pass condition)
- `src/test/kotlin/com/six2dez/burp/aiagent/ui/ChatPanelEdtConfinementTest.kt` — FOUND (17 executed tests, contains `assertEdt`)
- `.planning/phases/23-edt-confinement-ui-responsiveness/23-VALIDATION.md` — FOUND (`grep -c 'TBD'` → 0; no unticked checkbox remains)
- Commits `100a6e8`, `373fab8` — both present in `git log`
- Key link `ChatPanelEdtConfinementTest.kt` → `ChatPanel.kt` via `assertEdt` — present, reading the source through the already-declared `tasks.test` input
- All Task 1 and Task 2 `<acceptance_criteria>` re-run and passing, with two restated per Deviations 2 and 5
- Plan-level `<verification>` block re-run: full gate exit 0, PR-gate filter exit 0 with all three suites executing, baseline 1096, `assertEdt()` 6 with its body intact
