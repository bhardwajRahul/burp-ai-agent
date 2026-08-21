---
phase: 24-scheduler-process-robustness
plan: 01
subsystem: infra
tags: [kotlin, jvm, scheduledexecutorservice, concurrency, detekt, gradle, structural-test, junit5]

# Dependency graph
requires:
  - phase: 23-edt-confinement-ui-responsiveness
    provides: "D-02's two-places-at-once shape (shared helper + fail-fast structural check), the comment-stripping `codeLinesOf` source reader, the `tasks.test` inputs-declaration discipline, and threat T-23-06-07's do-not-widen constraint on both MCP registries"
provides:
  - "`util/GuardedScheduling.kt` — `runGuarded` (catches Throwable, logs one `[Component] <task> failed: <message>` line, never rethrows) and `ScheduledExecutorService.scheduleGuarded`"
  - "All three REL-06 recurring-schedule sites migrated: ActiveAiScanner queue drain, ScannerTaskRegistry cleaner, CollaboratorRegistry cleaner"
  - "`SchedulerGuardCoverageTest` — the structural gate that turns a fourth unguarded recurring schedule into a red build"
  - "`build.gradle.kts` `tasks.test` input `mainSourceTreeStructuralInputs` (`inputs.dir(\"src/main/kotlin\")`) — the single declaration every Phase 24 structural assertion depends on"
  - "Five shared `config/Defaults.kt` constants consumed by plans 24-02, 24-03 and 24-05"
affects: [24-02, 24-03, 24-04, 24-05, any future phase adding a recurring scheduled task]

actuals:
  tokens: 30587
  tasks: 3
  commits: 4

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Guarded recurring scheduling via a `util` extension function rather than a copy-pasted inline try/catch"
    - "Whole-tree `inputs.dir` on `tasks.test` for a tree-walking structural assertion (first in this build)"

key-files:
  created:
    - src/main/kotlin/com/six2dez/burp/aiagent/util/GuardedScheduling.kt
    - src/test/kotlin/com/six2dez/burp/aiagent/util/GuardedSchedulingTest.kt
    - src/test/kotlin/com/six2dez/burp/aiagent/util/SchedulerGuardCoverageTest.kt
  modified:
    - src/main/kotlin/com/six2dez/burp/aiagent/scanner/ActiveAiScanner.kt
    - src/main/kotlin/com/six2dez/burp/aiagent/mcp/tools/ScannerTaskRegistry.kt
    - src/main/kotlin/com/six2dez/burp/aiagent/mcp/tools/CollaboratorRegistry.kt
    - src/main/kotlin/com/six2dez/burp/aiagent/config/Defaults.kt
    - build.gradle.kts

key-decisions:
  - "The guard takes a `logError: (String) -> Unit` lambda, not a `MontoyaApi` — the two MCP registries log through their own private `::log` and must not gain a Burp dependency (T-23-06-07: do not widen either registry's reachable surface)"
  - "`runGuarded` catches `Throwable`, answered with an in-file `@Suppress(\"TooGenericExceptionCaught\")` plus a KDoc justification rather than a baseline entry — `detekt-baseline.xml` is untouched (QUAL-07)"
  - "One whole-tree `inputs.dir(\"src/main/kotlin\")` rather than per-file declarations: a tree-walking allowlist whose inputs list known files is blind to a scheduler introduced in an undeclared file, which is the exact defect it exists to catch"
  - "SC1's 'assert by injecting a throw' is satisfied per-site only for `ActiveAiScanner.processQueue`; the two registry cleaners are covered by mechanism-plus-routing (see the dedicated subsection below)"
  - "Red-before-green for the structural gate was proved with a single-file `git checkout <ref> -- <path>` instead of the plan's `git stash push`, because `git stash` shares one stack across all worktrees"

patterns-established:
  - "Recurring schedules: call `ScheduledExecutorService.scheduleGuarded(component, task, logError, initialDelay, delay, unit) { body() }`; a direct `scheduleAtFixedRate`/`scheduleWithFixedDelay` outside the three-file allowlist fails the build"
  - "Phase 24 numeric literals live in `config/Defaults.kt` as `const val` (detekt's MagicNumber ignores const) with a comment naming the consumer and the derivation"

requirements-completed: []

coverage:
  - id: D1
    description: "A recurring scheduled task whose body throws still fires on its next tick — the schedule is not silently cancelled for the rest of the Burp session"
    requirement: REL-06
    verification:
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/util/GuardedSchedulingTest.kt#aThrowOnTheFirstTickDoesNotCancelTheRecurringSchedule"
        status: pass
    human_judgment: false
  - id: D2
    description: "The guard catches Throwable, not only Exception, so an Error on a tick also fails to cancel the schedule"
    requirement: REL-06
    verification:
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/util/GuardedSchedulingTest.kt#anErrorOnTheFirstTickDoesNotCancelTheRecurringScheduleEither"
        status: pass
    human_judgment: false
  - id: D3
    description: "A suppressed tick failure is visible in Burp's error log with the `[Component] <task> failed: <message>` prefix, and a successful or no-op tick logs nothing"
    requirement: REL-06
    verification:
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/util/GuardedSchedulingTest.kt#aSuppressedFailureIsLoggedOnceWithTheComponentPrefixAndTheTaskLabel"
        status: pass
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/util/GuardedSchedulingTest.kt#aSuccessfulOrNoOpTickRecordsNothing"
        status: pass
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/util/GuardedSchedulingTest.kt#aThrowableWithANullMessageStillRecordsExactlyOneLine"
        status: pass
    human_judgment: false
  - id: D4
    description: "All three REL-06 recurring-schedule sites (ActiveAiScanner.processQueue, ScannerTaskRegistry.cleanupExpired, CollaboratorRegistry.cleanupExpired) route through the guard, and no file under src/main/kotlin can introduce a fourth unguarded one without the build going red"
    requirement: REL-06
    verification:
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/util/SchedulerGuardCoverageTest.kt#everyRecurringScheduleUnderMainSourceIsOnTheGuardAllowlist"
        status: pass
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/util/SchedulerGuardCoverageTest.kt#everyAllowlistedRecurringScheduleOutsideTheHelperOpensATryImmediatelyBelowIt"
        status: pass
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/util/SchedulerGuardCoverageTest.kt#oneShotScheduleCallSitesAreDeliberatelyOutOfRel06Scope"
        status: pass
    human_judgment: false
  - id: D5
    description: "TTL expiry behaviour of both MCP registries is unchanged by the migration — only fault containment changed, not cleanup semantics"
    requirement: REL-06
    verification:
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/mcp/tools/RegistryTtlTest.kt (regression control, unmodified)"
        status: pass
    human_judgment: false
  - id: D6
    description: "The structural gate cannot be served from a stale Gradle build cache — a comment-only edit to a main source file re-runs it"
    requirement: REL-06
    verification:
      - kind: other
        ref: "manual cache probe: baseline UP-TO-DATE -> append comment to GuardedScheduling.kt -> `> Task :test` executed; counterfactual with the declaration commented out -> `> Task :test UP-TO-DATE`"
        status: pass
    human_judgment: false
  - id: D7
    description: "The phase's five shared Defaults constants and the single tasks.test input declaration exist, so plans 24-02..24-05 never touch Defaults.kt or build.gradle.kts"
    requirement: REL-06
    verification:
      - kind: other
        ref: "grep -c 'mainSourceTreeStructuralInputs' build.gradle.kts == 1; grep -c 'withPropertyName' build.gradle.kts == 8; the five-constant grep over Defaults.kt == 5"
        status: pass
    human_judgment: false

# Metrics
duration: 33min
completed: 2026-08-21
status: complete
---

# Phase 24 Plan 01: Scheduler Guard Summary

**`runGuarded`/`scheduleGuarded` in `util/GuardedScheduling.kt` catch `Throwable` on every recurring tick and log one `[Component] <task> failed:` line, all three REL-06 schedule sites are migrated, and a whole-tree structural gate backed by the build's first `inputs.dir` makes a fourth unguarded scheduler a red build.**

## Performance

- **Duration:** 33 min
- **Started:** 2026-08-21T13:00:00Z
- **Completed:** 2026-08-21T13:33:00Z
- **Tasks:** 3
- **Files modified:** 8 (3 created, 5 modified)

## Accomplishments

- **The defect is closed at the mechanism level.** A recurring task whose body throws no longer
  disappears for the rest of the Burp session. `GuardedSchedulingTest` proves it against a REAL
  `ScheduledExecutorService` with a REAL throw and a REAL next tick — the failure mode is the JDK's
  own documented suppression behaviour, which a mock scheduler cannot reproduce.
- **`Throwable`, not `Exception`.** Tick 1 throwing a `StackOverflowError` still leaves tick 2 firing.
  This is the half that a narrower catch would have silently left open.
- **All three REL-06 sites migrated** — `ActiveAiScanner.startProcessing()`'s queue drain,
  `ScannerTaskRegistry`'s cleaner and `CollaboratorRegistry`'s cleaner — with intervals, `TimeUnit`s
  and both named daemon `ThreadFactory` lambdas untouched.
- **A fourth unguarded site is now a red build.** `SchedulerGuardCoverageTest` walks every `.kt` file
  under `src/main/kotlin`, strips comments, and pins the allowlist to exactly
  `GuardedScheduling.kt`, `AgentSupervisor.kt`, `ActiveAiScanner.kt`.
- **The gate cannot be cache-served.** `inputs.dir("src/main/kotlin")` is the build's first
  directory-scoped test input; the counterfactual below shows the guard would otherwise be served
  UP-TO-DATE in exactly the commit that breaks it.
- **Wave 2 and wave 4 are unblocked without a build-script conflict.** The five `Defaults` constants
  and the one `tasks.test` declaration land here so 24-02, 24-03, 24-04 and 24-05 never touch
  `Defaults.kt` or `build.gradle.kts`.

## Task Commits

1. **Task 1 (tracer, TDD): End-to-end guarded tick** — `e002c66` (test, RED) → `a618a76` (feat, GREEN)
2. **Task 2: Route both MCP registry cleaners through the guard** — `0853b93` (fix)
3. **Task 3: Structural allowlist gate, Gradle input, shared Defaults** — `9a00f69` (test)

## Files Created/Modified

- `src/main/kotlin/com/six2dez/burp/aiagent/util/GuardedScheduling.kt` — `runGuarded` +
  `ScheduledExecutorService.scheduleGuarded`; the file's block comment carries the failure-mode
  narrative moved out of `AgentSupervisor.kt:81-83`.
- `src/test/kotlin/com/six2dez/burp/aiagent/util/GuardedSchedulingTest.kt` — 5 headless tests
  (REL-06-A/B/C plus the two A-EDGE-2 empty cases).
- `src/test/kotlin/com/six2dez/burp/aiagent/util/SchedulerGuardCoverageTest.kt` — 3 structural
  assertions (allowlist, per-call-site inline guard, A-EDGE-1 one-shot positive control).
- `src/main/kotlin/com/six2dez/burp/aiagent/scanner/ActiveAiScanner.kt` — queue drain migrated to
  `scheduleGuarded`; 0 ms initial delay / 500 ms delay / `MILLISECONDS` preserved.
- `src/main/kotlin/com/six2dez/burp/aiagent/mcp/tools/ScannerTaskRegistry.kt`,
  `.../CollaboratorRegistry.kt` — cleaners migrated inside the existing `init` block, using each
  object's existing private `::log`.
- `src/main/kotlin/com/six2dez/burp/aiagent/config/Defaults.kt` — five new `const val`s.
- `build.gradle.kts` — one `inputs.dir` declaration, additions only.

## Decisions Made

### SC1 assertion shape for the two registry cleaners

ROADMAP SC1 reads *"Asserted by injecting a throw"* for all three sites. This plan performs that
per-site injection for **one** of the three, deliberately. Recorded here so a verifier reading SC1
literally does not score it a partial miss:

1. **`ActiveAiScanner.processQueue` is the site SC1's throw-injection wording is satisfied for.**
   The mechanism it runs through is asserted end-to-end by
   `GuardedSchedulingTest.aThrowOnTheFirstTickDoesNotCancelTheRecurringSchedule` and
   `...anErrorOnTheFirstTickDoesNotCancelTheRecurringScheduleEither`, and plan 24-02 adds the
   scanner-specific injection (`ActiveScannerFailureIsolationTest`, REL-06-E) once bounding
   `requestExecutor` creates the `RejectedExecutionException` throw site on the scheduler thread that
   does not exist today.
2. **Neither `ScannerTaskRegistry.cleanupExpired()` nor `CollaboratorRegistry.cleanupExpired()`
   contains an external throw lever.** Verified at source during this plan: each body is an iterator
   walk over a `ConcurrentHashMap` plus a `log()` call whose own `catch (_: Exception) {}` swallows
   callback failures. Recorded in `24-RESEARCH.md` §Pitfall 7.
3. **Manufacturing one would require a new test seam into either registry, which threat
   `T-23-06-07` forbids.** That threat records that an unintended `McpSupervisor.stop()` already
   clears both registries; widening either object's reachable surface to let a test inject a fault is
   exactly the direction it rules out. The substitution is therefore a consequence of a ratified
   security constraint, not a shortcut — the `git diff -U0` over `mcp/tools/` shows no visibility
   modifier changed, no new `fun`, and no `…ForTests` seam added.
4. **The guarantee for these two is carried by mechanism plus routing.**
   `GuardedSchedulingTest` proves the mechanism (catches `Throwable`, next tick fires, logs exactly
   once); `SchedulerGuardCoverageTest` proves both files route through `scheduleGuarded` and that no
   file may bypass it. This is the shape `24-VALIDATION.md` §"Assertions Explicitly Ruled Out"
   already ratifies — that section explicitly rules out *"Assert the registry cleaner's next tick
   fires without an injected lever"* on the grounds of the 5-minute interval, the private field and
   the absent throw site. The substitution traces to that pre-existing ruling, not to a post-hoc
   excuse.

### Other decisions

- **The guard's log sink is a lambda, not a `MontoyaApi`.** `ActiveAiScanner` passes
  `{ api.logging().logToError(it) }`; both registries pass their own private `::log`. Nothing in
  `mcp/tools/` gained a Burp dependency or a new member.
- **`@Suppress("TooGenericExceptionCaught")` in-file, baseline untouched.** `git diff --stat
  detekt-baseline.xml` is empty. The KDoc states why the catch cannot be narrowed and why the
  baseline is not an option even in principle (its IDs are exact strings keyed per file, so a new
  file's finding is never covered by an existing entry).
- **Whole-tree `inputs.dir` over per-file entries** (resolves RESEARCH assumption A6). Accepted cost:
  `tasks.test` re-runs on any main-source edit.

## Red-Before-Green Record

**REL-06-A/B/C (`GuardedSchedulingTest`) — WEAK form. Do not claim this as a gate.**
The suite went red only by **non-compilation**:

```
e: .../GuardedSchedulingTest.kt:69:22 Unresolved reference 'scheduleGuarded'.
e: .../GuardedSchedulingTest.kt:134:9 Unresolved reference 'runGuarded'.
```

`24-VALIDATION.md` §Red-Before-Green Gate classifies this explicitly: a suite that cannot compile
has not exercised a bypass. It is committed as its own RED commit (`e002c66`) so the ordering is
auditable, but the evidence is weaker than a genuinely failing assertion and is reported as such.

**REL-06-D (`SchedulerGuardCoverageTest`) — STRONG form.** Reverting `ScannerTaskRegistry.kt` alone
to its pre-migration content turned **two** assertions red against a compiling tree:

```
SchedulerGuardCoverageTest > everyRecurringScheduleUnderMainSourceIsOnTheGuardAllowlist() FAILED
    Actual: [ActiveAiScanner.kt, AgentSupervisor.kt, GuardedScheduling.kt, ScannerTaskRegistry.kt]
SchedulerGuardCoverageTest > everyAllowlistedRecurringScheduleOutsideTheHelperOpensATryImmediatelyBelowIt() FAILED
    ScannerTaskRegistry.kt calls a recurring schedule at code line 26 ... opens no `try {` within the next 4 code lines
3 tests completed, 2 failed
```

Restoring the migrated file returned the suite to green (`3 tests completed`, 0 failed).

## Cache-Staleness Record

Both directions were measured, not assumed.

**With the declaration (required check):**

```
# baseline
> Task :compileKotlin UP-TO-DATE
> Task :test UP-TO-DATE
# after appending one comment line to GuardedScheduling.kt
> Task :compileKotlin
> Task :test
```

**Without the declaration (counterfactual — the declaration commented out):**

```
# baseline
> Task :compileKotlin UP-TO-DATE
> Task :test UP-TO-DATE
# after appending one comment line to GuardedScheduling.kt
> Task :compileKotlin
> Task :test UP-TO-DATE
```

`compileKotlin` re-runs either way because the source text changed, but its output is byte-identical,
so without `inputs.dir` the `test` task's cache key survives and the structural guard never runs —
the measured 22-09 defect, reproduced against this exact guard. Both probe comments were removed and
the declaration restored; `git diff build.gradle.kts` is additions-only.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] detekt `UseCheckOrError` on the new test suite**

- **Found during:** Task 1, the `detekt` half of the verify command.
- **Issue:** `throw IllegalStateException("first tick blew up")` as the sole statement of an `if`
  block fires `UseCheckOrError`. The test-source exclusion that covers `TooGenericExceptionCaught`
  does not cover this rule, so `detekt` failed with 1 weighted issue.
- **Fix:** replaced with `error("first tick blew up")`, which throws the same
  `IllegalStateException` with the same message. No behavioural change to the test.
- **Files modified:** `src/test/kotlin/com/six2dez/burp/aiagent/util/GuardedSchedulingTest.kt`
- **Verification:** `./gradlew detekt` exits 0; `detekt-baseline.xml` unchanged.
- **Committed in:** `e002c66` (the RED commit, before the GREEN commit).

**2. [Rule 3 - Blocking] ktlint `no-consecutive-comments` on `GuardedScheduling.kt`**

- **Found during:** Task 1, the `ktlintCheck` half of the verify command.
- **Issue:** the plan calls for both a file-level narrative KDoc and a KDoc on `runGuarded`. ktlint
  1.5.0 rejects a KDoc immediately preceded by another KDoc (`a KDoc may not be preceded by a KDoc`).
- **Fix:** the file-level narrative is a plain block comment rather than KDoc; the reason is stated
  in the comment itself so the next editor does not "fix" it back. All prose the plan required is
  preserved verbatim.
- **Files modified:** `src/main/kotlin/com/six2dez/burp/aiagent/util/GuardedScheduling.kt`
- **Verification:** `./gradlew ktlintCheck` green.
- **Committed in:** `a618a76`.

**3. [Rule 3 - Blocking] ktlint `function-expression-body` on `SchedulerGuardCoverageTest.kt`**

- **Found during:** Task 3.
- **Issue:** `filesWithRecurringSchedule()`'s wrapped expression body tripped
  *"First line of body expression fits on same line as function signature"*.
- **Fix:** joined to one line (within the 250-char limit).
- **Committed in:** `9a00f69`.

### Instructed-command substitutions

**4. [Prohibition compliance] `git stash push <path>` replaced with `git checkout <ref> -- <path>`**

- **Found during:** Task 3's red-before-green acceptance criterion, which literally instructs
  `git stash push src/.../ScannerTaskRegistry.kt` … `git stash pop`.
- **Issue:** this plan executes inside a linked git worktree. `refs/stash` is stored in the **parent**
  `.git/` and is shared across every worktree, so `git stash`/`git stash pop` can pop a sibling
  worktree's WIP and contaminate an isolated working tree. The executor contract prohibits `git stash`
  in worktree mode outright.
- **Fix:** the same single-file revert was performed with
  `git checkout a618a76 -- src/main/kotlin/.../ScannerTaskRegistry.kt`, then restored with
  `git checkout HEAD -- <same path>`. Both are single-file operations that never touch `refs/stash`.
- **Verification:** the red output above, then `git status --short` clean of that path and the suite
  green again. The acceptance criterion's intent — observe the assertion fail, restore, observe green
  — was met in full.

**5. [Plan/repo drift, no action needed] `gradle/libs.versions.toml` does not exist**

- **Found during:** Task 2 and Task 3 acceptance checks, both of which assert
  `git diff --stat gradle/libs.versions.toml` is empty.
- **Issue:** this repository has no version catalog — `gradle/` contains only `wrapper/`, and
  dependencies are declared inline in `build.gradle.kts`. The command errors with
  `fatal: no such path in the working tree` rather than printing nothing.
- **Resolution:** T-24-SC is discharged the stronger way instead: `git diff build.gradle.kts` for
  this plan is **additions only, and every added line is a comment or the `inputs.dir` declaration** —
  no line touches the `dependencies { }` block. No package-manager install was run in this plan, so
  no package-legitimacy checkpoint applies.

---

**Total deviations:** 3 auto-fixed (all Rule 3, all lint/static-analysis blockers), plus 2 recorded
command/repo substitutions that changed no behaviour.
**Impact on plan:** none on scope or design. Every auto-fix was a house-style gate, not a
correctness change; no deviation altered an assertion, a threshold, or a call site.

## Issues Encountered

- **`RedactionTest.windowedScanRedactsJsonPairAcrossEveryBoundaryAlignment` failed once** on the
  first unfiltered `./gradlew test detekt ktlintCheck` run (1 of 805). It passed in isolation and the
  full gate passed green on an immediate re-run. It is the known pre-existing wall-clock flake
  against `SafeRegex`'s 50 ms deadline, in a file this plan does not touch. Out of scope per the
  executor's scope boundary; logged to
  `.planning/phases/24-scheduler-process-robustness/deferred-items.md`.

## Threat Flags

**No new attack surface was introduced by this plan.** No network endpoint, no auth path, no file
access pattern, no schema change, and no trust boundary was added or moved. The plan is pure fault
containment: five call sites changed how a failure is absorbed, never what is computed, who may call
it, or what crosses a boundary. Nothing under `mcp/tools/` gained a member or a visibility widening
(`git diff -U0` over that directory is the evidence, and it is an acceptance criterion of task 2).

The plan's dispositioned threats are discharged as follows:

| Threat ID | Severity | Disposition | Status | Evidence |
|-----------|----------|-------------|--------|----------|
| T-24-01 | high | mitigate | **discharged** | `runGuarded` catches `Throwable` and logs (`GuardedSchedulingTest` 5/5); `SchedulerGuardCoverageTest` pins the three-file allowlist so a fourth unguarded site is a red build. The aggravating factor — total silence — is closed by the `[Component] <task> failed:` line. |
| T-24-02 | high | mitigate | **discharged by wave order; stays live for wave 2** | This plan is wave 1 and lands the guard BEFORE any executor is bounded. `ActiveAiScanner.kt`'s `exec.submit` still sits outside a `try`, but the scheduler tick around it now runs through `scheduleGuarded`, so the `RejectedExecutionException` a bounded pool will introduce can no longer cancel the queue drain. Plans 24-02 and 24-05 must keep their `depends_on` on this plan. |
| T-24-10 | medium | mitigate | **discharged** | Edit confined to both existing `init` blocks. `git diff -U0 src/main/kotlin/com/six2dez/burp/aiagent/mcp/tools/` shows one import and one call-shape change per file, no visibility modifier touched, no new `fun`, no test seam. Both named daemon factories intact (`McpScannerTaskRegistryCleaner`, `McpCollaboratorRegistryCleaner`). |
| T-24-11 | medium | mitigate | **discharged** | `inputs.dir("src/main/kotlin")` as `mainSourceTreeStructuralInputs`, plus the two-directional cache-staleness measurement recorded above — including the counterfactual showing the guard IS cache-served without it. |
| T-24-SC | high | mitigate | **discharged** | No package-manager install ran. No dependency added: `git diff build.gradle.kts` is additions-only and every added line is a comment or the `inputs.dir` declaration; the `dependencies { }` block is untouched. (`gradle/libs.versions.toml` does not exist in this repo — see deviation 5.) |

No `threat_flag:` entries. Nothing in this plan warrants a new register row.

## Verification Record

| Check | Result |
|-------|--------|
| `./gradlew test detekt ktlintCheck` (unfiltered) | **green** (805 tests; one known `RedactionTest` flake on an earlier loaded run, green on re-run) |
| `./gradlew test -PexcludeHeavyTests=true` (PR-gate equivalent) | **green**, 793 tests |
| Both new suites present under `-PexcludeHeavyTests=true` | **yes** — `GuardedSchedulingTest` and `SchedulerGuardCoverageTest` both produce result XML in the filtered run, so neither was named into an excluded suffix |
| `GuardedSchedulingTest` | 5 tests, 0 failures, 0 skipped |
| `SchedulerGuardCoverageTest` | 3 tests, 0 failures, 0 skipped |
| `RegistryTtlTest` + `McpTool*` | green — TTL semantics unchanged |
| `ActiveScannerDedupTest`, `ScannerQueueBackpressureTest` | green — the `:340` migration changed no scanner behaviour |
| `git diff --stat detekt-baseline.xml` | empty |
| `grep -c 'catch (e: Throwable)' GuardedScheduling.kt` | 1 |
| `grep -c '@Suppress("TooGenericExceptionCaught")' GuardedScheduling.kt` | 1 |
| `grep -c 'scheduleGuarded(' ActiveAiScanner.kt` | 1 |
| comment-stripped `scheduleGuarded(` in each registry | 1 and 1 |
| `grep -c 'McpScannerTaskRegistryCleaner' / 'McpCollaboratorRegistryCleaner'` | 1 and 1 |
| `grep -c 'mainSourceTreeStructuralInputs' build.gradle.kts` | 1 |
| `grep -c 'withPropertyName' build.gradle.kts` | 8 (7 pre-existing + 1) |
| five-constant grep over `Defaults.kt` | 5 |
| `git diff build.gradle.kts` | additions only |

## Next Phase Readiness

**Wave 2 (24-02, 24-03) is unblocked.** The gate this plan exists to be is in place:

- Any executor bounded in a later plan now has a guarded scheduler tick above it, so a saturation
  `RejectedExecutionException` on the scheduler thread is absorbed and logged instead of permanently
  cancelling the queue drain. **24-02 and 24-05 must not drop their `depends_on` on 24-01.**
- `Defaults.MAX_SCAN_REQUEST_THREADS`, `SCAN_REQUEST_THREAD_KEEPALIVE_SECONDS` (24-02),
  `MAX_CLI_OUTPUT_CHARS`, `CLI_OUTPUT_TRUNCATION_MARKER` (24-03) and `MAX_WORKER_THREADS` (24-05) are
  declared and committed, so no later plan needs to edit `Defaults.kt`.
- `mainSourceTreeStructuralInputs` covers REL-07-D, REL-07-F and REL-07-G as well as REL-06-D, so no
  later plan needs to edit `build.gradle.kts` — which is what keeps waves 2-4 free of build-script
  merge conflicts.

**Notes for later plans:**

- Adding a recurring schedule anywhere under `src/main/kotlin` without `scheduleGuarded` now fails
  `SchedulerGuardCoverageTest`. The allowlist is three names; changing it is a deliberate act and the
  failure message says so.
- Do **not** write the literal tokens `scheduleAtFixedRate(` / `scheduleWithFixedDelay(` in a
  same-line trailing comment inside a main-source file — the allowlist's comment stripping is
  line-leading only, which is why the migrated files reference the guard by name instead.
- `REL-06` in `REQUIREMENTS.md` is intentionally **left unchecked**. Its wording — *"Covered by a test
  that injects a throw and asserts the next tick still fires"* — is only fully met for the scanner
  once 24-02's `ActiveScannerFailureIsolationTest` (REL-06-E/F/G) lands. Marking it complete here
  would overclaim; see the SC1 subsection above.

## Self-Check: PASSED

Files verified present on disk:

- `src/main/kotlin/com/six2dez/burp/aiagent/util/GuardedScheduling.kt` — FOUND
- `src/test/kotlin/com/six2dez/burp/aiagent/util/GuardedSchedulingTest.kt` — FOUND
- `src/test/kotlin/com/six2dez/burp/aiagent/util/SchedulerGuardCoverageTest.kt` — FOUND
- `.planning/phases/24-scheduler-process-robustness/deferred-items.md` — FOUND

Commits verified in `git log`: `e002c66`, `a618a76`, `0853b93`, `9a00f69` — all FOUND.

---
*Phase: 24-scheduler-process-robustness*
*Completed: 2026-08-21*
