---
phase: 23-edt-confinement-ui-responsiveness
verified: 2026-08-21T09:40:00Z
status: gaps_found
score: 5/6 must-haves verified
behavior_unverified: 0
overrides_applied: 0
gaps:
  - truth: "SC4 — Saving Settings with MCP enabled→disabled does not block the EDT on KtorMcpServerManager.stop()'s bounded 10-second wait. settingsRepo.save() and backends.reload() are likewise not blocking the EDT."
    status: partial
    reason: >-
      The Save-settings button path is genuinely fixed and I proved it discriminating with a red probe.
      But SC4's named scenario — MCP enabled→disabled paying stop()'s bounded future.get(10, SECONDS)
      on the EDT — is still fully reachable from the Settings tab through two other affordances that the
      phase did not close, and one of them is the phase's own second declared caller of the async save
      path. `restoreDefaultsWithConfirmation` (SettingsPanelActions.kt:105) calls `applySettingsToUi(defaults)`
      ON THE EDT before dispatch; `applySettingsToUi` (SettingsPanelSettingsIO.kt:264) unconditionally
      invokes `onMcpEnabledChanged` at :418, which MainTab.kt:446 wires to `settingsRepo.save(updated)`
      (:453) plus `mcpSupervisor.applySettings(...)` (:454). `defaultMcpSettings().enabled = false`
      (AgentSettings.kt:1157), so `McpSupervisor.applySettings` takes its `if (!settings.enabled) { stop() }`
      branch (McpSupervisor.kt:99) → `serverManager.stop{}` → `future.get(10, TimeUnit.SECONDS)`
      (KtorMcpServerManager.kt:270) — on the EDT, before any worker is started. The same chain fires from
      the MCP-enabled ToggleSwitch inside the Settings tab (SettingsPanelInit.kt:257) and from the header
      mcpToggle (MainTab.kt:479-488). Worse, SettingsSaveAsyncTest.kt:417 actively PINS the defect-causing
      ordering as correct — `assertTrue(applyToUi in 0 until dispatch, "Rule T-3: applySettingsToUi writes
      Swing, so it stays on the EDT.")` — treating a call that reaches disk I/O and a bounded 10-second
      server stop as if it were only "component writes". SettingsSaveAsyncTest is structurally blind to
      this because its fixture builds a bare SettingsPanel with no MainTab wiring, so the three
      onXxxChanged callbacks are null throughout the suite.
    artifacts:
      - path: "src/main/kotlin/com/six2dez/burp/aiagent/ui/SettingsPanelActions.kt"
        issue: "restoreDefaultsWithConfirmation:105 runs applySettingsToUi(defaults) on the EDT; that call is not confined to component writes."
      - path: "src/main/kotlin/com/six2dez/burp/aiagent/ui/SettingsPanelSettingsIO.kt"
        issue: "applySettingsToUi:418-420 unconditionally fires three host callbacks that perform disk I/O and a bounded 10s MCP stop on the calling (EDT) thread."
      - path: "src/main/kotlin/com/six2dez/burp/aiagent/ui/MainTab.kt"
        issue: "Untouched by the phase. :453-454 and :487-488 call settingsRepo.save() + mcpSupervisor.applySettings() directly on the EDT. Seven EDT settingsRepo.save() sites remain (:168, :453, :467, :475, :487, :502, :511)."
      - path: "src/test/kotlin/com/six2dez/burp/aiagent/ui/SettingsSaveAsyncTest.kt"
        issue: "restoreDefaultsConfirmsBeforeDispatchAndReportsFromTheCallback:417 asserts the defect-causing ordering is correct; newFixture() never installs the MainTab callbacks, so no test in the suite can observe the EDT block."
    missing:
      - "Move the onMcpEnabledChanged / onPassiveAiEnabledChanged / onActiveAiEnabledChanged notifications out of the EDT-confined applySettingsToUi body (e.g. fire them from applyAndSaveSettingsAsync's onEdt tail, or have applySettingsToUi take a suppressNotifications flag for the restore-defaults call)."
      - "Route MainTab's own mcpSupervisor.applySettings(...) calls (:454, :488) off the EDT, or gate them behind the same busy seam so the header/checkbox toggles cannot pay the 10s stop on the EDT either."
      - "Extend SettingsSaveAsyncTest's fixture to install a MainTab-shaped onMcpEnabledChanged that blocks, so the restore-defaults path has a seam that would go red. Today the suite cannot fail for this."
      - "Correct SettingsSaveAsyncTest.kt:417's assertion message and intent so it no longer certifies applySettingsToUi as EDT-safe 'component writes'."
deferred: []
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
  - test: "SC4 gap — restore defaults with the MCP server running"
    expected: "Confirm the freeze: with MCP enabled and the server Running, click Restore defaults in the Settings tab and time the UI freeze before the confirmation completes. Expect up to 10 seconds of frozen UI. Repeat by unchecking 'Enable MCP server' in the MCP tab without pressing Save."
    why_human: "Requires a live Burp with a running Ktor MCP server to pay the real future.get(10, SECONDS); no headless seam exists because SettingsSaveAsyncTest's fixture omits the MainTab wiring."
---

# Phase 23: EDT Confinement & UI Responsiveness Verification Report

**Phase Goal:** The Burp UI stays responsive during an agent tool chain and during a Settings save.
**Verified:** 2026-08-21T09:40:00Z
**Status:** gaps_found
**Re-verification:** No — initial verification

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| SC1 | `McpToolExecutor.executeTool` never invoked on the EDT from the chat tool-chain path, asserted by a test that does not rely on `-ea` | ✓ VERIFIED | Chain call site is `work = { McpToolExecutor.executeTool(...) }` inside `OffEdtDispatch.run` (`ChatPanel.kt:3078-3085`). The door guard is `check(!SwingUtilities.isEventDispatchThread())` — Kotlin `check`, active with assertions off — as the **first** statement of `executeToolResult` (`McpToolExecutorImpl.kt:159-165`). I ran `./gradlew edtGuardWithoutAssertionsTest` (jvmArgs `-da`) myself: 3 tests, 0 failures. `anApprovedChainToolExecutesOnANamedDaemonThread` captures the real `Thread` from a deep-stub Montoya answer and asserts not-EDT + daemon + name prefix `burp-ai-tool`. |
| SC2 | `api.http().sendRequest(...)` inside MCP tools and `runBlocking { manager.callTool(...) }` both execute off the EDT | ✓ VERIFIED | The guard precedes `canonicalToolId` and the `if (resolvedName.startsWith("ext:")) return routeExternalToolCall(...)` early return (`McpToolExecutorImpl.kt:168-170`), so `runBlocking { manager.callTool(...) }` at `:1149` is inside the guarded region. `executeTool` (`:1075`) is a thin wrapper over `executeToolResult`, and the MCP-server path (`McpToolHandlers.kt:129`) calls `executeToolResult` directly — one door, all callers. `McpToolExecutorEdtGuardTest.theGuardPrecedesTheExternalToolEarlyReturn` distinguishes the two candidate placements; `theSameCallOffTheEdtReachesPastTheGuard` is a real negative control that rejects an unconditional throw. `aToolThatRefusesTheEdtCompletesNormally` uses run status `"ok"` (not mere completion) as the discriminator, correctly noting `McpTool.runTool` converts an EDT refusal into an error *result*. |
| SC3 | An 8-iteration chain with a slow tool leaves the UI repainting; results arrive in order and land on the EDT | ✓ VERIFIED | `theEdtRunsQueuedWorkWhileAToolCallIsMidFlight` is a mutual-latch handshake, not a stopwatch: the tool double blocks on a runnable the test queues to the EDT *after* the tool was entered, so a blocked EDT is a categorical deadlock. **I confirmed it discriminates** (red probe below). `aFullAutoChainProducesEightResultsInSubmissionOrder` pins `MAX_AUTO_TOOL_ITERATIONS == 8` in its own assertion, awaits 8 settled workers, then asserts `decisionsFor(chainTraceId).map { it["step"] } == ["1".."8"]` — selected by trace id, never by list position — and asserts the panel ends in S0. Results land on the EDT through `OffEdtDispatch`'s single `invokeLater` → `finishApprovedToolCall` → `captured.panel.addMessage(...)` (`ChatPanel.kt:3209`). |
| SC4 | Saving Settings with MCP enabled→disabled does not block the EDT on `KtorMcpServerManager.stop()`'s bounded 10s wait; `settingsRepo.save()` and `backends.reload()` likewise off the EDT | ✗ FAILED | **Save-button path is fixed:** `saveSettings()` reads `currentSettings()` on the EDT then dispatches `applyAndSaveSettingsBody` (which contains `settingsRepo.save`, `backends.reload`, `supervisor.applySettings`, `mcpSupervisor.applySettings`) onto `burp-ai-settings-save`. Proved discriminating by red probe. **But the named scenario is still live:** `restoreDefaultsWithConfirmation:105` → `applySettingsToUi(defaults):418` → `onMcpEnabledChanged(false)` → `MainTab.kt:453-454` → `mcpSupervisor.applySettings(disabled)` → `McpSupervisor.stop():99` → `KtorMcpServerManager.kt:270 future.get(10, TimeUnit.SECONDS)` — **all on the EDT, before dispatch**. Same chain from the Settings-tab MCP toggle (`SettingsPanelInit.kt:257`) and the header toggle (`MainTab.kt:479`). See Gaps Summary. |
| SC5 | No regression in REL-01 EDT confinement for `ChatPanel` session maps | ✓ VERIFIED | Diffed against pre-phase HEAD `2a0c703`: `assertEdt()` body is byte-identical (`assert(SwingUtilities.isEventDispatchThread())` + the same REL-01 message), occurrence count unmoved at **6** — 1 declaration (`:810`), 4 invocations (`:1027`, `:1455`, `:2545`, `:2755`), 1 comment (`:2544`) — matching the corrected fact, not the "six call sites" the earlier artifacts state. `SwingUtilities.invokeLater` count unmoved at **11** (baseline 11): the phase added zero marshalling points to `ChatPanel.kt` because all of them route through `OffEdtDispatch`'s single tail. All 3 `work = { … }` lambdas read only EDT-captured locals (`captured.context`, `invocation`/`args`/`context`, `toolName`/`argsJson`/`context`); none references any of the 5 `@GuardedBy("EDT")` maps. Behavioural half `theToolTailWritesIntoItsCapturedPanelWhileTheGuardedMapsMoveUnderneathIt` failed under my red probe, so it discriminates. |
| SC6 | `MainTab`'s existing `Thread { … } + SwingUtilities.invokeLater` idiom reused, no new concurrency idiom | ✓ VERIFIED | `OffEdtDispatch.run` ends `Thread(body, threadName).apply { isDaemon = true }.start()` and marshals through exactly one `SwingUtilities.invokeLater` — structurally identical to `MainTab.kt:192-215`'s health-check block, plus the explicit `isDaemon` that idiom omits. Grep for `SwingWorker`, `ExecutorService`, `Executors.`, `CompletableFuture`, `runBlocking`, `GlobalScope`, `CoroutineScope`, `launch {` across the whole `ui/` package returns **zero** matches. |

**Score:** 5/6 truths verified (0 present, behavior-unverified)

### Phase-Blocking Gates

| Gate | Command | Result | Status |
|------|---------|--------|--------|
| New suites execute under the PR-gate filter | `JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test -PexcludeHeavyTests=true` | exit 0. `ChatPanelEdtConfinementTest` tests=17 failures=0; `McpToolExecutorEdtGuardTest` tests=3 failures=0; `SettingsSaveAsyncTest` tests=7 failures=0. **Control:** `ChatPanelConcurrencyTest` has no result file in the same run, so the filter demonstrably WAS applied. 106 suites total. | ✓ PASS |
| detekt baseline unmoved | `git diff --stat detekt-baseline.xml` · `grep -c '<ID>'` | Empty diff; 1096 entries. The one new finding (`LargeClass` on the 17-scenario `ChatPanelEdtConfinementTest`) was answered with an inline `@Suppress` carrying its reason, not a regenerated baseline. | ✓ PASS |
| Static analysis clean | `JAVA_HOME=$(…21) ./gradlew ktlintCheck detekt` | exit 0 | ✓ PASS |

### Anti-Vacuity Red Probe

This phase has a measured history of tests going green against a fully present defect, so I did not take "the test exists and passes" as evidence. I applied a targeted **offload-and-block** probe to the one file every path funnels through — the exact anti-pattern `OffEdtDispatch`'s own KDoc names (`MontoyaHttpTransport.execute` offloads then blocks) — by changing the dispatch statement to `.also { it.start(); it.join() }`, then ran the two acceptance suites.

**Result: 9 of 24 tests failed.** Including precisely the criterion-bearing ones:

| Test | Criterion | Failed at | Meaning |
|------|-----------|-----------|---------|
| `theEdtRunsQueuedWorkWhileAToolCallIsMidFlight` | SC3 | `ChatPanelEdtConfinementTest.kt:230` | The `status == "ok"` clause caught a blocked EDT. Not vacuous. |
| `theEdtIsFreeWhileASettingsSaveIsInFlight` | SC4 (save path) | `SettingsSaveAsyncTest.kt:138` | The `edtWasFree` handshake clause caught it. Not vacuous. |
| `theSnapshotIsTakenOnTheEdtAndTheBannerIsWrittenFromTheCallback` | SC4 | `SettingsSaveAsyncTest.kt:184` | — |
| `theToolTailWritesIntoItsCapturedPanelWhileTheGuardedMapsMoveUnderneathIt` | SC5 | `:1164` | — |
| `cancellingARunningToolDiscardsItsResultAndStillAuditsTheCall` | REL-05 audit | `:350` | — |
| `deletingASessionSupersedesItsRunningToolAndStillAuditsTheCall` | 23-04 | `:509` | — |
| `aProjectChangeSupersedesTheRunningToolAndNoWriteReachesTheDisposedPanel` | 23-04 | `:588` | — |
| `aSupersededConfirmEachCallReachesBurpExactlyOnce` | 23-04 | `:769` | — |
| `aThrowingToolBodyIsReportedRatherThanSwallowed` (via count) | 23-04 | — | — |

The probe was reverted; `git status` shows `OffEdtDispatch.kt` clean. **The SC1/SC2/SC3/SC5 test evidence discriminates.** The SC4 *save-button* evidence discriminates; the SC4 *restore-defaults* evidence does not exist at all (see Gaps).

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `src/main/kotlin/…/ui/OffEdtDispatch.kt` | Named daemon thread, Throwable-safe body, one `invokeLater`, dispatch + settle observers | ✓ VERIFIED | 113 lines. Both `@Volatile` observers present; `dispatchedObserver` fired as `run()`'s first statement on the calling thread (correct — a settle-record read cannot prove a worker never started). Imported and used at 4 sites. |
| `src/test/kotlin/…/ui/ChatPanelEdtConfinementTest.kt` | SC1/SC2/SC3/SC5/SC6 acceptance suite, name survives the `-PexcludeHeavyTests` filter | ✓ VERIFIED | 1791 lines, 17 scenarios, executed and green under the PR-gate filter. |
| `src/test/kotlin/…/ui/ChatPanelTestHarness.kt` | `awaitToolSettled` / `settledLabels` / `dispatchedLabels` / install+release observers | ✓ VERIFIED | 309 lines; all five members present and used. |
| `src/test/kotlin/…/mcp/tools/McpToolExecutorEdtGuardTest.kt` | S-10: throws with `-ea` off, incl. the `ext:` variant | ✓ VERIFIED | 157 lines, 3 tests, green under both `tasks.test` (`-ea`) and `edtGuardWithoutAssertionsTest` (`-da`). Carries a real negative control. |
| `src/test/kotlin/…/ui/SettingsSaveAsyncTest.kt` | S-11 + FLAG-23-06 | ⚠️ PARTIAL | 540 lines, 7 tests, green. The save-path scenarios are strong. But `newFixture()` builds a bare `SettingsPanel` with no MainTab wiring, so all three `onXxxChanged` callbacks are null and the restore-defaults EDT block is structurally unobservable to this suite — and `:417` pins the ordering that causes it. |
| `.planning/…/23-HUMAN-UAT.md` | 4 live-Burp items with instructions and pass conditions | ✓ VERIFIED | 4 items, all `pending`, each with test / expected / why_human. Contains `FLAG-23-01`. |
| `.planning/…/deferred-items.md` | Recorded residuals | ✓ VERIFIED | D-23-04-1 (Clear Chat supersede) recorded with severity and suggested home. |

### Key Link Verification

| From | To | Via | Status |
|------|----|-----|--------|
| `ChatPanel.executeApprovedToolCall` | `OffEdtDispatch` | `OffEdtDispatch.run(threadName = "burp-ai-tool-exec", label = traceId, work = { McpToolExecutor.executeTool(...) })` at `:3078-3086`, returning `ToolCallOutcome.EXECUTING` | ✓ WIRED |
| `ChatPanel.openToolDialog` | `OffEdtDispatch` | `:1115-1132`, `ToolCallOrigin.UserDialog` | ✓ WIRED |
| `ChatPanel.handleToolCommand` `/tool` branch | `OffEdtDispatch` | `:2515-2528`, `ToolCallOrigin.UserSlashCommand`, with the Rule S-4 `panel.addMessage("You", trimmed)` echo at `:2509` preceding it | ✓ WIRED |
| `McpToolExecutorImpl.executeToolResult` | `javax.swing.SwingUtilities` | `check(!SwingUtilities.isEventDispatchThread())` as the first statement, `:159` | ✓ WIRED |
| `SettingsPanelSettingsIO.applyAndSaveSettingsAsync` | `OffEdtDispatch` | `:564`, `threadName = "burp-ai-settings-save"`, two-layer `finally` lowering an `AtomicBoolean` compare-and-set seam | ✓ WIRED |
| `BottomTabsPanel` | `SettingsPanel` | `settingsPanel.setBusyListener { busy -> setActionsBusy(busy) }` at `:94`, immediately after `setDialogParent(root)` | ✓ WIRED (narrow — see WARN-3) |
| `ChatPanel.deleteSession` | `RunningToolTracker` | `if (runningTool.take() != null) setSendingState(false)` at `:932`, beside the existing `resolvePending` | ✓ WIRED |
| `ChatPanel.clearChatState` | `RunningToolTracker` | — | ✗ NOT WIRED (recorded residual D-23-04-1 / CR-03) |
| `ChatPanelTestHarness` | `OffEdtDispatch` | `registerSettledObserver` / `registerDispatchedObserver` | ✓ WIRED |
| `edtGuardWithoutAssertionsTest` | any CI workflow | — | ✗ NOT WIRED (WR-11 — see WARN-1) |

### Requirements Coverage

| Requirement | Source Plans | Description | Status | Evidence |
|-------------|--------------|-------------|--------|----------|
| REL-05 | 23-01, 23-02, 23-03, 23-04, 23-05 (all five) | "No MCP tool execution, backend HTTP call, or `runBlocking` on an external MCP server happens on the Swing EDT; the auto tool-chain (up to 8 iterations) leaves the UI responsive throughout. Saving Settings does not block the EDT on `serverManager.stop()`'s 10-second bounded wait." | ⚠️ PARTIALLY SATISFIED | Clauses 1 and 2 satisfied (SC1/SC2/SC3). Clause 3 is not: restore-defaults and both MCP toggles still pay `future.get(10, TimeUnit.SECONDS)` on the EDT. `REQUIREMENTS.md:28` is already marked `[x]`; that checkbox is premature and should be reverted until the SC4 gap closes. |

**Orphan check:** `REQUIREMENTS.md:50` maps REL-05 → Phase 23 and no other requirement ID maps to Phase 23. All five plans declare `requirements: [REL-05]`. **No orphaned requirements.**

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| — | — | `TBD` / `FIXME` / `XXX` in phase-touched files | — | None found. Debt-marker gate passes. |
| `OffEdtDispatch.kt` | 93-95 | Unguarded `logError` between the failed work and `SwingUtilities.invokeLater` | ⚠️ Warning | CR-04, confirmed at source. If the error sink throws, the entire EDT tail is skipped: no busy clear, no settle observer, panel stranded in S3. `applyAndSaveSettingsAsync` defends against this with its own outer layer; the three `ChatPanel` call sites do not. |
| `ChatPanel.kt` | 812-816 | `setSendingState` does not touch `toolsBtn`; `toolsBtn.isEnabled = mcpAvailable` (`:346`) is its only gate | ⚠️ Warning | CR-05, confirmed at source. Falsifies 23-01's own must-have truth "S3 is global to ChatPanel, so a second dispatch cannot be started from the UI while the first is live". A second dialog tool overwrites `runningTool`, and the first worker's result is silently discarded. |
| `ChatPanel.kt` | 1232-1263 | `clearChatState()` resolves pending but never takes the running-tool token | ⚠️ Warning | CR-03 / D-23-04-1. Recorded residual, not a hidden gap. Its own comment reads "Teardown path 3 of 5". |
| `App.kt` / `SettingsPanelActions.kt` | 220 / 146-151 | `SettingsPanel.shutdown()` stops two Swing timers only; no supersede for an in-flight `burp-ai-settings-save` worker | ⚠️ Warning | CR-01, confirmed at source. A worker that reaches `mcpSupervisor.applySettings(enabled=true)` after `App.shutdown()`'s `mcpSupervisor.shutdown()` restarts the MCP server post-unload. A defect the offload created. |

None of these four is a blocker for SC1–SC6, but the first three all live in code this phase wrote.

### Human Verification Required

See the `human_verification` frontmatter block. Four items are already recorded in `23-HUMAN-UAT.md` (all `pending`); I have added a fifth to confirm the SC4 freeze against a live Burp with a running Ktor MCP server.

## Gaps Summary

**One blocking gap: SC4 is met for the Save button and not for the Settings tab.**

The phase did the hard, correct thing at the two doors it named. `executeToolResult` refuses the EDT with a `check` that fires without `-ea` — I confirmed this myself by running the `-da` task rather than trusting the SUMMARY — and it sits *above* the `ext:` early return, so `runBlocking { manager.callTool(...) }` is covered by the same one-line placement. All three `ChatPanel` `executeTool` sites go through one dispatch helper that reuses `MainTab`'s existing `Thread` + `invokeLater` idiom and introduces no new concurrency layer. REL-01's confinement contract is provably unmoved: `assertEdt()` is byte-identical to pre-phase HEAD, its 6 occurrences are unmoved, and the `invokeLater` count in `ChatPanel.kt` is still 11 because everything routes through the helper's single tail. The tests are unusually careful and — verified by red probe, not by reading their KDoc — they discriminate.

The gap is that SC4's scenario was closed at one door and left open at three. `applyAndSaveSettingsBody` genuinely runs on `burp-ai-settings-save`, but `restoreDefaultsWithConfirmation` — the phase's own second declared caller, put on the same async path by D-13 — calls `applySettingsToUi(defaults)` on the EDT *before* dispatching. That function is not a component-writer. Its last three statements fire host callbacks, and `MainTab` wires `onMcpEnabledChanged` to `settingsRepo.save()` plus `mcpSupervisor.applySettings()`. Because `defaultMcpSettings().enabled` is `false`, restoring defaults with the server running takes `McpSupervisor`'s `if (!settings.enabled) { stop() }` branch straight into `KtorMcpServerManager`'s `future.get(10, TimeUnit.SECONDS)` — on the EDT, in a phase whose entire reason for existing is that this call must not run there. The same chain fires when a user simply unchecks "Enable MCP server" in the MCP settings tab, and again from the header toggle.

Two things make this a gap rather than a nitpick. First, it is the literal text of the success criterion — "MCP enabled→disabled", "`KtorMcpServerManager.stop()`'s bounded 10-second wait" — reachable from a button in the Settings tab. Second, the phase's own test does not merely miss it; `SettingsSaveAsyncTest.kt:417` **certifies it**, asserting `applyToUi in 0 until dispatch` with the justification "applySettingsToUi writes Swing, so it stays on the EDT". That is the vacuity pattern this phase's history warns about, in its one purely structural assertion: the suite's fixture builds a bare `SettingsPanel` whose three `onXxxChanged` callbacks are null, so no test in it could ever observe what those callbacks do in production.

`MainTab.kt` is byte-identical to pre-phase HEAD, so the EDT calls there are inherited rather than introduced. That mitigates blame, not the criterion.

**On the review's other Critical findings, judged independently:**

- **CR-01 (no supersede on unload)** — real, confirmed at source, and a defect the offload created. Out of scope for SC1–SC6; it is a correctness-during-teardown issue, not a responsiveness one. Should not block the phase but should not be lost either.
- **CR-02 (MainTab still saves on the EDT)** — real. The torn-snapshot half is out of scope. The *EDT-blocking* half is in scope for SC4 and is folded into the gap above; the review framed CR-02 as a concurrency problem and did not name the `restore-defaults → applySettingsToUi → 10s stop` chain, which is the sharper consequence.
- **CR-03 (Clear Chat)** — real and confirmed, but it is the pre-recorded residual D-23-04-1, documented with severity and a suggested home. Known and recorded, not hidden. Out of scope for SC1–SC6.
- **CR-04 (`OffEdtDispatch:92-95`)** — real. Narrow (requires the error sink itself to throw) and out of scope for the six criteria, but it sits in the one file whose stated purpose is a marshalling point that cannot be bypassed, and the Settings path already defends against exactly this shape while the three chat paths do not.
- **CR-05 (`openToolDialog` busy guard)** — real. Out of scope for SC1–SC6, but it directly falsifies a 23-01 PLAN must-have truth, and it is the reachable supersede door WR-09 correctly notes the suite never exercises.
- **WR-11 (`edtGuardWithoutAssertionsTest` in no CI workflow)** — confirmed: `build.yml:47`, `release.yml:33` and `nightly-regression.yml:26` run neither it nor anything with `-da`. SC1 holds **today** — I executed the task and it is green — but nothing in CI would catch a future revert of `check(...)` to `assert(...)`, because the fast gate runs with `-ea` where an `assert` is also green. SC1 is verified; its durability is not gated.

**What does not need re-litigating:** the detekt baseline is untouched at 1096, `ktlintCheck` and `detekt` pass, and all three new suites really do execute under `-PexcludeHeavyTests=true` with the `ChatPanelConcurrencyTest` absence as the control that makes those counts mean something. Both phase-blocking gates pass.

---

_Verified: 2026-08-21T09:40:00Z_
_Verifier: Claude (gsd-verifier)_
