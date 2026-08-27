---
phase: 28-the-issue-detail-cookie-carrier-auditissue-detail-scanner-is
plan: 04
subsystem: privacy
tags: [redaction, scanner, montoya, kotlin, junit5, cookie-carrier, priv-05]

requires:
  - phase: 28-the-issue-detail-cookie-carrier-auditissue-detail-scanner-is
    provides: "28-01's write-site control (`sanitizeInjectionPointValue`), `INJECTION_VALUE_STRIPPED_MARKER`, and `IssueDetailCookieCarrierTest`'s mocks-free fixture harness"
  - phase: 27-security-findings-round-4
    provides: "`AR-27-08` — the measured issue-detail cookie carrier and the measurement that `Redaction` is structurally blind to it"
provides:
  - "`ScannerIssueSupport.sanitizeRenderedPayload(point, payload, policy)` — a type-keyed whole-payload strip on the `Payload Used:` detail line"
  - "Route 1 of `AR-27-08` (`CR-01`) closed: a COOKIE point's value is absent from BOTH controlled detail lines under STRICT and BALANCED"
  - "A production-DERIVED payload fixture — `PayloadGenerator.generateContextAwarePayloads` — replacing the blind hand-typed one that made 14 green tests uninformative"
  - "`withDetailLineControlsApplied` — one prefix-qualified prediction helper shared by the content-destruction guard and the whole-blob difference enumeration"
  - "A corrected `PAYLOAD_VALUE_MAX_CHARS` KDoc (D-28-08 first site), append-and-amend with the false premise kept verbatim"
affects: [28-05, 28-06, AiScanCheck, verify-work, secure-phase]

actuals:
  tokens: 21723
  tasks: 3
  commits: 4

tech-stack:
  added: []
  patterns:
    - "Sibling gates: a second privacy control is a sibling function in the same file with the same `when` shape and the same marker constant, not a new mechanism"
    - "Prefix-qualified prediction: a difference enumeration substitutes rendered LINE PREFIXES, never a bare sentinel, so an unenumerated occurrence stands as a residue instead of being absorbed"
    - "Production-derived fixtures: a test payload is built by calling the production generator, with a guard asserting the interpolation actually happened"

key-files:
  created: []
  modified:
    - src/main/kotlin/com/six2dez/burp/aiagent/scanner/ScannerIssueSupport.kt
    - src/test/kotlin/com/six2dez/burp/aiagent/scanner/IssueDetailCookieCarrierTest.kt

key-decisions:
  - "The payload gate is TYPE-KEYED and strips the payload WHOLESALE (D-28-07). It reads `point.type` and `policy.stripCookies` and never the text of the value inside the payload."
  - "No emptiness guard on the gate: an empty-valued COOKIE point still renders the marker, or the point's type becomes observable as a rendering difference."
  - "One marker vocabulary (D-28-05): `INJECTION_VALUE_STRIPPED_MARKER` is referenced, never retyped, and no payload-specific marker constant was added."
  - "The prediction helper uses TWO PREFIX-QUALIFIED substitutions rather than the plan's literal ordering hazard, because a global sentinel replace would ABSORB a third uncontrolled route — this round's own failure class. The ordering probe was run for BOTH forms and both measurements are recorded."
  - "`ScannerIssueSupport.kt`'s single-producer KDoc claim was deliberately NOT corrected here (T-28-21) — plan 28-05 corrects it, because only that plan can name the second producer's control accurately."

patterns-established:
  - "Sibling gate: `sanitizeRenderedPayload` mirrors `sanitizeInjectionPointValue` line-for-line in shape, so a reader meets one control applied twice"
  - "Honest helper reach: a filtering assertion's failure message states the list it actually filters and names what it cannot see"
  - "Append-and-amend KDoc correction: prior text kept verbatim under a dated supersession marker, so a reader can see what was believed and why it was wrong"

requirements-completed: []

coverage:
  - id: D1
    description: "Under STRICT and BALANCED the `Payload Used:` line of the active-scan issue detail carries no byte of a COOKIE-typed point's `originalValue`, measured end to end through serialize -> `Redaction.apply` -> the extracted `detail` field, with a payload built the way production builds it."
    requirement: "PRIV-05"
    verification:
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/scanner/IssueDetailCookieCarrierTest.kt#cookiePayloadIsStrippedUnderStrict"
        status: pass
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/scanner/IssueDetailCookieCarrierTest.kt#cookiePayloadIsStrippedUnderBalanced"
        status: pass
    human_judgment: false
  - id: D2
    description: "The payload control is policy-driven, not an unconditional rewrite: under OFF the rendered payload survives verbatim, and a URL_PARAM point carrying the identical value and payload is untouched under STRICT."
    requirement: "PRIV-05"
    verification:
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/scanner/IssueDetailCookieCarrierTest.kt#cookiePayloadSurvivesUnderOff"
        status: pass
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/scanner/IssueDetailCookieCarrierTest.kt#urlParamPayloadSurvivesStrict_attributionControl"
        status: pass
    human_judgment: false
  - id: D3
    description: "The gate is type-keyed and never shape-keyed: a payload carrying only a percent-encoded transformation of the value is still stripped, an empty-valued COOKIE point still renders the marker on both lines, and the marker is never truncated."
    requirement: "PRIV-05"
    verification:
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/scanner/IssueDetailCookieCarrierTest.kt#aCookiePayloadCarryingOnlyAnEncodedFormOfTheValueIsStillStripped"
        status: pass
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/scanner/IssueDetailCookieCarrierTest.kt#anEmptyValuedCookiePointStillRendersTheMarkerOnBothLines"
        status: pass
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/scanner/IssueDetailCookieCarrierTest.kt#thePayloadStrippedMarkerIsNotTruncated"
        status: pass
    human_judgment: false
  - id: D4
    description: "The control destroys no content past its span: the STRICT `detail` equals the OFF `detail` under exactly two prefix-qualified substitutions, and the whole-blob difference enumeration names three differences with no residue."
    requirement: "PRIV-05"
    verification:
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/scanner/IssueDetailCookieCarrierTest.kt#theStrippedDetailFieldRetainsEverythingAfterTheControlPoint"
        status: pass
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/scanner/IssueDetailCookieCarrierTest.kt#theOnlyThreeDifferencesBetweenStrictAndOffAreTheEnumeratedControls"
        status: pass
    human_judgment: false
  - id: D5
    description: "SC3 for route 1: the control was verified to FAIL before it was verified to pass. Three mutations plus a two-form ordering probe, each applied to the working tree, run, and reverted, with every failure message recorded verbatim."
    requirement: "PRIV-05"
    verification:
      - kind: manual_procedural
        ref: "28-04-SUMMARY.md#sc3--the-red-probe-for-the-payload-control"
        status: pass
    human_judgment: true
    rationale: "A red probe is evidence a human reads and judges. Whether the recorded mutations actually exercise the control -- rather than merely producing red output -- is not decidable by a passing test, and this phase exists because a prior round's closure claim outran its evidence."

duration: 28min
completed: 2026-08-27
status: complete
---

# Phase 28 Plan 04: Route 1 of `AR-27-08` — The `Payload Used:` Cookie Carrier Summary

**A type-keyed whole-payload strip (`ScannerIssueSupport.sanitizeRenderedPayload`) closes the leak at `ScannerIssueSupport.kt:121`, where a COOKIE point's `PayloadGenerator`-derived payload re-emitted the exact bytes the line above had just stripped — proven by a production-derived fixture and a five-run red probe.**

## Performance

- **Duration:** 28 min
- **Started:** 2026-08-27T14:57:00Z
- **Completed:** 2026-08-27T15:25:00Z
- **Tasks:** 3
- **Files modified:** 2

## Accomplishments

- **Route 1 of `AR-27-08` (`CR-01`) is closed.** `sanitizeRenderedPayload(point, payload, policy)` sits directly after its sibling `sanitizeInjectionPointValue`, fires on `policy.stripCookies && point.type == InjectionType.COOKIE`, and writes the shared `INJECTION_VALUE_STRIPPED_MARKER`. Line 121 passes the `policy` already in scope from parameter six — no new parameter, no new `RedactionPolicy.fromMode(` call anywhere in the tree.
- **The fixture can now SEE the route it names.** `PAYLOAD` is derived at construction time from `PayloadGenerator().generateContextAwarePayloads(VulnClass.SQLI, DETAIL_SENTINEL, 5)`, exactly as `ActiveAiScanner.kt:511-515` builds it in production. A containment guard asserts the derived value contains the sentinel, so a generator that stops interpolating turns the fixture red instead of silently restoring the blind fixture that produced 14 uninformative greens.
- **The false premise that caused this line to be skipped is corrected on the record** (D-28-08, first site). `PAYLOAD_VALUE_MAX_CHARS`'s KDoc keeps its original paragraph verbatim — including the sentence "the payload is agent-authored, not operator traffic" — under a dated supersession marker that states what was measured instead.
- **The difference enumeration went from two named differences to three**, and its prediction is now built from prefix-qualified substitutions that cannot absorb an unenumerated occurrence.
- **SC3 is discharged with five measured runs**, not one: three mutations plus an ordering probe run in both forms. Every `org.opentest4j.AssertionFailedError` is quoted verbatim below.

## Task Commits

1. **Task 1 (RED): failing tests for the payload carrier** — `da3d195` (test)
2. **Task 1 (GREEN): the type-keyed payload gate** — `064096c` (feat)
3. **Task 2: repair the difference-enumeration guards** — `1ac4d47` (test)
4. **Task 3 / plan metadata: this SUMMARY** — see the final `docs(28-04)` commit

_Task 1 is `type="tracer" tdd="true"`, so it carries the RED and GREEN commits separately. No REFACTOR commit was needed — the GREEN implementation is the shape the plan specified._

## Files Created/Modified

- `src/main/kotlin/com/six2dez/burp/aiagent/scanner/ScannerIssueSupport.kt` — gains `sanitizeRenderedPayload`; line 121 rewired; `PAYLOAD_VALUE_MAX_CHARS` KDoc corrected append-and-amend.
- `src/test/kotlin/com/six2dez/burp/aiagent/scanner/IssueDetailCookieCarrierTest.kt` — production-derived `PAYLOAD`, the `[edge:encoding]` and `[edge:empty]` fixtures, `payloadRenderedFor`, `withDetailLineControlsApplied`, seven new tests, two repaired guards, two renames, class KDoc updated.

## Task 1 — the tracer, end to end

One COOKIE-typed injection point was driven through every layer this round touches: write site ->
`buildActiveIssueDetailLines` -> `IssueUtils.formatIssueDetailHtml` ->
`toolJson.encodeToString(IssueDetails)` -> `Redaction.apply` -> the extracted `detail` field.

### The RED gate, measured before any implementation

Against the un-modified `ScannerIssueSupport`, with the fixture swapped to the production-derived
payload: `21 tests completed, 9 failed`. The designated probe's message shows the leak in the
rendered blob, with the two lines adjacent:

```
org.opentest4j.AssertionFailedError: STRICT / PAYLOAD LINE: the COOKIE-typed injection point's originalValue must be ABSENT from the `detail` field, but the sentinel 'apple-orange-basket-lantern' was present. For a COOKIE point the payload is DERIVED FROM that value, so an uncontrolled 'Payload Used: ' line re-leaks exactly what the 'Original Value: ' line just stripped. Extracted detail: 'Vulnerability confirmed via active testing<br><br>Type:<br>&nbsp;&nbsp;SQLI<br>&nbsp;&nbsp;Injection Point: COOKIE - pumpkin-lantern-name<br>&nbsp;&nbsp;Original Value: [STRIPPED]<br>&nbsp;&nbsp;Payload Used: apple-orange-basket-lantern' AND '1'='1<br>&nbsp;&nbsp;Detection Method: BLIND_BOOLEAN<br>&nbsp;&nbsp;Evidence: evidence-marker-present<br><br>Backend: test-backend<br>Scan: Active<br>Confidence: 90' ==> expected: <false> but was: <true>
```

`Original Value: [STRIPPED]` immediately followed by `Payload Used: apple-orange-basket-lantern' AND '1'='1`
is 28-VERIFICATION's SC1 falsification reproduced from inside the test suite: round 1's mechanism was
sound and its SPAN was one line short.

Two of the seven new tests were GREEN in the RED phase, by design and not by accident:
`cookiePayloadSurvivesUnderOff` and `urlParamPayloadSurvivesStrict_attributionControl` both assert
PASS-THROUGH behaviour, which is unchanged by this round. They are the controls that catch the
opposite mistake — a control that fires unconditionally or ignores the type — so a green result from
them before the implementation is the correct reading, not an unexpected pass.

### The task-1 boundary state — two tests RED by design

The plan predicted this exactly and forbade repairing it here. After the GREEN implementation:
19 of 21 passing, and these two red for a SPECIFICATION reason on correct code. Both predicted the
STRICT output by an UNQUALIFIED whole-string substitution written when exactly one sentinel
occurrence lived inside `detail`, so both expected marker-plus-suffix where the shipped control
writes the marker alone.

**`theStrippedDetailFieldRetainsEverythingAfterTheControlPoint`:**

```
org.opentest4j.AssertionFailedError: CONTENT DESTRUCTION, FIELD-SCOPED to `detail`: the STRICT `detail` must equal the OFF `detail` under EXACTLY ONE substitution — the sentinel became '[STRIPPED]' — and under no other. They first diverge at index 219.
  expected: ...al Value: [STRIPPED]<br>&nbsp;&nbsp;Payload Used: [STRIPPED]' AND '1'='1<br>&nbsp;&nbsp;Detection Method: BLIND_BOOLEAN<...
  actual:   ...al Value: [STRIPPED]<br>&nbsp;&nbsp;Payload Used: [STRIPPED]<br>&nbsp;&nbsp;Detection Method: BLIND_BOOLEAN<br>&nbsp;&nb...
```

**`theOnlyTwoDifferencesBetweenStrictAndOffAreTheEnumeratedControls`:**

```
org.opentest4j.AssertionFailedError: A THIRD, UNENUMERATED DIFFERENCE between the OFF and STRICT blobs was found. Applying the two KNOWN substitutions to the OFF blob — the sentinel becomes '[STRIPPED]' inside `detail`, and 'Cookie: wibble=harbor-pebble-window-thistle' becomes 'Cookie: [STRIPPED]' inside requestResponses — must reproduce the STRICT blob EXACTLY. A residue remains, first differing at index 256.
  predicted: ...al Value: [STRIPPED]<br>&nbsp;&nbsp;Payload Used: [STRIPPED]' AND '1'='1<br>&nbsp;&nbsp;Detection Method: BLIND_BOOLEAN<...
  observed:  ...al Value: [STRIPPED]<br>&nbsp;&nbsp;Payload Used: [STRIPPED]<br>&nbsp;&nbsp;Detection Method: BLIND_BOOLEAN<br>&nbsp;&nb...
```

Neither the control nor any new assertion was softened to make these green. Task 2 repaired the
PREDICTION.

## Task 2 — the repaired prediction, and the ordering probe

### The new `expected` construction, quoted

Both guards now share one helper. Note that both substitutions are keyed on rendered LINE PREFIXES:

```kotlin
fun withDetailLineControlsApplied(text: String): String =
    text
        .replace(
            "$PAYLOAD_USED_PREFIX${payloadRenderedFor(cookiePoint(), PrivacyMode.OFF)}",
            "$PAYLOAD_USED_PREFIX${ScannerIssueSupport.INJECTION_VALUE_STRIPPED_MARKER}",
        ).replace(
            "$ORIGINAL_VALUE_PREFIX$DETAIL_SENTINEL",
            "$ORIGINAL_VALUE_PREFIX${ScannerIssueSupport.INJECTION_VALUE_STRIPPED_MARKER}",
        )
```

The field-scoped guard calls `withDetailLineControlsApplied(offDetail)`; the whole-blob guard calls
`withDetailLineControlsApplied(offBlob).replace(POSITIVE_CONTROL_HEADER_BEFORE, POSITIVE_CONTROL_HEADER_AFTER)`.
The payload substitution's OFF-mode text is DERIVED via `payloadRenderedFor(cookiePoint(), PrivacyMode.OFF)`
rather than restated, so it cannot drift from what the production line actually renders.

A third extractor pin was added ahead of the comparison: the OFF `detail` must contain
`"Payload Used: " + PAYLOAD.value`. Without it, an extractor returning a shorter field would make the
payload substitution match nothing, turn it into a no-op, and leave the equality measuring one
control while appearing to measure two.

### `theOnlyThreeDifferencesBetweenStrictAndOffAreTheEnumeratedControls`

`DIFFERENCE 3` is a first-class named clause in the same register as the other two: inside `detail`,
the OFF blob carries the rendered payload verbatim, the STRICT blob carries the marker in its place,
and no residue of the payload survives anywhere in that field. `thirdDifferenceMessage` became
`unenumeratedDifferenceMessage` — a count-free name that will not need renaming the next time a
difference is enumerated.

### ORDERING PROOF — measured in both forms, both results recorded

The plan's `[edge:ordering]` hazard is that the payload text CONTAINS the sentinel, so a sentinel
substitution running first mutates the payload text mid-flight and the later payload substitution
silently becomes a no-op. The probe was run twice because the first run measured something the plan
did not anticipate.

**Probe (i) — a bare swap of the two prefix-qualified substitutions.** `21 tests completed`,
`BUILD SUCCESSFUL`. **NO assertion went red.** This is a real finding and it is recorded rather than
dressed up: once BOTH substitutions are prefix-qualified, their keys occupy disjoint regions of the
string (`Original Value: <sentinel>` never occurs inside a `Payload Used: ` line), so the order is
genuinely immaterial. Prefix-qualification, not ordering, is what carries the safety. Reporting a red
here would have been reporting reach the probe does not have.

**Probe (ii) — the hazard's actual shape: the pre-existing UNQUALIFIED global sentinel replace,
placed first.** This is the form the file carried before this task, reordered ahead of the new payload
substitution. `21 tests completed, 2 failed`. Two NAMED assertions went red:

**`theStrippedDetailFieldRetainsEverythingAfterTheControlPoint`:**

```
org.opentest4j.AssertionFailedError: CONTENT DESTRUCTION, FIELD-SCOPED to `detail`: the STRICT `detail` must equal the OFF `detail` under EXACTLY TWO PREFIX-QUALIFIED substitutions — the 'Payload Used: ' line's rendered payload became '[STRIPPED]', and so did the 'Original Value: ' line's sentinel — and under no other. They first diverge at index 219.
  expected: ...al Value: [STRIPPED]<br>&nbsp;&nbsp;Payload Used: [STRIPPED]' AND '1'='1<br>&nbsp;&nbsp;Detection Method: BLIND_BOOLEAN<...
  actual:   ...al Value: [STRIPPED]<br>&nbsp;&nbsp;Payload Used: [STRIPPED]<br>&nbsp;&nbsp;Detection Method: BLIND_BOOLEAN<br>&nbsp;&nb...
```

**`theOnlyThreeDifferencesBetweenStrictAndOffAreTheEnumeratedControls`:**

```
org.opentest4j.AssertionFailedError: AN UNENUMERATED DIFFERENCE between the OFF and STRICT blobs was found. Applying the three KNOWN substitutions to the OFF blob — the 'Original Value: ' line's sentinel becomes '[STRIPPED]' inside `detail`, the 'Payload Used: ' line's rendered payload becomes the same marker inside `detail`, and 'Cookie: wibble=harbor-pebble-window-thistle' becomes 'Cookie: [STRIPPED]' inside requestResponses — must reproduce the STRICT blob EXACTLY. A residue remains, first differing at index 256.
  predicted: ...al Value: [STRIPPED]<br>&nbsp;&nbsp;Payload Used: [STRIPPED]' AND '1'='1<br>&nbsp;&nbsp;Detection Method: BLIND_BOOLEAN<...
  observed:  ...al Value: [STRIPPED]<br>&nbsp;&nbsp;Payload Used: [STRIPPED]<br>&nbsp;&nbsp;Detection Method: BLIND_BOOLEAN<br>&nbsp;&nb...
```

Both probes were reverted with `git checkout HEAD -- <path>`; `git stash` was never used, because
`refs/stash` lives in the parent `.git` and is shared across every linked worktree.
`git status --porcelain src/` printed `0` lines afterwards.

## SC3 — the red probe for the payload control

**Designated red-probe assertion:** `cookiePayloadIsStrippedUnderStrict` — specifically its first
`assertFalse`. It is named as such in the test class KDoc, so a future reader mutating
`sanitizeRenderedPayload` knows which line is supposed to catch them.

Three mutations, one at a time, each applied to the working tree, run, and reverted with
`git checkout HEAD -- <path>`. None is committed.

### Mutation A — the control revert

The payload line is changed back to render the untreated truncated payload value, at
`src/main/kotlin/com/six2dez/burp/aiagent/scanner/ScannerIssueSupport.kt:174`:

```diff
-        detailLines.add("  Payload Used: ${sanitizeRenderedPayload(point, payload, policy)}")
+        detailLines.add("  Payload Used: ${payload.value.take(PAYLOAD_VALUE_MAX_CHARS)}")
```

Result: `21 tests completed, 9 failed`. Both designated assertions went red. Verbatim:

```
IssueDetailCookieCarrierTest > cookiePayloadIsStrippedUnderStrict() FAILED
    org.opentest4j.AssertionFailedError: STRICT / PAYLOAD LINE: the COOKIE-typed injection point's originalValue must be ABSENT from the `detail` field, but the sentinel 'apple-orange-basket-lantern' was present. For a COOKIE point the payload is DERIVED FROM that value, so an uncontrolled 'Payload Used: ' line re-leaks exactly what the 'Original Value: ' line just stripped. Extracted detail: 'Vulnerability confirmed via active testing<br><br>Type:<br>&nbsp;&nbsp;SQLI<br>&nbsp;&nbsp;Injection Point: COOKIE - pumpkin-lantern-name<br>&nbsp;&nbsp;Original Value: [STRIPPED]<br>&nbsp;&nbsp;Payload Used: apple-orange-basket-lantern' AND '1'='1<br>&nbsp;&nbsp;Detection Method: BLIND_BOOLEAN<br>&nbsp;&nbsp;Evidence: evidence-marker-present<br><br>Backend: test-backend<br>Scan: Active<br>Confidence: 90' ==> expected: <false> but was: <true>
```

```
IssueDetailCookieCarrierTest > cookiePayloadIsStrippedUnderBalanced() FAILED
    org.opentest4j.AssertionFailedError: BALANCED: the COOKIE-typed injection point's originalValue must be ABSENT from the `detail` field, but the sentinel 'apple-orange-basket-lantern' was present on the 'Payload Used: ' line. BALANCED sets stripCookies just as STRICT does; a control that fires only under STRICT reads the wrong half of the policy. Extracted detail: 'Vulnerability confirmed via active testing<br><br>Type:<br>&nbsp;&nbsp;SQLI<br>&nbsp;&nbsp;Injection Point: COOKIE - pumpkin-lantern-name<br>&nbsp;&nbsp;Original Value: [STRIPPED]<br>&nbsp;&nbsp;Payload Used: apple-orange-basket-lantern' AND '1'='1<br>&nbsp;&nbsp;Detection Method: BLIND_BOOLEAN<br>&nbsp;&nbsp;Evidence: evidence-marker-present<br><br>Backend: test-backend<br>Scan: Active<br>Confidence: 90' ==> expected: <false> but was: <true>
```

The other seven reds under this mutation are the guards that read the payload line for a different
reason — `thePayloadStrippedMarkerIsNotTruncated`,
`aCookiePayloadCarryingOnlyAnEncodedFormOfTheValueIsStillStripped`,
`anEmptyValuedCookiePointStillRendersTheMarkerOnBothLines`,
`theStrippedDetailFieldRetainsEverythingAfterTheControlPoint`,
`theOnlyThreeDifferencesBetweenStrictAndOffAreTheEnumeratedControls` — plus
`cookieOriginalValueIsStrippedUnderStrict` and `…UnderBalanced`, which now see the leak because the
DERIVED payload carries the sentinel into the blob a second time. That last pair going red under a
PAYLOAD mutation is the fixture change earning its keep: before this plan they were blind to it.

### Mutation B — the policy-plumbing revert

The call to the new sanitizer stays in place; it is handed a policy constructed for OFF instead of
the live `policy` parameter:

```diff
-        detailLines.add("  Payload Used: ${sanitizeRenderedPayload(point, payload, policy)}")
+        detailLines.add("  Payload Used: ${sanitizeRenderedPayload(point, payload, RedactionPolicy.fromMode(com.six2dez.burp.aiagent.redact.PrivacyMode.OFF))}")
```

Result: `21 tests completed, 9 failed`. Verbatim, the designated probe and the marker-integrity guard:

```
IssueDetailCookieCarrierTest > cookiePayloadIsStrippedUnderStrict() FAILED
    org.opentest4j.AssertionFailedError: STRICT / PAYLOAD LINE: the COOKIE-typed injection point's originalValue must be ABSENT from the `detail` field, but the sentinel 'apple-orange-basket-lantern' was present. For a COOKIE point the payload is DERIVED FROM that value, so an uncontrolled 'Payload Used: ' line re-leaks exactly what the 'Original Value: ' line just stripped. ==> expected: <false> but was: <true>
```

```
IssueDetailCookieCarrierTest > thePayloadStrippedMarkerIsNotTruncated() FAILED
    org.opentest4j.AssertionFailedError: MARKER INTEGRITY: the COOKIE carrier's 'Payload Used: ' line must render the stripped marker EXACTLY, not a prefix of it and not the marker with a residual payload suffix attached. Rendered: 'apple-orange-basket-lantern' AND '1'='1'. ==> expected: <[STRIPPED]> but was: <apple-orange-basket-lantern' AND '1'='1>
```

**This is a STRONGER detection than 28-01 achieved on the same mutation class, and the difference is
worth naming.** 28-01's mutation B — hard-coding the write site's policy — was MEASURED as undetected
by every behavioural test then in the file; only the source-TEXT pin `theWriteSiteReadsTheLivePolicy`
caught it, and that pin is weaker than an execution assertion. Here the mutation is caught by
BEHAVIOURAL assertions, because `sanitizeRenderedPayload` receives its policy as an argument the test
can drive directly. `theWriteSiteReadsTheLivePolicy` itself stayed GREEN under this mutation — it
scans `ActiveAiScanner.kt`, and this mutation lives in `ScannerIssueSupport.kt`. That pin's blindness
to a mutation in a different file is stated here rather than left to be inferred; it is not reach the
probe has.

### Mutation C — the fixture regression

The hand-typed, non-interpolating payload value the fixture carried before this plan is restored, in
place of the `PayloadGenerator`-derived one:

```diff
 val PAYLOAD =
-    PayloadGenerator()
-        .generateContextAwarePayloads(VulnClass.SQLI, DETAIL_SENTINEL, DERIVED_PAYLOAD_LIMIT)
-        .first()
+    Payload(
+        value = "benign-probe-payload",
+        vulnClass = VulnClass.SQLI,
+        detectionMethod = DetectionMethod.REFLECTION,
+        risk = PayloadRisk.SAFE,
+        expectedEvidence = "evidence-marker-present",
+    )
```

Result: `21 tests completed, 21 failed`. The containment guard added to
`assertSentinelsAreDistinctAndNonOverlapping` is the assertion that fires, and because that guard also
runs from `@BeforeEach` it takes the whole class down — which is the intended blast radius for a
fixture that has stopped measuring the route it names. Verbatim:

```
IssueDetailCookieCarrierTest > sentinelsAreDistinctAndNonOverlapping() FAILED
    org.opentest4j.AssertionFailedError: FIXTURE / NON-VACUITY: the payload fixture is DERIVED at construction time from `PayloadGenerator().generateContextAwarePayloads(VulnClass.SQLI, DETAIL_SENTINEL, 5)` and MUST CONTAIN the sentinel 'apple-orange-basket-lantern'. It rendered as 'benign-probe-payload'. A PayloadGenerator that stops interpolating the original value would otherwise leave every payload assertion in this class VACUOUSLY GREEN — an absence assertion over a payload that never carried the sentinel proves nothing about the 'Payload Used: ' line. That exact vacuity is what `28-VERIFICATION.md` missing[3] measured in the hand-typed fixture this one replaced. ==> expected: <true> but was: <false>
```

This is the assertion whose ABSENCE let 14 green tests say nothing about this route.

### Probe hygiene

Every mutation is behavioural, caught by an execution assertion rather than by a source-text pin —
with the one exception stated under mutation B, where a source-text pin's non-firing is reported as a
limitation rather than as coverage. After all five runs:

```
$ git status --porcelain src/ | wc -l
       0
```

## Verification

```
$ JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test \
    --tests 'com.six2dez.burp.aiagent.scanner.IssueDetailCookieCarrierTest' \
    --tests 'com.six2dez.burp.aiagent.scanner.CookieRouteDispositionTest' \
    --tests 'com.six2dez.burp.aiagent.scanner.EvidenceTailReachTest'
BUILD SUCCESSFUL

IssueDetailCookieCarrierTest: tests=21 failures=0 errors=0 skipped=0
CookieRouteDispositionTest:   tests=5  failures=0 errors=0 skipped=0
EvidenceTailReachTest:        tests=2  failures=0 errors=0 skipped=0
```

`CookieCarrierInventoryTest` and `VulnClassInventoryTest` — the only other classes referencing
`ScannerIssueSupport` — were run additionally and are green.

```
$ JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew ktlintCheck detekt
BUILD SUCCESSFUL

$ git diff --stat -- detekt-baseline.xml
(no output — QUAL-07 respected, the baseline did not grow)

$ git diff --stat -- src/main/kotlin/com/six2dez/burp/aiagent/scanner/AdaptivePayloadEngine.kt
(no output — SC4 did not regress)
```

`./gradlew check` was NOT run as a gate: it is RED at HEAD for a maintainer-accepted reason (redact
BRANCH coverage 0.92784 against a 0.930 floor) and the floor was not touched.

### `.planning/REQUIREMENTS.md` — byte-unchanged, raw output

```
$ shasum -a 256 .planning/REQUIREMENTS.md
9b3219662ec0d007c1c82d64eed3ef2698bd306ce69f01205ac9bbc3f42fcfb4  .planning/REQUIREMENTS.md
```

```
$ grep -n 'PRIV-05' .planning/REQUIREMENTS.md
6:Scope = the 17 findings of the 2026-08-05 deep code review of v0.9.2. Two of them (SEC-04, PRIV-05) are **defects verified by running the shipped code**, not theoretical concerns — they are the reason this milestone exists. Phase numbering continues from the previous milestone (Phase 20+).
10:**Ordering constraint:** SEC-04 and PRIV-05 are live defects in a published release and lead the milestone. SEC-06 (agent trust boundary) and REL-05 (EDT) both rewrite `ChatPanel.maybeExecuteToolCall` and must be sequential, not parallel. QUAL-06 lands last so it can cover the code the earlier phases produce.
23:- [ ] **PRIV-05** (Finding 2, **high**): Cookie values do not reach an AI backend in STRICT or BALANCED mode by any path. Specifically, the passive scanner's `=== COOKIES ===` section — which today re-emits cookies as bare `name=value`, stripped of the `Cookie:` prefix that `cookieHeaderRegex` keys on — is redacted. Sensitive-key matching recognises real-world names (`JSESSIONID`, `PHPSESSID`, `connect.sid`, `auth_token`, `csrftoken`, `remember_me`) rather than only exact matches against the `SENSITIVE_KEYS` alternation. Covered by a test that asserts each of those names is redacted in both modes.
39:- [x] **DOC-03**: A security advisory documents SEC-04 and PRIV-05 for users running v0.9.0–v0.9.2, stating impact and the version that fixes them; `README.md`, `SPEC.md`, `DECISIONS.md` and the GitBook site (`burp-ai-agent-docs`) reflect the new tool-call confirmation flow and the corrected privacy claims.
47:| PRIV-05 | 2 | High | 21 |
```

Line 23 remains `- [ ]`. `requirements-completed` in this SUMMARY's frontmatter is deliberately
EMPTY: D-28-04 keeps PRIV-05 open, and whether this gap round finally justifies ticking it is a
judgement for the round's own gate, not this plan's.

### Source-level acceptance criteria

```
grep -c 'sanitizeRenderedPayload' ScannerIssueSupport.kt                        -> 3   (>= 2 required)
grep -E 'Payload Used: \$\{sanitizeRenderedPayload\(point, payload, policy\)\}' -> 1   (exactly 1)
grep -E 'policy\.stripCookies && point\.type == InjectionType\.COOKIE' | wc -l  -> 2   (one per sibling gate)
awk '/internal fun sanitizeRenderedPayload/,/^$/' | strip-comments | grep -c 'point\.' -> 1
grep -c 'RedactionPolicy.fromMode(' ActiveAiScanner.kt (comment-stripped)       -> 1   (unchanged; pin green)
grep -c 'generateContextAwarePayloads' IssueDetailCookieCarrierTest.kt          -> 3   (>= 1 required)
grep -c 'benign-probe-payload' IssueDetailCookieCarrierTest.kt                  -> 0
grep -c '@Test' IssueDetailCookieCarrierTest.kt                                 -> 21  (14 at HEAD + 7)
grep -c 'theOnlyTwoDifferencesBetweenStrictAndOffAreTheEnumeratedControls'      -> 0
grep -c 'theOnlyThreeDifferencesBetweenStrictAndOffAreTheEnumeratedControls'    -> 2
grep -c 'DIFFERENCE 3'                                                          -> 2   (>= 1 required)
grep -c 'thirdDifferenceMessage'                                                -> 0
```

The `point.` count of exactly **1** is the positive assertion that shape-keying is ABSENT: the only
`point.` read in the new function's executable body is the `point.type` in the gate. Any text-level
treatment of the value would raise it.

**One criterion's literal number differs from its stated expectation, and the difference is
reported rather than absorbed.** Task 2's criteria say
`grep -c 'theOnlyThreeDifferencesBetweenStrictAndOffAreTheEnumeratedControls'` returns 1; it returns
2. The second occurrence is the class KDoc's `[...]` cross-reference to the renamed test, which the
criterion did not account for. The substantive requirement — the old name is gone, the new name is
present — is met exactly, and updating the KDoc reference was itself a plan instruction.

## Decisions Made

- **Whole-payload strip, not substring excision** (D-28-07, locked before this plan). Cookie-point
  payload diagnostics are given up deliberately under STRICT and BALANCED. The trade is recorded in
  the function's own KDoc in the same register its sibling uses, and it is accepted because the
  operator retains the raw attack request byte-for-byte in the SAME issue's `requestResponses` pane —
  a checked invariant (`theRequestResponsesListIsNotAlteredByTheControl`), not a claim.
- **No emptiness guard.** The `28-REVIEW.md` `CR-01` sketch carried one; it would let an empty-valued
  COOKIE point through and make the point's TYPE observable as a rendering difference.
  `anEmptyValuedCookiePointStillRendersTheMarkerOnBothLines` holds this closed.
- **One marker vocabulary.** No second constant, no second `"[STRIPPED]"` literal in main source.
- **The prediction helper uses two PREFIX-QUALIFIED substitutions, not a global sentinel replace.**
  See "Deviations" below — this is a deliberate strengthening with a measured trade-off.

## Deviations from Plan

### 1. [Rule 2 — Missing Critical] The prediction helper's second substitution is prefix-qualified, not a global sentinel replace

- **Found during:** Task 2 (repairing the difference guards)
- **Issue:** The plan's `<action>` specifies "TWO PREFIX-QUALIFIED substitutions", while the
  `must_haves` `[edge:ordering]` truth describes the second one as "a global sentinel replace" whose
  corruption the ordering guards against. The two readings are not compatible: once BOTH
  substitutions are prefix-qualified their keys occupy disjoint regions of the string and the
  ordering hazard the truth names does not exist.
- **Fix:** Implemented the `<action>`'s reading — both substitutions prefix-qualified, payload first
  — because a global sentinel replace would ABSORB a sentinel occurrence appearing anywhere else in
  the blob. That is precisely the failure class this gap round exists to correct: it would silently
  swallow a THIRD uncontrolled detail route carrying the same bytes. Prefix-qualification leaves such
  an occurrence standing as an unexplained residue and turns the equality red on it.
- **Consequence, handled rather than hidden:** the ordering PROOF the acceptance criteria demand
  would have been vacuous against the shipped form. Both forms were therefore probed and both results
  recorded — the bare swap measured GREEN (reported as a finding, with the reason), and the global
  replace placed first measured RED with two named assertions and verbatim messages. The helper's
  KDoc states this measured outcome instead of asserting an ordering hazard that the shipped code
  does not have.
- **Files modified:** `src/test/kotlin/com/six2dez/burp/aiagent/scanner/IssueDetailCookieCarrierTest.kt`
- **Committed in:** `1ac4d47`

### 2. [Rule 3 — Blocking] The KDoc could not quote the replaced fixture's literal

- **Found during:** Task 1 (GREEN)
- **Issue:** The new `PAYLOAD` KDoc originally quoted the hand-typed literal it replaced, as
  historical context. That put the literal back in the file and broke the acceptance criterion
  `grep -c 'benign-probe-payload' … returns 0`.
- **Fix:** The KDoc now describes the replaced fixture ("a fixed probe string that contradicted its
  own `VulnClass.SQLI` label") and says explicitly that the literal is not repeated. The historical
  record is preserved in prose without reintroducing a string a source-scanning gate is watching for.
- **Files modified:** `src/test/kotlin/com/six2dez/burp/aiagent/scanner/IssueDetailCookieCarrierTest.kt`
- **Committed in:** `064096c`

---

**Total deviations:** 2 (1 missing-critical strengthening, 1 blocking). No scope creep: both are
inside the two files the plan's `files_modified` names, and neither widens what this plan claims.

## Issues Encountered

None beyond the two deviations above. The two tests the plan predicted would be red at the task-1
boundary were red exactly as predicted, for exactly the predicted reason, and were repaired by
correcting the prediction rather than by weakening the control.

## Known Stubs

None. `sanitizeRenderedPayload` is production code with no placeholder branch, no TODO and no
hard-coded value; the pass-through arm is the pre-existing behaviour byte-for-byte.

## Threat Flags

Disposition for every threat this plan registers. No NEW security-relevant surface was introduced:
the change removes bytes from an outbound blob and adds no endpoint, no auth path, no file access and
no schema change.

| Threat ID | Component | Disposition | Status at this commit |
|---|---|---|---|
| T-28-17 | `buildActiveIssueDetailLines` line 121 (`Payload Used:`) | mitigate | **MITIGATED.** Type-keyed whole-payload strip shipped. Held by `cookiePayloadIsStrippedUnderStrict` / `…UnderBalanced`, measured end to end through `Redaction.apply`, and proven falsifiable by mutations A and B. |
| T-28-18 | `IssueDetailCookieCarrierTest` fixture (`PAYLOAD`) | mitigate | **MITIGATED.** The fixture is derived from `PayloadGenerator.generateContextAwarePayloads`, and the containment guard in `assertSentinelsAreDistinctAndNonOverlapping` turns the class red if interpolation stops. Proven by mutation C. |
| T-28-19 | SC3 evidence in this SUMMARY | mitigate | **MITIGATED.** Designated assertion named verbatim; three mutations plus a two-form ordering probe; every `org.opentest4j.AssertionFailedError` quoted verbatim; reverts by `git checkout HEAD --` with a `git status --porcelain` clean gate returning 0; `git stash` never used. |
| T-28-20 | `Evidence:` line in the same detail block | accept | **ACCEPTED, NOT REOPENED.** Owned by `AR-28-01`, filed by plan 28-03 and accepted by the maintainer at that plan's blocking checkpoint. It is named here so this plan's closure cannot be read as covering it. |
| T-28-21 | This plan's own closure claim | mitigate | **MITIGATED.** See `## Residuals`. `ScannerIssueSupport.kt`'s single-producer KDoc claim is deliberately NOT corrected here — plan 28-05 corrects it, because only that plan can name the second producer's control accurately. |
| T-28-SC | package-manager installs | accept | **N/A, as planned.** This plan installed nothing. No Package Legitimacy Gate applies. |

## Residuals

Stated in this plan's own words, so nothing here is read as wider than the control that shipped.

1. **`AR-27-08` — ONE of THREE measured detail-line routes is closed by this plan.** `28-VERIFICATION.md`
   measured three routes carrying a COOKIE point's value to the `scanner_issues` tool result. This
   plan closes route 1 (`CR-01`), the `Payload Used:` line in `ScannerIssueSupport`. `AR-27-08` is NOT
   closed at this commit and must not be recorded as closed on the strength of this plan.
2. **`CR-02` — `AiScanCheck.buildDetail` is still UNCONTROLLED at this commit.** It is a second
   active-scan detail producer (`AiScanCheck.kt:353`, `:357`), reads no privacy mode at all, and
   therefore leaks in STRICT, BALANCED and OFF alike. It is live-registered at `App.kt:215` and reaches
   the tool via `McpToolExecutorImpl.kt:604`. **Plan 28-05 owns it.** Nothing in this plan's code,
   comments or record claims otherwise.
3. **`WR-01` / D-28-06 — the repository-wide single-producer gate DOES NOT EXIST and was deliberately
   NOT built.** The `assertEquals(1, …)` in `originalValueRenderedFor` filters the list
   `buildActiveIssueDetailLines` itself returned and is structurally incapable of seeing another file.
   `payloadRenderedFor` does NOT copy that framing: its failure message states its actual reach, names
   `AiScanCheck.buildDetail` as a producer it cannot see, and says explicitly that a green result is
   not a repository-wide guarantee. D-28-06 records the real gate as a named residual; this plan did
   not quietly satisfy it and does not claim it exists.
4. **`AR-28-01` — the ResponseAnalyzer evidence tail remains an ACCEPTED residual** (MEDIUM, DERIVED),
   by maintainer decision at plan 28-03's blocking checkpoint. It is not reopened here.
   `requestResponses` is byte-unchanged by this control, so the operator's own raw copy of their
   traffic in the local Burp evidence pane is untouched — this plan claims no privacy win it did not
   earn.

Also carried forward, unchanged and outside this plan's `files_modified`: `WR-05`
(`CookieRouteDispositionTest.kt:286` mixes `"src/main/kotlin"` with `File.separator` and fails on
Windows) and the `CookieCarrierInventoryTest.kt:539` evidence-tail bound defect deferred by 28-03.

## Next Phase Readiness

- **Ready for plan 28-05.** The symbols it consumes exist by the names the plan's
  `<artifacts_this_phase_produces>` table declares: `sanitizeRenderedPayload`, `payloadRenderedFor`,
  the seven named tests, `theOnlyThreeDifferencesBetweenStrictAndOffAreTheEnumeratedControls` and
  `unenumeratedDifferenceMessage`.
- **The marker vocabulary is fixed for route 2.** `INJECTION_VALUE_STRIPPED_MARKER` must be
  REFERENCED from `AiScanCheck.kt`, never retyped as a second `"[STRIPPED]"` literal (D-28-05).
- **The single-producer KDoc at `ScannerIssueSupport.kt` is left standing for 28-05 to correct**, per
  T-28-21. A reader arriving before 28-05 lands will find that claim false; the class KDoc of
  `IssueDetailCookieCarrierTest` now states plainly that a second producer exists and that this file
  cannot see it.
- **No blocker.** The three phase-28 test classes are green together, both lint gates pass, and
  `detekt-baseline.xml`, `AdaptivePayloadEngine.kt` and `.planning/REQUIREMENTS.md` are all
  byte-unchanged.

---
*Phase: 28-the-issue-detail-cookie-carrier-auditissue-detail-scanner-is*
*Plan: 04*
*Completed: 2026-08-27*

## Self-Check: PASSED

- `28-04-SUMMARY.md`, `ScannerIssueSupport.kt`, `IssueDetailCookieCarrierTest.kt` — all present on disk.
- Commits `da3d195`, `064096c`, `1ac4d47`, `8f75e67` — all present in `git log`.
- `grep -c "the suite went red"` over this SUMMARY -> **0**.
- The designated assertion `cookiePayloadIsStrippedUnderStrict` appears verbatim; 12 verbatim
  `org.opentest4j.AssertionFailedError` lines are quoted.
- `git status --porcelain` -> empty. No mutation, probe or scratch artifact was left behind.
