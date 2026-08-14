package com.six2dez.burp.aiagent.ui

import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.kotlin.verify

/**
 * SEC-06 / SC4 — the agent tool-call trust boundary, asserted against the real production path.
 *
 * **This file drives a REAL [ChatPanel] through its REAL Send button.** It is not a model of the
 * chat flow: [ChatPanelTestHarness] constructs the production ten-parameter panel from deep-stub
 * mocks and clicks the same button a user clicks in Burp. Nothing here simulates
 * `maybeExecuteToolCall` — it is executed.
 *
 * **The single test below asserts PRE-FIX behaviour and is GREEN today.** `maybeExecuteToolCall`
 * (`ChatPanel.kt:2152`) hands the parsed tool call straight to `McpToolExecutor.executeTool` with no
 * gate of any kind, so a model-emitted `scope_check` reaches `api.scope().isInScope(...)` without the
 * user ever deciding anything. That is the defect, executed rather than argued.
 *
 * **Plan 22-07 turns this file into the SC4 acceptance gate, and it does NOT invert this test.**
 * `scope_check` is `SecTier.AUTO` in 22-02's locked tier table, so it still runs silently by design
 * once the gate ships. 22-07 therefore *keeps* the assertion below exactly as it stands, as the
 * `AUTO`-tier companion proving an `AUTO` tool still runs with no approval card, and *adds* two new
 * assertions on a `CONFIRM`-tier tool, `proxy_http_history`:
 *
 * - `verify(api.proxy(), never()).history()` — the Burp call that tool makes must not happen; and
 * - `assertNotNull(findApprovalCard(panel.root))` — an approval card must be shown instead.
 *
 * Those two fail against the pre-gate commit and pass after it, which is what makes the gate
 * non-vacuous under the Phase 20 SC4 / Phase 21 rule: *a test that passes both before and after the
 * change has not tested the defect.* The assertion in this file passes both before and after on
 * purpose — it is the companion, never the gate.
 *
 * **Naming constraint (hard).** The class must stay `ChatPanelToolGateTest`. Renaming it to
 * `*IntegrationTest` (or `*ConcurrencyTest`, `*BackpressureTest`, `*RestartPolicyTest`,
 * `*SupervisionTest`) would make `-PexcludeHeavyTests=true` skip it — silently disabling the SC4
 * gate on exactly the PR gate where it matters most.
 */
class ChatPanelToolGateTest {
    @Test
    fun modelEmittedToolCall_reachesBurpWithNoUserDecision_preFixBaseline() {
        // The exact fenced payload research measured reaching Burp. ToolCallParser.extractFirst
        // resolves the "tool" and "args" keys directly (ToolCallParser.kt:80-108).
        val modelResponse =
            """
            ```json
            {"tool":"scope_check","args":{"url":"http://evil.example/"}}
            ```
            """.trimIndent()

        val harness = ChatPanelTestHarness.create(modelResponse = modelResponse)

        ChatPanelTestHarness.sendUserMessage(harness, "check scope please")
        ChatPanelTestHarness.drainEdt()

        // SEC-06 / SC4 — this line is the defect. It is GREEN today because maybeExecuteToolCall
        // (ChatPanel.kt:2152) calls McpToolExecutor.executeTool directly with no gate at all, and the
        // scope_check branch (McpToolExecutorImpl.kt:877) calls api.scope().isInScope(url).
        // After plan 22-07 this exact line still passes, because scope_check is SecTier.AUTO and runs
        // silently by design; 22-07 keeps it as the AUTO companion and puts the never() acceptance
        // assertion on the CONFIRM-tier proxy_http_history instead. Do not invert this line.
        verify(harness.api.scope(), Mockito.atLeastOnce()).isInScope("http://evil.example/")
    }
}
