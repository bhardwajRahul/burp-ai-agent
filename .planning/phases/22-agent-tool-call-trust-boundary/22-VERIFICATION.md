---
phase: 22-agent-tool-call-trust-boundary
verified: 2026-08-14T11:43:20Z
status: human_needed
score: 6/6 must-haves verified
overrides_applied: 0
human_verification:
  - test: "The approval card renders legibly in live Burp, in both themes"
    expected: "Model text and extension text distinguishable at a glance; four actions on CONFIRM / two on CONFIRM_EACH; args preview expands and is readable; tier badge legible in both themes"
    why_human: "Visual legibility is not assertable headlessly. 22-UI-SPEC.md T-4 records that the light-theme background channel is nearly invisible (#F5F5F5 card on #FFFFFF field), so the 1px border and monospace font carry the signal — whether that suffices for a glancing user is a human judgement"
  - test: "ADR-15 is factually accurate against the shipped code"
    expected: "All four D-14 elements present; no unqualified claim that is false the day it was written; every Residual: bullet true"
    why_human: "DecisionsAdrTest guards the sentence, not the truth. Verifier confirmed the four required elements are present and the three post-review corrections are honest, but full factual audit of every claim is judgement. See WARNING 4 — several line citations in ADR-15 are stale"
  - test: "Research assumption A1 — intruder and intruder_prepare stage a tab without sending"
    expected: "Both populate an Intruder tab; neither puts outbound traffic on the wire until the user clicks Start"
    why_human: "Montoya Intruder.sendToIntruder runtime semantics; no automated seam observes real outbound traffic. Affects one sentence's wording in ADR-15, not a tier assignment"
  - test: "Research assumption A2 — user_options_get exports credential material"
    expected: "Export contains upstream-proxy credentials, session-handling rules, TLS client-certificate paths"
    why_human: "Requires a live Burp with an upstream proxy configured with credentials. Confirms the justification for classifying *_get tools as CONFIRM despite being read-only"
warnings:
  - id: "WARN-1"
    concern: "toolNameSha256 carries the same 120-char truncation defect CR-03 fixed for argsSha256"
    artifacts:
      - path: "src/main/kotlin/com/six2dez/burp/aiagent/mcp/ToolDecisionReporter.kt"
        line: 208
        issue: "Hashing.sha256Hex(sanitizeInline(rawToolName).orEmpty()) digests a whitespace-collapsed 120-character prefix. On the !knownTool branch the plaintext name is replaced by \"unknown\", so this hash is the ONLY record of what the model asked for — and two unresolvable names differing only past character 120 record identically"
    severity: "Narrower than CR-03: affects unresolvable names only, which always resolve to CONFIRM_EACH and always show the user the full sanitized name on the card. Does not fail open — sanitizeInline still strips control characters before hashing, so no forgery is possible"
  - id: "WARN-2"
    concern: "repeater_tab / repeater_tab_with_payload are CONFIRM, contradicting ADR-15's own stated CONFIRM_EACH criterion"
    artifacts:
      - path: "src/main/kotlin/com/six2dez/burp/aiagent/mcp/McpToolCatalog.kt"
        line: 260
        issue: "ADR-15 defines CONFIRM_EACH as the tier for tools that put attacker-chosen traffic on the wire 'or stage it for one click', and applies that clause explicitly to intruder / intruder_prepare. McpToolExecutorImpl.kt:243-255 shows repeater_tab builds an HttpRequest from model-supplied input.content and stages it in Repeater for one Send click — the same shape — yet it ships as CONFIRM, so one Approve-for-session click covers every later repeater_tab call with different request content in that chat"
    severity: "Does not break SC6 — the tool still requires a user decision and is never AUTO — but it is an inconsistency between the ADR text and the shipped table, in a phase whose whole point is that the ADR governs the classification"
  - id: "WARN-3"
    concern: "ChatPanel.isKnownTool trusts the ext: prefix without validating against configured servers"
    artifacts:
      - path: "src/main/kotlin/com/six2dez/burp/aiagent/ui/ChatPanel.kt"
        line: 2423
        issue: "canonicalId.startsWith(\"ext:\") || catalog lookup. An ext: name no configured server exposes is filed as knownTool = true with no toolNameSha256, while the executor rejects it. Its own KDoc claims it checks 'an ext: name belonging to a configured external server', which the code does not do"
    severity: "Disclosed as a Residual in ADR-15:197. CR-01's unconditional sanitization bounds what such a name can do; it does not make the classification true"
  - id: "WARN-4"
    concern: "Stale line citations in ADR-15 and the new files"
    artifacts:
      - path: "DECISIONS.md"
        line: 182
        issue: "Cites ToolApprovalGate.kt:319-329 for the fail-closed tier resolution; tierFor actually sits at 356-366 (319-329 is the object's KDoc). ADR-15:201 cites ChatPanel.kt:1600 for restoreSessions; it is at 1528"
    severity: "Documentation accuracy only. The cited behaviour exists in every case — the numbers point at the wrong lines"
  - id: "WARN-5"
    concern: "dispatchResolvedToolCall discards the ToolCallOutcome"
    artifacts:
      - path: "src/main/kotlin/com/six2dez/burp/aiagent/ui/ChatPanel.kt"
        line: 2618
        issue: "Returns Unit, so a NOT_CHAINED outcome from reportFailedToolCall (approved call whose tool then throws) never reaches the caller and pending.onCompleted is never discharged on that path. The un-asked path handles the same case at ChatPanel.kt:759"
    severity: "Latent. Verified across src/main and src/test that openChatWithContext (MainTab.kt:527,536) — the only producer of a non-null onCompleted — has no callers, so onCompleted is always null today"
  - id: "WARN-6"
    concern: "No automated guard prevents approvedOrigin being widened back to internal"
    artifacts:
      - path: "src/main/kotlin/com/six2dez/burp/aiagent/mcp/ToolApprovalGate.kt"
        line: 383
        issue: "CR-02 narrowed approvedOrigin from internal to private, and the compiler enforces the current state — but widening it again compiles cleanly and fails no test. This control already regressed once, silently, and the ADR asserted the property anyway"
    severity: "The three CR fixes each shipped with a regression test except this one; the evidence recorded for it is a manual compile probe documented in ToolApprovalGate.kt:26-30"
  - id: "WARN-7"
    concern: "SEC-06 is still unchecked in REQUIREMENTS.md while six plan summaries claim it complete"
    artifacts:
      - path: ".planning/REQUIREMENTS.md"
        line: 18
        issue: "Reads '- [ ] **SEC-06**'. 22-03/04/05/07/08/09-SUMMARY.md all carry requirements-completed: [SEC-06]"
    severity: "Bookkeeping. Verifier assessment: the control substantively ships and the checkbox should now be marked [x]"
---

# Phase 22: Agent Tool-Call Trust Boundary Verification Report

**Phase Goal:** A tool call the extension parsed out of model output cannot reach Burp without the user deciding, and the reasoning is written down so future tools inherit it.
**Verified:** 2026-08-14T11:43:20Z
**Status:** human_needed
**Re-verification:** No — initial verification

## Goal Achievement

The single most decisive piece of evidence for this phase's goal is structural and was checked first,
before any success criterion: **there is exactly one place in the codebase where model output is parsed
into a tool call.**

```
$ grep -rn "extractFirst" src/main/kotlin/
src/main/kotlin/com/six2dez/burp/aiagent/ui/ToolCallParser.kt:20:    fun extractFirst(...)
src/main/kotlin/com/six2dez/burp/aiagent/ui/ChatPanel.kt:2334:  val call = ... ToolCallParser.extractFirst(responseText) ...
```

That one site (`ChatPanel.maybeExecuteToolCall`) reaches the gate nine lines later at `ChatPanel.kt:2343`
and has no branch to `McpToolExecutor` that does not pass through it. There are exactly three
`executeTool` call sites in the whole main source set, each declaring a distinct required
`ToolCallOrigin`, and the model one can only be satisfied by a token minted inside `ToolApprovalGate`.

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | ADR records the threat model, which tool classes require a decision and why, and why the `[EXTERNAL-TOOL-RESULT:...]` note is mitigation not a control | ✓ VERIFIED | `DECISIONS.md:178-205`. Threat model at :180 names all three attacker-controlled sources (Send-to-AI proxy traffic, passive-scan findings, external MCP results) and the inference chain. Tool classes at :182 with D-05's AUTO sentence verbatim at :184-185. The mitigation-not-control argument at :180 gives the *checkable* reason: the note at `McpToolExecutorImpl.kt:128-134` is appended only when external tools are present, so a session with no external MCP server gets no trust-boundary instruction at all. Guarded by `DecisionsAdrTest` (4/4 pass) |
| 2 | A model-emitted tool call surfaces a decision before execution — approve once, approve-for-session, deny — and denial continues the conversation rather than erroring the session | ✓ VERIFIED | Gate consulted at `ChatPanel.kt:2343` before any executor call; `Ask` branch parks the chain at :2495 with the card inserted via `SessionPanel.addComponent` at :2479. `DENIAL_RESULT` (`ToolApprovalGate.kt:343`) is deliberately not `Error:`-prefixed; `denyToolCall` (:2662-2731) sends a followup turn with a closing line that does not point at a nonexistent tool result. `confirmToolDoesNotReachBurpBeforeADecision`, `approveOnceExecutesTheCall`, `approveForSessionSuppressesTheNextCard`, `denyReturnsAResultThatLetsTheConversationContinue`, `denyForSessionResolvesLaterCallsWithNoCard`, `confirmEachOffersOnlyTwoActions` all pass against a real `ChatPanel` driven through its real Send button |
| 3 | Every decision is written to the audit log with tool name, decision and chain step, consistent with the `MCP_TOOL_CALL` telemetry shape | ✓ VERIFIED | `ToolDecisionReporter.report` → `AuditLogger.emitGlobal("mcp_tool_decision", payload)` at :151, payload's first four keys (`operation`/`status`/`traceId`/`step`) are `MCP_TOOL_CALL`'s existing shape, extended with `toolName`/`secTier`/`decision`. Called from **all five** decision branches: :2781 (approved run, incl. AUTO), :2682 (denial), :2853 (approved-then-threw), :2530 (click with transcript gone), :2590 (implicit deny). Reported *before* the nullable `aiRequestLogger?.log` at both :2681 and :2780, so SC3 does not depend on the other sink being wired. `everyDecisionEmitsTheSc3Metadata` asserts `toolName`/`secTier`/`step` on every event and `status = denied` on refusals. See WARN-1 |
| 4 | The auto-chain still terminates: `MAX_AUTO_TOOL_ITERATIONS = 8` respected, and a denied call does not consume the remaining budget in a loop | ✓ VERIFIED | `MAX_AUTO_TOOL_ITERATIONS = 8` at `ChatPanel.kt:1307`. `nextIterationBudget(remaining) = (remaining-1).coerceAtLeast(0)` at `ToolApprovalGate.kt:497`; both the approve branch (:2822) and the deny branch (:2728) call it — one decrement, not two copies. `eightConsecutiveDenialsTerminateTheChainWithNoNinthTurn` drives a full budget of denials through the real `maybeExecuteToolCall` under a 30s preemptive timeout and asserts an exact turn count first (so an under-drained chain fails loudly rather than passing vacuously), then that further draining produces no further turn, then `never()` on the Burp call |
| 5 | A user-initiated call through `ToolInvocationDialog` is not double-prompted; the gate applies to model-originated calls and the two paths are distinguishable in code | ✓ VERIFIED | Exactly three `executeTool` sites, each with a required non-defaulted origin: `ChatPanel.kt:1010` `ToolCallOrigin.UserDialog`, `:2313` `UserSlashCommand`, `:2759` `approved.origin`. `userDialogPathIsNotDoublePrompted` asserts on the `openToolDialog` source text that it declares its origin and mentions neither `ToolApprovalGate` nor `ToolApprovalCard` (the modal cannot be constructed headlessly). `slashCommandPathIsNotDoublePrompted` proves it behaviourally on a CONFIRM tool: `times(1)` on `api.proxy().history()` and no card. CR-02 verified fixed — `approvedOrigin` is `private` at `ToolApprovalGate.kt:383`, `ModelApproved` is top-level `private` at :182. See "SC5 guarantee strength" below and WARN-6 |
| 6 | Read-only vs state-mutating tools are treated per the ADR's classification, not one blanket rule chosen at implementation time | ✓ VERIFIED | `enum class SecTier { AUTO, CONFIRM, CONFIRM_EACH }` at `McpToolCatalog.kt:22-33`; `val secTier: SecTier` at :45 is required and non-defaulted, so a new tool that omits it does not compile. All 59 descriptors declare one: 19 AUTO + 26 CONFIRM + 14 CONFIRM_EACH = 59 (counted from source, matches the catalog size assertion). Every tool SC6 names is CONFIRM or CONFIRM_EACH, none AUTO — `http1_request`/`http2_request`/`scope_include`/`intruder` CONFIRM_EACH, `repeater_tab`/`scope_exclude` CONFIRM (the latter carries an inline justification at :223). `McpToolCatalogTierParityTest` pins both tier sets as enumerated literals, plus `noUnsafeToolIsAuto` and `tierIsIndependentOfUnsafeOnly`. See WARN-2 |

**Score:** 6/6 truths verified

### SC5 guarantee strength — judged against what ships

The phase originally documented SC5's origin control as file-scoped. It is not, and the codebase now
says so in three places (`ToolApprovalGate.kt:31-44`, `McpToolExecutorImpl.kt:1032-1036`,
`DECISIONS.md:187`). Kotlin seals a `sealed interface` to its package and module, never to a file, so a
new file under `com.six2dez.burp.aiagent.mcp` can declare its own `ToolCallOrigin` implementation and
pass it to `executeTool`. Both halves were measured with compile probes, and the weaker claim is what
ADR-15 now carries.

**The weaker guarantee satisfies SC5 as written.** SC5 asks for two things: that a user-initiated
`ToolInvocationDialog` call is not double-prompted, and that the two paths are distinguishable in code.
Both are fully met — the dialog path declares `UserDialog`, touches neither gate nor card, and the model
path cannot obtain its origin without going through `evaluate`/`resolve`. The file-scoped seal was a
stronger property the plan volunteered, not something SC5 requires. What ships closes the accidental
bypass (a new parse-and-execute site cannot compile without an origin, and cannot mint the model one),
and leaves a deliberate bypass from inside package `mcp` visible in a diff rather than prevented by the
type system. For a single-module Kotlin codebase that is the strongest available guarantee, and stating
it accurately is worth more than the overclaim it replaced.

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `src/main/kotlin/.../mcp/ToolApprovalGate.kt` | AWT-free decision core, sealed origin, tiers, denial constant, budget helpers | ✓ VERIFIED | 551 lines. Import list is `McpToolExecutor` only — no Swing, no AWT, so every decision is unit-testable with no harness. `evaluate` (:402) branch order is the evaluation order: AUTO → CONFIRM_EACH → denied → approved → ask, so "CONFIRM_EACH touches no session set" is structural |
| `src/main/kotlin/.../mcp/ToolDecisionReporter.kt` | Ordered audit payload, hash-by-default, Output line, `MCP_TOOL_CALL` metadata | ✓ VERIFIED | 328 lines. `linkedMapOf` for pinned key order; one `buildPayload` feeds both sinks; `isDenial` is an exhaustive `when` so a ninth `ToolDecision` is a compile error rather than a silent `status = ok` |
| `src/main/kotlin/.../ui/components/ToolApprovalCard.kt` | Inline card, 4/2 actions, args disclosure, resolved outcome row | ✓ VERIFIED | 937 lines. Model text lands only in `JTextField` (:304) and `JTextArea` (:308); `JLabel` is used only for extension-derived strings. `<html>` appears at :557/:862/:865 with extension-authored, integer-only-interpolated text — safe today, coverage gap noted as WR-06 in review |
| `src/main/kotlin/.../ui/ChatPanel.kt` | Gate wired into `maybeExecuteToolCall`, card, four callbacks, denial variant, three-valued outcome | ✓ VERIFIED | 2926 lines. `ToolCallOutcome` (:1732) is three-valued; `AWAITING_DECISION` parks `onCompleted` in `PendingToolDecision` (:1756); `resolvePending` (:2572) is the single retirement entry point reached from all five teardown paths (:523, :862, :1083, :1448, :1473) |
| `src/main/kotlin/.../mcp/McpToolCatalog.kt` | `SecTier` enum + non-defaulted `secTier` on all 59 tools | ✓ VERIFIED | `enum class SecTier` at :22; `val secTier: SecTier` at :45 with no default; 59 declaration sites counted |
| `src/main/kotlin/.../mcp/tools/McpToolExecutorImpl.kt` | `executeTool` with required non-defaulted `origin` | ✓ VERIFIED | `:1052-1057`. Parameter is declared and deliberately not consumed — the type is the control, not a runtime check. `executeToolResult` (:137) deliberately has no origin: it serves the MCP-server path (`McpToolHandlers.kt:129`), a separate trust boundary owned by SEC-04/05 |
| `DECISIONS.md` | ADR-15 | ✓ VERIFIED | `:178-205`, 28 lines including 16 Consequences/Residual bullets |
| Test suite (7 classes) | SC-mapped proofs | ✓ VERIFIED | `ChatPanelToolGateTest` 15, `ToolApprovalGateTest` 11, `ToolDecisionReporterTest` 18, `SecTierResolutionTest` 6, `McpToolCatalogTierParityTest` 5, `ToolApprovalCardTest` 9, `DecisionsAdrTest` 4 — **68 tests, 0 failures** |

### Key Link Verification

| From | To | Via | Status | Details |
|------|-----|-----|--------|---------|
| `ChatPanel.maybeExecuteToolCall` | `ToolApprovalGate.evaluate` | Consulted before any executor call | ✓ WIRED | `:2343`, nine lines after the only `extractFirst` in the codebase |
| `ChatPanel` Ask branch | `ToolApprovalCard` | `SessionPanel.addComponent` | ✓ WIRED | `:2466` construction, `:2479` insertion, `:2476` `onDecision` → `resolveToolDecision` |
| `ChatPanel` all decision branches | `ToolDecisionReporter` | `report(...)` then merge into `MCP_TOOL_CALL` | ✓ WIRED | 5 of 5 branches: :2530, :2590, :2682, :2781, :2853 |
| `ToolDecisionReporter` | `AuditLogger.emitGlobal` | `mcp_tool_decision` event | ✓ WIRED | `:151`. Captured live by `ChatPanelToolGateTest`'s global-emitter hook |
| `ToolDecisionReporter` | `Hashing.sha256Hex` | Hashes model-supplied args by default | ✓ WIRED | `:262`, over the whole value (CR-03 fix) |
| `ToolApprovalGate.tierFor` | `McpToolExecutor.canonicalToolId` | Canonicalise before catalog lookup | ✓ WIRED | `:357`. Same function the executor calls, not a copied alias table |
| `ToolApprovalMemory` | `McpToolExecutor.canonicalToolId` | Every key re-canonicalised | ✓ WIRED | `:258` |
| `ChatPanel` teardown paths | `resolvePending` / `resolveAllPending` | Single retirement entry point | ✓ WIRED | 5 of 5: sendFromInput :523, deleteSession :862, clearCurrentChat :1083, shutdown :1448, clearInMemorySessionState :1473 |
| `DecisionsAdrTest` | `DECISIONS.md` | Reads repo-root file, asserts heading + verbatim AUTO sentence | ✓ WIRED | 4/4 pass |

### Data-Flow Trace (Level 4)

| Artifact | Data Variable | Source | Produces Real Data | Status |
|----------|---------------|--------|--------------------|--------|
| `ToolApprovalCard` | `sanitizedToolId`, args area | `call.tool` / `call.argsJson` from `ToolCallParser.extractFirst` on real model output | Yes — the card in `ChatPanelToolGateTest` is found by depth-first search on the live panel and its buttons are clicked | ✓ FLOWING |
| `ToolApprovalCard` | `offersSessionActions` | Read from `ToolApprovalOutcome.Ask`, not re-derived from the tier | Yes — `confirmEachOffersOnlyTwoActions` asserts exactly `[Deny, Approve once]` | ✓ FLOWING |
| `ToolApprovalCard` | `repeatCount` | `state.approvalMemory.recordRequest(canonicalId)` | Yes — real per-session counter, not a constant | ✓ FLOWING |
| `mcp_tool_decision` audit event | payload map | `buildPayload` from live gate outcome | Yes — `everyDecisionEmitsTheSc3Metadata` captures real emissions and asserts `["approve_once", "deny"]` in click order | ✓ FLOWING |
| `MCP_TOOL_CALL` metadata | `metadata` | Returned by the same `report(...)` call, null-filtered | Yes — one construction feeds both sinks | ✓ FLOWING |
| Session-list pending marker | row label | `pendingDecisions` + `refreshSessionList()` | Yes — `sessionRowMarksAPendingDecisionAndClearsItOnResolution` asserts absent → present → absent | ✓ FLOWING |

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| All 7 phase test classes pass | `./gradlew test --tests '*ChatPanelToolGateTest*' … (7 filters)` | BUILD SUCCESSFUL; 68 tests, 0 failures, 0 errors | ✓ PASS |
| Full quality gate | `./gradlew build test ktlintCheck detekt` | BUILD SUCCESSFUL in 2m 32s, exit 0 | ✓ PASS |
| Whole suite | aggregated from `build/test-results/test/*.xml` | 109 classes, **737 tests, 0 failures, 0 errors**, 1 skipped | ✓ PASS |
| detekt baseline not grown (QUAL-07) | `git status --porcelain detekt-baseline.xml` | Empty; last touched `ab567fb` 2026-07-29, before this phase | ✓ PASS |
| Only one model-output parse site | `grep -rn "extractFirst" src/main/kotlin/` | 2 hits: the parser's own declaration and `ChatPanel.kt:2334` | ✓ PASS |
| Exactly three `executeTool` sites, each with a distinct origin | `grep -rn "executeTool(" src/main/kotlin/` | `:1010` UserDialog, `:2313` UserSlashCommand, `:2759` `approved.origin` | ✓ PASS |
| Blocker-fix commits exist | `git log -1 <sha>` ×4 | `6f352eb` CR-01, `47af7e9` CR-02, `6aba71a` CR-03, `ae723f7` ADR correction — all present, all HEAD-adjacent | ✓ PASS |
| No debt markers in phase files | `grep -nE "TBD|FIXME|XXX|TODO|HACK|PLACEHOLDER"` on all 6 production files | Zero matches | ✓ PASS |

### Probe Execution

| Probe | Command | Result | Status |
|-------|---------|--------|--------|
| — | `find scripts -path '*/tests/probe-*.sh'` | No probes in repo; no PLAN or SUMMARY declares one | ? N/A — project uses Gradle test tasks as its runnable gate, exercised above |

### Post-Review Blocker Fixes — Independently Confirmed

The three code-review blockers were re-verified in code rather than accepted from the review's
resolution notes. Two of them bear directly on SC3 and SC5.

| Finding | Claim | Verified in code |
|---------|-------|------------------|
| CR-01 — CWE-117 log injection letting a model forge `[SEC-06]` Output lines | Sanitize on both `knownTool` branches | ✓ `ToolDecisionReporter.kt:302-306` — `outputToolName` calls `sanitizeInline` unconditionally on both branches. Regression test `aRecognisedExtToolNameIsSanitizedBeforeReachingEitherSink` (`:342`) |
| CR-02 — `approvedOrigin` was `internal`, so SC5's control was not compiler-enforced | Narrow to `private` | ✓ `ToolApprovalGate.kt:383` reads `private fun approvedOrigin`, and `ModelApproved` at `:182` is top-level `private`. No regression test — see WARN-6 |
| CR-03 — `argsSha256` hashed only the first 120 chars | Digest the whole value | ✓ `ToolDecisionReporter.kt:257-264` — `auditValue` hashes the value as given; `sanitizeBlock` is applied only on the verbose plaintext seam. Two regression tests: `argsDigestDistinguishesPayloadsThatDifferPastTheInlineCap` (`:226`) and `argsDigestCoversTheWholeArgumentStringNotAPrefixOfIt` (`:243`), the latter asserting `Hashing.sha256Hex(args)` exactly and `assertNotEquals` against the old truncated form |

### On the SC4 acceptance gate's non-vacuity

The gate assertion `verify(h.api.proxy(), never()).history()` in
`confirmToolDoesNotReachBurpBeforeADecision` is only meaningful if the harness is *capable* of reaching
Burp. It is, and the proof sits in the same file: `autoToolStillRunsWithNoCard` drives the identical
harness with an AUTO tool and asserts `atLeastOnce()` on `api.scope().isInScope(...)`. The same
machinery reaches Burp for AUTO and provably does not for CONFIRM. The red-before-green claim against
pre-gate commit `5863de8` was therefore not taken on faith — the non-vacuity is demonstrated by a
passing companion assertion in the current tree.

The assertion order inside the gate test is also correct for the red-before-green standard: the
`never()` verification is written *before* the card assertion, so at the pre-gate commit it is the
Mockito `NeverWantedButInvoked` that fails, rather than a trivially-red missing card type.

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|-------------|-------------|--------|----------|
| SEC-06 | 22-01…22-09 (all 9) | A tool call parsed out of model output does not execute against Burp without an explicit user decision; approve per call, approve-for-session, or deny; the decision is audit-logged; the threat model is recorded as an ADR so future tools inherit the rule | ✓ SATISFIED | Every limb verified: no-execution-without-decision (truth 2, one parse site → gate), the three decision modes (truth 2, `ToolDecision` + card button assertions), audit logging (truth 3, 5/5 branches emit), the ADR (truth 1, `DECISIONS.md:178`), and inheritance by future tools (truth 6, non-defaulted `secTier` makes omission a compile error). **Not yet checked off in `.planning/REQUIREMENTS.md:18` — see WARN-7** |

No orphaned requirements: `grep -E "Phase 22" .planning/REQUIREMENTS.md` maps only SEC-06 to this phase,
and all 9 plans declare it.

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| `ToolDecisionReporter.kt` | 208 | Truncated digest — `sha256Hex(sanitizeInline(rawToolName))` | ⚠️ Warning | WARN-1. Same defect class as the fixed CR-03, narrower blast radius |
| `McpToolCatalog.kt` | 260, 269 | Tier assignment inconsistent with the governing ADR's own criterion | ⚠️ Warning | WARN-2 |
| `ChatPanel.kt` | 2423 | Prefix-trust classification; KDoc overstates what the code checks | ⚠️ Warning | WARN-3, disclosed as ADR-15 Residual |
| `DECISIONS.md`, new files | various | Stale `ChatPanel.kt:NNN` / `ToolApprovalGate.kt:NNN` cross-references | ⚠️ Warning | WARN-4. Confirmed by spot-check: ADR cites `ToolApprovalGate.kt:319-329` for `tierFor`, actual 356-366; cites `ChatPanel.kt:1600` for `restoreSessions`, actual 1528 |
| `ChatPanel.kt` | 2618 | Discarded return value on a security-lifecycle path | ⚠️ Warning | WARN-5, latent — verified no caller produces a non-null `onCompleted` |
| — | — | Debt markers (`TBD`/`FIXME`/`XXX`/`TODO`/`HACK`) | ✓ None | Zero across all six production files |
| — | — | Stub returns, empty handlers, hardcoded empty data | ✓ None | Every artifact traced to real data flow at Level 4 |

### Human Verification Required

Four items are open in `.planning/phases/22-agent-tool-call-trust-boundary/22-HUMAN-UAT.md`, all
`result: [pending]`. They are reproduced in this report's frontmatter. Two of them (1 and 2) bear on
success criteria this verification could only confirm structurally:

**1. The approval card renders legibly in live Burp, in both themes.** `ToolApprovalCardTest` proves
structure — no `JLabel` carries the HTML renderer for model text, button counts match the tier, model
text lands in a bordered region — but not legibility. `22-UI-SPEC.md` §T-4 states that in a light theme
the background channel is nearly invisible (`#F5F5F5` card on `#FFFFFF` field), leaving the 1px border
and monospace font as the load-bearing distinction between extension text and model text. Whether that
is enough for a glancing user is exactly the judgement SC2's decision surface depends on.

**2. ADR-15 is factually accurate.** This verification confirmed the four D-14 elements are present and
that the three post-review corrections are honest about what regressed. A full factual audit of all 16
Consequences/Residual bullets against live behaviour is judgement, and WARN-4 shows the ADR's line
citations are already drifting.

**3 and 4** are research-assumption confirmations that affect ADR wording, not tier assignments — both
UAT entries say so explicitly and neither can change a classification.

### Gaps Summary

**No gaps. The phase goal is achieved.**

Both halves of the goal hold. *"Cannot reach Burp without the user deciding"* is enforced structurally
rather than by a check that a future edit can invert: one parse site, one gate call before it, three
`executeTool` sites each with a required non-defaulted origin, and a model origin that can only be
minted by `evaluate`/`resolve`. *"The reasoning is written down so future tools inherit it"* holds
through a mechanism rather than prose: `secTier` is required and non-defaulted, so a tool added in a
later phase that skips the classification does not compile, and `McpToolCatalogTierParityTest` pins
both tier sets as enumerated literals so a promotion to a laxer tier is a reviewed diff.

This phase was verified with a deliberately hostile eye, because its own code review found the audit
path failing open in two places after every plan reported success. All three blockers are confirmed
fixed in code, not merely in the review's resolution notes, and two of the three shipped with precise
regression tests. The third (CR-02) is compiler-enforced but ungurded by a test, which is WARN-6 — the
one warning I would not defer indefinitely, since that control has already regressed once silently and
the ADR asserted the property anyway while it was broken.

Two warnings deserve a decision rather than a backlog entry:

- **WARN-1** repeats CR-03's defect on `toolNameSha256`. It is genuinely narrower — unresolvable names
  only, always CONFIRM_EACH, always shown in full on the card — but on that branch the hash is the only
  record of what the model asked for, and the fix is a one-line deletion of the `sanitizeInline` call.
- **WARN-2** is the one substantive tension between ADR-15 and the shipped table. ADR-15 defines
  CONFIRM_EACH as covering tools that put attacker-chosen traffic on the wire *or stage it for one
  click*, and applies that clause explicitly to `intruder`/`intruder_prepare`. `repeater_tab` builds an
  `HttpRequest` from model-supplied `content` and stages it in Repeater for one Send click
  (`McpToolExecutorImpl.kt:243-255`) yet ships as CONFIRM, so one approve-for-session click covers every
  later `repeater_tab` call with different request content in that chat. SC6 still passes — the tool is
  never AUTO and always requires a decision — but either the table or the ADR sentence should move.

**Status is `human_needed`, not `passed`,** solely because the four UAT items are open. Every
programmatically verifiable must-have is verified, the full gate is green (737 tests, 0 failures,
detekt baseline unchanged), and nothing blocks proceeding to Phase 23.

**Recommendation on SEC-06 (WARN-7):** mark `- [x] **SEC-06**` in `.planning/REQUIREMENTS.md:18`. The
executors' caution in leaving it unchecked until the control shipped was right; the control has now
shipped and been independently verified against the code. Six of the nine summaries already claim
`requirements-completed: [SEC-06]`, so the traceability document is currently the outlier.

---

_Verified: 2026-08-14T11:43:20Z_
_Verifier: Claude (gsd-verifier)_

---

## Addendum — post-verification remediation (2026-08-14)

The findings above are preserved verbatim as the point-in-time record. Three of them
have since been remediated on `main`, so the SC6 evidence row is now stale.

| Finding | Status | Commits |
|---|---|---|
| WARN-1 — `toolNameSha256` truncated at 120 chars | **Resolved** | `a323980` |
| WARN-2 — repeater tools contradict ADR-15's CONFIRM_EACH criterion | **Resolved** | `431db9d`, `8b42596`, `f396918` |
| WARN-6 — CR-02 origin control had no regression test | **Resolved** | `600b976` |

**Tier split changed.** SC6's evidence row cites `19 AUTO + 26 CONFIRM + 14 CONFIRM_EACH`,
which was accurate when verified. Both `repeater_tab` and `repeater_tab_with_payload` were
subsequently promoted to `CONFIRM_EACH` on the maintainer's decision — resolving WARN-2 by
moving the table to match the ADR rather than weakening the ADR. The catalog now splits
**19 AUTO / 24 CONFIRM / 16 CONFIRM_EACH**. SC6 remains VERIFIED; the classification is
strictly stronger than when it was assessed.

WARN-6's guard is pinned by two independent channels: a source-text assertion and a
bytecode-reflection assertion (Kotlin mangles `internal` member names, so widening
`approvedOrigin` back to `internal` yields `approvedOrigin$burp_ai_agent` and fails).

Gate after remediation: `build test ktlintCheck detekt` exit 0, 742 tests / 110 classes /
0 failures, `detekt-baseline.xml` unchanged.

**Status is unchanged at `human_needed`** — the four items in `22-HUMAN-UAT.md` still require
a live Burp instance. Remaining outstanding findings: WARN-3 (`isKnownTool` trusts the `ext:`
prefix without validating against configured servers) and the other code-review warnings.
