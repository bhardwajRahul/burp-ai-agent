---
phase: 22-agent-tool-call-trust-boundary
plan: 06
subsystem: ui
tags: [kotlin, swing, sec-06, trust-typography, anti-spoofing, accessibility, design-tokens, headless, junit5]

# Dependency graph
requires:
  - plan: "22-01"
    provides: "ChatPanelTestHarness (depth-first component finder) and -Djava.awt.headless=true in tasks.test"
  - plan: "22-02"
    provides: "SecTier { AUTO, CONFIRM, CONFIRM_EACH } in McpToolCatalog.kt"
  - plan: "22-03"
    provides: "ToolDecision, ImplicitDenyReason, sanitizeInline, sanitizeBlock in ToolApprovalGate.kt"
provides:
  - "ToolApprovalCard — the SEC-06 inline decision surface: pending CONFIRM / CONFIRM_EACH states, four or two actions, resolved outcome row, and both compact resolved variants"
  - "ToolApprovalCard.resolve(decision, implicitReason) — in-place mutation that replaces the button row with an outcome row in the same grid cell"
  - "ToolApprovalCard.compact(decision, ...) — the session-suppressed receipt row, both variants"
  - "An accessible description that always ends with the sanitized model tool ID"
  - "ToolApprovalCardTest — nine headless assertions on the trust-typography rules"
affects: ["22-07", "22-08", "22-09"]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Trust typography: model-supplied text is rendered ONLY into JTextField / JTextArea; JLabel, AbstractButton, JEditorPane and tooltips are forbidden for it because all four install Swing's HTML renderer"
    - "Anti-spoofing asserted mechanically via getClientProperty(\"html\") rather than by code review"
    - "Composite Swing component that reuses ActionCard's layout idiom, SubtleNotice's accent-strip compound border and SafetyIndicator's initialized-flag updateUI guard WITHOUT extending any of them"
    - "Tier-derived initial state computed inside the component so no call site can override it"
    - "getAccessibleContext() called explicitly inside a JComponent subclass, never the `accessibleContext` property"

key-files:
  created:
    - src/main/kotlin/com/six2dez/burp/aiagent/ui/components/ToolApprovalCard.kt
    - src/test/kotlin/com/six2dez/burp/aiagent/ui/ToolApprovalCardTest.kt
  modified: []

key-decisions:
  - "FLAG-22-01 repeat counter ACCEPTED; FLAG-22-03 SHIP BOTH compact variants; FLAG-22-04 caps kept at 40 lines / 3200 chars with the ceiling interpolated from Defaults.MAX_CONTEXT_TOTAL_CHARS; FLAG-22-05 tier badge stays private to the file; FLAG-22-06 U+2716 kept"
  - "resolve(ToolDecision.AUTO) fails loudly with error() rather than reusing another branch's copy — an AUTO call renders no card, and putting a human-decision sentence on a call no human saw is the exact conflation ToolDecision's KDoc forbids"
  - "The two session-scoped tooltips are omitted entirely when catalogTitle is null, rather than interpolating invented copy into a locked contract string"
  - "The <html> wrap prescribed by UI-SPEC for long label rows is applied ONLY to the two truncated footers and the compact outcome verb — extending it to row 10 would have put an HTML renderer on a pending card and broken the anti-spoofing assertion"

requirements-completed: []  # INTENTIONALLY EMPTY — see "Requirement Status" below.

# Metrics
duration: ~55 min
completed: 2026-08-14
---

# Phase 22 Plan 06: The SEC-06 Tool-Approval Card Summary

**An inline transcript card that renders a model-emitted tool call for an explicit user decision, and keeps the model's text structurally incapable of impersonating the extension's own chrome — proven headlessly, not argued.**

## Performance

- **Duration:** ~55 min
- **Tasks:** 3 of 3
- **Files created:** 2 (1 production, 1 test)
- **Commits:** 4 (3 planned tasks + 1 auto-fixed bug)

## Accomplishments

- **The anti-spoofing property is mechanical, not aspirational.** `modelSuppliedTextNeverInstallsTheHtmlRenderer` builds a card whose tool ID is `"<html><b>Approved</b>"` and whose args are `"<html><i>safe</i>"`, then walks the whole component tree asserting `getClientProperty("html")` is null on every `JLabel` and every `AbstractButton`, and that the model's strings appear only inside a `JTextField` or `JTextArea`. Rendering the tool ID into a `JLabel` was measured to turn it red.
- **The tier difference is structural, not decorative.** A `CONFIRM` card renders four D-11 actions in safe-action-first order with args collapsed; a `CONFIRM_EACH` card renders exactly two with args **expanded by default**. `initiallyExpanded` is derived from the tier inside the file (`private val initiallyExpanded = tier == SecTier.CONFIRM_EACH`) and is deliberately not a constructor parameter, so no call site can defeat the T-22-29 mitigation.
- **The card is a record.** `resolve()` removes the buttons — never disables them — and drops an outcome row naming the clicked action verbatim into the button row's own `GridBagLayout` cell, so no other row reflows. The tier badge, the tool-ID box and the args stay, and the accent strip drops to `borderSubtle` so a glance at the transcript answers "is anything waiting for me".
- **Both compact resolved variants ship** (FLAG-22-03 decided): a session-denied call is never invisible, and a session-approved call that ran against the target with nobody asked leaves a receipt.
- **The accessible description is safe by position.** All three UI-SPEC conditions hold: the ID is inline-sanitized, immediately preceded by the disclosure clause, and is the final element with nothing after it — asserted with `endsWith` across a pending known tool, a pending unknown tool and a resolved card.
- **Zero new tokens, zero new dependencies.** No `Color(0x…)` literal, no `Font(...)` constructor, no legacy theme-shim import, no nested scroll pane, no `preferredSize`, no `maximumSize` override, no `JTextArea.rows`. The detekt baseline did not grow.

## Task Commits

1. **Task 1: Build the pending card — structure, trust typography, tier badge, args disclosure** — `46316a7` (feat)
2. **Task 2: Add resolution, the compact resolved row, and the accessible description** — `e3c2f76` (feat)
3. **Rule 1 auto-fix: accessible context read through its getter** — `759f32f` (fix)
4. **Task 3: Create ToolApprovalCardTest — prove the trust-typography rules headlessly** — `00078c6` (test)

## Files Created/Modified

- `src/main/kotlin/com/six2dez/burp/aiagent/ui/components/ToolApprovalCard.kt` (937 lines, new) — `internal class ToolApprovalCard : JPanel(GridBagLayout())` with a **private primary constructor**, an `internal` secondary constructor for the pending card, and `companion object { compact(...) }` for the resolved-only row. Eleven rows on the full card, five visible on a compact one. Private `tierBadge()`, `resolve()`, `buildOutcomeRow()`, `updateAccessibleDescription()`, `updateArgsView()`, `applyTheme()`, `updateUI()` override behind an `initialized` guard. All copy is verbatim from the UI-SPEC Copywriting Contract.
- `src/test/kotlin/com/six2dez/burp/aiagent/ui/ToolApprovalCardTest.kt` (327 lines, new) — nine headless tests. Reuses `ChatPanelTestHarness.find` for every single-component lookup.

## Requirement Status

**`requirements-completed` is deliberately empty.** This plan builds the SEC-06 decision *surface*; it does not wire it into `ChatPanel`, so a model-emitted tool call still reaches Burp with no card in front of it. The control lands when 22-07 and 22-08 connect the gate, the card and the transcript. `REQUIREMENTS.md` was not modified — it is a shared wave artifact and marking SEC-06 complete here would check off an unshipped control, which is exactly the over-claim this milestone exists to eliminate. Consistent with 22-01's stance.

## Decisions Made

- **`resolve(ToolDecision.AUTO)` fails loudly.** The Copywriting Contract specifies no `AUTO` outcome line because an `AUTO`-tier call renders no card (D-02). Reusing `APPROVE_ONCE`'s line would put a human-decision sentence on a call no human ever saw — the exact conflation `ToolDecision`'s own KDoc says makes "did a human authorise this invocation?" unanswerable from the log. The branch therefore calls `error()` with a message naming D-02: it is a programming error at the call site, not a state a user can reach.
- **`IMPLICIT_DENY` with a non-`NEW_MESSAGE` reason drops the clause rather than inventing copy.** Only `NEW_MESSAGE` leaves a surviving surface; the other four teardown paths destroy the transcript, so their record is the SC3 audit event. If one ever reaches the card, it renders `Denied automatically` — the contract's own line minus the clause that would be false. Saying less beats inventing copy for a state nobody designed.
- **Session tooltips are omitted, not filled, when `catalogTitle` is null.** `Run this and any later {title} call…` has no honest rendering without a title, and the alternative (`this tool`) is grammatically broken. The state is unreachable anyway — an unknown tool resolves to `CONFIRM_EACH`, which renders no session buttons — so omission costs nothing and invents nothing.
- **The `<html>` wrap is applied narrowly.** UI-SPEC §Typography requires it on the two truncated footers and the compact outcome verb. It was NOT extended to row 10 (the session-scope footer), even though that row is also long: row 10 is present on every *pending* card, so wrapping it would install the HTML renderer on a `JLabel` in the exact card the anti-spoofing test probes. Spec and test agree here, and following the spec literally was the correct call.
- **The compact factory fixes the tier at `CONFIRM` rather than taking it from the caller.** It is invariant: both session sets can only be populated by `Approve for session` / `Deny for session`, which only a `CONFIRM` card renders. `require()` rejects any decision other than `SESSION_APPROVED` / `SESSION_DENIED`.
- **`onDecision = { }` is supplied inside the class for the compact row, not exposed on the factory.** A compact row renders no decision button so nothing can invoke it, and keeping it off the factory signature preserves the `ContextPreviewDialog.kt:21-23` property that no caller can ship a card whose buttons do nothing.
- **A `descendantsOf` collector was added to the test file alongside `ChatPanelTestHarness.find`.** The harness finder is reused for every single-component lookup and is not duplicated. But the assertions that carry the security property here are *counts* and *exhaustive sweeps* ("exactly four buttons", "no `JLabel` anywhere has the HTML renderer"), which a first-match finder cannot express. The distinction is documented in the helper's KDoc.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Accessible description NPE'd on every card ever constructed**

- **Found during:** Task 3 (all nine tests failed on the same `NullPointerException`)
- **Issue:** Task 2 wrote `accessibleContext.accessibleDescription = …` inside the card. From within a `JComponent` subclass, Kotlin resolves the name `accessibleContext` to `java.awt.Component`'s **protected field**, not to the synthetic property behind `getAccessibleContext()`. That field stays null until the getter lazily creates the context, so the assignment threw on the first card built — making the entire T-22-27 accessible-description contract unreachable at runtime.
- **Fix:** call `getAccessibleContext()` explicitly, exactly as the plan's Task 2 action specified. A comment records the resolution trap so it is not "simplified" back to the property form.
- **Files modified:** `src/main/kotlin/com/six2dez/burp/aiagent/ui/components/ToolApprovalCard.kt`
- **Commit:** `759f32f`

This is the clearest evidence the suite is non-vacuous: a contract that was written, reviewed and compiled cleanly was dead at runtime, and the tests caught it on their first run.

**2. [Rule 3 - Blocking] ktlint `function-signature` violations in the test file**

- **Found during:** Task 3 verification
- **Issue:** two single-expression helper functions wrapped their body onto the next line when it fit on the signature line.
- **Fix:** joined both onto one line. No behaviour change.
- **Commit:** folded into `00078c6`

### Structural choices worth recording (not deviations)

- The card was built as a **composite**, not an `ActionCard` subclass, per the plan and the UI-SPEC. It reuses `ActionCard`'s single-column `GridBagLayout` + expand/collapse idiom, `SubtleNotice`'s 3 px `MatteBorder` accent strip inside a compound border, `SafetyIndicator`'s `initialized`-flag `updateUI()` guard, `AccordionPanel`'s ▶/▼ glyphs and the `Components.kt` builders — while inheriting none of the four defects the UI-SPEC names in `ActionCard` (legacy theme shim, `Colors.surface`, nested scroll pane, off-grid insets, no `updateUI()` override).
- `PrivacyPill.kt` was read as the named anti-analog and its hardcoded colour literals were not copied.

## Issues Encountered

**1. The show-all button has no row number in the UI-SPEC.** §"Three-stage disclosure" requires a show-all button alongside the truncation footer, but the row table stops at row 10 with the footer as row 8. Adding a twelfth grid row would have renumbered rows 9 and 10 against the spec's own table. Resolved by making row 8 a `BoxLayout.Y_AXIS` container holding the footer and (when more is available on this card) the button — the spec's row count and insets table both stay exactly as written.

**2. Exact character counts in the truncation footer.** `sanitizeBlock` appends one ellipsis character when it truncates, so `previewArgs.length` overstates the number of *argument* characters on screen by one. The footer subtracts it. On a card whose entire purpose is authorisation, a count that is one too high is a false statement, which is the same standard the UI-SPEC applies to `Showing all N characters.`

**3. detekt `TooManyFunctions` (threshold 11) shaped the file's structure.** Pure helpers — outcome mapping, tier text and colour, footer copy, the ordinal, the row-adding extension — are top-level private functions rather than class members. This keeps the class at 10 functions and the file at 9 top-level ones, and it has the side benefit that every one of those helpers is a pure function of its arguments.

## Verification Results

| Check | Result |
|-------|--------|
| `./gradlew ktlintCheck detekt test` | **BUILD SUCCESSFUL** (2m 28s) |
| `ToolApprovalCardTest` suite XML | `tests="9" skipped="0" failures="0" errors="0"` |
| `git diff --stat -- detekt-baseline.xml` | **empty** — baseline did not grow (QUAL-07) |
| `git diff --diff-filter=D` since base | **empty** — no file deletions |
| Files touched | exactly the two declared in `files_modified` |

All per-task `<acceptance_criteria>` were executed individually against the **final** file, not just at task time. Counts: `class ToolApprovalCard` 1; `Color(0x` 0; `= Font(` 0; `UiTheme` 0; `JScrollPane` 0; `preferredSize =|override fun getMaximumSize|.rows =` 0; `override fun updateUI` 2; `initialized` 4; `isFocusPainted = true` 2; `initiallyExpanded` 2 with the assignment reading `tier == SecTier.CONFIRM_EACH`; `initiallyExpanded:` 0; `defaultButton|setMnemonic|requestFocusInWindow|VK_ESCAPE` 0; D-11 labels 4; `MAX_CONTEXT_TOTAL_CHARS` 5; `40000|40_000` 0; `setToolTipText` 0; `fun resolve(` 1; `isEnabled = false` 0; `remove(` 1; `accessibleDescription` 2; disclosure clause 1; `Ran without asking|Blocked automatically` 2; `no catalog entry matches this name` 1 with an adjacent `unreachable` comment; `✔|✖` 2; `scrollRectToVisible` 0; `class ToolApprovalCardTest` 1; `@Test` 9; `getClientProperty("html")` 1; `doClick` 1; `endsWith` 2.

The single `toolTipText` assignment was read manually: its value is either a literal or a `catalogTitle`-interpolated string, never the model's.

### Behaviour checks (red-when-broken proofs)

All three mandated checks were run, and each was reverted immediately after:

| Mutation | Expected failure | Observed |
|----------|------------------|----------|
| Render the model tool ID into a `JLabel` instead of the `JTextField` | `modelSuppliedTextNeverInstallsTheHtmlRenderer` | **3 failed** — that test plus `modelSuppliedTextIsNeverConcatenatedIntoExtensionText` and `unknownToolIsLabelledNeverShownBare`. The anti-spoofing rule is guarded from three independent directions. |
| Append `"."` after the tool ID in the accessible description | `accessibleDescriptionEndsWithTheSanitizedToolId` | **1 failed** — exactly that test. |
| Render four buttons for `CONFIRM_EACH` | `confirmEachCardRendersTwoDecisionButtons` | **1 failed** — exactly that test. |

`git diff` against the last commit was inspected after the reverts and showed no residue.

## Known Stubs

None. Every component on the card is wired to real data: the tool ID and args come from the constructor, the tier badge and accent strip from `SecTier`, the buttons from the `onDecision` callback, the counts from the sanitized strings. Two code paths are **unreachable in this phase and documented as such in-file** — the compact unknown-tool title (an unknown tool resolves to `CONFIRM_EACH`, which never populates a session set) and the `AUTO` tier badge (an `AUTO` call renders no card). Both exist for `when` exhaustiveness, both carry a comment naming the reason, and ADR-15 records them under "claim only what ships". Neither is a stub: they are implemented, not placeheld.

## Threat Flags

None. Every mitigation the plan's threat register assigned to this card is implemented and asserted:

| Threat | Mitigation | Asserted by |
|--------|------------|-------------|
| T-22-07 spoofing | Model text only in `JTextField` / `JTextArea` | `modelSuppliedTextNeverInstallsTheHtmlRenderer`, `modelSuppliedTextIsNeverConcatenatedIntoExtensionText` |
| T-22-26 fabricated title | Rule T-7 unknown-tool label | `unknownToolIsLabelledNeverShownBare` |
| T-22-27 screen-reader narration | Three-condition accessible description | `accessibleDescriptionEndsWithTheSanitizedToolId` |
| T-22-28 stray Enter / mis-click | No default button, no mnemonics, no Escape, no focus theft, 16 px pole gap, focus ring painted | grep criteria (0 forbidden bindings) + code review |
| T-22-29 hidden payload | No nested scroll pane, `CONFIRM_EACH` expands by default, honest truncation footers | `confirmEachCardRendersTwoDecisionButtons`, `confirmCardRendersFourDecisionButtons` |
| T-22-30 repudiation | Buttons removed and replaced by a verbatim outcome row; both compact variants ship | `resolutionRemovesButtonsAndAddsAnOutcomeRow`, `compactRowRendersBothVariantsWithNoButtons` |
| T-22-SC package tampering | Zero packages added — no dependency block touched | `git diff` on `build.gradle.kts` is empty |

## User Setup Required

None.

## Next Phase Readiness

- **For 22-08 (the wiring plan):** the API is `ToolApprovalCard(tier, catalogTitle, modelSuppliedToolId, modelSuppliedArgsJson, offersSessionActions, repeatCount, onDecision, onRequestFocusRestore = null)` for a pending card and `ToolApprovalCard.compact(decision, catalogTitle, modelSuppliedToolId, modelSuppliedArgsJson)` for a session-suppressed receipt. Resolution is `card.resolve(decision, implicitReason = null)`. Three things the caller owns and the card deliberately does not: `offersSessionActions` is computed by the gate (the card never re-derives it from the tier), `repeatCount` comes from 22-04's `ToolApprovalMemory.recordRequest`, and `onRequestFocusRestore` should be wired to the input-area focus call `ChatPanel` already makes at `ChatPanel.kt:960`.
- **`resolve()` is idempotent** — a second call is a no-op — so a race between a click and an implicit-denial path cannot produce two outcome rows.
- **Still to build for the UI half of the phase:** `SessionPanel.scrollToComponent(JComponent)` and the `Awaiting approval` marker in `ChatSessionRenderer`. Both are specified in the UI-SPEC and both are outside this plan's `files_modified`.
- **`ChatPanel.kt` was not touched**, so this branch should merge cleanly against the siblings in this wave.
- **No blockers.**

## Self-Check: PASSED

- Both key files verified present on disk (`ToolApprovalCard.kt` 937 lines, `ToolApprovalCardTest.kt` 327 lines).
- All 4 commits verified in `git log`: `46316a7`, `e3c2f76`, `759f32f`, `00078c6`.
- No file deletions across the branch (`git diff --diff-filter=D` empty).
- `git diff --name-status` since base shows exactly the two declared files, both added.
- `STATE.md` and `ROADMAP.md` untouched, as required in worktree mode.

---
*Phase: 22-agent-tool-call-trust-boundary*
*Completed: 2026-08-14*
