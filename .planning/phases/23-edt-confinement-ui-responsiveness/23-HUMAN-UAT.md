---
status: complete
phase: 23-edt-confinement-ui-responsiveness
source: [23-VALIDATION.md, 23-01-SUMMARY.md, 23-02-SUMMARY.md, 23-03-SUMMARY.md, 23-04-SUMMARY.md, 23-06-SUMMARY.md, 23-08-SUMMARY.md, 23-VERIFICATION.md]
started: 2026-08-20
updated: 2026-08-21
---

## Current Test

[testing complete]

## Tests

### 1. D-12 — the save-failure modal appears alongside the inline banner

Build the JAR (`JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew shadowJar`), load
`Custom-AI-Agent-*.jar` in Burp, open the AI Agent tab and go to Settings. Force a settings save to
fail and click **Save**. The cheapest reliable forcing function is to make the write itself fail —
point the extension at a state it cannot persist, or set a backend configuration
`applyAndSaveSettingsBody` will throw on (an unreachable Ollama/LM Studio base URL that
`backends.reload()` rejects is usually enough). If neither reproduces, temporarily revoke write
permission on Burp's project file.

expected: Three things.

1. **Both surfaces appear** — the inline banner in the Settings tab *and* the `JOptionPane` modal.
   D-12 deliberately keeps both; neither was deleted to make the other testable.
2. **The banner text matches the modal text.** Two different sentences for one failure is the defect
   this item exists to catch — the user reads one, dismisses the other, and leaves with a different
   account of what went wrong.
3. **The Settings tab is usable afterwards.** Save and Restore defaults both come back live. A
   permanently unsaveable Settings tab is FLAG-23-06, and the two-layer `finally` behind the busy
   seam is what should prevent it (asserted headlessly by
   `SettingsSaveAsyncTest.theBusySeamLowersOnBothFailureShapes`, so a failure here means the seam was
   bypassed rather than that the seam is wrong).

why_human: Not headlessly testable, and this was measured rather than assumed —
`JOptionPane.getRootFrame()` throws `HeadlessException` under `-Djava.awt.headless=true`, which is the
flag `tasks.test` sets. It is recorded in `CONCERNS.md` §"UI layer has no integration tests". Plan
23-03 asserts the banner and the completion-callback ordering; the modal itself has no automated seam
at all. Ledger entry: `WINDOWS.md` #3.

result: pass

### 2. FLAG-23-01 — does the disabled Save button read as disabled?

With the JAR loaded, trigger a **slow** save so the busy state is observable for more than a frame:
enable the MCP server first, so `applyAndSaveSettingsBody` pays `KtorMcpServerManager.stop()`'s
bounded ten-second wait. Click **Save** and watch the Save button. Repeat in a light theme and in a
dark theme (Burp: *User options > Display > User interface*).

expected: The Save button reads as visibly inert for the whole flight — not merely greyed text on an
otherwise identical orange field. Plan 23-03 implemented UI-SPEC Rule T-2's recolor
(`outlineVariant` background, `onSurfaceVariant` foreground while busy, `primary`/`onPrimary`
restored afterwards), so what you should see is a button that has changed colour, not only lost its
label contrast. **Restore defaults must be inert too** — D-13 puts both callers on one async path, so
leaving Restore live would re-open the double-save race through the other door.

**Record the outcome either way, including the negative.** If plain `isEnabled = false` already read
correctly on Burp's Look-and-Feel, the recolor may be dropped — but that has to be a recorded choice
rather than a default reached by omission. `isEnabled = false` itself stays mandatory whatever you
decide: it is what closes the double-save race, and the recolor is only about legibility.

why_human: `saveButton.isOpaque = true` with an explicit `primary` background
(`BottomTabsPanel.kt:61-64`) means Swing's own disabled rendering has very little to work with. A
headless `JButton` never paints, so no assertion can observe the result — only the property values,
which plan 23-03 already asserts. Whether the painted result reads as inert to someone glancing at it
is the judgement. Ledger entry: `WINDOWS.md` #4.

result: pass

### 3. FLAG-23-04 — sub-frame Send/Cancel flicker on the auto-approved chain path

Enable tools mode in a chat session, configure a set of `AUTO`-tier tools (the read-only ones —
`proxy_http_history` and friends), and send a prompt that provokes a long tool chain; the phase's own
budget is eight iterations, so ask for something that needs repeated lookups. Watch the Send button
region between chain steps.

expected: **This is an observation item, not a pass/fail gate.** It is the phase's one accepted
unresolved residual, recorded as such in `23-01-PLAN.md` §planner_assumptions and again in
`23-01-SUMMARY.md` §"Accepted Residual". Note whether you see a Send-button flash between steps and
how objectionable it is. Two secondary observations worth capturing in the same run:

- the **tool-cancel transcript line** (press Cancel while a tool is running) is around 160 characters
  — confirm it *wraps* rather than clipping. It is Rule C-1's honest line, and it says the request was
  already sent and will finish; if it clips, the honest half is the half that disappears.
- the line must **not** read `"Request cancelled."` for a tool call that was already dispatched.

The flicker itself is not being fixed in this phase: `setSendingState(true)` is already the first
statement of the same `invokeLater` block that dispatches the worker (UI-SPEC Rule S-3), so what is
left is a repaint landing between two back-to-back EDT events. Closing it would restructure
`maybeExecuteToolCall`, in a file this phase is explicitly chartered not to restructure.

why_human: A repaint between two `invokeLater` blocks is sub-frame; there is no deterministic
assertion for it. The transcript wrap is a rendering property of a `JEditorPane` that wraps at 75% of
viewport width and never paints headlessly. Ledger entry: `WINDOWS.md` #1.

result: pass

### 4. A1 — does Burp really throw on an EDT `sendRequest`?

Check out a pre-fix tree (any commit before `9347c14`, the plan 23-01 tracer), build it, load it, and
call `http1_request` from chat — the `/tool` slash command is the simplest route. Record the actual
exception text Burp produces, verbatim, from the Extensions error log.

expected: An exception whose text is, or closely matches, *"Extensions should not make HTTP requests
in the Swing event dispatch thread"*. Record whatever you actually get, including "no exception, it
just froze for N seconds" — that outcome is also informative and is the alternative this item exists
to distinguish.

**Nothing in the automated suite depends on the answer.** Scenario S-02
(`ChatPanelEdtConfinementTest.aToolThatRefusesTheEdtCompletesNormally`) configures the Montoya
*double* to refuse the EDT, so it pins *"we do not call Burp's HTTP API from the EDT"* against a
refusal this repo controls, regardless of what a live Burp does. What is at stake is only the framing
of SC2: if Burp throws, the pre-fix behaviour was a **defect** — a tool call that could not succeed at
all; if it merely blocks, it was a **latency** — a frozen UI for the duration. Both justify the phase;
they justify it differently, and the ADR wording should say which.

why_human: Montoya runtime behaviour against a live Burp. Research assumption A1 is corroborated by
this repo's own #80 `MontoyaHttpTransport` offload workaround and by third-party reports, but it has
never been confirmed here. Note also that `http1_request` is **not** headlessly drivable at all —
measured during plan 23-01, its body reaches `HttpRequest.httpRequest`, a static factory unavailable
in pure-JVM unit tests — so there is no automated route to this answer even in principle.

result: pass

### 5. SC4 — the MCP enabled→disabled transition no longer freezes the UI, and still actually stops the server

> **Read this first.** An earlier draft of this item (carried in the superseded `23-VERIFICATION.md`)
> told you to *"expect up to 10 seconds of frozen UI"*. That was written while SC4 was an OPEN gap.
> Plans 23-06 and 23-08 closed it, so **the expected observation is now inverted: a freeze is a
> FAILURE.** Do not run the old version.

Build the JAR (`JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew shadowJar`), load
`Custom-AI-Agent-*.jar` in Burp, enable the MCP server and confirm it reaches **Running**. Then
exercise all three doors, one at a time, re-enabling the server between each:

1. In the Settings tab's MCP section, untick **Enable MCP server** — *without* pressing Save.
2. Flip the header **MCP** toggle off.
3. Press **Restore defaults** and confirm the dialog (`defaultMcpSettings().enabled` is `false`, so
   this is also an enabled→disabled transition).

For each, keep the mouse moving over the Burp UI during the transition and watch the tab repaint.
Afterwards check the server really stopped: the status label reads **Stopped**, and
`lsof -nP -iTCP:<port> -sTCP:LISTEN` returns nothing for the configured MCP port.

expected: Two things, and both must hold.

1. **No freeze.** The UI keeps repainting and stays interactive throughout. The bounded
   `future.get(10, TimeUnit.SECONDS)` inside `KtorMcpServerManager.stop()` now runs on
   `burp-ai-settings-sync` (doors 1 and 2) or `burp-ai-settings-save` (door 3), never on the EDT. A
   stall of any length is an SC4 regression.
2. **The server actually stops.** A responsive UI with a listener still bound is the *other* failure
   mode — review 2's CR-03: `SettingsPersistQueue.applyIfCurrent` drops the whole superseded lambda,
   and `persistSettings` (`{ save }`) superseding `persistSettingsAndApplyMcp` (`{ save; applySettings }`)
   would persist "disabled" everywhere in the UI while leaving the socket up. To probe for it
   deliberately, flip the MCP toggle off and then immediately flip a passive/active toggle, and
   re-check `lsof`.

why_human: Requires a live Burp with a running Ktor MCP server to pay the real bounded wait against a
real socket. The headless suite proves the two halves it can reach —
`SettingsSaveAsyncTest.restoreDefaultsDoesNotFireTheHostNotificationsOnTheEdt` (the callback does not
fire on the EDT) and
`SettingsPersistQueueTest.theSubmittingThreadReturnsWhileTheApplyIsStillBlocked` (submit returns while
the apply is still blocked), both confirmed discriminating by red probe during verification — but it
cannot observe an actual repaint, and it has no socket to bind.

result: pass

## Summary

total: 5
passed: 5
issues: 0
pending: 0
skipped: 0
blocked: 0

## Gaps

Item 3 (FLAG-23-04) is an **observation**, not a gate. Recording "the flicker is present and is not
objectionable" closes it; a fix is out of scope for Phase 23 by construction.
