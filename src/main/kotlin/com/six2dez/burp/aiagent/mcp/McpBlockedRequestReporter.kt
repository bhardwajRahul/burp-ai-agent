package com.six2dez.burp.aiagent.mcp

import com.six2dez.burp.aiagent.audit.AuditLogger
import com.six2dez.burp.aiagent.audit.Hashing
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * D-06 mandates the SHAPE (a flat map with a `reason` key), not a literal constant. It deliberately
 * does NOT reuse `mcp_tool_blocked`: that constant is a file-private `const` in
 * `mcp/tools/McpTool.kt` and unreachable from here, and its payload keys (`tool`, `toolType`,
 * `hasArgs`, `argsSha256`) have no transport-level meaning — emitting them with a different key set
 * would corrupt downstream analysis of tool telemetry.
 */
private const val MCP_TRANSPORT_EVENT_BLOCKED = "mcp_transport_blocked"

private const val MAX_HEADER_VALUE_LENGTH = 200

private const val BLOCK_LOG_WINDOW_MS = 60_000L

/**
 * C0 controls and DEL via `\p{Cntrl}`, plus the C1 range spelled out — Java's `\p{Cntrl}` covers only
 * `\x00-\x1F` and `\x7F` unless UNICODE_CHARACTER_CLASS is set, and D-07 names C0 *and* C1.
 */
private val controlCharRegex = Regex("[\\p{Cntrl}\\u0080-\\u009F]")

private val whitespaceRegex = Regex("\\s+")

/**
 * Turns a [GateDecision.Deny] into observability. Two sinks, per D-06: an audit event on EVERY
 * occurrence, and a Burp Output-tab line that is rate-limited per reason with an aggregate count
 * (D-09). The access-control decision itself stays pure — this class is invoked by the caller from
 * the `Deny` branch, never from inside `evaluate`.
 *
 * **Output sink is a lambda, not a `MontoyaApi`.** The caller passes
 * `{ line -> api.logging().logToOutput(line) }`; tests capture into a list. That keeps this class
 * testable without a Mockito deep stub.
 *
 * **D-10 / [verboseAudit].** `AgentSettings` has `auditEnabled` but no verbose-audit flag anywhere in
 * the repo, so [verboseAudit] is a constructor seam that callers wire to `false`. Hashing reflected
 * header values is therefore the effective default, which is what CLAUDE.md's "hashes only unless
 * verbose is on" requires. Adding a user-facing verbose toggle is not in SEC-04/SEC-05 and is not
 * done here. `method` and `path` are sanitized but not hashed: they are not reflected header values,
 * and a hashed request path would make a blocked route undiagnosable.
 *
 * **T-20-12, accepted residual risk, stated for the audit-ENABLED case.** `App.kt:69` registers the
 * `AuditLogger` global emitter unconditionally at startup — the `enabled` short-circuit lives one
 * level deeper, in `AuditLogger.logEvent`. So when the user has turned audit logging ON, D-06's
 * "an audit event on every block" means one synchronous `logFile.appendText` per blocked request on a
 * Netty event-loop thread, and a loopback request loop can drive one file append per request. D-09's
 * per-reason window deliberately covers only the Output line, because collapsing N blocks into one
 * event carrying a `suppressed` count would contradict D-06's locked "on every occurrence". Accepted
 * because the source is loopback-only and async hand-off has no in-repo precedent; it is not
 * defended by "audit is disabled by default" alone.
 */
internal class McpBlockedRequestReporter(
    private val logToOutput: (String) -> Unit,
    private val verboseAudit: Boolean = false,
) {
    private data class ReasonWindow(
        val lastLoggedAtMs: AtomicLong = AtomicLong(0L),
        val suppressedCount: AtomicLong = AtomicLong(0L),
    )

    private val windows: MutableMap<BlockReason, ReasonWindow> = ConcurrentHashMap()

    /**
     * The only entry point. Emits the audit event first (always), then attempts the rate-limited
     * Output line.
     *
     * [nowMs] is injected by the caller rather than read from the system clock inside this class — the
     * same convention as `PassiveAiScannerAnalysis.maybeLogBackoff(nowMs, untilMs)` — so the D-09
     * window is assertable without sleeping.
     */
    internal fun report(
        deny: GateDecision.Deny,
        externalMode: Boolean,
        nowMs: Long,
    ) {
        emitTransportTelemetry(MCP_TRANSPORT_EVENT_BLOCKED, buildPayload(deny, externalMode))
        maybeLogBlocked(deny, nowMs)
    }

    /**
     * Builds the D-06 audit payload.
     *
     * Deliberately ABSENT: the raw, attacker-controlled header values. Every value is D-07 sanitized,
     * and the four reflected header values are SHA-256 hashed unless [verboseAudit] is on (D-10), so
     * an attacker cannot use a blocked request to write chosen plaintext — a session cookie echoed in
     * a `Referer`, a bearer token misplaced into `Origin` — into `~/.burp-ai-agent/audit.jsonl`.
     * Also absent: any request body, any response detail, and the configured token itself.
     *
     * A null header stays null through both steps; it is never hashed into the SHA-256 of the empty
     * string, which would otherwise make "header absent" indistinguishable from "header empty".
     */
    private fun buildPayload(
        deny: GateDecision.Deny,
        externalMode: Boolean,
    ): Map<String, Any?> =
        linkedMapOf(
            "reason" to deny.reason.wireValue,
            "mode" to if (externalMode) "external" else "local",
            "method" to sanitize(deny.facts.method),
            "path" to sanitize(deny.facts.path),
            "origin" to auditValue(deny.facts.origin),
            "host" to auditValue(deny.facts.host),
            "referer" to auditValue(deny.facts.referer),
            "userAgent" to auditValue(deny.facts.userAgent),
        )

    /** D-10: sanitize, then hash unless the caller opted into plaintext. Null in, null out. */
    private fun auditValue(value: String?): String? = sanitize(value)?.let { if (verboseAudit) it else Hashing.sha256Hex(it) }

    private fun emitTransportTelemetry(
        type: String,
        payload: Map<String, Any?>,
    ) {
        AuditLogger.emitGlobal(type, payload)
    }

    /**
     * D-09 limiter. Lock-free read-then-CAS: this runs on Netty event-loop threads, so there is no
     * monitor lock and no scheduled executor anywhere in this class. Windows expire lazily on the next
     * block for the same reason rather than being swept by a cleaner thread.
     *
     * `0L` means "never logged", so the first block for a reason always prints — computing
     * `nowMs - Long.MIN_VALUE` would overflow instead.
     */
    private fun maybeLogBlocked(
        deny: GateDecision.Deny,
        nowMs: Long,
    ) {
        val window = windows.computeIfAbsent(deny.reason) { ReasonWindow() }
        val prev = window.lastLoggedAtMs.get()
        val windowElapsed = prev == 0L || nowMs - prev >= BLOCK_LOG_WINDOW_MS
        // Short-circuits so the CAS is only attempted when the window has actually elapsed. Losing
        // the CAS means a concurrent thread printed for this reason, so this block is a suppression.
        if (!windowElapsed || !window.lastLoggedAtMs.compareAndSet(prev, nowMs)) {
            window.suppressedCount.incrementAndGet()
        } else {
            val suppressed = window.suppressedCount.getAndSet(0L)
            logToOutput(if (suppressed > 0L) aggregateLine(deny, suppressed) else blockedLine(deny))
        }
    }

    private fun blockedLine(deny: GateDecision.Deny): String =
        "[McpAccessControl] Blocked MCP request: reason=${deny.reason.wireValue} " +
            "method=${outputValue(deny.facts.method)} path=${outputValue(deny.facts.path)} " +
            "origin=${outputValue(deny.facts.origin)} host=${outputValue(deny.facts.host)} " +
            "referer=${outputValue(deny.facts.referer)} ua=${outputValue(deny.facts.userAgent)}"

    private fun aggregateLine(
        deny: GateDecision.Deny,
        suppressed: Long,
    ): String = "[McpAccessControl] $suppressed further blocks for ${deny.reason.wireValue} in the last 60s"

    /** The Output tab carries sanitized plaintext, never a hash — it is where a human diagnoses. */
    private fun outputValue(value: String?): String = sanitize(value) ?: "none"

    /**
     * D-07: make an attacker-controlled value safe to write into a log line. Control characters are
     * REMOVED rather than replaced, so `"a\r\nInjected: line"` collapses to `"aInjected: line"` and a
     * forged second log line is impossible (CWE-117).
     */
    private fun sanitize(value: String?): String? =
        value?.let { raw ->
            val cleaned =
                controlCharRegex
                    .replace(raw, "")
                    .replace(whitespaceRegex, " ")
                    .trim()
            if (cleaned.length > MAX_HEADER_VALUE_LENGTH) cleaned.take(MAX_HEADER_VALUE_LENGTH).trimEnd() + "..." else cleaned
        }
}
