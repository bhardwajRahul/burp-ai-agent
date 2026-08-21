---
phase: 23
slug: edt-confinement-ui-responsiveness
status: verified
# threats_open = count of OPEN threats at or above workflow.security_block_on severity (the blocking gate)
threats_open: 0
asvs_level: 1
block_on: high
created: 2026-08-21
---

# Phase 23 — Security

> Per-phase security contract: threat register, accepted risks, and audit trail.

**Register origin:** authored at plan time. All eight `*-PLAN.md` files carry a `<threat_model>`
block; no retroactive STRIDE was required.

---

## Trust Boundaries

| Boundary | Description | Data Crossing |
|----------|-------------|---------------|
| model output → `ToolApprovalGate` | Attacker-influenceable model context selects a tool; the SEC-06 gate is the control. This phase changes **where** an approved call runs, never **whether** it runs. | tool id + argument JSON, attacker-influenceable |
| any caller → `executeToolResult` | Convergence point for the chat path, the MCP-server path, the `ext:` branch and both `sendRequest` sites. The throwing door guard is a precondition on this boundary. | tool id, args, `McpToolContext`, origin |
| EDT → worker thread | Everything the worker needs is captured on the EDT first. The five `@GuardedBy("EDT")` maps do not cross. | `ToolCallCapture` value record |
| worker thread → Burp (`api.http().sendRequest`) | The call really leaves the process; it cannot be recalled. | full HTTP request to the target under test |
| EDT → settings worker | The `AgentSettings` snapshot crosses; Swing components do not. | `privacyMode`, `customRedactionPatterns`, `auditEnabled`, MCP bind/token |
| settings worker → global privacy state | `Redaction.setCustomPatterns` and `audit.setEnabled` publish policy that concurrent tool workers read. | redaction policy, audit toggle |
| settings worker → MCP supervisor / local network | `mcpSupervisor.applySettings` starts or stops a Ktor listener on `127.0.0.1`; reaches `KtorMcpServerManager.stop()`'s bounded 10 s wait. | bind address, port, bearer token |
| extension process → Burp Preferences (on-disk) | `settingsRepo.save()` persists ~107 sequential preference keys. | persisted settings, incl. privacy policy |
| extension lifecycle → Burp scanner engine | `passiveAiScanner.setEnabled` / `activeAiScanner.setEnabled` re-arm background analysis that sends traffic to an AI backend. | scanner enablement |
| worker tail → disposed panel / torn-down classloader | After a session delete, project change or unload the panel and its maps may be gone. The supersede check is the control. | tool result, audit metadata |
| extension → audit log | `AiRequestLogger` and `AuditLogger` are the only durable record of what the agent did. | decision + request metadata |
| user-typed `/tool` args → the executor | User-originated and deliberately ungated (Phase 22 SC5). Untrusted only in that the args are free text; the user is the author. | free-text tool arguments |
| CI workflow → shipped artifact | Gates that run on a pull request are the only automated statement about what the shipped JAR guarantees. | test/gate results |

---

## Threat Register

37 threats. 14 verified by this audit against the implementation; 23 previously discharged in the
`## Threat Flags` sections of `23-03`, `23-05`, `23-07` and `23-08` SUMMARY files.

### Verified by this audit (2026-08-21)

| Threat ID | Category | Component | Severity | Disposition | Mitigation / Evidence | Status |
|-----------|----------|-----------|----------|-------------|------------|--------|
| T-23-01 | Repudiation | `finishApprovedToolCall` cancel/supersede branch | high | mitigate | `ChatPanel.kt:3209-3300` — all three exits emit `toolDecisionReporter.report` → `aiRequestLogger?.log`; ordering is a data dependency (metadata is the report's return value). `ChatPanelEdtConfinementTest.cancellingARunningToolDiscardsItsResultAndStillAuditsTheCall:366` | closed |
| T-23-02 | Tampering | the five `@GuardedBy("EDT")` maps, read from the new worker | high | mitigate | `ChatPanel.kt:3124-3140` builds `ToolCallCapture` on the EDT; `:3151` worker body references only the capture + `McpToolExecutor`. Maps still `linkedMapOf`; `ConcurrentHashMap` count = 0 (Phase 17 decision preserved) | closed |
| T-23-03 | Repudiation | worker body throwing a `Throwable` Burp will not report | high | mitigate | `OffEdtDispatch.kt:108-111` `runCatching(work)` → `logError`; EDT tail wrapped `:116-120`, settle hook in `finally` `:121-124`. `ChatPanelEdtConfinementTest.aThrowingToolBodyIsReportedRatherThanSwallowed:842` | closed |
| T-23-04 | Elevation of Privilege | a future fourth parse-and-execute path calling `executeToolResult` directly | high | mitigate | `McpToolExecutorImpl.kt:159-164` — `check(!SwingUtilities.isEventDispatchThread())` is the first statement of `executeToolResult`, before `canonicalToolId` and the `ext:` early return. `check` ⇒ real `IllegalStateException`. Pinned `-da` by `build.gradle.kts:264-277` (`edtGuardWithoutAssertionsTest`) | closed |
| T-23-05 | Elevation of Privilege | TOCTOU between `ToolApprovalGate` producing `Run` and the worker starting | high | mitigate | `ChatPanel.kt:2611` `assertEdt()` → `:2621` gate evaluated on the EDT; outcome frozen into the capture `:3131`/`:3189`; worker consumes only `approved.origin`. `ChatPanelEdtConfinementTest.aDeniedToolCallStartsNoWorkerAndStillContinuesTheConversation:424` | closed |
| T-23-10 | Elevation of Privilege | double execution of one approved call | high | mitigate | `ChatPanel.kt:105` `clearIfMatches` = `compareAndSet`; `:3215` single-owner resolution, loser returns `:3224`. `ChatPanelEdtConfinementTest.aSupersededConfirmEachCallReachesBurpExactlyOnce:765` — `verify(scope, times(1))` after a long drain | closed |
| T-23-11 | Repudiation | a superseded call reaching Burp but never being logged | high | mitigate | `ChatPanel.kt:946` — the `deleteConfirmedSession` exit that inherits nothing; the returning worker's CAS fails into `discardSupersededToolResult:3314-3342`, emitting the ordered pair with `supersedeReason`. S-05…S-09 assert `assertOrderedAuditPair(…, extraKeys = setOf(SUPERSEDE_REASON_KEY))` | closed |
| T-23-12 | Denial of Service | a stuck non-daemon worker blocking extension unload | medium | mitigate | `OffEdtDispatch.kt:127` — `isDaemon = true`; `ChatPanel.shutdown()` contains no `join`/`get`/`awaitTermination` on the worker. `ChatPanelEdtConfinementTest.unloadSupersedesTheRunningToolWithoutWaitingForIt:664`, `:691` | closed |
| T-23-06-03 | Tampering | stale generation overwriting a newer settings write | medium | mitigate | `SettingsPersistQueue.kt:45,48` monotonic `AtomicLong`s; `:75` generation minted on the calling thread; `:113-118` `applyIfCurrent` drops on `generation <= applied.get()` and advances `applied` before the body. `SettingsPersistQueueTest.anOlderGenerationIsDroppedRatherThanAppliedOverANewerOne:195` | closed |
| T-23-06-04 | Denial of Service | Burp UI frozen up to 10 s per Settings-tab or header MCP toggle | high | mitigate | `SettingsPanelSettingsIO.kt:645-679` → `burp-ai-settings-save`; `MainTab.kt:584-602` → `burp-ai-settings-sync`. `KtorMcpServerManager.kt:270`'s bounded `future.get(10, SECONDS)` is reached only from those worker threads on the toggle paths. `SettingsPersistQueueTest.theSubmittingThreadReturnsWhileTheApplyIsStillBlocked:80`. **Scope caveat — see Caveat 1 below.** | closed |
| T-23-06-05 | Denial of Service | `dispose()` itself blocking the EDT for 10 s at unload | medium | mitigate | `SettingsPanelActions.kt:178-185` — `disposed = true` + generation bump + two `Timer.stop()`; no lock, no join. `SettingsPersistQueue.kt:97-99` `dispose()` explicitly must not take `lock`. `SettingsPersistQueueTest.disposeStopsNewWorkAndDoesNotBlockTheCaller:247` | closed |
| T-23-06-06 | Tampering | MCP listener started or stopped after `App.shutdown()` began | high | **transfer** | Local half bounded: `MainTab.kt:922-928` disposes the queue and panel first; `App.kt:221` runs `mainTab?.shutdown()` before `:233 mcpSupervisor.shutdown()`. **Transfer receiver named and landed** — `SettingsPanel.kt:59-76` names CR-01 / plan 23-08 as owner; receiving code at `SettingsPanelSettingsIO.kt:637,655` with `isCurrent` re-tested at `:550`, `:581`, `:599` and `:633 if (disposed) return`. Discharge `23-08-SUMMARY.md:285`; commits `84f4c83`, `915b6b3` | closed |
| T-23-06-07 | Elevation of Privilege | Scanner task / Collaborator registries cleared by an unintended `McpSupervisor.stop()` | medium | mitigate | `MainTab.kt:554-564` (`persistSettings`, disk only) vs `:584-602` (`persistSettingsAndApplyMcp`) — two narrow helpers, so the five non-MCP sites cannot reach `McpSupervisor.kt:132-139`'s registry clears. Enforced by `SettingsPersistQueueTest.everyMainTabSettingsWriteGoesThroughThePersistQueue:333`; `build.gradle.kts:211-222` declares `MainTab.kt` a `tasks.test` input so the gate cannot serve from cache | closed |
| T-23-06-SC | Tampering | supply chain | — | not applicable | `git show 8ca0194` / `2f6d1bc` — the only 23-06 commits touching build files; both add `inputs.file(...)` declarations inside `tasks.test` only. `gradle/libs.versions.toml` untouched; no dependency coordinate added | closed |

### Previously discharged (executor-declared in SUMMARY `## Threat Flags`)

| Threat ID | Category | Component | Severity | Disposition | Discharge record | Status |
|-----------|----------|-----------|----------|-------------|------------|--------|
| T-23-06 | Denial of Service | a missed call site turning a UI freeze into an `IllegalStateException` in shipped Burp | medium | accept | `23-02-SUMMARY.md:137` — accepted cost put before the maintainer at the `door-guard` checkpoint | closed |
| T-23-07 | Information Disclosure | a tool worker observing a half-applied privacy policy during a save | high | mitigate | `23-03-SUMMARY.md:336` — immutable per-call `McpToolContext` snapshot + whole-list `@Volatile` publication, pinned by the E8 test | closed |
| T-23-08 | Tampering | two overlapping saves, each with its own MCP stop/start, the second finishing first | high | mitigate | `23-03-SUMMARY.md:336` — both action buttons disabled for the whole flight | closed |
| T-23-09 | Denial of Service | a completion path that fails to lower the busy seam | medium | mitigate | `23-03-SUMMARY.md:336` — two-layer `finally`, asserted on the failure path | closed |
| T-23-13 | Tampering | the five `@GuardedBy("EDT")` maps, if a later edit reopens them | high | mitigate | `23-05-SUMMARY.md:325` — structural assertion over every dispatch worker lambda + behavioural assertion that the tail uses its capture | closed |
| T-23-06-01 | Tampering | `AgentSettingsRepository.save` reached concurrently from two `MainTab` listeners | high | mitigate | `23-06-SUMMARY.md:78,141` — one `ReentrantLock`, submission order; lock-removal red probe came back RED | closed |
| T-23-06-02 | Information Disclosure | persisted `privacyMode` + `customRedactionPatterns` | high | mitigate | `23-06-SUMMARY.md:141` — same lock prevents pairing a permissive `privacyMode` with a foreign pattern list | closed |
| T-23-06-08 | Tampering / Info. Disclosure | `MainTab.kt:111`'s EDT `settingsRepo.save`, outside the queue lock | high | accept | `23-06-SUMMARY.md:250` + `deferred-items.md` — residual `D-23-06-1`, both consequences written up | closed (accepted) |
| T-23-07-01 | Repudiation | `finishUserOriginatedToolCall`'s silent supersede branch | high | mitigate | `23-07-SUMMARY.md:268` — Task 2 | closed |
| T-23-07-02 | Tampering | `openToolDialog` reachable in state S3 | high | mitigate | `23-07-SUMMARY.md:268` — Task 1 | closed |
| T-23-07-03 | Tampering | `updateChatAvailability` restoring idle affordances mid-run | high | mitigate | `23-07-SUMMARY.md:268` — Task 1 | closed |
| T-23-07-04 | Tampering | a tool tail clearing an unrelated backend turn's busy state | medium | mitigate | `23-07-SUMMARY.md:268` — Task 1 | closed |
| T-23-07-05 | Denial of Service | `OffEdtDispatch`'s unguarded sinks | high | mitigate | `23-07-SUMMARY.md:268` — Task 3 | closed |
| T-23-07-06 | Repudiation | a throwing sink swallowing the ORIGINAL failure | medium | mitigate | `23-07-SUMMARY.md:268` — Task 3, `runCatching` around the logger | closed |
| T-23-07-SC | Tampering | supply chain | — | not applicable | `23-07-SUMMARY.md:268` — no dependency added | closed |
| T-23-08-01 | Elevation of Privilege | MCP server started by a save worker after `mcpSupervisor.shutdown()` | critical | mitigate | `23-08-SUMMARY.md:285` — Task 1 (CR-01) | closed |
| T-23-08-02 | Information Disclosure | AI scanners re-enabled after `App.shutdown()` disabled them | high | mitigate | `23-08-SUMMARY.md:285` — Task 2 | closed |
| T-23-08-03 | Denial of Service | `SettingsPanel.shutdown()` blocking the EDT for ten seconds | high | mitigate | `23-08-SUMMARY.md:285` — structural, verification 13 | closed |
| T-23-08-04 | Denial of Service | a save submitted after unload raising a busy seam nobody lowers | medium | mitigate | `23-08-SUMMARY.md:285` — Task 2, `if (disposed) return` | closed |
| T-23-08-05 | Tampering | a future revert of `check(...)` to `assert(...)` | high | mitigate | `23-08-SUMMARY.md:285` — Task 3, WR-11 CI gate | closed |
| T-23-08-06 | Repudiation | supersede abandoning `audit.setEnabled` / `Redaction.setCustomPatterns` mid-body | medium | accept | `23-08-SUMMARY.md:285` — documented in shipped KDoc | closed (accepted) |
| T-23-08-07 | Tampering | the in-flight window the supersede does not close | medium | accept | `23-08-SUMMARY.md:285` — documented in shipped KDoc | closed (accepted) |
| T-23-08-SC | Tampering | supply chain | — | not applicable | `23-08-SUMMARY.md:285` — `build.gradle.kts` changed by comment only | closed |

*Status: open · closed · open — below `high` threshold (non-blocking)*
*Severity: critical > high > medium > low — only open threats at or above `workflow.security_block_on` count toward `threats_open`*
*Disposition: mitigate (implementation required) · accept (documented risk) · transfer (third-party)*

---

## Accepted Risks Log

| Risk ID | Threat Ref | Rationale | Accepted By | Date |
|---------|------------|-----------|-------------|------|
| R-23-01 | T-23-06 | A missed call site turns a UI freeze into an `IllegalStateException` in shipped Burp. Accepted at the `door-guard` checkpoint: the guard's value is that it is *enforced*, and a helper-only variant reverts the guarantee to "correct today". | maintainer (23-02 checkpoint) | 2026-08-20 |
| R-23-02 | T-23-06-08 / D-23-06-1 | `MainTab.kt:111` is an eighth EDT `settingsRepo.save()` site inside the `applySettings` lambda passed to `ChatPanel`. The send path depends on `supervisor.applySettings(settings)` completing before the turn is sent, so the lambda cannot be moved wholesale within this run's scope. Two consequences written up in `deferred-items.md`: a torn-write window outside the queue lock, and a narrow SC4 residual bounded by the fact that `:111` passes current settings. | maintainer (23-06) | 2026-08-21 |
| R-23-03 | T-23-08-06 | `SettingsPanel.shutdown()`'s supersede can abandon `audit.setEnabled` and `Redaction.setCustomPatterns` mid-body. Documented in shipped KDoc rather than only in the plan. | maintainer (23-08) | 2026-08-21 |
| R-23-04 | T-23-08-07 | A mutation already past its `isCurrent` guard runs to completion; the supersede does not close that in-flight window. Stated, not overclaimed, at `SettingsPanel.kt:70-75`. | maintainer (23-08) | 2026-08-21 |

*Accepted risks do not resurface in future audit runs.*

---

## Auditor Caveats (informational — no threat opened)

1. **T-23-06-04's prose is broader than the code.** The mitigation text says the bounded
   `future.get(10, TimeUnit.SECONDS)` "never runs on the EDT". That holds for the two components the
   threat names (Settings tab, header MCP toggle), but `MainTab.kt:118-129` — the `applySettings`
   lambda handed to `ChatPanel` — still calls `settingsRepo.save` **and** `mcpSupervisor.applySettings`
   inline on the EDT, driven from `ChatPanel.kt:604` on every chat send. The `stop()` limb is bounded
   there because that lambda passes *current* settings and `McpSupervisor.kt:104-117` early-returns
   when already-running-and-unchanged. This is accepted residual `R-23-02` / `T-23-06-08`, out of this
   audit's scope — recorded so T-23-06-04's phrasing is not read as an unconditional guarantee.
2. **T-23-11's "audit pair" is a pair only where a decision record exists.** The user-originated
   superseded exit (`ChatPanel.kt:1211-1226`, `/tool` and the tool dialog) emits `aiRequestLogger?.log`
   with `runStatus = SUPERSEDED_RUN_STATUS` but no `toolDecisionReporter.report` — deliberately, since
   SC5 leaves those paths ungated and there is no decision to report (`:1181-1197`). The threat as
   written ("a superseded call reaching Burp but never being logged") is closed: every superseded exit
   is logged.

---

## Process Finding (WARNING — non-blocking)

`23-01-SUMMARY.md`, `23-02-SUMMARY.md`, `23-04-SUMMARY.md` and `23-06-SUMMARY.md` contain no
`## Threat Flags` section, though their plans carry 14 registered threats between them. `23-03`,
`23-05`, `23-07` and `23-08` do. There was therefore no executor-declared new attack surface to
cross-check for those four plans, and the closures above rest entirely on direct code reading rather
than executor self-report. Classified WARNING, not blocking — but the omission is what made this
audit necessary, and a future phase should treat a missing `## Threat Flags` section as an executor
self-check failure.

---

## Security Audit Trail

| Audit Date | Threats Total | Closed | Open | Run By |
|------------|---------------|--------|------|--------|
| 2026-08-21 | 37 | 37 | 0 | gsd-security-auditor (ASVS L1, block_on `high`) |

---

## Sign-Off

- [x] All threats have a disposition (mitigate / accept / transfer)
- [x] Accepted risks documented in Accepted Risks Log
- [x] `threats_open: 0` confirmed
- [x] `status: verified` set in frontmatter

**Approval:** verified 2026-08-21
