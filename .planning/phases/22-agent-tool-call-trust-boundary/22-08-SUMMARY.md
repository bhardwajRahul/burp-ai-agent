---
phase: 22-agent-tool-call-trust-boundary
plan: 08
subsystem: ui
tags: [sec-06, sc3, lifecycle, implicit-deny, audit, teardown, swing, edt, red-when-broken, junit5, mockito]

# Dependency graph
requires:
  - plan: "22-03"
    provides: "ToolDecision, ImplicitDenyReason and its five wire values, canonicalToolId"
  - plan: "22-04"
    provides: "ToolApprovalGate.evaluate/resolve/tierFor, ToolApprovalMemory, ToolApprovalOutcome, DENIAL_RESULT"
  - plan: "22-05"
    provides: "ToolDecisionReporter.report — the SC3 payload, the Output line and the returned metadata map"
  - plan: "22-06"
    provides: "ToolApprovalCard.resolve(decision, implicitReason), idempotent"
  - plan: "22-07"
    provides: "The gate in the path, pendingDecisions, PendingToolDecision, the four resolution callbacks, ChatPanelToolGateTest and ChatPanelTestHarness"
provides:
  - "ChatPanel.resolvePending / resolveAllPending — the single entry point every teardown path routes through"
  - "The SC3 record wired at five reporting branches, merged into the existing MCP_TOOL_CALL emission"
  - "ChatPanel.clearChatState() — Clear Chat's teardown as a modal-free, testable seam that also clears the D-10 approval memory"
  - "SessionPanel.scrollToComponent and the scroll-to-pending on session switch"
  - "The `Awaiting approval` session-row marker, asserted through the production renderer"
  - "Five new integration tests (15 total in ChatPanelToolGateTest), three of them proven red when their rule is broken"
affects: ["22-09", "phase-23"]

tech-stack:
  added: []
  patterns:
    - "One entry point per lifecycle event, not one hook per call site: the next field added to a resolution lands in one place instead of being forgotten in the copy"
    - "Report BEFORE the nullable sink, never inside its argument list — `logger?.log(report(...))` silently skips the audit event whenever the logger is absent"
    - "Pass the whole gate outcome instead of one field of it, so tier and decision reach the audit record without crossing detekt's parameter ceiling"
    - "A modal-free seam under the confirmation dialog, so the teardown a security control depends on is reachable by a headless test"
    - "Invoke a Swing cell renderer directly to assert what it renders: a headless JList never paints, and an unasserted UI affordance is an unproven claim"

key-files:
  created: []
  modified:
    - src/main/kotlin/com/six2dez/burp/aiagent/ui/ChatPanel.kt
    - src/test/kotlin/com/six2dez/burp/aiagent/ui/ChatPanelToolGateTest.kt
    - src/test/kotlin/com/six2dez/burp/aiagent/ui/ChatPanelTestHarness.kt

key-decisions:
  - "sendFollowup ships INERT with @Suppress(\"UnusedParameter\") and a KDoc that says so, rather than a live branch — a teardown path that CAN dispatch a backend turn is something a future edit can flip on, and 'no implicit denial starts a turn' is stronger as a structural property than as a call-site convention"
  - "askForToolApproval's inline stale-record branch was refactored to call resolvePending, making six call sites rather than the plan's five — a second removal site that merely happens to agree today is the ad-hoc-hook shape this plan exists to remove"
  - "Clear Chat replaces the whole ToolApprovalMemory holder instead of gaining a clear() method on it: one atomic assignment drops both sets and the repeat counter, and the memory keeps its narrow accessors"
  - "A fifth reporting branch the plan did not list: resolveToolDecision's vanished-transcript path, where a HUMAN clicked and the record would otherwise not exist at all"
  - "knownTool includes the `ext:` clause, per 22-05's call contract, not the plan's catalog-only expression — it must be the same test tierFor performs"

requirements-completed: [SEC-06]

# Metrics
duration: ~60 min
completed: 2026-08-14
---

# Phase 22 Plan 08: Pending-Decision Lifecycle and the SC3 Record Summary

**A pending authorisation can no longer outlive the thing that could answer it: all six paths that destroy a card or its session state now route through one `resolvePending`, none of them starts a backend turn, and every decision — including the two branches that previously vanished silently — writes one `mcp_tool_decision` event from the same construction that feeds the AI Activity record.**

## Performance

- **Duration:** ~60 min
- **Tasks:** 3 of 3
- **Files modified:** 3 (0 created)
- **Commits:** 4

## Task Commits

| # | Task | Commit | Type |
|---|------|--------|------|
| 1 | One `resolvePending` entry point across all teardown paths, plus the discarded-fallback fix | `33e920d` | feat |
| 2 | Wire the SC3 dual-destination decision record | `ae4d6ab` | feat |
| 3 | Session-list marker, `scrollToComponent`, four lifecycle/audit tests | `18993fd` | test |
| 3b | Assert the pending marker actually renders (beyond the plan's list) | `a02d49b` | test |

## Accomplishments

- **The permanently-stuck decision is gone, and it was real.** `clearCurrentChat()` removed the card from the transcript while the pending record survived — an authorisation nobody could grant and a continuation that never fired. Turning the `resolvePending` call off makes `clearChatResolvesThePendingCardAndClearsApprovalMemory` fail with `expected: <0> but was: <4>`: four live decision buttons on a card that has been ripped out of the UI. That is T-22-10 stated as an assertion.
- **No implicit denial starts a backend turn, proven by mutation.** Making `resolvePending` send a followup turns **two** tests red — `shutdownResolvesAllPendingDecisionsWithoutSendingATurn` (4 turns wanted 2) and `newMessageInTheSessionImplicitlyDeniesThePendingCard` (3 wanted 2). The two independent hazards T-22-33 names — dispatching during classloader teardown, and racing the user's own turn — are each guarded by their own test.
- **Two decisions that previously left no record now leave one.** The `runCatching` failure branch (an approved call whose tool threw) and the vanished-transcript branch (a human clicked, the panel was gone) both emitted nothing to the SC3 sink. The second is not in the plan; it was found by enumerating which `ToolDecision` values reach a reporting site.
- **`report` is called before the nullable sink, not inside it.** `supervisor.aiRequestLogger?.log(metadata = report(...))` would have skipped the audit event and the Output line entirely whenever the AI Activity logger is absent — Kotlin does not evaluate arguments of a short-circuited safe call. SC3 must not depend on another sink being wired, so every branch assigns the report to a local first. Recorded in-file at both sites.
- **Clear Chat also drops the D-10 grant.** An approval given while reviewing target A can no longer run silently against target B's task in the same chat window (T-22-34). Asserted by re-asking: after the clear, the same tool raises a fresh four-action card.
- **The `Awaiting approval` marker is asserted, not asserted-about.** The production cell renderer is invoked directly, because a headless `JList` never paints. Neutering the renderer's predicate turns the test red.

## The red-when-broken proofs (all four reverted, tree verified clean)

| Mutation | Expected red | Observed |
|----------|--------------|----------|
| Remove `resolvePending` from `clearChatState()` | `clearChatResolvesThePendingCardAndClearsApprovalMemory` | **1 failed** — `Clear Chat must resolve the pending decision, not orphan it (T-22-10). expected: <0> but was: <4>` |
| Make `resolvePending` send a followup turn | `shutdownResolvesAllPendingDecisionsWithoutSendingATurn` | **2 failed** — `TooManyActualInvocations` on `sendChat`: 4 wanted 2, and 3 wanted 2 in the new-message test. Stronger than the plan required. |
| Neuter the renderer's pending predicate | `sessionRowMarksAPendingDecisionAndClearsItOnResolution` | **1 failed** — `A session holding a pending decision must say so on its row. expected: <true> but was: <false>` |

After each, `git checkout -- <file>` restored the committed state and `git status --short` was empty before the next step.

## Verification Results

| Check | Expected | Actual |
|-------|----------|--------|
| `./gradlew test ktlintCheck detekt -q` | exit 0 | **0** |
| `git diff --stat -- detekt-baseline.xml` | empty | **empty** (QUAL-07 held) |
| `ChatPanelToolGateTest` | 0 failures | `tests="15" skipped="0" failures="0" errors="0"` |
| `test -PexcludeHeavyTests=true --tests "*ChatPanelToolGateTest*"` → `<testcase` count | all selected | **15** — the PR gate runs every one |
| `git diff --diff-filter=D` since base | empty | **empty** — no deletions in any commit |
| Files touched | the 3 declared | `ChatPanel.kt`, `ChatPanelToolGateTest.kt`, `ChatPanelTestHarness.kt` |

### Acceptance greps

| Check | Expected | Actual | Note |
|-------|----------|--------|------|
| `private fun resolvePending` / `private fun resolveAllPending` | 1 / 1 | 1 / 1 | |
| `resolvePending(\|resolveAllPending(` | 7 | **9** | see deviation 1 |
| `ImplicitDenyReason.` at call sites | 5 | **6** | `NEW_MESSAGE` appears twice; see deviation 1 |
| `sessionStates[…] ?: ToolSessionState()` | 0 | 0 | the latent defect is gone |
| `getOrPut` | ≥3 | 11 | |
| `InterruptedException` vs base | not increased | 2 vs 2 | |
| `ToolDecisionReporter(` | 1 | 1 | one instance, not one per call |
| `logToOutput = ` / `verboseAudit` | 1 / 0 | 1 / 0 | hash seam left at its default |
| `.report(` | ≥6 | **5** | see deviation 2 |
| `runStatus = "error"\|runStatus = status` | ≥2 | 3 | |
| `"toolName" to call.tool` / `"denied"` | 0 / 0 | 0 / 0 | hand-built map gone; D-12's status rule lives only in the reporter |
| `ActivityType.MCP_TOOL_CALL` vs base | unchanged | 3 vs 3 | no new emission sites, only enriched ones |
| `fun scrollToComponent` / `scrollRectToVisible` | 1 / 1 | 1 / 1 | |
| `Awaiting approval` | 1 | 1 | |
| `statusWarning` | ≥1 | 1 | |
| `Typography.caption` within 3 lines of the marker | 0 | 0 | the marker matches its sibling's font |
| `refreshSessionList()` vs base | increased | 6 vs 4 | on create and on both resolution paths |
| `@Test` | 14 | **15** | see deviation 3 |
| `registerGlobalEmitter(null)` | 1 | 1 | the emitter does not leak into later test classes |
| `shutdown()` contains a send call | no | no | read directly; the comment was reworded so a mechanical grep agrees |
| `switchToSession` resolves anything | no | no | it only scrolls |

**`SwingUtilities.invokeLater` moved 10 → 11.** The one addition is inside `SessionPanel.scrollToComponent`, which the UI-SPEC prescribes: scrolling must run after layout or it reveals a zero-height rectangle. No marshalling was added to the tool-call path, and `assertEdt()` and its KDoc are untouched.

## Decisions Made

- **`sendFollowup` ships inert.** The plan asks for the parameter and states it stays `false` at every call site. detekt's `UnusedParameter` fires on non-public functions, so it needed either a live branch or a suppression. A live branch would mean a teardown path that *can* dispatch a backend turn — something a future edit can switch on, and the phase's own standard is that a control a future edit can silently undo is not a control. It ships with `@Suppress("UnusedParameter")` (the same resolution 22-07 used for `origin`, and not a baseline entry) and a KDoc that states plainly that nothing reads it, why the D-08/D-12 apparent conflict resolves the way it does, and the two concrete hazards. Nobody can be misled: the annotation and the explanation sit on the declaration.
- **The stale-record branch was refactored rather than left alone.** 22-07's handoff note asked for exactly this, and it costs the plan's `== 7` grep. The alternative was a second removal site performing the same three steps — which is precisely how the SC3 record would have landed in one copy and not the other, since that is the field this plan added.
- **Clear Chat replaces the memory holder.** `ToolApprovalMemory` gained no `clear()` method. One assignment of a fresh instance drops both session sets and the repeat counter atomically, keeps the holder's narrow accessors intact (no caller can splice single entries in or out), and confines the change to `ChatPanel.kt` as the plan's file list intends.
- **`clearCurrentChat()` was split at the confirmation dialog.** `JOptionPane` builds a `JDialog` and throws `HeadlessException` under `-Djava.awt.headless=true`, which would have left the one path research found *permanently stuck* untestable. `clearChatState()` is `internal` for the same reason `MAX_AUTO_TOOL_ITERATIONS` is — module-scoped, invisible to any consumer of the shipped JAR. The dialog is the user clicking Yes; the seam is what that click does.
- **The whole `Run` / `Denied` outcome is passed, not just `origin`.** The audit record needs `tier` and `decision`; adding both to `executeApprovedToolCall` would have taken it to eleven parameters, past detekt's ceiling of ten. Passing the outcome keeps it at nine and removes a field-by-field unpack at the call site.
- **`chainStepFor` and `isKnownTool` are single functions.** The chain-step arithmetic existed in two copies and would have grown to four; `knownTool` must answer exactly what `tierFor` answers, or the audit record hashes the name of a tool that ran perfectly well.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Worktree spawned at the wrong base commit — the fifth occurrence this phase**

- **Found during:** Setup, before Task 1
- **Issue:** HEAD was `03f17a7` (the v0.9.2 release commit) rather than the required `dd7b13b`. None of the seven upstream dependencies existed; nothing in this plan could have compiled.
- **Fix:** Asserted the branch was in the `worktree-agent-*` namespace and the tree clean, then `git reset --hard dd7b13b…` exactly as the branch-check protocol prescribes.
- **Verification:** `git log --oneline -1` showed `dd7b13b docs(phase-22): update tracking after wave 4`.
- **Committed in:** n/a (pre-execution correction)

**2. [Rule 1 - Bug] The plan's `resolvePending` occurrence count cannot be met by correct code**

- **Found during:** Task 1
- **Issue:** The acceptance criterion expects **7** occurrences (2 declarations + 5 call sites) and 5 distinct reason constants. Two things break the arithmetic. First, `resolveAllPending` necessarily *calls* `resolvePending`, which the count does not allow for. Second, 22-07's inline stale-record branch is a sixth removal site; leaving it inline to protect the count would have kept a duplicate copy of the resolution — the exact ad-hoc-hook shape the plan's own objective forbids, and the copy that would have missed this plan's new SC3 line.
- **Fix:** Shipped 9 occurrences = 2 declarations + 1 internal delegation + 6 call sites, and 6 `ImplicitDenyReason` uses (`NEW_MESSAGE` twice). All five measured teardown paths are wired exactly once each.
- **Verification:** Read each site; `resolveAllPending` delegates, and no path retires a card outside the entry point.
- **Committed in:** `33e920d`

**3. [Rule 1 - Bug] `.report(` cannot reach 6 without duplicating call sites**

- **Found during:** Task 2
- **Issue:** The criterion expects **≥6** physical `.report(` calls, one per logical branch. But four approval decisions (`AUTO`, `SESSION_APPROVED`, `APPROVE_ONCE`, `APPROVE_SESSION`) share `executeApprovedToolCall`, and three denial decisions (`SESSION_DENIED`, `DENY`, `DENY_SESSION`) share `denyToolCall` — that funnelling is the plan's own "one construction" principle. Duplicating call sites to reach the number would create the drift the plan forbids.
- **Fix:** Shipped **5** physical sites covering **all eight** `ToolDecision` values plus the failure branch: the approved run, the extracted failure branch, the refusal, every implicit denial, and the vanished-transcript click. Enumerated against the enum rather than against the number.
- **Verification:** `everyDecisionEmitsTheSc3Metadata` drives an approve and a deny end to end and asserts both events; the decision-to-site mapping is listed in "Threat Model Coverage" below.
- **Committed in:** `ae4d6ab`

**4. [Rule 2 - Missing critical] A human click could leave no audit record at all**

- **Found during:** Task 2, enumerating which decisions reach a reporting site
- **Issue:** `resolveToolDecision`'s `panel == null` branch discharges the parked continuation and returns without dispatching. The user *clicked* — `APPROVE_ONCE`, `DENY`, whatever — and nothing was written anywhere. SC3 says every decision is recorded; this one was made and then vanished.
- **Fix:** Report the decision that was actually reached, with `runStatus = "error"` because nothing executed. Not in the plan's list of branches.
- **Files modified:** `ChatPanel.kt`
- **Committed in:** `ae4d6ab`

**5. [Rule 1 - Bug] `report(...)` inside a safe-call argument list would have been skipped**

- **Found during:** Task 2, writing the first merge
- **Issue:** The natural shape — `supervisor.aiRequestLogger?.log(…, metadata = toolDecisionReporter.report(…))` — does not evaluate its arguments when the safe call short-circuits. `aiRequestLogger` is nullable, so with the AI Activity logger absent the audit event and the Output line would both silently disappear, making SC3 conditional on an unrelated sink being wired.
- **Fix:** Every branch assigns the report to a local first and passes the local. A comment at both sites records why the order is not cosmetic.
- **Committed in:** `ae4d6ab`

**6. [Rule 1 - Bug] `knownTool` as written in the plan disagrees with the tier resolution**

- **Found during:** Task 2
- **Issue:** The plan says `knownTool = McpToolCatalog.all().any { it.id == canonicalId }`. 22-05's call contract (from the reporter's author) says it must be the same test `ToolApprovalGate.tierFor` performs — a catalog entry **or** an `ext:` name. An `ext:` tool would otherwise be recorded as an unresolvable name and have its ID hashed, even though the extension recognises it well enough to tier it.
- **Fix:** `isKnownTool` includes the `ext:` clause, in one function called from all five branches.
- **Committed in:** `ae4d6ab`

**7. [Rule 1 - Bug] A latent harness defect made every new lifecycle assertion pass vacuously**

- **Found during:** Task 3, first test run (three of the four new tests failed in ways that made no sense)
- **Issue:** `ChatPanelTestHarness.sendUserMessage` finds the first `JTextArea` under `ChatPanel.root`. Its KDoc claimed that was unambiguous — true when it was written, false since 22-07: `ToolApprovalCard` renders the model-supplied arguments into a read-only `JTextArea` as part of its anti-spoofing rule, and the transcript is added to the layout **before** the input panel. So the moment a card is on screen, a second `sendUserMessage` types into the *card* and clicks Send on an empty input, where `sendFromInput` returns immediately. Silent no-op. 22-07 never hit it because none of its ten tests sends a second user message after a card exists — and any new lifecycle test would have "passed" by never sending anything.
- **Fix:** Look up the only **editable** `JTextArea` — a property of what the component is for, not of where it sits in the tree. The KDoc now records the trap.
- **Verification:** The three affected tests went from failing-for-the-wrong-reason to green; the marker test independently confirms the second message now reaches the panel.
- **Committed in:** `18993fd`

**8. [Rule 1 - Bug] Two of my own comments broke the plan's mechanical criteria**

- **Found during:** Tasks 1 and 3 acceptance
- **Issue:** A KDoc explaining that the implicit-denial payload is *not* an exception used the word `InterruptedException` (criterion: count must not increase, 2 → 3), and a comment quoted `Awaiting approval` verbatim (criterion: exactly 1). The same class 22-07 hit as its deviation 6.
- **Fix:** Reworded both to say the same thing without the literal. `shutdown()`'s comment was likewise reworded so that a mechanical search for a send call inside it also returns nothing, not just a careful read.
- **Committed in:** `33e920d`, `18993fd`

**9. [Rule 2 - Missing critical] The `Awaiting approval` marker was only grep-asserted**

- **Found during:** Task 3 verification
- **Issue:** T-22-35's mitigation had no behavioural assertion. A headless `JList` never paints, so the renderer never ran in any test — the marker could have been wired to the wrong predicate, or to nothing, and the suite would have stayed green.
- **Fix:** A fifteenth test invoking the production cell renderer directly and asserting absent → present → absent across a decision's lifetime. Costs the plan's `@Test == 14` criterion; the criterion's intent was "the four new tests landed", and they did.
- **Verification:** Non-vacuous — neutering the renderer's predicate turns it red on the middle assertion.
- **Committed in:** `a02d49b`

**10. [Rule 3 - Blocking] `executeApprovedToolCall` was heading past detekt's `LongMethod` ceiling**

- **Found during:** Task 2
- **Issue:** The function was already ~68 counted lines against a threshold of 80, and the reporter merge plus the `errorClass` handling would have taken it to ~82.
- **Fix:** Extracted the `runCatching` failure branch into `reportFailedToolCall` (nine parameters, one under `LongParameterList`). The branch is the one the plan warns is easy to miss, so giving it a name and a KDoc that says why it matters is an improvement rather than a workaround.
- **Committed in:** `ae4d6ab`

---

**Total deviations:** 10 auto-fixed (3 blocking, 5 correctness, 2 missing-critical). **No architectural deviations, no Rule 4 escalations.** Five (2, 3, 6, 8, and the count half of 9) were plan-text or plan-arithmetic corrections; three (4, 5, 7) were real defects that would have shipped — two of them holes in SC3 itself and one a test-infrastructure bug that would have made this plan's own assertions vacuous. No packages added or changed, consistent with `T-22-SC`.

## Issues Encountered

**1. `executeTool` still runs on the EDT — reported, deliberately not fixed.** All three call sites remain synchronous on the Event Dispatch Thread, exactly as 22-07 left them. This plan changed nothing about where the executor runs and added no marshalling to that path. REL-05 / Phase 23 owns it. Recorded here to keep the handoff unbroken rather than because anything new was learned.

**2. The parked continuation cannot be driven headlessly, and is asserted structurally.** The only way to park an `onCompleted` is `startSessionFromContext`, which goes through `ContextPreviewDialog.confirm` → `JOptionPane.showOptionDialog` → `HeadlessException`. `shutdownResolvesAllPendingDecisionsWithoutSendingATurn` therefore asserts behaviourally that every pending card is retired and that no turn is dispatched, and asserts structurally — on the single shared line all six call sites route through — that `onCompleted` is invoked and that nothing sends a message from there. This is the second structural assertion in the file (the first is `userDialogPathIsNotDoublePrompted`, for the same headless reason) and it is labelled as such in place.

**3. An implicit denial emits no `MCP_TOOL_CALL` record, by design.** The plan pins the emission-site count at three, so the implicit path writes the `mcp_tool_decision` audit event and the Output line but does not add a fourth AI Activity row. Four of the five implicit paths are tearing the session down, so there is no chat turn for such a row to belong to. Stated here so it is a decision rather than an omission.

**4. SC4's ChatPanel-side decrement is already behaviourally asserted.** `eightConsecutiveDenialsTerminateTheChainWithNoNinthTurn` (22-07) drives eight real denials through the production path and asserts exactly nine backend turns, then that further draining adds none. No additional integration assertion was warranted; it still passes unchanged, and the two new turn-count assertions in this plan exercise the same `verifySendChatCount` helper.

**5. The known `RedactionTest` flake did not fire** on any of the nine full-suite runs during this plan.

## Known Stubs

None. Every branch named in the plan is implemented and, with the two exceptions recorded under "Issues Encountered" (the headless-unreachable parked continuation, and the deliberate absence of a fourth `MCP_TOOL_CALL` site), asserted behaviourally.

Two items from 22-07's stub table are discharged here: the temporary denial-path telemetry is replaced by the SC3 record, and the five implicit-denial teardown paths are wired. The `sessionStates[…] ?: ToolSessionState()` latent defect is fixed.

## Threat Model Coverage

| Threat ID | Disposition | How this plan discharges it |
|-----------|-------------|------------------------------|
| T-22-10 | mitigate | One `resolvePending` / `resolveAllPending` pair called from all six removal sites — the five measured teardown paths plus the refactored stale-record branch. Proven by `clearChatResolvesThePendingCardAndClearsApprovalMemory`, demonstrated red when the call is removed. |
| T-22-33 | mitigate | `sendFollowup` is inert and defaults to `false`; no teardown path can start a turn. Proven by `shutdownResolvesAllPendingDecisionsWithoutSendingATurn` **and** `newMessageInTheSessionImplicitlyDeniesThePendingCard`, both red when a followup is added. |
| T-22-09 | mitigate | Five reporting branches covering all eight `ToolDecision` values: `AUTO` / `SESSION_APPROVED` / `APPROVE_ONCE` / `APPROVE_SESSION` via the approved run and its failure branch, `SESSION_DENIED` / `DENY` / `DENY_SESSION` via the refusal, `IMPLICIT_DENY` via `resolvePending`, plus the vanished-transcript click. The returned map is merged into the existing `MCP_TOOL_CALL`, so both sinks come from one construction. |
| T-22-34 | mitigate | `clearChatState` replaces the session's `ToolApprovalMemory`. Asserted by re-asking: after the clear the same tool raises a fresh four-action card, with the live grant asserted as a precondition so the test cannot pass vacuously. |
| T-22-35 | mitigate | `Awaiting approval` on the session row, refreshed on create and on both resolution paths; `switchToSession` scrolls the pending card back into view. Asserted through the production renderer, red when the predicate is neutered. |
| T-22-36 | mitigate | The discarded-fallback form is gone; `grep` returns 0 and the site uses the `getOrPut` form the rest of the file uses. |
| T-22-31 | mitigate | Every implicit denial discharges the parked continuation on the one shared line; asserted structurally (see Issue 2). |
| T-22-SC | accept | Zero packages added or changed; `build.gradle.kts` untouched. |

## Threat Flags

None. This plan adds no network endpoint, no auth path, no file access and no schema change at a trust boundary. The audit payload keys are 22-05's, unchanged.

## User Setup Required

None.

## Next Phase Readiness

- **For 22-09:** the lifecycle and the record are complete. `resolvePending(sessionId, reason)` is the only way a decision is retired without a click, and `ImplicitDenyReason` has no unused constant left. Any new teardown path must call it rather than touch `pendingDecisions` directly — that is now a one-line rule with a test behind it.
- **`clearChatState()` is `internal`** and is the seam any future test of Clear Chat should drive; `clearCurrentChat()` is the dialog wrapper and is not headless-safe.
- **`ChatPanelTestHarness.sendUserMessage` now requires an editable `JTextArea`.** If a future component puts an editable text area in the transcript, that lookup breaks loudly rather than silently — read deviation 7 before "simplifying" it.
- **For Phase 23 / REL-05:** `executeTool` remains on the EDT at three call sites. See Issue 1.
- **Line citations in this summary are current as of `a02d49b`.** Every symbol in the plan was re-located by name; the plan's line numbers were stale, as 22-07 warned.
- **No blockers.**

## Success Criteria

- [x] No teardown path can leave a pending decision unresolved or a continuation dangling — six sites, one entry point, proven red when one is removed
- [x] No implicit denial starts a backend turn; only an explicit `Deny` click sends the D-12 followup — proven red by two independent tests when a followup is added
- [x] Every decision, including `AUTO`, emits one enriched `MCP_TOOL_CALL`, one `mcp_tool_decision` audit event and one Output line, all from a single construction
- [x] A pending decision is discoverable from the sessions list and scrolls into view on return — the marker asserted through the production renderer
- [x] The discarded-fallback defect is gone

## Self-Check: PASSED

- FOUND: `src/main/kotlin/com/six2dez/burp/aiagent/ui/ChatPanel.kt`
- FOUND: `src/test/kotlin/com/six2dez/burp/aiagent/ui/ChatPanelToolGateTest.kt`
- FOUND: `src/test/kotlin/com/six2dez/burp/aiagent/ui/ChatPanelTestHarness.kt`
- FOUND: commit `33e920d`
- FOUND: commit `ae4d6ab`
- FOUND: commit `18993fd`
- FOUND: commit `a02d49b`
- No file deletions in any commit (`git diff --diff-filter=D` empty across the branch)
- `STATE.md` and `ROADMAP.md` untouched, as required in worktree mode
- `detekt-baseline.xml` untouched

---
*Phase: 22-agent-tool-call-trust-boundary*
*Completed: 2026-08-14*
