package com.six2dez.burp.aiagent.redact

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.regex.Pattern

// PRIV-02 / SC3: unit tests for the SafeRegex interruptible-CharSequence ReDoS guard.
// All tests run headless (no AWT) and must complete well under the CI timeout budget.
class SafeRegexTest {
    // PRIV-02 / SC3: a catastrophically-backtracking pattern should be rejected within the
    // timeout budget and the call must return within ~200 ms wall-clock.
    @Test
    fun catastrophicPatternIsRejectedWithinBudget() {
        val start = System.currentTimeMillis()
        val safe = SafeRegex.isPatternSafe("(a+)+\$")
        val elapsed = System.currentTimeMillis() - start

        assertFalse(safe, "Catastrophic pattern (a+)+\$ must return false")
        assertTrue(elapsed < 200L, "isPatternSafe must return within 200 ms; took $elapsed ms")
    }

    // PRIV-02 / SC3: a benign pattern must be accepted.
    @Test
    fun benignPatternIsAccepted() {
        assertTrue(SafeRegex.isPatternSafe("\\d+"), "Benign pattern \\d+ must be accepted")
    }

    // PRIV-02 / SC3 / WR-03: THE TEXT HALF of the bounded-replacement contract — on timeout the
    // returned text is the ORIGINAL input, unchanged and uncorrupted, and the call does not hang.
    //
    // This assertion used to run against the deleted replaceAllSafe façade. WR-03 removed that
    // façade (see SafeReplaceResult's KDoc); the fail-soft TEXT behaviour it pinned is unchanged and
    // is still a real, separately-stated guarantee, so the assertion moved onto
    // replaceAllSafeReporting(...).text rather than being dropped. D-14's clause that
    // "SafeRegexTest:44 stays green unchanged" described plan 21-02's scope, and is superseded by
    // the maintainer's 2026-08-12 decision to close WR-03 — the behaviour survives, the façade does
    // not.
    @Test
    fun catastrophicPatternTimesOutAndReturnsInput() {
        // 2 000 'a' characters followed by '!' — on JDK 21 this reliably triggers the 50 ms
        // deadline for pathological patterns like (a+)+$ anchored at the end. The shorter
        // 64-char probe is handled by JDK 21's improved NFA engine without catastrophic blowup.
        val input = "a".repeat(2_000) + "!"
        val pattern = Pattern.compile("(a+)+\$")

        val start = System.currentTimeMillis()
        val result = SafeRegex.replaceAllSafeReporting(input, pattern, "[REDACTED]").text
        val elapsed = System.currentTimeMillis() - start

        assertEquals(
            input,
            result,
            "On timeout replaceAllSafeReporting(...).text must be the original input unchanged (fail-soft on the text)",
        )
        assertTrue(elapsed < 200L, "replaceAllSafeReporting must return within 200 ms; took $elapsed ms")
    }

    // PRIV-06 / D-14: the pair of tests is deliberate, and stays a pair after WR-03.
    // catastrophicPatternTimesOutAndReturnsInput above pins the TEXT half ("what you get back is
    // safe"); this one pins the FLAG half ("you can tell that you got it back for the wrong
    // reason"), which is what makes fail-closed possible. The returned text is identical in both the
    // "no matches" and the "timed out" cases, so timedOut is the only way a body-redaction caller
    // can tell that a window was never fully scanned and must be dropped rather than sent. Merging
    // the two would lose exactly that distinction.
    @Test
    fun catastrophicPatternReportsTimedOut() {
        // Same input and pattern as catastrophicPatternTimesOutAndReturnsInput — 2 000 'a'
        // characters followed by '!' reliably trips the 50 ms deadline on JDK 21 for (a+)+$.
        val input = "a".repeat(2_000) + "!"
        val pattern = Pattern.compile("(a+)+\$")

        val start = System.currentTimeMillis()
        val result = SafeRegex.replaceAllSafeReporting(input, pattern, "[REDACTED]")
        val elapsed = System.currentTimeMillis() - start

        assertTrue(result.timedOut, "On timeout replaceAllSafeReporting must report timedOut = true")
        assertEquals(input, result.text, "On timeout replaceAllSafeReporting must still return the original input as text")
        assertTrue(elapsed < 200L, "replaceAllSafeReporting must return within 200 ms; took $elapsed ms")
    }

    // PRIV-06 / D-14: the counter-assertion — timedOut must be false for a pattern that completes,
    // otherwise a fail-closed caller would drop every window and the flag would prove nothing.
    @Test
    fun benignPatternReportsNotTimedOut() {
        val result = SafeRegex.replaceAllSafeReporting("abc123", Pattern.compile("\\d+"), "[REDACTED]")

        assertFalse(result.timedOut, "A pattern that completes must report timedOut = false")
        assertEquals("abc[REDACTED]", result.text, "replaceAllSafeReporting must apply the replacement when it completes")
    }

    // WR-01: patterns that can match the empty (zero-width) string must be rejected. Otherwise
    // replaceAll would insert the replacement between every character, corrupting/bloating the
    // outbound context. Covers the common footguns: *, ?, and alternations with an empty branch.
    @Test
    fun emptyMatchingPatternsAreRejected() {
        val emptyMatchers = listOf("a*", "\\d*", "[0-9]*", "\\s*", "x?", "(foo)?", ".*", "(abc)*", "a|")
        for (p in emptyMatchers) {
            assertFalse(SafeRegex.isPatternSafe(p), "Empty-matching pattern must be rejected: $p")
        }
    }

    // WR-01: a pattern that requires at least one character (cannot match empty) must still pass.
    @Test
    fun nonEmptyMatchingPatternsStillAccepted() {
        val nonEmptyMatchers = listOf("\\bSECRET-\\d{4}\\b", "\\d+", "[A-Z]+", "INTERNAL-[A-Z0-9]{6}", "a+")
        for (p in nonEmptyMatchers) {
            assertTrue(SafeRegex.isPatternSafe(p), "Non-empty-matching pattern must be accepted: $p")
        }
    }

    // PRIV-02 / WR-03: the counter-assertion to catastrophicPatternTimesOutAndReturnsInput — a
    // benign pattern must actually APPLY its replacement. Without it, "returns the input unchanged"
    // would be satisfiable by a function that never replaces anything at all. Moved onto
    // replaceAllSafeReporting(...).text when WR-03 deleted the un-reporting façade; the guarantee is
    // unchanged.
    @Test
    fun benignReplaceAppliesReplacement() {
        val result =
            SafeRegex
                .replaceAllSafeReporting(
                    "id=12345",
                    Pattern.compile("\\d+"),
                    "[REDACTED]",
                ).text
        assertEquals("id=[REDACTED]", result)
    }
}
