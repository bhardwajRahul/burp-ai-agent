---
phase: 28-the-issue-detail-cookie-carrier-auditissue-detail-scanner-is
plan: 07
subsystem: security
tags: [kotlin, burp, montoya, redaction, scanner, privacy, kdoc, junit5, mockito]

requires:
  - phase: 28-05
    provides: "route 2's write-site control (`sanitizeCookiePointText`, `isCookieInsertionPoint`) and `AiScanCheckDetailCookieCarrierTest`, the file this plan makes honest"
  - phase: 28-06
    provides: "the round-3 measured baseline this plan's `<measured_facts>` were taken against"
provides:
  - "`PRIVACY_MODE_TOOLTIP` — the privacy-mode selector's copy names the write-time/read-time bound in operator language, pinned by a committed test"
  - "The canonical `WRITE-TIME/READ-TIME BOUND` token at `AiScanCheck.consolidateIssues`, with the three-hop reason `KEEP_EXISTING` makes an OFF-built issue sticky"
  - "The route-2 fail-open set (`HEADER`, `USER_PROVIDED`, `EXTENSION_PROVIDED`, `UNKNOWN`) named at the gate with all four reasons the predicate was NOT widened"
  - "Two residual pins: `theRouteTwoGateIsFailOpenForTheseCookieCapableTypes` and a 17-member enum-population tripwire"
  - "Four named assertions over route 2's `**Payload Used:**` line, making the probe claim two records already made TRUE of the file"
  - "The `javap` measurement replacing the false `type()`-returns-null KDoc premise"
affects: [28-08, phase-29, issue-detail-carrier-disposition, 26-SECURITY]

actuals:
  tokens: 9847
  tasks: 3
  commits: 3

tech-stack:
  added: []
  patterns:
    - "Residual-pinning test: a GREEN run records a residual's width and says so in its own assertion messages, so it cannot be mistaken for a control"
    - "Enum-population tripwire: pin a third-party enum's member count and name set so an API bump turns a residual RED instead of widening it silently"
    - "Diff gates pinned to a captured BASE commit rather than the working tree, so per-task commits cannot make the gate measure nothing"

key-files:
  created:
    - src/test/kotlin/com/six2dez/burp/aiagent/ui/PrivacyModeTooltipBoundTest.kt
  modified:
    - src/main/kotlin/com/six2dez/burp/aiagent/ui/SettingsPanelInit.kt
    - src/main/kotlin/com/six2dez/burp/aiagent/scanner/AiScanCheck.kt
    - src/test/kotlin/com/six2dez/burp/aiagent/scanner/AiScanCheckDetailCookieCarrierTest.kt

key-decisions:
  - "NAME THE RESIDUAL, do not widen the route-2 predicate — executed as planned, with all four reasons transcribed into `isCookieInsertionPoint`'s KDoc rather than left in the plan"
  - "The tooltip offers NO remediation. Naming the bound is the whole job; an unmeasured 'delete and re-scan' instruction in the one place an operator cannot check it would be worse than silence"
  - "The `type()` KDoc correction replaces the REASONING only. The test and its assertion are unchanged — the null arm is still worth pinning, it is just a mock arm rather than the real-Burp arm"
  - "The plan's four JUnit-XML `grep` criteria were written without the `()` JUnit appends to method names. Satisfied by intent with the corrected pattern rather than by editing anything; recorded as a deviation"

patterns-established:
  - "Residual pins carry their disposition in the assertion message: 'RESIDUAL, NOT A CONTROL' plus what to do when the pin goes red"
  - "A source-scan test proves an operator-facing string has exactly one assignment site AND that the site references the named constant (no quote character on the line)"
  - "A shape assertion guards an extractor helper: the fence line after the prefix is asserted, so a changed render makes the helper fail loudly rather than measure the wrong line"

requirements-completed: [PRIV-05]

coverage:
  - id: D1
    description: "The privacy-mode tooltip names the write-time/read-time bound in operator language — forward-only, and recorded findings keep the values they were built with — without offering an unmeasured remediation"
    requirement: PRIV-05
    verification:
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/ui/PrivacyModeTooltipBoundTest.kt#theTooltipNamesTheSettingAsForwardOnly"
        status: pass
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/ui/PrivacyModeTooltipBoundTest.kt#theTooltipSaysAlreadyRecordedFindingsKeepTheValuesTheyWereBuiltWith"
        status: pass
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/ui/PrivacyModeTooltipBoundTest.kt#theTooltipStillStatesWhatTheSettingDoes"
        status: pass
    human_judgment: false
  - id: D2
    description: "Exactly one operator-facing site assigns the privacy-mode tooltip, and it references the named constant rather than an inline literal, so the asserted string and the displayed string cannot drift"
    requirement: PRIV-05
    verification:
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/ui/PrivacyModeTooltipBoundTest.kt#exactlyOnePrivacyModeTooltipAssignmentExistsInMainSourceAndItReferencesTheConstant"
        status: pass
      - kind: other
        ref: "grep -ro 'privacyMode.toolTipText' src/main/kotlin/ | wc -l  ->  1"
        status: pass
    human_judgment: false
  - id: D3
    description: "`AiScanCheck.consolidateIssues` carries the canonical WRITE-TIME/READ-TIME BOUND token and the three-hop explanation of why an OFF-built issue is not self-healing"
    requirement: PRIV-05
    verification:
      - kind: other
        ref: "grep -o 'WRITE-TIME/READ-TIME BOUND' src/main/kotlin/com/six2dez/burp/aiagent/scanner/AiScanCheck.kt | wc -l  ->  1 (HEAD: 0)"
        status: pass
      - kind: other
        ref: "grep -o 'KEEP_EXISTING' src/main/kotlin/com/six2dez/burp/aiagent/scanner/AiScanCheck.kt | wc -l  ->  2 (HEAD: 1)"
        status: pass
    human_judgment: false
  - id: D4
    description: "This plan changed no runtime behaviour in AiScanCheck.kt — the whole 69-line diff on that file is comment lines and the identity-compare gate is byte-unchanged"
    verification:
      - kind: other
        ref: "git diff -U0 $BASE -- AiScanCheck.kt | (added/removed, non-header) | grep -cv '^[+-]\\s*(\\*|//|/\\*)'  ->  0 of 69"
        status: pass
      - kind: other
        ref: "grep -vE '^[[:space:]]*(\\*|//|/\\*)' AiScanCheck.kt | grep -o AuditInsertionPointType | wc -l  ->  2 (HEAD: 2, unchanged)"
        status: pass
      - kind: other
        ref: "grep -c 'insertionPoint.type() == AuditInsertionPointType.PARAM_COOKIE' AiScanCheck.kt  ->  1 (HEAD: 1, unchanged)"
        status: pass
    human_judgment: false
  - id: D5
    description: "The route-2 fail-open set is named at the gate with its four reasons, pinned by an assertion whose green run reads as a residual, and bounded by a 17-member enum-population tripwire"
    requirement: PRIV-05
    verification:
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/scanner/AiScanCheckDetailCookieCarrierTest.kt#theRouteTwoGateIsFailOpenForTheseCookieCapableTypes"
        status: pass
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/scanner/AiScanCheckDetailCookieCarrierTest.kt#theInsertionPointTypeEnumPopulationIsTheOneTheResidualWasMeasuredAgainst"
        status: pass
    human_judgment: false
  - id: D6
    description: "The `**Payload Used:**` probe claim two records already make is TRUE of the file: four named assertions across STRICT, BALANCED, OFF and a non-cookie attribution control"
    requirement: PRIV-05
    verification:
      - kind: unit
        ref: "AiScanCheckDetailCookieCarrierTest.kt#cookiePayloadLineIsStrippedUnderStrict"
        status: pass
      - kind: unit
        ref: "AiScanCheckDetailCookieCarrierTest.kt#cookiePayloadLineIsStrippedUnderBalanced"
        status: pass
      - kind: unit
        ref: "AiScanCheckDetailCookieCarrierTest.kt#cookiePayloadLineSurvivesUnderOff"
        status: pass
      - kind: unit
        ref: "AiScanCheckDetailCookieCarrierTest.kt#urlParamPayloadLineSurvivesStrict_attributionControl"
        status: pass
      - kind: other
        ref: "grep -o 'Payload Used' AiScanCheckDetailCookieCarrierTest.kt | wc -l  ->  8 (HEAD: 0)"
        status: pass
    human_judgment: false
  - id: D7
    description: "The false `type()`-returns-null KDoc premise is replaced by the javap measurement that disproved it, and the corrected KDoc names the EXTENSION_PROVIDED arm rather than leaving it implied"
    verification:
      - kind: other
        ref: "javap -c montoya-api-2026.2.jar AuditInsertionPoint -> type() is DEFAULT, body is `getstatic AuditInsertionPointType.EXTENSION_PROVIDED; areturn` (re-derived independently, not taken from the plan)"
        status: pass
      - kind: other
        ref: "grep -o 'getstatic AuditInsertionPointType.EXTENSION_PROVIDED' AiScanCheckDetailCookieCarrierTest.kt | wc -l  ->  1 (HEAD: 0)"
        status: pass
      - kind: unit
        ref: "AiScanCheckDetailCookieCarrierTest.kt#anAbsentInsertionPointTypeDoesNotThrowAndPassesThrough"
        status: pass
    human_judgment: false
  - id: D8
    description: "The asymmetry survives the repair: the new payload-line assertions state, before the first assertion, that line (4) is DEFENCE IN DEPTH and not a measured carrier at HEAD"
    verification:
      - kind: other
        ref: "grep -o 'DEFENCE IN DEPTH' AiScanCheckDetailCookieCarrierTest.kt | wc -l  ->  1 (HEAD: 0)"
        status: pass
      - kind: other
        ref: "the fixture PAYLOAD.value ('  OR  1 = 1 ' shape) carries no trace of DETAIL_SENTINEL — the absence is what makes the claim honest"
        status: pass
    human_judgment: true
    rationale: "Whether the prose actually reads as defence-in-depth rather than as a leak closure to a future maintainer is a judgment about writing, not a property a test can assert. The greps prove the words are present; only a human can confirm they do the job the round-3 contract asked for."

duration: 60 min
completed: 2026-08-28
status: complete
---

# Phase 28 Plan 07: Record Repair at Source Summary

**Four honest surfaces and ten new named assertions, with a byte-unchanged scanner gate: the write-time/read-time bound is now named at the operator's settings panel and at `consolidateIssues`, the route-2 fail-open set is named and tripwired instead of silently widened, the `**Payload Used:**` probe claim two records already made is finally true of the file, and a KDoc premise that was false against the shipped Montoya jar is replaced by the `javap` output that disproved it.**

## Performance

- **Duration:** ~60 min
- **Started:** 2026-08-28T06:26Z (approx; first task commit 07:14Z)
- **Completed:** 2026-08-28T07:26Z
- **Tasks:** 3
- **Files modified:** 4 (1 created, 3 modified)

## Accomplishments

- **The bound is named where an operator meets it.** `PRIVACY_MODE_TOOLTIP` is a top-level `internal const val` in `SettingsPanelInit.kt` carrying three clauses — the retained purpose sentence, `Applies from now on, not retroactively`, and `re-scanning does not rewrite them` — and the single `privacyMode.toolTipText` assignment now references it instead of an inline literal. Four tests pin it, including a source scan proving exactly one assignment site exists in `src/main/kotlin` and that its line carries no string literal at all.
- **The bound is named where the code makes it sticky.** `consolidateIssues`'s three-line placeholder KDoc is replaced by the canonical `WRITE-TIME/READ-TIME BOUND` token plus the full chain: both controls decide once at write time, `AuditIssue.detail()` is immutable and replayed by `scanner_issues`, `KEEP_EXISTING` means a re-scan does not repair the stale issue, and 28-05's red probe already showed `Redaction.apply` cannot rescue it downstream. The disposition (`D-28-09`, conditional on `D-28-10`) and the reason for it are stated so a reader does not re-litigate the acceptance.
- **The route-2 fail-open set is named, not widened.** All four reasons from the plan's `<the_choice_this_plan_makes>` are transcribed into `isCookieInsertionPoint`'s KDoc as its own headed paragraph, kept distinct from the pre-existing shared-predicate alternative-not-taken. `theRouteTwoGateIsFailOpenForTheseCookieCapableTypes` pins the residual's observable width for `HEADER`, `USER_PROVIDED`, `EXTENSION_PROVIDED` and `UNKNOWN`; `theInsertionPointTypeEnumPopulationIsTheOneTheResidualWasMeasuredAgainst` pins the 17-member population so a Burp bump goes red.
- **The probe claim is made true rather than retracted.** `PAYLOAD_USED_PREFIX`, a fence-shape-asserting `payloadLineRenderedFor` extractor, and four named tests now assert route 2's `**Payload Used:**` line across STRICT, BALANCED, OFF and a `PARAM_URL` attribution control — with a section comment stating, *before* the first assertion, that the line is DEFENCE IN DEPTH and not a measured carrier at HEAD.
- **A false premise is corrected at source.** Independently re-derived with `javap -c` on the resolved `montoya-api-2026.2.jar`: `AuditInsertionPoint.type()` is a DEFAULT method whose entire body is `getstatic AuditInsertionPointType.EXTENSION_PROVIDED; areturn`. The KDoc's reasoning is replaced; the test and its assertion are untouched, and the corrected text names both arms — the mock null arm this test pins, and the `EXTENSION_PROVIDED` arm the new fail-open test covers.
- **No behavioural diff.** All 69 changed lines in `AiScanCheck.kt` are comments.

## Task Commits

1. **Task 1 (tracer): the write-time/read-time bound, operator surface + gate** — `7ae5a79` (feat)
2. **Task 2: the route-2 fail-open set named and pinned; false `type()` premise corrected** — `6b7d61c` (test)
3. **Task 3: `**Payload Used:**` assertions with the asymmetry preserved** — `f58b8dd` (test)

**Plan metadata:** committed separately (docs).

_Base pinned before any edit: `0d2f1fea1aeef3c92eb91048ebd3c6f28b1015bf`. Every diff gate below is measured against it, not against the working tree._

## Files Created/Modified

- `src/main/kotlin/com/six2dez/burp/aiagent/ui/SettingsPanelInit.kt` (+33/-1) — `PRIVACY_MODE_TOOLTIP` constant with a KDoc recording *why* the bound is stated to an operator and what the copy deliberately omits; the `:58` assignment now references it.
- `src/main/kotlin/com/six2dez/burp/aiagent/scanner/AiScanCheck.kt` (+68/-1) — expanded `consolidateIssues` KDoc naming the bound; new headed paragraph on `isCookieInsertionPoint` naming the fail-open residual with its four reasons. **Comment lines only.**
- `src/test/kotlin/com/six2dez/burp/aiagent/ui/PrivacyModeTooltipBoundTest.kt` (NEW, 187 lines) — 4 tests: the two bound clauses, the retained purpose clause, and the exactly-one-assignment source scan.
- `src/test/kotlin/com/six2dez/burp/aiagent/scanner/AiScanCheckDetailCookieCarrierTest.kt` (+290/-4) — corrected `type()` KDoc premise; `theRouteTwoGateIsFailOpenForTheseCookieCapableTypes`; `theInsertionPointTypeEnumPopulationIsTheOneTheResidualWasMeasuredAgainst`; `PAYLOAD_USED_PREFIX` / `PAYLOAD_FENCE` / `PAYLOAD_VALUE_LINE_OFFSET`; `payloadLineRenderedFor`; four payload-line tests.

## Measured Deltas — Raw Output vs HEAD

Every `grep -o … | wc -l` acceptance criterion, with the plan's HEAD value beside it, so a reader sees the delta rather than a claim about it (plan verification item 6).

| Measurement | HEAD | After | Criterion | Result |
|---|---|---|---|---|
| `Applies from now on, not retroactively` in `SettingsPanelInit.kt` | 0 | **1** | `= 1` | PASS |
| `re-scanning does not rewrite them` in `SettingsPanelInit.kt` | 0 | **1** | `= 1` | PASS |
| `privacyMode.toolTipText` in `src/main/kotlin/` | 1 | **1** | `= 1` (unchanged) | PASS |
| `PRIVACY_MODE_TOOLTIP` in `SettingsPanelInit.kt` | 0 | **3** | `>= 2` | PASS |
| `WRITE-TIME/READ-TIME BOUND` in `AiScanCheck.kt` | 0 | **1** | `>= 1` | PASS |
| `KEEP_EXISTING` in `AiScanCheck.kt` | 1 | **2** | `>= 2` | PASS |
| `AuditInsertionPointType` in `AiScanCheck.kt`, comments stripped | 2 | **2** | `= 2` (no widening) | PASS |
| `insertionPoint.type() == AuditInsertionPointType.PARAM_COOKIE` | 1 | **1** | `= 1` (unchanged) | PASS |
| `EXTENSION_PROVIDED` in the carrier test | 0 | **7** | `>= 3` | PASS |
| `USER_PROVIDED` in the carrier test | 0 | **3** | `>= 2` | PASS |
| `getstatic AuditInsertionPointType.EXTENSION_PROVIDED` in the carrier test | 0 | **1** | `>= 1` | PASS |
| `Payload Used` in the carrier test | 0 | **8** | `>= 4` | PASS |
| `DEFENCE IN DEPTH` in the carrier test | 0 | **1** | `>= 1` | PASS |
| `PAYLOAD_USED_PREFIX` in the carrier test | 0 | **6** | `>= 3` | PASS |
| `"[STRIPPED]"` retyped literal in the carrier test | 0 | **0** | `= 0` (one vocabulary) | PASS |

### The comment-only gate (plan verification item 5)

Re-run once at the end of the plan, against the pinned base:

```
git diff -U0 0d2f1fe -- .../AiScanCheck.kt | grep -E '^[+-]' | grep -vE '^(\+\+\+|---)' \
  | grep -cvE '^[+-][[:space:]]*(\*|//|/\*)'
0
```

Raw output: **`0`**. Total added/removed lines in that file's diff: **69** — so the gate measured 69 lines and found every one of them to be a comment. The control the plan asked for: a bare `git diff -- AiScanCheck.kt` on the clean tree prints **`0` of `0` lines**, confirming the unpinned form would have passed by measuring nothing.

## Decisions Made

- **NAME THE RESIDUAL, not widen the predicate** (item 5 of the round-3 contract). Executed exactly as chartered. The four reasons are now in the code rather than only in the plan, so a future reader meets them where the decision lives.
- **The tooltip offers no remediation.** No "delete the finding and re-scan". The round measured no remediation; the tooltip is the one place an operator is least able to check an instruction, so silence is the honest option.
- **The `type()` KDoc correction is to the REASONING only.** The test and assertion are byte-identical. The null arm is still worth pinning — it proves the identity compare is null-safe by construction — it is just a *mock* arm, and the corrected text says so and points at the test covering the real-Burp arm.
- **`javap` re-derived independently.** Both measured facts the plan supplied (the 17-member population and `type()`'s default body) were re-measured against the resolved jar before being written into an assertion, rather than transcribed from the plan.

## Deviations from Plan

### 1. [Rule 3 - Blocking] The plan's four JUnit-XML `grep` criteria omit the `()` JUnit appends to method names

- **Found during:** Task 2 (and again in Task 3)
- **Issue:** Three acceptance criteria grep the JUnit XML for `name="<testName>"`. The Gradle JUnit 5 XML writer emits `name="<testName>()"` — with parentheses — so all four patterns as literally written return `0` even though every test is present and green. This is a stale premise in the criterion's *measurement*, not a defect in the code or the tests: it would have been unsatisfiable by any correct implementation.
- **Fix:** Satisfied the criteria by INTENT using the corrected pattern `name="<testName>()"`. Nothing in the tree was changed to accommodate the greps — no test was renamed, and no XML was edited.
- **Files modified:** none (measurement-only deviation)
- **Verification:** `grep -o 'name="theRouteTwoGateIsFailOpenForTheseCookieCapableTypes()"' <xml> | wc -l` → `1`; same for `theInsertionPointTypeEnumPopulationIsTheOneTheResidualWasMeasuredAgainst()` → `1`; `grep -o 'name="cookiePayloadLine[A-Za-z]*()"' <xml> | sort -u | wc -l` → `3`; `grep -o 'name="urlParamPayloadLineSurvivesStrict_attributionControl()"' <xml> | wc -l` → `1`. As-written patterns return `0` for all four, recorded here rather than quietly passed.
- **Committed in:** n/a — no tree change was required.

---

**Total deviations:** 1 (1 blocking measurement error in the plan's acceptance criteria).
**Impact on plan:** None on scope or behaviour. Every criterion's *intent* was met; only the pattern used to measure two of them was corrected, and the correction is recorded so plan 28-08 does not cite the as-written form.

## TDD Gate Compliance

Tasks 2 and 3 carry `tdd="true"`, and the RED gate is **structurally unavailable** for both — recorded here rather than fabricated.

Both tasks add tests that pin **existing, deliberately unchanged** production behaviour: `isCookieInsertionPoint` already returned `false` for the four fail-open members, and `sanitizeCookiePointText` already gated the `**Payload Used:**` line. The plan's own prohibition — *"MUST NOT change runtime behaviour in `AiScanCheck.kt`"* — makes a failing-then-passing cycle impossible by construction. Per `references/tdd.md`, a test that passes in the RED phase means "the feature already exists"; here that is the *point*, not a discipline violation. Neither task is behaviour-adding (`AiScanCheck.kt`'s diff is comment-only, so no non-test source file gained behaviour), so the MVP+TDD behaviour-adding predicate is `false` for both and the gate is exempt.

Commits are therefore typed `test(...)`, which is correct for test-and-comment-only changes.

## Issues Encountered

None. All three tasks' preconditions held when re-checked, both `<measured_facts>` re-derivations (the `consolidateIssues` range and the `isCookieInsertionPoint` range) matched the plan, and the two `javap` measurements were confirmed independently.

## Verification Results

| Check | Result |
|---|---|
| `./gradlew test` (full suite, backgrounded) | **BUILD SUCCESSFUL** — 1308 tests, 0 failures, 0 errors, 1 skipped, 181 classes |
| Suite delta vs baseline | 1298 → 1308 tests (+10, exactly the 10 added), 180 → 181 classes (+1, the new tooltip test) |
| The 1 skip | pre-existing `@Disabled` in `ExternalMcpClientManagerTest` — unchanged |
| `RedactionTest > windowedScanRedactsJsonPairAcrossEveryBoundaryAlignment()` | **PASSED** — the known wall-clock flake did not fire this run |
| `./gradlew ktlintCheck detekt` | **BUILD SUCCESSFUL** (exit 0) |
| `detekt-baseline.xml` | byte-unchanged (`git diff --stat` prints nothing) |
| `./gradlew check` | **NOT RUN** — the plan states it is RED at HEAD for a maintainer-accepted coverage-floor reason and is not a gate |
| `.planning/REQUIREMENTS.md` | untouched; `shasum -a 256` still `9b3219662ec0d007…`; PRIV-05 still `- [ ]` on line 23 |

## Known Stubs

None. No placeholder values, no skipped tests added, no `<verify>` left unrun.

## Threat Flags

None. This plan added no network endpoint, no auth path, no file access pattern and no schema change. The two `accept`-disposition threats it touches (`T-28-38` route-2 fail-open, `T-28-39` the write-time/read-time bound) are now *more* visible than before, not less: both are named at their gate, pinned by an assertion, and bounded by a tripwire.

## User Setup Required

None — no external service configuration required.

## Next Phase Readiness

**Ready for plan 28-08**, which writes the archival record and applies the override. Everything 28-08 may cite is now shipped and measured:

- `PRIVACY_MODE_TOOLTIP` and its four pinning tests exist at `ui/SettingsPanelInit.kt` / `ui/PrivacyModeTooltipBoundTest.kt`.
- `WRITE-TIME/READ-TIME BOUND` is the canonical token, spelled identically at both production sites. **28-08 must not introduce a synonym.**
- The two residual pins are named `theRouteTwoGateIsFailOpenForTheseCookieCapableTypes` and `theInsertionPointTypeEnumPopulationIsTheOneTheResidualWasMeasuredAgainst`.
- The four payload-line tests are `cookiePayloadLineIsStrippedUnderStrict`, `cookiePayloadLineIsStrippedUnderBalanced`, `cookiePayloadLineSurvivesUnderOff`, `urlParamPayloadLineSurvivesStrict_attributionControl`.

**Two things 28-08 must NOT claim**, because this plan deliberately did not ship them:

1. That route 2's `**Payload Used:**` line is a closed carrier. It is DEFENCE IN DEPTH at HEAD; the file says so in its own words.
2. That the route-2 fail-open set was closed. It was **named**, and `T-28-38` remains `accept`.

**Carried residuals, unchanged by this plan:** the write-time/read-time bound itself (`D-28-09`, a read-time fix is its own phase); the route-2 fail-open set for the four cookie-capable non-`PARAM_COOKIE` members (`D-28-11`); and the repository-wide detail-producer gate `WR-01` describes (`D-28-06`) — the new `payloadLineRenderedFor` helper's KDoc explicitly states it is **not** that gate.

---
*Phase: 28-the-issue-detail-cookie-carrier-auditissue-detail-scanner-is*
*Completed: 2026-08-28*
