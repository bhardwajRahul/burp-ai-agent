# Phase 24: Scheduler & Process Robustness — Pattern Map

**Mapped:** 2026-08-21
**Files analyzed:** 20 (5 new production, 8 modified production, 6 new/rewritten test, 1 build script)
**Analogs found:** 19 / 20 (one partial — see §No Analog Found)

> Every excerpt below is verbatim from this repo at the cited line. Nothing here is invented.
> This phase is **gap-closing**, not mechanism-inventing: the guard idiom, the named-daemon-factory
> idiom, the synchronized-bounded-buffer idiom and the structural-assertion idiom are all already
> written in tree. Copy them; do not design new ones.

---

## File Classification

### New production files

| New file | Role | Data Flow | Closest Analog | Match Quality |
|----------|------|-----------|----------------|---------------|
| `src/main/kotlin/com/six2dez/burp/aiagent/util/GuardedScheduling.kt` (`runGuarded`) | utility | event-driven (recurring tick) | `supervisor/AgentSupervisor.kt:78-93` (guard body) + `util/BudgetGuard.kt:1-45` (file shape) | exact (composite) |
| `src/main/kotlin/com/six2dez/burp/aiagent/backends/cli/CliOutputBuffer.kt` | utility (`internal class`) | streaming (subprocess stdout) | `backends/cli/CliBackend.kt:608-622,731-734` (`lastLines`) + `backends/http/CircuitBreaker.kt:1-45` (class shape) | role-match (composite) |
| `src/main/kotlin/com/six2dez/burp/aiagent/backends/cli/CliTempFileRegistry.kt` (`internal object`, D-02) | utility (lifecycle registry) | file-I/O | `backends/cli/CliBackend.kt:848-861` / `:862-895` (own-file `internal fun` extraction pattern) | exact (stated by D-02) |
| Named `ThreadFactory` + bounded pool construction (inline in `App.kt` / `ActiveAiScanner.kt`, or a small shared helper) | config / infrastructure | request-response | `mcp/tools/ScannerTaskRegistry.kt:20-23`, `mcp/tools/CollaboratorRegistry.kt:19-22` | exact |
| `Defaults` constant for the CLI buffer cap | config | — | `config/Defaults.kt:37-52` (`FINDINGS_BUFFER_SIZE`, `LARGE_PROMPT_THRESHOLD`, `OPENCODE_IDLE_TIMEOUT_MS`) | exact |

### Modified production files

| Modified file | Sites | Role | Data Flow | Pattern to apply |
|---------------|-------|------|-----------|------------------|
| `scanner/ActiveAiScanner.kt` | `:73` requestExecutor, `:337-353` schedulers, `:391-401` per-target catch, `:1086-1101` submit site, `:1653-1667` shutdown | service | event-driven + request-response | guard idiom (own `:346-353`), bounded pool + named factory, enrich log line with `target.id` |
| `mcp/tools/ScannerTaskRegistry.kt` | `:25-31` | registry | event-driven | wrap `cleanupExpired()` in `runGuarded` |
| `mcp/tools/CollaboratorRegistry.kt` | `:24-30` | registry | event-driven | wrap `cleanupExpired()` in `runGuarded` |
| `App.kt` | `:38` workerPool, `:220-247` shutdown | config / lifecycle | request-response | bounded pool + named factory; `safeShutdownStep` for the D-03 drain |
| `backends/cli/CliBackend.kt` | `:116-123`, `:135-138` deleteOnExit; `:209-224` rawOutput/reader; `:252`/`:264`/`:275` reads; `:288-307` finally | service | streaming + file-I/O | `CliOutputBuffer` swap-in; registry register/deregister |
| `supervisor/AgentSupervisor.kt` | `:1037-1047` log pump | service | streaming | move pump off `workerPool` onto a named daemon `Thread` — copy `CliBackend.kt:216-225` |
| `build.gradle.kts` | `tasks.test` `inputs` block (`:167-233`) | config | — | one `inputs.file(...).withPropertyName(...)` per structural assertion |

### New / rewritten test files

| Test file | Role | Data Flow | Closest Analog | Match Quality |
|-----------|------|-----------|----------------|---------------|
| `util/GuardedSchedulingTest.kt` | test (behavioural, concurrency) | event-driven | `ui/SettingsPersistQueueTest.kt:79-120` (latch + `assertTimeoutPreemptively`) | exact |
| `util/SchedulerGuardCoverageTest.kt` | test (structural) | file-I/O | `ui/SettingsPersistQueueTest.kt:309-341` + `codeLinesOf` `:369-383` | exact |
| `scanner/ActiveScannerFailureIsolationTest.kt` | test (headless, deep-stub Montoya) | event-driven | `scanner/ScannerQueueBackpressureTest.kt:18-48` | exact |
| `backends/cli/CliOutputBufferTest.kt` | test (unit + structural) | streaming | `util/BudgetGuardTest.kt:1-30` (pure unit) + `SettingsPersistQueueTest.kt:369-383` (structural half) | exact (composite) |
| `backends/cli/CliBackendTempFileTest.kt` (**rewrite**) | test (unit + structural) | file-I/O | its own surviving `:41-62` / `:65-85` control tests; delete `:87-133` | exact |
| `BoundedExecutorTest.kt` (package: recommend `com.six2dez.burp.aiagent.util`) | test (unit + structural) | request-response | `util/BudgetGuardTest.kt` + `SettingsPersistQueueTest.kt:369-383` | role-match |

---

## Pattern Assignments

### `util/GuardedScheduling.kt` — new `runGuarded` helper (utility, event-driven)

**Primary analog:** `src/main/kotlin/com/six2dez/burp/aiagent/supervisor/AgentSupervisor.kt:78-93`
**Secondary analog (file shape / KDoc conventions for a `util` file):** `src/main/kotlin/com/six2dez/burp/aiagent/util/BudgetGuard.kt:1-20`

**The guard body to copy — including its comment, which is the reason the helper exists** (`AgentSupervisor.kt:78-93`):

```kotlin
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

**Second in-tree instance of the same idiom** (`scanner/ActiveAiScanner.kt:346-353`) — note it catches `Throwable`, not `Exception`, which is what REL-06-B pins:

```kotlin
        oastPoller = Executors.newSingleThreadScheduledExecutor()
        oastPoller?.scheduleWithFixedDelay({
            try {
                pollOastInteractions()
            } catch (e: Throwable) {
                api.logging().logToError("[ActiveAiScanner] OAST poll failed: ${e.message}")
            }
        }, OAST_POLL_INTERVAL_SECONDS, OAST_POLL_INTERVAL_SECONDS, TimeUnit.SECONDS)
```

**Extracted contract for the helper (both analogs agree on all four points):**
1. `catch (e: Throwable)` — not `Exception`. Both existing sites already do this.
2. Log format is `"[Component] <label> failed: ${e.message}"` via `api.logging().logToError(...)` — the `[Component]` prefix is mandated by `.planning/codebase/CONVENTIONS.md` §Error Handling.
3. The guard swallows; it never rethrows.
4. The explanatory comment about silent cancellation moves into the helper's KDoc.

**File shape for a `util`-package helper** (`util/BudgetGuard.kt:1-20`) — package line, KDoc naming the AWT-free contract and the reason the file exists, then a pure `object`:

```kotlin
package com.six2dez.burp.aiagent.util

/**
 * AWT-free per-session token-budget decision object.
 * ...
 * ### AWT-free contract
 * This file MUST NOT import `java.awt.*` or `javax.swing.*`. The decision function is exercised
 * by unit tests in a headless context ...
 */
object BudgetGuard {
```

**detekt trap — the house answer for a generic catch** (`ui/SettingsPanelSettingsIO.kt:284-293`). `TooGenericExceptionCaught` is active and `detekt-baseline.xml` is a held metric that must not be regenerated. The pattern is `@Suppress` with a KDoc paragraph justifying it:

```kotlin
 * **`@Suppress("LongMethod")` rather than a regenerated baseline.** This function was already carried
 * by `detekt-baseline.xml` as `LongMethod`, but the baseline keys on the full signature ...
 * `detekt-baseline.xml` is a v0.10.0 milestone metric held at 1096 entries and must
 * not be regenerated (23-03 and 23-05 answered their own new findings the same way).
 */
@Suppress("LongMethod")
```

→ For `runGuarded`, write `@Suppress("TooGenericExceptionCaught")` with an equivalent KDoc paragraph naming *why* `Throwable` is required (an `Error` on the tick also cancels the schedule).

---

### `util/SchedulerGuardCoverageTest.kt` — structural allowlist (test, file-I/O)

**Analog:** `src/test/kotlin/com/six2dez/burp/aiagent/ui/SettingsPersistQueueTest.kt:309-341` (the assertion) and `:369-383` (the `codeLinesOf` helper).

**Assertion shape — counted tokens with a ledger message that names the defect a wrong count means** (`:309-341`):

```kotlin
    fun everyMainTabSettingsWriteGoesThroughThePersistQueue() {
        val code = codeLinesOf(MAIN_TAB_SOURCE)

        assertEquals(
            6,
            code.count { it.contains("persistSettings(") },
            "MainTab ledger: `persistSettings(` must be 1 declaration + 5 call sites (backend picker, " +
                "passive/active host callbacks, passive/active header toggles). A different count means " +
                "a site was added, removed, or regressed to an inline settingsRepo.save on the EDT.",
        )
```

**The comment-stripping reader — copy this verbatim, including the `file.isFile` guard and its message** (`:369-383`):

```kotlin
    private fun codeLinesOf(path: String): List<String> {
        val file = File(path)
        assertTrue(
            file.isFile,
            "Expected to find `$path` relative to the test working directory " +
                "`${System.getProperty("user.dir")}`, resolved as `${file.absolutePath}`. If the build " +
                "layout changed, fix the path here and in the matching `tasks.test` input declaration.",
        )
        return file
            .readLines()
            .filterNot { line ->
                val trimmed = line.trimStart()
                trimmed.startsWith("//") || trimmed.startsWith("*") || trimmed.startsWith("/*")
            }
    }
```

Two load-bearing details: comment lines are stripped **so the counted tokens can safely be named in the ledger KDoc**, and the `isFile` assertion converts a build-layout change from a bare `FileNotFoundException` into a message that tells the reader to also fix the `tasks.test` input declaration.

**REL-06-D adaptation:** instead of counting tokens in one file, walk `src/main/kotlin` for files containing `scheduleAtFixedRate(` / `scheduleWithFixedDelay(` and `assertEquals` the resulting set against a named allowlist. Note that a **directory walk cannot be declared as a single `inputs.file`** — see §Shared Patterns / Gradle input declarations for the consequence.

---

### `util/GuardedSchedulingTest.kt` — behavioural tick-survival (test, event-driven)

**Analog:** `src/test/kotlin/com/six2dez/burp/aiagent/ui/SettingsPersistQueueTest.kt:79-120`

**Import block to copy** (`:6-21`) — this is the project's concurrency-test import set:

```kotlin
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertTimeoutPreemptively
import java.time.Duration
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
```

**Core pattern — latch handshake wrapped in `assertTimeoutPreemptively`, never an elapsed-time comparison** (`:81-103`):

```kotlin
        assertTimeoutPreemptively(Duration.ofSeconds(30)) {
            val queue = SettingsPersistQueue { errors.add(it) }
            val applyEntered = CountDownLatch(1)
            ...
            assertTrue(applyEntered.await(20, TimeUnit.SECONDS), "SC4: the submit never reached the apply body.")
```

**Two conventions this file establishes that Phase 24 must inherit** (`:26-40`):

```kotlin
 * **Naming constraint (hard).** `build.gradle.kts` excludes `*IntegrationTest`, `*ConcurrencyTest`,
 * `*BackpressureTest`, `*RestartPolicyTest` and `*SupervisionTest` under `-PexcludeHeavyTests=true`,
 * which is exactly what `.github/workflows/build.yml` passes. `SettingsPersistQueueTest` is the
 * approved name; `SettingsPersistConcurrencyTest` would have been the natural one and would have made
 * this suite silently stop running on the PR gate.
 *
 * **No test here compares an elapsed duration to a threshold.** Blocking is proved by mutual latches
 * whose failure mode is a deadlock [assertTimeoutPreemptively] reports categorically ...
```

Failure recording uses `CopyOnWriteArrayList` accumulators cleared in `@BeforeEach` (`:44-62`), not shared mutable state.

---

### `backends/cli/CliOutputBuffer.kt` — new bounded thread-safe buffer (utility, streaming)

**Closest in-tree accumulator — same file, same subprocess-reader concern** (`backends/cli/CliBackend.kt:608, 616-622`):

```kotlin
        private val lastLines = ArrayDeque<String>(50)
        ...
                            synchronized(lastLines) {
                                if (lastLines.size >= 50) lastLines.removeFirst()
                                lastLines.addLast(line)
                            }
                            outputQueue.offer(line)
```

**Its matching guarded read** (`CliBackend.kt:731-734`) — the read side takes the same monitor, which is exactly the invariant REL-07-A asserts and the one `rawOutput` at `:209` currently violates:

```kotlin
        override fun lastOutputTail(): String? {
            val tail = synchronized(lastLines) { lastLines.joinToString("\n") }
            return tail.ifBlank { null }
        }
```

**Class shape analog — a small `internal`-style class with a private monitor, constructor-injected bounds, and `require` preconditions** (`backends/http/CircuitBreaker.kt:3-32`):

```kotlin
class CircuitBreaker(
    private val failureThreshold: Int = 5,
    private val resetTimeoutMs: Long = 30_000,
    private val halfOpenMaxAttempts: Int = 1,
    private val nowProvider: () -> Long = { System.currentTimeMillis() },
) {
    init {
        require(failureThreshold > 0) { "failureThreshold must be > 0" }
        ...
    }

    private val lock = Any()
    private var state: State = State.CLOSED
    ...
    fun tryAcquire(): Permission {
        synchronized(lock) {
```

Note `nowProvider` — the house way to make a class unit-testable without a real clock. `CliOutputBuffer` needs the equivalent seam for its cap: a constructor `maxChars` parameter defaulting to a `Defaults` constant, so the test can drive a small cap while production uses the generous one.

**The site being replaced** (`CliBackend.kt:209, 216-225`) — a plain `StringBuilder` appended on `burp-ai-agent-cli-reader` and read on the caller thread after a `join(2000)` that can expire mid-append:

```kotlin
                        val rawOutput = StringBuilder()
                        ...
                        val readerThread =
                            Thread({
                                val reader = BufferedReader(InputStreamReader(process.inputStream))
                                reader.forEachLine { line ->
                                    rawOutput.appendLine(line)
                                    hasOutput.set(true)
                                    lastOutputAt.set(System.currentTimeMillis())
                                }
                            }, "burp-ai-agent-cli-reader")
                        readerThread.isDaemon = true
                        readerThread.start()
```

**⚠ The three read sites, and the trap.** Two take a bounded head; the third is the real answer and must not be bounded tightly:

```kotlin
:252   val tail = rawOutput.toString().trim().take(2000)      // timeout error path
:264   val tail = rawOutput.toString().trim().take(2000)      // non-zero exit error path
:275   val stdoutText = stripAnsiCodes(rawOutput.toString())  // THE MODEL RESPONSE — full value
```

A `maxChars` of 2000 would silently corrupt every CLI backend's output. Retain the **head** (`take` returns the first N chars) so both error messages keep their current bytes.

**Cap constant home** (`config/Defaults.kt:37-52`) — same style as its neighbours:

```kotlin
    const val FINDINGS_BUFFER_SIZE = 50
    const val LARGE_PROMPT_THRESHOLD = 32_000
    const val OPENCODE_IDLE_TIMEOUT_MS = 30_000L
```

detekt's `MagicNumber` is active with a large baseline; a literal cap in the new class would be a new finding. Put it in `Defaults` with underscore digit grouping.

---

### `backends/cli/CliTempFileRegistry.kt` — new `internal object` (utility, file-I/O) — D-02

**Analog (named by D-02): the file's own extraction pattern.** `CliBackend.kt:846-861`:

```kotlin
/**
 * ...
 * Visibility: `internal` so CliTimeoutMessageTest can call it directly (same pattern as
 * buildCopilotCommand / CopilotCommandBuilderTest). Not part of the public API.
 */
internal fun buildTimeoutMessage(
    tail: String,
    timeoutSeconds: Int,
): String {
```

and `CliBackend.kt:874-880`:

```kotlin
 * Visibility: `internal` (module-scoped) so the test in `src/test/kotlin/.../cli/CopilotCommandBuilderTest.kt`
 * can call it directly without reflection. Not part of the backend's public surface.
 */
internal fun buildCopilotCommand(
    cmd: List<String>,
    prompt: String,
): List<String> {
```

**The KDoc contract to copy:** a `Guarantees:` bullet list, then an explicit **`Visibility:`** paragraph stating which test drives the seam and that it is not public API. Per D-04 the new object adds one more mandatory paragraph naming the window it does **not** close (SIGKILL / power loss ⇒ at most one orphan per in-flight call, in the OS temp dir).

**The call sites being replaced** (`CliBackend.kt:116-123`):

```kotlin
                    val outputFile =
                        if (backendId == "codex-cli") {
                            // REL-02: deleteOnExit() registers a JVM shutdown hook as crash-safety net;
                            // the finally block below (:274-288) is the primary cleanup path.
                            java.io.File
                                .createTempFile("burp-ai-agent-codex", ".txt")
                                .also { it.deleteOnExit() }
```

and `CliBackend.kt:135-138`:

```kotlin
                        val tFile = java.io.File.createTempFile("burp_uv_prompt_", ".txt")
                        // REL-02: deleteOnExit() registers a JVM shutdown hook as crash-safety net;
                        // the finally block below (:274-288) is the primary cleanup path.
                        tFile.deleteOnExit()
```

→ `.also { it.deleteOnExit() }` becomes `.also { CliTempFileRegistry.register(it) }`.

**The primary cleanup path that stays and gains the deregister** (`CliBackend.kt:288-307`) — note the `catch (_: Exception) {}` with an `// INTENTIONAL:` comment, which is the blessed form for `finally`-block cleanup per CONVENTIONS.md §Error Handling and is what makes the D-03 drain's idempotence free:

```kotlin
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

---

### `backends/cli/CliBackendTempFileTest.kt` — **rewrite** (test, file-I/O)

**Analog: its own two surviving tests.** `:41-62` and `:65-85` exercise the `finally` path D-01 preserves and stay as the control:

```kotlin
    @Test
    fun uvPromptTempFileIsCleanedUpAfterFailure() {
        val before = tempFilesMatching("burp_uv_prompt_")
        ...
        val after = tempFilesMatching("burp_uv_prompt_")
        assertEquals(before, after, "burp_uv_prompt_ temp file leaked after simulated failure")
    }
```

with the helper at `:22-30`:

```kotlin
    private fun tempDir(): File = File(System.getProperty("java.io.tmpdir"))

    private fun tempFilesMatching(prefix: String): Set<String> =
        tempDir()
            .listFiles()
            ?.filter { it.name.startsWith(prefix) }
            ?.map { it.absolutePath }
            ?.toSet()
            ?: emptySet()
```

**Delete outright — this is an anti-pattern, not an analog** (`:120-133`). It is fail-open: on JDK 21 the reflection throws `InaccessibleObjectException` and the `catch` converts it into a pass, so both `…DeleteOnExitIsRegistered` tests (`:95`, `:106`) are vacuous:

```kotlin
    private fun isRegisteredForDeleteOnExit(file: File): Boolean {
        return try {
            val hookClass = Class.forName("java.io.DeleteOnExitHook")
            ...
        } catch (_: Exception) {
            // If reflection fails (future JDK), check presence indirectly:
            // the test is best-effort; do not fail the build on JVM internals change.
            true
        }
    }
```

Remove `:87-133` entirely (both tests, the helper, and the `import java.lang.reflect.Field` at `:7`). Replace with direct assertions against the D-02 seam — `register` → size 1; register the same file twice → still 1; `deregister` → 0; `drain` deletes a registered file; `drain` is idempotent when the file is already gone. The class KDoc at `:9-18` must be rewritten too; it currently advertises the removed guarantee.

---

### `scanner/ActiveScannerFailureIsolationTest.kt` — new (test, headless deep-stub)

**Analog:** `src/test/kotlin/com/six2dez/burp/aiagent/scanner/ScannerQueueBackpressureTest.kt:18-48`

**Headless construction of the real `ActiveAiScanner` — the whole reason this suite is drivable** (`:20-27`):

```kotlin
        val scanner =
            ActiveAiScanner(
                api = mock<MontoyaApi>(defaultAnswer = Answers.RETURNS_DEEP_STUBS),
                supervisor = mock<AgentSupervisor>(),
                audit = mock<AuditLogger>(),
            ) { TestSettings.baselineSettings() }
        scanner.scopeOnly = false
        scanner.maxQueueSize = 3
        scanner.scanMode = ScanMode.FULL
```

The trailing lambda is the `getSettings` supplier — that is the injection lever REL-06-F needs (throw for target A only). Fixtures are built with `mock<ParsedHttpParameter>()` + `whenever(...)` stubs (`:51-60`).

**⚠ Naming:** `*BackpressureTest` is on the `-PexcludeHeavyTests` exclusion list (`build.gradle.kts:238-243`), which is what the PR gate passes. `ActiveScannerFailureIsolationTest` is safe; do not rename toward `…ConcurrencyTest`.

**The production site under test** (`scanner/ActiveAiScanner.kt:391-401`) — the per-target isolation already exists; REL-06-G's red half is that the log line carries no target identity:

```kotlin
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
```

`target.id` is already in scope at `:399` — the enrichment is a one-line change to the `:396` message.

**⚠ Also note `:389` `exec.submit {` sits OUTSIDE any try.** Once SC6 bounds `executor`, this line can throw `RejectedExecutionException` on the scheduler thread — which is precisely the throw site SC1's `runGuarded` around `processQueue()` must catch. SC1 and SC6 are coupled here.

---

### Bounded pools + named `ThreadFactory` (config, request-response)

**Analog — the house named-daemon-factory idiom, written identically twice.** `mcp/tools/ScannerTaskRegistry.kt:19-31`:

```kotlin
    private val cleaner =
        Executors.newSingleThreadScheduledExecutor { runnable ->
            Thread(runnable, "McpScannerTaskRegistryCleaner").apply { isDaemon = true }
        }

    init {
        cleaner.scheduleWithFixedDelay(
            { cleanupExpired() },
            CLEANUP_INTERVAL_MINUTES,
            CLEANUP_INTERVAL_MINUTES,
            TimeUnit.MINUTES,
        )
    }
```

`mcp/tools/CollaboratorRegistry.kt:19-30` is byte-identical apart from the thread name `"McpCollaboratorRegistryCleaner"`. Both `scheduleWithFixedDelay({ cleanupExpired() }, …)` bodies are the unguarded sites SC1 closes — the minimal diff is `{ runGuarded("…") { cleanupExpired() } }`.

**Thread-name convention:** `McpScannerTaskRegistryCleaner`, `McpCollaboratorRegistryCleaner` (PascalCase, subsystem-prefixed) vs `burp-ai-agent-cli-reader` (`CliBackend.kt:225`) and `burp-ai-settings-sync` (asserted in `SettingsPersistQueueTest.kt:115`) (kebab-case, product-prefixed). **The kebab-case `burp-ai-*` form is the newer one** and is the only one an existing test asserts on; prefer it for the two new pools.

**Sites to change** (`App.kt:38`, `scanner/ActiveAiScanner.kt:73`) — both plain, unnamed, unbounded:

```kotlin
    private val workerPool = Executors.newCachedThreadPool()          // App.kt:38
    private val requestExecutor = Executors.newCachedThreadPool()     // ActiveAiScanner.kt:73
```

**Existing bounded-pool precedent in the same method being changed** (`ActiveAiScanner.kt:337`) — `newFixedThreadPool(maxConcurrent)` is already the house shape for a sized pool; it just lacks a named factory:

```kotlin
        executor = Executors.newFixedThreadPool(maxConcurrent)
```

**⚠ Prerequisite — the long-lived occupant of `workerPool`** (`supervisor/AgentSupervisor.kt:1037-1047`). This task pumps a service process's stdout for the entire life of the process; a naive bound stalls the extension:

```kotlin
            workerPool.submit {
                try {
                    process.inputStream.bufferedReader().use { reader ->
                        reader.forEachLine { line ->
                            safeLogOutput("[$name] $line")
                        }
                    }
                } catch (e: Exception) {
                    safeLogOutput("[$name] output stream closed: ${e.message}")
                }
            }
```

**Pattern to move it onto** — the dedicated named daemon `Thread`, already used for exactly this shape of work in `CliBackend.kt:216-225`:

```kotlin
                        val readerThread =
                            Thread({
                                val reader = BufferedReader(InputStreamReader(process.inputStream))
                                reader.forEachLine { line -> ... }
                            }, "burp-ai-agent-cli-reader")
                        readerThread.isDaemon = true
                        readerThread.start()
```

**Shutdown patterns to preserve.** `ActiveAiScanner.kt:1653-1667` (`shutdown` → `awaitTermination` → `shutdownNow`, `catch (_: InterruptedException)` with an `// INTENTIONAL:` comment):

```kotlin
        requestExecutor.shutdown()
        try {
            if (!requestExecutor.awaitTermination(3, TimeUnit.SECONDS)) {
                requestExecutor.shutdownNow()
            }
        } catch (_: InterruptedException) {
            // INTENTIONAL: thread interrupt on executor shutdown; shutdownNow() for immediate termination
            requestExecutor.shutdownNow()
        }
```

**REL-07-I's seam** (`ActiveAiScanner.kt:1086-1101`) — `requestExecutor.submit(...)` at `:1089` is outside the `try` that starts at `:1094`, so a `RejectedExecutionException` propagates rather than returning `null`:

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
            api.logging().logToError("[ActiveAiScanner] Request timeout after ${timeout}s for ${request.url().take(80)}")
            null
```

The fix is to move the `submit` inside the `try` and return `null` on rejection, matching the `TimeoutException` arm's shape (log with `[ActiveAiScanner]` prefix, return `null`).

---

### `App.kt` shutdown — the D-03 drain insertion point (config, lifecycle)

**Analog: the surrounding method itself** (`App.kt:220-247`). Every step is wrapped in `safeShutdownStep("<Label>") { … }`, and the ordering is explicit and commented:

```kotlin
    fun shutdown() {
        safeShutdownStep("MainTab") { mainTab?.shutdown() }
        mainTab = null
        safeShutdownStep("AI Request Logger") { aiRequestLogger.shutdown() }
        ...
        // Split so a throw in setEnabled(false) can't skip shutdown() — shutdown() is the only
        // place the per-request requestExecutor (a cached thread pool) is terminated on unload.
        safeShutdownStep("Active scanner disable") { activeAiScanner.setEnabled(false) }
        safeShutdownStep("Active scanner") { activeAiScanner.shutdown() }
        safeShutdownStep("Supervisor") { supervisor.shutdown() }
        safeShutdownStep("MCP supervisor") { mcpSupervisor.shutdown() }
        safeShutdownStep("Backend registry") { backendRegistry.shutdown() }
        BackendDiagnostics.retry = null
        safeShutdownStep("Worker pool") {
            workerPool.shutdown()
            try {
                if (!workerPool.awaitTermination(5, TimeUnit.SECONDS)) {
                    workerPool.shutdownNow()
                }
            } catch (e: InterruptedException) {
                workerPool.shutdownNow()
                throw e
            }
        }
        safeShutdownStep("Alerting client") { Alerting.shutdownClient() }
        safeShutdownStep("Redaction mappings") { Redaction.clearMappings() }
```

→ The D-03 drain is one new `safeShutdownStep("CLI temp files") { CliTempFileRegistry.shutdown() }`. **Place it after `"Backend registry"` and before `"Worker pool"`** — the CLI executor lives under the backend registry, so a call in flight has already been terminated by then, and the drain therefore sweeps a settled set. `safeShutdownStep` already isolates a throw, so the drain cannot break the sequence.

---

## Shared Patterns

### Error-handling and logging (applies to every file in this phase)

**Source:** `.planning/codebase/CONVENTIONS.md` §Error Handling, realised at `AgentSupervisor.kt:84-86`, `ActiveAiScanner.kt:349-351`, `CliBackend.kt:288-307`.

Three distinct blessed forms — use the right one:

```kotlin
// 1. Recurring/boundary guard: catch Throwable, log with [Component] prefix, swallow.
} catch (e: Throwable) {
    api.logging().logToError("[AgentSupervisor] health check failed: ${e.message}")
}

// 2. finally-block cleanup: bare catch with a mandatory // INTENTIONAL: comment.
} catch (_: Exception) {
    // INTENTIONAL: finally block cleanup; file deletion must not prevent process cleanup
}

// 3. Interrupt: restore the flag, comment why.
} catch (_: InterruptedException) {
    // INTENTIONAL: thread interrupt during executor shutdown; interrupt flag restored
    Thread.currentThread().interrupt()
}
```

Log prefix is `[<OwningClass>]`, always via `api.logging().logToError(...)`. Messages are English only.

### detekt / baseline discipline (applies to every NEW file)

**Source:** `ui/SettingsPanelSettingsIO.kt:284-293`
**Apply to:** `GuardedScheduling.kt`, `CliOutputBuffer.kt`, `CliTempFileRegistry.kt`

`TooGenericExceptionCaught` and `MagicNumber` are active; `detekt-baseline.xml` is a held metric (QUAL-07 forbids growing it) and is keyed by exact file+signature string, so a new file's finding is never covered. The answer is an in-file `@Suppress("<Rule>")` plus a KDoc paragraph justifying it — never a regenerated baseline. Numeric constants go to `config/Defaults.kt`, not to a literal.

### Gradle input declarations for structural assertions (REL-06-D, REL-07-D, REL-07-F, REL-07-G)

**Source:** `build.gradle.kts:167-233` — seven existing declarations, all with the same three-part shape.

```kotlin
    inputs
        .file("src/main/kotlin/com/six2dez/burp/aiagent/ui/MainTab.kt")
        .withPropertyName("mainTabPersistSource")
        .withPathSensitivity(PathSensitivity.RELATIVE)
```

Each is preceded by a comment naming the requirement, the suite, the assertion, and the stale-cache defect it prevents (`:187-193` is the template). The failure mode is measured and recorded in-file (`:170-176`): *"Measured: mutating the AUTO sentence left `./gradlew test` GREEN until `cleanTest` forced a re-run."*

Files Phase 24 must declare: `src/main/kotlin/com/six2dez/burp/aiagent/backends/cli/CliBackend.kt` (REL-07-D, REL-07-F), `src/main/kotlin/com/six2dez/burp/aiagent/App.kt` and `src/main/kotlin/com/six2dez/burp/aiagent/scanner/ActiveAiScanner.kt` (REL-07-G).

**⚠ REL-06-D has no exact analog.** All seven existing declarations are `inputs.file(single path)`. REL-06-D's allowlist test walks a **directory tree**, which needs `inputs.dir("src/main/kotlin").withPropertyName(...).withPathSensitivity(PathSensitivity.RELATIVE)` — a shape that does not yet exist in this build. Alternative with a real analog: pin the allowlist to the five known files and declare five `inputs.file` entries, accepting that a scheduler introduced in a sixth, undeclared file would be cache-invisible. The planner should pick deliberately; `inputs.dir` is the honest one.

### Test-suite naming (applies to all six test files)

**Source:** `build.gradle.kts:236-245`

```kotlin
    if (excludeHeavyTests) {
        filter {
            excludeTestsMatching("*IntegrationTest")
            excludeTestsMatching("*ConcurrencyTest")
            excludeTestsMatching("*BackpressureTest")
            excludeTestsMatching("*RestartPolicyTest")
            excludeTestsMatching("*SupervisionTest")
        }
    }
```

None of the six planned names matches. Any rename toward `…ConcurrencyTest` silently removes the suite from the PR gate — the exact trap `SettingsPersistQueueTest.kt:26-30` documents.

---

## No Analog Found

| File / concern | Role | Data Flow | Reason |
|----------------|------|-----------|--------|
| `inputs.dir(...)` declaration for REL-06-D's tree-walking allowlist | config | file-I/O | All seven existing `tasks.test` input declarations are single-file (`build.gradle.kts:167-233`). No directory-input precedent exists in this build. Use `RESEARCH.md` guidance / Gradle docs, or fall back to five `inputs.file` entries (see §Shared Patterns). |
| A JVM shutdown-hook registration + `Runtime.removeShutdownHook` pair | utility | file-I/O | Nothing in tree registers a `Runtime.getRuntime().addShutdownHook`; the only shutdown-hook usage is the JDK's own `deleteOnExit()`, which D-01 removes. The *lifecycle* half has an analog (`App.shutdown()`'s `safeShutdownStep` sequence); the hook registration itself is genuinely new. Follow D-03 literally: lazy registration on first `register()`, `removeShutdownHook` + drain from `App.shutdown()`, both idempotent. |

---

## Metadata

**Analog search scope:** `src/main/kotlin/com/six2dez/burp/aiagent/{util,supervisor,scanner,backends/cli,backends/http,mcp/tools,config,ui}`, `src/test/kotlin/com/six2dez/burp/aiagent/{util,scanner,ui,backends/cli}`, `build.gradle.kts`
**Files scanned:** 14 read in full or in targeted ranges; ~30 matched by grep
**Pattern extraction date:** 2026-08-21
