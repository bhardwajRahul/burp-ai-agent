package com.six2dez.burp.aiagent.ui.components

import com.six2dez.burp.aiagent.config.Defaults
import com.six2dez.burp.aiagent.mcp.SecTier
import com.six2dez.burp.aiagent.mcp.ToolDecision
import com.six2dez.burp.aiagent.mcp.sanitizeBlock
import com.six2dez.burp.aiagent.mcp.sanitizeInline
import com.six2dez.burp.aiagent.ui.design.DesignTokens
import com.six2dez.burp.aiagent.ui.design.applyAreaStyle
import com.six2dez.burp.aiagent.ui.design.applyFieldStyle
import com.six2dez.burp.aiagent.ui.design.helpLabel
import java.awt.Color
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import java.awt.RenderingHints
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextArea
import javax.swing.JTextField
import javax.swing.SwingConstants
import javax.swing.border.CompoundBorder
import javax.swing.border.EmptyBorder
import javax.swing.border.LineBorder
import javax.swing.border.MatteBorder

// -------------------------------------------------------------------------------------------------
// Numeric constants. Named rather than inline because MagicNumber is active and QUAL-07 forbids
// growing detekt-baseline.xml.
// -------------------------------------------------------------------------------------------------

/** 3 px left accent strip, matching the compound-border idiom in SubtleNotice.kt:117. */
private const val ACCENT_STRIP_WIDTH = 3

/** Corner arc of the tier badge's rounded rect — the same 6 px `toolBadge` uses (Components.kt:452). */
private const val BADGE_ARC = 6

/** Tier-badge internal padding, adopted transitively from `toolBadge` (Components.kt:470). */
private const val BADGE_PAD_VERTICAL = 2

/** Tier-badge internal padding, adopted transitively from `toolBadge` (Components.kt:470). */
private const val BADGE_PAD_HORIZONTAL = 6

/**
 * Inline cap for the model-supplied tool ID. Long enough for any legitimate `ext:<server>:<tool>`
 * triple, short enough that a model cannot use the ID as a canvas.
 */
private const val TOOL_ID_MAX_LENGTH = 120

/** Preview character cap — roughly 40 rows x 80 columns, so it bites at about the same height as the line cap. */
private const val ARGS_PREVIEW_MAX_CHARS = 3200

/** Preview source-line cap, tightening ActionCard.limitLines(raw, 50). */
private const val ARGS_PREVIEW_MAX_LINES = 40

// Ordinal suffix arithmetic for the repeat counter (Mechanism B).
private const val ORDINAL_CENTURY = 100
private const val ORDINAL_DECADE = 10
private const val ORDINAL_TEEN_FIRST = 11
private const val ORDINAL_TEEN_LAST = 13
private const val ORDINAL_THIRD = 3

// -------------------------------------------------------------------------------------------------
// Copy. Every string here is EXTENSION-DERIVED and appears OUTSIDE every model region. Verbatim from
// 22-UI-SPEC.md "Copywriting Contract", which is normative. English only (CLAUDE.md, AGENTS.md).
// -------------------------------------------------------------------------------------------------

private const val HEADING = "The AI asked to run a tool"
private const val UNKNOWN_TOOL_TITLE = "Not a known tool — this call will fail if you approve it."
private const val TRUST_LABEL = "AI-supplied — this extension did not write the text below:"
private const val TIER_REASON_CONFIRM = "Approving for the session applies to this tool until this chat is deleted."
private const val TIER_REASON_CONFIRM_EACH =
    "This tool is approved one call at a time. A session-wide approval is not offered for it."
private const val SESSION_SCOPE_FOOTER =
    "\"Session\" means this chat. Approvals are forgotten when the chat is deleted, and are not restored when Burp restarts."

/**
 * The load-bearing last sentence of both truncation footers: the display cap is a DISPLAY limit, not
 * an execution limit. `McpToolExecutor.executeTool` receives the whole args string regardless of what
 * this card renders, and a user authorising a call must know that.
 */
private const val ARGS_SENT_IN_FULL = "The full arguments are sent to the tool if you approve."

private const val LABEL_DENY = "Deny"
private const val LABEL_DENY_SESSION = "Deny for session"
private const val LABEL_APPROVE_ONCE = "Approve once"
private const val LABEL_APPROVE_SESSION = "Approve for session"

private const val TIP_DENY = "Do not run this call. The AI is told the call was not authorised and continues without it."
private const val TIP_APPROVE_ONCE = "Run this tool call now. The AI must ask again next time."

/** U+25B6 / U+25BC, matching AccordionPanel.kt:103 so the affordance reads the same everywhere. */
private const val GLYPH_COLLAPSED = "▶"
private const val GLYPH_EXPANDED = "▼"

/** Which of the three args disclosure stages the card is showing. */
private enum class ArgsStage { PREVIEW, FULL }

/**
 * The SEC-06 decision surface: an inline card in the chat transcript that shows one model-emitted tool
 * call and offers the user an explicit decision about it (D-06, D-07, D-11).
 *
 * **The hard problem this component solves is not layout — it is that two classes of text share one
 * card.** The heading, the catalog title, the tier badge and the button labels are extension-derived
 * and trustworthy. The tool ID and the args JSON are model-supplied and an injected prompt can write
 * "safe routine read" into either. A user must tell them apart WITHOUT reading carefully, so:
 *
 * - **Containment (primary).** Every model-supplied string is rendered inside a bordered, input-styled
 *   region, and nothing extension-derived is ever rendered inside one.
 * - **Widget class (anti-spoofing).** Model text goes ONLY into [JTextField] / [JTextArea]. It is
 *   forbidden in [JLabel], any `AbstractButton`, any `JEditorPane`, and any tooltip: all of those route
 *   their text through `BasicHTML.updateRenderer`, so `"<html><b>Approved</b>"` in a tool ID would
 *   render as extension-looking chrome. `ToolApprovalCardTest` asserts this mechanically through
 *   `getClientProperty("html")`.
 * - **Font family (theme-independent).** Extension text is proportional; model text is monospace.
 * - **An explicit label.** A caption above the first model region says this extension did not write
 *   what follows. It sits outside every box, so model text physically cannot escape into it.
 *
 * The card is also a **record**, not just a prompt: it stays in the transcript after the decision and
 * must read unambiguously months later.
 *
 * Deliberately NOT an `ActionCard` subclass. `ActionCard` renders all four of its text fields into
 * `JLabel`s, which is the one thing this card may never do; it also uses the legacy theme shim, a
 * nested scroll pane, off-grid insets, and has no `updateUI()` override.
 *
 * Contract: `.planning/phases/22-agent-tool-call-trust-boundary/22-UI-SPEC.md` (approved, binding).
 */
internal class ToolApprovalCard(
    private val tier: SecTier,
    private val catalogTitle: String?,
    // Named so its trust class is obvious at every call site: this is `ParsedToolCall.tool`, i.e.
    // whatever string the model wrote. Attacker-influenceable.
    modelSuppliedToolId: String,
    // Also attacker-influenceable.
    modelSuppliedArgsJson: String?,
    // Computed by the gate, never re-derived from the tier here: whether the two session-wide actions
    // are offered is the gate's call, and duplicating the rule would let the two disagree.
    // Deliberately has NO default value (the ContextPreviewDialog.kt:21-23 convention).
    private val offersSessionActions: Boolean,
    private val repeatCount: Int,
    // Deliberately has NO default value: a default onDecision is how a future caller ships a card
    // whose buttons do nothing.
    private val onDecision: (ToolDecision) -> Unit,
) : JPanel(GridBagLayout()) {
    // `JPanel.<init>` invokes updateUI() BEFORE the Kotlin field initialisers below run, so an
    // unguarded re-apply would NPE on every child field. The flag is zero-initialised to false by the
    // JVM, so the guard short-circuits during super-construction, and is flipped at the end of init {}.
    // Same hazard and same fix as SafetyIndicator.kt:23-27. ActionCard has no such override; that is a
    // gap, not a precedent.
    private var initialized = false

    /** Rule T-6 inline form: control characters REMOVED (never replaced), whitespace collapsed, capped. */
    private val sanitizedToolId = sanitizeInline(modelSuppliedToolId, TOOL_ID_MAX_LENGTH).orEmpty()

    /** Rule T-6 block form: `\n` and `\t` preserved, uncapped — the honest denominator for every count below. */
    private val sanitizedArgs = sanitizeBlock(modelSuppliedArgsJson, maxChars = Int.MAX_VALUE, maxLines = Int.MAX_VALUE)

    private val previewArgs =
        sanitizeBlock(modelSuppliedArgsJson, maxChars = ARGS_PREVIEW_MAX_CHARS, maxLines = ARGS_PREVIEW_MAX_LINES)

    private val totalChars = sanitizedArgs.length

    private val previewTruncated = previewArgs != sanitizedArgs

    // sanitizeBlock appends exactly one ellipsis character when it truncates, so subtracting it gives
    // the exact number of ARGUMENT characters on screen. The footer states that number, and a count
    // that was one too high would be a false statement on a card whose whole job is authorisation.
    private val previewShownChars = if (previewTruncated) previewArgs.length - 1 else previewArgs.length

    // Defensive only: args come from model output requested at CHAT_MAX_OUTPUT_TOKENS, so in practice
    // this cannot bite — but a local backend may ignore the output cap. When it does bite the Full
    // stage says so, and "Showing all N characters." is never generated.
    private val ceilingEngaged = totalChars > Defaults.MAX_CONTEXT_TOTAL_CHARS

    private val fullArgs =
        if (ceilingEngaged) {
            sanitizeBlock(modelSuppliedArgsJson, maxChars = Defaults.MAX_CONTEXT_TOTAL_CHARS, maxLines = Int.MAX_VALUE)
        } else {
            sanitizedArgs
        }

    // Anti-habituation Mechanism A / threat T-22-29. Derived from the tier INSIDE this file rather
    // than exposed as a constructor parameter, so no call site can defeat the rule. D-02/D-04's whole
    // argument for CONFIRM_EACH is "constant tool name and hostile args" — for that tier the args ARE
    // the decision, so showing them is not friction, it is showing the thing that must be read.
    // Intended side effect: the two tiers become physically different sizes, so a CONFIRM_EACH card
    // visually interrupts a run of CONFIRM cards.
    private val initiallyExpanded = tier == SecTier.CONFIRM_EACH

    private var expanded = initiallyExpanded

    private var argsStage = ArgsStage.PREVIEW

    private val headingLabel = JLabel(HEADING)

    // Rule T-7: an unrecognised tool is LABELLED, never shown bare, and no catalog title is fabricated
    // for it. The model's own string still appears — sanitized, inside its box — because the user must
    // see what was asked for.
    private val titleLabel = JLabel(catalogTitle ?: UNKNOWN_TOOL_TITLE)

    private val tierReasonLabel = helpLabel(tierReasonFor(tier))

    private val repeatLabel =
        JLabel(if (repeatCount > 1) "${ordinalFor(repeatCount)} request for this tool in this chat" else "")

    private val trustLabel = helpLabel(TRUST_LABEL)

    private val toolIdField = JTextField(sanitizedToolId)

    private val argsToggle = JButton()

    private val argsArea = JTextArea()

    private val truncationFooter = helpLabel("")

    private val showAllButton = JButton()

    /** Holds the truncation footer and, when more is available on this card, the show-all button. */
    private val overflowRow = JPanel()

    private val buttonRow = JPanel()

    private val decisionButtons = mutableListOf<JButton>()

    private val sessionScopeLabel = helpLabel(SESSION_SCOPE_FOOTER)

    private val badge = tierBadge(tier)

    init {
        isOpaque = true

        // Set isEditable BEFORE the design-system builder runs: several L&Fs paint a non-editable text
        // component with the disabled background and would overwrite the token value if reversed.
        toolIdField.isEditable = false
        toolIdField.caretPosition = 0
        argsArea.isEditable = false

        argsToggle.horizontalAlignment = SwingConstants.LEFT
        argsToggle.isFocusPainted = false
        argsToggle.addActionListener {
            expanded = !expanded
            updateArgsView()
        }

        showAllButton.horizontalAlignment = SwingConstants.LEFT
        showAllButton.isFocusPainted = false
        showAllButton.addActionListener {
            argsStage = ArgsStage.FULL
            updateArgsView()
        }

        buildButtonRow()
        buildRows()

        initialized = true
        applyTheme()
        updateArgsView()
    }

    override fun updateUI() {
        super.updateUI()
        // Re-apply every token-derived colour, font and border after a Burp theme switch. Guarded
        // against the super-construction callback (see [initialized]).
        if (initialized) {
            applyTheme()
        }
    }

    /**
     * Single-column [GridBagLayout], eleven rows, `gridwidth = REMAINDER` throughout.
     *
     * Horizontal insets are 0 on every row: horizontal padding is declared once, by the card's own
     * `EmptyBorder`. All vertical insets are on the 4 px grid.
     */
    private fun buildRows() {
        val constraints =
            GridBagConstraints().apply {
                gridx = 0
                gridy = 0
                gridwidth = GridBagConstraints.REMAINDER
                fill = GridBagConstraints.HORIZONTAL
                weightx = 1.0
                anchor = GridBagConstraints.WEST
            }
        val tight = Insets(0, 0, DesignTokens.Spacing.xs, 0)
        val roomy = Insets(0, 0, DesignTokens.Spacing.sm, 0)

        // Row 0 — heading + tier badge, kept adjacent rather than pinning the badge to a far-right
        // edge that moves when the user drags the Burp tab divider.
        val header = JPanel()
        header.layout = BoxLayout(header, BoxLayout.X_AXIS)
        header.isOpaque = false
        header.add(headingLabel)
        header.add(Box.createRigidArea(Dimension(DesignTokens.Spacing.sm, 0)))
        header.add(badge)
        header.add(Box.createHorizontalGlue())
        addRow(header, constraints, tight)

        addRow(titleLabel, constraints, tight)
        addRow(tierReasonLabel, constraints, tight)
        // Row 3 — Mechanism B. The one signal that actually breaks autopilot: it says the model is
        // INSISTING. Rendered only when there is something to say.
        if (repeatCount > 1) {
            addRow(repeatLabel, constraints, tight)
        }
        addRow(trustLabel, constraints, tight)
        addRow(toolIdField, constraints, roomy)
        addRow(argsToggle, constraints, tight)
        addRow(argsArea, constraints, roomy)
        addRow(overflowRow, constraints, tight)
        addRow(buttonRow, constraints, Insets(DesignTokens.Spacing.sm, 0, 0, 0))
        addRow(sessionScopeLabel, constraints, tight)
    }

    /**
     * Row 9. Left-aligned, because the card is a record and not a dialog footer.
     *
     * Scope escalates left to right within each group, so the two narrowest actions flank the 16 px
     * gap and the two session-wide ones sit at the outer edges. Visual order equals focus order, so Tab
     * from the input area reaches `Deny` first — the fail-closed reflex target.
     */
    private fun buildButtonRow() {
        buttonRow.layout = BoxLayout(buttonRow, BoxLayout.X_AXIS)
        buttonRow.isOpaque = false
        // Tooltips interpolate the CATALOG title and never the model's string — a tooltip renders HTML,
        // so a model-authored one would be styled, floating, extension-looking text (Rule T-2). When the
        // tool is unrecognised there is no title to interpolate and the two session tooltips are simply
        // omitted rather than filled with invented copy.
        val denySessionTip =
            catalogTitle?.let { "Do not run this call, and refuse any later $it call in this chat without asking again." }
        val approveSessionTip = catalogTitle?.let { "Run this and any later $it call in this chat without asking." }

        buttonRow.add(decisionButton(LABEL_DENY, TIP_DENY, ToolDecision.DENY))
        if (offersSessionActions) {
            buttonRow.add(Box.createRigidArea(Dimension(DesignTokens.Spacing.sm, 0)))
            buttonRow.add(decisionButton(LABEL_DENY_SESSION, denySessionTip, ToolDecision.DENY_SESSION))
        }
        // The 16 px gap between the deny and approve poles is the cheapest mis-click mitigation
        // available: a mis-aimed click near the boundary lands in dead space, not on the wrong pole.
        buttonRow.add(Box.createRigidArea(Dimension(DesignTokens.Spacing.lg, 0)))
        buttonRow.add(decisionButton(LABEL_APPROVE_ONCE, TIP_APPROVE_ONCE, ToolDecision.APPROVE_ONCE))
        if (offersSessionActions) {
            buttonRow.add(Box.createRigidArea(Dimension(DesignTokens.Spacing.sm, 0)))
            buttonRow.add(decisionButton(LABEL_APPROVE_SESSION, approveSessionTip, ToolDecision.APPROVE_SESSION))
        }
        buttonRow.add(Box.createHorizontalGlue())

        overflowRow.layout = BoxLayout(overflowRow, BoxLayout.Y_AXIS)
        overflowRow.isOpaque = false
        truncationFooter.alignmentX = LEFT_ALIGNMENT
        val showAllRow = JPanel()
        showAllRow.layout = BoxLayout(showAllRow, BoxLayout.X_AXIS)
        showAllRow.isOpaque = false
        showAllRow.alignmentX = LEFT_ALIGNMENT
        showAllRow.add(showAllButton)
        showAllRow.add(Box.createHorizontalGlue())
        overflowRow.add(truncationFooter)
        overflowRow.add(showAllRow)
    }

    /**
     * A plain [JButton] with no colour of its own.
     *
     * Not `primaryButton`: that fills with the 10 % accent, whose reserved list does not include a
     * security affirmative, and making `Approve once` visually dominant is the opposite of the goal.
     * Not `statusError` on `Deny` either: deny is the safe, fully recoverable outcome, and colouring it
     * red would miscolour the safe action as dangerous. There is deliberately no recommended action.
     *
     * `isFocusPainted = true` is a deliberate deviation from ActionCard.kt:58 and ChatPanel.kt:433:
     * a keyboard user must see which pole is focused before pressing Space.
     */
    private fun decisionButton(
        label: String,
        tooltip: String?,
        decision: ToolDecision,
    ): JButton =
        JButton(label)
            .apply {
                font = DesignTokens.Typography.body
                isFocusPainted = true
                toolTipText = tooltip
                addActionListener { onDecision(decision) }
            }.also { decisionButtons.add(it) }

    /**
     * Three-stage args disclosure — collapsed, preview, full.
     *
     * Collapsed means `isVisible = false`, not zero height: a hidden component contributes nothing to
     * the layout, which keeps the height-capping wrapper installed by `SessionPanel.addComponent`
     * correct. There is no nested scroll pane — the transcript's own does the scrolling, and a nested
     * one would hide args below a fold and create the wheel trap.
     */
    private fun updateArgsView() {
        argsToggle.text =
            if (expanded) {
                "$GLYPH_EXPANDED Hide arguments"
            } else {
                "$GLYPH_COLLAPSED Show arguments ($totalChars characters)"
            }
        argsArea.isVisible = expanded
        argsArea.text = if (argsStage == ArgsStage.FULL) fullArgs else previewArgs
        argsArea.caretPosition = 0

        val footer = footerTextFor(argsStage, previewTruncated, ceilingEngaged, previewShownChars, totalChars)
        truncationFooter.text = footer.orEmpty()
        truncationFooter.isVisible = expanded && footer != null
        showAllButton.text = showAllLabelFor(totalChars)
        showAllButton.isVisible = expanded && argsStage == ArgsStage.PREVIEW && previewTruncated
        overflowRow.isVisible = truncationFooter.isVisible || showAllButton.isVisible

        revalidate()
        repaint()
    }

    /**
     * The tier badge — outlined, never filled.
     *
     * Structured exactly like `Components.toolBadge` (rounded rect at paint time, caption font, its
     * `EmptyBorder(2, 6, 2, 6)` padding, foreground re-applied on theme switch) but with a coloured
     * outline instead of a coloured fill. A filled pill was rejected on measured contrast grounds
     * (white on the warning token is ~2.70:1, far below the 4.5:1 AA threshold), and the
     * outlined form puts badge text in exactly the same foreground/background relationship as ordinary
     * body text, so it introduces no new contrast obligation.
     *
     * **The text is the load-bearing channel; colour never carries text on this card.** The `when` is
     * exhaustive including `AUTO` so a future tier cannot render blank, even though an `AUTO` call
     * produces no card at all (D-02).
     *
     * Not promoted to `Components.kt`: one caller, and Phase 13's "avoid premature generalisation"
     * stance applies until a second one exists (FLAG-22-05).
     */
    private fun tierBadge(tier: SecTier): JLabel =
        object : JLabel("Tier: ${tierTextFor(tier)}") {
            override fun paintComponent(g: Graphics) {
                val g2d = g as Graphics2D
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                // Re-read at paint time so the badge always reflects the active Burp theme, even if it
                // was created under a different one (the FLAG-10-01 handling in Components.kt:447-451).
                g2d.color = DesignTokens.Colors.cardSurface
                g2d.fillRoundRect(0, 0, width, height, BADGE_ARC, BADGE_ARC)
                super.paintComponent(g)
            }

            override fun updateUI() {
                super.updateUI()
                font = DesignTokens.Typography.caption
                foreground = DesignTokens.Colors.onSurface
                border =
                    CompoundBorder(
                        LineBorder(tierColorFor(tier), 1, true),
                        EmptyBorder(BADGE_PAD_VERTICAL, BADGE_PAD_HORIZONTAL, BADGE_PAD_VERTICAL, BADGE_PAD_HORIZONTAL),
                    )
            }
        }.apply {
            isOpaque = false
            font = DesignTokens.Typography.caption
            foreground = DesignTokens.Colors.onSurface
            border =
                CompoundBorder(
                    LineBorder(tierColorFor(tier), 1, true),
                    EmptyBorder(BADGE_PAD_VERTICAL, BADGE_PAD_HORIZONTAL, BADGE_PAD_VERTICAL, BADGE_PAD_HORIZONTAL),
                )
        }

    /**
     * Re-applies every token-derived value. Called at the end of `init {}` and again on every theme
     * switch. No token is cached in a `val` at construction — [DesignTokens] properties are computed
     * `get`s for exactly this reason.
     */
    private fun applyTheme() {
        background = DesignTokens.Colors.cardSurface
        border =
            CompoundBorder(
                CompoundBorder(
                    MatteBorder(0, ACCENT_STRIP_WIDTH, 0, 0, tierColorFor(tier)),
                    LineBorder(DesignTokens.Colors.border, 1, true),
                ),
                EmptyBorder(
                    DesignTokens.Spacing.sm,
                    DesignTokens.Spacing.md,
                    DesignTokens.Spacing.sm,
                    DesignTokens.Spacing.md,
                ),
            )

        headingLabel.font = DesignTokens.Typography.sectionTitle
        headingLabel.foreground = DesignTokens.Colors.onSurface
        titleLabel.font = DesignTokens.Typography.body
        titleLabel.foreground = DesignTokens.Colors.onSurface
        tierReasonLabel.font = DesignTokens.Typography.caption
        tierReasonLabel.foreground = DesignTokens.Colors.onSurfaceVariant
        repeatLabel.font = DesignTokens.Typography.caption
        repeatLabel.foreground = DesignTokens.Colors.statusWarning
        trustLabel.font = DesignTokens.Typography.caption
        trustLabel.foreground = DesignTokens.Colors.onSurfaceVariant
        truncationFooter.font = DesignTokens.Typography.caption
        truncationFooter.foreground = DesignTokens.Colors.onSurfaceVariant
        sessionScopeLabel.font = DesignTokens.Typography.caption
        sessionScopeLabel.foreground = DesignTokens.Colors.onSurfaceVariant

        // One call each supplies the monospace font, the 1 px outline and the input background — the
        // three containment channels of Rule T-1 at once, which is why the design-system builder is
        // used here instead of hand-set properties.
        applyFieldStyle(toolIdField)
        applyAreaStyle(argsArea)

        argsToggle.font = DesignTokens.Typography.body
        showAllButton.font = DesignTokens.Typography.body
        decisionButtons.forEach { it.font = DesignTokens.Typography.body }
    }
}

/** Adds one full-width row and advances to the next. [constraints] is mutated and reused by design. */
private fun JPanel.addRow(
    component: JComponent,
    constraints: GridBagConstraints,
    insets: Insets,
) {
    constraints.insets = insets
    add(component, constraints)
    constraints.gridy++
}

/**
 * Channel 2 of tier legibility, and always redundant with the badge text. `AUTO` is specified so the
 * `when` is exhaustive; it is unreachable because an `AUTO` call renders no card (D-02).
 */
private fun tierColorFor(tier: SecTier): Color =
    when (tier) {
        SecTier.AUTO -> DesignTokens.Colors.statusSuccess
        SecTier.CONFIRM -> DesignTokens.Colors.statusWarning
        SecTier.CONFIRM_EACH -> DesignTokens.Colors.statusError
    }

/** Channel 1 — the load-bearing one. Also rendered verbatim into the accessible description. */
private fun tierTextFor(tier: SecTier): String =
    when (tier) {
        SecTier.AUTO -> "AUTO"
        SecTier.CONFIRM -> "CONFIRM"
        SecTier.CONFIRM_EACH -> "CONFIRM EACH"
    }

/** Row 2 — what the buttons on THIS card mean. Reconciles the tier with the actions offered. */
private fun tierReasonFor(tier: SecTier): String =
    when (tier) {
        SecTier.CONFIRM -> TIER_REASON_CONFIRM
        else -> TIER_REASON_CONFIRM_EACH
    }

/**
 * Row 8. A bare ellipsis is forbidden here: on a card whose whole purpose is authorisation it reads as
 * "nothing important follows".
 *
 * Both truncated forms take an `<html>` prefix at position 0 so they wrap — the transcript's scroll
 * pane never shows a horizontal bar, so an over-wide label clips with no way to read the rest. That is
 * not a Rule T-2 violation: these are extension-authored templates whose only interpolations are
 * extension-computed integers, so no model byte can reach position 0 or any other offset in them. The
 * exemption covers only label rows carrying zero model-supplied text.
 */
private fun footerTextFor(
    stage: ArgsStage,
    previewTruncated: Boolean,
    ceilingEngaged: Boolean,
    shown: Int,
    total: Int,
): String? =
    when {
        stage == ArgsStage.PREVIEW && previewTruncated ->
            "<html>Showing the first $shown of $total characters. $ARGS_SENT_IN_FULL"
        stage == ArgsStage.PREVIEW -> null
        ceilingEngaged ->
            "<html>Showing the first ${Defaults.MAX_CONTEXT_TOTAL_CHARS} of $total characters. " +
                "This is the maximum this card will display. $ARGS_SENT_IN_FULL"
        // Reserved for the case where everything really is on screen, and never generated otherwise —
        // this is exactly the case where it would be a false statement.
        else -> "Showing all $total characters."
    }

/** Labelled so the button never promises more than it delivers. */
private fun showAllLabelFor(total: Int): String =
    if (total <= Defaults.MAX_CONTEXT_TOTAL_CHARS) {
        "Show all $total characters"
    } else {
        "Show first ${Defaults.MAX_CONTEXT_TOTAL_CHARS} characters"
    }

/** English ordinal for the repeat counter: 2nd, 3rd, 4th, 11th, 21st. */
private fun ordinalFor(n: Int): String {
    val suffix =
        when {
            n % ORDINAL_CENTURY in ORDINAL_TEEN_FIRST..ORDINAL_TEEN_LAST -> "th"
            n % ORDINAL_DECADE == 1 -> "st"
            n % ORDINAL_DECADE == 2 -> "nd"
            n % ORDINAL_DECADE == ORDINAL_THIRD -> "rd"
            else -> "th"
        }
    return "$n$suffix"
}
