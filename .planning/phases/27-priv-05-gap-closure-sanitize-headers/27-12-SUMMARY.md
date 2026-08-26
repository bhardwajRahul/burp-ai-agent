---
phase: 27-priv-05-gap-closure-sanitize-headers
plan: 12
subsystem: testing
tags: [privacy, redaction, repository-state-test, tripwire, kotlin, junit5, mcp]

requires:
  - phase: 27-priv-05-gap-closure-sanitize-headers (plan 27-10)
    provides: "widened COOKIE_NAME_PART, renamed and inverted the my_cookie survival pin, CookieHeaderNameWidthTest"
  - phase: 27-priv-05-gap-closure-sanitize-headers (plan 27-11)
    provides: "JSON_STRING_OPEN family and ~291 lines added to SerializedEmissionRedactionTest"
provides:
  - "RedactingPolicySurvivalSweepTest — a CI gate that fails on any green assertion that a sensitive value SURVIVES a redacting policy"
  - "The two prohibited api.example.com STRICT survival pins deleted from McpToolHelpersTest"
  - "McpToolHelpersTest.offLeavesBothSerializedShapesByteIdentical — the OFF byte-identity home for the pass-through those pins measured"
  - "Execution-time evidence that the detector reports exactly 3 hits on the pre-round contents that carried the real artifacts"
affects: [27-13, priv-05, future-redaction-phases, security-register-maintenance]

actuals:
  tokens: 16600
  tasks: 3
  commits: 3

tech-stack:
  added: []
  patterns:
    - "Repository-state tripwire with FILE WALK and DETECTOR as separable entry points, so a walk-level narrowing is assertable in both directions"
    - "Function-body isolation that CONSUMES blank lines, terminating on the first non-blank line at or below the declaration indent"
    - "Exclusions constructed INTO the detector with a measured count each, never into an allowlist"

key-files:
  created:
    - src/test/kotlin/com/six2dez/burp/aiagent/redact/RedactingPolicySurvivalSweepTest.kt
  modified:
    - src/test/kotlin/com/six2dez/burp/aiagent/mcp/tools/McpToolHelpersTest.kt

key-decisions:
  - "The two host pins were DELETED, not inverted — inverting to assertFalse would assert a behaviour that does not ship and would turn the suite red for a finding this plan is prohibited from fixing."
  - "The pass-through the pins measured is re-pointed at PrivacyMode.OFF and asserted with assertEquals byte-identity, so the replacement names no sensitive value in an assertTrue and cannot itself become the artifact the sweep exists to find."
  - "Both host pins live in ONE fixture (the whole pre-round function copied verbatim) because in the real tree both lived in ONE function; splitting them into two invented functions would be less faithful, not more."
  - "The raw-string skip in the file walk now IGNORES comment-only lines when tracking triple-quote state — a bug found by the sweep's own self-scan, not anticipated by the plan."
  - "Stated bounds in the KDoc record the numbers MEASURED at execution time (7 benign-accessor, 9 unqualified) rather than the numbers the plan projected (5, 7); the discrepancy is explained in place rather than smoothed over."

patterns-established:
  - "A prose must-have that a repository can falsify should be replaced by a scan, and the scan must state its bound before its first assertion."
  - "A clean self-scan is unfalsifiable without a counterpart asserting the same detector is NON-empty with the narrowing removed."

requirements-completed: []

coverage:
  - id: D1
    description: "The two green assertTrue assertions pinning a hostname as surviving PrivacyMode.STRICT are gone from McpToolHelpersTest, deleted rather than inverted, with a comment at each position naming AR-27-04 and the register file."
    requirement: "PRIV-05"
    verification:
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/mcp/tools/McpToolHelpersTest.kt#cookieVariantsAreStrippedEndToEndThroughRedactIfNeeded"
        status: pass
      - kind: other
        ref: "grep -rn --include='*.kt' --exclude='RedactingPolicySurvivalSweepTest.kt' -A3 'assertTrue(' src/test | sed -E 's/^[^ ]*\\.kt[:-][0-9]+[:-]//' | grep -v '^[[:space:]]*[/*]' | grep -c 'api\\.example\\.com'  (2 before, 0 after)"
        status: pass
    human_judgment: false
  - id: D2
    description: "The pass-through those pins measured is asserted byte-identically under PrivacyMode.OFF on both serialized payload shapes."
    requirement: "PRIV-05"
    verification:
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/mcp/tools/McpToolHelpersTest.kt#offLeavesBothSerializedShapesByteIdentical"
        status: pass
    human_judgment: false
  - id: D3
    description: "Plan 27-08's prose must-have is replaced by a CI gate: RedactingPolicySurvivalSweepTest scans src/test/kotlin and fails on a new survival pin, with an empty hit set and an empty ALLOWLIST on the tree as shipped."
    requirement: "PRIV-05"
    verification:
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/redact/RedactingPolicySurvivalSweepTest.kt#noGreenTestAssertsASensitiveValueSurvivesARedactingPolicy"
        status: pass
    human_judgment: false
  - id: D4
    description: "The sweep is proven to FIRE: three positive fixtures copied from the pre-round file contents are flagged, and the sweep scans its own file clean with NO self-file exclusion, falsifiably."
    requirement: "PRIV-05"
    verification:
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/redact/RedactingPolicySurvivalSweepTest.kt#theTwoHostPinsRemovedThisRoundAreFlagged"
        status: pass
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/redact/RedactingPolicySurvivalSweepTest.kt#theBlankLineHazardFixtureIsIsolatedWholeIncludingItsBlankLines"
        status: pass
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/redact/RedactingPolicySurvivalSweepTest.kt#theSweepFileItselfYieldsNoHits"
        status: pass
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/redact/RedactingPolicySurvivalSweepTest.kt#theRawStringSkipIsWhyTheSelfScanIsClean"
        status: pass
    human_judgment: false
  - id: D5
    description: "The sweep is proven to have fired ON THE TREE, not only on copies: the committed detector run against the pre-round file contents reports exactly three hits."
    requirement: "PRIV-05"
    verification:
      - kind: manual_procedural
        ref: "throwaway probe driving the committed detect()/fileWalk() over `git show 09e9cae:<path>` output — raw output quoted in this SUMMARY; NOT re-run by CI"
        status: pass
    human_judgment: false
  - id: D6
    description: "AR-27-04 stays OPEN at its recorded severity, untouched, still owed a human decision; PRIV-05 is not closed by this plan."
    verification: []
    human_judgment: true
    rationale: "Whether the recorded residual remains an acceptable release posture is the maintainer's judgment. The disposition on record was AUTO-SELECTED by `mode: yolo`, not maintainer-chosen. Plan 27-13 carries it to 27-HUMAN-UAT.md."

duration: 30 min
completed: 2026-08-26
status: complete
---

# Phase 27 Plan 12: The Redacting-Policy Survival Sweep Summary

**The two green `assertTrue` pins that taught the suite a hostname survives `PrivacyMode.STRICT` are deleted and their pass-through re-asserted byte-identically under `PrivacyMode.OFF`; plan 27-08's prose must-have is now `RedactingPolicySurvivalSweepTest`, an 11-test CI tripwire with an empty hit set, an empty allowlist, a clean unexcluded self-scan and a stated bound naming eleven things it cannot see.**

## Performance

- **Duration:** 30 min
- **Started:** 2026-08-26T10:43:00Z
- **Completed:** 2026-08-26T11:12:00Z
- **Tasks:** 3
- **Files modified:** 2 (1 created, 1 modified)

## Accomplishments

- The two assertions plan 27-05 explicitly prohibited, and this phase committed anyway, are gone. Deleted, not inverted — the behaviour they described still ships and stays open as `AR-27-04`.
- The pass-through they measured now lives in `offLeavesBothSerializedShapesByteIdentical`, which asserts `assertEquals` byte identity on the SAME two payload shapes under `PrivacyMode.OFF`, the one policy under which pass-through is correct.
- `RedactingPolicySurvivalSweepTest` (11 tests, green) replaces the prose claim with a scan of `src/test/kotlin`. It reports an EMPTY hit set with an EMPTY `ALLOWLIST` on the tree as shipped, and it is proven to fire against the three real artifacts this round removed.
- The sweep scans its OWN file with no self-file exclusion and comes out clean — and that zero is FALSIFIABLE, because the same detector over the same file without the raw-string skip returns 5.
- One real bug found by the sweep's own self-scan and fixed (see Deviations).

## Task Commits

1. **Task 1: Re-point the two prohibited host assertions at an OFF fixture** — `47140e1` (test)
2. **Task 2: The sweep — a repository-state tripwire for green survival pins** — `1a6be75` (test)
3. **Task 3: Prove the sweep would have caught the real thing, on the real tree** — measurement only; no file under `src/` changed, so it carries no code commit of its own. Its evidence is recorded below and lands with the plan metadata commit. The KDoc sentence task 3 required (durable check versus one-time evidence) was already written in task 2, so no edit was owed.

## Files Created/Modified

- `src/test/kotlin/com/six2dez/burp/aiagent/redact/RedactingPolicySurvivalSweepTest.kt` — NEW, 1145 lines. The sweep, its vocabulary, its four positive and six negative fixtures, its three constructed exclusions, its self-scan pair, and its stated bound.
- `src/test/kotlin/com/six2dez/burp/aiagent/mcp/tools/McpToolHelpersTest.kt` — the two prohibited assertions deleted and replaced in place by comments; one new sibling test added. 71 insertions, 9 deletions.

---

## MEASUREMENTS

Every number below was produced by running the stated command on this tree. Where a measured value
disagrees with the plan's projection, the measured value is what is recorded and the disagreement is
stated rather than smoothed over.

### 1. Task 1 red probe — the two host pins

Command, run identically at step 0 and at task end:

```
grep -rn --include='*.kt' --exclude='RedactingPolicySurvivalSweepTest.kt' -A3 'assertTrue(' src/test \
  | sed -E 's/^[^ ]*\.kt[:-][0-9]+[:-]//' | grep -v '^[[:space:]]*[/*]' | grep -c 'api\.example\.com'
```

**BEFORE the deletion it returned `2`.** The two matched lines, quoted verbatim:

```
src/test/kotlin/com/six2dez/burp/aiagent/mcp/tools/McpToolHelpersTest.kt-249-                rawMessageFinalText.contains("api.example.com"),
src/test/kotlin/com/six2dez/burp/aiagent/mcp/tools/McpToolHelpersTest.kt-285-                rawFinalText.contains("api.example.com"),
```

**AFTER the deletion it returns `0`.**

**ALL FOUR BOUNDARY VALUES, measured, so the two runs are never confused:**

| When | Excluded form | Unexcluded form | Note |
|---|---|---|---|
| task-1 step 0 (sweep file does not exist yet) | `2` | `2` | the exclusion costs the red probe NOTHING |
| task-1 end (sweep file still does not exist) | `0` | `0` | |
| PLAN END (after task 2) | `0` | `2` | the two remaining lines are FIXTURE COPIES, not a regression |

The two lines the unexcluded form still matches at plan end, quoted so nobody later reads them as a
regression:

```
src/test/kotlin/com/six2dez/burp/aiagent/redact/RedactingPolicySurvivalSweepTest.kt-842-                rawMessageFinalText.contains("api.example.com"),
src/test/kotlin/com/six2dez/burp/aiagent/redact/RedactingPolicySurvivalSweepTest.kt-878-                rawFinalText.contains("api.example.com"),
```

These are the `rawMessageFinalText` and `rawFinalText` copies inside `HOST_PIN_FIXTURE` — the verbatim
pre-round text the sweep uses as its positive fixture for the host-literal vocabulary entry. They live
inside a triple-quoted raw string and the sweep's own file walk skips them; the `grep` gate is
line-based and does not. That is why `--exclude='RedactingPolicySurvivalSweepTest.kt'` is part of this
gate's STATED BOUND rather than a convenience. All four measured values match the plan's projections
exactly.

### 2. The two companion gates, labelled honestly as invariants and NOT as red probes

- **`assertFalse` companion gate** — same command shape with the verb changed. Returns `0` BEFORE the
  edit and `0` after. This is a NEGATIVE INVARIANT whose job is to stay `0`; it is evidence that the
  pins were not INVERTED, and it is evidence of nothing changing. Do not cite it as evidence that
  something changed.
- **`AR-27-04` presence gate** — `grep -c 'AR-27-04' src/test/.../McpToolHelpersTest.kt` returns `4`
  BEFORE the edit and `4` after. Two message lines left with the deleted assertions and two
  replacement comments arrived. This is a PRESENCE INVARIANT reading `4` on both sides. The real check
  is READING the two replacement comments, which name `AR-27-04` and
  `.planning/phases/26-coverage-static-analysis-debt-docs/26-SECURITY.md` as where the measurement
  lives, and state why no assertion stands there.
- `git diff -- .planning/phases/26-coverage-static-analysis-debt-docs/26-SECURITY.md` is EMPTY. The
  record is plan 27-13's to amend.
- `git diff -- .planning/REQUIREMENTS.md` is EMPTY. **PRIV-05 stays `[ ]`. This plan closes no
  requirement.**
- `git diff -- build.gradle.kts gradle/libs.versions.toml` is EMPTY. No dependency was added, so no
  package-legitimacy checkpoint was owed (`T-27-12-SC`).

### 3. The pre-round SHA — resolved ONCE in task 2, reused unchanged by task 3

Resolution command, run verbatim:

```
git log --format='%H %s' -- src/test/kotlin/com/six2dez/burp/aiagent/redact/CookieHeaderNameParityTest.kt \
    src/test/kotlin/com/six2dez/burp/aiagent/mcp/tools/McpToolHelpersTest.kt \
  | grep -v -E '^[0-9a-f]+ [a-z]+\(27-1[0-9]\)' | head -1
```

Resolved to:

```
09e9caed0f6a8357d92cac009064355f4bcaafa0 test(27-04): re-pin the AR-27-01 block on the two measured shapes
```

**CONFIRMED BY CONTENT, not trusted.** `git show 09e9cae:.../CookieHeaderNameParityTest.kt` still
carries the PRE-RENAME method name `thePredicateIsDeliberatelyWiderThanTheTwoRegexes` (1 occurrence),
and `git show 09e9cae:.../McpToolHelpersTest.kt` still carries the host literal (13 occurrences,
including both pins). Neither check would pass on a post-round commit. Task 3 reused this SHA and did
not re-derive it.

The function offsets in both blobs match the plan's measurements exactly:
`thePredicateIsDeliberatelyWiderThanTheTwoRegexes` at `:178..202`, and
`cookieVariantsAreStrippedEndToEndThroughRedactIfNeeded` at `:178..289`.

### 4. TASK 3 — the detector run against the PRE-ROUND contents

Both blobs were written to a scratch directory with `git show 09e9cae:<path>` and the COMMITTED
detector — the same `detect()` / `fileWalk()` functions the committed tests drive, reached through a
throwaway `@Test` that was removed before the commit — was pointed at that directory.

**EXACTLY 3 hits. Raw output, verbatim:**

```
E prerounddFiles=2
E preroundHitsWithExclusions=3
    HIT preround-McpToolHelpersTest.kt#cookieVariantsAreStrippedEndToEndThroughRedactIfNeeded -> "api.example.com" (vocabulary entry 3)
    HIT preround-McpToolHelpersTest.kt#cookieVariantsAreStrippedEndToEndThroughRedactIfNeeded -> "api.example.com" (vocabulary entry 3)
    HIT preround-CookieHeaderNameParityTest.kt#thePredicateIsDeliberatelyWiderThanTheTwoRegexes -> sentinel (vocabulary entry 2)
```

Three hits, and they ARE the two host pins plus the underscore pin — the three artifacts the plan
named. This run was performed TWICE: once against the detector as first written, and once again
against the detector as COMMITTED after the detekt refactor of `candidatesIn`. Both runs produced
byte-identical output, which is what makes the refactor demonstrably behaviour-preserving rather than
assumed to be.

**THE SPLIT, STATED PLAINLY.** The in-file fixtures are the DURABLE check: they run on every CI run and
they are what survives this phase. **This tree run is EXECUTION-TIME evidence that the durable check
would have caught the real artifacts, and it is NOT re-run by CI.** No `@Test` shells out to git —
coupling the suite to repository history would make CI fail for a reason unrelated to the code under
test. Verified: `grep -v '^[[:space:]]*[/*]' <sweep> | grep -c 'git show\|ProcessBuilder\|Runtime.getRuntime'`
returns `0`, so prose describing this one-time run is allowed and code performing it is not.

### 5. The sweep's own numbers

| Measurement | Value | Note |
|---|---|---|
| `.kt` files under `src/test/kotlin` | **151** | `MIN_EXPECTED_TEST_FILES` floor set at **100**, well below |
| Hit set on the tree as shipped | **0** | `ALLOWLIST` is **empty** and stays empty |
| Same detector, vocabulary UNQUALIFIED by the three exclusions, same tree | **9** | every one a legitimate shape |
| Self-scan, WITH the raw-string skip | **0 hits** | no self-file exclusion exists |
| Self-scan, WITHOUT the raw-string skip | **5 hits** | this is what makes the zero meaningful |
| `BLANK_LINE_HAZARD_FIXTURE` isolated body | **24 lines, 2 blank** | proves the walk CONSUMED the blank lines |
| `HOST_PIN_FIXTURE` hits | **2** | both from the host-literal entry |

**THE THREE CONSTRUCTED EXCLUSIONS, each with the MEASURED pre-existing count it accounts for.** All
three are code paths in the detector. None is an `ALLOWLIST` key. `ALLOWLIST` is empty.

| Exclusion | Measured count on this tree | What it buys, as a cost |
|---|---|---|
| `BENIGN_ACCESSORS` (exactly one key, `Sentinel.BENIGN_CONTROL`) | **7** live functions | a genuinely sensitive value reached through that one accessor is invisible |
| The POSITION RULE | **1** — the pre-redaction fixture guard in `theRealTruncateIfNeededOutputShapeIsStrippedAndNotLengthened` | a pin positioned textually ABOVE the policy marker is invisible |
| The NEGATION RULE | **1** — `assertTrue(!output.contains(sentinel), ...)` in `RedactionTest.cookieHeaderNameVariantsAreStripped` | none beyond the shape itself |

7 + 1 + 1 = 9, which is exactly the unqualified count, and 9 − 9 = 0, the qualified count. The
arithmetic closes.

### 6. TWO MEASURED VALUES THAT DISAGREE WITH THE PLAN — reported, not adjusted

The plan instructed that a gate which does not measure what it claims must be REPORTED rather than
adjusted. Two of its projections were wrong, both in the same direction, both for the same reason.

- **`BENIGN_ACCESSORS` pre-existing count: plan projected `5`, MEASURED `7`.**
- **Unqualified-vocabulary count on the post-fix tree: plan projected `7`, MEASURED `9`.**

**Cause, identified rather than guessed.** The two extra benign-control assertions are in
`aCanonicalCookieAtTheOpenOfAJsonStringDoesNotSurviveStrict` and
`aCanonicalCookieAtTheOpenOfAJsonStringDoesNotSurviveBalanced`, which are plan **27-11**'s
JSON-string-open probes. Each carries its own `assertTrue(redacted.contains(Sentinel.BENIGN_CONTROL.value), …)`.
The plan's `5` was measured before 27-11 landed; this plan's `depends_on` puts 27-11 in its base, so
the correct number here is 7. The full unqualified hit list is reproduced below so the classification
is checkable rather than asserted:

```
D treeHitsWithExclusions=0 []
D treeHitsUnqualified=9
    UNQ SerializedEmissionRedactionTest.kt#headerNameAndBenignControlSurviveTheSerializedShape -> Sentinel.BENIGN_CONTROL.value (entry 1)
    UNQ SerializedEmissionRedactionTest.kt#everyCookieNameVariantCarriesADistinctSentinelAndNoneSurvives -> Sentinel.BENIGN_CONTROL.value (entry 1)
    UNQ SerializedEmissionRedactionTest.kt#siteMapEntryCarrierStripsCookiesInBothRedactingModes -> Sentinel.BENIGN_CONTROL.value (entry 1)
    UNQ SerializedEmissionRedactionTest.kt#issueDetailsCarrierStripsCookiesInBothRedactingModes -> Sentinel.BENIGN_CONTROL.value (entry 1)
    UNQ SerializedEmissionRedactionTest.kt#aCanonicalCookieAtTheOpenOfAJsonStringDoesNotSurviveStrict -> Sentinel.BENIGN_CONTROL.value (entry 1)
    UNQ SerializedEmissionRedactionTest.kt#aCanonicalCookieAtTheOpenOfAJsonStringDoesNotSurviveBalanced -> Sentinel.BENIGN_CONTROL.value (entry 1)
    UNQ SerializedEmissionRedactionTest.kt#theRealTruncateIfNeededOutputShapeIsStrippedAndNotLengthened -> sentinel (entry 2)
    UNQ SerializedEmissionRedactionTest.kt#bearerShapedAuthorizationIsStillRedactedAndTheHeaderNameSurvives -> Sentinel.BENIGN_CONTROL.value (entry 1)
    UNQ RedactionTest.kt#cookieHeaderNameVariantsAreStripped -> sentinel (entry 2)
```

**Nothing was adjusted to make these agree.** No vocabulary entry was narrowed, no `ALLOWLIST` key was
added, and `BENIGN_ACCESSORS` still contains exactly ONE key. What changed is the numbers written in
the KDoc, which now record what was measured here, with the plan's projection and the reason for the
gap stated beside them. A stated bound that does not match the control it describes is the exact
defect this phase exists to repair, and copying `5` forward would have been that defect one iteration
smaller.

**Three plan projections that matched exactly**, recorded so the two misses are read in proportion:
all four red-probe boundary values (`2`/`2`, then `0`/`2`); the pre-round detector count of exactly 3;
and the post-fix qualified hit count of exactly 0.

### 7. Full-suite gates

- `JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew check` — **exit 0**, 2m 53s. MCP tree line
  coverage 71.16% (2869/4032) against a 65.0% floor — MET.
- `JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test` — **exit 0**.
- `ktlintCheck` and `detekt` — **exit 0**.
- Aggregate across 175 test classes: **1238 tests, 1 skipped, 0 failures, 0 errors.**
- The single skipped test is `ExternalMcpClientManagerTest.connectAndListTools_returnsExpectedCount()`.
  It is PRE-EXISTING and unrelated to this plan — it was skipped on the base tree too, and no file this
  plan touched is in its path. Out of scope, not fixed, recorded here so the `1` in the count is not
  mistaken for something this plan introduced.
- `RedactionTest` did NOT flake on any run in this plan. The known `SafeRegex` 50 ms wall-clock
  sensitivity did not fire, so no re-run was needed and none is being reported as one.

---

## Decisions Made

- **Both host pins live in ONE positive fixture, not two.** `HOST_PIN_FIXTURE` is the WHOLE pre-round
  `cookieVariantsAreStrippedEndToEndThroughRedactIfNeeded` copied verbatim, and the detector reports
  exactly 2 hits on it. In the real tree both pins lived in ONE function; splitting them into two
  invented functions would have made the fixtures LESS faithful, and the plan's own prediction that
  the unexcluded grep reads `2` at plan end — naming `rawMessageFinalText` and `rawFinalText` as two
  lines in one file — is satisfied exactly by this shape.
- **Fixture VALUES are byte-verbatim; fixture SOURCE carries two value-preserving escapes.** Kotlin
  interpolates raw strings, so a literal `$` is written `${'$'}` and a literal triple quote is written
  as three `${'"'}` escapes. Both evaluate to the original character, so the string the detector sees
  is byte-identical to the source it was copied from. Every fixture was extracted mechanically from
  `git show` output or from the working tree by line range, never retyped.
- **The OFF replacement test asserts `assertEquals` byte identity, not containment.** Byte identity is
  a stronger claim AND it names no sensitive value in an `assertTrue`, so the sweep's own vocabulary
  cannot match it. The replacement cannot become the artifact the sweep exists to find (`T-27-12-07`).
- **`ANTI_SWALLOW_FIXTURE` and `NEGATIVE_ASSERT_FALSE_FIXTURE` are the same real block, flipped and
  unflipped.** The flipped one must be FLAGGED and the unflipped one CLEAN. The pair is what proves
  the verb — and not something else — is what made it flag, which is the floor under
  `BENIGN_ACCESSORS`.
- **The `NEGATIVE_OFF_MODE_FIXTURE` is labelled a DERIVATION, not a verbatim copy.** The tree carries
  no OFF-mode `assertTrue` containment assertion today; the real OFF block uses `assertEquals`. The
  shape must still be tolerated, so it was derived from the real block by substituting the assertion
  form, and its KDoc says so rather than passing it off as verbatim.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] A triple quote in the sweep's OWN KDoc prose inverted the raw-string skip for the whole file**

- **Found during:** Task 2, on the first run of the new test file.
- **Issue:** `dropRawStringInteriors` toggled its triple-quote state on EVERY line, comments included.
  The class KDoc explains what the walk does and, in doing so, quotes a bare triple quote. That is an
  ODD toggle: every line below it read with its state inverted, so the fixture interiors were scanned
  as if they were code and the real code between fixtures was blanked. The sweep flagged ITSELF with 5
  hits, and `noGreenTestAssertsASensitiveValueSurvivesARedactingPolicy` failed with those same 5.
  The plan predicted this failure MODE (`T-27-12-09`, measured at 2 self-hits on a mock) but not this
  CAUSE — the plan's mechanism assumed only fixture literals could toggle the state.
- **Why it is a bug and not a prose problem:** the loud direction is what happened here — the self-scan
  caught it. The DANGEROUS direction is the other one: an odd toggle anywhere in a scanned file's
  prose can blank REAL code and make the tree scan miss a real survival pin SILENTLY, with every test
  still green. That is precisely the vacuous-gate class this plan exists to prevent.
- **Fix:** a comment-only line never opens or closes a raw string, so it must not toggle the state.
  `dropRawStringInteriors` now consults `isCommentOnly` before scanning a line for triple quotes.
  This is in the FILE WALK only; the DETECTOR is untouched, so the two entry points stay separable
  and the both-directions assertions still mean what they say.
- **Deliberately NOT done:** the KDoc's triple quote was LEFT IN PLACE. Removing it would have made
  the new rule vacuous — nothing else in the file exercises it. The file is now its own regression
  fixture for this bug, and the reason is written in the comment beside the rule.
- **Files modified:** `src/test/kotlin/com/six2dez/burp/aiagent/redact/RedactingPolicySurvivalSweepTest.kt`
- **Verification:** `theSweepFileItselfYieldsNoHits` 0 hits; `theRawStringSkipIsWhyTheSelfScanIsClean`
  5 hits unskipped. Before the fix: 5 and 5.
- **Committed in:** `1a6be75` (Task 2 commit)

**2. [Rule 3 - Blocking] Two detekt style violations blocked the task 2 gate**

- **Found during:** Task 2, `./gradlew detekt`.
- **Issue:** `ReturnCount` on `vocabularyEntryFor` (3 returns, limit 2) and
  `LoopWithTooManyJumpStatements` on `candidatesIn` (one `break`, four `continue`).
- **Fix:** `candidatesIn` was decomposed into `containsOccurrencesIn` (index collection) and
  `assertsPresenceAt` (the `assertTrue` requirement plus the negation rule), leaving `candidatesIn` a
  filter chain in which the POSITION RULE and the NEGATION RULE are each one named step.
  `vocabularyEntryFor` became a single `takeIf`. Also one `ktlint` multiline-expression fix in
  `detect`.
- **Why this is not a weakening:** the refactor was verified behaviour-preserving by MEASUREMENT, not
  by reading. The full pre-round probe was re-run against the committed post-refactor detector and
  produced byte-identical output to the pre-refactor run: 3 pre-round hits, 0 tree hits, 9 unqualified,
  5 unskipped self-hits. No exclusion was added, none was widened, and the vocabulary is unchanged.
- **Files modified:** `src/test/kotlin/com/six2dez/burp/aiagent/redact/RedactingPolicySurvivalSweepTest.kt`
- **Verification:** `ktlintCheck` and `detekt` exit 0; all 11 sweep tests green.
- **Committed in:** `1a6be75` (Task 2 commit)

---

**Total deviations:** 2 auto-fixed (1 bug, 1 blocking).
**Impact on plan:** Both were necessary for correctness. Neither widened scope and neither touched a
production file. Deviation 1 made the sweep measurably stronger than the plan specified; deviation 2
was verified not to have made it weaker.

## Issues Encountered

- **The plan's `BENIGN_ACCESSORS` and unqualified-vocabulary counts were stale by two.** Resolved by
  measuring, identifying the cause (plan 27-11's two JSON-string-open probes, which landed between the
  plan being written and this plan executing), and recording the measured values in the KDoc with the
  discrepancy stated. See MEASUREMENTS §6. No gate was adjusted.
- **`RedactionTest.cookieHeaderNameVariantsAreStripped` carries a triple-quoted input literal**, which
  would have terminated the raw string holding it as a fixture. Resolved with the same
  value-preserving escape used for `$`, so the fixture value stays byte-verbatim.

## User Setup Required

None — no external service configuration required.

## Next Phase Readiness

- **PRIV-05 is NOT closed by this plan, and `REQUIREMENTS.md` is untouched.** Round 4 does not close
  it. What this plan delivers is that the requirement can no longer be closed on the strength of a
  green test that pins a leak as expected.
- **`AR-27-04` is untouched, still OPEN at MEDIUM, and still owed a HUMAN decision.** Its disposition
  on record was auto-selected by `mode: yolo` and says so. Its measurement, quoted probe output and
  two source-read exclusion reasons remain in `26-SECURITY.md`, which this plan did not edit — the
  record is plan 27-13's to amend, and plan 27-13 is what carries the outstanding decision to
  `27-HUMAN-UAT.md`.
- **For plan 27-13:** the standing rule it files can now be sourced from a MEASUREMENT rather than
  from a claim. The durable check is `RedactingPolicySurvivalSweepTest`; the one-time evidence that it
  would have caught the real artifacts is MEASUREMENTS §4 above, with its SHA, its command and its raw
  output.
- **Deliberately unchanged, as the plan prohibited:** `hostHeaderRegex`, `Serialization.kt`,
  `maybeAnonymizeUrl`, `AR-27-04`'s disposition, `REQUIREMENTS.md`, `AR-27-08`,
  `InjectionPointExtractor.kt:29`, `T-27-06-06`.
- **The sweep's honest weight, carried with the claim:** it is a TRIPWIRE OVER A MEASURED VOCABULARY,
  not a proof of coverage. Its KDoc names ELEVEN blind axes before its first assertion, three of which
  are the price of its three constructed exclusions. A future reader who quotes it as proof of
  coverage reproduces the defect this phase exists to repair.

## Known Stubs

None. No stub, placeholder, TODO or unwired data path was introduced by this plan. The one skipped
test in the suite is pre-existing and out of scope (see MEASUREMENTS §7).

## Threat Flags

None. This plan added no network endpoint, no auth path, no file-access pattern and no schema change.
Both files it touched are under `src/test/`; no file under `src/main/` was modified.

---
*Phase: 27-priv-05-gap-closure-sanitize-headers*
*Completed: 2026-08-26*

## Self-Check: PASSED

- `src/test/kotlin/com/six2dez/burp/aiagent/redact/RedactingPolicySurvivalSweepTest.kt` — FOUND on disk
- `src/test/kotlin/com/six2dez/burp/aiagent/mcp/tools/McpToolHelpersTest.kt` — FOUND on disk
- `.planning/phases/27-priv-05-gap-closure-sanitize-headers/27-12-SUMMARY.md` — FOUND on disk
- Commit `47140e1` (task 1) — FOUND in `git log`
- Commit `1a6be75` (task 2) — FOUND in `git log`
- Commit `c54244e` (plan metadata) — FOUND in `git log`
- Working tree clean; no throwaway probe code remains under `src/`.
- STATE.md and ROADMAP.md deliberately NOT modified — this plan ran in a worktree and the
  orchestrator owns those writes.
