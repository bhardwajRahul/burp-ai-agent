---
phase: 21-redaction-completeness
plan: 11
subsystem: privacy
tags: [redaction, windowing, minified-json, kotlin, tdd, mutation-testing, test-vacuity]

# Dependency graph
requires:
  - phase: 21-redaction-completeness (plan 06)
    provides: the windowed body stage, splitPoint, dropOrRetry and WINDOW_RETRY_MAX_DEPTH this plan changes
  - phase: 21-redaction-completeness (plan 09)
    provides: the looping windowEnd boundary extension, deliberately left splitPoint and the retry depth untouched for this plan
provides:
  - splitPoint's newline-free safe-cut fallback, closing CR-04 — a minified-JSON body above the window width is scanned instead of destroyed
  - SAFE_CUT_SEARCH_CHARS / SAFE_CUT_TERMINATORS / safeCutPoint / isSafeCutTerminator, with the terminator set derived from the built-in rules' own value classes
  - WINDOW_RETRY_MAX_DEPTH raised 2 -> 4, so the ladder reaches a scannable piece size for a 2 MiB newline-free window
  - testSplitPoint — a deterministic, hardware-independent assertion of the defect, alongside the timing-exposed end-to-end one
  - WR-05 closed - both fail-closed tests assert the property they exist to guard, proven by a mutation the old form passed
  - the three (?m)^-trap justifications written into source, and ADR-14's line-boundary claim qualified
affects: [redaction, privacy, MCP tool output, any future change to window boundaries or the retry ladder]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Anti-vacuity guard on a FIXTURE, not just on an assertion: assert the fixture is misaligned with the mechanism's trivial fallback, so the test cannot pass against a stub"
    - "Mutation-check the test's own fixture arithmetic, not only the implementation — two of three seam tests were vacuous by coincidence of length"
    - "Prove a strengthened assertion is non-vacuous by re-running the OLD assertion against the same mutation and showing it passes"

key-files:
  created: []
  modified:
    - src/main/kotlin/com/six2dez/burp/aiagent/redact/Redaction.kt
    - src/test/kotlin/com/six2dez/burp/aiagent/redact/RedactionTest.kt
    - DECISIONS.md
    - .planning/codebase/CONCERNS.md

key-decisions:
  - "A hard cut with a bounded safe-boundary search, never overlap — D-01 AMENDED's dropped clause stays dropped on its measured ground, and the halves remain disjoint"
  - "The terminator set is derived from each rule's value class rather than asserted, so the safety argument is checkable rather than stipulated"
  - "The character cut's reachability (only from dropOrRetry, only where the alternative is total loss) is what licenses the artificial (?m)^ anchor — recorded in source so it cannot be generalised"
  - "CONCERNS.md's existing body-stage entry was EXTENDED to a third residual rather than duplicated"

patterns-established:
  - "When a mutation check passes unexpectedly, suspect the fixture's arithmetic before concluding the implementation is well-guarded"
  - "Commit every intermediate test correction before the next `git checkout --` revert; an uncommitted hardening is indistinguishable from a mutation"

# Metrics
duration: 75min
completed: 2026-08-12
---

# Phase 21 Plan 11: CR-04 / WR-05 — a newline-free body is scanned in pieces instead of destroyed

**`splitPoint` can now cut a window that has no newline in it, so a default-configuration 2 MiB minified-JSON tool response reaches the model scanned rather than as a single drop marker — and the two fail-closed tests that were supposed to guard this machinery stopped being satisfiable by a one-character change.**

## Performance

- **Duration:** ~75 min
- **Started:** 2026-08-12T10:34:52Z
- **Completed:** 2026-08-12T11:50:00Z
- **Tasks:** 3 of 3
- **Files modified:** 4

## Accomplishments

- **CR-04 closed.** A newline-free body four times the window width is scanned end to end and its planted `"api_key"` pair is redacted in place. Measured: output went from **59 characters** (one drop marker) to **3 999 995 characters** against a 4 000 005-character input.
- **WR-05 closed, and proven closed.** The old assertion was not merely weak — a mutation emitting *every* window unscanned beside its marker keeps both tests **green** under the old form and fails both under the new one. That is recorded below as M4/M4b.
- **The `(?m)^` protection is provably intact** wherever a line boundary exists, and the guard proving it is itself mutation-verified after a first version that was not.
- **Two of my own seam tests were vacuous and the mutation checks caught them.** Both are documented in source with the coincidence that made them pass, because this phase's recurring failure mode is exactly that.

## Required measurements

The plan's `<output>` block requires five specific numbers. All five, measured rather than recalled.

### 1. The fixture multiplier and elapsed time that made the end-to-end test red

| | Value |
|---|---|
| Multiplier settled on | **4x** `Defaults.MAX_REDACTION_BODY_CHARS` (the plan's proposed starting point; no scaling up was needed) |
| Fixture length | **4 000 005** characters, newline-free minified JSON |
| Pre-fix elapsed | **0.198 s** |
| Post-fix elapsed | **1.911 s** |
| Pre-fix output length | **59** characters |
| Post-fix output length | **3 999 995** characters |

The test was **RED at 4x on the first run**, so the plan's fallback instruction ("if it is green pre-fix, scale up until it is red") did not apply.

**First failing assertion, verbatim:**

```
STRICT: the pair must be redacted IN PLACE, keeping its key — not removed wholesale
  ==> expected: <true> but was: <false>
```

**A correction to the plan's prediction, recorded rather than glossed.** The plan states that the `output.length > body.length / 2` assertion "is the one that is RED today". Both the in-place assertion *and* the length assertion are red pre-fix, and JUnit aborts on the first, so the length one never ran. It was confirmed red separately (M1b): with the in-place assertion temporarily removed, the length assertion reports

```
A newline-free body must be SCANNED, not collapsed behind a drop marker;
output was 59 chars against a 4000005-char input ==> expected: <true> but was: <false>
```

59 is exactly `[REDACTION INCOMPLETE - 4000005 CHARS DROPPED AND NOT SENT]` — the entire 4 MB payload replaced by one marker, which is CR-04 stated as a number.

**The post-fix length is worth reading closely: 4 000 005 - 3 999 995 = 10.** `"SC4-NEWLINE-SECRET-9"` (22 characters with its quotes) became `"[REDACTED]"` (12). Nothing else changed, and **zero** markers of either kind appear in the output. The body was not merely mostly preserved; it was scanned in full and edited in exactly one place.

### 2. Mutation-check failure sets

Every mutation was applied **on top of the committed implementation** and reverted with `git checkout -- <path>`, per the discipline 21-08's interrupted executor forced and 21-09 recorded. No mutation was ever hand-edited back out.

| # | Mutation | Result | What it proves |
|---|---|---|---|
| **M1** | `splitPoint`'s `else 0` restored (the defect) | **3 FAILED**: `splitPointCutsNewlineFreeWindowsInsteadOfRefusing`, `splitPointPrefersASafeCutBoundaryInMinifiedJson`, `newlineFreeOversizeBodyIsScannedNotDestroyed` | The plan required the first and third; the second fails too, since its window is also newline-free. The deterministic seam test and the end-to-end test both detect the defect independently |
| **M1b** | M1 plus the in-place assertion temporarily removed | `newlineFreeOversizeBodyIsScannedNotDestroyed` FAILED reporting `output was 59 chars against a 4000005-char input` | The length assertion is independently red pre-fix, not merely shadowed by the assertion before it |
| **M2** | `safeCutPoint` returns `mid` unconditionally (forward search deleted) | **PASSED — vacuity found.** After the fixture fix: **1 FAILED**, `splitPointPrefersASafeCutBoundaryInMinifiedJson`, `it landed after ':'` | The forward search is load-bearing, and *only* that test guards it. See Deviation 1 |
| **M3** | `splitPoint`'s two line-boundary branches deleted; always take the character cut | **PASSED — vacuity found.** After the fixture fix: **1 FAILED**, `splitPointStillCutsAtALineBoundaryWhenOneExists`, `expected: <\n> but was: < >` | The line branches are load-bearing, and the regression guard now actually guards. See Deviation 2 |
| **M4** | `dropOrRetry` appends the window verbatim *beside* its marker (fail-open with a marker) | **2 FAILED**: `oversizeBodyFailsClosed`, `subWindowBodyFailsClosed`, both on `No unscanned window may reach the output; only markers may remain` | The strengthened WR-05 assertions detect unscanned bytes reaching the output |
| **M4b** | M4, with the **old** assertions restored | **BUILD SUCCESSFUL — both PASS** | **This is WR-05, demonstrated rather than argued.** With every single window emitted unscanned, the old gate stayed green. It was decorative |
| **M5** | `WINDOW_RETRY_MAX_DEPTH` reverted to 2 | (diagnostic, not a gate) marker counts 8 and 4 rather than 19 and 16 | Quantifies the marker-bloat cost of the depth change |

M1 was re-run against the final committed base after all other work, reproducing the same three failures. The tree was confirmed byte-identical to the commit after every revert (`git status --short` and `git diff HEAD --stat` both empty).

### 3. Observed output lengths in both strengthened fail-closed tests

Measured at the **new** depth of 4, which is what the plan asked to be checked, because a deeper ladder emits more markers per window:

| Test | body.length | bound (`/2`) | observed output | `REDACTION INCOMPLETE` markers | headroom |
|---|---|---|---|---|---|
| `oversizeBodyFailsClosed` | 1 201 200 | 600 600 | **1 084** | 19 | **554x** under the bound |
| `subWindowBodyFailsClosed` | 800 800 | 400 400 | **912** | 16 | **439x** under the bound |

At the old depth of 2 the same fixtures produce 460 characters / 8 markers and 232 characters / 4 markers. So the depth change roughly **doubles to quadruples** the marker count, exactly as `2^depth` predicts — `subWindowBodyFailsClosed` is a single window and lands on 16 = 2^4 precisely — while the output stays **three orders of magnitude** under the half-length bound. **Pitfall 8 marker bloat is not real at the new depth**, and the assertion needed no loosening. No budget-exceeded markers were emitted in either case, so the ladder completes inside `MAX_REDACTION_BUDGET_MS` rather than being cut short by it.

### 4. Whether `CONCERNS.md` was extended or left to plan 21-09's wording

**Extended**, not duplicated. Plan 21-09's entry *Redaction body-stage bounds and the unbounded header stage* already frames window-boundary false negatives generally, so per the plan's instruction the existing entry was widened from "Two deliberate residuals" to "Three", with a **Third** clause covering the newline-free cut. Its **Files**, **Fix approach** and **Impact** lines were extended in the same pass, all anchored on symbol names (`splitPoint`, `safeCutPoint`, `isSafeCutTerminator`, `SAFE_CUT_SEARCH_CHARS`, `SAFE_CUT_TERMINATORS`, `dropOrRetry`, `WINDOW_RETRY_MAX_DEPTH`) and never on line numbers.

### 5. Before/after residual-bullet counts in `DECISIONS.md`

Compared against the prior **committed** file rather than a remembered number — the counting discipline plan 21-07 established:

| | `grep -c '^- Residual' DECISIONS.md` |
|---|---|
| Before (`git show HEAD:DECISIONS.md`, at `51a441b`) | **3** |
| After | **4** |

Grew by exactly one, matching the plan's expectation that 21-09's three become four.

## Task Commits

1. **Task 1: seam tests, end-to-end gate, WR-05 strengthening** — `efb761e` (test — TDD RED)
2. **Task 2: `splitPoint` safe-cut fallback, deeper ladder, corrected comments** — `354d0de` (fix — TDD GREEN)
3. **Task 1 correction: de-vacuify two seam fixtures** — `cb360b1` (test — forced by M2/M3)
4. **Task 3: ADR-14 and CONCERNS.md** — `89f1297` (docs)

## Files Created/Modified

- `src/main/kotlin/com/six2dez/burp/aiagent/redact/Redaction.kt` — `safeCutPoint`, `isSafeCutTerminator`, `SAFE_CUT_SEARCH_CHARS`, `SAFE_CUT_TERMINATORS`, `testSplitPoint`; `WINDOW_RETRY_MAX_DEPTH` 2 -> 4; corrected `splitPoint` and `dropOrRetry` comments
- `src/test/kotlin/com/six2dez/burp/aiagent/redact/RedactionTest.kt` — three seam tests, the newline-free end-to-end test, the two strengthened fail-closed tests, `isSafeCutTerminatorForTest` and five fixture constants
- `DECISIONS.md` — ADR-14 Decision clause qualified, one Consequences bullet, one Residual bullet
- `.planning/codebase/CONCERNS.md` — existing body-stage entry extended to a third residual

## Reachability argument for every new and strengthened test

The phase's recurring failure mode is a test that passes against a mutation which unwires the rule it exists to guard. Each test below states what makes it reachable **only** by the path under test, and each claim was checked by mutation rather than by inspection.

**`splitPointCutsNewlineFreeWindowsInsteadOfRefusing`** — calls `splitPoint` directly through the seam, so no other rule, stage or deadline participates at all. The only way to satisfy `0 < cut < 1000` on `"x".repeat(1_000)` is for the function to return a non-zero index. Verified by M1.

**`splitPointStillCutsAtALineBoundaryWhenOneExists`** — reachable only by the backward line search, and *this required a fixture fix to become true*. The assertion is that `window[cut - 1] == '\n'`; because `safeCutPoint` treats whitespace (including `\n`) as a terminator, a fixture whose only terminator is the newline is satisfied by either path. The fixture now carries spaces and a comma, so a forward character search lands on one of **those** first and only the line branch can produce a newline; and its midpoint (533) falls mid-line, so the backward search is genuinely exercised. Verified by M3, which now fails with `expected: <\n> but was: < >`.

**`splitPointPrefersASafeCutBoundaryInMinifiedJson`** — reachable only by the forward terminator search, and *this also required a fixture fix*. The assertion is that `window[cut - 1]` is a terminator; a fixture whose midpoint already sits just after one is satisfied by a stub that returns the midpoint. The fragment is now 12 characters × 167, putting the midpoint after a `:`, which is in no terminator set. Verified by M2, which now fails with `it landed after ':'`.

**`newlineFreeOversizeBodyIsScannedNotDestroyed`** — `SC4-NEWLINE-SECRET-9` is reachable by `jsonSecretKeyRegex` and by nothing else in either stage: the fixture contains no `=` anywhere, so `formBodyParamRegex` cannot reach it and `urlTokenParamRegex` — which runs **unbounded in the header stage** and would otherwise mask the defect entirely — additionally requires a leading `?` or `&`; the value is not `Bearer`- or `Basic`-prefixed and does not begin `eyJ`; there is no `Cookie:`/`Set-Cookie:` header, no `=== COOKIES ===` section and no ` (COOKIE)` type suffix; and no custom pattern is registered, with `@AfterEach resetCustomPatterns` preventing bleed from the two tests that install `(a+)+$`. Neither `id` nor `name` is in `SENSITIVE_WORDS` or `KNOWN_SESSION_KEYS`, so the filler cannot manufacture a `[REDACTED]` and create a false positive. Verified by M1.

**`oversizeBodyFailsClosed` / `subWindowBodyFailsClosed` (strengthened)** — both fixtures are nothing but 2 000-character `a` runs and `!`. Not one built-in rule can match them in either stage, every window in them must be dropped, and each sub-window at every ladder depth still contains at least one full 2 000-`a` line, which trips the deadline on its own. So a 2 000-`a` run can appear in the output **only** if some window was emitted unscanned — the run-absence check is exact rather than approximate. Verified by M4, and its converse by M4b.

## Decisions Made

**1. A hard cut with a bounded safe-boundary search, and the three justifications live in the source rather than only in the plan.**

The mechanism is a **hard cut**, not overlap. The two halves are disjoint, nothing is processed twice, and D-01 AMENDED's overlap clause stays dropped on its measured ground — single matches of the built-in rules reach 200 006 characters and user patterns are unbounded by construction, so no finite overlap constant is defensible. Verified by criterion: `grep -ci overlap` outside comment lines returns 0.

The three reasons the `(?m)^` trap is not reopened are written into `splitPoint`'s comment, as the plan required, and they are load-bearing in a specific order. A newline-free window has **no interior line anchors to corrupt**, so a cut creates exactly one artificial line start rather than one per line. That artificial anchor can only **over-redact**, which is fail-safe. And the branch is reachable **only** from `dropOrRetry` — only where a rule has already timed out and the alternative is discarding the window outright — so a match truncated by the cut loses nothing that would otherwise have been emitted. The third is what licenses the first two, and the comment says so explicitly, together with the counter-statement that this is **not** permission to cut mid-line anywhere else.

M3 turned that counter-statement from a caution into a measured fact: with the line branches removed, a window of ordinary prose is cut at the first **space** past the midpoint. That is the `(?m)^` trap in full, on entirely benign content, and it is now guarded by a named test rather than by a comment.

**2. The terminator set is derived, not stipulated.** `formBodyParamRegex` and `urlTokenParamRegex` share the value class `[^&\s"'<>]+`, so neither match can span an `&` or any whitespace; `jsonSecretKeyRegex`'s value is either a `"`-delimited string or an unquoted scalar, and in well-formed minified JSON both are immediately followed by `,`, `}` or `]`. The derivation is in the `safeCutPoint` comment so a future reader can check the set against the rules rather than trust it.

**3. The straddle residual is recorded, not solved** — the same class the maintainer confirmed for 21-09's analogous decision. The cut only **moves a boundary**; every byte still lands in exactly one window and is still scanned, so this is not "failing open" in D-02's sense. What remains is a rule false negative across a cut, and it is strictly better than the status quo, in which the content was not scanned *and not emitted at all*. Recorded in ADR-14 and `CONCERNS.md`.

**4. `WINDOW_RETRY_MAX_DEPTH` capped at 4 rather than higher**, with both bounds stated in the comment so neither is mistaken for the other: the real ceiling on retry work is `MAX_REDACTION_BUDGET_MS`, because every retry runs under the same budget clock, while the depth cap exists solely to hold marker bloat at `2^depth` per window (Pitfall 8). The measurement in section 3 confirms the cap is doing that job and not more.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 — Bug] `splitPointPrefersASafeCutBoundaryInMinifiedJson` was vacuous as written**

- **Found during:** Task 2 mutation check M2
- **Issue:** The plan specified a fixture "built by repeating a short `{"k":"v"},`-style fragment". Taken literally, that is a 10-character fragment, and 200 repetitions put the midpoint of the 2 000-character window at index 1000 — whose preceding character is the fragment's trailing `,`, a terminator, by pure arithmetic coincidence. M2, which deletes the forward search entirely and always returns the midpoint, **passed**. The test asserted nothing about the mechanism it was written to guard.
- **Fix:** Fragment changed to `{"kk":"vv"},` (12 characters) × 167, putting the midpoint of the 2 004-character window after a `:`. Added an explicit anti-vacuity guard asserting the fixture is misaligned, plus a comment recording the coincidence so a future edit cannot silently restore it.
- **Files modified:** `src/test/kotlin/com/six2dez/burp/aiagent/redact/RedactionTest.kt`
- **Verification:** M2 re-run — now fails with `it landed after ':'`, and **only** that test fails.
- **Committed in:** `cb360b1`

**2. [Rule 1 — Bug] `splitPointStillCutsAtALineBoundaryWhenOneExists` was vacuous as written**

- **Found during:** Task 2 mutation check M3
- **Issue:** The first fixture was a run of hyphenated words with no spaces and no commas. Because `\n` is whitespace and `safeCutPoint` treats whitespace as a terminator, the newline was the fixture's *only* terminator — so a forward character search finds it too, and `window[cut - 1] == '\n'` cannot distinguish the two paths. M3, which deletes both line-boundary branches, **passed**. This is the more serious of the two: the mutation it failed to catch would cut ordinary prose and HTML at the first space past the midpoint, which is precisely the `(?m)^` trap 21-RESEARCH.md proved.
- **Fix:** Fixture changed to `"key value, more text here\n"` × 41, which carries spaces and a comma so a naive forward search lands on one of those first, and whose midpoint (533) falls mid-line so the backward search is genuinely exercised. Added an anti-vacuity guard that computes the first terminator at or after the midpoint and asserts it is **not** a newline.
- **Files modified:** `src/test/kotlin/com/six2dez/burp/aiagent/redact/RedactionTest.kt`
- **Verification:** M3 re-run — now fails with `expected: <\n> but was: < >`, and **only** that test fails.
- **Committed in:** `cb360b1`

---

**Total deviations:** 2 auto-fixed (both Rule 1). No architectural change, no new dependency, no scope creep — both are corrections to tests this plan itself introduced.

**Impact on plan:** Substantive. Without them, this plan would have shipped two decorative tests inside the plan whose stated purpose is to remove a decorative test. The plan's own instruction — "verify by mutation rather than inspection" — is what caught both; inspection would not have, because both fixtures look obviously correct and the arithmetic that defeats them is invisible at a glance.

## Issues Encountered

**1. I reverted uncommitted work with `git checkout --`, and the mutation-hygiene rule is exactly why that rule exists.**

After confirming M4 and M4b, I ran `git checkout -- RedactionTest.kt` to undo M4b's temporary assertion swap. That file also carried the *uncommitted* Deviation 1 and 2 fixture fixes, which were destroyed. Nothing was permanently lost — the edits were re-applied from the transcript and re-verified — but the root cause is worth stating plainly: **the fixture hardening was real work sitting in the same file as a mutation, which makes `git checkout --` unable to tell them apart.** The standing rule ("commit the real implementation FIRST, then apply each mutation on top of the committed base") applies to *every* intermediate correction, not only to the task's headline implementation. The hardening was committed as `cb360b1` before any further mutation ran, and M1 was then re-confirmed against that final base.

**2. A grep criterion was already satisfied before the change (benign, no action).**

Task 2's criterion `grep -c 'or 0 when the window cannot be split without cutting a line'` expects 0. It returned **0 before the change as well**, because the pre-existing comment wrapped that phrase across two source lines (`…the window cannot` / `be split without cutting a line`) and `grep` matches per line. The criterion could therefore never have detected a failure to correct the claim. The **intent** was satisfied regardless: the falsified sentence is gone in full, replaced by the shipped behaviour and its justification. Nothing was deleted or reworded to make a number match. This is the seventh grep-criterion false positive recorded in this phase.

**3. The plan's prediction about which assertion goes red is off by one position.** Documented in Required measurements section 1 rather than here, because it changes what the summary must record rather than what was built. Both assertions are red; JUnit reports the earlier one.

**4. Timing exposure on the new end-to-end test, declared rather than discovered later.** `newlineFreeOversizeBodyIsScannedNotDestroyed` asserts *successful* redaction on a 4 MB fixture, so it carries the same structural exposure 21-09 recorded: `SafeRegex` enforces its deadline through a wall-clock check in `DeadlineCharSequence.get()`, which JaCoCo instruments once per character. Post-fix it runs in 1.9 s against a 30 s bound and the ladder has three spare levels of headroom before it would drop anything, so the margin is much wider than 21-09's case — but the exposure is the same class. It was stable across **six** consecutive `redact.*` runs and **two** full-suite runs with no recurrence. The mitigation is structural rather than hopeful: the deterministic half of CR-04 is asserted separately by `splitPointCutsNewlineFreeWindowsInsteadOfRefusing`, which is a pure-function test that runs in 0 ms. **If this test ever goes red in CI while that one stays green, the diagnosis is deadline pressure under instrumentation, not a CR-04 regression.** That instruction is written into the test's own comment, not only here.

## Verification

| Check | Result |
|---|---|
| `./gradlew test` (full suite) | **BUILD SUCCESSFUL** |
| `./gradlew test --tests "com.six2dez.burp.aiagent.redact.*"` | **BUILD SUCCESSFUL** — 34 tests in `RedactionTest`, 0 failures |
| `./gradlew ktlintCheck detekt` | **BUILD SUCCESSFUL** |
| `git diff --stat 51a441b HEAD -- detekt-baseline.xml` | **empty** — byte-identical (QUAL-07) |
| SC6 canaries (all 10 named) | green, including both deliberate SC6 inversions, untouched |
| 21-09's two boundary sweeps | green |
| `grep -c 'SAFE_CUT_SEARCH_CHARS' Redaction.kt` | 3 (declaration + 2 uses/references) — criterion is "at least 2" |
| `grep -c 'internal fun testSplitPoint' Redaction.kt` | 1 |
| `grep -c 'WINDOW_RETRY_MAX_DEPTH = 4' Redaction.kt` | 1 |
| `grep -c 'or 0 when the window cannot be split without cutting a line' Redaction.kt` | 0 |
| `grep -v '^\s*//' Redaction.kt \| grep -ci 'overlap'` | 0 |
| `git diff -U0 51a441b HEAD -- Redaction.kt \| grep -c 'MAX_JSON_BOUNDARY_LOOKAHEAD_LINES\|isJsonPairBoundaryContinuation\|pairMayBeInFlightAt'` | 0 — plan 21-09's work untouched |
| `git diff -U0 51a441b HEAD -- Redaction.kt \| grep -cE '(hkdf\|HKDF\|anonymizeHost)'` | 0 — HKDF / RFC 5869 vector untouched |
| `grep -c 'fun splitPoint…'` × 3, `grep -c 'fun newlineFreeOversizeBody…'` | 1 each |
| `grep -c 'contains(oversizeBody)' RedactionTest.kt` | 0 — vacuous assertion gone |
| `grep -v '^\s*//' RedactionTest.kt \| grep -c 'No unscanned window may reach the output'` | 2 |
| `grep -c '^## ADR-14' DECISIONS.md` / heading still says `body stage` | 1 / yes |
| `grep -c 'redaction never fails open' DECISIONS.md` | 0 |
| `grep -c 'never splitting a line'` / `grep -c 'no line boundary'` | 1 / 1, both on the same Decision paragraph (line 157) |
| `grep -c 'SAFE_CUT_SEARCH_CHARS' DECISIONS.md` | 2 |
| `grep -c '^- Residual' DECISIONS.md` | 3 -> 4 |
| Rejected-alternatives list (overlap-based windowing, `Matcher.region()`) | unchanged |
| `git diff --stat 21-CONTEXT.md` | empty — locked decision untouched |
| STATE.md / ROADMAP.md / REQUIREMENTS.md | **not modified** — orchestrator owns those writes |

Every Gradle invocation used `JAVA_HOME=$(/usr/libexec/java_home -v 21)`.

## Known Stubs

None. No hardcoded empty values, placeholder text or unwired components were introduced. `safeCutPoint`'s midpoint fallback is a deliberate, documented behaviour with a stated rationale, not a stub.

## Threat Flags

None. No new network endpoint, auth path, file access pattern or schema change at a trust boundary. The change is confined to a pure index function, its two helpers and one constant; `Redaction` remains a stateless `object` and every piece of the new state is a local (T-21-23).

Dispositions from the plan's register, as shipped: **T-21-33** and **T-21-06** mitigated (`splitPoint` fallback + depth 4, guarded by `newlineFreeOversizeBodyIsScannedNotDestroyed` and `splitPointCutsNewlineFreeWindowsInsteadOfRefusing`); **T-21-34** and **T-21-35** accepted and recorded in ADR-14 and `CONCERNS.md`; **T-21-03** mitigated with teeth, and the old gate's inadequacy demonstrated by M4b rather than argued; **T-21-08** mitigated (budget is the real ceiling, depth cap holds marker bloat — measured at 19 and 16 markers); **T-21-26** mitigated (ADR-14 qualified); **T-21-09** mitigated (HKDF untouched, verified by diff); **T-21-SC** — zero packages installed, no new Gradle dependency.

## Next Phase Readiness

- **CR-04 and WR-05 are both closed**, and both closures are mutation-verified rather than inspected.
- **For the verifier:** the RED/GREEN gate commits are `efb761e` (test) -> `354d0de` (fix), in that order, with `cb360b1` a test-only correction forced by the mutation checks and `89f1297` the records.
- **Carried forward:** the straddle residual at a character cut, and the artificial `(?m)^` anchor, are recorded in ADR-14 and `CONCERNS.md` as accepted. Neither has a complete fix — an overlap constant is the obvious move and is unsound for the reason D-01 AMENDED already records.
- **A note for whoever touches `splitPoint` next:** `splitPointStillCutsAtALineBoundaryWhenOneExists` is not a formality. M3 showed that removing the line branches silently degrades every prose and HTML window to a mid-line cut, and that the obvious fixture for that test does not catch it. Read the fixture comment before changing the fixture.

## Self-Check: PASSED

All four claimed files exist on disk:
`src/main/kotlin/com/six2dez/burp/aiagent/redact/Redaction.kt`,
`src/test/kotlin/com/six2dez/burp/aiagent/redact/RedactionTest.kt`,
`DECISIONS.md`, `.planning/codebase/CONCERNS.md`.

All four claimed commits exist in `git log`: `efb761e`, `354d0de`, `cb360b1`, `89f1297`.

---
*Phase: 21-redaction-completeness*
*Completed: 2026-08-12*
