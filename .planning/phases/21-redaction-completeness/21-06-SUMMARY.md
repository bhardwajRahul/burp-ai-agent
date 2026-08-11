---
phase: 21-redaction-completeness
plan: 06
subsystem: redact
tags: [PRIV-06, SC4, SC5, D-01, D-02, D-03, D-04, D-05, D-14, T-21-02, T-21-03, T-21-06, T-21-22, kotlin, redaction, privacy, windowing, fail-closed]

# Dependency graph
requires:
  - phase: 21-redaction-completeness
    plan: 02
    provides: "SafeRegex.replaceAllSafeReporting / SafeReplaceResult.timedOut and Defaults.MAX_REDACTION_BUDGET_MS"
  - phase: 21-redaction-completeness
    plan: 05
    provides: "the cookie rules in the stripCookies stage, which run BEFORE the body stage and are idempotent under it"
provides:
  - "Redaction.bodyStage — line-boundary windowed, budgeted, fail-closed body redaction; nothing is skipped above the size cap"
  - "Redaction.windowEnd / splitPoint — line-aligned window cutting with the JSON key/value boundary-safety rule"
  - "Redaction.truncationLogger — @Volatile settable Output-tab sink for the D-03 truncation notice"
  - "Redaction.maybeLogTruncation(nowMs, droppedChars) — read-then-CAS rate limiter, counts only, injected clock"
  - "Redaction.resetTruncationWindowForTest() — internal test seam for the limiter window"
  - "Two drop markers: [REDACTION BUDGET EXCEEDED - n CHARS DROPPED AND NOT SENT] and [REDACTION INCOMPLETE - n CHARS DROPPED AND NOT SENT]"
  - "Custom patterns applying in EVERY privacy mode including OFF (D-05)"
affects: [21-07, redaction, privacy, passive-scanner, mcp-tools]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Fail-closed streaming stage: cut at semantic boundaries, bound each unit by min(unit deadline, remaining total budget), and DROP behind a marker anything not fully processed — never emit unprocessed input"
    - "Halve-and-retry before dropping: a deadline used as a pacing mechanism rather than a cliff, bounded by an explicit recursion depth constant"
    - "Settable diagnostic sink on a dependency-light package: @Volatile ((String) -> Unit)? wired at App.initialize, so the package gains observability without gaining a dependency"

key-files:
  created: []
  modified:
    - src/main/kotlin/com/six2dez/burp/aiagent/redact/Redaction.kt
    - src/main/kotlin/com/six2dez/burp/aiagent/App.kt
    - src/test/kotlin/com/six2dez/burp/aiagent/redact/RedactionTest.kt
    - src/test/kotlin/com/six2dez/burp/aiagent/scanner/PassiveAiScannerPromptRedactionTest.kt

key-decisions:
  - "The two drop markers are private functions returning a template rather than const val format strings: String.format's %d is locale-sensitive for digit rendering, and the marker must be deterministic ASCII because it is hashed into sha256Hex(singlePrompt)"
  - "dropOrRetry refuses to recurse once the budget is spent, so an exhausted budget produces ONE marker for the window instead of 2^WINDOW_RETRY_MAX_DEPTH markers"
  - "The AuditLogger class name could not be written in Redaction.kt's comments — an acceptance criterion greps that literal expecting 0 — so the rationale is phrased as 'the project's audit log'"
  - "The @AfterEach added to PassiveAiScannerPromptRedactionTest delegates to the existing @BeforeEach method rather than repeating setCustomPatterns(emptyList()), keeping that literal at the single occurrence the acceptance criterion requires"

requirements-completed: [PRIV-06]

# Metrics
duration: 22min
completed: 2026-08-11
---

# Phase 21 Plan 06: Fail-Closed Windowed Body Stage Summary

**`Redaction.apply` no longer skips the body rules above 1 MB — the input is cut into line-boundary windows under a 2 s wall-clock budget, every rule runs through `replaceAllSafeReporting`, and any window that could not be fully scanned is DROPPED behind a visible marker instead of passed through; custom patterns moved outside the `redactTokens` branch and now apply in every privacy mode including OFF.**

## Performance

- **Duration:** 22 min
- **Started:** 2026-08-11T14:06:00Z
- **Completed:** 2026-08-11T14:28:00Z
- **Tasks:** 3
- **Files modified:** 4 (all modified, none created)

## The Two Deliberate SC6 Exceptions (read this first)

SC6 says "the existing `RedactionTest` suite stays green". Two of its 15 pre-phase tests were changed
**on purpose**, because two locked decisions require it. Neither is a regression.

**Exception 1 of 2 — `oversizeBodySkippedSafely` was REWRITTEN as `oversizeBodySecretDoesNotSurvive`.**
The old test asserted the fail-open as correct behaviour: its comment recorded that an over-cap secret
was allowed to remain, and its only substantive assertion was that the call returned quickly — which
stayed true *precisely because* every body rule was skipped. That is the PRIV-06 defect asserted as a
contract, so the test had to be rewritten rather than extended. The rename is deliberate: the old name
would now misdescribe the contract, and `21-VALIDATION.md`'s automated selector
`*RedactionTest.oversizeBody*` matches both names, so traceability is preserved.

**Exception 2 of 2 — the OFF limb of `customPatternRedactsInStrictAndBalanced` was INVERTED by D-05.**
It previously asserted `assertEquals(input, offOutput, "OFF mode must not apply custom patterns")`.
The custom-pattern loop now sits outside the `redactTokens` branch, so a user's custom list applies
under `PrivacyMode.OFF` too: OFF means "no BUILT-IN redaction", not "no redaction at all". **The method
name is unchanged on purpose** — `21-CONTEXT.md`, `21-VALIDATION.md` and `21-VERIFICATION.md` all refer
to this test by name, and traceability beats an accurate name here.

**13 of the 15 existing `RedactionTest` tests are unchanged and green**, including SC6's named RFC 5869
vector `hkdfMatchesRfc5869Vector`. The three tests plan 21-05 added are also unchanged and green.
`Redaction.kt`'s HKDF block was not touched at all: `git diff c9c420e HEAD -U0 -- Redaction.kt | grep -c
'hkdf\|HKDF_INFO\|HKDF_OKM_LEN\|anonymizeHost'` returns **0**.

## For Plan 21-07: the red-before-green insertion point

The plan's output spec requires this line number. Inside `bodyStage`, the D-04 single-pass guard is:

```
src/main/kotlin/com/six2dez/burp/aiagent/redact/Redaction.kt:468
        if (input.length <= Defaults.MAX_REDACTION_BODY_CHARS) {
```

21-07's mutation goes **directly after** it. The exact mutation that reinstates the pre-fix defect —
and which was already measured RED against both new SC4 tests during this plan, see the mutation table
below — is to replace `return windowedScan(input, rules)` (`Redaction.kt:481`) with `return input`.

## Accomplishments

- **The fail-open above the size cap is gone (SC4 / T-21-02).** `Redaction.apply:760` now calls
  `bodyStage(out, builtinsEnabled = policy.redactTokens)` unconditionally, outside the `redactTokens`
  branch. Above the window width the input is cut into line-boundary windows and **every** window is
  scanned. `oversizeBodySecretDoesNotSurvive` proves an `api_key=` secret sitting in the *second*
  window is redacted; it was measured FAILING against the reinstated skip.
- **Fail-closed is real, not nominal (D-02 / D-14 / T-21-03).** `scanWindow` consumes
  `SafeRegex.replaceAllSafeReporting` and branches on `timedOut`. `replaceAllSafe`'s return value is
  **never** assigned in the windowed path — on timeout it returns the input unchanged, byte-identical
  to "the pattern matched nothing", which is fail-**open** at exactly the moment D-02 demands
  fail-closed. `oversizeBodyFailsClosed` was measured FAILING when the `timedOut` branch is removed.
- **Deadlines compose without double-counting.** Every call uses
  `minOf(SafeRegex.DEFAULT_TIMEOUT_MS, remainingBudgetMs)`, so a per-pattern deadline can never
  outlive `Defaults.MAX_REDACTION_BUDGET_MS`. This is the rule 21-02 documented in `Defaults.kt` but
  left unenforced anywhere in code; it is now enforced at `Redaction.kt:530-535`.
- **Halve-and-retry before dropping (T-21-06).** A timed-out window is split at a line boundary and
  each half retried, to `WINDOW_RETRY_MAX_DEPTH = 2`, before being dropped. This closes 21-02's
  carry-forward warning that only ~2.2x headroom exists at 1 MB, so a 2-3x slower machine would drop
  content that ships today.
- **Truncation is visible in both agreed places (D-03).** A marker in the payload, plus a
  rate-limited `[Redaction] ...` Output-tab line wired at `App.kt:68` beside the other diagnostics
  sinks. `redact/` gained **no** dependency: `grep -c 'AuditLogger\|java.awt\|javax.swing'` on
  `Redaction.kt` returns 0.
- **D-05 landed and is proven on a real caller seam.** `offStillAppliesCustomPatterns` runs
  `redactScanMetadata(blob, PrivacyMode.OFF, salt)` against the real emitted blob and asserts the
  custom-pattern secret is gone — which is what makes plans 21-01 and 21-03's D-06 short-circuit
  deletions load-bearing rather than cosmetic.
- **OFF byte-identity preserved (Pitfall 6).** `rules.isEmpty()` returns the input *before* any
  windowing, so OFF with no custom patterns is a byte-identical passthrough.
  `offModePreservesBodies`, `offModePreservesAllTokens` and
  `redactScanMetadata_offModeIsByteIdentical` are all green untouched.
- **627 tests, 0 failures, 0 errors.** detekt baseline unchanged (QUAL-07), ktlint green.

## Task Commits

Each task was committed atomically:

1. **Task 1: Rewrite the body stage as a line-boundary windowed scan that fails closed** — `7d5470f` (feat)
2. **Task 2: Add the rate-limited truncation signal and wire it in App.kt (D-03)** — `3ce2198` (feat)
3. **Task 3: Rewrite the SC4 oversize tests and prove D-06 on the scanner seam** — `f0faffc` (test)

## Files Created/Modified

- `src/main/kotlin/com/six2dez/burp/aiagent/redact/Redaction.kt` — the D-03 sink and limiter
  (`:320-378`), the body-stage machinery (`:439-625`), and the rewritten `apply` wiring (`:752-760`).
  One new import, `java.util.concurrent.atomic.AtomicLong`.
- `src/main/kotlin/com/six2dez/burp/aiagent/App.kt` — one line at `:68`,
  `Redaction.truncationLogger = { api.logging().logToOutput(it) }`, beside `BackendDiagnostics.output`
  / `.error`. **`Redaction.setCustomPatterns(settings.customRedactionPatterns)` at `:95` was NOT
  touched**, and neither was `ui/SettingsPanelSettingsIO.kt:475`: D-05 changes only *where* the
  compiled pattern list is consulted, never how it is seeded or refreshed.
- `src/test/kotlin/com/six2dez/burp/aiagent/redact/RedactionTest.kt` — the D-05 limb inversion, a
  second `@AfterEach`, three file-private clock constants, `truncationSignalIsRateLimited`,
  `oversizeBodySecretDoesNotSurvive` (rewritten) and `oversizeBodyFailsClosed`. 23 tests, 0 failures.
- `src/test/kotlin/com/six2dez/burp/aiagent/scanner/PassiveAiScannerPromptRedactionTest.kt` —
  `offStillAppliesCustomPatterns`, an `@AfterEach`, and a `responseBody` parameter on the private
  `metadataBlob` helper. 8 tests, 0 failures; the 7 pre-existing tests are unchanged.

## The Shipped Body Stage (anchors for ADR-14 in plan 21-07)

All line numbers are post-plan and were re-derived, not copied.

| Element | Anchor | Role |
|---|---|---|
| `truncationLogger` (`@Volatile`, public) | `Redaction.kt:319-320` | D-03 Output-tab sink |
| `maybeLogTruncation(nowMs, droppedChars)` | `Redaction.kt:345-359` | read-then-CAS limiter, 10 s window |
| `truncationLine(droppedChars, suppressed)` | `Redaction.kt:363-369` | counts only, never dropped content |
| `resetTruncationWindowForTest()` | `Redaction.kt:374-377` | internal test seam |
| `NANOS_PER_MS`, `WINDOW_RETRY_MAX_DEPTH` | `Redaction.kt:382, 389` | named constants; no bare literals |
| `budgetExceededMarker` / `windowDroppedMarker` | `Redaction.kt:408, 414` | the two drop markers |
| `bodyStage(input, builtinsEnabled)` | `Redaction.kt:439-482` | rule list, OFF fast path, D-04 single pass |
| **D-04 single-pass guard** | **`Redaction.kt:468`** | **21-07's mutation goes directly after this** |
| `windowedScan` | `Redaction.kt:485-500` | in-order windows, ONE tail marker, `break` |
| `remainingBudgetMs` | `Redaction.kt:503` | the total-budget clock |
| `scanWindow` | `Redaction.kt:516-546` | `replaceAllSafeReporting` + `timedOut`, `minOf(...)` |
| `dropOrRetry` | `Redaction.kt:552-568` | halve-and-retry, then the marker |
| `splitPoint` | `Redaction.kt:573-578` | line boundary nearest the middle |
| `windowEnd` | `Redaction.kt:597-618` | line-aligned cut + JSON boundary safety |
| `isJsonPairBoundaryRisk` | `Redaction.kt:622-625` | trailing `:` or `"` pulls in one more line |
| `out = bodyStage(out, ...)` | `Redaction.kt:760` | **outside** the `redactTokens` block |

**The two markers**, verbatim, ASCII hyphen, constant shape plus one integer:

```
[REDACTION BUDGET EXCEEDED - <n> CHARS DROPPED AND NOT SENT]
[REDACTION INCOMPLETE - <n> CHARS DROPPED AND NOT SENT]
```

**The Output-tab line**, counts only:

```
[Redaction] Body redaction dropped <n> characters; that content was NOT sent to the AI backend.
[Redaction] ... Further notices suppressed since the previous line: <m>.
```

## Red-Before-Green: Every New Security Test Mutation-Verified

Phase 20's SC4 discipline, carried forward from plans 21-04 and 21-05. Each mutation was applied with
`Edit` and reverted with `git checkout HEAD -- <single path>` (the sanctioned single-file discard —
`git stash` is forbidden across worktrees and `git clean` is destructive here). The working tree was
confirmed clean after each.

| Mutation | Test(s) | Result | What it proves |
|---|---|---|---|
| `bodyStage` returns `input` instead of `windowedScan(...)` — the pre-Phase-21 skip, reinstated | `oversizeBodySecretDoesNotSurvive` | **FAILED** — `STRICT: a secret past the old size cap must not survive the body stage ==> expected: <false> but was: <true>` | The SC4 gate detects the PRIV-06 defect. This is the mutation 21-07 needs |
| same | `oversizeBodyFailsClosed` | **FAILED** — `A window that could not be fully scanned must be dropped behind a marker, not passed through` | The fail-closed marker only exists because windowing happens |
| `scanWindow` drops the `if (result.timedOut)` branch (assigns `result.text` unconditionally — the fail-OPEN shape) | `oversizeBodyFailsClosed` | **FAILED** — same message | D-14 is load-bearing: without `timedOut`, unscanned bytes are emitted silently and nothing else notices |
| custom patterns gated back on `builtinsEnabled` (D-05 reverted) | `offStillAppliesCustomPatterns` | **FAILED** — `OFF: a user's custom pattern is a 'never send this' list and must still redact` | D-05 is proven on the real scanner seam, not just in isolation |
| same | `customPatternRedactsInStrictAndBalanced` | **FAILED** — `OFF: a custom pattern must still redact SECRET-1234` | The inverted limb is a real assertion, not a rubber stamp |
| `maybeLogTruncation`'s window/CAS check short-circuited to always emit | `truncationSignalIsRateLimited` | **FAILED** — `A second truncation inside the same window must be suppressed ==> expected: <1> but was: <2>` | The D-03 limiter is asserted, not assumed |

**Fixture strength, per the 21-05 lesson (a test that another rule also catches is vacuous):**
- `oversizeBodySecretDoesNotSurvive` uses `api_key=SC4-SECRET-VALUE-7B3E` on its own line with **no**
  preceding `?` or `&`. `urlTokenParamRegex` — which runs *unbounded in the header stage* and would
  otherwise mask the defect entirely — requires `[?&]` before the key and cannot reach it. The value
  is not bearer- or basic-prefixed and does not start with `eyJ`. Only `formBodyParamRegex`'s
  `(^|[?&])` leading-field anchor, inside the body stage, can save it.
- `offStillAppliesCustomPatterns` runs under OFF, where `stripCookies`, `redactTokens` and
  `anonymizeHosts` are all false, so every built-in rule is inert by construction and the custom
  pattern is the only mechanism that can produce the assertion.

## Decisions Made

- **The markers are private functions, not `const val` format strings.** The plan called them "two
  constants". A `const val` with `%d` plus `String.format` is locale-sensitive for digit rendering
  (a JVM default locale using non-ASCII digits would change the marker), and the marker must be
  deterministic ASCII because it is hashed into `sha256Hex(singlePrompt)` and round-trips through
  backend transports. A Kotlin string template uses `Int.toString()`, which is always ASCII. The
  constant *shape* plus one integer — the property T-21-05 actually depends on — is preserved, and
  each literal appears exactly once in the file as the acceptance criteria require.
- **`dropOrRetry` refuses to recurse once the budget is spent.** Without that check, a window that
  times out because the *budget* ran out (rather than the pattern being slow) would split twice and
  emit up to four markers. Checking `remainingBudgetMs > 0` alongside the depth limit keeps it at one
  marker per genuinely-dropped window, which is Pitfall 8's concern applied one level down.
- **`windowEnd` computes `hard` as `if (width >= s.length - start) s.length else start + width`**
  rather than `minOf(s.length, start + width)`, so the sum cannot overflow `Int` on a very large
  input. Same result, no overflow window.
- **`@Suppress("ReturnCount")` on `bodyStage` and `windowEnd` only.** Both are genuinely three-return
  functions where restructuring would hurt clarity (the OFF fast path and the D-04 single pass are
  each a guard clause that deserves to read as one). Declaration-level suppression of a complexity
  rule has direct in-repo precedent — `ui/SettingsPanelInit.kt:29`, `ui/SettingsPanelMcpTabs.kt:148`,
  `scanner/PassiveAiScannerAnalysis.kt:169`. `scanWindow`, `dropOrRetry` and `splitPoint` were written
  to two returns and need no suppression. **No detekt baseline entry was added** (QUAL-07).
- **`MagicNumber` avoided with `NANOS_PER_MS` and `WINDOW_RETRY_MAX_DEPTH`**, exactly as the plan
  specified; `window.length / 2` is safe because `2` is in detekt's ignore list.
- **All window-loop state is local** (T-21-23). The only object-level state this plan adds is the two
  `AtomicLong`s the D-03 limiter needs.
- **Comment markers follow the destination file**, inheriting 21-05's decision: `/** */` on the public
  `truncationLogger` and on `maybeLogTruncation` (which needs `[...]` links the plan asked for), `//`
  on every private helper and on the `resetTruncationWindowForTest` seam, matching the existing
  `testHkdfExtract` style.

## Deviations from Plan

**None requiring a deviation rule.** No bug, missing critical functionality, blocking issue or
architectural question arose; no package was installed; no new Gradle dependency was added. Three
points where the plan's *letter* could not be followed exactly are recorded below, all documentation-
or lint-mechanical, none behavioural.

**1. The `AuditLogger` rationale could not name the class.** The plan's Task 2 action text asks the
KDoc to explain that "D-03 rules out `AuditLogger` because that package is deliberately AWT-free" —
but Task 2's own acceptance criterion is `grep -c 'AuditLogger\|java.awt\|javax.swing'
Redaction.kt` returns **0**. Writing the class name would break the criterion. This is the
comment/grep collision the execution brief warned about, hit for the fifth time in this phase. The
full rationale is present, phrased as "the project's audit log" and "free of any UI toolkit import"
(`Redaction.kt:308-315`). No content was dropped and no criterion was weakened.

**2. The `@AfterEach` in `PassiveAiScannerPromptRedactionTest` delegates rather than repeats.** The
criterion requires `grep -c 'setCustomPatterns(emptyList())'` to return **1**, but that literal
already exists in the file's `@BeforeEach` (added by plan 21-05, which explicitly asked that it be
kept). The new `@AfterEach` therefore calls `clearCustomPatterns()` rather than repeating the
statement. Both greps are satisfied, the `@BeforeEach` is byte-identical, and the leak is named in a
comment as the plan required.

**3. Task 1's HKDF acceptance criterion is unsatisfiable as literally written (documentation defect
in the plan, not a code defect).** The criterion `git diff HEAD~1 -- Redaction.kt | grep -c
'hkdf\|HKDF_INFO\|HKDF_OKM_LEN\|anonymizeHost'` returns **1**, not 0. The single match is a *context*
line in the unified diff — ` if (policy.anonymizeHosts) {` — which appears only because the `apply`
edit is directly above it, and `anonymizeHost` is a substring of `anonymizeHosts`. The criterion's
**intent** is fully satisfied and was verified directly: with `-U0` (changed lines only) the count is
**0**, and filtering to `^[+-]` lines also gives **0**. A future restatement should use `-U0` or
filter to changed lines. Identical in kind to the `SafeRegex.kt` banner-comment defect plan 21-02
reported.

**Total deviations:** 0 auto-fixed (no deviation rule triggered)
**Impact on plan:** None. Nothing on the do-not-touch list was modified — the HKDF block, host
anonymization, `App.kt`'s `setCustomPatterns` seeding and `ui/SettingsPanelSettingsIO.kt` are all
untouched, verified by diff.

## Known Residual: the sub-1 MB path now carries a deadline

**This is the one genuine behaviour change on the common path, and the plan asked for it to be
recorded.** Before this phase the two built-in body rules (`formBodyParamRegex`, `jsonSecretKeyRegex`)
ran with **no deadline at all** below the size cap — only custom patterns went through `SafeRegex`.
They now all run through `SafeRegex.replaceAllSafe` at the 50 ms `DEFAULT_TIMEOUT_MS`
(`Redaction.kt:468-475`).

The consequence: on the single-pass path a rule that exceeds 50 ms is skipped **fail-open**, exactly
as `replaceAllSafe` documents. Measured risk window: dense form content near 1 MB costs 23 ms on
Apple Silicon, so only a ~2x slower machine processing a near-1 MB dense body could cross the
deadline. `ContextCollector` truncates bodies to 4k/8k, and the large strings come from MCP tools and
the bounty resolver — which are above the window width and therefore take the *fail-closed* windowed
path instead. Halve-and-retry does not apply below the window width by construction.

This is the plan's specified design (D-04 keeps the sub-1 MB path "identical to today for the
overwhelming majority of payloads", and its acceptance criterion pins
`Defaults.MAX_REDACTION_BUDGET_MS` to a single occurrence, which is the windowed path). It is
recorded here, not silently absorbed. **Carry into 21-07's ADR-14 consequences and `CONCERNS.md`.**

## Issues Encountered

- The worktree spawned at `03f17a7`, an ancestor of the assigned base `c9c420e`. Corrected with
  `git reset --hard c9c420e` **after** the branch-namespace assertion passed, per the startup
  protocol. No work was lost — the reset ran before any edit. Third consecutive plan in this phase to
  hit this; it is a worktree-spawn characteristic, not a per-plan problem.
- Mutation testing inside a worktree again required care: `git stash` is forbidden (`refs/stash` is
  shared across worktrees) and `git clean` is destructive here. Every mutation was reverted with
  `git checkout HEAD -- <single path>` against `Redaction.kt` only, never a blanket restore, because
  the test files carried uncommitted work at the time.

## Threat Model Verification

| Threat ID | Disposition | Status |
|---|---|---|
| T-21-02 (fail-open above the size cap) | mitigate | **Satisfied** — the skip is gone; `bodyStage` windows the whole input. `oversizeBodySecretDoesNotSurvive` is the named gate and was verified FAILING against the reinstated skip |
| T-21-03 (fail-open via the silent timeout) | mitigate | **Satisfied** — `scanWindow` branches on `SafeReplaceResult.timedOut`; `oversizeBodyFailsClosed` asserts a marker and was verified FAILING when the `timedOut` branch is removed |
| T-21-04 (ReDoS via custom patterns in more contexts) | mitigate | **Satisfied** — `minOf(DEFAULT_TIMEOUT_MS, remainingBudgetMs)` per call plus `MAX_REDACTION_BUDGET_MS` overall; once the budget is spent all remaining content is dropped under exactly one marker. Save-time `isPatternSafe` rejection is unchanged |
| T-21-05 (drop marker as an injection vector) | mitigate | **Satisfied** — constant shape plus one integer, zero attacker-controlled substring, never echoes dropped content, not phrased as an instruction, ASCII hyphen, distinct from `[REDACTED]` |
| T-21-06 (fail-closed dropping removes legitimate context) | mitigate | **Satisfied** — halve-and-retry to depth 2 before dropping, plus the rate-limited Output-tab line so the user is told rather than failed silently |
| T-21-22 (the notice echoes dropped content) | mitigate | **Satisfied** — `maybeLogTruncation` receives a `Long`, never the text; the line is a constant sentence plus counts |
| T-21-09 (a change near `apply` perturbs HKDF) | mitigate | **Satisfied** — zero changed lines match `hkdf\|HKDF_INFO\|HKDF_OKM_LEN\|anonymizeHost`; `hkdfMatchesRfc5869Vector`, `hostAnonymizationFormatIsStable` and `hostAnonymizationIsStablePerSalt` all green |
| T-21-23 (concurrency: shared mutable window state) | mitigate | **Satisfied** — all window-loop state is local; the only new object-level state is two `AtomicLong`s used by a lock-free read-then-CAS |
| T-21-SC (package-install supply chain) | accept | **Satisfied** — zero packages installed, no new Gradle dependency; one new JDK import (`AtomicLong`) |

**Residual, accepted and disclosed** (both belong in ADR-14's consequences, neither is claimed fixed):
the eight header-stage rules still run unbounded on the full input and are outside D-01/D-02's scope;
and a **custom** pattern whose match spans a window boundary can be missed, because there is no
principled bound on a user regex's match length. The second is documented inline at
`Redaction.kt:591-594`. Add the sub-1 MB deadline residual above as a third.

## Known Stubs

None. Every function added is wired and exercised by live `Redaction.apply` calls or by a direct unit
test. `truncationLogger` being `null` in tests and headless contexts is its documented contract, not
an unwired stub — the production wiring exists at `App.kt:68` and the limiter is asserted directly.

## Threat Flags

None. This plan introduces no new network endpoint, no auth path, no file access pattern and no schema
change at a trust boundary. `redact/` gained no project-internal dependency and remains free of any UI
toolkit import. The one new surface — the drop marker entering model context — was anticipated as
T-21-05 and is mitigated above.

## Verification Results

All commands run with the mandatory JDK 21 prefix.

| Check | Result |
|---|---|
| `./gradlew test ktlintCheck detekt -q` | exit 0 (run after every task and after every mutation revert) |
| `git diff --stat -- detekt-baseline.xml` | empty — baseline did not grow (QUAL-07) |
| Suite totals | **627 tests, 0 failures, 0 errors** |
| `RedactionTest` | 23 tests (21 at base + `truncationSignalIsRateLimited` + `oversizeBodyFailsClosed`; the SC4 rewrite is a rename), 0 failures |
| `PassiveAiScannerPromptRedactionTest` | 8 tests (7 + `offStillAppliesCustomPatterns`), 0 failures |
| `git diff c9c420e HEAD -U0 -- Redaction.kt \| grep -c 'hkdf\|...\|anonymizeHost'` | **0** — SC6 boundary honoured |

**Task 1 acceptance greps:** `private fun bodyStage` = 1; `private fun windowEnd` = 1;
`replaceAllSafeReporting` = 2 (>= 1); `timedOut` = 2 (>= 1); `bodyStage(out` = 1 and the
`redactTokens` block contains it **0** times; `compiledCustomPatterns` = 3 (field, setter, one read in
`bodyStage`); `REDACTION BUDGET EXCEEDED` = 1; `REDACTION INCOMPLETE` = 1;
`NANOS_PER_MS\|WINDOW_RETRY_MAX_DEPTH` = 6 (>= 2); `Defaults.MAX_REDACTION_BUDGET_MS` = 1;
`OFF mode must not apply custom patterns` = 0; `fun customPatternRedactsInStrictAndBalanced` = 1.

**Task 2 acceptance greps:** `var truncationLogger` = 1 with `@Volatile` on the line directly above
(`:319`); `internal fun maybeLogTruncation` = 1 with first parameter `nowMs: Long`; `compareAndSet` = 1
(>= 1); `AuditLogger\|java.awt\|javax.swing` = **0**; `Redaction.truncationLogger = ` = 1 in `App.kt`
at line **68** (< 75); `Redaction.setCustomPatterns(settings.customRedactionPatterns)` = 1;
`fun truncationSignalIsRateLimited` = 1; `Thread.sleep` = **0**; `truncationLogger = null` = 1.

**Task 3 acceptance greps:** `fun oversizeBodySkippedSafely` = 0 and
`fun oversizeBodySecretDoesNotSurvive` = 1; `The over-cap secret may remain` = **0**;
`SC4-SECRET-VALUE-7B3E` = 2 (>= 2); `fun oversizeBodyFailsClosed` = 1, asserting on
`REDACTION INCOMPLETE` or `REDACTION BUDGET EXCEEDED`; `fun offStillAppliesCustomPatterns` = 1;
`@AfterEach` = 1 and `setCustomPatterns(emptyList())` = 1 in the scanner test.

**Monotonicity canaries — all executed and green:** `balancedModeRedactsUrlTokensInQueryStrings`
(`name=alice` survives), `bodyFormLeadingFieldRedacted` (`user=bob` survives),
`bodyJsonSecretKeysRedacted` (`"name":"alice"` survives), `bodyJsonUnquotedSecretValuesRedacted`,
`offModePreservesBodies` (byte-identity under OFF), `offModePreservesAllTokens`,
`hostAnonymizationFormatIsStable`, `hostAnonymizationIsStablePerSalt`, and SC6's named vector
`hkdfMatchesRfc5869Vector`.

## User Setup Required

None — no external service configuration required. Users on Burp will now see a `[Redaction] ...` line
in the Output tab (at most one per 10 s) when a body was large enough that redaction hit its ceiling.

## Next Phase Readiness

- **Plan 21-07 is unblocked.** The mutation it needs is recorded above with its exact insertion point
  (`Redaction.kt:468`, replace `return windowedScan(input, rules)` at `:481` with `return input`) and
  has already been measured RED against both SC4 tests in this plan.
- **Carry into 21-07 (ADR-14 / `CONCERNS.md`), three residuals:** (1) the eight header-stage rules run
  unbounded on the full input — ADR-14 must claim "the **body stage** never fails open", not the
  unqualified form; (2) a custom pattern whose match straddles a window boundary can be missed; (3)
  **new** — below the window width the built-in body rules now carry a 50 ms deadline they did not
  have before, so they can fail open there (see "Known Residual" above).
- **`CONCERNS.md`'s stale `Redaction.kt:56-79` anchor** for "Redaction regex coverage gaps": post-plan
  anchors are header/token regexes `62-85`, cookie rules `84-167`, `SENSITIVE_KEY_EXPR` and inputs
  `172-240`, consumer regexes `242-277`, D-03 sink and limiter `308-378`, body stage `380-625`, HKDF
  block (SC6, do not touch) `647-706`, `apply` `712-786`.
- **`.planning/STATE.md`, `.planning/ROADMAP.md` and `.planning/REQUIREMENTS.md` were deliberately NOT
  modified** — worktree execution, orchestrator owns those writes. PRIV-06 is a single phase-wide
  checkbox (`REQUIREMENTS.md:24`) claimed by five of the seven plans in this phase; the
  `requirements-completed: [PRIV-06]` frontmatter above records what this plan's `requirements` field
  claims, per the summary template, and is not an assertion that the orchestrator should check it off
  before 21-07 completes. All five executors in this phase reached the same conclusion independently.

## Self-Check: PASSED

- `.planning/phases/21-redaction-completeness/21-06-SUMMARY.md` — FOUND
- `src/main/kotlin/com/six2dez/burp/aiagent/redact/Redaction.kt` — FOUND, contains `truncationLogger`, `private fun bodyStage`, `private fun windowEnd`
- `src/main/kotlin/com/six2dez/burp/aiagent/App.kt` — FOUND, contains `Redaction.truncationLogger =`
- `src/test/kotlin/com/six2dez/burp/aiagent/redact/RedactionTest.kt` — FOUND, contains `oversizeBodySecretDoesNotSurvive`, `oversizeBodyFailsClosed`, `truncationSignalIsRateLimited`
- `src/test/kotlin/com/six2dez/burp/aiagent/scanner/PassiveAiScannerPromptRedactionTest.kt` — FOUND, contains `offStillAppliesCustomPatterns`
- Commit `7d5470f` — FOUND
- Commit `3ce2198` — FOUND
- Commit `f0faffc` — FOUND

Working tree clean, no untracked files, no file deletions in any of the three commits
(`git diff --diff-filter=D` empty for each).

---
*Phase: 21-redaction-completeness*
*Completed: 2026-08-11*
</content>
</invoke>
