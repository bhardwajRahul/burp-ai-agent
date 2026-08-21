---
phase: 23-edt-confinement-ui-responsiveness
verified: 2026-08-21T11:05:00Z
status: human_needed
score: 6/6 must-haves verified
behavior_unverified: 0
overrides_applied: 0
re_verification:
  previous_status: gaps_found
  previous_score: 2026-08-21T09:40:00Z — 5/6
  gaps_closed:
    - "SC4 — Saving Settings with MCP enabled→disabled does not block the EDT on KtorMcpServerManager.stop()'s bounded 10-second wait. settingsRepo.save() and backends.reload() are likewise not blocking the EDT."
  gaps_remaining: []
  regressions: []
  closure_evidence:
    - "missing item 1 — restoreDefaultsConfirmed now calls applySettingsToUi(defaults, notifyHosts = false) (SettingsPanelActions.kt:118); the three host notifications are behind `if (notifyHosts)` at SettingsPanelSettingsIO.kt:451-455 and fire from no EDT path."
    - "missing item 2 — all seven MainTab EDT settingsRepo.save() sites now route through SettingsPersistQueue onto the burp-ai-settings-sync daemon worker; the two MCP-applying sites are persistSettingsAndApplyMcp (MainTab.kt:462, :487)."
    - "missing item 3 — SettingsSaveAsyncTest.newFixture now installs a MainTab-shaped blocking onMcpEnabledChanged (installBlockingMcpCallback); restoreDefaultsDoesNotFireTheHostNotificationsOnTheEdt asserts 0 invocations and its positive control asserts 1."
    - "missing item 4 — SettingsSaveAsyncTest.kt:429's assertion message no longer certifies the defect; it is narrowed to COMPONENT writes and names the freeze it previously blessed."
    - "Red probe A (verifier-run): reverting :118 to applySettingsToUi(defaults) turns 2/15 SettingsSaveAsyncTest scenarios RED, including restoreDefaultsDoesNotFireTheHostNotificationsOnTheEdt."
    - "Red probe B (verifier-run): making SettingsPersistQueue.submit offload-then-join turns 3/5 SettingsPersistQueueTest scenarios RED, including theSubmittingThreadReturnsWhileTheApplyIsStillBlocked."
    - "Red probe C (verifier-run): replacing one persistSettings call site with an inline settingsRepo.save turns everyMainTabSettingsWriteGoesThroughThePersistQueue RED."
gaps: []
deferred: []
behavior_unverified_items: []
coincidental_reliance_items: []
human_verification:
  - test: "23-HUMAN-UAT.md item 1 — D-12 save-failure modal alongside the inline banner"
    expected: "Both the inline banner and the JOptionPane modal appear, their text matches, and the Settings tab is usable afterwards"
    why_human: "JOptionPane.getRootFrame() throws HeadlessException under -Djava.awt.headless=true, which tasks.test sets. No automated seam exists for the modal."
  - test: "23-HUMAN-UAT.md item 2 — FLAG-23-01 disabled Save button legibility under Burp's Look-and-Feel"
    expected: "Save and Restore defaults both read as visibly inert for the whole flight, in light and dark themes"
    why_human: "saveButton.isOpaque = true with an explicit primary background; a headless JButton never paints, so only property values are assertable."
  - test: "23-HUMAN-UAT.md item 3 — FLAG-23-04 sub-frame Send/Cancel flicker on the auto-approved chain, plus the ~160-char tool-cancel line wrapping"
    expected: "Observation item, not a gate. Record whether a Send flash appears between chain steps; confirm the Rule C-1 cancel line wraps rather than clipping and never reads 'Request cancelled.' for a dispatched tool call"
    why_human: "A repaint between two invokeLater blocks is sub-frame; JEditorPane wrap at 75% viewport width never paints headlessly."
  - test: "23-HUMAN-UAT.md item 4 — A1, does live Burp actually throw on an EDT sendRequest"
    expected: "An exception matching 'Extensions should not make HTTP requests in the Swing event dispatch thread', or a recorded 'no exception, just froze'"
    why_human: "Montoya runtime behaviour against a live Burp. http1_request is not headlessly drivable at all — its body reaches HttpRequest.httpRequest, a static factory unavailable in pure-JVM unit tests."
  - test: >-
      SC4 CONFIRMATION (new item 5 — REPLACES and INVERTS the stale item the previous VERIFICATION.md
      carried). With MCP enabled and the server Running, exercise all three doors in the Settings tab:
      (a) untick 'Enable MCP server' in the MCP tab WITHOUT pressing Save; (b) flip the header mcpToggle
      off; (c) press 'Restore defaults' and confirm. In each case keep the mouse moving over the Burp UI
      and watch the tab repaint. Then confirm the server actually stopped (the status label reads
      Stopped, and `lsof -nP -iTCP:<port> -sTCP:LISTEN` is empty).
    expected: >-
      NO FREEZE. The UI must keep repainting and stay interactive for the whole transition — the
      bounded future.get(10, TimeUnit.SECONDS) now runs on burp-ai-settings-sync or
      burp-ai-settings-save, not the EDT. AND the server must genuinely stop. A freeze means SC4
      regressed; a responsive UI with a listener still up means the MCP apply was dropped (see
      WARN-3 / CR-03).
    why_human: >-
      Requires a live Burp with a running Ktor MCP server to pay the real bounded wait. The headless
      suite proves the callback does not fire on the EDT and that the queue's submit returns while the
      apply is blocked, but it cannot observe an actual repaint or a real socket. NOTE FOR WHOEVER
      RUNS THIS — the previous report's version of this item told you to "expect up to 10 seconds of
      frozen UI"; that expectation was written against the OPEN gap and is now inverted. A freeze is
      now a FAILURE, not the expected observation.
warnings:
  - id: WARN-1
    finding: "CR-01 (review 2) — SettingsPersistQueue.applyIfCurrent reads `disposed` once and then runs the whole apply body; persistSettingsAndApplyMcp can start the Ktor server after App.shutdown(). 23-06-PLAN.md:566 records threat T-23-06-06 as 'fully mitigated in plan 23-08' — it is not, and deferred-items.md does not carry it."
    severity: high
    in_scope_for_sc: false
  - id: WARN-2
    finding: "CR-02 (review 2) — the Settings-tab save worker and the ChatPanel applySettings lambda write all ~107 preference keys outside the queue's lock; AgentSettingsRepository.save() is unsynchronised and setActionsBusy disables only saveButton/restoreButton. Pre-phase all eight save sites ran on the EDT and were serialised by it, so this interleave window is PHASE-INTRODUCED."
    severity: high
    in_scope_for_sc: false
  - id: WARN-3
    finding: "CR-03 (review 2) — applyIfCurrent drops the whole superseded lambda and the lambdas are heterogeneous, so a newer persistSettings can discard an older persistSettingsAndApplyMcp's MCP stop. ReentrantLock() is non-fair, so grant order is not click order under contention."
    severity: high
    in_scope_for_sc: false
  - id: WARN-4
    finding: "WR-01 (review 2) — CI's edtGuardWithoutAssertionsTest step drags :test and :jacocoTestReport in behind it WITHOUT -PexcludeHeavyTests=true. Confirmed by verifier-run --dry-run."
    severity: medium
    in_scope_for_sc: false
  - id: WARN-5
    finding: "WR-03 (review 2) — applySettingsToUi(notifyHosts = true) has no production caller; its negative control drives ~145 Swing writes off the EDT inside the EDT-confinement suite."
    severity: low
    in_scope_for_sc: false
  - id: WARN-6
    finding: "WR-04 (review 2) — ChatPanel.kt:1043 `inputArea.isEnabled = !sending` contradicts :361 and contradicts 23-07's own key_link `via` text, which claims both sites gate inputArea on `mcpAvailable && !isSending`."
    severity: medium
    in_scope_for_sc: false
  - id: WARN-7
    finding: "WR-02 (review 2) — the superseded user-originated tool call reaches only the nullable, disable-able in-memory AiRequestLogger; the KDoc's 'same reach' claim against the chain path (which also emits AuditLogger.emitGlobal) is an overclaim."
    severity: medium
    in_scope_for_sc: false
---

# Phase 23: EDT Confinement & UI Responsiveness Verification Report

**Phase Goal:** The Burp UI stays responsive during an agent tool chain and during a Settings save.
**Verified:** 2026-08-21T11:05:00Z
**Status:** human_needed
**Re-verification:** Yes — after gap closure (plans 23-06, 23-07, 23-08). Previous: `gaps_found` 5/6.

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| SC1 | `McpToolExecutor.executeTool` is never invoked on the EDT from the chat tool-chain path, asserted by a test that does not rely on `-ea` | ✓ VERIFIED | `check(!SwingUtilities.isEventDispatchThread())` is the FIRST statement of `executeToolResult` (`McpToolExecutorImpl.kt:159`) — Kotlin `check`, live with assertions off. All three chat call sites are `work = { McpToolExecutor.executeTool(...) }` inside `OffEdtDispatch.run` (`ChatPanel.kt:1152-1159`, `:2581-2587`, `:3144-3151`). **I ran the `-da` gate myself, forced fresh:** `./gradlew edtGuardWithoutAssertionsTest --rerun-tasks` → `tests="3" failures="0" errors="0"`, timestamp `2026-08-21T10:52:29`. Task carries `jvmArgs("-da", …)` and `filter { includeTestsMatching("*McpToolExecutorEdtGuardTest") }` (`build.gradle.kts:264-277`), so it cannot silently no-op. |
| SC2 | `api.http().sendRequest(...)` inside MCP tools and `runBlocking { manager.callTool(...) }` both execute off the EDT | ✓ VERIFIED | The guard at `:159` precedes `val resolvedName = canonicalToolId(name)` (`:166`) and the `if (resolvedName.startsWith("ext:")) return routeExternalToolCall(...)` early return (`:171-172`), so the `runBlocking { manager.callTool(...) }` at `:1149` is inside the guarded region. `executeTool` (`:1075`) is a thin wrapper over `executeToolResult`; the MCP-server path calls `executeToolResult` directly — one door, all callers. `McpToolExecutorEdtGuardTest.theGuardPrecedesTheExternalToolEarlyReturn` distinguishes the two candidate placements and runs under `-da`. |
| SC3 | An 8-iteration chain with a slow tool leaves the UI repainting; results arrive in order and land on the EDT | ✓ VERIFIED | `aFullAutoChainProducesEightResultsInSubmissionOrder` pins `MAX_AUTO_TOOL_ITERATIONS == 8`, awaits 8 settled workers and asserts step order selected by trace id. `theEdtRunsQueuedWorkWhileAToolCallIsMidFlight` is a mutual-latch handshake: the tool blocks on a runnable the test queues to the EDT *after* the tool was entered, so a blocked EDT is a categorical deadlock. **Re-probed against the CURRENT `OffEdtDispatch` (it changed in the gap-closure diff, so the prior probe no longer covered it):** offload-and-block turns 10 of 23 scenarios RED, `theEdtRunsQueuedWorkWhileAToolCallIsMidFlight` among them. Results land on the EDT through the helper's single `invokeLater` tail. |
| SC4 | Saving Settings with MCP enabled→disabled does not block the EDT on `KtorMcpServerManager.stop()`'s bounded 10s wait; `settingsRepo.save()` and `backends.reload()` likewise off the EDT | ✓ VERIFIED — **was the gap, now closed at all four doors** | I re-enumerated every production caller rather than trusting the SUMMARYs: `grep -rn "settingsRepo\.save(\|mcpSupervisor\.applySettings(\|backends\.reload(" src/main/kotlin/` yields exactly five non-comment sites. `App.kt:135` (startup thread). `SettingsPanelSettingsIO.kt:538/546/551` — inside `applyAndSaveSettingsBody`, dispatched onto `burp-ai-settings-save`, and it is now the *only* thing `restoreDefaultsConfirmed` uses for the MCP transition. `MainTab.kt:561` and `:592-593` — inside the two `SettingsPersistQueue` apply lambdas, dispatched onto `burp-ai-settings-sync`. `MainTab.kt:119/123` is the accepted residual `D-23-06-1`. The three doors the previous report named are individually closed: restore-defaults passes `notifyHosts = false` (`SettingsPanelActions.kt:118`); the Settings-tab checkbox (`SettingsPanelInit.kt:258`) and the header toggle both land on `persistSettingsAndApplyMcp` (`MainTab.kt:462`, `:487`). **Two independent red probes confirm the new evidence discriminates** — see Anti-Vacuity below. |
| SC5 | No regression in REL-01 EDT confinement for `ChatPanel` session maps | ✓ VERIFIED | `assertEdt()` body byte-identical to pre-phase `2a0c703` (`assert(SwingUtilities.isEventDispatchThread())` + the same REL-01 message). Occurrence count **6**, unmoved — 1 declaration (`:824`), 4 invocations (`:1056`, `:1521`, `:2611`, `:2821`), 1 comment (`:2610`). Counted on raw source, no comment filter; `theEdtConfinementAssertionIsByteIdenticalAndStillHasSixMentions` uses `occurrencesOf("assertEdt()", chatPanelSource())` with no filter either, so the house filter's destructive 5 never enters the evidence. `SwingUtilities.invokeLater` in `ChatPanel.kt` unmoved at **11**. All three `work = { … }` lambdas read only EDT-captured locals; every `sessionsById[...]` touch is in the `onEdt` tail. Behavioural half `theToolTailWritesIntoItsCapturedPanelWhileTheGuardedMapsMoveUnderneathIt` went RED under my probe, so it discriminates. |
| SC6 | `MainTab`'s existing `Thread { … } + SwingUtilities.invokeLater` idiom reused, no new concurrency idiom | ✓ VERIFIED | `OffEdtDispatch.run` still ends `Thread(body, threadName).apply { isDaemon = true }.start()` with exactly one `SwingUtilities.invokeLater`. `SettingsPersistQueue` adds **no** second marshalling helper — it delegates to `OffEdtDispatch.run` (`:78-85`). Grep for `SwingWorker`, `ExecutorService`, `Executors.`, `CompletableFuture`, `runBlocking`, `GlobalScope`, `CoroutineScope`, `launch {` across the whole `ui/` package returns **zero** matches. Stated honestly: the queue does add an `AtomicLong` pair and a `ReentrantLock`, which are `java.util.concurrent` primitives CONVENTIONS.md:95 permits, not a new concurrency layer. |

**Score:** 6/6 truths verified (0 present, behavior-unverified)

### Phase-Blocking Gates

| Gate | Command | Result | Status |
|------|---------|--------|--------|
| New suites execute under the PR-gate filter with non-zero counts | `JAVA_HOME=$(…21) ./gradlew test -PexcludeHeavyTests=true --no-daemon` | 108 suites, 785 tests. `ChatPanelEdtConfinementTest` **23**/0F/0E · `McpToolExecutorEdtGuardTest` **3**/0F/0E · `SettingsSaveAsyncTest` **15**/0F/0E. Bonus suites from gap closure: `SettingsPersistQueueTest` **5**/0F/0E · `OffEdtDispatchFailurePathTest` **4**/0F/0E. **Exclusion control:** `ChatPanelConcurrencyTest` has NO result file in the same run, so the filter demonstrably was applied and the counts above mean something. | ✓ PASS |
| — same run, one failure | `RedactionTest > windowedScanRedactsJsonPairWhoseValueStraddlesTheCut()` | **Documented flake, not a phase regression, and I checked rather than assumed.** Message is the exact recorded signature: *"shift=20: the sweep must prove the pair was REDACTED, not that the window was DROPPED"*. `git diff cb60e32 HEAD --name-only \| grep -i redact` is empty. The full-phase diff touches `redact/Redaction.kt` in **comments only** (two KDoc blocks; `applyAndSaveSettings` → `applyAndSaveSettingsBody` rename in prose). Re-ran isolated: `./gradlew test --tests '*RedactionTest'` → **BUILD SUCCESSFUL**. | ℹ️ Non-regression |
| detekt baseline unmoved | `git diff --stat detekt-baseline.xml` · `grep -c '<ID>'` | Empty diff; **1096** entries, pinned at the v0.10.0 milestone metric. New findings from gap closure were answered with inline `@Suppress` carrying reasons (`applySettingsToUi` LongMethod, `applyAndSaveSettingsBody` ReturnCount), not a regenerated baseline. | ✓ PASS |
| Static analysis clean | `JAVA_HOME=$(…21) ./gradlew ktlintCheck detekt` | exit 0, all tasks UP-TO-DATE — which doubles as proof my red probes were reverted byte-exactly. | ✓ PASS |
| Working tree clean after verification | `git status --porcelain` · `git diff --stat src/` | Only `M README.md` (pre-existing, predates this phase) plus untracked `.gsd/`, `.planning/milestone.lock`, `.planning/research/.cache/`. `git diff --stat src/` is **empty**. | ✓ PASS |

### Anti-Vacuity Red Probes (verifier-run, this pass)

This phase has a measured history of **ten** tests going green against a fully present defect, four of them found during gap closure and two of those specified by the PLAN itself. Review 2 reports finding no vacuous assertion in the new suites. I did not accept that; I probed the SC4 evidence at three independent points and re-probed SC3/SC5 because `OffEdtDispatch` changed underneath the previous probe.

| Probe | Mutation | Suite run | Result | Reads on |
|-------|----------|-----------|--------|----------|
| **A** | `SettingsPanelActions.kt:118` → `applySettingsToUi(defaults)` (restore the exact defect the previous report gapped on) | `*SettingsSaveAsyncTest` | **2 of 15 FAILED** — `restoreDefaultsDoesNotFireTheHostNotificationsOnTheEdt` AND `restoreDefaultsConfirmsBeforeDispatchAndReportsFromTheCallback` | SC4, restore-defaults door. Both the behavioural seam and the corrected structural assertion catch it. |
| **B** | `SettingsPersistQueue.submit` → offload-then-`join()` (the anti-pattern `OffEdtDispatch`'s own KDoc names) | `*SettingsPersistQueueTest` | **3 of 5 FAILED** — `theSubmittingThreadReturnsWhileTheApplyIsStillBlocked`, `anOlderGenerationIsDroppedRatherThanAppliedOverANewerOne`, `disposeStopsNewWorkAndDoesNotBlockTheCaller` | SC4, header-toggle / MCP-checkbox door. The queue's own EDT-freedom claim is falsifiable. |
| **C** | one `persistSettings("active-toggle", …)` call site → inline `settingsRepo.save(...)` | `*SettingsPersistQueueTest` | **1 of 5 FAILED** — `everyMainTabSettingsWriteGoesThroughThePersistQueue` | SC4, the structural ledger. Equality counts, and the `codeLinesOf` helper asserts `file.isFile` so a moved source path fails loudly instead of passing silently. |
| **D** | `OffEdtDispatch` → `.also { it.start(); it.join() }` | `*ChatPanelEdtConfinementTest` | **10 of 23 FAILED** — including `theEdtRunsQueuedWorkWhileAToolCallIsMidFlight` (SC3) and `theToolTailWritesIntoItsCapturedPanelWhileTheGuardedMapsMoveUnderneathIt` (SC5) | SC3 + SC5, re-established against the post-CR-04 `OffEdtDispatch`. |

All four probes reverted; `git diff --stat src/` empty and `ktlintCheck detekt` UP-TO-DATE afterwards.

**One honest limit on probe D.** `aFullAutoChainProducesEightResultsInSubmissionOrder` did **not** fail under the offload-and-block mutation — correctly so: serial execution still yields eight in-order results, and that test's criterion is ordering and count, not responsiveness. SC3's responsiveness half rests entirely on `theEdtRunsQueuedWorkWhileAToolCallIsMidFlight`, which is a single-tool scenario. No automated test asserts "EDT free across all eight iterations". The property holds by construction — every iteration dispatches through the same helper — and the visual half is 23-HUMAN-UAT item 3.

**Vacuity check on the SC4 fixture, done independently.** `restoreDefaultsDoesNotFireTheHostNotificationsOnTheEdt` asserts `seam.invocations.get() == 0`, and a zero-assertion is exactly the shape that passes when a fixture never wired the callback at all. Its positive control `applySettingsToUiStillNotifiesHostsByDefault` asserts `== 1` through the same `installBlockingMcpCallback` seam and also asserts `seam.edtWasFree`, so the seam is proved live. Probe A confirms the pair as a whole discriminates.

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `src/main/kotlin/…/ui/SettingsPersistQueue.kt` | Generation-ordered, lock-serialised off-EDT settings seam; `internal class SettingsPersistQueue`; ≥40 lines | ✓ VERIFIED | 120 lines. Generation minted on the calling thread as `submit`'s first statement; `applied` advanced before the body so a throwing apply is not replayable; `dispose()` explicitly lock-free with a stated reason. Imported and used at 8 sites in `MainTab.kt`. |
| `src/test/kotlin/…/ui/SettingsPersistQueueTest.kt` | Behavioural acceptance suite for the queue; ≥60 lines | ✓ VERIFIED | 388 lines, 5 scenarios, executed green under the PR-gate filter. Overlap detector is an entry-time `AtomicBoolean`, not a snapshot-field comparison — correct, since `AgentSettings` is a data class passed by reference. Probes B and C confirm it discriminates. |
| `src/main/kotlin/…/ui/SettingsPanelSettingsIO.kt` | `applySettingsToUi(notifyHosts)`; `applyAndSaveSettingsBody(isCurrent)` with three supersede re-checks | ✓ VERIFIED (see WARN-5) | `notifyHosts` gate at `:451-455`; three `if (!isCurrent()) return` guards at `:547`, `:577`, `:596`, each immediately before an externally visible mutation. `ReturnCount` answered inline with its reason. **But** the `notifyHosts = true` branch has no production caller. |
| `src/main/kotlin/…/ui/OffEdtDispatch.kt` | CR-04: every sink on the path to the single `invokeLater` wrapped | ✓ VERIFIED | 129 lines. Two `runCatching { logError` sites; `settledObserver` invocation also wrapped and in the `finally`; `dispatchedObserver` deliberately bare with its asymmetry argued in the KDoc. Still one `invokeLater`, still `Thread(...).apply { isDaemon = true }.start()`. |
| `src/test/kotlin/…/ui/OffEdtDispatchFailurePathTest.kt` | CR-04 acceptance, each limb separately | ✓ VERIFIED | 334 lines, 4 tests, green under the PR-gate filter. |
| `src/test/kotlin/…/ui/SettingsSaveAsyncTest.kt` | S-11 + FLAG-23-06 + the four `missing` items | ✓ VERIFIED (was ⚠️ PARTIAL) | 947 lines (was 540), 15 tests (was 7). `newFixture` now installs the MainTab-shaped blocking callback the previous report demanded. `:429`'s message no longer certifies the defect. |
| `src/test/kotlin/…/ui/ChatPanelEdtConfinementTest.kt` | SC1/SC2/SC3/SC5/SC6 acceptance | ✓ VERIFIED | 23 scenarios (was 17), green under the PR-gate filter, probe-D-discriminating. |
| `src/test/kotlin/…/mcp/tools/McpToolExecutorEdtGuardTest.kt` | S-10: throws with `-ea` off, incl. the `ext:` variant | ✓ VERIFIED | 3 tests, green under both `tasks.test` (`-ea`) and a fresh `edtGuardWithoutAssertionsTest` (`-da`) I ran myself. |
| `.planning/…/23-HUMAN-UAT.md` | Live-Burp items | ⚠️ INCOMPLETE → see Human Verification | 4 items, all `pending`, `total: 4`. Should gain the corrected SC4 confirmation as a 5th. |
| `.planning/…/deferred-items.md` | Recorded residuals | ⚠️ INCOMPLETE → see WARN-1 / WARN-2 | Carries `D-23-04-1`, `D-23-06-1`, `D-23-07-1` with severity and suggested homes. Does **not** carry the in-flight half of `T-23-06-06`, nor the second unlocked writer. |

### Key Link Verification

| From | To | Via | Status |
|------|----|-----|--------|
| `MainTab` (7 listener bodies) | `SettingsPersistQueue` | `persistSettings(...)` / `persistSettingsAndApplyMcp(...)` → `settingsPersistQueue.submit` (`:88`, `:176`, `:462`, `:469`, `:476`, `:487`, `:495`, `:503`) | ✓ WIRED |
| `SettingsPersistQueue.submit` | `OffEdtDispatch.run` | `threadName = "burp-ai-settings-sync"` (`:78-85`) — no second marshalling helper | ✓ WIRED |
| `SettingsPanelActions.restoreDefaultsConfirmed` | `applySettingsToUi` | `applySettingsToUi(defaults, notifyHosts = false)` at `:118` | ✓ WIRED |
| `SettingsPanelActions.restoreDefaultsConfirmed` | `applyAndSaveSettingsAsync` | `:120` — the MCP transition still happens, just on `burp-ai-settings-save` | ✓ WIRED |
| `SettingsPanel.shutdown` | `SettingsPanel.saveGeneration` | `disposed = true; saveGeneration.incrementAndGet()` (`SettingsPanelActions.kt:179-180`) | ✓ WIRED |
| `applyAndSaveSettingsBody` | `McpSupervisor` / scanners | `isCurrent()` re-checked at `:547`, `:577`, `:596` | ✓ WIRED |
| `MainTab.shutdown` | `SettingsPersistQueue.dispose` | `:926`, ordered FIRST, lock-free | ⚠️ PARTIAL — stops new applies only; an in-flight apply is unguarded (WARN-1) |
| `.github/workflows/build.yml` | `edtGuardWithoutAssertionsTest` | named pr-gate step, all three matrix OSes (`:55-56`) | ⚠️ PARTIAL — wired, but drags `:test` unfiltered (WARN-4) |
| `ChatPanel.setSendingState` / `updateChatAvailability` | `isSending` | claimed as "both gate `toolsBtn` AND `inputArea` on `mcpAvailable && !isSending`" | ⚠️ PARTIAL — true at `:360-361`, false at `:1043` (WARN-6) |
| `OffEdtDispatch` | `SwingUtilities.invokeLater` | every sink `runCatching`-wrapped | ✓ WIRED |
| `ChatPanel.clearChatState` | `RunningToolTracker` | — | ✗ NOT WIRED (recorded residual `D-23-04-1` / `D-23-07-1`) |

### Data-Flow Trace (Level 4)

| Artifact | Data | Source | Produces real data | Status |
|----------|------|--------|--------------------|--------|
| `SettingsPersistQueue` | `snapshot: AgentSettings` | `settingsPanel.currentSettings()` read on the EDT before dispatch | Yes — reaches `AgentSettingsRepository.save()` (real Burp preferences), not a stub | ✓ FLOWING |
| `applyAndSaveSettingsBody` | `updated: AgentSettings` | `currentSettings()` on the EDT, then `settingsRepo.save` / `backends.reload` / `mcpSupervisor.applySettings` | Yes | ✓ FLOWING |
| `restoreDefaultsConfirmed` | `defaults` | `settingsRepo.defaultSettings()` | Yes; `defaultMcpSettings().enabled = false`, so the async body genuinely takes `McpSupervisor`'s `if (!settings.enabled) { stop() }` branch | ✓ FLOWING |
| `OffEdtDispatch` tail | `Result<T>` | crosses the thread boundary intact (real `Throwable`, not stringified) | Yes — `reportFailedToolCall` keys `errorClass` off the throwable's own class | ✓ FLOWING |

### Behavioural Spot-Checks

| Behaviour | Command | Result | Status |
|-----------|---------|--------|--------|
| SC1's `-da` guarantee holds today, freshly executed | `./gradlew edtGuardWithoutAssertionsTest -x test -x jacocoTestReport --rerun-tasks` | `tests="3" failures="0" errors="0"`, timestamp `2026-08-21T10:52:29` (UTC) — a genuinely new run, not a cached UP-TO-DATE | ✓ PASS |
| WR-01: does the `-da` gate drag `:test` in? | `./gradlew edtGuardWithoutAssertionsTest --dry-run` | `:edtGuardWithoutAssertionsTest SKIPPED` / `:test SKIPPED` / `:jacocoTestReport SKIPPED` | ✗ FAIL → WARN-4 (confirms review 2 independently) |
| RedactionTest is a flake, not a phase break | `./gradlew test --tests '*RedactionTest'` | BUILD SUCCESSFUL in isolation | ✓ PASS |
| Full PR gate | `./gradlew test -PexcludeHeavyTests=true` | 785 tests, 1 failure = the flake above; all five phase suites green | ✓ PASS |
| `AgentSettingsRepository.save()` is synchronised? | `grep -n "fun save(\|@Synchronized\|synchronized" AgentSettings.kt` | only `fun save(settings: AgentSettings)` at `:542`; no synchronisation | ✗ FAIL → WARN-2 |
| `setActionsBusy` gates more than two buttons? | `grep -A20 "fun setActionsBusy" BottomTabsPanel.kt` | `saveButton` + `restoreButton` only (`:111-116`) | ✗ FAIL → WARN-2 |

### Probe Execution

No `scripts/*/tests/probe-*.sh` exist in this repository and no Phase 23 PLAN or SUMMARY declares one. **Step 7c: SKIPPED (no project probes declared or discoverable).** The verifier-run red probes in the Anti-Vacuity section above are the substitute and were executed in this process.

### Requirements Coverage

| Requirement | Source plans | Description | Status | Evidence |
|-------------|--------------|-------------|--------|----------|
| REL-05 | 23-01 … 23-08 (all eight, each `requirements: [REL-05]`) | "No MCP tool execution, backend HTTP call, or `runBlocking` on an external MCP server happens on the Swing EDT; the auto tool-chain (up to 8 iterations) leaves the UI responsive throughout. Saving Settings does not block the EDT on `serverManager.stop()`'s 10-second bounded wait." | ✓ SATISFIED | Clause 1 → SC1/SC2. Clause 2 → SC3. Clause 3 → SC4, now closed at all four doors with one recorded residual (`D-23-06-1`). `REQUIREMENTS.md:28`'s `[x]` was premature at the previous verification; it is now correct and needs no revert. |

**Orphan check:** `REQUIREMENTS.md:50` maps REL-05 → Phase 23 and no other requirement ID maps to Phase 23. All eight plans declare `requirements: [REL-05]`. **No orphaned requirements.**

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| — | — | `TBD` / `FIXME` / `XXX` across every file the phase touched (`git diff 2a0c703 HEAD --name-only -- src/ build.gradle.kts .github/`) | — | **None found.** Debt-marker gate passes. |
| `SettingsPersistQueue.kt` | 118-123 | `applyIfCurrent` reads `disposed` once, then runs the whole apply body with no re-check | ⚠️ Warning | WARN-1 / review-2 CR-01. Confirmed at source and traced through `App.kt:220-233` and `MainTab.kt:922-926`. |
| `SettingsPersistQueue.kt` | 51 | `ReentrantLock()` — non-fair | ⚠️ Warning | WARN-3 / review-2 CR-03. Grant order is not arrival order, and the queue's KDoc claim about "submission order is click order" is true of the numbering, not the arbitration. |
| `SettingsPersistQueue.kt` | 28-30 | The "Scope of that claim, stated honestly" block names one residual and omits the larger one | ⚠️ Warning | WARN-2. A materially incomplete honesty section is worse than none, because the next reader stops looking. |
| `SettingsPanelSettingsIO.kt` | 296, 451-455 | `notifyHosts = true` branch unreachable in production | ⚠️ Warning | WARN-5 / review-2 WR-03. Verified: `grep -rn "applySettingsToUi(" src/main/` returns exactly one call site, passing `false`. |
| `ChatPanel.kt` | 1043 | `inputArea.isEnabled = !sending` vs `:361`'s `mcpAvailable && !isSending` | ⚠️ Warning | WARN-6 / review-2 WR-04. Falsifies 23-07's own key-link `via` text. |
| `ChatPanel.kt` | 1232-1263 | `clearChatState()` resolves pending but never takes the running-tool token | ⚠️ Warning | Pre-recorded residual `D-23-04-1` / `D-23-07-1`, documented twice with severity, suggested home and the open UI question. Known and recorded, not a hidden gap. |

### Human Verification Required

Five items, all `pending`. See the `human_verification` frontmatter block for the full text.

Items 1–4 are already in `23-HUMAN-UAT.md`. **Item 5 is new and it REPLACES a stale item** that the previous `23-VERIFICATION.md` carried:

> *"Confirm the freeze: … Expect up to 10 seconds of frozen UI."*

That expectation was written while SC4 was OPEN. After plans 23-06 and 23-08 the correct observation **inverts** to *no freeze*. Left as written it is a human test that certifies the defect — the precise pattern that produced this phase's original gap. This report overwrites it. **`23-HUMAN-UAT.md` should gain the corrected item as a 5th** (`total: 4` → `5`, `pending: 4` → `5`), and its `source:` list should grow to include the 23-06/23-07/23-08 SUMMARYs.

Item 5 also carries a second pass condition the old item did not have: after the transition, the server must actually be **Stopped**. That is the check that would catch WARN-3 in the field.

## Verdict

**All six ROADMAP success criteria are met. The SC4 gap is genuinely closed, and I proved the new evidence discriminating rather than accepting that it exists and is green.**

Both phase-blocking gates pass: the three named suites execute with non-zero counts under `-PexcludeHeavyTests=true` (23 / 3 / 15, with `ChatPanelConcurrencyTest`'s absence as the control that makes those numbers mean something), and `detekt-baseline.xml` is unmoved at 1096 with the new findings answered by inline `@Suppress` rather than a regeneration. The one test failure in the gate run is the documented `RedactionTest` wall-clock flake; I did not take that on faith either — the message matched the recorded signature verbatim, the gap-closure diff touches no redaction code, the full-phase diff touches `Redaction.kt` in comments only, and the suite passes in isolation.

The status is `human_needed` rather than `passed` because five live-Burp items remain, not because anything failed.

**What the gap closure actually did, checked at source.** `restoreDefaultsConfirmed` now suppresses the three host notifications at its one call site instead of firing them on the EDT, and the MCP transition it still needs comes from `applyAndSaveSettingsAsync` on `burp-ai-settings-save`. The Settings-tab MCP checkbox and the header toggle both land on `persistSettingsAndApplyMcp`, which submits to `SettingsPersistQueue` and returns. Five production call sites of `settingsRepo.save` / `mcpSupervisor.applySettings` / `backends.reload` remain and I enumerated every one: one on the startup thread, two inside the queue's apply lambdas, three inside the async save body, and `MainTab.kt:119/123` — the eighth site, recorded before this verification as `D-23-06-1` with threat `T-23-06-08` rated high/accept, and narrow because it passes CURRENT settings and so can only re-enter a `stop()` a different site already originated.

**On review 2's three new blockers, judged independently at source and scored against SC1–SC6.**

- **CR-01 is real and I confirmed the whole chain.** `applyIfCurrent` reads `disposed` once at `SettingsPersistQueue.kt:119` and then runs `apply(snapshot)` to completion; `persistSettingsAndApplyMcp`'s body is `settingsRepo.save(it)` followed by `mcpSupervisor.applySettings(...)` with nothing between them. `MainTab.shutdown()` calls `dispose()` first — correct ordering, and it says honestly that the bound it buys is "no new apply starts" — but `App.kt:233` reaches `mcpSupervisor.shutdown()` while a worker already inside a ~107-key save runs on. 23-08's fix is on `applyAndSaveSettingsBody`, a different body on a different thread. **The ledger entry is the part that worries me more than the defect.** `23-06-PLAN.md:566` records `T-23-06-06` as *"fully mitigated in plan 23-08 (CR-01)"*, while `23-06-SUMMARY.md:281` says the opposite and correctly calls it a bound that 23-08 *should* extend. 23-08 did not, and `deferred-items.md` carries no entry. A high-rated threat is marked closed in the plan of record and tracked nowhere else. **Out of scope for SC1–SC6** — it is teardown correctness, not responsiveness — but it must not be lost, and the fix is the same predicate `applyAndSaveSettingsBody` already takes.
- **CR-02 is real, and the part review 2 does not say is that this phase CREATED it.** I checked pre-phase `2a0c703`: all eight `settingsRepo.save` sites ran on the EDT, so the EDT itself serialised every settings write and no interleave was possible. This phase moved seven onto `burp-ai-settings-sync` under one lock and the Settings-tab save onto `burp-ai-settings-save` under none, and left `MainTab.kt:119` on the EDT under none. `AgentSettingsRepository.save()` at `AgentSettings.kt:542` carries no `@Synchronized`, and `BottomTabsPanel.setActionsBusy` (`:111-116`) disables only `saveButton` and `restoreButton`, so every header toggle and every Settings-tab checkbox stays live for the flight. Two writers can therefore interleave inside ~107 sequential preference writes with `KEY_PRIVACY_MODE` and `KEY_CUSTOM_REDACTION_PATTERNS` a hundred keys apart. On a project whose stated core value is that the privacy controls are non-negotiable, a persisted permissive `privacyMode` beside a foreign pattern list is the worst outcome in the file. **Out of scope for SC4's literal text** — an interleave does not block the EDT — but it is a phase-introduced hazard, and the queue's own "stated honestly" block naming only `MainTab.kt:111` while omitting the larger writer is what turns a recorded residual into an invisible one. The cheap interim is `@Synchronized` on `save()`; both `MainTab` and `SettingsPanel` hold separate repository instances over the same `api.persistence().preferences()`.
- **CR-03 is real.** `applyIfCurrent` drops the entire superseded lambda, and 23-06 deliberately made the lambdas heterogeneous, so a newer `persistSettings` winning over an older `persistSettingsAndApplyMcp` persists the snapshot and never runs the MCP stop. `ReentrantLock()` at `:51` is the non-fair constructor, and with generation 0 parked inside a ten-second `stop()` that is a wide window for two more clicks to queue and the wrong one to win. `twoApplyBodiesNeverOverlapAndRunInSubmissionOrder` never has two waiters at once, so it cannot see this. **Out of scope for SC4's literal text** — the criterion is that the EDT does not block, and it does not — but I want to be exact about why this is not a gap rather than merely asserting it: on the ordinary single-click path the stop runs, off the EDT, every time; the drop needs a second submission to arrive during the first's flight. That makes it a defect in the mechanism, not a failure of the criterion. Its user-visible consequence — MCP off everywhere in the UI and a listener still on `127.0.0.1` — is severe enough that I folded a socket check into human item 5 so a field run can catch it.

**On the first review's targets.** CR-04 is closed: every sink on the path to the single `invokeLater` is wrapped, the `dispatchedObserver` asymmetry is argued rather than accidental, and `OffEdtDispatchFailurePathTest` covers each limb (4 tests, green). CR-05 is closed at the door that matters — the entry guard is first, `toolsBtn` goes inert, and `updateChatAvailability` now respects `isSending` so the 1 Hz tick cannot restore S0 — with the `/tool` door and `inputArea` left inconsistent (WARN-6). CR-03/`D-23-04-1` (`clearChatState()`) remains the deliberately deferred residual it was, recorded twice with the open UI question stated verbatim.

**What I checked that the artifacts get wrong.** The corrected `assertEdt()` fact holds: 6 occurrences = 1 declaration + 4 invocations + 1 comment, and both my count and the suite's own `occurrencesOf` read raw source with no comment filter, so the destructive house-filter value of 5 never enters the evidence. `SettingsPersistQueueTest.codeLinesOf` **does** strip comments, and that is correct there and only there — its ledger KDoc deliberately reproduces the very tokens it counts, so an unfiltered count would read high against a correct implementation. Two different files, two different right answers, and both are argued in place.

---

_Verified: 2026-08-21T11:05:00Z_
_Verifier: Claude (gsd-verifier) — re-verification after gap closure_
