---
phase: 23-edt-confinement-ui-responsiveness
plan: 06
subsystem: ui
tags: [swing, edt, concurrency, reentrantlock, settings, mcp, privacy, kotlin]

# Dependency graph
requires:
  - phase: 23-edt-confinement-ui-responsiveness
    provides: "OffEdtDispatch — the single named-daemon dispatch + one-invokeLater tail seam (23-01)"
  - phase: 23-edt-confinement-ui-responsiveness
    provides: "applyAndSaveSettingsAsync / applyAndSaveSettingsBody and the T-1 busy seam (23-03)"
provides:
  - "SettingsPersistQueue — a generation-ordered, ReentrantLock-serialised off-EDT settings persist seam"
  - "All seven enumerated MainTab EDT settingsRepo.save() sites routed through that one queue"
  - "applySettingsToUi(updated, notifyHosts = true) — suppressible host notifications"
  - "restoreDefaultsConfirmed() — the restore-defaults body, headlessly drivable without deleting the modal"
  - "A MainTab-shaped blocking onMcpEnabledChanged seam in SettingsSaveAsyncTest"
  - "Residual D-23-06-1 / threat T-23-06-08 — the eighth EDT save site at MainTab.kt:111"
affects: [23-07, 23-08, phase-26-quality, mcp-supervisor-shutdown]

actuals:
  tokens: 15693
  tasks: 3
  commits: 6

tech-stack:
  added: []
  patterns:
    - "Generation-ordered persist queue: mint the generation on the CALLING thread, serialise apply bodies under one ReentrantLock, drop an older generation rather than replay it"
    - "dispose() as a @Volatile flag that never takes the worker lock — bounds NEW work only, and says so"
    - "Suppressible host notifications via a defaulted boolean parameter, confining the change to the one caller that has the problem"
    - "KDoc mention ledger + structural gate with block comments stripped, so the ledger can name the tokens it pins"

key-files:
  created:
    - src/main/kotlin/com/six2dez/burp/aiagent/ui/SettingsPersistQueue.kt
    - src/test/kotlin/com/six2dez/burp/aiagent/ui/SettingsPersistQueueTest.kt
  modified:
    - src/main/kotlin/com/six2dez/burp/aiagent/ui/MainTab.kt
    - src/main/kotlin/com/six2dez/burp/aiagent/ui/SettingsPanelActions.kt
    - src/main/kotlin/com/six2dez/burp/aiagent/ui/SettingsPanelSettingsIO.kt
    - src/test/kotlin/com/six2dez/burp/aiagent/ui/SettingsSaveAsyncTest.kt
    - build.gradle.kts
    - .planning/phases/23-edt-confinement-ui-responsiveness/deferred-items.md

key-decisions:
  - "applySettingsToUi takes a notifyHosts flag rather than firing the three callbacks from applyAndSaveSettingsAsync's EDT tail — the tail fires for BOTH callers, and on the save path that is a second disk write and a second bounded MCP stop on every save"
  - "TWO persist helpers (persistSettings / persistSettingsAndApplyMcp) rather than one flag-taking helper, because McpSupervisor.stop() clears ScannerTaskRegistry and CollaboratorRegistry — an unconditional MCP apply on a passive toggle would drop live scanner tasks"
  - "The overlap assertion is an entry-time AtomicBoolean detector, never a snapshot-field comparison: AgentSettings is a data class passed to apply by reference, so a torn-fields assertion would be false by construction and could never fail"
  - "anOlderGenerationIsDroppedRatherThanAppliedOverANewerOne is driven through OffEdtDispatch's dispatchedObserver, parking the older SUBMITTER before its worker starts — the out-of-order arrival the guard exists for is otherwise a scheduler race a test cannot force"
  - "functionBody() in SettingsSaveAsyncTest now strips comment lines — measured, not theorised: the first draft failed because a production comment named restoreDefaultsConfirmed() ahead of the dialog"
  - "applySettingsToUi's re-fired LongMethod answered with an inline @Suppress carrying its reason; detekt-baseline.xml keys on the full signature, so adding a parameter orphans the entry"
  - "MainTab.kt:111 deliberately NOT routed through the queue — the send path depends on supervisor.applySettings completing before the turn is sent. Recorded as D-23-06-1 / T-23-06-08 (high, accept)"

patterns-established:
  - "Red probe as a first-class acceptance criterion: nine probes, each reversing ONE production statement, each recorded with failing test name and line number, each followed by an empty git diff"
  - "Structural gates strip BLOCK comments, not only //, so the ledger documenting a count may name the tokens it counts"

requirements-completed: [REL-05]

coverage:
  - id: D1
    description: "Unchecking 'Enable MCP server' in the Settings tab, flipping the header mcpToggle, and Restore defaults with the server Running all return control to the EDT without paying KtorMcpServerManager.stop()'s bounded 10s wait"
    requirement: "REL-05"
    verification:
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/ui/SettingsPersistQueueTest.kt#theSubmittingThreadReturnsWhileTheApplyIsStillBlocked"
        status: pass
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/ui/SettingsSaveAsyncTest.kt#restoreDefaultsDoesNotFireTheHostNotificationsOnTheEdt"
        status: pass
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/ui/SettingsPersistQueueTest.kt#everyMainTabSettingsWriteGoesThroughThePersistQueue"
        status: pass
    human_judgment: false
  - id: D2
    description: "CR-02's torn-snapshot half is closed for every write submitted through the queue: two apply bodies are mutually excluded by one ReentrantLock and run in submission order (threats T-23-06-01 / T-23-06-02, both high)"
    requirement: "REL-05"
    verification:
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/ui/SettingsPersistQueueTest.kt#twoApplyBodiesNeverOverlapAndRunInSubmissionOrder"
        status: pass
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/ui/SettingsPersistQueueTest.kt#anOlderGenerationIsDroppedRatherThanAppliedOverANewerOne"
        status: pass
    human_judgment: false
  - id: D3
    description: "applySettingsToUi's component writes still run on the EDT before dispatch; only its three host notifications are suppressed, and only at the restore-defaults call site"
    requirement: "REL-05"
    verification:
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/ui/SettingsSaveAsyncTest.kt#restoreDefaultsStillWritesTheComponentsBeforeDispatch"
        status: pass
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/ui/SettingsSaveAsyncTest.kt#applySettingsToUiStillNotifiesHostsByDefault"
        status: pass
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/ui/SettingsSaveAsyncTest.kt#restoreDefaultsConfirmsBeforeDispatchAndReportsFromTheCallback"
        status: pass
    human_judgment: false
  - id: D4
    description: "MainTab.shutdown() disposes the persist queue so no settings write submitted before unload can start after App.shutdown()'s mcpSupervisor.shutdown(); dispose() never takes the worker lock"
    requirement: "REL-05"
    verification:
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/ui/SettingsPersistQueueTest.kt#disposeStopsNewWorkAndDoesNotBlockTheCaller"
        status: pass
    human_judgment: false
  - id: D5
    description: "The live-Burp SC4 scenario: with MCP enabled and the server Running, Restore defaults / the Settings-tab checkbox / the header toggle no longer freeze the Burp UI for up to ten seconds"
    verification: []
    human_judgment: true
    rationale: "Requires a live Burp with a running Ktor MCP server to pay the real future.get(10, TimeUnit.SECONDS). The headless suite proves the persist body left the EDT and that no host notification fires on the restore path, but the observed freeze is only measurable in the shipped UI. Routed to 23-HUMAN-UAT.md."
  - id: D6
    description: "The JOptionPane save/restore-failure modal appears alongside the inline banner and carries the exception message"
    verification: []
    human_judgment: true
    rationale: "JOptionPane.getRootFrame() throws HeadlessException under -Djava.awt.headless=true, which tasks.test sets. Measurably not headlessly testable; unchanged by this plan and already tracked as 23-HUMAN-UAT.md item 1."

duration: 28min
completed: 2026-08-21
status: complete
---

# Phase 23 Plan 06: SC4 Gap Closure — Off-EDT Settings Persist Queue Summary

**A generation-ordered, lock-serialised `SettingsPersistQueue` that takes all seven enumerated `MainTab` EDT `settingsRepo.save()` sites off the AWT thread, plus a `notifyHosts` flag that stops `Restore defaults` firing three host callbacks into a bounded ten-second MCP stop on the EDT — closing both halves of CR-02 and all four `missing:` items in `23-VERIFICATION.md`.**

## Performance

- **Duration:** 28 min
- **Started:** 2026-08-21T08:44:01Z
- **Completed:** 2026-08-21T09:12:00Z
- **Tasks:** 3
- **Files modified:** 8 (2 created, 6 modified)

## Accomplishments

- **SC4's named scenario is closed at every remaining door.** Unchecking "Enable MCP server" in the Settings tab, flipping the header `mcpToggle`, and `Restore defaults` with the server Running all return control to the EDT immediately. `KtorMcpServerManager.stop()`'s `future.get(10, TimeUnit.SECONDS)` is now paid by `burp-ai-settings-sync`, not by the Burp UI. **D-14 was not re-opened** — `stop()` is byte-identical and no file under `src/main/.../mcp/` was touched; only callers moved.
- **CR-02's torn-snapshot half is closed for queue-submitted writes.** `SettingsPersistQueue.applyIfCurrent` runs each apply body to completion under one `ReentrantLock`, so two writes cannot interleave inside `AgentSettingsRepository.save()`'s ~107 sequential preference-key writes and pair a permissive `privacyMode` with a foreign `customRedactionPatterns` list. That pairing is the sharper of the two defects on a tool whose core value is that privacy controls are non-negotiable (threats `T-23-06-01` / `T-23-06-02`, both `high`).
- **All four `23-VERIFICATION.md` `missing:` items discharged**, each traceable to a task and to at least one assertion driven red — see the traceability table below.
- **Nine red probes executed and recorded**, including the lock-removal probe that carries both `high` threats. It came back RED on its `overlapSeen` clause, so the queue test is not vacuous.
- **The assertion that certified the defect is gone.** `SettingsSaveAsyncTest.kt:417`'s `"Rule T-3: applySettingsToUi writes Swing, so it stays on the EDT."` — which treated a call reaching disk I/O and a bounded ten-second server stop as "component writes" — is replaced by a claim that is true, plus a positive clause on `notifyHosts = false`. `grep -c 'so it stays on the EDT'` returns 0.

## Task Commits

1. **Task 1 (tracer): the Settings-tab MCP checkbox off the EDT** — `f4eae93` (feat)
2. **Task 2: the remaining six MainTab EDT save sites** — `2f6d1bc` (feat)
3. **Task 3 (TDD): suppress the restore-defaults host notifications**
   - `8ca0194` (refactor) — the extraction + `notifyHosts` parameter seam, behaviour unchanged
   - `97458da` (test) — RED: 10 tests, 2 failing
   - `ed5d2a6` (feat) — GREEN: `notifyHosts = false` at the one call site
   - `02c0b06` (chore) — inline `@Suppress("LongMethod")` with its reason

## Red-Probe Ledger

Nine probes. Each reversed exactly ONE production statement (never `git stash`, never an edit to a test), ran the target suite, and was restored to a byte-identical file. "Diff empty" was measured against the index/HEAD, so it is a real claim for the two new files too.

| # | Task | File | Statement reversed | Failing test | Line | Restored diff empty |
|---|---|---|---|---|---|---|
| 1 | 1 | `SettingsPersistQueue.kt` | Dispatch offload removed — `applyIfCurrent` evaluated on the calling thread instead of in the `work` lambda | `theSubmittingThreadReturnsWhileTheApplyIsStillBlocked` (failed on the `edtWasFree` clause, the intended one) | `:107` | ✓ |
| 2 | 1 | `SettingsPersistQueue.kt` | `if (generation <= applied.get()) return` deleted | `anOlderGenerationIsDroppedRatherThanAppliedOverANewerOne` | `:226` | ✓ |
| **3** | 1 | `SettingsPersistQueue.kt` | **`lock.withLock` removed from `applyIfCurrent`, body otherwise identical** | **`twoApplyBodiesNeverOverlapAndRunInSubmissionOrder` — failed on its `overlapSeen` clause** | **`:168`** | ✓ |
| 4 | 1 | `SettingsPersistQueue.kt` | `dispose()` body emptied (`disposed = true` deleted) | `disposeStopsNewWorkAndDoesNotBlockTheCaller` — failed on the **"a subsequent submit never reaches apply"** clause, NOT the non-blocking clause, which is what proves the flag is read | `:285` | ✓ |
| 5 | 2 | `MainTab.kt` | `passiveToggle`'s `persistSettings(...)` reverted to an inline `settingsRepo.save(settingsPanel.currentSettings())` | `everyMainTabSettingsWriteGoesThroughThePersistQueue` — message named the moved count (`persistSettings(` 6 → 5) | `:312` | ✓ |
| 6 | 3 | `SettingsPanelActions.kt` | `applySettingsToUi(defaults, notifyHosts = false)` → `applySettingsToUi(defaults)` | `restoreDefaultsDoesNotFireTheHostNotificationsOnTheEdt` — failed on the **invocation-count** assertion (count read 1), not on the `assertTimeoutPreemptively` deadlock | `:459` | ✓ |
| 7 | 3 | `SettingsPanelSettingsIO.kt` | The three `onXxxChanged?.invoke(...)` statements deleted outright | `applySettingsToUiStillNotifiesHostsByDefault` — the negative control is not vacuous | `:489` | ✓ |
| 8 | 3 | `SettingsPanelActions.kt` | `notifyHosts = false` → `notifyHosts = true` (a production-source reversal) | `restoreDefaultsConfirmsBeforeDispatchAndReportsFromTheCallback` — failed on its new fourth clause | `:425` | ✓ |
| 9 | 3 | `SettingsPanelSettingsIO.kt` | `privacyMode.selectedItem = updated.privacyMode` deleted | `restoreDefaultsStillWritesTheComponentsBeforeDispatch` | `:535` | ✓ |

**Probe 3 is the one that mattered.** Had it come back green, the overlap test would have been measuring snapshot identity rather than mutual exclusion — the precise vacuity this phase has shipped five times — and Task 1 would not have been done.

Probes 6 and 8 were also demonstrated **by construction**: the RED commit `97458da` was made against production source that still read `applySettingsToUi(defaults)`, and the suite reported `10 tests completed, 2 failed` at exactly those two clauses before `ed5d2a6` turned them green.

## Gap Traceability — `23-VERIFICATION.md` `missing:` items

| # | Item | Discharged by | Red evidence |
|---|---|---|---|
| 1 | Move the three host notifications out of the EDT-confined `applySettingsToUi` body | Task 3 — `notifyHosts: Boolean = true` guard + `applySettingsToUi(defaults, notifyHosts = false)` at `restoreDefaultsConfirmed` | Probes 6, 7, 8, 9 |
| 2 | Route `MainTab`'s own `mcpSupervisor.applySettings(...)` calls off the EDT | Tasks 1 + 2 — `persistSettingsAndApplyMcp` for `:453`/`:487`, `persistSettings` for the other five | Probes 1, 5 |
| 3 | Extend the fixture with a MainTab-shaped `onMcpEnabledChanged` that blocks | Task 3 — `Fixture.installBlockingMcpCallback()` / `BlockingHostCallback` | Probe 6 (the seam is what makes it observable) |
| 4 | Correct `SettingsSaveAsyncTest.kt:417`'s assertion message and intent | Task 3 — message rewritten to name the COMPONENT writes and `notifyHosts`; fourth and fifth clauses added | Probe 8 |

## Files Created/Modified

- `src/main/kotlin/com/six2dez/burp/aiagent/ui/SettingsPersistQueue.kt` (**new**, 120 lines) — the persist seam: `submit` / `dispose` / private `applyIfCurrent`, `AtomicLong` generations, one `ReentrantLock`, one `OffEdtDispatch.run`.
- `src/test/kotlin/com/six2dez/burp/aiagent/ui/SettingsPersistQueueTest.kt` (**new**, 388 lines) — 5 tests: 4 behavioural + the `MainTab` structural ledger gate.
- `src/main/kotlin/com/six2dez/burp/aiagent/ui/MainTab.kt` — `settingsPersistQueue` field, two persist helpers with the mention ledger, seven rewired listeners, header-toggle sync in `onSettingsChanged`, `dispose()` first in `shutdown()`.
- `src/main/kotlin/com/six2dez/burp/aiagent/ui/SettingsPanelSettingsIO.kt` — `applySettingsToUi(updated, notifyHosts = true)`, guard around the three host invocations, KDoc rationale, inline `@Suppress("LongMethod")`.
- `src/main/kotlin/com/six2dez/burp/aiagent/ui/SettingsPanelActions.kt` — `restoreDefaultsWithConfirmation` split; `internal fun SettingsPanel.restoreDefaultsConfirmed()` carries the body; the modal was **not** deleted.
- `src/test/kotlin/com/six2dez/burp/aiagent/ui/SettingsSaveAsyncTest.kt` — blocking host-callback seam, 3 new tests, corrected structural test, comment-stripping `functionBody()`.
- `build.gradle.kts` — two new `tasks.test` inputs (`mainTabPersistSource`, `settingsIoStructuralSource`); the five pre-existing property names are unchanged and unreordered.
- `.planning/phases/23-edt-confinement-ui-responsiveness/deferred-items.md` — residual `D-23-06-1`.

## Decisions Made

See `key-decisions` in the frontmatter. Two are worth restating in prose:

**Why the drop test needed the dispatch observer.** `applied.set(generation)` runs before `apply`, under the lock, so a straightforward "block generation 1, let generation 2 through" setup can never produce an out-of-order arrival — generation 2 simply waits on the lock and then advances normally. The guard only fires when an OLDER generation reaches the lock after a newer one has begun, which is a scheduler race. Parking the older *submitter* inside `OffEdtDispatch`'s `dispatchedObserver` — which fires on the calling thread **before** the worker is started — makes it deterministic. The plan's suggested setup would have produced a test that passes without ever exercising the guard.

**Why the structural assertions strip block comments, twice.** Task 2's ledger gate was designed for it. Task 3's `functionBody()` needed it after a measured failure: a comment I had just written above the confirmation dialog said `restoreDefaultsConfirmed()`, and the raw-text `indexOf` found the comment at offset 77 rather than the call at offset 232, so `confirm in 0 until handoff` failed against correct code. That is exactly the trap the plan's `<anti_vacuity_mandate>` names, arriving from the opposite direction.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Nested block comment broke compilation of `SettingsPersistQueueTest.kt`**
- **Found during:** Task 2
- **Issue:** The KDoc for `codeLinesOf` quoted the three comment markers literally, including a block-comment opener. Kotlin block comments **nest**, so that opener started a nested comment that swallowed the rest of the file — `Syntax error: Missing '}'` at `:341` and `Unclosed comment` at EOF.
- **Fix:** Reworded the KDoc to name the three markers longhand instead of quoting them, and recorded the reason inline so the next author does not reintroduce it.
- **Files modified:** `src/test/kotlin/com/six2dez/burp/aiagent/ui/SettingsPersistQueueTest.kt`
- **Verification:** `compileTestKotlin` green; suite runs 5 tests.
- **Committed in:** `2f6d1bc`

**2. [Rule 1 - Bug] `functionBody()` indexed a comment instead of the code**
- **Found during:** Task 3 (RED phase)
- **Issue:** The new first clause of `restoreDefaultsConfirmsBeforeDispatchAndReportsFromTheCallback` failed against a **correct** implementation, because a production comment above the confirmation dialog named `restoreDefaultsConfirmed()` and `indexOf` found it first. Left unfixed, the natural next move is to relax the assertion — which is how a real gate becomes a vacuous one.
- **Fix:** `functionBody()` now strips comment lines with the phase's canonical filter (`//`, `*`, `/*` as the first non-space characters) before any index comparison, with the measurement recorded in its KDoc.
- **Files modified:** `src/test/kotlin/com/six2dez/burp/aiagent/ui/SettingsSaveAsyncTest.kt`
- **Verification:** the clause passes on correct code and Probe 8 still drives the fourth clause red.
- **Committed in:** `97458da`

**3. [Rule 3 - Blocking] `detekt` `LongMethod` re-fired on `applySettingsToUi`**
- **Found during:** Task 3
- **Issue:** `detekt-baseline.xml:128` keys the existing suppression on the full signature (`...applySettingsToUi(updated: AgentSettings)`). Adding the `notifyHosts` parameter orphaned that entry, so the finding re-fired at 145 lines against the threshold of 80 and failed the gate.
- **Fix:** Inline `@Suppress("LongMethod")` carrying its reason — the sanctioned escape hatch, matching 23-03 and 23-05. The baseline was **not** regenerated: `git diff --stat detekt-baseline.xml` is empty and `grep -c '<ID>'` returns 1096.
- **Files modified:** `src/main/kotlin/com/six2dez/burp/aiagent/ui/SettingsPanelSettingsIO.kt`
- **Verification:** `ktlintCheck detekt test` exit 0.
- **Committed in:** `02c0b06`

**4. [Design adjustment, no rule] Task 3 executed as four commits rather than one**
- The plan marks Task 3 `tdd="true"`, and a meaningful RED requires the extraction seam to exist first. Sequence: `refactor` (seam, behaviour unchanged) → `test` (RED, 2 failing) → `feat` (GREEN) → `chore` (detekt). The RED commit `97458da` is a genuine failing-test commit, not a retrofit.

---

**Total deviations:** 3 auto-fixed (2 blocking, 1 bug) + 1 documented sequencing adjustment.
**Impact on plan:** No scope creep. Deviations 1 and 2 were both instances of the trap this phase exists to guard against — a comment being counted as code — caught by running the gate rather than by reading it.

## Cross-Plan Interaction (flagged by the plan, not fixed here)

Moving `renderStatus()` into the queue's asynchronous EDT tail makes `updateMcpControls()` → `chatPanel.setMcpAvailable(running)` → `updateChatAvailability()` fire at a new moment. `updateChatAvailability()` (`ChatPanel.kt:343-350`) writes `inputArea.isEnabled = mcpAvailable` and `toolsBtn.isEnabled = mcpAvailable` ignoring the busy state — a clobber that is already live today via `mcpStatusTimer`'s 1000 ms tick. **Plan 23-07 owns the fix and runs in this same wave.** This plan edited no `ChatPanel.kt`: `git diff` against the plan's base is empty for that file, `assertEdt` is unmoved at 6 and `SwingUtilities.invokeLater` at 11.

## Known Stubs

None. No hardcoded empty values, placeholder text, `TODO`/`FIXME` markers, skipped tests or unrun `<verify>` commands were introduced by this plan.

## Residual Recorded

**`D-23-06-1` / threat `T-23-06-08` (high, accept)** — `MainTab.kt:111` is an EIGHTH EDT `settingsRepo.save()` site that the verifier's enumeration of seven missed. It sits in the `applySettings` lambda passed to `ChatPanel`'s constructor and runs on the EDT for every chat message send. Deliberately not fixed: the send path depends on `supervisor.applySettings(settings)` completing before the turn is sent, so the lambda cannot be moved wholesale without a split this run's scope does not cover. Both consequences are written up in `deferred-items.md`: **(a)** a torn-write window outside the queue's lock, which is why the CR-02 truth is scoped to queue-submitted writes rather than stated as a property of the persisted file; **(b)** a narrow SC4 residual where a chat send racing an in-flight MCP disable drives `MainTab.kt:115` into the same bounded wait on the EDT — bounded because `:111` passes CURRENT settings and so cannot originate an enabled→disabled transition itself.

## Verification Results

| # | Check | Result |
|---|---|---|
| 1 | `./gradlew ktlintCheck detekt test` | **exit 0** |
| 2 | `./gradlew edtGuardWithoutAssertionsTest` | **exit 0**, 3 tests, 0 failures |
| 3 | `./gradlew test -PexcludeHeavyTests=true` | **exit 0**. `ChatPanelEdtConfinementTest` 17/0, `McpToolExecutorEdtGuardTest` 3/0, `SettingsSaveAsyncTest` **10**/0, `SettingsPersistQueueTest` **5**/0. **Control:** no result file for `ChatPanelConcurrencyTest`, so the filter demonstrably WAS applied |
| 4 | `git diff --stat detekt-baseline.xml` · `grep -c '<ID>'` | empty · **1096** |
| 5 | `ChatPanel.kt` diff · `assertEdt` · `invokeLater` | empty · **6** · **11** |
| 6 | `grep -n 'future.get' KtorMcpServerManager.kt` | `:270 future.get(10, TimeUnit.SECONDS)` unchanged; **no `src/main/.../mcp/` file touched** — D-14 not re-opened |
| 7 | `git status --short` | `README.md` still carries its pre-existing modification and appears in **0** commits of this plan |
| 8 | Nine red probes | all recorded above with failing test, line number and empty restored diff |

**Mention-ledger counts (block-comment-filtered, `MainTab.kt`):** `persistSettings(` = **6**, `persistSettingsAndApplyMcp(` = **3**, `settingsRepo.save(` = **3**, `mcpSupervisor.applySettings(` = **2**. A `//`-only filter returns 7/4/4/3 against this correct implementation, confirming the block-comment filter is load-bearing exactly as the plan predicted.

**`build.gradle.kts`:** `grep -c 'withPropertyName'` = **7**; `adrRecord`, `secTierKdocSource`, `originVisibilitySource`, `chatPanelStructuralSource`, `settingsActionsStructuralSource` all still present and unmodified; `edtGuardWithoutAssertionsTest` and `nightlyRegressionTest` both still registered.

## Issues Encountered

None beyond the three auto-fixed deviations above. `RedactionTest` did not flake in any of the three full-suite runs.

## User Setup Required

None — no external service configuration required.

## Next Phase Readiness

- **Ready for re-verification of SC4.** All four `missing:` items are discharged and the phase's three gates are green. The remaining SC4 evidence is the live-Burp observation (`23-HUMAN-UAT.md`, coverage item D5).
- **Plan 23-07** runs in this same wave and owns the `updateChatAvailability()` busy-state clobber noted above; it should re-check that its `ChatPanel.kt` changes still leave `assertEdt` at 6.
- **Plan 23-08 (CR-01)** inherits `T-23-06-06` as a bound rather than a mitigation: `dispose()` prevents a NEW apply from starting but does not stop an in-flight one. The same supersede should be extended to the `SettingsPanel` save worker there.
- **`D-23-06-1` is open** and rated `high`. It is the natural first item for any follow-up plan that can split the `ChatPanel` `applySettings` lambda.

## Self-Check: PASSED

- All 8 files in `key-files` verified present on disk with `[ -f ]`.
- All 6 commit hashes verified with `git log --oneline --all`.
- All three phase gates re-run and green after the final commit.

---
*Phase: 23-edt-confinement-ui-responsiveness*
*Completed: 2026-08-21*
