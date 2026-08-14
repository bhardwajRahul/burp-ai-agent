package com.six2dez.burp.aiagent.ui

import com.six2dez.burp.aiagent.mcp.ToolApprovalGate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertTimeoutPreemptively
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.atLeast
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import java.awt.Component
import java.awt.Container
import java.io.File
import java.time.Duration
import javax.swing.AbstractButton
import javax.swing.SwingUtilities

/**
 * SEC-06 / SC4 — the agent tool-call trust boundary, asserted against the real production path.
 *
 * **This file drives a REAL [ChatPanel] through its REAL Send button.** It is not a model of the
 * chat flow: [ChatPanelTestHarness] constructs the production ten-parameter panel from deep-stub
 * mocks and clicks the same button a user clicks in Burp. Nothing here simulates
 * `maybeExecuteToolCall` — it is executed.
 *
 * **This file is now the SC4 acceptance gate, landed in plan 22-07.** The gate assertion is
 * [confirmToolDoesNotReachBurpBeforeADecision]: a model-emitted `proxy_http_history` call — `CONFIRM`
 * in 22-02's locked tier table — must not reach `api.proxy().history()` before a click, and must raise
 * an approval card instead. Both halves are red against the pre-gate commit and green after, which is
 * what makes the gate non-vacuous under the Phase 20 SC4 / Phase 21 rule: *a test that passes both
 * before and after the change has not tested the defect.*
 *
 * **22-01's baseline assertion is NOT inverted and NOT deleted.** It survives as
 * [autoToolStillRunsWithNoCard]: `scope_check` is `SecTier.AUTO`, so it still runs silently by design
 * after the gate ships. Inverting that line to `never()` would assert the opposite of the shipped
 * behaviour and go red forever. It passes both before and after **on purpose** — it is the `AUTO`
 * companion, never the gate.
 *
 * **Naming constraint (hard).** The class must stay `ChatPanelToolGateTest`. Renaming it to
 * `*IntegrationTest` (or `*ConcurrencyTest`, `*BackpressureTest`, `*RestartPolicyTest`,
 * `*SupervisionTest`) would make `-PexcludeHeavyTests=true` skip it — silently disabling the SC4
 * gate on exactly the PR gate where it matters most.
 */
class ChatPanelToolGateTest {
    // ── The SC4 acceptance gate ──────────────────────────────────────────────────────────

    @Test
    fun confirmToolDoesNotReachBurpBeforeADecision() {
        val h = ChatPanelTestHarness.create(modelResponse = toolCall("proxy_http_history", """{"count":5}"""))

        ChatPanelTestHarness.sendUserMessage(h, "summarise the proxy history")
        ChatPanelTestHarness.drainEdt()

        // THE ORDER OF THESE TWO ASSERTIONS IS LOAD-BEARING, NOT STYLISTIC.
        // JUnit stops at the first failure, and the red-before-green proof requires the never()
        // verification to be the assertion that fails at the pre-gate commit — a Mockito
        // NeverWantedButInvoked proving the tool DID reach Burp with nobody asked. Written the other
        // way round, a harness that never reached Burp at all would pass the never() check and fail
        // only on the card, which is trivially red pre-gate (no card type is wired in there at all)
        // and says nothing whatever about the defect.
        verify(h.api.proxy(), never()).history()
        assertNotNull(
            ChatPanelTestHarness.findApprovalCard(h.panel.root),
            "A CONFIRM-tier model-emitted tool call must raise an approval card instead of running.",
        )
    }

    // ── The AUTO companion, kept verbatim from plan 22-01 ────────────────────────────────

    @Test
    fun autoToolStillRunsWithNoCard() {
        // The exact fenced payload research measured reaching Burp. ToolCallParser.extractFirst
        // resolves the "tool" and "args" keys directly (ToolCallParser.kt:80-108).
        val h = ChatPanelTestHarness.create(modelResponse = toolCall("scope_check", """{"url":"http://evil.example/"}"""))

        ChatPanelTestHarness.sendUserMessage(h, "check scope please")
        ChatPanelTestHarness.drainEdt()

        // D-02: scope_check is SecTier.AUTO, so the gate returns Run and the call still reaches Burp.
        // Unchanged behaviour is the CORRECT result here, not a sign the gate is inert. Do not invert.
        verify(h.api.scope(), Mockito.atLeastOnce()).isInScope("http://evil.example/")
        assertNull(
            ChatPanelTestHarness.findApprovalCard(h.panel.root),
            "An AUTO tool is read-only and bounded-output; there is nothing for a human to weigh (D-02).",
        )
    }

    // ── The four D-11 actions ────────────────────────────────────────────────────────────

    @Test
    fun approveOnceExecutesTheCall() {
        val h = ChatPanelTestHarness.create(modelResponse = toolCall("proxy_http_history", """{"count":5}"""))
        ChatPanelTestHarness.sendUserMessage(h, "summarise the proxy history")
        ChatPanelTestHarness.drainEdt()
        val card = requireNotNull(ChatPanelTestHarness.findApprovalCard(h.panel.root)) { NO_CARD }

        click(card, "Approve once")
        ChatPanelTestHarness.drainEdt()

        verify(h.api.proxy(), times(1)).history()
        assertEquals(
            0,
            decisionButtons(card).size,
            "Approve once is per-call: the card that was clicked becomes a record, buttons removed.",
        )
    }

    @Test
    fun approveForSessionSuppressesTheNextCard() {
        val h = ChatPanelTestHarness.create(modelResponse = toolCall("proxy_http_history", """{"count":5}"""))
        ChatPanelTestHarness.sendUserMessage(h, "summarise the proxy history")
        ChatPanelTestHarness.drainEdt()
        val card = requireNotNull(ChatPanelTestHarness.findApprovalCard(h.panel.root)) { NO_CARD }

        click(card, "Approve for session")
        ChatPanelTestHarness.drainEdt(times = LONG_DRAIN)

        // The grant is applied to every later call in this chat with nobody asked, so the model's
        // repeat of the same tool runs straight through. `atLeast(2)` rather than a literal: the chain
        // keeps going until the monotone budget stops it, so the exact count is the budget's business,
        // not this test's. What matters is that a SECOND call ran without a second decision.
        verify(h.api.proxy(), atLeast(2)).history()
        assertTrue(
            decisionButtons(h.panel.root).isEmpty(),
            "A session grant must suppress the CARD, not just the prompt: no decision button may remain.",
        )
    }

    @Test
    fun denyReturnsAResultThatLetsTheConversationContinue() {
        val h = ChatPanelTestHarness.create(modelResponse = toolCall("proxy_http_history", """{"count":5}"""))
        ChatPanelTestHarness.sendUserMessage(h, "summarise the proxy history")
        ChatPanelTestHarness.drainEdt()
        val card = requireNotNull(ChatPanelTestHarness.findApprovalCard(h.panel.root)) { NO_CARD }

        click(card, "Deny")
        ChatPanelTestHarness.drainEdt()

        verify(h.api.proxy(), never()).history()
        val prompts = sentPrompts(h)
        // SC2: the session continues rather than erroring — a further backend turn was issued.
        assertTrue(prompts.size >= 2, "A denial must continue the conversation, not end the session. Turns: ${prompts.size}")
        assertTrue(
            prompts.any { it.contains(ToolApprovalGate.DENIAL_RESULT) },
            "The followup must carry the one D-12 denial constant verbatim.",
        )
        // D-12 / T-22-19: nothing ran, so there is no tool result. Pointing the model at one that does
        // not exist is how a refusal turns into a retry with different arguments.
        assertTrue(
            prompts.none { it.contains(SUCCESS_CLOSING_LINE) },
            "The denial followup must not reuse the success branch's closing line.",
        )
    }

    @Test
    fun denyForSessionResolvesLaterCallsWithNoCard() {
        val h = ChatPanelTestHarness.create(modelResponse = toolCall("proxy_http_history", """{"count":5}"""))
        ChatPanelTestHarness.sendUserMessage(h, "summarise the proxy history")
        ChatPanelTestHarness.drainEdt()
        val card = requireNotNull(ChatPanelTestHarness.findApprovalCard(h.panel.root)) { NO_CARD }

        click(card, "Deny for session")
        ChatPanelTestHarness.drainEdt(times = LONG_DRAIN)

        verify(h.api.proxy(), never()).history()
        assertTrue(
            decisionButtons(h.panel.root).isEmpty(),
            "A session denial must resolve later calls instantly — re-asking is the habituation D-11 avoids.",
        )
    }

    @Test
    fun confirmEachOffersOnlyTwoActions() {
        // http1_request is SecTier.CONFIRM_EACH: one click must never hand the model unlimited
        // traffic-generating capability for a whole chat (T-22-18).
        val h = ChatPanelTestHarness.create(modelResponse = toolCall("http1_request", """{"content":"GET / HTTP/1.1"}"""))
        ChatPanelTestHarness.sendUserMessage(h, "send that request")
        ChatPanelTestHarness.drainEdt()
        val card = requireNotNull(ChatPanelTestHarness.findApprovalCard(h.panel.root)) { NO_CARD }

        assertEquals(
            listOf("Deny", "Approve once"),
            decisionButtons(card).map { it.text },
            "CONFIRM_EACH offers exactly Deny and Approve once — no session-wide action, in safe-first order.",
        )
    }

    // ── SC5: user-originated calls are not double-prompted ───────────────────────────────

    @Test
    fun userDialogPathIsNotDoublePrompted() {
        // ToolInvocationDialog extends JDialog with APPLICATION_MODAL, so constructing it under
        // `-Djava.awt.headless=true` throws HeadlessException and the dialog path cannot be driven
        // headlessly. Rather than weaken the claim, assert the equivalent property directly on the
        // call site, exactly as plan 22-07 prescribes for this case.
        val body = functionBody("fun openToolDialog()")

        assertTrue(
            body.contains("McpToolExecutor.executeTool("),
            "openToolDialog must still execute the tool the user picked.",
        )
        assertTrue(
            body.contains("ToolCallOrigin.UserDialog"),
            "SC5: the dialog call site must DECLARE its origin, so the three call sites are distinguishable.",
        )
        assertTrue(
            !body.contains("ToolApprovalGate") && !body.contains("ToolApprovalCard"),
            "SC5 / T-22-32: the user picked the tool and typed the args. Asking again trains click-through.",
        )
    }

    @Test
    fun slashCommandPathIsNotDoublePrompted() {
        // Deliberately a CONFIRM tool rather than an AUTO one: an AUTO tool raises no card either way,
        // so it could not tell a gated slash path from an ungated one.
        val h = ChatPanelTestHarness.create(modelResponse = toolCall("proxy_http_history", """{"count":5}"""))

        ChatPanelTestHarness.sendUserMessage(h, """/tool proxy_http_history {"count":5}""")
        ChatPanelTestHarness.drainEdt()

        verify(h.api.proxy(), times(1)).history()
        assertNull(
            ChatPanelTestHarness.findApprovalCard(h.panel.root),
            "SC5 / T-22-32: the user typed this call themselves; it must not be gated.",
        )
    }

    // ── SC4's ChatPanel-side monotone budget ─────────────────────────────────────────────

    @Test
    fun eightConsecutiveDenialsTerminateTheChainWithNoNinthTurn() {
        assertEquals(
            8,
            ChatPanel.MAX_AUTO_TOOL_ITERATIONS,
            "This test's NAME says eight. If the budget ever changes, rename the test — do not relax it.",
        )
        val expectedTurns = expectedTurnsForAFullyDeniedChain()
        val h = ChatPanelTestHarness.create(modelResponse = toolCall("proxy_http_history", """{"count":5}"""))

        // A non-monotone budget would loop forever; fail the suite instead of stalling CI.
        assertTimeoutPreemptively(Duration.ofSeconds(30)) {
            ChatPanelTestHarness.sendUserMessage(h, "summarise the proxy history")
            ChatPanelTestHarness.drainEdt()
            val card = requireNotNull(ChatPanelTestHarness.findApprovalCard(h.panel.root)) { NO_CARD }
            // One click; every later turn is auto-denied with no further interaction.
            click(card, "Deny for session")
            // Drain EXPLICITLY. drainEdt's default of 4 was measured sufficient for ONE turn, and each
            // chained turn sits behind exactly one invokeLater, so a bare drainEdt() would stop this
            // chain around turn 5. Over-draining is free: invokeAndWait on an empty queue is a no-op.
            ChatPanelTestHarness.drainEdt(times = ChatPanel.MAX_AUTO_TOOL_ITERATIONS * 2 + 4)
        }

        // ASSERT (b) FIRST, AND THAT IS THE POINT. Clauses (a) and (c) pass VACUOUSLY on an
        // under-drained chain: a chain stopped at turn 5 by too few drains also "terminates" and also
        // never reached Burp. Only the turn count fails loudly when the drain is short, so it is the
        // clause that licenses the other two. A future maintainer who sees this fail must fix the
        // drain or the budget — never relax the expected count.
        verifySendChatCount(h, expectedTurns)

        // (a) The chain really has terminated: draining further produces no further backend turn.
        ChatPanelTestHarness.drainEdt(times = LONG_DRAIN)
        verifySendChatCount(h, expectedTurns)

        // (c) Not one of the denied calls reached Burp.
        verify(h.api.proxy(), never()).history()
    }
}

// ── Helpers ──────────────────────────────────────────────────────────────────────────────

private const val NO_CARD = "No ToolApprovalCard in the transcript — the SEC-06 gate did not raise a decision."

/** The success-branch closing line the denial variant must never reuse (ChatPanel.executeApprovedToolCall). */
private const val SUCCESS_CLOSING_LINE = "Provide the final response using the tool result"

/** Comfortably longer than any chain this suite drives; over-draining an empty EDT queue is free. */
private const val LONG_DRAIN = 24

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

/**
 * Every live decision button under [root], in render order.
 *
 * A count, not a first match, because the properties that carry the security claim here are
 * exhaustive ones: "exactly two actions", "no button remains anywhere in the transcript".
 */
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

/**
 * Every prompt string handed to the backend, in order.
 *
 * `text` is the third parameter of `AgentSupervisor.sendChat`; the other twelve are matched
 * positionally so the captor lines up with it.
 */
private fun sentPrompts(h: ChatPanelTestHarness.Harness): List<String> {
    val prompt = argumentCaptor<String>()
    verify(h.supervisor, atLeast(1)).sendChat(
        any(),
        any(),
        prompt.capture(),
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
    )
    return prompt.allValues
}

private fun verifySendChatCount(
    h: ChatPanelTestHarness.Harness,
    expected: Int,
) {
    verify(h.supervisor, times(expected)).sendChat(
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
    )
}

/**
 * How many backend turns a chain of nothing but denials may produce, DERIVED rather than written out.
 *
 * Walks the real budget with the real helpers — `ChatPanel.MAX_AUTO_TOOL_ITERATIONS` (widened to
 * `internal` for exactly this), `ToolApprovalGate.allowsFurtherToolCalls` and
 * `ToolApprovalGate.nextIterationBudget` — so raising the budget or changing either helper moves this
 * expectation with it instead of leaving a stale literal quietly passing.
 *
 * One initial user turn, plus one followup per denial. D-13 makes a denial cost the same as an
 * approval, so the walk terminates.
 */
private fun expectedTurnsForAFullyDeniedChain(): Int {
    var remaining = ChatPanel.MAX_AUTO_TOOL_ITERATIONS
    var turns = 1
    while (remaining > 0) {
        turns++
        if (!ToolApprovalGate.allowsFurtherToolCalls(remaining)) break
        remaining = ToolApprovalGate.nextIterationBudget(remaining)
    }
    return turns
}

/**
 * The source text of one `ChatPanel` function, brace-matched from its declaration.
 *
 * Reading source is the sanctioned fallback for [ChatPanelToolGateTest.userDialogPathIsNotDoublePrompted]
 * only, where the production path is a modal `JDialog` and cannot be constructed headlessly at all.
 * Every other test in this file asserts on executed behaviour.
 */
private fun functionBody(declaration: String): String {
    val source = File("src/main/kotlin/com/six2dez/burp/aiagent/ui/ChatPanel.kt").readText()
    val start = source.indexOf(declaration)
    require(start >= 0) { "No '$declaration' in ChatPanel.kt — this structural assertion is stale." }
    val open = source.indexOf('{', start)
    var depth = 0
    var index = open
    while (index < source.length) {
        if (source[index] == '{') depth++
        if (source[index] == '}') {
            depth--
            if (depth == 0) break
        }
        index++
    }
    return source.substring(open, index + 1)
}
