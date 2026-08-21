# Phase 24: Scheduler & Process Robustness - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-08-21
**Phase:** 24-scheduler-process-robustness
**Areas discussed:** Temp-file cleanup

---

## Area selection

Four gray areas were offered. The user selected **one**.

| Option | Description | Selected |
|--------|-------------|----------|
| Scheduler guard shape | Inline try/catch at the 3 unguarded sites vs a shared wrapper; whether `processQueue` also needs per-target isolation, and what identifies the failing target in the log | |
| CLI output buffer | What replaces the shared `StringBuilder`; where the cap sits given `:275` uses the full buffer as the real response | |
| Pool bounds | Fixed pool vs `ThreadPoolExecutor` with a bounded queue; saturation policy (CallerRuns / Abort / log-and-drop) | |
| Temp-file cleanup | What replaces `deleteOnExit()`; note Burp extension unload is not JVM exit | ✓ |

**Notes:** The three unselected areas were not dropped — they are recorded in CONTEXT.md under
"Claude's Discretion" with their measured code findings, so the researcher and planner inherit the
groundwork rather than repeating it.

---

## Temp-file cleanup

### Q1 — What replaces the per-invocation `deleteOnExit()` as the crash-safety net?

| Option | Description | Selected |
|--------|-------------|----------|
| One hook, sweeps a live-file registry | `ConcurrentHashMap`-backed set of in-flight temp files; existing `finally` removes its entry; one hook registered once deletes what remains. Bounded by concurrent calls, not lifetime invocations | ✓ |
| One hook, sweeps a dedicated temp subdirectory | `burp-ai-agent/` subdir swept at exit; also reaps files orphaned by a previously crashed session — the only option that survives SIGKILL | |
| Drop it — the `finally` block is the cleanup | Delete both `deleteOnExit()` calls, add nothing. Smallest diff; accepts one stray file in the clean-quit-mid-call window | |

**User's choice:** One hook, sweeps a live-file registry → **D-01**

**Notes:** Grounding established before the question — both temp files are already deleted by the
same `finally` block (`CliBackend.kt:288-307`), which covers the normal and exception paths, so
`deleteOnExit()` is purely a safety net and it is the net that leaks (one never-removed
`java.io.DeleteOnExitHook` entry per invocation, surviving extension unload/reload).
`DELETE_ON_CLOSE` was ruled out at source: `codex-cli` writes `outputFile` as an external process.
A scenario table was used to establish that the hook's *only* unique coverage is a clean Burp quit
mid-call — hard kills defeat both mechanisms, and extension unload defeats the hook alone.

### Q2 — Where should the live-file registry and the single shutdown hook live?

| Option | Description | Selected |
|--------|-------------|----------|
| New `internal object` in the `cli` package | Matches `buildTimeoutMessage` (`:848`) / `buildCopilotCommand` (`:878`) extraction pattern, each with its own headless suite; gives the rewritten tests a pure-JVM seam; keeps `CliBackend.kt` from growing past 1043 lines | ✓ |
| Private companion object inside `CliBackend` | Smallest diff, concern stays local — but only exercisable by driving a real CLI subprocess | |
| Owned by `App` lifecycle | Hook removal on unload comes free and consistently; costs a cli→App coupling and an App stand-in in tests | |

**User's choice:** New `internal object` in the `cli` package → **D-02**

**Notes:** Decided on assertability. A companion-object registry reproduces the un-assertable-seam
problem Phase 23 hit repeatedly. Surfaced during this question: `CliBackendTempFileTest` already
exists and contains two tests (`:95`, `:106`) that assert `deleteOnExit` registration **happens** —
they pin the behaviour being removed and must be inverted in the same commit.

### Q3 — When is the hook registered, and what happens to it on extension unload?

| Option | Description | Selected |
|--------|-------------|----------|
| Lazy register; remove + sweep on `App.shutdown()` | Register on first temp-file creation; `removeShutdownHook` AND drain the registry at shutdown. Closes reload accumulation and unload-mid-call together | ✓ |
| Lazy register; never remove | One hook per load, left for Burp's lifetime. Each reload pins a dead classloader — same defect class, coarser | |
| Eager register at load; remove + sweep | Same leak-free lifecycle, but pays for the subsystem in sessions that never touch a CLI backend | |

**User's choice:** Lazy register; remove + sweep on `App.shutdown()` → **D-03**

**Notes:** The framing was that a never-removed hook trades a per-invocation leak for a per-reload
leak. `App.shutdown()` already owns this ordering (`App.kt:221` → `:233` → `:237`) and Phase 23
touched that exact sequence, so the insertion point is known. Drain must be idempotent with the
`finally` block.

### Q4 — Accept the SIGKILL residual, or add a bounded sweep?

| Option | Description | Selected |
|--------|-------------|----------|
| Accept and document the residual | Name it in KDoc: at most one temp file per in-flight call, OS reaps its own temp dir. Matches Phase 23's discipline of naming the window a mechanism does NOT close | ✓ |
| Add a bounded prefix sweep at load | Delete `burp_uv_prompt_` / `burp-ai-agent-codex` files older than a threshold; reaps crashed sessions, but the age threshold becomes load-bearing against a second concurrent Burp instance | |
| Revisit — switch D-01 to the directory sweep | Reopens D-01; the only design closing the gap without an age heuristic, at the cost of directory lifecycle | |

**User's choice:** Accept and document the residual → **D-04**

**Notes:** By construction a memory-resident registry cannot survive a hard kill, since neither the
`finally` nor the hook runs. The KDoc statement is treated as a deliverable, not a nicety.

### Continue check

| Option | Description | Selected |
|--------|-------------|----------|
| I'm ready for context | Write CONTEXT.md; unselected areas recorded as delegated with their findings | ✓ |
| Discuss the three unselected areas | Lock SC1–SC4 and SC6 too | |
| More questions on temp-file cleanup | Sweep logging policy; how the rewritten tests assert register/unregister/sweep | |

**User's choice:** I'm ready for context

---

## Claude's Discretion

- **Scheduler guard shape (SC1, SC2)** — guard placement, shared helper vs inline, per-target
  isolation inside the tick, and the log field identifying the failing target.
- **CLI output buffer (SC3, SC4)** — replacement for the shared `StringBuilder` and where the cap
  sits. Carries a measured trap: `CliBackend.kt:275` uses the full buffer as the real model
  response, so the bound must never be 2000.
- **Pool bounds and saturation policy (SC6)** — pool sizing and rejection policy for
  `App.workerPool` and `ActiveAiScanner.requestExecutor`.

One follow-up question was offered and not taken, left to the planner: whether the registry sweep
logs deletion failures. `CONVENTIONS.md` §Error Handling holds a real tension here — "never swallow
silently without at least a log" versus the explicitly blessed `catch (_: Exception) {}` for
cleanup calls in `finally` blocks.

## Deferred Ideas

- Bounded prefix sweep of orphaned temp files at extension load (rejected in D-04).
- Dedicated `burp-ai-agent/` temp subdirectory (the D-01 option B alternative).
- Passive scanner dedup cache unbounded per-session growth (`CONCERNS.md:125`) — same family, not
  named by REL-06/REL-07, out of scope.
