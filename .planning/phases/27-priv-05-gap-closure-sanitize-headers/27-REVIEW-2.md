---
phase: 27-priv-05-gap-closure-sanitize-headers
reviewed: 2026-08-26T00:00:00Z
depth: standard
files_reviewed: 8
files_reviewed_list:
  - src/main/kotlin/com/six2dez/burp/aiagent/redact/Redaction.kt
  - src/main/kotlin/com/six2dez/burp/aiagent/scanner/PassiveAiScannerFilters.kt
  - src/test/kotlin/com/six2dez/burp/aiagent/redact/RedactingPolicySurvivalSweepTest.kt
  - src/test/kotlin/com/six2dez/burp/aiagent/redact/CookieHeaderNameWidthTest.kt
  - src/test/kotlin/com/six2dez/burp/aiagent/redact/CookieHeaderNameParityTest.kt
  - src/test/kotlin/com/six2dez/burp/aiagent/redact/LogicalLineBoundaryScopeTest.kt
  - src/test/kotlin/com/six2dez/burp/aiagent/mcp/tools/McpToolHelpersTest.kt
  - src/test/kotlin/com/six2dez/burp/aiagent/mcp/tools/SerializedEmissionRedactionTest.kt
findings:
  critical: 3
  warning: 7
  info: 2
  total: 12
status: issues_found
---

# Phase 27 (round 4): Code Review Report

**Reviewed:** 2026-08-26
**Depth:** standard (with execution-time probes against the compiled `build/classes/kotlin/main`)
**Files Reviewed:** 8
**Status:** issues_found

## Summary

Round 4's two production edits are small and, on the axes they claim, correct. I verified by
execution rather than by reading:

- `COOKIE_NAME_PART = "[A-Za-z0-9_-]*"` closes the underscore class. `my_cookie` / `X_Cookie` /
  `session_cookie` are now stripped under STRICT and BALANCED; the mutual exclusion with
  `setCookieHeaderRegex` still holds for `X_Set-Cookie` and `Set_Cookie`; no new nested quantifier
  or backtracking surface was introduced (the class is a flat literal widening).
- Both lookbehinds in `logicalLineHeaderRule` are genuinely FIXED-WIDTH (`\\[rn]` = 2, `"` = 1),
  spelled as two separate lookbehinds in a non-capturing alternation, with no differing-width
  alternation inside either. Java accepts them and the escaped tail keeps its atomic `\\.` parity
  tokenisation.
- The two `assertTrue(...contains("api.example.com"))` STRICT host pins really are gone from
  `McpToolHelpersTest.kt`, and their replacement `offLeavesBothSerializedShapesByteIdentical` really
  is an `assertEquals` byte-identity fixture under `PrivacyMode.OFF`. `McpToolContext.redactIfNeeded`
  has no OFF short-circuit, so that test exercises the real pipeline and is not vacuous.
- `CookieHeaderNameParityTest`'s underscore pin really was inverted, not deleted.
- I re-implemented `RedactingPolicySurvivalSweepTest`'s detector independently and reproduced every
  number its KDoc claims: **151** `.kt` files, **9** hits without the three exclusions (7
  benign-control + 1 pre-redaction guard + 1 negated containment, exactly as enumerated), **0** with
  them, **5** unskipped self-hits, and **125** rationale comment lines against the floor of 90. Those
  claims are accurate.

That is the good news, and it is real. The defects below are all of the same family this phase keeps
producing — a control whose stated scope is wider than what it measures — plus one new shipped
behaviour that was never stated and is never gated.

**The three blockers:**

1. The sweep's `FUNCTION_DECLARATION` regex cannot see backtick-quoted or modifier-prefixed
   function declarations. **136 function declarations across the tree, 67 of them backtick-named
   `@Test` methods in 9 files — including `redact/SecretTripwireHooksTest.kt` — are invisible to the
   sweep today.** This is not a hypothetical future shape; it is live, and it is not among the eleven
   blind axes the file enumerates as its integrity claim.
2. Nothing gates the `fileWalk` → `detect` composition in the *flagging* direction. Every positive
   proof in the file bypasses `fileWalk`. The file's own comment names the silent-blanking direction
   as "the dangerous direction" and then does not assert against it.
3. `JSON_STRING_OPEN` makes **any** double quote a logical-line start, not just a JSON string open.
   Measured on the compiled classes: HTML attribute text, JS string literals, quoted CSV fields and
   prose are now destroyed mid-line. This over-match is undocumented (unlike D-27-15's, which is
   stated in detail three paragraphs above) and has no gating test.

---

## Critical Issues

### CR-01: The survival sweep is structurally blind to 136 function declarations, including 67 backtick-named `@Test` methods, today

**Classification:** BLOCKER
**File:** `src/test/kotlin/com/six2dez/burp/aiagent/redact/RedactingPolicySurvivalSweepTest.kt:569`
(`val FUNCTION_DECLARATION = Regex("^\\s*(private |internal )?fun (\\w+)\\(")`)

**Issue:**
`detect()` only enters a function at all if `FUNCTION_DECLARATION` matches its declaration line. The
regex admits exactly one optional modifier (`private ` or `internal `) and requires a `\w+` name.
It therefore matches none of:

- `` fun `cookie survives strict`() `` — backtick names, the standard Kotlin/JUnit5 idiom;
- `@Test fun x() {` on one line;
- `suspend fun`, `public fun`, `open fun`, `override fun`, `protected fun`, `inline fun`.

I measured this over `src/test/kotlin`: of 1779 `fun` declaration lines, **136 are invisible to the
sweep**, and **67 of those are backtick-named test methods** spread over 9 files:

```
  14  backends/http/HttpBackendCircuitFailureTest.kt
  13  mcp/McpSupervisorConnectionTest.kt
  13  redact/SecretTripwireHooksTest.kt          <-- a redaction-package test file
  10  agents/AgentProfileLoaderTest.kt
  10  supervisor/AgentSupervisorRestartPolicyTest.kt
   9  backends/http/HttpBackendTransportRoutingTest.kt
   ...
```

Verified against the detector, faithfully re-implemented:

```
backtick fun name            -> hits=[]        (a real survival pin, missed)
@Test fun on the same line   -> hits=[]        (missed)
suspend fun                  -> hits=[]        (missed)
public fun                   -> hits=[]        (missed)
plain `fun x()` control      -> hits=[('plainPin', '"sentinelleak"', 0)]   (found)
```

This matters more than a normal coverage gap because the file's whole value proposition is that its
eleven blind axes are the complete statement of what it cannot see. The KDoc says "it scans
`src/test/kotlin` on every CI run and fails on the next such pin" — false for any test written with a
backtick name, which is 3.8% of the current test methods and the more idiomatic of the two styles.
This is the register-wider-than-the-control defect, reproduced inside the artifact written to stop it.

**Fix:**

```kotlin
// Accepts any modifier prefix, an optional same-line annotation, and BOTH name spellings.
// Group 2 is the identifier for a plain name; group 3 for a backtick-quoted one.
val FUNCTION_DECLARATION =
    Regex("^\\s*(?:@\\w+(?:\\([^)]*\\))?\\s+)*(?:\\w+\\s+)*fun\\s+(?:<[^>]*>\\s*)?(?:(\\w+)|`([^`]+)`)\\s*\\(")
```

with `declaration.groupValues[1].ifEmpty { declaration.groupValues[2] }` as the identifier, and a
new non-vacuity test asserting the backtick and modifier forms are flagged (mirroring
`theBlankLineHazardFixtureIsIsolatedWholeIncludingItsBlankLines`, which is exactly the right shape
for this). If the narrower regex is kept deliberately, the backtick/modifier forms must be added to
the numbered blind-axis list and the "fails CI on the next such pin" sentence must be scoped — but
that is the weaker option, because 67 live methods are already outside it.

---

### CR-02: No test proves `fileWalk` + `detect` can flag anything — the sweep's only production path is ungated in the flagging direction

**Classification:** BLOCKER
**File:** `src/test/kotlin/com/six2dez/burp/aiagent/redact/RedactingPolicySurvivalSweepTest.kt:96-101`
(the tree scan), `:336-358` (`dropRawStringInteriors`), `:110-145` (the self-scan pair)

**Issue:**
The one assertion that actually protects the repository —
`noGreenTestAssertsASensitiveValueSurvivesARedactingPolicy` — runs
`detect(relativePath(it), fileWalk(it))` and expects an **empty** result. Every assertion that
proves the detector can produce a hit bypasses `fileWalk`:

| test | input to `detect` | expects |
|---|---|---|
| `noGreenTestAssertsASensitiveValueSurvivesARedactingPolicy` | `fileWalk(file)` | empty |
| `theSweepFileItselfYieldsNoHits` | `fileWalk(self)` | empty |
| `theRawStringSkipIsWhyTheSelfScanIsClean` | `self.readLines()` (**no walk**) | non-empty |
| `everyVocabularyEntryIsProvenLive…` | `fixture.lines()` (**no walk**) | non-empty |
| `theSkipHasNotDisarmedTheDetector` | `fixture.lines()` (**no walk**) | non-empty |
| `theTwoHostPinsRemovedThisRoundAreFlagged` | `HOST_PIN_FIXTURE.lines()` (**no walk**) | 2 hits |
| `theBlankLineHazardFixture…` | `fixture.lines()` (**no walk**) | non-empty |

So the composition `fileWalk` → `detect` has **no positive gate at all**. If `dropRawStringInteriors`
ever starts blanking real code — the exact failure the in-code comment at `:347-354` names as "the
dangerous direction… can also blank REAL code and make the tree scan miss a real survival pin
SILENTLY" — the tree scan returns zero hits and all eleven tests stay green. `theSweepFileItselfYieldsNoHits`
cannot catch it (fewer hits is a pass); `theRawStringSkipIsWhyTheSelfScanIsClean` cannot catch it
(it deliberately skips the walk); `theTreeWalkIsNonVacuous` only counts files, not lines survived.

I measured the current state: 0 of 151 files end in the INSIDE state and 625 lines tree-wide are
blanked, so the sweep is not vacuous *today*. That is luck, not a guarantee — it is the exact
property the file exists to stop relying on.

**Fix:** add one composition test using a synthetic two-part input, so the assertion binds the walk
and the detector together:

```kotlin
@Test
fun theWalkPreservesRealCodeWhileSkippingRawStringInteriors() {
    // A raw-string fixture (must be skipped) FOLLOWED by a real-code survival pin (must survive
    // the walk and be flagged). Asserting only the first half is what the current file does.
    val lines = (SELF_SHAPED_FIXTURE_WRAPPER + REAL_CODE_PIN).lines()
    val hits = detect(FIXTURE_ID, dropRawStringInteriors(lines))
    assertEquals(
        1, hits.size,
        "the walk must blank the raw-string fixture AND leave the real-code pin scannable. " +
            "Zero hits means the walk has started blanking real code, which makes the tree scan " +
            "silently vacuous with every other test in this file still green. Hits: $hits",
    )
}
```

Additionally consider making `dropRawStringInteriors` fail loudly on an unbalanced file — `if (inside)
throw AssertionError("unbalanced triple quotes in $path; the rest of this file was blanked")` — which
converts the silent-blindness mode into a red test.

---

### CR-03: `JSON_STRING_OPEN` makes *every* double quote a logical-line start; measured destruction of HTML/JS/CSV/prose content, undocumented and ungated

**Classification:** BLOCKER
**File:** `src/main/kotlin/com/six2dez/burp/aiagent/redact/Redaction.kt:277` (`JSON_STRING_OPEN`),
`:312-317` (`logicalLineHeaderRule`)

**Issue:**
`(?<=")` does not mean "a JSON string open". It means "any double quote". `Redaction.apply` runs over
every MCP tool result (`McpToolContext.redactIfNeeded`), `redact_preview`, `ContextCollector`,
`BountyPromptTagResolver` and the whole passive-scan metadata blob — all of which carry arbitrary
response bytes, not JSON. Measured on the compiled classes at
`build/classes/kotlin/main`, salt `probe-salt`, `PrivacyMode.STRICT`:

```
IN : <div title="Cookie: we use cookies for analytics. Accept?">text</div>
OUT: <div title="Cookie: [STRIPPED]">text</div>

IN : foo("cookie: analytics", KEEPME);
OUT: foo("cookie: [STRIPPED]", KEEPME);

IN : 1,"x-cookie: none",KEEPTAIL
OUT: 1,"x-cookie: [STRIPPED]",KEEPTAIL

IN : The header "Set-Cookie: foo" is described here. KEEPTAIL
OUT: The header "Set-Cookie: [STRIPPED]" is described here. KEEPTAIL

IN : See the docs: "authorization: Bearer required" and KEEPTAIL
OUT: See the docs: "authorization: [REDACTED]" and KEEPTAIL      <- authHeaderRegex too
```

None of these was matched before round 4 (the real-line branch is `^`-anchored and mid-line text has
no preceding `\\r`/`\\n` pair). All three composer-built rules inherit it, `authHeaderRegex` included.

Three separate problems, in descending order:

1. **It is a functional regression by this codebase's own standard.** `redactCookieSections`' comment
   at `:570-576` argues at length that "removing analytically load-bearing content from a security
   prompt is a functional regression", citing WR-01 as precedent. Response-body HTML attributes and
   JS string literals are precisely the content a passive AI scanner reasons over. On the MCP path
   the model receives a mutated response body and can draw a wrong conclusion from it.
2. **The bound is not stated.** The rationale block states D-27-15's over-match for start 2 in a
   dedicated paragraph ("OVER-redaction, fail-safe in direction, and cheaper to state than to fix"),
   and the 27-11 block for start 3 states only the *fourth* start that is missing — it says nothing
   about start 3's own cost. In a file whose entire discipline is stating costs where the reader meets
   the rule, an unstated one is the defect, not an omission.
3. **It is not gated.** `SerializedEmissionRedactionTest.aMatchBeginningAtAJsonStringOpenStopsAtThatStringsClosingQuote`
   bounds the tail *inside JSON*. `theHeaderMapShapeIsStillOutOfTheComposersReach` bounds JSON object
   keys (which are structurally immune, because the key's closing quote precedes the colon). Neither
   touches the non-JSON case, so a future widening of the tail terminator would ship green.

**Fix — narrow the start to a JSON string *value* open.** `(?<=:")` is still FIXED-WIDTH (two
characters), so the cost model in the rationale block is preserved verbatim, and it removes every
false positive above while keeping every real reachability case working. Measured on the same probe:

```
                              current (?<=")                 proposed (?<=:")
html    <div title="Cookie:…  Cookie: [STRIPPED]             UNCHANGED
js      foo("cookie:…         cookie: [STRIPPED]             UNCHANGED
prose   "Set-Cookie: foo"…    Set-Cookie: [STRIPPED]         UNCHANGED
notes   {"notes":"Cookie:…    Cookie: [STRIPPED]             Cookie: [STRIPPED]   <- still closed
nested  [{"notes":"Cookie:…   Cookie: [STRIPPED]             Cookie: [STRIPPED]   <- still closed
```

```kotlin
// The open of a JSON string VALUE — the THIRD logical line start (27-11). Two characters, so the
// lookbehind stays fixed-width. It is `:"` and not `"` because a bare quote is not a JSON string
// open: it also occurs in HTML attributes, JS string literals and quoted CSV fields, all of which
// reach Redaction.apply through McpToolContext.redactIfNeeded and the passive-scan blob, and a bare
// quote was MEASURED destroying them mid-line.
private const val JSON_STRING_OPEN = ":\""
```

State the narrowing this brings with it — a header at the open of a JSON *array element* string
(`["Cookie: …"]`) is then not a recognised start — as a named residual beside AR-27-09, and add a
negative test to `JsonStringOpenBoundary` pinning that an HTML-attribute payload is left byte-identical
under STRICT. If the maintainer instead keeps `(?<=")`, the over-match must be written into the 27-11
rationale block with the same rigour D-27-15 got, and gated by a test that pins its blast radius.

---

## Warnings

### WR-01: `theBoundaryFragmentsAreComposedIntoTheMeasuredRuleSetAndNoOther` never measures "and no other"

**Classification:** WARNING
**File:** `src/test/kotlin/com/six2dez/burp/aiagent/redact/LogicalLineBoundaryScopeTest.kt:41-80`;
claim restated at `src/main/kotlin/com/six2dez/burp/aiagent/redact/Redaction.kt:198-200`

**Issue:** The test asserts (a) each of the three `COMPOSED_RULES` contains exactly one
`logicalLineHeaderRule(` call and (b) `hostHeaderRegex` contains zero. It never counts the total
number of `logicalLineHeaderRule(` call sites in the file, so a **fourth** rule adopting the composer
leaves it green. `Redaction.kt:198-200` nevertheless claims: "`LogicalLineBoundaryScopeTest` pins that
set, so a fourth rule adopting the composer, or one of these three dropping it, reads as a data change
rather than as a silent regex edit." Only the second half of that sentence is true. I measured 4
non-comment occurrences of `logicalLineHeaderRule(` today (1 definition + 3 uses) — correct, but
unasserted.

**Fix:**

```kotlin
val callSites =
    sourceFile().readLines()
        .filterNot { isCommentOnly(it) }
        .count { it.contains(COMPOSER_CALL) && !it.contains("private fun $COMPOSER_CALL") }
assertEquals(
    COMPOSED_RULES.size, callSites,
    "the composer has $callSites call sites but COMPOSED_RULES names ${COMPOSED_RULES.size}. A " +
        "FOURTH rule adopting the boundary must be a data change here, not a silent regex edit " +
        "(D-27-13).",
)
```

---

### WR-02: The sweep's negation rule over-fires on compound assertions, silently hiding a real pin

**Classification:** WARNING
**File:** `src/test/kotlin/com/six2dez/burp/aiagent/redact/RedactingPolicySurvivalSweepTest.kt:437-449`
(`assertsPresenceAt`)

**Issue:** `negated` is computed as
`normalised.substring(trueOpener + ASSERT_TRUE.length, at).trimStart().startsWith("!")` — i.e. from
the `assertTrue(` opener to the `.contains(` under test. For a compound assertion whose **first**
operand is negated, every subsequent `.contains(` in the same call inherits `negated = true`:

```kotlin
assertTrue(!out.contains("noise") && out.contains("sentinelleak"), "msg")
// -> detector hits = []   (verified against a faithful re-implementation)
```

The KDoc documents the negation rule as covering `assertTrue(!x.contains(v))`. This wider behaviour
is not stated and is not among the eleven blind axes. It is a plausible shape: a pin combined with a
noise check in one assertion.

**Fix:** scope the negation test to the operand actually preceding the call, not to the whole prefix:

```kotlin
val operandStart =
    maxOf(
        normalised.lastIndexOf("&&", at),
        normalised.lastIndexOf("||", at),
        normalised.lastIndexOf(',', at),
        trueOpener + ASSERT_TRUE.length,
    )
val negated = normalised.substring(operandStart, at).trimStart().trimStart('&', '|', ',').trimStart().startsWith("!")
```

---

### WR-03: `argumentAt` counts parentheses with no string-literal awareness

**Classification:** WARNING
**File:** `src/test/kotlin/com/six2dez/burp/aiagent/redact/RedactingPolicySurvivalSweepTest.kt:452-471`

**Issue:** The matching-paren scan treats `(` and `)` inside string literals as structure. For
`assertTrue(out.contains("a)b"), "msg")` it returns `"a` — a truncated argument that then fails every
vocabulary regex, so the pin is dropped silently. Symmetrically, an unbalanced `(` inside a literal
walks the argument past its real end. Today's sentinels are bare alphanumerics so nothing is affected,
but this is a silent-drop failure mode in a detector, and silent drops are the class this file exists
to eliminate.

**Fix:** skip over string literals while counting:

```kotlin
var inString = false
while (index < normalised.length) {
    val c = normalised[index]
    when {
        c == '\\' && inString -> index++          // skip the escaped character
        c == '"' -> inString = !inString
        !inString && c == '(' -> depth++
        !inString && c == ')' -> { depth--; if (depth == 0) return normalised.substring(start, index).trim() }
    }
    index++
}
```

---

### WR-04: The two round-4 production changes are never exercised together

**Classification:** WARNING
**Files:** `src/test/kotlin/com/six2dez/burp/aiagent/redact/CookieHeaderNameWidthTest.kt:224-228`
(`redactHeaderLine` builds a real multi-line blob only);
`src/test/kotlin/com/six2dez/burp/aiagent/mcp/tools/SerializedEmissionRedactionTest.kt:215-217`
(variant list is `X-Cookie` / `Cookie2` / `Set-Cookie2`)

**Issue:** Round 4 changed two things: the name character class (`_`) and the start boundary (the
JSON string open). `CookieHeaderNameWidthTest` proves the widened class **only on the real-line
boundary**. `SerializedEmissionRedactionTest` proves the new boundary **only for canonical and
hyphenated names**. There is no test anywhere for an underscore-bearing cookie name on a serialized
or JSON-string-open payload — i.e. for the intersection of the two changes. Given that
`COOKIE_NAME_PART` is spliced into the name fragment that appears on **both** branches of the
composer, that intersection is the one place a splice mistake would show.

**Fix:** add one underscore sentinel to the serialized variant list:

```kotlin
"X_Cookie" to Sentinel.VARIANT_X_UNDERSCORE_COOKIE,
```

and one JSON-string-open probe with an underscore-named header at the open of `notes`.

---

### WR-05: The new polarity comment states an unconditional MUST that the two conjuncts on its own line violate

**Classification:** WARNING
**File:** `src/main/kotlin/com/six2dez/burp/aiagent/scanner/PassiveAiScannerFilters.kt:186-199`

**Issue:** The comment added this round says: *"any name admitted here MUST also be a name
`Redaction.apply` can strip"*. On the very next line, three predicates admit:

- `name.contains("auth")` — admits `X-Vendor-Auth-Signature`, `X-Shopify-Access-Token`, etc.
  `authHeaderRegex` is a closed 16-name alternation and strips none of them.
- `name.contains("token")` — same.
- `name.startsWith("x-")` two lines above (`:182`) — admits **every** `X-` header unconditionally.
- `Redaction.isCookieHeaderName(...)` — still admits the 13 uncovered RFC 9110 tchars (AR-27-10), so
  `X.Cookie` is admitted onto the prompt and matched by neither cookie regex.

The paragraph does then gesture at the auth/token conjuncts being "the open-ended vendor class
CONCERNS.md records as deliberately deferred" — but that sentence sits in the paragraph *above* and
the MUST is written without exception. A reader takes the MUST as the invariant, and it is false at
its own call site. This is the same "claim wider than the control" pattern the round is written to fix,
relocated into the comment the maintainer reads first.

**Fix:** scope the MUST to the class it actually covers:

```
// So any name admitted here BY THE COOKIE CONJUNCT must also be a name Redaction.apply can strip:
// Redaction.COOKIE_NAME_PART is the constant that has to move with Redaction.isCookieHeaderName.
// THE OTHER THREE CONJUNCTS DO NOT SATISFY THAT INVARIANT AND ARE NOT CLAIMED TO: `x-` admits every
// X- header, and `auth`/`token` admit the open-ended vendor class, none of which authHeaderRegex's
// 16-name alternation reaches. That gap is CONCERNS.md's deferred item, not this one, and it is
// stated here so the MUST above is not read as covering it.
```

---

### WR-06: `Locale.ROOT` convention is declared in this file and then not followed by its siblings

**Classification:** WARNING
**File:** `src/main/kotlin/com/six2dez/burp/aiagent/scanner/PassiveAiScannerFilters.kt:171-175` (the
D-27-04 statement) vs `:81` (`header.name().lowercase()`), `:73`, `:99`, `:105`, `:113`, `:323`
(`lowercase()` / `uppercase()` with no argument)

**Issue:** The D-27-04 comment declares `Locale.ROOT` explicit at `sanitizeHeadersForPrompt` precisely
so that "a future switch to a locale-SENSITIVE spelling reads as the security change it would be".
Six sibling call sites in the same file, one of them another header-name lowercase
(`hasInterestingResponseHeaders:81`), use the bare form. Behaviour is identical today — Kotlin's
no-argument `lowercase()`/`uppercase()` compile to `Locale.ROOT` — so this is not a live bug, but a
convention stated in one place and not applied two functions away is a convention that will not
survive the first careless edit, which is the entire argument the comment makes.

**Fix:** apply `Locale.ROOT` (already imported at `:15`) at the header-name sites at minimum:

```kotlin
val name = header.name().lowercase(Locale.ROOT)
```

---

### WR-07: `MIN_EXPECTED_LINES`' "measured" value is stale after this round

**Classification:** WARNING
**File:** `src/test/kotlin/com/six2dez/burp/aiagent/redact/LogicalLineBoundaryScopeTest.kt:229`
(`/** Measured at 2018 lines. A floor, not a count. */`)

**Issue:** `Redaction.kt` is now 2200 lines; round 4 added ~120. The floor of 1500 still holds so
nothing fails, but this is a "measured at N" annotation that is no longer the measurement. In this
file every other number was re-measured and updated this round (`MIN_RATIONALE_LINES` 20 → 90 with
"MEASURED at 125", which I verified is exactly right). Leaving one stale is the seed of the drift the
rest of the file is built to prevent.

**Fix:** `/** Measured at 2200 lines after plan 27-11. A floor, not a count. */`

---

## Info

### IN-01: `theTreeWalkIsNonVacuous`'s file floor and the sweep's stated counts are accurate — recorded so a future reviewer need not re-derive them

**Classification:** WARNING (informational confirmation, no action)
**File:** `src/test/kotlin/com/six2dez/burp/aiagent/redact/RedactingPolicySurvivalSweepTest.kt:551-566`

I independently re-implemented the detector and confirmed, against the tree as shipped:
151 `.kt` files; 9 hits without the three exclusions, distributed exactly as the KDoc enumerates
(7 × `Sentinel.BENIGN_CONTROL.value` in `SerializedEmissionRedactionTest`, 1 pre-redaction guard in
`theRealTruncateIfNeededOutputShapeIsStrippedAndNotLengthened`, 1 negated containment in
`RedactionTest.cookieHeaderNameVariantsAreStripped`); 0 hits with them; 5 unskipped self-hits; 125
rationale comment lines. The `dropRawStringInteriors` comment-line guard is genuinely load-bearing and
correctly ordered before function-declaration matching, and no test file currently ends in the INSIDE
state. These claims are sound and should not be re-litigated.

### IN-02: `projectSlug` is interpolated into a filesystem path without validation

**Classification:** WARNING
**File:** `src/main/kotlin/com/six2dez/burp/aiagent/scanner/PassiveAiScannerFilters.kt:341-346`

**Issue:** `api.project().id().take(8)` is interpolated directly into
`File(System.getProperty("user.home"), ".burp-ai-agent/cache/$projectSlug")`. The value comes from
Burp's own API and is UUID-shaped in practice, so this is not currently reachable — but there is no
character validation between an external API's string and a filesystem path, and `take(8)` does not
exclude `/` or `..`. Pre-existing, not introduced this round.

**Fix:**

```kotlin
val projectSlug =
    runCatching { api.project().id() }
        .getOrDefault("default")
        .filter { it.isLetterOrDigit() || it == '-' }
        .take(8)
        .ifBlank { "default" }
```

---

## Explicitly checked and found clean

Recorded so the next round does not re-spend the budget:

- **Both lookbehinds are fixed-width.** `(?<=\\[rn])` is width 2, `(?<=")` is width 1, spelled as two
  separate lookbehinds inside `(?:…|…)`. Neither holds a differing-width alternation. Java compiles
  and runs them (verified against the compiled classes).
- **No new backtracking surface from the widening.** `[A-Za-z0-9_-]*` is a flat class widening; the
  polynomial `P*cookieP*` shape and the `(?!P*set-cookie)` lookahead are unchanged in structure and
  pre-date this round. No nested quantifier was added.
- **The two host pins are gone and their replacement is under `PrivacyMode.OFF`.** `assertEquals`
  byte-identity, not containment, so it cannot itself become a survival pin. `McpToolContext.redactIfNeeded`
  has no OFF short-circuit (`McpToolContext.kt:59-76`), so the OFF test exercises the real pipeline.
- **The tr-TR guard is sound.** `assertFalse(("COOKIE" as java.lang.String).toLowerCase().contains("cookie"))`
  is correct under `tr-TR` (dotless ı), the swap is `@ResourceLock(Resources.LOCALE)`-guarded,
  restored in `finally`, and re-asserted in `@AfterEach` against a `@BeforeEach` capture.
- **Mutual exclusion of the two cookie rules survives the widening.** `X_Set-Cookie` goes to the
  set-cookie rule only; `Set_Cookie` (underscore, no hyphen) goes to the cookie rule only; `a_set-cookie`
  is now matched where it previously matched neither rule.
- **JSON validity is preserved by the escaped branch.** The tail excludes `"` and bare `\`, consumes
  `\\.` atomically, and terminates before the closing quote; the replacement adds only JSON-safe
  characters. `assertSameJsonShape` gates this on five carriers.
- **The obs-fold / leading-whitespace residual (AR-27-09) and the 13-tchar residual (AR-27-10) are
  genuinely filed** — both appear in `.planning/phases/26-coverage-static-analysis-debt-docs/26-SECURITY.md`
  and in `.planning/codebase/CONCERNS.md`. Re-measured on the compiled classes:
  `GET / HTTP/1.1\r\n Cookie: a=SECRET5\r\n\r\n` survives byte-unchanged under STRICT and BALANCED,
  exactly as the source comment records.
- `./gradlew test` for all six touched test classes is green under JDK 21.
- No hardcoded secrets, no `eval`-class constructs, no path traversal in the redaction files, no empty
  catch blocks, English-only comments throughout.

---

_Reviewed: 2026-08-26_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
