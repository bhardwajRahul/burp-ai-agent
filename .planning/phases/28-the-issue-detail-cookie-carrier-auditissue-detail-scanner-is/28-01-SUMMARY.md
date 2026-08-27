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
  - "IssueDetailCookieCarrierTest — the control's proof, carrying its own attribution control, positive control, non-vacuity guard and content-destruction guard"
  - "A measured red probe for the control, with both mutations' verbatim failure messages recorded"
affects: [28-02, 28-03, PRIV-05, AR-27-08]

actuals:
  tokens: 21000
  tasks: 3
  commits: 5

tech-stack:
  added: []
  patterns:
    - "Type-keyed privacy control at the write site — the last point in the route still holding InjectionType"
    - "Source-TEXT pin standing in, explicitly and named as weaker, for missing execution coverage"
    - "Field-scoped content-destruction guard: equality under exactly one known substitution, not a leak-only absence assertion"

key-files:
  created:
    - src/test/kotlin/com/six2dez/burp/aiagent/scanner/IssueDetailCookieCarrierTest.kt
  modified:
    - src/main/kotlin/com/six2dez/burp/aiagent/scanner/ScannerIssueSupport.kt
    - src/main/kotlin/com/six2dez/burp/aiagent/scanner/ActiveAiScanner.kt

key-decisions:
  - "D-28-01: the control goes at the write site (ScannerIssueSupport.buildActiveIssueDetailLines), keyed on InjectionType.COOKIE — every downstream site holds only a rendered string and would inherit the same blindness phase 27 measured in cookieTypedParamRegex"
  - "The content-destruction guard is FIELD-scoped to `detail`, not blob-scoped: a blob-scoped form is false by construction on this fixture because the positive-control Cookie: header is legitimately stripped by a pre-existing rule in the same STRICT output"
  - "Mutation B is detected ONLY by a source-TEXT pin, and the SUMMARY names that as weaker than an execution assertion rather than reporting it as reach this file does not have"

patterns-established:
  - "Red probe with a NAMED designated assertion recorded in the test class KDoc, so a future mutator knows which line is supposed to catch them"
  - "Enumerated-difference assertion: two known STRICT-vs-OFF differences named individually, a third is a finding rather than noise"

# PRIV-05 is NOT closed by this plan. This plan closes the WRITE half of AR-27-08 only;
# plans 28-02 and 28-03 carry the rest. .planning/REQUIREMENTS.md is byte-unchanged and
# PRIV-05 remains unchecked.
requirements-completed: []

duration: 35min
completed: 2026-08-27
status: complete
---

# Phase 28 Plan 01: The Issue-Detail Cookie Carrier Summary

**A COOKIE-typed injection point's `originalValue` no longer reaches `AuditIssue.detail()` under STRICT or BALANCED — the control is type-keyed at the write site, and its proof carries an attribution control, a positive control, a non-vacuity guard and a field-scoped content-destruction guard, with the red probe measured on both a control revert and a policy-plumbing revert.**

## Performance

- **Duration:** ~35 min (continuation agent; two prior agents died on this plan)
- **Tasks:** 3
- **Commits:** 5 (3 from tasks 1-2's original run and its recovery, plus this continuation's)

## Accomplishments

- `ScannerIssueSupport.sanitizeInjectionPointValue` gates on `InjectionType.COOKIE` and `policy.stripCookies`, substituting `INJECTION_VALUE_STRIPPED_MARKER`; every other injection type passes through truncated exactly as before.
- `ScannerIssueSupport.buildActiveIssueDetailLines` is now the only producer of active-scan detail lines in the repository; `ActiveAiScanner.createConfirmedIssue` calls it with `RedactionPolicy.fromMode(getSettings().privacyMode)`.
- `IssueDetailCookieCarrierTest` proves SC1, SC2, ATTRIBUTION, POSITIVE CONTROL, NON-VACUITY and CONTENT PRESERVATION, mocks-free, driving the real serializer types.
- The red probe is measured, not asserted: both mutations were applied to the working tree, run, and restored, and both verbatim failure messages are recorded below.

## SC3 — red probe

Both mutations were applied to the working tree, run, and reverted with `git checkout --`. Neither
mutation is committed: after restoration
`git status --porcelain src/main/kotlin/com/six2dez/burp/aiagent/scanner/ScannerIssueSupport.kt src/main/kotlin/com/six2dez/burp/aiagent/scanner/ActiveAiScanner.kt`
printed nothing and `IssueDetailCookieCarrierTest` was green (`BUILD SUCCESSFUL`).

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

Result: `9 tests completed, 3 failed`.

**Designated red probe:** `com.six2dez.burp.aiagent.scanner.IssueDetailCookieCarrierTest.cookieOriginalValueIsStrippedUnderStrict`
— the first `assertFalse` in that method, named as such in the test class KDoc so a future reader
mutating this control knows which line is supposed to catch them.

Verbatim JUnit failure messages, all three:

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

org.opentest4j.AssertionFailedError: OFF: the COOKIE-typed injection point's originalValue must be PRESENT verbatim in the serialized issue detail, but the sentinel 'apple-orange-basket-lantern' was missing — the control is policy-driven, not an unconditional rewrite.  ==> expected: <true> but was: <false>
```

The OFF assertion going red alongside the two stripping ones is the probe working as designed, not
collateral: negating the condition does not disable the control, it inverts it, so the mode defined
as applying no control starts applying one. A revert that only removed the gate would turn the first
two red and leave the third green; a revert that made the control unconditional would turn only the
third red. The three together distinguish those cases.

**Assertions that did NOT go red, and why that is expected** — this is the probe's reach, stated
rather than left to be inferred. Six of the nine stayed green because none of them reads the COOKIE
carrier's value under a policy-determined mode:

| Assertion | Why it is blind to mutation A |
|---|---|
| `sentinelsAreDistinctAndNonOverlapping` | A fixture guard over two constants. Reads no produced output at all. |
| `urlParamOriginalValueSurvivesStrict_attributionControl` | Carries the identical sentinel on a `URL_PARAM`-typed point. The mutated condition still cannot match a non-COOKIE type, so the sentinel survives either way — which is exactly the property that makes this an attribution control rather than a duplicate. |
| `theCookieHeaderPositiveControlFiresInTheSameStrictOutput` | Measures the PRE-EXISTING header rule inside `requestResponses`, which this mutation does not touch. |
| `theCookieNameSurvivesEveryMode` | Reads the injection point's NAME on the Injection Point line, never its value. |
| `theRequestResponsesListIsNotAlteredByTheControl` | Reads the `IssueDetails` object BEFORE serialization; the control never writes there. |
| `theWriteSiteReadsTheLivePolicy` | Asserts over `ActiveAiScanner.kt` source text. Mutation A edits `ScannerIssueSupport.kt`. |

### Mutation B — the policy-plumbing revert

The write site's policy argument is hard-coded to a constant instead of reading the operator's live
setting. This targets a different failure mode from A: A proves the gate's CONDITION is load-bearing,
B proves the POLICY actually reaches the gate rather than a constant standing in for it. Applied at
`src/main/kotlin/com/six2dez/burp/aiagent/scanner/ActiveAiScanner.kt:1244`:

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
measured as undetected. B is therefore reported as covered by a pin, NOT as covered by the tests.

**Assertions that did NOT go red for mutation B, and why:** all eight behavioural tests, for the one
reason above — they never reach the mutated call site. The pin's non-vacuity was derived rather than
assumed: `ActiveAiScanner.kt`'s comment-stripped `RedactionPolicy.fromMode(` count was 0 before this
phase, so a gate requiring exactly 1 measures a change this phase makes rather than restating a
constant that was already true.

## Residual — SC1 reach

**(a) Which of the two proofs actually shipped.** The SOURCE-LEVEL PIN
(`theWriteSiteReadsTheLivePolicy`) shipped. A live assertion routed through
`ActiveAiScanner.createConfirmedIssue` did NOT ship. SC1 is proven ON
`ScannerIssueSupport.buildActiveIssueDetailLines`, not THROUGH the write site.

**(b) What the pin cannot see.** It asserts over source TEXT. A refactor that keeps the literal
`RedactionPolicy.fromMode(getSettings().privacyMode)` on the page while routing a DIFFERENT policy
object into the call — an intermediate variable reassigned between the lookup and the call, a wrapper
that discards its argument, a second overload selected by a changed signature — passes this pin
unchanged and green. The pin measures that a string is present in a file; it does not measure that a
policy flows.

**(c) Phase 28 CARRIES this residual; it does not close it.** The difference between "proven on the
helper" and "proven through the write site" is the whole reachability question `AR-27-08` turns on,
and this plan has not answered it. Closing it needs an execution-level assertion over
`createConfirmedIssue`, which needs a Montoya-free seam at that method that does not exist today.
Recorded as carried, not reported as done. This is the same obligation plan 28-02 task 2 places on
its supervisor stub, for the same reason.

## Requirements

`requirements-completed` is deliberately EMPTY. The plan's frontmatter names `PRIV-05`, but this
plan closes only the WRITE half of `AR-27-08`. `.planning/REQUIREMENTS.md` is byte-unchanged and
PRIV-05 remains `[ ]` — plans 28-02 and 28-03 carry the rest, and the residual above is outstanding
regardless.

## Deviations from Plan

None affecting behaviour, thresholds, fixtures or measurements.

Two recording-level notes, both within the plan's own instructions:

1. Task 2's action states that if mutation B is not detected by an existing assertion, the executor
   must record that explicitly and add a source-level pin instead. B was measured as undetected, so
   that branch is the one that ran: the pin shipped and the gap is recorded in `## Residual — SC1
   reach` rather than silently reported as coverage.
2. Both mutations were re-run by this continuation agent to capture the verbatim JUnit failure
   messages the acceptance criteria require. The FIGURES match the earlier measurement exactly —
   mutation A turns exactly the three value-reading assertions red, mutation B leaves every
   behavioural assertion green — with the only difference being that the class now holds nine tests
   rather than eight, the ninth being the pin, which is why B now shows one failure instead of none.

## Self-Check

Pending — completed at the end of this plan.
