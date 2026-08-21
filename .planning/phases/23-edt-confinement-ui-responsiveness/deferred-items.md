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

## D-23-06-1 — `MainTab.kt:111` is an EIGHTH EDT `settingsRepo.save()` site, outside the persist queue

**Found during:** plan 23-06, Task 2, while routing the seven EDT save sites `23-VERIFICATION.md`
enumerated (`:168`, `:453`, `:467`, `:475`, `:487`, `:502`, `:511`) through `SettingsPersistQueue`.

**Measurement.** `MainTab.kt:111` (pre-plan line numbering) sits in the `applySettings` lambda passed
to `ChatPanel`'s constructor. It is invoked from `ChatPanel.sendMessage:590` **on the EDT for every
chat message send**, and its body does a disk write (`settingsRepo.save(settings)`) plus
`supervisor.applySettings(settings)` plus `mcpSupervisor.applySettings(...)` at `MainTab.kt:115`. The
verifier's enumeration of seven sites missed it; the count is eight.

**Why it was not fixed here.** The send path depends on `supervisor.applySettings(settings)` having
completed before the turn is sent, so the lambda cannot be moved wholesale onto the queue without
splitting it into a part the send waits on and a part it does not. That split is analysis this
gap-closure run's stated scope does not cover, and the user's scope decision explicitly defers it.

**Hazard — both consequences, because an incomplete residual is how a known issue becomes an
invisible one.**

- **(a) A torn-write window this plan does not close.** `:111` calls `settingsRepo.save` on the EDT
  *outside* the queue's `ReentrantLock`, so a chat send racing a header toggle can still interleave
  with a queue worker. `AgentSettingsRepository.save()` writes ~107 preference keys one at a time with
  `KEY_PRIVACY_MODE` (`AgentSettings.kt:603`) and `KEY_CUSTOM_REDACTION_PATTERNS` (`:703`) a hundred
  keys apart, so the interleave can persist a permissive `privacyMode` beside a foreign
  `customRedactionPatterns` list. This is why plan 23-06's CR-02 `must_haves` truth is scoped to
  queue-submitted writes rather than stated as a property of the persisted file. Tracked as threat
  `T-23-06-08`, rated **high / accept**.
- **(b) A narrow but real SC4 residual.** Unchecking MCP is now asynchronous, so its worker can be
  inside a bounded `stop()` of up to ten seconds when the user immediately sends a chat message.
  `ChatPanel.sendMessage:590` then drives `MainTab.kt:115`'s `mcpSupervisor.applySettings(...)` — with
  MCP disabled, into `McpSupervisor.stop()` — **on the EDT**, into the same bounded wait. Bound stated
  honestly: `:111` passes CURRENT settings, so it cannot itself originate an enabled→disabled
  transition; it can only re-enter a `stop()` for a transition another site already made. That is what
  makes it narrow rather than a re-opening of SC4.

**Severity.** High for limb (a) — identical consequence to `T-23-06-02`, which is what makes that
threat `high`. Reachability is bounded to a chat send racing a header toggle.

**Suggested home.** A follow-up plan that can split the `ChatPanel` `applySettings` lambda into the
supervisor half the send path must await and the persist half that can be submitted to
`SettingsPersistQueue`. The guard would be a fifth count in
`SettingsPersistQueueTest.everyMainTabSettingsWriteGoesThroughThePersistQueue`, moving
`settingsRepo.save(` from 3 to 2 and `mcpSupervisor.applySettings(` from 2 to 1.

## D-23-07-1 — CR-03 / D-23-04-1 remains OPEN and was NOT made a freebie by plan 23-07

**Found during:** plan 23-07, while closing CR-05 (`openToolDialog` refuses re-entry while a tool
worker is running). Transcribed here by plan 23-08, the single owner of this ledger in wave 2, so that
two wave-1 plans do not write the same file.

**Observation.** `ChatPanel.clearChatState()` still does not supersede a running tool worker. This is
the same defect `D-23-04-1` above records, restated after wave 1 because a reader could reasonably
have assumed CR-05 closed it. It did not: CR-05's fix guards `openToolDialog`'s **entry**, which is a
different path from `clearChatState`'s **teardown**. The two share no code, and the entry guard has no
effect at all on a worker that is already running when Clear Chat is pressed.

**Why it was not fixed in 23-07.** Closing CR-05 does not reduce it to a one-line addition, and the
plan's scope was the busy seam's two doors, not the fifth teardown path. The open UI question is
unchanged and must be answered before anyone writes the line, quoted verbatim from 23-07-SUMMARY.md:

> *"whether Clear Chat should return the panel to S0 while a worker is still running, or leave the busy
> state alone as `discardSupersededToolResult` does."*

**Severity.** Low-to-moderate, unchanged from `D-23-04-1`. No security control is bypassed — the call
was already approved and is already audited — but the transcript can show a result for a conversation
the user cleared, and the followup turn spends a chain iteration against a reset approval memory.

**Suggested home.** A follow-up plan or a `/gsd-quick` task that can also decide the UI question; the
guard would be one more scenario in `ChatPanelEdtConfinementTest` alongside S-05 through S-07.
