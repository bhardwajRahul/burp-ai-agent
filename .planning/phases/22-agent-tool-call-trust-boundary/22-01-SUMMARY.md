---
phase: 22-agent-tool-call-trust-boundary
plan: 01
subsystem: testing
tags: [kotlin, swing, headless, junit5, mockito, mockito-kotlin, gradle, sec-06, chatpanel]

# Dependency graph
requires:
  - phase: 20-mcp-access-control-correctness
    provides: "The Phase 20 SC4 standard — a test that passes both before and after the change has not tested the defect"
provides:
  - "A headless-constructible, drivable real ChatPanel (production guard on the single headless-hostile call)"
  - "ChatPanelTestHarness — shared fixture building the real ten-parameter ChatPanel from deep-stub mocks"
  - "ChatPanelToolGateTest — the SC4 acceptance-gate file, seeded with the pre-fix SEC-06 characterization"
  - "tasks.test running under -Djava.awt.headless=true so macOS and ubuntu-latest agree"
affects: [22-02, 22-03, 22-04, 22-05, 22-06, 22-07, 22-08, 22-09, 23]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Headless guard: GraphicsEnvironment.isHeadless() check plus a narrow HeadlessException catch, never a broad runCatching"
    - "Real-UI test harness: deep-stub MontoyaApi + AgentSupervisor, synchronous sendChat callbacks, depth-first component finder, EDT drain"
    - "Callback-index stubbing verified against the real signature rather than trusted from research"

key-files:
  created:
    - src/test/kotlin/com/six2dez/burp/aiagent/ui/ChatPanelTestHarness.kt
    - src/test/kotlin/com/six2dez/burp/aiagent/ui/ChatPanelToolGateTest.kt
  modified:
    - src/main/kotlin/com/six2dez/burp/aiagent/ui/ChatPanel.kt
    - build.gradle.kts

key-decisions:
  - "Used Mockito.atLeastOnce() (core Mockito) instead of the mockito-kotlin alias so the verification mode appears exactly once in ChatPanelToolGateTest.kt, satisfying the plan's exact-count acceptance criterion without an import-line collision"
  - "requirements-completed left EMPTY: this plan makes SEC-06 provable, it does not fix it — 22-07 lands the control"
  - "REQUIREMENTS.md deliberately not modified (shared wave artifact + SEC-06 not satisfied)"
  - "modelResponse is a non-defaulted parameter on the harness, matching the ContextPreviewDialog.kt:21-23 convention"

patterns-established:
  - "Pattern: production headless guards carry a comment naming the requirement (SEC-06 / SC4) so they are not deleted as dead code"
  - "Pattern: new gate tests are name-checked against the -PexcludeHeavyTests=true exclusion list before being considered landed"
  - "Pattern: acceptance-criteria grep counts are re-run after every prose edit, because KDoc text can collide with the counted token"

requirements-completed: []  # INTENTIONALLY EMPTY — see "Requirement Status" below. SEC-06 is NOT satisfied by this plan.

# Metrics
duration: ~40 min active (17h 10m wall clock)
completed: 2026-08-14
---

# Phase 22 Plan 01: Wave 0 — SC4 Acceptance-Gate Infrastructure Summary

**A two-line headless guard on `menuShortcutKeyMaskEx` makes the real `ChatPanel` constructible in tests, and `ChatPanelToolGateTest` now proves — by clicking the real Send button — that a model-emitted `scope_check` reaches `api.scope().isInScope("http://evil.example/")` with no user decision.**

## Performance

- **Duration:** ~40 min active execution; 17h 10m wall clock
- **Started:** 2026-08-13T14:19:45Z
- **Completed:** 2026-08-14T07:30:08Z
- **Tasks:** 3 of 3
- **Files modified:** 4 (2 created, 2 modified)

_The wall-clock figure includes a ~16h idle gap: the session was terminated by a stream watchdog between Task 1 and Task 2 and resumed by the coordinator. No work was lost — Task 1 was already committed._

## Accomplishments

- **The SEC-06 defect is now executable, not argumentative.** `ChatPanelToolGateTest` drives the real production path end to end: real `ChatPanel`, real Send button, real `maybeExecuteToolCall`, real `McpToolExecutor.executeTool`, real `api.scope().isInScope(...)`. The mockito `atLeastOnce()` verification passes, which means the call genuinely reached Burp with no gate.
- **Research assumption A3 (headless parity) confirmed locally.** With `-Djava.awt.headless=true` forced in `tasks.test`, the full suite is green on macOS — the same mode `ubuntu-latest` gives CI. Assumption A4 (Mockito inline mock-maker on a final Kotlin class) is also confirmed: `AgentSupervisor` is a final Kotlin class and mocks without any `mockito-extensions` config file.
- **The PR gate provably does not skip the new file.** Verified empirically by running `test -PexcludeHeavyTests=true --tests "*ToolGate*"` after deleting the prior report, then counting `<testcase>` in the regenerated XML: exactly 1.
- **Zero production behaviour change beyond the accelerator fallback**, and the detekt baseline did not grow.

## Task Commits

Each task was committed atomically:

1. **Task 1: Guard the headless-hostile accelerator read and enable headless mode in tasks.test** — `faa3f98` (feat)
2. **Task 2: Create ChatPanelTestHarness — the shared headless ChatPanel fixture** — `3a3c6b6` (test)
3. **Task 3: Seed ChatPanelToolGateTest with the pre-fix SEC-06 characterization** — `a8b9048` (test)

## Files Created/Modified

- `src/main/kotlin/com/six2dez/burp/aiagent/ui/ChatPanel.kt` — the `menuMask` assignment (was `:377-380`, now `:377-399`) reads the Cmd/Ctrl-T accelerator behind `GraphicsEnvironment.isHeadless()` plus a narrow `catch (_: HeadlessException)`, falling back to `InputEvent.CTRL_DOWN_MASK`. Carries a comment naming SEC-06 / SC4 so it is not removed as dead code.
- `build.gradle.kts` — `tasks.test` `jvmArgs("-ea", "-Djava.awt.headless=true")`, with the existing `-ea` / REL-01 justification preserved and a second comment naming the Phase 22 SC4 harness. `storeBuild.expected` and the `excludeHeavyTests` filter untouched.
- `src/test/kotlin/com/six2dez/burp/aiagent/ui/ChatPanelTestHarness.kt` — `object ChatPanelTestHarness` with `Harness` holder, `create(modelResponse, settings)`, generic depth-first `find`, `sendUserMessage`, `drainEdt`. No test annotations.
- `src/test/kotlin/com/six2dez/burp/aiagent/ui/ChatPanelToolGateTest.kt` — one test, `modelEmittedToolCall_reachesBurpWithNoUserDecision_preFixBaseline`, plus the mandated file KDoc.

## Requirement Status

**SEC-06 is NOT complete and `requirements-completed` is deliberately empty.**

This plan is Wave 0 infrastructure. It makes the defect *provable*; it does not fix it. The plan's own threat register says so for T-22-01: *"Not fixed by this plan — this plan makes the defect provable."* The control (the user decision gate) lands in **22-07**, which adds the `never()` acceptance assertion on the `CONFIRM`-tier `proxy_http_history`.

Copying `SEC-06` into `requirements-completed` would have caused `requirements mark-complete` to check off an unshipped security control — the exact class of over-claim this milestone exists to eliminate. **`REQUIREMENTS.md` was therefore not modified.** It is also a shared wave artifact that sibling plan 22-02 may touch on its own branch, so per-worktree writes to it invite merge conflicts. The orchestrator should mark SEC-06 complete only after 22-07.

## Decisions Made

- **`Mockito.atLeastOnce()` instead of `org.mockito.kotlin.atLeastOnce`.** The acceptance criterion requires `grep -c 'atLeastOnce'` to return exactly `1`. A mockito-kotlin import line would itself contain the token, making the count `2` no matter how the assertion was written. Importing `org.mockito.Mockito` and calling `Mockito.atLeastOnce()` keeps the token on exactly the one line where the verification mode applies. Semantically identical — the mockito-kotlin symbol is a re-export of this function.
- **The harness KDoc explicitly contradicts `ChatPanelConcurrencyTest`.** That file's KDoc claims a real `ChatPanel` "requires Swing + UiTheme and throws HeadlessException in CI". That premise is false — the entire cost was the one-call guard. The harness records this so nobody re-derives the modelled workaround. `ChatPanelConcurrencyTest.kt` itself was left untouched (outside this plan's `files_modified`).
- **`sendChat` callback indices verified, not trusted.** The plan required checking research's claim of index 7 / 8 against the real signature. Read at `AgentSupervisor.kt:435-449`: 13 parameters, `onChunk` at index 7, `onComplete` at index 8. Research was correct; the indices are named constants with the source line in their KDoc.

## Deviations from Plan

None — plan executed exactly as written. No Rule 1–4 deviations were triggered. No packages were installed (consistent with threat register `T-22-SC`), and no dependency block in `build.gradle.kts` was touched.

## Issues Encountered

**1. Three acceptance-criteria grep collisions caused by my own prose (self-corrected during the acceptance gate loop).**

The plan's criteria count occurrences of specific tokens. My first draft of each file mentioned the counted token in a comment or KDoc, inflating the count:

| File | Criterion | First draft | Fix |
|------|-----------|-------------|-----|
| `build.gradle.kts` | `grep -c 'Djava.awt.headless=true'` = 1 | 2 — my justification comment repeated the literal flag | Reworded the comment to "The headless flag above…" |
| `ChatPanelTestHarness.kt` | `grep -c '@Test'` = 0 | 1 — KDoc said "contains no `@Test` functions by design" | Reworded to "no annotated test functions" |
| `ChatPanelToolGateTest.kt` | `grep -c 'atLeastOnce'` = 1 | 2 — an explanatory comment named the function | Deleted the redundant comment |

Each was caught by running the criteria rather than assuming them, and each was fixed before the task was committed. Every criterion was then re-run and passes. This is the origin of the "re-run grep counts after every prose edit" pattern noted above.

**2. Line references in the plan have shifted.** Task 1's guard added 20 lines above the rest of `ChatPanel.kt`. The plan cites the ungated `McpToolExecutor.executeTool` call as `ChatPanel.kt:2132`; it is now **`:2152`**. `ChatPanelToolGateTest.kt` and this summary both cite the corrected `:2152`. Plans 22-02..22-09 that quote `:2132` should expect the +20 offset.

**3. Coordinator's 22-07 wording correction — no discrepancy found.** The coordinator asked me to use the "22-07 ADDS a CONFIRM-tier assertion while KEEPING this one as the AUTO companion" framing and to flag any conflict with the plan text. There is no conflict: the plan I executed is the revised version, whose `must_haves`, Task 3 action, and threat register all already state "keeps … adds" and explicitly warn against inverting the `scope_check` line. `22-VALIDATION.md:84-104` agrees. The KDoc as written follows this framing and states that the assertion "passes both before and after on purpose — it is the companion, never the gate."

**4. Worktree base correction.** On startup the worktree branch was created from `03f17a7`, an ancestor of the expected base `322e2cb`. Per the sanctioned `<worktree_branch_check>` procedure the branch was hard-reset to `322e2cb` (HEAD assertion passed first; working tree was clean, nothing lost).

## Verification Results

All plan-level `<verification>` commands re-run at completion:

| Check | Result |
|-------|--------|
| `./gradlew ktlintCheck detekt test` | **exit 0** — BUILD SUCCESSFUL in 2m 28s |
| `git diff --stat -- detekt-baseline.xml` | **empty** — baseline did not grow (QUAL-07) |
| `./gradlew test -PexcludeHeavyTests=true --tests "*ToolGate*"` | **exit 0**; regenerated report has exactly **1** `<testcase>` — the PR gate does not filter it |
| `ChatPanelToolGateTest` suite XML | `tests="1" skipped="0" failures="0" errors="0"` |

All per-task `<acceptance_criteria>` were executed individually and pass (counts: headless flag 1; `CTRL_DOWN_MASK` non-comment 2; `isHeadless`/`HeadlessException` non-comment 2; `SEC-06|SC4` 2; `object ChatPanelTestHarness` 1; `@Test` 0; `RETURNS_DEEP_STUBS` 2; `TestSettings.baselineSettings` 1; `invokeAndWait` 2; excluded-suffix name match 0; `class ChatPanelToolGateTest` 1; `isInScope` 3; `atLeastOnce` 1; `@Disabled` 0; `22-07` 4).

## Known Stubs

None. No hardcoded empty values, placeholder text, or unwired components were introduced. The harness's no-op lambdas (`applySettings = { }`, `showError = { }`, …) are deliberate test doubles satisfying the production constructor, not stubs in production code.

## User Setup Required

None — no external service configuration required.

## Next Phase Readiness

- **Wave 0 is complete and its CI assumptions are locally proven.** Plans 22-02..22-09 can build on `ChatPanelTestHarness` for SC2/SC4/SC5 work. The generic `find` helper is ready for locating the approval card and its four buttons.
- **Watch the first CI run on this branch.** The entire point of landing this in its own commit was to prove research assumptions A3/A4 on `ubuntu-latest` before eight plans depend on them. Local macOS green under forced headless is strong evidence but is not the CI run itself.
- **For 22-07 specifically:** keep the `scope_check` / `atLeastOnce()` assertion exactly as it stands (it is the `AUTO` companion) and add the `CONFIRM`-tier assertions on `proxy_http_history`. Per `22-VALIDATION.md:113-120`, the red-before-green proof counts only if the `never()` verification is the assertion that fails at the pre-gate SHA.
- **Note for the orchestrator:** this branch does not contain 22-02's `SecTier` enum or the `secTier` field on `McpToolDescriptor`; nothing here references them, so the two branches should merge without interaction.
- **No blockers.**

## Self-Check: PASSED

- All 4 key files verified present on disk.
- All 3 task commits verified in `git log`: `faa3f98`, `3a3c6b6`, `a8b9048`.
- No file deletions in any of the three commits (`git diff --diff-filter=D` empty for each).
- `STATE.md` and `ROADMAP.md` untouched, as required in worktree mode.

---
*Phase: 22-agent-tool-call-trust-boundary*
*Completed: 2026-08-14*
