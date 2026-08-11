# Phase 21: Redaction Completeness - Pattern Map

**Mapped:** 2026-08-11
**Files analyzed:** 16 (15 modified, 1 created)
**Analogs found:** 16 / 16

> **Scope note.** `21-RESEARCH.md` already did the deep design work with executed verification — the
> cookie rules, the key expression, the windowing algorithm, the body-stage skeleton and the D-06
> deletions are all in §"Code Examples" 1–7 and are **not** repeated here. This document answers a
> narrower question: *what existing code in this repo does the new code have to look like?* It is
> weighted toward the five areas where the phase adds genuinely new shapes — the three Wave 0
> extractions, the new test file, the `SafeRegex` sibling API, the `Redaction` diagnostic callback,
> and ADR-14.

---

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|-------------------|------|-----------|----------------|---------------|
| `src/main/kotlin/.../scanner/PassiveAiScannerPrompts.kt` **(3 new top-level funs)** | utility (pure prompt builders) | transform | **same file** — `buildCompactResponseBody` (`:33-50`) | exact |
| `src/test/kotlin/.../scanner/PassiveAiScannerPromptRedactionTest.kt` **(NEW)** | test | transform | `scanner/PassiveAiScannerJsonParsingTest.kt` (whole file) + `scanner/InjectionPointExtractorTest.kt:1-37` for the Montoya mock | exact |
| `src/main/kotlin/.../redact/SafeRegex.kt` (`replaceAllSafeReporting`, `SafeReplaceResult`) | utility | transform | **same file** — `replaceAllSafe` (`:56-77`) | exact |
| `src/main/kotlin/.../redact/Redaction.kt` — diagnostic-callback seam (D-03) | utility + settable state | event-driven (signal out) | `backends/BackendDiagnostics.kt:11-12` (field) + **same file** `compiledCustomPatterns` (`:120-142`) | exact |
| `src/main/kotlin/.../redact/Redaction.kt` — rate limiter (D-03) | utility | event-driven | `scanner/PassiveAiScannerAnalysis.kt:825-835` (`maybeLogBackoff`) | exact |
| `src/main/kotlin/.../redact/Redaction.kt` — cookie rules, key expr, body stage | utility (redactor) | transform | **same file** — `apply` (`:229-287`) | exact — see RESEARCH §"Code Examples" 1–6 |
| `src/main/kotlin/.../config/Defaults.kt` (`MAX_REDACTION_BODY_CHARS` KDoc + `MAX_REDACTION_BUDGET_MS`) | config | — | **same file** `:54-58` | exact |
| `src/main/kotlin/.../scanner/PassiveAiScannerAnalysis.kt:232-252, 372-381, 393-402` | service (prompt emitter) | batch / event-driven | **same file** — the `doAnalysis` body | exact — see RESEARCH §"Code Examples" 7 |
| `src/main/kotlin/.../mcp/McpToolContext.kt:59-75` (delete OFF branch) | service (context object) | request-response | **same file** `redactIfNeeded` | exact — see RESEARCH §"Code Examples" 7 |
| `src/main/kotlin/.../App.kt` (wire `Redaction.truncationLogger`) | config / wiring | — | `App.kt:62-63` (`BackendDiagnostics.output = { api.logging().logToOutput(it) }`) | exact |
| `src/main/kotlin/.../ui/ChatPanel.kt:1146` | component (Swing) | — | **same `when`** — the STRICT/BALANCED arms at `:1144-1145` | exact |
| `src/main/kotlin/.../ui/components/ContextPreviewDialog.kt:122` | component (Swing) | — | **same `when`** — `:120-121` | exact |
| `src/main/kotlin/.../ui/SettingsPanelActions.kt:236-251` | component (notice composer) | — | **same `when`** — the four OFF arms at `:237-251` | exact |
| `src/main/kotlin/.../ui/components/PrivacyPill.kt:41` | component (Swing) | — | **same `when`** — BALANCED tooltip at `:36` | exact |
| `src/test/kotlin/.../redact/RedactionTest.kt` (extend + 2 inversions) | test | transform | **same file** — `customPatternRedactsInStrictAndBalanced` (`:329-346`), `bodyJsonUnquotedSecretValuesRedacted` (`:294-310`) | exact |
| `src/test/kotlin/.../redact/SafeRegexTest.kt` (add `timedOut` coverage) | test | transform | **same file** — `catastrophicPatternTimesOutAndReturnsInput` (`:32-46`) | exact |
| `DECISIONS.md` (ADR-14) | doc | — | **same file** — ADR-13 (`:140-151`) | exact |
| `.planning/codebase/CONCERNS.md` (header-stage gap, plural-key gap) | doc | — | **same file** — §"Redaction regex coverage gaps" (`:62-67`) | exact |

---

## Pattern Assignments

### 1. `scanner/PassiveAiScannerPrompts.kt` — the three Wave 0 extractions

**Analog:** the file itself. It already hosts exactly this species of function: top-level,
package-visible, Montoya-free, pure.

**The house shape for a top-level `internal fun` in this file** (`:20-31`, verbatim):

```kotlin
internal fun buildCompactRequestBody(
    body: String,
    contentType: String,
    maxChars: Int,
): String {
    if (body.isBlank()) return ""
    return if (looksLikeJson(contentType, body)) {
        compactJsonBody(body, maxChars)
    } else {
        truncateWithEllipsis(body, maxChars)
    }
}
```

Rules the new functions must match, each observed in this file:

| Convention | Evidence | Consequence for the plan |
|---|---|---|
| **Top-level, no receiver.** `internal fun name(...)`, *not* `internal fun PassiveAiScanner.name(...)` | `:12`, `:20`, `:33`, `:138`, `:179`, `:225` — every function in the file | Contrast `PassiveAiScannerAnalysis.kt`, where every function is `internal fun PassiveAiScanner.x()`. The extraction moves *out* of the receiver-bound file into the receiver-free one. That is the whole point: it removes the `PassiveAiScanner`/Montoya dependency. |
| **No KDoc.** The file contains **zero** `/** */` blocks (verified by count). Comments are `//` lines, either file-level (`:6`) or inline (`:47`) | `grep -c '/\*\*'` → `0` | Use `//` comments above the new functions. Do **not** import the `/** */` style from `redact/`; it is not this file's convention and consistency inside a file beats consistency across packages. |
| **One parameter per line + trailing comma**, closing `): Type {` on its own line | `:12-15`, `:20-24`, `:33-37` | ktlint 1.5.0 is strict (`ignoreFailures` is false unless `-PktlintLenient=true`). A 14-parameter signature must be written this way. |
| **File-private constants are `private const val` UPPER_SNAKE at file top** | `:8-10` (`JSON_ARRAY_SAMPLE_SIZE`, `HTML_FORMS_SAMPLE_MAX`, `HTML_INLINE_SCRIPTS_SAMPLE_MAX`) | If the extraction needs a section-header literal, put it here — unless it is the shared `COOKIE_SECTION_HEADER`, which RESEARCH Pattern 1 places in `redact/Redaction.kt` and imports. |
| **The AWT-free banner is load-bearing** | `:6` — `// AWT-free contract: MUST NOT import java.awt.* or javax.swing.*` | Keep it at the top; the three new functions must not break it. |
| **Same-package symbols need no import** | `jsonMapper` (`:66`) and `PendingAnalysis` (`:179`) are used unimported | `buildScanMetadataText` can call `truncateWithEllipsis` etc. directly. But `redactScanMetadata` **does** need three new imports. |

**Import block after the extraction.** ktlint enforces lexicographic ordering; the current block is
only two lines (`:3-4`). `redactScanMetadata` requires `PrivacyMode`, `Redaction`, `RedactionPolicy`.
Copy the ordering from `PassiveAiScannerAnalysis.kt:3-13`, which already has all three:

```kotlin
import com.six2dez.burp.aiagent.redact.PrivacyMode
import com.six2dez.burp.aiagent.redact.Redaction
import com.six2dez.burp.aiagent.redact.RedactionPolicy
import com.six2dez.burp.aiagent.supervisor.AgentSupervisor
import com.six2dez.burp.aiagent.util.SecurityExcerpts
```

**⚠ BLOCKER the planner must decide before writing the extraction: detekt `LongParameterList`.**

`detekt.yml:8-10` sets `functionThreshold: 10`. RESEARCH's proposed `buildScanMetadataText`
signature has **14 parameters** — it *will* raise a new `LongParameterList` finding, and QUAL-07
forbids growing `detekt-baseline.xml` (which already carries 14 entries for that rule). Three
in-repo-supported resolutions, in order of precedent strength:

1. **`@Suppress("LongParameterList")` on the function.** Established precedent for suppressing a
   complexity rule on a top-level function: `ui/SettingsPanelInit.kt:29` and
   `ui/SettingsPanelMcpTabs.kt:148` both carry a bare `@Suppress("LongMethod")` on the line above
   the `internal fun`; `scanner/PassiveAiScannerAnalysis.kt:170` carries
   `@Suppress("CyclomaticComplexMethod", "LongMethod")` on the function being extracted *from*.
2. **Group the sections into one holder.** Note a `data class` constructor with >10 params trips the
   same rule via `constructorThreshold: 10`, so the holder must genuinely reduce the count
   (e.g. a `ScanMetadataSections(requestHeaders, responseHeaders, authHeaders, cookies, params)`
   five-field holder plus the scalars) — not merely relocate it.
3. Split into two builders (header block + section block). Highest churn; mentioned for completeness.

**File-private constants do not cross the file boundary.** `PARAM_VALUE_MAX_CHARS = 200` and
`COOKIES_MAX_COUNT = 6` are `private const val` at `PassiveAiScannerAnalysis.kt:26-27` and are
therefore **invisible** from `PassiveAiScannerPrompts.kt`. Two supported options, both already used
in this codebase:

- **Keep truncation at the call site** (preferred, and what RESEARCH's `formatParamLine(name, value,
  type)` signature already implies): `PassiveAiScannerAnalysis.kt:239` keeps
  `truncateWithEllipsis(p.value(), PARAM_VALUE_MAX_CHARS)` and passes the finished value in.
- **Redeclare the constant.** This is an explicitly documented idiom here —
  `PassiveAiScannerAnalysis.kt:22` reads
  `// Redeclared from PassiveAiScanner companion (private there, needed here for extension functions)`
  above `:23-29`. If a constant must cross, copy that comment form.

**The call site being extracted** (`PassiveAiScannerAnalysis.kt:342-391`) is a single
`buildString { ... }` whose inputs at that point are already `String` / `Int` / `List<String>` —
`kbSummary`, `displayUrl`, `urlPath`, `request.method()`, `response?.statusCode() ?: 0`,
`response?.statedMimeType()?.name ?: "unknown"`, `potentialIds`, `requestHeaders`, `responseHeaders`,
`authHeaders`, `cookies`, `params`, `requestBody`, `responseBody`. The two Montoya reads
(`request.method()`, `response?...`) must be evaluated at the call site and passed as plain values,
which is what makes the extracted function testable without mocks.

---

### 2. `src/test/kotlin/.../scanner/PassiveAiScannerPromptRedactionTest.kt` (NEW)

**Primary analog:** `src/test/kotlin/com/six2dez/burp/aiagent/scanner/PassiveAiScannerJsonParsingTest.kt`
— same package, same species (a test of top-level `internal fun`s), 88 lines, zero mocks.

**Header + class shape** (`:1-9`, verbatim):

```kotlin
package com.six2dez.burp.aiagent.scanner

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PassiveAiScannerJsonParsingTest {
    @Test
    fun cleanJsonResponse_extractsArrayFromMarkdownCodeFence() {
```

Key properties to copy:
- **`package com.six2dez.burp.aiagent.scanner`** — required, not optional. `internal` visibility is
  module-wide so any package would compile, but same-package placement means the extracted functions
  are called unqualified and unimported (`cleanJsonResponse(raw)` at `:17`), exactly as production
  calls them.
- **`org.junit.jupiter.api.Assertions.*` static imports**, not `kotlin.test`. Both styles exist in
  the repo (`InjectionPointExtractorTest.kt` uses `kotlin.test`), but the whole `redact/` package and
  `PassiveAiScannerJsonParsingTest` use JUnit Jupiter assertions. Match `RedactionTest.kt:3-8`, since
  the new tests are assertions about redaction.
- **`fun someBehaviour_someCondition()`** naming with an underscore. `detekt.yml:16-18` excludes
  `**/test/**` from `FunctionNaming`, so both `camelCase` and `snake_after_underscore` pass; the
  scanner package leans on the underscore form.
- **Raw-string test inputs via `"""…""".trimIndent()`** (`:10-15`, `:26-36`) — the same idiom
  `RedactionTest.kt:120-127` uses for HTTP blobs.

**⚠ Naming constraint (hard).** `build.gradle.kts:164-172` excludes `*IntegrationTest`,
`*ConcurrencyTest`, `*BackpressureTest`, `*RestartPolicyTest`, `*SupervisionTest` when
`-PexcludeHeavyTests=true`. `PassiveAiScannerPromptRedactionTest` ends in none of these — safe. Do
not rename it to anything ending in those suffixes.

**Montoya mock analog for the SC2 `parameterLineShape` test.** `InjectionPointExtractorTest.kt:1-37`
is the only in-repo pattern for mocking `ParsedHttpParameter`, and it mocks exactly the three
accessors `formatParamLine` consumes:

```kotlin
import burp.api.montoya.http.message.params.HttpParameterType
import burp.api.montoya.http.message.params.ParsedHttpParameter
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

val cookieParam = mock<ParsedHttpParameter>()
whenever(cookieParam.type()).thenReturn(HttpParameterType.COOKIE)
whenever(cookieParam.name()).thenReturn("session")
whenever(cookieParam.value()).thenReturn("abc")
```

The SC2 shape test asserts that `"${p.name()}=$value (${p.type().name})"` really produces
`JSESSIONID=… (COOKIE)`. `HttpParameterType.COOKIE.name` is the discriminator the redaction rule
keys on, so the mock must use the real enum constant — never a hand-written `"COOKIE"` string, or
the test proves nothing.

**Anti-analog — do NOT copy `PassiveAiScannerConfidenceTest.kt`.** It constructs a real
`PassiveAiScanner` with `mock<MontoyaApi>()` and an 85-field `AgentSettings` literal
(`:42-126`). That is the cost the Wave 0 extraction exists to avoid. If a new test in this file needs
`AgentSettings`, the extraction was drawn at the wrong boundary.

**Callback/rate-limit test analog (for the D-03 assertions, wherever they land).**
`src/test/kotlin/com/six2dez/burp/aiagent/mcp/BlockedRequestReporterTest.kt:14-46`:

```kotlin
private const val T0 = 1_000_000L
private const val T_INSIDE_WINDOW = 1_010_000L
private const val T_AFTER_WINDOW = 1_061_000L

class BlockedRequestReporterTest {
    private val lines = CopyOnWriteArrayList<String>()

    @BeforeEach
    fun registerCapturingEmitter() { lines.clear(); … }

    @AfterEach
    fun clearGlobalEmitter() {
        // globalEmitter is a @Volatile companion field — leaving it registered leaks into other
        // test classes running in the same JVM.
        AuditLogger.registerGlobalEmitter(null)
    }
```

Three things to lift verbatim: named `T0`/`T_INSIDE_WINDOW`/`T_AFTER_WINDOW` file-private constants
for the injected clock; a `CopyOnWriteArrayList` for the capture (the sink fires on the caller's
thread, the assertion reads from the JUnit thread); and an `@AfterEach` that **nulls the global
`@Volatile` sink**, with the leak reason stated in a comment. `RedactionTest.kt:113-117` is the same
discipline for `Redaction.setCustomPatterns(emptyList())` and is where the
`Redaction.truncationLogger = null` reset belongs.

---

### 3. `redact/SafeRegex.kt` — the `replaceAllSafeReporting` sibling (D-14)

**Analog:** `replaceAllSafe` in the same file, `:56-77`, verbatim:

```kotlin
    /**
     * Replaces all matches of [pattern] in [input] with [replacement], bounding the match to
     * [timeoutMs] milliseconds.
     *
     * If the pattern times out (RegexTimeoutException from the DeadlineCharSequence), the
     * ORIGINAL [input] is returned unchanged — fail-open so the redaction pipeline never hangs
     * and never corrupts content on account of a slow pattern.
     */
    fun replaceAllSafe(
        input: String,
        pattern: Pattern,
        replacement: String,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
    ): String =
        try {
            val deadline = System.nanoTime() + timeoutMs * 1_000_000L
            val matcher = pattern.matcher(DeadlineCharSequence(input, deadline))
            matcher.replaceAll(replacement)
        } catch (_: RegexTimeoutException) {
            // Fail-open: give up on this pattern; never corrupt or hang the pipeline.
            input
        }
```

Shape the sibling must match:

| Convention | Evidence |
|---|---|
| **KDoc `/** */` with `[param]` references** — the opposite of `PassiveAiScannerPrompts.kt`'s `//` style | `:50-53` (`DEFAULT_TIMEOUT_MS`), `:56-63`, `:79-88` |
| **The KDoc states the failure contract in prose and names the direction** (`— fail-open so …`) | `:60-62`. The sibling's KDoc must state the mirror-image contract: `timedOut` is the *only* reliable signal, because `text == input` in both the "no matches" and the "timed out" cases |
| **Expression body (`= try { … }`)**, not a block body | `:69-77`, and `isPatternSafe` at `:92-112` |
| **`timeoutMs: Long = DEFAULT_TIMEOUT_MS` as the last, defaulted parameter** | `:68`, `:91` |
| **`catch (_: RegexTimeoutException)` with the underscore-named binder and an inline `//` comment on the recovery line** | `:74-76`, `:110-111` |
| **The deadline arithmetic is `System.nanoTime() + timeoutMs * 1_000_000L`, spelled out at each site** — no shared helper | `:71`, `:104` |
| **Requirement tags in comments** (`PRIV-02 / SC3`, `WR-01`, `T-13-06`) | `:52`, `:87`, `:96` — the new API should carry `PRIV-06 / D-14` |
| **`DeadlineCharSequence` is `private class` at file scope (`:30`), `RegexTimeoutException` is `internal class` (`:25`)** | The sibling **must** live inside `object SafeRegex` in this file — `Redaction` cannot reach `DeadlineCharSequence` to reimplement the deadline. This is why D-14 is an API addition rather than a `Redaction`-local change. |

**Blast radius — exactly one production caller.** Verified by grep across `src/main` and `src/test`:

- `redact/Redaction.kt:272` — `out = SafeRegex.replaceAllSafe(out, p, "[REDACTED]")` (the custom-pattern loop)
- `redact/SafeRegexTest.kt:41` and `:72` — tests only

Nothing else calls it. RESEARCH's recommended refactor (make `replaceAllSafe` delegate to
`replaceAllSafeReporting(...).text`) therefore touches one production line and keeps the fail-open
assertion at `SafeRegexTest.kt:44` green byte-for-byte:

```kotlin
assertEquals(input, result, "On timeout replaceAllSafe must return the original input unchanged (fail-open)")
```

**Test analog for the new `timedOut` coverage:** `SafeRegexTest.kt:32-46`
(`catastrophicPatternTimesOutAndReturnsInput`) — reuse its input (`"a".repeat(2_000) + "!"`), its
pattern (`Pattern.compile("(a+)+\$")`), and its `elapsed < 200L` wall-clock guard; add
`assertTrue(result.timedOut, …)`.

---

### 4. `redact/Redaction.kt` — the diagnostic-callback seam (D-03)

Two separate analogs are needed: one for *the settable field*, one for *the rate limiter*. Both are
already in-repo; D-03 explicitly forbids inventing a third limiter.

**4a. The settable `@Volatile` sink — `backends/BackendDiagnostics.kt:11-18`:**

```kotlin
object BackendDiagnostics {
    @Volatile
    var output: ((String) -> Unit)? = null

    @Volatile
    var error: ((String) -> Unit)? = null
```

**4b. The one existing mutable field on `Redaction` — `Redaction.kt:120-142`, verbatim:**

```kotlin
    // (PRIV-02) Custom user patterns compiled by setCustomPatterns. Volatile so writes from the
    // EDT (save) are immediately visible to the redaction thread (apply) without full synchronization.
    @Volatile
    private var compiledCustomPatterns: List<Pattern> = emptyList()

    /**
     * Sets the list of user-supplied custom redaction patterns. Each string is compiled as a
     * java.util.regex.Pattern; entries that fail to compile (PatternSyntaxException) are silently
     * dropped. Passing an empty list clears all custom patterns.
     *
     * Call this from applyAndSaveSettings after the persisted list has been validated by
     * SafeRegex.isPatternSafe so the patterns in this list are already known-safe.
     */
    fun setCustomPatterns(patterns: List<String>) {
```

Note the two-comment discipline: a `//` comment on the field explaining **why `@Volatile`** (naming
the writing thread and the reading thread), and a `/** */` KDoc on the setter naming **where it is
called from**. The new `truncationLogger` must carry both — writer = EDT/startup, reader = scanner
and MCP threads. `Redaction.kt` mixes `//` (fields, regexes, algorithm notes) and `/** */`
(public entry points); follow that split rather than picking one.

**4c. Production wiring — `App.kt:62-63`, the exact analog for a `@Volatile var` lambda sink:**

```kotlin
        BackendDiagnostics.output = { api.logging().logToOutput(it) }
        BackendDiagnostics.error = { api.logging().logToError(it) }
```

**4d. Where the existing `Redaction` state is seeded/refreshed** — the new callback needs the same
treatment (seed once at startup; no save-time refresh needed since the sink never changes):

`App.kt:84-90`:

```kotlin
        // PRIV-02 / CR-01: seed the redaction engine with persisted custom patterns so they are
        // active immediately on launch — NOT only after the user re-saves Settings. […]
        Redaction.setCustomPatterns(settings.customRedactionPatterns)
```

`ui/SettingsPanelSettingsIO.kt:472-475`:

```kotlin
    // PRIV-02: push validated custom patterns into the live redaction pipeline so edits
    // take effect without a restart (per 13-RESEARCH A7 / Open Question 1).
    com.six2dez.burp.aiagent.redact.Redaction
        .setCustomPatterns(updated.customRedactionPatterns)
```

Note the fully-qualified call in `SettingsPanelSettingsIO.kt` (that file does not import `Redaction`)
— if the planner adds anything there, match the local style rather than adding an import.
**D-05 changes neither of these two sites**; they are shown so the planner can place the
`truncationLogger` wiring beside `App.kt:62` (with the other diagnostics sinks) rather than at
`App.kt:90`, and can state in the plan that the settings path is deliberately untouched.

**4e. The rate limiter — `scanner/PassiveAiScannerAnalysis.kt:825-835`, verbatim:**

```kotlin
internal fun PassiveAiScanner.maybeLogBackoff(
    nowMs: Long,
    untilMs: Long,
) {
    val prev = lastBackoffLogTime.get()
    if (nowMs - prev < BACKOFF_LOG_INTERVAL_MS) return
    if (lastBackoffLogTime.compareAndSet(prev, nowMs)) {
        val seconds = ((untilMs - nowMs).coerceAtLeast(0L) / 1000L)
        api.logging().logToOutput("[PassiveAiScanner] AI backend backoff active (${seconds}s remaining)")
    }
}
```

with `private const val BACKOFF_LOG_INTERVAL_MS = 10_000L` at `PassiveAiScannerAnalysis.kt:24` and
the state field `internal val lastBackoffLogTime = AtomicLong(0)` at `PassiveAiScanner.kt:66`.

The four properties to copy exactly: **`nowMs` is a parameter**, never read inside; **read-then-CAS**
(`get()` → compare against the window → `compareAndSet(prev, nowMs)`); **early `return`** when inside
the window; and the **`[Prefix] message` Output-tab line format** (`[PassiveAiScanner] …`,
`[Custom AI Agent] …` at `CliBackend.kt:77`). Pick one prefix for the redaction line and keep it
constant.

**The once-ever alternative, for completeness — `backends/cli/CliBackend.kt:27-28, 75-78`:**

```kotlin
    /** Avoid spamming "Found X CLI" on every isAvailable() call. */
    private val availabilityLogged = AtomicBoolean(false)
    …
        if (available && availabilityLogged.compareAndSet(false, true)) {
            com.six2dez.burp.aiagent.backends.BackendDiagnostics
                .log("[Custom AI Agent] Found $displayName: $executable")
        }
```

D-03 asks for a *window*, so `maybeLogBackoff` is the model and `availabilityLogged` is not — but the
planner should say so explicitly, since CONTEXT.md names both.

**Richer precedent if aggregation counts are wanted — `mcp/McpBlockedRequestReporter.kt:73-76, 88-95, 129-141`:**

```kotlin
    private data class ReasonWindow(
        val lastLoggedAtMs: AtomicLong = AtomicLong(0L),
        val suppressedCount: AtomicLong = AtomicLong(0L),
    )
    …
     * [nowMs] is injected by the caller rather than read from the system clock inside this class — the
     * same convention as `PassiveAiScannerAnalysis.maybeLogBackoff(nowMs, untilMs)` — so the D-09
     * window is assertable without sleeping.
```

Its KDoc explicitly cites `maybeLogBackoff` as the convention source — the same citation belongs in
the new `maybeLogTruncation` KDoc, so the lineage is one hop, not two.

**Threading note for the plan.** `Redaction.apply` is called concurrently from scanner threads and
MCP tool threads (RESEARCH §Decision 5). The limiter state must be `AtomicLong` on the `Redaction`
object, matching `lastBackoffLogTime`; the window loop's own state stays in locals per
CONTEXT.md §"Established Patterns".

---

### 5. `DECISIONS.md` — ADR-14

**Analog:** ADR-13 at `DECISIONS.md:140-151` — the most recent entry, and the closest in kind (it
also amends an in-phase decision and records a residual).

**Structural shape, from the file header at `:3`:** *"Each section follows the ADR (Architecture
Decision Record) shape: context → decision → consequences."*

```markdown
## ADR-13: Coalesce the MCP transport-block audit event for pre-authentication denials (amends D-06)

**Context.** <one paragraph, no bullets — states the prior decision being amended by name and
location, the mechanism as it is in code with file:line references, the threat with CWE ids, and
explicitly rebuts the obvious objection ("off by default is not a mitigation").>

**Decision.** <one paragraph, no bullets — states what changes, names the exact scope, names the
rejected alternative and why, and closes with the amendment authorisation sentence.>

**Consequences.**
- <bullet>
- <bullet>
- Residual: <the honest remaining gap, and why it is deliberately out of scope>
```

Observed conventions to match:

| Convention | Evidence in ADR-13 |
|---|---|
| Heading is `## ADR-N: <sentence-case claim>` — a **claim**, not a topic. Optional `(amends D-xx)` suffix | `:140` |
| Exactly three bold labels: `**Context.**`, `**Decision.**`, `**Consequences.**` — inline at paragraph start, with the trailing period inside the bold | `:142`, `:144`, `:146` |
| Context and Decision are **prose paragraphs, never bulleted**; Consequences is **always a bullet list** | `:142-145` vs `:146-151` |
| Context cites the source decision by phase-file path and decision id, and cites code by `file:line` | `:142` — `.planning/phases/20-.../20-CONTEXT.md`, `App.kt:69`, `McpBlockedRequestReporter.report` |
| The rejected alternative is named **inside the Decision paragraph** ("Explicitly reject the alternative of adding size-capping or rotation …") | `:144` |
| The last consequence bullet begins `Residual:` when a gap is knowingly left open | `:151` |
| Requirement/phase ids appear in the heading or first line (ADR-8 `(SEC-01)`, ADR-9 `(PRIV-01)`, ADR-12 `(CAP-04)`) | `:84`, `:95`, `:129` |

**Content constraints already locked** (do not re-derive): D-08 REFINED requires the title to claim
**"the body stage never fails open"**, not the unqualified form — because the eight header-stage
rules remain unbounded. Following the ADR-9/ADR-12 heading convention, that is roughly
`## ADR-14: The redaction body stage never fails open (PRIV-06)`. The Residual bullet is where the
header-stage gap and the custom-pattern-across-window-boundary gap belong, with the CONCERNS.md
cross-reference.

**CONCERNS.md entry shape** (`.planning/codebase/CONCERNS.md:62-67`), for the two gaps D-08 REFINED
routes there:

```markdown
### Redaction regex coverage gaps

- **Issue:** <what and where, with file:line>
- **Files:** `src/main/kotlin/.../redact/Redaction.kt:56-79`
- **Protocol for tightening:** Add new regex patterns …; add a corresponding test case in
  `src/test/kotlin/.../redact/RedactionTest.kt`; … Do not loosen existing patterns without
  documenting the reason in the PR.
- **Impact:** False negatives (data leakage) only; no false positive risk from adding patterns.
```

Note this existing entry cites `Redaction.kt:56-79`, line numbers that this phase's rewrite will
invalidate — the planner should refresh them in the same edit.

---

### 6–9. Files already fully specified by RESEARCH.md

These have exact in-file analogs and executed reference implementations; do **not** re-derive them.

| File | Analog | Where the code already is |
|---|---|---|
| `redact/Redaction.kt` — cookie rules, `SENSITIVE_KEY_EXPR`, body stage | the existing `apply` (`:229-287`) and its neighbours: `cookieHeaderRegex`/`setCookieHeaderRegex` (`:80-81`), `SENSITIVE_KEYS` (`:86-89`), the three consumer regexes (`:92-118`) | RESEARCH §"Code Examples" 1–6 |
| `scanner/PassiveAiScannerAnalysis.kt:393-402` | — (deletion) | RESEARCH §"Code Examples" 7 |
| `mcp/McpToolContext.kt:59-66` | — (deletion) | RESEARCH §"Code Examples" 7 |
| `config/Defaults.kt` | `MAX_REDACTION_BODY_CHARS` at `:54-58` — a `//` comment block naming the callers it protects, then `const val NAME = 1_000_000` with underscore digit separators, in the flat `object Defaults` | RESEARCH §"Budget constant" |

For `Defaults.kt`, one note the planner should carry: the existing comment at `:57` says *"Bodies
over this limit are skipped entirely — not hung, not partially redacted — per PRIV-02 size-cap
requirement."* That sentence is the fail-open being fixed. It must be rewritten in the same edit, or
the file will document the old behaviour (D-04 says only the KDoc changes; this is that KDoc).

---

### 10. The four D-07 UI strings

All four are single arms of a `when (mode)` whose sibling arms are correct and stay untouched — the
analog for each is literally the line above it.

| File:line | Current text | Sibling to match in tone/length |
|---|---|---|
| `ui/ChatPanel.kt:1146` | `PrivacyMode.OFF -> "Privacy: OFF (no redaction)"` | `:1144-1145` — `"Privacy: STRICT (cookies stripped, tokens redacted, hosts anonymized)"` / `"Privacy: BALANCED (cookies stripped, tokens redacted)"` |
| `ui/components/ContextPreviewDialog.kt:122` | `PrivacyMode.OFF -> "  (no redaction; raw traffic will be sent)"` | `:120-121` — note the **two leading spaces** are part of the string and must be preserved |
| `ui/components/PrivacyPill.kt:41` | `toolTipText = "OFF mode sends raw traffic without redaction."` | `:36` — `"BALANCED mode strips cookies and redacts tokens but keeps hosts."` (sentence case, trailing period, `<mode> mode …` opening) |
| `ui/SettingsPanelActions.kt:251` (and the three risk-combination arms at `:237-249`) | `"<b>Privacy mode is OFF.</b> Raw traffic may reach MCP and prompts."` | same `when` — HTML with `<b>…</b>` on the lead clause, `SubtleNotice.Level.RISK`/`WARN`/`INFO` paired via `to` |

Two structural notes: (a) `SettingsPanelActions.kt` has **four** OFF arms (`:237`, `:241`, `:245`,
`:249`), all of which assert "Raw traffic may reach MCP and prompts" — D-07's rewording has to be
consistent across all four or the panel contradicts itself; (b) D-07's "ideally conditioned on
whether patterns are actually configured" needs a `customRedactionPatterns.isNotEmpty()` read.
`refreshPrivacyNotice` (`:230-236`) already reads live component state
(`privacyMode.selectedItem`, `auditEnabled.isSelected`, `activeAiEnabled.isSelected`) rather than a
settings object, so the conditional belongs there most naturally; `ChatPanel` has `getSettings()`
available; `PrivacyPill.updateMode(mode)` takes **only** the mode and would need a signature change —
flag that as the one place where conditioning is not free.

---

## Shared Patterns

### Kotlin style gates (apply to every `.kt` file in this phase)

**Source:** `build.gradle.kts:188-204`, `detekt.yml`, `.editorconfig`
**Apply to:** all main and test sources

- ktlint 1.5.0, **strict** — `ignoreFailures` is false unless `-PktlintLenient=true`. Multi-parameter
  signatures are one-per-line with a trailing comma; max line length 250 (`.editorconfig` and
  `detekt.yml:13-14` agree).
- detekt with a **committed baseline that must not grow** (QUAL-07). Live thresholds from `detekt.yml`:
  `LongMethod: 80`, `LongParameterList: functionThreshold 10 / constructorThreshold 10`,
  `MaxLineLength: 250`, `FunctionNaming` excluded under `**/test/**`.
- Escape hatch for a new complexity finding is `@Suppress("RuleName")` on the declaration, with
  precedent at `ui/SettingsPanelInit.kt:29`, `ui/SettingsPanelMcpTabs.kt:148`,
  `scanner/PassiveAiScannerAnalysis.kt:170`, `mcp/external/ExternalMcpClientManager.kt:172,302`.
- Build invocation is **always** `JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew <task>`.

### Requirement-tagged comments

**Source:** `Redaction.kt:97`, `:106`, `:253`, `:269-270`; `SafeRegex.kt:52`, `:87`, `:96`;
`McpToolContext.kt:42-47`, `:67-69`
**Apply to:** every non-trivial new block in `redact/` and `scanner/`

Every substantive comment opens with the requirement or work-item id it implements —
`// (PRIV-02) …`, `// PRIV-03 (Phase 15): …`, `// REL-02/SC5b: …`, `// 07-03 D-03: …`,
`// WR-01: …`, `// T-13-06`. New code should carry `PRIV-05` / `PRIV-06` / `D-0x` / `SC-x` tags in
the same position. This is how the codebase stays traceable to `.planning/`, and reviewers look for it.

### Comment-style split by file

**Source:** `redact/Redaction.kt`, `redact/SafeRegex.kt` vs `scanner/PassiveAiScannerPrompts.kt`
**Apply to:** all new declarations

`redact/` uses `/** */` KDoc on public entry points and `//` on fields/regexes/algorithm notes.
`scanner/PassiveAiScannerPrompts.kt` uses **`//` exclusively** (zero KDoc blocks in the file).
Match the destination file, not a global rule.

### AWT-free contract

**Source:** `SafeRegex.kt:21-22` (`// AWT-free: no java.awt / javax.swing imports so Phase 15's
scanner-side tripwire can reuse this file headless`), `PassiveAiScannerPrompts.kt:6`,
`PassiveAiScannerAnalysis.kt:20`
**Apply to:** `redact/*`, `scanner/PassiveAiScannerPrompts.kt`, `scanner/PassiveAiScannerAnalysis.kt`

Three of the four files this phase edits most heavily carry an explicit AWT-free banner. D-03's
"no `AuditLogger` in `redact/`" is the same constraint one level up. Keep the banners; do not add an
import that violates them.

### Test-suite classification

**Source:** `build.gradle.kts:160-186`
**Apply to:** the one new test file

`-PexcludeHeavyTests=true` drops `*IntegrationTest`, `*ConcurrencyTest`, `*BackpressureTest`,
`*RestartPolicyTest`, `*SupervisionTest` from the PR gate (they run in `nightlyRegressionTest`
instead). `PassiveAiScannerPromptRedactionTest` is safe. Every `Test` task is
`finalizedBy(jacocoTestReport)` (`:214-216`), so a test run always produces coverage — no extra step.

### Global-state reset in tests

**Source:** `redact/RedactionTest.kt:113-117`, `mcp/BlockedRequestReporterTest.kt:42-46`
**Apply to:** any test touching `Redaction.setCustomPatterns` or `Redaction.truncationLogger`

```kotlin
    @AfterEach
    fun resetCustomPatterns() {
        // Prevent custom-pattern bleed across tests: reset after each test.
        Redaction.setCustomPatterns(emptyList())
    }
```

`Redaction` is a singleton `object`; every `@Volatile` field added by this phase becomes cross-test
state in a shared JVM. Both analogs pair the reset with a one-line comment naming the leak — copy
that, since a silent `@AfterEach` reads as boilerplate and gets deleted.

---

## No Analog Found

None. Every file in this phase has an existing in-repo analog, in most cases inside the same file.
Two areas are *new shapes* rather than missing ones, and are called out above so the planner does not
have to search:

| Concern | Why it looks analog-less | Resolution |
|---|---|---|
| A `SafeRegex` API that reports timeout | No existing function in the repo returns a `(text, flag)` result | Model the **function** on `replaceAllSafe` (`SafeRegex.kt:56-77`) and the **result type** on `BackendDiagnostics.RetryEvent` (`BackendDiagnostics.kt:4-9`) — the repo's only precedent for a small `data class` carrying a diagnostic payload out of a utility object |
| A 14-parameter extracted builder | Nothing in `src/main` has a 14-parameter *function* (the 14 baselined `LongParameterList` findings are constructors and DTO-shaped calls) | Not a missing pattern — a threshold violation. See §1's three resolutions; option 1 (`@Suppress`) has direct precedent |

---

## Metadata

**Analog search scope:**
`src/main/kotlin/com/six2dez/burp/aiagent/{redact,scanner,mcp,backends,config,ui}/`,
`src/test/kotlin/com/six2dez/burp/aiagent/{redact,scanner,mcp}/`,
plus repo-root `DECISIONS.md`, `build.gradle.kts`, `detekt.yml`, `.editorconfig`,
`.planning/codebase/CONCERNS.md`.

**Files read in full:** `redact/Redaction.kt` (334), `redact/SafeRegex.kt` (121),
`scanner/PassiveAiScannerPrompts.kt` (253), `backends/BackendDiagnostics.kt` (57),
`mcp/McpToolContext.kt` (110), `config/Defaults.kt` (75), `DECISIONS.md` (151), `detekt.yml` (19),
`scanner/PassiveAiScannerJsonParsingTest.kt` (88), `scanner/PassiveAiScannerConfidenceTest.kt` (127),
`redact/SafeRegexTest.kt` (79).

**Files read in targeted ranges:** `scanner/PassiveAiScannerAnalysis.kt` (1-40, 195-419, 805-835),
`redact/RedactionTest.kt` (1-40, 100-140, 305-398), `mcp/McpBlockedRequestReporter.kt` (1-140),
`mcp/BlockedRequestReporterTest.kt` (1-80), `scanner/InjectionPointExtractorTest.kt` (1-45),
`App.kt` (62-63, 78-100), `ui/SettingsPanelSettingsIO.kt` (468-482),
`backends/cli/CliBackend.kt` (20-40, 68-84), `ui/ChatPanel.kt` (1140-1152),
`ui/components/ContextPreviewDialog.kt` (112-132), `ui/SettingsPanelActions.kt` (228-258),
`ui/components/PrivacyPill.kt` (30-50), `build.gradle.kts` (156-224),
`.planning/codebase/CONCERNS.md` (62-84).

**Greps run:** `replaceAllSafe|SafeRegex.` (all sources), `ParsedHttpParameter|HttpParameterType`,
`org.mockito.kotlin` in `scanner/`, `lastBackoffLogTime|BACKOFF_LOG_INTERVAL_MS`, `@Suppress(`,
`LongParameterList` in `detekt-baseline.xml`, `BackendDiagnostics.output` in `App.kt`,
`no redaction` / `OFF mode sends raw traffic` / `Privacy mode is OFF` in `ui/`.

**Pattern extraction date:** 2026-08-11
