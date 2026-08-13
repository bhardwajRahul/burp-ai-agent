---
phase: 21-redaction-completeness
verified: 2026-08-13T08:41:15Z
status: human_needed
score: 6/6 must-haves verified
overrides_applied: 0
re_verification:
  previous_status: none
  previous_score: n/a
  note: "Initial verification. No prior 21-VERIFICATION.md existed; 21-VALIDATION.md is the planning-time validation strategy, not a verification record."
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
