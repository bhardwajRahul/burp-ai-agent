---
status: testing
phase: 28-the-issue-detail-cookie-carrier-auditissue-detail-scanner-is
source: [28-VERIFICATION.md]
started: 2026-08-28T11:20:00Z
updated: 2026-08-28T11:20:00Z
---

## Current Test

number: 1
name: The D-28-10 bound is actually readable at the privacy-mode selector
expected: |
  In a live Burp, open Settings and hover the privacy-mode selector. Read the tooltip to its end
  without moving the pointer. All three clauses are legible, including the two the maintainer's
  acceptance is conditioned on:
    - "Applies from now on, not retroactively."
    - "Scanner findings already recorded keep the values they were built with; re-scanning does not
       rewrite them."
awaiting: user response

## Tests

### 1. The `D-28-10` bound is actually readable at the privacy-mode selector

expected: All three clauses of `PRIVACY_MODE_TOOLTIP` legible on hover, to the end of the string,
without the tooltip dismissing first.

why human: Visual/widget behaviour that no grep settles, and `D-28-09`'s acceptance rests on it.
Measured at source by the verifier: the constant is 206 plain characters with no `<html>` wrapper,
and `grep -rn 'ToolTipManager'` over `ui/` returns nothing — so Swing's default 4000 ms dismiss and
single-line rendering apply. The string is present, wired to the only `privacyMode.toolTipText`
assignment in `src/main`, and pinned 4/4 by `PrivacyModeTooltipBoundTest`. What is unverified is
whether an operator can READ it. This is `28-REVIEW-3.md` WR-01.

if the answer is no: a persistent `privacyNotice` (`SubtleNotice`) already sits above the same
control in `PrivacyConfigPanel` NORTH, refreshed by `refreshPrivacyNotice()` on every mode change.
Moving the two conditioned clauses there is the known remedy.

result: [pending]

### 2. Decide the scope of the tooltip's second sentence before ship

expected: A recorded decision either way — keep the blanket wording, or narrow it to the scanner
detail lines it is actually about.

why human: Operator-copy judgement (`28-REVIEW-3.md` WR-05). The sentence "Applies from now on, not
retroactively." generalises a narrow residual. `McpToolContext.redactIfNeeded` applies the CURRENT
policy to already-captured traffic at send time, so switching to STRICT *is* retroactive for the
dominant path. The wording errs pessimistic (the safe direction), and `FORWARD_ONLY_CLAUSE` pins the
exact string — so correcting it is a copy change plus a test change together, which is why round 4
declined to do it silently.

result: [pending]

## Summary

2 human-verification items, both landing on `PRIVACY_MODE_TOOLTIP` — the artifact that discharges
`D-28-10` condition 3. Neither is a gap: the verifier scored the phase 6/6 with no gaps. They are the
one part of the maintainer's own acceptance conditions that only a human at a running Burp can close.

Everything else in the phase is verified by measurement. See `28-VERIFICATION.md`.

## Deferred, owned, and NOT part of this UAT

Recorded in `28-09-SUMMARY.md` with reasons, carried here so they are not lost:

- **WR-04** — `theRouteTwoGateIsFailOpenForTheseCookieCapableTypes` calls a pre-redaction string
  (`detailFor`) the residual's "OBSERVABLE width", while the file's SC1 assertions deliberately use
  `redactedDetailFor`. One helper call from being true. That file was outside plan 28-09's
  `files_modified`, and swapping the helper changes what the pinned residual measures.
- **WR-06** — `PrivacyModeTooltipBoundTest`'s source-scan needle is the literal
  `privacyMode.toolTipText`; a `setToolTipText(...)` call would be invisible to it. Latent — zero
  hits today.
- **Named follow-on (not a gap):** the residual's width for a JWT- or base64-shaped cookie value
  under STRICT's *generic* (non-cookie) rules is honestly marked UNMEASURED at every record site. It
  is measurable — a test feeding such a value through `Redaction.apply` would settle it — and it
  bears on how severe the accepted residual actually is. Belongs with the read-time-layer work
  `D-28-09` deferred to its own phase.
