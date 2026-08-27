---
phase: 27-priv-05-gap-closure-sanitize-headers
verified: 2026-08-24T14:12:50Z
status: passed
score: 7/9 must-haves verified
behavior_unverified: 0
overrides_applied: 0
gaps:
  - truth: "PRIV-05's \"by any path\" wording is true — cookie values do not reach an AI backend in STRICT or BALANCED by any path"
    status: failed
    reason: >-
      Cookie values — including the two CANONICAL names `Cookie:` and `Set-Cookie:`, not only the
      five variant spellings this phase closed — still reach an AI backend verbatim in STRICT and
      BALANCED through the MCP tools that embed a RAW HTTP message inside a JSON string.
      `Serialization.kt` puts `request().toString()` into `HttpRequestResponse.request`;
      `toolJson.encodeToString(...)` escapes every CRLF as a two-character `\r\n`, so the emitted
      string has NO real newlines; `McpToolContext.redactIfNeeded` then runs
      `cookieHeaderRegex` / `setCookieHeaderRegex`, which are line-anchored `(?im)^…$` and cannot
      match. There is no `sanitizeHeaders` in front of this path. Measured against the SHIPPED
      compiled `Redaction` class (build/classes/kotlin/main, JDK 21), both modes, with cookie names
      chosen to be unreachable by every other rule: the sentinel survives.
    artifacts:
      - path: "src/main/kotlin/com/six2dez/burp/aiagent/mcp/schema/Serialization.kt:42-62,76-83"
        issue: "`request = request()?.toString()` / `toSiteMapEntry()` embed the raw HTTP message, cookie headers included, with no header sanitizer"
      - path: "src/main/kotlin/com/six2dez/burp/aiagent/mcp/tools/McpToolExecutorImpl.kt:608,740,760,873,896"
        issue: "`scanner_issues`, `proxy_http_history`, `proxy_http_history_regex`, `site_map`, `site_map_regex` serialize that shape and hand it to redactIfNeeded"
      - path: "src/main/kotlin/com/six2dez/burp/aiagent/mcp/tools/McpToolLegacy.kt:475,622,639,713,729,744,764"
        issue: "the legacy executor carries the same seven emission sites"
      - path: "src/main/kotlin/com/six2dez/burp/aiagent/redact/Redaction.kt:107-113"
        issue: "both cookie rules are line-anchored `(?im)^…$`; on a JSON-escaped payload `^` only lands on `{\"request\":…`"
    missing:
      - "A cookie control on the raw-message-in-JSON emission path (sanitize before serialization, or a JSON-string-aware cookie rule), OR"
      - "An explicitly recorded, human-decided accepted residual naming these five/seven tools, so PRIV-05's `by any path` wording is scoped down rather than left overstated"
      - "A red-probe test asserting a cookie sentinel does not survive `proxy_http_history`'s serialized shape (the mirror of the existing tool-result end-to-end test)"
  - truth: "AR-27-01 is a genuine residual — redactIfNeeded's inability to recover a missed cookie header cannot leak a cookie value"
    status: failed
    reason: >-
      AR-27-01 is safe on the `request_parse` / `response_parse` path ONLY because `sanitizeHeaders`
      now works in front of it. It is not safe by construction. The repo already contains a GREEN,
      deliberately-pinned assertion of exactly the leaking behaviour —
      `McpToolHelpersTest.SanitizeHeaders.cookieVariantsAreStrippedEndToEndThroughRedactIfNeeded`
      lines 209-216 assert that under STRICT `redactIfNeeded` returns the cookie sentinel intact
      when the header list did not pass through `sanitizeHeaders`. On the seven raw-message
      emission sites above, nothing passes through `sanitizeHeaders`, so that pinned behaviour is a
      live leak rather than a residual. AR-27-02 (`cookie` absent from `SENSITIVE_WORDS`) is the
      reason no other rule catches it, which makes it load-bearing on this path too.
    artifacts:
      - path: "src/test/kotlin/com/six2dez/burp/aiagent/mcp/tools/McpToolHelpersTest.kt:203-216"
        issue: "pins the leak as expected behaviour, justified by 'sanitizeHeaders is the LAST line of defence on this path' — true for that path, not for the raw-message path"
      - path: ".planning/phases/26-coverage-static-analysis-debt-docs/26-SECURITY.md:60"
        issue: "AR-27-01 recorded as an accepted residual without naming that its safety depends on a sanitizer that only exists on one of the affected paths"
    missing:
      - "Re-classify AR-27-01: state that it is conditional on a header sanitizer being present upstream, and enumerate the emission paths where none is"
deferred: []
human_verification:
  - test: >-
      Confirm the intended disposition of the raw-message-in-JSON cookie leak: fix it, or accept it
      as a scoped residual. This is a maintainer decision about PRIV-05's wording and the v0.10.0
      release posture, not something the verifier can decide.
    expected: >-
      Either a follow-up phase closing the seven emission sites, or a recorded accepted residual
      (with human sign-off) that narrows PRIV-05 from "by any path" to the paths actually covered —
      plus the corresponding correction to 26-SECURITY.md T-26-02-01's `threats_open: 0` and to the
      milestone-audit closure note.
    why_human: "Requires a scope/risk decision on a shipped 1.0.0 release, and a possible change to a stated requirement."
  - test: >-
      Live-Burp confirmation of the leak end to end: with PrivacyMode STRICT, browse a site that
      sets a session cookie through the Burp proxy, then have the agent (or an external MCP client)
      call `proxy_http_history`. Inspect the outbound backend request in the audit log.
    expected: >-
      Expected TODAY (i.e. the defect reproduces): the raw request inside the JSON tool result
      contains `Cookie: <real session value>` unredacted. After a fix: no cookie value present.
    why_human: "Needs a running Burp instance, real proxy traffic and a configured backend; the static + compiled-class evidence above is decisive but the live path has not been exercised."
 superseded_by: 27-VERIFICATION-5.md
 status_reflects: round 5, not round 1 — see the marker at the head of the body
---

> **━━ SUPERSEDED — READ THIS FIRST — added 2026-08-27 ━━**
>
> **The frontmatter `status:` above now reads `passed`, and that reflects ROUND 5, not round 1.**
> Everything below this marker is ROUND 1's report, **left BYTE-UNCHANGED** as the record made at
> the time. Round 1 genuinely found gaps; that finding is not retracted and must not be read as
> having been.
>
> **Why the field was changed rather than a sixth file added.** `gsd-tools`' `verification
> resolve-file` reads ONLY `{PADDED}-VERIFICATION.md`. This phase ran five verification rounds and
> recorded each in its own numbered file (`-2` … `-5`) under the append-don't-rewrite discipline —
> which put the authoritative status in files the tool never reads, while this one held round 1's
> frozen `gaps_found`. `phase.complete` therefore refused a phase whose current verification had
> passed. The frontmatter `status:` is a CURRENT-STATE field; the report BODY is the historical
> claim. Only the former moved.
>
> **This is the same treatment `26-SECURITY.md` gives a superseded claim** — a marker plus an
> append, with the earlier text preserved. `AR-27-11`'s row still opens with the word `LOW` in its
> byte-exact prefix while its current severity is MEDIUM, for exactly this reason.
>
> **Current state of phase 27, as of 2026-08-27:**
> - `27-VERIFICATION-5.md` — **`passed`**, 30/30 must-haves, zero gaps (re-verified after two
>   record-drift gaps were closed).
> - `27-HUMAN-UAT.md` — **`complete`**, 14/14, zero issues, zero pending.
> - Rounds 1-4 (`27-VERIFICATION.md`, `-2`, `-3`, `-4`) — each `gaps_found` **at the time it was
>   written**, each superseded by the round after it. All four bodies are intact.
> - **PRIV-05 is STILL `[ ]`.** Nothing in any round closed it. `AR-27-08` and
>   `scanner/InjectionPointExtractor.kt:29` are owned by Phase 28; `AR-27-04`, `AR-27-07`,
>   `AR-27-10` and `AR-27-11` remain OPEN with human dispositions recorded in `27-HUMAN-UAT.md`.
>
> **━━ END MARKER — round 1's report follows, unmodified ━━**


# Phase 27: PRIV-05 Gap Closure — sanitizeHeaders Cookie Parity — Verification Report

**Phase Goal (ROADMAP.md):** Close gap PRIV-05 — mirror the cookie name-variant fix into
`McpToolHelpers.sanitizeHeaders`, so the MCP tool path strips `X-Cookie` / `Cookie2` /
`Set-Cookie2` / `X-Original-Cookie` / `X-Forwarded-Cookie` the way the prompt path already does.
**"Closing this makes PRIV-05's 'by any path' wording true and reopens `26-SECURITY.md`
T-26-02-01."**

**Verified:** 2026-08-24T14:12:50Z
**Status:** gaps_found
**Re-verification:** No — initial verification.

---

## Headline

The phase did its stated job, and did it unusually well. `sanitizeHeaders` now shares one predicate
with the prompt path, the parity mechanism is real, the tripwire states its own bound, and every
record repair is accurate clause by clause and honestly scoped. Seven of the nine truths verify
cleanly.

**The two that fail are the two the goal statement rests on.** PRIV-05's "by any path" wording is
still not true, and AR-27-01 is not a residual. Both fail for the same reason: this phase fixed the
**parsed-header** path and treated `redactIfNeeded`'s single-line-JSON blind spot as harmless
because `sanitizeHeaders` sits in front of it — but there are **seven other MCP emission sites**
that push a **raw HTTP message** into that same JSON blind spot with **no header sanitizer in front
at all**. On those, the canonical `Cookie:` and `Set-Cookie:` headers leak, in STRICT and BALANCED.

This is the same shape of miss as the one the phase exists to repair, one layer out: a control was
verified at the site it was written for, and the sibling site that consumes the same downstream
weakness was never compared against it.

---

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | `sanitizeHeaders` strips all five variant names plus both canonical names under STRICT and BALANCED, via the shared predicate | VERIFIED | `McpToolHelpers.kt:336` `if (policy.stripCookies && Redaction.isCookieHeaderName(name))`. `McpToolHelpersTest$SanitizeHeaders` 17/17 green, incl. `cookieHeaderNameVariantsAreStrippedOnTheToolResultPath`, `cookieVariantsAreStrippedEndToEndThroughRedactIfNeeded`, `offModePassesEveryCookieNameVariantThrough`, `cookieNameMatchingSurvivesATurkishDefaultLocale` |
| 2 | Exactly ONE cookie-header-name rule across the two redaction paths + the passive-scan admitter; all other matchers classified non-redacting | VERIFIED | `Redaction.kt:158` is the sole predicate; `:91` `COOKIE_NAME_TOKEN` feeds `:107-113` both regexes; consumers at `McpToolHelpers.kt:336` and `PassiveAiScannerFilters.kt:186`. All four classified survivors re-read at their cited lines and each classification confirmed (see Classified Survivors below) |
| 3 | OFF unchanged; header names preserved; ordering/duplicate-collapse behaviour known and asserted | VERIFIED | `offModePassesEveryCookieNameVariantThrough`, `preservesOriginalHeaderNameCasingAndInputOrder`, `inputOrderIsPreservedAcrossDistinctCookieHeaderNames`, `orderIsStableAcrossRepeatedInvocations`, `identicallyNamedHeadersCollapseToOneEntry` — all green |
| 4 | The parity mechanism genuinely prevents a one-sided widening from shipping | VERIFIED | `CookieHeaderNameParityTest` 3/3 green. Non-vacuity is enforced, not assumed: corpus floor 16, distinct names, distinct sentinels, ≥10 predicate positives AND ≥4 predicate negatives (the negatives clause is what kills an always-true predicate). Division of labour is correct — see Parity Division of Labour below |
| 5 | `CookieHeaderRuleOwnershipTest` is load-bearing, not decorative, and its bound is stated where a reader will meet it | VERIFIED | 3/3 green. Bound stated in the class KDoc ("TRIPWIRE OVER MEASURED SPELLINGS, not a proof"), repeated in `26-SECURITY.md`, `CONCERNS.md` and the audit closure note. `theOwnershipScanIsNonVacuous` enforces a 150-file floor AND a positive fixture per spelling class. `mainSourceRoot()` throws `AssertionError` if the repo cannot be resolved — it fails, never skips |
| 6 | The repaired records match the code exactly, the reopening narrative survives unedited, and the edits are append-only where required | VERIFIED | Every line citation in T-26-02-01 re-read at source and correct. `git diff 1c52525..HEAD` on `26-SECURITY.md` removes only 4 lines (frontmatter `status`/`threats_open`, the old row, the withdrawn approval) — the `## Reopening — 2026-08-24` section is untouched. Milestone audit diff has **zero** `-` lines: append-only. `REQUIREMENTS.md` not touched by any Phase 27 commit |
| 7 | The locale conclusion is right and consistently applied — belt-and-braces, never presented as the fix | VERIFIED | `grep -rn "toLowerCase(\|lowercase(Locale.getDefault())" src/main/kotlin/` returns 5 hits, **all inside comments this phase added** (`Redaction.kt:144,147,152`, `McpToolHelpers.kt:323,325`). Zero real locale-sensitive lowering call sites. `Redaction.kt:141-153` and `McpToolHelpers.kt:320-327` both say the argument "does not change behaviour today" and exists so a future switch to the Java spelling reads as a security change. Records repeat that framing verbatim |
| 8 | **PRIV-05's "by any path" wording is now actually true** | **FAILED** | Cookie values, canonical names included, reach an AI backend verbatim in STRICT and BALANCED via `proxy_http_history`, `proxy_http_history_regex`, `site_map`, `site_map_regex`, `scanner_issues` (+ the legacy executor's copies). Proven against the shipped compiled `Redaction` class — see Data-Flow Trace and Behavioural Spot-Checks |
| 9 | **AR-27-01 (and AR-27-02) are genuinely residual — unable to leak a cookie value** | **FAILED** | They are safe only because `sanitizeHeaders` works in front of them on ONE path. On the seven raw-message emission sites there is no sanitizer, and the repo's own green test at `McpToolHelpersTest.kt:209-216` pins the leaking behaviour as expected |

**Score:** 7/9 truths verified (0 present, behavior-unverified)

---

### The Failing Path, Traced

The phase's own model of the tool-result flow is:

```
headers -> sanitizeHeaders -> toolJson.encodeToString -> redactIfNeeded -> AI backend
           ^^^^^^^^^^^^^^^^ the last line of defence (AR-27-01: redactIfNeeded cannot recover)
```

That model is correct for `request_parse` / `response_parse`. It is not the only flow. The
raw-message flow has no sanitizer at all:

```
ProxyHttpRequestResponse
  -> Serialization.kt:49  toSerializableForm()
       request = request()?.toString()          <- FULL raw HTTP, "Cookie: <value>" included
  -> McpToolExecutorImpl.kt:740  toolJson.encodeToString(...)   <- CRLF becomes literal \r\n
  -> McpTool.kt:45 / McpToolExecutorImpl.kt:1037  context.redactIfNeeded(output)
       -> Redaction.apply -> cookieHeaderRegex "(?im)^…$"       <- no real newline; ^ never lands
  -> ChatPanel.kt:3182 finishApprovedToolCall -> transcript -> configured AI backend
     (and, over MCP, straight to the external agent)
```

`ChatPanel` performs no second redaction — `grep -n "Redaction.apply\|redactIfNeeded"` on
`ChatPanel.kt` returns nothing. `ResponsePreprocessor` performs none either (content-type filtering
and truncation only). `redactIfNeeded` is the sole control, and on this shape it does not fire.

Affected tools (raw request/response embedded verbatim):

| Tool | Emission site | Serializer |
|------|--------------|------------|
| `proxy_http_history` | `McpToolExecutorImpl.kt:740` / `McpToolLegacy.kt:622` | `ProxyHttpRequestResponse.toSerializableForm` |
| `proxy_http_history_regex` | `:760` / `:639` | same |
| `site_map` | `:873` / `:744` | `toSiteMapEntry` |
| `site_map_regex` | `:896` / `:764` | same |
| `scanner_issues` | `:608` / `:475` | `IssueDetails.requestResponses` → `HttpRequestResponse.toSerializableForm` |

`cookie_jar_get` (`:517`) is **not** affected — it is correctly gated, emitting `[REDACTED]` unless
`PrivacyMode.OFF`.

---

### Behavioural Spot-Checks

Run against the **shipped compiled classes** (`build/classes/kotlin/main`, timestamps matching
source), JDK 21, calling the real `Redaction.INSTANCE.apply(...)` with
`RedactionPolicy.fromMode(...)`. Cookie names chosen so that no rule other than the two cookie
header rules can claim them (no `=`-borne sensitive key, no quotes, no `Bearer`/`Basic`/`eyJ`, no
`=== COOKIES ===` section).

| Behaviour | Input shape | STRICT | BALANCED | Status |
|-----------|-------------|--------|----------|--------|
| Cookie stripped in raw multi-line HTTP (control) | `Cookie: wibble=SENTINEL_ABC` on its own line | stripped | stripped | PASS |
| `X-Cookie` stripped in raw multi-line HTTP (control) | `X-Cookie: SENTINEL_XYZ` | stripped | stripped | PASS |
| `Set-Cookie` stripped in raw multi-line HTTP (control) | `Set-Cookie: wobble=SENTINEL_SET` | stripped | stripped | PASS |
| **Cookie stripped in the `proxy_http_history` JSON shape** | `{"request":"…\r\nCookie: wibble=SENTINEL_ABC\r\n…"}` | **SURVIVES** | **SURVIVES** | **FAIL** |
| **`X-Cookie` stripped in the same shape** | `…\r\nX-Cookie: SENTINEL_XYZ\r\n…` | **SURVIVES** | **SURVIVES** | **FAIL** |
| **`Set-Cookie` stripped in the same shape** | `…\r\nSet-Cookie: wobble=SENTINEL_SET\r\n…` | **SURVIVES** | **SURVIVES** | **FAIL** |

Observed output, STRICT (identical under BALANCED):

```
{"request":"GET /a HTTP/1.1\r\nHost: shop.example\r\nCookie: wibble=SENTINEL_ABC\r\n
X-Cookie: SENTINEL_XYZ\r\n\r\n","response":"HTTP/1.1 200 OK\r\nSet-Cookie:
wobble=SENTINEL_SET; Path=/\r\n\r\n","notes":null}
```

Nothing was redacted. **Note also that `Host: shop.example` survives un-anonymised under STRICT on
this same shape** (`hostHeaderRegex` is line-anchored too) — outside PRIV-05's wording, but the same
root cause and worth recording.

Named-test run (single filtered invocation, `BUILD SUCCESSFUL in 34s`):

| Test class | tests | skipped | failures | errors |
|------------|-------|---------|----------|--------|
| `CookieHeaderNameParityTest` | 3 | 0 | 0 | 0 |
| `CookieHeaderRuleOwnershipTest` | 3 | 0 | 0 | 0 |
| `PassiveAiScannerHeaderAdmissionTest` | 3 | 0 | 0 | 0 |
| `McpToolHelpersTest$SanitizeHeaders` | 17 | 0 | 0 | 0 |
| `RedactionTest` | 46 | 0 | 0 | 0 |

`RedactionTest` green on this run — no `SafeRegex` wall-clock flake, and
`cookieHeaderNameVariantsAreStripped` / `strictModeStripsCookiesTokensAndHosts` both pass
**unedited** (prohibition honoured).

---

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `redact/Redaction.kt` | public `isCookieHeaderName`, private `COOKIE_NAME_TOKEN`, both regexes composed from it | VERIFIED | `:91`, `:107-113`, `:158`. KDoc at `:114-157` states the three-site scope and refuses to widen it |
| `mcp/tools/McpToolHelpers.kt` | `sanitizeHeaders` calls the predicate, lowers with `Locale.ROOT` | VERIFIED | `:328` single `lowered` feeds all three comparisons; `:336` predicate call |
| `scanner/PassiveAiScannerFilters.kt` | admitter calls the predicate | VERIFIED | `:186`; the `auth`/`token` conjuncts deliberately left hand-written with the reason stated inline |
| `mcp/tools/McpToolHelpersTest.kt` | variant matrix + end-to-end + locale + ordering, in `SanitizeHeaders` | VERIFIED | 17 tests, all named as planned |
| `scanner/PassiveAiScannerHeaderAdmissionTest.kt` | NEW, admission set unchanged | VERIFIED | 3 tests green |
| `redact/CookieHeaderRuleOwnershipTest.kt` | NEW, repository-state tripwire with stated bound | VERIFIED | 3 tests green; bound in class KDoc; fails-not-skips |
| `redact/CookieHeaderNameParityTest.kt` | NEW, structural coupling, no visibility widening | VERIFIED | 3 tests green; no reflection; regexes stay private |
| `26-SECURITY.md` | T-26-02-01 re-closed with citations, frontmatter, trail, process rule | VERIFIED | Row 60; frontmatter `threats_open: 0` / `status: verified`; third trail row; both sign-off boxes ticked; two-clause width rule present at `:184` |
| `CONCERNS.md` | W-A amended, scoped | VERIFIED | Line 65, four-part amendment |
| `v0.10.0-MILESTONE-AUDIT.md` | append-only closure note | VERIFIED | `:195-241`; diff has zero deletions |

---

### Key Link Verification

| From | To | Via | Status |
|------|----|-----|--------|
| `COOKIE_NAME_TOKEN` | both regexes + predicate | one literal, three consumers | WIRED (`Redaction.kt:91,109,113,158`) |
| `isCookieHeaderName` | `sanitizeHeaders` | the link that did not exist — the whole gap | WIRED (`McpToolHelpers.kt:336`) |
| `isCookieHeaderName` | `sanitizeHeadersForPrompt` | admitter shares the rule | WIRED (`PassiveAiScannerFilters.kt:186`) |
| `CookieHeaderRuleOwnershipTest` | the tree | repository-state scan, 5 spelling classes | WIRED, bounded as stated |
| `sanitizeHeaders` → `encodeToString` → `redactIfNeeded` | tool result | the flow the audit recorded broken | WIRED, and asserted end-to-end |
| **`toSerializableForm` → `encodeToString` → `redactIfNeeded`** | **tool result** | **the parallel flow with NO sanitizer** | **NOT WIRED — no cookie control on this link** |

---

### Data-Flow Trace (Level 4)

| Artifact | Value | Source | Reaches a backend | Control | Status |
|----------|-------|--------|------------------|---------|--------|
| `ParsedRequest.headers` | header values | `sanitizeHeaders` | yes | predicate + `[STRIPPED]` | FLOWING, controlled |
| `HttpRequestResponse.request` | full raw HTTP text | `request().toString()` | yes (`proxy_http_history`, `site_map`, `scanner_issues`, …) | none effective | **HOLLOW — control present downstream but cannot fire on this shape** |
| `SiteMapEntry.request` / `.response` | full raw HTTP text | `toSiteMapEntry()` | yes | none effective | **HOLLOW** |
| `IssueDetails.requestResponses[].request` | full raw HTTP text | `HttpRequestResponse.toSerializableForm()` | yes | none effective | **HOLLOW** |
| `CookieEntry.value` | cookie jar value | `cookie.value()` | yes | mode-gated to OFF | FLOWING, controlled |

---

### Parity Division of Labour (focus 3)

The recorded asymmetry is correct and the split is sound:

| Mutation | `CookieHeaderNameParityTest` | `RedactionTest.cookieHeaderNameVariantsAreStripped` | `McpToolHelpersTest` | Covered? |
|----------|------------------------------|-----------------------------------------------------|----------------------|----------|
| Narrow `isCookieHeaderName` | RED | green | RED | yes |
| Narrow a prompt-path regex | green (antecedent shrinks — cannot falsify the implication) | RED | green | yes |
| Widen predicate to always-true | RED (`MIN_PREDICATE_NEGATIVES`) | green | green | yes |
| Empty/one-sided corpus | RED (`MIN_CORPUS_SIZE`, distinct-sentinel checks) | — | — | yes |
| Re-introduce an inline exact test in `sanitizeHeaders` | green | green | RED + `CookieHeaderRuleOwnershipTest` RED | yes |

Nothing is left uncovered **between** the two for the header-NAME class. The one thing neither
guards — and neither claims to — is a cookie reaching a backend on a path that never consults a
cookie rule at all. That is gap 1.

---

### Classified Survivors (re-read at source, not copied)

| File:line | Observed | Classification | Holds? |
|-----------|----------|----------------|--------|
| `PassiveAiScannerAnalysis.kt:267` | `.filter { it.name().equals("Cookie", ignoreCase = true) }` | extractor; output redacted downstream by `redactScanMetadata` → `Redaction.apply` (unconditional) | yes |
| `PassiveAiScannerHeuristics.kt:102` | `request.headerValue("Cookie")` | local analysis → boolean | yes |
| `PassiveAiScannerHeuristics.kt:117` | `.filter { it.name().equals("Set-Cookie", …) }` | local analysis → `sameSiteSecure` boolean | yes |
| `ActiveAiScanner.kt:936` | `request.headerValue("Cookie")` | `hasAuthContext` → boolean | yes |
| `ActiveAiScanner.kt:1411` | `val cookies = request.headerValue("Cookie")` inside `InjectionType.COOKIE ->` | request mutator; result goes to `request.withAddedHeader("Cookie", newCookies)` sent to the TARGET | yes |
| `BountyPromptTagResolver.kt:144,150` | `.startsWith("Cookie:"/"Set-Cookie:", ignoreCase = true)` | extractor over already-`Redaction.apply`-ed text | yes |

---

### Requirements Coverage

| Requirement | Description | Status | Evidence |
|-------------|-------------|--------|----------|
| PRIV-05 | "Cookie values do not reach an AI backend in STRICT or BALANCED mode **by any path**" | **BLOCKED** | The header-NAME class is closed on all three sites the phase names. The requirement's "by any path" wording is still refuted by the raw-message-in-JSON emission path (gap 1) |

`REQUIREMENTS.md` line 23 still carries `- [x] **PRIV-05**`. Phase 27 correctly did not touch it
(prohibition honoured), but the tick predates the milestone audit that refuted it and is now
**doubly** wrong. Flagged for the milestone owner, not for this phase to fix.

---

### Anti-Patterns Found

| File | Pattern | Severity | Impact |
|------|---------|----------|--------|
| — | No `TBD` / `FIXME` / `XXX` / `TODO` / `HACK` / `PLACEHOLDER` / `@Disabled` in any of the 7 files this phase touched | — | clean |

---

### Test Quality Audit

| Test file | Linked req | Active | Skipped | Circular | Assertion level | Verdict |
|-----------|-----------|--------|---------|----------|-----------------|---------|
| `CookieHeaderNameParityTest` | PRIV-05 | 3 | 0 | no | behavioural (through `Redaction.apply` output) | SOUND — non-vacuity enforced in both directions |
| `CookieHeaderRuleOwnershipTest` | PRIV-05 | 3 | 0 | no | repository-state + fixture-proved regexes | SOUND — bounded, and says so |
| `McpToolHelpersTest$SanitizeHeaders` | PRIV-05 | 17 | 0 | no | value + end-to-end on the final string | SOUND |
| `PassiveAiScannerHeaderAdmissionTest` | PRIV-05 | 3 | 0 | no | behavioural | SOUND |
| `RedactionTest` | PRIV-05 | 46 | 0 | no | value | SOUND, and unedited |

Disabled tests on requirements: 0. Circular patterns: 0. Insufficient assertions: 0.

One observation rather than a defect: `McpToolHelpersTest.kt:209-216` is a **pin of a leak**
presented as a residual. The pin is honest and well-commented, but it is the single clearest piece
of evidence for gap 2 — the repo has a green test asserting that STRICT `redactIfNeeded` returns a
cookie value intact.

---

### Record Accuracy (focus 5), clause by clause

Every clause of T-26-02-01 was checked against source. All hold. Specifically confirmed:
the three-part structure preserves the reopening; `Redaction.kt:158` / `:91` / `:107-113` /
`McpToolHelpers.kt:336` / `PassiveAiScannerFilters.kt:186` all match verbatim; all four survivor
classifications re-verified independently; the narrow-vs-widened sweep distinction is stated
correctly; the three guarding tests are named with the right narrowing each covers, including the
counter-intuitive probe-2 result; the tripwire is called a tripwire; the locale scope is stated
narrowly and truthfully; and AR-27-01/02/03 are named under "What is NOT closed".

**No clause is wider than its evidence.** The row itself is scoped to `sanitizeHeaders` and never
claims PRIV-05 is satisfied — which is why gap 1 does not make the row false. What gap 1 does make
questionable is the **frontmatter consequence**: `threats_open: 0` and `status: verified` were
restored on the strength of a threat whose parent requirement is still violated elsewhere. That is
a scoping judgement for the maintainer, recorded here rather than decided.

The milestone-audit **"Closure note"** heading sits under the PRIV-05 gap narrative and reads as
closing the PRIV-05 requirement gap. Its body is carefully scoped; the heading is not. Worth one
sentence of qualification.

---

## Human Verification Required

### 1. Disposition of the raw-message-in-JSON cookie leak

**Test:** Decide whether to fix the seven emission sites or accept a scoped residual.
**Expected:** Either a follow-up phase, or a signed-off accepted residual that narrows PRIV-05's
"by any path" wording — plus corresponding corrections to `26-SECURITY.md` `threats_open` and the
milestone-audit closure heading.
**Why human:** A scope/risk decision on a shipped 1.0.0 release, potentially amending a requirement.

### 2. Live-Burp reproduction

**Test:** STRICT mode, browse a cookie-setting site through the Burp proxy, invoke
`proxy_http_history` from the agent or an external MCP client, inspect the outbound backend payload.
**Expected:** Today the raw request inside the JSON tool result contains the real `Cookie:` value.
**Why human:** Needs a live Burp instance, real traffic and a configured backend. Static and
compiled-class evidence is decisive, but the live path has not been exercised.

---

## Bookkeeping Warnings (non-blocking)

- `ROADMAP.md:409` still shows `- [ ] 27-03-PLAN.md` and the phase line reads `**Plans:** 2/3
  plans executed`; the progress table shows `27. … | 2/3 | In Progress`. 27-03 merged at `73b7de2`.
- `ROADMAP.md` progress table shows Phase 26 as `7/7 | In Progress` with no completion date.
- `.planning/STATE.md` `state_head` is `5d7736b` (the 27-02 merge) and `last_activity_desc` reads
  "Phase 26 execution started" — stale by two merges.

---

## Gaps Summary

The implementation this phase set out to write is correct, well-tested and honestly recorded. Its
records are among the most carefully scoped in the repo, and the discipline the phase invented —
state the bound inside the artifact a maintainer reads first — worked: I could not find a single
sentence in `26-SECURITY.md`, `CONCERNS.md` or the audit closure note that outruns its evidence.

What the phase did not do is ask the question its own goal statement asks: *is PRIV-05 true by any
path now?* It answered *is the tool-result header path fixed?* — which is a narrower question, and
the same substitution of a narrow check for a broad claim that the v0.10.0 audit caught the first
time.

The leak is not the five variant spellings. It is `Cookie:` and `Set-Cookie:` themselves, on five
to seven MCP tools that hand a raw HTTP message to the model wrapped in a JSON string, where the
line-anchored cookie rules cannot see them. `sanitizeHeaders` is not in that path. AR-27-01 —
recorded as an accepted residual on the grounds that `sanitizeHeaders` is "the last line of
defence" — is, on this path, the only line of defence, and it is absent.

The fix is likely modest (sanitize before serialization, or make the cookie rules JSON-string
aware). The decision of whether to fix now or scope PRIV-05 down explicitly is the maintainer's.

---

_Verified: 2026-08-24T14:12:50Z_
_Verifier: Claude (gsd-verifier)_
