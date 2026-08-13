package com.six2dez.burp.aiagent.redact

import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException

// ReDoS-safe regex utility.
//
// The JDK Matcher has no built-in timeout (JDK-8234713 "Won't fix"). This object bounds any
// single regex match to ~50 ms by wrapping the input in a DeadlineCharSequence whose get()
// throws RegexTimeoutException once System.nanoTime() exceeds the deadline. The Matcher reads
// input characters via CharSequence.get() during backtracking, so the deadline is observed
// promptly even under catastrophic backtracking.
//
// Reference: https://www.ocpsoft.org/regex/how-to-interrupt-a-long-running-infinite-java-regular-expression/
// [CITED — interruptible-CharSequence idiom, adapted to use a nanoTime deadline instead of
//  Thread.interrupted() so it works without requiring external thread management.]
//
// Design decisions mirrored from SecretCipher.kt:
//   - fail-soft: never throw into the redaction pipeline; return a safe fallback.
//   - no ExecutorService: avoids orphaned threads in Burp's long-lived JVM process.
//   - AWT-free: no java.awt / javax.swing imports so Phase 15's scanner-side tripwire can reuse
//     this file headless.

// Thrown by DeadlineCharSequence.get() when the match deadline is exceeded.
internal class RegexTimeoutException : RuntimeException()

// Wraps a CharSequence so that each get() call checks a nanoTime deadline before returning the
// character. The Matcher's inner backtracking loop calls CharSequence.get() (charAt) on every
// character access, so the deadline is observed promptly even under catastrophic backtracking.
private class DeadlineCharSequence(
    private val inner: CharSequence,
    private val deadlineNanos: Long,
) : CharSequence {
    override val length: Int get() = inner.length

    override fun get(index: Int): Char {
        if (System.nanoTime() > deadlineNanos) throw RegexTimeoutException()
        return inner[index]
    }

    override fun subSequence(
        startIndex: Int,
        endIndex: Int,
    ): CharSequence = DeadlineCharSequence(inner.subSequence(startIndex, endIndex), deadlineNanos)

    override fun toString(): String = inner.toString()
}

object SafeRegex {
    /**
     * Maximum wall-clock time (in milliseconds) allowed for a single regex match or probe.
     * Corresponds to the "50 ms per-pattern timeout" described in PRIV-02 / SC3.
     */
    const val DEFAULT_TIMEOUT_MS = 50L

    /**
     * Outcome of a bounded replacement (PRIV-06 / D-14).
     *
     * [timedOut] is the ONLY reliable signal that the pattern did not complete. [text] equals the
     * original input in BOTH the "the pattern matched nothing" case and the "the pattern never
     * finished" case, so a caller that inspects [text] alone cannot tell those two apart. A caller
     * that must fail closed — a body-redaction window whose unscanned bytes must never reach a
     * backend — has to branch on [timedOut], never on whether [text] changed.
     *
     * WR-03: THERE IS DELIBERATELY NO UN-REPORTING FAÇADE, and this note exists so the next person
     * who wants one finds the reason here instead of re-adding it.
     *
     * The DELETED function was `replaceAllSafe` — a `String`-returning one-line delegate that sat
     * beside [replaceAllSafeReporting] and returned exactly this [text] (the name is spelled out on
     * this line on purpose, so that grepping for it lands on its obituary). It ended Phase 21 with
     * ZERO production callers while its own KDoc named its hazard — "fail-open", "conflates
     * 'matched nothing' with 'timed out'" — i.e. a public fail-open replacement entry point inside
     * the redaction package, one autocomplete away from the next contributor who adds a rule. It was
     * removed (maintainer scope decision, 2026-08-12) rather than deprecated, because a `String`
     * return type structurally CANNOT carry [timedOut] and D-02 requires every body rule to fail
     * CLOSED on a timeout: a deprecated-but-callable fail-open helper is still a fail-open helper.
     * The shape that replaces it is explicit and one line longer:
     * `val r = replaceAllSafeReporting(...); if (r.timedOut) <fail closed> else r.text`.
     */
    data class SafeReplaceResult(
        val text: String,
        val timedOut: Boolean,
    )

    /**
     * Replaces all matches of [pattern] in [input] with [replacement], bounding the match to
     * [timeoutMs] milliseconds, and reports whether the match ran to completion (PRIV-06 / D-14).
     *
     * This is the ONLY replacement entry point in the redaction package (WR-03).
     *
     * On timeout [SafeReplaceResult.text] is the ORIGINAL [input] — fail-soft on the TEXT, so the
     * redaction pipeline never hangs and never corrupts content on account of a slow pattern — but
     * [SafeReplaceResult.timedOut] is true, which is what lets a caller drop unscanned content
     * instead of silently passing it through. See [SafeReplaceResult] for why the flag, not the
     * text, is the signal, and for why no `String`-returning convenience wrapper exists.
     */
    fun replaceAllSafeReporting(
        input: String,
        pattern: Pattern,
        replacement: String,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
    ): SafeReplaceResult =
        try {
            val deadline = System.nanoTime() + timeoutMs * 1_000_000L
            val matcher = pattern.matcher(DeadlineCharSequence(input, deadline))
            SafeReplaceResult(matcher.replaceAll(replacement), false)
        } catch (_: RegexTimeoutException) {
            // Fail-soft on the text as before, but report the timeout so the caller can fail closed.
            SafeReplaceResult(input, true)
        }

    /**
     * Returns true if [regex] compiles successfully AND finishes matching the adversarial probe
     * within [timeoutMs] milliseconds.
     *
     * Returns false if:
     *   - the regex fails to compile (PatternSyntaxException), or
     *   - the match against the adversarial probe times out (RegexTimeoutException).
     *
     * Used by the custom-pattern save-validation path (PrivacyConfigPanel) per SC3.
     */
    fun isPatternSafe(
        regex: String,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
    ): Boolean =
        try {
            val compiled = Pattern.compile(regex) // syntax check — throws PatternSyntaxException on bad regex
            // WR-01: reject patterns that can match the empty (zero-width) string, e.g. a*, \d*,
            // [0-9]*, \s*, x?, (foo)?, .*. Matcher.replaceAll advances past zero-width matches one
            // character at a time, inserting the replacement between EVERY character — corrupting
            // and bloating the outbound context (a 44-char body explodes to ~490 chars). Fail-safe
            // for secrecy, but a foreseeable footgun for non-expert regex users, so reject it up
            // front and surface a distinct rejection message in the save path.
            if (compiled.matcher("").find()) {
                false
            } else {
                val deadline = System.nanoTime() + timeoutMs * 1_000_000L
                compiled.matcher(DeadlineCharSequence(ADVERSARIAL_PROBE, deadline)).find()
                true
            }
        } catch (_: PatternSyntaxException) {
            false
        } catch (_: RegexTimeoutException) {
            false
        }

    // Catastrophic-backtracking probe for isPatternSafe.
    // On JDK 21 the classic (a+)+$ pattern requires ~2 000+ characters before the timeout
    // fires within the 50 ms budget (JDK 21 has improved its NFA engine for shorter inputs).
    // Using 2 000 'a' characters followed by '!' reliably triggers the 50 ms deadline for
    // truly pathological patterns while benign patterns (\d+, [A-Z]+, etc.) complete in
    // microseconds on the same probe.
    private val ADVERSARIAL_PROBE: String = "a".repeat(2_000) + "!"
}
