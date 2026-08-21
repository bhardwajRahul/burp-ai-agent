---
gsd_state_version: 1.0
milestone: v0.10.0
milestone_name: Security Correctness & Agent Trust
current_phase: 24
current_phase_name: Scheduler & Process Robustness
status: executing
stopped_at: All 5 plans executed and merged — phase gates pending
last_updated: "2026-08-21T13:34:19.559Z"
last_activity: 2026-08-21
last_activity_desc: Phase 24 execution complete — 5/5 plans, 834 tests green
state_head: 9a00f6950cfa886533e885ba11e648a2353cada0
progress:
  total_phases: 7
  completed_phases: 2
  total_plans: 51
  completed_plans: 51
  percent: 29
---

# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-08-05)

**Core value:** Bring modern AI to a real security workflow without leaking sensitive traffic to third-party providers — privacy controls and an audit trail are non-negotiable.
**Current focus:** Phase 24 — Scheduler & Process Robustness

## Current Position

Phase: 24 — Scheduler & Process Robustness
Plan: 05 of 5 complete (all 4 waves done)
Status: Executed — running phase gates
Resume file: None
Last activity: 2026-08-21 — all 5 plans merged; 121 suites / 834 tests green

## Milestone Origin

v0.10.0 comes from a deep review of v0.9.2 on 2026-08-05 (17 findings). Two were confirmed by
running the shipped code rather than by reading it:

- **SEC-04** — the MCP access-control interceptor is registered after `routing{}` in Ktor's `Call`
  phase, so it never executes for routes Ktor resolves. Reproduced: external mode, no
  `Authorization` header, `POST /message` → `400 "sessionId query parameter is not provided"`
  (handler ran) instead of `401`. Only unmatched paths return 401.

- **PRIV-05** — the passive scanner re-emits cookies as bare `name=value` without the `Cookie:`
  prefix that `cookieHeaderRegex` matches on. Verified against the live regexes: `JSESSIONID`,
  `PHPSESSID`, `connect.sid`, `auth_token`, `csrftoken` all pass through unredacted in STRICT and
  BALANCED; only a cookie literally named `session` is caught.

Measured baselines at milestone start (for Phase 26 to improve against):
coverage 34% line / 23% branch project-wide; detekt baseline 1096 entries; `test detekt ktlintCheck`
all green on v0.9.2.

## Deferred Items

Items acknowledged and deferred at the v0.9.0 milestone close on 2026-08-05:

| Category | Item | Status |
|----------|------|--------|
| uat_gap | Phase 01 — 01-HUMAN-UAT.md (1 open scenario) | partial |
| uat_gap | Phase 02 — 02-HUMAN-UAT.md (6 open scenarios) | partial |
| uat_gap | Phase 03 — 03-HUMAN-UAT.md (4 open scenarios) | partial |
| uat_gap | Phase 13 — 13-HUMAN-UAT.md (3 open scenarios) | partial |
| uat_gap | Phase 14 — 14-HUMAN-UAT.md (4 open scenarios) | partial |
| uat_gap | Phase 15 — 15-HUMAN-UAT.md (1 open scenario) | partial |
| uat_gap | Phase 17 — 17-HUMAN-UAT.md (1 open scenario) | partial |
| verification_gap | Phase 01 — 01-VERIFICATION.md | human_needed |
| verification_gap | Phase 03 — 03-VERIFICATION.md | human_needed |
| quick_task | 260527-f7q-fix-bugs-66-67-68-cli-tokenizer-copilot- | unknown |

Rationale: phases 01-03 belong to v0.7.0, shipped 2026-05-15. The formal
`v0.9.0-MILESTONE-AUDIT.md` returned `passed` with 22/22 requirements satisfied, 8/8 phases
verified and 6/6 E2E flows wired, so these are orphaned checkboxes rather than coverage gaps.

## Performance Metrics

**Velocity:**

- Total plans completed: 78
- Average duration: —
- Total execution time: 0 hours

**By Phase:**

| Phase | Plans | Total | Avg/Plan |
|-------|-------|-------|----------|
| — | — | — | — |
| 01 | 1 | - | - |
| 11 | 4 | - | - |
| 13 | 3 | - | - |
| 14 | 3 | - | - |
| 15 | 3 | - | - |
| 17 | 3 | - | - |
| 18 | 4 | - | - |
| 19 | 5 | - | - |
| 16 | 6 | - | - |
| 20 | 10 | - | - |
| 21 | 19 | - | - |
| 22 | 9 | - | - |
| 23 | 8 | - | - |

**Recent Trend:**

- Last 5 plans: —
- Trend: —

*Updated after each plan completion*
| Phase 08-bapp-store-resubmission-mcp-pivot-to-extension-native-tools- P02 | 27 | 3 tasks | 13 files |
| Phase 08 P03 | 20 | 2 tasks | 4 files |
| Phase 09 P01 | 3 | 2 tasks | 3 files |
| Phase 10-mcp-tools-tab-redesign P01 | 8 | 2 tasks | 2 files |
| Phase 11-settings-tabs-theme-rollout P01 | 2 | 2 tasks | 2 files |
| Phase 11 P02 | 8 | 2 tasks | 3 files |
| Phase 11 P03 | 14 | 2 tasks | 5 files |
| Phase 11-settings-tabs-theme-rollout P04 | 35 | 2 tasks | 1 files |
| Phase 13-privacy-redaction-hardening P01 | 30 | 3 tasks | 4 files |
| Phase 13 P02 | 30 | 3 tasks | 7 files |
| Phase 13 P03 | 5 | 3 tasks | 3 files |
| Phase 15 P01 | 4 | 3 tasks | 4 files |
| Phase 15-pre-send-secret-tripwire P03 | 6min | 3 tasks | 4 files |
| Phase 17-reliability-concurrency-hardening P01 | 10m | 2 tasks | 7 files |
| Phase 17 P02 | 3m | 2 tasks | 4 files |
| Phase 18 P01 | 15m | 2 tasks | 4 files |
| Phase 18 P02 | multi-session | 2 tasks | 40 files |
| Phase 18 P04 | 10m | 2 tasks | 5 files |
| Phase 16-external-mcp-client P01 | 5min | 2 tasks | 3 files |
| Phase 16-external-mcp-client P02 | 615 | 2 tasks | 7 files |
| Phase 16 P04 | 9min | 2 tasks | 3 files |
| Phase 19 P01 | 20m | 2 tasks | 4 files |
| Phase 19-mega-file-split-docs P02 | 120 | 7 tasks | 10 files |
| Phase 19-mega-file-split-docs P03 | multi-session-180min | 2 tasks | 7 files |
**Per-Plan Metrics:**

| Plan | Duration | Tasks | Files |
|------|----------|-------|-------|
| Phase 23 P01 | 42 min | 3 tasks | 5 files |
| Phase 23 P02 | 25 min | 4 tasks | 6 files |
| Phase 23 P03 | 36 min | 4 tasks | 7 files |
| Phase 23 P04 | 61 min | 3 tasks | 4 files |
| Phase 23 P05 | 27 min | 2 tasks | 4 files |
| Phase 23 P06 | 28 min | 3 tasks | 8 files |
| Phase 23 P07 | 27 min | 3 tasks | 5 files |
| Phase 23 P08 | 37 min | 3 tasks | 8 files |
| Phase 24 P01 | 33 min | 3 tasks | 8 files |

## Accumulated Context

### Decisions

Decisions are logged in PROJECT.md Key Decisions table.
Recent decisions affecting current work:

- Init: Active milestone scoped to v0.7.0 stabilization (Unreleased features + open bugs + docs + release).
- Init: Codebase mapping skipped — `SPEC.md`, `DECISIONS.md`, `AGENTS.md`, `CHANGELOG.md` already capture architecture.
- Init: Domain research skipped — maintainer is the domain expert.
- Roadmap: Three feature audits (Perplexity, insertion-point, prompt library) split into independent parallel-safe phases; release cut isolated as Phase 6 choke point.
- 08-01: Hand-rolled GenerateBuildFlagsTask (abstract class with @Input/@OutputDirectory) required for Gradle configuration-cache compatibility instead of doFirst lambda.
- 08-01: BuildFlags.kt generated into build/generated/buildflags/ (ktlint excluded via **/build/**); nativeTool: Boolean = false default preserves all 53 existing descriptors.
- 08-01: available(storeBuild: Boolean = BuildFlags.STORE_BUILD) default-arg overload enables unit testing without config-cache mocking.
- [Phase ?]: B1 fix: registerToolHandler() uses available() so STORE_BUILD=true silently skips generic tool IDs
- [Phase ?]: B2 fix: ai_passive_scan checks supervisor.isAiEnabled() BEFORE passiveScanner null check
- [Phase ?]: setAiToolDependencies() added to McpServerManager interface so typed reference can call it
- v0.8.0 Roadmap: UI-07 (no regressions) is cross-cutting — echoed as a success criterion in both Phase 10 and Phase 11 rather than assigned its own phase, since regression-safety is a property of phases that modify existing UI, not a deliverable on its own.
- v0.8.0 Roadmap: Phase 9 (design system) is additive only (new module, no panel migration); Phases 10 and 11 consume it. Phase 10 (MCP tab) prioritized over Phase 11 (settings rollout) because the MCP tab is the highest user pain point and benefits from Phase 8's nativeTool classification already in place.
- [Phase ?]: UiTheme.kt retained as legacy shim (KDoc-only change): Phase 11 will align naming (outline→border, statusRunning→statusSuccess) once all call sites migrated
- [Phase ?]: SC5 (formGrid non-null) deferred to DesignComponentsTest in Plan 02: formGrid() lives in Components.kt not DesignTokens.kt
- v0.9.0 Roadmap: Phase 12 (SEC) must be first — all new secret fields rely on it; no new secret lands in plaintext. Phase 19 (QUAL-01 mega-file split) must be last — PassiveAiScanner hook points from Phase 15 must be committed before the split.
- v0.9.0 Roadmap: CAP-03 (listener port filter) and CAP-04 (token budget) co-land with CAP-01 (Anthropic) in Phase 14 — small, non-conflicting additions; natural fit alongside Anthropic's four-field token usage surfacing.
- v0.9.0 Roadmap: CAP-02 (external MCP) requires kotlin-sdk 0.5.0→0.13.0 Burp-JVM test-run gate; placed after CAP-01 so the SDK bump does not block earlier phases.
- v0.9.0 Roadmap: PRIV-04 (redaction coverage UI) co-lands with PRIV-01+PRIV-02 in Phase 13 — the UI indicator shows when a known secret shape passes through, using the same curated pattern set as the Phase 15 tripwire.
- [Phase ?]: Body/form regex uses (^|[?&]) anchor to close leading-field gap (T-13-05)
- [Phase ?]: customRedactionPatterns persisted plaintext newline-joined — NOT SecretCipher (config not secrets)
- [Phase ?]: compiledCustomPatterns @Volatile list — EDT write visible to redaction thread without full synchronization
- [Phase ?]: setCustomPatterns called in applyAndSaveSettings — edits take effect without restart
- [13-03]: SecretShapes includes high-entropy hex shape placed last; T-13-11 false-positive risk accepted (non-blocking banner)
- [13-03]: SecretShapes is single AWT-free source of truth for PRIV-04 and Phase 15 tripwire reuse contract
- [13-03]: ContextPreviewDialog banner uses Level.WARN (advisory); categories-only — raw values never interpolated (T-13-10)
- [14-03]: listenerPort JSON key is camelCase (listenerPort) matching kotlinx-serialization field name — MCP clients send listenerPort not listener_port; GetProxyHttpHistoryRestricted also gains listenerPort for schema exposure under restricted branch
- [Phase ?]: recordHttpFailureIfRetryable defined as top-level CircuitBreaker extension so backends can import it directly
- [Phase ?]: A2 confirmed: generateBuildFlags.flatMap { it.outputDir } registers structural dependency in Gradle 8.12.1; builtBy() fallback not needed
- [Phase ?]: A1 confirmed: kotlin-compiler-embeddable warning absent in Kotlin 2.1.21 + detekt 1.23.8; no resolutionStrategy.force() needed
- [Phase ?]: detekt-formatting NOT added to detekt plugins to avoid double-gating style rules with ktlint
- [Phase ?]: SC5 scope: 45 catch sites in focused modules fully annotated; 138 remaining get TODO-AUDIT markers for future plan
- [Phase ?]: Module prefix convention: [ModuleName] added to AgentSupervisor logToError calls
- [Phase ?]: Path A confirmed: kotlin-sdk stays at 0.5.0; only 3 explicit dep pins needed (no SDK or Kotlin plugin bump)
- [Phase ?]: Wave 0 scaffold pattern: @Disabled stubs with commented production code document plan N+1 implementation contract
- [Phase ?]: SC1 < 500 line target physically unachievable for SettingsPanel.kt: field declarations alone are ~512 lines; 855 lines is the minimum achievable floor after all method body extractions
- [Phase 23]: Task 1 checkpoint resolved to door-guard: the throwing EDT precondition sits at McpToolExecutor.executeToolResult, not at the executeTool wrapper — the placement D-03/D-04 lock. No locked decision overridden.
- [Phase 23]: The -ea-off demonstration is a dedicated Gradle task (edtGuardWithoutAssertionsTest, jvmArgs -da) rather than a task-wide -da on tasks.test, which would weaken every other suite's assertions to buy one suite's evidence.
- [Phase 23]: FLAG-23-03 answered YES: the /tool command is appended to session.messages as a user ChatMessage, removing the asymmetry with openToolDialog rather than preserving it.
- [Phase 23]: The two user-originated tool tails emit no audit decision pair — SC5 makes these paths ungated, so there is no approval record to report and inventing one would fabricate a SEC-06 log entry.
- [Phase 23]: 23-03: E8 answered documented-residual — the residual is one named window (a tool worker's snapshot privacyMode from before a save paired with the custom-pattern list from after it); both halves are always fully published, so no call is ever redacted under no rules
- [Phase 23]: 23-03: the busy seam uses TWO finally layers behind one compare-and-set — the EDT tail covers a throwing completion callback, the worker's catch covers a worker that dies before posting its tail, and the CAS keeps the observed transition exactly [true, false]
- [Phase 23]: 23-03: setActionsBusy disables BOTH action buttons, because D-13 puts saveSettings and restoreDefaultsWithConfirmation on one async path and Restore is the other door into the double-save race
- [Phase 23]: 23-03: FLAG-23-01 recolor IMPLEMENTED (Save goes outlineVariant/onSurfaceVariant while busy), recorded as a deliberate choice with the escape hatch left to live UAT
- [Phase 23]: 23-03: restoreDefaultsWithConfirmation is asserted structurally, not behaviourally — JOptionPane.getRootFrame() throws HeadlessException so this caller cannot be driven headlessly; the source-read is paired with its own build.gradle.kts inputs.file declaration in the same commit
- [Phase 23]: 23-04: deleteSession was split into a modal half and an internal deleteConfirmedSession — JOptionPane throws HeadlessException, so every line of the teardown sat below an unreachable statement and could not be asserted at all
- [Phase 23]: 23-04: 23-01's suggested per-cause supersedeReason parameter was NOT implemented — three of four exits share cancelInFlightRequest, whose signature is a detekt-baseline ReturnCount key, so a reason reading 'cancelled' for an unload would be worse than one honest generic reason
- [Phase 23]: 23-04: the session-delete exit clears the busy state CONDITIONALLY on a worker having been superseded — unconditionally clobbers a backend turn in another session, not at all leaves Send hidden forever
- [Phase 23]: 23-04: transcript rows are identified by BODY text, never by header — ChatMessagePanel renders every non-user role as the literal 'AI', which made 23-01's contains('Tool result: ...') assertion false by construction and therefore unfalsifiable
- [Phase 23]: 23-04: S-08 asserts the measured consequence (a /tool supersedes the chain step via the panel-wide running-tool cell) instead of the plan's 'chain unharmed, still terminates at 8', which that cell makes unreachable
- [Phase 23]: 23-05: SC5's evidence is a POSITIVE body assertion plus two frozen counters, not a diff — the working branch IS main, so git merge-base HEAD main equals HEAD and the diff-based criterion was empty by construction
- [Phase 23]: 23-05: the E5 source assertion is scoped to each OffEdtDispatch call's own work argument — a file-wide scan reports shutdown()'s EDT-marshalled block, which legitimately reads sessionPanels, and that false positive is how a real guard gets deleted as noise
- [Phase 23]: 23-05: assert an extraction's site count BEFORE asserting anything about what it extracted — a none{} over an empty list is a passing test about nothing
- [Phase 23]: 23-05: 'assertEdt() and its six call sites' is a mis-measurement carried by four Phase 23 artifacts — the six grep hits are one declaration, one comment and FOUR invocations; the count of 6 is still SC5's evidence because it is unmoved
- [Phase 23]: 23-05: detekt LargeClass on the grown test class answered with an inline @Suppress carrying its reason — the suite name is pinned by the PR-gate filter so splitting is not free, and detekt-baseline.xml is the v0.10.0 milestone metric
- [Phase 23]: 23-05: SC6 evidenced structurally — the phase has ONE dispatch seam (OffEdtDispatch), which is why ChatPanel.kt's invokeLater count is still 11 after four plans of new asynchronous work
- [Phase 23]: 23-07: setSendingState's new toolsBtn write reads the FIELD (!isSending) rather than the parameter — it makes the busy rule textually identical at both enforcement sites and satisfies the plan's own three-occurrence gate without relaxing it
- [Phase 23]: 23-07: the Task 2 supersede is driven through Cancel, not a session delete — deleteConfirmedSession detaches the panel, so the row-count clause could never have failed
- [Phase 23]: 23-07: OffEdtDispatch's two in-the-try sinks needed an ESCAPE assertion, not a settle assertion — the finally runs either way, so the plan's specified clauses were vacuous; measured by probes that came back red only on the added clause
- [Phase 23]: 23-07: transcript-absence is asserted as a row COUNT — the row-TEXT form is false by construction because ChatMessagePanel renders every non-user role as the literal 'AI'
- [Phase 23]: 23-07: no toolDecisionReporter.report and no durationMs in the new audit record — the user-originated paths are UNGATED by SC5 and measure no duration, so either would be a fabricated audit field
- [Phase 23]: 23-07: red probes must run against a COMMITTED baseline — git checkout -- restores the correct implementation only if it is committed, otherwise it discards the work
- [Phase 23]: 23-06: TWO persist helpers (persistSettings / persistSettingsAndApplyMcp) rather than one flag-taking helper — McpSupervisor.stop() clears ScannerTaskRegistry and CollaboratorRegistry, so an unconditional MCP apply on a passive toggle would drop live scanner tasks
- [Phase 23]: 23-06: MainTab.kt:111 deliberately NOT routed through the persist queue — the send path depends on supervisor.applySettings completing before the turn is sent; recorded as D-23-06-1 / T-23-06-08 (high, accept)
- [Phase 23]: 23-08: red probe 3 recorded as a MEASUREMENT not a confirmation — removing saveGeneration.incrementAndGet() from shutdown() leaves the test GREEN because isCurrent is a conjunction and disposed = true alone already falsifies it; the counter is kept for the save-supersedes-save case D-10 currently makes unreachable
- [Phase 23]: 23-08: a supersede's KDoc names the window it does NOT close, because overclaiming makes the next reader stop looking
- [Phase 23]: seal: the api-coverage verify:pre gate fired on two false-positive signals (`wraps api`, `(surface) api`, both tracing to the Burp Montoya host API) and was closed with a reasoned COVERAGE.md no-integration declaration rather than a fabricated matrix
- [Phase 23]: seal: four SUMMARYs (23-01, 23-02, 23-04, 23-06) shipped with no `## Threat Flags` section, so their 14 registered threats had no executor self-report; all 14 were closed by direct code reading in 23-SECURITY.md — a missing Threat Flags section should count as an executor self-check failure in future phases
- [Phase 23]: Task 2's scanner supersede test ships as TWO shapes, not the plan's one: three sequential early-return guards mean a supersede landing before the first short-circuits the rest, so a single shape leaves every later guard unfalsifiable — Measured — probes 2-1 and 2-2 now fail on different lines naming different mocks; under the single-shape design 2-2 would have passed green with the active-scanner guard deleted
- [Phase 23]: WR-11 belongs to Phase 23, not Phase 26/QUAL-07 — checked against both texts rather than assumed — QUAL-07 covers the detekt baseline's direction, the disposition of ChatPanel's assert()-based enforcement, and SecretCipher docs; none is CI wiring. edtGuardWithoutAssertionsTest and the check(...) it proves were both created by plan 23-02 inside this phase
- [Phase 23]: The settings supersede's AtomicLong is redundant with the disposed flag at unload — measured by red probe 1-3, kept for the save-supersedes-save case D-10 currently makes unreachable — isCurrent is a conjunction, so disposed = true alone falsifies it; removing saveGeneration.incrementAndGet() from shutdown() left the test green
- [Phase 24]: 24-01: SC1's 'assert by injecting a throw' is met per-site only for ActiveAiScanner.processQueue — neither registry cleanupExpired has an external throw lever and manufacturing one needs a test seam T-23-06-07 forbids, so those two are carried by mechanism (GuardedSchedulingTest) plus routing (SchedulerGuardCoverageTest)
- [Phase 24]: 24-01: one whole-tree inputs.dir('src/main/kotlin') on tasks.test (mainSourceTreeStructuralInputs) replaces per-file declarations for every Phase 24 structural assertion — a tree-walking allowlist whose inputs list known files is blind to a scheduler added in an undeclared file; counterfactual measured (guard IS served UP-TO-DATE without it)
- [Phase 24]: 24-01: git stash is prohibited in a GSD worktree — refs/stash lives in the parent .git and is shared across every linked worktree, so a plan's 'git stash push <path> ... git stash pop' red-probe recipe must be run as 'git checkout <ref> -- <path>' then 'git checkout HEAD -- <path>'

### Roadmap Evolution

- 2026-05-27: Phase 7 added — Proxy Transport + MCP Scope Hardening (closes GitHub issue #69; parallel-safe with Phases 1, 4, 5; must merge before Phase 6).
- 2026-05-28: Phase 8 added — BApp Store resubmission (MCP pivot to extension-native tools + `-PstoreBuild` gate, gate all AI calls on `ai.isEnabled()`, migrate passive scanning to `ScanCheck.passiveAudit()`, confirm name). Addresses PortSwigger review feedback on issue #231; follows Phase 07. Approved plan seed: ~/.claude/plans/drifting-hatching-sphinx.md.
- 2026-05-29: Phases 9-11 added — v0.8.0 UI/UX Overhaul milestone. Phase 9: Design System Foundation (UI-01). Phase 10: MCP Tools Tab Redesign (UI-03, UI-04, UI-05, UI-07). Phase 11: Settings Tabs + Theme Rollout (UI-02, UI-06, UI-08, UI-07). All 8 UI-* requirements mapped; 100% coverage.
- 2026-06-10: Phases 12-19 added — v0.9.0 Hardening, Quality & New Capabilities milestone. 22 requirements mapped across 8 phases. Hard ordering constraints from research enforced: SEC first, QUAL-01 split last.

### Pending Todos

[From .planning/todos/pending/ — ideas captured during sessions]

[No pending todos — all pre-planning items resolved as of Phase 19.]

### Blockers/Concerns

[Issues that affect future work]

- **✅ RESOLVED 2026-06-12 — Phase 16 blocker dissolved (Path A, NO Kotlin bump).** A compile spike proved `kotlin-sdk:0.5.0` already ships the full MCP client (`Client`/`SseClientTransport`/`StdioClientTransport`) and compiles under Kotlin 2.1.21 with only `ktor-client-core/cio:3.1.3` added. No Kotlin/Ktor bump required; human-only Burp ClassLoader gate does not apply. Phase 16 executed successfully.
- **Phase 16 human-UAT pending** — Code-complete (5/6 plans committed, automated-verified); SC1 real-server connect and SC5 live-Burp load are human-UAT items tracked in `16-HUMAN-UAT.md`.

### Quick Tasks Completed

| # | Description | Date | Commit | Directory |
|---|-------------|------|--------|-----------|
| 260527-f7q | Fix bugs 66, 67, 68: CLI tokenizer, Copilot CLI hang, OpenAI-compatible diagnostics | 2026-05-27 | 8a6af50 | [260527-f7q-fix-bugs-66-67-68-cli-tokenizer-copilot-](./quick/260527-f7q-fix-bugs-66-67-68-cli-tokenizer-copilot-/) |
| 260602-v08 | Bump version to 0.8.0 + promote CHANGELOG [Unreleased] → [0.8.0] | 2026-06-02 | 55f0b28 | — |
| 260602-cl8 | Complete CHANGELOG [0.8.0]: Phase 07 (#69) scope hardening/transport + backend fixes #66/67/68 | 2026-06-02 | 8caf0cb | — |
| 260714-dp1 | BApp Store code-review quick-wins (findings 3, 4, 6, 8 + verify 5) | 2026-07-14 | 72dcd34..e9333b4 | [260714-dp1-bapp-store-code-review-quick-wins-findin](./quick/260714-dp1-bapp-store-code-review-quick-wins-findin/) |
| 260714-ekv | BApp Store code-review batch B (heavy findings 1, 2, 7) | 2026-07-14 | 80c45bd, b113a61, d1ccc56 | [260714-ekv-bapp-store-code-review-batch-b-heavy-fin](./quick/260714-ekv-bapp-store-code-review-batch-b-heavy-fin/) |

## Deferred Items

Items acknowledged and carried forward from previous milestone close:

| Category | Item | Status | Deferred At |
|----------|------|--------|-------------|
| v2 | MCP-V2-01 — user-registered MCP server (#41) | Promoted to CAP-02 in v0.9.0 | 2026-05-13 |
| v2 | REL-V2-01 — opt-in local-only diagnostics endpoint | Deferred to post-v0.9.0 | 2026-05-13 |

## Session Continuity

Last session: 2026-08-21T13:34:19.433Z
Stopped at: Completed 24-01-PLAN.md (wave 1 gate) — waves 2-4 unblocked
Resume file: None
