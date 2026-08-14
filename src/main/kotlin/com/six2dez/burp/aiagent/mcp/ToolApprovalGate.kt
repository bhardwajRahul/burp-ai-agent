package com.six2dez.burp.aiagent.mcp

import com.six2dez.burp.aiagent.mcp.tools.McpToolExecutor

// SEC-06 / ADR-15: the pure decision half of the tool-call trust boundary.
//
// Everything in this file is `internal` rather than `private` so ToolApprovalGateTest and
// SecTierResolutionTest can reach it without reflection — the same test-seam convention used by
// redact/Redaction.kt:215-228 and mcp/McpAccessControlDecision.kt:10-11.
//
// No Swing type and no AWT type is imported here — the import list above is the whole proof, and it
// is what keeps every SEC-06 decision provable in a unit test with no Swing harness and no display.
//
// ToolCallOrigin and ToolApprovalGate live in ONE file on purpose, against the two-file sketch in
// 22-PATTERNS.md, and the reason is load-bearing: Kotlin's `internal` is MODULE-WIDE (the whole main
// source set), so an `internal` factory would let any file in the module mint a model origin. The only
// compile-time mechanism that binds minting to the gate is FILE-PRIVATE visibility, and that requires
// the origin implementation and the gate to be declared in the same file. Splitting this file back
// into two would silently downgrade the SC5 control to a comment.

/** Cap for [sanitizeInline]. A named constant because MagicNumber is active and QUAL-07 forbids growing detekt-baseline.xml. */
private const val INLINE_MAX_LENGTH = 120

/**
 * C0 controls and DEL via `\p{Cntrl}`, plus the C1 range spelled out — Java's `\p{Cntrl}` covers only
 * `\x00-\x1F` and `\x7F` unless UNICODE_CHARACTER_CLASS is set. Copied from
 * mcp/McpBlockedRequestReporter.kt:25 rather than re-derived, so the two cannot drift.
 */
private val inlineControlCharRegex = Regex("[\\p{Cntrl}\\u0080-\\u009F]")

/**
 * The same character set MINUS `\n` and `\t`, which the block form must preserve. `&&` is Java's
 * character-class intersection; the C1 range is a separate alternative because it is not part of
 * `\p{Cntrl}` at all.
 */
private val blockControlCharRegex = Regex("[\\p{Cntrl}&&[^\\n\\t]]|[\\u0080-\\u009F]")

/** Collapses runs of whitespace for the inline form only. The block form must keep its line structure. */
private val inlineWhitespaceRegex = Regex("\\s+")

/**
 * What was decided about one model-emitted tool call. The wire string is defined once, here, and
 * nowhere else — both the SC3 audit payload and the approval card read [wireValue], so the two sinks
 * cannot disagree (the BlockReason convention from mcp/McpAccessControlDecision.kt:62-75).
 *
 * The pair that matters to an auditor is [APPROVE_ONCE] versus [SESSION_APPROVED]. [APPROVE_ONCE]
 * means a human clicked *for this specific call*; [SESSION_APPROVED] means an earlier click was
 * applied to this call without asking anyone. Collapsing the two into a single "approved" token would
 * make "did a human authorise this particular invocation?" unanswerable from the log — which is the
 * one question an audit trail exists to answer. The same split applies to [DENY] and [SESSION_DENIED].
 */
internal enum class ToolDecision(
    val wireValue: String,
) {
    /** A human clicked approve for this call, and only this call. */
    APPROVE_ONCE("approve_once"),

    /** A human clicked approve and extended it to this tool for the rest of the session (CONFIRM only). */
    APPROVE_SESSION("approve_session"),

    /** A human clicked deny for this call. */
    DENY("deny"),

    /** A human clicked deny and extended it to this tool for the rest of the session (CONFIRM only). */
    DENY_SESSION("deny_session"),

    /** The tool's tier is [SecTier.AUTO]; it ran with no user decision at all (D-02). */
    AUTO("auto"),

    /** An EARLIER click was applied to this call. No human saw this invocation. */
    SESSION_APPROVED("session_approved"),

    /** An earlier denial was applied to this call. No human saw this invocation. */
    SESSION_DENIED("session_denied"),

    /** No human ever answered: the card was destroyed by a teardown path. See [ImplicitDenyReason]. */
    IMPLICIT_DENY("implicit_deny"),
}

/**
 * Why a pending decision was resolved as [ToolDecision.IMPLICIT_DENY] without anyone clicking.
 *
 * Research measured **five** teardown paths that destroy a pending card, not the three D-08 lists;
 * plan 22-08 wires all five. The enum is declared here, before the wiring exists, so the audit shape
 * is fixed once rather than grown one string at a time as each path is discovered.
 */
internal enum class ImplicitDenyReason(
    val wireValue: String,
) {
    /** The user typed a new message instead of answering (D-08). */
    NEW_MESSAGE("new_message"),

    /** The chat session holding the card was deleted (D-08). */
    SESSION_DELETED("session_deleted"),

    /** The current chat was cleared, which destroys the card. Not in D-08's list. */
    CHAT_CLEARED("chat_cleared"),

    /** The Burp project changed, clearing in-memory session state. Not in D-08's list. */
    PROJECT_CHANGED("project_changed"),

    /** The extension was unloaded (D-08). */
    UNLOAD("unload"),
}

/**
 * Where a tool invocation came from, as a type the compiler checks rather than a comment.
 *
 * SC5, and the property is worth stating in plain English: **a hypothetical fourth parse-and-execute
 * call site cannot compile without obtaining a model origin from [ToolApprovalGate], which means going
 * through the decision.** A plain enum would let that call site write `origin = MODEL` and silently
 * reopen SEC-06 — the maintainer's standard is that a control a future edit can silently undo is not a
 * control.
 *
 * The two user-originated variants are public because a user who picked the tool and typed the args
 * themselves has already authorised it; there is nothing for the gate to decide. The model-originated
 * variant is deliberately NOT declared here — it is a file-private class, unnameable and
 * unconstructible outside this file, and reachable only through [ToolApprovalGate.approvedOrigin].
 */
internal sealed interface ToolCallOrigin {
    val wireValue: String

    /** The user picked the tool and typed the args in ToolInvocationDialog (ChatPanel.kt:928). */
    data object UserDialog : ToolCallOrigin {
        override val wireValue: String = "user_dialog"
    }

    /** The user typed the `/tool <name> <json>` slash command (ChatPanel.kt:2105). */
    data object UserSlashCommand : ToolCallOrigin {
        override val wireValue: String = "user_slash_command"
    }
}

/**
 * Parsed from model output AND authorised by the SEC-06 gate.
 *
 * Top-level `private`, which in Kotlin means FILE-private: no other file in the module can construct
 * this class or even name its type. That is the whole SC5 mechanism (T-22-11). It carries the proof of
 * the decision that produced it — the resolved [tier] and the [decision] a human (or the AUTO rule)
 * reached — so an audit record can be written from the origin alone.
 *
 * `internal` would NOT work here: Kotlin's `internal` is module-wide, so every file in the main source
 * set could mint one. Do not widen this to `internal` and do not move it to its own file.
 */
private class ModelApproved(
    val tier: SecTier,
    val decision: ToolDecision,
) : ToolCallOrigin {
    override val wireValue: String = "model_approved"
}

/**
 * The pure SEC-06 decision core.
 *
 * Pure by contract, in the shape of mcp/McpAccessControlDecision.kt:103-114: no logging, no audit
 * events, no I/O, no side effects of any kind. Audit emission is a separate call the ChatPanel caller
 * makes from the resolved branch (plan 22-05's ToolDecisionReporter) — never from inside this object.
 * That separation is what makes every row of the tier matrix assertable with no Swing harness.
 *
 * **Phase 20 D-09 aggregation-based rate limiting is deliberately NOT applied here**, and saying so is
 * better than leaving the omission unexamined. The flood vector D-09 answers was a remote,
 * unauthenticated peer able to generate blocks at will. Here the caller is the local model loop, whose
 * ceiling is `MAX_AUTO_TOOL_ITERATIONS = 8` per chain (ChatPanel.kt:1211) and whose counter D-13 makes
 * monotone. Eight cards per chain does not need coalescing, and coalescing them would hide exactly the
 * repetition a user needs to see.
 */
internal object ToolApprovalGate {
    /**
     * Resolves the SEC-06 tier for a model-supplied tool name. Total: every input resolves, and no
     * input throws.
     *
     * Canonicalisation comes FIRST and comes from [McpToolExecutor.canonicalToolId] — the executor's
     * own function, called rather than copied. That ordering reproduces `executeToolResult` exactly:
     * canonicalise, then test the external namespace, then look the catalog up. If this gate grew its
     * own alias table, an aliased call would be labelled "unknown tool" on the card while the executor
     * ran it as a known one, and the audit record would name a tool that never executed (T-22-12).
     */
    internal fun tierFor(rawToolName: String): SecTier {
        val canonical = McpToolExecutor.canonicalToolId(rawToolName)
        // D-04: DERIVED from the namespace prefix, never declared per tool. An external server's tools
        // are not in the catalog and are untrusted by ADR-11, so every one of them prompts every time —
        // including `ext:<server>:scope_check`, which must not inherit the built-in's AUTO tier.
        if (canonical.startsWith("ext:")) return SecTier.CONFIRM_EACH
        val descriptor = McpToolCatalog.all().firstOrNull { it.id == canonical }
        // Fail closed. D-03 removes the AUTHORING default; this is the RUNTIME fallback it does not
        // cover. An unrecognised name must never resolve to AUTO.
        return descriptor?.secTier ?: SecTier.CONFIRM_EACH
    }

    /**
     * Mints the unforgeable model origin. The ONLY way to obtain one, and it exists only in this file.
     *
     * Returns the [ToolCallOrigin] interface, never the implementing type, so callers pass the value on
     * without ever being able to name — or reconstruct — what they are holding.
     */
    internal fun approvedOrigin(
        tier: SecTier,
        decision: ToolDecision,
    ): ToolCallOrigin = ModelApproved(tier, decision)
}

/**
 * Makes a model-supplied value safe to render on one line and to write into an audit payload.
 *
 * Control characters are **REMOVED, never replaced**, so `"a\r\nInjected: line"` collapses to
 * `"aInjected: line"` and a forged second log line is impossible (CWE-117,
 * mcp/McpBlockedRequestReporter.kt:220-233). Stripping ESC also neuters ANSI sequences.
 *
 * Applied to the model-supplied TOOL ID on the card, in the accessible description and in the audit
 * payload. It is the wrong tool for the args JSON: `\p{Cntrl}` includes `\n` and `\t`, so this would
 * flatten JSON into one unreadable line — the opposite of D-07's rule that the full args are shown
 * because the args are where exfiltration hides. Use [sanitizeBlock] there.
 */
internal fun sanitizeInline(
    value: String?,
    maxLength: Int = INLINE_MAX_LENGTH,
): String? =
    value?.let { raw ->
        val cleaned =
            inlineControlCharRegex
                .replace(raw, "")
                .replace(inlineWhitespaceRegex, " ")
                .trim()
        if (cleaned.length > maxLength) cleaned.take(maxLength).trimEnd() + "…" else cleaned
    }

/**
 * Makes a model-supplied value safe to render as a multi-line block, preserving `\n` and `\t`.
 *
 * Same CWE-117 rule as [sanitizeInline] — control characters are REMOVED, never replaced — but `\n`
 * and `\t` survive, because this is what renders the args JSON in the card's expandable preview and
 * unreadable args defeat the point of showing them. Truncates by whichever of [maxChars] and
 * [maxLines] bites first, appending `…` so a truncated preview never looks complete.
 *
 * It is the wrong tool for a tool ID: a name like `"scope_check\n\n✔ Approved"` would occupy several
 * lines and imitate an outcome row. Use [sanitizeInline] there.
 */
internal fun sanitizeBlock(
    value: String?,
    maxChars: Int,
    maxLines: Int,
): String {
    val cleaned = blockControlCharRegex.replace(value.orEmpty(), "")
    val lines = cleaned.lines()
    val lineTruncated = lines.size > maxLines
    val lineCapped = lines.take(maxLines).joinToString("\n")
    val charTruncated = lineCapped.length > maxChars
    val capped = if (charTruncated) lineCapped.take(maxChars) else lineCapped
    return if (lineTruncated || charTruncated) "$capped…" else capped
}
