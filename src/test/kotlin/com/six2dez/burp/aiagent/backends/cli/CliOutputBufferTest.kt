package com.six2dez.burp.aiagent.backends.cli

import com.six2dez.burp.aiagent.config.Defaults
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertTimeoutPreemptively
import java.time.Duration
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * REL-07 / SC3 / SC4 — the behavioural acceptance suite for [CliOutputBuffer].
 *
 * **Naming constraint (hard).** `build.gradle.kts` excludes `*IntegrationTest`, `*ConcurrencyTest`,
 * `*BackpressureTest`, `*RestartPolicyTest` and `*SupervisionTest` under `-PexcludeHeavyTests=true`,
 * which is exactly what `.github/workflows/build.yml` passes. `CliOutputBufferTest` is the approved
 * name; `CliOutputConcurrencyTest` would have been the natural one for a suite that races eight
 * writers against two readers, and it would have made this suite silently stop running on the PR gate.
 *
 * **No test here spawns a subprocess and no test here compares an elapsed duration to a threshold.**
 * The buffer is drivable in pure JVM, so a real CLI process would add flake without adding evidence.
 * The concurrency test's failure mode is a deadlock or a lost thread, which
 * [assertTimeoutPreemptively] reports categorically.
 *
 * **The anti-corruption test is the one that matters most.** `CliBackend`'s success path reads the
 * FULL accumulated value and that value IS the model's answer, not a diagnostic tail; only the two
 * error paths take a 2000-character head. A cap set anywhere near that figure would silently corrupt
 * every CLI backend's output, and [aFiftyThousandCharacterAnswerRoundTripsByteIdentically] is what
 * makes that regression red.
 */
class CliOutputBufferTest {
    /**
     * REL-07-A (SC3) — eight writer threads appending while two reader threads snapshot.
     *
     * Asserts three things: nothing escapes from either side, every line in the final snapshot is
     * whole (a torn append would produce a fragment that is not in the expected set), and all 4000
     * lines survive. Start is coordinated with a [CountDownLatch] so the threads actually overlap.
     */
    @Test
    fun concurrentAppendsAndSnapshotReadsNeverTearALineOrThrow() {
        val buffer = CliOutputBuffer()
        val expected = (0 until WRITER_COUNT).flatMap { w -> (0 until LINES_PER_WRITER).map { i -> "w$w-line$i" } }
        val failures = CopyOnWriteArrayList<Throwable>()
        val start = CountDownLatch(1)
        val writersDone = CountDownLatch(WRITER_COUNT)
        val stopReaders = AtomicBoolean(false)

        val writers =
            (0 until WRITER_COUNT).map { w ->
                collectingThread("cli-output-buffer-writer-$w", failures) {
                    start.await()
                    try {
                        for (i in 0 until LINES_PER_WRITER) buffer.appendLine("w$w-line$i")
                    } finally {
                        writersDone.countDown()
                    }
                }
            }
        val readers =
            (0 until READER_COUNT).map { r ->
                collectingThread("cli-output-buffer-reader-$r", failures) {
                    start.await()
                    while (!stopReaders.get()) buffer.snapshot()
                    buffer.snapshot()
                }
            }

        assertTimeoutPreemptively(Duration.ofSeconds(30)) {
            (writers + readers).forEach { it.start() }
            start.countDown()
            writersDone.await()
            stopReaders.set(true)
            (writers + readers).forEach { it.join() }
        }

        assertTrue(
            failures.isEmpty(),
            "REL-07-A: no append and no concurrent snapshot may raise. Escaped: " +
                failures.joinToString { "${it::class.java.name}: ${it.message}" },
        )
        val lines = buffer.snapshot().split("\n").filter { it.isNotEmpty() }
        assertFalse(buffer.truncated, "REL-07-A: 4000 short lines sit far below the production cap; nothing should truncate.")
        assertEquals(
            WRITER_COUNT * LINES_PER_WRITER,
            lines.size,
            "REL-07-A: every appended line must appear exactly once. A different count means an append was " +
                "lost or a line was split across the unsynchronised boundary.",
        )
        assertEquals(
            expected.toSet(),
            lines.toSet(),
            "REL-07-A: every line in the snapshot must be whole. A fragment, or an interleaved pair of " +
                "half-lines, shows up here as a member that is not in the expected set.",
        )
    }

    /**
     * REL-07-C (SC4) — the anti-corruption assertion.
     *
     * A ~50 000-character multi-line answer appended at the PRODUCTION default cap must come back
     * byte-identically with `truncated == false`. This is the test that catches a cap set to the
     * 2000-character figure that belongs to `CliBackend`'s two error paths only.
     */
    @Test
    fun aFiftyThousandCharacterAnswerRoundTripsByteIdentically() {
        val lines = (0 until ANSWER_LINE_COUNT).map { "line $it: ${"the model answered with real prose ".repeat(2)}" }
        val answer = lines.joinToString(separator = "") { "$it\n" }
        assertTrue(
            answer.length >= MIN_LEGITIMATE_ANSWER_CHARS,
            "Fixture guard: the round-trip fixture must exceed 50 000 characters to be evidence at all; it is ${answer.length}.",
        )

        val buffer = CliOutputBuffer()
        lines.forEach { buffer.appendLine(it) }

        assertFalse(
            buffer.truncated,
            "REL-07-C: a ${answer.length}-character answer is a legitimate CLI response and must not be " +
                "truncated at the production cap.",
        )
        assertEquals(
            answer,
            buffer.snapshot(),
            "REL-07-C: the success path in CliBackend reads this value as the model's answer. A cap near " +
                "2000 characters, or an unconditional truncation marker, corrupts every CLI backend's output.",
        )
    }

    /**
     * REL-07-B (SC4) — appending past an injected cap retains the HEAD, flags truncation, and says so.
     *
     * Head retention is load-bearing: `String.take(n)` returns the FIRST n characters, so both
     * `CliBackend` error paths already show the head. A tail-retaining buffer would silently change
     * what `buildTimeoutMessage` reports.
     */
    @Test
    fun appendingPastTheCapRetainsTheHeadFlagsTruncationAndMarksTheSnapshot() {
        val buffer = CliOutputBuffer(maxChars = SMALL_CAP)
        val line = "0123456789"
        val everythingAppended = "$line\n".repeat(OVERFLOW_LINE_COUNT)
        repeat(OVERFLOW_LINE_COUNT) { buffer.appendLine(line) }

        assertTrue(
            buffer.truncated,
            "REL-07-B: ${everythingAppended.length} characters into a $SMALL_CAP-character cap must flag truncation.",
        )
        assertTrue(
            buffer.length <= SMALL_CAP,
            "REL-07-B: retention must never exceed the cap; it is ${buffer.length} against a cap of $SMALL_CAP.",
        )
        val retained = buffer.snapshot().removeSuffix(Defaults.CLI_OUTPUT_TRUNCATION_MARKER)
        assertTrue(
            everythingAppended.startsWith(retained),
            "REL-07-B: the retained content must be the HEAD of what was appended, not the tail.",
        )
        assertTrue(
            buffer.snapshot().endsWith(Defaults.CLI_OUTPUT_TRUNCATION_MARKER),
            "REL-07-B: when the cap is hit the caller must be able to tell, so the snapshot carries the marker.",
        )
    }

    /**
     * A-EDGE-4 (boundary) — one below the cap, exactly at it, and one character past it.
     *
     * [CliOutputBuffer.appendLine] appends the line plus one newline, so a line of `cap - 2`
     * characters lands exactly one below and a line of `cap - 1` lands exactly on the cap.
     */
    @Test
    fun theCapBoundaryBehavesAtOneBelowExactlyAtAndOneAbove() {
        val justUnderLine = "u".repeat(BOUNDARY_CAP - 2)
        val justUnder = CliOutputBuffer(maxChars = BOUNDARY_CAP)
        justUnder.appendLine(justUnderLine)
        assertEquals(BOUNDARY_CAP - 1, justUnder.length, "A-EDGE-4: one character below the cap is retained whole.")
        assertFalse(justUnder.truncated, "A-EDGE-4: one character below the cap is not truncation.")
        assertEquals("$justUnderLine\n", justUnder.snapshot(), "A-EDGE-4: no marker and no loss one below the cap.")

        val exactLine = "e".repeat(BOUNDARY_CAP - 1)
        val exactly = CliOutputBuffer(maxChars = BOUNDARY_CAP)
        exactly.appendLine(exactLine)
        assertEquals(BOUNDARY_CAP, exactly.length, "A-EDGE-4: exactly at the cap is retained whole.")
        assertFalse(exactly.truncated, "A-EDGE-4: reaching the cap exactly is not truncation — nothing was dropped.")
        assertEquals("$exactLine\n", exactly.snapshot(), "A-EDGE-4: no marker and no loss exactly at the cap.")

        exactly.appendLine("x")
        assertTrue(exactly.truncated, "A-EDGE-4: the first character past the cap flags truncation.")
        assertEquals(BOUNDARY_CAP, exactly.length, "A-EDGE-4: the first character past the cap is NOT retained.")
        assertEquals(
            "$exactLine\n${Defaults.CLI_OUTPUT_TRUNCATION_MARKER}",
            exactly.snapshot(),
            "A-EDGE-4: past the cap the retained head is unchanged and the marker is appended.",
        )
    }

    /** A-EDGE-4 (empty) — a buffer never appended to, and an append of the empty line. */
    @Test
    fun anUntouchedBufferIsEmptyAndAnEmptyLineAddsExactlyOneNewline() {
        val buffer = CliOutputBuffer()
        assertEquals("", buffer.snapshot(), "A-EDGE-4: a buffer never appended to snapshots as the empty string.")
        assertEquals(0, buffer.length, "A-EDGE-4: a buffer never appended to has length 0.")
        assertFalse(buffer.truncated, "A-EDGE-4: a buffer never appended to has truncated nothing.")

        buffer.appendLine("")
        assertEquals("\n", buffer.snapshot(), "A-EDGE-4: appending the empty line adds exactly one newline.")
        assertEquals(1, buffer.length, "A-EDGE-4: appending the empty line adds exactly one character.")
        assertFalse(buffer.truncated, "A-EDGE-4: appending the empty line truncates nothing.")
    }

    /**
     * A-EDGE-5 (precision) — a cut that would split a UTF-16 surrogate pair moves one character back.
     *
     * The cap counts UTF-16 chars, not code points, so without this rule a snapshot could end with an
     * unpaired high surrogate — a silent corruption of exactly the output SC4 exists to protect.
     */
    @Test
    fun aCutThatWouldSplitASurrogatePairMovesOneCharacterEarlier() {
        val gClef = "𝄞"
        val prefix = "abcdefghi"
        val buffer = CliOutputBuffer(maxChars = prefix.length + 1)
        buffer.appendLine("$prefix${gClef}trailing")

        assertTrue(buffer.truncated, "A-EDGE-5: the fixture must actually overflow, or the assertion is vacuous.")
        val retained = buffer.snapshot().removeSuffix(Defaults.CLI_OUTPUT_TRUNCATION_MARKER)
        assertEquals(
            prefix,
            retained,
            "A-EDGE-5: the cut lands between the two halves of the G clef, so it must move one character earlier.",
        )
        assertTrue(
            retained.none { it.isHighSurrogate() || it.isLowSurrogate() },
            "A-EDGE-5: the snapshot must never end with an unpaired surrogate. Retained: ${retained.map { it.code }}",
        )
    }

    /**
     * The truncation marker is conditional, not decorative.
     *
     * This is what keeps [aFiftyThousandCharacterAnswerRoundTripsByteIdentically]'s byte-identical
     * claim true: an unconditional marker would append itself to every legitimate answer.
     */
    @Test
    fun theTruncationMarkerIsAbsentWheneverNothingWasTruncated() {
        val buffer = CliOutputBuffer(maxChars = SMALL_CAP)
        buffer.appendLine("short")

        assertFalse(buffer.truncated, "Marker suppression fixture: five characters do not overflow a $SMALL_CAP-character cap.")
        assertFalse(
            buffer.snapshot().contains(Defaults.CLI_OUTPUT_TRUNCATION_MARKER),
            "An untruncated snapshot must not carry the marker, or every legitimate CLI answer gains a suffix.",
        )
        assertEquals("short\n", buffer.snapshot(), "An untruncated snapshot is exactly what was appended.")
    }

    // ---------------------------------------------------------------------------------------------
    // Fixture
    // ---------------------------------------------------------------------------------------------

    /**
     * A named, unstarted [Thread] whose uncaught throwables land in [sink].
     *
     * Collecting through an uncaught-exception handler rather than an in-body `catch` keeps the
     * assertion honest: a throw from inside [CliOutputBuffer] arrives here whether or not the test
     * body thought to wrap it.
     */
    private fun collectingThread(
        name: String,
        sink: MutableList<Throwable>,
        body: () -> Unit,
    ): Thread {
        val t = Thread(body, name)
        t.setUncaughtExceptionHandler { _, throwable -> sink.add(throwable) }
        return t
    }

    private companion object {
        const val WRITER_COUNT = 8
        const val LINES_PER_WRITER = 500
        const val READER_COUNT = 2
        const val ANSWER_LINE_COUNT = 1_000
        const val MIN_LEGITIMATE_ANSWER_CHARS = 50_000
        const val SMALL_CAP = 64
        const val OVERFLOW_LINE_COUNT = 20
        const val BOUNDARY_CAP = 128
    }
}
