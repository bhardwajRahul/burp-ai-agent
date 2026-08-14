---
phase: 22-agent-tool-call-trust-boundary
reviewed: 2026-08-14T00:00:00Z
depth: standard
files_reviewed: 22
files_reviewed_list:
  - build.gradle.kts
  - DECISIONS.md
  - src/main/kotlin/com/six2dez/burp/aiagent/mcp/McpToolCatalog.kt
  - src/main/kotlin/com/six2dez/burp/aiagent/mcp/ToolApprovalGate.kt
  - src/main/kotlin/com/six2dez/burp/aiagent/mcp/ToolDecisionReporter.kt
  - src/main/kotlin/com/six2dez/burp/aiagent/mcp/tools/McpToolExecutorImpl.kt
  - src/main/kotlin/com/six2dez/burp/aiagent/ui/ChatPanel.kt
  - src/main/kotlin/com/six2dez/burp/aiagent/ui/components/ToolApprovalCard.kt
  - src/test/kotlin/com/six2dez/burp/aiagent/DecisionsAdrTest.kt
  - src/test/kotlin/com/six2dez/burp/aiagent/mcp/AiGateMcpToolTest.kt
  - src/test/kotlin/com/six2dez/burp/aiagent/mcp/McpToolCatalogTierParityTest.kt
  - src/test/kotlin/com/six2dez/burp/aiagent/mcp/SecTierResolutionTest.kt
  - src/test/kotlin/com/six2dez/burp/aiagent/mcp/ToolApprovalGateTest.kt
  - src/test/kotlin/com/six2dez/burp/aiagent/mcp/ToolDecisionReporterTest.kt
  - src/test/kotlin/com/six2dez/burp/aiagent/mcp/tools/McpToolParityTest.kt
  - src/test/kotlin/com/six2dez/burp/aiagent/mcp/tools/McpToolScopeEnforcementTest.kt
  - src/test/kotlin/com/six2dez/burp/aiagent/mcp/tools/ProxyHistoryListenerPortFilterTest.kt
  - src/test/kotlin/com/six2dez/burp/aiagent/ui/ChatPanelTestHarness.kt
  - src/test/kotlin/com/six2dez/burp/aiagent/ui/ChatPanelToolGateTest.kt
  - src/test/kotlin/com/six2dez/burp/aiagent/ui/McpToolTabModelTest.kt
  - src/test/kotlin/com/six2dez/burp/aiagent/ui/ToolApprovalCardTest.kt
findings:
  critical: 3
  warning: 11
  info: 3
  total: 17
status: issues_found
---

# Phase 22: Code Review Report

**Reviewed:** 2026-08-14
**Depth:** standard
**Files Reviewed:** 22
**Status:** issues_found

## Summary

SEC-06's *decision* core is sound. I traced every path from `ToolCallParser.extractFirst`
to `McpToolExecutor.executeTool` and found exactly three call sites, all origin-typed; there is no
model-driven path to Burp that skips `ToolApprovalGate.evaluate`. The five teardown paths all route
through the one `resolvePending` entry point and all emit an SC3 record. The budget is monotone and
terminates at nine turns for a fully denied chain (verified by hand against
`expectedTurnsForAFullyDeniedChain`). The tier partition is genuinely pinned: 19 AUTO + 14
CONFIRM_EACH are enumerated literals and the catalog size is asserted, so the 26 CONFIRM tools are
determined by arithmetic and any promotion is a red test. `./gradlew compileKotlin`, the seven new
suites, `detekt` and `ktlintCheck` all pass, and `detekt-baseline.xml` is unchanged.

The defects are in the two halves the phase claims most loudly and tested least: the **record** and
the **origin type**.

1. **The Output-tab decision line is forgeable by the model** (CR-01). `isKnownTool` returns `true`
   for any string starting with `ext:` — no validation against configured servers — and the
   `knownTool == true` branch of `outputToolName` skips `sanitizeInline` entirely. A model-emitted
   tool name `"ext:a\n[SEC-06] decision=approve_once …"` writes two lines into Burp's Output tab, the
   sink ADR-15 calls "the only one most users ever see". The reporter's own KDoc claims this is
   impossible; its CWE-117 test only exercises `knownTool = false`.
2. **The SC5 unforgeable-origin control does not exist** (CR-02). `ToolApprovalGate.approvedOrigin`
   is `internal`, not `private`. The file-private `ModelApproved` class buys nothing when its only
   factory is module-visible: any file in the main source set can mint a valid model origin and pass
   it to `internal fun executeTool` without ever calling `evaluate` or `resolve`. Four KDoc blocks
   and ADR-15 assert a compile-time property the compiler is not enforcing. It has zero external
   callers — one keyword fixes it.
3. **`argsSha256` digests a 120-character prefix** (CR-03). `auditValue` runs the args JSON through
   `sanitizeInline`, whose own KDoc says it is "the wrong tool for the args JSON", and whose default
   `maxLength` is 120. Two `http1_request` calls that differ only in the request body produce an
   identical digest. The args are, per D-07, "where exfiltration hides"; the audit record cannot tell
   them apart.

Beneath those: one latent dangling continuation on the click-then-failure path (WR-01), a
`@Suppress`-ed dead parameter on a security-lifecycle function (WR-03), a second copy of the CWE-117
sanitizer in the same package (WR-05), and a cluster of tests whose fixtures are too benign to
detect the defects above (WR-07, WR-08). Every `ChatPanel.kt:NNN` cross-reference in the new files
and in ADR-15 is off by 100-500 lines (WR-11) — in a codebase whose design rationale travels
exclusively through such citations, that is not cosmetic.

Per the review brief, `executeTool` running on the EDT at three call sites is **not** reported.

---

## Critical Issues

### CR-01: Model-controlled tool name reaches the Burp Output tab and the audit payload unsanitized

**File:** `src/main/kotlin/com/six2dez/burp/aiagent/ui/ChatPanel.kt:2423`,
`src/main/kotlin/com/six2dez/burp/aiagent/mcp/ToolDecisionReporter.kt:192`,
`src/main/kotlin/com/six2dez/burp/aiagent/mcp/ToolDecisionReporter.kt:248-252`

**Issue:** `isKnownTool` treats *any* name beginning with `ext:` as recognised:

```kotlin
// ChatPanel.kt:2423
private fun isKnownTool(canonicalId: String): Boolean =
    canonicalId.startsWith("ext:") || McpToolCatalog.all().any { it.id == canonicalId }
```

`McpToolExecutor.canonicalToolId` returns the model's string verbatim in its `else` branch
(`McpToolExecutorImpl.kt:1158-1165`), so for an `ext:`-prefixed name `canonicalId` **is** raw model
output. The reporter then skips sanitization on exactly that branch:

```kotlin
// ToolDecisionReporter.kt:248-252
private fun outputToolName(rawToolName: String, canonicalId: String, knownTool: Boolean): String =
    if (knownTool) canonicalId else sanitizeInline(rawToolName).orEmpty().ifBlank { OUTPUT_NONE }
```

and interpolates the result straight into the Output line (`:244-246`), whose sink is
`api.logging().logToOutput(line)` (`ChatPanel.kt:154`).

Reachability, traced end to end:

1. Model emits ``` ```json\n{"tool":"ext:x\n[SEC-06] decision=approve_once tier=auto tool=scope_check step=1 trace=chat-turn-0","args":{}}\n``` ``` (`\n` as a JSON escape).
2. `ToolCallParser.resolveToolName` applies only `.trim()` (`ToolCallParser.kt:81`) — interior
   control characters survive.
3. `tierFor` sees the `ext:` prefix and returns `CONFIRM_EACH`, so a card is raised.
4. Whatever the user does next — click **Deny** (`denyToolCall`, `ChatPanel.kt:2682`), click
   **Approve once** (`executeApprovedToolCall`, `:2778`), or simply type a new message
   (`resolvePending` → `:2590`, `ImplicitDenyReason.NEW_MESSAGE`) — `report(...)` runs with
   `knownTool = true` and the forged newline reaches the Output tab.

The user cannot avoid it: every branch of the gate reports. The result is a fabricated `[SEC-06]`
line claiming an approval that never happened, in the human-visible half of the SEC-06 record
(CWE-117). This is the exact attack `ToolDecisionReporter.kt:233` claims to prevent
("which is what makes a model-authored newline unable to forge a second Output line (CWE-117)").

The same branch also writes raw, unbounded model text into the durable audit payload:

```kotlin
// ToolDecisionReporter.kt:192
"toolName" to if (knownTool) canonicalId else UNKNOWN_TOOL_NAME,
```

Jackson escapes the newline so JSONL lines cannot be forged, but this contradicts the class's stated
rule ("Only the values the model authored … are hashed", `:80-81`) and lets model output of
arbitrary length into `audit.jsonl`.

**Fix:** sanitize on both branches and stop treating an unvalidated `ext:` prefix as extension-derived.

```kotlin
// ToolDecisionReporter.kt — never emit an unsanitized name, on any branch
private fun outputToolName(
    rawToolName: String,
    canonicalId: String,
    knownTool: Boolean,
): String = sanitizeInline(if (knownTool) canonicalId else rawToolName).orEmpty().ifBlank { OUTPUT_NONE }
```

```kotlin
// ChatPanel.kt — an ext: name is "known" only if the configured server actually exposes it.
// ExternalMcpClientManager.availableTools() already exists (McpToolExecutorImpl.kt:79).
private fun isKnownTool(canonicalId: String, context: McpToolContext): Boolean =
    McpToolCatalog.all().any { it.id == canonicalId } ||
        context.externalClientManager?.availableTools().orEmpty().any { it.name == canonicalId }
```

Add a regression test that is the mirror of the existing one — `outputLineIsSingleLineAndSanitized`
with `rawToolName = "ext:a\r\nInjected: line"` and `knownTool = true`.

---

### CR-02: `ToolApprovalGate.approvedOrigin` is `internal`, so the SC5 origin control is not enforced

**File:** `src/main/kotlin/com/six2dez/burp/aiagent/mcp/ToolApprovalGate.kt:337-340`

**Issue:** The phase's central structural claim is that a future parse-and-execute call site *cannot
compile* without going through the gate. `ToolApprovalGate.kt:14-19` argues at length that Kotlin's
`internal` is module-wide and therefore insufficient — and then declares the only factory for the
file-private type with exactly that modifier:

```kotlin
internal object ToolApprovalGate {
    internal fun approvedOrigin(          // <-- module-wide
        tier: SecTier,
        decision: ToolDecision,
    ): ToolCallOrigin = ModelApproved(tier, decision)
```

`McpToolExecutor.executeTool` is also `internal` (`McpToolExecutorImpl.kt:1045`). Any file in the
main source set can therefore write:

```kotlin
McpToolExecutor.executeTool(
    call.tool, call.argsJson, context,
    ToolApprovalGate.approvedOrigin(SecTier.AUTO, ToolDecision.AUTO),   // compiles today
)
```

and reach Burp with a model-chosen tool name, no card, no session-memory consultation and no audit
record. Making the *class* file-private buys nothing while the *factory* is module-visible.

This falsifies five separate written claims:
- `ToolApprovalGate.kt:332-334` — "The ONLY way to obtain one, and it exists only in this file."
- `ToolApprovalGate.kt:14-19` — "The only compile-time mechanism that binds minting to the gate is
  FILE-PRIVATE visibility."
- `McpToolExecutorImpl.kt:1026-1029` — "which is called only from `evaluate` and `resolve`. A caller
  therefore cannot obtain one without going through the decision." (a statement about today's
  callers, not an enforced property)
- `ChatPanel.kt:2737-2739`
- `DECISIONS.md` ADR-15, Decision paragraph

Secondary, same class: `ToolCallOrigin` is a `sealed interface` in package
`com.six2dez.burp.aiagent.mcp`. Kotlin permits direct implementations in the **same package and
module**, so a new file under `mcp/` can also declare its own `ToolCallOrigin` implementation. The
sealed-interface boundary is a package boundary, not a file boundary.

**Fix:** narrow the factory. `grep -rn approvedOrigin src/` shows four callers, all inside the object
itself (`:369`, `:383`, `:416`, `:419`); no test references it, so this is a pure narrowing:

```kotlin
    /** Mints the unforgeable model origin. Object-private: `evaluate` and `resolve` are the only callers. */
    private fun approvedOrigin(
        tier: SecTier,
        decision: ToolDecision,
    ): ToolCallOrigin = ModelApproved(tier, decision)
```

Then correct `ToolApprovalGate.kt:117-118`, `:332-334`, `McpToolExecutorImpl.kt:1026-1029`,
`ChatPanel.kt:2737-2739` and ADR-15 to state the property that actually ships: the origin is
unforgeable outside `ToolApprovalGate.kt` *and* outside package `mcp` — or, if the package boundary
is judged sufficient, say so explicitly rather than claiming file scope.

---

### CR-03: `argsSha256` digests only the first 120 sanitized characters of the arguments

**File:** `src/main/kotlin/com/six2dez/burp/aiagent/mcp/ToolDecisionReporter.kt:224`,
`src/main/kotlin/com/six2dez/burp/aiagent/mcp/ToolApprovalGate.kt:469-480`

**Issue:**

```kotlin
// ToolDecisionReporter.kt:224
private fun auditValue(value: String?): String? =
    sanitizeInline(value)?.let { if (verboseAudit) it else Hashing.sha256Hex(it) }
```

`sanitizeInline` defaults to `maxLength = INLINE_MAX_LENGTH = 120` (`ToolApprovalGate.kt:22, 471`)
and truncates with `cleaned.take(maxLength).trimEnd() + "…"`. So the value that is hashed — or, under
the verbose seam, written in plaintext — is at most 121 characters of a whitespace-collapsed,
control-character-stripped prefix.

Concretely, an approved `http1_request`:

```
{"targetHostname":"example.com","targetPort":443,"usesHttps":true,"content":"GET /x HTTP/1.1 Host: exampl…
```

Everything past character 120 — the method, path, headers and body that constitute the entire
security question — is discarded before the digest. Two calls that differ only in the request body
produce a byte-identical `argsSha256`. An attacker who wants an exfiltration request to be
indistinguishable in the audit trail from a benign one only has to keep the first 120 characters
constant, which the argument key order makes trivial.

`sanitizeInline`'s own KDoc says this outright (`ToolApprovalGate.kt:465-467`):

> It is the wrong tool for the args JSON: `\p{Cntrl}` includes `\n` and `\t`, so this would flatten
> JSON into one unreadable line — the opposite of D-07's rule that the full args are shown because
> the args are where exfiltration hides. Use `sanitizeBlock` there.

This defeats CLAUDE.md's "hashes only unless verbose is on" in both directions: the hash does not
identify the arguments, and verbose mode records only a mangled prefix of them.

**Fix:** hash the full value; sanitize only what is actually written as text.

```kotlin
/**
 * D-10: hash the WHOLE argument string, so the digest identifies the arguments rather than a prefix
 * of them. Only the verbose plaintext form goes through the inline sanitizer, because only that form
 * is ever rendered.
 */
private fun auditValue(value: String?): String? =
    value?.takeIf { it.isNotBlank() }?.let {
        if (verboseAudit) sanitizeBlock(it, maxChars = VERBOSE_ARGS_MAX_CHARS, maxLines = VERBOSE_ARGS_MAX_LINES)
        else Hashing.sha256Hex(it)
    }
```

Add a test that makes the defect visible — the current one cannot (see WR-07):

```kotlin
@Test
fun argsDigestDistinguishesPayloadsThatDifferPastTheInlineCap() {
    val prefix = """{"targetHostname":"example.com","targetPort":443,"usesHttps":true,"content":"""" + "x".repeat(80)
    report(argsJson = prefix + """benign"}""")
    report(argsJson = prefix + """malicious"}""")
    assertNotEquals(payloadAt(0)["argsSha256"], payloadAt(1)["argsSha256"])
}
```

---

## Warnings

### WR-01: A parked continuation is dropped when a user-approved tool call throws

**File:** `src/main/kotlin/com/six2dez/burp/aiagent/ui/ChatPanel.kt:2618-2653`, `:2758-2770`,
`:2836-2872`, `:1743-1758`

**Issue:** `dispatchResolvedToolCall` discards the `ToolCallOutcome` its two branches return:

```kotlin
when (resolved) {
    is ToolApprovalOutcome.Run -> executeApprovedToolCall(... onCompleted = pending.onCompleted ...)
    is ToolApprovalOutcome.Denied -> denyToolCall(...)
    ...
}
```

On the success path `executeApprovedToolCall` hands `onCompleted` into `sendMessage` (CHAINED), so
the continuation lives. On the failure path it returns early through `reportFailedToolCall`
(`:2758-2770`), which returns `NOT_CHAINED` **without invoking `onCompleted`**. In the un-parked
flow that is handled by the caller inside `sendMessage`'s completion block (`:759-764`), but
`dispatchResolvedToolCall` has no equivalent — so a click-approved tool that throws leaves
`pending.onCompleted` uncalled forever.

That directly contradicts `PendingToolDecision`'s KDoc (`:1746-1747`): "Every path out of the
callback either invokes it or hands it into `sendMessage`, so the 'Send to AI' launch path can never
be left hanging (T-22-31)."

Currently latent: `MainTab.openChatWithContext` — the only entry that supplies a non-null
`onCompleted` — has no callers, so every reachable `sendMessage` passes `null`. It becomes live the
moment that context-menu wiring lands. `runTool` catches `Exception` broadly, so the throwing path
requires an `Error` or a fault in `normalizeArgs` / telemetry — rare, but the `runCatching` and the
`reportFailedToolCall` branch exist precisely because the author expected it.

**Fix:** honour the outcome in the resolution path, exactly as the un-parked path does.

```kotlin
private fun dispatchResolvedToolCall(pending: PendingToolDecision, panel: SessionPanel, resolved: ToolApprovalOutcome) {
    val outcome = when (resolved) {
        is ToolApprovalOutcome.Run -> executeApprovedToolCall(...)
        is ToolApprovalOutcome.Denied -> denyToolCall(...)
        is ToolApprovalOutcome.Ask -> error("...")
    }
    // T-22-31: NOT_CHAINED here means the tool threw and no followup turn was sent, so this is the
    // only remaining chance to discharge the parked continuation.
    if (outcome == ToolCallOutcome.NOT_CHAINED) pending.onCompleted?.invoke(ToolApprovalGate.DENIAL_RESULT, null)
}
```

### WR-02: `resolveToolDecision` destroys the pending record before a call that can throw

**File:** `src/main/kotlin/com/six2dez/burp/aiagent/ui/ChatPanel.kt:2510-2516`

**Issue:**

```kotlin
val pending = pendingDecisions.remove(sessionId) ?: return   // record gone
val state = sessionStates.getOrPut(sessionId) { ToolSessionState() }
val resolved = ToolApprovalGate.resolve(pending.call.tool, state.approvalMemory, decision)  // can throw
pending.card.resolve(decision)                               // not reached on throw
```

`ToolApprovalGate.resolve` has two `require` blocks (`ToolApprovalGate.kt:404`, `:411`). If either
fires, the `IllegalArgumentException` escapes into the AWT event pump: the pending record is already
gone, the card keeps its live buttons, and the parked continuation is never discharged. A second
click then hits `?: return` and silently does nothing — a permanently dead card, the T-22-10 state
the lifecycle work exists to eliminate.

Not reachable today (the card only renders session buttons when `ask.offersSessionActions` is true,
and `tierFor` is deterministic), but the ordering is the wrong way round for a fail-closed path.

**Fix:** resolve the gate first and only then take the record, or wrap the body so the card and the
continuation are always retired:

```kotlin
val pending = pendingDecisions.remove(sessionId) ?: return
try {
    val resolved = ToolApprovalGate.resolve(pending.call.tool, state.approvalMemory, decision)
    pending.card.resolve(decision)
    ...
} catch (e: IllegalArgumentException) {
    // A caller bug must still not strand the decision: retire it as an implicit denial.
    pending.card.resolve(ToolDecision.IMPLICIT_DENY)
    pending.onCompleted?.invoke(ToolApprovalGate.DENIAL_RESULT, null)
    showError("Tool approval could not be applied: ${e.message}")
}
```

### WR-03: `resolvePending(sendFollowup = true)` compiles and silently does nothing

**File:** `src/main/kotlin/com/six2dez/burp/aiagent/ui/ChatPanel.kt:2571-2576`

**Issue:**

```kotlin
@Suppress("UnusedParameter")
private fun resolvePending(
    sessionId: String,
    reason: ImplicitDenyReason,
    sendFollowup: Boolean = false,
) {
```

`sendFollowup` is never read. The KDoc explains it is "inert by construction" and exists to document
intent — but a parameter is the wrong medium for a comment. A future caller writing
`resolvePending(id, reason, sendFollowup = true)` gets no followup, no warning and no compile error,
on the security path that decides whether a denied model is left hanging. The blanket
`@Suppress("UnusedParameter")` also covers any *future* parameter that becomes unused.

**Fix:** delete the parameter and keep the rationale as prose in the KDoc, which is where it already
is. If the seam is genuinely wanted later, add it with an implementation at the same time.

### WR-04: `isKnownTool`'s documented contract does not match its implementation

**File:** `src/main/kotlin/com/six2dez/burp/aiagent/ui/ChatPanel.kt:2414-2423`

**Issue:** The KDoc states the function "applies exactly the test `ToolApprovalGate.tierFor` applies:
a catalog entry, or an `ext:` name **belonging to a configured external server**". The implementation
checks only `canonicalId.startsWith("ext:")`. `ExternalMcpClientManager.availableTools()` exists and
is already consumed by `McpToolExecutor.describeTools` (`McpToolExecutorImpl.kt:78-89`), so the
documented check was implementable.

Consequences beyond CR-01: an `ext:` name that no configured server exposes is recorded as
`knownTool = true`, so the audit record files it as a recognised tool and drops `toolNameSha256`,
while the executor will return `"External MCP client not available"` or an invalid-name error. The
record therefore names a tool that never existed.

**Fix:** see CR-01's `isKnownTool` snippet. Whichever way it is resolved, the KDoc and the code must
agree.

### WR-05: The CWE-117 sanitizer now exists in two copies in the same package

**File:** `src/main/kotlin/com/six2dez/burp/aiagent/mcp/ToolApprovalGate.kt:29`, `:469-480` vs
`src/main/kotlin/com/six2dez/burp/aiagent/mcp/McpBlockedRequestReporter.kt:25`, `:225-233`

**Issue:** `sanitizeInline` is a line-for-line reimplementation of
`McpBlockedRequestReporter.sanitize` — same regex, same remove-then-collapse-then-trim-then-cap
sequence — differing only in the truncation marker (`…` vs `...`) and the cap constant. The comment
at `ToolApprovalGate.kt:27` says it was "Copied … rather than re-derived, so the two cannot drift",
which is precisely backwards: copying is the mechanism by which two implementations drift. They have
already drifted on the marker.

`sanitizeInline` is `internal` and top-level in the same package, so `McpBlockedRequestReporter`
could simply call it.

**Fix:** delete `McpBlockedRequestReporter.sanitize` and `controlCharRegex`; have `outputValue` call
`sanitizeInline(value, MAX_HEADER_VALUE_LENGTH) ?: "none"`. One implementation, one regex, one
truncation marker. If the two markers must differ, make the marker a parameter rather than a second
function.

### WR-06: The card installs Swing's HTML renderer on `JLabel`s, and the anti-spoofing sweep never sees it

**File:** `src/main/kotlin/com/six2dez/burp/aiagent/ui/components/ToolApprovalCard.kt:557`, `:862`,
`:865`; `src/test/kotlin/com/six2dez/burp/aiagent/ui/ToolApprovalCardTest.kt:91-121`

**Issue:** The card's stated primary anti-spoofing control is that no `JLabel` or `AbstractButton`
may ever have the HTML renderer installed, "asserted mechanically through
`getClientProperty("html")`". Two components break that rule with extension-authored text:

- `buildOutcomeRow` (`:557`): `JLabel(if (isCompact) "<html>${current.verb}" else current.verb)`
- `truncationFooter` (`helpLabel`, i.e. a `JLabel`) receives the `<html>`-prefixed strings from
  `footerTextFor` (`:862`, `:865`) whenever the args are truncated.

Both are safe *today* because the strings are extension-authored with integer-only interpolation, and
both are documented as deliberate. The problem is coverage:
`modelSuppliedTextNeverInstallsTheHtmlRenderer` builds only a **pending** card with a 17-character
args string, so `footerTextFor` returns `null` and the compact branch is never constructed. The
exhaustive sweep therefore never visits either component. A later edit that interpolates
`catalogTitle`, or the model's tool ID, into `footerTextFor` — the natural next change to a
truncation footer — lands in an HTML-rendering `JLabel` with no test to catch it.

**Fix:** extend the sweep to the two states it currently cannot reach, and pin the exemption
explicitly rather than by omission.

```kotlin
@Test
fun onlyTheTwoDocumentedExtensionAuthoredLabelsMayCarryTheHtmlRenderer() {
    val truncated = pendingCard(args = "{\"x\":\"" + "y".repeat(ARGS_PREVIEW_MAX_CHARS * 2) + "\"}")
    val compact = ToolApprovalCard.compact(ToolDecision.SESSION_DENIED, "Send HTTP/1.1 request", "http1_request", "{}")
    listOf(truncated, compact).forEach { card ->
        descendantsOf(card).filterIsInstance<JLabel>().forEach { label ->
            if (label.getClientProperty("html") != null) {
                assertTrue(
                    label.text.startsWith("<html>Showing the first ") || label.text.startsWith("<html>Blocked ") ||
                        label.text.startsWith("<html>Ran without asking"),
                    "A new HTML-rendering label appeared on the SEC-06 card: '${label.text}'.",
                )
            }
        }
    }
}
```

### WR-07: `ToolDecisionReporterTest` cannot detect CR-01 or CR-03

**File:** `src/test/kotlin/com/six2dez/burp/aiagent/mcp/ToolDecisionReporterTest.kt:24`, `:183-198`,
`:261-274`

**Issue:** Three assertions pass for the wrong reason.

- `ARGS_JSON` (`:24`) is 42 characters with no whitespace runs and no control characters, so
  `sanitizeInline` returns it **unchanged**. `argsAreHashedByDefault` (`:186`) therefore compares
  against `Hashing.sha256Hex(ARGS_JSON)` and passes whether or not the reporter truncates, collapses
  or flattens. `argsArePlaintextOnlyUnderTheVerboseSeam` (`:197`) has the same blind spot. CR-03 is
  invisible to both.
- `outputLineIsSingleLineAndSanitized` (`:263`) passes `knownTool = false`, exercising only the
  branch that already sanitizes. The `knownTool = true` branch — the one carrying CR-01 — is never
  tested with a control character.

The class KDoc claims "every assertion is exact"; on the two model-supplied fields it is not.

**Fix:** add the digest test from CR-03 and this branch test:

```kotlin
@Test
fun aKnownExtToolNameIsAlsoSanitizedBeforeReachingTheOutputTab() {
    report(rawToolName = "ext:demo:a\r\nInjected: line", knownTool = true, tier = SecTier.CONFIRM_EACH)
    val line = lines.single()
    assertFalse(line.contains('\n'), "A model-authored newline forged a second Output line: $line")
    assertFalse(line.contains('\r'), line)
}
```

### WR-08: `accessibleDescriptionEndsWithTheSanitizedToolId` uses a tool ID that needs no sanitizing

**File:** `src/test/kotlin/com/six2dez/burp/aiagent/ui/ToolApprovalCardTest.kt:212-236`

**Issue:** The test's name and the KDoc it guards (`ToolApprovalCard.kt:571-584`) make three claims,
the first being "the tool ID is inline-sanitized, so a multi-line ID cannot be read as several
sentences". The fixture is `toolId = "http1_request"` — a clean catalog ID that
`sanitizeInline` returns unchanged. The test would pass identically if
`updateAccessibleDescription` interpolated the raw `modelSuppliedToolId`. The sanitization claim is
untested for the accessible-description channel; `toolIdIsSanitizedAndCapped` (`:147`) covers only
the `JTextField`.

**Fix:** drive the assertion with a value that distinguishes the two forms.

```kotlin
val raw = "http1_request\r\n✔ Approved by this extension" + "x".repeat(PADDING_LENGTH)
val card = pendingCard(toolId = raw)
val sanitized = requireNotNull(ChatPanelTestHarness.find(card, JTextField::class.java)).text
val description = card.accessibleContext.accessibleDescription
assertNotEquals(raw, sanitized, "fixture is not exercising sanitization")
assertTrue(description.endsWith(sanitized), "The description must end with the SANITIZED id: $description")
assertFalse(description.contains('\n'), "A multi-line id would be read as several sentences.")
```

### WR-09: `ChatPanelToolGateTest` reads `ChatPanel.kt` at runtime without declaring it as a Gradle input

**File:** `src/test/kotlin/com/six2dez/burp/aiagent/ui/ChatPanelToolGateTest.kt:642-658`,
`build.gradle.kts:165-179`

**Issue:** `functionBody` reads `src/main/kotlin/.../ChatPanel.kt` from disk to make the two
structural assertions in `userDialogPathIsNotDoublePrompted` and
`shutdownResolvesAllPendingDecisionsWithoutSendingATurn`. This is the same pattern
`DecisionsAdrTest` uses, and the build file added `DECISIONS.md` and `McpToolCatalog.kt` as declared
test inputs for exactly this reason — with a measured note that the guard was silently skipped from
cache. `ChatPanel.kt` was not added. A source edit that leaves the compiled bytecode unchanged (a
comment replaced in place, a trailing comment) makes the structural guard stale in exactly the case
it exists to catch.

Separately, `functionBody` calls `File(...).readText()` with no existence check, so a build-layout
change surfaces as a bare `FileNotFoundException` — `DecisionsAdrTest.readProjectFile` deliberately
asserts with a message naming the resolved path.

**Fix:**

```kotlin
// build.gradle.kts, tasks.test — same rationale as adrRecord / secTierKdocSource above
inputs
    .file("src/main/kotlin/com/six2dez/burp/aiagent/ui/ChatPanel.kt")
    .withPropertyName("chatPanelStructuralSource")
    .withPathSensitivity(PathSensitivity.RELATIVE)
```

and mirror `DecisionsAdrTest`'s existence assertion inside `functionBody`.

### WR-10: The SC4 harness mutates EDT-confined state off the EDT

**File:** `src/test/kotlin/com/six2dez/burp/aiagent/ui/ChatPanelTestHarness.kt:178-192`,
`src/main/kotlin/com/six2dez/burp/aiagent/ui/ChatPanel.kt:1294-1298`

**Issue:**

```kotlin
input.text = text                                   // JUnit thread
SwingUtilities.invokeAndWait { send.doClick() }      // EDT
```

Assigning `input.text` fires the input area's `DocumentListener` (`ChatPanel.kt:472-480`) →
`syncDraftFromInput()` → writes `sessionDrafts`, a `@GuardedBy("EDT")` map, from the JUnit thread.
`syncDraftFromInput` has no `assertEdt()`, so `-ea` does not catch it. The harness that exists to
assert the production path is itself violating the confinement REL-01 established, which makes it a
source of order-dependent flakiness and a bad template for the later tests its own KDoc invites.

**Fix:** put the whole interaction on the EDT.

```kotlin
SwingUtilities.invokeAndWait {
    input.text = text
    send.doClick()
}
```

### WR-11: Every `ChatPanel.kt:NNN` cross-reference in the phase's files is stale

**File:** `src/main/kotlin/com/six2dez/burp/aiagent/mcp/ToolApprovalGate.kt:123`, `:128`, `:157`,
`:164`, `:281`, `:290`, `:439`, `:453`;
`src/main/kotlin/com/six2dez/burp/aiagent/ui/components/ToolApprovalCard.kt:137`, `:209`, `:392`,
`:405`, `:610`, `:923`; `src/test/kotlin/com/six2dez/burp/aiagent/mcp/ToolApprovalGateTest.kt:19`,
`:247`, `:293`; `DECISIONS.md` ADR-15

**Issue:** Verified against the merged file:

| Cited | Claim | Actual |
| --- | --- | --- |
| `ChatPanel.kt:1211` (`ToolApprovalGate.kt:281`, `ToolApprovalGateTest.kt:19`) | `MAX_AUTO_TOOL_ITERATIONS = 8` | `1307` — and no longer `private`, it is `internal` |
| `ChatPanel.kt:1495` (`ToolApprovalGate.kt:164`) | `restoreSessions()` builds a fresh state | `1600` |
| `ChatPanel.kt:1621` (`ToolApprovalGate.kt:157`) | `ToolSessionState` | `1760` |
| `ChatPanel.kt:2177` (`ToolApprovalGate.kt:290`, `ToolApprovalGateTest.kt:247`) | `status = "error"` derivation | `2772` |
| `ChatPanel.kt:2210` / `:2213` (`ToolApprovalGate.kt:439`, `:453`) | budget helpers | `2725` / `2728` |
| `ChatPanel.kt:928` / `:2105` (`ToolApprovalGate.kt:123`, `:128`, ADR-15) | dialog / slash-command call sites | `1010` / `2313` |
| `ChatPanel.kt:433` / `:960` / `:1701` / `:1788` (`ToolApprovalCard.kt:610`, `:209`, `:392`, `:405`, `:137`, `:923`) | focus-paint, focus restore, height cap, timestamp | `2047` / `1042` / `1871` / `1974` |

This codebase deliberately carries its design rationale in cross-file line citations — several
comments in this very phase instruct a future contributor to "read that test first" or "see
`ChatPanel.kt:NNN`". A citation set that is uniformly off by 100-500 lines converts the
documentation mechanism into a maze, and it will get worse with every subsequent edit.

**Fix:** replace line numbers with symbol names for anything inside this repository —
`ChatPanel.MAX_AUTO_TOOL_ITERATIONS`, `ChatPanel.restoreSessions`, `ChatPanel.executeApprovedToolCall`
— which survive refactoring. Where a line number is genuinely needed (`ToolApprovalGate.kt:145` for
`ModelApproved` is a fair example), pair it with the symbol so a stale number is self-correcting.
Sweep all the entries in the table above, including ADR-15's.

---

## Info

### IN-01: The two sinks can disagree on `implicitDenyReason`

**File:** `src/main/kotlin/com/six2dez/burp/aiagent/mcp/ToolDecisionReporter.kt:202-204`, `:152`

**Issue:** `put("implicitDenyReason", implicitDenyReason?.wireValue)` inserts a `null` value when the
decision is `IMPLICIT_DENY` and no reason is supplied. The audit payload then carries
`"implicitDenyReason": null` while the returned metadata map drops the key entirely (`:152`) — the
two records the class exists to keep identical differ. Not reachable today: `resolvePending` always
passes a non-null reason. **Fix:** insert the key only when the value is non-null, mirroring the
`argsSha256` guard on the line below.

### IN-02: The compact card builds six widgets it never adds

**File:** `src/main/kotlin/com/six2dez/burp/aiagent/ui/components/ToolApprovalCard.kt:290-331`,
`:461-475`, `:711-762`

**Issue:** `headingLabel`, `tierReasonLabel`, `repeatLabel`, `sessionScopeLabel`, `badge` and
`buttonRow` are constructed as field initialisers but the compact branch of `buildRows` returns
before adding any of them; `applyTheme()` still restyles all six on every theme switch. `badge` also
carries a hardcoded `SecTier.CONFIRM` that no compact row ever shows. **Fix:** make the six
`by lazy` or move their construction into the non-compact branch, and skip them in `applyTheme` when
`isCompact`.

### IN-03: `DecisionsAdrTest` will fail the next phase in an unrelated file

**File:** `src/test/kotlin/com/six2dez/burp/aiagent/DecisionsAdrTest.kt:52-57`

**Issue:** `adr15ExistsAndIsTheHighestNumberedAdr` asserts `DECISIONS.md` contains no `## ADR-16`.
The KDoc says this is deliberate — a tripwire so the next author notices they must extend the guard.
Recorded here so the next phase's red test is recognised as designed rather than as a regression;
consider generalising the assertion to "the highest ADR number is also guarded" so it self-maintains
instead of failing by construction.

---

_Reviewed: 2026-08-14_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
