---
phase: 21
slug: redaction-completeness
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-08-11
---

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

*Pending plan creation — task IDs are assigned by `gsd-planner`. The requirement → test mapping below is
fixed by `21-RESEARCH.md` §"Phase Requirements → Test Map"; the planner binds each row to a task ID.*

| Requirement | Behaviour | Threat Ref | Test Type | Automated Command | File Exists | Status |
|-------------|-----------|------------|-----------|-------------------|-------------|--------|
| PRIV-05 / SC1 | Each of `JSESSIONID`, `PHPSESSID`, `connect.sid`, `auth_token`, `csrftoken` has its value absent from the redacted prompt in STRICT **and** BALANCED — asserted per name | — | unit | `./gradlew test --tests "*RedactionTest.cookieSectionValuesRedactedPerName*"` | ✅ extend | ⬜ pending |
| PRIV-05 / SC1 | Same, asserted against the **real emitted blob** from the extracted prompt builder | — | unit | `./gradlew test --tests "*PassiveAiScannerPromptRedactionTest*"` | ❌ W0 | ⬜ pending |
| PRIV-05 / SC1 | Section-header poisoning: a decoy `=== COOKIES ===` earlier in the blob does not shield the real section | injection | unit (security regression) | `./gradlew test --tests "*RedactionTest.cookieSectionDecoyDoesNotShieldRealSection*"` | ❌ W0 | ⬜ pending |
| PRIV-05 / SC2 | A `COOKIE`-typed param line loses its value, keeps its name and ` (COOKIE)` suffix; `(URL)` / `(BODY)` untouched | — | unit | `./gradlew test --tests "*RedactionTest.cookieTypedParametersRedacted*"` | ✅ extend | ⬜ pending |
| PRIV-05 / SC2 | `"${p.name()}=${value} (${p.type().name})"` really produces the shape the rule keys on | — | unit (Mockito `ParsedHttpParameter`) | `./gradlew test --tests "*PassiveAiScannerPromptRedactionTest.parameterLineShape*"` | ❌ W0 | ⬜ pending |
| PRIV-05 / SC3 | 31 must-redact keys redacted across all three consumer contexts (query, form, JSON) | — | unit (parameterised) | `./gradlew test --tests "*RedactionTest.sensitiveKeyNamesRedacted*"` | ✅ extend | ⬜ pending |
| PRIV-05 / SC3 | 21 must-not-redact keys untouched — **regression guard**, green before and after by design; label as such | over-redaction | unit (parameterised) | `./gradlew test --tests "*RedactionTest.benignKeyNamesNotRedacted*"` | ✅ extend | ⬜ pending |
| PRIV-06 / SC4 | Input > `MAX_REDACTION_BODY_CHARS` with a secret **past** the old cut-off does not retain the secret. **Red before green** | fail-open | unit | `./gradlew test --tests "*RedactionTest.oversizeBody*"` | ✅ **rewrite** `oversizeBodySkippedSafely` | ⬜ pending |
| PRIV-06 / SC4 | A pathological custom pattern on an oversized input yields a **marker**, never passthrough | fail-open | unit | `./gradlew test --tests "*RedactionTest.oversizeBodyFailsClosed*"` | ✅ extend | ⬜ pending |
| PRIV-06 / D-03 | The truncation signal fires; a second event inside the window is suppressed | — | unit (injected `nowMs`) | `./gradlew test --tests "*RedactionTest.truncationSignal*"` | ✅ extend | ⬜ pending |
| PRIV-06 / D-05 | A custom pattern redacts under `PrivacyMode.OFF` | — | unit | `./gradlew test --tests "*RedactionTest.customPattern*"` | ✅ **invert OFF limb** | ⬜ pending |
| PRIV-06 / D-05 | OFF **with no custom patterns** returns byte-identical output | — | unit | `./gradlew test --tests "*RedactionTest.offMode*"` | ✅ existing | ⬜ pending |
| PRIV-06 / D-06 | The scanner path applies custom patterns under OFF — proves the short-circuit is gone | — | unit on extracted seam | `./gradlew test --tests "*PassiveAiScannerPromptRedactionTest.offStillAppliesCustomPatterns*"` | ❌ W0 | ⬜ pending |
| SC6 | Whole `redact` package green, including `hkdfMatchesRfc5869Vector` | — | unit | `./gradlew test --tests "com.six2dez.burp.aiagent.redact.*"` | ✅ existing | ⬜ pending |
| SC5 / D-08 | ADR-14 present in `DECISIONS.md` | — | manual review | — | manual | ⬜ pending |
| D-07 | User-facing OFF strings no longer claim OFF means no redaction | — | manual review (Swing strings) | — | manual | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

Three mechanical, Montoya-free extractions from `PassiveAiScannerAnalysis` are what make SC1/SC2/D-06
testable end-to-end. Every input at the call site is already a plain `String`/`List<String>`.

- [ ] Extract `internal fun buildScanMetadataText(...)` from `PassiveAiScannerAnalysis.doAnalysis:342-391` into `PassiveAiScannerPrompts.kt` (the file already hosts `truncateWithEllipsis`, `buildCompactRequestBody`, `buildCompactResponseBody` as top-level `internal fun`s)
- [ ] Extract `internal fun formatParamLine(name: String, value: String, type: String): String` from the inline lambda at `PassiveAiScannerAnalysis.kt:238-241` — so the ` (COOKIE)` shape the SC2 rule keys on is asserted at its source
- [ ] Extract `internal fun redactScanMetadata(metadataText: String, mode: PrivacyMode, hostSalt: String): String` wrapping `PassiveAiScannerAnalysis.kt:393-402` — so D-06's deletion is asserted, not merely inspected
- [ ] `src/test/kotlin/com/six2dez/burp/aiagent/scanner/PassiveAiScannerPromptRedactionTest.kt` — new file covering SC1/SC2 end-to-end plus D-06
- [ ] `SafeRegexTest.kt` — add coverage for the `timedOut` reporting flag (the existing fail-open assertion at `:44` stays green unchanged)

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

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 30s
- [ ] SC4's oversize test is **red before green** against pre-fix `Redaction.kt`
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
