---
phase: 27-priv-05-gap-closure-sanitize-headers
verified: 2026-08-26T00:00:00Z
status: gaps_found
score: 12/15 must-haves verified
behavior_unverified: 0
overrides_applied: 0
re_verification:
  previous_status: gaps_found
  previous_score: 8/9
  gaps_closed:
    - "The COOKIE-typed HTTP parameter carrier — CLOSED at the producer by a genuine TYPE-KEYED control (`Redaction.isCookieParameterType` + `McpToolHelpers.sanitizeParameters`), independently re-verified at source and by running the phase's behavioural tests"
  gaps_remaining: []
  regressions: []
  new_gaps:
    - "A cookie-header name containing `_` is ADMITTED into the passive-scan prompt by the shared predicate and then NOT stripped by `Redaction.apply` — measured leaking under STRICT and BALANCED, and pinned GREEN by a test this phase wrote"
    - "Two green `assertTrue(... .contains(\"api.example.com\"))` assertions under STRICT were committed by this phase, violating plan 27-05's own high-severity prohibition"
gaps:
  - truth: "27-02: 'The predicate is deliberately WIDER than the two regexes ... Wider on the redacting side is fail-safe.'"
    status: failed
    reason: >-
      The premise is false for one of the predicate's three consumers, and that consumer is on the
      PROMPT path — the path this phase's goal line calls the reference implementation.
      `Redaction.isCookieHeaderName` is consumed at three sites (D-27-01). At
      `McpToolHelpers.sanitizeHeaders` it is a REDACTOR, where wider is indeed fail-safe. But at
      `PassiveAiScannerFilters.sanitizeHeadersForPrompt:186` it is an ADMITTER — returning true
      means the header is INCLUDED in the outbound prompt. There, wider is fail-OPEN: a name the
      predicate claims but neither cookie regex can match is admitted and never stripped.
      `COOKIE_NAME_PART = "[A-Za-z0-9-]*"` (`Redaction.kt:119`) excludes `_`, a legal RFC 9110
      tchar, so the difference set is non-empty and reachable.
      MEASURED against the SHIPPED compiled classes (`build/classes/kotlin/main`, JDK 21,
      `Redaction.INSTANCE.apply(blob, RedactionPolicy.fromMode(mode), "probe-salt", false)`), one
      header line per run at column 0 — exactly the shape `buildScanMetadataText` emits:
        STRICT    Cookie / X-Cookie / Cookie2 / Set-Cookie2 / X-Original-Cookie / X-Forwarded-Cookie   leaked=false
        STRICT    my_cookie        leaked=true
        STRICT    X_Cookie         leaked=true
        STRICT    session_cookie   leaked=true
        BALANCED  (identical results)
        isCookieHeaderName("my_cookie") = true
        isCookieHeaderName("X_Cookie")  = true
      Full chain, read at source: `PassiveAiScannerAnalysis.kt:257-258` calls
      `sanitizeHeadersForPrompt` -> `PassiveAiScannerPrompts.buildScanMetadataText:110-115`
      `appendLine`s each admitted header at column 0 -> `PassiveAiScannerAnalysis.kt:380`
      `redactScanMetadata` -> `Redaction.apply` -> no match -> `buildAnalysisPrompt` -> AI backend.
      HONEST ATTRIBUTION, because it changes what the fix is but not whether this is a gap: the
      LEAK is PRE-EXISTING. `git show fe379e5` shows the admitter's conjunct was already
      `name.contains("cookie")` before plan 27-01 replaced it with the identical predicate, and
      `COOKIE_NAME_PART` dates from Phase 21. Phase 27 did not introduce or widen it. What phase 27
      did was MEASURE the asymmetry, frame it in a KDoc and a plan prohibition as "fail-safe"
      without checking the admitting consumer, and then commit a GREEN test pinning the survival.
      It is recorded in NO security artifact: `grep -rn "my_cookie|underscore|_cookie"` over
      `26-SECURITY.md`, `CONCERNS.md`, `v0.10.0-MILESTONE-AUDIT.md` and `ROADMAP.md` returns ZERO
      hits. Unlike AR-27-04/06/07/08 this is not a deferral with an owner — it is unrecorded.
    artifacts:
      - path: "src/main/kotlin/com/six2dez/burp/aiagent/redact/Redaction.kt:119"
        issue: "`COOKIE_NAME_PART = \"[A-Za-z0-9-]*\"` excludes `_`, making both cookie regexes strictly narrower than the predicate that admits headers into the prompt"
      - path: "src/main/kotlin/com/six2dez/burp/aiagent/redact/Redaction.kt:264-268"
        issue: "`isCookieHeaderName` KDoc asserts 'Wider on the redacting side is fail-safe: the cost is over-redacting a benign Cookie-Consent-style header's VALUE'. Measured false for the admitting consumer: the cost is a cookie value reaching the backend."
      - path: "src/main/kotlin/com/six2dez/burp/aiagent/scanner/PassiveAiScannerFilters.kt:186"
        issue: "the predicate is used as an ADMISSION test here, not a redaction test; a true result puts the header into the prompt"
      - path: "src/test/kotlin/com/six2dez/burp/aiagent/redact/CookieHeaderNameParityTest.kt:176-201"
        issue: "`thePredicateIsDeliberatelyWiderThanTheTwoRegexes` asserts `output.contains(sentinel)` for `my_cookie` under STRICT and BALANCED — a green test pinning a cookie value surviving a redacting policy"
    missing:
      - "Widen the name class in the SAFE direction — `COOKIE_NAME_PART = \"[A-Za-z0-9_-]*\"` — so the two regexes reach every name the admitter admits, and flip `thePredicateIsDeliberatelyWiderThanTheTwoRegexes` to assert `my_cookie: [STRIPPED]`; add `X_Cookie` / `session_cookie` to `PARITY_CORPUS`. (The one-directional invariant test `everyNameThePromptPathStripsIsMatchedByTheSharedPredicate` stays true unchanged.)"
      - "OR, if the maintainer decides not to widen: record it as a numbered open finding in `26-SECURITY.md` with its measured probe output, its severity and a named owner, exactly as AR-27-04/07/08 are recorded — and re-point the pinning test at a `PrivacyMode.OFF` fixture so the suite holds no green 'cookie value survives STRICT'."
      - "Either way, correct `isCookieHeaderName`'s KDoc: the fail-safe claim must be scoped to the two REDACTING consumers and explicitly negated for the ADMITTING one."
  - truth: "27-05: 'The Host: header value and the sibling url field surviving un-anonymised under STRICT on this shape is MEASURED, quoted, and carried into plan 27-06 as open finding AR-27-04. It is recorded, NEVER PINNED BY A GREEN TEST.'"
    status: failed
    reason: >-
      Two green `assertTrue(... .contains("api.example.com"))` assertions under `PrivacyMode.STRICT`
      were committed by this phase in commit `09e9cae` ("test(27-04): re-pin the AR-27-01 block on
      the two measured shapes"). This violates plan 27-05's own high-severity prohibition verbatim:
      "Do NOT commit a test that asserts a leaked value SURVIVES ... a green assertion that a host
      reaches a backend under STRICT is exactly the artifact a future audit misreads, and this
      phase exists because of one."
      The underlying behaviour is confirmed by independent measurement against the shipped compiled
      classes:
        IN : {"request":"GET / HTTP/1.1\r\nHost: api.example.com\r\nCookie: a=SECRET\r\n\r\n","url":"https://api.example.com/x"}
        OUT: {"request":"GET / HTTP/1.1\r\nHost: api.example.com\r\nCookie: [STRIPPED]\r\n\r\n","url":"https://api.example.com/x"}
      `RedactionPolicy.fromMode(STRICT)` sets `anonymizeHosts = true` and host anonymisation is a
      user-visible pre-flight promise, yet `hostHeaderRegex` (`Redaction.kt:1894`) is the one rule
      deliberately excluded from the `logicalLineHeaderRule` composer, and `SiteMapEntry.url` /
      `IssueDetails.baseUrl` / `httpService.host` carry the host verbatim with no anonymiser.
      MITIGATION, stated because it is real and is to the phase's credit: the FINDING itself IS on
      the record as AR-27-04, with quoted probe output, and its disposition carries honest
      provenance — `26-SECURITY.md:228-232` and `27-06-SUMMARY.md:46` both state it was AUTO-SELECTED
      by `mode: yolo`, NOT maintainer-chosen. So this gap is a PROHIBITION VIOLATION and a
      test-artifact defect, not an unrecorded leak. It is nonetheless a failed must-have: the truth
      as written says "never pinned by a green test", and it is pinned by two.
    artifacts:
      - path: "src/test/kotlin/com/six2dez/burp/aiagent/mcp/tools/McpToolHelpersTest.kt:247-250"
        issue: "green `assertTrue(rawMessageFinalText.contains(\"api.example.com\"))` under STRICT on the serialized raw-message shape"
      - path: "src/test/kotlin/com/six2dez/burp/aiagent/mcp/tools/McpToolHelpersTest.kt:284-288"
        issue: "green `assertTrue(rawFinalText.contains(\"api.example.com\"))` under STRICT on the header-map shape"
      - path: "src/main/kotlin/com/six2dez/burp/aiagent/redact/Redaction.kt:1894"
        issue: "`hostHeaderRegex` is line-anchored `(?im)^host:...$` and is the one rule excluded from the logical-line composer, so STRICT host anonymisation cannot fire on any serialized emission"
    missing:
      - "Re-point both assertions at a `PrivacyMode.OFF` fixture (where pass-through IS correct), or replace them with an `assumeTrue`/documented negative so the suite contains no green assertion that a sensitive value survives STRICT. The AR-27-04 record already carries the measurement; the test does not need to."
      - "OR close AR-27-04 by routing `hostHeaderRegex` through `logicalLineHeaderRule` AND threading `maybeAnonymizeUrl` into `toSiteMapEntry` / `toSerializableForm`, then flip both assertions to `assertFalse`. Half of that fix alone produces a payload whose `request` is anonymised and whose `url` is not."
      - "Either way: AR-27-04's disposition needs a HUMAN decision recorded as such. A privacy-control bypass on a shipped 1.0.0 posture accepted by the harness is not an accepted risk."
  - truth: "27-08: 'No green test asserting that a cookie value SURVIVES a redacting policy is committed anywhere under src/.'"
    status: failed
    reason: >-
      Falsified by `CookieHeaderNameParityTest.thePredicateIsDeliberatelyWiderThanTheTwoRegexes`
      (`:176-201`), which asserts `output.contains(sentinel)` for the header `my_cookie` under both
      `PrivacyMode.STRICT` and `PrivacyMode.BALANCED`, with a failure message instructing a future
      reader NOT to fix it ("record the measurement, do not narrow the predicate to restore
      symmetry"). The test is green: `CookieHeaderNameParityTest` 3 tests, 0 failures, 0 errors,
      re-run in this verification.
      The test predates the must-have — it was committed in wave 2, and the must-have was authored
      in wave 8 — but the must-have is stated over the whole of `src/`, at the time of writing, and
      it is not true. Plan 27-08's own task did not sweep for it. That is the same shape of defect
      this phase has now recorded three times: a claim verified by a search narrower than the claim
      it makes (`26-SECURITY.md`'s own standing-rule clause (ii)).
      Same root artifact as the first gap above; listed separately because it is a distinct
      must-have from a distinct plan, and because closing one does not automatically close the
      other — the first gap is about the CONTROL, this one is about the RECORD of the control.
    artifacts:
      - path: "src/test/kotlin/com/six2dez/burp/aiagent/redact/CookieHeaderNameParityTest.kt:176-201"
        issue: "green assertion that a cookie-named header's value survives STRICT and BALANCED"
    missing:
      - "Close the first gap (widen `COOKIE_NAME_PART` or record the residual and re-point the test at OFF), which closes this one with it."
      - "Add the sweep that would have caught it, so the must-have is machine-checked rather than asserted: a repository-state test over `src/test` for `assertTrue(...contains(<sentinel>))` inside a STRICT/BALANCED fixture — the same tripwire shape `CookieHeaderRuleOwnershipTest` already uses, with its bound stated."
deferred:
  - truth: "AR-27-08 — a COOKIE-typed injection point's value reaching `scanner_issues` through `AuditIssue.detail()`, surviving STRICT and BALANCED"
    addressed_in: "Phase 28"
    evidence: "ROADMAP.md Phase 28 goal: 'Close AR-27-08. A COOKIE-typed injection point's value reaches the scanner_issues MCP tool result through AuditIssue.detail() and survives Redaction.apply in STRICT and in BALANCED' — with mechanism and reachability cited at source, and the pairing with InjectionPointExtractor.kt:29 stated as the point."
  - truth: "`InjectionPointExtractor.kt:29`'s unconverted `it.type().name == \"COOKIE\"` predicate"
    addressed_in: "Phase 28"
    evidence: "ROADMAP.md Phase 28: 'This phase closes AR-27-08 AND InjectionPointExtractor.kt:29 TOGETHER, and that pairing is the point.'"
  - truth: "Vendor auth headers outside `authHeaderRegex`'s 16-name alternation leak under STRICT"
    addressed_in: "CONCERNS.md (open, recorded, out of phase-27 scope by prohibition)"
    evidence: "27-05 prohibition: 'Do NOT change authHeaderRegex's NAME alternation ... The vendor-header gap recorded in CONCERNS.md stays open and stays out of scope.' Independently re-measured this run: `X-Shopify-Access-Token: SECRET7` unchanged under STRICT."
human_verification:
  - test: >-
      DECIDE the disposition of the underscore cookie-header-name gap (first gap above). Either
      widen `COOKIE_NAME_PART` to `[A-Za-z0-9_-]*` and flip the pinning test, or accept it as a
      numbered, owned residual in `26-SECURITY.md` with its measured probe output.
    expected: >-
      A recorded human decision. If accepted rather than fixed, the accepting test must assert on a
      `PrivacyMode.OFF` fixture so the suite holds no green 'cookie value survives STRICT', and the
      `isCookieHeaderName` KDoc's 'fail-safe' sentence must be corrected for the admitting consumer.
    why_human: "A scope/risk decision on a shipped 1.0.0 release, and a possible amendment to how PRIV-05's wording is scoped for a fourth time."
  - test: >-
      DECIDE AR-27-04 with a HUMAN in the loop. Its current disposition is recorded, honestly, as
      auto-selected by `mode: yolo` rather than maintainer-chosen (`26-SECURITY.md:228-232`,
      `27-06-SUMMARY.md:46`).
    expected: "Either a fix (host rule through the composer AND `maybeAnonymizeUrl` threaded into the two serializers) or a maintainer-signed acceptance, plus removal of the two green STRICT `contains(\"api.example.com\")` assertions."
    why_human: "A privacy-control bypass on a shipped release posture, currently accepted by the harness rather than by a person."
  - test: >-
      CARRIED FORWARD from `27-VERIFICATION-2.md` and `27-HUMAN-UAT.md` items 1-8, all still
      unanswered: live-Burp confirmation of the wave-4/5 raw-message fix; live confirmation that
      Montoya `parameters()` yields COOKIE entries in-process; live confirmation of the wave-7
      parameter fix; live confirmation of the AR-27-08 route; the `T-27-06-06` README/SPEC
      host-anonymisation overclaim; and the AR-27-07 / AR-27-08 severity dispositions.
    expected: "As stated per item in `27-HUMAN-UAT.md`. That file already records them as unanswered and must stay legible as such."
    why_human: "Needs a live Burp instance, real proxy traffic, a real MCP client, and maintainer risk decisions."
  - test: >-
      NEW — decide whether the JSON-string-open and leading-whitespace boundary blind spots in
      `logicalLineHeaderRule` are in scope for a follow-up. Measured this run: a CANONICAL `Cookie:`
      header survives STRICT when it is the FIRST content of a JSON string value
      (`{"notes":"Cookie: a=SECRET1\r\nX: y"}` unchanged), and when the header line is
      whitespace-indented (`GET / HTTP/1.1\r\n Cookie: a=SECRET5\r\n\r\n` unchanged). The positive
      control fired on the same run: `{"notes":"X: y\r\nCookie: a=SECRET9"}` -> `Cookie: [STRIPPED]`.
    expected: "Either a widening of the composer's start boundary (both widen only in the over-redacting direction) or a recorded residual naming `HttpRequestResponse.notes` as the reachable field."
    why_human: "A scope decision on the same rule this phase rewrote; reachability depends on whether analyst notes can begin with a header line, which is a product judgement."
---

# Phase 27: PRIV-05 Gap Closure — Verification Report (Round 3)

**Phase Goal (as it now stands, including both recorded widenings):** Close PRIV-05's cookie
carriers — the `sanitizeHeaders` name-variant parity (round 1), the serialized raw-HTTP-in-JSON
emission carrier (round 2), and the COOKIE-typed HTTP parameter carrier (round 3).
**Verified:** 2026-08-26
**Status:** gaps_found
**Re-verification:** Yes — third round, after `27-VERIFICATION.md` (7/9) and `27-VERIFICATION-2.md` (8/9)

---

## Headline

**What phase 27 claimed to close in round 3 IS closed.** The COOKIE-typed parameter carrier is
genuinely fixed: `Redaction.isCookieParameterType` reads `HttpParameterType.name` and never a
rendered string, `McpToolHelpers.sanitizeParameters` is the sole `ParsedParam` producer in the
repository, all four MCP producers plus the bounty-prompt resolver route through it, and 25 green
behavioural tests drive the real sanitizer and the real serializer. That is the first mechanism in
this phase keyed on the data's SOURCE rather than on a RENDERING of it, and it holds up to
independent inspection. The records work in waves 3, 6 and 9 is unusually honest — append-and-amend
throughout, `threats_open` computed with its population stated, `REQUIREMENTS.md` untouched (0
lines), the milestone audit append-only (189 insertions / 0 deletions), and the phase's own
"PRIV-05 NOT SATISFIED" sentence written into `ROADMAP.md` with Phase 28 named as owner.

**But this phase also committed two green tests asserting that a sensitive value survives a
redacting policy — the exact artifact its own security record says it exists to stop producing.**
Both code-review blockers were independently confirmed by driving the compiled shipped classes; I
did not inherit the reviewer's verdict on either. One of them (CR-01) is a **live cookie-value
disclosure to a third-party AI backend under STRICT, on the PROMPT path** — the path the phase goal
line calls the reference implementation — and it appears in **no** security record anywhere in
`.planning/`. It is not a deferral with an owner. It is unrecorded.

The honest attribution matters and cuts both ways: **the underscore leak is pre-existing**, not
introduced by this phase. `git show fe379e5` proves the admitter's conjunct was already
`name.contains("cookie")` before plan 27-01 refactored it, and `COOKIE_NAME_PART` dates from Phase
21. What phase 27 did was measure the asymmetry in wave 2, mis-frame it as "fail-safe" without
checking the admitting consumer, pin it green, and then in wave 8 author a must-have — "No green
test asserting that a cookie value SURVIVES a redacting policy is committed anywhere under `src/`"
— that its own repository falsifies.

---

## Verification Method

Every behavioural claim below was measured, not read. The probe drives the **shipped compiled
classes** (`build/classes/kotlin/main`, recompiled this run with
`JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew compileKotlin`, exit 0) through
`Redaction.INSTANCE.apply(raw, RedactionPolicy.Companion.fromMode(mode), "probe-salt", false)` from
a standalone Java harness on JDK 21 — no test-fixture indirection, no reimplementation of any rule.
Raw output is quoted inline.

Test evidence is from targeted named-class runs, not from SUMMARY claims. No test was edited, added
or disabled by this verification.

---

## Goal Achievement

### Observable Truths

Consolidated from the `must_haves.truths` of all nine plans plus the ROADMAP goal as widened.
Where several plan truths are one claim, they are merged; nothing is dropped.

| #  | Plan  | Truth | Status | Evidence |
|----|-------|-------|--------|----------|
| 1  | 27-01 | All seven cookie header spellings (`Cookie`, `Set-Cookie`, `X-Cookie`, `Cookie2`, `Set-Cookie2`, `X-Original-Cookie`, `X-Forwarded-Cookie`) yield `[STRIPPED]` from `sanitizeHeaders` under STRICT and BALANCED, via the shared predicate | ✓ VERIFIED | `McpToolHelpers.kt:334-336` calls `Redaction.isCookieHeaderName(name)`; `McpToolHelpersTest$SanitizeHeaders` 17 tests, 0 failures (run this verification). Predicate measured true for all seven. |
| 2  | 27-01 | Exactly ONE cookie-header-name rule across the two redaction paths and the passive-scan admitter; other matchers classified non-redacting; enforced by a repository-state tripwire with its five-spelling bound stated | ✓ VERIFIED | `Redaction.kt:293`; consumers at `McpToolHelpers.kt:336` and `PassiveAiScannerFilters.kt:186`. `CookieHeaderRuleOwnershipTest` 3 tests green; bound stated in its file header. |
| 3  | 27-01 | OFF stays OFF; header NAMES survive with original casing, only VALUES replaced; `Locale.ROOT` applied to the whole function | ✓ VERIFIED | `McpToolHelpers.kt:327` (`lowercase(Locale.ROOT)` feeding all three comparisons), `:343` (`sanitized[name] = value`, original-cased key). `apply()` uses `m.value.substringBefore(":")`, not a fixed string. Tests green. |
| 4  | 27-02 | Parity invariant `promptStrips(N) ⇒ predicate(N)` asserted non-vacuously; the reverse implication deliberately not asserted | ✓ VERIFIED | `CookieHeaderNameParityTest:150-171`; corpus-size assertion present; 3 tests green. |
| 5  | 27-02 | "The predicate is deliberately WIDER than the two regexes … **Wider on the redacting side is fail-safe**" | ✗ **FAILED** | **BLOCKER.** The predicate's third consumer is an ADMITTER, not a redactor. Measured: `my_cookie` / `X_Cookie` / `session_cookie` leak under STRICT **and** BALANCED. See Blocker 1. |
| 6  | 27-03 | `26-SECURITY.md` T-26-02-01 re-closed with source citations; reopening narrative preserved verbatim; claims scoped to three sites; `CONCERNS.md` W-A amended | ✓ VERIFIED | T-26-02-01 clauses (1)–(5) present, each preserved; scope sentences bounded to "the two redaction paths and the passive-scan admitter"; process rule clauses (i)/(ii) present. |
| 7  | 27-04 | A cookie sentinel does NOT survive the serialized emission shape under STRICT/BALANCED — canonical plus five variants, across `HttpRequestResponse`, `SiteMapEntry` and `IssueDetails` | ✓ VERIFIED | Measured: `{"request":"…\r\nCookie: a=SECRET\r\n…"}` → `Cookie: [STRIPPED]`. `SerializedEmissionRedactionTest` 24 tests across 5 nested classes, 0 failures. |
| 8  | 27-04 | Multi-line behaviour byte-identical by construction; `RedactionTest` green on a **zero-line diff**; four rule-shape hazards each gated by their own test with a JSON-parse assertion | ✓ VERIFIED | `git diff b811f42~2..HEAD -- RedactionTest.kt` → empty. `SerializedEmissionRedactionTest$Hazards` 7 tests green. Real-line branch is `^` + unchanged name fragment + `:\s*.+$` (`Redaction.kt:210`, `:236-240`). |
| 9  | 27-05 | The 14-site emission inventory and the three `addTool` registration paths are pinned; a credential-bearing auth-header value no longer survives the serialized shape; the composer's rule set is pinned with `hostHeaderRegex` excluded for a recorded reason | ✓ VERIFIED | `SerializedEmissionSiteInventoryTest` 5 tests green; `LogicalLineBoundaryScopeTest` 3 tests green; `authHeaderRegex` built via `logicalLineHeaderRule` at `Redaction.kt:97-105`; `SerializedEmissionRedactionTest$AuthHeaderCredentials` 7 tests green. |
| 10 | 27-05 | The `Host:`/`url` residual is "recorded, **never pinned by a green test**" | ✗ **FAILED** | **BLOCKER.** Two green `assertTrue(contains("api.example.com"))` under STRICT, committed by this phase in `09e9cae`. See Blocker 2. |
| 11 | 27-06 | Records append-and-amend: AR-27-01 reclassified, AR-27-02 superseded, AR-27-04 opened with quoted evidence; `threats_open` computed by a documented command; milestone-audit heading qualified; `REQUIREMENTS.md` untouched | ✓ VERIFIED | All three AR dispositions present in `26-SECURITY.md`; frontmatter carries the computation command and its quoted output; `git diff` shows `REQUIREMENTS.md` 0 lines, `v0.10.0-MILESTONE-AUDIT.md` +189 / −0. |
| 12 | 27-07 | The COOKIE-typed parameter carrier is closed at the producer by a **TYPE-KEYED** control that reads `HttpParameterType` and never a rendered string; one predicate and one sanitizer across the rewired producers; OFF still OFF; non-cookie types untouched; the bounty resolver gated | ✓ VERIFIED | `Redaction.isCookieParameterType` (`:335`) compares against `COOKIE_PARAMETER_TYPE_NAME` (`:343`); `McpToolHelpers.sanitizeParameters` (`:382-399`) is the sole `ParsedParam` producer; wired at `McpToolExecutorImpl.kt:360,381` and `McpToolLegacy.kt:160,189`; `BountyPromptTagResolver.kt:151`. `ParameterCarrierRedactionTest` 25 tests green; `BountyPromptTagResolverTest` 5 green. |
| 13 | 27-08 | `cookieTypedParamRegex`'s comment narrowed to its measured shape with a cross-reference to the type-keyed owner; a source-accessor-keyed carrier inventory exists with its bound and next blind axis stated; two neighbouring questions measured not assumed | ✓ VERIFIED | `Redaction.kt:695-724` clauses (c) and (d) present and correct; `CookieCarrierInventoryTest` 4 tests green, KDoc states four blind axes plus a fifth weaker bound before any assertion; measurements 1 and 2 recorded verbatim in `27-08-SUMMARY.md` and carried into AR-27-07 / AR-27-08. |
| 14 | 27-08 | "**No green test asserting that a cookie value SURVIVES a redacting policy is committed anywhere under `src/`**" | ✗ **FAILED** | **BLOCKER.** Falsified by `CookieHeaderNameParityTest:176-201`. Same artifact as truth 5; distinct must-have. |
| 15 | 27-09 | T-26-02-01 clause (5) with clauses (1)–(4) as an exact prefix; AR-27-06 defined; `threats_open` computed **with its population stated**; standing rule clause (iv); unanswered human items carried forward legible as unanswered; ROADMAP records PRIV-05 NOT satisfied with a NAMED SUCCESSOR | ✓ VERIFIED | Clause (5) present at `26-SECURITY.md:100`; AR-27-06/07/08 rows at `:161-163`; population bound stated in frontmatter comment; clause (iv) at `:574`; `27-HUMAN-UAT.md:75` "THESE ITEMS ARE UNANSWERED"; ROADMAP Phase 27 closing paragraph + Phase 28 entry. |

**Score:** 12/15 truths verified (0 present-but-behavior-unverified).

---

## Blocker 1 — CR-01 CONFIRMED: an underscore defeats cookie redaction on the prompt path, and this phase pinned it green

**Independently measured. The reviewer's verdict was not inherited.**

`Redaction.isCookieHeaderName` is a bare `contains("cookie")`. `COOKIE_NAME_PART` is
`[A-Za-z0-9-]*` and excludes `_`, which is a legal RFC 9110 `tchar`. The two cookie regexes are
therefore **strictly narrower** than the predicate, and the difference set is reachable.

That would be harmless if the predicate were only a redactor. It is not. Its third consumer,
`PassiveAiScannerFilters.sanitizeHeadersForPrompt:186`, is an **admitter** — a `true` result puts
the header into the outbound prompt:

```kotlin
if (name.contains("auth") || name.contains("token") || Redaction.isCookieHeaderName(header.name())) {
    return@filter true          // <-- ADMITS the header into the prompt
}
```

Measured on the shipped compiled classes, one header line per run at column 0 (the exact shape
`buildScanMetadataText` emits):

```
STRICT    Cookie               leaked=false
STRICT    X-Cookie             leaked=false
STRICT    Cookie2              leaked=false
STRICT    Set-Cookie2          leaked=false
STRICT    X-Original-Cookie    leaked=false
STRICT    X-Forwarded-Cookie   leaked=false
STRICT    my_cookie            leaked=true      <-- cookie value reaches the backend
STRICT    X_Cookie             leaked=true
STRICT    session_cookie       leaked=true
BALANCED  (identical)

isCookieHeaderName("my_cookie") = true
isCookieHeaderName("X_Cookie")  = true
```

Full chain, each hop read at source:

```
PassiveAiScannerAnalysis.kt:257-258   sanitizeHeadersForPrompt(request.headers(), isRequest = true)
  -> PassiveAiScannerFilters.kt:186   isCookieHeaderName -> ADMIT
  -> PassiveAiScannerPrompts.kt:110-115  appendLine(it)   (column 0, own line)
  -> PassiveAiScannerAnalysis.kt:380  redactScanMetadata -> Redaction.apply
  -> NO MATCH (COOKIE_NAME_PART excludes '_')
  -> buildAnalysisPrompt -> configured AI backend
```

This is also a hard **inconsistency between the two paths the phase set out to unify**, merely
inverted from the original defect: `McpToolHelpers.sanitizeHeaders` **does** strip `my_cookie`
(it uses the predicate), while `Redaction.apply` does not.

And the phase committed a green test asserting the survival as correct:

```kotlin
// CookieHeaderNameParityTest.kt:187-196
assertTrue(
    output.contains(sentinel),
    "$mode: the prompt path must NOT strip '$name' … This asymmetry is INTENTIONAL and fail-safe …
     If this assertion fails, the prompt-path regexes were widened — record the measurement,
     do not narrow the predicate to restore symmetry",
)
```

The failure message actively instructs the next reader not to fix it.

**Two things this verification will not overstate.**

1. **The leak is pre-existing.** `git show fe379e5` shows the admitter's conjunct was already
   `name.contains("cookie")` before plan 27-01 replaced it with the byte-equivalent predicate call;
   `COOKIE_NAME_PART` dates from Phase 21. Phase 27 neither introduced nor widened this.
2. **`27-02-PLAN.md` did consider the alternatives** — it explicitly rejects "narrow the predicate"
   and "drop `my_cookie` from the corpus", both correctly. What it never considered is the third and
   safe option: **widen the regex side** so the two agree in the over-redacting direction. The
   `isCookieHeaderName` KDoc (`Redaction.kt:264-268`) states the cost of the asymmetry as
   "over-redacting a benign `Cookie-Consent`-style header's VALUE". Measured, for the admitting
   consumer, the cost is the opposite: a cookie value reaching an AI backend.

**What makes this a BLOCKER rather than a recorded residual:** it is recorded nowhere.

```
$ grep -rn "my_cookie\|underscore\|_cookie" \
    .planning/phases/26-coverage-static-analysis-debt-docs/26-SECURITY.md \
    .planning/codebase/CONCERNS.md \
    .planning/v0.10.0-MILESTONE-AUDIT.md \
    .planning/ROADMAP.md
(no output)
```

Unlike AR-27-04, AR-27-06, AR-27-07 and AR-27-08 — each of which is a numbered, severity-assigned,
owned finding — this one exists only inside the test that pins it and inside `27-02`'s plan and
summary. `26-SECURITY.md`'s "what phase 27 leaves OPEN" enumeration does not contain it, so the
register is once again **wider than the control it describes**.

---

## Blocker 2 — CR-02 CONFIRMED: two green assertions that a real hostname survives STRICT

**Independently measured.**

```
IN : {"request":"GET / HTTP/1.1\r\nHost: api.example.com\r\nCookie: a=SECRET\r\n\r\n","url":"https://api.example.com/x"}
OUT: {"request":"GET / HTTP/1.1\r\nHost: api.example.com\r\nCookie: [STRIPPED]\r\n\r\n","url":"https://api.example.com/x"}
                                       ^^^^^^^^^^^^^^^ real host survives STRICT, in the same
                                                       payload whose cookie was stripped
```

`RedactionPolicy.fromMode(STRICT)` sets `anonymizeHosts = true`, and STRICT host anonymisation is a
user-visible pre-flight promise. `hostHeaderRegex` (`Redaction.kt:1894`) is the one rule
deliberately excluded from the `logicalLineHeaderRule` composer, so it cannot fire on any serialized
emission; `SiteMapEntry.url`, `IssueDetails.baseUrl` and `httpService.host` carry the host verbatim
with no anonymiser in front.

The behaviour is a recorded finding (AR-27-04) and I am not relitigating its disposition. **What
fails is the must-have as written.** Plan 27-05 states the residual "is recorded, **never pinned by
a green test**", and its own high-severity prohibition reads:

> "Do NOT commit a test that asserts a leaked value SURVIVES … a green assertion that a host
> reaches a backend under STRICT is exactly the artifact a future audit misreads, and this phase
> exists because of one."

Two such assertions were committed by this phase, in `09e9cae`:

```kotlin
// McpToolHelpersTest.kt:247-250   (serialized raw-message shape)
assertTrue(rawMessageFinalText.contains("api.example.com"), "measured AR-27-04: …")
// McpToolHelpersTest.kt:284-288   (header-map shape)
assertTrue(rawFinalText.contains("api.example.com"), "measured AR-27-04: …")
```

**To the phase's credit, and stated so this blocker is not read as worse than it is:** the finding
itself is fully on the record with quoted evidence, and its disposition carries honest provenance —
`26-SECURITY.md:228-232` and `27-06-SUMMARY.md:46` both state it was **AUTO-SELECTED by `mode: yolo`,
NOT maintainer-chosen**. That is exactly the kind of disclosure the phase's standing rules were
written to force. The gap is that the acceptance still needs a human, and that the suite should not
be teaching a future reader, in green, that a hostname surviving STRICT is expected.

---

## Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `redact/Redaction.kt` | `isCookieHeaderName`, `COOKIE_NAME_TOKEN`, `logicalLineHeaderRule` + 3 fragments, `isCookieParameterType`, narrowed `cookieTypedParamRegex` comment | ⚠️ PARTIAL | All symbols present and wired. `COOKIE_NAME_PART:119` is the defect; `isCookieHeaderName`'s KDoc "fail-safe" claim (`:264-268`) is measurably false for the admitting consumer. |
| `mcp/tools/McpToolHelpers.kt` | `sanitizeHeaders` via shared predicate + `Locale.ROOT`; `sanitizeParameters` type-keyed | ✓ VERIFIED | `:310-345`, `:382-399`. Sole `ParsedParam` producer. |
| `mcp/tools/McpToolExecutorImpl.kt` | `request_parse` + `params_extract` route through `sanitizeParameters` | ✓ VERIFIED | `:360`, `:381`. |
| `mcp/tools/McpToolLegacy.kt` | same, legacy executor | ✓ VERIFIED | `:160`, `:189`. |
| `scanner/PassiveAiScannerFilters.kt` | admitter routed through the shared predicate + `Locale.ROOT` | ⚠️ ORPHANED-BY-WIDTH | Wired correctly, but the predicate it now shares is wider than the regexes downstream — the wiring is what makes Blocker 1 reachable. |
| `prompts/bountyprompt/BountyPromptTagResolver.kt` | COOKIE type gate alongside the name filter | ✓ VERIFIED | `:151`. Note: class has zero instantiations in `src/main/kotlin` (latent, and the file says so). |
| `CookieHeaderNameParityTest.kt` | parity invariant, non-vacuous, no reverse implication | ✗ **CONTAINS THE BLOCKER** | Invariant test correct; `thePredicateIsDeliberatelyWiderThanTheTwoRegexes:176-201` pins a leak green. |
| `CookieHeaderRuleOwnershipTest.kt` | 5-spelling tripwire, bound stated, non-vacuous | ✓ VERIFIED | 3 tests green. |
| `SerializedEmissionRedactionTest.kt` | red-probe family over the real serialized shape | ✓ VERIFIED | 24 tests across 5 nested classes, green. Fixtures built from the real serializers. |
| `SerializedEmissionSiteInventoryTest.kt` | 14-site + 3-registration-path pins, both bounds stated separately | ✓ VERIFIED | 5 tests green. |
| `LogicalLineBoundaryScopeTest.kt` | composer applied to exactly 3 rules, host excluded | ✓ VERIFIED | 3 tests green. |
| `ParameterCarrierRedactionTest.kt` | red probes, OFF control, non-cookie control, enum parity, producer pin | ✓ VERIFIED | 25 tests green; drives the real `sanitizeParameters` and real `toolJson`. |
| `CookieCarrierInventoryTest.kt` | accessor-keyed inventory, 4 blind axes + 5th bound stated first | ✓ VERIFIED | 4 tests green; bounds stated before any assertion. |
| `McpToolHelpersTest.kt` | AR-27-01 pin inverted, non-vacuity guard preserved | ⚠️ **CONTAINS THE BLOCKER** | Inversion done correctly (`assertFalse` on the cookie sentinel, `:233-236`); but two green STRICT host-survival assertions added alongside. `$SanitizeHeaders` 17 tests green. |
| `26-SECURITY.md` | clauses (1)–(5), AR-27-01…08, computed `threats_open` + population, standing rule (i)–(iv), 5 audit-trail rows | ✓ VERIFIED | All present; history preserved verbatim through three reopenings. |
| `CONCERNS.md` | three dated W-A amendments, scoped | ✓ VERIFIED | Present. |
| `v0.10.0-MILESTONE-AUDIT.md` | append-only closure notes and corrections | ✓ VERIFIED | +189 / −0. Frontmatter, `scores:` and `gaps:` untouched. |
| `ROADMAP.md` | "PRIV-05 NOT SATISFIED" in the phase record + named successor Phase 28 | ✓ VERIFIED | Both present, with source-cited mechanism and reachability for AR-27-08. |
| `REQUIREMENTS.md` | untouched | ✓ VERIFIED | 0 lines changed across the phase. |
| `27-HUMAN-UAT.md` | 8 items, unanswered ones legible as unanswered | ✓ VERIFIED | `:75` "THESE ITEMS ARE UNANSWERED"; one item recorded as answered with its provenance. |

---

## Key Link Verification

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| `Redaction.COOKIE_NAME_TOKEN` | `cookieHeaderRegex` + `setCookieHeaderRegex` + `isCookieHeaderName` | shared const | ✓ WIRED | `:125`, `:242-248`, `:293`. |
| `Redaction.isCookieHeaderName` | `McpToolHelpers.sanitizeHeaders` | direct call | ✓ WIRED | `McpToolHelpers.kt:336`. |
| `Redaction.isCookieHeaderName` | `PassiveAiScannerFilters.sanitizeHeadersForPrompt` | direct call | ⚠️ **WIRED, FAIL-OPEN** | `:186`. Admitter semantics + narrower regexes = Blocker 1. |
| `sanitizeHeaders` | `toolJson.encodeToString` → `redactIfNeeded` | serialization | ✓ WIRED | End-to-end asserted on the final string in `McpToolHelpersTest`. |
| `logicalLineHeaderRule` | `cookieHeaderRegex`, `setCookieHeaderRegex`, `authHeaderRegex` | composer | ✓ WIRED | `:236-240`, `:97`, `:242`, `:247`. `hostHeaderRegex` excluded (`:1894`) — recorded, and the substance of Blocker 2. |
| `request.parameters()` | `sanitizeParameters` → `ParsedParam.value` → `toolJson` → tool result | producer control | ✓ WIRED | The link `27-VERIFICATION-2.md` recorded as NOT WIRED. Now wired at all four producers. |
| `request.parameters()` | `sanitizeParameters` → `params_extract` line | producer control | ✓ WIRED | `McpToolExecutorImpl.kt:360`, `McpToolLegacy.kt:160`. Formatter reads the sanitized `ParsedParam`, not the Montoya parameter. |
| `Redaction.isCookieParameterType` | `sanitizeParameters` + `BountyPromptTagResolver.buildRequestParameters` | shared predicate | ✓ WIRED | `:393`, `BountyPromptTagResolver.kt:151`. `InjectionPointExtractor.kt:29` keeps its own — named in the KDoc at `Redaction.kt:328`, deferred to Phase 28. |
| `27-08 measurement 2` | `AR-27-08` → ROADMAP Phase 28 | record → owner | ✓ WIRED | The deferral has a named owner; not an omission. |

---

## Data-Flow Trace (Level 4)

| Artifact | Value | Source | Reaches backend redacted? | Status |
|----------|-------|--------|---------------------------|--------|
| `ParsedRequest.headers` | header value | `sanitizeHeaders` | Yes, for every name the predicate matches | ✓ FLOWING |
| `ParsedRequest.parameters[].value` | COOKIE param value | `sanitizeParameters` | Yes — `[STRIPPED]` at the producer | ✓ FLOWING |
| `params_extract` line `value=` | COOKIE param value | `sanitizeParameters` | Yes | ✓ FLOWING |
| `HttpRequestResponse.request` | raw HTTP, `Cookie:` line | `logicalLineHeaderRule` escaped branch | Yes (measured `[STRIPPED]`) | ✓ FLOWING |
| Passive-scan prompt, cookie-named header | header value | `sanitizeHeadersForPrompt` → `Redaction.apply` | **No** for names containing `_` | ✗ **LEAKING (Blocker 1)** |
| `HttpRequestResponse.request` `Host:` | hostname | `hostHeaderRegex` | **No** on any serialized shape | ✗ **LEAKING (Blocker 2 / AR-27-04)** |
| `SiteMapEntry.url`, `IssueDetails.baseUrl`, `httpService.host` | hostname | none | **No anonymiser at all** | ✗ **DISCONNECTED (AR-27-04)** |
| `HttpRequestResponse.notes` beginning with a header line | `Cookie:` value | `logicalLineHeaderRule` | **No** — neither branch has a string-open boundary | ⚠️ **STATIC (new, see below)** |

---

## Behavioural Spot-Checks

All run against the shipped compiled classes, STRICT unless stated.

| Behaviour | Result | Status |
|-----------|--------|--------|
| Canonical + 5 variant cookie names on a real-newline blob | all `[STRIPPED]`, both modes | ✓ PASS |
| `my_cookie` / `X_Cookie` / `session_cookie` on a real-newline blob | **survive verbatim**, both modes | ✗ **FAIL** |
| Cookie in serialized JSON (`\r\n` escaped) | `Cookie: [STRIPPED]` | ✓ PASS |
| `Host:` + `url` in the same serialized payload | **both survive** | ✗ **FAIL** (AR-27-04) |
| COOKIE-typed param JSON handed straight to `Redaction.apply` | survives — expected; the control is at the producer, and AR-27-06 records exactly this no-backstop bound | ✓ PASS (as designed) |
| `{"notes":"Cookie: a=SECRET1\r\nX: y"}` (header at string open) | **survives** | ✗ **FAIL** (new) |
| `{"notes":"X: y\r\nCookie: a=SECRET9"}` (positive control, same run) | `Cookie: [STRIPPED]` | ✓ PASS — proves the probe above is a reach result, not a vacuous fixture |
| `GET / HTTP/1.1\r\n Cookie: a=SECRET5\r\n\r\n` (indented header line) | **survives** | ✗ **FAIL** (new, narrow) |
| `X-Shopify-Access-Token: SECRET7` under STRICT | survives | ✓ PASS (out of scope by 27-05 prohibition; open in `CONCERNS.md`) |

### New measurement not in either prior verification

`logicalLineHeaderRule`'s escaped branch requires a preceding `(?<=\\[rn])`. A header that is the
**first content of a JSON string value** has neither a real `^` nor a preceding escaped newline, so
**even the canonical `Cookie:` header survives STRICT** there. The positive control fired in the
same run, so this is a statement about the rule's reach and not a broken probe. Reachable through
`HttpRequestResponse.notes` (analyst annotations) and any future field carrying a header fragment
rather than a whole message. Recorded as a **WARNING and a human-decision item**, not a blocker:
the primary `request` field always begins `GET / HTTP/1.1`, so the 14 pinned emission sites' main
payload is unaffected, and `notes` is not enumerated as a cookie-byte carrier.

---

## Test Execution

Targeted named-class runs, this verification, JDK 21. No test edited, added or disabled.

| Class | Tests | Failures | Errors |
|-------|-------|----------|--------|
| `CookieCarrierInventoryTest` | 4 | 0 | 0 |
| `CookieHeaderNameParityTest` | 3 | 0 | 0 |
| `CookieHeaderRuleOwnershipTest` | 3 | 0 | 0 |
| `ParameterCarrierRedactionTest` | 25 | 0 | 0 |
| `SerializedEmissionRedactionTest` (5 nested) | 24 | 0 | 0 |
| `SerializedEmissionSiteInventoryTest` | 5 | 0 | 0 |
| `LogicalLineBoundaryScopeTest` | 3 | 0 | 0 |
| `PassiveAiScannerHeaderAdmissionTest` | 3 | 0 | 0 |
| `McpToolHelpersTest$SanitizeHeaders` | 17 | 0 | 0 |
| `BountyPromptTagResolverTest` | 5 | 0 | 0 |

No `RedactionTest` wall-clock flake was encountered; `RedactionTest` was not re-run by this
verification because its **zero-line diff** is the evidence the phase relies on, and the
orchestrator recorded the full suite green this run.

**The green suite is not the finding.** Every blocker above is green *because* a test asserts it.

---

## Requirements Coverage

| Requirement | Source | Description | Status | Evidence |
|-------------|--------|-------------|--------|----------|
| **PRIV-05** | all 9 plan frontmatters (`requirements: [PRIV-05]`) | "Cookie values do not reach an AI backend in STRICT or BALANCED mode by any path" (`REQUIREMENTS.md:23`) | ✗ **BLOCKED** | Not satisfied — and the phase itself says so. Three carriers closed (header names, serialized emission, typed parameters). Open: AR-27-08 (owned by Phase 28), and — **not owned by anyone** — the underscore name class measured above. |

**Orphaned requirements:** none. `REQUIREMENTS.md:47` maps PRIV-05 to Phase 21; every plan in this
phase declares `requirements: [PRIV-05]` and no other ID. All declared IDs are accounted for.

**The `- [x] PRIV-05` tick at `REQUIREMENTS.md:23` remains wrong.** This is correctly NOT this
phase's to fix — `REQUIREMENTS.md` is untouched (0 lines) and the ROADMAP states the re-derivation
is the milestone owner's job. Flagged here for that owner, as in both prior rounds. **New
information for that re-derivation:** the enumeration of what remains open in `26-SECURITY.md` is
itself incomplete — it does not contain the underscore name class.

---

## Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| `CookieHeaderNameParityTest.kt` | 176-201 | Green assertion that a sensitive value survives a redacting policy | 🛑 Blocker | Pins a leak as expected behaviour, and instructs the next reader not to fix it |
| `McpToolHelpersTest.kt` | 247-250, 284-288 | Green assertion that a hostname survives STRICT | 🛑 Blocker | Violates 27-05's own high-severity prohibition verbatim |
| `Redaction.kt` | 264-268 | KDoc asserts "wider on the redacting side is fail-safe" for a predicate with an admitting consumer | ⚠️ Warning | The claim a maintainer reads first is measurably false for one of three consumers |
| `Redaction.kt` | 97-105, 236-240 | `authHeaderRegex` passes a **capturing** group into a composer that interpolates it into both branches → groups 1 and 2, exactly one always null | ⚠️ Warning | Harmless today (`apply()` uses `m.value.substringBefore(":")`); the three "identical" composed rules are silently non-isomorphic |
| `Redaction.kt` | 335 | `isCookieParameterType` trims input a closed enum name can never carry; diverges from `isCookieHeaderName`, which does not trim | ⚠️ Warning | Unreachable normalisation that undercuts the KDoc's own exact-comparison argument |
| `BountyPromptTagResolver.kt` | 113-156 | New control added to a class with **zero instantiations** in `src/main/kotlin`, while the reachable defect in the same function (tag value never passes `Redaction.apply`) is left open | ⚠️ Warning | Also makes `Redaction.kt:488-491`'s "six callers" list factually wrong |
| `ParameterCarrierRedactionTest.kt` | 658-665 | `paramsExtractLines` re-types the production format expression | ⚠️ Warning | The *security* assertions are sound (they drive the real `sanitizeParameters`); only the line-shape claim is asserted against a copy |
| `CookieCarrierInventoryTest.kt` | 340-372 | Exact multiset pinned over the **entire** `src/main/kotlin` tree, incl. `"Content-Type" to 6`, `"Origin" to 1`, `"Referer" to 1` | ⚠️ Warning | High false-positive tripwire; predictable outcome is a contributor bumping the number without re-reading any consumer |
| `CookieCarrierInventoryTest.kt` | 534-556 | `ISSUE_DETAIL_CARRIER_DISPOSITION` — a 20-line `const val` no assertion reads | ⚠️ Warning | Prose compiled into a class file |
| `McpToolHelpers.kt` | 317, 343 | `LinkedHashMap` keyed on header name collapses repeated headers | ⚠️ Warning | Not a cookie leak (survivor is `[STRIPPED]`), but the MCP tools hand an agent a provably incomplete view. Pinned as accepted (AR-27-03, CP-27-02-01) |
| `McpToolHelpersTest.kt` / `PassiveAiScannerHeaderAdmissionTest.kt` | 398-440 / 49-108 | `@ResourceLock(Resources.LOCALE)` on locale-mutating methods, with parallel execution never enabled | ⚠️ Warning | The lock is inert; `PassiveAiScannerHeaderAdmissionTest.tearDown` carries no lock and would flake the day parallelism is switched on |
| `Redaction.kt` | 218-240 | Escaped branch has no string-open boundary; real-line branch allows no leading whitespace | ⚠️ Warning | Measured: canonical `Cookie:` survives STRICT at a JSON-string open. New this round |

**Debt-marker gate:** no `TBD` / `FIXME` / `XXX` in any of the 16 files this phase modified. ✓

---

## What This Phase Got Right

Recorded deliberately, because a report that only lists failures misrepresents the work.

- **The round-3 fix is the first one keyed on the data's SOURCE.** `isCookieParameterType` reads
  `HttpParameterType.name` and can't be defeated by a format change. That is a genuinely different
  class of control from the three that were refuted.
- **The records are the most honest artefact in the repo.** Three reopenings preserved verbatim;
  `threats_open` computed rather than written, *with its population stated*; a standing rule that
  grew a clause after each failure; a milestone-audit heading qualified rather than rewritten;
  `REQUIREMENTS.md` left untouched on principle.
- **The phase refused to grade its own homework.** "PHASE 27 COMPLETES WITH PRIV-05 NOT SATISFIED"
  is in `ROADMAP.md`, not buried in a SUMMARY, and AR-27-08 has a named successor with
  source-cited mechanism and reachability. That is the opposite of the pattern that created this
  phase.
- **Auto-selected checkpoint provenance was disclosed.** `26-SECURITY.md:228-232` states plainly that
  AR-27-04's disposition came from `mode: yolo`, not from a maintainer. Most phases would have let
  that read as a decision.
- **Tripwires state their own bounds before their first assertion**, and each names what it cannot
  see. `CookieCarrierInventoryTest`'s KDoc explicitly warns against being quoted as proof of
  coverage.

---

## Gaps Summary

Phase 27 closed the three carriers it enumerated. It did not close PRIV-05, and it says so.

The problem this round found is **not** the recorded deferral — that is owned by Phase 28 and I did
not relitigate it. The problem is that **the phase's own completeness claims are again wider than
its measurements**, in the one place a reader is least likely to look: its test suite.

Two green tests, both committed by this phase, assert that a sensitive value survives a redacting
policy. One of them (`api.example.com` under STRICT) pins a finding that IS on the record, so the
damage is confined to the artefact. The other (`my_cookie` under STRICT and BALANCED) pins a **live
cookie-value disclosure on the prompt path that appears in no security record at all** — and does
so with a failure message telling the next engineer not to fix it. That is precisely the mechanism
by which this threat was closed wrongly three times: a green test standing in for a measurement
nobody re-scoped.

The fix for the first gap is small and points in the safe direction —
`COOKIE_NAME_PART = "[A-Za-z0-9_-]*"`, flip the pinning assertion, add `X_Cookie` and
`session_cookie` to the corpus. The one-directional parity invariant stays true unchanged. If the
maintainer prefers to accept rather than fix, that is legitimate, but it must become a numbered
finding with a severity and an owner alongside AR-27-04/06/07/08, and the accepting test must move
to a `PrivacyMode.OFF` fixture.

**A fourth wrong closure would be confirming that the underscore case is fine because a green test
says so. It is not fine, and the test is why.**

---

_Verified: 2026-08-26_
_Verifier: Claude (gsd-verifier), round 3_
_Method: shipped compiled classes (`build/classes/kotlin/main`, JDK 21) driven from a standalone Java harness; targeted named-test runs; source read at every hop. No SUMMARY claim was accepted as evidence._
