---
phase: 23-edt-confinement-ui-responsiveness
plan: 08
subsystem: ui
tags: [swing, edt, supersede, lifecycle, mcp, scanner, ci, kotlin, github-actions]

# Dependency graph
requires:
  - phase: 23-edt-confinement-ui-responsiveness
    provides: "The off-EDT Settings save body and its busy seam (23-03)"
  - phase: 23-edt-confinement-ui-responsiveness
    provides: "SettingsPersistQueue and MainTab.shutdown()'s dispose-first ordering (23-06)"
  - phase: 23-edt-confinement-ui-responsiveness
    provides: "OffEdtDispatchFailurePathTest and the D-23-07-1 residual text (23-07)"
provides:
  - "A supersede seam for the burp-ai-settings-save worker: SettingsPanel.saveGeneration + @Volatile disposed, set FIRST in shutdown() without lock or wait"
  - "Three isCurrent() guards in applyAndSaveSettingsBody, one immediately before each externally visible mutation that can outlive the panel (CR-01)"
  - "if (disposed) return at the head of applyAndSaveSettingsAsync — a post-unload save is refused before the busy seam is raised"
  - "edtGuardWithoutAssertionsTest wired into build.yml's three-OS pr-gate and into nightly-regression.yml (WR-11)"
  - "Five new SettingsSaveAsyncTest scenarios, every never() preceded by an asserted settle await and paired with a positive control"
  - "deferred-items.md completed with D-23-07-1 — three entries, one owner per wave"
affects: [phase-24, phase-26-quality]

actuals:
  tokens: 7639
  tasks: 3
  commits: 4

tech-stack:
  added: []
  patterns:
    - "A supersede stated at its true strength in KDoc — bounds which mutations may BEGIN, never claimed to stop one already in progress"
    - "Sequential early-return guards need one test SHAPE per guard: a supersede landing before the first guard short-circuits the rest, so a single shape leaves every later guard unfalsifiable"
    - "A ReturnCount finding answered by an inline @Suppress whose reason lives in the KDoc — ktlint rejects an EOL comment between a KDoc and its annotation"

key-files:
  created: []
  modified:
    - src/main/kotlin/com/six2dez/burp/aiagent/ui/SettingsPanel.kt
    - src/main/kotlin/com/six2dez/burp/aiagent/ui/SettingsPanelActions.kt
    - src/main/kotlin/com/six2dez/burp/aiagent/ui/SettingsPanelSettingsIO.kt
    - src/test/kotlin/com/six2dez/burp/aiagent/ui/SettingsSaveAsyncTest.kt
    - .github/workflows/build.yml
    - .github/workflows/nightly-regression.yml
    - build.gradle.kts
    - .planning/phases/23-edt-confinement-ui-responsiveness/deferred-items.md

key-decisions:
  - "Task 2's scanner test ships as TWO shapes, not the one the plan specified: the three guards are sequential early returns, so a supersede landing before the passive guard short-circuits the active one and a probe deleting the active guard could never go red. Measured, not reasoned — the two-shape form made probes 2-1 and 2-2 red on different scanners at different lines"
  - "Task 1's worker is parked in supervisor.applySettings rather than backends.reload(): it is the statement immediately preceding guard 1 and newFixture() already exposes it, while backends is not exposed at all"
  - "detekt ReturnCount (3 returns vs a limit of 2) answered with an inline @Suppress whose reason sits in the KDoc — the three returns ARE the fix, and detekt-baseline.xml stays byte-identical at 1096"
  - "Red probe 3 of Task 1 recorded as a MEASUREMENT, not a confirmation: removing saveGeneration.incrementAndGet() from shutdown() leaves the test GREEN, because isCurrent is a conjunction and disposed = true alone already falsifies it. The counter is redundant for the unload case and kept for the save-supersedes-save case D-10 currently makes unreachable"
  - "WR-11 belongs to Phase 23, checked against QUAL-07's text rather than assumed — see the ownership paragraph below"
  - "release.yml deliberately untouched"

patterns-established:
  - "One test shape per sequential guard, so each guard is independently falsifiable"
  - "A supersede's KDoc names the window it does NOT close, because overclaiming makes the next reader stop looking"

requirements-completed: [REL-05]

coverage:
  - id: D1
    description: "A save in flight at unload never reaches mcpSupervisor.applySettings, so no MCP listener is left on 127.0.0.1 owned by an unloaded extension's classloader (CR-01, T-23-08-01)"
    requirement: "REL-05"
    verification:
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/ui/SettingsSaveAsyncTest.kt#aSaveSupersededByShutdownNeverReachesTheMcpSupervisor"
        status: pass
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/ui/SettingsSaveAsyncTest.kt#aSaveThatIsNotSupersededDoesReachTheMcpSupervisor"
        status: pass
    human_judgment: false
  - id: D2
    description: "A superseded save re-enables neither AI scanner, with each of the two guards falsifiable on its own (CR-01, T-23-08-02)"
    requirement: "REL-05"
    verification:
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/ui/SettingsSaveAsyncTest.kt#aSaveSupersededByShutdownNeverReEnablesTheScanners"
        status: pass
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/ui/SettingsSaveAsyncTest.kt#aSaveThatIsNotSupersededDoesEnableTheScanners"
        status: pass
    human_judgment: false
  - id: D3
    description: "A save submitted after unload is refused without raising a busy seam nobody can lower (T-23-08-04)"
    requirement: "REL-05"
    verification:
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/ui/SettingsSaveAsyncTest.kt#aSaveSubmittedAfterShutdownIsRefusedWithoutRaisingTheBusySeam"
        status: pass
    human_judgment: false
  - id: D4
    description: "SettingsPanel.shutdown() stays non-blocking — no lock, no join, no await, no get( in its body (T-23-08-03, D-08)"
    requirement: "REL-05"
    verification:
      - kind: other
        ref: "awk '/^fun SettingsPanel.shutdown/,/^}/' SettingsPanelActions.kt | grep -cE 'withLock|lock\\(|\\.join\\(|await|get\\(' => 0"
        status: pass
    human_judgment: false
  - id: D5
    description: "edtGuardWithoutAssertionsTest is invoked by a named pr-gate step and by nightly regression, and the task itself passes with assertions disabled (WR-11, T-23-08-05)"
    requirement: "REL-05"
    verification:
      - kind: other
        ref: "./gradlew edtGuardWithoutAssertionsTest => 3 tests, 0 failures"
        status: pass
      - kind: other
        ref: "python3 yaml.safe_load(build.yml) => pr-gate 9 steps (was 8), lint 5; step index fast(3) < edtGuard(4) < shadowJar(5)"
        status: pass
    human_judgment: false
  - id: D6
    description: "The two wired workflows actually execute green on GitHub's ubuntu/macos/windows matrix"
    verification: []
    human_judgment: true
    rationale: "Only observable on a real GitHub Actions run. Locally the task is proven to pass and the YAML is proven structurally valid, but a runner-specific failure (e.g. a Windows path difference in the -da JVM args) cannot be reproduced here. Confirm on the first PR after this phase merges."
  - id: D7
    description: "deferred-items.md carries three entries with one owner per wave, D-23-07-1 quoting its UI question verbatim"
    verification:
      - kind: other
        ref: "grep -c '^## D-23-0' deferred-items.md => 3; D-23-04-1 and D-23-06-1 unmodified in the diff"
        status: pass
    human_judgment: false
  - id: D8
    description: "The JOptionPane save-failure modal appears and carries the exception message"
    verification: []
    human_judgment: true
    rationale: "JOptionPane.getRootFrame() throws HeadlessException — measurably not headless-testable. Byte-unchanged by this plan; routed to 23-HUMAN-UAT.md as a backstop (D-12, UI-SPEC Rule C-2)."

# Metrics
duration: 37 min
completed: 2026-08-21
status: complete
---

# Phase 23 Plan 08: CR-01 Supersede + WR-11 CI Gate Summary

**The Settings save worker now loses a race it used to win: `SettingsPanel.shutdown()` supersedes it in two non-blocking statements, three `isCurrent()` guards stop it reaching the MCP supervisor or either AI scanner after `App.shutdown()` tore them down, and `edtGuardWithoutAssertionsTest` finally runs in CI on all three platforms.**

## Performance

- **Duration:** ~37 min
- **Started:** 2026-08-21T09:40Z (approx — first task commit at 09:59:31Z)
- **Completed:** 2026-08-21T10:17:08Z
- **Tasks:** 3
- **Files modified:** 8

## Accomplishments

- **CR-01 closed at all three externally visible mutations.** `mcpSupervisor.applySettings`, `passiveAiScanner.setEnabled` and `activeAiScanner.setEnabled` each sit behind an `isCurrent()` re-check, so a supersede landing mid-body cannot resurrect a stopped MCP listener or re-arm a scanner with nothing behind it.
- **Unload pays nothing for it.** `SettingsPanel.shutdown()` gained a `@Volatile` write and an `AtomicLong` increment, placed first, taking no lock and waiting on nothing. Asserted structurally: no `withLock`, `lock(`, `.join(`, `await` or `get(` anywhere in its body (D-08, T-23-08-03).
- **A save submitted *after* unload is refused outright** at `if (disposed) return`, above the seam raise, so there is no busy seam left raised for nobody to lower (T-23-08-04).
- **WR-11 gated on two workflows.** Reverting `check(...)` to `assert(...)` in `McpToolExecutorImpl` now turns a pull request red on ubuntu, macOS and Windows, and again in nightly regression, instead of passing every automated gate.
- **Seven red probes executed against committed baselines**, every one restored to a 0-byte diff. Two of them (2-1 and 2-2) only became falsifiable because the plan's specified single-shape test was rejected and rebuilt — see Deviations.
- **`deferred-items.md` completed** with `D-23-07-1`, transcribed verbatim from `23-07-SUMMARY.md` including its open UI question.

## Task Commits

1. **Task 1 (tracer): supersede the settings save worker at unload** — `84f4c83` (feat)
2. **Task 2: guard both scanner setEnabled calls and refuse a post-unload save** — `915b6b3` (feat)
3. **Task 3: gate SC1's -da guarantee in CI and transcribe D-23-07-1** — `83ef16b` (chore)

**Plan metadata:** see the `docs(23-08)` commit that carries this file.

## Files Created/Modified

- `src/main/kotlin/.../ui/SettingsPanel.kt` — `saveGeneration: AtomicLong` + `@Volatile disposed`, KDoc'd together with the guarantee stated at its true strength and the window it does *not* close named explicitly.
- `src/main/kotlin/.../ui/SettingsPanelActions.kt` — `shutdown()` gains two statements at the top; the four timer statements are byte-identical.
- `src/main/kotlin/.../ui/SettingsPanelSettingsIO.kt` — `applyAndSaveSettingsBody(updated, isCurrent: () -> Boolean = { true })` with three guards; `applyAndSaveSettingsAsync` gains `if (disposed) return` and mints the generation on the calling thread; `@Suppress("ReturnCount")` with its reason in the KDoc.
- `src/test/kotlin/.../ui/SettingsSaveAsyncTest.kt` — five new scenarios (10 → 15); `Fixture` now exposes `activeAiScanner`.
- `.github/workflows/build.yml` — one `pr-gate` step, between the fast suite and shadowJar.
- `.github/workflows/nightly-regression.yml` — one task added to the existing run line.
- `build.gradle.kts` — comment lines only; the stale duplication justification corrected.
- `.planning/phases/23-edt-confinement-ui-responsiveness/deferred-items.md` — `D-23-07-1` appended.

## Red-Probe Ledger

Seven probes, each reversing exactly ONE production statement against a **committed** baseline, never a test edit and never `git stash`.

| # | Task | File | Statement reversed | Failing test | Line | Outcome | Restore |
|---|---|---|---|---|---|---|---|
| 1-1 | 1 | `SettingsPanelSettingsIO.kt` | deleted `if (!isCurrent()) return` above `mcpSupervisor.applySettings(` | `aSaveSupersededByShutdownNeverReachesTheMcpSupervisor` | `SettingsSaveAsyncTest.kt:590` | **RED** — `NeverWantedButInvoked: mcpSupervisor.applySettings(...)` | 0 bytes |
| 1-2 | 1 | `SettingsPanelSettingsIO.kt` | guard → unconditional `return` (the mutation never happens at all) | `aSaveThatIsNotSupersededDoesReachTheMcpSupervisor` | `:618` | **RED** — `Wanted but not invoked`. Two pre-existing tests failed as expected collateral, confirming the statement really was disabled | 0 bytes |
| 1-3 | 1 | `SettingsPanelActions.kt` | removed `saveGeneration.incrementAndGet()`, kept `disposed = true` | `aSaveSupersededByShutdownNeverReachesTheMcpSupervisor` | — | **STILL GREEN — a measurement, not a failure.** See below | 0 bytes |
| 2-1 | 2 | `SettingsPanelSettingsIO.kt` | deleted the guard above `passiveAiScanner.setEnabled(` | `aSaveSupersededByShutdownNeverReEnablesTheScanners` | `:666` | **RED** — failure names `passiveAiScanner.setEnabled`, unambiguously | 0 bytes |
| 2-2 | 2 | `SettingsPanelSettingsIO.kt` | deleted the guard above `activeAiScanner.setEnabled(` **only**, passive kept | `aSaveSupersededByShutdownNeverReEnablesTheScanners` | `:691` | **RED** — failure names `activeAiScanner.setEnabled`. A *different* line and a *different* mock from 2-1, which is what proves the two guards are asserted independently | 0 bytes |
| 2-3 | 2 | `SettingsPanelSettingsIO.kt` | deleted `if (disposed) return` | `aSaveSubmittedAfterShutdownIsRefusedWithoutRaisingTheBusySeam` | `:751` | **RED** on the busy-values clause — `expected: <[]> but was: <[true]>` | 0 bytes |
| 2-4 | 2 | `SettingsPanelSettingsIO.kt` | `isCurrent = { false }` | `aSaveThatIsNotSupersededDoesEnableTheScanners` (`:714`) **and** `aSaveThatIsNotSupersededDoesReachTheMcpSupervisor` (`:618`) | both | **RED** — both positive controls discriminate | 0 bytes |

Task 3 carries no probe: it changes no production Kotlin. Its equivalents are the CI-workflow and ledger criteria, all verified below.

### What probe 1-3 measured

The plan required this probe's outcome to be *recorded*, not confirmed. The result: removing `saveGeneration.incrementAndGet()` from `shutdown()` leaves `aSaveSupersededByShutdownNeverReachesTheMcpSupervisor` **green**.

That is correct and expected once stated plainly. `isCurrent` is the conjunction `saveGeneration.get() == generation && !disposed`, so `disposed = true` alone already falsifies it. **For the unload case the two mechanisms are redundant, not complementary.** The `AtomicLong` earns its keep only for a *second save superseding a first while the panel is still alive* — which D-10's disabled Save and Restore buttons make unreachable today. It is kept because CR-01's own proposed fix names it, and because it is the half that survives if D-10's button-disabling is ever relaxed. Recording this is the honest alternative to letting a future reader assume the counter is load-bearing at unload; it is not.

## WR-11 Ownership Check (the deliverable, performed rather than assumed)

The dispatch flagged WR-11 as possibly Phase 26 / QUAL-07 territory. Both texts, read now:

> **QUAL-07** (`REQUIREMENTS.md:35`, Findings 16 + 17, Phase 26): *"The detekt baseline shrinks rather than grows — no finding from this milestone is added to it. `assert()`-based EDT enforcement, which is a no-op in production Burp, is either upgraded to something that reports in the field or explicitly documented as test-only. `SecretCipher`'s at-rest guarantee is described accurately in user-facing docs."*

> **WR-11** (`23-REVIEW.md:604-617`): *"`edtGuardWithoutAssertionsTest` … is registered, documented at length, and invoked by nothing … Reverting `check(...)` to `assert(...)` in `McpToolExecutorImpl.kt:161` would leave every automated gate passing. Fix: add it to the release workflow, or at minimum to `nightly-regression.yml`."*

**Conclusion: Phase 23 owns WR-11.** QUAL-07's three clauses are the detekt baseline's direction of travel, the *disposition* of `assert()`-based EDT enforcement, and `SecretCipher` documentation. None of them is CI wiring of a Gradle task. The nearest-looking clause is the second, and it is genuinely adjacent — but its subject is the `assert()` that is still in `ChatPanel` and still Phase 26's to upgrade or document, whereas WR-11's subject is the `check(...)` that plan **23-02 already shipped inside this phase**, together with the `edtGuardWithoutAssertionsTest` task that proves it. Wiring a task this phase created, to gate a guarantee this phase's SC1 claims, is this phase's work; leaving it for Phase 26 would ship SC1 verified-but-ungated for a whole milestone. The wiring was therefore performed. Had the comparison come out the other way, the plan explicitly permitted recording that and leaving WR-11 alone — it did not.

**Why `release.yml` was not modified:** release runs strictly after a pull request has merged, so it is later than the pr-gate step and adds no coverage the pr-gate and nightly gates do not already give.

## Decisions Made

See `key-decisions` in the frontmatter. The load-bearing one: the plan's single-shape scanner test was rejected in favour of two shapes, because sequential early-return guards cannot be isolated by a supersede that lands before the first of them.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] The plan's specified scanner test would have left the active guard unfalsifiable**

- **Found during:** Task 2, while designing `aSaveSupersededByShutdownNeverReEnablesTheScanners`.
- **Issue:** The plan specified one test whose supersede lands mid-body via the `backends.reload()` latch, then asserts `never()` on both scanners — and two red probes (2-1 and 2-2) each deleting one scanner guard. That is unsatisfiable by construction. The three guards are **sequential early returns**: a supersede landing before guard 1 makes the body return there, so deleting guard 2 or guard 3 changes nothing observable and both probes would come back GREEN. This is the same class of defect as 23-07's specified assertions — a plan-specified assertion that is true with the defect present.
- **Fix:** Split the test into two shapes inside one `@Test`, following the file's existing `theBusySeamLowersOnBothFailureShapes` idiom. Shape 1 parks the worker inside `mcpSupervisor.applySettings` (past guard 1) so the **passive** guard is the only thing that can stop it. Shape 2 parks inside `passiveAiScanner.setEnabled` (past guard 2) so the **active** guard is the only thing left — and asserts `times(1)` on the passive scanner, proving the worker really did get that far. The KDoc states why one shape would not do.
- **Files modified:** `src/test/kotlin/.../SettingsSaveAsyncTest.kt`
- **Verification:** Probes 2-1 and 2-2 now fail on **different lines** (`:666` vs `:691`) naming **different mocks** (`passiveAiScanner` vs `activeAiScanner`). Under the plan's single-shape design, 2-2 would have passed.
- **Committed in:** `915b6b3`

**2. [Rule 3 - Blocking] detekt `ReturnCount` fired at three returns**

- **Found during:** Task 2, after adding guards 2 and 3.
- **Issue:** `SettingsPanelSettingsIO.kt:526:28 — Function applyAndSaveSettingsBody has 3 return statements which exceeds the limit of 2. [ReturnCount]`. The plan anticipated `LongMethod` (which did not fire, measured) but not `ReturnCount`.
- **Fix:** Inline `@Suppress("ReturnCount")` with its reason. The first attempt placed the reason as `//` comment lines between the KDoc and the annotation, which ktlint rejected: *"an EOL comment may not be preceded by a KDoc"*. The reason moved into the KDoc as a titled paragraph, with the bare annotation below it. `detekt-baseline.xml` was never regenerated.
- **Files modified:** `src/main/kotlin/.../SettingsPanelSettingsIO.kt`
- **Verification:** `ktlintCheck detekt` exit 0; `git diff --stat detekt-baseline.xml` empty; `grep -c '<ID>'` = 1096.
- **Committed in:** `915b6b3`

**3. [Rule 3 - Blocking] `newFixture()` did not expose `activeAiScanner`**

- **Found during:** Task 2. `Fixture` carried `passiveAiScanner` but built the active scanner inline as an anonymous mock, so no test could verify against it.
- **Fix:** Hoisted the mock to a local and added it to `Fixture`, matching the passive scanner's existing shape.
- **Files modified:** `src/test/kotlin/.../SettingsSaveAsyncTest.kt`
- **Verification:** All 15 tests pass; probes 2-1/2-2/2-4 read the new field.
- **Committed in:** `915b6b3`

### Minor divergences, recorded rather than hidden

- **Parking point.** Task 1's worker is parked in `supervisor.applySettings` rather than the plan's `backends.reload()`. Both precede guard 1 and both prove the worker is inside the body; `supervisor.applySettings` is the statement *immediately* before the guard, is already the parking point three existing tests in this file use, and is exposed by `Fixture` — `backends` is not exposed at all, so following the plan literally would have required a fixture change for no gain.
- **Stale acceptance-criterion name.** Task 2's criteria name `theSaveButtonIsReEnabledAfterAFailedSave`; no test by that name exists. The suite's equivalent FLAG-23-06 scenario is `theBusySeamLowersOnBothFailureShapes`, which passes unmodified, as does every other pre-existing scenario (15/15, 0 failures).
- **Tracer feedback gate.** The plan emits no `checkpoint:*` task and its tracer `<verify>` carries no `<human-check>` — it is a Gradle command with nothing for a human to judge. The gate was therefore discharged by re-running the tracer's `<verify>` end-to-end after committing Task 1 (`SettingsSaveAsyncTest` 12/0, `SettingsPersistQueueTest` 5/0) before starting any expansion task.

---

**Total deviations:** 3 auto-fixed (1 bug, 2 blocking) + 3 recorded minor divergences.
**Impact on plan:** Deviation 1 is the substantive one and it strengthened the plan rather than working around it — without it, two of the seven mandated red probes would have come back green and the active-scanner guard would have shipped unasserted. No scope creep; `D-14`, `assertEdt()`, `detekt-baseline.xml` and `release.yml` are all untouched.

## Issues Encountered

None. `RedactionTest` did not flake in either full-suite run (115 suites, 0 with failures or errors).

## Verification Results

| # | Check | Result |
|---|---|---|
| 1 | `./gradlew ktlintCheck detekt test` | **exit 0** (2m 38s) |
| 2 | `./gradlew edtGuardWithoutAssertionsTest` | **exit 0**, `McpToolExecutorEdtGuardTest` **3** tests, 0 failures |
| 3 | `./gradlew test -PexcludeHeavyTests=true` | **exit 0**. `SettingsSaveAsyncTest` **15**/0, `SettingsPersistQueueTest` **5**/0, `ChatPanelEdtConfinementTest` **23**/0, `OffEdtDispatchFailurePathTest` **4**/0, `McpToolExecutorEdtGuardTest` **3**/0. **Control:** no result file for `ChatPanelConcurrencyTest`, so the exclusion filter demonstrably WAS applied. 108 suites, **0** with failures or errors |
| 4 | `python3 yaml.safe_load` on `build.yml`, `nightly-regression.yml`, `release.yml` | **exit 0**. `pr-gate` **9** steps (baseline 8 — one added), `lint` **5** (unchanged). Step index: fast suite **3** < edtGuard **4** < shadowJar **5** |
| 5 | `git diff --stat detekt-baseline.xml` · `grep -c '<ID>'` | empty · **1096** |
| 6 | `git diff --stat 87d4c0f -- src/main/.../mcp/` | empty — **D-14 not re-opened** |
| 7 | `git diff --stat 87d4c0f -- ChatPanel.kt` · `assertEdt` · `SwingUtilities.invokeLater` | empty · **6** (UNFILTERED, never comment-filtered) · **11** |
| 8 | `git diff 87d4c0f -- build.gradle.kts` non-comment changed lines · `withPropertyName` | **0** · **7** |
| 9 | `git status --short` | `README.md` still carries its pre-existing modification and appears in **0** commits of this plan |
| 10 | `./gradlew check --dry-run` | does **not** list `edtGuardWithoutAssertionsTest` — it stays out of the `check` lifecycle task |
| 11 | Structural counts (house block-comment filter) | `if (!isCurrent()) return` = **3**, `if (disposed) return` = **1** in `SettingsPanelSettingsIO.kt`; `edtGuardWithoutAssertionsTest` = **1** in `build.gradle.kts` (the registration line), **1** in each workflow (`#`-filtered) |
| 12 | Guard/mutation ordering (character index within the body) | guard **1020** < `mcpSupervisor.applySettings(` **1049**; guard **2946** < `passiveAiScanner.setEnabled(` **2975**; guard **4110** < `activeAiScanner.setEnabled(` **4139**. `if (disposed) return` precedes `saveGeneration.incrementAndGet()` in `applyAndSaveSettingsAsync` |
| 13 | `shutdown()` body forbidden tokens | **0** matches for `withLock\|lock(\|.join(\|await\|get(`; `disposed = true` and `saveGeneration.incrementAndGet()` are its first two statements |
| 14 | `deferred-items.md` | **3** `## D-23-0*` headings; `D-23-04-1` and `D-23-06-1` untouched (diff is 26 pure insertions); `clearChatState` present; UI question quoted verbatim |
| 15 | Seven red probes | all recorded above with failing test, failing line and a **0-byte** restore diff, verified individually |

## Known Stubs

None. No hardcoded empty values, placeholder text, `TODO`/`FIXME` markers, skipped tests or unrun `<verify>` commands were introduced.

## Threat Flags

None. All seven dispositioned threats are discharged as planned: `T-23-08-01` by Task 1, `T-23-08-02` by Task 2, `T-23-08-03` structurally (verification 13), `T-23-08-04` by Task 2, `T-23-08-05` by Task 3, and `T-23-08-06` / `T-23-08-07` remain `accept`, both documented in the shipped KDoc rather than only in the plan. `T-23-08-SC` confirmed: no dependency added, `build.gradle.kts` changed by comment only.

## User Setup Required

None — no external service configuration required.

## Next Phase Readiness

- **CR-01 is closed**, and it was the phase's only Critical review finding. The residual it does *not* close is named in the shipped KDoc and accepted as `T-23-08-07`: a mutation already past its guard runs to completion, because closing that window would require joining the worker, which D-08 rejects.
- **`23-HUMAN-UAT.md` item 5 is now STALE and must be re-worded before the phase gate runs it.** It asks the operator to time the UI freeze on **Restore defaults** with the MCP server Running. After plans 23-06 and 23-08 the expected observation inverts — there should be **no freeze**. Left as-is, the item asserts the defect. This plan does not edit `23-HUMAN-UAT.md`; whoever runs the phase gate owns the correction.
- **`FLAG-23-04` untouched** — the sub-frame `Send`↔`Cancel` flicker on the auto-approved chain path is neither closed nor worsened; this plan modified no `ChatPanel.kt`.
- **Three residuals remain open and are all in `deferred-items.md` with one owner per wave:** `D-23-04-1` / `D-23-07-1` (Clear Chat does not supersede, with its unanswered UI question) and `D-23-06-1` (`MainTab.kt:111`, the eighth EDT save site). None is fixed here by design.
- **Watch on the first PR after merge:** the new pr-gate step is the only part of this plan not observable locally (coverage `D6`). A Windows- or macOS-specific failure in the `-da` JVM args would surface there and nowhere else.
- Phase 23 has no further plans. Ready for phase verification.

## Self-Check: PASSED

- All 8 files in `key-files.modified` verified present on disk with `[ -f ]`.
- All 3 task commit hashes verified with `git log --oneline 87d4c0f..HEAD`.
- All three phase gates re-run green **after** the final production commit (verification rows 1–3).
- All seven red probes restored to a 0-byte diff, each verified individually at the moment of restore.
- Frozen invariants re-measured after the last commit: baseline 1096 / empty diff, `assertEdt` 6, `invokeLater` 11, `withPropertyName` 7, `mcp/` empty diff.

---
*Phase: 23-edt-confinement-ui-responsiveness*
*Completed: 2026-08-21*
