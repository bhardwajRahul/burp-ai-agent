---
phase: 23-edt-confinement-ui-responsiveness
reviewed: 2026-08-20T00:00:00Z
depth: standard
files_reviewed: 14
files_reviewed_list:
  - build.gradle.kts
  - src/main/kotlin/com/six2dez/burp/aiagent/mcp/tools/McpToolExecutorImpl.kt
  - src/main/kotlin/com/six2dez/burp/aiagent/redact/Redaction.kt
  - src/main/kotlin/com/six2dez/burp/aiagent/ui/BottomTabsPanel.kt
  - src/main/kotlin/com/six2dez/burp/aiagent/ui/ChatPanel.kt
  - src/main/kotlin/com/six2dez/burp/aiagent/ui/OffEdtDispatch.kt
  - src/main/kotlin/com/six2dez/burp/aiagent/ui/SettingsPanel.kt
  - src/main/kotlin/com/six2dez/burp/aiagent/ui/SettingsPanelActions.kt
  - src/main/kotlin/com/six2dez/burp/aiagent/ui/SettingsPanelSettingsIO.kt
  - src/test/kotlin/com/six2dez/burp/aiagent/mcp/tools/McpToolExecutorEdtGuardTest.kt
  - src/test/kotlin/com/six2dez/burp/aiagent/ui/ChatPanelEdtConfinementTest.kt
  - src/test/kotlin/com/six2dez/burp/aiagent/ui/ChatPanelTestHarness.kt
  - src/test/kotlin/com/six2dez/burp/aiagent/ui/ChatPanelToolGateTest.kt
  - src/test/kotlin/com/six2dez/burp/aiagent/ui/SettingsSaveAsyncTest.kt
findings:
  critical: 5
  warning: 11
  info: 5
  total: 21
status: issues_found
---

# Phase 23: Code Review Report

**Reviewed:** 2026-08-20
**Depth:** standard
**Files Reviewed:** 14
**Status:** issues_found

## Summary

The phase does what it says at the two doors it names: `McpToolExecutor.executeToolResult` now refuses
the EDT with a `check(...)` that fires without `-ea`, all three remaining `executeTool` call sites go
through `OffEdtDispatch`, and the Settings save body no longer blocks the EDT on
`McpSupervisor.stop()`'s ten-second wait.

The defects are all in the **second half** of that move — the part that has to survive the concurrency
the offload just created. Three separate concurrent writers to shared state were serialized on the EDT
before this phase and are not serialized by anything now: (1) the settings worker versus extension
unload, (2) the settings worker versus the MainTab / in-tab toggles that still call
`settingsRepo.save()` and `mcpSupervisor.applySettings()` on the EDT, and (3) the tool worker versus
`clearChatState()`, the one teardown path of five that got neither a supersede nor a `cancelInFlightRequest()`.
The busy seams are also narrower than their KDoc claims: the Settings seam disables two buttons and
nothing else, and the chat seam leaves `toolsBtn` fully live, so a second tool call can be started
mid-flight from the UI and silently discards the first one's result with no transcript row and no
`SUPERSEDED_RUN_STATUS` audit record.

`OffEdtDispatch` — the file whose whole purpose is "one marshalling point that cannot be bypassed" — has
one unguarded statement between the failed work and the `invokeLater`, which is enough to drop the tail
entirely and strand the busy state permanently. And the `edtGuardWithoutAssertionsTest` task, which is
the only evidence for the phase's central `check`-not-`assert` decision, runs in no CI workflow.

The tests are unusually careful (the dispatch-vs-settle observer split is genuinely the right call, and
the vacuity arguments in the KDoc are sound), but two of them assert a state the shipped UI cannot reach
and the shared settle queue is process-global in a way that can go false-green.

## Critical Issues

### CR-01: A Settings save worker in flight during extension unload restarts the MCP server after `App.shutdown()` tore it down

**File:** `src/main/kotlin/com/six2dez/burp/aiagent/ui/SettingsPanelSettingsIO.kt:553-598`, `src/main/kotlin/com/six2dez/burp/aiagent/ui/SettingsPanelActions.kt:150-155`, `src/main/kotlin/com/six2dez/burp/aiagent/App.kt:220-234`

**Issue:** `applyAndSaveSettingsAsync` dispatches `applyAndSaveSettingsBody` onto a detached daemon
thread and nothing anywhere supersedes, joins, or gates it. `SettingsPanel.shutdown()` (the only
teardown hook the panel has, reached from `MainTab.shutdown()` at `MainTab.kt:850`) stops two Swing
timers and returns:

```kotlin
fun SettingsPanel.shutdown() {
    statusRefreshTimer?.stop()
    statusRefreshTimer = null
    saveFeedbackResetTimer?.stop()
    saveFeedbackResetTimer = null
}
```

The save body is up to ten seconds long by the phase's own accounting (`McpSupervisor.stop()` →
`KtorMcpServerManager.stop()`'s `future.get(10, TimeUnit.SECONDS)`). A user who clicks **Save settings**
and then unloads the extension — or whose Burp project changes — puts `App.shutdown()`'s
`mcpSupervisor.shutdown()`, `passiveAiScanner.shutdown()`, `activeAiScanner.shutdown()` and
`backendRegistry.shutdown()` in a race with the worker's `mcpSupervisor.applySettings(...)` (`:488`),
`passiveAiScanner.setEnabled(updated.passiveAiEnabled)` (`:519`) and
`activeAiScanner.setEnabled(updated.activeAiEnabled)` (`:530`).

Losing that race leaves an MCP server **listening on 127.0.0.1 owned by an unloaded extension's
classloader**, plus re-enabled scanners with no live extension behind them. Before this phase the save
body ran on the EDT and so did the unload path, which made the interleave impossible. The phase context
states teardown exits supersede a running worker — that is true only of `ChatPanel`'s tool worker; the
Settings worker has no supersede at all.

**Fix:** Give the settings worker the same compare-and-set supersede `ChatPanel` got, and check it before
each externally visible mutation — at minimum before the supervisor/scanner block:

```kotlin
// SettingsPanel.kt
internal val saveGeneration = java.util.concurrent.atomic.AtomicLong(0)
@Volatile internal var disposed = false

// SettingsPanelActions.kt — shutdown()
fun SettingsPanel.shutdown() {
    disposed = true
    saveGeneration.incrementAndGet()   // supersede any worker in flight
    statusRefreshTimer?.stop(); statusRefreshTimer = null
    saveFeedbackResetTimer?.stop(); saveFeedbackResetTimer = null
}

// applyAndSaveSettingsAsync
val generation = saveGeneration.incrementAndGet()
...
work = { if (saveGeneration.get() == generation) applyAndSaveSettingsBody(updated) }
```

and re-test `saveGeneration.get() == generation` inside `applyAndSaveSettingsBody` immediately before
`mcpSupervisor.applySettings` and before the two `setEnabled` calls, so a supersede that lands mid-body
still cannot resurrect a stopped server.

---

### CR-02: The Settings busy seam guards two buttons; every other settings writer still runs on the EDT concurrently with the worker

**File:** `src/main/kotlin/com/six2dez/burp/aiagent/ui/BottomTabsPanel.kt:110-115`, `src/main/kotlin/com/six2dez/burp/aiagent/ui/MainTab.kt:167-168`, `:445-513`, `src/main/kotlin/com/six2dez/burp/aiagent/ui/SettingsPanelSettingsIO.kt:476-536`

**Issue:** `setActionsBusy` disables exactly `saveButton` and `restoreButton`. Its KDoc reasons that
disabling both closes "the double-save race, re-entered through the other door" — but there are at least
six other doors, all of them live during the up-to-ten-second worker, and all of them writing the same
state on the EDT:

- `MainTab.kt:167-168` — the backend picker's listener: `settingsRepo.save(settingsPanel.currentSettings())`
- `MainTab.kt:451-458` and `:485-492` — MCP toggle (panel-side and toolbar-side): `settingsRepo.save(updated)` **and** `mcpSupervisor.applySettings(...)`
- `MainTab.kt:467`, `:475`, `:502`, `:511` — passive/active scanner toggles: `settingsRepo.save(...)`

Two concrete consequences:

1. **`McpSupervisor.applySettings` is not synchronized** (`McpSupervisor.kt:79-104` is a bare sequence of
   `AtomicReference.set` calls followed by `stop()` / start). Two threads entering it concurrently can
   interleave a stop with a start on the same Ktor server.
2. **`AgentSettingsRepository.save()` writes many independent `Preferences` keys, not one atomic blob.**
   Two concurrent saves therefore persist a *mixture* of two snapshots — including privacy-relevant keys
   such as `privacyMode`, `auditEnabled` and the custom pattern list — rather than one losing cleanly.
   The user is given no indication their save was partially overwritten.

Both were impossible before this phase because every one of these writers ran on the EDT.

**Fix:** Route the busy seam to everything that can start a settings write, not just the two buttons.
Either (a) have `SettingsPanel` expose the busy state and have `MainTab` gate its four toggle listeners
and the backend picker on it (`if (settingsPanel.isSaveInFlight()) return@addActionListener`), or
(b) serialize all settings mutation onto a single-threaded executor so the interleave cannot occur
regardless of which door is used. (b) is the smaller surface and also fixes the toggle-vs-toggle case:

```kotlin
// one owner for every settings write, EDT or worker
private val settingsWriter = Executors.newSingleThreadExecutor { r ->
    Thread(r, "burp-ai-settings-save").apply { isDaemon = true }
}
```

---

### CR-03: Clear Chat does not supersede a running tool worker — its result lands in the transcript the user just wiped and continues the chain

**File:** `src/main/kotlin/com/six2dez/burp/aiagent/ui/ChatPanel.kt:1232-1253` (compare `:932`)

**Issue:** `clearChatState()` is documented in this very file as "Teardown path 3 of 5". Of the five,
session delete takes the token explicitly (`:932`), and Cancel / project change / unload all route
through `cancelInFlightRequest()` (`:1033`). `clearChatState()` routes through **neither**:

```kotlin
internal fun clearChatState() {
    val selected = sessionsList.selectedValue ?: return
    val panel = sessionPanels[selected.id] ?: return
    resolvePending(selected.id, ImplicitDenyReason.CHAT_CLEARED)   // pending decisions only
    panel.clearMessages()
    supervisor.removeChatSession(selected.id)
    selected.messages.clear()
    ...
}
```

A user who clicks **Clear Chat** while an approved chain tool is running (`toolsBtn` and `clearChatBtn`
are both left enabled by `setSendingState`, `:1011-1016`) gets, when the worker returns:

- `finishApprovedToolCall`'s `runningTool.clearIfMatches(token)` **succeeds** — the token was never taken —
- so a `Tool result: <tool>` row is rendered into the transcript that was just cleared, and
- `sendMessage(captured.sessionId, followup, ...)` sends a followup turn whose `captured.userText` is the
  pre-clear user message, against a session whose history the user just deleted and whose
  `supervisor.removeChatSession` already ran.

This is exactly the dangling-continuation class of bug `deleteConfirmedSession`'s comment says the
`take()` exists to prevent — reached through the one teardown path that did not get it. It is untested:
`clearChatState` appears in the suite only at `ChatPanelToolGateTest.kt:635,654`, both Phase 22
pending-decision assertions, never with a worker in flight.

**Fix:** Give it the same two lines the session-delete path has, before `panel.clearMessages()`:

```kotlin
internal fun clearChatState() {
    val selected = sessionsList.selectedValue ?: return
    val panel = sessionPanels[selected.id] ?: return
    // Teardown path 3 of 5. Same supersede as deleteConfirmedSession: a running worker must not
    // render into, or chain off, a transcript the user just wiped.
    if (runningTool.take() != null) setSendingState(false)
    resolvePending(selected.id, ImplicitDenyReason.CHAT_CLEARED)
    ...
```

and add a confinement test mirroring `deleteConfirmedSession`'s (`ChatPanelEdtConfinementTest.kt:471-524`).

---

### CR-04: `OffEdtDispatch` drops its entire EDT tail if the error sink throws, permanently stranding the busy state

**File:** `src/main/kotlin/com/six2dez/burp/aiagent/ui/OffEdtDispatch.kt:91-110`

**Issue:** The failure log call sits **outside** any `try`, on the path to the `invokeLater`:

```kotlin
val body = Runnable {
    val outcome = runCatching(work)
    outcome.exceptionOrNull()?.let { failure ->
        logError("[OffEdtDispatch] $label failed off the EDT: ...")   // <-- unguarded
    }
    SwingUtilities.invokeLater { ... }                                 // <-- never reached if it throws
}
```

`logError` is `api.logging().logToError` at every production call site. A Montoya API handle whose
extension has been unloaded is not guaranteed to keep accepting calls, and this is precisely the
co-occurring condition: `work` failed *and* the extension is tearing down. If `logError` throws, the
runnable dies and:

- `ChatPanel` never runs `finishApprovedToolCall` / `finishUserOriginatedToolCall`, so `setSendingState(false)`
  never fires, `runningTool` keeps a token no one will clear, and `onCompleted` is silently dropped —
  the panel is stuck in S3 (Send hidden, input disabled) for the rest of the Burp session with nothing running;
- the settle observer never fires, so the harness's `awaitToolSettled` would hang to its failsafe.

The same shape appears one level down at `:100-104`: the `logError` inside the tail's `runCatching`
handler is itself unguarded, though there the `finally` at least still runs.

**Fix:** Wrap both sinks so a throwing logger can never cost the tail:

```kotlin
val body = Runnable {
    val outcome = runCatching(work)
    outcome.exceptionOrNull()?.let { failure ->
        runCatching { logError("[OffEdtDispatch] $label failed off the EDT: ${failure.javaClass.simpleName}: ${failure.message}") }
    }
    SwingUtilities.invokeLater {
        try {
            runCatching { onEdt(outcome) }.exceptionOrNull()?.let { failure ->
                runCatching { logError("[OffEdtDispatch] $label failed in its EDT tail: ...") }
            }
        } finally {
            runCatching { settledObserver?.invoke(label) }
        }
    }
}
```

Note that `applyAndSaveSettingsAsync` already defends against this for its own seam (its outer `catch`
posts `lowerBusy` before rethrowing, `SettingsPanelSettingsIO.kt:573-578`) — but that lowering happens
*after* the unguarded `logError`, so it is on the dead path too.

---

### CR-05: `openToolDialog` has no busy guard — a second tool call silently destroys the first one's result, and clears an unrelated backend turn's busy state

**File:** `src/main/kotlin/com/six2dez/burp/aiagent/ui/ChatPanel.kt:1070-1131`, `:1153-1164`, `:1011-1016`

**Issue:** `setSendingState` toggles only `sendBtn.isVisible`, `cancelBtn.isVisible` and
`inputArea.isEnabled`. `toolsBtn` is enabled purely on `mcpAvailable` (`:346`) and is never touched by
the busy state, so `openToolDialog()` is fully reachable in state S3. It contains no re-entrancy check
of any kind. Two reachable failures:

1. **Silent result destruction.** Run tool A from the dialog; while it is in flight, run tool B.
   `runningTool.set(token2)` (`:1113`) overwrites token A. When A's worker returns,
   `finishUserOriginatedToolCall` does:

   ```kotlin
   if (!runningTool.clearIfMatches(token)) return
   ```

   and returns having rendered **nothing**. The transcript is left showing `You: /tool a {}` with no
   result row that will ever appear, and — unlike the chain path's `discardSupersededToolResult`
   (`:3196-3225`) — no `SUPERSEDED_RUN_STATUS` decision record is emitted either. The user is given no
   indication the call happened, no indication it was discarded, and no way to recover the answer.

2. **Busy-state clobber across an unrelated turn.** Open the tool dialog while a *backend turn* is
   streaming. The tool's tail calls `setSendingState(false)` (`:1161`) unconditionally, which un-hides
   Send, hides Cancel and re-enables the input area while the backend turn is still running — so the user
   can now start a second turn that clobbers `inFlightConnection`, and has lost the affordance to cancel
   the first. Before this phase `openToolDialog` touched the sending state not at all, so this is a
   regression introduced by the offload rather than a pre-existing gap.

**Fix:** Gate the entry, disable the button with the rest of the busy seam, and make the superseded
branch visible:

```kotlin
private fun setSendingState(sending: Boolean) {
    isSending = sending
    sendBtn.isVisible = !sending
    cancelBtn.isVisible = sending
    inputArea.isEnabled = !sending
    toolsBtn.isEnabled = mcpAvailable && !sending   // S-2: one tool at a time, one door
}

fun openToolDialog() {
    if (isSending) { showError("A request is already running. Cancel it first."); return }
    ...
}
```

and in `finishUserOriginatedToolCall`, replace the bare `return` with the chain path's treatment — a
`SUPERSEDED_RUN_STATUS` record plus a transcript line naming the discarded tool, so a discarded
user-originated call is as legible as a discarded chain step.

## Warnings

### WR-01: A throw between raising the busy seam and starting the thread strands it forever

**File:** `src/main/kotlin/com/six2dez/burp/aiagent/ui/SettingsPanelSettingsIO.kt:563-564`, `src/main/kotlin/com/six2dez/burp/aiagent/ui/ChatPanel.kt:1112-1131`, `:3076-3088`

**Issue:** Every call site raises the busy state and *then* calls `OffEdtDispatch.run`, with no `try`
around the dispatch. `OffEdtDispatch.run` can throw before a worker exists — `dispatchedObserver?.invoke(label)`
at `OffEdtDispatch.kt:89` is unguarded, and `Thread(...).start()` at `:111` throws `OutOfMemoryError`
when the JVM cannot create a thread. In either case the busy state is already raised and no tail will
ever lower it: the Settings tab becomes permanently unsaveable, and the chat panel permanently hides
Send. The `applyAndSaveSettingsAsync` KDoc names exactly this outcome ("leaves the Settings tab
permanently unsaveable, with no error and no way back short of reloading the extension") and then does
not cover it.

**Fix:**

```kotlin
busyListener?.invoke(true)
try {
    OffEdtDispatch.run(...)
} catch (t: Throwable) {
    lowerBusy()
    throw t
}
```

Apply the mirror form (`setSendingState(false); runningTool.take()`) at the three `ChatPanel` dispatch sites.

---

### WR-02: New custom redaction patterns go live *after* the MCP server has been restarted under the new privacy mode

**File:** `src/main/kotlin/com/six2dez/burp/aiagent/ui/SettingsPanelSettingsIO.kt:488-507`

**Issue:** The body's order is `mcpSupervisor.applySettings(...)` (`:488`) → `audit.setEnabled(...)`
(`:503`) → `Redaction.setCustomPatterns(...)` (`:507`). `mcpSupervisor.applySettings` starts/restarts
the MCP server, which begins serving tool calls immediately; those calls are redacted against the
**old** custom-pattern list until `:507` lands. A user who adds a pattern for a newly discovered secret
and saves therefore has a window in which the freshly restarted server emits that secret unredacted.
Tightening should always precede exposure.

The phase also *moved* `audit.setEnabled` from before `mcpSupervisor.applySettings` to after it, which
widens the window in which a restarted server serves tool calls while auditing is still disabled — the
opposite of what the `E8` comment block claims to be buying. The comment argues the two writes are kept
"contiguous"; contiguity with each other is not the property that matters here, ordering relative to the
server restart is.

`SettingsSaveAsyncTest.aToolWorkerMidSaveRedactsUnderItsSnapshotModeAndIsNeverUnredacted` parks the save
at `passiveAiScanner.setEnabled` — i.e. *after* `setCustomPatterns` has published — so it never exercises
this window at all.

**Fix:** Move both global privacy writes above the supervisor calls:

```kotlin
settings = updated
settingsRepo.save(updated)
// Tighten before exposing: the MCP restart below immediately serves tool calls.
audit.setEnabled(updated.auditEnabled)
Redaction.setCustomPatterns(updated.customRedactionPatterns)
...
supervisor.applySettings(updated)
mcpSupervisor.applySettings(...)
```

and add a test that parks the save *between* `mcpSupervisor.applySettings` and `setCustomPatterns` and
asserts the marker is still redacted.

---

### WR-03: Supersede messages are written to the selected session, not the session that owned the tool

**File:** `src/main/kotlin/com/six2dez/burp/aiagent/ui/ChatPanel.kt:1046-1057`, `:932`

**Issue:** `cancelInFlightRequest` picks its transcript by current selection:

```kotlin
val sessionId = sessionsList.selectedValue?.id ?: return true
val panel = sessionPanels[sessionId] ?: return true
...
panel.addMessage("System", line)
```

`RunningToolToken` carries `tool` but not `sessionId`, and `runningTool` is one panel-wide cell shared
across all sessions. So:

- switch sessions while a tool runs, then press Cancel → `toolCancelLine("<tool>")` ("… was already sent
  to Burp and will finish") is written into a transcript that never ran that tool, and the owning
  transcript gets nothing;
- `deleteConfirmedSession` (`:932`) takes the token of a tool belonging to a **different** session and
  deliberately writes no line at all, reasoning that "the only transcript that could carry it is the one
  being deleted" — which is false whenever the running tool belongs elsewhere. Deleting Chat 2 silently
  kills Chat 1's running tool and breaks its chain with no message in Chat 1.

The KDoc claims the line is "SELECTED BY WHAT WAS TAKEN, never by … which state the panel believes it is
in", but the *panel* it is written to is still selected by panel state.

**Fix:** Put the session id in the token and address the transcript by it:

```kotlin
private class RunningToolToken(val tool: String, val sessionId: String)
...
val target = toolToken?.let { sessionPanels[it.sessionId] } ?: sessionPanels[sessionsList.selectedValue?.id]
target?.addMessage("System", line)
```

and have `deleteConfirmedSession` write the C-1 line when the taken token's `sessionId` is not the
session being deleted.

---

### WR-04: A superseded user-originated tool call produces no record on any surface

**File:** `src/main/kotlin/com/six2dez/burp/aiagent/ui/ChatPanel.kt:1160`

**Issue:** `if (!runningTool.clearIfMatches(token)) return` — nothing else. The chain path's equivalent
(`discardSupersededToolResult`, `:3196-3225`) emits a `ToolDecisionReporter.report(runStatus = "cancelled")`
pair plus an `aiRequestLogger` entry, on the stated D-07 grounds that "a cancelled call must never become
an unlogged one". The `/tool` and dialog paths are exempted from that rule with no justification beyond
"UNGATED by SC5 and so carry no approval record" — but the *run status* is not an approval record, and
its absence means the audit log cannot distinguish a user-originated call whose result was discarded from
one that completed normally. (`McpTool.runTool`'s own `mcp_tool_start`/`mcp_tool_end` telemetry does record
that the call happened, so `toolCancelLine`'s "The call is in the audit log" claim holds — but the discard
does not appear anywhere.)

**Fix:** Emit a `runStatus = SUPERSEDED_RUN_STATUS` event from the superseded branch, with the origin
recorded, before returning.

---

### WR-05: Custom-pattern ReDoS validation still runs on the EDT, unbounded in the number of patterns

**File:** `src/main/kotlin/com/six2dez/burp/aiagent/ui/SettingsPanelActions.kt:69`, `src/main/kotlin/com/six2dez/burp/aiagent/ui/SettingsPanelSettingsIO.kt:208`, `:220-242`

**Issue:** `saveSettings()` deliberately calls `currentSettings()` on the EDT before dispatch, which calls
`validateAndCollectCustomPatterns()`, which runs `SafeRegex.isPatternSafe` — documented as a "regex
compile + 50 ms ReDoS probe" — once per non-blank line, in a plain loop, on the EDT. Forty patterns is up
to two seconds of frozen UI, and the count is user-controlled with no cap. This is now the single largest
remaining EDT block on the save path, in the phase whose stated purpose was to remove EDT blocks from the
save path.

**Fix:** Split `currentSettings()` into the Swing read (EDT) and the validation (worker). Read
`customPatternsArea.text` on the EDT, pass the raw text into the worker, run `isPatternSafe` there, and
marshal the `patternsFeedbackLabel` update back through the existing `onEdt` tail.

---

### WR-06: The `/tool` slash path shows the user's command to the model but never the result

**File:** `src/main/kotlin/com/six2dez/burp/aiagent/ui/ChatPanel.kt:2509-2527`

**Issue:** The phase added `sessionsById[sessionId]?.messages?.add(ChatMessage("user", trimmed))` so "the
model sees it too (FLAG-23-03), which removes an asymmetry with the dialog path". But the dialog path's
`onSuccess` also adds the *result* (`:1131-1134`), and the slash path's does not:

```kotlin
onEdt = { result ->
    finishUserOriginatedToolCall(panel, toolName, token, result) {
        state.toolsMode = true
        state.toolCatalogSent = state.toolCatalogSent || argsJson != null
    }
}
```

The net effect is that the change *created* an asymmetry rather than removing one: the model's history now
contains a `/tool proxy_http_history {...}` request with no answer after it, which on the next turn reads
as a failed or ignored request and can prompt the model to re-issue it.

**Fix:** Mirror the dialog path:

```kotlin
finishUserOriginatedToolCall(panel, toolName, token, result) { text ->
    sessionsById[sessionId]?.messages?.add(ChatMessage("assistant", "Tool result ($toolName):\n$text"))
    state.toolsMode = true
    state.toolCatalogSent = state.toolCatalogSent || argsJson != null
}
```

---

### WR-07: A stale `inFlightConnection` makes Cancel claim a tool call was cancelled when it was sent

**File:** `src/main/kotlin/com/six2dez/burp/aiagent/ui/ChatPanel.kt:1053`, `:676-686`, `:801-802`

**Issue:** The new C-1 copy selection is:

```kotlin
val line = if (conn != null) "Request cancelled." else toolCancelLine(requireNotNull(toolToken).tool)
```

It relies entirely on the comment's premise that "by the time a tool is running the backend turn's
connection has already been cleared". That premise depends on `callbackConnectionRef.get()` being non-null
inside `onComplete` — but `inFlightConnection.set(connection)` and `callbackConnectionRef.set(connection)`
happen at `:801-802`, *after* `supervisor.sendChat(...)` returns, and `onComplete` can fire on the backend
thread before that. When it does, `callbackConnection == null`, the `clearIfMatches` branch is skipped, and
the connection published at `:802` for an already-completed turn is **never cleared**. A later Cancel on a
running tool then takes a non-null stale `conn` and prints `"Request cancelled."` — telling the tester the
request was cancelled when it was actually sent to the target, which is the exact harm the
`toolCancelLine` KDoc says C-1 exists to prevent.

**Fix:** Select the line from what was taken, not from a proxy for it:

```kotlin
val line = when {
    toolToken != null -> toolCancelLine(toolToken.tool)
    else -> "Request cancelled."
}
```

Separately, publish the connection before it can be observed — set `callbackConnectionRef`/`inFlightConnection`
from inside `sendChat`'s connection factory, or have `onComplete` clear unconditionally on a generation
counter rather than on connection identity.

---

### WR-08: The shared settle queue can be satisfied by a leaked worker from a previous test

**File:** `src/test/kotlin/com/six2dez/burp/aiagent/ui/ChatPanelTestHarness.kt:229-238`, `:283-297`

**Issue:** `settled` is a process-global `LinkedBlockingQueue<String>` on an `object`, and
`awaitToolSettled(count)` consumes **any** label:

```kotlin
repeat(count) { index ->
    requireNotNull(settled.poll(failsafeSeconds, TimeUnit.SECONDS)) { ... }
}
```

`ChatPanelEdtConfinementTest.kt:1185-1189` explicitly records that "panels built by earlier tests in this
class are never shut down, and their queued `invokeLater` chains run on this test's drains". A leaked
worker that settles after `installSettledObserver()` has cleared the queue therefore counts toward the
current test's await, letting an assertion run before the worker it was actually waiting for has finished.
That is a false-green, not merely a flake, and it defeats the purpose of the settle observer.

**Fix:** Make the await selective — `awaitToolSettled(label: String)` draining until the expected label
appears (the suite already has `pendingTraceId(h)` for exactly this), or key the queue per-harness rather
than per-process.

---

### WR-09: Two supersede tests drive a UI state the shipped panel forbids

**File:** `src/test/kotlin/com/six2dez/burp/aiagent/ui/ChatPanelToolGateTest.kt:517-541` (`aToolSupersededByASlashCommandRefundsNoChainIterationEither`)

**Issue:** The test sends `/tool status {}` while a chain tool is in flight, via
`ChatPanelTestHarness.sendUserMessage`, which assigns `inputArea.text` and calls `send.doClick()`. In the
shipped panel that state is unreachable: `setSendingState(true)` has set `inputArea.isEnabled = false` and
`sendBtn.isVisible = false`. `doClick()` fires an `ActionListener` on an invisible button and text can be
assigned to a disabled `JTextArea`, so the test passes — against a path no user can take.

The KDoc explains this variant replaced a project-change one because the project-change version "went
green against the defect". Replacing an over-permissive test with an unreachable-path test does not
restore the coverage: the real reachable supersede door is `openToolDialog` (see CR-05), which the suite
does not exercise at all.

**Fix:** Re-point the test at `openToolDialog` / `runToolInvocation` (reachable, since `toolsBtn` stays
enabled), or have `sendUserMessage` assert `send.isVisible && input.isEnabled` before clicking so a test
that drives an impossible state fails loudly.

---

### WR-10: `supersedeReason` never reaches the durable audit event

**File:** `src/main/kotlin/com/six2dez/burp/aiagent/ui/ChatPanel.kt:3199-3213`

**Issue:**

```kotlin
val metadata = toolDecisionReporter.report(...) + ("supersedeReason" to "result discarded before it was applied")
```

`ToolDecisionReporter.report` emits its `AuditLogger.emitGlobal(MCP_TOOL_DECISION_EVENT, payload)`
(`ToolDecisionReporter.kt:151`) **before returning**, so the `+ (...)` only decorates the map handed to the
nullable `aiRequestLogger`. The durable SEC-06 record — the one the D-07 argument is about — carries
`status = "cancelled"` but never the reason. With `aiRequestLogger` null (its documented possible state,
cited two lines away in `denyToolCall`) the reason is lost entirely.

**Fix:** Pass the reason through `report(...)` as a parameter so it lands in the emitted payload, rather
than appending it to the returned map.

---

### WR-11: The one gate that proves the phase's central design decision runs in no CI workflow

**File:** `build.gradle.kts:236-249`, `.github/workflows/build.yml:47`, `.github/workflows/release.yml:33`

**Issue:** `edtGuardWithoutAssertionsTest` exists because SC1's whole argument is that `assert` is a no-op
in shipped Burp and `check` is not. It is registered, documented at length, and invoked by nothing:
`build.yml` runs `test -PexcludeHeavyTests=true`, `release.yml` runs `test nightlyRegressionTest`,
`nightly-regression.yml` runs `test nightlyRegressionTest`. The task's own comment says "It stays out of
`check` on purpose: it duplicates coverage the fast PR gate already has" — but the fast PR gate runs with
`-ea`, where an `assert`-based guard is *also* green. Reverting `check(...)` to `assert(...)` in
`McpToolExecutorImpl.kt:161` would leave every automated gate passing.

**Fix:** Add it to the release workflow (`./gradlew test nightlyRegressionTest edtGuardWithoutAssertionsTest`),
or at minimum to `nightly-regression.yml`. Its cost is one test class.

## Info

### IN-01: `isSending` is written and never read

**File:** `src/main/kotlin/com/six2dez/burp/aiagent/ui/ChatPanel.kt:137`, `:1012`

`@Volatile private var isSending = false` has exactly two references in the whole repository: its
declaration and its assignment in `setSendingState`. The busy state is carried entirely by component
visibility/enablement, which is why CR-05's re-entrancy hole exists — there is no queryable predicate to
guard against. Either read it (see CR-05's fix) or delete it.

### IN-02: `RunningToolTracker.current()` is dead code

**File:** `src/main/kotlin/com/six2dez/burp/aiagent/ui/ChatPanel.kt:109`

Only `set`, `take` and `clearIfMatches` are ever called. `current()` exists to mirror
`InFlightConnectionTracker`'s "deliberately the same four-method surface", but symmetry with a sibling is
not a use. Remove it or the next reader will assume there is a caller.

### IN-03: The supersede token is typed `Any?`, and `as?` can silently swallow a taken token

**File:** `src/main/kotlin/com/six2dez/burp/aiagent/ui/ChatPanel.kt:98-110`, `:1033`

`val toolToken = runningTool.take() as? RunningToolToken` consumes the cell unconditionally and then
discards anything that is not a `RunningToolToken`, so a future second token type would be taken and
dropped with no diagnostic — while `clearIfMatches` on the original owner would then also fail. Nothing
requires the erased typing: make it `AtomicReference<RunningToolToken?>` and let the compiler enforce it.

### IN-04: Chain tool workers share one dispatch label across chain steps

**File:** `src/main/kotlin/com/six2dez/burp/aiagent/ui/ChatPanel.kt:3081`, `src/main/kotlin/com/six2dez/burp/aiagent/ui/OffEdtDispatch.kt:70-72`

`label = traceId`, and `ChatPanelToolGateTest.kt:249-250` records that "a chain's trace id is threaded
through every followup turn". So the label is per-*chain*, not per-*unit of work*, which contradicts
`OffEdtDispatch.run`'s KDoc ("callers pass a trace id, so an ordering assertion can select by identity
instead of by list position") — identity selection is ambiguous the moment a chain has two steps. The two
user-originated call sites already do the right thing (`"chat-tool-slash-" + UUID.randomUUID()`).
Suggest `"$traceId#${chainStepFor(remainingToolIterations)}"`.

### IN-05: `edtGuardWithoutAssertionsTest` drags the whole `-ea` test task along with it

**File:** `build.gradle.kts:236-249`, `:290-300`

`tasks.withType<Test> { finalizedBy(tasks.named("jacocoTestReport")) }` is a live collection, so it also
applies to the newly registered task; `jacocoTestReport` in turn `dependsOn(tasks.named("test"))`. Running
`./gradlew edtGuardWithoutAssertionsTest` therefore also runs the full `-ea` suite and overwrites the
jacoco report with coverage from a different JVM configuration. Exclude the new task from the
`finalizedBy` wiring:

```kotlin
tasks.withType<Test>().matching { it.name == "test" }.configureEach {
    finalizedBy(tasks.named("jacocoTestReport"))
}
```

---

_Reviewed: 2026-08-20_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
