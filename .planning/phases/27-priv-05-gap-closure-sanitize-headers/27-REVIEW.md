---
phase: 27-priv-05-gap-closure-sanitize-headers
reviewed: 2026-08-26T00:00:00Z
depth: standard
files_reviewed: 16
files_reviewed_list:
  - src/main/kotlin/com/six2dez/burp/aiagent/redact/Redaction.kt
  - src/main/kotlin/com/six2dez/burp/aiagent/mcp/tools/McpToolHelpers.kt
  - src/main/kotlin/com/six2dez/burp/aiagent/mcp/tools/McpToolExecutorImpl.kt
  - src/main/kotlin/com/six2dez/burp/aiagent/mcp/tools/McpToolLegacy.kt
  - src/main/kotlin/com/six2dez/burp/aiagent/prompts/bountyprompt/BountyPromptTagResolver.kt
  - src/main/kotlin/com/six2dez/burp/aiagent/scanner/PassiveAiScannerFilters.kt
  - src/test/kotlin/com/six2dez/burp/aiagent/mcp/tools/McpToolHelpersTest.kt
  - src/test/kotlin/com/six2dez/burp/aiagent/mcp/tools/ParameterCarrierRedactionTest.kt
  - src/test/kotlin/com/six2dez/burp/aiagent/mcp/tools/SerializedEmissionRedactionTest.kt
  - src/test/kotlin/com/six2dez/burp/aiagent/mcp/tools/SerializedEmissionSiteInventoryTest.kt
  - src/test/kotlin/com/six2dez/burp/aiagent/prompts/bountyprompt/BountyPromptTagResolverTest.kt
  - src/test/kotlin/com/six2dez/burp/aiagent/redact/CookieCarrierInventoryTest.kt
  - src/test/kotlin/com/six2dez/burp/aiagent/redact/CookieHeaderNameParityTest.kt
  - src/test/kotlin/com/six2dez/burp/aiagent/redact/CookieHeaderRuleOwnershipTest.kt
  - src/test/kotlin/com/six2dez/burp/aiagent/redact/LogicalLineBoundaryScopeTest.kt
  - src/test/kotlin/com/six2dez/burp/aiagent/scanner/PassiveAiScannerHeaderAdmissionTest.kt
findings:
  critical: 2
  warning: 8
  info: 0
  total: 10
status: issues_found
---

# Phase 27: Code Review Report

**Reviewed:** 2026-08-26
**Depth:** standard
**Files Reviewed:** 16
**Status:** issues_found

## Summary

The diff does what it claims on the axes it chose: `sanitizeParameters` is a genuine type-keyed
control, it is the only `ParsedParam` producer, both executors route through it, and the
logical-line composer really does close the JSON-escaped-newline blind spot for the cookie and
16-name auth-header classes. All of that was verified by running the suite (green), by
`ktlintCheck`/`detekt` (green), and by re-deriving the carrier scan independently (72 sites, exact
match).

The two BLOCKERs below were found by driving the **shipped compiled classes** directly, not by
reading comments. Both are cases where this phase's own new tests contain a **green assertion that a
sensitive value survives a redacting policy** — the exact artifact the phase's own security record
says it exists to stop producing.

Verification method for both: `Redaction.INSTANCE.apply(blob, RedactionPolicy.fromMode(STRICT),
"probe-salt", false)` against `build/classes/kotlin/main`. Raw output is quoted inline.

`InjectionPointExtractor.kt:29` and `AR-27-08` (the `AuditIssue.detail()` carrier) are excluded from
this report as tracked, per phase scope.

## Critical Issues

### CR-01: An underscore in a cookie header name defeats redaction on the prompt path — and a new test pins the leak green

**File:** `src/main/kotlin/com/six2dez/burp/aiagent/redact/Redaction.kt:119` (`COOKIE_NAME_PART`),
`src/test/kotlin/com/six2dez/burp/aiagent/redact/CookieHeaderNameParityTest.kt:170-201`
(`thePredicateIsDeliberatelyWiderThanTheTwoRegexes`)

**Issue:**
`COOKIE_NAME_PART = "[A-Za-z0-9-]*"` excludes `_`, but `_` is a legal RFC 9110 `tchar` and therefore
a legal HTTP field-name character. `Redaction.isCookieHeaderName` (a bare `contains("cookie")`)
returns **true** for such names, so:

- `PassiveAiScannerFilters.sanitizeHeadersForPrompt` (edited in this diff, line 185) **admits**
  `my_cookie` / `X_Cookie` / `session_cookie` into the passive-scan prompt *precisely because the
  shared predicate classifies them as cookie headers*;
- `PassiveAiScannerPrompts.buildScanMetadataText:110-112` emits each admitted header as its own
  column-0 line;
- `Redaction.apply` then **does not strip it**, because neither `cookieHeaderRegex` nor
  `setCookieHeaderRegex` can match a name containing `_`.

Measured on the shipped classes, STRICT:

```
== my_cookie [STRICT]
GET / HTTP/1.1
Host: host-24a17739c0c6.local
my_cookie: SECRETVALUE          <-- leaked

== X_Cookie [STRICT]         -> X_Cookie: SECRETVALUE       (leaked)
== session_cookie [STRICT]   -> session_cookie: SECRETVALUE (leaked)
== Cookie [STRICT]           -> Cookie: [STRIPPED]
== X-Cookie [STRICT]         -> X-Cookie: [STRIPPED]

isCookieHeaderName("my_cookie") = true
```

This is a live cookie-value disclosure to a third-party AI backend under the **strongest** privacy
mode, in the exact class PRIV-05 owns. It also produces a hard inconsistency between the two paths
the phase set out to unify: `McpToolHelpers.sanitizeHeaders` **does** strip `my_cookie` (it uses the
predicate), while `Redaction.apply` does not — the same divergence the v0.10.0 audit found, merely
inverted.

Worse, `CookieHeaderNameParityTest.thePredicateIsDeliberatelyWiderThanTheTwoRegexes` asserts the
survival as correct behaviour under **STRICT and BALANCED**:

```kotlin
assertTrue(
    output.contains(sentinel),
    "$mode: the prompt path must NOT strip '$name' ...",
)
```

`27-02-PLAN.md:26` frames the only two options as "narrow the predicate" or "drop `my_cookie` from
the corpus" and rejects both. It never considers the third and correct one: **widen the regex side**
so the two agree in the safe direction. As written, the asymmetry is documented as "fail-safe"
while the failing side is the *redacting* side.

**Fix:** widen the name-part class to the RFC 9110 tchar subset that actually occurs, and flip the
test to assert stripping.

```kotlin
// Redaction.kt — '_' is a legal RFC 9110 tchar and appears in real vendor header names.
// Keeping it out of this class makes the two prompt-path cookie rules NARROWER than
// isCookieHeaderName, which is the unsafe direction: sanitizeHeadersForPrompt admits such a
// header precisely because the predicate claims it, and Redaction.apply then leaks the value.
private const val COOKIE_NAME_PART = "[A-Za-z0-9_-]*"
```

Then in `CookieHeaderNameParityTest`, replace
`thePredicateIsDeliberatelyWiderThanTheTwoRegexes` with the symmetric assertion for `my_cookie`
(`assertTrue(output.contains("my_cookie: [STRIPPED]"))`) and add `X_Cookie` / `session_cookie` to
`PARITY_CORPUS`. Keep the one-directional invariant test as-is; it stays true.

---

### CR-02: STRICT host anonymization is inoperative on every serialized MCP emission, and this diff adds a green assertion that a real hostname survives STRICT

**File:** `src/main/kotlin/com/six2dez/burp/aiagent/redact/Redaction.kt:1894` (`hostHeaderRegex`),
`src/test/kotlin/com/six2dez/burp/aiagent/mcp/tools/McpToolHelpersTest.kt:245-250` and `:283-288`

**Issue:**
`hostHeaderRegex = Regex("(?im)^host:\\s*([^\\s]+)\\s*$")` was deliberately excluded from the new
`logicalLineHeaderRule` composer (D-27-13 / `LogicalLineBoundaryScopeTest.EXCLUDED_RULE`). The
consequence, measured:

```
IN : {"request":"GET / HTTP/1.1\r\nHost: api.example.com\r\nCookie: a=SECRET\r\n\r\n"}
OUT: {"request":"GET / HTTP/1.1\r\nHost: api.example.com\r\nCookie: [STRIPPED]\r\n\r\n"}
                                        ^^^^^^^^^^^^^^^ real host survives STRICT
```

`RedactionPolicy.fromMode(STRICT)` sets `anonymizeHosts = true`, and host anonymization is a
user-visible, pre-flight privacy promise in the settings UI. On the serialized emission shape it
does nothing. And the host is carried a second time with **no anonymizer anywhere in the pipeline**:

- `mcp/schema/Serialization.kt:79` — `SiteMapEntry.url = req?.url()`, verbatim (`site_map`,
  `site_map_regex`);
- `mcp/schema/Serialization.kt:16-22` — `IssueDetails.httpService.host` and `baseUrl`, verbatim
  (`scanner_issues`).

`maybeAnonymizeUrl` exists (`McpToolHelpers.kt:401`) and is applied on `request_parse` only. So under
STRICT the extension ships real target hostnames to the configured third-party provider through at
least five tools.

This diff makes it worse in the way that matters for a security review: it adds a **green** assertion
that the leak is expected.

```kotlin
// McpToolHelpersTest.kt:245-250, inside cookieVariantsAreStrippedEndToEndThroughRedactIfNeeded
assertTrue(
    rawMessageFinalText.contains("api.example.com"),
    "measured AR-27-04: the line-anchored host rule cannot fire on the serialized ...",
)
```

`27-06-SUMMARY.md:46` records the disposition of AR-27-04 verbatim as
`"accept-residual — AUTO-SELECTED by the configured run mode (mode: yolo auto-selects
gate=\"blocking\" checkpoints), NOT maintainer-chosen"`. A privacy-control bypass on a shipped 1.0.0
release posture was accepted by the harness, not by a person, and then pinned by a test.

**Fix:** two edits, both small; the second is what makes the first honest.

```kotlin
// 1. Redaction.kt — route the host rule through the same composer as the other three.
//    The composer's replacement contract already matches: the host lambda reads groupValues[1],
//    so make the value a capture on BOTH branches, or switch it to m.value.substringAfter(":").trim().
private val hostHeaderRegex = logicalLineHeaderRule("host")
// ... and in apply():
out = out.replace(hostHeaderRegex) { m ->
    val anon = anonymizeHost(m.value.substringAfter(":").trim(), stableHostSalt, recordMapping)
    "${m.value.substringBefore(":")}: $anon"
}
```

```kotlin
// 2. Serialization.kt — close the url/baseUrl/httpService.host half at the same time, or the
//    header fix produces a payload whose `request` is anonymised and whose `url` is not.
//    Both toSiteMapEntry() and toSerializableForm() need the context-aware maybeAnonymizeUrl,
//    which means threading McpToolContext into them (the two executors already hold it).
```

Then replace the two `assertTrue(... .contains("api.example.com"))` assertions in
`McpToolHelpersTest` with `assertFalse`, and update the `RedactionHostMapBoundTest` bound that
D-27-13 cites as the reason for deferral — that bound is a load argument, not a correctness one, and
it should be re-measured rather than used to keep a control switched off.

If the maintainer genuinely wants to accept this, it needs a human decision recorded as such, not a
`yolo`-mode auto-select, and the accepting test should assert on a `PrivacyMode.OFF` fixture so the
suite never contains a green "sensitive value survives STRICT".

## Warnings

### WR-01: `authHeaderRegex`'s name group is capturing and appears twice, so group 1 is null on half the matches

**File:** `src/main/kotlin/com/six2dez/burp/aiagent/redact/Redaction.kt:97-105`, `:236-240`

**Issue:** `logicalLineHeaderRule` interpolates `namePattern` into **both** alternation branches.
`authHeaderRegex` passes a **capturing** group (`"(" + "authorization|..." + ")"`), so the compiled
pattern has groups 1 and 2 for the same logical field, and exactly one of them is always null.
`apply()` happens to use `m.value.substringBefore(":")` today, so nothing breaks — but the two
cookie rules pass non-capturing fragments, so the three "identical" rules are silently
non-isomorphic. Any future `groupValues[1]` usage produces `""` on the escaped branch and silently
mis-renders the header name.

**Fix:** make the fragment non-capturing so all three composed rules have zero groups.

```kotlin
private val authHeaderRegex =
    logicalLineHeaderRule(
        "(?:" +
            "authorization|proxy-authorization|" +
            // ... unchanged
            ")",
    )
```

Optionally add an assertion to `LogicalLineBoundaryScopeTest` that every composed rule reports
`toPattern().matcher("").groupCount() == 0`, so the invariant is machine-checked rather than lucky.

---

### WR-02: The escaped branch has no "start of embedded JSON string" boundary, and neither branch handles a leading-whitespace header line

**File:** `src/main/kotlin/com/six2dez/burp/aiagent/redact/Redaction.kt:218-240`

**Issue:** the escaped branch requires `(?<=\\[rn])`. A header that is the **first** content of a
JSON string value has no preceding escaped newline and no real `^`, so it is missed. Measured:

```
IN : {"h":"Cookie: a=SECRET1\r\nX: y"}
OUT: {"h":"Cookie: a=SECRET1\r\nX: y"}     <-- not stripped
```

Reachable through any serialized string field whose content begins with a header line —
`HttpRequestResponse.notes` (analyst annotations), and any future field that carries a header
fragment rather than a whole message. Separately, the real-line branch's `^` means an obs-folded or
whitespace-indented header line is never matched:

```
IN : "GET / HTTP/1.1\r\n Cookie: a=SECRET5\r\n\r\n"   (note leading space)
OUT: unchanged                                        <-- not stripped
```

**Fix:** add the string-open boundary as a third start alternative and allow leading horizontal
whitespace on the real-line branch. Both widen only in the over-redacting direction.

```kotlin
private fun logicalLineHeaderRule(namePattern: String): Regex =
    Regex(
        "(?im)(?:^[ \\t]*" + namePattern + REAL_LINE_HEADER_VALUE +
            "|(?<=" + JSON_ESCAPED_NEWLINE + "|\")" + namePattern + JSON_ESCAPED_HEADER_VALUE + ")",
    )
```

Note the lookbehind becomes variable-width (2 vs 1); if the measured 2.4x cost matters, spell it as
two separate lookbehind alternatives rather than dropping the boundary.

---

### WR-03: The new cookie gate was added to dead code, while the reachable gap in the same function was left open

**File:** `src/main/kotlin/com/six2dez/burp/aiagent/prompts/bountyprompt/BountyPromptTagResolver.kt:118-156`

**Issue:** `BountyPromptTagResolver` has **zero instantiations** anywhere in `src/main/kotlin`
(verified: the only matches outside the file itself are comments in `Redaction.kt`). The phase spent
a control plus ~130 lines of new tests on an unreachable class. Meanwhile the same function carries a
documented, unfixed defect the comment states plainly: the tag value never passes `Redaction.apply`,
so a JWT, bearer token or secret in a URL- or BODY-typed parameter **value** reaches the prompt
verbatim in every mode — `sensitiveParamName` keys on the parameter *name* only.
`BountyPromptTagResolverTest.theExistingSensitiveNameFilterAndPassThroughAreBothUnchanged` pins that
pass-through under STRICT (`assertTrue(resolved.contains("wibble=$PASS_THROUGH_SENTINEL (URL)"))`).

This is also a factual inconsistency in `Redaction.kt:491`, which lists `BountyPromptTagResolver`
among "six callers" of `Redaction.apply` as if it were live.

**Fix:** either wire the resolver up or delete the package member. If it stays, run the assembled
block through the same choke point every sibling tag uses:

```kotlin
BountyPromptTag.HTTP_REQUESTS_PARAMETERS ->
    truncateChunk(
        Redaction.apply(buildRequestParameters(rr, policy, hostSalt), policy, stableHostSalt = hostSalt),
        maxChunkChars,
    )
```

The stated objection ("two controls for one class at one site") does not hold: `cookieTypedParamRegex`
rewriting an already-`[STRIPPED]` line is idempotent, which the file itself argues at `Redaction.kt:2011`.

---

### WR-04: The `params_extract` line-shape tests assert against a copy of the formatter, not the formatter

**File:** `src/test/kotlin/com/six2dez/burp/aiagent/mcp/tools/ParameterCarrierRedactionTest.kt:658-665`

**Issue:** `paramsExtractLines` re-types the production expression
(`"type=${param.type} name=${param.name} value=${param.value}"`). Every assertion in
`cookieSentinelDoesNotSurviveTheParamsExtractLineShapeUnder{Strict,Balanced}`,
`...UnderOff` and `nonCookieParamsExtractLinesAreUnchangedInEveryMode` therefore measures the test's
own string, not `McpToolExecutorImpl.kt:360` or `McpToolLegacy.kt:157`. If either executor's format
changes, those four tests stay green while asserting a shape that is no longer emitted. The KDoc
acknowledges the duplication but justifies it with "an extracted formatter would make this test
assert that a function equals itself" — that is the wrong trade for a security-shape test.

**Fix:** extract the one-line formatter into `McpToolHelpers` beside `sanitizeParameters`, call it
from both executors and from the test, and keep the *behavioural* assertions (sentinel absence,
`[STRIPPED]` presence) as the thing under test.

```kotlin
// McpToolHelpers.kt
internal fun formatExtractedParamLine(param: ParsedParam): String =
    "type=${param.type} name=${param.name} value=${param.value}"
```

The producer-count pin in `theProducerInventoryIsExactlyFourAndEveryOneRoutesThroughTheSanitizer`
already prevents the formatter from being fed anything but a `ParsedParam`.

---

### WR-05: `CookieCarrierInventoryTest` pins tree-wide raw counts, so unrelated edits turn a cookie-privacy test red

**File:** `src/test/kotlin/com/six2dez/burp/aiagent/redact/CookieCarrierInventoryTest.kt:340-360`,
`:365-372`, `:534-556`

**Issue:** the test asserts an exact multiset over the **entire** `src/main/kotlin` tree:
`EXPECTED_TOTAL_CARRIER_SITES = 72`, a per-file/per-accessor map for 11 files, and
`MEASURED_HEADER_VALUE_ARGUMENTS` including `"Content-Type" to 6`, `"Origin" to 1`, `"Referer" to 1`.
Adding one `.headers()` call, one `.parameters(` call or one `headerValue("Content-Type")` **anywhere**
— in a file with no cookie relevance at all — fails a test whose name says it is about cookie
carriers. That is a high false-positive tripwire on a 172-file tree; the predictable outcome is that
the next contributor bumps the number without re-reading any consumer, which is precisely the
bookkeeping the file exists to force.

Secondary: `ISSUE_DETAIL_CARRIER_DISPOSITION` (`:534-556`) is a 20-line `const val` that no assertion
reads. It is prose compiled into a class file.

**Fix:** scope the pin to the accessors that can actually carry cookie bytes — drop
`"Content-Type"`, `"Origin"` and `"Referer"` from the argument multiset (assert only that no *new*
cookie-named argument appears), and restrict the per-file count map to the files in `ROUTED_THROUGH`
+ `CLASSIFIED_NON_CARRYING` rather than a global total. Move
`ISSUE_DETAIL_CARRIER_DISPOSITION` into `26-SECURITY.md` where AR-27-08 already lives, or make an
assertion read it.

---

### WR-06: `sanitizeHeaders` collapses repeated header names, silently dropping data from `request_parse` / `response_parse`

**File:** `src/main/kotlin/com/six2dez/burp/aiagent/mcp/tools/McpToolHelpers.kt:317`, `:345`

**Issue:** the accumulator is `LinkedHashMap<String, String>` keyed on the original-cased name, so
`n` byte-identically named headers become one entry (last value wins, first position kept).
`Set-Cookie`, `Via`, `Warning`, `Link` and `X-Forwarded-For` all legitimately repeat.
`McpToolHelpersTest.identicallyNamedHeadersCollapseToOneEntry` pins this as accepted. It is not a
cookie leak (the surviving entry is `[STRIPPED]`), but it means the MCP tools hand an AI agent a
provably incomplete view of the traffic it is asked to reason about — in a security tool, that is a
correctness defect, not a formatting quirk.

**Fix:** change the schema field to preserve multiplicity.

```kotlin
// mcp/schema — ParsedRequest / ParsedResponse
val headers: List<ParsedHeader>          // was Map<String, String>
@Serializable data class ParsedHeader(val name: String, val value: String)
```

and have `sanitizeHeaders` return `List<ParsedHeader>`. If the map shape must stay for wire
compatibility, at minimum emit `"n1, n2"` joined values (RFC 9110 field-line combination) instead of
discarding.

---

### WR-07: `isCookieParameterType` trims input that a closed enum name can never carry, and diverges from its sibling predicate

**File:** `src/main/kotlin/com/six2dez/burp/aiagent/redact/Redaction.kt:335`

**Issue:** `typeName.trim().uppercase(Locale.ROOT)` — every caller passes
`HttpParameterType.name`, and a Java enum constant name cannot contain whitespace (verified:
`javap` on `montoya-api-2026.2` shows seven plain constants, no `toString` override). The `.trim()`
is unreachable defensive code. It also makes the two predicates inconsistent: `isCookieHeaderName`
does *not* trim, so a caller reading both is given two different contracts for "normalise the input".

**Fix:** drop the `.trim()` and say why in one line, or add it to `isCookieHeaderName` too. Prefer
dropping it — the KDoc already argues (correctly) that the type is a closed enum and therefore needs
exact comparison, and unreachable normalisation undercuts that argument.

```kotlin
fun isCookieParameterType(typeName: String): Boolean =
    typeName.uppercase(Locale.ROOT) == COOKIE_PARAMETER_TYPE_NAME
```

---

### WR-08: Two test classes mutate the JVM default locale; the `@ResourceLock` that guards them is currently inert

**File:** `src/test/kotlin/com/six2dez/burp/aiagent/mcp/tools/McpToolHelpersTest.kt:398-440`,
`src/test/kotlin/com/six2dez/burp/aiagent/scanner/PassiveAiScannerHeaderAdmissionTest.kt:49-53`, `:86-108`

**Issue:** both classes call `Locale.setDefault(Locale.forLanguageTag("tr-TR"))` and annotate the
mutating method with `@ResourceLock(Resources.LOCALE)`. There is **no** `junit-platform.properties`
and no `systemProperty("junit.jupiter.execution.parallel.enabled", ...)` in `build.gradle.kts`, so
JUnit runs sequentially and the lock is a no-op. The day parallel execution is switched on,
`PassiveAiScannerHeaderAdmissionTest.tearDown` — which asserts
`assertEquals(localeAtStart, Locale.getDefault())` on **every** test in that class and carries **no**
resource lock — will fail non-deterministically whenever `McpToolHelpersTest`'s Turkish test is in
flight.

Also `assertFalse(("COOKIE" as java.lang.String).toLowerCase().contains("cookie"))` asserts a
JDK/locale-provider behaviour rather than this project's behaviour; it is an environment assertion
in a privacy suite.

**Fix:** annotate the *class* with `@ResourceLock(Resources.LOCALE)` on both test classes (which
covers `@BeforeEach`/`@AfterEach`), and either enable parallel execution properly or drop the
annotations so no one is misled about what is guarded. Downgrade the `java.lang.String` probe to an
`assumeTrue` so a JDK change reports "not applicable" instead of "privacy control broken".

---

### WR-09: Vendor auth headers outside the 16-name alternation still leak under STRICT

**File:** `src/main/kotlin/com/six2dez/burp/aiagent/redact/Redaction.kt:92-105`

**Issue:** the new comment states this honestly ("matched by NO rule here at all"), and it is
recorded in `CONCERNS.md` — but it is a live STRICT disclosure and this phase reopened the file that
owns it. Measured:

```
IN : "GET / HTTP/1.1\nX-Shopify-Access-Token: SECRET7\n"
OUT: unchanged
```

`jsonSecretKeyRegex` cannot reach it (header value, not a quoted JSON key), `bearerRegex` /
`basicAuthRegex` / `jwtRegex` do not match a plain token, and `urlTokenParamRegex` /
`formBodyParamRegex` need an `=`. Note the same value **is** caught when it appears as a JSON key in
the header-map shape (`"X-Auth-Token": "SECRET3"` → `"[REDACTED]"`), so coverage is
shape-dependent in a way a reader would not predict.

**Fix:** replace the exact-name alternation with the token-boundary vocabulary already built for
keys, applied to the header-name position only:

```kotlin
// One rule instead of a 16-name list; SENSITIVE_KEY_EXPR already encodes the whole-token
// boundary and the CREDENTIAL_PREFIXES narrowing that WR-01 measured.
private val authHeaderRegex =
    logicalLineHeaderRule("(?:[A-Za-z0-9_-]*(?:authorization|" + SENSITIVE_KEY_EXPR + ")[A-Za-z0-9_-]*)")
```

This needs its own measurement pass against the WR-01 false-positive corpus before it ships; file it
as a successor rather than an in-place edit if that budget is not available now.

---

_Reviewed: 2026-08-26_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
