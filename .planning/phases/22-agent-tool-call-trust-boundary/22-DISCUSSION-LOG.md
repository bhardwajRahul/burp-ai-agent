# Phase 22: Agent Tool-Call Trust Boundary - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-08-13
**Phase:** 22-agent-tool-call-trust-boundary
**Areas discussed:** Gate scope, Decision surface, Approve-for-session lifetime, Denial semantics

The maintainer initially selected Gate scope and Decision surface, then pulled the two deferred areas
(Approve-for-session lifetime, Denial semantics) back in rather than leaving them as recommendations.

---

## Gate scope

### Which model-emitted tool calls require a user decision?

| Option | Description | Selected |
|--------|-------------|----------|
| New SEC-06 classification | Own risk tiers independent of `unsafeOnly`; cannot be widened by the Unsafe Mode switch. What SC6 literally asks for. | ✓ |
| Every model-emitted call prompts | No classification to get wrong; approve-for-session limits friction. Cost: up to 8 dialogs per fresh chain, habituated clicking. | |
| Only the 21 `unsafeOnly` tools | Reuses the existing classification. Cost: near-no-op with Unsafe Mode off; leaves read-only recon/exfil ungated. | |

**User's choice:** New SEC-06 classification
**Notes:** Presented with the code finding that `isUnsafeToolAllowed` returns `true` for everything
once `unsafeEnabled` is on, and that with Unsafe Mode off those 21 tools are already blocked — so
`unsafeOnly` is a capability switch, not a trust model.

### How many tiers?

| Option | Description | Selected |
|--------|-------------|----------|
| Three tiers | `AUTO` / `CONFIRM` (session-approvable) / `CONFIRM_EACH` (per-call only). Stops one click granting session-wide outbound HTTP. | ✓ |
| Two tiers | `AUTO` vs `CONFIRM`. Simplest taxonomy. Cost: approve-for-session becomes all-or-nothing. | |
| You decide | Delegate to research, with the outcome recorded in ADR-15. | |

**User's choice:** Three tiers

### Where does the tier live, and what if a tool omits it?

| Option | Description | Selected |
|--------|-------------|----------|
| Required field, no default | Non-defaulted field on `McpToolDescriptor`; all 60 declare or the build fails. Cites the Phase 21 `ContextPreviewDialog.confirm` "deliberately has NO default value" precedent. | ✓ |
| Field defaulting to `CONFIRM_EACH` | Forgetting is safe, smaller diff. Cost: does not force the author to think; tools drift into the noisiest tier. | |
| Separate map keyed by tool ID | Classification in one readable place. Cost: two lists to sync by hand — the shape Phase 21 rejected. | |

**User's choice:** Required field, no default
**Notes:** Accepted cost is a wide mechanical diff across `McpToolCatalog.kt`. Also inverts the
fragility recorded in CONCERNS.md §"MCP unsafe-tool gate — new tools must opt in".

### How are external MCP tools (`ext:<server>:<tool>`) classified?

| Option | Description | Selected |
|--------|-------------|----------|
| `ext:` prefix → `CONFIRM_EACH` | Derived from the ID, cannot desync; justified by ADR-11's own untrusted-server framing; catches the constant-name/hostile-args exfil shape. | ✓ |
| `ext:` prefix → `CONFIRM` | Session-approvable since the user registered the server. Cost: one approval covers all later args. | |
| External tools out of scope | Built-in tools only; ADR-11's marker stays the sole control. | |

**User's choice:** `ext:` prefix → `CONFIRM_EACH`
**Notes:** Accepted friction for heavy external-MCP users.

### What qualifies a tool for `AUTO`?

| Option | Description | Selected |
|--------|-------------|----------|
| Read-only AND bounded output | Neither mutates Burp state nor pulls bulk traffic into model context. `proxy_history`, site map, issue listing become `CONFIRM`. | ✓ |
| Read-only is enough | Larger `AUTO` set, quieter chains. Cost: the exfiltration path SEC-06 exists for stays fully automatic. | |
| Read-only AND local-only | Reasons about where the tool reaches. Cost: data always goes to the AI backend, so it misses the leak direction. | |

**User's choice:** Read-only AND bounded output
**Notes:** Asked as an extra question after the maintainer chose to continue on this area rather than
move on — flagged as the sentence ADR-15 has to contain.

---

## Decision surface

### How is the decision presented?

| Option | Description | Selected |
|--------|-------------|----------|
| Inline card in the transcript | Reuses `ActionCard` + `SessionPanel.addComponent`; stays in the transcript as a record; forces the callback-driven shape REL-05 needs. | ✓ |
| Modal dialog | Mirrors `ContextPreviewDialog.confirm`, cannot be ignored. Cost: keeps the synchronous EDT-blocking shape Phase 23 must unpick. | |
| Modal for `CONFIRM_EACH`, inline for `CONFIRM` | Matches severity to friction. Cost: two surfaces, two continuation shapes in one function. | |

**User's choice:** Inline card in the transcript
**Notes:** Options were re-grounded mid-question after finding `SessionPanel.addComponent` and
`ActionCard` already exist, which made inline far cheaper than first assumed. The ROADMAP's own
ordering rationale ("22 first: the confirmation gate changes the call's shape") argued the same way.

### What does the card render, given tool name and args are model-generated?

| Option | Description | Selected |
|--------|-------------|----------|
| Full args, plain text, sanitized | Extension-derived title/tier + full args JSON; model text never rendered as markdown or chrome; control chars stripped, length capped (Phase 20 D-07). | ✓ |
| Summary line, args behind a click | Less noise across a chain. Cost: default view hides the field where a hostile call differs from a benign one. | |
| Tool identity only | Impossible to spoof via args. Cost: the decision reduces to the tool class, which the tier already encodes. | |

**User's choice:** Full args, plain text, sanitized

### What happens to a pending card the user never answers?

| Option | Description | Selected |
|--------|-------------|----------|
| No timer; deny on "user moved on" | Waits indefinitely; denies on new message in that session, session deletion, or extension unload. Session switching does not cancel. | ✓ |
| Auto-deny after a timeout | Guarantees no parked chain. Cost: a tunable with no defensible value; kills chains while the user reads elsewhere. | |
| Wait forever, explicit click only | Simplest state machine. Cost: dangling continuation on session delete / unload — a Phase 24 shutdown problem. | |

**User's choice:** No timer; deny on "user moved on"

### Should Settings offer an opt-out or tier downgrade?

| Option | Description | Selected |
|--------|-------------|----------|
| No opt-out | Gate is unconditional; `ToolSessionState.toolsMode` is already the correct escape hatch. Keeps ADR-15 a rule, not a default. | ✓ |
| Per-tool downgrade, `CONFIRM_EACH` locked | Durable trust for specific tools. Cost: a malicious settings JSON flipping everything to `AUTO` becomes an import-path attack. | |
| Global off-switch, warned like Unsafe Mode | Honest and familiar. Cost: it is the control's own bypass, shipped in the box. | |

**User's choice:** No opt-out
**Notes:** Accepted cost — heavy chain users feel this with no dial to turn.

---

## Approve-for-session lifetime

### What is "the session"?

| Option | Description | Selected |
|--------|-------------|----------|
| The chat session | Keyed on `sessionId`, held on the existing `ToolSessionState`; dies with the session. Prevents target-A approval applying to target B. | ✓ |
| The Burp/extension session | Lowest friction across a working day. Cost: trust context is long gone when reused, with no UI reminder. | |
| The current auto-chain only | Tightest scope. Cost: barely different from approve-once given the 8-iteration cap. | |

**User's choice:** The chat session

### Can a denial be remembered too?

| Option | Description | Selected |
|--------|-------------|----------|
| Four explicit actions | Approve once / Approve for session / Deny / Deny for session. Session-denied tools resolve with no card, bounding the retry loop structurally. | ✓ |
| Two buttons plus a "remember" checkbox | Same four outcomes, less visual weight. Cost: a checkbox that changes a security button's meaning is a classic mis-click. | |
| Denial is always one-off | Simplest card. Cost: the user becomes the loop, clicking Deny up to 8 times per chain. | |

**User's choice:** Four explicit actions
**Notes:** Framed as the mechanism SC4's second clause needs, not as symmetry for its own sake.

---

## Denial semantics

### What result goes back to the model on denial?

| Option | Description | Selected |
|--------|-------------|----------|
| Fixed neutral string, no `Error:` prefix | Deterministic constant instructing no retry. `Error:` is already read as tool failure at `ChatPanel:2157`; denial is policy, not malfunction. | ✓ |
| Reuse the `Error:` prefix | Flows through existing telemetry unchanged. Cost: tells the model something broke, inviting a retry with different args. | |
| Let the user attach a reason | Most informative. Cost: user text on a security path, breaks determinism, turns a click into a writing task. | |

**User's choice:** Fixed neutral string, no `Error:` prefix
**Notes:** Carries a plan consequence — the followup template at `ChatPanel:2176-2185` needs a denial
variant that does not say "using the tool result".

### Does a denied call consume one of the 8 chain iterations?

| Option | Description | Selected |
|--------|-------------|----------|
| Yes, denial decrements | Counter stays monotone, so SC4's first clause is a one-line proof; deny-for-session handles the second. Stops a 60-different-tools card walk. | ✓ |
| No, denials are free | An early cautious Deny does not cost the rest of the chain. Cost: bounded only by how many tools exist; the card queue becomes the attack surface. | |
| Free, but capped at N per chain | Keeps legitimate work intact and still bounds the walk. Cost: two counters and a second constant to justify in the ADR. | |

**User's choice:** Yes, denial decrements

---

## Claude's Discretion

Two mechanisms were left open with recommendations for research to confirm rather than assume:

- **SC5 — distinguishing model-originated from user-originated calls.** Recommended: a required,
  non-defaulted origin parameter threaded into the `McpToolExecutor.executeTool` path, so a future
  fourth call site cannot silently inherit the ungated path. Same reasoning as Phase 20 D-06 and
  Phase 21 D-06.
- **SC3 — the audit event shape.** Recommended: extend the existing `ActivityType.MCP_TOOL_CALL`
  payload with the decision and tier; both `AuditLogger` and Output tab per Phase 20 D-06; model-supplied
  values hashed unless verbose per Phase 20 D-10 and the CLAUDE.md constraint.

The maintainer was offered the chance to lock the SC5 mechanism directly and chose to leave it as a
recommendation.

## Deferred Ideas

- Moving tool execution and the chain continuation off the EDT — Phase 23 / REL-05.
- Documenting the confirmation flow on the GitBook site and in the security advisory — DOC-03, Phase 26.
- A test asserting mutation tools are marked `unsafeOnly` — CONCERNS.md, previously deferred by Phase 20.
- Upgrading `assertEdt()` from a production no-op — QUAL-07, Phase 26.
- Per-tool durable trust configuration (persisted `CONFIRM`→`AUTO` downgrade) — rejected as D-09;
  revisit only with a settings-import threat story.

No scope creep was raised during this discussion — every thread stayed inside SEC-06's boundary.
