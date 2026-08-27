---
phase: 28-the-issue-detail-cookie-carrier-auditissue-detail-scanner-is
plan: 02
subsystem: testing
tags: [kotlin, redaction, privacy, scanner, montoya, cookie-carrier, source-scan-tripwire]

requires:
  - phase: 28-01
    provides: "ScannerIssueSupport.sanitizeInjectionPointValue — the write-site control that removed phase 27's objection to converting the extractor's predicate"
  - phase: 27-07
    provides: "Redaction.isCookieParameterType — the shared cookie-parameter-type predicate this plan wires a third executable call site into"
provides:
  - "InjectionPointExtractor's hand-written cookie-parameter predicate replaced by Redaction.isCookieParameterType (identity swap, zero behaviour change)"
  - "CookieRouteDispositionTest — 5 tests: a derived predicate count, a no-double-redaction tripwire, and one-marker-vocabulary-per-route prompt capture"
  - "Redaction.isCookieParameterType's KDoc superseded under a dated marker, with the count it pinned re-derived by test rather than restated"
  - "Six-site correction fan-out, including two sites no grep reaches at any spelling"
  - "CookieCarrierInventoryTest's INJECTION_EXTRACTOR/PARAMETER_LIST entry moved from CLASSIFIED_NON_CARRYING to ROUTED_THROUGH"
affects: [28-03, PRIV-05 closure, any future cookie-carrier audit]

actuals:
  tokens: 72740
  tasks: 3
  commits: 3

tech-stack:
  added: []
  patterns:
    - "Derived-count-over-restated-count: a source-derivable number lives in a test that re-derives it from the tree, never in prose that goes stale silently"
    - "Marker-derived-from-source: a test reads another file's inline marker literal out of that file rather than hardcoding a copy"

key-files:
  created:
    - src/test/kotlin/com/six2dez/burp/aiagent/scanner/CookieRouteDispositionTest.kt
  modified:
    - src/main/kotlin/com/six2dez/burp/aiagent/scanner/InjectionPointExtractor.kt
    - src/main/kotlin/com/six2dez/burp/aiagent/redact/Redaction.kt
    - src/test/kotlin/com/six2dez/burp/aiagent/redact/CookieCarrierInventoryTest.kt
    - src/test/kotlin/com/six2dez/burp/aiagent/redact/CookieHeaderRuleOwnershipTest.kt
    - src/test/kotlin/com/six2dez/burp/aiagent/mcp/tools/ParameterCarrierRedactionTest.kt

key-decisions:
  - "D-28-02 upheld: the extractor keeps returning the RAW value. The control stays at each consumer, because the predicate's two consumers have different dispositions and redacting in the producer would double-redact the already-controlled one with a foreign vocabulary."
  - "matchInsertionPoint's three-way `when` left unconverted — it is a dispatch over all three Montoya type names, not a cookie predicate; converting one arm would leave arms spelled two ways."
  - "The plan's acceptance grep for site-scoped cookie literals returns 1, not 0, and the 1 is that `when` arm. The criterion as written contradicts the action that ordered the arm left alone; reported honestly rather than satisfied by over-converting."
  - "SITE 5's replacement live example is BountyPromptTagResolver.kt:151, recorded with its latency caveat (zero instantiations in main) rather than presented as a live leak."

patterns-established:
  - "Supersede-never-delete for measurement prose: the original measurement is preserved as a byte-exact prefix and the correction appended, with character counts reported as proof."
  - "Stale-but-green fan-out: prose sites whose citation rots while their assertion stays green are enumerated BY HAND, because no grep reaches a paraphrase."

requirements-completed: []

coverage:
  - id: D1
    description: "InjectionPointExtractor's cookie filter routes through the shared Redaction.isCookieParameterType predicate, with no behaviour change"
    requirement: "PRIV-05"
    verification:
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/scanner/InjectionPointExtractorTest.kt (12 tests, zero edits)"
        status: pass
      - kind: unit
        ref: "CookieRouteDispositionTest#exactlyOneCookieTypePredicateExistsInMainSource"
        status: pass
  - id: D2
    description: "AdaptivePayloadEngine's already-controlled path is proven, not asserted, to be undisturbed by the conversion"
    requirement: "PRIV-05"
    verification:
      - kind: unit
        ref: "CookieRouteDispositionTest#cookieInjectionPointCarriesTheRawValueSoTheControlledConsumerIsNotDoubleRedacted"
        status: pass
      - kind: unit
        ref: "CookieRouteDispositionTest#theAdaptivePayloadPromptCarriesOneMarkerVocabularyUnderBalanced"
        status: pass
      - kind: unit
        ref: "CookieRouteDispositionTest#theAdaptivePayloadPromptCarriesTheRawValueUnderOff"
        status: pass
      - kind: other
        ref: "git diff --stat 121d2e2 HEAD -- src/main/kotlin/com/six2dez/burp/aiagent/scanner/AdaptivePayloadEngine.kt (empty)"
        status: pass
  - id: D3
    description: "All six prose sites asserting or relying on the deferral are amended in the same phase as the conversion"
    requirement: "PRIV-05"
    verification:
      - kind: unit
        ref: "CookieCarrierInventoryTest (4), CookieHeaderRuleOwnershipTest (3), ParameterCarrierRedactionTest (25)"
        status: pass

status: complete
---

# Phase 28 Plan 02: Cookie-Predicate Conversion and Correction Fan-Out Summary

Converted `InjectionPointExtractor`'s hand-written cookie-parameter predicate to the shared `Redaction.isCookieParameterType` — an identity swap that changes no behaviour — and closed the six prose sites that still asserted, or silently relied on, phase 27's deliberate refusal to make that conversion.

## What Was Built

**Task 1 — the conversion and the re-derived count** (`c6330bc`)

`InjectionPointExtractor.kt`'s COOKIE filter now calls the shared predicate. The swap is value-preserving in the safe direction: the shared predicate trims and upper-cases before comparing, so it accepts everything the old exact-equality accepted and nothing extra that Montoya's closed `HttpParameterType` enum can produce.

`Redaction.isCookieParameterType`'s KDoc — which recorded the refusal AND pinned a resulting predicate count, both of which this commit falsifies — was superseded under a dated marker. Phase 27's reasoning is kept verbatim, because it explains why the deferral was *correct while the route was uncontrolled*, and deleting it would leave a later reader unable to tell a considered deferral from an oversight. The count is deliberately **not** restated with a new number; `exactlyOneCookieTypePredicateExistsInMainSource` derives it from the tree instead.

**Task 2 — proving the controlled consumer undisturbed** (`a270ca8`)

Both halves of D-28-02's composition proof are machine-checked, and the direct confirmation shipped too — the supervisor stub worked, so **no Montoya-construction fallback was needed and the stronger proof is the one that ran**.

**Task 3 — the six-site fan-out** (this commit)

Details in the table below.

## The Propagation Grep — Raw Output and Reconciliation

The plan measured six lines on 2026-08-27 and instructed me to reconcile rather than silently trust a differing count. **I measured seven.** Run at the start of task 3:

```
$ grep -rnE 'type\(\)\.name == \\?"COOKIE\\?"' src/
src/test/kotlin/com/six2dez/burp/aiagent/mcp/tools/ParameterCarrierRedactionTest.kt:485:        // `it.type().name == "COOKIE"` cookie-parameter test in a THIRD file. It is measured by
src/test/kotlin/com/six2dez/burp/aiagent/redact/CookieHeaderRuleOwnershipTest.kt:154:         * picks up `InjectionPointExtractor.kt`'s `it.type().name == "COOKIE"`, which compares a
src/test/kotlin/com/six2dez/burp/aiagent/redact/CookieCarrierInventoryTest.kt:296:                        positiveFixture = """request.parameters().filter { it.type().name == "COOKIE" }.forEach { param ->""",
src/test/kotlin/com/six2dez/burp/aiagent/redact/CookieCarrierInventoryTest.kt:498:                    "its own `it.type().name == \"COOKIE\"` predicate that plan 27-07 baseline B9 " +
src/test/kotlin/com/six2dez/burp/aiagent/scanner/CookieRouteDispositionTest.kt:22: * `it.type().name == "COOKIE"` filter to the shared [com.six2dez.burp.aiagent.redact.Redaction]
src/test/kotlin/com/six2dez/burp/aiagent/scanner/CookieRouteDispositionTest.kt:350:                """.filter { it.type().name == "COOKIE" }""",
src/main/kotlin/com/six2dez/burp/aiagent/redact/Redaction.kt:594:     *    own `it.type().name == "COOKIE"` filter and is deliberately NOT converted (D-27-17): its
```

**Line count: 7. Reconciliation against the plan's measured 6:**

| Delta | Cause |
|---|---|
| −1 | `InjectionPointExtractor.kt:29` — the converted line. It was one of the plan's six and is no longer a match, which is the intended effect of task 1. |
| +2 | `CookieRouteDispositionTest.kt:22` and `:350` — both in the file *I* created in tasks 1–2. `:22` is a historical citation in the class KDoc; `:350` is a spelling fixture that must quote the old form to prove the regex is not vacuous. |

6 − 1 + 2 = 7. **No original site moved or vanished unexpectedly.** Confirmed the pre-conversion line existed at the base with `git show HEAD~2:...InjectionPointExtractor.kt`.

**Which of the seven are fan-out sites:** four are (`ParameterCarrierRedactionTest:485`, `CookieHeaderRuleOwnershipTest:154`, `CookieCarrierInventoryTest:296` and `:498`). Three are not: `Redaction.kt:594` is task 1's own KDoc (already amended, and its old text is now the preserved historical record beneath the supersession marker), and the two `CookieRouteDispositionTest` lines are this plan's own new artifact.

**Post-fan-out re-run returns 6 lines**, and every one is now an explicitly-marked historical citation or a deliberate fixture. The escaped spelling (`CookieCarrierInventoryTest:498`) is gone — that entry was rewritten wholesale by the SITE 2 move.

## The Six-Site Fan-Out Table

| Site | Location | Reached by grep? | Edit made |
|---|---|---|---|
| **1** | `CookieCarrierInventoryTest` `PARAMETER_LIST` `positiveFixture` | **YES** (bare spelling, `:296`) | Fixture text updated to the converted line. Verified the `AccessorSpec` regex keys on `\.parameters\(` — the accessor call, not the predicate — so it matched before *and* after, which is exactly why the fixture had to be corrected by hand. Stale-but-green. |
| **2** | `CookieCarrierInventoryTest` `INJECTION_EXTRACTOR`/`PARAMETER_LIST` entry | **YES** (escaped spelling, `:498` — invisible to the bare grep) | Entry **moved** from `CLASSIFIED_NON_CARRYING` to `ROUTED_THROUGH`, key byte-identical. Reason rewritten to name both consumers and both controls. Producer line numbers re-measured (`:22`/`:26`/`:37`/`:170`). |
| **3** | `CookieCarrierInventoryTest` `ISSUE_DETAIL_CARRIER_DISPOSITION` + third blind axis in class KDoc | **NOT REACHED BY GREP** — it paraphrases the deferral without quoting the predicate | Supersession appended, original measurement preserved as byte-exact prefix. Class KDoc's blind-axis worked example amended to read as a closed example rather than an open finding, while stating the axis itself stays open. |
| **4** | `CookieHeaderRuleOwnershipTest` `MATCHER_SPELLINGS` KDoc | **YES** (`:154`) | Rule kept, citation replaced. The case-sensitivity choice now stands on its general ground (a parameter TYPE is not a header NAME, different control, different owner); the extractor is cited as the converted historical example. |
| **5** | `ParameterCarrierRedactionTest` bound-of-this-pin comment | **YES** (`:485`) | Bound kept, worked example replaced. States the previous example was converted and the pin's blindness is **unchanged by that**, then names a replacement live example. |
| **6** | `CookieHeaderRuleOwnershipTest` `CLASSIFIED_NON_REDACTING` KDoc absence-justification | **NOT REACHED BY GREP** — it describes the removed construct *in words* | Rule kept (the file's absence from the map is still correct, zero-hit status unchanged), citation amended to describe the tree as it now is. Added an explicit note that this clause was rewritten *even though nothing turned red*. |

**Two of six are unreachable by the propagation grep at any spelling.** A correction driven by grep output alone would have shipped with SITE 3 and SITE 6 rotting silently.

## Verification Results

**Byte-exact prefix preservation (SITE 3), programmatically verified:**

| Measure | Value |
|---|---|
| `ISSUE_DETAIL_CARRIER_DISPOSITION` before | **1578 chars** |
| after | **3082 chars** |
| appended | **1504 chars** |
| `new.startsWith(old)` | **True** |

**Acceptance greps:**

| Check | Expected | Actual |
|---|---|---|
| `isCookieParameterType` in extractor (comment-stripped) | 1 | **1** |
| `InjectionType.URL_PARAM\|BODY_PARAM` in extractor | unchanged | **4 → 4** |
| `Redaction.kt` diff non-comment lines | 0 | **0** |
| `CarrierSite(INJECTION_EXTRACTOR, PARAMETER_LIST)` occurrences | 1 | **1** (at `:406`, between `ROUTED_THROUGH` `:390` and `CLASSIFIED_NON_CARRYING` `:473` — inside `ROUTED_THROUGH`) |
| SITE 4/5 files, non-comment diff lines | 0 | **0** (no assertion logic edited) |
| `INJECTION_VALUE_STRIPPED_MARKER` in new test (comment-stripped) | ≥2 | **3** |
| `AdaptivePayloadEngine.kt` diff across the phase | empty | **empty** |
| `detekt-baseline.xml` | byte-unchanged | **byte-unchanged** |
| `.planning/REQUIREMENTS.md` / `STATE.md` / `ROADMAP.md` | untouched | **untouched** |

**Full suite: green.** `178 classes / 1277 tests / 0 failures / 0 errors / 1 skipped` in 3m 18s.

Reconciled against wave 1's baseline of 177 classes / 1272 tests: **+1 class and +5 tests, exactly `CookieRouteDispositionTest`.** No other class gained or lost a test, which is the corroborating evidence that task 3 edited only prose.

The single skip is pre-existing and unrelated: `ExternalMcpClientManagerTest.connectAndListTools_returnsExpectedCount`, `@Disabled` because it needs a live MCP server (deferred to HUMAN-UAT). This plan touched neither that file nor that decision.

**The known `RedactionTest` CPU-load flake did not appear**, so no idle-machine re-run was required.

**Gates:** `ktlintCheck` and `detekt` green. `check` was **not** run and is not cited — it is RED for a maintainer-accepted coverage-floor reason whose deciding branch is a wall-clock guard, so its colour tracks machine load rather than code.

**Coverage direction:** as the plan predicted, the only `redact` package change is KDoc prose. Comments are not executable, so neither the branch numerator nor the denominator moved.

## The RED Probe

A tripwire that has never been observed failing is a tripwire nobody has tested. Before committing task 2, I temporarily routed `ScannerIssueSupport.INJECTION_VALUE_STRIPPED_MARKER` through `extract()` — the exact mistake D-28-02 exists to prevent — and confirmed the tripwire fires:

```
CookieRouteDispositionTest > cookieInjectionPointCarriesTheRawValue...  FAILED
  InjectionPointExtractor.extract must hand the RAW cookie value to its consumers.
  It returned '[STRIPPED]' instead, which means a redaction control has been MOVED
  INTO THE EXTRACTOR. Per D-28-02 that is the wrong place for it: ...
```

Only that one test went red; the two prompt-capture tests stayed green, which is correct and is the point of the composition split — they exercise the engine's own vocabulary, not the extractor's output. The probe was fully reverted (`git diff` on the extractor clean before the task-2 commit).

## Deviations from Plan

### 1. [Rule 1 — contradictory acceptance criterion] Task 1's cookie-literal grep returns 1, not the specified 0

- **Found during:** Task 1 acceptance checks.
- **Issue:** The criterion states `grep -vE '^\s*(//|\*|/\*)' InjectionPointExtractor.kt | grep -c 'COOKIE"'` must return `0`. It returns `1`. The single hit is `"COOKIE" -> InjectionType.COOKIE` at `:177` — the `matchInsertionPoint` three-way `when` arm that **the same task's `<action>` explicitly ordered left in place** ("SCOPE CALL YOU MUST MAKE AND RECORD, not silently absorb"). The two instructions are arithmetically incompatible: that arm contains the literal, so the grep cannot reach 0 while the arm survives.
- **Resolution:** Followed the `<action>`, which is the specific and reasoned instruction, over the grep, which is a coarser proxy the plan author did not reconcile against their own scope call. The `must_haves` truth is about the *cookie-parameter predicate* being gone — a three-way dispatch is not a predicate, and the count test excludes it by design with the reason documented.
- **Honest measurement of the criterion's actual intent** — no hand-written cookie *comparison* survives in that file: `grep -cE '[=!]= "COOKIE"|"COOKIE" [=!]=|equals\("COOKIE"'` returns **0**.
- **Not fixed by over-converting.** Converting one arm of a three-way `when` would leave arms spelled two different ways, which the action rejects on readability grounds and which would not have made the file better.

### 2. [Rule 2 — missing critical follow-through] Sibling cross-reference broken by the SITE 2 move

- **Found during:** Task 3, SITE 2.
- **Issue:** `CarrierSite(INJECTION_EXTRACTOR, HEADER_LIST)`'s reason said InjectionPoint values "share the disposition recorded for this file's PARAMETER_LIST entry **below**". Moving PARAMETER_LIST into `ROUTED_THROUGH` made "below" false — a stale-but-green rot of exactly the class this task exists to close, created *by* this task.
- **Fix:** Amended to name the destination map explicitly.
- **Not in the plan's six sites,** but clause (viii)(a) is about a correction reaching every artifact citing the finding, and a reference the correction itself breaks plainly qualifies.

### 3. [Deviation — count reconciliation] Propagation grep returns 7, not the plan's 6

Fully reconciled above; no action needed beyond recording it, which the plan explicitly required.

## Known Stubs

None. No stub, placeholder, `TODO`, or skipped test was introduced. No test was weakened into a skip — the plan's escape hatch for a Montoya-construction constraint was not needed.

## Threat Flags

None. T-28-06 (tampering via a silently behaviour-changing "identity" swap) is mitigated as the threat model specifies: `InjectionPointExtractorTest`'s 12 tests stay green with zero edits, and task 2's raw-value assertion pins the output directly.

## Self-Check: PASSED

- `CookieRouteDispositionTest.kt` — FOUND (18475 bytes)
- `28-02-SUMMARY.md` — FOUND
- Commit `c6330bc` — FOUND
- Commit `a270ca8` — FOUND
- Full suite re-run after all edits — 0 failures

## Notes for Future Phases

- **PRIV-05 is deliberately NOT closed by this plan.** `REQUIREMENTS.md` is byte-unchanged and PRIV-05 remains `[ ]`. `requirements-completed` is empty on purpose.
- **`AdaptivePayloadEngine.kt` must stay out of every remaining plan's `files_modified` for the rest of phase 28.** Its absence from the phase diff is half of D-28-02's proof; editing it later retroactively weakens a claim already made in this SUMMARY.
- The engine writes `[REDACTED_VALUE]` as an inline literal rather than an exported constant. `CookieRouteDispositionTest` derives it from source to compensate, but promoting it to a named constant beside `INJECTION_VALUE_STRIPPED_MARKER` would let the test reference it directly. Not done here — out of scope, and it would have broken the byte-unchanged proof.
