package com.six2dez.burp.aiagent.ui

import burp.api.montoya.proxy.ProxyHttpRequestResponse
import com.six2dez.burp.aiagent.audit.AuditLogger
import com.six2dez.burp.aiagent.mcp.ToolApprovalGate
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertTimeoutPreemptively
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.atLeast
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.awt.Component
import java.awt.Container
import java.time.Duration
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import javax.swing.AbstractButton
import javax.swing.JButton
import javax.swing.JEditorPane
import javax.swing.JTextArea
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

    /**
     * S-02 / SC2 — a Montoya double that refuses the EDT is never called on the EDT.
     *
     * **The double's message is a claim about THIS TEST, not about Burp's runtime.** Research assumption
     * A1 records that Burp's *"Extensions should not make HTTP requests in the Swing event dispatch
     * thread"* exception is corroborated — by this repo's own `MontoyaHttpTransport` workaround for #80
     * and by third-party reports — but not live-confirmed here. Constructing the refusal ourselves is
     * what closes that gap: the test pins *"we do not call Burp's API from the EDT"* against a double we
     * control, so it holds regardless of what a live Burp actually does. Live confirmation is a UAT item
     * routed by plan 23-05. Do not read the message below as evidence of Burp's behaviour.
     *
     * The tool is asserted to have completed **normally** rather than merely to have run: `McpTool.runTool`
     * catches `Exception` and turns it into an error RESULT, so a call that did hit the EDT would still
     * produce a transcript row — and only the recorded run status tells the two apart.
     *
     * **`proxy_http_history` rather than the `http1_request` this scenario was drafted against, and the
     * reason is measured rather than assumed.** Driven headlessly, `http1_request` records
     * `status=error` with a 112-character error result before any EDT question arises: its body reaches
     * Montoya's `HttpRequest.httpRequest` static factory, which `McpScopeFilter.deriveScopeUrl`'s own
     * KDoc already records as *"unavailable in pure-JVM unit tests"*. The assertion would then be red
     * for a reason that has nothing to do with the EDT, which is the one thing a red-before-green gate
     * must never be. `proxy_http_history` is the same trust boundary through a seam that works.
     */
    @Test
    fun aToolThatRefusesTheEdtCompletesNormally() {
        val h = ChatPanelTestHarness.create(modelResponse = toolCall("proxy_http_history", """{"count":5}"""))
        whenever(h.api.proxy().history()).thenAnswer {
            check(!SwingUtilities.isEventDispatchThread()) { BURP_EDT_REFUSAL }
            emptyList<ProxyHttpRequestResponse>()
        }

        // assertTimeoutPreemptively runs its block on a SEPARATE thread, so the trace id crosses back
        // through an AtomicReference rather than a captured local.
        val traceIdRef = AtomicReference<String>()
        assertTimeoutPreemptively(Duration.ofSeconds(30)) {
            ChatPanelTestHarness.sendUserMessage(h, "summarise the proxy history")
            ChatPanelTestHarness.drainEdt()
            val card = requireNotNull(ChatPanelTestHarness.findApprovalCard(h.panel.root)) { NO_CARD }
            traceIdRef.set(pendingTraceId(h))
            click(card, "Approve once")
            ChatPanelTestHarness.awaitToolSettled(count = 1)
        }

        val record = decisionsFor(traceIdRef.get()).single()
        assertEquals(
            "ok",
            record["status"],
            "SC2: the tool ran to completion, so it was never entered from the EDT. A status of 'error' " +
                "here means the double's EDT refusal fired — i.e. the call WAS made on the EDT.",
        )
    }

    /**
     * S-01 / SC3 / AI-SPEC E3 — the EDT runs queued work WHILE a tool call is mid-flight.
     *
     * **A mutual latch handshake, not a stopwatch.** The tool double counts down [ToolLatches.entered]
     * and then waits on [ToolLatches.probeRan], which the test counts down from a runnable it queues to
     * the EDT *after* the tool has been entered. A free EDT therefore runs that runnable and the tool
     * returns; a blocked EDT **deadlocks**, and a deadlock is a categorical failure
     * `assertTimeoutPreemptively` reports.
     *
     * **Neither timeout is a wall-clock assertion, and the distinction is not a technicality.** Nothing
     * here is compared against the duration of the work: the inner failsafe bounds a deadlock and the
     * outer one stops CI stalling, exactly as `ChatPanelToolGateTest.kt:377` already does for a
     * non-monotone chain budget. The margin over the work — which settles in microseconds once the
     * handshake resolves — is four orders of magnitude. That is what separates this from the
     * `RedactionTest` flake, where a 50 ms deadline bounds work of the same order and fails under load.
     *
     * The inner failsafe is 10 seconds and not 60 for a budgeted reason: a red-before-green run against
     * the pre-fix tree pays it in full, once, and ten seconds is what that demonstration is worth.
     */
    @Test
    fun theEdtRunsQueuedWorkWhileAToolCallIsMidFlight() {
        val latches = ToolLatches()
        val h = ChatPanelTestHarness.create(modelResponse = toolCall("proxy_http_history", """{"count":5}"""))
        whenever(h.api.proxy().history()).thenAnswer {
            latches.entered.countDown()
            // The tool waits on the EDT. This is what makes a FREE EDT a precondition for the tool
            // returning, and therefore what makes a blocked one a deadlock rather than a slow path.
            check(latches.probeRan.await(HANDSHAKE_FAILSAFE_SECONDS, TimeUnit.SECONDS)) {
                "The EDT never ran a queued runnable while the tool was mid-call — it was blocked inside it."
            }
            emptyList<ProxyHttpRequestResponse>()
        }

        val traceIdRef = AtomicReference<String>()
        assertTimeoutPreemptively(Duration.ofSeconds(30)) {
            ChatPanelTestHarness.sendUserMessage(h, "summarise the proxy history")
            ChatPanelTestHarness.drainEdt()
            val card = requireNotNull(ChatPanelTestHarness.findApprovalCard(h.panel.root)) { NO_CARD }
            traceIdRef.set(pendingTraceId(h))
            click(card, "Approve once")

            assertTrue(
                latches.entered.await(HANDSHAKE_FAILSAFE_SECONDS, TimeUnit.SECONDS),
                "The tool worker never started, so the handshake below would prove nothing.",
            )
            // Queued WHILE the tool is mid-call. On a blocked EDT this never runs and the tool's own
            // await above expires; on a free one it runs and releases the tool.
            SwingUtilities.invokeLater { latches.probeRan.countDown() }
            ChatPanelTestHarness.awaitToolSettled(count = 1)
        }

        // THE RUN STATUS IS THE CLAUSE THAT FAILS LOUDLY, AND IT IS ASSERTED FIRST FOR THAT REASON.
        // The latch count below is NOT sufficient on its own and would pass VACUOUSLY against the
        // pre-fix tree: with the tool running on the EDT, the click blocks until the tool's own await
        // expires, the tool returns an error, the click returns — and only THEN does the queued runnable
        // finally run, leaving the latch at zero and the assertion green with the defect fully present.
        // Completing NORMALLY is the property only a free EDT can produce, because the tool returns
        // normally only if the runnable ran while it was still inside its call.
        assertEquals(
            "ok",
            decisionsFor(traceIdRef.get()).single()["status"],
            "SC3/E3: the tool returned normally, which is possible only if the EDT ran the queued " +
                "runnable while the tool was still mid-call. An 'error' status means the tool's own " +
                "await expired — the EDT was blocked inside the call.",
        )
        assertEquals(
            0L,
            latches.probeRan.count,
            "Corroborates the above: the queued runnable really did execute.",
        )
    }

    /**
     * SC3 / AI-SPEC E3 — a full auto-approved chain produces eight results in submission order.
     *
     * **Selected by trace id, never by list position** (Phase 22 commit `ab55ff5`). `AuditLogger`'s
     * emitter is a process-global hook and panels built by earlier tests in this class are never shut
     * down, so a positional read over the collected events silently mixes in another test's chain. A
     * chain threads ONE trace id through every followup turn, which is what makes it the correct
     * selector — and the `step` field, not the list index, is what carries the ordering claim.
     *
     * `awaitToolSettled(count = 8)` is the primary synchronisation rather than a raised drain count: a
     * chain of eight daemon workers is precisely what draining the EDT queue is blind to.
     */
    @Test
    fun aFullAutoChainProducesEightResultsInSubmissionOrder() {
        // FIRST, AND IN ITS OWN ASSERTION. If the budget ever changes, this fails by name and the test
        // gets renamed — the alternative is a derived expectation that silently relaxes.
        assertEquals(
            8,
            ChatPanel.MAX_AUTO_TOOL_ITERATIONS,
            "This test's name says eight. If the budget changes, rename the test — do not relax it.",
        )
        val h = ChatPanelTestHarness.create(modelResponse = toolCall("scope_check", """{"url":"http://evil.example/"}"""))

        assertTimeoutPreemptively(Duration.ofSeconds(60)) {
            ChatPanelTestHarness.sendUserMessage(h, "check scope repeatedly")
            ChatPanelTestHarness.awaitToolSettled(count = ChatPanel.MAX_AUTO_TOOL_ITERATIONS)
        }

        // The chain's identity, taken from THIS test's own dispatch record rather than guessed from the
        // audit log. That it collapses to exactly one value is itself the claim that one chain ran.
        val chainTraceId =
            requireNotNull(ChatPanelTestHarness.settledLabels().distinct().singleOrNull()) {
                "Expected exactly one chain to have settled; labels were ${ChatPanelTestHarness.settledLabels()}."
            }
        // LICENSES THE ORDERING CLAIM BELOW, so it is asserted first. A short chain would also produce a
        // correctly ORDERED prefix; only the count fails loudly when the chain stopped early.
        assertEquals(
            ChatPanel.MAX_AUTO_TOOL_ITERATIONS,
            ChatPanelTestHarness.settledLabels().size,
            "Eight tool workers must have settled — one per chain step.",
        )
        assertEquals(
            (1..ChatPanel.MAX_AUTO_TOOL_ITERATIONS).map { it.toString() },
            decisionsFor(chainTraceId).map { it["step"] },
            "SC3: eight results, each recorded at its own chain step, in submission order.",
        )
        // The busy state is cleared exactly once per call: the chain ends in S0.
        assertTrue(sendButton(h).isVisible, "The panel must finish the chain in S0 with Send visible.")
        assertTrue(inputArea(h).isEnabled, "The panel must finish the chain in S0 with the input enabled.")
    }

    /**
     * S-04 / D-07 / UI-SPEC Rules S-5 and C-1 — Cancel is a live affordance while a tool is running,
     * and it tells the user the truth about what happened to their request.
     *
     * The five things Cancel must do in state S3 are asserted together, because each of them is a way
     * the others could be satisfied dishonestly: the panel returns to S0 **immediately** (not when the
     * worker finishes), the transcript says the request was already sent rather than cancelled, the
     * result is discarded with no followup turn, no chain iteration is refunded — and the call is
     * **still audited**, which is D-07's load-bearing corollary. A cancelled call that became an
     * unlogged call would put a hole in the SEC-06 record at exactly the point a user interrupted
     * something.
     *
     * The worker is never interrupted and this test does not check for an interrupt: Montoya's
     * `sendRequest` takes no cancellation token, so an interrupt would work for one transport and
     * silently no-op for Burp's. A cancel that works sometimes is worse than one that states its limits.
     */
    @Test
    fun cancellingARunningToolDiscardsItsResultAndStillAuditsTheCall() {
        val latches = ToolLatches()
        val h = ChatPanelTestHarness.create(modelResponse = toolCall("proxy_http_history", """{"count":5}"""))
        whenever(h.api.proxy().history()).thenAnswer {
            latches.entered.countDown()
            check(latches.probeRan.await(HANDSHAKE_FAILSAFE_SECONDS, TimeUnit.SECONDS)) {
                "The test never released the blocked tool — the cancel window was never opened."
            }
            emptyList<ProxyHttpRequestResponse>()
        }

        val traceIdRef = AtomicReference<String>()
        assertTimeoutPreemptively(Duration.ofSeconds(30)) {
            ChatPanelTestHarness.sendUserMessage(h, "summarise the proxy history")
            ChatPanelTestHarness.drainEdt()
            val card = requireNotNull(ChatPanelTestHarness.findApprovalCard(h.panel.root)) { NO_CARD }
            traceIdRef.set(pendingTraceId(h))
            click(card, "Approve once")
            assertTrue(
                latches.entered.await(HANDSHAKE_FAILSAFE_SECONDS, TimeUnit.SECONDS),
                "The tool worker never started, so there was nothing to cancel.",
            )

            // The real Cancel button, clicked on the EDT exactly as the AWT pump would dispatch it,
            // WHILE the tool is still blocked inside its call.
            SwingUtilities.invokeAndWait { cancelButton(h).doClick() }

            // ASSERTED BEFORE THE LATCH IS RELEASED, and that ordering is the claim. "The panel returns
            // to S0 immediately" is only meaningful while the worker is demonstrably still running —
            // read after the release it would also pass on an implementation that made the user wait.
            assertTrue(sendButton(h).isVisible, "UI-SPEC S-5: Cancel returns the panel to S0 at once.")
            assertTrue(inputArea(h).isEnabled, "UI-SPEC S-5: the input is usable again at once.")

            latches.probeRan.countDown()
            ChatPanelTestHarness.awaitToolSettled(count = 1)
        }

        val transcript = transcriptText(h)
        assertTrue(
            transcript.contains("was already sent to Burp and will finish"),
            "UI-SPEC C-1: the user must be told the request really was sent. Transcript: $transcript",
        )
        assertFalse(
            transcript.contains("Request cancelled."),
            "UI-SPEC C-1: the backend-turn line is FALSE on the tool path — the request was not cancelled.",
        )
        assertFalse(
            transcript.contains("Tool result: proxy_http_history"),
            "UI-SPEC S4: a superseded run renders no transcript row for its discarded result.",
        )
        // D-07's corollary: the call reached Burp and cannot be recalled, so it stays in the record.
        val record = decisionsFor(traceIdRef.get()).single()
        assertEquals(
            ChatPanel.SUPERSEDED_RUN_STATUS,
            record["status"],
            "The cancelled call must be audited, with the cancellation recorded rather than filed as a " +
                "malfunction or as a policy refusal.",
        )
        assertEquals("approve_once", record["decision"], "The decision the user really made is unchanged by the cancel.")
        // No followup turn: exactly the one turn the user's own message started. A second would mean the
        // discarded result was fed back to the model anyway.
        verifySendChatCount(h, 1)
    }

    /**
     * S-03 / SC1 / AI-SPEC E1 — a Deny executes nothing and STARTS nothing, and the model is still told.
     *
     * **The dispatch record is the load-bearing selector, and reading the settle record instead would
     * make this test pass vacuously.** A settle event is written from the EDT tail's `finally`, so in
     * precisely the world this assertion exists to catch — a denial that dispatched a worker anyway —
     * that worker has not finished when the assertion runs and its label is missing from the settle
     * record too. The dispatch record is written synchronously with the dispatch statement itself
     * (`OffEdtDispatch.kt`), so absence there is a claim about what happened rather than about timing.
     *
     * The captured `Thread` is the same argument one layer down: `never()` on the Montoya double proves
     * Burp was not reached, and a null capture proves no worker got far enough to reach for it.
     *
     * **This is AI-SPEC E1's PASS clause.** Phase 23 changes WHERE an approved call runs; it must never
     * change WHETHER a call runs. The SEC-06 trust boundary is still evaluated on the EDT, before any
     * dispatch, and a refusal still refuses.
     */
    @Test
    fun aDeniedToolCallStartsNoWorkerAndStillContinuesTheConversation() {
        val toolThread = AtomicReference<Thread?>(null)
        // A CONFIRM-tier tool, deliberately: an AUTO-tier one raises no card at all, so it could not
        // tell a working gate from a missing one.
        val h = ChatPanelTestHarness.create(modelResponse = toolCall("proxy_http_history", """{"count":5}"""))
        whenever(h.api.proxy().history()).thenAnswer {
            toolThread.set(Thread.currentThread())
            emptyList<ProxyHttpRequestResponse>()
        }

        val traceIdRef = AtomicReference<String>()
        assertTimeoutPreemptively(Duration.ofSeconds(30)) {
            ChatPanelTestHarness.sendUserMessage(h, "summarise the proxy history")
            ChatPanelTestHarness.drainEdt()
            val card = requireNotNull(ChatPanelTestHarness.findApprovalCard(h.panel.root)) { NO_CARD }
            // Captured while exactly one decision is parked, before the click resolves it.
            traceIdRef.set(pendingTraceId(h))
            click(card, "Deny")
            ChatPanelTestHarness.drainEdt(times = LONG_DRAIN)
        }

        verify(h.api.proxy(), never()).history()
        assertNull(
            toolThread.get(),
            "SEC-06 / E1: the tool body ran on ${toolThread.get()?.name} after a Deny. A refusal that " +
                "still reaches Burp is the whole failure this gate exists to prevent.",
        )
        assertFalse(
            ChatPanelTestHarness.dispatchedLabels().contains(traceIdRef.get()),
            "REL-05 / E1: a denied call must start NO worker. Read from the dispatch record, which is " +
                "written synchronously with the dispatch — an unfinished worker is absent from the " +
                "settle record too, so that record could not tell these two worlds apart. " +
                "Dispatched: ${ChatPanelTestHarness.dispatchedLabels()}.",
        )
        val prompts = sentPrompts(h)
        assertTrue(
            prompts.size >= 2,
            "D-12: a refusal must be answerable — the model gets the denial back and the session " +
                "continues. Turns: ${prompts.size}.",
        )
        assertTrue(
            prompts.any { it.contains(ToolApprovalGate.DENIAL_RESULT) },
            "The followup must carry the one D-12 denial constant verbatim, so the model is told the " +
                "call was refused rather than left to infer it from silence.",
        )
    }

    // ── Audit + worker capture plumbing ──────────────────────────────────────────────────

    private val auditEvents = CopyOnWriteArrayList<Pair<String, Map<*, *>>>()

    /**
     * Every SC3 decision record belonging to [traceId], in emission order.
     *
     * The trace id is the ONLY sound selector here: `AuditLogger.registerGlobalEmitter` is a
     * process-global hook, panels built by earlier tests in this class are never shut down, and their
     * queued `invokeLater` chains run on this test's drains. Filtering by tool name or reading
     * positionally would silently fold in another chain's events.
     */
    private fun decisionsFor(traceId: String): List<Map<*, *>> = auditEvents.filter { it.first == TOOL_DECISION_EVENT && it.second["traceId"] == traceId }.map { it.second }

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

/**
 * Comfortably longer than any followup this suite drives after a decision click; over-draining an
 * empty EDT queue is free, and under-draining would leave the denial's followup turn unsent.
 */
private const val LONG_DRAIN = 24

/** The AWT Event Dispatch Thread's name, which is what "not the EDT" is asserted against. */
private const val EDT_THREAD_NAME = "AWT-EventQueue-0"

/** The `AuditLogger` event type every SC3 decision record is emitted under. */
private const val TOOL_DECISION_EVENT = "mcp_tool_decision"

/**
 * Burp's refusal text, reproduced in a double THIS SUITE controls.
 *
 * It is not evidence of Burp's runtime behaviour — see [ChatPanelEdtConfinementTest] S-02 — and the
 * live confirmation is a UAT item. It reads as Burp's message so a maintainer meeting the failure
 * recognises which real-world constraint the test encodes.
 */
private const val BURP_EDT_REFUSAL = "Extensions should not make HTTP requests in the Swing event dispatch thread"

/**
 * Deadlock failsafe for the SC3 handshake, in seconds.
 *
 * A bound on a hang, never a threshold the work is measured against: nothing compares an elapsed
 * duration to it. Ten and not sixty because a red-before-green run against the pre-fix tree pays it in
 * full, once, and ten seconds is the budgeted price of that demonstration.
 */
private const val HANDSHAKE_FAILSAFE_SECONDS = 10L

/** The two halves of the SC3 mutual handshake, named so the test body reads as the protocol it is. */
private class ToolLatches {
    /** Counted down by the tool double the instant its body is entered. */
    val entered = CountDownLatch(1)

    /** Counted down from a runnable the test queues to the EDT; the tool double waits on it. */
    val probeRan = CountDownLatch(1)
}

/**
 * The trace id of the single parked decision, read reflectively off the live panel.
 *
 * Same helper, and the same reason, as `ChatPanelToolGateTest.kt:818`: every audit assertion needs a
 * selector that is this chain's alone, and the trace id is the only field that qualifies.
 */
private fun pendingTraceId(h: ChatPanelTestHarness.Harness): String {
    lateinit var traceId: String
    SwingUtilities.invokeAndWait {
        val field = ChatPanel::class.java.getDeclaredField("pendingDecisions")
        field.isAccessible = true
        val pending = field.get(h.panel) as Map<*, *>
        val record = requireNotNull(pending.values.singleOrNull()) { "Expected exactly one parked decision; found ${pending.keys}." }
        val slot = record.javaClass.getDeclaredField("traceId").also { it.isAccessible = true }
        traceId = slot.get(record) as String
    }
    return traceId
}

/** The panel's real Send button — one half of the UI-SPEC S0 assertion. */
private fun sendButton(h: ChatPanelTestHarness.Harness): JButton =
    requireNotNull(ChatPanelTestHarness.find(h.panel.root, JButton::class.java) { it.text == "Send" }) {
        "No JButton labelled 'Send' under ChatPanel.root — the button lookup is stale."
    }

/** The panel's real input area, found by the same `isEditable` rule the harness documents. */
private fun inputArea(h: ChatPanelTestHarness.Harness): JTextArea =
    requireNotNull(ChatPanelTestHarness.find(h.panel.root, JTextArea::class.java) { it.isEditable }) {
        "No editable JTextArea under ChatPanel.root — the input area lookup is stale."
    }

/** The panel's real Cancel button — the affordance UI-SPEC Rule S-5 says must not be inert in S3. */
private fun cancelButton(h: ChatPanelTestHarness.Harness): JButton =
    requireNotNull(ChatPanelTestHarness.find(h.panel.root, JButton::class.java) { it.text == "Cancel" }) {
        "No JButton labelled 'Cancel' under ChatPanel.root — Rule C-1 pins that label, so a miss here " +
            "means it was renamed."
    }

/**
 * The transcript's rendered text, tags stripped.
 *
 * Read through the `HTMLDocument` rather than off `JEditorPane.text`: the transcript renders through
 * `MarkdownRenderer.toHtml`, so the raw property is markup in which a sentence can be split by tags,
 * and a `contains` over it would be a claim about the renderer instead of about the copy.
 */
private fun transcriptText(h: ChatPanelTestHarness.Harness): String =
    allDescendants(h.panel.root)
        .filterIsInstance<JEditorPane>()
        .joinToString(" ") { pane -> pane.document.getText(0, pane.document.length) }

/**
 * Asserts the exact number of backend turns.
 *
 * `sendChat` takes 13 parameters; all are matched positionally so the verification lines up.
 */

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
 * Every prompt handed to the backend, in turn order.
 *
 * `sendChat` takes 13 parameters; all are matched positionally so the captor lines up with the third.
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
