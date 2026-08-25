---
phase: 27-priv-05-gap-closure-sanitize-headers
plan: 08
subsystem: security
tags: [privacy, redaction, montoya, kotlin, cookies, junit5, source-scan, inventory]

requires:
  - phase: 27-priv-05-gap-closure-sanitize-headers
    provides: "Redaction.isCookieParameterType and McpToolHelpers.sanitizeParameters (27-07) — the two named controls this inventory classifies call sites against; CookieHeaderRuleOwnershipTest's classify-or-route registry and SerializedEmissionSiteInventoryTest's BOUND 1 / BOUND 2 discipline as structural templates"
provides:
  - "CookieCarrierInventoryTest — the carrier/accessor inventory: 5 accessors, 72 measured sites, 11 files, classify-or-route with NEW/STALE diagnostics, four blind axes plus a fifth granularity bound named in its KDoc"
  - "A narrowed cookieTypedParamRegex comment block whose documented reach equals the rule's actual reach, with the type-keyed owner cited by name"
  - "Seven prompt-path preservation fixtures in ParameterCarrierRedactionTest, including the RFC 6265 DQUOTE case WINDOWS.md records as having nearly shipped green"
  - "MEASUREMENT 1 — the non-cookie parameter-type result, with a firing attribution control"
  - "MEASUREMENT 2 — the AuditIssue.detail() transitive carrier result (AR-27-08), with a firing positive control and source-cited reachability"
affects: [27-09, priv-05, scanner-issues, active-scanner, mcp-tools]

actuals:
  tokens: 13560
  tasks: 3
  commits: 3

tech-stack:
  added: []
  patterns:
    - "Accessor-keyed inventory: enumerate what RETURNS the sensitive bytes rather than what renders them, so the mechanism is one axis above every rendering-keyed control it audits"
    - "Bound-before-claim: the tripwire states what it cannot see, in its own KDoc, before any reader can quote it as proof"
    - "Measure-with-a-control: a null result is only a measurement when a positive control fired on the same run; otherwise it is an inference and must be labelled one"

key-files:
  created:
    - src/test/kotlin/com/six2dez/burp/aiagent/redact/CookieCarrierInventoryTest.kt
  modified:
    - src/main/kotlin/com/six2dez/burp/aiagent/redact/Redaction.kt
    - src/test/kotlin/com/six2dez/burp/aiagent/mcp/tools/ParameterCarrierRedactionTest.kt

key-decisions:
  - "D-27-22 applied: build the carrier mechanism keyed on the SOURCE accessor, and state its bound in the same breath as its claim"
  - "D-27-19 applied: cookieTypedParamRegex is byte-unchanged; only its comment block was narrowed, and the surviving reasoning was preserved verbatim"
  - "D-27-20 applied: measurement 1 was MEASURED with an attribution control and NOT fixed; SENSITIVE_WORDS is unwidened"
  - "T-27-08-06 kept at TRANSFER, not mitigate: this plan applies no control to the issue-detail route, it only measures it"
  - "Deviation: the prompt-path fixtures are FLAT, not @Nested, because @Nested writes a separate JUnit XML and would make task 1 criterion 2 unsatisfiable — the same conflict 27-07 recorded"
  - "Deviation: the registry is keyed on a (file, accessor) PAIR rather than on a bare path, because 6 of the 11 files have accessors with different dispositions and a path-only key makes assertion 1 meaningless for them"

patterns-established:
  - "Registry granularity is itself a bound: where a key aggregates calls with different dispositions, say so and pin the count so a change forces a re-read"
  - "A provisional registry entry names the task that will measure it and refuses to guess the answer in the meantime"

requirements-completed: []

coverage:
  - id: D1
    description: "cookieTypedParamRegex's comment block no longer claims coverage of request.parameters() that the rule does not have; it names the one rendered shape, its single producer, and the type-keyed owner of the shapes it does not reach"
    requirement: PRIV-05
    verification:
      - kind: command
        ref: "git diff HEAD --unified=0 -- redact/Redaction.kt | grep '^-' | grep -v '^--- ' -> 7 removed lines, every one a `//` comment; the regex source line absent from the removed set"
        status: pass
    human_judgment: false
  - id: D2
    description: "The passive scanner's name=value (COOKIE) behaviour is preserved and pinned by five fixture groups, including an RFC 6265 DQUOTE-wrapped value asserted on the full output string"
    requirement: PRIV-05
    verification:
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/mcp/tools/ParameterCarrierRedactionTest.kt#promptPathCanonicalCookieParamLineIsRedactedUnderStrict"
        status: pass
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/mcp/tools/ParameterCarrierRedactionTest.kt#promptPathDquoteWrappedCookieValueIsRedactedUnderStrict"
        status: pass
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/mcp/tools/ParameterCarrierRedactionTest.kt#promptPathMultiParameterBlockRedactsOnlyTheCookieLineAndPreservesOrder"
        status: pass
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/mcp/tools/ParameterCarrierRedactionTest.kt#promptPathNonCookieTypeLabelIsUntouchedInEveryMode"
        status: pass
    human_judgment: false
  - id: D3
    description: "A mechanism enumerates cookie-byte carriers by their source accessor and fails when a new carrying call site appears unclassified; 72 sites across 11 files, per-file per-accessor counts pinned"
    requirement: PRIV-05
    verification:
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/redact/CookieCarrierInventoryTest.kt#everyCookieByteCarrierSiteIsRoutedOrClassified"
        status: pass
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/redact/CookieCarrierInventoryTest.kt#theMeasuredPerFilePerAccessorCountsArePinned"
        status: pass
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/redact/CookieCarrierInventoryTest.kt#theHeaderValueArgumentMultisetIsPinned"
        status: pass
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/redact/CookieCarrierInventoryTest.kt#theCarrierScanIsNonVacuous"
        status: pass
    human_judgment: false
  - id: D4
    description: "The inventory names four things it cannot see, plus a fifth granularity bound, in its own KDoc before anyone can quote it as proof"
    requirement: PRIV-05
    verification:
      - kind: command
        ref: "KDoc bound paragraph quoted verbatim in this SUMMARY under 'The bound, quoted back'"
        status: pass
    human_judgment: false
  - id: D5
    description: "MEASUREMENT 1 — whether a sensitive-named URL/BODY-typed parameter value survives request_parse's serialized JSON, measured with an attribution control that fired"
    verification:
      - kind: other
        ref: "ResidualProbe (uncommitted, full source in this SUMMARY): M1 SURVIVES under STRICT/BALANCED/OFF; M1-CONTROL REDACTED under STRICT/BALANCED, SURVIVES under OFF"
        status: pass
    human_judgment: true
    rationale: "The measurement is complete and its control fired, but the DISPOSITION — whether to widen SENSITIVE_WORDS against WR-01's measured 32 false positives, or to record it as an accepted residual — is a maintainer judgment that plan 27-09 must file, not a test result."
  - id: D6
    description: "MEASUREMENT 2 — whether a COOKIE-typed injection point's value reaches scanner_issues through AuditIssue.detail(), measured with a positive control that fired on the same payload, plus source-cited reachability"
    verification:
      - kind: other
        ref: "ResidualProbe (uncommitted, full source in this SUMMARY): ISSUE-DETAIL-COOKIE SURVIVES under STRICT/BALANCED/OFF; CONTROL-RAW-COOKIE-HEADER REDACTED under STRICT/BALANCED on the SAME payload"
        status: pass
    human_judgment: true
    rationale: "AR-27-08 is an uncontrolled disclosure route this plan deliberately did not fix (T-27-08-06 is disposition TRANSFER, not mitigate). A human must accept the medium severity and open the named successor plan 27-09 owes it; no test here proves the route is safe, because it is not."
  - id: D7
    description: "The Montoya half of the parameter carrier — that HttpRequest.parameters() yields COOKIE-typed entries inside a live Burp process — carried forward unchanged from 27-VERIFICATION-2 and 27-07"
    verification: []
    human_judgment: true
    rationale: "HttpRequest.httpRequest() is a Montoya static factory requiring Burp's internal ObjectFactory and cannot run in a pure-JVM test. Unchanged by this plan; still routed to 27-HUMAN-UAT.md."

duration: 47min
completed: 2026-08-25
status: complete
---

# Phase 27 Plan 08: The Cookie-Byte Carrier Inventory Summary

**A mechanism that enumerates what CARRIES cookie bytes — 5 Montoya accessors, 72 pinned call sites across 11 files, every one routed through a named control or classified from its own consumer — together with a rule whose documented reach now equals its actual reach, and two neighbouring questions answered by measurement with firing controls rather than by assumption in either direction.**

## What this plan does NOT claim

Stated first, because this phase has produced an over-wide claim three times and the correction is the
deliverable.

- **This plan does not close PRIV-05.** It builds a tripwire and narrows a claim.
- **The inventory is not a proof.** It is a tripwire over a MEASURED ACCESSOR SET. Its own KDoc says so,
  names four things it cannot see, and adds a fifth bound on its own bookkeeping.
- **Measurement 2 found a live, uncontrolled disclosure route and this plan applied no control to it.**
  `T-27-08-06`'s disposition stays **TRANSFER**, not mitigate. Calling a measurement a mitigation is
  the overclaim vocabulary plan 27-09 exists to correct.
- **Nothing here is evidence about a cookie byte that never passes a Montoya accessor.** That is the
  NAMED NEXT BLIND AXIS, quoted verbatim below.

## Performance

- **Duration:** 47 min
- **Started:** 2026-08-25T13:02:00Z
- **Completed:** 2026-08-25T13:49:00Z
- **Tasks:** 3
- **Files modified:** 3 (1 created, 2 modified)

---

# THE TWO MEASURED-NOT-FIXED RESULTS

Recorded verbatim and in full. Plan 27-09 consumes these directly and cannot re-derive them.
Neither is fixed in this plan. Neither probe is committed under `src/`.

## MEASUREMENT 1 — does a sensitive-named NON-COOKIE parameter survive `request_parse`'s JSON shape?

### THE RESULT

**YES. It survives, in STRICT and in BALANCED alike.** A `URL`-typed parameter named `access_token`
and a `BODY`-typed parameter named `password`, each carrying a distinct sentinel VALUE, both reached
the end of `Redaction.apply` byte-for-byte unchanged in all three modes.

**THE ATTRIBUTION CONTROL FIRED.** The identical two names, presented as bare JSON KEYS on the same
run, were both rewritten to `[REDACTED]` under STRICT and BALANCED and both survived under OFF. So the
null result above is attributable to the rule's REACH and not to a misconfigured probe — this is a
MEASUREMENT, not an inference.

**MECHANISM, now measured rather than read off the source:** `jsonSecretKeyRegex` keys on the JSON
KEY. In the `request_parse` shape the key carrying the value is the literal `"value"`, which is not in
`SENSITIVE_WORDS`; the parameter's own sensitive-looking name sits in a sibling `"name"` key where no
rule looks. This is the same mechanism `AR-27-02` records one field over.

**SEVERITY: LOW**, with its reasoning and the mitigating property named.
- **Mitigating, and it is the decisive property:** `request_parse` and `params_extract` parse a raw
  request string supplied BY THE CALLER in the tool arguments. The AI agent already possessed those
  bytes before the tool ran; this is caller-echoed content, not Burp-held traffic.
- **Mitigating:** it is outside PRIV-05's cookie wording entirely (D-27-20).
- **Aggravating:** none new. The fix — widening `SENSITIVE_WORDS` — carries WR-01's measured cost of
  32 false positives across all three consumer regexes at once.
- **DIFFERENCE FROM THE PLAN'S REGISTER, stated rather than silently overridden:** plan 27-08's threat
  table carried `T-27-08-07` at `medium`, assigned at authoring time before this measurement existed.
  The measurement supports **low** on the caller-echo property. Plan 27-09 should record the
  disagreement and pick, rather than inherit either number.

### The probe, verbatim

Payload (276 bytes), exactly as `toolJson.encodeToString` emits `ParsedRequest`:

```
{"method":"GET","path":"/a","url":"http://example.com/a","headers":{"Host":"example.com"},"parameters":[{"type":"URL","name":"access_token","value":"probeurlnoncookiesentinel"},{"type":"BODY","name":"password","value":"probebodynoncookiesentinel"}],"body":null,"bodyLength":0}
```

Verdicts and full output, all three modes:

```
==== M1: request_parse, non-cookie parameter types (276 bytes) ====
carries an escaped newline: false
carries a real newline: false
STRICT    URL-PARAM-VALUE            SURVIVES
STRICT    BODY-PARAM-VALUE           SURVIVES
---- STRICT output ----
{"method":"GET","path":"/a","url":"http://example.com/a","headers":{"Host":"example.com"},"parameters":[{"type":"URL","name":"access_token","value":"probeurlnoncookiesentinel"},{"type":"BODY","name":"password","value":"probebodynoncookiesentinel"}],"body":null,"bodyLength":0}
BALANCED  URL-PARAM-VALUE            SURVIVES
BALANCED  BODY-PARAM-VALUE           SURVIVES
---- BALANCED output ----
{"method":"GET","path":"/a","url":"http://example.com/a","headers":{"Host":"example.com"},"parameters":[{"type":"URL","name":"access_token","value":"probeurlnoncookiesentinel"},{"type":"BODY","name":"password","value":"probebodynoncookiesentinel"}],"body":null,"bodyLength":0}
OFF       URL-PARAM-VALUE            SURVIVES
OFF       BODY-PARAM-VALUE           SURVIVES
---- OFF output ----
{"method":"GET","path":"/a","url":"http://example.com/a","headers":{"Host":"example.com"},"parameters":[{"type":"URL","name":"access_token","value":"probeurlnoncookiesentinel"},{"type":"BODY","name":"password","value":"probebodynoncookiesentinel"}],"body":null,"bodyLength":0}
```

The attribution control, SAME run, SAME classes:

```
==== M1-CONTROL: bare JSON pairs keyed by the sensitive name (81 bytes) ====
carries an escaped newline: false
carries a real newline: false
STRICT    CONTROL-ACCESS_TOKEN-KEY   REDACTED
STRICT    CONTROL-PASSWORD-KEY       REDACTED
---- STRICT output ----
{"access_token":"[REDACTED]","password":"[REDACTED]"}
BALANCED  CONTROL-ACCESS_TOKEN-KEY   REDACTED
BALANCED  CONTROL-PASSWORD-KEY       REDACTED
---- BALANCED output ----
{"access_token":"[REDACTED]","password":"[REDACTED]"}
OFF       CONTROL-ACCESS_TOKEN-KEY   SURVIVES
OFF       CONTROL-PASSWORD-KEY       SURVIVES
---- OFF output ----
{"access_token":"probecontrolkeyedsentinel","password":"probecontrolpwdsentinel"}
```

## MEASUREMENT 2 — does a COOKIE-typed injection point's value reach `scanner_issues` through `AuditIssue.detail()`?

### THE RESULT

**YES. A cookie value embedded in the `Original Value:` detail line SURVIVES `Redaction.apply` on the
serialized `IssueDetails` shape, in STRICT and in BALANCED alike.** It is emitted verbatim.

**THE POSITIVE CONTROL FIRED, ON THE SAME PAYLOAD.** A real `Cookie:` header carried in
`requestResponses[0].request` of the very same `IssueDetails` object became `Cookie: [STRIPPED]` in
the very same STRICT output in which the detail-line sentinel survived. One object, one call, one
output — one field controlled and one not. The null result is therefore attributable to REACH and not
to a broken probe, and the two fields are directly comparable because nothing differs between them
except the shape of the text.

**MECHANISM, measured:**
- `IssueUtils.formatIssueDetailHtml` (`util/IssueUtils.kt:51-63`) joins `detailLines` with `<br>` and
  rewrites a leading two-space indent to `&nbsp;&nbsp;`. The resulting blob contains **no newline at
  all**, so the logical-line cookie header rules — the ones waves 4-6 taught to key on a real line
  boundary — have nothing to bind to.
- The rendered shape is `Original Value: <value>`, not `name=<value> (COOKIE)`, so
  `Redaction.cookieTypedParamRegex` cannot key on it either.
- The enclosing JSON key is `detail`, which is not in `SENSITIVE_WORDS`, so `jsonSecretKeyRegex`
  cannot reach it.

**REACHABILITY, cited at source for every clause — this is the difference between a live leak and a
latent one, and this phase has twice recorded a finding at the wrong severity for want of it:**

| Question | Answer | Cited at |
|---|---|---|
| Is the write privacy-mode gated? | **NO.** The value is written unconditionally. | `scanner/ActiveAiScanner.kt:1239` |
| Is a confirmation required first? | **YES.** `handleResult` calls `confirmFinding` only when `confirmation != null && confirmation.confirmed`. | `scanner/ActiveAiScanner.kt:1172-1176`, `:1183` |
| Which mode must be enabled? | Active AI scanning, which is **opt-in and defaults to `false`**. | `config/AgentSettings.kt:127` (`val activeAiEnabled: Boolean = false`), also `:391`, `:520`; wired at `App.kt:182` |
| Can a COOKIE-typed injection point reach that line? | **YES.** `extractInjectionPoints` returns every point from `InjectionPointExtractor.extract`, and the target loop filters on vuln CLASS only — never on `point.type`. | `scanner/ActiveAiScanner.kt:232-246`, `:1684`; points created at `scanner/InjectionPointExtractor.kt:29` |
| How does it leave the machine? | `Serialization.kt:14` assigns `detail = detail()` into `IssueDetails`, which the `scanner_issues` MCP tool emits. | `mcp/schema/Serialization.kt:14` (baseline C6) |

**SEVERITY: MEDIUM**, with the aggravating and mitigating properties named in the same breath, and
deliberately neither rounded up nor down.
- **Aggravating, and it is strictly worse than measurement 1:** this carries **Burp-held proxied
  traffic** — a real session cookie the operator's browser sent — not caller-echoed content. The AI
  backend did not previously possess these bytes.
- **Aggravating:** it defeats STRICT outright. There is no mode in which this field is protected.
- **Mitigating:** it is **LATENT**, behind three independent preconditions — the opt-in active
  scanner being switched on, a finding reaching `confirmed`, and a `scanner_issues` call being made.
- **Why not high:** it is unreachable in the default posture.
- **Why not low:** when it is reachable, a real session cookie crosses the trust boundary in STRICT.

**Filed as `AR-27-08`** for plan 27-09, which owes it both transfer targets `T-27-08-06` names: the
`26-SECURITY.md` entry at this measured severity, AND a named successor (a ROADMAP phase entry or an
explicit wave 10) that owns the fix. `InjectionPointExtractor.kt:29` is deferred WITH this route and is
closed by the same successor.

### The probe, verbatim

Payload (793 bytes) — the `detail` field is what `formatIssueDetailHtml` produces from
`createConfirmedIssue`'s `detailLines`; `requestResponses[0].request` is the positive control:

```
{"name":"[AI Active] SQLI","detail":"Vulnerability confirmed via active testing<br><br>Type:<br>&nbsp;&nbsp;SQLI<br>&nbsp;&nbsp;Injection Point: COOKIE - PHPSESSID<br>&nbsp;&nbsp;Original Value: probeissuedetailcookiesentinel<br>&nbsp;&nbsp;Payload Used: probeissuedetailcookiesentinel' AND '1'='1<br>&nbsp;&nbsp;Detection Method: ERROR_BASED<br>&nbsp;&nbsp;Evidence: SQL syntax error<br>","remediation":"Use parameterised queries.","httpService":{"host":"shop.example","port":443,"secure":true},"baseUrl":"https://shop.example/basket","severity":"HIGH","confidence":"FIRM","requestResponses":[{"request":"GET /basket HTTP/1.1\r\nHost: shop.example\r\nCookie: PHPSESSID=probeissuecontrolcookiesentinel\r\n\r\n","response":"HTTP/1.1 200 OK\r\n\r\n","notes":null}],"collaboratorInteractions":[]}
```

Verdicts and full output, all three modes:

```
==== M2: scanner_issues IssueDetails, cookie in the detail line (793 bytes) ====
carries an escaped newline: true
carries a real newline: false
STRICT    ISSUE-DETAIL-COOKIE        SURVIVES
STRICT    CONTROL-RAW-COOKIE-HEADER  REDACTED
---- STRICT output ----
{"name":"[AI Active] SQLI","detail":"Vulnerability confirmed via active testing<br><br>Type:<br>&nbsp;&nbsp;SQLI<br>&nbsp;&nbsp;Injection Point: COOKIE - PHPSESSID<br>&nbsp;&nbsp;Original Value: probeissuedetailcookiesentinel<br>&nbsp;&nbsp;Payload Used: probeissuedetailcookiesentinel' AND '1'='1<br>&nbsp;&nbsp;Detection Method: ERROR_BASED<br>&nbsp;&nbsp;Evidence: SQL syntax error<br>","remediation":"Use parameterised queries.","httpService":{"host":"shop.example","port":443,"secure":true},"baseUrl":"https://shop.example/basket","severity":"HIGH","confidence":"FIRM","requestResponses":[{"request":"GET /basket HTTP/1.1\r\nHost: shop.example\r\nCookie: [STRIPPED]\r\n\r\n","response":"HTTP/1.1 200 OK\r\n\r\n","notes":null}],"collaboratorInteractions":[]}
BALANCED  ISSUE-DETAIL-COOKIE        SURVIVES
BALANCED  CONTROL-RAW-COOKIE-HEADER  REDACTED
---- BALANCED output ----
{"name":"[AI Active] SQLI","detail":"Vulnerability confirmed via active testing<br><br>Type:<br>&nbsp;&nbsp;SQLI<br>&nbsp;&nbsp;Injection Point: COOKIE - PHPSESSID<br>&nbsp;&nbsp;Original Value: probeissuedetailcookiesentinel<br>&nbsp;&nbsp;Payload Used: probeissuedetailcookiesentinel' AND '1'='1<br>&nbsp;&nbsp;Detection Method: ERROR_BASED<br>&nbsp;&nbsp;Evidence: SQL syntax error<br>","remediation":"Use parameterised queries.","httpService":{"host":"shop.example","port":443,"secure":true},"baseUrl":"https://shop.example/basket","severity":"HIGH","confidence":"FIRM","requestResponses":[{"request":"GET /basket HTTP/1.1\r\nHost: shop.example\r\nCookie: [STRIPPED]\r\n\r\n","response":"HTTP/1.1 200 OK\r\n\r\n","notes":null}],"collaboratorInteractions":[]}
OFF       ISSUE-DETAIL-COOKIE        SURVIVES
OFF       CONTROL-RAW-COOKIE-HEADER  SURVIVES
---- OFF output ----
{"name":"[AI Active] SQLI","detail":"Vulnerability confirmed via active testing<br><br>Type:<br>&nbsp;&nbsp;SQLI<br>&nbsp;&nbsp;Injection Point: COOKIE - PHPSESSID<br>&nbsp;&nbsp;Original Value: probeissuedetailcookiesentinel<br>&nbsp;&nbsp;Payload Used: probeissuedetailcookiesentinel' AND '1'='1<br>&nbsp;&nbsp;Detection Method: ERROR_BASED<br>&nbsp;&nbsp;Evidence: SQL syntax error<br>","remediation":"Use parameterised queries.","httpService":{"host":"shop.example","port":443,"secure":true},"baseUrl":"https://shop.example/basket","severity":"HIGH","confidence":"FIRM","requestResponses":[{"request":"GET /basket HTTP/1.1\r\nHost: shop.example\r\nCookie: PHPSESSID=probeissuecontrolcookiesentinel\r\n\r\n","response":"HTTP/1.1 200 OK\r\n\r\n","notes":null}],"collaboratorInteractions":[]}
```

Note the `OFF` row of the control: it reads `SURVIVES` there and `REDACTED` under both redacting
modes, which is what proves the control is measuring the policy rather than an unconditional rewrite.

### The probe harness, in full — NOT committed, and why

A green assertion under `src/` that a sensitive value survives STRICT is the artifact `26-SECURITY.md`
exists to stop producing. The probe lives in the session scratchpad; the source is reproduced here so
the measurement stays re-runnable without living in the tree.

```java
// Phase 27 plan 27-08 task 3 — throwaway measurement probe. NOT committed, by design: a green
// assertion under src/ that a sensitive value survives STRICT is the artifact 26-SECURITY.md
// exists to stop producing.
//
// Runs the REAL compiled Redaction.apply (build/classes/kotlin/main) over two shapes this plan
// MEASURES rather than assumes, plus their attribution controls.
//
//   javac -cp build/classes/kotlin/main:<kotlin-stdlib.jar> -d <out> ResidualProbe.java
//   java  -cp build/classes/kotlin/main:<kotlin-stdlib.jar>:<out> ResidualProbe
import com.six2dez.burp.aiagent.redact.PrivacyMode;
import com.six2dez.burp.aiagent.redact.Redaction;
import com.six2dez.burp.aiagent.redact.RedactionPolicy;

public final class ResidualProbe {

    // ── MEASUREMENT 1 (D-27-20 / T-27-08-07) ────────────────────────────────────────────────
    // The request_parse serialized shape, exactly as toolJson.encodeToString emits ParsedRequest:
    // the JSON KEY carrying the parameter value is the literal "value"; the parameter's own
    // sensitive-looking NAME sits in a sibling "name" key. Two non-cookie types, two sentinels.
    private static final String M1_REQUEST_PARSE =
        "{\"method\":\"GET\",\"path\":\"/a\",\"url\":\"http://example.com/a\","
            + "\"headers\":{\"Host\":\"example.com\"},"
            + "\"parameters\":["
            + "{\"type\":\"URL\",\"name\":\"access_token\",\"value\":\"probeurlnoncookiesentinel\"},"
            + "{\"type\":\"BODY\",\"name\":\"password\",\"value\":\"probebodynoncookiesentinel\"}"
            + "],\"body\":null,\"bodyLength\":0}";

    // THE ATTRIBUTION CONTROL for measurement 1, on the SAME run. Identical sensitive names, but
    // here they ARE the JSON keys. If this does not fire, a null result above is a broken probe
    // rather than a statement about reach.
    private static final String M1_CONTROL =
        "{\"access_token\":\"probecontrolkeyedsentinel\",\"password\":\"probecontrolpwdsentinel\"}";

    // ── MEASUREMENT 2 (T-27-08-06) ──────────────────────────────────────────────────────────
    // The scanner_issues serialized shape. `detail` is what IssueUtils.formatIssueDetailHtml
    // produced from ActiveAiScanner.createConfirmedIssue's detailLines: ONE line, joined by
    // <br>, with a leading two-space indent rewritten to &nbsp;&nbsp;. The cookie value arrives
    // there from a COOKIE-typed InjectionPoint.originalValue (InjectionPointExtractor.kt:29).
    //
    // requestResponses[0].request is the POSITIVE CONTROL, on the same payload: a raw HTTP
    // message with a REAL escaped CRLF before its Cookie: header, which is the shape the
    // logical-line cookie rules were taught to see in waves 4-6.
    private static final String M2_ISSUE_DETAILS =
        "{\"name\":\"[AI Active] SQLI\","
            + "\"detail\":\"Vulnerability confirmed via active testing<br><br>Type:<br>"
            + "&nbsp;&nbsp;SQLI<br>"
            + "&nbsp;&nbsp;Injection Point: COOKIE - PHPSESSID<br>"
            + "&nbsp;&nbsp;Original Value: probeissuedetailcookiesentinel<br>"
            + "&nbsp;&nbsp;Payload Used: probeissuedetailcookiesentinel' AND '1'='1<br>"
            + "&nbsp;&nbsp;Detection Method: ERROR_BASED<br>"
            + "&nbsp;&nbsp;Evidence: SQL syntax error<br>\","
            + "\"remediation\":\"Use parameterised queries.\","
            + "\"httpService\":{\"host\":\"shop.example\",\"port\":443,\"secure\":true},"
            + "\"baseUrl\":\"https://shop.example/basket\",\"severity\":\"HIGH\",\"confidence\":\"FIRM\","
            + "\"requestResponses\":[{"
            + "\"request\":\"GET /basket HTTP/1.1\\r\\nHost: shop.example"
            + "\\r\\nCookie: PHPSESSID=probeissuecontrolcookiesentinel\\r\\n\\r\\n\","
            + "\"response\":\"HTTP/1.1 200 OK\\r\\n\\r\\n\",\"notes\":null}],"
            + "\"collaboratorInteractions\":[]}";

    private static final String[][] M1_ARTIFACTS = {
        {"URL-PARAM-VALUE", "probeurlnoncookiesentinel"},
        {"BODY-PARAM-VALUE", "probebodynoncookiesentinel"},
    };

    private static final String[][] M1_CONTROL_ARTIFACTS = {
        {"CONTROL-ACCESS_TOKEN-KEY", "probecontrolkeyedsentinel"},
        {"CONTROL-PASSWORD-KEY", "probecontrolpwdsentinel"},
    };

    private static final String[][] M2_ARTIFACTS = {
        {"ISSUE-DETAIL-COOKIE", "probeissuedetailcookiesentinel"},
        {"CONTROL-RAW-COOKIE-HEADER", "probeissuecontrolcookiesentinel"},
    };

    private static void run(String label, String payload, String[][] artifacts) {
        System.out.println("==== " + label + " (" + payload.length() + " bytes) ====");
        System.out.println("carries an escaped newline: " + payload.contains("\\r\\n"));
        System.out.println("carries a real newline: " + payload.contains("\n"));
        for (PrivacyMode mode : new PrivacyMode[] {PrivacyMode.STRICT, PrivacyMode.BALANCED, PrivacyMode.OFF}) {
            RedactionPolicy policy = RedactionPolicy.Companion.fromMode(mode);
            String out = Redaction.INSTANCE.apply(payload, policy, "probe-salt", false);
            for (String[] artifact : artifacts) {
                boolean present = out.contains(artifact[1]);
                System.out.printf("%-9s %-26s %s%n", mode, artifact[0], present ? "SURVIVES" : "REDACTED");
            }
            System.out.println("---- " + mode + " output ----");
            System.out.println(out);
        }
        System.out.println();
    }

    public static void main(String[] args) {
        run("M1: request_parse, non-cookie parameter types", M1_REQUEST_PARSE, M1_ARTIFACTS);
        run("M1-CONTROL: bare JSON pairs keyed by the sensitive name", M1_CONTROL, M1_CONTROL_ARTIFACTS);
        run("M2: scanner_issues IssueDetails, cookie in the detail line", M2_ISSUE_DETAILS, M2_ARTIFACTS);
    }
}
```

Exact invocation:

```bash
SP=<scratchpad>
KSTD=/Users/six2dez/.gradle/caches/modules-2/files-2.1/org.jetbrains.kotlin/kotlin-stdlib/2.1.21/\
97a0975aa19d925e109537af60eb46902920015c/kotlin-stdlib-2.1.21.jar

/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home/bin/javac \
  -cp build/classes/kotlin/main:$KSTD -d $SP/probeout $SP/ResidualProbe.java

/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home/bin/java \
  -cp build/classes/kotlin/main:$KSTD:$SP/probeout ResidualProbe
```

**Neither probe is committed. Verified BY PATH, not by substring** — `WINDOWS.md` entry 14 records an
acceptance criterion in this phase that was unsatisfiable because a long-standing unrelated file
matched its name pattern, and that is exactly what happens here:

```
$ git status --porcelain src/
 M src/test/kotlin/com/six2dez/burp/aiagent/redact/CookieCarrierInventoryTest.kt

$ find src -name '*Probe*'
src/test/kotlin/com/six2dez/burp/aiagent/mcp/McpSupervisorProbeTest.kt

$ git log --oneline -1 -- src/test/kotlin/com/six2dez/burp/aiagent/mcp/McpSupervisorProbeTest.kt
08e8ff8 fix(20-05): make probeExistingServer mode-aware and give it its first test

$ git status --porcelain src/test/kotlin/com/six2dez/burp/aiagent/mcp/McpSupervisorProbeTest.kt
(empty — tracked and unmodified)
```

The one `*Probe*` hit is a file from phase 20, unmodified by this plan. The criterion is satisfied on
the PATH check: `git status --porcelain src/` shows exactly one entry, the registry test, and no new
file.

---

# THE CARRIER INVENTORY

## Re-measured accessor counts, per file per accessor

Measured at execution time on THIS tree with the Edit tool and re-run scans, comment-only lines
(`^\s*(//|\*|/\*)`) stripped before counting. **Not copied from the plan's baseline table** — the plan
required re-measurement before any constant was written, and a baseline asserted rather than measured
is the defect `WINDOWS.md` records most often in this phase.

| file | `.headers()` | `.parameters(` | `.headerValue(` | req/resp `.toString()` | `cookieJar()` |
|------|---|---|---|---|---|
| `scanner/ActiveAiScanner.kt` | 5 | 0 | 2 | 0 | 0 |
| `scanner/PassiveAiScannerHeuristics.kt` | 5 | 2 | 5 | 0 | 0 |
| `scanner/PassiveAiScannerAnalysis.kt` | 5 | 1 | 2 | 0 | 0 |
| `scanner/PassiveAiScannerFilters.kt` | 2 | 1 | 0 | 0 | 0 |
| `scanner/InjectionPointExtractor.kt` | 2 | 4 | 2 | 0 | 0 |
| `scanner/ResponseAnalyzer.kt` | 2 | 0 | 0 | 0 | 0 |
| `mcp/tools/McpToolLegacy.kt` | 2 | 3 | 0 | 5 | 1 |
| `mcp/tools/McpToolExecutorImpl.kt` | 2 | 3 | 0 | 5 | 1 |
| `mcp/schema/Serialization.kt` | 0 | 0 | 0 | 5 | 0 |
| `context/ContextCollector.kt` | 0 | 0 | 0 | 2 | 0 |
| `prompts/bountyprompt/BountyPromptTagResolver.kt` | 0 | 1 | 0 | 2 | 0 |
| **TOTAL** | **25** | **15** | **11** | **19** | **2** |

**72 sites across 11 distinct files. EVERY per-accessor total and every per-file cell matches plan
27-08's baseline table exactly. There is no difference to explain.** That is a real result rather than
a formality: plan 27-07 landed between the baseline measurement and this one, and it changed what
happens to the RESULT of `request.parameters()` at four sites without changing whether the call is
made — which is precisely what the table predicted and what the re-measurement confirms.

**Two definitional notes, because both matter for reproducing the numbers:**

- **`req/resp .toString()` is NOT a bare `.toString()` scan.** A bare scan returns 176 hits across 42
  files, most of them UI and supervisor code. The accessor is the RAW MESSAGE, so the pattern is
  `(request|response)\(\)\??\.toString\(\)`. That yields exactly the 19 in the table. `Serialization.kt`
  illustrates why the narrowing matters: it has 9 bare `.toString()` hits, of which 5 are raw-message
  reads (`:44, :45, :50, :59, :82`) and 4 are not (`:29` `id()`, `:30` `timeStamp()`, `:67` `payload()`,
  `:81` `req?`, an already-unwrapped local).
- **`cookieJar()` alone is a complete detector for the jar accessor on this tree.** Both sites are
  single lines reading `api.http().cookieJar().cookies()` (`McpToolLegacy.kt:316`,
  `McpToolExecutorImpl.kt:476`), and `.cookies()` appears nowhere else. Using
  `cookieJar()|\.cookies()` would double-count the same two lines to 4 and disagree with the baseline
  for no gain.

### Supporting measurements

| # | Measurement | Plan said | MEASURED here | OK |
|---|---|---|---|---|
| C1 | `.headerValue(` argument multiset | `Content-Type` ×6, `Cookie` ×3, `Referer` ×1, `Origin` ×1 | **identical**; cookie sites at `PassiveAiScannerHeuristics.kt:102`, `ActiveAiScanner.kt:936`, `ActiveAiScanner.kt:1411` | yes |
| C2 | `bodyToString(` sites, comment-stripped | 32 across 8 files | **32 across 8 files** (`ActiveAiScanner` 12, `ResponseAnalyzer` 7, `AiScanCheck` 3, `McpToolExecutorImpl` 2, `McpToolLegacy` 2, `InjectionPointExtractor` 2, `PassiveAiScanner` 2, `PassiveAiScannerAnalysis` 2) | yes |
| C3 | `CookieCarrierInventoryTest.kt` exists | no | **no** before, **yes** after | yes |
| C4 | `cookieTypedParamRegex` line numbers | comment `:628-639`, regex `:640` | **comment `:678-689`, regex `:690`** — a **+50 line shift**, explained below | explained |
| C5 | `git diff HEAD -- RedactionTest.kt \| wc -l` | 0 | **0** before and after | yes |
| C6 | `AuditIssue.detail()` reaches an MCP tool result | YES via `Serialization.kt:14` | **YES**, `detail = detail()` at `Serialization.kt:14` | yes |

**C4, the one difference from the plan's baseline, explained BEFORE any constant depending on it was
written.** The plan measured at commit `389cbbd`; this worktree is based on `a20290f`, which includes
plan 27-07's merge. 27-07 added `Redaction.isCookieParameterType` and
`COOKIE_PARAMETER_TYPE_NAME` with their KDoc to `Redaction.kt` ABOVE this rule, shifting everything
below by exactly 50 lines. The plan's own `<context>` block states that `27-07-SUMMARY.md` supersedes
anything the plan says about what 27-07 built, and this is that case. **Nothing in this plan is keyed
on a line number**: the comment block was located by content and edited with the `Edit` tool, and the
inventory registry is keyed on PATH and COUNT, never on line, precisely so it does not rot the first
time a file above one of its sites changes (`WINDOWS.md` 11/13/14/15).

## The full classification table, with the consumer read for each entry

Every entry's reason was read from that site's CONSUMER, not from the call — `26-SECURITY.md` standing
rule (i): presence is not width. The registry lives in `CookieCarrierInventoryTest.kt` with these
reasons in full; the table below names, for each entry, exactly which consumer was read.

### ROUTED_THROUGH — 12 entries

| Site | Named control | CONSUMER READ |
|---|---|---|
| `McpToolExecutorImpl` / headerList | `McpToolHelpers.sanitizeHeaders` | `:376`, `:395` — both pass the list straight in as `ParsedRequest.headers` / `ParsedResponse.headers` |
| `McpToolLegacy` / headerList | `McpToolHelpers.sanitizeHeaders` | `:184`, `:207` — same two shapes |
| `McpToolExecutorImpl` / parameterList | `McpToolHelpers.sanitizeParameters` | `:360` (params_extract), `:381` (request_parse). **Third call `:406` is NON-CARRYING:** read `:406-411` — `find_reflected` uses the value only as a needle for `countOccurrences` and emits `name=… type=… count=…` |
| `McpToolLegacy` / parameterList | `McpToolHelpers.sanitizeParameters` | `:160`, `:189`. **Third call `:222` NON-CARRYING:** read `:222-227`, same `find_reflected` shape |
| `McpToolExecutorImpl` / rawMessage | `Redaction.apply` at the `redactIfNeeded` choke point | `:592`, `:593`, `:732`, `:761`, `:811` all sit inside the dispatch `when` wrapped at `McpToolExecutorImpl.kt:1045` |
| `McpToolLegacy` / rawMessage | `Redaction.apply` at the `redactIfNeeded` choke point | `:460`, `:461`, `:622`, `:640`, `:690` all inside `mcpTool` / `mcpPaginatedTool` registrations wrapped at `McpTool.kt:45` and `:78` |
| `Serialization` / rawMessage | `Redaction.apply` at the choke point | `:44`, `:45`, `:50`, `:59`, `:82` build `HttpRequestResponse` / `SiteMapEntry` carriers; only the two executors serialize them, and those emissions are wrapped. This file emits nothing itself |
| `ContextCollector` / rawMessage | `Redaction.apply`, called directly | `:41`, `:45` truncated then redacted at `:51`, `:52`; only the redacted strings reach `HttpItem` |
| `BountyPromptTagResolver` / rawMessage | `Redaction.apply`, called directly | `:77`, `:78` redacted at `:79`, `:80`; every tag branch below reads only `requestRedacted` / `responseRedacted` |
| `BountyPromptTagResolver` / parameterList | `Redaction.isCookieParameterType` (27-07, D-27-21) | `buildRequestParameters` at `:119` renders `name=value (TYPE)`; the type gate at `:151` writes `[STRIPPED]` under any `stripCookies` policy. **Recorded with it:** the tag value never passes `Redaction.apply`, so a token in a URL/BODY-typed VALUE is not covered here |
| `PassiveAiScannerAnalysis` / headerList | `Redaction.apply` via `redactScanMetadata` | `:257`, `:258` via `sanitizeHeadersForPrompt`; `:266` selects Cookie values for `cookieSectionLines` / the `=== COOKIES ===` span rule; `:274` auth headers; `:286` response headers to tech hints. All five feed `buildScanMetadataText` at `:360`, redacted at `:380` |
| `PassiveAiScannerAnalysis` / parameterList | `Redaction.cookieTypedParamRegex` via `redactScanMetadata` | `:248` maps through `formatParamLine` (`PassiveAiScannerPrompts.kt:34`) into `name=value (TYPE)`, matched at `Redaction.kt:724` after `:380` |

### CLASSIFIED_NON_CARRYING — 14 entries

| Site | Classification | CONSUMER READ |
|---|---|---|
| `McpToolExecutorImpl` / cookieJar | MODE-GATED AT THE FIELD | `:476` reads the jar; the map at `:502-523` emits `cookie.value()` ONLY when `includeValues && privacyMode == OFF`, writing `[REDACTED]` otherwise; domain anonymised under STRICT |
| `McpToolLegacy` / cookieJar | MODE-GATED AT THE FIELD | `:316` reads the jar; `:348-353` applies the byte-identical gate |
| `ActiveAiScanner` / headerList | LOCAL ANALYSIS + TARGET-BOUND MUTATION | `:933` `hasAuthContext` → Boolean; `:943` `stripAuthHeaders` REMOVES the matched headers and `Cookie` outright; `:995` `buildFullResponse` used only for `.contains(payload.value)` at `:873-884`; `:1025` `responseHeaderValue` serves `isCacheable` / `isCacheHit` Booleans; `:1387` rebuilds a request sent to the TARGET |
| `ActiveAiScanner` / singleHeaderLookup | BOTH ARE `headerValue("Cookie")`, BOTH NON-EMITTING | `:936` → `authCookieHint.containsMatchIn(...)`, a Boolean; `:1411` rewrites the cookie string and sends it to the TARGET via `withAddedHeader` |
| `PassiveAiScannerHeuristics` / headerList | LOCAL ANALYSIS → fixed `LocalFinding` detail strings | `:66` compares Content-Length / Transfer-Encoding; `:101`, `:107` → Booleans; `:116` reduces Set-Cookie to a `sameSiteSecure` Boolean; `:186` reduces Location to a filename containment test |
| `PassiveAiScannerHeuristics` / parameterList | LOCAL ANALYSIS | `:106` tests parameter NAMES against `csrfTokenRegex`; `:139` tests a name and a value PREFIX (`rO0AB` / `aced0005`) and returns a fixed detail string |
| `PassiveAiScannerHeuristics` / singleHeaderLookup | LOCAL ANALYSIS | `:102` `headerValue("Cookie")` → `authCookieHint.containsMatchIn`, a Boolean; `:110`, `:111` null/blank tests; `:149`, `:173` Content-Type containment tests |
| `PassiveAiScannerAnalysis` / singleHeaderLookup | NON-CARRYING BY ARGUMENT | `:356`, `:357` both pass `"Content-Type"`, feeding `buildCompactRequestBody` / `buildCompactResponseBody` |
| `PassiveAiScannerFilters` / headerList | ADMISSION AND HASHING | `:80` tests header NAMES against an allowlist → Boolean; `:147` passes the sanitized list into `sha256Hex` at `:159`, so only a digest survives as a dedup cache key |
| `PassiveAiScannerFilters` / parameterList | ARITY ONLY | `:60` calls `.isNotEmpty()` in `shouldSkipAiAnalysis`; the parameter objects are discarded |
| `InjectionPointExtractor` / headerList | ALLOWLISTED INJECTION POINTS | `:33` admits a header only when `headerAllowlist` contains its lowered name; `:185` matches by raw-byte offset for a user selection. Both produce `InjectionPoint` values and share the parameterList disposition below |
| `InjectionPointExtractor` / parameterList | **TWO CONSUMERS, BOTH READ, AND THEY DIFFER** — see below | `AdaptivePayloadEngine.kt:52` AND `ActiveAiScanner.kt:1239` |
| `InjectionPointExtractor` / singleHeaderLookup | NON-CARRYING BY ARGUMENT | `:45`, `:207` both pass `"Content-Type"` to choose a JSON or XML field extractor |
| `ResponseAnalyzer` / headerList | PATTERN MATCHING, with one narrow transitive tail recorded | `:616`, `:623` build `modifiedHeaders` / `originalHeaders`, consumed by `isFalsePositive`, `analyzeErrorBased`, `analyzeReflection`, `analyzeContentBased` — all `containsMatchIn` / `find` tests. **THE TAIL:** a MATCHED substring of a vuln-class signature, capped at 80 chars, can be written into `VulnConfirmation.evidence`, which `ActiveAiScanner.kt:1246` puts in the SAME `AuditIssue` detail as measurement 2 |

**Every one of the 26 (file, accessor) pairs appears in exactly one of the two maps; the overlap set is
asserted empty; NEW and STALE are both diagnosed by name.**

## The disposition of `InjectionPointExtractor.kt:29`

Called out separately because 27-07 explicitly deferred it to this plan, and because it is the entry
the inventory was built to surface.

`InjectionPointExtractor.kt:29` writes its own cookie-parameter predicate,
`request.parameters().filter { it.type().name == "COOKIE" }`, and remains **BYTE-UNCHANGED** by this
plan — as it was by 27-07 (baseline B9). It is not converted to `Redaction.isCookieParameterType`, and
that is a decision, not an omission: swapping the predicate there would change nothing about the
route's disclosure while making the route LOOK addressed. That is the exact failure mode this phase
exists to repair.

Its disposition in the registry is **CLASSIFIED_NON_CARRYING, not ROUTED**, on the stated ground that
**a route with an uncontrolled consumer is not a route.** The entry names both consumers:

- **CONSUMER 1, the AI-facing prompt path — CONTROLLED, by a DIFFERENT mechanism from every other
  entry in the registry.** `AdaptivePayloadEngine.kt:52` reads
  `val safeOriginalValue = if (privacyMode != PrivacyMode.OFF) "[REDACTED_VALUE]" else originalValue`,
  substituting the value under ANY non-`OFF` mode before it reaches a prompt. Surfacing exactly this
  kind of one-off control is what an accessor-keyed inventory is for.
- **CONSUMER 2 — UNCONTROLLED.** `ActiveAiScanner.kt:1239` → `Serialization.kt:14` → `scanner_issues`.
  This is measurement 2 above: **AR-27-08, medium**, MEASURED and NOT fixed here.

`InjectionPointExtractor.kt:29` is therefore deferred **WITH** the issue-detail route and must be
closed by the SAME successor plan 27-09 opens. Fixing the predicate without fixing the route it feeds
would produce a tidier file and an unchanged leak.

## The bound, quoted back

Acceptance criterion 9 requires reading the KDoc bound back verbatim. From
`src/test/kotlin/com/six2dez/burp/aiagent/redact/CookieCarrierInventoryTest.kt`:

> ── THE BOUND, STATED FIRST AND NOT AS A FOOTNOTE ──
>
> THIS IS A TRIPWIRE OVER A MEASURED ACCESSOR SET. IT IS NOT A PROOF OF COVERAGE. Anyone who quotes
> this test as evidence that no cookie value can reach an AI backend reproduces, one iteration
> smaller, the defect this whole phase exists to repair: a record wider than the control it
> describes. Four things it CANNOT see, named here so nobody has to discover them as round five:
>
> 1. **A COOKIE BYTE THAT NEVER PASSES A MONTOYA ACCESSOR.** Operator-pasted text in the chat box; a
>    model echoing a cookie back into the transcript; a cookie this extension itself persisted to
>    settings or a cache and later emitted. No accessor-keyed mechanism reaches any of these.
>
> 2. **`bodyToString()`, DELIBERATELY EXCLUDED** — with the reason that actually holds. Measured at 32
>    call sites across 8 files, comment-stripped, 2026-08-25. The exclusion is NOT because an entity
>    body cannot contain cookie bytes. It can: a body can carry a pasted raw HTTP message, a
>    forwarded webhook envelope, or a proxied upstream request, and any of those carries a `Cookie:`
>    header inside the body text. Writing the exclusion as "an entity body does not contain the
>    Cookie header" would be an absolute that one pasted request falsifies — a premise authored to
>    be refuted in round five. THE REASON THAT DOES HOLD: a body of that shape reaching a backend
>    passes `Redaction.apply` at the `redactIfNeeded` choke point, and there the logical-line cookie
>    rules DO fire, because such a body carries a REAL newline before the `Cookie:` token — exactly
>    the boundary waves 4-6 taught those rules to see. The carrier is covered by an EXISTING control
>    on a DIFFERENT axis, so enumerating its 32 sites here would add churn without adding coverage.
>    Two contingencies travel with that reason, because it is contingent where the old one pretended
>    to be absolute: (a) the exclusion FAILS for any body that reaches a backend on a path bypassing
>    `Redaction.apply`, and an accessor-keyed inventory cannot see such a path — that is axis 1, one
>    step out; and (b) a session token duplicated into a body FIELD (a JSON value, a form parameter)
>    carries no header line and no newline discriminator, so it is the TOKEN class (PRIV-02),
>    reachable by different rules and NOT covered here either.
>
> 3. **TRANSITIVE CARRIERS**, where a cookie byte is copied into a field whose accessor is not on the
>    list. `AuditIssue.detail()` is the worked example, surfaced by the `InjectionPointExtractor` and
>    `ResponseAnalyzer` entries below and carried in `[ISSUE_DETAIL_CARRIER_DISPOSITION]`. This
>    inventory can point at the FIRST hop; it cannot follow a value through arbitrary copies.
>
> 4. **A NEW MONTOYA ACCESSOR** added by a future API version that returns cookie data under a name not
>    in `[COOKIE_BYTE_ACCESSORS]`. The set is additive-only and a reader adding one must extend it.
>
> ── A FIFTH, WEAKER BOUND, ON THE GRANULARITY OF THE CLASSIFICATION ITSELF ──
>
> Separated from the four above rather than averaged into them, because it is a limit of the
> BOOKKEEPING and not of the axis. A registry key is a (file, accessor) PAIR, not an individual call.
> Where the calls behind one pair split — `McpToolExecutorImpl`'s three `parameters()` calls are two
> `sanitizeParameters` producers plus one non-carrying `find_reflected` reader — the pair sits in
> `[ROUTED_THROUGH]` and its reason enumerates the split by line. That prose is NOT machine-checked.
> What IS machine-checked is the COUNT: adding a fourth `parameters()` call to that file turns
> `[theMeasuredPerFilePerAccessorCountsArePinned]` red and forces the split to be re-read. The tripwire
> property survives; the per-call attribution is a human record inside it.

### THE NAMED NEXT BLIND AXIS

**Axis 1 above is the named next blind axis, and it is named as such rather than left implicit.** A
cookie byte that arrives by a route which is not an accessor call at all defeats this entire
mechanism. Plan 27-08's `<assumption_delta_decision>` states what that would force: *"a cookie byte
arriving by a route that is not an accessor call at all… At that point the primary noun becomes 'a
value that entered the process', and an accessor-keyed inventory demotes to one variant of THAT."*
Three concrete instances are named in the KDoc — operator-pasted chat text, a model echoing a cookie
back into the transcript, and a cookie this extension persisted to settings or a cache and later
emitted. None of them is covered by anything this plan built.

---

# TASK-BY-TASK RESULTS

## Task 1 — the narrowed comment block and the prompt-path fixtures

**Commit `a2edf9f`.**

### Criterion 1 — the regex source line is byte-identical, and every removed line is a comment

The ROBUST form was used, never `grep -c '^-[^-]'` (`WINDOWS.md` entry 18 records that as a false zero
on markdown bullets):

```
$ git diff HEAD --unified=0 -- src/main/kotlin/com/six2dez/burp/aiagent/redact/Redaction.kt \
    | grep '^-' | grep -v '^--- '
-    // (PRIV-05) SC2 / D-09: a COOKIE-typed parameter line — the second entry point of the same
-    // leak, reached through request.parameters(). Parameters are emitted as "name=value (TYPE)"
-    // where TYPE is the Montoya HttpParameterType name (formatParamLine in
-    // scanner/PassiveAiScannerPrompts.kt), so keying on the semantic type label rather than on a
-    // section header makes this rule CONTEXT-FREE: it survives a section rename and works wherever
-    // the shape appears. The asymmetry with the section rule above is deliberate — that section has
-    // no discriminator other than its header, whereas a parameter line carries one in its own text.
```

**Seven removed lines. Every one begins with `//` after leading whitespace. The pattern definition is
absent from the removed set.** Independently confirmed by comparing the line at both revisions:

```
$ grep -n 'private val cookieTypedParamRegex' src/main/kotlin/.../Redaction.kt
724:    private val cookieTypedParamRegex = Regex("(?im)^([^=\\r\\n]+)=(.*?)(\\s\\(COOKIE\\))\\s*$")

$ git show HEAD:src/main/kotlin/.../Redaction.kt | grep -n 'private val cookieTypedParamRegex'
690:    private val cookieTypedParamRegex = Regex("(?im)^([^=\\r\\n]+)=(.*?)(\\s\\(COOKIE\\))\\s*$")
```

Same bytes; the line number moved because the comment block grew.

**What the new comment says.** (a) the ONE shape, naming `formatParamLine` and its single producer
`PassiveAiScannerAnalysis.kt`; (b) that this repository owns that shape, so changing the separator or
the label there silently disables the rule with no compile error; (c) that the MCP renderings are NOT
reached and are owned by `Redaction.isCookieParameterType` + `McpToolHelpers.sanitizeParameters`; (d)
the correction, dated 2026-08-25 and attributed to plan 27-08, stating that the previous "reached
through `request.parameters()`" claim is true of the DATA and false of the RULE, and that the gap is
how two live MCP tools leaked past it for three rounds. The `^name=value$` rejection reasoning and the
section-rule asymmetry sentence survive **verbatim**, under an explicit "What survives that correction
UNALTERED, because it is still true:" heading. **The block contains no sentence of the form "the
cookie parameter class is now covered."**

### Criterion 2 — the five fixture groups, present BY NAME

`TEST-com.six2dez.burp.aiagent.mcp.tools.ParameterCarrierRedactionTest.xml`:
`tests="25" skipped="0" failures="0" errors="0"` (18 from 27-07 + 7 new).

| Group | Method, as it appears in the XML |
|---|---|
| (i) canonical, STRICT | `promptPathCanonicalCookieParamLineIsRedactedUnderStrict()` |
| (i) canonical, BALANCED | `promptPathCanonicalCookieParamLineIsRedactedUnderBalanced()` |
| (ii) OFF control | `promptPathCanonicalCookieParamLineIsUntouchedUnderOff()` |
| (iii) DQUOTE, STRICT | `promptPathDquoteWrappedCookieValueIsRedactedUnderStrict()` |
| (iii) DQUOTE, BALANCED | `promptPathDquoteWrappedCookieValueIsRedactedUnderBalanced()` |
| (iv) non-cookie label, all modes | `promptPathNonCookieTypeLabelIsUntouchedInEveryMode()` |
| (v) multi-parameter, order | `promptPathMultiParameterBlockRedactsOnlyTheCookieLineAndPreservesOrder()` |

All drive the REAL `Redaction.apply` with `RedactionPolicy.fromMode`, never a copy of the pattern.
Group (iii) asserts on the FULL output string (`layoutpref=[REDACTED] (COOKIE)`), so a swallowed or
stranded quote fails it even though the sentinel would be absent either way.

### Criterion 3 — RED PROBE, with the specific assertion that failed in each group

The type-label group `(\s\(COOKIE\))` was temporarily replaced with `(\s\(ZZZPROBEZZZ\))` and the
suite re-run with `--rerun`. Result: `tests="25" failures="5"`.

| Group | Method | Verdict | The assertion that failed |
|---|---|---|---|
| (i) | `promptPathCanonicalCookieParamLineIsRedactedUnderStrict` | **RED** | `the passive scanner's canonical COOKIE parameter line must be redacted under STRICT, with name and type label written back verbatim ==> expected: <layoutpref=[REDACTED] (COOKIE)> but was: <layoutpref=promptalfa (COOKIE)>` |
| (i) | `promptPathCanonicalCookieParamLineIsRedactedUnderBalanced` | **RED** | `…must be redacted under BALANCED ==> expected: <layoutpref=[REDACTED] (COOKIE)> but was: <layoutpref=promptbravo (COOKIE)>` |
| (ii) | `promptPathCanonicalCookieParamLineIsUntouchedUnderOff` | GREEN | — |
| (iii) | `promptPathDquoteWrappedCookieValueIsRedactedUnderStrict` | **RED** | `an RFC 6265 DQUOTE-wrapped cookie value must be redacted whole under STRICT, leaving no stranded quote in the output ==> expected: <layoutpref=[REDACTED] (COOKIE)> but was: <layoutpref="promptdelta" (COOKIE)>` |
| (iii) | `promptPathDquoteWrappedCookieValueIsRedactedUnderBalanced` | **RED** | `…under BALANCED ==> expected: <layoutpref=[REDACTED] (COOKIE)> but was: <layoutpref="promptecho" (COOKIE)>` |
| (iv) | `promptPathNonCookieTypeLabelIsUntouchedInEveryMode` | GREEN | — |
| (v) | `promptPathMultiParameterBlockRedactsOnlyTheCookieLineAndPreservesOrder` | **RED** | `only the COOKIE-typed line may change, and the three lines must keep their order ==> expected: <…layoutpref=[REDACTED] (COOKIE)…> but was: <…layoutpref=promptgolf (COOKIE)…>` |

**Groups (i), (iii) and (v) RED; (ii) and (iv) GREEN — exactly what criterion 3 predicted, with no
substitution.** `WINDOWS.md` entry 13 records a probe in this phase that failed a DIFFERENT assertion
than its plan predicted; this one did not, and the specific message is quoted for each so the claim is
checkable rather than asserted. The regex was then restored and the byte-identity re-verified above.

**One process note worth recording, because it nearly produced a wrong reading.** A first pass at
extracting the failures with a regex over the XML mis-attributed failure bodies to the wrong
`<testcase>` names, because self-closing `<testcase …/>` elements defeat a greedy match. The
attribution above was re-derived by reading the raw XML element structure directly. Reading a
`testcase name` attribute out of a sloppily-parsed XML is the same class of error as reading a count
out of a comment-inflated grep.

### Criterion 4 — no green test asserts a cookie value SURVIVES a redacting policy

**THE SEARCH RUN, verbatim:**

```bash
grep -rniE 'assert(True|Equals).*(cookie|JSESSIONID|PHPSESSID|SESSIONID)' src/test/kotlin --include=*.kt -n \
  | grep -viE 'assertFalse|assertNull'
```

**THE POPULATION RULE APPLIED**, as the criterion bounds it: an assertion is IN the population only if
the value it asserts on is the RETURN of a redacting call (`Redaction.apply`, `redactScanMetadata`,
`redactIfNeeded`, or a `RedactionPolicy`-taking helper) **and** it asserts PRESENCE of a cookie
sentinel under STRICT or BALANCED. A fixture pinning an EMITTER's shape UPSTREAM of the choke point is
not asserting survival under a policy — no policy has run.

**EVERY HIT AND ITS DISPOSITION. 39 hits across 11 files; NONE is in the population.**

| File:line(s) | Asserted value | Disposition |
|---|---|---|
| `ParameterCarrierRedactionTest.kt:114,116,127,253` | `STRIPPED_MARKER` / `"COOKIE"` type label | OUT — asserts the MARKER, the OPPOSITE of survival |
| `ParameterCarrierRedactionTest.kt:143` | `Sentinel.COOKIE_OFF.value` | OUT — **OFF mode**. The criterion is scoped to STRICT or BALANCED, and this is the control that makes the STRICT/BALANCED probes measure the policy |
| `McpToolHelpersTest.kt:115,116,354,355,430,431,453,531` | `stripped` | OUT — all assert the marker |
| `McpToolHelpersTest.kt:561` | header NAME ordering | OUT — no value asserted |
| `AiRequestLoggerTest.kt:130` | `map["sessionId"]` | OUT — an audit-log session identifier, not a cookie value; no redacting call |
| `BountyPromptTagResolverTest.kt:93` | `"Cookie: [STRIPPED]"` | OUT — asserts the marker |
| `SecretTripwireGateTest.kt:120,158` | `payload["sessionId"]` | OUT — the tripwire's own telemetry session id; not a cookie, no redacting call |
| `SecretTripwireHooksTest.kt:45,77,129,137,186,195` | `payload["sessionId"]` / `result["sessionId"]` | OUT — same, six occurrences |
| `InjectionPointExtractorTest.kt:45,207` | `InjectionType` and `point.name` | OUT — asserts a TYPE and a NAME, never a value; no redacting call |
| `RedactionPolicyTest.kt:24,33,42` | `policy.stripCookies` | OUT — a POLICY FIELD, not output |
| `RedactionPolicyTest.kt:260` | `"sid=[REDACTED]"` | OUT — asserts the marker |
| `RedactionTest.kt:367` | `"Cookie: [STRIPPED]"` | OUT — asserts the marker |
| `RedactionTest.kt:1654` | planted-region SIZE | OUT — a count, not a value |
| `PassiveAiScannerHeaderAdmissionTest.kt:103,104` | `X-COOKIE: turkishcookievalue`, `SET-COOKIE: turkishsetcookievalue` | OUT — **named exclusion 1, RE-VERIFIED on this tree**: an ADMISSION fixture with no policy applied. Measured, the whole file contains exactly ONE match for `RedactionPolicy\|Redaction\.apply\|PrivacyMode`, and it is the comment at `:25` stating that value redaction is `Redaction.apply`'s job downstream |
| `PassiveAiScannerPromptRedactionTest.kt:88,91` | `=== COOKIES ===`, verbatim cookie lines | OUT — **named exclusion 2, RE-VERIFIED, and EXCLUDED BY METHOD not by file**: both sit inside `buildScanMetadataText_emitsCookieAndParameterSections` (starts `:80`), a pre-redaction emitter fixture. The file carries **15** matches for `RedactionPolicy\|Redaction\.apply\|PrivacyMode` and DOES contain genuine redaction tests; a file-level exclusion would have blinded this search to them |
| `PassiveAiScannerPromptRedactionTest.kt:343` | `headerIndex >= 0` | OUT — a fixture-setup INDEX check inside `cookieSectionEntriesAreSanitizedAtTheEmitter` (starts `:326`), on the pre-redaction `metadataBlob` |
| `PassiveAiScannerPromptRedactionTest.kt:393` | `COOKIES_MAX_COUNT == lines.size` | OUT — a line COUNT from the PRODUCER, inside `blankCookieElementsDoNotConsumeDisplaySlots` (starts `:390`) |

**RESULT: no hit is both the return of a redacting call and an assertion of cookie-sentinel PRESENCE
under STRICT or BALANCED. There is no live finding to report.**

**THE CLAIM IS BOUNDED TO THE SCOPE THE SEARCH COVERED, and is deliberately not restated wider**
(`26-SECURITY.md` standing rule (ii)): the search covers `src/test/kotlin/**/*.kt`, `assertTrue` and
`assertEquals` calls whose line mentions `cookie` / `JSESSIONID` / `PHPSESSID` / `SESSIONID`
case-insensitively. An assertion spelling its sentinel some other way, or splitting the assertion
across lines so neither line carries both tokens, is outside what this search can see. What is
established is: **across that population, nothing under `src/` asserts that a cookie value survives a
redacting policy — and this plan added nothing of the kind.**

### Criteria 5 and 6

`git diff HEAD -- src/test/kotlin/.../RedactionTest.kt | wc -l` → **0**. `detekt` and `ktlintCheck` →
**BUILD SUCCESSFUL**.

## Task 2 — the carrier inventory

**Commit `3125f0f`.** Four assertions, present BY NAME in
`TEST-com.six2dez.burp.aiagent.redact.CookieCarrierInventoryTest.xml`:
`tests="4" skipped="0" failures="0" errors="0"`.

`everyCookieByteCarrierSiteIsRoutedOrClassified()`,
`theMeasuredPerFilePerAccessorCountsArePinned()`,
`theHeaderValueArgumentMultisetIsPinned()`,
`theCarrierScanIsNonVacuous()`.

Baseline C3 measured the file as ABSENT, so this gate could not pass vacuously.

### The three red probes and the non-vacuity check, each naming the SPECIFIC assertion

**RED PROBE A — a carrier call in a file in neither map.** Appended
`private fun probeAUnclassifiedCarrier(rr: HttpRequestResponse): Int = rr.request().headers().size`
to `scanner/AiScanCheck.kt`, a file with no measured accessor. Result `failures="2"`.

Assertion **(1)** `everyCookieByteCarrierSiteIsRoutedOrClassified` went RED naming the path under NEW,
exactly as criterion 4 requires:

```
the set of cookie-byte carrier sites has changed.
  NEW (route it through a named control, or classify it in CLASSIFIED_NON_CARRYING with the reason
  you read from its CONSUMER — not from the call site): [com/six2dez/burp/aiagent/scanner/AiScanCheck.kt[headerList]]
  STALE (declared here but no longer measured — remove the entry so the registry cannot accumulate dead keys): []
```

Assertion **(2)** also went red, on its `set of FILES` limb, because the probe introduced a whole new
FILE and not merely a new call in a known one. **Reported rather than smoothed over**: criterion 4 asks
only for assertion (1), and (1) is the one that named the path. Restored via
`git checkout -- scanner/AiScanCheck.kt`; the file is byte-clean.

**RED PROBE B — one entry removed from `MEASURED_CARRIER_SITES`.** Dropped `PARAMETER_LIST to 1` from
the `PassiveAiScannerAnalysis` map. Result `failures="1"`.

**It is assertion (2) `theMeasuredPerFilePerAccessorCountsArePinned`, and NOT assertion (1)** —
confirmed explicitly, because `WINDOWS.md` entry 13 records exactly this confusion in this phase.
Assertion (1) stayed GREEN, which is correct and informative: the site is still classified, only its
COUNT moved, and the two assertions genuinely answer different questions.

```
com/six2dez/burp/aiagent/scanner/PassiveAiScannerAnalysis.kt no longer carries the pinned accessor counts.
The WHOLE per-file map is printed so a drift is diagnosable without re-running the scan by hand.
  pinned:   {headerList=5, singleHeaderLookup=2}
  measured: {headerList=5, parameterList=1, singleHeaderLookup=2}
```

**RED PROBE C — one declared `.headerValue(` argument changed.** Replaced `"Cookie" to 3` with
`"X-Probe-C" to 3`. Result `failures="1"`, and it is assertion **(3)**
`theHeaderValueArgumentMultisetIsPinned` ALONE; (1), (2) and (4) all stayed GREEN:

```
the `.headerValue("…")` argument multiset has changed. A NEW cookie-named argument is a new carrier
site even if the per-file count did not move.
  pinned:   {Content-Type=6, X-Probe-C=3, Origin=1, Referer=1}
  measured: {Cookie=3, Origin=1, Referer=1, Content-Type=6}
```

**NON-VACUITY CHECK — the walk pointed at a root with no source.** Run in two variants, because the
first does not exercise the floor:

- `MAIN_SOURCE_ROOT = "src/main/kotlin-probe-nonexistent"` → **all four assertions FAIL** with
  `java.lang.AssertionError: could not resolve src/main/kotlin-probe-nonexistent from user.dir=… Resolve
  the path rather than weakening this test into a skip.` The resolver throws before the floor
  assertion can run, so this variant proves the walk-up fails closed but says nothing about the floor.
- `MAIN_SOURCE_ROOT = "src/main/resources"` — an EXISTING directory containing no `.kt` file, so the
  resolver succeeds and the floor is reached. Assertion **(4)** `theCarrierScanIsNonVacuous` fails on
  the floor itself:

```
the walk found only 0 .kt files under src/main/resources — the scan is not reaching the repository,
so every other assertion here proves nothing
```

**In neither variant does any assertion pass on an empty file set.** Both restored.

### Criterion 8 — the two template suites still green and UNEDITED

```
$ git diff HEAD --stat -- src/test/kotlin/.../CookieHeaderRuleOwnershipTest.kt \
                          src/test/kotlin/.../SerializedEmissionSiteInventoryTest.kt
(no entries)
```

`CookieHeaderRuleOwnershipTest` `tests="3" failures="0" errors="0"`;
`SerializedEmissionSiteInventoryTest` `tests="5" failures="0" errors="0"`.

## Task 3 — the two measurements

**Commit `dc2f1c9`.** Results, probes, controls, reachability and severities are recorded in full in
**THE TWO MEASURED-NOT-FIXED RESULTS** above. The one change to the committed tree is the
`ISSUE_DETAIL_CARRIER_DISPOSITION` constant in `CookieCarrierInventoryTest.kt`, which no longer carries
the provisional text and now states the measured classification, its mechanism, its source-cited
reachability, its severity with reasoning, and the residual id `AR-27-08` that plan 27-09 files it
under. `CookieCarrierInventoryTest` is still green with all four assertions present by name.

---

## Task Commits

1. **Task 1: narrow the reach claim, pin the prompt path** — `a2edf9f` (docs)
2. **Task 2: the cookie-byte carrier inventory** — `3125f0f` (test)
3. **Task 3: the measured issue-detail carrier disposition** — `dc2f1c9` (docs)

## Files Created/Modified

- `src/test/kotlin/com/six2dez/burp/aiagent/redact/CookieCarrierInventoryTest.kt` — **NEW**, 561 lines.
  The carrier inventory: 5 accessors, 26 (file, accessor) pairs, 72 sites, 4 assertions, 5 stated bounds.
- `src/main/kotlin/com/six2dez/burp/aiagent/redact/Redaction.kt` — `cookieTypedParamRegex`'s comment
  block narrowed (+41/−7 lines, all comment). The pattern is byte-identical.
- `src/test/kotlin/com/six2dez/burp/aiagent/mcp/tools/ParameterCarrierRedactionTest.kt` — +175 lines:
  seven prompt-path fixtures, nine sentinels, the `[REDACTED]` marker constant, an amended class KDoc
  recording that two non-interchangeable controls now live in one file.

## Decisions Made

All four decisions the plan locked (D-27-22, D-27-19, D-27-20, and T-27-08-06's TRANSFER disposition)
were applied as written. No `checkpoint:decision` was emitted: every task is `type="auto"` and the plan
is `autonomous: true`. Two execution-time decisions are recorded as deviations below.

Two judgements the plan left to the measurement are recorded where they were made:
- **Measurement 1's severity is LOW**, against the plan register's authored `medium`. The disagreement
  is stated in the measurement record rather than resolved silently.
- **Measurement 2's severity is MEDIUM**, not high and not low, with both the aggravating and the
  mitigating property named in the same sentence as the criterion requires.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] The prompt-path fixtures are FLAT, not `@Nested`**
- **Found during:** Task 1
- **Issue:** The task's action text says to add the fixtures "in a nested class whose name says these
  guard the PROMPT PATH". JUnit writes each `@Nested` inner class to its own
  `TEST-<outer>$<Inner>.xml`. The SAME task's acceptance criterion 2 requires all five fixture groups
  present BY NAME in `TEST-…ParameterCarrierRedactionTest.xml` — unsatisfiable against a nested layout
  no matter how green the suite is. This is the `WINDOWS.md` 11/13/14/15 class: a criterion counting a
  population the artifact does not contain. 27-07 hit the identical conflict and recorded it as its own
  deviation 1; re-nesting now would have re-opened it.
- **Fix:** One flat class, one XML, one decidable gate. The grouping the plan wanted is carried by the
  `promptPath…` method-name prefix and by a banner comment that states the reason, so a future reader
  does not "tidy" it back.
- **Files modified:** `ParameterCarrierRedactionTest.kt`
- **Verification:** all seven methods present by name in the single expected XML, `failures="0"`.
- **Committed in:** `a2edf9f`

**2. [Rule 3 - Blocking] The registry is keyed on a (file, accessor) PAIR, not on a bare path**
- **Found during:** Task 2
- **Issue:** The task's action text says to declare `ROUTED_THROUGH` and `CLASSIFIED_NON_CARRYING` as
  maps from `path -> …`. Measured, **6 of the 11 carrier files have accessors with DIFFERENT
  dispositions** — `McpToolExecutorImpl` alone routes its headers through `sanitizeHeaders`, its
  parameters through `sanitizeParameters`, its raw messages through the `redactIfNeeded` choke point,
  and mode-gates its cookie jar. A path-only key forces those four answers into one string and makes
  assertion (1)'s "exactly one of the two maps" meaningless for exactly the files that matter most.
- **Fix:** The key is `CarrierSite(path, accessor)`. Assertion (1) still reads "exactly one of the two
  maps" and is now decidable per accessor. The residual granularity limit — a pair can still aggregate
  calls that split, as `McpToolExecutorImpl`'s three `parameters()` calls do — is stated in the KDoc as
  a FIFTH, explicitly weaker bound, separated from the four axis bounds rather than averaged into them.
- **Files modified:** `CookieCarrierInventoryTest.kt`
- **Verification:** all four assertions green; red probes A, B and C each fail the assertion the plan
  named.
- **Committed in:** `3125f0f`

---

**Total deviations:** 2 auto-fixed, both blocking. Both make a stated acceptance criterion decidable
that was otherwise unsatisfiable. No scope added, no coverage removed, no plan decision reopened.

## Issues Encountered

**`ktlintCheck` rejected the first draft of the registry's continuation-string indentation** (24 where
it wanted 20, 40+ lines). Resolved with `./gradlew ktlintFormat`, which touched only the new file;
`git status` after the format showed exactly one entry. No semantic change.

**No `RedactionTest` wall-clock flake occurred.** The known `SafeRegex` 50 ms deadline flake did not
fire on any run, including the full gate. The deadline was not widened and `RedactionTest.kt` stayed on
a zero-line diff throughout.

## Verification Results

| Check | Result |
|---|---|
| `JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test detekt ktlintCheck` | **BUILD SUCCESSFUL in 2m 53s** |
| Full suite, aggregated from every `TEST-*.xml` | **172 classes, 1217 tests, 1 skipped, 0 failures, 0 errors** |
| `ParameterCarrierRedactionTest` XML | `tests="25" skipped="0" failures="0" errors="0"` |
| `CookieCarrierInventoryTest` XML | `tests="4" skipped="0" failures="0" errors="0"` |
| `CookieHeaderRuleOwnershipTest` XML | `tests="3" skipped="0" failures="0" errors="0"` |
| `SerializedEmissionSiteInventoryTest` XML | `tests="5" skipped="0" failures="0" errors="0"` |
| `RedactionTest` XML | `tests="46" skipped="0" failures="0" errors="0"` |
| Accessor counts re-measured before any constant written | all 5 totals and all 11 per-file rows match the baseline |
| Red probes for task 1 (1) and task 2 (A, B, C) + non-vacuity ×2 | all run, each naming the specific assertion |
| `git diff HEAD -- redact/RedactionTest.kt \| wc -l` | **0** |
| Prior suites unedited (`SerializedEmission*`, `LogicalLineBoundaryScopeTest`, `CookieHeaderNameParityTest`, `McpToolHelpersTest`, `CookieHeaderRuleOwnershipTest`) | `git diff --stat` → no entries |
| `TODO`/`FIXME`/`XXX`/`HACK`/`PLACEHOLDER` introduced | **0** in every touched file |
| Neither probe committed under `src/` | verified BY PATH; the one `*Probe*` hit is phase 20's `McpSupervisorProbeTest.kt`, tracked and unmodified |
| Working tree | clean; no untracked or deleted files |

## Known Stubs

**None.** No stub, placeholder, skipped test or unrun `<verify>` was introduced by this plan. Every
`<verify>` command in the plan was executed and its result is recorded above.

**No `.planning/WINDOWS.md` entry was appended, and the reason is stated rather than left as silence.**
The ledger's kinds are `stub | todo | fixme | skipped-test | lint-warning | unmet-truth | unrun-verify
| deviation`, and this plan produced none of the first seven. The two deviations are recorded above in
full. The two measured residuals are not defects introduced here — they are plan-mandated TRANSFERS
that `T-27-08-06` and `T-27-08-07` route to plan 27-09, which owes them a `26-SECURITY.md` entry and, for
`AR-27-08`, a named successor. `WINDOWS.md` is additionally a shared cross-phase file that the
orchestrator owns in worktree mode.

## User Setup Required

None — no external service configuration required.

## Next Phase Readiness

Plan 27-09 can consume the following directly from this document, without re-deriving any of it:

1. **`AR-27-08`, severity MEDIUM** — the `AuditIssue.detail()` transitive carrier. Measured result,
   firing positive control, mechanism, and source-cited reachability all above. Both transfer targets
   `T-27-08-06` names are still owed: the `26-SECURITY.md` entry at this severity, AND a **named
   successor** (a ROADMAP phase entry or an explicit wave 10) that owns the fix, so the transfer has an
   owner rather than being a deferral into a document nobody opens.
2. **`T-27-08-07`, measured severity LOW** — the non-cookie parameter-type result, with its firing
   attribution control. Note the recorded disagreement with the plan register's authored `medium`.
3. **`InjectionPointExtractor.kt:29`** — byte-unchanged, classified NON-CARRYING with both consumers
   read, and deferred WITH the issue-detail route. It must be closed by the SAME successor: fixing the
   predicate alone would produce a tidier file and an unchanged leak.
4. **The inventory's measured counts** — 25 / 15 / 11 / 19 / 2, 72 sites, 11 files — for
   `26-SECURITY.md`'s standing-rule clause.
5. **The four named blind axes plus the fifth granularity bound**, quoted verbatim above, for the
   human-verification file. **Axis 1 is the named next blind axis** and is what would force the primary
   noun to be re-promoted from "a carrier of cookie bytes" to "a value that entered the process".
6. **`T-27-07-04` stays `medium`** — unchanged by this plan; `BountyPromptTagResolver` remains
   uninstantiated in `src/main/kotlin`.

**Open human item, unchanged:** the live-Burp Montoya confirmation (coverage D7), carried over from
`27-VERIFICATION-2.md` and `27-07`. It should stay routed to `27-HUMAN-UAT.md` where it remains legible
as unanswered.

## Self-Check: PASSED

- `src/test/kotlin/com/six2dez/burp/aiagent/redact/CookieCarrierInventoryTest.kt` — FOUND on disk
- `src/main/kotlin/com/six2dez/burp/aiagent/redact/Redaction.kt` — FOUND, comment narrowed, pattern byte-identical
- `src/test/kotlin/com/six2dez/burp/aiagent/mcp/tools/ParameterCarrierRedactionTest.kt` — FOUND, 25 tests
- Commits `a2edf9f`, `3125f0f`, `dc2f1c9` — all FOUND in `git log` on `worktree-agent-acbe585dbf161398a`
- No probe file under `src/`; working tree clean
- `STATE.md` and `ROADMAP.md` NOT modified — the orchestrator owns those in worktree mode

---
*Phase: 27-priv-05-gap-closure-sanitize-headers*
*Completed: 2026-08-25*
