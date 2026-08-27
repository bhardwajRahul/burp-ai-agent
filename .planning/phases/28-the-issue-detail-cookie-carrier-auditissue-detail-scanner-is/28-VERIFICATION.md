---
phase: 28-the-issue-detail-cookie-carrier-auditissue-detail-scanner-is
verified: 2026-08-27T22:40:00Z
status: gaps_found
score: 5/6 must-haves verified
behavior_unverified: 0
overrides_applied: 0
re_verification:
  previous_status: gaps_found
  previous_score: 5/6
  gaps_closed:
    - "SC1 route 1 (CR-01) — the `Payload Used:` line of `ScannerIssueSupport.buildActiveIssueDetailLines` re-leaked the value the line above had just stripped. Closed by `sanitizeRenderedPayload` (`ScannerIssueSupport.kt:127-138`), wired at `:221`, type-keyed on `InjectionType.COOKIE` + `policy.stripCookies`."
    - "SC1 route 2 (CR-02) — `AiScanCheck.buildDetail` read no privacy mode at all and leaked identically in STRICT, BALANCED and OFF. Closed by `sanitizeCookiePointText` / `isCookieInsertionPoint` (`AiScanCheck.kt:459-486`), wired at `:388` and `:392`."
    - "The wrong-predicate hazard I flagged as unmeasured — `Redaction.isCookieParameterType` compares against the string `COOKIE` and is FALSE for `PARAM_COOKIE`. Measured on the record by 28-05 red probe 1 and pinned by `theSharedStringNamePredicateDoesNotRecogniseTheInsertionPointCookieConstant`."
    - "The fixture-only SC1 proof (round-1 `coincidental_reliance_items[0]`). `IssueDetailCookieCarrierTest.PAYLOAD` is now DERIVED at construction from `PayloadGenerator().generateContextAwarePayloads(VulnClass.SQLI, DETAIL_SENTINEL, N)` with a non-vacuity assertion at `:728-739`. The hand-typed `benign-probe-payload` is gone."
    - "The `AR-27-08` closure sentence I measured false. Row 315 was re-amended append-and-amend by 28-06; the row now reads STAYS OPEN, NARROWED, and clause (a) states in its own words why the first closure was premature."
    - "`CookieCarrierInventoryTest.kt:476`'s `BOTH NOW CONTROLLED` claim — amended in place at `:495` under a dated marker rather than rewritten."
    - "28-03's RUN 2 recording failure (round-1 gap 2, `partial`). `28-03-SUMMARY.md:542-560` now carries both raw outputs and states plainly that the gate RAN LATE, after the commits it was written to block."
  gaps_remaining:
    - "SC1 — still FAILED, but on a NARROWER and DIFFERENT mechanism than round 1. Round 1: the value leaked under a pure STRICT scan. Round 2: every write under STRICT/BALANCED is now controlled and measurably clean; what remains is the TEMPORAL bound — a detail string rendered while `privacyMode = OFF` is stored verbatim and emitted verbatim on a later STRICT read."
  regressions: []
gaps:
  - truth: "SC1 — A COOKIE-typed injection point's `originalValue` does not appear in the `scanner_issues` tool result in STRICT or BALANCED"
    status: partial
    reason: >-
      ADJUDICATION (b). Both controls are WRITE-TIME SNAPSHOTS. `AuditIssue.detail()` is an
      immutable string stored in Burp's site map (`ActiveAiScanner.kt:1285` `api.siteMap().add(issue)`;
      route 2 via the `AuditResult` Burp files itself) and read back by `scanner_issues`
      (`McpToolExecutorImpl.kt:605` -> `api.siteMap().issues()`; `Serialization.kt:14`
      `detail = detail()`). An issue built while `privacyMode = OFF` bakes the raw cookie value into
      that string; a later read under STRICT emits it verbatim, and `Redaction.apply` cannot rescue
      it. SC1's wording binds to the TOOL RESULT, not to the write site, so a STRICT read that
      returns the value falsifies it as written. `AR-27-08`'s own register sentence — "the value
      reaches the `scanner_issues` tool result through `AuditIssue.detail()` and SURVIVES
      `Redaction.apply` in STRICT and in BALANCED alike, emitted verbatim" — is therefore still
      literally true at HEAD along this path, which is why this is a surviving instance of the
      chartered finding rather than a new, separately-owned one. MEASURED, not inferred: 28-05's own
      red probe 1 (`28-05-SUMMARY.md:224`) recorded that when the write gate does not fire, the
      sentinel is PRESENT in the REDACTED detail field under STRICT. The OFF path reaches the
      redactor in exactly that state. `AiScanCheck.consolidateIssues` (`:101-112`) returns
      `KEEP_EXISTING` on matching canonical name + normalized URL, so re-scanning under STRICT does
      NOT replace the stale OFF-built issue.
      SCOPE OF THE FAILURE, STATED SO IT IS NOT OVER-READ. For every issue PRODUCED under STRICT or
      BALANCED — which includes the entire default posture, since `AgentSettings.kt:493` defaults
      `privacyMode = BALANCED` — SC1 now holds, measurably, across all four detail lines of both
      producers. The residual requires a deliberate `OFF` scan followed by a mode switch.
    artifacts:
      - path: "src/main/kotlin/com/six2dez/burp/aiagent/scanner/AiScanCheck.kt"
        issue: >-
          `buildDetail` (`:356-404`) reads `getSettings().privacyMode` once, at construction, and
          bakes the result into an immutable String. `consolidateIssues` (`:101-112`) then returns
          `KEEP_EXISTING`, making the stale value sticky against re-scan. Neither the function KDoc
          (`:330-355`) nor the companion KDoc (`:416-486`) records that the placement makes the
          control non-retroactive.
      - path: "src/main/kotlin/com/six2dez/burp/aiagent/scanner/ScannerIssueSupport.kt"
        issue: >-
          Same class of defect on route 1. `:150-157` justifies the write-site placement purely on
          `InjectionType` availability — correctly — and never states the consequence: the decision
          is taken once, at write time, and no later policy change revisits it.
      - path: "src/test/kotlin/com/six2dez/burp/aiagent/redact/CookieCarrierInventoryTest.kt"
        issue: >-
          `ISSUE_DETAIL_CARRIER_DISPOSITION` (`:700-768`) has a clause headed "STILL OPEN, NAMED SO
          NOBODY READS THIS AS A CLOSURE" that enumerates the `Evidence:` line (AR-28-01), AR-27-08's
          own open status, and the absent repo-wide gate. The temporal bound is not in it. An
          enumeration that declares itself the place residuals are named, and omits one, reads as a
          closure of what it omits.
      - path: ".planning/phases/26-coverage-static-analysis-debt-docs/26-SECURITY.md"
        issue: >-
          Row 315 clause (d) — "WHAT THIS AMENDMENT DOES NOT COVER, BY IDENTIFIER" — is likewise
          exhaustive by construction and omits the temporal bound.
    missing:
      - "A DISPOSITION for the write-time/read-time gap, chosen and recorded. Either (i) a second, shape-keyed line-prefix scrub against the LIVE policy at the emission boundary (`Serialization.kt` / `McpToolExecutorImpl` `scanner_issues`), documented explicitly as a SECOND layer so a future reader does not delete the type-keyed write-site gate as redundant — note `IssueUtils.formatIssueDetailHtml` joins route 1's lines with `<br>`, so that route splits on `<br>`, not `\\n`; or (ii) an explicit maintainer acceptance recorded as a named residual."
      - "If (ii): the temporal bound named in `ISSUE_DETAIL_CARRIER_DISPOSITION`'s STILL OPEN clause and in row 315's clause (d), a note at `AiScanCheck.consolidateIssues`, and operator-facing text next to the privacy-mode selector — changing the mode does not re-render issues already recorded. `SettingsPanelInit.kt:58`'s tooltip currently reads 'Controls how traffic is redacted before sending to a model.', which an operator reads as retroactive."
  - truth: "The phase's own record enumerates its residuals exhaustively and names a committed probe only where one exists"
    status: failed
    reason: >-
      D-28-08's rule for this phase is that prose asserting a closure that does not hold must be
      corrected, not left standing. Three statements at HEAD fail that rule. This is a
      record-integrity gap, filed separately from SC1 because it stands regardless of how SC1 is
      dispositioned — even an accepted-residual ruling on SC1 requires these repaired.
    artifacts:
      - path: "src/test/kotlin/com/six2dez/burp/aiagent/redact/CookieCarrierInventoryTest.kt"
        issue: >-
          (1) `:751-754` — "COMMITTED PROBES: ... AiScanCheckDetailCookieCarrierTest for (3) AND (4)".
          There is no probe for (4). `grep -n "Payload Used"
          src/test/kotlin/.../AiScanCheckDetailCookieCarrierTest.kt` returns ZERO lines, KDoc
          included; re-run by this verifier. Not one of that file's 10 tests reads the
          `**Payload Used:**` line under any mode, so deleting `sanitizeCookiePointText` from
          `AiScanCheck.kt:392` keeps the whole 1298-test suite green. The register is right that (4)
          is defence in depth rather than a measured carrier; it is wrong that a probe holds it.
          (2) `:735-767` — the STILL OPEN clause omits BOTH the temporal bound (gap 1) and the
          fail-open insertion-point-type set (below).
    missing:
      - "Either three assertions on route 2's `**Payload Used:**` line (STRICT stripped / OFF survives / PARAM_URL attribution control — the fixture already exists), or a correction of the register to say (4) is UNPROBED defence in depth."
      - "Record the route-2 fail-open type set. MEASURED at source by this verifier via `javap` on the resolved `montoya-api-2026.2.jar`: `AuditInsertionPoint.type()` is a DEFAULT method whose entire body is `getstatic AuditInsertionPointType.EXTENSION_PROVIDED; areturn`, and the enum also carries `USER_PROVIDED` and `HEADER`. Unlike route 1's `InjectionType`, whose only cookie-capable member IS `COOKIE` (so D-28-01's pass-through was safe by construction), this enum has cookie-capable non-`PARAM_COOKIE` members. Any point from `registerAuditInsertionPointProvider` or the public `auditInsertionPoint(name, request, start, end)` factory reaches `AiScanCheck` (`App.kt:215`, `PER_INSERTION_POINT`) reporting `EXTENSION_PROVIDED`, and `buildDetail` renders its `baseValue()` verbatim under STRICT. Either widen the predicate to a set, or name the residual — but `anAbsentInsertionPointTypeDoesNotThrowAndPassesThrough`'s KDoc currently asserts the opposite premise ('a real Burp implementation may not override it' -> null), which is false against the shipped jar."
deferred: []
behavior_unverified_items: []
coincidental_reliance_items: []
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
