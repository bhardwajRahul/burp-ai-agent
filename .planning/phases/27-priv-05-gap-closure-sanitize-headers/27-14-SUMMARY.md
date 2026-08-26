---
phase: 27-priv-05-gap-closure-sanitize-headers
plan: 14
subsystem: privacy
tags: [redaction, regex, lookbehind, mcp, json, kotlin, over-match]

requires:
  - phase: 27-priv-05-gap-closure-sanitize-headers
    provides: "the 27-11 third logical-line start whose bare-quote spelling this plan narrows, and the JsonStringOpenBoundary nest this plan repairs"
provides:
  - "JSON_STRING_OPEN narrowed from a bare double quote to the two-character colon-quote sequence — a JSON string VALUE open"
  - "the cost of the bare quote and the cost of the narrowing written into Redaction.kt where a reader meets the rule, the latter cited as AR-27-11"
  - "a non-JSON byte-identity negative gate and a blast-radius gate with content AFTER the value in the same JSON string"
  - "a source-read anti-drift pin on the constant's VALUE and its fixed width"
affects: [27-15, 27-16, phase-28]

actuals:
  tokens: 61600
  tasks: 3
  commits: 3

tech-stack:
  added: []
  patterns:
    - "source-read value pin: a test decodes a constant's literal out of Redaction.kt at test time rather than re-typing it, so a silent revert goes RED"
    - "whole-payload assertEquals byte identity as the preferred over-match gate — a stronger claim than containment, naming no sensitive value"

key-files:
  created: []
  modified:
    - src/main/kotlin/com/six2dez/burp/aiagent/redact/Redaction.kt
    - src/test/kotlin/com/six2dez/burp/aiagent/mcp/tools/SerializedEmissionRedactionTest.kt
    - src/test/kotlin/com/six2dez/burp/aiagent/redact/LogicalLineBoundaryScopeTest.kt

key-decisions:
  - "Narrow JSON_STRING_OPEN to `:\"` rather than keep the bare quote and accept a 93% blast radius. Independently measured by 27-REVIEW-2 CR-03 and 27-VERIFICATION-4 gap 1; both showed it removes every measured false positive AND keeps both `notes` carriers closed."
  - "Accept the array-element residual (`[\"Cookie: …\"]` is no longer a recognised start) at LOW, file it as AR-27-11, and pin it by NO test — a green assertion that a cookie value survives STRICT is the artifact class 26-SECURITY.md clause (vi) prohibits."
  - "Keep the degenerate last-content over-match gate and label it, rather than delete it. It still asserts a true thing about the cross-field bound; what it cannot do is measure a blast radius, and its KDoc now says so."
  - "Probe A shape 5 as written in the plan cannot be byte-identical: its fixture carries the literal `Bearer `, which the unrelated shipped `bearerRegex` claims. A narrowed shape 5b was added rather than the assertion weakened."

patterns-established:
  - "Cost-of-the-start paragraphs: every logical-line start now carries its own two-direction cost paragraph in the rationale block — what the previous spelling cost, and what the narrowing costs — at the rigour D-27-15's paragraph set."

requirements-completed: []

coverage:
  - id: D1
    description: "JSON_STRING_OPEN is a JSON string VALUE open (colon-quote), closing all five measured non-JSON false positives and the 1589-of-1714-character destruction on the serialized MCP emission path"
    requirement: "PRIV-05"
    verification:
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/mcp/tools/SerializedEmissionRedactionTest.kt#JsonStringOpenBoundary.anHtmlAttributePayloadIsLeftByteIdenticalUnderBothRedactingModes"
        status: pass
      - kind: other
        ref: "ad-hoc JDK 21 probe against build/classes/kotlin/main — PROBE A shapes 1-4 and 5b, PROBE B (1714-char payload), both columns recorded below"
        status: pass
    human_judgment: false
  - id: D2
    description: "Round 4's own target stays closed: both `notes` carriers and the escaped-newline control still produce `Cookie: [STRIPPED]` in STRICT and BALANCED"
    requirement: "PRIV-05"
    verification:
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/mcp/tools/SerializedEmissionRedactionTest.kt#JsonStringOpenBoundary.contentAfterTheCookieValueInTheSameJsonStringIsMeasuredNotAssumed"
        status: pass
      - kind: other
        ref: "ad-hoc JDK 21 probe — PROBE C, all three cases, both modes, both columns recorded below"
        status: pass
    human_judgment: false
  - id: D3
    description: "The narrowed value cannot be silently re-widened — it is read out of Redaction.kt at test time and pinned at a fixed width of two"
    requirement: "PRIV-05"
    verification:
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/redact/LogicalLineBoundaryScopeTest.kt#theJsonStringOpenIsAValueOpenAndNotABareQuote"
        status: pass
    human_judgment: false
  - id: D4
    description: "The array-element residual AR-27-11 is measured, recorded in source and in this SUMMARY, and pinned by no test"
    verification: []
    human_judgment: true
    rationale: "A deliberately unpinned residual cannot be proven by a test — that is the point. A human must confirm plan 27-16 files AR-27-11 in 26-SECURITY.md and that no test asserting the value survives has appeared."

duration: 40min
completed: 2026-08-26
status: complete
---

# Phase 27 Plan 14: Narrow the third logical-line start to a JSON string VALUE open — Summary

**`JSON_STRING_OPEN` goes from a bare double quote to the two-character colon-quote sequence, closing a shipped correctness regression that destroyed 1589 of 1714 characters of a realistic serialized MCP tool result while leaving the JSON structurally valid — with both costs written where a reader meets the rule and the value source-pinned against a silent revert.**

## Performance

- **Duration:** 40 min
- **Started:** 2026-08-26T12:55:00Z
- **Completed:** 2026-08-26T13:35:48Z
- **Tasks:** 3
- **Files modified:** 3

## Accomplishments

- `Redaction.JSON_STRING_OPEN` narrowed to the colon-quote sequence. Still fixed-width (two regex-literal characters), so the composer's measured 2.4x look-back cost model and the `SafeRegex` 50ms deadline hold verbatim; no quantifier, alternation or value tail changed. All three composed rules — `cookieHeaderRegex`, `setCookieHeaderRegex`, `authHeaderRegex` — are fixed by the one edit.
- Both costs stated in source. The constant's KDoc and a new two-part paragraph in the 27-11 rationale block name what the bare quote also matched, quote the 1589-of-1714 measurement, state the mechanism in one clause, and record the narrowing's own residual as `AR-27-11`.
- The gate that missed it is repaired, not merely supplemented. The `JsonStringOpenBoundary` nest gains a whole-payload `assertEquals` byte-identity negative gate on a non-JSON HTML-attribute carrier (with its own non-vacuity control) and a blast-radius gate carrying content AFTER the value inside the same JSON string. The degenerate last-content gate is kept and its KDoc now labels it as degenerate.
- The narrowed value is read out of `Redaction.kt` at test time and pinned at width two, with a failure message carrying the measurement, the direction of the regression and `AR-27-11`.
- Full suite green: 1241 tests, 0 failures, first pass, no `RedactionTest` flake.

## Task Commits

1. **Task 1: Narrow the third logical-line start and state the cost of the start that was added** — `2935165` (fix)
2. **Task 2: Repair the gate that missed it** — `796891d` (test)
3. **Task 3: Pin the narrowed value from source** — `bd31f42` (test)

## Files Created/Modified

- `src/main/kotlin/com/six2dez/burp/aiagent/redact/Redaction.kt` — `JSON_STRING_OPEN` narrowed to `":\""`; KDoc rewritten so the name and the value agree and the bare quote's measured cost is stated; the 27-11 rationale block's start-3 line corrected and given its own two-direction cost paragraph citing `AR-27-11`; the "width one on the string open" sentence corrected to width two.
- `src/test/kotlin/com/six2dez/burp/aiagent/mcp/tools/SerializedEmissionRedactionTest.kt` — three new `Sentinel` entries in a 27-14 group; `htmlAttributeFixture()`; `anHtmlAttributePayloadIsLeftByteIdenticalUnderBothRedactingModes`; `contentAfterTheCookieValueInTheSameJsonStringIsMeasuredNotAssumed`; a paragraph appended to the degenerate gate's KDoc.
- `src/test/kotlin/com/six2dez/burp/aiagent/redact/LogicalLineBoundaryScopeTest.kt` — `theJsonStringOpenIsAValueOpenAndNotABareQuote`; `JSON_STRING_OPEN_DECLARATION` and `EXPECTED_JSON_STRING_OPEN_WIDTH = 2`; a `decodeKotlinStringLiteral` helper that fails on any escape it does not cover.

## Measurements

All probes were driven against the FRESHLY COMPILED classes at `build/classes/kotlin/main` under JDK 21 (`/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home`), via
`Redaction.INSTANCE.apply(blob, RedactionPolicy.Companion.fromMode(mode), "round5-probe-salt", false)`.
The BEFORE column was taken against the pre-edit constant; the AFTER column against the narrowed one. Every probe was red before the edit except where noted.

### RED PROBE A — the five non-JSON false positives

STRICT and BALANCED produced IDENTICAL results for every shape, in both columns.

| # | Input | BEFORE (bare quote) | AFTER (`:"`) |
|---|---|---|---|
| 1 | `<div title="Cookie: we use cookies for analytics. Accept?">text</div>` | `<div title="Cookie: [STRIPPED]">text</div>` | byte-identical to input |
| 2 | `foo("cookie: analytics", KEEPME);` | `foo("cookie: [STRIPPED]", KEEPME);` | byte-identical to input |
| 3 | `1,"x-cookie: none",KEEPTAIL` | `1,"x-cookie: [STRIPPED]",KEEPTAIL` | byte-identical to input |
| 4 | `The header "Set-Cookie: foo" is described here. KEEPTAIL` | `The header "Set-Cookie: [STRIPPED]" is described here. KEEPTAIL` | byte-identical to input |
| 5 | `See the docs: "authorization: Bearer required" and KEEPTAIL` | `See the docs: "authorization: [REDACTED]" and KEEPTAIL` | `See the docs: "authorization: Bearer [REDACTED]" and KEEPTAIL` |
| 5b | `See the docs: "authorization: header required" and KEEPTAIL` | `See the docs: "authorization: [REDACTED]" and KEEPTAIL` | byte-identical to input |

Shape 5 does NOT reach byte identity, and the plan's expectation that it would was wrong. Its residual change is `Bearer required` → `Bearer [REDACTED]`, produced by `bearerRegex` — an independent, un-anchored, shipped rule with nothing to do with the composer. The composer's over-match on shape 5 IS closed: the header name and the rest of the line survive, where before the whole tail to the closing quote was replaced. Shape 5b is the same prose with the `Bearer ` token removed, and it is the clean proof that `authHeaderRegex` inherits the same start and is fixed by the same edit: it over-matched before and is byte-identical after. See "Deviations from Plan" below.

Shape 5 also demonstrates why a colon-quote start does not fire on this family at all: it carries a colon followed by a SPACE before the quote.

### RED PROBE B — the primary shipped shape

A 1714-character `proxy_http_history`-shaped payload: a serialized object carrying `url`, a `response` field holding an HTML body whose first quote-preceded `cookie:` sits inside a `title=` attribute, forty occurrences of the content marker `IMPORTANTCONTENT` after that point, and a sibling `notes` field last.

| | BEFORE (bare quote) | AFTER (`:"`) |
|---|---|---|
| IN length | 1714 | 1714 |
| OUT length | **125** | **1714** |
| characters destroyed | **1589** | **0** |
| content markers IN | 40 | 40 |
| content markers OUT | **0** | **40** |
| byte-identical | false | **true** |
| output still parses as JSON | yes | yes |

Both modes identical. The BEFORE output, verbatim:

```
{"url":"/orders/aaaaaaaaaaaaaaaaaaaaaaaaaaa","response":"<html><body><div title=\"cookie: [STRIPPED]","notes":"analyst note"}
```

The sibling `notes` field is byte-identical in that output and the key set is unchanged — which is exactly why every existing shape assertion in the suite passed while 93% of the payload was gone.

### GREEN-BOTH-WAYS PROBE C — round 4's own target, NOT un-fixed

STRICT and BALANCED, both columns, all three cases. Nothing changed between the two columns.

| # | Input | BEFORE | AFTER |
|---|---|---|---|
| 1 | `{"notes":"Cookie: a=SECRET1\r\nX: y"}` | `{"notes":"Cookie: [STRIPPED]\r\nX: y"}` | `{"notes":"Cookie: [STRIPPED]\r\nX: y"}` |
| 2 | `[{"notes":"Cookie: a=SECRET1\r\nX: y"}]` | `[{"notes":"Cookie: [STRIPPED]\r\nX: y"}]` | `[{"notes":"Cookie: [STRIPPED]\r\nX: y"}]` |
| 3 | `{"notes":"X: y\r\nCookie: a=SECRET9"}` (escaped-newline positive control) | `{"notes":"X: y\r\nCookie: [STRIPPED]"}` | `{"notes":"X: y\r\nCookie: [STRIPPED]"}` |

### MEASURED-RESIDUAL PROBE D — the cost of the narrowing (AR-27-11)

`{"tags":["Cookie: a=SECRET8"]}`, STRICT and BALANCED, identical in both modes.

| | BEFORE (bare quote) | AFTER (`:"`) |
|---|---|---|
| output | `{"tags":["Cookie: [STRIPPED]"]}` | `{"tags":["Cookie: a=SECRET8"]}` (byte-identical to input) |

A JSON array element opens on a bracket-quote or comma-quote sequence, so it is not a recognised start after this change. This is `AR-27-11`. It is cited in `Redaction.kt` twice, filed in `26-SECURITY.md` by plan 27-16, and **pinned by NO test anywhere under `src/`** — a green assertion that a cookie value survives a redacting policy is the artifact class 26-SECURITY.md standing-rule clause (vi) prohibits and `RedactingPolicySurvivalSweepTest` detects. Verified: `grep -rn '\["Cookie:' src/` returns only comment lines in `Redaction.kt`.

### RED RUNS of the new gates

`anHtmlAttributePayloadIsLeftByteIdenticalUnderBothRedactingModes`, against the pre-task-1 constant, verbatim:

```
org.opentest4j.AssertionFailedError: STRICT: a cookie-shaped run inside an HTML attribute is not a
header and must be left BYTE-IDENTICAL. The bare-quote start 27-11 shipped destroyed 1589 of 1714
characters of a realistic serialized tool result and removed all 40 of its content markers; this is
the gate that measurement should have tripped ==> expected:
<{"request":"GET /basket HTTP/1.1\r\nAccept: text/html\r\n\r\n","response":null,"notes":"<div
title=\"cookie: analytics disclosure notice\">IMPORTANTCONTENT IMPORTANTCONTENT IMPORTANTCONTENT
IMPORTANTCONTENT IMPORTANTCONTENT IMPORTANTCONTENT IMPORTANTCONTENT IMPORTANTCONTENT</div>"}> but was:
<{"request":"GET /basket HTTP/1.1\r\nAccept: text/html\r\n\r\n","response":null,"notes":"<div
title=\"cookie: [STRIPPED]"}>
```

Green after task 1, in both modes.

`contentAfterTheCookieValueInTheSameJsonStringIsMeasuredNotAssumed` is GREEN in both columns, and that is correct rather than a missing red: both of its fixtures sit at a genuine `:"` JSON string value open, which matched under the bare quote and still matches under the colon-quote sequence. It is a blast-radius NON-REGRESSION gate — it measures what the tail destroys between a value and its terminator, which no gate in the nest could observe before. The plan's acceptance criterion requires the RED run of test A specifically, and that is what is recorded above.

`theJsonStringOpenIsAValueOpenAndNotABareQuote`, against the pre-task-1 tree, verbatim:

```
org.opentest4j.AssertionFailedError: the third logical-line start must stay FIXED-WIDTH at 2
characters. A width-one value here means the bare double quote 27-11 shipped is back: a bare quote
is NOT a JSON string open — it also opens HTML attribute values, JS string literals and quoted CSV
fields — and it was MEASURED destroying 1589 of 1714 characters of a realistic serialized tool
result, removing all forty of its content markers while leaving the JSON structurally valid, so
every shape assertion passed and the model read a truncated body. Widening this constant back is a
CORRECTNESS REGRESSION on the primary MCP emission path, not a tightening.
  Read as: "\"" ==> expected: <2> but was: <1>
```

Green on the post-task-1 tree.

### Re-measured `Sentinel.BENIGN_CONTROL` live-function count

**7 live functions**, all in `SerializedEmissionRedactionTest`, unchanged by this plan. This is the count `RedactingPolicySurvivalSweepTest.BENIGN_ACCESSORS`' KDoc and `26-SECURITY.md` clause (vi) both quote, and it is what plan 27-15 must restate:

| # | Function |
|---|---|
| 1 | `headerNameAndBenignControlSurviveTheSerializedShape` |
| 2 | `everyCookieNameVariantCarriesADistinctSentinelAndNoneSurvives` |
| 3 | `siteMapEntryCarrierStripsCookiesInBothRedactingModes` |
| 4 | `issueDetailsCarrierStripsCookiesInBothRedactingModes` |
| 5 | `aCanonicalCookieAtTheOpenOfAJsonStringDoesNotSurviveStrict` |
| 6 | `aCanonicalCookieAtTheOpenOfAJsonStringDoesNotSurviveBalanced` |
| 7 | `bearerShapedAuthorizationIsStillRedactedAndTheHeaderNameSurvives` |

Neither new test adds an eighth: test A's non-vacuity control is an `assertFalse` on `Sentinel.JSON_STRING_OPEN_CONTROL`, not a `BENIGN_CONTROL` survival assertion, and test B asserts absences and byte identities only.

### Other acceptance measurements

| Check | Result |
|---|---|
| `sed -n '/private const val JSON_STRING_OPEN/p'` | one line, value `":\""` — two characters, colon first |
| declaration position | still BELOW `private const val JSON_ESCAPED_NEWLINE` (line 333 vs 322) |
| `grep -v '^[[:space:]]*[/*]' Redaction.kt \| grep -c 'logicalLineHeaderRule('` | **4** — one definition plus three uses, unchanged |
| `grep -n 'AR-27-11' Redaction.kt` | lines 266 and 325; line 266 is inside the contiguous rationale region above the fragments |
| `grep -n 'ALLOWLIST = emptyMap' RedactingPolicySurvivalSweepTest.kt` | one line — allowlist still empty |
| `REQUIREMENTS.md` changes across this plan's commit range | **zero** — the diff touches exactly three source files |
| plan verification gradle command | BUILD SUCCESSFUL, first pass, no `RedactionTest` flake |
| full `./gradlew test` | BUILD SUCCESSFUL — 1241 tests, 0 failures, 0 errors |
| `./gradlew ktlintCheck` | BUILD SUCCESSFUL |

## Decisions Made

- **Narrow rather than keep the bare quote.** The alternative — accept a 93% blast radius on the primary MCP emission path in exchange for catching an array-element string open — trades a high-severity correctness break for a low-severity residual. The narrowing was independently measured by `27-REVIEW-2.md` CR-03 and `27-VERIFICATION-4.md` gap 1, both of which showed it removes every measured false positive AND keeps both `notes` carriers closed. This SUMMARY records that provenance and claims no maintainer signature no artifact corroborates.
- **File the residual, pin it with nothing.** `AR-27-11` is cited in source, recorded here with both columns, and left untested on the `AR-27-09` precedent and standing-rule clause (vi).
- **Keep the degenerate gate.** It still asserts a true thing about the cross-field bound. Deleting it would remove real coverage to make room for a label; adding the label costs nothing.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Probe A shape 5 cannot reach byte identity; a narrowed shape 5b was added rather than the assertion weakened**

- **Found during:** Task 1 (post-edit probe run)
- **Issue:** The plan's PROBE A shape 5, `See the docs: "authorization: Bearer required" and KEEPTAIL`, is specified with an AFTER column of "byte-identical". It cannot be. The fixture carries the literal `Bearer `, which `bearerRegex` — an un-anchored, independently shipped rule invoked under `policy.redactTokens`, with nothing to do with `logicalLineHeaderRule` — claims regardless of any boundary. The AFTER output is `"authorization: Bearer [REDACTED]"`. The plan's expected value described a payload the shipped rule set was never going to leave alone.
- **Fix:** Recorded shape 5's real AFTER value verbatim, and added shape 5b — the same prose with the `Bearer ` token removed — as the clean proof that `authHeaderRegex` inherits the same start and is fixed by the same edit. 5b was measured in BOTH columns: it over-matched to `"authorization: [REDACTED]"` before and is byte-identical after. The plan's own instruction for this class of fixture collision ("narrow the FIXTURE and record why; never weaken the assertion") was applied to the probe.
- **Files modified:** none — this is a probe/record correction, not a code change.
- **Verification:** both columns of 5 and 5b measured against the compiled classes and recorded above.
- **Committed in:** n/a (recorded here; the code under test is `2935165`)

**Consequence for the plan's acceptance criteria:** the criterion "RED PROBE A, all five shapes … output byte-identical to input after the edit" is met for shapes 1-4 and 5b, and is NOT met for shape 5 as literally written, for a reason external to this change. The composer over-match that shape 5 exists to demonstrate IS closed in both columns.

---

**Total deviations:** 1 auto-fixed (1 bug, in the plan's probe fixture rather than in the code)
**Impact on plan:** none on scope. The narrowing, the gates and the pin are all as planned. The correction makes the record accurate instead of quietly reporting a byte identity that no shipped rule set could produce.

## Issues Encountered

- **The first draft of test B's fixture-2 guard scanned the whole payload for escaped newlines** and therefore always failed: the `notesCarrier` `request` field always carries escaped CRLFs, so the guard was asserting about the wrong field and could never pass. Fixed before the task-2 commit by scoping the guard to the `notes` string, with a comment saying why. Caught by the RED run — which is what the RED run is for.
- **`RedactionTest` did not flake.** The plan flagged it as a known wall-clock/`SafeRegex`-deadline flake under CPU load; it was green on the first pass of both the plan verification command and the full suite, so no re-run was needed and there is nothing to distinguish.

## Known Stubs

None.

## Threat Flags

Each `T-27-14-*` row from the plan's threat model, with its measured outcome.

| Threat ID | Category | Disposition | Outcome |
|---|---|---|---|
| T-27-14-01 | Tampering — `Redaction.apply` output on the serialized MCP emission path | mitigate | **MITIGATED.** PROBE B: 1589-character destruction → 0, 40 markers preserved, byte-identical in both redacting modes. Gated by `anHtmlAttributePayloadIsLeftByteIdenticalUnderBothRedactingModes`, whole-payload `assertEquals` in STRICT and BALANCED, RED before the narrowing. |
| T-27-14-02 | Information Disclosure — `logicalLineHeaderRule` reach on `HttpRequestResponse.notes` | mitigate | **MITIGATED, no divergence.** PROBE C: all three cases produce `Cookie: [STRIPPED]` in BOTH columns and both modes, measured in the same run as the narrowing. No stop condition triggered. |
| T-27-14-03 | Information Disclosure — JSON array-element string open | accept | **ACCEPTED and MEASURED.** PROBE D: matched before, byte-unchanged after. Filed as `AR-27-11`, cited twice in `Redaction.kt`, recorded here with both columns, pinned by no test. Plan 27-16 owes the register entry. |
| T-27-14-04 | Denial of Service — fixed-width lookbehind cost model, `SafeRegex` 50ms deadline | mitigate | **MITIGATED.** Both new characters are regex-literal; the lookbehind stays fixed-width at two and no quantifier, alternation or tail changed. Pinned from source by `EXPECTED_JSON_STRING_OPEN_WIDTH = 2`. `RedactionTest` green first pass with no deadline flake. |
| T-27-14-05 | Repudiation — the record of what this round changed | transfer | **TRANSFERRED, as planned.** Source now carries both costs and the `AR-27-11` citation; this SUMMARY carries both columns of all four probes and the provenance of the decision. Plan 27-16 still owes: the `AR-27-11` register entry in `26-SECURITY.md`, the T-26-02-01 clause (7) append, and `THIRD_OPEN_FINDING` in `LogicalLineBoundaryScopeTest`. |
| T-27-14-SC | Tampering — package-manager installs | accept | **EMPTY POPULATION, confirmed.** No dependency added, no install command run; the Gradle dependency set is byte-unchanged. |

## Next Phase Readiness

- **Plan 27-15** must restate the `Sentinel.BENIGN_CONTROL` live-function count as **7** — re-measured above and unchanged by this plan.
- **Plan 27-16** owes three things this plan cites forward: the `AR-27-11` entry in `26-SECURITY.md`, the T-26-02-01 clause (7) append, and `THIRD_OPEN_FINDING` in `LogicalLineBoundaryScopeTest`'s companion. Until the register entry lands, `AR-27-11` is cited in source and in this SUMMARY but not filed.
- **PRIV-05 is NOT closed by this plan.** `REQUIREMENTS.md` is untouched and PRIV-05 stays `[ ]`. `AR-27-08` and `InjectionPointExtractor.kt:29` remain owned by Phase 28 and were not touched.
- `AR-27-09` — the leading-whitespace / obs-fold fourth start — is still unrecognised and still out of scope.

---
*Phase: 27-priv-05-gap-closure-sanitize-headers*
*Completed: 2026-08-26*
