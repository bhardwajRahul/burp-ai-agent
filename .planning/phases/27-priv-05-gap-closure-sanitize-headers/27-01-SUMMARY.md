---
phase: 27-priv-05-gap-closure-sanitize-headers
plan: 01
subsystem: privacy
tags: [kotlin, redaction, cookies, mcp, locale, burp-montoya, junit5]

requires:
  - phase: 21-priv-05-cookie-rules
    provides: "Redaction.COOKIE_NAME_PART and the name-contains-cookie prompt-path rules this plan mirrors"
  - phase: 26-mcp-tool-helpers
    provides: "McpToolHelpers.sanitizeHeaders, the second (tool-result) redaction path that had drifted"
provides:
  - "Redaction.isCookieHeaderName — one public cookie-header-name predicate shared by the two redaction paths and the passive-scan admitter"
  - "Redaction.COOKIE_NAME_TOKEN — the single literal both cookie regexes and the predicate are composed from"
  - "McpToolHelpers.sanitizeHeaders strips all five measured cookie name variants plus both canonical names under STRICT and BALANCED"
  - "CookieHeaderRuleOwnershipTest — a repository-state tripwire that fails CI on a fourth hand-written matcher spelled in any of five measured classes"
  - "PassiveAiScannerHeaderAdmissionTest — behavioural proof the passive-scan admission set is unchanged by the refactor"
  - "Measured correction: Kotlin's no-argument lowercase() is locale-agnostic, so the tree has ZERO locale-sensitive lowering call sites"
affects: [27-02, 27-03, security-register, privacy, mcp]

actuals:
  tokens: 12954
  tasks: 3
  commits: 3

tech-stack:
  added: []
  patterns:
    - "Security rule promoted to one shared symbol, consumed by every path, rather than duplicated per path"
    - "Repository-state test as the durable carrier of an ownership invariant, with its own bound written into its file header"
    - "Outcome-level end-to-end assertion on the final string, so the requirement is locked independently of which layer enforces it"

key-files:
  created:
    - src/test/kotlin/com/six2dez/burp/aiagent/redact/CookieHeaderRuleOwnershipTest.kt
    - src/test/kotlin/com/six2dez/burp/aiagent/scanner/PassiveAiScannerHeaderAdmissionTest.kt
  modified:
    - src/main/kotlin/com/six2dez/burp/aiagent/redact/Redaction.kt
    - src/main/kotlin/com/six2dez/burp/aiagent/mcp/tools/McpToolHelpers.kt
    - src/main/kotlin/com/six2dez/burp/aiagent/scanner/PassiveAiScannerFilters.kt
    - src/test/kotlin/com/six2dez/burp/aiagent/mcp/tools/McpToolHelpersTest.kt

key-decisions:
  - "D-27-01 executed as planned: the cookie-name rule is promoted to Redaction.isCookieHeaderName and three call sites consume it; both regexes are composed from COOKIE_NAME_TOKEN and their compiled patterns are byte-identical."
  - "D-27-04 executed, but its stated RATIONALE was measured false and corrected in source: Kotlin's no-argument lowercase() is already locale-agnostic (it compiles to toLowerCase(Locale.ROOT)), so the explicit Locale.ROOT argument is defensive documentation, not a behaviour change."
  - "Task 1 assert-4's non-vacuity guard was changed from host anonymisation to bearer-token redaction, because hostHeaderRegex is line-anchored and provably cannot fire on single-line JSON — the plan's specified guard would have been unsatisfiable."
  - "RED PROBE B was replaced by PROBE B2 (Locale.getDefault() instead of Locale.ROOT removal) because the planned mutation is a measured no-op on this toolchain."

patterns-established:
  - "Scope guard on prose: an acceptance criterion greps the source comments themselves, so a claim cannot widen in the artifact a maintainer reads first"
  - "Tripwire tests state their own bound in their file header, so a reader cannot mistake measured coverage for exhaustive coverage"

requirements-completed: [PRIV-05]

coverage:
  - id: D1
    description: "McpToolHelpers.sanitizeHeaders recognises a cookie header by name-CONTAINS-cookie, matching the prompt path, via the shared Redaction.isCookieHeaderName predicate"
    requirement: PRIV-05
    verification:
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/mcp/tools/McpToolHelpersTest.kt#cookieHeaderNameVariantsAreStrippedOnTheToolResultPath"
        status: pass
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/mcp/tools/McpToolHelpersTest.kt#headersMerelyContainingCookieAreStrippedByDesign"
        status: pass
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/mcp/tools/McpToolHelpersTest.kt#benignHeaderNamesWithoutTheTokenSurvive"
        status: pass
    human_judgment: false
  - id: D2
    description: "An X-Cookie sentinel does not survive the full flow sanitizeHeaders -> toolJson.encodeToString -> McpToolContext.redactIfNeeded, asserted on the final string"
    requirement: PRIV-05
    verification:
      - kind: integration
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/mcp/tools/McpToolHelpersTest.kt#cookieVariantsAreStrippedEndToEndThroughRedactIfNeeded"
        status: pass
    human_judgment: false
  - id: D3
    description: "PrivacyMode.OFF is unchanged — every cookie name variant passes through sanitizeHeaders with its original value"
    requirement: PRIV-05
    verification:
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/mcp/tools/McpToolHelpersTest.kt#offModePassesEveryCookieNameVariantThrough"
        status: pass
    human_judgment: false
  - id: D4
    description: "Header-name matching does not depend on the ambient JVM default locale, and the default locale is restored after the swap"
    requirement: PRIV-05
    verification:
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/mcp/tools/McpToolHelpersTest.kt#cookieNameMatchingSurvivesATurkishDefaultLocale"
        status: pass
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/mcp/tools/McpToolHelpersTest.kt#defaultLocaleIsRestored"
        status: pass
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/scanner/PassiveAiScannerHeaderAdmissionTest.kt#admissionSurvivesATurkishDefaultLocale"
        status: pass
    human_judgment: false
  - id: D5
    description: "The empty edge — an empty header list yields an empty map, and a cookie-named header with an empty value is still redacted rather than passed through"
    requirement: PRIV-05
    verification:
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/mcp/tools/McpToolHelpersTest.kt#emptyValuedCookieHeaderIsStillRedacted"
        status: pass
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/mcp/tools/McpToolHelpersTest.kt#emptyHeaderListYieldsEmptyMap"
        status: pass
    human_judgment: false
  - id: D6
    description: "The passive-scan admitter routes through the shared predicate and its admission set is unchanged for every non-cookie rule"
    requirement: PRIV-05
    verification:
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/scanner/PassiveAiScannerHeaderAdmissionTest.kt#admitsEveryCookieNameVariantAfterRoutingThroughTheSharedPredicate"
        status: pass
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/scanner/PassiveAiScannerHeaderAdmissionTest.kt#nonCookieAdmissionRulesAreUnchanged"
        status: pass
    human_judgment: false
  - id: D7
    description: "The ownership invariant is carried by a repository-state test, proven falsifiable, and bounded by five measured spelling classes stated in its own file header"
    requirement: PRIV-05
    verification:
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/redact/CookieHeaderRuleOwnershipTest.kt#noHandWrittenCookieMatcherSurvivesInTheRedactionPaths"
        status: pass
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/redact/CookieHeaderRuleOwnershipTest.kt#everyCookieHeaderNameMatcherInMainIsClassified"
        status: pass
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/redact/CookieHeaderRuleOwnershipTest.kt#theOwnershipScanIsNonVacuous"
        status: pass
    human_judgment: false
  - id: D8
    description: "The per-site classification of every surviving cookie-header-name matcher, and the corrected default-locale backlog observation, are accurate enough for plan 27-03 to cite when it amends a security register"
    requirement: PRIV-05
    verification: []
    human_judgment: true
    rationale: "The classification is a reading of four files' consumer chains and the locale finding overturns a premise the plan asserted. Both are inputs to a security-record amendment in 27-03, and no test asserts that a prose classification is the RIGHT classification — a human must agree with the reasoning before it is certified."

duration: 38 min
completed: 2026-08-24
status: complete
---

# Phase 27 Plan 01: PRIV-05 Gap Closure — sanitizeHeaders Summary

**One shared `Redaction.isCookieHeaderName` predicate now backs the MCP tool-result redactor, the prompt path's two regexes and the passive-scan admitter, closing the five-variant cookie leak on the tool-result path — and a repository-state tripwire keeps a fourth hand-written copy from appearing silently.**

## Performance

- **Duration:** 38 min
- **Started:** 2026-08-24T14:05Z (approx., first task work)
- **Completed:** 2026-08-24T14:43Z
- **Tasks:** 3
- **Files modified:** 6 (4 modified, 2 created)

## Accomplishments

- **The gap is closed.** `McpToolHelpers.sanitizeHeaders` compared against the two canonical spellings; it now calls `Redaction.isCookieHeaderName`. All five measured variants (`Cookie2`, `X-Cookie`, `Set-Cookie2`, `X-Original-Cookie`, `X-Forwarded-Cookie`) plus both canonical names return `[STRIPPED]` under STRICT and BALANCED, each under its own original-cased key.
- **The agreement is structural, not coincidental.** `COOKIE_NAME_TOKEN` is one private literal from which both cookie regexes and the predicate are composed; the compiled regex patterns are byte-identical to before.
- **The requirement is asserted as an outcome.** The end-to-end test runs `sanitizeHeaders` → `toolJson.encodeToString(ParsedRequest)` → `McpToolContext.redactIfNeeded` and asserts on the final string, so a future narrowing goes red regardless of which layer was meant to catch it.
- **AR-27-01 / AR-27-02 are pinned by measurement, not assumed.** The same STRICT context handed the RAW header list still emits the sentinel verbatim — proof that `redactIfNeeded` cannot recover a header `sanitizeHeaders` missed.
- **The ownership invariant outlives the phase.** `CookieHeaderRuleOwnershipTest` carries the five measured spelling classes and a four-file classification allowlist, and was proven falsifiable.
- **A premise of the plan was measured false and corrected in source.** Kotlin's no-argument `lowercase()` is already locale-agnostic; the tree has ZERO locale-sensitive lowering call sites. See "Measured corrections" below — this materially narrows what plan 27-03 may claim about the locale hazard.

## Task Commits

1. **Task 1 (tracer): one end-to-end path, X-Cookie stripped through the full flow** — `02d71c2` (fix)
2. **Task 2: full variant matrix per privacy mode, plus locale and empty-value edges** — `984c296` (test)
3. **Task 3: route the admitter through the predicate, classify survivors, ownership tripwire** — `fe379e5` (refactor)

## Files Created/Modified

- `src/main/kotlin/.../redact/Redaction.kt` — new private `COOKIE_NAME_TOKEN`; both cookie regexes recomposed from it; new public `isCookieHeaderName(name)` with scoped KDoc
- `src/main/kotlin/.../mcp/tools/McpToolHelpers.kt` — `sanitizeHeaders` calls the shared predicate; lowered name uses `Locale.ROOT`
- `src/main/kotlin/.../scanner/PassiveAiScannerFilters.kt` — `sanitizeHeadersForPrompt`'s cookie conjunct calls the shared predicate; lowered name uses `Locale.ROOT`; `auth`/`token` conjuncts untouched
- `src/test/kotlin/.../mcp/tools/McpToolHelpersTest.kt` — 7 new tests, a `@BeforeEach` clearing custom patterns, and an `@AfterEach` asserting the default locale is restored
- `src/test/kotlin/.../scanner/PassiveAiScannerHeaderAdmissionTest.kt` — NEW, 3 tests
- `src/test/kotlin/.../redact/CookieHeaderRuleOwnershipTest.kt` — NEW, 3 tests

## Test counts

| Point | `*McpToolHelpersTest` |
|---|---|
| Before this plan | 66 |
| End of Task 1 | 67 (+1) |
| End of Task 2 | **73 (+6)** |

New classes: `PassiveAiScannerHeaderAdmissionTest` 3 tests, `CookieHeaderRuleOwnershipTest` 3 tests.

## Red probes, with observed output

### RED PROBE A — revert the matcher to exact-name equality (PASSED: goes red)

Mutation: `Redaction.isCookieHeaderName(name)` → `(lowered == "cookie" || lowered == "set-cookie")`.

```
McpToolHelpersTest > SanitizeHeaders > cookieVariantsAreStrippedEndToEndThroughRedactIfNeeded() FAILED
org.opentest4j.AssertionFailedError: the X-Cookie value must not survive the tool-result flow
(got: {"method":"GET","path":"/","url":"https://api.example.com/","headers":{"X-Cookie":"sentinelxrayninezulu",
"Authorization":"[REDACTED]","Host":"host-35c825cf9864.local","X-Request-Id":"benignidcontrolvalue"},
"parameters":[],"body":null,"bodyLength":0}) ==> expected: <false> but was: <true>
```

Restored with `git checkout HEAD -- .../McpToolHelpers.kt`; `git diff --quiet src/main/kotlin` exits 0.

This is also the ORIGINAL RED output from the TDD step before any production change — the defect verbatim: `X-Cookie` reaching the model while `Authorization` and `Host` were handled.

### RED PROBE B — remove the `Locale.ROOT` argument (DID NOT go red; reported, not papered over)

Mutation: `name.lowercase(Locale.ROOT)` → `name.lowercase()` in `isCookieHeaderName`.

```
BUILD SUCCESSFUL in 36s
```

The planned probe is a **measured no-op on this toolchain**. Kotlin's no-argument `String.lowercase()` compiles to `toLowerCase(Locale.ROOT)` and is already locale-agnostic, so removing the explicit argument changes nothing. The plan's `must_haves` truth *"removing the `Locale.ROOT` argument turns the `tr-TR` assertion red"* is **falsified by measurement**. It is recorded as falsified rather than claimed as a pass.

### RED PROBE B2 — the mutation at the spelling where the hazard actually lives (PASSED: goes red)

Mutation: `name.lowercase(Locale.ROOT)` → `name.lowercase(Locale.getDefault())`.

```
McpToolHelpersTest > SanitizeHeaders > cookieNameMatchingSurvivesATurkishDefaultLocale() FAILED
org.opentest4j.AssertionFailedError: expected: <[STRIPPED]> but was: <sentinelturkishcookie>
130 tests completed, 1 failed
```

Exactly one of 130 tests failed, and it is a DIFFERENT assertion from Probe A's. So the two mutation directions are still proven to fail two different assertions — just at the correct spelling. The `X-COOKIE` value leaks verbatim under a `tr-TR` default when the lowering is locale-sensitive.

### Ownership-test falsifiability probe (PASSED: goes red)

A hand-written matcher was temporarily appended to `scanner/InjectionPointExtractor.kt`, which is not in the classification:

```
CookieHeaderRuleOwnershipTest > everyCookieHeaderNameMatcherInMainIsClassified() FAILED
the set of files with a hand-written cookie-header-name matcher has changed.
  NEW (route it through Redaction.isCookieHeaderName, or classify it in CLASSIFIED_NON_REDACTING
  with the reason you read from its source and its consumer):
  [com/six2dez/burp/aiagent/scanner/InjectionPointExtractor.kt]
  STALE (classified here but no longer matched — remove the entry so the allowlist cannot
  accumulate dead keys): []
```

Restored with `git checkout HEAD -- .../InjectionPointExtractor.kt`. Without this probe the allowlist would be an assumption.

## Observed `Locale.getDefault()` behaviour under `tr-TR`

Measured directly on this JVM (JDK 21, Kotlin toolchain in this repo) with the default locale set to `tr_TR`:

```
PROBE default=tr_TR
  kotlin  lowercase()            = [cookie]  matchesToken=true
  kotlin  lowercase(Locale.ROOT) = [cookie]
  java    toLowerCase()          = [cookıe]  matchesToken=false
  java    toLowerCase(default)   = [cookıe]
```

The dotless-i hazard is **real**, but it belongs to the **Java** spelling. The Kotlin spelling this codebase uses everywhere does not exhibit it. Both facts are now written into `isCookieHeaderName`'s KDoc and the `sanitizeHeaders` comment, replacing the incorrect rationale this plan originally specified.

The `tr-TR` tests are kept and are not vacuous: they assert BOTH halves of the measurement inline (`"COOKIE".lowercase()` matches; `("COOKIE" as java.lang.String).toLowerCase()` does not), and Probe B2 shows they catch the real hazard if anyone switches spelling.

## Passive-scanner admitter: covered BEHAVIOURALLY

`PassiveAiScannerHeaderAdmissionTest` constructs a real `PassiveAiScanner` (mock `MontoyaApi` / `AgentSupervisor` / `AuditLogger`, as `PassiveAiScannerConfidenceTest` does) and calls `sanitizeHeadersForPrompt` directly. No fallback to a structural-only assertion was needed. `getSettings` is deliberately a throwing lambda, which also proves the function has no settings dependency.

The admission set is **unchanged** by the refactor — the conjunct replaced was already `name.contains("cookie")` — and those tests were green before and after, as the plan predicted.

## Per-site classification of every surviving cookie-header-name matcher

The sweep was re-run in this task, **case-sensitively**, against the working tree. It returned **9 sites in 6 files**, reproducing the plan's table exactly. Every row below was traced to its consumer by reading the file in this task.

| Site | Classification | My read vs the plan's table | Source evidence read in this task |
|---|---|---|---|
| `mcp/tools/McpToolHelpers.kt:321` | **REDACTOR** — fixed | **Agrees** | The gap itself. `if (policy.stripCookies && (lowered == "cookie" \|\| lowered == "set-cookie"))` decided whether a header VALUE was replaced. Now calls the predicate. |
| `scanner/PassiveAiScannerFilters.kt:172` | **ADMITTER** — routed | **Agrees** | The third `contains` conjunct in the filter lambda gates which headers enter the passive-scan prompt. Narrow here does not leak; parity is what makes the rule singular. Now calls the predicate. |
| `scanner/PassiveAiScannerAnalysis.kt:267` | EXTRACTOR — no | **Agrees** | Read at `:265-269`: `.filter { it.name().equals("Cookie", ignoreCase = true) }.map { it.value() }` feeding `cookieSectionLines(...)`. Consumer confirmed at `PassiveAiScannerPrompts.kt:45-49`, where `redactScanMetadata` calls `Redaction.apply` **unconditionally** with an explicit comment forbidding an OFF short-circuit. Fail-safe by direction: narrowing puts FEWER cookie values into the prompt. |
| `scanner/PassiveAiScannerHeuristics.kt:102` | LOCAL-ANALYSIS — no | **Agrees** | `request.headerValue("Cookie") ?: ""` then `authCookieHint.containsMatchIn(cookieHeader)` — reduced to a boolean gate that returns `null` (no finding) or builds a `LocalFinding`. |
| `scanner/PassiveAiScannerHeuristics.kt:117` | LOCAL-ANALYSIS — no | **Agrees** | `.filter { it.name().equals("Set-Cookie", ignoreCase = true) }?.any { ... }` collapses to the `sameSiteSecure` boolean. No header value crosses the process boundary. |
| `prompts/bountyprompt/BountyPromptTagResolver.kt:144` and `:150` | EXTRACTOR over ALREADY-REDACTED text — no | **Agrees** | Confirmed directly: `requestRedacted`/`responseRedacted` are assigned from `Redaction.apply(...)` at `:79-80`, and `BountyPromptTag.HTTP_COOKIES -> truncateChunk(extractCookies(requestRedacted, responseRedacted), ...)` at `:106` hands `extractCookies` exactly those redacted strings. Its `startsWith("Cookie:")` / `startsWith("Set-Cookie:")` filters therefore run on lines already through the wide prompt-path rules. |
| `scanner/ActiveAiScanner.kt:936` | LOCAL-ANALYSIS — no | **Agrees** | `hasAuthContext` reduces `headerValue("Cookie")` to a boolean via `authCookieHint.containsMatchIn`; `stripAuthHeaders` immediately below ends with `withRemovedHeader("Cookie")`. |
| `scanner/ActiveAiScanner.kt:1411` | REQUEST MUTATOR — no | **Agrees** | The `InjectionType.COOKIE` branch rewrites the `Cookie` header with an attack payload and returns the request sent to the **target**. Not a redaction path at all. |

**No row contradicted the plan's table.** Nothing here changes what plan 27-03 may certify relative to the plan's own expectation.

Confirmed NOT sweep hits, and correctly absent from `CLASSIFIED_NON_REDACTING`: `scanner/PassiveAiScanner.kt` (a bare `"set-cookie"` set member), `scanner/InjectionPointExtractor.kt` (`it.type().name == "COOKIE"`, an uppercase parameter-TYPE compare), and `redact/Redaction.kt` (regexes composed from `COOKIE_NAME_TOKEN`). Seeding any of them would have produced a dead key.

### `CLASSIFIED_NON_REDACTING` — exactly four keys

1. `com/six2dez/burp/aiagent/scanner/PassiveAiScannerAnalysis.kt`
2. `com/six2dez/burp/aiagent/scanner/PassiveAiScannerHeuristics.kt`
3. `com/six2dez/burp/aiagent/prompts/bountyprompt/BountyPromptTagResolver.kt`
4. `com/six2dez/burp/aiagent/scanner/ActiveAiScanner.kt`

## The ownership test's stated bound

Quoted verbatim from `CookieHeaderRuleOwnershipTest.kt`'s file header:

> The scan sees only the five spelling classes enumerated in `MATCHER_SPELLINGS`: exact-name equality, `ignoreCase` equality, a line-prefix test, a Montoya `headerValue` lookup, and a substring test. A cookie matcher spelled OUTSIDE those five classes — a `startsWith` on an already-lowered name, an `in` operator against a set literal, a `when` branch, a name assembled from a constant — is invisible to this scan and leaves this test GREEN.
>
> This test is therefore a TRIPWIRE OVER MEASURED SPELLINGS, not a proof of exhaustive coverage.

## Default-locale backlog observation — MEASURED, and narrower than the plan assumed

The command the plan specified:

```
$ grep -rn 'lowercase()' src/main/kotlin --include=*.kt | grep -v 'Locale' | wc -l
114
```

The plan asked for that count to be recorded as surviving hazards. **It is not a hazard count.** All 114 are the Kotlin `lowercase()` spelling, which the measurement above proves is locale-agnostic. The spellings that ARE locale-sensitive return:

```
$ grep -rn 'toLowerCase(' src/main/kotlin --include=*.kt | wc -l
5        # all five are inside comments added by THIS plan
$ grep -rn 'lowercase(Locale.getDefault())' src/main/kotlin --include=*.kt | wc -l
1        # also a comment added by THIS plan
```

**There are ZERO real locale-sensitive lowering call sites in `src/main/kotlin`.**

The named survivors the plan asked to be listed were each confirmed by reading, and each uses the locale-agnostic Kotlin spelling: `scanner/PassiveAiScannerFilters.kt:79` (now `:81`, untouched as required), `mcp/McpAccessControlDecision.kt:225` and `:272` (`isBrowserUserAgent`), `mcp/KtorMcpServerManager.kt:348-350` (scheme and host), `mcp/McpTakeoverProof.kt:73` (host inside the HMAC message), `mcp/tools/ResponsePreprocessor.kt:106/119/123` (content-type allowlist), `mcp/tools/McpToolLegacy.kt:316/323` (cookie-jar domain filter).

All of these remain deliberately OUT of this phase's scope. The honest framing for plan 27-03 is that a future locale-hardening pass has **nothing to harden at these sites today**; what it should add is a guard against introducing a locale-sensitive spelling, not a migration of 114 call sites.

## Ownership sweeps (both return 0)

```
$ grep -rn 'contains("cookie")' src/main/kotlin --include=*.kt | grep -v 'isCookieHeaderName' | wc -l
0

$ grep -rnE 'equals\("(Set-)?Cookie"|startsWith\("(Set-)?Cookie:"|headerValue\("(Set-)?Cookie"\)|contains\("cookie"\)|== "(set-)?cookie"' \
    src/main/kotlin --include=*.kt | grep -v 'isCookieHeaderName' \
  | grep -vE 'PassiveAiScannerAnalysis\.kt|PassiveAiScannerHeuristics\.kt|BountyPromptTagResolver\.kt|ActiveAiScanner\.kt|Redaction\.kt' | wc -l
0
```

**Scope note, load-bearing:** these gates prove *"one cookie-header-name rule across the two redaction paths and the passive-scan admitter"*. They do not prove, and must never be quoted as proving, a claim about the tree at large. Four other cookie-header-name matchers survive and are classified above.

## Decisions Made

- **The ownership claim is stated at the scope the evidence supports** — the two redaction paths and the passive-scan admitter — in the SUMMARY, in `isCookieHeaderName`'s KDoc, and in the ownership test's file header. The scope-guard greps return `0` on `Redaction.kt`, `PassiveAiScannerFilters.kt` and `CookieHeaderRuleOwnershipTest.kt`.
- **The locale rationale was corrected in source rather than left as a comfortable but false comment.** A comment that misstates why a security control is written the way it is, is the same defect class this phase exists to repair, in the artifact a maintainer reads first.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 — Bug] Task 1's assert-4 non-vacuity guard was unsatisfiable as specified**

- **Found during:** Task 1
- **Issue:** The plan specified the guard as *"the `Host` value is anonymised"* on the RAW single-line JSON path. `Redaction.hostHeaderRegex` is `Regex("(?im)^host:\\s*([^\\s]+)\\s*$")` — line-anchored, so it provably cannot match `"Host":"api.example.com"` inside single-line JSON. Asserting it would have failed for a correct implementation.
- **Fix:** The guard now uses `bearerRegex`, which is NOT line-anchored and does fire on single-line JSON under a token-redacting policy — proving `redactIfNeeded` really ran. A third assertion additionally records that the host is NOT anonymised there, which demonstrates AR-27-01 a second time, on a second rule.
- **Files modified:** `McpToolHelpersTest.kt`
- **Verification:** `cookieVariantsAreStrippedEndToEndThroughRedactIfNeeded` green; Probe A shows it red on mutation.
- **Committed in:** `02d71c2`

**2. [Rule 1 — Bug] The `Locale.ROOT` rationale in the KDoc and the call-site comment was factually wrong**

- **Found during:** Task 2
- **Issue:** As instructed by the plan, the first draft of `isCookieHeaderName`'s KDoc stated that a no-argument `"COOKIE".lowercase()` yields `cookıe` under a Turkish default locale. Measured on this toolchain, it yields `cookie` — Kotlin's `lowercase()` is locale-agnostic. Leaving the claim would have put a false security rationale in the artifact a maintainer reads first.
- **Fix:** Both the KDoc and the `sanitizeHeaders` comment now state the measured behaviour, name the Java spelling as the one that IS hazardous, and state the honest bound (removing the argument changes nothing today; `Locale.getDefault()` would break the control).
- **Files modified:** `Redaction.kt`, `McpToolHelpers.kt`
- **Verification:** the measurement is reproduced as two inline assertions inside `cookieNameMatchingSurvivesATurkishDefaultLocale`; Probe B2 confirms the hazardous spelling is caught.
- **Committed in:** `984c296`

**3. [Rule 1 — Bug] RED PROBE B, as specified, is a no-op — replaced by PROBE B2**

- **Found during:** Task 2
- **Issue:** The plan required Probe B (removing `Locale.ROOT`) to turn the `tr-TR` assertion red. It does not, for the reason above. Reporting it as a pass would have been a fabricated result.
- **Fix:** Probe B was run and its green result recorded as a falsification of the plan's truth. Probe B2 (`Locale.getDefault()`) was added and does go red, preserving the plan's actual goal — two mutations failing two different assertions.
- **Files modified:** none (probes are reverted)
- **Verification:** both probe outputs recorded verbatim above; `git diff --quiet src/main/kotlin` exits 0 after each.
- **Committed in:** n/a (probe only)

**4. [Rule 3 — Blocking] ktlint `function-signature` violation in the new ownership test**

- **Found during:** Task 3
- **Issue:** `ktlintCheck` failed: `CookieHeaderRuleOwnershipTest.kt:126:51 First line of body expression fits on same line as function signature`.
- **Fix:** Collapsed `relativePath` onto one line.
- **Files modified:** `CookieHeaderRuleOwnershipTest.kt`
- **Verification:** `ktlintCheck` green.
- **Committed in:** `fe379e5`

---

**Total deviations:** 4 auto-fixed (3 bugs, 1 blocking).
**Impact on plan:** No scope creep. Deviations 1–3 are all the same shape — the plan asserted a fact about the code that measurement contradicted, and in each case the measurement was followed and the contradiction recorded rather than smoothed over. That is the behaviour this phase's own prohibitions demand. Deviation 2 in particular removes a false security rationale from shipped source.

## Issues Encountered

None beyond the deviations above. The known `RedactionTest` wall-clock flake did not occur; `RedactionTest` was green on every run and its file is unedited (`git diff --quiet` exits 0).

## Verification

```
$ JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test detekt ktlintCheck
BUILD SUCCESSFUL in 2m 54s
```

- `git diff --quiet detekt-baseline.xml` exits 0 — no baseline entry added.
- `git diff --quiet src/test/kotlin/.../RedactionTest.kt` exits 0 — the prompt path's guard passed unedited (T-27-01-05).
- Both ownership sweeps return `0`.
- `CookieHeaderRuleOwnershipTest` green with exactly 3 tests; `PassiveAiScannerHeaderAdmissionTest` green with exactly 3 tests.

## Threat register outcome

`T-27-01-01` (variant leak), `T-27-01-05` (parity via narrowing), `T-27-01-06` (third hand-written copy), `T-27-01-07` (predicate cost), `T-27-01-08` (undocumented over-match) and `T-27-01-09` (claim wider than the sweep) are mitigated as planned.

`T-27-01-02` (locale) needs restating for 27-03: the threat is **not** currently instantiated anywhere in `src/main/kotlin`, because every lowering uses the locale-agnostic Kotlin spelling. The mitigation shipped is a guard against introducing the hazardous spelling, plus tests that catch it — not the closure of an active hazard. Certifying it as "an active hazard, now closed" would overstate the record.

`T-27-01-03` / `T-27-01-04` remain accepted residuals **AR-27-01** and **AR-27-02**, now pinned by a passing assertion rather than by prose.

## Threat Flags

None — no new network endpoint, auth path, file access pattern or schema change at a trust boundary. `CookieHeaderRuleOwnershipTest` reads the repository's own source tree at test time, which is build-time-only and not a runtime surface.

## Next Phase Readiness

- Ready for **27-02** (`CookieHeaderNameParityTest`, the `ordering` edge). `Redaction.isCookieHeaderName` is public and is the symbol 27-02's parity test asserts against.
- **Input that 27-03 must not skip:** the locale finding above narrows what a security-register amendment may claim about `T-27-01-02`, and the "114 surviving hazards" framing the plan anticipated is measurably wrong. 27-03 should cite the measured zero, not the grep count.

## Self-Check: PASSED

- Created files exist on disk: `CookieHeaderRuleOwnershipTest.kt`, `PassiveAiScannerHeaderAdmissionTest.kt`, `27-01-SUMMARY.md`.
- All four commits exist: `02d71c2`, `984c296`, `fe379e5`, `96d633f`.
- `git diff HEAD~4 HEAD --name-only` lists exactly the 6 source files plus the SUMMARY. **No `STATE.md` or `ROADMAP.md`** — the orchestrator owns those writes.
- Working tree clean; both red-probe restorations verified with `git diff --quiet src/main/kotlin`.
- Every task-level `<acceptance_criteria>` was executed and passed; the four that could not be satisfied as literally written are recorded above as deviations with their measurements, not silently skipped.

---
*Phase: 27-priv-05-gap-closure-sanitize-headers*
*Completed: 2026-08-24*
