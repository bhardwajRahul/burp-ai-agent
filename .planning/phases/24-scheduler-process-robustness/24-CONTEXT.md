# Phase 24: Scheduler & Process Robustness - Context

**Gathered:** 2026-08-21
**Status:** Ready for planning

<domain>
## Phase Boundary

Background subsystems survive their own exceptions and stay bounded. Concretely:

- Every recurring `scheduleWithFixedDelay` / `scheduleAtFixedRate` task keeps running after a throw
  in its body (REL-06).
- CLI subprocess output capture is thread-safe and bounded, and CLI temp-file cleanup no longer
  accumulates a JVM shutdown-hook entry per invocation (REL-07).
- `App.workerPool` and `ActiveAiScanner.requestExecutor` use bounded pools with named thread
  factories (REL-07).

**Not in this phase:** new scanner capabilities, new backends, changes to what the scheduled tasks
*do*. This phase changes how failure and resource use are contained, never what the subsystems
compute.

</domain>

<decisions>
## Implementation Decisions

Only one of the four identified gray areas was discussed in this session (temp-file cleanup). The
other three are recorded under **Claude's Discretion** with their measured findings, so downstream
agents do not rediscover them from scratch.

### CLI temp-file cleanup (SC5)

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

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

ROADMAP.md declares no `Canonical refs:` line for Phase 24; the list below was accumulated from
REQUIREMENTS.md, the codebase maps, and the code read during this discussion.

### Requirements
- `.planning/REQUIREMENTS.md` §REL-06 (line 29) — every recurring scheduled task survives a throw,
  matching the guard already on `AgentSupervisor.checkHealth` and the OAST poller. Findings 6.
- `.planning/REQUIREMENTS.md` §REL-07 (line 30) — CLI output thread-safe and bounded,
  `deleteOnExit()` accumulation, unbounded `newCachedThreadPool()` use. Findings 9, 13, 14.
- `.planning/ROADMAP.md` §"Phase 24: Scheduler & Process Robustness" — the six success criteria.

### Conventions this phase is bound by
- `.planning/codebase/CONVENTIONS.md` §Error Handling — the `try/catch(e: Exception)` at process and
  network boundaries idiom, `api.logging().logToError("[Component] ${e.message}")` logging format,
  the blessed `catch (_: Exception) {}` for `destroy()`/`close()` in `finally` blocks, and the
  `AiScanCheck` per-payload failure-isolation precedent.
- `.planning/codebase/CONCERNS.md` — existing recorded concerns; note the unbounded-growth entries
  already logged there (passive scanner dedup cache, line 125) are **not** in this phase's scope.

### Reference implementations to copy, not invent
- `src/main/kotlin/com/six2dez/burp/aiagent/supervisor/AgentSupervisor.kt:79-93` — the guarded
  `scheduleAtFixedRate`, including the comment explaining the failure mode.
- `src/main/kotlin/com/six2dez/burp/aiagent/scanner/ActiveAiScanner.kt:346-353` — the guarded OAST
  poller.
- `src/main/kotlin/com/six2dez/burp/aiagent/mcp/tools/ScannerTaskRegistry.kt:20-23` — named daemon
  thread factory pattern.

### Prior-phase decisions that constrain this phase
- `.planning/phases/23-edt-confinement-ui-responsiveness/23-CONTEXT.md` §D-02 — "the guarantee lives
  in two places at once": a shared helper AND a fail-fast structural check.
- `.planning/phases/23-edt-confinement-ui-responsiveness/23-CONTEXT.md` §D-05 — reuse the existing
  concurrency idiom rather than introducing a new one.
- `.planning/phases/23-edt-confinement-ui-responsiveness/23-CONTEXT.md` §D-14 — minimal blast
  radius: move the callers, do not restructure the component.
- `.planning/phases/23-edt-confinement-ui-responsiveness/23-SECURITY.md` §T-23-06-07 — an
  unintended `McpSupervisor.stop()` clears `ScannerTaskRegistry` and `CollaboratorRegistry`. Phase
  24 touches both registries' cleaners; do not widen who can reach them.

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- **`CliBackend.kt:288-307` `finally` block** — already a complete primary cleanup path for BOTH
  temp files (`promptFile` at `:296`, `outputFile` at `:303`), covering the normal and exception
  paths. D-01 keeps it as primary and demotes the hook to a genuine safety net.
- **Top-level `internal fun` extraction pattern** — `buildTimeoutMessage` (`:848`),
  `buildCopilotCommand` (`:878`), each with a dedicated headless suite. D-02 follows it.
- **Named daemon thread factories** — `ScannerTaskRegistry.kt:21-23`,
  `CollaboratorRegistry.kt:20-22`. SC6 needs the same shape for the two pools it bounds.

### Established Patterns
- **The guard idiom is already written twice** (`AgentSupervisor.kt:81-93`,
  `ActiveAiScanner.kt:348-353`). SC1 is closing three sites that were missed, not introducing a
  mechanism.
- **`App.shutdown()` owns lifecycle ordering** (`App.kt:221`, `:233`, `:237`) — the natural home for
  D-03's `removeShutdownHook` + registry drain.

### Integration Points
- `CliBackend.kt:122-123` (codex `outputFile`) and `:135-138` (`promptFile`) — the two
  `deleteOnExit()` call sites D-01 replaces.
- `App.kt` `shutdown()` — new call into the cli-package registry object (D-03).

### ⚠ Blocking test constraint for the planner
`src/test/kotlin/com/six2dez/burp/aiagent/backends/cli/CliBackendTempFileTest.kt` contains two tests
that reach into the JDK-internal `DeleteOnExitHook` by reflection and assert registration **happens**:

- `uvPromptDeleteOnExitIsRegistered` (`:95`)
- `codexOutputDeleteOnExitIsRegistered` (`:106`)

They pin exactly the behaviour D-01 removes. They must be **inverted in the same commit** as the
production change — leaving them turns the fix red, and deleting them without replacement drops the
only existing coverage of this path. The two surviving tests in that file
(`uvPromptTempFileIsCleanedUpAfterFailure` `:41`, `codexOutputTempFileIsCleanedUpAfterFailure` `:65`)
assert the `finally`-path behaviour D-01 preserves and should stay green unchanged — which makes them
a useful control.

</code_context>

<specifics>
## Specific Ideas

- The hook must cover the one scenario it uniquely can: **a clean Burp quit while a CLI call is in
  flight.** That is the entire justification for keeping a hook at all after D-01; the `finally`
  covers every other non-hard-kill path.
- The registry drain and the `finally` delete must be **idempotent** — both can run for the same
  file, and a double-delete must not surface as an error.
- Per D-04, the KDoc on the new object is a deliverable, not a nicety: it must state the window the
  mechanism does not close.

</specifics>

<deferred>
## Deferred Ideas

- **Bounded prefix sweep of orphaned temp files at extension load** — rejected in D-04 because a
  second concurrent Burp instance can own live files matching `burp_uv_prompt_` /
  `burp-ai-agent-codex`, making the age threshold load-bearing. Revisit only if stray temp files are
  observed in practice.
- **Dedicated `burp-ai-agent/` temp subdirectory** — the D-01 option B alternative. It is the only
  design that closes the hard-kill gap without an age heuristic; worth reconsidering if the accepted
  residual ever becomes a real complaint.
- **Passive scanner dedup cache unbounded per-session growth** (`CONCERNS.md:125`) — an unbounded-
  growth issue of the same family, but not named by REL-06 or REL-07 and out of scope here.

</deferred>

---

*Phase: 24-scheduler-process-robustness*
*Context gathered: 2026-08-21*
