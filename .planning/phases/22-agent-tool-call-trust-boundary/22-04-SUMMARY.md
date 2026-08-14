---
phase: 22-agent-tool-call-trust-boundary
plan: 04
subsystem: mcp
tags: [sec-06, trust-boundary, state-machine, session-memory, fail-closed, denial-of-service, audit]
requires:
  - phase: 22-agent-tool-call-trust-boundary
    provides: ToolApprovalGate.tierFor, approvedOrigin, ToolDecision, ToolCallOrigin, McpToolExecutor.canonicalToolId (plan 22-03)
  - phase: 22-agent-tool-call-trust-boundary
    provides: SecTier and the non-defaulted McpToolDescriptor.secTier field (plan 22-02)
provides:
  - ToolApprovalMemory
  - ToolApprovalOutcome
  - ToolApprovalGate.DENIAL_RESULT
  - ToolApprovalGate.evaluate
  - ToolApprovalGate.resolve
  - ToolApprovalGate.nextIterationBudget
  - ToolApprovalGate.allowsFurtherToolCalls
  - ToolApprovalGateTest
affects:
  - com.six2dez.burp.aiagent.mcp
  - com.six2dez.burp.aiagent.ui
tech-stack:
  added: []
  patterns:
    - "Branch order as evaluation order: one `when` whose ordering makes 'this tier touches no session set' structural rather than asserted"
    - "Policy flags computed in the gate (offersSessionActions) so a UI surface cannot re-derive a security rule and get it wrong"
    - "Absence of a parameter as the control: no settings/enable/bypass argument means no future edit can pass one"
    - "Test expectations derived from the budget constant rather than written out, so raising the real budget cannot leave a stale literal passing"
key-files:
  created:
    - src/test/kotlin/com/six2dez/burp/aiagent/mcp/ToolApprovalGateTest.kt
  modified:
    - src/main/kotlin/com/six2dez/burp/aiagent/mcp/ToolApprovalGate.kt
key-decisions:
  - "evaluate() is a single `when` rather than three early returns — forced by detekt ReturnCount (limit 2) and strictly better, because the branch order now IS the evaluation order"
  - "ToolApprovalMemory re-canonicalises every key internally rather than trusting callers to pass a canonical ID (Rule 2 hardening against T-22-12)"
  - "resolve()'s `when` is exhaustive over all eight ToolDecision constants with no `else`, so a ninth constant fails the build here instead of silently defaulting"
  - "DENIAL_RESULT is a member of object ToolApprovalGate, not a top-level const, matching the plan's key_links reference and 22-03's approvedOrigin precedent"
  - "evaluate/resolve are object members because a top-level `evaluate` already exists in this package (McpAccessControlDecision.kt:111)"
patterns-established:
  - "Mutation testing as acceptance evidence, continued from 22-03: flip the invariant, record the exact assertion message, revert"
  - "Session-scoped policy state lives in a caller-owned holder the gate never stores, so two chat sessions cannot leak through the gate object"
requirements-completed: [SEC-06]
duration: 20min
completed: 2026-08-14
---

# Phase 22 Plan 04: SEC-06 Decision State Machine Summary

**The gate can now decide: three tiers resolved through one ordered `when`, four clickable actions writing session memory for exactly two of them, one neutral non-`Error:` denial constant shared by all four denial paths, and a monotone budget whose eight-step termination is a unit test — eleven tests, three mutations, no mocks and no Swing.**

## Performance

- **Duration:** ~20 min
- **Started:** 2026-08-14T10:12:52Z (worktree base)
- **Completed:** 2026-08-14T10:32:37Z
- **Tasks:** 2
- **Files modified:** 2 (1 created, 1 modified)

## Accomplishments

- **D-02's third tier is structural, not asserted.** `evaluate` is one `when` whose branch order is the evaluation order: `CONFIRM_EACH` returns before either session set is named, so "this tier consults neither and writes to neither" is a property of the control flow rather than a claim a future edit could quietly break. Twenty consecutive approvals leave both sets empty.
- **The four-versus-two button rule is computed once, in the gate.** `Ask.offersSessionActions` ships the D-11 rule to the card as a flag. The card reads it; it never re-derives it from the tier. Two surfaces independently deriving the same security rule is exactly how one of them ends up offering a session grant for `http1_request` (T-22-18).
- **D-09 is enforced by an omission.** `evaluate(rawToolName, memory)` — no settings object, no enable flag, no bypass argument. A parameter that does not exist cannot be passed `false` by a future edit, a malicious settings import, or a call site that just wants the tests green. Verified by grep, not by intent.
- **The denial constant is one string across four decisions.** `DENY`, `DENY_SESSION`, `SESSION_DENIED` and `IMPLICIT_DENY` all return byte-identical text, so the model cannot tell from the response whether a human clicked, an earlier click was applied, or nobody answered — and cannot tune a retry on the difference.
- **SC4 is proven in both halves on the AWT-free seam.** Clause 1: exactly `MAX_AUTO_TOOL_ITERATIONS` steps reach zero, strictly decreasing, saturating rather than going negative. Clause 2: a session-denied tool resolves instantly as `Denied` and never asks again, which bounds the retry loop structurally rather than leaning on the counter.

## Task Commits

1. **Task 1: Session approval memory and the evaluate/resolve state machine** — `5c5bb2b` (feat)
2. **Task 2: ToolApprovalGateTest — state machine, denial constant, iteration accounting** — `4f90645` (test)

## Files Created/Modified

- `src/main/kotlin/com/six2dez/burp/aiagent/mcp/ToolApprovalGate.kt` (modified, +256/-1, now 505 lines) — `ToolApprovalMemory` (three private collections behind narrow accessors), `ToolApprovalOutcome` (`Run` / `Ask` / `Denied`), `ToolApprovalGate.DENIAL_RESULT`, `evaluate`, `resolve`, `nextIterationBudget`, `allowsFurtherToolCalls`.
- `src/test/kotlin/com/six2dez/burp/aiagent/mcp/ToolApprovalGateTest.kt` (created, 364 lines) — eleven tests across D-02, D-10, D-11, D-12 and D-13, using the real catalog IDs `scope_check` / `proxy_http_history` / `http1_request` and the real alias `history`.

## Verification Evidence

### Test run

```
11 tests completed, 0 failed, 0 skipped
```

`JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test ktlintCheck detekt -q` exits **0**. `git diff --stat -- detekt-baseline.xml` is empty — the baseline was never touched.

### Mutation testing (required by Task 2 acceptance)

**1. `CONFIRM_EACH` allowed to gain session memory** — the early `CONFIRM_EACH` branch removed from `evaluate` and the session-action guard removed from `resolve`: **2 of 11 failed.**

```
AssertionFailedError: D-02/D-11: CONFIRM_EACH shows only Approve once / Deny. Offering a session
action here is T-22-18 — one click handing the model unlimited traffic-generating tools.
==> expected: <false> but was: <true>

AssertionFailedError: Expected java.lang.IllegalArgumentException to be thrown, but nothing was thrown.
```

**2. `DENIAL_RESULT` prefixed with the error marker:** **1 of 11 failed.**

```
AssertionFailedError: The denial constant must never carry the Error: prefix — it would be logged
as a tool failure and read by the model as something that broke. ==> expected: <false> but was: <true>
```

**3. `nextIterationBudget` changed to return `remaining`:** **1 of 11 failed.**

```
AssertionFailedError: The budget must fall one per call and stop at zero.
==> expected: <[7, 6, 5, 4, 3, 2, 1, 0, 0, 0]> but was: <[8, 8, 8, 8, 8, 8, 8, 8, 8, 8]>
```

All three were reverted with `git checkout -- <file>` (never `git clean`, never `git stash`), and `git status` confirmed the gate file returned byte-identical to `5c5bb2b` before the test was committed.

### Acceptance greps

| Check | Expected | Actual |
|-------|----------|--------|
| `class ToolApprovalMemory` | 1 | 1 |
| `sealed interface ToolApprovalOutcome` | 1 | 1 |
| `DENIAL_RESULT` occurrences | ≥4 | 6 |
| `not authorised by the user` | 1 | 1 |
| `"Error: ` literal in gate | 0 | 0 |
| `fun evaluate(` | 1 | 1 |
| `Settings\|enabled\|bypass` within 6 lines of `evaluate` | 0 | 0 |
| `fun nextIterationBudget\|fun allowsFurtherToolCalls` | 2 | 2 |
| Swing/AWT literals in gate | 0 | 0 |
| `AuditLogger\|aiRequestLogger\|logToOutput` in gate | 0 | 0 |
| `class ToolApprovalGateTest` | 1 | 1 |
| `@Test` | ≥10 | 11 |
| `startsWith("Error:")` in test | ≥1 | 1 |
| `mock(\|mock<` in test | 0 | 0 |

### Sampling command — read this before running the phase's quick filter

`22-VALIDATION.md`'s quick-run filter is `--tests "*ToolGate*"`. **`ToolApprovalGateTest` does not contain the substring `ToolGate`** (it is `ToolApprovalGateTest` — `Approval` sits between `Tool` and `Gate`), so that filter does **not** select this file. The per-task command for this plan is:

```
JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test --tests "*ToolApprovalGateTest*"
```

Use `--tests "*ToolApproval*"` to sweep this plan and 22-06's `ToolApprovalCardTest` together. The name is deliberate: `build.gradle.kts:161-171` excludes `*IntegrationTest` and similar suffixes from the PR gate, and `ToolApprovalGateTest` carries none of them, so it runs on every PR.

## Decisions Made

- **`evaluate` is a single `when`, not three early returns.** detekt's `ReturnCount` (limit 2) rejected the plan's literal three-return shape, and QUAL-07 forbids growing the baseline. The rewrite is strictly better: with one `when`, the branch order *is* the evaluation order, so "`CONFIRM_EACH` never reaches the session-set lookups" is enforced by the language rather than by a reader checking that an early `return` came first. The mandated order — AUTO, then CONFIRM_EACH, then denied-before-approved, then Ask — is preserved exactly.
- **`ToolApprovalMemory` re-canonicalises every key.** The plan's signatures imply the caller passes a canonical ID, and `evaluate`/`resolve` do. But the UI (plan 22-06) will hold the raw model string as well, and `canonicalToolId` is idempotent for an already-canonical ID, so routing every key through it costs nothing and makes "keyed on the canonical ID, never the raw model string" structural. Without it, a UI caller passing `history` would open a second counter and a second grant for a tool the user already approved as `proxy_http_history` (T-22-12).
- **`resolve`'s `when` is exhaustive over all eight `ToolDecision` constants**, with the three gate-derived ones mapping to `error(...)`, rather than a shorter four-branch `when` with an `else`. The `require` already rejects them at runtime; exhaustiveness adds a compile-time property — adding a ninth decision constant breaks the build *here*, at the place that must classify it, instead of silently falling into a default branch.
- **`DENIAL_RESULT`, `evaluate` and `resolve` are members of `object ToolApprovalGate`**, not top-level declarations. Two reasons: the plan's own `key_links` names `ToolApprovalGate.DENIAL_RESULT`, and a top-level `internal fun evaluate(facts, settings)` **already exists in this package** at `McpAccessControlDecision.kt:111`. A second top-level `evaluate` would be a confusing overload in every file that imports the package.
- **The budget walk in the test uses a `CONFIRM_EACH` tool.** Both `APPROVE_ONCE` and `DENY` are legal for that tier, so the approval walk and the denial walk differ in exactly one variable — the decision — which is the precise claim D-13 makes.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Worktree spawned at the wrong base commit**
- **Found during:** Setup, before Task 1
- **Issue:** The worktree HEAD was `03f17a7` (the v0.9.2 release commit), not the required base `389e41c`. `git merge-base` confirmed the drift. Neither 22-02's `SecTier` nor 22-03's `ToolApprovalGate.kt` existed, so nothing in this plan could have compiled. This is the third occurrence in this phase — 22-03 hit it too.
- **Fix:** `git reset --hard 389e41cd58eeafc06be57bee175e73b049440400`, exactly as the branch-check protocol prescribes, after asserting the branch was in the `worktree-agent-*` namespace and the tree was clean.
- **Verification:** `git rev-parse HEAD` matched; `git log` showed the wave-2 merge and 22-03's three commits.
- **Committed in:** n/a (pre-execution correction)

**2. [Rule 3 - Blocking] detekt `ReturnCount` rejected the plan's three-return `evaluate`**
- **Found during:** Task 1
- **Issue:** The action text specifies `evaluate` as an ordered sequence of early returns (AUTO, then CONFIRM_EACH, then a `when`). That is three `return` statements; detekt's `ReturnCount` limit is 2, and `./gradlew detekt` failed. Adding a baseline entry was not an option — QUAL-07 forbids growing `detekt-baseline.xml`, and the plan's own verification asserts the baseline diff is empty.
- **Fix:** Collapsed the three returns into one `when` with the same branch order. Semantics are identical; the ordering guarantee is stronger, because a `when` cannot evaluate a later branch unless every earlier one was false.
- **Files modified:** `src/main/kotlin/com/six2dez/burp/aiagent/mcp/ToolApprovalGate.kt`
- **Verification:** `./gradlew compileKotlin ktlintCheck detekt -q` exits 0; `git diff --stat -- detekt-baseline.xml` empty. Mutation 1 confirms the `CONFIRM_EACH` branch is still load-bearing.
- **Committed in:** `5c5bb2b`

**3. [Rule 1 - Bug] Every `ChatPanel.kt` line number in the plan was stale by +20**
- **Found during:** Task 1
- **Issue:** The plan cites `ChatPanel.kt:2157` (the `status = "error"` derivation), `:1475` (`restoreSessions`), `:1191` (`MAX_AUTO_TOOL_ITERATIONS`), `:1599-1605` (`ToolSessionState`) and `:2186-2195` (the `sendMessage` handoff). Plan 22-01 shifted the file by +20 lines. Writing the plan's numbers into KDoc would have shipped five citations that point at the wrong code — the exact defect `22-CONTEXT.md` D-14 ("claim only what ships") exists to prevent, and worse in a security file where the comment is the rationale.
- **Fix:** Re-located every reference by symbol, per the upstream context's instruction, and wrote the measured line numbers: `:2177`, `:1495`, `:1211`, `:1621`, `:2210`/`:2213`.
- **Files modified:** `src/main/kotlin/com/six2dez/burp/aiagent/mcp/ToolApprovalGate.kt`, `src/test/kotlin/com/six2dez/burp/aiagent/mcp/ToolApprovalGateTest.kt`
- **Verification:** Each cited line read directly from the file; `:2177` is `val status = if (result.startsWith("Error:")) "error" else "ok"` and `:2210`/`:2213` are the two handoff expressions the helpers reproduce.
- **Committed in:** `5c5bb2b`, `4f90645`

**4. [Rule 2 - Missing hardening] Memory keys were canonicalised by convention, not by construction**
- **Found during:** Task 1
- **Issue:** The plan states all three collections are keyed on the canonical ID, but the mechanism was "the gate canonicalises before calling". `recordRequest` is documented as the one method the UI calls **directly**, and the UI also holds the raw model string — so the invariant depended on a caller getting it right, in the one class where getting it wrong silently splits a user's approval across two keys.
- **Fix:** Added a private `key()` that routes every access through `McpToolExecutor.canonicalToolId`. Idempotent for canonical input, so the gate's path is unchanged.
- **Files modified:** `src/main/kotlin/com/six2dez/burp/aiagent/mcp/ToolApprovalGate.kt`
- **Verification:** `sessionMemoryIsKeyedOnCanonicalIdNotTheModelString` and `repeatCounterIncrementsPerCanonicalId` both pass the alias `history` directly and observe the canonical tool's grant and counter.
- **Committed in:** `5c5bb2b`

**5. [Rule 3 - Blocking] Mutation 1 as literally worded would have been vacuous**
- **Found during:** Task 2 acceptance
- **Issue:** The plan's first behaviour check is "make `evaluate` consult `approvedForSession` for `CONFIRM_EACH`". Applied literally, the suite stays **green** — nothing ever puts `http1_request` into `approvedForSession`, because `resolve`'s guard rejects `APPROVE_SESSION` for that tier and `APPROVE_ONCE` writes nothing. A mutation that cannot change behaviour proves nothing about the assertions.
- **Fix:** Mutated the actual T-22-18 defect instead — removed the `CONFIRM_EACH` branch from `evaluate` *and* the session-action guard from `resolve`, i.e. "CONFIRM_EACH behaves exactly like CONFIRM". Two tests went red, including `confirmEachNeverGainsSessionMemory` as the plan predicted.
- **Verification:** Failure messages recorded verbatim above; reverted and re-run green.
- **Committed in:** n/a (verification procedure, reverted)

---

**Total deviations:** 5 auto-fixed (4 blocking, 1 hardening). No architectural deviations, no Rule 4 escalations.
**Impact on plan:** None on scope, design or the shipped policy. Deviation 2 changed the shape of one function and strengthened its ordering guarantee; deviation 4 added defence in depth; the rest were environment, documentation accuracy and verification-procedure corrections. No packages added, consistent with `T-22-SC` (accept, zero dependency changes).

## Issues Encountered

- **Nothing calls `evaluate` or `resolve` yet, and that is the plan's boundary.** `executeTool`'s signature is still unchanged; wiring is plan 22-07's work. detekt's unused-code rules target `private` declarations only, so `internal` declarations awaiting their wiring plan do not trip the gate — same situation 22-03 recorded and confirmed clean.
- **The known `RedactionTest` flake did not fire** on any of the six full-suite runs during this plan, despite two sibling agents building concurrently.
- **`Run` is a `data class` holding a file-private `ModelApproved`.** Its generated `equals` therefore falls back to identity for the origin field, so two separately-minted `Run` values are never equal. Nothing in this plan compares outcomes by equality — every assertion reads named fields — but plan 22-07 should not start comparing `Run` instances with `assertEquals`.

## Threat Model Coverage

| Threat ID | Disposition | How this plan discharges it |
|-----------|-------------|------------------------------|
| T-22-08 | mitigate | `nextIterationBudget` is monotone and saturating. `iterationBudgetIsMonotoneAndTerminates` derives the expected walk from `MAX_AUTO_TOOL_ITERATIONS`, proves it strictly decreases to zero in exactly eight steps, and proves the denial walk is element-for-element identical to the approval walk. Shown non-vacuous by mutation 3. |
| T-22-18 | mitigate | `evaluate` returns for `CONFIRM_EACH` before either session set is named; `resolve` throws `IllegalArgumentException` on a session-scoped action for that tier. `confirmEachNeverGainsSessionMemory` asserts both after twenty approvals. Shown non-vacuous by mutation 1 (two tests red). |
| T-22-19 | mitigate | One `DENIAL_RESULT`, asserted byte-identical across all four denial decisions and asserted not to carry the `Error:` prefix that `ChatPanel.kt:2177` reads as a tool failure. Shown non-vacuous by mutation 2. |
| T-22-20 | mitigate | The gate holds no state; memory is a caller-owned holder. `sessionMemoryIsKeyedOnCanonicalIdNotTheModelString` approves in `memoryA` and asserts `memoryB` still asks and its approved set is still empty — D-10's target-A / target-B case. |
| T-22-21 | mitigate | `evaluate(rawToolName, memory)` accepts no settings object, no enable flag, no bypass parameter; grep over the six lines following the declaration returns 0 for all three tokens. |
| T-22-22 | mitigate | `approveForSessionSuppressesTheNextCard` asserts the suppressed call records `SESSION_APPROVED`, **not** `APPROVE_SESSION`; `denyForSessionResolvesLaterCallsInstantlyWithNoCard` asserts the same split for `SESSION_DENIED`. An auditor can distinguish "a human clicked for this call" from "an earlier click was applied". |
| T-22-SC | accept | Zero packages added or changed. |

**Threat flags:** none. This plan adds pure decision functions, an in-memory holder and one string constant — no network endpoint, no auth path, no file access, no schema change.

## Known Stubs

Declared here, consumed by later plans. This is the plan's stated scope boundary, not an oversight — the objective says the caller wiring is 22-07's work.

| Symbol | Wired by |
|--------|----------|
| `evaluate`, `resolve`, `ToolApprovalOutcome` | plan 22-07 (`maybeExecuteToolCall` gate branch, `executeTool` origin parameter) |
| `nextIterationBudget`, `allowsFurtherToolCalls` | plan 22-07 (replacing the inline arithmetic at `ChatPanel.kt:2210`/`:2213`) |
| `ToolApprovalMemory` | plan 22-07 (as a default-valued field on `ChatPanel.ToolSessionState`) |
| `recordRequest`, `approvedSnapshot`, `deniedSnapshot` | plan 22-06 (the card's repeat caption) |
| `DENIAL_RESULT` | plans 22-07 (denial followup template) and 22-05 (audit payload) |

Nothing in this plan's own goal is stubbed: `evaluate`, `resolve` and both budget helpers are fully implemented and fully tested.

**Reachability note for 22-06 and ADR-15:** `CONFIRM_EACH` consults neither session set, and 22-03's fail-closed fallback sends every unrecognised name to `CONFIRM_EACH`. Together those mean an unknown tool name **can never enter `approvedForSession` or `deniedForSession`**, so `22-UI-SPEC.md`'s compact-row unknown-tool string (`Not a known tool — no catalog entry matches this name.`) is **unreachable in this phase**. Plan 22-06 should implement it for `when` exhaustiveness only, and ADR-15 should record it under "claim only what ships".

## Success Criteria

- [x] `evaluate` returns `Run` for `AUTO` and for session-approved `CONFIRM`, `Denied` for session-denied `CONFIRM`, and `Ask` otherwise — with `CONFIRM_EACH` always asking (tests 1, 2, 3, 5, 6)
- [x] The four D-11 actions resolve as specified and write session memory only for the two session-scoped actions (tests 4, 5, 6, 7)
- [x] The denial result is one deterministic constant and never carries the `Error:` prefix (test 8, mutation 2)
- [x] The iteration budget is monotone, terminates in 8 steps from `MAX_AUTO_TOOL_ITERATIONS`, and is independent of the user's choice (test 9, mutation 3)

## Next Phase Readiness

Plan 22-07 has everything it needs and no ambiguity left in the policy: call `ToolApprovalGate.evaluate` before the executor, branch on the three outcomes, pass `Run.origin` to `executeTool`'s new parameter, and replace the inline budget arithmetic at `ChatPanel.kt:2210`/`:2213` with the two helpers so the denial branch and the success branch share one decrement.

Two things 22-07 must not do: re-derive `offersSessionActions` from the tier (read the flag), and compare `ToolApprovalOutcome.Run` values with `assertEquals` (the origin's equality is identity-based).

Plan 22-05's reporter reads `ToolDecision.wireValue` and `ToolApprovalOutcome`; plan 22-06's card reads `Ask.offersSessionActions`, `Ask.canonicalId` and `recordRequest`. Neither needs a change here.

## Self-Check: PASSED

- FOUND: `src/main/kotlin/com/six2dez/burp/aiagent/mcp/ToolApprovalGate.kt`
- FOUND: `src/test/kotlin/com/six2dez/burp/aiagent/mcp/ToolApprovalGateTest.kt`
- FOUND: commit `5c5bb2b`
- FOUND: commit `4f90645`

---
*Phase: 22-agent-tool-call-trust-boundary*
*Completed: 2026-08-14*
