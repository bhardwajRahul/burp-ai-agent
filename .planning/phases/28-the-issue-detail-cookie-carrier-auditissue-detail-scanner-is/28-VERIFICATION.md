---
phase: 28-the-issue-detail-cookie-carrier-auditissue-detail-scanner-is
verified: 2026-08-28T11:05:00Z
status: human_needed
score: 6/6 must-haves verified
behavior_unverified: 0
overrides_applied: 1
overrides:
  - must_have: "SC1 — A COOKIE-typed injection point's `originalValue` does not appear in the `scanner_issues` tool result in STRICT or BALANCED"
    reason: >-
      The write-time/read-time bound is accepted as a NAMED RESIDUAL. Every issue produced under
      STRICT or BALANCED — the whole default posture — is measurably clean across all four detail
      lines of both producers. The residual requires a deliberate OFF scan followed by a mode switch,
      the same latent, opt-in reachability profile that put AR-27-08 at MEDIUM rather than high.
      Accepted on condition the silence is repaired: named in ISSUE_DETAIL_CARRIER_DISPOSITION's
      STILL OPEN clause and in 26-SECURITY.md row 315 clause (d), noted at
      AiScanCheck.consolidateIssues, and surfaced next to the privacy-mode selector.
    accepted_by: "Project maintainer, human answer given interactively at the `verify_phase_goal` gate on 2026-08-28 and recorded as D-28-09 (the acceptance) and D-28-10 (its conditions) in 28-CONTEXT.md. NOT an auto-advance default: `workflow.auto_advance` and `workflow._auto_chain_active` were both `false` on disk in .planning/config.json when the question was put and when this override was written."
    accepted_at: "2026-08-28T07:58:34Z"
re_verification:
  round: 3
  previous_status: gaps_found
  previous_score: 5/6
  history:
    - round: 1
      status: gaps_found
      score: 5/6
      note: "SC1 failed on a pure STRICT scan (two live leak routes); one `partial` on 28-03's RUN 2 recording failure."
    - round: 2
      status: gaps_found
      score: 5/6
      note: "Both round-1 leak routes closed and measured. SC1 re-failed on the NARROWER write-time/read-time bound; a second gap filed on record integrity."
    - round: 3
      status: human_needed
      score: 6/6
      note: "This pass. Covers gap-closure plans 28-07, 28-08 and 28-09 (rounds 3 and 4). SC1 carried by the D-28-09 override with all four D-28-10 conditions independently measured; the record-integrity gap closed, including the NEW overclaim round 3 itself committed and round 4 retired."
  gaps_closed:
    - "Gap 1 (SC1, the write-time/read-time bound) — DISPOSITIONED, not re-measured. Route (ii) taken: maintainer acceptance D-28-09 recorded as a named residual, with the four D-28-10 conditions verified independently by this verifier: (1) `ISSUE_DETAIL_CARRIER_DISPOSITION` clause (a) carries the literal `WRITE-TIME/READ-TIME BOUND` (`CookieCarrierInventoryTest.kt:784`); (2) `26-SECURITY.md` row 315 clause (d) extended under `AMENDED 2026-08-28 by plan 28-08`, literal present exactly once in the row; (3) `AiScanCheck.consolidateIssues`'s KDoc carries the full chain at `:100-161`; (4) `PRIVACY_MODE_TOOLTIP` (`SettingsPanelInit.kt:54-58`) assigned at `:96`, the only `privacyMode.toolTipText` site in `src/main`, pinned by `PrivacyModeTooltipBoundTest` 4/4 green."
    - "Gap 2, half 1 — the probe claim for detail line (4) was made TRUE rather than retracted. `grep -o 'Payload Used'` on `AiScanCheckDetailCookieCarrierTest.kt` went 0 -> 8; four named tests added (`cookiePayloadLineIsStrippedUnderStrict`, `...UnderBalanced`, `cookiePayloadLineSurvivesUnderOff`, `urlParamPayloadLineSurvivesStrict_attributionControl`). Class count 10 -> 16, all green in this verifier's own run. The DEFENCE-IN-DEPTH asymmetry is preserved in the tests' own banner comment and in both records — the assertions made a claim true, they did not close a leak."
    - "Gap 2, half 2 — the route-2 fail-open type set is recorded. `theRouteTwoGateIsFailOpenForTheseCookieCapableTypes` pins HEADER / USER_PROVIDED / EXTENSION_PROVIDED / UNKNOWN as passing through, `theInsertionPointTypeEnumPopulationIsTheOneTheResidualWasMeasuredAgainst` pins the 17-member population so a Burp bump goes red instead of widening in silence, and the false `type()`-may-return-null KDoc premise is corrected at source against the `javap` measurement. NAME-THE-RESIDUAL was the executed choice, with the four reasons for not widening recorded in clause (e)."
    - "The NEW overclaim round 3 committed while repairing the old ones — the absolute `There is no read-time pass over it` at four record sites, plus the companion word `provably` at two. Retired by plan 28-09 and re-measured here: both literals now occur 0 times in `AiScanCheck.kt`, `SettingsPanelInit.kt`, `CookieCarrierInventoryTest.kt` and row 315. The replacements name the mechanism (`McpTool.kt:45`/`:78` -> `McpToolContext.redactIfNeeded` -> `Redaction.apply` under the CURRENT mode) and I confirmed every cited line number against the tree."
  gaps_remaining: []
  regressions: []
deferred: []
behavior_unverified_items: []
coincidental_reliance_items: []
human_verification:
  - test: "In a live Burp, open Settings -> the privacy-mode selector and hover it. Read the tooltip to its end without moving the pointer."
    expected: "All three clauses are legible, including the two that D-28-10 made conditions of the SC1 acceptance: `Applies from now on, not retroactively.` and `Scanner findings already recorded keep the values they were built with; re-scanning does not rewrite them.`"
    why_human: "Visual/widget behaviour. Measured at source by this verifier: `PRIVACY_MODE_TOOLTIP` is a 206-character plain string with NO `<html>` wrapper, and `grep -rn 'ToolTipManager' src/main/kotlin/com/six2dez/burp/aiagent/ui/` returns nothing, so Swing's default 4000 ms `dismissDelay` and single-line rendering apply. `28-REVIEW-3.md` WR-01 records the same measurement and the finding is deliberately DEFERRED in `28-09-SUMMARY.md`. The string is present and wired — whether an operator can actually READ the conditioned clauses is the one part of D-28-10 condition 3 that no grep can settle, and it is the condition the acceptance rests on."
  - test: "Read the tooltip's second sentence as an operator: `Applies from now on, not retroactively.` Decide whether it should be scoped before ship."
    expected: "A decision recorded either way — keep the pessimistic blanket wording, or narrow it to the scanner-detail lines it is actually about."
    why_human: "Operator-copy judgement, deliberately deferred as `28-REVIEW-3.md` WR-05 and owned in `28-09-SUMMARY.md`. The sentence is unqualified and generalises a narrow residual: `McpToolContext.redactIfNeeded` DOES apply the current policy to already-captured traffic at send time, so switching to STRICT is retroactive for the dominant path. It errs pessimistic, which is the safe direction, and `FORWARD_ONLY_CLAUSE` in `PrivacyModeTooltipBoundTest.kt` currently pins the exact wording — so correcting it is a copy change plus a test change, not a silent edit."
---
> **READ ORDER — THIS FILE NOW CARRIES THREE VERIFICATION ROUNDS.** The frontmatter above is
> CURRENT (round 3, `human_needed`, 6/6 with one override). The report body immediately below is the
> **round-2** report, preserved BYTE-UNCHANGED because it is the artifact the maintainer's `D-28-09`
> acceptance was given against — its `gaps_found` / 5/6 headline is a historical statement, not the
> current one. The **round-3 report** (covering gap-closure plans 28-07, 28-08 and 28-09) is appended
> at the end of this file, under "Round 3 Re-Verification". Nothing in the round-2 body was edited,
> softened or deleted; the fenced `overrides:` template block at what was line 282 is untouched.

---


# Phase 28: The Issue-Detail Cookie Carrier — Verification Report (Round 2)

**Phase Goal:** Close `AR-27-08`. A COOKIE-typed injection point's value reaches the `scanner_issues`
MCP tool result through `AuditIssue.detail()` and survives `Redaction.apply` in STRICT and BALANCED,
emitted verbatim. Closes `AR-27-08` AND `InjectionPointExtractor.kt:29` together.

**Verified:** 2026-08-27
**Status:** gaps_found
**Re-verification:** Yes — round 2, after gap closure by plans 28-04, 28-05 and 28-06
**Mode:** standard (no `mode:` on the ROADMAP phase; MVP rules dormant)

## Goal Achievement

**The gap round did the work it was scoped to do, and it did it well.** Both round-1 leak routes are
genuinely closed — measured, not taken on trust. Route 1's payload line is gated type-keyed on a
closed enum and proven with a fixture the production `PayloadGenerator` builds, which is precisely
the vacuity my round-1 report measured. Route 2's gate avoids the wrong-predicate trap on the record
rather than by luck. Every round-1 gap and the one round-1 `partial` are closed, with no regression
anywhere in a 1298-test suite. That is real, and none of what follows should be read as diminishing it.

**SC1 is nonetheless still not satisfied**, on a narrower and different mechanism. Round 1: the value
leaked under a pure STRICT scan. Round 2: every write under STRICT or BALANCED is controlled and
measurably clean — including the whole of the default posture, since `privacyMode` defaults to
`BALANCED` — and what remains is the temporal bound. That distinction is the finding, not a hedge.

### The adjudication: (b), and why

The prompt asked for (a) or (b). **I conclude (b).** Three facts decide it.

**1. SC1's wording binds to the tool result, not to the write site.** "A COOKIE-typed injection
point's `originalValue` does not appear in the `scanner_issues` tool result in STRICT or BALANCED."
The tool result is produced at read time. There is a read, with `privacyMode = STRICT`, that returns
the value. Reading "in STRICT" as scoping the *scan* rather than the *emission* is available, but it
is the strained reading, and it is the reading that lets the record be wider than the control — the
exact pattern this phase exists to correct, now applied to the phase's own verification.

**2. `AR-27-08`'s own sentence is still literally true at HEAD.** Row 315's original 2026-08-25
measurement reads: *"A COOKIE-typed injection point's value reaches the `scanner_issues` tool result
through `AuditIssue.detail()` and SURVIVES `Redaction.apply` in STRICT and in BALANCED alike, emitted
verbatim."* Along the OFF-then-switch path, every clause of that sentence holds. This is therefore a
surviving instance of the chartered finding, not a new residual outside the boundary. It is inside
scope by exactly the argument D-28-05 used to pull route 2 in: *"both are instances of `AR-27-08` as
the ROADMAP defines it, so both are inside the existing boundary."* And unlike D-28-06's repo-wide
gate, it was never considered-and-rejected — it is simply absent from every record.

**3. It is measured, not inferred.** I did not need to mutate the tree; the phase's own committed
evidence closes the path end to end:

| Step | Evidence | Where |
|---|---|---|
| An OFF-built detail carries the value | `cookieBaseValueSurvivesUnderOff` — green | `AiScanCheckDetailCookieCarrierTest:179` |
| That string is stored, not re-rendered | `api.siteMap().add(issue)`; `detail = detail()` | `ActiveAiScanner.kt:1285`; `Serialization.kt:14` |
| `scanner_issues` reads it back | `val issues = api.siteMap().issues()` | `McpToolExecutorImpl.kt:605` |
| STRICT redaction does NOT remove it | 28-05 **red probe 1**, verbatim: `AssertionFailedError: STRICT: … the sentinel 'cedar-anchor-marble-feather' was present` — in the **redacted** detail field, with the gate not firing | `28-05-SUMMARY.md:224` |
| A corroborating green shows the same blindness | `urlParamInsertionPointSurvivesStrict_attributionControl` asserts the sentinel SURVIVES `Redaction.apply` under STRICT | `AiScanCheckDetailCookieCarrierTest:200` |
| Re-scanning under STRICT does not repair it | `consolidateIssues` -> `KEEP_EXISTING` on canonical name + normalized URL | `AiScanCheck.kt:101-112` |

The 28-05 red probe is the decisive one: it is a direct measurement of "gate does not fire at write
time, STRICT redactor sees the value, value survives." The OFF path arrives at the redactor in
exactly that state. This is not the same-day inference pattern the phase set out to avoid.

**What (a) has going for it, stated fairly.** The mechanism the phase chartered — a type-keyed write
site control at the last point that still holds the type — is complete and correct at all four lines.
SC2 explicitly requires the value to be present under OFF, and the leaking string *was* produced
under OFF. `privacyMode` defaults to `BALANCED`, so the sequence needs a deliberate `OFF`. Reachability
is the same latent, opt-in profile the register itself used to justify `AR-27-08` at MEDIUM rather
than high. **This is why the honest disposition may well be "accept the residual" — but that is a
maintainer decision recorded as a named residual, not a verifier's finding of satisfaction.** The
override block at the end of this report is the vehicle for it.

**The silence stands as a finding either way, as the prompt anticipated.** The temporal bound appears
in NO phase-28 KDoc, decision (D-28-01 / D-28-05 / D-28-06 / D-28-07 / D-28-08), SUMMARY, register
row, or `ISSUE_DETAIL_CARRIER_DISPOSITION`. I grepped `src/main/kotlin`, `src/test/kotlin` and
`.planning/phases/28-*/` for `retroactiv|non-retroactive|write-time snapshot|KEEP_EXISTING|stale`: the
only hits are inside `28-REVIEW-2.md` itself. Two enumerations that declare themselves the place
residuals get named — `ISSUE_DETAIL_CARRIER_DISPOSITION`'s "STILL OPEN, NAMED SO NOBODY READS THIS AS
A CLOSURE" and row 315's "(d) WHAT THIS AMENDMENT DOES NOT COVER, BY IDENTIFIER" — both omit it. And
`SettingsPanelInit.kt:58`'s tooltip ("Controls how traffic is redacted before sending to a model")
invites precisely the retroactive reading that is false.

### Observable Truths

| # | Truth (verbatim ROADMAP Success Criterion) | Status | Evidence |
|---|---|---|---|
| SC1 | A COOKIE-typed injection point's `originalValue` does not appear in the `scanner_issues` tool result in STRICT or BALANCED. Cookie NAMES may remain; VALUES must not. | ✗ **FAILED** (narrowed) | Holds for every issue PRODUCED under STRICT/BALANCED — all four detail lines across both producers, 31 tests. Falsified for issues produced under OFF and read under STRICT. See the adjudication above. |
| SC2 | Under `OFF` the value still appears — the fix is policy-driven, not an unconditional rewrite. | ✓ VERIFIED | Four gates, all keyed on `policy.stripCookies`; `Redaction.kt:41-45` gives OFF `stripCookies = false`. `cookieOriginalValueSurvivesUnderOff` (route 1) and `cookieBaseValueSurvivesUnderOff` (route 2) both green in this verifier's own full run. Attribution controls on both routes prove the gates are type-keyed, not unconditional. |
| SC3 | A red probe reverting the control turns a NAMED assertion red, with the specific assertion and its failure message recorded — not "the suite went red". | ✓ VERIFIED | Discharged for BOTH gap routes. 28-04: five measured runs, every `org.opentest4j.AssertionFailedError` quoted verbatim (`28-04-SUMMARY.md:162-375`), including a fixture non-vacuity failure. 28-05: three probes (`:200-265`), the first being the **wrong-predicate** probe — it compiles, every presence assertion stays green, and only the designated `cookieBaseValueIsStrippedUnderStrict` goes red. That probe is the single strongest artifact this phase produced. |
| SC4 | `InjectionPointExtractor.kt:29` resolved in the same phase as the route, with its two consumers' differing dispositions preserved (`AdaptivePayloadEngine.kt:52` must not be double-redacted). | ✓ VERIFIED | `git diff --stat ad2ca90 HEAD -- .../AdaptivePayloadEngine.kt` **empty** — byte-unchanged across the whole phase including the gap round; `:52` still `if (privacyMode != PrivacyMode.OFF) "[REDACTED_VALUE]"`. Extractor predicate is `Redaction.isCookieParameterType(it.type().name)` (`:38`) and still returns the RAW value, so consumer 1 is not double-redacted. `InjectionPointExtractorTest` 12/12, `CookieRouteDispositionTest` 7/7. |
| SC5 | `26-SECURITY.md`'s `AR-27-08` row amended — append-and-amend, prior text byte-prefix intact — and `threats_open` recomputed rather than asserted. | ✓ VERIFIED | Re-derived independently: row 315 is 16191 bytes; `awk 'NR==315' \| head -c 8693 \| shasum -a 256` = `8dc326ac23204becce687deeba867740eb2d4dde21346c58d7da9595d137ae2e`, the value the amendment itself pins. Two dated markers with distinct plan ids (28-03, 28-06), and the second names WHICH it supersedes so they cannot be merged. Row head still `**NEW, OPEN, severity MEDIUM…**`. `threats_open: 0` with the awk's own raw output recorded (46 rows, 46 closed) and the AR-/T-26- population distinction stated. |
| SC6 | `ResponseAnalyzer`'s narrow transitive tail examined in the same pass. | ✓ VERIFIED | `EvidenceTailReachTest` 2/2. Cap multiset re-confirmed at source by this verifier: `ResponseAnalyzer.kt:682 take(80)`, `:720 take(60)`, `:791 take(80)` — the ROADMAP's singular "capped at 80" corrected to `{80, 80, 60}`, with `ActiveAiScanner.kt:1206`'s larger `take(100)` recorded as defeating every construction cap. `AR-28-01` filed at row 319, DERIVED MEDIUM, named owner and venue, two-directional reach where the negative case still asserts the analyzer fired. |

**Score: 5/6 truths verified (0 present, behavior-unverified).**

The headline is unchanged from round 1, and that is misleading unless read with the mechanism: round
1's SC1 failed because a pure STRICT scan leaked; round 2's fails only on a stored string written in
a different mode. Six of six round-1 gap items are closed.

### Required Artifacts

| Artifact | Expected | Status | Details |
|---|---|---|---|
| `ScannerIssueSupport.sanitizeRenderedPayload` | Type-keyed strip of the `Payload Used:` line | ✓ VERIFIED | `:127-138`; gate `policy.stripCookies && point.type == InjectionType.COOKIE`; marker referenced, not retyped; no emptiness guard (deliberate, stated). Wired at `:221`. |
| `AiScanCheck.isCookieInsertionPoint` | Identity compare on `AuditInsertionPointType.PARAM_COOKIE` | ✓ VERIFIED | `:459`. Constant confirmed present in `montoya-api-2026.2.jar` by `javap`. Pinned against the wrong-predicate substitution by a dedicated test. |
| `AiScanCheck.sanitizeCookiePointText` | Single gate both route-2 lines call | ✓ VERIFIED | `:474-486`, companion-scoped (deviation recorded with its detekt `TooManyFunctions` reason, and QUAL-07 honoured — `detekt-baseline.xml` byte-unchanged, machine-checked). Wired at `:388` and `:392`. |
| `AiScanCheckDetailCookieCarrierTest` | Non-vacuous route-2 probe | ⚠️ PARTIAL | 10/10 green with fixture pin, OFF survival, attribution control and a non-vacuity tail check. **But zero assertions on `**Payload Used:**`** — line (4) is unprobed while the register names a probe for it. See gap 2. |
| `IssueDetailCookieCarrierTest` | Route-1 probe, production-derived fixture | ✓ VERIFIED | 21/21. `PAYLOAD` derived from `PayloadGenerator.generateContextAwarePayloads` with a non-vacuity assertion (`:728-739`) that names my round-1 finding as the reason it exists. |
| `CookieCarrierInventoryTest` | Inventory + disposition register, corrected | ⚠️ PARTIAL | 4/4; `isCommentOnly` narrowed (`:243-246`); `BOTH NOW CONTROLLED` amended in place at `:495`. Residual enumeration incomplete — gap 2. |
| `CookieRouteDispositionTest` | Predicate-population tripwires | ✓ VERIFIED | 7/7, WR-05's `File.separator` defect fixed with the reason recorded (`:383-386`). |
| `26-SECURITY.md` row 315 | Append-and-amend, prefix intact, OPEN | ✓ VERIFIED | See SC5. |
| `.planning/REQUIREMENTS.md` | Byte-unchanged, PRIV-05 `- [ ]` | ✓ VERIFIED | `shasum -a 256` = `9b3219662ec0d007c1c82d64eed3ef2698bd306ce69f01205ac9bbc3f42fcfb4`; line 23 `- [ ]`; absent from `git status --porcelain`. |

### Key Link Verification

| From | To | Via | Status |
|---|---|---|---|
| `ScannerIssueSupport.kt:221` | `sanitizeRenderedPayload` | Direct call in the detail-line accumulator | ✓ WIRED |
| `AiScanCheck.kt:388`, `:392` | `sanitizeCookiePointText` | Raw-string interpolation in `buildDetail` | ✓ WIRED |
| `AiScanCheck.buildDetail` | `getSettings().privacyMode` | `RedactionPolicy.fromMode` at `:361-362` | ✓ WIRED (live policy read — **at write time only**; see gap 1) |
| `App.kt:215` | `AiScanCheck` | `registerActiveScanCheck(..., PER_INSERTION_POINT)` | ✓ WIRED |
| `ActiveAiScanner.kt:1285` | Burp site map | `api.siteMap().add(issue)` | ✓ WIRED |
| `McpToolExecutorImpl.kt:605` | `Serialization.kt:14` | `api.siteMap().issues()` -> `detail = detail()` | ✗ **NOT POLICY-GATED** — the read path applies no live-policy filter to the stored string. This is gap 1's mechanism. |

### Data-Flow Trace (Level 4)

| Artifact | Data variable | Source | Produces real data | Status |
|---|---|---|---|---|
| `buildActiveIssueDetailLines` | `point.type`, `payload.value` | Real `InjectionPoint` from `InjectionPointExtractor`; payload from `PayloadGenerator` (fixture derived the same way) | Yes | ✓ FLOWING |
| `AiScanCheck.buildDetail` | `insertionPoint.baseValue()`, `insertionPoint.type()` | Real Montoya `AuditInsertionPoint` supplied by Burp | Yes | ✓ FLOWING |
| Both gates | `policy` | `RedactionPolicy.fromMode(getSettings().privacyMode)` — the live setting | Yes, at write time | ⚠️ **STALE ON READ** — the value emitted by `scanner_issues` was filtered against the policy in force when the issue was built, not the one in force when it is emitted. |

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|---|---|---|---|
| Full suite, forced re-run | `JAVA_HOME=$(…21) ./gradlew test --rerun-tasks` | exit 0 | ✓ PASS |
| Suite totals from XML, not console | aggregate over `build/test-results/test/TEST-*.xml` | 180 classes, **1298 tests, 0 failures, 0 errors, 1 skipped** | ✓ PASS (the 1 skip is the pre-existing `@Disabled` in `ExternalMcpClientManagerTest`) |
| Gap-round class counts | same aggregation | `IssueDetailCookieCarrierTest` 21, `AiScanCheckDetailCookieCarrierTest` 10, `CookieRouteDispositionTest` 7, `CookieCarrierInventoryTest` 4, `EvidenceTailReachTest` 2, `InjectionPointExtractorTest` 12 — all 0/0/0 | ✓ PASS |
| `AdaptivePayloadEngine` untouched | `git diff --stat ad2ca90 HEAD -- …/AdaptivePayloadEngine.kt` | empty | ✓ PASS |
| `detekt-baseline.xml` untouched | `git diff --stat ad2ca90 HEAD -- detekt-baseline.xml` | empty | ✓ PASS |
| Row-315 byte-prefix | `awk 'NR==315' … \| head -c 8693 \| shasum -a 256` | `8dc326ac…7ae2e` | ✓ PASS |
| Montoya `type()` default body | `javap -c` on `montoya-api-2026.2.jar` | `getstatic AuditInsertionPointType.EXTENSION_PROVIDED; areturn` | ✓ PASS (confirms WR-01's premise; falsifies the KDoc at `AiScanCheckDetailCookieCarrierTest:241-260`) |
| Route-2 payload-line coverage | `grep -n "Payload Used" …/AiScanCheckDetailCookieCarrierTest.kt` | **zero lines** | ✗ FAIL — gap 2 |
| Temporal-bound recorded anywhere | `grep -rniE "retroactiv\|write-time snapshot\|KEEP_EXISTING\|stale" src/ .planning/phases/28-*/` | only hits are inside `28-REVIEW-2.md` | ✗ FAIL — gap 1 |

`./gradlew check` was not run as a gate — RED for the maintainer-accepted coverage-floor reason.

### Probe Execution

No `scripts/*/tests/probe-*.sh` exists in this repository and no phase-28 plan declares one; the
phase's probe discipline is the JUnit red-probe protocol, verified under SC3 above.

### Requirements Coverage

| Requirement | Source plans | Status | Evidence |
|---|---|---|---|
| PRIV-05 — "Cookie values do not reach an AI backend in STRICT or BALANCED mode **by any path**" | 28-01…28-06 (all six declare it) | ✗ BLOCKED — and correctly recorded as such | Stays `- [ ]` by D-28-04. **That decision is CONSISTENT with what the code now does, and this round makes it more so, not less.** Five carriers remain open (`AR-27-04`, `AR-27-07`, `AR-27-10`, `AR-27-11`, `AR-28-01`), `AR-27-08` itself stays open, and gap 1 adds a sixth path. `REQUIREMENTS.md` byte-unchanged; not edited by this verifier. |

No orphaned requirements: `grep -nE "\| 28 \|" .planning/REQUIREMENTS.md` returns nothing beyond
PRIV-05's own mapping row, and all six plans claim it.

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|---|---|---|---|---|
| — | — | `TBD` / `FIXME` / `XXX` / `TODO` / `HACK` / `PLACEHOLDER` across all six phase-modified files | — | **None found.** Debt-marker gate clean. |
| — | — | 28-05 probe-3 scratch symbol residue (`scratchProbeSecondInsertionPointPredicate`) | — | **None found** in `src/main` or `src/test` — the probe was reverted as claimed. |
| `CookieRouteDispositionTest.kt` | 378 | `trimmed.startsWith("*")` retained in `matchingCodeLinesIn` — the exact predicate 28-06 narrowed next door | ⚠️ Warning | The helper backing this round's own route-2 tripwire misclassifies the entire route-2 detail template as comment. Not a live hole today (the shipped predicate is not in a raw string), but the tripwire is defeatable inside the one file it declares as owner. Also present in ~13 sibling test scanners — a shared `isCommentOnly` utility is the real fix. |
| `AiScanCheckDetailCookieCarrierTest.kt` | 86-93 | KDoc guarantees the fixture pin "runs before any behavioural assertion" — no `@TestMethodOrder`, no `junit-platform.properties` | ℹ️ Info | Claim defect, not a live hole: `cookieBaseValueSurvivesUnderOff` and the attribution control cover the same ground. Moving the two `assertEquals` into the fixture factory would make the claim true by construction. |

### Deferred Items

None. Phase 28 is the last phase of the milestone (`ROADMAP.md` phase details end at 28), so no later
phase addresses either gap. Neither is deferrable within this milestone.

### Human Verification Required

Not applicable at this status — gaps_found takes precedence. **But the SC1 disposition is a
maintainer judgement, not an engineering gap**, and the cheapest correct outcome may be to accept the
residual rather than build the second layer. To take that path, add to this file's frontmatter:

```yaml
overrides:
  - must_have: "SC1 — A COOKIE-typed injection point's `originalValue` does not appear in the `scanner_issues` tool result in STRICT or BALANCED"
    reason: >-
      The write-time/read-time bound is accepted as a NAMED RESIDUAL. Every issue produced under
      STRICT or BALANCED — the whole default posture — is measurably clean across all four detail
      lines of both producers. The residual requires a deliberate OFF scan followed by a mode switch,
      the same latent, opt-in reachability profile that put AR-27-08 at MEDIUM rather than high.
      Accepted on condition the silence is repaired: named in ISSUE_DETAIL_CARRIER_DISPOSITION's
      STILL OPEN clause and in 26-SECURITY.md row 315 clause (d), noted at
      AiScanCheck.consolidateIssues, and surfaced next to the privacy-mode selector.
    accepted_by: "<maintainer>"
    accepted_at: "<ISO timestamp>"
```

**The override covers gap 1 only.** Gap 2 (the unprobed line (4) claimed as probed, and the two
missing residual entries) is not a judgement call — it is prose asserting something the tree does not
support, which D-28-08 governs directly.

### Gaps Summary

Two gaps, both narrow, neither of them a defect in the mechanism this round built.

**Gap 1 — SC1, the temporal bound.** Both controls decide once, at issue-construction time, and bake
the result into an immutable string that Burp stores and `scanner_issues` replays. Nothing in the
emission path consults the live policy. An issue built under `OFF` therefore emits the raw cookie
value on a later STRICT read; `consolidateIssues` returns `KEEP_EXISTING`, so re-scanning under STRICT
does not repair the site map; and `Redaction.apply` provably cannot rescue it — 28-05's own red probe
recorded the sentinel surviving STRICT redaction verbatim when the write gate does not fire. Fix is a
disposition, not necessarily code: either a second, explicitly-weaker line-prefix scrub at the MCP
boundary against the live policy, or a recorded acceptance. **Silently shipping a control an operator
will read as retroactive is the option that is not available** — and `SettingsPanelInit.kt:58`'s
tooltip currently invites exactly that reading.

**Gap 2 — the record is narrower than it claims in two places and wider in one.** Route 2's
`**Payload Used:**` line has zero assertions while `ISSUE_DETAIL_CARRIER_DISPOSITION` and row 315
both name `AiScanCheckDetailCookieCarrierTest` as its probe. And the two clauses that exist precisely
to enumerate residuals — "STILL OPEN, NAMED SO NOBODY READS THIS AS A CLOSURE" and "(d) WHAT THIS
AMENDMENT DOES NOT COVER" — omit both the temporal bound and the fail-open insertion-point-type set,
the latter measured at source here by `javap`: `AuditInsertionPoint.type()`'s default body returns
`EXTENSION_PROVIDED`, and the enum also carries `USER_PROVIDED` and `HEADER`, none of which route 2's
identity compare catches. Route 1's pass-through was safe because its enum's only cookie-capable
member IS `COOKIE`; that reasoning does not transfer, and the record does not say so.

Both gaps share a root cause worth naming: **this round measured its controls rigorously and did not
re-measure its record against them.** The mechanism work is the strongest in the phase; the
enumerations that describe it drifted one step behind it.

Not re-reported, per the round's standing decisions: D-28-06's absent repo-wide single-producer gate
(named residual, and I confirmed it is NOT misrepresented as closed — `ScannerIssueSupport.kt:197-207`,
`ISSUE_DETAIL_CARRIER_DISPOSITION` and row 315 clause (e) each state plainly that a third producer is
caught by nothing); `AR-28-01`'s uncontrolled `Evidence:` tail; `AiScanCheck.kt:392` as defence in
depth rather than a measured carrier; PRIV-05 staying `- [ ]`; and `./gradlew check`'s accepted red.

---

_Verified: 2026-08-27_
_Verifier: Claude (gsd-verifier), round 2_

---

# Phase 28: The Issue-Detail Cookie Carrier — Verification Report (Round 3)

**Phase Goal:** Close `AR-27-08`. A COOKIE-typed injection point's value reaches the `scanner_issues`
MCP tool result through `AuditIssue.detail()` and survives `Redaction.apply` in STRICT and BALANCED,
emitted verbatim. Closes `AR-27-08` AND `InjectionPointExtractor.kt:29` together.

**Verified:** 2026-08-28
**Status:** human_needed
**Score:** 6/6 must-haves verified (1 by override)
**Re-verification:** Yes — round 3, covering gap-closure plans 28-07, 28-08 (round 3) and 28-09 (round 4)
**Mode:** standard (no `mode:` on the ROADMAP phase; MVP rules dormant)

## What this round was asked to decide

Round 2 returned `gaps_found` 5/6 on two gaps: SC1's write-time/read-time bound, and a
record-integrity gap. Since then the bound was **dispositioned** by a human maintainer (`D-28-09`,
conditional on `D-28-10`), and three plans ran. Round 4 (plan 28-09) exists only because the round-3
code review caught round 3 committing a **new** overclaim while repairing the old ones.

So this round has one question, and it is not about the mechanism: **does the record now match the
tree?** That is what round 2 found wanting, and it is where I spent this pass.

**It does.** Every claim I could falsify, I tried to falsify at source, and all of them held.

## Goal Achievement

### Observable Truths

| # | Truth (verbatim ROADMAP Success Criterion) | Status | Evidence |
|---|---|---|---|
| SC1 | A COOKIE-typed injection point's `originalValue` does not appear in the `scanner_issues` tool result in STRICT or BALANCED. Cookie NAMES may remain; VALUES must not. | ✓ **PASSED (override)** | Override: the write-time/read-time bound accepted as a NAMED RESIDUAL — accepted by the project maintainer, interactively at the `verify_phase_goal` gate, on 2026-08-28T07:58:34Z (`D-28-09`). Round 2's underlying measurement is UNCHANGED and I did not re-litigate it. What I DID verify is that the acceptance's four `D-28-10` conditions are discharged in the tree — see the table below. |
| SC2 | Under `OFF` the value still appears — the fix is policy-driven, not an unconditional rewrite. | ✓ VERIFIED | Re-run by this verifier, not taken from a SUMMARY: `cookieBaseValueSurvivesUnderOff`, `cookiePayloadLineSurvivesUnderOff`, `cookieOriginalValueSurvivesUnderOff` all green. Both attribution controls (`urlParamInsertionPointSurvivesStrict_attributionControl`, `urlParamPayloadLineSurvivesStrict_attributionControl`) green, so the four gates are type-keyed rather than unconditional. |
| SC3 | A red probe reverting the control turns a NAMED assertion red, with the specific assertion and its failure message recorded. | ✓ VERIFIED | Unchanged from round 2 and not disturbed by rounds 3–4 (both are comment/record rounds). 28-04's five measured runs and 28-05's three probes stand, including the wrong-predicate probe. |
| SC4 | `InjectionPointExtractor.kt:29` resolved in the same phase as the route, with its two consumers' differing dispositions preserved (`AdaptivePayloadEngine.kt:52` must not be double-redacted). | ✓ VERIFIED | Re-derived at HEAD: `git diff --stat ad2ca90 HEAD -- .../AdaptivePayloadEngine.kt` **empty** — byte-unchanged across all nine plans; `:52` still reads `if (privacyMode != PrivacyMode.OFF) "[REDACTED_VALUE]"`. The extractor predicate is `Redaction.isCookieParameterType(it.type().name)` (`:37`) and still passes the RAW value through, with the reason written next to it. `InjectionPointExtractorTest` 12/12, `CookieRouteDispositionTest` 7/7 in my own run. |
| SC5 | `26-SECURITY.md`'s `AR-27-08` row amended — append-and-amend, prior text byte-prefix intact — and `threats_open` recomputed rather than asserted. | ✓ VERIFIED | Both digests re-derived independently under `LC_ALL=C` at HEAD after the round-4 edit: 8693-byte prefix = `8dc326ac23204becce687deeba867740eb2d4dde21346c58d7da9595d137ae2e`, 16071-byte prefix = `5316a97149017ae824d162b72b99d954cb0fa25b28b0c3a8214c01e42390ed72`. Row is now 25949 bytes and carries **four** dated markers in date order (`28-03`, `28-06`, `28-08`, `28-09`), the fourth using `NARROWED` rather than `AMENDED` and stating explicitly that it withdraws nothing. `threats_open` recomputed by re-running the register's own documented awk: **46 rows, 46 closed, open=0**, matching the frontmatter value. |
| SC6 | `ResponseAnalyzer`'s narrow transitive tail examined in the same pass. | ✓ VERIFIED | Unchanged from round 2; `EvidenceTailReachTest` 2/2 in my own run. `AR-28-01` still filed and still named as OPEN in clause (f) of the carrier registry. |
| G2 | *(round-2 gap 2, carried as a truth)* The phase's own record enumerates its residuals exhaustively and names a committed probe only where one exists. | ✓ VERIFIED | Both halves closed, and the NEW overclaim round 3 introduced is closed too. See "Record integrity" below. |

**Score: 6/6 truths verified (1 by override, 0 present-behaviour-unverified).**

The headline moves 5/6 -> 6/6 for two different reasons that should not be blurred: SC1 moves because
a **human decided**, not because code changed; G2 moves because the **tree changed** to match the
prose. Nothing was re-measured into passing.

### The four `D-28-10` conditions, verified at source

The override is CONDITIONAL. An override whose conditions were never checked is worth nothing, so I
checked all four myself rather than reading 28-08's precondition gate.

| # | `D-28-10` condition | Status | Evidence measured at HEAD |
|---|---|---|---|
| 1a | Named in `ISSUE_DETAIL_CARRIER_DISPOSITION`'s STILL OPEN clause | ✓ | `CookieCarrierInventoryTest.kt:784` — clause (a) `THE WRITE-TIME/READ-TIME BOUND, NAMED`, inside the plan-28-08 supersession block that explicitly EXTENDS (not replaces) the 28-06 STILL OPEN clause. Clause (f) re-lists it among what is still open. |
| 1b | Named in `26-SECURITY.md` row 315 clause (d), append-and-amend, 8693-byte prefix intact | ✓ | `AMENDED 2026-08-28 by plan 28-08 … THIS AMENDMENT ADDS TO CLAUSE (d)`; literal `WRITE-TIME/READ-TIME BOUND` present exactly once in the row; both pinned digests re-assert (above). |
| 2 | Noted at `AiScanCheck.consolidateIssues` | ✓ | `AiScanCheck.kt:100-161` — a KDoc that names the bound, the three hops (`api.siteMap().add` -> stored string -> `scanner_issues` replay), `KEEP_EXISTING` as why it is not self-healing, the disposition with its human authority, and the round-3/round-4 correction history. Opens with `THIS PARAGRAPH IS A NOTE, NOT A CONTROL`. |
| 3 | Surfaced next to the privacy-mode selector | ⚠️ **present and wired; legibility needs a human** | `PRIVACY_MODE_TOOLTIP` (`SettingsPanelInit.kt:54-58`) assigned at `:96`; `grep -rn 'privacyMode.toolTipText' src/main/kotlin/` returns exactly **1** line and it references the constant. `PrivacyModeTooltipBoundTest` 4/4 green pins all three clauses plus the exactly-one-site fact. **But** the string is 206 plain characters with no `<html>` wrapper and there is no `ToolTipManager` tuning anywhere in `ui/`, so Swing's 4000 ms dismiss applies — `28-REVIEW-3.md` WR-01, deferred. Routed to human verification. |

Condition 3 is why this report is `human_needed` rather than `passed`. The condition is met in code —
the string exists, is wired to the one assignment site, and is pinned by a test. Whether an operator
can actually READ the two conditioned clauses before the tooltip vanishes is a visual property no
grep settles, and it is the condition the maintainer's acceptance rests on. I am not reopening
`D-28-09`; I am flagging the one part of its own conditions that only a human at a running Burp can
close.

### Record integrity — round 2's gap 2, and the new overclaim round 3 committed

This is where round 2 failed the phase, so I treated every claim here as guilty until measured.

**Half 1 — the probe claim for detail line (4).** Round 2 measured `grep -o "Payload Used"` on
`AiScanCheckDetailCookieCarrierTest.kt` returning **0** while two records named that class as the
committed probe for that line. At HEAD it returns **8**, and the class carries four named tests
(`cookiePayloadLineIsStrippedUnderStrict`, `...UnderBalanced`, `cookiePayloadLineSurvivesUnderOff`,
`urlParamPayloadLineSurvivesStrict_attributionControl`). Class count moved 10 -> 16; all 16 green in
my own run.

The repair took the harder road `D-28-11` asked for — **make the claim true, don't retract it** — and,
more to the point, it did not smooth the asymmetry while doing so. A 15-line banner comment above the
new tests states that a green run here is NOT a leak closure, because `AiScanCheck` sources payloads
from the static `getQuickPayloads` table and interpolates no insertion-point value. I verified the
fixture backs that: `PAYLOAD` carries no trace of `DETAIL_SENTINEL`, so the claim is honest by
construction rather than by assertion. Clause (d) of the registry says the same in the record.

**Half 2 — the route-2 fail-open set.** Now recorded, pinned, and bounded.
`theRouteTwoGateIsFailOpenForTheseCookieCapableTypes` asserts `isCookieInsertionPoint` returns FALSE
for `HEADER`, `USER_PROVIDED`, `EXTENSION_PROVIDED` and `UNKNOWN`, and that under STRICT each still
renders `**Original Value:** <sentinel>` verbatim — with an assertion message that says in as many
words that a GREEN run records a residual's width and is NOT evidence of correct behaviour.
`theInsertionPointTypeEnumPopulationIsTheOneTheResidualWasMeasuredAgainst` pins the 17-member
population and both member names, so a Burp API bump turns the pin red instead of silently widening
the residual. The false KDoc premise round 2 falsified by `javap` is corrected at source
(`AiScanCheckDetailCookieCarrierTest.kt:400-419`), naming the measurement and separating the mock-only
null arm from the real `EXTENSION_PROVIDED` arm. Clause (e) records WIDENING WAS CONSIDERED AND NOT
TAKEN with four reasons.

**The new overclaim — round 3's own.** `28-REVIEW-3.md` WR-02/WR-03 found round 3 had committed the
absolute `There is no read-time pass over it` at four record sites plus the companion `provably` at
two. It is false: `McpTool.kt:45` and `:78` wrap every tool result in `McpToolContext.redactIfNeeded`,
which calls `Redaction.apply(raw, RedactionPolicy.fromMode(privacyMode), …)` unconditionally at
`McpToolContext.kt:67`. Round 4 (plan 28-09) retired both. Re-measured at HEAD by this verifier:

- `no read-time pass` → **0** occurrences in `AiScanCheck.kt`, `SettingsPanelInit.kt`,
  `CookieCarrierInventoryTest.kt`, and row 315 of `26-SECURITY.md` (was 1 each).
- `provably` → **0** in `AiScanCheck.kt` and `CookieCarrierInventoryTest.kt` (was 1 each).

More important than the deletions: **I checked every replacement citation against the tree and all of
them are accurate.** `McpTool.kt:45`/`:78` are exactly the two `context.redactIfNeeded(...)` calls;
`McpToolContext.kt:59` is exactly `fun redactIfNeeded`. The narrowed claim is neither the old absolute
nor its mirror image — clause (a) now says the pass runs but is not TYPE-keyed, names the three cookie
rules and the framing each needs, and ends with the conditional `UNLESS one of the generic,
non-cookie rules happens to match the value's own shape … and the width of the condition is
unmeasured`. That is the honest shape. The `provably` replacement is better still: it reads 28-05's
red probe at its **actual reach**, recording that `DETAIL_SENTINEL` was deliberately shaped with no
digits, no `=`, no metacharacters and no `cookie` token, so the probe bounds the type-keyed question
ONLY and says nothing about a JWT or base64 session value.

Both corrections are recorded AS corrections, naming `28-REVIEW-3.md` WR-02/WR-03, in all four sites —
and the retired phrases are deliberately not quoted anywhere, because the greps that enforce the
retirement are literal. That is a genuinely good piece of record hygiene, and it is the round's own
named pattern.

### Required Artifacts

| Artifact | Expected | Status | Details |
|---|---|---|---|
| `SettingsPanelInit.PRIVACY_MODE_TOOLTIP` | Operator-facing statement of the bound, referenced not inlined | ✓ VERIFIED (copy flagged) | `:54-58`; single assignment at `:96`; `internal` so the test reads the same symbol. Copy accuracy is WR-05, deferred — see Human Verification. |
| `PrivacyModeTooltipBoundTest` | Pins the three clauses + the exactly-one-site fact | ✓ VERIFIED | 4/4 green in my own run. `exactlyOnePrivacyModeTooltipAssignmentExistsInMainSourceAndItReferencesTheConstant` walks `src/main` rather than trusting a constant. (Its needle is literal — WR-06, deferred.) |
| `AiScanCheck.consolidateIssues` KDoc | Names the bound where the code makes it sticky | ✓ VERIFIED | `:100-161`, comment-only. `git diff -U0 <pre-28-07> HEAD -- AiScanCheck.kt` filtered to non-comment lines returns **0** across BOTH rounds — the "it is a note, not a control" must-have holds by measurement, not by assertion. |
| `AiScanCheckDetailCookieCarrierTest` | Non-vacuous route-2 probe incl. line (4) | ✓ VERIFIED (was ⚠️ PARTIAL) | 16/16. Four payload-line tests, fail-open residual pin, enum-population tripwire, corrected absent-type KDoc. |
| `CookieCarrierInventoryTest.ISSUE_DETAIL_CARRIER_DISPOSITION` | Residual enumeration, complete and current | ✓ VERIFIED (was ⚠️ PARTIAL) | Clauses (a)–(f) plus the round-4 `NARROWED 2026-08-28` block. 4/4 green. Clause (f) lists five open items and states the constant cannot be read as a closure. |
| `26-SECURITY.md` row 315 | Append-and-amend, prefixes intact, `AR-27-08` OPEN | ✓ VERIFIED | Four dated markers in date order; both digests re-assert; row head still `**NEW, OPEN, severity MEDIUM…**`; round-4 marker states `AR-27-08`'s OPEN status stands unchanged. |
| `AdaptivePayloadEngine.kt` | Byte-unchanged (SC4) | ✓ VERIFIED | Empty diff vs `ad2ca90`. |
| `.planning/REQUIREMENTS.md` | Byte-unchanged, PRIV-05 `- [ ]` | ✓ VERIFIED | `shasum -a 256` = `9b3219662ec0d007c1c82d64eed3ef2698bd306ce69f01205ac9bbc3f42fcfb4`; line 23 `- [ ]`; absent from `git status --porcelain`. |
| `28-VERIFICATION.md` (round-2 body) | Verifier's own findings not rewritten by the override | ✓ VERIFIED | `git diff a50dfe3^ a50dfe3` on this file shows ONLY `overrides_applied: 0 -> 1` plus the added `overrides:` block. `status`, `score`, `gaps`, `deferred` and the fenced template block untouched, and the file is absent from every later commit. |

### Key Link Verification

| From | To | Via | Status |
|---|---|---|---|
| `SettingsPanelInit.kt:96` | `PRIVACY_MODE_TOOLTIP` | `privacyMode.toolTipText = PRIVACY_MODE_TOOLTIP` — the only such assignment in `src/main` | ✓ WIRED |
| `PrivacyModeTooltipBoundTest` | `PRIVACY_MODE_TOOLTIP` | `internal` + friend test source set — the test reads the operator's string, not a copy | ✓ WIRED |
| `McpTool.kt:45`, `:78` | `McpToolContext.redactIfNeeded` (`:59`) | `context.redactIfNeeded(execute(...))` on every tool result | ✓ WIRED — and this is the link whose EXISTENCE round 4 exists to admit |
| `McpToolContext.kt:67` | `Redaction.apply` | `RedactionPolicy.fromMode(privacyMode)`, unconditional, current mode | ✓ WIRED (not type-keyed — the residual, now stated conditionally) |
| `AiScanCheck.kt:388`, `:392` | `sanitizeCookiePointText` | Raw-string interpolation in `buildDetail` | ✓ WIRED (unchanged; 0 non-comment lines moved in rounds 3–4) |
| `ScannerIssueSupport.kt:221` | `sanitizeRenderedPayload` | Direct call in the detail-line accumulator | ✓ WIRED (unchanged) |
| `InjectionPointExtractor.kt:37` | `Redaction.isCookieParameterType` | Shared predicate, RAW value passed through on purpose | ✓ WIRED |

### Data-Flow Trace (Level 4)

| Artifact | Data variable | Source | Produces real data | Status |
|---|---|---|---|---|
| `PRIVACY_MODE_TOOLTIP` | the tooltip string | A `const val` read by both the panel and the test | Yes — same symbol both sides | ✓ FLOWING |
| `buildDetail` | `insertionPoint.baseValue()`, `type()` | Real Montoya `AuditInsertionPoint` | Yes | ✓ FLOWING |
| `scanner_issues` blob | stored `AuditIssue.detail()` | `api.siteMap().issues()` -> `detail = detail()` -> `redactIfNeeded` | Yes — and the read-time pass DOES run over it | ⚠️ STALE-BY-CONSTRUCTION on the OFF-then-STRICT path — the accepted `D-28-09` residual, now stated conditionally at all four record sites |

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|---|---|---|---|
| Phase-28 test classes green | `./gradlew test --tests '*IssueDetailCookieCarrierTest' … (7 classes)` | `IssueDetailCookieCarrierTest` 21, `AiScanCheckDetailCookieCarrierTest` 16, `CookieCarrierInventoryTest` 4, `CookieRouteDispositionTest` 7, `PrivacyModeTooltipBoundTest` 4, `InjectionPointExtractorTest` 12, `EvidenceTailReachTest` 2 — all 0 failures / 0 errors | ✓ PASS |
| Full suite, this verifier's own run | `JAVA_HOME=$(…21) ./gradlew test`, aggregated over `build/test-results/test/TEST-*.xml` | **181 classes, 1308 tests, 0 failures, 0 errors, 1 skipped** | ✓ PASS |
| `no read-time pass` retired | `grep -o` at all four sites | 0, 0, 0, 0 | ✓ PASS |
| `provably` retired | `grep -c` at both sites | 0, 0 | ✓ PASS |
| Replacement citations accurate | `grep -n redactIfNeeded` on `McpTool.kt` / `McpToolContext.kt` | `:45`, `:78`; `fun redactIfNeeded` at `:59`; `Redaction.apply` at `:67` | ✓ PASS |
| Row-315 digests after round 4 | `LC_ALL=C awk 'NR==315{printf "%s", substr($0,1,N)}' \| shasum -a 256` | `8dc326ac…7ae2e` (8693) and `5316a971…0ed72` (16071) | ✓ PASS |
| Row-315 marker order | `grep -o 'by plan 28-0[0-9]'` on the row | 28-03, 28-06, 28-08, 28-09 (fourth as `NARROWED`) | ✓ PASS |
| `threats_open` recomputed | the register's own documented awk, re-run | 46 rows, 46 closed, open=0 — matches `threats_open: 0` | ✓ PASS |
| Route-2 line (4) coverage | `grep -o "Payload Used"` | **8** (was 0 — round-2 gap 2 half 1) | ✓ PASS |
| `AiScanCheck.kt` behaviour frozen | `git diff -U0 <pre-28-07> HEAD` filtered to non-comment lines | **0** | ✓ PASS |
| `PRIVACY_MODE_TOOLTIP` value frozen in round 4 | `git diff -U0 bdd8882 HEAD -- SettingsPanelInit.kt`, non-comment | **0** | ✓ PASS |
| `28-VERIFICATION.md` not rewritten | `git diff a50dfe3^ a50dfe3` on that file | only the additive `overrides:` block | ✓ PASS |
| Tooltip widget facts (WR-01 premise) | `grep -rn 'ToolTipManager\|<html>' src/main/kotlin/…/ui/` | no `ToolTipManager` anywhere; no `<html>` on this tooltip | ✓ PASS (confirms WR-01) |

`./gradlew check` was not run as a gate — RED for the maintainer-accepted coverage-floor reason, as
in round 2. `ktlintCheck` and `detekt` were reported green by the orchestrator and rounds 3–4 changed
no non-comment main-source line, so no new static-analysis surface was created.

### Probe Execution

No `scripts/*/tests/probe-*.sh` exists in this repository and no phase-28 plan declares one. The
phase's probe discipline is the JUnit red-probe protocol (SC3), unchanged by rounds 3–4, which are
comment-and-record rounds.

### Requirements Coverage

| Requirement | Source plans | Status | Evidence |
|---|---|---|---|
| PRIV-05 — "Cookie values do not reach an AI backend in STRICT or BALANCED mode **by any path**" | 28-01 … 28-09 (all nine declare it) | ✗ BLOCKED — **and correctly recorded as such by standing decision `D-28-04`** | Stays `- [ ]`. Verified byte-unchanged: `sha256 9b32196…fcfb4`, line 23 `- [ ]`, clean `git status`. `D-28-04` is engaged rather than assumed here: the requirement says "by any path", and clause (f) of the carrier registry lists five paths still open — `AR-28-01`, `AR-27-08` itself, the accepted write/read bound, the route-2 fail-open set, and the absent repo-wide producer gate. Ticking it would be the exact overclaim this phase series exists to correct. |

No orphaned requirements: `grep -nE '\| *28 *\|' .planning/REQUIREMENTS.md` returns nothing, PRIV-05's
mapping row points at phase 21, and all nine plans claim it.

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|---|---|---|---|---|
| — | — | `TBD` / `FIXME` / `XXX` / `TODO` / `HACK` / `PLACEHOLDER` across all seven phase-modified source files | — | **None found.** Debt-marker gate clean. |
| `AiScanCheckDetailCookieCarrierTest.kt` | ~447 | `theRouteTwoGateIsFailOpenForTheseCookieCapableTypes` calls a **pre-redaction** string (`detailFor`) the residual's "OBSERVABLE width", while the file's SC1 assertions deliberately use `redactedDetailFor` | ⚠️ Warning | `28-REVIEW-3.md` WR-04, **deliberately deferred and owned** in `28-09-SUMMARY.md`. Same species as WR-03 and one helper call from being true. Not a live hole — the residual it records is real and the assertion is green either way — but the word "OBSERVABLE" is doing more work than the helper supports. Correct disposition given round 4's charter (that file was outside `files_modified`, and swapping the helper moves what the pinned residual measures). |
| `SettingsPanelInit.kt` | 56 | `Applies from now on, not retroactively.` is an unqualified claim the product contradicts on the dominant path | ⚠️ Warning | `28-REVIEW-3.md` WR-05, deferred and owned. Errs PESSIMISTIC (safe direction), and `FORWARD_ONLY_CLAUSE` currently pins the wording so the test defends it. Routed to human verification, not filed as a gap. |
| `PrivacyModeTooltipBoundTest.kt` | 86-93 | Source-scan needle is the literal `privacyMode.toolTipText`; a `setToolTipText(...)` call would be invisible to it | ℹ️ Info | `28-REVIEW-3.md` WR-06, deferred and owned. Claim-scope defect in a tripwire, not a live hole — there is exactly one assignment today and it uses the property form. |
| `CookieRouteDispositionTest.kt` | 378 | `trimmed.startsWith("*")` retained in `matchingCodeLinesIn` | ⚠️ Warning | Carried over from round 2, unchanged, and not re-litigated here. |

### Deferred Items

None deferrable within the milestone — phase 28 is its last phase, so no later phase addresses
anything here. `28-REVIEW-3.md`'s WR-01, WR-04, WR-05 and WR-06 are **deferred with named owners and
recorded reasons** in `28-09-SUMMARY.md` (WR-01/05/06 form one tooltip work item; WR-04 is a one-line
helper swap). I verified each is present in that SUMMARY with its reason rather than dropped. They
are treated here as known-and-owned, not as new gaps — but WR-01 and WR-05 both land on the artifact
that discharges `D-28-10` condition 3, which is why they surface as human-verification items instead
of disappearing.

### Human Verification Required

#### 1. The `D-28-10` bound is actually readable at the privacy-mode selector

**Test:** In a live Burp, open Settings, hover the privacy-mode selector, and read the tooltip to its
end without moving the pointer.
**Expected:** All three clauses legible, including the two the acceptance is conditioned on:
`Applies from now on, not retroactively.` and `Scanner findings already recorded keep the values they
were built with; re-scanning does not rewrite them.`
**Why human:** Visual/widget behaviour. Measured at source here: the constant is 206 plain characters
with no `<html>` wrapper, and there is no `ToolTipManager` tuning anywhere in `ui/`, so Swing's
default 4000 ms dismiss and single-line rendering apply — `28-REVIEW-3.md` WR-01. The string is
present, wired and test-pinned; whether an operator READS it is the one part of condition 3 no grep
can settle, and `D-28-09` rests on it. A persistent `SubtleNotice` already sits above the same
control if the answer is no.

#### 2. Decide the scope of the tooltip's second sentence before ship

**Test:** Read `Applies from now on, not retroactively.` as an operator and decide whether to keep the
blanket wording or narrow it to the scanner-detail lines it is actually about.
**Expected:** A recorded decision either way.
**Why human:** Operator-copy judgement (`28-REVIEW-3.md` WR-05, deferred). The sentence generalises a
narrow residual: `McpToolContext.redactIfNeeded` applies the CURRENT policy to already-captured
traffic at send time, so switching to STRICT *is* retroactive for the dominant path. It errs
pessimistic, and `FORWARD_ONLY_CLAUSE` pins the exact wording — so correcting it is a copy change
plus a test change together, which is precisely why round 4 declined to do it silently.

### Gaps Summary

**No gaps.** Both round-2 gaps are closed, and so is the new overclaim round 3 committed while
closing them.

What I want on the record about how they closed, because the two are different in kind:

**Gap 1 (SC1) closed by DISPOSITION, not by code.** A human maintainer accepted the write-time/read-time
bound as a named residual on 2026-08-28, and I verified all four conditions of that acceptance
discharged in the tree rather than trusting 28-08's own precondition gate. The mechanism round 2
measured is unchanged and I did not re-open it. Condition 3 is met in code but carries a visual
question that only a human can close — hence `human_needed` rather than `passed`.

**Gap 2 closed by making the tree match the prose, in the harder direction.** The probe claim for
detail line (4) was made TRUE (0 -> 8 references, 4 named tests, class 10 -> 16) instead of retracted,
and the DEFENCE-IN-DEPTH asymmetry was preserved in the same breath rather than smoothed. The
fail-open type set is recorded, pinned, and bounded by an enum-population tripwire, with widening
explicitly CONSIDERED AND NOT TAKEN for four stated reasons.

**And round 4 is the part that most deserves credit.** Round 3's repair introduced a fresh absolute —
`There is no read-time pass over it` — at four sites; the round-3 review caught it, and round 4
retired it along with `provably`, replaced both with claims I independently verified are accurate
down to the cited line numbers, and recorded the change AS a correction naming the reviewing artifact.
The replacement is neither the old absolute nor its mirror image: it states that the read-time pass
exists and runs, that it is not type-keyed, and that the residual's width for a realistic
high-entropy value is **unmeasured** rather than zero or large. That last word is the difference
between this round and the four before it.

**Named follow-on, not a gap:** the residual's width for a JWT- or base64-shaped cookie value under
STRICT's generic rules is recorded as UNMEASURED at every site. It is measurable — a test feeding such
a value through `Redaction.apply` would settle it — and it bears directly on how severe the accepted
residual actually is. It belongs with the read-time-layer work `D-28-09` sent to its own phase, and it
is correctly named rather than assumed in either direction.

Not re-reported, per standing decisions: `D-28-06`'s absent repo-wide producer gate (named residual,
clause (f)); `AR-28-01`'s uncontrolled `Evidence:` tail; route 2's line (4) as defence in depth;
PRIV-05 staying `- [ ]` under `D-28-04`; and `./gradlew check`'s accepted red.

---

_Verified: 2026-08-28_
_Verifier: Claude (gsd-verifier), round 3 — covering gap-closure plans 28-07, 28-08 and 28-09_
