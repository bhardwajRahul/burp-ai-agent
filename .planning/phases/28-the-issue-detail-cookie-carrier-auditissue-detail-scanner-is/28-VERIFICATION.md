---
phase: 28-the-issue-detail-cookie-carrier-auditissue-detail-scanner-is
verified: 2026-08-27T00:00:00Z
status: gaps_found
score: 5/6 must-haves verified
behavior_unverified: 0
overrides_applied: 0
re_verification: null
gaps:
  - truth: "SC1 — a COOKIE-typed injection point's `originalValue` does not appear in the `scanner_issues` tool result in STRICT or BALANCED"
    status: failed
    reason: >-
      MEASURED FALSE by this verifier, not inferred. The control at
      `ScannerIssueSupport.sanitizeInjectionPointValue` guards exactly ONE of the nine detail lines
      the same function emits. Two independent routes carry the same cookie bytes to the same MCP
      tool result under STRICT and BALANCED. Route 1 was reproduced with a temporary red probe on the
      phase's own fixture and its verbatim failure messages are recorded below; route 2 has no
      privacy code in its file at all.
    artifacts:
      - path: "src/main/kotlin/com/six2dez/burp/aiagent/scanner/ScannerIssueSupport.kt"
        issue: >-
          Line 121 writes `"  Payload Used: ${payload.value.take(PAYLOAD_VALUE_MAX_CHARS)}"` with NO
          policy argument, one line below the sanitized line 120. For a COOKIE point the payload is
          DERIVED FROM the cookie value: `ActiveAiScanner.kt:512` passes
          `target.injectionPoint.originalValue` into `PayloadGenerator.generateContextAwarePayloads`,
          which builds `"$originalValue' AND '1'='1"` at `PayloadGenerator.kt:782` (also :762, :771,
          :791) with no injection-type filter. The KDoc premise at `ScannerIssueSupport.kt:32-33`
          ("the payload is agent-authored, not operator traffic") is false for context-aware
          payloads. The KDoc claim at `ScannerIssueSupport.kt:74-75` ("THE ONLY PRODUCER OF THE
          ACTIVE-SCAN ISSUE DETAIL LINES IN THE REPOSITORY") is false — see AiScanCheck.kt below.
      - path: "src/main/kotlin/com/six2dez/burp/aiagent/scanner/AiScanCheck.kt"
        issue: >-
          A SECOND, entirely uncontrolled active-scan `AuditIssue` detail producer. `:353` writes
          `**Original Value:** ${insertionPoint.baseValue().take(100)}` and `:357` writes
          `${payload.value.take(500)}`; `buildDetail` is handed to `AuditIssue.auditIssue(...)` at
          `:250`. `grep -n "Redaction|PrivacyMode|RedactionPolicy|sanitize|privacyMode"` over the file
          returns NOTHING — the file reads no privacy mode, so it behaves identically in STRICT,
          BALANCED and OFF. `doCheck` (`:34-93`) applies no insertion-point TYPE filter, so
          `PARAM_COOKIE` insertion points reach `:353`. LIVE-REGISTERED at `App.kt:215`
          (`registerActiveScanCheck(aiScanCheck, ScanCheckType.PER_INSERTION_POINT)`) behind the same
          `settings.activeAiEnabled` opt-in that gates AR-27-08 itself. Reaches the tool via
          `McpToolExecutorImpl.kt:604` -> `api.siteMap().issues()`. NOT MENTIONED ANYWHERE in
          `.planning/phases/28-*/28-0*-PLAN.md` or `28-0*-SUMMARY.md`.
      - path: "src/test/kotlin/com/six2dez/burp/aiagent/scanner/IssueDetailCookieCarrierTest.kt"
        issue: >-
          The green SC1 result is FIXTURE-DEPENDENT. `PAYLOAD.value` is hardcoded
          `"benign-probe-payload"` at `:544` while the fixture is labelled `VulnClass.SQLI` — a
          payload no SQLI generator would produce for that value. The advertised repository-wide
          "single-producer gate" does not exist: `originalValueRenderedFor` (`:625-632`) filters
          `detailLinesFor(point, mode)`, the list `buildActiveIssueDetailLines` ITSELF returned, and
          is structurally incapable of seeing another file.
      - path: ".planning/phases/26-coverage-static-analysis-debt-docs/26-SECURITY.md"
        issue: >-
          The amended `AR-27-08` row (line 315) asserts "A COOKIE-typed injection point's
          `originalValue` no longer reaches `AuditIssue.detail()` under STRICT or BALANCED" — a
          closure sentence measured false above. Its `SCOPE: ONE LINE, NOT THE BLOB` clause names
          only the `Evidence` line as the other uncontrolled line; it is silent on the `Payload
          Used:` line and on `AiScanCheck` entirely. This is the register-wider-than-the-control
          overclaim the file's own T-26-02-01 history exists to correct.
      - path: "src/test/kotlin/com/six2dez/burp/aiagent/redact/CookieCarrierInventoryTest.kt"
        issue: >-
          `:407` records the `INJECTION_EXTRACTOR/PARAMETER_LIST` entry as "TWO CONSUMERS, BOTH READ,
          AND BOTH NOW CONTROLLED". Consumer 2's own detail block emits the uncontrolled `Payload
          Used:` line carrying the same bytes, so "controlled" is true of one line and false of the
          block.
    missing:
      - "A control on the `Payload Used:` line for COOKIE-typed points (or a payload-side control at `PayloadGenerator`), keyed on `InjectionType.COOKIE` + `policy.stripCookies` as line 120 already is."
      - "A control on `AiScanCheck.buildDetail` — it currently reads no privacy mode at all — or an explicit, recorded disposition for it."
      - "Replace the fixture-only SC1 proof: drive `IssueDetailCookieCarrierTest` with a payload built the way production builds it (`PayloadGenerator.generateContextAwarePayloads(VulnClass.SQLI, DETAIL_SENTINEL, ...)`) so the assertion can see route 1."
      - "A single-producer gate that actually scans `src/main/kotlin` for detail-line producers, instead of filtering the list one producer returned."
      - "Retract or scope-correct the closure sentence in the `AR-27-08` amended cell and the `BOTH NOW CONTROLLED` reason in `CookieCarrierInventoryTest.kt:407`."
      - "A measurement of the `AiScanCheck` route through `Redaction.apply` — not performed by this verifier; the shape (`**Original Value:** <value>` under JSON key `detail`) is the same rule-blindness class phase 27 measured, but that is an inference, not a measurement."
  - truth: "28-03 must_have — the PRIV-05 gate RUN 2 (post-completion, pre-commit) is executed and its RAW OUTPUTS are recorded in 28-03-SUMMARY.md under a heading distinct from the in-task run"
    status: partial
    reason: >-
      The distinct heading exists (`28-03-SUMMARY.md:542`) but is still a set of INSTRUCTIONS marked
      "OUTSTANDING — the phase commit is BLOCKED until RUN 2 is recorded here", with the literal text
      "Paste BOTH raw outputs under this heading, replacing this block." The phase commits landed
      anyway (through `723ae59`) without the block being replaced. The INVARIANT the gate protects
      does hold at HEAD — re-derived by this verifier — so this is a recording failure, not a
      requirements-file corruption.
    artifacts:
      - path: ".planning/phases/28-the-issue-detail-cookie-carrier-auditissue-detail-scanner-is/28-03-SUMMARY.md"
        issue: "Lines 542-580 still carry the pre-run instruction block instead of RUN 2's raw output."
    missing:
      - "Replace the RUN 2 block with the raw `shasum -a 256 .planning/REQUIREMENTS.md` and `grep -n 'PRIV-05' .planning/REQUIREMENTS.md` output taken after `phase.complete 28`."
deferred: []
behavior_unverified_items: []
coincidental_reliance_items:
  - truth: "SC1 (as tested green by the phase) — the COOKIE originalValue is absent from the STRICT/BALANCED serialized detail"
    reason: fixture-only
    harden: >-
      The green rests entirely on `IssueDetailCookieCarrierTest.PAYLOAD.value =
      \"benign-probe-payload\"` (:544). The production path derives the payload from the very value
      under test. Build the fixture payload through `PayloadGenerator` rather than hand-typing one.
      (Recorded for completeness — SC1 is FAILED, so this is diagnosis, not an advisory on a pass.)
---

# Phase 28: The Issue-Detail Cookie Carrier Verification Report

**Phase Goal:** Close `AR-27-08`. A COOKIE-typed injection point's value reaches the `scanner_issues`
MCP tool result through `AuditIssue.detail()` and survives `Redaction.apply` in STRICT and BALANCED,
emitted verbatim. This phase closes `AR-27-08` AND `InjectionPointExtractor.kt:29` together.

**Verified:** 2026-08-27
**Status:** gaps_found
**Re-verification:** No — initial verification
**Mode:** standard (no `mode:` on the ROADMAP phase; MVP rules dormant)

## Goal Achievement

The phase goal is **NOT achieved**. `AR-27-08` is not closed. The mechanism plan 28-01 built is, in
isolation, correct and well-proven — type-keyed on a closed enum, reading the live policy at the write
site, with a genuinely non-vacuous probe and a measured red probe. What failed is the *span* of the
control: it guards one of the nine detail lines the same function emits, and the register was amended
to say the route is closed while two routes carrying the same bytes remain open.

Success criteria 2, 3, 4, 5 and 6 are each independently verified and are good work. SC1 — the one
the goal sentence is about — is measured false.

### Observable Truths

| # | Truth (verbatim ROADMAP Success Criterion) | Status | Evidence |
|---|---|---|---|
| SC1 | A COOKIE-typed injection point's `originalValue` does not appear in the `scanner_issues` tool result in STRICT or BALANCED. Cookie NAMES may remain; VALUES must not. | ✗ **FAILED** | Falsified by measurement (below) and by a second, uncontrolled producer. See "SC1 — the measurement". |
| SC2 | Under `OFF` the value still appears — the fix is policy-driven, not an unconditional rewrite. | ✓ VERIFIED | `cookieOriginalValueSurvivesUnderOff` passes (run by this verifier). Gate is `policy.stripCookies && point.type == InjectionType.COOKIE` (`ScannerIssueSupport.kt:68`); `RedactionPolicy.fromMode` supplies `stripCookies=false` for OFF. |
| SC3 | A red probe reverting the control turns a NAMED assertion red, and the specific assertion and its failure message are recorded — not "the suite went red". | ✓ VERIFIED | `28-01-SUMMARY.md:123-305`. Designated assertion NAMED (`cookieOriginalValueIsStrippedUnderStrict`, `:146`), three mutations measured, every verbatim `org.opentest4j.AssertionFailedError` message recorded incl. the one mutation detected ONLY by a source-text pin, which the SUMMARY explicitly calls weaker rather than reporting reach it lacks. |
| SC4 | `InjectionPointExtractor.kt:29` is resolved in the same phase as the route, with its two consumers' differing dispositions preserved. | ✓ VERIFIED | Predicate now `Redaction.isCookieParameterType(it.type().name)` (`InjectionPointExtractor.kt:37`); shared predicate at `Redaction.kt:622` trims+uppercases, so the swap is value-preserving in the safe direction. `git diff --stat ad2ca90 HEAD -- .../AdaptivePayloadEngine.kt` is **empty** — byte-unchanged, machine-checked not asserted. Extractor still returns the RAW value (`:38`), so consumer 1 is not double-redacted. `CookieRouteDispositionTest` 5/5 pass. |
| SC5 | `26-SECURITY.md`'s `AR-27-08` row is amended — append-and-amend, prior text byte-prefix intact — and `threats_open` is recomputed rather than asserted. | ✓ VERIFIED | Independently re-derived: `sha256(first 3399 bytes)` of the row at `ad2ca90` and at HEAD both `c60cacb666505311afe4d919fdbfad038fb2b524700dc99ab71b6a7a90266129`. Dated supersession marker present, nothing deleted. Documented awk re-run by this verifier: raw output `0`, 46 `T-26-` rows scanned; `threats_open: 0` at line 198 matches. Counter population stated explicitly at lines 187-197 (AR- rows sit outside it at any severity). |
| SC6 | `ResponseAnalyzer`'s narrow transitive tail is examined in the same pass. | ✓ VERIFIED | `EvidenceTailReachTest` 2/2 pass; derives the cap set from source with a pin as drift tripwire. Roadmap's "capped at 80" corrected to the measured multiset **{80, 80, 60}** — confirmed at source by this verifier: `ResponseAnalyzer.kt:682 take(80)`, `:720 take(60)`, `:791 take(80)`. `AR-28-01` filed with id, DERIVED severity MEDIUM, named owner (maintainer) and named venue (phase 28 human UAT). Two-directional reach measurement where the negative case still asserts the analyzer fired. |

**Score: 5/6 truths verified (0 present, behavior-unverified).**

### SC1 — the measurement

Presence checks alone would have passed this. They were not sufficient, so the control's span was
measured directly. A temporary working-tree mutation replaced the test fixture's hand-typed payload
with the payload **production actually builds** for a string-context SQLI probe of a COOKIE point
(`PayloadGenerator.kt:782`, `"$originalValue' AND '1'='1"`), the class was run, and the file was
restored (`git status --porcelain src/` clean afterwards; class re-run green at 14/14).

Mutation: `IssueDetailCookieCarrierTest.kt:544`
`value = "benign-probe-payload"` → `value = "$DETAIL_SENTINEL' AND '1'='1"`

Result: **14 tests completed, 4 failed.** Verbatim:

```
cookieOriginalValueIsStrippedUnderStrict()
org.opentest4j.AssertionFailedError: STRICT: the COOKIE-typed injection point's originalValue
must be ABSENT from the serialized issue detail, but the sentinel
'apple-orange-basket-lantern' was present. ==> expected: <false> but was: <true>

cookieOriginalValueIsStrippedUnderBalanced()
org.opentest4j.AssertionFailedError: BALANCED: the COOKIE-typed injection point's originalValue
must be ABSENT from the serialized issue detail, but the sentinel
'apple-orange-basket-lantern' was present. ==> expected: <false> but was: <true>

theStrippedDetailFieldRetainsEverythingAfterTheControlPoint()
  actual: ...&nbsp;Original Value: [STRIPPED]<br>&nbsp;&nbsp;Payload Used:
          apple-orange-basket-lantern' AND ...
```

That `actual:` line is SC1's falsification in one string: the value stripped from `Original Value:`
survives verbatim on the next line, after `Redaction.apply`, in STRICT. The phase's own designated
red-probe assertion is the one that goes red — the assertion is sound; the fixture was chosen so it
could not fire.

Route 2 needs no probe: `AiScanCheck.kt` contains no privacy identifier at all.

### Plan-Frontmatter Must-Haves (additive to the roadmap SCs)

| Plan | Must-have | Status | Evidence |
|---|---|---|---|
| 28-01 | Key link: `ActiveAiScanner.createConfirmedIssue` calls `buildActiveIssueDetailLines`, no inline rebuild | ✓ VERIFIED | `ActiveAiScanner.kt:1237-1245`; zero inline detail accumulators remain in that file. |
| 28-01 | Key link: policy reaches the write site as `RedactionPolicy.fromMode(getSettings().privacyMode)` | ✓ VERIFIED | `ActiveAiScanner.kt:1244`; pinned by `theWriteSiteReadsTheLivePolicy`. |
| 28-01 | Key link: gate keys on `InjectionType.COOKIE`, never a rendered string shape | ✓ VERIFIED | `ScannerIssueSupport.kt:68`. |
| 28-01 | ATTRIBUTION / POSITIVE CONTROL / NON-VACUITY / CONTENT PRESERVATION | ✓ VERIFIED | All four named tests present and passing (14/14 run by this verifier). |
| 28-01 | LOCAL EVIDENCE INTACT — `requestResponses` byte-unchanged by the control | ✓ VERIFIED | `theRequestResponsesListIsNotAlteredByTheControl` passes; `ActiveAiScanner.kt:1281` list untouched. |
| 28-01 | "THE ONLY PRODUCER … IN THE REPOSITORY" (source claim + advertised single-producer gate) | ✗ **FAILED** | `grep -rn "Original Value" src/main/kotlin/` returns **two** producers. The gate at `:625-632` filters the producer's own return list. |
| 28-02 | SC4 both halves, NO DOUBLE REDACTION, ONE MARKER VOCABULARY | ✓ VERIFIED | `CookieRouteDispositionTest` 5/5. |
| 28-02 | COUNT RE-DERIVED, NOT RESTATED | ✓ VERIFIED | `Redaction.kt:613-616` deliberately declines to restate a number and points at `exactlyOneCookieTypePredicateExistsInMainSource`. |
| 28-02 | CORRECTION FAN-OUT COMPLETE (six prose sites) | ✓ VERIFIED | `git diff --stat ad2ca90 HEAD` shows all six files amended: `Redaction.kt`, `InjectionPointExtractor.kt`, `CookieCarrierInventoryTest.kt`, `CookieHeaderRuleOwnershipTest.kt`, `ParameterCarrierRedactionTest.kt`, plus the inventory key move. |
| 28-03 | SC6 + bound correction; AR-28-01 with id, severity, owner, venue | ✓ VERIFIED | See SC6 row. |
| 28-03 | PRIV-05 JUDGEMENT — explicit enumerated decision, REQUIREMENTS.md byte-unchanged, sha re-derived as a gate | ✓ VERIFIED | Re-derived by this verifier: `9b3219662ec0d007c1c82d64eed3ef2698bd306ce69f01205ac9bbc3f42fcfb4`, PRIV-05 line 23 `- [ ]`. Enumeration at `28-03-SUMMARY.md:515-539`. |
| 28-03 | PRIV-05 GATE RUNS TWICE, RUN 2 recorded post-completion | ⚠️ **PARTIAL** | Heading exists and is distinct, but still holds the pre-run instruction block. See gaps. |
| 28-03 | SCOPE HONESTY — "ONE LINE, NOT THE BLOB" stated where a reader meets it | ⚠️ PARTIAL | Stated in both `AR-27-08` and `AR-28-01` and load-bearing (grep count 0→1). But it enumerates only the `Evidence` line; the `Payload Used:` line in the same block and `AiScanCheck` are absent, so the clause under-states the residual it exists to disclose. |

### Required Artifacts

| Artifact | Expected | Status | Details |
|---|---|---|---|
| `src/main/kotlin/.../scanner/ScannerIssueSupport.kt` | Gains `buildActiveIssueDetailLines`, `sanitizeInjectionPointValue`, `ORIGINAL_VALUE_MAX_CHARS`, `INJECTION_VALUE_STRIPPED_MARKER` | ⚠️ **HOLLOW** | All four symbols exist, are substantive and wired. But the control's span is one line of nine; line 121 in the same function re-emits the bytes line 120 strips. Exists ✓ / substantive ✓ / wired ✓ / achieves the truth ✗. |
| `src/test/kotlin/.../scanner/IssueDetailCookieCarrierTest.kt` | New, 14 tests | ⚠️ FIXTURE-DEPENDENT | 759 lines, 14/14 pass. High quality and genuinely non-vacuous for the line it covers; blind to the adjacent line by fixture choice. |
| `src/main/kotlin/.../scanner/InjectionPointExtractor.kt` | Predicate converted | ✓ VERIFIED | `:37` calls the shared predicate; raw value preserved at `:38`; comment records D-28-02. |
| `src/main/kotlin/.../redact/Redaction.kt` | KDoc amended, zero executable change | ✓ VERIFIED | +22 lines, all KDoc (`:600-620`); `isCookieParameterType` body unchanged at `:622`. |
| `src/test/kotlin/.../scanner/CookieRouteDispositionTest.kt` | New | ✓ VERIFIED | 356 lines, 5/5 pass. |
| `src/test/kotlin/.../scanner/EvidenceTailReachTest.kt` | New | ✓ VERIFIED | 264 lines, 2/2 pass; derives caps from source. |
| `.planning/phases/26-.../26-SECURITY.md` | AR-27-08 amended, AR-28-01 appended, threats_open recomputed | ⚠️ **CONTENT DEFECT** | Mechanics all correct (byte-prefix, supersession marker, recomputed counter). The amended cell asserts a closure that is measured false. |
| `src/main/kotlin/.../scanner/AiScanCheck.kt` | *(not in any plan's `files_modified`)* | ✗ **UNENUMERATED PRODUCER** | Second uncontrolled detail producer; absent from every phase-28 artifact. |

### Key Link Verification

| From | To | Via | Status | Details |
|---|---|---|---|---|
| `ActiveAiScanner.createConfirmedIssue` | `ScannerIssueSupport.buildActiveIssueDetailLines` | direct call + `RedactionPolicy.fromMode(getSettings().privacyMode)` | ✓ WIRED | `:1237-1245` |
| `ScannerIssueSupport` | `AuditIssue.detail` | `IssueUtils.formatIssueDetailHtml` → `AuditIssue.auditIssue` → `api.siteMap().add` | ✓ WIRED | `:1246`, `:1272-1284` |
| `api.siteMap().issues()` | `scanner_issues` MCP tool result | `McpToolExecutorImpl.kt:602-618` | ✓ WIRED | Confirms both producers reach the tool. |
| `InjectionPointExtractor` | `Redaction.isCookieParameterType` | `filter { Redaction.isCookieParameterType(it.type().name) }` | ✓ WIRED | `:37` |
| `InjectionPointExtractor` → `AdaptivePayloadEngine` | raw value, own marker | `originalValue` unredacted at producer | ✓ WIRED | `:38`; engine byte-unchanged. |
| `AiScanCheck.buildDetail` | any privacy control | — | ✗ **NOT WIRED** | No `Redaction` / `PrivacyMode` / `RedactionPolicy` / `sanitize` identifier in the file. |
| `ScannerIssueSupport` line 121 (`Payload Used:`) | any privacy control | — | ✗ **NOT WIRED** | No policy argument on the payload branch. |

### Data-Flow Trace (Level 4)

| Artifact | Data variable | Source | Reaches `scanner_issues` under STRICT/BALANCED | Status |
|---|---|---|---|---|
| `ScannerIssueSupport.kt:120` | `sanitizeInjectionPointValue(point, policy)` | `InjectionPoint.originalValue`, gated | No — `[STRIPPED]` | ✓ CONTROLLED |
| `ScannerIssueSupport.kt:121` | `payload.value` | `PayloadGenerator.kt:762/771/782/791`, interpolated from `originalValue` | **Yes, verbatim** | ✗ FLOWING (measured) |
| `ScannerIssueSupport.kt:123` | `evidence` | `ResponseAnalyzer` matched substring | **Yes, all three modes** | ⚠️ FLOWING — filed as `AR-28-01` |
| `AiScanCheck.kt:353` | `insertionPoint.baseValue()` | Montoya cookie insertion point | **Yes** (route unmeasured through `Redaction.apply`) | ✗ FLOWING (uncontrolled) |
| `ActiveAiScanner.kt:1281` `requestResponses` | raw request | Burp-held traffic, by design | Local Burp UI only | ✓ INTENDED |

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|---|---|---|---|
| Phase-28 test classes pass at HEAD | `./gradlew test --tests IssueDetailCookieCarrierTest --tests CookieRouteDispositionTest --tests EvidenceTailReachTest` | 14/5/2, 0 failures, 0 errors | ✓ PASS |
| SC1 holds against a production-shaped payload | fixture mutation at `:544` → run → restore | **14 completed, 4 FAILED**; sentinel present in STRICT and BALANCED | ✗ **FAIL** |
| Restore is clean | `git status --porcelain src/` + re-run | empty; 14/14 green | ✓ PASS |
| `threats_open` re-derivation | documented awk from `26-SECURITY.md:8-10` | raw output `0`, 46 rows | ✓ PASS |
| `AR-27-08` byte-prefix preservation | `sha256` of first 3399 bytes, `ad2ca90` vs HEAD | identical digest | ✓ PASS |
| `AdaptivePayloadEngine` untouched | `git diff --stat ad2ca90 HEAD -- AdaptivePayloadEngine.kt` | empty | ✓ PASS |
| `REQUIREMENTS.md` gate | `shasum -a 256` + `grep -n PRIV-05` | `9b3219662ec0d007…`, line 23 `- [ ]` | ✓ PASS |
| Repo-wide detail-producer count | `grep -rn "Original Value" src/main/kotlin/` | **2** producers | ✗ FAIL (KDoc claims 1) |
| `AiScanCheck` privacy handling | `grep -n "Redaction\|PrivacyMode\|RedactionPolicy\|sanitize" AiScanCheck.kt` | no matches | ✗ FAIL |

Full-suite status taken from the orchestrator (1279 tests, 1 skipped, 1 known `RedactionTest`
wall-clock flake that passes in isolation and whose failing assertion is the fail-closed anti-vacuity
check, not the leak check). Not re-run — the workspace-suite budget was spent on the targeted classes
and the SC1 probe.

### Probe Execution

| Probe | Command | Result | Status |
|---|---|---|---|
| — | `find scripts -path '*/tests/probe-*.sh'` | none | N/A — no shell probes in this project; no phase-28 artifact declares one |

### Requirements Coverage

| Requirement | Source plan | Description | Status | Evidence |
|---|---|---|---|---|
| PRIV-05 | 28-01, 28-02, 28-03 (all three) | Cookie values do not reach an AI backend in STRICT or BALANCED **by any path** | ✗ **BLOCKED** — correctly left open | `REQUIREMENTS.md:23` remains `- [ ]`, file byte-unchanged (`9b3219662ec0d007…`). D-28-04 enumerates five open carriers. **The decision to leave it open is CONSISTENT with the code** — more so than the enumeration itself knows, since its premise "Closing `AR-27-08` closes ONE carrier" is false: AR-27-08 is not closed either. |

No orphaned requirements: `grep -E "Phase 28" .planning/REQUIREMENTS.md` maps only PRIV-05, which all
three plans claim.

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|---|---|---|---|---|
| `ScannerIssueSupport.kt` | 32-33 | KDoc premise false for context-aware payloads ("the payload is agent-authored, not operator traffic") | 🛑 Blocker | Documents away the exact leak on the adjacent line. |
| `ScannerIssueSupport.kt` | 74-75 | KDoc asserts a repository-wide property no committed test can check, and which is false | 🛑 Blocker | A future reader trusts a single-producer invariant that does not hold. |
| `CookieCarrierInventoryTest.kt` | 407 | "BOTH NOW CONTROLLED" — true of one line, false of the block | 🛑 Blocker | Registry now over-states coverage. |
| `26-SECURITY.md` | 315 | Register cell asserts route closure | 🛑 Blocker | The register-wider-than-the-control class T-26-02-01 already records three times. |
| `IssueDetailCookieCarrierTest.kt` | 544 | Fixture payload contradicts its own `VulnClass.SQLI` label | ⚠️ Warning | Makes the SC1 green unfalsifiable by the code path it names. |
| `28-03-SUMMARY.md` | 542-580 | Instruction block left in place of RUN 2 output | ⚠️ Warning | Declared outstanding rather than claimed done — honest, but undischarged past the commit. |

Debt-marker gate: `TBD` / `FIXME` / `XXX` / `TODO` / `HACK` / `PLACEHOLDER` scan across all ten files
changed by this phase returns **zero** matches. Clean.

### Human Verification Required

None routed here — the gaps are code-observable and take precedence. Two judgement calls belong to
the maintainer when planning gap closure:

1. Whether `AiScanCheck` should be controlled or explicitly dispositioned as a new `AR-` row (it is a
   different code path with the same opt-in precondition as AR-27-08).
2. Whether the `Payload Used:` line should be controlled at the render site (mirroring line 120) or at
   `PayloadGenerator` (which would also close the payload's presence in `ScanKnowledgeBase` and the
   `active_scan_confirmed` audit event) — the two have different blast radii.

### Gaps Summary

The phase did excellent work on five of its six criteria and on the discipline criteria in
particular: the append-and-amend prefix is byte-verified, `threats_open` is genuinely recomputed, the
`AdaptivePayloadEngine` no-op is proven by `git diff` rather than asserted, the roadmap's own "capped
at 80" claim is corrected to a derived {80, 80, 60}, the red probe is measured on three mutations
with verbatim messages, and PRIV-05 was correctly refused closure. The 28-01 mechanism itself is
sound.

The failure is span, and it is the same failure this phase-27/28 series exists to correct: a control
was applied to one line, and the records were then written as though the route were closed. The
project's own vocabulary is the right frame — plan 27-08 was praised for calling a measurement a
measurement rather than a mitigation; here a one-line mitigation is being called a route closure. The
`Payload Used:` occurrence is not a newly discovered surface: phase 27's own probe output at
`27-08-SUMMARY.md:297` printed `Original Value: <sentinel>` and `Payload Used: <sentinel>' AND '1'='1`
side by side, and the phase stripped one of the two occurrences out of that string.

Recommended shape for gap closure: control line 121 for COOKIE points, control or explicitly
disposition `AiScanCheck.buildDetail`, rebuild the SC1 fixture through `PayloadGenerator` so the
assertion can see route 1, replace the fake single-producer gate with a real `src/main/kotlin` scan,
and then correct the three prose sites that now assert closure. `AR-27-08` should stay OPEN with a
second append-and-amend recording why the first closure was premature — that record is worth more
than the closure would have been.

---

_Verified: 2026-08-27_
_Verifier: Claude (gsd-verifier)_
