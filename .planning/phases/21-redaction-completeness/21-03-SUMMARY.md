---
phase: 21-redaction-completeness
plan: 03
subsystem: privacy
tags: [kotlin, swing, redaction, privacy-mode, mcp, montoya]

# Dependency graph
requires:
  - phase: 15-secret-tripwire
    provides: "SecretTripwire.detectAndBuild on the FINAL post-redaction MCP string (PRIV-03, G1/G8) — preserved verbatim by this plan"
  - phase: 13-context-preview
    provides: "ContextPreviewDialog.confirm as the single pre-flight gate whose privacy hint this plan corrects"
provides:
  - "McpToolContext.redactIfNeeded calls Redaction.apply unconditionally — the MCP half of D-06; OFF is now expressed once, as a policy, inside Redaction"
  - "All four D-07 user-facing OFF strings corrected: ChatPanel.privacySummary, ContextPreviewDialog.privacyModeHint, PrivacyPill tooltip, and all four SettingsPanelActions OFF arms"
  - "ContextPreviewDialog.confirm/privacyModeHint carry a no-default customPatternsConfigured: Boolean"
  - "SettingsPanelActions.refreshPrivacyNotice exposes a single shared offClause across all four OFF risk combinations"
affects: [21-06 (D-05 custom-pattern loop move), 26-docs (DOC-03 GitBook + security advisory + HelpConfigPanel)]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Caller-side privacy-mode short-circuits are deleted, not flagged — the mode is a policy, resolved in one place"
    - "User-facing privacy claims are conditioned on live component/settings state rather than asserted unconditionally"
    - "A shared clause local (offClause) keeps sibling warning arms from diverging"

key-files:
  created:
    - .planning/phases/21-redaction-completeness/21-03-SUMMARY.md
  modified:
    - src/main/kotlin/com/six2dez/burp/aiagent/mcp/McpToolContext.kt
    - src/main/kotlin/com/six2dez/burp/aiagent/ui/ChatPanel.kt
    - src/main/kotlin/com/six2dez/burp/aiagent/ui/components/ContextPreviewDialog.kt
    - src/main/kotlin/com/six2dez/burp/aiagent/ui/components/PrivacyPill.kt
    - src/main/kotlin/com/six2dez/burp/aiagent/ui/SettingsPanelActions.kt

key-decisions:
  - "customPatternsConfigured on ContextPreviewDialog.confirm has NO default value — one caller today, and a default is how a future caller silently inherits the wrong OFF hint"
  - "refreshPrivacyNotice reads customPatternsArea.text (live JTextArea) rather than the constructor-time settings snapshot, and deliberately does NOT call validateAndCollectCustomPatterns() whose 50 ms per-pattern ReDoS probe must not run on the EDT"
  - "PrivacyPill stays unconditional — updateMode(mode) takes only the mode, so the 'if any are configured' clause carries the truth instead of a signature change"
  - "The three SettingsPanelActions risk tails were re-punctuated (lead-clause + shared offClause + tail) rather than rewritten, so risk semantics are byte-for-byte the same claims"

patterns-established:
  - "D-06 structural enforcement: a privacy-mode branch at a call site is deleted so no caller can hold a private copy of the OFF decision"
  - "D-07 conditioned wording: privacy claims that depend on user configuration read that configuration rather than asserting a worst case"

requirements-completed: [PRIV-05, PRIV-06]

# Metrics
duration: 17min
completed: 2026-08-11
---

# Phase 21 Plan 03: MCP OFF bypass + D-07 privacy strings Summary

**Deleted the `McpToolContext` caller-side `PrivacyMode.OFF` short-circuit so OFF is resolved once as a `RedactionPolicy`, and corrected all four user-facing strings that claimed OFF means "no redaction" — three of them now conditioned on whether custom patterns are actually configured.**

## Performance

- **Duration:** 17 min
- **Started:** 2026-08-11T13:06:00Z
- **Completed:** 2026-08-11T13:23:31Z
- **Tasks:** 3
- **Files modified:** 5

## Accomplishments

- **D-06 (MCP half) closed.** `McpToolContext.redactIfNeeded` no longer branches on the privacy mode; it calls `Redaction.apply(raw, RedactionPolicy.fromMode(privacyMode), stableHostSalt = hostSalt)` unconditionally. `grep -rn 'PrivacyMode.OFF' McpToolContext.kt` now prints nothing.
- **D-07 (as AMENDED, four strings) closed.** `ChatPanel.privacySummary`, `ContextPreviewDialog.privacyModeHint`, `PrivacyPill`'s OFF tooltip and all four `SettingsPanelActions` OFF arms now describe OFF as *built-in redaction disabled*, not *no redaction*.
- **No self-contradiction possible in the settings panel.** The four OFF arms share one `offClause` local, so the wording cannot drift between risk combinations.
- **Scope held exactly.** Only the 5 files in `files_modified` changed. Every do-not-touch `PrivacyMode.OFF` check (`McpToolLegacy.kt:344`, `McpToolExecutorImpl.kt:479`, `ActiveAiScanner.kt:979`, `AdaptivePayloadEngine.kt:51-53`, `SettingsPanelMcpTabs.kt:607`, `MainTab.kt:605-607`, `PrivacyPill`'s own OFF check) and `PassiveAiScannerAnalysis.kt:394` (plan 21-01) are untouched. `HelpConfigPanel.kt:26` (`"OFF (raw data)"`) is untouched, deferred to DOC-03 / Phase 26.

## The four D-07 strings, verbatim (for manual review without re-reading the Swing files)

### 1. `ui/ChatPanel.kt` — `privacySummary(mode)` OFF arm

Before:

```
Privacy: OFF (no redaction)
```

After — conditioned on `getSettings().customRedactionPatterns.isNotEmpty()`:

```
Privacy: OFF (built-in redaction disabled; your custom patterns still apply)
Privacy: OFF (built-in redaction disabled; no custom patterns configured)
```

STRICT and BALANCED arms untouched; the `Privacy: OFF (…)` prefix shape is preserved so the label still parallels its siblings.

### 2. `ui/components/ContextPreviewDialog.kt` — `privacyModeHint(mode, customPatternsConfigured)` OFF arm

Before:

```
  (no redaction; raw traffic will be sent)
```

After — conditioned on the new `customPatternsConfigured` parameter:

```
  (built-in redaction off; only your custom patterns are applied)
  (no redaction; raw traffic will be sent)
```

Both variants keep the **two leading spaces** that separate the hint from the mode name. The not-configured variant is intentionally the unchanged original — with no patterns configured, OFF really does send raw traffic. STRICT (`  (cookies, tokens, and hosts redacted)`) and BALANCED arms untouched.

### 3. `ui/components/PrivacyPill.kt` — OFF tooltip

Before:

```
OFF mode sends raw traffic without redaction.
```

After — unconditional but true in both states:

```
OFF mode disables built-in redaction; custom redaction patterns, if any are configured, still apply.
```

`fun updateMode(mode: PrivacyMode)`'s signature is unchanged, and the OFF arm's `text = ""` / `isVisible = false` value-display logic is unchanged — only the tooltip string moved.

### 4. `ui/SettingsPanelActions.kt` — all four OFF arms of `refreshPrivacyNotice`

New shared clause (`offClause`), selected by whether `customPatternsArea.text` has at least one non-blank line:

```
Built-in redaction is disabled; only your custom patterns are applied to MCP and prompts.
Built-in redaction is disabled; raw traffic may reach MCP and prompts.
```

The four arms, in declaration order, each rendered as *bold lead clause + `offClause` + risk tail*:

| # | Level | Rendered message (shown with the not-configured `offClause`) |
|---|-------|--------------------------------------------------------------|
| 1 | RISK | `<b>Privacy OFF + Audit logging OFF + Active Scanner ON.</b> Built-in redaction is disabled; raw traffic may reach MCP and prompts. There is no audit trail, and live payloads go to targets.` |
| 2 | RISK | `<b>Privacy OFF + Audit logging OFF.</b> Built-in redaction is disabled; raw traffic may reach MCP and prompts. Without audit logs, traceability and data-protection guarantees are reduced.` |
| 3 | RISK | `<b>Privacy OFF + Active Scanner ON.</b> Built-in redaction is disabled; raw traffic may reach MCP and prompts. The active scanner sends payloads to real targets.` |
| 4 | WARN | `<b>Privacy mode is OFF.</b> Built-in redaction is disabled; raw traffic may reach MCP and prompts.` |

Risk levels are unchanged (RISK / RISK / RISK / WARN). The STRICT arm (`STRICT anonymizes hosts in AI prompts but does not prevent the active scanner from sending real requests to targets.`), the `else -> null to null` arm and the `if (level != null && htmlMessage != null)` dispatch below the `when` are all unchanged.

## Task Commits

1. **Task 1: Delete the McpToolContext OFF short-circuit (D-06, MCP half)** — `7e83436` (fix)
2. **Task 2: Correct the ChatPanel, ContextPreviewDialog and PrivacyPill OFF strings (D-07)** — `7ef4883` (fix)
3. **Task 3: Reword all four SettingsPanelActions OFF arms as one unit (D-07)** — `ad36c5a` (fix)

**Plan metadata:** committed with this SUMMARY (worktree mode — STATE.md / ROADMAP.md are written centrally by the phase orchestrator after the wave merges).

## Files Created/Modified

- `src/main/kotlin/com/six2dez/burp/aiagent/mcp/McpToolContext.kt` — `redactIfNeeded` now calls `Redaction.apply` unconditionally; the `PRIV-06 / D-06` comment records why the branch was deleted rather than kept behind a fourth `RedactionPolicy` flag.
- `src/main/kotlin/com/six2dez/burp/aiagent/ui/ChatPanel.kt` — `privacySummary` OFF arm conditioned; the single `ContextPreviewDialog.confirm` call site passes `customPatternsConfigured` as a named argument.
- `src/main/kotlin/com/six2dez/burp/aiagent/ui/components/ContextPreviewDialog.kt` — `confirm` and `privacyModeHint` take `customPatternsConfigured: Boolean` (no default); OFF hint conditioned.
- `src/main/kotlin/com/six2dez/burp/aiagent/ui/components/PrivacyPill.kt` — OFF tooltip reworded; signature and OFF check unchanged.
- `src/main/kotlin/com/six2dez/burp/aiagent/ui/SettingsPanelActions.kt` — `refreshPrivacyNotice` gains `customPatternsConfigured` + `offClause` locals; four OFF arms rewritten as one unit.

## Decisions Made

- **`customPatternsConfigured` has no default value on `ContextPreviewDialog.confirm`.** Followed the plan exactly. There is one caller (`ChatPanel.kt:310`) and a default is how a second caller silently inherits the wrong pre-flight claim. Verified: `grep -c 'customPatternsConfigured: Boolean = '` returns 0.
- **`refreshPrivacyNotice` reads `customPatternsArea.text.split('\n').any { it.isNotBlank() }`.** Matches the split-and-filter idiom `validateAndCollectCustomPatterns()` at `SettingsPanelSettingsIO.kt:218-223` already uses, so the notice agrees with what would actually be persisted. It deliberately does **not** call that validating collector: its `SafeRegex.isPatternSafe` 50 ms per-pattern ReDoS probe would run on the EDT on every notice refresh.
- **T-21-15 (uninitialised `customPatternsArea`) checked and found not to apply.** `SettingsPanel`'s only `init { initUiWiring() }` block is the **last** member of the class (`SettingsPanel.kt:492`), while `customPatternsArea` is declared at `SettingsPanel.kt:177`. Kotlin runs property initialisers and `init` blocks in declaration order, so the field is assigned before any construction-time path (`SettingsPanelInit.kt:325-326`, `:401-402`) can reach `refreshPrivacyNotice`. A defensive read was therefore **not** added — it would have been dead code. The reasoning is recorded in-file so a future reader does not re-derive it.
- **The three SettingsPanelActions risk tails were re-punctuated, not rewritten.** Each tail keeps its original claim, promoted to its own sentence now that `offClause` sits between the bold lead and the tail (e.g. `…, with no audit trail and live payloads going to targets.` → `There is no audit trail, and live payloads go to targets.`). No risk claim was added, removed or softened.

## Deviations from Plan

None — plan executed exactly as written. No deviation rule fired; no auto-fixes were needed.

## Issues Encountered

- **Comment wording collided with a verification grep (caught before commit).** The first draft of the `PRIV-06 / D-06` comment in `McpToolContext.kt` quoted the deleted branch literally (`if (privacyMode == PrivacyMode.OFF) raw`), which would have made the plan's `grep -rn 'PrivacyMode.OFF' McpToolContext.kt` verification print a line and `grep -c 'privacyMode == PrivacyMode.OFF'` return 1. Reworded to describe the branch without reproducing the token. Same class of collision was avoided in `ChatPanel.kt` (the comment must not contain the phrase `no redaction`, which the plan verification requires to be absent from that file) and in `SettingsPanelActions.kt` (the comment must not contain `Built-in redaction is disabled`, which must appear exactly twice). All greps re-run and green afterwards. No code behaviour was involved.

## Verification

All plan-level gates run from the worktree with the mandatory JDK 21 prefix:

- `JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test ktlintCheck detekt -q` — exits 0 (run after each of the three tasks; the MCP-scoped `--tests "com.six2dez.burp.aiagent.mcp.*"` variant was also run for Task 1).
- `git diff --stat -- detekt-baseline.xml` — empty after every task. QUAL-07 respected; no baseline entry added and no `@Suppress` needed.
- `grep -rc 'no redaction' ChatPanel.kt PrivacyPill.kt` — 0 for both.
- `grep -rn 'PrivacyMode.OFF' McpToolContext.kt` — prints nothing.

Per-task acceptance criteria, all green:

| Grep | Expected | Actual |
|------|----------|--------|
| `privacyMode == PrivacyMode.OFF` in `McpToolContext.kt` | 0 | 0 |
| `Redaction.apply(raw, RedactionPolicy.fromMode(privacyMode)` in `McpToolContext.kt` | 1 | 1 |
| `import com.six2dez.burp.aiagent.redact.PrivacyMode` in `McpToolContext.kt` | 1 | 1 |
| `SecretTripwire` in `McpToolContext.kt` | unchanged (3) | 3 |
| `D-06` in `McpToolContext.kt` | ≥ 1 | 1 |
| `Privacy: OFF (no redaction)` in `ChatPanel.kt` | 0 | 0 |
| `built-in redaction disabled` in `ChatPanel.kt` | 2 | 2 |
| `customRedactionPatterns.isNotEmpty()` in `ChatPanel.kt` | 2 | 2 |
| `customPatternsConfigured` in `ContextPreviewDialog.kt` | ≥ 3 | 4 |
| `customPatternsConfigured: Boolean = ` in `ContextPreviewDialog.kt` | 0 | 0 |
| `  (cookies, tokens, and hosts redacted)` in `ContextPreviewDialog.kt` | 1 | 1 |
| `OFF mode sends raw traffic without redaction` in `PrivacyPill.kt` | 0 | 0 |
| `OFF mode disables built-in redaction` in `PrivacyPill.kt` | 1 | 1 |
| `fun updateMode(mode: PrivacyMode)` in `PrivacyPill.kt` | 1 | 1 |
| `raw traffic may reach MCP and prompts` (lowercase r) in `SettingsPanelActions.kt` | 1 | 1 |
| `Built-in redaction is disabled` in `SettingsPanelActions.kt` | 2 | 2 |
| `offClause` in `SettingsPanelActions.kt` | ≥ 5 | 5 |
| `customPatternsArea` in `SettingsPanelActions.kt` | ≥ 2 | 4 |
| `SubtleNotice.Level.RISK` / `.WARN` in `SettingsPanelActions.kt` | 3 / 1 | 3 / 1 |
| `STRICT anonymizes hosts in AI prompts` in `SettingsPanelActions.kt` | 1 | 1 |

**Manual-only (recorded in `21-VALIDATION.md` under "Manual-Only Verifications"):** all four strings are Swing labels/tooltips with no UI test harness in this project. The automated gate is compilation plus the greps above; the wording itself is verified by code review against the verbatim quotes in this summary.

## Note for anyone comparing audit volumes after plan 21-06

`SecretTripwire.detectAndBuild` in `McpToolContext.redactIfNeeded` still scans the **final** post-redaction string and still never blocks — unchanged by this plan. But once D-05 lands in plan 21-06 (custom-pattern loop moved outside the `policy.redactTokens` branch), that final string under `PrivacyMode.OFF` will have had the user's custom patterns applied to it. **Tripwire hit counts under OFF may therefore drop slightly.** That is correct and intended: fewer secrets survive redaction to be detected. It is not a regression in tripwire coverage.

Today the deletion is behaviour-preserving: `RedactionPolicy.fromMode(OFF)` sets all three flags false, so `Redaction.apply` returns its input byte-identically. The deletion is what makes the 21-06 change reach the MCP path at all.

## Requirements status (important — do NOT read this plan as closing PRIV-05 / PRIV-06)

`requirements-completed: [PRIV-05, PRIV-06]` above is copied from the plan's `requirements:` frontmatter per template convention, but **both are phase-wide requirements that plan 21-03 only partially advances**:

- **PRIV-05** — this plan removes one bypass on a live leak path (the MCP half of D-06). The cookie-section redaction and sensitive-key matching that PRIV-05 is actually specified by belong to plans 21-01 / 21-02.
- **PRIV-06** — this plan delivers the D-07 half (the product's description of OFF is now true). The "redaction never fails open" half (D-01…D-04 over-cap truncation) and D-05's custom-pattern loop move belong to plans 21-04 / 21-05 / 21-06.

`REQUIREMENTS.md` was deliberately **not** modified by this worktree agent: the requirements are not satisfied by this plan alone, and sibling wave-1 agents claim the same IDs. The orchestrator owns that write after the phase completes.

## User Setup Required

None — no external service configuration required, no dependency added, no package installed.

## Next Phase Readiness

- **Ready for plan 21-06 (wave 3).** `McpToolContext` no longer holds a private copy of the OFF decision, so moving the custom-pattern loop outside the `redactTokens` branch inside `Redaction.kt` will now reach the MCP path. Plan 21-01 must do the same for `PassiveAiScannerAnalysis.kt:394` — that is the other real short-circuit and is explicitly out of this plan's scope.
- **Ready for DOC-03 / Phase 26.** The in-product strings are now true; the GitBook privacy page, the SEC-04 / PRIV-05 security advisory and `HelpConfigPanel.kt:26` (`"OFF (raw data)"`) are the remaining false-or-stale claims and are all deliberately deferred.
- **One residual, disclosed in the plan's threat model and unchanged:** no unit test covers the MCP half of D-06 — constructing an `McpToolContext` requires a `MontoyaApi` mock, an `McpRequestLimiter` and a `BurpSuiteEdition`. The behaviour it enables is proven where the logic lives (`RedactionTest`'s inverted OFF limb and `PassiveAiScannerPromptRedactionTest.offStillAppliesCustomPatterns`, both plan 21-06). The MCP half is covered here by the source assertion that the branch is absent.
- **No blockers.**

---
*Phase: 21-redaction-completeness*
*Completed: 2026-08-11*
