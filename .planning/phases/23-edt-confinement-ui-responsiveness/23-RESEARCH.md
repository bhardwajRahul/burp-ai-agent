# Phase 23: EDT Confinement & UI Responsiveness - Research

**Researched:** 2026-08-20
**Domain:** Swing EDT confinement in a JVM Burp extension; deterministic headless testing of an async UI
**Confidence:** HIGH (every mechanical claim below was read at source this session; the three genuinely
external claims are tagged and rated separately)

## Summary

This phase is already decided. `23-CONTEXT.md` locks D-01…D-14, `23-UI-SPEC.md` locks the five-state
machine and every string, `23-AI-SPEC.md` §5 locks E1–E10 and S-01…S-12. **Nothing in this document
re-opens any of them.** What research adds is (a) verification of the ten facts the planner was told
to treat as established — eight hold exactly, one is mis-pathed, and **one is materially false** —
(b) three mechanical discoveries that change how the phase must be built, and (c) a recommendation on
each of the six open questions.

**The three discoveries that change the plan:**

1. **`McpToolExecutor` is a Kotlin `object` singleton, statically referenced from `ChatPanel`.** There
   is no injectable executor and therefore **no "executor test double" to put a `CountDownLatch` in** —
   the phrasing both AI-SPEC §5 Eval Tooling and CONTEXT's Claude's-Discretion section use. The seam
   that actually exists is the deep-stub `MontoyaApi` mock, which is what `ChatPanelToolGateTest`
   already asserts against (`verify(h.api.proxy(), times(1)).history()`).
2. **`McpRequestLimiter` contention between two chat call sites is unreachable — the limiter is
   constructed *per context*.** `ChatPanel.buildToolContext` does `limiter = McpRequestLimiter(...)`
   on every call, so a `/tool` racing a chain gets its own semaphore. E10, S-08's limiter clause and
   FLAG-23-05 rest on a false premise and must be rewritten, not deleted.
3. **`McpToolContext` is an immutable snapshot already built on the EDT before dispatch at every call
   site.** Snapshot-before-dispatch — one of E8's three candidate answers — is therefore ~90 % already
   implemented by the shipped architecture. The residual is narrow and nameable, which turns E8 from a
   design decision into a documentation-plus-one-test task.

Two further constraints nobody has recorded: **the detekt baseline is signature-keyed and must not
grow** (STATE.md pins 1096 entries as the v0.10.0 milestone-start baseline that Phase 26 improves
against), and **`ChatPanel.kt` is already declared as a `tasks.test` input** for the structural
assertions — but `McpToolExecutorImpl.kt`, `SettingsPanelSettingsIO.kt` and `BottomTabsPanel.kt` are
**not**, so any new source-text assertion on those files reproduces the 22-09 stale-cache defect.

**Primary recommendation:** build the phase as five plans in four waves, led by a tracer that moves
**only the chain call site** (`ChatPanel.kt:2856`) with its full lifecycle — dispatch helper, S3 busy
state, supersede tracker, cancel semantics, marshalled audit tail — and **deliberately without the
throwing guard**, because D-04's own sequencing constraint makes "guard + one call site" impossible.
The guard lands in plan 2 in one commit with the two remaining call sites.

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

Copied verbatim from `23-CONTEXT.md` §decisions (headline of each; the full reasoning stays in
CONTEXT.md and is not restated here):

- **D-01:** **All three `ChatPanel` `executeTool` call sites move**, not only the chat chain
  (`:1010` UserDialog, `:2319` UserSlashCommand, `:2856` the SEC-06-gated chain).
- **D-02:** **The guarantee lives in two places at once — a shared dispatch helper AND a fail-fast
  check at the executor's door.**
- **D-03:** **The guard throws.** `IllegalStateException`, matching CONVENTIONS.md §Error Handling.
- **D-04:** **Placement recommendation for the guard: `McpToolExecutor.executeToolResult`
  (`McpToolExecutorImpl.kt:137`), not `executeTool` (`:1052`).** … **the throwing guard and D-01's
  three call-site moves must land in the same commit.**
- **D-05:** **Mechanism: a daemon `Thread` per call plus `SwingUtilities.invokeLater`** … The daemon
  flag must be set explicitly — `Thread(task, "…").apply { isDaemon = true }` … Also binding here:
  **CONVENTIONS.md:95 — do not introduce coroutines outside the MCP package.**
- **D-06:** **The panel goes busy for the duration of the tool call — reuse `setSendingState(true)`
  (`ChatPanel.kt:941-946`).**
- **D-07:** **Cancel stops the chain and discards the result; the tool itself is not interrupted.** …
  **Load-bearing corollary: the call did reach Burp, so Phase 22's SC3 audit event must still fire,
  with the cancellation recorded.**
- **D-08:** **Session delete, project change and extension unload use the same supersede path as
  cancel.**
- **D-09:** **The busy state stays global to `ChatPanel`, not per session.**
- **D-10:** **Save disables while the work is in flight; every other field stays editable.**
- **D-11:** **The whole `applyAndSaveSettings` body moves to one background worker; the Swing tail
  marshals back via `invokeLater`.** … `currentSettings()` reads Swing components … so the snapshot is
  already taken on the EDT before dispatch — preserve that ordering.
- **D-12:** **Failure keeps both surfaces, marshalled back to the EDT** — the same `"Save failed: …"`
  banner and the same `JOptionPane` as today.
- **D-13:** **One async path, used by both callers.**
- **D-14:** **`KtorMcpServerManager.stop()` stays blocking; only the caller moves off the EDT.**

### Claude's Discretion

Verbatim from `23-CONTEXT.md` §"Claude's Discretion" — each carries a recommendation to be
**confirmed, not assumed**:

- **Enforcement mechanism and its relationship to the existing `assertEdt()`.** *Recommendation:* a
  new, separate check rather than a change to `ChatPanel.assertEdt()` (`:783`).
- **How SC3 is asserted deterministically.** *Recommendation:* do not measure wall-clock
  responsiveness. Assert the **invariant** instead.
- **Whether the transcript shows anything between approval and `Tool result:`.** *Recommendation:*
  nothing new beyond D-06's busy state. **(Already answered and closed by `23-UI-SPEC.md`
  §"The Open Question, Answered", with Rule S-4 added. Not re-opened here.)**
- ~~**`ExternalMcpClientManager.kt:408` and `:442`**~~ — **RESOLVED 2026-08-20, no longer open.**

### Deferred Ideas (OUT OF SCOPE)

Verbatim headlines from `23-CONTEXT.md` §deferred — **research must not plan any of these**:

- **`MontoyaHttpTransport.execute`'s offload-and-block** (`backends/http/MontoyaHttpTransport.kt:79-92`).
- **Non-`@Volatile` scanner settings fields** — `PassiveAiScanner.kt:77-79`, `ActiveAiScanner.kt:113,119`.
- **`isSending` is dead state** — `ChatPanel.kt:110`, written at `:942`, never read.
- **`ExternalMcpClientManager.kt:408,442` `runBlocking`** — not EDT-reachable. Confirmed deferred.
- **Upgrading `assertEdt()` from a production no-op** — QUAL-07 / Phase 26.
- **`ChatPanel.kt` mega-file split** — explicitly Out of Scope for v0.10.0.
- **A per-call progress indicator in the transcript** — considered under D-06 and left out.
- **Chat-originated HTTP tool calls bypass Burp target scope entirely — NOT this phase's, but do not
  lose it.**
- **Non-transactional settings application becomes observable mid-flight.** *(Research answers the
  "decide in planning" half below — see Open Question 4. The scope of the answer stays inside
  Phase 23; nothing is fixed in `PassiveAiScanner`/`ActiveAiScanner`.)*
- **`McpRequestLimiter` contention becomes reachable** — *(Research **refutes** the premise. See
  Finding F-3 below.)*
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| REL-05 | No MCP tool execution, backend HTTP call, or `runBlocking` on an external MCP server happens on the Swing EDT; the auto tool-chain (up to 8 iterations) leaves the UI responsive throughout. Saving Settings does not block the EDT on `serverManager.stop()`'s 10-second bounded wait. [VERIFIED: .planning/REQUIREMENTS.md:28 — quoted verbatim] | §Architecture Patterns P-1 (the off-EDT dispatch helper) and P-2 (the throwing door guard) deliver clause 1; §P-4 (the settings async seam) delivers clause 3; §Validation Architecture maps every clause to a named automated command. Finding F-4 records that clause 1's `runBlocking` limb is satisfied *vacuously* in production today and why the guard still covers it structurally. |

**Ordering constraint that produced this phase's shape** [VERIFIED: .planning/REQUIREMENTS.md:10]:
*"SEC-06 (agent trust boundary) and REL-05 (EDT) both rewrite `ChatPanel.maybeExecuteToolCall` and
must be sequential, not parallel."*

**Success criteria** [VERIFIED: .planning/ROADMAP.md §"Phase 23", read this session] — SC1 executeTool
never on the EDT from the chat chain, asserted by a test not inspection, not relying on `-ea`; SC2
`sendRequest` and the external `runBlocking` both off the EDT; SC3 an 8-iteration chain with slow
tools leaves the UI repainting, results in order, landing on the EDT; SC4 Settings save does not block
the EDT on the 10 s wait, nor on `settingsRepo.save()` / `backends.reload()`; SC5 no REL-01
regression; SC6 reuse `MainTab`'s `Thread` + `invokeLater` idiom rather than a new concurrency idiom.
</phase_requirements>

## Project Constraints (from CLAUDE.md)

| Directive | Source | How this phase must comply |
|-----------|--------|----------------------------|
| Kotlin (JVM 21), Gradle Kotlin DSL, Montoya API — fixed by ADR-1/2/3 | CLAUDE.md §Constraints | No new language, no new build tool. `java.util.concurrent` only. |
| **English only in code and comments** (AGENTS.md, non-negotiable) | CLAUDE.md §Constraints | Every new comment, thread name and user string in English. |
| Audit defaults unchanged — disabled by default, opt-in verbose, hashes only | CLAUDE.md §Constraints | D-07's "a cancelled call must still be audited" adds an *event on an existing path*; it must not change `auditEnabled`'s default or promote anything to verbose. |
| Privacy STRICT/BALANCED/OFF stay user-visible and pre-flight | CLAUDE.md §Constraints | E8's answer must not weaken redaction; see Open Question 4. |
| MIT — dependencies must stay compatible | CLAUDE.md §Constraints | This phase adds **zero** dependencies. |
| **GSD workflow enforcement** — no direct repo edits outside a GSD workflow | CLAUDE.md §GSD Workflow Enforcement | All edits land through `/gsd-execute-phase`. |
| Build requires JDK 21 | MEMORY.md (user auto-memory) | Every gradle command in the plan must be prefixed `JAVA_HOME=$(/usr/libexec/java_home -v 21)`. Default JDK 25 breaks Gradle 8.12.1. |
| Distribution: `./gradlew shadowJar` → `Custom-AI-Agent-<version>.jar` | CLAUDE.md §Constraints | The PR gate builds the shadow JAR; a new file must not break it. |

**Additional binding conventions read at source this session:**

- [VERIFIED: .planning/codebase/CONVENTIONS.md:92-95] *"Everywhere else concurrency is handled with
  `java.util.concurrent` primitives (`Executors`, `AtomicBoolean`, `ConcurrentHashMap`,
  `LinkedBlockingQueue`) — do NOT introduce coroutines outside the MCP package without discussion"*
- [VERIFIED: .planning/codebase/CONVENTIONS.md:188] *"**Strategy:** fail loudly to the caller via
  exceptions; never swallow silently without at least a log."* and `:190` *"`IllegalStateException`
  for logical precondition failures"* — D-03's exact warrant.
- [VERIFIED: .planning/codebase/CONVENTIONS.md:204] *"All Swing mutations go through
  `SwingUtilities.invokeLater { ... }` when called from a non-EDT thread"*
- [VERIFIED: .planning/codebase/CONVENTIONS.md:210] *"**Rule:** all new UI panels MUST use
  `UiTheme.Colors.*` and `UiTheme.Typography.*` constants … Do not hardcode `Color(...)` or `Font(...)`
  inline."* — UI-SPEC Rule N-1 refines this per-file; follow the UI-SPEC.
- [VERIFIED: .planning/codebase/CONVENTIONS.md:216-221 §Comments] KDoc on public API only; inline
  comments for *"concurrency contracts (`// Thread-safe: …`)"*; **"No temporal language: comments
  describe current behavior, not history or intentions without action."**

---

## Architectural Responsibility Map

Swing/JVM tiers, not web tiers. Every capability in this phase and the tier that owns it.

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Decide *whether* a tool runs (SEC-06 gate) | **EDT (`ChatPanel` / `ToolApprovalGate`)** | — | Phase 22's; reads `pendingDecisions` and drives the card. Unchanged by this phase. |
| Capture everything the call needs (backendId, chainStep, canonicalId, panel, context, origin/tier/decision, traceId, iterations) | **EDT (`ChatPanel`)** | — | These reads touch `@GuardedBy("EDT")` maps. Must happen before dispatch — E5's whole content. |
| Enter the busy state (S3) and register the supersede token | **EDT (`ChatPanel`)** | — | Swing field writes; UI-SPEC Rule S-2. |
| Create and start the worker | **EDT (dispatch helper)** | — | `Thread(...).start()` is cheap and non-blocking; only its *body* is off-EDT. |
| Execute the tool (limiter, redaction, `api.http().sendRequest`, `runBlocking`) | **Worker thread** | — | REL-05's whole point. Must reach `executeToolResult` off-EDT. |
| Refuse execution when on the EDT | **Executor door (`McpToolExecutorImpl.executeToolResult`)** | — | D-04's convergence point: chat path, MCP-server path, `ext:` branch and both `sendRequest` sites all pass through it. |
| Marshal the result back; supersede check; audit pair; transcript; followup turn | **EDT (worker's `invokeLater` tail)** | — | Touches the guarded maps and Swing. One boundary (AI-SPEC §4 Core Pattern step 7). |
| Snapshot the settings form | **EDT (`currentSettings()`)** | — | Reads Swing components. D-11 preserves this ordering. |
| Persist + reload + restart MCP + apply to scanners | **Worker thread** | — | Contains `KtorMcpServerManager.stop()`'s bounded 10 s wait (D-14). |
| Report save success/failure (banner, modal, warnings refresh) | **EDT (settings worker's `invokeLater` tail)** | — | D-12/D-13. |
| Raise / lower the Save-busy affordance | **EDT (`BottomTabsPanel` via a listener seam)** | **`SettingsPanel` raises the signal** | The buttons are `private val` on `BottomTabsPanel` — see Finding F-7. |
| Bound MCP shutdown | **`KtorMcpServerManager` (unchanged)** | — | D-14: REL-02's bounded-shutdown guarantee is not re-opened. |

**The tier error this map exists to prevent:** putting the audit pair (`toolDecisionReporter.report`
→ `supervisor.aiRequestLogger?.log`) on the **worker** because it is "not Swing". It reads
`@GuardedBy("EDT")`-adjacent state and its ordering is load-bearing; AI-SPEC §4 names splitting the
pair across the boundary as a FAIL condition for E4.

---

## Fact Verification — the ten "established facts"

Every one was re-read at source this session. **Eight hold exactly. One has a wrong path. One is
materially false.**

| # | Claim as given | Verdict | Evidence |
|---|----------------|---------|----------|
| 1 | Three `executeTool` sites at `ChatPanel.kt:1010`, `:2319`, `:2856`; guard site `McpToolExecutorImpl.kt:137`; `executeTool` at `:1052`; MCP-server path `McpToolHandlers.kt:129` | ✅ **exact** | [VERIFIED: ChatPanel.kt:1010,2319,2856 — `McpToolExecutor.executeTool(...)` at each; McpToolExecutorImpl.kt:137 `fun executeToolResult(`; :1052 `internal fun executeTool(`; :1058 `val result = executeToolResult(name, argsJson, context)`; McpToolHandlers.kt:129 `val result = McpToolExecutor.executeToolResult(descriptor.id, argsJson, context)`] |
| 2 | `slashCommandPathIsNotDoublePrompted` (`:350`) reaches `executeToolResult` on the EDT via `ChatPanelTestHarness.sendUserMessage`'s `invokeAndWait` (`:193`); guard + moves must land in one commit | ✅ **exact** | [VERIFIED: ChatPanelToolGateTest.kt:349-362; ChatPanelTestHarness.kt:189-205 — `fun sendUserMessage(...) { SwingUtilities.invokeAndWait { … send.doClick() } }`] |
| 3 | `MainTab.kt:193` is a bare `Thread { … }`, no `isDaemon`; `MontoyaHttpTransport.kt:85` is the correct form | ✅ **exact** | [VERIFIED: MainTab.kt:193 `Thread {` … `:218 }.start()` — no `isDaemon`; MontoyaHttpTransport.kt:85 `Thread(task, "montoya-http-offedt").apply { isDaemon = true }.start()`] |
| 4 | 11 `invokeLater` at 647, 658, 673, 715, 723, 741, 1909, 1926, 2190, 2268, 2283; 6 `assertEdt()`; 3024 lines | ✅ **exact** | [VERIFIED: `grep -n 'SwingUtilities.invokeLater' ChatPanel.kt` → `647 658 673 715 723 741 1909 1926 2190 2268 2283`; `grep -c 'assertEdt()'` → `6`; `wc -l` → `3024`] |
| 5 | `.github/workflows/build.yml:47` runs `./gradlew test -PexcludeHeavyTests=true`; `build.gradle.kts:206-213` excludes the five suffixes | ✅ **exact** | [VERIFIED: build.yml:47 `run: ./gradlew test -PexcludeHeavyTests=true --no-daemon`; build.gradle.kts:206-213 `excludeTestsMatching("*IntegrationTest") / ("*ConcurrencyTest") / ("*BackpressureTest") / ("*RestartPolicyTest") / ("*SupervisionTest")`] |
| 6 | `drainEdt()` (`:211`) is `repeat(times) { SwingUtilities.invokeAndWait { } }`; `ChatPanelToolGateTest.kt:358` becomes racy | ✅ **exact** | [VERIFIED: ChatPanelTestHarness.kt:211-213 `fun drainEdt(times: Int = DEFAULT_EDT_DRAINS) { repeat(times) { SwingUtilities.invokeAndWait { } } }`; ChatPanelToolGateTest.kt:358 `verify(h.api.proxy(), times(1)).history()` immediately after `drainEdt()`] |
| 7 | `saveButton.isOpaque = true` with explicit `background = UiTheme.Colors.primary` (`BottomTabsPanel.kt:61-64`); both buttons `private val` (`:19-20`) | ✅ **exact** | [VERIFIED: BottomTabsPanel.kt:19-20 `private val saveButton = JButton("Save settings")` / `private val restoreButton = JButton("Restore defaults")`; :61 `saveButton.background = UiTheme.Colors.primary`; :62 `saveButton.foreground = UiTheme.Colors.onPrimary`; :63 `saveButton.isOpaque = true`; :64 `saveButton.border = EmptyBorder(8, 14, 8, 14)`] |
| 8 | `McpRequestLimiter` (`:9-14`) is a fair semaphore with 250 ms `tryAcquire` default | ✅ **exact** *(but see F-3 — the conclusion drawn from it is false)* | [VERIFIED: McpRequestLimiter.kt:9 `private val semaphore = Semaphore(maxConcurrent, true)`; :11-12 `fun tryAcquire(timeoutMs: Long = 250): Boolean`] |
| 9 | `KtorMcpServerManager.stop()` stays blocking; `McpSupervisor.stop()` (`:132-141`) depends on completion | ✅ **exact** | [VERIFIED: McpSupervisor.kt:132-141 `fun stop() { serverManager.stop { … }; restartAttempts.set(0); takeoverAttempts.set(0); stdioBridge.stop(); ScannerTaskRegistry.clear(); CollaboratorRegistry.clear() }`; KtorMcpServerManager.kt:268 `future.get(10, TimeUnit.SECONDS)` with the RESTART-SAFE comment at `:252-255`] |
| 10 | `ExternalMcpClientManager.kt:408,442` `runBlocking`, both inside `fun stop()` (`:395`); nothing in `src/main` constructs it; out of scope | ⚠️ **holds, wrong path** | [VERIFIED: the file is `mcp/external/ExternalMcpClientManager.kt`, **not** `mcp/`. `grep -n runBlocking` → `408`, `442`; `fun stop()` at `:395`; the only constructor calls are in `src/test/.../external/ExternalMcpClientManagerTest.kt:52,68,121,182`. **Correct the citation path in the plan.**] |

### The one materially false premise, and two more corrections

**F-3 (FALSE) — `McpRequestLimiter` contention between two chat call sites is unreachable, before and
after this phase.** The limiter is **constructed per `McpToolContext`**, not shared:

> ```kotlin
> limiter = McpRequestLimiter(settings.mcpSettings.maxConcurrentRequests),
> ```
> [VERIFIED: src/main/kotlin/com/six2dez/burp/aiagent/ui/ChatPanel.kt:3019, inside `private fun buildToolContext(settings: AgentSettings, sessionId: String): McpToolContext` at `:3005-3023`]

`buildToolContext` is called at six sites — `:583` (chain, once per turn), `:979` (`openToolDialog`),
`:1027`, `:1121`, `:2300` (`/tools`), `:2316` (`/tool`) [VERIFIED: `grep -n buildToolContext ChatPanel.kt`].
A `/tool` fired during a chain therefore builds a **fresh `McpRequestLimiter` with a fresh
`Semaphore(4, true)`** and cannot contend with the chain's. The only shared limiter in the codebase is
the MCP-server one, minted once per runtime context [VERIFIED: McpRuntimeContextFactory.kt:28
`val limiter = McpRequestLimiter(settings.maxConcurrentRequests)`], and this phase does not touch that
path. Default `maxConcurrentRequests = 4` [VERIFIED: AgentSettings.kt:1170 `maxConcurrentRequests = 4,`
and :1305 `(prefs.getInteger(KEY_MCP_MAX_CONCURRENT) ?: 4).coerceIn(1, 64)`].

**Consequence for the plan:** E10's rubric, S-08's limiter clause, UI-SPEC FLAG-23-05 and CONTEXT's
Deferred item *"`McpRequestLimiter` contention becomes reachable"* are all built on a premise that
does not hold. **Do not delete them — rewrite them.** S-08 keeps its real content (both calls dispatch
off-EDT, both execute, the chain is unharmed, no double-execution, the transcript shows two results).
E10 becomes a **negative** dimension worth keeping precisely because it is counter-intuitive: *"a
`/tool` racing a chain does **not** produce `Too many concurrent MCP requests.`, because each chat
call site mints its own limiter; if that message ever appears from the chat path it means a shared
limiter was introduced."* That is assertable and it is a real guard against a future refactor that
hoists the limiter to a field "for efficiency" and silently couples two user actions.

**F-1 (structural) — there is no executor test double, because `McpToolExecutor` is a singleton
`object`.**

> ```kotlin
> object McpToolExecutor {
> ```
> [VERIFIED: src/main/kotlin/com/six2dez/burp/aiagent/mcp/tools/McpToolExecutorImpl.kt:45]

All three call sites reference it statically (`McpToolExecutor.executeTool(...)`). Every phrase in
CONTEXT and AI-SPEC of the form *"a `CountDownLatch` in the executor test double"* has no referent.
The seam that exists is the deep-stub `MontoyaApi` — `mock(defaultAnswer = Answers.RETURNS_DEEP_STUBS)`
[VERIFIED: ChatPanelTestHarness.kt:85] — through which the real executor reaches Burp, and which
`ChatPanelToolGateTest.kt:358` already verifies against. See Open Question 1.

**F-2 (structural) — `McpToolContext` is an immutable snapshot already taken on the EDT.** It is a
`data class` carrying `privacyMode`, `determinismMode`, `hostSalt`, `toolToggles`, `unsafeEnabled`,
`unsafeTools`, `enabledUnsafeTools`, `limiter`, `edition`, `maxBodyBytes` [VERIFIED:
McpToolContext.kt:19-48], built from `getSettings()` on the EDT at each call site (F-3's citation).
This materially simplifies E8 — see Open Question 4.

**F-4 — SC2's `runBlocking` limb is satisfied vacuously in production, and the guard covers it anyway.**
`context.externalClientManager` is `null` in every production path (nothing in `src/main` constructs
the manager), so `routeExternalToolCall`'s `runBlocking { manager.callTool(...) }` at
`McpToolExecutorImpl.kt:1126` returns `"External MCP client not available"` before reaching the
coroutine. **But** `executeToolResult` early-returns into `routeExternalToolCall` at `:147`
[VERIFIED: McpToolExecutorImpl.kt:145-148 — `if (resolvedName.startsWith("ext:")) { return routeExternalToolCall(...) }`],
so a guard placed as the **first statement** of `executeToolResult` covers the `ext:` branch too.
**The guard must precede the `ext:` early return, not follow it.** State this in the plan; it is a
one-line placement decision with a whole SC clause riding on it.

**F-5 — `deleteSession` does NOT call `cancelInFlightRequest()`.** Three of D-08's four exits get the
supersede for free if the tracker is taken inside `cancelInFlightRequest()`: cancel button (`:498`),
keyboard action (`:468`), `clearInMemorySessionState()` (`:1480`) and `shutdown()` (`:1455`)
[VERIFIED: `grep -n cancelInFlightRequest ChatPanel.kt` → `468, 498, 956, 1019, 1446(comment), 1455, 1480`].
**Session delete is the exception** — it calls `resolvePending(session.id, ImplicitDenyReason.SESSION_DELETED)`
and never `cancelInFlightRequest()` [VERIFIED: ChatPanel.kt:851-890, the `deleteSession` body read in
full]. S-05 therefore needs an **explicit** supersede call added to `deleteSession`; it will not fall
out of the `cancelInFlightRequest` change. This is the single most likely thing to be missed.

**F-6 — `ToolCallOutcome` needs a fourth value, and adding it is safe by construction.**

> ```kotlin
> private enum class ToolCallOutcome {
>     /** No tool call, or it failed outright. The caller invokes `onCompleted` itself. */
>     NOT_CHAINED,
>
>     /** A followup turn was sent and carries `onCompleted` with it. */
>     CHAINED,
>
>     /** A card is on screen; `onCompleted` is parked in [PendingToolDecision] until the user clicks. */
>     AWAITING_DECISION,
> }
> ```
> [VERIFIED: src/main/kotlin/com/six2dez/burp/aiagent/ui/ChatPanel.kt:1738-1747]

The enum encodes **who discharges `onCompleted`**, and both consumers test only for `NOT_CHAINED`
[VERIFIED: ChatPanel.kt:759 `if (outcome == ToolCallOutcome.NOT_CHAINED) {`; ChatPanel.kt:2621, the
same test inside `resolveToolDecision`]. A new `EXECUTING` value — *"a worker holds `onCompleted`; it
is discharged from the worker's `invokeLater` tail"* — therefore falls through both sites correctly
with **zero** changes there. Record it as a deliberate choice with a KDoc line, not as an accident:
silent fall-through is also exactly how a continuation gets dropped, and E6's FAIL clause names
*"a parked continuation left dangling"*.

**F-7 — the detekt baseline is signature-keyed, is pinned at 1096 entries as a milestone metric, and
three functions this phase touches are in it.**

> *"Measured baselines at milestone start (for Phase 26 to improve against): coverage 34% line / 23%
> branch project-wide; detekt baseline 1096 entries"*
> [VERIFIED: .planning/STATE.md:56-58]

Entries that this phase can invalidate by changing a signature [VERIFIED: `grep -n` in
detekt-baseline.xml]:

| Line | Entry | Risk |
|---|---|---|
| 884 | `ReturnCount:ChatPanel.kt$ChatPanel$fun cancelInFlightRequest(): Boolean` | D-07 extends this function. **Keep the signature `(): Boolean`** or the suppression stops matching and detekt goes red. |
| 886 | `ReturnCount:ChatPanel.kt$ChatPanel$fun openToolDialog()` | D-01 moves its call site. Keep `openToolDialog()` parameterless. |
| 889 | `ReturnCount:…$private fun handleToolCommand( text: String, sessionId: String, panel: SessionPanel, state: ToolSessionState, settings: AgentSettings, ): Boolean` | D-01 + Rule S-4 touch it. Keep the signature. |
| 141 | `LongParameterList:ChatPanel.kt$ChatPanel$( private val api …, private val passiveScanner: PassiveAiScanner? = null, )` | **Do not add an 11th constructor parameter to `ChatPanel`** — the baseline entry is the whole verbatim parameter list. This rules out the "injected `Executor` seam" option in Open Question 1. |
| 87 / 110-111 | `LargeClass:ChatPanel.kt$ChatPanel`, `LongMethod:…sendMessage(…)` | Already baselined — growing the class and `sendMessage` is free. |

**Not baselined, and therefore a live risk:** `executeApprovedToolCall` (`ChatPanel.kt:2841-2923`,
~83 source lines) and `applyAndSaveSettings` (`SettingsPanelSettingsIO.kt:455-505`, ~51 lines) carry
**no** `LongMethod` entry, and `detekt.yml` sets `complexity.LongMethod.threshold: 80` [VERIFIED:
detekt.yml:5-7]. Wrapping either body in a worker + `invokeLater` tail will very likely cross 80.
**The plan must extract the marshalled tail into its own private function** rather than inline it —
which is also what AI-SPEC §4's "one boundary a reader can see" asks for. `LongParameterList` is
`functionThreshold: 10` / `constructorThreshold: 10` [VERIFIED: detekt.yml:8-10], so a helper taking
the ~10 captured values is at the edge: prefer a small private `data class` capture record.

**F-8 — `ChatPanel.kt` is already a declared `tasks.test` input; the phase's other three files are
not.** `build.gradle.kts:197-200` declares
`inputs.file("src/main/kotlin/com/six2dez/burp/aiagent/ui/ChatPanel.kt").withPropertyName("chatPanelStructuralSource")`
with a comment recording the 22-09 defect: *"an edit that changes the source text but not the compiled
bytecode … produces an identical cache key, so the test task is served from cache and the structural
guard never runs in exactly the case it exists to catch"* [VERIFIED: build.gradle.kts:190-200]. Four
such declarations exist (`DECISIONS.md`, `McpToolCatalog.kt`, `ToolApprovalGate.kt`, `ChatPanel.kt`).
**If this phase adds a structural assertion reading `McpToolExecutorImpl.kt`, `SettingsPanelSettingsIO.kt`
or `BottomTabsPanel.kt` from disk, it MUST add the matching `inputs.file` declaration in the same
commit** — otherwise the guard silently stops running.

**F-9 — a global test-observer hook already exists in production code, with an established
install/clear discipline.**

> ```kotlin
> @Volatile
> private var globalEmitter: ((String, Any) -> Unit)? = null
>
> fun registerGlobalEmitter(emitter: ((String, Any) -> Unit)?) {
> ```
> [VERIFIED: src/main/kotlin/com/six2dez/burp/aiagent/audit/AuditLogger.kt:18-22, inside `companion object`]

The suite installs it in `@BeforeEach` and clears it in `@AfterEach` with a comment explaining why
[VERIFIED: ChatPanelToolGateTest.kt:640-653]. This is the precedent for Open Question 1's completion
hook, and it is a **precedent in this exact test class**.

**F-10 — `AuditLogger.enabled` really is `@Volatile`** [VERIFIED: AuditLogger.kt:33-34 —
`@Volatile` / `private var enabled: Boolean = true`], confirming CONTEXT's correction of the earlier
false claim.

---

## Standard Stack

**No new library. No new dependency. No install step.** This is the correct answer, not an omission:
the phase adds threads and a listener, in a stack that already contains every primitive it needs.

### Core (all already present)

| Library / API | Version | Purpose | Why standard here |
|---|---|---|---|
| `java.lang.Thread` + `javax.swing.SwingUtilities` | JDK 21 | The D-05 dispatch mechanism | SC6 names this exact idiom; `MontoyaHttpTransport.kt:85` is the in-repo daemon+named form [VERIFIED] |
| `java.util.concurrent.atomic.AtomicReference` | JDK 21 | The supersede token cell | `InFlightConnectionTracker` already uses exactly this [VERIFIED: ChatPanel.kt:69-83] |
| `java.util.concurrent.CountDownLatch` | JDK 21 | Test-side worker await and the SC3 handshake | Already imported in `McpToolExecutorImpl.kt:41` |
| JUnit Jupiter 6.0.3 + `assertTimeoutPreemptively` | current | Deadlock failsafe for the SC3 handshake | Already used at `ChatPanelToolGateTest.kt:377` (`Duration.ofSeconds(30)`) [VERIFIED] |
| mockito-kotlin 5.4.0 deep stubs | current | The only tool-body seam that exists (F-1) | `ChatPanelTestHarness.kt:85` [VERIFIED] |

### Alternatives Considered

| Instead of | Could Use | Tradeoff — why rejected |
|---|---|---|
| Daemon `Thread` per call (D-05) | `Executors.newSingleThreadExecutor` owned by `ChatPanel` | D-05 rejected it already. Research adds one measurement: it would also require an unload lifecycle, and `ChatPanel` owns **no** executor today [VERIFIED: no `Executors.` reference in ChatPanel.kt]. Locked — do not revisit. |
| `java.util.concurrent` | Kotlin coroutines | Forbidden by CONVENTIONS.md:95 outside `mcp/`. Locked. |
| `SwingWorker` | — | Would be a *new* concurrency idiom, which SC6 explicitly warns against, and its `done()` marshalling adds nothing over `invokeLater`. **Do not use `SwingWorker`.** |
| An injected dispatcher constructor parameter on `ChatPanel` | — | Rejected on a measured cost: it invalidates `detekt-baseline.xml:141`'s verbatim `LongParameterList` entry (F-7). |

**Installation:** none. **Version verification:** not applicable — zero packages added.

## Package Legitimacy Audit

**Not applicable — this phase installs no external packages.** No entry is added to
`build.gradle.kts` dependencies, `gradle/libs.versions.toml` or any lockfile. The legitimacy gate has
nothing to check.

**Packages removed due to [SLOP] verdict:** none.
**Packages flagged as suspicious [SUS]:** none.

---

## Architecture Patterns

### System Architecture Diagram

Data flow for one approved chain step, after this phase. `═►` crosses a thread boundary.

```
  user types / clicks
          │
          ▼
 ┌─────────────────────── EDT ───────────────────────┐
 │ sendFromInput ─► sendMessage ─► supervisor.sendChat│──► backend thread (streams tokens back)
 │                                                    │        │
 │        invokeLater(:741) ◄─────────────────────────┼────────┘  onComplete
 │              │                                     │
 │              ▼                                     │
 │        maybeExecuteToolCall                        │
 │              │  parse ─► ToolApprovalGate.evaluate │
 │      ┌───────┼──────────┬─────────────┐            │
 │  Denied   Ask(card)   Run             │            │
 │      │       │          │             │            │
 │      │  pendingDecisions│  (user click resolves)   │
 │      │       └──────────┘             │            │
 │      ▼                                ▼            │
 │  denyToolCall                executeApprovedToolCall│
 │  (CHAINED)                            │            │
 │                        ┌──────────────┴─────────┐  │
 │                        │ 1. capture EVERYTHING   │  │  ← E5: last EDT read of guarded maps
 │                        │ 2. setSendingState(true)│  │  ← S3
 │                        │ 3. tracker.set(token)   │  │  ← D-08 supersede cell
 │                        │ 4. dispatch helper      │  │
 │                        └──────────┬──────────────┘  │
 └───────────────────────────────────┼─────────────────┘
                                     ═► daemon "burp-ai-tool-exec", isDaemon=true
                                     │
                       ┌─────────────▼──────────────────────────────┐
                       │ runCatching {                              │
                       │   McpToolExecutor.executeTool(...)         │
                       │     └► executeToolResult   ◄── D-03 GUARD  │ throws IllegalStateException
                       │          (guard is the FIRST statement,    │ if isEventDispatchThread()
                       │           BEFORE the ext: early return)    │
                       │        ├► ext: ─► routeExternalToolCall    │
                       │        └► runTool ─► limiter ─► redaction  │
                       │                   ─► api.http().sendRequest│
                       │ }                                          │
                       │ Throwable-safe: logToError on escape       │  ← E9
                       └─────────────┬──────────────────────────────┘
                                     ═► SwingUtilities.invokeLater  ← the SINGLE marshalling point
 ┌───────────────────────────────────▼─────────────────┐
 │ EDT tail (extracted private fun — see F-7):         │
 │  a. superseded = !tracker.clearIfMatches(token)     │
 │  b. audit pair ALWAYS: report(...) ─► metadata      │  ← E4, ordering load-bearing
 │                        aiRequestLogger?.log(meta)   │
 │  c. if superseded: discharge onCompleted, STOP      │  ← S4 state, no row, no turn, no refund
 │  d. else: addMessage("Tool result: …")              │
 │          setSendingState(false)                     │
 │          sendMessage(followup, nextIterationBudget) │
 │  e. finally: notify test observer ("settled")       │  ← Open Question 1
 └─────────────────────────────────────────────────────┘
```

Settings, after this phase:

```
 [EDT] Save/Restore click ─► currentSettings() (reads Swing)  ← stays on EDT (D-11)
        │  (restore only) applySettingsToUi(defaults), confirm modal   ← stays BEFORE dispatch (T-3)
        │  busyListener(true) ─► BottomTabsPanel disables + recolors   ← T-1/T-2
        │  updateSaveFeedback("Saving settings..." | "Restoring defaults...")
        ═► daemon "burp-ai-settings-save", isDaemon=true
             try { applyAndSaveSettingsBody(updated) }        ← settingsRepo.save, backends.reload,
             catch (t: Throwable) { … }                          mcpSupervisor.applySettings →
             finally { invokeLater { try { onDone(result) }      KtorMcpServerManager.stop()'s
                                     finally { busyListener(false) } } }   future.get(10s)
 [EDT] tail: banner (success|failure) + modal on failure + onSettingsChanged
        + refreshPassiveAiStatus / refreshActiveAiStatus / updateProfileWarnings / updateRiskWarnings
```

### Recommended file layout (additive only — no restructuring)

```
src/main/kotlin/com/six2dez/burp/aiagent/
├── ui/
│   ├── OffEdtDispatch.kt        # NEW — the D-05 helper + E9 wrapping + F-9-style test observer
│   ├── ChatPanel.kt             # 3 call sites, S3, tracker, cancel, tail (NO restructuring)
│   ├── SettingsPanelSettingsIO.kt  # applyAndSaveSettings → body + async wrapper
│   ├── SettingsPanelActions.kt  # setBusyListener seam; both callers take a completion callback
│   ├── SettingsPanel.kt         # one new field: busyListener (next to dialogParent, :49)
│   └── BottomTabsPanel.kt       # installs the listener next to setDialogParent(root) (:93)
└── mcp/tools/McpToolExecutorImpl.kt  # the throwing guard, FIRST statement of executeToolResult

src/test/kotlin/com/six2dez/burp/aiagent/
├── ui/ChatPanelTestHarness.kt        # + awaitToolSettled(...) worker-aware await
├── ui/ChatPanelEdtConfinementTest.kt # NEW — S-01..S-09, S-12  (NOT *ConcurrencyTest)
├── ui/SettingsSaveAsyncTest.kt       # NEW — S-11, E7, FLAG-23-06
└── mcp/tools/McpToolExecutorEdtGuardTest.kt  # NEW — S-10
```

### Pattern P-1 — the off-EDT dispatch helper (D-05, SC6, E9)

**What:** one named function that creates a **named, daemon** thread, wraps the body so no `Throwable`
escapes silently, and notifies a test-visible observer when the marshalled tail has finished.

**When to use:** every one of the three `executeTool` call sites, and the settings save. One helper,
four callers — that is D-02's "the helper makes it work".

**Why a helper and not four inline `Thread {}`:** three inline copies is exactly the "three threading
idioms in one file" shape D-02 rejects, and each copy is a place to forget `isDaemon`.

```kotlin
// Source: shape derived from MontoyaHttpTransport.kt:85 (VERIFIED daemon+named form) and
// MainTab.kt:190-215 (the Thread + invokeLater idiom SC6 names). Observer hook shape from
// AuditLogger.registerGlobalEmitter (AuditLogger.kt:18-22).
internal object OffEdtDispatch {
    /**
     * Test-visible completion observer. Null in production.
     *
     * Same shape and same discipline as `AuditLogger.registerGlobalEmitter`: a `@Volatile` nullable
     * hook, installed in `@BeforeEach` and cleared in `@AfterEach`. Fired from the EDT tail's
     * `finally`, so "settled" means the marshalled work has already run.
     */
    @Volatile
    private var settledObserver: ((String) -> Unit)? = null

    fun registerSettledObserver(observer: ((String) -> Unit)?) {
        settledObserver = observer
    }

    /**
     * Runs [work] on a fresh named daemon thread, then marshals [onEdt] back with the result.
     *
     * Thread-safe: [work] must not touch Swing or any `@GuardedBy("EDT")` map. Burp does not catch
     * or report exceptions thrown on background threads, so an escape is logged to the extension
     * error stream rather than lost.
     */
    fun <T> run(
        threadName: String,
        label: String,
        logError: (String) -> Unit,
        work: () -> T,
        onEdt: (Result<T>) -> Unit,
    ) {
        val body = Runnable {
            val outcome = runCatching(work)
            outcome.exceptionOrNull()?.let { logError("[$label] worker failed: $it") }
            SwingUtilities.invokeLater {
                try {
                    onEdt(outcome)
                } catch (t: Throwable) {
                    logError("[$label] completion failed: $t")
                } finally {
                    settledObserver?.invoke(label)
                }
            }
        }
        Thread(body, threadName).apply { isDaemon = true }.start()
    }
}
```

Three properties the plan should assert rather than assume:
1. `isDaemon` is set **explicitly** (E9; `MainTab.kt:193` does not, so copying `MainTab` verbatim is
   the wrong move — copy `MontoyaHttpTransport.kt:85`).
2. The thread is **named**, so a stuck worker is identifiable in a thread dump.
3. The `finally` on the observer covers the case where `onEdt` itself throws — the same `finally`
   discipline FLAG-23-06 demands for the settings seam.

**Review rule inherited verbatim from AI-SPEC §4b, and it is the single best code-review question for
this phase:** *after the dispatch line, no statement may block on the worker's result. No `Future.get`,
no `Thread.join`, no `CountDownLatch.await`, no `invokeAndWait` from the worker back to the EDT.*
`MontoyaHttpTransport.execute` is the in-repo counter-example — it offloads and then blocks the EDT on
`task.get(timeoutMs + 5_000)` [VERIFIED: MontoyaHttpTransport.kt:84-90]. **Do not copy that shape.**

### Pattern P-2 — the throwing door guard (D-03, D-04, SC1, E2)

**What:** the **first statement** of `McpToolExecutor.executeToolResult`.

```kotlin
// Source: McpToolExecutorImpl.kt:137-148 read this session; CONVENTIONS.md:190 for the exception type.
fun executeToolResult(
    name: String,
    argsJson: String?,
    context: McpToolContext,
): CallToolResult {
    // REL-05 / D-03: an `assert()` is a no-op in shipped Burp, so this throws. The convergence point
    // for the chat path, the MCP-server path (McpToolHandlers.kt:129) and the ext: branch below.
    // Placed BEFORE the ext: early return so routeExternalToolCall's runBlocking is covered too.
    check(!SwingUtilities.isEventDispatchThread()) {
        "MCP tool execution must not run on the Swing EDT (REL-05). Dispatch via OffEdtDispatch."
    }
    val resolvedName = canonicalToolId(name)
    if (resolvedName.startsWith("ext:")) { ... }
```

`check(...)` throws `IllegalStateException`, which is exactly what D-03 specifies and what
CONVENTIONS.md:190 names for *"logical precondition failures"*. Two placement facts:

- It must precede `:145-148`'s `ext:` early return (F-4).
- **It imports `javax.swing.SwingUtilities` into a file under `mcp/`.** That is a new
  `ui`-flavoured import in the MCP package. It is the right trade (the door is where the guarantee
  belongs, per D-04) but the plan should state it so a reviewer does not read it as a layering
  accident. `SwingUtilities.isEventDispatchThread()` is a pure JDK query with no AWT initialisation
  side effect, so it is headless-safe.

**Anti-patterns to avoid**

- **`assert(...)` for the new guard.** SC1 names this explicitly; `assertEdt()` is `assert()`-based
  and is a no-op in shipped Burp [VERIFIED: ChatPanel.kt:783-787 — *"Uses JVM assert (active under
  -ea in CI tests; no-op in production)"*].
- **Touching `assertEdt()` or any of its 6 call sites.** SC5's cheapest evidence is that they are
  byte-identical; Phase 26 owns that method.
- **`Thread.interrupt()` as a cancel.** `Http.sendRequest` has **no** cancellation parameter — all
  four overloads take only `(HttpRequest[, HttpMode][, String][, RequestOptions])`
  [CITED: portswigger.github.io/burp-extensions-montoya-api/javadoc/burp/api/montoya/http/Http.html].
  D-07's stated-limits contract is the honest one.
- **Copying `MontoyaHttpTransport.execute`'s offload-and-block.**
- **`SwingWorker`** — a new idiom, against SC6.

### Pattern P-3 — the supersede tracker (D-08, S4, E4/E6)

Reuse `InFlightConnectionTracker`'s shape verbatim — `set` / `clearIfMatches` (CAS) / `take` /
`current` [VERIFIED: ChatPanel.kt:69-83], already covered by `ChatPanelConcurrencyTest.kt:20-60`.

```kotlin
// A second cell of the SAME shape, holding an opaque token for the running tool call.
private class RunningToolTracker {
    private val ref = AtomicReference<Any?>()
    fun set(token: Any) { ref.set(token) }
    fun clearIfMatches(expected: Any): Boolean = ref.compareAndSet(expected, null)
    fun take(): Any? = ref.getAndSet(null)
    fun current(): Any? = ref.get()
}
```

Wire-up, from F-5's measurement:
- `cancelInFlightRequest()` (`:956`) — take the tool token **before** its existing
  `inFlightConnection.take() ?: return false` early return, or a tool-only cancel returns `false` and
  the button is inert. Keep the `(): Boolean` signature (F-7).
- `clearInMemorySessionState()` (`:1480`) and `shutdown()` (`:1455`) inherit it through
  `cancelInFlightRequest()`.
- **`deleteSession` (`:851`) needs an explicit call added** — it does not go through
  `cancelInFlightRequest()` (F-5).

### Pattern P-4 — the Settings busy seam (D-10, Rule T-1, FLAG-23-06)

Minimal shape, mirroring the existing `setDialogParent` precedent exactly:

```kotlin
// SettingsPanel.kt — one field, immediately beside `internal var dialogParent: JComponent? = null` (:49)
internal var busyListener: ((Boolean) -> Unit)? = null

// SettingsPanelActions.kt — beside `fun SettingsPanel.setDialogParent(component: JComponent)` (:35-37)
fun SettingsPanel.setBusyListener(listener: (Boolean) -> Unit) {
    busyListener = listener
}

// BottomTabsPanel.kt init — one line after `settingsPanel.setDialogParent(root)` (:93)
settingsPanel.setBusyListener { busy -> setActionsBusy(busy) }
```

> ```kotlin
> fun SettingsPanel.setDialogParent(component: JComponent) {
>     dialogParent = component
> }
> ```
> [VERIFIED: src/main/kotlin/com/six2dez/burp/aiagent/ui/SettingsPanelActions.kt:35-37 — the precedent Rule T-1 names]

**FLAG-23-06 is a two-layer `finally`, not one.** The seam must lower even if (a) the completion
callback throws, and (b) the worker dies before it can post the tail. `OffEdtDispatch.run`'s inner
`finally` covers (a); wrapping the *whole* worker body so the lowering is posted from a `finally`
covers (b). The test must exercise **the failure path**, not only the success path — checker
recommendation 3 in `23-UI-SPEC.md` §"Checker Sign-Off" says exactly this.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---|---|---|---|
| Off-EDT execution with a Swing tail | A `SwingWorker`, a coroutine scope, an `ExecutorService` on `ChatPanel` | `OffEdtDispatch` (P-1) over `Thread` + `invokeLater` | SC6 names the idiom; CONVENTIONS.md:95 bans coroutines here; D-05 bans the owned executor |
| Superseding a stale async result | A `cancelled: Boolean` flag, a generation counter, a `synchronized` block | The `InFlightConnectionTracker` CAS shape (P-3) | Already in the file, already tested, already the reviewed answer for the same problem on the backend turn |
| Knowing when async work finished, in a test | `Thread.sleep`, polling, retry-until-true | A latch fed by the completion observer (Open Question 1) | `CONCERNS.md` §`RedactionTest` documents what wall-clock costs this project |
| Bounding MCP shutdown | A second timeout, a `shutdownNow` | `KtorMcpServerManager.stop()` unchanged (D-14) | Its `RESTART-SAFE` comment records that terminating the shared executor breaks the next `start()` [VERIFIED: KtorMcpServerManager.kt:252-255] |
| Reaching a button in another class | Making the fields `internal`, walking the component tree, a static registry | A listener seam mirroring `setDialogParent` (P-4) | One precedent, one line, no new visibility |
| Cancelling an in-flight Burp HTTP request | `Thread.interrupt()`, a `Future.cancel(true)` | Nothing — discard the result (D-07) | `Http.sendRequest` takes no cancellation token [CITED: Montoya javadoc] |
| Redaction-consistency across a settings save | A lock, a `synchronized(settings)`, a settings generation number | The existing per-call `McpToolContext` snapshot (F-2) | It is already there and already correct for everything it carries |

**Key insight:** every mechanism this phase needs already exists in the codebase in a reviewed,
tested form. The phase is almost entirely *wiring an existing shape to a new caller*. Anything that
looks like new machinery is a signal to go and find the shape that already exists.

---

## Runtime State Inventory

This is a threading refactor, not a rename or a data migration. Each category was checked explicitly.

| Category | Items Found | Action Required |
|---|---|---|
| Stored data | **None.** No schema, key, collection name or persisted field changes. Sessions still serialise to `api.persistence().extensionData()` (`ChatPanel.projectData()`, `:1441`) and settings to `settingsRepo`; AI-SPEC §4 §State Management records *"Persistence is untouched. No schema change in this phase."* [VERIFIED: 23-AI-SPEC.md §4; ChatPanel.kt:1441] | none |
| Live service config | **None.** The MCP server's bind/port/token settings are *applied* by the code this phase moves, but no value changes and no external service config is edited. `KtorMcpServerManager` behaviour is explicitly not re-opened (D-14). | none |
| OS-registered state | **None.** No OS registration exists — the artefact is a single fat JAR loaded by Burp. Thread **names** are new (`burp-ai-tool-exec`, `burp-ai-settings-save`) but they are process-local and not registered anywhere. | none |
| Secrets / env vars | **None.** No key name, env var or `.env` entry is read or written by this phase. | none |
| Build artifacts / installed packages | **`detekt-baseline.xml` is signature-keyed state that this phase can invalidate** (F-7): three `ReturnCount` entries and one `LongParameterList` entry key off exact signatures of functions this phase touches. Also `build.gradle.kts`'s four `inputs.file` declarations (F-8). | Keep `cancelInFlightRequest(): Boolean`, `openToolDialog()` and `handleToolCommand(...)`'s signatures byte-identical; add an `inputs.file` declaration for any newly source-read file; **do not regenerate the baseline** — STATE.md:58 pins 1096 as the milestone metric. |

**The canonical question, answered:** after every source file is changed, the only runtime state that
still carries an assumption from before the change is the **detekt baseline** and the **Gradle
test-input declarations**. Both are build-time, both are in git, and both are listed above.

---

## Common Pitfalls

### Pitfall 1 — naming a test `*ConcurrencyTest`
**What goes wrong:** the entire SC1–SC5 suite runs nightly on ubuntu only and never on the
macOS/Windows PR matrix.
**Why:** `build.gradle.kts:206-213` excludes five suffixes under `-PexcludeHeavyTests=true`, which is
what `build.yml:47` passes [VERIFIED].
**How to avoid:** `ChatPanelEdtConfinementTest`, `McpToolExecutorEdtGuardTest`, `SettingsSaveAsyncTest`.
**Warning signs:** the natural name is the wrong one here — `ChatPanelConcurrencyTest` already exists
and is already excluded. `ChatPanelTestHarness.kt:37-40` carries this warning in its own KDoc; read it.

### Pitfall 2 — landing the guard and the call-site moves in separate commits
**What goes wrong:** guard-first turns `slashCommandPathIsNotDoublePrompted` red; moves-first leaves
the guard with nothing to catch.
**Why:** the harness's `invokeAndWait` puts the `/tool` path on the EDT today [VERIFIED].
**How to avoid:** one commit, per D-04. See Open Question 6 for how this shapes the tracer.
**Warning signs:** a plan whose task list has "add guard" and "move `/tool`" as separate tasks with
separate commits.

### Pitfall 3 — offload-then-block
**What goes wrong:** the UI stays frozen and the symptom is indistinguishable from the bug.
**Why:** it already happened in this repo — `MontoyaHttpTransport.execute` dodges Burp's EDT
exception with a daemon thread and then blocks the EDT on `task.get(...)` [VERIFIED:
MontoyaHttpTransport.kt:79-92].
**How to avoid:** the review rule in P-1.
**Warning signs:** any `.get(`, `.join(`, `.await(` or `invokeAndWait` appearing after a dispatch line.

### Pitfall 4 — a silent worker exception
**What goes wrong:** a tool that throws produces nothing at all — no transcript row, no log, a panel
stuck busy.
**Why:** Burp does not catch or report exceptions thrown on extension background threads (E9's
premise; `MontoyaHttpTransport` and `MainTab` both keep their work inside try/catch for this reason).
**How to avoid:** `runCatching` inside the worker (P-1), `logToError` on escape, and the `Result`
carried **intact** across the `invokeLater` boundary so `reportFailedToolCall` (`ChatPanel.kt:2859`)
still gets a `Throwable` rather than a stringified message.
**Warning signs:** a worker body that converts a throw into a `String` before marshalling.

### Pitfall 5 — splitting or reordering the audit pair
**What goes wrong:** a cancelled call becomes an unlogged call — §1b's sharpest domain failure.
**Why:** `toolDecisionReporter.report(...)` **returns** the `metadata` map that
`supervisor.aiRequestLogger?.log(...)` consumes, and reporting is first because the logger is nullable
[VERIFIED: ChatPanel.kt:2864-2891, read in full].
**How to avoid:** both calls in the EDT tail, in that order, on **every** exit including supersede.
**Warning signs:** `report` inside the worker; `log` called with a freshly-built map.

### Pitfall 6 — an under-synchronised async test
**What goes wrong:** the flake class `CONCERNS.md` already records against `RedactionTest`, now in the
tests that are supposed to prove the phase.
**Why:** `drainEdt()` cannot see a daemon worker [VERIFIED].
**How to avoid:** the worker-aware await (Open Question 1) **before** any scenario is written.
**Warning signs:** `drainEdt()` immediately followed by a `verify(...)` on tool-side behaviour — i.e.
exactly `ChatPanelToolGateTest.kt:357-358` as it stands today.

### Pitfall 7 — asserting on elapsed milliseconds
**What goes wrong:** a green suite that fails under CPU load and reads as a regression in whatever is
in flight.
**Why:** `.planning/codebase/CONCERNS.md` §"`RedactionTest` has a wall-clock flake" records the cost,
paid repeatedly. Phases 21 and 22 both observed it and correctly left it alone.
**How to avoid:** the handshake in Open Question 2.
**Warning signs:** `System.currentTimeMillis()` in a test; `assertTrue(elapsed < N)`.

### Pitfall 8 — growing the detekt baseline
**What goes wrong:** the v0.10.0 milestone metric moves the wrong way and Phase 26 inherits it.
**Why:** F-7 — `executeApprovedToolCall` (~83 lines) and `applyAndSaveSettings` (~51 lines) are not
baselined for `LongMethod` and the threshold is 80.
**How to avoid:** extract the marshalled tails into private functions; keep the three baselined
signatures byte-identical; **never** run `./gradlew detektBaseline`.
**Warning signs:** a diff that touches `detekt-baseline.xml`.

### Pitfall 9 — a structural assertion on an undeclared Gradle input
**What goes wrong:** the guard is served from cache and never runs, in exactly the case it exists to
catch (the 22-09 defect).
**Why:** F-8 — only four files are declared as `tasks.test` inputs.
**How to avoid:** add the `inputs.file` declaration in the same commit as the assertion.
**Warning signs:** a test calling `File("src/main/…").readText()` on a path not in `build.gradle.kts`.

### Pitfall 10 — assuming session delete inherits the supersede
**What goes wrong:** S-05 passes vacuously and a real session delete leaves a worker writing to a
disposed panel.
**Why:** F-5 — `deleteSession` calls `resolvePending`, never `cancelInFlightRequest`.
**How to avoid:** add the explicit call and assert it.

---

## Code Examples

### The SC3 handshake — deterministic, no wall-clock assertion

```kotlin
// Source: composed from ChatPanelTestHarness.kt:79-134 (deep-stub construction) and
// ChatPanelToolGateTest.kt:377 (assertTimeoutPreemptively precedent), both read this session.
@Test
fun theEdtRunsQueuedWorkWhileAToolCallIsMidFlight() {
    val workerThread = AtomicReference<Thread?>(null)
    val toolEntered = CountDownLatch(1)
    val probeRan = CountDownLatch(1)

    val h = ChatPanelTestHarness.create(modelResponse = toolCall("proxy_http_history", """{"count":5}"""))

    // The ONLY tool-body seam that exists: the deep-stub MontoyaApi (McpToolExecutor is an object).
    whenever(h.api.proxy().history()).thenAnswer {
        workerThread.set(Thread.currentThread())        // captured, not inferred (E2 + E9, one capture)
        toolEntered.countDown()
        // The tool waits on the EDT. A BLOCKED EDT therefore DEADLOCKS and the preemptive timeout
        // fails the test; a FREE EDT completes. Nothing here measures elapsed time.
        check(probeRan.await(FAILSAFE_SECONDS, TimeUnit.SECONDS)) {
            "The EDT never ran a queued runnable while the tool was mid-call — it was blocked inside it."
        }
        emptyList<ProxyHttpRequestResponse>()
    }

    assertTimeoutPreemptively(Duration.ofSeconds(30)) {
        ChatPanelTestHarness.sendUserMessage(h, "summarise the proxy history")
        ChatPanelTestHarness.drainEdt()
        click(requireNotNull(ChatPanelTestHarness.findApprovalCard(h.panel.root)) { NO_CARD }, "Approve once")

        assertTrue(toolEntered.await(FAILSAFE_SECONDS, TimeUnit.SECONDS), "The tool worker never started.")
        SwingUtilities.invokeLater { probeRan.countDown() }   // queued WHILE the tool is mid-call
        ChatPanelTestHarness.awaitToolSettled(count = 1)
        ChatPanelTestHarness.drainEdt()
    }

    val t = requireNotNull(workerThread.get())
    assertFalse(SwingUtilities.isEventDispatchThread() && t.name == "AWT-EventQueue-0", "…")
    assertNotEquals("AWT-EventQueue-0", t.name, "SC1/SC2: the tool must not execute on the EDT.")
    assertTrue(t.isDaemon, "E9/D-08: a non-daemon worker can block extension unload.")
    assertTrue(t.name.startsWith("burp-ai-tool"), "E9: a stuck worker must be identifiable in a thread dump.")
}
```

**Why the two timeouts are not a wall-clock assertion.** Neither number is compared against the work.
`FAILSAFE_SECONDS` (recommend 10) exists only so a deadlock fails instead of hanging CI, and
`Duration.ofSeconds(30)` is the same failsafe the suite already uses at `ChatPanelToolGateTest.kt:377`
for the same reason (*"A non-monotone budget would loop forever; fail the suite instead of stalling
CI."*). This is categorically different from `RedactionTest`, where the 50 ms `SafeRegex` deadline is
the **same order of magnitude as the work being timed** [VERIFIED: CONCERNS.md §RedactionTest — *"the
4 MB newline-free fixture already spends ~1.9 s of that 2 s budget on reference hardware"*]. Here the
margin is four orders of magnitude.

**Red-before-green behaviour on HEAD:** the tool body runs on the EDT, so `probeRan` can never be
counted down, `check(...)` fails after the failsafe, the exception propagates out of `runCatching`
(`ChatPanel.kt:2856`) into the transcript, and the thread assertions fail too. Both halves go red.
Cost of the red run: ~10 s. Keep the failsafe at 10 s, not 60 s, for exactly this reason.

### The worker-aware await (harness addition)

```kotlin
// ChatPanelTestHarness.kt — the phase's FIRST commit, per AI-SPEC §5 Eval Tooling.
private val settled = LinkedBlockingQueue<String>()

/** Installed in @BeforeEach, cleared in @AfterEach — the AuditLogger.registerGlobalEmitter discipline. */
fun installSettledObserver() {
    settled.clear()
    OffEdtDispatch.registerSettledObserver { label -> settled.add(label) }
}

fun releaseSettledObserver() = OffEdtDispatch.registerSettledObserver(null)

/**
 * Blocks until [count] tool workers have finished their marshalled EDT tail, then drains the EDT.
 *
 * `drainEdt()` alone cannot do this: it drains the EDT QUEUE and knows nothing about a daemon
 * worker (ChatPanelTestHarness.kt:211). The observer fires from the tail's `finally`, so a
 * successful await means the tail has ALREADY run on the EDT.
 */
fun awaitToolSettled(count: Int, failsafeSeconds: Long = 10) {
    repeat(count) { i ->
        requireNotNull(settled.poll(failsafeSeconds, TimeUnit.SECONDS)) {
            "Tool worker ${i + 1} of $count never settled — the async dispatch or its EDT tail is broken."
        }
    }
    drainEdt()
}
```

### The dispatched chain call site

```kotlin
// ChatPanel.executeApprovedToolCall, after the change. Everything the worker needs is read HERE.
private fun executeApprovedToolCall(...): ToolCallOutcome {
    val captured = ToolCallCapture(                    // small private data class — keeps the helper
        backendId = backendIdFor(sessionId),           // under detekt's functionThreshold: 10 (F-7)
        chainStep = chainStepFor(remainingToolIterations),
        canonicalId = McpToolExecutor.canonicalToolId(call.tool),
        startedAt = System.currentTimeMillis(),
        // … panel, context, approved.origin/.tier/.decision, traceId, remainingToolIterations
    )
    // -- After this point the worker must not read a @GuardedBy("EDT") map. --
    setSendingState(true)                              // S3 (UI-SPEC Rule S-2/S-3)
    val token = Any()
    runningTool.set(token)

    OffEdtDispatch.run(
        threadName = "burp-ai-tool-exec",
        label = captured.traceId,                      // WR-11: select by trace id, never by position
        logError = { api.logging().logToError(it) },
        work = { McpToolExecutor.executeTool(call.tool, call.argsJson, captured.context, approved.origin) },
        onEdt = { result -> finishApprovedToolCall(captured, token, result, onCompleted) },
    )
    return ToolCallOutcome.EXECUTING          // NEW — see F-6; both `== NOT_CHAINED` sites fall through
}
```

**`label = traceId`** is deliberate and carries a Phase 22 lesson forward: the most recent Phase 22
commit is *"test(22): make the SC3 metadata assertion select by trace id, not by position"*
[VERIFIED: `git log` — commit `ab55ff5`]. An ordering assertion keyed on trace id survives a
reordering bug by *reporting* it; one keyed on list position hides it.

---

## Open Questions — answered

### 1. The worker-aware await mechanism

**Recommendation: a `@Volatile` completion observer on the `OffEdtDispatch` helper, fired from the EDT
tail's `finally`, registered and cleared by the test exactly like `AuditLogger.registerGlobalEmitter`.**

Three candidates were weighed against what actually exists:

| Candidate | Verdict |
|---|---|
| A `CountDownLatch` in the executor test double | **Impossible.** `McpToolExecutor` is an `object` and every call site references it statically (F-1). There is no double. |
| An injected `Executor`/dispatcher seam on `ChatPanel` | **Rejected on a measured cost.** It requires an 11th constructor parameter, which invalidates `detekt-baseline.xml:141`'s verbatim `LongParameterList` entry (F-7), and STATE.md:58 pins the baseline as a milestone metric. It also changes every production construction site for a test-only benefit. |
| A completion hook the production dispatch helper exposes | **Chosen.** |

Why the hook wins on its merits and not only by elimination:

- **Precedent, in this exact test class.** `AuditLogger.registerGlobalEmitter` is a `@Volatile`
  nullable static hook installed in `@BeforeEach` and cleared in `@AfterEach` with a comment
  explaining why [VERIFIED: AuditLogger.kt:18-22; ChatPanelToolGateTest.kt:640-653]. The reviewer has
  already accepted this shape once.
- **It signals the right event.** A latch inside the Montoya stub signals *tool entry* and *tool exit*;
  the race at `ChatPanelToolGateTest.kt:358` lives in the window **between the worker returning and
  its `invokeLater` tail running**. Only a hook fired from the tail closes that window. This is the
  distinction that makes the whole difference and it is why "block the Montoya stub" is necessary but
  not sufficient.
- **It satisfies SC3's ordering clause directly.** The observer carries the call's `traceId`, so
  "eight results in submission order" is asserted against the recorded sequence of settle events —
  by identity, per WR-11 — rather than inferred from transcript positions.
- **Its production cost is one nullable field read per tool call**, inside a block that is already
  doing Swing work.

Use **both** seams, for different jobs: the Montoya stub blocks (to create the mid-flight window and
to capture the worker `Thread`), the observer awaits (to know the tail has run). They are
complementary, not alternatives.

*Residual to record honestly:* this is production code whose only consumer is a test. Mitigate it the
way `AuditLogger` does — a KDoc line saying so, `@Volatile`, null in production, and an `@AfterEach`
that clears it so it cannot leak across test classes.

### 2. Asserting "the EDT processed queued work while a tool was mid-call"

**Recommendation: a mutual latch handshake in which the tool waits on the EDT — see the code example
above. No elapsed-time assertion anywhere.**

The mechanically sound property is: *make a free EDT a precondition for the tool returning.* Then a
blocked EDT is a **deadlock**, not a slow path, and a deadlock is a categorical failure that
`assertTimeoutPreemptively` reports — the same mechanism `ChatPanelToolGateTest.kt:377` already uses
for a non-monotone chain budget. Nothing in the test compares a duration to a threshold, so there is
no number to tune and no machine speed to depend on.

Contrast with the flake class: `RedactionTest` fails because a 50 ms per-pattern deadline is the same
order as the work it bounds [VERIFIED: CONCERNS.md]. Here the failsafe is 10 s against work that
completes in microseconds once the handshake resolves.

Two corollaries for the plan:
- The red-before-green demonstration costs its full failsafe (~10 s) once, on HEAD. Budget for it and
  keep the failsafe at 10 s.
- The same handshake generalises to S-11 (settings interleave): the settings worker blocks on a latch
  that a tool worker counts down, proving the two really do run concurrently rather than being
  serialised by an accident of scheduling.

### 3. Detecting EDT-ness in a test double

**Recommendation: capture `Thread.currentThread()` itself into an `AtomicReference<Thread>` inside the
Montoya stub's `thenAnswer`, and assert three things off that one capture.**

`SwingUtilities.isEventDispatchThread()` evaluated inside the stub body is the primitive; capturing
the `Thread` rather than a `Boolean` is strictly better because it satisfies **E2** (not the EDT),
**E9's daemon clause** (`t.isDaemon`) and **E9's naming clause** (`t.name.startsWith("burp-ai-tool")`)
from a single observation. "Captured, not inferred" then means: the assertion reads the recorded
`Thread`; it never re-derives thread identity after the fact, when the answer would be the *test's*
thread.

Two capture points, deliberately:
- **call-level** (the Montoya stub) for S-01/S-02 — proves the production path dispatched correctly;
- **door-level** (`McpToolExecutorEdtGuardTest` calling `executeToolResult` from
  `SwingUtilities.invokeAndWait` and asserting `IllegalStateException`) for S-10 — proves the guard
  fires with `-ea` off, which is SC1's explicit requirement.

Neither substitutes for the other: the first can pass with no guard at all, the second can pass with
all three call sites still on the EDT. D-02's "two places at once" is mirrored by two tests.

### 4. Settings atomicity (E8 / CONTEXT §Deferred)

**Recommendation: an accepted-and-documented residual, narrowed to one named window and pinned by one
interleave test. Do not add mutual exclusion.**

The premise changes once F-2 is on the table. `McpToolContext` is an **immutable data-class snapshot
built on the EDT before dispatch**, carrying `privacyMode`, `hostSalt`, `toolToggles`, `unsafeEnabled`,
`enabledUnsafeTools`, `maxBodyBytes`, `edition` and the limiter [VERIFIED: McpToolContext.kt:19-48;
ChatPanel.kt:3005-3023]. **Snapshot-before-dispatch — one of the three candidate answers — is already
the shipped architecture for everything the context carries.** There is nothing to build for that
half.

What genuinely remains is the global state the worker reaches *through* the snapshot:

| Global | Written by `applyAndSaveSettings` | Read by the worker | Torn? |
|---|---|---|---|
| `Redaction.compiledCustomPatterns` | `Redaction.setCustomPatterns(...)` (`SettingsPanelSettingsIO.kt:466-467`) | `context.redactIfNeeded` → `Redaction.apply` | **No** — a whole new `List<Pattern>` is assigned to a `@Volatile` field [VERIFIED: Redaction.kt:769-789 — `@Volatile private var compiledCustomPatterns: List<Pattern> = emptyList()` and `setCustomPatterns` assigning `patterns.mapNotNull { … }` wholesale] |
| `AuditLogger.enabled` | `audit.setEnabled(...)` (`:463`) | audit emission | No — `@Volatile Boolean` [VERIFIED: AuditLogger.kt:33-34] |
| `AgentProfileLoader` active profile | `:459` | prompt building (not the tool path) | n/a |

So the **entire** reachable half-applied state is: *snapshot `privacyMode` from before the save,
combined with the custom-pattern set from after it* (or the converse). Every combination is a valid
mode paired with a valid, fully-published pattern list. There is no state in which a call is redacted
under **no** rules, and none in which the pattern list is partially compiled.

Why not mutual exclusion: a lock held across `applyAndSaveSettings` would be held across
`KtorMcpServerManager.stop()`'s bounded **10-second** wait. A tool dispatch would then either block on
it — and if the lock were taken before dispatch, that block is **on the EDT**, which is precisely the
`future.get(10s)` shape this phase exists to delete — or fail closed with a message the user cannot
act on. That is a strictly worse trade than the residual.

Why not "tighten the snapshot further": there is nowhere to tighten. The snapshot is already taken at
the last EDT statement before dispatch.

**What the plan must therefore do — four small, concrete things:**
1. **Record the residual** in `23-*-SUMMARY.md` and in a comment at the dispatch site, in the
   fail-closed language the codebase already uses: *the worker redacts under the privacy mode captured
   at dispatch and the custom-pattern set current at execution; both are always fully published, never
   torn.*
2. **Fix the stale comment.** `Redaction.kt:767-768` justifies `@Volatile` by *"writes from the EDT
   (save)"* — D-11 makes that rationale false while the behaviour survives. Rewrite it to name the
   settings **worker** as the writer. CONVENTIONS.md §Comments forbids temporal language, so state the
   current writer, not the change.
3. **Keep the privacy-relevant global writes contiguous** inside the worker body
   (`Redaction.setCustomPatterns` and `audit.setEnabled`), so the window is one readable block rather
   than scattered across ten mutations.
4. **Pin it with S-11.** A tool worker blocked mid-call while `applyAndSaveSettings` runs; assert the
   worker's `context.privacyMode` is the **pre-save** value (proving the snapshot held) and that its
   output is fully redacted under *some* consistent policy — never unredacted. That assertion goes red
   if a future refactor ever makes the worker read `getSettings()` directly.

*Ownership note:* AI-SPEC E8 assigns the decision itself to the maintainer-as-practitioner. This is a
**recommendation with its reasoning**, not a locked answer; the plan should carry it to the maintainer
as a one-line confirm.

### 5. The `BottomTabsPanel` seam (D-10 / Rule T-1 / FLAG-23-06)

**Recommendation: exactly the shape in Pattern P-4** — one `internal var busyListener` field on
`SettingsPanel` beside `dialogParent` (`:49`), one `setBusyListener` extension in
`SettingsPanelActions.kt` beside `setDialogParent` (`:35-37`), one install line in `BottomTabsPanel.init`
after `:93`. Three lines of production code plus the private `setActionsBusy` that applies Rule T-2's
recolor to `saveButton` and a plain `isEnabled = false` to `restoreButton`.

Two things research adds beyond the UI-SPEC:

- **FLAG-23-06 needs a two-layer `finally`, not one** (P-4). The failure the flag names — Settings
  permanently unsaveable — is reachable not only from a throwing completion callback but from a worker
  that dies before it posts its tail. Both layers, and the test asserts the **failure** path.
- **`SettingsPanel` looks headlessly constructible, which would let `SettingsSaveAsyncTest` drive the
  real panel** rather than assert structurally. Evidence: the constructor takes seven injectable
  collaborators [VERIFIED: SettingsPanel.kt:30-38], `init { initUiWiring() }` is the only init work
  [VERIFIED: SettingsPanel.kt:492-494], and a grep for `Toolkit.`, `Desktop.`, `JFileChooser`,
  `getRootFrame`, `GraphicsEnvironment` and `ImageIO` across `SettingsPanel.kt` and `SettingsPanelInit.kt`
  returns **nothing** — the one `Toolkit.getDefaultToolkit().systemClipboard` in the settings files is
  at `SettingsPanelMcpTabs.kt:144`, inside a click handler, not in construction. An in-memory
  `Preferences` fake already exists at `SettingsDefaultsPersistenceTest.kt:68`.
  **This is inference from absence, so treat it as a 30-minute spike, not a fact** — it is the highest
  ROI unknown in the phase's second half. If it fails, the documented fallback is structural
  assertions on `SettingsPanelSettingsIO.kt` **plus the matching `inputs.file` declaration** (F-8).

### 6. Wave / plan decomposition, and the tracer

**The tracer question has a wrinkle nobody has named: "one call site moved with the guard in place" is
impossible.** D-04's sequencing constraint requires the guard to land with **all three** moves,
because the guard immediately breaks `slashCommandPathIsNotDoublePrompted` (`:350`) otherwise.
Guard-in-the-tracer therefore forces the whole of D-01 into the tracer, tripling its diff in a
3024-line file — which is what tracer-first exists to avoid.

**Recommendation — the thinnest honest tracer is the *chain call site with its full lifecycle, minus
the guard*:** `ChatPanel.kt:2856` dispatched via `OffEdtDispatch`, S3 busy state, the supersede
tracker, the cancel path (D-07: token taken, new C-1 transcript line, no iteration refund, audit pair
still fires), the marshalled EDT tail with the audit pair intact, `ToolCallOutcome.EXECUTING`, and the
harness worker-aware await — verified by S-01, S-02 and S-04 with S-01/S-02 recorded red against HEAD.

That is a *production-quality end-to-end slice*: one path, finished, including its exits. The guard is
a **lock on the door**, not part of the mechanism — deferring it by exactly one plan (which lands it
in one commit with the two remaining call sites, as D-04 requires) costs nothing and keeps the tracer
reviewable.

| Plan | Wave | Depends on | Content | Closes |
|---|---|---|---|---|
| **23-01** *(tracer)* | 1 | — | `OffEdtDispatch` (P-1) + harness `awaitToolSettled` + chain site moved + S3 + supersede tracker + full cancel path + `EXECUTING` + extracted EDT tail | S-01, S-02, S-04; E2 (chain), E3, E9 (partial) |
| **23-02** | 2 | 23-01 | The two user-originated sites (`:1010`, `:2319`) + Rule S-4 `/tool` echo + **the throwing guard, same commit** + fix `slashCommandPathIsNotDoublePrompted` + `McpToolExecutorEdtGuardTest` | SC1 fully, S-10, S-03, E1, E2 (all sites) |
| **23-03** | 2 *(parallel with 23-02)* | 23-01 *(helper only)* | Settings: busy seam, `applyAndSaveSettings` off-EDT, D-13 completion callback for both callers, T-1/T-2, C-2 copy, the E8 residual + comment fix, `SettingsSaveAsyncTest` | SC4, S-11, E7, E8, FLAG-23-06 |
| **23-04** | 3 | 23-01, 23-02 | The remaining teardown paths — **explicit** supersede in `deleteSession` (F-5), project change, unload — plus worker-throws (S-12) and the E10 rewrite (F-3) | S-05, S-06, S-07, S-08, S-09, S-12; E4, E6, E9, E10 |
| **23-05** | 4 | all | SC5 regression evidence (new `invokeLater` count stated with a reason per addition, `assertEdt()` byte-identity), `23-VALIDATION.md`, `23-HUMAN-UAT.md` (FLAG-23-01 L&F check, FLAG-23-04 flicker, D-12 modal), final gate | SC5, SC6, E5 |

Why 23-02 and 23-03 are genuinely parallel: they share **zero** files. 23-02 touches `ChatPanel.kt`
and `McpToolExecutorImpl.kt`; 23-03 touches `SettingsPanel*.kt` and `BottomTabsPanel.kt`. Their only
shared symbol is `OffEdtDispatch`, delivered and frozen by 23-01. That is the same wave shape Phase 22
used (nine plans, six waves, `wave: 3` carrying three parallel plans) [VERIFIED: `grep -h '^wave:' .planning/phases/22-*/22-*-PLAN.md`].

Why 23-04 is not folded into 23-01: it is four teardown paths × two assertions each, and the tracer is
already the largest plan. Splitting keeps the tracer's diff readable, which is its whole purpose.

---

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|---|---|---|
| A1 | Burp throws *"Extensions should not make HTTP requests in the Swing event dispatch thread"* when an extension calls `sendRequest` from the EDT. Corroborated by third-party extension bug reports and by this repo's own `MontoyaHttpTransport.kt:79-92` workaround citing issue #80 — but the Montoya `Http` javadoc documents **no** threading requirement, and no live-Burp confirmation was made this session. `[CITED: github.com/Quitten/Autorize/issues/1, github.com/irsdl/BurpSuiteJSBeautifier/issues/12]` `[VERIFIED: MontoyaHttpTransport.kt:79-92]` — LOW for the live behaviour, HIGH for the workaround's existence | §Summary, Pitfall 3 | **Low.** S-02 does not depend on it: the test configures the Montoya *mock* to throw when on the EDT, so it pins *"we do not call Burp's HTTP API from the EDT"* regardless of what Burp really does. The only thing at risk is the *framing* of SC2 as "a defect, not a latency". Confirm in `23-HUMAN-UAT.md`; do not gate the plan on it. |
| A2 | `SettingsPanel` is headlessly constructible from deep-stub collaborators | Open Question 5 | **Medium.** Inference from a grep that found no headless-hostile call in the construction path — absence of evidence. If false, `SettingsSaveAsyncTest` falls back to structural assertions and must add an `inputs.file` declaration (F-8). Spike it in 23-03's first task. |
| A3 | Extracting the EDT tails keeps `executeApprovedToolCall` and `applyAndSaveSettings` under detekt's `LongMethod` threshold of 80 | F-7, Pitfall 8 | **Low-medium.** Line counts (~83 and ~51 source lines) were measured; detekt's exact counting rules were not re-derived. Mitigation is mechanical: run `./gradlew detekt` before commit and extract further if needed. Never regenerate the baseline. |
| A4 | Adding `ToolCallOutcome.EXECUTING` needs no change at the two `== NOT_CHAINED` branch sites | F-6 | **Low.** Both sites were read in full (`:759`, `:2621`) and both test only for `NOT_CHAINED`. The risk is a *third* consumer added later; the KDoc line is the mitigation. |
| A5 | The maintainer accepts the E8 residual rather than mutual exclusion | Open Question 4 | **Low.** AI-SPEC E8 assigns this call to the maintainer-as-practitioner. Carry it as a one-line confirm in the plan; the reasoning is above and the alternative's cost is named. |
| A6 | `SwingUtilities.isEventDispatchThread()` inside `mcp/tools/` is headless-safe and initialises no AWT state | Pattern P-2 | **Very low.** It reads `EventQueue.isDispatchThread()`, a pure thread comparison. The whole test suite already runs with `-Djava.awt.headless=true` and constructs a real `ChatPanel`. |

---

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|---|---|---|---|---|
| JDK 21 (Temurin) | Gradle 8.12.1 build | ✓ | `21` (CI pins `java-version: '21'` [VERIFIED: build.yml:40-43]) | none — default JDK 25 breaks Gradle 8.12.1 (MEMORY.md); prefix every command `JAVA_HOME=$(/usr/libexec/java_home -v 21)` |
| Gradle wrapper | all builds | ✓ | 8.12.1 | — |
| JUnit Jupiter | every test | ✓ | 6.0.3 | — |
| mockito-kotlin (inline mock maker) | deep-stub `MontoyaApi`, final Kotlin classes | ✓ | 5.4.0 | — |
| ktlint / detekt | `lint` CI job (blocking) | ✓ | ktlint 1.5.0 via plugin 12.1.1; detekt with `detekt.yml` + `detekt-baseline.xml` | — |
| `-Djava.awt.headless=true` in `tasks.test` | headless `ChatPanel` construction | ✓ | already set [VERIFIED: referenced at ChatPanelTestHarness.kt:31 and AI-SPEC §5] | — |
| A running Burp Suite | A1's live confirmation; UAT items | ✗ (not available to this agent) | — | Route to `23-HUMAN-UAT.md`; do not gate CI on it |

**Missing dependencies with no fallback:** none.
**Missing dependencies with fallback:** a live Burp instance — routed to human UAT, which is the
established practice for Phases 20–22.

---

## Validation Architecture

### Test Framework

| Property | Value |
|---|---|
| Framework | JUnit Jupiter 6.0.3 + mockito-kotlin 5.4.0, Gradle `useJUnitPlatform()` |
| Config file | `build.gradle.kts` (`tasks.test`, incl. `-Djava.awt.headless=true`, the `excludeHeavyTests` filter at `:201-214`, and four `inputs.file` declarations at `:166-200`) |
| Quick run command | `JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test --tests 'com.six2dez.burp.aiagent.ui.ChatPanelEdtConfinementTest'` |
| Full suite command | `JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew ktlintCheck detekt test` |

**Naming constraint, and it decides whether the PR gate runs any of this** (Pitfall 1): the new suites
**must not** end in `IntegrationTest`, `ConcurrencyTest`, `BackpressureTest`, `RestartPolicyTest` or
`SupervisionTest`. Use `ChatPanelEdtConfinementTest`, `McpToolExecutorEdtGuardTest`,
`SettingsSaveAsyncTest`.

### Phase Requirements → Test Map

Grounded in AI-SPEC §5's E1–E10 and S-01…S-12; no parallel scheme is invented. Every command below is
`./gradlew test --tests '<class>' --tests-method` style and runs in the fast PR gate.

| Req / SC | Dimension · Scenario | Behavior | Test Type | Automated Command | File Exists? |
|---|---|---|---|---|---|
| SC1 | E2 · S-10 | `executeToolResult` throws `IllegalStateException` when entered from `SwingUtilities.invokeAndWait`, with `-ea` off | unit | `./gradlew test --tests '*McpToolExecutorEdtGuardTest'` | ❌ Wave 23-02 |
| SC1 | E2 · S-01 | The chain call site reaches the executor on a non-EDT, daemon, named thread — captured, not inferred | integration (headless real `ChatPanel`) | `./gradlew test --tests '*ChatPanelEdtConfinementTest'` | ❌ Wave 23-01 |
| SC1 | E2 · S-03 | A `Deny` produces **zero** `executeToolResult` invocations and starts no worker | integration | same class | ❌ Wave 23-02 |
| SC2 | E2 · S-02 | A Montoya double that throws when called on the EDT is never called on the EDT (**red against HEAD**) | integration | same class | ❌ Wave 23-01 |
| SC2 | E2 · F-4 | The guard precedes the `ext:` early return, so `routeExternalToolCall`'s `runBlocking` is covered | unit | `*McpToolExecutorEdtGuardTest` | ❌ Wave 23-02 |
| SC3 | E3 · S-01 | A runnable queued to the EDT runs **while** the tool is mid-call (mutual latch handshake, no wall-clock); 8 results in submission order **by trace id**; busy state cleared exactly once (**red against HEAD**) | integration | `*ChatPanelEdtConfinementTest` | ❌ Wave 23-01 |
| SC4 | E7 · S-11 | `applyAndSaveSettings` runs off the EDT; the EDT is not blocked on `future.get(10, SECONDS)`; `currentSettings()` snapshot still taken on the EDT; both callers report from the completion callback; Save disabled in flight | integration | `./gradlew test --tests '*SettingsSaveAsyncTest'` | ❌ Wave 23-03 |
| SC4 | E7 · FLAG-23-06 | The busy seam lowers on the **failure** path, not only on success | integration | same class | ❌ Wave 23-03 |
| SC5 | E5 | `assertEdt()` and its 6 call sites byte-identical; new `invokeLater` count stated with a per-addition reason; no worker-side read of a `@GuardedBy("EDT")` map | structural + integration | `*ChatPanelEdtConfinementTest` (+ `inputs.file` already declared for `ChatPanel.kt`) | ❌ Wave 23-05 |
| SC6 | E9 · S-07 | The worker thread is `isDaemon` and named; unload does not join it and does not block; a throwing worker writes `logToError` | integration | `*ChatPanelEdtConfinementTest` | ❌ Waves 23-01 / 23-04 |
| REL-05 (audit) | E4 · S-04/05/06/07/09/12 | The audit pair fires — `report` first, its `metadata` consumed by `log` — on **every** exit incl. cancel and supersede; the `Result` crosses the boundary intact | integration | same class | ❌ Waves 23-01 / 23-04 |
| REL-05 (budget) | E6 · S-04/S-09 | Cancel and supersede send no followup turn and **refund no iteration**; the chain still terminates at 8; `onCompleted` discharged on every exit | integration | extends `eightConsecutiveDenialsTerminateTheChainWithNoNinthTurn` (`ChatPanelToolGateTest.kt:368`) | ⚠️ partial — extend |
| REL-05 (E10, **rewritten**) | E10 · S-08 | A `/tool` racing a chain: both dispatch off-EDT, both execute, the chain is unharmed, and **no** `"Too many concurrent MCP requests."` appears — each chat call site mints its own limiter (F-3) | integration | `*ChatPanelEdtConfinementTest` | ❌ Wave 23-04 |
| E1 | E1 · S-03/S-09 | No worker starts for a model-originated call until the gate produced `Run` **on the EDT**; exactly one `executeToolResult` per approved call; a superseded card produces no second execution | integration | same class | ❌ Waves 23-02 / 23-04 |
| E8 | E8 · S-11 | A tool worker dispatched during a settings save redacts under its **snapshot** privacy mode and is never unredacted; the stale `Redaction.kt:767-768` comment is corrected | integration + structural | `*SettingsSaveAsyncTest` | ❌ Wave 23-03 |

### Sampling Rate

- **Per task commit:** `JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test --tests '*ChatPanelEdtConfinementTest' --tests '*McpToolExecutorEdtGuardTest' --tests '*SettingsSaveAsyncTest' --tests '*ChatPanelToolGateTest'`
- **Per wave merge:** `JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew ktlintCheck detekt test`
- **Phase gate:** full suite green, **plus** `./gradlew test -PexcludeHeavyTests=true` to confirm the
  new suites actually run under the PR-gate filter (Pitfall 1's direct check), **plus**
  `git diff --stat detekt-baseline.xml` empty (Pitfall 8), before `/gsd-verify-work`.

### Wave 0 Gaps

- [ ] `src/test/kotlin/.../ui/ChatPanelTestHarness.kt` — add `awaitToolSettled(count, failsafe)`,
      `installSettledObserver()` / `releaseSettledObserver()`. **Prerequisite for every chat scenario;
      AI-SPEC §5 names it the phase's first commit.**
- [ ] `src/main/kotlin/.../ui/OffEdtDispatch.kt` — the helper the harness observes.
- [ ] `src/test/kotlin/.../ui/ChatPanelEdtConfinementTest.kt` — covers SC1, SC2, SC3, SC5, SC6, E1–E6, E9, E10.
- [ ] `src/test/kotlin/.../mcp/tools/McpToolExecutorEdtGuardTest.kt` — covers S-10.
- [ ] `src/test/kotlin/.../ui/SettingsSaveAsyncTest.kt` — covers SC4, E7, E8. **Gated on the A2 spike.**
- [ ] `build.gradle.kts` — an `inputs.file` declaration for any newly source-read main file (F-8).
- [ ] Framework install: **none** — JUnit, mockito-kotlin, JaCoCo and the headless flag are all present.

**Human/UAT residuals, stated so they are not silently dropped** (AI-SPEC §5 and UI-SPEC flags):
D-12's `JOptionPane` (not headless-testable — `getRootFrame()` throws `HeadlessException`), FLAG-23-01
(does `isEnabled = false` read as disabled on the opaque orange Save button under Burp's L&F),
FLAG-23-04 (the sub-frame Send↔Cancel flicker on the auto-approved path), and A1 (live confirmation of
Burp's EDT `sendRequest` exception). All four go to `23-HUMAN-UAT.md`.

---

## Security Domain

`security_enforcement` is not disabled in `.planning/config.json`, so this section is required.

### Applicable ASVS Categories

| ASVS Category | Applies | Standard control in this phase |
|---|---|---|
| V1 Architecture | **yes** | The trust boundary is `ToolApprovalGate` (SEC-06 / ADR-15). This phase changes **where** an approved call runs, never **whether** it runs. E1 is the dimension that goes red if that inverts. |
| V2 Authentication | no | No credential handling is touched. The MCP bearer-token path is not modified. |
| V3 Session Management | **partially** | Chat "sessions" are UI state, not auth sessions — but REL-01's EDT confinement of the five `@GuardedBy("EDT")` maps is the integrity control, and E5 protects it. |
| V4 Access Control | **yes** | The approval gate and `unsafeOnly` tool gating are the access controls. Both must remain evaluated **on the EDT before dispatch**; a worker that re-evaluates or bypasses either is E1's FAIL condition. |
| V5 Input Validation | no change | Model-supplied JSON is decoded through `kotlinx-serialization` `@Serializable` data classes (AI-SPEC §4b); untouched. |
| V6 Cryptography | no change | HKDF host anonymisation and the audit hashing are untouched — never hand-rolled here. |
| V7 Error Handling & Logging | **yes** | D-03's throwing guard, E9's `logToError` wrapping, and E4's audit-on-every-exit are all V7 controls. *"Burp does not catch and report exceptions in background threads"* makes an unwrapped worker throw a **silent** failure. |
| V8 Data Protection | **yes** | E8: a tool worker must never redact under a half-applied policy. See Open Question 4. |

### Known Threat Patterns for Kotlin/Swing/Montoya in this phase

| Pattern | STRIDE | Standard mitigation |
|---|---|---|
| Time-of-check/time-of-use between gate approval and dispatch | Elevation of Privilege | Capture `approved.origin`/`.tier`/`.decision` on the EDT before dispatch; the worker re-evaluates nothing (E1) |
| Double execution of one approved call (worker + resolution path) | Elevation of Privilege | Supersede CAS token — `clearIfMatches` returns false for the loser (E1, S-09) |
| A cancelled call reaching Burp but never being logged | Repudiation | D-07's corollary: the audit pair fires on **every** exit incl. supersede (E4) |
| Silent worker exception hiding a failed security-relevant call | Repudiation | `runCatching` + `logToError` in `OffEdtDispatch`; `Result` carried intact to `reportFailedToolCall` (E9, S-12) |
| Data race on the five `@GuardedBy("EDT")` maps from the new worker | Tampering | Everything captured before dispatch; no worker-side map read (E5). Phase 17 explicitly declined converting them to concurrent collections. |
| Half-applied privacy policy observed by a concurrent tool call | Information Disclosure | Per-call immutable `McpToolContext` snapshot (F-2) + the documented residual (E8) |
| A stuck non-daemon worker blocking extension unload | Denial of Service | `isDaemon = true` set explicitly, thread named, unload never joins (E9, S-07) |
| A future fourth call site bypassing the dispatch helper | Elevation of Privilege *(threading)* | The throwing door guard at `executeToolResult` — D-02's "the guard makes it stay" |

**Out of scope and must not be fixed here** (carried from CONTEXT §Deferred): chat-originated HTTP
tool calls bypassing Burp target scope (`McpToolContext.scopeOnly` defaults `false` and
`ChatPanel.buildToolContext` never passes it, while `McpRuntimeContextFactory` does). This is a real
asymmetry and a candidate SEC-family requirement — **triage it, do not touch it in Phase 23.**

---

## State of the Art

| Old approach | Current approach | When changed | Impact on this phase |
|---|---|---|---|
| `assert()`-based EDT checks | A throwing check at the boundary | This phase (D-03) | `assert()` is a no-op in shipped Burp; SC1 requires a check that fires in the field |
| Modelled UI tests (`ChatPanelConcurrencyTest`) | A real headless `ChatPanel` from deep stubs (`ChatPanelTestHarness`) | Phase 22, 2026-08-14 | `CONCERNS.md` records the "high setup cost" objection as **measured false**. Do not cite it again. |
| Wall-clock assertions | Invariant / handshake assertions | Phase 21–23 | `CONCERNS.md` §RedactionTest is the cost record |
| Tool execution synchronous on the EDT | Daemon worker + single `invokeLater` marshalling point | This phase | The whole of REL-05 |

**Deprecated / outdated in the specs themselves:**
- *"a `CountDownLatch` in the executor test double"* (AI-SPEC §5 Eval Tooling; CONTEXT §Claude's
  Discretion) — **no such double exists** (F-1).
- *"`McpRequestLimiter` contention becomes reachable"* (CONTEXT §Deferred; UI-SPEC FLAG-23-05;
  AI-SPEC E10/S-08) — **false** (F-3).
- `ChatPanelConcurrencyTest.kt:59-71`'s KDoc premise that a real `ChatPanel` throws `HeadlessException`
  — stale, recorded in `CONCERNS.md`, **not this phase's to fix**.
- `Redaction.kt:767-768`'s *"writes from the EDT (save)"* — goes stale under D-11; **this phase must
  correct it** (Open Question 4, item 2).

---

## Sources

### Primary (HIGH confidence — read at source this session)

- `src/main/kotlin/com/six2dez/burp/aiagent/ui/ChatPanel.kt` — lines 60-145, 455-502, 630-760, 775-800,
  851-890, 930-1030, 1435-1495, 1738-1764, 2290-2335, 2596-2640, 2820-2925, 3000-3024
- `src/main/kotlin/com/six2dez/burp/aiagent/mcp/tools/McpToolExecutorImpl.kt` — 1-60, 120-175, 1035-1075
- `src/main/kotlin/com/six2dez/burp/aiagent/mcp/McpToolContext.kt` — 1-95
- `src/main/kotlin/com/six2dez/burp/aiagent/mcp/McpRequestLimiter.kt` — whole file
- `src/main/kotlin/com/six2dez/burp/aiagent/mcp/McpRuntimeContextFactory.kt` — 23-33
- `src/main/kotlin/com/six2dez/burp/aiagent/mcp/McpSupervisor.kt` — 125-145
- `src/main/kotlin/com/six2dez/burp/aiagent/mcp/KtorMcpServerManager.kt` — 245-285
- `src/main/kotlin/com/six2dez/burp/aiagent/ui/SettingsPanel.kt` — 1-60, 492-494
- `src/main/kotlin/com/six2dez/burp/aiagent/ui/SettingsPanelActions.kt` — 35-100, 315-345, 131-136
- `src/main/kotlin/com/six2dez/burp/aiagent/ui/SettingsPanelSettingsIO.kt` — 440-515
- `src/main/kotlin/com/six2dez/burp/aiagent/ui/BottomTabsPanel.kt` — 1-110
- `src/main/kotlin/com/six2dez/burp/aiagent/ui/MainTab.kt` — 185-220
- `src/main/kotlin/com/six2dez/burp/aiagent/backends/http/MontoyaHttpTransport.kt` — 75-100
- `src/main/kotlin/com/six2dez/burp/aiagent/redact/Redaction.kt` — 764-800, 1133-1139
- `src/main/kotlin/com/six2dez/burp/aiagent/audit/AuditLogger.kt` — 1-45
- `src/test/kotlin/com/six2dez/burp/aiagent/ui/ChatPanelTestHarness.kt` — whole file
- `src/test/kotlin/com/six2dez/burp/aiagent/ui/ChatPanelToolGateTest.kt` — 330-400, 600-700, 874-899
- `src/test/kotlin/com/six2dez/burp/aiagent/ui/SettingsDefaultsPersistenceTest.kt` — 1-75
- `build.gradle.kts` — 166-260; `detekt.yml` — whole file; `detekt-baseline.xml` — targeted grep
- `.github/workflows/build.yml` — 38-55
- `.planning/REQUIREMENTS.md`, `.planning/ROADMAP.md` §Phase 23, `.planning/STATE.md`,
  `.planning/config.json`, `.planning/codebase/CONVENTIONS.md`, `.planning/codebase/CONCERNS.md`
- `23-CONTEXT.md`, `23-UI-SPEC.md`, `23-AI-SPEC.md` (§4, §4b, §5, Orchestrator notes)

### Secondary (MEDIUM confidence)

- PortSwigger Montoya `Http` javadoc — confirms four `sendRequest` overloads and **no** cancellation or
  threading parameter; documents nothing about the EDT.
  `[CITED: https://portswigger.github.io/burp-extensions-montoya-api/javadoc/burp/api/montoya/http/Http.html]`

### Tertiary (LOW confidence — flagged for validation)

- Third-party reports of Burp's EDT `sendRequest` exception:
  `[CITED: https://github.com/Quitten/Autorize/issues/1]`,
  `[CITED: https://github.com/irsdl/BurpSuiteJSBeautifier/issues/12]`. Legacy-API-era reports; the
  Montoya-era behaviour is inferred. **Assumption A1.**
- PortSwigger extension guidance that slow operations must not run on the EDT.
  `[CITED: https://portswigger.net/burp/documentation/desktop/extend-burp/extensions]`

---

## Metadata

**Confidence breakdown:**
- Standard stack: **HIGH** — zero new dependencies; every primitive verified present in the tree.
- Architecture: **HIGH** — every pattern is a shape already in the codebase, cited by file and line.
- Fact verification: **HIGH** — all ten facts re-read at source; three corrections carry their evidence.
- Pitfalls: **HIGH** — nine of ten are measured in this repo; the tenth (A1) is tagged LOW and
  explicitly does not gate anything.
- Open-question recommendations: **HIGH** for 1, 2, 3, 5, 6 (each rests on a verified structural fact);
  **MEDIUM** for 4 (the residual is verified; the *acceptance* of it is the maintainer's call per E8).

**Research date:** 2026-08-20
**Valid until:** 2026-09-19 (30 days — the codebase is the only fast-moving input, and Phase 23 is the
next thing to touch it; re-verify the `invokeLater` count and the three baselined signatures if any
other work lands in `ChatPanel.kt` first)
