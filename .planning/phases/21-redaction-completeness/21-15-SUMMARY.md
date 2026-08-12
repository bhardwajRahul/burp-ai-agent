---
phase: 21-redaction-completeness
plan: 15
subsystem: redact
tags: [PRIV-05, PRIV-06, cookies, W-01, W-02, W-03, W-05, W-07, IN-04]
requires: [21-08, 21-10, 21-13]
provides:
  - "Redaction.MAX_COOKIE_SECTION_LINES as the internal authority for the cookie section bound"
  - "COOKIES_MAX_COUNT derived from and clamped to the redactor's bound"
  - "a direct unit test on Redaction.sanitizeCookieSectionEntries, including its CR/LF limb"
  - "an expired cookie budget that preserves a prompt with no section left in it"
affects: [scanner, mcp]
tech-stack:
  added: []
  patterns: ["compile-time clamp plus a drift-detecting assertion, in place of two restated constants"]
key-files:
  created: []
  modified:
    - src/main/kotlin/com/six2dez/burp/aiagent/redact/Redaction.kt
    - src/main/kotlin/com/six2dez/burp/aiagent/scanner/PassiveAiScannerAnalysis.kt
    - src/main/kotlin/com/six2dez/burp/aiagent/scanner/PassiveAiScannerPrompts.kt
    - src/test/kotlin/com/six2dez/burp/aiagent/redact/RedactionTest.kt
    - src/test/kotlin/com/six2dez/burp/aiagent/scanner/PassiveAiScannerPromptRedactionTest.kt
decisions:
  - "W-02 dispositioned ACCEPT on two measured grounds, not on the plan's say-so"
  - "the cookie section replace stays outside SafeRegex, recorded as a residual with its reason"
metrics:
  duration: ~2h
  completed: 2026-08-12
---

# Phase 21 Plan 15: Cookie Bound Coupling and Sanitizer Guards Summary

The redactor's cookie-section bound is now the authority the emitter is clamped to, the sanitizer's
CR/LF limb has the only test in the suite that can reach it, and an expired cookie budget no longer
destroys a prompt that has no cookie section left in it.

## What Shipped

| Finding | Disposition | What closed it |
|---|---|---|
| **W-01** | mitigate | `MAX_COOKIE_SECTION_LINES` is `internal` and authoritative; `COOKIES_MAX_COUNT` is `COOKIES_MAX_COUNT_INTENDED` clamped to it; two named tests go red on drift |
| **W-02** | **accept** | both alternatives measured and rejected; residual note states both directions; blast radius pinned |
| **W-03** | mitigate | `sanitizeCookieSectionEntriesNeutralisesEveryFramingPrimitive`, the only test that reaches the CR/LF limb |
| **W-05** | mitigate | `indexOf` + `break` hoisted above the deadline check; constant's comment now claims only what it bounds |
| **W-07 (KDoc half)** | mitigate | KDoc names both shipped call sites and the corrected guard method name |
| **IN-04** | recorded | double sanitize documented at the emitter site with a different named guard per call |

## 1. Measured leak boundary, taken BEFORE any change

Driven through the real `Redaction.apply` under `PrivacyMode.STRICT` at the pristine base
(`b65622c`), 20 entries `ck0`…`ck19` with opaque values:

```
=== M0: 20-entry section, trailing newline ===
leaked indices: ck16 ck17 ck18 ck19
first leaking index: 16
```

**This matches the reviewer's measurement exactly** (`21-REVIEW-2.md` §W-01: `ck16 ck17 ck18 ck19`).
The walk's line accounting is what both of us thought it was.

Three further measurements taken in the same pre-change run, none of which the plan asked for and two
of which turned out to be load-bearing:

| Fixture | Leaked |
|---|---|
| 20 entries, section followed by `=== PARAMETERS ===` | `ck16 ck17 ck18 ck19` — identical, so end-of-text is not what bounds it |
| exactly 16 entries | **none** |
| **12 entries with one blank line between each** | **`ck8 ck9 ck10 ck11`** |

The last row is the reason `cookieEmitterBoundStaysWithinTheRedactorBound` asserts **2x** headroom
rather than 1x. `cookieSectionEnd` skips blank lines *without terminating* — that is the CR-01 fix —
so a blank line consumes one of the budgeted lines and the bound is a LINE count, not an entry count.
At the shipped 16-line bound only 8 blank-interleaved entries fit. A 1x assertion
(`COOKIES_MAX_COUNT <= MAX_COOKIE_SECTION_LINES`) would have been satisfied by a bound of 6 that
leaks from the third entry on blank-interleaved input.

## 2. Verbatim RED for `cookieSectionBudgetExpiryWithNoSectionRemainingPreservesTheText`

Observed at the committed test commit `edcd999`, before the implementation:

```
org.opentest4j.AssertionFailedError: W-05: an expired budget with NO cookie section pending must
return the text BYTE-IDENTICALLY. Dropping it behind a marker destroys up to 2 MiB of prompt for a
hazard that is not present. ==> expected: <=== RESPONSE BODY ===
<html><body>
debug=verbose_stack_trace_here
sql=SELECT * FROM users WHERE id=1
internal=10.0.0.5
version=4.2.1-rc3
</body></html>> but was: <[REDACTION INCOMPLETE - 151 CHARS DROPPED AND NOT SENT]>
```

at `RedactionTest.cookieSectionBudgetExpiryWithNoSectionRemainingPreservesTheText(RedactionTest.kt:1382)`.
An assertion failure, not a compile error. **The entire 151-char text was replaced by one drop
marker** — the defect exactly as W-05 describes it. `git diff --stat HEAD~1 -- src/main` at that
commit was empty: the RED commit touches no production code.

The same RED was reproduced a second time as a control, at the same commit, while the implementation
was held out-of-tree during the flake investigation in §6.

`sanitizeCookieSectionEntriesNeutralisesEveryFramingPrimitive` was **GREEN in that same run**, as its
own comment says it would be. It is a latent-trap closure and its gate is M3, not a RED.

## 3. Mutation results

Every mutation was applied **on top of the committed implementation** and reverted with
`git checkout -- <path>`. After each revert `git status --short` and `git diff HEAD --stat` were both
empty, and `grep -c MUTATION` over the touched file returned 0.

| # | Mutation | Failure set | Verbatim assertion |
|---|---|---|---|
| **M1** | `MAX_COOKIE_SECTION_LINES` → 8 | `cookieEmitterBoundStaysWithinTheRedactorBound` (`:1113`) — **plus the known flake, nothing else** | `PRIV-05: MAX_COOKIE_SECTION_LINES counts LINES, not entries … The redactor's bound must therefore stay at or above 12 — which is also why shrinking it to COOKIES_MAX_COUNT + 2, the alternative 21-REVIEW-2 W-02 proposes, was rejected. ==> expected: <true> but was: <false>` |
| **M1b** | walk off-by-one: `lines < MAX_COOKIE_SECTION_LINES - 1` | `everyEntryOfAMaximalCookieSectionIsRedacted` | `STRICT: cookie entry ck15 sits inside the 16-line section bound and must be redacted — this index is the FIRST entry the span stops covering ==> expected: <false> but was: <true>` |
| **M2** | emitter literal `COOKIES_MAX_COUNT_INTENDED` → 20 | `cookieEmitterBoundStaysWithinTheRedactorBound` (`:1106`) **and** `blankCookieElementsDoNotConsumeDisplaySlots` (`:393`) | `PRIV-05: PassiveAiScannerAnalysis intends to emit 20 cookie entries but Redaction redacts a cookie section only 16 lines deep. Every entry past that bound reaches the AI backend UNREDACTED. ==> expected: <true> but was: <false>` |
| **M3** | CR/LF limb deleted from `sanitizeCookieSectionEntries` | `sanitizeCookieSectionEntriesNeutralisesEveryFramingPrimitive` — **and nothing else** | `All three limbs, asserted as one exact list … ==> expected: <[a=1 === FOO ===, b=2 === BAR ===,  === BAZ ===, c=3]> but was: <[a=1\n=== FOO ===, b=2\r=== BAR ===,  === BAZ ===, c=3]>` |
| **M4** | deadline check restored above `indexOf` | `cookieSectionBudgetExpiryWithNoSectionRemainingPreservesTheText`; `cookieSectionDeadlineFailsClosed` **stayed GREEN** | as quoted verbatim in §2 |

### M1 did not fail the test the plan predicted, and that is a plan defect

The plan expects M1 to fail `everyEntryOfAMaximalCookieSectionIsRedacted` "naming the first leaking
entry index". **It cannot, by construction.** That test builds a section of
`Redaction.MAX_COOKIE_SECTION_LINES` entries — the plan's own `<behavior>` specifies exactly that —
so lowering the constant lowers the fixture with it and all 8 entries are still redacted. Both sides
of the comparison move together. Confirmed by measurement, not assumed: under M1 that test **passed**.

The intent — *M1 must produce a red test that names the hazard* — is satisfied by
`cookieEmitterBoundStaysWithinTheRedactorBound`'s 2x-headroom assertion, which is red under M1 and
whose message names the rejected W-02 alternative explicitly. **M1b was added** to close the gap the
plan's mutation left open: it mutates the walk instead of the constant, and the per-index test fails
naming `ck15`, proving that test is a genuine walk guard rather than a passenger. No assertion was
weakened and no test was edited to make a mutation red.

### M2: both the clamp and a test fired

The plan asks which of the two fired. **Both.** `cookieEmitterBoundStaysWithinTheRedactorBound` went
red on the intended literal (20 vs 16), and the clamp simultaneously held the *effective* value at
the redactor's bound — visible in the second failure, where `blankCookieElementsDoNotConsumeDisplaySlots`
reported `expected: <16> but was: <7>` because `cookieSectionLines` was asked for 16 entries from a
7-element fixture. So the runtime cannot leak, and the drift is still announced. That is why the test
asserts the **intended** literal against the bound and separately asserts `intended == effective`: an
assertion on the clamped value alone would have been vacuous.

### M3 is the finding W-03 asserts

Only the new direct test failed. Both existing end-to-end guards
(`poisonedCookieHeaderCannotTerminateTheCookieSection`, `cookieSectionEntriesAreSanitizedAtTheEmitter`)
stayed green with the limb gone, because both use the literal `=== FOO ===` with no embedded newline.
Nothing else in the suite noticed. Stated plainly: before this plan, deleting
`.replace('\r', ' ').replace('\n', ' ')` reopened CR-01's second trigger in full with a green build.

## 4. W-02: disposition `accept`, with both grounds MEASURED before accepting

The plan directs `accept` and supplies two grounds. Both were verified against the shipped code
rather than taken on the plan's word.

### Ground 1 — header framing converts over-redaction into UNDER-redaction

The reviewer's `isFramedCookieHeader` was applied verbatim as a mutation and four fixtures driven
through the real `Redaction.apply`:

```
MAX_COOKIE_SECTION_LINES = 16
LEAKS    | redact_preview, caption line then the section (no blank line)
LEAKS    | header not alone on its line
LEAKS    | joined tool output, previous line non-blank
redacted | CONTROL: emitter-framed (blank line before, header alone)
```

Three genuine cookie sections leak `OPAQUE_VALUE_XYZ`; the emitter-framed control still redacts. So
the change is a pure loss for PRIV-05 rather than a trade — and **the entire redact + scanner suite
stayed green under that mutation** (only the known flake failed). An unguarded under-redaction is the
precise defect shape this phase exists to eliminate.

The reachability is concrete, not hypothetical: `Redaction.apply` has **six** call sites and only one
is the scanner emitter. `McpToolContext.redactIfNeeded` runs over every MCP tool result and every
tool's args JSON (`McpTool.kt:45`, `:78`, `McpToolExecutorImpl.kt:1013`, `:1088`);
`McpToolExecutorImpl.kt:986` is the `redact_preview` tool, whose entire contract is "apply the policy
to this arbitrary text I hand you"; `ContextCollector.kt:52-53` and `BountyPromptTagResolver.kt:79-80`
pass raw request/response text. None of them passes through `buildScanMetadataText`, so none carries
the emitter's framing.

### Ground 2 — shrinking the bound directly worsens W-01

With `MAX_COOKIE_SECTION_LINES` set to `COOKIES_MAX_COUNT + 2 = 8`, the same 20-entry fixture:

```
MAX_COOKIE_SECTION_LINES = 8
leaked: ck8 ck9 ck10 ck11 ck12 ck13 ck14 ck15 ck16 ck17 ck18 ck19
first leaking index: 8
```

The first leaking entry moves from **16 to 8** and the leaking set **triples**, from 4 entries to 12.

### Residual pin

`plantedCookieHeaderBlastRadiusIsBoundedToTheSectionBound_acceptedResidual` asserts both halves: the
lines inside the bound lose their values (including the `debug=`, `sql=` and `internal=` shapes a
passive scanner most needs) **and** the first line past the bound survives. Green before and after by
design, deliberately kept separate from `everyEntryOfAMaximalCookieSectionIsRedacted` so that a
residual pin and a security guard cannot fail for the same reason and be misread.

The source residual note now states the widening case, names what is lost, names the other reachable
surfaces (`formatParamLine` emits `name=value (TYPE)` into `=== PARAMETERS ===` with no sanitiser;
both header sections carry attacker-controlled text), records that WR-01 was fixed on exactly this
ground, and gives both rejected alternatives with their measured reasons.

## 5. Fixture-reachability arguments

| Test | Why it is reachable ONLY by the path under test |
|---|---|
| `everyEntryOfAMaximalCookieSectionIsRedacted` | Names `ck0`…`ckN` are tokens in none of `SENSITIVE_WORDS`, `BROAD_WORDS`, `CREDENTIAL_PREFIXES`, `KNOWN_SESSION_KEYS`, so `SENSITIVE_KEY_EXPR` and its three consumers cannot reach them. Values carry no `=`, no leading `?`/`&`, no `Bearer `/`Basic ` prefix, no `eyJ`, no JSON pair context and no ` (COOKIE)` suffix. `redactCookieSections` is the only rule that can touch them. **A name like `session0` or `key0` would make the test pass with the defect fully present.** Proven by M1b. |
| `cookieEmitterBoundStaysWithinTheRedactorBound` | Has no fixture at all — it reads the two shipped constants directly, which is exactly why no input change can defuse it. Proven by M1 and M2. |
| `sanitizeCookieSectionEntriesNeutralisesEveryFramingPrimitive` | Calls the pure function directly, so no other rule runs. The decisive inputs are `"a=1\n=== FOO ==="` and `"b=2\r=== BAR ==="`; **no other test in the suite passes an entry containing a CR or LF**, which is what M3 measures. Input reachability: `cookieSectionLines` splits the raw `Cookie:` header on `;` and trims, without removing interior newlines, and a hand-edited Repeater/Intruder `Cookie:` header is ordinary Burp usage. |
| `cookieSectionBudgetExpiryWithNoSectionRemainingPreservesTheText` | The `testRedactCookieSections` seam bypasses `Redaction.apply` entirely — no header rule, no typed-parameter rule, no body stage — so byte-identity can only come from the loop breaking on "no section found". The fixture's `name=value` lines *would* be rewritten if a section were ever opened, so passing by accident is unavailable. Proven by M4. |
| `plantedCookieHeaderBlastRadiusIsBoundedToTheSectionBound_acceptedResidual` | Same elimination as the first row (`debug`, `sql`, `internal`, `version`, `l0`…`lN`), which is what makes the **survival** of the line past the bound genuine evidence of the residual rather than another rule declining to fire. Fixture size asserted `== MAX_COOKIE_SECTION_LINES` before the behaviour, so it cannot drift off the boundary. |

## 6. Verification

**Full suite: 659 tests, 1 failure** — `newlineFreeOversizeBodyIsScannedNotDestroyed`, the
pre-existing **D-21-01** flake owned by plan 21-16. Diagnosed by stack trace, not by testcase name,
per the known-XML-misattribution warning. ktlint and detekt clean;
`git diff --stat -- detekt-baseline.xml` empty; `DECISIONS.md`, `.planning/codebase/CONCERNS.md`,
`STATE.md`, `ROADMAP.md` and `REQUIREMENTS.md` all untouched.

### One transient failure investigated and cleared

During Task 2's verification run, `windowedScanRedactsJsonPairWhoseValueStraddlesTheCut` (21-13's
sweep) failed once with `shift=10: the sweep must prove the pair was REDACTED, not that the window
was DROPPED`. It was **not** absorbed as noise:

1. Re-run in isolation with the implementation applied: **passed**.
2. Implementation backed up out-of-tree, `Redaction.kt` reverted to the committed base with
   `git checkout --`, full redact suite re-run: the sweep **did not fail there either**, so it is not
   deterministic at either state. Implementation then restored with `/bin/cp -f`. No `git stash` was
   used and no uncommitted work was lost.
3. Reachability argument: the sweep's fixture contains no `=== COOKIES ===` header, so
   `redactCookieSections` runs exactly one iteration and one `indexOf` under both the old and new
   ordering. The hoist is unobservable for header-free input. The reported symptom is a
   `MAX_REDACTION_BUDGET_MS` window drop — a different budget, and the same wall-clock-pressure
   family as W-04/D-21-01, which failed in that same run.

It has not recurred in the four subsequent full runs. Recorded here rather than silently ignored.

### Monotonicity canaries — named individually, all green

`balancedModeRedactsUrlTokensInQueryStrings`, `bodyFormLeadingFieldRedacted`,
`bodyJsonSecretKeysRedacted`, `offModePreservesBodies`, `balancedModeRedactsCustomAuthHeaders`,
`hkdfMatchesRfc5869Vector`, `cookieSectionValuesRedactedPerName`,
`cookieSectionBlankEntriesDoNotCollapseSpan`, `cookieSectionDecoyDoesNotShieldRealSection`,
`cookieSectionHeaderShapedEntryTerminatesSpan_documentedResidual`, `cookieSectionDeadlineFailsClosed`,
`redactCookieSectionsIsLinearInSectionCount`, `windowedScanRedactsJsonPairAcrossEveryBoundaryAlignment`,
`jsonPairWithBlankLineBetweenKeyAndValueIsRedacted`, `windowedScanRedactsJsonPairWhoseValueStraddlesTheCut`,
`blankCookieElementsDoNotConsumeDisplaySlots`, `poisonedCookieHeaderCannotTerminateTheCookieSection`,
`cookieSectionEntriesAreSanitizedAtTheEmitter`, `emittedBlobContainsTheSectionConstant_parity`.

**No canary moved.** The two locked SC6 inversions were not touched.

### D-10 not regressed

`redactCookieSections` still calls `indexOf(COOKIE_SECTION_HEADER, cursor)` inside a `while` with a
monotonically advancing cursor — the hoist moved the call *earlier* in the same iteration, it did not
remove or bound it. `cookieSectionDecoyDoesNotShieldRealSection` is green.

### Ownership boundaries respected

`git diff -U0 b65622c..HEAD -- Redaction.kt` contains **zero** `+`/`-` lines mentioning `windowEnd`,
`isJsonPairBoundaryRisk`, `endsInsideOpenQuotedValue`, `pairMayBeInFlightAt`,
`isJsonPairBoundaryContinuation`, `splitPoint`, `safeCutPoint`, `WINDOW_RETRY_MAX_DEPTH`,
`MAX_JSON_BOUNDARY_LOOKAHEAD_LINES`, `hkdf`, `HKDF` or `anonymizeHost`. The only hits for
`MAX_REDACTION_BUDGET_MS` are four comment-prose lines inside the `COOKIE_SECTION_BUDGET_MS` block
that were reflowed, not changed; `Defaults.kt` is not in this plan's diff at all.
`git diff -U0 -- PassiveAiScannerPrompts.kt` touches **no** `appendLine()` call, so the emitted blob
is byte-identical and `emittedBlobContainsTheSectionConstant_parity` holds.

W-07's **test half** (`RedactionTest.kt`'s stale `emittedSectionShapedCookieCannotTerminateSpan`
reference) was deliberately left alone: `21-16-PLAN.md` declares `W-07-test` in its `gaps_closed`.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 2 - Missing correctness] A fourth copy of the literal `6` in the test source**

- **Found during:** Task 1, after making `COOKIES_MAX_COUNT` internal
- **Issue:** `PassiveAiScannerPromptRedactionTest.kt:32` carried `private const val COOKIES_MAX_COUNT = 6`
  with a comment stating the real one was `private` — now false, and a same-package file-private
  shadow of an `internal` constant with the identical name. W-01 is precisely about an unasserted
  duplicate of this number; leaving a fourth copy that *shadows* the authority would have defeated
  the coupling from the test side.
- **Fix:** mirror deleted, the real `internal` constant referenced directly, and the comment replaced
  with why it is no longer mirrored (and why `PARAM_VALUE_MAX_CHARS` still is — truncation genuinely
  stays at the call site and no redactor rule depends on it).
- **Files modified:** `src/test/kotlin/com/six2dez/burp/aiagent/scanner/PassiveAiScannerPromptRedactionTest.kt`
- **Commit:** `5caeaa4`

**2. [Rule 1 - Bug] Both corrected records re-embedded the false string they were correcting**

- **Found during:** Task 3 final verification
- **Issue:** the corrections quoted the old wording verbatim in order to negate it, so
  `grep "across ALL"` and `grep "Nothing in plan 21-08 calls this yet"` still matched the file. A
  grep-based verifier would conclude neither fix was applied — the tenth false-positive grep criterion
  this phase would have produced.
- **Fix:** both paraphrased. Meaning preserved, exact strings gone (`grep -c` returns 0).
- **Commit:** `1c2a58e`

### Plan Defects Recorded (intent satisfied, criterion not literally satisfiable)

**1. Task 1's M1 acceptance criterion is unsatisfiable as written.** It requires
`everyEntryOfAMaximalCookieSectionIsRedacted` to fail when `MAX_COOKIE_SECTION_LINES` is lowered to 8
and "the SUMMARY quotes the failing index". The same plan specifies that test's fixture as
"a section with exactly `Redaction.MAX_COOKIE_SECTION_LINES` entries", so the mutation moves the
fixture with the constant and the test is vacuous under M1 by construction. **Measured, not assumed**
— it passed under M1. Intent satisfied two ways: `cookieEmitterBoundStaysWithinTheRedactorBound`'s
headroom assertion is red under M1, and **M1b** (a walk off-by-one) makes the per-index test red
naming `ck15`, which is the failing index the criterion wanted. Nothing was weakened to make a
criterion match.

**2. Task 2's acceptance criterion "the KDoc no longer contains the phrase …" is a grep criterion on
a comment that must discuss the phrase.** Satisfied by paraphrase (deviation 2 above) rather than by
deleting the correction's context.

### Decisions Made

**`cookieSectionPairRegex` stays outside `SafeRegex` — recorded as a residual, not claimed closed.**
The plan required a decision in writing. `SafeRegex.replaceAllSafe` returns the **original input** on
timeout, which for this rule means the unredacted cookie section passes straight through — fail
**open**, in direct contradiction of D-02. Adopting `replaceAllSafeReporting` and branching on
`timedOut` would be the correct route and is a behaviour change outside this plan's surface.
Acceptable meanwhile because the expression `(?m)^([^=\r\n]+)=(.*)$` has one negated class, one
dot-star, no alternation, no nested quantifier and no backreference, and `.` excludes newline without
`DOTALL` so every match is line-bounded: worst case linear in the section length. Written into the
constant's comment as a residual.

## Threat Model Outcomes

| Threat ID | Disposition | Outcome |
|---|---|---|
| T-21-46 | mitigate | Closed. Clamp + `cookieEmitterBoundStaysWithinTheRedactorBound` + `everyEntryOfAMaximalCookieSectionIsRedacted`; proven by M1, M1b, M2. |
| T-21-47 | mitigate | Closed. M3 shows the limb's deletion is caught by the new direct test and by **nothing else**. |
| T-21-48 | mitigate | Closed. `indexOf` hoisted; M4 shows the two deadline tests pin opposite halves independently. |
| T-21-49 | **accept** | Both alternatives measured and rejected (§4); radius pinned; residual note states both directions. |
| T-21-50 | mitigate | Both records corrected and paraphrased so they cannot read as still-false. |
| T-21-51 | mitigate | IN-04 recorded at the emitter site with both call sites, idempotence, and a different guard each. |
| T-21-SC | mitigate | Zero packages installed, no new Gradle dependency, no new import in main source. |

## Threat Flags

None. No new network endpoint, auth path, file access pattern or schema change; the only new
cross-package reference is a test-source import of two `internal` scanner constants, which keeps the
main-source dependency direction (scanner → redact) unchanged.

## Known Stubs

None.

## Task Commits

| Task | Commit | Description |
|---|---|---|
| 1 | `5caeaa4` | Couple the emitter cookie bound to the redactor bound (W-01) |
| 2 (RED) | `edcd999` | RED for the cookie budget branch, direct test for the sanitizer (W-05, W-03) |
| 2 | `4c39a58` | Pay the fail-closed price only when a section is pending (W-05, W-07) |
| 3 | `f2cbf82` | Dispose of W-02 as accept with measured grounds, pin the radius, record IN-04 |
| 3 (fix) | `1c2a58e` | Paraphrase the two corrected claims instead of quoting them verbatim |

## Self-Check: PASSED

All five task commits resolve (`5caeaa4`, `edcd999`, `4c39a58`, `f2cbf82`, `1c2a58e`). All five named
tests exist in `RedactionTest.kt`. `MAX_COOKIE_SECTION_LINES = 16` is `internal` in `Redaction.kt`;
`COOKIES_MAX_COUNT` is an `internal val` in `PassiveAiScannerAnalysis.kt`; IN-04 is present in
`PassiveAiScannerPrompts.kt`. No file claimed as modified is missing from the diff, and no file
outside the plan's declared surface was touched.

## Next Phase Readiness

Plan 21-16 owns W-04/D-21-01 (the injected-budget seam for
`newlineFreeOversizeBodyIsScannedNotDestroyed`) and W-07's test half. Neither was touched here.
`MAX_COOKIE_SECTION_LINES` is now `internal`, so 21-16 can reference it if needed without another
restatement.
