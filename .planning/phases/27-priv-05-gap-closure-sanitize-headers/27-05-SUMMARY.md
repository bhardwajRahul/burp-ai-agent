---
phase: 27-priv-05-gap-closure-sanitize-headers
plan: 05
subsystem: privacy
tags: [kotlin, redaction, auth-headers, mcp, json-serialization, regex, junit5, source-tripwire]

requires:
  - phase: 27-priv-05-gap-closure-sanitize-headers
    plan: "04"
    provides: "Redaction.logicalLineHeaderRule and the three boundary fragments this plan applies to authHeaderRegex unchanged"
provides:
  - "authHeaderRegex fires on the serialized MCP emission shape in STRICT and BALANCED — a plain-token X-API-Key value no longer leaves the process verbatim"
  - "SerializedEmissionSiteInventoryTest — the 14-site emission inventory and all three addTool registration paths pinned as gated counts"
  - "LogicalLineBoundaryScopeTest — the composer's rule set pinned to three rules, with hostHeaderRegex's exclusion asserted from source and from the rationale comment"
  - "Measured after-state for AR-27-04: Host: header value and SiteMapEntry.url both survive STRICT on the serialized shape, with a reproducible probe recorded"
affects: [27-06, privacy, mcp, security-register]

actuals:
  tokens: 61000
  tasks: 3
  commits: 3

tech-stack:
  added: []
  patterns:
    - "Declaration-block isolation by name and INDENTATION rather than paren counting, because regex-fragment initializers carry unbalanced parens inside string literals"
    - "A source-state tripwire that asserts the rationale COMMENT and the composition agree, so a decision cannot drift out of the code it governs"
    - "Path-and-count pinning of a call-site set rather than file:line, so the gate does not rot on unrelated reformatting"
    - "Pre-change output captured from the compiled shipped classes as the expectation for a byte-identity test"

key-files:
  created:
    - src/test/kotlin/com/six2dez/burp/aiagent/mcp/tools/SerializedEmissionSiteInventoryTest.kt
    - src/test/kotlin/com/six2dez/burp/aiagent/redact/LogicalLineBoundaryScopeTest.kt
  modified:
    - src/main/kotlin/com/six2dez/burp/aiagent/redact/Redaction.kt
    - src/test/kotlin/com/six2dez/burp/aiagent/mcp/tools/SerializedEmissionRedactionTest.kt

key-decisions:
  - "D-27-12 executed as specified. authHeaderRegex rebuilt through logicalLineHeaderRule; the 16-name alternation is byte-identical — the four name-carrying lines do not appear in the diff at all."
  - "D-27-13 held. hostHeaderRegex stays excluded and the exclusion is now asserted from source by LogicalLineBoundaryScopeTest, not only recorded in prose."
  - "D-27-14 executed with one recorded refinement: the addTool registration set is pinned by PATH and COUNT, not by file:line, matching CookieHeaderRuleOwnershipTest's stated path-keyed discipline. Line numbers are recorded here and in the test's constant comment."
  - "SCOPE STRENGTHENED beyond the plan: theMeasuredEmissionSiteCountIsPinned also scans ALL of src/main/kotlin for the emission call shape and fails if it appears outside the two pinned executors. The plan's KDoc bound confessed that hole; it cost three lines to close and a fourth red probe proves it fires."
  - "FALSIFIED ACCEPTANCE CRITERION (task 1, red probe 2): removing a tool name from the raw-HTTP set fails theMeasuredEmissionSiteCountIsPinned via the names-vs-count cross-check, NOT everyEmissionToolNameAppearsInBothExecutors — a smaller set checks fewer names and stays green. Both the criterion's literal probe and the probe it intended were run and recorded."
  - "FALSIFIED ACCEPTANCE CRITERION (task 3): 'no path containing Probe appears anywhere under src/' is unsatisfiable — McpSupervisorProbeTest.kt has existed since phase 20 and is unrelated. The intended invariant was verified directly instead."

requirements-completed: [PRIV-05]

coverage:
  - id: D1
    description: "A credential-bearing auth header value (plain-token X-API-Key) does not survive the serialized emission shape in STRICT or BALANCED"
    requirement: PRIV-05
    verification:
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/mcp/tools/SerializedEmissionRedactionTest.kt#credentialBearingAuthHeaderValueDoesNotSurviveTheSerializedShapeUnderStrict"
        status: pass
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/mcp/tools/SerializedEmissionRedactionTest.kt#credentialBearingAuthHeaderValueDoesNotSurviveTheSerializedShapeUnderBalanced"
        status: pass
      - kind: command
        ref: "ResidualProbe against build/classes/kotlin/main: STRICT/BALANCED APIKEY SURVIVES -> STRIPPED"
        status: pass
    human_judgment: false
  - id: D2
    description: "authHeaderRegex's inheritance of plan 27-04's escape-pair tail and two-branch shape is VERIFIED, not assumed"
    requirement: PRIV-05
    verification:
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/mcp/tools/SerializedEmissionRedactionTest.kt#anAuthHeaderValueEndingInABackslashAtTheEndOfThePayloadStillParsesAsJson"
        status: pass
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/mcp/tools/SerializedEmissionRedactionTest.kt#aRealMultiLineAuthHeaderValueContainingAQuoteIsStillStrippedWhole"
        status: pass
    human_judgment: false
  - id: D3
    description: "The tool contract, the name-preservation invariant and OFF-mode byte-identity hold on the auth-bearing shape"
    requirement: PRIV-05
    verification:
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/mcp/tools/SerializedEmissionRedactionTest.kt#redactedAuthBearingSerializedOutputStillParsesAsJsonWithTheSameKeySet"
        status: pass
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/mcp/tools/SerializedEmissionRedactionTest.kt#bearerShapedAuthorizationIsStillRedactedAndTheHeaderNameSurvives"
        status: pass
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/mcp/tools/SerializedEmissionRedactionTest.kt#offModeLeavesTheAuthBearingSerializedShapeByteIdentical"
        status: pass
    human_judgment: false
  - id: D4
    description: "The 14-site serialized-emission inventory is pinned by measurement, with a per-file split, per-tool-name presence in BOTH executors, and a whole-main stray scan"
    requirement: PRIV-05
    verification:
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/mcp/tools/SerializedEmissionSiteInventoryTest.kt#theMeasuredEmissionSiteCountIsPinned"
        status: pass
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/mcp/tools/SerializedEmissionSiteInventoryTest.kt#everyEmissionToolNameAppearsInBothExecutors"
        status: pass
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/mcp/tools/SerializedEmissionSiteInventoryTest.kt#theInventoryScanIsNonVacuous"
        status: pass
    human_judgment: false
  - id: D5
    description: "Every emission site and ALL THREE addTool registration paths are asserted downstream of McpToolContext.redactIfNeeded, including McpToolHandlers.kt's wrapper-less delegation"
    requirement: PRIV-05
    verification:
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/mcp/tools/SerializedEmissionSiteInventoryTest.kt#everyEmissionSiteIsDownstreamOfTheSingleRedactingChokePoint"
        status: pass
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/mcp/tools/SerializedEmissionSiteInventoryTest.kt#everyToolRegistrationPathReachesTheChokePoint"
        status: pass
    human_judgment: false
  - id: D6
    description: "The set of rules carrying the logical-line boundary is pinned to three, hostHeaderRegex is asserted excluded, and the rationale comment must keep agreeing with the code"
    requirement: PRIV-05
    verification:
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/redact/LogicalLineBoundaryScopeTest.kt#theBoundaryFragmentsAreComposedIntoTheMeasuredRuleSetAndNoOther"
        status: pass
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/redact/LogicalLineBoundaryScopeTest.kt#theStatedBoundIsPresentWhereAReaderMeetsIt"
        status: pass
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/redact/LogicalLineBoundaryScopeTest.kt#theScopeScanIsNonVacuous"
        status: pass
    human_judgment: false
  - id: D7
    description: "Multi-line behaviour is unchanged: RedactionTest holds a zero-line diff and stays 46/46 green"
    requirement: PRIV-05
    verification:
      - kind: command
        ref: "git diff --stat HEAD -- src/test/kotlin/com/six2dez/burp/aiagent/redact/RedactionTest.kt (empty) + RedactionTest 46/46 green"
        status: pass
    human_judgment: false
  - id: D8
    description: "The excluded host residual (AR-27-04) is measured with a reproducible, uncommitted probe whose source and output are recorded; nothing green in the repository asserts a leak"
    requirement: PRIV-05
    verification:
      - kind: command
        ref: "ResidualProbe source + command + STRICT/BALANCED verdict lines quoted verbatim in this SUMMARY; git status --porcelain src/ clean"
        status: pass
    human_judgment: false

duration: 48 min
completed: 2026-08-24
status: complete
---

# Phase 27 Plan 05: Auth-Header Closure and the Pinned Emission Inventory Summary

`authHeaderRegex` now recognises a JSON-escaped newline as a logical line boundary, so a plain-token
`X-API-Key` value stops reaching the AI backend verbatim through the MCP tool results that embed a raw
HTTP message in a JSON string — and the 14-site emission inventory plus all three tool-registration
paths are pinned as gated counts, so the class of miss that produced this phase fails a test instead
of a review.

**Duration:** 48 min · **Tasks:** 3 · **Commits:** 3 · **Files:** 4 (2 created, 2 modified)

---

## What Was Built

### Task 1 — the emission-site inventory tripwire (`6a54bbf`)

`SerializedEmissionSiteInventoryTest`, 5 tests, over a measured inventory declared as named constants
so a drift reads as a data change rather than a logic change.

### Task 2 — the auth-header closure (`b1b70e9`)

`authHeaderRegex` rebuilt as `logicalLineHeaderRule(<its unchanged 16-name alternation>)`, plus 7
assertions added to `SerializedEmissionRedactionTest` (17 → 24).

### Task 3 — the boundary-scope tripwire (`87751a8`)

`LogicalLineBoundaryScopeTest`, 3 tests, pinning that exactly three rules carry the composer and
`hostHeaderRegex` does not — and that the rationale comment keeps saying so.

---

## Measurements

Everything below was re-measured on this checkout rather than taken from the plan.

### The emission inventory — measured, then pinned

```
$ grep -rhE "encodeToString\(it\.(toSerializableForm|toSiteMapEntry)\(" \
    src/main/kotlin/com/six2dez/burp/aiagent/mcp/tools/ --include=*.kt | wc -l
      14

$ grep -rcE "encodeToString\(it\.(toSerializableForm|toSiteMapEntry)\(" \
    src/main/kotlin/com/six2dez/burp/aiagent/mcp/tools/ --include=*.kt | grep -v ':0'
src/main/kotlin/com/six2dez/burp/aiagent/mcp/tools/McpToolLegacy.kt:7
src/main/kotlin/com/six2dez/burp/aiagent/mcp/tools/McpToolExecutorImpl.kt:7

$ grep -rnE "encodeToString\(it\.(toSerializableForm|toSiteMapEntry)\(" src/main/kotlin --include=*.kt | wc -l
      14
```

**14 total, 7 / 7, and zero anywhere else in `src/main/kotlin`.** The plan's table reproduced exactly,
including every line number:

| Tool name | Carrier | `McpToolExecutorImpl.kt` | `McpToolLegacy.kt` | Raw HTTP? |
|---|---|---|---|---|
| `scanner_issues` | `IssueDetails.requestResponses` | 608 | 475 | yes |
| `proxy_http_history` | `HttpRequestResponse` | 740 | 622 | yes |
| `proxy_http_history_regex` | `HttpRequestResponse` | 760 | 639 | yes |
| `proxy_ws_history` | `WebSocketMessage` | 836 | 713 | no |
| `proxy_ws_history_regex` | `WebSocketMessage` | 855 | 729 | no |
| `site_map` | `SiteMapEntry` | 873 | 744 | yes |
| `site_map_regex` | `SiteMapEntry` | 896 | 764 | yes |

5 raw-HTTP names → 10 sites, 2 WebSocket names → 4 sites. The verifier's 12 and the UAT note's 10 were
both partial; 14 is what the tree holds.

### The three `addTool` registration paths — measured

```
$ grep -rn 'addTool(' src/main/kotlin --include=*.kt
src/main/kotlin/com/six2dez/burp/aiagent/mcp/tools/McpTool.kt:34:    addTool(
src/main/kotlin/com/six2dez/burp/aiagent/mcp/tools/McpTool.kt:72:    addTool(
src/main/kotlin/com/six2dez/burp/aiagent/mcp/tools/McpToolHandlers.kt:122:    addTool(
```

**Exactly three, at exactly the plan's line numbers.** Their reachability, each verified from source:

| # | Site | How its output reaches the choke point |
|---|---|---|
| A | `McpTool.kt:34` (`mcpTool<I>`) | wraps directly at `McpTool.kt:45`; `mcpPaginatedTool` delegates here at `:82` |
| B | `McpTool.kt:72` (no-input overload) | wraps directly at `McpTool.kt:78` |
| C | `McpToolHandlers.kt:122` (`registerToolHandler`) | **no wrapper of its own** — `grep -c redactIfNeeded McpToolHandlers.kt` returns **0**. Safe ONLY because its handler calls `McpToolExecutor.executeToolResult(` at `:129`, which reaches `context.redactIfNeeded(output)` at `McpToolExecutorImpl.kt:1037` |

`McpToolLegacy.kt` also returns **0** for `redactIfNeeded`: all seven of its emission sites are inside
`mcpPaginatedTool<…>` registrations (opened at 466, 607, 625, 701, 716, 732, 747), which route through
`mcpTool<I>`'s wrapping handler. That is the structural reason a one-line change to a regex in
`redact/Redaction.kt` reaches all 14 sites.

### The auth-header leak — BEFORE, on the compiled shipped classes

Probe run against `build/classes/kotlin/main` on JDK 21 (temurin-21), salt `probe-salt`,
`recordMapping=false`, on the real serialized shape. **Full source and command are in the AR-27-04
section below** — the same instrument produced both readings.

```
payload bytes: 335
STRICT    COOKIE          STRIPPED
STRICT    SETCOOKIE       STRIPPED
STRICT    APIKEY          SURVIVES
STRICT    BEARER          STRIPPED
STRICT    HOST-HEADER     SURVIVES
STRICT    URL-FIELD       SURVIVES
STRICT    BENIGN-CONTROL  SURVIVES
---- STRICT output ----
{"url":"https://shop.example/basket","request":"GET /basket HTTP/1.1\r\nHost: shop.example\r\nCookie: [STRIPPED]\r\nSet-Cookie: [STRIPPED]\r\nX-API-Key: probeapikeysentinel\r\nAuthorization: Bearer [REDACTED]\r\nX-Request-Id: benignprobecontrol\r\n\r\n","response":"HTTP/1.1 200 OK\r\n\r\n"}
BALANCED  COOKIE          STRIPPED
BALANCED  SETCOOKIE       STRIPPED
BALANCED  APIKEY          SURVIVES
BALANCED  BEARER          STRIPPED
BALANCED  HOST-HEADER     SURVIVES
BALANCED  URL-FIELD       SURVIVES
BALANCED  BENIGN-CONTROL  SURVIVES
```

Two things this reading shows that a summary sentence would flatten:

1. **`APIKEY SURVIVES` in both redacting modes** — a credential leaving the process verbatim under the
   strongest privacy mode. That is the leak this plan closes.
2. **`Authorization: Bearer [REDACTED]`** — the header rule did NOT fire. The un-anchored `bearerRegex`
   claimed the token while `authHeaderRegex` missed the line entirely. Bearer survived by luck; a plain
   token had no luck to survive by.

**Note on the plan's quoted before-state.** `<measured_facts>` shows `STRICT COOKIE SURVIVES`. That was
measured before wave 4. My base includes wave 4's fix, so the cookie rows read `STRIPPED` — expected,
and useful: they are the positive control proving the probe ran against fresh classes.

### AFTER, re-run on freshly compiled classes

`JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew compileKotlin --rerun`, then the identical probe:

```
STRICT    COOKIE          STRIPPED       BALANCED  COOKIE          STRIPPED
STRICT    SETCOOKIE       STRIPPED       BALANCED  SETCOOKIE       STRIPPED
STRICT    APIKEY          STRIPPED       BALANCED  APIKEY          STRIPPED
STRICT    BEARER          STRIPPED       BALANCED  BEARER          STRIPPED
STRICT    HOST-HEADER     SURVIVES       BALANCED  HOST-HEADER     SURVIVES
STRICT    URL-FIELD       SURVIVES       BALANCED  URL-FIELD       SURVIVES
STRICT    BENIGN-CONTROL  SURVIVES       BALANCED  BENIGN-CONTROL  SURVIVES
---- STRICT output ----
{"url":"https://shop.example/basket","request":"GET /basket HTTP/1.1\r\nHost: shop.example\r\nCookie: [STRIPPED]\r\nSet-Cookie: [STRIPPED]\r\nX-API-Key: [REDACTED]\r\nAuthorization: [REDACTED]\r\nX-Request-Id: benignprobecontrol\r\n\r\n","response":"HTTP/1.1 200 OK\r\n\r\n"}
```

`APIKEY` moves `SURVIVES → STRIPPED` in both modes, and `Authorization` now reads `[REDACTED]` rather
than `Bearer [REDACTED]` — the header rule fires and its name-preserving lambda replaces the whole
value. The benign control still survives, so this is not blanket destruction. `HOST-HEADER` and
`URL-FIELD` are unchanged **on purpose** (AR-27-04, below).

### The name alternation is untouched

The plan's baselines re-measured on this checkout, before any edit: `authHeaderRegex` = **6**,
`hostHeaderRegex` = **3**, alternation holds **16** names. All three match what wave 4 recorded.

The only removed CODE lines in `Redaction.kt`'s `authHeaderRegex` are the three that carried the
boundary, and none of them carries a header name:

```
-        Regex(
-            "(?im)^(" +
-                "):\\s*.+$",
```

The four name-carrying lines do not appear in the diff at all. Counted after the change: **16 names**,
the identical set.

### RED, recorded before the `Redaction.kt` edit

`SerializedEmissionRedactionTest` on the unmodified rule: **24 tests, 3 failed.**

```
AuthHeaderCredentials > credentialBearingAuthHeaderValueDoesNotSurviveTheSerializedShapeUnderStrict()
org.opentest4j.AssertionFailedError: a plain-token X-API-Key value must not survive STRICT redaction
of the serialized shape. It is not bearer-, basic- or JWT-shaped, so no un-anchored token rule rescues
it and authHeaderRegex is the only control (got: {"request":"GET /basket HTTP/1.1\r\nX-API-Key:
wibble=sentinelsierra\r\nX-Request-Id: benignidcontrolvalue\r\n\r\n","response":null,"notes":null})
==> expected: <false> but was: <true>

AuthHeaderCredentials > credentialBearingAuthHeaderValueDoesNotSurviveTheSerializedShapeUnderBalanced()
org.opentest4j.AssertionFailedError: BALANCED sets redactTokens too, so the API-key value must not
survive (got: {"request":"GET /basket HTTP/1.1\r\nX-API-Key: wibble=sentineltango\r\nX-Request-Id:
benignidcontrolvalue\r\n\r\n","response":null,"notes":null}) ==> expected: <false> but was: <true>

AuthHeaderCredentials > anAuthHeaderValueEndingInABackslashAtTheEndOfThePayloadStillParsesAsJson()
org.opentest4j.AssertionFailedError: the auth header value must still be redacted (got:
{"request":"GET /basket HTTP/1.1\r\nX-API-Key: wibble=sentinelxray\\","response":null,"notes":null})
==> expected: <false> but was: <true>
```

The plan predicted two RED tests; **three** went red. The extra one is test 6, failing on its ABSENCE
half rather than its parse half — the payload was untouched, so it parsed fine and leaked. A stronger
red probe than the plan anticipated, recorded rather than smoothed over.

### The inherited blocker, VERIFIED rather than assumed

Both inheritance tests are green after the change and both were run against the composed rule, not
reasoned about:

- **`anAuthHeaderValueEndingInABackslashAtTheEndOfThePayloadStillParsesAsJson`** — the value ends in a
  backslash as the last content in the JSON string, the exact shape a "quote not preceded by a
  backslash" terminator destroys. `assertSameJsonShape` passes: the escape-pair tail carried across to
  `authHeaderRegex` intact.
- **`aRealMultiLineAuthHeaderValueContainingAQuoteIsStillStrippedWhole`** — a REAL multi-line
  `X-API-Key: a="q"; snork=sentinelyankee` is still stripped whole, character-identical to what
  shipped. The two-branch shape carried across too.

The expectation for the second is **measured, not typed**. Captured from the PRE-CHANGE compiled
classes with a throwaway harness:

```
IN :  GET /a HTTP/1.1\r\nX-API-Key: a="q"; snork=sentinelyankee\r\nAccept: text/html\r\n\r\n
OUT:  GET /a HTTP/1.1\r\nX-API-Key: [REDACTED]\r\nAccept: text/html\r\n\r\n
```

So `SHIPPED_REAL_MULTILINE_AUTH_OUTPUT` cannot have been typed to match whatever the new rule happens
to do. The harness lives in the scratchpad and is not committed.

### Four red probes for the inventory tripwire (the plan asked for three)

| # | Mutation | Test that went RED | Message |
|---|---|---|---|
| 1 | `EXPECTED_EMISSION_SITES` 14 → 13 | `theMeasuredEmissionSiteCountIsPinned` | `the measured serialized-emission inventory has drifted from the pinned total. Per file: {…ExecutorImpl.kt=7, …Legacy.kt=7} ==> expected: <13> but was: <14>` |
| 2a | removed `"site_map_regex"` from the raw-HTTP set | `theMeasuredEmissionSiteCountIsPinned` | `the raw-HTTP site count and the raw-HTTP tool-name set disagree: 4 names across 2 executors ==> expected: <10> but was: <8>` |
| 2b | renamed it `"site_map_regexx"` | `everyEmissionToolNameAppearsInBothExecutors` | `the tool name "site_map_regexx" is not registered in …McpToolExecutorImpl.kt …` |
| 3 | dropped `McpToolHandlers.kt` from the pinned set | `everyToolRegistrationPathReachesTheChokePoint` | `the set of \`addTool(\` registration sites has changed … expected: <{…McpTool.kt=2}> but was: <{…McpTool.kt=2, com/six2dez/…}>` |
| 4 | added the emission call shape to a third file (`McpToolHelpers.kt`) | `theMeasuredEmissionSiteCountIsPinned` | `the measured serialized-emission call shape appears OUTSIDE the two pinned executor files … [com/six2dez/burp/aiagent/mcp/tools/McpToolHelpers.kt]` |

Probe 2 needed splitting — see **Deviations**. Probe 4 covers the scope strengthening described there.

### Two red probes for the scope tripwire

| # | Mutation | Message |
|---|---|---|
| A | `hostHeaderRegex = logicalLineHeaderRule("(host)")` | `hostHeaderRegex must NOT be routed through \`logicalLineHeaderRule(\` (D-27-13). Two measured reasons: anonymizeHost writes into a de-anonymisation map bounded by RedactionHostMapBoundTest … and SiteMapEntry.url carries the same host VERBATIM …` |
| B | `authHeaderRegex` hand-inlined back to `Regex("(?im)^(…):\s*.+$")` | `authHeaderRegex must be built by exactly one \`logicalLineHeaderRule(\` call. Plan 27-04 routed every logical-line header rule through ONE composer so the boundary cannot be edited for one rule and forgotten for the others — a hand-inlined rule here is that drift beginning.` |

Probe A caught a real weakness in my first draft: the isolation guard probed for a literal `Regex(` in
the excluded rule's block, so it fired INSTEAD of the assertion carrying the claim. Coupling a guard to
one implementation shape is how a gate ends up reporting the wrong thing. Rewritten to check only that
the declaration was found, then re-run — the negative assertion is now the one that speaks. Both probes
reverted via `git checkout -- Redaction.kt`; `git status --short` clean afterwards.

### AR-27-04 — the excluded residual, measured with a reproducible probe

`Host: shop.example` and `"url":"https://shop.example/basket"` **both survive un-anonymised under
STRICT** on the serialized emission shape, after this plan as before it. Deliberate, per D-27-13.

Nothing under `src/` asserts this. It is recorded here in prose with a probe a later reader can re-run,
because a green test asserting that a host reaches a backend under STRICT is precisely the artifact a
future audit misreads — and this phase exists because of one.

**Probe source, verbatim** (`ResidualProbe.java`, kept out of the repository on purpose):

```java
// Phase 27 plan 27-05 — throwaway residual probe. NOT committed, by design: a green assertion in
// src/ that a Host value survives STRICT is the artifact this phase exists to stop producing.
//
// Runs Redaction.apply on the REAL serialized emission shape (a raw HTTP message inside a JSON
// string, CRLFs already escaped by kotlinx.serialization) against the compiled shipped classes.
//
//   javac -cp build/classes/kotlin/main:<kotlin-stdlib.jar> -d <out> ResidualProbe.java
//   java  -cp build/classes/kotlin/main:<kotlin-stdlib.jar>:<out> ResidualProbe
import com.six2dez.burp.aiagent.redact.PrivacyMode;
import com.six2dez.burp.aiagent.redact.Redaction;
import com.six2dez.burp.aiagent.redact.RedactionPolicy;

public final class ResidualProbe {
    // The serialized shape: exactly what toolJson.encodeToString emits for a SiteMapEntry — the
    // `url` field carries the SAME host as the `Host:` header, which is the whole point of D-27-13.
    private static final String PAYLOAD =
        "{\"url\":\"https://shop.example/basket\","
            + "\"request\":\"GET /basket HTTP/1.1"
            + "\\r\\nHost: shop.example"
            + "\\r\\nCookie: wibble=probecookiesentinel"
            + "\\r\\nSet-Cookie: wobble=probesetcookiesentinel"
            + "\\r\\nX-API-Key: probeapikeysentinel"
            + "\\r\\nAuthorization: Bearer probebearersentinel"
            + "\\r\\nX-Request-Id: benignprobecontrol"
            + "\\r\\n\\r\\n\","
            + "\"response\":\"HTTP/1.1 200 OK\\r\\n\\r\\n\"}";

    private static final String[][] ARTIFACTS = {
        // label            needle                        what it proves
        {"COOKIE", "probecookiesentinel"},
        {"SETCOOKIE", "probesetcookiesentinel"},
        {"APIKEY", "probeapikeysentinel"},
        {"BEARER", "probebearersentinel"},
        {"HOST-HEADER", "Host: shop.example"},
        {"URL-FIELD", "\"url\":\"https://shop.example/basket\""},
        {"BENIGN-CONTROL", "benignprobecontrol"},
    };

    public static void main(String[] args) {
        System.out.println("payload bytes: " + PAYLOAD.length());
        for (PrivacyMode mode : new PrivacyMode[] {PrivacyMode.STRICT, PrivacyMode.BALANCED}) {
            RedactionPolicy policy = RedactionPolicy.Companion.fromMode(mode);
            String out = Redaction.INSTANCE.apply(PAYLOAD, policy, "probe-salt", false);
            for (String[] artifact : ARTIFACTS) {
                boolean present = out.contains(artifact[1]);
                System.out.printf("%-9s %-15s %s%n", mode, artifact[0], present ? "SURVIVES" : "STRIPPED");
            }
            System.out.println("---- " + mode + " output ----");
            System.out.println(out);
        }
    }
}
```

**Exact commands** (paths as run; the stdlib jar is the one Gradle resolves for Kotlin 2.1.21):

```
JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew compileKotlin --rerun

KSTD=~/.gradle/caches/modules-2/files-2.1/org.jetbrains.kotlin/kotlin-stdlib/2.1.21/\
97a0975aa19d925e109537af60eb46902920015c/kotlin-stdlib-2.1.21.jar

/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home/bin/javac \
  -cp build/classes/kotlin/main:$KSTD -d <scratch>/probeout <scratch>/ResidualProbe.java

/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home/bin/java \
  -cp build/classes/kotlin/main:$KSTD:<scratch>/probeout ResidualProbe
```

**Verdict lines for AR-27-04**, after the fix, on fresh classes:

| Artifact | STRICT | BALANCED |
|---|---|---|
| `Host:` header value (`shop.example`) | **SURVIVES** | **SURVIVES** |
| `url` field value (`https://shop.example/basket`) | **SURVIVES** | **SURVIVES** |
| cookie sentinel (positive control) | STRIPPED | STRIPPED |

The cookie control reads STRIPPED, so the probe ran against the fixed classes and the measurement is
valid. Under BALANCED `anonymizeHosts` is `false`, so the host is expected to survive there; the
finding is the STRICT row, where the policy asks for anonymisation and does not get it on this shape.

### Test results, verified BY NAME in the JUnit XML

`SerializedEmissionSiteInventoryTest` — **5 tests, 0 skipped, 0 failures, 0 errors**:
`theMeasuredEmissionSiteCountIsPinned`, `everyEmissionToolNameAppearsInBothExecutors`,
`everyEmissionSiteIsDownstreamOfTheSingleRedactingChokePoint`,
`everyToolRegistrationPathReachesTheChokePoint`, `theInventoryScanIsNonVacuous`.

`LogicalLineBoundaryScopeTest` — **3 tests, 0 skipped, 0 failures, 0 errors**:
`theBoundaryFragmentsAreComposedIntoTheMeasuredRuleSetAndNoOther`, `theScopeScanIsNonVacuous`,
`theStatedBoundIsPresentWhereAReaderMeetsIt`.

`SerializedEmissionRedactionTest` — **24 tests, 0 skipped, 0 failures** (17 from plan 27-04 + 7 here):

| Nested class | tests |
|---|---|
| *(outer)* — `everySentinelInThisFileIsDistinct` | 1 |
| `ProxyHistoryCarrier` | 7 |
| `SiteMapCarrier` | 1 |
| `IssueDetailsCarrier` | 1 |
| `Hazards` | 7 |
| **`AuthHeaderCredentials`** (new) | **7** |

`everySentinelInThisFileIsDistinct` is among them and green — the distinctness gate still covers the
file after this plan added seven sentinels to it, because `ALL_SENTINELS` derives from the `Sentinel`
enum and the new values are entries.

`RedactionTest` — **46 tests, 0 failures**, and
`git diff --stat HEAD -- src/test/kotlin/com/six2dez/burp/aiagent/redact/RedactionTest.kt` produced **no
output** at every gate. The file is unmodified, which is what makes the multi-line byte-identity claim
evidence rather than assertion.

### Full gate, verbatim

```
JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test detekt ktlintCheck --rerun

> Task :compileTestKotlin
> Task :detekt
> Task :runKtlintCheckOverTestSourceSet
> Task :ktlintTestSourceSetCheck
> Task :ktlintCheck
> Task :test
> Task :jacocoTestReport

BUILD SUCCESSFUL in 2m 51s
15 actionable tasks: 6 executed, 9 up-to-date
```

Aggregated across **170 test classes: 1184 tests, 1 skipped, 0 failures, 0 errors.** The single skip is
`ExternalMcpClientManagerTest.connectAndListTools_returnsExpectedCount`, `@Disabled` since phase 16
because it requires a live MCP server — pre-existing, untouched file, out of scope. **No `SafeRegex`
wall-clock flake occurred; no re-run was needed.**

---

## Deviations from Plan

### 1. [Rule 2 — missing critical] The inventory scan now covers all of `src/main/kotlin` for the call shape

**Found during:** Task 1, while writing the class KDoc's stated bound.

**Issue.** The plan scopes the emission-site scans to two named files and has the KDoc confess that a
site added in a THIRD file is invisible. But `T-27-05-01` is precisely "a future emission site added
outside the fixed rule's reach", and a whole-main scan for the same call shape closes that hole in
three lines. A tripwire whose own documentation names a hole it can cheaply close is a tripwire with a
hole in it.

**Fix.** `theMeasuredEmissionSiteCountIsPinned` additionally walks all of `src/main/kotlin` and fails if
the measured call shape appears outside `EMISSION_EXECUTOR_FILES`. Measured: it appears nowhere else
(whole-main count = 14 = the two-file count). Red probe 4 above proves the assertion fires.

**Bound restated honestly, not widened.** The class KDoc still states both bounds separately, as the
acceptance criterion requires — it just states them accurately: the per-file split, tool-name presence
and choke-point scans remain scoped to two named files; the CALL-SHAPE scan is whole-main; and the
residual bound over the whole tree is the SHAPE, since a site written differently is invisible
everywhere. Nothing here is stated as complete coverage.

**Files modified:** `SerializedEmissionSiteInventoryTest.kt` **Commit:** `6a54bbf`

### 2. [Recorded, not fixed] Task 1's red probe 2 does not fail the test the criterion names

**Found during:** Task 1 gate execution.

The criterion says: *"temporarily remove one tool name from the raw-HTTP set, re-run
`everyEmissionToolNameAppearsInBothExecutors`, observe the failure."*

**Measured: that test stays GREEN.** It asserts "every name in the set appears in both executors", so a
smaller set simply checks fewer names. Removing `"site_map_regex"` instead fails
`theMeasuredEmissionSiteCountIsPinned`, through the names-vs-count cross-check:

```
the raw-HTTP site count and the raw-HTTP tool-name set disagree: 4 names across 2 executors
==> expected: <10> but was: <8>
```

The criterion's INTENT — prove the tool-name set is load-bearing — is satisfied by that, and I also ran
the probe it meant: renaming the entry to `"site_map_regexx"` keeps the set at 5, so the count check
still passes and `everyEmissionToolNameAppearsInBothExecutors` goes red on its own terms. Both probes
are in the table above. No source was reworded to satisfy the criterion; it is recorded as falsified.

### 3. [Recorded, not fixed] Task 3's "no `Probe` path under `src/`" criterion is unsatisfiable

**Found during:** Task 3 close-out.

`find src -iname '*Probe*'` returns `src/test/kotlin/com/six2dez/burp/aiagent/mcp/McpSupervisorProbeTest.kt`,
added by `08e8ff8` in **phase 20** and unrelated to redaction measurement. No implementation of this
plan can make that grep return zero without deleting an unrelated test.

The invariant it means was verified directly: `git status --porcelain src/` shows only the files this
plan's `files_modified` names; `ResidualProbe.java` and `ShippedAuthCapture.java` exist only in the
session scratchpad and appear in no commit; and this SUMMARY carries the probe's full source, so the
measurement stays reproducible without living in `src/`.

### 4. [Rule 3 — blocking] detekt `LoopWithTooManyJumpStatements` in the scope tripwire

`declarationBlockOf` used a loop with two `break`s. Rewritten as `takeWhile`, which reads better and
says the same thing. Suite re-run after the change (3/3 still green) rather than assumed unaffected.
**Commit:** `87751a8`

### 5. [Design, recorded] The `addTool` set is pinned by path and count, not by `file:line`

The plan's `must_haves` name the three sites as `McpTool.kt:34`, `McpTool.kt:72` and
`McpToolHandlers.kt:122`. Pinning line numbers in a test makes it fail on any unrelated edit above line
34, and `CookieHeaderRuleOwnershipTest` states the opposite discipline in its own source: *"Keyed on
path, not line number, so the map does not rot the first time a file is reformatted."*

`EXPECTED_ADD_TOOL_SITES` is therefore `{McpTool.kt: 2, McpToolHandlers.kt: 1}` — the same hit set at
file granularity, three hits over two files. The measured line numbers are recorded in this SUMMARY and
in the constant's own comment, where they cost nothing to keep accurate. Red probe 3 confirms the
path+count pin still fails when a path is dropped.

---

**Total deviations:** 1 scope strengthening (with its own red probe), 2 acceptance criteria falsified
by measurement and recorded rather than worked around, 1 mechanical lint fix, 1 recorded design refinement.
**Impact:** the plan's objective is fully met. Nothing was reworded to manufacture a pass.

---

## Verification Against `must_haves`

| Truth | Verdict | Evidence |
|---|---|---|
| Emission-site set established BY MEASUREMENT and PINNED — 14 total, 7/7, 10 raw-HTTP across 5 names, 4 WebSocket across 2 | MET | `theMeasuredEmissionSiteCountIsPinned` + `everyEmissionToolNameAppearsInBothExecutors`; grep output quoted |
| Structural claim ASSERTED over ALL THREE registration paths, including `McpToolHandlers.kt`'s wrapper-less delegation | MET | `everyToolRegistrationPathReachesTheChokePoint`; `grep -c redactIfNeeded McpToolHandlers.kt` = 0 |
| The tripwire STATES BOTH OF ITS BOUNDS separately, where a reader meets them | MET | class KDoc, `BOUND 1` / `BOUND 2` headings; bound 1 restated accurately per Deviation 1 |
| A credential-bearing auth header value does not survive the serialized shape in STRICT or BALANCED; before-state measured | MET | probe `APIKEY SURVIVES → STRIPPED`; 2 red tests recorded before the edit |
| The boundary is composed into a MEASURED, STATED rule set and no other; `hostHeaderRegex` excluded for a recorded reason | MET | `theBoundaryFragmentsAreComposedIntoTheMeasuredRuleSetAndNoOther` + 2 red probes |
| The `Host:` / `url` residual is MEASURED, quoted, carried to 27-06 as AR-27-04 — recorded, never pinned green | MET | probe source + commands + verdict table above; nothing under `src/` asserts it |
| Every added test is non-vacuous by construction | MET | `theInventoryScanIsNonVacuous`, `theScopeScanIsNonVacuous`; both fail rather than skip, enforce line floors, prove comment stripping live, and assert a positive per scanned symbol |

Scope-claim hygiene: `grep -rn "whole codebase" src/` returns **0**, and no negated form was
substituted — the phrase and its inverse are both absent.

---

## Residuals — recorded, not silently carried

1. **AR-27-04 — `hostHeaderRegex` and `SiteMapEntry.url` under STRICT** (plan 27-06). Measured above
   with a reproducible probe. D-27-13: `anonymizeHost` records into a de-anonymisation map that
   `RedactionHostMapBoundTest` bounds, and the `url` field carries the same host verbatim with no
   `maybeAnonymizeUrl` in front of it, so a header-only fix reads as closed and is not.
2. **The `ParsedRequest` / `ParsedResponse` HEADER-MAP shape still has no line boundary of any kind.**
   Wave 4 measured this and it is unchanged here: that payload contains no escaped newline, so neither
   branch of the composer can fire and `redactIfNeeded` cannot recover a missed header on it.
   `sanitizeHeaders` remains the **only** control for `request_parse` / `response_parse`. Nothing in
   this plan widens `redactIfNeeded`'s reach to that shape, and no claim here should be read as doing so.
3. **Vendor auth headers outside the 16-name alternation** — `X-Shopify-Access-Token`,
   `X-Amz-Security-Token` and their kind are matched by no rule at all. Unchanged by this plan
   (deliberately: D-27-12 changes the boundary, not the names), still open in `CONCERNS.md`, and now
   stated in `Redaction.kt`'s own comment above the rule so a reader meets it there.
4. **D-27-15 over-match, inherited along with the fix.** `authHeaderRegex` now carries the composer's
   cost too: a raw value containing a literal backslash-`r`/`n` is indistinguishable from an encoded
   newline, so an auth-class header immediately after one is over-redacted. Fail-safe in direction,
   stated in the rule's comment.
5. **Header-stage cost not re-measured here.** Wave 4 measured the composer's cost directly (557 ms →
   650 ms on a 4,194,327-char single-line STRICT payload) across the whole `Redaction.apply` pipeline
   with two rules changed; this plan adds a third rule of the same shape. Per T-27-05-07 no separate
   measurement was required, and none was taken — recorded as an absence rather than implied.
6. **The choke-point test's registration bound is genuinely weaker than the emission scan's.** A future
   `McpToolHandlers` handler that KEPT the `executeToolResult` call and ALSO emitted its own serialized
   payload beside it would pass `everyToolRegistrationPathReachesTheChokePoint`. Stated in the class
   KDoc as its own paragraph, not merged into the stronger claim.

## Known Stubs

None. No hardcoded empty value, placeholder string, `TODO` or `FIXME` was introduced. Every new test
asserts against a real `Redaction.apply` / `redactIfNeeded` call or a real repository-state scan, and
no test was skipped or disabled.

## Threat Flags

None. No new network endpoint, auth path, file access pattern or schema change. The change is one regex
recomposition plus tests; the threat surface is narrowed, not widened.

---

## Next

Ready for **27-06**, which should record:
- **AR-27-04** with the probe source and verdict table above — the measurement is reproducible, not a
  quoted line to take on trust.
- **Residual 2** (the header-map shape has no line boundary, so `sanitizeHeaders` is the sole control
  for `request_parse` / `response_parse`) — it narrows what AR-27-01 "closure" means, and wave 4 also
  recommended it.
- The three ledger entries this plan appended to `.planning/WINDOWS.md`.

## Self-Check: PASSED

- `key-files.created` and `key-files.modified` all present on disk: `SerializedEmissionSiteInventoryTest.kt`,
  `LogicalLineBoundaryScopeTest.kt`, `Redaction.kt`, `SerializedEmissionRedactionTest.kt`, this SUMMARY.
- All three commits resolve in `git log --oneline --all`: `6a54bbf`, `b1b70e9`, `87751a8`.
- Both throwaway harnesses (`ResidualProbe.java`, `ShippedAuthCapture.java`) live only in the session
  scratchpad; `git status --porcelain src/` is clean and neither appears in any commit.
- Every task `<acceptance_criteria>` re-run at close-out. All pass except task 1's red probe 2 and task
  3's `Probe`-path criterion, both unsatisfiable as written and recorded as falsified in Deviations 2
  and 3 rather than worked around.
- Plan-level `<verification>` re-run: full gate green (1184 tests, 0 failures, 1 pre-existing skip);
  `RedactionTest` diff empty; six red probes recorded with their observed failures quoted; the residual
  probe's source, commands and before/after output all present above.
- `STATE.md` and `ROADMAP.md` NOT modified — the orchestrator owns those writes.
