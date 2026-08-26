---
phase: 27-priv-05-gap-closure-sanitize-headers
verified: 2026-08-26T12:17:42Z
status: gaps_found
score: 29/33 must-haves verified
behavior_unverified: 0
overrides_applied: 0
re_verification:
  previous_status: gaps_found
  previous_score: 12/15
  gaps_closed:
    - "27-02's 'wider on the redacting side is fail-safe' — the underscore cookie-header-name class is CLOSED at the control. `COOKIE_NAME_PART = \"[A-Za-z0-9_-]*\"`; `my_cookie` / `X_Cookie` / `session_cookie` measured `[STRIPPED]` under STRICT and BALANCED against the shipped compiled classes. Consumer polarity is now stated in the predicate KDoc AND at the admitting call site."
    - "27-05's 'never pinned by a green test' — both `assertTrue(...contains(\"api.example.com\"))` STRICT host pins are GONE from `McpToolHelpersTest.kt`; the pass-through is re-pointed at `offLeavesBothSerializedShapesByteIdentical`, a real `assertEquals` byte-identity fixture under `PrivacyMode.OFF` (`redactIfNeeded` has no OFF short-circuit, so it is non-vacuous)."
    - "27-08's 'no green test asserting a cookie value SURVIVES a redacting policy exists under src/' — independently re-swept with a detector WIDER than the shipped one (any modifier, any annotation, backtick names). Zero real survival pins on the current tree; the only hits are the seven `Sentinel.BENIGN_CONTROL` negative controls and one benign `wibble` pass-through control."
    - "The JSON-string-open carrier — a canonical `Cookie:` header that is the FIRST content of a JSON string value is now stripped under STRICT and BALANCED on the real `HttpRequestResponse.notes` shape, with the positive control firing in the same run."
  gaps_remaining: []
  regressions:
    - "NEW, INTRODUCED BY THIS ROUND: `JSON_STRING_OPEN = \"\\\"\"` makes ANY double quote a logical-line start. Measured on the shipped compiled classes — 1589 of 1714 characters of a realistic serialized MCP tool result destroyed. Round-3 state produced NO MATCH on the same input."
  new_gaps:
    - "The round-4 boundary widening is a bare quote, not a JSON string open — a content-destruction regression on the shipped emission path, undocumented in source and in every record, and gated by no test"
    - "`RedactingPolicySurvivalSweepTest.FUNCTION_DECLARATION` is structurally blind to 136 of 1779 `fun` declaration lines, 67 of them backtick-named `@Test` methods across 9 files — so 'fails CI on the next such pin' is false for the more idiomatic Kotlin naming style"
gaps:
  - truth: "27-11: 'BOUND, carried with the claim. The boundary now recognises three logical-line starts: a real line start, an escaped newline, and a JSON string open.'"
    status: failed
    reason: >-
      The third start is NOT a JSON string open. `JSON_STRING_OPEN = "\""` (`Redaction.kt:277`) is a
      BARE DOUBLE QUOTE, and `logicalLineHeaderRule` composes it as `(?<=")`. A double quote is not a
      JSON string open — it also occurs in HTML attributes, JS string literals, quoted CSV fields and
      ordinary prose, all of which reach `Redaction.apply` through SEVEN consumers
      (`McpToolContext.redactIfNeeded`, `ContextCollector` x2, `McpToolExecutorImpl:1018`,
      `BountyPromptTagResolver` x2, `PassiveAiScannerPrompts:49`). The truth's own final sentence —
      "Nothing here may be read as 'the boundary is complete'" — is honoured for the MISSING fourth
      start; what is not honoured is the cost of the start that was ADDED. It is stated nowhere: a
      `grep -i "over-match|html|attribute|CSV|prose|bare quote"` over `Redaction.kt` returns no line
      describing it, and the only "over-match" in `27-11-SUMMARY.md` refers to the TAIL running past
      a closing quote, which is a different property.
      MEASURED INDEPENDENTLY against the shipped compiled classes
      (`build/classes/kotlin/main`, JDK 21, `Redaction.INSTANCE.apply(blob,
      RedactionPolicy.fromMode(mode), "probe-salt", false)`), STRICT and BALANCED identical:
        IN : <div title="Cookie: we use cookies for analytics. Accept?">text</div>
        OUT: <div title="Cookie: [STRIPPED]">text</div>
        IN : foo("cookie: analytics", KEEPME);          OUT: foo("cookie: [STRIPPED]", KEEPME);
        IN : 1,"x-cookie: none",KEEPTAIL                OUT: 1,"x-cookie: [STRIPPED]",KEEPTAIL
        IN : See the docs: "authorization: Bearer required" and KEEPTAIL
        OUT: See the docs: "authorization: [REDACTED]" and KEEPTAIL   <- authHeaderRegex inherits it too
      MUCH WORSE ON THE PRIMARY SHIPPED SHAPE, and this goes beyond what `27-REVIEW-2.md` reported.
      Inside a serialized JSON tool result an escaped quote `\"` is consumed ATOMICALLY by the tail's
      `(?:\\.|[^"\\])+?` alternation, so the tail cannot terminate on any escaped quote and runs to
      the JSON string's real closing quote. On a realistic `proxy_http_history`-shaped payload
      carrying an HTML response body:
        IN  length = 1714   OUT length = 125   CHARS DESTROYED = 1589 (93%)
        "IMPORTANT-CONTENT" occurrences  IN=40  OUT=0
        OUT: {"url":"https://shop.example/faq","response":"HTTP/1.1 200 OK\r\n\r\n<div title=\"Cookie: [STRIPPED]","notes":"analyst note"}
      The JSON stays STRUCTURALLY VALID (the sibling `notes` field is byte-identical), which is
      precisely why `assertSameJsonShape` and
      `aMatchBeginningAtAJsonStringOpenStopsAtThatStringsClosingQuote` do not catch it: that gate's
      fixture puts the cookie value LAST in its string, so there is no content between the value and
      the terminator to destroy. The model receives a silently truncated response body.
      IT IS NEW TO THIS ROUND, not pre-existing. Rebuilding the composed regex with the round-3 start
      set (`(?<=\\[rn])` only) and the round-4 name class, on the same fixtures:
        BEFORE (round-3 state)   -> NO MATCH on all five non-JSON shapes and on the 1714-char payload
        SHIPPED round 4          -> MATCH, consumed 1607 chars on the 1714-char payload
        PROPOSED (?<=:")         -> NO MATCH on all five; STILL MATCHES both `notes` carrier cases
      By this codebase's own standard (`Redaction.kt:570-576`, "removing analytically load-bearing
      content from a security prompt is a functional regression") this is a regression, and it is the
      one class of defect this phase has NOT previously produced: every earlier failure was
      fail-open; this one fails safe for privacy and destroys correctness instead.
    artifacts:
      - path: "src/main/kotlin/com/six2dez/burp/aiagent/redact/Redaction.kt:277"
        issue: "`private const val JSON_STRING_OPEN = \"\\\"\"` — a bare double quote presented as 'the open of a JSON string value'. The KDoc names its width and its declaration order and says nothing about what else a bare quote matches."
      - path: "src/main/kotlin/com/six2dez/burp/aiagent/redact/Redaction.kt:214-217"
        issue: "the 27-11 rationale block lists start 3 as 'a JSON STRING OPEN `(?<=\")`' and states only the MISSING fourth start's cost. D-27-15's over-match for start 2 gets a dedicated paragraph three lines above; start 3's gets none, in a file whose stated discipline is stating costs where the reader meets the rule."
      - path: "src/main/kotlin/com/six2dez/burp/aiagent/redact/Redaction.kt:312-317"
        issue: "`logicalLineHeaderRule` feeds the bare-quote lookbehind to ALL THREE composed rules — `cookieHeaderRegex`, `setCookieHeaderRegex` and `authHeaderRegex` — so one unstated cost is inherited three times."
      - path: "src/test/kotlin/com/six2dez/burp/aiagent/mcp/tools/SerializedEmissionRedactionTest.kt:456-538"
        issue: "the two over-match gates bound the JSON case only. `aMatchBeginningAtAJsonStringOpenStopsAtThatStringsClosingQuote` uses a fixture whose cookie value is the LAST content of its string, so it cannot observe destruction of content BETWEEN the value and the terminator; `theHeaderMapShapeIsStillOutOfTheComposersReach` bounds JSON object keys, which are structurally immune. Neither touches non-JSON text."
      - path: ".planning/phases/26-coverage-static-analysis-debt-docs/26-SECURITY.md"
        issue: "no AR-* row, no clause and no evidence section records this over-match. The round-4 residual list in `26-SECURITY.md` and `ROADMAP.md` names SIX residuals and this is not among them."
    missing:
      - "Narrow the start to a JSON string VALUE open — `private const val JSON_STRING_OPEN = \":\\\"\"`. Still FIXED-WIDTH (two characters), so the composer's measured 2.4x cost model is preserved verbatim. Independently confirmed on the same probe: it removes ALL five non-JSON false positives and the 1714-char destruction, and KEEPS both `notes`-carrier cases closed (`{\"notes\":\"Cookie: …` and `[{\"notes\":\"Cookie: …`)."
      - "State the narrowing it brings with it as a named residual: a header at the open of a JSON ARRAY ELEMENT string (`[\"Cookie: …\"]`) is then not a recognised start — measured MATCH today, NO MATCH under the proposal."
      - "Add a negative gate to `JsonStringOpenBoundary` pinning an HTML-attribute payload byte-identical under STRICT, and a positive gate on a JSON string carrying content AFTER the cookie value inside the SAME string, so the tail's real blast radius is measured rather than the degenerate last-content case."
      - "OR, if `(?<=\\\"\")` is deliberately kept: write the over-match into the 27-11 rationale block with the rigour D-27-15 got, open it as a numbered AR-* finding with the quoted 1589-character measurement, and gate its blast radius by test. Silently keeping it is the one option the standing rules forbid."
  - truth: "27-12: 'The claim above is MACHINE-CHECKED, not asserted a second time. RedactingPolicySurvivalSweepTest scans src/test/kotlin and fails CI on a new survival pin.'"
    status: failed
    reason: >-
      Independently reproduced, and the reviewer's numbers are exact.
      `FUNCTION_DECLARATION = Regex("^\\s*(private |internal )?fun (\\w+)\\(")`
      (`RedactingPolicySurvivalSweepTest.kt:569`) is the gate on `detect()` — a line that does not
      match causes `return@forEachIndexed`, so the whole function body is never scanned. That regex
      admits exactly one optional modifier and requires a `\w+` name.
      MEASURED over `src/test/kotlin` (151 `.kt` files): of 1779 function-declaration lines, 136 are
      INVISIBLE to the sweep, and 67 of those are backtick-named `@Test` methods across 9 files,
      including `redact/SecretTripwireHooksTest.kt` (13) — a file in the redaction package itself.
      Top files: HttpBackendCircuitFailureTest 14, McpSupervisorConnectionTest 13,
      SecretTripwireHooksTest 13, AgentProfileLoaderTest 10, AgentSupervisorRestartPolicyTest 10,
      HttpBackendTransportRoutingTest 9.
      FALSIFIED BY EXECUTION, not by reading. A faithful re-implementation of the shipped detector
      (same vocabulary, same markers, same position/negation/benign rules, same indent-based
      isolation, same raw-string skip) run against a synthetic survival pin in six declaration
      shapes:
        fun `cookie value survives strict`()  -> hits=[]        MISSED
        @Test fun sameLinePin()               -> hits=[]        MISSED
        suspend fun suspendPin()              -> hits=[]        MISSED
        public fun publicPin()                -> hits=[]        MISSED
        override fun overridePin()            -> hits=[]        MISSED
        fun plainPin()      (control)         -> hits=[('plainPin', '"sentinelleak"', 0)]   FOUND
      The sweep genuinely fires on the plain-named form — I reproduced its EXACT 3 hits against the
      pre-round contents of the two files at `git show 09e9cae` (the two host pins plus the
      underscore pin) and its 0 on the current tree. The defect is that its scope claim covers a
      naming style 3.8% of current test methods already use and 100% of a future author might.
      This is the register-wider-than-the-control defect reproduced INSIDE the artifact written to
      stop it, which is the reason it is a blocker and not a warning.
    artifacts:
      - path: "src/test/kotlin/com/six2dez/burp/aiagent/redact/RedactingPolicySurvivalSweepTest.kt:569"
        issue: "`FUNCTION_DECLARATION` admits only `private `/`internal ` and a `\\w+` name — no backtick names, no same-line annotation, no `suspend`/`public`/`open`/`override`/`protected`/`inline`."
      - path: "src/test/kotlin/com/six2dez/burp/aiagent/redact/RedactingPolicySurvivalSweepTest.kt:370-378"
        issue: "`detect()` returns early on a non-matching declaration line, so an unmatched declaration makes its ENTIRE body invisible — not merely unnamed."
      - path: "src/test/kotlin/com/six2dez/burp/aiagent/redact/RedactingPolicySurvivalSweepTest.kt:22"
        issue: "the class KDoc states 'it scans src/test/kotlin on every CI run and fails on the next such pin' without qualification."
    missing:
      - "Widen the regex to accept any modifier prefix, an optional same-line annotation and BOTH name spellings, e.g. `Regex(\"^\\\\s*(?:@\\\\w+(?:\\\\([^)]*\\\\))?\\\\s+)*(?:\\\\w+\\\\s+)*fun\\\\s+(?:<[^>]*>\\\\s*)?(?:(\\\\w+)|`([^`]+)`)\\\\s*\\\\(\")`, taking the identifier from group 1 falling back to group 2."
      - "Add a non-vacuity test asserting the backtick and modifier forms ARE flagged — same shape as `theBlankLineHazardFixtureIsIsolatedWholeIncludingItsBlankLines`, which is the right precedent."
      - "Re-run the 3-hit pre-round evidence and the 0-hit tree scan after widening, and record whether the widened regex surfaces anything new on the current tree (my wider re-implementation found nothing beyond the seven `Sentinel.BENIGN_CONTROL` controls and one benign `wibble` pass-through control, so a widening is expected to stay at 0 today)."
      - "If the narrow regex is kept deliberately, the declaration-shape blindness must be added to the numbered blind-axis list, the 'fails CI on the next such pin' sentence must be scoped, AND `26-SECURITY.md` standing-rule clause (vi) must be amended with it — but that is the weaker option, because 67 live methods are already outside the scan."
  - truth: "27-12: 'BOUND, carried with the claim. … THE NEXT BLIND AXES, NAMED: [eleven axes] … It is a TRIPWIRE OVER A MEASURED VOCABULARY and its KDoc must say so before its first assertion.'"
    status: failed
    reason: >-
      The KDoc does say so, and the eleven axes it names are real and honestly stated. The bound is
      nevertheless FALSE AS A STATEMENT OF WHAT THE SWEEP CANNOT SEE, because a TWELFTH axis is live
      on the current tree and is not among them: the FUNCTION-DECLARATION SHAPE (gap 2 above). All
      eleven listed axes are properties of the ASSERTION or the VALUE; none is a property of the
      DECLARATION LINE, and the declaration line is the gate everything else is downstream of.
      A SECOND, WEAKER OMISSION, measured and recorded here rather than left for a later round: the
      composition `fileWalk` -> `detect` has NO POSITIVE GATE. Both `fileWalk` call sites
      (`:96` the tree scan, `:118` the self-scan) assert an EMPTY hit set; every assertion that
      proves the detector can produce a hit bypasses the walk via `.lines()` / `readLines()`
      (`:132`, `:160`, `:177`, `:188`, `:253`, `:265`, `:284`). The file's own comment at `:347-354`
      names the silent-blanking direction as "the dangerous direction" and then does not assert
      against it. I measured the current state — 625 lines blanked tree-wide, 0 of 151 files ending
      in the INSIDE state — so the walk is not vacuous TODAY. That is a measurement, not a guarantee,
      and it is exactly the property this file exists to stop relying on.
      A third, narrower one, unmeasured by me but consistent with the source at `:437-449`: the
      negation rule computes `negated` from the `assertTrue(` opener to the `.contains(` under test,
      so in a compound assertion whose FIRST operand is negated every subsequent `.contains(`
      inherits `negated = true`. The KDoc documents the rule as covering `assertTrue(!x.contains(v))`
      only. Not among the eleven either.
    artifacts:
      - path: "src/test/kotlin/com/six2dez/burp/aiagent/redact/RedactingPolicySurvivalSweepTest.kt:30-83"
        issue: "the eleven-axis enumeration presented as the file's integrity claim omits the declaration-shape axis (live, 136 declarations / 67 backtick `@Test` methods), the ungated walk-preserves-real-code direction, and the compound-assertion negation over-fire."
      - path: ".planning/phases/26-coverage-static-analysis-debt-docs/26-SECURITY.md:923+"
        issue: "standing-rule clause (vi) INHERITS the claim — 'Its own KDoc names ELEVEN things it cannot see before its first assertion' — so the register now carries a bound wider than its control, which is the precise defect clause (vi) was written to stop. Amending the sweep requires amending this clause in the same change."
    missing:
      - "Add the declaration-shape axis to the enumeration, or (preferred) close it per gap 2 and leave the enumeration at eleven honest axes."
      - "Add ONE composition test binding the walk to the detector in the FLAGGING direction: a raw-string fixture (must be blanked) FOLLOWED by a real-code survival pin (must survive the walk and be flagged), asserting exactly 1 hit. Zero hits then means the walk has started blanking real code — today that failure ships green."
      - "Consider `if (inside) throw AssertionError(\"unbalanced triple quotes in $path\")` at the end of `dropRawStringInteriors`, converting the silent-blindness mode into a red test."
      - "State the compound-assertion behaviour of the negation rule, or scope `assertsPresenceAt` to the receiver of the `.contains(` under test."
      - "Amend `26-SECURITY.md` clause (vi)'s 'ELEVEN' sentence in the same change as the sweep."
  - truth: "27-13: 'BOUND, carried with the claim. This round closed TWO axes … THE NEXT BLIND AXES, NAMED: AR-27-09, AR-27-10, AR-27-04, AR-27-08, the CONCERNS.md vendor auth-header class, and the sweep's own vocabulary bound. Six named residuals is not a completeness claim.'"
    status: failed
    reason: >-
      The six named residuals are each real, correctly severity-assigned and correctly owned — I
      re-measured two of them independently and both hold exactly as recorded (AR-27-09: the
      indented header survives BYTE-UNCHANGED under STRICT *and* BALANCED; AR-27-10: all thirteen
      remaining tchars are admitted by `isCookieHeaderName` and leak under STRICT). The failure is
      not in what is named; it is that the list omits BOTH residuals THIS ROUND CREATED:
      (a) the bare-quote logical-line start over-match (gap 1) — a shipped correctness regression on
      the primary emission path, in no record anywhere; and
      (b) the sweep's declaration-shape blindness (gap 2) — which the register affirmatively
      MISSTATES, because clause (vi) cites the sweep's eleven-axis KDoc as the check's stated bound.
      A round whose central lesson is "a stated bound wider than its control is the defect" closed
      with a stated bound wider than its control in two places. The list is a residual list for what
      was INHERITED, not for what was INTRODUCED, and nothing in the record marks that distinction.
    artifacts:
      - path: ".planning/ROADMAP.md"
        issue: "the amended round-4 residual paragraph enumerates six residuals, none of them introduced by round 4."
      - path: ".planning/phases/26-coverage-static-analysis-debt-docs/26-SECURITY.md:64-91"
        issue: "the `threats_open` population paragraph asks 'the question that population forces' for the two findings round 4 OPENED (AR-27-09, AR-27-10) and never asks it for the two round 4 INTRODUCED."
    missing:
      - "Open the bare-quote over-match as a numbered AR-* finding with its 1589-character measurement quoted, or fix it per gap 1 and record the fix."
      - "Record the sweep's declaration-shape blindness wherever clause (vi) currently asserts the eleven-axis bound."
      - "Re-run the documented `awk` recomputation afterwards and restate the population, as this round already does correctly for AR-27-09 / AR-27-10."
deferred:
  - truth: "AR-27-08 — a COOKIE-typed injection point's value reaching `scanner_issues` through `AuditIssue.detail()`, surviving STRICT and BALANCED"
    addressed_in: "Phase 28"
    evidence: "ROADMAP.md Phase 28 goal names AR-27-08 explicitly with its mechanism and reachability; round 4 does not touch it and says so."
  - truth: "`InjectionPointExtractor.kt:29`'s unconverted `it.type().name == \"COOKIE\"` predicate"
    addressed_in: "Phase 28"
    evidence: "ROADMAP.md Phase 28: 'This phase closes AR-27-08 AND InjectionPointExtractor.kt:29 TOGETHER, and that pairing is the point.'"
  - truth: "Vendor auth headers outside `authHeaderRegex`'s 16-name alternation leak under STRICT"
    addressed_in: "CONCERNS.md (open, recorded, out of phase-27 scope by prohibition)"
    evidence: "27-05 prohibition, restated in round 4's residual list as the CONCERNS.md vendor auth-header class."
  - truth: "AR-27-09 — the leading-whitespace / obs-folded logical-line start"
    addressed_in: "26-SECURITY.md AR-27-09 (open, LOW, owned, with its one-token fix written down)"
    evidence: "Re-measured this run against the shipped classes: `GET / HTTP/1.1\\r\\n Cookie: a=SECRET5\\r\\n\\r\\n` unchanged under STRICT and under BALANCED. Deliberately out of round-4 scope by maintainer decision."
  - truth: "AR-27-10 — the thirteen RFC 9110 tchars outside the widened cookie name class"
    addressed_in: "26-SECURITY.md AR-27-10 (open, LOW, owned)"
    evidence: "Re-measured this run: all 13 of `! # $ % & ' * + . ^ ` | ~` produce `isCookieHeaderName = true` and a STRICT leak. Partition pinned in source by `CookieHeaderNameWidthTest`, which reads `COOKIE_NAME_PART` out of `Redaction.kt` at test time."
behavior_unverified_items: []
coincidental_reliance_items: []
human_verification:
  - test: >-
      DECIDE the disposition of the bare-quote logical-line start (gap 1). Either narrow
      `JSON_STRING_OPEN` to `":\""` — measured to remove every false positive while keeping both
      `notes`-carrier cases closed, and to cost only the JSON-array-element start — or keep `"` and
      accept a measured 93% content-destruction blast radius on the primary MCP emission path.
    expected: >-
      A recorded human decision. If kept, it must be written into the 27-11 rationale block with the
      rigour D-27-15 got, opened as a numbered AR-* finding with the 1589-character measurement
      quoted, and gated by a test pinning its blast radius.
    why_human: >-
      A trade between privacy fail-safety and analytic correctness on a shipped 1.0.0 release, on the
      same rule this round rewrote. `Redaction.kt:570-576` already records the maintainer's position
      that destroying analytically load-bearing content is a functional regression; this is the same
      judgement with the sign reversed.
  - test: >-
      CARRIED FORWARD, all eleven items of `27-HUMAN-UAT.md` are recorded as unanswered and remain
      so: items 1-8 (live-Burp reproduction and re-test; Montoya `parameters()` yielding COOKIE
      entries in-process; the `T-27-06-06` README/SPEC host-anonymisation overclaim; live
      confirmation of the wave-7 parameter fix; the AR-27-08 route with its scan preconditions; the
      AR-27-07 and AR-27-08 severity dispositions), plus items 10 and 11 (the AR-27-09 and AR-27-10
      dispositions opened this round).
    expected: "As stated per item in `27-HUMAN-UAT.md`. That file already records them as unanswered and must stay legible as such."
    why_human: "Needs a live Burp instance, real proxy traffic, a real MCP client, and maintainer risk decisions."
  - test: >-
      DECIDE AR-27-04 with a HUMAN in the loop — item 9. Round 4 correctly did NOT relitigate it: the
      finding is unchanged at MEDIUM, `hostHeaderRegex` is still excluded from the composer,
      `maybeAnonymizeUrl` is still not threaded into the two serializers, and the register's appended
      note states plainly that removing the two green pins is a test-artifact repair supplying no
      human judgment.
    expected: "Either the fix (host rule through the composer AND `maybeAnonymizeUrl` threaded into `toSiteMapEntry` / `toSerializableForm`) or a maintainer-signed acceptance replacing the `mode: yolo` auto-selection."
    why_human: "A privacy-control bypass on a shipped release posture, currently accepted by the harness rather than by a person."
  - test: >-
      CONFIRM the provenance claim that both round-4 decisions (widen the name class; put the
      JSON-string-open boundary in scope) were made by the MAINTAINER before planning, not
      auto-selected by `mode: yolo`.
    expected: "Maintainer confirmation. `.planning/config.json` still carries `mode: yolo`, so the record's contrast with AR-27-04 rests on an assertion no artifact can corroborate."
    why_human: "Process provenance is not observable in the codebase; the record is honest about the distinction but cannot itself establish it."
---

# Phase 27: PRIV-05 Gap Closure — Verification Report (Round 4)

**Phase Goal (as widened by its own gap-closure rounds):** Close PRIV-05's cookie carriers — the
`sanitizeHeaders` name-variant parity (round 1), the serialized raw-HTTP-in-JSON emission carrier
(round 2), and the COOKIE-typed HTTP parameter carrier (round 3).
**Verified:** 2026-08-26T12:17:42Z
**Status:** gaps_found
**Re-verification:** Yes — fourth round, after 7/9, 8/9 and 12/15.

---

## Headline

**Round 4 closed all three of round 3's failed must-haves. I confirmed each by measurement, not by
reading the summaries.** The underscore cookie-header-name class is fixed at the control and
`my_cookie` / `X_Cookie` / `session_cookie` are now `[STRIPPED]` under STRICT and BALANCED against
the shipped compiled classes. The two prohibited `assertTrue(...contains("api.example.com"))` STRICT
host pins are gone and their pass-through is re-pointed at a genuine `assertEquals` byte-identity
fixture under `PrivacyMode.OFF`. And the "no green survival pin under `src/`" claim now HOLDS as a
fact — I re-swept the tree with a detector deliberately WIDER than the shipped one and found no real
survival pin, only the seven `Sentinel.BENIGN_CONTROL` negative controls and one benign `wibble`
pass-through control. The JSON-string-open carrier the maintainer newly scoped is closed too, with
its positive control firing on the same run.

**The record work is the strongest in this phase's history and I could falsify none of it.**
T-26-02-01 clause (6) is appended with clauses (1)-(5) preserved as a byte-exact character prefix (I
diffed the row and checked). `threats_open` recomputes to `0` over 46 rows by the documented `awk`
command, which I re-ran. `REQUIREMENTS.md` is untouched by round 4 — 0 changes across the whole
range — and PRIV-05 stays `[ ]`. `ROADMAP.md` re-confirms "PHASE 27 COMPLETES WITH PRIV-05 NOT
SATISFIED" for the fourth round. The two new residuals AR-27-09 and AR-27-10 are numbered, owned,
severity-assigned, and I re-measured both: the indented header survives byte-unchanged under STRICT
*and* BALANCED (one mode wider than round 3 recorded, exactly as the register now says), and all
thirteen remaining tchars are admitted-and-unmatched. `WINDOWS.md` even ledgers the plan's own
falsified projections with the observed numbers beside them.

**But round 4 introduced a defect of a kind this phase has not produced before, and did not name
it.** Every prior failure was fail-OPEN. This one fails safe for privacy and breaks correctness
instead: `JSON_STRING_OPEN` is a bare `"`, not a JSON string open, so **any** double quote is now a
logical-line start. I measured **1589 of 1714 characters destroyed** on a realistic serialized MCP
tool result carrying an HTML response body — the entire response body after the first quote-preceded
`cookie:`, with the JSON left structurally valid so every shape assertion still passes. The same
input produced **NO MATCH** in the round-3 state. It is in no source comment, no summary and no
security record, and no test gates it. That is a blocker.

**And the mechanism written to make the third claim durable is blind on an axis it does not
enumerate.** `RedactingPolicySurvivalSweepTest` genuinely fires — I reproduced its exact 3 hits
against the pre-round file contents at `git show 09e9cae` and its 0 on the current tree. But its
`FUNCTION_DECLARATION` regex cannot see 136 of 1779 declaration lines, 67 of them backtick-named
`@Test` methods across 9 files including `redact/SecretTripwireHooksTest.kt`. A survival pin written
in the more idiomatic Kotlin style ships green today. The claim "fails CI on the next such pin" is
false for that style, the axis is not among the file's eleven, and `26-SECURITY.md` clause (vi) now
cites those eleven as the check's stated bound — so the register carries a bound wider than its
control, which is verbatim the defect clause (vi) was written to stop.

**I confirmed all three `27-REVIEW-2.md` blockers independently and did not inherit its verdict on
any of them. On CR-03 my measurement is materially worse than the reviewer's**: the reviewer showed
inline mutation on non-JSON text; I additionally measured whole-body annihilation on the serialized
JSON path, which is the primary shipped surface and the one the existing over-match gate is
structurally unable to observe.

---

## Goal Achievement

### Observable Truths

Must-haves are taken from the `must_haves.truths` frontmatter of the four round-4 plans. ROADMAP.md
declares no Success Criteria block for Phase 27, so PLAN frontmatter is the contract, read alongside
the phase goal line and its recorded widenings.

#### Plan 27-10 — the underscore name class (7/7)

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | Underscore cookie-header names have their VALUE replaced by `[STRIPPED]` on the prompt path under STRICT and BALANCED, measured on the column-0 header-line shape | ✓ VERIFIED | Driven against `build/classes/kotlin/main` (JDK 21): `my_cookie` / `X_Cookie` / `session_cookie` → `<name>: [STRIPPED]`, `STRICTleak=false BALleak=false`. All six canonical variants unchanged-good on the same run. |
| 2 | No test under `src/` asserts the `my_cookie` value SURVIVES; the wave-2 assertion is INVERTED, not deleted | ✓ VERIFIED | `CookieHeaderNameParityTest:213-253` — `theUnderscoreNameClassIsStrippedByBothTheRegexesAndThePredicate` asserts `assertFalse(output.contains(sentinel))` plus `assertTrue(output.contains("$name: [STRIPPED]"))`. Corpus entry retained with its sentinel. |
| 3 | The one-directional parity invariant is unchanged, still asserted, still non-vacuous; the corpus grew and its floors grew with it | ✓ VERIFIED | `PARITY_CORPUS` = 19 entries (14 positive / 5 negative); floors `MIN_CORPUS_SIZE=18`, `MIN_PREDICATE_POSITIVES=12`, `MIN_PREDICATE_NEGATIVES=4`; `EXPECTED_UNDERSCORE_NAMES=3` is an EXACT count, not a floor. 3 tests green. |
| 4 | Where a reader MEETS the shared predicate, the claim names all three consumers AND EACH CONSUMER'S POLARITY | ✓ VERIFIED | `Redaction.kt:341-359` — the KDoc now lists redactor / redactor / ADMITTER explicitly and states "This paragraph previously claimed fail-safety UNCONDITIONALLY". `PassiveAiScannerFilters.kt:187-189` carries the polarity note at the admitting call site. |
| 5 | BOUND named: the thirteen remaining tchars are outside `COOKIE_NAME_PART`, enumerated in source, filed as AR-27-10 | ✓ VERIFIED | Probe: all 13 of `! # $ % & ' * + . ^ ` \| ~` give `pred=true, STRICTleak=true`. `CookieHeaderNameWidthTest.NOT_COVERED_TCHARS` exists; AR-27-10 row exists in `26-SECURITY.md:209`. |
| 6 | The enumeration has no silent expiry — `COVERED_TCHARS` is READ OUT OF `Redaction.kt` at test time and `NOT_COVERED_TCHARS` is derived as the complement | ✓ VERIFIED | `CookieHeaderNameWidthTest:159-197` reads the file, asserts exactly one `COOKIE_NAME_PART` declaration, expands the shipped class and `assertEquals(COVERED_TCHARS, expandCharClass(shipped))`. `NOT_COVERED_TCHARS = ALL_RFC9110_TCHARS - COVERED_TCHARS`. |
| 7 | This plan closes no requirement; PRIV-05 stays `[ ]` | ✓ VERIFIED | `REQUIREMENTS.md` untouched by the entire round-4 commit range. |

#### Plan 27-11 — the JSON-string-open boundary (6/7)

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | A canonical `Cookie:` header first in a JSON string value is stripped under STRICT and BALANCED on the real `notes` carrier | ✓ VERIFIED | Probe: `{"notes":"Cookie: a=SECRET1\r\nX: y"}` → `{"notes":"Cookie: [STRIPPED]\r\nX: y"}` in both modes. |
| 2 | The positive control fires on the same run | ✓ VERIFIED | Same run: `{"notes":"X: y\r\nCookie: a=SECRET9"}` → `Cookie: [STRIPPED]`. |
| 3 | The two lookbehinds are spelled SEPARATELY and each is FIXED-WIDTH | ✓ VERIFIED | `Redaction.kt:315` — `(?:(?<=\\[rn])\|(?<="))`, widths 2 and 1, non-capturing alternation of two separate lookbehinds. |
| 4 | Real multi-line behaviour is byte-identical: `RedactionTest` zero-line diff, and the two MEASURED pre-change constants still hold for the cookie AND auth families | ✓ VERIFIED | `git diff <round-4-base>~1 HEAD -- RedactionTest.kt` is EMPTY. `SHIPPED_REAL_MULTILINE_OUTPUT` (`:1130`) and `SHIPPED_REAL_MULTILINE_AUTH_OUTPUT` (`:1138`) both asserted with `assertEquals`; all `SerializedEmissionRedactionTest` nests green. |
| 5 | The composer's rule set is unchanged at exactly three; `hostHeaderRegex` stays excluded and stays AR-27-04 | ✓ VERIFIED | 4 non-comment occurrences of `logicalLineHeaderRule(` in `Redaction.kt`: 1 definition (`:312`) + 3 uses (`:98` auth, `:320` cookie, `:325` set-cookie). AR-27-04 unchanged at MEDIUM. |
| 6 | BOUND carried: the boundary now recognises three logical-line starts — a real line start, an escaped newline, and **a JSON string open** | ✗ FAILED | The third start is `"`, not a JSON string open. Measured content destruction on five non-JSON shapes and 1589/1714 characters on a realistic serialized tool result; NO MATCH in the round-3 state. Undocumented in source and every record; ungated. See gap 1. |
| 7 | This plan closes no requirement | ✓ VERIFIED | As above. |

#### Plan 27-12 — the survival-pin sweep (9/11)

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | No test under `src/` asserts a sensitive value SURVIVES STRICT or BALANCED; the two host pins are gone and the pass-through re-pointed at OFF | ✓ VERIFIED | Independent WIDER sweep (any modifier / annotation / backtick name) over 151 files: 0 real pins. The only two `api.example.com` `assertTrue` occurrences left in the tree are inside `HOST_PIN_FIXTURE`'s triple-quoted raw string (`:858`, `:894`). `offLeavesBothSerializedShapesByteIdentical` is a real `assertEquals` pair; `redactIfNeeded` has no OFF short-circuit so it is non-vacuous. |
| 2 | The claim is MACHINE-CHECKED — the sweep scans `src/test/kotlin` and **fails CI on a new survival pin** | ✗ FAILED | 136 of 1779 declaration lines invisible; 67 backtick-named `@Test` methods in 9 files. Falsified by execution against six declaration shapes: five missed, plain-named control found. See gap 2. |
| 3 | The sweep is proven to FIRE — non-vacuity tests against verbatim copies of the three removed pins and against the legitimate shapes it must NOT flag | ✓ VERIFIED | 11 tests green. Fixtures are verbatim slices; `EXPECTED_NEGATIVE_FIXTURES = 6`; `ANTI_SWALLOW_FIXTURE` / `NEGATIVE_ASSERT_FALSE_FIXTURE` are the flip-pair that floors the benign exclusion. |
| 4 | The ISOLATION UNIT is a whole `fun` body INCLUDING blank lines, asserted not assumed | ✓ VERIFIED | `functionBodyAt` (`:503-514`) terminates on the first non-blank line at indent ≤ declaration, never on a blank line. `theBlankLineHazardFixtureIsIsolatedWholeIncludingItsBlankLines` asserts ≥2 blank lines were CONSUMED. |
| 5 | The sweep SCANS ITSELF, clean, with NO self-file exclusion, and the reason is a stated MECHANISM with the 2-to-0 pair asserted | ✓ VERIFIED | `theSweepFileItselfYieldsNoHits` (walk → 0) paired with `theRawStringSkipIsWhyTheSelfScanIsClean` (no walk → ≥ `MIN_EXPECTED_UNSKIPPED_SELF_HITS = 2`). `theTreeWalkIsNonVacuous` asserts the walk reaches this file, so there is no exclusion. |
| 6 | The raw-string skip is proven not to have disarmed the detector — the SAME text handed DIRECTLY is still FLAGGED | ✓ VERIFIED | `theSkipHasNotDisarmedTheDetector` (`:172-181`) drives every positive fixture through `detect` directly and asserts non-empty. |
| 7 | The sweep is proven to have fired ON THE TREE, not only on copies — pre-round contents via `git show`, hit count recorded | ✓ VERIFIED | Independently reproduced: pre-round contents at `09e9cae` yield **exactly 3** hits — `McpToolHelpersTest#cookieVariantsAreStrippedEndToEndThroughRedactIfNeeded` × 2 (`"api.example.com"`, entry 3) and `CookieHeaderNameParityTest#thePredicateIsDeliberatelyWiderThanTheTwoRegexes` (`sentinel`, entry 2). |
| 8 | The VOCABULARY IS CONSTRUCTED WITH ITS EXCLUSIONS, never allowlisted after the fact; measured counts recorded as bounds | ✓ VERIFIED | `ALLOWLIST = emptyMap()`; `BENIGN_ACCESSORS` holds exactly one key; the position rule and negation rule live in `candidatesIn` / `assertsPresenceAt`, not in a list. Pre-round 3 and post-fix 0 both reproduced. The plan's projected 5/7/10 were corrected to the measured 7/9/0 and both divergences ledgered in `WINDOWS.md` entries 35 and 36 rather than reconciled by narrowing. |
| 9 | AR-27-04's disposition is NOT relitigated — open, same severity, still owed a human, measurement stays in `26-SECURITY.md` | ✓ VERIFIED | The AR-27-04 row carries an APPENDED note only: "THE FINDING ITSELF IS UNCHANGED: still OPEN, still at MEDIUM … removing a green pin is a test-artifact repair and supplies no human judgment." `hostHeaderRegex` untouched; host still survives STRICT in my probe. |
| 10 | BOUND carried: eleven named blind axes; a TRIPWIRE OVER A MEASURED VOCABULARY and its KDoc must say so before its first assertion | ✗ FAILED | The KDoc says so, but a TWELFTH axis is live and unnamed (declaration shape), plus the ungated `fileWalk`→`detect` flagging direction and the compound-assertion negation over-fire. See gap 3. |
| 11 | This plan closes no requirement | ✓ VERIFIED | As above. |

#### Plan 27-13 — the records, fourth time (7/8)

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | T-26-02-01 carries a SIXTH clause and clauses (1)-(5) survive as an exact character prefix | ✓ VERIFIED | Byte-level check: old row prose (18,352 chars) is an EXACT prefix of the new row (29,499 chars). Clause (6) opens "REOPENED A FOURTH TIME AND RE-CLOSED — 2026-08-26 … This threat has now been closed wrongly FOUR times". |
| 2 | AR-27-09 and AR-27-10 are numbered, severity-assigned, owned and evidenced from a MEASUREMENT taken this round | ✓ VERIFIED | Rows at `:208` and `:209`, evidence sections at `:454` and `:510`. Both re-measured independently: indented header byte-unchanged under STRICT *and* BALANCED; 13 tchars admitted-and-leaking. The register correctly separates AR-27-10's measured half (the partition, source-pinned) from its inferred half. |
| 3 | `threats_open` is RECOMPUTED by the documented command and its POPULATION is restated | ✓ VERIFIED | Re-ran the frontmatter `awk`: `open_high_or_critical=0 rows_scanned=46`. The population paragraph is restated verbatim rather than cross-referenced, and the "question that population forces" is asked and answered for both new findings. |
| 4 | Two new standing-rule clauses: (v) consumer polarity, (vi) green tests are not measurements | ✓ VERIFIED | `26-SECURITY.md:886` and `:923`, both with worked examples from this phase's own history. **Caveat recorded under gap 3:** clause (vi)'s "stated bound" cites the sweep's eleven blind axes, which my measurement shows to be incomplete — amending the sweep requires amending this clause. |
| 5 | The provenance of this round's two decisions is recorded and is DIFFERENT from AR-27-04's | ✓ VERIFIED | `27-HUMAN-UAT.md:311-360` — "THE TWO DECISIONS THIS ROUND — MAINTAINER-MADE, recorded WITH their provenance", with "THE CONTRAST, WRITTEN OUT RATHER THAN LEFT TO BE INFERRED" against AR-27-04's `mode: yolo` auto-selection. (Whether the maintainer really decided is not codebase-verifiable → human item 4.) |
| 6 | AR-27-04 is NOT relitigated; only an APPENDED note | ✓ VERIFIED | As truth 27-12/9. |
| 7 | PHASE 27 STILL CLOSES WITH PRIV-05 NOT SATISFIED; ROADMAP says so; REQUIREMENTS.md UNTOUCHED and PRIV-05 stays `[ ]` | ✓ VERIFIED | `ROADMAP.md`: "RE-CONFIRMED 2026-08-26 AFTER ROUND 4 (plan 27-13): THIS PARAGRAPH STILL HOLDS, UNCHANGED." Round-4 planning diff shows `.planning/REQUIREMENTS.md` and `v0.10.0-MILESTONE-AUDIT.md` with ZERO changes. |
| 8 | BOUND carried: two axes closed, six named residuals, not a completeness claim | ✗ FAILED | The six are each real and correctly owned, but the list omits both residuals THIS ROUND CREATED — the bare-quote over-match and the sweep's declaration-shape blindness — and the register affirmatively misstates the second. See gap 4. |

**Score: 29/33 truths verified (0 present, behavior-unverified).**

---

### Deferred Items

| # | Item | Addressed In | Evidence |
|---|------|-------------|----------|
| 1 | AR-27-08 — issue-detail cookie carrier via `AuditIssue.detail()` | Phase 28 | ROADMAP.md Phase 28 goal names it with mechanism and reachability; untouched by round 4 and stated as such |
| 2 | `InjectionPointExtractor.kt:29` unconverted COOKIE predicate | Phase 28 | ROADMAP.md: "closes AR-27-08 AND InjectionPointExtractor.kt:29 TOGETHER, and that pairing is the point" |
| 3 | Vendor auth headers outside `authHeaderRegex`'s 16-name alternation | CONCERNS.md (open, owned) | 27-05 prohibition, restated in the round-4 residual list |
| 4 | AR-27-09 — leading-whitespace / obs-fold logical-line start | 26-SECURITY.md AR-27-09 (open, LOW) | Re-measured: unchanged under STRICT and BALANCED. Out of round-4 scope by maintainer decision; one-token fix written down |
| 5 | AR-27-10 — thirteen RFC 9110 tchars outside the cookie name class | 26-SECURITY.md AR-27-10 (open, LOW) | Re-measured: all 13 admitted, all leak under STRICT. Partition source-pinned by `CookieHeaderNameWidthTest` |

Deferred items do not affect status.

---

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `src/main/kotlin/.../redact/Redaction.kt` | `COOKIE_NAME_PART` widened; polarity in KDoc and rationale | ✓ VERIFIED | `[A-Za-z0-9_-]*` at `:130`; polarity paragraph at `:341-359`; history + direction rule + AR-27-10 bound in the constant's own comment |
| `src/main/kotlin/.../redact/Redaction.kt` | `JSON_STRING_OPEN` + second fixed-width lookbehind + extended rationale | ⚠️ HOLLOW | The constant, the lookbehind and the rationale all exist and the rule DOES close the `notes` carrier — but the constant is a bare `"` and its cost is stated nowhere. Wired and functioning for its stated purpose while shipping an unstated regression |
| `src/main/kotlin/.../scanner/PassiveAiScannerFilters.kt` | polarity comment at the admitting call site | ✓ VERIFIED | `:187-189`, in the `filter{}` lambda where the predicate is consumed |
| `src/test/kotlin/.../redact/CookieHeaderNameParityTest.kt` | inverted assertion, 2 new corpus entries, raised floors, corrected DIRECTION header | ✓ VERIFIED | 19-entry corpus, `EXPECTED_UNDERSCORE_NAMES=3`, floors 18/12/4, DIRECTION header at `:18-27`. 3 tests green |
| `src/test/kotlin/.../redact/CookieHeaderNameWidthTest.kt` (NEW) | `COVERED_TCHARS` / `NOT_COVERED_TCHARS` / `ALL_RFC9110_TCHARS` partition + anti-drift source read | ✓ VERIFIED | 306 lines, 4 tests green, `theCoveredSetIsReadFromRedactionSourceNotRetyped` reads `Redaction.kt` at test time. Notably asserts only ABSENCE — it does not pin the uncovered leak green |
| `src/test/kotlin/.../mcp/tools/SerializedEmissionRedactionTest.kt` | `JsonStringOpenBoundary` nest, 3 new Sentinels, over-match and header-map gates | ⚠️ ORPHANED (partial) | The nest exists (5 tests green) and the gates are real, but both bound the JSON case only — neither can observe the non-JSON over-match or content destroyed BETWEEN a value and its terminator |
| `src/test/kotlin/.../redact/LogicalLineBoundaryScopeTest.kt` | `JSON_STRING_OPEN` appended as a FIFTH `REQUIRED_DECLARATIONS` entry below `JSON_ESCAPED_NEWLINE`; AR-27-09 in rationale assertions | ✓ VERIFIED | 3 tests green; declaration order preserved so `rationaleRegionAboveFragments` still walks back correctly |
| `src/test/kotlin/.../redact/RedactingPolicySurvivalSweepTest.kt` (NEW) | the mechanical sweep, vocabulary, allowlist, fixtures, stated bound | ⚠️ HOLLOW | 1145 lines, 11 tests green, empty `ALLOWLIST`, genuinely fires (3 pre-round hits reproduced). Structurally blind to 136 declarations / 67 backtick `@Test` methods, and that axis is not among its eleven |
| `src/test/kotlin/.../mcp/tools/McpToolHelpersTest.kt` | two prohibited assertions removed, re-pointed at an OFF fixture; STRICT non-vacuity guards preserved | ✓ VERIFIED | Both `assertTrue(...contains("api.example.com"))` gone; `offLeavesBothSerializedShapesByteIdentical` at `:321-351` uses `assertEquals` on both shapes; `SanitizeHeaders` nest 18 tests green |
| `.planning/phases/26-.../26-SECURITY.md` | clause (6), AR-27-09/10, AR-27-04 note, audit row 6, clauses (v)/(vi), recomputed `threats_open` | ✓ VERIFIED (one inherited inaccuracy) | All present and independently checked; clause (vi) inherits the sweep's "ELEVEN axes" claim, which is incomplete |
| `.planning/codebase/CONCERNS.md` | fourth dated W-A amendment, scoped | ✓ VERIFIED | 1 insertion, 0 deletions |
| `.planning/phases/27-.../27-HUMAN-UAT.md` | items 1-8 legible as unanswered; two maintainer decisions with provenance; AR-27-09/10 dispositions | ✓ VERIFIED | "NINE ITEMS ARE STILL UNANSWERED. NOTHING IN ROUND 4 ANSWERED ANY OF THEM."; items 10 and 11 added; provenance contrast section present |
| `.planning/WINDOWS.md` | one entry per falsified premise with its observed number | ✓ VERIFIED | Entries 35, 36, 38, 39 — each carries the projection, the measurement, and the cause |
| `.planning/ROADMAP.md` | round-4 note, four plan entries under waves 10-13, six residuals with owners, unchanged not-satisfied sentence | ✓ VERIFIED | All present; the round-1 goal line is qualified in place rather than rewritten, per the phase's own convention |
| `.planning/phases/27-.../COVERAGE.md` | extended to plans 27-10..27-13 on the same reasoned declaration | ✓ VERIFIED | Extended with a measured inventory table AND an explicit statement that the measurement disagrees with the plan instruction that requested it |

---

### Key Link Verification

| From | To | Via | Status | Details |
|------|-----|-----|--------|---------|
| `COOKIE_NAME_PART` | `cookieHeaderRegex` + `setCookieHeaderRegex` | composed name pattern → `Redaction.apply` → passive-scan prompt | ✓ WIRED | The previously fail-open link is closed: every underscore name the admitter admits is now matched by both regexes (measured) |
| `Redaction.isCookieHeaderName` | `PassiveAiScannerFilters.sanitizeHeadersForPrompt:186` | ADMISSION test in a `filter{}` | ✓ WIRED + polarity documented | The polarity omission that produced the whole round-3 gap is now stated at both ends |
| `CookieHeaderNameWidthTest.NOT_COVERED_TCHARS` | AR-27-10 | register row + `27-10-SUMMARY` | ✓ WIRED | The residual is no longer source-comment-only, which is what let the underscore class live three rounds |
| `HttpRequestResponse.notes` → `toSerializableForm()` → `toolJson.encodeToString` → `redactIfNeeded` → `Redaction.apply` | the composed cookie rules | JSON string open lookbehind | ✓ WIRED | Carrier chain measured end to end; the target case is closed |
| `JSON_STRING_OPEN` | all THREE composed rules | one composer | ⚠️ WIRED, OVER-BROAD | One edit reaches the cookie pair and `authHeaderRegex` together, as designed — including the unstated over-match, which the auth family also inherits (measured on `"authorization: Bearer required"`) |
| `LogicalLineBoundaryScopeTest.REQUIRED_DECLARATIONS` | the rationale region walk-back | declaration ORDER below `JSON_ESCAPED_NEWLINE` | ✓ WIRED | Order preserved; the rationale line floor still passes |
| `RedactingPolicySurvivalSweepTest` | plan 27-08's prose must-have | CI | ⚠️ PARTIAL | The claim is in CI, but the CI check is narrower than the claim on the declaration-shape axis |
| the removed host assertions | `26-SECURITY.md` AR-27-04 | replacement comments at each deleted position | ✓ WIRED | The measurement keeps its home; only the green pin moved; the record says explicitly that this supplies no human judgment |
| the sweep's `ALLOWLIST` | a source-verified reason per entry | — | ✓ WIRED (vacuously, correctly) | `emptyMap()` — the three legitimate shapes are excluded BY CONSTRUCTION in the detector, exactly as the plan required |

---

### Behavioral Spot-Checks

All probes driven against the SHIPPED compiled classes at `build/classes/kotlin/main` (verified
same-mtime as `Redaction.kt`, clean working tree under `src/`), JDK 21, salt `probe-salt`, via
`Redaction.INSTANCE.apply(blob, RedactionPolicy.Companion.fromMode(mode), "probe-salt", false)`.

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| Underscore names stripped, both redacting modes | `Probe` over 3 underscore + 6 canonical names | all `[STRIPPED]`, `leak=false` in STRICT and BALANCED | ✓ PASS |
| Canonical cookie at JSON string open stripped | `{"notes":"Cookie: a=SECRET1\r\nX: y"}` | `Cookie: [STRIPPED]` both modes | ✓ PASS |
| Positive control fires in the same run | `{"notes":"X: y\r\nCookie: a=SECRET9"}` | `Cookie: [STRIPPED]` both modes | ✓ PASS |
| AR-27-09 residual still open (indented header) | `GET / HTTP/1.1\r\n Cookie: a=SECRET5\r\n\r\n` | byte-UNCHANGED, STRICT and BALANCED | ✓ PASS (residual confirmed as recorded) |
| AR-27-10 residual still open (13 tchars) | `x<tchar>cookie: a=SENTINEL` × 13 | `pred=true, STRICTleak=true` × 13 | ✓ PASS (residual confirmed as recorded) |
| AR-27-04 residual still open (host) | serialized `Host:` + `url` under STRICT | host verbatim in both fields; cookie stripped | ✓ PASS (residual confirmed as recorded) |
| **Bare-quote over-match, non-JSON** | 5 shapes: HTML attr, JS string, CSV, prose Set-Cookie, prose authorization | all 5 mutated mid-line in STRICT and BALANCED | **✗ FAIL** |
| **Bare-quote over-match, serialized JSON** | 1714-char `proxy_http_history`-shaped payload with HTML body | 1589 chars destroyed (93%); 40/40 content markers gone; JSON still structurally valid | **✗ FAIL** |
| Regression is new to round 4 | round-3 start set vs round-4 start set vs `(?<=:")` on the same 6 fixtures | BEFORE: NO MATCH ×6. SHIPPED: MATCH, 1607 chars. PROPOSED: closes both `notes` cases, no false positives | ✓ PASS (attribution confirmed) |
| Sweep fires on the real pre-round artifacts | shipped detector re-implemented, run over `git show 09e9cae:<2 files>` | **exactly 3 hits** — 2 host pins + 1 underscore pin | ✓ PASS |
| Sweep clean on the current tree, WIDER detector | any-modifier/annotation/backtick declaration regex over 151 files | 0 real pins (7 `Sentinel.BENIGN_CONTROL` + 1 benign `wibble` control) | ✓ PASS |
| **Sweep declaration-shape blindness** | 1779 declaration lines classified; 6 synthetic pins driven through the shipped detector | 136 invisible / 67 backtick `@Test`; 5 of 6 shapes MISSED, plain-named control FOUND | **✗ FAIL** |
| Raw-string walk not vacuous today | shipped `dropRawStringInteriors` semantics over the tree | 625 lines blanked, 0 of 151 files unbalanced | ✓ PASS (today only — ungated) |
| `threats_open` recomputation | documented frontmatter `awk` re-run | `0`, 46 rows scanned | ✓ PASS |
| T-26-02-01 clause prefix preservation | byte-level prefix comparison of old vs new row | exact prefix = True (18352 → 29499 chars) | ✓ PASS |
| Round-4 test classes | `./gradlew test --tests` on the 6 touched classes | Sweep 11, Width 4, Parity 3, BoundaryScope 3, SerializedEmission 22 across 6 nests, McpToolHelpers 63 across 14 nests — 0 failures, 0 errors, 0 skipped | ✓ PASS |

Full-suite result taken from the orchestrator's single run (1238 tests / 175 classes / 0 failures /
1 pre-existing skip, `check` green). Not re-run here, per the one-full-run budget.

### Probe Execution

| Probe | Command | Result | Status |
|-------|---------|--------|--------|
| — | `find scripts -path '*/tests/probe-*.sh'` | no conventional probes in this repository; no PLAN declares one | N/A |

---

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|-------------|-------------|--------|----------|
| PRIV-05 | 27-10, 27-11, 27-12, 27-13 (all four) | "Cookie values do not reach an AI backend in STRICT or BALANCED mode by any path" | ✗ BLOCKED — **deliberately and correctly** | Round 4's own must-haves say "This plan closes no requirement. PRIV-05 stays `[ ]`" four times, and the codebase agrees: `REQUIREMENTS.md` has ZERO changes across the round-4 range and PRIV-05 is `[ ]`. AR-27-08 (a COOKIE-typed injection point's value reaching `scanner_issues` through `AuditIssue.detail()`, measured surviving STRICT and BALANCED) and `InjectionPointExtractor.kt:29` are untouched and owned by Phase 28. **This is a recorded deferral with a named owner, not an unmet claim.** |

**Orphaned requirements:** none. `REQUIREMENTS.md` maps PRIV-05 to Phase 21 in its ID table and
`ROADMAP.md` Phase 27 declares `Requirements: PRIV-05`; no other ID is mapped to Phase 27, and every
ID declared by a round-4 plan is accounted for above.

---

### Test Quality Audit

| Test File | Linked Req | Active | Skipped | Circular | Assertion Level | Verdict |
|-----------|-----------|--------|---------|----------|-----------------|---------|
| `CookieHeaderNameWidthTest.kt` | PRIV-05 | 4 | 0 | No | Value + source-read anti-drift | ✓ STRONG — the covered set is read out of `Redaction.kt` at test time rather than re-typed, which is the fix for the "asserted against a re-typed copy" shape round 3 recorded |
| `CookieHeaderNameParityTest.kt` | PRIV-05 | 3 | 0 | No | Value (`[STRIPPED]` marker + sentinel absence) | ✓ STRONG — per-name sentinels, exact underscore count, both-polarity corpus floors |
| `RedactingPolicySurvivalSweepTest.kt` | PRIV-05 | 11 | 0 | No | Repository-state, with paired non-vacuity | ⚠️ ADEQUATE-WITH-GAP — every exclusion has a floor and the detector is proven live on real pre-round artifacts, but the declaration-shape gate is untested and unstated |
| `SerializedEmissionRedactionTest.kt` (`JsonStringOpenBoundary`) | PRIV-05 | 5 | 0 | No | Value + byte-identity on a sibling field | ⚠️ INSUFFICIENT for the new start's cost — the over-match gate's fixture puts the value LAST in its string, so the tail's real blast radius is never exercised |
| `McpToolHelpersTest.kt` | PRIV-05 | 63 (14 nests) | 0 | No | Value + `assertEquals` byte identity | ✓ STRONG — the OFF replacement is a byte-identity assertion naming no sensitive value inside an `assertTrue`, which is strictly stronger than what it replaced |
| `LogicalLineBoundaryScopeTest.kt` | PRIV-05 | 3 | 0 | No | Structural + rationale-region floor | ⚠️ ADEQUATE — "and no other" is asserted per-rule, never as a total call-site count, so a FOURTH rule adopting the composer ships green (WR-01) |

**Disabled tests on requirements:** 0.
**Circular patterns detected:** 0 — no test in this round derives an expected value from the system
under test. The two `SHIPPED_REAL_MULTILINE_*` constants are explicitly captured from the PRE-CHANGE
`Redaction.kt`, which is valid external provenance for a byte-identity claim.
**Insufficient assertions:** 2 (WARNING) — the `JsonStringOpenBoundary` over-match gate and
`LogicalLineBoundaryScopeTest`'s "and no other".

---

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| — | — | `TBD` / `FIXME` / `XXX` | — | **None** across all 8 files modified this round |
| — | — | `TODO` / `HACK` / `PLACEHOLDER` | — | **None** |
| — | — | `@Disabled` / `@Ignore` / `assumeTrue` | — | **None** |
| `Redaction.kt` | 277 | A constant whose NAME asserts a semantic (`JSON_STRING_OPEN`) that its VALUE (`"`) does not carry | 🛑 Blocker | A future reader — and this round's own rationale block, and the four planning documents — all describe the boundary in terms of the name, not the value. That gap is how the regression shipped unstated |
| `RedactingPolicySurvivalSweepTest.kt` | 569 | A gate regex narrower than the claim the file makes about its own scope | 🛑 Blocker | 67 live backtick-named `@Test` methods outside a check whose value proposition is completeness of its stated bound |
| `RedactingPolicySurvivalSweepTest.kt` | 96-101, 336-358 | An in-code comment naming a failure direction ("the dangerous direction") with no assertion against it | ⚠️ Warning | If `dropRawStringInteriors` ever blanks real code, the tree scan returns 0 and all 11 tests stay green |

---

### Decision Coverage

Skipped — no `*-CONTEXT.md` exists for this phase, so there is no `<decisions>` block to check.
Non-blocking either way.

---

## Gaps Summary

Round 4 was asked to close three failed must-haves and one newly scoped axis. **It closed all four.**
The underscore class is fixed at the control and measured; the two prohibited STRICT host pins are
gone and their pass-through correctly re-homed under OFF; the "no green survival pin" claim now holds
as a fact under an independent sweep wider than the one that ships; and the JSON-string-open carrier
is closed with its control firing. The record repair is the most rigorous this phase has produced —
byte-exact clause preservation, a recomputed counter with its population restated and the "does any
new finding sit above the gate?" question asked and answered in writing, `REQUIREMENTS.md` genuinely
untouched, and the plan's own falsified projections ledgered with their observed numbers rather than
quietly reconciled.

**Two things stop this being a pass, and they are the same defect wearing two costumes: a stated
scope wider than the thing that enforces it.**

**Gap 1 is the one that matters most, because it is a NEW shipped defect and not an inherited one.**
`JSON_STRING_OPEN` is a bare `"`. Round 4 taught three redaction rules that every double quote begins
a logical line, and on the primary serialized emission path — where escaped quotes are consumed
atomically by the tail — a single quote-preceded `cookie:` in an HTML attribute or a JS string now
annihilates the rest of the JSON string it sits in. I measured 1589 of 1714 characters gone, with the
JSON left structurally valid so every existing shape assertion passes and the model silently receives
a truncated response body. The identical input produced no match at all in the round-3 state. It is in
no comment, no summary and no register; the two tests written to bound the new start both bound the
JSON case only, and the one that could have caught this uses a fixture whose value is the last content
of its string. The proposed `":\""` narrowing is still fixed-width, closes every measured false
positive, and keeps both `notes`-carrier cases working — I verified all three of those claims myself.
This phase's own comment at `Redaction.kt:570-576` argues that destroying analytically load-bearing
content is a functional regression. By that standard round 4 shipped one.

**Gaps 2 to 4 are the sweep and its record.** `RedactingPolicySurvivalSweepTest` is a genuinely good
artifact — empty allowlist, exclusions built into the detector rather than listed, every exclusion
floored by a flip-pair, and demonstrated firing on the three real pre-round artifacts, all of which I
reproduced. Its `FUNCTION_DECLARATION` regex nevertheless cannot see 136 of 1779 declaration lines,
67 of them backtick-named `@Test` methods — the more idiomatic of the two Kotlin styles, already in
use in nine files including one in the redaction package. A survival pin written that way ships green.
That axis is not among the file's eleven, and `26-SECURITY.md` standing-rule clause (vi) now cites
those eleven as the check's stated bound — so the register itself carries a claim wider than its
control, which is verbatim the failure clause (vi) exists to prevent. The fix is one regex plus one
non-vacuity test plus one sentence in the register; the fix for the residual list is to record that a
residual list should also enumerate what the round INTRODUCED, not only what it inherited.

**Neither gap is deferrable.** Phase 28 owns AR-27-08 and `InjectionPointExtractor.kt:29`; neither of
these is in that scope, and gap 1 is a live correctness regression on shipped code.

**What I want to say plainly, because this phase's history makes it worth saying:** round 4 did not
close PRIV-05 and correctly refuses to claim it did. It also did not close itself. But unlike rounds
1-3, the thing it got wrong this time is not the thing it said it got right — the three targets are
genuinely and measurably closed, and I could not falsify any of the closures. The gaps are a new
regression on a rule that was rewritten, and a mechanism whose stated bound outran its gate by one
axis. Both are small, both are precisely located, and both have a written-down fix.

---

_Verified: 2026-08-26T12:17:42Z_
_Verifier: Claude (gsd-verifier), round 4_
