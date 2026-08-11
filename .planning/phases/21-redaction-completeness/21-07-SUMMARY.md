---
phase: 21-redaction-completeness
plan: 07
subsystem: redact
tags: [PRIV-05, PRIV-06, SC4, SC5, D-01, D-02, D-04, D-05, D-08, ADR-14, T-21-11, T-21-24, T-21-25, T-21-26, T-21-27, kotlin, redaction, privacy, fail-closed, documentation]

# Dependency graph
requires:
  - phase: 21-redaction-completeness
    plan: 06
    provides: "the shipped windowed fail-closed body stage, and the measured SC4 mutation point"
  - phase: 21-redaction-completeness
    plan: 04
    provides: "SENSITIVE_KEY_EXPR and the accepted over-redaction list cited by ADR-14 and CONCERNS.md"
  - phase: 21-redaction-completeness
    plan: 02
    provides: "SafeRegex.replaceAllSafeReporting / SafeReplaceResult.timedOut, consumed by the single-pass fix"
provides:
  - "SC4 proven by measurement: the two oversize tests fail against a body stage carrying the reinstated fail-open guard and pass against the shipped one"
  - "Redaction.bodyStage single-pass path is now fail-CLOSED — a timed-out rule discards the partial result and falls through to windowedScan on the ORIGINAL input"
  - "RedactionTest.subWindowBodyFailsClosed — the sub-1 MB fail-closed gate, mutation-verified RED"
  - "DECISIONS.md ADR-14 — the redaction body stage never fails open (PRIV-06)"
  - ".planning/codebase/CONCERNS.md — the header-stage, window-boundary and plural-key residuals, and refreshed symbol-anchored Redaction.kt references"
affects: [redaction, privacy, phase-21-verification]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Fail-closed fallthrough by reuse: when a bounded fast path cannot complete, discard its partial result and re-enter the slow path from the ORIGINAL input, rather than duplicating the slow path's marker/drop machinery"
    - "Two-variant mutation transcript: run both the orchestrator-measured mutation and the plan-literal mutation, and record that both produce an identical failure set"

key-files:
  created: []
  modified:
    - src/main/kotlin/com/six2dez/burp/aiagent/redact/Redaction.kt
    - src/test/kotlin/com/six2dez/burp/aiagent/redact/RedactionTest.kt
    - DECISIONS.md
    - .planning/codebase/CONCERNS.md

key-decisions:
  - "The single-pass timeout falls through to windowedScan(input, rules) computed from the ORIGINAL input, not the partially-processed string — avoids double-marking and any partial-application ordering artifact, and reuses the fail-closed machinery instead of duplicating it"
  - "The worst-case ceiling is stated in-code: a single-pass timeout plus the windowed retry can cost up to DEFAULT_TIMEOUT_MS per rule ON TOP of MAX_REDACTION_BUDGET_MS, because windowedScan starts its own budget; bounded and documented rather than silently absorbed"
  - "The sub-1 MB gate fixture uses a bare pathological custom pattern with NO sensitive key name, so no built-in rule can reach it — the 21-05 vacuity lesson applied"

requirements-completed: [PRIV-05, PRIV-06]

# Metrics
duration: 41min
completed: 2026-08-11
---

# Phase 21 Plan 07: SC4 Proof, the Last Fail-Open, and the Phase Record Summary

**SC4 is proven by measurement rather than asserted — both oversize tests were driven RED against a body stage carrying the reinstated fail-open guard — and the one fail-open this phase had introduced (a timed-out rule on the sub-1 MB single-pass path silently passing content through) is now closed by falling through to the fail-closed windowed scan, so ADR-14's claim that the body stage never fails open is literally true with no size carve-out.**

## SC4 — Red-Before-Green Transcript

The gate SC4 actually demands is not "the oversize tests pass" — it is "the oversize tests **fail**
against the defect". `RedactionTest.oversizeBodySkippedSafely` previously asserted the PRIV-06
fail-open *as correct behaviour*, so a rewrite that happened to be green both before and after would
have tested nothing. This section is the measurement.

**Why a whole-file rollback was not used.** Restoring `Redaction.kt` from the pre-phase commit does
not compile against the rest of the phase: `scanner/PassiveAiScannerPrompts.kt` imports
`Redaction.COOKIE_SECTION_HEADER` (plan 21-05), `App.kt` assigns `Redaction.truncationLogger`
(plan 21-06), and `RedactionTest` calls `resetTruncationWindowForTest`. **A compile failure proves
nothing about the defect** — it is not a red test run. The defect was therefore reinstated
surgically, with a single-line mutation that restores exactly the pre-Phase-21 contract documented
in the old `Defaults.kt` comment: *"Bodies over this limit are skipped entirely."*

**Two mutation variants were run, not one.** The orchestrator's brief specifies the mutation plan
21-06 had already measured (replace the windowed call with a passthrough); the plan file's Task 1
text specifies an inserted size guard. They are behaviourally identical — above the window width the
original passes through, at or below it the single pass is untouched — so both were executed and both
are recorded, removing any ambiguity about which text was honoured.

### Mutation A — the 21-06-measured mutation (orchestrator brief)

Applied at `src/main/kotlin/com/six2dez/burp/aiagent/redact/Redaction.kt:476`, inside `bodyStage`,
one line changed and nothing else:

```diff
@@ -476 +476 @@ object Redaction {
-        return windowedScan(input, rules)
+        return input
```

### Mutation B — the plan-literal insertion (`21-07-PLAN.md` Task 1, step 2)

Applied to the restored file, inserted immediately after the `if (rules.isEmpty()) return input`
fast-path line (`:461`) and before the single-pass guard (`:468`), one line added and nothing else:

```diff
@@ -462,0 +463,2 @@ object Redaction {
+        if (input.length > Defaults.MAX_REDACTION_BODY_CHARS) return input
+
```

### Exit codes and the complete failure list

| Run | Command | Exit code | Result |
|---|---|---|---|
| Mutation A | `./gradlew test --tests "com.six2dez.burp.aiagent.redact.*"` | **1** | 90 tests completed, **2 failed** |
| Mutation B | same | **1** | 90 tests completed, **2 failed** |
| Restored | same | **0** | 90 tests completed, 0 failed |

`RedactionTest` alone, from the JUnit XML: `tests="23" failures="2" errors="0"` under both
mutations, `tests="23" failures="0" errors="0"` restored.

**The complete list of methods that failed in the mutated runs — identical for A and B:**

| # | Failed method | Assertion site | Verbatim failure message |
|---|---|---|---|
| 1 | `RedactionTest > oversizeBodySecretDoesNotSurvive()` | `RedactionTest.kt:848` | `org.opentest4j.AssertionFailedError: STRICT: a secret past the old size cap must not survive the body stage ==> expected: <false> but was: <true>` |
| 2 | `RedactionTest > oversizeBodyFailsClosed()` | `RedactionTest.kt:888` | `org.opentest4j.AssertionFailedError: A window that could not be fully scanned must be dropped behind a marker, not passed through ==> expected: <true> but was: <false>` |

**Assertion failures, not compile failures.** Both mutated runs executed `> Task :compileKotlin` and
`> Task :compileTestKotlin` successfully — the only compiler output was two pre-existing
`JsonNode.fields()` deprecation warnings in `OllamaBackend.kt` and `InjectionPointExtractor.kt` and
one pre-existing `Check for instance is always 'true'` in `DesignComponentsTest.kt`, none of them
in `redact/`. The failures are `org.opentest4j.AssertionFailedError` raised from the test bodies.
This distinction is T-21-25 and it is satisfied: the mutation is a one-line change that compiles by
construction, and the red run is a real red run.

### Mapping each failed method to the SC4 property it covers

| Failed method | SC4 property covered |
|---|---|
| `oversizeBodySecretDoesNotSurvive` | A secret past the old cut-off does not survive. The fixture is 10 001 lines of 99 `x` (1 000 100 chars) followed by `api_key=SC4-SECRET-VALUE-7B3E`, so the secret sits in the **second** window. It is reachable by `formBodyParamRegex`'s `(^\|[?&])` leading-field anchor alone — `urlTokenParamRegex`, which runs unbounded in the header stage and would otherwise mask the defect, requires a `?` or `&` before the key and cannot reach it; the value is not bearer- or basic-prefixed and does not begin `eyJ` |
| `oversizeBodyFailsClosed` | A pathological custom pattern on an oversized input yields a marker, never passthrough. `(a+)+$` over 600 repetitions of 2 000 `a` plus `!` must produce `REDACTION INCOMPLETE` or `REDACTION BUDGET EXCEEDED`, and the input must not survive verbatim |

### Exactly those two methods failed, and no others

The failure list contains **exactly** `oversizeBodySecretDoesNotSurvive` and `oversizeBodyFailsClosed`
— no third method failed in either mutated run. **This is the expected result.** The mutation changes
behaviour only on the branch taken when `input.length > Defaults.MAX_REDACTION_BODY_CHARS`; every
other case in `RedactionTest`, and every case in `SafeRegexTest`, `SecretShapesTest` and
`SecretTripwireTest` (the other classes matched by the `com.six2dez.burp.aiagent.redact.*` selector,
90 tests in total), uses an input at or below the 1 MB window width and therefore takes the
single-pass path, which the mutation does not touch. Had any other method failed, the mutation would
have reached further than intended and this transcript would say so.

### Scope of this gate: the body stage only

This experiment exercises the **body stage** and nothing else. SC1 (cookie-section values), SC2
(`COOKIE`-typed parameters) and SC3 (sensitive-key matching) are **not** exercised by it, because
SC1 and SC2 live in the `stripCookies` **header stage** and SC3 lives in the shared
`SENSITIVE_KEY_EXPR` consumed by three regexes across both stages. They are covered by their own
mutation-verified tests in plans 21-04 (`sensitiveKeyNamesRedacted`, `benignKeyNamesNotRedacted`,
`camelCaseKeysRedactedWithAcceptedOverRedactions`) and 21-05 (`cookieSectionValuesRedactedPerName`,
`cookieSectionDecoyDoesNotShieldRealSection`, `cookieTypedParametersRedacted`, plus three
end-to-end cases in `PassiveAiScannerPromptRedactionTest`). SC4's own wording scopes this gate to
the oversize behaviour, which is what was measured here.

### The experiment left no trace

| Check | Result |
|---|---|
| SHA-256 of `Redaction.kt` before mutation | `5d2a60f81b442397e84f44842ee8111706f739823cca69b35a8d319c7bfa5269` |
| SHA-256 of `Redaction.kt` after restore | `5d2a60f81b442397e84f44842ee8111706f739823cca69b35a8d319c7bfa5269` — identical |
| `diff` scratch copy vs. working file | no output (byte-identical), both before and after |
| `git status --porcelain -- src/` | prints nothing |
| `git log --oneline -1 -- .../redact/Redaction.kt` | `3ce2198 feat(21-06): add the rate-limited truncation signal and wire it in App.kt` — still a plan 21-06 commit; neither mutation was ever committed |

The shipped file was copied to the session scratchpad with `/bin/cp -f` and restored from that copy
with `/bin/cp -f` (this environment aliases `cp` and `rm` to their interactive forms, which silently
no-op in a non-interactive shell). No red commit was made, no branch state was saved aside, and no
blanket working-tree reset was used — the shared `refs/stash` ref is off-limits across worktrees and
`git clean` is destructive here. This is a single-task local verification step and must not be
generalised into any other workflow.

## The Added Task: Closing the Last Fail-Open (not in the plan file)

The plan file has three tasks. A fourth was added by the maintainer on 2026-08-11, ahead of Tasks 2
and 3 and after Task 1, and it is the only product-code change in this plan.

**What was wrong.** Plan 21-06 shipped the sub-1 MB single-pass path assigning
`SafeRegex.replaceAllSafe(...)`, which returns its input **unchanged** on timeout — byte-identical to
"the pattern matched nothing". A built-in body rule that overran the 50 ms deadline was therefore
silently skipped and its unredacted content passed straight through. That is fail-**OPEN**, and
21-06 recorded it honestly as a residual rather than hiding it.

**Why it was fixed rather than recorded.** Unlike the unbounded header stage — which is pre-existing
and explicitly outside D-01/D-02's scope — this path is a fail-open that **this phase introduced**,
in the phase whose stated goal is that the body stage never fails open. Recording it would have left
ADR-14's headline claim carrying a silent "except below 1 MB" carve-out.

**What shipped** (`Redaction.kt:495-503`):

```kotlin
if (input.length <= Defaults.MAX_REDACTION_BODY_CHARS) {
    var out = input
    for ((pattern, replacement) in rules) {
        val result = SafeRegex.replaceAllSafeReporting(out, pattern, replacement)
        if (result.timedOut) return windowedScan(input, rules)
        out = result.text
    }
    return out
}
```

The fallthrough is computed from the **original** `input`, not from the partially-processed `out`.
That avoids double-marking and any partial-application ordering artifact, and it reuses
`windowedScan`'s existing fail-closed machinery (halve-and-retry to `WINDOW_RETRY_MAX_DEPTH`, then
drop behind a visible marker) instead of duplicating it.

### Invariants held

| Invariant | How it is held | Evidence |
|---|---|---|
| `rules.isEmpty()` still returns `input` **before** any of this | The OFF fast path at `:488` is above the size branch and was not touched | `offModePreservesBodies`, `offModePreservesAllTokens` and `redactScanMetadata_offModeIsByteIdentical` all green, untouched |
| The non-timeout path is byte-identical to before | `replaceAllSafe` was already a one-line delegate to `replaceAllSafeReporting(...).text` (plan 21-02), so when `timedOut` is false the loop computes exactly the same string as before | All 23 pre-existing `RedactionTest` cases green, unmodified; the single-pass mutation run below failed **only** the new test, proving nothing else observed a change |
| `MAX_REDACTION_BUDGET_MS` still bounds the fallthrough | `windowedScan` starts its own budget clock | Stated explicitly in a `BUDGET CEILING` comment block — see below |
| detekt `ReturnCount` (max 2) | `bodyStage` already carries a declaration-level `@Suppress("ReturnCount")` from plan 21-06, with in-repo precedent at `ui/SettingsPanelInit.kt:29`, `ui/SettingsPanelMcpTabs.kt:148`, `scanner/PassiveAiScannerAnalysis.kt:169`. The new return path needs **no new suppression** | `./gradlew detekt` exit 0; `git diff --stat -- detekt-baseline.xml` **empty** (QUAL-07) |

### The budget ceiling, stated rather than assumed

The brief required a comment stating whether a single-pass timeout plus a windowed retry can exceed
the intended ceiling, and bounding it if so. **It can, and it is bounded.** `windowedScan` starts a
fresh `Defaults.MAX_REDACTION_BUDGET_MS` clock, so the composition is not capped at 2 s. The excess
is bounded by construction: the single pass gives up after at most
`rules.size × SafeRegex.DEFAULT_TIMEOUT_MS`, so the worst case is
`rules.size × DEFAULT_TIMEOUT_MS + MAX_REDACTION_BUDGET_MS` — finite, and only reachable when a rule
is already pathological. With the two built-ins plus ten custom patterns that is ~2.6 s.

Threading one shared deadline into `windowedScan` was **rejected**: the fallthrough would then
routinely arrive with the budget already spent and drop the **entire** body behind a single marker,
which is fail-closed but destroys all analytic context (T-21-06) on an input small enough to scan
properly. The reasoning is recorded in the `BUDGET CEILING` comment block so a future contributor
does not "tidy" it into a shared budget. Measured cost of the new test's full fallthrough path:
**0.681 s**, comfortably inside the stated bound.

### The test, and the proof that the test works

`RedactionTest.subWindowBodyFailsClosed` (`RedactionTest.kt:922`) pushes `(a+)+$` in through
`setCustomPatterns` and asserts that an **800 800-character** body — strictly below the 1 MB window
width — yields `REDACTION INCOMPLETE` or `REDACTION BUDGET EXCEEDED` and never survives verbatim.

**Fixture strength, per the 21-05 lesson.** Plan 21-05 found *both* its SC2 tests were vacuous as
specified: they passed against a mutation that unwired the very rule they existed to guard, because
the fixture names (`JSESSIONID`, `remember_me`) were also caught by `SENSITIVE_KEY_EXPR`. This
fixture is reachable **only** by the path under test:

- the body is 800 800 characters, strictly **below** `MAX_REDACTION_BODY_CHARS`, and the test asserts
  that explicitly — so a future change to the constant cannot silently turn this into a duplicate of
  `oversizeBodyFailsClosed`;
- the content is nothing but `a` runs and `!`. No `=`, no sensitive key name, no cookie section, no
  bearer/basic prefix, no `eyJ`, no `Host:` header. **Not one built-in rule can match it in either
  stage**, so the marker asserted can only have been produced by the fail-closed fallthrough.

**Verified by mutation, not by inspection:**

| Mutation | Test | Result | What it proves |
|---|---|---|---|
| The single-pass loop reverted to `out = SafeRegex.replaceAllSafe(out, pattern, replacement)` — the pre-fix fail-open shape | `subWindowBodyFailsClosed` | **FAILED** — `A sub-window body whose rule timed out must be dropped behind a marker, never passed through ==> expected: <true> but was: <false>` | The new test detects the defect it exists to guard. Exit code 1, `24 tests completed, 1 failed` |
| same | the other 23 `RedactionTest` cases | **all passed** | The fix changes nothing on the non-timeout path — the byte-identity invariant, measured rather than asserted |

The mutated file was restored from a scratch copy (`/bin/cp -f`), verified by SHA-256
(`526d6759…` before and after), because at that point `Redaction.kt` carried uncommitted work and a
`git checkout HEAD -- <path>` would have discarded the fix itself.

## ADR-14 and the residual record

**Task 2 — `DECISIONS.md` ADR-14**, appended after ADR-13 in ADR-13's exact shape: a
`## ADR-N: <sentence-case claim>` heading with the requirement id in parentheses (the ADR-9 / ADR-12
convention), then exactly three inline bold labels — `**Context.**` and `**Decision.**` as single
prose paragraphs that are never bulleted, `**Consequences.**` as a bullet list whose last bullet
begins `Residual:`, with the rejected alternatives named inside the Decision paragraph.

**The title is scoped, deliberately.** `## ADR-14: The redaction body stage never fails open
(PRIV-06)`. Per D-08 REFINED the unqualified form would be **false the day it is written**, because
the eight header-stage rules still run unbounded. The unqualified phrase appears **nowhere** in
`DECISIONS.md` (`grep -c` returns 0).

**With the added task landed, the headline claim is literally true with no size carve-out**, and
ADR-14 says so: *"This holds at every input size, with no carve-out."* A dedicated consequence bullet
records that the single-pass fail-open 21-06 reported was **fixed in this phase, not accepted** — so
a future reader does not go looking for a residual that no longer exists.

**Task 3 — `.planning/codebase/CONCERNS.md`.** The regex-coverage entry's stale references are gone
and the new residual entry is in place. Both stale range references were removed (`Redaction.kt:55-79`
in `**Issue:**` and `Redaction.kt:56-79` in `**Files:**`), as were all three inline refs
(`jwtRegex (line 71)`, `urlTokenParamRegex (line 74-78)`, `authHeaderRegex (line 56-64)`), replaced
with symbol-anchored references carrying current Phase 21 line numbers and an explicit note that the
symbol names are the durable anchors.

## Phase 21 residuals

The three gaps `CONCERNS.md` now records, so phase verification can cite them without re-reading two
files. **None is claimed fixed.**

| # | Residual | Where recorded | Why deferred |
|---|---|---|---|
| 1 | The **eight header-stage rules** (`authHeaderRegex`, `bearerRegex`, `basicAuthRegex`, `jwtRegex`, `urlTokenParamRegex`, `cookieHeaderRegex`, `setCookieHeaderRegex`, `hostHeaderRegex`) plus the two Phase 21 cookie rules run **unbounded on the full input** — no per-pattern deadline, outside the total budget. ~25-51 ms per rule at 10 MB | `CONCERNS.md` new entry; ADR-14 `Residual:` bullet | Pre-existing, not a Phase 21 regression; D-01/D-02 scoped themselves to the body stage. This is the reason ADR-14 says "body stage" |
| 2 | A **user custom pattern whose match spans a window boundary** can be missed | `CONCERNS.md` new entry; ADR-14 `Residual:` bullet; inline at `Redaction.kt` `windowEnd` | No principled bound exists on a user regex's match length, so no window size or overlap constant closes it. Built-ins are unaffected |
| 3 | **Plural key forms** (`codes`, `tokens`, `keys`) do not redact | `CONCERNS.md` regex-coverage entry; ADR-14 `Residual:` bullet | One-character recipe (optional trailing `s`), but a second widening axis plus six more tests, and SC3 does not require it. Accepted risk: a field named `codes` carrying MFA backup codes |

**Closed by this plan, and therefore NOT a residual:** the single-pass fail-open below 1 MB that
21-06 recorded as its "Known Residual". It was fixed, tested and mutation-verified above.

**Accepted costs, recorded rather than discovered in the field** — ten over-redactions from plan
21-04, every one in the fail-safe direction: seven under the separator rule (`token_bucket_size`,
`session_timeout_seconds`, `auth_provider`, `key_size`, `code_version`, `secret_santa`,
`password_hint_enabled`) and three under the camelCase rule (`codeName`, `keyName`, `tokenCount`),
the latter asserted **as accepted** in `RedactionTest` with a one-line revert point named in source.
Only `auth_provider` has real analytic value.

## Task Commits

| # | Task | Commit | Type |
|---|---|---|---|
| 1 | Plan Task 1 — the SC4 red-before-green experiment and its transcript | `abcea9a` | docs |
| 2 | **Added task** — close the single-pass fail-open, with its mutation-verified test | `1da98d1` | fix |
| 3 | Plan Task 2 — ADR-14 in `DECISIONS.md` | `e08b970` | docs |
| 4 | Plan Task 3 — `CONCERNS.md` residuals and stale-reference refresh | `8c328f1` | docs |

Executed in exactly that order. The order was load-bearing: Task 1 had to run against the code as it
stood at the base, before the added task moved the mutation point, and ADR-14 had to be written after
the added task landed so it could claim the body stage never fails open with no carve-out.

## Files Created/Modified

- `src/main/kotlin/com/six2dez/burp/aiagent/redact/Redaction.kt` — the single-pass fail-closed
  branch (`:495-503`), its `BUDGET CEILING` rationale block, and the `bodyStage` header comment
  updated from "every rule **above the window width**" to "fail CLOSED, at **every** size", which
  was stale the moment the fix landed. No new import. HKDF block untouched.
- `src/test/kotlin/com/six2dez/burp/aiagent/redact/RedactionTest.kt` — `subWindowBodyFailsClosed`
  (`:922`) added after `oversizeBodyFailsClosed`. No existing test modified. 23 → 24 cases.
- `DECISIONS.md` — ADR-14 appended. ADR-1 through ADR-13 untouched.
- `.planning/codebase/CONCERNS.md` — regex-coverage entry refreshed and corrected; new
  `### Redaction body-stage bounds and the unbounded header stage` entry. No other entry modified.
- `.planning/phases/21-redaction-completeness/21-07-SUMMARY.md` — this file.

## Decisions Made

- **The fallthrough restarts from the original input.** Continuing from the partially-processed `out`
  would risk double-marking (a rule already applied being re-applied inside `windowedScan`) and an
  ordering artifact where the result depends on which rule happened to time out. Restarting is also
  the only version whose correctness argument is one sentence long.
- **Reuse `windowedScan` instead of duplicating fail-closed logic.** A local "drop behind a marker"
  branch on the single-pass path would have been a second implementation of the same security rule,
  and second implementations drift. `windowedScan` on a sub-1 MB input produces exactly one window
  by construction (`windowEnd` returns `s.length` when the width covers the remainder), so the
  fallthrough is a single bounded window with halve-and-retry available.
- **No new detekt suppression and no baseline entry.** `bodyStage`'s existing declaration-level
  `@Suppress("ReturnCount")` already covers the added return, so QUAL-07 is respected without
  touching `detekt-baseline.xml`.
- **Both mutation variants were run for SC4**, not just one. The orchestrator brief and the plan file
  specify different-but-equivalent one-line mutations; running both removes any ambiguity about which
  text was honoured and costs one extra 3-second test run.
- **The stale `bodyStage` header comment was updated as part of the fix**, not left for later. A
  comment on a security control that says "every rule above the window width runs through
  `replaceAllSafeReporting`" would have been false the moment the single-pass path started doing the
  same thing — and stale comments on redaction internals are precisely what this plan's `CONCERNS.md`
  task exists to clean up.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 2 - Missing critical functionality] The single-pass path was fail-open**

- **Found during:** the added task (specified by the maintainer, executed as task 2 of 4)
- **Issue:** `bodyStage`'s sub-1 MB branch assigned `SafeRegex.replaceAllSafe`, whose return value is
  the unchanged input on timeout — indistinguishable from "no matches". A body rule overrunning
  50 ms was silently skipped and unredacted content reached the backend.
- **Fix:** branch on `replaceAllSafeReporting(...).timedOut`, discard the partial result, re-scan the
  original input through the fail-closed windowed path. Budget ceiling stated in-code.
- **Files modified:** `redact/Redaction.kt`, `redact/RedactionTest.kt`
- **Commit:** `1da98d1`

**2. [Rule 1 - Bug] The `bodyStage` header comment became false**

- **Found during:** the added task
- **Issue:** the comment asserted that `replaceAllSafeReporting` is used "above the window width",
  which stopped being the whole truth once the single-pass path used it too. A false comment on a
  security control is a real defect — it is what produces the stale-reference problem Task 3 exists
  to fix.
- **Fix:** rewritten to state that fail-closed holds at every size, and that there is no size at
  which a body rule can time out and its unscanned bytes still be emitted.
- **Files modified:** `redact/Redaction.kt`
- **Commit:** `1da98d1`

### Documentation defects in the plan (recorded, not worked around)

**3. `21-06-SUMMARY.md`'s mutation-point line number had drifted by five lines.** It records
`return windowedScan(input, rules)` at `Redaction.kt:481`; the actual position at this plan's base is
`:476`. The `bodyStage` single-pass guard it also records at `:468` **is** correct. The statement was
verified by `grep` rather than trusted, the mutation was applied to the correct line, and the
transcript quotes the real diff hunk header (`@@ -476 +476 @@`). Recorded because 21-07's own
acceptance criteria depend on that number.

**4. The plan file's Task 1 mutation and the orchestrator brief's mutation are different lines.**
The plan specifies inserting a size guard; the brief specifies replacing the windowed call. Both are
one-line, both compile by construction, and both reinstate the identical pre-Phase-21 contract.
Rather than pick one and leave the other unaddressed, **both were executed** and both are recorded
with their diff hunks and results. They produced the same two failures.

---

**Total deviations:** 2 auto-fixed (1 missing critical functionality, 1 bug), 2 plan documentation
defects recorded. No architectural decision arose, no package was installed, no new Gradle dependency
was added. Nothing on the do-not-touch list was modified: the HKDF block, host anonymization,
`App.kt`'s `setCustomPatterns` seeding and `ui/SettingsPanelSettingsIO.kt` are all untouched.

## Issues Encountered

- **The worktree spawned at `03f17a7`, an ancestor of the assigned base `0178d67`.** Corrected with
  `git reset --hard 0178d67` **after** the branch-namespace assertion passed, per the startup
  protocol. No work was lost — the reset ran before any edit. This is the **fourth** consecutive plan
  in this phase to hit it (21-02, 21-05, 21-06, 21-07); it is a worktree-spawn characteristic, not a
  per-plan problem, and it is worth fixing in the orchestrator rather than re-diagnosing a fifth time.
- **Restoring a mutated file needed two different mechanisms.** For Task 1, `Redaction.kt` was
  committed and clean, so a scratch copy plus `/bin/cp -f` was used (and `git checkout HEAD -- <path>`
  would also have worked). For the added task's mutation, `Redaction.kt` carried the **uncommitted
  fix**, so a single-file checkout would have destroyed the work under test; only the scratch-copy
  route was safe. Both restores were verified by SHA-256, not by eye.
- **The grep-criterion hazard that has bitten every executor in this phase did not bite here**, but
  only because it was checked. This plan's own summary is grep-asserted to contain zero occurrences
  of a certain two-word `git` command, so the prohibition had to be described without naming it. Two
  of ADR-14's criteria were also verified against `git show HEAD:DECISIONS.md` (13 → 14 for each of
  the three bold labels, 1 → 2 for `Residual:`) rather than against a raw count, since a bare count
  proves nothing about a delta.
- **A stray closing XML-ish tag** was left at the end of this summary by the initial file write (a
  tool-call artifact, not content) and was removed before the final commit. The same class of
  artifact is present at the end of `21-06-SUMMARY.md`, on its last two lines, and was left alone —
  it is outside this plan's scope and belongs to another plan's committed output. Worth a sweep at
  phase close.

## Threat Model Verification

| Threat ID | Disposition | Status |
|---|---|---|
| T-21-11 (the transient mutation is committed or left in place) | mitigate | **Satisfied** — shipped file copied to scratch first and restored from that copy; SHA-256 identical before and after; `git status --porcelain -- src/` prints nothing; `git log -1` for the file still points at 21-06's `3ce2198`; this plan ran alone in wave 4 so no concurrent plan could observe the mutated file |
| T-21-24 (the transcript is hand-waved) | mitigate | **Satisfied** — both exit codes, both mutation diff hunks, the JUnit XML counts and the two verbatim assertion messages are recorded, plus a prose statement that no third method failed and why |
| T-21-25 (a compile failure mistaken for a red run) | mitigate | **Satisfied** — both mutated runs executed `compileKotlin` and `compileTestKotlin` successfully; the failures are `org.opentest4j.AssertionFailedError` from the test bodies, and the only compiler output was three pre-existing warnings, none in `redact/` |
| T-21-26 (ADR-14 overclaims) | mitigate | **Satisfied** — heading grep-asserted to contain `body stage`; the unqualified phrase appears 0 times in `DECISIONS.md`; the `Residual:` bullet names the header stage, the window boundary and the plural forms with measurements, and cross-references `CONCERNS.md` |
| T-21-27 (stale `file:line` refs send a contributor to the wrong code) | mitigate | **Satisfied** — both stale ranges and all three inline `(line NN)` refs grep-asserted removed and replaced with symbol-anchored references; the entry states that symbols are the durable anchor |
| T-21-02 / T-21-03 (fail-open in the body stage) | mitigate | **Strengthened** — the last remaining fail-open, on the sub-1 MB path, is closed and mutation-verified. There is now no input size at which a timed-out body rule emits unscanned bytes |
| T-21-06 (fail-closed dropping removes legitimate context) | accept | **Satisfied as accepted** — a shared budget for the fallthrough was explicitly rejected precisely because it would drop an entire scannable body; halve-and-retry still applies |
| T-21-SC (package-install supply chain) | accept | **Satisfied** — zero packages installed, no new Gradle dependency, no new import |

## Known Stubs

None. The one code path added is wired into live `Redaction.apply` calls and is exercised by a direct
unit test that was proven to fail without it. No placeholder, empty-collection, mock-data or TODO
path was introduced.

## Threat Flags

None. This plan introduces no new network endpoint, no auth path, no file access pattern and no
schema change at a trust boundary. `redact/` gained no project-internal dependency and remains free
of any UI toolkit import — `grep -c 'AuditLogger\|java.awt\|javax.swing'` on `Redaction.kt` returns
**0**. The change narrows what leaves the redaction trust boundary; it does not widen any surface.

## Verification Results

All Gradle commands run with the mandatory JDK 21 prefix
(`JAVA_HOME=$(/usr/libexec/java_home -v 21)`).

| Check | Result |
|---|---|
| `./gradlew test ktlintCheck detekt -q` | **exit 0** — final state, and after the added task |
| Suite totals | **628 tests, 0 failures, 0 errors** (627 at base + `subWindowBodyFailsClosed`) |
| `RedactionTest` | **24 tests**, 0 failures (23 at base + 1) |
| `git diff --stat -- detekt-baseline.xml` | **empty** — baseline did not grow (QUAL-07) |
| `git status --porcelain -- src/` | prints nothing |
| `git diff --stat -- src/` | empty — everything committed |
| `git diff -U0 -- Redaction.kt \| grep -c 'hkdf\|HKDF_INFO\|HKDF_OKM_LEN\|anonymizeHost'` | **0** — SC6 boundary honoured, `-U0` used deliberately (a context line carries `anonymizeHosts` otherwise) |
| `grep -c 'AuditLogger\|java.awt\|javax.swing'` on `Redaction.kt` | **0** |

**Task 1 acceptance criteria:** transcript section present with both mutation lines quoted verbatim,
non-zero exit for the mutated runs and exit 0 restored; failure list contains
`oversizeBodySecretDoesNotSurvive` and `oversizeBodyFailsClosed`; prose states exactly those two and
names why every other case is unaffected; prose states the gate covers the body stage only and names
where SC1/SC2/SC3 are covered instead; `git status --porcelain -- src/` empty;
`git log --oneline -1 -- Redaction.kt` = `3ce2198` (a 21-06 commit); the forbidden two-word command
appears **0** times in this summary.

**Task 2 acceptance criteria:** `^## ADR-14: The redaction body stage never fails open (PRIV-06)$`
= **1**; unqualified claim = **0**; `^**Context.**` / `^**Decision.**` / `^**Consequences.**` each
**13 → 14** (+1, verified against `git show HEAD:DECISIONS.md`); `^- Residual:` **1 → 2** (+1) and
the new one names the header stage and the window boundary; `PrivacyMode.OFF` present;
`MAX_REDACTION_BUDGET_MS\|Defaults.MAX_REDACTION_BODY_CHARS` = **2** (≥ 1);
`git diff DECISIONS.md | grep -c '^-'` = **1** (the diff header only — ADR-1 through ADR-13
untouched).

**Task 3 acceptance criteria:** `Redaction\.kt:5[0-9]-79` = **0**;
`\(line 71\)|\(line 74-78\)|\(line 56-64\)` = **0**;
`### Redaction body-stage bounds and the unbounded header stage` = **1**; `SENSITIVE_KEY_EXPR` = **2**
(≥ 1); `fixed allowlist of parameter names` = **0**; `codes\|plural` = **1** (≥ 1);
`Protocol for tightening` = **1** (unchanged); `window boundary` = **1** (≥ 1); `header stage` = **5**;
the new entry uses `**Issue:**`, `**Files:**`, `**Fix approach:**`, `**Impact:**` — the label set six
other entries already use. `git diff -U0` shows exactly **3** removed lines, all from the
regex-coverage entry, so no other entry was modified.

**Monotonicity canaries — all executed and green** in the final run: `balancedModeRedactsUrlTokens`
`InQueryStrings` (`name=alice` survives), `bodyFormLeadingFieldRedacted` (`user=bob` survives),
`bodyJsonSecretKeysRedacted` (`"name":"alice"` survives), `bodyJsonUnquotedSecretValuesRedacted`,
`offModePreservesBodies` (byte-identity under OFF), `offModePreservesAllTokens`,
`customPatternRedactsInStrictAndBalanced` and `oversizeBodyFailsClosed` (the two SC6 inversions from
21-06), `hostAnonymizationFormatIsStable`, `hostAnonymizationIsStablePerSalt`, and SC6's named vector
`hkdfMatchesRfc5869Vector`.

## User Setup Required

None — no external service configuration required. No user-visible behaviour changes except in the
pathological case: a body under 1 MB whose redaction rule overruns its deadline now yields a
`[REDACTION INCOMPLETE ...]` or `[REDACTION BUDGET EXCEEDED ...]` marker and a rate-limited
`[Redaction] ...` Output-tab line, where it previously passed through silently unredacted.

## Next Phase Readiness

- **Phase 21 is complete from this plan's side.** All six success criteria have owners: SC1/SC2 in
  21-05, SC3 in 21-04, SC4 proven here with the transcript above, SC5 in 21-06 plus ADR-14's OFF
  half, SC6 verified green throughout.
- **`.planning/STATE.md`, `.planning/ROADMAP.md` and `.planning/REQUIREMENTS.md` were deliberately
  NOT modified** — worktree execution, the orchestrator owns those writes. PRIV-05 and PRIV-06 are
  phase-wide checkboxes claimed by five of the seven plans; the `requirements-completed` frontmatter
  above records what this plan's `requirements` field claims, per the summary template, and is not an
  instruction to check them off. All six executors in this phase reached the same conclusion
  independently.
- **For the phase verifier:** the three residuals are tabulated under "Phase 21 residuals" above with
  their recording locations, so they can be cited without re-reading `CONCERNS.md` and `DECISIONS.md`.
- **Carry into a future phase (not this one):** bounding the header stage is the single largest
  remaining gap in the redaction pipeline and now has a written fix approach in `CONCERNS.md` —
  apply each header rule through `SafeRegex.replaceAllSafeReporting` under
  `min(DEFAULT_TIMEOUT_MS, remaining budget)` and fail closed, exactly as the body stage does.
- **Worth fixing in the orchestrator:** the worktree-spawns-at-an-ancestor issue has now cost four
  plans in this phase a recovery step each.

## Self-Check: PASSED

Files claimed, verified present on disk with their claimed content:

- `.planning/phases/21-redaction-completeness/21-07-SUMMARY.md` — FOUND, contains
  `## SC4 — Red-Before-Green Transcript`
- `src/main/kotlin/com/six2dez/burp/aiagent/redact/Redaction.kt` — FOUND, contains
  `replaceAllSafeReporting(out, pattern, replacement)` and
  `if (result.timedOut) return windowedScan(input, rules)`
- `src/test/kotlin/com/six2dez/burp/aiagent/redact/RedactionTest.kt` — FOUND, contains
  `fun subWindowBodyFailsClosed`
- `DECISIONS.md` — FOUND, contains `## ADR-14`
- `.planning/codebase/CONCERNS.md` — FOUND, contains
  `### Redaction body-stage bounds and the unbounded header stage`

Commits claimed, all present on `worktree-agent-a8d61a637975b8cd7` above the base `0178d67`:
`abcea9a`, `1da98d1`, `e08b970`, `8c328f1`.

Working tree clean, no untracked files, and `git diff --diff-filter=D` empty for every commit — no
file deletions in any of them.

---
*Phase: 21-redaction-completeness*
*Completed: 2026-08-11*
