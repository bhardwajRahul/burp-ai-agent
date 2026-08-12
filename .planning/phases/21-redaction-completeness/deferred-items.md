# Phase 21 — Deferred Items

Out-of-scope discoveries logged during execution. Nothing here was fixed by the plan that found it.

---

## D-21-01: `newlineFreeOversizeBodyIsScannedNotDestroyed` fails on the reference machine (W-04)

- **Found during:** plan 21-13, Task 2 verification
- **Status:** PRE-EXISTING — reproduced at the unmodified baseline, not introduced by 21-13
- **Test:** `RedactionTest.newlineFreeOversizeBodyIsScannedNotDestroyed`
- **Failing assertion:** `STRICT: the pair must be redacted IN PLACE, keeping its key — not removed
  wholesale ==> expected: <true> but was: <false>` (`RedactionTest.kt:1691`)
- **Observed time:** 2.191 s / 2.199 s against `MAX_REDACTION_BUDGET_MS = 2_000`

### Evidence it is pre-existing

`Redaction.kt` was restored to the committed baseline (`git checkout --`, with the in-progress
implementation backed up out-of-tree first) and the test was run in isolation. It failed there with
the **identical** assertion message. It then failed identically with 21-13's change applied. The
canary did not move.

### Why 21-13's change cannot reach it

The fixture is newline-free, and the test asserts that property itself
(`assertFalse(body.contains('\n'))`, `RedactionTest.kt:1677`). A body with no `'\n'` takes
`windowEnd`'s `lastNewline <= start` branch and returns before any boundary predicate is consulted,
so `isJsonPairBoundaryRisk` — and therefore the new `endsInsideOpenQuotedValue` clause — is never
evaluated for this input.

### Diagnosis

This is exactly `21-REVIEW-2.md` **W-04**: a wall-clock gate running at 97-101 % of the budget it
races. The review bisected the threshold and predicted the presentation precisely — at a reduced
budget, `keptKeyAssert` is the first assertion to break while
`assertFalse(output.contains("SC4-NEWLINE-SECRET-9"))` still holds. That is what was observed: the
secret assertion PASSED and only the capability assertion failed, i.e. the window was dropped
fail-closed. **No leak.**

The proximate trigger is machine load: plan 21-14 was executing concurrently in a sibling worktree,
competing for CPU on the same host.

### Not fixed here, deliberately

W-04 is an open reviewer warning with its own recommended fix (stop racing a production constant
with a fixture size; the deterministic half of CR-04 is already covered hardware-independently by
`splitPointCutsNewlineFreeWindowsInsteadOfRefusing`). Fixing it would mean editing a test outside
21-13's declared surface and re-litigating `MAX_REDACTION_BUDGET_MS`, which the plan forbids. It is
recorded here rather than absorbed.
