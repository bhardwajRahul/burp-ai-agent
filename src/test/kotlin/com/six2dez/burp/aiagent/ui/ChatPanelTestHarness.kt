package com.six2dez.burp.aiagent.ui

import burp.api.montoya.MontoyaApi
import burp.api.montoya.core.BurpSuiteEdition
import com.six2dez.burp.aiagent.TestSettings
import com.six2dez.burp.aiagent.config.AgentSettings
import com.six2dez.burp.aiagent.supervisor.AgentSupervisor
import com.six2dez.burp.aiagent.ui.components.ToolApprovalCard
import org.mockito.Answers
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.awt.Container
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JTextArea
import javax.swing.SwingUtilities

/**
 * Shared headless fixture that builds a **real** [ChatPanel] and drives its **real** Send button.
 *
 * This is deliberately not a model of the chat flow. It constructs the production ten-parameter
 * [ChatPanel] from deep-stub mocks, so assertions written against it exercise the same code path a
 * user drives in Burp. That is what makes the SEC-06 / SC4 acceptance gate in [ChatPanelToolGateTest]
 * non-vacuous.
 *
 * It is enabled by two things that landed with it in Phase 22 Wave 0:
 * - the `ChatPanel.kt` headless guard on `Toolkit.getDefaultToolkit().menuShortcutKeyMaskEx`, the one
 *   headless-hostile call in the whole construction path;
 * - `-Djava.awt.headless=true` in `tasks.test`, so a developer Mac and `ubuntu-latest` agree.
 *
 * Note for anyone reading `ChatPanelConcurrencyTest`: its KDoc claims a real `ChatPanel` "requires
 * Swing + UiTheme and throws HeadlessException in CI". That premise was measured false — the entire
 * cost was the one-call guard above. Do not copy the modelled approach from that file into new tests.
 *
 * **Naming constraint (hard).** `build.gradle.kts` excludes `*IntegrationTest`, `*ConcurrencyTest`,
 * `*BackpressureTest`, `*RestartPolicyTest` and `*SupervisionTest` under `-PexcludeHeavyTests=true`,
 * which is exactly what the PR gate passes. This object, and every test built on it, must avoid those
 * suffixes or the gate silently stops running them.
 *
 * This object declares no annotated test functions at all by design — it is a fixture, not a suite,
 * so no JUnit runner picks it up.
 */
object ChatPanelTestHarness {
    /**
     * Zero-based index of the `onChunk: (String) -> Unit` callback in `AgentSupervisor.sendChat`.
     *
     * Verified against the real signature at `AgentSupervisor.kt:435-449` rather than trusted from
     * research: the function takes 13 parameters and `onChunk` is the 8th of them.
     */
    private const val ON_CHUNK_INDEX = 7

    /** Zero-based index of `onComplete: (Throwable?) -> Unit` — `AgentSupervisor.kt:444`. */
    private const val ON_COMPLETE_INDEX = 8

    /**
     * Measured-sufficient number of EDT drains for the send -> response -> tool-call chain.
     *
     * Exposed as a parameter on [drainEdt] so a later plan can raise it in one place instead of
     * editing every call site.
     */
    private const val DEFAULT_EDT_DRAINS = 4

    /** Holder giving a test access to the mocks after it has driven the real UI. */
    data class Harness(
        val panel: ChatPanel,
        val api: MontoyaApi,
        val supervisor: AgentSupervisor,
    )

    /**
     * Builds a real [ChatPanel] whose backend answers every chat turn with [modelResponse].
     *
     * `sendChat` is stubbed to invoke the response callback and then the completion callback
     * synchronously on the calling thread. That is what makes the whole flow deterministic without
     * sleeping on a timer.
     */
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
            supervisor.sendChat(
                any(),
                any(),
                any(),
                anyOrNull(),
                anyOrNull(),
                any(),
                any(),
                any(),
                any(),
                anyOrNull(),
                anyOrNull(),
                anyOrNull(),
                anyOrNull(),
            ),
        ).thenAnswer { invocation ->
            @Suppress("UNCHECKED_CAST")
            val onChunk = invocation.arguments[ON_CHUNK_INDEX] as (String) -> Unit

            @Suppress("UNCHECKED_CAST")
            val onComplete = invocation.arguments[ON_COMPLETE_INDEX] as (Throwable?) -> Unit
            onChunk(modelResponse)
            onComplete(null)
            null
        }

        val panel =
            ChatPanel(
                api = api,
                supervisor = supervisor,
                getSettings = { settings },
                applySettings = { },
                validateBackend = { null },
                ensureBackendReady = { true },
                showError = { },
                onStatusChanged = { },
                onResponseReady = { },
                passiveScanner = null,
            )
        panel.createNewSession()
        return Harness(panel, api, supervisor)
    }

    /**
     * Depth-first search of the Swing component tree under [root] for the first component of [type]
     * satisfying [predicate].
     *
     * Kept generic on purpose: later plans reuse it to locate the tool-call approval card and its
     * buttons, not just the input area and Send.
     */
    fun <T : JComponent> find(
        root: Container,
        type: Class<T>,
        predicate: (T) -> Boolean = { true },
    ): T? {
        root.components.forEach { child ->
            val self = if (type.isInstance(child)) type.cast(child).takeIf(predicate) else null
            val match = self ?: (child as? Container)?.let { find(it, type, predicate) }
            if (match != null) return match
        }
        return null
    }

    /**
     * The first SEC-06 [ToolApprovalCard] in the transcript under [root], or `null` if none was added.
     *
     * A thin wrapper over [find] rather than a second traversal, so "is a decision on screen?" is asked
     * exactly one way across the suite. `null` is the meaningful answer for the two cases that must
     * NEVER produce a card: an `AUTO`-tier call, and a call the user originated themselves.
     */
    internal fun findApprovalCard(root: Container): ToolApprovalCard? = find(root, ToolApprovalCard::class.java)

    /**
     * Types [text] into the panel's real input area and clicks its real Send button on the EDT.
     *
     * The input area is the only [JTextArea] in the panel — the transcript is a `JEditorPane` — so the
     * plain depth-first lookup is unambiguous.
     */
    fun sendUserMessage(
        h: Harness,
        text: String,
    ) {
        val input =
            requireNotNull(find(h.panel.root, JTextArea::class.java)) {
                "No JTextArea found under ChatPanel.root — the input area lookup is stale."
            }
        val send =
            requireNotNull(find(h.panel.root, JButton::class.java) { it.text == "Send" }) {
                "No JButton labelled 'Send' found under ChatPanel.root — the button lookup is stale."
            }
        input.text = text
        SwingUtilities.invokeAndWait { send.doClick() }
    }

    /**
     * Drains the nested `invokeLater` blocks the send chain schedules, so assertions run after the
     * response and any tool call have been processed.
     */
    fun drainEdt(times: Int = DEFAULT_EDT_DRAINS) {
        repeat(times) { SwingUtilities.invokeAndWait { } }
    }
}
