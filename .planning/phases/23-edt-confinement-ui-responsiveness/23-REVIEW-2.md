---
phase: 23-edt-confinement-ui-responsiveness
reviewed: 2026-08-21T00:00:00Z
depth: standard
files_reviewed: 16
files_reviewed_list:
  - src/main/kotlin/com/six2dez/burp/aiagent/ui/SettingsPersistQueue.kt
  - src/main/kotlin/com/six2dez/burp/aiagent/ui/MainTab.kt
  - src/main/kotlin/com/six2dez/burp/aiagent/ui/SettingsPanel.kt
  - src/main/kotlin/com/six2dez/burp/aiagent/ui/SettingsPanelActions.kt
  - src/main/kotlin/com/six2dez/burp/aiagent/ui/SettingsPanelSettingsIO.kt
  - src/main/kotlin/com/six2dez/burp/aiagent/ui/ChatPanel.kt
  - src/main/kotlin/com/six2dez/burp/aiagent/ui/OffEdtDispatch.kt
  - src/test/kotlin/com/six2dez/burp/aiagent/ui/SettingsPersistQueueTest.kt
  - src/test/kotlin/com/six2dez/burp/aiagent/ui/SettingsSaveAsyncTest.kt
  - src/test/kotlin/com/six2dez/burp/aiagent/ui/ChatPanelEdtConfinementTest.kt
  - src/test/kotlin/com/six2dez/burp/aiagent/ui/OffEdtDispatchFailurePathTest.kt
  - src/test/kotlin/com/six2dez/burp/aiagent/ui/ChatPanelTestHarness.kt
  - build.gradle.kts
  - .github/workflows/build.yml
  - .github/workflows/nightly-regression.yml
  - CLAUDE.md
findings:
  critical: 3
  warning: 8
  info: 0
  total: 11
status: issues_found
---

# Phase 23 GAP CLOSURE: Code Review Report

**Reviewed:** 2026-08-21
**Depth:** standard
**Scope:** `cb60e32..HEAD` (plans 23-06, 23-07, 23-08) only
**Files Reviewed:** 16
**Status:** issues_found

## Summary

Verdict on the four findings this work set out to close:

| Prior finding | Status |
|---|---|
| **CR-04** (throwing sink kills the EDT tail) | **Closed.** Every sink on the path to `invokeLater` is wrapped, the dispatched observer is deliberately left bare, and `OffEdtDispatchFailurePathTest` proves each limb separately with a real negative control. Nothing to add. |
| **CR-05** (`openToolDialog` re-entrancy) | **Closed at the door that matters.** The entry guard is the first statement, `toolsBtn` goes inert, and `updateChatAvailability` now respects `isSending` so the 1 Hz tick cannot restore S0. The second door (`handleToolCommand`'s `/tool` branch) was left unguarded — see WR-05. |
| **CR-01** (save worker outliving unload) | **Closed for the `burp-ai-settings-save` worker, and re-opened for a NEW one.** The three sequential `isCurrent()` guards are correctly placed and — unusually for this phase — genuinely falsifiable, because each shape parks the worker *past* the preceding guard. But 23-06 introduced a second worker (`burp-ai-settings-sync`) whose apply body has no supersede at all, and `23-06-PLAN.md` records `T-23-06-06` as "fully mitigated in plan 23-08". It is not. See **CR-01** below. |
| **CR-02** (concurrent settings writers / torn 106-key snapshot) | **NOT closed.** The queue serialises `MainTab`-vs-`MainTab` writes. The Settings tab's own **Save settings / Restore defaults** worker — the only writer that carries `customRedactionPatterns` from the text area — writes all 106 preference keys and calls `mcpSupervisor.applySettings` entirely outside the queue's lock, and nothing gates the header toggles while it runs. See **CR-02** below. |

The queue itself is well built: generations minted on the calling thread, `applied` advanced before the body so a throwing apply cannot be replayed, `dispose()` explicitly lock-free with a stated reason. Two things are wrong with it. First, the "Scope of that claim, stated honestly" block names exactly one residual (`MainTab.kt:111`) and omits the larger one — a stated-honestly section that is materially incomplete is worse than no section, because the next reader stops looking. Second, dropping a superseded generation drops the *whole* apply lambda, and 23-06 deliberately made those lambdas heterogeneous (`persistSettings` vs `persistSettingsAndApplyMcp`), so a supersede can silently lose an MCP stop/start.

The tests are the strongest in the phase so far. `SettingsPersistQueueTest` and `OffEdtDispatchFailurePathTest` both pass locally (`./gradlew test --tests '*SettingsPersistQueueTest' --tests '*OffEdtDispatchFailurePathTest'` — BUILD SUCCESSFUL); `detekt` and `ktlintCheck` are clean. I found **no vacuous assertion** in the new suites — the CR-01 two-shape design in particular is exactly the right answer to the "supersede lands before guard 1 makes guards 2 and 3 unreachable" trap. One order-dependent assertion is flaky rather than vacuous (WR-06), and one control guards code with no production caller (WR-03).

The CI wiring runs on all three OSes and cannot silently no-op — but the step as written also drags the entire `:test` task in behind it *without* `-PexcludeHeavyTests=true`, so the PR gate now runs the heavy suites it was built to exclude. Verified with `--dry-run` (WR-01).

Deliberately not re-reported, per scope: `KtorMcpServerManager.stop()` staying blocking (D-14), `clearChatState()` not superseding (D-23-04-1 / D-23-07-1), `assertEdt()` (Phase 26), and `MainTab.kt:111` (D-23-06-1 / T-23-06-08).

## Structural Findings (fallow)

No structural pre-pass was supplied with this review. All findings below are narrative.

## Narrative Findings (AI reviewer)

### Critical Issues

---

### CR-01: The persist queue's apply body has no supersede, so `T-23-06-06` is recorded as mitigated while a post-unload MCP listener is still reachable

**File:** `src/main/kotlin/com/six2dez/burp/aiagent/ui/SettingsPersistQueue.kt:97-119`, `src/main/kotlin/com/six2dez/burp/aiagent/ui/MainTab.kt:584-608`, `:920-928`

**Issue:** `dispose()` sets a flag, and `applyIfCurrent` reads it exactly once, before the body:

```kotlin
lock.withLock {
    if (disposed) return
    if (generation <= applied.get()) return
    applied.set(generation)
    apply(snapshot)          // <-- never re-checks anything
}
```

For `persistSettingsAndApplyMcp` that body is:

```kotlin
apply = {
    settingsRepo.save(it)                        // ~106 preference writes
    mcpSupervisor.applySettings(it.mcpSettings, …)  // starts or stops the Ktor server
},
```

There is no `isCurrent()` re-check between them — the exact pattern 23-08 added to `applyAndSaveSettingsBody` (`SettingsPanelSettingsIO.kt:547-549`, `:577-581`, `:596-599`) is absent from the queue's path.

The reachable sequence:

1. User ticks **Enable MCP** (header `mcpToggle` at `MainTab.kt:480-487`, or the Settings-tab checkbox at `SettingsPanelInit.kt:258` → `onMcpEnabledChanged` → `MainTab.kt:462`). Queue worker `burp-ai-settings-sync` takes the lock, passes `if (disposed)`, and starts `settingsRepo.save(...)` — 106 individual `prefs.set*` calls.
2. The user unloads the extension. `App.shutdown()` → `mainTab?.shutdown()` → `settingsPersistQueue.dispose()` — too late, the worker is already inside the body — then `App.kt:233` `mcpSupervisor.shutdown()`.
3. The worker reaches `mcpSupervisor.applySettings(enabled = true, …)`, which at `McpSupervisor.kt:96-97` re-wires **two process-global singletons** to a dead handle:

   ```kotlin
   ScannerTaskRegistry.setLogger { api.logging().logToOutput("[ScannerTaskRegistry] $it") }
   CollaboratorRegistry.setLogger { api.logging().logToOutput("[CollaboratorRegistry] $it") }
   ```

   and then starts the Ktor server. Nothing ever clears those two sinks again — `App.shutdown()` unwires `BackendDiagnostics.retry`, `Redaction.truncationLogger` and `AuditLogger`'s emitter, but not these.

The end state is verbatim CR-01's: **an MCP server listening on `127.0.0.1` owned by an unloaded extension's classloader**, with no live extension behind SEC-04's access-control checks — reached through the worker 23-06 created rather than the one 23-08 fixed.

This is not an accepted residual. `23-06-PLAN.md:566` records `T-23-06-06` (*"MCP listener started or stopped after `App.shutdown()` began"*, high) as **transfer**, with the mitigation column reading *"fully mitigated in plan 23-08 (CR-01), which adds the same supersede to the `SettingsPanel` save worker"* — but the `SettingsPanel` save worker and the persist-queue worker are two different threads running two different bodies. 23-08 fixed one and the ledger closed the threat for both. `deferred-items.md` does not carry it either.

**Fix:** Give the queue the same predicate the save body got, and re-check it before the one mutation that outlives the panel:

```kotlin
// SettingsPersistQueue
private fun applyIfCurrent(generation: Long, snapshot: AgentSettings, apply: (AgentSettings, () -> Boolean) -> Unit) {
    lock.withLock {
        if (disposed) return
        if (generation <= applied.get()) return
        applied.set(generation)
        apply(snapshot) { !disposed && generation >= applied.get() }
    }
}

// MainTab.persistSettingsAndApplyMcp
apply = { s, isCurrent ->
    settingsRepo.save(s)
    // Same guard, same reason, as SettingsPanelSettingsIO's guard 1 of 3: this is the only
    // statement here that can leave a socket LISTENING after the extension is gone.
    if (!isCurrent()) return@apply
    mcpSupervisor.applySettings(s.mcpSettings, s.privacyMode, s.determinismMode, s.toPreprocessorSettings())
},
```

Then correct `T-23-06-06`'s disposition and add the missing test: `SettingsPersistQueueTest` covers only "a write submitted *after* `dispose()` never applies" (`disposeStopsNewWorkAndDoesNotBlockTheCaller`); the in-flight case has no scenario, which is why the gap survived.

---

### CR-02: CR-02 is not closed — the Settings-tab save worker writes all 106 preference keys and applies MCP settings outside the queue's lock, and no seam stops the header toggles from racing it

**File:** `src/main/kotlin/com/six2dez/burp/aiagent/ui/SettingsPanelSettingsIO.kt:538`, `:551-556`, `src/main/kotlin/com/six2dez/burp/aiagent/ui/SettingsPersistQueue.kt:19-30`, `src/main/kotlin/com/six2dez/burp/aiagent/ui/BottomTabsPanel.kt:111-115`

**Issue:** The queue's KDoc states the torn-snapshot defect precisely and then scopes the fix:

> **Scope of that claim, stated honestly.** It covers writes submitted THROUGH this queue. `MainTab` still calls `settingsRepo.save` on the EDT from the `applySettings` lambda it hands `ChatPanel`, outside this lock — recorded as residual `D-23-06-1` and threat `T-23-06-08`, not silently absorbed.

That names one residual and omits the bigger one. `applyAndSaveSettingsBody` runs on its own `burp-ai-settings-save` worker and does:

```kotlin
settings = updated
settingsRepo.save(updated)                       // :538 — 106 prefs.set* calls, unsynchronised
…
mcpSupervisor.applySettings(updated.mcpSettings, …)  // :551 — unsynchronised
```

Neither statement touches `SettingsPersistQueue`. Measured against the current tree:

- `AgentSettings.kt:542-…` — `save()` performs **106** `prefs.set*` calls with no lock. Within that sequence `KEY_PRIVACY_MODE` is write #62 and `KEY_CUSTOM_REDACTION_PATTERNS` is write #162 — the 100-key gap the queue's own KDoc cites as the reason the lock exists.
- `McpSupervisor.applySettings` (`McpSupervisor.kt:79-…`) is a bare sequence of `AtomicReference.set` calls followed by `stop()` or a start. Still unsynchronised.
- `BottomTabsPanel.setActionsBusy` disables `saveButton` and `restoreButton` and nothing else. The header `mcpToggle` / `passiveToggle` / `activeToggle` / `backendPicker` (`MainTab.kt:480-503`, `:173-177`) and the Settings tab's **own** three checkboxes (`SettingsPanelInit.kt:258`, `:334`, `:403`) all stay live for the whole flight.

So the reachable sequence is a single tab away:

1. Click **Save settings**. Worker A begins `settingsRepo.save(updated)` — the only write path that carries the freshly typed `customRedactionPatterns`.
2. Tick **Enable MCP server** (same tab, still enabled) or any header toggle. That routes to `persistSettings*` → queue worker B → `settingsRepo.save(snapshotB)`.
3. A and B interleave inside 106 unsynchronised preference writes. The persisted file can hold `privacyMode` from one snapshot beside `customRedactionPatterns` from the other — **a permissive mode paired with a foreign pattern list, written to disk and surviving restart**, with no error and no indication to the user.

The same overlap covers `mcpSupervisor.applySettings`, and there the window is *wide*: worker A sits inside `KtorMcpServerManager`'s bounded `future.get(10, TimeUnit.SECONDS)` for up to ten seconds while worker B enters the same unsynchronised method. A's `stop()` interleaving with B's start (or the reverse) can leave the server running when the user's last action disabled it — an MCP listener the user believes they closed.

On a project whose stated core value is that the privacy controls are non-negotiable, this is the finding the queue was built for, and it is the one path the queue does not cover.

**Fix:** Route the Settings-tab save through the same queue, which is where the ordering guarantee already lives:

```kotlin
// SettingsPanel gets the MainTab queue (or MainTab constructs SettingsPanel with it)
settingsPersistQueue.submit(
    label = "settings-save",
    snapshot = updated,
    apply = { s -> applyAndSaveSettingsBody(s, isCurrent = { !disposed && saveGeneration.get() == generation }) },
    onSettled = { result -> /* existing onEdt tail */ },
)
```

If that is too large for this pass, the minimum correct interim is to make `AgentSettingsRepository.save()` `@Synchronized` (it is the shared resource; both `MainTab` and `SettingsPanel` hold separate repository instances over the same `api.persistence().preferences()`), and to synchronise `McpSupervisor.applySettings`. Either way, update the queue's scope block and `deferred-items.md` so the residual is recorded rather than implied absent.

---

### CR-03: A newer plain persist supersedes an older MCP-applying persist and silently drops the MCP stop/start

**File:** `src/main/kotlin/com/six2dez/burp/aiagent/ui/SettingsPersistQueue.kt:51`, `:108-119`, `src/main/kotlin/com/six2dez/burp/aiagent/ui/MainTab.kt:554-608`

**Issue:** `applyIfCurrent` drops a superseded generation by skipping the **entire** `apply` lambda. 23-06 deliberately made those lambdas heterogeneous — `persistSettings` is `{ save }`, `persistSettingsAndApplyMcp` is `{ save; mcpSupervisor.applySettings }` — with a documented reason (`T-23-06-07`: applying MCP settings on a scanner toggle would clear `ScannerTaskRegistry`). The supersede rule was not revisited for that asymmetry.

Consequence: when a `persistSettings` generation wins over an older `persistSettingsAndApplyMcp` generation, the persisted settings are correct (the newer snapshot carries `mcpSettings.enabled` from the already-flipped checkbox, since `currentSettings()` reads the panel after `setMcpEnabled`) — but **`mcpSupervisor.applySettings` never runs at all**. The server is left in its previous state while the persisted settings, the Settings-tab checkbox and the header toggle all say otherwise. Turn MCP *off* and this leaves the listener up on `127.0.0.1` with every UI surface reporting it disabled.

Two ways to get there, and the second needs no unlucky scheduling:

1. **Thread-start reordering.** `submit` mints the generation on the calling thread but the drop decision is taken at *lock-acquisition* time. Generation 1's worker can be descheduled between `Thread.start()` and `lock.lock()` while generation 2's worker gets there first. `SettingsPersistQueueTest.anOlderGenerationIsDroppedRatherThanAppliedOverANewerOne` already demonstrates precisely this outcome (with the reordering forced through the dispatched observer) — it asserts the older body is dropped whole.
2. **Non-fair lock grant.** `private val lock = ReentrantLock()` is the **non-fair** constructor. With generation 0 holding the lock through a ten-second MCP `stop()`, generations 1 (MCP toggle) and 2 (passive toggle) both queue behind it, and on release the JVM may grant to either. If 2 wins, `applied` jumps to 2 and generation 1's MCP apply is discarded. A ten-second window is more than enough for a user to click two more toggles.

The queue's KDoc claims *"submission order is click order rather than thread-start order"*. That is true of the *numbering*; it is not true of the *arbitration*, which is lock-grant order. `twoApplyBodiesNeverOverlapAndRunInSubmissionOrder` never has two waiters at once, so it cannot detect this.

**Fix:** Make the queue FIFO and make supersede safe for heterogeneous bodies. Cheapest correct combination:

```kotlin
// Fair lock: grant order becomes arrival order, so the "older" body is never the one that wins.
private val lock = ReentrantLock(true)
```

plus, since fairness alone still lets a *later-arriving* plain persist drop an MCP apply, either (a) make every submitted body idempotent and MCP-applying (rejected by `T-23-06-07`, so no), or (b) separate the two concerns — supersede the *settings snapshot* by generation, but always run the MCP apply of the newest snapshot:

```kotlin
apply(snapshot)                       // newest snapshot only
if (anyDroppedGenerationNeededMcp) {  // sticky flag set by submit(), cleared under the lock
    mcpSupervisor.applySettings(snapshot.mcpSettings, …)
}
```

Add a `SettingsPersistQueueTest` scenario with two waiters queued behind a held apply, asserting the MCP-applying body's side effect survives whichever generation wins.

## Warnings

---

### WR-01: The new `pr-gate` step drags the full `:test` task in behind it, and does so *without* `-PexcludeHeavyTests=true`

**File:** `.github/workflows/build.yml:54-56`, `build.gradle.kts:318-320`, `:322-328`

**Issue:** The CI wiring is right in the ways the prior review asked for — it runs on all three matrix OSes, and the task's `filter { includeTestsMatching("*McpToolExecutorEdtGuardTest") }` fails on no match, so it cannot silently no-op. But `tasks.withType<Test> { finalizedBy(tasks.named("jacocoTestReport")) }` is a live collection that also matches `edtGuardWithoutAssertionsTest`, and `jacocoTestReport` declares `dependsOn(tasks.named("test"))`. Confirmed against this tree:

```
$ ./gradlew edtGuardWithoutAssertionsTest --dry-run
:edtGuardWithoutAssertionsTest SKIPPED
:test SKIPPED
:jacocoTestReport SKIPPED
```

The preceding pr-gate step runs `./gradlew test -PexcludeHeavyTests=true`; the new step runs `./gradlew edtGuardWithoutAssertionsTest` with **no** `-PexcludeHeavyTests`. `Test.filter` is a task input, so the differing filter invalidates the earlier run and `:test` executes a second time — this time including `*IntegrationTest`, `*ConcurrencyTest`, `*BackpressureTest`, `*RestartPolicyTest` and `*SupervisionTest` (the last carries a documented 30-second coerced-timeout floor), on ubuntu, macOS **and Windows**. Those suites were excluded from the PR gate on purpose and have never been validated on the Windows or macOS runners. `jacocoTestReport` is also overwritten with coverage from the `-da` JVM.

**Fix:** Two lines, both needed:

```kotlin
// build.gradle.kts — do not finalize the -da gate with a report that depends on the -ea suite
tasks.withType<Test>().matching { it.name == "test" }.configureEach {
    finalizedBy(tasks.named("jacocoTestReport"))
}
```

```yaml
# build.yml — keep the gate's scope identical to the step above it
- run: ./gradlew edtGuardWithoutAssertionsTest -PexcludeHeavyTests=true --no-daemon
```

---

### WR-02: The WR-04 supersede record reaches one sink, not two — the KDoc's "same reach" claim contradicts itself two sentences earlier

**File:** `src/main/kotlin/com/six2dez/burp/aiagent/ui/ChatPanel.kt:1188-1198`, `:1210-1226`, compare `:3313-3341`

**Issue:** The new superseded branch emits only:

```kotlin
supervisor.aiRequestLogger?.log(type = ActivityType.MCP_TOOL_CALL, …, metadata = mapOf(…, "runStatus" to SUPERSEDED_RUN_STATUS, …))
```

The chain path it claims parity with emits **two** records: `toolDecisionReporter.report(...)`, which performs `AuditLogger.emitGlobal(MCP_TOOL_DECISION_EVENT, payload)` before returning — the durable SEC-06 record — *and then* the `aiRequestLogger` entry. The same KDoc says both of these:

> *"no `toolDecisionReporter.report(...)` call belongs in this function, because the user picked the tool themselves and there is no decision to report"*

> *"It is the same treatment [discardSupersededToolResult] gives a superseded chain step, with the same reach and the same restraint"*

Those cannot both be true. The first is the design decision; the second is an overclaim, and it is the one a maintainer will read. Practically, `AiRequestLogger` is a nullable, capped in-memory ring buffer that no-ops when disabled (`AiRequestLogger.kt:104`, `if (!enabled) return`) — so a discarded user-originated call leaves nothing on the durable audit surface at all, which is the surface D-07's argument is about.

**Fix:** Either emit a durable record for the discard (a `runStatus`-only `AuditLogger.emitGlobal` needs no approval fields), or delete the "same reach" sentence and say plainly that this record lands on the activity log only, and is lost when `aiRequestLogger` is null or disabled.

---

### WR-03: `applySettingsToUi(notifyHosts = true)` now has no production caller, and its negative-control test guards dead code from a non-EDT thread

**File:** `src/main/kotlin/com/six2dez/burp/aiagent/ui/SettingsPanelSettingsIO.kt:294-297`, `:451-455`, `src/main/kotlin/com/six2dez/burp/aiagent/ui/SettingsPanelActions.kt:118`, `src/test/kotlin/com/six2dez/burp/aiagent/ui/SettingsSaveAsyncTest.kt:482-505`

**Issue:** `applySettingsToUi` has exactly one call site in `src/main/`, and it passes `notifyHosts = false`:

```
$ grep -rn "applySettingsToUi(" src/main/
SettingsPanelActions.kt:118:    applySettingsToUi(defaults, notifyHosts = false)
SettingsPanelSettingsIO.kt:294:internal fun SettingsPanel.applySettingsToUi(
```

So the `if (notifyHosts) { … }` block at `:451-455` is unreachable in production. The control test's stated purpose — *"without it, deleting the three notifications outright would also make that test pass — and would silently break every OTHER caller of `applySettingsToUi`"* — rests on other callers that do not exist. (The three host callbacks themselves are still live via `SettingsPanelInit.kt:258/334/403`, so nothing is broken; the *parameter* is what is dead.)

Secondary: the control drives `applySettingsToUi` from a plain `Thread("apply-to-ui-driver")`, i.e. ~145 Swing component writes off the EDT, in the suite whose subject is EDT confinement.

**Fix:** Drop the `notifyHosts` parameter and the `if` block, inline the three notifications at the two `SettingsPanelInit` checkbox listeners that actually fire them, and delete `applySettingsToUiStillNotifiesHostsByDefault`. If the parameter is kept for a planned second caller, say so in the KDoc and mark the test as guarding a currently-unused branch.

---

### WR-04: `setSendingState` and `updateChatAvailability` state the input-area rule two different ways, and `sendFromInput` guards neither

**File:** `src/main/kotlin/com/six2dez/burp/aiagent/ui/ChatPanel.kt:1043` vs `:361`, `:552-579`

**Issue:** The `setSendingState` KDoc makes the point explicitly for `toolsBtn`:

> *"The `toolsBtn` write reads the FIELD rather than the parameter, so that it is textually identical to `updateChatAvailability`'s: the rule … is then one fact stated one way at both of its sites"*

The line directly above it does not follow that rule:

```kotlin
inputArea.isEnabled = !sending                    // :1043
toolsBtn.isEnabled  = mcpAvailable && !isSending  // :1044
```

versus `updateChatAvailability`:

```kotlin
toolsBtn.isEnabled  = mcpAvailable && !isSending  // :360
inputArea.isEnabled = mcpAvailable && !isSending  // :361
```

If MCP drops while a tool is running, the tail's `setSendingState(false)` re-enables the input area even though `mcpAvailable` is false, until the next 1 Hz tick corrects it. `sendFromInput` (`:552`) checks neither `mcpAvailable` nor `isSending`, so an Enter in that window runs a turn — or, via `handleToolCommand`, a `/tool` call — against a server the panel already knows is down.

**Fix:** One line, and the rule becomes true as stated:

```kotlin
inputArea.isEnabled = mcpAvailable && !isSending
```

---

### WR-05: Rule S-1's "one tool at a time, one door" is enforced at one of the two doors

**File:** `src/main/kotlin/com/six2dez/burp/aiagent/ui/ChatPanel.kt:2559-2596`, compare `:1099-1107`

**Issue:** `openToolDialog` got the entry guard. `handleToolCommand`'s `/tool` branch — the other path that mints a token and calls `runningTool.set(token)` (`:2578-2580`) — did not:

```kotlin
if (trimmed.startsWith("/tool ")) {
    …
    setSendingState(true)
    val token = RunningToolToken(toolName)
    runningTool.set(token)      // overwrites whatever is running, unconditionally
```

Today this is defence-in-depth rather than a live bug: `inputArea` is disabled whenever `isSending` is true, so the slash path is not user-reachable in S3. But the guard's own KDoc argues the affordance can be bypassed and that the *control* is what matters — and the control was installed at one door only. The panel's own suite drives the unguarded one: `ChatPanelToolGateTest.aToolSupersededByASlashCommandRefundsNoChainIterationEither` calls `ChatPanelTestHarness.sendUserMessage` mid-flight, which assigns text to a disabled `JTextArea` and clicks an invisible button — a path that is now *more* divergent from the shipped UI than before this change, since `toolsBtn` is also inert.

**Fix:** Hoist the guard into `sendFromInput`, ahead of `handleToolCommand`, so both doors share it:

```kotlin
private fun sendFromInput() {
    if (isSending) { showError("A request is already running. Cancel it first."); return }
    …
}
```

and make `sendUserMessage` assert `send.isVisible && input.isEnabled` before clicking, so a test driving an impossible state fails loudly.

---

### WR-06: `aThrowingSettleObserverDoesNotEscapeTheTail` asserts an ordering nothing guarantees, on a process-global observer

**File:** `src/test/kotlin/com/six2dez/burp/aiagent/ui/OffEdtDispatchFailurePathTest.kt:186-216`

**Issue:**

```kotlin
assertEquals(listOf(FIRST_LABEL, SECOND_LABEL), seen.toList(), "Both dispatches must settle, in dispatch order.")
```

The two dispatches are two independently created `Thread`s (`OffEdtDispatch.kt:127`), each posting its own `invokeLater` from its own thread. Nothing establishes a happens-before between worker 1 reaching `invokeLater` and worker 2 reaching it — only their `Thread.start()` calls are ordered, and thread start-to-run latency is unbounded. Both `work` bodies are trivial (`{ "first" }`, `{ "second" }`), so the whole race is decided inside a few microseconds of scheduler jitter. On a loaded Windows or macOS runner this can invert. The test's own claim ("dispatch order") is not a property `OffEdtDispatch` documents or provides.

Compounding it, `settledObserver` is process-global on an `object`, and this test's observer both records labels and counts down `bothSettled`. A worker leaked by an earlier class settling here contributes a foreign label (false red on the `assertEquals`) or consumes a `countDown` (the await passes before this test's own second dispatch has settled).

**Fix:** Assert set membership rather than sequence, and select by label rather than by count:

```kotlin
assertEquals(setOf(FIRST_LABEL, SECOND_LABEL), seen.filter { it == FIRST_LABEL || it == SECOND_LABEL }.toSet())
```

with `bothSettled` counted down only for those two labels.

---

### WR-07: `submit` creates one unbounded OS thread per click and has no try/catch around the dispatch

**File:** `src/main/kotlin/com/six2dez/burp/aiagent/ui/SettingsPersistQueue.kt:66-83`

**Issue:** Every `submit` goes straight to `OffEdtDispatch.run`, which does `Thread(body, threadName).apply { isDaemon = true }.start()`. There is no coalescing: N clicks during a ten-second MCP stop create N threads, all of which park on `lock` for the full wait and then — for all but the newest — discover they are superseded and return having done nothing. The thread is the unit of work even when the work is known-dead the moment a newer generation is minted.

Second limb: `submit` has no `try`. If `Thread.start()` throws (`OutOfMemoryError: unable to create native thread` is the realistic case, and it is the case the unbounded thread creation above makes reachable), or if `dispatchedObserver` throws — which `OffEdtDispatch` deliberately leaves unwrapped — then `onSettled` is never invoked and `renderStatus()` never runs, so the MCP badge is left reporting the pre-click state with no error.

**Fix:** Check the generation once on the calling thread before dispatching (`if (generation <= applied.get()) return`, a cheap early drop that costs no thread for an already-stale click), and wrap the dispatch:

```kotlin
try {
    OffEdtDispatch.run(…)
} catch (t: Throwable) {
    logError("[SettingsPersistQueue] $label-$generation was never dispatched: ${t.javaClass.simpleName}")
    onSettled(Result.failure(t))
    throw t
}
```

---

### WR-08: Two unconditional multi-second waits were added to the fast PR gate

**File:** `src/test/kotlin/com/six2dez/burp/aiagent/ui/SettingsPersistQueueTest.kt:165`, `src/test/kotlin/com/six2dez/burp/aiagent/ui/SettingsSaveAsyncTest.kt:757-760`

**Issue:** Both are waits that are *expected* to expire on every green run, not failsafes that expire only on failure:

```kotlin
bothEntered.await(3, TimeUnit.SECONDS)                  // "under the lock it cannot, so this await times out"
assertFalse(settledSignal.await(2, TimeUnit.SECONDS))   // no worker is dispatched, so this always waits 2s
```

That is five seconds added to every PR-gate run on every one of the three matrix OSes, permanently. The first is also the weaker half of its test — the real claim is `overlapSeen`, and three seconds is an arbitrary guess at how long "long enough for a defect to show" is.

**Fix:** For the overlap probe, drop the timed window and instead assert directly that the second worker is blocked: register a dispatched observer for the second submit and assert `bothEntered.count == 1` immediately after it fires, then release. For the post-shutdown probe, assert on the dispatched-observer record (which is written synchronously, so its absence is immediate and needs no wait) rather than on the settle record.

---

_Reviewed: 2026-08-21_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
_Scope: cb60e32..HEAD — Phase 23 gap closure (plans 23-06 / 23-07 / 23-08)_
