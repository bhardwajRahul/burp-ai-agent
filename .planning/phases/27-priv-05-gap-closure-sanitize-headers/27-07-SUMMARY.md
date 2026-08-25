---
phase: 27-priv-05-gap-closure-sanitize-headers
plan: 07
subsystem: security
tags: [privacy, redaction, mcp, montoya, kotlin, cookies, junit5, mockito]

requires:
  - phase: 27-priv-05-gap-closure-sanitize-headers
    provides: "sanitizeHeaders cookie parity (waves 1-6); Redaction.isCookieHeaderName as the owned header-name predicate; SerializedEmissionRedactionTest's sentinel discipline and real-serializer fixture pattern"
provides:
  - "Redaction.isCookieParameterType — the type-keyed cookie-parameter predicate, sited beside isCookieHeaderName"
  - "McpToolHelpers.sanitizeParameters — the sole producer of ParsedParam in the repository"
  - "Four rewired MCP producers (request_parse x2, params_extract x2) routing through one sanitizer"
  - "BountyPromptTagResolver.buildRequestParameters gated on the same predicate"
  - "ParameterCarrierRedactionTest — 18 probes: both MCP shapes in all three modes, OFF control, non-cookie controls, enum-parity preservation fixture, producer-ownership pin with its bound and a live worked example"
  - "Four new BountyPromptTagResolverTest probes on the resolved tag output"
affects: [27-08, 27-09, priv-05, mcp-tools, bounty-prompts]

actuals:
  tokens: 13889
  tasks: 3
  commits: 3

tech-stack:
  added: []
  patterns:
    - "Type-keyed privacy controls: key on a closed host enum, never on a rendered string, so a format change cannot silently defeat the control"
    - "Producer-ownership pin: source-scan a named file set for one call shape, assert the count against a tool-name set, and state the bound with a live worked example"

key-files:
  created:
    - src/test/kotlin/com/six2dez/burp/aiagent/mcp/tools/ParameterCarrierRedactionTest.kt
  modified:
    - src/main/kotlin/com/six2dez/burp/aiagent/redact/Redaction.kt
    - src/main/kotlin/com/six2dez/burp/aiagent/mcp/tools/McpToolHelpers.kt
    - src/main/kotlin/com/six2dez/burp/aiagent/mcp/tools/McpToolExecutorImpl.kt
    - src/main/kotlin/com/six2dez/burp/aiagent/mcp/tools/McpToolLegacy.kt
    - src/main/kotlin/com/six2dez/burp/aiagent/prompts/bountyprompt/BountyPromptTagResolver.kt
    - src/test/kotlin/com/six2dez/burp/aiagent/prompts/bountyprompt/BountyPromptTagResolverTest.kt

key-decisions:
  - "D-27-17 applied: one type-keyed predicate + one shared sanitizer at the producer, rather than per-site inline strips or a widened shape-bound regex"
  - "D-27-18 applied: the stripped value is the same [STRIPPED] marker sanitizeHeaders writes; ParsedParam's field set and nullability are byte-unchanged"
  - "D-27-21 applied: the bounty-prompt type gate is added ALONGSIDE the pre-existing sensitiveParamName name filter, ordered so the cookie gate wins when both apply"
  - "Deviation: ParameterCarrierRedactionTest is FLAT rather than @Nested, because JUnit writes each @Nested class to its own XML and the plan's by-name acceptance gate would otherwise be unsatisfiable"
  - "Deviation: a producer-ownership pin was pulled forward into task 1, because HttpRequest.httpRequest() cannot run in a pure-JVM test and no behavioural probe can reach the production branch"

patterns-established:
  - "Type-keyed over shape-keyed: the control reads HttpParameterType and never a rendered string"
  - "Enum-parity preservation fixture: when a refactor changes which accessor renders a token, assert accessor equivalence over every enum constant rather than assuming it"
  - "Bounds stated with a live worked example a reader can go and look at, not abstractly"

requirements-completed: [PRIV-05]

coverage:
  - id: D1
    description: "A COOKIE-typed parameter sentinel does not survive request_parse's serialized ParsedRequest under STRICT or BALANCED, does survive under OFF, and non-cookie types are untouched in all three modes"
    requirement: PRIV-05
    verification:
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/mcp/tools/ParameterCarrierRedactionTest.kt#cookieSentinelDoesNotSurviveTheSerializedRequestParseShapeUnderStrict"
        status: pass
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/mcp/tools/ParameterCarrierRedactionTest.kt#cookieSentinelDoesNotSurviveTheSerializedRequestParseShapeUnderBalanced"
        status: pass
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/mcp/tools/ParameterCarrierRedactionTest.kt#cookieSentinelDoesSurviveTheSerializedRequestParseShapeUnderOff"
        status: pass
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/mcp/tools/ParameterCarrierRedactionTest.kt#urlTypedParameterValueIsUntouchedInEveryMode"
        status: pass
    human_judgment: false
  - id: D2
    description: "A COOKIE-typed parameter sentinel does not survive params_extract's line shape under STRICT or BALANCED, and the line is byte-identical for non-cookie types"
    requirement: PRIV-05
    verification:
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/mcp/tools/ParameterCarrierRedactionTest.kt#cookieSentinelDoesNotSurviveTheParamsExtractLineShapeUnderStrict"
        status: pass
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/mcp/tools/ParameterCarrierRedactionTest.kt#nonCookieParamsExtractLinesAreUnchangedInEveryMode"
        status: pass
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/mcp/tools/ParameterCarrierRedactionTest.kt#everyHttpParameterTypeRendersTheSameTokenThroughNameAsThroughToString"
        status: pass
    human_judgment: false
  - id: D3
    description: "All four MCP producers route through one sanitizer; neither executor constructs ParsedParam itself; the two surviving direct value readers are find_reflected"
    requirement: PRIV-05
    verification:
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/mcp/tools/ParameterCarrierRedactionTest.kt#theProducerInventoryIsExactlyFourAndEveryOneRoutesThroughTheSanitizer"
        status: pass
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/mcp/tools/ParameterCarrierRedactionTest.kt#theProducerScanIsNonVacuous"
        status: pass
    human_judgment: false
  - id: D4
    description: "BountyPromptTagResolver's parameters tag emits the marker instead of a cookie value under STRICT and BALANCED, while the pre-existing name filter and non-cookie pass-through are unchanged"
    requirement: PRIV-05
    verification:
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/prompts/bountyprompt/BountyPromptTagResolverTest.kt#cookieTypedParameterValueIsStrippedFromTheParametersTagUnderStrict"
        status: pass
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/prompts/bountyprompt/BountyPromptTagResolverTest.kt#theExistingSensitiveNameFilterAndPassThroughAreBothUnchanged"
        status: pass
    human_judgment: false
  - id: D5
    description: "The Montoya half of the defect — that HttpRequest.parameters() actually yields COOKIE-typed entries inside a live Burp process for a request built by HttpRequest.httpRequest(rawContent)"
    requirement: PRIV-05
    verification: []
    human_judgment: true
    rationale: "HttpRequest.httpRequest() is a Montoya static factory requiring Burp's internal ObjectFactory; it cannot run in a pure-JVM test (McpToolScopeEnforcementTest records the same constraint). 27-VERIFICATION-2.md carried this as an open human_verification item and this plan does not close it. Requires loading the fat JAR in a live Burp, setting Privacy to STRICT, and calling params_extract / request_parse with a raw request carrying a Cookie header."

duration: 41 min
completed: 2026-08-25
status: complete
---

# Phase 27 Plan 07: The COOKIE-Typed Parameter Carrier Summary

**Cookie values no longer reach an AI backend through `HttpParameterType.COOKIE` parameters: one type-keyed predicate and one shared sanitizer now gate all four MCP producers plus the bounty-prompt resolver, proven by 22 behavioural probes and five recorded red probes.**

## Performance

- **Duration:** 41 min
- **Started:** 2026-08-25T10:31:00Z
- **Completed:** 2026-08-25T11:12:00Z
- **Tasks:** 3
- **Files modified:** 7 (1 created, 6 modified)

## Which carrier this plan closed, and which it did not

**CLOSED — the MCP + prompt-path COOKIE-typed parameter carrier.** A cookie sentinel supplied as a
`HttpParameterType.COOKIE` parameter does not appear in the serialized `ParsedRequest` under STRICT
or BALANCED, does not appear in the `params_extract` line output under STRICT or BALANCED, in
EITHER executor, and does not appear in `BountyPromptTagResolver`'s parameters tag.

**NOT CLOSED — PRIV-05 as a whole.** This plan closes ONE carrier. Specifically still open:

1. **`InjectionPointExtractor.kt:29`** keeps its own `it.type().name == "COOKIE"` predicate. Named,
   byte-unchanged, deliberately deferred (D-27-17) with the issue-detail carrier its value feeds.
   Plan 27-08 task 3 measures that route; plan 27-09 files it.
2. **Non-cookie parameter types carrying tokens** (T-27-07-06). A URL- or BODY-typed parameter named
   `access_token` may survive `request_parse`'s JSON shape — `jsonSecretKeyRegex` keys on the JSON
   KEY, which here is the literal `value`. Deliberately out of scope (D-27-20), MEASURED by plan
   27-08 task 3, not assumed either way here.
3. **The wider `BountyPromptTagResolver` bypass.** Because the tag value never passes
   `Redaction.apply`, a JWT/bearer/secret in a URL- or BODY-typed parameter VALUE reaches the prompt
   verbatim in EVERY mode. Recorded in source; plan 27-09 records it as a named residual.
4. **The live-Burp Montoya half** (coverage D5) stays a human_verification item, unchanged from
   `27-VERIFICATION-2.md`.

## Measured baselines, before and after

Each measured on this tree, comment-only lines (`^\s*(//|\*|/\*)`) stripped, re-measured rather than
inherited.

| # | Measurement | Plan said TODAY | MEASURED before | Required AFTER | MEASURED after | OK |
|---|-------------|-----------------|-----------------|----------------|----------------|-----|
| B1 | `isCookieParameterType` in `src/main/kotlin` | 0 | **0** | 3 | **3** | yes |
| B2 | `sanitizeParameters` in `src/main/kotlin` | 0 | **0** | 5 | **5** | yes |
| B3 | `ParsedParam(` in the two executors | 2 | **2** (`McpToolLegacy.kt:182`, `McpToolExecutorImpl.kt:372`) | 0 | **0** | yes |
| B4 | `ParsedParam(` in `McpToolHelpers.kt` | 0 | **0** | 1 | **1** | yes |
| B5 | `param.value()` in the two executors | 6 | **6** | 2 | **2** | yes |
| B6 | `BountyPromptTagResolver` instantiations in `src/main/kotlin` | 0 | **0** | 0, re-measured | **0** | yes |
| B7 | `git diff HEAD -- RedactionTest.kt \| wc -l` | 0 | **0** | 0 | **0** | yes |
| B8 | `ParameterCarrierRedactionTest.kt` exists | no | **no** | yes | **yes** | yes |
| B9 | `COOKIE` label as a literal, comment lines stripped | 2 | **2** | 3 | **3** | yes |

**B1 after, all three sites:**
```
McpToolHelpers.kt:393       policy.stripCookies && Redaction.isCookieParameterType(typeName) -> "[STRIPPED]"
Redaction.kt:335            fun isCookieParameterType(typeName: String): Boolean = ...
BountyPromptTagResolver.kt:151  policy.stripCookies && Redaction.isCookieParameterType(param.type().name) -> "[STRIPPED]"
```

**B2 after, all five sites:** declaration at `McpToolHelpers.kt:382`, calls at
`McpToolLegacy.kt:160` (params_extract), `McpToolLegacy.kt:189` (request_parse),
`McpToolExecutorImpl.kt:360` (params_extract), `McpToolExecutorImpl.kt:381` (request_parse).

**B5 after — the two survivors, by file:line:**
```
McpToolExecutorImpl.kt:407    val value = param.value()   <- find_reflected
McpToolLegacy.kt:223          val value = param.value()   <- find_reflected
```
Both emit `name=… type=… count=…` and never the value. This is the sharpest gate in the plan: a
positive count dropping 6 -> 2, unsatisfiable by any subset of the work.

### B9, with both populations labelled

The two numbers in play are different and conflating them is how this phase produced the claim it
is repairing.

- **SEMANTIC population — cookie-parameter PREDICATES: 1 before -> 2 after.** Before:
  `InjectionPointExtractor.kt:29` only. After: that site (byte-unchanged) plus
  `Redaction.isCookieParameterType`.
- **MECHANICAL population — the `COOKIE` label as a literal, which B9's fixed command measures:
  2 before -> 3 after.** This is broader: it also catches `InjectionPointExtractor.kt:169`'s
  label-to-`InjectionType` mapper, which is not a predicate at all.

Command run verbatim. **BEFORE (2):**
```
InjectionPointExtractor.kt:29   request.parameters().filter { it.type().name == "COOKIE" }...   <- PREDICATE
InjectionPointExtractor.kt:169  "COOKIE" -> InjectionType.COOKIE                                <- label mapper
```
**AFTER (3):** those two byte-unchanged, plus
`Redaction.kt:343  private const val COOKIE_PARAMETER_TYPE_NAME = "COOKIE"`.

**Comment-strip confirmation, run both ways as criterion 8 requires.** WITH the strip: **2 before,
3 after**. WITHOUT the strip: **4 before, 8 after**. The gap is entirely KDoc prose — including the
sentence naming `InjectionPointExtractor.kt:29`. That is the point: the strip is what stops
documentation of a deferral from inflating the count of the thing deferred. A result of 2 after
would have meant the predicate was written without matching the label at all; 4+ would have meant an
unauthorized site was converted or added.

**The deferred site is untouched AND named.** `git diff HEAD -- .../scanner/InjectionPointExtractor.kt | wc -l`
returns **0**. The predicate's KDoc names it verbatim:

> `scanner/InjectionPointExtractor.kt:29` writes its own `it.type().name == "COOKIE"` filter and is
> deliberately NOT converted (D-27-17): its value feeds `InjectionPoint.originalValue` and from
> there the issue-detail route, which plan 27-08 task 3 measures and plan 27-09 files. Swapping the
> predicate there would change nothing about that route's disclosure while making the route look
> addressed.

## The five red probes, with the specific assertion that went red

Each was applied, run with `--rerun`, recorded, and restored.

**Probe 1 — predicate forced to constant false (task 1).** 6 of 13 red. Named assertion:
```
cookieSentinelDoesNotSurviveTheSerializedRequestParseShapeUnderStrict()
AssertionFailedError: the cookie value reached the serialized tool result:
{"method":"GET",...,"parameters":[{"type":"COOKIE","name":"wibble","value":"paramindia"}],...}
==> expected: <false> but was: <true>
```
The OFF control and both non-cookie controls stayed GREEN, so the probe proved the STRICT/BALANCED
gates specifically and not a blanket failure.

**Probe 2 — `request_parse` reverted to inline `ParsedParam` construction (task 1).** 1 of 13 red.
Named assertion:
```
theRequestParseProducerRoutesThroughTheSanitizer()
AssertionFailedError: com/.../McpToolExecutorImpl.kt no longer calls `sanitizeParameters(`.
Its request_parse branch is emitting parameter values with no cookie control — the exact state
27-VERIFICATION-2.md recorded. ==> expected: <true> but was: <false>
```

**Probe 3 — sanitizer call dropped from ONE producer via rename (task 2).** Run twice: first with a
delegating stub, then with a genuine bypass constructing `ParsedParam` raw. Both times 1 of 18 red.
Named assertion:
```
theProducerInventoryIsExactlyFourAndEveryOneRoutesThroughTheSanitizer()
AssertionFailedError: com/.../McpToolLegacy.kt carries 1 `sanitizeParameters(` calls, not 2.
==> expected: <2> but was: <1>
```

> **FINDING — criterion 5 expected BOTH the behavioural probe and the ownership pin to go red. Only
> the pin did, and this is reported rather than assumed** (`WINDOWS.md` entry 13 records a probe in
> this phase that failed the wrong assertion). The behavioural probes CANNOT go red on a producer
> unwiring, because they cannot reach the production branch: every producer begins
> `HttpRequest.httpRequest(content)`, a Montoya static factory needing Burp's internal ObjectFactory
> that cannot run in a pure-JVM test. That is a real division of labour, not a gap: the behavioural
> probes prove the SANITIZER, the ownership pin proves the PRODUCERS are wired to it, and it is
> stated in the pin's KDoc. Note also that JUnit stops a test method at its first failed assertion,
> so the pin's `HELPERS_FILE` count assertion (which the genuine-bypass variant would also have
> tripped) never got to run.

**Probe 4 — bounty-prompt type gate removed (task 3).** 2 of 5 red. Named assertions:
`cookieTypedParameterValueIsStrippedFromTheParametersTagUnderStrict()` and
`...UnderBalanced()`. The OFF assertion stayed GREEN, exactly as criterion 5 requires.
```
AssertionFailedError: a COOKIE-typed parameter value reached the prompt under STRICT:
[1] GET https://host-976a3349780d.local/a
URL: https://host-976a3349780d.local/a
Parameters:
PHPSESSID=bountyalfa (COOKIE)
```
**That leaked line is independently informative.** It renders the exact `name=value (COOKIE)` shape
`Redaction.cookieTypedParamRegex` is written to catch — and it survived anyway. That is live
confirmation of D-27-21's premise: this tag value never passes `Redaction.apply`, so the rule that
would have caught it never runs. It also confirms the `sensitiveParamName` name filter does not
match `PHPSESSID`.

## The BountyPromptTagResolver latency re-measurement

Re-measured at execution time BEFORE any change, per criterion 1. Four independent searches, not
just the bare type name:

```
$ grep -rn --include=*.kt 'BountyPromptTagResolver' src/main/kotlin
  Redaction.kt:259, :327, :491, :1960          <- all comment/KDoc prose
  BountyPromptTagResolver.kt:9                 <- the class's own declaration

$ grep -rn --include=*.kt 'BountyPromptTagResolver()' src/
  BountyPromptTagResolverTest.kt:79            <- the ONLY instantiation in the repository

$ grep -rn --include=*.kt -iE 'tagResolver|TagResolver\b' src/main/kotlin
  (same five hits — no injection point, no factory, no DI binding)

$ grep -rn --include=*.kt 'buildRequestParameters\|HTTP_REQUESTS_PARAMETERS' src/
  BountyPromptModels.kt:48, BountyPromptTagResolver.kt:88, :90, :113
  (all internal to the resolver and its own enum)
```

**Result: ZERO instantiations under `src/main/kotlin`.** B6 holds at 0 and the plan's number is
confirmed rather than inherited. The class remains dead code in production, so **T-27-07-04 stays
`medium`** and plan 27-09 should record it at that severity. Had this returned non-zero it would be
a LIVE leak of Burp-held request data and this SUMMARY would have led with it.

## The enum-parity fixture result

`everyHttpParameterTypeRendersTheSameTokenThroughNameAsThroughToString()` — **PASSES**. Every
`HttpParameterType` constant satisfies `toString() == name()`, so moving the `params_extract` type
token from the Montoya enum's `toString()` to `ParsedParam.type` (which is `HttpParameterType.name`)
is shape-preserving for every constant. The `params_extract` line is therefore byte-identical to its
pre-change output for every non-cookie parameter — measured, not asserted. The fixture also guards
`HttpParameterType.entries.isNotEmpty()` so it cannot pass vacuously.

## Accomplishments

- `Redaction.isCookieParameterType` — type-keyed, exact-comparison, `Locale.ROOT`, `String`-typed so
  `redact/` stays Montoya-free at its API edge. Both bounds in its KDoc.
- `McpToolHelpers.sanitizeParameters` — sole `ParsedParam` producer, sited directly beneath
  `sanitizeHeaders` so the two controls on the two fields of one JSON object are read together.
- Four MCP producers rewired; `param.value()` in the executors 6 -> 2.
- `BountyPromptTagResolver` gated, with the unadopted `Redaction.apply` alternative and the wider
  non-cookie bypass both recorded in source where the next reader meets them.
- 22 new tests across two files, all present by name in the JUnit XML with `failures="0" errors="0"`.

## Task Commits

1. **Task 1 (tracer): request_parse + predicate + sanitizer + tracer probes** — `76049e0` (feat)
2. **Task 2: the remaining three producers + enum parity + ownership pin** — `f4a243e` (feat)
3. **Task 3: the bounty-prompt carrier** — `838751a` (feat)

**Tracer feedback gate:** after `76049e0` the tracer's `<verify>` was re-run end-to-end before any
expansion task — 13/13 green, `failures="0" errors="0"`. Logged `⚡ Tracer verified end-to-end —
expanding`. Auto-mode was read from `.planning/config.json`'s `mode: yolo` together with the plan's
`autonomous: true` and D-27-23's own statement that this run mode auto-selects blocking checkpoints;
`workflow.auto_advance` and `workflow._auto_chain_active` are both `false`, so this reading is
recorded rather than left implicit.

## Files Created/Modified

- `src/main/kotlin/.../redact/Redaction.kt` — `isCookieParameterType` + `COOKIE_PARAMETER_TYPE_NAME`
- `src/main/kotlin/.../mcp/tools/McpToolHelpers.kt` — `sanitizeParameters`
- `src/main/kotlin/.../mcp/tools/McpToolExecutorImpl.kt` — both producers rewired
- `src/main/kotlin/.../mcp/tools/McpToolLegacy.kt` — both producers rewired
- `src/main/kotlin/.../prompts/bountyprompt/BountyPromptTagResolver.kt` — type gate + recorded residuals
- `src/test/kotlin/.../mcp/tools/ParameterCarrierRedactionTest.kt` — NEW, 18 tests
- `src/test/kotlin/.../prompts/bountyprompt/BountyPromptTagResolverTest.kt` — 4 tests added

## Decisions Made

All four locked plan decisions (D-27-17, D-27-18, D-27-20, D-27-21) applied as written. No
`checkpoint:decision` emitted, per D-27-23. Two execution-time decisions are recorded as deviations
below.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] `ParameterCarrierRedactionTest` written FLAT rather than `@Nested`**
- **Found during:** Task 1
- **Issue:** The file was first written with two `@Nested` inner classes, following
  `SerializedEmissionRedactionTest` as the plan's `<read_first>` directs. JUnit writes each
  `@Nested` class to its own `TEST-<outer>$<Inner>.xml`, producing three XML files. Acceptance
  criterion 1 requires every test method present by name in
  `TEST-…ParameterCarrierRedactionTest.xml` — which would have been UNSATISFIABLE regardless of how
  green the suite was. This is the `WINDOWS.md` 11/13/14/15 class: a criterion counting a population
  the artifact does not contain.
- **Fix:** Flattened to one class, one XML, one decidable gate. Reason recorded in the class KDoc so
  a future reader does not "tidy" it back.
- **Files modified:** `ParameterCarrierRedactionTest.kt`
- **Verification:** `tests="18" failures="0" errors="0"` in the single expected XML; all 18 names present.
- **Committed in:** `76049e0` / `f4a243e`

**2. [Rule 2 - Missing Critical] Producer-ownership pin pulled forward from task 2 into task 1**
- **Found during:** Task 1, red probe 2
- **Issue:** Task 1's behavioural probes drive `sanitizeParameters` directly. Nothing in task 1 as
  planned could detect the `request_parse` producer being unwired from it — the suite would stay
  green with the sanitizer correct and never called, which is precisely the "verified at the site it
  was written for" failure this phase exists to repair. Criterion 5 (revert the branch, confirm RED)
  was unsatisfiable. An end-to-end call is impossible: `HttpRequest.httpRequest()` is a Montoya
  static factory needing Burp's internal ObjectFactory (`McpToolScopeEnforcementTest` records the
  same constraint).
- **Fix:** Added `theRequestParseProducerRoutesThroughTheSanitizer` plus a non-vacuity guard in task
  1, scoped to `McpToolExecutorImpl.kt`; task 2 widened it to the full four-producer inventory as
  planned. No planned coverage was dropped.
- **Files modified:** `ParameterCarrierRedactionTest.kt`
- **Verification:** Red probe 2 — the pin fails with the expected message; restored, green.
- **Committed in:** `76049e0`

---

**Total deviations:** 2 auto-fixed (1 blocking, 1 missing critical).
**Impact on plan:** Both make a stated acceptance criterion decidable that was otherwise
unsatisfiable. No scope added, no coverage removed, no plan decision reopened.

## Issues Encountered

**Criterion 5's "BOTH assertions go red" expectation was not met, and is reported as a finding
rather than worked around.** See red probe 3 above. The behavioural probes structurally cannot
detect a producer unwiring; only the source-scan pin can. This is documented in the pin's KDoc as
the division of labour between the two mechanisms.

No `RedactionTest` wall-clock flake occurred — the full gate
(`test detekt ktlintCheck`, 2m 50s) was green on the first run.

## Verification Results

| Check | Result |
|-------|--------|
| `./gradlew test detekt ktlintCheck` (JDK 21) | **BUILD SUCCESSFUL**, 2m 50s |
| `ParameterCarrierRedactionTest` XML | `tests="18" skipped="0" failures="0" errors="0"` |
| `BountyPromptTagResolverTest` XML | `tests="5" skipped="0" failures="0" errors="0"` |
| B1-B9 re-measured before and after | all match required-after values |
| Five red probes run, specific assertion named | done |
| `RedactionTest.kt` diff | `0` lines |
| `InjectionPointExtractor.kt` diff | `0` lines |
| Pre-existing suites unedited (`git diff --stat`) | no entries for `SerializedEmission*` / `CookieHeader*` |
| `TODO`/`FIXME`/`XXX`/`HACK`/`PLACEHOLDER` introduced | none |
| Untracked/deleted files | none; working tree clean |

## Known Stubs

None. No stub, placeholder, skipped test or unrun `<verify>` was introduced by this plan. Every
`<verify>` command in the plan was executed and its result recorded above.

## User Setup Required

None — no external service configuration required.

## Next Phase Readiness

Ready for plan 27-08, which consumes `sanitizeParameters` and `isCookieParameterType` as the named
controls its carrier inventory classifies against, and which owes three measurements this plan
deliberately did not make:

1. **Task 2** — classify `InjectionPointExtractor.kt:29` (measured here at B9, byte-unchanged).
2. **Task 3** — MEASURE whether a URL/BODY-typed parameter carrying a token survives `request_parse`
   (T-27-07-06; asserted neither way here).
3. Narrow `cookieTypedParamRegex`'s KDoc, which still claims coverage of `request.parameters()` that
   the rule does not have, and add the preservation fixtures (D-27-19).

Plan 27-09 consumes every measured number above, plus the `medium` severity confirmation for
T-27-07-04 and the two named residuals recorded in `BountyPromptTagResolver.kt`.

**Open human item, unchanged:** the live-Burp Montoya confirmation (coverage D5) carried over from
`27-VERIFICATION-2.md`. It should stay routed to `27-HUMAN-UAT.md` where it remains legible as
unanswered, per D-27-23.

---
*Phase: 27-priv-05-gap-closure-sanitize-headers*
*Completed: 2026-08-25*
