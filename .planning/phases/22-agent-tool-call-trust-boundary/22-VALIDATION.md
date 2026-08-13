---
phase: 22
slug: agent-tool-call-trust-boundary
status: draft
nyquist_compliant: false
wave_0_complete: false
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
| **Quick run command** | `JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test --tests "*ToolGate*" --tests "*SecTier*" --tests "*TierParity*"` |
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

- **After every task commit:** Run the quick run command
- **After every plan wave:** Run `JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew ktlintCheck detekt test`
- **Before `/gsd-verify-work`:** Full suite green, plus the detekt baseline unchanged from milestone start (1096 entries)
- **Max feedback latency:** ~15 seconds (quick), well inside the sampling requirement

---

## Per-Task Verification Map

Task IDs are assigned at planning. Until then this maps success criteria to their
automated command and the Wave-0 file that must exist first.

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| TBD | TBD | 0 | SEC-06 / SC4 | Indirect prompt injection → tool selection | Headless guard on `ChatPanel.kt:377-380` lets a real `ChatPanel` construct under test | infra | `./gradlew test --tests "*ChatPanelToolGate*"` | ❌ W0 | ⬜ pending |
| TBD | TBD | 1 | SEC-06 / SC6 | Fail-closed access control | All 59 catalog tools declare a `secTier`; the `AUTO` set is exactly the enumerated 19 | unit | `./gradlew test --tests "*McpToolCatalogTierParityTest*"` | ❌ W0 | ⬜ pending |
| TBD | TBD | 1 | SEC-06 / SC6 | Fail-closed access control | `ext:server:tool` → `CONFIRM_EACH`; unknown → `CONFIRM_EACH`; all ten aliases resolve to their canonical tool's tier; gate and executor share one canonicalisation | unit | `./gradlew test --tests "*SecTierResolutionTest*"` | ❌ W0 | ⬜ pending |
| TBD | TBD | 1 | SEC-06 / SC2 | Human authorisation of the action | Approve once executes; Approve for session suppresses the next card for that tool; Deny returns the D-12 constant; Deny for session denies instantly with no card | unit (AWT-free seam) | `./gradlew test --tests "*ToolApprovalGateTest*"` | ❌ W0 | ⬜ pending |
| TBD | TBD | 1 | SEC-06 / SC4 | DoS through the safety control | 8 denials send at most 8 turns and terminate — monotone counter (D-13) | unit | `./gradlew test --tests "*ToolApprovalGateTest*"` | ❌ W0 | ⬜ pending |
| TBD | TBD | 1 | SEC-06 / SC3 | Log injection (CWE-117) + audit confidentiality | Model-supplied args hashed, never plaintext, with `verboseAudit = false`; control chars stripped, length capped | unit | `./gradlew test --tests "*ToolApprovalGateTest*"` | ❌ W0 | ⬜ pending |
| TBD | TBD | 2 | SEC-06 / SC4 | **ACCEPTANCE GATE** — must be RED against today's code | A model-emitted tool call does **not** reach Burp before a decision: `verify(api.scope(), never()).isInScope("http://evil.example/")` | integration (real `ChatPanel`, real Send button) | `./gradlew test --tests "*ChatPanelToolGateTest*"` | ❌ W0 | ⬜ pending |
| TBD | TBD | 2 | SEC-06 / SC2 | Human authorisation of the action | A `CONFIRM` model call adds an approval card and does not execute until resolved; denial sends a followup turn rather than erroring the session | integration | `./gradlew test --tests "*ChatPanelToolGateTest*"` | ❌ W0 | ⬜ pending |
| TBD | TBD | 2 | SEC-06 / SC5 | No double-prompt on user-originated calls | `ToolInvocationDialog` (`ChatPanel:928`) and `/tool` (`:2105`) execute with no card | integration | `./gradlew test --tests "*ChatPanelToolGateTest*"` | ❌ W0 | ⬜ pending |
| TBD | TBD | 2 | SEC-06 / SC5 | Unforgeable authorisation | A `ModelApproved` origin cannot be constructed outside the gate; the gate mints one | unit + KDoc negative-compilation note | `./gradlew test --tests "*ToolApprovalGateTest*"` | ❌ W0 | ⬜ pending |
| TBD | TBD | 2 | SEC-06 / SC3 | Non-repudiation | Every decision emits `MCP_TOOL_CALL` metadata carrying `toolName`, `decision`, `secTier`, `step`, plus an `AuditLogger` event and one Output-tab line | integration (metadata capture) | `./gradlew test --tests "*ChatPanelToolGateTest*"` | ❌ W0 | ⬜ pending |
| TBD | TBD | 3 | SEC-06 / SC1 | Threat model is written down and inheritable | ADR-15 exists in `DECISIONS.md`, numbered 15, containing D-05's `AUTO` sentence verbatim | doc assertion **or** human UAT — see Manual-Only below | `./gradlew test --tests "*DecisionsAdrTest*"` | ❌ W0 | ⬜ pending |

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

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 20s
- [ ] SC4's acceptance gate confirmed RED against pre-fix `maybeExecuteToolCall` before the fix lands
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
