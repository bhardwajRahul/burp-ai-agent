---
phase: 21-redaction-completeness
plan: 13
subsystem: redaction
tags: [privacy, redaction, regex, windowing, json, kotlin]

# Dependency graph
requires:
  - phase: 21-redaction-completeness
    provides: "21-09's windowEnd extension loop, isJsonPairBoundaryRisk, pairMayBeInFlightAt, isJsonPairBoundaryContinuation and the two committed boundary sweeps"
  - phase: 21-redaction-completeness
    provides: "21-11's splitPoint/safeCutPoint newline-free window cut, whose seam tests bound the same windowEnd"
provides:
  - "endsInsideOpenQuotedValue — models the open-string state jsonSecretKeyRegex's \"[^\"]*\" value is in at a window cut"
  - "isJsonPairBoundaryRisk widened to a three-clause disjunction; pairMayBeInFlightAt and isJsonPairBoundaryContinuation inherit it unchanged"
  - "windowedScanRedactsJsonPairWhoseValueStraddlesTheCut — the third boundary sweep, guarding a shape the two existing sweeps structurally cannot construct"
  - "Three overclaiming coverage records corrected: Redaction.kt's D-01 paragraph, DECISIONS.md ADR-14, .planning/codebase/CONCERNS.md"
affects: [redaction, mcp-tool-output, passive-scanner, phase-21-review-3]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Boundary predicates model regex STATE, not surrounding punctuation"
    - "Fixture geometry is measured against the builder before a sweep is trusted"

key-files:
  created:
    - .planning/phases/21-redaction-completeness/deferred-items.md
  modified:
    - src/main/kotlin/com/six2dez/burp/aiagent/redact/Redaction.kt
    - src/test/kotlin/com/six2dez/burp/aiagent/redact/RedactionTest.kt
    - DECISIONS.md
    - .planning/codebase/CONCERNS.md

key-decisions:
  - "The reviewer's literally-suggested fixture is vacuous under boundarySweepBody and was replaced with a measured-geometry one, not transcribed"
  - "The falsified sentence is paraphrased in its retirement note rather than quoted, so it is not left verbatim in the file"
  - "M2 (backslash-escape limb) kills no test and is recorded as an unguarded limb rather than given an invented test"
  - "W-04's newlineFreeOversizeBodyIsScannedNotDestroyed failure is pre-existing and out of scope; logged to deferred-items.md, not absorbed"

patterns-established:
  - "Predicate-vs-bound distinction: a missing STATE is not narrowed by raising a cap, and records must say which one they bound"
  - "Fixture-geometry proof: sweep the builder over the candidate fixture and record the leak set BEFORE writing assertions"

requirements-completed: [PRIV-06]

# Metrics
duration: 32min
completed: 2026-08-12
---

# Phase 21 Plan 13: JSON Open-Quoted-Value Window Straddle Summary

**`jsonSecretKeyRegex` pairs whose quoted value carries a raw newline across a window cut are now redacted on the windowed path at every swept alignment, closing a default-configuration redaction bypass that survived a fix plus three mutations because the risk predicate modelled punctuation instead of the open-string state `[^"]*` is actually in.**

## Performance

- **Duration:** 32 min
- **Started:** 2026-08-12T13:08Z
- **Completed:** 2026-08-12T13:40Z
- **Tasks:** 3/3
- **Files modified:** 4 (+1 created)

## Accomplishments

- Reproduced 21-REVIEW-2 CR-01 as an **observed** RED test with the reviewer's exact shape — windowed leak, `dropMarker=false`, single-pass control clean.
- Discovered and corrected a **vacuity trap in the reviewer's own suggested fixture** before it could ship as a green test (see Measurement 2).
- Closed the leak by widening the *predicate*, not the bound: `MAX_JSON_BOUNDARY_LOOKAHEAD_LINES` is untouched at 8.
- Proved the new sweep guards a **distinct mechanism** by mutation M1: it fails while both existing sweeps stay green.
- Corrected all three overclaiming records in the same pass, per D-08 REFINED.

## Required measurements

The plan's `<output>` block requires five. All five measured, none recalled.

### 1. The single-pass control — the asymmetry that IS the defect

Driven through the real `Redaction.apply` under `PrivacyMode.STRICT`, **before any assertion was written**, on a 36-character document (`MAX_REDACTION_BODY_CHARS` = 1 000 000, so this is unambiguously the single-pass path):

```
=== FACT 1: single-pass control, two-line open-quoted-value pair ===
input len : 36 (MAX_REDACTION_BODY_CHARS=1000000)
input :   "api_key": "AK\nSTRADDLE-SECRET-2"\n
output:   "api_key": "[REDACTED]"\n
redacted? true
```

**Yes — the shipped rule redacts the two-line open-quoted-value shape below the window width.** `[^"]*` spans the raw newline, so the pair is one match. The reviewer's literal shape behaves identically:

```
=== FACT 1b: single-pass control, reviewer's literal shape ===
input :   "api_key": "STRADDLE-SECRET-2\ntail-of-value"\n
output:   "api_key": "[REDACTED]"\n
redacted? true
```

So the same content is redacted below the window width and leaked above it. That asymmetry — not a coverage gap — is what makes this a redaction bypass keyed only on payload size and alignment.

### 2. Shifts swept, first failing shift, and a vacuity trap in the plan's own reference fixture

**The reviewer's literally-suggested fixture is vacuous under `boundarySweepBody`, and this was measured, not guessed.** Both candidate fixtures were swept over 40 alignments through the live `Redaction.apply` before the test was written:

| Fixture | First line | Leaks / 40 | Failing shifts |
|---|---|---|---|
| `"  \"api_key\": \"STRADDLE-SECRET-2\ntail-of-value\"\n"` (21-REVIEW-2's literal suggestion) | 31 chars | **0** | — |
| `"  \"api_key\": \"AK\nSTRADDLE-SECRET-2\"\n"` (shipped) | 16 chars | **8** | 0, 1, 2, 3, 20, 21, 22, 23 |

All 8 leaks reported **`dropMarker=false`** — a leak, not a fail-closed drop — reproducing the reviewer's shape.

The cause, from the measured cut geometry:

```
shift=0  pairStart=999981 cutAfterNewlineAt=999997 delta=16 tail=\n  "api_key": "AK\n
shift=3  pairStart=999984 cutAfterNewlineAt=1000000 delta=16 tail=z\n  "api_key": "AK\n
shift=4  pairStart=999985 cutAfterNewlineAt=999984 delta=-1 tail=yyyyyyyyyyyyy\nzzzz\n
```

`boundarySweepBody` places the pair **periodically** in `PAD_LINE_CHARS`: across every shift `pairStart` lands in `[999981, 1000000]`. For the cut to fall *after the pair's first line* rather than at the pair's start, that first line must be at most 19 characters. At 31 characters the cut always lands at `delta=-1` — the pair's start — handing the whole pair to the next window, where it redacts normally. **Widening the sweep cannot rescue it**, because `pairStart` is periodic with period 20, so shifts 24-79 revisit the same 20 positions. This is why the plan's "widen to 80 shifts" escape hatch was not needed and would not have worked.

Observed RED at the committed test commit `6f93b80`, verbatim:

```
org.opentest4j.AssertionFailedError: shift=0: a quoted value straddling a window boundary must still be redacted ==> expected: <false> but was: <true>
```

An assertion failure, not a compile error. Shifts swept: **24** (`BOUNDARY_SWEEP_SHIFTS`, unchanged); JUnit aborts on first failure, so 0 is the first of the 8-member failing set measured independently above.

At that same commit both existing sweeps **PASSED**, and `git diff --stat HEAD~1 -- src/main` was **empty** — the RED commit touches no production code.

### 3. Mutation checks

Both applied **on top of the committed implementation** (`b72f852`) and reverted with `git checkout -- <path>`. No mutation was hand-edited out.

| # | Mutation | Result | What it proves |
|---|---|---|---|
| **M1** | Third clause removed from `isJsonPairBoundaryRisk`, restoring the two-clause form | `windowedScanRedactsJsonPairWhoseValueStraddlesTheCut` **FAILED at shift=0**; `windowedScanRedactsJsonPairAcrossEveryBoundaryAlignment` **PASSED**; `jsonPairWithBlankLineBetweenKeyAndValueIsRedacted` **PASSED** | The new sweep guards a **distinct mechanism**. It is not a duplicate of the existing two, and the existing two genuinely cannot reach this shape. |
| **M2** | `endsInsideOpenQuotedValue` made to ignore the backslash escape (count every `"`) | **No test failed.** Full suite: 645 tests, 1 failure — and that one is the pre-existing W-04 canary present at the unmodified baseline too. | The backslash-escape limb is **UNGUARDED**. Stated plainly rather than papered over. |

**M2 is an unguarded limb and no test was invented for it.** The plan explicitly directed this outcome be recorded rather than manufactured. Note the limb is not decorative: `jsonSecretKeyRegex`'s own documented limitation is that a value containing an escaped quote is only partially matched, so the escape handling keeps the predicate consistent with the rule it serves. It is simply not currently pinned by a test, and a future contributor should know that before relying on it.

Tree confirmed **byte-identical to the commit after each revert** — `git diff HEAD --stat` and `git status --short` both empty, checked after M1 and again after M2.

### 4. Residual-bullet counts in `DECISIONS.md`

Taken from the prior **committed** file, not from memory:

| | `grep -c '^- Residual' DECISIONS.md` |
|---|---|
| Before (`git show HEAD:DECISIONS.md`) | **4** |
| After | **4** |

**Unchanged, as the plan expected.** This correction narrows an existing residual rather than adding one, so the eight-line bullet was **extended in place** and no new `- Residual` bullet was created.

### 5. Monotonicity canaries

Full suite on the final source state: **645 tests, 1 failure.** Named individually:

| Canary | Result |
|---|---|
| `balancedModeRedactsUrlTokensInQueryStrings` | PASS |
| `bodyFormLeadingFieldRedacted` | PASS |
| `bodyJsonSecretKeysRedacted` | PASS |
| `offModePreservesBodies` | PASS |
| `hkdfMatchesRfc5869Vector` | PASS |
| Two locked SC6 inversions | untouched; all SC6/anonymization tests PASS (`anonymizedHostMatchesExpectedFormat`, `hostAnonymizationFormatIsStable`, `hostAnonymizationIsStablePerSalt`, `redactScanMetadata_strictAnonymizesHosts`) |
| 21-08 cookie tests (11, incl. `cookieSectionValuesRedactedPerName`, `cookieSectionDeadlineFailsClosed`, `poisonedCookieHeaderCannotTerminateTheCookieSection`) | PASS |
| `cookieSectionDecoyDoesNotShieldRealSection` | PASS |
| 21-11 seam tests (`splitPointCutsNewlineFreeWindowsInsteadOfRefusing`, `splitPointPrefersASafeCutBoundaryInMinifiedJson`, `splitPointStillCutsAtALineBoundaryWhenOneExists`) | PASS |
| 21-09 sweeps (`windowedScanRedactsJsonPairAcrossEveryBoundaryAlignment`, `jsonPairWithBlankLineBetweenKeyAndValueIsRedacted`) | PASS |
| 21-12 `factoredKeyVocabularyMatchesItsReadableSpecification` | PASS |
| `newlineFreeOversizeBodyIsScannedNotDestroyed` | **FAIL — pre-existing, did not move** |

**No canary moved.** The one failure is proven pre-existing: see "Issues Encountered".

## Fixture-reachability argument for the new test

The phase's dominant defect is a test that passes against a mutation unwiring the rule it guards, because some other rule also catches the fixture. `STRADDLE-SECRET-2` is reachable by `jsonSecretKeyRegex` and by nothing else in either stage:

- **No `=` anywhere in the pair** — `formBodyParamRegex` requires a `key=` shape. `urlTokenParamRegex` requires that *and* a leading `?` or `&`; this matters specifically because it runs **unbounded in the header stage** and would otherwise mask the defect entirely.
- **Not `Bearer`- or `Basic`-prefixed, does not begin `eyJ`** — `bearerRegex`, `basicAuthRegex`, `jwtRegex` cannot match.
- **No `Cookie:`/`Set-Cookie:` header, no `=== COOKIES ===` section, no ` (COOKIE)` type suffix** — neither cookie rule, including 21-08's section rule, can reach it.
- **Padding is `y` and `z` only** — the filler cannot manufacture a `[REDACTED]` and create a false positive on the surviving-key leg.
- **No custom pattern registered**, and `@AfterEach resetCustomPatterns` prevents bleed from `oversizeBodyFailsClosed` / `subWindowBodyFailsClosed`, which both install `(a+)+$`.

**Verified by mutation M1, not by inspection.**

Two further anti-vacuity properties, both measured rather than asserted:

1. **The no-drop-marker leg is load-bearing.** A dropped window also removes the sentinel, so "sentinel absent" alone would be satisfied by the fail-closed path and the sweep would degrade into a second `oversizeBodyFailsClosed`. All 8 pre-fix leaks reported `dropMarker=false`, confirming the leg discriminates.
2. **The fixture reaches the windowed path only.** The per-shift guard `body.length > Defaults.MAX_REDACTION_BODY_CHARS` is retained, so fixture drift cannot silently sweep the single-pass path — which, per Measurement 1, redacts this shape and would make the test pass for the wrong reason.

## Task Commits

1. **Task 1: RED third boundary sweep** — `6f93b80` (`test`)
2. **Task 2: `endsInsideOpenQuotedValue` + source record correction** — `b72f852` (`fix`)
3. **Task 3: ADR-14 and CONCERNS.md corrections** — `bfcea18` (`docs`)

## Files Created/Modified

- `src/main/kotlin/com/six2dez/burp/aiagent/redact/Redaction.kt` — `endsInsideOpenQuotedValue`, widened `isJsonPairBoundaryRisk`, corrected D-01 paragraph, corrected cap residual note, corrected `windowEnd` trigger description
- `src/test/kotlin/com/six2dez/burp/aiagent/redact/RedactionTest.kt` — `windowedScanRedactsJsonPairWhoseValueStraddlesTheCut`
- `DECISIONS.md` — ADR-14 residual bullet extended; byte-identity retirement bullet notes the recurrence
- `.planning/codebase/CONCERNS.md` — body-stage entry extended in place
- `.planning/phases/21-redaction-completeness/deferred-items.md` — **created**, logging W-04

## Decisions Made

**1. The reviewer's suggested fixture was measured, not transcribed — and was replaced.**

21-REVIEW-2's `<fix>` block proposes `"  \"api_key\": \"STRADDLE-SECRET-2\ntail-of-value\"\n"`. Swept through `boundarySweepBody` it leaks **0 of 40**. Transcribing it verbatim would have produced a green test at every alignment, and CR-01 would have been reported closed on the strength of a vacuous guard — the exact failure the phase has now recorded five times. The shipped fixture puts the sentinel on the pair's *second* line so the first line stays at 16 characters, which is what lets the cut land inside the open quoted value. The reasoning is written into the test's own comment so a later "simplification" cannot silently vacate it.

**2. The predicate was widened; the bound was not.** `MAX_JSON_BOUNDARY_LOOKAHEAD_LINES` stays 8. The defect was a missing predicate **state**, not an insufficient bound — the cap was never reached for this shape because the lookahead never started. Raising it would have been a non-response. This distinction is now written into the constant's own residual note, because reading the old note naturally suggests the opposite fix.

**3. The maintainer's cap ruling was not disturbed.** At the cap the window is still deliberately not dropped. This plan extends *detection* only; the extension still merely moves a boundary, every byte still lands in exactly one window, and `scanWindow`/`dropOrRetry` still fail closed.

**4. The falsified sentence is paraphrased in its retirement note, not quoted.** The retirement note initially reproduced the falsified claim verbatim. That leaves the exact overclaiming sentence in the file where a future reader — or a grep-based audit — can lift it back out of its retirement framing. It is now paraphrased, which preserves the full record while removing the hazard. This also resolves the plan's grep criterion honestly rather than by accident (see "Issues Encountered").

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 — Bug] The plan's reference fixture cannot reproduce the defect it was written for**

- **Found during:** Task 1
- **Issue:** The fixture specified in the plan and in 21-REVIEW-2 §CR-01 §Fix has a 31-character first line. `boundarySweepBody` places the pair periodically in `[999981, 1000000]`, so the cut always lands at the pair's *start*, never inside the quoted value. Measured: **0 leaks over 40 alignments** pre-fix. Widening the sweep does not help — `pairStart` has period 20, so all shifts revisit the same 20 positions.
- **Fix:** Kept the required key (`api_key`), sentinel (`STRADDLE-SECRET-2`) and two-line open-quoted-value shape, but moved the sentinel to the second line so the first line is 16 characters. Measured **8 leaks over 40**, all `dropMarker=false`.
- **Files modified:** `src/test/kotlin/.../RedactionTest.kt`
- **Verification:** Observed RED at shift=0; mutation M1 confirms non-vacuity.
- **Committed in:** `6f93b80`

**2. [Rule 2 — Missing critical correctness] `windowEnd`'s own trigger description still named only the two-clause predicate**

- **Found during:** Task 2
- **Issue:** The plan named three records to correct. A fourth passage — `windowEnd`'s "JSON boundary safety" paragraph — described the extension as triggering only "when the last line ends with a colon or a double quote". Left alone, the file would have shipped a corrected D-01 paragraph directly above a stale mechanism description, which is the same defect this plan exists to remove (D-08 REFINED).
- **Fix:** Rewritten to name both newline-spanning positions (`\s*` around the colon, and `"[^"]*"`) and both predicate clauses. A CR-01 note was also added to `windowEnd`'s residual block distinguishing "how far an extension runs" (21-09's loop) from "when one starts" (this plan).
- **Files modified:** `src/main/kotlin/.../Redaction.kt`
- **Committed in:** `b72f852`

---

**Total deviations:** 2 auto-fixed (1 × Rule 1, 1 × Rule 2). No Rule 4 escalation; no architectural change; no new dependency.

## Issues Encountered

**1. A monotonicity canary is red, and it is pre-existing (W-04). Logged, not fixed.**

`newlineFreeOversizeBodyIsScannedNotDestroyed` fails on this machine:

```
STRICT: the pair must be redacted IN PLACE, keeping its key — not removed wholesale ==> expected: <true> but was: <false>
```

at 2.191 s against `MAX_REDACTION_BUDGET_MS = 2_000`.

**Proven pre-existing, not argued.** `Redaction.kt` was restored to the committed baseline (with the in-progress implementation backed up out-of-tree first, so no work was at risk) and the test run in isolation: it failed with the **identical** message. It then failed identically with the change applied. **The canary did not move.**

**Structurally unreachable by this change.** The fixture is newline-free and the test asserts that itself (`assertFalse(body.contains('\n'))`). A body with no `'\n'` takes `windowEnd`'s `lastNewline <= start` branch and returns before any boundary predicate is consulted, so `endsInsideOpenQuotedValue` is never evaluated for it.

This is exactly `21-REVIEW-2` **W-04**, whose bisection predicted the presentation precisely: at reduced budget `keptKeyAssert` breaks first while the secret assertion still holds. That is what was observed — **the secret assertion PASSED**, only the capability assertion failed, i.e. the window was dropped fail-closed. **No leak.** Proximate trigger is machine load: plan 21-14 was executing concurrently in a sibling worktree.

Not fixed here: W-04 is an open warning with its own recommended fix, and acting on it would mean editing a test outside this plan's declared surface and re-litigating `MAX_REDACTION_BUDGET_MS`, which the plan forbids. Recorded in `deferred-items.md` as **D-21-01**.

**Consequence for one acceptance criterion:** Task 2's "`./gradlew test` is BUILD SUCCESSFUL" is not satisfiable on this machine for reasons predating this plan. Intent satisfied instead: the failure set is **identical** before and after (645 tests, same single failure).

**2. The plan's grep criterion for the falsified sentence returns a false negative — the eighth this phase.**

The criterion is that `is covered by a bounded lookahead` returns nothing. A plain `grep` returned nothing **while the phrase was still present**, because my retirement note wrapped it across two source lines — precisely the failure mode the plan's own parenthetical warns about. Checked properly with a comment-stripped, whitespace-normalized search, which found **1 hit** inside the retirement note. Rather than accept a criterion satisfied by a line-wrap accident, the note was reworded to paraphrase the retired claim. Re-verified with the same normalized search: **0 hits**, and the D-01 paragraph read end to end no longer asserts the coverage.

No load-bearing comment was deleted and no assertion weakened to make a criterion pass.

## Verification

| Check | Result |
|---|---|
| `windowedScanRedactsJsonPairWhoseValueStraddlesTheCut` | RED at `6f93b80` (shift=0), GREEN at `b72f852` |
| `com.six2dez.burp.aiagent.redact.*` suite | 105 tests, 1 failure (pre-existing W-04) |
| Full suite | 645 tests, 1 failure (same, pre-existing) |
| `ktlintCheck` | clean |
| `detekt` | clean — no `ReturnCount`, no `LoopWithTooManyJumpStatements`, no `MagicNumber` |
| `git diff --stat -- detekt-baseline.xml` | empty (QUAL-07) |
| `MAX_JSON_BOUNDARY_LOOKAHEAD_LINES` | still `8` |
| `Defaults.kt` | unmodified |
| Protected-symbol diff (`hkdf`, `HKDF`, `anonymizeHost`, `WINDOW_RETRY_MAX_DEPTH`, `splitPoint`, `safeCutPoint`, `SENSITIVE_KEY_WORDS`) | **0** matching `+`/`-` lines in `git diff -U0 HEAD~1 -- Redaction.kt` |
| `21-CONTEXT.md`, `STATE.md`, `ROADMAP.md`, `REQUIREMENTS.md` | unmodified (empty `git diff --stat`) |
| `CONCERNS.md` body-stage entry structure | exactly one **Issue** / **Files** / **Fix approach** / **Impact** — extended, not duplicated |
| `CONCERNS.md` **Files** line names `endsInsideOpenQuotedValue` | yes |

## Known Stubs

None. `endsInsideOpenQuotedValue` is fully wired into `isJsonPairBoundaryRisk`, which both `pairMayBeInFlightAt` and `isJsonPairBoundaryContinuation` already delegate to, so the widened predicate is live on both the backward and forward paths.

## Threat Flags

None. No new network endpoint, auth path, file access or schema change. No Gradle dependency added and no new import — the Package Legitimacy Gate has nothing to evaluate (T-21-SC).

T-21-40 (the widened predicate firing on quote-heavy benign HTML) is mitigated as planned: `MAX_JSON_BOUNDARY_LOOKAHEAD_LINES` is unchanged, so the extension is still bounded at 8 lines and buys no unbounded window.

## Next Phase Readiness

CR-01 of `21-REVIEW-2.md` is closed with an observed RED, a mutation-proven guard, and three corrected records. Open items a reviewer should still expect:

- **W-04** (`newlineFreeOversizeBodyIsScannedNotDestroyed` racing its budget) is **currently red on the reference machine** and is logged as D-21-01. It is the most likely source of a false "regression" signal in the next review.
- The backslash-escape limb of `endsInsideOpenQuotedValue` is **unguarded** (M2). Recorded, not hidden.
- The eight-line cap residual stands, correctly scoped now to the cap alone.

## Self-Check: PASSED

All claimed files exist on disk (`Redaction.kt`, `RedactionTest.kt`, `DECISIONS.md`, `CONCERNS.md`, `deferred-items.md`, this SUMMARY). All three claimed commits exist in `git log`: `6f93b80`, `b72f852`, `bfcea18`, on base `222df36`. No claim in this document is unverified.
