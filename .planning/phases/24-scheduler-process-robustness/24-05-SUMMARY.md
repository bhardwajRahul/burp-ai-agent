---
phase: 24-scheduler-process-robustness
plan: 05
subsystem: infra
tags: [kotlin, jvm, threadpool, threadfactory, daemon-thread, concurrency, structural-test, junit5]

# Dependency graph
requires:
  - phase: 24-scheduler-process-robustness
    plan: 01
    provides: "`Defaults.MAX_WORKER_THREADS` (the ceiling this plan applies) and the `tasks.test` input `mainSourceTreeStructuralInputs`, without which both structural assertions in this suite are cache-served and never run"
  - phase: 24-scheduler-process-robustness
    plan: 04
    provides: "`App.shutdown()`'s `safeShutdownStep(\"CLI temp files\")` step, whose position between `\"Backend registry\"` and `\"Worker pool\"` this plan must not disturb and now pins with an assertion"
  - phase: 24-scheduler-process-robustness
    plan: 02
    provides: "`scanRequestThreadFactory()` and `ScanRequestExecutorTest` — the factory shape mirrored here and the sibling suite whose structure this one follows"
  - phase: 23-edt-confinement-ui-responsiveness
    provides: "D-05 (unbounded-duration work belongs on a dedicated daemon thread, flag set explicitly), D-14 (move the caller, do not restructure the component), and the ledger-message assertion style"
provides:
  - "`App.workerPool` bounded at `Defaults.MAX_WORKER_THREADS` via `Executors.newFixedThreadPool(n, workerPoolThreadFactory())` — the last unbounded pool in main source is gone"
  - "`workerPoolThreadFactory(): ThreadFactory` — top-level `internal fun` in `App.kt`, daemon threads named `burp-ai-agent-worker-<n>`"
  - "The service stdout pump moved off `workerPool` onto a dedicated daemon `Thread` named `burp-ai-agent-service-<name>`"
  - "`AgentSupervisor.monitorExec` named `burp-ai-agent-supervisor-monitor`"
  - "`WorkerPoolExecutorTest` — 5 headless tests; the two structural ones turn a re-introduced log pump or a reverted pool shape into a red build"
affects: [any future submit to App.workerPool, any future change to AgentSupervisor.startService or App.shutdown()'s step order]

actuals:
  tokens: 7207
  tasks: 3
  commits: 4

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "A never-returning reader loop gets its own named daemon `Thread`, never a slot on a shared bounded pool"
    - "Two pools in the same phase get two different saturation contracts on purpose — the queue/rejection shape follows the caller's ability to survive a throw, not a house style"

key-files:
  created:
    - src/test/kotlin/com/six2dez/burp/aiagent/WorkerPoolExecutorTest.kt
  modified:
    - src/main/kotlin/com/six2dez/burp/aiagent/App.kt
    - src/main/kotlin/com/six2dez/burp/aiagent/supervisor/AgentSupervisor.kt

key-decisions:
  - "The pump moved BEFORE the pool was bounded, in that order, with the ordering re-verified by grep between the two commits. Bounding first would have parked one of four worker threads per managed service permanently and queued every auto-restart task behind them forever (T-24-09) — a stall with nothing in the error log."
  - "`workerPool` keeps a fixed pool with an unbounded `LinkedBlockingQueue` and NO rejection policy, deliberately unlike `requestExecutor`'s `SynchronousQueue` + `AbortPolicy` from 24-02. The submit in `AgentSupervisor.scheduleRestart` is not inside a try, so a rejection would propagate uncaught and kill auto-restart (T-24-18). The suite's boundary message says so explicitly, in both directions, so a later reader does not 'fix' the inconsistency."
  - "The structural assertion on `App.kt` is scoped to the construction LINE, not to the file. File-scoped, the `workerPoolThreadFactory()` check would have been satisfied by the factory's own declaration further down and kept passing against a pool that no longer used it — a self-satisfying assertion."
  - "Red-before-green used a SURGICAL revert of App.kt's construction line rather than reverting the whole file. A whole-file revert deletes `workerPoolThreadFactory` and the suite fails to compile, which is the weak red the plan warned about; the surgical revert produced a genuine compiling red for the App structural test."
  - "`git stash push` (the plan's literal instruction for the red probe) was replaced with `git checkout <ref> -- <path>` plus a surgical edit: `refs/stash` is shared across every linked worktree, so a stash here can pop a sibling worktree's WIP. Same substitution 24-01, 24-03 and 24-04 made."

patterns-established:
  - "Work submitted to `App.workerPool` must be short and bursty. Anything that reads a stream to EOF, waits on a process, or otherwise runs for a caller-controlled duration gets its own named daemon thread — `WorkerPoolExecutorTest` fails the build if a second `workerPool.submit` appears in `AgentSupervisor.kt`."

requirements-completed: [REL-07]

coverage:
  - id: D1
    description: "`App.workerPool` is bounded — it cannot spawn threads without limit"
    requirement: REL-07
    verification:
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/WorkerPoolExecutorTest.kt#theWorkerPoolShapeIsBoundedAtTheSharedCeiling"
        status: pass
      - kind: structural
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/WorkerPoolExecutorTest.kt#appDeclaresNoUnboundedPoolAndBuildsItsWorkerPoolFromTheSharedCeiling"
        status: pass
    human_judgment: false
  - id: D2
    description: "Bounding `workerPool` does not stall the extension: no task submitted to it occupies a thread for the lifetime of a service process"
    requirement: REL-07
    verification:
      - kind: structural
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/WorkerPoolExecutorTest.kt#theSupervisorKeepsOnlyTheAutoRestartSubmitOnTheBoundedWorkerPool"
        status: pass
    human_judgment: false
  - id: D3
    description: "A managed service's stdout is still pumped and still logged, from a named daemon thread that never blocks extension unload"
    requirement: REL-07
    verification:
      - kind: structural
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/WorkerPoolExecutorTest.kt#theSupervisorKeepsOnlyTheAutoRestartSubmitOnTheBoundedWorkerPool"
        status: pass
      - kind: regression
        ref: "./gradlew test --tests '*Supervisor*' --tests '*Agent*' --tests '*Backend*'"
        status: pass
    human_judgment: true
    rationale: "The daemon flag and the thread name are source-asserted, and the pump body is proved unchanged by diff. That the pump still emits `[name] line` into Burp's output for a REAL `ollama serve` process is NOT asserted by any test — the plan forbids spawning a real service process, so no automated coverage observes a live pump. The structural gate proves the mechanism was moved intact; it does not prove the moved mechanism still works end to end against a live subprocess."
  - id: D4
    description: "Every thread `workerPool` creates carries a readable name in a Burp thread dump"
    requirement: REL-07
    verification:
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/WorkerPoolExecutorTest.kt#theWorkerPoolFactoryNamesEveryThreadAndMarksItDaemon"
        status: pass
    human_judgment: false
  - id: D5
    description: "Saturation queues rather than throwing, so the auto-restart path cannot be killed by a rejection"
    requirement: REL-07
    verification:
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/WorkerPoolExecutorTest.kt#theWorkerPoolAcceptsItsCeilingConcurrentlyAndQueuesTheNextTaskInsteadOfRejectingIt"
        status: pass
    human_judgment: false
  - id: D6
    description: "`App.shutdown()`'s worker-pool step, its 5-second awaitTermination and its shutdownNow fallback keep working unchanged, and 24-04's CLI-temp-files step keeps its position"
    requirement: REL-07
    verification:
      - kind: structural
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/WorkerPoolExecutorTest.kt#appDeclaresNoUnboundedPoolAndBuildsItsWorkerPoolFromTheSharedCeiling"
        status: pass
      - kind: command
        ref: "git diff -U0 src/main/kotlin/com/six2dez/burp/aiagent/App.kt (no change inside the Worker pool step)"
        status: pass
    human_judgment: true
    rationale: "`shutdown()` needs a live MontoyaApi, so no unit test executes it. The source-order assertion proves the step order; the diff proves the block's body is byte-identical. Neither observes an actual unload."

duration: "~40 min"
completed: 2026-08-21
status: complete
---

# Phase 24 Plan 05: Bound and Name the Worker Pool Summary

`App.workerPool` went from an unbounded `newCachedThreadPool` to a fixed pool of `Defaults.MAX_WORKER_THREADS` named daemon threads — but only after the service stdout pump, which occupied a worker thread for the entire lifetime of a managed service process, was moved onto its own dedicated daemon thread. The order was the whole plan.

**Duration:** ~40 min · **Tasks:** 3/3 · **Files:** 1 created, 2 modified · **Commits:** 4

## Accomplishments

**The stdout pump no longer lives on the shared pool.** `AgentSupervisor.startService` used to `workerPool.submit { … reader.forEachLine { … } }`. `forEachLine` returns when the stream reaches EOF — i.e. when the service process dies — so that task held a worker thread for the process's whole life. With two reachable call sites (`ollama-serve`, `lmstudio-server`) up to two threads could be occupied indefinitely. The pump now runs on `Thread(task, "burp-ai-agent-service-$name")` with `isDaemon = true` set as its own explicit statement, following `CliBackend`'s reader-thread shape. The pump body, its `catch (e: Exception)` and its `safeLogOutput("[$name] output stream closed: …")` message are byte-identical — this was a caller move, not a restructure (Phase 23 D-14).

**`App.workerPool` is bounded and named.** `Executors.newFixedThreadPool(Defaults.MAX_WORKER_THREADS, workerPoolThreadFactory())`, with the field type pinned to `ExecutorService` so no call site changed. The new top-level `internal fun workerPoolThreadFactory()` produces daemon threads named `burp-ai-agent-worker-<n>`, mirroring 24-02's `scanRequestThreadFactory()` rather than sharing a helper with it. `App.shutdown()` is untouched: 24-04's `"CLI temp files"` step keeps its position and the `"Worker pool"` step keeps its `shutdown()` / `awaitTermination(5, SECONDS)` / `shutdownNow()` sequence.

**The saturation contract is documented at the call site and pinned by a test.** A ~15-line comment above the construction records why a fixed pool with an unbounded queue rather than 24-02's explicit `ThreadPoolExecutor`, and names both rejected alternatives with their specific failure modes: a throwing policy would kill the auto-restart path (the submit in `scheduleRestart` is not inside a try), and a caller-runs policy would have run the never-returning pump inline on the calling thread — possibly the EDT that Phase 23 spent eight plans clearing. The comment discusses `CallerRunsPolicy` in prose; no policy object is constructed, which is why the acceptance criterion greps the comment-filtered file.

**`WorkerPoolExecutorTest` — 5 headless tests, no live `App`, no live Burp, no real service process.** Naming, ceiling, the queue-on-saturation boundary, and two structural gates on `App.kt` and `AgentSupervisor.kt`. The class KDoc names `WorkerPoolSupervisionTest` and `WorkerPoolRestartPolicyTest` specifically as the two suffix traps that would have silently removed this suite from the PR gate.

**`AgentSupervisor.monitorExec` is named** `burp-ai-agent-supervisor-monitor`. Size and interval unchanged; naming only.

## Task Commits

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | Move the service log pump off the worker pool | `d27e6c9` | `AgentSupervisor.kt` |
| 2 | Bound and name `App.workerPool` | `ea87505` | `App.kt` |
| 3 | Pin bounds, naming, and the pump's separation | `dd12514` | `WorkerPoolExecutorTest.kt` (new) |

## Red-Before-Green Evidence

The plan's literal instruction was `git stash push <two files>`. **`git stash` was not used** — `refs/stash` is shared across every linked worktree, so a stash here can pop a sibling worktree's WIP. Substituted with `git checkout 81d16ae -- <path>` plus one surgical edit. Same substitution 24-01, 24-03 and 24-04 made.

A straight whole-file revert of `App.kt` deletes `workerPoolThreadFactory`, so the suite would not compile — the weak "red by non-compilation" the plan explicitly warns against. Instead the App-side probe reverted **only the construction line** back to `Executors.newCachedThreadPool()`, keeping the factory declaration so the suite still compiled and the assertion could genuinely fail.

**Probe (both applied at once):**
- `App.kt` construction line → `Executors.newCachedThreadPool()` (factory declaration kept)
- `AgentSupervisor.kt` → whole file at `81d16ae` (pre-fix); `grep -c 'workerPool.submit'` confirmed **2**

**Result — both structural tests genuinely RED, compiling:**

```
WorkerPoolExecutorTest > appDeclaresNoUnboundedPoolAndBuildsItsWorkerPoolFromTheSharedCeiling() FAILED
    org.opentest4j.AssertionFailedError at WorkerPoolExecutorTest.kt:194

WorkerPoolExecutorTest > theSupervisorKeepsOnlyTheAutoRestartSubmitOnTheBoundedWorkerPool() FAILED
    org.opentest4j.AssertionFailedError at WorkerPoolExecutorTest.kt:265

5 tests completed, 2 failed
```

**After restoring both files:** `BUILD SUCCESSFUL`, 5 tests, 0 failures.

**REL-07-H is the weaker form and is reported as such.** The naming test asserts on `workerPoolThreadFactory()`, a symbol this plan creates. Against the genuinely pre-fix `App.kt` it is red only by non-compilation — that is weaker evidence than the two structural gates above, and `24-VALIDATION.md` says so. It is not claimed as a gate. The same applies to the ceiling test, which references `Defaults.MAX_WORKER_THREADS`.

**Cache-staleness check.** Appended a comment line to `App.kt`, re-ran `--tests '*WorkerPoolExecutorTest'`, and Gradle reported `> Task :test` as **executed** (not `UP-TO-DATE`, not `FROM-CACHE`). 24-01's `mainSourceTreeStructuralInputs` works. Comment reverted.

## Phase-Closing Gate

| Check | Result |
|-------|--------|
| `./gradlew test -PexcludeHeavyTests=true` | `BUILD SUCCESSFUL` |
| `./gradlew test detekt ktlintCheck shadowJar` | `BUILD SUCCESSFUL` (see the flake note below) |
| `git diff --stat detekt-baseline.xml` across the whole phase (`e002c66~1..HEAD`) | empty |
| `git diff --stat build.gradle.kts` for this plan | empty |

**All seven suites present in the filtered report:**

| Suite | Tests | Failures |
|-------|-------|----------|
| `GuardedSchedulingTest` | 5 | 0 |
| `SchedulerGuardCoverageTest` | 3 | 0 |
| `ActiveScannerFailureIsolationTest` | 4 | 0 |
| `ScanRequestExecutorTest` | 5 | 0 |
| `CliOutputBufferTest` | 8 | 0 |
| `CliBackendTempFileTest` (rewrite) | 11 | 0 |
| `WorkerPoolExecutorTest` (new) | 5 | 0 |

None was named into the `-PexcludeHeavyTests` exclusion list.

## Deviations from Plan

### 1. [Rule 3 - Blocker] `git stash` replaced with `git checkout <ref> -- <path>` for the red probe

- **Found during:** Task 3
- **Issue:** The plan's acceptance criterion says `git stash push <files>`. `refs/stash` is a single stack shared across the main checkout and every linked worktree, so a stash/pop pair here can silently apply a sibling worktree's WIP.
- **Fix:** `git checkout 81d16ae -- AgentSupervisor.kt` plus a surgical single-line edit to `App.kt`; restored with `git checkout HEAD -- <both>`.
- **Verification:** Both structural tests red under the probe, green after restore, `git status` clean.
- **Commit:** n/a (probe was transient; no source change survived it)

### 2. [Rule 1 - Wrong criterion, strengthened] The `workerPoolThreadFactory()` structural check was self-satisfying as written

- **Found during:** Task 3
- **Issue:** The plan's behaviour spec says to assert that `workerPoolThreadFactory()` "appears" in comment-stripped `App.kt`. Written that way it also matches the factory's own declaration line, `internal fun workerPoolThreadFactory(): ThreadFactory {` — so the assertion would keep passing after the construction site stopped using the factory. Same hazard for `Defaults.MAX_WORKER_THREADS`. This is the third genuinely-wrong acceptance criterion the phase's executors have hit.
- **Fix:** Both checks were narrowed to the construction **line** rather than the file: locate the line containing `Executors.newFixedThreadPool(`, then assert that same line carries the ceiling constant and the factory call. What the criterion *meant* — "the production site is built from the shared ceiling with the named factory" — is now what is actually asserted. The criterion was strengthened, not weakened; a comment in the test records why.
- **Verification:** Red probe A (construction line reverted to `newCachedThreadPool()`, factory declaration deliberately left in place) fails this test. Under the plan's literal wording it would have passed.
- **Commit:** `dd12514`

### 3. [Rule 3 - Blocker] `gradle/libs.versions.toml` does not exist in this repo

- **Found during:** Task 2
- **Issue:** The plan's prohibition and a Task 3 acceptance criterion both reference `gradle/libs.versions.toml`; `git diff --stat` on it fails with `no such path in the working tree`. The repo declares dependencies directly in `build.gradle.kts` and has no version catalog. `gradle/` contains only `wrapper/`.
- **Fix:** The criterion's *intent* — no dependency was added — was verified against the real dependency surface instead: `git diff --stat build.gradle.kts` is empty for this plan, and across the whole phase the only `build.gradle.kts` change is 24-01's `mainSourceTreeStructuralInputs` input declaration. `T-24-SC` is discharged.
- **Verification:** `git diff --stat e002c66~1 HEAD -- build.gradle.kts` shows the 14-line 24-01 change and nothing else.
- **Commit:** n/a (documentation-only finding)

### 4. [Rule 3 - Blocker] Two ktlint formatting fixes

- **Found during:** Tasks 1 and 3
- **Issue:** `ktlintMainSourceSetCheck` required a blank line before the commented `monitorExec` declaration; `ktlintTestSourceSetCheck` rejected the two-line expression body of `newWorkerPool()`.
- **Fix:** Added the blank line; wrapped the cast onto a continuation line.
- **Verification:** `./gradlew ktlintCheck` exits 0.
- **Commits:** `d27e6c9`, `dd12514`

**Total deviations:** 4 (1 wrong-criterion strengthened, 3 blockers auto-fixed). **Impact:** none on the shipped behaviour. Deviation 2 makes the delivered gate strictly stronger than the plan specified.

## Requirement Closure

**REL-07 is marked complete.** All three clauses are genuinely closed and were re-verified on disk at the end of this plan, not assumed from sibling summaries:

| Clause | Closed by | Verified how |
|--------|-----------|--------------|
| CLI output capture thread-safe and bounded — no unsynchronised `StringBuilder` | 24-03 | `grep -n 'StringBuilder\|CliOutputBuffer' CliBackend.kt` → one hit, `CliOutputBuffer()`; zero `StringBuilder` |
| `deleteOnExit()` no longer accumulates per invocation | 24-04 | `grep -rn 'deleteOnExit' src/main/kotlin` → one hit, and it is a KDoc line in `CliTempFileRegistry` explaining what it replaced |
| Unbounded `newCachedThreadPool()` replaced with bounded pools | 24-02 + **this plan** | `grep -rn 'newCachedThreadPool\|newWorkStealingPool\|newVirtualThreadPerTaskExecutor' src/main/kotlin` → **zero hits** |

`REQUIREMENTS.md` line 30 flipped to `[x]` via `requirements.mark-complete`, which reported `ready` (no sibling plan in this phase still declares REL-07 without a SUMMARY).

**REL-06 was already closed by 24-01.** With both, Phase 24's requirement set is complete.

## Threat Flags

| Flag | File | Description |
|------|------|-------------|
| — | — | No new security-relevant surface. This plan opens no network endpoint, adds no auth path, touches no file-access pattern and changes no schema at a trust boundary. Every edit is thread-lifecycle: one task moved from a shared pool to a dedicated daemon thread, one pool given a ceiling and a factory, one scheduler given a thread name. |

**Threat register dispositions from the plan, all `mitigate`, all discharged:**

| Threat | Disposition | Evidence |
|--------|-------------|----------|
| T-24-09 (DoS — bounding a pool that hosts a never-returning task) | mitigated | Task ordering enforced by the precondition grep between commits `d27e6c9` and `ea87505`; pinned by `theSupervisorKeepsOnlyTheAutoRestartSubmitOnTheBoundedWorkerPool`, observed red at 2 submits |
| T-24-17 (DoS — unbounded cached pool) | mitigated | Fixed ceiling + named daemon factory; `maximumPoolSize` / `corePoolSize` asserted; zero unbounded pool factories left in main source |
| T-24-18 (DoS — a rejection policy that kills the auto-restart path) | mitigated | Fixed pool with an unbounded queue makes rejection unreachable; the boundary test asserts the overflow task is accepted, queued (`queue.size == 1`) and later runs |
| T-24-19 (DoS — non-daemon pump thread holds the JVM open) | mitigated | `pumpThread.isDaemon = true` as its own statement; asserted structurally and by the Task 1 grep gate |
| T-24-SC (Tampering — dependency graph) | mitigated | No dependency added; `build.gradle.kts` untouched by this plan. The plan's stated artefact `gradle/libs.versions.toml` does not exist in this repo (deviation 3) |

## Issues Encountered

**One unidentified intermittent test failure, not reproduced.** The first `./gradlew test detekt ktlintCheck shadowJar` run reported `Execution failed for task ':test' — There were failing tests`. The suite name was not captured because the output was tailed, and the XML results were overwritten by the next run before it could be read. **Three subsequent full runs, two of them `--rerun-tasks` forced, were all green**, as was `test -PexcludeHeavyTests=true`. This project has a recorded `RedactionTest` wall-clock flake that fires under CPU load via `SafeRegex`'s 50 ms deadline, and the failing run started immediately after a full filtered suite had saturated the machine — that is the probable cause, but it is **not** asserted here because the evidence was lost. Logged to `WINDOWS.md` so it is not silently swallowed.

Nothing in this plan can plausibly cause it: no test added here touches redaction, and all three of this plan's source edits are thread-lifecycle changes to code no other suite exercises concurrently.

## Estimate vs Actual

The plan estimated 52 000 tokens. Actual, measured on the same scale (`git diff 81d16ae..HEAD -- src/` = 28 829 chars ÷ 4): **7 207**. A ~7× overshoot. This is the phase-wide direction rather than a one-off — 24-01 estimated 78 000 against 30 587 actual, 24-04 estimated 70 000 against 9 449. All four plans carried `confidence: low`. The number is recorded unrounded; the pattern is the useful signal.

## Next Phase Readiness

Phase 24 is complete: 5 of 5 plans have summaries, all six success criteria are closed, and both REL-06 and REL-07 are marked complete in `REQUIREMENTS.md`. No blockers for Phase 25 (SEC-07).

## Self-Check: PASSED

- `src/test/kotlin/com/six2dez/burp/aiagent/WorkerPoolExecutorTest.kt` — FOUND
- `src/main/kotlin/com/six2dez/burp/aiagent/App.kt` — FOUND, bounded construction present
- `src/main/kotlin/com/six2dez/burp/aiagent/supervisor/AgentSupervisor.kt` — FOUND, 1 `workerPool.submit`
- Commits `d27e6c9`, `ea87505`, `dd12514` — all FOUND in `git log`
- All task acceptance criteria re-run and passing
- Plan-level `<verification>` re-run: filtered and unfiltered gates both green, seven suites present, `detekt-baseline.xml` unchanged across the phase
