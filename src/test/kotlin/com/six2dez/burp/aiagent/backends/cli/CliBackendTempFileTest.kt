package com.six2dez.burp.aiagent.backends.cli

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Files

/**
 * REL-07 / SC5 — the acceptance suite for [CliTempFileRegistry], the bounded live-file registry that
 * replaces the JDK's per-invocation exit-time deletion registration in `CliBackend` (CONTEXT.md D-01
 * through D-04).
 *
 * **Naming constraint (hard).** `build.gradle.kts` excludes `*IntegrationTest`, `*ConcurrencyTest`,
 * `*BackpressureTest`, `*RestartPolicyTest` and `*SupervisionTest` under `-PexcludeHeavyTests=true`,
 * which is exactly what `.github/workflows/build.yml` passes on the PR gate. `CliBackendTempFileTest`
 * is the approved name and does not match any of them — recorded here because
 * `CliTempFileRegistryConcurrencyTest` is the natural name for a suite that drives a thread-safe
 * registry and would have made this suite silently stop running on the PR gate.
 *
 * **What this file replaced, and why it was replaced rather than inverted.** Two tests named
 * `uvPromptDeleteOnExitIsRegistered` and `codexOutputDeleteOnExitIsRegistered` used to live here with
 * a reflective helper that read the JDK's internal exit-hook file set. They were measured vacuous, not
 * blocking: each created its OWN temp file under a different prefix, neither referenced `CliBackend`
 * at all, and the helper's reflection raises `InaccessibleObjectException` on the project's JDK 21 —
 * a `RuntimeException`, which the helper's own `catch (_: Exception) { true }` converted into a pass.
 * They were incapable of ever failing. SC5 therefore had zero real coverage before this rewrite.
 *
 * **No test in this file reflects into `java.base`.** That technique is dead on every JDK 16 and
 * later and fails open by construction; `24-VALIDATION.md` §Assertions Explicitly Ruled Out records
 * it. The registry is `internal` (D-02) precisely so its state is directly observable in pure JVM,
 * with no subprocess and no reflection.
 *
 * **The two surviving tests are a control only in the weakest sense.** `uvPromptTempFileIsCleanedUpAfterFailure`
 * and `codexOutputTempFileIsCleanedUpAfterFailure` create and delete their own files and prove that
 * `File.delete()` in a `finally` block works. They are NOT coverage of `CliBackend`'s own `finally`
 * path — nothing here drives that code. They are kept because they still pin the shape D-01 preserves
 * and they must stay green across this rewrite, which is a useful regression signal and nothing more.
 */
class CliBackendTempFileTest {
    // -------- helper: list current temp files matching a prefix --------

    private fun tempDir(): File = File(System.getProperty("java.io.tmpdir"))

    private fun tempFilesMatching(prefix: String): Set<String> =
        tempDir()
            .listFiles()
            ?.filter { it.name.startsWith(prefix) }
            ?.map { it.absolutePath }
            ?.toSet()
            ?: emptySet()

    // -------- registry fixture --------

    private lateinit var workDir: File

    /**
     * Each test starts from an empty registry with no hook reference, and ends with the hook removed
     * from the JVM. `resetForTests()` deliberately does NOT unregister an already-registered hook, so
     * the `shutdown()` in the teardown below is what keeps this suite from leaving hooks behind.
     */
    @BeforeEach
    fun setUp() {
        CliTempFileRegistry.resetForTests()
        workDir = Files.createTempDirectory("cli-temp-registry-test").toFile()
    }

    @AfterEach
    fun tearDown() {
        CliTempFileRegistry.shutdown()
        workDir.listFiles()?.forEach { it.delete() }
        workDir.delete()
    }

    private fun newFile(name: String): File =
        File(workDir, name).also {
            it.writeText("payload")
        }

    // -------- behavioral test: finally-cleanup proves no leak --------

    /**
     * Create a real temp file using the same createTempFile call sites as CliBackend, then
     * simulate the failure-path (try/catch throwing before the file is used).
     * The file is explicitly deleted in the finally block, proving the contract.
     * This test validates the behavior that CliBackendTempFileTest must guard against regression.
     */
    @Test
    fun uvPromptTempFileIsCleanedUpAfterFailure() {
        val before = tempFilesMatching("burp_uv_prompt_")

        // Simulate the production code path: create the temp file, then throw before use.
        var promptFile: File? = null
        try {
            val tFile = File.createTempFile("burp_uv_prompt_", ".txt")
            promptFile = tFile
            throw RuntimeException("simulated write failure")
        } catch (_: RuntimeException) {
            // expected — mirrors the production catch in CliBackend's large-prompt branch
        } finally {
            try {
                promptFile?.delete()
            } catch (_: Exception) {
                // INTENTIONAL: mirrors the production finally block's bare cleanup catch
            }
        }

        val after = tempFilesMatching("burp_uv_prompt_")
        assertEquals(before, after, "burp_uv_prompt_ temp file leaked after simulated failure")
    }

    @Test
    fun codexOutputTempFileIsCleanedUpAfterFailure() {
        val before = tempFilesMatching("burp-ai-agent-codex")

        var outputFile: File? = null
        try {
            val tFile = File.createTempFile("burp-ai-agent-codex", ".txt")
            outputFile = tFile
            throw RuntimeException("simulated processing failure")
        } catch (_: RuntimeException) {
            // expected
        } finally {
            try {
                outputFile?.delete()
            } catch (_: Exception) {
                // INTENTIONAL: mirrors the production finally block's bare cleanup catch
            }
        }

        val after = tempFilesMatching("burp-ai-agent-codex")
        assertEquals(before, after, "burp-ai-agent-codex temp file leaked after simulated failure")
    }

    // -------- REL-07-E: the registry seam (D-01, D-02) --------

    /**
     * REL-07-E — registration is set-membership on the absolute path, so a retry that re-registers the
     * same file cannot inflate the registry. This is the boundary half of A-EDGE-4: the registry has no
     * numeric cap, its bound is structural.
     */
    @Test
    fun registeringTheSameFileTwiceStillYieldsOneEntry() {
        val first = newFile("prompt-a.txt")
        val second = newFile("prompt-b.txt")

        CliTempFileRegistry.register(first)
        assertEquals(1, CliTempFileRegistry.sizeForTests(), "One registered file must be one entry.")

        CliTempFileRegistry.register(first)
        assertEquals(
            1,
            CliTempFileRegistry.sizeForTests(),
            "Registering the SAME file twice must still be one entry. If a retry could add a second " +
                "entry the registry would grow with invocation count, which is exactly the unbounded " +
                "growth D-01 exists to remove (REL-07 / SC5, threat T-24-15).",
        )

        CliTempFileRegistry.register(second)
        assertEquals(
            2,
            CliTempFileRegistry.sizeForTests(),
            "Two DIFFERENT in-flight files must be two entries — the bound is one entry per in-flight " +
                "call, not one entry overall.",
        )
    }

    /**
     * REL-07-E — deregistration is what returns the registry to empty when a call finishes, and it must
     * tolerate a path it never held. `CliBackend`'s `finally` runs unconditionally, including on paths
     * where the optional codex output file or the optional large-prompt file was never created.
     */
    @Test
    fun deregisteringReturnsToEmptyAndTolerantOfUnknownFiles() {
        val file = newFile("prompt-c.txt")
        CliTempFileRegistry.register(file)

        CliTempFileRegistry.deregister(file)
        assertEquals(
            0,
            CliTempFileRegistry.sizeForTests(),
            "Deregistering the only registered file must return the registry to empty.",
        )

        CliTempFileRegistry.deregister(newFile("never-registered.txt"))
        assertEquals(
            0,
            CliTempFileRegistry.sizeForTests(),
            "Deregistering a file that was never registered must be a no-op, not an error: the " +
                "production finally block runs on paths where the file was never created.",
        )
    }

    /**
     * REL-07-E — the drain is the safety net's actual work: it deletes what the `finally` block did not
     * get to. This is the one case a hook uniquely covers, a clean Burp quit while a CLI call is in
     * flight.
     */
    @Test
    fun drainDeletesAStillRegisteredFileAndEmptiesTheRegistry() {
        val file = newFile("in-flight.txt")
        CliTempFileRegistry.register(file)
        assertTrue(file.isFile, "Fixture precondition: the file exists on disk before the drain.")

        CliTempFileRegistry.drain()

        assertFalse(
            file.exists(),
            "The drain must delete a file still registered when the extension shuts down. That is the " +
                "only case the safety net exists for (REL-07 / SC5).",
        )
        assertEquals(0, CliTempFileRegistry.sizeForTests(), "The drain must leave the registry empty.")
    }

    /**
     * REL-07-E (A-EDGE-4, idempotence) — both the `finally` block and the drain may run for the same
     * file. A double delete must not surface as an error; this is a stated D-03 requirement, not a
     * nicety.
     */
    @Test
    fun drainIsIdempotentWhenTheFinallyPathAlreadyDeletedTheFile() {
        val file = newFile("already-gone.txt")
        CliTempFileRegistry.register(file)
        assertTrue(file.delete(), "Fixture precondition: the finally path deletes the file first.")

        CliTempFileRegistry.drain()

        assertEquals(
            0,
            CliTempFileRegistry.sizeForTests(),
            "A drain over a file the finally block already deleted must still empty the registry and " +
                "must not throw — both mechanisms legitimately run for the same file (D-03).",
        )
    }

    /** REL-07-E (A-EDGE-4, the empty boundary) — draining an empty registry is a no-op. */
    @Test
    fun drainOnAnEmptyRegistryIsANoOp() {
        assertEquals(0, CliTempFileRegistry.sizeForTests(), "Fixture precondition: the registry is empty.")

        CliTempFileRegistry.drain()

        assertEquals(
            0,
            CliTempFileRegistry.sizeForTests(),
            "Draining an empty registry must be a no-op — a Burp session that never used a CLI backend " +
                "reaches this path on every unload.",
        )
    }

    /**
     * REL-07-E — the direct SC5 assertion. Ten complete calls, each registering its temp file and then
     * deregistering it the way the `finally` block does, must leave the registry empty. Under the
     * mechanism D-01 removes the equivalent count would be ten retained path strings for the life of
     * the Burp JVM, with no removal API.
     */
    @Test
    fun tenCompleteCallsLeaveTheRegistryEmpty() {
        repeat(SIMULATED_CALL_COUNT) { index ->
            val file = newFile("call-$index.txt")
            CliTempFileRegistry.register(file)
            file.delete()
            CliTempFileRegistry.deregister(file)
        }

        assertEquals(
            0,
            CliTempFileRegistry.sizeForTests(),
            "After $SIMULATED_CALL_COUNT completed calls the registry must be empty. A non-zero count " +
                "means the registry grows with lifetime invocation count instead of being bounded by " +
                "in-flight concurrency, which is the defect REL-07 / SC5 exists to close.",
        )
    }

    /**
     * D-03 — the hook is registered lazily on first temp-file creation, never eagerly. A Burp session
     * that never uses a CLI backend must pay nothing: no hook object, no retained extension-classloader
     * class in the JVM's shutdown-hook table.
     */
    @Test
    fun theShutdownHookIsRegisteredLazilyOnTheFirstTempFile() {
        assertFalse(
            CliTempFileRegistry.isHookRegisteredForTests(),
            "No hook may exist before the first register(...). Registering eagerly would make every " +
                "Burp session pay for a CLI backend it may never use, and would put an " +
                "extension-classloader object into the JVM's shutdown-hook table at load time (D-03).",
        )

        CliTempFileRegistry.register(newFile("first.txt"))
        assertTrue(
            CliTempFileRegistry.isHookRegisteredForTests(),
            "The first temp file must arm the hook — otherwise a clean Burp quit mid-call leaks the " +
                "file, which is the one case the net exists for.",
        )

        CliTempFileRegistry.register(newFile("second.txt"))
        assertTrue(
            CliTempFileRegistry.isHookRegisteredForTests(),
            "A second registration must reuse the same hook. Registering once per call would rebuild " +
                "the accumulation defect in a coarser form (threat T-24-07).",
        )

        CliTempFileRegistry.shutdown()
        assertFalse(
            CliTempFileRegistry.isHookRegisteredForTests(),
            "shutdown() must leave no hook behind: an unremoved hook is an extension-classloader " +
                "object and pins a dead classloader across extension reloads (D-03, threat T-24-07).",
        )

        CliTempFileRegistry.shutdown()
        assertFalse(
            CliTempFileRegistry.isHookRegisteredForTests(),
            "shutdown() must be idempotent — App.shutdown() can run more than once across a reload.",
        )
    }

    private companion object {
        const val SIMULATED_CALL_COUNT = 10
    }
}
