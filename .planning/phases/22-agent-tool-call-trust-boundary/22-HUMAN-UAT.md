---
status: complete
phase: 22-agent-tool-call-trust-boundary
source: [22-VALIDATION.md]
started: 2026-08-14
updated: 2026-08-19
---

## Current Test

[testing complete]
## Tests

### 1. The approval card renders legibly in live Burp, in both themes

Build the JAR (`./gradlew shadowJar`), load it in Burp, open the AI Agent tab, enable tools mode in a
chat session, and send a prompt that elicits a `CONFIRM`-tier tool call — asking the agent to look at
recent proxy traffic will normally produce `proxy_http_history`. Then inspect the card that appears
inline in the transcript, once in a light theme and once in a dark theme.

expected: Four specific things, each checkable rather than a general impression.

1. **The extension's text and the model's text are distinguishable without reading carefully.** The
   card heading, the catalog title and the tier badge are written by this codebase and are
   trustworthy. The tool ID and the args JSON came from the model and an injected prompt can write
   anything into them — including the words "safe routine read". Every model-supplied string must sit
   inside a bordered, monospace, input-styled box, and nothing extension-derived may appear inside
   one. The label `AI-supplied — this extension did not write the text below:` sits outside every box.
2. **All four actions are present, reachable and labelled** — `Approve once`, `Approve for session`,
   `Deny`, `Deny for session`. On a `CONFIRM_EACH` tool (for example anything that sends a request)
   only `Approve once` and `Deny` appear; that is correct, not a rendering fault.
3. **The args preview expands and is readable.** Full args are shown deliberately: the args are where
   exfiltration hides, so approving without seeing them is approving a tool class rather than a call.
4. **The tier badge is legible in both themes.**

why_human: Interaction and visual quality are not assertable headlessly. `ToolApprovalCardTest`
asserts structure — that no `JLabel` carries HTML, that the button count matches the tier, that model
text lands in a bordered region — but it cannot judge legibility. `22-UI-SPEC.md` §T-4 states honestly
that in a light theme the background channel is **nearly invisible** (`#F5F5F5` card against `#FFFFFF`
field), so the 1 px border and the monospace font are the load-bearing signals there. Whether that is
actually enough for a user glancing at the card is precisely the judgement a human must make, and the
UI-SPEC asks for it to be reviewed on its own terms rather than as if the background did the work.

result: pass

### 2. ADR-15 is factually accurate

Read `DECISIONS.md` §ADR-15 against `22-CONTEXT.md` decision D-14's four required elements, and check
each claim against the shipped code rather than against the plan.

expected: (a) the threat model is recorded — model context contains attacker-controlled data from
Send-to-AI proxy traffic, passive-scan findings and external MCP tool results, the model chooses the
tool, therefore tool selection is attacker-influenceable; (b) the `[EXTERNAL-TOOL-RESULT:...]` marker
and its advisory note are stated to be mitigation rather than a control, with the checkable reason;
(c) D-05's `AUTO` sentence appears verbatim; (d) the independence from `unsafeOnly` is recorded with
its concrete demonstration. And, the part that matters most: **no unqualified claim that is false the
day it was written.** Every `Residual:` bullet should be true, and anything the reader knows to be a
limitation should be among them. The known ones are the token cost of deny-for-session, the
non-persistence of approvals across a Burp restart, the card being a live-session record only, the
four implicit-denial paths that leave no visible card, the unreachable compact unknown-tool string,
and tool execution still running on the EDT.

why_human: Judgement, not a string match. `DecisionsAdrTest` guards the *sentence* — that ADR-15
exists, is the highest-numbered ADR, and that its `AUTO` definition still agrees byte-for-byte with
the `SecTier` KDoc — but it cannot guard the *truth*. An ADR is the one success criterion that is easy
to mark done without checking, and Phase 21's D-08 refinement is the standing precedent that an
overclaiming record is worse than none: it tells a future reader a control exists where it does not.

result: pass

### 3. Research assumption A1 — `intruder` and `intruder_prepare` stage a tab without sending

With Unsafe Mode enabled, call `intruder` once and `intruder_prepare` once in live Burp (the `/tool`
slash command is the simplest route). Watch Proxy > HTTP history and the Logger while doing so.

expected: Both populate an Intruder tab with the supplied request and insertion points. **Neither
sends outbound traffic** — no attack is launched until the user clicks Start in Intruder.

why_human: Montoya `Intruder.sendToIntruder` runtime semantics; no automated seam observes real
outbound traffic. Scope: this affects **one sentence's wording** in ADR-15, not a tier assignment.
Both tools are `CONFIRM_EACH` on the independent ground that they stage attacker-chosen requests for
one-click launch, and that stands either way. If the assumption turns out to be wrong — if either call
does put traffic on the wire — then ADR-15's phrase "or stage it for one click" is merely redundant
rather than incorrect, and no classification changes.

result: pass

### 4. Research assumption A2 — `user_options_get` exports credential material

On a Burp instance with an **upstream proxy configured with credentials** (and ideally a TLS client
certificate and a platform-authentication entry), call `user_options_get` once and read the exported
JSON.

expected: The export contains upstream-proxy configuration including credential material, session
handling rules, and TLS client-certificate paths.

why_human: Requires a configured live Burp; the shape of the export cannot be determined from this
repo. Scope: this confirms the justification Pitfall 1 gives for classifying `project_options_get` and
`user_options_get` as `CONFIRM` despite their being read-only. If Burp turns out to scrub credentials
from the export, the *argument* weakens but the *classification does not change* — `CONFIRM` remains
correct on volume grounds alone, and ADR-15's worked example would need its wording softened rather
than its conclusion reversed. Record the outcome either way, because the worked example is what a
future tool author reads when deciding whether a new `*_get` tool may be `AUTO`.

result: pass

## Summary

total: 4
passed: 4
issues: 0
pending: 0
skipped: 0
blocked: 0

## Gaps
