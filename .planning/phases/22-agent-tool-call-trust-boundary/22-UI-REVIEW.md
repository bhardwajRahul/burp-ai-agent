# Phase 22 — UI Review

**Audited:** 2026-08-19
**Baseline:** `22-UI-SPEC.md` (approved 2026-08-13, binding) + `22-CONTEXT.md` D-01…D-14
**Component under audit:** `ui/components/ToolApprovalCard.kt` (937 lines, new) + its `ChatPanel` wiring
**Screenshots:** **not applicable** — Kotlin/Swing inside Burp, no browser, no DOM, no dev server (ports 3000 / 5173 / 8080 all closed). This is a **code + headless-measurement** audit. Every quantitative claim below was measured on Temurin JDK 21 headless, not estimated.
**Registry audit:** skipped — `components.json` absent, `22-UI-SPEC.md` §Registry Safety declares n/a, zero dependencies added.

> **Scope note.** A human passed live Burp UAT on all four items of `22-HUMAN-UAT.md` §1 (legible in both themes, four actions present and reachable, args preview expands and is readable, tier badge legible). **That is settled and this review does not relitigate it.** What UAT did *not* exercise, and what this audit targets, is: a **narrow transcript**, a card with **truncated args** (> 3200 chars), a card with **zero args**, the **repeat-counter** row, a **resolved** card, a **compact** row, the **screen-reader Tab path**, and a **live theme switch with a card already on screen**.

---

## Pillar Scores

| Pillar | Score | Key Finding |
|--------|-------|-------------|
| 1. Copywriting | 3/4 | All ~25 contract strings verbatim; a zero-args call renders `▶ Show arguments (0 characters)` with no contract copy for it |
| 2. Visuals | 3/4 | Trust distinction implemented exactly as specified, but three hierarchy inversions point the eye at the least informative content |
| 3. Color | 4/4 | Zero literals, `primary` absent, every status use redundant with text or structure, the contrast hazard resolved as specified |
| 4. Typography | 3/4 | Exactly the 5 declared roles, zero new derivations; the declared **overflow contract is applied to 3 of 5 qualifying rows** |
| 5. Spacing | 2/4 | Inset table implemented flawlessly, but the card **cannot shrink** and the transcript has no horizontal scrollbar — measured floor 660 px |
| 6. Experience Design | 3/4 | Strongest part of the phase; the two model regions have **no accessible name**, so the Tab-in path bypasses the T-5 attribution |

**Overall: 18/24**

---

## Top Priority Fixes

Ranked by impact. Six actionable items, not three.

1. **BLOCKER — The card has a 660 px hard floor and the transcript cannot scroll horizontally.**
   *Impact:* dragging the sessions divider right (`MainTab.kt:251-257`, a plain `JSplitPane` with **no minimum size** on `chatPanel.root`) silently pushes the right-hand side of the approval card past the viewport edge. `ChatPanel.kt:1852` sets `horizontalScrollBarPolicy = HORIZONTAL_SCROLLBAR_NEVER`, so there is no scrollbar, no wrap and no affordance. Row 10's tail is cut first; below ~520 px of viewport, **`Approve for session` itself becomes unreachable.**
   *Fix:* add an `<html>` prefix to `SESSION_SCOPE_FOOTER` (`ToolApprovalCard.kt:96-97`) and to both `TIER_REASON_*` strings (`:93-95`). **Measured effect:** row 10's minimum width drops from **615 px to 67 px**, taking the card's hard floor from 644 px to the 4-button row's 487 px + 29 px chrome ≈ **516 px**. Then set `chatPanel.root.minimumSize` in `MainTab.kt` so the divider cannot be dragged past that floor. See finding **S-1**.

2. **BLOCKER-adjacent — The anti-spoofing test's blanket assertion is already inconsistent with shipped behaviour, and it blocked fix #1.**
   *Impact:* `ToolApprovalCardTest.kt:99-109` asserts `getClientProperty("html")` is null on **every** `JLabel`. `truncationFooter` is a `JLabel` on a **pending** card and is assigned `"<html>Showing the first…"` at `ToolApprovalCard.kt:862` whenever the preview truncates. Measured: that assignment installs the renderer (non-null client property). The test passes only because its fixture args are 23 characters (`ToolApprovalCardTest.kt:287`). This is why row 10 was left unwrapped (`22-06-SUMMARY.md:93`) — a correct UI fix was blocked by an over-broad test, and the next legitimately-wrapped label will turn the suite red.
   *Fix:* scope the sweep to labels carrying model text, or exempt the three extension-authored wrapped rows by identity. `modelSuppliedTextIsNeverConcatenatedIntoExtensionText` (`:128-143`) already asserts the property that actually carries the security guarantee. See finding **T-2**.

3. **WARNING — Rule T-5's disclosure label is painted in the L&F's *disabled text* colour.**
   *Impact:* `Colors.onSurfaceVariant` resolves to `UIManager.getColor("Label.disabledForeground")` (`DesignTokens.kt:134-135`) — the role whose entire design purpose is to be ignorable. §T-4 concedes the background channel is nearly invisible in light theme, leaving the 1 px border, the mono font and this caption to carry the trust distinction; the caption is the only one that says in words *what the box means*, and it is the most de-emphasised text on the card, at 0.9× size, in the same treatment as the session-scope boilerplate.
   *Fix:* `trustLabel.foreground = DesignTokens.Colors.onSurface` at `ToolApprovalCard.kt:739`. One line, no new token, size unchanged. See **V-2**.

4. **WARNING — The two model regions have zero internal padding.**
   *Impact:* measured — after `applyFieldStyle` / `applyAreaStyle` replace the L&F border with `LineBorder(border, 1, true)`, both `JTextField` and `JTextArea` report `margin = [0,0,0,0]` and total `insets = [1,1,1,1]`. The model's monospace text renders flush against the 1 px line on all four sides. Containment is the card's **primary** trust channel; a box with no padding reads as a hairline rule around text rather than as a container, and this actively *removes* padding the L&F's own field border would have supplied.
   *Fix:* in `applyTheme()` (`ToolApprovalCard.kt:748-749`), after the builder calls, add `toolIdField.margin = Insets(Spacing.xs, Spacing.xs, Spacing.xs, Spacing.xs)` and the same on `argsArea`. On-grid, no new token. See **S-2**.

5. **WARNING — The model regions have no accessible name, so the Tab-in path bypasses the T-5 attribution.**
   *Impact:* the card **root's** `accessibleDescription` is correct and rigorously tested, but `toolIdField` is a deliberate Tab stop (§Traversal) and `argsArea` is focusable whenever visible. Neither carries an `accessibleName`, and `trustLabel` has no `setLabelFor`. A screen-reader user tabbing from the input area lands on the field and hears the **model's string with no attribution** — the T-22-27 narration threat, on the one path the root-description mitigation does not cover.
   *Fix:* `trustLabel.labelFor = toolIdField` (makes the contracted T-5 sentence the field's accessible name via `ACCESSIBLE_LABELED_BY`, leaving the model string as the field's *value* — no concatenation, no new copy, and `labelFor` installs no mnemonic so the forbidden-bindings rule is untouched), plus `argsArea.getAccessibleContext().accessibleName = TRUST_LABEL`. See **X-1**.

6. **WARNING — Visual hierarchy is inverted twice on every card.**
   *Impact:* (a) the heading `The AI asked to run a tool` is `sectionTitle` (bold, 1.2×, measured 193 px) and is **byte-identical on every card**, while the catalog title — the varying answer to "what is being authorised" — is `body`, the same role as the `Deny` button label; (b) `argsToggle` is added with `fill = HORIZONTAL, weightx = 1.0`, so the one control that is **not** a decision renders as a full-bleed slab spanning the whole card, while `showAllButton` three lines away is glue-wrapped to its preferred width.
   *Fix:* `titleLabel.font = DesignTokens.Typography.label` at `:732` (existing role, bold base); wrap `argsToggle` in the same `BoxLayout.X_AXIS` + `horizontalGlue` idiom already present at `:452-457`. See **V-1**, **V-3**.

---

## Detailed Findings

### Pillar 1: Copywriting (3/4)

**Verified verbatim against §Copywriting Contract — all of it.** Every one of ~25 contract strings matches character for character: heading (`:80`), both unknown-tool titles (`:81`, `:90`), both tier reasons (`:93-95`), repeat counter (`:300`), trust label (`:92`), both toggle labels (`:637-639`), both show-all labels (`:874-877`), all three footers (`:862-869`), the session-scope footer (`:96-97`), all four D-11 button labels (`:106-109`), all four tooltips (`:111-112`, `:518-520`) and all seven outcome lines (`:892-914`). No generic label appears anywhere — grep for `Submit` / `Click Here` / `OK` / `Cancel` returns nothing on this card.

Three details deserve credit rather than criticism, because they are the kind of thing that is normally wrong:

- `previewShownChars` subtracts the one ellipsis character `sanitizeBlock` appends (`:252-255`), so the truncation footer states the exact number of *argument* characters on screen. On a card whose purpose is authorisation, a count one too high is a false statement.
- `Showing all {N} characters.` is *structurally* unreachable when the 40 000 ceiling engages (`footerTextFor`, `:860-870` — the `ceilingEngaged` branch precedes it), so the copy cannot become a lie.
- The ceiling footer's middle sentence explains the absent show-all button, and the last sentence of both footers carries the display-limit-is-not-an-execution-limit property.

**WARNING C-1 — no copy for a zero-args call, and it is reachable.**
`ParsedToolCall.argsJson` is nullable (`ToolCallParser.kt:14`); `resolveToolArgs` (`:106-121`) returns `null` when the model emits a tool call with no `args` / `arguments` / `input` / `function.arguments` key — the normal emission for a no-parameter tool. `sanitizeBlock(null, …)` returns `""` (`ToolApprovalGate.kt:544`), so `totalChars == 0` and the toggle at `ToolApprovalCard.kt:639` reads **`▶ Show arguments (0 characters)`**. Expanding it produces an empty bordered box directly under `AI-supplied — this extension did not write the text below:`, pointing at nothing. The Copywriting Contract's "Empty state — n/a" line is about the *card*, not the args region. Note that `proxy_http_history` — the tool `22-HUMAN-UAT.md` §1 instructs the tester to elicit — takes no required arguments.
*Fix:* suppress rows 6/7/8 entirely when `totalChars == 0` and render one caption in their place (e.g. `This call has no arguments.`), or state the case in the contract.

**WARNING C-2 — `Denied automatically` (`:906`) is an un-contracted user-facing string.** Invented at implementation time for the four non-`NEW_MESSAGE` implicit-deny reasons, and documented as a deliberate call (`22-06-SUMMARY.md:91`). The reasoning — saying less beats inventing copy — is sound, and the string is currently unrenderable because all four of those paths destroy the transcript after `resolve()` runs. It becomes visible the moment a sixth teardown path leaves the transcript alive. Recommend adding it to the contract rather than leaving it as implementation-only copy.

**MINOR C-3 — the two session tooltips are omitted when `catalogTitle == null`** (`:518-520`). The contract lists them unconditionally. Unreachable today (unknown tool → `CONFIRM_EACH` → `offersSessionActions = false`), but the card takes `offersSessionActions` from the gate and **deliberately never re-derives it** (`:192-195`) — so the two facts are not coupled, and a gate change alone would ship two untooltipped security buttons.

**Score rationale:** contract conformance is exceptional and the honesty discipline around counts exceeds what the contract required. Not 4/4 because one reachable path renders a nonsense string on the most commonly demonstrated tool.

---

### Pillar 2: Visuals (3/4)

**The trust-typography contract is implemented in full, and the security-critical parts are correct.** Verified by reading, and cross-checked against the test suite's structural assertions:

- Model text reaches **only** `JTextField` (`:304`) and `JTextArea` (`:308`). No `JLabel`, no `AbstractButton`, no `JEditorPane`, no tooltip carries it — `setToolTipText` on this card is called exactly once, at `:622`, with a value that is either a literal or a `catalogTitle` interpolation.
- `isEditable = false` is set **before** the builder runs (`:338-340` vs `:748-749`), so the documented L&F disabled-background trap is avoided.
- The trust label sits outside every box (`:302`, added at `:495` / `:469`), on both card kinds — Rule T-5's non-conditionality is honoured.
- Tier legibility ships all four redundant channels: badge text (`:674`, `:829-834`), badge border colour (`:691`), 3 px accent strip (`:715-719`), button count (`:522-534`). Remove all colour and the card still reads.
- `PrivacyPill.kt`'s hardcoded `Color(0x…)` literals were **not** copied — grep for `Color(0x` in this file returns zero.

**WARNING V-1 — the loudest text on the card is the least informative text on the card.**
`headingLabel` = `Typography.sectionTitle` (bold, 1.2×, measured preferred width 193 px) at `:730`. It is identical on every card. `titleLabel` — the catalog title, i.e. *which tool* — is `Typography.body` at `:732`, the same role as the four button labels (`:620`) and the args toggle (`:751`). The card is explicitly designed to "read unambiguously months later"; months later a reader scrolling a transcript wants to see **which tool**, not forty repetitions of `The AI asked to run a tool`. The spec's own §compact-row reasoning drops the heading *because it is redundant* — the same argument applies to a full card sitting in a run of eight.
*Fix:* `titleLabel.font = DesignTokens.Typography.label` at `:732`. Existing role, no new derivation.

**WARNING V-2 — the T-5 disclosure label is styled as disabled text.** (Priority fix #3; full rationale there.) `trustLabel = helpLabel(TRUST_LABEL)` (`:302`) → `Typography.caption` + `Colors.onSurfaceVariant` (`:738-739`), and `onSurfaceVariant` is `Label.disabledForeground` (`DesignTokens.kt:134-135`). This is exactly what the spec prescribed, so it is a **spec-conformant weakness, not a deviation** — the brief asked for §T-4's honesty to be judged on its own terms, and this is my answer: in light theme the containment channel is carried by a 1 px `Component.borderColor` line that looks identical to every ordinary text field in the extension, plus a monospace font, plus one caption in disabled grey. Two of those three signals are weak, and the third — the only one that *explains* the boundary — is deliberately de-emphasised. Promoting it to `onSurface` costs nothing and is the cheapest available strengthening.

**WARNING V-3 — the args toggle is the single largest control on a card whose thesis is "no visual winner".** `argsToggle` is added through `addRow` (`:497`) with the shared constraints `fill = HORIZONTAL, weightx = 1.0, gridwidth = REMAINDER` (`:437-444`), so its button chrome spans the entire card width (measured preferred 251 px; it will stretch to ≈ 615 px at the card's current minimum). `showAllButton` — the sibling control in the same disclosure flow — is wrapped in a `BoxLayout.X_AXIS` row with a `horizontalGlue` at `:452-457` and therefore renders at preferred width. Two controls in the same flow, rendered at radically different widths, with no stated reason. §Anti-Habituation says "there is deliberately no default / recommended action"; the biggest button on the card is the one that is not a decision.

**MINOR V-4 — the tier badge's fill is a guaranteed no-op.** `paintComponent` fills `Colors.cardSurface` (`:680-681`) onto a card whose `background` is also `Colors.cardSurface` (`:712`). The 6 px rounded corners never appear. Harmless and spec-conformant (the spec assigns `cardSurface` as the badge background role), but it means the badge rests entirely on its 1 px outline and its text, and the paint-time re-read at `:678-681` is dead theme-handling code. Worth knowing before someone "fixes" the badge later.

**Score rationale:** the security-critical visual contract is met in full and the redundancy requirements are genuinely satisfied. Three independent hierarchy/weight problems — all spec-permitted, all working against the spec's own stated goals — keep it off 4.

---

### Pillar 3: Color (4/4)

**Every colour rule in the contract is honoured, verified by grep and by reading.**

| Rule | Evidence |
|------|----------|
| No `Color(0x…)` literal | grep `Color(0x` on `ToolApprovalCard.kt` → **0 hits** |
| No `Font(...)` constructor, no hardcoded px size | grep `= Font(` → **0 hits**; grep `deriveFont` → **0 hits** |
| No legacy `UiTheme` import | grep `UiTheme` → **0 hits** (`ChatPanel`/`ActionCard` correctly left alone per Rule 5) |
| `primary` (Burp orange) absent | grep `Colors.primary` → **0 hits**; `primaryButton` not used; plain `JButton` at `:618` |
| `statusError` not on `Deny` | `decisionButton` (`:613-624`) sets font, focus paint, tooltip and listener — no foreground/background |
| Tokens read at call time, never cached | every reference is `DesignTokens.Colors.x` inside `applyTheme()` (`:711-762`), which is re-run from `updateUI()` (`:421-428`) behind the `initialized` guard (`:237`) |
| Colour never carries text | badge is outlined (`:689-693`), text is `onSurface` (`:688`); the only coloured glyph is the outcome mark (`:756-757`), which is redundant with the verb |
| Status budget, all redundant | `statusWarning` → strip + badge border + repeat caption (redundant with badge text, button count, caption text); `statusError` → strip + badge border + denied glyph (redundant with badge text and verb); `statusSuccess` → approved glyph + unreachable `AUTO` badge; `borderSubtle` → every resolved strip (redundant with the presence of an outcome row) |

**60/30/10:** `primary` is deliberately 0 % on this card; the card is entirely the 30 % band (`cardSurface` body, `inputBackground` model regions, `border`/`borderSubtle` outlines) sitting on the transcript's 60 % `surface`, which it does not repaint. That is the contract's declared distribution for this component. Recorded here so the absence of accent is not later "corrected" by someone applying the global 60/30/10 rule to a single component.

**Contrast, stated with the same honesty the spec uses.** The one hazard the spec identified — a filled tier pill, white on `statusWarning` ≈ 2.70:1 — was resolved exactly as specified, by outlining. The outlined badge reuses `Label.foreground` over `Table.background`, the same pairing every other label in the extension depends on, so it adds no new obligation. **No contrast number is claimed for Burp's actual L&F, because none was measured.**

**MINOR CL-1 (routed to V-2 for action) — `onSurfaceVariant` carries four of the card's seven text roles.** Tier reason, trust label, both footers and the outcome timestamp all resolve to `Label.disabledForeground`. For the record: the *fallback* literal `Color(0x666666)` on a `#F5F5F5` light card computes to **5.27:1** (passes AA); but the fallback is not what ships, since Burp defines the key, and a disabled-text role is by construction chosen to sit *below* normal-text contrast. This is a Phase-09 token-role property surfaced by this card, not a Phase-22 colour violation — which is why Colour scores 4 and the actionable part lives under Visuals.

**Score rationale:** 4/4 is earned, not given. Zero literals, the named anti-analog avoided, every status use provably redundant, the identified contrast hazard resolved as specified, and both themes handled through call-time resolution plus a guarded `updateUI()`. This is stronger colour discipline than the surrounding codebase.

---

### Pillar 4: Typography (3/4)

**Role discipline is exact.** Counted across the file: `Typography.body` ×6, `caption` ×8, `label` ×1, `sectionTitle` ×1, plus `mono` supplied through `applyFieldStyle` / `applyAreaStyle`. Exactly the 5 declared roles, **zero `deriveFont` calls**, zero new derivations. The one sanctioned exception — the session-list `Awaiting approval` marker at `ChatPanel.kt:1829` — matches its sibling's `label.font.deriveFont((size - 2))` exactly as specified, rather than importing a second type scale into that column.

Assignment is correct throughout: `sectionTitle` on the heading, `body` on the title / buttons / toggle / outcome glyph, `caption` on the four caption rows plus the badge and timestamp, `label` (bold base) on the outcome verb, `mono` on 100 % of model text and nothing else. Sizes are multipliers of `Label.font`, so the card follows the OS accessibility setting — which is why the caps in §Args Preview are expressed in characters, not pixels.

**WARNING T-1 — the overflow contract is applied to 3 of 5 qualifying rows.**
§Typography requires an `<html>` prefix on extension-derived label rows long enough to clip, because `helpLabel` returns a plain `JLabel` (which does not wrap) and the transcript sets `HORIZONTAL_SCROLLBAR_NEVER`. Implemented on the preview footer (`:862`), the ceiling footer (`:865`) and the compact outcome verb (`:557`). **Not** implemented on:

| Row | String | Chars | Measured preferred = minimum width |
|-----|--------|-------|------------------------------------|
| 10 — `sessionScopeLabel` (`:96-97`, `:501`) | `"Session" means this chat. Approvals are forgotten…` | 117 | **615 px** |
| 2 — `tierReasonLabel`, `CONFIRM_EACH` (`:94-95`, `:489`) | `This tool is approved one call at a time…` | 88 | **445 px** |
| 2 — `tierReasonLabel`, `CONFIRM` (`:93`) | `Approving for the session applies…` | 74 | 372 px |

Both omitted rows appear on **every** pending card. For a non-HTML `JLabel`, `BasicLabelUI.getMinimumSize` returns the preferred size, so these rows cannot shrink; with an `<html>` prefix the minimum drops to the longest-word span — **measured 67 px** for the same 100-character string. That single missing prefix is what sets the card's hard floor. See **S-1**.

**WARNING T-2 — the recorded reason for the omission is contradicted by the shipped code.**
`22-06-SUMMARY.md:93` records that row 10 was left unwrapped because wrapping it "would install the HTML renderer on a `JLabel` in the exact card the anti-spoofing test probes". But `truncationFooter` **is** a `JLabel` on a pending card, and `:862` assigns it `"<html>Showing the first…"` whenever the preview truncates. Measured on Temurin 21 headless:

```
JLabel.setText("Showing the first 3200 of 41003 characters. …")           -> clientProperty("html") = null
JLabel.setText("<html>Showing the first 3200 of 41003 characters. …")     -> clientProperty("html") = NON-NULL
JLabel("<html>Blocked automatically — you denied this tool for this chat") -> clientProperty("html") = NON-NULL
```

`ToolApprovalCardTest.modelSuppliedTextNeverInstallsTheHtmlRenderer` (`:99-109`) asserts that property is `null` on **every** `JLabel` in the tree. It passes only because the fixture's args are `{"host":"evil.example"}` — 23 characters (`ToolApprovalCardTest.kt:287`) — so the footer never truncates and never takes the prefix. So the assertion is already broader than the spec's own T-2 exemption and broader than shipped behaviour, and it is what prevented a correct layout fix. Fix the test and the two rows together; `modelSuppliedTextIsNeverConcatenatedIntoExtensionText` (`:128-143`) already asserts the property that actually carries the security guarantee (no model substring in any `JLabel`/`AbstractButton`, no model substring in any tooltip).

**Score rationale:** role discipline is flawless and would justify 4. The declared overflow contract — the one paragraph of §Typography that the checker explicitly flagged as never having been through a checker pass — is 60 % implemented, and the omission has a measured, user-visible layout consequence.

---

### Pillar 5: Spacing (2/4)

**The inset table is implemented exactly, row for row, on both card kinds.** Verified against §Row insets and §"Insets — no compact-specific value":

| Rows | Spec | Implementation |
|------|------|----------------|
| 0–4 (header, title, tier reason, repeat, trust) | `Insets(0,0,xs,0)` | `tight` at `:446`, applied `:486-495` ✓ |
| 5, 7 (model regions) | `Insets(0,0,sm,0)` | `roomy` at `:447`, applied `:496`, `:498` ✓ |
| 6, 8, 10 | `Insets(0,0,xs,0)` | `:497`, `:499`, `:501` ✓ |
| 9 (buttons) | `Insets(sm,0,0,0)` | `:500` ✓ |
| C0–C2, C4, C6 | `Insets(0,0,xs,0)` | `:467-473` ✓ |
| C3, C5 | `Insets(0,0,sm,0)` | `:470`, `:472` ✓ |

Horizontal insets are 0 everywhere; horizontal padding is declared once in the card's `EmptyBorder(sm, md, sm, md)` (`:722-727`) ✓. Button-row gaps are `sm` / **`lg` between the poles** / `sm` (`:524`, `:529`, `:532`) ✓. `ActionCard`'s off-grid 6/10 insets were correctly **not** copied. `preferredSize`, `maximumSize` and `JTextArea.rows` are never set — grep returns 0 for all three. The two off-grid values present are exactly the two the spec pre-authorised: the 3 px accent strip (`:45`, `:719`) and the badge's transitively-adopted `EmptyBorder(2,6,2,6)` (`:51-54`, `:692`).

**BLOCKER S-1 — the sizing half of the Layout & Sizing Contract fails, and it makes a security control unreachable.**

Measured chain, all values from Temurin 21 headless at Metal / Dialog-12 (Burp's base font is larger, so real numbers are ~8–10 % higher):

```
widest non-shrinkable row  = row 10 sessionScopeLabel   615 px  (min == preferred, plain JLabel)
card chrome                = 3 strip + 1 line + 12 + 12 + 1     =  29 px
card hard minimum width                                          = 644 px
transcript scroll padding  = 8 + 8                               =  16 px
=> transcript viewport must be >= 660 px or content is clipped
```

And the clipping is unrecoverable in place:
- The transcript is the **right component of a user-draggable `JSplitPane.HORIZONTAL_SPLIT`** (`MainTab.kt:251-257`), `resizeWeight = 0.2`, with **no `minimumSize` set on `chatPanel.root`**.
- `ChatPanel.kt:1852` sets `horizontalScrollBarPolicy = HORIZONTAL_SCROLLBAR_NEVER`, so `JViewport` sizes the view to `max(preferred, extent)` and the overflow is off the right edge with **no scrollbar, no shift-wheel, no wrap**.
- `SessionPanel.addComponent`'s wrapper (`ChatPanel.kt:1867-1878`) caps **height** only; it does nothing for width.

Failure order as the viewport narrows: row 10's tail first (it is the widest row), then — below roughly 520 px of viewport — `Approve for session`, then `Approve once`. **Credit where due:** the spec's safe-action-first ordering means `Deny` is the *last* control to be lost, so the degradation is fail-safe rather than fail-open. And dragging the divider back recovers. But a user who narrows the panel sees an approval card with cut-off buttons, no scrollbar and no explanation.

*Fix, ranked:* (a) `<html>`-prefix rows 2 and 10 — measured to take row 10's minimum from 615 px to 67 px and the card's floor to ≈ 516 px, gated on the test fix in **T-2**; (b) set `chatPanel.root.minimumSize` in `MainTab.kt` so the divider cannot pass the floor; (c) optionally give `buttonRow` a two-row fallback below a threshold width — noting that a naive `FlowLayout` will wrap visually but report a one-row preferred height, which the height-capping wrapper would then clip.

**WARNING S-2 — zero internal padding in both model regions.** Measured:

```
after applyAreaStyle:  JTextArea  margin = [0,0,0,0]   insets = [1,1,1,1]
after applyFieldStyle: JTextField margin = [0,0,0,0]   insets = [1,1,1,1]
(JTextField with the L&F's own default border, for comparison:  insets = [2,2,2,2])
```

Both builders (`Components.kt:484-505`) replace the L&F border with a bare `LineBorder(border, 1, true)` and set no margin, so the model's monospace text renders flush against the 1 px line on all four sides — and the replacement actively *removes* the padding the L&F's field border supplied. Containment is this card's **primary** trust channel and, per §T-4, in light theme that 1 px line is one of only two load-bearing signals. A box with no padding reads as a rule around text, not as a container. Inherited from Phase 09, so not authored here, but this card is its highest-stakes consumer and the fix is local and on-grid: set `margin = Insets(Spacing.xs, Spacing.xs, Spacing.xs, Spacing.xs)` on both after the builder calls at `:748-749`.

**WARNING S-3 — `overflowRow` has a 0 px internal gap.** `:449-459` stacks `truncationFooter` directly on `showAllRow` in a `BoxLayout.Y_AXIS` with no spacer, so the footer sentence and the `Show all {N} characters` button touch, where every other adjacent pair on the card is separated by 4 px. *Fix:* `overflowRow.add(Box.createRigidArea(Dimension(0, DesignTokens.Spacing.xs)))` between them.

**Score rationale:** the declared inset table is implemented perfectly and the grid discipline is better than the surrounding code. But §"Layout & Sizing Contract" is two halves, and the sizing half has a measured failure that puts a security control out of reach at a width the user can reach with one drag. 2/4 reflects a pillar where the specified part is right and the emergent behaviour is wrong.

---

### Pillar 6: Experience Design (3/4)

**State coverage is the strongest part of this phase.** All eleven states from §"All states" are accounted for, including the two the spec marked optional:

| State | Shipped | Evidence |
|-------|---------|----------|
| 1 Suppressed — auto | no card, by construction | `addSuppressedDecisionRow` early-returns on anything but the two session decisions (`ChatPanel.kt:2399`) |
| 2 Suppressed — session-approved | **shipped** (FLAG-22-03 was optional) | `compact()` `:777-802`; `ChatPanel.kt:2402` |
| 3 Suppressed — session-denied | shipped | same |
| 4 Pending `CONFIRM` | 4 buttons, args collapsed | `:522-534`, `:286` |
| 5 Pending `CONFIRM_EACH` | 2 buttons, **args expanded** | `initiallyExpanded = tier == SecTier.CONFIRM_EACH` at `:275`, deliberately not a constructor parameter so no call site can defeat it |
| 6–9 Explicit outcomes | outcome row replaces buttons in the same grid cell | `resolve()` `:399-418`, `getConstraints` reuse at `:407` |
| 10 Implicit denial (new message) | shipped with its own copy | `:896-910` |
| 11 Container destroyed | audit event is the record | `resolvePending` `:2572-2607` |
| Pending, background session | `Awaiting approval` marker | `ChatPanel.kt:1823-1831` — the preferred FLAG-22-02 variant, not the cheap fallback |
| Pending, on return | auto-scrolled into view, focus unmoved | `scrollToComponent` `ChatPanel.kt:1918-1921`, called at `:1279` |

Interaction discipline is equally thorough. Buttons are **removed, not disabled** (`:408`) — grep for `isEnabled = false` returns 0. `resolve()` is idempotent (`:388-390`) and `ChatPanel` removes the pending record *first* in both resolution paths (`:2510`, `:2578`), so a click racing a teardown cannot double-resolve. Focus is restored to the input area only when the card actually owns it (`:394-397`), before the row is removed. Grep for `defaultButton`, `setMnemonic`, `VK_ESCAPE`, `requestFocusInWindow`, `registerKeyboardAction` and `getInputMap` on the card returns **0 hits** — every forbidden binding in §Keyboard and Focus is absent. `isFocusPainted = true` on decision buttons (`:621`) and `false` on both non-decision toggles (`:343`, `:350`), exactly as specified. The one-pending-card-per-session invariant is enforced rather than assumed, and violating it retires the stale card through the single `resolvePending` entry point plus a user-visible error (`ChatPanel.kt:2453-2457`).

**WARNING X-1 — the two model regions have no accessible name.** (Priority fix #5.) The card **root's** `accessibleDescription` is correct, satisfies all three of §Accessibility's conditions and is asserted three ways with `endsWith` (`ToolApprovalCardTest.kt:213-236`) — and the NPE found during implementation proves the assertion is non-vacuous. But that covers the *root*. `toolIdField` is a deliberate Tab stop and `argsArea` is focusable whenever visible; grep for `accessibleName` and `labelFor` on this card returns **0 hits**. A screen-reader user who tabs in from the input area lands on the field and hears the model's string with no attribution — T-22-27's exact shape, on the one path the root-description mitigation does not reach. The fix costs no new copy: `trustLabel.labelFor = toolIdField` routes the contracted T-5 sentence into the field's accessible name via `ACCESSIBLE_LABELED_BY` while the model string stays the field's *value*, and `labelFor` without `setDisplayedMnemonic` installs no key binding, so §Forbidden bindings is untouched.

**WARNING X-2 — no degenerate-args handling.** Cross-reference **C-1**: a zero-args call renders a live disclosure control offering `0 characters` and, when expanded, an empty bordered region under the trust caption.

**MINOR X-3 — `resolve()` carries no EDT assertion.** `ChatPanel.resolveToolDecision` opens with `assertEdt()` (`:2508`), but the card is the reusable unit and `resolve()` mutates the component tree and calls `revalidate()` / `repaint()` (`:417-418`) with no guard of its own. A future caller reaching it off the EDT would corrupt layout silently. Add the assertion, or at minimum a KDoc contract line.

**MINOR X-4 — theme-switch re-application order, traced and found benign; recording it so it stays benign.** `SwingUtilities.updateComponentTreeUI` calls the parent's `updateUI()` **before** the children's, so `applyTheme()` (`:711-762`) runs first and each child's UI delegate then re-installs its own defaults. This is safe today only because `LookAndFeel.installColorsAndFont` / `installBorder` overwrite a value only when it is `null` or a `UIResource`: `LineBorder` is not a `UIResource` (the containment border survives), and `statusWarning` / `statusError` / `statusSuccess` fall through to plain `Color` literals (`DesignTokens.kt:177-186`) while the derived fonts are plain `Font`s, so the repeat-counter colour and the outcome-glyph colours survive. The one property that *is* re-derived is `argsArea.background`, which flips from `TextField.background` to `TextArea.background` — equal in practice. **If anyone later gives `statusWarning` a real `UIManager` key, the repeat counter and the outcome glyph will silently lose their colour on the first theme switch.** Worth a comment at `:736-737` and `:756-757`.

**Score rationale:** state coverage, lifecycle wiring and keyboard discipline are excellent, and both optional flags were shipped rather than defaulted by omission. The accessibility gap is a real hole in one of the card's own declared threat mitigations, on a path the test suite does not reach.

---

## Method and Measurement Appendix

No screenshots exist because there is no renderable web surface; the equivalent evidence is headless measurement. All figures below were produced on Temurin JDK 21, `java.awt.headless=true`, default Metal L&F, `Label.font = Dialog 12`. **Burp's L&F uses a larger base font, so real-world widths will be ~8–10 % higher than these; they are a floor, not a ceiling.**

| Measurement | Value | Used by |
|-------------|-------|---------|
| `JLabel` text starting `<html>` → `getClientProperty("html")` | **non-null** (renderer installed) | T-2 |
| Same string without the prefix | `null` | T-2 |
| Row 10 `sessionScopeLabel`, 117 ch, caption | preferred **615 px**, minimum **615 px** | S-1, T-1 |
| Same string with an `<html>` prefix | minimum **67 px** | S-1 fix sizing |
| Row 2 `CONFIRM_EACH` tier reason, 88 ch, caption | 445 px | T-1 |
| Trust label, 58 ch, caption | 305 px | — |
| Compact outcome verb, 58 ch, bold | 363 px | (already wrapped) |
| Heading, `sectionTitle` | 193 px | V-1 |
| Four-button row incl. 8/16/8 gaps | **487 px** | S-1 |
| Two-button row incl. 16 px gap | 193 px | — |
| Args toggle button, preferred | 251 px | V-3 |
| Card chrome (strip + line + 12/12 + line) | 29 px | S-1 |
| **Card hard minimum width** | **644 px** | S-1 |
| **Transcript viewport floor before clipping** | **660 px** | S-1 |
| `JTextArea` after `applyAreaStyle` | `margin [0,0,0,0]`, `insets [1,1,1,1]` | S-2 |
| `JTextField` after `applyFieldStyle` | `margin [0,0,0,0]`, `insets [1,1,1,1]` | S-2 |
| `JTextField` with the L&F's own border, for contrast | `insets [2,2,2,2]` | S-2 |

Grep-based conformance checks on `ToolApprovalCard.kt`, all returning **0 hits**: `Color(0x`, `= Font(`, `deriveFont`, `UiTheme`, `JScrollPane`, `preferredSize`, `maximumSize`, `.rows =`, `Colors.primary`, `isEnabled = false`, `defaultButton`, `setMnemonic`, `VK_ESCAPE`, `requestFocusInWindow`, `registerKeyboardAction`, `getInputMap`, `accessibleName`, `labelFor`.

---

## Files Audited

**Under audit**
- `src/main/kotlin/com/six2dez/burp/aiagent/ui/components/ToolApprovalCard.kt` (937 lines, read in full)
- `src/main/kotlin/com/six2dez/burp/aiagent/ui/ChatPanel.kt` (2926 lines — card insertion `:2465-2496`, compact rows `:2395-2409`, resolution `:2505-2545`, `resolvePending` `:2572-2607`, `switchToSession` scroll `:1268-1280`, `ChatSessionRenderer` marker `:1795-1834`, `SessionPanel.addComponent` / `scrollToComponent` `:1867-1921`, scroll policy `:1852`)
- `src/test/kotlin/com/six2dez/burp/aiagent/ui/ToolApprovalCardTest.kt` (327 lines, read in full)

**Design system and contract dependencies**
- `src/main/kotlin/com/six2dez/burp/aiagent/ui/design/DesignTokens.kt` (read in full)
- `src/main/kotlin/com/six2dez/burp/aiagent/ui/design/Components.kt` (`helpLabel`, `toolBadge`, `applyFieldStyle`, `applyAreaStyle`)
- `src/main/kotlin/com/six2dez/burp/aiagent/ui/MainTab.kt` (`:245-270` split-pane composition)
- `src/main/kotlin/com/six2dez/burp/aiagent/ui/ToolCallParser.kt` (`ParsedToolCall`, `resolveToolArgs`)
- `src/main/kotlin/com/six2dez/burp/aiagent/mcp/ToolApprovalGate.kt` (`sanitizeBlock`)
- `src/main/kotlin/com/six2dez/burp/aiagent/mcp/McpToolCatalog.kt` (`McpToolDescriptor`)

**Planning artifacts**
- `22-UI-SPEC.md` (1280 lines, read in full — the binding baseline)
- `22-CONTEXT.md` (D-01…D-14), `22-06-PLAN.md` intent, `22-06-SUMMARY.md`, `22-07-SUMMARY.md`, `22-08-SUMMARY.md`, `22-HUMAN-UAT.md`, `22-VERIFICATION.md`

**Not audited (out of this phase's UI scope, per the spec):** the existing `ChatMessagePanel` markdown path (FLAG-22-07, deferred to Phase 26 / DOC-03), and the gate / state machine / audit emission, which are AWT-free.
