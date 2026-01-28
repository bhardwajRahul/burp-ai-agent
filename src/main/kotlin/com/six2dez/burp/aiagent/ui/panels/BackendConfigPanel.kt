package com.six2dez.burp.aiagent.ui.panels

import com.six2dez.burp.aiagent.ui.UiTheme
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextField
import javax.swing.border.EmptyBorder
import javax.swing.border.LineBorder
import com.six2dez.burp.aiagent.ui.components.ToggleSwitch

data class BackendConfigState(
    val codexCmd: String = "",
    val geminiCmd: String = "",
    val opencodeCmd: String = "",
    val claudeCmd: String = "",
    val ollamaCliCmd: String = "",
    val ollamaModel: String = "",
    val ollamaUrl: String = "",
    val ollamaServeCmd: String = "",
    val ollamaAutoStart: Boolean = false,
    val lmStudioUrl: String = "",
    val lmStudioModel: String = "",
    val lmStudioTimeoutSeconds: String = "",
    val lmStudioServerCmd: String = "",
    val lmStudioAutoStart: Boolean = true
)

class BackendConfigPanel(
    initialState: BackendConfigState = BackendConfigState()
) : JPanel(BorderLayout()) {
    private val cardLayout = CardLayout()
    private val cards = JPanel(cardLayout)

    private val codexCmd = JTextField(initialState.codexCmd)
    private val geminiCmd = JTextField(initialState.geminiCmd)
    private val opencodeCmd = JTextField(initialState.opencodeCmd)
    private val claudeCmd = JTextField(initialState.claudeCmd)
    private val ollamaCliCmd = JTextField(initialState.ollamaCliCmd)
    private val ollamaModel = JTextField(initialState.ollamaModel)
    private val ollamaUrl = JTextField(initialState.ollamaUrl)
    private val ollamaServeCmd = JTextField(initialState.ollamaServeCmd)
    private val ollamaAutoStart = ToggleSwitch(initialState.ollamaAutoStart)
    private val lmStudioUrl = JTextField(initialState.lmStudioUrl)
    private val lmStudioModel = JTextField(initialState.lmStudioModel)
    private val lmStudioTimeout = JTextField(initialState.lmStudioTimeoutSeconds)
    private val lmStudioServeCmd = JTextField(initialState.lmStudioServerCmd)
    private val lmStudioAutoStart = ToggleSwitch(initialState.lmStudioAutoStart)

    init {
        background = UiTheme.Colors.surface
        cards.background = UiTheme.Colors.surface

        applyFieldStyle(codexCmd)
        applyFieldStyle(geminiCmd)
        applyFieldStyle(opencodeCmd)
        applyFieldStyle(claudeCmd)
        applyFieldStyle(ollamaCliCmd)
        applyFieldStyle(ollamaModel)
        applyFieldStyle(ollamaUrl)
        applyFieldStyle(ollamaServeCmd)
        applyFieldStyle(lmStudioUrl)
        applyFieldStyle(lmStudioModel)
        applyFieldStyle(lmStudioTimeout)
        applyFieldStyle(lmStudioServeCmd)

        codexCmd.toolTipText = "Command used to launch Codex CLI."
        geminiCmd.toolTipText = "Command used to launch Gemini CLI."
        opencodeCmd.toolTipText = "Command used to launch OpenCode CLI with the model (e.g., opencode --model anthropic/claude-sonnet-4-5)."
        claudeCmd.toolTipText = "Command used to launch Claude Code CLI (e.g., claude)."
        ollamaCliCmd.toolTipText = "Command used to launch Ollama CLI with a model."
        ollamaModel.toolTipText = "Model name for Ollama HTTP backend. If empty, the CLI command is parsed."
        ollamaUrl.toolTipText = "Base URL for Ollama HTTP backend and health checks."
        ollamaServeCmd.toolTipText = "Command used to start the Ollama server."
        ollamaAutoStart.toolTipText = "Automatically start the Ollama server when needed."
        lmStudioUrl.toolTipText = "Base URL for LM Studio OpenAI-compatible endpoint."
        lmStudioModel.toolTipText = "Model name sent to LM Studio."
        lmStudioTimeout.toolTipText = "Request timeout in seconds."
        lmStudioServeCmd.toolTipText = "Command used to start the LM Studio server."
        lmStudioAutoStart.toolTipText = "Automatically start the LM Studio server when needed."

        cards.add(buildSingleFieldPanel("Codex CLI command", codexCmd), "codex-cli")
        cards.add(buildSingleFieldPanel("Gemini CLI command", geminiCmd), "gemini-cli")
        cards.add(buildOpenCodePanel(), "opencode-cli")
        cards.add(buildSingleFieldPanel("Claude Code command", claudeCmd), "claude-cli")
        cards.add(buildOllamaPanel(), "ollama")
        cards.add(buildLmStudioPanel(), "lmstudio")

        add(cards, BorderLayout.CENTER)
    }

    fun setBackend(id: String) {
        cardLayout.show(cards, id)
    }

    fun currentBackendSettings(): BackendConfigState {
        return BackendConfigState(
            codexCmd = codexCmd.text.trim(),
            geminiCmd = geminiCmd.text.trim(),
            opencodeCmd = opencodeCmd.text.trim(),
            claudeCmd = claudeCmd.text.trim(),
            ollamaCliCmd = ollamaCliCmd.text.trim(),
            ollamaModel = ollamaModel.text.trim(),
            ollamaUrl = ollamaUrl.text.trim(),
            ollamaServeCmd = ollamaServeCmd.text.trim(),
            ollamaAutoStart = ollamaAutoStart.isSelected,
            lmStudioUrl = lmStudioUrl.text.trim(),
            lmStudioModel = lmStudioModel.text.trim(),
            lmStudioTimeoutSeconds = lmStudioTimeout.text.trim(),
            lmStudioServerCmd = lmStudioServeCmd.text.trim(),
            lmStudioAutoStart = lmStudioAutoStart.isSelected
        )
    }

    fun applyState(state: BackendConfigState) {
        codexCmd.text = state.codexCmd
        geminiCmd.text = state.geminiCmd
        opencodeCmd.text = state.opencodeCmd
        claudeCmd.text = state.claudeCmd
        ollamaCliCmd.text = state.ollamaCliCmd
        ollamaModel.text = state.ollamaModel
        ollamaUrl.text = state.ollamaUrl
        ollamaServeCmd.text = state.ollamaServeCmd
        ollamaAutoStart.isSelected = state.ollamaAutoStart
        lmStudioUrl.text = state.lmStudioUrl
        lmStudioModel.text = state.lmStudioModel
        lmStudioTimeout.text = state.lmStudioTimeoutSeconds
        lmStudioServeCmd.text = state.lmStudioServerCmd
        lmStudioAutoStart.isSelected = state.lmStudioAutoStart
    }

    private fun buildSingleFieldPanel(labelText: String, field: JComponent): JPanel {
        val panel = JPanel(GridBagLayout())
        panel.background = UiTheme.Colors.surface
        panel.border = EmptyBorder(4, 8, 0, 8)
        addRow(panel, 0, labelText, field)
        addVerticalFiller(panel, 1)
        return panel
    }

    private fun buildOllamaPanel(): JPanel {
        val panel = JPanel(GridBagLayout())
        panel.background = UiTheme.Colors.surface
        panel.border = EmptyBorder(8, 8, 8, 8)
        var row = 0
        addRow(panel, row++, "Ollama CLI command", ollamaCliCmd)
        addRow(panel, row++, "Ollama model", ollamaModel)
        addRow(panel, row++, "Ollama base URL", ollamaUrl)
        addRow(panel, row++, "Ollama serve command", ollamaServeCmd)
        addToggleRow(panel, row, "Auto-start Ollama server", ollamaAutoStart)
        return panel
    }

    private fun buildOpenCodePanel(): JPanel {
        val panel = JPanel(GridBagLayout())
        panel.background = UiTheme.Colors.surface
        panel.border = EmptyBorder(8, 8, 8, 8)
        addRow(panel, 0, "OpenCode CLI command", opencodeCmd)
        return panel
    }

    private fun buildLmStudioPanel(): JPanel {
        val panel = JPanel(GridBagLayout())
        panel.background = UiTheme.Colors.surface
        panel.border = EmptyBorder(8, 8, 8, 8)
        addRow(panel, 0, "LM Studio base URL", lmStudioUrl)
        addRow(panel, 1, "LM Studio model", lmStudioModel)
        addRow(panel, 2, "LM Studio timeout (seconds)", lmStudioTimeout)
        addRow(panel, 3, "LM Studio serve command", lmStudioServeCmd)
        addToggleRow(panel, 4, "Auto-start LM Studio server", lmStudioAutoStart)
        return panel
    }

    private fun addRow(panel: JPanel, row: Int, labelText: String, field: JComponent) {
        val label = JLabel(labelText)
        label.font = UiTheme.Typography.body
        label.foreground = UiTheme.Colors.onSurface

        val labelConstraints = GridBagConstraints().apply {
            gridx = 0
            gridy = row
            anchor = GridBagConstraints.WEST
            insets = Insets(4, 0, 4, 10)
        }
        val fieldConstraints = GridBagConstraints().apply {
            gridx = 1
            gridy = row
            weightx = 1.0
            fill = GridBagConstraints.HORIZONTAL
            insets = Insets(4, 0, 4, 0)
        }
        panel.add(label, labelConstraints)
        panel.add(field, fieldConstraints)
    }

    private fun addToggleRow(panel: JPanel, row: Int, labelText: String, toggle: ToggleSwitch) {
        val label = JLabel(labelText)
        label.font = UiTheme.Typography.body
        label.foreground = UiTheme.Colors.onSurface
        val labelConstraints = GridBagConstraints().apply {
            gridx = 0
            gridy = row
            anchor = GridBagConstraints.WEST
            insets = Insets(6, 0, 4, 10)
        }
        val toggleConstraints = GridBagConstraints().apply {
            gridx = 1
            gridy = row
            anchor = GridBagConstraints.WEST
            insets = Insets(6, 0, 4, 0)
        }
        panel.add(label, labelConstraints)
        panel.add(toggle, toggleConstraints)
    }

    private fun addVerticalFiller(panel: JPanel, row: Int) {
        val filler = JPanel()
        filler.isOpaque = false
        val constraints = GridBagConstraints().apply {
            gridx = 0
            gridy = row
            gridwidth = 2
            weighty = 1.0
            fill = GridBagConstraints.VERTICAL
        }
        panel.add(filler, constraints)
    }

    private fun applyFieldStyle(field: JTextField) {
        field.font = UiTheme.Typography.mono
        field.border = LineBorder(UiTheme.Colors.outline, 1, true)
        field.background = UiTheme.Colors.inputBackground
        field.foreground = UiTheme.Colors.inputForeground
    }
}
