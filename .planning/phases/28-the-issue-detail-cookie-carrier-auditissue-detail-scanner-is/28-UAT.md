---
status: complete
phase: 28-the-issue-detail-cookie-carrier-auditissue-detail-scanner-is
source: [28-VERIFICATION.md]
started: 2026-08-28T11:20:00Z
updated: 2026-08-28T12:05:00Z
---

## Current Test

[testing complete]

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

result: pass
note: |
  Confirmed in a live Burp on 2026-08-28. WR-01's concern does not materialize in practice: the
  4000 ms dismiss and single-line rendering did not prevent an operator reading all three clauses to
  the end. `D-28-10` condition 3 is therefore discharged in observed behaviour, not only in code —
  which is the part the verifier could not settle by grep and the part `D-28-09`'s acceptance rests
  on. WR-01 stays on record as a latent robustness note (the string is still 206 plain chars with no
  `<html>` wrapper and no `ToolTipManager` tuning), but it is not a live defect.

### 2. Decide the scope of the tooltip's second sentence before ship

expected: A recorded decision either way — keep the blanket wording, or narrow it to the scanner
detail lines it is actually about.

why human: Operator-copy judgement (`28-REVIEW-3.md` WR-05). The sentence "Applies from now on, not
retroactively." generalises a narrow residual. `McpToolContext.redactIfNeeded` applies the CURRENT
policy to already-captured traffic at send time, so switching to STRICT *is* retroactive for the
dominant path. The wording errs pessimistic (the safe direction), and `FORWARD_ONLY_CLAUSE` pins the
exact string — so correcting it is a copy change plus a test change together, which is why round 4
declined to do it silently.

result: pass
decision: narrow
note: |
  Decided 2026-08-28 by the maintainer at this checkpoint: NARROW IT. The blanket sentence
  "Applies from now on, not retroactively." was DELETED rather than qualified.

  Why deleted and not qualified: the forward-only meaning is already carried, correctly scoped, by
  the sentence that follows it — "Scanner findings already recorded keep the values they were built
  with; re-scanning does not rewrite them." Qualifying the blanket form in place would have said
  "scanner findings" twice in a tooltip that UAT test 1 confirmed is already at its readable limit.

  Shipped in the same commit, copy and test together:
    - `SettingsPanelInit.PRIVACY_MODE_TOOLTIP` drops the sentence; a NARROWED KDoc block records why,
      naming `McpToolContext.redactIfNeeded` as the mechanism that makes the blanket form false.
    - `PrivacyModeTooltipBoundTest.FORWARD_ONLY_CLAUSE` retargeted from the blanket sentence to
      `keep the values they were built with`, so the forward-only claim stays pinned at its true
      scope; `theTooltipNamesTheSettingAsForwardOnly` renamed to
      `theTooltipNamesScannerFindingsAsForwardOnly` to match what it now asserts.
    - NEW negative pin `theTooltipDoesNotMakeAnUnscopedForwardOnlyClaim` asserts the retired blanket
      clause is ABSENT, so it cannot return as a well-meaning copy edit. Class 4 -> 5 tests.

  This closes `28-REVIEW-3.md` WR-05, which round 4 deliberately deferred rather than fixing
  silently. `D-28-10` condition 3 is unaffected — the bound is still named at the selector, and now
  named accurately.

## Summary

total: 2
passed: 2
issues: 0
pending: 0
skipped: 0
blocked: 0

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
- **WR-05** — CLOSED at this UAT, not deferred. See test 2.
- **WR-06** — `PrivacyModeTooltipBoundTest`'s source-scan needle is the literal
  `privacyMode.toolTipText`; a `setToolTipText(...)` call would be invisible to it. Latent — zero
  hits today.
- **Named follow-on (not a gap):** the residual's width for a JWT- or base64-shaped cookie value
  under STRICT's *generic* (non-cookie) rules is honestly marked UNMEASURED at every record site. It
  is measurable — a test feeding such a value through `Redaction.apply` would settle it — and it
  bears on how severe the accepted residual actually is. Belongs with the read-time-layer work
  `D-28-09` deferred to its own phase.
