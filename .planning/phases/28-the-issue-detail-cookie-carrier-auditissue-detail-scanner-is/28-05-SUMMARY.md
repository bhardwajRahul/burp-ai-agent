---
phase: 28-the-issue-detail-cookie-carrier-auditissue-detail-scanner-is
plan: 05
subsystem: privacy
tags: [redaction, scanner, montoya, kotlin, junit5, mockito, cookie-carrier, priv-05, ar-27-08]

requires:
  - phase: 28-the-issue-detail-cookie-carrier-auditissue-detail-scanner-is
    provides: "28-01's `INJECTION_VALUE_STRIPPED_MARKER`, `ORIGINAL_VALUE_MAX_CHARS`, `PAYLOAD_VALUE_MAX_CHARS` and the `sanitizeInjectionPointValue` gate shape; 28-04's sibling-gate precedent and the `IssueDetailCookieCarrierTest` serialization tail"
  - phase: 27-security-findings-round-4
    provides: "`AR-27-08` — the issue-detail cookie carrier, and the measurement that `Redaction` is structurally blind to it"
provides:
  - "`AiScanCheck.isCookieInsertionPoint` — the repository's single insertion-point cookie predicate, an identity compare against the closed Montoya `AuditInsertionPointType.PARAM_COOKIE`"
  - "`AiScanCheck.sanitizeCookiePointText` — the one gate both of route 2's detail lines call, writing the shared marker by reference"
  - "Route 2 of `AR-27-08` (`CR-02`) controlled: a `PARAM_COOKIE` point's `baseValue()` is absent from the redacted `detail` field under STRICT and BALANCED, present under OFF"
  - "`AiScanCheckDetailCookieCarrierTest` — the repository's FIRST Montoya `AuditInsertionPoint` test fixture"
  - "`CookieRouteDispositionTest.exactlyOneInsertionPointCookieTypePredicateExistsInMainSource` — a non-vacuous tripwire over the NEW predicate spelling class, disjoint from the old one"
  - "The corrected `ONLY PRODUCER` KDoc (D-28-08 second site), append-and-amend, naming the D-28-06 residual"
  - "`WR-05` fixed — `CookieRouteDispositionTest.relativePath` is cross-platform"
affects: [28-06, verify-work, secure-phase, AR-27-08, WR-01]

actuals:
  tokens: 14001
  tasks: 3
  commits: 3

tech-stack:
  added: []
  patterns:
    - "Two disjoint predicate populations, two counts, two owners: when a second control must key on a DIFFERENT closed enum, it gets its own tripwire rather than widening an existing count that is evidence for a different claim"
    - "Companion-held pure gates: a control's pure helper functions live in a companion object when adding them as instance methods would trip a complexity gate, rather than growing a suppression baseline"
    - "The wrong-predicate red probe: for any type-keyed control, prove on the record that the OBVIOUS shared predicate returns false for the real constant, because a control that compiles and reads as correct is indistinguishable from a working one by inspection"

key-files:
  created:
    - src/test/kotlin/com/six2dez/burp/aiagent/scanner/AiScanCheckDetailCookieCarrierTest.kt
  modified:
    - src/main/kotlin/com/six2dez/burp/aiagent/scanner/AiScanCheck.kt
    - src/main/kotlin/com/six2dez/burp/aiagent/scanner/ScannerIssueSupport.kt
    - src/test/kotlin/com/six2dez/burp/aiagent/scanner/CookieRouteDispositionTest.kt

key-decisions:
  - "Route 2 keys on `insertionPoint.type() == AuditInsertionPointType.PARAM_COOKIE`, an identity compare against a member of a closed Montoya enum. Option (b) — widening `Redaction.isCookieParameterType` — was CONSIDERED AND NOT TAKEN, and the reason is written into source."
  - "One marker vocabulary (D-28-05): `ScannerIssueSupport.INJECTION_VALUE_STRIPPED_MARKER` is referenced across the file boundary and never retyped. No marker constant and no marker literal was added to `AiScanCheck.kt`."
  - "The payload line at `AiScanCheck.kt:357` is controlled as DEFENCE IN DEPTH, not as a measured leak closure. This class sources payloads from the static quick-payload table and interpolates nothing, so unlike route 1 it is not a carrier at HEAD. Source, threat table and this record all say so."
  - "Both new helpers live in a `companion object` rather than as instance methods — a forced deviation, see below. Growing `detekt-baseline.xml` and raising the threshold were both rejected."
  - "`requirements-completed` is deliberately EMPTY. D-28-04 holds PRIV-05 at `- [ ]` and `.planning/REQUIREMENTS.md` byte-unchanged; whether the gap round justifies ticking it is the round gate's judgement, not this plan's."

patterns-established:
  - "Disjoint populations get disjoint tripwires: the class KDoc's exclusion list grows an item that names the new spelling AND points at the tripwire that covers it, so 'excluded' never silently means 'uncovered'"
  - "Fixture pin before behaviour: when a test fakes a collaborator, an explicit assertion that the stub took precedes every behavioural assertion, because a stub that did not take makes absence assertions pass while measuring nothing"
  - "Append-and-amend with a recorded byte digest: the pre-edit block digest goes into the commit and the record, and the post-edit prefix is proven byte-identical rather than asserted to be"

requirements-completed: []

coverage:
  - id: D1
    description: "Under STRICT and BALANCED, a `PARAM_COOKIE` insertion point's `baseValue()` is absent from the `detail` field of an `AiScanCheck`-produced issue, measured end to end through `IssueDetails` -> `toolJson.encodeToString` -> `Redaction.apply` -> the extracted `detail` field. This is the FIRST pass of the route-2 shape through the redactor (`28-VERIFICATION.md` `missing[6]`)."
    requirement: "PRIV-05"
    verification:
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/scanner/AiScanCheckDetailCookieCarrierTest.kt#cookieBaseValueIsStrippedUnderStrict"
        status: pass
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/scanner/AiScanCheckDetailCookieCarrierTest.kt#cookieBaseValueIsStrippedUnderBalanced"
        status: pass
    human_judgment: false
  - id: D2
    description: "Route 2's control is POLICY-DRIVEN, not an unconditional rewrite: under OFF the `baseValue()` survives verbatim, and a `PARAM_URL` point carrying the identical value is untouched under STRICT. Before this plan the file read no privacy mode at all and behaved identically in all three modes."
    requirement: "PRIV-05"
    verification:
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/scanner/AiScanCheckDetailCookieCarrierTest.kt#cookieBaseValueSurvivesUnderOff"
        status: pass
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/scanner/AiScanCheckDetailCookieCarrierTest.kt#urlParamInsertionPointSurvivesStrict_attributionControl"
        status: pass
    human_judgment: false
  - id: D3
    description: "The type-keying question is answered by MEASUREMENT: the shared string-name predicate returns FALSE for the Montoya insertion-point cookie constant, whose name is `PARAM_COOKIE`. A future reader cannot re-derive it wrongly by inspection."
    requirement: "PRIV-05"
    verification:
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/scanner/AiScanCheckDetailCookieCarrierTest.kt#theSharedStringNamePredicateDoesNotRecogniseTheInsertionPointCookieConstant"
        status: pass
    human_judgment: false
  - id: D4
    description: "Edge cases: an empty-valued `PARAM_COOKIE` point still renders the marker (no emptiness guard), and a point whose `type()` is absent does not throw — it takes the pass-through branch."
    requirement: "PRIV-05"
    verification:
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/scanner/AiScanCheckDetailCookieCarrierTest.kt#anEmptyBaseValueStillRendersTheMarkerUnderStrict"
        status: pass
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/scanner/AiScanCheckDetailCookieCarrierTest.kt#anAbsentInsertionPointTypeDoesNotThrowAndPassesThrough"
        status: pass
    human_judgment: false
  - id: D5
    description: "The measurement is non-vacuous: the pre-existing `Cookie:` header rule fires in the SAME STRICT output the detail assertion is made in, and the sentinel sits well inside the serialized blob rather than at its truncated tail."
    requirement: "PRIV-05"
    verification:
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/scanner/AiScanCheckDetailCookieCarrierTest.kt#theCookieHeaderPositiveControlFiresInTheSameStrictOutput"
        status: pass
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/scanner/AiScanCheckDetailCookieCarrierTest.kt#theSentinelIsNotTheTailOfTheSerializedBlob_nonVacuity"
        status: pass
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/scanner/AiScanCheckDetailCookieCarrierTest.kt#theInsertionPointMockActuallyReturnsWhatItWasStubbedToReturn"
        status: pass
    human_judgment: false
  - id: D6
    description: "The NEW predicate spelling class has its own committed, non-vacuous tripwire, and the OLD population is proven unmoved by running it rather than reading it."
    verification:
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/scanner/CookieRouteDispositionTest.kt#exactlyOneInsertionPointCookieTypePredicateExistsInMainSource"
        status: pass
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/scanner/CookieRouteDispositionTest.kt#everyInsertionPointSpellingHasAKnownPositive"
        status: pass
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/scanner/CookieRouteDispositionTest.kt#exactlyOneCookieTypePredicateExistsInMainSource"
        status: pass
    human_judgment: false
  - id: D7
    description: "`WR-05` fixed: `CookieRouteDispositionTest.relativePath` no longer mixes a forward-slash root literal with the platform separator, so the owner assertion works on Windows."
    verification:
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/scanner/CookieRouteDispositionTest.kt#exactlyOneCookieTypePredicateExistsInMainSource"
        status: pass
    human_judgment: false
  - id: D8
    description: "The false `ONLY PRODUCER` claim (D-28-08 second site) is corrected append-and-amend: prior text byte-identical as a contiguous prefix, a dated supersession marker, two producers named, the non-existent gate named as non-existent, and the D-28-06 residual stated without implying a gate exists."
    verification: []
    human_judgment: true
    rationale: "Prose correctness against a decision record is a judgement call. The mechanical half — that the prior text survives byte-for-byte — IS measured (sha256 recorded pre- and post-edit, below), but whether the amendment's three claims are faithful to D-28-06 and to what `WR-01` measured cannot be asserted by a test."

duration: 25min
completed: 2026-08-27
status: complete
---

# Phase 28 Plan 05: Route 2 of the Issue-Detail Cookie Carrier Summary

**`AiScanCheck.buildDetail` — a second, entirely uncontrolled active-scan detail producer — now strips a `PARAM_COOKIE` insertion point's `baseValue()` behind an enum-identity type key against Montoya's closed `AuditInsertionPointType`, measured through `Redaction.apply` by the repository's first `AuditInsertionPoint` test fixture, with the wrong-predicate probe proving on the record that reusing the shared string-name predicate would have shipped a control that never fires.**

## Performance

- **Duration:** 25 min
- **Started:** 2026-08-27T15:25:00Z
- **Completed:** 2026-08-27T15:50:00Z
- **Tasks:** 3
- **Files modified:** 4 (3 modified, 1 created)

## Accomplishments

- **Route 2 of `AR-27-08` (`CR-02`) is controlled.** At the parent commit `b8b22ab`,
  `grep -cE 'RedactionPolicy|privacyMode|Redaction|sanitize' AiScanCheck.kt` returned **0** — the
  file read no privacy mode at all and rendered a cookie insertion point's value identically under
  STRICT, BALANCED and OFF, into an `AuditIssue.detail()` that reaches the `scanner_issues` MCP tool
  result via `api.siteMap()`. It now derives the live policy from the `getSettings()` that was
  already on `buildDetail`'s first line.
- **The type-keying choice is stated in source with its measurement, not assumed.** This was the
  round's single highest-risk item: `Redaction.isCookieParameterType` compares an uppercased,
  trimmed type NAME against `"COOKIE"` and returns **false** for `PARAM_COOKIE`. Reusing it would
  have compiled, read as correct, and shipped a dead control.
- **The repository's first Montoya `AuditInsertionPoint` fixture.** Measured at `b8b22ab`,
  `grep -rl 'AuditInsertionPoint' src/test/kotlin/` listed **zero** files.
- **Route 2 measured through the redactor**, discharging `28-VERIFICATION.md` `missing[6]` — the
  route-2 shape had never been passed through `Redaction.apply`.
- **The new predicate spelling class has its own non-vacuous tripwire**, and the old population is
  proven unmoved by running it rather than reading it.
- **`WR-05` fixed** — a real Windows defect against a stated project constraint.
- **The false `ONLY PRODUCER` claim corrected** append-and-amend, naming the `D-28-06` residual
  plainly rather than implying a gate exists.

## Task Commits

Each task was committed atomically:

1. **Task 1: Control `AiScanCheck.buildDetail`** — `c35f230` (feat)
2. **Task 2: The route-2 test, both red probes recorded** — `36ba487` (test)
3. **Task 3: Tripwire the new population, correct the `ONLY PRODUCER` claim** — `7b5c3b9` (test)

_This plan's SUMMARY commit follows._

## Files Created/Modified

- `src/main/kotlin/com/six2dez/burp/aiagent/scanner/AiScanCheck.kt` — gains the policy lookup,
  `isCookieInsertionPoint`, `sanitizeCookiePointText` (both in a companion object, see Deviations);
  `buildDetail` widened `private` -> `internal` and both its detail lines routed through the gate.
- `src/test/kotlin/com/six2dez/burp/aiagent/scanner/AiScanCheckDetailCookieCarrierTest.kt` — **NEW**,
  10 tests. The repository's first `AuditInsertionPoint` fixture.
- `src/test/kotlin/com/six2dez/burp/aiagent/scanner/CookieRouteDispositionTest.kt` — the class KDoc
  exclusion list 2 -> 3 items, two new tests, a new companion pattern list with fixtures, a shared
  `matchingCodeLinesIn` helper, and the `WR-05` fix.
- `src/main/kotlin/com/six2dez/burp/aiagent/scanner/ScannerIssueSupport.kt` — the `ONLY PRODUCER`
  KDoc corrected append-and-amend. **No code change**; the function body is byte-unchanged.

## SC3 — The Red Probes, Named Assertion and Verbatim Message

The plan's SC3 forbids recording "the suite went red". Both probes below name the assertion and
quote its message verbatim. Both were reverted with `git checkout HEAD -- <path>` (never
`git stash`, which is prohibited in this repository's worktrees), and `git status --porcelain src/`
was confirmed clean after each.

### Probe 1 — THE WRONG-PREDICATE PROBE (SC3, the round's highest-risk item)

Substituted the shared string-name predicate for the enum identity compare inside
`AiScanCheck.isCookieInsertionPoint`:

```kotlin
internal fun isCookieInsertionPoint(insertionPoint: AuditInsertionPoint): Boolean =
    com.six2dez.burp.aiagent.redact.Redaction.isCookieParameterType(insertionPoint.type()?.name.orEmpty())
```

It **compiles**. The class stayed **GREEN on every presence assertion** —
`cookieBaseValueSurvivesUnderOff`, `urlParamInsertionPointSurvivesStrict_attributionControl`,
`theSharedStringNamePredicateDoesNotRecogniseTheInsertionPointCookieConstant` and the fixture pin all
passed — while the designated assertion went **RED with the sentinel PRESENT**:

```
cookieBaseValueIsStrippedUnderStrict()
org.opentest4j.AssertionFailedError: STRICT: a PARAM_COOKIE insertion point's baseValue() must be
ABSENT from the redacted `detail` field of an AiScanCheck-produced issue, but the sentinel
'cedar-anchor-marble-feather' was present. This is route 2 of AR-27-08 (CR-02): the value reaches
the scanner_issues MCP tool result through AuditIssue.detail().
```

**This is the measured proof of the plan's central risk.** A predicate that compiles and reads as
correct is indistinguishable from a working control by inspection; only the probe distinguishes
them. `10 tests completed, 4 failed`.

### Probe 2 — THE CONTROL-REVERT PROBE

Restored the untreated render:

```kotlin
**Original Value:** ${insertionPoint.baseValue().take(ScannerIssueSupport.ORIGINAL_VALUE_MAX_CHARS)}
```

Same 4 assertions red, and `cookieBaseValueIsStrippedUnderStrict` failed with the **identical
verbatim message** quoted above. `10 tests completed, 4 failed`.

### Probe 3 — TRIPWIRE NON-VACUITY

Added a second identity compare at a scratch main-source location
(`ScannerIssueSupport.scratchProbeSecondInsertionPointPredicate`):

```
exactlyOneInsertionPointCookieTypePredicateExistsInMainSource()
org.opentest4j.AssertionFailedError: expected exactly ONE insertion-point cookie-type predicate in
src/main/kotlin — the one inside com/six2dez/burp/aiagent/scanner/AiScanCheck.kt's
isCookieInsertionPoint. Found 2: com/six2dez/burp/aiagent/scanner/ScannerIssueSupport.kt -> internal
fun scratchProbeSecondInsertionPointPredicate(insertionPoint: AuditInsertionPoint): Boolean =
insertionPoint.type() == AuditInsertionPointType.PARAM_COOKIE;
com/six2dez/burp/aiagent/scanner/AiScanCheck.kt -> internal fun
isCookieInsertionPoint(insertionPoint: AuditInsertionPoint): Boolean = insertionPoint.type() ==
AuditInsertionPointType.PARAM_COOKIE. A SECOND insertion-point cookie predicate is how the route-2
control gets bypassed without anyone editing isCookieInsertionPoint: the new predicate silently
acquires its own notion of what a cookie insertion point is, and the two drift apart. Route the new
call site through AiScanCheck.isCookieInsertionPoint instead of widening this expectation.
==> expected: <1> but was: <2>
```

Hit count **2**, **both paths listed**, as the acceptance criterion required.

## The Append-and-Amend Digest (D-28-08 second site)

**The plan's line numbers were STALE.** It cited the `ONLY PRODUCER` block at
`ScannerIssueSupport.kt:72-104`; after 28-04 merged at `b8b22ab` the same 33-line block sits at
**141-173** (shifted by exactly 69 lines, same length). Both digests are recorded so the shift is
auditable rather than silent:

| What | Range | sha256 |
|---|---|---|
| Pre-edit, the ACTUAL block | 141–173 | `2792aa10ec5778a9f95a8844ec15246aca5fbbf7ea8e6208177b3efa32f053c2` |
| Pre-edit, the plan's STALE range (recorded for the audit trail only) | 72–104 | `004ba9ca6ed514a04bc0f85fc46d122a97192566f9e1a02279d39df9a9a55670` |

**Post-edit verification.** The prior text must survive verbatim as a *contiguous prefix*. Lines
141–172 (the block minus its closing `*/`, which necessarily moves) were compared byte-for-byte
against the same range at `HEAD`:

```
eb8e045ff36a6b1bf41c0a35e676a5515c2c9e9801983c9858b7bce1908c407c  pre_prefix.txt   (from git show HEAD:...)
eb8e045ff36a6b1bf41c0a35e676a5515c2c9e9801983c9858b7bce1908c407c  post_prefix.txt  (working tree)
```

`diff` reported no differences. **Prior text survives byte-for-byte.**

The amendment states three things plainly, per the plan:
(a) there are **TWO** producers, the second being `AiScanCheck.buildDetail`, now controlled by its
own type-keyed gate against a **different** closed enum;
(b) the single-producer gate the original KDoc pointed at **does not exist** — it filters the
`List<String>` the function itself returned and is structurally incapable of seeing another file, so
it did not fail when the second producer existed;
(c) **no repository-wide enforcement is added by this round.** `D-28-06` records that as CONSIDERED
AND NOT TAKEN. After this round there are **two controlled producers and still no gate that would
catch a third**, and the new predicate tripwire is explicitly named as a *different mechanism over a
different population* that does **not** close `WR-01`.

## Decisions Made

- **The enum identity compare, option (a).** Widening `Redaction.isCookieParameterType` was
  considered and rejected: it would move
  `CookieRouteDispositionTest.exactlyOneCookieTypePredicateExistsInMainSource`, which counts a
  different population (`HttpParameterType` name comparisons) and whose count is evidence for a
  different claim; and merging two unrelated Montoya enums under one predicate would make that
  predicate's own contract ambiguous. Recorded in `isCookieInsertionPoint`'s KDoc.
- **The payload line is defence in depth, and the record says so.** `AiScanCheck` sources payloads
  from `getQuickPayloads`, a static table with no interpolation (`PayloadGenerator.kt:633-639`), so
  unlike route 1 its payload line is **not** a carrier at HEAD. Its fixture in the new test is
  hand-built rather than production-derived *precisely so it carries no trace of the sentinel* — a
  derived fixture would have made the line look like a carrier it is not. Calling this a measured
  leak closure would be the overclaim this phase exists to correct.
- **`requirements-completed` left empty.** D-28-04 holds PRIV-05 at `- [ ]` and `REQUIREMENTS.md`
  byte-unchanged (verified: `git diff --stat` prints nothing). Ticking it is the round gate's call.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Both new helpers moved into a `companion object`**

- **Found during:** Task 1
- **Issue:** Adding `isCookieInsertionPoint` and `sanitizeCookiePointText` as instance methods took
  `AiScanCheck` to **11** functions and turned detekt red:
  `AiScanCheck.kt:24:7: Class 'AiScanCheck' with '11' functions detected. Defined threshold inside
  classes is set to '11' [TooManyFunctions]`. `TooManyFunctions` is not configured in `detekt.yml`
  (detekt's default applies) and this class has no baseline entry for it.
- **Why the obvious fixes were refused:** growing `detekt-baseline.xml` is banned by QUAL-07 *and* by
  this plan's own acceptance criterion (`git diff --stat -- detekt-baseline.xml` must print nothing);
  raising the threshold in `detekt.yml` would weaken a repository-wide quality gate to land a
  two-function change, and `detekt.yml` is not in this plan's `files_modified`.
- **Fix:** both functions are **pure** — they read no instance state, only their arguments — so a
  companion object is where they already belonged. They remain `internal`, the identity compare still
  lives in `scanner/AiScanCheck.kt` (which is what the new tripwire asserts), and the qualified call
  form `AiScanCheck.isCookieInsertionPoint(...)` matches the plan's artifacts table exactly.
- **Files modified:** `src/main/kotlin/com/six2dez/burp/aiagent/scanner/AiScanCheck.kt`
- **Verification:** `compileKotlin`, `ktlintCheck` and `detekt` all exit 0;
  `git diff --stat -- detekt-baseline.xml` prints nothing. Rationale written into the companion's
  own KDoc so the deviation is attributable in source.
- **Committed in:** `c35f230`

**2. [Rule 1 - Bug] The plan's `createIssue` reference does not exist**

- **Found during:** Task 1
- **Issue:** The plan named `buildDetail`'s caller `createIssue` (`AiScanCheck.kt:249-260`) in two
  places. There is no function by that name in this class — the `AuditIssue.auditIssue(...)`
  construction is inline in **`testPayload`**. A KDoc `[createIssue]` link would have been dangling
  and, worse, would have sent a reader looking for a function that is not there.
- **Fix:** the KDoc references `[testPayload]` and notes the discrepancy explicitly so a reader
  comparing plan to source is not confused.
- **Files modified:** `src/main/kotlin/com/six2dez/burp/aiagent/scanner/AiScanCheck.kt`
- **Verification:** `grep -n "fun createIssue"` returns nothing; `buildDetail` has exactly one call
  site, at `AiScanCheck.kt:252` inside `testPayload`.
- **Committed in:** `c35f230`

**3. [Rule 1 - Bug] The plan's `ONLY PRODUCER` line numbers were stale**

- **Found during:** Task 3
- **Issue:** The plan cited `ScannerIssueSupport.kt:72-104` and made the pre-edit digest of that
  range an acceptance criterion. After 28-04 merged, the block sits at 141–173. Hashing 72–104 would
  have digested an unrelated span (the `PAYLOAD_VALUE_MAX_CHARS` KDoc) and the "byte-prefix intact"
  proof would have measured nothing.
- **Fix:** digested the **actual** block, and recorded the stale range's digest too so the shift is
  auditable. Both are in the table above.
- **Verification:** the block is the same 33 lines, shifted by exactly 69; post-edit prefix proven
  byte-identical by `diff` and matching sha256.
- **Committed in:** `7b5c3b9`

### Acceptance-criterion literal, satisfied in substance

`grep -c 'exactlyOneInsertionPointCookieTypePredicateExistsInMainSource'` returns **2**, not the
criterion's stated 1: one `fun` declaration (line 135) and one KDoc cross-reference (line 53). The
cross-reference is **mandated by the same task's action** ("Say explicitly that this is not an
exemption — the new population gets its own tripwire below"), so the criterion's literal `1` was
written without accounting for a sentence the task itself requires. The substantive requirement —
exactly one test by that name, and it passes — holds. Flagged rather than silently satisfied.

---

**Total deviations:** 3 auto-fixed (1 blocking, 2 bugs) + 1 criterion-literal note.
**Impact on plan:** No scope creep and no weakened assertions. The companion move is the only
structural change to the plan's stated shape, it was forced by a quality gate, and it preserves every
property the plan and its tripwire depend on.

## Issues Encountered

None beyond the deviations above. The known `RedactionTest` wall-clock flake did not appear (that
class was not in this plan's verification set). `./gradlew check` was **not** run as a gate, per the
carried constraint.

## Verification

| Test class | Tests | Failures | Errors |
|---|---|---|---|
| `AiScanCheckDetailCookieCarrierTest` | 10 | 0 | 0 |
| `CookieRouteDispositionTest` | 7 | 0 | 0 |
| `IssueDetailCookieCarrierTest` | 21 | 0 | 0 |
| `EvidenceTailReachTest` | 2 | 0 | 0 |
| `AiPassiveScanCheckTest` | 2 | 0 | 0 |
| **Total** | **42** | **0** | **0** |

Counts read from `build/test-results/test/*.xml`, not from console output, so a silently-zero run
cannot be mistaken for a pass.

- `ktlintCheck` and `detekt` — both exit 0.
- `git diff --stat` prints nothing for `detekt-baseline.xml`, `build.gradle.kts`,
  `.planning/REQUIREMENTS.md`, and `AdaptivePayloadEngine.kt` (SC4 does not regress).
- `grep -rl 'AuditInsertionPoint' src/test/kotlin/` at plan end lists **exactly two** paths, the
  expected value:
  - `src/test/kotlin/com/six2dez/burp/aiagent/scanner/AiScanCheckDetailCookieCarrierTest.kt`
  - `src/test/kotlin/com/six2dez/burp/aiagent/scanner/CookieRouteDispositionTest.kt`

  Task 2's "exactly one" was a task-boundary criterion; Task 3's known-positive fixtures necessarily
  contain the literal, so two is correct at plan end and not a regression.
- Source-shape criteria on `AiScanCheck.kt` (comment lines stripped where the criterion says so):
  `RedactionPolicy|privacyMode` = 3 (was **0** at `b8b22ab`); `RedactionPolicy.fromMode(` = 1, with
  argument `settings.privacyMode`; `getSettings()` inside `buildDetail` = 1;
  `AuditInsertionPointType.PARAM_COOKIE` = 1; `isCookieParameterType` = **0**; the literal
  `"[STRIPPED]"` = **0**; `INJECTION_VALUE_STRIPPED_MARKER` = 2; `take(100)|take(500)` inside
  `buildDetail` = **0**; `internal fun buildDetail` = 1.
- `WR-05`: `File.separator` = **0**, `invariantSeparatorsPath` = 1 in `CookieRouteDispositionTest.kt`.
- Sentinel isolation: every sentinel in the new test file (`cedar-anchor-marble-feather`,
  `walnut-ribbon-quarry-saddle`, `juniper-satchel-name`, `wobble`) returns **0** hits against
  `IssueDetailCookieCarrierTest.kt`.

## TDD Gate Compliance

The plan marks Task 1 `tdd="true"` but assigns the test file to **Task 2**, so the commit order is
`feat` (`c35f230`) then `test` (`36ba487`) — the RED gate does not precede the GREEN gate in git
history. **The reason is structural, not a shortcut:** the test cannot compile until Task 1 widens
`buildDetail` from `private` to `internal`, so no temporally-prior failing test was possible without
splitting Task 1 across the task boundary and breaking the atomic per-task commits.

The RED measurement is supplied instead by **Probe 2 (the control revert)**, which is an explicit
acceptance criterion of Task 2 and is recorded above with its named assertion and verbatim message.
That probe demonstrates exactly what a prior-failing test would have: with the control absent, the
test is red on the right assertion for the right reason. Recorded here rather than left for a
reviewer to notice.

## Residuals — stated, not softened

- **`WR-01` is NOT closed.** There is still no repository-wide gate that would catch a *third*
  issue-detail producer. `D-28-06` records this as CONSIDERED AND NOT TAKEN. After this plan there
  are **two controlled producers and no such gate**. The tripwire built here counts cookie-type
  **predicates**, not issue-detail **producers** — a different mechanism over a different population.
- **The route-2 test does not prove the operator's configured mode reaches `buildDetail` in
  production.** That link is the `getSettings()` call plus the `App.kt:214-215` wiring, and no
  assertion here executes either. Same residual `IssueDetailCookieCarrierTest` names for route 1;
  stated in the new file's class KDoc.
- **The payload line is defence in depth only** — not a measured leak closure at HEAD.
- **The operator's raw traffic is untouched.** `listOf(baseRequestResponse, markedAttack)` at the
  `AuditIssue.auditIssue` call site is byte-unchanged by this control; the operator keeps the raw
  attack request in their own Burp evidence pane. The accepted trade route 1 already records.
- **`AR-28-01`** (ResponseAnalyzer evidence tail) is not reopened here, per `already_known`.

## Next Phase Readiness

- Route 1 (28-04) and route 2 (28-05) are both controlled and both measured through `Redaction.apply`.
  `AR-27-08` now has both of its measured routes closed at their write sites.
- `28-06` consumes measured fact 7 from this plan's context: `.baseValue()` has exactly two main-source
  call sites, both in `AiScanCheck.kt` — `:114` (non-carrying, a regex subject inside
  `determineVulnClasses`) and the carrier this plan controlled. That measurement still holds.
- The round gate — not a plan — decides whether PRIV-05 may finally be ticked. `REQUIREMENTS.md` is
  byte-unchanged and was not touched.

## Self-Check: PASSED

All claimed files exist on disk:

- `.planning/phases/28-.../28-05-SUMMARY.md`
- `src/main/kotlin/com/six2dez/burp/aiagent/scanner/AiScanCheck.kt`
- `src/main/kotlin/com/six2dez/burp/aiagent/scanner/ScannerIssueSupport.kt`
- `src/test/kotlin/com/six2dez/burp/aiagent/scanner/AiScanCheckDetailCookieCarrierTest.kt`
- `src/test/kotlin/com/six2dez/burp/aiagent/scanner/CookieRouteDispositionTest.kt`

All claimed commits exist on `worktree-agent-a5bb3b501d1e0cb7d`, ahead of base `b8b22ab`:
`c35f230`, `36ba487`, `7b5c3b9`, `d50727c` (this SUMMARY; the digest above is its parent state).

`STATE.md` and `ROADMAP.md` were NOT modified — the orchestrator owns those writes after the wave
merges.

---
*Phase: 28-the-issue-detail-cookie-carrier-auditissue-detail-scanner-is*
*Completed: 2026-08-27*
