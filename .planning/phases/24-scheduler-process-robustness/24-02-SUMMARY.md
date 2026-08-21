---
phase: 24-scheduler-process-robustness
plan: 02
subsystem: scanner
tags: [kotlin, jvm, threadpoolexecutor, concurrency, montoya, structural-test, junit5, tdd]

# Dependency graph
requires:
  - phase: 24-scheduler-process-robustness
    plan: 01
    provides: "`scheduleGuarded` around `ActiveAiScanner.processQueue` (the catcher for the `RejectedExecutionException` this plan makes reachable), `Defaults.MAX_SCAN_REQUEST_THREADS` / `Defaults.SCAN_REQUEST_THREAD_KEEPALIVE_SECONDS`, and the `tasks.test` input `mainSourceTreeStructuralInputs` that keeps this plan's structural assertions off the build cache"
provides:
  - "Per-target scan failures are logged as `[ActiveAiScanner] Target scan failed: <target.id>: <message>` — URL + injection point + vulnerability class, capped at 200 characters"
  - "`requestExecutor` is a bounded `ThreadPoolExecutor(0, Defaults.MAX_SCAN_REQUEST_THREADS, keepalive, SECONDS, SynchronousQueue, named daemon factory, AbortPolicy)`"
  - "`scanRequestThreadFactory(): ThreadFactory` — top-level `internal fun` in `ActiveAiScanner.kt`, producing `burp-ai-agent-scan-request-<n>` daemon threads"
  - "A saturated request pool degrades to a logged `null` from inside `sendRequestWithTimeout`'s existing guarded region, never to an exception escaping into the per-target catch"
  - "The scanner's other three executors carry named daemon factories: scan worker pool, queue-drain scheduler, OAST poller"
  - "`ActiveScannerFailureIsolationTest` (REL-06-E/F/G + A-EDGE-2) and `ScanRequestExecutorTest` (REL-07-G/H/I + A-EDGE-4)"
affects: [24-05, any future plan touching ActiveAiScanner's executors or its per-target failure path]

actuals:
  tokens: 26520
  tasks: 3
  commits: 4

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Explicit `ThreadPoolExecutor` construction (ceiling, queue, factory, rejection policy all stated at the call site) in place of an `Executors` convenience factory"
    - "Rejection-policy throw sites handled at BOTH ends: the call path returns null, the scheduler path is absorbed by `scheduleGuarded`"
    - "Nullable `Future` handle so a `submit` can move inside a pre-existing `try` without rewriting its catch arms"

key-files:
  created:
    - src/test/kotlin/com/six2dez/burp/aiagent/scanner/ActiveScannerFailureIsolationTest.kt
    - src/test/kotlin/com/six2dez/burp/aiagent/scanner/ScanRequestExecutorTest.kt
  modified:
    - src/main/kotlin/com/six2dez/burp/aiagent/scanner/ActiveAiScanner.kt

key-decisions:
  - "`ThreadPoolExecutor(0, MAX, keepalive, SECONDS, SynchronousQueue(), factory, AbortPolicy())` rather than `newFixedThreadPool` — a fixed pool plus threads orphaned by `future.cancel(true)` on an uninterruptible `sendRequest` would queue every later submit behind the orphans and time out every `future.get`, degrading the scanner to 'every request returns null' with nothing logged"
  - "The `Future` handle became `var future: Future<HttpRequestResponse>? = null` so the submit could move inside the existing `try`; the two pre-existing catch arms keep their bodies and change only `future.cancel` to `future?.cancel`"
  - "`catch (_: RejectedExecutionException)` with an underscore, not a named parameter — detekt's `SwallowedException` allows `_` by its default `allowedExceptionNameRegex`, and the arm has nothing useful to interpolate (the exception's message is usually null, which is the whole reason the arm exists)"
  - "Red-before-green for `ScanRequestExecutorTest` was proved by a partial revert of the two changed hunks, not by `git checkout` of the whole file — reverting the file would also delete `scanRequestThreadFactory` and turn the suite red by non-compilation, which is the weak evidence 24-VALIDATION.md warns against"
  - "`git stash` was not used anywhere; the executor contract prohibits it in a linked worktree because `refs/stash` is shared with every sibling worktree"
  - "STATE.md and ROADMAP.md were deliberately NOT edited — this plan ran as one of two parallel wave-2 worktrees and the orchestrator is the single writer for both files"

patterns-established:
  - "A per-target log line resolves its label ABOVE the `try` that will use it, so the catch block can never throw while building its own message"
  - "A pool's contract is pinned twice: behaviourally on a locally-constructed executor of the same shape, and structurally on the comment-stripped production source, so neither half can drift alone"

requirements-completed: [REL-06]

coverage:
  - id: D1
    description: "One target failing mid-scan does not stop the active scanner from draining the rest of its queue"
    requirement: REL-06
    verification:
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/scanner/ActiveScannerFailureIsolationTest.kt#aTargetThatFailsMidScanDoesNotStopTheQueueFromDraining"
        status: pass
    human_judgment: false
  - id: D2
    description: "A per-target scan failure is logged with enough context to identify the target — the line carries `target.id` (URL + injection point + vulnerability class) and keeps the `[ActiveAiScanner] ` prefix"
    requirement: REL-06
    verification:
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/scanner/ActiveScannerFailureIsolationTest.kt#theFailureLogLineNamesTheTargetItFailedOn"
        status: pass
    human_judgment: false
  - id: D3
    description: "The queue-drain ticker keeps ticking when `exec.submit` is rejected on the scheduler thread"
    requirement: REL-06
    verification:
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/scanner/ActiveScannerFailureIsolationTest.kt#aRejectedSubmitOnTheSchedulerThreadDoesNotEndTheQueueDrainTicker"
        status: pass
    human_judgment: false
  - id: D4
    description: "A tick over an empty queue is a no-op — nothing logged to error, no scan counted (A-EDGE-2)"
    requirement: REL-06
    verification:
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/scanner/ActiveScannerFailureIsolationTest.kt#aTickOverAnEmptyQueueLogsNothingAndCompletesNoScans"
        status: pass
    human_judgment: false
  - id: D5
    description: "An active scan against a black-holing host cannot spawn threads without limit — the request pool is bounded, unqueued and aborting, and `ActiveAiScanner.kt` declares no unbounded pool"
    requirement: REL-07
    verification:
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/scanner/ScanRequestExecutorTest.kt#theScanRequestPoolShapeIsBoundedUnqueuedAndAborting"
        status: pass
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/scanner/ScanRequestExecutorTest.kt#theScannerDeclaresNoUnboundedPoolAndBuildsItsRequestPoolFromTheSharedCeiling"
        status: pass
    human_judgment: false
  - id: D6
    description: "The pool accepts exactly `Defaults.MAX_SCAN_REQUEST_THREADS` concurrent tasks and rejects the next one (A-EDGE-4, both sides)"
    requirement: REL-07
    verification:
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/scanner/ScanRequestExecutorTest.kt#theScanRequestPoolAcceptsExactlyItsCeilingAndRejectsOneMore"
        status: pass
    human_judgment: false
  - id: D7
    description: "A rejected HTTP submit degrades to a logged null return, not to an exception escaping `sendRequestWithTimeout`"
    requirement: REL-07
    verification:
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/scanner/ScanRequestExecutorTest.kt#theRequestSubmitSitsInsideTheTryThatHandlesRejection"
        status: pass
    human_judgment: false
  - id: D8
    description: "Every executor `ActiveAiScanner` creates carries a named thread factory, so a Burp thread dump is readable"
    requirement: REL-07
    verification:
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/scanner/ScanRequestExecutorTest.kt#theScanRequestFactoryNamesEveryThreadAndMarksItDaemon"
        status: pass
      - kind: other
        ref: "grep over ActiveAiScanner.kt for the four thread-name literals returns exactly 4 lines (request factory, scan worker, queue scheduler, OAST poller)"
        status: pass
    human_judgment: false

# Metrics
duration: 32min
completed: 2026-08-21
status: complete
---

# Phase 24 Plan 02: Scanner Fault Isolation & Bounded Request Pool Summary

**A failed target is now named in Burp's error log by URL, injection point and vulnerability class, and the active scanner's per-request pool is a bounded, named, daemon `ThreadPoolExecutor` whose saturation degrades to a logged `null` instead of aborting a target with a message of `null`.**

## Performance

- **Duration:** 32 min
- **Started:** 2026-08-21T13:38:00Z
- **Completed:** 2026-08-21T14:10:00Z
- **Tasks:** 3
- **Files modified:** 3 (2 created, 1 modified)

## Accomplishments

- **SC2's real deliverable landed.** The per-target catch used to log `[ActiveAiScanner] Error: <message>`,
  which named nothing. It now logs `[ActiveAiScanner] Target scan failed: <target.id>: <message>`, and
  `target.id` is URL plus injection-point name plus vulnerability class — all three identifiers SC2 asks
  for, in one containment assertion.
- **The label is resolved above the `try`, not inside it.** `val targetLabel = target.id.take(200)` sits
  immediately after the queue poll. A failure inside `target.originalRequest.request()` therefore cannot
  make the catch block throw while building its own message, which would have turned a contained
  per-target failure back into an uncontained one.
- **`requestExecutor` has a hard ceiling.** `Executors.newCachedThreadPool()` became an explicit
  `ThreadPoolExecutor` with `Defaults.MAX_SCAN_REQUEST_THREADS` as its maximum, a `SynchronousQueue`, a
  named daemon factory and `AbortPolicy`. The field's declared type stayed `ExecutorService`, so no call
  site and no part of the `shutdown` / `awaitTermination` / `shutdownNow` sequence changed.
- **The rejection path is closed at both ends.** In `sendRequestWithTimeout` the submit moved inside the
  existing `return try {` and gained a `RejectedExecutionException` arm that logs and returns `null`,
  mirroring the timeout arm exactly. On the scheduler thread, plan 24-01's `scheduleGuarded` absorbs the
  same exception — asserted here, not assumed.
- **All four scanner executors are named and daemon.** `burp-ai-agent-scan-request-<n>`,
  `burp-ai-agent-scan-worker-<n>`, `burp-ai-agent-scan-scheduler`, `burp-ai-agent-oast-poller`. Sizes,
  intervals and every other argument of the three pre-existing pools are untouched — this half is naming
  only, and it is what makes SC6's stated goal (a readable Burp thread dump) actually hold.
- **Nine assertions across two headless suites**, neither of which starts a live Burp, a live subprocess
  or a Swing component, and neither of which compares an elapsed duration to a threshold.

## Task Commits

1. **Task 1 (TDD): name the failing target** — `d95277f` (test, RED) → `eaa9613` (feat, GREEN)
2. **Task 2: bound and name `requestExecutor`, degrade rejection to null** — `70dd341` (feat)
3. **Task 3: pin bounds, naming, rejection and submit placement** — `91e96cc` (test)

## Files Created/Modified

- `src/main/kotlin/com/six2dez/burp/aiagent/scanner/ActiveAiScanner.kt` — the `targetLabel` resolution and
  enriched catch message in `processQueue`; the bounded `requestExecutor`; the restructured
  `sendRequestWithTimeout`; three named daemon factories in `startProcessing()`; and the new top-level
  `internal fun scanRequestThreadFactory()`.
- `src/test/kotlin/com/six2dez/burp/aiagent/scanner/ActiveScannerFailureIsolationTest.kt` — 4 tests
  (REL-06-F isolation, REL-06-G log content, REL-06-E ticker survival, A-EDGE-2 empty tick).
- `src/test/kotlin/com/six2dez/burp/aiagent/scanner/ScanRequestExecutorTest.kt` — 5 tests (REL-07-H naming,
  REL-07-G shape and structural, A-EDGE-4 boundary, REL-07-I source order).

## Decisions Made

### Why not a fixed pool (the prohibition, restated because it is load-bearing)

`newFixedThreadPool` is the obvious way to bound a pool and it is the wrong one here. Montoya does not
document `api.http().sendRequest` as responsive to `Thread.interrupt()`, so `future.cancel(true)` on a
timeout can orphan a request thread. With a fixed pool, orphans occupy worker slots permanently: every
later submit queues behind them and every `future.get(timeout)` expires, so the scanner silently returns
`null` for every request while still reporting a healthy scan. That is worse than the unbounded pool it
would replace. `corePoolSize = 0` plus a `SynchronousQueue` keeps the cached pool's direct hand-off — this
pool feeds a call that is already time-bounded, so a work queue in front of it would only add delay — and
`AbortPolicy` makes saturation an event rather than a silence.

### The ceiling is a constant, not a function of `maxConcurrent`

`requestExecutor` is a `val` initialised at construction; `maxConcurrent` is a mutable `var` written later
from settings. Deriving the ceiling from it would have read the default `3` and never the user's value.
`Defaults.MAX_SCAN_REQUEST_THREADS = 32` is three times the hard `coerceIn(1, 10)` ceiling on
`activeAiMaxConcurrent`, leaving headroom for orphans before any rejection occurs.

### The `Future` handle had to become nullable

The plan says to leave the existing catch arms untouched. Moving the `submit` inside the `try` makes
`future` a local declared before it, and the two pre-existing arms call `future.cancel(true)`. The minimal
honest change is `var future: Future<HttpRequestResponse>? = null` plus `future?.cancel(true)` in those two
arms — one character each, no body rewritten, no behaviour changed on either path. Recorded here so a
reader diffing against the plan text does not read it as unplanned drift.

### `catch (_: RejectedExecutionException)`, not a named parameter

detekt's `SwallowedException` is active (the existing `TimeoutException` arm sits in the baseline for
exactly this reason) and a new named-but-unused catch parameter would have added a finding — which QUAL-07
forbids absorbing into `detekt-baseline.xml`. The underscore form is allowed by the rule's default
`allowedExceptionNameRegex`. It is also the honest form: `RejectedExecutionException`'s message is usually
`null`, which is precisely why the arm writes its own line instead of interpolating one.

## Red-Before-Green Record

### REL-06-G (`ActiveScannerFailureIsolationTest`) — STRONG form

The suite was written and run first, against the unmodified `ActiveAiScanner.kt`. Three of its four tests
passed immediately and one failed:

```
ActiveScannerFailureIsolationTest > theFailureLogLineNamesTheTargetItFailedOn() FAILED
    org.opentest4j.AssertionFailedError at ActiveScannerFailureIsolationTest.kt:145

REL-06 / SC2 log-content gate: the per-target failure line must carry `target.id` — the URL, the
injection point name and the vulnerability class. ... Expected a line containing
`http://example.com/isolation?id=7_id_SQLI`.
Actual error lines: [[ActiveAiScanner] Error: induced settings failure]
    ==> expected: <true> but was: <false>

4 tests completed, 1 failed
```

After the production edit: `4 tests completed, 0 failed`.

**`aTargetThatFailsMidScanDoesNotStopTheQueueFromDraining` passed both before and after the fix, and is
reported as such.** SC2's "keeps processing" clause is already true in the pre-fix code because the
per-target `try/catch/finally` predates this phase, so that assertion is regression cover, not a gate.
`24-VALIDATION.md` §"Assertions Explicitly Ruled Out" rules it out as a *sole* SC2 assertion; it is
retained only as the paired half of REL-06-G. The same applies to REL-06-E and A-EDGE-2: the guard they
exercise landed in plan 24-01, so they are green from the first run here.

### REL-07-G and REL-07-I (`ScanRequestExecutorTest`) — STRONG form

Both structural assertions were run against a **partially reverted** `ActiveAiScanner.kt`: the two changed
hunks (the pool construction and the submit placement) restored to their pre-fix content, with
`scanRequestThreadFactory` left in place so the tree still compiled.

```
ScanRequestExecutorTest > theRequestSubmitSitsInsideTheTryThatHandlesRejection() FAILED
    REL-07 / SC6 (REL-07-I): `requestExecutor.submit(` must appear AFTER `return try {`. ...
    Found the try at line 9 and the submit at line 4 of the comment-stripped body.
        ==> expected: <true> but was: <false>

ScanRequestExecutorTest > theScannerDeclaresNoUnboundedPoolAndBuildsItsRequestPoolFromTheSharedCeiling() FAILED
    REL-07 / SC6: `Executors.newCachedThreadPool(` creates a pool with no upper bound and must not
    appear in `src/main/kotlin/com/six2dez/burp/aiagent/scanner/ActiveAiScanner.kt`. ...
        ==> expected: <0> but was: <1>

5 tests completed, 2 failed
```

Restoring the file with `git checkout HEAD -- <path>` returned the suite to `5 tests completed`, 0 failed.

**Why a partial revert rather than reverting the whole file.** The plan's acceptance criterion asks for a
whole-file stash. Reverting the whole file also deletes `scanRequestThreadFactory`, so the suite would have
failed to COMPILE — the weak form `24-VALIDATION.md` explicitly says not to claim as a gate. The partial
revert produces a compiling tree in which the two structural assertions fail on their own merits, which is
strictly stronger evidence for the same claim.

**REL-07-H (naming) is the WEAK form and is reported as such.** `scanRequestThreadFactory` did not exist
before this plan, so its assertion could only have gone red by unresolved-reference. No bypass was
exercised.

## Cache-Staleness Record

Measured, not assumed, for `ScanRequestExecutorTest` specifically:

```
# baseline
> Task :compileKotlin UP-TO-DATE
> Task :test UP-TO-DATE
# after inserting one comment line into ActiveAiScanner.kt
> Task :compileKotlin
> Task :test
```

`test` re-ran on a comment-only main-source edit, confirming that plan 24-01's
`mainSourceTreeStructuralInputs` covers this suite's two structural assertions. The probe comment was
reverted with `git checkout HEAD -- <path>`; `git status --short` is clean of that file.

## Deviations from Plan

### Instructed-command substitutions

**1. [Prohibition compliance] `git stash push <path>` replaced with a partial revert plus `git checkout HEAD -- <path>`**

- **Found during:** Task 3's red-before-green acceptance criterion, which literally instructs
  `git stash push src/main/kotlin/.../ActiveAiScanner.kt` … `git stash pop`.
- **Issue:** this plan executes inside a linked git worktree. `refs/stash` lives in the parent `.git/` and
  is shared across every worktree, so `git stash` / `git stash pop` can pop a sibling worktree's WIP into
  an isolated tree. The executor contract prohibits it outright in worktree mode, and plan 24-01 hit and
  recorded the same constraint.
- **Fix:** the two changed hunks were reverted in place, the suite run, then the file restored with
  `git checkout HEAD -- <path>`. Neither operation touches `refs/stash`.
- **Verification:** the red output above, then `git status --short` clean of that path and the suite green
  again. The criterion's intent — observe the assertion fail, restore, observe green — was met in full, and
  the partial revert is stronger evidence than the whole-file one it replaces (see above).

**2. [Plan/repo drift, no action needed] `gradle/libs.versions.toml` does not exist**

- **Found during:** task 3's `git diff --stat detekt-baseline.xml gradle/libs.versions.toml` criterion.
- **Issue:** this repository has no version catalog; `gradle/` contains only `wrapper/` and dependencies are
  declared inline in `build.gradle.kts`. The command errors rather than printing nothing. Same finding as
  24-01 deviation 5.
- **Resolution:** discharged the stronger way — `git diff --stat detekt-baseline.xml build.gradle.kts
  src/main/kotlin/com/six2dez/burp/aiagent/config/Defaults.kt` is EMPTY for this plan. No build script, no
  dependency declaration and no baseline entry was touched, and no package-manager install was run.

**3. [Orchestrator instruction] STATE.md and ROADMAP.md were not written**

- **Found during:** the executor's `state_updates` step.
- **Issue:** this plan ran as one of two parallel wave-2 worktrees. The orchestrator is the single writer
  for `.planning/STATE.md` and `.planning/ROADMAP.md` this wave; wave 1's executor wrote STATE.md and caused
  a merge conflict.
- **Resolution:** `state.advance-plan`, `state.update-progress`, `state.record-metric`, `state.add-decision`,
  `state.record-session` and `roadmap.update-plan-progress` were all skipped. `REQUIREMENTS.md` WAS updated
  (REL-06 marked complete) because no other wave-2 plan writes to it and 24-01 deliberately left REL-06 open
  pending exactly this plan's suite. **The orchestrator must run the STATE/ROADMAP updates after merge.**

### Auto-fixed Issues

**4. [Rule 3 - Blocking] ktlint `Declarations and declarations with comments should have an empty space between`**

- **Found during:** Task 2, the `ktlintCheck` half of the verify command.
- **Issue:** the multi-line `// REL-07 / SC6:` block explaining the pool shape was placed directly beneath
  `private val responseAnalyzer`, with no blank line between the two declarations.
- **Fix:** one blank line inserted before the comment block. No prose changed.
- **Files modified:** `src/main/kotlin/com/six2dez/burp/aiagent/scanner/ActiveAiScanner.kt`
- **Committed in:** `70dd341`.

**5. [Rule 3 - Blocking] the suite's own no-platform-reflection grep matched its KDoc**

- **Found during:** Task 1's acceptance greps.
- **Issue:** an acceptance criterion requires `grep -c 'java.io.DeleteOnExitHook\|java.base'` over
  `ActiveScannerFailureIsolationTest.kt` to return 0. The suite's KDoc explained *why* it does not reflect
  into that module and named it verbatim, so the grep read 1 against a correct implementation — the same
  class of defect as an unstripped structural reader counting its own documentation.
- **Fix:** the KDoc now says "a JDK platform module" and cross-references `24-VALIDATION.md`'s ruled-out
  `deleteOnExit` probe instead of naming the module. The claim is unchanged; the grep now reads 0.
- **Files modified:** `src/test/kotlin/com/six2dez/burp/aiagent/scanner/ActiveScannerFailureIsolationTest.kt`
- **Committed in:** `eaa9613`.

---

**Total deviations:** 2 auto-fixed (both Rule 3, both gate mechanics rather than correctness), plus 3
recorded command / instruction substitutions that changed no behaviour. No deviation altered an assertion,
a threshold, a call site or a design decision.

## Issues Encountered

- **`RedactionTest` failed 2 of 814 on one unfiltered `./gradlew test detekt ktlintCheck` run** and passed
  on an immediate re-run of the same command (`BUILD SUCCESSFUL`). This is the known pre-existing wall-clock
  flake against `SafeRegex`'s 50 ms deadline under CPU load, in a file this plan does not touch and does not
  reach. Plan 24-01 already logged it to
  `.planning/phases/24-scheduler-process-robustness/deferred-items.md`; it is deliberately NOT appended a
  second time, because `deferred-items.md` is shared with the file-disjoint 24-03 worktree running in this
  same wave and a duplicate entry would create a merge conflict for no new information.

## Known Stubs

None. No file created or modified by this plan contains a `TODO`, a `FIXME`, a placeholder literal, or a
component wired to an empty data source. No test is skipped: both new suites report `skipped="0"`.

## Threat Flags

**No new attack surface was introduced by this plan.** No network endpoint, no authentication or
authorization path, no file access pattern, no schema change and no trust boundary was added or moved. The
production diff changes how a failure is absorbed and how many threads may exist — never what is computed,
who may call it, or what data crosses a boundary. One new string reaches Burp's LOCAL error log; it is
analysed as `T-24-03` below and is bounded.

The plan's dispositioned threats are discharged as follows:

| Threat ID | Severity | Disposition | Status | Evidence |
|-----------|----------|-------------|--------|----------|
| T-24-02 | high | mitigate | **discharged** | Bounding `requestExecutor` with `AbortPolicy` is what makes a scheduler-thread rejection reachable at all, and the wave order guaranteed the catcher landed first: `depends_on: ["24-01"]` put `scheduleGuarded` around `processQueue` before this plan bounded anything. Guard presence was verified at source before the first edit, and `ActiveScannerFailureIsolationTest#aRejectedSubmitOnTheSchedulerThreadDoesNotEndTheQueueDrainTicker` asserts the ticker survives repeated rejections rather than assuming it. |
| T-24-03 | low | accept | **discharged as accepted, bounded** | The new line interpolates `target.id` only — URL, injection-point name, vulnerability class — capped at 200 characters, matching this file's existing `request.url().take(80)` habit. No response body, header or parameter value is interpolated, asserted by reading the single `logToError` call site. The line goes to Burp's local error log and never to an AI backend, so the redaction pipeline is out of scope by design, exactly as the register records. The residual risk (credentials in a query string reaching a local log) is unchanged from the pre-existing `:1099` line and is accepted, not newly created. |
| T-24-08 | high | mitigate | **discharged** | Hard ceiling `Defaults.MAX_SCAN_REQUEST_THREADS = 32` with `SynchronousQueue` and `AbortPolicy`, pinned from both directions: behaviourally (`maximumPoolSize`, queue type, handler type, and the accept-at-N / reject-at-N+1 boundary) and structurally (`ActiveAiScanner.kt` declares no unbounded pool factory, comment-stripped). The structural half was observed red against the pre-fix construction. |
| T-24-12 | high | mitigate | **discharged** | The submit moved inside `return try {` and a `RejectedExecutionException` arm logs and returns `null`. Pinned by `ScanRequestExecutorTest#theRequestSubmitSitsInsideTheTryThatHandlesRejection`, which asserts BOTH the source order and the presence of the arm, and which was observed red against the pre-fix ordering. Without both halves the failure mode is a whole target aborted with a message of `null`. |
| T-24-SC | high | mitigate | **discharged** | No package-manager install ran and no dependency was added. `git diff --stat detekt-baseline.xml build.gradle.kts Defaults.kt` is empty for this plan — the entire diff is two new test files and one production file. (`gradle/libs.versions.toml` does not exist in this repo; see deviation 2.) No `[ASSUMED]` or `[SUS]` package exists in this plan, so no legitimacy checkpoint applies. |

**No `threat_flag:` entries.** Nothing in this plan warrants a new register row. The one candidate — a
bounded pool creating a new `RejectedExecutionException` throw site — is not a new flag but the already
registered `T-24-02`, which is why the plan carried a `depends_on` on the guard that catches it.

## Verification Record

| Check | Result |
|-------|--------|
| `./gradlew test detekt ktlintCheck` (unfiltered) | **green** (814 tests; 2 known `RedactionTest` wall-clock flakes on one loaded run, green on immediate re-run) |
| `./gradlew test -PexcludeHeavyTests=true` (PR-gate equivalent) | **green** |
| Both new suites present under `-PexcludeHeavyTests=true` | **yes** — both produce result XML in the filtered run, so neither was named into an excluded suffix |
| `ActiveScannerFailureIsolationTest` | 4 tests, 0 failures, 0 skipped |
| `ScanRequestExecutorTest` | 5 tests, 0 failures, 0 skipped |
| `ScannerQueueBackpressureTest`, `ActiveScannerDedupTest`, `ActiveScannerQueueModelTest` | green — no scanner regression |
| `grep -c 'Target scan failed' ActiveAiScanner.kt` | 1 |
| `grep -c 'target.id.take(200)' ActiveAiScanner.kt` | 1 |
| label `val` introduced ABOVE the per-target `try` | confirmed in `git diff -U3` |
| comment-stripped `ThreadPoolExecutor(` in `ActiveAiScanner.kt` | 1 |
| `grep -c 'scanRequestThreadFactory' ActiveAiScanner.kt` | 2 (one declaration, one call site) |
| `grep -c 'RejectedExecutionException' ActiveAiScanner.kt` | 2 (import + catch arm) |
| four thread-name literals in `ActiveAiScanner.kt` | 4 lines |
| `grep -c 'Defaults.MAX_SCAN_REQUEST_THREADS' ActiveAiScanner.kt` | 1, and no bare numeric ceiling at the construction site |
| source order in `sendRequestWithTimeout` | `return try {` at :1150, `requestExecutor.submit(` at :1152 |
| `grep -c '@AfterEach' ActiveScannerFailureIsolationTest.kt` | 3, and the teardown calls `shutdown()` |
| `grep -c 'java.io.DeleteOnExitHook\|java.base' ActiveScannerFailureIsolationTest.kt` | 0 |
| `grep -c 'assertTimeoutPreemptively' ScanRequestExecutorTest.kt` | 3 |
| `grep -c 'System.currentTimeMillis\|System.nanoTime' ScanRequestExecutorTest.kt` | 0 |
| `git diff --stat detekt-baseline.xml` | empty |
| `git diff --stat build.gradle.kts Defaults.kt` | empty |
| cache-staleness probe for this suite | `test` executed after a comment-only main-source edit |

## Next Phase Readiness

- **REL-06 is now fully covered and marked complete in `REQUIREMENTS.md`.** 24-01 deliberately left it
  unchecked because its wording — *"Covered by a test that injects a throw and asserts the next tick still
  fires"* — was only fully met for the scanner once this plan's `ActiveScannerFailureIsolationTest` landed.
  It is met now: 24-01 proved the mechanism and the routing, this plan injects the scanner-specific throw
  and asserts the surviving tick.
- **REL-07 stays open.** This plan closes only the scanner half of SC6. The CLI output buffer (24-03), the
  temp-file registry (24-04) and the worker pool (24-05) are still outstanding.
- **Notes for plan 24-05.** `scanRequestThreadFactory()` is the pattern `workerPoolThreadFactory()` should
  copy: a top-level `internal fun` closing over its own `AtomicInteger`, returning a `ThreadFactory` that
  produces kebab-case `burp-ai-agent-*` daemon threads. But the worker pool's rejection contract is NOT the
  same — `AgentSupervisor` parks a long-lived stdout pump on it, so a naive `SynchronousQueue` + `AbortPolicy`
  copy will stall the extension. `24-PATTERNS.md` §"Bounded pools" records the dedicated named daemon
  `Thread` that pump should move to first.
- **The orchestrator owes STATE.md and ROADMAP.md** for this plan (see deviation 3).

## Self-Check: PASSED

Files verified present on disk:

- `src/main/kotlin/com/six2dez/burp/aiagent/scanner/ActiveAiScanner.kt` — FOUND
- `src/test/kotlin/com/six2dez/burp/aiagent/scanner/ActiveScannerFailureIsolationTest.kt` — FOUND
- `src/test/kotlin/com/six2dez/burp/aiagent/scanner/ScanRequestExecutorTest.kt` — FOUND

Commits verified in `git log`: `d95277f`, `eaa9613`, `70dd341`, `91e96cc` — all FOUND.

---
*Phase: 24-scheduler-process-robustness*
*Completed: 2026-08-21*
