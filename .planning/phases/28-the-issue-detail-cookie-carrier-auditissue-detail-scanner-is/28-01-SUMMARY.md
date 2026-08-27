---
phase: 28-the-issue-detail-cookie-carrier-auditissue-detail-scanner-is
plan: 01
subsystem: testing
tags: [privacy, redaction, scanner, montoya, kotlin, junit5, mutation-testing]

requires:
  - phase: 27-priv-05-gap-closure-sanitize-headers
    provides: "AR-27-08 — the measured issue-detail cookie carrier, dispositioned TRANSFER with no control applied"
provides:
  - "ScannerIssueSupport.sanitizeInjectionPointValue — a type-keyed cookie control on the active-scan issue detail"
  - "ScannerIssueSupport.buildActiveIssueDetailLines — the single producer of the active-scan detail lines"
  - "IssueDetailCookieCarrierTest — 14 tests carrying an attribution control, a positive control, a non-vacuity guard and a field-scoped content-destruction guard"
  - "A measured red probe for the control, with both mutations' verbatim failure messages recorded"
  - "A measured over-match probe proving the content-destruction guard sees a regression class every leak-only assertion is blind to"
affects: [28-02, 28-03, PRIV-05, AR-27-08]

actuals:
  tokens: 24000
  tasks: 3
  commits: 6

tech-stack:
  added: []
  patterns:
    - "Type-keyed privacy control at the write site — the last point in the route still holding InjectionType"
    - "Source-TEXT pin standing in, explicitly and named as weaker, for missing execution coverage"
    - "Field-scoped content-destruction guard: equality under exactly one known substitution, not a leak-only absence assertion"
    - "Enumerated-difference assertion: two known STRICT-vs-OFF differences named individually, a third is a finding"

key-files:
  created:
    - src/test/kotlin/com/six2dez/burp/aiagent/scanner/IssueDetailCookieCarrierTest.kt
  modified:
    - src/main/kotlin/com/six2dez/burp/aiagent/scanner/ScannerIssueSupport.kt
    - src/main/kotlin/com/six2dez/burp/aiagent/scanner/ActiveAiScanner.kt

key-decisions:
  - "D-28-01: the control goes at the write site (ScannerIssueSupport.buildActiveIssueDetailLines), keyed on InjectionType.COOKIE — every downstream site holds only a rendered string and would inherit the same blindness phase 27 measured in cookieTypedParamRegex"
  - "The content-destruction guard is FIELD-scoped to `detail`, not blob-scoped: a blob-scoped form is false by construction on this fixture because the positive-control Cookie: header is legitimately stripped by a pre-existing rule in the same STRICT output"
  - "Mutation B is detected ONLY by a source-TEXT pin, and this SUMMARY names that as weaker than an execution assertion rather than reporting it as reach the file does not have"
  - "HEADER_RULE_STRIPPED_MARKER is kept as its own literal rather than read from INJECTION_VALUE_STRIPPED_MARKER — the two markers are independently owned and only happen to share the text today"

patterns-established:
  - "Red probe with a NAMED designated assertion recorded in the test class KDoc, so a future mutator knows which line is supposed to catch them"
  - "Re-measure the probe when the class grows, rather than carrying a count that drifted inside the same plan"

# PRIV-05 is NOT closed by this plan. This plan closes the WRITE half of AR-27-08 only;
# plans 28-02 and 28-03 carry the rest. .planning/REQUIREMENTS.md is byte-unchanged and
# PRIV-05 remains unchecked.
requirements-completed: []

coverage:
  - id: D1
    description: "A COOKIE-typed injection point's originalValue is absent from the serialized IssueDetails.detail under STRICT and BALANCED, present verbatim under OFF, with the cookie NAME surviving every mode"
    requirement: "PRIV-05"
    verification:
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/scanner/IssueDetailCookieCarrierTest.kt#cookieOriginalValueIsStrippedUnderStrict"
        status: pass
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/scanner/IssueDetailCookieCarrierTest.kt#cookieOriginalValueIsStrippedUnderBalanced"
        status: pass
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/scanner/IssueDetailCookieCarrierTest.kt#cookieOriginalValueSurvivesUnderOff"
        status: pass
    human_judgment: false
  - id: D2
    description: "The null result is attributable: an identical sentinel on a URL_PARAM point survives STRICT, and the pre-existing header rule fires on a real Cookie: header in the same output"
    requirement: "PRIV-05"
    verification:
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/scanner/IssueDetailCookieCarrierTest.kt#urlParamOriginalValueSurvivesStrict_attributionControl"
        status: pass
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/scanner/IssueDetailCookieCarrierTest.kt#theCookieHeaderPositiveControlFiresInTheSameStrictOutput"
        status: pass
    human_judgment: false
  - id: D3
    description: "The fixture exercises the value's tail, an over-match past the control point turns a named field-scoped assertion red, and the two legitimate STRICT-vs-OFF differences are enumerated so a third cannot hide among them"
    requirement: "PRIV-05"
    verification:
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/scanner/IssueDetailCookieCarrierTest.kt#theSentinelIsNotTheTailOfTheSerializedBlob_nonVacuity"
        status: pass
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/scanner/IssueDetailCookieCarrierTest.kt#theStrippedDetailFieldRetainsEverythingAfterTheControlPoint"
        status: pass
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/scanner/IssueDetailCookieCarrierTest.kt#theOnlyTwoDifferencesBetweenStrictAndOffAreTheEnumeratedControls"
        status: pass
    human_judgment: false
  - id: D4
    description: "SC1 is proven ON the helper, not THROUGH ActiveAiScanner.createConfirmedIssue — the reach gap is carried as a residual and needs a human to accept it"
    verification: []
    human_judgment: true
    rationale: "Whether the source-TEXT pin is an acceptable stand-in for execution coverage of the write site is a judgement about accepted residual risk, not something a test can settle. The pin passes green in exactly the refactor it cannot see."

duration: 40min
completed: 2026-08-27
status: complete
---

# Phase 28 Plan 01: The Issue-Detail Cookie Carrier Summary

**A COOKIE-typed injection point's `originalValue` no longer reaches `AuditIssue.detail()` under STRICT or BALANCED — the control is type-keyed at the write site, and its proof carries an attribution control, a positive control, a non-vacuity guard and a field-scoped content-destruction guard, with the red probe measured on three separate mutations rather than asserted.**

## Performance

- **Duration:** ~40 min (continuation agent; three prior agents died on this phase)
- **Tasks:** 3
- **Commits:** 6 across the plan (3 from tasks 1-2's original run and its recovery, 3 from this continuation)
- **Tests:** 9 → 14 in `IssueDetailCookieCarrierTest`

## Accomplishments

- `ScannerIssueSupport.sanitizeInjectionPointValue` gates on `InjectionType.COOKIE` and `policy.stripCookies`, substituting `INJECTION_VALUE_STRIPPED_MARKER`; every other injection type passes through truncated exactly as before.
- `ScannerIssueSupport.buildActiveIssueDetailLines` is the only producer of active-scan detail lines in the repository; `ActiveAiScanner.createConfirmedIssue` calls it with `RedactionPolicy.fromMode(getSettings().privacyMode)`.
- `IssueDetailCookieCarrierTest` proves SC1, SC2, ATTRIBUTION, POSITIVE CONTROL, NON-VACUITY and CONTENT PRESERVATION, mocks-free, driving the real serializer types rather than a hand-typed JSON envelope.
- The red probe is MEASURED, not claimed: three mutations were applied to the working tree, run, and restored, and every verbatim failure message is recorded below.
- The truncation bound is derived from `ORIGINAL_VALUE_MAX_CHARS` in executable code; the literal `100` appears in no executable line of the test file.

## SC3 — red probe

Both mutations were applied to the working tree, run, and reverted with `git checkout --`. Neither
is committed: after restoration
`git status --porcelain src/main/kotlin/com/six2dez/burp/aiagent/scanner/ScannerIssueSupport.kt src/main/kotlin/com/six2dez/burp/aiagent/scanner/ActiveAiScanner.kt`
printed nothing and `IssueDetailCookieCarrierTest` was green.

### Mutation A — the control revert

The gate's condition is negated so it can never fire in a stripping mode. Applied at
`src/main/kotlin/com/six2dez/burp/aiagent/scanner/ScannerIssueSupport.kt:68`:

```diff
-            policy.stripCookies && point.type == InjectionType.COOKIE -> INJECTION_VALUE_STRIPPED_MARKER
+            !policy.stripCookies && point.type == InjectionType.COOKIE -> INJECTION_VALUE_STRIPPED_MARKER
```

Command:

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test --tests 'com.six2dez.burp.aiagent.scanner.IssueDetailCookieCarrierTest'
```

**Designated red probe:** `com.six2dez.burp.aiagent.scanner.IssueDetailCookieCarrierTest.cookieOriginalValueIsStrippedUnderStrict`
— the first `assertFalse` in that method, named as such in the test class KDoc so a future reader
mutating this control knows which line is supposed to catch them.

Measured TWICE, because task 3 changed the population and a count carried across that change would
have drifted inside a single plan — the exact failure mode standing rule (vi) exists to prevent:

| Run | Class size | Result |
|---|---|---|
| RUN 1 — after task 2, before task 3 | 9 tests | `9 tests completed, 3 failed` |
| RUN 2 — after task 3 | 14 tests | `14 tests completed, 7 failed` |

Verbatim JUnit failure messages, captured in RUN 1, all three:

```
IssueDetailCookieCarrierTest > cookieOriginalValueIsStrippedUnderStrict() FAILED
    org.opentest4j.AssertionFailedError at IssueDetailCookieCarrierTest.kt:104

org.opentest4j.AssertionFailedError: STRICT: the COOKIE-typed injection point's originalValue must be ABSENT from the serialized issue detail, but the sentinel 'apple-orange-basket-lantern' was present. ==> expected: <false> but was: <true>
```

```
IssueDetailCookieCarrierTest > cookieOriginalValueIsStrippedUnderBalanced() FAILED
    org.opentest4j.AssertionFailedError at IssueDetailCookieCarrierTest.kt:120

org.opentest4j.AssertionFailedError: BALANCED: the COOKIE-typed injection point's originalValue must be ABSENT from the serialized issue detail, but the sentinel 'apple-orange-basket-lantern' was present. ==> expected: <false> but was: <true>
```

```
IssueDetailCookieCarrierTest > cookieOriginalValueSurvivesUnderOff() FAILED
    org.opentest4j.AssertionFailedError at IssueDetailCookieCarrierTest.kt:136

org.opentest4j.AssertionFailedError: OFF: the COOKIE-typed injection point's originalValue must be PRESENT verbatim in the serialized issue detail, but the sentinel 'apple-orange-basket-lantern' was missing — the control is policy-driven, not an unconditional rewrite. ==> expected: <true> but was: <false>
```

The OFF assertion going red alongside the two stripping ones is the probe working as designed, not
collateral: negating the condition does not disable the control, it INVERTS it, so the mode defined
as applying no control starts applying one. A revert that merely removed the gate would turn the
first two red and leave the third green; a revert that made the control unconditional would turn
only the third red. The three together distinguish those cases.

RUN 2 adds four more, all from task 3, and all for the same structural reason — each reads the
COOKIE carrier's value under a policy-determined mode:
`theStrippedMarkerIsNotTruncated`, `theStrippedDetailFieldRetainsEverythingAfterTheControlPoint`,
`theOnlyTwoDifferencesBetweenStrictAndOffAreTheEnumeratedControls` and
`theSentinelIsNotTheTailOfTheSerializedBlob_nonVacuity`.

**Assertions that did NOT go red, and why that is expected.** This is the probe's reach, stated
rather than left to be inferred. Seven of the fourteen stayed green because none of them reads the
COOKIE carrier's value under a policy-determined mode:

| Assertion | Why it is blind to mutation A |
|---|---|
| `sentinelsAreDistinctAndNonOverlapping` | A fixture guard over two constants. Reads no produced output at all. |
| `urlParamOriginalValueSurvivesStrict_attributionControl` | Carries the identical sentinel on a `URL_PARAM`-typed point. The mutated condition still cannot match a non-COOKIE type, so the sentinel survives either way — which is exactly the property that makes this an attribution control rather than a duplicate. |
| `theCookieHeaderPositiveControlFiresInTheSameStrictOutput` | Measures the PRE-EXISTING header rule inside `requestResponses`, which this mutation does not touch. |
| `theCookieNameSurvivesEveryMode` | Reads the injection point's NAME on the Injection Point line, never its value. |
| `theRequestResponsesListIsNotAlteredByTheControl` | Reads the `IssueDetails` object BEFORE serialization; the control never writes there. |
| `theOriginalValueBoundIsDerivedFromTheConstant` | Drives a `URL_PARAM`-typed point, so it measures the pass-through branch the mutation does not reach. |
| `theWriteSiteReadsTheLivePolicy` | Asserts over `ActiveAiScanner.kt` source text. Mutation A edits `ScannerIssueSupport.kt`. |

### Mutation B — the policy-plumbing revert

The write site's policy argument is hard-coded to a constant instead of reading the operator's live
setting. This targets a DIFFERENT failure mode from A: A proves the gate's CONDITION is
load-bearing, B proves the POLICY actually reaches the gate rather than a constant standing in for
it. Applied at `src/main/kotlin/com/six2dez/burp/aiagent/scanner/ActiveAiScanner.kt:1244`:

```diff
-                RedactionPolicy.fromMode(getSettings().privacyMode),
+                RedactionPolicy.fromMode(PrivacyMode.OFF),
```

Command:

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test --tests 'com.six2dez.burp.aiagent.scanner.IssueDetailCookieCarrierTest'
```

Result: `9 tests completed, 1 failed`.

**Failing assertion:** `com.six2dez.burp.aiagent.scanner.IssueDetailCookieCarrierTest.theWriteSiteReadsTheLivePolicy`

Verbatim JUnit failure message:

```
IssueDetailCookieCarrierTest > theWriteSiteReadsTheLivePolicy() FAILED
    org.opentest4j.AssertionFailedError at IssueDetailCookieCarrierTest.kt:254

org.opentest4j.AssertionFailedError: PIN: the write site must derive its policy from the OPERATOR'S LIVE SETTING, not from a constant. Expected the argument text `getSettings().privacyMode`, found: `RedactionPolicy.fromMode(PrivacyMode.OFF),` ==> expected: <true> but was: <false>
```

**MUTATION B WAS NOT DETECTED BY ANY BEHAVIOURAL ASSERTION, AND THAT IS RECORDED HERE RATHER THAN
GLOSSED.** When B was first measured — against the file as task 1 left it, before the pin existed —
**all eight behavioural tests stayed GREEN**. The cause is structural, not incidental: every one of
them calls `ScannerIssueSupport.buildActiveIssueDetailLines(...)` directly and constructs its own
`RedactionPolicy`, so no assertion in this file executes `ActiveAiScanner.createConfirmedIssue` and
none of them can observe a change at that call site. The single detection above comes from
`theWriteSiteReadsTheLivePolicy`, the source-TEXT pin added by task 2 precisely because B was
measured as undetected. **B is therefore reported as covered by a pin, NOT as covered by the
tests.**

**Assertions that did NOT go red for mutation B, and why:** all eight behavioural tests, for the one
reason above — they never reach the mutated call site. The pin's non-vacuity was derived rather than
assumed: `ActiveAiScanner.kt`'s comment-stripped `RedactionPolicy.fromMode(` count was 0 before this
phase, so a gate requiring exactly 1 measures a change this phase makes rather than restating a
constant that was already true.

## Content-destruction probe — proving the new guard is not vacuous

A guard that passes is only worth what it would catch. Task 3's content-destruction guard was
therefore MEASURED against the regression class it exists for, rather than shipped green and
assumed effective.

The mutation is an OVER-MATCH: the control's span is widened so it eats the line FOLLOWING the
control point, which is exactly the shape phase 27 round 4 shipped. Applied in
`ScannerIssueSupport.buildActiveIssueDetailLines`:

```diff
-        detailLines.add("  Payload Used: ${payload.value.take(PAYLOAD_VALUE_MAX_CHARS)}")
+        if (!(policy.stripCookies && point.type == InjectionType.COOKIE)) {
+            detailLines.add("  Payload Used: ${payload.value.take(PAYLOAD_VALUE_MAX_CHARS)}")
+        }
```

Result: `14 tests completed, 2 failed` —
`theStrippedDetailFieldRetainsEverythingAfterTheControlPoint` and
`theOnlyTwoDifferencesBetweenStrictAndOffAreTheEnumeratedControls`.

**EVERY LEAK-ONLY ASSERTION IN THE FILE STAYED GREEN.** The value is still absent under STRICT and
BALANCED, still present under OFF, the name still survives, the attribution and positive controls
still fire. That is the measurement that justifies these two guards existing at all: an absence
assertion is STRUCTURALLY unable to see content destruction, so a phase that ships only leak-only
assertions ships blind to this class.

Verbatim JUnit failure message from the field-scoped guard, showing the divergence index and the
bounded windows it prints:

```
IssueDetailCookieCarrierTest > theStrippedDetailFieldRetainsEverythingAfterTheControlPoint() FAILED
    org.opentest4j.AssertionFailedError at IssueDetailCookieCarrierTest.kt:342

org.opentest4j.AssertionFailedError: CONTENT DESTRUCTION, FIELD-SCOPED to `detail`: the STRICT `detail` must equal the OFF `detail` under EXACTLY ONE substitution — the sentinel became '[STRIPPED]' — and under no other. They first diverge at index 195.
  expected: ...me<br>&nbsp;&nbsp;Original Value: [STRIPPED]<br>&nbsp;&nbsp;Payload Used: benign-probe-payload<br>&nbsp;&nbsp;Detection ...
  actual:   ...me<br>&nbsp;&nbsp;Original Value: [STRIPPED]<br>&nbsp;&nbsp;Detection Method: REFLECTION<br>&nbsp;&nbsp;Evidence: eviden...
The divergence is EITHER a real over-match that ate content past the control's span, OR a second redaction rule firing on a fixture token. Diagnose WHICH and record it in 28-01-SUMMARY.md. Relaxing this assertion is not one of the two options: relaxing a guard that was red on arrival is exactly how phase 27 round 4 shipped a content-destruction regression. ==> expected: <...> but was: <...>
```

And from the enumeration guard, which correctly classified the same damage as a THIRD, unenumerated
difference rather than absorbing it:

```
IssueDetailCookieCarrierTest > theOnlyTwoDifferencesBetweenStrictAndOffAreTheEnumeratedControls() FAILED
    org.opentest4j.AssertionFailedError at IssueDetailCookieCarrierTest.kt:384

org.opentest4j.AssertionFailedError: A THIRD, UNENUMERATED DIFFERENCE between the OFF and STRICT blobs was found. Applying the two KNOWN substitutions to the OFF blob — the sentinel becomes '[STRIPPED]' inside `detail`, and 'Cookie: wibble=harbor-pebble-window-thistle' becomes 'Cookie: [STRIPPED]' inside requestResponses — must reproduce the STRICT blob EXACTLY. A residue remains, first differing at index 232.
  predicted: ...me<br>&nbsp;&nbsp;Original Value: [STRIPPED]<br>&nbsp;&nbsp;Payload Used: benign-probe-payload<br>&nbsp;&nbsp;Detection ...
  observed:  ...me<br>&nbsp;&nbsp;Original Value: [STRIPPED]<br>&nbsp;&nbsp;Detection Method: REFLECTION<br>&nbsp;&nbsp;Evidence: eviden...
An unenumerated difference in this blob is either an over-match or a rule nobody accounted for. Both are FINDINGS, not fixture noise. Diagnose which, and do not widen the substitution list to absorb it. ==> expected: <...> but was: <...>
```

Restored with `git checkout --`; `git status --porcelain` printed nothing and the class returned to
green.

### Why the guard is FIELD-scoped, and why that is not a weakening

The measured STRICT-vs-OFF diff on this fixture has exactly TWO differences, and only one of them
belongs to this plan:

1. Inside `detail`: `Original Value: apple-orange-basket-lantern` → `Original Value: [STRIPPED]`. **This plan's control.**
2. Inside `requestResponses[0].request`: `Cookie: wibble=harbor-pebble-window-thistle` → `Cookie: [STRIPPED]`. **The PRE-EXISTING header rule**, and the positive control `theCookieHeaderPositiveControlFiresInTheSameStrictOutput` REQUIRES it to fire.

The second difference sits AFTER the sentinel in the same serialized string, so a WHOLE-BLOB form of
"the STRICT output equals the OFF output under one substitution" is FALSE BY CONSTRUCTION here — red
on arrival, for a specification reason rather than a code reason. The cheapest repair for a guard
that is red on arrival is to relax it until it no longer detects anything, which is precisely how
round 4 shipped its regression. So the guard is SCOPED to the field this plan's control owns, and
the whole-blob level is covered separately by
`theOnlyTwoDifferencesBetweenStrictAndOffAreTheEnumeratedControls`, which names both differences
individually and fails on a third. Nothing was relaxed; the invariant was stated over the domain
where it is true, and a second invariant was added over the domain where it is not.

The extractor is pinned against vacuity BEFORE any comparison runs: the OFF-mode `detail` must be
non-blank AND must contain the sentinel, so an extractor that silently returned an empty string
cannot make the equality pass by comparing nothing to nothing.

Measured, not assumed: the host `shop.example` is NOT anonymized under STRICT in this fixture, so
host anonymization does not add a third difference. That was confirmed by dumping both blobs before
the assertions were written, rather than predicted.

## Residual — SC1 reach

**(a) Which of the two proofs actually shipped.** The SOURCE-LEVEL PIN
(`theWriteSiteReadsTheLivePolicy`) shipped. A live assertion routed through
`ActiveAiScanner.createConfirmedIssue` did NOT ship. SC1 is proven ON
`ScannerIssueSupport.buildActiveIssueDetailLines`, not THROUGH the write site.

**(b) What the pin cannot see.** It asserts over source TEXT. A refactor that keeps the literal
`RedactionPolicy.fromMode(getSettings().privacyMode)` on the page while routing a DIFFERENT policy
object into the call — an intermediate variable reassigned between the lookup and the call, a
wrapper that discards its argument, a second overload selected by a changed signature — passes this
pin unchanged and green. The pin measures that a string is present in a file; it does not measure
that a policy flows.

**(c) Phase 28 CARRIES this residual; it does not close it.** The difference between "proven on the
helper" and "proven through the write site" is the whole reachability question `AR-27-08` turns on,
and this plan has not answered it. Closing it needs an execution-level assertion over
`createConfirmedIssue`, which needs a Montoya-free seam at that method that does not exist today.
Recorded as carried, not reported as done. This is the same obligation plan 28-02 task 2 places on
its supervisor stub, for the same reason.

## Requirements

`requirements-completed` is deliberately EMPTY. The plan's frontmatter names `PRIV-05`, but this
plan closes only the WRITE half of `AR-27-08`. Verified rather than asserted:

- `shasum -a 256 .planning/REQUIREMENTS.md` → `9b3219662ec0d007c1c82d64eed3ef2698bd306ce69f01205ac9bbc3f42fcfb4`, which begins `9b3219662ec0d007` as the plan's gate requires. The file is byte-unchanged.
- `.planning/REQUIREMENTS.md:23` still reads `- [ ] **PRIV-05**`.

Plans 28-02 and 28-03 carry the rest, and the residual above is outstanding regardless.

## Deviations from Plan

None affecting behaviour, thresholds, fixtures or measurements. Four recording-level notes:

1. **Mutation B's undetected branch is the one that ran.** Task 2's action states that if B is not
   detected by an existing assertion, the executor must record that explicitly and add a
   source-level pin instead. B was measured as undetected, so the pin shipped and the gap is
   recorded in `## Residual — SC1 reach` rather than silently reported as coverage.
2. **Both mutations were re-run by this continuation agent** to capture the verbatim JUnit failure
   messages the acceptance criteria require; the prior agent recorded the counts but stalled before
   capturing the text. The FIGURES reproduce the earlier measurement exactly.
3. **Mutation A was measured a second time after task 3** (RUN 2 above). Carrying RUN 1's "3 of 8"
   into a 14-test class would have been a count that drifted inside a single plan — the drift class
   standing rule (vi) exists to stop, and the class the phase-27 round-4 post-mortem names. The test
   class KDoc was updated with the re-measured figure in the same commit.
4. **A third mutation was added that the plan did not require** — the over-match probe in
   `## Content-destruction probe`. Task 3 specifies the guard but not a measurement of it, and a
   content-destruction guard that has never been shown to go red is exactly the artifact whose green
   result cannot be trusted. Cost was one minute; it converts "this guard should catch an
   over-match" from a claim into a measurement.

## Known Stubs

None. No hardcoded empty values, placeholder text, TODO/FIXME markers or unwired components were
introduced by this plan. No test is skipped and every `<verify>` command in the plan was run.

## Verification

| Gate | Result |
|---|---|
| `./gradlew test --tests '...IssueDetailCookieCarrierTest'` | green, 14 tests |
| `./gradlew ktlintCheck detekt` | green |
| `./gradlew test` (full suite) | green |
| `detekt-baseline.xml` | byte-unchanged (`git diff --stat` empty) |
| `.planning/REQUIREMENTS.md` | byte-unchanged, hash begins `9b3219662ec0d007` |
| `git diff --stat ad2ca90 HEAD` | exactly three source files plus the SUMMARY |
| `grep -vE '^\s*(//\|\*\|/\*)' ...Test.kt \| grep -c 'ORIGINAL_VALUE_MAX_CHARS'` | `4` (≥ 2 required) |
| `grep -vE '^\s*(//\|\*\|/\*)' ...Test.kt \| grep -cE '\b100\b'` | `0` |
| `grep -vE '^\s*(//\|\*\|/\*)' ActiveAiScanner.kt \| grep -c 'RedactionPolicy\.fromMode('` | `1` |

`./gradlew check` was NOT used as a gate. It is red for a maintainer-accepted reason unrelated to
this plan (`jacocoTestCoverageVerification`, redact BRANCH 0.92784 against a 0.930 floor, where the
deciding branch is a wall-clock guard whose colour depends on machine load).

## Self-Check: PASSED

Files claimed as created or modified, checked on disk:

- `src/test/kotlin/com/six2dez/burp/aiagent/scanner/IssueDetailCookieCarrierTest.kt` — FOUND
- `src/main/kotlin/com/six2dez/burp/aiagent/scanner/ScannerIssueSupport.kt` — FOUND
- `src/main/kotlin/com/six2dez/burp/aiagent/scanner/ActiveAiScanner.kt` — FOUND
- `.planning/phases/28-the-issue-detail-cookie-carrier-auditissue-detail-scanner-is/28-01-SUMMARY.md` — FOUND

Commits claimed, checked in `git log`:

| Commit | Subject |
|---|---|
| `c07194d` | `test(28-01): add failing test for the issue-detail COOKIE carrier` (task 1, RED) |
| `3faa9fb` | `feat(28-01): strip COOKIE-typed originalValue from the issue detail under STRICT/BALANCED` (task 1, GREEN) |
| `8fca62e` | `test(28-01): SC3 red-probe record and the write-site source pin (recovered)` (task 2, code) |
| `331ccb5` | `docs(28-01): record the SC3 red probe and the SC1 reach residual` (task 2, record) |
| `c200e9b` | `test(28-01): non-vacuity, field-scoped content destruction and a derived bound` (task 3) |
| `c4aba27` | `docs(28-01): re-measure the red probe against the 14-test class` |
| `c3246ad` | `docs(28-01): complete the summary — task 3, the over-match probe and verification` |

All FOUND.

Final gate run, full suite: `JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew ktlintCheck detekt test`
→ `BUILD SUCCESSFUL in 3m 25s`. 177 test classes, 1272 tests, 0 failures. The known `RedactionTest`
CPU-load flake did not occur on this run.

TDD gate sequence for this plan, verified in `git log`: `test(...)` at `c07194d` (RED), `feat(...)`
at `3faa9fb` (GREEN). No REFACTOR commit was needed.

Working tree clean; no mutation from any of the three probes is committed.
