---
phase: 26-coverage-static-analysis-debt-docs
plan: 02
subsystem: testing
tags: [jacoco, junit5, mockito-kotlin, kotlinx-serialization, mcp, redaction, path-traversal]

# Dependency graph
requires:
  - phase: 15-privacy-hardening
    provides: RedactionPolicy.fromMode / Redaction.anonymizeHost — the policy table these tests derive their expectations from
  - phase: 25-mcp-hardening
    provides: the post-Phase-25 mcp tree whose 61.83% line coverage is this plan's measured baseline
provides:
  - McpToolHelpersTest — behavioural coverage of all 14 pure/near-pure helpers in McpToolHelpers.kt, including a recorded red probe on the report-path containment guard
  - SerializationTest — first test coverage of the MCP wire schema (new src/test/kotlin/.../mcp/schema/ package)
  - McpToolModelsTest — JSON-in/guard-out coverage of the model-supplied tool inputs
  - a documented measure-test-remeasure recipe for jacoco-gated coverage work on this repo
affects: [26-07, coverage-gates, mcp-tools, privacy-controls]

# Actuals (#2632)
actuals:
  tokens: 18617
  tasks: 3
  commits: 3

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Named mockito mocks (mock<T>(name = ...)) to control toString() on Montoya interfaces without stubbing toString()"
    - "Recording resolveHost transform as the observable proxy for a guard that cannot reach the Montoya object factory in a unit test"
    - "Deriving redaction expectations from RedactionPolicy.fromMode instead of re-encoding the mode-to-flag table in the test"

key-files:
  created:
    - src/test/kotlin/com/six2dez/burp/aiagent/mcp/tools/McpToolHelpersTest.kt
    - src/test/kotlin/com/six2dez/burp/aiagent/mcp/schema/SerializationTest.kt
    - src/test/kotlin/com/six2dez/burp/aiagent/mcp/tools/McpToolModelsTest.kt
  modified: []

key-decisions:
  - "Header fixtures are mockito mocks, not HttpHeader.httpHeader(...) — the Montoya static factory NPEs outside the Burp runtime (ObjectFactoryLocator.FACTORY is null)"
  - "toMontoyaServiceOrNull's accept path is asserted through a recording resolveHost transform rather than by asserting a returned HttpService, because building one requires the absent Montoya object factory"
  - "REQUIREMENTS.md was NOT touched: QUAL-06 is claimed by four plans (26-01, 26-02, 26-03, 26-07), three of which run concurrently in Wave 1"
  - "executeIssueCreate / findProxyHistoryMatch / hasEquivalentIssue / getActiveEditor deliberately excluded — Montoya-graph-bound or EDT-bound"

patterns-established:
  - "Red probe as falsifiability evidence: delete the guard, record the observed failure, restore with git checkout HEAD -- <file>, then assert git diff --quiet src/main/kotlin exits 0"
  - "Pin observed behaviour with a test name that states it, rather than guessing an expectation and bending production code"

requirements-completed: []

coverage:
  - id: D1
    description: "sanitizeHeaders asserted per PrivacyMode against RedactionPolicy.fromMode, with case-insensitive header-name matching and casing/order preservation (T-26-02-01)"
    requirement: QUAL-06
    verification:
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/mcp/tools/McpToolHelpersTest.kt#SanitizeHeaders (7 tests)"
        status: pass
    human_judgment: false
  - id: D2
    description: "maybeAnonymizeUrl asserted STRICT-only, host-only, and safe on unparseable / hostless input (T-26-02-02)"
    requirement: QUAL-06
    verification:
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/mcp/tools/McpToolHelpersTest.kt#MaybeAnonymizeUrl (4 tests)"
        status: pass
    human_judgment: false
  - id: D3
    description: "resolveReportPath containment asserted as a REJECTION for both the relative-parent-segment and absolute-outside-home forms, proven falsifiable by a recorded red probe (T-26-02-03)"
    requirement: QUAL-06
    verification:
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/mcp/tools/McpToolHelpersTest.kt#ResolveReportPath (7 tests)"
        status: pass
    human_judgment: false
  - id: D4
    description: "The remaining ten pure McpToolHelpers functions have both branch directions asserted; truncateIfNeeded asserted as a BYTE bound on a multi-byte payload (T-26-02-06)"
    requirement: QUAL-06
    verification:
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/mcp/tools/McpToolHelpersTest.kt (66 tests total)"
        status: pass
    human_judgment: false
  - id: D5
    description: "MCP wire schema mapping asserted, including severity/confidence mapped by NAME and every null-tolerance branch"
    requirement: QUAL-06
    verification:
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/mcp/schema/SerializationTest.kt (26 tests)"
        status: pass
    human_judgment: false
  - id: D6
    description: "Model-supplied tool inputs deserialised through the production toolJson; blank-host / non-positive-port guard asserted as a rejection (T-26-02-04) and missing required fields asserted to FAIL (T-26-02-05)"
    requirement: QUAL-06
    verification:
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/mcp/tools/McpToolModelsTest.kt (32 tests)"
        status: pass
    human_judgment: false
  - id: D7
    description: "SC2 coverage floors met: mcp tree 71.02% line (floor 65.0%), measured from build/reports/jacoco/test/jacocoTestReport.xml"
    requirement: QUAL-06
    verification:
      - kind: other
        ref: "JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test jacocoTestReport — mcp tree LINE 2858/4024"
        status: pass
    human_judgment: false

# Metrics
duration: 42min
completed: 2026-08-22
status: complete
---

# Phase 26 Plan 02: MCP Coverage on the Privacy and Trust Paths Summary

**The `mcp` tree went from 61.83% to 71.02% line coverage by asserting the four privacy/trust helpers, the ten remaining pure helpers, the wire schema, and the model-supplied tool inputs — with the report-path containment guard proven falsifiable by a recorded red probe.**

## Performance

- **Duration:** 42 min
- **Started:** 2026-08-22T14:40:00+02:00
- **Completed:** 2026-08-22T15:19:04+02:00
- **Tasks:** 3
- **Files modified:** 3 (all new test sources; zero production files)

## Accomplishments

- **All five SC2 coverage floors met, every one by a wide margin** (numbers below are covered/total line pairs read out of `build/reports/jacoco/test/jacocoTestReport.xml`).
- **The two MCP privacy controls that nothing previously asserted now have tests.** `sanitizeHeaders` is a second redaction path, independent of `Redaction.redact`, and it had no coverage at all; it is now asserted per `PrivacyMode` against `RedactionPolicy.fromMode` — the policy table itself, not a stale copy — with case-insensitive header-name matching (the PRIV-05 failure mode) explicitly covered.
- **The report-path containment guard is proven falsifiable, not merely green.** A recorded red probe deleted `if (!resolved.startsWith(home))` and turned exactly the three escape tests red.
- **`Serialization.kt` reached 100% line coverage** and its whole package went from 48.59% to 97.89%.
- Whole suite: **1004 tests, 0 failures, 1 pre-existing skip** (was 880 tests before this plan). `detekt` and `ktlintCheck` green; `detekt-baseline.xml` byte-identical; `git diff --quiet src/main/kotlin` exits 0.

### Coverage: measured before and after

| Scope | Before (4f0ebd7) | After | Floor | Met |
|---|---|---|---|---|
| `mcp` tree (4 packages summed) | 2488/4024 = 61.83% | **2858/4024 = 71.02%** | ≥ 65.0% | yes |
| pkg `mcp/tools` | 956/2221 = 43.04% | **1256/2221 = 56.55%** | ≥ 49.0% | yes |
| pkg `mcp/schema` | 69/142 = 48.59% | **139/142 = 97.89%** | ≥ 70.0% | yes |
| `McpToolHelpers.kt` | 23/224 = 10.27% | **134/224 = 59.82%** | ≥ 58.0% | yes |
| `McpToolModels.kt` | 83/315 = 26.35% | **271/315 = 86.03%** | ≥ 50.0% | yes |
| `Serialization.kt` | 38/108 = 35.19% | **108/108 = 100.00%** | ≥ 70.0% | yes |

Branch coverage moved with it: the `mcp` tree went 792/1700 = 46.59% → 890/1700 = 52.35%.

Per-task progression on `McpToolHelpers.kt`, recorded as covered/total pairs rather than bare percentages:

| Point | covered/total | % |
|---|---|---|
| Pre-phase | 23/224 | 10.27% |
| After Task 1 (4 privacy/trust helpers) | 74/224 | 33.04% |
| After Task 2 (remaining 10 helpers) | 134/224 | 59.82% |

Task 1's floor was ≥ 30.0% and Task 2's was ≥ 58.0%; both were cleared.

## Task Commits

Each task was committed atomically:

1. **Task 1: The measure-test-remeasure loop, proven on the four privacy/trust helpers** — `3881215` (test)
2. **Task 2: Cover the remaining ten pure helpers and take the McpToolHelpers floor** — `216a809` (test)
3. **Task 3: Cover the MCP wire schema and the model-supplied tool inputs; take the tree floor** — `add9c8e` (test)

_Task 1 was the plan's `type="tracer"` task: a thin end-to-end slice of the measure-test-remeasure loop (write assertions → run the class → run the full suite → parse the jacoco XML → confirm the counter moved), executed and verified before Tasks 2 and 3 applied the same recipe at volume._

## Files Created/Modified

- `src/test/kotlin/com/six2dez/burp/aiagent/mcp/tools/McpToolHelpersTest.kt` — **created.** 66 tests across 14 `@Nested` groups, one per helper, so a failure names the helper without reading the stack. Covers all fourteen pure/near-pure helpers in `McpToolHelpers.kt`.
- `src/test/kotlin/com/six2dez/burp/aiagent/mcp/schema/SerializationTest.kt` — **created**, in a new test source directory `src/test/kotlin/com/six2dez/burp/aiagent/mcp/schema/` with a matching `package` declaration (`InvalidPackageDeclaration` is a live detekt rule here). 26 tests covering the four `toSerializableForm` extensions, `toSiteMapEntry`, and round-trips of every schema data class through the production `toolJson`.
- `src/test/kotlin/com/six2dez/burp/aiagent/mcp/tools/McpToolModelsTest.kt` — **created.** 32 tests. Every model is built by deserialising JSON; Kotlin constructors appear only as the *expected* side of an equality assertion.

No production source file was created or modified. No dependency was added — `mockito-kotlin`, `junit-jupiter` and `montoya-api` were already on `testImplementation`.

## Red Probe (Task 1 acceptance criterion), with observed output

The containment check at `McpToolHelpers.kt:376-378` was deleted:

```kotlin
    if (!resolved.startsWith(home)) {
        throw IllegalArgumentException("Report path must be under $home")
    }
```

`./gradlew test --tests '*McpToolHelpersTest'` then reported:

```
> Task :test FAILED
McpToolHelpersTest > ResolveReportPath > deeplyNestedParentSegmentsThatEscapeUserHomeAreRejected() FAILED
McpToolHelpersTest > ResolveReportPath > absolutePathOutsideUserHomeIsRejected() FAILED
McpToolHelpersTest > ResolveReportPath > relativeParentSegmentsThatEscapeUserHomeAreRejected() FAILED
23 tests completed, 3 failed
BUILD FAILED in 2s
```

Exactly the three escape-path assertions turned red and nothing else did — the guard's tests fail for the guard's absence and for no other reason. Restored with `git checkout HEAD -- src/main/kotlin/com/six2dez/burp/aiagent/mcp/tools/McpToolHelpers.kt` (never `git stash` — `refs/stash` is shared across linked worktrees and three sibling executors were live). `git diff --quiet src/main/kotlin` exits 0.

## Deliberate exclusions (Task 2), with reasons

Four functions in `McpToolHelpers.kt` hold 90 of the file's original 201 missed lines and were left uncovered on purpose:

| Function | Missed lines | Reason |
|---|---|---|
| `executeIssueCreate` | 54 | Needs a deep `MontoyaApi` graph (`siteMap()`, `scanner()`, `proxy()`) plus the `AuditIssue.auditIssue(...)` static factory. The factory dereferences `burp.api.montoya.internal.ObjectFactoryLocator.FACTORY`, which is null outside Burp, so the only way to "cover" this is to mock the whole graph — at which point the mock, not the function, is what the test asserts. |
| `findProxyHistoryMatch` | 24 | Same: requires `api.proxy().history()` and Montoya request/response construction. |
| `hasEquivalentIssue` | 5 | Delegates to `IssueUtils.hasEquivalentIssue`, which already has its own coverage; the wrapper needs `api.siteMap().issues()`. |
| `getActiveEditor` | 8 | Walks `KeyboardFocusManager.getCurrentKeyboardFocusManager()` and is EDT-bound. The test JVM runs with `-Djava.awt.headless=true`, which makes this untestable here. |

That accounts for the gap between `McpToolHelpers.kt`'s 59.82% and 100%: 90 of the 90 remaining missed lines are exactly these four functions.

## Model coverage in `McpToolModelsTest` — what was covered and what was left

Covered by deserialisation-plus-assertion, prioritising `secTier` `CONFIRM_EACH` / `CONFIRM` inputs per the plan: `SendHttp1Request`, `SendHttp2Request`, `CreateRepeaterTab`, `RepeaterTabWithPayload`, `SendToIntruder`, `IntruderPrepare`, `InsertionPointRange`, `InsertionPoints`, `StartAudit`, `StartAuditMode`, `StartAuditWithRequests`, `StartCrawl`, `GenerateScannerReport`, `GetScanTaskStatus`, `DeleteScanTask`, `CreateAuditIssue`, `ScopeCheck`, `ScopeUpdate`, `SetProjectOptions`, `SetUserOptions`, `SetTaskExecutionEngineState`, `SetProxyInterceptState`, `SetActiveEditorContents`, `ProxyHistoryAnnotate`, `CookieJarGet`, `CookieEntry`, `CollaboratorGenerate`, `CollaboratorPoll`, `JwtDecode`, `HashCompute`, `GenerateRandomString`, `UrlEncode`, `UrlDecode`, `Base64Encode`, `Base64Decode`, `DecodeAs`, `DiffRequests`, `FindReflected`, `ComparerSend`, `ExtractParams`, `RequestParse`, `ResponseParse`, `ParsedParam`, `ParsedRequest`, `ParsedResponse`, and all ten `Paginated` models.

Left uncovered, as a recorded decision rather than an accident: `ToolSpec` (a plain non-`@Serializable` DTO built by `describeTools`, already exercised through the executor's own tests, and not on the model-supplied-JSON trust path this plan is about). The residual 44 uncovered lines in `McpToolModels.kt` are generated `copy`/`hashCode`/`componentN` members that no production call site invokes.

## Decisions Made

1. **Header fixtures use `mock<HttpHeader>()`, not the Montoya static factory.** The plan's action text specified `HttpHeader.httpHeader(name, value)`. That call resolves to `ObjectFactoryLocator.FACTORY.httpHeader(...)` and `FACTORY` is a public static field left null outside the Burp runtime, so it NPEs in a unit test. The repo already handles this the same way in `InjectionPointExtractorTest`, and `AiPassiveScanCheckTest` documents the NPE explicitly. See Deviation 1.
2. **`toMontoyaServiceOrNull`'s accept path is asserted through a recording `resolveHost` transform.** Same root cause: a valid host/port pair passes the guard and then calls `HttpService.httpService(...)`, which cannot succeed outside Burp. Because `resolveHost(targetHostname)` is evaluated as the factory's first argument, "the resolver saw the hostname" and "the resolver saw nothing" are precisely the accept and reject outcomes — a stronger and more stable assertion than catching an NPE. The test additionally asserts the call never yields a *silent null* for a valid pair.
3. **Montoya values that map through `toString()` use named mocks** (`mock<HttpResponse>(name = "HTTP/1.1 200 OK")`). Mockito returns the mock name from `toString()`, which gives deterministic fixture text without stubbing `toString()` itself.
4. **`REQUIREMENTS.md` was not touched.** QUAL-06 is claimed by 26-01, 26-02, 26-03 and 26-07; three of those ran concurrently in Wave 1 against the same file. Marking it complete from this worktree would be both premature and a guaranteed merge conflict. Flagged for the orchestrator below.
5. **Two degenerate behaviours were run, observed, and pinned rather than assumed**, per the plan's instruction: `countOccurrences("", ...)` returns 0 for an empty needle, and `resolveAuditConfig` does *not* catch the `Enum.valueOf` failure for an unrecognised value — it propagates. Both have test names that state the behaviour.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Montoya static factories cannot be called in a unit test**

- **Found during:** Task 1 (and again in Task 3)
- **Issue:** The plan's action text for Task 1 said to build header fixtures with "the Montoya static factory `HttpHeader.httpHeader(name, value)`; the `montoya-api` artifact is on `testImplementation`, so no mock is needed for a header." Being on the classpath is not sufficient: `javap` on `montoya-api-2026.2.jar` shows every `httpService`/`httpHeader` static factory compiles to `getstatic burp/api/montoya/internal/ObjectFactoryLocator.FACTORY` followed by an interface call. `FACTORY` is a public static field with no static initialiser — it is null until Burp populates it — so each factory NPEs outside the Burp runtime. The same applies to `HttpService.httpService(...)`, which `toMontoyaServiceOrNull` calls on its accept path.
- **Fix:** Header fixtures are `mock<HttpHeader>()` with `name()`/`value()` stubbed, following the existing `InjectionPointExtractorTest`. The `toMontoyaServiceOrNull` accept path is asserted via the recording-resolver technique in Decision 2. Both choices are documented in the test-class KDoc so the next reader does not retry the factory.
- **Files modified:** `McpToolHelpersTest.kt`, `McpToolModelsTest.kt` (test sources only)
- **Verification:** `./gradlew test --tests '*McpToolHelpersTest' --tests '*McpToolModelsTest'` green; the guard tests are falsifiable (Task 1's red probe covers the containment guard directly).
- **Committed in:** `3881215` and `add9c8e`

**2. [Rule 3 - Blocking] Mockito `UnfinishedStubbingException` from mocks created inside `thenReturn(...)`**

- **Found during:** Task 3 (`SerializationTest`)
- **Issue:** 19 of 26 tests failed on first run with `UnfinishedStubbingException`. Fixture helpers written as `whenever(rr.annotations()).thenReturn(annotations(notes))` create and stub a *second* mock while the first `whenever` chain is still open, which Mockito rejects.
- **Fix:** Every collaborating mock is now fully built into a local `val` before the enclosing `whenever` chain starts, with a comment naming the reason.
- **Files modified:** `SerializationTest.kt`
- **Verification:** `./gradlew test --tests '*SerializationTest'` — 26/26 green.
- **Committed in:** `add9c8e`

**3. [Rule 1 - Bug] Test asserted a `SerializationException` the production Json configuration does not throw**

- **Found during:** Task 3 (`McpToolModelsTest`)
- **Issue:** A test asserted that `{"targetPort":"443"}` (a quoted integer for an `Int` field) fails deserialisation. It does not — `toolJson` accepts it and yields `443`. The test, not the production code, was wrong.
- **Fix:** Per the plan's explicit prohibition on bending production code to match a guessed expectation, the observed behaviour was pinned in a test whose name states it (`aQuotedIntegerIsAcceptedForAnIntFieldByTheProductionJsonConfiguration`), with a comment explaining that a future change to the Json configuration should surface here as a failure. A separate test now asserts genuine wrong-shape rejection using `{"targetPort":[443]}`.
- **Files modified:** `McpToolModelsTest.kt`
- **Verification:** `./gradlew test --tests '*McpToolModelsTest'` — 32/32 green.
- **Committed in:** `add9c8e`

**4. [Rule 3 - Blocking] Two ktlint `function-signature` violations in `McpToolModelsTest`**

- **Found during:** Task 3 verification
- **Issue:** `ktlintCheck` failed on three single-expression fixture helpers ("First line of body expression fits on same line as function signature").
- **Fix:** `./gradlew ktlintFormat`. Formatting only; no behavioural change. Re-ran the full suite afterwards to confirm.
- **Files modified:** `McpToolModelsTest.kt`
- **Verification:** `./gradlew detekt ktlintCheck` green; full `./gradlew test` green afterwards.
- **Committed in:** `add9c8e`

**5. [Rule 3 - Blocking] `REQUIREMENTS.md` intentionally not updated**

- **Found during:** close-out
- **Issue:** QUAL-06 is claimed by four plans in this phase — 26-01, 26-02, 26-03, 26-07 — and 26-01/26-02/26-03 executed concurrently in Wave 1. Marking QUAL-06 complete from this worktree would assert a requirement this plan only partly satisfies, and would collide with two sibling executors editing the same lines.
- **Fix:** `requirements-completed` left empty; the decision recorded here and flagged for the orchestrator.
- **Files modified:** none
- **Verification:** `git status --short` clean apart from this SUMMARY.

---

**Total deviations:** 5 auto-fixed (4 blocking, 1 bug)
**Impact on plan:** No scope creep. Deviations 1–4 were mechanical obstacles between the plan's intent and a green suite; none changed what is asserted, and none touched production code. Deviation 5 is a deliberate withholding to avoid a false requirement claim and a concurrent-write conflict.

## Issues Encountered

- **`RedactionTest` wall-clock flake did not fire.** The known SafeRegex 50 ms deadline flake is recorded for this repo under CPU load, and four executors were running full Gradle builds concurrently throughout. All three full-suite runs came back with 0 failures, so there is nothing to report on that front.
- **Full-suite runs cost ~2m50s each** under that concurrency. Three were needed (one per task) because the coverage floors are stated against the whole-suite jacoco report, not a filtered run.

## Known Stubs

None. Every test added by this plan asserts on a result; no test executes production code merely to move a jacoco counter. No test is `@Disabled` or skipped, no existing test was deleted, renamed or narrowed, and the plan's `<verify>` block was run in full for every task.

## Threat Flags

None. This plan adds no production surface — it is test-source only. The four trust boundaries in the plan's threat model (`sanitizeHeaders`, `maybeAnonymizeUrl`, `resolveReportPath`, `toMontoyaServiceOrNull`) all had `mitigate` dispositions satisfied by making the existing controls falsifiable; see D1–D3 and D6 in the coverage block.

## User Setup Required

None — no external service configuration required.

## Next Phase Readiness

- **For the orchestrator:** `STATE.md` and `ROADMAP.md` were not touched (worktree mode). **`REQUIREMENTS.md` was also not touched** — QUAL-06 should be marked complete only once 26-01, 26-03 and 26-07 have also landed.
- **For 26-07:** this plan added a new test source directory `src/test/kotlin/com/six2dez/burp/aiagent/mcp/schema/` whose `package` declaration matches its path, so it adds no second `InvalidPackageDeclaration` violation to the one 26-07 is removing. `detekt-baseline.xml` is byte-identical to its state at `4f0ebd7`.
- **Remaining headroom in the `mcp` tree** now sits mainly in `mcp/external` (137/225 = 60.89% line, 13/66 = 19.70% branch — untouched by this plan) and in the Montoya-bound functions listed under "Deliberate exclusions". Anyone raising the tree further should start with `mcp/external`'s branch coverage, which is the weakest number left.

---
*Phase: 26-coverage-static-analysis-debt-docs*
*Completed: 2026-08-22*
