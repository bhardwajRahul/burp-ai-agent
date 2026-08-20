package com.six2dez.burp.aiagent.ui

import burp.api.montoya.proxy.ProxyHttpRequestResponse
import com.six2dez.burp.aiagent.TestSettings
import com.six2dez.burp.aiagent.audit.AuditLogger
import com.six2dez.burp.aiagent.config.AgentSettings
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
import org.mockito.kotlin.doAnswer
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
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import javax.swing.AbstractButton
import javax.swing.JButton
import javax.swing.JEditorPane
import javax.swing.JList
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

    /**
     * S-05 / D-08 — deleting a session supersedes the tool worker its chain left running.
     *
     * **This scenario is the whole reason plan 23-04's Task 1 exists, and it is written so that
     * removing that one line turns it red.** Session delete is the ONE teardown exit of four that does
     * not route through `cancelInFlightRequest`, so it inherited nothing from plan 23-01's supersede
     * cell (research F-5). Without the explicit `runningTool.take()` in `deleteConfirmedSession` the
     * returning worker finds its token still current, takes the SUCCESS branch, files an `"ok"` run
     * for a session that no longer exists, and sends a followup turn continuing that dead chain.
     *
     * **Two clauses are loud and two are not, and which is which was MEASURED against the reverted
     * tree rather than assumed.** Loud: the recorded run status (`"ok"` with the defect present), and
     * the `supersedeReason` key, which only the superseded exit merges onto the pair. Corroboration
     * only:
     * - the missing transcript row — `ToolCallCapture` freezes the `SessionPanel` OBJECT before
     *   dispatch, and a deleted session's panel is DETACHED rather than destroyed, so the row really
     *   is written on the un-superseded tree, into a panel [transcriptText] can no longer reach;
     * - the turn count — `sendMessage` returns early when `sessionPanels[sessionId]` is null
     *   (`ChatPanel.kt`), and a session delete removes exactly that entry, so the followup the success
     *   branch asks for is refused one layer further down whether or not the supersede happened.
     *
     * Both stay, because both are the claim for a supersede whose panel SURVIVES — the unload exit
     * below is one — and neither may ever be promoted to carry this test on its own.
     *
     * **`deleteConfirmedSession` rather than `deleteSession`, and the modal was not deleted to get
     * here.** `deleteSession` opens `JOptionPane.showConfirmDialog`, which constructs a `JDialog` and
     * throws `HeadlessException` under `-Djava.awt.headless=true`; every line of the teardown sits
     * below it. Production keeps the confirmation in one function and the teardown in another, so
     * this drives the real teardown while real users still meet the real modal.
     */
    @Test
    fun deletingASessionSupersedesItsRunningToolAndStillAuditsTheCall() {
        val latches = ToolLatches()
        val discharged = CountDownLatch(1)
        val h = ChatPanelTestHarness.create(modelResponse = toolCall("proxy_http_history", """{"count":5}"""))
        whenever(h.api.proxy().history()).thenAnswer {
            latches.entered.countDown()
            check(latches.probeRan.await(HANDSHAKE_FAILSAFE_SECONDS, TimeUnit.SECONDS)) {
                "The test never released the blocked tool — the delete window was never opened."
            }
            emptyList<ProxyHttpRequestResponse>()
        }

        val traceIdRef = AtomicReference<String>()
        assertTimeoutPreemptively(Duration.ofSeconds(30)) {
            ChatPanelTestHarness.sendUserMessage(h, "summarise the proxy history")
            ChatPanelTestHarness.drainEdt()
            val card = requireNotNull(ChatPanelTestHarness.findApprovalCard(h.panel.root)) { NO_CARD }
            // Substitutes a continuation into the record the REAL gate just parked, so T-22-31's
            // "discharged rather than dropped" clause is assertable on this exit too.
            traceIdRef.set(parkContinuation(h) { _, _ -> discharged.countDown() })
            click(card, "Approve once")
            assertTrue(
                latches.entered.await(HANDSHAKE_FAILSAFE_SECONDS, TimeUnit.SECONDS),
                "The tool worker never started, so there was nothing for the delete to supersede.",
            )

            // The user deletes the session out from under the running call. On the EDT, exactly as the
            // popup-menu action listener would reach it once the modal has been answered.
            SwingUtilities.invokeAndWait { h.panel.deleteConfirmedSession(onlySession(h)) }

            latches.probeRan.countDown()
            ChatPanelTestHarness.awaitToolSettled(count = 1)
        }

        // LOUD CLAUSE 1. On a tree without the explicit supersede this reads "ok": the worker's token
        // still matched, so the tail applied a result belonging to a session that was already gone.
        val record = decisionsFor(traceIdRef.get()).single()
        assertEquals(
            ChatPanel.SUPERSEDED_RUN_STATUS,
            record["status"],
            "D-08: a session delete must supersede the tool worker its chain left running. This exit " +
                "does not route through the in-flight cancel, so it inherits nothing and needs its own take().",
        )
        // LOUD CLAUSE 2, and independent of the first: only the superseded exit merges a
        // supersedeReason onto the pair, so this fails on the un-superseded tree even if some later
        // change made "ok" and "cancelled" agree. E4's ordering claim rides in the same assertion.
        assertOrderedAuditPair(h, record, traceIdRef.get(), extraKeys = setOf(SUPERSEDE_REASON_KEY))
        // CORROBORATION. See the KDoc: sendMessage refuses a followup for a session whose panel is
        // gone, so this clause is satisfied by the defect too. It is the real claim only where the
        // panel survives the supersede.
        verifySendChatCount(h, 1)
        assertTrue(
            discharged.await(HANDSHAKE_FAILSAFE_SECONDS, TimeUnit.SECONDS),
            "T-22-31: the parked continuation must be discharged on the superseded exit, not dropped — " +
                "no followup turn is carrying it any more.",
        )
        // CORROBORATION ONLY. See the KDoc: the deleted panel is detached, not destroyed, so this
        // clause is also satisfied by the defect. It is here to fail alongside the two above, never
        // instead of them.
        assertFalse(
            transcriptText(h).contains("Tool result: proxy_http_history"),
            "UI-SPEC S4: a superseded run renders no transcript row.",
        )
    }

    /**
     * S-06 / D-08 / AI-SPEC E5 — a Burp project change supersedes every running tool worker.
     *
     * Unlike a session delete this exit inherits its supersede: `clearInMemorySessionState` calls the
     * in-flight cancel, which takes the running-tool token. So this test is a REGRESSION guard rather
     * than a red-before-green demonstration, and it is worth having for exactly that reason — the
     * coverage is invisible in the source of `clearInMemorySessionState`, which mentions no worker.
     *
     * The E5 clause is asserted on the map state AFTER the worker has settled: a tail that wrote
     * anything back into the session maps would resurrect a session the project change destroyed, and
     * the user would be looking at a chat belonging to a Burp project they had already left.
     *
     * **Loud clauses: the run status and the `supersedeReason` key.** The turn count is corroboration
     * on this exit for the same measured reason it is on the session-delete one — a project change
     * clears `sessionPanels`, and `sendMessage` refuses a followup when the session's panel is gone,
     * so no second turn appears whether or not the worker was superseded.
     */
    @Test
    fun aProjectChangeSupersedesTheRunningToolAndNoWriteReachesTheDisposedPanel() {
        val latches = ToolLatches()
        val discharged = CountDownLatch(1)
        val h = ChatPanelTestHarness.create(modelResponse = toolCall("proxy_http_history", """{"count":5}"""))
        whenever(h.api.proxy().history()).thenAnswer {
            latches.entered.countDown()
            check(latches.probeRan.await(HANDSHAKE_FAILSAFE_SECONDS, TimeUnit.SECONDS)) {
                "The test never released the blocked tool — the project-change window was never opened."
            }
            emptyList<ProxyHttpRequestResponse>()
        }

        val traceIdRef = AtomicReference<String>()
        assertTimeoutPreemptively(Duration.ofSeconds(30)) {
            ChatPanelTestHarness.sendUserMessage(h, "summarise the proxy history")
            ChatPanelTestHarness.drainEdt()
            val card = requireNotNull(ChatPanelTestHarness.findApprovalCard(h.panel.root)) { NO_CARD }
            traceIdRef.set(parkContinuation(h) { _, _ -> discharged.countDown() })
            click(card, "Approve once")
            assertTrue(
                latches.entered.await(HANDSHAKE_FAILSAFE_SECONDS, TimeUnit.SECONDS),
                "The tool worker never started, so there was nothing for the project change to supersede.",
            )

            // MainTab.onProjectChanged()'s whole body, reached on the EDT.
            SwingUtilities.invokeAndWait { h.panel.clearInMemorySessionState() }
            assertEquals(0, sessionCount(h), "Setup: the project change really did clear every session.")

            latches.probeRan.countDown()
            ChatPanelTestHarness.awaitToolSettled(count = 1)
        }

        val record = decisionsFor(traceIdRef.get()).single()
        assertEquals(
            ChatPanel.SUPERSEDED_RUN_STATUS,
            record["status"],
            "D-08: a project change must supersede the running tool worker.",
        )
        assertOrderedAuditPair(h, record, traceIdRef.get(), extraKeys = setOf(SUPERSEDE_REASON_KEY))
        // Corroboration on THIS exit — see the KDoc. Loud on the unload exit, where the panel lives.
        verifySendChatCount(h, 1)
        assertTrue(
            discharged.await(HANDSHAKE_FAILSAFE_SECONDS, TimeUnit.SECONDS),
            "T-22-31: the parked continuation must be discharged when the project changes under it.",
        )
        // E5, read after the worker has finished its tail: nothing the returning worker did put a
        // session back. Read before the settle this would be a claim about the project change alone.
        assertEquals(
            0,
            sessionCount(h),
            "AI-SPEC E5: no write from the returning worker may reach the disposed panel — a resurrected " +
                "session belongs to the Burp project the user already left.",
        )
    }

    /**
     * S-07 / SC6 / D-08 — extension unload supersedes the worker and does NOT wait for it.
     *
     * **The non-blocking claim is proved as a deadlock, not as a duration.** `shutdown()` is called
     * from a non-EDT thread while the tool double is still blocked inside its call. If unload joined
     * the worker, nothing would ever release the tool's latch — the release below happens only after
     * `shutdown()` returns — and `assertTimeoutPreemptively` reports that categorically. Nothing here
     * is compared against elapsed time; this file contains no wall-clock reading at all.
     *
     * **What this scenario cannot catch, stated rather than implied:** a BOUNDED wait
     * (`future.get(10, SECONDS)`), which would expire and let `shutdown()` return late instead of
     * hanging. That shape is D-08's actual named counter-example, and the guard against it is the
     * structural one in plan 23-04's Task 1 — `shutdown()`'s body contains no `.get(` or `.join(`.
     * Two guards, because one covers the hang and the other covers the timeout.
     *
     * The daemon assertion is the other half of why not waiting is safe: a non-daemon worker would
     * hold the JVM open past unload even with nobody waiting on it.
     */
    @Test
    fun unloadSupersedesTheRunningToolWithoutWaitingForIt() {
        val latches = ToolLatches()
        val discharged = CountDownLatch(1)
        val toolThread = AtomicReference<Thread?>(null)
        val toolReturned = AtomicBoolean(false)
        val h = ChatPanelTestHarness.create(modelResponse = toolCall("proxy_http_history", """{"count":5}"""))
        whenever(h.api.proxy().history()).thenAnswer {
            toolThread.set(Thread.currentThread())
            latches.entered.countDown()
            check(latches.probeRan.await(HANDSHAKE_FAILSAFE_SECONDS, TimeUnit.SECONDS)) {
                "The test never released the blocked tool — shutdown() must have waited for it."
            }
            toolReturned.set(true)
            emptyList<ProxyHttpRequestResponse>()
        }

        val traceIdRef = AtomicReference<String>()
        assertTimeoutPreemptively(Duration.ofSeconds(30)) {
            ChatPanelTestHarness.sendUserMessage(h, "summarise the proxy history")
            ChatPanelTestHarness.drainEdt()
            val card = requireNotNull(ChatPanelTestHarness.findApprovalCard(h.panel.root)) { NO_CARD }
            traceIdRef.set(parkContinuation(h) { _, _ -> discharged.countDown() })
            click(card, "Approve once")
            assertTrue(
                latches.entered.await(HANDSHAKE_FAILSAFE_SECONDS, TimeUnit.SECONDS),
                "The tool worker never started, so there was nothing for unload to supersede.",
            )

            // Burp's unload handler calls this from a Montoya thread, not the EDT — which is the case
            // shutdown()'s own invokeAndWait exists for. Returning from here at all, with the worker
            // demonstrably still inside its call, IS the SC6 claim.
            h.panel.shutdown()
            assertFalse(
                toolReturned.get(),
                "SC6 / D-08: unload returned only after the worker finished, so it waited for it. " +
                    "A tool call has no cancellation token; waiting is how unload becomes a hang.",
            )

            latches.probeRan.countDown()
            ChatPanelTestHarness.awaitToolSettled(count = 1)
        }

        val record = decisionsFor(traceIdRef.get()).single()
        assertEquals(
            ChatPanel.SUPERSEDED_RUN_STATUS,
            record["status"],
            "D-08: unload must supersede the running tool worker, so its result is never applied to a " +
                "panel whose classloader Burp is tearing down.",
        )
        // LOUD ON THIS EXIT, unlike on the session-delete and project-change ones. Unload leaves
        // sessionPanels intact, so sendMessage's own null-panel refusal does not stand in for the
        // supersede here: an un-superseded tail really does dispatch a backend request into a Burp
        // that is tearing the extension classloader down (T-22-33).
        verifySendChatCount(h, 1)
        assertOrderedAuditPair(h, record, traceIdRef.get(), extraKeys = setOf(SUPERSEDE_REASON_KEY))
        assertTrue(
            discharged.await(HANDSHAKE_FAILSAFE_SECONDS, TimeUnit.SECONDS),
            "T-22-31: the parked continuation must be discharged at unload, not dropped.",
        )
        assertTrue(
            requireNotNull(toolThread.get()) { "The tool body never ran." }.isDaemon,
            "E9 / T-23-12: unload does not join the worker, so the daemon flag is what stops a stuck " +
                "one holding the JVM open. Without it, 'we do not wait' becomes 'Burp does not exit'.",
        )
    }

    /**
     * S-09 / CFM 2 / T-23-10 — an approved call that is superseded mid-flight executes EXACTLY ONCE.
     *
     * The supersede is a compare-and-set with exactly one winner: whoever takes the token wins, and
     * the returning worker's `clearIfMatches` loses and discards. The failure this rules out is the
     * one that would matter most on a security tool — the resolution path and the worker both
     * proceeding, so a single human `Approve once` click puts two requests on the wire.
     *
     * **A genuine `CONFIRM_EACH` tool, not a `CONFIRM` one approved once.** The two behave alike for a
     * single call, so substituting would have quietly turned a tier-specific claim into a generic one.
     * `scope_include` is the one CONFIRM_EACH tool that both reaches a stubbable Montoya seam and
     * completes headlessly: `http1_request` / `http2_request` die in `HttpRequest.httpRequest`, which
     * `McpScopeFilter.deriveScopeUrl`'s KDoc records as unavailable in pure-JVM unit tests; every
     * `scan_*` one is Professional-only; `ai_analyze` and `ai_passive_scan` need runtime AI
     * dependencies the chat context does not carry. Reaching it costs the settings override below,
     * which is a real user configuration rather than a test hook.
     *
     * The two-button assertion is a SETUP assertion: it fails if `scope_include` ever stops being
     * CONFIRM_EACH, rather than letting this test silently become the CONFIRM case.
     */
    @Test
    fun aSupersededConfirmEachCallReachesBurpExactlyOnce() {
        val latches = ToolLatches()
        val h =
            ChatPanelTestHarness.create(
                modelResponse = toolCall("scope_include", """{"url":"http://s09.example/"}"""),
                settings = settingsEnabling("scope_include"),
            )
        // Resolved to a local FIRST. `includeInScope` returns void, so the stub has to be written
        // `doAnswer {}.whenever(mock)`, and leaving the deep-stub `h.api.scope()` call inside the
        // `whenever(...)` argument makes Mockito read THAT call as the one being stubbed —
        // UnfinishedStubbingException, measured.
        val scope = h.api.scope()
        doAnswer {
            latches.entered.countDown()
            check(latches.probeRan.await(HANDSHAKE_FAILSAFE_SECONDS, TimeUnit.SECONDS)) {
                "The test never released the blocked tool — the supersede window was never opened."
            }
            null
        }.whenever(scope).includeInScope(any<String>())

        val traceIdRef = AtomicReference<String>()
        assertTimeoutPreemptively(Duration.ofSeconds(30)) {
            ChatPanelTestHarness.sendUserMessage(h, "add that host to scope")
            ChatPanelTestHarness.drainEdt()
            val card = requireNotNull(ChatPanelTestHarness.findApprovalCard(h.panel.root)) { NO_CARD }
            assertEquals(
                listOf("Deny", "Approve once"),
                decisionButtons(card).map { it.text },
                "Setup: this scenario is about CONFIRM_EACH. Two actions is what that tier offers; four " +
                    "would mean the tool was re-tiered and this test now proves something else.",
            )
            traceIdRef.set(pendingTraceId(h))
            click(card, "Approve once")
            assertTrue(
                latches.entered.await(HANDSHAKE_FAILSAFE_SECONDS, TimeUnit.SECONDS),
                "The tool worker never started, so there was nothing to supersede.",
            )

            SwingUtilities.invokeAndWait { cancelButton(h).doClick() }

            latches.probeRan.countDown()
            ChatPanelTestHarness.awaitToolSettled(count = 1)
        }

        // THE CLAIM. One click, one call on the wire — read from the Montoya double the tool body
        // actually reached, not from a counter this test keeps.
        verify(scope, times(1)).includeInScope(any<String>())
        // And the loser of the compare-and-set does not re-enter later: drained well past the point a
        // second dispatch would have landed.
        ChatPanelTestHarness.drainEdt(times = LONG_DRAIN)
        verify(scope, times(1)).includeInScope(any<String>())

        val record = decisionsFor(traceIdRef.get()).single()
        assertEquals(
            ChatPanel.SUPERSEDED_RUN_STATUS,
            record["status"],
            "The superseded call is still audited — it reached Burp and cannot be recalled (D-07).",
        )
        assertEquals("approve_once", record["decision"], "The decision the human really made is unchanged by the supersede.")
        assertOrderedAuditPair(h, record, traceIdRef.get(), extraKeys = setOf(SUPERSEDE_REASON_KEY))
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

/**
 * The extra metadata key `discardSupersededToolResult` merges onto the reporter's map.
 *
 * Named here so [assertOrderedAuditPair] can subtract it before comparing the two records, rather
 * than having each caller restate the string.
 */
private const val SUPERSEDE_REASON_KEY = "supersedeReason"

/**
 * AI-SPEC E4 — the audit PAIR for one call, asserted as the DATA DEPENDENCY it actually is.
 *
 * `toolDecisionReporter.report` emits the `mcp_tool_decision` event AND returns the null-filtered copy
 * of that same payload; `supervisor.aiRequestLogger?.log` then consumes what it returned. So "report
 * ran first" is not a convention to be checked by call order — it is provable from the data, and this
 * checks it there: the AI-activity metadata must BE the decision payload, minus its null values and
 * plus [extraKeys]. A `log` call that assembled its own map, or one made with a stale one, fails this
 * and would pass an ordering check.
 *
 * Both sides are selected by trace id and never by list position: `AuditLogger`'s emitter is
 * process-global and panels built by earlier tests in this class are still alive.
 */
private fun assertOrderedAuditPair(
    h: ChatPanelTestHarness.Harness,
    decision: Map<*, *>,
    traceId: String,
    extraKeys: Set<String>,
) {
    val logged = loggedMetadataFor(h, traceId)
    val reported = decision.entries.filter { it.value != null }.associate { it.key.toString() to it.value.toString() }
    assertEquals(
        reported,
        logged - extraKeys,
        "E4: the AI-activity record must be exactly what the decision reporter returned. A record that " +
            "merely LOOKS right but was built separately would drift from the audit event the first time " +
            "either shape changed, and the two sinks would then disagree about the same call.",
    )
    extraKeys.forEach { key ->
        assertTrue(
            logged.containsKey(key),
            "The '$key' key must ride along on the AI-activity record; the pair carried ${logged.keys}.",
        )
    }
}

/**
 * The metadata map handed to `AiRequestLogger.log` for [traceId].
 *
 * `log` takes ten parameters and every one is matched positionally, so the captor lines up with the
 * last. `singleOrNull` is itself an assertion: exactly one AI-activity record per call is the other
 * half of "exactly one audit pair per exit".
 */
private fun loggedMetadataFor(
    h: ChatPanelTestHarness.Harness,
    traceId: String,
): Map<String, String> {
    val metadata = argumentCaptor<Map<String, String>>()
    verify(requireNotNull(h.supervisor.aiRequestLogger) { "No AiRequestLogger on the supervisor double." }, atLeast(1)).log(
        any(),
        any(),
        any(),
        any(),
        anyOrNull(),
        anyOrNull(),
        anyOrNull(),
        anyOrNull(),
        anyOrNull(),
        metadata.capture(),
    )
    return requireNotNull(metadata.allValues.singleOrNull { it["traceId"] == traceId }) {
        "Expected exactly one AI-activity record for trace $traceId; captured ${metadata.allValues.map { it["traceId"] }}."
    }
}

/**
 * Substitutes [onCompleted] into the LIVE pending record the production path just parked.
 *
 * Ported verbatim in intent from `ChatPanelToolGateTest`, and narrow for the same reason: the only
 * producer of a non-null `onCompleted` is `MainTab.openChatWithContext`, whose only route in blocks on
 * an application-modal `ContextPreviewDialog` that cannot be driven under `-Djava.awt.headless=true`.
 * So the record is built by the real gate on the real Send path and ONLY its callback slot is swapped.
 *
 * Runs on the EDT because `pendingDecisions` is EDT-confined (REL-01).
 *
 * @return the parked chain's trace id — the only field that identifies this chain's audit events
 *   among those of every other still-live panel the global emitter also sees.
 */
private fun parkContinuation(
    h: ChatPanelTestHarness.Harness,
    onCompleted: (String, Throwable?) -> Unit,
): String {
    lateinit var traceId: String
    SwingUtilities.invokeAndWait {
        val field = ChatPanel::class.java.getDeclaredField("pendingDecisions")
        field.isAccessible = true

        @Suppress("UNCHECKED_CAST")
        val pending = field.get(h.panel) as MutableMap<String, Any>
        val sessionId =
            requireNotNull(pending.keys.singleOrNull()) {
                "Expected exactly one parked decision to substitute a continuation into; found ${pending.keys}."
            }
        val record = pending.getValue(sessionId)
        val type = record.javaClass
        val constructor =
            requireNotNull(type.declaredConstructors.singleOrNull()) {
                "PendingToolDecision no longer has exactly one constructor — this fixture is stale."
            }
        constructor.isAccessible = true

        fun slot(name: String): Any? = type.getDeclaredField(name).also { it.isAccessible = true }.get(record)

        traceId = slot("traceId") as String
        pending[sessionId] =
            constructor.newInstance(
                slot("sessionId"),
                slot("userText"),
                slot("call"),
                slot("context"),
                slot("remainingToolIterations"),
                traceId,
                onCompleted,
                slot("card"),
            )
    }
    return traceId
}

/**
 * Every session the panel currently holds, read off the production sessions-list model.
 *
 * The list is a SIBLING of the transcript — `MainTab` is handed it separately — so it is not under
 * `ChatPanel.root` and has to be searched from `sessionsComponent()`.
 */
@Suppress("UNCHECKED_CAST")
private fun sessions(h: ChatPanelTestHarness.Harness): List<ChatPanel.ChatSession> {
    val list =
        requireNotNull(ChatPanelTestHarness.find(h.panel.sessionsComponent(), JList::class.java)) {
            "No JList under ChatPanel.sessionsComponent() — the sessions-list lookup is stale."
        } as JList<Any?>
    return (0 until list.model.size).map { list.model.getElementAt(it) as ChatPanel.ChatSession }
}

private fun sessionCount(h: ChatPanelTestHarness.Harness): Int = sessions(h).size

/** The single session a freshly built harness holds — asserted to be single, never assumed. */
private fun onlySession(h: ChatPanelTestHarness.Harness): ChatPanel.ChatSession =
    requireNotNull(sessions(h).singleOrNull()) {
        "Expected exactly one session on the panel; found ${sessions(h).map { it.title }}."
    }

/**
 * Baseline settings with [toolIds] switched on, unsafe mode included.
 *
 * A real user configuration rather than a test hook: every `CONFIRM_EACH` tool that both completes
 * headlessly and touches a stubbable Montoya seam is `unsafeOnly` and off by default, so reaching that
 * tier at all requires the same two toggles a user would set in the MCP settings tab.
 */
private fun settingsEnabling(vararg toolIds: String): AgentSettings {
    val base = TestSettings.baselineSettings()
    return base.copy(
        mcpSettings =
            base.mcpSettings.copy(
                unsafeEnabled = true,
                toolToggles = toolIds.associateWith { true },
            ),
    )
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
