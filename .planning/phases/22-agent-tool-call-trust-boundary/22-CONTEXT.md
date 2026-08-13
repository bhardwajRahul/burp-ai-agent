# Phase 22: Agent Tool-Call Trust Boundary - Context

**Gathered:** 2026-08-13
**Status:** Ready for planning

<domain>
## Phase Boundary

A tool call the extension parsed out of **model output** cannot reach Burp without an explicit user
decision, and the threat model that motivates the rule is recorded as an ADR so future tools inherit
it rather than re-litigating it. Requirement SEC-06 (Finding F3, **high**).

**The defect:** `ui/ChatPanel.kt:2114-2197` (`maybeExecuteToolCall`) takes `ToolCallParser.extractFirst`
output and calls `McpToolExecutor.executeTool` directly, then chains via `sendMessage` up to
`MAX_AUTO_TOOL_ITERATIONS = 8` (`:1191`). The only controls in the path are the per-tool toggles and
the `unsafeOnly` flag. Model context contains attacker-controlled data — proxy traffic sent via "Send
to AI", passive-scan findings, and external MCP tool results — so tool *selection* is
attacker-influenceable. The `[EXTERNAL-TOOL-RESULT:...]` marker and its advisory note
(`mcp/tools/McpToolExecutorImpl.kt:127-133`, ADR-11) are prompt-level mitigation, not a control.

**The three `executeTool` call sites, all in `ChatPanel`** — this is the whole surface:

| Line | Origin | In scope |
|---|---|---|
| `:928` | `openToolDialog` → `ToolInvocationDialog`, user picked the tool and typed the args | No — SC5 says do not double-prompt |
| `:2105` | `/tool <name> <json>` slash command, user typed it | No — user-originated |
| `:2132` | `maybeExecuteToolCall`, parsed from model output | **Yes — this is SEC-06** |

**In scope:** the SEC-06 tier classification and its required descriptor field; the decision card and
its four actions; per-chat-session approve/deny memory; denial result and chain accounting; the audit
event; structurally distinguishing model-originated from user-originated calls; ADR-15.

**Out of scope:** moving tool execution off the EDT (Phase 23 / REL-05 — **report, do not fix**, and
see D-06 for why this phase's shape change is what makes Phase 23 cleaner); the `unsafeOnly` gate and
the Unsafe Mode master switch, which stay exactly as they are and are a *capability* switch, not a
trust boundary; the GitBook site and the security advisory (DOC-03, Phase 26); the
`ChatPanel.kt` mega-file split, explicitly listed Out of Scope for v0.10.0.

</domain>

<decisions>
## Implementation Decisions

### Gate scope — which tools require a decision (SC6)

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

### Decision surface (SC2)

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

### Approve-for-session — scope and lifetime (SC2)

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

### Denial semantics and chain accounting (SC2, SC4)

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

### The ADR (SC1)

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

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### The defect and its evidence
- `.planning/notes/2026-08-05-code-review.md` §"F3" — the finding, the file:line anchors, and the
  statement that the `[EXTERNAL-TOOL-RESULT:...]` note is prompt-level mitigation rather than a
  control. **Read this first.**
- `.planning/notes/2026-08-05-code-review.md` §"F4" — the EDT half of the same code path. Phase 23
  owns it; read it to avoid designing something that makes REL-05 harder (see D-06).

### Requirements and success criteria
- `.planning/REQUIREMENTS.md` §"Access Control & Server Security (SEC)" — SEC-06.
- `.planning/ROADMAP.md` §"Phase 22" — the six success criteria, plus the ordering rationale
  explaining why 22 precedes 23 (the gate changes the call's shape). The ROADMAP's *"Open question for
  `/gsd-discuss-phase`"* is answered by D-01/D-02/D-05.

### Prior locked decisions that constrain this phase
- `DECISIONS.md` §ADR-11 — external MCP server output is untrusted and wrapped in a trust-boundary
  marker. D-04 derives from this. ADR-11 is also the "mitigation, not control" that ADR-15 must
  qualify. **ADR-15 is the next free number.**
- `.planning/phases/20-mcp-access-control-correctness/20-CONTEXT.md` §decisions D-06, D-07, D-09,
  D-10 — dual-destination logging because audit is off by default, sanitize-and-truncate for
  attacker-controlled values, aggregation-based rate limiting, and hash-by-default-plaintext-under-
  verbose. D-07 and the SC3 discretion item inherit all four.
- `.planning/phases/21-redaction-completeness/21-CONTEXT.md` §decisions D-06 — the structural-not-
  reorderable framing (delete the short-circuit rather than add a flag) that drives D-03 and the SC5
  recommendation; and the `ContextPreviewDialog.confirm` "deliberately has NO default value" comment
  that D-03 cites directly.
- `.planning/phases/16-external-mcp-client/16-CONTEXT.md` §decisions — `ext:<server>:<tool>`
  namespacing, which D-04 keys on. Do not disturb the namespace format.
- `.planning/codebase/CONCERNS.md` §"MCP unsafe-tool gate — new tools must opt in" — the opt-in
  fragility D-03 is designed to invert, and the current verified list of 21 `unsafeOnly` tools.
- `.planning/codebase/CONCERNS.md` §"UI layer has no integration tests" — `ChatPanel` has only
  `ToolCallParserTest` and `ChatPanelConcurrencyTest`. Relevant to how SC2/SC4/SC5 get tested; a Swing
  gate with no test is the same shape of gap that let SEC-04 ship green.
- `CLAUDE.md` §Constraints — audit defaults to disabled, opt-in verbose, hashes only unless verbose;
  English only in code and comments.

### Files this phase changes
- `src/main/kotlin/com/six2dez/burp/aiagent/ui/ChatPanel.kt:2114-2197` — `maybeExecuteToolCall`, the
  gate itself and the denial followup template at `:2176-2185`.
- `src/main/kotlin/com/six2dez/burp/aiagent/ui/ChatPanel.kt:1601-1604` — `ToolSessionState`, where
  D-10's per-session approve/deny memory lives.
- `src/main/kotlin/com/six2dez/burp/aiagent/ui/ChatPanel.kt:928` and `:2105` — the two user-originated
  call sites; SC5 requires these stay un-gated and become distinguishable in code.
- `src/main/kotlin/com/six2dez/burp/aiagent/mcp/McpToolCatalog.kt` — D-03's required tier field on all
  60 `McpToolDescriptor` entries.
- `src/main/kotlin/com/six2dez/burp/aiagent/ui/components/ActionCard.kt` — extend or wrap for D-06/D-07.
- `src/main/kotlin/com/six2dez/burp/aiagent/mcp/tools/McpToolExecutorImpl.kt:1019` — `executeTool`, if
  the SC5 origin parameter lands there.
- `DECISIONS.md` — ADR-15.

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `ui/components/ActionCard.kt` — an in-transcript card with action name, source, target, privacy
  summary and an **expandable payload preview**, already styled on `UiTheme`. Roughly 80% of the
  approval card D-06 needs. One existing caller (`ChatPanel:344`).
- `ChatPanel.SessionPanel.addComponent(JComponent)` (`:1677`) — inserts an arbitrary component into
  the message flow with correct vertical sizing. The inline card needs no new plumbing.
- `ui/components/ContextPreviewDialog.kt` — the project's existing approve-before-send gate. **Not**
  the chosen surface (D-06), but its discipline is the model: extension-controlled chrome, an explicit
  privacy statement, and a parameter with no default value.
- `ChatPanel:2158-2174` — the `ActivityType.MCP_TOOL_CALL` telemetry call with
  `operation` / `status` / `traceId` / `step` / `toolName`. SC3 extends this shape, does not invent one.
- `ToolSessionState` (`:1601`) — the existing per-`sessionId` mutable holder. D-10's memory belongs
  here.
- `McpToolCatalog.unsafeToolIds()` and `McpToolContext.isUnsafeToolAllowed` — the existing gate. Leave
  untouched; the SEC-06 tier is a **second, independent** axis (D-01).

### Established Patterns
- `maybeExecuteToolCall` is EDT-confined by an `assertEdt()` (`:2125`) whose KDoc notes the assert is a
  no-op in production Burp — QUAL-07 already tracks that. The continuation is scheduled from a backend
  thread via `SwingUtilities.invokeLater` at `:667`. D-06's async continuation must preserve the
  documented REL-01 confinement contract.
- Blocked/denied paths in this codebase fail **closed** and return a typed result rather than
  throwing (Phase 20 §code_context). D-12's denial string follows that.
- Security-relevant classifications live next to the thing they classify and are enforced by a test —
  `McpToolParityTest.registeredToolIds_matchCatalog()` is the existing shape.
- Hand-rolled Swing on `UiTheme` / `ui/design` tokens; no new UI framework.

### Integration Points
- `ToolCallParser.extractFirst` (`ui/ToolCallParser.kt:20`) returns `ParsedToolCall(tool, argsJson)`
  where **`tool` is whatever string the model wrote** — it is not validated against the catalog. D-07's
  "unrecognised tool name is labelled as unknown" lands here.
- `buildToolContext` (`:2229`) constructs a fresh `McpToolContext` per call, carrying `toolToggles`,
  `unsafeEnabled`, `unsafeTools`, `enabledUnsafeTools`. Relevant to the SC5 discretion item.
- `sendMessage(..., allowToolCalls, toolIterationsLeft, traceId, onCompleted)` (`:479`) is the chain
  driver. D-13's decrement and D-12's denial followup both land in the `maybeExecuteToolCall` →
  `sendMessage` handoff.
- `mcp/external/ExternalMcpClientManager.kt:35-36` — `ext:` namespacing and the trust-boundary marker
  constants that D-04 keys on.

</code_context>

<specifics>
## Specific Ideas

- The maintainer's framing across Phases 20, 21 and now 22 is consistent and should drive every
  discretionary call: **a fix that only satisfies the unit test while the real path stays open is not
  a fix**, and a control that a future edit can silently undo is not a control. That produced "delete
  the short-circuit" over "add a flag" in Phase 21, "`ApplicationPlugin` over move-the-line" in Phase
  20, and here it produces D-03's non-defaulted field and the SC5 origin-parameter recommendation.
- **`unsafeOnly` is a capability switch, not a trust model.** This sentence is the crux of D-01 and
  belongs in ADR-15 — it is the thing a future contributor will otherwise get wrong when they ask
  "why are there two classifications?"
- **The gate must not become a habituation trainer.** Every rejected option that produced more dialogs
  was rejected partly on this ground. It is also the reason D-05 accepts a small `AUTO` set rather
  than a large one: fewer, more meaningful prompts beat many cheap ones.
- The card is a **record**, not just a prompt. It stays in the transcript after resolution, which is
  why D-11 chose four explicit labels over a checkbox — the transcript should read unambiguously
  months later.
- SC4's acceptance gate deserves the Phase 20 SC4 / Phase 21 treatment: **a test that passes both
  before and after the change has not tested the defect.** A regression test must fail against
  today's `maybeExecuteToolCall`.
- Note for the planner: `ChatPanel.kt` is 2248 lines and its split is explicitly Out of Scope for
  v0.10.0 because Phases 22 and 23 touch it heavily. Add code here; do not restructure the file.

</specifics>

<deferred>
## Deferred Ideas

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

</deferred>

---

*Phase: 22-agent-tool-call-trust-boundary*
*Context gathered: 2026-08-13*
