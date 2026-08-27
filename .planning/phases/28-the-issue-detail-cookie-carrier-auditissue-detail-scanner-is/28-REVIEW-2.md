---
phase: 28-the-issue-detail-cookie-carrier-auditissue-detail-scanner-is
reviewed: 2026-08-27T19:45:38Z
depth: standard
round: 2
diff_base: e92218f8f5c5cecefcf140710805e820a46924d5
files_reviewed: 6
files_reviewed_list:
  - src/main/kotlin/com/six2dez/burp/aiagent/scanner/AiScanCheck.kt
  - src/main/kotlin/com/six2dez/burp/aiagent/scanner/ScannerIssueSupport.kt
  - src/test/kotlin/com/six2dez/burp/aiagent/redact/CookieCarrierInventoryTest.kt
  - src/test/kotlin/com/six2dez/burp/aiagent/scanner/AiScanCheckDetailCookieCarrierTest.kt
  - src/test/kotlin/com/six2dez/burp/aiagent/scanner/CookieRouteDispositionTest.kt
  - src/test/kotlin/com/six2dez/burp/aiagent/scanner/IssueDetailCookieCarrierTest.kt
findings:
  critical: 1
  warning: 6
  info: 5
  total: 12
status: issues_found
---

# Phase 28 (Round 2): Code Review Report

**Reviewed:** 2026-08-27T19:45:38Z
**Depth:** standard
**Files Reviewed:** 6
**Status:** issues_found

## Summary

Round 2 shipped two things: `ScannerIssueSupport.sanitizeRenderedPayload` (28-04, closing round-1
`CR-01`) and `AiScanCheck.sanitizeCookiePointText` / `isCookieInsertionPoint` (28-05, closing round-1
`CR-02`), plus the inventory/tripwire bookkeeping in 28-06.

**Both round-1 CRs are genuinely closed as specified, and the verification is not vacuous.** Measured
directly rather than taken on trust:

- `isCookieInsertionPoint` compares against `AuditInsertionPointType.PARAM_COOKIE`, and that constant
  exists with that exact spelling in the resolved `montoya-api-2026.2.jar` (`javap` confirmed). The
  round-1 hazard — reusing `Redaction.isCookieParameterType`, which compares against the string
  `"COOKIE"` and is FALSE for `PARAM_COOKIE` — was correctly avoided and is pinned by an assertion.
- `AiScanCheckDetailCookieCarrierTest` is non-vacuous. The fixture pin proves the mock returns
  `PARAM_COOKIE` and the sentinel; `cookieBaseValueSurvivesUnderOff` proves the sentinel reaches the
  output at all; and `urlParamInsertionPointSurvivesStrict_attributionControl` proves that
  `Redaction.apply` under STRICT does **not** remove that sentinel by any other rule. A revert of the
  gate therefore turns `cookieBaseValueIsStrippedUnderStrict` red for the right reason.
- The `companion object` move is behaviour-neutral: both functions are pure, read no instance state,
  and `buildDetail` resolves them unqualified. No state was lost.
- The `isCommentOnly` narrowing in `CookieCarrierInventoryTest` is correct and does **not** over-match.
  I re-ran the scan under both the old and the new heuristic across all of `src/main/kotlin`: the only
  delta is `AiScanCheck.kt` going from 1 to 2 `.baseValue()` sites (73 -> 74 total, 12 files), which is
  exactly what `MEASURED_CARRIER_SITES` and `EXPECTED_TOTAL_CARRIER_SITES` were re-pinned to. No other
  per-file/per-accessor count moved.
- `./gradlew detekt ktlintCheck` is green and `detekt-baseline.xml` is unchanged (QUAL-07 held). All
  four test classes pass.

What the round did **not** close, and where the defects are:

1. One **BLOCKER**: both controls are applied at issue-*construction* time, but `AuditIssue.detail()`
   is a stored string that `scanner_issues` reads back out of `api.siteMap()`. An issue produced while
   `privacyMode = OFF` keeps the raw cookie value forever and is emitted verbatim after the operator
   switches to STRICT — a fact this round's own green test proves, and which nothing in phase 28
   records.
2. The route-2 gate is **fail-open** for every insertion-point type other than `PARAM_COOKIE`,
   including `EXTENSION_PROVIDED`, which is what Montoya's `AuditInsertionPoint.type()` default method
   actually returns. That residual is absent from the register's own "STILL OPEN" enumeration.
3. Route 2's `**Payload Used:**` gate has **zero tests**, while
   `CookieCarrierInventoryTest.ISSUE_DETAIL_CARRIER_DISPOSITION` claims a committed probe for it.
4. The comment heuristic was fixed in **one** file and left broken in the sibling file edited in the
   same round — including in the helper that backs the new route-2 predicate tripwire.

Findings previously raised in `28-REVIEW.md` (round 1) are not repeated here.

---

## Critical Issues

### CR-01: The cookie control is a write-time snapshot; issues scanned under OFF are emitted verbatim under STRICT

**File:** `src/main/kotlin/com/six2dez/burp/aiagent/scanner/AiScanCheck.kt:356-404` (and
`:101-112`); `src/main/kotlin/com/six2dez/burp/aiagent/scanner/ScannerIssueSupport.kt:206-227`

**Issue:** Both controls read `getSettings().privacyMode` **when the `AuditIssue` is built** and bake
the result into an immutable `String`. That string is stored in Burp's site map and read back later:

- `McpToolExecutorImpl.kt:605` — `"scanner_issues" -> { val issues = api.siteMap().issues() ... }`
- `Serialization.kt:14` — `detail = detail()`

The privacy mode is a **runtime toggle** the product documents as pre-flight ("choose STRICT before
talking to a cloud backend"). The sequence below leaks:

1. Operator scans with `privacyMode = OFF`. `buildDetail` renders
   `**Original Value:** <raw session cookie>` and Burp stores it.
2. Operator switches to STRICT and connects a cloud backend / MCP client.
3. `scanner_issues` returns the stored `detail` — with the raw cookie value intact.

`Redaction.apply` cannot rescue this, and that is not an inference: it is proven by a **green test
shipped in this round**. `AiScanCheckDetailCookieCarrierTest.urlParamInsertionPointSurvivesStrict_attributionControl`
asserts that under `PrivacyMode.STRICT` the string `**Original Value:** cedar-anchor-marble-feather`
survives `Redaction.apply` **verbatim**. The redactor is structurally blind to this rendered shape
(the phase-27 measurement quoted in `ScannerIssueSupport.kt:60-63`), so the write-time decision is the
only decision there ever is.

`AiScanCheck.consolidateIssues` (`:101-112`) makes it stickier: on a re-scan of the same
name + normalized URL it returns `ConsolidationAction.KEEP_EXISTING`, so the stale OFF-mode issue
**suppresses** the freshly-built STRICT-mode one. Re-scanning under STRICT does not repair the site
map.

Neither the round-1 review nor any phase-28 KDoc, decision (D-28-01 / D-28-06 / D-28-07 / D-28-08) or
register entry records this. `ScannerIssueSupport.kt:150-157` justifies the write-site placement
purely on `InjectionType` availability and never mentions that the placement makes the control
non-retroactive. `ISSUE_DETAIL_CARRIER_DISPOSITION` enumerates what is "STILL OPEN" (the `Evidence:`
line, the missing repo-wide gate) and does not list this. PRIV-05's guarantee as an operator would
read it — "STRICT strips cookie values from `scanner_issues`" — is false for every issue created in a
different mode.

**Fix:** two options; pick one and record it.

Preferred — make the emission path re-render rather than replay. Keep the write-site marker for the
Burp UI, and add a type-preserving stripping pass at the MCP boundary so `detail` is filtered against
the *live* policy before it leaves the product. The type is gone by then, so the honest version is a
line-prefix filter on the two owned prefixes at the emission site, applied only when
`policy.stripCookies` is set:

```kotlin
// McpToolExecutorImpl / Serialization boundary, live policy
private val OWNED_DETAIL_PREFIXES =
    listOf("Original Value: ", "Payload Used: ", "**Original Value:** ", "**Payload Used:** ")

internal fun scrubStoredIssueDetail(detail: String, policy: RedactionPolicy): String =
    if (!policy.stripCookies) {
        detail
    } else {
        detail.lineSequence().joinToString("\n") { line ->
            val prefix = OWNED_DETAIL_PREFIXES.firstOrNull { line.trimStart().startsWith(it) }
            if (prefix == null) line
            else line.substringBefore(prefix) + prefix +
                ScannerIssueSupport.INJECTION_VALUE_STRIPPED_MARKER
        }
    }
```

This is shape-keyed and therefore weaker than the write-site gate — it is a **second** layer, not a
replacement for it, and it must be documented as such so a future reader does not delete the
type-keyed gate as redundant. Note `IssueUtils.formatIssueDetailHtml` joins route 1's lines with
`<br>`, so the split must be on `<br>` for that route; verify before shipping.

Minimum acceptable alternative — if the maintainer accepts the residual, say so explicitly: add the
non-retroactivity to `ISSUE_DETAIL_CARRIER_DISPOSITION`'s "STILL OPEN" clause, add a `consolidateIssues`
note, and surface it in the UI next to the privacy-mode selector ("changing this mode does not
re-render issues already recorded"). Silently shipping a control an operator will read as retroactive
is the option that is not available.

---

## Warnings

### WR-01: Route 2's gate is fail-open for `EXTENSION_PROVIDED` / `USER_PROVIDED` / `HEADER` insertion points that carry cookie bytes

**File:** `src/main/kotlin/com/six2dez/burp/aiagent/scanner/AiScanCheck.kt:459`, `:474-486`

**Issue:** `isCookieInsertionPoint` fires only on `AuditInsertionPointType.PARAM_COOKIE`. Everything
else takes the pass-through branch. In route 1's `InjectionType` enum the only cookie-capable member
*is* `COOKIE`, so D-28-01's pass-through was safe by construction. **That is not true of Montoya's
enum.** Decompiled from `montoya-api-2026.2.jar`:

```
public default AuditInsertionPointType type();
    0: getstatic  AuditInsertionPointType.EXTENSION_PROVIDED
    3: areturn
```

`type()` is a **default method returning `EXTENSION_PROVIDED`**. Any insertion point built through the
public factory `AuditInsertionPoint.auditInsertionPoint(name, request, startIndexInclusive,
endIndexExclusive)` — which takes an arbitrary byte range, cookie bytes included — reports
`EXTENSION_PROVIDED` unless the implementer overrides `type()`. Points supplied by another extension
via `registerAuditInsertionPointProvider` reach **every** registered `PER_INSERTION_POINT` check,
including this one (`App.kt:214-215`). `USER_PROVIDED` (operator-marked bytes) and `HEADER` (a
`Cookie:` header insertion point) are the same story. In all three cases `baseValue()` is the cookie
value and `buildDetail` renders it verbatim under STRICT.

The gate is not wrong to be type-keyed; it is wrong to be *silent about it*.
`ISSUE_DETAIL_CARRIER_DISPOSITION` (`CookieCarrierInventoryTest.kt:735-767`) carefully enumerates what
is "STILL OPEN, NAMED SO NOBODY READS THIS AS A CLOSURE" — the `Evidence:` line, the missing repo-wide
gate — and does not name this one. A reader takes route 2's cookie carrier as covered.

**Fix:** either widen the predicate to a set, or record the residual. Widening is cheap and keeps the
closed-enum discipline intact:

```kotlin
private val COOKIE_CAPABLE_INSERTION_POINT_TYPES =
    setOf(
        AuditInsertionPointType.PARAM_COOKIE,
        // Cookie bytes can be selected by an operator or supplied by another extension under these
        // types; `type()`'s DEFAULT implementation returns EXTENSION_PROVIDED, so this is the
        // fail-open case, not an exotic one.
        AuditInsertionPointType.USER_PROVIDED,
        AuditInsertionPointType.EXTENSION_PROVIDED,
    )

internal fun isCookieInsertionPoint(insertionPoint: AuditInsertionPoint): Boolean =
    insertionPoint.type() in COOKIE_CAPABLE_INSERTION_POINT_TYPES ||
        (insertionPoint.type() == AuditInsertionPointType.HEADER &&
            Redaction.isCookieHeaderName(insertionPoint.name()))
```

Note this changes the pass-through cost (an extension-provided point over a non-cookie field is now
stripped under STRICT), so it is a trade to state, not to slip in. If the trade is rejected, add the
three types to the "STILL OPEN" clause by name and update `CookieRouteDispositionTest`'s fixtures.

### WR-02: Route 2's `**Payload Used:**` gate is untested, and the register claims a probe for it

**File:** `src/main/kotlin/com/six2dez/burp/aiagent/scanner/AiScanCheck.kt:392`;
`src/test/kotlin/com/six2dez/burp/aiagent/scanner/AiScanCheckDetailCookieCarrierTest.kt` (whole file);
`src/test/kotlin/com/six2dez/burp/aiagent/redact/CookieCarrierInventoryTest.kt:747-754`

**Issue:** `grep -n "Payload Used" src/test/kotlin/.../AiScanCheckDetailCookieCarrierTest.kt` returns
**nothing but KDoc prose**. Not one assertion in that file reads the `**Payload Used:**` line under any
mode. Deleting `sanitizeCookiePointText(...)` from `AiScanCheck.kt:392` and restoring
`payload.value.take(500)` keeps the whole suite green.

That is a coverage gap on a defence-in-depth control, which is tolerable on its own. What is not
tolerable is that the register states otherwise. `ISSUE_DETAIL_CARRIER_DISPOSITION` reads:

> FOUR LINES ARE CONTROLLED NOW: ... (3) and (4) `AiScanCheck.sanitizeCookiePointText` on BOTH detail
> lines of the SECOND producer ... **COMMITTED PROBES:** ... `AiScanCheckDetailCookieCarrierTest`
> for (3) and (4)

There is no probe for (4). This is exactly the overclaim class phase 28 exists to correct, applied to
phase 28's own record — and it is the concrete mechanism by which the untested gate gets deleted later
("the KDoc says it isn't a carrier, and nothing goes red").

**Fix:** add the missing assertions — the fixture is already there:

```kotlin
@Test
fun cookiePayloadLineIsStrippedUnderStrict() {
    val detail = detailFor(cookieInsertionPoint(), PrivacyMode.STRICT)
    assertTrue(
        detail.contains("```\n${ScannerIssueSupport.INJECTION_VALUE_STRIPPED_MARKER}\n```"),
        "STRICT: route 2's payload line is DEFENCE IN DEPTH, not a measured carrier — but it is a " +
            "shipped control and an unmeasured control is a deleted control. Detail was: $detail",
    )
    assertFalse(detail.contains(PAYLOAD.value), "Detail was: $detail")
}

@Test
fun cookiePayloadLineSurvivesUnderOff() {
    assertTrue(detailFor(cookieInsertionPoint(), PrivacyMode.OFF).contains(PAYLOAD.value))
}

@Test
fun urlParamPayloadLineSurvivesUnderStrict_attributionControl() {
    assertTrue(detailFor(urlParamInsertionPoint(), PrivacyMode.STRICT).contains(PAYLOAD.value))
}
```

If instead the decision is that the line should not be gated at all, remove the gate and correct the
register. Either way the register and the tree must agree.

### WR-03: `CookieRouteDispositionTest` kept the exact comment heuristic 28-06 fixed next door, and its new route-2 tripwire is defeatable inside the file it guards

**File:** `src/test/kotlin/com/six2dez/burp/aiagent/scanner/CookieRouteDispositionTest.kt:370-382`

**Issue:** 28-06 narrowed `CookieCarrierInventoryTest.isCommentOnly` because
`trimmed.startsWith("*")` swallows raw-string markdown lines. The same round **refactored**
`CookieRouteDispositionTest`'s stripper into a new shared helper `matchingCodeLinesIn` — and kept the
broken predicate verbatim:

```kotlin
trimmed.startsWith("//") || trimmed.startsWith("*") || trimmed.startsWith("/*")
```

Measured across `src/main/kotlin`, exactly **7** lines are misclassified by that rule, and all 7 are
`AiScanCheck.kt:385, 387, 388, 390, 395, 396, 398` — i.e. the *entire* route-2 detail template,
including the two lines this round controls. So a second cookie predicate written as a raw-string
interpolation, in the very file the new tripwire declares as the rule's `INSERTION_POINT_OWNER`, is
invisible to it:

```kotlin
**Original Value:** ${if (insertionPoint.type() == AuditInsertionPointType.PARAM_COOKIE) "x" else y}
```

`exactlyOneInsertionPointCookieTypePredicateExistsInMainSource` still counts 1 and stays green, which
is the precise bypass its assertion message says it exists to catch. The same defect remains in 13
other test files (`SerializedEmissionSiteInventoryTest.kt:355`, `LogicalLineBoundaryScopeTest.kt:417`,
`ParameterCarrierRedactionTest.kt:627`, `CookieHeaderRuleOwnershipTest.kt:115`,
`RedactingPolicySurvivalSweepTest.kt:883`, `EvidenceTailReachTest.kt:174`, and others).

**Fix:** promote 28-06's narrowed predicate to one shared test utility and route every scanner through
it, rather than fixing one copy:

```kotlin
// src/test/kotlin/com/six2dez/burp/aiagent/testsupport/SourceScanning.kt
internal fun isCommentOnly(line: String): Boolean {
    val trimmed = line.trimStart()
    return trimmed.startsWith("//") || trimmed.startsWith("/*") ||
        trimmed == "*" || trimmed.startsWith("* ") || trimmed.startsWith("*\t") ||
        trimmed.startsWith("*/")
}
```

At minimum, fix `CookieRouteDispositionTest.matchingCodeLinesIn` in this round — it is the helper
backing the control this round shipped.

### WR-04: The new route-2 tripwire cannot see the most idiomatic second spelling on Kotlin 2.1

**File:** `src/test/kotlin/com/six2dez/burp/aiagent/scanner/CookieRouteDispositionTest.kt:475-482`

**Issue:** `INSERTION_POINT_COOKIE_TYPE_COMPARISONS` matches only the **fully qualified** constant:

```kotlin
Regex("""[=!]= AuditInsertionPointType\.PARAM_COOKIE""")
Regex("""AuditInsertionPointType\.PARAM_COOKIE [=!]=""")
```

`build.gradle.kts:7` pins `kotlin("jvm") version "2.1.21"`, and K2 permits **unqualified enum entries
in a `when` over an enum subject** with no import. A second predicate written as

```kotlin
when (insertionPoint.type()) {
    PARAM_COOKIE -> INJECTION_VALUE_STRIPPED_MARKER
    else -> raw.take(maxChars)
}
```

compiles, is idiomatic for this codebase (both shipped gates are two-arm `when`s), and matches neither
regex. The count stays 1 and the tripwire stays green while two divergent predicates exist. The
route-1 population's exclusion list explicitly calls out the analogous `"COOKIE" ->` arm case (class
KDoc item 1); the new population's exclusion list (item 3) does not, so the gap is not recorded either.

**Fix:** add the `when`-arm spelling to the list with its own fixture, and state the residual:

```kotlin
// unqualified `when` arm over an AuditInsertionPointType subject — legal without import on K2
Regex("""^\s*PARAM_COOKIE\s*->"""),
```

with fixture `"""            PARAM_COOKIE -> INJECTION_VALUE_STRIPPED_MARKER"""`. If the bare spelling
is judged too collision-prone, say so in item 3 the way item 1 does, rather than leaving it unstated.

### WR-05: `anAbsentInsertionPointTypeDoesNotThrowAndPassesThrough` documents a premise that is false against the shipped Montoya jar

**File:** `src/test/kotlin/com/six2dez/burp/aiagent/scanner/AiScanCheckDetailCookieCarrierTest.kt:241-260`
(with the `untypedInsertionPoint()` fixture)

**Issue:** The KDoc asserts:

> `AuditInsertionPoint.type()` is a Java DEFAULT method returning a platform type, so Kotlin cannot
> guarantee it is non-null and **a real Burp implementation may not override it**.

The second clause is wrong, and it is checkable in one command. The default body in
`montoya-api-2026.2.jar` is `getstatic AuditInsertionPointType.EXTENSION_PROVIDED; areturn` — an
implementation that does not override `type()` returns `EXTENSION_PROVIDED`, **never null**. The null
in this test exists solely because Mockito intercepts default methods and answers `null`; it is a
mocking artifact with no production counterpart.

Two costs. First, in a codebase whose whole discipline is "measured, not assumed", this is an assumed
claim that is wrong, in a file whose class KDoc lectures the reader about exactly that. Second, it
buries the finding that actually matters: the real behaviour of an unoverridden `type()` is
`EXTENSION_PROVIDED`, which is the fail-open case in WR-01. The test currently enshrines
"pass through when the type is unknown" as *desired* behaviour for a privacy gate.

**Fix:** correct the KDoc to say what was measured, and add the assertion that matters:

```kotlin
@Test
fun theUnoverriddenTypeDefaultIsExtensionProvidedAndTakesThePassThroughBranch() {
    // MEASURED from montoya-api: AuditInsertionPoint.type()'s default body returns
    // EXTENSION_PROVIDED, never null. The null case below is a Mockito artifact retained only to
    // pin that the identity compare does not throw on it.
    assertEquals(
        AuditInsertionPointType.EXTENSION_PROVIDED,
        object : AuditInsertionPoint { /* name/baseValue/... , type() NOT overridden */ }.type(),
    )
}
```

and cross-reference WR-01 so the two are read together.

### WR-06: The fixture-pin test does not run before the assertions it is said to protect

**File:** `src/test/kotlin/com/six2dez/burp/aiagent/scanner/AiScanCheckDetailCookieCarrierTest.kt:86-93`

**Issue:** The KDoc says "THE FIXTURE PIN, **and it runs before any behavioural assertion is read as
evidence**." There is no `@TestMethodOrder`/`MethodOrderer` on the class and no
`junit-platform.properties` anywhere under `src/test` (checked). JUnit 5's default method order is
deterministic but arbitrary, and it is explicitly documented as not to be relied upon. Running a single
method (`--tests '*cookieBaseValueIsStrippedUnderStrict'`) — which is what a developer does while
iterating, and what a red-probe run does — executes the behavioural assertion with the fixture pin
never having run.

The suite as a whole is still non-vacuous (`cookieBaseValueSurvivesUnderOff` and the attribution
control cover the same ground), so this is a claim defect, not a live hole. But it is stated as a
guarantee.

**Fix:** either make it true —

```kotlin
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class AiScanCheckDetailCookieCarrierTest {
    @Test @Order(0) fun theInsertionPointMockActuallyReturnsWhatItWasStubbedToReturn() { ... }
```

— or, better, move the two `assertEquals` into the `cookieInsertionPoint()` factory so every caller
carries the pin by construction, and reword the KDoc.

---

## Info

### IN-01: ~90 lines of extractor logic duplicated between the two carrier tests

**File:** `src/test/kotlin/com/six2dez/burp/aiagent/scanner/AiScanCheckDetailCookieCarrierTest.kt:454-520`

**Issue:** `detailFieldOf`, `requestFieldOf`, `parsedOrNull`, `fallbackStringField` and
`trailingBackslashCount` are copy-pasted from `IssueDetailCookieCarrierTest`. The stated reason ("that
file's are private companion members") is a reason to *extract them*, not a reason to copy. The copies
have already diverged: this one throws `AssertionError` on an unterminated field while the original
still `return ""`s (`IssueDetailCookieCarrierTest.kt:1063`, round-1 `IN-01`).

**Fix:** extract to `src/test/kotlin/.../testsupport/RedactedBlobExtractors.kt` and have both classes
call it, so a fix to one is a fix to both.

### IN-02: `PAYLOAD` companion initialiser can fail with `ExceptionInInitializerError`

**File:** `src/test/kotlin/com/six2dez/burp/aiagent/scanner/IssueDetailCookieCarrierTest.kt:~860`

**Issue:** `PayloadGenerator().generateContextAwarePayloads(VulnClass.SQLI, DETAIL_SENTINEL, 5).first()`
runs in companion-object init. `generateContextAwarePayloads` returns `emptyList()` for any class other
than SQLI/IDOR/BOLA, and `generateSqliPayloads` builds its list conditionally. If it ever returns
empty, `.first()` throws `NoSuchElementException` inside `<clinit>`, which JUnit surfaces as
`ExceptionInInitializerError` on every method in the class with no indication of the real cause — the
opposite of the fail-loud behaviour the rest of this file is careful about.

**Fix:** `.firstOrNull() ?: error("FIXTURE: generateContextAwarePayloads(SQLI, ...) returned no payload; the derived fixture cannot be built and every payload assertion in this class would be vacuous")`.

### IN-03: `withDetailLineControlsApplied`'s prefix keys assume the payload survives JSON encoding unescaped

**File:** `src/test/kotlin/com/six2dez/burp/aiagent/scanner/IssueDetailCookieCarrierTest.kt:1079-1115`

**Issue:** The KDoc states both substitutions are "keyed on rendered LINE PREFIXES, which survive JSON
encoding unescaped". True of the *prefixes*, but the payload substitution's key is
`"$PAYLOAD_USED_PREFIX${payloadRenderedFor(cookiePoint(), PrivacyMode.OFF)}"` — it embeds the whole
rendered payload. `generateSqliPayloads` currently emits only single quotes, so the key happens to
survive. A generator change introducing `"` or `\` into a SQLi payload makes the key never match the
serialized blob, and `theOnlyThreeDifferencesBetweenStrictAndOffAreTheEnumeratedControls` fails with an
"unenumerated difference" message pointing at the wrong cause.

**Fix:** apply the substitutions to the JSON-encoded form of the key
(`toolJson.encodeToString(key).trim('"')`), or run the prediction on the extracted `detail` field only
and compare the remaining blob structurally.

### IN-04: `sanitizeCookiePointText` decouples the text from the point it claims to describe

**File:** `src/main/kotlin/com/six2dez/burp/aiagent/scanner/AiScanCheck.kt:474-486`

**Issue:** Unlike its route-1 siblings, which take the typed `InjectionPoint` / `Payload` and read the
value *from* it, this helper takes `raw: String` and `maxChars: Int` as free parameters. Nothing binds
`raw` to `insertionPoint`, and nothing binds `maxChars` to `raw`'s role — the call site is free to pass
`payload.value` with `ORIGINAL_VALUE_MAX_CHARS`, or text from a different point entirely, and it still
compiles and still reports as "controlled". For a function whose whole value is being the one gate two
lines call, that is more surface than it needs.

**Fix:** split into `sanitizeCookiePointOriginalValue(insertionPoint, policy)` and
`sanitizeCookiePointPayload(insertionPoint, payload, policy)`, each reading its own source and its own
bound — matching the shape `sanitizeInjectionPointValue` / `sanitizeRenderedPayload` already use. Note
this adds a function to the file; keep both in the companion so `TooManyFunctions` stays green.

### IN-05: Unused local `settings` in `testPayload`

**File:** `src/main/kotlin/com/six2dez/burp/aiagent/scanner/AiScanCheck.kt:181`

**Issue:** `val settings = getSettings()` is declared and never read anywhere in `testPayload`
(`:175-262`). It is a live `getSettings()` invocation on every payload of every insertion point of
every scan — a settings load per probe, for nothing. Pre-existing, not introduced by this round, but it
sits three lines from code this round touched and is a misleading signal that this function is
policy-aware when it is not.

**Fix:** delete the line.

---

_Reviewed: 2026-08-27T19:45:38Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
_Round: 2 (diff base `e92218f8`)_
