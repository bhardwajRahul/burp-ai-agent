---
phase: 21-redaction-completeness
reviewed: 2026-08-12T12:29:03Z
depth: deep
review_round: 2
supersedes: .planning/phases/21-redaction-completeness/21-REVIEW.md
diff_base: bf76cf2
files_reviewed: 7
files_reviewed_list:
  - src/main/kotlin/com/six2dez/burp/aiagent/redact/Redaction.kt
  - src/main/kotlin/com/six2dez/burp/aiagent/redact/SafeRegex.kt
  - src/main/kotlin/com/six2dez/burp/aiagent/config/Defaults.kt
  - src/main/kotlin/com/six2dez/burp/aiagent/scanner/PassiveAiScannerPrompts.kt
  - src/main/kotlin/com/six2dez/burp/aiagent/scanner/PassiveAiScannerAnalysis.kt
  - src/test/kotlin/com/six2dez/burp/aiagent/redact/RedactionTest.kt
  - src/test/kotlin/com/six2dez/burp/aiagent/scanner/PassiveAiScannerPromptRedactionTest.kt
findings:
  critical: 1
  warning: 8
  info: 4
  total: 13
status: issues_found
---

# Phase 21: Code Review Report (round 2 — gap-closure re-review)

**Reviewed:** 2026-08-12T12:29:03Z
**Depth:** deep
**Files Reviewed:** 7
**Status:** issues_found

## Summary

Three of the four blockers from `21-REVIEW.md` are genuinely closed, and I verified each one by
driving the **compiled shipped classes** (`build/classes/kotlin/main`) from a JDK 21 harness rather
than by reading the diff or trusting a SUMMARY. `./gradlew test` for `RedactionTest`,
`PassiveAiScannerPromptRedactionTest` and `SafeRegexTest` is green (37/37 in `RedactionTest`), and
`ktlintCheck` + `detekt` are clean.

Evidence for the closures, all re-derived:

- **CR-01** — a blank first entry and a blank mid-list entry no longer collapse or truncate the span;
  a `=== `-shaped entry is neutralised at the emitter. Live-verified end to end.
- **CR-03** — 512 KB / 32 768 header occurrences now complete in ~10 ms in a verbatim transcription
  and ~71 ms through the real `Redaction.apply`, against the 2 631 ms I measured pre-fix. The pass is
  a genuine single monotone partition: 200 randomised adversarial trials rebuild the input exactly.
- **CR-04** — `splitPoint("x".repeat(1000))` now returns 500 instead of 0; the depth-4 ladder reaches
  ~125 KB pieces on a 2 MB newline-free window; a live 4 MB minified-JSON body is scanned in place
  with `"api_key":"[REDACTED]"` surviving.
- **WR-01** — the hand-factored `SENSITIVE_KEY_WORDS` is **provably** equivalent to
  `NAIVE_KEY_EXPR_FOR_TEST`: 900 000 randomised key names across all three consumer contexts plus an
  exhaustive sweep of every key of length ≤ 4 over a 14-character alphabet produced **zero**
  divergences, and group counts match (2 and 2) so Pitfall 7 holds.
- **No fail-open was reintroduced.** `replaceAllSafe` still has zero production callers; every body
  rule on both paths branches on `SafeReplaceResult.timedOut`.

**CR-02 is only partially closed.** The three-line and blank-gap shapes are fixed — 24-shift sweeps
show 0 leaks — but a **two-line** shape is not, and it is not the residual that was recorded. When a
window cut lands inside an *open* quoted JSON value, `isJsonPairBoundaryRisk` cannot see it (the line
ends with a value character, not `"` or `:`), the lookahead never engages, and the pair is cut in
half. Driving the real `Redaction.apply` over 40 boundary alignments of a 1 MB body, **6 alignments
leak the secret on the windowed path with no drop marker, while the single-pass path redacts it**.
That is the same statement that made CR-02 a blocker, and `Redaction.kt:777-779`, `DECISIONS.md:176`
and `CONCERNS.md:73` all now assert coverage this shape does not have.

The remediation also introduced two new attacker-facing behaviours in the cookie rule that nothing
tests: `MAX_COOKIE_SECTION_LINES = 16` silently *stops redacting* past line 16 (verified: entries
17-20 of a 20-entry section reach the output verbatim), and because blank lines are no longer
terminators, a `=== COOKIES ===` planted in a response body now deterministically blinds the next
16 lines of that body from the AI regardless of paragraph structure.

Finally, `newlineFreeOversizeBodyIsScannedNotDestroyed` measures **1.93–2.02 s against a 2 000 ms
budget** on this machine (Apple Silicon / JDK 21 / JaCoCo, three consecutive runs). Asked plainly: yes,
the suite has a timing-dependent gate and it will flake on a slower CI runner.

Deliberate decisions listed in the review brief — ADR-14 test inversions, custom patterns under `OFF`,
the eight unbounded header-stage rules, the CR-02 cap not dropping the window, the seven accepted
`token`-driven over-redactions, the `@Suppress` annotations, the HKDF block — were verified as
intentional and are **not** reported.

---

## Verdicts on `21-REVIEW.md` findings

| ID | Verdict | Evidence |
|----|---------|----------|
| **CR-01** | **CLOSED** | `cookieSectionEnd` (`Redaction.kt:234-253`) starts its walk at `headerNewline + 1` and advances past blank lines instead of terminating. Live: `=== COOKIES ===\n\nJSESSIONID=…\n\nabtest_bucket=…\n\n=== PARAMETERS ===` redacts **both** values and leaves `q=red running shoes (URL)` intact. The `=== `-shaped-entry trigger is neutralised at the emitter by `sanitizeCookieSectionEntries` (`Redaction.kt:293-299`), wired at `PassiveAiScannerPrompts.kt:66` and `:141`; both call sites are individually mutation-guarded (deleting either fails a different named test). See W-01/W-02/W-03 for what the fix newly exposes. |
| **CR-02** | **PARTIALLY CLOSED** | The three-line shape is fixed: 24-shift sweeps of `"token"` / `:` / `"value"` and of the blank-gap variant leak **0** times against a verbatim transcription, and `windowedScanRedactsJsonPairAcrossEveryBoundaryAlignment` is green. But a two-line shape — the cut landing inside an open quoted value — still leaks live at 6 of 40 alignments with `dropMarker=false`. See **CR-01 (new)**. |
| **CR-03** | **CLOSED** | `redactCookieSections` (`Redaction.kt:179-210`) is one `StringBuilder` and one monotone cursor; `nextSectionRegex` is gone, replaced by an O(1) `startsWith(NEXT_SECTION_PREFIX, p)` at a known line start. Measured 524 288 chars / 32 768 occurrences: **10 ms** transcribed, **71 ms** through `Redaction.apply` (was 2 631 ms). 200 randomised adversarial partition trials rebuild the input byte-exactly; 200 000 randomised cookie-token fuzz inputs raise no exception and produce no spurious marker. Fail-closed on deadline verified by `cookieSectionDeadlineFailsClosed`. See W-05 for the limit of the budget. |
| **CR-04** | **CLOSED** | `safeCutPoint` (`Redaction.kt:1042-1053`) always returns a strictly interior index for `window.length >= 2`; `splitPoint("x".repeat(1000)) == 500`, `splitPoint("&&&&") == 3`, `splitPoint("\n") == 0` (terminating drop). The depth-4 ladder on a 2 MB minified-JSON window cuts at 1 000 022 → 500 016 → 250 015 → 125 008, all landing just after `,`/`}`. Live 4 MB newline-free body: output 99 % of input, secret gone, `"api_key":"[REDACTED]"` present. See W-04 for the end-to-end gate's timing fragility. |
| **WR-01** | **CLOSED** (maintainer decision, verified) | `BROAD_WORDS` removed from the free-containment rule; `status_code`, `errorCode`, `primary_key`, `cache_key`, `public_key` and 27 others survive, `api_key`/`access_code`/`signing_key` and 21 others still redact, both pinned in `wr01BroadWordBenignKeys` (32) and `wr01CredentialBroadWordKeys` (24). The factoring is not merely asserted — I ran a differential fuzz of the shipped vs naive expression: 400 000 random keys × 3 consumer contexts, 500 000 additional camelCase-heavy keys, and an exhaustive sweep of all keys of length ≤ 4 over `acekinoprstuy_`. **Zero divergences.** See W-06 for why the committed guard is weaker than that. |
| **WR-02** | **STILL OPEN** (deliberately deferred) | `SettingsPanelActions.kt:243` still reads `customPatternsArea.text`. File untouched since `bf76cf2`. Deferral recorded at `21-12-PLAN.md` §Deferred. |
| **WR-03** | **STILL OPEN** (deliberately deferred) | `SafeRegex.replaceAllSafe` (`SafeRegex.kt:105-110`) is unchanged and still has **zero** production callers — `grep -rn "replaceAllSafe\b" src/main` returns the declaration plus three warning comments in `Redaction.kt`. Deferral recorded. |
| **WR-04** | **STILL OPEN, exposure increased** | `App.kt:68` sets `Redaction.truncationLogger`; `App.shutdown()` (`:202-232`) still clears `BackendDiagnostics.retry`, `AuditLogger`'s emitter and `Redaction.clearMappings()` but not the logger. `maybeLogTruncation` (`Redaction.kt:626-639`) still has no `runCatching`. This phase added a **third** call site (`Redaction.kt:194`, inside `redactCookieSections`), so the teardown window is now reachable from the header stage as well as from two body-stage paths. See W-08. |
| **WR-05** | **CLOSED** | `oversizeBodyFailsClosed` (`RedactionTest.kt:1444-1451`) and `subWindowBodyFailsClosed` (`:1503-1510`) now assert `!output.contains("a".repeat(2_000))` and `output.length < body.length / 2`. Both are exact for these fixtures — every window must be dropped, so a surviving 2 000-'a' run can only come from an unscanned emission. This genuinely kills the "emit three windows unscanned, mark the fourth" mutation. |
| **WR-06** | **CLOSED** (narrower than its name) | Two sweeps added (`RedactionTest.kt:1736`, `:1780`), each 24 shifts = one full `PAD_LINE_CHARS` period, each with the anti-vacuity `assertFalse(contains("REDACTION INCOMPLETE") …)` third leg that stops a dropped window from masquerading as a redaction. The sweeps are real. They cover **one fixture family** (key/colon/value on separate lines), which is why the two-line variant in **CR-01 (new)** survived them — so this closes the specific gap CR-02 fell through, not "the D-01 invariant". |
| **WR-07** | **STILL OPEN** (deliberately deferred) | `SafeRegex.isPatternSafe` still validates against the single `ADVERSARIAL_PROBE` (`SafeRegex.kt:153`); `App.kt:93` still seeds persisted patterns without re-validation. File untouched. Deferral recorded. |

---

## Narrative Findings (AI reviewer)

No `<structural_findings>` block was supplied for this review, so all findings below are narrative.

---

## Critical Issues

### CR-01: CR-02 is not closed — a JSON pair whose quoted value straddles the window cut still leaks on the windowed path

**File:** `src/main/kotlin/com/six2dez/burp/aiagent/redact/Redaction.kt:1197-1200`
(`isJsonPairBoundaryRisk`), driving `:1111-1151` (`windowEnd`); false coverage claim at `:777-779`;
residual mis-recorded at `DECISIONS.md:176` and `.planning/codebase/CONCERNS.md:73`.

**Issue:**
The CR-02 fix decides whether to extend a window by asking whether the **last line of the window ends
with `:` or `"`**:

```kotlin
private fun isJsonPairBoundaryRisk(line: String): Boolean {
    val trimmed = line.trimEnd()
    return trimmed.endsWith(":") || trimmed.endsWith("\"")
}
```

That predicate detects a cut sitting in the `\s*` **around the colon**. It cannot detect a cut sitting
**inside the value**, because `jsonSecretKeyRegex`'s value alternative is `"[^"]*"` and `[^"]*`
matches newlines. When the value's opening quote is on one line and its closing quote is on the next,
the window's last line ends with an ordinary value character, `isJsonPairBoundaryRisk` returns false,
`pairMayBeInFlightAt` returns false, the loop at `:1135` never runs even once, and the pair is cut in
half. Neither half can match, so the value is emitted verbatim.

This is **not** the recorded residual. `DECISIONS.md:176` and `CONCERNS.md:73` both say the residual is
"a pair spread over **more than eight lines**", and `Redaction.kt:777-779` states outright:

> "the one built-in whose match can span newlines, `jsonSecretKeyRegex`, is covered by a bounded
> lookahead of `MAX_JSON_BOUNDARY_LOOKAHEAD_LINES` lines in `windowEnd`."

The shape below is **two** lines. The cap is irrelevant to it; the lookahead never starts.

Reproduced against the **compiled shipped `Redaction` object** (not a transcription), sweeping the
pair across the deterministic 1 000 000-char cut over 40 alignments, `PrivacyMode.STRICT`, no custom
patterns:

```
=== LIVE: CR-02 residual, JSON string value with a raw newline across the cut ===
  LEAK at extra=31 dropMarker=false
  LEAK at extra=32 dropMarker=false
  LEAK at extra=33 dropMarker=false
live windowed leaks over 40 alignments = 6
single-pass control leaks = false
```

`dropMarker=false` is the load-bearing part: this is a leak, not a fail-closed drop. The
identical body below the window width is redacted (`single-pass control leaks = false`), so this is
a redaction bypass keyed only on payload size and alignment — verbatim the property that made CR-02 a
blocker.

The window tail / next-window head at the divergence:

```
  window tail: qqqqqqqqqqqqqqqqqqqq\n  "api_key": "SEC-AAAAA\n
  next  head : more"\nyyyyyyyyyyyyyyyyyyy\nyyyyyyyyyyyyyyyyyyy
```

**Reachability.** `McpToolContext.redactIfNeeded` (`McpToolContext.kt:59-75`) calls `Redaction.apply`
on serialized tool output capped at `maxBodyBytes`, whose default is `2 * 1024 * 1024`
(`AgentSettings.kt:1171`) — twice `MAX_REDACTION_BODY_CHARS`. The windowed path is therefore a
default-configuration path, response bodies are attacker-controlled, and the cut position is
deterministic given a known prefix length, so the alignment is craftable rather than accidental.
The trigger is narrower than the original CR-02 (it needs a raw newline inside the quoted value, i.e.
malformed JSON, a wrapped log dump, or non-JSON text carrying a `"key": "value` shape) — but the
redactor operates on raw bytes, never on validated JSON, and it is the pipeline's own single-pass path
that establishes the value *is* a secret worth redacting.

**Fix:** make the risk predicate model "the line ends inside an unterminated quoted string", which is
the state `[^"]*` is actually in. This is a character count on the line already in hand, so it costs
nothing extra and adds no dependency:

```kotlin
// (PRIV-06) CR-02: a line that ends with an ODD number of unescaped double quotes leaves a
// "[^"]*" value OPEN across the newline, so jsonSecretKeyRegex's match is still in flight even
// though the line ends with an ordinary value character.
private fun endsInsideOpenQuotedValue(line: String): Boolean {
    var quotes = 0
    var i = 0
    while (i < line.length) {
        when (line[i]) {
            '\\' -> i++          // skip the escaped character
            '"' -> quotes++
        }
        i++
    }
    return quotes % 2 == 1
}

private fun isJsonPairBoundaryRisk(line: String): Boolean {
    val trimmed = line.trimEnd()
    return trimmed.endsWith(":") || trimmed.endsWith("\"") || endsInsideOpenQuotedValue(line)
}
```

`isJsonPairBoundaryContinuation` inherits it for free. The existing eight-line cap still bounds the
extension, so this trades no denial of service for the fix.

Then extend the sweep so the class is guarded rather than rediscovered — the fixture family is the
only thing separating WR-06's sweep from having caught this:

```kotlin
@Test
fun windowedScanRedactsJsonPairWhoseValueStraddlesTheCut() {
    val pair = "  \"api_key\": \"STRADDLE-SECRET-2\ntail-of-value\"\n"
    for (shift in 0 until BOUNDARY_SWEEP_SHIFTS) { /* same three assertions as the sibling sweeps */ }
}
```

Whatever is chosen, `Redaction.kt:777-779`, `DECISIONS.md:176` and `CONCERNS.md:73` must stop claiming
that `jsonSecretKeyRegex` is covered up to the cap — for this shape the cap is never reached, and a
record that overstates its own coverage is how CR-02 survived the first review.

---

## Warnings

### W-01: `MAX_COOKIE_SECTION_LINES` silently stops redacting past line 16, and nothing couples it to `COOKIES_MAX_COUNT`

**File:** `src/main/kotlin/com/six2dez/burp/aiagent/redact/Redaction.kt:108-116` (the constant),
`:243-250` (the walk); producer bound at
`src/main/kotlin/com/six2dez/burp/aiagent/scanner/PassiveAiScannerAnalysis.kt:26` (`COOKIES_MAX_COUNT = 6`).

**Issue:** the nine-line comment on `MAX_COOKIE_SECTION_LINES` describes it in one direction only —
"what bounds the over-redaction blast radius of such a plant to 16 lines". It never states the other
direction, which is that **cookie entries beyond line 16 of a section are not redacted at all**.
Verified against the compiled shipped code with a 20-entry section:

```
=== LIVE: cookie section beyond MAX_COOKIE_SECTION_LINES ===
leaked cookie entries: ck16 ck17 ck18 ck19
```

Today that is unreachable through the scanner, because `COOKIES_MAX_COUNT` is 6. The defect is that
**nothing enforces that relationship.** Raising `COOKIES_MAX_COUNT` — an obvious, innocuous-looking
one-constant change in a different file, in a different package, with a name that gives no hint the
redactor depends on it — reopens PRIV-05 for every entry past the sixteenth, and the entire suite
stays green: `cookieSectionValuesRedactedPerName`, `cookieSectionBlankEntriesDoNotCollapseSpan` and
`emittedCookieSectionValuesAreRedacted_sc1` all use six entries or fewer, and no test anywhere
constructs a section longer than 16 lines.

This is the same failure mode the phase already paid for twice: an unasserted coupling between an
emitter constant and a redactor rule.

**Fix:** assert the coupling and state the direction. `COOKIES_MAX_COUNT` is `private` to
`PassiveAiScannerAnalysis.kt`, so make the redactor's bound the authority and derive the emitter's:

```kotlin
// Redaction.kt — state BOTH directions.
// UNDER-redaction direction, stated because it is the dangerous one: a section longer than this is
// redacted only up to line MAX_COOKIE_SECTION_LINES and every entry below leaks. The emitter is
// bounded to COOKIES_MAX_COUNT entries, which MUST stay strictly below this value.
private const val MAX_COOKIE_SECTION_LINES = 16
```

plus a regression test that is red the moment the two drift:

```kotlin
@Test
fun everyEntryOfAMaximalCookieSectionIsRedacted() {
    val entries = (0 until COOKIES_MAX_COUNT_MIRROR).map { "ck$it=OPAQUE_ZZ${it}_END" }
    val blob = Redaction.COOKIE_SECTION_HEADER + "\n" + entries.joinToString("\n") + "\n"
    val out = Redaction.apply(blob, RedactionPolicy.fromMode(PrivacyMode.STRICT), stableHostSalt = "salt")
    for (i in 0 until COOKIES_MAX_COUNT_MIRROR) {
        assertFalse(out.contains("OPAQUE_ZZ${i}_END"), "cookie entry $i must be redacted at the section bound")
    }
}
```

---

### W-02: a planted `=== COOKIES ===` now blinds a fixed 16 lines of an attacker's own response body, regardless of blank lines

**File:** `src/main/kotlin/com/six2dez/burp/aiagent/redact/Redaction.kt:149-152` (the accepted-residual
note), `:243-250` (the walk).

**Issue:** the residual note frames the change as a tightening — "`MAX_COOKIE_SECTION_LINES` now bounds
that blast radius to 16 lines, where it was previously 'to the next blank line'". For dense content
with no blank lines that is true. For ordinary paginated HTML, log output or pretty-printed config —
where blank lines occur every two or three lines — it is a **widening**, and it converts a
content-dependent bound into a deterministic 16-line one an attacker can rely on.

Verified live through `Redaction.apply`:

```
=== RESPONSE BODY ===
<html>
=== COOKIES ===
debug=[REDACTED]
sql=[REDACTED]
version=[REDACTED]
internal=[REDACTED]
...                     <- 16 lines, all values destroyed
l17=SURVIVES
```

The values a passive vulnerability scanner most needs — `debug=verbose_stack_trace_here`,
`sql=SELECT * FROM users WHERE id=1`, `internal=10.0.0.5` — are exactly what a `name=value` line rule
removes. A target that wants to hide a region of its response from the AI now has a reliable
16-line primitive, and the same primitive is reachable through any section whose lines the emitter
does not sanitise: `=== PARAMETERS ===` entries (`formatParamLine` at `PassiveAiScannerPrompts.kt:34-38`
emits `name=value (TYPE)` with no sanitiser) and both header sections all pass attacker-controlled
text into the blob.

This is over-redaction, not a leak — but WR-01 was raised, accepted and fixed on precisely the ground
that removing analytically load-bearing content from a security prompt is a functional regression, not
a cosmetic one. The same standard applies here.

**Fix:** require the header to be *framed* the way the emitter frames it, which a body line cannot
reproduce. The emitter always writes the header on its own line preceded by a blank line
(`buildScanMetadataText` closes every section with `appendLine()`), so:

```kotlin
// Only treat the header as genuine when it stands alone on its line AND is preceded by a blank
// line — the shape buildScanMetadataText always emits and a mid-body occurrence never has.
private fun isFramedCookieHeader(text: String, h: Int): Boolean {
    val lineStart = text.lastIndexOf('\n', h - 1) + 1
    if (lineStart != h) return false
    val afterHeader = h + COOKIE_SECTION_HEADER.length
    if (afterHeader < text.length && text[afterHeader] != '\n' && text[afterHeader] != '\r') return false
    val prevLineEnd = h - 1
    if (prevLineEnd <= 0) return true
    val prevLineStart = text.lastIndexOf('\n', prevLineEnd - 1) + 1
    return text.substring(prevLineStart, prevLineEnd).isBlank()
}
```

If that is judged to weaken D-10's decoy defence, the alternative is to shrink the bound to
`COOKIES_MAX_COUNT + 2` so the blast radius matches what the emitter can actually produce, and to say
so in the residual note. Either way the note should stop describing the change as a pure tightening.

---

### W-03: `sanitizeCookieSectionEntries` has no direct test, and its CR/LF limb is completely unguarded

**File:** `src/main/kotlin/com/six2dez/burp/aiagent/redact/Redaction.kt:293-299`.

**Issue:** the function has three limbs, and its KDoc calls the middle one load-bearing —
"every CR and LF inside an entry becomes a single space, **so one entry can never become two emitted
lines**". Without it, an entry such as `a=1\n=== FOO ===` is emitted by `appendLine` as two lines, the
second of which sits at a line start and terminates the cookie span — CR-01's second trigger,
reopened in full.

There is no test for it. `grep -rn "sanitizeCookieSectionEntries" src/test` returns three matches, all
inside **comments**. The two named guards both use the literal `"=== FOO ==="` with no embedded
newline:

- `PassiveAiScannerPromptRedactionTest.poisonedCookieHeaderCannotTerminateTheCookieSection:283-288`
- `PassiveAiScannerPromptRedactionTest.cookieSectionEntriesAreSanitizedAtTheEmitter:328-333`

Delete `.replace('\r', ' ').replace('\n', ' ')` from line 297 and the entire suite stays green while
the control is gone. Asked the brief's question — "what else in the pipeline would catch this same
input?" — the answer for the CR/LF limb is *nothing*.

The path is reachable: `cookieSectionLines` splits the raw `Cookie:` header value and trims each
element, and a hand-edited Repeater/Intruder request with an embedded newline in a `Cookie:` header
is entirely ordinary Burp usage.

**Fix:** a direct unit test on the exported function, which is cheap because it is pure:

```kotlin
@Test
fun sanitizeCookieSectionEntriesNeutralisesEveryFramingPrimitive() {
    val out = Redaction.sanitizeCookieSectionEntries(
        listOf("a=1\n=== FOO ===", "b=2\r=== BAR ===", "   ", "", "=== BAZ ===", "c=3"),
    )
    assertEquals(listOf("a=1 === FOO ===", "b=2 === BAR ===", " === BAZ ===", "c=3"), out)
    assertTrue(out.none { it.contains('\n') || it.contains('\r') }, "one entry must never become two lines")
    assertTrue(out.none { it.startsWith("===") }, "no entry may forge a section boundary")
}
```

---

### W-04: `newlineFreeOversizeBodyIsScannedNotDestroyed` is a wall-clock gate running at 97-101 % of the budget it races

**File:** `src/test/kotlin/com/six2dez/burp/aiagent/redact/RedactionTest.kt:1661-1702`, sized by
`NEWLINE_FREE_WINDOW_MULTIPLIER = 4` at `:151`; budget at
`src/main/kotlin/com/six2dez/burp/aiagent/config/Defaults.kt:78`.

**Issue:** asked plainly, as the brief requests: **yes, this will flake in CI.** Three consecutive
runs of the single test on Apple Silicon / JDK 21 with the JaCoCo agent attached (the plugin is
applied at `build.gradle.kts:11` and `tasks.withType<Test> { finalizedBy(jacocoTestReport) }` at
`:215`, so every `test` run is instrumented):

```
run: 2.022s  newlineFreeOversizeBodyIsScannedNotDestroyed()
run: 1.973s  newlineFreeOversizeBodyIsScannedNotDestroyed()
run: 1.931s  newlineFreeOversizeBodyIsScannedNotDestroyed()
```

against `MAX_REDACTION_BUDGET_MS = 2_000`. Uninstrumented, the body stage alone measures 1 672-1 698 ms
of that 2 000 ms budget (84 %). I bisected the failure threshold by re-running the same fixture under
a reduced budget:

```
budget=1800 ms -> outLen 99%  keptKeyAssert=true   lengthAssert=true
budget=1600 ms -> outLen 87%  keptKeyAssert=true   lengthAssert=true
budget=1000 ms -> outLen 50%  keptKeyAssert=false  lengthAssert=true    <- assertion at :1691 fails
budget= 800 ms -> outLen 37%  keptKeyAssert=false  lengthAssert=false   <- assertion at :1695 fails too
```

So `assertTrue(output.contains("\"api_key\":\"[REDACTED]\""))` breaks on a runner roughly 2× slower
than an M-series Mac — which is the normal speed of a GitHub-hosted `ubuntu-latest` runner for
single-threaded regex work, before instrumentation. `assertFalse(output.contains("SC4-NEWLINE-SECRET-9"))`
would survive, so the failure presents as the *capability* assertion breaking, which is exactly the
"deadline pressure, not a CR-04 regression" diagnosis the comment at `:1653-1660` predicts. Predicting
a flake in a comment does not stop it going red and does not stop the next contributor from
`@Disabled`-ing it.

The related sweeps are safer but not free: `windowedScanRedactsJsonPairAcrossEveryBoundaryAlignment`
(7.96 s) and `jsonPairWithBlankLineBetweenKeyAndValueIsRedacted` (7.84 s) each assert
`assertFalse(contains("REDACTION INCOMPLETE") …)` per shift at ~330 ms against the same 2 000 ms
budget — ~6× headroom. Those five windowing tests are 21.7 s of a 22.0 s suite.

**Fix:** stop racing a production constant with a fixture size. The comment at `:141-150` argues 4×
because 2× "is how a fixture silently stops reproducing on faster hardware" — but the *deterministic*
half of CR-04 is already asserted hardware-independently by
`splitPointCutsNewlineFreeWindowsInsteadOfRefusing`, so this test only has to prove the **wiring**:

```kotlin
// 1.5x the window width is enough to make the body ONE oversized newline-free window and force
// dropOrRetry, which is the wiring under test; the arithmetic is asserted by the splitPoint seam.
private const val NEWLINE_FREE_WINDOW_MULTIPLIER = 2
```

If the 4× size is genuinely required, add an internal budget seam in the style of
`testRedactCookieSections(text, budgetMs)` and drive `windowedScan` with an explicit, generous budget
so the assertion is deterministic:

```kotlin
internal fun testWindowedScan(input: String, budgetMs: Long): String = /* … */
```

---

### W-05: `COOKIE_SECTION_BUDGET_MS` is sampled only *between* header occurrences, so it does not bound what its comment says it bounds

**File:** `src/main/kotlin/com/six2dez/burp/aiagent/redact/Redaction.kt:118-124` (the claim),
`:186-207` (the loop).

**Issue:** the constant is documented as "the wall-clock ceiling for `redactCookieSections` across ALL
occurrences within a single call". The check sits at the top of the loop, before `indexOf`:

```kotlin
while (true) {
    if (System.nanoTime() - deadline >= 0L) { … return sb.toString() }
    val h = text.indexOf(COOKIE_SECTION_HEADER, cursor)
    …
    sb.append(text.substring(bodyStart, end).replace(cookieSectionPairRegex) { … })
    cursor = end
}
```

The `String.replace(Regex)` at `:204` is the only unbounded work in the function and it is **not**
routed through `SafeRegex` and not checked against the deadline. One occurrence whose section
contains a multi-megabyte line therefore runs to completion regardless of the budget. The pattern
`(?m)^([^=\r\n]+)=(.*)$` is linear, so this is not a denial of service — but the guarantee stated is
stronger than the one implemented, and this is the exact class of overstatement that let CR-02 ship.

The second half is worse in the other direction: because the check precedes `indexOf`, a stop-the-world
GC pause longer than 250 ms anywhere in the loop causes **the entire remaining prompt** — up to
`maxBodyBytes`, 2 MiB by default — to be replaced by one `windowDroppedMarker`, even when there are no
further cookie sections in it. That fails closed, so it is not a leak; it is a plausible, silent
total loss of analytic context on a well-behaved input, triggered by nothing the user did.

**Fix:** two lines. Route the section replace through the same deadline discipline as every body rule,
and only pay the fail-closed price when a cookie section is actually still pending:

```kotlin
val h = text.indexOf(COOKIE_SECTION_HEADER, cursor)
if (h < 0) break                      // hoisted ABOVE the deadline check
if (System.nanoTime() - deadline >= 0L) { /* drop the tail, as today */ }
```

and reword the constant's comment from "across ALL occurrences" to "sampled once per occurrence; a
single occurrence's replace is bounded by its section length, not by this budget".

---

### W-06: the factored-vocabulary equivalence test compares one rule against the whole pipeline, in one consumer context

**File:** `src/test/kotlin/com/six2dez/burp/aiagent/redact/RedactionTest.kt:757-799`.

**Issue:** this test is the *only* guard on a hand-optimised, security-critical regex rewrite, so its
strength matters more than most. Two structural weaknesses:

1. **Asymmetric comparison.** The naive side runs a single rule; the shipped side runs the entire
   pipeline:

   ```kotlin
   val naiveRedacted   = !naiveJsonRule.replace(doc, "$1\"[REDACTED]\"").contains(SC3_SENTINEL)
   val shippedRedacted = !redactWith(doc, PrivacyMode.STRICT).contains(SC3_SENTINEL)
   ```

   `redactWith` also applies `authHeaderRegex`, `bearerRegex`, `basicAuthRegex`, `jwtRegex`,
   `urlTokenParamRegex`, `formBodyParamRegex`, both cookie rules and any custom pattern. Any of those
   firing sets `shippedRedacted = true` and *masks* an under-match in the factored form. That is the
   brief's own test-vacuity question — "what else in the pipeline would catch this same input?" — and
   the answer here is "eight other rules". For the current corpus the masking risk happens to be nil
   (a bare `{"key":"SENTINEL"}` carries no `=`, no `?`/`&`, no `Bearer`/`Basic`/`eyJ` and no cookie
   context), but that is a property of the fixture, not of the test.

2. **One consumer context.** Only `jsonSecretKeyRegex` is exercised. `urlTokenParamRegex` and
   `formBodyParamRegex` embed the same expression with different anchors, and `formBodyParamRegex`
   additionally captures the key in group 2, so a factoring change that altered group numbering would
   corrupt its `$1$2=[REDACTED]` replacement without failing here. (I verified group counts are 2 and
   2 today, so Pitfall 7 currently holds — but the test does not check it.)

Note that the factoring itself is **correct**: I ran a differential fuzz of the shipped vs naive
expression over 400 000 random keys × 3 consumer contexts, 500 000 additional camelCase-heavy keys,
and every key of length ≤ 4 over a 14-character alphabet — **zero divergences**. This is a finding
about the guard, not about the code it guards.

Also: `assertTrue(corpus.size > 100, …)` at `:787` measures the list *before* `.distinct()` at `:789`,
so it does not bound what is actually iterated.

**Fix:** compare like with like, and cover all three consumers:

```kotlin
private fun naiveRules(): List<Pair<Regex, String>> =
    listOf(
        Regex("(?i)(\"${Redaction.NAIVE_KEY_EXPR_FOR_TEST}\"\\s*:\\s*)(\"[^\"]*\"|true|false|null|-?\\d+(?:\\.\\d+)?)")
            to "$1\"[REDACTED]\"",
        Regex("(?im)(^|[?&])(${Redaction.NAIVE_KEY_EXPR_FOR_TEST})=[^&\\s\"'<>]+") to "$1$2=[REDACTED]",
        Regex("(?i)([?&](?:${Redaction.NAIVE_KEY_EXPR_FOR_TEST})=)[^&\\s\"'<>]+") to "$1[REDACTED]",
    )
// For each key, compare the NAIVE rule's output against the SHIPPED rule's output in the SAME
// context, not against the whole pipeline; and assert group counts match so Pitfall 7 is pinned.
```

and change the anti-vacuity guard to `assertTrue(corpus.distinct().size > 100, …)`.

---

### W-07: two "named guard" references point at things that do not exist

**File:** `src/test/kotlin/com/six2dez/burp/aiagent/redact/RedactionTest.kt:1133`;
`src/main/kotlin/com/six2dez/burp/aiagent/redact/Redaction.kt:290-291`.

**Issue:** this codebase deliberately uses "the named guard is X" comments as a load-bearing safety
mechanism — several of them are the only thing telling a future contributor that deleting a line
reopens a leak. Two of them are now wrong, which devalues all of them:

- `RedactionTest.kt:1133` says the end-to-end guard is
  `PassiveAiScannerPromptRedactionTest.emittedSectionShapedCookieCannotTerminateSpan`. No such test
  exists; it is `poisonedCookieHeaderCannotTerminateTheCookieSection` (`:278`). A reader following the
  reference concludes the guard was deleted.
- `Redaction.kt:290-291`, the KDoc of `sanitizeCookieSectionEntries`, still says "Nothing in plan
  21-08 calls this yet, which is deliberate". Plan 21-10 wired it at two call sites. A KDoc on a
  security control that states the control is unused is actively misleading.

**Fix:** correct both strings. For the first, prefer a form that cannot rot silently — reference the
test file rather than a method name, or add the missing method name to
`21-VALIDATION.md`'s automated selector list so a rename is caught.

---

### W-08: WR-04's teardown race is unfixed and this phase widened it

**File:** `src/main/kotlin/com/six2dez/burp/aiagent/App.kt:68` (set), `:202-232` (`shutdown`);
`src/main/kotlin/com/six2dez/burp/aiagent/redact/Redaction.kt:626-639` (`maybeLogTruncation`),
new third call site at `:194`.

**Issue:** `21-12-PLAN.md` §Deferred defers WR-04 as "a teardown-race robustness issue rather than a
correctness or disclosure defect", which was accurate when it was written. It is less accurate now:
this phase added a **third** caller of `maybeLogTruncation`, inside `redactCookieSections`, which runs
in the *header* stage of every `Redaction.apply` where `stripCookies` is true — i.e. in the default
BALANCED mode, on every MCP tool call and every passive scan, not only on oversized bodies.

`shutdown()` still unwires `BackendDiagnostics.retry`, `AuditLogger`'s global emitter and
`Redaction.clearMappings()`, and still leaves `Redaction.truncationLogger` holding a lambda that
captures `api`. `maybeLogTruncation` still has no `try`/`catch`, so a throw from a torn-down
`api.logging()` propagates out through `redactCookieSections` → `apply` → the caller — on a path that
now runs far more often than when the deferral was recorded.

**Fix:** unchanged from `21-REVIEW.md` WR-04, two lines, and it belongs beside the existing step:

```kotlin
safeShutdownStep("Redaction mappings") { Redaction.clearMappings() }
Redaction.truncationLogger = null
```

```kotlin
// Redaction.maybeLogTruncation — a diagnostics sink must never abort a redaction pass.
runCatching { truncationLogger?.invoke(truncationLine(droppedChars, suppressed)) }
```

---

## Info

### IN-01: `NAIVE_KEY_EXPR_FOR_TEST` is a test-only constant shipped inside the production fat JAR

**File:** `src/main/kotlin/com/six2dez/burp/aiagent/redact/Redaction.kt:516-521`.

**Issue:** Kotlin `internal const val` compiles to a `public static final String` field on
`Redaction`, so this ~700-character test seam is present in `Custom-AI-Agent-<version>.jar` and is
reachable by any code on the classpath. It is inert and harmless, but the file's two other test seams
(`testRedactCookieSections`, `testSplitPoint`) are `internal fun`s, which at least get name-mangled.

**Fix:** if it matters for the BApp Store review surface, build the naive expression in the test source
set from three `internal const val` vocabulary constants instead of exporting the assembled
expression. Otherwise, note in the comment that it ships.

---

### IN-02: `splitPoint`'s documented safety premise does not hold for the branch it justifies

**File:** `src/main/kotlin/com/six2dez/burp/aiagent/redact/Redaction.kt:995-997`.

**Issue:** point 1 of the three-point safety argument reads "A window with **no interior newline** has
no interior line anchors to corrupt." The branch it justifies is reached whenever
`backward <= 0 && (forward < 0 || forward + 1 >= window.length)` — which includes windows that **do**
have a newline, at index 0 or at the final position. `splitPoint("\n" + "x".repeat(10))` returns 5,
cutting mid-line in a window that has two lines. Points 2 (an artificial anchor can only over-redact)
and 3 (reachable only where the alternative is emitting nothing) still hold and still carry the
argument, so this is a comment accuracy issue, not a behaviour one.

**Fix:** reword point 1 to "a window with **no usable interior** newline — the cut position is the only
artificial line start this branch can create, rather than one per line."

---

### IN-03: reaching `MAX_JSON_BOUNDARY_LOOKAHEAD_LINES` has no test

**File:** `src/main/kotlin/com/six2dez/burp/aiagent/redact/Redaction.kt:706-731`,
`:1135-1150`.

**Issue:** the cap is documented as a deliberate residual, and I confirmed it behaves as documented
(a pair spread over nine lines leaks on the windowed path at 6 of 24 alignments while the single-pass
path redacts it — the recorded, accepted behaviour). But nothing asserts either half: not that the
loop stops at eight, and not that the window stays line-aligned at the cap. A change to the cap or to
the continuation predicate would move the boundary silently.

**Fix:** a cheap seam assertion in the style of `testSplitPoint`, exercising `windowEnd` directly with
a nine-line risky run and asserting the returned index is exactly eight lines past the natural cut.

---

### IN-04: `sanitizeCookieSectionEntries` runs twice on the production path

**File:** `src/main/kotlin/com/six2dez/burp/aiagent/scanner/PassiveAiScannerPrompts.kt:66` and `:141`.

**Issue:** `doAnalysis` builds `cookies` through `cookieSectionLines`, which sanitises; then passes it
to `buildScanMetadataText`, which sanitises again. The operation is idempotent (a
space-prefixed `" === FOO ==="` no longer satisfies `startsWith("===")`), so there is no defect, and
both call sites are individually mutation-guarded by different tests — the producer call by
`blankCookieElementsDoNotConsumeDisplaySlots`, the emitter call by
`cookieSectionEntriesAreSanitizedAtTheEmitter`. Recorded only so the duplication is not later
"cleaned up" by removing whichever call the reader happens to notice first; the emitter call is the
defence-in-depth one and must stay.

---

_Reviewed: 2026-08-12T12:29:03Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: deep_
_Round: 2 (re-review of `21-REVIEW.md` after gap-closure plans 21-08…21-12)_
