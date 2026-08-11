---
phase: 21-redaction-completeness
plan: 02
subsystem: security
tags: [kotlin, redaction, redos, regex, safe-regex, privacy, defaults]

# Dependency graph
requires:
  - phase: 13-privacy-redaction
    provides: SafeRegex interruptible-CharSequence ReDoS guard (DeadlineCharSequence, RegexTimeoutException, replaceAllSafe) and Defaults.MAX_REDACTION_BODY_CHARS
provides:
  - "SafeRegex.replaceAllSafeReporting — bounded replacement that reports whether the match completed"
  - "SafeRegex.SafeReplaceResult(text, timedOut) — the result carrier whose timedOut flag is the only reliable timeout signal"
  - "SafeRegex.replaceAllSafe reduced to a pure one-line delegate, fail-open contract intact"
  - "Defaults.MAX_REDACTION_BUDGET_MS = 2_000L — the total wall-clock bound for the body stage"
  - "Defaults.MAX_REDACTION_BODY_CHARS re-documented as the body-stage window width (value unchanged)"
affects: [21-06 body-stage rewrite, 21-07 ADR-14 and CONCERNS, redaction, privacy]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Reporting sibling: a utility that must fail soft keeps its façade byte-for-byte and gains a sibling returning a small data class carrying the diagnostic flag; the façade becomes a one-line delegate so the two can never diverge"
    - "Result-carrier data class modelled on backends/BackendDiagnostics.RetryEvent"

key-files:
  created: []
  modified:
    - src/main/kotlin/com/six2dez/burp/aiagent/redact/SafeRegex.kt
    - src/test/kotlin/com/six2dez/burp/aiagent/redact/SafeRegexTest.kt
    - src/main/kotlin/com/six2dez/burp/aiagent/config/Defaults.kt

key-decisions:
  - "replaceAllSafe became a pure delegate rather than a copy, so the fail-open façade and the reporting sibling cannot drift apart; the single production caller (Redaction.kt:272) was left untouched"
  - "SafeReplaceResult was written as a multi-line class signature with a trailing comma, matching DeadlineCharSequence and BackendDiagnostics.RetryEvent, because ktlint 1.5.0 defaults to the ktlint_official code style (no ktlint_code_style in .editorconfig) which forces multiline at >= 2 parameters"
  - "The MAX_REDACTION_BODY_CHARS comment retains the ContextCollector 4k/8k and MCP-tools/bounty-resolver caller context from the old block — that framing is still true and explains why the constant exists — while the fail-open 'skipped entirely' sentence is deleted"

patterns-established:
  - "Paired tests for a fail-soft/fail-closed seam: one test pins the façade contract, its sibling pins the flag that makes fail-closed possible, and a comment states why both must exist"
  - "Constants that describe not-yet-implemented behaviour carry their decision tag (D-02 / D-04) and their rejected alternatives inline, so the documentation cannot be silently orphaned from the decision"

requirements-completed: [PRIV-06]

# Metrics
duration: 8min
completed: 2026-08-11
---

# Phase 21 Plan 02: SafeRegex timeout reporting + redaction budget constants Summary

**`SafeRegex.replaceAllSafeReporting` makes a regex timeout observable via a `timedOut` flag — the prerequisite for a fail-closed body stage — while `replaceAllSafe` collapses to a one-line delegate with its fail-open contract untouched, and `Defaults` gains `MAX_REDACTION_BUDGET_MS = 2_000L` alongside a `MAX_REDACTION_BODY_CHARS` that now documents a window instead of a skip.**

## Performance

- **Duration:** 8 min
- **Started:** 2026-08-11T13:06:54Z
- **Completed:** 2026-08-11T13:14:35Z
- **Tasks:** 2
- **Files modified:** 3

## Accomplishments

- **The timeout is now observable.** `SafeRegex.replaceAllSafe` returns the original input on timeout, which is byte-identical to "the pattern matched nothing". A body stage built on it would be fail-**open** in exactly the case D-02 exists to close, while looking correct (T-21-03). `replaceAllSafeReporting` returns the same text plus a `timedOut` flag, so plan 21-06 can drop an unscanned window instead of passing it through.
- **The fail-open façade is provably unchanged.** `replaceAllSafe` is now literally `replaceAllSafeReporting(input, pattern, replacement, timeoutMs).text` — a delegate, not a copy, so the two implementations cannot drift. Its KDoc is unchanged apart from one added pointer sentence, `SafeRegexTest.kt:44`'s assertion message is byte-for-byte intact, and the single production caller `Redaction.kt:272` compiles untouched (T-21-14).
- **`timedOut` is pinned in both directions.** `catastrophicPatternReportsTimedOut` proves it true on `(a+)+$` over 2 000 `a`s within 200 ms; `benignPatternReportsNotTimedOut` proves it false on `\d+` over `abc123` with the replacement actually applied — the assertion that stops the flag from being vacuously true. `SafeRegexTest` went from 6 to 8 tests, 0 failures.
- **The fail-open sentence is gone from `Defaults.kt`.** *"Bodies over this limit are skipped entirely — not hung, not partially redacted"* documented the PRIV-06 / F5 behaviour this phase exists to remove. `MAX_REDACTION_BODY_CHARS` now documents the body-stage **window width** (PRIV-06 / D-04), keeping its name and its `1_000_000` value so an input at or below 1 MB stays a single pass with cost and behaviour identical to today.
- **`MAX_REDACTION_BUDGET_MS = 2_000L` exists** with its fail-closed contract, the `min(SafeRegex.DEFAULT_TIMEOUT_MS, remaining budget)` composition rule that stops a per-pattern deadline outliving the total, its measured sizing basis, and its deliberate non-configurability recorded inline (T-21-04).

## Task Commits

Each task was committed atomically:

1. **Task 1: Add `SafeRegex.replaceAllSafeReporting` and make `replaceAllSafe` delegate to it** — `bb579f6` (feat)
2. **Task 2: Repurpose `MAX_REDACTION_BODY_CHARS` as the window width and add `MAX_REDACTION_BUDGET_MS`** — `fc9b83b` (docs)

## Files Created/Modified

- `src/main/kotlin/com/six2dez/burp/aiagent/redact/SafeRegex.kt` — added `SafeReplaceResult` (`:65-68`) and `replaceAllSafeReporting` (`:79-92`); `replaceAllSafe` (`:105-110`) is now a one-line delegate. No new imports; still only `java.util.regex.Pattern` and `java.util.regex.PatternSyntaxException`.
- `src/test/kotlin/com/six2dez/burp/aiagent/redact/SafeRegexTest.kt` — added `catastrophicPatternReportsTimedOut` and `benignPatternReportsNotTimedOut` immediately after `catastrophicPatternTimesOutAndReturnsInput`, which is itself unmodified.
- `src/main/kotlin/com/six2dez/burp/aiagent/config/Defaults.kt` — `MAX_REDACTION_BODY_CHARS` (`:64`) re-documented as the window width; `MAX_REDACTION_BUDGET_MS = 2_000L` added (`:78`).

## Exact signatures for plan 21-06

Recorded here so the body-stage rewrite does not need to re-read `SafeRegex.kt`.

```kotlin
// object SafeRegex, src/main/kotlin/com/six2dez/burp/aiagent/redact/SafeRegex.kt

data class SafeReplaceResult(
    val text: String,
    val timedOut: Boolean,
)

fun replaceAllSafeReporting(
    input: String,
    pattern: Pattern,
    replacement: String,
    timeoutMs: Long = DEFAULT_TIMEOUT_MS,
): SafeReplaceResult
```

Call shape for the body stage, per `21-RESEARCH.md` Decision 4:

```kotlin
val budgetDeadlineNanos = System.nanoTime() + Defaults.MAX_REDACTION_BUDGET_MS * 1_000_000L
// per call:
val remainingMs = (budgetDeadlineNanos - System.nanoTime()) / 1_000_000L
val result = SafeRegex.replaceAllSafeReporting(
    window, pattern, "[REDACTED]",
    minOf(SafeRegex.DEFAULT_TIMEOUT_MS, remainingMs),
)
if (result.timedOut) { /* window not fully scanned — drop it behind a marker, do NOT use result.text */ }
```

`result.timedOut == true` ⇒ the window was not fully scanned ⇒ drop it and emit a marker. Never branch on
whether `result.text` differs from the input: it is identical in both the "no matches" and the "timed out"
cases, which is the entire reason D-14 exists.

## Decisions Made

- **`replaceAllSafe` as a delegate, not a copy.** The plan permitted either reading; the delegate is what makes the "byte-for-byte unchanged contract" claim structurally true rather than a promise. The one-line body is the exact string the plan's acceptance criterion greps for.
- **`SafeReplaceResult` written multi-line.** `.editorconfig` sets no `ktlint_code_style`, so ktlint 1.5.0 falls back to `ktlint_official`, whose `class-signature` rule forces a multi-line signature at two or more parameters. `DeadlineCharSequence` (2 params, multi-line, trailing comma) in the same file confirms it. The single-line form shown illustratively in `21-RESEARCH.md` §Decision 4 would not have passed `ktlintCheck`.
- **Kept the caller context in the `MAX_REDACTION_BODY_CHARS` comment.** The old block's note that `ContextCollector` truncates bodies to 4k/8k and that the large strings come from MCP tools and the bounty resolver is still accurate, and is what explains why a 1 MB constant is the right size. Only the fail-open sentence was deleted, not the whole block.
- **Recovered the worktree base before starting.** The worktree spawned at `03f17a7`, an ancestor of the required base `e76a65a` (`docs(21): create phase plan`). The branch-namespace assertion passed first, then `git reset --hard e76a65a` per the documented recovery, so the plan file and its phase context were present.

## Deviations from Plan

None — plan executed exactly as written. No deviation rule was triggered; no bug, missing critical functionality, blocking issue, or architectural question arose.

**Total deviations:** 0
**Impact on plan:** None.

## Issues Encountered

**One acceptance criterion is unsatisfiable as written (documentation defect in the plan, not a code defect).**

Task 1's criterion `grep -c 'java.awt\|javax.swing\|import com.six2dez.burp.aiagent' SafeRegex.kt` returns 0
in fact returns **1**, both before and after this plan's changes (verified against `git show HEAD:...`).
The single match is `SafeRegex.kt:21`, the AWT-free banner comment itself:

```
//   - AWT-free: no java.awt / javax.swing imports so Phase 15's scanner-side tripwire can reuse
```

That banner is load-bearing and must not be deleted. The criterion's **intent** — zero AWT imports and zero
project-internal imports — is satisfied and was verified directly: `grep -n '^import' SafeRegex.kt` returns
exactly `java.util.regex.Pattern` and `java.util.regex.PatternSyntaxException`. A future restatement of this
criterion should anchor on `^import` rather than matching anywhere in the file.

## Deliberate Transient State

**Between this plan and plan 21-06, `Defaults.kt` documents post-phase behaviour that `Redaction.kt` does not
yet implement.** `MAX_REDACTION_BODY_CHARS` now describes a window width and `MAX_REDACTION_BUDGET_MS` describes
a fail-closed budget, while `Redaction.apply` still holds the pre-Phase-21 skip and nothing yet reads
`MAX_REDACTION_BUDGET_MS`. This is intentional and was specified by the plan: the constant and its documentation
were kept together in one edit, rather than split across waves, precisely so the fail-open sentence could not be
forgotten. The two are reconciled inside this same phase by plan 21-06 (wave 3), which is the sole consumer of
`MAX_REDACTION_BUDGET_MS`.

`replaceAllSafeReporting` likewise has no production caller yet — `replaceAllSafe` delegates to it, so it is
fully exercised, but the fail-closed branch on `timedOut` arrives with 21-06.

## Known Stubs

None. Both new API surfaces are complete and fully tested; neither is a placeholder. The absence of a production
caller for `replaceAllSafeReporting` is a wave-ordering consequence documented above, not an unwired stub.

## Threat Flags

None. This plan introduces no new network endpoint, auth path, file access pattern, or schema change. `redact/`
gained no dependency and remains AWT-free. The three threats this plan is dispositioned `mitigate` for are
addressed: T-21-03 (timeout now observable via `timedOut`, with its KDoc naming it the only reliable signal),
T-21-04 (`MAX_REDACTION_BUDGET_MS` introduced with the `min(...)` composition rule recorded), and T-21-14
(`replaceAllSafe` is a delegate; its KDoc and `SafeRegexTest.kt:44` are unchanged). T-21-SC is satisfied trivially:
zero packages installed, no new Gradle dependency.

## Verification

All plan-level verification commands were run with the mandatory JDK 21 prefix and passed:

| Check | Result |
|---|---|
| `./gradlew test ktlintCheck detekt -q` | exit 0 (run after each task) |
| `git diff --stat -- detekt-baseline.xml` | empty — baseline did not grow (QUAL-07) |
| `SafeRegexTest` execution | `tests="8" failures="0"`, both new test names present in the JUnit XML |
| `grep -c 'skipped entirely' Defaults.kt` | 0 |
| `grep -c 'fun replaceAllSafeReporting'` / `'data class SafeReplaceResult'` | 1 / 1 |
| `grep -c 'replaceAllSafeReporting(input, pattern, replacement, timeoutMs).text'` | 1 — delegate, not a copy |
| `grep -c 'DeadlineCharSequence'` | 7 (≥ 4), still `private class DeadlineCharSequence` |
| `grep -c 'On timeout replaceAllSafe must return the original input unchanged (fail-open)'` | 1 — assertion message untouched |
| `grep -c 'MAX_REDACTION_BUDGET_MS = 2_000L'` / `'MAX_REDACTION_BODY_CHARS = 1_000_000'` | 1 / 1 |
| `grep -c 'window'` / `'D-04'` / `'D-02'` in `Defaults.kt` | 4 / 2 / 1 |
| `grep -n '^import' SafeRegex.kt` | only `java.util.regex.Pattern`, `java.util.regex.PatternSyntaxException` |

## User Setup Required

None — no external service configuration required.

## Next Phase Readiness

- **Plan 21-06 is unblocked.** Both primitives it depends on exist with the signatures recorded above, and neither file this plan touched is contended by another wave-1 plan.
- **Carry into 21-06:** the `min(SafeRegex.DEFAULT_TIMEOUT_MS, remainingBudgetMs)` rule is documented in `Defaults.kt` but not enforced anywhere in code — 21-06 must implement it, or a per-pattern deadline will outlive the total budget.
- **Carry into 21-07 (ADR-14 / CONCERNS):** `21-RESEARCH.md` flags that the eight **header-stage** rules still run unbounded on the full input and are outside D-01/D-02's scope. `MAX_REDACTION_BUDGET_MS` bounds the body stage only. ADR-14's title must claim "the body stage never fails open", not the unqualified form (T-21-08 residual, accepted).
- **Open question for 21-06 (planner's call, ~15 lines, from `21-RESEARCH.md` §Throughput):** on a window timeout, halve the window at a line boundary and retry to a depth of 2 before dropping. Headroom at 1 MB is only ~2.2× on Apple Silicon, so on a 2-3× slower machine dense form content would time out and, under D-02's fail-closed rule, drop a window that today passes through — a user-visible capability regression. `timedOut` is exactly the signal that retry loop needs.

---
*Phase: 21-redaction-completeness*
*Completed: 2026-08-11*
