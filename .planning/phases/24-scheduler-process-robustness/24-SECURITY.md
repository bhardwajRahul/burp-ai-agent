---
phase: 24
slug: scheduler-process-robustness
status: verified
# threats_open = count of OPEN threats at or above workflow.security_block_on severity (the blocking gate)
threats_open: 0
asvs_level: 1
block_on: high
created: 2026-08-21
---

# Phase 24 — Security

> Per-phase security contract: threat register, accepted risks, and audit trail.

**Register origin:** authored at plan time. All five `*-PLAN.md` files carry a `<threat_model>` block,
and all five `*-SUMMARY.md` files carry a `## Threat Flags` discharge record. No retroactive STRIDE was
required — a direct improvement on Phase 23, where four of eight SUMMARYs omitted the section and forced
a full 14-threat audit.

**Short-circuit applied:** `threats_open: 0` AND `register_authored_at_plan_time: true` AND
`asvs_level == 1` — L1 grep-depth is sufficient, so no auditor subagent was spawned. Two threats that
intersect this session's code-review findings were nevertheless checked individually (T-24-06, T-24-15,
below).

---

## Trust Boundaries

| Boundary | Description | Data Crossing |
|----------|-------------|---------------|
| JDK scheduler → recurring task body | `scheduleWithFixedDelay` cancels the task permanently on any escaping `Throwable`. `runGuarded` is the control. | none — a control boundary, not a data one |
| CLI subprocess → extension heap | Bytes from an external process's stdout cross into JVM memory. Previously unbounded and unsynchronised; now through one monitored, capped accumulator. | arbitrary subprocess output |
| reader thread → timeout path | `readerThread.join(2000)` can time out while the reader is still appending. | the captured output buffer |
| extension → OS temp directory | Two temp files per CLI call, one holding the full prompt. | prompt content |
| JVM exit hook / `App.shutdown()` → temp-file registry | Two independent drains over the same live-file set; must be idempotent. | temp file paths |
| scanner thread pool → target under test | A bounded pool now caps concurrent outbound requests. | HTTP requests to the target |
| Gradle dependency graph → shipped artifact | A new dependency would widen the supply-chain surface. | none this phase — no dependency added |

---

## Threat Register

25 register rows across five plans; 20 unique threats (T-24-02 appears in both 24-01 and 24-02;
`T-24-SC` is declared once per plan). All discharged.

| Threat ID | Category | Component | Sev | Disposition | Status |
|-----------|----------|-----------|-----|-------------|--------|
| T-24-01 | Denial of Service | the three unguarded recurring sites | high | mitigate | closed — `runGuarded`/`scheduleGuarded`; `GuardedSchedulingTest` (5), `SchedulerGuardCoverageTest` (3) |
| T-24-02 | Denial of Service | `exec.submit` on the scheduler thread | high | mitigate | closed — the SC1↔SC6 coupling; guard lands wave 1, both bounding plans depend on it |
| T-24-03 | Information Disclosure | the new `Target scan failed:` log line | low | accept | closed (accepted) — R-24-01 |
| T-24-04 | Denial of Service | `CliBackend` stdout capture | high | mitigate | closed — `CliOutputBuffer` capped at 262 144 |
| T-24-05 | Tampering | the cap value | medium | mitigate | closed — `Defaults.MAX_CLI_OUTPUT_CHARS`, round-trip test proves no legitimate answer truncated |
| T-24-06 | Information Disclosure | CLI prompt temp file | low | accept | closed (accepted) — R-24-02. **⚠ see qualification below** |
| T-24-07 | Denial of Service | the new `Runtime` shutdown hook | medium | mitigate | closed — one hook, lazily registered, removed on `App.shutdown()` |
| T-24-08 | Denial of Service | `requestExecutor` | high | mitigate | closed — `ThreadPoolExecutor(0, 32, SynchronousQueue, AbortPolicy)` |
| T-24-09 | Denial of Service | `App.workerPool` | high | mitigate | closed — `newFixedThreadPool(4, namedFactory)` |
| T-24-10 | Elevation of Privilege | the two MCP registries | medium | mitigate | closed — `git diff -U0` over `mcp/tools/` shows no member added, no visibility widened |
| T-24-11 | Tampering | structural gate vs the Gradle build cache | medium | mitigate | closed — `inputs.dir("src/main/kotlin")`; cache counterfactual measured, not assumed |
| T-24-12 | Denial of Service | `sendRequestWithTimeout` | high | mitigate | closed — submit moved inside the `try`; rejection degrades to null |
| T-24-13 | Tampering | `CliBackend` stdout capture | high | mitigate | closed — single monitor; zero `StringBuilder` remains |
| T-24-14 | Denial of Service | `stripAnsiCodes` | medium | mitigate | closed — bounded input caps the transient heap amplification |
| T-24-15 | Denial of Service | JVM exit-time deletion set | medium | mitigate | closed — **twice**, see note below |
| T-24-16 | Denial of Service | `App.shutdown()` sequence | low | mitigate | closed — CLI-temp-files step ordered after "Backend registry", before "Worker pool" |
| T-24-17 | Denial of Service | `App.workerPool` unbounded | high | mitigate | closed — bounded at `MAX_WORKER_THREADS` |
| T-24-18 | Denial of Service | `workerPool` rejection policy | high | mitigate | closed — unbounded queue, no rejection policy; `CallerRunsPolicy` prohibited with the EDT reason |
| T-24-19 | Denial of Service | the new service pump thread | medium | mitigate | closed — named daemon thread; pump moved off the pool *before* bounding |
| T-24-SC | Tampering | Gradle dependency graph | high | mitigate | closed — no dependency added by any plan; `build.gradle.kts` diff is additions-only and touches no line in `dependencies { }` |

*Status: open · closed · open — below `high` threshold (non-blocking)*
*Disposition: mitigate (implementation required) · accept (documented risk) · transfer (third-party)*

### ⚠ T-24-06 — accepted, with a platform qualification the register did not state

The plan's `accept` rationale names the compensating control explicitly: *"`Files.setPosixFilePermissions`
OWNER_READ/OWNER_WRITE."* Review finding **WR-11** (recorded this session) establishes that this control
**does not exist on Windows** — `CliBackend.kt:149-153` wraps `setPosixFilePermissions` in
`catch (_: UnsupportedOperationException)` and skips it on non-POSIX filesystems.

The acceptance still stands: the residual is bounded at one temp file per in-flight call, in the OS's own
temp directory, and only after a hard kill. But it is accepted **on the strength of a control that is
present on POSIX and absent on Windows**, and the register did not say so. Recorded here so a future
reader does not inherit the unqualified claim. WR-11 remains open by explicit decision.

### T-24-15 — closed twice, worth recording why

The plan's mitigation (D-01, replacing per-invocation `deleteOnExit()` with a live-file registry) was
sound, but the *new* mechanism reconstituted the same unbounded-growth defect on two paths the plan had
not enumerated:

1. **During execution**, plan 24-04's executor found a `return@submit` in the prompt-write failure branch
   that exits the executor lambda before the outer `try/finally`, so its two planned deregistrations
   could not run. Every failed prompt write would have retained a permanent entry. It added a third
   deregistration site and asserted **3**, not the plan's 2 — asserting the plan's number would have
   required deleting the fix.
2. **After execution**, code review found **CR-02**: a file whose `delete()` returned `false` was
   deregistered anyway. `File.delete()` returns a Boolean rather than throwing, so the surrounding
   comment's stated rationale was reasoning about a failure mode that cannot occur. On Windows the child
   process holds the handle and `delete()` returns `false` routinely. Fixed in `19691c7` via
   `deleteAndDeregister`, which drops the entry only when the file is actually gone.

Post-fix the coverage exceeds what `deleteOnExit()` provided: that facility retried only at clean JVM
exit, whereas a failed delete is now retried by both the exit hook and `App.shutdown()`'s drain.

---

## Accepted Risks Log

| Risk ID | Threat Ref | Rationale | Accepted By | Date |
|---------|------------|-----------|-------------|------|
| R-24-01 | T-24-03 | The per-target scan failure log line now carries `target.id` (url + injection point + vuln class). It reaches Burp's **local** error log only, never an AI backend, and the id is bounded by `.take(200)`. Required by SC2 — a failure that cannot be attributed to a target is not actionable. | maintainer (24-02) | 2026-08-21 |
| R-24-02 | T-24-06 | A temp file holding prompt content survives a hard kill, because on SIGKILL or power loss neither the `finally` nor any hook runs. Bounded at one file per in-flight call in the OS temp directory; named in `CliTempFileRegistry`'s `Window NOT closed:` KDoc rather than papered over (D-04). **Qualified:** the stated POSIX-permissions compensating control is absent on Windows — see WR-11. | maintainer (24-04, D-04) | 2026-08-21 |

*Accepted risks do not resurface in future audit runs.*

---

## Open Review Findings (not phase blockers)

The phase's code review recorded 2 Critical, 11 Warning, 9 Info. **Both Criticals are resolved**
(`2446da1`, `19691c7`) and were verified in code, not merely in the review's status line. The 20
Warning/Info findings remain open by explicit decision. Those bearing on security:

| Finding | Bearing |
|---------|---------|
| **WR-04** | The two MCP registry cleaner executors are never shut down — every extension reload leaks a scheduled daemon thread pinning a dead classloader and a `MontoyaApi`-capturing lambda. Same defect class as T-24-07, in two files this phase edited. **Pre-existing, not introduced here.** |
| **WR-03** | A narrow `shutdown()`/`register()` race could arm a hook that is never removed. |
| **WR-11** | The T-24-06 compensating-control claim, above. |
| **WR-10** | A rejected submit drops the polled target and abandons the rest of that tick's batch — now *silently*, because the guard absorbs the throw. Availability, not confidentiality; the subsystem stays alive. |

---

## Security Audit Trail

| Audit Date | Threats Total | Closed | Open | Run By |
|------------|---------------|--------|------|--------|
| 2026-08-21 | 20 unique (25 rows) | 20 | 0 | orchestrator short-circuit (ASVS L1, block_on `high`, register authored at plan time) |

---

## Sign-Off

- [x] All threats have a disposition (mitigate / accept / transfer)
- [x] Accepted risks documented in Accepted Risks Log
- [x] `threats_open: 0` confirmed
- [x] `status: verified` set in frontmatter

**Approval:** verified 2026-08-21
