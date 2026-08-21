---
phase: 24-scheduler-process-robustness
verified: 2026-08-21T19:20:00Z
status: human_needed
score: 6/6 must-haves verified
behavior_unverified: 0
overrides_applied: 0
human_verification:
  - test: "Load `Custom-AI-Agent-<version>.jar` in a real Burp session, configure a CLI backend (codex-cli or gemini-cli), and send one prompt that produces a normal-length answer."
    expected: "The full model answer is returned verbatim with no `[output truncated: ...]` marker and no error. The prompt/output temp files are gone from the OS temp directory after the call returns."
    why_human: "SC3/SC4/SC5 are verified against the extracted seams (`CliOutputBuffer`, `CliTempFileRegistry`) in pure JVM. No automated test drives a real `codex-cli`/`gemini-cli` subprocess through `CliBackend.executeInternal`, so the reader-thread/timeout-path interaction and the `finally`-block cleanup are proven at the seam, never end-to-end against a real process."
  - test: "With the extension loaded, run an active AI scan against a target with many injection points. While it runs, take a Burp thread dump (jstack on the Burp PID)."
    expected: "Thread names `burp-ai-agent-worker-N` (at most 4), `burp-ai-agent-scan-request-N` (at most 32), `burp-ai-agent-scan-worker-N`, `burp-ai-agent-scan-scheduler`, `burp-ai-agent-oast-poller` are present and bounded. No anonymous `pool-N-thread-M` growth attributable to the scanner. The scan completes and the queue drains to zero."
    why_human: "SC6's ceilings and names are asserted against locally-constructed pools of identical shape plus a structural read of `App.kt` / `ActiveAiScanner.kt`. The production pool instances are never observed under real scan load, and 'a Burp thread dump is readable' is by definition a live-Burp observation."
  - test: "Quit Burp cleanly (File > Exit) while a CLI backend call is still in flight, then inspect the OS temp directory."
    expected: "No `burp_uv_prompt_*.txt` or `burp-ai-agent-codex*.txt` files remain."
    why_human: "The exit-hook sweep was verified in a forked JVM against the real compiled `CliTempFileRegistry` (file registered, JVM exited with no `finally`/`shutdown()` call, file was gone). What is NOT covered is the same path inside a live Burp process with a real in-flight subprocess — the interleaving of Burp's own shutdown, `App.shutdown()`'s `removeShutdownHook`, and the JVM exit hook."
  - test: "Unload and reload the extension several times (Extensions > Installed > untick/retick), then check the JVM for accumulating `burp-ai-agent-cli-temp-sweep` threads and `McpScannerTaskRegistryCleaner` / `McpCollaboratorRegistryCleaner` threads."
    expected: "Exactly one `burp-ai-agent-cli-temp-sweep` hook thread at most, and it disappears on unload."
    why_human: "Extension reload cannot be simulated headlessly. Relevant because open review finding WR-04 records that the two MCP registry cleaner executors are never shut down (pre-existing, not introduced here), and WR-03 records a narrow `shutdown()`/`register()` race that could arm a hook that is never removed."
---

# Phase 24: Scheduler & Process Robustness Verification Report

**Phase Goal:** A single exception cannot silently disable a background subsystem for the rest of the Burp session, and CLI output handling is thread-safe and bounded.
**Verified:** 2026-08-21
**Status:** human_needed — all six success criteria MET by code and test evidence; four live-Burp / real-subprocess observations remain that no headless test can make.
**Re-verification:** No — initial verification.

---

## Goal Achievement

### Observable Truths (ROADMAP Success Criteria)

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| SC1 | `processQueue`, `ScannerTaskRegistry.cleanupExpired`, `CollaboratorRegistry.cleanupExpired` survive a throw and run on the next tick | ✓ **MET** | Mechanism + routing, both verified — see §SC1 below |
| SC2 | Scanner keeps processing after an induced failure on one target; failure logged with target context | ✓ **MET** | `ActiveAiScanner.kt:445,453`; `ActiveScannerFailureIsolationTest.kt:90,130` |
| SC3 | CLI stdout capture thread-safe; no unsynchronised `StringBuilder` across reader thread and timeout path | ✓ **MET** | `CliOutputBuffer.kt:64-108`; `CliBackend.kt:213,224,256,268,279`; `CliOutputBufferTest.kt:44,272` |
| SC4 | CLI output bounded, and the bound does not truncate a legitimate answer | ✓ **MET** | `Defaults.kt:61` (262 144); `CliOutputBufferTest.kt:109,272` |
| SC5 | No per-invocation `deleteOnExit()` entry; cleanup still happens on the normal path and on crash | ✓ **MET** | `CliTempFileRegistry.kt:94-99` (CR-02 fix); forked-JVM probe below |
| SC6 | `App.workerPool` and `ActiveAiScanner.requestExecutor` bounded; phase-created executors have named factories | ✓ **MET** | `App.kt:61,365`; `ActiveAiScanner.kt:95-103,1749` |

**Score:** 6/6 criteria met (0 present-but-behaviour-unverified).

---

### SC1 — Recurring tasks survive a throw

**Verdict: MET.** The literal wording ("asserted by injecting a throw" at all three sites) is discharged for one site directly and for the other two by a substitution I judge sound.

**Mechanism — behaviourally proven, not merely present.**
`GuardedScheduling.kt:96 scheduleGuarded` wraps the body in `runGuarded` (`:63`), which catches `Throwable` and never rethrows. `GuardedSchedulingTest.kt:64` schedules a real `ScheduledExecutorService` at a 10 ms delay, throws on tick 1, and waits on a `CountDownLatch(2)` for ticks 2 and 3. `:101` does the same with a `StackOverflowError`. `:235` covers a throwing log sink.

I confirmed these assertions are **falsifiable**, not green-by-construction, with a standalone JDK 21 probe (no repo files modified):

```
UNGUARDED     later-ticks-fired=false invocations=1
NARROW-CATCH  later-tick-fired=false  invocations=1
```

An unguarded `scheduleWithFixedDelay`, and a guard narrowed to `catch (Exception)` facing an `Error`, both produce exactly the `false` these tests assert must be `true`. The tests can fail.

**Routing — proven for all three sites.** `ActiveAiScanner.kt:383`, `ScannerTaskRegistry.kt:29`, `CollaboratorRegistry.kt:28` all call `scheduleGuarded`. `SchedulerGuardCoverageTest.kt:42` walks `src/main/kotlin` and asserts the set of files calling a recurring schedule directly equals exactly `{GuardedScheduling.kt, AgentSupervisor.kt, ActiveAiScanner.kt}` (the helper plus the two pre-existing inline guards). `build.gradle.kts:245-248` declares `inputs.dir("src/main/kotlin")` as `mainSourceTreeStructuralInputs`, so the guard is not cache-served.

**On the substitution — I checked the rationale rather than accepting it.** `24-01-SUMMARY.md:190` claims neither registry cleaner has an external throw lever. **Verified at source and the claim is accurate:** `ScannerTaskRegistry.kt:75-90` and `CollaboratorRegistry.kt:75-90` are each a `ConcurrentHashMap` iterator walk plus `log()`, and `log()` (`ScannerTaskRegistry.kt:97-103`) wraps the callback in `catch (_: Exception) {}`. There is genuinely nothing an external caller can make throw. Manufacturing a lever needs a new seam into `mcp/tools/`, which `T-23-06-07` forbids, and `24-VALIDATION.md` §"Assertions Explicitly Ruled Out" pre-ratified the exclusion before execution — it is not a post-hoc excuse.

I judge the substitution **discharges SC1's intent**. The behavioural evidence is taken at the exact composition point the registries use (`scheduleGuarded(...) { throwing body }`), and the only unexercised element is which body is passed — which cannot affect whether `runGuarded` catches. The structural half is additionally *stronger* than a per-site injection would be: it also fails the build on a future fourth site that bypasses the helper.

**Bonus site-specific injection.** `ActiveScannerFailureIsolationTest.kt:173` reflects a `RejectingExecutorService` into the scanner's private `executor` field, producing a real `RejectedExecutionException` on the scheduler thread inside `processQueue`, and asserts ≥2 submit attempts across separate ticks. I traced the control flow: the throw escapes `repeat(maxConcurrent)`, so one attempt per tick — the assertion genuinely requires the schedule to survive. This is a literal SC1 throw-injection for `processQueue`.

### SC2 — Scanner survives a per-target failure, and names it

**Verdict: MET.**

`ActiveAiScanner.kt:445` resolves `val targetLabel = target.id.take(200)` **above** the `try` (so the catch block itself cannot throw), and `:453` logs `[ActiveAiScanner] Target scan failed: $targetLabel: ${e.message}`. `target.id` is URL + injection point + vuln class.

`ActiveScannerFailureIsolationTest.kt:90` injects a throw through the constructor's `getSettings` lambda for target 1 of 3, then asserts `scansCompleted == 3` and `queueSize == 0`. `:130` captures `logToError` and asserts a line contains the failing `target.id`, plus that every line keeps the `[ActiveAiScanner] ` prefix.

The suite itself is honest about which half is a gate: `:44-49` records that the isolation clause was already true pre-fix and that only the log-content assertion goes red. That matches `24-VALIDATION.md` and I found no overclaim.

### SC3 — Thread-safe CLI capture

**Verdict: MET.**

`CliOutputBuffer.kt` holds a single `private val lock = Any()`; `appendLine`, `snapshot()`, `truncated` and `length` all take it. No unsynchronised accessor exists.

Wiring read directly in `CliBackend.kt`: constructed at `:213`; appended by the `burp-ai-agent-cli-reader` daemon thread at `:224`; read at all three sites — timeout path `:256`, non-zero-exit path `:268`, success path `:279`. `readerThread.join(2000)` at `:249`/`:262` can still expire while the reader appends, but every access now shares one monitor, so a happens-before edge exists.

`grep -rn "StringBuilder" CliBackend.kt` returns nothing. `CliOutputBufferTest.kt:272` pins this structurally with a ledger (0 `StringBuilder`, 1 `CliOutputBuffer()`, 3 `snapshot()`, 1 `appendLine(`). `:44` drives concurrent appends and snapshot reads asserting no torn lines.

### SC4 — Bounded output that does not corrupt a legitimate answer

**Verdict: MET — and the corruption trap flagged in the phase brief is explicitly closed.**

`Defaults.kt:61 MAX_CLI_OUTPUT_CHARS = 262_144` — 131× the 2000-char figure that belongs to the two error paths only.

The anti-corruption assertion is real: `CliOutputBufferTest.kt:109` builds a >50 000-char multi-line answer (with a fixture guard asserting the fixture actually exceeds 50 000), appends it at the **production default cap**, and asserts `truncated == false` and `snapshot() == answer` byte-identically.

The structural ledger at `CliOutputBufferTest.kt:272` guards the exact failure mode the brief warned about: it asserts `rawOutput.snapshot().trim().take(2000)` appears on **exactly 2** code lines and that `take(2000)` appears on exactly 3 — so a third `.take(2000)` reaching the success path fails the build with a message naming the corruption.

⚠ Note, not a gap: open finding **WR-02** — above 256 KiB, `snapshot()` welds `CLI_OUTPUT_TRUNCATION_MARKER` onto the value the success path treats as the model's answer, and no production code reads `CliOutputBuffer.truncated`. That is visible degradation on an extreme input, not the silent 2000-char corruption SC4 guards against; SC4 stands.

### SC5 — No per-invocation shutdown-hook entry; cleanup on the normal path and on crash

**Verdict: MET. The CR-02 fix holds, verified by test and by direct read.**

`grep -rn "deleteOnExit" src/main/kotlin src/test/kotlin` → the only surviving occurrence is a KDoc line in `CliTempFileRegistry.kt:10` describing what replaced it. Zero call sites.

`CliTempFileRegistry.kt` holds one `ConcurrentHashMap.newKeySet()` bounded by in-flight calls, and `armHook()` (`:141`) returns early when a hook is already armed — so exactly one hook, not one per invocation.

**CR-02 fix verified at source:**
```kotlin
fun deleteAndDeregister(file: File?) {
    if (file == null) return
    if (file.delete() || !file.exists()) {
        deregister(file)
    }
}
```
A `delete()` that returns `false` on a file that still exists keeps its entry, so the drain can retry. `CliBackendTempFileTest.kt:292` pins this with a deterministic fixture — a **non-empty directory**, whose `delete()` returns `false` on every platform — and asserts `sizeForTests() == 1` afterwards, with a fixture precondition assertion so it cannot pass vacuously. `:319` covers the three complementary arms (successful delete, already-gone file, `null`).

**"and on crash" — verified end-to-end, not inferred.** I ran the real compiled `CliTempFileRegistry` in a forked JDK 21 JVM: registered a temp file, then exited the JVM with **no** `deleteAndDeregister` and **no** `shutdown()` call — simulating a clean Burp quit mid-CLI-call:
```
registered exists=true hookArmed=true
--- after JVM exit ---
GONE (exit hook swept it)
```
Wiring: `CliBackend.kt:124,139` register; `:160,305,310` `deleteAndDeregister`; `App.kt:263 safeShutdownStep("CLI temp files") { CliTempFileRegistry.shutdown() }`, sequenced after the backend registry and before the worker pool (pinned by `CliBackendTempFileTest.kt:466`).

The SIGKILL residual is an accepted, KDoc-documented non-goal (D-04), not an SC clause.

### SC6 — Bounded pools with named thread factories

**Verdict: MET.**

| Pool | Construction | Bound | Factory |
|------|-------------|-------|---------|
| `App.workerPool` | `App.kt:61` `newFixedThreadPool(Defaults.MAX_WORKER_THREADS, workerPoolThreadFactory())` | 4 (`Defaults.kt:78`) | `burp-ai-agent-worker-N`, daemon (`App.kt:365`) |
| `ActiveAiScanner.requestExecutor` | `ActiveAiScanner.kt:95-103` explicit `ThreadPoolExecutor(0, MAX_SCAN_REQUEST_THREADS, 60s, SynchronousQueue, factory, AbortPolicy)` | 32 (`Defaults.kt:70`) | `burp-ai-agent-scan-request-N`, daemon (`:1749`) |
| Scanner worker pool | `ActiveAiScanner.kt:372` `newFixedThreadPool(maxConcurrent)` | `maxConcurrent` | `burp-ai-agent-scan-worker-N`, daemon |
| Service log pump | `AgentSupervisor.kt:1051` dedicated `Thread` | 1 per service | `burp-ai-agent-service-$name`, daemon (`:1063`) |
| CLI temp sweep hook | `CliTempFileRegistry.kt:144` | 1 | `burp-ai-agent-cli-temp-sweep` |

Repo-wide `grep` confirms **zero** `newCachedThreadPool`, `newWorkStealingPool`, `newVirtualThreadPerTaskExecutor` in `src/main/kotlin`.

The `AbortPolicy` throw site is handled: `sendRequestWithTimeout` (`ActiveAiScanner.kt:1142-1170`) submits **inside** the `try` and carries a dedicated `catch (_: RejectedExecutionException)` arm returning `null` with a saturation-naming log line. `ScanRequestExecutorTest.kt:209` pins the source ordering; `:127` asserts the pool accepts exactly its ceiling and rejects one more; `WorkerPoolExecutorTest.kt:123` asserts the worker pool *queues* rather than rejecting (the deliberately different contract, reasoned in `App.kt:44-59`).

The five remaining anonymous `pool-N-thread-M` sites (open finding IN-06) were **not created by this phase**, so SC6's "executors created by this phase" scope is satisfied.

---

### Behavioural Spot-Checks

| Behaviour | Command | Result | Status |
|-----------|---------|--------|--------|
| All 7 phase suites pass | `./gradlew test --tests '*GuardedSchedulingTest' … ×7` | BUILD SUCCESSFUL | ✓ PASS |
| All 7 present in PR gate (not name-excluded) | `./gradlew test -PexcludeHeavyTests=true --rerun-tasks` + result-XML scan | 7/7 PRESENT, 45 tests, 0 failures, 0 errors | ✓ PASS |
| SC1 assertions are falsifiable | standalone JDK 21 probe (unguarded + narrow-catch schedulers) | both `false` — the value the tests assert must be `true` | ✓ PASS |
| SC5 exit hook actually sweeps on JVM exit | forked JVM over `build/classes/kotlin/main`, no `finally`/`shutdown()` | file GONE after exit | ✓ PASS |
| `deleteOnExit` eliminated | `grep -rn deleteOnExit src/main src/test` | 1 KDoc mention, 0 call sites | ✓ PASS |
| No unbounded pool factories | `grep -rn newCachedThreadPool\|newWorkStealingPool\|newVirtualThread src/main/kotlin` | 0 matches | ✓ PASS |
| Structural guards not cache-served | `build.gradle.kts:245-248` `inputs.dir(...)` `mainSourceTreeStructuralInputs` | declared | ✓ PASS |
| No source modified during verification | `git status --porcelain src/ build.gradle.kts detekt-baseline.xml` | clean | ✓ PASS |

Per-suite results from the PR-gate run: `GuardedSchedulingTest` 7 · `SchedulerGuardCoverageTest` 3 · `ActiveScannerFailureIsolationTest` 4 · `CliOutputBufferTest` 8 · `CliBackendTempFileTest` 13 · `ScanRequestExecutorTest` 5 · `WorkerPoolExecutorTest` 5.

Naming constraint independently re-checked against `build.gradle.kts:253-261`: none of the 7 suite names ends in `*IntegrationTest`, `*ConcurrencyTest`, `*BackpressureTest`, `*RestartPolicyTest` or `*SupervisionTest`.

### Requirements Coverage

| Requirement | Description | Status | Evidence |
|-------------|-------------|--------|----------|
| REL-06 | Every recurring scheduled task survives an exception in its body | ✓ SATISFIED | SC1, SC2 above. The requirement's own wording ("covered by a test that injects a throw and asserts the next tick still fires") is met literally by `GuardedSchedulingTest.kt:64` and `ActiveScannerFailureIsolationTest.kt:173`. |
| REL-07 | CLI output thread-safe and bounded; no `deleteOnExit()` accumulation; bounded pools | ✓ SATISFIED | SC3, SC4, SC5, SC6 above. |

Both are marked `[x]` in `REQUIREMENTS.md:29-30`; both are genuinely satisfied. No orphaned requirements map to Phase 24.

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| — | — | `TODO`/`FIXME`/`XXX`/`TBD`/`HACK`/`PLACEHOLDER` scan across all 9 phase-touched main-source files | ℹ️ Info | **Zero matches.** No debt markers introduced. |
| `.planning/ROADMAP.md` | 320, 323-326 | Stale plan bookkeeping | ⚠️ Warning | Header reads "1/5 plans executed"; plans 24-02…24-05 still `[ ]`. All five are committed and merged (`3562dc7 chore(24): wave 4 tracking — 5/5 plans executed`) and `STATE.md:11` correctly records 5/5. Documentation-only; no code impact. |

`detekt-baseline.xml` is untouched across the phase (`git diff` empty), confirming the `@Suppress("TooGenericExceptionCaught")` was taken in-file as QUAL-07 requires.

### Open Review Findings That Bear on a Success Criterion

Both Criticals are genuinely resolved — I verified the code, not the review's status line: CR-01 in `2446da1` (`GuardedScheduling.kt:70-78`, the nested `catch (_: Throwable)` around `logError`), CR-02 in `19691c7` (`CliTempFileRegistry.kt:94-99`). 11 Warnings + 9 Info remain open by explicit decision. Those touching an SC:

| Finding | Bears on | Assessment |
|---------|----------|------------|
| WR-02 | SC4 | Truncation marker spliced into the model's answer above 256 KiB; `truncated` never read in production. Visible degradation on an extreme input, not the silent 2000-char corruption SC4 guards. **Does not block SC4.** |
| WR-10 | SC2 | A rejected submit drops the polled target and abandons the rest of that tick's batch — now silently, because the guard absorbs the throw. The *subsystem stays alive* (the phase goal), but *work is lost*. Narrow in production today (`executor` is a fixed pool with an unbounded queue). **Does not block SC2**, but it is the finding most worth closing next. |
| WR-03 | SC5 | `shutdown()`/`register()` race can arm a hook that is never removed. Narrow; requires a CLI call starting during extension unload. |
| WR-08 | SC1 | `SchedulerGuardCoverageTest`'s "really guarded" check accepts a `try` with no `catch` and matches by file name not path — weakens the *structural* half slightly. The behavioural half is unaffected. |
| WR-04 | phase goal | The two MCP registry cleaner executors are never shut down (classloader leak per reload). **Pre-existing**, not introduced here. |
| WR-05 | SC1 | `runGuarded` swallows `InterruptedException` without restoring the flag. |

### Human Verification Required

Four items — all are real-Burp or real-subprocess observations no headless test in this repo can make. See frontmatter `human_verification` for full detail.

1. **Real CLI subprocess round-trip** — SC3/SC4/SC5 are verified at the seam classes; no test drives an actual `codex-cli`/`gemini-cli` through `CliBackend`.
2. **Thread dump under real scan load** — SC6's ceilings/names are asserted on identically-shaped local pools plus a structural read; the production instances are never observed live, and "a readable thread dump" is by definition a live observation.
3. **Clean Burp quit mid-CLI-call** — the exit hook is proven in a forked JVM against the real class; the live-Burp interleaving is not.
4. **Repeated extension reload** — hook/cleaner-thread accumulation, relevant to open WR-03 and WR-04.

### Gaps Summary

**None.** All six success criteria are met with evidence I read or executed myself rather than taking from a SUMMARY.

Two things are worth stating plainly, because the brief asked me to treat SUMMARYs as claims:

- **The SUMMARY claims I spot-checked held up.** In particular `24-01-SUMMARY.md:190`'s argument that the two registry cleaners have no external throw lever is factually correct at source, and its own §Red-Before-Green Record is honest about `GuardedSchedulingTest` being red only by non-compilation ("WEAK form. Do not claim this as a gate"). I found no SUMMARY overclaiming against the tree.
- **The one bookkeeping defect is in ROADMAP.md, not in code** — four plan checkboxes are stale. Fix during ship.

The phase goal holds: every recurring scheduled task in `src/main/kotlin` now either routes through a guard proven to absorb `Throwable` and keep ticking, or is one of two grandfathered sites carrying an inline guard that a structural test enforces; CLI stdout capture is monitor-guarded and capped at 262 144 chars without truncating legitimate answers; temp-file cleanup is bounded by in-flight concurrency instead of lifetime invocation count; and both named pools are bounded.

---

_Verified: 2026-08-21_
_Verifier: Claude (gsd-verifier)_
