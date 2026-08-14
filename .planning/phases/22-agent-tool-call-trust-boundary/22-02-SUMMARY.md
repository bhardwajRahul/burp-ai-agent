---
phase: 22-agent-tool-call-trust-boundary
plan: 02
subsystem: mcp
tags: [sec-06, trust-boundary, mcp-catalog, classification]
requires: []
provides:
  - SecTier
  - McpToolDescriptor.secTier
  - McpToolCatalogTierParityTest
affects:
  - com.six2dez.burp.aiagent.mcp
tech-stack:
  added: []
  patterns:
    - "Required constructor parameter with deliberately no default value (D-03) — forgetting is a compile error"
    - "Enum with a single wireValue string, following McpAccessControlDecision.BlockReason"
    - "Enumerated-literal test expectations instead of computed ones, so a laxer tier requires a reviewed diff"
key-files:
  created:
    - src/test/kotlin/com/six2dez/burp/aiagent/mcp/McpToolCatalogTierParityTest.kt
  modified:
    - src/main/kotlin/com/six2dez/burp/aiagent/mcp/McpToolCatalog.kt
    - src/test/kotlin/com/six2dez/burp/aiagent/ui/McpToolTabModelTest.kt
decisions:
  - "SecTier is an enum, never a Boolean or String, so a future positional construction site is a type error rather than a silent argument shift"
  - "secTier is defaulted at the McpToolTabModelTest helper level only; D-03 governs the production data class, not test fixtures"
  - "The 19/26/14 split was locked by the plan, including every row research flagged ambiguous; no tier was re-decided during execution"
metrics:
  duration: ~50 min
  completed: 2026-08-14
  tasks: 2
  commits: 2
---

# Phase 22 Plan 02: SEC-06 Tool Classification Summary

Three-valued `SecTier` enum plus a required, non-defaulted `secTier` field on `McpToolDescriptor`, declared by all 59 MCP catalog tools (19 AUTO / 26 CONFIRM / 14 CONFIRM_EACH), with a parity test that pins the AUTO and CONFIRM_EACH sets to enumerated literals.

## What Was Built

**`SecTier`** (`McpToolCatalog.kt`) — a public enum with three constants carrying wire strings (`auto`, `confirm`, `confirm_each`), following the `McpAccessControlDecision.BlockReason` convention so the approval card and any audit payload read the same token and cannot disagree. Its KDoc carries D-05's definition verbatim ("read-only AND bounded output. A tool qualifies only if it neither mutates Burp state nor pulls bulk attacker-controlled traffic into model context") and states the D-01 independence rule: `unsafeOnly` is a *capability* switch, `secTier` is a *trust* axis.

**`McpToolDescriptor.secTier`** — a required field with **no default value**, positioned adjacent to `unsafeOnly`, carrying the `ContextPreviewDialog.kt:21-23` comment shape. This inverts the failure mode recorded in `.planning/codebase/CONCERNS.md` §"MCP unsafe-tool gate — new tools must opt in": a new tool that forgets to classify itself no longer inherits permissive behaviour silently — it fails to compile.

**All 59 construction sites classified** to the plan's locked split. Six sites a future contributor is most likely to want to change carry a one-line justification comment: `cookie_jar_get`, `scope_exclude`, `project_options_get`, `user_options_get`, `ai_analyze`, `ai_passive_scan`.

**`McpToolCatalogTierParityTest`** — five tests, all expectations written as enumerated literals rather than computed from the catalog, because a computed expectation would follow any edit silently.

## Verification Evidence

**D-03 (the compile-error mechanism) was proven, not assumed.** Removing a single `secTier = SecTier.AUTO,` line and compiling produced:

```
e: .../McpToolCatalog.kt:58:17 No value passed for parameter 'secTier'.
```

A hard compile error at the catalog site, exactly the inversion the phase depends on. Reverted immediately afterward.

**The parity test is non-vacuous.** Promoting `site_map` from `CONFIRM` to `AUTO` turned the suite red on `autoTierIsExactlyTheEnumeratedNineteen` (5 tests completed, 1 failed). Reverted; catalog returned to 19/26/14.

**Classification is a verified exact partition.** Before writing any code, the three ID lists were diffed against the 59 IDs extracted from the catalog: exact match, no duplicates, no omissions. Cross-checks against the 21 `unsafeOnly` tools confirmed zero overlap with AUTO, 12 unsafe→CONFIRM_EACH and 9 unsafe→CONFIRM, matching research's measured totals.

**All acceptance-criteria greps pass:**

| Check | Expected | Actual |
|-------|----------|--------|
| `enum class SecTier` | 1 | 1 |
| `val secTier: SecTier` | 1 | 1 |
| `val secTier: SecTier = ` (a default) | 0 | 0 |
| `secTier = SecTier\.` | 59 | 59 |
| `secTier = SecTier.AUTO` | 19 | 19 |
| `secTier = SecTier.CONFIRM,` | 26 | 26 |
| `secTier = SecTier.CONFIRM_EACH` | 14 | 14 |
| `read-only AND bounded output` | ≥1 | 1 |
| `fun unsafeToolIds` | 1 | 1 |
| `@Test` in parity test | 5 | 5 |
| `kotlin.test` in parity test | 0 | 0 |

**Gates:** `./gradlew test ktlintCheck detekt -q` exits 0. `git diff --stat -- detekt-baseline.xml` is empty — the baseline was never touched. The descriptor went from 8 to 9 constructor parameters, under `detekt.yml`'s `constructorThreshold: 10`, as research predicted.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Wrong package on the `SecTier` import in the test helper**
- **Found during:** Task 1
- **Issue:** The import was first written as `com.six2dez.burp.aiagent.SecTier`; `SecTier` lives in the `.mcp` subpackage.
- **Fix:** Corrected to `com.six2dez.burp.aiagent.mcp.SecTier`, placed after `McpToolDescriptor` to satisfy ktlint's alphabetical import ordering.
- **Files modified:** `src/test/kotlin/com/six2dez/burp/aiagent/ui/McpToolTabModelTest.kt`
- **Commit:** 8828fb7

**2. [Rule 3 - Blocking] ktlint `chain-method-continuation` violations in the new test**
- **Found during:** Task 2
- **Issue:** `ktlintTestSourceSetCheck` reported 8 violations — the two `McpToolCatalog.all().filter { … }.map { … }.toSet()` chains needed to be broken across lines.
- **Fix:** Split both chains onto separate lines; also pre-emptively reordered `assertNotEquals` before `assertNotNull`.
- **Files modified:** `src/test/kotlin/com/six2dez/burp/aiagent/mcp/McpToolCatalogTierParityTest.kt`
- **Commit:** ed05da8

No architectural deviations. No tier was re-decided — the plan locked all six ambiguous rows and every one was implemented as written. No packages added, consistent with threat `T-22-SC` (accept, zero dependency changes).

## Threat Model Coverage

| Threat ID | Disposition | How this plan discharges it |
|-----------|-------------|------------------------------|
| T-22-02 | mitigate | Bulk-read tools (`proxy_http_history*`, `response_body_search`, `proxy_ws_history*`, `site_map*`, `scanner_issues`, `editor_get`, `cookie_jar_get`, `ai_findings_recent`, `ai_audit_query`) are all CONFIRM. `autoTierIsExactlyTheEnumeratedNineteen` makes any promotion a reviewed diff — demonstrated by the `site_map` flip. |
| T-22-03 | mitigate | `ai_analyze` and `ai_passive_scan` are CONFIRM_EACH, so a per-tool session grant can never cover a later argument set. Asserted by `tierIsIndependentOfUnsafeOnly`. |
| T-22-04 | mitigate | `scope_include` is CONFIRM_EACH; `scope_exclude` is CONFIRM, with the asymmetry recorded in a code comment. |
| T-22-05 | mitigate | `project_options_set` and `user_options_set` are CONFIRM_EACH. |
| T-22-14 | mitigate | `secTier` is required and non-defaulted; proven to be a compile error above. |
| T-22-15 | mitigate | `noUnsafeToolIsAuto` and `tierIsIndependentOfUnsafeOnly` pin the two axes as separate. `unsafeToolIds()` is byte-for-byte unchanged and still returns the same 21 IDs. |

No new security-relevant surface was introduced — this plan adds a classification field and a test, no network endpoints, auth paths, file access or schema changes.

## Known Stubs

None. `SecTier` is fully populated across the catalog. The *consumption* side (the approval card and the gate that reads `secTier` at call time) is deliberately out of this plan's scope and belongs to later plans in phase 22 — this plan delivers only the classification and its invariant, which is what its objective states.

## Success Criteria

- [x] Every catalog tool declares a SEC-06 tier; omitting one does not compile — verified by deliberate compile break
- [x] The AUTO set is exactly the 19 enumerated tools; CONFIRM_EACH is exactly the 14
- [x] `unsafeToolIds()` and every existing catalog accessor behave identically — untouched; `McpToolParityTest` still green
- [x] No `unsafeOnly` tool is AUTO; `ai_analyze` and `ai_passive_scan` are CONFIRM_EACH without being `unsafeOnly`

## Self-Check: PASSED

- FOUND: `src/main/kotlin/com/six2dez/burp/aiagent/mcp/McpToolCatalog.kt`
- FOUND: `src/test/kotlin/com/six2dez/burp/aiagent/mcp/McpToolCatalogTierParityTest.kt`
- FOUND: `src/test/kotlin/com/six2dez/burp/aiagent/ui/McpToolTabModelTest.kt`
- FOUND: commit `8828fb7`
- FOUND: commit `ed05da8`
