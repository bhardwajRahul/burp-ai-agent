# Phase 23: EDT Confinement & UI Responsiveness - Pattern Map

**Mapped:** 2026-08-20
**Files analyzed:** 11 (4 new, 7 modified)
**Analogs found:** 11 / 11 (all exact or role-match — this phase invents no new shape)

> **Headline for the planner:** every mechanism this phase needs already exists in this repo in a
> reviewed, tested form. Anything that looks like new machinery is a signal to go find the existing
> shape. The two shapes most likely to be copied wrongly are called out under
> [Anti-Patterns](#anti-patterns--shapes-that-look-right-and-are-not).

---

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|-------------------|------|-----------|----------------|---------------|
| **NEW** `ui/OffEdtDispatch.kt` | utility (concurrency helper) | event-driven / dispatch+marshal | `backends/http/MontoyaHttpTransport.kt:79-92` (thread form) + `ui/MainTab.kt:190-215` (idiom) + `audit/AuditLogger.kt:18-31` (observer hook) | composite — three partial analogs, one per concern |
| **NEW** `ui/ChatPanelEdtConfinementTest.kt` | test (integration, headless real panel) | request-response | `ui/ChatPanelToolGateTest.kt` | exact |
| **NEW** `mcp/tools/McpToolExecutorEdtGuardTest.kt` | test (unit) | request-response | `ui/ChatPanelToolGateTest.kt` (assertion style) — no closer unit analog for the executor | role-match |
| **NEW** `ui/SettingsSaveAsyncTest.kt` | test (integration) | CRUD / file-I/O | `ui/ChatPanelToolGateTest.kt` fixture shape; `SettingsDefaultsPersistenceTest.kt:68` in-memory `Preferences` fake | role-match |
| `ui/ChatPanel.kt` — 3 `executeTool` sites (`:1010`, `:2319`, `:2856`) | component (Swing panel) | request-response | `ui/MainTab.kt:190-215` (dispatch + `invokeLater` tail) | exact |
| `ui/ChatPanel.kt` — supersede tracker (new `RunningToolTracker`) | utility (in-file) | event-driven | `InFlightConnectionTracker` (`ChatPanel.kt:69-83`) | **exact — copy verbatim, same file** |
| `ui/ChatPanel.kt` — `cancelInFlightRequest` / `deleteSession` / `shutdown` / `clearInMemorySessionState` | component (teardown) | event-driven | each other — `shutdown()` (`:1443`) and `clearInMemorySessionState()` (`:1475`) are the reviewed teardown template | exact |
| `ui/ChatPanel.kt` — `ToolCallOutcome.EXECUTING` | model (private enum) | — | the existing 3 values (`:1738-1747`) with their per-value KDoc | exact |
| `mcp/tools/McpToolExecutorImpl.kt:137` — the guard | service (door precondition) | request-response | `executeToolResult`'s own existing early-returns (`:145-155`); `check()`/`IllegalStateException` per CONVENTIONS.md:190 | role-match |
| `ui/SettingsPanelSettingsIO.kt:456-505` + `ui/SettingsPanelActions.kt:64-93` | service + controller | CRUD / file-I/O | `MainTab.kt:190-215` for the dispatch; `UiActions.refreshBountyPromptCache` (`SettingsPanelSettingsIO.kt:460`) is the off-thread precedent **inside the very function being restructured** | role-match |
| `ui/BottomTabsPanel.kt` + `SettingsPanel.kt` field + `SettingsPanelActions.kt` setter | config (listener seam) | event-driven | `setDialogParent` seam — field `SettingsPanel.kt:49`, setter `SettingsPanelActions.kt:35-37`, install `BottomTabsPanel.kt:93` | **exact — mirror all three lines** |
| `test/.../ui/ChatPanelTestHarness.kt` — `awaitToolSettled` | test fixture | event-driven | `AuditLogger.registerGlobalEmitter` install/clear at `ChatPanelToolGateTest.kt:640-653` | exact |

---

## Pattern Assignments

### `ui/OffEdtDispatch.kt` (NEW — utility, dispatch+marshal)

Three analogs, each contributing one concern. **No single file has the whole shape** — that is why
the helper exists.

#### (a) Thread creation form — copy `MontoyaHttpTransport.kt:85`, NOT `MainTab.kt:193`

**Analog:** `src/main/kotlin/com/six2dez/burp/aiagent/backends/http/MontoyaHttpTransport.kt:79-92`

```kotlin
if (SwingUtilities.isEventDispatchThread()) {
    // Burp throws "Extensions should not make HTTP requests in the Swing event dispatch
    // thread" if sendRequest runs on the EDT (#80 — reached via the pre-send LM Studio /
    // Ollama health check). Run it on a short-lived daemon worker and block for the
    // result; the request is already bounded by timeoutMs.
    val task = FutureTask { api.http().sendRequest(request, options) }
    Thread(task, "montoya-http-offedt").apply { isDaemon = true }.start()
    try {
        task.get(timeoutMs + EDT_OFFLOAD_GRACE_MS, TimeUnit.MILLISECONDS)   // <-- ANTI-PATTERN, see below
    } catch (e: ExecutionException) {
        throw e.cause ?: e
    }
}
```

**Copy exactly one line:** `Thread(body, threadName).apply { isDaemon = true }.start()`.
**Do NOT copy the `task.get(...)` tail** — it blocks the caller (the EDT) on the worker. That is the
in-repo counter-example RESEARCH.md P-1 names; dodging Burp's exception is not the same as freeing
the UI.

#### (b) Dispatch + marshal idiom — `MainTab.kt:190-215` (the idiom SC6 names)

**Analog:** `src/main/kotlin/com/six2dez/burp/aiagent/ui/MainTab.kt:190-215`

```kotlin
healthTimer =
    Timer(5000) {
        val settings = settingsPanel.currentSettings()   // <-- EDT snapshot BEFORE dispatch
        Thread {                                          // <-- NOTE: no isDaemon. See (a).
            val health = supervisor.backendHealth(settings)
            SwingUtilities.invokeLater {
                when (health) {
                    is HealthCheckResult.Healthy -> { backendStatusLabel.text = "AI: OK" ; ... }
                    ...
                }
            }
        }.start()
    }
```

Two properties to carry forward, one to reject:
- **Carry:** the EDT snapshot (`settingsPanel.currentSettings()`) is taken *before* the `Thread`,
  and only the immutable snapshot crosses. This is exactly D-11's "preserve that ordering" and
  D-05/E5's "capture EVERYTHING on the EDT first".
- **Carry:** the worker's only Swing contact is one `SwingUtilities.invokeLater` tail. One boundary.
- **Reject:** the bare `Thread { }` — `isDaemon` is never set here. D-08's "a daemon thread never
  blocks unload" is only true if the flag is set. Set it, and name the thread.

#### (c) Test-visible completion observer — `AuditLogger.kt:18-31`

**Analog:** `src/main/kotlin/com/six2dez/burp/aiagent/audit/AuditLogger.kt:14-31`

```kotlin
class AuditLogger(private val api: MontoyaApi) {
    companion object {
        @Volatile
        private var globalEmitter: ((String, Any) -> Unit)? = null

        fun registerGlobalEmitter(emitter: ((String, Any) -> Unit)?) {
            globalEmitter = emitter
        }

        fun emitGlobal(type: String, payload: Any) {
            globalEmitter?.invoke(type, payload)
        }
    }
```

Copy all four elements: `@Volatile`, nullable, a nullable-accepting register function (so
`register(null)` is the clear), and a separate `emit` that null-safe-invokes. The matching test-side
discipline is in [Shared Patterns](#4-global-test-observer-installclear-discipline).

**Why this and not an injected seam:** `McpToolExecutor` is an `object` singleton
(`McpToolExecutorImpl.kt:45`) referenced statically at all three call sites — there is no double to
put a latch in. And an 11th `ChatPanel` constructor parameter would invalidate
`detekt-baseline.xml:141`'s verbatim `LongParameterList` entry. Both constraints are checked; the
observer hook is the only remaining shape.

---

### `ui/ChatPanel.kt` — the supersede tracker (modified: new private class + wiring)

**Analog:** `InFlightConnectionTracker` — `src/main/kotlin/com/six2dez/burp/aiagent/ui/ChatPanel.kt:69-83`
**Match: exact, and it is in the same file.**

```kotlin
internal class InFlightConnectionTracker {
    private val ref = AtomicReference<AgentConnection?>()

    fun set(connection: AgentConnection?) {
        ref.set(connection)
    }

    fun clearIfMatches(expected: AgentConnection?): Boolean {
        if (expected == null) return ref.get() == null
        return ref.compareAndSet(expected, null)
    }

    fun take(): AgentConnection? = ref.getAndSet(null)

    fun current(): AgentConnection? = ref.get()
}
```

Copy the four-method surface verbatim (`set` / `clearIfMatches` (CAS) / `take` / `current`) with an
opaque token type. Its test analog already exists and should be extended in the same shape:
`ChatPanelConcurrencyTest.kt:19-56` (`clearIfMatches_onlyClearsWhenConnectionMatches`,
`take_returnsConnectionOnlyOnceUnderConcurrency`) — but **note the naming trap**: that file is
`*ConcurrencyTest` and is excluded from the PR gate. New assertions go in
`ChatPanelEdtConfinementTest`, not there.

---

### `ui/ChatPanel.kt` — the three `executeTool` call sites (modified)

**Site 3 (the chain, `:2856`) is the tracer.** Current shape:

```kotlin
val startedAt = System.currentTimeMillis()
val resultOutcome = runCatching { McpToolExecutor.executeTool(call.tool, call.argsJson, context, approved.origin) }
val durationMs = System.currentTimeMillis() - startedAt
if (resultOutcome.isFailure) {
    return reportFailedToolCall(...)
}
val result = resultOutcome.getOrThrow()
```
[`ChatPanel.kt:2854-2860`]

The `runCatching { … }` → `Result` → branch shape is already exactly what `OffEdtDispatch.run`'s
`onEdt: (Result<T>) -> Unit` parameter consumes. **The `Result` crosses the thread boundary intact;
do not unwrap it on the worker.**

**Audit pair — must stay together, on the EDT tail** (`ChatPanel.kt:2870-2900`):

```kotlin
val metadata =
    toolDecisionReporter.report(
        rawToolName = call.tool, canonicalId = canonicalId,
        knownTool = isKnownTool(canonicalId, context),
        tier = approved.tier, decision = approved.decision,
        argsJson = call.argsJson, traceId = traceId, chainStep = chainStep,
        resultChars = result.length, runStatus = status,
    )
supervisor.aiRequestLogger?.log(
    type = ActivityType.MCP_TOOL_CALL, source = "chat", backendId = backendId,
    sessionId = sessionId, detail = "Tool ${call.tool} executed",
    durationMs = durationMs, metadata = metadata,
)
```

`report(...)` returns the `metadata` that `log(...)` consumes — the ordering is a data dependency,
not a convention. Splitting this pair across the thread boundary is a named FAIL condition.

**Sites 1 and 2 are structurally simpler** — a single statement plus two `addMessage` lines:

```kotlin
// ChatPanel.kt:1008-1013 (openToolDialog)
// SC5: user-originated. The user picked the tool and typed the args in ToolInvocationDialog, so
// this call is deliberately UNGATED and consults no approval gate — double-prompting a decision
// the user just made trains them to click through (T-22-32).
val result = McpToolExecutor.executeTool(invocation.toolId, args, context, ToolCallOrigin.UserDialog)
panel.addMessage("Tool result: ${invocation.toolId}", result)
session.messages.add(ChatMessage("assistant", "Tool result (${invocation.toolId}):\n$result"))
state.toolsMode = true
```

```kotlin
// ChatPanel.kt:2316-2322 (/tool slash command)
val context = buildToolContext(settings, sessionId)
// SC5: user-originated. The user typed `/tool <name> <json>` themselves, so this call is
// deliberately UNGATED and consults no approval gate (T-22-32).
val result = McpToolExecutor.executeTool(toolName, argsJson, context, ToolCallOrigin.UserSlashCommand)
panel.addMessage("Tool result: $toolName", result)
state.toolsMode = true
state.toolCatalogSent = state.toolCatalogSent || argsJson != null
```

**Preserve the SC5 comments verbatim.** `ChatPanelToolGateTest.userDialogPathIsNotDoublePrompted`
(`:318-347`) reads `openToolDialog`'s **source text** and asserts it contains
`"McpToolExecutor.executeTool("` and `"ToolCallOrigin.UserDialog"` and does **not** contain
`ToolApprovalGate` / `ToolApprovalCard`. Moving the call into a lambda keeps those substrings; adding
a gate reference would not.

**Detekt signature constraints (do not widen):**
- `openToolDialog()` must stay parameterless — `detekt-baseline.xml:886`.
- `handleToolCommand(text, sessionId, panel, state, settings): Boolean` must keep its signature — `:889`.
- `cancelInFlightRequest(): Boolean` must keep `(): Boolean` — `:884`.

---

### `ui/ChatPanel.kt` — the marshalled tail and `ToolCallOutcome`

**Analog for the `invokeLater`-tail-branching-on-outcome shape:** `ChatPanel.kt:735-770` — the block
that already calls `maybeExecuteToolCall` inside `invokeLater` and branches:

```kotlin
if (allowToolCalls && state.toolsMode && toolContext != null) {
    SwingUtilities.invokeLater {
        // Map reads and panel.addMessage now run on the EDT (confinement fix).
        val outcome = maybeExecuteToolCall(sessionId = sessionId, userText = userText, ...)
        if (outcome == ToolCallOutcome.NOT_CHAINED) {
            onCompleted?.invoke(finalResp, null)
        }
    }
}
```

**Analog for the new enum value:** `ChatPanel.kt:1738-1747` — every value carries a one-line KDoc
saying *who discharges `onCompleted`*. Match that voice exactly:

```kotlin
private enum class ToolCallOutcome {
    /** No tool call, or it failed outright. The caller invokes `onCompleted` itself. */
    NOT_CHAINED,

    /** A followup turn was sent and carries `onCompleted` with it. */
    CHAINED,

    /** A card is on screen; `onCompleted` is parked in [PendingToolDecision] until the user clicks. */
    AWAITING_DECISION,
}
```

Both consumers (`:759`, `:2621`) test only `== NOT_CHAINED`, so `EXECUTING` falls through correctly
with zero edits there — but that is also how a continuation gets silently dropped. The KDoc line is
the mitigation, not an optional nicety.

**Detekt note:** `executeApprovedToolCall` is ~83 source lines at `ChatPanel.kt:2841-2923` and carries
**no** `LongMethod` baseline entry (threshold 80). Extract the marshalled tail into its own private
function — which is also what "one boundary a reader can see" asks for.

---

### `ui/ChatPanel.kt` — teardown / supersede wiring (modified)

**Analog: the file's own reviewed teardown template.**

```kotlin
fun cancelInFlightRequest(): Boolean {
    assertEdt()
    val conn = inFlightConnection.take() ?: return false   // <-- take the TOOL token BEFORE this line
    setSendingState(false)
    try {
        conn.stop()
    } catch (_: Exception) {
    }
    val sessionId = sessionsList.selectedValue?.id ?: return true
    val panel = sessionPanels[sessionId] ?: return true
    panel.addMessage("System", "Request cancelled.")
    return true
}
```
[`ChatPanel.kt:956-968`, with its REL-01 KDoc at `:948-955`]

Also the busy toggle D-06 reuses **unchanged**:

```kotlin
/** Toggle UI between sending and idle states */
private fun setSendingState(sending: Boolean) {
    isSending = sending
    sendBtn.isVisible = !sending
    cancelBtn.isVisible = sending
    inputArea.isEnabled = !sending
}
```
[`ChatPanel.kt:941-946`]

**The numbered-teardown-path comment convention is established and must be continued** — every exit
carries `// Teardown path N of 5.` plus a sentence on ordering:

```kotlin
// ChatPanel.kt:860-862 (deleteSession)
// Teardown path 2 of 5. Ahead of the try, so the card and its state are still intact even
// if removeChatSession throws and the finally strips the maps out from under it.
resolvePending(session.id, ImplicitDenyReason.SESSION_DELETED)
```

```kotlin
// ChatPanel.kt:1454-1458 (shutdown)
// Teardown path 5 of 5. Inside the marshalled block because pendingDecisions is
// @GuardedBy("EDT") like every other session map here. No backend turn is started from
// this path: dispatching a request while Burp tears down the extension classloader is how
// a safety control turns into an unload hang (T-22-33).
resolveAllPending(ImplicitDenyReason.UNLOAD)
cancelInFlightRequest()
sessionPanels.values.forEach { it.stopAllTimers() }
```

```kotlin
// ChatPanel.kt:1477-1482 (clearInMemorySessionState)
// Teardown path 4 of 5, reached from MainTab.onProjectChanged(). The same dangling
// continuation as a session delete, but across every session at once — and D-08 does not list
// it either. Resolved first, while the cards and the states behind them still exist.
resolveAllPending(ImplicitDenyReason.PROJECT_CHANGED)
cancelInFlightRequest()
```

**The measured trap (F-5):** `deleteSession` (`:851-890`) calls `resolvePending(...)` and **never**
`cancelInFlightRequest()`. Unload and project-change inherit the tool supersede for free through
`cancelInFlightRequest`; **session delete needs an explicit call added.** This is the single most
likely omission in the phase.

**Do not touch** `assertEdt()` (`ChatPanel.kt:783-787`) or any of its 6 call sites — byte-identity is
SC5's evidence:

```kotlin
/**
 * Asserts that the calling code is on the AWT Event Dispatch Thread.
 * Uses JVM assert (active under -ea in CI tests; no-op in production) so this never
 * changes prod behavior — the EDT-confinement test is the real SC1 gate, not this check.
 */
private fun assertEdt() {
    assert(SwingUtilities.isEventDispatchThread()) {
        "session maps must be touched on the EDT only — off-EDT access is a data race (REL-01)"
    }
}
```

---

### `mcp/tools/McpToolExecutorImpl.kt:137` — the throwing door guard (modified)

**Analog:** the function's own existing precondition style, plus the file's `@Suppress` comment voice.

```kotlin
fun executeToolResult(
    name: String,
    argsJson: String?,
    context: McpToolContext,
): CallToolResult {
    val resolvedName = canonicalToolId(name)

    // Phase 16 (CAP-02 / D-04): route ext:-prefixed tool calls to ExternalMcpClientManager.
    // Built-in Burp tools ALWAYS win when name does not start with "ext:" — the early return
    // below is the sole path for external tools (T-16-04-COL mitigation).
    if (resolvedName.startsWith("ext:")) {
        return routeExternalToolCall(name, resolvedName, argsJson, context)
    }

    val descriptor =
        McpToolCatalog.all().firstOrNull { it.id == resolvedName }
            ?: return errorResult("Unknown tool: $name")
    if (descriptor.proOnly && context.edition != BurpSuiteEdition.PROFESSIONAL) {
        return errorResult("Tool requires Burp Suite Professional: $resolvedName")
    }
```
[`McpToolExecutorImpl.kt:137-156`]

**Insert the guard as the FIRST statement — before `canonicalToolId` and before the `ext:` early
return**, or `routeExternalToolCall`'s `runBlocking` (`:1126`) escapes it and SC2's `runBlocking`
clause is unmet.

Use `check(...)` (→ `IllegalStateException`, CONVENTIONS.md:190 for "logical precondition failures"),
never `assert(...)` — `assert` is a no-op in shipped Burp, which is the whole point of D-03.

**The comment must justify the layering**, in the voice this file already uses for its `@Suppress`
block (`:1045-1051` — a paragraph explaining *why the unusual thing is the right thing*). The guard
imports `javax.swing.SwingUtilities` into a file under `mcp/`; a reviewer must be able to read that
as deliberate, not accidental. `isEventDispatchThread()` is a pure thread comparison with no AWT
initialisation, so it is headless-safe.

`executeTool` (`:1052-1064`) is only a text-extracting wrapper over `executeToolResult` — no guard
belongs there.

---

### `ui/SettingsPanelSettingsIO.kt:456-505` + `ui/SettingsPanelActions.kt:64-93` (modified)

**Body to move** (`applyAndSaveSettings`, `SettingsPanelSettingsIO.kt:455-505`). The precedent for
off-thread work *inside this very function* already exists at `:460`:

```kotlin
internal fun SettingsPanel.applyAndSaveSettings(updated: AgentSettings) {
    settings = updated
    settingsRepo.save(updated)
    // Re-prime the BountyPrompt cache off-thread so menu builds never touch disk (BApp #231, finding 2).
    UiActions.refreshBountyPromptCache(updated)
    AgentProfileLoader.setActiveProfile(updated.agentProfile)
    backends.reload()
    supervisor.applySettings(updated)
    audit.setEnabled(updated.auditEnabled)
    mcpSupervisor.applySettings(...)                      // → KtorMcpServerManager.stop()'s future.get(10s)
    com.six2dez.burp.aiagent.redact.Redaction
        .setCustomPatterns(updated.customRedactionPatterns)
    ...
    api.logging().logToOutput("AI Agent settings saved.")
    onSettingsChanged?.invoke(updated)                    // ┐
    refreshPassiveAiStatus()                              // │ the Swing tail — these five
    refreshActiveAiStatus()                               // │ marshal back via invokeLater
    updateProfileWarnings()                               // │
    updateRiskWarnings()                                  // ┘
}
```

Check `UiActions.refreshBountyPromptCache` is not double-dispatched once the whole body is on a
worker. Keep `audit.setEnabled` and `Redaction.setCustomPatterns` **contiguous** so the E8 window is
one readable block.

**Callers to restructure** (`SettingsPanelActions.kt:64-93`) — the banner + modal surfaces D-12 keeps:

```kotlin
fun SettingsPanel.saveSettings() {
    updateSaveFeedback("Saving settings...", DesignTokens.Colors.statusWarning)
    try {
        applyAndSaveSettings(currentSettings())          // currentSettings() reads Swing — stays on EDT
        updateSaveFeedback("Saved and applied.", DesignTokens.Colors.statusSuccess, resetMs = 3000)
    } catch (e: Exception) {
        updateSaveFeedback("Save failed: ${e.message ?: "unknown error"}", DesignTokens.Colors.statusError, resetMs = 5000)
        api.logging().logToError("AI Agent settings save failed: ${e.message}")
        JOptionPane.showMessageDialog(
            dialogParentComponent(),
            "Failed to save settings: ${e.message ?: "unknown error"}",
            "Custom AI Agent",
            JOptionPane.ERROR_MESSAGE,
        )
    }
}

fun SettingsPanel.restoreDefaultsWithConfirmation() {
    val confirmed = JOptionPane.showConfirmDialog(dialogParent, "Restore default settings? ...", ...)
    if (confirmed != JOptionPane.YES_OPTION) return
    val defaults = settingsRepo.defaultSettings()
    applySettingsToUi(defaults)
    applyAndSaveSettings(defaults)
    updateSaveFeedback("Defaults restored and applied.", DesignTokens.Colors.statusSuccess, resetMs = 3000)  // ← becomes a LIE when async
}
```

The `try` → success-banner / `catch` → banner+`logToError`+`JOptionPane` triple is the shape D-13's
completion callback must reproduce, once, for both callers. The confirm modal and `applySettingsToUi`
stay **before** dispatch.

**Detekt:** `applyAndSaveSettings` is ~51 lines with no `LongMethod` entry (threshold 80) — wrapping
it inline will likely cross. Split into an `applyAndSaveSettingsBody(updated)` plus a thin async
wrapper. If a helper would take ~10 captured values, prefer a small private `data class` capture
record (`LongParameterList` thresholds are 10/10).

---

### `ui/BottomTabsPanel.kt` + `SettingsPanel.kt` + `SettingsPanelActions.kt` — the busy seam (modified)

**Analog: the `setDialogParent` seam. Mirror all three of its lines, in the same three places.**

```kotlin
// 1. SettingsPanel.kt:49 — the field, among the other listener/parent fields
internal var dialogParent: JComponent? = null
```

```kotlin
// 2. SettingsPanelActions.kt:35-37 — the extension setter
fun SettingsPanel.setDialogParent(component: JComponent) {
    dialogParent = component
}
```

```kotlin
// 3. BottomTabsPanel.kt:93 — last line of init, after the layout is assembled
settingsPanel.setDialogParent(root)
```

The buttons the seam must reach are `private val` and styled with explicit opaque colours, so a plain
`isEnabled = false` will not visually read as disabled on `saveButton` without a recolor:

```kotlin
// BottomTabsPanel.kt:19-20
private val saveButton = JButton("Save settings")
private val restoreButton = JButton("Restore defaults")

// BottomTabsPanel.kt:61-69
saveButton.font = UiTheme.Typography.label
saveButton.background = UiTheme.Colors.primary
saveButton.foreground = UiTheme.Colors.onPrimary
saveButton.isOpaque = true
saveButton.border = EmptyBorder(8, 14, 8, 14)
saveButton.isFocusPainted = false
saveButton.addActionListener {
    settingsPanel.saveSettings()
}
```

Use `UiTheme.Colors.*` constants for the busy recolor — CONVENTIONS.md:210 forbids inline
`Color(...)`. Add a private `setActionsBusy(busy: Boolean)` on `BottomTabsPanel`; do not widen the
buttons' visibility, do not walk the component tree, do not add a static registry.

---

### `test/.../ui/ChatPanelEdtConfinementTest.kt`, `SettingsSaveAsyncTest.kt`, `McpToolExecutorEdtGuardTest.kt` (NEW)

**Analog: `src/test/kotlin/com/six2dez/burp/aiagent/ui/ChatPanelToolGateTest.kt` — 15 tests driving a
real headless `ChatPanel`. Match quality: exact.**

#### Fixture setup — `ChatPanelTestHarness.create` (`ChatPanelTestHarness.kt:79-134`)

```kotlin
fun create(
    // Deliberately has NO default value, matching the ContextPreviewDialog.kt:21-23 convention:
    // a default is how a future test silently asserts against the wrong model output.
    modelResponse: String,
    settings: AgentSettings = TestSettings.baselineSettings(),
): Harness {
    val api: MontoyaApi = mock(defaultAnswer = Answers.RETURNS_DEEP_STUBS)
    whenever(api.burpSuite().version().edition()).thenReturn(BurpSuiteEdition.COMMUNITY_EDITION)

    val supervisor: AgentSupervisor = mock(defaultAnswer = Answers.RETURNS_DEEP_STUBS)
    whenever(supervisor.requiresBurpAiAndDisabled(any())).thenReturn(false)
    whenever(
        // 13 matchers, one per parameter of AgentSupervisor.sendChat; nullable parameters use
        // anyOrNull() so an omitted optional argument still matches.
        supervisor.sendChat(any(), any(), any(), anyOrNull(), anyOrNull(), any(), any(),
                            any(), any(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull()),
    ).thenAnswer { invocation ->
        @Suppress("UNCHECKED_CAST")
        val onChunk = invocation.arguments[ON_CHUNK_INDEX] as (String) -> Unit
        @Suppress("UNCHECKED_CAST")
        val onComplete = invocation.arguments[ON_COMPLETE_INDEX] as (Throwable?) -> Unit
        onChunk(modelResponse)
        onComplete(null)
        null
    }

    val panel = ChatPanel(api = api, supervisor = supervisor, getSettings = { settings }, ... )
    panel.createNewSession()
    return Harness(panel, api, supervisor)
}
```

The **deep-stub `MontoyaApi` is the only tool-body seam that exists** (F-1). It is where a
`thenAnswer` captures `Thread.currentThread()` into an `AtomicReference<Thread>` — one capture that
satisfies "not the EDT", "isDaemon" and "named `burp-ai-tool*`" at once.

#### Driving the real UI — `sendUserMessage` (`ChatPanelTestHarness.kt:189-205`)

```kotlin
fun sendUserMessage(h: Harness, text: String) {
    SwingUtilities.invokeAndWait {
        val input =
            requireNotNull(find(h.panel.root, JTextArea::class.java) { it.isEditable }) {
                "No editable JTextArea found under ChatPanel.root — the input area lookup is stale."
            }
        val send =
            requireNotNull(find(h.panel.root, JButton::class.java) { it.text == "Send" }) {
                "No JButton labelled 'Send' found under ChatPanel.root — the button lookup is stale."
            }
        input.text = text
        send.doClick()
    }
}
```

Two load-bearing details documented at `:168-184`: the `isEditable` predicate (a `ToolApprovalCard`
also contains a `JTextArea`) and the *whole* interaction — tree walk included — running on the EDT,
because assigning `input.text` fires a `DocumentListener` that writes a `@GuardedBy("EDT")` map.
**This `invokeAndWait` is exactly why `slashCommandPathIsNotDoublePrompted` reaches
`executeToolResult` on the EDT today, and hence why the guard and all three call-site moves must land
in one commit.**

#### Decision-click helper and drain

```kotlin
click(card, "Deny for session")
ChatPanelTestHarness.drainEdt(times = LONG_DRAIN)
```
with the constants at the file's bottom:
```kotlin
private const val NO_CARD = "No ToolApprovalCard in the transcript — the SEC-06 gate did not raise a decision."
/** Comfortably longer than any chain this suite drives; over-draining an empty EDT queue is free. */
private const val LONG_DRAIN = 24
/** The four D-11 labels, defined once so "is this a decision button?" is asked one way. */
private val DECISION_LABELS = setOf("Deny", "Deny for session", "Approve once", "Approve for session")
```

```kotlin
fun drainEdt(times: Int = DEFAULT_EDT_DRAINS) {
    repeat(times) { SwingUtilities.invokeAndWait { } }
}
```
[`ChatPanelTestHarness.kt:211-213`]

**`drainEdt` is not sufficient after this phase** — it drains the EDT queue and knows nothing about a
daemon worker. It stays for EDT-queued work; `awaitToolSettled` is added beside it for worker-aware
synchronisation.

#### Deadlock failsafe, not a wall-clock threshold

```kotlin
// A non-monotone budget would loop forever; fail the suite instead of stalling CI.
assertTimeoutPreemptively(Duration.ofSeconds(30)) {
    ChatPanelTestHarness.sendUserMessage(h, "summarise the proxy history")
    ...
    ChatPanelTestHarness.drainEdt(times = ChatPanel.MAX_AUTO_TOOL_ITERATIONS * 2 + 4)
}
```
[`ChatPanelToolGateTest.kt:377-389`]

Copy this shape for SC3's mutual-latch handshake. Nothing compares a duration to a threshold — a
blocked EDT becomes a *deadlock* (categorical) rather than a *slow path* (timing-dependent). This is
the direct answer to the `RedactionTest` wall-clock flake documented in CONCERNS.md.

#### Ordering assertions — select by identity, never by position

`ChatPanelToolGateTest.kt` also carries the comment discipline for a vacuous-pass trap:

```kotlin
// ASSERT (b) FIRST, AND THAT IS THE POINT. Clauses (a) and (c) pass VACUOUSLY on an
// under-drained chain: a chain stopped at turn 5 by too few drains also "terminates" and also
// never reached Burp. Only the turn count fails loudly when the drain is short, so it is the
// clause that licenses the other two.
```
[`ChatPanelToolGateTest.kt:391-396`]

Carry the same reasoning to SC3's "eight results in submission order": key the assertion on
`traceId`, not list position (the Phase 22 lesson recorded in commit `ab55ff5`).

#### Structural-assertion fallback (only when headless driving is impossible)

```kotlin
@Test
fun userDialogPathIsNotDoublePrompted() {
    // ToolInvocationDialog extends JDialog with APPLICATION_MODAL, so constructing it under
    // `-Djava.awt.headless=true` throws HeadlessException and the dialog path cannot be driven
    // headlessly. Rather than weaken the claim, assert the equivalent property directly on the
    // call site, exactly as plan 22-07 prescribes for this case.
    val body = functionBody("fun openToolDialog()")
    assertTrue(body.contains("McpToolExecutor.executeTool("), "...")
}
```
[`ChatPanelToolGateTest.kt:318-347`]

**If a new structural assertion reads `McpToolExecutorImpl.kt`, `SettingsPanelSettingsIO.kt` or
`BottomTabsPanel.kt` from disk, the matching `inputs.file` declaration MUST land in the same commit**
(pattern below). `ChatPanel.kt` is already declared.

#### Class naming (hard constraint, from the fixture's own KDoc)

`ChatPanelTestHarness.kt:37-40`:
> `build.gradle.kts` excludes `*IntegrationTest`, `*ConcurrencyTest`, `*BackpressureTest`,
> `*RestartPolicyTest` and `*SupervisionTest` under `-PexcludeHeavyTests=true`, which is exactly what
> the PR gate passes. This object, and every test built on it, must avoid those suffixes.

Use `ChatPanelEdtConfinementTest`, `McpToolExecutorEdtGuardTest`, `SettingsSaveAsyncTest`.

---

### `test/.../ui/ChatPanelTestHarness.kt` — `awaitToolSettled` (modified)

**Analog: `AuditLogger.registerGlobalEmitter` install/clear, in this exact test class**
(`ChatPanelToolGateTest.kt:638-653`) — see [Shared Patterns §4](#4-global-test-observer-installclear-discipline).

Match the harness's own documentation voice: constants get a KDoc explaining *why that number*
(`ChatPanelTestHarness.kt:57-63`), and the fixture declares no `@Test` functions so no runner picks
it up (`:42-43`).

---

## Shared Patterns

### 1. EDT snapshot before dispatch, one `invokeLater` tail after

**Source:** `ui/MainTab.kt:190-215`
**Apply to:** `OffEdtDispatch`, all three `ChatPanel` call sites, `applyAndSaveSettings`

Read every Swing-owned / `@GuardedBy("EDT")` value into immutables *before* the `Thread`, cross the
boundary with only immutables, and return through exactly one `SwingUtilities.invokeLater`.
`currentSettings()` (settings) and `buildToolContext(...)` (`ChatPanel.kt:3005-3023`, an immutable
`McpToolContext` data class) already do this — preserve, do not re-derive on the worker.

### 2. `@GuardedBy("EDT")` + `assertEdt()` confinement (REL-01 — must not regress)

**Source:** `ChatPanel.kt:114-120` (the annotated maps), `:783-787` (`assertEdt`), `:948-955` (the
KDoc pattern for "this MUST run on the EDT; off-EDT callers marshal first")
**Apply to:** every new EDT-tail function

New EDT-side functions get the same KDoc form: name the guarded state they touch, name the
requirement ID, and say what off-EDT callers must do.

### 3. Named daemon threads

**Source:** `backends/http/MontoyaHttpTransport.kt:85` — `Thread(task, "montoya-http-offedt").apply { isDaemon = true }.start()`
**Apply to:** both `OffEdtDispatch` callers

`isDaemon = true` explicitly (D-08 depends on it), and a name (`burp-ai-tool-exec`,
`burp-ai-settings-save`) so a stuck worker is identifiable in a thread dump. English only.

### 4. Global test-observer install/clear discipline

**Source:** `audit/AuditLogger.kt:18-31` (production) + `ui/ChatPanelToolGateTest.kt:638-653` (test)
**Apply to:** `OffEdtDispatch.registerSettledObserver` and its harness wrapper

```kotlin
private val auditEvents = CopyOnWriteArrayList<Pair<String, Map<*, *>>>()

@BeforeEach
fun captureAuditEvents() {
    auditEvents.clear()
    AuditLogger.registerGlobalEmitter { type, payload ->
        if (payload is Map<*, *>) auditEvents += type to payload
    }
}

@AfterEach
fun releaseAuditEmitter() {
    // The emitter is a global singleton hook. Leaving it registered would have this class capturing
    // — and holding — events emitted by every test class that runs after it.
    AuditLogger.registerGlobalEmitter(null)
}
```

Copy all four elements: `CopyOnWriteArrayList` collector, `clear()` in `@BeforeEach`, register in
`@BeforeEach`, `register(null)` in `@AfterEach` **with the comment explaining why**. Also copy the
production-side KDoc convention: state that the hook is null in production and exists for tests.

### 5. Error handling — fail loudly, typed, never silently

**Source:** CONVENTIONS.md:188-190; `SettingsPanelActions.kt:64-79` (try/banner/log/modal);
`ChatPanel.kt:2856` (`runCatching` → `Result` → branch); `MontoyaHttpTransport.kt:87-89`
(`ExecutionException` unwrap: `throw e.cause ?: e`)
**Apply to:** the guard (`check(...)` → `IllegalStateException`), the worker wrapper, both EDT tails

Burp does not report exceptions thrown on background threads, so a worker escape must reach
`api.logging().logToError(...)`. The `Result` crosses the boundary intact; branch on it in the tail.

### 6. Decision comments that justify the unusual choice

**Source:** `McpToolExecutorImpl.kt:1045-1051` (the `@Suppress("UnusedParameter")` paragraph);
`ChatPanel.kt:735-739`, `:1454-1458`, `:860-862`; `MontoyaHttpTransport.kt:80-83`
**Apply to:** the guard's layering note, the `EXECUTING` KDoc, the E8 residual note, every teardown edit

The house style is a short paragraph naming the requirement/decision ID and the failure it prevents —
not a restatement of the code. CONVENTIONS.md:216-221 forbids temporal language: describe current
behaviour, never history. This is why `Redaction.kt:767-768`'s *"writes from the EDT (save)"* comment
must be **rewritten to name the settings worker**, not annotated with a change note.

### 7. `inputs.file` declaration for any source-text-reading test

**Source:** `build.gradle.kts:188-201`

```kotlin
// SEC-06 / SC4 / WR-09: ChatPanelToolGateTest reads this file from disk in `functionBody` to make
// the two structural assertions it cannot drive headlessly ... an edit that changes the source text
// but not the compiled bytecode — a comment replaced in place, a string reflowed — produces an
// identical cache key, so the test task is served from cache and the structural guard never runs in
// exactly the case it exists to catch.
inputs
    .file("src/main/kotlin/com/six2dez/burp/aiagent/ui/ChatPanel.kt")
    .withPropertyName("chatPanelStructuralSource")
    .withPathSensitivity(PathSensitivity.RELATIVE)
```

Four such declarations exist. Add one per newly source-read main file, in the same commit, with the
same three-part shape (path / `withPropertyName` / `withPathSensitivity`) and a comment naming what
reads it.

---

## Anti-Patterns — shapes that look right and are not

| Shape | Where it lives | Why it is wrong here |
|---|---|---|
| `task.get(timeout)` after offloading | `MontoyaHttpTransport.kt:84-90` | Offloads *and then blocks the EDT*. The single best code-review question for this phase: **after the dispatch line, no statement may block on the worker's result** — no `Future.get`, `Thread.join`, `CountDownLatch.await`, or `invokeAndWait` from worker back to EDT. |
| Bare `Thread { … }.start()` | `MainTab.kt:193` | Copy the *idiom* (SC6), not the *form*. No `isDaemon` → D-08's unload guarantee is false. |
| `assert(...)` for the new guard | `ChatPanel.kt:783-787` | No-op without `-ea`, i.e. in every shipped Burp. SC1 rules it out by name. Use `check(...)`. |
| Editing `assertEdt()` or its 6 call sites | `ChatPanel.kt:783` | Phase 26 / QUAL-07 owns it; byte-identity is SC5's cheapest evidence. |
| `SwingWorker` | — | A *new* concurrency idiom; SC6 explicitly warns against one. |
| Kotlin coroutines outside `mcp/` | — | CONVENTIONS.md:95. Use `java.util.concurrent`. |
| An injected `Executor` / dispatcher constructor param on `ChatPanel` | — | 11th parameter invalidates `detekt-baseline.xml:141`'s verbatim `LongParameterList` entry; the baseline is pinned at 1096 as a milestone metric. |
| A latch in an "executor test double" | — | `McpToolExecutor` is an `object` (`McpToolExecutorImpl.kt:45`) with no interface, referenced statically. **No such double can exist.** |
| Naming a new suite `*ConcurrencyTest` | `ChatPanelConcurrencyTest.kt` | Excluded by `build.gradle.kts:206-213` under the PR gate's `-PexcludeHeavyTests=true`. It is the *natural* name to reach for and it would silently disable the whole phase's suite. |
| `Thread.interrupt()` as a cancel | — | `Http.sendRequest` takes no cancellation token; works for `runBlocking`, silently no-ops for Montoya. D-07's discard contract is the honest one. |
| A `cancelled: Boolean` flag or generation counter | — | The CAS tracker (`ChatPanel.kt:69-83`) is the reviewed answer to the same problem, in the same file. |
| Regenerating `detekt-baseline.xml` | — | Phase gate requires `git diff --stat detekt-baseline.xml` empty. Extract functions instead. |

---

## No Analog Found

None. Every file in this phase has at least a role-match analog — which is the phase's defining
property. The three weakest matches, and their fallback:

| File | Role | Data Flow | Note |
|------|------|-----------|------|
| `mcp/tools/McpToolExecutorEdtGuardTest.kt` | test (unit) | request-response | No existing unit test calls `executeToolResult` directly. Assertion style from `ChatPanelToolGateTest`; the mechanism (call from `SwingUtilities.invokeAndWait`, assert `IllegalStateException`) is novel but trivial. |
| `ui/SettingsSaveAsyncTest.kt` | test (integration) | CRUD / file-I/O | No existing test drives a real `SettingsPanel`. Headless constructibility is **inference from absence** (A2) — spike it first; documented fallback is structural assertions on `SettingsPanelSettingsIO.kt` **plus** the matching `inputs.file` declaration. In-memory `Preferences` fake exists at `SettingsDefaultsPersistenceTest.kt:68`. |
| `ui/OffEdtDispatch.kt` | utility | dispatch+marshal | No single-file analog; composed from three exact partial analogs above. |

---

## Metadata

**Analog search scope:** `src/main/kotlin/com/six2dez/burp/aiagent/{ui,mcp/tools,backends/http,audit,redact}`,
`src/test/kotlin/com/six2dez/burp/aiagent/ui`, `build.gradle.kts`, `detekt-baseline.xml`
**Files read this session:** 14 (7 main, 4 test, 1 build, 2 planning inputs)
**Pattern extraction date:** 2026-08-20
