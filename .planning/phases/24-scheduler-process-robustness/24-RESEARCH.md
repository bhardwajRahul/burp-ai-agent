# Phase 24: Scheduler & Process Robustness - Research

**Researched:** 2026-08-21
**Domain:** JVM concurrency hardening inside a Burp Montoya extension — recurring-task fault
containment, cross-thread stream capture, executor bounding, JVM shutdown-hook lifecycle
**Confidence:** HIGH (every load-bearing claim is either read from this repo's source this session,
or read from the JDK 21 `src.zip` shipped with the project's own toolchain)

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

Copied verbatim from `.planning/phases/24-scheduler-process-robustness/24-CONTEXT.md` §Implementation
Decisions. Only the temp-file half (SC5) was discussed and locked.

- **D-01:** **Replace the per-invocation `deleteOnExit()` with one shutdown hook that sweeps a
  live-file registry.** The registry holds only temp files belonging to in-flight CLI calls; the
  existing `finally` block (`CliBackend.kt:288-307`) removes a file's entry when it deletes the
  file. Bounded by concurrent CLI calls (normally 0–1), not by lifetime invocation count.

  Rejected alternatives, with reasons: a **dedicated temp subdirectory swept at load** (D-01 option
  B) handles the hard-kill case natively but adds directory lifecycle and a wider blast radius;
  **dropping the net entirely** and relying on the `finally` alone is the smallest diff but gives up
  the one case the hook genuinely covers — a clean Burp quit while a CLI call is in flight.
  `DELETE_ON_CLOSE` was ruled out at source, not by preference: `codex-cli` writes `outputFile`
  as an external process, so the JVM cannot hold it open.

  — **Reversibility:** reversible — the registry is a private seam behind two call sites in one file.

- **D-02:** **The registry and the hook live in a new `internal object` in the `cli` package**
  (alongside `CliBackend.kt`), not in a private companion inside `CliBackend`. This follows the
  file's own established extraction pattern — `buildTimeoutMessage` (`CliBackend.kt:848`) and
  `buildCopilotCommand` (`:878`) are top-level `internal fun`s with their own headless suites
  (`CliTimeoutMessageTest`, `CopilotCommandBuilderTest`).

  The deciding factor is assertability: a companion-object registry can only be exercised by driving
  a real CLI subprocess, which is the un-assertable-seam problem Phase 23 hit repeatedly. A separate
  object gives the rewritten `CliBackendTempFileTest` a seam it can drive in pure JVM. Secondary:
  `CliBackend.kt` is already 1043 lines / 44 KB.

  — **Reversibility:** reversible — file-level extraction, no published contract.

- **D-03:** **The hook is registered lazily on first temp-file creation, and on `App.shutdown()`
  the extension both calls `Runtime.removeShutdownHook` AND drains the registry**, deleting any
  leftovers. This closes two gaps at once:

  1. **Reload accumulation.** A hook that is never removed re-registers on every extension reload
     and pins a dead classloader — the same defect class as the `deleteOnExit()` leak, only
     coarser. SC5's wording ("no never-removed shutdown-hook entry") reads against leaving it.
  2. **Unload-mid-call.** The shutdown hook does *not* fire on extension unload while Burp keeps
     running; only the `App.shutdown()` drain covers a CLI call in flight at unload.

  The drain must be idempotent with the `finally` block — both may run for the same file.
  `App.shutdown()` already owns this kind of ordering (`App.kt:221` `mainTab?.shutdown()` →
  `:233` `mcpSupervisor.shutdown()` → `:237` `workerPool.shutdown()`), and Phase 23 touched that
  exact sequence, so the insertion point is known and recently exercised.

  Lazy (not eager) registration means a session that never uses a CLI backend pays nothing.

  — **Reversibility:** reversible — one registration site, one shutdown site.

- **D-04:** **The hard-kill residual is accepted and named in the object's KDoc, not papered over.**
  A registry that lives in JVM memory cannot reap files orphaned by SIGKILL or power loss, because
  neither the `finally` nor the shutdown hook runs. The residual is bounded at **at most one temp
  file per in-flight CLI call**, in the OS's own temp directory, which the OS reaps on its own
  schedule.

  Rejected: a **bounded prefix sweep at load** (delete `burp_uv_prompt_` / `burp-ai-agent-codex`
  files older than an age threshold). It would reap previous crashed sessions, but a second
  concurrent Burp instance can legitimately own live files matching those prefixes, which makes the
  age threshold load-bearing rather than cosmetic. Following Phase 23's discipline
  (`SettingsPanel.kt:70-75`), the KDoc must name the window it does **not** close rather than
  overclaim.

  — **Reversibility:** reversible — the sweep can be added later without touching D-01–D-03.

### Claude's Discretion

Three gray areas were identified and deliberately **not** discussed. The planner and researcher own
them. The measured findings below are inputs, not decisions — nothing here is locked.

- **Scheduler guard shape (SC1, SC2).** Three call sites are unguarded:
  `ActiveAiScanner.kt:341` (`processQueue()`), `ScannerTaskRegistry.kt:26` and
  `CollaboratorRegistry.kt:25` (both `cleanupExpired()`). Two guarded sites already exist and are
  the idiom to copy: `AgentSupervisor.kt:81-93` (which carries the explanatory comment about
  `scheduleAtFixedRate` silently cancelling on an uncaught throw) and the OAST poller at
  `ActiveAiScanner.kt:348-353`. Open: inline try/catch per site vs a shared wrapper plus a
  structural check (the Phase 23 D-02 shape). Also open: whether SC2's "keeps processing its queue"
  needs per-target isolation *inside* the tick — `CONVENTIONS.md` §Error Handling already names that
  idiom ("`AiScanCheck` wraps each payload test in `try/catch` … and continues to the next payload")
  — and what field identifies the failing target in the log line.

- **CLI output buffer (SC3, SC4).** `CliBackend.kt:209` `rawOutput` is a plain `StringBuilder`,
  appended by the `burp-ai-agent-cli-reader` thread (`:216-224`) and read by the timeout path at
  `:252` and `:264` after `readerThread.join(2000)` — which can time out while the reader is still
  appending.

  ⚠ **Measured trap the planner must not step on:** `CliBackend.kt:275` does
  `stripAnsiCodes(rawOutput.toString())` and that value **is the real model response**, not a
  diagnostic tail. Only the two error paths take `.take(2000)`. Any bound on the buffer must
  therefore be generous enough never to truncate a legitimate answer — bounding at 2000 would
  silently corrupt every CLI backend's output.

- **Pool bounds and saturation policy (SC6).** `App.kt:38` `workerPool` and
  `ActiveAiScanner.kt:73` `requestExecutor` are both `Executors.newCachedThreadPool()`. Open: fixed
  pool sized from `maxConcurrent`/CPU count vs a `ThreadPoolExecutor` with a bounded queue, and the
  rejection policy (CallerRuns / Abort / log-and-drop) — saturation behaviour is the part that
  changes observable behaviour, since a scan that silently drops work is worse than one that applies
  backpressure. Named thread factories are required by SC6 and there is already a house pattern for
  them: `ScannerTaskRegistry.kt:21-23` and `CollaboratorRegistry.kt:20-22` both name their cleaner
  thread and set `isDaemon = true`.

### Deferred Ideas (OUT OF SCOPE)

- **Bounded prefix sweep of orphaned temp files at extension load** — rejected in D-04 because a
  second concurrent Burp instance can own live files matching `burp_uv_prompt_` /
  `burp-ai-agent-codex`, making the age threshold load-bearing. Revisit only if stray temp files are
  observed in practice.
- **Dedicated `burp-ai-agent/` temp subdirectory** — the D-01 option B alternative. It is the only
  design that closes the hard-kill gap without an age heuristic; worth reconsidering if the accepted
  residual ever becomes a real complaint.
- **Passive scanner dedup cache unbounded per-session growth** (`CONCERNS.md:125`) — an unbounded-
  growth issue of the same family, but not named by REL-06 or REL-07 and out of scope here.
</user_constraints>

---

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| REL-06 | "Every recurring scheduled task survives an exception in its body — `ActiveAiScanner.processQueue`, `ScannerTaskRegistry.cleanupExpired` and `CollaboratorRegistry.cleanupExpired` each keep running after a throw, matching the guard already present on `AgentSupervisor.checkHealth` and the OAST poller. Covered by a test that injects a throw and asserts the next tick still fires." `[VERIFIED: .planning/REQUIREMENTS.md:29]` | §Finding 1 (complete inventory of recurring sites — there are exactly 5, 3 unguarded), §Finding 2 (JDK-confirmed failure mode), §Finding 3 (`processQueue` has no reachable throw site on the scheduler thread *today* — the injection strategy must account for this), §Pattern 1 (`runGuarded` + structural check), §Validation Architecture rows REL-06-A…D |
| REL-07 | "CLI output capture is thread-safe and bounded — no unsynchronised `StringBuilder` shared across the reader thread and the timeout path, and no unbounded accumulation of a chatty CLI's output. `deleteOnExit()` no longer accumulates one shutdown-hook entry per CLI invocation. Unbounded `newCachedThreadPool()` use is replaced with bounded pools so an active scan cannot spawn threads without limit." `[VERIFIED: .planning/REQUIREMENTS.md:30]` | §Finding 5 (buffer hazard + where the cap belongs), §Finding 6 (`deleteOnExit` leak shape verified in JDK source; a correction to D-03's stated rationale), §Finding 7 (`workerPool` has a *permanently-occupying* task — naive bounding stalls it), §Finding 8 (`requestExecutor` sizing and the `submit` throw site outside the try), §Validation Architecture rows REL-07-A…F |
</phase_requirements>

---

## Summary

This phase is almost entirely **closing gaps in mechanisms this repo already has**, not inventing
new ones. The guard idiom is written twice already; the named-daemon-thread-factory idiom is written
twice already; the `finally`-based temp-file cleanup is complete already. The research value is
therefore concentrated in five places where the obvious implementation is **wrong in a way that only
shows up at runtime or in review**, and in one place where the phase's own CONTEXT.md is factually
mistaken about the test baseline.

The five traps, in descending order of cost-if-missed:

1. **`ActiveAiScanner.processQueue()` has no reachable throw site on the scheduler thread today** —
   every statement that can plausibly throw is already inside the per-target `try/catch` at
   `ActiveAiScanner.kt:391-401`. SC1's "asserted by injecting a throw" therefore cannot be satisfied
   for `processQueue` by poisoning its body from outside. But SC6 *creates* one: bounding an executor
   makes `exec.submit(…)` throw `RejectedExecutionException` on the scheduler thread. SC1 and SC6
   are coupled, and doing SC6 without SC1 would make the scanner strictly worse.
2. **Bounding `App.workerPool` naively stalls the extension.** `AgentSupervisor.kt:1037` submits a
   task that pumps a service process's stdout with `reader.forEachLine { … }` — it occupies its
   worker thread for the entire lifetime of that process. At most two such tasks exist
   (`ollama-serve`, `lmstudio-server`), so a bounded pool must either exceed 2 by a comfortable
   margin *or*, better, those two pumps must move to dedicated named daemon `Thread`s first.
3. **The 2000-char figure in SC4 is a red herring, and it is a *head*, not a tail.** `String.take(2000)`
   returns the FIRST 2000 characters. `CliBackend.kt:275`'s full `rawOutput.toString()` is the actual
   model answer. A bound must be generous (recommendation: 262,144 chars) and must preserve the head
   so the two error messages keep their current bytes.
4. **`CliBackendTempFileTest`'s two "deleteOnExit is registered" tests are not blocking — they are
   vacuous.** Measured on the project's own JDK 21.0.12: the reflection into `java.io.DeleteOnExitHook`
   throws `InaccessibleObjectException`, which the test's own `catch (_: Exception) { true }` swallows
   into a pass. They will stay green after D-01 removes `deleteOnExit()`. CONTEXT.md's §"Blocking test
   constraint" is wrong on the facts. They still must be replaced — they provide zero coverage today —
   but the planner must not budget a task around "keeping the build green".
5. **New files that `catch (Exception)` / `catch (Throwable)` will fail `./gradlew detekt`.**
   `TooGenericExceptionCaught` is active, the baseline is exact-string keyed by file, and QUAL-07
   forbids growing the baseline. The house answer is `@Suppress("TooGenericExceptionCaught")` with a
   KDoc justification (`SettingsPanelSettingsIO.kt:284-293`).

**Primary recommendation:** build one `internal` guard helper + one structural allowlist test (the
Phase 23 D-02 "two places at once" shape), do SC6 in the order *separate the long-lived pumps →
bound → name*, and treat the CLI buffer as a small `internal class` with a `Defaults` constant so it
is unit-testable without a subprocess.

---

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Recurring-task fault containment (SC1) | Shared JVM utility (`util` package) | Each scheduling owner (`ActiveAiScanner`, both registries, `AgentSupervisor`) | A guard that lives in each owner is a guard three people can forget. Phase 23 D-02 already ruled: helper + structural check. |
| Per-target scan failure isolation + diagnostics (SC2) | `ActiveAiScanner` (scanner tier) | — | Already owned there (`:391-401`); this phase only enriches the log line. Not a shared concern. |
| CLI stdout capture buffer (SC3, SC4) | `backends/cli` package (new `internal class`) | `Defaults` (config tier) owns the cap constant | Subprocess-boundary concern, local to one backend family. `Defaults` owns magic numbers (detekt `MagicNumber` is active with 651 baselined entries). |
| CLI temp-file lifecycle (SC5) | `backends/cli` package (new `internal object`, per D-02) | `App` (extension lifecycle tier) calls the drain | Locked by D-02/D-03. |
| Executor bounding + naming (SC6) | Each executor's owning class (`App`, `ActiveAiScanner`) | Shared named-`ThreadFactory` helper | The two pools have *different* workload shapes and must not share a sizing policy. A shared factory helper is fine; a shared pool is not. |
| Long-lived service-log pumping (SC6 prerequisite) | `AgentSupervisor` — dedicated named daemon `Thread` | — | Occupancy is unbounded in time; it does not belong in a bounded pool. Precedent: Phase 23 D-05, and `CliBackend.kt:216-226`. |

---

## Standard Stack

This phase adds **no new dependencies**. Everything needed is in `java.util.concurrent` /
`java.lang` on the JVM 21 toolchain the project already pins.

### Core

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| `java.util.concurrent` (JDK) | 21 | `ScheduledExecutorService`, `ThreadPoolExecutor`, `RejectedExecutionHandler`, `ThreadFactory` | `[VERIFIED: .planning/codebase/CONVENTIONS.md:308-309 as cited in 23-CONTEXT.md]` — *"coroutines are used **only** in the MCP layer; everywhere else use `java.util.concurrent`"*. Binding. |
| `java.lang.StringBuffer` (JDK) | 21 | Thread-safe character accumulation | JDK 21 `StringBuilder` KDoc, verbatim: *"Instances of `StringBuilder` are not safe for use by multiple threads. If such synchronization is required then it is recommended that `StringBuffer` be used."* `[VERIFIED: JDK 21 src.zip java.base/java/lang/StringBuilder.java:70-72]` |
| JUnit Jupiter | 6.0.3 | Test framework | `[VERIFIED: build.gradle.kts:57]` — `testImplementation("org.junit.jupiter:junit-jupiter:6.0.3")` |
| mockito-kotlin | 5.4.0 | `mock<MontoyaApi>(defaultAnswer = Answers.RETURNS_DEEP_STUBS)` | `[VERIFIED: build.gradle.kts:59]` — `testImplementation("org.mockito.kotlin:mockito-kotlin:5.4.0")`. Already the mechanism that makes `ActiveAiScanner` headlessly instantiable (`ScannerQueueBackpressureTest.kt:21-27`). |

### Supporting

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| `org.junit.jupiter.api.assertTimeoutPreemptively` | 6.0.3 | Bound a polling wait so a stall reports categorically instead of hanging CI | Every SC1/SC2 test that waits on a background tick. Already used this way in `SettingsPersistQueueTest.kt:17`. |
| `java.util.concurrent.CountDownLatch` | JDK 21 | Deterministic tick counting without wall-clock thresholds | Preferred over `Thread.sleep` + elapsed-time assertions — see §Pitfall 4. |

### Alternatives Considered

| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| `StringBuffer` / a custom bounded buffer | `java.util.concurrent.LinkedBlockingQueue<String>` + drain | Already the idiom used by the *other* CLI reader in the same file (`CliBackend.kt:607` `outputQueue = LinkedBlockingQueue<String>()`, `:608` `lastLines = ArrayDeque<String>(50)`) `[VERIFIED: src/main/kotlin/com/six2dez/burp/aiagent/backends/cli/CliBackend.kt:607-608]`. Bounds naturally if constructed with a capacity, but changes the shape of every read site (`:252`, `:264`, `:275`) from "give me the string" to "drain then join". Larger diff for the same guarantee. |
| A purpose-built bounded buffer class | Plain `StringBuffer` with no cap | Satisfies SC3 with a one-word diff but leaves SC4 unmet. SC4 explicitly requires bounding. |
| A shared `runGuarded` helper | Inline `try/catch` at each of the three sites | Smallest diff, matches the two existing guarded sites byte-for-byte. But it gives nothing that stops a *fourth* unguarded site appearing — the exact objection Phase 23 D-02 already litigated and resolved in favour of helper + structural check. |
| `ThreadPoolExecutor` with a bounded queue | `Executors.newFixedThreadPool(n)` | `newFixedThreadPool` uses an **unbounded** `LinkedBlockingQueue` `[CITED: docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/Executors.html]` — *"reuses a fixed number of threads operating off a shared unbounded queue"*. It bounds threads (which is literally what SC6 asks for) but not memory. Acceptable for `workerPool`; see §Finding 7 for why the queue matters less there than the occupancy does. |

**Installation:** none. No `dependencies {}` change; no `gradle/libs.versions.toml` change.

---

## Package Legitimacy Audit

**Not applicable — this phase installs no external packages.** Every API used is in the JDK 21
platform (`java.util.concurrent`, `java.lang`, `java.io`) or in dependencies already declared in
`build.gradle.kts:55-60`. `[VERIFIED: build.gradle.kts:24-61]`

**Packages removed due to [SLOP] verdict:** none
**Packages flagged as suspicious [SUS]:** none

A plan that proposes adding any dependency for this phase should be treated as scope creep:
`23-SECURITY.md:63` records `T-23-06-SC` (supply chain) closed on the basis that *"`gradle/libs.versions.toml`
untouched; no dependency coordinate added"* `[VERIFIED: .planning/phases/23-edt-confinement-ui-responsiveness/23-SECURITY.md:63]`.
Phase 24 should discharge the same threat the same way.

---

## Architecture Patterns

### System Architecture Diagram

```
                     ┌───────────────────────────────────────────────────────┐
                     │  Burp JVM  (one per Burp process, survives reloads)    │
                     └───────────────────────────────────────────────────────┘
                                             │
        ┌────────────────────────────────────┼─────────────────────────────────────┐
        │                                    │                                     │
   RECURRING TICKS                    CLI SUBPROCESS I/O                    POOLED WORK
        │                                    │                                     │
        ▼                                    ▼                                     ▼
┌───────────────────┐            ┌────────────────────────┐        ┌──────────────────────────┐
│ 5 schedule sites  │            │ CliBackend.send()      │        │ App.workerPool           │
│                   │            │  (single-thread exec)  │        │  ← AgentSupervisor:1013  │
│ AgentSupervisor:79│─ guarded ✓ │                        │        │      (short: restart)    │
│ ActiveAiScanner   │            │  spawn ProcessBuilder  │        │  ← AgentSupervisor:1037  │
│   :347 OAST       │─ guarded ✓ │        │               │        │      (INFINITE: log pump)│
│   :340 processQ   │─ UNGUARDED │        ▼               │        └──────────────────────────┘
│ ScannerTaskReg:26 │─ UNGUARDED │  ┌──────────────┐      │                     │
│ CollaboratorReg:25│─ UNGUARDED │  │reader thread │──┐   │        ┌──────────────────────────┐
└───────────────────┘            │  │(named daemon)│  │   │        │ ActiveAiScanner          │
        │                        │  └──────────────┘  │   │        │  .executor (fixed, ≤10)  │
        │ throw escapes          │        appends     ▼   │        │      submits ──┐         │
        ▼                        │            ┌───────────┴──┐     │                ▼         │
┌───────────────────┐            │            │  rawOutput   │     │  .requestExecutor CACHED │
│ JDK suppresses    │            │            │ StringBuilder│     │   (per-HTTP-request,     │
│ ALL FUTURE TICKS  │            │            │  ⚠ UNSYNC    │     │    future.get(timeout))  │
│ (silent, no log)  │            │            │  ⚠ UNBOUNDED │     └──────────────────────────┘
└───────────────────┘            │            └───────┬──────┘                     │
                                 │  reads at :252 (timeout path,  head-2000)       │
                                 │  reads at :264 (exit≠0 path,   head-2000)       ▼
                                 │  reads at :275 (SUCCESS path, FULL — the answer)
                                 │        │                                 orphaned threads
                                 │        ▼                                 when cancel(true)
                                 │  finally: delete promptFile + outputFile  does not unblock
                                 │  ALSO: deleteOnExit() → DeleteOnExitHook  api.http().sendRequest
                                 │        (one String retained per call,
                                 │         never removable)
                                 └────────────────────────┘
```

Read the three columns as the three independent failure families this phase closes: a throw kills a
ticker forever (left), a race/leak corrupts or bloats subprocess capture (middle), unbounded pools
spawn threads without limit (right). They share no code today; the only coupling is between the
right column and the left — see §Finding 3.

### Recommended Project Structure

```
src/main/kotlin/com/six2dez/burp/aiagent/
├── util/
│   └── GuardedScheduling.kt          # NEW — internal fun runGuarded / scheduleGuarded (SC1)
├── backends/cli/
│   ├── CliBackend.kt                 # EDIT — rawOutput type, 2 deleteOnExit removals (SC3/4/5)
│   ├── CliOutputBuffer.kt            # NEW — internal class, synchronized + capped (SC3, SC4)
│   └── CliTempFileRegistry.kt        # NEW — internal object, D-02's registry + hook (SC5)
├── scanner/
│   └── ActiveAiScanner.kt            # EDIT — guard :340, enrich :393 log, bound :73 (SC1/2/6)
├── mcp/tools/
│   ├── ScannerTaskRegistry.kt        # EDIT — guard :26 (SC1)
│   └── CollaboratorRegistry.kt       # EDIT — guard :25 (SC1)
├── supervisor/
│   └── AgentSupervisor.kt            # EDIT — move :1037 log pump off workerPool (SC6 prereq)
├── config/
│   └── Defaults.kt                   # EDIT — MAX_CLI_OUTPUT_CHARS, pool-size constants
└── App.kt                            # EDIT — bound + name workerPool, D-03 drain in shutdown()

src/test/kotlin/com/six2dez/burp/aiagent/
├── util/GuardedSchedulingTest.kt              # NEW — SC1 mechanism (real throw, real next tick)
├── util/SchedulerGuardCoverageTest.kt         # NEW — SC1 structural allowlist
├── scanner/ActiveScannerFailureIsolationTest.kt  # NEW — SC2 (getSettings fault injector)
├── backends/cli/CliOutputBufferTest.kt        # NEW — SC3 + SC4
└── backends/cli/CliBackendTempFileTest.kt     # REWRITE — 2 vacuous tests replaced (SC5)
```

⚠ Every one of those test filenames deliberately avoids the five excluded suffixes. See
§Pitfall 1.

### Pattern 1: Guard helper + structural allowlist ("two places at once")

**What:** One `internal fun` that owns the try/catch, plus a test that asserts no file outside an
allowlist calls `scheduleAtFixedRate`/`scheduleWithFixedDelay` directly.

**When to use:** SC1. This is the shape Phase 23 already ratified.
`[VERIFIED: .planning/phases/23-edt-confinement-ui-responsiveness/23-CONTEXT.md:72-79]`, verbatim:

> **D-02:** **The guarantee lives in two places at once — a shared dispatch helper AND a fail-fast
> check at the executor's door.** The helper makes it work; the guard makes it stay. […] Rejected:
> helper only (nothing stops a fourth parse-and-execute path calling `executeTool` directly — exactly
> the gap SC5 closed for the trust boundary, reopened for the threading one) and guard only […]

**Example** (shape only — the existing guard's wording is the model):

```kotlin
// Source: modelled on src/main/kotlin/com/six2dez/burp/aiagent/supervisor/AgentSupervisor.kt:79-92
//         (the only guarded scheduleAtFixedRate in the repo, verified this session)
internal fun ScheduledExecutorService.scheduleGuarded(
    label: String,
    logError: (String) -> Unit,
    initialDelay: Long,
    delay: Long,
    unit: TimeUnit,
    body: () -> Unit,
): ScheduledFuture<*> =
    scheduleWithFixedDelay(
        { runGuarded(label, logError, body) },
        initialDelay,
        delay,
        unit,
    )

@Suppress("TooGenericExceptionCaught") // see §Pitfall 5 — detekt rule is active, baseline may not grow
internal fun runGuarded(
    label: String,
    logError: (String) -> Unit,
    body: () -> Unit,
) {
    try {
        body()
    } catch (e: Throwable) {
        logError("[$label] scheduled task failed: ${e.message}")
    }
}
```

Note the existing guard catches `Throwable`, not `Exception`
`[VERIFIED: src/main/kotlin/com/six2dez/burp/aiagent/supervisor/AgentSupervisor.kt:86]` — verbatim
`} catch (e: Throwable) {`. That is correct here and must be preserved: the JDK suppresses future
executions on **any** `Throwable`, including `Error`, not only `Exception`. Copy it; do not
"tighten" it to `Exception`.

### Pattern 2: Named daemon thread factory

**What:** `Thread(runnable, "<Name>").apply { isDaemon = true }` handed to the executor factory.

**When to use:** every executor this phase creates or modifies (SC6's second sentence).

```kotlin
// Source: src/main/kotlin/com/six2dez/burp/aiagent/mcp/tools/ScannerTaskRegistry.kt:20-23 (verbatim)
    private val cleaner =
        Executors.newSingleThreadScheduledExecutor { runnable ->
            Thread(runnable, "McpScannerTaskRegistryCleaner").apply { isDaemon = true }
        }
```

`CollaboratorRegistry.kt:19-22` is the same shape with the name `"McpCollaboratorRegistryCleaner"`
`[VERIFIED: src/main/kotlin/com/six2dez/burp/aiagent/mcp/tools/CollaboratorRegistry.kt:19-22]`.

For a multi-thread pool the name needs a per-thread suffix so a thread dump distinguishes them —
an `AtomicInteger` counter in the factory closure. There is no existing multi-thread named factory
in this repo to copy `[VERIFIED: exhaustive grep of src/main/kotlin for `newFixedThreadPool|newCachedThreadPool|ThreadPoolExecutor(`, §Finding 1 table]`;
all six existing named factories are single-thread.

### Pattern 3: Test-only `internal` seam on a Kotlin `object`

**What:** an `internal fun …ForTests(…)` beside the production API.

**When to use:** when a `private` scheduled body cannot be reached from a test. Precedent lives in
the exact two files this phase edits:

```kotlin
// Source: src/main/kotlin/com/six2dez/burp/aiagent/mcp/tools/ScannerTaskRegistry.kt:66-68 (verbatim)
    internal fun configureTtlMillisForTests(milliseconds: Long) {
        ttlMs.set(milliseconds.coerceAtLeast(1L))
    }
```

Consumed by `RegistryTtlTest.kt:22` / `:33`. This is the sanctioned answer to "the registry cleaner
runs every 5 minutes and I cannot wait for it."

### Anti-Patterns to Avoid

- **Catching `Exception` instead of `Throwable` in a scheduler guard.** The JDK's suppression is
  triggered by *"An execution of the task throws an exception"* with no narrowing
  `[VERIFIED: JDK 21 src.zip java.base/java/util/concurrent/ScheduledExecutorService.java:138-142]`;
  an `Error` (e.g. `StackOverflowError` from a deep regex) kills the ticker just as permanently.
- **Bounding a pool that hosts an infinite task.** See §Finding 7. `AgentSupervisor.kt:1037`'s
  `reader.forEachLine` never returns while the service lives.
- **Applying `.take(2000)` at the buffer instead of at the error paths.** Named explicitly by
  CONTEXT.md; verified independently in §Finding 5.
- **`Thread.sleep` + elapsed-duration assertions.** `.planning/codebase/CONCERNS.md` records a
  wall-clock flake (`RedactionTest`) and Phase 23 forbade duration-threshold assertions outright
  `[VERIFIED: src/test/kotlin/com/six2dez/burp/aiagent/ui/SettingsPersistQueueTest.kt:33-35]`, verbatim:
  *"**No test here compares an elapsed duration to a threshold.** Blocking is proved by mutual latches
  whose failure mode is a deadlock `assertTimeoutPreemptively` reports categorically"*.
- **Naming a new suite `*ConcurrencyTest`.** It disappears from the PR gate. See §Pitfall 1.
- **Reflecting into `java.base` internals from a test.** Measured to throw on JDK 21 — see §Finding 9.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Restarting a ticker after it dies | A watchdog that re-schedules a cancelled `ScheduledFuture` | A `try/catch` inside the body so it never dies | The JDK gives no notification of suppression — the `Future` just becomes `isDone()` with no callback. A watchdog must poll. Prevention is one line. |
| Bounded thread pool with a saturation policy | A `Semaphore` + manual thread spawning | `ThreadPoolExecutor` with an explicit `RejectedExecutionHandler` | The JDK ships four policies with documented semantics `[CITED: docs.oracle.com/…/ThreadPoolExecutor.html]`. Hand-rolled backpressure is where lost-wakeup bugs live. |
| Thread-safe string accumulation | `synchronized` blocks around a `StringBuilder` scattered at 4 sites | One `internal class` with the lock inside, or `StringBuffer` | Four call sites (`:220` append, `:252`, `:264`, `:275` read) each needing the same lock is four chances to miss one. |
| Deleting temp files at JVM exit | A polling reaper thread | `Runtime.addShutdownHook` + a live registry (locked as D-01/D-03) | Already decided; a reaper reintroduces the age-threshold problem D-04 rejected. |
| Detecting an unguarded scheduler site | Code review discipline | A structural allowlist test | Phase 23 D-02's whole argument. Review does not scale to a fourth site added in Phase 27. |
| Identifying a failed scan target in a log line | A new `toString()` on `ActiveScanTarget` | `target.id` — it already concatenates url + injection point + vuln class | See §Finding 4. |

**Key insight:** every "don't hand-roll" row here resolves to *something this repo or the JDK already
has*. The phase's risk is not missing capability, it is mis-wiring capability that exists.

---

## Runtime State Inventory

Not a rename/refactor/migration phase in the string-replacement sense, but it *does* change process
and JVM-level runtime state, so the categories are answered rather than skipped.

| Category | Items Found | Action Required |
|----------|-------------|------------------|
| Stored data | **None.** No datastore, collection name, key or ID changes. Verified: no edit in scope touches `AgentSettingsRepository`, the audit JSONL, or the persistent prompt cache. | none |
| Live service config | **None.** No MCP tool signature, no settings key, no external service configuration changes. `activeAiMaxConcurrent` is *read* differently but its persisted key `KEY_ACTIVE_AI_MAX_CONCURRENT` and clamp `coerceIn(1, 10)` are untouched `[VERIFIED: src/main/kotlin/com/six2dez/burp/aiagent/config/AgentSettings.kt:392]` — verbatim `activeAiMaxConcurrent = (prefs.getInteger(KEY_ACTIVE_AI_MAX_CONCURRENT) ?: 3).coerceIn(1, 10),`. | none |
| OS-registered state | **YES — this is the phase's core subject.** (a) `java.io.DeleteOnExitHook`'s static `LinkedHashSet<String> files` accumulates one path String per CLI invocation and is *never* removable `[VERIFIED: JDK 21 src.zip java.base/java/io/DeleteOnExitHook.java:37, java.base/java/io/File.java:1087-1088]`. (b) D-03 adds a **new** `Runtime` shutdown hook whose `Thread` subclass/lambda IS loaded by the extension classloader. | (a) removed by D-01 — but note the existing accumulation in a *running* Burp cannot be undone by this change; it clears only on Burp restart. (b) MUST be removed in `App.shutdown()` per D-03 or it pins the classloader across reloads. |
| Secrets/env vars | **None.** No env var, no SOPS key, no bearer token, no preference key is renamed or read differently. | none |
| Build artifacts | **`build.gradle.kts` `tasks.test` input declarations.** If a structural test is added (Pattern 1's second half), the source files it reads must be declared as `tasks.test` inputs or the guard is served from cache and never runs — the documented 22-09 defect, re-litigated five times in `build.gradle.kts:165-235`. | Add an `inputs` declaration for whatever the structural test reads. See §Pitfall 2. |

---

## Findings (measured this session)

### Finding 1 — Complete inventory of scheduling and executor sites

Produced by an exhaustive grep of `src/main/kotlin` for
`scheduleWithFixedDelay|scheduleAtFixedRate|newCachedThreadPool|newFixedThreadPool|newSingleThreadScheduledExecutor|newSingleThreadExecutor|newScheduledThreadPool|ThreadPoolExecutor(`.

**Recurring schedules — exactly five, three unguarded:**

| Site | Call | Guarded? | In SC1 scope? |
|------|------|----------|---------------|
| `AgentSupervisor.kt:79` | `scheduleAtFixedRate` → `checkHealth()` | ✅ `catch (e: Throwable)` at `:86` | reference implementation |
| `ActiveAiScanner.kt:347` | `scheduleWithFixedDelay` → `pollOastInteractions()` | ✅ `catch (e: Throwable)` at `:350` | reference implementation |
| `ActiveAiScanner.kt:340` | `scheduleWithFixedDelay` → `processQueue()` | ❌ | **yes** |
| `ScannerTaskRegistry.kt:26` | `scheduleWithFixedDelay` → `cleanupExpired()` | ❌ | **yes** |
| `CollaboratorRegistry.kt:25` | `scheduleWithFixedDelay` → `cleanupExpired()` | ❌ | **yes** |

**One-shot schedules — out of REL-06 scope but worth naming so the structural test does not over-reach:**
`ExternalMcpClientManager.kt:273` and `McpSupervisor.kt:238` both use `scheduler.schedule(...)`
(one-shot) `[VERIFIED: grep of `scheduler\.` across both files]`. A one-shot task that throws fails
only itself; there is no "subsequent execution" to suppress. **The structural allowlist must match
`scheduleAtFixedRate|scheduleWithFixedDelay` only, never a bare `schedule(`.**

**Executors — 15 sites, 2 unbounded (both named by SC6):**

| Site | Construction | Named factory? | SC6 scope |
|------|--------------|----------------|-----------|
| `App.kt:38` | `Executors.newCachedThreadPool()` | ❌ | **yes** |
| `ActiveAiScanner.kt:73` | `Executors.newCachedThreadPool()` | ❌ | **yes** |
| `ActiveAiScanner.kt:337` | `newFixedThreadPool(maxConcurrent)` | ❌ | bounded already; naming is a judgement call |
| `ActiveAiScanner.kt:338`, `:346` | `newSingleThreadScheduledExecutor()` | ❌ | bounded already |
| `AgentSupervisor.kt:76` | `newSingleThreadScheduledExecutor()` | ❌ | bounded already |
| `ScannerTaskRegistry.kt:21`, `CollaboratorRegistry.kt:20` | single-thread scheduled | ✅ | pattern source |
| `BurpAiBackend.kt:50`, `LmStudioBackend.kt:105`, `OllamaBackend.kt:217`, `OpenAiCompatibleBackend.kt:144`, `AnthropicBackend.kt:81`, `PassiveAiScanner.kt:68` | `newSingleThreadExecutor { … }` | ✅ | out of scope |
| `CliBackend.kt:97`, `:605`, `:606`, `KtorMcpServerManager.kt:45`, `McpSupervisor.kt:38`, `ExternalMcpClientManager.kt:123` | single-thread | ❌ | out of scope |

**Scoping call for the planner:** SC6's second sentence is *"Executors created by this phase carry
named thread factories"* `[VERIFIED: .planning/ROADMAP.md:316]`. That is a bound on *new* executors,
not a mandate to name all 15. Naming `ActiveAiScanner.kt:337/338/346` and `AgentSupervisor.kt:76` is
cheap and directly serves SC6's stated goal ("so a Burp thread dump is readable"), but it is
optional. Naming the six backend single-thread executors is out of scope.

### Finding 2 — The failure mode, verified at the primary source

JDK 21 `ScheduledExecutorService` KDoc, applying identically to `scheduleAtFixedRate` and
`scheduleWithFixedDelay` — verbatim, both occurrences:

> `* <li>An execution of the task throws an exception.  In this case`
> `* calling {@link Future#get() get} on the returned future will throw`
> `* {@link ExecutionException}, holding the exception as its cause.`
> `* </ul>`
> `* Subsequent executions are suppressed.  Subsequent calls to`
> `* {@link Future#isDone isDone()} on the returned future will`
> `* return {@code true}.`

`[VERIFIED: JDK 21 src.zip java.base/java/util/concurrent/ScheduledExecutorService.java:138-144 (scheduleAtFixedRate) and :181-187 (scheduleWithFixedDelay); toolchain /Library/Java/JavaVirtualMachines/temurin-21.jdk, java --version → openjdk 21.0.12]`
Cross-checked against `[CITED: docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/ScheduledExecutorService.html]`.

Two consequences the plan should encode:
1. **The suppression is silent.** Nothing is logged; the exception is only observable by calling
   `get()` on a `ScheduledFuture` that this codebase discards at all five sites. So the *current*
   production symptom of this bug is "the active scanner queue stops draining and nothing appears in
   the Burp error log" — worth putting in the guard's KDoc.
2. **`Throwable`, not `Exception`.** The JDK text says "an exception" but `ScheduledFutureTask.run`
   suppresses on any `Throwable` reaching it. The two existing guards already catch `Throwable`
   (`AgentSupervisor.kt:86`, `ActiveAiScanner.kt:350`). Match them.

### Finding 3 ⚠ — `processQueue()` has no test-reachable throw site on the scheduler thread

This is the most consequential finding for planning SC1, and it contradicts the naive reading of
SC1's "asserted by injecting a throw".

`processQueue()` in full `[VERIFIED: src/main/kotlin/com/six2dez/burp/aiagent/scanner/ActiveAiScanner.kt:382-406]`:

```kotlin
    private fun processQueue() {
        if (!enabled.get() || !scanning.get()) return

        val exec = executor ?: return

        // Process up to maxConcurrent targets
        repeat(maxConcurrent) {
            val target = scanQueue.poll() ?: return@repeat

            exec.submit {
                try {
                    currentTarget.set("${target.vulnHint.vulnClass}: ${target.originalRequest.request().url().take(50)}")
                    val result = executeScan(target)
                    handleResult(result)
                } catch (e: Exception) {
                    api.logging().logToError("[ActiveAiScanner] Error: ${e.message}")
                } finally {
                    scansCompleted.incrementAndGet()
                    processedTargets[target.id] = System.currentTimeMillis()
                }
            }
        }

        if (scanQueue.isEmpty()) {
            currentTarget.set(null)
        }
    }
```

Everything that touches a target — including `target.originalRequest.request().url()` — is *inside*
the submitted lambda's `try`. On the **scheduler thread** only these run: two `AtomicBoolean.get()`,
a null check, `repeat(maxConcurrent)` over an `Int` field, `ConcurrentLinkedQueue.poll()`,
`ExecutorService.submit(Runnable)`, and `AtomicReference.set`. The only one of those that can throw
in practice is **`exec.submit` → `RejectedExecutionException`**, which happens when the executor is
shut down or (per the JDK) when it *"uses finite bounds for both maximum threads and work queue
capacity, and is saturated"* `[CITED: docs.oracle.com/…/ThreadPoolExecutor.html §Rejected tasks]`.

**Three planning consequences:**

1. **SC1 and SC6 are coupled.** Today the unguarded `:340` site is *nearly* unreachable. The moment
   SC6 bounds an executor with the default `AbortPolicy`, `submit` starts throwing on the scheduler
   thread and permanently kills the queue drain. **Doing SC6 without SC1 makes the scanner strictly
   worse than it is now.** The plan must order them, or land both in one wave with the guard first.
2. **The honest injection for `processQueue` is executor poisoning**, not body poisoning. A test can
   reflect the private `executor` field (`private var executor: ExecutorService?` at
   `ActiveAiScanner.kt:110`) and swap in a stub whose `submit` throws and counts invocations. Deep
   reflection into *this project's own classes* is unrestricted — the module restriction measured in
   §Finding 9 applies only to `java.base`. Alternatively, `stopProcessing()` shuts `executor` down
   while `scanning` is still true within a narrow window; that is racy and should not be the
   assertion.
3. **Prefer the Pattern-1 combination.** `GuardedSchedulingTest` asserts the mechanism with a real
   `ScheduledExecutorService`, a real throw and a real next tick; `SchedulerGuardCoverageTest`
   asserts `ActiveAiScanner.kt` routes `:340` through it. Together those satisfy SC1's intent
   without a reflection-based fixture that would break on any refactor.

### Finding 4 — SC2's per-target isolation already exists; only the log line is missing

The `try/catch` at `:391-401` above **already is** the `AiScanCheck` per-payload idiom that
CONVENTIONS.md §Error Handling names
`[VERIFIED: .planning/codebase/CONVENTIONS.md:195]` — verbatim: *"Scanner failure isolation:
`AiScanCheck` wraps each payload test in `try/catch`, logs to `api.logging().logToError`, and
continues to the next payload — does not fail the entire scan"*.

So SC2's first clause ("keeps processing its queue after an induced failure on one target") is
**already true today** for any failure inside `executeScan`. What is *not* true is SC2's second
clause: the log line is

```kotlin
api.logging().logToError("[ActiveAiScanner] Error: ${e.message}")
```

— no target identifier at all. This is the concrete deliverable of SC2.

**The identifier is already computed.** `ActiveScanTarget` carries a synthesised `id`
`[VERIFIED: src/main/kotlin/com/six2dez/burp/aiagent/scanner/ActiveScanModels.kt:293-301]`, verbatim:

```kotlin
data class ActiveScanTarget(
    val originalRequest: HttpRequestResponse,
    val injectionPoint: InjectionPoint,
    val vulnHint: VulnHint,
    val priority: Int = 50, // 0-100, higher = more urgent
    val queuedAtEpochMs: Long = System.currentTimeMillis(),
) {
    val id: String = "${originalRequest.request().url()}_${injectionPoint.name}_${vulnHint.vulnClass}"
}
```

`target.id` is url + injection point + vuln class — precisely "enough context to identify the
target". **Recommendation:** `"[ActiveAiScanner] Target scan failed: ${target.id}: ${e.message}"`.
No new field, no new `toString()`.

Two caveats for the plan:
- `target.id` embeds a **full URL including query string**. That is consistent with existing
  practice in the same class (`:1099` logs `request.url().take(80)`; `:322` logs `targetId`) and it
  goes to Burp's local error log, never to an AI backend, so the redaction pipeline is not in play.
  But if the planner wants belt-and-braces, `target.id.take(200)` matches the file's truncation
  habit.
- Build the label **before** the `try`, or a failure in `originalRequest.request()` itself would
  make the catch block throw.

**Fault injector for the SC2 test — `getSettings` is the seam, and it is already a constructor
parameter.** `getSettings()` is invoked at exactly one place in the whole class
`[VERIFIED: grep -n "getSettings()" ActiveAiScanner.kt → single hit at :411]`, the first statement of
`executeScan`:

```kotlin
    private fun executeScan(target: ActiveScanTarget): ActiveScanResult {
        val settings = getSettings()
```

`[VERIFIED: src/main/kotlin/com/six2dez/burp/aiagent/scanner/ActiveAiScanner.kt:410-411]`

and the constructor takes it as `private val getSettings: () -> AgentSettings`
`[VERIFIED: src/main/kotlin/com/six2dez/burp/aiagent/scanner/ActiveAiScanner.kt:58]`. `queueTarget`
does **not** call it. So a test can pass a lambda that throws on the first N invocations and returns
`TestSettings.baselineSettings()` afterwards — a clean, no-reflection, per-target fault injector that
fires inside the submit lambda exactly where SC2 needs it.

### Finding 5 ⚠ — the CLI buffer: what "bounded" must and must not mean

Verified read/write sites `[VERIFIED: src/main/kotlin/com/six2dez/burp/aiagent/backends/cli/CliBackend.kt:209, :216-224, :252, :264, :275]`:

| Line | Verbatim | Role |
|------|----------|------|
| `:209` | `val rawOutput = StringBuilder()` | the shared, unsynchronised buffer |
| `:220` | `rawOutput.appendLine(line)` | written by thread `"burp-ai-agent-cli-reader"` (`:225` `readerThread.isDaemon = true`) |
| `:252` | `val tail = rawOutput.toString().trim().take(2000)` | **timeout path**, read after `readerThread.join(2000)` at `:247` |
| `:264` | `val tail = rawOutput.toString().trim().take(2000)` | **exit≠0 path**, read after `join(2000)` at `:258` |
| `:275` | `val stdoutText = stripAnsiCodes(rawOutput.toString())` | **success path — this is the model's answer** |

**Confirming CONTEXT.md's trap, independently.** `:275` feeds `readCodexOutput` / `readGeminiOutput` /
`readOpenCodeOutput` / `readClaudeOutput` / `readCopilotOutput`, all of which are line-filters over
the whole string that join everything surviving the filter
`[VERIFIED: CliBackend.kt:419-443, :445-462, :492-510, :556-572, :574-590]`. There is no
"take the last N lines" anywhere. A 2000-char cap would truncate every answer longer than ~30 lines.

**A detail CONTEXT.md did not state, and which decides head-vs-tail:** `String.take(n)` returns the
**first** `n` characters. Both error paths therefore already show the **head** of the output — the
local variable is misleadingly called `tail`, and `buildTimeoutMessage`'s parameter is also called
`tail` `[VERIFIED: CliBackend.kt:848-850]`, verbatim `internal fun buildTimeoutMessage(\n    tail: String,\n    timeoutSeconds: Int,\n): String {`.
Its KDoc even says *"only `[tail]` is interpolated and it is already bounded by the caller's
`take(2000)` discipline"* `[VERIFIED: CliBackend.kt:841-842]`.

**Recommendation — head-retention with an explicit truncation marker.** Reasons, in order:
1. It preserves both error messages byte-for-byte. A tail-retaining ring buffer would silently
   change what `buildTimeoutMessage` reports, with no requirement asking for that. `CliTimeoutMessageTest`
   exists and would need rewriting for no benefit.
2. The overflow case is pathological by construction (see the cap below); losing the far tail of a
   megabyte of CLI chatter is the correct loss.
3. It is trivially unit-testable and has no ambiguity about "which half survives".

**Recommended cap: `Defaults.MAX_CLI_OUTPUT_CHARS = 262_144`** (256 Ki chars). Justification the
plan can cite:
- It is 8× the repo's own `LARGE_PROMPT_THRESHOLD = 32_000`
  `[VERIFIED: src/main/kotlin/com/six2dez/burp/aiagent/config/Defaults.kt:40]`, verbatim
  `const val LARGE_PROMPT_THRESHOLD = 32_000`, which is this codebase's existing definition of
  "text too big to pass on a command line". A model answer larger than 8× that is not a chat turn.
- It is 131× the 2000-char error head, so both error paths are unaffected in every realistic case.
- **Memory amplification matters more than the cap alone.** `stripAnsiCodes` at `:275` runs four
  sequential `String.replace(Regex, "")` passes over the whole buffer
  `[VERIFIED: CliBackend.kt:1024-1042]` — each allocates a fresh full-size `String`. Peak transient
  heap is therefore roughly 5× the cap (≈ 2.5 MB at 256 Ki chars, ≈ 10 MB at 1 Mi). Note also that
  `stripAnsiCodes` uses raw `Regex`, **not** the project's `SafeRegex` deadline wrapper, so there is
  no timeout protection on those four passes — bounding the input is the only protection. A plan
  that chooses 1_048_576 should say so knowingly.

**SC3 mechanism.** Two acceptable shapes:
- **Minimal:** change `:209` to `val rawOutput = StringBuffer()`. `StringBuffer.append` and
  `.toString()` are both `synchronized`, giving mutual exclusion *and* the happens-before edge the
  timeout path needs. Satisfies SC3 in a one-token diff. Does **not** satisfy SC4.
- **Recommended:** a new `internal class CliOutputBuffer(private val maxChars: Int)` in the `cli`
  package with `synchronized` `appendLine(String)` / `snapshot(): String` / `val truncated: Boolean`.
  One lock, one place, four call sites converted, and it is unit-testable in pure JVM with no
  subprocess — which is exactly the assertability argument D-02 already made for the temp-file
  registry. This is the shape to plan for.

**Why unsynchronised sharing is a real hazard, not a theoretical one:** `readerThread.join(2000)` at
`:247` and `:258` has a 2-second bound and the reader is a daemon that keeps appending if the join
times out `[VERIFIED: CliBackend.kt:246-251, :257-262]`. Without synchronisation the reading thread
has no happens-before edge to the appending thread, so `toString()` can observe a `count` field
larger than the initialised region of `value[]` and return garbage or throw
`StringIndexOutOfBoundsException`/`ArrayIndexOutOfBoundsException` from inside `CliBackend`. `[ASSUMED — the specific exception type; the absence of the happens-before edge is documented, the crash shape is inference]`

### Finding 6 — the `deleteOnExit` leak shape, and a correction to D-03's stated rationale

Verified at the JDK source shipped with the project's toolchain:

```java
class DeleteOnExitHook {
    private static LinkedHashSet<String> files = new LinkedHashSet<>();
```
`[VERIFIED: JDK 21 src.zip java.base/java/io/DeleteOnExitHook.java:37-38]`

and `File.deleteOnExit`'s KDoc, verbatim:

> `* <p> Once deletion has been requested, it is not possible to cancel the`
> `* request.  This method should therefore be used with care.`

`[VERIFIED: JDK 21 src.zip java.base/java/io/File.java:1087-1088]`

So the leak is: **one retained path `String` per CLI invocation, for the life of the Burp JVM, with
no removal API.** `runHooks()` nulls the set only at JVM exit
`[VERIFIED: java.base/java/io/DeleteOnExitHook.java:69-76]`. The two registration sites are
`CliBackend.kt:123` (`.also { it.deleteOnExit() }`, codex output file) and `CliBackend.kt:138`
(`tFile.deleteOnExit()`, large-prompt file) `[VERIFIED: grep -n "deleteOnExit()" CliBackend.kt]`.

⚠ **Correction the planner should carry into the implementation KDoc.** CONTEXT.md D-03 argues:

> A hook that is never removed re-registers on every extension reload and **pins a dead classloader**
> — the same defect class as the `deleteOnExit()` leak, only coarser.

The second half is inaccurate as applied to `deleteOnExit()`: `DeleteOnExitHook.files` holds
`String`s, not `File` objects and not anything loaded by the extension classloader, so today's leak
does **not** pin the classloader. It is a pure unbounded-`String`-set leak.

**D-03's conclusion is nonetheless correct — for the *new* hook, and more strongly than stated.** The
`Runnable`/`Thread` passed to `Runtime.addShutdownHook` by the extension *is* an extension-classloader
class, so an unremoved custom hook genuinely does pin a dead classloader across reloads. D-03's
`removeShutdownHook` requirement is therefore load-bearing; only the analogy is wrong. Two further
mechanical notes for the implementation:
- `Runtime.removeShutdownHook` throws `IllegalStateException` if the VM is already shutting down.
  `App.shutdown()`'s existing `safeShutdownStep` wrapper catches `Exception`
  `[VERIFIED: src/main/kotlin/com/six2dez/burp/aiagent/App.kt:302-314]` — verbatim `catch (e: Exception) {`
  `api.logging().logToError("$component shutdown failed: ${e.message}")` — so wrapping the D-03
  drain in `safeShutdownStep("CLI temp files") { … }` handles it for free. Use that wrapper; do not
  hand-roll a try/catch.
- Existing `DeleteOnExitHook` entries accumulated in a **currently running** Burp cannot be undone by
  this change. SC5 is about future invocations. Worth one sentence in the KDoc so nobody tries to
  "verify the fix" by inspecting a long-running Burp session.

### Finding 7 ⚠ — `App.workerPool` hosts a task that never returns

`workerPool` has exactly **two** submit sites `[VERIFIED: grep -rn "workerPool" src/main/kotlin — 4 hits in App.kt (construction + 3 shutdown lines), 1 constructor param + 2 submits in AgentSupervisor.kt]`:

| Site | Task | Duration |
|------|------|----------|
| `AgentSupervisor.kt:1013` | `workerPool.submit { startOrAttach(backendId) }` (auto-restart) | short, bursty |
| `AgentSupervisor.kt:1037` | a stdout pump: `process.inputStream.bufferedReader().use { reader -> reader.forEachLine { line -> safeLogOutput("[$name] $line") } }` | **the entire lifetime of the service process** |

`[VERIFIED: src/main/kotlin/com/six2dez/burp/aiagent/supervisor/AgentSupervisor.kt:1013 and :1036-1046]`

`startService` is reachable from exactly two call sites — `:261` `startService("ollama-serve", cmd)`
and `:272` `startService("lmstudio-server", cmd)`
`[VERIFIED: grep -rn "startService" src/main/kotlin]` — so at most **2** worker threads can be
permanently occupied.

**Consequences for SC6:**
- A `newFixedThreadPool(n)` with n ≤ 2 can deadlock the auto-restart path outright: both threads
  parked in `forEachLine`, restart tasks queue forever on the unbounded `LinkedBlockingQueue`, and
  nothing reports it. A bounded queue with `AbortPolicy` would instead throw
  `RejectedExecutionException` out of `scheduleRestart`'s `workerPool.submit` — which is *not* inside
  a try — killing that call path.
- `CallerRunsPolicy` is actively dangerous on `workerPool`: the caller of `startService` would run
  the infinite pump inline. Given Phase 23 (REL-05) just finished moving work **off** the EDT, a
  CallerRuns policy here risks re-introducing an EDT block through the back door. **Do not use
  CallerRunsPolicy on `workerPool`.**

**Recommendation (ordered):**
1. **First, move the log pump off the pool.** Replace `workerPool.submit { … }` at `:1037` with a
   named daemon thread — `Thread(task, "BurpAiAgent-service-$name").apply { isDaemon = true }.start()`.
   One edit, one call site, and it matches both the house pattern (`CliBackend.kt:216-226`'s
   `"burp-ai-agent-cli-reader"`) and Phase 23 D-05's ruling for unbounded-duration work
   `[VERIFIED: .planning/phases/23-edt-confinement-ui-responsiveness/23-CONTEXT.md:111-117]`, which
   explicitly requires the daemon flag be set *explicitly* because "a daemon thread never blocks
   unload" is only true if the flag is actually set.
2. **Then bound `workerPool`** as `ThreadPoolExecutor(0, MAX_WORKER_THREADS, 60L, SECONDS,
   SynchronousQueue(), namedFactory, AbortPolicy)` — cached-pool semantics with a ceiling. With the
   pump gone, only short bursty tasks remain, so saturation is implausible and Abort's "fail loudly"
   matches CONVENTIONS.md §Error Handling. Suggested `MAX_WORKER_THREADS = 8` in `Defaults`.
   Alternatively `newFixedThreadPool(4)` with a named factory — simpler, bounded, and the unbounded
   queue is acceptable once no task is infinite. Either satisfies SC6; the fixed pool is the smaller
   diff.
3. Whichever is chosen, `App.kt:237-243`'s existing `shutdown()` / `awaitTermination(5, SECONDS)` /
   `shutdownNow()` sequence keeps working unchanged
   `[VERIFIED: src/main/kotlin/com/six2dez/burp/aiagent/App.kt:236-246]`.

### Finding 8 — `ActiveAiScanner.requestExecutor`: demand, leak path, and the un-caught `submit`

`requestExecutor` has exactly **one** submit site `[VERIFIED: grep -n "requestExecutor" ActiveAiScanner.kt → :73 construction, :1089 submit, :1658/:1660/:1661/:1665 shutdown]`:

```kotlin
    private fun sendRequestWithTimeout(request: HttpRequest): HttpRequestResponse? {
        val timeout = timeoutSeconds.coerceAtLeast(5).toLong()
        val future =
            requestExecutor.submit(
                Callable {
                    api.http().sendRequest(request, tlsRequestOptions)
                },
            )
        return try {
            future.get(timeout, TimeUnit.SECONDS)
        } catch (e: TimeoutException) {
            future.cancel(true)
            …
```

`[VERIFIED: src/main/kotlin/com/six2dez/burp/aiagent/scanner/ActiveAiScanner.kt:1085-1105]`

**Steady-state demand is bounded and small.** Callers are the scanner worker threads from
`executor = Executors.newFixedThreadPool(maxConcurrent)` `[VERIFIED: ActiveAiScanner.kt:337]`, and
`maxConcurrent` is persisted with `coerceIn(1, 10)` `[VERIFIED: AgentSettings.kt:392]`. Each worker
blocks on `future.get(...)`. So at most ~10 requests are in flight at once.

**The leak path is orphaned threads, not concurrency.** On timeout the code calls
`future.cancel(true)`, which interrupts the worker thread — but whether `api.http().sendRequest`
responds to interrupt is not documented by Montoya. `[ASSUMED]` If it does not, the thread stays
occupied for the remainder of the underlying socket timeout while a *new* request immediately gets a
*new* thread from the cached pool (`newCachedThreadPool` = `ThreadPoolExecutor(0, Integer.MAX_VALUE,
60L, SECONDS, SynchronousQueue)` `[VERIFIED: JDK 21 src.zip java.base/java/util/concurrent/Executors.java:217-219]`,
verbatim `return new ThreadPoolExecutor(0, Integer.MAX_VALUE,` / `60L, TimeUnit.SECONDS,` /
`new SynchronousQueue<Runnable>());`). **That is the concrete mechanism behind SC6's "an active scan
with many injection points cannot spawn unbounded threads":** a scan against a black-holing host,
one orphan per 30-second timeout, unbounded.

**Recommendation:**
- `ThreadPoolExecutor(0, Defaults.MAX_SCAN_REQUEST_THREADS, 60L, SECONDS, SynchronousQueue(),
  namedFactory, AbortPolicy)` with `MAX_SCAN_REQUEST_THREADS = 32`. Rationale: 3× the hard ceiling
  of `maxConcurrent` (10), so orphans have headroom before rejection; `SynchronousQueue` preserves
  today's direct-handoff latency (no queueing delay in front of a `future.get(timeout)`).
- **Do NOT use a fixed pool here.** With `newFixedThreadPool(10)` and 10 orphaned threads, every
  subsequent submit queues behind them and *every* `future.get` times out — the scanner degrades to
  "all requests return null" with no error anywhere. That is silently worse than the current bug.
  This is the single most important sizing decision in SC6.
- **`requestExecutor.submit(...)` at `:1089` is OUTSIDE the `try` at `:1092`.** With `AbortPolicy`,
  a saturated pool throws `RejectedExecutionException` out of `sendRequestWithTimeout`, up through
  `executeScan`, into the per-target catch at `:396` — one target aborted with a message of
  `null` (RejectedExecutionException's message is often null). **The plan must move the `submit`
  inside the `try`, or add an explicit `catch (e: RejectedExecutionException)` returning `null` with
  a log line.** Without this edit, SC6 introduces a new confusing failure mode.
- **Sizing cannot read `maxConcurrent`.** `requestExecutor` is a `val` initialised at construction
  (`:73`) while `maxConcurrent` is a mutable `var` (`:113`, default 3) set later from settings
  (`App.kt:151`, `SettingsPanelScannerTabs.kt:210`, `SettingsPanelSettingsIO.kt:589`). A constant
  ceiling is the correct answer; deriving from `maxConcurrent` would require moving construction into
  `startProcessing()` and making the field nullable — a much larger diff for no benefit.
- **Naming:** `"BurpAiAgent-scan-request-$n"` via an `AtomicInteger` in the factory closure.

### Finding 9 ⚠ — `CliBackendTempFileTest`'s "blocking" tests are vacuous, not blocking

CONTEXT.md §"⚠ Blocking test constraint for the planner" states that
`uvPromptDeleteOnExitIsRegistered` (`:95`) and `codexOutputDeleteOnExitIsRegistered` (`:106`) *"pin
exactly the behaviour D-01 removes"* and *"must be inverted in the same commit as the production
change — leaving them turns the fix red"*.

**Measured: that is not true, for two independent reasons.**

**Reason 1 — the tests never touch `CliBackend`.** All four tests in that file create their own temp
file and call `deleteOnExit()` themselves. Verbatim
`[VERIFIED: src/test/kotlin/com/six2dez/burp/aiagent/backends/cli/CliBackendTempFileTest.kt:94-104]`:

```kotlin
    @Test
    fun uvPromptDeleteOnExitIsRegistered() {
        val tFile = File.createTempFile("burp_uv_prompt_test_dox_", ".txt")
        try {
            tFile.deleteOnExit()
            assertTrue(isRegisteredForDeleteOnExit(tFile), "deleteOnExit must be registered for $tFile")
        } finally {
            tFile.delete()
        }
    }
```

Note the prefix is `"burp_uv_prompt_test_dox_"`, not the production `"burp_uv_prompt_"`, and the
`deleteOnExit()` under assertion is the test's own call two lines above. Removing `deleteOnExit()`
from `CliBackend.kt:123` and `:138` cannot affect it. The file contains **zero** imports of
`CliBackend` `[VERIFIED: CliBackendTempFileTest.kt:1-7 — imports are junit, java.io.File, java.lang.reflect.Field only]`.

**Reason 2 — the reflection is dead on JDK 21 and fails *open*.** The helper
`[VERIFIED: CliBackendTempFileTest.kt:117-133]` ends with:

```kotlin
        } catch (_: Exception) {
            // If reflection fails (future JDK), check presence indirectly:
            // the test is best-effort; do not fail the build on JVM internals change.
            true
        }
```

Measured on the project's own toolchain (`/Library/Java/JavaVirtualMachines/temurin-21.jdk`,
`openjdk 21.0.12 2026-07-21 LTS, Temurin-21.0.12+8`), a standalone probe performing exactly the
same three reflective calls prints:

```
REFLECTION_FAILED java.lang.reflect.InaccessibleObjectException: Unable to make field private static
java.util.LinkedHashSet java.io.DeleteOnExitHook.files accessible: module java.base does not
"opens java.io" to unnamed module @27ae2fd0
```

`[VERIFIED: probe executed this session with $JAVA_HOME/bin/java on /tmp/DoxProbe.java]`

`InaccessibleObjectException extends RuntimeException`, so it is caught by `catch (_: Exception)` and
the helper returns `true` unconditionally. `tasks.test` passes only `jvmArgs("-ea",
"-Djava.awt.headless=true")` — no `--add-opens`
`[VERIFIED: build.gradle.kts:154]`, verbatim
`jvmArgs("-ea", "-Djava.awt.headless=true") // Enable JVM assertions so EDT assert() fires in CI (REL-01 SC1 gate)`.

Confirmed end-to-end: `./gradlew test --tests '*CliBackendTempFileTest*' detekt` exits **0** today
`[VERIFIED: run this session, exit code 0]`.

**What the planner should do instead.** The two tests provide zero coverage and must be *replaced*,
not "inverted":
- **Delete the JDK-internal reflection entirely.** It is dead code on every JDK ≥ 16 and its
  fail-open catch makes it incapable of ever failing. Nothing of value is lost.
- **Assert against the D-02 `internal object` seam instead** — the whole point of D-02 was to create
  something a pure-JVM test can drive. Proposed replacements: (a) registering a file adds exactly one
  entry and registering the same file twice adds one; (b) the `finally`-path deregistration removes
  it; (c) the drain deletes a still-registered file and is idempotent when the file is already gone;
  (d) the registry size returns to zero after a simulated call completes — the direct
  "no per-invocation accumulation" assertion SC5 asks for.
- **A structural assertion that `CliBackend.kt` no longer contains `deleteOnExit(`** is the cheap,
  exact counterpart to SC5's first clause, and it *is* checkable (unlike the reflection). It needs a
  `tasks.test` input declaration — see §Pitfall 2.
- **The two `…TempFileIsCleanedUpAfterFailure` tests (`:41`, `:65`) are equally self-contained** —
  they too create and delete their own files and never call `CliBackend`. CONTEXT.md calls them "a
  useful control"; they are a control only in the weakest sense (they prove `File.delete()` works).
  Keeping them green is free; relying on them as coverage of the `finally` path would be a mistake.

### Finding 10 — the `-PexcludeHeavyTests` naming trap (exact list)

The exclusions are **conditional**, not unconditional — a detail the planner needs to reason about
CI correctly `[VERIFIED: build.gradle.kts:236-248]`, verbatim:

```kotlin
    val excludeHeavyTests =
        (project.findProperty("excludeHeavyTests") as? String)
            ?.trim()
            ?.equals("true", ignoreCase = true) == true
    if (excludeHeavyTests) {
        filter {
            excludeTestsMatching("*IntegrationTest")
            excludeTestsMatching("*ConcurrencyTest")
            excludeTestsMatching("*BackpressureTest")
            excludeTestsMatching("*RestartPolicyTest")
            excludeTestsMatching("*SupervisionTest") // WR-03: 30s coerced-timeout floor — excluded from fast PR gate
        }
    }
```

And the PR gate does pass it `[VERIFIED: .github/workflows/build.yml:47]`, verbatim
`run: ./gradlew test -PexcludeHeavyTests=true --no-daemon`. Full runs happen in
`.github/workflows/nightly-regression.yml:26` (`./gradlew test nightlyRegressionTest
edtGuardWithoutAssertionsTest shadowJar --no-daemon`) and `.github/workflows/release.yml:33`.

**The five forbidden suffixes for a Phase 24 suite name:** `*IntegrationTest`, `*ConcurrencyTest`,
`*BackpressureTest`, `*RestartPolicyTest`, `*SupervisionTest`.

This trap has already bitten and been documented in-repo
`[VERIFIED: src/test/kotlin/com/six2dez/burp/aiagent/ui/SettingsPersistQueueTest.kt:26-31]`, verbatim:

> **Naming constraint (hard).** `build.gradle.kts` excludes `*IntegrationTest`, `*ConcurrencyTest`,
> `*BackpressureTest`, `*RestartPolicyTest` and `*SupervisionTest` under `-PexcludeHeavyTests=true`,
> which is exactly what `.github/workflows/build.yml` passes. `SettingsPersistQueueTest` is the
> approved name; `SettingsPersistConcurrencyTest` would have been the natural one and would have made
> this suite silently stop running on the PR gate.

Phase 24 is a *concurrency* phase, so the natural names are exactly the forbidden ones. Every plan
must state its chosen suite name explicitly, and the plan-checker should verify none ends in those
five suffixes. Copy `SettingsPersistQueueTest`'s KDoc paragraph into each new suite.

---

## Project Constraints (from CLAUDE.md and AGENTS.md)

| Directive | Source | Impact on this phase |
|-----------|--------|----------------------|
| All code, comments and explanations in **English only** — "non-negotiable" | `AGENTS.md:4-5`, `CLAUDE.md:19` | Every new KDoc (D-04's residual disclosure, the guard's failure-mode note) must be English. |
| Kotlin + Gradle Kotlin DSL, Burp Montoya API, JVM 21 | `CLAUDE.md:12`, `build.gradle.kts:63-67` | No new language, no coroutines outside the MCP layer. |
| "Production-grade: stable, defensive coding, deterministic modes" | `AGENTS.md:9` | Directly the phase thesis. |
| "Use small, testable components / Favor pure functions / Add unit tests where feasible / No demo-only shortcuts" | `AGENTS.md:60-64` | Supports extracting `CliOutputBuffer` and `runGuarded` rather than inlining. |
| Strict boundary between the 7 listed subsystems | `AGENTS.md:11-20` | A shared `runGuarded` in `util/` crosses no boundary; a shared *pool* would. |
| Distribution is a single fat JAR, MIT-compatible deps only | `CLAUDE.md:15-17` | Reinforces "no new dependency". |
| **Start work through a GSD command; no direct repo edits outside a GSD workflow** | `CLAUDE.md:47-56` | Execution must go through `/gsd-execute-phase`. |
| Build requires JDK 21 explicitly | measured (§Environment Availability) | Every Gradle invocation in every plan must be prefixed `JAVA_HOME=$(/usr/libexec/java_home -v 21)`. |

---

## Common Pitfalls

### Pitfall 1: Naming a new suite with an excluded suffix

**What goes wrong:** the suite compiles, passes locally, and never runs on the PR gate.
**Why it happens:** this is a concurrency phase; `SchedulerGuardConcurrencyTest` is the name that
comes naturally, and it is on the exclusion list.
**How to avoid:** see §Finding 10. Use `…GuardTest`, `…IsolationTest`, `…BufferTest`, `…QueueTest`.
**Warning signs:** `./gradlew test -PexcludeHeavyTests=true` reports fewer tests than `./gradlew test`.

### Pitfall 2: A structural test served from a stale build cache

**What goes wrong:** a source-reading guard is restored from the Gradle build cache and never
executes, precisely in the commit that breaks the thing it guards.
**Why it happens:** `tasks.test`'s cache key is derived from the compiled classpath. An edit that
changes `.kt` source text but not bytecode (a comment, a reflowed string, a moved call inside an
already-generic block) yields an identical key.
**How to avoid:** declare every source file the structural test reads as a `tasks.test` input. The
repo has done this six times and documented why each time; the shape
`[VERIFIED: build.gradle.kts:186-189]`:

```kotlin
    inputs
        .file("src/main/kotlin/com/six2dez/burp/aiagent/mcp/ToolApprovalGate.kt")
        .withPropertyName("originVisibilitySource")
        .withPathSensitivity(PathSensitivity.RELATIVE)
```

For a **whole-tree** allowlist scan (Pattern 1's second half), individual `inputs.file` entries
defeat the purpose — a *sixth* unguarded scheduler in an undeclared file would be invisible to the
cache key. Use `inputs.files(fileTree("src/main/kotlin")).withPropertyName("schedulerGuardSources")
.withPathSensitivity(PathSensitivity.RELATIVE)` and accept that the test task then re-runs on any
main-source edit. That trade is correct for a whole-tree invariant; state it in the comment as the
other six entries do.
**Warning signs:** deliberately breaking the invariant locally and seeing `test` reported
`UP-TO-DATE` or `FROM-CACHE`. The repo's own measurement note
(`build.gradle.kts:170-171`: *"Measured: mutating the AUTO sentence left `./gradlew test` GREEN until
`cleanTest` forced a re-run."*) is the canonical reproduction.

### Pitfall 3: New `catch (Exception)` / `catch (Throwable)` fails `./gradlew detekt`

**What goes wrong:** `detekt` fails the build on a rule that 40 existing sites are baselined against.
**Why it happens:** `TooGenericExceptionCaught` is **active** — `detekt.yml` overrides only
`LongMethod`, `LongParameterList`, `MaxLineLength` and `FunctionNaming`
`[VERIFIED: detekt.yml — full file read this session, 17 lines, no `exceptions:` section]` — and
`ignoreFailures` is not set for detekt, only for ktlint `[VERIFIED: build.gradle.kts:310-317 and :293-300]`.
The baseline holds 1096 entries, 40 of them `TooGenericExceptionCaught`
`[VERIFIED: grep -c "<ID>" detekt-baseline.xml → 1096; grep -c TooGenericExceptionCaught → 40]`.
**Detekt baseline IDs are exact strings keyed by file and signature** — e.g.
`TooGenericExceptionCaught:ActiveAiScanner.kt$ActiveAiScanner$e: Throwable`
`[VERIFIED: detekt-baseline.xml]`. So a *new* `catch (e: Throwable)` added **inside
`ActiveAiScanner.kt`'s `ActiveAiScanner` class** is covered by the existing entry, while the same
catch in a **new file** (`GuardedScheduling.kt`, `CliOutputBuffer.kt`, `CliTempFileRegistry.kt`)
produces a new, unbaselined ID and fails the build.
**Also relevant:** `MagicNumber` has 651 baselined entries — put every new literal (pool ceilings,
buffer cap, tick intervals) in `Defaults` as a `const val`, which detekt's `MagicNumber` ignores by
default. `SwallowedException` has 9 entries; D-03's idempotent double-delete will want
`catch (_: Exception) {}`, which is the CONVENTIONS-blessed form
`[VERIFIED: .planning/codebase/CONVENTIONS.md:194]` but may still trip the rule in a new file.
**How to avoid:** `@Suppress("TooGenericExceptionCaught")` with a KDoc justification. This is the
established house answer, and the precedent even explains why it is preferred over regenerating the
baseline `[VERIFIED: src/main/kotlin/com/six2dez/burp/aiagent/ui/SettingsPanelSettingsIO.kt:284-293]`
— verbatim: *"**`@Suppress("LongMethod")` rather than a regenerated baseline.**"*. Existing
`TooGenericExceptionCaught` suppressions: `SettingsPanelSettingsIO.kt:623`,
`McpToolExecutorImpl.kt:1126`, `ExternalMcpClientManager.kt:172` and `:302`.
**Warning signs:** `./gradlew detekt` red on a file you just created. QUAL-07 forbids fixing it by
regenerating the baseline: *"The detekt baseline shrinks rather than grows — no finding from this
milestone is added to it"* `[VERIFIED: .planning/REQUIREMENTS.md:34]`.

### Pitfall 4: Asserting a tick with a wall-clock duration

**What goes wrong:** the suite flakes under CI load and gets muted.
**Why it happens:** the repo already has one such flake on record —
`.planning/codebase/CONCERNS.md` §"`RedactionTest` has a wall-clock flake that surfaces under CPU
load" `[VERIFIED: cited by 23-CONTEXT.md:312-313]`.
**How to avoid:** poll a *state* (a `CountDownLatch`, an `AtomicInteger` tick counter) inside
`assertTimeoutPreemptively(Duration.ofSeconds(N))`. Never compare `System.currentTimeMillis()`
deltas to a threshold. Use a very short scheduler delay (10–20 ms) in the mechanism test so the
generous timeout is never approached.
**Warning signs:** any `assertTrue(elapsed < X)` in a diff.

### Pitfall 5: Bounding a pool before separating the workloads

Covered in §Finding 7. The symptom is a *silent* stall (queued forever) or a confusing
`RejectedExecutionException` with a null message from a call path with no catch.
**Warning signs:** a plan task that changes `App.kt:38` without a preceding task that changes
`AgentSupervisor.kt:1037`.

### Pitfall 6: Leaking a live `ActiveAiScanner` out of a test

**What goes wrong:** `setEnabled(true)` starts a 500 ms ticker, a fixed pool, two single-thread
schedulers and leaves `requestExecutor` alive; a test that does not tear down leaves them running for
the rest of the JVM, producing cross-test interference and slow, noisy runs.
**Why it happens:** `startProcessing()` creates four executors `[VERIFIED: ActiveAiScanner.kt:333-353]`
and only `shutdown()` (`:1653`) terminates `requestExecutor`.
**How to avoid:** `@AfterEach { scanner.shutdown() }` in every suite that calls `setEnabled(true)`.
Note the existing `ActiveScannerDedupTest` calls `setEnabled(true)` and does **not** shut down
`[VERIFIED: src/test/kotlin/com/six2dez/burp/aiagent/scanner/ActiveScannerDedupTest.kt:31-32]` — do
not copy that part.
**Warning signs:** `./gradlew test` hanging at the end, or Gradle warning about non-daemon threads.

### Pitfall 7: Assuming `ScannerTaskRegistry` / `CollaboratorRegistry` cleaners are drivable

**What goes wrong:** a test waits for a cleaner tick that is 5 minutes away.
**Why it happens:** both are Kotlin `object`s whose `init` schedules the cleaner at class-init with
`CLEANUP_INTERVAL_MINUTES = 5L` `[VERIFIED: src/main/kotlin/com/six2dez/burp/aiagent/mcp/tools/ScannerTaskRegistry.kt:100]`,
verbatim `private const val CLEANUP_INTERVAL_MINUTES = 5L`, and the `cleaner` field is `private` with
no restart path. `cleanupExpired()` is `private` and, on inspection, contains **no throw site at
all** — it iterates a `ConcurrentHashMap`, compares two `Long`s, and calls `log()`, which already
swallows everything `[VERIFIED: ScannerTaskRegistry.kt:70-96]`, verbatim of `log`:

```kotlin
    private fun log(message: String) {
        try {
            loggerRef.get().invoke(message)
        } catch (_: Exception) {
            // Ignore logging callback failures; registry behavior should stay deterministic.
        }
    }
```

So there is no external lever to make `cleanupExpired()` throw.
**How to avoid:** satisfy SC1 for these two via Pattern 1 (mechanism test on `runGuarded` +
structural test proving both files route through it), and — if the plan wants a per-site behavioural
assertion — add an `internal fun runCleanupTickForTests()` beside the existing
`internal fun configureTtlMillisForTests` (`:66-68`), invoking the *same guarded* wrapper the
scheduler invokes, plus an `internal` fault flag. The `…ForTests` seam is already house style in
these exact two files.
**Warning signs:** a plan task promising "assert the registry cleaner survives a throw" without
naming the lever it uses.

---

## Code Examples

### Guarded recurring schedule (the existing reference implementation)

```kotlin
// Source: src/main/kotlin/com/six2dez/burp/aiagent/supervisor/AgentSupervisor.kt:78-92 (verbatim)
    init {
        monitorExec.scheduleAtFixedRate(
            {
                // scheduleAtFixedRate silently cancels the recurring task if any run throws an
                // uncaught exception — guard so a transient checkHealth() failure can't stop the
                // health monitor (and with it auto-restart) for the rest of the session.
                try {
                    checkHealth()
                } catch (e: Throwable) {
                    api.logging().logToError("[AgentSupervisor] health check failed: ${e.message}")
                }
            },
            Defaults.HEALTH_CHECK_INTERVAL_MS,
            Defaults.HEALTH_CHECK_INTERVAL_MS,
            TimeUnit.MILLISECONDS,
        )
```

### The second reference implementation (OAST poller)

```kotlin
// Source: src/main/kotlin/com/six2dez/burp/aiagent/scanner/ActiveAiScanner.kt:346-353 (verbatim)
        oastPoller = Executors.newSingleThreadScheduledExecutor()
        oastPoller?.scheduleWithFixedDelay({
            try {
                pollOastInteractions()
            } catch (e: Throwable) {
                api.logging().logToError("[ActiveAiScanner] OAST poll failed: ${e.message}")
            }
        }, OAST_POLL_INTERVAL_SECONDS, OAST_POLL_INTERVAL_SECONDS, TimeUnit.SECONDS)
```

Note both use the `"[Component] message"` log prefix that CONVENTIONS.md §Error Handling mandates
`[VERIFIED: .planning/codebase/CONVENTIONS.md:193]`, verbatim: *"`try/catch(e: Exception)` at process
and network boundaries; log with `api.logging().logToError("[Component] ${e.message}")`"*.

### The unguarded site to fix (SC1)

```kotlin
// Source: src/main/kotlin/com/six2dez/burp/aiagent/scanner/ActiveAiScanner.kt:340-342 (verbatim)
        scheduledExecutor?.scheduleWithFixedDelay({
            processQueue()
        }, 0, 500, TimeUnit.MILLISECONDS)
```

### The `finally` path D-01 preserves (SC5)

```kotlin
// Source: src/main/kotlin/com/six2dez/burp/aiagent/backends/cli/CliBackend.kt:290-307 (verbatim)
                    } finally {
                        // Guaranteed cleanup: kill process, delete temp files
                        try {
                            process?.destroyForcibly()
                        } catch (_: Exception) {
                            // INTENTIONAL: finally block cleanup; destroyForcibly() must not prevent file cleanup
                        }
                        try {
                            promptFile?.delete()
                        } catch (_: Exception) {
                            // INTENTIONAL: finally block cleanup; file deletion must not prevent process cleanup
                        }
                        try {
                            outputFile?.delete()
                        } catch (_: Exception) {
                            // INTENTIONAL: finally block cleanup; file deletion must not prevent process cleanup
                        }
                    }
```

⚠ CONTEXT.md cites this block as `:288-307`; the `} finally {` token is actually at **`:290`**
`[VERIFIED: grep -n "} finally {" CliBackend.kt → 290, 623]`. The D-01 deregistration calls belong
beside the two `?.delete()` calls at `:298` and `:303`.

### The shutdown-step wrapper D-03 should reuse

```kotlin
// Source: src/main/kotlin/com/six2dez/burp/aiagent/App.kt:302-314 (verbatim)
    private fun safeShutdownStep(
        component: String,
        action: () -> Unit,
    ) {
        try {
            action()
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            api.logging().logToError("$component shutdown interrupted")
        } catch (e: Exception) {
            api.logging().logToError("$component shutdown failed: ${e.message}")
        }
    }
```

### Headless `ActiveAiScanner` construction (the SC2 test's starting point)

```kotlin
// Source: src/test/kotlin/com/six2dez/burp/aiagent/scanner/ScannerQueueBackpressureTest.kt:20-27 (verbatim)
        val scanner =
            ActiveAiScanner(
                api = mock<MontoyaApi>(defaultAnswer = Answers.RETURNS_DEEP_STUBS),
                supervisor = mock<AgentSupervisor>(),
                audit = mock<AuditLogger>(),
            ) { TestSettings.baselineSettings() }
```

The trailing lambda is `getSettings` — the SC2 fault injector identified in §Finding 4.

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Deep reflection into JDK internals from tests (`setAccessible` on `java.base` privates) | Strong encapsulation by default; requires `--add-opens` | JDK 16 (`--illegal-access=deny` default), enforced hard from JDK 17 | `CliBackendTempFileTest`'s reflection has been dead since the project moved to JDK 21. §Finding 9. |
| `Executors.newCachedThreadPool()` as the default "just run it" pool | Explicit `ThreadPoolExecutor` with a stated ceiling and a stated rejection policy | Long-standing guidance; codified in the JDK's own `ThreadPoolExecutor` §Rejected tasks | The whole of SC6. |
| `File.deleteOnExit()` as a temp-file safety net | Registry + one removable shutdown hook, or `Files.createTempDirectory` + recursive sweep | Long-standing; the JDK KDoc's *"not possible to cancel the request"* is the reason | SC5 / D-01. |
| `StringBuilder` shared across threads with "it's probably fine" | `StringBuffer` or an explicitly synchronised wrapper | JDK 5 (`StringBuilder` introduced explicitly as the *unsynchronised* variant) | SC3. |

**Deprecated/outdated in this repo's context:**
- The `isRegisteredForDeleteOnExit` helper in `CliBackendTempFileTest.kt:117-133`: unreachable on the
  project's own JDK and fail-open by construction. Delete rather than port.
- The `// REL-02: deleteOnExit() registers a JVM shutdown hook as crash-safety net; the finally block
  below (:274-288) is the primary cleanup path.` comments at `CliBackend.kt:119-120` and `:136-137`:
  both must go with D-01, and both already carry a stale line reference (the `finally` is at `:290`,
  not `:274`).

---

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | Montoya's `api.http().sendRequest(...)` may not respond to `Thread.interrupt()`, so `future.cancel(true)` can orphan a request thread. | §Finding 8 | If `sendRequest` *is* interruptible, the "orphaned thread" mechanism behind SC6 is weaker than described — but SC6's requirement (bounded pool) is unchanged, and the recommended `SynchronousQueue` + ceiling remains correct either way. Low risk. |
| A2 | Unsynchronised `StringBuilder.toString()` racing with `appendLine` can surface as a `StringIndexOutOfBoundsException`/garbage rather than only stale data. | §Finding 5 | The *absence of a happens-before edge* is documented; the specific crash shape is inference from `AbstractStringBuilder`'s `count`/`value[]` layout. If wrong, SC3 is still required verbatim by the ROADMAP; only the KDoc's description of the symptom would need softening. Low risk. |
| A3 | `MAX_CLI_OUTPUT_CHARS = 262_144` is generous enough that no legitimate CLI answer is ever truncated. | §Finding 5 | If a user's agentic CLI legitimately emits >256 Ki chars in one turn, its answer gains a truncation marker instead of being silently corrupted — a visible, recoverable failure. Mitigation: expose it as a `Defaults` constant so bumping it is a one-line change. Medium-low risk; **worth confirming with the user in plan-check.** |
| A4 | `MAX_WORKER_THREADS = 8` / `MAX_SCAN_REQUEST_THREADS = 32` are appropriate ceilings. | §Findings 7, 8 | Numbers are derived from measured demand (≤2 permanent pumps; `maxConcurrent` clamped to 1..10) with 3–4× headroom, not from a benchmark. Too low → rejected work under pathological load; too high → SC6 satisfied only nominally. **Worth confirming with the user.** |
| A5 | Naming `ActiveAiScanner.kt:337/338/346` and `AgentSupervisor.kt:76` executors is optional under SC6's "Executors created by this phase". | §Finding 1 | If the maintainer reads SC6 as "name every executor the phase touches", scope grows by four one-line edits. Cheap either way; **worth a one-line confirmation in plan-check.** |
| A6 | A whole-tree `inputs.files(fileTree("src/main/kotlin"))` declaration on `tasks.test` is acceptable despite busting the test cache on any main-source edit. | §Pitfall 2 | If unacceptable, fall back to per-file `inputs.file` for the five known scheduler files — at the cost that a sixth file's unguarded scheduler escapes the cache key. **Worth confirming with the user.** |
| A7 | The 500 ms `processQueue` tick and the deep-stub Montoya mock let the SC2 test complete in seconds rather than minutes. | §Validation Architecture | If the deep-stub HTTP path proves slow or divergent, fall back to making *every* target fail via the `getSettings` injector (no HTTP path at all) and asserting three distinct failure log lines + continued draining. Documented as the fallback in the validation table. Low risk. |

---

## Open Questions

1. **Does SC1 require a per-site behavioural assertion, or is mechanism + structural sufficient?**
   - What we know: the mechanism (`runGuarded` under a real `ScheduledExecutorService`) is fully
     assertable; `ActiveAiScanner.processQueue` is assertable via executor poisoning; both registries
     have **no external throw lever** (§Pitfall 7).
   - What's unclear: whether the maintainer reads *"Asserted by injecting a throw"* as "one test per
     named method" or "one test of the guarantee".
   - Recommendation: plan for mechanism + structural as the baseline (that is Phase 23 D-02's
     ratified shape), and add an `internal fun …ForTests` fault lever to the two registries only if
     plan-check says the literal reading is required. Raise it at the plan checkpoint.

2. **Head-retention vs head+tail for the CLI buffer.**
   - What we know: head-retention preserves both error paths byte-for-byte; the answer is usually at
     the end for chatty CLIs.
   - What's unclear: whether any supported CLI emits >256 Ki chars of banner before its answer. None
     of the five noise filters (`isGeminiNoiseLine`, `isOpenCodeNoiseLine`) suggests banners of that
     magnitude.
   - Recommendation: head-retention plus a marker. A head+tail split is a strictly-later refinement
     that requires no interface change to `CliOutputBuffer`.

3. **Fixed pool vs capped-cached for `App.workerPool`.**
   - What we know: once the log pump moves off (§Finding 7 step 1), both are safe.
   - What's unclear: whether the maintainer prefers the smaller diff (`newFixedThreadPool(4)` +
     named factory) or the explicit `ThreadPoolExecutor` that documents its rejection policy.
   - Recommendation: `newFixedThreadPool(n, namedFactory)` for `workerPool` (short tasks, unbounded
     queue is fine) and an explicit `ThreadPoolExecutor` for `requestExecutor` (where the queue shape
     is load-bearing — §Finding 8). Different answers for different workloads is the point.

4. **Should `App.shutdown()`'s D-03 drain run before or after `workerPool.shutdown()`?**
   - What we know: the existing order is `MainTab → AiRequestLogger → passive scanner → active
     scanner disable → active scanner → supervisor → MCP supervisor → backend registry → worker pool
     → Alerting → Redaction` `[VERIFIED: App.kt:220-246]`.
   - What's unclear: nothing blocking — CLI backends are torn down by `backendRegistry.shutdown()`,
     which precedes `workerPool.shutdown()`.
   - Recommendation: place the drain **after** `safeShutdownStep("Backend registry")` so every
     in-flight CLI call has already had its `finally` run; the drain then reaps only genuine
     stragglers, which is exactly its stated job.

---

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| JDK 21 (Temurin) | Gradle 8.12.1 daemon + `java { toolchain { 21 } }` | ✓ | 21.0.12 (Temurin-21.0.12+8, arm64) | none — **required** |
| Gradle wrapper | all build/test tasks | ✓ | 8.12.1 (`gradle/wrapper/gradle-wrapper.properties`) | none |
| git | commits | ✓ | 2.55.0 | none |
| Maven Central / JetBrains repos | dependency resolution | ✓ | — (a full `test`+`detekt` run succeeded this session) | Gradle cache |
| Burp Suite (running) | manual UAT of SC2/SC5/SC6 in a live session | ✗ (not probed) | — | All six SCs have headless automated coverage; live Burp is confirmatory only. |

**Missing dependencies with no fallback:** none.

⚠ **JDK selection is a hard trap, measured this session.** `/usr/libexec/java_home -V` reports the
default JVM as **OpenJDK 26.0.2** (Homebrew). Gradle 8.12.1 does not support a JDK-26 daemon. Every
Gradle command in every plan must be written as:

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew <tasks>
```

`[VERIFIED: /usr/libexec/java_home -V run this session — 4 JVMs, default 26.0.2; the successful
`test`+`detekt` run used the explicit `-v 21` prefix]`

---

## Validation Architecture

### Test Framework

| Property | Value |
|----------|-------|
| Framework | JUnit Jupiter 6.0.3 (`useJUnitPlatform()`), with mockito-kotlin 5.4.0 and `kotlin("test")` |
| Config file | `build.gradle.kts:152-249` (`tasks.test`) — no separate config file |
| Quick run command | `JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test --tests '*<SuiteName>*'` |
| Full suite command | `JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test detekt ktlintCheck` |
| PR-gate equivalent | `JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test -PexcludeHeavyTests=true` |
| Test JVM args | `-ea -Djava.awt.headless=true` — **no `--add-opens`** (see §Finding 9) |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| REL-06-A (SC1) | `runGuarded` swallows a throw and the **next tick still fires** — real `ScheduledExecutorService`, 10 ms delay, body throws on tick 1, `CountDownLatch` counts ticks 2–3, inside `assertTimeoutPreemptively(Duration.ofSeconds(5))` | unit | `./gradlew test --tests '*GuardedSchedulingTest'` | ❌ Wave 0 |
| REL-06-B (SC1) | `runGuarded` catches **`Throwable`, not only `Exception`** — body throws an `Error`; ticker survives | unit | `./gradlew test --tests '*GuardedSchedulingTest'` | ❌ Wave 0 |
| REL-06-C (SC1) | `runGuarded` logs with the `[Component] …` prefix and the label, so a suppressed failure is visible | unit | `./gradlew test --tests '*GuardedSchedulingTest'` | ❌ Wave 0 |
| REL-06-D (SC1) | **Structural allowlist**: the set of files under `src/main/kotlin` containing `scheduleAtFixedRate(` or `scheduleWithFixedDelay(` equals `{GuardedScheduling.kt}` (or the explicit allowlist the plan chooses). Requires a `tasks.test` `inputs` declaration — §Pitfall 2 | structural | `./gradlew test --tests '*SchedulerGuardCoverageTest'` | ❌ Wave 0 |
| REL-06-E (SC1, optional) | `ActiveAiScanner`'s scheduler survives a `RejectedExecutionException` from `exec.submit` — private `executor` field swapped for a throwing/counting stub; assert submit is attempted on ≥2 ticks | unit (reflection into own class) | `./gradlew test --tests '*ActiveScannerTickSurvivalTest'` | ❌ Wave 0 — include only if plan-check demands the literal per-site reading (Open Question 1) |
| REL-06-F (SC2) | With the `getSettings` lambda throwing for target A only, targets B and C still complete: `getStatus().scansCompleted` reaches 3 and `getStatus().queueSize` reaches 0, inside `assertTimeoutPreemptively` | unit (headless, deep-stub Montoya) | `./gradlew test --tests '*ActiveScannerFailureIsolationTest'` | ❌ Wave 0 |
| REL-06-G (SC2) | The failure log line contains `target.id` — captured via `verify(api.logging()).logToError(argThat { contains(target.id) })` | unit | `./gradlew test --tests '*ActiveScannerFailureIsolationTest'` | ❌ Wave 0 |
| REL-07-A (SC3) | Concurrent `appendLine` from N threads plus concurrent `snapshot()` reads produce no exception and no torn content: every appended line appears whole, character counts are consistent | unit | `./gradlew test --tests '*CliOutputBufferTest'` | ❌ Wave 0 |
| REL-07-B (SC4) | Appending far beyond the cap retains at most `maxChars`, sets `truncated`, and the retained region is the **head** (first bytes preserved) | unit | `./gradlew test --tests '*CliOutputBufferTest'` | ❌ Wave 0 |
| REL-07-C (SC4) | A legitimate-sized answer (e.g. 50 000 chars) round-trips **byte-identically** through the buffer — the anti-corruption assertion that would catch a 2000-char cap | unit | `./gradlew test --tests '*CliOutputBufferTest'` | ❌ Wave 0 |
| REL-07-D (SC3) | **Structural**: `CliBackend.kt` no longer declares `StringBuilder()` for `rawOutput` (grep the source; needs a `tasks.test` input declaration) | structural | `./gradlew test --tests '*CliOutputBufferTest'` | ❌ Wave 0 |
| REL-07-E (SC5) | The D-02 registry: register→size 1; register same file twice→size 1; deregister→size 0; drain deletes a registered file; drain is idempotent when the file is already gone | unit | `./gradlew test --tests '*CliBackendTempFileTest'` | ⚠ File exists — **rewrite** (two vacuous tests deleted, §Finding 9) |
| REL-07-F (SC5) | **Structural**: `CliBackend.kt` contains zero occurrences of `deleteOnExit(` (needs a `tasks.test` input declaration) | structural | `./gradlew test --tests '*CliBackendTempFileTest'` | ❌ Wave 0 |
| REL-07-G (SC6) | `App.workerPool` and `ActiveAiScanner.requestExecutor` are bounded — asserted structurally (no `newCachedThreadPool()` remains in `App.kt` / `ActiveAiScanner.kt`) **and**, where a seam exists, by reading `(pool as ThreadPoolExecutor).maximumPoolSize` | structural + unit | `./gradlew test --tests '*BoundedExecutorTest'` | ❌ Wave 0 |
| REL-07-H (SC6) | Threads produced by the new factories carry the expected names and `isDaemon` — assert directly on the `ThreadFactory` (`factory.newThread(Runnable {}).name` / `.isDaemon`), which needs no live pool | unit | `./gradlew test --tests '*BoundedExecutorTest'` | ❌ Wave 0 |
| REL-07-I (SC6) | `sendRequestWithTimeout` returns `null` (rather than propagating) when the pool rejects — assert by shutting down a locally-constructed executor of the same shape | unit | `./gradlew test --tests '*BoundedExecutorTest'` | ❌ Wave 0 |

**Headless drivability — stated per assertion, as required.** All rows above are headlessly drivable
on `-Djava.awt.headless=true`: none constructs a Swing component, none requires a live Burp, none
requires a real subprocess, and none reflects into `java.base`. The two rows that reflect at all
(REL-06-E, and the `maximumPoolSize` half of REL-07-G) reflect only into **this project's own
classes**, which is unrestricted — the module encapsulation measured in §Finding 9 applies to
`java.base` alone. `ActiveAiScanner` is confirmed headlessly constructible by an existing green suite
`[VERIFIED: ScannerQueueBackpressureTest.kt:20-27]`.

**Assertions explicitly ruled OUT as false-by-construction or vacuous** (Phase 23's lesson):
- ❌ "Assert `deleteOnExit` is / is not registered via `java.io.DeleteOnExitHook` reflection" —
  measured dead on JDK 21, fail-open (§Finding 9).
- ❌ "Assert `processQueue` survives a throw injected into its body" — no such throw site exists on
  the scheduler thread today (§Finding 3). An assertion phrased that way would either be vacuous or
  would silently be testing something else.
- ❌ "Assert the scanner keeps processing after a target failure" **as the sole SC2 assertion** —
  already true today (§Finding 4), so it cannot fail before the fix. The *log-content* assertion
  (REL-06-G) is the one that goes red pre-fix. **Pair them.**
- ❌ Any assertion comparing an elapsed duration to a threshold (§Pitfall 4).
- ❌ "Assert the registry cleaner's next tick fires" without an injected lever — 5-minute interval,
  private field, no throw site (§Pitfall 7).

**Red-before-green gate.** Phase 20 established this as an acceptance criterion
`[VERIFIED: .planning/ROADMAP.md:62]` — *"a test that passes both before and after has not tested the
bypass"*. For Phase 24 the rows that genuinely go red pre-fix are: **REL-06-D** (structural: three
files currently call `scheduleWithFixedDelay` directly), **REL-06-G** (log line has no target id),
**REL-07-B/C/D** (no buffer class exists), **REL-07-F** (`deleteOnExit(` present twice),
**REL-07-G/H** (`newCachedThreadPool()` present twice, no named factories). Rows REL-06-A/B/C and
REL-07-A/E test code that does not exist yet and are red by non-compilation, which is weaker — note
that in the plan rather than claiming them as gates.

### Sampling Rate

- **Per task commit:** `JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test --tests '*<the suite this task touches>*'`
- **Per wave merge:** `JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test detekt ktlintCheck`
  — detekt is not optional here; §Pitfall 3 makes a new-file detekt failure the single most likely
  wave-merge break.
- **Phase gate:** `JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test -PexcludeHeavyTests=true`
  **and** the unfiltered `./gradlew test detekt ktlintCheck shadowJar` green before `/gsd-verify-work`.
  Running both proves no new suite was accidentally named into the exclusion list (§Finding 10).

### Wave 0 Gaps

- [ ] `src/test/kotlin/com/six2dez/burp/aiagent/util/GuardedSchedulingTest.kt` — covers REL-06-A/B/C
- [ ] `src/test/kotlin/com/six2dez/burp/aiagent/util/SchedulerGuardCoverageTest.kt` — covers REL-06-D
- [ ] `src/test/kotlin/com/six2dez/burp/aiagent/scanner/ActiveScannerFailureIsolationTest.kt` — covers REL-06-F/G
- [ ] `src/test/kotlin/com/six2dez/burp/aiagent/backends/cli/CliOutputBufferTest.kt` — covers REL-07-A/B/C/D
- [ ] `src/test/kotlin/com/six2dez/burp/aiagent/backends/cli/CliBackendTempFileTest.kt` — **rewrite**, covers REL-07-E/F
- [ ] `src/test/kotlin/com/six2dez/burp/aiagent/BoundedExecutorTest.kt` (package TBD) — covers REL-07-G/H/I
- [ ] `build.gradle.kts` — `tasks.test` `inputs` declaration(s) for every structural assertion (REL-06-D, REL-07-D, REL-07-F, REL-07-G). **Without this the guards are cache-served and never run** (§Pitfall 2).
- [ ] Framework install: **not needed** — JUnit 6.0.3 + mockito-kotlin 5.4.0 already declared.

---

## Security Domain

`security_enforcement` is absent from `.planning/config.json`
`[VERIFIED: .planning/config.json — full file read this session; keys are model_profile, commit_docs,
parallelization, search_gitignored, brave_search, firecrawl, exa_search, git, workflow, hooks,
project_code, phase_naming, agent_skills, features, resolve_model_ids, mode, granularity]`, therefore
enabled by default.

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | no | This phase touches no auth path. The MCP bearer-token work is Phase 25 (SEC-07). |
| V3 Session Management | no | No session state changes. `cliSessionIdRef` is read but not modified. |
| V4 Access Control | **indirect — must not regress** | `T-23-06-07` records that an unintended `McpSupervisor.stop()` clears `ScannerTaskRegistry` and `CollaboratorRegistry` `[VERIFIED: .planning/phases/23-edt-confinement-ui-responsiveness/23-SECURITY.md:62]`. Phase 24 edits both registries. **Control: do not widen either registry's visibility or add a new public entry point.** The guard change must stay inside the existing `init` block and `private fun cleanupExpired`. |
| V5 Input Validation | **yes** | The CLI output buffer is a boundary that ingests untrusted subprocess bytes. Control: hard cap at `Defaults.MAX_CLI_OUTPUT_CHARS`, applied at append time. |
| V6 Cryptography | no | Nothing cryptographic in scope. `SecretCipher` untouched. |
| V7 Error Handling & Logging | **yes** | SC2 adds attacker-influenced data (a target URL) to a log line. Control: it is Burp's local error log, not an AI backend, so redaction does not apply — but truncate (`target.id.take(200)`) and never interpolate response bodies. |
| V12 File & Resource | **yes** | Temp-file lifecycle (SC5). Control: `Files.setPosixFilePermissions` OWNER_READ/OWNER_WRITE already applied to the prompt file `[VERIFIED: CliBackend.kt:143-152]` — **the D-01 rewrite must not drop it**. |

### Known Threat Patterns for Kotlin/JVM + Burp extension

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| A chatty or hostile CLI subprocess exhausts extension heap via unbounded stdout (`OutOfMemoryError` in Burp's JVM, taking Burp down) | Denial of Service | The SC4 cap. **This is the security case for SC4** and should be named as such in the plan — a 5× transient amplification through `stripAnsiCodes` makes the unbounded case worse than it first appears (§Finding 5). |
| A single malformed target permanently disables the active scanner for the session, silently | Denial of Service | SC1's guard + SC2's log line. Silence is the aggravating factor: today the operator has no signal. |
| Bounding an executor with `AbortPolicy` converts saturation into an uncaught throw on a scheduler thread, permanently killing that ticker | Denial of Service | §Finding 3 — SC1 must land with or before SC6. This is a threat *introduced by this phase* if ordered wrong; it belongs in the phase's threat table. |
| An unremoved extension-classloader shutdown hook pins a dead classloader across reloads (heap growth per reload) | Denial of Service | D-03's `removeShutdownHook`. §Finding 6 sharpens why this applies to the *new* hook specifically. |
| A temp file containing prompt content (which may include captured request/response data) survives a hard kill | Information Disclosure | Accepted and bounded by D-04; the KDoc must name the window. Preserve the existing OWNER_READ/OWNER_WRITE permissions. |
| A target URL with credentials in the query string reaches the Burp error log via SC2's new line | Information Disclosure | Local log only, never an AI backend; consistent with `ActiveAiScanner.kt:1099`'s existing `request.url().take(80)`. Truncate. |
| Supply chain | Tampering | Not applicable — no dependency added. Discharge exactly as `T-23-06-SC` did: `git show` the phase's build-file commits and confirm `gradle/libs.versions.toml` untouched. |

---

## Sources

### Primary (HIGH confidence)

- **This repository, read this session** (all `[VERIFIED: path:lines]` citations above):
  `src/main/kotlin/.../scanner/ActiveAiScanner.kt`, `.../scanner/ActiveScanModels.kt`,
  `.../mcp/tools/ScannerTaskRegistry.kt`, `.../mcp/tools/CollaboratorRegistry.kt`,
  `.../supervisor/AgentSupervisor.kt`, `.../backends/cli/CliBackend.kt`, `.../App.kt`,
  `.../config/Defaults.kt`, `.../config/AgentSettings.kt`,
  `src/test/kotlin/.../cli/CliBackendTempFileTest.kt`, `.../scanner/ScannerQueueBackpressureTest.kt`,
  `.../scanner/ActiveScannerDedupTest.kt`, `.../mcp/tools/RegistryTtlTest.kt`,
  `.../ui/SettingsPersistQueueTest.kt`, `build.gradle.kts`, `detekt.yml`, `detekt-baseline.xml`,
  `.github/workflows/build.yml`, `.github/workflows/nightly-regression.yml`,
  `.github/workflows/release.yml`, `AGENTS.md`, `CLAUDE.md`.
- **JDK 21 `src.zip`** from the project's own toolchain
  (`/Library/Java/JavaVirtualMachines/temurin-21.jdk`, `openjdk 21.0.12 2026-07-21 LTS`):
  `java.base/java/util/concurrent/ScheduledExecutorService.java:138-144, :181-187`,
  `java.base/java/util/concurrent/Executors.java:217-219`,
  `java.base/java/io/DeleteOnExitHook.java:37-38, :69-76`, `java.base/java/io/File.java:1078-1105`,
  `java.base/java/lang/StringBuilder.java:70-72`,
  `java.base/java/util/concurrent/ThreadPoolExecutor.java:2038, :2065, :2089`.
- **Executed probes:** `java /tmp/DoxProbe.java` on JDK 21.0.12 (§Finding 9);
  `./gradlew test --tests '*CliBackendTempFileTest*' detekt` → exit 0.
- **Planning artifacts:** `.planning/REQUIREMENTS.md:29-30, :34`, `.planning/ROADMAP.md:304-316, :62`,
  `.planning/STATE.md`, `.planning/phases/24-…/24-CONTEXT.md`,
  `.planning/phases/23-…/23-CONTEXT.md:72-79, :111-123`, `.planning/phases/23-…/23-SECURITY.md:62-63`,
  `.planning/codebase/CONVENTIONS.md:186-200`, `.planning/codebase/CONCERNS.md:123-131`.

### Secondary (MEDIUM confidence)

- `[CITED: docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/ScheduledExecutorService.html]`
  — cross-check of §Finding 2, agrees with `src.zip` verbatim.
- `[CITED: docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/ThreadPoolExecutor.html]`
  — §"Rejected tasks" four policies and §"Queuing" three strategies. Used for the rejection-policy
  analysis in §Findings 7, 8.
- `[CITED: docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/Executors.html]`
  — `newFixedThreadPool` "shared unbounded queue" wording. ⚠ The fetched summary mischaracterised
  `newCachedThreadPool`'s queue as "unbounded"; corrected against `src.zip` (it is a
  `SynchronousQueue`). Prefer the `src.zip` citation.
- `[CITED: docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/StringBuilder.html]`
  — cross-check of the thread-safety statement, agrees with `src.zip`.

### Tertiary (LOW confidence)

- Assumptions A1 (Montoya `sendRequest` interruptibility) and A2 (torn-`StringBuilder` crash shape) —
  training knowledge, flagged in the Assumptions Log, neither load-bearing for any success criterion.

---

## Metadata

**Confidence breakdown:**
- Standard stack: **HIGH** — no new dependency; every API used was read in the JDK 21 source shipped
  with the project's own toolchain.
- Architecture: **HIGH** — the recurring-schedule and executor inventories are exhaustive greps of
  `src/main/kotlin`, cross-checked against each file's source; the two guarded reference
  implementations were read verbatim.
- Pitfalls: **HIGH** — five of the seven are measured this session (the JDK-21 reflection failure, the
  `-PexcludeHeavyTests` list and its CI usage, the detekt baseline keying, the `workerPool` infinite
  task, the `processQueue` throw-site absence). Two (the wall-clock flake, the stale-cache defect) are
  quoted from in-repo records of prior measurements.
- Test strategy: **HIGH** for drivability (an existing green suite proves headless `ActiveAiScanner`
  construction); **MEDIUM** for the SC2 timing budget (assumption A7, with a documented fallback).
- Sizing constants: **MEDIUM** — derived from measured demand and existing repo constants, not from a
  benchmark. Flagged as A3/A4 for user confirmation.

**Research date:** 2026-08-21
**Valid until:** 2026-09-20 (30 days — the domain is the JDK's stable concurrency API plus this
repository's own source; the only volatile input is the repo itself, which changes only through this
phase)
