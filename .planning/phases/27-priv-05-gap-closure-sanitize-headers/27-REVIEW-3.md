---
phase: 27
round: 3
reviewed: 2026-08-26T00:00:00Z
depth: standard
diff_base: c2d980f
files_reviewed: 4
files_reviewed_list:
  - src/main/kotlin/com/six2dez/burp/aiagent/redact/Redaction.kt
  - src/test/kotlin/com/six2dez/burp/aiagent/mcp/tools/SerializedEmissionRedactionTest.kt
  - src/test/kotlin/com/six2dez/burp/aiagent/redact/LogicalLineBoundaryScopeTest.kt
  - src/test/kotlin/com/six2dez/burp/aiagent/redact/RedactingPolicySurvivalSweepTest.kt
findings:
  critical: 1
  warning: 3
  info: 7
  total: 11
status: issues_found
---

# Phase 27 (round 5 source changes): Code Review Report — round 3

**Reviewed:** 2026-08-26
**Depth:** standard, with execution-time probes against `build/classes/kotlin/main` and one
revert-and-restore experiment
**Diff base:** `c2d980f..HEAD` (waves 8-10; plans 27-14, 27-15, 27-16)
**Files reviewed:** 4
**Status:** issues_found

## Summary

Round 5's three plans are, on the axes they claim, **correct and non-vacuous**, and I verified that
by execution rather than by reading. What follows the good news is one BLOCKER of the same family
this phase keeps producing — a stated bound narrower than the cost it describes — plus three
WARNINGs, one of which round 5 newly made load-bearing.

### What I proved is closed

**27-REVIEW-2 CR-01 is CLOSED, measured.** I re-implemented the widened `FUNCTION_DECLARATION`
independently and ran it over `src/test/kotlin`. Of 1797 declaration-ish lines, **3 are invisible**,
and all three are extension receivers — exactly the population blind axis 9 now names:

```
declaration-ish lines: 1797  invisible: 3
  util/SchedulerGuardCoverageTest.kt: private fun String.isRecurringSchedule(): Boolean = ...
  redact/RedactingPolicySurvivalSweepTest.kt: private fun String.indentWidth(): Int = ...
  redact/LogicalLineBoundaryScopeTest.kt: private fun String.indentWidth(): Int = ...
```

The KDoc's "3 extension receivers, one of them in this very file; 0 multi-line signatures" is exactly
right. **The capture-group question is answered too** — old vs new regex over the six fixture shapes,
with the identifier the new regex actually yields:

```
OLD  NEW  identifier                       shape
n    Y    a backtick quoted name           fun `a backtick quoted name`() {
n    Y    anAnnotationAndFunOnTheSameLine  @Test fun anAnnotationAndFunOnTheSameLine() {
n    Y    aSuspendModifierPin              suspend fun aSuspendModifierPin() {
n    Y    aPublicModifierPin               public fun aPublicModifierPin() {
n    Y    anOverrideModifierPin            override fun anOverrideModifierPin() {
Y    Y    aPlainDeclarationControl         fun aPlainDeclarationControl() {
```

1 of 6 → 6 of 6, and `groupValues[1].ifEmpty { groupValues[2] }` yields the right identifier for both
spellings. No group-index shift.

**27-REVIEW-2 CR-02 is CLOSED.** `theWalkPreservesRealCodeWhileSkippingRawStringInteriors` binds
`dropRawStringInteriors` → `detect` in the flagging direction and asserts both the count and *which*
half produced the hit, so blanking-real-code (0 hits) and skip-failure (2 hits) are distinguished.
`theWalkFailsLoudlyWhenAFileEndsInsideARawString` covers the case no in-file fixture could reach.

**27-REVIEW-2 CR-03 is CLOSED for the five carriers it named, and the gates are NOT vacuous.**
I reverted `JSON_STRING_OPEN` to `"` , recompiled, and ran the two touched test classes:

```
SerializedEmissionRedactionTest > JsonStringOpenBoundary >
    anHtmlAttributePayloadIsLeftByteIdenticalUnderBothRedactingModes() FAILED
LogicalLineBoundaryScopeTest > theJsonStringOpenIsAValueOpenAndNotABareQuote() FAILED
35 tests completed, 2 failed
```

Both new 27-14 gates go RED on the one-character revert. The source was restored byte-identically and
`build/classes` recompiled; `git diff --quiet -- src/` reports no change.

**Behavioural confirmation** on the restored classes, `PrivacyMode.STRICT` and `BALANCED`, salt
`probe-salt`, with the positive control firing in the same run:

```
[SAME   ] html attribute   <div title="cookie: analytics">KEEPTAIL</div>
[SAME   ] js literal       foo("cookie: analytics", KEEPME);
[SAME   ] csv              1,"x-cookie: none",KEEPTAIL
[SAME   ] prose            The header "Set-Cookie: foo" is described. KEEPTAIL
[SAME   ] prose auth       See the docs: "authorization: Bearer required" and KEEPTAIL
[CHANGED] control          {"notes":"Cookie: a=SECRET1\r\nX: y"} -> {"notes":"Cookie: [STRIPPED]\r\nX: y"}
```

**Regex correctness is sound.** `":\""` is the two literal characters `:` and `"`; neither is a regex
metacharacter, so `(?<=:")` is genuinely fixed-width at 2 and Java accepts it — the composed pattern
compiles and runs for all three consumers (`cookieHeaderRegex`, `setCookieHeaderRegex`,
`authHeaderRegex`; I exercised all three). No backtracking surface was added: the narrowing *reduces*
the set of candidate start positions and introduces no quantifier.

**Other verification.** `./gradlew test` green for the `redact` and `mcp.tools` packages (52 s).
`ktlintCheck` and `detekt` green. The KDoc's "7 benign-control assertions" is exactly right — I count
7 `redacted.contains(Sentinel.BENIGN_CONTROL.value)` assertion sites at `:151, 241, 291, 315, 371,
394, 1055`. `ALLOWLIST` is still `emptyMap()`. Comments are English-only throughout; no secrets, no
`eval`-class constructs, no empty catch blocks, no new path handling.

### What is wrong

The narrowing bought a real correctness repair, and it also gave up coverage on **at least four**
distinct payload families. Source and `26-SECURITY.md` name **one** of them. Standing-rule clause
(vii) added this very round requires a residual list to enumerate what the round INTRODUCED; the
enumeration it produced is one item where the measurement says four. That is CR-01 below.

---

## Critical Issues

### CR-01: `AR-27-11` states ONE family; the narrowing measurably gave up FOUR, and the LOW severity was derived from the incomplete list

**Classification:** BLOCKER
**Files:**
- `src/main/kotlin/com/six2dez/burp/aiagent/redact/Redaction.kt:258-268` (rationale block, "(b) WHAT
  THE NARROWING COSTS")
- `src/main/kotlin/com/six2dez/burp/aiagent/redact/Redaction.kt:322-327` (the constant's own "THE
  COST OF THE NARROWING" paragraph)
- `src/test/kotlin/com/six2dez/burp/aiagent/redact/LogicalLineBoundaryScopeTest.kt:370-382`
  (`THIRD_OPEN_FINDING` KDoc, which restates the same single-family bound)
- `.planning/phases/26-coverage-static-analysis-debt-docs/26-SECURITY.md:111-123` and its
  "reachability enumeration" section (`:655-670`)

**Issue (PROVED by execution, not reasoned).**

Both source paragraphs state the cost as exactly one shape:

> `:"` recognises a JSON string VALUE open ONLY. A header at the open of a JSON ARRAY ELEMENT string —
> `["Cookie: …"]` — is NOT a recognised start after 27-14 … Filed as open finding AR-27-11.

That is written as an exhaustive statement of what the narrowing costs. It is not. I built the two
composed patterns from the shipped fragments (`COOKIE_NAME_PART`, `COOKIE_NAME_TOKEN`,
`JSON_ESCAPED_NEWLINE`, `JSON_ESCAPED_HEADER_VALUE`) and differed only in the start constant:

```
BARE     COLONQ    case
MATCH    -         nested escaped json string value open (response body is JSON)
MATCH    -         pretty json, space between colon and quote
MATCH    -         array element                                <- the ONE family that is named
MATCH    -         bare top-level json string
MATCH    MATCH     CONTROL compact object value open            <- positive control, still works
MATCH    -         HTML attribute (must NOT match after narrowing)
```

Confirmed end-to-end against the shipped classes through `Redaction.apply(..., STRICT, "probe-salt")`,
with the compact control stripping in the same run:

```
SURVIVES: MCP tool result whose RESPONSE BODY is JSON carrying a cookie header at a nested string-value open
   IN/OUT (identical): {"request":"GET /api HTTP/1.1\r\nAccept: */*\r\n\r\n","response":"HTTP/1.1 200 OK\r\n
                        Content-Type: application/json\r\n\r\n{\"cookie_header\":\"Cookie: sess=SECRETNESTED\"}","notes":null}
SURVIVES: same, but the target pretty-printed its JSON body
   IN/OUT (identical): {"response":"HTTP/1.1 200 OK\r\n\r\n{\r\n  \"cookie_header\": \"Cookie: sess=SECRETPRETTY\"\r\n}"}
SURVIVES: redact_preview-style paste of pretty JSON
   IN/OUT (identical): {\n  "notes": "Cookie: sess=SECRETPASTE"\n}
SURVIVES: array element in a tool result
   IN/OUT (identical): {"tags":["Cookie: sess=SECRETARRAY"]}
STRIPPED: CONTROL compact nested value open (no escaping)
   IN : {"cookie_header":"Cookie: sess=SECRETCOMPACT"}
   OUT: {"cookie_header":"Cookie: [STRIPPED]"}
```

All three composed rules lose the same families — `authHeaderRegex` and `setCookieHeaderRegex`
behave identically (measured: `{"notes": "X-API-Key: SECRETAUTH2"}` and
`{"notes": "Set-Cookie: s=SECRETSC1; Path=/"}` both survive STRICT byte-unchanged, while their
compact-shaped twins are `[REDACTED]` / `[STRIPPED]`).

**The four families, and why three of them are not the array case:**

1. **Whitespace between `:` and `"`** — `{"notes": "Cookie: …"}`. Any pretty-printed or
   space-formatted JSON. `toolJson` is `Json { encodeDefaults = true }` (compact), so the MCP
   *emission* schema is unaffected — but `Redaction.apply` also receives **arbitrary** text on three
   other paths: `McpToolExecutorImpl.kt:1018` (`redact_preview`, whose whole purpose is to answer
   "would this leak?" — it now answers "no" for pretty JSON), `ContextCollector`, and the passive-scan
   prompt blob, where the payload is a *target's* response body and pretty-printed JSON is routine.
2. **A nested/escaped JSON string value open** — `\"key\":\"Cookie: …\"`. The two characters before
   the header are `\` and `"`, not `:` and `"`. This one sits on the **primary MCP emission path**:
   any `proxy_http_history` / `response_parse` result whose captured response body is JSON. It is
   precisely the class the register's reachability enumeration did not ask about.
3. **A bare top-level JSON string document** — `"Cookie: …"`.
4. **Array-element opens** — `["Cookie: …"]` and `,"Cookie: …"`. The one that is named.

**Why this is a BLOCKER rather than a documentation nit.** `26-SECURITY.md` rates `AR-27-11` LOW on
a reachability enumeration that asks exactly one question — *"which serialized fields on the MCP
emission path are JSON ARRAYS OF STRINGS?"* — and answers it correctly for arrays. Families 1-3 are
not arrays, were never asked about, and family 2 is reachable through the emission path the register
concluded arrays were not. A severity derived from an incomplete family list is not a measured
severity. And round 5's own contribution to `26-SECURITY.md` is standing-rule clause (vii): *a
residual list must enumerate what the round INTRODUCED, not only what it INHERITED.* The list it
produced under INTRODUCED is `AR-27-11` + sweep axis 9. Measurement says `AR-27-11` is four findings
wearing one id. This is the register-wider-than-control defect, reproduced in the clause written to
stop it — the fifth iteration.

**Mitigating fact, stated so the fix is not over-scoped.** In every one of the four families, only a
header that is the **FIRST content of the string** escapes. A realistic raw HTTP message inside any
of them is still stripped, because its header follows an escaped newline, which IS a recognised
start. I re-measured that and it holds. So the correct action is almost certainly **restate the
bound**, not re-widen the regex.

**Fix.** Two parts, both required.

(1) Replace the single-family sentence at `Redaction.kt:258-268` and `:322-327` with the measured
list. Keep the array wording as one entry:

```kotlin
// (b) WHAT THE NARROWING COSTS. `:"` recognises the open of a JSON string VALUE IN COMPACT,
// SINGLY-ENCODED JSON and nothing else. FOUR shapes stopped being recognised starts, MEASURED
// against the shipped fragments with the compact control matching in the same run:
//
//   {"notes": "Cookie: a=S"}                         whitespace between `:` and `"` (pretty JSON)
//   {"r":"…{\"k\":\"Cookie: a=S\"}"}                 a NESTED/escaped string value open
//   "Cookie: a=S"                                    a bare top-level JSON string document
//   {"tags":["Cookie: a=S"]}   and   ,"Cookie: a=S"  an ARRAY ELEMENT open
//
// In ALL FOUR only a header that is the FIRST CONTENT of the string escapes; a raw HTTP message
// inside any of them is still stripped, because its header follows an escaped newline. Filed as
// open finding AR-27-11 (all four shapes under one id), and NOT pinned by a test, because a green
// assertion that a cookie value survives STRICT is the artifact 26-SECURITY.md clause (vi) forbids.
```

(2) Re-derive `AR-27-11`'s severity in `26-SECURITY.md` against all four shapes, not arrays alone.
The reachability question has to become *"which `Redaction.apply` inputs can carry a header at the
open of a JSON string in ANY of the four spellings?"*, and it must name `redact_preview`
(`McpToolExecutorImpl.kt:1018`, arbitrary caller-supplied text), `ContextCollector` and the
passive-scan blob, none of which are compact-JSON-shaped by construction. If the conclusion is still
LOW, that is fine — but it has to be LOW *against the measured list*.

If instead the maintainer wants the coverage back without the bare quote's blast radius, the cheap
option is a third fixed-width lookbehind for the escaped spelling (`(?<=\\")`, width 2) and
`\s`-tolerance is **not** available in a fixed-width look-back, so pretty JSON would need a
consuming-group redesign — which is a bigger change than this round should take. Restating the bound
is the right move; state that too.

---

## Warnings

### WR-01: The narrowing does not remove the non-JSON over-match class it claims to, and the one gate added covers one of its shapes

**Classification:** WARNING
**Files:** `src/main/kotlin/com/six2dez/burp/aiagent/redact/Redaction.kt:304-311`;
`src/test/kotlin/com/six2dez/burp/aiagent/mcp/tools/SerializedEmissionRedactionTest.kt:575-618`

**Issue (PROVED).** The constant's comment argues the bare quote was wrong because "it also opens
HTML attribute values, JS string literals and quoted CSV fields", and the new gate is an HTML
attribute byte-identity test. But `:"` is not a JSON-only sequence — it is the object-literal
separator in JavaScript, JSON5, CSS and several config dialects. Measured on the shipped classes,
STRICT and BALANCED, restored source:

```
IN : var o = {note:"cookie: analytics", keep:"KEEPTAIL"};
OUT: var o = {note:"cookie: [STRIPPED]", keep:"KEEPTAIL"};

IN : a::before{content:"cookie: analytics"} KEEPTAIL
OUT: a::before{content:"cookie: [STRIPPED]"} KEEPTAIL
```

This is over-redaction — fail-safe in direction, and the blast radius is bounded to the enclosing
quoted run rather than to 1589 characters — so it is a WARNING and not the blocker the bare quote
was. But it is exactly the class `redactCookieSections`' own comment calls "a functional regression"
(removing analytically load-bearing content from a security prompt), it is unstated, and the gate
added this round would not catch it: swap `htmlAttributeFixture()`'s payload for the JS
object-literal line above and `anHtmlAttributePayloadIsLeftByteIdenticalUnderBothRedactingModes`
fails today.

**Fix.** State the residual beside (b), in one sentence, and scope the gate's KDoc to what it
measures:

```kotlin
// RESIDUAL OVER-MATCH, stated because `:"` is narrower than a bare quote and is NOT JSON-only:
// it is also the object-literal separator in JS/JSON5/CSS/YAML. MEASURED under STRICT and BALANCED:
//   var o = {note:"cookie: analytics", keep:"KEEPTAIL"};  ->  note:"cookie: [STRIPPED]"
//   a::before{content:"cookie: analytics"} KEEPTAIL       ->  content:"cookie: [STRIPPED]"
// Over-redaction, bounded to the enclosing quoted run (the tail terminates on the closing quote),
// and accepted — unlike the bare quote, which ran across it.
```

and either add a second byte-identity fixture for a JS object literal (if the decision is that this
too must survive) or add a positive fixture pinning the bounded blast radius (if it is accepted).
Right now the file records neither.

### WR-02: `dropRawStringInteriors`' comment rule covers only FULL-line comments — round 5 turned that gap into a hard build break

**Classification:** WARNING
**File:** `src/test/kotlin/com/six2dez/burp/aiagent/redact/RedactingPolicySurvivalSweepTest.kt:497,
518, 531-540`

**Issue (PROVED by faithful replication of the shipped helper).** The in-code comment at `:497` states
the rule without qualification:

> A COMMENT LINE NEVER OPENS OR CLOSES A RAW STRING, so it must not toggle the state.

The implementation at `:518` is `if (!isCommentOnly(line))`, and `isCommentOnly` only recognises a
line whose *first* non-space characters are `//`, `*` or `/*`. A **trailing** comment is a comment
and does toggle:

```
trailing-comment toggle test:
  ends inside raw string (=> AssertionError thrown by the walk): true
      val x = 1 // a raw string is opened with """ in Kotlin
  same text as a FULL-line comment: false
      // a raw string is opened with """
```

Before 27-15 this silently inverted the walk state and blanked the rest of the file — bad, silent.
27-15 added the `if (inside) throw AssertionError(...)` at `:531`, which converts it into a **hard
failure of `noGreenTestAssertsASensitiveValueSurvivesARedactingPolicy` and
`theSweepFileItselfYieldsNoHits`**, triggered by any developer anywhere in `src/test/kotlin` writing
an end-of-line comment that mentions `"""`. Nothing in the repository does today, so this is latent
— but the failure message will say "unbalanced triple quotes in <file>: fix the FILE — balance the
quote — not this check", which is wrong advice for a perfectly balanced file, and "do not exclude the
file from the walk" closes off the obvious escape hatch. That is a trap laid for a future engineer by
a claim wider than its control.

**Fix.** Make the implementation match the stated rule by stripping a trailing `//` comment before
counting (a `"""` cannot legally start after a `//` on the same line unless it is inside a string,
which the counter already cannot see either way):

```kotlin
// A COMMENT NEVER OPENS OR CLOSES A RAW STRING — whole-line OR trailing. `isCommentOnly` only sees
// the whole-line form, and a trailing `// … """ …` toggled the state for the rest of the file,
// which since this plan is a THROWN error rather than a silent blanking. Measured: the one-line
// fixture `val x = 1 // … """ …` ends INSIDE and throws; the same text as a whole-line comment does not.
val scannable = if (isCommentOnly(line)) "" else line.substringBefore("//")
```

and add the one-line fixture above to `NEGATIVE_FIXTURES`-style coverage so the rule is machine-checked
rather than asserted in prose. If the trailing form is deliberately out of scope, it must be axis 14
and `theStatedBlindAxisCountMatchesTheEnumeration`'s constant must move with it.

### WR-03: The thirteen-axis enumeration is machine-checked for COUNT, not for completeness, and a known recorded blind spot is not in it

**Classification:** WARNING
**File:** `src/test/kotlin/com/six2dez/burp/aiagent/redact/RedactingPolicySurvivalSweepTest.kt:42-99`
(the enumeration), `:424-450` (`theStatedBlindAxisCountMatchesTheEnumeration`), `:636-654`
(`argumentAt`)

**Issue (reasoned, with the code read).** Round 5 does two good things here: it adds axis 9 (the price
of its own widening) and axis 10 (27-REVIEW-2 WR-02's negation over-fire, with the fix written down
and the deferral argued). It then presents the count as machine-checked. But
`theStatedBlindAxisCountMatchesTheEnumeration` compares `STATED_BLIND_AXES` against the number of
lines matching `^\s*\*\s+\d+\.\s` **in the same KDoc**. It proves the prose and the constant agree.
It cannot prove the enumeration is complete, and the KDoc's framing — "WHAT THE SCAN CANNOT SEE.
THIRTEEN axes … named because a tripwire quoted wider than the vocabulary it scans reproduces … the
register-wider-than-control defect" — reads as a completeness claim.

Concretely: **27-REVIEW-2 WR-03 is not among the thirteen.** `argumentAt` counts parentheses with no
string-literal awareness, so `assertTrue(out.contains("a)b"))` yields the truncated argument `"a`,
which matches no vocabulary entry and is **silently dropped**. That is a recorded, unfixed
silent-drop path in the detector, and round 5's enumeration — which now claims to be the complete
statement of what the scan cannot see — does not list it.

Two smaller instances of the same shape:

- The regex `AXIS_ENTRY` counts numbered lines but does not check the numbers are `1..13` and
  distinct, so `1. 2. 2. 4. …` passes.
- The class KDoc says the sweep fails on the next pin "with or without a same-line annotation". An
  annotation whose argument list contains a `)` inside a string literal —
  `@ParameterizedTest(name = "a)b") fun x()` — is invisible, because `(?:@\w+(?:\([^)]*\))?\s+)*`
  stops the argument scan at the first `)`. I measured 0 live instances on this tree (there are no
  same-line annotated declarations at all), so this is latent, not live.

**Fix.** Either add `argumentAt`'s string-unaware paren counting as axis 14 with the fix written down
in the same style as axis 10, or fix it (27-REVIEW-2 WR-03 carries the patch). Add `1..N` sequencing
to the count check:

```kotlin
val numbers = source.subList(kdocOpensAt, classAt)
    .mapNotNull { AXIS_ENTRY.find(it)?.value?.filter(Char::isDigit)?.toIntOrNull() }
assertEquals((1..STATED_BLIND_AXES).toList(), numbers,
    "the enumeration must be 1..$STATED_BLIND_AXES in order; a duplicated or skipped number keeps " +
        "the COUNT right while the list is wrong")
```

and scope the "with or without a same-line annotation" sentence, or widen the annotation group to
`(?:@\w+(?:\((?:[^()\"]|\"[^\"]*\")*\))?\s+)*`.

---

## Info

### IN-01: The source-read pin binds the constant's VALUE, never its USE

**Classification:** WARNING (informational; low likelihood)
**File:** `src/test/kotlin/com/six2dez/burp/aiagent/redact/LogicalLineBoundaryScopeTest.kt:187-236`

`theJsonStringOpenIsAValueOpenAndNotABareQuote` reads the declaration line and decodes its literal —
robust, anchored at line start after `trimStart()`, comment-filtered first, and proven RED on a
revert. What it does not assert is that `logicalLineHeaderRule` still *references*
`JSON_STRING_OPEN`. Inlining a bare `"` into the composer and orphaning the constant leaves every
assertion in this file green. `SerializedEmissionRedactionTest` would still catch it behaviourally,
so this is a defence-in-depth gap, not a hole.

**Fix:** one line in the same test —
`assertTrue(sourceFile().readLines().filterNot { isCommentOnly(it) }.any { it.contains("(?<=\" + $COMPOSER_ARG") }` is
awkward; simpler is to assert the composer's own line names the symbol:

```kotlin
val composerBody = sourceFile().readLines().filterNot { isCommentOnly(it) }
assertTrue(composerBody.any { it.contains("JSON_STRING_OPEN") && it.contains("(?<=") },
    "the composer no longer references JSON_STRING_OPEN, so pinning the constant's value proves " +
        "nothing about the boundary the three rules actually carry")
```

### IN-02: `EXPECTED_JSON_STRING_OPEN_WIDTH` measures literal length, not look-back width, and is redundant

**Classification:** WARNING (informational)
**File:** `src/test/kotlin/com/six2dez/burp/aiagent/redact/LogicalLineBoundaryScopeTest.kt:387-390,
216-231`

The constant is documented as guarding fixed-width-ness of the lookbehind, but the assertion compares
`decoded.length` — the number of *characters in the Kotlin literal*, which equals the regex look-back
width only because `:` and `"` are both regex-literal. `"\\s\""` would decode to 3 characters while
matching width 2; `"[:,]\""` decodes to 6. And the `assertEquals(":\"", decoded)` two lines below
subsumes it entirely. Keep one; if the width claim is the one worth keeping, name it
`EXPECTED_JSON_STRING_OPEN_LITERAL_LENGTH` and say in the KDoc that it is a proxy that holds only
while every character is regex-literal.

### IN-03: `noNewlineNotes`' scope comment is not what the code does, and it is silently coupled to field order

**Classification:** WARNING (informational)
**File:** `src/test/kotlin/com/six2dez/burp/aiagent/mcp/tools/SerializedEmissionRedactionTest.kt:690-692`

The comment says *"The NOTES string only. `request` always carries escaped CRLFs on this carrier, so
a whole-payload scan here would be a guard on the wrong field"*, but
`noNewline.substringAfter("\"notes\":\"")` returns everything from the notes value to the **end of the
document**. It happens to exclude `request` only because `notes` is the last field of
`HttpRequestResponse` (`mcp/schema/Serialization.kt:124-128`). Reorder those three properties and the
fixture guard `assertFalse(contains("\\r"))` fires — a RED test whose message blames the fixture,
for a change with nothing to do with redaction.

**Fix:** `val noNewlineNotes = noNewline.substringAfter("\"notes\":\"").substringBefore("\"")`, and
change the comment to say why (the value ends at the first unescaped quote, and this fixture's notes
value contains none).

### IN-04: `assertSameJsonShape(serialized, redacted)` is tautological in the byte-identity test

**Classification:** WARNING (informational)
**File:** `src/test/kotlin/com/six2dez/burp/aiagent/mcp/tools/SerializedEmissionRedactionTest.kt:616`

The line above it is `assertEquals(serialized, redacted, …)`. Two identical strings trivially have
the same key set and field count. It is dead weight in a file that otherwise argues carefully for
every assertion it keeps; drop it or say in one clause why it is kept for symmetry with the nest's
other tests.

### IN-05: Two "MEASURED at N" annotations in `LogicalLineBoundaryScopeTest` are stale, and round 5 widened both gaps

**Classification:** WARNING (informational — carried forward from 27-REVIEW-2 WR-07, not re-litigated)
**File:** `src/test/kotlin/com/six2dez/burp/aiagent/redact/LogicalLineBoundaryScopeTest.kt:338, 415-421`

Measured just now:

```
Redaction.kt lines=2256   rationale comment lines=157
```

against `/** Measured at 2018 lines. A floor, not a count. */` and `MEASURED at 125 comment lines
after plan 27-11`. Both floors (1500 / 90) still hold so nothing is red. Round 5 added ~56 lines to
`Redaction.kt` and ~32 comment lines to the rationale region and refreshed neither, so the file that
exists to stop record-drift now carries two stale measurements. Restate as
`Measured at 2256 lines after plan 27-14` and `MEASURED at 157 comment lines after plan 27-14`.

### IN-06: `theWalkPreservesRealCodeWhileSkippingRawStringInteriors` exercises `dropRawStringInteriors`, not `fileWalk`

**Classification:** WARNING (informational)
**File:** `src/test/kotlin/com/six2dez/burp/aiagent/redact/RedactingPolicySurvivalSweepTest.kt:196-221,
487`

The test's KDoc and failure message both describe "the walk". `fileWalk` is
`dropRawStringInteriors(relativePath(file), file.readLines())` — a one-line adapter — so the
composition claim is substantively met and this is fine. Worth one clause in the KDoc noting that
`fileWalk`'s `File` → lines adapter (and `relativePath`) is still only exercised in the
expect-empty direction, so the statement stays as narrow as the control.

### IN-07: The 27-16 `MayBeConst` conversions are correct and inert

**Classification:** WARNING (informational confirmation, no action)
**File:** `src/test/kotlin/com/six2dez/burp/aiagent/redact/RedactingPolicySurvivalSweepTest.kt:1372,
1442, 1477`

`DECLARATION_SHAPE_FIXTURE`, `WALK_COMPOSITION_FIXTURE` and `UNBALANCED_WALK_FIXTURE` are
compile-time-constant concatenations of string literals and `TRIPLE_QUOTE`, so `const val` is legal
and value-identical. I checked the hazard specific to this file: each fixture's source lines contain
an **even** number of literal `"""` sequences (the `""" + TRIPLE_QUOTE + """` splice line carries
two), so the self-scan's raw-string state machine stays balanced — which the now-throwing
`dropRawStringInteriors` would have caught immediately otherwise. Recorded so a future reviewer does
not re-derive it.

---

## Explicitly checked and found clean

Recorded so round 6 does not re-spend the budget:

- **`(?<=:")` is genuinely fixed-width and Java-legal.** Both characters are regex-literals; the two
  starts remain two *separate* fixed-width lookbehinds in a non-capturing alternation, never one
  lookbehind over differing widths. The composed pattern compiles and runs for all three consumers.
- **No new backtracking surface.** The narrowing strictly reduces candidate start positions and adds
  no quantifier, no nesting and no alternation inside a look-back.
- **`authHeaderRegex` composes correctly with the new start.** Measured: `{"notes":"X-API-Key:
  SECRETAUTH1"}` → `{"notes":"X-API-Key: [REDACTED]"}` under STRICT.
- **`decodeKotlinStringLiteral` is correct and fails closed** on any escape other than `\"` / `\\`,
  rather than silently passing it through.
- **The `THIRD_OPEN_FINDING` rationale-region pin is well-scoped.** `AR-27-11` appears inside the
  region `rationaleRegionAboveFragments()` isolates (`Redaction.kt:139-297`, 157 comment lines), so
  the assertion reads the block a maintainer actually meets and not the whole file.
- **The blast-radius gate is non-degenerate and its byte-identity tail assertion is real.**
  `assertEquals(serialized.substringAfter(sentinel.value), redacted.substringAfter("Cookie: [STRIPPED]"))`
  cannot pass on a no-op, because `redacted.contains("Cookie: [STRIPPED]")` is asserted first.
- **The three new sentinels are distinct and non-substring** against the existing set, enforced by
  `everySentinelInThisFileIsDistinct` deriving from `entries` rather than a hand list.
- **`ALLOWLIST` is still `emptyMap()`**, so the tree scan's green is a true zero and not an
  allowlisted zero.
- **The "7 benign-control assertions" claim is exact** — 7 sites, all in
  `SerializedEmissionRedactionTest`, none added by 27-14.
- `./gradlew test` green for `com.six2dez.burp.aiagent.redact.*` and
  `com.six2dez.burp.aiagent.mcp.tools.*`; `ktlintCheck` and `detekt` green.
- English-only comments and code; no hardcoded secrets; no `eval`-class constructs; no path handling,
  no deserialization and no empty catch blocks in the changed files.
- The working tree was restored byte-identically after the revert experiment
  (`git diff --quiet -- src/` → no diff) and `build/classes` recompiled from the restored source.

**Not re-reported (owned elsewhere):** the `jacocoTestCoverageVerification` redact BRANCH 0.9278
shortfall at `Redaction.kt:1628`; the `RedactionTest` boundary-sweep wall-clock flake; `AR-27-04`,
`AR-27-08` and `InjectionPointExtractor.kt:29`. I found no second, unrelated coverage or correctness
problem in the four files under review.

---

_Reviewed: 2026-08-26_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
