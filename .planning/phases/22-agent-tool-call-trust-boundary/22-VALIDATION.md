---
phase: 22
slug: agent-tool-call-trust-boundary
status: planned
nyquist_compliant: true
wave_0_complete: false  # 22-01 lands it (wave 1)
created: 2026-08-13
---

# Phase 22 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.
> Derived from `22-RESEARCH.md` §"Validation Architecture". Task IDs in the
> Per-Task Verification Map are filled in by `/gsd-plan-phase` once plans exist.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit Jupiter 6.0.3 + `kotlin("test")` + mockito-kotlin 5.4.0 (all already present — no install needed) |
| **Config file** | `build.gradle.kts` (`tasks.test`, lines 152-172) — `useJUnitPlatform()`, `jvmArgs("-ea")` |
| **Quick run command** | `JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test --tests "*ToolGate*" --tests "*ToolApproval*" --tests "*ToolDecision*" --tests "*SecTier*" --tests "*TierParity*" --tests "*DecisionsAdr*"` |
| **Full suite command** | `JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test` |
| **Phase gate command** | `JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew ktlintCheck detekt test` |
| **Estimated runtime** | ~15 s quick / full suite is 669 tests as of Phase 21 close |

> **PR-gate caveat (research Pitfall 7 — read before naming any test file).** CI runs
> `./gradlew test -PexcludeHeavyTests=true`, which **excludes** `*IntegrationTest`,
> `*ConcurrencyTest`, `*BackpressureTest`, `*RestartPolicyTest`, `*SupervisionTest`.
> A file named `ChatPanelToolGateIntegrationTest` would silently not run on pull
> requests. Use the names in Wave 0 below.

> **JDK note.** Gradle 8.12.1 breaks on this machine's default JDK. Every command
> above must carry the `JAVA_HOME` prefix.

---

## Sampling Rate

- **After every task commit:** Run the quick run command. `ToolApprovalGateTest`, `ToolDecisionReporterTest`, `ToolApprovalCardTest` and `DecisionsAdrTest` do NOT contain the substring `ToolGate`, which is why the filter above names them explicitly.
- **After every plan wave:** Run `JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew ktlintCheck detekt test`
- **Before `/gsd-verify-work`:** Full suite green, plus the detekt baseline unchanged from milestone start (1096 entries)
- **Max feedback latency:** ~15 seconds (quick), well inside the sampling requirement

---

## Per-Task Verification Map

Task IDs assigned by `/gsd-plan-phase` on 2026-08-13. `➡️ 22-NN` in the File Exists
column means the file is created by that plan. Every Gradle command below must be
prefixed with `JAVA_HOME=$(/usr/libexec/java_home -v 21)`.

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 22-01 T1 | 22-01 | 1 | SEC-06 / SC4 | T-22-13 | Headless guard on `ChatPanel.kt:377-380` lets a real `ChatPanel` construct under test; `tasks.test` runs headless | infra | `./gradlew test` | ➡️ 22-01 | ⬜ pending |
| 22-01 T2 | 22-01 | 1 | SEC-06 / SC4 | T-22-13 | Shared harness constructs a real `ChatPanel` from deep stubs and drives the real Send button | infra | `./gradlew compileTestKotlin` | ➡️ 22-01 | ⬜ pending |
| 22-01 T3 | 22-01 | 1 | SEC-06 / SC4 | T-22-01 | **Pre-fix baseline** — a model-emitted call reaches Burp with no decision (GREEN today; 22-07 T3 inverts it) | integration | `./gradlew test --tests "*ChatPanelToolGateTest*"` | ➡️ 22-01 | ⬜ pending |
| 22-02 T1 | 22-02 | 1 | SEC-06 / SC6 | T-22-14, T-22-15 | All 59 catalog tools declare a required non-defaulted `secTier`; omitting one does not compile | unit + compile | `./gradlew test` | ➡️ 22-02 | ⬜ pending |
| 22-02 T2 | 22-02 | 1 | SEC-06 / SC6 | T-22-02, T-22-15 | The `AUTO` set is exactly the enumerated 19; no `unsafeOnly` tool is `AUTO`; `ai_analyze`/`ai_passive_scan` prove the axes are independent | unit | `./gradlew test --tests "*McpToolCatalogTierParityTest*"` | ➡️ 22-02 | ⬜ pending |
| 22-03 T1 | 22-03 | 2 | SEC-06 / SC6 | T-22-12 | Gate and executor consume one `canonicalToolId`; no alias map exists under `ui/` | unit | `./gradlew test` | ➡️ 22-03 | ⬜ pending |
| 22-03 T2 | 22-03 | 2 | SEC-06 / SC5, SC6 | T-22-11, T-22-16 | `ext:` derives `CONFIRM_EACH`; unknown fails closed; the model-approved origin is unconstructible outside the gate file | compile + grep | `./gradlew compileKotlin` | ➡️ 22-03 | ⬜ pending |
| 22-03 T3 | 22-03 | 2 | SEC-06 / SC6 | T-22-12, T-22-16 | All ten aliases resolve to their canonical tool's tier; all 59 built-ins resolve; unknown never returns `AUTO` | unit | `./gradlew test --tests "*SecTierResolutionTest*"` | ➡️ 22-03 | ⬜ pending |
| 22-04 T1 | 22-04 | 3 | SEC-06 / SC2, SC4 | T-22-08, T-22-18, T-22-21 | Three-tier `evaluate`/`resolve`, per-session memory, D-12 constant, monotone budget helpers; no opt-out parameter exists | compile | `./gradlew compileKotlin` | ➡️ 22-04 | ⬜ pending |
| 22-04 T2 | 22-04 | 3 | SEC-06 / SC2, SC4 | T-22-08, T-22-18, T-22-19, T-22-22 | Approve-once/for-session/deny/deny-for-session behave per D-11; `CONFIRM_EACH` never gains session memory; 8 denials terminate | unit | `./gradlew test --tests "*ToolApprovalGateTest*"` | ➡️ 22-04 | ⬜ pending |
| 22-05 T1 | 22-05 | 3 | SEC-06 / SC3 | T-22-06, T-22-09, T-22-23 | One `mcp_tool_decision` event + one Output line + the `MCP_TOOL_CALL` metadata map, from a single construction | compile | `./gradlew compileKotlin` | ➡️ 22-05 | ⬜ pending |
| 22-05 T2 | 22-05 | 3 | SEC-06 / SC3 | T-22-06, T-22-19, T-22-23, T-22-24 | Payload key order pinned; args hashed by default; denial is `status = denied`; `secTier` present on every event; Output line single-line and sanitized | unit | `./gradlew test --tests "*ToolDecisionReporterTest*"` | ➡️ 22-05 | ⬜ pending |
| 22-06 T1 | 22-06 | 3 | SEC-06 / SC2 | T-22-07, T-22-29 | Model text only in boxed `JTextField`/`JTextArea`; four or two actions per tier; token-only styling | compile | `./gradlew compileKotlin` | ➡️ 22-06 | ⬜ pending |
| 22-06 T2 | 22-06 | 3 | SEC-06 / SC2 | T-22-27, T-22-30 | Resolution removes buttons for an outcome row; both compact resolved variants; accessible description ends with the sanitized tool ID | compile | `./gradlew compileKotlin` | ➡️ 22-06 | ⬜ pending |
| 22-06 T3 | 22-06 | 3 | SEC-06 / SC2 | T-22-07, T-22-26, T-22-27 | `getClientProperty("html")` is null on every `JLabel`/`AbstractButton`; unknown tool labelled; button counts per tier | unit (headless Swing) | `./gradlew test --tests "*ToolApprovalCardTest*"` | ➡️ 22-06 | ⬜ pending |
| 22-07 T1 | 22-07 | 4 | SEC-06 / SC5 | T-22-11, T-22-19 | `executeTool` requires a non-defaulted origin at all eleven call sites; the gate runs before the executor; every non-`Run` outcome is fail-closed | unit + compile | `./gradlew test` | ➡️ 22-07 | ⬜ pending |
| 22-07 T2 | 22-07 | 4 | SEC-06 / SC2 | T-22-01, T-22-31 | The card supplies the decision; `AWAITING_DECISION` parks `onCompleted`; no branch leaves it undischarged; no new EDT marshalling | unit | `./gradlew test` | ➡️ 22-07 | ⬜ pending |
| 22-07 T3 | 22-07 | 4 | SEC-06 / SC2, SC4, SC5 | T-22-01, T-22-32 | **ACCEPTANCE GATE — must be RED against pre-gate code.** A `CONFIRM` model call never reaches Burp before a decision; `AUTO` still runs silently; neither user path is double-prompted | integration (real `ChatPanel`, real Send button) | `./gradlew test --tests "*ChatPanelToolGateTest*"` | ➡️ 22-07 | ⬜ pending |
| 22-08 T1 | 22-08 | 5 | SEC-06 / SC2 | T-22-10, T-22-33, T-22-36 | All five teardown paths route through one `resolvePending`; no implicit denial starts a backend turn; `:342` discarded-fallback fixed | unit | `./gradlew test` | ➡️ 22-08 | ⬜ pending |
| 22-08 T2 | 22-08 | 5 | SEC-06 / SC3 | T-22-09 | Every resolved branch — including `AUTO` and all five implicit-denial reasons — reports; the metadata map is merged, not duplicated | unit | `./gradlew test` | ➡️ 22-08 | ⬜ pending |
| 22-08 T3 | 22-08 | 5 | SEC-06 / SC2, SC3 | T-22-34, T-22-35 | `Awaiting approval` marker; scroll-to-pending on session switch; four lifecycle/audit integration tests | integration | `./gradlew test --tests "*ChatPanelToolGateTest*"` | ➡️ 22-08 | ⬜ pending |
| 22-09 T1 | 22-09 | 6 | SEC-06 / SC1 | T-22-37, T-22-39, T-22-40 | ADR-15 records the threat model, qualifies ADR-11's marker, carries D-05 verbatim, and closes with `Residual:` bullets | doc + human UAT | `./gradlew test --tests "*DecisionsAdrTest*"` | ➡️ 22-09 | ⬜ pending |
| 22-09 T2 | 22-09 | 6 | SEC-06 / SC1 | T-22-37, T-22-38 | ADR-15 exists, is the highest-numbered ADR, and its `AUTO` sentence matches the `SecTier` KDoc byte-for-byte | doc assertion | `./gradlew test --tests "*DecisionsAdrTest*"` | ➡️ 22-09 | ⬜ pending |
| 22-09 T3 | 22-09 | 6 | SEC-06 / SC1, SC2 | T-22-38 | `CONCERNS.md` corrected; `22-HUMAN-UAT.md` carries the four manual verifications with `result: [pending]` | doc + manual | `test -f .planning/phases/22-agent-tool-call-trust-boundary/22-HUMAN-UAT.md` | ➡️ 22-09 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

**The SC4 acceptance gate, stated concretely.** Research measured that against today's
`ChatPanel.maybeExecuteToolCall`, a model-emitted `scope_check` reaches
`api.scope().isInScope("http://evil.example/")` with no user decision. The assertion
`verify(api.scope(), never()).isInScope("http://evil.example/")` therefore **fails today
and passes after the gate lands**. A companion assertion,
`assertNotNull(findApprovalCard(panel.root))`, also fails today. Neither is vacuous and
neither is modelled — both drive the real production path from the real Send button. This
follows the Phase 20 SC4 / Phase 21 rule: **a test that passes both before and after the
change has not tested the defect.**

---

## Wave 0 Requirements

- [ ] `src/main/kotlin/.../ui/ChatPanel.kt:377-380` — headless guard on `Toolkit.getDefaultToolkit().menuShortcutKeyMaskEx`. **Blocks every SC2/SC4/SC5 integration test.** Two-line additive production change; measured sufficient (it is the only headless-hostile call in the whole construction path)
- [ ] `build.gradle.kts:153` — add `-Djava.awt.headless=true` to `tasks.test` `jvmArgs` so the harness behaves identically on macOS and `ubuntu-latest`
- [ ] `src/test/kotlin/com/six2dez/burp/aiagent/ui/ChatPanelTestHarness.kt` — shared fixture: deep-stub `MontoyaApi` + `AgentSupervisor`, a `sendChat` stub emitting a caller-supplied response, a depth-first component finder, an EDT drain helper. Covers SC2/SC4/SC5
- [ ] `src/test/kotlin/com/six2dez/burp/aiagent/ui/ChatPanelToolGateTest.kt` — SC2, SC4 acceptance gate, SC5
- [ ] `src/test/kotlin/com/six2dez/burp/aiagent/mcp/ToolApprovalGateTest.kt` — SC2 state machine, SC3 payload, SC4 iteration accounting, D-12 denial constant. **AWT-free**, mirroring the `redact/` package's dependency discipline
- [ ] `src/test/kotlin/com/six2dez/burp/aiagent/mcp/SecTierResolutionTest.kt` — SC6 resolution, `ext:` derivation, unknown fail-closed, alias parity
- [ ] `src/test/kotlin/com/six2dez/burp/aiagent/mcp/McpToolCatalogTierParityTest.kt` — SC6 completeness; asserts the exact `AUTO` set so a future promotion is a deliberate, reviewed diff
- [ ] Update `src/test/kotlin/com/six2dez/burp/aiagent/ui/McpToolTabModelTest.kt:29-38` — the one existing test helper that D-03's non-defaulted field breaks
- [ ] Framework install: **not needed** — JUnit Jupiter, mockito-kotlin and `kotlin("test")` are all present

**Risk to watch (research assumption A3/A4):** land the Wave-0 harness in its own commit
and watch the GitHub Actions run before building the rest of the phase on it. The
mitigation for a CI-only headless or mock-maker difference is cheap and definitive only
if the harness ships first.

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| The approval card renders legibly in live Burp — extension-derived title and tier visually distinct from model text, four labelled actions reachable, expandable args preview readable in light and dark theme | SEC-06 / SC2, D-07, D-11 | Visual and interaction quality is not assertable headlessly; the UI-SPEC for this phase defines the contract | Load the JAR in Burp, enable tools mode, send a prompt that elicits a `CONFIRM` tool call, inspect the card in both themes |
| ADR-15 is factually accurate — the threat model matches shipped behaviour and claims only what ships | SEC-06 / SC1 | Judgement, not a string match. Research recommends this as human UAT unless the planner adds a cheap guard for D-05's verbatim sentence | Read ADR-15 against `22-CONTEXT.md` D-14's four required elements; confirm no unqualified claim |
| Research assumption A1 — `intruder` / `intruder_prepare` stage a tab without launching an attack | SEC-06 / SC6 | Montoya `sendToIntruder` runtime semantics; affects one ADR sentence's wording, not tier assignment | Call each tool once in live Burp, confirm no outbound traffic |
| Research assumption A2 — `exportUserOptionsAsJson()` / `exportProjectOptionsAsJson()` include upstream-proxy credentials | SEC-06 / SC6 | Requires a configured Burp with an upstream proxy | Call `user_options_get` once in live Burp, inspect the export for credential material |

> Add these to `22-HUMAN-UAT.md` at execution time, matching the Phase 20 and Phase 21
> pattern. **Do not leave SC1 unassigned** — an ADR is the one success criterion that is
> easy to mark done without checking.

---

## Validation Sign-Off

- [x] All tasks have `<automated>` verify or Wave 0 dependencies (23/23 tasks carry an `<automated>` command)
- [x] Sampling continuity: no 3 consecutive tasks without automated verify (every task has one)
- [x] Wave 0 covers all MISSING references (plan 22-01 lands the guard, the harness and the baseline test in wave 1)
- [x] No watch-mode flags
- [x] Feedback latency < 20s
- [ ] SC4's acceptance gate confirmed RED against pre-fix `maybeExecuteToolCall` before the fix lands
- [x] `nyquist_compliant: true` set in frontmatter

**Approval:** task IDs assigned by `/gsd-plan-phase` on 2026-08-13; SC4's red-before-green proof is enforced as an acceptance criterion of 22-07 Task 3.
