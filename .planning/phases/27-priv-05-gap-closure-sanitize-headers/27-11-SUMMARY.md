---
phase: 27-priv-05-gap-closure-sanitize-headers
plan: 11
subsystem: privacy
tags: [redaction, regex, lookbehind, kotlin, mcp, cookie-header, json-escaping]

requires:
  - phase: 27-priv-05-gap-closure-sanitize-headers (plan 27-10)
    provides: "COOKIE_NAME_PART widened to [A-Za-z0-9_-]*, the inverted my_cookie pin, and CookieHeaderNameWidthTest — the file region family this plan edits next"
  - phase: 27-priv-05-gap-closure-sanitize-headers (plans 27-04, 27-05)
    provides: "logicalLineHeaderRule, the single composer carrying cookieHeaderRegex, setCookieHeaderRegex and authHeaderRegex"
provides:
  - "JSON_STRING_OPEN — the THIRD logical-line start: the open of a JSON string value"
  - "A canonical Cookie header at a JSON string open is stripped under STRICT and BALANCED, proven by a probe that failed first with its positive control green in the same run"
  - "An over-match bound: a match beginning at a string open stops at that string's closing quote, gated by a byte-identity assertion on a sibling field"
  - "A header-map non-regression gate asserting the ABSENCE of the [STRIPPED] marker, never the survival of a value"
  - "AR-27-09 — the FOURTH start, still unrecognised, re-measured against the compiled classes and pinned from source"
affects: [27-12, 27-13, phase-28, privacy, redaction]

actuals:
  tokens: 55623
  tasks: 3
  commits: 4

tech-stack:
  added: []
  patterns:
    - "Two SEPARATE fixed-width lookbehinds in a non-capturing alternation, never one lookbehind holding differing widths"
    - "Red probe + positive control in the SAME run as the unit of evidence for a reach claim"
    - "Residual bounds pinned from source by register id, not only recorded in planning documents"

key-files:
  created: []
  modified:
    - src/main/kotlin/com/six2dez/burp/aiagent/redact/Redaction.kt
    - src/test/kotlin/com/six2dez/burp/aiagent/mcp/tools/SerializedEmissionRedactionTest.kt
    - src/test/kotlin/com/six2dez/burp/aiagent/redact/LogicalLineBoundaryScopeTest.kt

key-decisions:
  - "Spelled the escaped branch's start as TWO separate fixed-width lookbehinds rather than one variable-width alternation, preserving the composer's measured 2.4x argument"
  - "Used the scanner_issues (IssueDetails) carrier for the over-match test because HttpRequestResponse declares notes LAST and a sibling-after assertion would have nothing to bite on"
  - "Gated the header-map shape on the ABSENCE of [STRIPPED] rather than on the survival of a value, so the gate can never become a green pin on a leak"
  - "Raised MIN_RATIONALE_LINES 20 -> 90 against a region measured at 125 lines: a floor, not a count"
  - "Widened the AR-27-09 sentence to STRICT and BALANCED after measurement showed the round-3 record was one mode narrower than reality"

patterns-established:
  - "A red probe is only evidence when its positive control fires in the same run — otherwise it is a fixture that proves nothing about reach"
  - "When a residual is re-measured and the prediction was NARROWER than the measurement, widen the record: understating a residual is the same failure as overclaiming a fix"

requirements-completed: []

coverage:
  - id: D1
    description: "A canonical Cookie header that is the FIRST content of a JSON string value has its value replaced by [STRIPPED] under STRICT and under BALANCED, on the real serialized emission shape through toolJson.encodeToString and McpToolContext.redactIfNeeded, with HttpRequestResponse.notes as the carrier"
    requirement: "PRIV-05"
    verification:
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/mcp/tools/SerializedEmissionRedactionTest.kt#JsonStringOpenBoundary.aCanonicalCookieAtTheOpenOfAJsonStringDoesNotSurviveStrict"
        status: pass
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/mcp/tools/SerializedEmissionRedactionTest.kt#JsonStringOpenBoundary.aCanonicalCookieAtTheOpenOfAJsonStringDoesNotSurviveBalanced"
        status: pass
    human_judgment: false
  - id: D2
    description: "The positive control fires on the same run: the same header placed AFTER an escaped newline in the same field is stripped both before and after the fix"
    requirement: "PRIV-05"
    verification:
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/mcp/tools/SerializedEmissionRedactionTest.kt#JsonStringOpenBoundary.theSameCookieHeaderAfterAnEscapedNewlineIsStrippedInBothRedactingModes"
        status: pass
    human_judgment: false
  - id: D3
    description: "A match beginning at a JSON string open terminates at that string's closing quote — the sibling field after the carrier is byte-identical after redaction"
    verification:
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/mcp/tools/SerializedEmissionRedactionTest.kt#JsonStringOpenBoundary.aMatchBeginningAtAJsonStringOpenStopsAtThatStringsClosingQuote"
        status: pass
    human_judgment: false
  - id: D4
    description: "The header-map (ParsedRequest) shape stays out of the composer's reach — no [STRIPPED] marker is introduced there"
    verification:
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/mcp/tools/SerializedEmissionRedactionTest.kt#JsonStringOpenBoundary.theHeaderMapShapeIsStillOutOfTheComposersReach"
        status: pass
    human_judgment: false
  - id: D5
    description: "Real multi-line behaviour and the shared-composer auth family are byte-identical: RedactionTest carries a zero-line diff and both SHIPPED_REAL_MULTILINE_* constants still describe the produced output"
    verification:
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/mcp/tools/SerializedEmissionRedactionTest.kt#AuthHeaderCredentials (7 tests, section byte-identical to base)"
        status: pass
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/redact/RedactionTest.kt (46 tests, zero-line diff)"
        status: pass
    human_judgment: false
  - id: D6
    description: "Both residual register ids (AR-27-04, AR-27-09) are traceable from source, and the boundary fragments plus composer are pinned as five REQUIRED_DECLARATIONS"
    verification:
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/redact/LogicalLineBoundaryScopeTest.kt#theStatedBoundIsPresentWhereAReaderMeetsIt"
        status: pass
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/redact/LogicalLineBoundaryScopeTest.kt#theScopeScanIsNonVacuous"
        status: pass
    human_judgment: false
  - id: D7
    description: "The uncovered FOURTH start (leading-whitespace / obs-folded header line) is a quoted measurement against the compiled classes, deliberately NOT pinned by any committed test, ready for plan 27-13 to file as AR-27-09"
    verification: []
    human_judgment: true
    rationale: "Deliberately unautomated. A committed test asserting the indented value SURVIVES a redacting policy is precisely the artifact class this round exists to remove, so the evidence is a throwaway harness run quoted below plus the source rationale block. Whether AR-27-09 is accepted at medium or scheduled is a maintainer scope decision, not a test outcome."

duration: 31 min
completed: 2026-08-26
status: complete
---

# Phase 27 Plan 11: The JSON String Open Boundary Summary

**`logicalLineHeaderRule` now recognises the open of a JSON string as a logical line start, so a canonical `Cookie:` header written as the first content of `HttpRequestResponse.notes` is stripped under STRICT and BALANCED — proven by a probe that failed first with its positive control green in the same run.**

## Performance

- **Duration:** 31 min
- **Started:** 2026-08-26T10:05:00Z (approximate — precondition run at 10:14Z, first commit 10:18Z)
- **Completed:** 2026-08-26T10:36:18Z
- **Tasks:** 3
- **Files modified:** 3

## Accomplishments

- Closed the reach gap the round-3 verification measured: the CANONICAL spelling — not a variant, not an underscore name, not a typed parameter — defeated the strongest privacy mode at a JSON string open, and now does not.
- Preserved the composer's cost model explicitly: **two separate fixed-width lookbehinds** (width two on the escaped newline, width one on the string open) in a non-capturing alternation, never one lookbehind holding differing widths.
- Bounded the new start on BOTH sides: it reaches the position it was added for, and it does not reach across a JSON string's closing quote (byte-identity assertion on a sibling field) or into a JSON object member (header-map marker-absence gate).
- Re-measured the FOURTH start against the compiled classes and found the round-3 record one mode NARROWER than reality; widened the source sentence rather than leaving the residual understated.

## Task Commits

1. **Task 1 (RED): failing probe at the JSON string open** — `0c38476` (test)
2. **Task 1 (GREEN): the third start boundary** — `31a56d0` (fix)
3. **Task 2: over-match bound, header-map non-regression, source-state pins** — `598a758` (test)
4. **Task 3: measurement-driven correction of the AR-27-09 sentence** — `7c2909d` (docs)

_Task 1 is a `tracer` executed TDD, so it carries the two-commit RED → GREEN pair. No REFACTOR commit was needed._

## Files Created/Modified

- `src/main/kotlin/com/six2dez/burp/aiagent/redact/Redaction.kt` — `JSON_STRING_OPEN` (declared BELOW `JSON_ESCAPED_NEWLINE`), the second fixed-width lookbehind in `logicalLineHeaderRule`'s escaped branch, and the extended rationale block naming all three recognised starts and the uncovered fourth.
- `src/test/kotlin/com/six2dez/burp/aiagent/mcp/tools/SerializedEmissionRedactionTest.kt` — the `JsonStringOpenBoundary` nested class (5 tests), three new `Sentinel` entries, the `notesCarrier` fixture.
- `src/test/kotlin/com/six2dez/burp/aiagent/redact/LogicalLineBoundaryScopeTest.kt` — `JSON_STRING_OPEN` appended as the FIFTH `REQUIRED_DECLARATIONS` entry, `SECOND_OPEN_FINDING = "AR-27-09"` asserted alongside `AR-27-04`, `MIN_RATIONALE_LINES` 20 → 90.

## The Red Run, Verbatim

Run BEFORE any edit to `Redaction.kt`, on `JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test --tests '*SerializedEmissionRedactionTest'`:

```
SerializedEmissionRedactionTest > JsonStringOpenBoundary > aCanonicalCookieAtTheOpenOfAJsonStringDoesNotSurviveBalanced() FAILED
    org.opentest4j.AssertionFailedError at SerializedEmissionRedactionTest.kt:388

SerializedEmissionRedactionTest > JsonStringOpenBoundary > aCanonicalCookieAtTheOpenOfAJsonStringDoesNotSurviveStrict() FAILED
    org.opentest4j.AssertionFailedError at SerializedEmissionRedactionTest.kt:365

27 tests completed, 2 failed
```

The nested class's own result file for that run: `tests="3" skipped="0" failures="2" errors="0"`. The two failure payloads, verbatim from the JUnit XML:

```
a canonical Cookie value at the OPEN of a JSON string must not survive STRICT
(got: {"request":"GET /basket HTTP/1.1\r\nAccept: text/html\r\n\r\n","response":null,
"notes":"Cookie: wibble=sentinelzulu\r\nX-Request-Id: benignidcontrolvalue"})
==> expected: <false> but was: <true>

BALANCED sets stripCookies too, so the value must not survive there either
(got: {"request":"GET /basket HTTP/1.1\r\nAccept: text/html\r\n\r\n","response":null,
"notes":"Cookie: wibble=sentinelnorth\r\nX-Request-Id: benignidcontrolvalue"})
==> expected: <false> but was: <true>
```

**The positive control passed in that same run** — its `<testcase>` element is self-closing with no `<failure>` child:

```
<testcase name="theSameCookieHeaderAfterAnEscapedNewlineIsStrippedInBothRedactingModes()"
  classname="…SerializedEmissionRedactionTest$JsonStringOpenBoundary" time="0.001"/>
```

That combination — two probes red, control green, one run — is what makes this a statement about the rule's REACH rather than a broken fixture.

## The Green Run

Same command plus the neighbour classes, after the `Redaction.kt` edit:

| Class | Tests | Failures | Errors |
|-------|-------|----------|--------|
| `SerializedEmissionRedactionTest$JsonStringOpenBoundary` | 3 (5 after task 2) | 0 | 0 |
| `SerializedEmissionRedactionTest$ProxyHistoryCarrier` | 7 | 0 | 0 |
| `SerializedEmissionRedactionTest$Hazards` | 7 | 0 | 0 |
| `SerializedEmissionRedactionTest$AuthHeaderCredentials` | 7 | 0 | 0 |
| `SerializedEmissionRedactionTest$SiteMapCarrier` / `$IssueDetailsCarrier` / outer | 1 / 1 / 1 | 0 | 0 |
| `LogicalLineBoundaryScopeTest` | 3 | 0 | 0 |
| `RedactionTest` | 46 | 0 | 0 |
| `McpToolHelpersTest` (14 nested) | 76 | 0 | 0 |
| `SerializedEmissionSiteInventoryTest` | 5 | 0 | 0 |
| `ParameterCarrierRedactionTest` | 25 | 0 | 0 |
| `CookieCarrierInventoryTest` | 4 | 0 | 0 |

Task 2's six-class verify: **142 tests, 0 failures, 0 errors, one run.**

## The Indented-Header Measurement (AR-27-09)

> **[SUPERSEDED 2026-08-26 by plan 27-17 — `AR-27-09` is CLOSED BY FIX, not open at LOW.]** The
> statement above is preserved byte-for-byte as the record this plan made; none of it is withdrawn.
> The LOW rested on an explicitly UNMEASURED reachability claim, so the maintainer decided the
> finding by FIX rather than by acceptance at UAT (`27-HUMAN-UAT.md` item 10, commit `ae3371a`).
> `Redaction.logicalLineHeaderRule`'s REAL-LINE branch now starts at `REAL_LINE_START = "^[ \t]*+"`
> instead of a bare `^`, so an indented header line and an obs-folded continuation line ARE
> recognised, in STRICT and BALANCED, across all three composed rules. Measured before/after, the
> consuming-vs-zero-width hazard, the falsified variable-width-lookbehind premise and the two-way
> mutation proof are in the **"AR-27-09 — CLOSED BY FIX 2026-08-26"** section of
> `.planning/phases/26-coverage-static-analysis-debt-docs/26-SECURITY.md`. Gated by
> `IndentedLogicalLineStartTest` and `LogicalLineBoundaryScopeTest`. **PRIV-05 is still `[ ]`**;
> `AR-27-10` and `AR-27-11` are still open. `AR-27-09` is an `AR-` row and was always outside the
> `threats_open` population, which was recomputed and is unchanged at `0`.

Driven through a **throwaway `jshell` harness against the freshly compiled classes** — `build/classes/kotlin/main` plus `kotlin-stdlib-2.2.21` — calling `Redaction.INSTANCE.apply(raw, RedactionPolicy.Companion.fromMode(mode), "round4-measurement-salt", false)`. Not a committed test, deliberately: a committed test asserting this value SURVIVES a redacting policy is exactly the artifact class this round exists to remove. `\r` and `\n` are rendered as two-character escapes below so the raw bytes are legible.

```
== indented-header (AR-27-09) / STRICT
BEFORE: GET / HTTP/1.1\r\n Cookie: a=SECRET5\r\n\r\n
AFTER : GET / HTTP/1.1\r\n Cookie: a=SECRET5\r\n\r\n
SURVIVES: true

== indented-header (AR-27-09) / BALANCED
BEFORE: GET / HTTP/1.1\r\n Cookie: a=SECRET5\r\n\r\n
AFTER : GET / HTTP/1.1\r\n Cookie: a=SECRET5\r\n\r\n
SURVIVES: true
```

The same harness re-drove the shape this plan fixed, confirming the fix holds **outside its own test harness**:

```
== json-string-open (the fix) / STRICT
BEFORE: {"notes":"Cookie: a=SECRET1\r\nX: y"}
AFTER : {"notes":"Cookie: [STRIPPED]\r\nX: y"}

== json-string-open (the fix) / BALANCED
BEFORE: {"notes":"Cookie: a=SECRET1\r\nX: y"}
AFTER : {"notes":"Cookie: [STRIPPED]\r\nX: y"}

== json-string-open positive control / STRICT
BEFORE: {"notes":"X: y\r\nCookie: a=SECRET9"}
AFTER : {"notes":"X: y\r\nCookie: [STRIPPED]"}

== real-line control (unchanged branch) / STRICT
BEFORE: GET / HTTP/1.1\r\nCookie: a=SECRET7\r\n\r\n
AFTER : GET / HTTP/1.1\r\nCookie: [STRIPPED]\r\n\r\n
```

## Premises Task 1 Wrote and Task 3 Measured

**No premise was falsified. One was too NARROW, and was widened.**

| | |
|---|---|
| **Original prediction (task 1)** | "A header line preceded by LEADING HORIZONTAL WHITESPACE … MEASURED surviving under **STRICT** in round 3 on the shape `GET / HTTP/1.1\r\n Cookie: a=SECRET5\r\n\r\n`." |
| **Measurement (task 3)** | Survives byte-unchanged under **STRICT and BALANCED**, re-driven against the compiled classes at the end of round 4. One mode wider than the round-3 record. |
| **Corrected text (shipped)** | "MEASURED surviving under STRICT in round 3, and RE-MEASURED against the compiled classes at the end of round 4 — where it survives BYTE-UNCHANGED under BOTH STRICT and BALANCED, which is one mode WIDER than round 3 recorded", followed by both raw shapes inline. |

Recorded here in all three parts so plan 27-13 writes the `AR-27-09` row and the `WINDOWS.md` entry from a measurement rather than from a prediction. The direction of the error matters: understating a residual is the same failure mode as overclaiming a fix.

## The Rationale Region

- **Before this plan:** 78 comment lines above `private const val JSON_ESCAPED_NEWLINE`.
- **After this plan:** **125** comment lines.
- `MIN_RATIONALE_LINES` raised 20 → **90** — strictly below the measured 125, and a floor rather than a count, matching the file's own `MIN_EXPECTED_LINES` discipline (1500 against a measured 2018). Prose edits stay free; gutting the stated bounds turns red.

The walk-back anchor is unchanged: `private const val JSON_ESCAPED_NEWLINE` is still `REQUIRED_DECLARATIONS.first()` and `JSON_STRING_OPEN` was appended at the END, so `MIN_RATIONALE_LINES` still measures the region it measured before.

## Whole-Suite Pass

| Command | Result | Wall clock |
|---------|--------|-----------|
| `./gradlew check` | **BUILD SUCCESSFUL** | 2m 53s |
| `./gradlew test --rerun` | **BUILD SUCCESSFUL** | 2m 50s |

Totalled from `build/test-results/test/`: **174 test classes / nested classes, 1226 tests, 0 failures, 0 errors, 1 skipped.**

The single skip is `ExternalMcpClientManagerTest.connectAndListTools_returnsExpectedCount()`, `@Disabled("Requires live MCP server")` since Phase 16. It is **pre-existing and not introduced by this plan**.

`jacocoMcpTreeCoverageVerification`: mcp tree line coverage 71.16% (2869/4032), floor 65.0% — MET.

**No `RedactionTest` wall-clock flake was encountered.** `RedactionTest` ran green (46 tests) in every run of this plan, so no re-run was needed to distinguish a `SafeRegex` deadline timeout from a real change.

## Timing, Stated Rather Than Assumed

The observed durations of THIS run are recorded above. **No before/after comparison was made**, because the pre-change suite duration is not recoverable retrospectively. Nothing here should be read as a performance claim in either direction. The only cost statement this plan makes is structural: the two lookbehinds are spelled separately and each is fixed-width, so the composer's previously measured 2.4x fixed-width argument is preserved by construction rather than re-measured.

## Decisions Made

- **Two separate fixed-width lookbehinds, not one variable-width alternation.** `(?:(?<=\\[rn])|(?<="))`. The shorter spelling would have traded away the composer's measured 2.4x silently, at every position of every serialized emission.
- **The over-match carrier is `IssueDetails`, not `HttpRequestResponse`.** `HttpRequestResponse` declares `notes` LAST, so on that carrier there is no sibling field after the one under test and the byte-identity assertion would have had nothing to bite on. `IssueDetails` carries the same `notes` one level deeper, followed by `collaboratorInteractions` and `definition` — a real emission shape, not one invented to make the assertion possible. `notes` ends immediately after the cookie value there, so the tail's only available terminator is the closing quote itself: the hardest form of the case.
- **The header-map gate asserts the ABSENCE of `[STRIPPED]`.** `[STRIPPED]` is produced by exactly two things in `Redaction.kt` — `cookieHeaderRegex` and `setCookieHeaderRegex` — so its absence *is* the proof that the composer did not begin matching a JSON object member. Asserting instead that some value survived STRICT would have been a green pin on a leak.
- **`SECOND_OPEN_FINDING` is a named constant, not an inline string,** mirroring how `OPEN_FINDING` carries `AR-27-04`.

## Deviations from Plan

None — plan executed exactly as written.

Two things the plan left to judgment and how they were resolved, recorded because a later reader will otherwise have to re-derive them:

1. **The over-match carrier.** The plan said "a cookie header at the open of the `notes` value AND a sibling field after it". `HttpRequestResponse` declares `notes` last, so that combination is not constructible on that carrier. Resolved by moving one carrier deeper to the scanner_issues shape, which satisfies the plan's stated property exactly and is a real emission shape. Not treated as a deviation because the plan's *property* is met unchanged.
2. **`MIN_RATIONALE_LINES`.** The plan said to raise the floor "only if the region grew". It grew, 78 → 125, so it was raised — to 90, not to 125, following the file's own floor-not-a-count discipline.

## Issues Encountered

Two ktlint violations on first-pass formatting (`First line of body expression fits on same line as function signature`; `Expected newline before '.'` on a four-link call chain). Both fixed before the respective commit; `ktlintCheck` and `detekt` are green.

## Bounds — read before quoting any of this as evidence

- **This plan closes no requirement. PRIV-05 stays `[ ]`.** `.planning/REQUIREMENTS.md` is untouched (zero-line diff) and `requirements-completed` is empty above.
- **The boundary is NOT complete.** It recognises three logical line starts. The FOURTH — a leading-whitespace or obs-folded header line — is measured surviving under STRICT *and* BALANCED, is deliberately out of this round's scope, and is filed as `AR-27-09` by plan 27-13. Its one-token fix (`^[ \t]*` in place of `^` on the real-line branch) is written down beside it in `Redaction.kt` so a successor need not re-derive it.
- **`hostHeaderRegex` remains excluded** and that exclusion is still `AR-27-04`, requiring a HUMAN decision. Not relitigated and not fixed here.
- **The two `assertTrue(...contains("api.example.com"))` pins at `McpToolHelpersTest.kt:249`/`:285` are still present.** They belong to plan 27-12 and were deliberately left untouched. So the phase-wide property "no test under `src/` asserts a sensitive value survives STRICT or BALANCED" is **not yet true**; what IS true is that *this plan* added no such assertion — every positive assertion in its `src/test` diff is a fixture guard on pre-redaction input, the benign control value, the `[STRIPPED]` marker, or a source-text check.
- **The claim is bounded to the serialized emission path and the cookie-header and exact-name auth-header classes**, as it was before.

> **[SUPERSEDED 2026-08-26 by plan 27-17 — `AR-27-09` is CLOSED BY FIX, not open at LOW.]** The
> statement above is preserved byte-for-byte as the record this plan made; none of it is withdrawn.
> The LOW rested on an explicitly UNMEASURED reachability claim, so the maintainer decided the
> finding by FIX rather than by acceptance at UAT (`27-HUMAN-UAT.md` item 10, commit `ae3371a`).
> `Redaction.logicalLineHeaderRule`'s REAL-LINE branch now starts at `REAL_LINE_START = "^[ \t]*+"`
> instead of a bare `^`, so an indented header line and an obs-folded continuation line ARE
> recognised, in STRICT and BALANCED, across all three composed rules. Measured before/after, the
> consuming-vs-zero-width hazard, the falsified variable-width-lookbehind premise and the two-way
> mutation proof are in the **"AR-27-09 — CLOSED BY FIX 2026-08-26"** section of
> `.planning/phases/26-coverage-static-analysis-debt-docs/26-SECURITY.md`. Gated by
> `IndentedLogicalLineStartTest` and `LogicalLineBoundaryScopeTest`. **PRIV-05 is still `[ ]`**;
> `AR-27-10` and `AR-27-11` are still open. `AR-27-09` is an `AR-` row and was always outside the
> `threats_open` population, which was recomputed and is unchanged at `0`.

## Known Stubs

None. No stub, TODO, FIXME, skipped test or unrun `<verify>` was introduced by this plan. The single suite-wide skip is pre-existing (Phase 16, `@Disabled`, needs a live MCP server) and is not this plan's window.

## Threat Flags

None. No new network endpoint, auth path, file-access pattern or schema change at a trust boundary was introduced. `git diff -- build.gradle.kts gradle/libs.versions.toml` is zero lines, so `T-27-11-SC` (dependency installs) is satisfied with no package-legitimacy checkpoint owed.

Threat register dispositions, verified:

| Threat ID | Disposition | Evidence |
|-----------|-------------|----------|
| T-27-11-01 | mitigate | `JSON_STRING_OPEN` added; red probe → green, control fired in the red run |
| T-27-11-02 | mitigate | `aMatchBeginningAtAJsonStringOpenStopsAtThatStringsClosingQuote` (byte-identity on sibling field) + `assertSameJsonShape` on every new probe |
| T-27-11-03 | accept | Re-measured above, quoted verbatim, filed as `AR-27-09` for plan 27-13 |
| T-27-11-04 | mitigate | Composer body read: exactly two `(?<=` occurrences, each fixed-width, neither holding an alternation |
| T-27-11-05 | mitigate | `AuthHeaderCredentials` section byte-identical to base (sha256 `be57ab3628c47330`, 189 lines, both before and after); both `SHIPPED_REAL_MULTILINE_*` constants untouched |
| T-27-11-06 | mitigate | Rationale block names the fourth start; `theStatedBoundIsPresentWhereAReaderMeetsIt` asserts both `AR-27-04` and `AR-27-09`; task 3 corrected the sentence against measurement |
| T-27-11-07 | transfer | `hostHeaderRegex` untouched; `EXCLUDED_RULE` and `COMPOSED_RULES` unchanged in the diff |
| T-27-11-SC | accept | Zero-line dependency diff |

## Verification Checklist

| # | Check | Result |
|---|-------|--------|
| 1 | Red probe raw output recorded, two probes failing AND control passing in one run | ✓ quoted above |
| 2 | Post-fix output recorded for the same three tests | ✓ 3/3 then 5/5 green |
| 3 | `./gradlew check` and `./gradlew test` both exit zero | ✓ 2m53s / 2m50s |
| 4 | `git diff -- src/test/…/RedactionTest.kt` is EMPTY | ✓ 0 lines |
| 5 | `git diff -- .planning/REQUIREMENTS.md` is EMPTY | ✓ 0 lines |
| 6 | No new test asserts a sensitive value survives STRICT or BALANCED | ✓ every added assertion reviewed |
| 7 | Indented-header measurement quoted verbatim, NOT pinned by any committed test | ✓ throwaway jshell harness only |
| 8 | `JSON_STRING_OPEN` declared at a line GREATER than `JSON_ESCAPED_NEWLINE` | ✓ 260 → 271 |
| 9 | `REQUIRED_DECLARATIONS` holds FIVE entries, none removed | ✓ removed-entry grep returns `0` |
| 10 | `JSON_ESCAPED_NEWLINE` still `REQUIRED_DECLARATIONS.first()` | ✓ index 0 |
| 11 | `grep -c 'AR-27-09'` in `Redaction.kt` and in `LogicalLineBoundaryScopeTest.kt` | ✓ ≥ 1 each |
| 12 | `COMPOSED_RULES` still three rules, `EXCLUDED_RULE` unchanged | ✓ diff grep returns `0` |
| 13 | `ktlintCheck` + `detekt` green | ✓ |

## Self-Check: PASSED

- All three modified files exist on disk and carry the claimed symbols (`JSON_STRING_OPEN` at `Redaction.kt:271`, `JsonStringOpenBoundary` in `SerializedEmissionRedactionTest.kt`, five `REQUIRED_DECLARATIONS` entries in `LogicalLineBoundaryScopeTest.kt`).
- All four commit hashes exist on this branch: `0c38476`, `31a56d0`, `598a758`, `7c2909d`.
- Every task's `<acceptance_criteria>` was re-run mechanically; results in the table above.
- Plan-level `<verification>` re-run; all seven items pass.

## User Setup Required

None — no external service configuration required.

## Next Phase Readiness

Ready for plan 27-12 (the mechanical sweep for green survival pins, which owns the two `api.example.com` assertions in `McpToolHelpersTest`) and plan 27-13 (which files `AR-27-09` in the register and the `WINDOWS.md` entry, and routes `AR-27-04` to `27-HUMAN-UAT.md`).

Everything 27-13 needs from this plan is quoted above as a measurement rather than a prediction: the raw before/after strings under both modes, the direction in which the round-3 record was too narrow, and the one-token fix already written into the source beside the residual.

**Not ready for:** any statement that PRIV-05 is closed. It is not, this plan does not close it, and round 4 was never scoped to.

---
*Phase: 27-priv-05-gap-closure-sanitize-headers*
*Completed: 2026-08-26*
