---
phase: 24-scheduler-process-robustness
plan: 04
subsystem: backends
tags: [kotlin, jvm, shutdown-hook, temp-files, lifecycle, structural-test, junit5]

# Dependency graph
requires:
  - phase: 24-scheduler-process-robustness
    plan: 01
    provides: "the `tasks.test` input `mainSourceTreeStructuralInputs` (`inputs.dir(\"src/main/kotlin\")`) without which REL-07-F and the D-03 ordering gate are cache-served and never run"
  - phase: 24-scheduler-process-robustness
    plan: 03
    provides: "the restructured `CliBackend` capture region (`CliOutputBuffer`) that this plan's finally-block edits sit beside, and the comment-stripping `codeLinesOf` reader in the same test package"
  - phase: 23-edt-confinement-ui-responsiveness
    provides: "the ledger-message assertion style, and `SettingsPanel.kt`'s discipline of naming the window a mechanism does NOT close instead of overclaiming"
provides:
  - "`backends/cli/CliTempFileRegistry.kt` — `internal object` with `register` / `deregister` / `drain` / `shutdown` plus three `…ForTests` seams; one exit hook armed lazily on the first temp file and dropped on unload"
  - "Both `CliBackend` temp-file sites routed onto the registry; zero occurrences of the JDK's per-invocation exit-time deletion registration remain in that file"
  - "Three deregistration sites in `CliBackend` — two in the finally block, one in the prompt write-failure branch that returns before it"
  - "`App.shutdown()` step `safeShutdownStep(\"CLI temp files\")`, between `\"Backend registry\"` and `\"Worker pool\"`"
  - "`CliBackendTempFileTest` — 11 tests; SC5 has real coverage for the first time (it had zero before this plan)"
affects: [24-05, any future change to CliBackend's temp-file lifecycle or App.shutdown()'s step order]

actuals:
  tokens: 9449
  tasks: 3
  commits: 4

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "A JVM exit hook is armed lazily by the first thing that needs it, held in one nullable field behind a monitor, and unregistered on extension unload — never armed at load, never left behind"
    - "A cleanup registry is an `internal object` in its own file rather than a private companion, so every path is drivable in pure JVM instead of only through a real subprocess"

key-files:
  created:
    - src/main/kotlin/com/six2dez/burp/aiagent/backends/cli/CliTempFileRegistry.kt
  modified:
    - src/main/kotlin/com/six2dez/burp/aiagent/backends/cli/CliBackend.kt
    - src/main/kotlin/com/six2dez/burp/aiagent/App.kt
    - src/test/kotlin/com/six2dez/burp/aiagent/backends/cli/CliBackendTempFileTest.kt

key-decisions:
  - "The prompt write-failure branch needed a THIRD deregistration the plan did not anticipate: it `return@submit`s before the outer try/finally, so it is the only place that can clear its own entry. Without it the registry retained one entry per failed write for the JVM lifetime — the exact unbounded growth D-01 exists to remove. The structural gate asserts 3, not the plan's 2."
  - "Deregistration is ordered AFTER the delete in every pairing. If the delete throws, the entry survives and the drain sweeps the file later; the reverse order would drop the file entirely."
  - "A seventh member, `isHookRegisteredForTests()`, was added beyond the plan's declared API. D-03's laziness and removal are otherwise unobservable without reflecting into `java.base`, which 24-VALIDATION.md rules out as fail-open."
  - "`shutdown()` deliberately hand-rolls no catch around the hook removal, per the plan — `App.safeShutdownStep` already catches the `IllegalStateException` raised when the VM is already shutting down, and in that case the hook itself performs the drain."
  - "The registry KDoc says `the JVM's hook-removal call` rather than naming the method, because the acceptance criterion pins `grep -c 'removeShutdownHook'` to exactly 1 line and the call site is that line."
  - "Red-before-green for REL-07-F used `git checkout <ref> -- <path>` on a single file, not the plan's `git stash push`: `refs/stash` is shared across every linked worktree (same substitution 24-03 made)."

patterns-established:
  - "CLI temp files go through `CliTempFileRegistry`: `register` at creation, `deregister` beside every delete INCLUDING on branches that return before the finally block. Never re-introduce a per-invocation JVM exit registration — the JDK's set has no removal API."

requirements-completed: []

coverage:
  - id: D1
    description: "Registering the same file twice yields exactly one entry, and two different files yield two — the registry is bounded by in-flight concurrency, not by lifetime invocation count"
    requirement: REL-07
    verification:
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/backends/cli/CliBackendTempFileTest.kt#registeringTheSameFileTwiceStillYieldsOneEntry"
        status: pass
    human_judgment: false
  - id: D2
    description: "Deregistering a registered file returns the registry to empty, and deregistering a file that was never registered is a no-op rather than an error"
    requirement: REL-07
    verification:
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/backends/cli/CliBackendTempFileTest.kt#deregisteringReturnsToEmptyAndTolerantOfUnknownFiles"
        status: pass
    human_judgment: false
  - id: D3
    description: "The drain deletes a file still registered at shutdown and empties the registry — the clean-Burp-quit-mid-call case the net uniquely covers"
    requirement: REL-07
    verification:
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/backends/cli/CliBackendTempFileTest.kt#drainDeletesAStillRegisteredFileAndEmptiesTheRegistry"
        status: pass
    human_judgment: false
  - id: D4
    description: "The drain is idempotent with the finally block — a file the finally already deleted does not throw and still leaves the registry empty (A-EDGE-4)"
    requirement: REL-07
    verification:
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/backends/cli/CliBackendTempFileTest.kt#drainIsIdempotentWhenTheFinallyPathAlreadyDeletedTheFile"
        status: pass
    human_judgment: false
  - id: D5
    description: "Draining an empty registry is a no-op — the path every Burp session that never used a CLI backend takes on unload (A-EDGE-4, empty boundary)"
    requirement: REL-07
    verification:
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/backends/cli/CliBackendTempFileTest.kt#drainOnAnEmptyRegistryIsANoOp"
        status: pass
    human_judgment: false
  - id: D6
    description: "Ten complete CLI calls leave the registry empty — the direct SC5 assertion, where the removed mechanism would have retained ten permanent path strings"
    requirement: REL-07
    verification:
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/backends/cli/CliBackendTempFileTest.kt#tenCompleteCallsLeaveTheRegistryEmpty"
        status: pass
    human_judgment: false
  - id: D7
    description: "The exit hook is absent before the first register, armed by it, reused by the second, removed by shutdown(), and shutdown() is idempotent (D-03)"
    requirement: REL-07
    verification:
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/backends/cli/CliBackendTempFileTest.kt#theShutdownHookIsRegisteredLazilyOnTheFirstTempFile"
        status: pass
    human_judgment: false
  - id: D8
    description: "REL-07-F — CliBackend.kt, comment-stripped, contains zero per-invocation JVM exit registrations, exactly 2 register sites and exactly 3 deregister sites"
    requirement: REL-07
    verification:
      - kind: structural
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/backends/cli/CliBackendTempFileTest.kt#theCliBackendTempFilesCannotRegressToPerInvocationJvmRegistration"
        status: pass
    human_judgment: false
  - id: D9
    description: "App.kt, comment-stripped, has exactly one \"CLI temp files\" step calling into the registry, ordered after \"Backend registry\" and before \"Worker pool\" (D-03)"
    requirement: REL-07
    verification:
      - kind: structural
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/backends/cli/CliBackendTempFileTest.kt#appShutdownDrainsTheRegistryAfterTheBackendRegistryAndBeforeTheWorkerPool"
        status: pass
    human_judgment: false
  - id: D10
    description: "The prompt temp file keeps its owner-read/owner-write POSIX permissions and its Windows fallback across the rewrite (ASVS V12, T-24-06)"
    requirement: REL-07
    verification:
      - kind: structural
        ref: "grep -c 'setPosixFilePermissions' and 'OWNER_READ' on CliBackend.kt both return 1; the block is untouched in `git diff`"
        status: pass
    human_judgment: false

# Metrics
duration: 33min
completed: 2026-08-21
status: complete
---

# Phase 24 Plan 04: Bounded CLI Temp-File Registry Summary

**`CliBackend`'s per-invocation JVM exit-time deletion registration is gone, replaced by one lazily-armed exit hook over a live-file registry that returns to empty after every call, drained and unregistered on extension unload — and SC5, which had zero real coverage, now has nine behavioural and two structural assertions.**

## Performance

- **Duration:** 33 min
- **Started:** 2026-08-21T15:59:00Z
- **Completed:** 2026-08-21T16:32:15Z
- **Tasks:** 3
- **Files modified:** 4 (1 created, 3 modified)

## Accomplishments

**The leak D-01 targets is closed.** Every CLI invocation used to hand one absolute path to the JDK's
exit-time deletion facility, whose static set has no removal API. In a long Burp session that set grew
with lifetime invocation count and nothing could shrink it. Both call sites now register with
`CliTempFileRegistry` instead, whose entries are cleared when the call ends.

**The one case a hook genuinely covers is preserved.** `CliBackend`'s `finally` block stays the primary
cleanup path on both the normal and the exception path, unchanged in structure. The registry is a
safety net for exactly one scenario — a clean Burp quit while a CLI call is in flight — and its KDoc
says so rather than implying broader coverage.

**Unload is covered too, and unload is not the same lifecycle as JVM exit.** The exit hook does not
fire when the extension is unloaded while Burp keeps running, so `App.shutdown()` gained one step that
both unregisters the hook and drains the registry. That removal is what stops a reload from
accumulating hooks: unlike the JDK facility being removed, which retained plain strings, the hook this
extension arms is an extension-classloader object and would pin a dead classloader on every reload.

**SC5's zero-coverage state is over, and the vacuity was measured rather than assumed.** The two
`…DeleteOnExitIsRegistered` tests were deleted, not inverted. Before deleting them the fail-open
mechanism was reproduced directly on the project's JDK 21 (see Red-Before-Green Record): the reflection
into `java.io.DeleteOnExitHook` raises `InaccessibleObjectException`, a `RuntimeException`, which the
helper's own `catch (_: Exception) { true }` converted into a pass. Those tests were incapable of
failing under any implementation.

**A leak the plan did not anticipate was found and closed.** See Deviations — the prompt write-failure
branch needed a third deregistration.

## Task Commits

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 (RED) | Rewrite the suite against the registry seam | `519e3f2` | `CliBackendTempFileTest.kt` |
| 1 (GREEN) | `CliTempFileRegistry` — the D-02 object with the D-04 residual in its KDoc | `cb66ab5` | `CliTempFileRegistry.kt` |
| 2 | Wire `CliBackend` onto the registry, preserve the primary cleanup path | `ba3d165` | `CliBackend.kt` |
| 3 | Drain and unregister on unload; pin both halves structurally | `6c3e116` | `App.kt`, `CliBackendTempFileTest.kt` |

## Files Created/Modified

**Created**
- `src/main/kotlin/com/six2dez/burp/aiagent/backends/cli/CliTempFileRegistry.kt` (149 lines) — `internal object`; `ConcurrentHashMap.newKeySet()` of absolute paths, one nullable hook `Thread` behind a monitor, `register` / `deregister` / `drain` / `shutdown`, plus `sizeForTests` / `isHookRegisteredForTests` / `resetForTests`.

**Modified**
- `src/main/kotlin/com/six2dez/burp/aiagent/backends/cli/CliBackend.kt` — 2 registrations, 3 deregistrations, both stale `// REL-02:` comments removed. The owner-only POSIX permission block and every `catch` arm and `// INTENTIONAL:` comment in the `finally` block are byte-identical.
- `src/main/kotlin/com/six2dez/burp/aiagent/App.kt` — one import, one commented step. Pure addition: `git diff -U0` shows no reordering or removal of any existing step.
- `src/test/kotlin/com/six2dez/burp/aiagent/backends/cli/CliBackendTempFileTest.kt` — 4 tests to 11; reflection helper and both vacuous tests deleted; class KDoc rewritten; suite-naming constraint recorded.

## Decisions Made

### The registry gained a seventh member the plan did not declare

`<artifacts_this_phase_produces>` lists six members. D-03's two claims — that the hook is armed lazily
and that unload removes it — are unobservable through the other six: `sizeForTests()` reports the file
set, not the hook. The only alternatives were reflecting into `java.base` (ruled out by
24-VALIDATION.md as fail-open, and the very technique this plan deletes) or leaving D-03's central
claim unasserted. `isHookRegisteredForTests(): Boolean` is the honest seam and it is what makes
`theShutdownHookIsRegisteredLazilyOnTheFirstTempFile` a real test rather than a comment.

### `resetForTests()` does not unregister an armed hook, and that is stated at the seam

Per the plan it "clears the set and drops the hook reference without deleting anything". That leaves a
window: a suite that arms a hook and then calls only `resetForTests()` orphans it. Rather than quietly
widen the method, the KDoc names the constraint and the suite's `@AfterEach` calls `shutdown()`, which
is what actually keeps the test JVM clean.

### Delete first, deregister second — in all three pairings

The ordering is load-bearing. If a `delete()` throws, deregistering first would have removed the only
record that the file exists, so nothing would ever sweep it. Deleting first means a failed delete
leaves the entry behind for the drain, which is the direction that degrades safely.

## Red-Before-Green Record

**REL-07-E — red only by non-compilation. Stated at that strength, not stronger.** The seven
behavioural tests were written and run before `CliTempFileRegistry.kt` existed:

```
e: .../CliBackendTempFileTest.kt:67:9 Unresolved reference 'CliTempFileRegistry'.
   (12 further occurrences)
> Task :compileTestKotlin FAILED
```

Per `24-VALIDATION.md` §Red-Before-Green Gate this is the weak column: a suite that cannot compile has
not exercised a bypass. It is recorded as such and is **not** claimed as a gate.

**Baseline confirmation that the deleted tests were vacuous.** On the unmodified tree
`./gradlew test --tests '*CliBackendTempFileTest'` was green with `tests="4" failures="0"`. The
mechanism was then reproduced directly on the project's JDK 21 rather than inferred:

```
$ java Vac.java     # Class.forName("java.io.DeleteOnExitHook").getDeclaredField("files").setAccessible(true)
REFLECTION FAILED -> helper catch returns true: java.lang.reflect.InaccessibleObjectException
```

`InaccessibleObjectException` extends `RuntimeException`, so the helper's `catch (_: Exception)`
returned `true` on every run. Both tests passed unconditionally, before and after any change.

**REL-07-F and the D-03 ordering gate — genuinely red.** Both structural assertions were run against
the real pre-fix sources (`CliBackend.kt` restored to the pre-plan blob with
`git checkout e4914611 -- <path>`, `App.kt` not yet edited):

```
CliBackendTempFileTest > appShutdownDrainsTheRegistryAfterTheBackendRegistryAndBeforeTheWorkerPool() FAILED
CliBackendTempFileTest > theCliBackendTempFilesCannotRegressToPerInvocationJvmRegistration() FAILED

expected: <1> but was: <0>   # App.kt "CLI temp files" steps
expected: <0> but was: <2>   # CliBackend.kt per-invocation JVM registrations
```

After the fix, `tests="11" skipped="0" failures="0" errors="0"`.

## Cache-Staleness Record

```
# baseline
> Task :test UP-TO-DATE

# after appending one comment line to App.kt
> Task :testClasses UP-TO-DATE
> Task :test
```

`:testClasses` stayed `UP-TO-DATE` while `:test` re-executed — which is precisely the 22-09 defect
shape and precisely what `mainSourceTreeStructuralInputs` exists to defeat: the source text changed,
the bytecode did not, so without the whole-tree input declaration the structural gate would have been
served from cache in exactly the commit that breaks it. The probe comment was then reverted; the
resulting `App.kt` blob is byte-identical to the pre-probe one (`f9f570e` both times).

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 2 — Missing critical functionality] The prompt write-failure branch leaked a registry entry**

- **Found during:** Task 2, reviewing the diff before commit.
- **Issue:** `CliBackend`'s large-prompt branch registers `tFile` and then enters a `try` whose `catch`
  does `tFile.delete(); onComplete(e); return@submit`. That `return@submit` exits the executor lambda
  **before** the outer `try { … } finally { … }` is entered, so the plan's two finally-block
  deregistrations cannot run for it. Every failed prompt write would have left one permanent entry —
  reconstructing threat T-24-15 in the exact mechanism this plan removes it from, only on a rarer path.
  The other three `return@submit` sites (`:178`, `:259`, `:277`) are *inside* the outer `try`, so their
  `finally` still runs; this was the only uncovered one.
- **Fix:** added `CliTempFileRegistry.deregister(tFile)` immediately after the existing `tFile.delete()`
  in that branch, with a comment naming why it is the only place that can clear the entry.
- **Consequence for the plan's criteria:** `grep -c 'CliTempFileRegistry.deregister('` returns **3**,
  not the 2 the plan and the task-3 behavior block predicted. Following the standing instruction not to
  bend code to a wrong criterion, the structural gate asserts 3 and its ledger message explains all
  three sites, so dropping any one of them is a red build. Asserting 2 would have required deleting the
  fix.
- **Files modified:** `src/main/kotlin/com/six2dez/burp/aiagent/backends/cli/CliBackend.kt`
- **Commit:** `ba3d165`

**2. [Rule 3 — Blocking issue, self-inflicted] Over-broad revert of the cache-staleness probe**

- **Found during:** Task 3. `git checkout HEAD -- App.kt` was used to drop the probe comment, but HEAD
  was the task-2 commit, so it also discarded the still-uncommitted `"CLI temp files"` step and import.
- **Fix:** both edits reapplied and the diff verified identical to the pre-revert one (same blob
  `f9f570e`). No probe residue reached the commit.
- **Impact:** none on the delivered tree; recorded because the safer form is a probe edit made in a file
  the task is not currently editing.

### Instructed-command substitutions

**3. `git stash push` replaced with `git checkout <ref> -- <path>` (task 3 acceptance criterion).**
`refs/stash` lives in the parent `.git/` and is shared across every linked worktree, so a stash here
can pop a sibling worktree's WIP. The single-file checkout gives the same red-probe evidence with no
shared-ref hazard. This is the same substitution 24-03 made.

**4. `gradle/libs.versions.toml` does not exist in this repository.** The plan's prohibition list and
task-3 acceptance criterion both name it. This project declares dependencies directly in
`build.gradle.kts`; there is no version catalog (`ls gradle/` returns only `wrapper`). The invariant
the criterion *meant* — no dependency was added — is discharged by `git diff --stat build.gradle.kts`
being empty, which it is. 24-03 hit and recorded the same wrong reference; it is a plan-template error,
not a tree change.

### Deliberately not done

**5. `STATE.md`, `ROADMAP.md` and `REQUIREMENTS.md` were not touched.** The execution context names the
orchestrator as the single writer for the first two, and REL-07 stays unchecked because 24-05 still
owns the worker-pool half of it. `requirements-completed` is therefore empty in this summary's
frontmatter even though the plan's `requirements:` field lists REL-07.

## Issues Encountered

**The `grep -c 'removeShutdownHook'` criterion and a complete KDoc are in tension.** The acceptance
criterion pins that token to one line, meaning one removal *site*; a KDoc paragraph explaining why no
catch is hand-rolled around it naturally names the method and pushes the count to 2. Resolved by
phrasing the KDoc as "the JVM's hook-removal call", keeping both the explanation and the criterion. The
same tension does not arise for `addShutdownHook` because that paragraph never needed the literal.

**No detekt suppression turned out to be necessary.** The plan anticipated `TooGenericExceptionCaught`
on the new file. detekt's default `allowedExceptionNameRegex` accepts `_`, and the drain uses the
project-blessed `catch (_: Exception)` cleanup form, so `detekt` is green with no `@Suppress` and no
baseline entry. `git diff --stat detekt-baseline.xml` is empty (QUAL-07 respected).

## Threat Flags

**No new attack surface was introduced by this plan.** No network endpoint, no auth path, no new file
access pattern, no schema change, and no trust boundary was added or moved. The set of files written to
disk is unchanged — the same two temp files, at the same paths, with the same permissions. What changed
is only *which in-memory structure remembers them* and *when that memory is released*. Nothing gained a
public member: `CliTempFileRegistry` is `internal`, and `CliBackend`'s public surface is untouched (all
edits are inside one private connection class's method body). `App.kt` gained one step inside an
existing private shutdown sequence.

The plan's dispositioned threats are discharged as follows:

| Threat ID | Severity | Disposition | Status | Evidence |
|-----------|----------|-------------|--------|----------|
| T-24-06 | low | accept | **accepted, residual named** | The hard-kill residual is unchanged and unreachable by test by construction — on SIGKILL or power loss neither the `finally` nor any hook runs. It is named in `CliTempFileRegistry`'s `Window NOT closed:` paragraph, bounded at one temp file per in-flight call in the OS temp directory. The compensating control survived intact: `grep -c 'setPosixFilePermissions'` and `grep -c 'OWNER_READ'` on `CliBackend.kt` both return 1, and the block including its `catch (_: UnsupportedOperationException)` Windows fallback is byte-identical in `git diff`. |
| T-24-07 | medium | mitigate | **discharged** | The hook is armed at most once, lazily, behind `synchronized(hookLock)`, and `shutdown()` unregisters it and nulls the reference. Asserted behaviourally by `theShutdownHookIsRegisteredLazilyOnTheFirstTempFile` (absent before first register, present after, same hook after a second register, absent after `shutdown()`, still absent after a second `shutdown()`) and pinned structurally by the `App.kt` gate, whose failure message names this threat. |
| T-24-15 | medium | mitigate | **discharged, residual named** | Zero per-invocation JVM exit registrations remain in `CliBackend.kt` (structural gate, red pre-fix at 2). The registry returns to empty after ten simulated complete calls, and re-registering the same path cannot inflate it. Residual, stated in the KDoc: entries the JDK facility already accumulated inside a Burp process running right now clear only on Burp restart — SC5 is about future invocations, so inspecting a long-running session is not a way to verify this fix. |
| T-24-16 | low | mitigate | **discharged** | The new step uses the existing `safeShutdownStep` wrapper — no hand-rolled try/catch — so a throw from the drain cannot skip `"Worker pool"`, `"Alerting client"`, `"Redaction mappings"` or any later step, and the `IllegalStateException` the JVM raises when the VM is already shutting down is already caught there. `git diff -U0 App.kt` shows one added step and nothing else. |
| T-24-SC | high | mitigate | **discharged** | No package-manager install ran and no dependency was added. `git diff --stat build.gradle.kts detekt-baseline.xml` is empty; the plan touched three Kotlin sources and one Kotlin test. (`gradle/libs.versions.toml` does not exist in this repo — see deviation 4.) |

No `threat_flag:` entries. Nothing in this plan warrants a new register row.

## Verification Record

| Check | Result |
|-------|--------|
| `./gradlew test --tests '*CliBackendTempFileTest'` | PASS — `tests="11" skipped="0" failures="0" errors="0"` (9 after task 1, as the plan predicted) |
| `./gradlew test detekt ktlintCheck shadowJar` | PASS — BUILD SUCCESSFUL; 120 result files, none with a non-zero `failures` or `errors` |
| `./gradlew test -PexcludeHeavyTests=true` | PASS — BUILD SUCCESSFUL, and `CliBackendTempFileTest` is present in the report with 11 tests; the suite name matches no excluded suffix |
| `git diff --stat detekt-baseline.xml build.gradle.kts` | PASS — empty (QUAL-07) |
| `grep -c 'CliTempFileRegistry.register(' CliBackend.kt` | PASS — 2 |
| `grep -c 'CliTempFileRegistry.deregister(' CliBackend.kt` | DEVIATION — 3, one more than the plan predicted, deliberately; see deviation 1 |
| `grep -c 'deleteOnExit' / 'crash-safety' CliBackend.kt` | PASS — 0 / 0 |
| `grep -c 'setPosixFilePermissions' / 'OWNER_READ' CliBackend.kt` | PASS — 1 / 1 (ASVS V12 control intact) |
| `grep -c 'Window NOT closed' / 'Visibility:' CliTempFileRegistry.kt` | PASS — 1 / 1 |
| `grep -c 'addShutdownHook' / 'removeShutdownHook' CliTempFileRegistry.kt` | PASS — 1 / 1 |
| `grep -c 'INTENTIONAL:' CliTempFileRegistry.kt` | PASS — 1 |
| `grep -c 'DeleteOnExitHook\|java.lang.reflect' CliBackendTempFileTest.kt` | PASS — 0 |
| `grep -c 'tempFilesMatching(' CliBackendTempFileTest.kt` | PASS — 5 (both controls survive) |
| `grep -c 'ProcessBuilder\|Runtime.getRuntime().exec' CliBackendTempFileTest.kt` | PASS — 0 (pure JVM, no subprocess) |
| `grep -c 'CLI temp files' / 'CliTempFileRegistry.shutdown()' App.kt` | PASS — 1 / 1 |
| Step order in `App.kt` | PASS — `Backend registry` (:235) then `CLI temp files` (:241) then `Worker pool` (:243) |
| Manual verifications | None required — the plan declares no manual-only checks, and the one accepted gap (T-24-06) is unreachable by construction |

## Next Phase Readiness

24-05 (wave 4) owns the worker-pool half of REL-07 and will edit `App.kt`'s `"Worker pool"` step. Two
things to hand over:

- **`CliBackendTempFileTest` now asserts the `App.shutdown()` step ORDER.** Reordering, renaming or
  removing `"Backend registry"` or `"Worker pool"` turns this suite red with a message explaining why.
  That is intentional, but 24-05 should expect it rather than be surprised by it.
- **`REL-07` stays unchecked in `REQUIREMENTS.md`.** SC5 is closed; SC6 is not. 24-05 is the plan that
  gets to check the box.

Nothing in this plan blocks anything else. The registry is self-contained, `internal`, and has one
production caller plus one shutdown caller.

## Known Stubs

None. No hardcoded empty value, placeholder string, `TODO`, `FIXME`, or unwired component was
introduced. Every path in `CliTempFileRegistry` has a behavioural assertion, and no test was skipped or
left unrun.

## Self-Check: PASSED

- `src/main/kotlin/com/six2dez/burp/aiagent/backends/cli/CliTempFileRegistry.kt` — FOUND
- `src/test/kotlin/com/six2dez/burp/aiagent/backends/cli/CliBackendTempFileTest.kt` — FOUND
- `src/main/kotlin/com/six2dez/burp/aiagent/backends/cli/CliBackend.kt` — FOUND
- `src/main/kotlin/com/six2dez/burp/aiagent/App.kt` — FOUND
- Commit `519e3f2` — FOUND
- Commit `cb66ab5` — FOUND
- Commit `ba3d165` — FOUND
- Commit `6c3e116` — FOUND
