---
quick_id: 260829-18o
slug: uispec-s4-s3-source-order-test
type: quick
closes: WINDOWS.md entry 2
subsystem: testing
tags: [kotlin, junit5, swing, edt, source-order-assertion, ui-spec]

requires:
  - phase: 23-edt-confinement-ui-responsiveness
    provides: "UI-SPEC Rules S-3 / S-4 and their implementation on both user-originated tool paths"
provides:
  - "Committed source-order assertions for UI-SPEC Rule S-4 on both user-originated tool paths"
  - "Committed source-order assertions for UI-SPEC Rule S-3 busy-state entry on both paths"
  - "A whole-file busy-state entry counter (4) with a per-site justification ledger"
  - "assertPrecedesWithin — a reusable, non-vacuous body-relative ordering helper"
affects: [phase-23 verification, WINDOWS.md entry 2, future ChatPanel send paths]

actuals:
  tokens: 3300
  tasks: 3
  commits: 2

tech-stack:
  added: []
  patterns:
    - "Body-relative source-order assertion (index within a brace-matched function body, never a line number)"
    - "Exactly-once precondition guarding an ordering assertion against indexOf returning -1"
    - "Counter plus per-site KDoc ledger (the CHAT_PANEL_INVOKE_LATER_SITES pattern, extended to busy-state entries)"

key-files:
  created: []
  modified:
    - src/test/kotlin/com/six2dez/burp/aiagent/ui/ChatPanelEdtConfinementTest.kt

key-decisions:
  - "Ledger sites named by function, not by line number — WINDOWS.md entry 34 records line citations in this repo rotting twice"
  - "Ordering assertions require exactly one occurrence of each needle, so a deleted call fails instead of passing on indexOf == -1"
  - "Red-ness demonstrated by a scratch mutation of ChatPanel.kt, reverted via git checkout and never committed"

patterns-established:
  - "assertPrecedesWithin(declaration, earlier, later, rule, why): the sanctioned form for 'A must precede B inside one function' in this suite"

requirements-completed: []

coverage:
  - id: D1
    description: "UI-SPEC Rule S-4 (echo before async dispatch) asserted on the /tool slash path"
    verification:
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/ui/ChatPanelEdtConfinementTest.kt#theSlashToolPathEchoesTheTypedCommandBeforeItGoesAsync"
        status: pass
    human_judgment: false
  - id: D2
    description: "UI-SPEC Rule S-4 asserted on the tool-dialog path"
    verification:
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/ui/ChatPanelEdtConfinementTest.kt#theDialogToolPathEchoesTheCommandPreviewBeforeItGoesAsync"
        status: pass
    human_judgment: false
  - id: D3
    description: "UI-SPEC Rule S-3 (busy-state entry before async dispatch) asserted on the /tool slash path"
    verification:
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/ui/ChatPanelEdtConfinementTest.kt#theSlashToolPathEntersTheBusyStateBeforeItGoesAsync"
        status: pass
    human_judgment: false
  - id: D4
    description: "UI-SPEC Rule S-3 asserted on the tool-dialog path"
    verification:
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/ui/ChatPanelEdtConfinementTest.kt#theDialogToolPathEntersTheBusyStateBeforeItGoesAsync"
        status: pass
    human_judgment: false
  - id: D5
    description: "Whole-file busy-state entry count pinned at 4 with a per-site ledger, so a third async send path added without one is caught"
    verification:
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/ui/ChatPanelEdtConfinementTest.kt#everyBusyStateEntryInChatPanelIsAccountedFor"
        status: pass
    human_judgment: false
  - id: D6
    description: "The four ordering assertions are non-vacuous — each can go red"
    verification:
      - kind: other
        ref: "scratch mutation of ChatPanel.kt (echo + setSendingState moved below OffEdtDispatch.run in handleToolCommand) produced 2 failures; reverted with git checkout"
        status: pass
    human_judgment: false

duration: 18 min
completed: 2026-08-29
status: complete
---

# Quick 260829-18o: UI-SPEC S-4 / S-3 source-order test Summary

**Five committed assertions replace the run-once execution-time greps behind WINDOWS.md entry 2: the `"You"` echo and the busy-state entry are now proven to precede the async dispatch on both user-originated tool paths, by index within an extracted function body.**

## Performance

- **Duration:** 18 min
- **Started:** 2026-08-29T00:40:00Z (approx.)
- **Completed:** 2026-08-29T00:58:50Z
- **Tasks:** 3
- **Files modified:** 1

## Accomplishments

- **Four ordering relations, the full matrix the plan asked for.** Rule S-4 (echo before dispatch) and Rule S-3 (busy state before dispatch), each on both `handleToolCommand`'s `/tool` branch and `openToolDialog`.
- **A fifth assertion the plan asked for but the four relations could not give:** the whole-file `setSendingState(true)` count, pinned at 4 with a ledger naming what each site is for. The four ordering tests only see the two functions they name — a third asynchronous send path added with no busy-state entry would be invisible to them. This is the assertion that notices.
- **Non-vacuity demonstrated, not asserted.** A scratch mutation moving both slash-path calls below `OffEdtDispatch.run(` turned exactly the two slash tests red, with the intended messages (verbatim below). Reverted with `git checkout HEAD --`; `src/main/` is byte-identical to the base commit.

## Measured baselines (re-measured at base `97e3a7e`, all unmoved from the plan's figures)

| Fact | Value |
|---|---|
| Dialog echo | `ChatPanel.kt:1165` `panel.addMessage("You", commandPreview)`; model add `:1166`; `setSendingState(true)` `:1172`; dispatch `:1176` |
| Slash echo | `:2599` `panel.addMessage("You", trimmed)`; model add `:2600`; `setSendingState(true)` `:2602`; dispatch `:2605` |
| `commandPreview` occurrences | 3 (`:1158`, `:1165`, `:1166`) |
| `setSendingState(true)` occurrences | 4 (`:605` `sendMessage`, `:1172` `openToolDialog`, `:2602` `handleToolCommand`, `:3172` `executeApprovedToolCall`) |
| Rule S-4 cited in source | KDoc at `:2593-2598` |

Line numbers appear here as a point-in-time record only. **No committed assertion references one** — the ledger names sites by function, and every ordering check is a body-relative index, both for the reason WINDOWS.md entry 34 exists.

## Test count

`ChatPanelEdtConfinementTest`: **23 → 28** tests, 0 skipped, 0 failures.

## Task Commits

1. **Task 1: Rule S-4 on both user-originated paths** — `461551a` (test)
2. **Task 2: Rule S-3 busy-state entry + count ledger** — `5b5da7b` (test)

Plan metadata: this SUMMARY's own commit.

## Files Created/Modified

- `src/test/kotlin/com/six2dez/burp/aiagent/ui/ChatPanelEdtConfinementTest.kt` — +217 lines: five `@Test`s, the `assertPrecedesWithin` helper, four needle constants, and the `CHAT_PANEL_BUSY_STATE_ENTRIES` ledger. Reuses the file's existing `chatPanelSource()` / `functionBody()` / `occurrencesOf()` helpers and its existing `OFF_EDT_DISPATCH_CALL` constant; no new file, no new `tasks.test` input, no read of any main file other than `ChatPanel.kt`.

## Decisions Made

- **The exactly-once precondition is load-bearing, not defensive.** `String.indexOf` returns `-1` for an absent needle and `-1` sorts before everything, so a bare `indexOf(a) < indexOf(b)` would go **green** the moment the echo it guards was deleted — the loudest form of the regression the test exists to catch. Requiring exactly one occurrence of each needle inside the body makes both "moved below the dispatch" and "removed entirely" failures.
- **Sites named by function, never by line.** The ledger reads `sendMessage`, `openToolDialog`, `handleToolCommand`, `executeApprovedToolCall`. A function name survives every edit that does not actually move the call; a line number does not, and this repo has burned that twice.
- **The count test counts source text, and the KDoc says so.** A comment in `ChatPanel.kt` spelling `setSendingState(true)` out verbatim would move the counter — the same trap prose sprang on the `assertEdt()` counter in plan 23-01. Naming it in the failure message is what stops the next reader "fixing" it by relaxing the constant.

## Non-vacuity evidence (verbatim)

Scratch mutation: in `handleToolCommand`'s `/tool` branch, `panel.addMessage("You", trimmed)` and `setSendingState(true)` moved below the `OffEdtDispatch.run(...)` call. Result — 28 tests, **2 failures**, both new:

```
theSlashToolPathEchoesTheTypedCommandBeforeItGoesAsync()
org.opentest4j.AssertionFailedError: UI-SPEC Rule S-4 (slash path): `panel.addMessage("You", trimmed)`
must appear BEFORE `OffEdtDispatch.run(` inside `private fun handleToolCommand(`, because the /tool echo
must be emitted before the work leaves the EDT, or the transcript can show a `Tool result:` row with no
visible request above it.

theSlashToolPathEntersTheBusyStateBeforeItGoesAsync()
org.opentest4j.AssertionFailedError: UI-SPEC Rule S-3 (slash path): `setSendingState(true)` must appear
BEFORE `OffEdtDispatch.run(` inside `private fun handleToolCommand(`, because between the dispatch and a
later busy-state entry the panel is idle and live, and a Send pressed in that window fans out a second
worker.
```

The `everyBusyStateEntryInChatPanelIsAccountedFor` counter correctly stayed **green** under this mutation — the call was moved, not deleted, so the count was still 4. That division of labour is the design: the ordering tests catch a move, the counter catches a removal or an unaccounted addition.

Mutation reverted with `git checkout HEAD -- src/main/kotlin/com/six2dez/burp/aiagent/ui/ChatPanel.kt`, never `git stash`, and never committed.

## Verification

```
JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test --tests '*ChatPanelEdtConfinement*' ktlintCheck detekt --console=plain
BUILD SUCCESSFUL
```

`ChatPanelEdtConfinementTest`: tests=28 skipped=0 failures=0 errors=0. `ktlintCheck` and `detekt` clean — no `detekt-baseline.xml` entry added, so `DetektBaselineBoundTest`'s count is unmoved. `./gradlew check` deliberately not run (coverage floor plus WINDOWS entry 47 flake, per the plan).

## Deviations from Plan

None — plan executed exactly as written. Every measured baseline in the plan was re-measured at the base commit and found unmoved, so nothing needed re-pinning.

## Issues Encountered

None.

## Prohibition compliance

- `git status --porcelain -- src/main/` empty at both task commits and at close-out; the base-to-HEAD diff touches one file, under `src/test/`.
- No new test file; no new `tasks.test` input declaration; no main-source read other than `ChatPanel.kt`.
- No absolute line number in any assertion.
- `.planning/REQUIREMENTS.md` sha256 verified `9b3219662ec0d007c1c82d64eed3ef2698bd306ce69f01205ac9bbc3f42fcfb4`.
- `.planning/WINDOWS.md` untouched — entry 2 is the orchestrator's to close after merge.

## Self-Check: PASSED

- `src/test/kotlin/com/six2dez/burp/aiagent/ui/ChatPanelEdtConfinementTest.kt` present, +217 lines vs base.
- Commits `461551a` and `5b5da7b` present in `git log`.
- Targeted test plus `ktlintCheck` plus `detekt` re-run green after the scratch mutation was reverted.

## Next Readiness

WINDOWS.md entry 2 (`unrun-verify`, phase 23) is now discharged by a committed test rather than a grep that ran once. The orchestrator can close it after merge.

---
*Quick task: 260829-uispec-s4-s3-source-order-test*
*Completed: 2026-08-29*
