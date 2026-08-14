---
phase: 22-agent-tool-call-trust-boundary
plan: 03
subsystem: mcp
tags: [sec-06, trust-boundary, canonicalisation, sealed-type, fail-closed, cwe-117]
requires:
  - phase: 22-agent-tool-call-trust-boundary
    provides: SecTier and the non-defaulted McpToolDescriptor.secTier field (plan 22-02)
provides:
  - McpToolExecutor.canonicalToolId
  - ToolApprovalGate.tierFor
  - ToolApprovalGate.approvedOrigin
  - ToolCallOrigin
  - ToolDecision
  - ImplicitDenyReason
  - sanitizeInline
  - sanitizeBlock
  - SecTierResolutionTest
affects:
  - com.six2dez.burp.aiagent.mcp
  - com.six2dez.burp.aiagent.mcp.tools
  - com.six2dez.burp.aiagent.ui
tech-stack:
  added: []
  patterns:
    - "One exposed canonicalisation function consumed by both sides, never a copied alias map"
    - "Top-level private class as an unforgeable capability token (file-private is the only Kotlin visibility that binds minting to one file)"
    - "Fail-closed runtime fallback distinct from the removed authoring default"
    - "Paired inline/block sanitizers declared once in the AWT-free layer, shared by the audit sink and the Swing card"
key-files:
  created:
    - src/main/kotlin/com/six2dez/burp/aiagent/mcp/ToolApprovalGate.kt
    - src/test/kotlin/com/six2dez/burp/aiagent/mcp/SecTierResolutionTest.kt
  modified:
    - src/main/kotlin/com/six2dez/burp/aiagent/mcp/tools/McpToolExecutorImpl.kt
key-decisions:
  - "ModelApproved shipped as a top-level private class — the plan's primary form compiled on Kotlin 2.1.21 with no error, so the internal-constructor fallback was not needed"
  - "approvedOrigin is a member of object ToolApprovalGate rather than a top-level extension function; the plan offered the extension form as an example, and a member keeps the mint site inside the object that owns the decision"
  - "The AWT-free contract and the context-free contract are stated in prose without naming the forbidden packages/types verbatim, because the plan's own acceptance greps require zero occurrences of those literals anywhere in the file"
  - "INLINE_MAX_LENGTH is a named constant rather than an inline 120 default, following the MagicNumber/QUAL-07 precedent in McpAccessControlDecision.kt"
patterns-established:
  - "Tier resolution order is fixed by the executor: canonicalise, then test the ext: namespace, then look up the catalog, then fail closed"
  - "Mutation testing as acceptance evidence: flip the invariant, record the exact assertion message, revert"
requirements-completed: [SEC-06]
duration: 20min
completed: 2026-08-14
---

# Phase 22 Plan 03: SEC-06 Decision Core Summary

**AWT-free tool-approval gate: one shared `canonicalToolId` seam consumed by both the gate and the executor, an unforgeable file-private model origin, fail-closed tier resolution for `ext:` and unknown names, and the paired CWE-117 sanitizers — proven by six tests and two mutations.**

## Performance

- **Duration:** ~20 min
- **Started:** 2026-08-14T09:50:12Z (worktree base)
- **Completed:** 2026-08-14T10:10:00Z
- **Tasks:** 3
- **Files modified:** 3 (2 created, 1 modified)

## Accomplishments

- **One canonicalisation seam, not two agreeing lists.** `resolveAlias` was `private`; it is now `McpToolExecutor.canonicalToolId`, public, with the ten-entry alias table byte-identical. The gate calls the executor's own function, so the tier on the card, the `toolName` in the audit record and the tool that actually runs cannot disagree.
- **SC5 is a compile-time control, not a comment.** The model-originated origin is a **top-level `private class`** in `ToolApprovalGate.kt` — file-private, so no other file in the module can construct it *or name its type*. A fourth parse-and-execute call site cannot obtain one without going through the gate.
- **Tier resolution fails closed, provably.** `ext:` derives `CONFIRM_EACH` from the namespace; unknown, empty and wrong-case names fall back to `CONFIRM_EACH`. Both rules were shown non-vacuous by deliberate mutation.
- **The sanitizers landed once, in the AWT-free file**, before either consumer exists — so plans 22-05 (audit) and 22-06 (card) cannot ship two subtly different copies of a shipped control.

## Task Commits

1. **Task 1: Expose canonicalToolId as the single canonicalisation seam** — `09fba2b` (refactor)
2. **Task 2: Create ToolApprovalGate.kt — origin type, decision vocabulary, and tierFor** — `83b6200` (feat)
3. **Task 3: Create SecTierResolutionTest** — `e24d0f7` (test)

## Files Created/Modified

- `src/main/kotlin/com/six2dez/burp/aiagent/mcp/ToolApprovalGate.kt` (created, 250 lines) — `ToolDecision` (8 constants), `ImplicitDenyReason` (5 constants), `ToolCallOrigin` sealed interface + file-private `ModelApproved`, `object ToolApprovalGate` with `tierFor` and `approvedOrigin`, and the `sanitizeInline` / `sanitizeBlock` pair.
- `src/test/kotlin/com/six2dez/burp/aiagent/mcp/SecTierResolutionTest.kt` (created, 130 lines) — six tests covering catalog tiers, all ten aliases, `ext:` derivation, three unknown shapes, gate/executor parity, and a sweep of all 59 catalog tools.
- `src/main/kotlin/com/six2dez/burp/aiagent/mcp/tools/McpToolExecutorImpl.kt` (modified, +20/-3) — rename plus visibility widening plus KDoc; both in-file callers updated.

## Verification Evidence

### Sanitizer behaviour check (required by Task 2 acceptance)

Run through a temporary probe test in the test source set, then removed before committing:

```
sanitizeInline("a\r\nInjected: line") = "aInjected: line"
inline control chars removed not replaced = true
---- sanitizeBlock raw output between markers ----
>>>{
  "host": "a	b",
  "n": 1
}<<<
block newline count = 3
block preserved tab = true
block dropped CR = true
```

Control characters are **removed, not replaced** — the forged-second-log-line vector (CWE-117) is closed — and the block form preserves `\n` and `\t` while still dropping `\r`.

### Mutation testing (required by Task 3 acceptance)

**1. Fail-closed fallback flipped `CONFIRM_EACH` → `AUTO`:** 6 tests completed, **1 failed**.

```
AssertionFailedError: Unrecognised tool name 'definitely_not_a_tool' must fail closed to
CONFIRM_EACH. ==> expected: <CONFIRM_EACH> but was: <AUTO>
```

**2. `tierFor` made to skip canonicalisation (`val canonical = rawToolName`):** 6 tests completed, **2 failed**.

```
AssertionFailedError: Alias 'history' did not resolve to its canonical tool's tier. A gate that
skips canonicalisation labels this call 'unknown tool' on the card and in the audit record while
the executor runs it as a known bulk-history tool. ==> expected: <CONFIRM> but was: <CONFIRM_EACH>

AssertionFailedError: The gate and the executor disagree about 'history'. Either side growing its
own alias handling is the defect this assertion exists to catch — there must be exactly one
canonicalisation function and both sides must call it. ==> expected: <CONFIRM> but was: <CONFIRM_EACH>
```

Both mutations were reverted; `git status` confirmed the gate file returned byte-identical to its commit before the test was committed.

### Acceptance greps

| Check | Expected | Actual |
|-------|----------|--------|
| `fun canonicalToolId` in executor | 1 | 1 |
| `private fun canonicalToolId` | 0 | 0 |
| `canonicalToolId(` in executor (decl + 2 calls) | 3 | 3 |
| `"site_map_history" -> "site_map"` (alias table intact) | 1 | 1 |
| `[auto]` marker in executor | 0 | 0 |
| `resolveAlias` anywhere in `src/` | 0 | 0 |
| `object ToolApprovalGate` | 1 | 1 |
| `sealed interface ToolCallOrigin` | 1 | 1 |
| Swing/AWT literals in gate | 0 | 0 |
| `enum class ToolDecision` | 1 | 1 |
| ToolDecision wire strings (4 sampled) | 4 | 4 |
| `enum class ImplicitDenyReason` | 1 | 1 |
| All five implicit-deny wire strings | present | present |
| `canonicalToolId` in gate | ≥1 | 2 |
| Alias literals copied into gate | 0 | 0 |
| `SecTier.CONFIRM_EACH` on non-comment lines | 2 | 2 |
| `private class ModelApproved` | ≥1 | 1 |
| `ModelApproved` in any other main file | none | none |
| `fun sanitizeInline` / `fun sanitizeBlock` | 2 | 2 |
| `u0080` (C1 range spelled out) | ≥1 | 2 |
| `class SecTierResolutionTest` | 1 | 1 |
| `@Test` in resolution test | 6 | 6 |
| Alias-input lines in test | ≥10 | 10 |
| `McpToolContext` in test | 0 | 0 |

**Gates:** `./gradlew ktlintCheck detekt test` exits 0 (105 test classes, `SecTierResolutionTest` 6/6 passing, 0 failures). `git diff --stat -- detekt-baseline.xml` is empty — the baseline was never touched.

### Which `ModelApproved` form shipped

The plan's **primary** form — a top-level `private class ModelApproved(val tier: SecTier, val decision: ToolDecision) : ToolCallOrigin` — compiled on Kotlin 2.1.21 with **no compiler error**. The `internal constructor` fallback was therefore not used, and no downgrade to a plain enum occurred. A sealed interface permits implementations in the same package and module, and top-level `private` (file-private) is more restrictive than the interface, which Kotlin allows.

## Decisions Made

- **`approvedOrigin` is a member of `object ToolApprovalGate`, not a top-level extension function.** The plan offered `internal fun ToolApprovalGate.approvedOrigin(...)` as an example ("e.g."). A member is strictly better here: an extension adds no encapsulation, needs an import to resolve, and could be shadowed. The security property is unchanged — the return type is the `ToolCallOrigin` interface, so callers never name the implementing type.
- **`INLINE_MAX_LENGTH = 120` as a named constant** rather than an inline default-argument literal, because detekt's `MagicNumber` is active with defaults (`detekt.yml` overrides only `LongMethod`, `LongParameterList`, `MaxLineLength` and `FunctionNaming`) and QUAL-07 forbids growing the baseline. Same precedent as `MAX_HEADER_VALUE_LENGTH` in `McpBlockedRequestReporter.kt`.
- **The gate KDoc records the D-09 non-application explicitly** rather than leaving it as an unexamined omission: the flood vector in Phase 20 was a remote unauthenticated peer, whereas here the ceiling is `MAX_AUTO_TOOL_ITERATIONS = 8` per chain (verified at `ChatPanel.kt:1211`) with a D-13 monotone counter, and coalescing cards would hide exactly the repetition the user needs to see.
- **The `ext:` and unknown branches carry separate comments.** They both return `CONFIRM_EACH` but for different reasons — one is *derivation* (D-04, from the namespace) and one is *fallback* (D-05 runtime, the gap D-03's authoring change does not cover). Collapsing them into one comment would lose the distinction a future reader needs.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Plan prose and acceptance grep conflicted on the AWT-free header wording**
- **Found during:** Task 2
- **Issue:** The action text required the file header to state *"no `javax.swing` and no `java.awt` import appears in this file"*, while the acceptance criterion required `grep -c 'javax.swing\|java.awt'` to return **0**. Writing the sentence verbatim would have failed the plan's own check.
- **Fix:** Stated the contract without the literal package tokens — *"No Swing type and no AWT type is imported here — the import list above is the whole proof."* Meaning preserved; the mechanical check passes.
- **Files modified:** `src/main/kotlin/com/six2dez/burp/aiagent/mcp/ToolApprovalGate.kt`
- **Verification:** `grep -c 'javax.swing\|java.awt'` returns 0; the header still names the contract.
- **Committed in:** `83b6200`

**2. [Rule 3 - Blocking] Same conflict in the test KDoc, caught by the acceptance grep**
- **Found during:** Task 3
- **Issue:** The test's class KDoc explained that the gate is context-free by naming the fixture type it deliberately does not use. The acceptance criterion requires `grep -c 'McpToolContext'` to return **0**; the first draft returned 1.
- **Fix:** Rephrased to *"No tool-context fixture appears here…"*. Re-ran the gates.
- **Files modified:** `src/test/kotlin/com/six2dez/burp/aiagent/mcp/SecTierResolutionTest.kt`
- **Verification:** `grep -c 'McpToolContext'` returns 0; `./gradlew test ktlintCheck detekt -q` exits 0.
- **Committed in:** `e24d0f7`

**3. [Rule 3 - Blocking] Worktree base was behind the stated plan base**
- **Found during:** Setup, before Task 1
- **Issue:** The worktree spawned at `03f17a7` (a v0.9.2 release commit), not the required base `f732736` (wave 1 merged). `git merge-base` returned `03f17a7`, so neither wave-1 plan's output was present — `SecTier` would not have existed.
- **Fix:** `git reset --hard f7327361b9972f3bf3a4abc5af9e393b1e2e4023`, exactly as the branch-check protocol prescribes, after the HEAD assertion confirmed the branch was in the `worktree-agent-*` namespace. Working tree was clean, so nothing was lost.
- **Verification:** `git rev-parse HEAD` matched the expected base; `git log` showed both wave-1 merge commits.
- **Committed in:** n/a (pre-execution correction)

---

**Total deviations:** 3 auto-fixed (3 blocking). No architectural deviations, no Rule 4 escalations.
**Impact on plan:** None on scope or design. Two were wording adjustments forced by the plan's own acceptance greps, resolved in favour of the mechanical check with meaning preserved. The third was environment correction. No packages added, consistent with `T-22-SC` (accept, zero dependency changes).

## Issues Encountered

- **Nothing consumes the new types yet, and that is deliberate.** `approvedOrigin`, `ToolDecision`, `ImplicitDenyReason` and `ToolCallOrigin` are declared but not called anywhere. detekt's unused-code rules target `private` declarations only, so `internal` declarations awaiting their wiring plans do not trip the gate. Confirmed clean.
- The private-class-implements-sealed-interface form was the one risk flagged by the plan (with a documented fallback). It compiled first time; no fallback was needed.

## Threat Model Coverage

| Threat ID | Disposition | How this plan discharges it |
|-----------|-------------|------------------------------|
| T-22-11 | mitigate | `ModelApproved` is a top-level `private` class in `ToolApprovalGate.kt`. `grep -rl 'ModelApproved' src/main/kotlin` lists only that file. No other file can construct it or name its type; `approvedOrigin` returns the interface. |
| T-22-12 | mitigate | One exposed `McpToolExecutor.canonicalToolId`, consumed by both sides. `gateAndExecutorConsumeTheSameCanonicalisation` went red under the skip-canonicalisation mutation with the intended message. |
| T-22-16 | mitigate | Unknown, empty and wrong-case names resolve to `CONFIRM_EACH`; explicit `assertNotEquals(AUTO, …)` on each. Proven non-vacuous by flipping the fallback to `AUTO` and observing exactly one failure. |
| T-22-03 | mitigate | `ext:` derives `CONFIRM_EACH` from the namespace prefix. `ext:demo:scope_check` asserted to be `CONFIRM_EACH` and explicitly asserted **not** `AUTO`, so an external tool cannot inherit a built-in's silent tier. |
| T-22-17 | mitigate | No tier marker added to `describeTools` / `buildToolPreamble`; `grep -c '\[auto\]'` returns 0. The executor KDoc records the omission as deliberate and names ADR-15. |
| T-22-SC | accept | Zero packages added or changed. |

**Threat flags:** none. This plan adds a pure decision function, type declarations and two string sanitizers — no network endpoint, no auth path, no file access, no schema change.

## Known Stubs

The following are **declared but not yet consumed**, which is the plan's explicit scope boundary, not an oversight:

| Symbol | Wired by |
|--------|----------|
| `ToolApprovalGate.approvedOrigin`, `ToolCallOrigin` | plan 22-07 (`executeTool` origin parameter) |
| `ToolDecision` (beyond `tierFor`'s vocabulary) | plan 22-04 (`evaluate`, session memory, denial constant) |
| `ImplicitDenyReason` | plan 22-08 (all five teardown paths) |
| `sanitizeInline`, `sanitizeBlock` | plans 22-05 (audit payload) and 22-06 (approval card) |

The plan's objective states this boundary explicitly: *"the decision state machine, session memory, the denial constant and iteration accounting land in plan 22-04, in this same file. Do not build them here."* Nothing in this plan's own goal is stubbed — `tierFor` is fully implemented and fully tested.

## Success Criteria

- [x] One canonicalisation function, consumed by the gate and the executor — `canonicalToolId`, with the parity assertion proven non-vacuous
- [x] `ext:` names resolve to `CONFIRM_EACH` by derivation; unknown names by fail-closed fallback; neither can be `AUTO` — both asserted, both mutation-tested
- [x] The model-originated origin variant is unconstructible outside `ToolApprovalGate.kt` — top-level `private`, grep-confirmed
- [x] `ToolApprovalGate.kt` imports no Swing or AWT type — grep-confirmed, and the whole test suite runs it with no harness

## Next Phase Readiness

Wave 2's sibling plans can proceed. `ToolApprovalGate.kt` is the file plan 22-04 extends in place (session memory, `evaluate`, the outcome type); the file header already records why the origin and the gate must stay co-located, so a future edit that splits them has a stated reason not to. `tierFor` is the entry point plans 22-05/22-06/22-07 consume.

One note for 22-07: `executeTool`'s signature is deliberately **unchanged** here — the `origin` parameter is that plan's work, and its six test callers will need to declare an origin.

## Self-Check: PASSED

- FOUND: `src/main/kotlin/com/six2dez/burp/aiagent/mcp/ToolApprovalGate.kt`
- FOUND: `src/test/kotlin/com/six2dez/burp/aiagent/mcp/SecTierResolutionTest.kt`
- FOUND: `src/main/kotlin/com/six2dez/burp/aiagent/mcp/tools/McpToolExecutorImpl.kt`
- FOUND: commit `09fba2b`
- FOUND: commit `83b6200`
- FOUND: commit `e24d0f7`

---
*Phase: 22-agent-tool-call-trust-boundary*
*Completed: 2026-08-14*
