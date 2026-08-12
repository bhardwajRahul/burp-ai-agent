---
phase: 21-redaction-completeness
plan: 09
subsystem: privacy
tags: [redaction, regex, windowing, json, kotlin, tdd, mutation-testing]

# Dependency graph
requires:
  - phase: 21-redaction-completeness (plan 06)
    provides: the windowed body stage, windowEnd and the one-line JSON boundary mitigation this plan replaces
  - phase: 21-redaction-completeness (plan 08)
    provides: the single-pass deadline-bounded redactCookieSections and the LargeClass suppression on RedactionTest
provides:
  - a LOOPING, capped JSON boundary extension in windowEnd that closes CR-02
  - MAX_JSON_BOUNDARY_LOOKAHEAD_LINES (8) bounding the extension so the fix cannot trade a leak for an unbounded window
  - isJsonPairBoundaryContinuation — a blank line continues an in-flight extension
  - pairMayBeInFlightAt — the initial test walks backward over blank lines
  - the WR-06 shifted-fixture sweep, the first assertion of the D-01 windowing invariant
  - three corrected records — the source comment, ADR-14 and CONCERNS.md
affects: [21-11 (CR-04, splitPoint and retry depth — same file), redaction, privacy, any future change to window boundaries]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Shifted-fixture sweep: walk a fixture across a deterministic cut over one full alignment period, rather than testing a single alignment"
    - "Anti-vacuity assertion on fail-closed paths: assert NO drop marker, so a security test cannot pass by dropping the region instead of redacting it"
    - "Mutation discipline: commit the real implementation FIRST, then mutate on top of the committed base and revert with git checkout -- <path>"

key-files:
  created: []
  modified:
    - src/main/kotlin/com/six2dez/burp/aiagent/redact/Redaction.kt
    - src/test/kotlin/com/six2dez/burp/aiagent/redact/RedactionTest.kt
    - DECISIONS.md
    - .planning/codebase/CONCERNS.md

key-decisions:
  - "The cap fails to a RECORDED RESIDUAL, not to a dropped window — dropping at the cap was rejected because the trigger is a content heuristic ordinary multi-line HTML attributes and nested YAML both satisfy"
  - "The initial boundary test walks BACKWARD over blank lines (pairMayBeInFlightAt) — forced by reproduction at shift 0, not by design taste"
  - "21-CONTEXT.md's locked D-01 AMENDED text left untouched: its mechanism shipped and is correct; only the evidence sentence was over-claimed"

patterns-established:
  - "Establish the single-pass control BEFORE writing a windowed-path test, so the asymmetry is measured rather than assumed"
  - "State fixture reachability as an explicit rule-by-rule elimination argument, then verify it by mutation"

# Metrics
duration: 40min
completed: 2026-08-12
---

# Phase 21 Plan 09: CR-02 / WR-06 — the windowed path no longer leaks what the single pass redacts

**`windowEnd`'s JSON boundary mitigation now loops under an eight-line cap instead of pulling exactly one line and never re-checking, closing a redaction bypass keyed only on payload size — and the three records that claimed otherwise were corrected in the same pass.**

## Performance

- **Duration:** ~40 min
- **Started:** 2026-08-12T08:50:00Z
- **Completed:** 2026-08-12T09:30:00Z
- **Tasks:** 3 of 3
- **Files modified:** 4

## Accomplishments

- **CR-02 closed.** A `jsonSecretKeyRegex` key/colon/value pair spread over three lines and straddling a window cut is now redacted on the windowed path at every alignment, as it always was on the single-pass path.
- **WR-06 closed.** The D-01 windowing invariant has an assertion for the first time. It sweeps a full pad-line period and is written so it cannot pass by dropping the window.
- **The fix is bounded.** `MAX_JSON_BOUNDARY_LOOKAHEAD_LINES = 8` stops the loop from trading an information leak for an unbounded window (T-21-08).
- **Three records corrected** so none outlives the claim CR-02 disproved: the source comment, ADR-14's residual, and the `CONCERNS.md` entry.

## Required measurements

The plan's `<output>` block requires five specific numbers. All five, measured rather than recalled:

### 1. First failing shift before the fix

| Sweep | First failing shift | Assertion that failed |
|---|---|---|
| `windowedScanRedactsJsonPairAcrossEveryBoundaryAlignment` | **shift=7** | `a JSON pair straddling a window boundary must still be redacted` |
| `jsonPairWithBlankLineBetweenKeyAndValueIsRedacted` | **shift=0** | `a blank line between key and value must not carry the pair past a window cut` |

Shift 7 reproduces the reviewer's measured `DIVERGENCE at shift=7 windowedLeak=true singlePassLeak=false` exactly. JUnit aborts the loop on the first failure, so 7 and 0 are first-failures; an independent full sweep against a verbatim transcription of the pre-fix `windowEnd` showed the complete failing sets:

- three-line pair: shifts **7, 8, 9, 10**
- four-line blank-gap pair: shifts **0-10 and 20-23**

### 2. The single-pass control — the asymmetry that IS CR-02

Run against the real `jsonSecretKeyRegex` before either test was written:

```
=== FACT 1: single-pass match of 3-line pair ===
input :   "token"\n  :\n  "BOUNDARY-SECRET-7"\n
output:   "token"\n  :\n  "[REDACTED]"\n
redacted? true
```

So the same fixture below the window width **is** redacted, and above it **was not**. That asymmetry — single pass redacts, windowed path leaks — is the whole of CR-02, and it is what makes this a redaction bypass keyed only on payload size rather than a coverage gap. It also falsifies D-01's byte-identity claim directly.

### 3. Does the four-line blank-gap shape match on the single-pass path at all?

**Yes.** This was checked before writing the fix, exactly as the plan required, because if it did not match there would be nothing to fix and the test would assert the wrong thing:

```
=== FACT 2: single-pass match of 4-line blank-gap pair ===
input :   "token"\n  :\n\n  "BLANK-GAP-SECRET-3"\n
output:   "token"\n  :\n\n  "[REDACTED]"\n
redacted? true

=== FACT 2b: whitespace-only gap line ===
redacted? true
```

A truly empty line and a whitespace-only line both match. So the second test asserts a property the engine genuinely has, and no implementation was contorted to satisfy it. The consequence recorded by the plan — drop the test and say so — did not apply.

### 4. Mutation checks

All three were applied **on top of the committed implementation** and reverted with `git checkout -- <path>`, per the discipline 21-08's interrupted executor established. No mutation was ever hand-edited back out, so an interrupt could not have stranded a defect in the tree.

| # | Mutation | Result | What it proves |
|---|---|---|---|
| **M1** | Loop cap forced to 1 — pull one line, never re-check (the pre-CR-02 form) | `windowedScanRedactsJsonPairAcrossEveryBoundaryAlignment` **FAILED at shift=7**; blank-gap sweep FAILED at shift=6 | The sweep tests the LOOP, not merely the presence of an extension |
| **M2** | `isJsonPairBoundaryContinuation` reduced to `isJsonPairBoundaryRisk` — blank no longer continues | **Only** `jsonPairWithBlankLineBetweenKeyAndValueIsRedacted` FAILED, at shift=6 | The blank-line handling is load-bearing, and the two tests guard **different** mechanisms — neither is redundant |
| **M3** | `pairMayBeInFlightAt` reduced to the plain forward-only initial test | **Only** `jsonPairWithBlankLineBetweenKeyAndValueIsRedacted` FAILED, at shift=0 | The backward walk is load-bearing and not defensive decoration |

Tree confirmed byte-identical to the commit after each revert (`git diff HEAD --stat` empty), and the suite re-verified green afterwards.

### 5. Residual-bullet counts in `DECISIONS.md`

Compared against the prior **committed** file rather than a remembered number, which is the counting discipline plan 21-07 recorded:

| | `grep -c '^- Residual' DECISIONS.md` |
|---|---|
| Before (`git show HEAD:DECISIONS.md`) | **2** |
| After | **3** |

Grew by exactly one, as required.

## Task Commits

1. **Task 1: The WR-06 sweep** — `b5d1480` (test — TDD RED)
2. **Task 2: Loop the extension under a cap, correct the D-01 comment** — `3d62891` (fix — TDD GREEN)
3. **Task 3: Correct ADR-14 and CONCERNS.md** — `ea79021` (docs)

## Files Created/Modified

- `src/main/kotlin/com/six2dez/burp/aiagent/redact/Redaction.kt` — looping capped boundary extension, `MAX_JSON_BOUNDARY_LOOKAHEAD_LINES`, `isJsonPairBoundaryContinuation`, `pairMayBeInFlightAt`, corrected D-01 comment
- `src/test/kotlin/com/six2dez/burp/aiagent/redact/RedactionTest.kt` — the two sweeps, the `boundarySweepBody` fixture builder, and three file-level sweep constants
- `DECISIONS.md` — ADR-14 residual + consequences
- `.planning/codebase/CONCERNS.md` — the body-stage bounds entry

## Fixture-reachability argument for each new test

The phase's recurring failure mode is a test that passes against a mutation which unwires the rule it exists to guard, because the fixture is also caught by a different rule. Both new fixtures are therefore reachable by `jsonSecretKeyRegex` and by **nothing else in either stage**:

- **No `=` anywhere in the pair.** `formBodyParamRegex` requires a `key=` shape; `urlTokenParamRegex` requires that *and* a leading `?` or `&`. Neither can reach the value. This matters specifically because `urlTokenParamRegex` runs **unbounded in the header stage** and would otherwise mask the defect entirely.
- **Not `Bearer`- or `Basic`-prefixed, does not begin `eyJ`.** `bearerRegex`, `basicAuthRegex` and `jwtRegex` cannot match.
- **No `Cookie:`/`Set-Cookie:` header, no `=== COOKIES ===` section, no ` (COOKIE)` type suffix.** Neither cookie rule can reach it — including 21-08's section rule.
- **No custom pattern registered**, and `@AfterEach resetCustomPatterns` guarantees no bleed from `oversizeBodyFailsClosed` / `subWindowBodyFailsClosed`, which both install `(a+)+$`.
- **Filler is `y` and `z` only**, so nothing in the padding can produce `[REDACTED]` and create a false positive on the surviving-key assertion.

Verified by mutation (M1-M3), not by inspection.

## Decisions Made

**1. The cap fails to a recorded residual, not to a dropped window.**

The executor brief asked for the cap to "fail closed (drop behind the marker)". The plan's own threat register assigns T-21-31 the disposition **`accept`**, and Task 3 explicitly requires *recording* the residual — instructions that are incoherent if the window is dropped at the cap. Resolved in favour of the plan, for reasons that are worth stating because they are not merely deference:

- **Dropping would be the larger harm.** The cap's trigger is a content heuristic: 8+ consecutive lines that are blank or end in `:`/`"`. Multi-line HTML attributes (`<div class="a"` / `id="b"` / …) and nested YAML (`key:` / `sub:` / …) both satisfy it on entirely benign input. Dropping would destroy up to a megabyte of analytic context — the exact harm `T-21-06` warns about — in exchange for a narrow false negative.
- **The cap is not fail-open in D-02's sense.** The extension only ever *moves a boundary*. Every byte still lands in exactly one window and is still scanned; nothing is skipped and nothing is emitted unscanned. Any window the extension grows beyond what will scan in time is still dropped behind a marker by `scanWindow`/`dropOrRetry`. What remains at the cap is a **rule false negative across a cut** — the same class as the long-accepted custom-pattern residual, not the class PRIV-06 exists to remove.
- **The exploitable shape is narrow.** For `jsonSecretKeyRegex` to span N lines the text between key and value must match `\s*:\s*`, so the only lines that can sit between them are a lone colon and whitespace. Nine lines of lookahead covers every realistic pretty-printer.

Both the reasoning and the rejected alternative are recorded in the source comment on `MAX_JSON_BOUNDARY_LOOKAHEAD_LINES` and in ADR-14, so the choice is auditable rather than implicit.

**2. `21-CONTEXT.md`'s locked D-01 AMENDED text left untouched**, per the plan. Its *mechanism* — cut at line boundaries, no overlap — is what shipped and is correct. What was over-claimed was the evidence sentence, and the three places that had to be right are the code comment, the ADR and `CONCERNS.md`. A later reader should not "fix" the locked decision.

**3. No overlap mechanism reintroduced.** Verified by criterion (`grep -ci overlap` outside comments returns 0). The bounded lookahead moves the boundary, keeping windows disjoint and line-aligned, rather than duplicating a region into two windows — stated in the comment so the distinction is not lost.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 — Bug] The plan's specified predicate split does not satisfy the plan's own test**

- **Found during:** Task 2 (GREEN gate)
- **Issue:** The plan specified the initial boundary test as `isJsonPairBoundaryRisk` (which deliberately does not fire on a blank line) and the loop test as `isJsonPairBoundaryContinuation`. Implemented exactly as written, `jsonPairWithBlankLineBetweenKeyAndValueIsRedacted` **still failed at shift=0**. The cause: the window cut can land *on the blank line that is itself inside the pair* (`"token"` / `:` / blank / **cut** / `"value"`). The initial test then examines the blank line, declines to start an extension, and the pair is cut in half exactly as before. The plan's own `must_haves` truth — "a blank line continues the risk rather than ending it" — is violated by the plan's literal algorithm.
- **Fix:** Added `pairMayBeInFlightAt`, which walks **backward** over blank lines — bounded by the same cap — to the nearest line carrying content and lets that line decide. This preserves the asymmetry rationale the plan gave: a window merely ending on a blank line still starts no extension unless something risky precedes it.
- **Files modified:** `src/main/kotlin/com/six2dez/burp/aiagent/redact/Redaction.kt`
- **Verification:** M3 mutation — removing the backward walk reproduces the shift=0 failure and nothing else.
- **Committed in:** `3d62891`

**2. [Rule 3 — Blocking] detekt `LoopWithTooManyJumpStatements` on the new backward walk**

- **Found during:** Task 2
- **Issue:** The first form of `pairMayBeInFlightAt` used two `break` statements; detekt failed the build with 1 weighted issue. QUAL-07 forbids growing `detekt-baseline.xml`, so baselining was not an option.
- **Fix:** Restructured into a single `while` condition carrying all three exit tests, with one `return`. Zero jumps, and the result reads better than the original.
- **Files modified:** `src/main/kotlin/com/six2dez/burp/aiagent/redact/Redaction.kt`
- **Verification:** `ktlintCheck detekt` clean; `git diff --stat -- detekt-baseline.xml` empty.
- **Committed in:** `3d62891`

---

**Total deviations:** 2 auto-fixed (1 × Rule 1, 1 × Rule 3)
**Impact on plan:** Both were necessary for correctness. Deviation 1 is the substantive one — without it, the plan's second must_have would have shipped unmet while its own test failed. No scope creep; both changes are inside `windowEnd`'s helper set and add no dependency.

## Issues Encountered

**1. Grep-criterion annotation drift (benign, threshold still satisfied).**

Task 1's criterion reads: `grep -v '^\s*//' RedactionTest.kt | grep -c 'REDACTION INCOMPLETE'` is **at least 4** *(2 pre-existing, 2 new)*. The actual filtered count is **6**. The threshold holds; the parenthetical was wrong — there are **4** pre-existing occurrences, not 2, because plan 21-08 added two more (`RedactionTest.kt:890` and `:923`, the cookie-section budget guards) after this plan was written. Nothing was weakened or deleted to make a number match. The plan's literal `\s` form also works under macOS BSD grep, so no portability workaround was needed for the recorded criterion.

**2. The anti-vacuity assertion is inherently timing-sensitive. Observed once, not reproduced in five subsequent full-suite runs.**

During the first post-fix run of the full `redact.*` suite — while the blank-line bug was still present, so the sweep was executing ~19 iterations for the first time — `windowedScanRedactsJsonPairAcrossEveryBoundaryAlignment` failed at **shift=18** on the *drop-marker* assertion, not the security assertion. The secret was absent; a `REDACTION INCOMPLETE`/`BUDGET EXCEEDED` marker was present.

Root cause is structural rather than a defect in the fix: `SafeRegex` enforces its deadline through `DeadlineCharSequence.get()`, a **wall-clock** `System.nanoTime()` check on every character access. That method is project code, so JaCoCo instruments it — one probe per character over a 1 MB window, on top of ~400 MB of allocation churn across 48 applies. Measured headroom at 1 MB is roughly 12 ms against a 50 ms deadline, and `dropOrRetry` halves twice before dropping, so a marker requires three consecutive timeouts over the same sub-range — reachable only under a long GC pause. This is Pitfall 9 ("assuming the 50 ms deadline has generous headroom") surfacing in a test rather than in production.

**Not weakened, deliberately.** The assertion is the difference between a real invariant test and one that passes for the wrong reason, and any assertion that proves *successful* redaction on a 1 MB fixture carries the same exposure — `oversizeBodySecretDoesNotSurvive`, a shipped SC6 canary, already does. Stability after the complete fix: **5 consecutive green full-`redact` runs plus 2 green full-suite runs**, with no recurrence. Flagged here so that if it ever reappears in CI it is diagnosed as deadline pressure under instrumentation, not as a CR-02 regression.

## Verification

| Check | Result |
|---|---|
| `./gradlew test` (full suite) | **BUILD SUCCESSFUL** |
| `./gradlew test --tests "com.six2dez.burp.aiagent.redact.*"` | **BUILD SUCCESSFUL** (97 tests) |
| `./gradlew ktlintCheck detekt` | **BUILD SUCCESSFUL** |
| `git diff --stat 2a4e711 HEAD -- detekt-baseline.xml` | **empty** — byte-identical (QUAL-07) |
| SC6 canaries (all 10 named) | green, including both deliberate SC6 inversions, untouched |
| `git diff -U0 -- Redaction.kt \| grep -c 'WINDOW_RETRY_MAX_DEPTH'` | 0 — CR-04's symbols untouched, as 21-11 requires |
| `git diff -U0 -- Redaction.kt \| grep -cE '^[+-].*(hkdf\|HKDF\|anonymizeHost)'` | 0 — HKDF/SC6 RFC 5869 vector untouched |
| `grep -c 'proven byte-identical to whole-document processing' Redaction.kt` | 0 |
| `grep -v '^\s*//' Redaction.kt \| grep -ci 'overlap'` | 0 |
| `git diff --stat 21-CONTEXT.md` | empty — locked decision untouched |
| STATE.md / ROADMAP.md / REQUIREMENTS.md | **not modified** — orchestrator owns those writes |

Every Gradle invocation used `JAVA_HOME=$(/usr/libexec/java_home -v 21)`.

## Known Stubs

None. No hardcoded empty values, placeholder text or unwired components were introduced.

## Threat Flags

None. No new network endpoint, auth path, file access pattern or schema change at a trust boundary. The change is confined to a pure boundary-index function and its two predicates; `Redaction` remains a stateless `object` and every piece of the new loop state is local (T-21-23).

## Next Phase Readiness

- **Ready for plan 21-11 (CR-04).** `splitPoint` and `WINDOW_RETRY_MAX_DEPTH` were deliberately not touched, verified by criterion, so the later wave's edits to the same file will not conflict.
- **Disjoint from plan 21-10**, which ran concurrently in `scanner/`. No shared file.
- **Carried forward:** the narrowed built-in residual (pairs spread over more than eight lines) is now recorded in ADR-14 and `CONCERNS.md`, with the fix approach noting that `MAX_JSON_BOUNDARY_LOOKAHEAD_LINES` is a dial, not a fix.
- **For the verifier:** the RED/GREEN gate commits are `b5d1480` (test) → `3d62891` (fix), in that order.

---
*Phase: 21-redaction-completeness*
*Completed: 2026-08-12*
