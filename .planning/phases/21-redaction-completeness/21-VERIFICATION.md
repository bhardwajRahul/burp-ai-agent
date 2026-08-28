---
phase: 21-redaction-completeness
verified: 2026-08-13T08:41:15Z
status: passed
score: 9/9 must-haves verified (SC1-SC6 6/6; plan 21-19 3/3 — G-1/G-2 closed by plan 21-20, G-3 closed by measurement)
re_verified: 2026-08-28
behavior_unverified: 0
overrides_applied: 0
re_verification:
  round: 3
  previous_status: gaps_found
  previous_score: "8/9 re-verified (SC1-SC6 held 6/6; plan 21-19 2/3)"
  previous_verified: 2026-08-27
  gaps_closed:
    - "G-1 (record accuracy) — CONCERNS.md:65 headline amended in place and 21-19-SUMMARY.md lines 50/171 amended under one dated CORRECTION marker; residuals (a)/(b)/(c) attributed to 27-01, 27-04/27-11/27-14/27-17 and 27-10 respectively, with (c)'s admitter-vs-redactor fail-OPEN mechanism stated at both sites"
    - "G-2 (process) — 21-19 restored to ROADMAP.md (line 196, with the three residuals named) and to STATE.md (lines 239/240/250); phase-21 plan count 18 -> 19 -> 20; the omission recorded as a CAUSE at ROADMAP.md:198-205, not silently inserted"
    - "G-3 (live blocker) — NOT closed by work; re-measured and found already passing. ./gradlew check exit 0 at HEAD; redact BRANCH missed 13 / covered 181 = 0.93299 vs the 0.930 floor, LINE 0.97978 vs 0.975. Independently measured by this verifier from a freshly generated jacocoTestReport.xml, not accepted from the brief."
  gaps_remaining: []
  regressions: []
  warnings:
    - "ROADMAP.md:112 headline reads '20/20 plans executed' while its own wave breakdown sums to 19 (7 + 5 + 6 + 1); 21-20 is parked under a bare 'Plans:' heading above Wave 1 rather than a labelled fourth round. Introduced by tracking commit 5681155, not by plan 21-20. Cosmetic."
    - "redact BRANCH clears its floor by 0.00299 — under one branch of headroom (194 total). Losing a single covered branch in Redaction returns the gate to 0.92784 RED. QUAL-06 is green but has no margin."
    - "21-19-SUMMARY.md frontmatter provides[0] ('closing W-A') and key-decisions[0] ('W-A CLOSED rather than RECORDED') still read as class-closure. Outside plan 21-20's stated scope, which named lines 50 and 171; the prose CORRECTION marker at line 52 governs the file. Advisory."
  judgment_calls_reviewed:
    - call: "Executor added one labelled record-note line beneath the amended headline instead of satisfying two jointly-unsatisfiable must-haves (amend-in-place with no seventh AMENDMENT block, AND numstat additions > deletions)."
      verdict: "ACCEPTED — both intents preserved. grep -c 'AMENDMENT' is 8 before and 8 after; git diff --numstat is +2/-1; the superseded opening claim is quoted verbatim inside the amended line; the note does not restate the six prior amendments and says so."
    - call: "Executor left 21-19-SUMMARY.md's requirements-completed: [PRIV-05] frontmatter unedited, arguing that editing it would destroy evidence."
      verdict: "ACCEPTED, on stronger grounds than the executor gave. gsd-core/workflows/execute-plan.md:409 defines requirements-completed as a verbatim copy of the PLAN's requirements array — provenance, not a completion assertion. 21-19-PLAN.md:13 is requirements: [PRIV-05], so the field is correct by its own contract, and editing it would desynchronise this summary from 86 siblings. The authoritative field, REQUIREMENTS.md PRIV-05, is - [ ] and byte-frozen."
constraints_honoured:
  - ".planning/REQUIREMENTS.md sha256 9b3219662ec0d007c1c82d64eed3ef2698bd306ce69f01205ac9bbc3f42fcfb4 — re-measured, matches. PRIV-05 still - [ ], correctly: AR-27-08 is open and owned by Phase 28, which accepted a further residual (D-28-09). 'By any path' is still not literally true."
  - "git status --porcelain -- src/ prints nothing; no commit in 21-20's range (f5003d0..HEAD) touches src/."
  - "21-VERIFICATION.md was not edited by plan 21-20 (git log f5003d0..HEAD -- 21-VERIFICATION.md is empty) — the plan did not mark its own homework."
---

# Phase 21: Redaction Completeness — Verification Report

**Phase Goal:** No path sends cookie values or other credentials to an AI backend under STRICT or BALANCED, and redaction never silently declines to run.
**Requirements:** PRIV-05, PRIV-06
**Verified:** 2026-08-13T08:41:15Z
**Status:** human_needed
**Re-verification:** No — initial verification (18 plans, 2 review rounds, 2 gap-closure rounds)

## Method

Every claim below was re-derived from source or measured against the **compiled shipped classes**
(`build/classes/kotlin/main`) through standalone JDK 21 harnesses written for this verification. No
SUMMARY.md assertion was accepted as evidence. Because this phase has a documented history of
grep-shaped acceptance criteria producing false positives and of tests that were green over a live
leak, three additional disciplines were applied:

1. **Behaviour over grep.** Every criterion was checked by driving `Redaction.apply`,
   `buildScanMetadataText` and `redactScanMetadata` with adversarial fixtures, not by pattern-matching
   source.
2. **Mutation testing of the repo's own guards.** Two of this phase's headline fixes were reverted in
   an isolated `git worktree` and the suite re-run, to prove the named guards are not vacuous.
3. **Sensitivity validation of my own probes.** Where a probe passed, it was re-run against the
   mutated classes to confirm it *would* have failed. Two of my first-draft probes passed against the
   defective build and were rebuilt until they were sensitive — the same "absence of a fixture family"
   trap CONCERNS.md warns about.

Baseline re-measured independently: `JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test ktlintCheck detekt -q`
→ exit 0, **668 tests, 0 failures, 1 skipped**. `detekt-baseline.xml` is byte-identical to end of
Phase 20 (QUAL-07 held; Phase 21 added zero entries). Working tree left clean; the mutation worktree
was removed.

## Goal Achievement

### Observable Truths — ROADMAP Success Criteria

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| SC1 | A passive scan of a request carrying `Cookie: JSESSIONID=…; PHPSESSID=…; connect.sid=…; auth_token=…; csrftoken=…` produces a prompt in which **none** of those values appear, in both STRICT and BALANCED. Asserted per cookie name. | ✓ VERIFIED | Probe over compiled classes: 6 cookie names × 2 modes → 12/12 values absent, 12/12 names preserved as `NAME=[REDACTED]`. End-to-end through the real emitter (`buildScanMetadataText` → `redactScanMetadata`): the raw `Cookie:` header line is also `Cookie: [STRIPPED]`. Repo guards `cookieSectionValuesRedactedPerName` (per-name, both modes, name-preservation asserted) and `emittedCookieSectionValuesAreRedacted_sc1` both ran green. **Non-vacuity proven by mutation:** reverting `cookieSectionEnd`'s blank-line skip turns `cookieSectionBlankEntriesDoNotCollapseSpan` red with `"a blank cookie entry must not let the value of 'abtest_bucket' reach the backend"`. |
| SC2 | The same holds for cookies surfaced through `request.parameters()` as `COOKIE`-type params in `=== PARAMETERS ===` — both entry points closed. | ✓ VERIFIED | Probe: 6 `(COOKIE)` lines × 2 modes → all values absent, `(COOKIE)` suffix and name preserved; `q=searchterm (URL)` and `comment=hello (BODY)` byte-identical. `cookieTypedParamRegex` is type-keyed, so it is context-free rather than section-scoped. Repo guards `cookieTypedParametersRedacted` + `parameterLineShape` (asserts the shape against the real `HttpParameterType.COOKIE` enum) ran green. |
| SC3 | Sensitive-key matching recognises compound and vendor names (`auth_token`, `api-key`, `X-Session-Id`, `remember_me`) without over-redacting `keyboard_layout` or `codename`. Both directions asserted. | ✓ VERIFIED | Probe: 16 compound/vendor keys × 3 consumer contexts (query string, form body, JSON) = 48/48 redacted; 7 benign keys survive verbatim. Repo corpora: 31 must-redact, 21 must-not-redact, camelCase set, plus the WR-01 corpora (`wr01BroadWordKeysSurviveUnlessCredentialBearing`, `wr01NonBroadWordOverRedactionsRemainAccepted`) — all ran green. `factoredKeyVocabularyMatchesItsReadableSpecification` is non-vacuous by inspection: it asserts consumer-set coverage, `distinct().size > 100`, **byte-identical** output per key (not just classification), and a comparison-count invariant. |
| SC4 | A payload exceeding `MAX_REDACTION_BODY_CHARS` is truncated-and-redacted or refused; the secret does not survive. | ✓ VERIFIED | Probe suite, all against compiled classes: 40-alignment sweep of a two-line JSON pair with an open quoted value (0 leaks); 40-alignment sweep of a single-line form param (0 leaks); **121-offset precision straddle sweep** placing the cut inside the open quoted value (0 leaks); 1.5 MB newline-free minified JSON (secret redacted, body **not** destroyed — 1 500 033 chars out); sizes 999 999 / 1 000 000 / 1 000 001 / 1 001 000 (0 leaks); cookie section embedded in and appended to an oversize body (0 leaks, both modes); pathological custom pattern `(a+)+$` on a 1.4 MB body → 2 037 ms, output collapses to 1 085 chars of markers, secret absent — **fail closed, not passthrough**. |
| SC5 | The interaction between user custom patterns and `PrivacyMode.OFF` is settled deliberately and documented in `DECISIONS.md`. | ✓ VERIFIED | `DECISIONS.md` § ADR-14 states the decision ("custom patterns apply in **every** privacy mode including `OFF`, so `OFF` means *no built-in redaction* rather than *no redaction at all*") **and** records the two rejected alternatives (OFF-means-off; always-apply-plus-opt-out) and the structural enforcement (deleting the two caller-side OFF short-circuits). Behaviour confirmed: custom pattern redacts in STRICT, BALANCED **and** OFF, including on an oversize body under OFF; OFF with no custom patterns is a byte-identical passthrough. |
| SC6 | The existing `RedactionTest` suite including the RFC 5869 HKDF vector stays green; host anonymization is not perturbed. | ✓ VERIFIED | 668/668 green. `hkdfMatchesRfc5869Vector` ran. `hmacSha256`, `hkdfExpand`, `anonymizeHost`, `clearMappings` and `boundedLru` are **byte-identical** to the pre-phase baseline (`9cd4987`) after whitespace normalisation. 15 of the 16 base `RedactionTest` functions survive by name; the single removal is `oversizeBodySkippedSafely`, the documented deliberate exception 1 (rewritten as `oversizeBodySecretDoesNotSurvive`). Deliberate exception 2 confirmed in place: the OFF limb of `customPatternRedactsInStrictAndBalanced` now asserts `[REDACTED]` where it previously asserted `assertEquals(input, offOutput)`, with the inversion explained in-line. Canaries green: `balancedModeRedactsUrlTokensInQueryStrings`, `bodyFormLeadingFieldRedacted`, `bodyJsonSecretKeysRedacted`, `offModePreservesBodies`, `offModePreservesAllTokens`. |

**Score: 6/6 success criteria verified.**

### Observable Truths — the phase goal sentence itself

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| G1 | "No path sends cookie values or other credentials to an AI backend under STRICT or BALANCED" — **as operationalised by PRIV-05 and SC1/SC2** | ✓ VERIFIED | Both named entry points closed and adversarially probed: blank leading/middle cookie entries, a cookie value forging `=== FOO ===`, a cookie NAME forging the framing, CR/LF injected inside an entry, a decoy `=== COOKIES ===` planted in the prior-knowledge block ahead of the real section, `Set-Cookie` response headers, more cookies than `COOKIES_MAX_COUNT`, CRLF line endings, no trailing newline, header not alone on its line, header at EOF, U+2028/U+2029/U+0085 inside a value, and `Authorization` / `X-API-Key` headers — **all redacted in both modes**. |
| G2 | "…by **any** path" — literal reading | ⚠️ PARTIAL | Not literally true, and the shortfall is measured rather than inferred. `sanitizeHeadersForPrompt` admits every header whose lowercased name starts with `x-` or contains `auth` / `token` / `cookie`, while `authHeaderRegex` / `cookieHeaderRegex` / `setCookieHeaderRegex` are exact-name alternations. 19 header names are therefore both emitted into the passive-scan prompt and unredacted under STRICT and BALANCED. Split: **14 are the already-recorded, explicitly-deferred `authHeaderRegex` vendor gap** (CONCERNS.md); **5 are cookie-name variants and are recorded nowhere** — see WARNING W-A. All pre-existing (both mechanisms predate this phase), all explicitly declared out of scope at planning time by `21-CONTEXT.md`. |
| G3 | "…and redaction never silently declines to run" | ✓ VERIFIED | No fail-open path found by source trace or probe. `SafeRegex.replaceAllSafe` is **deleted** (grep: zero occurrences outside obituary comments) so `replaceAllSafeReporting` is the only replacement entry point and it reports `timedOut`. Every consumer branches on the flag: the ≤1 MB single pass discards its partial result and re-scans through `windowedScan`; `scanWindow` routes to `dropOrRetry`; `dropOrRetry` halves to depth 4 then drops behind `windowDroppedMarker`; `windowedScan` coalesces the tail behind `budgetExceededMarker`; `redactCookieSections` drops the remainder behind the same marker on deadline expiry. `maybeLogTruncation` wraps only the sink invocation in `runCatching`, leaving the window bookkeeping outside it. Probed: a pathological-but-compilable custom pattern on an oversize body produces markers and drops content — it never passes the body through. |

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `src/main/kotlin/…/redact/Redaction.kt` | Cookie-section rule, typed-param rule, `SENSITIVE_KEY_EXPR`, windowed/budgeted/fail-closed body stage | ✓ VERIFIED | 1 789 lines. `COOKIE_SECTION_HEADER`, `redactCookieSections` (single-pass, monotone cursor, deadline), `cookieSectionEnd` (blank lines skipped, `MAX_COOKIE_SECTION_LINES` bound), `sanitizeCookieSectionEntries`, `cookieTypedParamRegex`, `SENSITIVE_KEY_EXPR` (first-letter factored), `bodyStage`/`bodyRules`/`windowedScan`/`scanWindow`/`dropOrRetry`/`splitPoint`/`safeCutPoint`, `windowEnd`/`pairMayBeInFlightAt`/`endsInsideOpenQuotedValue`/`isJsonPairBoundaryRisk`/`isJsonPairBoundaryContinuation`, `maybeLogTruncation`. All wired into `apply()` and all exercised by probe. |
| `src/main/kotlin/…/redact/SafeRegex.kt` | `replaceAllSafeReporting` + `SafeReplaceResult`; `replaceAllSafe` removed; `ADVERSARIAL_PROBES` | ✓ VERIFIED | `replaceAllSafe` gone; six-probe corpus (3 char classes × 2 terminators) driving `isPatternSafe`; zero-width rejection above the probe loop with its own return, guarded by `zeroWidthPatternsAreRejectedWithoutRunningAnyProbe`. |
| `src/main/kotlin/…/scanner/PassiveAiScannerPrompts.kt` | Montoya-free `buildScanMetadataText` / `formatParamLine` / `redactScanMetadata` / `cookieSectionLines` | ✓ VERIFIED | All four are top-level `internal fun` and callable from a plain JVM harness with no `MontoyaApi` — I drove them directly. `redactScanMetadata` calls `Redaction.apply` unconditionally (no OFF short-circuit). |
| `src/main/kotlin/…/scanner/PassiveAiScannerAnalysis.kt` | `COOKIES_MAX_COUNT` clamped to the redactor's bound; `doAnalysis` uses the extracted functions | ✓ VERIFIED | `COOKIES_MAX_COUNT = minOf(COOKIES_MAX_COUNT_INTENDED, Redaction.MAX_COOKIE_SECTION_LINES)` = 6. `doAnalysis:360-380` builds via `buildScanMetadataText` and redacts via `redactScanMetadata`; `safeMetadataText` (not `metadataText`) feeds `buildAnalysisPrompt` and the batch queue item. |
| `src/main/kotlin/…/mcp/McpToolContext.kt` | `redactIfNeeded` with no privacy-mode short-circuit | ✓ VERIFIED | `Redaction.apply(raw, RedactionPolicy.fromMode(privacyMode), …)` called unconditionally. |
| `src/main/kotlin/…/ui/SettingsPanelActions.kt` | `privacyNoticeFor` as a top-level pure composer taking the persisted list | ✓ VERIFIED | `internal fun privacyNoticeFor(...)` at :238; single call site at :311 passes `settings.customRedactionPatterns` (persisted), not `customPatternsArea` text. Guarded by `PrivacyNoticeCompositionTest`. |
| `src/main/kotlin/…/App.kt` | Truncation sink wired and unwired; persisted patterns re-validated at startup | ✓ VERIFIED (source-asserted) | `initialize:69` sets the sink; `initialize:113` `setCustomPatterns(… .filter { SafeRegex.isPatternSafe(it) })`; `shutdown:269` `safeShutdownStep("Redaction truncation sink") { Redaction.truncationLogger = null }`. Both need a live `MontoyaApi` — routed to human verification, exactly as 21-18 states. |
| `DECISIONS.md` § ADR-14 | Records D-01…D-05 as one principle, scoped to the body stage | ✓ VERIFIED | Present, with both claim retirements recorded as deliberate (byte-identity, then the narrower over-claim), the WR-01 ruling, the first-letter-factoring rationale, and four explicitly-named residuals. |
| `.planning/codebase/CONCERNS.md` | Header-stage, window-boundary and vocabulary residuals recorded, not claimed fixed | ✓ VERIFIED | Two entries carry them, including the round-2 correction, the `authHeaderRegex` vendor-header deferral and the plural-key gap. **Gap:** the cookie-header NAME variant class is absent — see W-A. |
| `.planning/phases/21-redaction-completeness/deferred-items.md` | D-21-01…D-21-03 recorded | ✓ VERIFIED | D-21-01 closed by 21-16 with the corrected diagnosis; D-21-02 open (~3.2 MB retry-ladder ceiling, fail-closed, MCP default 2 MiB sits inside it); D-21-03 open (boundary sweeps under CPU contention, fail-closed both times). All three consistent with what I measured. |

### Key Link Verification

| From | To | Via | Status | Details |
|------|-----|-----|--------|---------|
| `PassiveAiScannerPrompts.buildScanMetadataText` | `Redaction.COOKIE_SECTION_HEADER` | imported constant, never an inline literal | ✓ WIRED | Compile-time coupling; parity guard `emittedBlobContainsTheSectionConstant_parity` ran green. |
| `PassiveAiScannerPrompts` (producer **and** emitter) | `Redaction.sanitizeCookieSectionEntries` | two deliberate call sites | ✓ WIRED | `cookieSectionLines:66` (before `take(maxCount)`) and `buildScanMetadataText:158`. Each guarded by a **different** named test. Probed: a forged `=== FOO ===` cookie value/name and CR/LF injection are all neutralised end to end. |
| `PassiveAiScannerAnalysis.COOKIES_MAX_COUNT` | `Redaction.MAX_COOKIE_SECTION_LINES` | compile-time clamp + drift test | ✓ WIRED | `cookieEmitterBoundStaysWithinTheRedactorBound` asserts **both** `INTENDED ≤ bound` and `INTENDED × 2 ≤ bound` — stronger than the plan claimed. |
| `Redaction.bodyStage` / `scanWindow` | `SafeRegex.replaceAllSafeReporting` | every rule under `min(DEFAULT_TIMEOUT_MS, remaining)`; `timedOut` never ignored | ✓ WIRED | Verified at all four branch points. No path assigns `.text` without first branching on the flag. |
| `Redaction.windowEnd` | `isJsonPairBoundaryRisk` | `pairMayBeInFlightAt` (backward) + `isJsonPairBoundaryContinuation` (forward) | ✓ WIRED | Both delegate, so both inherit the widened predicate. **Mutation-proven:** dropping the `endsInsideOpenQuotedValue` clause reopens the leak (see below). |
| `App.initialize` | `SafeRegex.isPatternSafe` | re-validation before `setCustomPatterns` | ✓ WIRED | Source-asserted; needs live Burp to exercise. |
| `App.shutdown` | `Redaction.truncationLogger` | `safeShutdownStep` beside `clearMappings()` | ✓ WIRED | Source-asserted; needs live Burp. Automated fallback (`runCatching` in `maybeLogTruncation`) verified present and guarded. |

### Data-Flow Trace (Level 4)

| Artifact | Data variable | Source | Produces real data | Status |
|----------|--------------|--------|--------------------|--------|
| `PassiveAiScannerAnalysis.doAnalysis` | `safeMetadataText` | `redactScanMetadata(metadataText, …)` | Yes — flows into `buildAnalysisPrompt` (:383) and the batch queue item (:417). The **unredacted** `metadataText` is not referenced past :380. | ✓ FLOWING |
| `PassiveAiScannerPrompts.cookieSectionLines` | `cookies` | `request.headers().filter { name == "Cookie" }.map { value }` → split/sanitise/take | Yes — real Montoya header values | ✓ FLOWING |
| `Redaction.bodyStage` | `rules` | `bodyRules(builtinsEnabled)` — the **same** list the `testWindowedBodyStage` seam builds | Yes; `rules.isEmpty()` short-circuit is the OFF byte-identity path | ✓ FLOWING |
| `Redaction.truncationLogger` | sink lambda | `App.initialize:69` → `api.logging().logToOutput` | Yes in production; null in tests, and the pipeline never depends on it | ✓ FLOWING |
| `SettingsPanelActions.privacyNoticeFor` | `persistedCustomPatterns` | `settings.customRedactionPatterns` | Yes — persisted list, not `JTextArea` text | ✓ FLOWING |

### Behavioural Spot-Checks

| Behaviour | Command | Result | Status |
|-----------|---------|--------|--------|
| Full gate | `JAVA_HOME=…21 ./gradlew test ktlintCheck detekt -q` | exit 0 | ✓ PASS |
| Test census | parse `build/test-results/test/*.xml` | `tests=668 failures=0 errors=0 skipped=1` | ✓ PASS |
| SC1/SC2/SC3 behaviour | `java -cp build/classes/kotlin/main Probe1` | FAILS=0 (79 assertions) | ✓ PASS |
| Adversarial end-to-end (emitter → redactor) | `java … Probe2` | 22/32 PASS; 10 FAIL are all header-NAME-variant / body-embedded cases outside SC1–SC2 — see W-A / I-A | ⚠️ see findings |
| PRIV-06 fail-closed | `java -Xmx4g … Probe3` | FAILS=0 (23 assertions incl. 80 sweep alignments) | ✓ PASS |
| Header-name coverage census | `java … Probe4` | 20/31 header names leak; 19 of them are emitted by `sanitizeHeadersForPrompt` | ⚠️ see W-A / I-A |
| Precision straddle sweep | `java -Xmx4g … Probe6` (shipped) | 121 offsets, 0 leaks, 0 dropped windows | ✓ PASS |
| Line-terminator / framing edge cases | `java … Probe7` | 15/19 PASS; 4 FAIL are the two documented accepted residuals plus two latent low-severity cases — see I-B / I-C | ⚠️ see findings |
| Named-guard integrity | AST scan of `Redaction.kt`/`SafeRegex.kt`/scanner sources vs. test source set | 14 referenced guards, **0 missing** | ✓ PASS |
| Detekt baseline (QUAL-07) | `git diff <phase-20-end> HEAD -- detekt-baseline.xml` | empty | ✓ PASS |

### Mutation Testing (anti-vacuity)

Both mutations were applied in a detached `git worktree` (removed afterwards; the working tree is clean).

| Mutation | Reintroduces | Repo suite result | Verdict |
|----------|-------------|-------------------|---------|
| `cookieSectionEnd`: make a blank line **terminate** the span | 21-REVIEW CR-01 (the live PRIV-05 cookie leak) | `cookieSectionBlankEntriesDoNotCollapseSpan` **FAILED** — "STRICT: a blank cookie entry must not let the value of 'abtest_bucket' reach the backend" | Guard is genuine |
| `isJsonPairBoundaryRisk`: drop the `endsInsideOpenQuotedValue` clause | 21-REVIEW-2 CR-01 (the two-line JSON straddle leak) | `windowedScanRedactsJsonPairWhoseValueStraddlesTheCut` **FAILED** at shift=0 | Guard is genuine |

Independent confirmation: my own 121-offset straddle probe leaks at **15 offsets** against the mutated
build and at **0** against the shipped build. Two earlier drafts of that probe passed against the
*defective* build — a first-hand reproduction of the "a coverage claim is only as strong as the shapes
its fixtures can construct" lesson this phase recorded. Only the sensitivity-validated probe is
reported above.

### Requirements Coverage

| Requirement | Source plans | Description | Status | Evidence |
|-------------|-------------|-------------|--------|----------|
| **PRIV-05** | 21-01, 21-03, 21-04, 21-05, 21-07, 21-08, 21-10, 21-12, 21-13, 21-15, 21-17 | Cookie values do not reach an AI backend in STRICT/BALANCED by any path. Specifically the `=== COOKIES ===` section is redacted; sensitive-key matching recognises real-world names; covered by a test asserting each name in both modes. | ✓ SATISFIED **as scoped** — tick recommended with W-A recorded in the same commit | All three "Specifically" clauses verified by probe and by non-vacuous repo tests. The **lead sentence** ("by any path") is broader than what ships — see G2 / W-A. |
| **PRIV-06** | 21-02, 21-03, 21-06, 21-07, 21-08, 21-09, 21-11, 21-13, 21-14, 21-16, 21-18 | Redaction never fails open. A payload above the cap is truncated-and-redacted or refused. Custom patterns × `PrivacyMode.OFF` is an explicit documented decision. | ✓ SATISFIED — tick recommended unreservedly | Both halves verified. Fail-closed proven at every size class including the pathological-pattern case; ADR-14 records the OFF decision with rejected alternatives and structural enforcement. |

No orphaned requirements: REQUIREMENTS.md maps only PRIV-05 and PRIV-06 to Phase 21, and every plan's
`requirements:` field declares one or both.

### Plan Must-Have Audit (18 plans)

Every `must_haves` truth, artifact and key link across all 18 plans was checked. **No must-have is
claimed but false.** Two are *superseded within the phase*, and both supersessions are recorded in
source rather than left dangling:

| Plan | Must-have | Disposition |
|------|-----------|-------------|
| 21-02 | "The existing `replaceAllSafe` keeps its documented fail-open contract byte-for-byte; `SafeRegexTest.kt:44`'s fail-open assertion stays green unchanged" | **Superseded** by 21-18 / WR-03, which deleted the façade. `SafeRegexTest` documents the supersession in-line and moved the assertion onto `replaceAllSafeReporting(...).text`; the fail-soft *text* behaviour it pinned is unchanged. Not a gap. |
| 21-06 | "Line-boundary cutting was proven byte-identical to whole-document processing" | **Retired as false** by 21-09 (CR-02) and again narrowed by 21-13 (round-2 CR-01). Both retirements are written into `Redaction.kt`'s D-01 paragraph and into ADR-14 as deliberate, with a named test per surviving claim. Not a gap — this is the phase's own D-08 REFINED discipline working. |

Spot-verified in addition: 21-01's Montoya-free seam (I called all four functions from a plain JVM
harness), 21-03's four D-07 strings, 21-04's non-capturing-group requirement (pinned by a derived
group-count assertion, not a hard-coded number), 21-07's red-before-green transcript (present, with the
expected failure set exactly `oversizeBodySecretDoesNotSurvive` + `oversizeBodyFailsClosed`), 21-11's
`splitPoint` fallback, 21-12's WR-01 corpora, 21-15's clamp, 21-16's named-guard registry, 21-17's
`naiveKeyExprForTest` as an internal *method* (`javap` confirms no public ~700-char field ships),
21-18's façade deletion and probe corpus.

### Review Finding Closure

All 11 findings of `21-REVIEW.md` and all 13 of `21-REVIEW-2.md` are accounted for: closed with a
named guard, or dispositioned in source with a pinned assertion. Independently confirmed for
CR-01 (both rounds), CR-02, CR-03, CR-04, WR-01…WR-07, W-01…W-08, IN-01…IN-04.

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| — | — | `TBD` / `FIXME` / `XXX` in any file this phase touched | — | **None found.** Debt-marker gate passes. |
| — | — | `TODO` / `HACK` / `PLACEHOLDER` | — | **None found.** |

### Findings requiring a decision

#### ⚠️ W-A — Cookie-header NAME VARIANTS leak, and are recorded nowhere

`sanitizeHeadersForPrompt` (`PassiveAiScannerFilters.kt:162-182`) admits any header whose lowercased
name `startsWith("x-")` or `contains("cookie")`. `cookieHeaderRegex` and `setCookieHeaderRegex` anchor
on `^cookie:` / `^set-cookie:`. Measured against the compiled classes, under **both** STRICT and
BALANCED, these five reach the prompt verbatim:

`Cookie2`, `X-Cookie`, `Set-Cookie2`, `X-Original-Cookie`, `X-Forwarded-Cookie`

`Cookie2` is RFC 2965 (obsolete); the `X-*-Cookie` forms are injected by some reverse proxies and API
gateways. Frequency is low, but the class is **cookie values under STRICT/BALANCED**, which is
PRIV-05's own subject matter, and it is the only measured leak in this phase that appears in **no**
record — not ADR-14, not CONCERNS.md, not `deferred-items.md`, not either review.

Pre-existing (both mechanisms predate Phase 21) and explicitly declared out of scope at planning time —
`21-CONTEXT.md:306-308` states the two cookie regexes are "correct for real header lines … **They are
not the bug**". That scoping call is defensible; leaving the record silent is not, by this phase's own
D-08 REFINED standard. **Recommendation:** add one sentence to CONCERNS.md's existing
"Redaction regex coverage gaps" entry, beside the already-accepted `authHeaderRegex` vendor-header
deferral, in the same commit that ticks PRIV-05. Cheap alternative if closing is preferred: widen the
two regexes to `(?im)^[a-z0-9\-]*cookie[a-z0-9\-]*:\s*.+$`.

#### ℹ️ I-A — Vendor credential headers leak (already recorded, already deferred)

Same mechanism, credential rather than cookie. Measured leaking under both modes and emitted by
`sanitizeHeadersForPrompt`: `X-Shopify-Access-Token`, `Stripe-Signature`, `X-Amz-Security-Token`,
`X-Vault-Token`, `X-Refresh-Token`, `X-Firebase-AppCheck`, `X-Goog-Api-Key`, `X-Algolia-API-Key`,
`X-Hub-Signature-256`, `X-Sumo-Token`, `X-Auth-Key`, `X-Auth-Email`, `X-Requested-With-Token`,
`X-Airtable-Application-Id`, `X-Twilio-Signature`.

**Not a gap.** CONCERNS.md already records this exact class — naming `x-shopify-access-token` and
`stripe-signature` — with the reason it stays deferred ("`authHeaderRegex` would need the same
treatment rather than inheriting it"). Listed here only so the accepted residual now has a measured
name list rather than two examples.

#### ℹ️ I-B — The two `MAX_COOKIE_SECTION_LINES` residuals reproduce exactly as documented

My probe reproduced the source comment's own measured numbers to the entry: a 17-entry raw section
leaks `ck16`; a 12-entry section with a blank line between every entry leaks `ck8..ck11`. Both are
written verbatim into `Redaction.kt`'s `MAX_COOKIE_SECTION_LINES` comment, pinned by
`everyEntryOfAMaximalCookieSectionIsRedacted` and `cookieEmitterBoundStaysWithinTheRedactorBound`, and
**unreachable on the production scanner path**: the emitter is clamped to 6 entries and
`sanitizeCookieSectionEntries` drops blank entries before they are emitted. My emitter-path probe (12
cookies in, 6 emitted) leaks nothing. **Accepted residual, accurately recorded — not a gap.** Worth
stating that the record is *measurably* accurate, which is not something this phase's history let me
assume.

#### ℹ️ I-C — Two latent low-severity primitives, neither production-reachable today

1. **Bare CR inside a cookie value.** `cookieSectionPairRegex`'s `(.*)` excludes `\r`, so
   `JSESSIONID=AAA\rSECRET` yields `JSESSIONID=[REDACTED]\rSECRET` — the tail survives. Neutralised on
   the scanner path by `sanitizeCookieSectionEntries`' CR/LF flattening (my emitter probe passes);
   reachable only through a non-emitter caller (`McpToolContext.redactIfNeeded`, `redact_preview`)
   where the "cookie section" is attacker-supplied text in the first place.
2. **`formatParamLine` has no sanitiser.** A parameter value containing a raw newline splits into two
   emitted lines and the second escapes `cookieTypedParamRegex`. A `COOKIE`-typed value cannot carry a
   raw newline in a well-formed request, so this is latent. Already noted in `Redaction.kt:213-215`.

#### ℹ️ I-D — Two pre-existing paths outside this phase's requirements

1. **`ContextCollector.HttpItem.url`** (`ContextCollector.kt:63`) is `item.request().url()` verbatim and
   is serialised into `contextJson`, which `ChatPanel` sends to the backend. The host is **not**
   anonymized in STRICT there (only in `previewUrl`, which feeds the preview, not the payload), and a
   query-string credential survives in that field even though the same credential is redacted inside
   `request`. Present since the first commit; not a cookie value; outside PRIV-05/PRIV-06.
   `BountyPromptTagResolver.redactUrl` shows the correct pattern.
2. **`BountyPromptTagResolver.buildRequestParameters`** renders `name=value (TYPE)` from the **raw**
   request with a hand-rolled `sensitiveParamName` regex instead of `Redaction.apply`, so `PHPSESSID`,
   `connect.sid` and `remember_me` cookie parameters would survive. **`resolve()` has no production
   caller** — the class is constructed only in its own test — so this is latent, not live. Worth a
   guard before it is ever wired, since it re-implements exactly the SC2 rule with a narrower
   vocabulary.

Neither I-D item is claimed by any Phase 21 plan and neither is a regression; recorded for the backlog.

### Deferred Items

None. No later phase in this milestone (22–26: SEC-06, REL-05, REL-06/07, SEC-07, QUAL-06/07, DOC-03)
addresses header-regex coverage, so W-A is a residual requiring disposition rather than a deferral.

## Gaps Summary

**The phase goal is achieved on both halves as the phase defined them, and all six ROADMAP success
criteria verify against the codebase rather than against SUMMARY prose.**

The second half of the goal — "redaction never silently declines to run" — is the stronger of the two
results. I could not construct any input, at any size, in any privacy mode, for which a body rule
times out and its unscanned bytes still reach the output. The deletion of the `replaceAllSafe` façade
is what makes that structural rather than incidental: there is now exactly one replacement entry point
and it cannot return without reporting `timedOut`.

The first half is achieved for every path PRIV-05 names and every path SC1/SC2 define, and it survives
a wide adversarial sweep — forged section framing, decoy sections planted through attacker-controlled
response headers, blank and CR/LF-poisoned entries, CRLF and Unicode line terminators, oversize
carriers, and the emitter/redactor boundary. It is **not** true in the unqualified "by any path" sense
the goal sentence uses, and the single finding that matters there is W-A: five cookie-header name
variants that leak under STRICT and BALANCED and appear in no record. That class is pre-existing,
low-frequency, explicitly scoped out at planning time, and its credential-carrying sibling is already
an accepted documented residual — so it does not block the phase, but it does mean PRIV-05's
checkbox should be ticked together with a one-line record correction rather than on its own.

The strongest evidence in this report is not that 668 tests pass — this phase has already demonstrated
that 628 green tests can sit over a live cookie leak. It is that reverting either of the two headline
fixes turns a *named* guard red, and that an independently-constructed 121-offset probe leaks at 15
offsets against the reverted build and at zero against the shipped one.

**Recommendation:** tick **PRIV-06** now. Tick **PRIV-05** in the same commit that records W-A in
CONCERNS.md (or closes it). Phase 22 is not blocked — it shares no files with this work.

---

_Verified: 2026-08-13T08:41:15Z_
_Verifier: Claude (gsd-verifier) — goal-backward, FORCE stance_

---

## Re-verification — 2026-08-27

**Trigger:** the report above was committed at `ed31468` (2026-08-13 10:45:31). `21-19-SUMMARY.md`
was committed at `caeff0b` (2026-08-13 11:17:50) — **32 minutes later**. Every other summary predates
the report. Exactly one plan therefore shipped without ever being verified: **21-19**, the plan
created to close this report's own **W-A**. This section verifies that plan against the tree at
`14f59cb`, and re-confirms SC1–SC6 have not been invalidated by Phase 27's rewrite of `Redaction.kt`.

**Method.** No claim below is taken from `21-19-SUMMARY.md`. The load-bearing result is a
**differential probe**: 21-19's own regex form (extracted verbatim from `git show f1d5a83`) and the
current tree's form were run side by side, in one JVM, over the two carriers PRIV-05 actually has —
the passive-scan prompt (real newlines) and the MCP tool result (the raw message inside a JSON string,
CR/LF escaped). Probe at `scratchpad/probe/WA.java`. Baseline re-measured, clean tree:
`clean check` → **1258 tests, 0 failures, 1 skipped**, but the gate **FAILS** — see G-3.

### Re-verified must-haves — plan 21-19

| # | Must-have (from `21-19-PLAN.md`) | Status | Evidence |
|---|----------------------------------|--------|----------|
| 19-T1a | The five W-A names — `Cookie2`, `X-Cookie`, `Set-Cookie2`, `X-Original-Cookie`, `X-Forwarded-Cookie` — no longer reach **the prompt** verbatim under STRICT or BALANCED | ✓ VERIFIED | Differential probe, carrier A (real newlines): **21-19's own form strips all five**. Credit is genuine and belongs to 21-19, not to Phase 27. `RedactionTest.cookieHeaderNameVariantsAreStripped` exists verbatim as 21-19 wrote it (`RedactionTest.kt:434`) and **PASSES** on today's tree; the negative control `X-Request-Id: benignidcontrolvalue` survives both modes. |
| 19-T1b | "…so PRIV-05's lead sentence (**'by any path'**) becomes **literally true** rather than true-as-scoped" | ✗ **FAILED** | Measured, three independent ways. See G-1. 21-19 closed **one of two** carriers and **five of six** name shapes on the carrier it did close. |
| 19-T2 | The header NAME is preserved — no silent rename of `X-Cookie` to `Cookie` (T-21-WA2) | ✓ VERIFIED | Both replacements are name-preserving lambdas (`Redaction.kt:2262`, `:2267`, `m.value.substringBefore(":")`). Probe: `X-Cookie: [STRIPPED]` present; canonical renderings byte-identical (`Cookie: [STRIPPED]`, `Set-Cookie: [STRIPPED]`), so `RedactionTest:366` and `BountyPromptTagResolverTest:93` are green **unedited**. This half survived Phase 27's rewrite intact. |
| 19-T3 | The 14 vendor `authHeaderRegex` names stay an accepted, **recorded** residual | ✓ VERIFIED | `CONCERNS.md:65` records the vendor class as accepted-and-deferred with its reason (an open-ended vendor list is never complete). Still accurate. |

**Plan 21-19 score: 2/3.**

### Re-confirmed — ROADMAP SC1–SC6 on today's tree

Phase 27 rewrote these rules into `logicalLineHeaderRule` with a four-way logical-line-start model
(`REAL_LINE_START`, `JSON_ESCAPED_NEWLINE`, `JSON_STRING_OPEN`), so the 6/6 above was re-checked rather
than assumed. **No phase-21 must-have was invalidated.** All 13 named guards this report relied on were
re-run and pass: `hkdfMatchesRfc5869Vector` (SC6), `cookieSectionValuesRedactedPerName` and
`emittedCookieSectionValuesAreRedacted_sc1` (SC1), `cookieTypedParametersRedacted` (SC2),
`factoredKeyVocabularyMatchesItsReadableSpecification` (SC3), `oversizeBodySecretDoesNotSurvive` and
`windowedScanRedactsJsonPairWhoseValueStraddlesTheCut` (SC4), `customPatternRedactsInStrictAndBalanced`
and `offModePreservesBodies` (SC5), plus `cookieSectionBlankEntriesDoNotCollapseSpan`,
`cookieEmitterBoundStaysWithinTheRedactorBound`, `truncationLoggerThatThrowsDoesNotAbortRedaction`,
`zeroWidthPatternsAreRejectedWithoutRunningAnyProbe`. Every artifact/key-link in the tables above still
holds: `replaceAllSafe` is still deleted (4 residual mentions, all obituary comments), the
`COOKIES_MAX_COUNT = minOf(...)` clamp stands, `McpToolContext.redactIfNeeded` still calls
`Redaction.apply` unconditionally, `privacyNoticeFor` still reads the persisted list
(`SettingsPanelActions.kt:374`), and `App.kt:92 / :136 / :298` still wire the sink, the `isPatternSafe`
seeding filter and the shutdown unwire. **SC1–SC6: 6/6 held.**

### Human verification — CLOSED

`21-HUMAN-UAT.md` is `status: complete`, 3/3 passed, 0 issues (`14f59cb`). The three live-Burp items in
this report's `human_verification` block are answered. The fourth item — the W-A maintainer disposition —
was resolved by choosing CLOSE over RECORD on 2026-08-13. **No human verification remains outstanding**,
which is why this re-verification resolves to `gaps_found` on evidence rather than to `human_needed`.

### Gaps

#### G-1 — BLOCKER (record accuracy): W-A's *class* was closed by Phase 27, not by 21-19

The distinction matters because only one of the two answers means phase 21 did its job.

**Closed by 21-19:** W-A exactly as this report worded it — those five names, on the passive-scan prompt
path. Carrier A of the probe confirms it against 21-19's own regex.

**Closed by Phase 27:** the W-A *class* — a cookie-bearing header name variant reaching an AI backend
under STRICT/BALANCED. 21-19 left three residuals of it, all measured:

| # | Residual left by 21-19 | Mechanism | Closed by |
|---|------------------------|-----------|-----------|
| a | `sanitizeHeaders` compared `lowered == "cookie" \|\| lowered == "set-cookie"` — exact names. Confirmed at `git show caeff0b:…/McpToolHelpers.kt:321`. All five variants survived `request_parse` / `response_parse`. | Second carrier never touched | 27-01 (`Redaction.isCookieHeaderName`, now shared by all three sites) |
| b | Even the widened regex could not fire on the tool-result carrier: `Serialization.kt` puts the raw message inside a JSON string and escapes every CR/LF, so `(?im)^` never landed. Probe carrier B: 21-19's form leaks **all five**; current tree strips all five. | Anchor never matched | 27-04 / 27-11 / 27-14 / 27-17 |
| c | **`COOKIE_NAME_PART` was `[A-Za-z0-9-]*`, which excludes `_`.** `X_Cookie`, `my_cookie`, `session_cookie` leaked **on the prompt path** — W-A's own carrier — under STRICT and BALANCED. Probe carrier A: 21-19's form **LEAK**, current tree stripped. Independently corroborated by 27-10's commit body, which measured the same three names. | Redactor narrower than the admitter it was supposed to match | 27-10 |

Residual (c) is the sharpest: **21-19 did not fully close W-A even on the single path W-A was about**,
and its own `cookieHeaderNameVariantsAreStripped` test could not see it because all five fixtures use
hyphens. `sanitizeHeadersForPrompt` is an *admitter* — a name it claims that the regex cannot match is
put on the outbound prompt and then not removed — so the difference set was fail-**open**. That is the
same admitter-vs-redactor asymmetry W-A itself was, reintroduced one character wide by the fix for it.

The report above was therefore **right to score 6/6 and right to raise W-A**, but the record that W-A
was "closed" (`CONCERNS.md:65`, `21-19-SUMMARY.md`, `21-HUMAN-UAT.md` §Gaps) overstates what 21-19
shipped. **PRIV-05 is still `[ ]` and correctly so**: `AR-27-08` (a COOKIE-typed value reaching
`scanner_issues` via `AuditIssue.detail()`) is open and owned by Phase 28, so "by any path" is not
literally true even today.

**Missing:** correct `CONCERNS.md:65` and `21-19-SUMMARY.md`'s "by any path becomes literally true" to
say what 21-19 actually closed — the prompt carrier, hyphenated names — and to attribute (a)/(b)/(c)
to Phase 27.

#### G-2 — BLOCKER (process): plan 21-19 is absent from the project record

`ROADMAP.md` §Phase 21 states "**Plans**: 18 plans" and its list stops at `21-18-PLAN.md`. `21-19` appears
in **neither** `ROADMAP.md` nor `STATE.md` (grep: zero hits). The third gap-closure round — the one that
touched a security control — was executed, committed and summarised, but never recorded as a plan and
never re-verified. This is the same shape as the stale verification itself, and it is the reason the
incomplete fix survived to the v0.10.0 milestone audit and became Phase 27's five rounds of rework.

**Missing:** add 21-19 to `ROADMAP.md` §Phase 21 and correct the plan count to 19.

#### G-3 — BLOCKER (live): `./gradlew check` is RED at HEAD

Reproduced on a **clean tree with zero tracked modifications**, twice (`check`, then `clean check`):

```
Rule violated for package com.six2dez.burp.aiagent.redact:
branches covered ratio is 0.927, but expected minimum is 0.930
```

Measured from `jacocoTestReport.xml`: redact BRANCH **missed 14 / covered 180 = 0.92784**, against the
`0.930` floor QUAL-06 sealed in `build.gradle.kts:411`. All 14 missed branches are in the `Redaction`
class (`missed 14 / covered 115`); every other class in the package is at zero missed. Tests themselves
are green (1258 / 0 failures / 1 skipped) and LINE coverage holds at 0.97528 against its 0.975 floor —
so this is a **coverage-floor regression, not a test failure**, introduced by branches Phase 27 added to
`Redaction.kt` (14 commits since `caeff0b`) without covering them.

This contradicts the standing build note that `check` is green end to end at `0.93299`. It is recorded
here because it is a live blocker on the gate that protects a phase-21 artifact, and because a 0.003
margin under a sealed floor is exactly the kind of drift QUAL-06 exists to catch.

**Missing:** cover the 14 branches, or move the floor deliberately with the seal updated.

### Deferred items — measured, not accepted from the brief

`deferred-items.md` carries **3** items, not 14: `D-21-01` **CLOSED** by plan 21-16, `D-21-02` **OPEN**
(retry-ladder capability ceiling ~3 MB, fail-closed), `D-21-03` **OPEN** (boundary-sweep per-pattern
deadline exposure under CPU contention — the known `RedactionTest` wall-clock flake). **Phase 27 closed
neither.** Its 14 commits to `Redaction.kt` are all header-rule work; `MAX_REDACTION_BUDGET_MS` is
untouched at `2_000L` (`Defaults.kt:104`) and `SafeRegex.DEFAULT_TIMEOUT_MS` at `50L`, which is the
shared mechanism both open items turn on. The real phase-21 deferred debt is **2 open items**, and
Phase 27 shrank it by zero.

### Verdict

**Status: `gaps_found`. Score 8/9** — SC1–SC6 held 6/6 on re-check; plan 21-19 scores 2/3.

Phase 21's redaction work is sound and has survived a substantial downstream rewrite without a single
guard going red. What did not hold is the phase's **record of itself**. The 6/6 above is still earned.
The claim layered on top of it afterwards — that 21-19 made PRIV-05 true "by any path" — is not, and
because no re-verification ran, nothing tested it. A gap-closure plan that closed one of two carriers
and five of six name shapes was recorded as a complete closure, and stayed that way for twelve days
until a milestone audit re-opened PRIV-05.

`REQUIREMENTS.md` is unchanged; PRIV-05 remains `[ ]`, which is the correct state.

---

_Re-verified: 2026-08-27_
_Verifier: Claude (gsd-verifier) — goal-backward, FORCE stance, differential-probe method_

---

## Re-verification round 3 — 2026-08-28

**Everything above this line is preserved verbatim.** The report of 2026-08-13 and the re-verification
of 2026-08-27, including its G-1 / G-2 / G-3 account, are byte-identical to what they were when
committed. This section appends the verdict on those three gaps and changes nothing else. The one
edit outside this section is the frontmatter, which carries the machine-readable verdict and must
reflect the current round; the initial round's `human_verification` block is archived verbatim at the
end of this section rather than deleted.

**Trigger.** Plan **21-20** was written and executed on 2026-08-28 to close G-1 and G-2. G-3 was not
worked on at all — the brief asserts it was already passing and asked this verifier to re-measure it
rather than accept either the "accepted red" or the "green" framing. That is what happened below.

**Method.** Every claim is measured at HEAD (`5681155`). No sentence of `21-20-SUMMARY.md` is taken as
evidence; the `.planning` diffs were read directly and the build was run in this verifier's own
process. `.planning/REQUIREMENTS.md` was re-hashed, not assumed.

### The three gaps

| Gap | Prior verdict | This round | Basis |
|-----|---------------|------------|-------|
| **G-1** record accuracy | 🛑 BLOCKER | ✓ **CLOSED** | Both sites amended; residuals attributed by plan id with mechanism |
| **G-2** process | 🛑 BLOCKER | ✓ **CLOSED** | 21-19 present in `ROADMAP.md` and `STATE.md`; omission recorded as a cause |
| **G-3** live blocker | 🛑 BLOCKER | ✓ **CLOSED by measurement** | `./gradlew check` exit 0; redact BRANCH `0.93299` ≥ `0.930`, measured here |

#### G-1 — CLOSED

**`CONCERNS.md:65`.** The headline now opens `**W-A — the PROMPT carrier was closed by plan 21-19
(maintainer-decided 2026-08-13); the cookie header NAME *class* was closed by PHASE 27.**` and carries
four numbered parts: (1) what 21-19 actually closed — the passive-scan prompt carrier for **hyphenated**
name shapes, the five names this report measured, "every one of which separates its words with `-`";
(2) the three residuals attributed by plan id — **(a)** `McpToolHelpers.sanitizeHeaders` exact-name
comparison, second carrier, closed by **27-01**; **(b)** the CR/LF-escaped tool-result carrier the
`(?im)^` anchor could not reach, closed by **27-04 / 27-11 / 27-14 / 27-17**; **(c)** `COOKIE_NAME_PART`
excluding `_`, closed by **27-10**; (3) why (c) is the sharpest, stated as a mechanism rather than as
bookkeeping — `sanitizeHeadersForPrompt` is an **admitter**, so the admitter-vs-redactor difference set
was fail-**OPEN**, "the same asymmetry W-A itself was, reintroduced one character wide by the fix for
it"; (4) what is **not** being corrected — the code, with SC1–SC6 6/6 quoted. The superseded opening
claim is preserved verbatim inside the amended line, and the six Phase-27 amendments beneath it are
untouched and unrestated.

**`21-19-SUMMARY.md`.** Both named claims are amended under a single dated `CORRECTION — 2026-08-28`
marker at line 52, four numbered parts, `+46 / -0` — nothing deleted. Item (1) corrects the line-50
one-liner ("Closed the last open finding of the phase — it was not the last one"). Item (2) corrects the
line-171 §Record Correction claim, with a back-reference planted at line 219 so a reader arriving at
that section is not stranded. Item (3) records **why it stood for twelve days** — never in `ROADMAP.md`
or `STATE.md`, so nothing re-verified it. Item (4) protects the 6/6.

**Judgment call 1 — the record-note line. ACCEPTED.** The plan required both "amend in place, do not
append a seventh AMENDMENT block" and "`git diff --numstat` additions > deletions", which for a pure
single-line edit are jointly unsatisfiable. The executor amended the headline and added one labelled
`**Record note — 2026-08-28 …**` bullet. Measured: `grep -c 'AMENDMENT'` is **8 before and 8 after**,
`git diff --numstat f5003d0 HEAD -- CONCERNS.md` is **`2 1`**. Both intents are preserved — the entry
did not gain a redundant amendment round (the note is four sentences and explicitly states "the
amendments below are unaffected, unrestated and still stand as written"), and the append-and-amend
discipline holds because the superseded claim is quoted verbatim inside the line that replaced it.

**Judgment call 2 — `requirements-completed: [PRIV-05]` left unedited. ACCEPTED**, and on firmer
ground than the executor's own "destroying evidence" argument. The field is not a completion assertion:
`gsd-core/workflows/execute-plan.md:409` defines it as "**MUST** copy `requirements` array from PLAN.md
frontmatter verbatim", and `21-19-PLAN.md:13` is `requirements: [PRIV-05]`. It is **provenance** — which
requirement the plan was working on — and is correct by its own contract; editing it would desynchronise
this summary from 86 siblings that follow the same convention. The field that carries the actual claim,
`REQUIREMENTS.md` PRIV-05, is `- [ ]` and byte-frozen. The correction's item (4) names the frontmatter
explicitly and states the true status, so a reader is not misled either.

⚠️ **Advisory, not a gap.** Two other frontmatter fields still read as class-closure —
`provides[0]` ("…closing W-A") and `key-decisions[0]` ("W-A CLOSED rather than RECORDED"). They were
outside plan 21-20's stated scope, which named lines 50 and 171, and the CORRECTION marker sits four
lines below them. Worth tightening the next time this file is touched; not worth reopening a gap for.

#### G-2 — CLOSED

`ROADMAP.md:196` now carries the `21-19-PLAN.md` entry, and it does not merely list the plan — it states
what 21-19 closed (the five hyphenated variants), what it did **not** close (all three residuals), and
who closed each. Lines 198–205 record the omission as a **cause**: "`grep -c '21-19'` returned **zero**
in both files until this line was written. Because it was not in \[the record\] … became Phase 27's five
rounds of rework." `STATE.md` gained three entries (239, 240, 250) carrying the carrier-vs-class
attribution, the explicit refusal to tick PRIV-05, and the dated record-repair note. Plan count went
`18` → `19` (78fd66f) → `20/20` (5681155); 20 `21-*-PLAN.md` files exist on disk, so 20 is the right
total.

⚠️ **Warning (cosmetic, introduced by the tracking commit, not by 21-20).** The `20/20` headline's own
wave breakdown still sums to **19** — "7 original in 4 waves, 5 gap-closure in 4 waves, 6 second-round
in 5 waves, plus 1 third-round in 1 wave" — because 5681155 bumped the total without adding a fourth
term, and parked `- [x] 21-20-PLAN.md` under a bare `Plans:` heading above `**Wave 1**` instead of a
labelled fourth round. The same commit flipped the summary-table row from `19/19 | Complete |
2026-08-13` to `20/20 | In Progress |` with the completion date blanked, which is expected while a phase
is reopened for verification but should be restored on close. None of this touches G-2's substance.

#### G-3 — CLOSED by measurement. The prior round was right at its tree, and so is the brief at this one.

Run in this verifier's own process, foreground, at HEAD on a clean tree:

```
JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew check
...
> Task :jacocoMcpTreeCoverageVerification
mcp tree line coverage: 71,16% (2869/4032), floor 65,0% - MET
> Task :jacocoTestCoverageVerification
> Task :check
BUILD SUCCESSFUL in 2m 58s
```

**Exit 0.** `jacocoTestCoverageVerification` ran to completion rather than being short-circuited.
Measured independently from the `jacocoTestReport.xml` that run regenerated (mtime 14:13, after the
run began):

| Counter | Missed | Covered | Ratio | Floor (`build.gradle.kts:403,411`) | Verdict |
|---------|--------|---------|-------|------------------------------------|---------|
| redact **BRANCH** | 13 | 181 | **0.93299** | 0.930 | ✓ PASS |
| redact **LINE** | 9 | 436 | **0.97978** | 0.975 | ✓ PASS |

Suite: **1309 tests, 0 failures, 0 errors, 1 skipped** (parsed from `build/test-results/test/*.xml`).

**The environmental failure is gone, and it was environmental.** `McpSupervisorProbeTest` reports
`tests=4, failures=0, errors=0` — the `BindException: Address already in use` was a live Burp MCP
server holding the port during phase-20 UAT, exactly as the caveat described, and it is not present
now. So the earlier RED proves nothing about coverage, and this run is not inheriting that framing:
it is a fresh, complete `check` whose coverage step actually executed.

**Why the number moved, which matters more than that it moved.** The prior round measured missed 14 /
covered 180 = `0.92784` against tree `14f59cb`, and that measurement was correct there. `git diff
--stat 14f59cb HEAD -- src/` is **14 files, +3989 / −57**: Phase 28 added 22 lines to `Redaction.kt`
and roughly 3 400 lines of cookie-carrier tests (`IssueDetailCookieCarrierTest`,
`AiScanCheckDetailCookieCarrierTest`, `CookieRouteDispositionTest`, `EvidenceTailReachTest`,
`CookieCarrierInventoryTest` +376). One branch in `Redaction` moved from missed to covered. This is
**real downstream work landing**, not measurement drift and not a floor that was quietly moved — the
floors at `build.gradle.kts:403` and `:411` are still `0.975` and `0.930`, unchanged.

⚠️ **Warning, and it is the same warning the prior round raised with its sign flipped.** The gate clears
by **0.00299** over 194 total branches — **less than one branch of headroom**. Lose a single covered
branch in `Redaction` and the package returns to `13+1 / 180` = `0.92784`, RED. The prior round wrote
that "a 0.003 margin under a sealed floor is exactly the kind of drift QUAL-06 exists to catch"; a
0.003 margin *over* it is the same fact. QUAL-06 is green and has no room.

### Regression check — SC1–SC6 and the phase artifacts

Not re-litigated, per scope, but not assumed either. All **14** named guards the prior two rounds
relied on were confirmed **PASS** in this run's own JUnit XML: `hkdfMatchesRfc5869Vector`,
`cookieSectionValuesRedactedPerName`, `emittedCookieSectionValuesAreRedacted_sc1`,
`cookieTypedParametersRedacted`, `factoredKeyVocabularyMatchesItsReadableSpecification`,
`oversizeBodySecretDoesNotSurvive`, `windowedScanRedactsJsonPairWhoseValueStraddlesTheCut`,
`customPatternRedactsInStrictAndBalanced`, `offModePreservesBodies`,
`cookieSectionBlankEntriesDoNotCollapseSpan`, `cookieEmitterBoundStaysWithinTheRedactorBound`,
`truncationLoggerThatThrowsDoesNotAbortRedaction`, `zeroWidthPatternsAreRejectedWithoutRunningAnyProbe`,
`cookieHeaderNameVariantsAreStripped`. **SC1–SC6: 6/6 still held.**

### Prohibition audit — plan 21-20

| Prohibition | Status | Evidence |
|-------------|--------|----------|
| MUST NOT re-score, soften or edit `21-VERIFICATION.md` | ✓ HELD | `git log f5003d0..HEAD -- 21-VERIFICATION.md` is empty. The plan did not mark its own homework. |
| MUST NOT tick PRIV-05 or edit `.planning/REQUIREMENTS.md` | ✓ HELD | sha256 re-measured: `9b3219662ec0d007c1c82d64eed3ef2698bd306ce69f01205ac9bbc3f42fcfb4`, matches. PRIV-05 is `- [ ]` at line 23. `git log f5003d0..HEAD -- REQUIREMENTS.md` empty. |
| MUST NOT claim the corrections make PRIV-05 "by any path" true | ✓ HELD | `STATE.md:240` says the opposite explicitly, naming `AR-27-08` and `D-28-09`. The `CONCERNS.md` and summary corrections make no completeness claim. |
| MUST NOT touch any file under `src/` | ✓ HELD | `git status --porcelain -- src/` prints nothing; `--stat` on each of the seven commits `f5003d0..HEAD` shows only `.planning/` paths. Zero code, as designed. |
| No debt markers introduced | ✓ HELD | `TBD` / `FIXME` / `XXX` absent from every added line across all four modified files. |

### Verdict

**Status: `passed`. Score 9/9** — SC1–SC6 6/6, plan 21-19 3/3.

Two of the three blockers were closed by work and one by measurement, and the distinction is worth
keeping in the record. G-1 and G-2 were real record defects and plan 21-20 repaired them without
touching a line of code, which is the correct shape for a record defect. G-3 was never a phase-21
regression at all: it was a real coverage shortfall at the tree the prior round measured, closed by
Phase 28's tests landing afterwards, and confirmed here by running the gate rather than by reading
anyone's account of it — including the brief's, which turned out to be right.

What the phase now claims about itself matches what it shipped. `21-19` closed the passive-scan prompt
carrier for hyphenated names; Phase 27 closed the class; Phase 28 owns `AR-27-08` and accepted
`D-28-09`. **PRIV-05 remains `- [ ]`, which is still the correct state** — "by any path" is not
literally true today, and the record now says so at every site that used to say otherwise.

### Appendix — archived `human_verification` block from the initial round

Preserved verbatim from the pre-2026-08-28 frontmatter. All four were resolved: items 1, 3 and 4 by
`21-HUMAN-UAT.md` (`status: complete`, 3/3 passed, 0 issues), and item 2 — the W-A maintainer
disposition — by the CLOSE-over-RECORD decision of 2026-08-13, whose *scope* is what G-1 corrected.
Recorded here so the frontmatter can carry the current verdict without deleting them.

```yaml
human_verification:
  - test: "Load the fat JAR in a live Burp, proxy a request carrying `Cookie: JSESSIONID=…; PHPSESSID=…; connect.sid=…; auth_token=…; csrftoken=…; remember_me=…`, trigger a passive AI scan in STRICT and then BALANCED, and inspect the outbound prompt via the context preview / AI request log."
    expected: "None of the six cookie values appears anywhere in the prompt; each cookie NAME is still present as `NAME=[REDACTED]`; the `=== PARAMETERS ===` section shows `(COOKIE)` lines with values replaced and `(URL)` / `(BODY)` lines untouched."
    why_human: "`PassiveAiScannerAnalysis.doAnalysis` needs a live `MontoyaApi`, a backend session and `ScanKnowledgeBase` state. The Wave-0 extractions move the PRIV-05-relevant logic out of that reach and I verified it end to end against the real emitter, but the surrounding `doAnalysis` orchestration itself is not unit-reachable."
  - test: "MAINTAINER DISPOSITION (not a test): decide how to treat the cookie-header NAME VARIANT residual — `Cookie2`, `X-Cookie`, `Set-Cookie2`, `X-Original-Cookie`, `X-Forwarded-Cookie`. `sanitizeHeadersForPrompt` admits any header whose lowercased name contains `cookie` (or starts with `x-`), but `cookieHeaderRegex` / `setCookieHeaderRegex` anchor on the exact names `^cookie:` / `^set-cookie:`, so these five reach the prompt verbatim under STRICT and BALANCED. Measured, not inferred (see Anti-Patterns table)."
    expected: "Either (a) the class is added to `.planning/codebase/CONCERNS.md`'s existing 'Redaction regex coverage gaps' entry beside the already-accepted `authHeaderRegex` vendor-header residual, so the record claims only what ships (the phase's own D-08 REFINED standard); or (b) it is closed by widening the two cookie header regexes. Until one of the two happens, PRIV-05's lead sentence — 'Cookie values do not reach an AI backend … by any path' — is stronger than what ships."
    why_human: "Scope and record decision, not an implementation defect. Pre-existing since the first commit, explicitly declared out of scope at planning time (`21-CONTEXT.md`: 'They are not the bug'), and its sibling class is already an accepted documented residual. Only a maintainer can choose accept-and-record versus close."
  - test: "In a live Burp, set Privacy to OFF with at least one custom redaction pattern configured, then with none; read the ChatPanel privacy line, the ContextPreviewDialog banner, the PrivacyPill tooltip and all four SettingsPanelActions OFF arms."
    expected: "No string claims OFF means no redaction. With patterns configured the wording says built-in redaction is disabled but custom patterns still apply; with none configured it says built-in redaction is disabled and no custom patterns are configured."
    why_human: "D-07 covers Swing label strings; this project has no UI integration-test harness (recorded in CONCERNS.md 'UI layer has no integration tests')."
  - test: "Unload the extension in a live Burp while a passive scan is in flight; then reload it with a hand-edited preferences file containing a pathological custom pattern."
    expected: "No exception surfaces from `Redaction.apply` during teardown, and the pathological persisted pattern is dropped at startup rather than seeded."
    why_human: "`App.shutdown()`'s `Redaction.truncationLogger = null` step and `App.initialize`'s `isPatternSafe` seeding filter both need a live `MontoyaApi`. 21-18 states this plainly and identifies `maybeLogTruncation`'s `runCatching` as the automated defence that holds regardless — I confirmed that wrap exists and is guarded by `truncationLoggerThatThrowsDoesNotAbortRedaction`."
```

---

_Re-verified (round 3): 2026-08-28_
_Verifier: Claude (gsd-verifier) — goal-backward, FORCE stance; build run in-process, coverage measured from the regenerated report_
