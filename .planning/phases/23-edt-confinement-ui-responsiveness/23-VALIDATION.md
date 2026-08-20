---
phase: 23
slug: edt-confinement-ui-responsiveness
# status lifecycle: draft (seeded by plan-phase) → validated (set by validate-phase §6)
# audit-milestone §5.5 distinguishes NOT-VALIDATED (draft) from PARTIAL (validated + nyquist_compliant: false) (#2117)
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-08-20
---

# Phase 23 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.
> Seeded by plan-phase from `23-RESEARCH.md` §"Validation Architecture", which is itself grounded in
> `23-AI-SPEC.md` §5 (dimensions E1–E10, scenarios S-01…S-12). No parallel scheme is invented here.
> Task-level rows are filled in at plan time — the plans do not exist yet when this file is seeded.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit Jupiter 6.0.3 + mockito-kotlin 5.4.0, Gradle `useJUnitPlatform()` |
| **Config file** | `build.gradle.kts` — `tasks.test` (incl. `-Djava.awt.headless=true`), the `excludeHeavyTests` filter at `:201-214`, and four `inputs.file` declarations at `:166-200` |
| **Quick run command** | `JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test --tests '*ChatPanelEdtConfinementTest' --tests '*McpToolExecutorEdtGuardTest' --tests '*SettingsSaveAsyncTest' --tests '*ChatPanelToolGateTest'` |
| **Full suite command** | `JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew ktlintCheck detekt test` |
| **Estimated runtime** | quick ~30–60 s · full several minutes |

> **`JAVA_HOME` is not optional.** The default JDK on this machine is 25 and breaks Gradle 8.12.1.

### ⚠ Naming constraint — this decides whether the PR gate runs any of this

`.github/workflows/build.yml:47` runs `./gradlew test -PexcludeHeavyTests=true`, and
`build.gradle.kts:206-213` excludes `*IntegrationTest`, `*ConcurrencyTest`, `*BackpressureTest`,
`*RestartPolicyTest` and `*SupervisionTest` under that flag. A new suite named `*ConcurrencyTest`
would run **nightly only** and never on the cross-platform matrix — the one place a platform EDT
difference would surface. `ChatPanelConcurrencyTest` is already in the excluded set, which makes the
wrong name the natural one to reach for here.

**Use:** `ChatPanelEdtConfinementTest`, `McpToolExecutorEdtGuardTest`, `SettingsSaveAsyncTest`.

---

## Sampling Rate

- **After every task commit:** the quick run command above
- **After every plan wave:** `./gradlew ktlintCheck detekt test`
- **Before `/gsd-verify-work`:** full suite green, **plus** two phase-specific gates —
  `./gradlew test -PexcludeHeavyTests=true` to confirm the new suites actually execute under the
  PR-gate filter, and `git diff --stat detekt-baseline.xml` empty (the baseline is signature-keyed
  and pinned at 1096 as a milestone metric; QUAL-07 requires it shrink, never grow)
- **Max feedback latency:** ~60 seconds

---

## Per-Task Verification Map

> Requirement-level map, seeded from research. **Task IDs are assigned at plan time** — the `Task ID`
> and `Plan` columns are completed when `23-NN-PLAN.md` files exist. Every command runs in the fast
> PR gate.

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| TBD | TBD | 2 | SC1 · E2 · S-10 | V7 | `executeToolResult` throws `IllegalStateException` when entered from `invokeAndWait`, with `-ea` off | unit | `--tests '*McpToolExecutorEdtGuardTest'` | ❌ W0 | ⬜ pending |
| TBD | TBD | 1 | SC1 · E2 · S-01 | V4 | Chain call site reaches the executor on a non-EDT, daemon, **named** thread — captured, not inferred | integration | `--tests '*ChatPanelEdtConfinementTest'` | ❌ W0 | ⬜ pending |
| TBD | TBD | 2 | SC1 · E1 · S-03 | V4 | A `Deny` produces **zero** `executeToolResult` invocations and starts no worker | integration | `--tests '*ChatPanelEdtConfinementTest'` | ❌ W0 | ⬜ pending |
| TBD | TBD | 1 | SC2 · E2 · S-02 | V7 | A Montoya double that throws when called on the EDT is never called on the EDT — **red against HEAD** | integration | `--tests '*ChatPanelEdtConfinementTest'` | ❌ W0 | ⬜ pending |
| TBD | TBD | 2 | SC2 · E2 · F-4 | V4 | The guard precedes the `ext:` early return, so `routeExternalToolCall`'s `runBlocking` is covered | unit | `--tests '*McpToolExecutorEdtGuardTest'` | ❌ W0 | ⬜ pending |
| TBD | TBD | 1 | SC3 · E3 · S-01 | — | A runnable queued to the EDT runs **while** the tool is mid-call (mutual latch handshake, no wall-clock); 8 results in submission order **by trace id**; busy cleared exactly once — **red against HEAD** | integration | `--tests '*ChatPanelEdtConfinementTest'` | ❌ W0 | ⬜ pending |
| TBD | TBD | 3 | SC4 · E7 · S-11 | V8 | `applyAndSaveSettings` off the EDT; EDT not blocked on `future.get(10, SECONDS)`; `currentSettings()` snapshot still taken on the EDT; both callers report from the completion callback; Save disabled in flight | integration | `--tests '*SettingsSaveAsyncTest'` | ❌ W0 | ⬜ pending |
| TBD | TBD | 3 | SC4 · FLAG-23-06 | V7 | The busy seam lowers on the **failure** path, not only on success | integration | `--tests '*SettingsSaveAsyncTest'` | ❌ W0 | ⬜ pending |
| TBD | TBD | 5 | SC5 · E5 | V3 | `assertEdt()` and its 6 call sites byte-identical; new `invokeLater` count stated with a per-addition reason; no worker-side read of a `@GuardedBy("EDT")` map | structural + integration | `--tests '*ChatPanelEdtConfinementTest'` | ❌ W0 | ⬜ pending |
| TBD | TBD | 1 / 4 | SC6 · E9 · S-07 | V7 | Worker is `isDaemon` **and named**; unload does not join it and does not block; a throwing worker writes `logToError` | integration | `--tests '*ChatPanelEdtConfinementTest'` | ❌ W0 | ⬜ pending |
| TBD | TBD | 1 / 4 | REL-05 (audit) · E4 · S-04/05/06/07/09/12 | V7 | The audit pair fires — `report` first, its `metadata` consumed by `log` — on **every** exit incl. cancel and supersede; the `Result` crosses the boundary intact | integration | `--tests '*ChatPanelEdtConfinementTest'` | ❌ W0 | ⬜ pending |
| TBD | TBD | 4 | REL-05 (budget) · E6 · S-04/S-09 | V4 | Cancel and supersede send no followup turn and **refund no iteration**; chain still terminates at 8; `onCompleted` discharged on every exit | integration | extends `ChatPanelToolGateTest.kt:368` | ⚠️ partial — extend | ⬜ pending |
| TBD | TBD | 4 | REL-05 · E10 (**rewritten negative**) · S-08 | V4 | A `/tool` racing a chain: both dispatch off-EDT, both execute, chain unharmed, and **no** `"Too many concurrent MCP requests."` appears — each chat call site mints its own limiter (`ChatPanel.kt:3019`) | integration | `--tests '*ChatPanelEdtConfinementTest'` | ❌ W0 | ⬜ pending |
| TBD | TBD | 2 / 4 | E1 · S-03/S-09 | V1, V4 | No worker starts for a model-originated call until the gate produced `Run` **on the EDT**; exactly one `executeToolResult` per approved call; a superseded card produces no second execution | integration | `--tests '*ChatPanelEdtConfinementTest'` | ❌ W0 | ⬜ pending |
| TBD | TBD | 3 | E8 · S-11 | V8 | A tool worker dispatched during a settings save redacts under its **snapshot** privacy mode and is never unredacted; the stale `Redaction.kt:767-768` comment is corrected | integration + structural | `--tests '*SettingsSaveAsyncTest'` | ❌ W0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `src/test/kotlin/.../ui/ChatPanelTestHarness.kt` — add `awaitToolSettled(count, failsafe)` plus
      `installSettledObserver()` / `releaseSettledObserver()`. **Prerequisite for every chat scenario
      and the phase's first commit.** The existing `drainEdt()` (`:211`) is
      `repeat(times) { SwingUtilities.invokeAndWait { } }` and is structurally blind to a daemon
      worker; `ChatPanelToolGateTest.kt:358` becomes racy without this.
- [ ] `src/main/kotlin/.../ui/OffEdtDispatch.kt` — the production helper the harness observes.
      **The observer must hang off this helper, not off an executor mock:** `McpToolExecutor` is an
      `object` singleton (`mcp/tools/McpToolExecutorImpl.kt:45`) with no interface, so there is no
      executor test double to put a latch in. Install/clear follows the
      `AuditLogger.registerGlobalEmitter` precedent already used in the same test class.
- [ ] `src/test/kotlin/.../ui/ChatPanelEdtConfinementTest.kt` — SC1, SC2, SC3, SC5, SC6, E1–E6, E9, E10
- [ ] `src/test/kotlin/.../mcp/tools/McpToolExecutorEdtGuardTest.kt` — S-10
- [ ] `src/test/kotlin/.../ui/SettingsSaveAsyncTest.kt` — SC4, E7, E8. **Gated on the A2 spike below.**
- [ ] `build.gradle.kts` — an `inputs.file` declaration for any newly source-read main file. Only four
      files are currently declared inputs and three of this phase's are not among them, so a
      structural assertion would not re-run when its target changes.
- [ ] Framework install: **none required** — JUnit, mockito-kotlin, JaCoCo and the headless flag all ship.

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| The `JOptionPane` save-failure modal appears and carries the exception message | SC4 · D-12 | Not headless-testable — `JOptionPane.getRootFrame()` throws `HeadlessException` (`CONCERNS.md` §"UI layer has no integration tests", measured) | In Burp, force a save failure; confirm both the inline banner **and** the modal appear, and that the banner text matches the modal |
| Does `isEnabled = false` read as disabled on the opaque orange Save button? | SC4 · UI-SPEC FLAG-23-01 | Depends on Burp's live Look-and-Feel; `saveButton.isOpaque = true` with an explicit `primary` background (`BottomTabsPanel.kt:61-64`) | Trigger a slow save; confirm the recolored button is visibly inert, in both light and dark themes |
| Sub-frame Send↔Cancel flicker on the auto-approved path | SC3 · UI-SPEC FLAG-23-04 | A repaint between two `invokeLater` blocks (`:658` → `:741`) is sub-frame; no deterministic assertion exists | Run an 8-iteration chain with `AUTO`-tier tools; watch for a visible Send-button flash between steps |
| Burp throws *"Extensions should not make HTTP requests in the Swing event dispatch thread"* | SC2 · research A1 | Corroborated by this repo's own #80 workaround and third-party reports, but not live-confirmed | Call `http1_request` from chat against pre-fix code; record the actual exception text |

All four go to `23-HUMAN-UAT.md`.

---

## Open Risk Gating a Wave-3 File

**A2 — is `SettingsPanel` headlessly constructible?** Research found no `Toolkit` / `Desktop` /
`ImageIO` call in its construction path, seven injectable dependencies, and an existing in-memory
`Preferences` fake — but that is inference from absence, not proof. `SettingsSaveAsyncTest.kt` depends
on it. **Mitigation:** a 30-minute spike as the first task of the Wave-3 plan, with structural
assertions plus an `inputs.file` declaration as the documented fallback if construction proves
infeasible.

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 60s
- [ ] New suites confirmed to run under `-PexcludeHeavyTests=true`
- [ ] `detekt-baseline.xml` unchanged
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
