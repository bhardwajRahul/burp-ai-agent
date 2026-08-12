---
phase: 21-redaction-completeness
plan: 14
subsystem: ui
tags: [kotlin, swing, privacy, redaction, settings-ui, wr-02, d-07]

# Dependency graph
requires:
  - phase: 21-redaction-completeness
    provides: "21-03's four D-07 OFF strings and the shared offClause; 21-10's extract-then-test pattern for Swing logic"
provides:
  - "privacyNoticeFor — a top-level pure composer for the Privacy & Logging advisory, parameterised on the persisted custom-pattern list"
  - "refreshPrivacyNotice reduced to an adapter that reads live toggle state but persisted engine state"
  - "PrivacyNoticeCompositionTest — 9 cases pinning all four OFF arms in both clause directions, plus the STRICT and hidden-notice arms"
affects: [22-docs, 23-release-hardening, privacy-ui, redaction]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Make a defect unrepresentable rather than merely fixed: hoist the composer to top level so the wrong source is out of lexical scope"
    - "When a pure-function extraction makes an honest RED impossible, substitute a recorded mutation gate and say so plainly"

key-files:
  created:
    - src/test/kotlin/com/six2dez/burp/aiagent/ui/PrivacyNoticeCompositionTest.kt
  modified:
    - src/main/kotlin/com/six2dez/burp/aiagent/ui/SettingsPanelActions.kt

key-decisions:
  - "privacyNoticeFor is top-level, not a SettingsPanel extension — an extension would keep customPatternsArea in lexical scope and could silently regress"
  - "The plan's 'grep customPatternsArea returns 0' criterion is unsatisfiable without breaking the Privacy tab; satisfied its intent instead and recorded the defect"
  - "No honest RED was constructible; the mutation gate is the substitute and is labelled as such rather than dressed up as TDD"
  - "The pattern clause now lags until Save, by design — the banner must never overclaim; the 'unsaved patterns are not active' wording belongs to the docs phase"

patterns-established:
  - "Source-of-truth split: live UI state for toggles the user can flip without saving, persisted engine state for anything the banner claims the engine is doing"
  - "Mutation hygiene: commit the implementation first, mutate on top of the committed base, revert with git checkout, verify by checksum"

requirements-completed: [PRIV-06]

# Metrics
duration: 22min
completed: 2026-08-12
---

# Phase 21 Plan 14: WR-02 — OFF Banner Reads Persisted Patterns Summary

**The OFF-mode privacy banner now derives "your custom patterns are applied" from the persisted, `isPatternSafe`-validated list that actually feeds `Redaction.setCustomPatterns`, and the composer was hoisted to top level so the unsaved `JTextArea` is no longer in lexical scope.**

## Performance

- **Duration:** 22 min
- **Started:** 2026-08-12T13:09:00Z
- **Completed:** 2026-08-12T13:31:00Z
- **Tasks:** 2
- **Files modified:** 2 (1 modified, 1 created)

## Accomplishments

- **WR-02 closed structurally, not cosmetically.** `refreshPrivacyNotice` no longer infers protection from `customPatternsArea.text`. It passes `settings.customRedactionPatterns` into a new top-level `privacyNoticeFor`, which physically cannot reach a Swing component.
- **All four OFF arms stayed a single consistent unit.** They still share one `offClause`; not one of the five user-visible strings changed by a single byte.
- **The pin is mutation-verified in both directions.** Mutation M1 kills all six OFF-arm tests; Mutation M2 kills exactly the three empty-list tests, proving the safety-critical direction is independently guarded.

## Task Commits

1. **Task 1: Extract the notice composition into a top-level pure function** — `7edf616` (refactor)
2. **Task 2: Pin both OFF arms against the persisted list** — `7dcfce7` (test)

## Files Created/Modified

- `src/main/kotlin/com/six2dez/burp/aiagent/ui/SettingsPanelActions.kt` — added top-level `privacyNoticeFor(selectedPrivacy, auditOff, activeOn, persistedCustomPatterns)`; reduced `refreshPrivacyNotice` to an adapter; rewrote the D-07 comment block to record the source-of-truth split and deleted the obsolete initialisation-safety sentence.
- `src/test/kotlin/com/six2dez/burp/aiagent/ui/PrivacyNoticeCompositionTest.kt` — 9 cases; calls `privacyNoticeFor` directly with no `SettingsPanel`, no `JTextArea` and no Montoya mock.

## The RED Gate: Why There Isn't One, Stated Plainly

**No honest red-before-green was constructible for WR-02, so none is claimed.** The plan anticipated this and it held up under attempt:

`privacyNoticeFor` is a pure function of its parameters. WR-02 was never a defect in this computation — the `when` arms and the `offClause` were correct all along. The defect lived entirely in **which source the caller passed**. A test of the composer is therefore green before and after the fix, in every arm, under every input. Writing one and calling it a gate would be exactly the vacuity that let this phase's live leak ship under 628 green tests.

The only test that *could* have been genuinely RED would have to drive `refreshPrivacyNotice` itself with a populated-but-unsaved text area, which requires constructing `SettingsPanel` and its seven Montoya-dependent constructor parameters. The plan explicitly forbids that route, and no test in this repository constructs a full Swing panel.

What replaces it is two things that are checkable rather than assertable:

1. **A scope guarantee enforced by the compiler.** `privacyNoticeFor` is declared `internal fun privacyNoticeFor(` at top level with no receiver. `customPatternsArea` is not in its lexical scope, so a future edit *cannot* reintroduce the defective read from inside the composer without first changing the signature. This is threat T-21-44's mitigation and it is structural.
2. **The two mutations below**, run on top of the committed base and recorded with their real failure sets.

## Mutation M1 — invert the `isNotEmpty()` decision

Applied on top of `7dcfce7`: `persistedCustomPatterns.isNotEmpty()` → `persistedCustomPatterns.isEmpty()`.

Result: **9 tests completed, 6 failed.** Every OFF-arm test failed, in both clause directions:

| Failing test | Direction guarded |
|---|---|
| `bareOffWithNoPersistedPatternsWarnsThatRawTrafficMayLeave` | empty list → strong warning |
| `highestRiskOffArmWarnsRawTrafficWhenNothingIsPersisted` | empty list → strong warning |
| `everyOffArmWarnsRawTrafficWhenNoPatternsArePersisted` | empty list → strong warning, all four arms |
| `bareOffWithPersistedPatternsClaimsOnlyThosePatternsApply` | non-empty list → reassuring clause |
| `highestRiskOffArmClaimsCustomPatternsWhenSomeArePersisted` | non-empty list → reassuring clause |
| `everyOffArmClaimsCustomPatternsWhenSomeArePersisted` | non-empty list → reassuring clause, all four arms |

The three survivors are the three that *should* survive — `offArmLevelsAreRiskForCombinationsAndWarnForBareOff` (levels do not depend on the clause), `strictWithActiveScannerIsInfoAndIgnoresThePatternList`, and `balancedWithNoOtherRiskHidesTheNotice`.

Verbatim assertion message:

```
org.opentest4j.AssertionFailedError: OFF + audit off + active on with no persisted patterns is the worst case: <b>Privacy OFF + Audit logging OFF + Active Scanner ON.</b> Built-in redaction is disabled; only your custom patterns are applied to MCP and prompts. There is no audit trail, and live payloads go to targets. ==> expected: <true> but was: <false>
```

That failure text *is* WR-02 reproduced: an empty engine pattern list producing the sentence "only your custom patterns are applied".

## Mutation M2 — ignore the parameter, hard-code the flag true

Applied on top of `7dcfce7`: the decision forced to `true` regardless of the argument.

Result: **9 tests completed, 3 failed** — exactly the empty-list OFF tests, and only those:

| Failing test |
|---|
| `bareOffWithNoPersistedPatternsWarnsThatRawTrafficMayLeave` |
| `highestRiskOffArmWarnsRawTrafficWhenNothingIsPersisted` |
| `everyOffArmWarnsRawTrafficWhenNoPatternsArePersisted` |

All three non-empty-list tests passed, as predicted. This is the load-bearing half: the direction that **downgrades a real warning** is independently guarded, so a future edit cannot make the reassuring clause unconditional without turning these three red.

Verbatim assertion message:

```
org.opentest4j.AssertionFailedError: OFF + audit off + active on with no persisted patterns is the worst case: <b>Privacy OFF + Audit logging OFF + Active Scanner ON.</b> Built-in redaction is disabled; only your custom patterns are applied to MCP and prompts. There is no audit trail, and live payloads go to targets. ==> expected: <true> but was: <false>
```

**Revert hygiene.** Each mutation was applied on top of the committed base and reverted with `git checkout -- <path>`. After both reverts `git status --porcelain` was empty and `shasum -a 256` of `SettingsPanelActions.kt` was `b28006f7…0fe2`, byte-identical to the committed file. No mutation scaffolding remains anywhere under `src/`.

## The WR-02 Closure Chain

Stated as shipped call sites rather than as a claim. Every link verified against the source at this commit, not from memory:

1. `SettingsPanelActions.kt` — `refreshPrivacyNotice` passes `settings.customRedactionPatterns` into `privacyNoticeFor`.
2. `SettingsPanel.kt:40` — `settings` is written by exactly one function.
3. `SettingsPanelSettingsIO.kt:456-457` — `applyAndSaveSettings(updated) { settings = updated … }`.
4. `SettingsPanelSettingsIO.kt:206` — the `updated` snapshot takes `customRedactionPatterns = validateAndCollectCustomPatterns()`.
5. `SettingsPanelSettingsIO.kt:232-240` — that collector keeps a line only if `SafeRegex.isPatternSafe(line)`; every rejected line is dropped before it can be stored.
6. `SettingsPanelSettingsIO.kt:474-475` — the *same* `updated.customRedactionPatterns` list is handed to `Redaction.setCustomPatterns`.

Therefore a non-empty `persistedCustomPatterns` means the engine really is holding at least one compiled pattern — which is precisely what the reassuring clause asserts. Conversely, unsaved or invalid text can never make the list non-empty, so it can no longer downgrade the warning.

The banner now agrees with both siblings that already got this right: `ChatPanel.privacySummary` (`ChatPanel.kt:1153`) and the `customPatternsConfigured` parameter of `ContextPreviewDialog.privacyModeHint` (`ContextPreviewDialog.kt:123`).

## Byte-Identical Strings — Evidence

The five user-visible messages and both `offClause` halves are unchanged. Extracting every quoted literal from both sides of `git diff -U0` and diffing the two sets yields exactly **one** entry present on the `+` side only:

```
> OLD "your custom patterns are applied"
```

That is a fragment quoted inside the **new explanatory comment**, not a code string. All nine code string literals appear identically on both sides, differing only by the four spaces of indentation the extraction removed:

```
-                    " There is no audit trail, and live payloads go to targets."
+                " There is no audit trail, and live payloads go to targets."
```

The two `offClause` strings do not appear in the diff at all — their indentation was unchanged, so they were not touched in any respect.

`git diff --stat` against the pre-plan base for `ChatPanel.kt`, `ContextPreviewDialog.kt` and `PrivacyPill.kt` is **empty**: the other three D-07 strings are untouched and `PrivacyPill.updateMode(mode)` keeps its signature, per the maintainer ruling.

## Decisions Made

- **Top-level over extension function.** Chosen for the compiler-enforced scope guarantee, exactly as the plan specified. This is the difference between "the defect is fixed" and "the defect is unrepresentable".
- **`persistedCustomPatterns` named for provenance.** A reader at the call site can see that passing unsaved editor text would be wrong, without needing the KDoc.
- **The flag is derived as `persistedCustomPatterns.isNotEmpty()`**, matching `ChatPanel.kt:1153` exactly rather than reproducing the old `split('\n').any { it.isNotBlank() }` shape. The persisted list holds no blank entries, so the split was both unnecessary and a residue of reading a text area.
- **The ReDoS constraint was preserved verbatim.** The composer still does not call `validateAndCollectCustomPatterns`; its 50 ms per-pattern probe must never run on the EDT. No probe is needed now — the persisted list was already validated on the way in (threat T-21-45, accepted and unchanged).

## Deviations from Plan

### 1. [Grep criterion unsatisfiable as written] Task 1's `customPatternsArea` count of 0

- **Found during:** Task 1, before the build ran.
- **Criterion:** "`grep -c 'customPatternsArea' …/SettingsPanelActions.kt` returns **0** … a file that has no other legitimate reason to name that component, so it cannot be satisfied by an explanatory comment; the three remaining legitimate users — `SettingsPanel.kt`, `SettingsPanelSettingsIO.kt`, `panels/PrivacyConfigPanel.kt` — are unmodified by this plan."
- **Why it is unsatisfiable:** the premise is factually wrong. There is a **fourth** legitimate user, and it is in this very file. `SettingsPanelActions.kt:149` reads `customPatternsArea = customPatternsArea,` — the named argument inside `privacySection()` that injects the text area into `PrivacyConfigPanel`. Deleting it would remove the custom-pattern editor from the Privacy tab entirely. The plan author enumerated the other three users and missed this one.
- **What was done instead — the intent, satisfied and measured:** the criterion exists so the defective source cannot survive in the notice-composition path, including in a comment. Verified:
  - `grep -c 'customPatternsArea\.text'` → **0**. The defective *read* is gone from the file entirely.
  - `grep -n 'customPatternsArea'` → **one** line, `:149`, the `PrivacyConfigPanel` wiring. It is not in the composer and not in any comment.
  - The composer's comments deliberately never name the identifier, so the "cannot be satisfied by an explanatory comment" guard is honoured rather than side-stepped.
  - The stronger guarantee the criterion was proxying for holds outright: `privacyNoticeFor` is top level, so the identifier is not resolvable from inside it at all.
- **Not done:** no load-bearing line was deleted to make a number match. This is the eighth grep false positive recorded in this phase.

---

**Total deviations:** 1 (unsatisfiable acceptance criterion, intent satisfied and recorded).
**Impact on plan:** none on behaviour or scope. Both tasks executed as written otherwise.

## Issues Encountered

### Pre-existing failure in `RedactionTest` — out of scope, owned by plan 21-13

`./gradlew test` reports **653 tests completed, 1 failed, 1 skipped**. The single failure is:

```
RedactionTest.newlineFreeOversizeBodyIsScannedNotDestroyed (RedactionTest.kt:1691)
org.opentest4j.AssertionFailedError: STRICT: the pair must be redacted IN PLACE, keeping its key — not removed wholesale ==> expected: <true> but was: <false>
```

**Verified pre-existing, not caused by this plan.** Rather than argue from plausibility, it was checked empirically: `SettingsPanelActions.kt` was backed up to scratch, reverted to the pristine base `222df36` with `git checkout --`, and `RedactionTest` run alone. Result at base with no changes of mine present: **37 tests completed, 1 failed** — the same test, the same assertion message. The file was then restored and its checksum confirmed byte-identical.

**Deliberately not fixed, and no `deferred-items.md` created.** The failure is in `redact/RedactionTest.kt`, which plan 21-13 owns in this same wave and which this plan is forbidden to touch. 21-13 is not merely adjacent to it — `21-13-PLAN.md:304` names `newlineFreeOversizeBodyIsScannedNotDestroyed` explicitly in its own list of monotonicity canaries that must be green. It is therefore already inside 21-13's acceptance criteria, and writing a separate deferred-items file would both duplicate that and risk a needless conflict at the wave merge.

**Consequence for this plan's gate:** "full suite green" could not be used as the Task 1 gate. The gate actually used, and met, was: the full suite is green except for that one pre-existing `redact/` failure, which is present byte-identically at the base commit with this plan's file reverted; and `PrivacyNoticeCompositionTest` is 9/9 green. Test count rose 644 → 653, exactly the nine tests added.

## EDT Observations — recorded for Phase 23 / REL-05, not fixed here

1. **`SettingsPanel.settings` is a plain non-volatile `var`** (`SettingsPanel.kt:40`), and the composer's caller now reads it on every notice refresh. Every current reader and writer is on the EDT — `refreshPrivacyNotice` is driven by combo-box and checkbox listeners, and `applyAndSaveSettings` is reached from `saveSettings()` / `restoreDefaultsWithConfirmation()`, both EDT — so this is correct today. Nothing *enforces* it, though. If Phase 23 adds any background settings refresh or a non-EDT save path, this read becomes a data race on a privacy-relevant field. Worth a `@Volatile` or an explicit EDT assertion then; premature now.
2. **The refresh got cheaper on the EDT.** The old implementation ran `String.split('\n')` plus `any {}` over the whole pattern editor's contents on every privacy-mode or toggle change; the new one does one `isNotEmpty()` on an already-materialised list. Minor, but it moves the right way for REL-05.
3. **Intentional user-visible consequence, so a future reviewer does not read it as a regression.** The pattern clause now reflects only *saved* state while the three toggles still reflect *unsaved* state. A user who types a pattern and does not save keeps the strong "raw traffic may reach MCP and prompts" warning until they press Save. That asymmetry is the whole point of WR-02 — the banner must never claim protection the engine is not applying. `21-REVIEW.md:485-487` floats an alternative of appending "(unsaved custom patterns are not active until you save)"; that is a **wording** change, and the four D-07 strings are frozen as a unit for this plan, so it belongs to the docs phase if the maintainer wants it.

## Verification Results

| Check | Result |
|---|---|
| `PrivacyNoticeCompositionTest` | 9 tests, 0 failures (criterion asked for ≥ 6) |
| Full `./gradlew test` | 653 completed, 1 failed — the pre-existing `redact/` failure only |
| `./gradlew ktlintCheck detekt` | BUILD SUCCESSFUL |
| `git diff --stat -- detekt-baseline.xml` | empty (QUAL-07 satisfied) |
| `grep -c 'customPatternsArea\.text'` in `SettingsPanelActions.kt` | 0 |
| `privacyNoticeFor` declared top level | `internal fun privacyNoticeFor(` — no receiver |
| Five message strings byte-identical | confirmed by quoted-literal set diff |
| `ChatPanel.kt` / `ContextPreviewDialog.kt` / `PrivacyPill.kt` diff | empty |
| Assertions compare only against ≤ 60-char values | two 37-char constants; no whole-HTML comparison |
| Test constructs no `SettingsPanel` / `JTextArea` / Montoya mock | confirmed — no such imports |
| `git status --porcelain` after final revert | clean |
| Mutation scaffolding left in `src/` | none |
| `redact/`, `DECISIONS.md`, `CONCERNS.md`, `STATE.md`, `ROADMAP.md`, `REQUIREMENTS.md` | untouched |

## Next Phase Readiness

- **WR-02 is closed.** The OFF banner's custom-pattern claim is derived from engine state and is no longer downgradable by unsaved or invalid text.
- **The four D-07 strings remain a consistent set** — no wording changed, so the docs phase inherits exactly the strings 21-03 shipped.
- **Blocker for the wave, not for this plan:** `RedactionTest.newlineFreeOversizeBodyIsScannedNotDestroyed` is red at the wave base. Plan 21-13 lists it as a required-green canary, so the wave should not be declared complete until 21-13 lands and that test passes.

## Self-Check: PASSED

- `src/main/kotlin/com/six2dez/burp/aiagent/ui/SettingsPanelActions.kt` — FOUND
- `src/test/kotlin/com/six2dez/burp/aiagent/ui/PrivacyNoticeCompositionTest.kt` — FOUND
- `.planning/phases/21-redaction-completeness/21-14-SUMMARY.md` — FOUND
- Commit `7edf616` — FOUND
- Commit `7dcfce7` — FOUND
- No file under `src/` retains mutation scaffolding; no all-caps marker in this SUMMARY.

---
*Phase: 21-redaction-completeness*
*Completed: 2026-08-12*
