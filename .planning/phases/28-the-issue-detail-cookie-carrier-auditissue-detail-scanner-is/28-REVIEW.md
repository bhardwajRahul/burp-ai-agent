---
phase: 28-the-issue-detail-cookie-carrier-auditissue-detail-scanner-is
reviewed: 2026-08-27T00:00:00Z
depth: standard
files_reviewed: 10
files_reviewed_list:
  - src/main/kotlin/com/six2dez/burp/aiagent/redact/Redaction.kt
  - src/main/kotlin/com/six2dez/burp/aiagent/scanner/ActiveAiScanner.kt
  - src/main/kotlin/com/six2dez/burp/aiagent/scanner/InjectionPointExtractor.kt
  - src/main/kotlin/com/six2dez/burp/aiagent/scanner/ScannerIssueSupport.kt
  - src/test/kotlin/com/six2dez/burp/aiagent/mcp/tools/ParameterCarrierRedactionTest.kt
  - src/test/kotlin/com/six2dez/burp/aiagent/redact/CookieCarrierInventoryTest.kt
  - src/test/kotlin/com/six2dez/burp/aiagent/redact/CookieHeaderRuleOwnershipTest.kt
  - src/test/kotlin/com/six2dez/burp/aiagent/scanner/CookieRouteDispositionTest.kt
  - src/test/kotlin/com/six2dez/burp/aiagent/scanner/EvidenceTailReachTest.kt
  - src/test/kotlin/com/six2dez/burp/aiagent/scanner/IssueDetailCookieCarrierTest.kt
findings:
  critical: 2
  warning: 8
  info: 2
  total: 12
status: issues_found
---

# Phase 28: Code Review Report

**Reviewed:** 2026-08-27
**Depth:** standard
**Files Reviewed:** 10
**Status:** issues_found

## Summary

The control that plan 28-01 built is, in isolation, correct: `ScannerIssueSupport.sanitizeInjectionPointValue`
is type-keyed on the closed `InjectionType` enum, reads the live policy at the write site
(`ActiveAiScanner.kt:1242`), preserves the pre-existing truncation on the pass-through branch, and is
held by a genuinely non-vacuous probe. The 28-02 identity swap in `InjectionPointExtractor.kt:37` is
behaviour-preserving: Montoya's `HttpParameterType` enum names are uppercase and whitespace-free, so
`Redaction.isCookieParameterType` accepts exactly the set the old `== "COOKIE"` accepted, and no arm
was dropped or added. `./gradlew test` on the six touched test classes, `ktlintCheck` and `detekt` all
pass locally.

The problem is the phase's *scope claim*, not its mechanism. **PRIV-05 is not closed.** A raw session
cookie still reaches `AuditIssue.detail()` — and therefore the `scanner_issues` MCP emission — under
STRICT and BALANCED by **two** routes the phase did not measure:

1. The `Payload Used:` line **in the very block the control guards**. SQLI context-aware payloads are
   built by interpolating `InjectionPoint.originalValue` (`PayloadGenerator.kt:762-791`), so a COOKIE
   point produces payloads that literally contain the cookie value, and `ScannerIssueSupport.kt:121`
   writes them verbatim. Phase 27's own measured fixture shows this
   (`27-08-SUMMARY.md:297`: `Original Value: <sentinel>` **and** `Payload Used: <sentinel>' AND '1'='1`)
   — the phase stripped one occurrence out of the same string and declared the route controlled.
2. `AiScanCheck.buildDetail` (`AiScanCheck.kt:353`), a second active-scan issue-detail producer
   registered `PER_INSERTION_POINT` at `App.kt:215`, writes `insertionPoint.baseValue()` unredacted for
   `PARAM_COOKIE` insertion points and never calls the new control.

Both routes are falsifications of claims the phase committed to source: `ScannerIssueSupport.kt:74`
("THE ONLY PRODUCER OF THE ACTIVE-SCAN ISSUE DETAIL LINES IN THE REPOSITORY"), `ScannerIssueSupport.kt:32-33`
("the payload is agent-authored, not operator traffic") and `CookieCarrierInventoryTest.kt:406`
("TWO CONSUMERS, BOTH READ, AND BOTH NOW CONTROLLED"). The registers were updated to say the route is
closed while it is not — the exact "record wider than the control it describes" failure the inventory
file's own KDoc names as the defect this work exists to repair.

The tests are otherwise high quality and demonstrably non-vacuous (mutation results are recorded and
match what the assertions can see), but one advertised tripwire — the repository-wide "single-producer
gate" — does not exist, and one new source-text pin will go red on the correct fix for CR-01.

## Critical Issues

### CR-01: The `Payload Used:` line re-leaks the raw cookie value the control just stripped

**File:** `src/main/kotlin/com/six2dez/burp/aiagent/scanner/ScannerIssueSupport.kt:121` (with
`src/main/kotlin/com/six2dez/burp/aiagent/scanner/ActiveAiScanner.kt:511-515`,
`src/main/kotlin/com/six2dez/burp/aiagent/scanner/PayloadGenerator.kt:752-791`)

**Issue:**
`buildActiveIssueDetailLines` sanitises `Original Value:` and then, two lines later, writes
`"  Payload Used: ${payload.value.take(PAYLOAD_VALUE_MAX_CHARS)}"` with no control at all. For a
COOKIE-typed injection point the payload is *derived from the cookie value*:

- `ActiveAiScanner.executeScan` calls `payloadGenerator.generateContextAwarePayloads(vulnClass, target.injectionPoint.originalValue, 5)` (`:511-515`).
- `PayloadGenerator.generateContextAwarePayloads` routes `VulnClass.SQLI` to `generateSqliPayloads`
  (`:656`), which builds `"$originalValue AND 1=1"`, `"$originalValue' AND '1'='1"`, … (`:762-791`).
- If any such payload confirms, `confirmation.payload.value` — containing the raw cookie value —
  lands in the detail blob, is copied into `IssueDetails.detail` by `Serialization.kt`, and is emitted
  by the `scanner_issues` MCP tool. No downstream rule keys on it: it is not `Cookie: …`, not
  `name=value (COOKIE)`, and the blob carries no newline after `IssueUtils.formatIssueDetailHtml`.

Reachability is the *default automated* path, not a corner case: `PassiveAiScannerFinding.kt:233-245`
extracts injection points (COOKIE included) and queues them into the active scanner, and
`ActiveAiScanner.manualScan` (`:232-241`) pairs every extracted point with every requested class and
is callable over MCP (`McpToolExecutorImpl.kt:1000`). Phase 27 already *printed* this leak in its own
probe output (`27-08-SUMMARY.md:297`) and nobody read the second occurrence.

The KDoc at `ScannerIssueSupport.kt:26-35` states the opposite as settled fact — "This constant is NOT
part of the privacy control: the payload is agent-authored, not operator traffic" — which is how the
line got skipped. It is false for every context-aware payload.

**Fix:** control the payload line on the same type key, and correct the KDoc.

```kotlin
internal const val PAYLOAD_ECHO_MARKER = "[STRIPPED]"

/**
 * (PRIV-05) — the payload line is NOT unconditionally agent-authored. Context-aware payloads are
 * built by interpolating InjectionPoint.originalValue (PayloadGenerator.kt:762-791), so a
 * COOKIE-typed point yields payloads that contain the operator's session cookie verbatim.
 */
internal fun sanitizeRenderedPayload(
    point: InjectionPoint,
    payload: Payload,
    policy: RedactionPolicy,
): String =
    when {
        policy.stripCookies &&
            point.type == InjectionType.COOKIE &&
            point.originalValue.isNotEmpty() &&
            payload.value.contains(point.originalValue) ->
            payload.value.replace(point.originalValue, INJECTION_VALUE_STRIPPED_MARKER).take(PAYLOAD_VALUE_MAX_CHARS)
        else -> payload.value.take(PAYLOAD_VALUE_MAX_CHARS)
    }
```

and at line 121:

```kotlin
detailLines.add("  Payload Used: ${sanitizeRenderedPayload(point, payload, policy)}")
```

Add the red probe to `IssueDetailCookieCarrierTest` by making `PAYLOAD` echo the sentinel
(`value = "$DETAIL_SENTINEL' AND '1'='1"`) — today that fixture uses a payload
(`"benign-probe-payload"`) that is structurally incapable of exposing this leak, which is why 14 green
tests say nothing about it.

---

### CR-02: `AiScanCheck` is a second active-scan issue-detail producer that bypasses the control entirely

**File:** `src/main/kotlin/com/six2dez/burp/aiagent/scanner/AiScanCheck.kt:353` (registered at
`src/main/kotlin/com/six2dez/burp/aiagent/App.kt:214-215`)

**Issue:**
`ScannerIssueSupport.kt:74-80` asserts it is "THE ONLY PRODUCER OF THE ACTIVE-SCAN ISSUE DETAIL LINES
IN THE REPOSITORY" and that a second producer would be caught by a gate. It is not the only producer.
`AiScanCheck.buildDetail` builds its own detail string:

```kotlin
**Insertion Point:** ${insertionPoint.name()} (${insertionPoint.type()})
**Original Value:** ${insertionPoint.baseValue().take(100)}
```

and hands it to `AuditIssue.auditIssue(...)` at `:248`. `AiScanCheck` is registered with
`ScanCheckType.PER_INSERTION_POINT`, so Burp Pro drives it for every insertion point Burp derives —
including `AuditInsertionPointType.PARAM_COOKIE`, whose `baseValue()` is the raw cookie value. The
issue is added to the site map and emitted by `scanner_issues` exactly like the `ActiveAiScanner` one.
It reads no privacy mode at all: the leak is identical in STRICT, BALANCED and OFF.

This route is invisible to every guard the phase shipped: `CookieCarrierInventoryTest` keys on Montoya
*cookie-byte accessors* and `AuditInsertionPoint.baseValue()` is not one; `CookieRouteDispositionTest`
keys on parameter-*type* spellings and this site compares nothing; `IssueDetailCookieCarrierTest`
never leaves `ScannerIssueSupport`. Nothing in `.planning/phases/28-*/` mentions `AiScanCheck`.

**Fix:** route this producer through a type-keyed control of its own — `AuditInsertionPointType` is a
closed enum, so the same discipline applies — and delete or soften the "only producer" claim.

```kotlin
// AiScanCheck.kt
private fun sanitizedBaseValue(insertionPoint: AuditInsertionPoint): String {
    val policy = RedactionPolicy.fromMode(getSettings().privacyMode)
    return if (policy.stripCookies && insertionPoint.type() == AuditInsertionPointType.PARAM_COOKIE) {
        ScannerIssueSupport.INJECTION_VALUE_STRIPPED_MARKER
    } else {
        insertionPoint.baseValue().take(ScannerIssueSupport.ORIGINAL_VALUE_MAX_CHARS)
    }
}
```

then `**Original Value:** ${sanitizedBaseValue(insertionPoint)}`. Cover it with a sibling of
`IssueDetailCookieCarrierTest` and add `AiScanCheck.kt` to the `CookieCarrierInventoryTest` registry
(it is currently absent from every map because `baseValue()` is not in `COOKIE_BYTE_ACCESSORS`).

## Warnings

### WR-01: The "single-producer gate" the control's KDoc relies on does not exist

**File:** `src/main/kotlin/com/six2dez/burp/aiagent/scanner/ScannerIssueSupport.kt:76-80`

**Issue:** "`IssueDetailCookieCarrierTest`'s single-producer gate fails if a detail-line accumulator
reappears inline in `ActiveAiScanner`." No such gate exists. The only "SINGLE PRODUCER" assertion is
inside `IssueDetailCookieCarrierTest.originalValueRenderedFor` (`:626-632`), which counts
`Original Value: ` lines **in the list `buildActiveIssueDetailLines` itself returned** — it cannot see
another file, another function, or an inline accumulator anywhere. The only source-text pin in the
file counts `RedactionPolicy.fromMode(` occurrences (`:262-285`). CR-02 is the live proof the claimed
gate would have caught nothing.

**Fix:** either implement the gate or delete the claim. A real gate is cheap:

```kotlin
@Test
fun noDetailLineAccumulatorExistsOutsideScannerIssueSupport() {
    val offenders = mainSourceFiles()
        .filterNot { relativePath(it).endsWith("scanner/ScannerIssueSupport.kt") }
        .filter { file -> codeLines(file).any { it.contains("Original Value:") } }
        .map { relativePath(it) }
    assertEquals(emptyList<String>(), offenders, "a second issue-detail producer bypasses sanitizeInjectionPointValue")
}
```

(That test is red today — it finds `scanner/AiScanCheck.kt`.)

---

### WR-02: The carrier registry now records a closure that has not happened

**File:** `src/test/kotlin/com/six2dez/burp/aiagent/redact/CookieCarrierInventoryTest.kt:406-421` and
`:584-602`

**Issue:** The `INJECTION_EXTRACTOR / PARAMETER_LIST` entry was moved from `CLASSIFIED_NON_CARRYING`
to `ROUTED_THROUGH` with the text "TWO CONSUMERS, BOTH READ, AND BOTH NOW CONTROLLED", and
`ISSUE_DETAIL_CARRIER_DISPOSITION` was superseded with "THE ROUTE IS NOW CONTROLLED". Both are
inaccurate: there is a **third** consumer of `InjectionPoint.originalValue` —
`payloadGenerator.generateContextAwarePayloads` at `ActiveAiScanner.kt:513` and `:709` — whose output
re-enters the same `AuditIssue.detail()` blob uncontrolled (CR-01). Note also that map membership is
pure prose: `everyCookieByteCarrierSiteIsRoutedOrClassified` only asserts `ROUTED_THROUGH ∪
CLASSIFIED_NON_CARRYING == measured` and non-overlap, so moving an entry between the two maps is
untested by construction — the accuracy of the "routed" claim rests entirely on the author.

**Fix:** after CR-01 lands, enumerate the third consumer in the entry and name the control that covers
it; until then the entry should stay classified with the residual named, per the file's own
"supersession, never deletion" discipline.

---

### WR-03: The audit log writes the cookie-derived payload to disk unredacted

**File:** `src/main/kotlin/com/six2dez/burp/aiagent/scanner/ActiveAiScanner.kt:1218`

**Issue:** `audit.logEvent("active_scan_confirmed", mapOf(... "payload" to confirmation.payload.value.take(100) ...))`
and `AuditLogger.logEvent` (`AuditLogger.kt:60-68`) serialises the payload map **raw** alongside its
SHA-256. For a COOKIE point with a context-aware payload (CR-01) that writes a live session cookie to
the audit file. `CLAUDE.md` states the audit contract as "hashes only unless verbose is on"; this call
path honours only the `enabled` flag, never a verbose flag. Pre-existing line, but it is in a reviewed
file and it is the same value CR-01 concerns.

**Fix:** apply the same sanitiser to the audited payload, or gate the raw `payload` key behind the
verbose flag and keep `payloadSha256` for the non-verbose case.

---

### WR-04: `theWriteSiteReadsTheLivePolicy` will go red on the correct fix and invite being relaxed

**File:** `src/test/kotlin/com/six2dez/burp/aiagent/scanner/IssueDetailCookieCarrierTest.kt:262-285`

**Issue:** The pin asserts **exactly one** executable `RedactionPolicy.fromMode(` in
`ActiveAiScanner.kt`. Any legitimate second policy lookup in that file — for example the one CR-02's
sibling fix or a second write site would need — turns it red for a reason that is not a defect. A
guard that is red on arrival for a specification reason is precisely the shape this file's own KDoc
(`:326-339`) says caused the round-4 regression, because the cheapest repair is to widen it.

**Fix:** assert the *property* rather than the count — every `RedactionPolicy.fromMode(` in the file
derives from the live setting:

```kotlin
assertTrue(fromModeLines.isNotEmpty(), "PIN: no policy lookup found at all")
fromModeLines.forEach {
    assertTrue(it.contains("RedactionPolicy.fromMode(getSettings().privacyMode)"), "…")
}
```

---

### WR-05: `CookieRouteDispositionTest.relativePath` breaks on Windows, a supported platform

**File:** `src/test/kotlin/com/six2dez/burp/aiagent/scanner/CookieRouteDispositionTest.kt:286`

**Issue:**
`file.absolutePath.substringAfter("$MAIN_SOURCE_ROOT${File.separator}")` mixes a forward-slash
constant (`"src/main/kotlin"`) with the platform separator. On Windows the needle
`src/main/kotlin\` never occurs in `C:\…\src\main\kotlin\…`, `substringAfter` returns the *whole
absolute path*, and `assertEquals(OWNER, hits.single().first)` fails with a confusing message. The
project explicitly targets Windows, and the sibling file written in the same requirement thread
already does this correctly (`CookieCarrierInventoryTest.kt:238`).

**Fix:**

```kotlin
private fun relativePath(file: File): String = file.relativeTo(mainSourceRoot()).invariantSeparatorsPath
```

---

### WR-06: The adaptive-prompt harness leaks global state on failure and depends on argument position

**File:** `src/test/kotlin/com/six2dez/burp/aiagent/scanner/CookieRouteDispositionTest.kt:201-240`

**Issue:** Two defects in one helper:
- `ScanKnowledgeBase` is a process-wide singleton. It is cleared at `:202` and again at `:235`, but
  the trailing `clear()` is not in a `finally`. Any failing assertion (`:205`), any throw from
  `generateAdaptivePayloads`, or the `requireNotNull` at `:236` leaves the seeded host in the shared
  knowledge base for every later test in the JVM.
- `:219` casts `invocation.arguments[6]` to `(Throwable?) -> Unit` by **position**. It is correct today
  (`AgentSupervisor.send`'s 7th parameter is `onComplete`), but inserting any parameter before it turns
  a clear compile-time contract into a runtime `ClassCastException` inside a Mockito answer.

**Fix:** wrap the body in `try { … } finally { ScanKnowledgeBase.clear() }`, and locate the callback
defensively — `invocation.arguments.filterIsInstance<Function1<*, *>>().last()`, or stub `send` with a
typed `whenever(...).thenAnswer` against the named parameters instead of a `defaultAnswer`.

---

### WR-07: Dead insertion-point path, documented as live, whose HEADER branch is an unguarded cookie carrier

**File:** `src/main/kotlin/com/six2dez/burp/aiagent/scanner/InjectionPointExtractor.kt:138-256`
(with `src/main/kotlin/com/six2dez/burp/aiagent/scanner/ActiveAiScanner.kt:273-300`)

**Issue:** `matchInsertionPoint` has **no caller in `src/main/kotlin`** — only tests — and neither does
`ActiveAiScanner.manualScanInsertionPoint`. Both KDocs assert they are "Used by the 'AI Scan on
Selected Insertion Point' right-click action"; `App.kt`'s `ContextMenuItemsProvider` provides no such
item, and `UiActions` never references either symbol. That matters beyond dead weight: `:194` treats an
empty `headerAllowlist` (the parameter default, `:164`) as *allow every header*, so wiring this action
up would produce `InjectionPoint(HEADER, "Cookie", "<raw cookie header value>")` whenever a user
selects bytes on the Cookie line — and `sanitizeInjectionPointValue`'s `else` branch passes
HEADER-typed values through verbatim in every mode. Today only the fixed
`ScannerUtils.HEADER_INJECTION_ALLOWLIST` (host/origin/referer/x-forwarded-*) is used, so it is latent,
not live.

**Fix:** either delete both functions and their tests, or wire the menu item and, in the same change,
make `matchInsertionPoint` reject non-allowlisted headers when the allowlist is empty (fail closed) and
extend `sanitizeInjectionPointValue` to strip HEADER-typed points whose name satisfies
`Redaction.isCookieHeaderName(point.name)`.

---

### WR-08: `EvidenceTailReachTest`'s tripwire cannot see a new site that uses a named cap

**File:** `src/test/kotlin/com/six2dez/burp/aiagent/scanner/EvidenceTailReachTest.kt:236-251`

**Issue:** `EVIDENCE_CONSTRUCTION = Regex("""match\.value\.take\((\d+)\)""")` matches only a **decimal
literal**. The KDoc claims "The site-count assertion above is what turns a future divergence red", but
a fourth construction site written as `match.value.take(EVIDENCE_TAIL_MAX)` — the shape this very
phase encouraged by naming `ORIGINAL_VALUE_MAX_CHARS` under QUAL-07 — is not counted, the site count
stays 3, and the tripwire stays green while AR-28-01's recorded reach silently grows. The tripwire is
directionally sound (removing/renaming an existing cap does turn it red); the claim is stronger than
the mechanism.

**Fix:** widen to `Regex("""match\.value\.take\(([A-Za-z0-9_.]+)\)""")`, keep the numeric multiset
assertion for the literal captures, and fail explicitly when a capture is non-numeric so a named cap
must be resolved by hand rather than skipped.

## Info

### IN-01: `fallbackStringField` ends in an unreachable `return ""`

**File:** `src/test/kotlin/com/six2dez/burp/aiagent/scanner/IssueDetailCookieCarrierTest.kt:693-698`

**Issue:** `assertTrue(false, …)` always throws, so the trailing `return ""` is dead. Harmless, but
`fail(...)` states the intent directly and removes the dead line.

**Fix:** `fail("EXTRACTOR: the `$key` field's opening quote was found but its closing quote was not.")`.

### IN-02: `sentinelsAreDistinctAndNonOverlapping` duplicates the `@BeforeEach` guard

**File:** `src/test/kotlin/com/six2dez/burp/aiagent/scanner/IssueDetailCookieCarrierTest.kt:118-123`

**Issue:** The same helper runs in `@BeforeEach` (`:115`) and as a test. Deliberate and documented
(visibility in the test report), so noted rather than flagged — but it does mean the class's "14 tests"
count includes one that asserts over two constants.

---

_Reviewed: 2026-08-27_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
