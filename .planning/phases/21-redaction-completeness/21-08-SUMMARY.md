---
phase: 21-redaction-completeness
plan: 08
subsystem: redact
gap_closure: true
tags: [PRIV-05, PRIV-06, CR-01, CR-03, SC1, D-02, D-09, D-10, T-21-28, T-21-29, T-21-32, kotlin, redaction, privacy, cookies, performance]
requires:
  - "Redaction.COOKIE_SECTION_HEADER, cookieSectionPairRegex, windowDroppedMarker, maybeLogTruncation, NANOS_PER_MS (all pre-existing)"
provides:
  - "Redaction.cookieSectionEnd — the collapse-proof span bound (CR-01)"
  - "Redaction.redactCookieSections — single O(n) pass under a wall-clock deadline that fails closed (CR-03)"
  - "Redaction.sanitizeCookieSectionEntries — exported for plan 21-10, UNCALLED in this plan by design"
  - "Redaction.testRedactCookieSections — internal injected-budget seam"
  - "MAX_COOKIE_SECTION_LINES = 16, COOKIE_SECTION_BUDGET_MS = 250L"
affects:
  - "plan 21-10 wires sanitizeCookieSectionEntries into PassiveAiScannerPrompts.buildScanMetadataText"
tech-stack:
  added: []
  patterns:
    - "injected-bound test seam (testRedactCookieSections), matching maybeLogTruncation(nowMs, …)"
    - "monotone forward line walk replacing two independent terminator searches"
key-files:
  created: []
  modified:
    - "src/main/kotlin/com/six2dez/burp/aiagent/redact/Redaction.kt"
    - "src/test/kotlin/com/six2dez/burp/aiagent/redact/RedactionTest.kt"
decisions:
  - "Fixed CR-01 in the redactor, not the emitter; the emitter filter is defence in depth and ships in 21-10"
  - "Went further than the reviewer's proposed snippet: blank lines are SKIPPED, not merely skipped-once at the head, which closes the mid-list blank the snippet leaves leaking"
  - "Dropped BOTH terminator searches for one forward line walk — two searches were the second half of CR-03"
  - "Deadline fails CLOSED via windowDroppedMarker rather than capping section count and passing the remainder"
  - "detekt LargeClass on RedactionTest suppressed on the declaration rather than baselined (QUAL-07)"
metrics:
  duration: "~40 min"
  completed: "2026-08-12"
  tasks: 2
  commits: 2
  tests_total: 632
  tests_failing: 0
---

# Phase 21 Plan 08: CR-01 Cookie Span Collapse + CR-03 Quadratic Rebuild Summary

Closed the live PRIV-05 cookie leak (a blank cookie entry collapsed the redaction span to zero length, sending every cookie in the section to the AI backend) and the attacker-controlled quadratic stall in the same function, by replacing `redactCookieSections`'s per-occurrence string rebuild and dual terminator search with a single O(n) pass over a collapse-proof, line-bounded, deadline-bounded span.

## What Shipped

**CR-01 — the live leak.** `COOKIE_SECTION_HEADER` carries no trailing newline while the emitter uses `appendLine`, so `bodyStart` landed on the header line's own `\n` and `indexOf("\n\n", bodyStart)` matched **at `bodyStart`** whenever the first cookie entry was blank: the span became the empty string and nothing was redacted. `cookieSectionEnd` now starts its walk at the line *after* the header, and treats blank lines as **skippable, never terminators**. The scanner really does emit blank entries — `.split(";").map { it.trim() }` with no blank filter, so `Cookie: ; JSESSIONID=…` or any `a=b;;c=d` produces one.

**CR-03 — the stall.** The loop rebuilt the whole string per occurrence (O(k·n), k attacker-controlled). Now: one `StringBuilder`, one strictly monotone cursor, disjoint appended spans, and a forward-only line walk. Both terminator searches were removed deliberately — each could scan to end-of-text independently, which kept the loop quadratic *even after* the rebuild was fixed. A 250 ms deadline bounds the residual and fails closed through `windowDroppedMarker` + `maybeLogTruncation`, the D-02 discipline the body stage already follows.

**T-21-32 — the residual that is not closable here.** A cookie element shaped like `=== FOO ===` still terminates the span. This is in-band signalling: the span terminator is necessarily derived from content inside the region it protects, so no redactor-side rule can distinguish a planted header from a genuine one. `sanitizeCookieSectionEntries` is exported for the emitter; **it ships uncalled** pending plan 21-10, which is deliberate and keeps the two `Redaction.kt` edits in separate waves.

## RED Failure Set (Task 1, before Task 2)

Command: `./gradlew test --tests "*RedactionTest.cookieSectionBlankEntriesDoNotCollapseSpan*" --tests "*RedactionTest.redactCookieSectionsIsLinearInSectionCount*"` → **2 tests completed, 2 failed.**

| Test | Observed failure |
|------|------------------|
| `cookieSectionBlankEntriesDoNotCollapseSpan` | `STRICT: a blank cookie entry must not let the value of 'abtest_bucket' reach the backend ==> expected: <false> but was: <true>` |
| `redactCookieSectionsIsLinearInSectionCount` | `redactCookieSections must be linear in input length; took 3372ms (pre-fix this same fixture measured 2 631 ms and rose 4x per doubling) ==> expected: <true> but was: <false>` |

The blank-entry test failed on **`abtest_bucket` and only `abtest_bucket`** — exactly as the fixture-reachability argument predicts. `JSESSIONID` and `PHPSESSID` are rescued by `SENSITIVE_KEY_EXPR` from the leading-field position even with the section rule fully broken, which is precisely why the pre-fix suite was green on a live leak.

Two honest notes on the RED gate:

- `cookieSectionDeadlineFailsClosed` **could not compile** before Task 2 (it calls the `testRedactCookieSections` seam Task 2 adds). The plan anticipated this. A compile error is *not* evidence a defect exists, so the RED evidence above was taken with that one test temporarily block-commented, then restored before the Task 1 commit. The two failures recorded are genuine assertion failures.
- `cookieSectionHeaderShapedEntryTerminatesSpan_documentedResidual` was verified **GREEN pre-fix** in its own run — as designed. It asserts a residual, so it is green before and after; it is not a guard for this plan's change.

## Mutation Verification

### Mutation A — `out = redactCookieSections(out)` commented out at the `apply` call site

**28 tests completed, 3 failed.**

| Test | Observed failure |
|------|------------------|
| `cookieSectionBlankEntriesDoNotCollapseSpan` | `STRICT: a blank cookie entry must not let the value of 'abtest_bucket' reach the backend ==> expected: <false> but was: <true>` |
| `cookieSectionValuesRedactedPerName` | `STRICT: the value of cookie 'abtest_bucket' must be absent from the redacted prompt ==> expected: <false> but was: <true>` |
| `cookieSectionDecoyDoesNotShieldRealSection` | `STRICT: a decoy section header must not shield an unremarkably-named real cookie ==> expected: <false> but was: <true>` |

Three tests **did not** fail under Mutation A, each for a defensible reason:

- `cookieSectionDeadlineFailsClosed` — calls `Redaction.testRedactCookieSections` **directly**, bypassing `apply`. That is the entire point of the seam, so unwiring the call site cannot reach it. See the plan defect below.
- `redactCookieSectionsIsLinearInSectionCount` — removing the rule makes it *faster*, not slower. Its guard mutation is the pre-fix quadratic implementation, which is the Task 1 RED observation above (3372 ms vs a 1 000 ms bound).
- `cookieSectionHeaderShapedEntryTerminatesSpan_documentedResidual` — asserts a residual (`OPAQUE_VALUE_XYZ` **survives**), and `JSESSIONID` is redacted by `SENSITIVE_KEY_EXPR` regardless. Correct behaviour for a residual-pinning test.

### Mutation B — the deadline branch deleted from `redactCookieSections`

**28 tests completed, 1 failed.**

| Test | Observed failure |
|------|------------------|
| `cookieSectionDeadlineFailsClosed` | `An expired cookie-section budget must leave the windowDroppedMarker, not silence ==> expected: <true> but was: <false>` |

Precisely one test fails, and it is the one that exists to guard that branch.

Both mutations were reverted. Mutation B was run **after** the Task 2 commit specifically so the restore was `git checkout --`, a guaranteed-clean revert rather than a hand edit. `git status` is clean and `grep -c MUTATION` over both files returns 0.

## CR-03 Measured Before/After

| | Elapsed |
|---|---|
| Before (this machine, in-assertion measurement) | **3372 ms** |
| Before (reviewer, Apple Silicon / JDK 21) | 2631 ms |
| After (whole test wall time incl. building the 524 288-char fixture) | **76 ms** |

Same fixture both times: `("=== COOKIES ===" + "\n").repeat(32_768)` = 524 288 chars, the reviewer's 512 KB / 32 768-occurrence row. Roughly a 44× improvement, against a 1 000 ms assertion bound that is unreachable pre-fix on any machine the project targets.

The test also asserts the output contains **no** `REDACTION INCOMPLETE` marker. That assertion is load-bearing: without it, a future change could make the test pass by tripping the budget instantly and dropping the tail — fast, but fail-closed truncation rather than the linear pass under test.

## Fixture Reachability — why each new test is reachable ONLY by the path under test

`abtest_bucket=OPAQUE_VALUE_XYZ` is the sentinel in every new fixture, for the reason plans 21-05 and the reviewer both used it. The name's tokens are `abtest` and `bucket`, neither of which is a member of `SENSITIVE_WORDS` or `KNOWN_SESSION_KEYS`, so `SENSITIVE_KEY_EXPR` and its three consumers (`urlTokenParamRegex`, `formBodyParamRegex`, `jsonSecretKeyRegex`) cannot reach it. Its value contains no `=`, is not preceded by `?` or `&`, does not start with `eyJ`, is neither `Bearer`- nor `Basic`-prefixed, sits in no JSON key/value pair, and carries no ` (COOKIE)` suffix — so no other rule in `Redaction.apply` can touch it either.

| Test | What makes it non-vacuous |
|------|---------------------------|
| `cookieSectionBlankEntriesDoNotCollapseSpan` | Only the section rule can redact `abtest_bucket`, **and** that line sits *after* the mid-list blank — the half the reviewer's own proposed patch leaves leaking. `JSESSIONID`/`PHPSESSID` are deliberately non-decisive. Proven by Mutation A (failed on `abtest_bucket`) and by the Task 1 RED run. The fixture itself is asserted before the behaviour (`"=== COOKIES ===\n\n"` present, and `PHPSESSID=…\n\nabtest_bucket=` present) so a reformat cannot silently defuse it. |
| `cookieSectionHeaderShapedEntryTerminatesSpan_documentedResidual` | Asserts `OPAQUE_VALUE_XYZ` **survives**. Because nothing but the section rule could ever redact it, its survival is genuine evidence of the residual rather than an artefact of another rule declining to fire. Green before and after by design. |
| `redactCookieSectionsIsLinearInSectionCount` | Fixture length pinned with `assertEquals(524_288, …)` so it cannot drift off the measured row; the no-marker assertion blocks the fail-closed shortcut. Guard mutation = the pre-fix quadratic code, observed RED at 3372 ms. |
| `cookieSectionDeadlineFailsClosed` | The seam bypasses `Redaction.apply` entirely, so **no other rule even runs** — the absence of `OPAQUE_VALUE_XYZ` can only be the deadline branch dropping the tail. Proven by Mutation B. |

## D-10 Not Regressed

The rewritten loop still iterates **every** occurrence of `COOKIE_SECTION_HEADER` (`indexOf(COOKIE_SECTION_HEADER, cursor)` in a `while`, cursor advancing monotonically). `cookieSectionDecoyDoesNotShieldRealSection` is green, and Mutation A shows it still fails on its `abtest_bucket` assertion when the rule is unwired — so it remains a live guard, not a passenger.

Monotonicity canaries all green in the full run: `balancedModeRedactsUrlTokensInQueryStrings`, `bodyFormLeadingFieldRedacted`, `bodyJsonSecretKeysRedacted`, `offModePreservesBodies`, `hkdfMatchesRfc5869Vector`, plus the two deliberate SC6 inversions, which were left untouched.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] detekt `LargeClass` on `RedactionTest`**
- **Found during:** Task 2 verification (`./gradlew detekt` → "Analysis failed with 1 weighted issues")
- **Issue:** Task 1's 207 added lines pushed `RedactionTest` over detekt's `LargeClass` threshold. `RedactionTest` is not in `detekt-baseline.xml` (its `LargeClass` entries are all main-source classes), and QUAL-07 forbids growing the baseline.
- **Fix:** `@Suppress("LargeClass")` on the class declaration with a comment giving the reason — the sanctioned route, matching the declaration-level precedent at `scanner/PassiveAiScannerAnalysis.kt:169`. Splitting the file was rejected: it would scatter the monotonicity canaries away from the CR guards that must not regress them.
- **Files modified:** `src/test/kotlin/com/six2dez/burp/aiagent/redact/RedactionTest.kt`
- **Commit:** `1b3a3c5`

### Plan Defects Recorded (intent satisfied, criterion not literally satisfiable)

**1. Task 2's mutation acceptance criterion is wrong about `cookieSectionDeadlineFailsClosed`.**
The plan requires that with `redactCookieSections(out)` commented out at the `apply` call site, *both* `cookieSectionBlankEntriesDoNotCollapseSpan` **and** `cookieSectionDeadlineFailsClosed` fail. The latter is impossible by construction: the plan itself specifies that test to call `Redaction.testRedactCookieSections(blob, budgetMs = 0L)`, which delegates straight to `redactCookieSections` and never enters `apply`. Unwiring the call site cannot affect it. **Intent satisfied** by adding Mutation B (deleting the deadline branch), under which that test — and only that test — fails. No assertion was weakened and no comment removed to make a criterion match.

**2. `@Suppress("ReturnCount")` on `cookieSectionEnd` was needed, as the plan predicted;** it has three returns. Kept. `redactCookieSections` itself has two returns and needed none.

**3. Test-count arithmetic.** The plan states a 628-test baseline, so 633 after five additions. Measured after: **632 tests, 0 failures** (632 `<testcase>` elements across 101 result files, cross-checked against the summed `tests=` attributes). The five new tests are all present and passing; the discrepancy is in the quoted baseline, not in this plan's additions. Reported as measured rather than asserting the plan's figure.

### Comment style
`redact/` convention (and 21-05's recorded precedent) is `/** */` on public entry points and `//` on fields and algorithm notes. The plan says "KDoc" throughout; the required *content* is present in full either way. Applied as: `/** */` on the public `sanitizeCookieSectionEntries`, `//` on the private constants, `cookieSectionEnd`, `redactCookieSections` and the internal seam — matching the surrounding file.

## Known Stubs

| Item | File | Reason |
|------|------|--------|
| `sanitizeCookieSectionEntries` ships **uncalled** | `redact/Redaction.kt` | Deliberate and specified by the plan. It is dead code until plan 21-10 wires it into `PassiveAiScannerPrompts.buildScanMetadataText`, which keeps the two `Redaction.kt` edits in separate waves. Until then, T-21-32 (a section-shaped cookie value truncating the span) remains open and is pinned by `cookieSectionHeaderShapedEntryTerminatesSpan_documentedResidual`. |

## Threat Model Outcomes

| Threat | Disposition | Outcome |
|--------|-------------|---------|
| T-21-28 (span collapse — the live PRIV-05 leak) | mitigate | **Closed.** `cookieSectionEnd` starts past the header line and skips blanks; guard `cookieSectionBlankEntriesDoNotCollapseSpan`, verified RED pre-fix and under Mutation A |
| T-21-29 (quadratic rebuild, attacker-controlled k) | mitigate | **Closed.** O(n) single pass, 3372 ms → 76 ms on the 512 KB row; `COOKIE_SECTION_BUDGET_MS` bounds the residual and fails closed |
| T-21-01 (section-header poisoning) | mitigate | **Not regressed.** Every occurrence still iterated; decoy guard green and still failing under Mutation A |
| T-21-32 (cookie value shaped like a section header) | transfer | **Transferred as planned.** `sanitizeCookieSectionEntries` exported, uncalled until 21-10; residual asserted rather than left implicit |
| T-21-21 (planted header over-redacts) | accept | **Narrowed.** `MAX_COOKIE_SECTION_LINES = 16` replaces "to the next blank line" |
| T-21-05 (drop marker in model context) | mitigate | `windowDroppedMarker` reused verbatim; no third marker shape |
| T-21-23 (concurrency) | mitigate | Builder, cursor and deadline are all locals; no object-level field added |
| T-21-SC (supply chain) | accept | Zero packages installed; no new Gradle dependency |

## Verification

- `./gradlew test ktlintCheck detekt` — **BUILD SUCCESSFUL**, 632 tests / 0 failures, ktlint clean, detekt clean
- `git diff --stat -- detekt-baseline.xml` — **empty** (QUAL-07 satisfied)
- `git diff -U0 -- Redaction.kt | grep -c 'hkdfExtract\|hkdfExpand'` — **0** (HKDF block untouched; `hkdfMatchesRfc5869Vector` green)
- `grep -v '^\s*//' Redaction.kt | grep -c 'nextSectionRegex'` — **0** (removed)
- `grep -c 'out.substring(0,' Redaction.kt` — **0**, checked across the whole file rather than only the function body (stricter than the criterion as written)
- `grep -c` for `private fun cookieSectionEnd` / `fun sanitizeCookieSectionEntries` / `internal fun testRedactCookieSections` — **1** each; constants grep — **5** (≥ 4)
- Non-comment `abtest_bucket` references — **12** (≥ 6)

## Tasks

1. **Task 1: Four red-before-green tests for the span collapse and the quadratic rebuild** — `9238ddc` (test)
2. **Task 2: Single-pass, line-bounded, deadline-bounded redactCookieSections** — `1b3a3c5` (fix)

## Self-Check: PASSED

- `src/main/kotlin/com/six2dez/burp/aiagent/redact/Redaction.kt` — FOUND, modified
- `src/test/kotlin/com/six2dez/burp/aiagent/redact/RedactionTest.kt` — FOUND, modified
- `.planning/phases/21-redaction-completeness/21-08-SUMMARY.md` — FOUND
- Commit `9238ddc` — FOUND
- Commit `1b3a3c5` — FOUND
- STATE.md / ROADMAP.md / REQUIREMENTS.md — **not modified** (orchestrator owns those writes)
</content>
</invoke>
