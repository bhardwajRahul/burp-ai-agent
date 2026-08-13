---
phase: 21-redaction-completeness
plan: 18
subsystem: privacy-redaction
tags: [redos, safe-regex, redaction, teardown-race, diagnostics-sink, fail-closed, kotlin]

requires:
  - phase: 21-redaction-completeness
    provides: "replaceAllSafeReporting + timedOut (21-02), truncationLogger wiring (21-06), bodyStage seams (21-13/21-15/21-16/21-17)"
provides:
  - "SafeRegex has exactly one replacement entry point; the fail-open replaceAllSafe facade is deleted"
  - "ADVERSARIAL_PROBES: a (character class x terminator) ReDoS probe corpus replacing the single fixed probe"
  - "App.initialize re-validates persisted custom patterns through isPatternSafe before seeding"
  - "maybeLogTruncation cannot propagate a diagnostics-sink failure into a redaction pass"
  - "App.shutdown unwires Redaction.truncationLogger beside Redaction.clearMappings()"
affects: [phase-23-edt-confinement, future-redaction-rule-authors]

tech-stack:
  added: []
  patterns:
    - "ReDoS probe corpus keyed on (character class x terminator), not character class alone"
    - "Diagnostics sinks invoked under runCatching with the wrap enclosing ONLY the invocation"

key-files:
  created: []
  modified:
    - src/main/kotlin/com/six2dez/burp/aiagent/redact/SafeRegex.kt
    - src/main/kotlin/com/six2dez/burp/aiagent/redact/Redaction.kt
    - src/main/kotlin/com/six2dez/burp/aiagent/App.kt
    - src/test/kotlin/com/six2dez/burp/aiagent/redact/SafeRegexTest.kt
    - src/test/kotlin/com/six2dez/burp/aiagent/redact/RedactionTest.kt

key-decisions:
  - "WR-03: replaceAllSafe DELETED rather than @Deprecated — a String return type structurally cannot carry timedOut, and a deprecated-but-callable fail-open helper is still a fail-open helper"
  - "D-14's 'SafeRegexTest:44 stays green unchanged' clause is SUPERSEDED by the maintainer's 2026-08-12 scope decision; the fail-soft TEXT behaviour is preserved and re-asserted on replaceAllSafeReporting(...).text"
  - "WR-07's proposed probe corpus was measured INSUFFICIENT — it does not reject WR-07's own second example. The shipped corpus varies the TERMINATOR as well as the character class"
  - "No whitespace probe shipped: 11 whitespace-targeting candidates screened, none uniquely caught by it"
  - "maybeLogTruncation's runCatching wraps ONLY the sink invocation; limiter read, CAS and getAndSet stay outside"
  - "App.shutdown's truncationLogger clearing is SOURCE-asserted, not test-asserted, and is documented as such"

patterns-established:
  - "Probe corpus design: catastrophic (X+)+L backtracking needs a long X-run NOT followed by L, so terminator diversity matters as much as class diversity"
  - "Screen every proposed test candidate for the cheaper rejection path (zero-width) BEFORE writing it, and record which candidates were already-green"

requirements-completed: [PRIV-06]

duration: 78min
completed: 2026-08-13
---

# Phase 21 Plan 18: Redaction Completeness Gap Closure (WR-03 / WR-04 / WR-07 / W-08) Summary

**Deleted the last fail-open replacement facade in `redact/`, replaced the single-string ReDoS gate with a (character class x terminator) probe corpus that rejects patterns the review's own proposal would have accepted, and made a throwing diagnostics sink incapable of aborting a redaction pass.**

## Performance

- **Duration:** ~78 min
- **Tasks:** 3/3
- **Files modified:** 5
- **Tests:** 660 -> 668 (+8), 0 failures, 0 errors
- **detekt-baseline.xml:** byte-identical (QUAL-07 held)

## Commits

| Commit | Gate | Description |
|--------|------|-------------|
| `ee714f5` | — | `refactor(21-18)`: delete the fail-open `replaceAllSafe` facade (WR-03) |
| `0977226` | RED | `test(21-18)`: `isPatternSafe` accepts patterns catastrophic on digits and uppercase (WR-07) |
| `83818ec` | GREEN | `feat(21-18)`: widen the ReDoS gate to a probe corpus and re-validate at seed time (WR-07) |
| `110bcbf` | RED | `test(21-18)`: a throwing truncation sink aborts a redaction pass (WR-04 / W-08) |
| `66b3e80` | GREEN | `fix(21-18)`: a diagnostics sink can never abort a redaction pass, and shutdown unwires it |

---

## 1. WR-03 — zero-production-caller premise, re-verified before deletion

Taken on the committed base `a2523c0`, **before** any edit, rather than trusted from two reviews:

```
$ grep -rn 'replaceAllSafe\b' src/main src/test
src/main/.../SafeRegex.kt:75      * behaviour as [replaceAllSafe] — but ...     (KDoc cross-reference)
src/main/.../SafeRegex.kt:105    fun replaceAllSafe(                            (THE DECLARATION)
src/main/.../Redaction.kt:159    // SafeRegex.replaceAllSafe returns the ...    (comment)
src/main/.../Redaction.kt:1013   // ... Assigning replaceAllSafe's return ...   (comment)
src/main/.../Redaction.kt:1037   // SafeRegex.replaceAllSafe returns its ...    (comment)
src/main/.../Redaction.kt:1138   // replaceAllSafe's return value is never ...  (comment)
src/test/.../SafeRegexTest.kt:41,103                                            (the two tests being moved)
src/test/.../SafeRegexTest.kt:30,44,45,49,99                                    (their comments)
src/test/.../RedactionTest.kt:1957,2016                                         (comments)
```

**Result: zero production callers.** The only non-comment `src/main` occurrence was the declaration itself. The premise holds, so this was a removal of dead weight and not a behaviour change.

**The word boundary was verified on this machine before being relied on** (this phase has recorded nine grep-criterion false positives). `grep -rn 'replaceAllSafe\b'` returned 4 `Redaction.kt` lines while the unanchored `grep -rn 'replaceAllSafe'` returned 6 — the two extra being the `replaceAllSafeReporting` call sites at `Redaction.kt:1061` and `:1155`. The boundary excludes `replaceAllSafeReporting` as documented.

### Defect found: the plan undercounted the warning comments

The plan's `read_first` named **three** `Redaction.kt` comments. There are **four** — the fourth is at `Redaction.kt:159`, in the `COOKIE_SECTION_BUDGET_MS` block, arguing why the cookie-section rule is deliberately not routed through `SafeRegex`. It named the deleted symbol just as the other three did, so leaving it would have reproduced exactly the record-outlives-the-code defect this round exists to close. All four were corrected. Two further comments in `RedactionTest.kt` (`:1957`, `:2016`) also named it and were corrected; `RedactionTest.kt` is in the plan's `files_modified` frontmatter, so this is inside the plan's surface even though Task 1's `<files>` list omitted it.

`git diff -U0 -- Redaction.kt` for Task 1 shows **comment lines only**; no executable line changed.

### Grep acceptance criterion was unsatisfiable — INTENT satisfied, defect recorded

Task 1's criterion says `grep -rn 'replaceAllSafe\b' src/main src/test` must return **no** hits. That criterion contradicts the same task's `<action>`, which requires the KDoc to explain "why no un-reporting facade exists any more ... so a future contributor who wants a convenience wrapper finds the reason it was removed rather than re-adding it". A contributor finds that note by grepping the name; a note that omits the name is unfindable. Grep-criterion false positive **#10** for this phase.

**INTENT satisfied instead, and it is the stronger property:**

- `grep -rn 'fun replaceAllSafe('` → **0 hits** (no declaration)
- `grep -rn '\.replaceAllSafe('` → **0 hits** (no call site)
- 4 mentions remain, all historical, and each carries `DELETED` / `deleted` / `since-deleted` **on the same physical line as the name**, so a line-oriented audit cannot mistake one for a live reference. The KDoc was reworded specifically to move the word `DELETED` onto the name's own line — this phase has already been bitten by a criterion that a line wrap satisfied or defeated by accident.

No load-bearing comment was deleted and no assertion was weakened to make a number match.

### D-14 supersession, recorded rather than glossed

D-14 states "the existing fail-open assertion in `SafeRegexTest:44` stays green unchanged". That clause described plan 21-02's *scope*; WR-03 was raised afterwards and the maintainer put it in scope on **2026-08-12**. The clause is therefore **superseded, not violated**:

- the fail-soft **behaviour** is untouched — on timeout the returned text is still byte-identically the input;
- it is still asserted, now on `replaceAllSafeReporting(...).text` in `catastrophicPatternTimesOutAndReturnsInput`;
- `SafeRegexTest` still carries **four** replacement tests, still split into the TEXT half (`catastrophicPatternTimesOutAndReturnsInput`, `benignReplaceAppliesReplacement`) and the FLAG half (`catastrophicPatternReportsTimedOut`, `benignPatternReportsNotTimedOut`). They were deliberately **not** merged into two: collapsing them would erase the distinction between "the text you get back is safe" and "you can tell you got it back for the wrong reason".

The supersession is also recorded in-source, in `catastrophicPatternTimesOutAndReturnsInput`'s comment.

---

## 2. WR-07 — candidate screening, and why the review's proposed fix does not work

Every candidate was screened **before** a test was written, using a byte-equivalent replica of `isPatternSafe` (same `DeadlineCharSequence`, same 50 ms deadline) so probe designs could be measured without touching shipped source.

### Screening results for WR-07's three named candidates

| Candidate | Matches empty? | Accepted today? | Verdict |
|---|---|---|---|
| `(\d+)+@` | no | **yes** | **SHIPS** — genuine red-before-green |
| `([a-z]+)+!` | no | **yes** | **SHIPS**, but *not* closable by the corpus WR-07 proposed |
| `(\w+\s?)+$` | no | **NO — already rejected** | ships as a labelled regression pin, **not** as evidence |

Also screened and rejected as test candidates: `([a-zA-Z]+)+!` (same probe as `([a-z]+)+!`, adds no class), `([A-Za-z0-9]+)+@` and `(\w+)+@` (both already rejected today — would have been vacuous).

**`(\w+\s?)+$` is already green today.** `\w` matches `a`, so the existing 2 000-'a' probe already defeats its `$` anchor and blows up. A test asserting its rejection passes identically with the widening reverted. It ships anyway because the property is real, but it is labelled in-source as an **already-green regression pin** so nobody later reads it as evidence the corpus works. Had the plan's three candidates been written without screening, one third of the "new" evidence would have been vacuous — the tenth vacuity instance of this phase, avoided.

### The measured finding that changed the fix

WR-07 proposed varying the **character class** across four probes, all still ending in `'!'`. Measured, that corpus does **not** reject WR-07's own second example:

```
([a-z]+)+!   LOWER!=surv  DIGIT!=surv  MIXED("aA1_-"x400)!=surv  WORDS("x"x50+" ")x40!=surv
             LOWER-=REJ(50ms)   LOWER_SPACE=REJ(50ms)
```

The reason is the **terminator**, not the class. Catastrophic backtracking in the `(X+)+L` family requires a long `X`-run **not** followed by `L`. `([a-z]+)+!` on a lowercase run ending in `'!'` matches greedily and immediately, never backtracking. It is linear on every probe that lacks a long lowercase run. Only a lowercase run with a *different* terminator forces the failure that triggers the blow-up.

The review's mixed probe `"aA1_-".repeat(400)` compounds this: its runs are one character per class, so no nested quantifier can blow up on it at all. It rejected nothing in the entire screening.

**Shipped corpus — 3 classes x 2 terminators:**

```kotlin
"a".repeat(2_000) + "!"   // original probe, VERBATIM
"a".repeat(2_000) + "-"   // rejects ([a-z]+)+!
"1".repeat(2_000) + "!"   // rejects (\d+)+@
"1".repeat(2_000) + "-"   // rejects (\d+)+!
"A".repeat(2_000) + "!"
"A".repeat(2_000) + "-"   // rejects ([A-Z]+)+!
```

Every realistic user-pattern class — hex `[a-f0-9]`, base64 `[A-Za-z0-9+/=]`, `\w`, `[a-z]`, `[A-Z]`, `\d`, `\S`, `.` — contains `a`, `1` or `A`, so each is reachable under at least one non-matching terminator. The original probe is kept **byte-for-byte first**, so nothing rejected before this change alters verdict for an unrelated reason (verified: all 10 existing `SafeRegexTest` rejection cases and all 5 acceptance cases hold).

**A fourth test was added beyond the plan's three:** `([A-Z]+)+!` (uppercase). The same analysis showed uppercase-only patterns escaping every probe the review proposed, and realistic user patterns are full of uppercase token shapes (`AKIA…`, `INTERNAL-…`, `ghp_…`).

### No whitespace probe — a measurement, not an omission

WR-07 proposed `("x".repeat(50) + " ").repeat(40) + "!"`. **Eleven** whitespace-targeting candidates were screened against it — `(x+ ?)+$`, `(x+ )+$`, `([a-z]+ )+$`, `(\w+ )+$`, `([a-z]+ )+#`, `(x+ )+@`, `(x+\s)+!`, `(\w+\s)+$`, `(\S+\s)+$`, `([a-z ]+)+#`, `([a-z]+\s?)+#` — and **not one is uniquely caught by it**. The two it does catch are already caught by the cheaper lowercase probes; the rest are not catastrophic at all, because a **mandatory separator inside the group removes the ambiguity** that drives the blow-up (`(x+ )+` is unambiguous; `(x+)+` is not). Shipping it would have cost a probe's worth of worst case for coverage that could not be demonstrated — the "named guard pointing at nothing" defect this round exists to close. The reasoning and all eleven candidates are recorded in-source so a future contributor with a genuine fixture can add the probe **with** that fixture.

### RED evidence (verbatim, commit `0977226`)

```
org.opentest4j.AssertionFailedError: WR-07: (\d+)+@ is catastrophic on a run of digits and must be
rejected; the all-lowercase probe alone accepts it because it contains no digit ==> expected: <false> but was: <true>

org.opentest4j.AssertionFailedError: WR-07: ([a-z]+)+! must be rejected; it survives EVERY
'!'-terminated probe because its own trailing literal is '!', so the corpus must terminate a
lowercase run some other way ==> expected: <false> but was: <true>

org.opentest4j.AssertionFailedError: WR-07: ([A-Z]+)+! is catastrophic on a run of uppercase and must
be rejected; no lowercase or digit probe reaches it ==> expected: <false> but was: <true>
```

Anti-vacuity is asserted in the suite itself, not only in this document: `wr07CandidatesAreNotRejectedByTheZeroWidthGuard` proves each candidate does **not** match the empty string, so WR-01's cheaper guard is not what rejects it.

### Counter-assertions (without these, the rejection tests are worthless)

- `realisticUserPatternsSurviveTheWidenedProbeCorpus` — 10 realistic shapes still **accepted**. A corpus that rejected everything would satisfy all four rejection tests while destroying the feature.
- `zeroWidthPatternsAreRejectedWithoutRunningAnyProbe` — WR-01's zero-width rejection keeps its own **distinct path above the probe loop**, asserted by **cost**: nine empty-matchers must return in under one probe deadline (measured 5-6 µs each, versus a 50 ms minimum for any probe timeout). This is what stops the widening from silently swallowing a separately documented control and its distinct save-path message.

### Measured seeding cost, through the real compiled `SafeRegex`

Ten realistic persisted patterns, `App.initialize`'s exact call shape:

| | 10 benign patterns (cold) | 10 benign (warm best) | 5-pattern stale list, 3 pathological |
|---|---|---|---|
| **BEFORE** (single probe) | 7.75 ms | 0.48 ms | **kept 4 of 5** in 50 ms |
| **AFTER** (6 probes) | 13.36 ms | 1.39 ms | **kept 2 of 5** in 151 ms |

The stale-list row is the finding: **the old gate kept 2 of the 3 pathological patterns** (`(\d+)+@` and `([A-Z]+)+!` were accepted; only `(a+)+$` was caught). Since D-05 each of those runs in **every** privacy mode including OFF and `bodyStage` fails **closed**, so each would spend `MAX_REDACTION_BUDGET_MS` and drop real content behind markers on every call. All three are now dropped at seed time for 151 ms once per launch.

**Worst case, stated rather than assumed.** The theoretical ceiling is `patterns x probes x DEFAULT_TIMEOUT_MS` = 6 x 50 ms = **300 ms per pattern**, but it is only reachable by a pattern that is slow-but-completing on five probes and pathological on the sixth. Measured reality: `([A-Z]+)+!`, rejected by the **sixth** probe, cost **50 ms end-to-end**, because the five it survives complete in microseconds. The realistic worst case for a fully pathological list is therefore ~50 ms per pattern — 20 pathological patterns would cost ~1 s, and the contrived 300 ms/pattern shape would cost 6 s. **This exceeds one second for a large pathological list and is flagged rather than silently accepted**; it is bounded, once per launch, and only reachable by patterns that are themselves the hazard being removed.

### EDT cost — REPORTED to Phase 23 / REL-05, not fixed here (T-21-67)

`isPatternSafe` runs **on the EDT** at save time via `SettingsPanel.validateAndCollectCustomPatterns`. This widening multiplies that path's worst case by the probe count: **6x**, i.e. up to 300 ms per pattern in the contrived case and ~50 ms per pathological pattern in practice. Benign patterns cost microseconds and are unaffected in any user-perceptible way. Per the plan's constraint, the save path was **not** restructured here — Phase 23 owns EDT confinement. Flagged in-source (`ADVERSARIAL_PROBES` KDoc block) and here.

The startup seeding path added in this plan is **not** on the EDT-critical path; it runs on the extension-load thread.

---

## 3. WR-04 / W-08 — which half is automated, which is source-asserted

**Stated plainly, and the shutdown step is NOT claimed as tested:**

| Half | Guarded by | Kind |
|---|---|---|
| `maybeLogTruncation`'s `runCatching` — a throwing sink cannot abort a pass | `RedactionTest.truncationLoggerThatThrowsDoesNotAbortRedaction`, mutation-verified by **M4** and **M5b** | **AUTOMATED** |
| `App.shutdown()` clearing `Redaction.truncationLogger` | source inspection only | **SOURCE-ASSERTED** |

`App.shutdown()` cannot be exercised without a live `MontoyaApi`, so no unit test reaches it. **M3 confirms this empirically** (below): the entire 667-test suite passes with the analogous `App.initialize` call site mutated away. The `runCatching` is the defence that holds **even if the shutdown line is ever removed**, and that relationship is documented in-source at both ends.

The RED failure (commit `110bcbf`) reproduced WR-04's predicted propagation path exactly:

```
java.lang.IllegalStateException: Extension has been unloaded
  at Redaction.maybeLogTruncation(Redaction.kt:821)
  at Redaction.redactCookieSections(Redaction.kt:291)
  at Redaction.testRedactCookieSections(Redaction.kt:361)
```

`redactCookieSections` is the **third** `maybeLogTruncation` call site, added by this phase, and it runs in the **header stage** of every `Redaction.apply` where `stripCookies` is true — the default BALANCED mode, on every MCP tool call and every passive scan. That is why the 21-12 deferral (accurate when written, when only oversized bodies could reach the sink) no longer held.

**Fixture reachability is exact:** `testRedactCookieSections` bypasses `Redaction.apply` entirely so no other rule runs, and `budgetMs = 0` is deterministically expired on the first iteration (the `>= 0L` comparison). The deadline branch is therefore the *only* `maybeLogTruncation` call site the call can reach, so an escaping exception can only have come from the sink invocation.

**Wrap boundary verified by diff.** `git diff -U0` shows exactly one executable line changed:

```
-        truncationLogger?.invoke(truncationLine(droppedChars, suppressed))
+        runCatching { truncationLogger?.invoke(truncationLine(droppedChars, suppressed)) }
```

The limiter read, the `compareAndSet` and the `getAndSet` all stay outside the wrap.

**Cross-test bleed prevented at two layers:** the test restores `truncationLogger` in a `finally`, and `RedactionTest` already carries an `@AfterEach resetTruncationSignal` that nulls the sink and resets the window. The full `com.six2dez.burp.aiagent.redact.*` suite runs in one JVM with no unrelated failure.

---

## Mutation results

| ID | Mutation | Outcome |
|---|---|---|
| **M1** | `bodyStage` single-pass loop: assign `result.text`, drop the `if (result.timedOut)` branch | **FAILS — 1 of 44**: `subWindowBodyFailsClosed`. `oversizeBodyFailsClosed` correctly did NOT fail (its input exceeds `MAX_REDACTION_BODY_CHARS`, so it never enters the mutated single-pass loop). |
| **M2** | `ADVERSARIAL_PROBES` reduced to the single original probe | **FAILS — exactly 3 of 15**: the three new rejection tests, and **no acceptance test**. Exactly as the acceptance criterion predicted. |
| **M3** | Remove the `isPatternSafe` filter from `App.kt` seeding | **NO TEST FAILS.** Full suite: 667 tests, 0 failures. Stated plainly below. |
| **M4** | Remove the `runCatching` from `maybeLogTruncation` | **FAILS — 1 of 45**: `truncationLoggerThatThrowsDoesNotAbortRedaction`, with the propagated exception. |
| **M5** | Move the wrap so it also encloses the CAS | **NO TEST FAILS.** Analysed and re-probed below. |
| **M5b** | *(added)* wrap + `onFailure { rollback lastTruncationLogMs / suppressedTruncations }` | **FAILS — 1 of 45**, and it is what tells the accounting assertion apart. |

### Verbatim failures

**M1** — `RedactionTest.kt:2050`:
```
org.opentest4j.AssertionFailedError: A sub-window body whose rule timed out must be dropped behind a
marker, never passed through ==> expected: <true> but was: <false>
```

**M4** — the propagated exception, not an assertion:
```
java.lang.IllegalStateException: Extension has been unloaded
  at Redaction.maybeLogTruncation(Redaction.kt:840)
  at Redaction.redactCookieSections(Redaction.kt:291)
  at Redaction.testRedactCookieSections(Redaction.kt:361)
```

**M5b**:
```
org.opentest4j.AssertionFailedError: The throwing call must still have OPENED the rate-limiter window:
a notice 5 s later must be suppressed ==> expected: <0> but was: <1>
```

### M3 — stated plainly: no test covers the seeding filter

Removing `App.initialize`'s `isPatternSafe` filter breaks **nothing**. `App.initialize` requires a live `MontoyaApi`, so no unit test reaches it; the only test that touches this area (`RedactionTest`'s seeding test) calls `Redaction.setCustomPatterns` directly, downstream of the filter.

**What the filter is guarded by instead:** nothing automated. It is source-asserted, like the shutdown step. What *is* mutation-verified (M2) is `isPatternSafe` itself — the function the filter calls — so the gate's *logic* is guarded and only the *call site* is not. Recommended follow-up: extract the seeding step into a pure, testable helper (`fun seedableCustomPatterns(persisted: List<String>): List<String>`) so the call site becomes assertable without a live Burp. **Not done here** — it is a refactor of `App.kt` structure, outside this plan's surface.

### M5 — why it does not fail, and what the accounting assertion actually pins

Widening the wrap to enclose the CAS does not break anything, and the reason is ordering: the `compareAndSet` and `getAndSet` complete **before** the sink is invoked, so an exception thrown afterwards cannot undo them. (`runCatching` is `inline`, so the non-local `return` on the suppression path still returns from `maybeLogTruncation`; the wrap is behaviourally transparent there too.) The wrap width is therefore a **readability and intent** boundary, not a behavioural one — which the in-source comment now says, rather than claiming a guard that does not exist.

To avoid leaving a vague claim, **M5b** was added to establish what the accounting assertion *is* pinning: it catches a fix shaped as "catch the throw and roll the limiter back so the next call can retry" — a plausible and tempting implementation that would make every subsequent notice emit unsuppressed. The assertion pins that the throwing call still **performed and kept** its window bookkeeping.

---

## Verification

| Check | Result |
|---|---|
| `./gradlew test ktlintCheck detekt` (JDK 21) | **BUILD SUCCESSFUL** |
| Test totals | **668 tests, 0 failures, 0 errors** (660 baseline + 8 new) |
| `git diff --stat a2523c0 HEAD -- detekt-baseline.xml` | **empty** — QUAL-07 held |
| `grep 'fun replaceAllSafe('` / `grep '\.replaceAllSafe('` | **0 hits** — no declaration, no call site |
| `SafeRegex.DEFAULT_TIMEOUT_MS` | **still `50L`** |
| `Defaults.MAX_REDACTION_BUDGET_MS`, `MAX_REDACTION_BODY_CHARS` | **untouched** |
| `redact/` free of `java.awt` / `javax.swing` / `AuditLogger` imports | **yes** (only comments mention them) |
| HKDF block, cookie bounds, `windowEnd`, `splitPoint`, `testKeyRules`, injected-budget seams | **untouched** |
| New Gradle dependency / package install | **none** |

### Monotonicity canaries — all green in the 0-failure run

`balancedModeRedactsUrlTokensInQueryStrings`, `bodyFormLeadingFieldRedacted`, `bodyJsonSecretKeysRedacted`, `offModePreservesBodies`, `balancedModeRedactsCustomAuthHeaders`, the two locked SC6 inversions (left alone), 21-08/21-15's cookie tests, `cookieSectionDecoyDoesNotShieldRealSection`, 21-09/21-13's windowing sweeps including `windowedScanRedactsJsonPairWhoseValueStraddlesTheCut`, 21-11's seam tests, 21-12's 120-name corpus, 21-16's de-raced CR-04 gate (`newlineFreeOversizeBodyIsScannedNotDestroyed`), 21-17's three-consumer equivalence guard, plus `oversizeBodyFailsClosed`, `subWindowBodyFailsClosed`, `cookieSectionDeadlineFailsClosed` and `truncationSignalIsRateLimited`.

---

## Deviations from Plan

### 1. [Rule 2 — missing critical coverage] A fourth `Redaction.kt` comment named the deleted symbol

**Found during:** Task 1. The plan enumerated three; there are four (`Redaction.kt:159`). Left uncorrected it would have been a comment describing a hazard via a symbol that no longer exists — the exact defect the round exists to close. Corrected, comment-only. Two `RedactionTest.kt` comments corrected for the same reason.

### 2. [Rule 2] The shipped probe corpus differs from the one WR-07 and the plan proposed

**Found during:** Task 2 screening. The proposed corpus (4 probes, all `'!'`-terminated) was measured **not to reject WR-07's own second example** `([a-z]+)+!`, and its mixed probe rejected nothing at all. Shipped instead: 3 classes x 2 terminators (6 probes), the original kept verbatim first. Evidence in §2. **The plan's stated `<behavior>` outcomes are all satisfied** — `(\d+)+@`, `([a-z]+)+!` and `(\w+\s?)+$` all return `false`, benign patterns all return `true`, zero-width patterns keep their distinct path.

### 3. [Rule 2] No whitespace probe shipped

**Found during:** Task 2 screening. 11 candidates screened, none uniquely caught by it. Shipping it would have added an undemonstrable guard and a probe's worth of worst-case EDT cost. Recorded in-source with all 11 candidates.

### 4. [Rule 1 — vacuity] WR-07's third candidate is already green today

`(\w+\s?)+$` is rejected by the *existing* single probe, so a test asserting it proves nothing about the widening. Shipped as an explicitly labelled regression pin, and a genuinely-RED fourth candidate (`([A-Z]+)+!`, uppercase) added so the evidence set is three real red-before-greens.

### 5. [Recorded, not fixed] M5 does not fail; M5b added

Documented in §Mutation results rather than papered over. The wrap width is an intent boundary, not a behavioural one, and the in-source comment says so.

### 6. Task 1 had no honest red-before-green

Task 1 is a **deletion**; there is no state in which a test is red because a function still exists. Stated plainly rather than manufacturing a test that passes on both sides. Mutation **M1** is the gate, and it confirms the deletion removed a footgun without removing a guard.

---

## Deferred Issues

| Item | Reason |
|---|---|
| `App.initialize` seeding filter has no automated guard (M3) | Needs `App.kt` restructuring into a pure helper — outside this plan's surface. Recommended shape recorded above. |
| `App.shutdown()` truncation-sink clearing has no automated guard | Needs a live `MontoyaApi`. Mitigated by `maybeLogTruncation`'s `runCatching` (T-21-65), which holds regardless. |
| EDT save-path cost multiplied 6x by the widened corpus | **T-21-67 — reported to Phase 23 / REL-05** per the plan's constraint. Flagged in-source and here. |
| `redactCookieSections` still has no per-match deadline | Pre-existing residual, unchanged by this plan; its comment now describes the hazard in terms of what ships. |

## Known Stubs

None.

## Threat Flags

None. No new network endpoint, auth path, file access pattern or schema change was introduced. All five threat-register mitigations (`T-21-62` … `T-21-67`) are implemented as dispositioned, with `T-21-66` and `T-21-64` recorded as source-asserted rather than test-asserted.
