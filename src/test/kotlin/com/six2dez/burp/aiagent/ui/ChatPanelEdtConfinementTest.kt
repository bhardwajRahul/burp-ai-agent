package com.six2dez.burp.aiagent.ui

import burp.api.montoya.proxy.ProxyHttpRequestResponse
import com.six2dez.burp.aiagent.audit.AuditLogger
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertTimeoutPreemptively
import org.mockito.kotlin.whenever
import java.awt.Component
import java.awt.Container
import java.time.Duration
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicReference
import javax.swing.AbstractButton
import javax.swing.SwingUtilities

/**
 * REL-05 / SC1 · SC2 · SC3 — an approved tool call runs OFF the AWT Event Dispatch Thread.
 *
 * **This file drives a REAL [ChatPanel] through its REAL Send button and its REAL approval card**, via
 * [ChatPanelTestHarness]. Nothing about the dispatch path is modelled: the assertions read a `Thread`
 * the production code actually created, captured inside the deep-stub `MontoyaApi` at the moment the
 * tool body ran.
 *
 * **Naming constraint (hard), inherited from [ChatPanelTestHarness]'s own KDoc.** The PR gate runs
 * `./gradlew test -PexcludeHeavyTests=true` (`.github/workflows/build.yml:47`) and
 * `build.gradle.kts:206-213` excludes `*IntegrationTest`, `*ConcurrencyTest`, `*BackpressureTest`,
 * `*RestartPolicyTest` and `*SupervisionTest` under that flag. `ChatPanelConcurrencyTest` already sits
 * in the excluded set, which makes `*ConcurrencyTest` the natural — and silently fatal — name to reach
 * for here. `ChatPanelEdtConfinementTest` is the approved one. Do not rename this class.
 *
 * **No assertion in this file compares an elapsed duration to a threshold.** Every timeout present is a
 * deadlock failsafe whose margin over the work is four orders of magnitude — categorically unlike the
 * `RedactionTest` wall-clock flake, where a 50 ms deadline bounds work of the same order.
 */
class ChatPanelEdtConfinementTest {
    /**
     * S-01 / SC1 / SC2 / AI-SPEC E2 + E9 — the tool body runs on a named daemon thread that is not the
     * EDT, proven from a captured `Thread` rather than inferred after the fact.
     *
     * Capturing the `Thread` itself rather than a `Boolean` satisfies three separate claims from one
     * observation — not the EDT (E2), daemon (E9's unload clause), named (E9's thread-dump clause) —
     * and it means the assertions read a RECORDED thread. Re-deriving thread identity after the drive
     * would answer about the test's own thread, which is never the question.
     *
     * The deep-stub `MontoyaApi` is the only tool-body seam that exists: `McpToolExecutor` is an
     * `object` singleton (`McpToolExecutorImpl.kt:45`) referenced statically at every call site, so
     * there is no executor double to put a probe in.
     */
    @Test
    fun anApprovedChainToolExecutesOnANamedDaemonThread() {
        val toolThread = AtomicReference<Thread?>(null)
        val h = ChatPanelTestHarness.create(modelResponse = toolCall("proxy_http_history", """{"count":5}"""))
        whenever(h.api.proxy().history()).thenAnswer {
            toolThread.set(Thread.currentThread())
            emptyList<ProxyHttpRequestResponse>()
        }

        // A deadlock failsafe, matching ChatPanelToolGateTest.kt:377 — if the dispatch never completes
        // the suite fails instead of stalling CI. Nothing here is timed against it.
        assertTimeoutPreemptively(Duration.ofSeconds(30)) {
            ChatPanelTestHarness.sendUserMessage(h, "summarise the proxy history")
            ChatPanelTestHarness.drainEdt()
            click(requireNotNull(ChatPanelTestHarness.findApprovalCard(h.panel.root)) { NO_CARD }, "Approve once")
            ChatPanelTestHarness.awaitToolSettled(count = 1)
        }

        val captured = requireNotNull(toolThread.get()) { "The tool body never ran — nothing was dispatched." }
        assertNotEquals(
            EDT_THREAD_NAME,
            captured.name,
            "SC1/SC2: an approved tool call must not execute on the EDT. Burp refuses HTTP from the EDT " +
                "and the whole Burp UI freezes for as long as the call runs there.",
        )
        assertTrue(captured.isDaemon, "E9/D-08: a non-daemon worker can hold extension unload open.")
        assertTrue(
            captured.name.startsWith("burp-ai-tool"),
            "E9: a stuck worker must be identifiable in a thread dump. Thread name was '${captured.name}'.",
        )
    }

    // ── Audit + worker capture plumbing ──────────────────────────────────────────────────

    private val auditEvents = CopyOnWriteArrayList<Pair<String, Map<*, *>>>()

    @BeforeEach
    fun installObservers() {
        auditEvents.clear()
        AuditLogger.registerGlobalEmitter { type, payload ->
            if (payload is Map<*, *>) auditEvents += type to payload
        }
        ChatPanelTestHarness.installSettledObserver()
    }

    @AfterEach
    fun releaseObservers() {
        // Both are process-global singleton hooks. Left registered, this class would capture — and hold
        // — events emitted by every test class that runs after it. Same discipline as
        // ChatPanelToolGateTest.kt:649-654, and for the same reason.
        AuditLogger.registerGlobalEmitter(null)
        ChatPanelTestHarness.releaseSettledObserver()
    }
}

// ── Helpers ──────────────────────────────────────────────────────────────────────────────

private const val NO_CARD = "No ToolApprovalCard in the transcript — the SEC-06 gate did not raise a decision."

/** The AWT Event Dispatch Thread's name, which is what "not the EDT" is asserted against. */
private const val EDT_THREAD_NAME = "AWT-EventQueue-0"

/** The four D-11 labels, defined once so "is this a decision button?" is asked one way. */
private val DECISION_LABELS = setOf("Deny", "Deny for session", "Approve once", "Approve for session")

/** The fenced payload shape `ToolCallParser.extractFirst` resolves (ToolCallParser.kt:80-108). */
private fun toolCall(
    tool: String,
    argsJson: String,
): String =
    """
    ```json
    {"tool":"$tool","args":$argsJson}
    ```
    """.trimIndent()

private fun allDescendants(root: Container): List<Component> =
    root.components.flatMap { child ->
        listOf(child) + if (child is Container) allDescendants(child) else emptyList()
    }

private fun decisionButtons(root: Container): List<AbstractButton> = allDescendants(root).filterIsInstance<AbstractButton>().filter { it.text in DECISION_LABELS }

/** Clicks a decision button on the EDT, exactly as the AWT event pump would dispatch it. */
private fun click(
    card: Container,
    label: String,
) {
    val button =
        requireNotNull(decisionButtons(card).firstOrNull { it.text == label }) {
            "No '$label' button on the card. Present: ${decisionButtons(card).map { it.text }}"
        }
    SwingUtilities.invokeAndWait { button.doClick() }
}
