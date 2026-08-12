# Phase 21 — Deferred Items

Out-of-scope discoveries logged during execution. Nothing here was fixed by the plan that found it.

---

## D-21-01: `newlineFreeOversizeBodyIsScannedNotDestroyed` fails on the reference machine (W-04)

- **Found during:** plan 21-13, Task 2 verification
- **Status:** **CLOSED by plan 21-16.** The diagnosis below was half right — see the correction at the
  end of this entry, and D-21-02 for the product residual it uncovered.
- **Status when filed:** PRE-EXISTING — reproduced at the unmodified baseline, not introduced by 21-13
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

### Correction, added by plan 21-16

The diagnosis above — and W-04's, and the budget bisection behind both — identified a **real** cause
but not the **binding** one. Measured at the 21-16 base with the total budget raised to 60 000 ms
through an injected-budget seam, the test still failed, identically, on every run:

```
mult=4  in=4000005  out=928   incomplete=16  budgetMarkers=0  keptKey=false   (3/3 runs)
mult=3  in=3000019  out=3000009  incomplete=0  budgetMarkers=0  keptKey=true  (3/3 runs)
mult=2  in=2000007  out=1999997  incomplete=0  budgetMarkers=0  keptKey=true
mult=1  in=1000021  out=1000011  incomplete=0  budgetMarkers=0  keptKey=true  (20/20 runs)
```

Zero budget markers at a 60 s budget: the total budget was never the thing stopping it at 4x.
Reproduced identically **with and without** the JaCoCo agent, so instrumentation was not it either.

The binding constraint is `SafeRegex.DEFAULT_TIMEOUT_MS` (50 ms) against the size of the deepest
piece `dropOrRetry` can produce, which is `fixture / 2^WINDOW_RETRY_MAX_DEPTH`. At 4x that piece is
250 000 characters and no longer scans in time, so all 16 are dropped and the body is destroyed.
Closed by sizing the fixture to the ladder's measured capability (`NEWLINE_FREE_WINDOW_MULTIPLIER`
4 -> 1, with the two-sided bound written into its comment) **and** by the injected budget, which is
still needed: at the corrected size the stage takes up to 779 ms of the shipped 2 000 ms budget,
which is 2.6x headroom and under this phase's own 3x bar.

**`Defaults.MAX_REDACTION_BUDGET_MS` was not touched and remains `2_000L`.**

---

## D-21-02: the retry ladder's capability ceiling is ~3 MB, and it moved when a rule got slower

- **Found during:** plan 21-16, Task 1 (while closing D-21-01 / W-04)
- **Status:** OPEN — a product residual, not a test problem. Fail-CLOSED, so it is a capability
  limit rather than a leak.
- **Files:** `redact/Redaction.kt` (`dropOrRetry`, `WINDOW_RETRY_MAX_DEPTH`, `scanWindow`),
  `redact/SafeRegex.kt` (`DEFAULT_TIMEOUT_MS`)

### The residual

`dropOrRetry` halves an unscannable window at most `WINDOW_RETRY_MAX_DEPTH` (4) times, so the
smallest piece the ladder can ever offer a rule is one sixteenth of the window. If that still
exceeds the 50 ms per-pattern deadline, **every** piece is dropped behind a marker and the body is
destroyed — exactly the CR-04 outcome, one size class up. The ceiling is therefore

```
2^WINDOW_RETRY_MAX_DEPTH  x  (whatever DEFAULT_TIMEOUT_MS can scan)
```

Measured on dense newline-free minified JSON on Apple Silicon / JDK 21: the deadline scans roughly
200 KB of that shape, putting the ceiling at about **3.2 MB**. Above it, a newline-free body is
destroyed. Measured directly: a 4 000 005-character body yields 928 characters of output and 16 drop
markers, on every run.

### Why it matters, and why it is not urgent

The MCP default `maxBodyBytes` is 2 MiB (2 097 152 characters), which is **inside** the ceiling, so
the payload CR-04 is actually about is covered. A user who raises `maxBodyBytes` past ~3 MB gets the
body destroyed instead of scanned. It fails closed, so no unscanned bytes reach a backend.

### The part worth acting on

**The ceiling moved silently.** Plan 21-11 sized its fixture at 4x on a measured ~31 ms/MB, which put
the depth-4 piece about 6 % inside the deadline. Plan 21-12 then factored `SENSITIVE_KEY_EXPR` and
raised `jsonSecretKeyRegex`'s cost by roughly half — its own commit message records 47 ms vs 58 ms on
a 1 MB body — and that pushed the depth-4 piece past the deadline. Nothing was watching the ceiling
except a test that everyone then read as flaky. **Any future change to a body rule's cost moves this
ceiling, and there is no assertion on it.**

### Fix approach (not applied — outside 21-16's surface)

`WINDOW_RETRY_MAX_DEPTH`, `MAX_REDACTION_BODY_CHARS` and `SafeRegex.DEFAULT_TIMEOUT_MS` are all
explicitly out of scope for plan 21-16. Options for whoever picks this up, in rough order of
appeal:

1. Let `dropOrRetry` keep halving while the total budget allows, replacing the depth cap with a
   minimum piece SIZE — the depth cap exists only to bound marker bloat at `2^depth`, and a size
   floor bounds it just as well while making the ceiling proportional to the budget rather than to
   the rule's speed.
2. Assert the ceiling: a test that measures what the deadline can scan and fails when the ladder can
   no longer reach it, so a rule getting more expensive is a visible test change rather than a
   mystery flake three plans later.
3. Accept and record it in ADR-14 / `CONCERNS.md` alongside the other body-stage residuals.

---

## D-21-03: the three boundary sweeps carry the same per-pattern-deadline exposure, under CPU contention

- **Found during:** plan 21-16, Task 2 (the timing-exposure audit the plan required)
- **Status:** OPEN — recorded with measurements. Not converted, deliberately: 21-16's plan states
  "do NOT convert the three boundary sweeps reflexively: they drive `Redaction.apply` end to end, so
  converting them would change what they test".
- **Tests:** `RedactionTest.windowedScanRedactsJsonPairAcrossEveryBoundaryAlignment`,
  `.jsonPairWithBlankLineBetweenKeyAndValueIsRedacted`,
  `.windowedScanRedactsJsonPairWhoseValueStraddlesTheCut`

### What was measured

Each sweep runs 24 alignments of a ~1 MB body through `Redaction.apply`, and each alignment's
anti-vacuity leg asserts that NEITHER marker shape appears — i.e. it asserts SUCCESSFUL redaction,
which is the assertion shape that carries wall-clock exposure.

| Condition | Worst alignment | Bound | Headroom |
|---|---|---|---|
| Warm JIT (after other tests) | 430 ms | 2 000 ms total budget | 4.7x |
| Cold JVM, sweep run alone (3 runs) | 502-649 ms | 2 000 ms total budget | 3.1-4.0x |

Against the TOTAL BUDGET the headroom is adequate — above this phase's 3x bar in both conditions,
and no run produced a marker.

### The exposure that has no headroom number

Observed **once in roughly ten runs**, during
`./gradlew test --tests "com.six2dez.burp.aiagent.redact.*" ktlintCheck detekt` — one Gradle
invocation, so `detekt` (which runs `parallel = true`) and `ktlintCheck` compete with the test JVM
for CPU:

```
windowedScanRedactsJsonPairAcrossEveryBoundaryAlignment  1.552 s
shift=0: the sweep must prove the pair was REDACTED, not that the window was DROPPED
  ==> expected: <false> but was: <true>
```

It failed at the FIRST alignment, with the total budget not exhausted (no `BUDGET EXCEEDED`
marker) — so this is the **per-pattern** limb, not the total-budget limb: a ~1 MB window missed
`SafeRegex.DEFAULT_TIMEOUT_MS` and was dropped. That limb has no headroom multiple, because it is a
per-window, per-rule check rather than a single measurable total, and it is the same mechanism as
D-21-02. Not reproduced in 9 further runs (3 package-only, 3 combined, 3 cold-JVM).

**No leak in either direction:** the failing leg is the anti-vacuity one, and the secret-absence
assertion stayed green — the window was dropped fail-closed.

### Fix approach (not applied)

Whatever closes D-21-02 closes this too, since it is the same deadline-versus-piece-size mechanism.
A cheaper interim step: stop running `test` and `detekt` in one Gradle invocation for the sampling
gate, since the project's own quick-run command in `21-VALIDATION.md` already separates them.
