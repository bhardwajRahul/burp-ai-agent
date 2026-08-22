---
phase: 26-coverage-static-analysis-debt-docs
plan: 07
subsystem: testing
tags: [detekt, ktlint, jacoco, static-analysis, coverage, kotlin, gradle]

requires:
  - phase: 26-01
    provides: backends.cli coverage floors and the CliBackend.kt shape this plan edits
  - phase: 26-02
    provides: McpToolHelpers.resolveReportPath behavioural assertions that constrain the UseRequire conversion
  - phase: 26-03
    provides: config and redact coverage floors re-verified by the seal
  - phase: 26-04
    provides: the post-wave-1 ChatPanel.kt shape that makes cancelCurrentRequest vs cancelInFlightRequest decidable
  - phase: 26-05
    provides: the GitBook handoff whose status this plan carries forward as OPEN
  - phase: 26-06
    provides: merged tree the seal is measured against
provides:
  - detekt baseline trimmed 1096 -> 1040 entries, removals only, 45 of 56 backed by a source fix
  - proof that no Phase 20-25 finding was ever added to the baseline (empty git log, one command)
  - 26-COVERAGE.md - both coverage endpoints with commands, commits and a 14-floor MET/MISSED verdict
  - AgentSettings DEFAULT_* constants replacing fourteen constant-returning private functions
affects: [static-analysis-debt, coverage-floors, ui-design-tokens, magic-number-cleanup]

actuals:
  tokens: 21000
  tasks: 3
  commits: 4

tech-stack:
  added: []
  patterns:
    - "Baseline entries are deleted by exact line match, never by regenerating the file"
    - "A source fix and its baseline-entry removal land in the same commit"
    - "A finding whose fix would touch a file outside files_modified is skipped and recorded, not smuggled in"

key-files:
  created:
    - .planning/phases/26-coverage-static-analysis-debt-docs/26-COVERAGE.md
  modified:
    - detekt-baseline.xml
    - src/main/kotlin/com/six2dez/burp/aiagent/config/AgentSettings.kt
    - src/main/kotlin/com/six2dez/burp/aiagent/mcp/tools/McpToolHelpers.kt
    - src/main/kotlin/com/six2dez/burp/aiagent/mcp/KtorMcpServerManager.kt
    - src/main/kotlin/com/six2dez/burp/aiagent/backends/cli/CliBackend.kt
    - src/main/kotlin/com/six2dez/burp/aiagent/ui/ChatPanel.kt

key-decisions:
  - "The MayBeConst fix on DesignTokens.Spacing was abandoned: const val requires SCREAMING_SNAKE_CASE under ktlint, and satisfying both linters would rename 7 tokens across 222 references in 16 files, 14 of them outside files_modified"
  - "KtorMcpServerManager's two compound security gates keep their conditions verbatim and only swap throw for error(); a De Morgan inversion on a TLS/loopback gate is not worth the risk for one baseline entry"
  - "ResponseAnalyzer.truePayload was left in place: removing the parameter changes the method signature, which invalidates its existing CyclomaticComplexMethod baseline entry and would require adding a new one"
  - "SC6's GitBook clause is carried as OPEN, not met - 26-05's handoff diff targets a separate repository and no human has confirmed applying it"

patterns-established:
  - "Recompute the stale/missing set at execution time via a restored detektBaseline probe, never inherit it from plan time"
  - "Report the removals split into stale entries and fixed findings, so a shrink cannot read as larger than the work behind it"

requirements-completed: [QUAL-06, QUAL-07]

coverage:
  - id: D1
    description: "detekt baseline trimmed from 1096 to 1040 entries with a removals-only diff; 45 removals backed by a source fix, 11 by a recomputed stale entry"
    requirement: "QUAL-07"
    verification:
      - kind: other
        ref: "grep -c '<ID>' detekt-baseline.xml -> 1040; git diff -U0 ab567fb..HEAD -- detekt-baseline.xml | grep -c '^+.*<ID>' -> 0"
        status: pass
      - kind: integration
        ref: "JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew detekt -> 0 findings"
        status: pass
    human_judgment: false
  - id: D2
    description: "No Phase 20-25 finding was ever added to the baseline"
    requirement: "QUAL-07"
    verification:
      - kind: other
        ref: "git log --oneline ab567fb..HEAD -- detekt-baseline.xml -> empty"
        status: pass
    human_judgment: false
  - id: D3
    description: "detekt.yml byte-identical to 4f0ebd7; no rule deactivated, no threshold relaxed, no excludes broadened; no new @Suppress"
    requirement: "QUAL-07"
    verification:
      - kind: other
        ref: "git diff --quiet 4f0ebd7..HEAD -- detekt.yml -> 0; git diff -U0 3534219..HEAD -- src/ | grep '^+' | grep -c '@Suppress' -> 0"
        status: pass
    human_judgment: false
  - id: D4
    description: "26-COVERAGE.md records both coverage endpoints with their commands and commits, and a MET/MISSED verdict on all 14 floors claimed by this phase"
    requirement: "QUAL-06"
    verification:
      - kind: other
        ref: ".planning/phases/26-coverage-static-analysis-debt-docs/26-COVERAGE.md; project line 58.14% >= 57.0%, branch 35.75% >= 34.8%"
        status: pass
    human_judgment: false
  - id: D5
    description: "Suite green at the baseline failure and skip counts, and the shadowJar still builds after the source edits"
    verification:
      - kind: integration
        ref: "JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew build jacocoTestReport -> 1131 tests / 158 classes / 0 failures / 1 skip; build/libs/Custom-AI-Agent-full-0.9.2.jar"
        status: pass
    human_judgment: false
  - id: D6
    description: "Behaviour change: String.format now pins Locale.ROOT in ChatPanel and OllamaBackend, altering output under a Turkish or Arabic-digit locale"
    verification: []
    human_judgment: true
    rationale: "The rule exists because the default-locale behaviour is the defect; confirming the new behaviour is desirable under a non-ROOT locale is a product judgment, and no test in this repo runs under one."

duration: 23min
completed: 2026-08-22
status: complete
---

# Phase 26 Plan 07: Detekt Baseline Trim and Phase Coverage Seal Summary

**The detekt baseline finally moves — 1096 to 1040 entries across ten rule categories, 45 of the 56 removals backed by a real source fix, with `detekt.yml` byte-identical and not one `<ID>` added anywhere in the phase; plus `26-COVERAGE.md`, which records both coverage endpoints with their provenance and returns MET on all 14 floors this phase claimed.**

## Performance

- **Duration:** 23 min
- **Started:** 2026-08-22T14:02:06Z
- **Completed:** 2026-08-22T14:24:45Z
- **Tasks:** 3
- **Files modified:** 21 (plus 1 created, 1 renamed)

## Accomplishments

### SC3 clause 2, proven by one command

```
$ git log --oneline ab567fb..HEAD -- detekt-baseline.xml
$
```

**Empty.** `ab567fb` (2026-07-29, `chore(detekt): refresh baseline for v0.9.1 changes`) is the last
commit that touched the file, and it predates `ecc69bc` (2026-08-05), Phase 20's first commit. No
finding introduced by Phases 20 through 25 — or by any of the six sibling plans in this phase — was
ever added to the baseline. The direction check agrees: across `ab567fb..HEAD` the file shows
**56 `<ID>` removals and 0 additions**.

### The stale/missing set, recomputed against the merged tree

The plan measured 7 stale entries at plan time and warned that waves 1 and 2 would change the set.
They did. The probe — back the baseline up outside the repo with `/bin/cp -f`, run `detektBaseline`,
diff both `<ID>` sets, restore, verify — returned:

- **STALE: 11** (up from the planner's 7). The four new ones are all in files waves 1 and 2 edited:
  `MagicNumber:CliBackend.kt$4`, `ReturnCount:CliBackend.kt$shellEscape`,
  `ReturnCount:CliBackend.kt$normalizeWindowsCommand` and `TooManyFunctions:CliBackend.kt$CliConnection`
  — all four downstream of plan 26-01's `shellEscape` rewrite. Two of the planner's seven
  (`ReturnCount:ChatPanel.kt$clearCurrentChat`, `ReturnCount:ChatPanel.kt$maybeExecuteToolCall`)
  survived unchanged.
- **MISSING: 0.** Every current finding is covered by an entry. No wave-1 or wave-2 plan introduced a
  finding, so the blocker condition the plan named at Task 1 Step 2 did not fire.

The probe was restored before any real edit and verified: `git diff --quiet detekt-baseline.xml`
exited **0**, and the file's SHA-256 matched the committed one byte for byte
(`cce3275b2213008c179d388cf26aaf1801248c3f44027294cf4f0948956bf0bd`). A `detektBaseline` run left in
place would have re-baselined every finding this plan then fixed.

### The trim: 1096 → 1040, and where each removal came from

| | count |
|---|---|
| Starting entries (`4f0ebd7`, unchanged through waves 1 and 2) | **1096** |
| Removed — **backed by a source fix** | **−45** |
| Removed — recomputed stale entries (no source change, detekt already ignored them) | **−11** |
| **Final** | **1040** |

**1040 ≤ 1045 (the gate). 45 fixed-finding removals ≥ 40 (the anti-hollow floor).**
The 11 stale prunes are 20% of the shrink; four fifths of it is work.

Seven categories were cleared to zero, three partially:

| Category | Before | After | Removed | Fix |
|---|---|---|---|---|
| `FunctionOnlyReturningConstant` | 14 | **0** | 14 | `private fun defaultXxx()` → `private const val DEFAULT_*`, values byte-identical |
| `UseCheckOrError` | 6 | **0** | 6 | `throw IllegalStateException(m)` → `error(m)` / `check(cond) { m }` |
| `UseRequire` | 4 | **0** | 4 | guarded `throw IllegalArgumentException(m)` → `require(cond) { m }` |
| `ImplicitDefaultLocale` | 3 | **0** | 3 | `Locale.ROOT` passed to `String.format` |
| `UnusedPrivateMember` | 3 | **0** | 3 | dead private functions deleted after a whole-tree reference-count grep |
| `EmptyFunctionBlock` | 1 | **0** | 1 | reason stated in the block, per the repo's `INTENTIONAL:` convention |
| `InvalidPackageDeclaration` | 1 | **0** | 1 | `git mv` the file so its path matches its package |
| `UnusedPrivateProperty` | 13 | 2 | 11 | dead locals, fields and duplicated constants deleted |
| `MayBeConst` | 8 | 7 | 1 | see the skip below |
| `UnusedParameter` | 2 | 1 | 1 | see the skip below |
| stale entries (across `ReturnCount`, `CyclomaticComplexMethod`, `LongMethod`, `TooManyFunctions`, `MagicNumber`) | — | — | 11 | none needed |

The plan projected 62 removals; 56 landed. The 6-entry shortfall is four skips, each recorded below
with its reason. The planner's margin — gate 1045, target 1034 — is exactly what absorbed them.

### Four skips, and why each one is a skip rather than a fix

The plan's prohibition against editing files outside `files_modified` is what stopped three of these.
None was topped up with a stale prune to recover the count.

**1. `MayBeConst` on `DesignTokens.Spacing` — 7 entries left in place.** This is the substantive one.
Converting `val xs = 4` to `const val xs = 4` satisfies detekt and immediately breaks ktlint:
`property-naming` requires SCREAMING_SNAKE_CASE for a `const val`, and it is not auto-correctable.

```
DesignTokens.kt:42:19 Property name should use the screaming snake case notation
when the value can not be changed (cannot be auto-corrected)   [x7]
```

Satisfying both linters means renaming `xs`/`sm`/`md`/`lg`/`xl`/`sectionPad`/`formGridPad` to
`XS`/`SM`/`MD`/`LG`/`XL`/`SECTION_PAD`/`FORM_GRID_PAD` across **222 references in 16 files**, 14 of
which are outside this plan's `files_modified`. That is a design-token pass, not a static-analysis
cleanup. The conversion was written, the ktlint failure observed, and the file reverted to its
committed state. Deferred.

**2. `UnusedParameter` on `ResponseAnalyzer.analyzeBooleanBasedDual(truePayload)` — 1 entry left.**
Removing the parameter changes the method signature, and the baseline keys
`CyclomaticComplexMethod` entries on the signature string. detekt went red with a *new* finding:

```
ResponseAnalyzer.kt:905:9: The function analyzeBooleanBasedDual appears to be too complex based on
Cyclomatic Complexity (complexity: 24). Defined complexity threshold for methods is set to '15'
```

The only ways forward were adding a baseline entry (prohibited, and the exact inversion of this
plan's goal) or refactoring a 24-complexity method (explicitly out of scope). The parameter removal
was reverted in both `ResponseAnalyzer.kt` and its one call site in `ActiveAiScanner.kt`.

**3. `UnusedPrivateProperty` on `ContextCollector.api` — 1 entry left.** Genuinely dead, but removing
the constructor parameter changes `ContextCollector(api)` at `App.kt:105` and at two sites in
`ContextPreviewConsistencyTest.kt`, none of which is in `files_modified`.

**4. `UnusedPrivateProperty` on `OpenAiCompatibleBackend.modelSelector` — 1 entry left.** Same
reason: `NvidiaNimBackendFactory.kt:32` and `PerplexityBackendFactory.kt:23` both pass it by name.

> **Worth a follow-up, not a bug.** `modelSelector` is unreferenced inside `OpenAiCompatibleBackend`
> — the model is read from `config.model` at line 67 — so those two factories are passing a lambda
> that has no effect. I traced it before deciding: the model does reach the backend, via
> `AgentSupervisor.kt:828/859` and each factory's own `settings.<x>Model.trim()` at
> `NvidiaNimBackendFactory.kt:65` / `PerplexityBackendFactory.kt:44`. So this is redundant dead
> configuration, **not** a live defect where NVIDIA and Perplexity ignore their configured model.
> Removing the parameter and the two call sites is a clean three-file follow-up.

### The seal — `26-COVERAGE.md`

Both endpoints, each with the command and commit that produced it, re-measured on the merged tree at
`a3f4b74` rather than trusting any sibling plan's isolated figure.

| scope | pre-phase (`4f0ebd7`) | post-phase (`a3f4b74`) |
|---|---|---|
| **project line** | 14138/25096 = **56.34%** | 14567/25053 = **58.14%** |
| **project branch** | 3705/10860 = **34.11%** | 3881/10856 = **35.75%** |

**14 of 14 floors MET.** SC2's targets (≥ 57.0% line, ≥ 34.8% branch) are cleared with 1.14 and 0.95
points of margin. Every per-package and per-file floor claimed by 26-01, 26-02 and 26-03 survives the
merge.

Three post-merge deltas are named in `26-COVERAGE.md` rather than smoothed over, because this plan's
own edits moved denominators: **`McpToolHelpers.kt` fell from 26-02's isolated 59.82% to 59.46%**
(the `require()` conversion removed two `if` lines, 224 → 222), `mcp/tools` moved 56.55% → 56.54%,
and `CliBackend.kt` rose 28.95% → 29.00%. All three remain inside their floors. `config`'s denominator
also dropped 1061 → 1048 from the fourteen `const val` conversions — a smaller file, not a narrowed test.

### SC6's GitBook clause is carried as OPEN

Recorded in both `26-COVERAGE.md` and here: plan 26-05 prepared a diff at `26-GITBOOK-HANDOFF.md`
targeting a **separate repository** (`~/Tools/burp-ai-agent-doc`). No human has confirmed applying it.
The existence of a handoff document does not satisfy the clause, and it is **not** recorded as met.

## Task Commits

1. **Task 1: SC3 clause 2, recompute the stale set, and the trim loop on one category** — `6014f60` (refactor)
2. **Task 2: The dead-code and constant categories** — `bbd013e` (refactor)
3. **Task 3a: The idiom categories** — `a3f4b74` (refactor)
4. **Task 3b: The phase coverage seal** — `d19a9c6` (docs)

## Files Created/Modified

**Created**
- `.planning/phases/26-coverage-static-analysis-debt-docs/26-COVERAGE.md` — both coverage endpoints, 14-floor MET/MISSED table, SC2 derivation rationale, SC6 OPEN

**Renamed**
- `src/test/.../mcp/AiGateMcpToolTest.kt` → `src/test/.../mcp/tools/AiGateMcpToolTest.kt` — `git mv`, recorded by git as `R` (the package line was already correct; the location was wrong)

**Modified — baseline and config**
- `detekt-baseline.xml` — 1096 → 1040 entries, deletions only

**Modified — main**
- `config/AgentSettings.kt` — 14 `private fun defaultXxx()` → `private const val DEFAULT_*`, all call sites updated
- `mcp/tools/McpToolHelpers.kt` — `resolveReportPath`'s empty-path and path-containment guards → `require()`
- `mcp/KtorMcpServerManager.kt` — 3 `throw IllegalStateException` → `error()`, conditions untouched
- `mcp/tools/McpToolExecutorImpl.kt` — Pro-edition gate → `check()`
- `backends/cli/CliBackend.kt` — elvis throw → `error()`; `startProcess` guard → `check()`
- `backends/ollama/OllamaBackend.kt` — `Locale.ROOT` on the `\uXXXX` escape format
- `ui/ChatPanel.kt` — `Locale.ROOT` on two size formats; dead `cancelCurrentRequest()` deleted
- `ui/components/ToolInvocationDialog.kt` — 2 JSON-shape guards → `require()`
- `ui/McpHelpPanel.kt` — unused `api` ctor parameter and its import removed
- `ui/components/PrivacyPill.kt` — dead `offColor`
- `ui/design/Components.kt` — dead `bgColor` in `toolBadge`
- `ui/panels/BackendConfigPanel.kt` — dead `buildSingleFieldPanel`
- `scanner/ActiveAiScanner.kt` — dead `buildMetadataSection`
- `scanner/AiScanCheck.kt` — unused `vulnClass` parameter on `buildDetail` and its one call site
- `scanner/AdaptivePayloadEngine.kt` — dead `safeHost` local
- `scanner/ResponseAnalyzer.kt` — dead `isTrueCondition` local
- `scanner/PassiveAiScanner.kt` — dead `lastRequestTime`
- `scanner/PassiveAiScannerHeuristics.kt` — 3 duplicated constants replaced by a comment recording where the live ones live

**Modified — test**
- `redact/RedactionTest.kt` — `val l = 42` → `const val L = 42` (RFC 5869's own notation, and what ktlint requires)
- `config/SecretCipherTest.kt` — reasons stated in `Handler.flush()` / `close()`
- `audit/AiRequestLoggerTest.kt` — unused loop variable removed via `repeat()`
- `mcp/tools/ProxyHistoryListenerPortFilterTest.kt` — dead `host9999` fixture

## Decisions Made

- **Compound security gates keep their conditions verbatim.** `KtorMcpServerManager`'s external-TLS
  and loopback-host checks are `if (a && !b)` shapes. The plan preferred `check(cond)` with the
  condition inverted; a De Morgan flip on a TLS gate is a genuine correctness risk for one baseline
  entry each, so those three became `error(message)` inside the untouched `if`. Same exception type,
  same message, no boolean logic edited. `check()` was used only where the inversion is trivially
  safe: `isEmpty()` → `isNotEmpty()` and `!=` → `==`.
- **`PassiveAiScannerHeuristics`'s three unused constants were deleted, not preserved as
  documentation.** The plan flagged them as the likely "kept as documentation of a threshold" case.
  Greps proved otherwise: all three are exact duplicates of live declarations elsewhere —
  `LOCAL_FINDING_SKIP_CONFIDENCE` in `PassiveAiScannerFilters.kt:22` (used at :55) and both
  `*_MAX_CHARS` in `PassiveAiScanner.kt:420-421` (used at :276-277). They are leftovers of the file
  split. A comment now records the coupling between the file's three `confidence = 90` literals and
  the live threshold, so the non-obvious information survives the deletion.
- **`ChatPanel.cancelCurrentRequest` was safe to delete and `cancelInFlightRequest` was not.** The
  plan named this as the hazard. The grep decided it: `cancelCurrentRequest` has exactly one
  reference — its own declaration, whose body just delegates — while `cancelInFlightRequest` has 16
  across main and test, including `MainTab.kt:362`.
- **SC2 was measured against the post-Phase-25 tree, not the milestone baseline.** Rationale recorded
  in `26-COVERAGE.md`: the 2026-08-05 figure (34% line / 23% branch) is nine phases stale, and gating
  Phase 26 against it would credit this phase with everything Phases 20-25 earned.

## Deviations from Plan

### Auto-fixed Issues

**None.** No bug, missing-critical gap or blocking issue was discovered that required a Rule 1-3
auto-fix. Every change in this plan is planned work.

### Acceptance criteria not met as literally written

**1. Task 1: `grep -c 'private fun default' AgentSettings.kt` returns 16, not 0.**

The criterion rests on a wrong premise. It assumed the file's `private fun default*` declarations
*are* the fourteen `FunctionOnlyReturningConstant` findings. There are actually **30**; only 14 are
flagged. **The category itself is fully cleared** — 14 of 14 entries removed, detekt reports 0
findings — but 16 unflagged `default*` functions legitimately remain:

| remaining | count | why it is not a constant |
|---|---|---|
| `defaultOllamaTimeoutSeconds` and four siblings | 5 | return `Defaults.CLI_PROCESS_TIMEOUT_SECONDS`, a reference rather than a literal — detekt does not flag them |
| the prompt functions (`defaultRequestPrompt`, `defaultIssuePrompt`, …) | 9 | return `"""…""".trim()`, not a compile-time constant |
| `defaultBountyPromptDir()` | 1 | reads `System.getProperty("user.home")` at call time; a `val` would freeze it at class init |
| `defaultMcpSettings()` | 1 | **calls `McpSettings.generateToken()` and `generatePassword()` on every invocation.** Converting it to a `val` would generate one token and one password per class load and share them for the process lifetime — a real security regression |

Forcing the grep to 0 would have meant either that regression or renaming functions purely to dodge a
pattern match. Neither is a fix. Recorded rather than gamed.

**2. Task 2: baseline at 1053 after the task, not "at most 1048".** The four skips above account for
the gap. The phase gate — ≤ 1045 — is what actually matters and is met at **1040**.

## Issues Encountered

Two conversions failed on first attempt and were resolved by reverting rather than by loosening a
gate. Both are documented above with their observed output: the ktlint `property-naming` collision on
`DesignTokens.Spacing` (skip 1) and the signature-keyed `CyclomaticComplexMethod` entry invalidated
by removing `truePayload` (skip 2). In both cases the alternative was adding a baseline entry, which
this plan exists to prevent.

`RedactionTest`'s recorded wall-clock flake did not fire — the suite ran green on every full
invocation.

## Threat Flags

None. No new network endpoint, auth path, file-access pattern or trust-boundary schema change was
introduced. Two threat-register items are worth an explicit note:

- **T-26-07-05 (`UseRequire` on a path-containment guard) discharged.** `resolveReportPath`'s two
  guards were converted with the exception type and message preserved, and 26-02's assertions were
  re-run separately and immediately afterwards: `McpToolHelpersTest$ResolveReportPath` — **7 tests, 0
  failures**.
- **T-26-07-07 (`ImplicitDefaultLocale`) accepted, as planned.** Pinning `Locale.ROOT` on three
  `String.format` calls changes output under a Turkish or Arabic-digit locale. That is the defect the
  rule names, and it is a deliberate behaviour change — not a cosmetic edit.

## Known Stubs

None. No stub, placeholder, `TODO` or `FIXME` was introduced. The four skipped baseline entries are
not stubs — they are pre-existing findings left correctly baselined, each with a recorded reason and
a named follow-up.

## Verification

| check | result |
|---|---|
| `grep -c '<ID>' detekt-baseline.xml` | **1040** (gate ≤ 1045) |
| fixed-finding removals | **45** (floor ≥ 40); stale removals **11**; total **56** |
| `git log --oneline ab567fb..HEAD -- detekt-baseline.xml` | **empty** |
| `git diff -U0 ab567fb..HEAD -- detekt-baseline.xml \| grep -c '^+.*<ID>'` | **0** |
| `git diff -U0 ab567fb..HEAD -- detekt-baseline.xml \| grep -c '^-.*<ID>'` | **56** |
| `git diff --quiet 4f0ebd7..HEAD -- detekt.yml` | **exit 0** (byte-identical) |
| `git diff --quiet 3534219..HEAD -- build.gradle.kts` | **exit 0** |
| new `@Suppress` in `src/` | **0** (cap was 2) |
| `./gradlew detekt` | green, **0 findings** |
| `./gradlew ktlintCheck` | green |
| `./gradlew test` | **1131 tests / 158 classes / 0 failures / 1 skip** |
| `./gradlew test --tests '*McpToolHelpersTest'` | green; `ResolveReportPath` 7/7 |
| `./gradlew build` | green; `build/libs/Custom-AI-Agent-full-0.9.2.jar` produced |
| project line coverage | **58.14%** (floor 57.0%) |
| project branch coverage | **35.75%** (floor 34.8%) |
| floors re-verified on the merged tree | **14 / 14 MET** |
| `README.md`, `STATE.md`, `ROADMAP.md` | **untouched** |

## User Setup Required

None — no external service configuration required.

## Next Phase Readiness

- **`MagicNumber` is now 650 of the 1040 remaining entries — 62.5%.** It is the single largest
  available win and is deliberately untouched, since the bulk are inline layout literals in `ui/`
  whose fix is a `DesignTokens` refactor with real regression surface.
- **The `DesignTokens.Spacing` `const val` / SCREAMING_SNAKE_CASE rename is a natural companion to
  that work** — same files, same 222 references, and it recovers 7 more baseline entries.
- **Three small, bounded follow-ups** are fully diagnosed above and need only files outside this
  plan's scope: `ContextCollector.api` (3 files), `OpenAiCompatibleBackend.modelSelector` (3 files),
  and `ResponseAnalyzer.truePayload` (needs a complexity refactor first).
- **`McpHelpPanel` is entirely unreferenced** — the whole class, not just the parameter this plan
  removed. Worth a deletion decision.
- **Blocker for the phase verifier:** SC6's GitBook clause is **OPEN**. It must not be recorded as met
  until 26-05's handoff diff lands in `~/Tools/burp-ai-agent-doc` and is verified there.

---
*Phase: 26-coverage-static-analysis-debt-docs*
*Completed: 2026-08-22*

## Self-Check: PASSED

All claimed artifacts verified on disk and all five commits verified in `git log`:

- `26-07-SUMMARY.md`, `26-COVERAGE.md`, `detekt-baseline.xml`, the moved
  `mcp/tools/AiGateMcpToolTest.kt` and `build/libs/Custom-AI-Agent-full-0.9.2.jar` all present.
- The old `mcp/AiGateMcpToolTest.kt` path is gone — the `git mv` completed as a rename, not a
  delete-plus-add.
- Commits `6014f60`, `bbd013e`, `a3f4b74`, `d19a9c6`, `1a50968` all present on
  `worktree-agent-a7256b610cd8cc13f`.
- `git status --porcelain` is empty — nothing uncommitted.
