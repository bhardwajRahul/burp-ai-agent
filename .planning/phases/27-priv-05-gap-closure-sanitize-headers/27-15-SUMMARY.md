---
phase: 27-priv-05-gap-closure-sanitize-headers
plan: 15
subsystem: testing
tags: [redaction, sweep, regex, kotlin, tripwire, self-scan, static-analysis]

requires:
  - phase: 27-priv-05-gap-closure-sanitize-headers
    provides: "the RedactingPolicySurvivalSweepTest this plan repairs (27-12), and 27-14's landed tests whose counts this plan re-measures rather than inherits"
provides:
  - "FUNCTION_DECLARATION widened to both name spellings, any modifier prefix, an optional same-line annotation and an optional generic parameter list — 133 previously invisible declaration lines are now scanned"
  - "the identifier taken from the plain-name group falling back to the backtick group, floored by an identifier-set assertion and not only a count"
  - "a six-shape declaration non-vacuity gate: 1 of 6 before, 6 of 6 after"
  - "the fileWalk -> detect composition gated in the FLAGGING direction for the first time — the sweep's only production path"
  - "the unbalanced-file blindness converted from a silently blanked tail into a named AssertionError"
  - "STATED_BLIND_AXES = 13, machine-checked against the class KDoc's own enumeration by a source-read test"
  - "26-SECURITY.md clause (vi) amended IN PLACE in the same change as the control it describes, with every quoted count re-measured"
affects: [27-16, phase-28]

actuals:
  tokens: 60259
  tasks: 3
  commits: 5

tech-stack:
  added: []
  patterns:
    - "source-read count pin: a constant stating a bound is asserted against the prose enumeration it describes, read out of the file's own source at test time, so a hand-transcribed number in a register can go stale only if a test goes red first"
    - "three-direction gate on a composition: assert the shipped behaviour, then neutralise the component in BOTH directions and record all three counts — a test that has only ever produced its expected value has measured nothing"
    - "loud failure over silent narrowing: a scan whose input preprocessing can silently empty its own input throws naming the source rather than returning a blanked tail"

key-files:
  created: []
  modified:
    - src/test/kotlin/com/six2dez/burp/aiagent/redact/RedactingPolicySurvivalSweepTest.kt
    - .planning/phases/26-coverage-static-analysis-debt-docs/26-SECURITY.md

key-decisions:
  - "Use 27-REVIEW-2 CR-01's regex verbatim rather than re-deriving it. Two independent measurements (CR-01 and 27-VERIFICATION-4 gap 2) produced the same pattern; re-deriving would have added a third opinion and no evidence."
  - "Close the declaration-shape axis rather than enumerate it, so the enumeration lists no closed axis — but name the axis the widening CREATES in the same change, with its population measured rather than assumed."
  - "State axis 9 as the shape whose opening parenthesis does not follow the identifier on its declaration line, covering BOTH the extension receiver (3 measured) and the multi-line signature (0 measured), rather than the plan's multi-line-only wording. The measurement showed the live residual is extension receivers, not multi-line signatures."
  - "Do NOT fix the compound-assertion negation over-fire (axis 10). Write the fix down instead. The negation rule is load-bearing for 1 measured live hit and changing it without its own flip-pair fixture is how a detector gets quietly disarmed."
  - "Report the raw 36-occurrence count alongside the 9, separating the 27 assertFalse containments. The assertTrue requirement is not one of the three exclusions, and folding those into the 9 would overstate the exclusions' cost fourfold."
  - "Record the 625-vs-652 blanked-line divergence as UNRECONCILED rather than explaining it away. The trees differ (27-14 plus this plan's three fixtures) and the direction that matters — 0 files ending INSIDE — agrees in both."

patterns-established:
  - "Re-measure, never carry forward: every count restated in this plan was taken against the tree with 27-14 landed, and each divergence from a previously written number is recorded with BOTH values and the reason, never narrowed to make an old number true."

requirements-completed: []

coverage:
  - id: D1
    description: "A survival pin written with a backtick-quoted name, a same-line annotation, or a suspend/public/override modifier is FLAGGED — the declaration gate now covers the 3.8% of live test methods that were structurally invisible"
    requirement: "PRIV-05"
    verification:
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/redact/RedactingPolicySurvivalSweepTest.kt#everyDeclarationShapeInUseInThisRepositoryIsVisibleToTheSweep"
        status: pass
      - kind: other
        ref: "ad-hoc JDK 21 probe driving the shipped detector over DECLARATION_SHAPE_FIXTURE, before and after; both columns recorded below"
        status: pass
    human_judgment: false
  - id: D2
    description: "The widening did not corrupt what the detector says about the real artifacts it was proven on — the pre-round files at 09e9cae still report EXACTLY 3 hits under the same three identifiers, and the current tree still reports 0 qualified"
    requirement: "PRIV-05"
    verification:
      - kind: other
        ref: "ad-hoc JDK 21 probe over `git show 09e9cae` copies of McpToolHelpersTest.kt and CookieHeaderNameParityTest.kt through the WIDENED detector; identifiers checked, not only counts"
        status: pass
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/redact/RedactingPolicySurvivalSweepTest.kt#noGreenTestAssertsASensitiveValueSurvivesARedactingPolicy"
        status: pass
    human_judgment: false
  - id: D3
    description: "The fileWalk -> detect composition is gated in the FLAGGING direction: if the walk ever starts blanking real code, a test goes red instead of the tree scan going quietly empty"
    requirement: "PRIV-05"
    verification:
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/redact/RedactingPolicySurvivalSweepTest.kt#theWalkPreservesRealCodeWhileSkippingRawStringInteriors"
        status: pass
      - kind: other
        ref: "two-direction neutralisation of dropRawStringInteriors: pass-through -> 2 hits, blank-everything -> 0 hits, shipped -> 1 hit; all three recorded below"
        status: pass
    human_judgment: false
  - id: D4
    description: "A scanned file that ends INSIDE a raw string raises a named AssertionError instead of returning a silently blanked tail"
    requirement: "PRIV-05"
    verification:
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/redact/RedactingPolicySurvivalSweepTest.kt#theWalkFailsLoudlyWhenAFileEndsInsideARawString"
        status: pass
    human_judgment: false
  - id: D5
    description: "The stated blind-axis count is machine-checked against the enumeration it describes, and 26-SECURITY.md clause (vi) cites the same number, amended in the same change"
    requirement: "PRIV-05"
    verification:
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/redact/RedactingPolicySurvivalSweepTest.kt#theStatedBlindAxisCountMatchesTheEnumeration"
        status: pass
      - kind: other
        ref: "deliberate off-by-one probe: STATED_BLIND_AXES = 12 against a 13-entry enumeration; failure message recorded below"
        status: pass
    human_judgment: false
  - id: D6
    description: "Round 5 closed NO requirement — REQUIREMENTS.md is untouched, PRIV-05 stays unchecked, AR-27-04's disposition is not relitigated, and the register counter is left to plan 27-16"
    verification: []
    human_judgment: true
    rationale: "A deliberate non-action. No test can assert that a disposition was not relitigated; it is verified by reading the diff, which is quoted below, and confirmed by a maintainer."

duration: 34min
completed: 2026-08-26
status: complete
---

# Phase 27 Plan 15: Close the sweep's own stated-bound defect Summary

**The artifact written to stop "a stated bound wider than its control" carried one itself: its declaration gate was blind to 133 of 1781 declaration lines, its only production path had no positive gate, and the register inherited the claim — all three closed, with every count re-measured and the axis count now machine-checked.**

## Performance

- **Duration:** 34 min
- **Started:** 2026-08-26T13:40Z (approx, first task edit)
- **Completed:** 2026-08-26T14:14Z
- **Tasks:** 3 of 3
- **Files modified:** 2

## Accomplishments

- **Gap 2 closed at the gate.** `FUNCTION_DECLARATION` now admits any modifier prefix, an optional same-line annotation, an optional generic parameter list and BOTH name spellings. A synthetic survival pin in six declaration shapes went from **1 of 6** to **6 of 6**.
- **CR-02 closed.** The `fileWalk` → `detect` composition is gated in the flagging direction, and the unbalanced-file case is a named error rather than a silently blanked tail.
- **Gap 3 closed at both ends.** The enumeration is honest (13 axes, the closed one removed, two open ones added with their costs), the count is source-pinned, and `26-SECURITY.md` clause (vi) cites the same number, amended in the same change.
- **Round 5 closed no requirement,** and this SUMMARY says so.

## Task Commits

1. **Task 1: Widen the declaration gate** — `db7925f` (test, RED) → `915b2fa` (fix, GREEN)
2. **Task 2: Bind the walk to the detector, make the blindness loud** — `cff00b7` (test, RED) → `38081c8` (fix, GREEN)
3. **Task 3: Restate the bound where it is made and where it is inherited** — `b3be819` (docs)

## Files Created/Modified

- `src/test/kotlin/com/six2dez/burp/aiagent/redact/RedactingPolicySurvivalSweepTest.kt` — widened declaration gate, corrected identifier extraction, three new fixtures, four new tests, amended enumeration, source-read count pin. 11 tests → **15 tests**, all green.
- `.planning/phases/26-coverage-static-analysis-debt-docs/26-SECURITY.md` — standing-rule clause (vi) amended in place: ELEVEN → THIRTEEN, the admission, the re-measured arithmetic, the dated round-5 paragraph. 96 insertions, 21 deletions, **all three hunks inside the clause (vi) region**.

---

## MEASUREMENT 1 — the six declaration shapes, before and after

Command (both columns): `JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test --tests 'com.six2dez.burp.aiagent.redact.RedactingPolicySurvivalSweepTest'`, driving `detect(FIXTURE_ID, DECLARATION_SHAPE_FIXTURE.lines())`.

| # | Declaration shape | BEFORE | AFTER |
|---|---|---|---|
| 1 | `` fun `a backtick quoted name`() `` | not flagged | **flagged** |
| 2 | `@Test fun anAnnotationAndFunOnTheSameLine()` | not flagged | **flagged** |
| 3 | `suspend fun aSuspendModifierPin()` | not flagged | **flagged** |
| 4 | `public fun aPublicModifierPin()` | not flagged | **flagged** |
| 5 | `override fun anOverrideModifierPin()` | not flagged | **flagged** |
| 6 | `fun aPlainDeclarationControl()` — the control | flagged | **flagged** |

**BEFORE — 1 of 6**, verbatim from the RED run at `db7925f`:

```
Hits: [<fixture>#aPlainDeclarationControl -> "sentinelplainshape" (vocabulary entry 0)]
  ==> expected: <6> but was: <1>
```

**AFTER — 6 of 6**, verbatim from the probe at `915b2fa`:

```
  6 of 6
     <fixture>#a backtick quoted name -> "sentinelbacktickshape" (vocabulary entry 0)
     <fixture>#anAnnotationAndFunOnTheSameLine -> "sentinelannotationshape" (vocabulary entry 0)
     <fixture>#aSuspendModifierPin -> "sentinelsuspendshape" (vocabulary entry 0)
     <fixture>#aPublicModifierPin -> "sentinelpublicshape" (vocabulary entry 0)
     <fixture>#anOverrideModifierPin -> "sentineloverrideshape" (vocabulary entry 0)
     <fixture>#aPlainDeclarationControl -> "sentinelplainshape" (vocabulary entry 0)
```

The identifiers are non-empty for BOTH spellings, which is the T-27-15-06 check: the backtick shape reports from group 2 and the five plain shapes from group 1.

## MEASUREMENT 2 — the population that gate stands in for, RE-MEASURED

Command: a Python re-implementation of the two regexes over `src/test/kotlin` (script retained in the run scratchpad; the population regex is quoted in the sweep's own KDoc).

| Quantity | MEASURED this round | Previously written | Reconciliation |
|---|---|---|---|
| `.kt` files under `src/test/kotlin` | **151** | 151 | agrees |
| declaration lines (paren on the line) | **1781** | 1779 (`27-REVIEW-2` CR-01) | +2 — plan 27-14's new tests |
| invisible to the shipped narrow regex | **133** | 136 (CR-01) | see below |
| declaration lines (paren-optional population) | **1784** | — | — |
| invisible on that wider population | **136** | 136 (CR-01) | **agrees exactly** |
| backtick-named `@Test` methods | **67** across **9** files | 67 across 9 (CR-01) | agrees |
| `override fun` declarations | **61** | not previously stated | new |

**The 133-vs-136 divergence is fully explained and is NOT a disagreement about the tree.** CR-01's population includes 3 **extension-receiver** declarations — `private fun String.isRecurringSchedule()` in `util/SchedulerGuardCoverageTest.kt`, and `private fun String.indentWidth()` in both `redact/RedactingPolicySurvivalSweepTest.kt` and `redact/LogicalLineBoundaryScopeTest.kt`. My narrower population requires the opening parenthesis to follow the captured identifier, which those three do not. Both numbers are recorded, neither is reconciled away, and **the 3 are a finding in their own right**: they remain invisible AFTER the widening, because the widened regex also requires the parenthesis to follow the identifier. They are named as blind axis 9.

Breakdown of the 133: **67** backtick names + **61** `override fun` + **5** generic-parameter declarations.

## MEASUREMENT 3 — the historical regression probe, through the WIDENED detector

Corpus: `git show 09e9cae:<path>` for both pre-round files, written to a scratch directory and walked by the shipped `fileWalk` → `detect`.

```
  CookieHeaderNameParityTest.kt: 1 hits
     CookieHeaderNameParityTest.kt#thePredicateIsDeliberatelyWiderThanTheTwoRegexes -> sentinel (vocabulary entry 2)
  McpToolHelpersTest.kt: 2 hits
     McpToolHelpersTest.kt#cookieVariantsAreStrippedEndToEndThroughRedactIfNeeded -> "api.example.com" (vocabulary entry 3)
     McpToolHelpersTest.kt#cookieVariantsAreStrippedEndToEndThroughRedactIfNeeded -> "api.example.com" (vocabulary entry 3)
```

**EXACTLY 3 hits**, and the three identifiers are the same strings `27-12-SUMMARY.md` recorded: the underscore pin plus the two host pins. **Identifiers were checked, not only the count** — a count that matched while identifiers were empty would have been the T-27-15-06 failure, and it is the specific way this widening could have corrupted output silently.

## MEASUREMENT 4 — the current tree, qualified and unqualified

| Quantity | MEASURED | Previously written | Note |
|---|---|---|---|
| qualified hits (three exclusions ON) | **0** | 0 | unchanged BY the widening |
| unqualified, three exclusions OFF | **9** | 9 | unchanged BY the widening |
| — of which benign-control | **7** | 7 | 7 distinct live functions, all in `SerializedEmissionRedactionTest` |
| — of which position-rule | **1** | 1 | — |
| — of which negation-rule | **1** | 1 | — |
| RAW occurrences over the same population | **36** | not previously stated | of which **27** are `assertFalse` |

**The equality is the load-bearing part.** Widening the gate to 133 more declaration lines surfaced **nothing new**: every one of the 9 is a legitimate shape, and the classifier reported **zero** `REAL-OR-UNCLASSIFIED` entries. Nothing was narrowed to keep the set empty — `ALLOWLIST` is still `emptyMap()`, `BENIGN_ACCESSORS` still holds exactly one key, no vocabulary entry was narrowed, and no self-file exclusion exists.

The 36/27 split is recorded because the 9 is easy to misread: `assertsPresenceAt` bundles the `assertTrue` REQUIREMENT with the NEGATION RULE, and disabling it wholesale sweeps in every absence assertion in the repository. The `assertTrue` requirement is not one of the three exclusions; folding those 27 into the 9 would overstate the exclusions' cost fourfold.

**Benign-accessor count re-measured after 27-14, as the wave-8 handoff required: still 7.** 27-14's new tests add none — its non-vacuity control is an `assertFalse`. The 7 live functions are `aCanonicalCookieAtTheOpenOfAJsonStringDoesNotSurviveBalanced`, `…Strict`, `bearerShapedAuthorizationIsStillRedactedAndTheHeaderNameSurvives`, `everyCookieNameVariantCarriesADistinctSentinelAndNoneSurvives`, `headerNameAndBenignControlSurviveTheSerializedShape`, `issueDetailsCarrierStripsCookiesInBothRedactingModes`, `siteMapEntryCarrierStripsCookiesInBothRedactingModes`.

## MEASUREMENT 5 — the composition gate, all three directions

| `dropRawStringInteriors` state | Hits | Interpretation |
|---|---|---|
| **shipped** | **1** — `aRealCodePinTheWalkMustPreserve` | correct: fixture blanked, real code preserved |
| neutralised to pass-through | **2** | the raw-string skip has stopped working |
| neutralised to blank-everything | **0** | the walk has started blanking real code |

Pass-through run, verbatim:

```
Hits: [<fixture>#aPinThatLivesInsideTheRawString -> "sentinelinsidetherawstring" (vocabulary entry 0),
       <fixture>#aRealCodePinTheWalkMustPreserve -> "sentinelrealcodepin" (vocabulary entry 0)]
  ==> expected: <1> but was: <2>
```

Blank-everything run, verbatim: `Hits: [] ==> expected: <1> but was: <0>`.

**THE FINDING IN THAT THIRD ROW, which is the reason this task existed.** With the walk blanking everything, **`theWalkPreservesRealCodeWhileSkippingRawStringInteriors` was the ONLY failing test in the class — the other 13 stayed green**, including the tree scan, the self-scan, and `theTreeWalkIsNonVacuous`. That is the silently-vacuous pass in full: the sweep would have reported the entire repository clean while seeing nothing at all, and before this plan nothing in the class could tell.

## MEASUREMENT 6 — the loud failure, and the walk tree-wide

RED, on the pre-task tree (`cff00b7`), verbatim: `Expected java.lang.AssertionError to be thrown, but nothing was thrown.` GREEN after `38081c8`, with the message asserted to name the source identifier.

| Quantity | MEASURED | Previously written | Reconciliation |
|---|---|---|---|
| files walked with no throw | **151 of 151** | — | so **0 files end INSIDE** |
| files ending INSIDE | **0** | 0 (`27-REVIEW-2` CR-02) | agrees |
| blanked lines tree-wide | **652** | 625 (CR-02) | **NOT reconciled** — see below |
| — of which in the sweep file itself | **352** | — | its own fixtures |
| — of which elsewhere | **300** | — | — |

The 0-files-INSIDE figure is now established **by the tree scan itself** rather than by a one-off probe: `noGreenTestAssertsASensitiveValueSurvivesARedactingPolicy` walks all 151 files and would throw. The 652-vs-625 difference is **recorded and deliberately not reconciled**: CR-02 measured with an independent re-implementation on the round-4 tree, which has since gained plan 27-14's tests and this plan's three fixtures, and the direction that matters agrees in both. Narrowing anything to make 625 true again is exactly what this plan prohibits.

## MEASUREMENT 7 — the axis count, and the deliberate off-by-one

RED probe with `STATED_BLIND_AXES = 12` against the 13-entry enumeration, verbatim:

```
the class KDoc states its bound as 12 blind axes and enumerates 13. …
  ==> expected: <12> but was: <13>
```

Set to the measured value, the suite is green. **The number is the same in all three places, verified by reading all three:**

| Location | Value |
|---|---|
| `RedactingPolicySurvivalSweepTest` class KDoc — "THIRTEEN axes" | 13 |
| `RedactingPolicySurvivalSweepTest.STATED_BLIND_AXES` (`:787`) | 13 |
| `26-SECURITY.md` clause (vi) — "names **THIRTEEN** things it cannot see" | 13 |

The enumeration contains **no** entry describing the declaration-shape blindness (task 1 closed it), and **one each** for the two open axes, both stated as costs:

- **Axis 9** — a declaration whose opening parenthesis does not follow the identifier on its line. Two shapes, both measured: extension receiver (**3** on this tree, one of them in the sweep file itself), multi-line signature (**0** today). Created by task 1's widening and named in the same change.
- **Axis 10** — the compound-assertion negation over-fire, with the fix written down (scope the negation test to the operand immediately preceding the call, bounded by the nearest `&&`, `||` or comma) and **deliberately not applied this round**.

## MEASUREMENT 8 — other counts restated, each re-measured

| Quantity | MEASURED | Previously written | Cause of movement |
|---|---|---|---|
| tests in the class | **15** | 11 | +4 this plan |
| unskipped self-hits (no walk) | **14** | 5 | 5 + 6 shape pins + 2 composition halves + 1 unbalanced pin |
| `dropRawStringInteriors(` call sites | **3**, all passing a source id | — | no overload omits it |
| `ALLOWLIST` | `emptyMap()` | `emptyMap()` | unchanged |
| `BENIGN_ACCESSORS` | 1 key | 1 key | unchanged |

## Decisions Made

See `key-decisions` in the frontmatter. The two worth restating here:

1. **Axis 9 is stated as the parenthesis-position shape, not as "multi-line signature" alone.** The plan's wording anticipated multi-line signatures; the measurement found the live residual is 3 extension receivers and 0 multi-line signatures. The axis is written at what was observed, with both shapes and both counts, rather than at what was projected.
2. **The negation over-fire is named, not fixed.** Touching `assertsPresenceAt` without its own flip-pair fixture is the failure mode `theBenignExclusionCannotSwallowARealSentinel` exists to prevent for the other exclusion.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Two stated measurements in the sweep went false as a direct result of this plan's own fixtures**
- **Found during:** Tasks 1 and 2
- **Issue:** `MIN_EXPECTED_UNSKIPPED_SELF_HITS`'s comment stated "MEASURED … 5 unskipped", and the register's clause (vi) repeated it as "returns 5". Adding three fixtures moved the real figure to 14. Leaving them would have committed, in this plan, the exact defect this plan exists to repair.
- **Fix:** Both restated to **14** with the movement itemised (5 + 6 + 2 + 1), in the same commits that caused it.
- **Files modified:** `RedactingPolicySurvivalSweepTest.kt`, `26-SECURITY.md`
- **Committed in:** `915b2fa`, `38081c8`, `b3be819`

**2. [Rule 1 - Bug] A false statement in the walk's own comment**
- **Found during:** Task 2
- **Issue:** The comment read "the class KDoc above quotes a bare triple quote". The bare triple quote is in `fileWalk`'s KDoc, not the class KDoc.
- **Fix:** "the class KDoc above" → "a KDoc above". One word, but a false statement inside the file whose subject is claim/control alignment.
- **Committed in:** `38081c8`

**3. [Rule 1 - Bug] A stale cross-reference created by the renumbering**
- **Found during:** Task 3
- **Issue:** `fileWalk`'s KDoc said the raw-string skip "is declared in the class KDoc as blind axis 9". The renumbering moved it to 11.
- **Fix:** Updated to axis 11.
- **Committed in:** `b3be819`

---

**Total deviations:** 3 auto-fixed (3 × Rule 1). **Impact on plan:** All three are stale-measurement corrections of exactly the class this plan exists to eliminate. No scope creep; no new behaviour.

## Issues Encountered

**One full-suite failure, confirmed to be the known flake, not a regression.** `./gradlew test` reported `1245 tests completed, 1 failed`: `RedactionTest > windowedScanRedactsJsonPairWhoseValueStraddlesTheCut`. Re-run alone, `RedactionTest` is **46/46 green, 0 failures, 0 errors**. This is the documented `SafeRegex` 50 ms wall-clock deadline flake under CPU load. It is also structurally unrelated: this plan changed **one test file** and no production source.

## Threat Flags

| Threat ID | Category | Disposition | Outcome this plan |
|---|---|---|---|
| T-27-15-01 | Information Disclosure | mitigate | **CLOSED.** Regex widened; six-shape gate 1/6 → 6/6; historical 3-hit re-run holds. |
| T-27-15-02 | Repudiation | mitigate | **CLOSED.** Clause (vi) amended in the same change, every count re-measured, the count now source-pinned. |
| T-27-15-03 | Tampering | mitigate | **CLOSED.** Composition gated 1/2/0; unbalanced file now a named error; 0 of 151 files end INSIDE. |
| T-27-15-04 | Information Disclosure | accept | **ACCEPTED AND NAMED** as axis 10, with its fix written down and deliberately not applied. |
| T-27-15-05 | Information Disclosure | accept | **ACCEPTED AND NAMED** as axis 9 — and WIDENED against the plan's projection: the live residual is 3 extension receivers, not the multi-line signatures the plan anticipated (0 measured). |
| T-27-15-06 | Spoofing | mitigate | **CLOSED.** Identifier taken from group 1 falling back to group 2; floored by `DECLARATION_SHAPE_IDENTIFIERS`, and the historical probe checked identifiers rather than only the count. |
| T-27-15-SC | Tampering | accept | **EMPTY POPULATION.** No package-manager dependency added; the Gradle dependency set is byte-unchanged. |

**No new threat surface.** This plan changed one test file and one planning document. No production source, no network path, no schema, no dependency.

## What this plan deliberately did NOT do

- **`REQUIREMENTS.md` shows ZERO changes** across `5f5aeab..HEAD`. PRIV-05 stays `[ ]`. Round 5 closes no requirement.
- **`threats_open` is unchanged at 0**, the `awk` command and its population paragraph are untouched, and **0 lines of any Threat Register row or Accepted Risks Log row were changed** (`git diff … | grep -cE '^[-+].*AR-27-0[0-9] \|'` → `0`). All register-counter work belongs to plan 27-16.
- **AR-27-04's disposition is not relitigated.**
- **`THIRD_OPEN_FINDING` / `AR-27-11` register work is not added** — reserved for plan 27-16 per the wave-8 handoff.

## Next Phase Readiness

Plan 27-16 owns the register counter recomputation and the `AR-27-11` row. It should note that clause (vi) has been amended by this plan and that its axis count is now enforced by `theStatedBlindAxisCountMatchesTheEnumeration` — changing the sweep's enumeration without updating `STATED_BLIND_AXES` will turn the suite red, which is the intended behaviour.

Two open axes are carried forward with owners: axis 9 (3 extension-receiver declarations invisible) and axis 10 (compound-assertion negation over-fire, fix written down). Neither is a new leak; both are bounds on the sweep.

---
*Phase: 27-priv-05-gap-closure-sanitize-headers*
*Completed: 2026-08-26*
