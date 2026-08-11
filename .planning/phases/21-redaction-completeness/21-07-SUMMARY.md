---
phase: 21-redaction-completeness
plan: 07
subsystem: redact
tags: [PRIV-05, PRIV-06, SC4, SC5, D-01, D-02, D-04, D-05, D-08, ADR-14, T-21-11, T-21-24, T-21-25, T-21-26, T-21-27, kotlin, redaction, privacy, fail-closed, documentation]

# Dependency graph
requires:
  - phase: 21-redaction-completeness
    plan: 06
    provides: "the shipped windowed fail-closed body stage, and the measured SC4 mutation point"
  - phase: 21-redaction-completeness
    plan: 04
    provides: "SENSITIVE_KEY_EXPR and the accepted over-redaction list cited by ADR-14 and CONCERNS.md"
  - phase: 21-redaction-completeness
    plan: 02
    provides: "SafeRegex.replaceAllSafeReporting / SafeReplaceResult.timedOut, consumed by the single-pass fix"
provides:
  - "SC4 proven by measurement: the two oversize tests fail against a body stage carrying the reinstated fail-open guard and pass against the shipped one"
  - "Redaction.bodyStage single-pass path is now fail-CLOSED — a timed-out rule discards the partial result and falls through to windowedScan on the ORIGINAL input"
  - "RedactionTest.subWindowBodyFailsClosed — the sub-1 MB fail-closed gate, mutation-verified RED"
  - "DECISIONS.md ADR-14 — the redaction body stage never fails open (PRIV-06)"
  - ".planning/codebase/CONCERNS.md — the header-stage, window-boundary and plural-key residuals, and refreshed symbol-anchored Redaction.kt references"
affects: [redaction, privacy, phase-21-verification]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Fail-closed fallthrough by reuse: when a bounded fast path cannot complete, discard its partial result and re-enter the slow path from the ORIGINAL input, rather than duplicating the slow path's marker/drop machinery"
    - "Two-variant mutation transcript: run both the orchestrator-measured mutation and the plan-literal mutation, and record that both produce an identical failure set"

key-files:
  created: []
  modified:
    - src/main/kotlin/com/six2dez/burp/aiagent/redact/Redaction.kt
    - src/test/kotlin/com/six2dez/burp/aiagent/redact/RedactionTest.kt
    - DECISIONS.md
    - .planning/codebase/CONCERNS.md

key-decisions:
  - "The single-pass timeout falls through to windowedScan(input, rules) computed from the ORIGINAL input, not the partially-processed string — avoids double-marking and any partial-application ordering artifact, and reuses the fail-closed machinery instead of duplicating it"
  - "The worst-case ceiling is stated in-code: a single-pass timeout plus the windowed retry can cost up to DEFAULT_TIMEOUT_MS per rule ON TOP of MAX_REDACTION_BUDGET_MS, because windowedScan starts its own budget; bounded and documented rather than silently absorbed"
  - "The sub-1 MB gate fixture uses a bare pathological custom pattern with NO sensitive key name, so no built-in rule can reach it — the 21-05 vacuity lesson applied"

requirements-completed: [PRIV-05, PRIV-06]

# Metrics
duration: 41min
completed: 2026-08-11
---

# Phase 21 Plan 07: SC4 Proof, the Last Fail-Open, and the Phase Record Summary

**SC4 is proven by measurement rather than asserted — both oversize tests were driven RED against a body stage carrying the reinstated fail-open guard — and the one fail-open this phase had introduced (a timed-out rule on the sub-1 MB single-pass path silently passing content through) is now closed by falling through to the fail-closed windowed scan, so ADR-14's claim that the body stage never fails open is literally true with no size carve-out.**

## SC4 — Red-Before-Green Transcript

The gate SC4 actually demands is not "the oversize tests pass" — it is "the oversize tests **fail**
against the defect". `RedactionTest.oversizeBodySkippedSafely` previously asserted the PRIV-06
fail-open *as correct behaviour*, so a rewrite that happened to be green both before and after would
have tested nothing. This section is the measurement.

**Why a whole-file rollback was not used.** Restoring `Redaction.kt` from the pre-phase commit does
not compile against the rest of the phase: `scanner/PassiveAiScannerPrompts.kt` imports
`Redaction.COOKIE_SECTION_HEADER` (plan 21-05), `App.kt` assigns `Redaction.truncationLogger`
(plan 21-06), and `RedactionTest` calls `resetTruncationWindowForTest`. **A compile failure proves
nothing about the defect** — it is not a red test run. The defect was therefore reinstated
surgically, with a single-line mutation that restores exactly the pre-Phase-21 contract documented
in the old `Defaults.kt` comment: *"Bodies over this limit are skipped entirely."*

**Two mutation variants were run, not one.** The orchestrator's brief specifies the mutation plan
21-06 had already measured (replace the windowed call with a passthrough); the plan file's Task 1
text specifies an inserted size guard. They are behaviourally identical — above the window width the
original passes through, at or below it the single pass is untouched — so both were executed and both
are recorded, removing any ambiguity about which text was honoured.

### Mutation A — the 21-06-measured mutation (orchestrator brief)

Applied at `src/main/kotlin/com/six2dez/burp/aiagent/redact/Redaction.kt:476`, inside `bodyStage`,
one line changed and nothing else:

```diff
@@ -476 +476 @@ object Redaction {
-        return windowedScan(input, rules)
+        return input
```

### Mutation B — the plan-literal insertion (`21-07-PLAN.md` Task 1, step 2)

Applied to the restored file, inserted immediately after the `if (rules.isEmpty()) return input`
fast-path line (`:461`) and before the single-pass guard (`:468`), one line added and nothing else:

```diff
@@ -462,0 +463,2 @@ object Redaction {
+        if (input.length > Defaults.MAX_REDACTION_BODY_CHARS) return input
+
```

### Exit codes and the complete failure list

| Run | Command | Exit code | Result |
|---|---|---|---|
| Mutation A | `./gradlew test --tests "com.six2dez.burp.aiagent.redact.*"` | **1** | 90 tests completed, **2 failed** |
| Mutation B | same | **1** | 90 tests completed, **2 failed** |
| Restored | same | **0** | 90 tests completed, 0 failed |

`RedactionTest` alone, from the JUnit XML: `tests="23" failures="2" errors="0"` under both
mutations, `tests="23" failures="0" errors="0"` restored.

**The complete list of methods that failed in the mutated runs — identical for A and B:**

| # | Failed method | Assertion site | Verbatim failure message |
|---|---|---|---|
| 1 | `RedactionTest > oversizeBodySecretDoesNotSurvive()` | `RedactionTest.kt:848` | `org.opentest4j.AssertionFailedError: STRICT: a secret past the old size cap must not survive the body stage ==> expected: <false> but was: <true>` |
| 2 | `RedactionTest > oversizeBodyFailsClosed()` | `RedactionTest.kt:888` | `org.opentest4j.AssertionFailedError: A window that could not be fully scanned must be dropped behind a marker, not passed through ==> expected: <true> but was: <false>` |

**Assertion failures, not compile failures.** Both mutated runs executed `> Task :compileKotlin` and
`> Task :compileTestKotlin` successfully — the only compiler output was two pre-existing
`JsonNode.fields()` deprecation warnings in `OllamaBackend.kt` and `InjectionPointExtractor.kt` and
one pre-existing `Check for instance is always 'true'` in `DesignComponentsTest.kt`, none of them
in `redact/`. The failures are `org.opentest4j.AssertionFailedError` raised from the test bodies.
This distinction is T-21-25 and it is satisfied: the mutation is a one-line change that compiles by
construction, and the red run is a real red run.

### Mapping each failed method to the SC4 property it covers

| Failed method | SC4 property covered |
|---|---|
| `oversizeBodySecretDoesNotSurvive` | A secret past the old cut-off does not survive. The fixture is 10 001 lines of 99 `x` (1 000 100 chars) followed by `api_key=SC4-SECRET-VALUE-7B3E`, so the secret sits in the **second** window. It is reachable by `formBodyParamRegex`'s `(^\|[?&])` leading-field anchor alone — `urlTokenParamRegex`, which runs unbounded in the header stage and would otherwise mask the defect, requires a `?` or `&` before the key and cannot reach it; the value is not bearer- or basic-prefixed and does not begin `eyJ` |
| `oversizeBodyFailsClosed` | A pathological custom pattern on an oversized input yields a marker, never passthrough. `(a+)+$` over 600 repetitions of 2 000 `a` plus `!` must produce `REDACTION INCOMPLETE` or `REDACTION BUDGET EXCEEDED`, and the input must not survive verbatim |

### Exactly those two methods failed, and no others

The failure list contains **exactly** `oversizeBodySecretDoesNotSurvive` and `oversizeBodyFailsClosed`
— no third method failed in either mutated run. **This is the expected result.** The mutation changes
behaviour only on the branch taken when `input.length > Defaults.MAX_REDACTION_BODY_CHARS`; every
other case in `RedactionTest`, and every case in `SafeRegexTest`, `SecretShapesTest` and
`SecretTripwireTest` (the other classes matched by the `com.six2dez.burp.aiagent.redact.*` selector,
90 tests in total), uses an input at or below the 1 MB window width and therefore takes the
single-pass path, which the mutation does not touch. Had any other method failed, the mutation would
have reached further than intended and this transcript would say so.

### Scope of this gate: the body stage only

This experiment exercises the **body stage** and nothing else. SC1 (cookie-section values), SC2
(`COOKIE`-typed parameters) and SC3 (sensitive-key matching) are **not** exercised by it, because
SC1 and SC2 live in the `stripCookies` **header stage** and SC3 lives in the shared
`SENSITIVE_KEY_EXPR` consumed by three regexes across both stages. They are covered by their own
mutation-verified tests in plans 21-04 (`sensitiveKeyNamesRedacted`, `benignKeyNamesNotRedacted`,
`camelCaseKeysRedactedWithAcceptedOverRedactions`) and 21-05 (`cookieSectionValuesRedactedPerName`,
`cookieSectionDecoyDoesNotShieldRealSection`, `cookieTypedParametersRedacted`, plus three
end-to-end cases in `PassiveAiScannerPromptRedactionTest`). SC4's own wording scopes this gate to
the oversize behaviour, which is what was measured here.

### The experiment left no trace

| Check | Result |
|---|---|
| SHA-256 of `Redaction.kt` before mutation | `5d2a60f81b442397e84f44842ee8111706f739823cca69b35a8d319c7bfa5269` |
| SHA-256 of `Redaction.kt` after restore | `5d2a60f81b442397e84f44842ee8111706f739823cca69b35a8d319c7bfa5269` — identical |
| `diff` scratch copy vs. working file | no output (byte-identical), both before and after |
| `git status --porcelain -- src/` | prints nothing |
| `git log --oneline -1 -- .../redact/Redaction.kt` | `3ce2198 feat(21-06): add the rate-limited truncation signal and wire it in App.kt` — still a plan 21-06 commit; neither mutation was ever committed |

The shipped file was copied to the session scratchpad with `/bin/cp -f` and restored from that copy
with `/bin/cp -f` (this environment aliases `cp` and `rm` to their interactive forms, which silently
no-op in a non-interactive shell). No red commit was made, no branch state was saved aside, and no
blanket working-tree reset was used — the shared `refs/stash` ref is off-limits across worktrees and
`git clean` is destructive here. This is a single-task local verification step and must not be
generalised into any other workflow.
</content>
