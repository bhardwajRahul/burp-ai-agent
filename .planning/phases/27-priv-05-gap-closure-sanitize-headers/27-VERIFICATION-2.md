---
phase: 27-priv-05-gap-closure-sanitize-headers
verified: 2026-08-24T22:40:00Z
status: gaps_found
score: 8/9 must-haves verified
behavior_unverified: 0
overrides_applied: 0
re_verification:
  previous_status: gaps_found
  previous_score: 7/9
  gaps_closed:
    - "Cookie values reach an AI backend verbatim through the raw-message-in-JSON emission path (14 sites) — CLOSED and independently re-measured against the shipped compiled classes"
    - "AR-27-01 recorded as an accepted residual while it was a live leak — RECLASSIFIED, and the green test that pinned the leak is inverted"
  gaps_remaining:
    - "PRIV-05's `by any path` wording is still not true — a THIRD carrier of the same cookie values (COOKIE-typed HTTP parameters) has no cookie control on the MCP tool path"
  regressions: []
gaps:
  - truth: "PRIV-05's \"by any path\" wording is true — cookie values do not reach an AI backend in STRICT or BALANCED by any path"
    status: failed
    reason: >-
      A THIRD carrier of the same cookie values was never enumerated: COOKIE-typed HTTP parameters.
      Burp parses the `Cookie:` header into `HttpParameterType.COOKIE` parameters, and
      `HttpRequest.parameters()` returns them — a fact this repository already states in its own
      source (`Redaction.kt:628-629`: "a COOKIE-typed parameter line — the second entry point of the
      same leak, reached through request.parameters()") and already relies on
      (`InjectionPointExtractor.kt:29` filters `it.type().name == "COOKIE"` off that same call).
      Two live MCP tools emit those parameter VALUES with no cookie control in front of them and no
      rule behind them.
      `request_parse` is the sharpest case: the SAME tool result whose `headers` map was just
      cookie-stripped by `sanitizeHeaders` hands the identical cookie values straight back in its
      `parameters` array. The control is defeated on its own output, in the same JSON object.
      `params_extract` has no cookie control at all and emits `type=COOKIE name=<n> value=<v>` per
      line.
      `Redaction.cookieTypedParamRegex` — the rule that exists precisely for this leak class (SC2) —
      is `(?im)^([^=\r\n]+)=(.*?)(\s\(COOKIE\))\s*$`, keyed to the passive scanner's
      `formatParamLine` shape `name=value (COOKIE)` (`PassiveAiScannerPrompts.kt:38`). Neither MCP
      shape matches it, so `redactIfNeeded` removes nothing.
      MEASURED against the shipped compiled `Redaction` class (`build/classes/kotlin/main`, JDK 21,
      `RedactionPolicy.fromMode`, `recordMapping=false`), sentinels chosen so no other rule can claim
      them. STRICT and BALANCED, both shapes, output byte-identical to input:
        IN  {"parameters":[{"type":"COOKIE","name":"wibble","value":"SENTINEL_PARAM_AAA"}]}
        OUT {"parameters":[{"type":"COOKIE","name":"wibble","value":"SENTINEL_PARAM_AAA"}]}
        IN  type=COOKIE name=JSESSIONID value=SENTINEL_PX_CCC
        OUT type=COOKIE name=JSESSIONID value=SENTINEL_PX_CCC
      Control on the same run, proving the probe was not vacuous:
        IN  wibble=SENTINEL_PX_DDD (COOKIE)      OUT wibble=[REDACTED] (COOKIE)
        IN  === COOKIES ===\nwibble=SENTINEL_PX_FFF   OUT === COOKIES ===\nwibble=[REDACTED]
      This is the same defect class the phase exists to close, one field further in: a control
      verified at the site it was written for, never compared against the sibling that carries the
      same data — here the sibling sits inside the same serialized object.
    artifacts:
      - path: "src/main/kotlin/com/six2dez/burp/aiagent/mcp/tools/McpToolExecutorImpl.kt:371-373"
        issue: "`request_parse` emits `ParsedParam(type, name, value = param.value())` for every parameter, COOKIE-typed included, two lines after `headers = sanitizeHeaders(request.headers(), context)` at :369"
      - path: "src/main/kotlin/com/six2dez/burp/aiagent/mcp/tools/McpToolLegacy.kt:181-183"
        issue: "the legacy executor carries the identical `request_parse` emission, two lines after its own `sanitizeHeaders` call at :179"
      - path: "src/main/kotlin/com/six2dez/burp/aiagent/mcp/tools/McpToolExecutorImpl.kt:353-355"
        issue: "`params_extract` emits `\"type=${param.type()} name=${param.name()} value=${param.value()}\"` — no sanitizer, no matching redaction rule"
      - path: "src/main/kotlin/com/six2dez/burp/aiagent/mcp/tools/McpToolLegacy.kt:155-157"
        issue: "the legacy executor's copy of `params_extract`, identical"
      - path: "src/main/kotlin/com/six2dez/burp/aiagent/mcp/tools/McpToolModels.kt:133-137"
        issue: "`ParsedParam.value` is a plain `String` with no privacy treatment at any producer"
      - path: "src/main/kotlin/com/six2dez/burp/aiagent/redact/Redaction.kt:628-640"
        issue: "`cookieTypedParamRegex` is the rule for exactly this leak class but is shape-bound to `name=value (TYPE)`; its own KDoc calls the rule CONTEXT-FREE, which is true only within the prompt path's formatting"
      - path: "src/main/kotlin/com/six2dez/burp/aiagent/prompts/bountyprompt/BountyPromptTagResolver.kt:113-134"
        issue: "LATENT, not live — `buildRequestParameters` bypasses `Redaction.apply` entirely (unlike every sibling tag at :86-106) and name-filters with a local `sensitiveParamName` regex that does not match e.g. `PHPSESSID`. The class is not instantiated anywhere in `src/main/kotlin` today, so it is dead code, but T-26-02-01 classified this file at `:144,150` only and never examined `:119`"
    missing:
      - "A cookie control on the parameter carrier: strip/redact the VALUE of any parameter whose `type()` is COOKIE at the two `request_parse` producers and the two `params_extract` producers, OR a `Redaction` rule that reaches both MCP parameter shapes, OR"
      - "An explicitly recorded, human-decided accepted residual naming these four sites, so PRIV-05's `by any path` wording is scoped down rather than left overstated for a third time"
      - "A red probe asserting a COOKIE-typed parameter sentinel does not survive the `request_parse` serialized shape — the mirror of `SerializedEmissionRedactionTest`, whose declared scope is the raw-message carrier and the cookie-HEADER class only"
      - "Extend `SerializedEmissionSiteInventoryTest`'s inventory idea to the parameter carrier, or state in its KDoc that a cookie value also leaves via `request.parameters()` and is outside its measured call shape"
deferred: []
human_verification:
  - test: >-
      Confirm whether Burp's `HttpRequest.parameters()` returns COOKIE-typed parameters for a request
      built by `HttpRequest.httpRequest(rawContent)` inside a live Burp process, and that
      `request_parse` / `params_extract` therefore emit real cookie values. Load the fat JAR, set
      Privacy to STRICT, and call `params_extract` (then `request_parse`) with a raw request carrying
      `Cookie: wibble=SENTINEL_ABC`.
    expected: >-
      Expected TODAY (the defect reproduces): the tool result contains
      `type=COOKIE name=wibble value=SENTINEL_ABC`, and `request_parse`'s JSON shows
      `"headers":{"Cookie":"[STRIPPED]"}` beside `{"type":"COOKIE","name":"wibble","value":"SENTINEL_ABC"}`.
      After a fix: no cookie value in either field.
    why_human: >-
      The redaction half is measured decisively against the shipped compiled classes. The Montoya
      half — that `parameters()` yields COOKIE entries in-process — is established from the API
      surface (`HttpParameterType.COOKIE`, `parameters(HttpParameterType)`) and from two independent
      in-repo statements that rely on it, but has not been executed inside a live Burp.
  - test: >-
      Decide the disposition of the parameter-carrier leak: fix the four sites, or accept a scoped
      residual. Note the mitigating property, which is a scope question rather than a technical one:
      `request_parse` / `params_extract` take CALLER-SUPPLIED content, so the cookie is already in
      the caller's possession and the tool ECHOES it rather than exfiltrating Burp-held data. The
      original PRIV-05 finding that created this phase had exactly the same property and was treated
      as a blocker, and the echoed value is additionally written into the transcript and audit
      surfaces for which `redactIfNeeded` is the sole control.
    expected: >-
      Either a follow-up phase closing the four sites, or a recorded accepted residual with human
      sign-off that narrows PRIV-05's "by any path" wording to the carriers actually covered — plus
      the corresponding note against `26-SECURITY.md` T-26-02-01 and the milestone-audit correction.
    why_human: "A scope/risk decision on a shipped 1.0.0 release, and a possible amendment to a stated requirement."
  - test: >-
      CARRIED FORWARD, still pending from `27-HUMAN-UAT.md` test 2. Live-Burp confirmation that the
      wave-4/5 fix holds end to end: STRICT, browse a cookie-setting site through the proxy, then call
      `proxy_http_history`, `site_map` and `scanner_issues` from a real MCP client.
    expected: "Neither `SENTINEL_ABC` nor `SENTINEL_SET` appears in any tool result, in STRICT or BALANCED. Cookie NAMES may remain."
    why_human: >-
      Needs a live Burp instance, real proxy traffic and a real MCP client. The static and
      compiled-class evidence is decisive and 24 behavioural tests build their fixtures from the real
      serializers, but the end-to-end path has still never been exercised.
  - test: >-
      CARRIED FORWARD. Action or formally defer the `T-27-06-06` backlog item: `README.md:247` and
      `SPEC.md:80,86` still state STRICT host anonymisation without the exclusion AR-27-04 records.
    expected: "The two files state that host anonymisation does not apply to the raw HTTP message or the `url` field emitted by the five raw-HTTP tools."
    why_human: >-
      A user-facing documentation change to what ships; plan 27-06 scoped itself to record files and
      recorded the item as deliberately unactioned. There is no `.planning/BACKLOG.md` in this repo,
      so the item lives only inside `26-SECURITY.md` and `27-06-SUMMARY.md`.
---

# Phase 27: PRIV-05 Gap Closure — Verification Report #2 (re-verification after waves 4-6)

**Phase Goal (ROADMAP.md):** "Close gap: PRIV-05 … **Closing this makes PRIV-05's 'by any path'
wording true** and reopens `26-SECURITY.md` T-26-02-01."

**Verified:** 2026-08-24T22:40:00Z
**Status:** gaps_found
**Re-verification:** Yes — after gap closure (waves 4, 5, 6). Previous: `gaps_found`, 7/9.

---

## Headline

**Both gaps from report #1 are genuinely closed.** I re-measured them myself against the shipped
compiled classes rather than reading the SUMMARYs, and the raw-message-in-JSON leak is gone in both
redacting modes, for canonical names, all five variants, and the auth-header class. The tripwires
waves 5 and 6 added are real mechanisms — I confirmed that by replaying their exact scan predicates
against mutated copies of the source, not by trusting their names. The records are the most carefully
scoped in this repository and I could not find a clause wider than its evidence.

**And PRIV-05's "by any path" wording is still not true.** For a third time, the same shape of miss:
the phase enumerated *carriers of a raw HTTP message* and fixed every one of them, and never asked
which other fields of the same tool results carry the same cookie bytes. They do. Burp parses
`Cookie:` into COOKIE-typed **parameters**, and two live MCP tools emit those parameter VALUES with
no control in front and no rule behind.

The sharpest form of it is in the tool this phase was created for. `request_parse`
(`McpToolExecutorImpl.kt:365-376`) does this, three lines apart, in one JSON object:

```kotlin
headers    = sanitizeHeaders(request.headers(), context),        // :369  Cookie -> [STRIPPED]
parameters = request.parameters().map { param ->                 // :371
                 ParsedParam(type = param.type().name, name = param.name(), value = param.value())
             },                                                  // :372  Cookie value, verbatim
```

The control is defeated on its own output. `params_extract` (`:353-355`) has no control at all.

---

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | **PRIV-05's "by any path" wording is now true** | **FAILED** | COOKIE-typed parameter values reach the tool result verbatim in STRICT and BALANCED via `request_parse` and `params_extract`, in both executors. Measured against the shipped compiled `Redaction` class — see Behavioural Spot-Checks |
| 2 | Cookie values do not survive the SERIALIZED RAW-MESSAGE path (14 sites) in STRICT or BALANCED | VERIFIED | Independently re-probed: canonical `Cookie:`/`Set-Cookie:` and `Cookie2` / `X-Cookie` / `Set-Cookie2` / `X-Original-Cookie` / `X-Forwarded-Cookie` all `[STRIPPED]` in STRICT and BALANCED on the JSON-escaped shape; all pass through under OFF. `SerializedEmissionRedactionTest` 24/24 green |
| 3 | The credential-bearing auth-header class is closed on the same shape, with the 16-name alternation byte-identical | VERIFIED | Same probe: `X-API-Key: sentinel…` → `[REDACTED]`, `Authorization: Bearer …` → `[REDACTED]`, OFF untouched. `git diff` on the alternation lines carries no name change |
| 4 | The header-map residual (AR-27-05) is honestly bounded — all four `ParsedRequest`/`ParsedResponse` sites pass through `sanitizeHeaders` | VERIFIED | `grep -rn "ParsedRequest\|ParsedResponse" src/main/kotlin` returns exactly 4 construction sites (`McpToolExecutorImpl.kt:365,385`, `McpToolLegacy.kt:175,199`); each carries `headers = sanitizeHeaders(…)` at `:369`, `:387`, `:179`, `:201`. Re-read at source, not from any SUMMARY. **The bound is correct for the HEADERS field — it is silent about the `parameters` field beside it, which is gap 1** |
| 5 | `AR-27-01`, `AR-27-02`, `AR-27-04`, `AR-27-05` are each recorded at the RIGHT scope | VERIFIED | AR-27-02's measurement reproduced byte-for-byte on my own probe (table below): `{"X-API-Key":…}` redacted, `{"Cookie":…}` and `{"X-Cookie":…}` not, `{"session":…}` redacted — so `cookie`'s absence from `SENSITIVE_WORDS` (`Redaction.kt:663-664`) is exactly why the cookie class has no JSON-key backstop. Scope "superseded on the raw-message shape only, still load-bearing on the header-map shape" is precisely right |
| 6 | The new tripwires are mechanisms, not decoration, and state their bounds where a reader meets them | VERIFIED | Scan predicates replayed against mutated copies (repo untouched) — every mutation goes RED. Bounds stated in each class KDoc, including the deliberately WEAKER registration-path bound. See Tripwire Falsification |
| 7 | Record accuracy clause by clause; both prior narratives survive verbatim; edits append-only where required | VERIFIED (warning) | All ten clause-(4) line citations in T-26-02-01 verify exactly. `threats_open` recomputed by the documented command: `0` over 46 rows, 46 closed. Milestone audit and `REQUIREMENTS.md`: **zero** removed lines under the robust diff form. Warning: clause (3)'s citations (`Redaction.kt:158`, `:91`, `:107-113`) now point into comments |
| 8 | Wave 6's checkpoint provenance cannot be misread as a human decision, and its cost is an unactioned backlog item | VERIFIED | `26-SECURITY.md`: "**AUTO-SELECTED BY THE CONFIGURED RUN MODE, NOT MAINTAINER-CHOSEN**" and "A future auditor should read this row as a recorded default, not as a human having weighed the release posture." `T-27-06-06` names `README.md:247` and `SPEC.md:80,86` and states "**Until that edit lands, this residual is accepted AND the documentation still overclaims**" |
| 9 | `Host:` / `SiteMapEntry.url` are recorded as an open finding with a re-runnable probe, and no record implies host anonymisation is complete | VERIFIED (warning) | AR-27-04 probe reproduced: `Host: shop.example` and the `url` field both survive STRICT on the serialized shape. Every planning record scopes it correctly. Warning: `README.md:247` and `SPEC.md:80,86` — the records a *user* reads — still do imply completeness, which the record itself says and flags as unactioned |

**Score:** 8/9 truths verified (0 present, behavior-unverified)

---

### Behavioural Spot-Checks

Run against the **shipped compiled classes** (`build/classes/kotlin/main`, compiled 22:58 from source
last written 22:35), JDK 21, calling the real `Redaction.INSTANCE.apply(...)` with
`RedactionPolicy.fromMode(...)`, salt `probe-salt`, `recordMapping=false`. Sentinels are bare
lowercase/underscored words carrying no `SENSITIVE_KEY_EXPR` token, no `=` inside the value, no
`Bearer`/`Basic`/`eyJ` shape and no `=== COOKIES ===` section, so only the intended rule can claim
them.

#### A. What waves 4 and 5 closed — re-measured, not read

| # | Shape | STRICT | BALANCED | Status |
|---|-------|--------|----------|--------|
| P3 | `{"request":"…\r\nCookie: wibble=SENTINEL_RAW_CCC\r\n…","response":"…\r\nSet-Cookie: wobble=SENTINEL_RAW_DDD…"}` | both `[STRIPPED]` | both `[STRIPPED]` | ✓ PASS |
| P4 | `…\r\nX-Cookie: SENTINEL_RAW_EEE\r\n…` | `[STRIPPED]` | `[STRIPPED]` | ✓ PASS |
| — | `Cookie2` / `X-Forwarded-Cookie` / `Set-Cookie2` / `X-Original-Cookie` on the same shape | all `[STRIPPED]` | all `[STRIPPED]` | ✓ PASS |
| — | `X-API-Key: sentinelapikeyvalue` / `Authorization: Bearer …` on the same shape | both `[REDACTED]` | both `[REDACTED]` | ✓ PASS |
| — | Same payload under `OFF` | every sentinel intact | — | ✓ PASS (policy respected) |

Observed, STRICT (identical under BALANCED):

```
{"request":"GET /a HTTP/1.1\r\nX-API-Key: [REDACTED]\r\nAuthorization: [REDACTED]\r\nCookie2: [STRIPPED]\r\nX-Forwarded-Cookie: [STRIPPED]\r\nSet-Cookie2: [STRIPPED]\r\nX-Original-Cookie: [STRIPPED]\r\n\r\n"}
```

**The prior report's gap 1 is genuinely closed on the carrier it named.**

#### B. The parameter carrier — the new gap

| # | Shape | STRICT | BALANCED | Status |
|---|-------|--------|----------|--------|
| P1 | `request_parse` JSON: `{"headers":{"Cookie":"[STRIPPED]"},"parameters":[{"type":"COOKIE","name":"wibble","value":"SENTINEL_PARAM_AAA"}]}` | **SURVIVES** | **SURVIVES** | ✗ FAIL |
| P2 | same with `"name":"session"` | **SURVIVES** | **SURVIVES** | ✗ FAIL |
| Q1 | `params_extract` lines: `type=COOKIE name=wibble value=SENTINEL_PX_AAA` | **SURVIVES** | **SURVIVES** | ✗ FAIL |
| Q2 | `type=COOKIE name=JSESSIONID value=SENTINEL_PX_CCC` | **SURVIVES** | **SURVIVES** | ✗ FAIL |
| Q3 | **control** — prompt-path shape `wibble=SENTINEL_PX_DDD (COOKIE)` | `wibble=[REDACTED] (COOKIE)` | same | ✓ PASS |
| Q5 | **control** — `=== COOKIES ===\nwibble=SENTINEL_PX_FFF` | `wibble=[REDACTED]` | same | ✓ PASS |

Q3 and Q5 are what make B a measurement rather than an inference: the SC1 and SC2 cookie rules fired
on the same run, on the same policy objects, so the four failures above are the rules not reaching
those shapes — not the probe misconfigured.

#### C. AR-27-02 / AR-27-05 attribution, reproduced (STRICT, bare JSON pairs)

| Input | Output |
|-------|--------|
| `{"X-API-Key":"probesentinelvalue"}` | `{"X-API-Key":"[REDACTED]"}` |
| `{"X-Cookie":"probesentinelvalue"}` | `{"X-Cookie":"probesentinelvalue"}` |
| `{"Cookie":"probesentinelvalue"}` | `{"Cookie":"probesentinelvalue"}` |
| `{"Host":"shop.example"}` | `{"Host":"shop.example"}` |
| `{"session":"probesentinelvalue"}` | `{"session":"[REDACTED]"}` |

Matches `26-SECURITY.md`'s quoted probe output exactly. The auth class has a JSON-key backstop on the
header-map shape; the cookie class has none. AR-27-02's narrowed scope is correct.

#### D. One additional stated-bound observation (low, no action proposed)

| Shape | STRICT | Note |
|-------|--------|------|
| `{"request":"Cookie: wibble=SENTINEL_FIRST_GGG\r\nHost: x\r\n\r\n"}` | **SURVIVES** | The escaped branch's start boundary is a lookbehind on `\\[rn]`, so a header at the very START of the JSON string has no boundary in front of it |
| `{"payload":"Cookie: wibble=SENTINEL_WS_HHH",…}` | **SURVIVES** | Same cause, on the WebSocket carrier |

Not a live leak on the raw-HTTP carriers: `request().toString()` / `response().toString()` always
begin with a start line, so no header is ever first. The WebSocket carrier is correctly excluded by
`SerializedEmissionSiteInventoryTest` ("no header block and therefore no header rule to miss"). Worth
one sentence in `Redaction.kt`'s D-27-15 bound paragraph, which today states the over-match cost but
not this under-match edge.

---

### Named-Test Run

`./gradlew test --tests '*SerializedEmission*' --tests '*LogicalLineBoundaryScopeTest' --tests
'*CookieHeader*'` → `BUILD SUCCESSFUL in 3s`.

| Test class | tests | skipped | failures | errors |
|------------|-------|---------|----------|--------|
| `SerializedEmissionRedactionTest` (+ 5 nested) | **24** | 0 | 0 | 0 |
| `SerializedEmissionSiteInventoryTest` | 5 | 0 | 0 | 0 |
| `LogicalLineBoundaryScopeTest` | 3 | 0 | 0 | 0 |
| `CookieHeaderNameParityTest` | 3 | 0 | 0 | 0 |
| `CookieHeaderRuleOwnershipTest` | 3 | 0 | 0 | 0 |

The 24 confirms the record's count (17 from 27-04 + 7 from 27-05) by class name, not by SUMMARY
assertion. `src/` was byte-unmodified before and after (`git status --porcelain -- src/` empty).

---

### Tripwire Falsification (focus 4)

Source mutation of the repository was refused by the sandbox, so instead I re-implemented each
tripwire's **exact scan predicate** — `declarationBlockOf` by name and indentation, comment
stripping, the `encodeToString\(it\.(toSerializableForm|toSiteMapEntry)\(` regex, the
non-executor-file filter — and ran it over mutated **in-memory copies**. The repository was never
written to.

| Mutation | Scan result | Verdict |
|----------|-------------|---------|
| Baseline: `cookieHeaderRegex` / `setCookieHeaderRegex` / `authHeaderRegex` composer-calls | 1 / 1 / 1 | matches the pinned set |
| Baseline: `hostHeaderRegex` composer-calls | 0 | matches the pinned exclusion |
| **A** — route `hostHeaderRegex` through `logicalLineHeaderRule` | 1 | `assertEquals(0, …)` → **RED**. Mechanism |
| **B** — `setCookieHeaderRegex` stops using the composer | 0 | `assertEquals(1, …)` → **RED**. Mechanism |
| Baseline: emission-shaped lines outside the two executors | `[]` | matches the pinned inventory |
| **C** — add an emission-shaped line to `Serialization.kt` (a third file) | detected | stray assertion → **RED**. Mechanism |

Independently, my own whole-tree grep returns exactly **14** emission sites at exactly the 14 pinned
line numbers, and exactly **3** `addTool(` sites at `McpTool.kt:34`, `McpTool.kt:72`,
`McpToolHandlers.kt:122`. Both counts match the pins.

**Both files state their bounds honestly, including the awkward ones.**
`SerializedEmissionSiteInventoryTest`'s KDoc separates BOUND 1 (one measured call shape, two named
files) from BOUND 2 (registration reachability, explicitly "a DIFFERENT AND WEAKER BOUND", and it
names the exact bypass that would still pass). `LogicalLineBoundaryScopeTest` says it is "a tripwire
over FOUR NAMED RULES in ONE NAMED FILE" and that it "says nothing about whether the boundary is the
RIGHT one". Neither is decoration and neither overstates itself.

**What neither can see, and neither claims to:** a cookie value that leaves on a carrier that is not
a raw HTTP message. That is gap 1.

---

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `redact/Redaction.kt` | three named fragments + one composer, three rules composed, `hostHeaderRegex` excluded | VERIFIED | `:206`, `:210`, `:218-219`, `:236-240`, `:242-246`, `:247-248`, `:97-105`, `:1810`. Names untouched: `COOKIE_NAME_PART:119`, `COOKIE_NAME_TOKEN:125`, `isCookieHeaderName:293` |
| `mcp/schema/Serialization.kt` | unchanged carrier shapes | VERIFIED | `toSerializableForm` ×3, `toSiteMapEntry`; `SiteMapEntry.url` still `req?.url()` verbatim (AR-27-04, as recorded) |
| `mcp/tools/McpToolExecutorImpl.kt` | 7 emission sites, 4 header sites through `sanitizeHeaders`, one choke point | VERIFIED | 608/740/760/836/855/873/896; `:369`, `:387`; `context.redactIfNeeded(output)` at `:1037`, after every emission branch |
| `mcp/tools/McpToolLegacy.kt` | same seven + two, no local redaction | VERIFIED | 475/622/639/713/729/744/764; `:179`, `:201`; zero `redactIfNeeded` occurrences in the file |
| `SerializedEmissionRedactionTest.kt` | 24 behavioural tests over the REAL serializer types | VERIFIED | 24/24 green; fixtures built from `HttpRequestResponse`/`SiteMapEntry`/`IssueDetails` + `toolJson`; sentinel distinctness and substring-collision asserted in Kotlin |
| `SerializedEmissionSiteInventoryTest.kt` | 14-site + 3-path pin, non-vacuous, bounds stated | VERIFIED | 5/5 green; falsified above |
| `LogicalLineBoundaryScopeTest.kt` | 3 composed + 1 excluded, rationale-region assertion | VERIFIED | 3/3 green; falsified above |
| `McpToolHelpersTest.kt` | the AR-27-01 pin INVERTED, no green test asserting a cookie survives | VERIFIED | `:230-236` now `assertFalse(contains("sentinelxrayninezulu"))` on the raw-message shape, with a `bearerRegex` non-vacuity guard preserved |
| `26-SECURITY.md` | clause (4), AR-27-01/02 rescoped, AR-27-04/05 defined, computed counter, 4th trail row | VERIFIED | Every citation re-read; counter recomputed = 0; clauses (1)(2)(3) byte-prefix preserved |
| `CONCERNS.md` | second W-A amendment, four parts | VERIFIED | Line 66; 2 removed lines under the robust diff — a genuine bullet replacement, which ledger entry 18 records rather than hides |
| `v0.10.0-MILESTONE-AUDIT.md` | append-only correction qualifying the closure heading | VERIFIED | `:246-…`; **zero** removed lines under the robust diff form |
| `REQUIREMENTS.md` | untouched (prohibition) | VERIFIED | zero removed lines, zero added |

---

### Data-Flow Trace (Level 4)

| Field | Source | Reaches a backend | Control | Status |
|-------|--------|-------------------|---------|--------|
| `HttpRequestResponse.request` / `.response` | `request()?.toString()` | yes, 10 sites | both cookie rules + `authHeaderRegex` via the escaped branch | ✓ FLOWING, controlled |
| `SiteMapEntry.request` / `.response` | `toSiteMapEntry()` | yes | same | ✓ FLOWING, controlled |
| `IssueDetails.requestResponses[]` | `HttpRequestResponse.toSerializableForm()` | yes | same | ✓ FLOWING, controlled |
| `SiteMapEntry.url` | `req?.url()` | yes | none (STRICT) | ⚠️ AR-27-04, recorded open |
| `Host:` inside the raw message | the raw message | yes | none (STRICT) | ⚠️ AR-27-04, recorded open |
| `ParsedRequest.headers` / `ParsedResponse.headers` | `sanitizeHeaders(...)` | yes, 4 sites | predicate + `[STRIPPED]`, sole control | ✓ FLOWING, controlled (AR-27-05 bound) |
| **`ParsedRequest.parameters[].value`** | `param.value()`, COOKIE type included | **yes, 2 sites** | **none** | **✗ HOLLOW — cookie control present on the sibling field and absent here** |
| **`params_extract` output lines** | `param.value()`, COOKIE type included | **yes, 2 sites** | **none** | **✗ DISCONNECTED — no cookie control anywhere on this path** |
| `WebSocketMessage.payload` | `payload()?.toString()` | yes, 4 sites | none needed (no header block) | ✓ as recorded |
| `CookieEntry.value` | `cookie.value()` | yes | mode-gated to `OFF` | ✓ FLOWING, controlled |
| `HttpItem.request` (chat context) | `ContextCollector.kt:52-53` | yes | `Redaction.apply` on the REAL multi-line message | ✓ FLOWING, controlled |
| Bounty-prompt `HTTP_REQUESTS_PARAMETERS` | `BountyPromptTagResolver.kt:119` | **no — class not instantiated in `src/main/kotlin`** | local name regex only, no `Redaction.apply` | ⚠️ LATENT, dead code |
| `find_reflected` output | `param.name()`/`type()`/count | yes | n/a — no value emitted | ✓ safe |

---

### Key Link Verification

| From | To | Via | Status |
|------|----|-----|--------|
| `logicalLineHeaderRule` | `cookieHeaderRegex`, `setCookieHeaderRegex`, `authHeaderRegex` | one composer, three consumers | WIRED (`:242,247,97`) |
| `toSerializableForm`/`toSiteMapEntry` → `encodeToString` → `redactIfNeeded` | tool result | the flow report #1 found broken | **WIRED — the rule now fires on this shape** |
| `sanitizeHeaders` → `ParsedRequest.headers` | tool result | the phase's original fix | WIRED at all 4 sites |
| `isCookieHeaderName` | `sanitizeHeaders`, `sanitizeHeadersForPrompt`, both regexes | one predicate | WIRED (`McpToolHelpers.kt:336`, `PassiveAiScannerFilters.kt:186`) |
| `McpToolHandlers.registerToolHandler` | `redactIfNeeded` | delegation only, no local wrapper | WIRED, and asserted as delegation-only |
| **`param.value()` (COOKIE) → `ParsedParam.value` / `params_extract` line** | **tool result** | **no cookie control on this link** | **NOT WIRED** |

---

### Requirements Coverage

| Requirement | Description | Status | Evidence |
|-------------|-------------|--------|----------|
| PRIV-05 | "Cookie values do not reach an AI backend in STRICT or BALANCED mode **by any path**" | **BLOCKED** | Closed on the raw-message carrier (10 sites) and on the header-map carrier (4 sites). Open on the parameter carrier (4 sites) |

`REQUIREMENTS.md:23` still carries `- [x] **PRIV-05**`. Phase 27 correctly did not touch it
(prohibition honoured, verified: zero-line diff). The tick is now wrong for the third time and
remains the milestone owner's to re-derive.

---

### Anti-Patterns Found

| File | Pattern | Severity | Impact |
|------|---------|----------|--------|
| — | No `TBD` / `FIXME` / `XXX` / `TODO` / `HACK` / `PLACEHOLDER` in any file this phase touched | — | clean |
| — | No `@Disabled` added; suite skip count unchanged at 1 (`ExternalMcpClientManagerTest`, since phase 16) | — | clean |

---

### Record Accuracy, clause by clause (focus 5)

Every citation in T-26-02-01 clause (4) was resolved against source. **All ten hold exactly:**
`JSON_ESCAPED_NEWLINE:206`, `REAL_LINE_HEADER_VALUE:210`, `JSON_ESCAPED_HEADER_VALUE:218-219`,
`logicalLineHeaderRule:236-240`, `cookieHeaderRegex:242-246`, `setCookieHeaderRegex:247-248`,
`authHeaderRegex:97-105`, `COOKIE_NAME_PART:119`, `COOKIE_NAME_TOKEN:125`, `isCookieHeaderName:293`.
The AR-27-05 citations `McpToolExecutorImpl.kt:369,387` and `McpToolLegacy.kt:179,201` all resolve to
`headers = sanitizeHeaders(…)`. `hostHeaderRegex:1810` and `SENSITIVE_WORDS:663-664` both resolve.

**Both prior narratives survive verbatim.** Clauses (1)(2)(3) of T-26-02-01 are preserved as an exact
byte prefix (the ledger's entry 17 records why a naive removed-line gate could not show this: the
whole row is one physical markdown line). The `## Reopening — 2026-08-24` section is untouched. The
milestone audit's closure note is untouched and the correction is appended beneath it — **zero**
removed lines under the robust diff form. `CONCERNS.md`'s first W-A amendment survives; its 2 removed
lines are the bullet replacement that ledger entry 18 documents, along with the GSD gate defect that
would have hidden it.

**`threats_open` recomputed independently:** the documented `awk` command returns `0`; the register
carries 46 rows, all `closed`; AR-27-04 and AR-27-05 are MEDIUM, below the `high` gate, and are
present in the Accepted Risks Log rather than erased. The counter is honest.

**No clause is wider than its evidence.** Every closure sentence names the serialized emission path,
the cookie-header class and the exact-name auth-header class, and no sentence claims PRIV-05 is
satisfied. That discipline is why gap 1 does not falsify a single sentence of the record — it
falsifies the *goal statement*, which the records deliberately never restated.

**Two accuracy warnings, neither a falsehood:**

1. Clause (3)'s line citations (`Redaction.kt:158`, `:91`, `:107-113`) are now stale — `:158` and
   `:91` land inside comments after wave 4 moved the declarations. Clause (3) is preserved verbatim
   by design and clause (4) supplies current numbers, but nothing tells a reader that clause (3)'s
   numbers have rotted.
2. `26-SECURITY.md` says a committed "green assertion under `src/` that a `Host` value survives
   STRICT is precisely the artifact this register exists to stop producing", and gives that as the
   reason the residual probe was not committed. `McpToolHelpersTest.kt` already carries exactly such
   an assertion — `assertTrue(rawMessageFinalText.contains("api.example.com"), "measured AR-27-04…")`.
   It is well-commented and traceable, so this is a record inconsistency rather than a defect, but
   the two sentences cannot both be the policy.

---

### Checkpoint Provenance (focus 6)

Verified and correct. `26-SECURITY.md` states the AR-27-04 disposition was "**AUTO-SELECTED BY THE
CONFIGURED RUN MODE, NOT MAINTAINER-CHOSEN**", explains that `mode: yolo` auto-selects blocking
checkpoints, and instructs the reader: "A future auditor should read this row as a recorded default,
not as a human having weighed the release posture." `27-06-SUMMARY.md:366-382` repeats it and adds
that all eight sign-off boxes mark **executor** verification, not maintainer confirmation. There is
no reading of this record under which the default passes for a decision.

The cost is captured, not closed: `T-27-06-06`, disposition `transfer`, naming `README.md:247` and
`SPEC.md:80,86` and stating "Until that edit lands, this residual is accepted AND the documentation
still overclaims". I confirmed both files still carry the unqualified claim — `README.md:247`
("STRICT privacy mode anonymizes hosts using real HKDF…") and `SPEC.md:80` (`anonymized
(HKDF/HmacSHA256)`) with its paragraph at `:86`.

**Warning:** this repository has no `.planning/BACKLOG.md`, and the item appears in no ROADMAP entry.
It lives only inside a phase-26 security register and a phase-27 SUMMARY — the two documents a
docs maintainer is least likely to open. Recorded honestly, but easy to lose.

---

### The Failing Path, Traced

```
model / MCP client supplies raw request content
  -> HttpRequest.httpRequest(input.content)
  -> request.headers()      -> sanitizeHeaders(...)   -> "Cookie": "[STRIPPED]"     <- the phase's fix
  -> request.parameters()   -> ParsedParam(type="COOKIE", name, value=param.value())
                                                       -> cookie VALUE, verbatim    <- NO control
  -> toolJson.encodeToString(parsed)
  -> McpToolExecutorImpl.kt:1037  context.redactIfNeeded(output)
       -> Redaction.apply
            cookieHeaderRegex / setCookieHeaderRegex  -> need a `name:` at a logical line start. Miss.
            cookieTypedParamRegex `^n=v (COOKIE)$`    -> needs the prompt path's suffix.       Miss.
            jsonSecretKeyRegex                        -> key is "value"/"name". Miss (AR-27-02).
  -> transcript / audit / the configured AI backend / the external MCP client
```

`params_extract` is the same trace with the `sanitizeHeaders` line deleted.

**Why this is the phase's own defect class.** `Redaction.kt:628-640` already names this leak — "a
COOKIE-typed parameter line — the **second entry point of the same leak**, reached through
`request.parameters()`" — and calls the rule that closes it "CONTEXT-FREE: it survives a section
rename and works wherever the shape appears". It is context-free only within the prompt path's
formatting. The MCP path formats the same data two other ways, and the rule that was written for
exactly this data cannot see either of them. A control was verified at the site it was written for
and never compared against its siblings — for the third time in this requirement's history.

**The mitigating property, stated because it is real and it is a scope question.** Both tools take
CALLER-SUPPLIED content, so the cookie value is already in the caller's possession and the tool
ECHOES it rather than exfiltrating Burp-held data. The original PRIV-05 finding that created this
phase — variant cookie names surviving `request_parse` — had precisely the same property and was
treated as a blocker by the milestone audit. The echoed value is additionally written into the
transcript and audit surfaces for which `redactIfNeeded` is the sole control. I have not made that
call; it is routed to human verification item 2.

---

## Gaps Summary

Waves 4, 5 and 6 did their stated job and did it very well. The fix is real and I re-measured it
myself; the tripwires fire on the mutations they claim to catch; the records are accurate clause by
clause, append-only where required, honest about a checkpoint that was auto-selected rather than
decided, and — remarkably — carry an accurate description of their own overclaiming interval instead
of editing it away. On the carrier report #1 named, PRIV-05 now holds.

The goal statement still does not. The phase enumerated **carriers of a raw HTTP message** with real
rigour — 14 sites, pinned, gated, split by tool name — and never enumerated **fields that carry cookie
bytes**. Burp hands the same cookie values back a second way, as COOKIE-typed parameters, and two
live MCP tools emit them with no control in front and no rule behind. `request_parse` strips the
`Cookie` header and returns the same values three lines later in the same JSON object.

The fix is probably small: strip or redact `param.value()` where `param.type()` is `COOKIE` at four
producers, or give `cookieTypedParamRegex` a sibling that reaches the two MCP shapes. The decision of
whether to fix now, or finally scope PRIV-05's "by any path" wording down to the carriers actually
covered, is the maintainer's — and after three refutations, writing that scope down explicitly may be
the more durable outcome than a fourth closure.

---

_Verified: 2026-08-24T22:40:00Z_
_Verifier: Claude (gsd-verifier) — re-verification #2_

---

## Bookkeeping Warnings (non-blocking, carried forward from report #1 and still unfixed)

- `ROADMAP.md:423` still shows `- [ ] 27-06-PLAN.md`; 27-06 merged at `a1392e6`.
- `ROADMAP.md:397` reads `**Plans:** 5/6 plans executed (3/6 executed)` — two counters, both wrong.
- `ROADMAP.md:442` progress table: `27. … | 5/6 | In Progress |` with no completion date.
- `ROADMAP.md:441` progress table: `26. … | 7/7 | In Progress |` with no completion date.
- `.planning/STATE.md`: `state_head` is `190735d` (the 27-05 merge), `last_activity_desc` reads
  "Phase 26 execution started", `stopped_at` reads "Phase 25 complete, ready to plan Phase 26".
  Stale by one merge and by two phases respectively.
- `.planning/WINDOWS.md`: `open_count: 18`, all `open`. With `workflow.windows_enforce` on, `/gsd-ship`
  blocks until these are waived or fixed. Entries 11-18 are all deliberate, well-argued deviation
  records rather than defects — several (17, 18) are GSD gate defects, not project defects.
