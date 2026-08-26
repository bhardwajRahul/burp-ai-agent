---
phase: 27-priv-05-gap-closure-sanitize-headers
plan: 10
subsystem: security
tags: [redaction, privacy, cookies, regex, rfc9110, passive-scanner, kotlin]

requires:
  - phase: 27-priv-05-gap-closure-sanitize-headers
    provides: "Redaction.isCookieHeaderName as the single shared cookie-header-name predicate (27-01/27-02), and the parity corpus that measures it"
provides:
  - "COOKIE_NAME_PART widened to [A-Za-z0-9_-]*, closing a MEASURED live leak: my_cookie / X_Cookie / session_cookie values reaching a third-party AI backend under STRICT and BALANCED"
  - "The wave-2 assertion that pinned my_cookie's SURVIVAL under STRICT is inverted, not deleted — the corpus entry keeps carrying its measurement"
  - "Consumer POLARITY stated at both sites a reader meets the predicate: REDACTOR vs ADMITTER, with the fail-safe claim scoped to the redactors only"
  - "CookieHeaderNameWidthTest — the character-axis width check, with COVERED_TCHARS pinned to the shipped constant by a SOURCE READ rather than a re-typed copy"
  - "NOT_COVERED_TCHARS: the thirteen remaining RFC 9110 tchars, enumerated in source as the measurement plan 27-13 files AR-27-10 from"
affects: [27-13, 27-12, phase-28, PRIV-05, AR-27-10]

actuals:
  tokens: 8670   # chars/4 over the realized src/ diff (34,679 chars). See note in Performance.
  tasks: 3
  commits: 4

tech-stack:
  added: []
  patterns:
    - "Anti-drift source pin: a test constant that MUST match a shipped production constant is READ OUT OF the production source at test time and asserted to expand to it, rather than re-typed and asserted against other re-typed copies"
    - "Derived complement: an 'uncovered remainder' set is computed as ALL - COVERED, never hand-listed, so it cannot go stale in silence when either operand moves"
    - "Polarity-scoped safety claims: a shared predicate's KDoc names each consumer AND whether that consumer REDACTS or ADMITS, because 'wider is fail-safe' is only true for the redactors"

key-files:
  created:
    - src/test/kotlin/com/six2dez/burp/aiagent/redact/CookieHeaderNameWidthTest.kt
  modified:
    - src/main/kotlin/com/six2dez/burp/aiagent/redact/Redaction.kt
    - src/main/kotlin/com/six2dez/burp/aiagent/scanner/PassiveAiScannerFilters.kt
    - src/test/kotlin/com/six2dez/burp/aiagent/redact/CookieHeaderNameParityTest.kt

key-decisions:
  - "Widened the REGEX side rather than narrowing the predicate. Narrowing would shrink what McpToolHelpers.sanitizeHeaders strips on the MCP path — the direction that reopened this phase."
  - "Inverted the my_cookie assertion instead of deleting it, so the corpus entry keeps carrying its measurement (T-27-10-04)."
  - "Generalized the underscore test to iterate ALL corpus names containing '_' rather than hardcoding my_cookie. Deviation — see Deviations from Plan #2."
  - "requirements-completed is deliberately EMPTY. This plan closes no requirement; PRIV-05 stays open because AR-27-08 (the issue-detail carrier) is owned by Phase 28."

patterns-established:
  - "A test constant mirroring a production constant must be measured from source, not re-typed — otherwise the evidence it supports has a silent expiry date"
  - "A safety claim about a shared predicate must name the consumer it is true for"

requirements-completed: []  # DELIBERATE. The plan's own must_haves state: "This plan closes no
                            # requirement. PRIV-05 stays `[ ]`: AR-27-08 (the issue-detail carrier)
                            # is owned by Phase 28 and is untouched here." REQUIREMENTS.md diff is
                            # verified EMPTY (plan verification item 4).

coverage:
  - id: D1
    description: "A cookie-header name containing '_' (my_cookie, X_Cookie, session_cookie) has its VALUE replaced by [STRIPPED] on the prompt path under STRICT and BALANCED"
    requirement: "PRIV-05"
    verification:
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/redact/CookieHeaderNameParityTest.kt#theUnderscoreNameClassIsStrippedByBothTheRegexesAndThePredicate"
        status: pass
      - kind: other
        ref: "jshell probe over build/classes/kotlin/main (JDK 21): Redaction.INSTANCE.apply on the column-0 header-line shape, pre-fix vs post-fix, both modes"
        status: pass
    human_judgment: false
  - id: D2
    description: "The parity corpus covers the whole underscore class with per-entry sentinels, and its non-vacuity floors moved with it, so a future deletion turns the suite red"
    verification:
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/redact/CookieHeaderNameParityTest.kt#parityCorpusIsNonEmptyAndContainsBothPolarities"
        status: pass
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/redact/CookieHeaderNameParityTest.kt#everyNameThePromptPathStripsIsMatchedByTheSharedPredicate"
        status: pass
    human_judgment: false
  - id: D3
    description: "The character axis is machine-checked as a partition, and COVERED_TCHARS is pinned to the shipped COOKIE_NAME_PART by a source read, so NOT_COVERED_TCHARS (AR-27-10's evidence) cannot go silently stale"
    verification:
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/redact/CookieHeaderNameWidthTest.kt#theCoveredCharacterClassIsStrippedByBothTheRegexesAndThePredicate"
        status: pass
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/redact/CookieHeaderNameWidthTest.kt#theThreeCharacterSetsPartitionEachOther"
        status: pass
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/redact/CookieHeaderNameWidthTest.kt#theScanIsNonVacuous"
        status: pass
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/redact/CookieHeaderNameWidthTest.kt#theCoveredSetIsReadFromRedactionSourceNotRetyped"
        status: pass
    human_judgment: false
  - id: D4
    description: "The predicate's width claim is CORRECT AS PROSE at both sites a reader meets it — the isCookieHeaderName KDoc and the admitting call site — with each consumer's polarity named"
    verification:
      - kind: other
        ref: "grep gates: sanitizeHeadersForPrompt and AR-27-10 present in Redaction.kt; COOKIE_NAME_PART present in PassiveAiScannerFilters.kt; git diff shows comment-only changes at the call site"
        status: pass
    human_judgment: true
    rationale: >-
      The grep gates only prove the required SYMBOLS are present. Whether the rewritten paragraph
      actually says the true thing — that fail-safety is scoped to the two redacting consumers and
      explicitly negated for the admitting one — is a reading judgment, and this whole phase exists
      because a plausible-sounding paragraph was wrong for one consumer in three. A maintainer must
      read both passages, not trust a count.

duration: ~34 min
completed: 2026-08-26
status: complete
---

# Phase 27 Plan 10: Close the underscore cookie-header name class at the regex Summary

**`COOKIE_NAME_PART` widened from `[A-Za-z0-9-]*` to `[A-Za-z0-9_-]*`, closing a measured live cookie disclosure to third-party AI backends under STRICT and BALANCED; the green test that pinned that disclosure is inverted, the predicate's fail-safety claim is now scoped per consumer, and the uncovered residual is enumerated in source and machine-pinned to the shipped constant.**

## Performance

- **Duration:** ~34 min
- **Started:** 2026-08-26T09:30:00Z (approximate — first commit at 09:48:28Z, preceded by required reading and the red probe)
- **Completed:** 2026-08-26T10:04:00Z
- **Tasks:** 3
- **Files modified:** 4 (3 modified, 1 created)

**Note on `actuals.tokens`:** measured as chars/4 over the realized `src/` diff (34,679 chars → ~8,670), per the executor's stated basis. The plan's `estimate.tokens: 70000` was almost certainly computed on a whole-file-context basis (`Redaction.kt` alone is ~100 KB), so the two numbers are **not on the same scale** and the apparent 8× "overestimate" should not be read as an estimation miss. Recorded unrounded and with its basis stated rather than adjusted to look closer.

## Accomplishments

- **Closed a measured, reachable, fail-OPEN information disclosure.** `Redaction.isCookieHeaderName` is a bare `contains("cookie")`; its third consumer `PassiveAiScannerFilters.sanitizeHeadersForPrompt:186` is an **ADMITTER**, so a name the predicate claims but neither cookie regex can match was admitted onto the outbound prompt and never stripped. One token in one `const val` closed the character axis of that difference set.
- **Removed the green test that pinned the leak**, by INVERTING it rather than deleting it — the corpus entry and its sentinel survive, so the measurement is retained.
- **Corrected the claim that hid the gap**, at both sites a reader meets it, with per-consumer polarity.
- **Enumerated the residual in source** (`NOT_COVERED_TCHARS`) and **pinned it to the shipped constant by a source read**, so `AR-27-10`'s evidence has no silent expiry date.

## Task Commits

1. **Task 1 (RED): invert the `my_cookie` pin** — `5c27c9a` (test)
2. **Task 1 (GREEN): widen `COOKIE_NAME_PART`** — `399d0cd` (fix)
3. **Task 2: grow the parity corpus and raise its floors** — `90f206d` (test)
4. **Task 3: consumer polarity + the character-axis width check** — `58ec7ff` (docs)

Task 1 is `type="tracer" tdd="true"` and produced the RED/GREEN pair above.

## Files Created/Modified

- `src/main/kotlin/com/six2dez/burp/aiagent/redact/Redaction.kt` — `COOKIE_NAME_PART` widened; its rationale comment and the `isCookieHeaderName` KDoc width paragraph rewritten for consumer polarity. **The one-token constant change is the ONLY non-comment production change in this entire plan** (verified below).
- `src/main/kotlin/com/six2dez/burp/aiagent/scanner/PassiveAiScannerFilters.kt` — comment-only: polarity stated at the admitting call site, naming `COOKIE_NAME_PART`.
- `src/test/kotlin/com/six2dez/burp/aiagent/redact/CookieHeaderNameParityTest.kt` — inverted + generalized underscore test, two new corpus entries, raised floors, rewritten DIRECTION note.
- `src/test/kotlin/com/six2dez/burp/aiagent/redact/CookieHeaderNameWidthTest.kt` — **NEW.** The character-axis width check and the anti-drift source pin.

## Measurements (the required verbatim record)

### 1. Task 1 red probe — BEFORE the fix

`JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test --tests '*CookieHeaderNameParityTest'` → **EXIT=1**

```
> Task :test FAILED
CookieHeaderNameParityTest > theUnderscoreNameClassIsStrippedByBothTheRegexesAndThePredicate() FAILED
    org.opentest4j.AssertionFailedError at CookieHeaderNameParityTest.kt:190
3 tests completed, 1 failed
BUILD FAILED in 23s
```

Failure message, verbatim from `build/test-results/test/TEST-…CookieHeaderNameParityTest.xml` — **the sentinel survives verbatim, and the anonymised `Host` line proves the STRICT policy really ran**:

```
org.opentest4j.AssertionFailedError: STRICT: the value of 'my_cookie' SURVIVED a redacting
policy. '_' is a legal RFC 9110 tchar, so this is a real header name, and
PassiveAiScannerFilters.sanitizeHeadersForPrompt ADMITS it into the outbound passive-scan
prompt PRECISELY BECAUSE the shared predicate claims it. […] (output: GET / HTTP/1.1
Host: host-80cfeeef83aa.local
my_cookie: sentinelparityunderscorecookie
) ==> expected: <false> but was: <true>
```

### 2. Task 1 — AFTER the fix

Same command → **EXIT=0**, `tests="3" skipped="0" failures="0" errors="0"`.

Five-suite verify (`CookieHeaderNameParityTest`, `RedactionTest`, `SerializedEmissionRedactionTest`, `McpToolHelpersTest`, `PassiveAiScannerHeaderAdmissionTest`) → **EXIT=0**; `RedactionTest` 46/46, `PassiveAiScannerHeaderAdmissionTest` 3/3, all others green in the same run.

### 3. Independent per-name measurement against the compiled classes

`jshell` over `build/classes/kotlin/main` (JDK 21), one header line at column 0 — the shape `buildScanMetadataText` actually emits — `Redaction.INSTANCE.apply(blob, RedactionPolicy.fromMode(mode), "probe-salt", false)`:

```
PRE-FIX  (COOKIE_NAME_PART = "[A-Za-z0-9-]*")
  STRICT    my_cookie  leaked=true   predicate=true
  STRICT    X_Cookie   leaked=true   predicate=true
  STRICT    session_cookie  leaked=true   predicate=true
  STRICT    Cookie / X-Cookie / Cookie2 / Set-Cookie2 / X-Original-Cookie / X-Forwarded-Cookie
                                     leaked=false  predicate=true
  BALANCED  (identical results)

POST-FIX (COOKIE_NAME_PART = "[A-Za-z0-9_-]*")
  STRICT    all nine names           leaked=false  predicate=true
  BALANCED  all nine names           leaked=false  predicate=true
```

This independently reproduces `27-VERIFICATION-3.md`'s measurement exactly, and extends it with the post-fix half. It is the per-name evidence the JUnit test cannot give on its own, because a single test method fails fast at the first name.

### 4. Task 1 grep gates — before/after pairs

| Gate | Planning-time expectation | BEFORE (measured) | AFTER (measured) |
|---|---|---|---|
| `grep -A1 'assertFalse($' <parity> \| grep -c 'output.contains(sentinel)'` | 0 → ≥1 | **0** | **1** |
| `grep -A1 'assertTrue($' <parity> \| grep -c 'output.contains(sentinel)'` | 1 → 0 | **1** | **0** |
| `grep -c 'INTENTIONAL and fail-safe' <parity>` | 1 → 0 | **1** | **0** |
| `grep -v '^\s*[/*]' <parity> \| grep -c 'DeliberatelyWiderThanTheTwoRegexes'` | → 0 | **1** | **0** |

Every gate flipped exactly as the plan predicted. No gate was adjusted to make it pass.

### 5. Task 2 — measured corpus counts

| Quantity | Floor (after this plan) | Measured actual |
|---|---|---|
| Corpus size | `MIN_CORPUS_SIZE = 18` | **19** |
| Predicate positives | `MIN_PREDICATE_POSITIVES = 12` | **14** |
| Predicate negatives | `MIN_PREDICATE_NEGATIVES = 4` (unchanged) | **5** |

Negatives, unchanged: `X-Request-Id`, `X-Cook`, `Cook-ie`, `Accept`, `Content-Type` — so a predicate widened to always-true still turns the suite red. Every floor is strictly below its actual, as required.

Sentinel distinctness for the two new entries (`sentinelparityunderscoreprefixed`, `sentinelparityunderscoresession`): neither is a substring of any other sentinel in the list, checked mechanically over all 19.

### 6. Task 3 — the character axis, read from source

The exact character-class TEXT extracted from `Redaction.kt` by `theCoveredSetIsReadFromRedactionSourceNotRetyped`, and the set it expands to:

```
shipped class text      : A-Za-z0-9_-
expands to (COVERED_TCHARS), 64 chars : A-Z  a-z  0-9  _  -
ALL_RFC9110_TCHARS      : 77  (15 punctuation + 10 digits + 52 letters)
NOT_COVERED_TCHARS      : 13
```

**`NOT_COVERED_TCHARS`, exact contents — this is the measurement plan 27-13 must file `AR-27-10` from:**

```
! # $ % & ' * + . ^ ` | ~
```

(as a single string: ``!#$%&'*+.^`|~`` — thirteen characters.)

A later reader can re-check whether this still holds by re-reading ONE constant: if `Redaction.COOKIE_NAME_PART` has moved, `theCoveredSetIsReadFromRedactionSourceNotRetyped` is RED and this table is stale by construction.

Expander non-vacuity, both asserted and green: `expandCharClass("a-c1") == {a, b, c, 1}`, and `expandCharClass("A-Za-z0-9-")` — the PRE-FIX class — is asserted **NOT** equal to `COVERED_TCHARS` (63 vs 64 characters). That second assertion fails on the unfixed tree, which is what makes it a probe rather than a restatement.

### 7. Plan-level verification

| Item | Result |
|---|---|
| 1. Red probe recorded with raw failure output, sentinel present | **PASS** (§1 above) |
| 2. `./gradlew check` exits zero at plan end | **PASS** — `BUILD SUCCESSFUL in 2m 51s`, **1221 tests, 0 failures**; jacoco floors met (mcp tree 71.16% vs floor 65.0%) |
| 3. `git diff -- src/test/…/RedactionTest.kt` is EMPTY | **PASS** — 0 lines |
| 4. `git diff -- .planning/REQUIREMENTS.md` is EMPTY | **PASS** — 0 lines |
| 5. No file under `src/` gained an assertion that a sensitive value survives STRICT/BALANCED | **PASS for this plan's diff** — every added `output.contains(sentinel)` sits under `assertFalse`; the single removed one was the old `assertTrue` survival pin. See "Issues Encountered" for a pre-existing violation elsewhere that this plan does not own. |
| T-27-10-SC (no dependency installs) | **PASS** — `git diff -- build.gradle.kts gradle/libs.versions.toml` is 0 lines |

**Blast radius of the production change**, filtering the cumulative diff to non-comment lines:

```
- private const val COOKIE_NAME_PART = "[A-Za-z0-9-]*"
+ private const val COOKIE_NAME_PART = "[A-Za-z0-9_-]*"
```

That is the entire non-comment production delta of this plan.

## Decisions Made

- **Widen the regex, never narrow the predicate.** The maintainer's stated direction, and the reason is asymmetric: narrowing `isCookieHeaderName` would shrink what `McpToolHelpers.sanitizeHeaders` strips on the MCP path — reopening the gap this phase exists to close.
- **`_` placed before the trailing `-`** inside the character class, so `-` stays last and is read as a literal rather than a range delimiter.
- **`NOT_COVERED_TCHARS` derived, not listed.** A hand-listed complement is a third copy to keep in sync; the derived one cannot disagree with its operands.
- **`requirements-completed: []`.** This plan closes no requirement (see frontmatter note).

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] The file-header "WHICH GUARD COVERS WHICH MUTATION" block became ambiguous once Task 1 renamed a test in the same file**

- **Found during:** Task 2
- **Issue:** The block reads "a NARROWING of `Redaction.isCookieHeaderName` turns **THIS test** red … a NARROWING of either prompt-path regex leaves **this test** GREEN". With only one behavioural test in the file that was unambiguous. Task 1's rename plus Task 2's additions left three tests and no way to tell which "this test" meant — in a comment block whose entire job is telling a maintainer which guard covers which mutation. A stale-by-construction claim in the artifact a maintainer reads first is the exact defect class this phase exists to repair.
- **Fix:** Named the method in each bullet explicitly, and added a third bullet recording that a re-narrowing of `COOKIE_NAME_PART` turns `theUnderscoreNameClassIsStrippedByBothTheRegexesAndThePredicate` red — the one mutation the one-directional implication test structurally cannot see.
- **Files modified:** `src/test/kotlin/com/six2dez/burp/aiagent/redact/CookieHeaderNameParityTest.kt`
- **Verification:** suite green; the claim now matches the file as it stands.
- **Committed in:** `90f206d`

**2. [Rule 2 - Missing Critical] The two new corpus entries would have been covered by NOTHING that can fail**

- **Found during:** Task 2
- **Issue:** The plan's Task 2 acceptance criterion requires "the renamed underscore test passes **for all three underscore names**", but that test as Task 1 left it hardcodes `val name = "my_cookie"`. `X_Cookie` and `session_cookie` would therefore have been exercised only by `everyNameThePromptPathStripsIsMatchedByTheSharedPredicate`, which is **one-directional**: a narrowing of `COOKIE_NAME_PART` shrinks the implication's antecedent and **cannot falsify it**. The two new entries would have raised the floors and asserted nothing — a corpus entry no failing test covers. That is precisely the vacuity failure class this phase keeps being refuted on, one iteration smaller.
- **Fix:** Generalized the test to iterate `PARITY_CORPUS.filter { it.first.contains('_') }`, with an EXACT-count non-vacuity guard (`EXPECTED_UNDERSCORE_NAMES = 3`) so deleting any one of the three turns the test red rather than silently shrinking the evidence (strengthens `T-27-10-04`).
- **Files modified:** `src/test/kotlin/com/six2dez/burp/aiagent/redact/CookieHeaderNameParityTest.kt`
- **Verification:** the generalized test is green post-fix; and with `COOKIE_NAME_PART` temporarily reverted it goes **RED** (probe run and then reverted — `git status` clean afterwards, constant restored byte-identical). Per-name pre-fix leakage for all three names is measured independently in §3 above, because JUnit fails fast and reports only the first.
- **Committed in:** `90f206d`

---

**Total deviations:** 2 auto-fixed (1 bug, 1 missing-critical). Both are inside this plan's own files and its stated intent.
**Impact on plan:** No scope creep. Deviation 2 is what makes the plan's Task 2 acceptance criterion literally true rather than nominally satisfied.

## Issues Encountered

**A pre-existing violation of the "no green survival pin" rule survives elsewhere in the tree, and this plan does not own it.** `27-VERIFICATION-3.md` recorded TWO gaps; this plan closes one. The other — two green `assertTrue(… .contains("api.example.com"))` assertions under `PrivacyMode.STRICT`, at `src/test/kotlin/com/six2dez/burp/aiagent/mcp/tools/McpToolHelpersTest.kt:249` and `:285` — is still present and still green. It belongs to `AR-27-04`, whose disposition is explicitly out of this plan's scope. Recording it here so the phase-level claim is not over-read: **the "no test asserts a sensitive value survives STRICT/BALANCED" property is true of this plan's diff, and is NOT yet true of the repository as a whole.** The standing mechanical sweep that would make it a tree-wide check is plan 27-12's.

**One pre-existing sentinel substring pair, left alone deliberately.** `sentinelparityxcook` (for `X-Cook`) is a substring of `sentinelparityxcookie` (for `X-Cookie`). It predates this plan and is harmless under the file's one-header-line-per-invocation fixture discipline — each blob contains only its own sentinel — so it cannot make an absence assertion satisfiable by the wrong entry. Not fixed: out of scope, and not caused by this plan's changes. Both NEW sentinels were verified clear of any substring relation.

**No `RedactionTest` wall-clock flake occurred.** The known `SafeRegex` 50 ms deadline flake did not fire in any run of this plan; `RedactionTest` was green 46/46 in the five-suite verify and again in the final full `check`. No re-run was needed, so no result here is a re-run result.

## User Setup Required

None — no external service configuration required.

## Next Phase Readiness

- **PRIV-05 is NOT closed by this plan and REQUIREMENTS.md is untouched, by design.** `AR-27-08` (the issue-detail carrier) is owned by Phase 28.
- **Plan 27-13 can now file `AR-27-10` from a MEASUREMENT rather than a prediction.** The exact residual is in §6 above and in source as `CookieHeaderNameWidthTest.NOT_COVERED_TCHARS`, pinned to the shipped constant.
- **Plan 27-12's mechanical sweep must find this plan's artifacts clean** — it should, and it should also find the two `AR-27-04` pins in `McpToolHelpersTest.kt` dirty. That is the expected, honest outcome, not a regression introduced here.
- **The bound to carry forward, stated so no one over-reads this round:** this plan closes the difference set on ONE axis — the characters either side of the token in a header NAME. The emission SHAPE, parameter TYPE and issue-detail RENDERING axes are untouched here, and `CookieHeaderNameWidthTest`'s own KDoc says so before its first assertion.

## Self-Check

- `src/test/kotlin/com/six2dez/burp/aiagent/redact/CookieHeaderNameWidthTest.kt` — FOUND on disk
- `5c27c9a`, `399d0cd`, `90f206d`, `58ec7ff` — all FOUND in `git log`
- `./gradlew check` — EXIT 0, 1221 tests, 0 failures
- STATE.md / ROADMAP.md — NOT modified (worktree mode; orchestrator owns those writes)

## Self-Check: PASSED

---
*Phase: 27-priv-05-gap-closure-sanitize-headers*
*Completed: 2026-08-26*
