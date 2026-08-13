# Phase 22: Agent Tool-Call Trust Boundary - Research

**Researched:** 2026-08-13
**Domain:** In-process trust boundary for LLM-selected tool invocation (Kotlin / Swing / Burp Montoya)
**Confidence:** HIGH (every load-bearing claim was measured against the real build, not reasoned)

## Summary

This is a brownfield phase inside one repository. There is **no new library, no new dependency, and no
ecosystem question to answer** — CONTEXT.md's twelve locked decisions already fix the design. Research
therefore did what the brief asked: it went and *measured* the four claims the plan depends on, and it
found one that is wrong-as-stated, one that is stronger than assumed, and one that unlocks a
qualitatively better acceptance gate than the phase was scoped for.

The three headline results. **(1) D-03 is verified and safe** — adding a non-defaulted enum field to
`McpToolDescriptor` produces a compile error at every one of the 59 catalog sites *and* at the one test
helper, whether the field is inserted mid-class or at the end. No site silently inherits. The count in
CONTEXT.md ("all 60 descriptors") is off by one: there are **59** tools; `grep -c 'McpToolDescriptor('`
counts the `data class` declaration itself. **(2) SC4 can have a real, non-vacuous acceptance gate.** A
real `ChatPanel` is constructible headless with mockito deep stubs after a **one-line** guard on
`ChatPanel.kt:377-380` (`Toolkit.getDefaultToolkit().menuShortcutKeyMaskEx`, the single headless-hostile
call in the entire construction path). With that guard I drove a model-emitted `scope_check` tool call
through the real Send button and **verified it reached `api.scope().isInScope("http://evil.example/")`
with no user decision**. That assertion is green today and must go red after the fix — exactly the
Phase 20 SC4 / Phase 21 standard. **(3) D-05's `AUTO` sentence, read literally, admits
`project_options_get` and `user_options_get` into `AUTO`** — they are read-only and they are not
"attacker-controlled traffic", but they export the user's upstream-proxy credentials, session-handling
rules and platform-auth material straight into a cloud model's context. That is a wording gap the ADR
must close without breaking D-14's "verbatim" requirement.

Two further structural findings the planner must act on. The async gate does **not** require touching
the REL-01 confinement contract: `maybeExecuteToolCall`'s return type changes from `Boolean` to a
three-valued outcome and the `onCompleted` invocation moves into the resolution callback — everything
still runs on the EDT, so Phase 23's REL-05 work is untouched. And D-08's implicit-denial list is
**incomplete**: `clearCurrentChat()` and `MainTab.onProjectChanged() → clearInMemorySessionState()` both
destroy the card and the session state without resolving a pending decision, which is the exact dangling
continuation D-08 rejected wait-forever to avoid.

**Primary recommendation:** land the headless guard first (Wave 0), build the SC2/SC4/SC5 regression
tests as *real* `ChatPanel` integration tests against it, thread a sealed `ToolCallOrigin` whose
`ModelApproved` variant can only be minted by the gate, and put the tier resolution + decision state
machine in an AWT-free class under `mcp/` so it is unit-testable independently of Swing.

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| SEC-06 tier declaration per tool | Catalog (`mcp/McpToolCatalog.kt`) | — | D-03: the classification lives next to the thing it classifies; a descriptor field is the only place a new tool author cannot miss. |
| Tier resolution (built-in + `ext:` derivation + unknown fallback) | MCP layer (`mcp/`, AWT-free) | — | Must agree by construction with `McpToolExecutor`'s own `resolveAlias` / `startsWith("ext:")` routing. Cannot live in Swing or it is untestable. |
| Approve/deny decision state machine + session memory | MCP layer (AWT-free), held by `ChatPanel.ToolSessionState` | UI (owns lifetime) | D-10 says memory dies with the chat session; the *logic* must be unit-testable without a Swing harness (precedent: `ui/McpToolTabModel.kt`, `redact/`). |
| Decision surface (inline card, four actions) | UI (`ui/components/`, extends `ActionCard`) | — | D-06/D-07. Swing-only; renders extension-derived chrome around sanitized model text. |
| Chain continuation + iteration accounting | UI (`ChatPanel.maybeExecuteToolCall`) | — | D-12/D-13. The continuation is the `sendMessage` handoff; it stays where it is. |
| Tool execution against Burp | MCP layer (`McpToolExecutorImpl.executeTool`) | — | Unchanged this phase. Still on the EDT — that is Phase 23 / REL-05, **report do not fix**. |
| Origin declaration (model vs user) | MCP layer (type on `executeTool`) | UI (call sites declare) | SC5. Must NOT live on `McpToolContext` — see §"SC5 Origin Plumbing". |
| Audit + Output-tab emission | `audit/` via `AuditLogger.emitGlobal` **and** `AiRequestLogger` | — | Phase 20 D-06: both destinations, because audit is off by default. |
| Threat-model record | `DECISIONS.md` ADR-15 | — | D-14. |

## Standard Stack

**This phase installs nothing.** Every mechanism it needs is already in the repository at a pinned
version. Verified by reading `build.gradle.kts` and the existing source.

### Core (already present — no version change)
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Kotlin / JVM | 21 toolchain, Gradle 8.12.1 | Language + build | ADR-1, fixed. Build needs `JAVA_HOME=$(/usr/libexec/java_home -v 21)` on this machine. [VERIFIED: build.gradle.kts, measured] |
| Swing (`javax.swing`) | JDK 21 | Inline decision card | ADR-2, fixed. No new UI framework (CONTEXT §code_context). [VERIFIED: codebase] |
| Burp Montoya API | project-pinned | Tool execution target | ADR-3, fixed. [VERIFIED: codebase] |
| `kotlinx.serialization.json` | project-pinned | Args JSON already parsed by `ToolCallParser` | Existing parser; no change needed. [VERIFIED: `ui/ToolCallParser.kt`] |

### Supporting (test-only — already present)
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| `org.junit.jupiter:junit-jupiter` | 6.0.3 | Test framework | All new tests. [VERIFIED: build.gradle.kts:56] |
| `org.mockito.kotlin:mockito-kotlin` | 5.4.0 | `MontoyaApi` / `AgentSupervisor` deep stubs | The `ChatPanel` integration harness. Mockito 5 uses the inline mock maker by default, so Kotlin's final classes mock without extra config — **measured working** against `AgentSupervisor`. [VERIFIED: build.gradle.kts:58 + measured] |
| `kotlin("test")` | bundled | assertions | Existing convention. [VERIFIED: build.gradle.kts:55] |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Real `ChatPanel` integration test | Modelled test à la `ChatPanelConcurrencyTest` | Modelled tests are the existing precedent and need no production change — but they cannot go red against today's `maybeExecuteToolCall`, so they do not satisfy SC4's acceptance gate. Use the real harness; keep modelled tests only for what the harness genuinely cannot reach. |
| Extending `ActionCard` | New `ApprovalCard` component | `ActionCard` is ~80% of what D-06 needs and is already `UiTheme`-styled, but its five constructor params are `String`-typed labels with no button row. Recommend a **new `ToolApprovalCard`** in `ui/components/` that reuses `ActionCard`'s layout idiom rather than bending `ActionCard`'s one existing caller (`ChatPanel:344`). Measured: `ActionCard` constructs fine headless, so either choice is testable. |

**Installation:** none.

**Version verification:** N/A — no package is added. `npm view` / `pip index` / `cargo search` are not
applicable to a Gradle/Kotlin phase that adds no dependency.

## Package Legitimacy Audit

**Not applicable — this phase installs zero external packages.** No `build.gradle.kts` dependency block
changes are required by any of D-01..D-14 or by any recommendation in this document. `slopcheck` was
therefore not run; there is nothing for it to check.

| Package | Registry | Age | Downloads | Source Repo | slopcheck | Disposition |
|---------|----------|-----|-----------|-------------|-----------|-------------|
| *(none)* | — | — | — | — | — | — |

**Packages removed due to slopcheck [SLOP] verdict:** none
**Packages flagged as suspicious [SUS]:** none

If planning later concludes a dependency *is* needed (it should not), run the Package Legitimacy Gate
before adding it.

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

**Gate scope — which tools require a decision (SC6)**

- **D-01:** SEC-06 gets **its own classification**, independent of `unsafeOnly`. Rejected: reusing
  `unsafeOnly`. Two reasons, both measured against the code. First, `unsafeOnly` is a *capability*
  switch, not a trust model — `McpToolContext.isUnsafeToolAllowed` returns `true` for everything the
  moment `unsafeEnabled` is on, so binding the trust boundary to it means one unrelated toggle
  silently disables the gate. Second, with Unsafe Mode **off** those 21 tools are already blocked, so
  a mutating-only gate is close to a no-op for cautious users while leaving the read-only
  recon/exfiltration path — bulk `proxy_history` pulled into a cloud model's context — fully
  automatic. That path is the one the threat model in SC1 is actually about. Rejected also:
  prompt-for-everything (no classification to get wrong, but an 8-step chain on a fresh session is up
  to 8 dialogs, which is the fastest route to habituated clicking — a safety control that trains the
  user to dismiss it).

- **D-02:** **Three tiers.** `AUTO` (runs silently), `CONFIRM` (prompts; approve-for-session offered),
  `CONFIRM_EACH` (prompts every call; **no** session memory). The third tier exists specifically so
  one click cannot hand the model unlimited `http1_request` / `http2_request` / `intruder` /
  `scan_audit_start` for a whole session — those are the calls that put attacker-chosen traffic on the
  wire, which is most of what SEC-06 exists to bound. Rejected: two tiers, where approve-for-session
  is all-or-nothing.

- **D-03:** The tier is a **required, non-defaulted field on `McpToolDescriptor`**. All 60 descriptors
  in `mcp/McpToolCatalog.kt` must declare one; a new tool that omits it **does not compile**. Direct
  precedent: Phase 21's `ContextPreviewDialog.confirm` carries the comment *"deliberately has NO
  default value… a default is how a future caller silently gets the OFF hint wrong"*. This also closes
  the fragility recorded in `.planning/codebase/CONCERNS.md` §"MCP unsafe-tool gate — new tools must
  opt in", where forgetting to classify is the failure mode. Rejected: a field defaulting to
  `CONFIRM_EACH` (safe when forgotten, but it never forces the author to think, so tools drift into
  the noisiest tier by neglect) and a separate ID-keyed map (two lists to keep in sync by hand —
  exactly the shape Phase 21's cookie work rejected in favour of predicates that agree by
  construction). **Accepted cost:** a wide mechanical diff across `McpToolCatalog.kt`.

- **D-04:** **External MCP tools resolve to `CONFIRM_EACH` from the `ext:` ID prefix**, derived rather
  than declared. They have no catalog descriptor, so deriving from the namespace keeps D-03's change
  confined to the 60 built-ins and means the two sides cannot fall out of sync. Justified by ADR-11's
  own framing — the external server is untrusted. `CONFIRM_EACH` rather than `CONFIRM` because the
  exfiltration shape here has a **constant tool name and hostile args**: injected traffic steers the
  model into calling `ext:<server>:<sink>` with proxy history as an argument, and a per-tool session
  approval would cover every later call regardless of args. **Accepted cost:** real friction for
  anyone using external MCP heavily.

- **D-05:** **`AUTO` means read-only AND bounded output.** A tool qualifies only if it neither mutates
  Burp state nor pulls bulk attacker-controlled traffic into model context. Consequence, and this is
  the load-bearing part: **`proxy_history`, site-map listing and issue listing are `CONFIRM`, not
  `AUTO`.** Rejected: "read-only is enough", which leaves the exfiltration path SEC-06 was written for
  fully automatic; and "read-only AND local-only", which reasons about where the *tool* reaches rather
  than where the *data* goes — the data always goes to the AI backend, so local-only misses the leak
  direction entirely. **Accepted cost:** `AUTO` ends up a small set (decoders, schema/catalog lookups,
  scope reads), so the gate is felt. The per-tool assignment across all 60 is research/planning work
  under this definition; the definition itself is locked and belongs verbatim in ADR-15.

**Decision surface (SC2)**

- **D-06:** The decision is an **inline card in the chat transcript**, not a modal. Reuses
  `ui/components/ActionCard.kt` (already an expandable card with action name, target, privacy summary
  and payload preview) via the existing `SessionPanel.addComponent` (`ChatPanel:1677`, one caller
  today at `:344`). Three reasons: the resolved card stays in the transcript as a visible record next
  to the tool result it authorised; a modal blocks the EDT for the whole decision; and the inline card
  **forces the chain continuation to become callback-driven**, which is the shape REL-05 needs. The
  ROADMAP's own ordering rationale is explicit — *"22 first: the confirmation gate changes the call's
  shape, and moving it off the EDT is cleaner once that shape is settled."* A modal would preserve the
  synchronous shape and hand Phase 23 the job of unpicking it. Rejected: modal (mirrors
  `ContextPreviewDialog.confirm`, but see above) and a per-tier split of modal/inline (two surfaces,
  two continuation shapes inside one function). **Accepted cost:** a pending-decision state to manage
  across session switch and panel close — D-08 defines its lifecycle.

- **D-07:** The card renders the parsed tool ID, the catalog title and tier **resolved by the
  extension**, and the **full args JSON** in the expandable preview. All model-supplied text renders
  as **plain text — never markdown, never as extension chrome** — with control/ANSI characters
  stripped and a length cap, following Phase 20 D-07's sanitize-and-truncate discipline for
  attacker-controlled values. An unrecognised tool name is labelled as unknown rather than shown bare.
  The reasoning is that the tool name and args are model-generated, so an injected prompt can write
  *"safe routine read"* into them; the extension-derived title and tier are the only trustworthy text
  on the card and must be visually distinguishable from the model's. Full args are shown rather than
  hidden because **the args are where exfiltration hides** — approving `http1_request` without seeing
  the request is approving a tool class, which the tier already encodes.

- **D-08:** **No timer.** A pending card waits indefinitely and resolves to **Deny** only on signals
  that the user moved on: a new message typed in that session, the session being deleted, or extension
  unload. Each of those returns the standard denial result (D-11) so the model is never left hanging
  and no continuation dangles. Switching to another session does **not** cancel it. Rejected: a
  timeout (a countdown on a security prompt either auto-approves, which is unacceptable, or silently
  stalls; and it is a new tunable with no defensible value) and wait-forever-with-no-implicit-denial
  (a deleted session or an unloaded extension leaves a dangling continuation — a shutdown-ordering
  problem Phase 24 would inherit).

- **D-09:** **No opt-out and no tier downgrade in Settings.** The gate is unconditional for
  model-originated calls; D-02's tiers and D-10's session memory are the only friction relief. The
  escape hatch already exists and is the correct one — `ToolSessionState.toolsMode` (`ChatPanel:1601`)
  lets a user stop the model calling tools at all. *"Let the model use tools without asking"* is not a
  setting this project should ship given its stated core value, and its absence keeps ADR-15 a rule
  rather than a default. Rejected: a per-tool `CONFIRM`→`AUTO` downgrade (new persisted settings
  surface, and a malicious settings JSON that flips every tool to `AUTO` becomes an import-path attack
  adjacent to the one QUAL-06 already tracks) and a warned global off-switch (it is the control's own
  bypass shipped in the box — and Unsafe Mode being on is precisely the state in which the gate
  matters most). **Accepted cost:** heavy chain users feel this with no dial to turn.

**Approve-for-session — scope and lifetime (SC2)**

- **D-10:** "Session" means the **chat session**. Memory is keyed on `sessionId` and held on the
  existing `ToolSessionState` (`ChatPanel:1601`) beside `toolsMode` and `toolCatalogSent` — no new
  lifecycle, and it dies with the session already. A new chat is a new task, so re-consent is
  proportionate. The decisive case: an approval granted while reviewing target A must not silently
  apply when the user opens a new chat about target B. Rejected: the Burp/extension session (the trust
  context that justified the approval is long gone by the time it is reused, and nothing in the UI
  reminds the user it is still in force) and the current auto-chain (barely different from
  approve-once given chains cap at 8).

- **D-11:** **Four explicit actions** on the card: `Approve once` / `Approve for session` /
  `Deny` / `Deny for session`. Deny-for-session is **not** symmetry for its own sake — it is the
  mechanism SC4's second clause needs: a later call to a session-denied tool resolves instantly with
  the standard denial result and **no card**, which bounds the retry loop structurally rather than
  leaning on the iteration counter. Explicit labels also make the transcript record unambiguous on
  re-read. `CONFIRM_EACH` tools show only `Approve once` / `Deny` — per D-02 they have no session
  memory in either direction. Rejected: two buttons plus a "remember" checkbox (a checkbox that
  silently changes what a security button means is a classic mis-click, and the transcript then has to
  reconstruct intent from two widgets) and one-off-denials-only (which makes the *user* the loop,
  clicking Deny up to 8 times per chain).

**Denial semantics and chain accounting (SC2, SC4)**

- **D-12:** Denial returns a **fixed, neutral, deterministic string** — one constant, along the lines
  of *"This tool call was not authorised by the user. Do not retry it; continue with the information
  you already have."* It is **deliberately not `Error:`-prefixed**: `maybeExecuteToolCall:2157` already
  reads that prefix as a tool failure (`status = "error"` in telemetry), and denial is a policy
  outcome, not a malfunction — conflating them tells the model something broke, which invites a retry
  with different args, and it corrupts the audit record. Determinism matters here because stable
  templates are a shipped feature. **Consequence for the plan:** the followup template at `:2176-2185`
  ends with *"Provide the final response using the tool result."* and needs a **denial variant** — it
  must not tell the model to use a tool result that does not exist. Rejected: reusing `Error:` and
  letting the user attach a free-text reason (user-authored text on a security path, breaks
  determinism, turns a two-second decision into a writing task).

- **D-13:** **A denied call decrements the iteration budget.** The counter stays monotone regardless of
  what the user clicks, so SC4's first clause — `MAX_AUTO_TOOL_ITERATIONS = 8` is respected — is a
  one-line proof with no case analysis, and SC4's second clause is satisfied by D-11's
  deny-for-session making a repeat impossible rather than merely expensive. The decisive case against
  free denials: if a denial cost nothing, injected traffic could walk the model through 60 *different*
  tools and produce 60 cards — a user-facing denial-of-service delivered **through the safety control
  itself**. Rejected: free denials, and free-but-capped-at-N (two counters and a second constant to
  justify in the ADR where one monotone counter already proves the property).

**The ADR (SC1)**

- **D-14:** One ADR, **ADR-15** in repo-root `DECISIONS.md` (ADR-14 was taken by Phase 21). It must
  record: (a) the threat model — model context contains attacker-controlled data from "Send to AI"
  proxy traffic, passive-scan findings and external MCP tool results; the model chooses tools;
  therefore tool selection is attacker-influenceable; (b) that the `[EXTERNAL-TOOL-RESULT:...]` marker
  and advisory note (ADR-11) are **mitigation, not a control**, and why; (c) D-05's `AUTO` definition
  verbatim, since that sentence is what future tools inherit; (d) that the classification is
  deliberately independent of `unsafeOnly` (D-01). Claim only what ships — Phase 21's D-08 refinement
  is the precedent for not writing an unqualified claim that is false the day it is written.

### Claude's Discretion

Two mechanisms were left to research and planning. Both carry a recommendation to be **confirmed, not
assumed** — the planner should treat them as open.

- **SC5 — making model-originated vs user-originated structurally distinguishable.**
  *Recommendation:* thread a **required, non-defaulted origin parameter** into the
  `McpToolExecutor.executeTool` path so each of the three call sites must declare which it is, and a
  future fourth call site cannot silently inherit the ungated path. Same reasoning that produced
  Phase 20 D-06 and Phase 21 D-06, and the same "no default value" argument as D-03 above. The weaker
  alternative — gate only inside `maybeExecuteToolCall` — satisfies SC5's literal text today and
  reopens the moment someone adds another parse-and-execute path. Research should confirm the
  parameter can be added without disturbing `McpToolExecutorImpl.executeTool` (`:1019`) call
  compatibility, and should check whether the origin belongs on `executeTool` or on `McpToolContext`
  (note: `McpToolContext` is constructed per call in `buildToolContext`, so it is a candidate — but it
  is also passed to MCP-server paths that have no chat origin at all, which argues for the parameter).

- **SC3 — the audit event shape.**
  *Recommendation:* reuse the existing `ActivityType.MCP_TOOL_CALL` shape already emitted at
  `ChatPanel:2158-2174` — `operation` / `status` / `traceId` / `step` / `toolName` — adding the
  decision and the tier. Per Phase 20 D-06 the event goes to **both** `AuditLogger` and the Output tab,
  because audit logging is disabled by default in this project and an audit-only signal would be
  invisible to most users. Per Phase 20 D-10 and the CLAUDE.md constraint *"hashes only unless verbose
  is on"*, any model-supplied value in the payload is **hashed by default, plaintext only under
  verbose**; the tool ID and the decision are extension-derived and can be plaintext. Research should
  decide whether an `AUTO`-tier call — which runs with no decision — emits anything beyond today's
  existing `MCP_TOOL_CALL` event; the argument for a tier field on every event is that it makes "which
  calls ran without a decision" answerable from the log.

### Deferred Ideas (OUT OF SCOPE)

- **Moving tool execution and the chain continuation off the EDT** — Phase 23 / REL-05. D-06 is
  deliberately shaped to make that easier. If this phase's work reveals an EDT problem, **record it
  for Phase 23 rather than fixing it here** (same protocol Phase 21 used).
- **Documenting the confirmation flow on the GitBook site and in the security advisory** — DOC-03,
  Phase 26. DOC-03's text already promises that `README.md`, `SPEC.md`, `DECISIONS.md` and the GitBook
  site reflect the new tool-call confirmation flow; ADR-15 in `DECISIONS.md` is this phase's share.
- **A test asserting that mutation tools are marked `unsafeOnly`** — recorded in
  `.planning/codebase/CONCERNS.md` and previously deferred by Phase 20. D-03 adds an adjacent
  invariant for the SEC-06 tier; if the two tests are cheap together, Phase 26's coverage work is the
  place to fold them.
- **Upgrading `assertEdt()` from a production no-op** — QUAL-07, Phase 26.
- **Per-tool durable trust configuration** (a persisted `CONFIRM`→`AUTO` downgrade) — rejected as D-09
  for this phase on settings-import-attack grounds. Revisit only with a story for how a malicious
  settings JSON cannot silently disable the gate.
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| SEC-06 | A tool call that the extension parsed out of **model output** does not execute against Burp without an explicit user decision. The user can approve per call, approve-for-session per tool, or deny; the decision is audit-logged. The threat model that motivates this (attacker-controlled HTTP traffic reaching model context, then steering tool selection) is recorded as an ADR so future tools inherit the rule rather than re-litigating it. | §"D-03 Verification" (the classification cannot be forgotten), §"Per-Tool Tier Assignment" (all 59 tools tiered), §"The Async Gate vs the EDT Contract" (the control-flow shape that makes the gate possible without touching REL-01), §"SC5 Origin Plumbing" (structural distinguishability), §"SC3 Audit Shape" (the audit record), §"Validation Architecture" (SC4's non-vacuous gate), §"ADR-15 Content Checklist" (the ADR). |

Success-criterion coverage map:

| SC | Where research addresses it |
|----|-----------------------------|
| SC1 (ADR) | §"ADR-15 Content Checklist", §"Pitfall 6" (the `intruder` wire-traffic claim), §"Pitfall 1" (the `AUTO` wording gap) |
| SC2 (decision surface) | §"The Async Gate vs the EDT Contract", §"Pending-Decision Lifecycle" |
| SC3 (audit) | §"SC3 Audit Shape" |
| SC4 (termination) | §"Chain Accounting", §"Validation Architecture" |
| SC5 (no double-prompt, distinguishable) | §"SC5 Origin Plumbing" |
| SC6 (tier classification) | §"Per-Tool Tier Assignment", §"D-03 Verification", §"`ext:` Tier Derivation" |
</phase_requirements>

## Project Constraints (from CLAUDE.md and AGENTS.md)

Directives the planner must verify compliance against. These carry the same authority as locked
decisions.

| Constraint | Source | Implication for this phase |
|------------|--------|----------------------------|
| **English only in code and comments** | CLAUDE.md §Constraints, AGENTS.md §Non-negotiables | The denial string, card labels, ADR text, KDoc — all English. Non-negotiable. |
| Kotlin (JVM 21), Gradle Kotlin DSL, Montoya API | CLAUDE.md §Constraints (ADR-1/2/3) | No new language, no new UI framework. |
| Audit defaults disabled, opt-in verbose, **hashes only unless verbose is on** | CLAUDE.md §Constraints | SC3: model-supplied values in the audit payload are SHA-256 hashed by default. Follow `McpBlockedRequestReporter`'s `verboseAudit` constructor seam — there is still **no user-facing verbose flag in the repo** (verified). |
| MIT licence — keep dependencies compatible | CLAUDE.md §Constraints | Moot: no dependency added. |
| Privacy modes stay user-visible and pre-flight | CLAUDE.md §Constraints (ADR-5) | The card shows the resolved privacy posture; do not add a code path that bypasses `context.redactIfNeeded`. |
| Strict layer boundary: UI / redaction / MCP / audit | AGENTS.md §Architecture constraints | Reinforces the recommendation to put tier resolution + the decision state machine in `mcp/`, not in `ui/ChatPanel.kt`. |
| Production-grade, defensive, deterministic modes | AGENTS.md §Non-negotiables | D-12's fixed denial string is the deterministic-mode-compatible choice. |
| GSD workflow enforcement (no direct edits outside a GSD command) | CLAUDE.md | Research made **zero** edits to the working tree; all measurement ran in a throwaway `git worktree` that has been removed. `git status` is clean. |

**Project skills:** none. `.claude/skills/`, `.agents/skills/`, `.cursor/skills/`, `.github/skills/` and
`.codex/skills/` do not exist in this repository (verified by `ls`).

## Architecture Patterns

### System Architecture Diagram

Data flow for a model-emitted tool call after this phase. Follow the arrows from "AI backend response"
to either "Burp API" or "denial followup".

```
 AI backend response (attacker-influenceable: contains proxy traffic,
 passive-scan findings, ext: tool results already in context)
        │
        │  onComplete(err=null) on the backend-executor thread
        ▼
 sendMessage.onComplete ──── SwingUtilities.invokeLater ───▶ [EDT boundary]
        │                                                        │
        │ (allowToolCalls && toolsMode && toolContext != null)    │
        ▼                                                        ▼
                                             maybeExecuteToolCall()  assertEdt()
                                                        │
                                       ToolCallParser.extractFirst(responseText)
                                                        │  ParsedToolCall(tool, argsJson)
                                                        ▼
                                       canonicalToolId(tool)   ← same fn the executor uses
                                                        │
                                                        ▼
                                          ┌──── SEC-06 TIER RESOLUTION ────┐
                                          │ ext:*  → CONFIRM_EACH (D-04)   │
                                          │ catalog → descriptor.secTier   │
                                          │ unknown → CONFIRM_EACH (fail   │
                                          │           closed, NEVER AUTO)  │
                                          └───────────────┬────────────────┘
                                                          ▼
                                        ┌──── SESSION MEMORY (D-10) ────┐
                                        │ approvedForSession contains?  │
                                        │ deniedForSession contains?    │
                                        │ (CONFIRM_EACH consults neither)│
                                        └───────────────┬───────────────┘
                     ┌────────────────────┬─────────────┴─────────────┬─────────────────┐
                     ▼                    ▼                           ▼                 ▼
                AUTO tier         session-approved            session-denied      needs a decision
                     │                    │                           │                 │
                     │                    │                           │       addComponent(ToolApprovalCard)
                     │                    │                           │       return AWAITING_DECISION
                     │                    │                           │                 │
                     │                    │                           │       [user clicks — ActionListener,
                     │                    │                           │        already on the EDT]
                     │                    │                           │                 │
                     │                    │                           │       Approve once / Approve for
                     │                    │                           │       session / Deny / Deny for
                     │                    │                           │       session
                     │                    │                           │        │              │
                     └────────┬───────────┘                           └────────┼──────────────┘
                              ▼                                                ▼
                    ToolCallOrigin.ModelApproved(decision)              DENIED path
                    minted ONLY by the gate                                    │
                              │                                                │
                              ▼                                                ▼
              McpToolExecutor.executeTool(name, args,                 D-12 denial constant
                  context, origin)   ── still on the EDT              (no "Error:" prefix)
                              │        (Phase 23 owns moving it)              │
                              ▼                                               │
                        Burp Montoya API                                      │
                              │                                               │
                              └──────────────┬────────────────────────────────┘
                                             ▼
                          audit: AuditLogger.emitGlobal(...) + AiRequestLogger
                          Output tab line (Phase 20 D-06: BOTH destinations)
                                             │
                                             ▼
                          followup prompt (result variant OR denial variant)
                                             │
                          sendMessage(toolIterationsLeft = n-1)   ← D-13 monotone
                                             │
                                             ▼
                                  next turn, or chain ends
```

Independent of the model path, and **not gated** (SC5):

```
 User clicks "Tools" ─▶ ToolInvocationDialog ─▶ executeTool(..., origin = UserDialog)   [:928]
 User types "/tool x {}" ─────────────────────▶ executeTool(..., origin = UserSlash)    [:2105]
 External MCP client ─▶ McpToolHandlers ──────▶ executeToolResult(...)  (no origin)     [:129]
```

### Recommended Project Structure

Additive only. `ChatPanel.kt` is 2248 lines and its split is explicitly out of scope for v0.10.0.

```
src/main/kotlin/com/six2dez/burp/aiagent/
├── mcp/
│   ├── McpToolCatalog.kt          # + `enum class SecTier`, + non-defaulted `secTier` on 59 descriptors
│   ├── ToolCallOrigin.kt          # NEW — sealed origin type; ModelApproved constructor is file-private
│   ├── ToolApprovalGate.kt        # NEW (same file as above, or same package) — AWT-FREE:
│   │                              #   tier resolution, session memory, decision state machine,
│   │                              #   denial constant, canonicalisation delegation
│   └── tools/
│       └── McpToolExecutorImpl.kt # + required `origin` param on executeTool; expose canonicalToolId
├── ui/
│   ├── ChatPanel.kt               # maybeExecuteToolCall returns a 3-valued outcome; card wiring;
│   │                              # headless guard at :377; resolvePending() called from 5 sites
│   └── components/
│       └── ToolApprovalCard.kt    # NEW — Swing card, four actions, sanitize-and-truncate rendering
└── audit/                          # unchanged; reuse AuditLogger.emitGlobal + AiRequestLogger

src/test/kotlin/com/six2dez/burp/aiagent/
├── mcp/
│   ├── SecTierResolutionTest.kt        # NEW — tier table, ext: derivation, unknown fail-closed
│   └── ToolApprovalGateTest.kt         # NEW — state machine, denial string, iteration accounting
└── ui/
    ├── ChatPanelTestHarness.kt         # NEW — shared headless ChatPanel fixture (mocks + EDT drain)
    ├── ChatPanelToolGateTest.kt        # NEW — SC2/SC4/SC5 integration; the non-vacuous SC4 gate
    └── McpToolCatalogTierParityTest.kt # NEW — every catalog tool declares a tier; AUTO set is exact
```

### Pattern 1: Three-valued continuation outcome (the async gate)
**What:** `maybeExecuteToolCall`'s `Boolean` return becomes an enum with a third state meaning "the
continuation is parked; do not invoke `onCompleted` yet".
**When to use:** any time a synchronous decision point becomes user-driven inside an existing
callback chain.
**Example:**
```kotlin
// Source: derived from the existing contract at ChatPanel.kt:660-694 (measured, this repo)
private enum class ToolCallOutcome { NOT_CHAINED, CHAINED, AWAITING_DECISION }

// caller — the ONLY change to the REL-01 block:
if (allowToolCalls && state.toolsMode && toolContext != null) {
    SwingUtilities.invokeLater {
        val outcome = maybeExecuteToolCall(/* … onCompleted = onCompleted */)
        // AWAITING_DECISION parks onCompleted inside the pending record; it is invoked
        // from the resolution callback, which Swing dispatches on this same EDT.
        if (outcome == ToolCallOutcome.NOT_CHAINED) onCompleted?.invoke(finalResp, null)
    }
} else {
    onCompleted?.invoke(finalResp, null)
}
```
Why this preserves the documented contract: the resolution callback is a Swing `ActionListener`, which
the AWT event pump dispatches **on the EDT by definition**. So the `assertEdt()` at `:2125`, the
`@GuardedBy("EDT")` map reads, and the `panel.addMessage` calls all stay on the EDT with no new
marshalling. The `onCompleted` thread choice also stays consistent with the KDoc at `:681-686`
("invoke `onCompleted` back on this EDT-scheduled block"). **Nothing about where `executeTool` runs
changes** — REL-05 is untouched.

### Pattern 2: Origin token that cannot be forged (SC5, stronger than the CONTEXT recommendation)
**What:** the `ModelApproved` origin carries proof of a decision and can only be minted by the gate.
**When to use:** when a "declare your origin" parameter would otherwise be a comment the compiler
cannot check.
**Example:**
```kotlin
// Source: pattern derived from Phase 21 D-06 "structural, not reorderable" (this repo's own precedent)
// File: mcp/ToolCallOrigin.kt — ModelApproved's constructor is FILE-PRIVATE, so only
// ToolApprovalGate (declared in this same file) can construct one.
sealed interface ToolCallOrigin {
    /** User picked the tool and typed the args in ToolInvocationDialog (ChatPanel:928). */
    data object UserDialog : ToolCallOrigin

    /** User typed `/tool <name> <json>` (ChatPanel:2105). */
    data object UserSlashCommand : ToolCallOrigin

    /** Parsed from model output AND authorised by the SEC-06 gate. Unforgeable. */
    class ModelApproved private constructor(
        val tier: SecTier,
        val decision: ToolDecision,
    ) : ToolCallOrigin {
        internal companion object {
            // Only ToolApprovalGate, in this file, can reach this.
            fun mint(tier: SecTier, decision: ToolDecision) = ModelApproved(tier, decision)
        }
    }
}
```
A hypothetical fourth parse-and-execute call site cannot compile without obtaining a `ModelApproved`
from the gate — which means going through the decision. A plain enum would let it type
`origin = ToolCallOrigin.MODEL` and silently reopen SEC-06.

### Pattern 3: Single `resolvePending` entry point for every implicit denial
**What:** one function called from every path that destroys a card or a session, rather than three
ad-hoc hooks.
**When to use:** D-08's lifecycle. There are **five** such paths, not three — see §"Pending-Decision
Lifecycle".
**Example:**
```kotlin
// Source: measured call sites in ChatPanel.kt (this repo)
private fun resolvePending(sessionId: String, reason: ImplicitDenyReason) { /* … */ }
private fun resolveAllPending(reason: ImplicitDenyReason) { /* … */ }

// Called from:
//   sendFromInput()            :448  — user typed a new message (D-08)
//   deleteSession()            :775  — session deleted (D-08)
//   clearCurrentChat()         :971  — NOT IN D-08's LIST; clearMessages() destroys the card
//   clearInMemorySessionState():1348 — NOT IN D-08's LIST; Burp project change (MainTab:819)
//   shutdown()                 :1322 — extension unload (D-08)
```

### Anti-Patterns to Avoid
- **Duplicating the alias map in the gate.** `McpToolExecutor.resolveAlias` is `private` and maps ten
  alias inputs (`history`, `proxy_history`, `requests`, `history_regex`, `proxy_history_regex`,
  `ws_history`, `websocket_history`, `websocket`, `sitemap`, `site_map_history`) onto four canonical
  IDs — all four of which are `CONFIRM` tools. A gate that skips canonicalisation labels every alias
  "unknown". Copying the map creates the two-lists-to-keep-in-sync shape D-03 explicitly rejected.
  **Expose one canonicalisation function and have both sides call it.**
- **Letting an unrecognised tool name default to `AUTO`.** D-03 removes the *authoring* default; it
  does not supply a *runtime* fallback. Unknown must resolve to `CONFIRM_EACH`.
- **Advertising the tier in the tool preamble.** `McpToolExecutor.describeTools` already emits
  `[unsafe]` and `[pro]` markers. Adding `[auto]` hands an injected prompt a map of which tools run
  silently. Do not do it; note it in ADR-15 as a deliberate omission.
- **Sending a denial followup turn on an *implicit* denial.** See §"Pending-Decision Lifecycle" — for
  three of the five paths there is no session left to continue, and for the fourth the user's own new
  message *is* the continuation.
- **Reaching for a modal.** Beyond D-06's reasons: measured, `JOptionPane.getRootFrame()` throws
  `HeadlessException` under `-Djava.awt.headless=true`, so a modal decision surface would be
  **untestable in CI**. The inline card is testable — `ActionCard` constructs and `JButton.doClick()`
  works headless. This is an additional, previously unrecorded argument for D-06.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Sanitising the model-supplied tool name and args for display | A fresh control-char regex | The `McpBlockedRequestReporter.sanitize` idiom: `Regex("[\\p{Cntrl}\\u0080-\\u009F]")` removal + whitespace collapse + length cap | Phase 20 D-07's shipped implementation. Java's `\p{Cntrl}` misses the C1 range unless `UNICODE_CHARACTER_CLASS` is set — that bug is already fixed there. |
| Hashing model-supplied values for the audit payload | New digest code | `audit/Hashing.sha256Hex(value)` | Exists, one line, already used by Phase 20's reporter and by `McpTool.runTool`'s `argsSha256`. |
| Rate-limiting repeated Output-tab lines | A timer or scheduler | `McpBlockedRequestReporter`'s lock-free read-then-CAS window idiom (or `PassiveAiScannerAnalysis.maybeLogBackoff`) | Two existing precedents, both with `nowMs` injected so windows are assertable without sleeping. |
| Deciding whether a name is an external tool | A new `"ext:"` string check | The **same** predicate the router uses, exposed once | `McpToolExecutorImpl:146` is `resolvedName.startsWith("ext:")` after `resolveAlias`. Two independent checks can disagree; one function cannot. |
| Truncating card payload preview | Custom line counter | `ActionCard.setPayloadPreview` already caps at 50 lines via `limitLines` | Existing, tested by usage. Add a **character** cap on top per D-07. |
| Test fixture for `AgentSettings` | A hand-built settings object | `TestSettings.baselineSettings()` (`src/test/kotlin/.../TestSettings.kt`) | `AgentSettings` has ~90 non-defaulted params; the helper already exists and is what every other test uses. |
| Constructing a headless `ChatPanel` | Reflection or a subclass | The harness described in §"Validation Architecture" — mockito `RETURNS_DEEP_STUBS` + one production guard | Measured working. |

**Key insight:** every mechanism this phase needs already exists in this repository in a hardened form,
because Phases 15, 16, 20 and 21 built them. The failure mode here is not "pick the wrong library" — it
is "write a second, subtly different copy of a control that already ships". D-03's rejection of a
parallel ID-keyed map is the same instinct applied to data; apply it to code too.

## Verified Findings

Every claim in this section was measured in a throwaway `git worktree` against the real Gradle build
with JDK 21. The worktree has been removed; the working tree is clean.

### 1. D-03 Verification — does a non-defaulted field actually compile-fail? **YES.**

Method: added `enum class SecTier { AUTO, CONFIRM, CONFIRM_EACH }` and a non-defaulted
`val secTier: SecTier` **in the middle** of `McpToolDescriptor` (between `category` and
`defaultEnabled`), then ran `./gradlew compileKotlin` and `compileTestKotlin`.

| Question | Measured answer |
|----------|-----------------|
| How many descriptors are there? | **59**, not 60. `grep -c 'McpToolDescriptor('` returns 60 because it counts the `data class` declaration line. `grep -c '^                id = "'` returns 59, and the compiler produced exactly **59 errors**. [VERIFIED: measured] |
| Do all catalog sites use named arguments? | Yes, all 59. Verified by reading the file end to end. No positional construction anywhere. |
| Does every site fail? | Yes — 59 × `No value passed for parameter 'secTier'.` No site compiled. [VERIFIED: measured] |
| Are there construction sites outside the catalog? | **One**: `src/test/kotlin/com/six2dez/burp/aiagent/ui/McpToolTabModelTest.kt:29-38`, a `descriptor(...)` test helper. It also uses named args and it also failed: `McpToolTabModelTest.kt:37:9 No value passed for parameter 'secTier'.` [VERIFIED: measured] |
| Other `McpToolDescriptor` references? | `ui/McpToolTabModel.kt`, `ui/SettingsPanelMcpTabs.kt`, `ui/components/ToolInvocationDialog.kt` — **type usages only**, never construction. Unaffected. [VERIFIED: grep] |
| `McpToolRegistrations`? | Holds tool-ID **strings** grouped by category, not descriptors. Unaffected by D-03. `McpToolParityTest.registeredToolIds_matchCatalog()` compares ID sets and keeps working. [VERIFIED: grep] |
| Does middle-vs-end placement matter? | Not for compile-failure — with all-named call sites both fail identically. It matters for a *hypothetical future positional* site: because `SecTier` is a distinct enum type (not `Boolean`/`String`), a positional site would produce a **type error**, not a silent argument shift. **Recommendation: use an enum type, never a Boolean or String, and place the field adjacent to `unsafeOnly` so the two axes read together.** |
| Does the mechanical diff trip a lint gate? | No. With all 59 filled in, `./gradlew ktlintCheck detekt` → `BUILD SUCCESSFUL`. `detekt.yml` sets `LongParameterList.constructorThreshold: 10`; the descriptor goes from 8 to 9 parameters, still under. [VERIFIED: measured] |

**Verdict: D-03's stated guarantee holds exactly as written.** The only correction is the count — the
plan should say **59 descriptors + 1 test helper = 60 sites**, which is probably where the "60" came
from.

### 2. SC4's Acceptance Gate — a real, non-vacuous regression test IS available

The brief asked: say concretely which new test fails against today's `maybeExecuteToolCall`. Research
found something better than a modelled test.

**Measurement A — what actually blocks a headless `ChatPanel`?** Exactly one call:

```
java.desktop/sun.awt.HeadlessToolkit.getMenuShortcutKeyMaskEx(HeadlessToolkit.java:141)
com.six2dez.burp.aiagent.ui.ChatPanel.inputPanel(ChatPanel.kt:380)
com.six2dez.burp.aiagent.ui.ChatPanel.<init>(ChatPanel.kt:261)
```

`ChatPanel.kt:377-380` reads `java.awt.Toolkit.getDefaultToolkit().menuShortcutKeyMaskEx` to build the
Cmd/Ctrl-T accelerator. That is the **sole** headless-hostile call in the whole construction path.
`CONCERNS.md` §"UI layer has no integration tests" attributes the gap to "Swing headless testing has
high setup cost" — measured, the cost is three lines.

**Measurement B — headless capability baseline** (`-Djava.awt.headless=true`, which is what
`ubuntu-latest` gives CI):

| Operation | Headless result |
|-----------|-----------------|
| `JPanel()` | OK |
| `ActionCard("a","b","c","d","e")` | **OK** — the D-06 card is constructible in CI |
| `JButton("x").doClick()` | **OK** — approve/deny clicks are simulatable in CI |
| `SwingUtilities.invokeAndWait { }` | OK — the EDT exists headless |
| `JOptionPane.getRootFrame()` | **`java.awt.HeadlessException`** — modals are *not* testable |

**Measurement C — the enabling change.** Two lines of production diff:

```kotlin
// ChatPanel.kt:377-380
val menuMask =
    runCatching {
        java.awt.Toolkit.getDefaultToolkit().menuShortcutKeyMaskEx
    }.getOrDefault(InputEvent.CTRL_DOWN_MASK)
```

plus (optional but recommended, so the test is deterministic on a developer's Mac too):

```kotlin
// build.gradle.kts:153
jvmArgs("-ea", "-Djava.awt.headless=true")
```

**Measurement D — the gate itself.** With only those changes (catalog untouched, no other production
edit), this test compiles and runs today and **passes**, proving the defect:

```kotlin
// Constructs a REAL ChatPanel headless, drives the REAL Send button, and asserts the
// model-emitted tool call reached Burp with no user decision.
val api: MontoyaApi = mock(defaultAnswer = Answers.RETURNS_DEEP_STUBS)
whenever(api.burpSuite().version().edition()).thenReturn(BurpSuiteEdition.COMMUNITY_EDITION)
val supervisor: AgentSupervisor = mock(defaultAnswer = Answers.RETURNS_DEEP_STUBS)
whenever(supervisor.requiresBurpAiAndDisabled(any())).thenReturn(false)
whenever(supervisor.sendChat(/* 13 matchers */)).thenAnswer { inv ->
    (inv.arguments[7] as (String) -> Unit)("""```json
{"tool":"scope_check","args":{"url":"http://evil.example/"}}
```""")
    (inv.arguments[8] as (Throwable?) -> Unit)(null)
    null
}
val panel = ChatPanel(api, supervisor, { TestSettings.baselineSettings() }, …)
panel.createNewSession()
// find the JTextArea and the JButton whose text == "Send" inside panel.root, then:
input.text = "check scope please"
SwingUtilities.invokeAndWait { send.doClick() }
repeat(4) { SwingUtilities.invokeAndWait { } }   // drain nested invokeLater blocks

verify(api.scope(), atLeastOnce()).isInScope("http://evil.example/")   // GREEN TODAY
```

Console output from the measured run: `PROBE3 input=true sendBtn=true` /
`PROBE3 toolReachedBurp=YES` / `BUILD SUCCESSFUL`.

**The SC4 regression test is the inversion of the last line:**

```kotlin
verify(api.scope(), never()).isInScope("http://evil.example/")   // RED today, GREEN after the fix
assertNotNull(findApprovalCard(panel.root))                      // RED today (no card exists)
```

That is a genuine, behavioural, pre-fix-red assertion against the real production path — not a
modelled stand-in. It satisfies the Phase 20 SC4 / Phase 21 standard without qualification.

**Scope judgement for the planner:** the two-line headless guard is a production change that is not
literally part of SEC-06. Phase 20's precedent is directly on point — plan `20-02` added the
`BuildFlags.VERSION` Gradle seam purely so `20-04`'s regression tests could exist. Recommend the same:
a Wave-0 plan that lands the guard plus `ChatPanelTestHarness.kt`, so every later wave can assert
against the real panel. It is additive, it does not restructure `ChatPanel.kt`, and without it SC4's
acceptance gate degrades to a modelled test.

### 3. `ext:` Tier Derivation (D-04) — stable, but the gate must canonicalise

| Question | Measured answer |
|----------|-----------------|
| Is the `ext:<server>:<tool>` format stable? | Yes. Minted in exactly one place: `ExternalMcpClientManager.kt:235`, `name = "ext:$serverName:${tool.name}"`. Consumed in exactly one place: `McpToolExecutorImpl.kt:146`, `resolvedName.startsWith("ext:")` → `routeExternalToolCall`, which splits on `":"` with `limit = 3`. [VERIFIED: grep + read] |
| Is the prefix check reachable at tier-resolution time? | Yes — but **only if the gate canonicalises first**. `executeToolResult` applies `resolveAlias(name)` *before* the `ext:` check. `resolveAlias` returns the original string for anything not in its ten-entry alias map, so for `ext:` names `resolvedName == name` and the check behaves identically. [VERIFIED: read] |
| Does `ToolCallParser` deliver a clean name? | Yes — `resolveToolName` calls `.trim()` on every candidate (`ToolCallParser.kt:81-101`), so a leading-space `" ext:a:b"` cannot slip past `startsWith`. [VERIFIED: read] |
| Case sensitivity | `{"tool":"EXT:demo:x"}` → `resolveAlias` returns it unchanged → not `ext:`-prefixed → catalog lookup fails → executor returns `Unknown tool:`. The gate, using the same function, resolves it to unknown → `CONFIRM_EACH`. Consistent; the user is prompted for a call that will fail harmlessly. Minor, worth a one-line note. |

**Consequence the plan must handle:** `resolveAlias` is `private` in `McpToolExecutor`. Expose it
(recommend `fun canonicalToolId(name: String): String`, `internal` or public) and have the gate call
it. The ten aliases all canonicalise onto `proxy_http_history`, `proxy_http_history_regex`,
`proxy_ws_history` and `site_map` — all four are `CONFIRM`. Without canonicalisation, a model writing
`{"tool":"history"}` gets a card labelled "unknown tool" for a call that executes as
`proxy_http_history`. Fail-closed on tier (unknown → `CONFIRM_EACH`) means it is not a *security* hole,
but it is a correctness and UX defect, and it corrupts the SC3 audit record's `toolName`.

Add a parity test in the shape of `McpToolParityTest`: for every alias input and for a sample `ext:`
ID, assert `gate.tierFor(x)` and the executor's routing agree — i.e. they consume the same
canonicalisation.

### 4. SC5 Origin Plumbing — CONFIRM the parameter, REFUTE `McpToolContext`

| Question | Measured answer |
|----------|-----------------|
| `executeTool` signature | `McpToolExecutorImpl.kt:1019` — `fun executeTool(name: String, argsJson: String?, context: McpToolContext): String`. A thin wrapper over `executeToolResult` that flattens `CallToolResult` to text. [VERIFIED: read] |
| Production callers of `executeTool` | Exactly **three**, all in `ChatPanel`: `:928` (dialog), `:2105` (`/tool`), `:2132` (model). [VERIFIED: grep] |
| Test callers of `executeTool` | Six: `AiGateMcpToolTest` ×3, `McpToolScopeEnforcementTest:390` (helper), `ProxyHistoryListenerPortFilterTest` ×3, `McpToolParityTest:61`. All will need to declare an origin — acceptable, and arguably desirable. [VERIFIED: grep] |
| Does adding a param disturb the MCP-server path? | **No.** `McpToolHandlers.kt:129` calls `executeToolResult`, not `executeTool`. `executeToolResult`'s signature is untouched. [VERIFIED: read] |
| Should the origin live on `McpToolContext`? | **No — refuted.** `McpToolContext` is constructed in exactly two places: `ChatPanel.buildToolContext:2234` (chat) and `McpRuntimeContextFactory.kt:31` (the MCP-server runtime). Adding a required origin field would force the MCP-server factory to declare a chat origin that has no meaning there — the exact objection CONTEXT.md anticipated, now confirmed by measurement. [VERIFIED: grep + read] |

**Recommendation: put the origin on `executeTool`, and make it a sealed type whose `ModelApproved`
variant only the gate can construct** (Pattern 2 above). The CONTEXT's enum-parameter version satisfies
SC5's letter; the sealed version satisfies the maintainer's stated standard — *"a control that a future
edit can silently undo is not a control"*. The incremental cost over an enum is roughly fifteen lines.

Two notes for the planner:
- Kotlin's `internal` is **module-wide** (the whole `main` source set), so `internal` alone does not
  bind the factory to the gate. **File-private constructor + the gate declared in the same file** does.
- The origin is not consumed inside `McpToolExecutor` — it is a declaration, not a behaviour change
  there. That is fine and intended. If the planner wants the executor to *enforce* something, the
  cheapest honest option is a KDoc contract plus the unforgeable type; do not add runtime branching to
  `executeTool` in this phase.

### 5. SC3 Audit Shape — concrete field additions

Today, `ChatPanel:2158-2174` emits to `AiRequestLogger` only:

```kotlin
supervisor.aiRequestLogger?.log(
    type = ActivityType.MCP_TOOL_CALL, source = "chat", backendId = …, sessionId = …,
    detail = "Tool ${call.tool} executed", durationMs = …,
    metadata = mapOf("operation" to "tool_chain", "status" to status, "traceId" to traceId,
                     "step" to chainStep.toString(), "toolName" to call.tool,
                     "resultChars" to result.length.toString()),
)
```

Separately, `McpTool.runTool` already emits `mcp_tool_start` / `mcp_tool_end` / `mcp_tool_blocked` to
`AuditLogger.emitGlobal` for **every** tool call including this one. So the two destinations Phase 20
D-06 requires already both exist — they are just not both carrying the SEC-06 decision.

**Recommended shape** (extends, does not invent):

| Field | Value | Plaintext or hashed | Rationale |
|-------|-------|---------------------|-----------|
| `operation` | `"tool_chain"` | plaintext | unchanged |
| `status` | `"ok"` / `"error"` / **`"denied"`** | plaintext | D-12: denial is a policy outcome, so it needs its own status rather than reusing `"error"`. This is the concrete consequence of D-12's "not `Error:`-prefixed" rule reaching the telemetry. |
| `traceId`, `step`, `toolName` | unchanged | `toolName` **plaintext after canonicalisation** | The canonical ID is extension-derived (it came from the catalog), so it is not attacker-controlled. An *uncanonicalisable* name is model-controlled — emit `"unknown"` plus `toolNameSha256`. |
| **`secTier`** | `AUTO` / `CONFIRM` / `CONFIRM_EACH` / `EXTERNAL` | plaintext | Extension-derived. **Emit on every event, including `AUTO`** — this is what makes "which calls ran without a decision" answerable from the log. Strongly recommended: without it, an `AUTO` call and a decision-less pre-fix call are indistinguishable in a historical log. |
| **`decision`** | `approve_once` / `approve_session` / `deny` / `deny_session` / `auto` / `session_approved` / `session_denied` / `implicit_deny` | plaintext | Extension-derived. Distinguishing `approve_once` from `session_approved` is what lets an auditor see whether a human clicked *for this call*. |
| **`implicitDenyReason`** | `new_message` / `session_deleted` / `chat_cleared` / `project_changed` / `unload` — only when `decision == implicit_deny` | plaintext | Extension-derived. Needed because §"Pending-Decision Lifecycle" has five sources. |
| **`argsSha256`** | `Hashing.sha256Hex(argsJson)` when args are non-blank | **hash** | Args are model-supplied and therefore attacker-influenceable. CLAUDE.md: hashes only unless verbose. Reuses the exact key name `McpTool.runTool` already uses. |
| `resultChars` | unchanged, absent on denial | plaintext | A denial has no result. |

**Destinations.** Emit the SEC-06 decision to **both**, per Phase 20 D-06:
1. `AiRequestLogger` via the existing `MCP_TOOL_CALL` call (extend its `metadata` map).
2. `AuditLogger.emitGlobal("mcp_tool_decision", payload)` — a **new type constant**, not a reuse of
   `mcp_tool_blocked`. `McpBlockedRequestReporter`'s file header records exactly why: reusing a
   constant whose payload keys have different meaning "would corrupt downstream analysis".
3. One Burp Output-tab line via `api.logging().logToOutput(...)` for the human-visible path, because
   audit is off by default. Follow `McpBlockedRequestReporter`'s "output sink is a lambda, not a
   `MontoyaApi`" convention so it stays testable.

**Verbose flag.** There is still **no user-facing verbose-audit setting anywhere in the repo** — verified
by grep; `McpBlockedRequestReporter.kt:42-45` documents this and wires `verboseAudit = false` as a
constructor seam. Do the same here. Do **not** add a settings toggle: it is not in SEC-06 and Phase 20
explicitly declined it.

**Rate limiting.** Phase 20 D-09 aggregation is **not needed here**. The flood vector there was a remote
unauthenticated peer; here the ceiling is `MAX_AUTO_TOOL_ITERATIONS = 8` per chain, and D-13 makes it
monotone. Say so explicitly in the plan rather than leaving it as an unexamined omission.

### 6. Chain Accounting (D-13 / SC4) — one hazard to resolve

D-13's decrement lands naturally: the existing handoff already passes
`toolIterationsLeft = (remainingToolIterations - 1).coerceAtLeast(0)` and
`allowToolCalls = remainingToolIterations > 1` (`ChatPanel:2186-2195`). A denial that sends the D-12
followup uses the same handoff, so the counter is monotone by construction with no extra bookkeeping —
D-13's "one-line proof" is literally true.

**The hazard:** a *session-denied* tool (D-11) resolves instantly with no card, then still sends a
followup turn. If the model re-emits the same tool each turn, the chain burns up to 7 further **backend
round-trips** — real latency and real token spend — with zero user interaction and zero cards. The chain
does terminate (SC4 clause 1 holds, and SC4 clause 2's "not in a loop" holds because no card recurs),
so this is a cost note, not a correctness defect. Record it in ADR-15 under "claim only what ships":
deny-for-session bounds the *prompting*, not the *token cost*.

### 7. Runtime Behaviour Notes (measured, for ADR-15 accuracy)

- **`intruder` and `intruder_prepare` do not put traffic on the wire.** Both call
  `api.intruder().sendToIntruder(...)`, which populates an Intruder tab; it does not launch an attack.
  D-02's *assignment* of `CONFIRM_EACH` is locked and correct on other grounds (they stage
  attacker-chosen requests for one-click launch), but D-02's *rationale sentence* — "those are the
  calls that put attacker-chosen traffic on the wire" — is accurate for `http1_request`,
  `http2_request` and the `scan_*_start` family and **not** for `intruder`/`intruder_prepare`. D-14
  says "claim only what ships". **Recommend the ADR phrase it as "put attacker-chosen traffic on the
  wire, or stage it for one click" so the sentence is true of every tool in the tier.**
- **D-10's approval memory does not survive a Burp restart.** `restoreSessions()` creates a fresh
  `ToolSessionState()` at `ChatPanel.kt:1475`, and `ToolSessionState` is in-memory only. So a chat
  session that persists across restarts comes back with **no** approvals. That is stricter than D-10
  requires and is the right behaviour — record it in ADR-15 so a future contributor does not "fix" it
  by persisting the set.
- **`assertEdt()` is a production no-op but is LIVE in tests.** `build.gradle.kts:153` passes `-ea`.
  The measured integration run exercised `maybeExecuteToolCall` with assertions on and passed, which
  confirms the `invokeLater` path really does land on the EDT in the harness. QUAL-07 still owns
  upgrading it for production.
- **`ChatPanel.kt:342`** reads `sessionStates[session.id] ?: ToolSessionState()` and discards the
  fallback instead of storing it. Harmless today (`createSession` at `:725` always populates the map
  first) but it is a latent shape that would silently lose D-10's approval set if the ordering ever
  changed. Worth a one-line fix while in the file; not a phase requirement.

## Pending-Decision Lifecycle

D-08 names three implicit-denial signals. Measured against the code, there are **five** paths that
destroy the card or the session state:

| # | Path | Line | In D-08's list? | What happens today | Required action |
|---|------|------|-----------------|--------------------|-----------------|
| 1 | `sendFromInput()` — user types a new message | `:448` | Yes | Calls `sendMessage`, starting a second concurrent turn and clobbering `inFlightConnection` | Resolve pending → Deny **before** `sendMessage`. Do **not** send a denial followup — the user's message *is* the continuation. Invoke the parked `onCompleted` and drop. |
| 2 | `deleteSession()` | `:775-800` | Yes | `sessionPanels.remove` + `sessionStates.remove` → card and state gone, `onCompleted` dangles | Resolve pending → Deny. Do **not** send a followup: `sendMessage` would bail at `:509` (`sessionPanel == null`) after side-effecting `setSendingState(true)`. |
| 3 | `clearCurrentChat()` | `:971-1004` | **NO** | `panel.clearMessages()` removes the card from the transcript; the pending record survives with **no UI to resolve it**. Permanently stuck. | **Must be added.** Resolve pending → Deny. Also decide whether to clear the D-10 approval sets (recommend **yes** — Clear Chat means "new task"). |
| 4 | `clearInMemorySessionState()` ← `MainTab.onProjectChanged():819` | `:1348` | **NO** | Clears `sessionStates`, `sessionPanels`, `sessionsById` on Burp project change. Same dangling continuation as #2, across every session at once. | **Must be added.** `resolveAllPending(PROJECT_CHANGED)`. |
| 5 | `shutdown()` | `:1322-1342` | Yes | `invokeAndWait` from a Montoya thread; cancels the in-flight connection and stops timers, but nothing resolves a pending decision | Resolve all pending → Deny. **Do not call `sendMessage`** — starting a backend request while Burp tears down the classloader is wrong. |

**The contradiction the planner must resolve.** D-08 says each implicit-denial signal "returns the
standard denial result (D-11) so the model is never left hanging". D-12 makes the denial result flow
into a **followup turn**. For paths 2–5 there is no session left to run a turn in, and for path 1 the
user has already supplied the next turn. **Recommended reading: an implicit denial resolves the card to
Deny, writes the SC3 audit event with `decision = implicit_deny`, invokes the parked `onCompleted`, and
terminates the chain — it does not send a followup.** Only an *explicit* click on `Deny` /
`Deny for session` sends the D-12 followup so the conversation can continue. This satisfies D-08's
actual goal ("no continuation dangles") without the incoherent behaviours.

**Precedent for the terminate-without-a-turn shape:** `ChatPanel.kt:321` already does exactly this when
the user cancels the context preview —
`onCompleted?.invoke("", InterruptedException("Context preview cancelled by user"))`. Reuse that idiom
(with a non-exception denial payload, since D-12 says denial is not an error).

**Also confirmed:** switching sessions does **not** destroy state (`switchToSession` only swaps the
`CardLayout` card), so D-08's "switching does not cancel it" needs no work — the card simply stays in
the other session's transcript. [VERIFIED: read]

## Per-Tool Tier Assignment

All **59** catalog tools, tiered under D-05's definition (*read-only AND bounded output — neither
mutates Burp state nor pulls bulk attacker-controlled traffic into model context*). External `ext:*`
tools derive `CONFIRM_EACH` (D-04) and are not listed.

**Totals: 19 `AUTO`, 26 `CONFIRM`, 14 `CONFIRM_EACH`.** Cross-check against the 21 `unsafeOnly` tools:
none is `AUTO`; 12 are `CONFIRM_EACH`, 9 are `CONFIRM`. Two `CONFIRM_EACH` tools are **not**
`unsafeOnly` (`ai_analyze`, `ai_passive_scan`) — which is D-01's independence claim demonstrated
concretely.

### AUTO (19)

| Tool | Justification (keyed to D-05) |
|------|-------------------------------|
| `status` | Fixed three-line extension/Burp version string. No Burp state read, no mutation, constant size. |
| `url_encode` | Pure transform of args. Output ≈ input the model already had. |
| `url_decode` | Pure transform of args. |
| `base64_encode` | Pure transform of args. |
| `base64_decode` | Pure transform of args. |
| `random_string` | Generates from args; no Burp state. |
| `hash_compute` | Pure transform of args; output is fixed-length. |
| `jwt_decode` | Pure transform of args; no signature verification, no network. |
| `decode_as` | Pure transform of args (gzip/deflate/brotli). Decompression expansion is bounded by `context.limitOutput`. |
| `scope_check` | D-05 names "scope reads". Output is literally `in_scope=true|false`. |
| `insertion_points` | Offsets computed from an args-supplied request. No Burp read. |
| `params_extract` | Parameters of an args-supplied request. The values were already in the model's context. |
| `diff_requests` | Diff of two args-supplied requests. |
| `request_parse` | Parses an args-supplied request; applies `maybeAnonymizeUrl` + `sanitizeHeaders`. |
| `response_parse` | Parses an args-supplied response. |
| `find_reflected` | Counts reflections across an args-supplied request/response pair. |
| `redact_preview` | Pure transform of args through the redaction engine. **Ambiguous — see below.** |
| `scan_task_status` | One status line for a task ID. Pro-only. **Ambiguous — see below.** |
| `ai_backends_list` | Backend IDs + current state. Small, fixed shape. **Ambiguous — see below.** |

### CONFIRM (26)

| Tool | `unsafeOnly` | Justification |
|------|:---:|---------------|
| `cookie_jar_get` | | Read-only, but enumerates every domain the user holds cookies for — an authenticated-target list. Values are `[REDACTED]` unless privacy is `OFF` *and* `includeValues` is set. |
| `proxy_http_history` | | D-05 explicit: bulk attacker-controlled traffic. |
| `proxy_http_history_regex` | | D-05 explicit. |
| `proxy_history_annotate` | ✓ | Mutates history annotations; also regex-scans bulk traffic. |
| `response_body_search` | | Regex search across proxy response bodies — bulk attacker-controlled. |
| `proxy_ws_history` | | Bulk attacker-controlled. |
| `proxy_ws_history_regex` | | Bulk attacker-controlled. |
| `site_map` | | D-05 explicit ("site-map listing"). |
| `site_map_regex` | | D-05 explicit. |
| `scope_exclude` | ✓ | Mutates the scope boundary. Narrowing scope hides traffic from scope-gated tools and from the scanner. **Ambiguous vs `CONFIRM_EACH` — see below.** |
| `scanner_issues` | | D-05 explicit ("issue listing"). Pro-only. |
| `repeater_tab` | ✓ | Mutates Burp state (creates a tab). Does **not** send traffic. |
| `repeater_tab_with_payload` | ✓ | Same, after placeholder substitution. |
| `comparer_send` | ✓ | Mutates Burp state. No wire traffic. |
| `task_engine_state` | ✓ | Pauses/resumes Burp's task engine. |
| `proxy_intercept` | ✓ | Toggles intercept — disabling it reduces the user's visibility of traffic. |
| `editor_get` | | Read-only, but returns whatever the user has open in the active editor — commonly a full request/response with credentials. Not bounded. |
| `editor_set` | ✓ | Writes model-authored text into the editor the user may then send. |
| `project_options_get` | | Read-only, but `exportProjectOptionsAsJson()` includes upstream-proxy config, session-handling rules and platform-auth material. **Ambiguous under D-05 as literally worded — see Pitfall 1.** |
| `user_options_get` | | Same for `exportUserOptionsAsJson()` — user-level proxy credentials, TLS client-cert paths. **Same ambiguity.** |
| `collaborator_generate` | | Creates a Collaborator client, registers it, returns the payload **and the secret key**. Establishes an out-of-band channel. |
| `collaborator_poll` | | Returns attacker-driven callback data including full HTTP request/response bodies when `includeHttp` is set. |
| `scan_task_delete` | ✓ | Mutates scanner state. Pro-only. |
| `issue_create` | | Writes model-authored findings into Burp's issue list — extension-native, default-enabled, not `unsafeOnly`. |
| `ai_findings_recent` | | Returns passive-scan findings containing attacker-controlled URLs and detail text. |
| `ai_audit_query` | | Returns audit entries — leaks other sessions' prompts/metadata into this session's context. |

### CONFIRM_EACH (14)

| Tool | `unsafeOnly` | Justification |
|------|:---:|---------------|
| `http1_request` | ✓ | D-02 explicit. Attacker-chosen traffic on the wire; args are the whole risk. |
| `http2_request` | ✓ | D-02 explicit. |
| `intruder` | ✓ | D-02 explicit. Stages attacker-chosen requests for one-click launch (see §7 — it does not itself send). |
| `intruder_prepare` | ✓ | Same family, explicit insertion points. |
| `scan_audit_start` | ✓ | D-02 explicit. Launches an active audit → wire traffic. Pro-only. |
| `scan_audit_start_mode` | ✓ | Same family. |
| `scan_audit_start_requests` | ✓ | Same family; args carry the requests. |
| `scan_crawl_start` | ✓ | Launches a crawl → wire traffic. |
| `scope_include` | ✓ | **Control-bypass primitive.** `McpScopeFilter.rejectIfOutOfScope` is what keeps `http1_request` / `http2_request` / `repeater_tab` / `intruder` inside scope. One session-wide approval of `scope_include` lets the model widen that boundary arbitrarily and thereby neutralise the gate protecting every traffic tool. Per-call is the only defensible tier. **Research addition — argued, not derived from D-02.** |
| `project_options_set` | ✓ | Imports arbitrary Burp configuration from model-supplied JSON: upstream proxy, TLS verification, platform auth. Args are the entire attack surface. |
| `user_options_set` | ✓ | Same at user level. |
| `scan_report` | ✓ | Writes a file to a model-chosen path. `resolveReportPath` confines it under `$HOME` (verified) but the filename and content are model-chosen — an arbitrary-write-under-home primitive whose risk lives entirely in the args. |
| `ai_analyze` | | **Not `unsafeOnly`, default-enabled, extension-native.** Sends model-authored text to the active AI backend — i.e. straight to a third-party cloud provider — and blocks up to 120s. Constant tool name, hostile args: the exact exfiltration shape D-04 cites for external tools. **Research addition — this is the single most important classification in the table.** |
| `ai_passive_scan` | | **Not `unsafeOnly`, default-enabled.** Pulls proxy history (args-selected via `siteMapUrl` / `maxRequests`) and queues it for AI analysis — bulk attacker-controlled traffic to the AI backend, plus token spend. **Research addition.** |

### Genuinely ambiguous — planner decisions, not mine

| Tool | Proposed | The ambiguity |
|------|----------|---------------|
| `project_options_get`, `user_options_get` | `CONFIRM` | D-05's sentence excludes tools that "pull bulk **attacker-controlled traffic**". These leak the **user's own secrets** — a category D-05 does not name. A literal reader could mark them `AUTO`. See Pitfall 1 for the recommended ADR wording. |
| `redact_preview` | `AUTO` | A pure function on args, so no leak. But it is a redaction-evasion oracle: an injected prompt can probe which shapes the engine catches. Weak, because the model already observes redacted output everywhere. |
| `ai_backends_list` | `AUTO` | Read-only and tiny, but it reveals which providers are configured — light local-configuration recon. |
| `scan_task_status` | `AUTO` | Tiny and bounded, but it is Pro-only scanner state and its whole family is `CONFIRM`/`CONFIRM_EACH`. Consistency argues for `CONFIRM`; the definition argues for `AUTO`. |
| `request_parse`, `response_parse`, `params_extract`, `insertion_points`, `diff_requests`, `find_reflected` | `AUTO` | The largest `AUTO` cluster (6 of 19). All are pure transforms of args — no new data enters context. But D-05's parenthetical predicts a small `AUTO` set of "decoders, schema/catalog lookups, scope reads", and this cluster is none of those three. If the planner wants `AUTO` to match that parenthetical literally, these six become `CONFIRM` and `AUTO` drops to 13. **I recommend `AUTO`** — the definition is the rule; the parenthetical is an illustration. |
| `scope_exclude` | `CONFIRM` | Symmetry with `scope_include` argues `CONFIRM_EACH`. Asymmetry argues `CONFIRM`: excluding narrows the blast radius rather than widening it. |
| `cookie_jar_get` | `CONFIRM` | Under `STRICT` it returns anonymised domains and `[REDACTED]` values, which is close to bounded and harmless. Under `OFF` with `includeValues` it dumps session cookies. Tier cannot depend on privacy mode (that would make the trust boundary mode-dependent, the same defect D-01 rejects for `unsafeOnly`), so it must be tiered for its worst case. |

## ADR-15 Content Checklist

D-14 fixes what ADR-15 must contain. Mapping each requirement to the evidence gathered here, plus the
three additions research recommends.

| D-14 requirement | Source material |
|------------------|-----------------|
| (a) Threat model: attacker-controlled data in model context (Send-to-AI proxy traffic, passive-scan findings, external MCP results); the model chooses tools; therefore tool selection is attacker-influenceable | `.planning/notes/2026-08-05-code-review.md` §F3, verbatim. §"Validation Architecture" gives a *demonstrated* instance: a model-emitted `scope_check` reaching `api.scope()` with no decision. |
| (b) `[EXTERNAL-TOOL-RESULT:...]` + advisory note is mitigation, not a control | The note lives at `McpToolExecutorImpl.kt:127-133` and is appended **only when external tools are present** — so for a session using no external MCP server, the model receives no trust-boundary instruction at all even though "Send to AI" traffic is already in its context. That is a concrete, checkable reason it is not a control. [VERIFIED: read] |
| (c) D-05's `AUTO` definition verbatim | Copy from §User Constraints above. **Plus the rider in Pitfall 1** — the definition unchanged, with worked examples that close the local-secrets gap. |
| (d) Classification deliberately independent of `unsafeOnly` | `McpToolContext.isUnsafeToolAllowed` (`McpToolContext.kt:53-57`) returns `true` for everything once `unsafeEnabled` is on — one toggle, whole gate. And the concrete demonstration: `ai_analyze` and `ai_passive_scan` are `CONFIRM_EACH` while not being `unsafeOnly` at all. [VERIFIED: read] |

**Three additions research recommends:**
1. **State that `AUTO` is enumerated, not derived.** The tier is a declared descriptor field; the
   definition guides an author, it does not compute anything at runtime. This is what stops a
   literal-reading disagreement from silently promoting a tool.
2. **State the deliberate omissions**, per D-14's "claim only what ships": (i) tiers are not advertised
   in the tool preamble, because that hands an injected prompt a map of the silent tools; (ii) a
   session-denied tool still costs up to seven further backend round-trips; (iii) approvals do not
   survive a Burp restart, and that is intentional.
3. **Fix the wire-traffic phrasing** for the `CONFIRM_EACH` tier — see §7.

## Common Pitfalls

### Pitfall 1: D-05's `AUTO` sentence admits a credential dump
**What goes wrong:** `project_options_get` and `user_options_get` are read-only, and what they return is
not "attacker-controlled traffic" — it is the user's own Burp configuration. Read literally, D-05's
definition permits them as `AUTO`, and an injected prompt then exports upstream-proxy credentials,
session-handling rules and platform-auth material to a cloud model with no prompt.
**Why it happens:** D-05 was written to close the *inbound* exfiltration path (bulk proxy history). It
reasons about the provenance of the data, not about its sensitivity.
**How to avoid:** keep D-05's sentence verbatim (D-14 requires it) and add worked examples immediately
after it in ADR-15 — e.g. *"`project_options_get` and `user_options_get` are `CONFIRM` despite being
read-only: the data is not attacker-controlled but it is the user's own credential material, and the
destination is the same third-party model."* The per-tool table is the enforcement; the examples are
what a future author reads.
**Warning signs:** anyone proposing to promote a `*_get` tool to `AUTO` on the grounds that it is
read-only.

### Pitfall 2: The gate reads a different tool name than the executor
**What goes wrong:** `{"tool":"history"}` resolves to `proxy_http_history` inside the executor but is
"unknown" to a gate that does not canonicalise. Ten alias inputs are affected. The card mislabels, the
audit `toolName` is wrong, and the tier lookup silently falls through.
**Why it happens:** `resolveAlias` is `private` in `McpToolExecutor`, so the obvious move is to copy it.
**How to avoid:** expose one canonicalisation function; both sides call it. Add a parity test in the
shape of `McpToolParityTest.registeredToolIds_matchCatalog()`.
**Warning signs:** a `when` block or a `mapOf` of aliases appearing anywhere in `ui/`.

### Pitfall 3: The `AWAITING_DECISION` state is forgotten in one of the five teardown paths
**What goes wrong:** `clearCurrentChat()` removes the card from the transcript while the pending record
survives — the decision becomes permanently unresolvable and `onCompleted` never fires. The caller that
supplied `onCompleted` (the "Send to AI" launch path) hangs.
**Why it happens:** D-08 names three signals; the code has five.
**How to avoid:** one `resolvePending` / `resolveAllPending` pair, called from all five sites. See
§"Pending-Decision Lifecycle".
**Warning signs:** a pending-decision map with only three removal sites.

### Pitfall 4: An implicit denial starts a new backend turn
**What goes wrong:** on `shutdown()`, a denial followup would dispatch a backend request while Burp is
tearing down the extension classloader. On `sendFromInput()`, it would run concurrently with the user's
own turn and clobber `inFlightConnection`.
**Why it happens:** D-12 makes denial produce a followup; D-08 says implicit denials "return the
standard denial result".
**How to avoid:** implicit denial terminates; explicit denial continues. Reuse the `ChatPanel.kt:321`
cancel idiom.
**Warning signs:** `sendMessage` being called from a teardown path.

### Pitfall 5: Reusing `status = "error"` for a denial
**What goes wrong:** D-12 already forbids the `Error:` prefix in the *result string* because
`maybeExecuteToolCall:2157` derives telemetry status from it. If the SC3 event nevertheless records
`status = "error"`, the audit log cannot distinguish a policy decision from a malfunction — the exact
corruption D-12 names.
**How to avoid:** add `"denied"` as a third status value.
**Warning signs:** an SC3 payload with only `ok` / `error`.

### Pitfall 6: An ADR sentence that is false the day it ships
**What goes wrong:** D-02's rationale says the `CONFIRM_EACH` tools "put attacker-chosen traffic on the
wire". Measured, `intruder` and `intruder_prepare` only populate an Intruder tab.
**Why it happens:** the tier grouping is right; the one-line justification over-generalises.
**How to avoid:** "…put attacker-chosen traffic on the wire, or stage it for one click". Phase 21's D-08
refinement is the precedent D-14 already cites for this exact discipline.

### Pitfall 7: Naming a new test `*ConcurrencyTest`
**What goes wrong:** `build.gradle.kts:161-171` excludes `*IntegrationTest`, `*ConcurrencyTest`,
`*BackpressureTest`, `*RestartPolicyTest` and `*SupervisionTest` when `-PexcludeHeavyTests=true` — which
is exactly what `.github/workflows/build.yml:47` passes on the PR gate. A test named
`ChatPanelToolGateIntegrationTest` **would not run on pull requests**, so the SC4 gate would be invisible
where it matters most.
**How to avoid:** name it `ChatPanelToolGateTest`. Verify with
`./gradlew test -PexcludeHeavyTests=true --tests "*ToolGate*"` that it is selected.
**Warning signs:** any new file in this phase ending in one of those five suffixes.

### Pitfall 8: `runCatching` around the headless guard swallowing a real failure
**What goes wrong:** `runCatching { … }.getOrDefault(InputEvent.CTRL_DOWN_MASK)` catches `Throwable`
broadly, so a genuine toolkit misconfiguration on a user's machine degrades silently to Ctrl.
**How to avoid:** acceptable here (the fallback is a correct accelerator on Linux/Windows), but prefer
an explicit `if (GraphicsEnvironment.isHeadless())` check plus a narrow `catch (_: HeadlessException)`
so intent is legible, and add a KDoc line naming the SC4 test as the reason the guard exists.

## Code Examples

Verified patterns from this repository.

### Sanitize an attacker-controlled value for display and logging (D-07)
```kotlin
// Source: mcp/McpBlockedRequestReporter.kt:220-233 (this repo, Phase 20 D-07)
private val controlCharRegex = Regex("[\\p{Cntrl}\\u0080-\\u009F]")  // C0 + DEL + C1
private val whitespaceRegex = Regex("\\s+")

private fun sanitize(value: String?): String? =
    value?.let { raw ->
        val cleaned = controlCharRegex.replace(raw, "")   // REMOVE, never replace (CWE-117)
            .replace(whitespaceRegex, " ")
            .trim()
        if (cleaned.length > MAX_LENGTH) cleaned.take(MAX_LENGTH).trimEnd() + "..." else cleaned
    }
```

### Hash-by-default, plaintext-under-verbose (Phase 20 D-10 + CLAUDE.md)
```kotlin
// Source: mcp/McpBlockedRequestReporter.kt:172 (this repo)
// verboseAudit is a CONSTRUCTOR SEAM wired to false — there is no user-facing verbose flag
// anywhere in the repo (verified 2026-08-13). Null in, null out: never hash the empty string,
// or "absent" becomes indistinguishable from "empty".
private fun auditValue(value: String?): String? =
    sanitize(value)?.let { if (verboseAudit) it else Hashing.sha256Hex(it) }
```

### The existing MCP_TOOL_CALL emission that SC3 extends
```kotlin
// Source: ui/ChatPanel.kt:2158-2174 (this repo) — extend metadata, do not invent a new shape
supervisor.aiRequestLogger?.log(
    type = ActivityType.MCP_TOOL_CALL,
    source = "chat",
    backendId = backendId,
    sessionId = sessionId,
    detail = "Tool ${call.tool} executed",
    durationMs = durationMs,
    metadata = mapOf(
        "operation" to "tool_chain",
        "status" to status,
        "traceId" to traceId,
        "step" to chainStep.toString(),
        "toolName" to call.tool,
        "resultChars" to result.length.toString(),
    ),
)
```

### Fail-closed classification lookup
```kotlin
// AWT-free. Same canonicalisation the executor uses, so the two agree by construction.
fun tierFor(rawToolName: String): SecTier {
    val canonical = McpToolExecutor.canonicalToolId(rawToolName)
    if (canonical.startsWith("ext:")) return SecTier.CONFIRM_EACH        // D-04
    val descriptor = McpToolCatalog.all().firstOrNull { it.id == canonical }
    // D-03 removes the AUTHORING default; this is the RUNTIME fallback it does not cover.
    return descriptor?.secTier ?: SecTier.CONFIRM_EACH                   // unknown → fail closed
}
```

### Headless `ChatPanel` harness (the SC4 enabler)
```kotlin
// Requires the ChatPanel.kt:377 guard. Measured working under -Djava.awt.headless=true.
val api: MontoyaApi = mock(defaultAnswer = Answers.RETURNS_DEEP_STUBS)
whenever(api.burpSuite().version().edition()).thenReturn(BurpSuiteEdition.COMMUNITY_EDITION)
val supervisor: AgentSupervisor = mock(defaultAnswer = Answers.RETURNS_DEEP_STUBS)
whenever(supervisor.requiresBurpAiAndDisabled(any())).thenReturn(false)

val panel = ChatPanel(
    api = api, supervisor = supervisor,
    getSettings = { TestSettings.baselineSettings() },
    applySettings = { }, validateBackend = { null }, ensureBackendReady = { true },
    showError = { }, onStatusChanged = { }, onResponseReady = { },
)
panel.createNewSession()

// Depth-first component search over panel.root finds the JTextArea and the "Send" JButton.
// SwingUtilities.invokeAndWait { send.doClick() }, then drain: repeat(4) { invokeAndWait { } }
```

## Runtime State Inventory

Not applicable — this is a feature phase, not a rename, refactor or migration. No stored data, live
service configuration, OS registration, secret name or build artifact carries a string this phase
changes.

One adjacent item worth stating explicitly so nobody looks for it: **D-10's approve/deny memory is
in-memory only and is deliberately not persisted.** `restoreSessions()` builds a fresh
`ToolSessionState()` (`ChatPanel.kt:1475`), so a chat session restored from `.burp` project data returns
with no approvals. Verified by reading the restore path. Nothing to migrate.

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| JDK 21 | Gradle 8.12.1 build (default JDK 25 breaks it) | ✓ | `/usr/libexec/java_home -v 21` resolves | none — prefix every Gradle call with `JAVA_HOME=$(/usr/libexec/java_home -v 21)` |
| Gradle wrapper | build | ✓ | 8.12.1 | — |
| `ktlint` (Gradle plugin) | lint gate | ✓ | 1.5.0 | — |
| `detekt` (Gradle plugin) | lint gate | ✓ | configured via `detekt.yml` | — |
| JUnit Jupiter | tests | ✓ | 6.0.3 | — |
| mockito-kotlin | `ChatPanel` harness | ✓ | 5.4.0 (Mockito 5 inline mock maker → final Kotlin classes mock without config; **measured**) | — |
| AWT/Swing headless EDT | `ChatPanel` harness | ✓ | JDK 21; `isHeadless=true` still provides an EDT and non-Window components (**measured**) | — |
| Network / external MCP server | none | — | — | not needed; `ext:` tier derivation is a string check |

**Missing dependencies with no fallback:** none.
**Missing dependencies with fallback:** none.

Full-suite baseline measured this session in a clean worktree: `./gradlew ktlintCheck detekt` →
`BUILD SUCCESSFUL`; `compileKotlin` / `compileTestKotlin` → green on unmodified `HEAD`.

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit Jupiter 6.0.3 + `kotlin("test")` + mockito-kotlin 5.4.0 |
| Config file | `build.gradle.kts` (`tasks.test`, lines 152-172) — `useJUnitPlatform()`, `jvmArgs("-ea")` |
| Quick run command | `JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test --tests "*ToolGate*" --tests "*SecTier*"` |
| Full suite command | `JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test` |
| Phase gate command | `JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew ktlintCheck detekt test` |
| PR-gate caveat | CI runs `./gradlew test -PexcludeHeavyTests=true`, which **excludes** `*IntegrationTest`, `*ConcurrencyTest`, `*BackpressureTest`, `*RestartPolicyTest`, `*SupervisionTest`. Do not use those suffixes — see Pitfall 7. |

### Phase Requirements → Test Map

| Req / SC | Behavior | Test Type | Automated Command | File Exists? |
|----------|----------|-----------|-------------------|-------------|
| SC1 | ADR-15 exists in `DECISIONS.md`, numbered 15, and contains D-05's `AUTO` sentence verbatim | doc assertion (string match) | `./gradlew test --tests "*DecisionsAdrTest*"` | ❌ Wave 0 (or manual review — see note) |
| SC2 | A `CONFIRM` model call adds an approval card and does **not** execute until resolved | integration (real `ChatPanel`) | `./gradlew test --tests "*ChatPanelToolGateTest*"` | ❌ Wave 0 |
| SC2 | `Approve once` executes; `Approve for session` executes and suppresses the next card for that tool; `Deny` returns the D-12 constant; `Deny for session` suppresses the next card and denies instantly | integration | same | ❌ Wave 0 |
| SC2 | Denial returns a result that lets the conversation continue (a followup turn is sent, not an error) | integration | same | ❌ Wave 0 |
| SC3 | Every decision emits `MCP_TOOL_CALL` metadata carrying `toolName`, `decision`, `secTier`, `step`; and an `AuditLogger` event; and one Output-tab line | unit (gate + reporter) + integration (metadata capture) | `./gradlew test --tests "*ToolApprovalGateTest*" --tests "*ChatPanelToolGateTest*"` | ❌ Wave 0 |
| SC3 | Model-supplied args are hashed, never plaintext, with `verboseAudit = false` | unit | `./gradlew test --tests "*ToolApprovalGateTest*"` | ❌ Wave 0 |
| SC4 | `MAX_AUTO_TOOL_ITERATIONS = 8` respected: a chain of 8 denials sends at most 8 turns and terminates | unit (iteration accounting on the AWT-free seam) | `./gradlew test --tests "*ToolApprovalGateTest*"` | ❌ Wave 0 |
| SC4 | **Acceptance gate:** a model-emitted tool call does **not** reach Burp before a decision | integration — **RED against today's code** | `./gradlew test --tests "*ChatPanelToolGateTest*"` | ❌ Wave 0 |
| SC4 | A session-denied tool produces no further cards | integration | same | ❌ Wave 0 |
| SC5 | `ToolInvocationDialog` (`:928`) and `/tool` (`:2105`) execute with no card | integration | same | ❌ Wave 0 |
| SC5 | Origin is structurally declared — a `ModelApproved` origin cannot be constructed outside the gate | compile-time (negative-compilation note in KDoc) + unit that the gate mints one | `./gradlew test --tests "*ToolApprovalGateTest*"` | ❌ Wave 0 |
| SC6 | Every one of the 59 catalog tools declares a `secTier`; the `AUTO` set is exactly the 19 enumerated | unit | `./gradlew test --tests "*McpToolCatalogTierParityTest*"` | ❌ Wave 0 |
| SC6 | `ext:server:tool` → `CONFIRM_EACH`; unknown → `CONFIRM_EACH`; all ten aliases resolve to their canonical tool's tier | unit | `./gradlew test --tests "*SecTierResolutionTest*"` | ❌ Wave 0 |
| SC6 | Gate and executor consume the **same** canonicalisation | unit (parity, shape of `McpToolParityTest`) | `./gradlew test --tests "*SecTierResolutionTest*"` | ❌ Wave 0 |

**The SC4 acceptance gate, stated concretely.** Against today's `ChatPanel.maybeExecuteToolCall`, this
assertion **fails**:

```kotlin
verify(api.scope(), never()).isInScope("http://evil.example/")
```

because — measured this session — the model-emitted call reaches `api.scope()` immediately. It passes
after the gate lands. A companion assertion, `assertNotNull(findApprovalCard(panel.root))`, also fails
today because no card exists. Neither is vacuous, neither is modelled, and both exercise the real
production path from the real Send button.

**On the SC1 doc test:** a string-matching test over `DECISIONS.md` is cheap and prevents the ADR from
silently losing D-05's verbatim sentence, but it is unusual for this repo. If the planner prefers, make
SC1 a human-verified item in `22-HUMAN-UAT.md` instead — Phases 20 and 21 both carry such files. Either
is defensible; do not leave it unassigned.

### Sampling Rate
- **Per task commit:** `JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test --tests "*ToolGate*" --tests "*SecTier*" --tests "*TierParity*"`
- **Per wave merge:** `JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew ktlintCheck detekt test`
- **Phase gate:** full suite green (669 tests as of Phase 21 close, plus this phase's additions) plus `detekt` baseline unchanged, before `/gsd-verify-work`.

### Wave 0 Gaps
- [ ] `src/main/kotlin/.../ui/ChatPanel.kt:377-380` — headless guard on `menuShortcutKeyMaskEx`. **Blocks every SC2/SC4/SC5 integration test.** Two-line production change; measured sufficient.
- [ ] `build.gradle.kts:153` — add `-Djava.awt.headless=true` to `tasks.test` `jvmArgs` so the harness behaves identically on macOS and on `ubuntu-latest`.
- [ ] `src/test/kotlin/com/six2dez/burp/aiagent/ui/ChatPanelTestHarness.kt` — shared fixture: deep-stub `MontoyaApi` + `AgentSupervisor`, a `sendChat` stub that emits a caller-supplied response, a depth-first component finder, and an EDT drain helper. Covers SC2/SC4/SC5.
- [ ] `src/test/kotlin/com/six2dez/burp/aiagent/ui/ChatPanelToolGateTest.kt` — SC2, SC4 (acceptance gate), SC5.
- [ ] `src/test/kotlin/com/six2dez/burp/aiagent/mcp/SecTierResolutionTest.kt` — SC6 resolution, `ext:` derivation, unknown fail-closed, alias parity.
- [ ] `src/test/kotlin/com/six2dez/burp/aiagent/mcp/ToolApprovalGateTest.kt` — SC2 state machine, SC3 payload, SC4 iteration accounting, D-12 denial constant. AWT-free.
- [ ] `src/test/kotlin/com/six2dez/burp/aiagent/mcp/McpToolCatalogTierParityTest.kt` — SC6 completeness; asserts the exact `AUTO` set so a future promotion is a deliberate, reviewed diff.
- [ ] Update `src/test/kotlin/com/six2dez/burp/aiagent/ui/McpToolTabModelTest.kt:29-38` — the one existing test helper that D-03 breaks.
- [ ] Framework install: **not needed** — JUnit Jupiter, mockito-kotlin and `kotlin("test")` are all present.

## Security Domain

### Applicable ASVS Categories

Scoped to a desktop JVM plugin with an embedded MCP server. Verified against the phase's actual surface.

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V1 Architecture / Threat Modelling | **yes** | ADR-15 is the artefact (SC1/D-14). This phase's whole point is that the threat model becomes a written, inheritable rule. |
| V2 Authentication | no | The gate authorises an *action* by a local human at the UI; there is no principal to authenticate. Burp's own process boundary is the trust anchor. |
| V3 Session Management | **yes (adapted)** | D-10 scopes the approval grant to the chat session and lets it die with the session; approvals are not persisted across restarts. That is session-scoped authorisation, and it is the ASVS-relevant property. |
| V4 Access Control | **yes — the core of the phase** | Fail-closed default (unknown → `CONFIRM_EACH`); no bypass shipped (D-09); the authorisation decision is not derivable from an unrelated toggle (D-01); the grant is unforgeable (Pattern 2). |
| V5 Input Validation / Output Encoding | **yes** | Tool name and args are attacker-influenceable model output. Sanitize-and-truncate before rendering (D-07) and before logging (CWE-117 log injection). Reuse `McpBlockedRequestReporter.sanitize`. |
| V6 Cryptography | **yes (narrow)** | Only `Hashing.sha256Hex` for audit values. **Never hand-roll** — the helper exists at `audit/Hashing.kt`. |
| V7 Error Handling & Logging | **yes** | Dual-destination emission (Phase 20 D-06); denial is `status = "denied"`, not `"error"` (D-12); no raw model text in the persistent record without hashing (CLAUDE.md). |
| V8 Data Protection | **yes** | The tier table *is* the data-protection control: it decides which local data (proxy history, cookie jar, Burp options) can flow to a third-party model without a human in the loop. |
| V12 Files & Resources | **yes (one tool)** | `scan_report` writes a model-chosen filename; `resolveReportPath` confines it under `$HOME` (verified). `CONFIRM_EACH` is the compensating control for the residual. |
| V14 Configuration | **yes** | D-09 deliberately ships no opt-out, and rejects a persisted per-tool downgrade specifically because a malicious settings import would become a gate bypass. |

### Known Threat Patterns for this stack

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Indirect prompt injection via proxy traffic → attacker-chosen tool selection | Elevation of Privilege | **This phase.** Human decision on every non-`AUTO` model-originated call; unforgeable approval token. |
| Data exfiltration through a read-only tool (`proxy_http_history` → cloud model) | Information Disclosure | D-05's `AUTO` definition; bulk-read tools are `CONFIRM`. |
| Exfiltration through a constant-name sink with hostile args (`ext:*`, `ai_analyze`) | Information Disclosure | `CONFIRM_EACH` — per-call, because a per-tool session grant covers every later argument set (D-04's reasoning, extended to `ai_analyze`). |
| Control-boundary widening (`scope_include` neutralises `McpScopeFilter`) | Elevation of Privilege | `CONFIRM_EACH`, argued in §"Per-Tool Tier Assignment". |
| Configuration tampering (`project_options_set` / `user_options_set` disabling TLS verification or redirecting the upstream proxy) | Tampering | `CONFIRM_EACH`; full args shown on the card (D-07). |
| Log injection via model-authored tool name / args (CWE-117) | Tampering / Repudiation | Control-char removal + whitespace collapse + length cap before either sink. |
| Audit-log flooding through the safety control itself | Denial of Service | Bounded by `MAX_AUTO_TOOL_ITERATIONS = 8` and D-13's monotone counter. Phase 20 D-09 aggregation is **not** required here (no unauthenticated remote trigger) — state this explicitly rather than omitting it. |
| Approval habituation (training the user to click through) | — (human factor) | D-01/D-05's deliberately small `AUTO` set is the wrong-way-round-sounding but correct answer: fewer, more meaningful prompts. Explicit four-label buttons (D-11) instead of a checkbox. |
| UI spoofing — model text rendered as extension chrome | Spoofing | D-07: model text is plain text, visually distinct from extension-derived title/tier; unknown tool names labelled, never shown bare. |
| Dangling authorisation continuation on teardown | Denial of Service / Repudiation | Single `resolvePending` entry point across all five teardown paths. |

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Prompt-level instruction ("treat this as untrusted data") as the defence against indirect prompt injection | Out-of-band human authorisation of the *action*, because prompt-level instructions are advisory to a model that the attacker is also instructing | Established industry position by 2024-2025; this repo's own ADR-11 already frames the marker as a boundary marker, and F3 names it "mitigation, not a control" | ADR-15's central claim. The `[EXTERNAL-TOOL-RESULT:...]` marker stays — it is useful — but it stops being counted as a control. [CITED: `.planning/notes/2026-08-05-code-review.md` §F3; `DECISIONS.md` §ADR-11] |
| One "dangerous tools" flag | Two independent axes: *capability* (`unsafeOnly` — may this tool ever run) and *trust* (`secTier` — did a human authorise this particular invocation) | This phase, D-01 | The sentence "`unsafeOnly` is a capability switch, not a trust model" is what a future contributor needs; it belongs in ADR-15. [VERIFIED: `McpToolContext.isUnsafeToolAllowed`] |
| Approve/deny as a boolean | Three tiers with an explicitly per-call top tier | This phase, D-02 | Prevents one click granting session-wide access to traffic-generating tools. |

**Deprecated / outdated within this repo:**
- Treating `Error:`-prefixed tool output as the only failure signal (`ChatPanel:2157`) — D-12 adds a
  third outcome (`denied`) that is neither success nor error.
- `.planning/codebase/CONCERNS.md` §"UI layer has no integration tests" says Swing headless testing has
  "high setup cost". **Measured false**: two production lines plus a fixture. The concern entry should
  be updated when this phase lands.

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | `intruder` / `intruder_prepare` populate an Intruder tab without launching an attack (Montoya `sendToIntruder` semantics) | §7, Pitfall 6 | Only affects one ADR sentence's wording. Tier assignment is unchanged either way. Cheap to confirm in live Burp during human UAT. |
| A2 | `exportUserOptionsAsJson()` / `exportProjectOptionsAsJson()` include upstream-proxy credentials and platform-auth material | Per-Tool Tier Assignment, Pitfall 1 | If the export is already credential-scrubbed by Burp, the Pitfall 1 argument weakens — but `CONFIRM` remains right on volume grounds alone. Confirmable by calling the tool once in live Burp. |
| A3 | `-Djava.awt.headless=true` faithfully reproduces `ubuntu-latest` CI behaviour for these Swing operations | §"SC4's Acceptance Gate" | If CI differs, the harness may fail there. Mitigation is cheap and definitive: land the Wave-0 harness in its own commit and watch the GitHub Actions run before building the rest of the phase on it. |
| A4 | Mockito's inline mock maker is active by default (Mockito 5.x) so final Kotlin classes mock without a `mockito-extensions` file | §"Standard Stack" | Measured working locally for `AgentSupervisor` and `MontoyaApi`; the risk is a CI-only difference, covered by the same A3 mitigation. |
| A5 | Adding `"denied"` as a third `status` value does not break any existing consumer of `MCP_TOOL_CALL` metadata | §"SC3 Audit Shape" | The only consumers found are the AI Activity view and `ai_audit_query`, both of which render `detail` and `type` rather than switching on `status`. Low risk; worth one grep during planning. |

Everything else in this document was measured against the running build or read directly from the
source at `HEAD` (`44610a5`).

## Open Questions

1. **Should `clearCurrentChat()` also clear D-10's approval sets?**
   - What we know: it already resets `toolCatalogSent` and `toolsMode` (`ChatPanel:994-998`), and it
     deletes the persisted messages. The user's mental model is "this chat is now empty".
   - What's unclear: D-10 ties approval lifetime to the *session*, and Clear Chat does not delete the
     session.
   - Recommendation: **clear them.** Clear Chat is the user declaring a new task, which is exactly
     D-10's stated justification for re-consent. Record the choice in ADR-15 so it is not re-litigated.

2. **Should the resolved decision be appended to `session.messages` so it survives restart and Markdown export?**
   - What we know: today `maybeExecuteToolCall` calls `panel.addMessage("Tool result: …", result)` but
     does **not** add to `session.messages` — tool results are already transient. `exportCurrentChatAsMarkdown`
     iterates `session.messages`, so neither the card nor the tool result appears in an export today.
   - What's unclear: D-06 says the card "stays in the transcript as a visible record"; that is true only
     within the live session.
   - Recommendation: **do not persist it in this phase.** Matching the existing tool-result behaviour is
     the consistent choice, and adding decisions to `session.messages` also feeds them back to the model
     as history — a new prompt-injection surface for no stated requirement. Note the limitation in
     ADR-15 under "claim only what ships": the card is a live-session record, not a durable audit trail;
     the durable record is the SC3 audit event.

3. **Is the Wave-0 headless guard inside this phase's scope?**
   - What we know: it is a two-line additive change to a file this phase already modifies, and without
     it SC4's acceptance gate degrades from a real integration test to a modelled one. Phase 20's plan
     `20-02` set the precedent by adding a Gradle seam purely to enable `20-04`'s regression tests.
   - What's unclear: strictly, it is test infrastructure, and `ChatPanel.kt` restructuring is out of
     scope for v0.10.0.
   - Recommendation: **in scope, as its own Wave-0 plan**, justified in the plan text by the Phase 20
     precedent. It is additive, not structural.

4. **Does `AUTO` include the six args-only parsers (`request_parse`, `response_parse`, `params_extract`, `insertion_points`, `diff_requests`, `find_reflected`)?**
   - What we know: they are pure transforms of text the model already holds; no new data enters context.
     Under D-05's definition they qualify.
   - What's unclear: D-05's illustrative parenthetical says `AUTO` is "decoders, schema/catalog lookups,
     scope reads", and these are none of the three. Including them makes `AUTO` 19 of 59 rather than 13.
   - Recommendation: **`AUTO`** — the definition governs, the parenthetical illustrates. But this is the
     single biggest judgement in the table and the planner should make it consciously.

5. **Does SC1 get an automated test or a human-UAT item?**
   - What we know: Phases 20 and 21 both shipped `*-HUMAN-UAT.md` files for items automation could not
     reach; no precedent exists in this repo for asserting on `DECISIONS.md` content from a test.
   - Recommendation: human-UAT item, unless the planner wants a cheap string-match guard for D-05's
     verbatim sentence specifically. Do not leave it unassigned — an ADR is the one SC that is easy to
     mark done without checking.

## Sources

### Primary (HIGH confidence — measured or read at `HEAD` 44610a5)
- **Measured builds** in a disposable `git worktree` with JDK 21: `compileKotlin`, `compileTestKotlin`,
  `ktlintCheck`, `detekt`, and four probe tests. Worktree removed; `git status` clean.
- `src/main/kotlin/com/six2dez/burp/aiagent/ui/ChatPanel.kt` — `:129-261` init, `:320-358` ActionCard
  caller, `:377-380` headless blocker, `:415-470` send path, `:472-700` `sendMessage` + REL-01 contract,
  `:707-711` `assertEdt`, `:775-812` `deleteSession`, `:891-934` `openToolDialog`, `:971-1004`
  `clearCurrentChat`, `:1191` `MAX_AUTO_TOOL_ITERATIONS`, `:1322-1356` shutdown / clear-in-memory,
  `:1403-1480` restore, `:1601-1604` `ToolSessionState`, `:1649-1718` `SessionPanel`, `:2095-2197`
  `handleToolCommand` + `maybeExecuteToolCall`, `:2229-2247` `buildToolContext`.
- `src/main/kotlin/com/six2dez/burp/aiagent/mcp/McpToolCatalog.kt` — all 59 descriptors.
- `src/main/kotlin/com/six2dez/burp/aiagent/mcp/tools/McpToolExecutorImpl.kt` — `:44` object, `:127-133`
  advisory note, `:136-158` `executeToolResult` + `ext:` routing, tool branches `:163-1012`,
  `:1019-1039` `executeTool`, `:1070-1098` `routeExternalToolCall`, `:1114-1121` `resolveAlias`.
- `src/main/kotlin/com/six2dez/burp/aiagent/mcp/tools/McpTool.kt:120-227` — `runTool` gate + telemetry.
- `src/main/kotlin/com/six2dez/burp/aiagent/mcp/McpBlockedRequestReporter.kt` — Phase 20 D-06/D-07/D-09/D-10 reference implementation.
- `src/main/kotlin/com/six2dez/burp/aiagent/mcp/McpToolContext.kt` — `:49-100` gate predicates, `limitOutput`.
- `src/main/kotlin/com/six2dez/burp/aiagent/mcp/external/ExternalMcpClientManager.kt:34-36, :235` — `ext:` namespace.
- `src/main/kotlin/com/six2dez/burp/aiagent/audit/AiRequestLogger.kt`, `audit/AuditLogger.kt`, `audit/Hashing.kt`.
- `src/main/kotlin/com/six2dez/burp/aiagent/ui/ToolCallParser.kt`, `ui/components/ActionCard.kt`,
  `ui/components/ContextPreviewDialog.kt:18-27`, `ui/McpToolTabModel.kt`.
- `src/test/kotlin/.../ui/ChatPanelConcurrencyTest.kt`, `.../ui/McpToolTabModelTest.kt`, `.../TestSettings.kt`,
  `.../mcp/tools/McpToolParityTest.kt`.
- `build.gradle.kts:140-200`, `detekt.yml`, `.github/workflows/build.yml`.
- `.planning/notes/2026-08-05-code-review.md` §F3, §F4.
- `.planning/phases/22-agent-tool-call-trust-boundary/22-CONTEXT.md`.
- `.planning/phases/20-mcp-access-control-correctness/20-CONTEXT.md` §D-06/D-07/D-09/D-10.
- `.planning/phases/21-redaction-completeness/21-CONTEXT.md` §D-06.
- `DECISIONS.md` §ADR-11, §ADR-13, §ADR-14 (**ADR-15 confirmed free**).
- `.planning/REQUIREMENTS.md` §SEC-06, `.planning/ROADMAP.md` §Phase 22, `.planning/codebase/CONCERNS.md`.
- `CLAUDE.md`, `AGENTS.md`.

### Secondary (MEDIUM confidence)
- Montoya `Intruder.sendToIntruder` semantics (tab creation, not attack launch) — inferred from the
  call-site code plus API naming; logged as assumption **A1**.
- Burp options-export contents — inferred from Burp's documented option surface; logged as **A2**.

### Tertiary (LOW confidence)
- None. No claim in this document rests on an unverified web source; nothing external was required.

## Metadata

**Confidence breakdown:**
- Standard stack: **HIGH** — no dependency is added; every tool used is pinned in `build.gradle.kts` and was exercised this session.
- D-03 verification: **HIGH** — compiled, error count matched the descriptor count exactly, test helper caught.
- SC4 acceptance gate: **HIGH** — the failing/passing behaviour was executed end to end against the real `ChatPanel`.
- Architecture (async gate, origin type, lifecycle): **HIGH** — every call site and thread-confinement claim read from source; the five teardown paths enumerated by grep.
- Per-tool tier assignment: **MEDIUM-HIGH** — 53 of 59 follow directly from D-05 plus the implementation; 6 are flagged ambiguous with the reasoning exposed, and 2 (A1/A2) rest on Montoya/Burp behaviour not executed here.
- SC3 audit shape: **HIGH** — both destinations, the hashing helper and the absent verbose flag all verified in code.
- Pitfalls: **HIGH** — every one is anchored to a specific line in this repository.

**Research date:** 2026-08-13
**Valid until:** 2026-09-12 (30 days — brownfield, no external dependency, stable pinned stack). Re-verify
immediately if `ChatPanel.kt`, `McpToolCatalog.kt` or `McpToolExecutorImpl.kt` change before planning.
