---
phase: 26
slug: coverage-static-analysis-debt-docs
# status lifecycle: draft (seeded by plan-phase) → validated (set by validate-phase §6)
status: validated
nyquist_compliant: true
wave_0_complete: true
created: 2026-08-24
---

# Phase 26 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.
> Reconstructed retroactively by `/gsd-validate-phase 26` on 2026-08-24 (State B — the phase
> executed without a seeded VALIDATION.md). Every status below was re-measured against the tree at
> `1.0.0`, not copied from a SUMMARY.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit Jupiter (`useJUnitPlatform()`) + mockito-kotlin + `kotlin("test")`, Kotlin 2.1.21 |
| **Config file** | `build.gradle.kts` (`tasks.test`, ~`:152-290`) — no separate config file |
| **Quick run command** | `JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test --tests '*<SuiteName>*'` |
| **Full suite command** | `JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew check` |
| **Coverage gate** | `./gradlew jacocoTestCoverageVerification jacocoMcpTreeCoverageVerification` (both wired into `check`) |
| **Static analysis** | detekt (baseline `detekt-baseline.xml`, config `detekt.yml`) + ktlint |
| **PR-gate equivalent** | `./gradlew test -PexcludeHeavyTests=true` |
| **Test JVM args** | `-ea -Djava.awt.headless=true` |
| **Suite size at sign-off** | **159 classes / 1133 tests / 0 failures / 1 skip** |
| **Estimated runtime** | quick ~5–20 s per suite; full `check` ~3 min cold |

⚠ **JDK 21 is required** — `JAVA_HOME=$(/usr/libexec/java_home -v 21)`. The default JDK 25 on this
machine breaks Gradle 8.12.1.

⚠ **Known flake, not a regression.** `RedactionTest` can fail under CPU load via `SafeRegex`'s 50 ms
wall-clock deadline. Re-run before investigating.

---

## Sampling Rate

- **After every task commit:** `./gradlew test --tests '*<the suite this task touches>*'`
- **After every plan wave:** `./gradlew test jacocoTestReport detekt ktlintCheck` — **detekt is not
  optional in this phase.** QUAL-07 forbids growing the baseline and the baseline is exact-string
  keyed per file, so a new or moved file is the most likely wave-merge break.
- **Before `/gsd-verify-work`:** full `./gradlew check` green — which now includes both coverage
  verification tasks and `DetektBaselineBoundTest`.
- **Max feedback latency:** < 30 s for a single suite.

⚠ **Suite-naming constraint.** `tasks.test` excludes five filename suffixes under
`-PexcludeHeavyTests=true`: `*IntegrationTest`, `*ConcurrencyTest`, `*BackpressureTest`,
`*RestartPolicyTest`, `*SupervisionTest`. No phase-26 suite matches an excluded suffix; keep it that
way.

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 26-01-01 | 01 | 1 | QUAL-06 (SC1) | T-26-01-01/02/03 | An argv character outside `[A-Za-z0-9._/-]` is single-quoted before reaching `sh -c`; `'` uses the POSIX close/escape/reopen form | unit + structural | `./gradlew test --tests '*ShellEscapeTest'` | ✅ `ShellEscapeTest.kt` | ✅ green (13) |
| 26-01-02 | 01 | 1 | QUAL-06 (SC2) | T-26-01-04/05 | Allowlisted arguments pass through byte-identical — the fix cannot over-quote and break working CLI backends | unit | `./gradlew test --tests '*CliCommandHelpersTest' --tests '*CliTimeoutMessageTest' --tests '*CopilotCommandBuilderTest'` | ✅ 3 suites | ✅ green |
| 26-02-01 | 02 | 1 | QUAL-06 (SC2) | T-26-02-01/02/03 | `sanitizeHeaders` matches case-insensitively per privacy mode; `resolveReportPath` rejects escape above `user.home`; `maybeAnonymizeUrl` falls back rather than throwing | unit + red probe | `./gradlew test --tests '*McpToolHelpersTest'` | ✅ `McpToolHelpersTest.kt` | ✅ green (61) |
| 26-02-02 | 02 | 1 | QUAL-06 (SC2) | T-26-02-06/07 | The MCP body cap is a BYTE bound and holds on multi-byte UTF-8 | unit | `./gradlew test --tests '*McpToolHelpersTest'` | ✅ 11 nested classes | ✅ green |
| 26-02-03 | 02 | 1 | QUAL-06 (SC2) | T-26-02-04/05 | A tool-input payload missing a required field FAILS rather than defaults; blank host / non-positive port yield no `HttpService` | unit | `./gradlew test --tests '*SerializationTest' --tests '*McpToolModelsTest'` | ✅ both | ✅ green (26 + 32) |
| 26-03-01 | 03 | 1 | QUAL-06 (SC2) | T-26-03-01/02/03 | An IPv4-mapped IPv6 literal classifies identically to its hex spelling, in both directions, with zero name resolution | unit + JVM-wide counter | `./gradlew test --tests '*SsrfGuard*' --tests '*Ipv4LiteralTest'` | ✅ 3 suites | ✅ green (40) |
| 26-03-02 | 03 | 1 | QUAL-07 | T-26-03-04/05 | `isTokenWeak` never fires against `generateToken()`'s own output; the floor and that generator are asserted as a RELATION | unit | `./gradlew test --tests '*McpTokenStrengthTest'` | ✅ `McpTokenStrengthTest.kt` | ✅ green (7) |
| 26-03-03 | 03 | 1 | QUAL-06 (SC2) | T-26-03-06/07 | `RedactionPolicy.fromMode`'s flag triple asserted per flag per mode; `SecretCipher.decrypt` returns `""` on auth failure, never the raw ciphertext | unit | `./gradlew test --tests '*RedactionPolicyTest' --tests '*SecretCipherTest'` | ✅ both | ✅ green (28) |
| 26-04-01 | 04 | 1 | QUAL-07 (SC4) | T-26-04-01 | — (disposition choice, not a behavior) | **checkpoint:decision** | — | n/a | ⬜ manual-only |
| 26-04-02 | 04 | 1 | QUAL-07 (SC4) | T-26-04-02/03/05 | The EDT check's KDoc states it has no production effect; the wording cannot drift back into reading as a guarantee | structural | `./gradlew test --tests '*ChatPanelEdtGuardTest'` | ✅ `ChatPanelEdtGuardTest.kt` | ✅ green (6) |
| 26-04-03 | 04 | 1 | QUAL-07 (SC4) | T-26-04-04/06 | All four `@GuardedBy("EDT")` sites are uniform; no bypass switch exists | structural | `./gradlew test --tests '*ChatPanelEdt*'` | ✅ + `ChatPanelEdtConfinementTest.kt` | ✅ green (29) |
| 26-05-01 | 05 | 2 | DOC-03 (SC5) | T-26-05-01/03/05 | `SECURITY.md` carries SEC-04 and PRIV-05 with affected versions, impact, fixed version and a user action, and states that no CVE/GHSA was issued | doc-guard | `./gradlew test --tests '*SecurityDocsTest'` | ✅ `SecurityDocsTest.kt` | ✅ green (21) |
| 26-05-02 | 05 | 2 | DOC-03 (SC6) | T-26-05-02/06 | Every at-rest claim names `secret.master.key.v1` beside the ciphertext — in-repo half | doc-guard | `./gradlew test --tests '*SecurityDocsTest'` | ✅ 6 `inputs.file` declarations | ✅ green (in-repo) |
| 26-05-03 | 05 | 2 | DOC-03 (SC6) | T-26-05-04 | The GitBook site states the at-rest caveat and documents the confirmation flow | **checkpoint:human-action** | — (separate repository) | n/a | ⬜ manual-only |
| 26-06-01 | 06 | 2 | QUAL-07 / DOC-03 | T-26-06-01/02 | ADR-16 lists ≥ 7 `Residual:` bullets — a bound equal to the shipped count, so a single-bullet deletion goes red | doc-guard | `./gradlew test --tests '*DecisionsAdrTest'` | ✅ `DecisionsAdrTest.kt` | ✅ green (6) |
| 26-06-02 | 06 | 2 | QUAL-07 | T-26-06-03/04/07 | ADR-17 names all three QUAL-07 subjects by symbol; `.kt:NNN` citations do not increase | doc-guard | `./gradlew test --tests '*DecisionsAdrTest'` | ✅ + `inputs.file("DECISIONS.md")` | ✅ green |
| 26-06-03 | 06 | 2 | QUAL-07 | T-26-06-05/06 | A non-loopback bind conflict reports the real reason and names the bound host; the loopback gate stays | unit | `./gradlew test --tests '*McpSupervisorConnectionTest'` | ✅ `McpSupervisorConnectionTest.kt` | ✅ green (8) |
| 26-07-01 | 07 | 3 | QUAL-07 (SC3) | T-26-07-01/02/03 | `detekt.yml` untouched; the baseline shrinks and is never appended to | static + bound test | `./gradlew detekt test --tests '*DetektBaselineBoundTest'` | ✅ `DetektBaselineBoundTest.kt` **(added by this audit)** | ✅ green (2) |
| 26-07-02 | 07 | 3 | QUAL-07 (SC3) | T-26-07-04/06 | No load-bearing declaration was deleted; the suite stays green and `shadowJar` still builds | full gate | `./gradlew check` | ✅ | ✅ green |
| 26-07-03 | 07 | 3 | QUAL-06 (SC2) | T-26-07-05/07 | `resolveReportPath`'s guards keep their exception type and message through the `UseRequire` conversion; the 14 coverage floors hold | unit + coverage gate | `./gradlew test --tests '*McpToolHelpersTest' jacocoTestCoverageVerification jacocoMcpTreeCoverageVerification` | ✅ **(gates added by this audit)** | ✅ green |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

**Totals:** 19 of 21 tasks have automated verification. 2 are checkpoint tasks with no automatable
behavior (see Manual-Only). Phase-26 guard suites re-run by this audit: **253 tests, 0 failures,
0 skipped**.

---

## Wave 0 Requirements

Existing infrastructure covered all phase requirements — JUnit 5, jacoco, detekt and ktlint were
already wired before the phase opened, and every plan added suites into the existing tree. No Wave 0
scaffolding was needed.

This audit added two gates that did not exist at phase close:

- [x] `src/test/kotlin/com/six2dez/burp/aiagent/DetektBaselineBoundTest.kt` — bounds the detekt
      baseline at its sealed 1040 entries (SC3 / ADR-17 clause 1), plus a second test rejecting an
      empty or lost baseline so the ceiling cannot pass vacuously.
- [x] `build.gradle.kts` — `jacocoTestCoverageVerification` (13 of the 14 sealed floors) and
      `jacocoMcpTreeCoverageVerification` (the 14th), both wired into `tasks.check`, plus
      `inputs.file("detekt-baseline.xml")` so the new guard cannot be cache-served.

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| The disposition of `ChatPanel`'s `assert()`-based EDT enforcement (upgrade vs document) | QUAL-07 (SC4) | A `checkpoint:decision` — a judgement about which mechanism to ship, not a behavior. The *outcome* is gated: `ChatPanelEdtGuardTest` pins the wording the decision produced. | Read ADR-17 clause 2 and confirm it records the disposition and the off-EDT `shutdown()` residual the probe could not retire. |
| The GitBook site (`burp-ai-agent-docs`) states `SecretCipher`'s at-rest caveat and documents the tool-call confirmation flow | DOC-03 (SC6) | Out-of-repo. T-26-05-04 explicitly prohibits automated cross-repository writes, and no test in this repo can reach that checkout. Phase 26 shipped a prepared diff (`26-GITBOOK-HANDOFF.md`) and a human applied it. | In `~/Tools/burp-ai-agent-doc`: `grep -rn "master key" backends/anthropic.md mcp/external-servers.md privacy/limitations.md` must show the beside-the-ciphertext caveat, and `grep -rln "SEC-06\|Approve for session"` must be non-empty. **Verified 2026-08-24** — closed by that repo's commit `d9712b3`. |
| Baseline *direction* across an arbitrary commit range (a removal+addition pair that leaves the count level) | QUAL-07 (SC3) | `DetektBaselineBoundTest` bounds the count, which catches the append-instead-of-fix failure mode. It cannot judge whether a removal was backed by a real source fix. | `git diff -U0 <ref>..HEAD -- detekt-baseline.xml \| grep -c '^+.*<ID>'` must return 0. Documented in the test's KDoc as the complementary check. |

---

## Validation Sign-Off

- [x] All tasks have `<automated>` verify or a documented manual-only rationale
- [x] Sampling continuity: no 3 consecutive tasks without automated verify
- [x] Wave 0 covers all MISSING references (none were missing; 2 gates added retroactively)
- [x] No watch-mode flags
- [x] Feedback latency < 30 s per suite
- [x] `nyquist_compliant: true` set in frontmatter

**Approval:** approved 2026-08-24

---

## Validation Audit 2026-08-24

| Metric | Count |
|--------|-------|
| Tasks audited | 21 |
| Automated | 19 |
| Manual-only | 2 |
| Gaps found | 2 |
| Resolved | 2 |
| Escalated | 0 |

### The two gaps

**GAP-1 — SC2 / QUAL-06: the 14 coverage floors were recorded, not enforced.** `26-COVERAGE.md`
sealed 14 floors with a MET verdict each, but `build.gradle.kts` had no
`jacocoTestCoverageVerification` rule and no test parsed the report. A regression below any floor was
invisible to `./gradlew check` — the requirement's verification was a human reading a markdown table.

Closed by a `jacocoTestCoverageVerification` configuration carrying 13 of the floors (BUNDLE ×2,
PACKAGE ×7, SOURCEFILE ×4) plus a `jacocoMcpTreeCoverageVerification` task for the 14th. Floor 3 —
`mcp` tree line ≥ 65.0% — aggregates four sibling packages, and JaCoCo offers no element between
BUNDLE and PACKAGE. It is **not** decomposable into four per-package floors: `mcp/tools` (56.54%) and
`mcp/external` (60.89%) each sit below 65% while the aggregate is above it. The custom task sums the
four packages' LINE counters out of the same report the other 13 verify against. Nothing was dropped
and no floor was lowered; the floors used are 26-COVERAGE.md's **Floor** column, never the Measured
column. Both tasks are wired into `tasks.check`.

**GAP-2 — SC3 / QUAL-07: the baseline shrink-only rule was written but not gated.** ADR-17 clause 1
states the baseline may only shrink; `DecisionsAdrTest` asserted only that ADR-17 *names* the string
`detekt-baseline.xml`. T-26-07-03's `git diff … | grep -c '^+.*<ID>'` was a one-off command inside the
plan. Closed by `DetektBaselineBoundTest`, which reads the baseline from disk, bounds it at 1040, and
fails with a message quoting ADR-17 clause 1 and telling the contributor to fix the finding rather
than baseline it — plus `inputs.file("detekt-baseline.xml")` so a baseline-only edit cannot be
cache-served.

### Red probes

Both gates were proven falsifiable rather than assumed:

- Every floor raised to 0.999 (and `Serialization.kt`, already at 100%, probed with a `COVEREDCOUNT`
  minimum instead) — 13 JaCoCo rules and the custom task all reported violations. All restored.
- Baseline ceiling lowered to 1039 → `:test FAILED`. Restored, re-ran green.
- **The cache probe, the one that matters:** one `<ID>` appended to the baseline with zero source
  change. `:compileKotlin`, `:compileTestKotlin` and `:testClasses` all reported UP-TO-DATE and
  `:test` re-executed and failed anyway — the `inputs.file` declaration works. Baseline restored to
  1040, `git diff --stat detekt-baseline.xml` empty.

### Defect found by the red probe

Written with slashed package names, all seven PACKAGE rules matched **zero elements and passed
vacuously with no output** — the build was green with every floor at 99.9%. JaCoCo names PACKAGE
elements with dots and SOURCEFILE elements with the slashed path. Seven dead rules would have shipped
had the gate not been red-probed. The mismatch is now documented in a comment at that spot in
`build.gradle.kts`.

### Constraints held

`detekt.yml` byte-identical. `detekt-baseline.xml` byte-identical at 1040 entries — nothing was
baselined to make the new work pass; the one ktlint violation the new build code tripped
(`Expected newline before '.'`) was fixed by hoisting an import, not suppressed. No file under
`src/main/` was modified. Suite grew 158/1131 → **159/1133**, skip count unchanged at 1, 0 failures.
