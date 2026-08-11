---
phase: 21-redaction-completeness
plan: 01
subsystem: testing
tags: [kotlin, redaction, privacy, burp-montoya, junit5, mockito-kotlin, detekt, ktlint]

# Dependency graph
requires:
  - phase: 13-privacy-redaction
    provides: Redaction.apply, RedactionPolicy.fromMode, PrivacyMode, SafeRegex
provides:
  - "Three Montoya-free top-level internal funs in PassiveAiScannerPrompts.kt: buildScanMetadataText, formatParamLine, redactScanMetadata"
  - "PassiveAiScannerPromptRedactionTest — the Wave 0 test seam that makes SC1/SC2/D-06 assertable against the real emitted blob"
  - "D-06 (scanner half): the caller-side PrivacyMode.OFF short-circuit in doAnalysis is deleted; Redaction.apply is now called unconditionally"
affects: [21-04, 21-05, 21-06, redaction, passive-scanner, prompt-building]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "File-level @file:Suppress for detekt file-scoped rules instead of growing detekt-baseline.xml (QUAL-07)"
    - "Named arguments at wide extracted-builder call sites so a parameter reorder cannot transpose same-typed lists"

key-files:
  created:
    - src/test/kotlin/com/six2dez/burp/aiagent/scanner/PassiveAiScannerPromptRedactionTest.kt
  modified:
    - src/main/kotlin/com/six2dez/burp/aiagent/scanner/PassiveAiScannerPrompts.kt
    - src/main/kotlin/com/six2dez/burp/aiagent/scanner/PassiveAiScannerAnalysis.kt

key-decisions:
  - "TooManyFunctions resolved with @file:Suppress mirroring McpAccessControlPlugin.kt:5, not a baseline entry (QUAL-07)"
  - "buildScanMetadataText keeps statusCode: Int as planned; Montoya's Short statusCode() is widened at the call site so rendered digits stay identical"
  - "The three Wave 0 extractions are placed contiguously after truncateWithEllipsis, keeping the phase's additions reviewable as one block"

patterns-established:
  - "Wave 0 seam: extract pure-String logic out of a Montoya-bound receiver file into the receiver-free sibling so security behaviour is assertable without mocks"
  - "Test discriminators come from real Montoya enum constants (HttpParameterType.COOKIE), never hand-written strings"

requirements-completed: [PRIV-05, PRIV-06]

# Metrics
duration: 15min
completed: 2026-08-11
---

# Phase 21 Plan 01: Wave 0 Prompt-Builder Extraction Summary

**Three Montoya-free prompt-builder functions extracted out of `doAnalysis` plus a 4-test seam that pins the emitted metadata blob's cookie/parameter shapes and proves `redactScanMetadata` reaches `Redaction.apply` — with the caller-side `PrivacyMode.OFF` bypass deleted.**

## Performance

- **Duration:** 15 min
- **Started:** 2026-08-11T13:06:10Z
- **Completed:** 2026-08-11T13:21:06Z
- **Tasks:** 3
- **Files modified:** 3 (2 modified, 1 created)

## Accomplishments

- `formatParamLine`, `redactScanMetadata` and `buildScanMetadataText` now exist as top-level, receiver-free, Montoya-free `internal fun`s in `PassiveAiScannerPrompts.kt`. All three are callable from a unit test with no `MontoyaApi`, no `AgentSettings` and no live Burp — the Wave 0 prerequisite for SC1/SC2/D-06.
- **D-06, scanner half:** the `if (settings.privacyMode == PrivacyMode.OFF) metadataText else Redaction.apply(...)` branch at the old `PassiveAiScannerAnalysis.kt:393-402` is **deleted**, not moved. `redactScanMetadata` calls `Redaction.apply` unconditionally, so OFF is expressed once, as a policy, inside `Redaction`. `PassiveAiScannerAnalysis.kt` no longer references `PrivacyMode` at all and its import is gone.
- The extraction is provably behaviour-preserving: the 46 body lines of the relocated `buildString` diff **byte-identically** against the pre-extraction source once the three Montoya reads are substituted for their parameters (verified mechanically, not by eye).
- `PassiveAiScannerPromptRedactionTest` runs 4 green tests and asserts **no current leak behaviour**, so plans 21-05 and 21-06 can invert the cookie rules without turning this file red.
- detekt baseline unchanged (`git diff --stat -- detekt-baseline.xml` empty), ktlint green, full suite green.

## Task Commits

Each task was committed atomically:

1. **Task 1: Extract formatParamLine and redactScanMetadata** — `b93fed2` (refactor)
2. **Task 2: Extract buildScanMetadataText** — `e2aa5a8` (refactor)
3. **Task 3: Create PassiveAiScannerPromptRedactionTest** — `cadb3ff` (test)

## Files Created/Modified

- `src/main/kotlin/com/six2dez/burp/aiagent/scanner/PassiveAiScannerPrompts.kt` — hosts the three new extractions; gained the three `redact.*` imports and a file-level `@Suppress("TooManyFunctions")`
- `src/main/kotlin/com/six2dez/burp/aiagent/scanner/PassiveAiScannerAnalysis.kt` — `doAnalysis` now calls all three extracted functions; the `buildString` block and the OFF short-circuit are gone, as is the `PrivacyMode` import
- `src/test/kotlin/com/six2dez/burp/aiagent/scanner/PassiveAiScannerPromptRedactionTest.kt` — the Wave 0 seam (4 tests)

## The `buildScanMetadataText` Call Site (for plans 21-05 and 21-06)

Recorded verbatim per the plan's `<output>` requirement, so later plans can extend the test file
without re-reading `doAnalysis`. This is `PassiveAiScannerAnalysis.kt` as of `e2aa5a8`:

```kotlin
// Named arguments: a future parameter reorder must not silently transpose the header lists.
val metadataText =
    buildScanMetadataText(
        kbSummary = ScanKnowledgeBase.buildContextSummary(host),
        displayUrl = displayUrl,
        urlPath = urlPath,
        method = request.method(),
        // Montoya statusCode() is a Short; widen here so the rendered digits stay identical.
        statusCode = response?.statusCode()?.toInt() ?: 0,
        mimeType = response?.statedMimeType()?.name ?: "unknown",
        potentialIds = potentialIds,
        requestHeaders = requestHeaders,
        responseHeaders = responseHeaders,
        authHeaders = authHeaders,
        cookies = cookies,
        params = params,
        requestBody = requestBody,
        responseBody = responseBody,
    )

// PRIV-06 / D-06: no caller-side PrivacyMode.OFF short-circuit — OFF is a policy inside Redaction.
val safeMetadataText = redactScanMetadata(metadataText, settings.privacyMode, settings.hostAnonymizationSalt)
```

Declared signature (all 14 parameters required, no defaults):

```kotlin
@Suppress("LongParameterList")
internal fun buildScanMetadataText(
    kbSummary: String?,
    displayUrl: String,
    urlPath: String,
    method: String,
    statusCode: Int,
    mimeType: String,
    potentialIds: List<String>,
    requestHeaders: List<String>,
    responseHeaders: List<String>,
    authHeaders: List<String>,
    cookies: List<String>,
    params: List<String>,
    requestBody: String,
    responseBody: String,
): String
```

The test file already carries a 3-parameter `metadataBlob(requestHeaders, cookies, params)` helper
that fills the other 11 with benign constants — extend that rather than re-spelling 14 arguments.

## Decisions Made

- **`TooManyFunctions` → `@file:Suppress`, not a baseline entry.** The file's three same-package siblings (`PassiveAiScannerAnalysis.kt`, `PassiveAiScannerFilters.kt`, `PassiveAiScannerFinding.kt`) all carry this identical file-scoped finding via `detekt-baseline.xml`, so crossing the threshold here is expected. QUAL-07 forbids the baseline route, and `TooManyFunctions` is file-scoped so a declaration-level `@Suppress` cannot reach it. `McpAccessControlPlugin.kt:1-5` is exact in-repo precedent — including its rationale comment naming QUAL-07.
- **`statusCode` stays `Int`.** Montoya's `HttpResponse.statusCode()` returns `Short`, so the planned `Int` parameter needed a `.toInt()` at the call site. Widening at the call site (rather than changing the signature to `Short`) keeps the plan's pinned signature, keeps the test able to pass plain `200`, and renders identical digits.
- **Line-exact section assertions.** Test 2 asserts via `blob.lines().contains("=== COOKIES ===")` rather than `String.contains`, so a future edit that merges a section header onto another line fails the test rather than passing on a substring match.
- **`@BeforeEach` clears `Redaction.setCustomPatterns(emptyList())`.** `Redaction` is a singleton; custom patterns leaked by another test class in the same JVM would make the OFF byte-identity assertion vacuous once D-05 (plan 21-06) moves the custom-pattern loop outside the `redactTokens` branch.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] detekt `TooManyFunctions` on `PassiveAiScannerPrompts.kt`**
- **Found during:** Task 1 (first verification run)
- **Issue:** The file held 9 top-level functions; adding 2 brought it to detekt's file threshold of 11, failing `./gradlew detekt` and blocking Task 1's verification. Task 2's third extraction would have pushed it to 12. The plan anticipated `LongParameterList` but not this file-scoped rule.
- **Fix:** Added `@file:Suppress("TooManyFunctions")` above the `package` declaration with a comment naming the three baselined siblings and citing QUAL-07 — mirroring `McpAccessControlPlugin.kt:1-5`, the in-repo precedent for exactly this trade-off. Explicitly did **not** add a baseline entry (QUAL-07) and did not restructure the file to dodge the threshold.
- **Files modified:** `src/main/kotlin/com/six2dez/burp/aiagent/scanner/PassiveAiScannerPrompts.kt`
- **Verification:** `./gradlew test --tests "com.six2dez.burp.aiagent.scanner.*" ktlintCheck detekt -q` exits 0; `git diff --stat -- detekt-baseline.xml` empty
- **Committed in:** `b93fed2` (Task 1 commit)

**2. [Rule 3 - Blocking] `statusCode` type mismatch: Montoya returns `Short`**
- **Found during:** Task 2 (compile step)
- **Issue:** `compileKotlin` failed with `Argument type mismatch: actual type is 'Short', but 'Int' was expected`. `HttpResponse.statusCode()` returns `Short`; the pre-extraction code only string-interpolated it, so the type never surfaced.
- **Fix:** `statusCode = response?.statusCode()?.toInt() ?: 0` at the call site, with an inline comment. The planned `statusCode: Int` signature is preserved and the rendered digits are unchanged (`Short 200` and `Int 200` both interpolate to `200`; `null` still yields `0`).
- **Files modified:** `src/main/kotlin/com/six2dez/burp/aiagent/scanner/PassiveAiScannerAnalysis.kt`
- **Verification:** `./gradlew test ktlintCheck detekt -q` exits 0; the 46-line normalized body diff against the pre-extraction source is empty
- **Committed in:** `e2aa5a8` (Task 2 commit)

**3. [Rule 1 - Bug] Comment text duplicated the section-header literals and broke two acceptance greps**
- **Found during:** Task 2 (acceptance-criteria check)
- **Issue:** My explanatory comments quoted `=== COOKIES ===` and `=== PARAMETERS ===` verbatim, so `grep -c` returned 2 and 3 where the plan's acceptance criteria require exactly 1 each. A verifier re-running those greps would have flagged a false regression.
- **Fix:** Reworded both comments to say "the cookie and parameter section headers" / "the parameters section", leaving the literals only at their emission sites.
- **Files modified:** `src/main/kotlin/com/six2dez/burp/aiagent/scanner/PassiveAiScannerPrompts.kt`
- **Verification:** both greps now return 1; full gate re-run green
- **Committed in:** `e2aa5a8` (Task 2 commit)

---

**Total deviations:** 3 auto-fixed (2 blocking, 1 bug)
**Impact on plan:** All three were mechanical and stayed inside the plan's own decided approach — suppress on the declaration, never grow the baseline, never redesign to dodge a lint threshold. No scope creep: no redaction behaviour changed, no `PrivacyMode.OFF` check outside the scanner was touched, and D-06's do-not-touch list is intact.

## Issues Encountered

- The first attempt to verify the extraction was byte-identical produced a misleading diff because the normalization stripped a fixed indent from lines at three different depths. Re-normalized by stripping all leading whitespace and removing the `val kbSummary = …` line (which became a parameter); the 46-line bodies then compared identical. Worth noting for 21-05/21-06: compare these blocks with indentation stripped, not with a fixed `sed` offset.

## Threat Model Verification

| Threat ID | Disposition | Status |
|-----------|-------------|--------|
| T-21-10 (extraction silently alters the emitted blob) | mitigate | **Satisfied** — normalized 46-line body diff is empty; Task 3 tests 1–2 pin the param-line shape and both section headers; full suite green |
| T-21-12 (OFF branch deletion assumed behaviour-preserving) | mitigate | **Satisfied** — `RedactionPolicy.fromMode(OFF)` sets all three flags false (`Redaction.kt:39-44`) and `redactScanMetadata_offModeIsByteIdentical` asserts byte-identity |
| T-21-13 (scope creep into unrelated `PrivacyMode.OFF` checks) | accept + control | **Satisfied** — only `PassiveAiScannerAnalysis.kt` was touched; `grep -v '^\s*//' … \| grep -c 'PrivacyMode'` returns 0 there, and no file on D-06's do-not-touch list appears in any commit |
| T-21-SC (package-install supply chain) | accept | **Satisfied** — zero packages installed; no Gradle dependency added |

No new security-relevant surface (network endpoints, auth paths, file access, trust-boundary schema) was introduced — this plan relocates pure-`String` transformations and deletes a bypass.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- **Wave 0 is complete for this plan's three extractions.** `21-VALIDATION.md`'s Wave 0 checklist items for 21-01 T1/T2/T3 are satisfied; the remaining Wave 0 item (`SafeRegexTest` `timedOut` coverage) belongs to 21-02.
- Plans 21-05 and 21-06 can extend `PassiveAiScannerPromptRedactionTest` using the `metadataBlob(...)` helper and the call site recorded above. Nothing in the current 4 tests asserts the defect, so the cookie-rule inversions will not turn this file red.
- One coupling to carry forward: `redactScanMetadata` is now the single scanner-side entry into `Redaction.apply`. When D-05 moves the custom-pattern loop outside the `redactTokens` branch, `redactScanMetadata_offModeIsByteIdentical` remains true only because this test class clears custom patterns in `@BeforeEach` — keep that reset when extending the file.
- The MCP half of D-06 (`mcp/McpToolContext.kt:59-66`) is untouched by this plan and remains outstanding.

## Self-Check: PASSED

All 4 claimed files exist on disk; all 4 claimed commits (`b93fed2`, `e2aa5a8`, `cadb3ff`, `bd2b2dc`)
exist in the branch history. Plan verification block re-run at completion:
`./gradlew test ktlintCheck detekt -q` exits 0, `git diff --stat -- detekt-baseline.xml` is empty,
and `grep -v '^\s*//' PassiveAiScannerAnalysis.kt | grep -c 'PrivacyMode'` returns 0.

---
*Phase: 21-redaction-completeness*
*Completed: 2026-08-11*
