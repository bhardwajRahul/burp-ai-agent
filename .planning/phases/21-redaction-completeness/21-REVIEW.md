---
phase: 21-redaction-completeness
reviewed: 2026-08-11T15:38:03Z
depth: standard
files_reviewed: 14
files_reviewed_list:
  - src/main/kotlin/com/six2dez/burp/aiagent/App.kt
  - src/main/kotlin/com/six2dez/burp/aiagent/config/Defaults.kt
  - src/main/kotlin/com/six2dez/burp/aiagent/mcp/McpToolContext.kt
  - src/main/kotlin/com/six2dez/burp/aiagent/redact/Redaction.kt
  - src/main/kotlin/com/six2dez/burp/aiagent/redact/SafeRegex.kt
  - src/main/kotlin/com/six2dez/burp/aiagent/scanner/PassiveAiScannerAnalysis.kt
  - src/main/kotlin/com/six2dez/burp/aiagent/scanner/PassiveAiScannerPrompts.kt
  - src/main/kotlin/com/six2dez/burp/aiagent/ui/ChatPanel.kt
  - src/main/kotlin/com/six2dez/burp/aiagent/ui/components/ContextPreviewDialog.kt
  - src/main/kotlin/com/six2dez/burp/aiagent/ui/components/PrivacyPill.kt
  - src/main/kotlin/com/six2dez/burp/aiagent/ui/SettingsPanelActions.kt
  - src/test/kotlin/com/six2dez/burp/aiagent/redact/RedactionTest.kt
  - src/test/kotlin/com/six2dez/burp/aiagent/redact/SafeRegexTest.kt
  - src/test/kotlin/com/six2dez/burp/aiagent/scanner/PassiveAiScannerPromptRedactionTest.kt
findings:
  critical: 4
  warning: 7
  info: 0
  total: 11
status: issues_found
---

# Phase 21: Code Review Report

**Reviewed:** 2026-08-11T15:38:03Z
**Depth:** standard
**Files Reviewed:** 14
**Status:** issues_found

## Summary

Phase 21 closes PRIV-05 and PRIV-06 as specified: the cookie-section and COOKIE-typed-parameter
rules exist, the body stage no longer skips over-cap input, and every body rule now branches on
`SafeReplaceResult.timedOut` rather than on `replaceAllSafe`'s conflated return value. The
fail-open assignment the phase set out to eliminate is genuinely gone — I traced every call site
of `SafeRegex` in `src/main` and `replaceAllSafe` has **zero** production callers.

That is where the good news stops. Four defects were reproduced with standalone JDK 21 probes
that transcribe the shipped code verbatim (`redactCookieSections`, `windowEnd`, `splitPoint`,
`SENSITIVE_KEY_EXPR`, `formBodyParamRegex`, `jsonSecretKeyRegex`):

1. **The cookie-section span collapses to zero length on a blank cookie entry**, so every cookie
   value in the section reaches the AI backend unredacted — the exact PRIV-05 leak, reachable
   through a `Cookie:` header containing an empty `;`-separated element.
2. **The windowed body scan is not byte-identical to whole-document processing**, contradicting
   the load-bearing D-01 claim. A JSON key/colon/value pair spread over three lines at a window
   boundary leaks its value on the windowed path while the single-pass path redacts it.
3. **`redactCookieSections` is quadratic and has no deadline at all** — measured 2.6 s on a
   512 KB attacker-controlled input, extrapolating to ~42 s at the MCP default `maxBodyBytes` of
   2 MiB. Every other rule this phase touched is deadline-bounded; this one is not.
4. **A newline-free body above the window width is destroyed in its entirety.** The
   halve-and-retry mitigation that exists specifically so slower machines "pace instead of losing
   content outright" provably cannot engage on the single most common oversized payload shape
   (minified JSON). A 2 MiB minified JSON body measures 66 ms for the JSON rule alone against a
   50 ms per-pattern deadline, on the maintainers' own reference hardware.

Separately, the widened key expression over-redacts a class that is far larger than the ten
accepted cases recorded in the tests, including `status_code`, `error_code`, `statusCode` and
`token_type` — values a security-analysis prompt cannot function without.

Deliberate decisions listed in the review brief (ADR-14 test inversions, custom patterns under
`OFF`, unbounded header-stage rules, the `@Suppress` annotations, the ten accepted
over-redactions, the HKDF block) were verified as intentional and are **not** reported.

## Narrative Findings (AI reviewer)

No `<structural_findings>` block was supplied for this review, so all findings below are narrative.

---

## Critical Issues

### CR-01: `redactCookieSections` span collapses to zero length — every cookie value in the section leaks

**File:** `src/main/kotlin/com/six2dez/burp/aiagent/redact/Redaction.kt:132-153` (span bound at
`:140-141`), with the emitter at
`src/main/kotlin/com/six2dez/burp/aiagent/scanner/PassiveAiScannerPrompts.kt:103-112` and the
producer at `src/main/kotlin/com/six2dez/burp/aiagent/scanner/PassiveAiScannerAnalysis.kt:246-251`.

**Issue:**
The cookie section is bounded by `out.indexOf("\n\n", bodyStart)`. `bodyStart` is the index
immediately after `=== COOKIES ===`, i.e. the newline that terminates the header line. If the
**first** emitted cookie line is blank, that newline and the blank line's newline form `"\n\n"`
*at `bodyStart` itself*, so `end == bodyStart`, `section` is the empty string, and the rule
redacts nothing at all. If a blank line appears **anywhere inside** the list, every cookie after
it is left untouched.

The emitter produces exactly that blank line. `PassiveAiScannerAnalysis.kt:246-251` does:

```kotlin
.flatMap { it.value().split(";").map { c -> c.trim() } }
.take(COOKIES_MAX_COUNT)
```

with no blank filter, and `PassiveAiScannerPrompts.kt:110` does `cookies.forEach { appendLine(it) }`.
A `Cookie: ; JSESSIONID=…; abtest_bucket=…` header (or any `a=b;;c=d`) yields an empty element
and therefore a blank line.

Reproduced against a verbatim transcription of the shipped function:

```
input:
=== COOKIES ===
<blank>
JSESSIONID=REALSECRET
abtest_bucket=OPAQUE_XYZ

output: unchanged  -->  LEAK OPAQUE_XYZ? true
```

`JSESSIONID` is incidentally saved by `formBodyParamRegex` via `SENSITIVE_KEY_EXPR`;
`abtest_bucket` — the name the tests themselves identify as "the entry only the section rule
saves" — leaks verbatim to the backend. `RedactionTest.cookieSectionValuesRedactedPerName` and
`PassiveAiScannerPromptRedactionTest.emittedCookieSectionValuesAreRedacted_sc1` both build their
fixtures with no blank entries, so neither catches this.

The same root cause has a second trigger: `nextSectionRegex` (`(?m)^=== `) also shortens the span,
so a cookie element that begins with `=== ` after `trim()` truncates the section and leaks
everything below it. Reproduced:

```
=== COOKIES ===
abtest_bucket=[REDACTED]      <- redacted
=== FOO ===
abtest2_bucket=OPAQUE_ABC     <- LEAKED
```

Both triggers are the same defect: the span terminator is derived from unsanitised content that
sits *inside* the region the rule is supposed to protect.

**Fix:** close it at both ends. Drop blank cookie elements at the producer, and make the span
bound refuse to collapse.

```kotlin
// PassiveAiScannerAnalysis.kt — never emit a blank or section-shaped cookie line
val cookies =
    request
        .headers()
        .filter { it.name().equals("Cookie", ignoreCase = true) }
        .flatMap { it.value().split(";").map { c -> c.trim() } }
        .filter { it.isNotBlank() && !it.startsWith("===") }
        .take(COOKIES_MAX_COUNT)
```

```kotlin
// Redaction.kt — a blank line adjacent to the header must not end the section, and the section
// must never be bounded before its first content line.
val firstLine = out.indexOf('\n', bodyStart)
val scanFrom = if (firstLine >= 0) firstLine + 1 else bodyStart
var end = out.length
val blankLine = out.indexOf("\n\n", scanFrom)
if (blankLine >= 0) end = minOf(end, blankLine)
val nextSection = nextSectionRegex.find(out, scanFrom)
if (nextSection != null) end = minOf(end, nextSection.range.first)
end = maxOf(end, bodyStart)
```

Add a regression test with a blank first element and a blank middle element, asserting per name
exactly as `cookieSectionValuesRedactedPerName` does, and keep `abtest_bucket` as the sentinel so
`formBodyParamRegex` cannot mask the result.

---

### CR-02: window boundary splits a multi-line JSON pair — the secret leaks, and D-01's byte-identity claim is false

**File:** `src/main/kotlin/com/six2dez/burp/aiagent/redact/Redaction.kt:626-654`
(`windowEnd` / `isJsonPairBoundaryRisk`, specifically the one-line extension at `:642-645`);
claim under review at `:429-431`.

**Issue:**
`windowEnd`'s own comment states the hazard precisely — "`jsonSecretKeyRegex`'s whitespace class
CAN span newlines (it matches a pretty-printed key/colon/value spread over four lines), so a pair
split exactly across a window boundary would be missed" — and then mitigates it by pulling in
**exactly one** additional line:

```kotlin
if (isJsonPairBoundaryRisk(s.substring(maxOf(start, prevLineStart), lastNewline))) {
    val following = s.indexOf('\n', end)
    if (following >= 0) end = following + 1
}
```

The extension does not re-run the risk check on the newly-included line, so the very shape the
comment cites is not handled. When the key, the colon and the value sit on three separate lines,
the boundary lands after the key line, the risk check fires (the line ends with `"`), one more
line is pulled in (the `:` line), and the window stops there. The value starts the next window and
`jsonSecretKeyRegex` cannot match across the cut.

Reproduced by driving a verbatim transcription of `windowEnd` over the real
`formBodyParamRegex` / `jsonSecretKeyRegex`, sweeping the pair across the boundary:

```
DIVERGENCE at shift=7  windowedLeak=true  singlePassLeak=false  cut=4002
  window tail : yyyyyyy\nzzzzzzz\n  "token"\n  :\n
  next  head  :   "SUPER-SECRET-7"\nyyyyyyyyyyyyyyyyyyy\ny
  byte-identical? false
```

Two consequences:

- **A secret that the single-pass path redacts survives the windowed path.** That is a redaction
  bypass keyed only on payload size, which is exactly what PRIV-06 exists to eliminate.
- **The D-01 statement at `:429-431`** — "Line-boundary cutting was proven byte-identical to
  whole-document processing, which is the property actually needed" — is not true as implemented.
  Whatever established it did not sweep the fixture across the cut, and no committed test asserts
  it (see WR-06).

The response body is attacker-controlled and window boundaries are deterministic given a known
prefix length, so the alignment is craftable, not merely accidental.

**Fix:** loop the extension until the window ends on a line that is not a JSON-pair continuation,
with a hard cap so a pathological input cannot grow the window without bound.

```kotlin
private const val MAX_JSON_BOUNDARY_LOOKAHEAD_LINES = 8

// ...
var end = lastNewline + 1
var lineStart = maxOf(start, s.lastIndexOf('\n', lastNewline - 1) + 1)
var lineEnd = lastNewline
var pulled = 0
while (pulled < MAX_JSON_BOUNDARY_LOOKAHEAD_LINES &&
    isJsonPairBoundaryRisk(s.substring(lineStart, lineEnd))
) {
    val following = s.indexOf('\n', end)
    if (following < 0) return s.length
    lineStart = end
    lineEnd = following
    end = following + 1
    pulled++
}
return end
```

`isJsonPairBoundaryRisk` must also treat a blank/whitespace-only line as risky when the previous
line was risky, otherwise the `"key"` / `:` / *blank* / `"value"` shape still slips through.

Add a property-style test that sweeps a three-line JSON pair across the cut (shift 0..N) and
asserts `windowedScan(input) == singlePass(input)` for every shift — that is the assertion D-01
actually claims.

---

### CR-03: `redactCookieSections` is quadratic with no deadline — attacker-controlled multi-second to multi-minute stall

**File:** `src/main/kotlin/com/six2dez/burp/aiagent/redact/Redaction.kt:132-153` (the rebuild at
`:145-148`).

**Issue:**
Each loop iteration rebuilds the whole string:

```kotlin
out =
    out.substring(0, bodyStart) +
    section.replace(cookieSectionPairRegex) { "${it.groupValues[1]}=[REDACTED]" } +
    out.substring(end)
```

That is an O(n) copy per occurrence of `=== COOKIES ===`, giving O(k·n). The occurrence count `k`
is attacker-controlled: `buildScanMetadataText` emits `=== RESPONSE BODY ===` and
`=== RESPONSE HEADERS ===` verbatim, and `McpToolContext.redactIfNeeded` runs the same function
over raw tool output. The rule runs whenever `policy.stripCookies` is true, i.e. in the default
BALANCED mode as well as STRICT.

Unlike every rule this phase touched, this one is not routed through `SafeRegex`, is not inside
`bodyStage`, and is not covered by `MAX_REDACTION_BUDGET_MS`. It is also not one of the eight
pre-existing header-stage rules the brief scopes out — it is new in this phase.

Measured on Apple Silicon / JDK 21 against a verbatim transcription:

```
  64 KB body,   4096 header occurrences ->     65.3 ms
 128 KB body,   8192 header occurrences ->    187.7 ms
 256 KB body,  16384 header occurrences ->    682.4 ms
 512 KB body,  32768 header occurrences ->   2630.9 ms
```

Clean 4× per doubling. Extrapolated to the MCP default `maxBodyBytes` of 2 MiB
(`AgentSettings.kt:1171`) that is ~42 s per tool call, on an uninterruptible worker thread, with
no budget and no marker. On slower hardware it is minutes.

(Supporting datum: the same phase made `urlTokenParamRegex` ~7× more expensive by embedding
`SENSITIVE_KEY_EXPR` — measured 70 ms vs 10 ms on a 2 MiB adversarial query-string body — and that
rule is also unbounded. That one stays linear and is not itself a defect, but it removes the
headroom the header stage used to have.)

**Fix:** collect every span first, then rebuild once, and bound the number of sections processed.

```kotlin
private const val MAX_COOKIE_SECTIONS = 32

private fun redactCookieSections(text: String): String {
    val sb = StringBuilder(text.length)
    var cursor = 0
    var handled = 0
    while (handled < MAX_COOKIE_SECTIONS) {
        val h = text.indexOf(COOKIE_SECTION_HEADER, cursor)
        if (h < 0) break
        val bodyStart = h + COOKIE_SECTION_HEADER.length
        val end = cookieSectionEnd(text, bodyStart)   // see CR-01 for the corrected bound
        sb.append(text, cursor, bodyStart)
        sb.append(text.substring(bodyStart, end).replace(cookieSectionPairRegex) {
            "${it.groupValues[1]}=[REDACTED]"
        })
        cursor = end
        handled++
    }
    sb.append(text, cursor, text.length)
    return sb.toString()
}
```

This is O(n) overall. If `MAX_COOKIE_SECTIONS` is reached, fail closed on the remainder rather
than passing it through — dropping the tail behind `windowDroppedMarker` matches the D-02
discipline the rest of the file follows.

---

### CR-04: a newline-free body above the window width is destroyed wholesale; halve-and-retry cannot engage

**File:** `src/main/kotlin/com/six2dez/burp/aiagent/redact/Redaction.kt:602-608` (`splitPoint`),
`:634-639` (`windowEnd` oversized-line path), `:581-597` (`dropOrRetry`);
`src/main/kotlin/com/six2dez/burp/aiagent/config/Defaults.kt:64`.

**Issue:**
`windowEnd` deliberately makes an over-width line its own window:

```kotlin
if (lastNewline <= start) {
    val next = s.indexOf('\n', start)
    return if (next < 0) s.length else next + 1
}
```

`splitPoint` then returns `0` for any window with no interior newline, so `dropOrRetry` skips
straight to the drop:

```kotlin
val cut = if (retryable) splitPoint(window) else 0
if (cut <= 0 || cut >= window.length) {
    sink.append(windowDroppedMarker(window.length))
```

The `WINDOW_RETRY_MAX_DEPTH` machinery — justified at `:386-389` as existing so that "a 2-3x
slower machine would [not] drop content that ships today" — is structurally inapplicable to a
newline-free body. The result is that the entire payload is replaced by a single marker.

This is not a theoretical shape. `McpToolContext.redactIfNeeded` receives serialized tool output
capped at `maxBodyBytes` (default 2 MiB), and `toolJson.encodeToString(...)` emits **minified,
newline-free JSON**. Measured on the reference hardware:

```
newline-free body chars = 2097156   contains \n? false
windowEnd(0, 1_000_000) = 2097156   (whole body becomes ONE window)
  run 0: form=11.0 ms  json=66.1 ms  total=77.2 ms   (per-pattern deadline is 50 ms)
  run 1: form=14.4 ms  json=62.6 ms  total=77.0 ms
  run 2: form=13.6 ms  json=64.7 ms  total=78.3 ms
```

The JSON rule alone exceeds the 50 ms deadline by 25%, on Apple Silicon / JDK 21 — the very
platform the 27 ms/MB sizing note in `Defaults.kt:72-74` was measured on. That sizing was
evidently taken on newline-bearing content; dense newline-free JSON costs ~31 ms/MB, so the cliff
lands around 1.6 MB here and around 800 KB on a 2× slower machine. Above it, the AI receives
`[REDACTION INCOMPLETE - 2097156 CHARS DROPPED AND NOT SENT]` and nothing else.

Fail-closed is the right direction, but silently emitting an empty analysis for a
default-configuration input is incorrect behaviour, and no test covers it.

**Fix:** let `splitPoint` fall back to a character split when no line boundary exists. A mid-line
cut can only affect line-anchored rules, and a window that is *already* being discarded outright
has strictly nothing to lose:

```kotlin
private fun splitPoint(window: String): Int {
    val mid = window.length / 2
    val backward = window.lastIndexOf('\n', mid)
    if (backward > 0) return backward + 1
    val forward = window.indexOf('\n', mid)
    if (forward >= 0 && forward + 1 < window.length) return forward + 1
    // No line boundary at all: a mid-line cut is strictly better than discarding the whole
    // window. The alternative is emitting nothing, which cannot preserve any anchor either.
    return if (window.length > 1) mid else 0
}
```

Also raise `WINDOW_RETRY_MAX_DEPTH` (2 levels only reaches quarters) or make the retry depth a
function of `window.length / MAX_REDACTION_BODY_CHARS`, and add a test for a newline-free body of
`MAX_REDACTION_BODY_CHARS * 2` asserting that a planted `api_key=` value is redacted rather than
that the whole body is replaced by a marker.

---

## Warnings

### WR-01: the widened key expression over-redacts a much larger class than the ten accepted cases

**File:** `src/main/kotlin/com/six2dez/burp/aiagent/redact/Redaction.kt:179-180`
(`SENSITIVE_WORDS`), `:238-240` (`SENSITIVE_KEY_EXPR`).

**Issue:** the tests record ten accepted over-redactions. Driving the real
`formBodyParamRegex` and `jsonSecretKeyRegex` over a wider key corpus shows the class is far
bigger, and includes names that are load-bearing for the product's core function:

```
status_code   RED     error_code    RED     response_code RED     http_code     RED
statusCode    RED     errorCode     RED     token_type    RED     tokenType     RED
zip_code      RED     country_code  RED     postal_code   RED     currency_code RED
language_code RED     product_code  RED     promo_code    RED     coupon_code   RED
area_code     RED     qr_code       RED     session_count RED     auth_type     RED
auth_url      RED     primary_key   RED     foreign_key   RED     sort_key      RED
partition_key RED     cache_key     RED     idempotency_key RED   row_key       RED
public_key    RED     sortKey       RED     cacheKey      RED     zipCode       RED
```

`{"statusCode": 401, "errorCode": "AUTH_FAILED"}` becomes
`{"statusCode": "[REDACTED]", "errorCode": "[REDACTED]"}` before the analysis prompt is built —
the model is then asked to find an authentication flaw with the status and error code removed.
`token_type: "Bearer"` (benign OAuth metadata) goes the same way. This is a functional regression
in a passive-vulnerability scanner, not a cosmetic over-redaction.

The driver is the two broadest words in the vocabulary, `code` and `key`, combined with the D-11
whole-token boundary. `session`, `auth` and `token` contribute a smaller tail.

**Fix:** the cheapest correct change is to require a separator-delimited *credential-shaped*
context for the two broad words rather than dropping them:

```kotlin
// Words that are only sensitive when they are the WHOLE key or the trailing token, so
// status_code / zip_code / primary_key / cache_key survive while access_code / api_key do not.
private const val BROAD_WORDS = "key|code"
private const val SENSITIVE_WORDS =
    "access_token|api_key|apikey|auth|token|secret|password|pwd|session|sid"
```

and give `BROAD_WORDS` its own alternative in `SENSITIVE_KEY_EXPR` that requires a
credential-bearing prefix (`api`, `access`, `secret`, `auth`, `private`, `signing`, `enc`) or
whole-key equality. Whatever shape is chosen, extend `sc3BenignKeys` with `status_code`,
`error_code`, `statusCode`, `errorCode`, `token_type`, `primary_key`, `sort_key`, `cache_key` and
`zip_code` so the decision is pinned in the corpus rather than rediscovered in the field. If the
maintainer decides the current breadth is correct, these belong in the accepted-over-redaction
list in the file comment and in the tests — silently accepting `status_code` is not the same as
deciding it.

---

### WR-02: the OFF-mode privacy banner infers "custom patterns are applied" from unsaved text

**File:** `src/main/kotlin/com/six2dez/burp/aiagent/ui/SettingsPanelActions.kt:243-251`.

**Issue:**

```kotlin
val customPatternsConfigured = customPatternsArea.text.split('\n').any { it.isNotBlank() }
val offClause =
    if (customPatternsConfigured) {
        "Built-in redaction is disabled; only your custom patterns are applied to MCP and prompts."
    } else {
        "Built-in redaction is disabled; raw traffic may reach MCP and prompts."
    }
```

The live `JTextArea` is not the engine's state. `Redaction.compiledCustomPatterns` is only updated
by `applyAndSaveSettings` (`SettingsPanelSettingsIO.kt:474-475`), and
`validateAndCollectCustomPatterns` (`:218-260`) drops any line that fails `isPatternSafe`. A user
who is *mid-typing* a pattern in OFF mode — or who has typed only patterns that will be rejected —
sees the reassuring clause while raw traffic is in fact flowing to MCP and prompts. The banner
downgrades a real risk warning based on text that has no effect on anything.

The two sibling call sites got this right: `ChatPanel.kt:315` and `ChatPanel.kt:1153` both read
`getSettings().customRedactionPatterns`, the persisted and validated list. Only this one diverges.

**Fix:** read the same source as the other two, which is also cheap and does not run the ReDoS
probe on the EDT:

```kotlin
val customPatternsConfigured = settings.customRedactionPatterns.isNotEmpty()
```

If the intent is genuinely to reflect unsaved edits, then say so explicitly and do not weaken the
warning — e.g. keep the "raw traffic may reach MCP and prompts" clause and append
"(unsaved custom patterns are not active until you save)".

---

### WR-03: `SafeRegex.replaceAllSafe` is dead in production and retained as a fail-open footgun

**File:** `src/main/kotlin/com/six2dez/burp/aiagent/redact/SafeRegex.kt:94-110`.

**Issue:** grepping `src/main` for `replaceAllSafe\b` returns only the declaration itself and four
comment references inside `Redaction.kt`. There is not one production caller left. What remains is
a `public` function in the redaction package whose own KDoc says it is "fail-open" and "conflates
'matched nothing' with 'timed out'" — precisely the mistake this phase exists to remove, sitting
one autocomplete away from the next contributor who adds a rule.

The four comments in `Redaction.kt` (`:437`, `:474`, `:543`) that warn against using it are
evidence that the team already anticipates it being reached for.

**Fix:** delete it and move `SafeRegexTest.catastrophicPatternTimesOutAndReturnsInput` /
`benignReplaceAppliesReplacement` onto `replaceAllSafeReporting(...).text`. If it must survive for
an out-of-tree consumer, mark it so the compiler pushes back:

```kotlin
@Deprecated(
    "Fail-open: returns the input unchanged on timeout, indistinguishable from 'no matches'. " +
        "Use replaceAllSafeReporting and branch on timedOut.",
    ReplaceWith("replaceAllSafeReporting(input, pattern, replacement, timeoutMs).text"),
    DeprecationLevel.ERROR,
)
fun replaceAllSafe(...)
```

---

### WR-04: `Redaction.truncationLogger` is never cleared on unload

**File:** `src/main/kotlin/com/six2dez/burp/aiagent/App.kt:68` (set) and `:202-232` (`shutdown`).

**Issue:** `shutdown()` deliberately unwires every other global sink —
`BackendDiagnostics.retry = null` (`:217`), `AuditLogger.registerGlobalEmitter(null)` (`:231`) —
and even resets `Redaction`'s other global state via `Redaction.clearMappings()` (`:230`). The new
`Redaction.truncationLogger` is the one addition that is set and never cleared.

The lambda captures `api`. Any redaction still in flight on an MCP tool thread or the Burp scanner
pool during teardown can call `api.logging().logToOutput` on a torn-down API; because
`maybeLogTruncation` has no try/catch, a throw from that sink propagates out through
`windowedScan` → `bodyStage` → `Redaction.apply` into the caller.

**Fix:** one line, beside the existing `Redaction.clearMappings()` step:

```kotlin
safeShutdownStep("Redaction mappings") { Redaction.clearMappings() }
Redaction.truncationLogger = null
```

Defensively, also make the sink non-throwing at the source:

```kotlin
runCatching { truncationLogger?.invoke(truncationLine(droppedChars, suppressed)) }
```

so a diagnostics sink can never abort a redaction pass. The KDoc at `:304-318` already promises
"a missing sink costs the user visibility, never correctness" — this makes that true for a
*failing* sink as well.

---

### WR-05: the two fail-closed tests never assert that the unscanned bytes are absent

**File:** `src/test/kotlin/com/six2dez/burp/aiagent/redact/RedactionTest.kt:888-895` and
`:937-944`.

**Issue:** both `oversizeBodyFailsClosed` and `subWindowBodyFailsClosed` verify the contract with:

```kotlin
assertTrue(output.contains("REDACTION INCOMPLETE") || output.contains("REDACTION BUDGET EXCEEDED"))
assertFalse(output.contains(oversizeBody))
```

The second assertion is trivially satisfied by *any* modification to the input — inserting a
single marker anywhere already makes `output.contains(body)` false. It therefore proves nothing
about the property under test. A mutation that emitted, say, three windows unscanned and marked
only the fourth would pass both assertions and both tests would stay green while the fail-open the
phase exists to kill was back in place.

Since every window in these fixtures must be dropped, the missing assertion is direct and cheap:

**Fix:**

```kotlin
assertFalse(
    output.contains("a".repeat(2_000)),
    "No unscanned window may reach the output; only markers may remain",
)
assertTrue(
    output.length < body.length / 2,
    "A wholly-unscannable body must collapse to markers, not pass through",
)
```

---

### WR-06: no test covers the D-01 windowing invariant — the exact gap CR-02 fell through

**File:** `src/test/kotlin/com/six2dez/burp/aiagent/redact/RedactionTest.kt` (whole file);
claim under test at `src/main/kotlin/com/six2dez/burp/aiagent/redact/Redaction.kt:429-431`.

**Issue:** D-01's central claim is that windowing is byte-identical to whole-document processing
and that `(?m)^` anchors are neither created nor destroyed at a cut. The committed tests exercise
the windowed path in exactly two ways: one secret placed in the second window
(`oversizeBodySecretDoesNotSurvive`) and two pathological-pattern drop cases. Nothing asserts
equivalence, and nothing sweeps a fixture across the boundary.

A shifted-fixture sweep found a counterexample within 8 shifts (CR-02). The same technique would
also pin the anchor invariant, the `hard == s.length` edge, the CRLF case, and the
`lastNewline <= start` oversized-line branch — none of which have any coverage today.

**Fix:** add a parameterised equivalence test. It is the assertion the design document already
claims to have made:

```kotlin
@Test
fun windowedScanIsByteIdenticalToSinglePass() {
    val secretLine = "  \"token\"\n  :\n  \"BOUNDARY-SECRET\"\n"
    val pad = "y".repeat(19) + "\n"
    for (shift in 0 until 64) {
        val body = buildString {
            while (length < Defaults.MAX_REDACTION_BODY_CHARS - 20 - shift) append(pad)
            append("z".repeat(shift)).append('\n')
            append(secretLine)
            while (length < Defaults.MAX_REDACTION_BODY_CHARS + 800) append(pad)
        }
        val policy = RedactionPolicy.fromMode(PrivacyMode.STRICT)
        assertFalse(
            Redaction.apply(body, policy, stableHostSalt = "salt").contains("BOUNDARY-SECRET"),
            "shift=$shift: a JSON pair straddling a window boundary must still be redacted",
        )
    }
}
```

---

### WR-07: `isPatternSafe`'s single fixed probe now gates patterns that run in every mode and can spend the whole body budget

**File:** `src/main/kotlin/com/six2dez/burp/aiagent/redact/SafeRegex.kt:122-153`;
consumer at `src/main/kotlin/com/six2dez/burp/aiagent/redact/Redaction.kt:454-458`.

**Issue:** `isPatternSafe` validates against one fixed string, `"a".repeat(2_000) + "!"`. A pattern
that is catastrophic on a different character class — `(\d+)+@`, `([a-z]+)+!`, `(\w+\s?)*$` — runs
in microseconds on that probe and is accepted.

Before this phase that was tolerable: custom patterns ran only inside the `redactTokens` branch and
a slow one was simply skipped. D-05 changed both halves of that. Custom patterns now run in
**every** privacy mode including `OFF` (`Redaction.kt:454-458`), and `bodyStage` now fails
**closed**, so an accepted-but-slow pattern no longer degrades gracefully — it burns
`MAX_REDACTION_BUDGET_MS` and causes real content to be dropped behind markers on every single
call, including calls the user believes are unfiltered. The blast radius of a bad accept grew in
both directions in this phase without the gate being strengthened.

`App.kt:93-95` compounds it: startup seeding trusts persisted patterns wholesale
("persisted patterns were already validated by isPatternSafe on save") and re-validates nothing.
Burp preferences are user-editable and may predate `isPatternSafe`.

**Fix:** widen the probe to a small corpus rather than a single string, and re-validate at seed
time:

```kotlin
private val ADVERSARIAL_PROBES: List<String> =
    listOf(
        "a".repeat(2_000) + "!",
        "1".repeat(2_000) + "!",
        "aA1_-".repeat(400) + "!",
        ("x".repeat(50) + " ").repeat(40) + "!",
    )
// isPatternSafe: run the deadline against every probe; any timeout rejects.
```

```kotlin
// App.initialize — do not trust the persisted list blindly
Redaction.setCustomPatterns(settings.customRedactionPatterns.filter { SafeRegex.isPatternSafe(it) })
```

This runs off the EDT-critical path at startup and costs at most `n * 50 ms` once per launch.

---

_Reviewed: 2026-08-11T15:38:03Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
