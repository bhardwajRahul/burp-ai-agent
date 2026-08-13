# Phase 22: Agent Tool-Call Trust Boundary - Pattern Map

**Mapped:** 2026-08-13
**Files analyzed:** 15 (8 new, 7 modified)
**Analogs found:** 15 / 15 (13 exact-or-strong, 2 partial)

Sources: `22-CONTEXT.md` (D-01..D-14), `22-RESEARCH.md` (§Recommended Project Structure, §Wave 0 Gaps),
`22-UI-SPEC.md` (§Component / Builder Usage Map). Every excerpt below was read from source at the
line numbers given.

---

## File Classification

| New/Modified File | New? | Role | Data Flow | Closest Analog | Match Quality |
|-------------------|:----:|------|-----------|----------------|---------------|
| `src/main/kotlin/com/six2dez/burp/aiagent/mcp/ToolCallOrigin.kt` | NEW | model (sealed type) | transform | `mcp/McpAccessControlDecision.kt:62-101` | exact |
| `src/main/kotlin/com/six2dez/burp/aiagent/mcp/ToolApprovalGate.kt` | NEW | service (pure policy + state machine) | event-driven | `mcp/McpAccessControlDecision.kt` + `mcp/McpBlockedRequestReporter.kt` | exact |
| `src/main/kotlin/com/six2dez/burp/aiagent/ui/components/ToolApprovalCard.kt` | NEW | component (Swing) | event-driven | `ui/components/ActionCard.kt` (layout) + `ui/components/SubtleNotice.kt` (accent strip, `updateUI`) + `ui/components/SafetyIndicator.kt` (`initialized` guard) | role-match (composite) |
| `src/main/kotlin/com/six2dez/burp/aiagent/mcp/McpToolCatalog.kt` | MOD | model / static catalog | transform | itself (`:5-14`, `:19-33`) | self |
| `src/main/kotlin/com/six2dez/burp/aiagent/mcp/tools/McpToolExecutorImpl.kt` | MOD | service (executor) | request-response | itself (`:1019-1039`, `:1114-1121`) | self |
| `src/main/kotlin/com/six2dez/burp/aiagent/ui/ChatPanel.kt` | MOD | controller / panel | event-driven + request-response | itself (`:310-357`, `:660-694`, `:2158-2197`) | self |
| `DECISIONS.md` | MOD | doc (ADR) | — | `DECISIONS.md:140-151` (ADR-13) | exact |
| `build.gradle.kts` | MOD | config (build) | — | `build.gradle.kts:152-154` (`jvmArgs("-ea")`) | exact |
| `src/test/kotlin/com/six2dez/burp/aiagent/mcp/SecTierResolutionTest.kt` | NEW | test (unit, parity) | transform | `mcp/tools/McpToolParityTest.kt` | exact |
| `src/test/kotlin/com/six2dez/burp/aiagent/mcp/McpToolCatalogTierParityTest.kt` | NEW | test (unit, invariant) | transform | `mcp/tools/McpToolParityTest.kt:17-22` | exact |
| `src/test/kotlin/com/six2dez/burp/aiagent/mcp/ToolApprovalGateTest.kt` | NEW | test (unit, state machine + audit) | event-driven | `mcp/BlockedRequestReporterTest.kt` | exact |
| `src/test/kotlin/com/six2dez/burp/aiagent/ui/ChatPanelTestHarness.kt` | NEW | test fixture (Swing) | — | `mcp/tools/McpToolParityTest.kt:44-45` (deep stub) + `TestSettings.kt` | partial — no headless-Swing harness exists |
| `src/test/kotlin/com/six2dez/burp/aiagent/ui/ChatPanelToolGateTest.kt` | NEW | test (integration, real panel) | event-driven | none in repo (`ChatPanelConcurrencyTest.kt` is a deliberate anti-analog) | partial |
| `src/test/kotlin/com/six2dez/burp/aiagent/ui/McpToolTabModelTest.kt` | MOD | test (unit) | transform | itself (`:21-38`) | self |
| `.planning/codebase/CONCERNS.md` | MOD | doc | — | itself | self |

**Not in this phase (confirmed by reading):** `mcp/McpRuntimeContextFactory.kt` and
`mcp/tools/McpToolHandlers.kt` are untouched — the origin parameter goes on `executeTool`, and
`McpToolHandlers.kt:129` calls `executeToolResult`, whose signature does not change.

---

## Pattern Assignments

### `mcp/ToolCallOrigin.kt` (model, sealed type) — NEW

**Analog:** `src/main/kotlin/com/six2dez/burp/aiagent/mcp/McpAccessControlDecision.kt`

This file is the repo's existing "pure, sealed, AWT-free, test-reachable decision type". Copy four
things from it: the file-header comment naming the test seam, the `internal`-not-`private` visibility
convention, the enum-with-`wireValue` shape (which the audit payload and the UI both read so the two
sinks cannot disagree), and the sealed outcome type.

**File-header / test-seam convention** (`McpAccessControlDecision.kt:8-13`):
```kotlin
// SEC-04 / SEC-05: the pure decision half of the MCP access-control gate.
//
// Everything in this file is `internal` rather than `private` so McpAccessControlDecisionTest can
// reach it without reflection — the same test-seam convention used by redact/Redaction.kt:215-228.
// No server-side Ktor package is imported here — only `io.ktor.http`, which is engine-free. That is
// what keeps the decision provable without binding a port.
```
For this phase the equivalent sentence is: *no `javax.swing` / `java.awt` import here — that is what
keeps the SEC-06 decision provable without a Swing harness.*

**Enum with a single wire string** (`McpAccessControlDecision.kt:62-75`) — the model for
`SecTier`'s and `ToolDecision`'s log tokens (`approve_once`, `deny_session`, `implicit_deny`, …):
```kotlin
/**
 * Why a request was denied. The wire string is defined once, here, and nowhere else — both the
 * audit payload and the Output-tab line read [wireValue] so the two sinks cannot disagree (D-06).
 */
internal enum class BlockReason(
    val wireValue: String,
) {
    ORIGIN_MISMATCH("origin_mismatch"),
    HOST_MISMATCH("host_mismatch"),
    ...
}
```

**Sealed outcome type** (`McpAccessControlDecision.kt:92-101`) — the model for `ToolCallOrigin`:
```kotlin
/** Outcome of [evaluate]: either the request proceeds, or it is refused with a status and a reason. */
internal sealed class GateDecision {
    data object Allow : GateDecision()

    data class Deny(
        val status: HttpStatusCode,
        val reason: BlockReason,
        val facts: RequestFacts,
    ) : GateDecision()
}
```
**Delta this phase adds:** `ModelApproved` must have a **file-private constructor** with the gate
declared in the same file (RESEARCH §4: Kotlin `internal` is module-wide and does not bind the
factory to the gate). `GateDecision` does not need that, so this is the one place the analog is
extended rather than copied.

**"No default value" precedent to cite in the KDoc** (`ui/components/ContextPreviewDialog.kt:21-23`):
```kotlin
// D-07: deliberately has NO default value. There is exactly one caller, and a default is how a
// future caller silently gets the OFF hint wrong.
```
D-03 cites this verbatim. The same comment shape belongs on `McpToolDescriptor.secTier` and on the
`origin` parameter of `executeTool`.

---

### `mcp/ToolApprovalGate.kt` (service, pure policy + state machine) — NEW

**Analog A — purity contract:** `mcp/McpAccessControlDecision.kt:103-114`
```kotlin
/**
 * Decides allow-or-deny for one MCP request from [facts] plus an immutable [settings] snapshot.
 *
 * Pure by contract (D-08): no logging, no audit events, no I/O, no side effects of any kind. The
 * caller invokes [McpBlockedRequestReporter] from the [GateDecision.Deny] branch — the reporter is
 * never invoked from inside this function. That separation is what makes every row of the
 * access-control matrix assertable in a unit test with no server and no clock.
 */
internal fun evaluate(
    facts: RequestFacts,
    settings: McpSettings,
): GateDecision = if (settings.externalEnabled) evaluateExternal(facts, settings) else evaluateLocal(facts, settings)
```
Apply the same split here: `tierFor(rawToolName)` and the decision resolution stay pure; the audit
emission is a separate call the `ChatPanel` caller makes from the resolved branch.

**Analog B — sanitize / hash / dual-sink / injected clock:** `mcp/McpBlockedRequestReporter.kt`

Constructor seams (`:69-72`):
```kotlin
internal class McpBlockedRequestReporter(
    private val logToOutput: (String) -> Unit,
    private val verboseAudit: Boolean = false,
) {
```
The KDoc at `:38-47` states why the output sink is a lambda ("tests capture into a list … keeps this
class testable without a Mockito deep stub") and why `verboseAudit` is a seam wired to `false`
("`AgentSettings` has `auditEnabled` but no verbose-audit flag anywhere in the repo"). Both apply
verbatim to the SC3 emitter.

Sanitize (`:21-27` and `:220-233`) — **copy, do not re-derive**. UI-SPEC Rule T-6 needs an *inline*
variant (this one) and a *block* variant that preserves `\n`/`\t`:
```kotlin
/**
 * C0 controls and DEL via `\p{Cntrl}`, plus the C1 range spelled out — Java's `\p{Cntrl}` covers only
 * `\x00-\x1F` and `\x7F` unless UNICODE_CHARACTER_CLASS is set, and D-07 names C0 *and* C1.
 */
private val controlCharRegex = Regex("[\\p{Cntrl}\\u0080-\\u009F]")

private val whitespaceRegex = Regex("\\s+")

/**
 * D-07: make an attacker-controlled value safe to write into a log line. Control characters are
 * REMOVED rather than replaced, so `"a\r\nInjected: line"` collapses to `"aInjected: line"` and a
 * forged second log line is impossible (CWE-117).
 */
private fun sanitize(value: String?): String? =
    value?.let { raw ->
        val cleaned =
            controlCharRegex
                .replace(raw, "")
                .replace(whitespaceRegex, " ")
                .trim()
        if (cleaned.length > MAX_HEADER_VALUE_LENGTH) cleaned.take(MAX_HEADER_VALUE_LENGTH).trimEnd() + "..." else cleaned
    }
```

Hash-by-default (`:171-172`):
```kotlin
/** D-10: sanitize, then hash unless the caller opted into plaintext. Null in, null out. */
private fun auditValue(value: String?): String? = sanitize(value)?.let { if (verboseAudit) it else Hashing.sha256Hex(it) }
```
`Hashing.sha256Hex` is `audit/Hashing.kt:7-13` — one line, already used by `McpTool.runTool`. Do not
hand-roll a digest.

Ordered payload map (`:156-169`) — `linkedMapOf`, so key order is asserted in the test:
```kotlin
private fun buildPayload(...): Map<String, Any?> =
    linkedMapOf(
        "reason" to deny.reason.wireValue,
        "mode" to if (externalMode) "external" else "local",
        ...
        "userAgent" to auditValue(deny.facts.userAgent),
    )
```

Injected clock (`:89-96`): `nowMs` is a parameter, not `System.currentTimeMillis()` inside the class.
Only needed here if the gate timestamps decisions — RESEARCH §5 says **Phase 20 D-09 rate limiting
is NOT needed** for SEC-06 (the ceiling is `MAX_AUTO_TOOL_ITERATIONS = 8`); state that explicitly
rather than importing the `ReasonWindow`/CAS machinery.

**Analog C — the fail-closed lookup shape:** `mcp/tools/McpToolExecutorImpl.kt:150-152`
```kotlin
val descriptor =
    McpToolCatalog.all().firstOrNull { it.id == resolvedName }
        ?: return errorResult("Unknown tool: $name")
```
The gate's `tierFor` must use the **same** `firstOrNull { it.id == canonical }` lookup against the
**same** canonicalised name, and fall through to `CONFIRM_EACH` (never `AUTO`).

**Analog D — canonicalisation must be shared, not copied:** `McpToolExecutorImpl.kt:1114-1121`
```kotlin
private fun resolveAlias(toolName: String): String =
    when (toolName.trim().lowercase()) {
        "history", "proxy_history", "requests" -> "proxy_http_history"
        "history_regex", "proxy_history_regex" -> "proxy_http_history_regex"
        "ws_history", "websocket_history", "websocket" -> "proxy_ws_history"
        "sitemap", "site_map_history" -> "site_map"
        else -> toolName
    }
```
Ten alias inputs, four canonical IDs. `executeToolResult:141` applies it **before** the `ext:` check
at `:146` (`resolvedName.startsWith("ext:")`). The gate must call the same function — expose it as
`fun canonicalToolId(name: String): String` and have `resolveAlias` delegate, or rename it. A `when`
block or `mapOf` of aliases appearing anywhere under `ui/` is the warning sign (RESEARCH Pitfall 2).

---

### `ui/components/ToolApprovalCard.kt` (component, Swing) — NEW

Three analogs, each supplying a different piece. UI-SPEC §"Why a new component instead of extending
`ActionCard`" is authoritative on what **not** to copy.

**Analog A — layout skeleton:** `ui/components/ActionCard.kt:65-95`
```kotlin
val constraints =
    GridBagConstraints().apply {
        gridx = 0
        gridy = 0
        anchor = GridBagConstraints.WEST
        fill = GridBagConstraints.HORIZONTAL
        weightx = 1.0
        insets = Insets(8, 10, 0, 10)
    }
add(actionLabel, constraints)

constraints.gridy++
constraints.insets = Insets(4, 10, 0, 10)
add(sourceLabel, constraints)
```
Copy the single-column `GridBagLayout` + `constraints.gridy++` idiom. **Do NOT copy** the insets
(`8,10,0,10` / `4,10,0,10` / `6,10,10,10` are off the 4 px grid — UI-SPEC §Row insets replaces them
with `Insets(0, 0, Spacing.xs, 0)` / `Insets(0, 0, Spacing.sm, 0)` / `Insets(Spacing.sm, 0, 0, 0)`),
the `UiTheme` import at `:3`, the `Colors.surface` background at `:34`, or the nested `JScrollPane`
at `:30`/`:94`.

**Expand/collapse** (`ActionCard.kt:97-116`) — the idiom to reuse for the args toggle:
```kotlin
fun setExpanded(value: Boolean) {
    if (expanded == value) {
        return
    }
    expanded = value
    updateExpandedState()
}

fun setPayloadPreview(raw: String) {
    val trimmed = limitLines(raw, 50)
    previewArea.text = trimmed
    previewArea.caretPosition = 0
}

private fun updateExpandedState() {
    previewScroll.isVisible = expanded          // hidden, not zero-height — keeps preferredSize correct
    toggleButton.text = if (expanded) "Hide payload preview" else "Show payload preview"
    revalidate()
    repaint()
}
```
`limitLines(raw, 50)` at `:118-130` is the anchor UI-SPEC tightens to 40 lines; add a **character**
cap on top (3200 preview / 40 000 ceiling).

Glyph toggle text comes from `ui/components/AccordionPanel.kt:101-106` (not `ActionCard`'s wording):
```kotlin
private fun updateExpandedState() {
    contentPanel.isVisible = expanded
    toggleLabel.text = if (expanded) "▼" else "▶"
    revalidate()
    repaint()
}
```

**Analog B — accent strip + `updateUI()` re-apply:** `ui/components/SubtleNotice.kt:44-46, 91-98, 113-120`
```kotlin
// `lateinit` so we can guard `updateUI()` against the L&F firing before the field is set
// during super-constructor chain (`JPanel(BorderLayout)` → `JComponent` may call updateUI()).
private lateinit var body: JTextPane

override fun updateUI() {
    super.updateUI()
    // Re-apply colors / borders after a Burp theme switch. Guarded because `super.updateUI()`
    // can fire during the super-constructor chain before our `body` field is initialised.
    if (::body.isInitialized) {
        applyStyle()
    }
}

border =
    BorderFactory.createCompoundBorder(
        BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(UiTheme.Colors.outlineVariant, 1, true),
            BorderFactory.createMatteBorder(0, 3, 0, 0, accent),
        ),
        BorderFactory.createEmptyBorder(8, 12, 8, 12),
    )
```
This is exactly the 3 px accent strip + 1 px outline + `EmptyBorder(8, 12, 8, 12)` structure
UI-SPEC §"Card structure" specifies — with `DesignTokens.Colors.border` / `Spacing.sm` / `Spacing.md`
substituted for the `UiTheme` reads and the literal `8, 12`.

**Analog C — `initialized`-flag guard variant + self-describing text:** `ui/components/SafetyIndicator.kt:21-34, 50-57`
```kotlin
private var level: Level = Level.OK

// `JLabel.<init>` invokes `updateUI()` BEFORE the Kotlin field initialiser for `level` runs,
// so any access to `level` from inside that callback would NPE. The flag is zero-initialised
// to `false` by the JVM (so the guard short-circuits during super-construction) and flipped
// to `true` only after our own `init {}` block has finished.
private var initialized = false

init {
    isOpaque = true
    font = UiTheme.Typography.body
    initialized = true
    applyStyle()
}

override fun updateUI() {
    super.updateUI()
    if (initialized) {
        applyStyle()
    }
}
```
`AccordionPanel.kt:28, 49, 72-80` uses the identical `initialized` flag. Use the flag form (not
`lateinit`) because the card has several non-nullable child fields.
**Do NOT copy** `SafetyIndicator`'s filled pill (`applyStyle():59-69` sets
`foreground = onPrimary` over a status background) — UI-SPEC §"Badge fill" computes that pairing at
≈2.70:1 and rejects it. Use the outlined badge instead.

**Analog D — design-system builders (reuse as-is, do not reimplement):** `ui/design/Components.kt`

| Need | Builder | Lines |
|------|---------|-------|
| Captions, trust label, truncation footers, session-scope footer | `helpLabel(text)` | `:331-335` |
| Model-supplied tool ID region | `applyFieldStyle(field)` | `:484-489` |
| Model-supplied args region | `applyAreaStyle(area)` | `:498-505` |
| Tier badge structure (private copy, per FLAG-22-05) | `toolBadge(label, style)` | `:426-472` |

`toolBadge`'s two-part theme handling is the part to replicate (`:441-471`):
```kotlin
return object : JLabel(label) {
    override fun paintComponent(g: Graphics) {
        val g2d = g as Graphics2D
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        // Re-read the current theme color at paint time so the background always reflects the
        // active Burp theme, even if the panel was created under a different theme (FLAG-10-01).
        g2d.color = ...
        g2d.fillRoundRect(0, 0, width, height, 6, 6)
        super.paintComponent(g)
    }

    // FLAG-10-01: reapply foreground from current DesignTokens on theme switch.
    override fun updateUI() {
        super.updateUI()
        foreground = ...
    }
}.apply {
    isOpaque = false
    font = DesignTokens.Typography.caption
    foreground = fgColor
    border = EmptyBorder(2, 6, 2, 6)
}
```

**Anti-example to avoid, in-repo:** `ui/components/PrivacyPill.kt:11-13` hardcodes
`Color(0x1B9E5A)` / `Color(0xF9A825)` / `Color(0xB3261E)`. UI-SPEC Light/Dark rule 1 forbids any
`Color(0x…)` literal in the new file.

**Token names verified present** in `ui/design/DesignTokens.kt`: `Spacing.xs/sm/md/lg/xl` (`:42-54`),
`Typography.sectionTitle/body/caption/label/mono` (`:98-110`), `Colors.surface` (`:128`),
`onSurface` (`:131`), `onSurfaceVariant` (`:134`), `cardSurface` (`:141`), `border` (`:154`),
`borderSubtle` (`:157`), `inputBackground` (`:161`), `inputForeground` (`:165`),
`statusSuccess` (`:177`), `statusError` (`:181`), `statusWarning` (`:185`).

---

### `mcp/McpToolCatalog.kt` (model / static catalog) — MODIFIED

**Analog:** itself. The file has one `data class` and 59 all-named-argument construction sites.

**Current descriptor** (`:5-14`) — insert `secTier` adjacent to `unsafeOnly` so the two axes read
together (RESEARCH §1 recommendation), and give it **no default**:
```kotlin
data class McpToolDescriptor(
    val id: String,
    val title: String,
    val description: String,
    val category: String,
    val defaultEnabled: Boolean,
    val proOnly: Boolean = false,
    val unsafeOnly: Boolean = false,
    val nativeTool: Boolean = false, // true = extension-native; present in the BApp Store build
)
```

**Entry shape to extend, ×59** (`:19-33` is representative):
```kotlin
McpToolDescriptor(
    id = "status",
    title = "Extension status",
    description = "Returns basic extension and Burp version status.",
    category = "Extension",
    defaultEnabled = true,
    nativeTool = true,
),
```

**Measured constraints for the mechanical diff:**
- 59 descriptors, not 60 (`grep -c 'McpToolDescriptor('` counts the `data class` line).
- All 59 use named arguments; **one** other construction site exists —
  `src/test/kotlin/.../ui/McpToolTabModelTest.kt:29-38`.
- Parameter count goes 8 → 9; `detekt.yml:8-10` sets `LongParameterList.constructorThreshold: 10`,
  so this stays under. Verified in `detekt.yml`.
- `SecTier` must be an **enum type**, never `Boolean`/`String`, so a hypothetical positional site
  produces a type error rather than a silent argument shift.

**Accessors that must keep working unchanged** (`:471-483`) — the tier is a second axis, not a
replacement for `unsafeToolIds()`:
```kotlin
fun all(): List<McpToolDescriptor> = tools

fun available(storeBuild: Boolean = BuildFlags.STORE_BUILD): List<McpToolDescriptor> = if (storeBuild) tools.filter { it.nativeTool } else tools

fun defaults(): Map<String, Boolean> = tools.associate { it.id to it.defaultEnabled }

fun unsafeToolIds(): Set<String> = tools.filter { it.unsafeOnly }.map { it.id }.toSet()
```

---

### `mcp/tools/McpToolExecutorImpl.kt` (service, executor) — MODIFIED

**Analog:** itself.

**Signature to extend** (`:1019-1039`). Add a required, non-defaulted `origin: ToolCallOrigin`.
`executeToolResult` (`:136`) is **not** changed — that is what keeps `McpToolHandlers.kt:129` and the
MCP-server path compiling untouched.
```kotlin
fun executeTool(
    name: String,
    argsJson: String?,
    context: McpToolContext,
): String {
    val result = executeToolResult(name, argsJson, context)
    val text =
        result.content
            .filterIsInstance<TextContent>()
            .map { it.text?.toString().orEmpty() }
            .joinToString("\n")
    val isError = result.isError == true
    if (text.startsWith("Unknown tool:") || text.startsWith("Tool requires Burp Suite Professional:")) {
        return text
    }
    return if (isError && text.isNotBlank()) {
        "Error: $text"
    } else {
        text.ifBlank { "Tool executed: ${resolveAlias(name)}" }
    }
}
```
The `origin` is **declared, not consumed** here (RESEARCH §4): document it in KDoc using the
`ContextPreviewDialog.kt:21-23` "no default value" comment shape; do not add runtime branching.

**Canonicalisation to expose:** `:1114-1121` (`resolveAlias`, currently `private`) — see
`ToolApprovalGate.kt` §Analog D above.

**Deliberate omission to record:** `describeTools`/`buildToolPreamble` (`:99-134`) already emit
`[unsafe]`, `[pro]` and `[external]` markers. Do **not** add an `[auto]` marker — it hands an
injected prompt a map of which tools run silently (RESEARCH §Anti-Patterns). ADR-15 records this.

---

### `ui/ChatPanel.kt` (controller / panel) — MODIFIED

**Analog:** itself. Additive only — the file is 2248 lines and its split is out of scope for v0.10.0.

**Pattern 1 — inserting a card into the transcript** (`:341-344`, the only existing caller):
```kotlin
val panel = sessionPanels[session.id] ?: return
val state = sessionStates[session.id] ?: ToolSessionState()
val actionCard = buildActionCard(capture, spec.actionName, prompt, session.id, state)
panel.addComponent(actionCard)
```
`SessionPanel.addComponent` (`:1677-1688`) already wraps the component so it cannot stretch:
```kotlin
fun addComponent(component: JComponent) {
    // Wrap in a panel that prevents vertical stretching
    val wrapper =
        object : JPanel(BorderLayout()) {
            override fun getMaximumSize(): Dimension = Dimension(super.getMaximumSize().width, preferredSize.height)
        }
    wrapper.isOpaque = false
    wrapper.add(component, BorderLayout.CENTER)
    messages.add(wrapper)
    messages.add(javax.swing.Box.createRigidArea(Dimension(0, 4)))
    refreshScroll()
}
```
The wrapper sets `isOpaque = false`, which is why UI-SPEC requires the card itself to be
`isOpaque = true` with `background = Colors.cardSurface`.
**Latent defect to fix while in the file** (RESEARCH §7): `:342` reads
`sessionStates[session.id] ?: ToolSessionState()` and discards the fallback instead of storing it —
harmless today, but it would silently drop D-10's approval set. `:453` and `:514` use the correct
`sessionStates.getOrPut(session.id) { ToolSessionState() }` form.

**Pattern 2 — `SessionPanel.scrollToComponent` sibling** (`:1711-1717`, the idiom to mirror):
```kotlin
private fun refreshScroll() {
    messages.revalidate()
    SwingUtilities.invokeLater {
        val scrollBar = scroll.verticalScrollBar
        scrollBar.value = scrollBar.maximum
    }
}
```
Revalidate, then `invokeLater` so the scroll runs after layout.

**Pattern 3 — the state to extend** (`:1601-1604`). D-10's approve/deny sets and FLAG-22-01's repeat
counter live here:
```kotlin
private data class ToolSessionState(
    var toolsMode: Boolean = true,
    var toolCatalogSent: Boolean = false,
)
```
Map declarations at `:105-115` carry `@GuardedBy("EDT")`:
```kotlin
@GuardedBy("EDT")
private val sessionStates = linkedMapOf<String, ToolSessionState>()
```
Any new pending-decision map must carry the same annotation and the same EDT discipline.

**Pattern 4 — the REL-01 continuation block that changes shape** (`:660-694`):
```kotlin
// REL-01: maybeExecuteToolCall reads sessionPanels/sessionsById and calls
// panel.addMessage — all EDT-confined map/Swing operations.  This callback
// runs on the backend executor thread (NOT the EDT), so marshal the
// map-touching + Swing-mutating body onto the EDT via invokeLater.
if (allowToolCalls && state.toolsMode && toolContext != null) {
    SwingUtilities.invokeLater {
        val chained =
            maybeExecuteToolCall(
                sessionId = sessionId, userText = userText, responseText = finalResp,
                context = toolContext, remainingToolIterations = toolIterationsLeft,
                traceId = traceId, onCompleted = onCompleted,
            )
        if (!chained) {
            onCompleted?.invoke(finalResp, null)
        }
    }
} else {
    onCompleted?.invoke(finalResp, null)
}
```
`Boolean` → three-valued outcome; `AWAITING_DECISION` parks `onCompleted`. Nothing else in this block
moves. `assertEdt()` (`:702-711`) and its KDoc stay as-is:
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

**Pattern 5 — the telemetry call SC3 extends** (`:2158-2174`). Extend the `metadata` map; do not
invent a shape. Note `AiRequestLogger.log` takes `metadata: Map<String, String>` (`AiRequestLogger.kt:102`),
so every value must be a `String`:
```kotlin
val result = resultOutcome.getOrThrow()
val status = if (result.startsWith("Error:")) "error" else "ok"
supervisor.aiRequestLogger?.log(
    type = ActivityType.MCP_TOOL_CALL,
    source = "chat",
    backendId = backendId,
    sessionId = sessionId,
    detail = "Tool ${call.tool} executed",
    durationMs = durationMs,
    metadata =
        mapOf(
            "operation" to "tool_chain",
            "status" to status,
            "traceId" to traceId,
            "step" to chainStep.toString(),
            "toolName" to call.tool,
            "resultChars" to result.length.toString(),
        ),
)
```
Adds per RESEARCH §5: `"denied"` as a third `status`, plus `secTier`, `decision`,
`implicitDenyReason`, `argsSha256`. `toolName` becomes the **canonicalised** ID.

**Pattern 6 — the followup template needing a denial variant** (`:2176-2195`):
```kotlin
val followup =
    buildString {
        appendLine("Tool result for ${call.tool}:")
        appendLine(result)
        appendLine()
        appendLine("User request:")
        appendLine(userText)
        appendLine()
        appendLine("Provide the final response using the tool result.")
    }.trim()
sendMessage(
    sessionId, followup,
    contextJson = null,
    allowToolCalls = remainingToolIterations > 1,
    actionName = "Tool Followup",
    onCompleted = onCompleted,
    toolIterationsLeft = (remainingToolIterations - 1).coerceAtLeast(0),
    traceId = traceId,
)
```
D-13's monotone decrement is already here — the denial branch reuses this exact `sendMessage` handoff
with a denial-variant `followup`. The last line must not say "using the tool result".

**Pattern 7 — terminate-without-a-turn, for implicit denials** (`:320-323`):
```kotlin
) {
    onCompleted?.invoke("", InterruptedException("Context preview cancelled by user"))
    return
}
```
This is the existing "user cancelled, resolve the continuation and stop" idiom. Reuse it for
`resolvePending`, with a non-exception payload (D-12: denial is not an error).

**Pattern 8 — the five implicit-denial call sites** (all measured):

| # | Site | Line | Current shape |
|---|------|------|---------------|
| 1 | `sendFromInput()` | `:448-470` | resolve **before** `sendMessage` at `:463` |
| 2 | `deleteSession()` | `:775-812` | removes from `sessionPanels`/`sessionStates` in a `finally` block at `:786-793` |
| 3 | `clearCurrentChat()` | `:971-1004` | `panel.clearMessages()` at `:983`; already resets `state.toolCatalogSent`/`toolsMode` at `:994-998` — the natural place to clear the approval sets too |
| 4 | `clearInMemorySessionState()` | `:1348-1356` | `sessionStates.clear()` at `:1353`; called from `MainTab.kt:819` inside `onProjectChanged()` (`MainTab.kt:814`) |
| 5 | `shutdown()` | `:1322-1342` | `invokeAndWait` from a Montoya thread; **never call `sendMessage` from here** |

**Pattern 9 — the session-list pending marker** (`:1625-1643`). Add a third `JLabel` to `textPanel`;
match `backendLabel`'s font, not `Typography.caption` (UI-SPEC §"From outside the session"):
```kotlin
val textPanel = JPanel()
textPanel.layout = BoxLayout(textPanel, BoxLayout.Y_AXIS)
textPanel.isOpaque = false

val titleLabel = JLabel(value.title)
titleLabel.font = label.font
titleLabel.foreground = if (isSelected) list.selectionForeground else list.foreground

val infoText = "$backendText  ·  $dateStr"
val backendLabel = JLabel(infoText)
backendLabel.font = label.font.deriveFont((label.font.size - 2).toFloat())
backendLabel.foreground = if (isSelected) list.selectionForeground else UiTheme.Colors.onSurfaceVariant
```
Refresh via `refreshSessionList()` (`:1248-1257`), which re-sets every model element.

**Pattern 10 — the headless guard (Wave 0)** (`:377-380`), the single headless-hostile call in the
whole construction path:
```kotlin
val menuMask =
    java.awt.Toolkit
        .getDefaultToolkit()
        .menuShortcutKeyMaskEx
```
`InputEvent` is already imported (`:38`). RESEARCH Pitfall 8 prefers an explicit
`GraphicsEnvironment.isHeadless()` check plus a narrow `catch (_: HeadlessException)` over a broad
`runCatching`, with a KDoc line naming the SC4 test as the reason the guard exists.

**Pattern 11 — the two ungated user-originated call sites** (SC5 — they must stay ungated but become
declarative):
```kotlin
// :928  — ToolInvocationDialog
val result = McpToolExecutor.executeTool(invocation.toolId, args, context)

// :2105 — /tool slash command
val result = McpToolExecutor.executeTool(toolName, argsJson, context)
```

---

### `DECISIONS.md` (doc, ADR-15) — MODIFIED

**Analog:** `DECISIONS.md:140-151` (ADR-13). ADR-15 is the next free number (ADR-14 at `:153` is
Phase 21).

**Structure to copy — three bold-labelled sections, no sub-headings:**
```markdown
## ADR-13: Coalesce the MCP transport-block audit event for pre-authentication denials (amends D-06)

**Context.** <threat, with file:line anchors and measured facts> …

**Decision.** <what ships, plus "Explicitly reject the alternative of …"> …

**Consequences.**
- <bullet>
- Residual: <what is knowingly left open>
```
ADR-13 is the right length model (one screen). ADR-14 (`:153-176`) is far longer and carries
`**Round-2 correction**` / `**superseded in part**` bullets — that shape is for an ADR that has been
revised, not for a new one. Two conventions from both worth carrying:
- Name the alternative *and* why it was rejected inside the Decision paragraph.
- End Consequences with an explicit `Residual:` bullet (D-14's "claim only what ships").

---

### `build.gradle.kts` (config) — MODIFIED

**Analog:** `build.gradle.kts:152-154`. The existing `jvmArgs` line already carries an inline
justification comment naming the requirement it serves — match that:
```kotlin
tasks.test {
    useJUnitPlatform()
    jvmArgs("-ea") // Enable JVM assertions so EDT assert() fires in CI (REL-01 SC1 gate)
```
Becomes `jvmArgs("-ea", "-Djava.awt.headless=true")` with a comment naming the SC4 harness.

**Test-name constraint measured at `:161-171`** — `excludeHeavyTests=true` (what
`.github/workflows/build.yml` passes on the PR gate) excludes `*IntegrationTest`, `*ConcurrencyTest`,
`*BackpressureTest`, `*RestartPolicyTest`, `*SupervisionTest`. None of this phase's new test files may
use those suffixes.

---

### `mcp/SecTierResolutionTest.kt` + `mcp/McpToolCatalogTierParityTest.kt` (test, unit) — NEW

**Analog:** `src/test/kotlin/com/six2dez/burp/aiagent/mcp/tools/McpToolParityTest.kt`

**The invariant shape to copy** (`:17-22`) — this is the exact form for "every catalog tool declares
a tier" and "the `AUTO` set is exactly these N":
```kotlin
@Test
fun registeredToolIds_matchCatalog() {
    val catalogIds = McpToolCatalog.all().map { it.id }.toSet()
    val registered = McpToolRegistrations.allIds()
    assertEquals(catalogIds, registered)
}
```

**Per-descriptor sweep** (`:24-40`) — the form for asserting a property across all 59:
```kotlin
@Test
fun inputSchema_mapping_coversCatalogTools() {
    val noArgTools = setOf("status", "editor_get", ...)
    McpToolCatalog.all().forEach { descriptor ->
        val schema = McpToolExecutor.inputSchema(descriptor.id)
        if (!noArgTools.contains(descriptor.id)) {
            assertTrue(schema.properties.isNotEmpty(), "Missing schema mapping for ${descriptor.id}")
        }
    }
}
```

**Deep-stub `McpToolContext` fixture** (`:42-59`) — reuse verbatim if the resolution test needs a
context (it should not, if the gate is AWT- and context-free):
```kotlin
val api = mock<MontoyaApi>(defaultAnswer = Answers.RETURNS_DEEP_STUBS)
whenever(api.burpSuite().version().edition()).thenReturn(BurpSuiteEdition.PROFESSIONAL)
val context =
    McpToolContext(
        api = api, privacyMode = PrivacyMode.OFF, determinismMode = false, hostSalt = "test",
        toolToggles = McpToolCatalog.all().associate { it.id to true },
        unsafeEnabled = false, unsafeTools = McpToolCatalog.unsafeToolIds(),
        enabledUnsafeTools = emptySet(), limiter = McpRequestLimiter(4),
        edition = BurpSuiteEdition.PROFESSIONAL, maxBodyBytes = 1024,
    )
```

**Executor/gate agreement test** (`:42-66`) — the shape for "the gate and the executor consume the
same canonicalisation":
```kotlin
@Test
fun executeTool_and_executeToolResult_stayAlignedForUnknownTool() {
    ...
    val text = McpToolExecutor.executeTool("missing_tool", null, context)
    val result = McpToolExecutor.executeToolResult("missing_tool", null, context)

    assertEquals("Unknown tool: missing_tool", text)
    assertTrue(result.isError == true)
}
```

Assertion imports in this repo are `org.junit.jupiter.api.Assertions.*` (not `kotlin.test`) —
`McpToolParityTest.kt:9-11`.

---

### `mcp/ToolApprovalGateTest.kt` (test, unit — state machine + SC3 audit) — NEW

**Analog:** `src/test/kotlin/com/six2dez/burp/aiagent/mcp/BlockedRequestReporterTest.kt`

**Capturing the global audit emitter** (`:28-47`) — copy including the `@AfterEach` cleanup comment,
which names a real cross-test leak:
```kotlin
private val lines = CopyOnWriteArrayList<String>()

// The emitter fires on the caller's thread (a Netty event-loop thread in production) and is read
// from the JUnit thread, so the capture list must be concurrent. The payload type is `Any`, not
// `Map<String, Any?>` — AuditLogger.kt:20 declares `((String, Any) -> Unit)?`.
private val captured = CopyOnWriteArrayList<Pair<String, Any>>()

@BeforeEach
fun registerCapturingEmitter() {
    lines.clear()
    captured.clear()
    AuditLogger.registerGlobalEmitter { type, payload -> captured += type to payload }
}

@AfterEach
fun clearGlobalEmitter() {
    // globalEmitter is a @Volatile companion field — leaving it registered leaks into other
    // test classes running in the same JVM.
    AuditLogger.registerGlobalEmitter(null)
}
```
`AuditLogger.registerGlobalEmitter` / `emitGlobal` are at `audit/AuditLogger.kt:20-31`.

**Asserting the payload key set and order** (`:53-63`) — this is what pins the SC3 shape:
```kotlin
@Test
fun report_emitsExactlyOneTransportBlockedEventPerInvocation() {
    reporter().report(denyOrigin(), externalMode = false, nowMs = T0)

    assertEquals(1, captured.size)
    assertEquals("mcp_transport_blocked", captured[0].first)
    assertEquals(
        listOf("reason", "mode", "method", "path", "origin", "host", "referer", "userAgent"),
        payloadAt(0).keys.toList(),
    )
}
```

**Hash-vs-plaintext assertion** (`:104-110`) — the SC3 "args hashed by default" test:
```kotlin
// D-10 — hashing by default, plaintext only behind the verboseAudit seam
@Test
fun report_hashesReflectedHeaderValuesByDefault() {
    reporter(verboseAudit = false).report(denyOrigin(), externalMode = false, nowMs = T0)
```

**Named time constants at file top** (`:15-19`) — reuse the convention if the gate takes a clock:
```kotlin
private const val T0 = 1_000_000L
private const val T_INSIDE_WINDOW = 1_010_000L
```

---

### `ui/ChatPanelTestHarness.kt` + `ui/ChatPanelToolGateTest.kt` (test fixture + integration) — NEW

**Partial analog only.** No existing test constructs a real Swing panel. The two pieces that do exist:

1. **Deep-stub idiom** — `McpToolParityTest.kt:44-45`:
   ```kotlin
   val api = mock<MontoyaApi>(defaultAnswer = Answers.RETURNS_DEEP_STUBS)
   whenever(api.burpSuite().version().edition()).thenReturn(BurpSuiteEdition.PROFESSIONAL)
   ```
   For the harness, `BurpSuiteEdition.COMMUNITY_EDITION` per RESEARCH's measured run.
2. **Settings fixture** — `src/test/kotlin/com/six2dez/burp/aiagent/TestSettings.kt:11-12`:
   ```kotlin
   object TestSettings {
       fun baselineSettings(preferredBackendId: String = "codex-cli"): AgentSettings =
   ```
   `AgentSettings` has ~90 non-defaulted params; never hand-build one.

**Constructor to satisfy** (`ChatPanel.kt:75-87`) — ten parameters, nine of them lambdas:
```kotlin
class ChatPanel(
    private val api: MontoyaApi,
    private val supervisor: AgentSupervisor,
    private val getSettings: () -> AgentSettings,
    private val applySettings: (AgentSettings) -> Unit,
    private val validateBackend: (AgentSettings) -> String?,
    private val ensureBackendReady: (AgentSettings) -> Boolean,
    private val showError: (String) -> Unit,
    private val onStatusChanged: () -> Unit,
    private val onResponseReady: () -> Unit,
    private val passiveScanner: PassiveAiScanner? = null,
)
```
`root` is `public val` (`:89`), so a depth-first component search over `panel.root` is available.

**Deliberate anti-analog:** `src/test/kotlin/com/six2dez/burp/aiagent/ui/ChatPanelConcurrencyTest.kt:59-71`
is the existing UI-adjacent test, and its own KDoc explains why it must **not** be the model here:
```kotlin
/**
 * SC1 gate: session-map EDT confinement contract.
 *
 * Models the EDT-confinement invariant without constructing a real ChatPanel
 * (which requires Swing + UiTheme and throws HeadlessException in CI).
 * ...
 */
```
That premise is what RESEARCH measured false (the cost is a two-line guard). A modelled test cannot
go red against today's `maybeExecuteToolCall`, so it does not satisfy SC4's acceptance gate. Keep
modelled tests only for what the real harness genuinely cannot reach.

---

### `ui/McpToolTabModelTest.kt` (test) — MODIFIED

**Analog:** itself. The one non-catalog `McpToolDescriptor` construction site, and the second half of
D-03's compile-failure proof (`:21-38`):
```kotlin
private fun descriptor(
    id: String,
    title: String,
    description: String = "",
    category: String = "Cat",
    nativeTool: Boolean = false,
    proOnly: Boolean = false,
    unsafeOnly: Boolean = false,
) = McpToolDescriptor(
    id = id,
    title = title,
    description = description,
    category = category,
    defaultEnabled = true,
    nativeTool = nativeTool,
    proOnly = proOnly,
    unsafeOnly = unsafeOnly,
)
```
Add a `secTier: SecTier = SecTier.CONFIRM` **helper-level** default here — the helper is a test
convenience, and defaulting it in the helper does not weaken D-03, which is about the production
`data class`. The 14+ existing call sites then compile unchanged.

---

## Shared Patterns

### Sanitize attacker-controlled text before display and before logging (CWE-117)
**Source:** `mcp/McpBlockedRequestReporter.kt:21-27, 220-233`
**Apply to:** `ToolApprovalCard.kt` (tool ID + args rendering), `ToolApprovalGate.kt` (audit payload),
`ChatPanel.kt` (the extended `MCP_TOOL_CALL` metadata)
Control characters are **removed, never replaced**. Two variants are needed (UI-SPEC Rule T-6): the
inline one collapses whitespace and caps at 120 chars; the block one preserves `\n`/`\t` for the args
JSON. Java's `\p{Cntrl}` misses the C1 range — the regex at `:25` already handles that.

### Hash by default, plaintext only under a verbose seam
**Source:** `mcp/McpBlockedRequestReporter.kt:42-47, 171-172`; helper at `audit/Hashing.kt:7-13`;
existing key name at `mcp/tools/McpTool.kt:121` (`argsSha256`)
**Apply to:** every model-supplied value in the SC3 payload
```kotlin
private fun auditValue(value: String?): String? = sanitize(value)?.let { if (verboseAudit) it else Hashing.sha256Hex(it) }
```
Null in, null out — never hash the empty string, or "absent" and "empty" become indistinguishable.
`verboseAudit` is a constructor seam wired to `false`; there is still no user-facing verbose flag in
the repo, and adding one is not in scope.

### Dual-destination emission (audit + Output tab), because audit is off by default
**Source:** `mcp/tools/McpTool.kt:222-227` (audit side) + `McpBlockedRequestReporter.kt:69-71, 217-218`
(Output side, via a lambda not a `MontoyaApi`)
**Apply to:** the SEC-06 decision event
```kotlin
private fun emitToolTelemetry(type: String, payload: Map<String, Any?>) {
    AuditLogger.emitGlobal(type, payload)
}

/** The Output tab carries sanitized plaintext, never a hash — it is where a human diagnoses. */
private fun outputValue(value: String?): String = sanitize(value) ?: "none"
```
Use a **new** type constant (`mcp_tool_decision`), not a reuse of `mcp_tool_blocked` — the reason is
written at `McpBlockedRequestReporter.kt:8-14`: a constant whose payload keys have different meaning
"would corrupt downstream analysis".

### Required parameter with deliberately no default value
**Source:** `ui/components/ContextPreviewDialog.kt:21-23`
**Apply to:** `McpToolDescriptor.secTier`, `executeTool(origin = …)`
```kotlin
// D-07: deliberately has NO default value. There is exactly one caller, and a default is how a
// future caller silently gets the OFF hint wrong.
customPatternsConfigured: Boolean,
```
D-03 cites this verbatim. Carry the same comment shape so the next reader sees the intent, not an
omission.

### Fail closed with a typed result, never a throw
**Source:** `mcp/tools/McpTool.kt:135-158`; `mcp/tools/McpToolExecutorImpl.kt:150-152`
**Apply to:** unknown-tool tier resolution, session-denied resolution, all five implicit-denial paths
```kotlin
if (context.isUnsafeTool(name) && !context.isUnsafeToolAllowed(name)) {
    emitToolTelemetry(MCP_TOOL_EVENT_BLOCKED, baseTelemetry + mapOf("reason" to "unsafe_not_allowed"))
    return CallToolResult(
        content = listOf(TextContent("Unsafe mode is disabled for tool: $name. ...")),
        isError = true,
    )
}
```
Note the shape: emit telemetry **and** return a typed refusal, in that order, from the blocked branch.

### Swing theme re-apply on `updateUI()` with a construction guard
**Source:** `ui/components/SafetyIndicator.kt:23-34, 50-57` (flag form);
`ui/components/SubtleNotice.kt:44-46, 91-98` (`lateinit` form);
`ui/components/AccordionPanel.kt:28, 49, 72-80` (flag form)
**Apply to:** `ToolApprovalCard.kt` and its private `tierBadge`
The super-constructor calls `updateUI()` before Kotlin field initialisers run — an unguarded re-apply
NPEs. `ActionCard` has **no** `updateUI()` override; that is a gap, not a precedent.

### EDT confinement
**Source:** `ui/ChatPanel.kt:105-115` (`@GuardedBy("EDT")` on every session map), `:660-668`
(`SwingUtilities.invokeLater` marshalling from the backend thread), `:702-711` (`assertEdt()`)
**Apply to:** any new pending-decision map, the card's `ActionListener` callbacks, `resolvePending`
A Swing `ActionListener` is dispatched on the EDT by definition, so the async gate needs no new
marshalling — that is what leaves REL-05 (Phase 23) untouched.

### One canonicalisation function, consumed by both sides
**Source:** `mcp/tools/McpToolExecutorImpl.kt:141, 146, 1114-1121`; namespace minted once at
`mcp/external/ExternalMcpClientManager.kt:235` (`name = "ext:$serverName:${tool.name}"`)
**Apply to:** `ToolApprovalGate.tierFor`, the card's displayed tool ID, the audit `toolName`
Two independent `startsWith("ext:")` checks can disagree; one exposed function cannot.

---

## No Analog Found

| File | Role | Data Flow | Reason |
|------|------|-----------|--------|
| `src/test/kotlin/.../ui/ChatPanelTestHarness.kt` | test fixture | — | No headless-Swing fixture exists in the repo. `CONCERNS.md` §"UI layer has no integration tests" records the gap; `ChatPanelConcurrencyTest.kt:59-71` documents the (measured-false) reason it was never built. Compose from the mockito deep-stub idiom (`McpToolParityTest.kt:44-45`) + `TestSettings.baselineSettings()` + RESEARCH §"Headless `ChatPanel` harness" for the depth-first finder and EDT drain. |
| `src/test/kotlin/.../ui/ChatPanelToolGateTest.kt` | test (integration) | event-driven | Same root cause — it is the first test in the repo to drive a real `ChatPanel` through its real Send button. Structure and assertion style come from `McpToolParityTest.kt` / `BlockedRequestReporterTest.kt`; only the harness half is new. |

Both are Wave-0 work and both depend on the `ChatPanel.kt:377-380` guard landing first.

---

## Metadata

**Analog search scope:**
`src/main/kotlin/com/six2dez/burp/aiagent/{mcp,mcp/tools,mcp/external,ui,ui/components,ui/design,audit,util}`,
`src/test/kotlin/com/six2dez/burp/aiagent/{mcp,mcp/tools,ui,ui/design}`, `DECISIONS.md`,
`build.gradle.kts`, `detekt.yml`.

**Files scanned:** 164 Kotlin sources listed; 21 read (14 main, 5 test, 2 config/doc).

**Files read for excerpts:**
`mcp/McpBlockedRequestReporter.kt`, `mcp/McpAccessControlDecision.kt`, `mcp/McpToolCatalog.kt`,
`mcp/McpToolContext.kt`, `mcp/tools/McpToolExecutorImpl.kt`, `mcp/tools/McpTool.kt`,
`ui/ChatPanel.kt`, `ui/ToolCallParser.kt`, `ui/components/ActionCard.kt`,
`ui/components/SubtleNotice.kt`, `ui/components/SafetyIndicator.kt`,
`ui/components/AccordionPanel.kt`, `ui/components/ContextPreviewDialog.kt`,
`ui/design/Components.kt`, `ui/design/DesignTokens.kt`, `audit/Hashing.kt`, `audit/AuditLogger.kt`,
`audit/AiRequestLogger.kt`, `mcp/tools/McpToolParityTest.kt`, `mcp/BlockedRequestReporterTest.kt`,
`ui/McpToolTabModelTest.kt`, `ui/ChatPanelConcurrencyTest.kt`, `TestSettings.kt`, `DECISIONS.md`,
`build.gradle.kts`, `detekt.yml`, `ui/MainTab.kt` (grep only).

**Pattern extraction date:** 2026-08-13
