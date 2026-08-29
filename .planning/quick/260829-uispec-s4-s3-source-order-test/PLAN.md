---
quick_id: 260829-18o
slug: uispec-s4-s3-source-order-test
type: quick
created: 2026-08-29
closes: WINDOWS.md entry 2
files_modified:
  - src/test/kotlin/com/six2dez/burp/aiagent/ui/ChatPanelEdtConfinementTest.kt
---

# Commit a test for UI-SPEC Rules S-4 and S-3 on both user-originated tool paths

## Why

`WINDOWS.md` entry 2 (phase 23, `unrun-verify`), the last genuinely-unverified item in the v0.10.0
milestone:

> No committed test asserts the UI-SPEC Rule S-4 `/tool` transcript echo or the S3 busy-state entry on
> either user-originated tool path; both verified only by execution-time source-order greps (23-02 D7).

**Both rules are already implemented.** This task does not change behaviour — it replaces an ad-hoc grep
that ran once, during execution, with an assertion that runs on every build.

## The two rules, from `23-UI-SPEC.md`

**Rule S-4 (`:202`)** — the `/tool` path must echo its command in the existing `"You"` channel *before*
dispatch, as the dialog path already does, so the transcript never shows a result with no visible request.

**Rule S-3 (`:187`)** — `setSendingState(true)` must be the first statement inside the same block that
dispatches the worker, so no idle flash appears between a step and its tool.

## Measured at HEAD `99f9f7e` — pin these, do not assume them

`ChatPanel.kt`:

| Fact | Value |
|---|---|
| Dialog path (`ToolCallOrigin.UserDialog`) echo | `:1165` `panel.addMessage("You", commandPreview)`, model add at `:1166`, `setSendingState(true)` at `:1172` |
| Slash path (`ToolCallOrigin.UserSlashCommand`) echo | `:2599` `panel.addMessage("You", trimmed)`, model add at `:2600`, `setSendingState(true)` at `:2602`, dispatch `OffEdtDispatch.run` at `:2605` |
| `commandPreview` occurrences | **3** (`:1158`, `:1165`, `:1166`) |
| `setSendingState(true)` occurrences | **4** (`:605`, `:1172`, `:2602`, `:3172`) |
| Rule S-4 cited in source | KDoc at `:2593-2598` names the rule and why |

Re-measure each before writing; if any has moved, pin the new value and say so.

## Where the test goes

`src/test/kotlin/com/six2dez/burp/aiagent/ui/ChatPanelEdtConfinementTest.kt` — **not a new file.** It
already owns phase 23's source-order assertions and already has the helpers:

- `chatPanelSource()` — reads `ChatPanel.kt`
- `functionBody("private fun …")` — extracts one function body
- `occurrencesOf(needle, source)` — counts

`ChatPanel.kt` is already a declared `tasks.test` input in `build.gradle.kts`, so a source-text edit
re-runs these without a new declaration (the 22-09 stale-cache defect). Adding a read of any *other* main
file would need one — so do not read another main file.

## Tasks

### Task 1 — assert Rule S-4 on both user-originated paths

Add tests asserting that on **each** user-originated path the `"You"` echo occurs **before** the async
dispatch, by source order within the enclosing function body:

- Slash path: inside `handleToolCommand`'s `/tool` branch, `panel.addMessage("You", …)` appears at a lower
  index than `OffEdtDispatch.run`.
- Dialog path: the same relation in its own function.

Assert by **index comparison within the extracted function body**, not by raw line numbers — line numbers
rot, and this repo has a recorded window (entry 34) for exactly that.

Include a negative control: the assertion must be able to go red. State in the KDoc which edit would
break it (moving the echo after the dispatch), and prefer a form where that is obvious.

### Task 2 — assert Rule S-3's busy-state entry on both paths

Assert `setSendingState(true)` precedes the dispatch in the same body on both paths, and pin the
whole-file `setSendingState(true)` count at its measured value with a KDoc ledger saying what each site
is — the pattern `CHAT_PANEL_INVOKE_LATER_SITES` already uses in this file. A bare count is not evidence;
a count with a per-site reason is.

### Task 3 — run and close out

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test --tests '*ChatPanelEdtConfinement*' ktlintCheck detekt --console=plain
```

Then write `SUMMARY.md` in this directory. Record the measured baselines and the new test count.

## Must-haves

- Committed tests assert **both** rules on **both** user-originated paths — four relations total
- Assertions are by source order **within an extracted function body**, never by absolute line number
- Each new assertion's KDoc names the edit that would turn it red
- No change to any file under `src/main/` — this task adds tests only
- `ChatPanelEdtConfinementTest` stays green; the wider suite is unaffected
- `.planning/REQUIREMENTS.md` untouched: sha256 `9b3219662ec0d007c1c82d64eed3ef2698bd306ce69f01205ac9bbc3f42fcfb4`

## Prohibitions

- **MUST NOT modify `src/main/`.** Both rules already hold; if a test goes red, the test is wrong, not
  `ChatPanel.kt`. Report it rather than "fixing" the source.
- **MUST NOT create a new test file.** The helpers and the phase-23 source-order contract live in
  `ChatPanelEdtConfinementTest.kt`.
- **MUST NOT read a main-source file other than `ChatPanel.kt`** in a test — that would need a new
  `tasks.test` input declaration and silently stale-cache without one.
- **MUST NOT assert on absolute line numbers.**
