---
phase: 24-scheduler-process-robustness
reviewed: 2026-08-21T15:31:49Z
depth: standard
files_reviewed: 18
files_reviewed_list:
  - src/main/kotlin/com/six2dez/burp/aiagent/util/GuardedScheduling.kt
  - src/main/kotlin/com/six2dez/burp/aiagent/backends/cli/CliOutputBuffer.kt
  - src/main/kotlin/com/six2dez/burp/aiagent/backends/cli/CliTempFileRegistry.kt
  - src/main/kotlin/com/six2dez/burp/aiagent/backends/cli/CliBackend.kt
  - src/main/kotlin/com/six2dez/burp/aiagent/scanner/ActiveAiScanner.kt
  - src/main/kotlin/com/six2dez/burp/aiagent/supervisor/AgentSupervisor.kt
  - src/main/kotlin/com/six2dez/burp/aiagent/App.kt
  - src/main/kotlin/com/six2dez/burp/aiagent/mcp/tools/ScannerTaskRegistry.kt
  - src/main/kotlin/com/six2dez/burp/aiagent/mcp/tools/CollaboratorRegistry.kt
  - src/main/kotlin/com/six2dez/burp/aiagent/config/Defaults.kt
  - build.gradle.kts
  - src/test/kotlin/com/six2dez/burp/aiagent/util/GuardedSchedulingTest.kt
  - src/test/kotlin/com/six2dez/burp/aiagent/util/SchedulerGuardCoverageTest.kt
  - src/test/kotlin/com/six2dez/burp/aiagent/scanner/ActiveScannerFailureIsolationTest.kt
  - src/test/kotlin/com/six2dez/burp/aiagent/scanner/ScanRequestExecutorTest.kt
  - src/test/kotlin/com/six2dez/burp/aiagent/backends/cli/CliOutputBufferTest.kt
  - src/test/kotlin/com/six2dez/burp/aiagent/backends/cli/CliBackendTempFileTest.kt
  - src/test/kotlin/com/six2dez/burp/aiagent/WorkerPoolExecutorTest.kt
findings:
  critical: 2
  warning: 10
  info: 9
  total: 21
status: issues_found
critical_resolved:
  - id: CR-01
    commit: 2446da1
  - id: CR-02
    commit: 19691c7
---

# Phase 24: Code Review Report

**Reviewed:** 2026-08-21T15:31:49Z
**Depth:** standard (per-file, concurrency/lifecycle weighted)
**Files Reviewed:** 18 (11 production, 7 test)
**Status:** issues_found

## Summary

The phase's mechanisms are sound in shape: `CliOutputBuffer`'s monitor is correctly scoped
(every mutation and every read takes the same lock, no check-then-act outside it), the surrogate-pair
cut is genuinely handled, the two executor shapes are deliberately different and the asymmetry is
justified in code, and the `SynchronousQueue` + `AbortPolicy` + `core=0` combination on
`requestExecutor` is a legitimate construction (no JDK `corePoolSize=0` starvation trap, because a
`SynchronousQueue.offer` always fails when no worker is idle).

Two things I verified specifically because they were the highest-risk claims:

- **`snapshot()` is byte-identical on the non-truncated path.** Confirmed by reading, and confirmed
  that the append semantics did not shift: the previous `StringBuilder.appendLine(line)` compiled to
  `append(line); append('\n')` — I disassembled `kotlin/text/StringsKt__StringBuilderJVMKt.class`
  from the project's `kotlin-stdlib-2.0.21` and the constant is `bipush 10`, not
  `System.lineSeparator()`. So `line + "\n"` in `CliOutputBuffer.appendLine` is a faithful
  replacement on Windows too.
- **Sibling early-exit paths in the CLI executor lambda.** The `return@submit` at
  `CliBackend.kt:178` and `:259` and `:277` are all inside the outer `try` (opened at `:174`), so the
  `finally` covers them. But the *exceptional* exits between the two `register(...)` calls and that
  `try` are **not** covered — see WR-01.

The two Critical findings are both regressions of a guarantee this phase claims to strengthen:
`runGuarded` does not actually satisfy its own "never rethrows" contract (CR-01), and the temp-file
safety net now abandons a file that `File.delete()` *failed* to delete, which the removed
`deleteOnExit()` mechanism would have retried at JVM exit (CR-02).

**Claims in the SUMMARYs that the code does not support** are called out inline at WR-01 (24-04
SUMMARY line 54: "`deregister` beside every delete INCLUDING on branches that return before the
finally block") and CR-02 (24-04 SUMMARY §"Delete first, deregister second", whose stated rationale
is built on the wrong `File.delete()` failure mode).

I did **not** re-run the gate; findings below are all derived from reading the tree.

---

## Critical Issues

### CR-01: `runGuarded` rethrows when the log sink throws — the "never rethrows" contract is false

**Status: RESOLVED** in `2446da1` — the reporting call now sits in its own `catch (_: Throwable)`
inside the guard's catch, so a `logError` that throws is absorbed instead of escaping to the JDK
scheduler; `GuardedSchedulingTest` gains the unit and the end-to-end assertions that were previously
only prose, both red against the unfixed guard by real assertion. The `InterruptedException` re-assert
the fix snippet also showed is **not** included — that is WR-05 and remains open.

**File:** `src/main/kotlin/com/six2dez/burp/aiagent/util/GuardedScheduling.kt:67-71`
(contract stated at `:47-48`; caller at `src/main/kotlin/com/six2dez/burp/aiagent/scanner/ActiveAiScanner.kt:386`)

**Issue:** The guard is

```kotlin
try {
    body()
} catch (e: Throwable) {
    logError("[$component] $task failed: ${e.message}")   // <- not itself guarded
}
```

If `logError` throws, the throwable escapes `runGuarded`, reaches the JDK's
`ScheduledThreadPoolExecutor`, and the recurring schedule is cancelled for the rest of the Burp
session with nothing in any log — **exactly the REL-06 defect the file exists to close**, now
concentrated in a single chokepoint that all three migrated call sites depend on.

The KDoc at `:47-48` states the opposite as an absolute: *"This function never rethrows. That is the
entire contract."* That statement is falsifiable by reading six lines.

The exposure is not symmetric across call sites. `ScannerTaskRegistry.kt:97-103` and
`CollaboratorRegistry.kt:97-103` pass `::log`, which wraps the callback in its own `try/catch`, so
they are accidentally safe. `ActiveAiScanner.kt:386` passes a raw
`{ api.logging().logToError(it) }` — a torn-down or mid-teardown `MontoyaApi` is precisely the
condition under which a scheduler tick is also most likely to throw, so the two failures correlate.

`GuardedSchedulingTest.kt:180-184` explicitly names this property in prose ("a guard that throws
inside its own catch re-opens the defect it exists to close") but only tests the null-message case;
no test injects a throwing `logError`, so the gap is invisible to the suite.

**Fix:**

```kotlin
@Suppress("TooGenericExceptionCaught")
internal fun runGuarded(
    component: String,
    task: String,
    logError: (String) -> Unit,
    body: () -> Unit,
) {
    try {
        body()
    } catch (e: Throwable) {
        if (e is InterruptedException) Thread.currentThread().interrupt()   // see WR-05
        try {
            logError("[$component] $task failed: ${e.message}")
        } catch (_: Throwable) {
            // INTENTIONAL: the guard's own reporting must never re-cancel the schedule it protects.
        }
    }
}
```

Add a `GuardedSchedulingTest` case whose `logError` throws and whose later ticks still fire — that is
the assertion that makes this red before the fix.

---

### CR-02: A temp file whose `delete()` *returns false* is deregistered anyway — the safety net is lost, and this is a regression against `deleteOnExit()`

**Status: RESOLVED** in `19691c7` — all three sites now go through a new
`CliTempFileRegistry.deleteAndDeregister(file)`, which drops the entry only when `delete()` returned
`true` or the file is already gone, so a failed delete leaves the entry for the drain and the exit
hook to retry. Centralising it in the registry (rather than inlining the branch three times) is what
makes the behaviour assertable in pure JVM without reflecting into `java.base` — D-02's own rationale
for the object existing. The structural gate now forbids a bare `CliTempFileRegistry.deregister(` in
`CliBackend.kt` outright instead of counting occurrences.

**File:** `src/main/kotlin/com/six2dez/burp/aiagent/backends/cli/CliBackend.kt:302-315`
(and the same shape at `:157-161`)

**Issue:**

```kotlin
try {
    // Delete first, deregister second: if the delete throws, the entry survives and the
    // registry's drain sweeps the file later. The reverse order would lose it entirely.
    promptFile?.delete()
    promptFile?.let { CliTempFileRegistry.deregister(it) }
} catch (_: Exception) { … }
```

The stated rationale is built on the wrong failure mode. `java.io.File.delete()` does **not** throw
on failure — it *returns `false`*. It throws only `SecurityException`, which a Burp extension will
essentially never see. So the ordering protects against the case that cannot happen and does nothing
about the case that does: when `delete()` returns `false`, the entry is removed from the registry
anyway and the file is left on disk with no remaining owner.

This is reachable and platform-specific: on Windows, `File.delete()` returns `false` while another
process still holds the file open. At `:305` the code has just called `process.destroyForcibly()`
(`:298`); the OS handle held by the CLI child on `promptFile` (or on the codex `outputFile`, which
that process writes) is not guaranteed to be released by the time `delete()` runs. `.planning/WINDOWS.md`
is a tracked ledger in this repo, so Windows is a supported target, not a hypothetical.

It is also a **regression against the mechanism this phase removed**. `File.deleteOnExit()` recorded
the path in the JDK's static set and re-attempted deletion at JVM exit *regardless* of whether the
`finally` block's `delete()` had succeeded. The new registry drops the path on an unconditional
`deregister`, so the JVM-exit sweep no longer covers a failed delete. The net that D-01 promised to
preserve ("the same crash-time coverage") is strictly weaker for this case.

Consequence: a `burp_uv_prompt_*.txt` containing the full prompt — which on the Windows path also
never received owner-only permissions, because `setPosixFilePermissions` raises
`UnsupportedOperationException` and is swallowed at `:151-153` — can persist indefinitely in the
shared OS temp directory. For a product whose stated non-negotiable is privacy, that is a data-at-rest
exposure, not just an untidy file.

**Fix:** deregister only when the delete actually succeeded, and let the drain retry otherwise.

```kotlin
try {
    promptFile?.let { file ->
        if (file.delete() || !file.exists()) CliTempFileRegistry.deregister(file)
    }
} catch (_: Exception) {
    // INTENTIONAL: finally block cleanup; file deletion must not prevent process cleanup
}
try {
    outputFile?.let { file ->
        if (file.delete() || !file.exists()) CliTempFileRegistry.deregister(file)
    }
} catch (_: Exception) {
    // INTENTIONAL: finally block cleanup; file deletion must not prevent process cleanup
}
```

Apply the same shape at `:157-161`. Note that `CliBackendTempFileTest`'s structural gate asserts
`CliTempFileRegistry.deregister(` appears on exactly 3 code lines — the fix above keeps that count at
3, so the gate does not need re-aiming. Add a behavioural case where the registered file cannot be
deleted (e.g. a registered path pointing at a non-empty directory, whose `delete()` returns `false`)
and assert the entry *survives*.

---

## Warnings

### WR-01: `register(...)` sits outside the guarded region — an exception between registration and the outer `try` leaks the entry, the file, and the caller's completion callback

**File:** `src/main/kotlin/com/six2dez/burp/aiagent/backends/cli/CliBackend.kt:117-173`

**Issue:** The codex `outputFile` is registered at `:124` and the large-prompt `tFile` at `:139`, but
the `try` whose `finally` performs deregistration does not open until `:174`. Everything at
`:129-173` — `cliSessionIdRef.get()`, the string concatenation, `File.createTempFile` at `:136`, and
`buildCommand(promptToSend, outputFile)` at `:172` — runs unprotected. A throw anywhere in that band
means:

1. the registry keeps the entry for the life of the JVM (the exact unbounded-growth shape D-01 exists
   to remove — the drain will eventually clear it at unload, but the "returns to empty" property the
   KDoc claims at `CliTempFileRegistry.kt:24-28` no longer holds for the session);
2. the temp file is never deleted; and
3. **`onComplete` is never called**, because the exception escapes the `executor.submit { … }`
   `Runnable` into a `Future` nobody inspects — the chat/scan caller waits forever with no error.

Point 3 predates this phase (`createTempFile` at `:136` was already outside any try), but points 1
and 2 are new, and this is structurally the same defect as the `return@submit`-before-the-try path
that plan 24-04 found and fixed. 24-04-SUMMARY line 54 asserts "`deregister` beside every delete
INCLUDING on branches that return before the finally block"; that claim covers the *normal-return*
branch only and is not true of the exceptional exits above.

**Fix:** move the registrations inside a `try` that spans them, or — simpler and with a smaller blast
radius — wrap the whole lambda body in a `try { … } catch (t: Throwable) { onComplete(t) }` so the
existing `finally` shape is reachable from every path and no caller can be stranded.

---

### WR-02: The truncation marker is spliced into the model's answer on the success path, and `CliOutputBuffer.truncated` is never read in production

**File:** `src/main/kotlin/com/six2dez/burp/aiagent/backends/cli/CliBackend.kt:280`;
`src/main/kotlin/com/six2dez/burp/aiagent/backends/cli/CliOutputBuffer.kt:81-94`

**Issue:** `snapshot()` appends `Defaults.CLI_OUTPUT_TRUNCATION_MARKER` whenever the cap was hit, and
`:280` feeds that value straight into `stripAnsiCodes(...)` → `readGeminiOutput` / `readClaudeOutput`
/ `readCopilotOutput` / `readOpenCodeOutput` → `onChunk(finalMessage)`. On a >256 KiB CLI response
the user receives a truncated answer with an English sentence welded onto the end and **no error**.
When `jsonMode` is set (scanner payload generation), the appended prose guarantees a parse failure
that surfaces as a generic malformed-response error rather than "the CLI produced more output than we
capture".

The class provides exactly the signal needed to handle this properly —
`CliOutputBuffer.truncated` — and I verified by grep that **no production code reads it**; the only
readers are `CliOutputBufferTest`. Same for `CliOutputBuffer.length`. A public API added and consumed
only by its own tests is dead code by the project's own §Code Quality bar.

**Fix:** consult the flag at the success path and fail loudly, per `CONVENTIONS.md` §Error Handling:

```kotlin
if (rawOutput.truncated) {
    api.logging().logToError(
        "[CliBackend] $backendId output exceeded ${Defaults.MAX_CLI_OUTPUT_CHARS} chars; response truncated",
    )
}
val stdoutText = stripAnsiCodes(rawOutput.snapshot())
```

and either suppress the marker on the success path or convert the condition into an
`onComplete(IllegalStateException(...))` when `jsonMode` is true.

---

### WR-03: `CliTempFileRegistry.shutdown()` is not atomic with `register()` — a racing registration can arm a hook that is never removed

**File:** `src/main/kotlin/com/six2dez/burp/aiagent/backends/cli/CliTempFileRegistry.kt:111-122`,
`:141-148`, `:88-99`

**Issue:** Three distinct non-atomic windows:

1. `shutdown()` reads-and-nulls `hook` under `hookLock` (`:112-117`), then releases the lock and calls
   `Runtime.removeShutdownHook(armed)` at `:119`. A concurrent `register()` between those two points
   sees `hook == null` and arms a **new** hook at `:145`. `shutdown()` then removes the old one and
   returns; the new one is never removed. That re-opens T-24-07 (an extension-classloader `Thread`
   pinned in the JVM's hook table across reloads) — the single defect this object's design goes to the
   most trouble to close. It is a narrow race, but "extension unload while a CLI call is starting" is
   an ordinary user action (clicking Unload with a chat request in flight).
2. `drain()` at `:89-90` does `liveFiles.toList()` then `liveFiles.clear()`. A `register()` landing
   between those two statements has its entry silently discarded without the file being deleted.
   `ConcurrentHashMap.KeySetView.clear()` is not atomic with the snapshot.
3. After `shutdown()`, `hook` is `null`, so any later `register()` re-arms. If the VM is already in
   shutdown, `Runtime.addShutdownHook` throws `IllegalStateException` — which propagates out of
   `register()`, out of the CLI `submit` lambda, and strands `onComplete` (same mechanism as WR-01).

**Fix:** take `hookLock` for the whole of `shutdown()` (including the `removeShutdownHook` call) and
for the whole of `register()`'s arm step, and add a terminal `shuttingDown` flag so a post-shutdown
`register()` is a no-op rather than a re-arm:

```kotlin
@Volatile private var shuttingDown = false

fun register(file: File) {
    synchronized(hookLock) {
        if (shuttingDown) return
        liveFiles.add(file.absolutePath)
        if (hook == null) {
            val t = Thread({ drain() }, "burp-ai-agent-cli-temp-sweep")
            runCatching { Runtime.getRuntime().addShutdownHook(t) }.onSuccess { hook = t }
        }
    }
}

fun shutdown() {
    synchronized(hookLock) {
        shuttingDown = true
        hook?.let { runCatching { Runtime.getRuntime().removeShutdownHook(it) } }
        hook = null
    }
    drain()
}
```

For (2), replace the snapshot-then-clear with a drain loop that removes each entry as it is taken
(`while (true) { val p = liveFiles.firstOrNull() ?: break; liveFiles.remove(p); … }`), or accept and
document the window explicitly.

---

### WR-04: The two MCP registry cleaners are never shut down — every extension reload leaks a scheduled thread pinning a dead classloader and a dead `MontoyaApi`

**File:** `src/main/kotlin/com/six2dez/burp/aiagent/mcp/tools/ScannerTaskRegistry.kt:21-24, 26-37`;
`src/main/kotlin/com/six2dez/burp/aiagent/mcp/tools/CollaboratorRegistry.kt:20-23, 25-36`

**Issue:** Both `object`s create a `newSingleThreadScheduledExecutor` in a field initializer and
schedule a 5-minute recurring cleaner in `init`. I grepped every reference to both objects across
`src/main/kotlin`: the only lifecycle calls are `configureTtlMinutes`, `setLogger`, `put`, `get`,
`remove` and `clear`. **Neither `cleaner` is ever shut down**, and `setLogger` (installed at
`McpSupervisor.kt:96-97` with a lambda capturing `api`) is never cleared.

Consequence on extension reload: the old `McpScannerTaskRegistryCleaner` /
`McpCollaboratorRegistryCleaner` threads keep ticking forever, each holding a `Runnable` that closes
over the object, which closes over a `loggerRef` lambda capturing a torn-down `MontoyaApi` and the
old extension classloader. That is the *identical* defect class as T-24-07, in two files this phase
edited — and the phase explicitly went to the trouble of adding `removeShutdownHook` + a drain for
the CLI hook while leaving these open.

It also contradicts `App.kt:275-297`'s own discipline, which unwires `BackendDiagnostics.retry`,
`Redaction.truncationLogger` and `AuditLogger.registerGlobalEmitter(null)` for exactly this reason.

**Fix:** give each registry a `shutdown()` that stops its cleaner and clears the logger, and call it
from `App.shutdown()`:

```kotlin
fun shutdown() {
    cleaner.shutdownNow()
    loggerRef.set({})
    clear()
}
```

then in `App.shutdown()`, alongside the existing steps:

```kotlin
safeShutdownStep("MCP registries") {
    ScannerTaskRegistry.shutdown()
    CollaboratorRegistry.shutdown()
}
```

Note the Phase 23 T-23-06-07 constraint ("do not widen who can reach these registries"): adding a
`shutdown()` called only from `App.shutdown()` does not widen the MCP-tool-facing surface, but the
new member should be documented as extension-lifecycle-only.

---

### WR-05: `runGuarded` swallows `InterruptedException` without restoring the interrupt flag

**File:** `src/main/kotlin/com/six2dez/burp/aiagent/util/GuardedScheduling.kt:69-71`

**Issue:** `catch (e: Throwable)` absorbs `InterruptedException` too. The JVM clears the thread's
interrupt status when it throws `InterruptedException` from a blocking call, so swallowing it without
`Thread.currentThread().interrupt()` destroys the cancellation signal. Every other interrupt handler
in the touched code does restore it — `CliBackend.kt:253-256`, `:264-267`, `:328-331`,
`ActiveAiScanner.kt:415-417`, `App.kt:337-339` — so this is an inconsistency with the codebase's own
established idiom, in the one helper that now sits under three schedulers.

Practical exposure today is low (`processQueue` and both `cleanupExpired` bodies do not block), but it
becomes real the moment any guarded body acquires a lock or waits, and the helper is explicitly
designed to be the landing site for future schedules.

**Fix:** see the CR-01 snippet — re-assert the interrupt before logging.

---

### WR-06: Two retained tests in `CliBackendTempFileTest` are vacuous by their own admission, and are flaky by construction

**File:** `src/test/kotlin/com/six2dez/burp/aiagent/backends/cli/CliBackendTempFileTest.kt:91-136`
(suite KDoc admission at `:37-41`)

**Issue:** `uvPromptTempFileIsCleanedUpAfterFailure` and `codexOutputTempFileIsCleanedUpAfterFailure`
create their own temp file, throw their own exception, and delete the file in their own `finally`.
They import nothing from `CliBackend` and drive no production code. The suite KDoc says so outright:
*"They are NOT coverage of `CliBackend`'s own `finally` path — nothing here drives that code."* They
cannot fail under any implementation of the code under review — which is the exact property that got
the two `…DeleteOnExitIsRegistered` tests deleted in this same phase. Keeping two vacuous tests while
deleting two others for being vacuous is inconsistent, and it inflates the apparent coverage of SC5.

Worse, they are not merely useless: `tempFilesMatching(prefix)` (`:48-54`) enumerates the **shared OS
temp directory** and diffs the path set before/after. Any concurrently running Burp instance with this
extension loaded — or a second Gradle test JVM — that creates or deletes a `burp_uv_prompt_*` or
`burp-ai-agent-codex*` file inside the test window turns `assertEquals(before, after)` red. That is a
cross-process wall-clock race in a suite whose own validation contract rules out timing-dependent
assertions.

**Fix:** delete both tests. The registry seam tests (`:145-318`) are the real SC5 coverage. If a
control over the `finally` shape is genuinely wanted, assert it structurally on `CliBackend.kt` (the
suite already reads that file at `:335`) rather than by racing the OS temp directory.

---

### WR-07: `WorkerPoolExecutorTest` asserts a condition that the preceding `await()` already guarantees

**File:** `src/test/kotlin/com/six2dez/burp/aiagent/WorkerPoolExecutorTest.kt:166-175`

**Issue:**

```kotlin
release.countDown()
queuedTaskRan.await()                    // returns ONLY when count == 0
assertEquals(0L, queuedTaskRan.count, "…the queued task must actually run…")
```

`CountDownLatch.await()` with no timeout returns only when the count has reached zero. The assertion
on the next line therefore cannot fail; the actual detector is the enclosing
`assertTimeoutPreemptively(15s)`, which reports a hang as a timeout rather than as the specific,
well-argued failure the message describes. An assertion that cannot fail is exactly the defect class
this phase deleted two tests for.

**Fix:** use the timed overload and assert on its result, so the failure message is the diagnostic:

```kotlin
release.countDown()
assertTrue(
    queuedTaskRan.await(QUEUE_DRAIN_TIMEOUT_SECONDS, TimeUnit.SECONDS),
    "REL-07 / SC6 boundary: the queued task must actually run once a worker frees up. …",
)
```

---

### WR-08: `SchedulerGuardCoverageTest`'s "really guarded" check accepts a `try` with no `catch`, and matches allowlist entries by file name rather than path

**File:** `src/test/kotlin/com/six2dez/burp/aiagent/util/SchedulerGuardCoverageTest.kt:68-86`, `:43`

**Issue:** Two holes in the structural half of the "two places at once" guarantee:

1. `:76-83` asserts only that some line within the next 4 comment-stripped lines contains `try {`. It
   does not check that a `catch` exists, that it catches `Throwable` (the whole point of REL-06-B),
   or that the catch body does not rethrow. `try { checkHealth() } finally { }` passes. So the two
   grandfathered inline guards can be silently gutted while the suite stays green — the failure mode
   the test's own KDoc at `:63-66` says it exists to prevent.
2. `:43` and `:94` compare `File.name`, not the path relative to `MAIN_SOURCE_ROOT`. A new
   `foo/bar/ActiveAiScanner.kt` in a different package with an unguarded `scheduleWithFixedDelay`
   would be admitted by the allowlist, and test 2 would then demand a `try` in *that* file rather than
   flagging it.

**Fix:** for (1), extend the lookahead check to require a `catch (` containing `Throwable` within a
slightly larger window (or assert on the whole enclosing block up to the schedule call's closing
paren). For (2), key the allowlist on `file.path.removePrefix("$MAIN_SOURCE_ROOT/")` and list the
three full relative paths.

---

### WR-09: The new files violate `CONVENTIONS.md` §Comments — "No temporal language: comments describe current behavior, not history or intentions without action"

**File:** `src/main/kotlin/com/six2dez/burp/aiagent/util/GuardedScheduling.kt:22-24, 50-58`;
`src/main/kotlin/com/six2dez/burp/aiagent/backends/cli/CliTempFileRegistry.kt:10-13, 41-47`;
`src/main/kotlin/com/six2dez/burp/aiagent/App.kt:41-59`;
`src/main/kotlin/com/six2dez/burp/aiagent/supervisor/AgentSupervisor.kt:1044-1049`;
`src/main/kotlin/com/six2dez/burp/aiagent/config/Defaults.kt:54-60`

**Issue:** The project's own convention document (`.planning/codebase/CONVENTIONS.md:222`) forbids
temporal/historical narration in comments and restricts KDoc to "public API classes and methods only".
The new code is dominated by it. Representative, verbatim:

- `GuardedScheduling.kt:22-24` — "That reasoning was first written inline above the health monitor's
  guard … it moves here because the guarantee now has to hold at five scheduling sites rather than one."
- `CliTempFileRegistry.kt:10-13` — "It replaces the JDK's `File.deleteOnExit()` facility, which
  `CliBackend` used to call once per CLI invocation."
- `App.kt:47-56` — nineteen lines narrating two *rejected* alternatives and referencing "Phase 23
  (REL-05) … eight plans".
- `AgentSupervisor.kt:1045-1046` — "It used to run on the shared workerPool; once that pool is
  bounded (App.kt) …".
- `Defaults.kt:56-58` — three lines arguing about whether 262 144 is "eight times" 32 000.

The volume compounds it: `GuardedScheduling.kt` is 100 lines of which roughly 65 are comment for 12
lines of executable code; `CliTempFileRegistry.kt` is 149 lines for ~35 lines of code. This is a real
maintenance hazard rather than a style quibble — every one of these blocks encodes a claim about a
*different* file that nothing keeps in sync (WR-10 and IN-01 are both instances of such a claim
already being stale or wrong, and CR-02's comment is an instance of one being incorrect on the day it
was written).

**Fix:** compress each block to the invariant a future reader must not break, and move the
alternatives-considered narrative to the phase's decision records, which already hold it (24-CONTEXT
D-01…D-04). Keep the parts that state current behaviour — the AWT-free contract, the "head not tail"
retention rule, the D-04 window-not-closed statement — and drop the history.

---

### WR-10: `processQueue` loses the polled target and abandons the rest of the batch when a submit is rejected — now silently, because `scheduleGuarded` absorbs the throw

**File:** `src/main/kotlin/com/six2dez/burp/aiagent/scanner/ActiveAiScanner.kt:439-458`

**Issue:** `scanQueue.poll()` at `:440` removes the target *before* `exec.submit` at `:445`. If the
submit throws `RejectedExecutionException`, the target is gone: it is never scanned, `scansCompleted`
is never incremented (the `finally` at `:454` is inside the submitted body), and
`processedTargets` never records it. The throw also propagates out of the `repeat(maxConcurrent)`
lambda, so the remaining iterations of that tick never run.

Before this phase the consequence was loud-but-terminal (the schedule died). After it, the
consequence is a `[ActiveAiScanner] queue processing failed: <message>` line per tick with no target
named, while work is dropped. `ActiveScannerFailureIsolationTest.aRejectedSubmitOnTheSchedulerThreadDoesNotEndTheQueueDrainTicker`
(`:173-200`) exercises this exact path and passes while four targets are silently discarded — the test
proves the ticker survives and is blind to the work loss.

In production the window is currently narrow (`executor` is a fixed pool with an unbounded queue, so
it rejects only after `shutdownNow`, and `scanning.get()` usually short-circuits first), but the
guard's purpose is to make the *general* rejected-submit case survivable, and the general case loses
data.

**Fix:** wrap the submit per target so the target is requeued and named:

```kotlin
try {
    exec.submit { … }
} catch (e: RejectedExecutionException) {
    scanQueue.offer(target)
    api.logging().logToError("[ActiveAiScanner] Submit rejected, requeued: $targetLabel: ${e.message}")
    return@repeat
}
```

---

## Info

### IN-01: Stale comment — `requestExecutor` is no longer a cached thread pool

**File:** `src/main/kotlin/com/six2dez/burp/aiagent/App.kt:251-252`
**Issue:** "shutdown() is the only place the per-request requestExecutor (a cached thread pool) is
terminated on unload" — this phase replaced it with an explicit bounded `ThreadPoolExecutor`
(`ActiveAiScanner.kt:94-102`). The comment now describes a construction that the phase's own
structural gate (`ScanRequestExecutorTest.theScannerDeclaresNoUnboundedPoolAnd…`) forbids.
**Fix:** drop the parenthetical, or say "the bounded per-request pool".

### IN-02: Scanner configuration fields are shared, mutable and non-`@Volatile`

**File:** `src/main/kotlin/com/six2dez/burp/aiagent/scanner/ActiveAiScanner.kt:139-151`
**Issue:** `executor`, `scheduledExecutor`, `maxConcurrent`, `timeoutSeconds`, `scopeOnly`,
`scanMode`, `maxPayloadsPerPoint`, `requestDelayMs`, `maxRiskLevel`, `useCollaborator`, `maxQueueSize`
are written from the EDT (`SettingsPanelScannerTabs.kt:210`, `SettingsPanelSettingsIO.kt:589`,
`App.kt:174`) and read from the scheduler and worker threads with no happens-before edge. Pre-existing,
but this is a phase about publication/visibility and `App.kt:90-91` explicitly reasons about
`maxConcurrent` being "a mutable var written later from settings".
**Fix:** mark the mutable configuration fields `@Volatile` (they are all primitives or immutable
references, so no further coordination is needed), or route them through a single immutable snapshot
object swapped atomically.

### IN-03: Dead code in `ActiveAiScanner.shutdown()`

**File:** `src/main/kotlin/com/six2dez/burp/aiagent/scanner/ActiveAiScanner.kt:1721-1722`
**Issue:** `stopProcessing()` at `:1720` sets `executor = null` and `scheduledExecutor = null`
(`:419-420`), so the two `?.shutdownNow()` calls immediately after are unconditionally no-ops.
**Fix:** delete both lines.

### IN-04: After a surrogate-shortened cut, `CliOutputBuffer` can splice a later line's characters onto the retained head

**File:** `src/main/kotlin/com/six2dez/burp/aiagent/backends/cli/CliOutputBuffer.kt:69-77`
**Issue:** When the cut moves back one character to avoid splitting a surrogate pair, `retained.length`
ends one below `maxChars`, so the *next* `appendLine` sees `room == 1` and appends one character of a
different line onto the truncated head. The result is a head that mixes two lines, already flagged as
truncated. Extremely narrow, but it contradicts the "the retained content is the HEAD of what was
appended" property the test at `:141-164` asserts for the general case.
**Fix:** short-circuit once truncated — `if (truncatedFlag) return` as the first statement inside the
`synchronized` block. This also makes `appendLine` O(1) after the cap.

### IN-05: Over-broad structural counters will fail confusingly on unrelated edits

**File:** `src/test/kotlin/com/six2dez/burp/aiagent/backends/cli/CliOutputBufferTest.kt:276-323`
**Issue:** The `StringBuilder` counter forbids the token anywhere in the 1000-line `CliBackend.kt`,
not in "the capture region" the failure message names — a legitimate `StringBuilder` in an unrelated
helper would fail with a message about a data race that does not exist. Likewise
`code.count { it.contains("take(2000)") } == 3` couples this suite to `buildExitError`, which the
message itself says is out of REL-07's scope.
**Fix:** scope both counters to the `send(...)` capture region (the suite already has a
`functionBody`-style helper pattern available in `ScanRequestExecutorTest.kt:258-273`), or narrow the
forbidden token to the actual declaration (`val rawOutput = StringBuilder()`).

### IN-06: Five executor construction sites still produce anonymous `pool-N-thread-M` threads, and nothing guards against a sixth

**File:** `src/main/kotlin/com/six2dez/burp/aiagent/backends/cli/CliBackend.kt:97, 614, 615`;
`src/main/kotlin/com/six2dez/burp/aiagent/mcp/McpSupervisor.kt:38`;
`src/main/kotlin/com/six2dez/burp/aiagent/mcp/KtorMcpServerManager.kt:45`;
`src/main/kotlin/com/six2dez/burp/aiagent/mcp/external/ExternalMcpClientManager.kt:123`
**Issue:** SC6's stated purpose is a readable Burp thread dump. Six sites still create unnamed,
non-daemon executor threads — including the three in `CliBackend`, which is the subsystem this phase
spent two plans on. SC6 names only the two pools, so this is scope-accurate, but it means the stated
goal is only partially delivered and there is no structural gate (unlike REL-06-D) to stop a seventh.
**Fix:** record as a follow-up; a `ThreadFactoryCoverageTest` in the shape of
`SchedulerGuardCoverageTest` would close it cheaply.

### IN-07: `catch (e: TimeoutException)` binds an unused variable

**File:** `src/main/kotlin/com/six2dez/burp/aiagent/scanner/ActiveAiScanner.kt:1158`
**Issue:** `e` is never referenced in the arm (the message is built from `timeout` and the URL). The
sibling arm at `:1162` correctly uses `_`.
**Fix:** `catch (_: TimeoutException)`.

### IN-08: `oneShotScheduleCallSitesAreDeliberatelyOutOfRel06Scope` is near-tautological

**File:** `src/test/kotlin/com/six2dez/burp/aiagent/util/SchedulerGuardCoverageTest.kt:93-106`
**Issue:** It asserts two hard-coded file names are absent from a set built by matching two full
tokens neither file contains. It also passes vacuously if either file is renamed or deleted. Its
stated purpose — proving the match is not a bare `schedule(` prefix — would be tested directly and
non-vacuously by asserting the matcher rejects the string `scheduler.schedule(task, 1, SECONDS)`.
**Fix:** replace with a unit assertion on `String.isRecurringSchedule()` over positive and negative
literal inputs.

### IN-09: Two test-hygiene leaks

**File:** `src/main/kotlin/com/six2dez/burp/aiagent/backends/cli/CliTempFileRegistry.kt:136-139`;
`src/test/kotlin/com/six2dez/burp/aiagent/scanner/ActiveScannerFailureIsolationTest.kt:178-180`
**Issue:** (a) `resetForTests()` nulls the `hook` reference without removing the JVM hook; the KDoc
documents this, but it means any test that arms a hook and does not reach `shutdown()` (e.g. one that
fails inside `@BeforeEach`) leaves a live hook in the JVM for the rest of the Gradle test worker's
life. (b) `aRejectedSubmitOnTheSchedulerThreadDoesNotEndTheQueueDrainTicker` overwrites the private
`executor` field after `setEnabled(true)` has already created a real fixed pool; that pool is never
shut down, so its threads (up to `maxConcurrent`) leak for the JVM's lifetime. Both are daemon threads
so they do not block the build, but they accumulate across the suite.
**Fix:** (a) make `resetForTests()` remove an armed hook before nulling, or rename it to make the
contract unmissable. (b) capture and shut down the replaced executor:
`(executorField.get(live) as? ExecutorService)?.shutdownNow()` before setting the stub.

---

## What I checked and found correct

Recorded so a follow-up review does not re-litigate them:

- **`CliOutputBuffer` lock discipline** — `appendLine`, `snapshot`, `truncated` and `length` all take
  the same monitor; no check-then-act escapes it; the non-truncated `snapshot()` returns
  `retained.toString()` with nothing appended, so the success-path round trip is byte-identical.
- **Append semantics unchanged** — verified against the compiled stdlib (`bipush 10`), not assumed.
- **`ThreadPoolExecutor(0, MAX, …, SynchronousQueue, …, AbortPolicy)`** — not the `corePoolSize = 0`
  starvation trap (that requires a queue whose `offer` succeeds); `ScanRequestExecutorTest`'s
  ceiling/rejection boundary test is deterministic, not racy, because all 32 accepted tasks block.
- **`sendRequestWithTimeout`** — the submit is inside the `try`, `RejectedExecutionException` degrades
  to `null` with a specific log line, and `future?.cancel(true)` is null-safe on every arm.
- **The `outputFile` / `promptFile` branches are mutually exclusive by `backendId`**
  (`codex-cli` vs `claude-cli`/`copilot-cli`), so the write-failure `return@submit` at `:163` cannot
  strand a registered codex output file.
- **`safeShutdownStep` catches `IllegalStateException`**, so `CliTempFileRegistry.shutdown()`'s
  un-caught `removeShutdownHook` is genuinely isolated as its KDoc claims, and the later steps still
  run.
- **`App.shutdown()` ordering** — nothing `CliTempFileRegistry.shutdown()` depends on has been torn
  down at that point; it touches no Burp API.
- **`drain()` running concurrently from the exit hook and `App.shutdown()`** cannot deadlock (no lock
  held across the delete) and a double `File.delete()` returns `false` rather than throwing.
- **Executor asymmetry** (`AbortPolicy` on the scanner pool vs. an unbounded queue and no rejection
  policy on `workerPool`) is correct as built and should not be unified; the log pump correctly moved
  off `workerPool` onto its own explicitly-daemon thread.
- **English-only** (`AGENTS.md`) — no non-English text in any changed file.

---

_Reviewed: 2026-08-21T15:31:49Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
