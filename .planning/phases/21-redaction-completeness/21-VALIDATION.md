---
phase: 21
slug: redaction-completeness
status: approved
nyquist_compliant: true
wave_0_complete: false
created: 2026-08-11
approved: 2026-08-11
---

> **`wave_0_complete` stays `false` deliberately.** The five Wave 0 items are *assigned* (21-01 T1/T2/T3,
> 21-02 T1) but not yet *built* — the field flips during wave 1 execution, not at planning time.
> `nyquist_compliant: true` is a planning-time property and was verified by `gsd-plan-checker`:
> 19/19 tasks carry an automated verify, no watch-mode flags, no three-task sampling gap.

# Phase 21 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.
> Derived from `21-RESEARCH.md` §"Validation Architecture". All commands require the JDK 21 prefix.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit Jupiter 6.0.3 (`useJUnitPlatform()`) + Mockito-Kotlin 5.4.0 |
| **Config file** | `build.gradle.kts` (`tasks.test`, `-PexcludeHeavyTests`) |
| **Quick run command** | `JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test --tests "com.six2dez.burp.aiagent.redact.*" -q` |
| **Full suite command** | `JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test -q` |
| **Lint/static gate** | `JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew ktlintCheck detekt` — ktlint strict; detekt baseline must not grow (QUAL-07) |
| **Estimated runtime** | quick ~seconds; full suite minutes |

**Suite classification constraint:** new test classes must NOT be named `*IntegrationTest` / `*ConcurrencyTest` / `*BackpressureTest` / `*RestartPolicyTest` — those are excluded from the PR gate.

**Pre-change baseline (verified):** `./gradlew test --tests "com.six2dez.burp.aiagent.redact.*"` exits 0 today.

---

## Sampling Rate

- **After every task commit:** `JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test --tests "com.six2dez.burp.aiagent.redact.*" -q`
- **After every plan wave:** `JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test -q` plus `./gradlew ktlintCheck detekt`
- **Before `/gsd-verify-work`:** full suite green AND detekt baseline not grown
- **Max feedback latency:** ~30 seconds (quick run)

---

## Per-Task Verification Map

*Bound to plan/task IDs 2026-08-11 after planning. All 16 rows trace to a named task and a runnable
gate — verified independently by `gsd-plan-checker`. Mapping fixed by `21-RESEARCH.md` §"Phase
Requirements → Test Map".*

| Requirement | Behaviour | Threat Ref | Test Type | Automated Command | Owning task | Status |
|-------------|-----------|------------|-----------|-------------------|-------------|--------|
| PRIV-05 / SC1 | Each of `JSESSIONID`, `PHPSESSID`, `connect.sid`, `auth_token`, `csrftoken` has its value absent from the redacted prompt in STRICT **and** BALANCED — asserted per name | — | unit | `./gradlew test --tests "*RedactionTest.cookieSectionValuesRedactedPerName*"` | **21-05 T2** (rule in T1) | ⬜ pending |
| PRIV-05 / SC1 | Same, asserted against the **real emitted blob** from the extracted prompt builder | — | unit | `./gradlew test --tests "*PassiveAiScannerPromptRedactionTest.emittedCookieSectionValuesAreRedacted_sc1*"` | **21-05 T3** (seam from 21-01 T3) | ⬜ pending |
| PRIV-05 / SC1 | Section-header poisoning: a decoy `=== COOKIES ===` earlier in the blob does not shield the real section | T-21-01 | unit (security regression) | `./gradlew test --tests "*RedactionTest.cookieSectionDecoyDoesNotShieldRealSection*"` | **21-05 T2** | ⬜ pending |
| PRIV-05 / SC2 | A `COOKIE`-typed param line loses its value, keeps its name and ` (COOKIE)` suffix; `(URL)` / `(BODY)` untouched | — | unit | `./gradlew test --tests "*RedactionTest.cookieTypedParametersRedacted*"` | **21-05 T2** (rule in T1) | ⬜ pending |
| PRIV-05 / SC2 | `"${p.name()}=${value} (${p.type().name})"` really produces the shape the rule keys on | — | unit (Mockito `ParsedHttpParameter`) | `./gradlew test --tests "*PassiveAiScannerPromptRedactionTest.parameterLineShape*"` | **21-01 T3** | ⬜ pending |
| PRIV-05 / SC3 | 31 must-redact keys redacted across all three consumer contexts (query, form, JSON) | — | unit (parameterised) | `./gradlew test --tests "*RedactionTest.sensitiveKeyNamesRedacted*"` | **21-04 T2** (expr in T1) | ⬜ pending |
| PRIV-05 / SC3 | 21 must-not-redact keys untouched — **regression guard**, green before and after by design; label as such | T-21-06 | unit (parameterised) | `./gradlew test --tests "*RedactionTest.benignKeyNamesNotRedacted*"` | **21-04 T2** | ⬜ pending |
| PRIV-05 / SC3 / D-13 | camelCase keys (`authToken`, `accessToken`, `userSessionId`) redact; the three accepted FPs (`codeName`, `keyName`, `tokenCount`) are asserted **as accepted** | T-21-06 | unit (parameterised) | `./gradlew test --tests "*RedactionTest.camelCase*"` | **21-04 T2** | ⬜ pending |
| PRIV-06 / D-14 | `replaceAllSafeReporting` surfaces `timedOut`; `replaceAllSafe` keeps its fail-soft contract | T-21-04 | unit | `./gradlew test --tests "*SafeRegexTest*"` | **21-02 T1** | ⬜ pending |
| PRIV-06 / SC4 | Input > `MAX_REDACTION_BODY_CHARS` with a secret **past** the old cut-off does not retain the secret. **Red before green** | T-21-02 | unit | `./gradlew test --tests "*RedactionTest.oversizeBody*"` | **21-06 T3**, gated by **21-07 T1** | ⬜ pending |
| PRIV-06 / SC4 | A pathological custom pattern on an oversized input yields a **marker**, never passthrough | T-21-03 | unit | `./gradlew test --tests "*RedactionTest.oversizeBodyFailsClosed*"` | **21-06 T3**, gated by **21-07 T1** | ⬜ pending |
| PRIV-06 / D-03 | The truncation signal fires; a second event inside the window is suppressed | T-21-05 | unit (injected `nowMs`) | `./gradlew test --tests "*RedactionTest.truncationSignal*"` | **21-06 T2** | ⬜ pending |
| PRIV-06 / D-05 | A custom pattern redacts under `PrivacyMode.OFF` | — | unit | `./gradlew test --tests "*RedactionTest.customPattern*"` | **21-06 T3** (behaviour in T1) | ⬜ pending |
| PRIV-06 / D-05 | OFF **with no custom patterns** returns byte-identical output | — | unit (canary) | `./gradlew test --tests "*RedactionTest.offMode*"` | **21-06 T1** | ⬜ pending |
| PRIV-06 / D-06 | The scanner path applies custom patterns under OFF — proves the short-circuit is gone | — | unit on extracted seam | `./gradlew test --tests "*PassiveAiScannerPromptRedactionTest.offStillAppliesCustomPatterns*"` | **21-06 T3** (scanner half 21-01, MCP half 21-03 T1) | ⬜ pending |
| SC6 | Whole `redact` package green, including `hkdfMatchesRfc5869Vector` | — | unit | `./gradlew test --tests "com.six2dez.burp.aiagent.redact.*"` | **all 7 plans** (plan-level `<verification>`) | ⬜ pending |
| SC5 / D-08 | ADR-14 present in `DECISIONS.md`, scoped to the body stage | T-21-26 | manual review | `grep -c '^## ADR-14' DECISIONS.md` | **21-07 T2** | ⬜ pending |
| D-07 | All four user-facing OFF strings no longer claim OFF means no redaction | — | manual review (Swing strings) | — | **21-03 T2** (ChatPanel, ContextPreviewDialog, PrivacyPill) + **21-03 T3** (four `SettingsPanelActions` arms) | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Named-Guard Selectors

Added by **21-16 T2/T3** (`21-REVIEW-2.md` W-07, IN-03).

This codebase uses "the named guard is X" comments in `Redaction.kt` and `RedactionTest.kt` as a
load-bearing safety mechanism: several are the only thing telling a contributor that deleting a line
reopens a leak. Two of them were found pointing at methods that do not exist, which devalues every
other one. A method name inside a comment is invisible to the compiler, so the guards that carry the
most weight are registered here — a rename that does not update the comment then shows up as a
**failing selector** rather than as a comment quietly rotting.

| Guarded behaviour | Guard test | Automated selector |
|-------------------|-----------|--------------------|
| A cookie element shaped like a section header cannot terminate the emitted cookie span (the residual is closed at the emitter, and only there) | `PassiveAiScannerPromptRedactionTest.poisonedCookieHeaderCannotTerminateTheCookieSection` | `./gradlew test --tests "*PassiveAiScannerPromptRedactionTest.poisonedCookieHeaderCannotTerminateTheCookieSection"` |
| A `jsonSecretKeyRegex` pair whose QUOTED VALUE straddles a window cut is still redacted (21-REVIEW-2 CR-01 — a leak, not a fail-closed drop) | `RedactionTest.windowedScanRedactsJsonPairWhoseValueStraddlesTheCut` | `./gradlew test --tests "*RedactionTest.windowedScanRedactsJsonPairWhoseValueStraddlesTheCut"` |
| `windowEnd`'s lookahead stops at exactly `MAX_JSON_BOUNDARY_LOOKAHEAD_LINES` and the window stays line-aligned there (IN-03) | `RedactionTest.windowEndStopsAtTheJsonBoundaryLookaheadCap` | `./gradlew test --tests "*RedactionTest.windowEndStopsAtTheJsonBoundaryLookaheadCap"` |

All three require the JDK 21 prefix: `JAVA_HOME=$(/usr/libexec/java_home -v 21)`.

---

## Wave 0 Requirements

Three mechanical, Montoya-free extractions from `PassiveAiScannerAnalysis` are what make SC1/SC2/D-06
testable end-to-end. Every input at the call site is already a plain `String`/`List<String>`.

All five are **assigned** and land in wave 1 (`21-01`, `21-02`). They are not yet built — see the
frontmatter note on `wave_0_complete`.

- [ ] **→ 21-01 T2.** Extract `internal fun buildScanMetadataText(...)` from `PassiveAiScannerAnalysis.doAnalysis:342-391` into `PassiveAiScannerPrompts.kt` (the file already hosts `truncateWithEllipsis`, `buildCompactRequestBody`, `buildCompactResponseBody` as top-level `internal fun`s). Carries `@Suppress("LongParameterList")` — 14 params vs `detekt.yml`'s `functionThreshold: 10`, and QUAL-07 forbids growing the baseline
- [ ] **→ 21-01 T1.** Extract `internal fun formatParamLine(name: String, value: String, type: String): String` from the inline lambda at `PassiveAiScannerAnalysis.kt:238-241` — so the ` (COOKIE)` shape the SC2 rule keys on is asserted at its source
- [ ] **→ 21-01 T1.** Extract `internal fun redactScanMetadata(metadataText: String, mode: PrivacyMode, hostSalt: String): String` wrapping `PassiveAiScannerAnalysis.kt:393-402` — so D-06's deletion is asserted, not merely inspected
- [ ] **→ 21-01 T3.** `src/test/kotlin/com/six2dez/burp/aiagent/scanner/PassiveAiScannerPromptRedactionTest.kt` — new file covering SC1/SC2 end-to-end plus D-06. Must NOT assert current leak behaviour, or waves 2–3 turn it red
- [ ] **→ 21-02 T1.** `SafeRegexTest.kt` — add coverage for the `timedOut` reporting flag (the existing fail-open assertion at `:44` stays green unchanged)

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| ADR-14 recorded in `DECISIONS.md` covering both PRIV-06 halves | SC5 / D-08 | Documentation artifact, no test harness | Read repo-root `DECISIONS.md`; confirm ADR-14 exists and states the custom-patterns-under-OFF rule and the body-stage fail-closed rule |
| User-facing OFF strings corrected | D-07 | Swing label strings with no UI test harness in this project | Code review of `ui/ChatPanel.kt:1146`, `ui/components/ContextPreviewDialog.kt:122`, `ui/SettingsPanelActions.kt:236-251` (+ `ui/components/PrivacyPill.kt:41` if in scope) |
| Redaction behaviour inside a live Burp | PRIV-05 | `doAnalysis` needs `MontoyaApi`, a backend session and `ScanKnowledgeBase` state | Load the fat JAR in Burp, proxy a request with session cookies, trigger a passive AI scan in STRICT and BALANCED, inspect the context preview |

**Not testable without a live Burp:** `doAnalysis` as a whole. The three Wave 0 extractions move 100% of
the PRIV-05-relevant logic out of that reach.

---

## SC6 Regression Surface — deliberate exceptions

SC6 requires the existing `RedactionTest` suite to stay green. Two assertions are **deliberately
inverted** by locked decisions — these are not regressions and must be stated as such in
`21-VERIFICATION.md`:

| Test | Line | Fate | Driver |
|------|------|------|--------|
| `oversizeBodySkippedSafely` | `RedactionTest.kt:379` | **rewritten** — currently asserts the fail-open as correct | D-01 |
| `customPatternRedactsInStrictAndBalanced` (OFF limb) | `RedactionTest.kt:342-345` | **inverted** | D-05 |

13 of 15 `RedactionTest` tests stay green untouched. `hkdfMatchesRfc5869Vector` (`:239`) is SC6's named
vector — `Redaction.kt:167-227` must not be touched. Canaries that prove no over-redaction crept in:
`balancedModeRedactsUrlTokensInQueryStrings` (`name=alice` survives), `bodyFormLeadingFieldRedacted`
(`user=bob` survives), `bodyJsonSecretKeysRedacted` (`"name":"alice"` survives), `offModePreservesBodies`
(byte-identity under OFF).

Also expected green in-package: `SafeRegexTest`, `RedactionHostMapBoundTest`, `EntropyTest`,
`SecretShapesTest`, `SecretTripwireTest`, `SecretTripwireGateTest`, `SecretTripwireHooksTest`.

---

## Validation Sign-Off

Verified by `gsd-plan-checker` against the seven plan files, 2026-08-11.

- [x] All tasks have `<automated>` verify or Wave 0 dependencies — 19/19
- [x] Sampling continuity: no 3 consecutive tasks without automated verify
- [x] Wave 0 covers all MISSING references — all five bound to 21-01 / 21-02
- [x] No watch-mode flags
- [x] SC4's oversize test is **red before green** against pre-fix `Redaction.kt` — gated by 21-07 T1, which runs alone in wave 4 and reinstates the defect with a one-line surgical mutation rather than a whole-file rollback (a rollback would not compile against the rest of the phase, and a compile failure proves nothing). Expected failure set is exactly `oversizeBodySecretDoesNotSurvive` and `oversizeBodyFailsClosed`
- [x] `nyquist_compliant: true` set in frontmatter
- [ ] Feedback latency < 30s — **known deviation.** Six task-level gates run the full `test ktlintCheck detekt` (minutes), not the package-scoped quick run. Deliberate: those tasks touch `ui/`, `mcp/` and `config/` outside the `redact` package, so a package-scoped gate would not see their regressions. The fast run remains the per-commit sampling gate.

**Approval:** approved 2026-08-11
