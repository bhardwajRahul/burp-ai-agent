---
phase: 28
slug: the-issue-detail-cookie-carrier-auditissue-detail-scanner-is
status: verified
# threats_open = count of OPEN threats at or above workflow.security_block_on severity (the blocking gate)
threats_open: 0
asvs_level: 1
block_on: high
register_authored_at_plan_time: true
created: 2026-08-28
---

# Phase 28 — Security

> Per-phase security contract: threat register, accepted risks, and audit trail.

**Input state:** B (no prior SECURITY.md; 9 PLAN.md and 9 SUMMARY.md present).
**Register origin:** authored at plan time. 8 of 9 plans carry a parseable `<threat_model>` block —
see the honesty note on `28-09` under *Register Completeness* below.
**Verification depth:** ASVS L1 (grep-depth), the configured level. Per the `secure-phase` short-circuit
rule, `threats_open: 0` + `register_authored_at_plan_time: true` + `asvs_level == 1` means no deeper
auditor pass is required. **That is a statement about the configured depth, not a claim that every
mitigation was traced end-to-end.**

---

## Trust Boundaries

| Boundary | Description | Data Crossing |
|----------|-------------|---------------|
| Burp scanner → `AuditIssue.detail()` | Issue construction bakes a rendered detail string into an immutable Burp-stored object | Injection-point `baseValue()` / rendered payload — may be a cookie value |
| `AuditIssue.detail()` → `scanner_issues` MCP tool | Stored issue replayed to an MCP client | The already-baked detail string |
| `McpTool.execute()` → MCP client | Every tool result passes `McpToolContext.redactIfNeeded` → `Redaction.apply` under the CURRENT privacy mode | Serialized tool output |
| Operator → privacy-mode selector | Operator states a redaction intent | `PRIVACY_MODE_TOOLTIP` copy (outbound understanding, not data) |

---

## Threat Register

`block_on: high`. Only OPEN threats at or above `high` count toward `threats_open`.

| Threat ID | Category | Component | Severity | Disposition | Mitigation | Status |
|-----------|----------|-----------|----------|-------------|------------|--------|
| T-28-01 | Information Disclosure | `ActiveAiScanner.createConfirmedIssue` → `scanner_issues` | medium | mitigate | Type-keyed gate in `ScannerIssueSupport.sanitizeInjectionPointValue` | closed |
| T-28-02 | Tampering | the type gate itself | medium | mitigate | Enum-member key ⇒ rename is a compile error | closed |
| T-28-03 | Information Disclosure | `IssueDetailCookieCarrierTest` fixture | medium | mitigate | `theSentinelIsNotTheTailOfTheSerializedBlob_nonVacuity` | closed |
| T-28-04a | Tampering | content-destruction guard scope | medium | mitigate | Equality scoped to the `detail` field; extractor pinned against vacuity | closed |
| T-28-04 | Denial of Service | operator's local evidence | low | accept | Raw value retained in the request pane; `theRequestResponsesListIsNotAlteredByTheControl` | closed |
| T-28-05 | Spoofing | sentinel attribution | medium | mitigate | `urlParamOriginalValueSurvivesStrict_attributionControl` | closed |
| T-28-06 | Tampering | predicate conversion | medium | mitigate | `InjectionPointExtractorTest` 12/12 green, zero edits | closed |
| T-28-07 | Information Disclosure | double redaction of consumer 1 | medium | mitigate | Route-specific control (D-28-02); `AdaptivePayloadEngine.kt` byte-unchanged vs `ad2ca90` | closed |
| T-28-08 | Repudiation | stale prose asserting the deferral | medium | mitigate | All six sites amended in one commit | closed |
| T-28-09 | Tampering | stale positive fixture | low | mitigate | `PARAMETER_LIST` fixture updated; accessor regex re-confirmed | closed |
| T-28-10 | Information Disclosure | predicate count drift | medium | mitigate | Count derived from the tree over comment-stripped lines | closed |
| T-28-11 | Information Disclosure | `ResponseAnalyzer` evidence tail | medium | transfer | Filed as `AR-28-01` in `26-SECURITY.md` (3 references), owner + venue named | closed |
| T-28-12 | Repudiation | the "carrier is closed" reading | high | mitigate | `SCOPE: ONE LINE, NOT THE BLOB` sentinel present ×2 in `26-SECURITY.md` | closed |
| T-28-13 | Tampering | `REQUIREMENTS.md` side-effect write | high | mitigate | sha256 `9b321966…fcfb4`; PRIV-05 line 23 still `- [ ]`; gate run twice | closed |
| T-28-14 | Tampering | `threats_open` counter | medium | mitigate | Recomputed by the register's own awk: 46 rows, 46 closed, open=0 | closed |
| T-28-15 | Repudiation | append-never-rewrite | high | mitigate | Byte-exact prefix digests re-asserted (see T-28-42) | closed |
| T-28-16 | Spoofing | derived vs dispositioned severity | medium | mitigate | Distinction mandatory in the row text | closed |
| T-28-17 | Information Disclosure | `buildActiveIssueDetailLines:121` (`Payload Used:`) | high | mitigate | `sanitizeRenderedPayload` type-keyed strip; held end-to-end through `Redaction.apply` | closed |
| T-28-18 | Tampering | `IssueDetailCookieCarrierTest` `PAYLOAD` fixture | high | mitigate | Fixture derived from `PayloadGenerator` (8 refs); containment guard | closed |
| T-28-19 | Repudiation | SC3 evidence | medium | mitigate | 13 `AssertionFailedError` quotations in `28-04-SUMMARY.md`; reverts via `git checkout HEAD --` | closed |
| T-28-20 | Information Disclosure | `Evidence:` line | medium | accept | Owned by `AR-28-01`; explicitly not reopened | closed |
| T-28-21 | Repudiation | this plan's own closure claim | high | mitigate | `## Residuals` section present in `28-04-SUMMARY.md` | closed |
| T-28-22 | Information Disclosure | `AiScanCheck` `**Original Value:**` | high | mitigate | `sanitizeCookiePointText` gated on `stripCookies && isCookieInsertionPoint` | closed |
| T-28-23 | Spoofing | the cookie-type predicate | high | mitigate | Enum identity compare against `AuditInsertionPointType.PARAM_COOKIE` | closed |
| T-28-24 | Tampering | predicate spelling class | high | mitigate | `exactlyOneInsertionPointCookieTypePredicateExistsInMainSource` + non-vacuity probe | closed |
| T-28-25 | Information Disclosure | `AiScanCheck` payload line | medium | mitigate | Same gate, recorded as **DEFENCE IN DEPTH**, not as a measured carrier | closed |
| T-28-26 | Elevation of Privilege | `buildDetail` `private` → `internal` | low | accept | Module-scoped; single fat JAR; no external surface created | closed |
| T-28-27 | Repudiation | `ONLY PRODUCER` KDoc | high | mitigate | Append-and-amend under a dated supersession marker with pre-edit digest | closed |
| T-28-28 | Denial of Service | `AiScanCheck.doCheck` | low | accept | Rendering-only change; rate limits, scope checks, opt-in, payload cap untouched | closed |
| T-28-29 | Repudiation | `26-SECURITY.md` `AR-27-08` row | high | mitigate | 8693-byte prefix digest `8dc326ac…7ae2e` re-asserted after edit | closed |
| T-28-30 | Repudiation | closure-claim scope | high | mitigate | All six identifiers present in row 315 (verified individually) | closed |
| T-28-31 | Tampering | `.planning/REQUIREMENTS.md` | high | mitigate | Absent from `files_modified`; sha256 gate ×2 | closed |
| T-28-32 | Denial of Service | executor context vs `26-SECURITY.md` | high | mitigate | Bounded `LC_ALL=C awk`/`sed` reads mandated; no executor lost in this phase | closed |
| T-28-33 | Tampering | `CookieCarrierInventoryTest` accessor set | high | mitigate | `MEASURED_CARRIER_SITES` cross-checked against pinned total and live scan | closed |
| T-28-34 | Information Disclosure | `AR-28-01` `Evidence:` line | medium | accept | Maintainer decision at 28-03's blocking checkpoint; not reopened | closed |
| T-28-35 | Spoofing | absent repo-wide producer gate | medium | accept | `D-28-06` — CONSIDERED AND NOT TAKEN, residual stated in register and source | closed |
| T-28-36 | Repudiation | probe for detail line (4) | high | mitigate | `grep -o "Payload Used"` 0 → 8; four named assertions | closed |
| T-28-37 | Spoofing | privacy-mode tooltip | high | mitigate | Named constant, single assignment site (=1), pinned by `PrivacyModeTooltipBoundTest` 5/5. **Re-verified after the 2026-08-28 UAT copy change — see Audit Trail note 2.** | closed |
| T-28-38 | Information Disclosure | route-2 gate fail-open (`HEADER`/`USER_PROVIDED`/`EXTENSION_PROVIDED`/`UNKNOWN`) | medium | accept | NAMED RESIDUAL, not widened; pinned + bounded by a 17-member enum tripwire | closed |
| T-28-39 | Information Disclosure | write-time/read-time bound | medium | accept | `D-28-09` maintainer acceptance; `D-28-10`'s four conditions discharged | closed |
| T-28-40 | Tampering | `AiScanCheck.kt` runtime behaviour | high | mitigate | Comment-only diff gate: **0 non-comment changed lines** across rounds 3 and 4 | closed |
| T-28-41 | Repudiation | KDoc premise on `type()` returning null | medium | mitigate | Replaced with the `javap` measurement of the default method body | closed |
| T-28-42 | Repudiation | row 315 prior text | high | mitigate | Both prefix digests re-assert: `8dc326ac…`@8693, `5316a971…`@16071 | closed |
| T-28-43 | Tampering | stale anchor literal | high | mitigate | Anchor re-derived with uniqueness check; both forms recorded in the SUMMARY | closed |
| T-28-44 | Repudiation | the override's conditionality | high | mitigate | Six machine-checked preconditions + HALT rule; verified at source by the phase verifier | closed |
| T-28-45 | Tampering | verifier's `status`/`score`/`gaps` | high | mitigate | Only `overrides_applied: 0 → 1` plus the additive block; body byte-unchanged | closed |
| T-28-46 | Tampering | `.planning/REQUIREMENTS.md` | high | mitigate | Same gate as T-28-13/31, run again in plan 28-08 | closed |
| T-28-47 | Denial of Service | executor context vs 266 KB register | high | mitigate | Bounded extractions mandated in `<read_first>`; warning also passed at dispatch | closed |
| T-28-48 | Information Disclosure | bound + fail-open set in the archival record | medium | accept | Named residuals in both records; explicit "round 3 added no control" statement | closed |
| T-28-49 | Spoofing | absent repo-wide detail-producer gate | medium | accept | `D-28-06`; `WR-01` stays open; restated rather than implied away | closed |
| T-28-50 | Repudiation | **round 4 + the UAT copy change, authored with no plan-time threat model** | medium | mitigate | Retroactively registered — see *Register Completeness*. Scope verified: 0 non-comment source lines in round 4; the UAT change is copy + test, full suite 1309/181 green, `ktlintCheck`/`detekt` clean. | closed |
| T-28-SC | Tampering | npm/pip/cargo installs | n/a | accept | Empty population — `build.gradle.kts` byte-unchanged across all 9 plans (`git diff --stat` prints nothing). A measured n/a, not a skipped check. | closed |

*Status: open · closed · open — below high threshold (non-blocking)*
*Disposition: mitigate (implementation required) · accept (documented risk) · transfer (third-party)*

---

## Register Completeness

**`28-09-PLAN.md` carries no `<threat_model>` block.** 8 of 9 plans do. Round 4 was planned mid-execution,
in response to code-review findings WR-02/WR-03, and the plan was written without the threat-model
section the other eight carry. The subsequent tooltip copy change (phase 28 UAT test 2) was likewise
not threat-modelled at plan time.

This is recorded rather than omitted, because a register that silently skips the one plan authored
under time pressure is exactly the "record wider than its control" defect this phase exists to correct
— and `T-28-12` rates that class `high`.

`T-28-50` covers both retroactively. Its scope was verified rather than assumed:

- Round 4 (`28-09`) changed **0 non-comment source lines** — comment and record text only.
- The UAT change edits one operator-facing string constant and its test; no control, gate, or
  data path is touched.
- Full suite green at 1309 tests / 181 classes / 0 failures / 0 errors / 1 skipped;
  `ktlintCheck` and `detekt` clean.

Residual risk is low, but it is **not zero and not measured to L2 depth**: a retroactive register
built by the same session that wrote the code is weaker evidence than a plan-time one reviewed
independently. Stated so a later reader can weigh it.

---

## Accepted Risks Log

| Risk ID | Threat Ref | Rationale | Accepted By | Date |
|---------|------------|-----------|-------------|------|
| `D-28-09` | T-28-39, T-28-48 | Write-time/read-time bound accepted as a NAMED RESIDUAL. Every issue produced under STRICT or BALANCED is measurably clean across all four detail lines of both producers; the residual needs a deliberate OFF scan then a mode switch. Conditional on `D-28-10`'s four naming conditions, all four verified discharged. | Project maintainer, interactively at the `verify_phase_goal` gate | 2026-08-28 |
| `D-28-06` | T-28-35, T-28-49 | Repository-wide producer gate CONSIDERED AND NOT TAKEN. A third detail producer added later would be caught by nothing; written down rather than implied away. `WR-01` stays open. | Project maintainer | 2026-08-27 |
| `AR-28-01` | T-28-11, T-28-20, T-28-34 | `ResponseAnalyzer`'s `Evidence:` tail — transferred to its own audit row with a named owner and decision venue rather than closed here. | Project maintainer, at 28-03's blocking checkpoint | 2026-08-27 |
| 28-07 four-reason decision | T-28-38 | Route-2 gate fail-open set NAMED, not widened — four reasons recorded at the gate KDoc, pinned by a test and bounded by a 17-member enum tripwire so it cannot grow silently. | Plan 28-07, recorded decision | 2026-08-28 |
| `D-28-04` | — | PRIV-05 stays `- [ ]`. The requirement says "by any path" and clause (f) lists five paths still open; ticking it would be the overclaim this phase series exists to correct. | Project maintainer | 2026-08-27 |
| — | T-28-04, T-28-26, T-28-28 | Low-severity accepts: operator's local evidence view, `internal` visibility widening, and unchanged `doCheck` behaviour. Each carries a stated invariant. | Plan-time disposition | 2026-08-27 |

*Accepted risks do not resurface in future audit runs.*

---

## What This Phase Did NOT Close

Recorded so a reader cannot mistake `threats_open: 0` for "the carrier is closed":

- **`AR-27-08` remains OPEN** in `26-SECURITY.md`. This phase controlled two write-time routes and
  named the rest; it did not close the parent audit row.
- **PRIV-05 remains unchecked.** See `D-28-04`.
- The **read-time layer** — a type-keyed pass over already-stored detail strings — is new architecture
  deferred to its own phase by `D-28-09`.
- The residual's width for a **JWT- or base64-shaped cookie value** under STRICT's *generic*
  (non-cookie) rules is honestly marked **UNMEASURED** at every record site. It is measurable and it
  bears on how severe the accepted residual actually is.
- `28-REVIEW-3.md` **WR-04** and **WR-06** are deferred with owners (see `28-09-SUMMARY.md`).
  WR-01 was closed by UAT test 1 (tooltip legible in a live Burp); WR-05 by UAT test 2.

---

## Security Audit Trail

| Audit Date | Threats Total | Closed | Open | Run By |
|------------|---------------|--------|------|--------|
| 2026-08-28 | 52 | 52 | 0 | `/gsd-secure-phase 28` (orchestrator, ASVS L1 short-circuit) |

**Note 1 — no auditor subagent was spawned.** The `secure-phase` short-circuit applies:
`threats_open: 0` + `register_authored_at_plan_time: true` + `asvs_level == 1`. Classification was
grep-depth, performed by the orchestrator against the tree. Raising `workflow.security_asvs_level` to
2 would force a `gsd-security-auditor` pass with L2 boundary-placement checks on the next run.

**Note 1a — corrected 2026-08-28.** This trail first said **51** threats. The register carries **52**
rows: `T-28-01`…`T-28-50` is 50, plus `T-28-04a` and `T-28-SC`, both of which the original count
skipped. Found by the round-4 verifier. The status column was re-derived across all 52 and every row
reads `closed`, so `threats_open: 0` was correct either way — but a security document that miscounts
its own register is precisely the defect this phase exists to correct, so it is fixed here and the
error is recorded rather than quietly overwritten.

**Note 2 — `T-28-37` was re-verified after its mitigation was edited.** The privacy-mode tooltip
changed during phase 28 UAT (test 2, closing `28-REVIEW-3.md` WR-05): the unscoped
`Applies from now on, not retroactively` sentence was deleted because
`McpToolContext.redactIfNeeded` re-redacts MCP tool results under the current mode, making the
blanket claim false for the dominant path. The mitigation's substance survives and is stronger:
the constant still exists, `privacyMode.toolTipText` still resolves to exactly **1** assignment site
referencing it, and `PrivacyModeTooltipBoundTest` went 4 → 5 tests — `FORWARD_ONLY_CLAUSE` retargeted
onto the correctly-scoped half-sentence, plus a new **negative** pin asserting the retired blanket
form cannot return.

---

## Sign-Off

- [x] All threats have a disposition (mitigate / accept / transfer)
- [x] Accepted risks documented in Accepted Risks Log
- [x] `threats_open: 0` confirmed
- [x] Register completeness gap (`28-09` has no plan-time threat model) recorded, not omitted
- [x] `T-28-37` re-verified after its mitigation was edited during UAT
- [x] Scope limits recorded — `AR-27-08` open, PRIV-05 unchecked, read-time layer deferred
