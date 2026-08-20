# Phase 23 — Deferred Items

Out-of-scope discoveries logged during execution. Not fixed here (executor scope boundary).

## D-23-04-1 — Clear Chat (teardown path 3 of 5) does not supersede a running tool worker

**Found during:** plan 23-04, Task 1, while enumerating the four teardown exits D-08 names.

**Observation.** `ChatPanel.clearChatState()` (`ChatPanel.kt`, "Teardown path 3 of 5") resolves the
pending decision with `ImplicitDenyReason.CHAT_CLEARED` and calls `panel.clearMessages()`, but never
takes the running-tool token and never routes through `cancelInFlightRequest`. A tool worker started
before the clear therefore returns into the freshly cleared transcript: it renders a `Tool result:`
row for a chain the user just declared over, and sends a followup turn continuing that chain —
carrying `state.approvalMemory` that `clearChatState` has just reset for the T-22-34 reason.

**Why it was not fixed in 23-04.** The plan scopes itself to the four exits D-08 enumerates — cancel,
session delete, project change and unload — and `must_haves.truths` names exactly those four. Clear
Chat is the fifth teardown path and the one D-08 does not list. Fixing it is a one-line change of the
same shape as the session-delete supersede, but it is new scope and belongs in a plan that can also
decide the UI question it raises: whether Clear Chat should return the panel to S0 while a worker is
still running, or leave the busy state alone as `discardSupersededToolResult` does.

**Severity.** Low-to-moderate. No security control is bypassed — the call was already approved and is
already audited — but the transcript can show a result for a conversation the user cleared, and the
followup turn spends a chain iteration against a reset approval memory.

**Suggested home.** A follow-up plan or a `/gsd-quick` task; the guard would be one more scenario in
`ChatPanelEdtConfinementTest` alongside S-05 through S-07.
