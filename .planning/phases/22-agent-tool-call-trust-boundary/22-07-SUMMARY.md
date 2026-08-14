---
phase: 22-agent-tool-call-trust-boundary
plan: 07
subsystem: ui
tags: [sec-06, trust-boundary, sc4-acceptance-gate, fail-closed, edt, swing, red-before-green, junit5, mockito]

# Dependency graph
requires:
  - plan: "22-01"
    provides: "ChatPanelTestHarness (real ChatPanel, real Send button) and -Djava.awt.headless=true in tasks.test"
  - plan: "22-02"
    provides: "SecTier and the locked tier table — scope_check AUTO, proxy_http_history CONFIRM, http1_request CONFIRM_EACH"
  - plan: "22-03"
    provides: "ToolCallOrigin, the file-private ModelApproved variant, ToolDecision, ImplicitDenyReason, canonicalToolId"
  - plan: "22-04"
    provides: "ToolApprovalGate.evaluate/resolve, ToolApprovalMemory, ToolApprovalOutcome, DENIAL_RESULT, nextIterationBudget, allowsFurtherToolCalls"
  - plan: "22-06"
    provides: "ToolApprovalCard — the pending card, resolve(), and both compact resolved variants"
provides:
  - "The SEC-06 gate wired into ChatPanel.maybeExecuteToolCall: tier evaluation before any executeTool call, the approval card, the four resolution callbacks, the denial-variant followup and the three-valued continuation outcome"
  - "McpToolExecutor.executeTool with a required, non-defaulted origin: ToolCallOrigin"
  - "ChatPanel.ToolCallOutcome { NOT_CHAINED, CHAINED, AWAITING_DECISION } and PendingToolDecision"
  - "ChatPanelToolGateTest — the SC4 acceptance gate, ten tests, demonstrated red at the pre-gate commit"
  - "ChatPanelTestHarness.findApprovalCard"
affects: ["22-08", "22-09", "phase-23"]

tech-stack:
  added: []
  patterns:
    - "Enforcement by TYPE rather than by CHECK: executeTool declares an origin it never consumes, because a runtime branch is something a future edit can weaken while an unconstructible parameter is not"
    - "Three-valued continuation outcome so a parked callback is distinguishable from a discharged one, without adding EDT marshalling"
    - "Red-before-green proved in a detached git worktree pinned to the pre-gate SHA, copying ONLY test sources — never git stash, never a path-scoped revert"
    - "Test expectations derived by walking the real budget helpers, so raising the budget moves the expectation instead of leaving a stale literal passing"

key-files:
  created: []
  modified:
    - src/main/kotlin/com/six2dez/burp/aiagent/ui/ChatPanel.kt
    - src/main/kotlin/com/six2dez/burp/aiagent/mcp/tools/McpToolExecutorImpl.kt
    - src/test/kotlin/com/six2dez/burp/aiagent/ui/ChatPanelToolGateTest.kt
    - src/test/kotlin/com/six2dez/burp/aiagent/ui/ChatPanelTestHarness.kt
    - src/test/kotlin/com/six2dez/burp/aiagent/mcp/AiGateMcpToolTest.kt
    - src/test/kotlin/com/six2dez/burp/aiagent/mcp/tools/McpToolScopeEnforcementTest.kt
    - src/test/kotlin/com/six2dez/burp/aiagent/mcp/tools/ProxyHistoryListenerPortFilterTest.kt
    - src/test/kotlin/com/six2dez/burp/aiagent/mcp/tools/McpToolParityTest.kt

key-decisions:
  - "executeTool became `internal` — Kotlin refuses to let a public function expose the internal ToolCallOrigin, and narrowing beat widening the security type"
  - "The unconsumed origin is suppressed in place with a justified @Suppress rather than added to detekt-baseline.xml, which QUAL-07 forbids growing"
  - "maybeExecuteToolCall was decomposed into four helpers because changing its return type invalidates its ReturnCount baseline ID, so it had to land at two returns"
  - "The pre-gate proof copy excludes test 10 and its derivation helper: they read a constant that is `private` at $PRE_GATE, and a compile failure is not a valid red"
  - "Test 9 drives the CONFIRM-tier proxy_http_history rather than the plan's AUTO scope_check, because an AUTO tool raises no card either way and could not tell a gated slash path from an ungated one"

requirements-completed: [SEC-06]

# Metrics
duration: ~95 min
completed: 2026-08-14
---

# Phase 22 Plan 07: Close SEC-06 — the gate in the path Summary

**A model-emitted `CONFIRM`-tier tool call can no longer reach Burp without a click, and that is proven by execution rather than argued: the same assertion that is green today fails at the pre-gate commit with `NeverWantedButInvoked`, naming `McpToolExecutorImpl.kt:694` as the line the model reached four times with nobody asked.**

## Performance

- **Duration:** ~95 min
- **Tasks:** 3 of 3
- **Files modified:** 8 (0 created)
- **Commits:** 3

## The pre-gate SHA, recorded verbatim

```
PRE_GATE = 5863de8ffc7fe163349e3f38ace3c2bde6bd2f36
```

Recorded by Task 1 **after** the worktree base correction and **before** the first production edit, to `/tmp/sec06-pregate.sha`. Written here so the proof is reproducible after `/tmp` is cleared.

**Proven pre-gate mechanically, not by reading its commit subject** (wave-4 merge subjects name no plan, so a subject test is neither satisfiable nor falsifiable here):

```
$ git grep -q 'ToolApprovalGate.evaluate' 5863de8… -- src/main/kotlin/com/six2dez/burp/aiagent/ui/ChatPanel.kt
EXIT=1        # FAILED, as required — the gate does not exist at this commit
```

Corroborated inside the proof worktree itself: `grep -c ToolApprovalGate ChatPanel.kt` returned **0**, `executeTool` still had three parameters, and `MAX_AUTO_TOOL_ITERATIONS` was still `private`.

## Accomplishments

- **The trust boundary is now a place in the code, not a claim.** `ToolApprovalGate.evaluate` runs before anything in `maybeExecuteToolCall` touches `McpToolExecutor`, and every non-`Run` outcome is fail-closed. There is no branch from parsed model output to Burp that goes around it.
- **SC5 is a compile-time property.** `executeTool`'s `origin` has no default, so all eleven call sites must declare one — and the model-approved variant is a file-private class inside `ToolApprovalGate.kt`, so the only way to obtain one is to go through the decision. A future parse-and-execute call site cannot reach Burp by writing `origin = MODEL`; it will not compile.
- **The intermediate commit was never ungated.** Task 1 shipped `Ask` as a denial — strict, fail-closed and releasable — and Task 2 replaced that branch with the real card. At no commit in this plan does a `CONFIRM` model call reach Burp unasked.
- **D-13 is structural, not a comment.** Both the success and denial branches call `ToolApprovalGate.allowsFurtherToolCalls` and `nextIterationBudget`; the inline arithmetic is gone. `eightConsecutiveDenialsTerminateTheChainWithNoNinthTurn` then proves it through the real `maybeExecuteToolCall` rather than the helper in isolation.
- **REL-01 is untouched and measurably so.** `SwingUtilities.invokeLater` appears exactly **10** times in `ChatPanel.kt`, the same count as at `HEAD~1` before Task 2. The resolution callback adds no marshalling because an `ActionListener` is already dispatched on the EDT. `assertEdt()` and its KDoc are byte-identical.
- **The acceptance gate is non-vacuous, demonstrated not asserted.** See the proof below.

## Task Commits

| # | Task | Commit | Type |
|---|------|--------|------|
| 1 | Thread the origin parameter and put the gate in front of executeTool (fail-closed) | `d49f721` | feat |
| 2 | Add the approval card, the parked continuation and the four resolution callbacks | `6ba817d` | feat |
| 3 | Land the SC4 acceptance assertion on a CONFIRM-tier tool | `e95cd94` | test |

## The red-before-green proof (mandatory evidence)

Run in a **detached `git worktree` pinned to `$PRE_GATE`**, never via `git stash` (GSD commits per task, so a stash would have been empty and the proof would have passed vacuously against the *fixed* code) and never via a path-scoped revert (which would have broken compilation of the eight updated test call sites).

Procedure executed, in order: `worktree remove --force` → `/bin/rm -rf` (this shell aliases `rm` to `rm -i`) → `worktree prune` → `worktree add --detach /tmp/sec06-pregate 5863de8…` → copy in **only** test sources → run → `worktree remove --force`. `ChatPanel.kt` and `McpToolExecutorImpl.kt` were **never** copied; the worktree kept the pre-gate production code throughout, and the main working tree was never modified.

### The failure — exact class and message

```
ChatPanelToolGateTest > confirmToolDoesNotReachBurpBeforeADecision() FAILED
    org.mockito.exceptions.verification.NeverWantedButInvoked at ChatPanelToolGateTest.kt:68

org.mockito.exceptions.verification.NeverWantedButInvoked:
proxy.history();
Never wanted here:
-> at com.six2dez.burp.aiagent.ui.ChatPanelToolGateTest.confirmToolDoesNotReachBurpBeforeADecision(ChatPanelToolGateTest.kt:68)
But invoked here:
-> at com.six2dez.burp.aiagent.mcp.tools.McpToolExecutor.executeToolResult$lambda$78(McpToolExecutorImpl.kt:694) with arguments: []
-> at com.six2dez.burp.aiagent.mcp.tools.McpToolExecutor.executeToolResult$lambda$78(McpToolExecutorImpl.kt:694) with arguments: []
-> at com.six2dez.burp.aiagent.mcp.tools.McpToolExecutor.executeToolResult$lambda$78(McpToolExecutorImpl.kt:694) with arguments: []
-> at com.six2dez.burp.aiagent.mcp.tools.McpToolExecutor.executeToolResult$lambda$78(McpToolExecutorImpl.kt:694) with arguments: []
```

**This is the valid red the plan demanded**, and it is stronger than required. The failure is the Mockito `never()` verification, not the card assertion — the run compiled, reached the assertion phase, and proved the tool *did* reach Burp. `McpToolExecutorImpl.kt:694` is exactly the `api.proxy().history()` line research measured. **Four** invocations, because at `$PRE_GATE` each chained turn re-emitted the same call and ran it again: attacker-influenceable model output driving repeated Burp reads with nobody asked, which is SEC-06 stated as a stack trace.

### Per-test outcome at `$PRE_GATE`

| Test | At `$PRE_GATE` | Expected there |
|------|----------------|----------------|
| `confirmToolDoesNotReachBurpBeforeADecision` | **FAILED — NeverWantedButInvoked** | **the proof** |
| `autoToolStillRunsWithNoCard` | PASSED | yes — the `AUTO` companion passes before AND after by design |
| `slashCommandPathIsNotDoublePrompted` | PASSED | yes — the slash path was never gated |
| `approveOnceExecutesTheCall` | FAILED — IllegalArgumentException | yes — no card exists pre-gate |
| `approveForSessionSuppressesTheNextCard` | FAILED — IllegalArgumentException | yes |
| `denyReturnsAResultThatLetsTheConversationContinue` | FAILED — IllegalArgumentException | yes |
| `denyForSessionResolvesLaterCallsWithNoCard` | FAILED — IllegalArgumentException | yes |
| `confirmEachOffersOnlyTwoActions` | FAILED — IllegalArgumentException | yes |
| `userDialogPathIsNotDoublePrompted` | FAILED — AssertionFailedError | yes — the origin is not declared pre-gate |

Afterwards `git worktree list` showed only the main worktree, this agent's worktree, and one pre-existing unrelated worktree belonging to another tool — no `sec06-pregate` entry — and `/tmp/sec06-pregate` no longer exists.

### The proof copy set, stated precisely

Copied in: `ChatPanelToolGateTest.kt` and `ChatPanelTestHarness.kt` (the harness had to come too, because Task 3 added `findApprovalCard` to it).

The shipped test file **cannot compile at `$PRE_GATE`**, and this had to be resolved before the proof meant anything:

```
e: ChatPanelToolGateTest.kt:241 Cannot access 'val MAX_AUTO_TOOL_ITERATIONS: Int': it is private in 'ChatPanel.Companion'
e: ChatPanelToolGateTest.kt:257 …
e: ChatPanelToolGateTest.kt:385 …
```

Those three references belong to test 10 and its derivation helper. The constant is `internal` only because **Task 1 widened it** — a test-visibility change that is *not part of the gate*. A compile failure invalidates the proof, so the copy set was fixed exactly as the plan directs. The proof copy is the shipped file **minus test 10 and `expectedTurnsForAFullyDeniedChain`, and nothing else**, produced by a script that asserts the SC4 test survived byte-identical:

```
removed test 10:  37 lines
removed helper:   21 lines
@Test in proof:   9 (shipped: 10)
SC4 gate test carried over BYTE-IDENTICAL: yes

$ diff shipped proof | grep '^[0-9]'
236,265d235
267,273d236
373,393d335        # deletion-only: three `d` hunks, zero additions
```

## Verification Results

| Check | Expected | Actual |
|-------|----------|--------|
| `./gradlew test ktlintCheck detekt -q` | exit 0 | **0** |
| `git diff --stat -- detekt-baseline.xml` | empty | **empty** (QUAL-07 held) |
| `ChatPanelToolGateTest` suite | 10 tests, 0 failures | `tests="10" skipped="0" failures="0" errors="0"` |
| `test -PexcludeHeavyTests=true --tests "*ToolGate*"` → `grep -c '<testcase'` | 10 | **10** — the PR gate runs all ten |
| `git diff --diff-filter=D` per commit | empty | **empty** — no deletions in any of the three |

### Acceptance greps

| Check | Expected | Actual |
|-------|----------|--------|
| `origin: ToolCallOrigin` in `McpToolExecutorImpl.kt` | 1 | 1 |
| `origin: ToolCallOrigin = ` (a default) | 0 | 0 |
| `ToolCallOrigin.UserDialog` / `UserSlashCommand` in `ChatPanel.kt` | 1 / 1 | 1 / 1 |
| `ToolApprovalGate.evaluate(` | 1 | 1 |
| `nextIterationBudget\|allowsFurtherToolCalls` | ≥4 | 4 |
| `coerceAtLeast(0)` inside `maybeExecuteToolCall` | 0 | 0 (only `:1268`, an unrelated progress-bar computation) |
| `approvalMemory` | ≥2 | 2 |
| `internal const val MAX_AUTO_TOOL_ITERATIONS` / `private const val …` | 1 / 0 | 1 / 0 |
| `Provide the final response using the tool result` | 1 | 1 |
| `enum class ToolCallOutcome` / `AWAITING_DECISION` | 1 / ≥3 | 1 / 4 |
| `if (!chained)` | 0 | 0 |
| `@GuardedBy("EDT")` above `pendingDecisions` | ≥1 | 1 |
| `ToolApprovalCard(` / `panel.addComponent(` | ≥1 / ≥2 | 1 / 3 |
| `ToolApprovalGate.resolve(` | 1 | 1 |
| `SwingUtilities.invokeLater` vs `HEAD~1` | not increased | 10 vs 10 |
| `TODO(22-07 Task 2)` | 0 | 0 |
| `sessionStates[…] ?: ToolSessionState()` | 1 | 1 (only the pre-existing `:363`, which 22-08 fixes) |
| `never()` / `atLeastOnce()` in the test | ≥2 / ≥1 | 7 / 1 |
| `@Test` / `findApprovalCard` / `DENIAL_RESULT` in the test | 10 / ≥4 / ≥1 | 10 / 9 / 1 |
| Eleven `executeTool` call sites compile | `compileKotlin compileTestKotlin` exit 0 | **0** |

### The derived turn count

`N = 9`, **derived, not hardcoded**, by walking the real budget with the real helpers:

```
remaining = ChatPanel.MAX_AUTO_TOOL_ITERATIONS (= 8); turns = 1
while (remaining > 0) { turns++; if (!allowsFurtherToolCalls(remaining)) break; remaining = nextIterationBudget(remaining) }
```

8 → 7 → 6 → 5 → 4 → 3 → 2 → 1, then `allowsFurtherToolCalls(1)` is false. That is **8 denials** (one per budget step) and **9 backend turns** (one initial user turn plus one followup per denial). The real chain produced exactly 9, so the derivation and the shipped behaviour agree rather than the test being fitted to the code. `assertEquals(8, ChatPanel.MAX_AUTO_TOOL_ITERATIONS)` is asserted separately, with a message telling a future editor to rename the test if the budget changes.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Worktree spawned at the wrong base commit — again**

- **Found during:** Setup, before Task 1
- **Issue:** HEAD was `03f17a7` (the v0.9.2 release commit), not the required `5863de8`. None of the six upstream dependencies existed; nothing in this plan could have compiled. This is the fourth occurrence in this phase (22-03, 22-04 and others hit it).
- **Fix:** Asserted the branch was in the `worktree-agent-*` namespace and the tree clean, then `git reset --hard 5863de8…` exactly as the branch-check protocol prescribes.
- **Verification:** `git log` showed the wave-3 merge plus 22-04/05/06's commits. **`PRE_GATE` was recorded after this reset**, so the red-before-green proof is anchored to the correct base.
- **Committed in:** n/a (pre-execution correction)

**2. [Rule 3 - Blocking] `executeTool` had to become `internal`**

- **Found during:** Task 1
- **Issue:** The plan specifies `fun executeTool(name, argsJson, context, origin: ToolCallOrigin)`. Kotlin rejects that outright: *"public function exposes its internal parameter type"*. `ToolCallOrigin` is `internal` by 22-03's deliberate design.
- **Fix:** Narrowed `executeTool` to `internal`. The alternative — widening `ToolCallOrigin` to public — would have loosened a security type declared by an upstream plan to satisfy a visibility technicality. Narrowing is strictly better and costs nothing: all eleven call sites are inside this module, and `internal` is still visible to the module's test compilation.
- **Verification:** `compileKotlin compileTestKotlin` exit 0; `executeToolResult` untouched, so `McpToolHandlers.kt:129` and the whole MCP-server path are unaffected (confirmed by reading it before editing, as the plan instructed).
- **Committed in:** `d49f721`

**3. [Rule 3 - Blocking] detekt `UnusedParameter` rejected the deliberately-unconsumed origin**

- **Found during:** Task 1
- **Issue:** `Function parameter 'origin' is unused. [UnusedParameter]` — detekt applies this rule to non-public functions, which `internal` now is. The parameter is unused **on purpose**: that is the entire design.
- **Fix:** A narrow `@Suppress("UnusedParameter")` whose justification sits in the KDoc immediately above it. **Not** a baseline entry — QUAL-07 forbids growing `detekt-baseline.xml` and the plan's own verification asserts the baseline diff is empty. The KDoc states why consuming it would be the actual bug: a runtime branch is a check, and a check is something a future edit can weaken; an unconsumed parameter whose only satisfying value must come from the gate cannot be weakened without deleting the type.
- **Verification:** `detekt` exit 0, baseline diff empty.
- **Committed in:** `d49f721`

**4. [Rule 3 - Blocking] The return-type change invalidates `maybeExecuteToolCall`'s `ReturnCount` baseline entry**

- **Found during:** Task 1 design, before the first edit
- **Issue:** `detekt-baseline.xml:890` suppresses `ReturnCount` for `maybeExecuteToolCall` using an ID that embeds the **full signature, ending `): Boolean`**. Task 2 changes that to `): ToolCallOutcome`, so the entry stops matching and `ReturnCount` (limit 2) fires as a *new* finding on a function that had five returns. Adding a fresh baseline entry is forbidden.
- **Fix:** Decomposed `maybeExecuteToolCall` into `executeApprovedToolCall`, `denyToolCall`, `askForToolApproval`, `dispatchResolvedToolCall` and `addSuppressedDecisionRow`, landing the function at exactly **two** returns. Same shape constraint 22-04 hit with `evaluate`, and the same verdict: the decomposition is better code, and the gate `when` now reads as one exhaustive branch per outcome.
- **Verification:** `detekt` exit 0 with the baseline unmodified.
- **Committed in:** `d49f721`, `6ba817d`

**5. [Rule 3 - Blocking] `askForToolApproval` tripped `LongParameterList`**

- **Found during:** Task 2
- **Issue:** detekt reports at **>= 10** parameters, not above 10; the function had exactly 10.
- **Fix:** Dropped the `state` parameter and re-read it with `sessionStates.getOrPut(sessionId) { ToolSessionState() }`, which returns the very instance the caller just resolved. Nine parameters, no behaviour change.
- **Committed in:** `6ba817d`

**6. [Rule 1 - Bug] An explanatory comment broke the plan's own acceptance grep**

- **Found during:** Task 1 acceptance
- **Issue:** `grep -c 'Provide the final response using the tool result'` must return 1. It returned 2, because the comment explaining why the denial branch does *not* use that line quoted the line verbatim.
- **Fix:** Reworded the comment to describe the success branch's closing line rather than quote it. The criterion is now mechanically true as well as semantically true.
- **Committed in:** `d49f721`

**7. [Rule 1 - Bug] The plan's expected Burp-call count for `approveForSessionSuppressesTheNextCard` does not match shipped behaviour**

- **Found during:** Task 3
- **Issue:** The plan says "assert … the Burp call happened twice". After `Approve for session`, *every* later turn in the chat is auto-approved, so the chain runs until the monotone budget stops it — measured up to 8 calls, not 2. `times(2)` would have been red forever.
- **Fix:** Asserted `atLeast(2)` — the property that matters is that a **second call ran with no second decision** — and asserted separately that **no decision button remains anywhere in the transcript**, which is the stronger and more direct statement of "the card was suppressed". A comment records that the exact count is the budget's business, not this test's.
- **Committed in:** `e95cd94`

**8. [Rule 1 - Bug] The plan's slash-command test could not distinguish gated from ungated**

- **Found during:** Task 3
- **Issue:** The plan specifies `/tool scope_check {}`. `scope_check` is `SecTier.AUTO`, so it raises no card **whether or not** the slash path consults the gate — the test would have passed even if `/tool` were fully gated, proving nothing about SC5. (`{}` would also have failed argument decoding before reaching Burp.)
- **Fix:** Drove `/tool proxy_http_history {"count":5}` — a `CONFIRM` tool. If the slash path consulted the gate, a card would appear; the test asserts none does, and that `api.proxy().history()` was called exactly once. Strictly stronger, and it is now a real SC5 assertion.
- **Committed in:** `e95cd94`

**9. [Rule 3 - Blocking] `ToolInvocationDialog` cannot be driven headlessly**

- **Found during:** Task 3
- **Issue:** `ToolInvocationDialog : JDialog(owner, "Invoke MCP Tool", ModalityType.APPLICATION_MODAL)`. Constructing a `JDialog` under `-Djava.awt.headless=true` throws `HeadlessException`, so the `:928` path cannot be executed in this suite at all.
- **Fix:** Took the plan's explicitly sanctioned fallback rather than weakening the assertion: `userDialogPathIsNotDoublePrompted` brace-matches `openToolDialog`'s body out of `ChatPanel.kt` and asserts three things — it still calls `McpToolExecutor.executeTool(`, it declares `ToolCallOrigin.UserDialog`, and it mentions neither `ToolApprovalGate` nor `ToolApprovalCard`. **This is the one structural test in the file**; a KDoc on the helper says so, so nobody mistakes it for the house style. Recorded here per the plan's instruction to state which form shipped.
- **Committed in:** `e95cd94`

**10. [Rule 3 - Blocking] The proof copy set had to exclude test 10**

- **Found during:** Task 3, first proof run
- **Issue:** Documented in full under "The proof copy set, stated precisely" above. In short: three references to `MAX_AUTO_TOOL_ITERATIONS`, `private` at `$PRE_GATE`, made the whole test source set fail to compile — and a compile failure is not a valid red.
- **Fix:** Excised exactly test 10 and its derivation helper from the **proof copy only**, via a script that asserts the SC4 acceptance test is carried over byte-identical and that the diff is deletion-only. The shipped file keeps all ten tests.
- **Verification:** The re-run compiled, reached the assertion phase, and produced the mandated `NeverWantedButInvoked`.
- **Committed in:** n/a (verification procedure)

**11. [Rule 3 - Blocking] `findApprovalCard` had to be `internal`, and the harness is not in the plan's `files_modified`**

- **Found during:** Task 3
- **Issue:** A `public` harness function cannot return the `internal` `ToolApprovalCard`. Separately, `ChatPanelTestHarness.kt` is absent from the plan's frontmatter `files_modified`, though Task 3's action text explicitly instructs adding `findApprovalCard` to it.
- **Fix:** Declared the function `internal`; recorded the extra file here. The frontmatter list was stale, not the action text.
- **Committed in:** `e95cd94`

**12. [Rule 3 - Blocking] ktlint `function-signature` and `no-consecutive-comments`**

- **Found during:** Tasks 1 and 3
- **Issue:** An EOL comment placed between a KDoc and its annotation, and a single-expression function wrapping a body that fits on the signature line.
- **Fix:** Folded the comment into the KDoc; joined the signature. No behaviour change.
- **Committed in:** `d49f721`, `e95cd94`

---

**Total deviations:** 12 auto-fixed (9 blocking, 3 correctness). **No architectural deviations, no Rule 4 escalations.** Nine were environment, tooling-threshold or verification-procedure corrections; three (6, 7, 8) corrected assertions that would have been wrong or vacuous as written. No packages added or changed, consistent with `T-22-SC`.

## Issues Encountered

**1. `executeTool` still runs on the EDT — deliberately unchanged, reported for Phase 23.** `maybeExecuteToolCall` and both resolution paths call `McpToolExecutor.executeTool` synchronously on the Event Dispatch Thread, exactly as before this plan. A slow tool therefore blocks the UI, and the approval card makes that *more* visible because the click that triggers it comes from the EDT too. This plan changed nothing about **where** the executor runs and deliberately added no marshalling; REL-05 / Phase 23 owns moving it. Recorded here rather than fixed, following the Phase 21 protocol.

**2. A pending card is currently retired only when a new `Ask` arrives in the same session.** The five implicit-denial teardown paths (new message, session deleted, chat cleared, project changed, unload) are plan `22-08`'s scope and the plan explicitly forbids adding ad-hoc hooks here. Until `22-08` lands its single `resolvePending` entry point, clearing the chat or deleting the session while a card is pending drops the parked `onCompleted` for that session. The in-plan case *is* handled: a second `Ask` retires the stale card as `IMPLICIT_DENY` **and** discharges its parked continuation, so T-22-31 is covered for the one path this plan owns.

**3. The known `RedactionTest` flake did not fire** on any of the six full-suite runs during this plan.

**4. `ToolApprovalOutcome.Run` equality is identity-based** (it holds the file-private `ModelApproved`). Nothing here compares outcomes with `assertEquals`; every assertion reads named fields, per 22-04's warning.

## Known Stubs

| Item | Status | Resolved by |
|------|--------|-------------|
| Denial-path telemetry (`status = "denied"` on the existing `aiRequestLogger` shape) | Temporary by plan instruction — a refusal is never silent, but this is not the SC3 record | `22-08` (22-05's `ToolDecisionReporter`) |
| The five implicit-denial teardown paths | Out of scope by explicit plan boundary; see Issue 2 | `22-08` (one `resolvePending` entry point) |
| Session-list `Awaiting approval` marker, `scrollToComponent` | Out of scope by explicit plan boundary | `22-08` |
| `sessionStates[session.id] ?: ToolSessionState()` at `ChatPanel.kt:363` | Pre-existing latent defect; **not copied** into any new code, which uses `getOrPut` | `22-08` |

Nothing in **this plan's own goal** is stubbed. The gate, the card, the four actions, both compact rows, the denial followup and the monotone budget are all fully implemented and all asserted against the real production path.

## Threat Model Coverage

| Threat ID | Disposition | How this plan discharges it |
|-----------|-------------|------------------------------|
| T-22-01 | mitigate | `evaluate` runs before any `executeTool` call; every non-`Run` outcome is fail-closed. Proven by `confirmToolDoesNotReachBurpBeforeADecision`, demonstrated **red at `$PRE_GATE`** with `NeverWantedButInvoked` and green after. |
| T-22-11 | mitigate | `origin` has no default; all eleven call sites declare one and the module compiles. The model-approved variant is unconstructible outside `ToolApprovalGate.kt`. |
| T-22-31 | mitigate | Every branch either invokes `onCompleted` or hands it into `sendMessage`; the stale-pending path resolves as `IMPLICIT_DENY` **and** discharges the parked continuation; a vanished transcript discharges it too. Remaining teardown paths are 22-08's, recorded in Issue 2. |
| T-22-19 | mitigate | The denial carries `DENIAL_RESULT` (no `Error:` prefix) in a followup whose closing line does not reference a tool result. `denyReturnsAResultThatLetsTheConversationContinue` asserts the constant is present, the success line is absent, and a further turn was issued. |
| T-22-08 | mitigate | Both branches call the same two budget helpers; `eightConsecutiveDenialsTerminateTheChainWithNoNinthTurn` drives 8 real denials and asserts exactly 9 backend turns, then that further draining adds none, then that Burp was never reached. |
| T-22-32 | mitigate | `:948` declares `UserDialog`, `:2125` declares `UserSlashCommand`, neither consults the gate. `slashCommandPathIsNotDoublePrompted` asserts behaviourally; `userDialogPathIsNotDoublePrompted` asserts structurally (see deviation 9). |
| T-22-18 | mitigate | `offersSessionActions` is passed through from the gate and never re-derived. `confirmEachOffersOnlyTwoActions` asserts `http1_request` renders exactly `Deny` and `Approve once`. |
| T-22-SC | accept | Zero packages added or changed; `build.gradle.kts` untouched. |

## Threat Flags

None. This plan adds no network endpoint, no auth path, no file access and no schema change. The one new file read is in **test** code (`functionBody` reads `ChatPanel.kt` from the project directory for the SC5 structural assertion) and ships in no artifact.

## User Setup Required

None.

## Next Phase Readiness

- **For `22-08`:** the seams it needs are in place. `pendingDecisions` is a `@GuardedBy("EDT") linkedMapOf<String, PendingToolDecision>` keyed on session ID; `PendingToolDecision` carries the card and the parked `onCompleted`. A single `resolvePending(sessionId, reason)` entry point should remove the record, call `card.resolve(ToolDecision.IMPLICIT_DENY, reason)` (idempotent) and discharge `onCompleted` — the three steps `askForToolApproval`'s stale-record branch already performs inline and which `22-08` should refactor to call the new entry point rather than duplicate.
- **Also for `22-08`:** replace `denyToolCall`'s temporary `status = "denied"` log with 22-05's `ToolDecisionReporter` (remember `knownTool` is computed at the **call site**), fix `ChatPanel.kt:363`'s discarded-fallback defect, and add the session-list marker and `scrollToComponent`.
- **For Phase 23 / REL-05:** `executeTool` remains on the EDT at three call sites. See Issue 1.
- **Line citations are current as of this commit** and were all re-located by symbol, never trusted from the plan text — which was stale by +20 lines as 22-04 recorded.
- **No blockers.**

## Success Criteria

- [x] A `CONFIRM` or `CONFIRM_EACH` model-emitted tool call does not reach Burp until the user clicks — proven red-before-green
- [x] `AUTO` tools still run with no card (22-01's companion, kept and not inverted)
- [x] Denial returns the D-12 constant in a denial-variant followup and the conversation continues
- [x] `ToolInvocationDialog` and `/tool` are not double-prompted and are distinguishable in code
- [x] The REL-01 EDT contract and `assertEdt()` are unchanged; no new marshalling (`invokeLater` count 10 → 10)

## Self-Check: PASSED

- FOUND: `src/main/kotlin/com/six2dez/burp/aiagent/ui/ChatPanel.kt`
- FOUND: `src/main/kotlin/com/six2dez/burp/aiagent/mcp/tools/McpToolExecutorImpl.kt`
- FOUND: `src/test/kotlin/com/six2dez/burp/aiagent/ui/ChatPanelToolGateTest.kt`
- FOUND: `src/test/kotlin/com/six2dez/burp/aiagent/ui/ChatPanelTestHarness.kt`
- FOUND: commit `d49f721`
- FOUND: commit `6ba817d`
- FOUND: commit `e95cd94`
- No file deletions in any commit (`git diff --diff-filter=D` empty for all three)
- `STATE.md` and `ROADMAP.md` untouched, as required in worktree mode
- `detekt-baseline.xml` untouched

---
*Phase: 22-agent-tool-call-trust-boundary*
*Completed: 2026-08-14*
