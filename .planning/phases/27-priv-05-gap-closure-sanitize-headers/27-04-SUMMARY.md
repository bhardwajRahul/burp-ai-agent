---
phase: 27-priv-05-gap-closure-sanitize-headers
plan: 04
subsystem: privacy
tags: [kotlin, redaction, cookies, mcp, json-serialization, regex, junit5]

requires:
  - phase: 21-priv-05-cookie-rules
    provides: "the line-anchored cookie header rules and COOKIE_NAME_PART this plan recomposes without widening"
  - phase: 27-priv-05-gap-closure-sanitize-headers
    plan: "01"
    provides: "Redaction.isCookieHeaderName and COOKIE_NAME_TOKEN — the shared cookie-name predicate this plan consumes unchanged"
provides:
  - "Redaction.logicalLineHeaderRule(namePattern) — one private composer producing the two-branch header rule, reused by plan 27-05 for authHeaderRegex"
  - "Redaction.JSON_ESCAPED_NEWLINE / REAL_LINE_HEADER_VALUE / JSON_ESCAPED_HEADER_VALUE — the three named fragments the boundary rationale is written against"
  - "cookieHeaderRegex and setCookieHeaderRegex fire on the serialized MCP emission shape (raw HTTP message inside a JSON string) in STRICT and BALANCED"
  - "SerializedEmissionRedactionTest — 17-test red-probe and hazard family over the real serialized shape, all three carriers"
  - "Measured correction: redactIfNeeded recovers a missed cookie on the raw-message-in-JSON shape but NOT on the ParsedRequest header-map shape, which carries no line boundary at all"
affects: [27-05, 27-06, privacy, mcp, security-register]

actuals:
  tokens: 12593
  tasks: 3
  commits: 3

tech-stack:
  added: []
  patterns:
    - "Two-branch alternation where the value terminator is coupled to the start boundary, so the byte-identity claim is structural before it is tested"
    - "Escape-pair tokenization instead of a negative lookbehind, because a fixed-width look-back cannot express a parity predicate"
    - "Shipped-output capture against the pre-change build as the expectation for a byte-identity test, so the expectation cannot be typed to match the new behaviour"
    - "Gating the ROOT CAUSE (absence of a line boundary) instead of its leak consequence, to record a residual without leaving a green test that asserts a value survives"

key-files:
  created:
    - src/test/kotlin/com/six2dez/burp/aiagent/mcp/tools/SerializedEmissionRedactionTest.kt
  modified:
    - src/main/kotlin/com/six2dez/burp/aiagent/redact/Redaction.kt
    - src/test/kotlin/com/six2dez/burp/aiagent/mcp/tools/McpToolHelpersTest.kt

key-decisions:
  - "D-27-06/07/08/15 executed as specified. The composed cookie pattern matches the plan's reference string character for character, and all seven fixture rows of the plan's terminator-shape table reproduced exactly on this checkout (JDK 21 temurin-21)."
  - "FALSIFIED PLAN PREMISE (task 3): the plan expected the McpToolHelpersTest pin to turn RED after task 1. It did not — measured green. The pin's fixture is the ParsedRequest HEADER-MAP shape, whose headers are JSON object members rather than lines, so it carries no line boundary of any kind and neither branch can fire. Inverting in place would have committed a RED, false test."
  - "Task 3 resolved by gating both shapes for what each actually does: the cookie assertion is inverted on the raw-message-in-JSON shape (where the inversion is true), and the header-map shape's root cause — no escaped newline in the payload — is gated directly instead of its cookie consequence. No green test asserts a cookie value survives a redacting policy."
  - "FALSIFIED ACCEPTANCE CRITERION (task 1, criterion 3): the removed-line grep returns 3, not 0, and cannot return 0 for any implementation the same plan mandates. It counts CONSUMER lines of COOKIE_NAME_PART/COOKIE_NAME_TOKEN, and rebuilding the two regexes necessarily rewrites those lines. The definitions themselves are byte-identical and at identical line numbers."
  - "D-27-16 measured directly rather than inherited: Redaction.apply on a 4,194,327-char single-line serialized payload under STRICT, best of three, 557 ms before and 650 ms after (+93 ms, 1.17x) — far under the 20x stop-and-report threshold."

requirements-completed: [PRIV-05]

coverage:
  - id: D1
    description: "A canonical Cookie value does not survive STRICT or BALANCED redaction of the serialized proxy_http_history shape"
    requirement: PRIV-05
    verification:
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/mcp/tools/SerializedEmissionRedactionTest.kt#canonicalCookieDoesNotSurviveTheSerializedProxyHistoryShapeUnderStrict"
        status: pass
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/mcp/tools/SerializedEmissionRedactionTest.kt#canonicalCookieDoesNotSurviveTheSerializedShapeUnderBalanced"
        status: pass
    human_judgment: false
  - id: D2
    description: "Set-Cookie and the five measured name variants are stripped on the serialized shape in both redacting modes, each with a distinct sentinel"
    requirement: PRIV-05
    verification:
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/mcp/tools/SerializedEmissionRedactionTest.kt#canonicalSetCookieDoesNotSurviveTheSerializedShapeInBothRedactingModes"
        status: pass
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/mcp/tools/SerializedEmissionRedactionTest.kt#everyCookieNameVariantCarriesADistinctSentinelAndNoneSurvives"
        status: pass
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/mcp/tools/SerializedEmissionRedactionTest.kt#everySentinelInThisFileIsDistinct"
        status: pass
    human_judgment: false
  - id: D3
    description: "All three serialized carriers — HttpRequestResponse, SiteMapEntry, IssueDetails.requestResponses — strip cookies in both redacting modes"
    requirement: PRIV-05
    verification:
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/mcp/tools/SerializedEmissionRedactionTest.kt#siteMapEntryCarrierStripsCookiesInBothRedactingModes"
        status: pass
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/mcp/tools/SerializedEmissionRedactionTest.kt#issueDetailsCarrierStripsCookiesInBothRedactingModes"
        status: pass
    human_judgment: false
  - id: D4
    description: "The redacted tool result still parses as JSON with an unchanged key set and field count"
    requirement: PRIV-05
    verification:
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/mcp/tools/SerializedEmissionRedactionTest.kt#redactedSerializedOutputStillParsesAsJsonWithTheSameKeySet"
        status: pass
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/mcp/tools/SerializedEmissionRedactionTest.kt#aCookieValueEndingInOneBackslashAtTheEndOfThePayloadStillParsesAsJson"
        status: pass
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/mcp/tools/SerializedEmissionRedactionTest.kt#aCookieValueEndingInTwoBackslashesAtTheEndOfThePayloadStillParsesAsJson"
        status: pass
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/mcp/tools/SerializedEmissionRedactionTest.kt#anEmptyCookieValueOnTheSerializedShapeStillParsesAsJson"
        status: pass
    human_judgment: false
  - id: D5
    description: "Multi-line behaviour is byte-identical to what shipped, including the two shapes RedactionTest does not cover"
    requirement: PRIV-05
    verification:
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/mcp/tools/SerializedEmissionRedactionTest.kt#aRealMultiLineCookieValueContainingAQuoteIsStillStrippedWhole"
        status: pass
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/mcp/tools/SerializedEmissionRedactionTest.kt#aRealMultiLineCookieValueEndingInABackslashIsStillStrippedWhole"
        status: pass
      - kind: command
        ref: "git diff --stat HEAD -- src/test/kotlin/com/six2dez/burp/aiagent/redact/RedactionTest.kt (empty) + RedactionTest 46/46 green"
        status: pass
    human_judgment: false
  - id: D6
    description: "PrivacyMode.OFF leaves the serialized payload byte-identical; header names survive, only values are replaced"
    requirement: PRIV-05
    verification:
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/mcp/tools/SerializedEmissionRedactionTest.kt#offModeLeavesTheSerializedShapeByteIdentical"
        status: pass
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/mcp/tools/SerializedEmissionRedactionTest.kt#headerNameAndBenignControlSurviveTheSerializedShape"
        status: pass
    human_judgment: false
  - id: D7
    description: "The remaining named hazards — the real truncateIfNeeded shape and an escaped quote inside a value — are each gated"
    requirement: PRIV-05
    verification:
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/mcp/tools/SerializedEmissionRedactionTest.kt#theRealTruncateIfNeededOutputShapeIsStrippedAndNotLengthened"
        status: pass
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/mcp/tools/SerializedEmissionRedactionTest.kt#anEscapedQuoteInsideACookieValueDoesNotEndTheRedactionEarly"
        status: pass
    human_judgment: false
  - id: D8
    description: "No green test in the repository asserts that a cookie value survives a redacting privacy mode"
    requirement: PRIV-05
    verification:
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/mcp/tools/McpToolHelpersTest.kt#cookieVariantsAreStrippedEndToEndThroughRedactIfNeeded"
        status: pass
      - kind: command
        ref: "awk '/assertTrue\\(/,/\\)/' McpToolHelpersTest.kt | grep sentinelxrayninezulu -> no output"
        status: pass
    human_judgment: false
  - id: D9
    description: "The added header-stage cost is bounded and recorded as a measured number"
    requirement: PRIV-05
    verification:
      - kind: command
        ref: "Redaction.apply, 4,194,327-char single-line STRICT payload, best of three: 557 ms before / 650 ms after"
        status: pass
    human_judgment: false

duration: 33 min
completed: 2026-08-24
status: complete
---

# Phase 27 Plan 04: Serialized-Emission Cookie Leak Closure Summary

The two cookie rules now recognise a JSON-escaped newline as a logical line boundary via a shared
two-branch composer, so `Cookie:` and `Set-Cookie:` values stop reaching the AI backend through the
MCP tool results that embed a raw HTTP message in a JSON string — the one truth the phase's 7/9
verification failed.

**Duration:** 33 min (21:29–22:02 CEST, 2026-08-24) · **Tasks:** 3 · **Commits:** 3 · **Files:** 3

---

## What Was Built

### Task 1 — the tracer slice (`20569be`)

`Redaction.kt` gains three private fragments and one private composer:

| Symbol | Regex it contributes |
|---|---|
| `JSON_ESCAPED_NEWLINE` | `\\[rn]` — fixed-width two-character look-back over the escape `toolJson` emits for a real CR or LF |
| `REAL_LINE_HEADER_VALUE` | `:\s*.+$` — the shipped tail, character for character |
| `JSON_ESCAPED_HEADER_VALUE` | `:\s*(?:\\.\|[^"\\])+?(?=\\[rn]\|$\|")` — reluctant, escape pairs consumed atomically |
| `logicalLineHeaderRule(namePattern)` | `(?im)(?:^NAME<real tail>\|(?<=\\[rn])NAME<escaped tail>)` |

`cookieHeaderRegex` and `setCookieHeaderRegex` are rebuilt from that composer with their name
fragments passed through unchanged, including the negative lookahead that keeps the two mutually
exclusive. `COOKIE_NAME_PART`, `COOKIE_NAME_TOKEN` and `isCookieHeaderName` are untouched.

The composed pattern for `cookieHeaderRegex` matches the plan's reference string character for
character:

```
(?im)(?:^(?![A-Za-z0-9-]*set-cookie)[A-Za-z0-9-]*cookie[A-Za-z0-9-]*:\s*.+$|(?<=\\[rn])(?![A-Za-z0-9-]*set-cookie)[A-Za-z0-9-]*cookie[A-Za-z0-9-]*:\s*(?:\\.|[^"\\])+?(?=\\[rn]|$|"))
```

The KDoc above the two rules states which boundary each branch recognises, why the quote terminator
lives on the escaped branch only, why the escape pair replaces a negative lookbehind, the D-27-15
over-match bound, and that the composer is applied to the two cookie rules **only** in this plan —
naming `hostHeaderRegex` as still blind (AR-27-04) and `authHeaderRegex` as the rule plan 27-05
closes one wave later with this same composer.

### Task 2 — the probe family (`06df287`)

`SerializedEmissionRedactionTest` grows from 3 to **17 tests**: three carriers, both redacting modes,
an OFF negative control, seven named hazards, and a Kotlin-side sentinel-distinctness gate.

### Task 3 — the inverted pin (`09e9cae`)

The `McpToolHelpersTest` block that asserted a cookie sentinel survives STRICT no longer does. See
**Deviations** — the plan's premise for this task was measurably false and the resolution differs
from the instruction.

---

## Measurements

### RED, recorded before the `Redaction.kt` edit

`SerializedEmissionRedactionTest` on the unmodified rule: **3 tests, 1 failed**.

```
org.opentest4j.AssertionFailedError: a canonical Cookie value must not survive STRICT redaction of
the serialized proxy_http_history shape (got: {"request":"GET /basket HTTP/1.1\r\nHost:
shop.example\r\nCookie: wibble=sentinelalfa\r\nX-Request-Id: benignidcontrolvalue\r\nAccept:
text/html\r\n\r\n","response":"HTTP/1.1 200 OK\r\nContent-Type: text/html\r\n\r\n<html></html>",
"notes":null}) ==> expected: <false> but was: <true>
```

Nothing was redacted — `Host: shop.example` survives un-anonymised under STRICT on the same payload,
reproducing the verifier's observation exactly. The other two tests passed on the shipped rule, as
designed: they are the contract and non-vacuity gates, not the red probe.

### The plan's terminator-shape table, re-measured on this checkout

Run against JDK 21 (temurin-21) `java.util.regex` with the shipped name fragments, four candidate
shapes over the plan's fixtures. **Every cell reproduced.**

| Fixture | shipped `^…:\s*.+$` | single tail + `(?<!\\)"` | single tail + escape-pair | TWO-BRANCH (shipped here) |
|---|---|---|---|---|
| serialized, cookie mid-string | miss (leak) | strip, JSON OK | strip, JSON OK | strip, JSON OK |
| serialized, value ends in ONE backslash | miss (leak) | **JSON BROKEN** | strip, JSON OK | strip, JSON OK |
| serialized, value ends in TWO backslashes | miss (leak) | **JSON BROKEN** | strip, JSON OK | strip, JSON OK |
| serialized, escaped quote inside value | miss (leak) | strip, JSON OK | strip, JSON OK | strip, JSON OK |
| real multi-line, plain value | strip | strip | strip | strip (byte-identical) |
| real multi-line, value contains `"` | strip | **LEAKS TAIL** | **LEAKS TAIL** | strip (byte-identical) |
| real multi-line, value ends in backslash | strip | strip | **NO MATCH (leak)** | strip (byte-identical) |

Both rejected candidates regress REAL multi-line behaviour, which no shipped `RedactionTest` fixture
covers. That is the finding that forces the two-branch shape, and it held under independent
re-measurement rather than being taken on the plan's authority.

### Mode-by-carrier-by-name outcome — the mirror of the verifier's Behavioural Spot-Checks

| Behaviour | Carrier | STRICT | BALANCED | Status |
|---|---|---|---|---|
| `Cookie:` stripped | `HttpRequestResponse` | stripped | stripped | **PASS** |
| `Set-Cookie:` stripped | `HttpRequestResponse` | stripped | stripped | **PASS** |
| `X-Cookie` stripped | `HttpRequestResponse` | stripped | stripped | **PASS** |
| `Cookie2` stripped | `HttpRequestResponse` | stripped | stripped | **PASS** |
| `Set-Cookie2` stripped | `HttpRequestResponse` | stripped | stripped | **PASS** |
| `X-Original-Cookie` stripped | `HttpRequestResponse` | stripped | stripped | **PASS** |
| `X-Forwarded-Cookie` stripped | `HttpRequestResponse` | stripped | stripped | **PASS** |
| `Cookie:` stripped | `SiteMapEntry` | stripped | stripped | **PASS** |
| `Cookie:` stripped | `IssueDetails.requestResponses` | stripped | stripped | **PASS** |
| header NAME preserved (T-21-WA2) | all three | preserved | preserved | **PASS** |
| benign non-cookie header untouched | all three | untouched | untouched | **PASS** |
| payload byte-identical | all three, `PrivacyMode.OFF` | n/a | n/a | **PASS** |
| JSON parses, same key set + field count | all three | same | same | **PASS** |

All six rows the verifier recorded as **FAIL** are now **PASS**. The `Host:` row is unchanged and
deliberately so — see Residuals.

### Header-stage cost (D-27-16), measured directly

`newlineFreeOversizeBodyIsScannedNotDestroyed` **cannot** gate this: it drives
`Redaction.testWindowedBodyStage`, a seam that bypasses the header stage entirely. It is carried here
as a regression check only, and it passes. The number below is a direct `Redaction.apply`
measurement, taken on the same machine minutes apart with the pre-change `Redaction.kt` checked out
for the first run.

| | payload | runs (ms) | best |
|---|---|---|---|
| **before** (shipped rule) | 4,194,327-char single-line serialized shape, STRICT | 600, 569, 557 | **557 ms** |
| **after** (two-branch rule) | same | 670, 670, 650 | **650 ms** |

**+93 ms, a factor of 1.17** on total `Redaction.apply` — far below the 20x stop-and-report
threshold. Recorded as a number, not asserted as a threshold: a wall-clock bound in the committed
test set is the flake this repository already carries once (`SafeRegex`, 50 ms).

Note the scale difference from the plan's figures (17 ms → 220 ms): those isolate the **three**
header rules; this measures the **whole** `Redaction.apply` pipeline (body stage included, ~0.5 s on
this payload) with only the **two** cookie rules changed. Direction and magnitude are consistent.

### Shipped-output capture for the byte-identity assertions

Captured by checking out `20569be~1`'s `Redaction.kt`, running `Redaction.apply` on the exact M1/M2
fixtures, then restoring. Both produced:

```
GET /a HTTP/1.1\r\nCookie: [STRIPPED]\r\nAccept: text/html\r\n\r\n
```

The post-change run produced byte-identical output for both, independently of the tests' own
assertions. The expectation in the test file is that captured value, so it cannot have been typed to
match whatever the new rule happens to do.

### Baseline greps (task 1 criterion 5), re-measured rather than trusted

| Symbol | at pinned sha `9588922` | post-edit | gate |
|---|---|---|---|
| `authHeaderRegex` | **5** | **6** | 5 < 6 — PASS |
| `hostHeaderRegex` | **2** | **3** | 2 < 3 — PASS |

The plan's stated baselines (5 and 2) matched my own measurement exactly. Scoped check (a), over the
`COOKIE_NAME_TOKEN`…`setCookieHeaderRegex` block: `authHeaderRegex` = 1, `hostHeaderRegex` = 1 —
both ≥ 1, PASS. `authHeaderRegex`'s alternation was counted directly and holds **16** names, matching
the plan's wording.

### Test results, verified by name in the JUnit XML

`SerializedEmissionRedactionTest` — **17 tests, 0 skipped, 0 failures, 0 errors** (floor: 17):

| Nested class | Method |
|---|---|
| *(outer)* | `everySentinelInThisFileIsDistinct` |
| `ProxyHistoryCarrier` | `canonicalCookieDoesNotSurviveTheSerializedProxyHistoryShapeUnderStrict` |
| `ProxyHistoryCarrier` | `redactedSerializedOutputStillParsesAsJsonWithTheSameKeySet` |
| `ProxyHistoryCarrier` | `headerNameAndBenignControlSurviveTheSerializedShape` |
| `ProxyHistoryCarrier` | `canonicalCookieDoesNotSurviveTheSerializedShapeUnderBalanced` |
| `ProxyHistoryCarrier` | `canonicalSetCookieDoesNotSurviveTheSerializedShapeInBothRedactingModes` |
| `ProxyHistoryCarrier` | `everyCookieNameVariantCarriesADistinctSentinelAndNoneSurvives` |
| `ProxyHistoryCarrier` | `offModeLeavesTheSerializedShapeByteIdentical` |
| `SiteMapCarrier` | `siteMapEntryCarrierStripsCookiesInBothRedactingModes` |
| `IssueDetailsCarrier` | `issueDetailsCarrierStripsCookiesInBothRedactingModes` |
| `Hazards` | `aCookieValueEndingInOneBackslashAtTheEndOfThePayloadStillParsesAsJson` (H1a) |
| `Hazards` | `aCookieValueEndingInTwoBackslashesAtTheEndOfThePayloadStillParsesAsJson` (H1b) |
| `Hazards` | `theRealTruncateIfNeededOutputShapeIsStrippedAndNotLengthened` (H1c) |
| `Hazards` | `anEscapedQuoteInsideACookieValueDoesNotEndTheRedactionEarly` (H2) |
| `Hazards` | `anEmptyCookieValueOnTheSerializedShapeStillParsesAsJson` (H3) |
| `Hazards` | `aRealMultiLineCookieValueContainingAQuoteIsStillStrippedWhole` (M1) |
| `Hazards` | `aRealMultiLineCookieValueEndingInABackslashIsStillStrippedWhole` (M2) |

The `redact` package — all green, no edits to any of it:

| Class | tests | skipped | failures | errors |
|---|---|---|---|---|
| `RedactionTest` | 46 | 0 | 0 | 0 |
| `CookieHeaderNameParityTest` | 3 | 0 | 0 | 0 |
| `CookieHeaderRuleOwnershipTest` | 3 | 0 | 0 | 0 |
| `RedactionHostMapBoundTest` | 6 | 0 | 0 | 0 |
| `RedactionPolicyTest` | 15 | 0 | 0 | 0 |
| `SafeRegexTest` | 18 | 0 | 0 | 0 |
| `EntropyTest` | 20 | 0 | 0 | 0 |
| `SecretShapesTest` / `SecretTripwire*Test` | 4 / 35 | 0 | 0 | 0 |

`newlineFreeOversizeBodyIsScannedNotDestroyed` — **PASSED** (regression check, not a gate on this
change). `McpToolHelpersTest$SanitizeHeaders` — **17 tests**, 0 skipped, 0 failures.

`git diff --stat HEAD -- src/test/kotlin/com/six2dez/burp/aiagent/redact/RedactionTest.kt` produced
**no output** at every gate: the file is unmodified, which is what makes the byte-identity claim
evidence rather than assertion.

### Full gate, verbatim

```
JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test detekt ktlintCheck

> Task :detekt
> Task :runKtlintCheckOverTestSourceSet
> Task :ktlintTestSourceSetCheck
> Task :ktlintCheck
> Task :test
> Task :jacocoTestReport

BUILD SUCCESSFUL in 2m 51s
15 actionable tasks: 5 executed, 10 up-to-date
```

Aggregated across all 167 test classes: **1169 tests, 1 skipped, 0 failures, 0 errors.** The single
skip is `ExternalMcpClientManagerTest.connectAndListTools_returnsExpectedCount`, `@Disabled` since
phase 16 because it requires a live MCP server — pre-existing, in an untouched file, out of scope.
No `SafeRegex` wall-clock flake occurred; no re-run was needed.

---

## Deviations from Plan

### 1. [Rule 3 — blocking] Task 3's premise was measurably false; the instruction as written would have committed a false test

**Found during:** Task 3.

**Issue.** The plan states: *"After task 1 that assertion is false and the test will be RED."* Run
before any edit, `McpToolHelpersTest` was **green**. Measured through `redactIfNeeded` under STRICT:

| Shape | payload | cookie sentinel | bearer sentinel | host |
|---|---|---|---|---|
| header-map (`ParsedRequest`, the pin's fixture) | `{"headers":{"X-Cookie":"…"}}` | **SURVIVES** | redacted | survives |
| raw-message-in-JSON (`proxy_http_history` et al.) | `{"request":"GET / HTTP/1.1\r\nX-Cookie: …"}` | **STRIPPED** | redacted | survives |

The pin's fixture is the **header-map** shape: headers are JSON object members, not lines. The
payload contains no `\r`/`\n` escape at all (measured: `contains("\\r") == false`), so neither branch
can fire — the shipped `^` anchor has no line start, and the new branch has no escaped newline. This
plan did not change that shape's behaviour and never claimed to. Inverting the assertion in place
would have committed a **RED, false** test.

This is the same defect class the phase has now caught six times: a control verified where it was
written but never compared against the sibling consumer of the same weakness. The plan reasoned from
"single-line JSON" as one category when measurement shows two, distinguished by whether the payload
carries an escaped newline.

**Fix.** The block now gates both shapes for what each actually does:

- **raw-message-in-JSON** — the cookie assertion is **inverted to `assertFalse`**, which is the
  inversion the plan asked for, placed on the shape where it is true. This is the recovery this plan
  delivers.
- **header-map-in-JSON** — the **root cause** is gated directly (`assertFalse(rawJson.contains("\\r")
  || rawJson.contains("\\n"))`) instead of its cookie consequence. The structural fact stays under
  test, and no green assertion says a cookie value survives a redacting policy — which is what
  T-27-04-05 exists to prevent, since such an assertion is what a later audit misreads as intent.

The **non-vacuity guard is preserved and strengthened**: the bearer sentinel is asserted absent on
*both* shapes, and the note explaining why the host is unsuitable for that guard survives, now
labelled AR-27-04. The comment's scope was narrowed against the plan's wording: the plan would have
had it say `redactIfNeeded` is a second control "on single-line JSON"; measurement supports only
"on single-line JSON **that carries an escaped newline**". The wider sentence would have been a claim
wider than its gate.

**Method name kept** — `cookieVariantsAreStrippedEndToEndThroughRedactIfNeeded` still describes what
the test asserts, more so than before, since a second shape now strips too. Per the plan's
instruction, the name is changed only if it asserts something untrue; it does not.

**Files modified:** `src/test/kotlin/com/six2dez/burp/aiagent/mcp/tools/McpToolHelpersTest.kt`
**Verification:** `McpToolHelpersTest` green, `SanitizeHeaders` 17/17; cookie sentinel in zero
`assertTrue(` calls; repo-wide grep for a green cookie-survival assertion returns nothing.
**Commit:** `09e9cae`

### 2. [Recorded, not fixed] Task 1 acceptance criterion 3 is unsatisfiable by the implementation the same plan mandates

**Found during:** Task 1 gate execution.

The criterion requires this to return `0`:

```
git diff HEAD -- .../Redaction.kt | grep '^-[^-]' | sed 's/^-//' | grep -v '^\s*//' | grep -v '^\s*\*' \
  | grep -c 'isCookieHeaderName\|COOKIE_NAME_TOKEN\|COOKIE_NAME_PART'
```

**Observed: `3`.** It cannot return `0` for any implementation this plan permits. The four removed
lines are exactly and only the two regex property initializers:

```
-    private val cookieHeaderRegex =
-            "(?im)^(?!" + COOKIE_NAME_PART + "set-" + COOKIE_NAME_TOKEN + ")" +
-                COOKIE_NAME_PART + COOKIE_NAME_TOKEN + COOKIE_NAME_PART + ":\\s*.+$",
-        Regex("(?im)^" + COOKIE_NAME_PART + "set-" + COOKIE_NAME_TOKEN + COOKIE_NAME_PART + ":\\s*.+$")
```

Three of them mention the fragments — because they **consume** them, and the plan's own `<action>`
requires rebuilding both regexes via `logicalLineHeaderRule`. The criterion counts consumer lines
while its stated intent ("No removed CODE line touches the 27-01 predicate or its fragments") is about
**definitions**. As instructed, no comment-line matches were filtered out — the filtered set is
empty, so nothing is hidden by the comment strip.

The invariant the criterion means was measured and **holds**:

| Symbol | baseline `9588922` | working tree | verdict |
|---|---|---|---|
| `COOKIE_NAME_PART` | `[A-Za-z0-9-]*` @ line 85 | `[A-Za-z0-9-]*` @ line 85 | IDENTICAL |
| `COOKIE_NAME_TOKEN` | `"cookie"` @ line 91 | `"cookie"` @ line 91 | IDENTICAL |
| `isCookieHeaderName` body | — | — | IDENTICAL (`diff` empty) |

Confirmed behaviourally: `CookieHeaderNameParityTest` (3/3) and `CookieHeaderRuleOwnershipTest` (3/3)
are green **unedited**. No fix applied — the criterion is wrong, the code is right, and recording it
is the correct outcome.

### 3. [Rule 3 — blocking] ktlint formatting on the expanded test file

`ktlintCheck` reported three `standard:class-signature` / `standard:function-signature` violations in
`SerializedEmissionRedactionTest.kt`. Resolved with `./gradlew ktlintFormat`; the suite was re-run
after formatting (17/17 still green) rather than assumed unaffected. **Commit:** `06df287`

---

**Total deviations:** 1 behavioural (task 3 re-scoped on measurement), 1 falsified acceptance
criterion recorded rather than worked around, 1 mechanical formatting fix.
**Impact:** the plan's objective is fully met. One plan premise and one acceptance criterion were
falsified by measurement and are recorded as falsified — neither was manufactured into a pass.

---

## Verification Against `must_haves`

| Truth | Verdict | Evidence |
|---|---|---|
| Cookie sentinel does not survive STRICT/BALANCED on the serialized shape, asserted on the SERIALIZED shape | MET | `canonicalCookieDoesNotSurvive…UnderStrict` / `…UnderBalanced` |
| Same for `Set-Cookie` and the five variants, both modes, all three carriers | MET | 4 tests, distinct sentinel per name |
| PRIV-05's "by any path" true ON THE SERIALIZED EMISSION PATH, claim not restated wider | MET | KDoc and test-file headers bound the claim; `whole codebase` appears 0 times |
| Tool contract preserved BY MEASUREMENT (JSON parses, same key set, same field count) | MET | `assertSameJsonShape` on every serialized carrier |
| Multi-line behaviour byte-identical BY CONSTRUCTION, `RedactionTest` zero-line diff | MET | shipped tail named and reused verbatim; `RedactionTest` unmodified, 46/46 |
| Byte-identity ASSERTED on the two shapes a single-tail rewrite breaks | MET | M1/M2 assert character equality against captured shipped output |
| `PrivacyMode.OFF` byte-identical; header NAMES survive | MET | `offModeLeavesTheSerializedShapeByteIdentical` + name assertions |
| Four named hazards each gated by their own test with a parse assertion | MET | H1a, H1b, H1c, H2 (+H3, M1, M2) — seven in total |
| Header-stage cost MEASURED against the shipped rule and recorded as a number | MET | 557 ms → 650 ms, 4,194,327 chars, best of three |
| `McpToolHelpersTest` pin inverted; no green test asserts a cookie survives; non-vacuity guard preserved | MET, **re-scoped** | See Deviation 1 — inverted on the shape where the inversion is true; root cause gated on the other |
| ONE rule in the owner, composed from shared fragments; 27-01 mechanism untouched | MET | one composer, three fragments; parity/ownership tests green unedited |

---

## Residuals — recorded, not silently carried

These are deliberate scope boundaries, each already owned by a later plan. None has a test pinning it
green.

1. **`hostHeaderRegex` on the serialized shape (AR-27-04, plan 27-06).** `Host:` still reaches the
   backend un-anonymised under STRICT on every raw-message tool result. Measured twice in this plan
   and named in the `Redaction.kt` KDoc and in the `McpToolHelpersTest` comment. Out of scope per
   D-27-11: it rewrites through `anonymizeHost`, which records a de-anonymisation mapping that
   `RedactionHostMapBoundTest` exists to bound — its own decision with its own evidence.
2. **`authHeaderRegex` on the serialized shape.** Closed one wave later by plan 27-05 (D-27-12) using
   this plan's `logicalLineHeaderRule` composer. Deliberately **not** described anywhere in source as
   "still carrying the blind spot", since that sentence goes false in wave 5.
3. **Vendor auth headers outside `authHeaderRegex`'s 16-name alternation.** No rule names them at
   all; unchanged by this plan and by 27-05.
4. **The `ParsedRequest` header-map shape has no line boundary of any kind**, so `redactIfNeeded`
   cannot recover a missed cookie there — `sanitizeHeaders` remains the only control for the
   cookie-header class on `request_parse` / `response_parse`. Newly measured by this plan (see
   Deviation 1); the control itself is gated end-to-end by the first half of
   `cookieVariantsAreStrippedEndToEndThroughRedactIfNeeded`. **Recommended for plan 27-06 to record
   as a finding** — it narrows what AR-27-01 "closure" means.
5. **No independent backstop behind the cookie rules** (D-27-10, accepted): `cookie` stays out of
   `SENSITIVE_WORDS`, so the two cookie rules are a single point of control on this path.
6. **D-27-15 over-match, accepted and stated in the KDoc:** a literal backslash-`r`/`n` inside a raw
   value (a Windows path, a regex in a body) is indistinguishable from an encoded newline, so a
   `cookie:` run immediately after one is over-redacted. Fail-safe in direction.
7. **Pre-existing, untouched:** the comment above `RedactionTest.cookieHeaderNameVariantsAreStripped`
   says `authHeaderRegex`'s list has "14 names"; it holds **16**. Out of scope (`RedactionTest` must
   stay at a zero-line diff) and logged here rather than fixed.

## Known Stubs

None. No hardcoded empty value, placeholder string, `TODO` or `FIXME` was introduced; every new test
asserts against a real `Redaction.apply` / `redactIfNeeded` call, and no test was skipped or disabled.

## Threat Flags

None. No new network endpoint, auth path, file access pattern or schema change was introduced. The
change is confined to two regex definitions and their tests; the threat surface is narrowed, not
widened.

---

## Next

Ready for **27-05** — it inherits `logicalLineHeaderRule` and `JSON_ESCAPED_HEADER_VALUE` unchanged
to close `authHeaderRegex` on the same shape. Plan **27-06** should record residual 4 above alongside
AR-27-04.

## Self-Check: PASSED

- `key-files.created` / `key-files.modified` all present on disk (`SerializedEmissionRedactionTest.kt`,
  `Redaction.kt`, `McpToolHelpersTest.kt`, this SUMMARY).
- All three commits resolve in `git log --oneline --all`: `20569be`, `06df287`, `09e9cae`.
- Both throwaway capture harnesses (`ZzShippedBaselineCaptureTest.kt`, `ZzPinShapeProbeTest.kt`) were
  deleted before their task commits; neither is present in the tree or in any commit.
- Every task `<acceptance_criteria>` re-run at close-out. All pass except task 1 criterion 3, which is
  unsatisfiable as written and is recorded as falsified in Deviation 2 rather than worked around.
- Plan-level `<verification>` re-run: full gate green; `RedactionTest` diff empty; removed lines in
  `Redaction.kt` touch no definition of `isCookieHeaderName` / `COOKIE_NAME_TOKEN` / `COOKIE_NAME_PART`,
  and neither `authHeaderRegex` nor `hostHeaderRegex` is recomposed.
- `.planning/STATE.md` and `.planning/ROADMAP.md` are unmodified — the orchestrator owns those writes.
