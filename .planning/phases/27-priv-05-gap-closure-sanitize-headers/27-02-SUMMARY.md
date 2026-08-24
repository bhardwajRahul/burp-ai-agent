---
phase: 27-priv-05-gap-closure-sanitize-headers
plan: 02
subsystem: privacy
tags: [kotlin, redaction, cookies, mcp, junit5, parity-test, wire-contract]

requires:
  - phase: 27-priv-05-gap-closure-sanitize-headers
    provides: "Redaction.isCookieHeaderName — the public shared predicate this plan's parity test asserts against, and the sanitizeHeaders behaviour Task 3 pins"
  - phase: 21-priv-05-cookie-rules
    provides: "cookieHeaderRegex / setCookieHeaderRegex and COOKIE_NAME_PART, the prompt-path half of the parity invariant"
  - phase: 26-mcp-tool-helpers
    provides: "McpToolHelpers.sanitizeHeaders and its LinkedHashMap return shape"
provides:
  - "CookieHeaderNameParityTest — the mechanism that turns a one-sided widening of the cookie rule into a failing test instead of a shipped leak"
  - "Measured directionality: the parity test guards a PREDICATE narrowing; RedactionTest.cookieHeaderNameVariantsAreStripped guards a PROMPT-PATH narrowing"
  - "CP-27-02-01 — the recorded decision to keep Map<String, String> as the MCP tool-result header shape"
  - "AR-27-03 — accepted residual: byte-identically-named headers collapse to one entry in the tool-result header map"
  - "Order, stability and duplicate-collapse assertions on sanitizeHeaders, with the collapse proven to leak no original value"
affects: [27-03, security-register, privacy, mcp, roadmap-backlog]

actuals:
  tokens: 4886
  tasks: 2
  commits: 2

tech-stack:
  added: []
  patterns:
    - "Parity test as a structural coupling between two independent implementations of one security rule, asserted through observable behaviour rather than reflection"
    - "The corpus guard as its own @Test, with minimum counts on BOTH polarities, so the invariant cannot pass vacuously"
    - "An invariant stated as a one-directional implication, with the forbidden converse named in the file header so a future reader meets the design intent before the temptation"
    - "A pre-existing residual pinned by a named test that says in its own KDoc that it asserts CURRENT behaviour, not an aspiration"

key-files:
  created:
    - src/test/kotlin/com/six2dez/burp/aiagent/redact/CookieHeaderNameParityTest.kt
  modified:
    - src/test/kotlin/com/six2dez/burp/aiagent/mcp/tools/McpToolHelpersTest.kt

key-decisions:
  - "CP-27-02-01 = keep-map-plus-backlog (option C), decided by the human user, not by the executor. sanitizeHeaders keeps returning Map<String, String>; the entry-list change is carried as a ROADMAP backlog item rather than shipped inside a privacy gap-closure phase."
  - "The parity invariant is asserted in ONE direction only — promptStrips(N) => isCookieHeaderName(N). The converse is false by design (my_cookie) and is prohibited from this file."
  - "Task 3's TDD RED was obtained by asserting the `ordering` edge as LITERALLY WRITTEN (3 Set-Cookie headers => 3 entries) and observing it fail, rather than by inventing a wrong expectation. That failure IS the measurement behind AR-27-03."
  - "An unplanned fourth probe (LinkedHashMap -> HashMap) was added because nothing in the plan proved the order assertions were load-bearing rather than accidentally true."

patterns-established:
  - "Verify a new test RAN, by name, in the JUnit XML — a green suite is not evidence that a test executed (this plan produced a silently non-running test and caught it only by inspecting the compiled class)"
  - "Record which mutation direction each guard covers, measured by probe, so a later audit can point at the measurement instead of re-deriving it"

requirements-completed: [PRIV-05]

coverage:
  - id: D1
    description: "Every header name the prompt path strips is matched by the shared predicate, over a 17-name corpus, under STRICT and BALANCED — so a widening applied to one redaction path and forgotten in the other fails a test"
    requirement: PRIV-05
    verification:
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/redact/CookieHeaderNameParityTest.kt#everyNameThePromptPathStripsIsMatchedByTheSharedPredicate"
        status: pass
    human_judgment: false
  - id: D2
    description: "The parity corpus is proven non-vacuous and two-polarity by its own test (>=16 names, >=10 predicate-positive, >=4 predicate-negative), so the invariant above cannot pass over an empty or one-sided list"
    requirement: PRIV-05
    verification:
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/redact/CookieHeaderNameParityTest.kt#parityCorpusIsNonEmptyAndContainsBothPolarities"
        status: pass
    human_judgment: false
  - id: D3
    description: "The predicate is deliberately WIDER than the two regexes, asserted positively via my_cookie in both directions, and the converse implication is asserted nowhere in the file"
    requirement: PRIV-05
    verification:
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/redact/CookieHeaderNameParityTest.kt#thePredicateIsDeliberatelyWiderThanTheTwoRegexes"
        status: pass
    human_judgment: false
  - id: D4
    description: "The `ordering` edge for DISTINCT header names: five distinct names yield five entries in input order, case-only variants stay apart, and the non-cookie control returns byte-identical"
    requirement: PRIV-05
    verification:
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/mcp/tools/McpToolHelpersTest.kt#inputOrderIsPreservedAcrossDistinctCookieHeaderNames"
        status: pass
    human_judgment: false
  - id: D5
    description: "Byte-identically-named headers collapse to ONE entry at the first one's position (AR-27-03), and no original cookie value survives the collapse — the clause that makes the residual privacy-safe rather than merely lossy"
    requirement: PRIV-05
    verification:
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/mcp/tools/McpToolHelpersTest.kt#identicallyNamedHeadersCollapseToOneEntry"
        status: pass
    human_judgment: false
  - id: D6
    description: "The tool-result header order is stable across repeated invocations over the same input"
    requirement: PRIV-05
    verification:
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/mcp/tools/McpToolHelpersTest.kt#orderIsStableAcrossRepeatedInvocations"
        status: pass
    human_judgment: false
  - id: D7
    description: "CP-27-02-01 recorded with its options, the chosen id, who decided it and its one-way rating; AR-27-03 recorded as accepted residual; the ROADMAP backlog entry named as an obligation owed by the orchestrator"
    requirement: PRIV-05
    verification: []
    human_judgment: true
    rationale: "A recorded decision and an outstanding backlog obligation cannot be asserted by a test. The backlog line is not yet in ROADMAP.md — this executor is worktree-isolated and must not write it — so a human must confirm the obligation was discharged after the merge before this deliverable is certified."

duration: 20 min
completed: 2026-08-24
status: complete
---

# Phase 27 Plan 02: The Parity Test and the Tool-Result Header Shape Summary

**`CookieHeaderNameParityTest` makes a one-sided widening of the cookie-header-name rule fail a test instead of shipping, and CP-27-02-01 keeps the `request_parse` / `response_parse` header map as it is — closing the `ordering` edge in two named halves rather than one quiet omission.**

## Performance

- **Duration:** 20 min of executor wall-clock (excludes the CP-27-02-01 human decision wait)
- **Started:** 2026-08-24T12:44Z
- **Completed:** 2026-08-24T13:04Z
- **Tasks:** 2 executed (Task 1, Task 3) + 1 checkpoint resolved by the human (Task 2)
- **Files modified:** 2 (1 created, 1 modified) — no production files

## Accomplishments

- **The agreement between the two redaction paths is now structural.** `CookieHeaderNameParityTest` asserts `promptStrips(N) ⇒ isCookieHeaderName(N)` over a 17-name corpus under STRICT and BALANCED, behaviourally through `Redaction.apply`. No reflection, no visibility widening — the two cookie regexes stay `private`.
- **Which guard covers which mutation direction is MEASURED, not asserted.** Probe 1 narrows the predicate and turns the parity test red; probe 2 narrows a prompt-path regex and leaves the parity test green while `RedactionTest.cookieHeaderNameVariantsAreStripped` goes red. That pair is the evidence the v0.10.0 audit needed and did not have, and plan 27-03 can cite it by name.
- **The corpus guard is proven load-bearing.** Probe 3 widens the predicate to always-true; only `parityCorpusIsNonEmptyAndContainsBothPolarities` goes red, on exactly its "at least 4 negatives" clause. The parity implication itself stays green — trivially — which is precisely why the guard has to be a separate test.
- **CP-27-02-01 resolved by the human as `keep-map-plus-backlog`.** No wire shape moved inside a gap-closure phase.
- **The duplicate collapse is now a KNOWN state with a measurement behind it.** A probe asserting the edge as literally written reported `expected: <3> but was: <1>`. AR-27-03 is recorded, and the test asserts that none of the three original values survives — the clause that makes the residual privacy-safe rather than merely lossy.
- **A silently non-running test was caught and fixed.** The first insertion of Task 3's tests landed inside another test method and compiled as a local function; the suite went green with the test never executing. See Deviations — this is the exact failure class this phase's own prohibitions name.

## Task Commits

1. **Task 1: `CookieHeaderNameParityTest` — make a one-sided widening impossible to ship** — `33b3c33` (test)
2. **Task 2: CP-27-02-01** — checkpoint, decided by the human user; no commit
3. **Task 3: order, stability and duplicate-collapse assertions on the tool-result path** — `b7519c5` (test)

## Files Created/Modified

- `src/test/kotlin/.../redact/CookieHeaderNameParityTest.kt` — NEW, 3 tests, 215 lines. Carries the parity corpus, the exclusion reasoning for four header names, and the direction note.
- `src/test/kotlin/.../mcp/tools/McpToolHelpersTest.kt` — 3 tests appended to the `SanitizeHeaders` nested class (+108 lines).

**No production file was changed by this plan.** `git diff --quiet src/main/kotlin` exits 0.

## Test counts

| Point | `*McpToolHelpersTest` | `*CookieHeaderNameParityTest` |
|---|---|---|
| End of plan 27-01 Task 2 (recorded baseline) | 73 | — |
| End of this plan | **76 (+3)** | **3 (new)** |

The `SanitizeHeaders` nested class went 14 → 17. Each of the three new tests was verified present **by name** in `TEST-…McpToolHelpersTest$SanitizeHeaders.xml`, not merely inferred from a green build — see Deviation 1 for why that check is now mandatory.

## CP-27-02-01 — the MCP tool-result header shape

**Decision: `keep-map-plus-backlog` (option C). Decided by the human user. Reversibility rating: `one-way`. Date: 2026-08-24.**

### What was measured before the decision was put to the human

Read from the shipped source, not from the plan's description of it:

| Fact | Location |
|---|---|
| `val sanitized = LinkedHashMap<String, String>()` | `McpToolHelpers.kt:317` |
| `sanitized[name] = value`, keyed on the ORIGINAL-cased name | `McpToolHelpers.kt:345` |
| `ParsedRequest.headers: Map<String, String>` | `McpToolModels.kt:144` |
| `ParsedResponse.headers: Map<String, String>` | `McpToolModels.kt:153` |
| Call site 1 (request, executor) | `McpToolExecutorImpl.kt:369` |
| Call site 2 (response, executor) | `McpToolExecutorImpl.kt:387` |
| Call site 3 (request, legacy) | `McpToolLegacy.kt:179` |
| Call site 4 (response, legacy) | `McpToolLegacy.kt:201` |

Exactly four call sites, confirmed by grep rather than estimated. `SendHttp2Request.headers` (`McpToolModels.kt:51`) is a separate **input** model, untouched by `sanitizeHeaders`, and was excluded from the blast radius quoted to the human so the count would not be inflated.

The collapse was then confirmed by TEST as well as by reading — see the RED probe under Task 3 below.

### The options as presented

| id | Option | Outcome |
|---|---|---|
| `keep-map` | Keep `Map<String, String>`, record AR-27-03 | No wire change; residual carried in the threat register only |
| `entry-list` | Change to an ordered list of name/value entries | Satisfies the edge literally; breaks the `request_parse` / `response_parse` schema for every external MCP client in a shipped 1.0.0 release. Plan would have STOPPED here. |
| `keep-map-plus-backlog` | `keep-map` now, plus a ROADMAP backlog entry | Same code outcome as A; the signal loss stays visible as an explicit backlog item |

### Rationale for the choice

Two things are true at once and were kept apart in the framing the decision was made on:

- The collapse is **privacy-safe**. Every entry that collapses was already destined for `[STRIPPED]`, so no cookie value escapes. PRIV-05 is unaffected either way.
- The collapse **costs analysis signal**. The analyst and the model see one `Set-Cookie` where the response carried three, and multiple `Set-Cookie` headers are the normal case in a real HTTP response — not a corner case.

Because option B buys analysis signal rather than privacy, shipping it inside a privacy gap-closure phase would move a published contract for a reason unrelated to the gap being closed — which is how a breaking change ships without a changelog entry. Option C preserves the improvement as a visible obligation instead of burying it in a threat register.

### Outstanding obligation — the ROADMAP backlog entry is NOT yet written

Option C requires a line in `.planning/ROADMAP.md`'s Backlog section naming `ParsedRequest.headers` / `ParsedResponse.headers` and the identically-named-header collapse.

**This executor did not write it and must not.** It runs worktree-isolated and `ROADMAP.md` is orchestrator-owned; a per-worktree write would diverge across siblings. The orchestrator has accepted this obligation explicitly and will append the line to `.planning/ROADMAP.md` after merging branch `worktree-agent-a34a7e78cd6f10bac`. Recorded here so the record shows where the obligation sits and so a later audit can check it was discharged. Until it is, deliverable D7 stays `human_judgment: true`.

## AR-27-03 — accepted residual

**Threat `T-27-02-03` (Information Disclosure), severity `low`, disposition `accept`.**

Byte-identically-named headers collapse to one entry in the MCP tool-result header map. Last value wins, at the first one's position.

**Why it is accepted rather than mitigated:** the collapse cannot leak a value — every collapsed entry was already destined for `[STRIPPED]`, and `identicallyNamedHeadersCollapseToOneEntry` asserts that none of the three original values appears anywhere in the returned map's values. Changing it therefore buys analysis signal, not privacy, and a published-contract change does not belong inside a gap-closure phase (CP-27-02-01).

**It is pre-existing, not newly introduced.** It is the behaviour of a `Map`-returning function that predates plan 27-01; 27-01 changed only which names receive `[STRIPPED]`, never the container. The source lines that produce it are `McpToolHelpers.kt:317` and `:345`, quoted above and named in the test's own KDoc.

## The `ordering` edge — closed in two halves, neither of them quietly

The resolved edge asks that *"N `Set-Cookie` headers produce N redacted entries in their original positions. Output order is the input order and is stable."*

| Half | Status | Evidence |
|---|---|---|
| **Distinct header names** — order preserved, case-only variants kept apart, stability | **Satisfied as written** | `inputOrderIsPreservedAcrossDistinctCookieHeaderNames`, `orderIsStableAcrossRepeatedInvocations` |
| **Byte-identical header names** — N headers do NOT produce N entries | **Carried as AR-27-03 under CP-27-02-01** | `identicallyNamedHeadersCollapseToOneEntry`, plus the RED probe measurement `expected: <3> but was: <1>` |

The edge is **not** fully satisfied and it is **not** dropped. Neither statement would be true.

## Red probes, with observed output

All four probes were restored with `git checkout HEAD -- <path>` and never `git stash`. `git diff --quiet src/main/kotlin` exits 0 after each and at the end of the plan.

### RED PROBE 1 — narrow `isCookieHeaderName` to exact-name equality (PASSED: goes red)

Mutation: `name.lowercase(Locale.ROOT).contains(COOKIE_NAME_TOKEN)` → `name.lowercase(Locale.ROOT).let { it == COOKIE_NAME_TOKEN || it == "set-" + COOKIE_NAME_TOKEN }`.

This probe doubled as the TDD RED step for Task 1 — the tests were made to fail by reverting the predicate, not by writing a wrong expectation.

```
CookieHeaderNameParityTest > everyNameThePromptPathStripsIsMatchedByTheSharedPredicate() FAILED
  org.opentest4j.AssertionFailedError: STRICT: the prompt path strips 'Cookie2' but
  Redaction.isCookieHeaderName('Cookie2') is false. The two redaction paths have drifted apart
  again: whatever widened the prompt-path cookie rule was not mirrored into the shared predicate,
  so the MCP tool-result path will emit this header's value verbatim
  (output: GET / HTTP/1.1\nHost: host-80cfeeef83aa.local\n...)

CookieHeaderNameParityTest > parityCorpusIsNonEmptyAndContainsBothPolarities() FAILED
  org.opentest4j.AssertionFailedError: at least 10 corpus names must satisfy the predicate
  (got 4: [Cookie, Set-Cookie, COOKIE, set-cookie]) ==> expected: <true> but was: <false>

CookieHeaderNameParityTest > thePredicateIsDeliberatelyWiderThanTheTwoRegexes() FAILED
  org.opentest4j.AssertionFailedError: the shared predicate is a bare contains() and must match
  'my_cookie' ==> expected: <true> but was: <false>

3 tests completed, 3 failed
```

### RED PROBE 2 — narrow `cookieHeaderRegex` to its exact-name form (MEASURED GREEN on the parity test; this is the expected result)

Mutation: `cookieHeaderRegex` → `Regex("(?im)^" + COOKIE_NAME_TOKEN + ":\\s*.+$")`.

```
RedactionTest > cookieHeaderNameVariantsAreStripped() FAILED
  org.opentest4j.AssertionFailedError: STRICT: the value of Cookie2 must not reach the prompt
  (leaked: GET / HTTP/1.1\nHost: host-76414f348552.local\nCookie2: sentinelalphaone\n
   X-Cookie: sentinelbravotwo\nSet-C...)
60 tests completed, 1 failed

CookieHeaderNameParityTest: tests="3" skipped="0" failures="0" errors="0"
```

**Interpretation — this is the measurement, not a failure.** The invariant is `promptStrips(N) ⇒ isCookieHeaderName(N)`. Narrowing a prompt-path regex SHRINKS the antecedent, and shrinking an implication's antecedent cannot falsify it. So the two guards cover two different mutation directions:

- **the parity test guards a PREDICATE narrowing** (probe 1);
- **`RedactionTest.cookieHeaderNameVariantsAreStripped` guards a PROMPT-PATH narrowing** (probe 2).

Probe 2 going RED would have been a STOP condition — it would have meant the forbidden converse implication had been asserted somewhere in the parity file. It did not. Plan 27-03 can cite this probe by name when amending `CONCERNS.md`.

### RED PROBE 3 — widen `isCookieHeaderName` to always-true (PASSED: goes red, on exactly the intended clause)

Mutation: `fun isCookieHeaderName(name: String): Boolean = name.lowercase(Locale.ROOT).isNotEmpty() || true`.

```
CookieHeaderNameParityTest > parityCorpusIsNonEmptyAndContainsBothPolarities() FAILED
  org.opentest4j.AssertionFailedError: at least 4 corpus names must NOT satisfy the predicate —
  a predicate widened to always-true makes the parity implication trivially true (got 0: [])
  ==> expected: <true> but was: <false>
3 tests completed, 1 failed
```

Only the corpus guard failed. `everyNameThePromptPathStripsIsMatchedByTheSharedPredicate` stayed green — trivially, since the consequent is now always true — which is exactly why the corpus guard must be its own `@Test` rather than a few extra assertions inside the invariant.

### RED PROBE 4 (UNPLANNED, added by this executor) — `LinkedHashMap` → `HashMap` (PASSED: goes red)

Nothing in the plan established that the new order assertions were load-bearing rather than accidentally true, and the whole discipline of this phase is that an unproven guard is an assumption. Mutation applied at `McpToolHelpers.kt:317`.

```
McpToolHelpersTest > SanitizeHeaders > inputOrderIsPreservedAcrossDistinctCookieHeaderNames() FAILED
  expected: <[Set-Cookie, X-Request-Id, X-Cookie, set-cookie, Cookie2]> but was: <...>
McpToolHelpersTest > SanitizeHeaders > preservesOriginalHeaderNameCasingAndInputOrder() FAILED
  expected: <[HoSt, CoOkIe, X-Custom-Header, AuThOrIzAtIoN]> but was: <[CoO...]>
McpToolHelpersTest > SanitizeHeaders > orderIsStableAcrossRepeatedInvocations() FAILED
  expected: <[Set-Cookie, X-Request-Id, X-Cookie, Host]> but was: <[X-Request-Id, Set-Cookie, Host, X-Cookie]>
76 tests completed, 3 failed
```

The pre-existing `preservesOriginalHeaderNameCasingAndInputOrder` fails alongside the two new ones, which also confirms the new tests extend that coverage rather than duplicating it.

### Task 3's TDD RED — the `ordering` edge asserted as literally written

Task 3 asserts CURRENT behaviour and changes no production code, so a genuine RED could not come from the finished tests. Rather than fabricate one, the edge was asserted **as the resolved criterion literally words it** and the failure recorded:

```
McpToolHelpersTest > SanitizeHeaders > edgeProbeThreeSetCookieHeadersProduceThreeEntries() FAILED
  org.opentest4j.AssertionFailedError: the ordering edge as literally written:
  N Set-Cookie headers produce N entries ==> expected: <3> but was: <1>
74 tests completed, 1 failed
```

**This failure IS the measurement behind AR-27-03** — the collapse confirmed by test, not only by reading `McpToolHelpers.kt:317` and `:345`. The temporary probe test was then removed and replaced by the three real tests.

## Wave 1 falsifications, carried forward

Plan 27-01 falsified two premises. Neither is restated as true here, and this plan's claims were checked against them:

1. **The locale premise.** Kotlin's no-argument `lowercase()` already compiles to `toLowerCase(Locale.ROOT)` and is locale-invariant; the dotless-i hazard belongs to the *Java* spelling, which this codebase does not use. Measured under `tr_TR` in 27-01: `kotlin lowercase() = [cookie]`, `java toLowerCase() = [cookıe]`. **Nothing in plan 27-02 rests on the locale hazard** — no test, probe or claim added here asserts one — so the falsification required no correction in this plan. It is repeated only so the record does not silently regain the false premise.
2. **`hostHeaderRegex` is line-anchored** — `(?im)^host:\s*([^\s]+)\s*$`. In 27-01 that made it unable to fire on single-line JSON. In THIS plan the fixture is a genuine multi-line header blob, so the regex **does** fire: probe 1's captured output shows `Host: host-80cfeeef83aa.local`. That is exactly why `Host` is excluded from the parity corpus — `promptStrips("Host")` would be true while `isCookieHeaderName("Host")` is false, breaking the implication for a non-cookie reason. The 27-01 finding and this one are consistent; the difference is the fixture, not the regex.

## Corpus composition, and the exclusion reason for each omitted name

17 names (minimum 16), 12 predicate-positive (minimum 10), 5 predicate-negative (minimum 4).

- **cookie-positive (11):** `Cookie`, `Set-Cookie`, `COOKIE`, `set-cookie`, `Cookie2`, `X-Cookie`, `Set-Cookie2`, `X-Original-Cookie`, `X-Forwarded-Cookie`, `Cookie-Consent`, `X-Cookie-Policy`
- **predicate-only (1):** `my_cookie` — `_` is outside `COOKIE_NAME_PART` (`[A-Za-z0-9-]*`), so neither prompt-path regex can match it while the predicate's bare `contains()` does
- **cookie-negative (5):** `X-Request-Id`, `X-Cook`, `Cook-ie`, `Accept`, `Content-Type`

**Deliberately excluded, with the owning rule named for each** (recorded in the test file's header comment, which is why `grep -c 'Authorization'` returns 2 and both occurrences are inside comments):

| Excluded name | Rule that claims it |
|---|---|
| `Authorization` | `Redaction.authHeaderRegex` — its first alternative |
| `Proxy-Authorization` | `Redaction.authHeaderRegex` — its second alternative |
| `X-API-Key` | `Redaction.authHeaderRegex` — its `x-api-key` alternative |
| `Host` | `Redaction.hostHeaderRegex` — rewrites the value under a host-anonymising policy |

For each of the four, `promptStrips(N)` would be true while `isCookieHeaderName(N)` is false. A corpus containing them would make the implication fail for a reason that has nothing to do with cookies.

**Fixture reachability:** every sentinel is a bare lowercase alphabetic word on an unquoted header line — no `=` (defeats `formBodyParamRegex`, `urlTokenParamRegex`, `cookieTypedParamRegex`), no quotes (defeats `jsonSecretKeyRegex`), no `Bearer`/`Basic`/`eyJ` prefix or dotted segment (defeats `bearerRegex`, `basicAuthRegex`, `jwtRegex`), no `=== COOKIES ===` header (so `redactCookieSections` never runs). `SENSITIVE_KEY_EXPR` is only ever consulted as a KEY immediately followed by `=` or inside quotes.

## The reverse implication was deliberately NOT asserted

`CookieHeaderNameParityTest` asserts `promptStrips(N) ⇒ isCookieHeaderName(N)` and nowhere asserts the converse.

The converse is **false by design**: `my_cookie` satisfies the predicate and is not stripped by the prompt path, and `thePredicateIsDeliberatelyWiderThanTheTwoRegexes` asserts exactly that, in both directions. The only two ways to make a symmetric version pass would be to NARROW the predicate — which reopens the gap this phase closed — or to drop `my_cookie` from the corpus, which deletes the asserted asymmetry. The file header states this so a future reader meets the design intent before the temptation, and probe 2's green result is the standing evidence that the converse is not being asserted.

## Scope of the claims made here

Every claim in this document, in the new test file's comments and in the commit messages is scoped to **the two redaction paths and the passive-scan admitter**. Nothing here restates the wider ownership claim. The scope-guard grep returns 0 on both files touched by this plan.

The 27-01 ownership test's guarantee remains **bounded to its five measured spelling classes** — exact-name equality, `ignoreCase` equality, a line-prefix test, a Montoya `headerValue` lookup, and a substring test. It is a tripwire over measured spellings, not a proof of exhaustive coverage, and this plan neither widens that bound nor relies on it being wider.

## Decisions Made

- **CP-27-02-01 = `keep-map-plus-backlog`**, decided by the human user. Recorded above in full with options, rationale, blast radius and date.
- **The ordering falsifiability probe was added unprompted** because the plan asked for order assertions but supplied nothing proving them load-bearing. An order assertion that would pass against a `HashMap` asserts nothing.
- **The JUnit XML is now checked by test NAME after every insertion.** See Deviation 1 — a green suite is not evidence a test ran.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 — Bug] Task 3's tests were first inserted INSIDE another test method and silently never ran**

- **Found during:** Task 3
- **Issue:** A line-numbered `sed` insert used an off-by-one anchor and placed the new test between the last assertion of `emptyValuedCookieHeaderIsStillRedacted` and that method's closing brace. Kotlin compiled it as a LOCAL FUNCTION — `javap` shows `emptyValuedCookieHeaderIsStillRedacted$edgeProbeThreeSetCookieHeadersProduceThreeEntries` — so it carried no `@Test` registration, never executed, and the suite reported `BUILD SUCCESSFUL`. A test that cannot fail was sitting in the tree looking like coverage. This is precisely the "passing test about nothing" failure class `parityCorpusIsNonEmptyAndContainsBothPolarities` exists to catch, arriving through a different door.
- **Fix:** Reverted the file with `git checkout HEAD --`, re-inserted using a unique content anchor instead of a line number, and then verified — by name, in `TEST-…McpToolHelpersTest$SanitizeHeaders.xml` — that all three new tests appear as real `testcase` entries (14 → 17 in the nested class, 73 → 76 overall).
- **Files modified:** `McpToolHelpersTest.kt`
- **Verification:** the three test names present in the JUnit XML; probe 4 shows all three genuinely red under mutation.
- **Committed in:** `b7519c5` (the botched insert never reached a commit)

**2. [Rule 3 — Blocking] Gradle reported `compileTestKotlin UP-TO-DATE` after an in-place edit, masking the bug above**

- **Found during:** Task 3
- **Issue:** After `sed -i ''` rewrote the test file, `./gradlew test` reported every task `UP-TO-DATE` and finished in ~300 ms, so the stale previous results were reported as if they were a fresh run. `--no-watch-fs` did not help. This is what let the silently-nested test read as "green" for two consecutive runs.
- **Fix:** Forced the compile with `./gradlew compileTestKotlin --rerun`, then inspected the emitted class with `javap`, which is what exposed Deviation 1. All later edits went through the `Edit` tool rather than `sed -i`, and no `UP-TO-DATE` reappeared.
- **Files modified:** none (build-tooling behaviour)
- **Verification:** subsequent runs recompiled and reported real test counts (74, then 76).
- **Committed in:** n/a

**3. [Measurement, not a fix] Task 3's "failing first" was satisfied by asserting the edge as literally written**

- **Found during:** Task 3
- **Issue:** The plan asks for Task 3's tests "failing first", but Task 3 asserts CURRENT behaviour and changes no production code, so the finished tests cannot have a genuine RED. Manufacturing one would have meant writing an expectation known to be wrong.
- **Fix:** A temporary probe asserted the `ordering` edge exactly as the resolved criterion words it (3 `Set-Cookie` headers ⇒ 3 entries) and was observed red: `expected: <3> but was: <1>`. That is a real, meaningful RED — it is the measurement that justifies AR-27-03 — and the probe was then removed in favour of the three real tests. Recorded rather than smoothed over.
- **Files modified:** `McpToolHelpersTest.kt` (probe removed before commit)
- **Verification:** output recorded verbatim above.
- **Committed in:** n/a (probe only)

**4. [Scope] The ROADMAP backlog line required by option C was NOT written by this executor**

- **Found during:** Task 2 resolution
- **Issue:** CP-27-02-01's action step tells the executing agent to append a backlog line to `.planning/ROADMAP.md`. This executor is worktree-isolated and `ROADMAP.md` is orchestrator-owned; writing it here would diverge across sibling worktrees.
- **Fix:** The obligation is recorded in this SUMMARY under CP-27-02-01, explicitly named as owed by the orchestrator after merge, and deliverable D7 is held at `human_judgment: true` until it is discharged.
- **Files modified:** none
- **Verification:** `git status` shows no `ROADMAP.md` or `STATE.md` change on this branch.
- **Committed in:** n/a

---

**Total deviations:** 4 (1 bug, 1 blocking, 1 recorded measurement, 1 scope boundary).
**Impact on plan:** No scope creep and no production change. Deviation 1 is the significant one — the phase nearly shipped a test that could not fail, and it was caught by inspecting the compiled artifact rather than by trusting a green build. That check is now written into `patterns-established` so the next plan inherits it.

## Issues Encountered

None beyond the deviations above. The known `RedactionTest` wall-clock flake did not occur; `RedactionTest` was green on every run except the one where probe 2 deliberately mutated the regex it guards, and its file is unedited.

## Verification

```
$ JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test detekt ktlintCheck
BUILD SUCCESSFUL in 2m 46s
15 actionable tasks: 6 executed, 9 up-to-date
```

- `git diff --quiet src/main/kotlin` exits 0 — all four probes restored; this plan changed no production code.
- `git diff --quiet detekt-baseline.xml` exits 0 — no baseline entry added.
- `CookieHeaderNameParityTest`: `tests="3" skipped="0" failures="0" errors="0"` — exactly 3 tests, as SC1 requires.
- `grep -c 'setAccessible\|java.lang.reflect' CookieHeaderNameParityTest.kt` → `0`. No reflection into the private regexes.
- `grep -c 'Authorization' CookieHeaderNameParityTest.kt` → `2`, both inside the exclusion comment at lines 45-46, verified by reading.
- Scope guard: the literal `whole codebase` appears 0 times in both files touched.
- `git status --short` clean; no `STATE.md` or `ROADMAP.md` modification on this branch.

## Threat register outcome

| Threat ID | Disposition | Outcome |
|---|---|---|
| `T-27-02-01` (one-sided widening) | mitigate | **Closed.** `everyNameThePromptPathStripsIsMatchedByTheSharedPredicate`, falsified by probe 1. |
| `T-27-02-02` (vacuous parity test) | mitigate | **Closed.** `parityCorpusIsNonEmptyAndContainsBothPolarities`, proven load-bearing by probe 3. |
| `T-27-02-03` (identical-name collapse) | **accept** | **AR-27-03**, recorded above. Asserted non-leaking by `identicallyNamedHeadersCollapseToOneEntry`. |
| `T-27-02-04` (silent wire-shape change) | mitigate | **Closed.** CP-27-02-01 went to the human; option C keeps the shape. No production file changed. |
| `T-27-02-05` (widening private regex visibility) | mitigate | **Closed.** Zero reflection; the two regexes remain `private`; the assertion goes through `Redaction.apply`. |
| `T-27-02-06` (invariant misread as symmetric) | mitigate | **Closed.** Converse asserted nowhere; asymmetry asserted positively via `my_cookie`; probe 2's green is the standing evidence. |
| `T-27-02-SC` (package installs) | **accept** | No package installed, no Gradle dependency added. |

No `high` threat is left `accept`. The one accepted residual, AR-27-03, is `low`.

## Threat Flags

None — no new network endpoint, auth path, file access pattern or schema change at a trust boundary. This plan added tests only.

## Next Phase Readiness

- **Ready for 27-03.** The two inputs it should cite by name are: probe 2's measured GREEN (which guard covers which mutation direction), and AR-27-03 with its CP-27-02-01 reference.
- **Carried forward from 27-01, unchanged:** the locale finding narrows what a security-register amendment may claim about `T-27-01-02` — cite the measured zero locale-sensitive call sites, not the 114 `lowercase()` grep count.
- **Open obligation:** the ROADMAP backlog line for the entry-list change (see CP-27-02-01). Owed by the orchestrator after merge, not by this branch.
- **For any future plan touching cookie matching:** the ownership test's guarantee stays bounded to five measured spelling classes, and this plan's parity test is bounded to its 17-name corpus. Both are tripwires, not proofs.

## Self-Check: PASSED

- Created files exist on disk: `CookieHeaderNameParityTest.kt`, `27-02-SUMMARY.md`.
- All three commits exist: `33b3c33`, `b7519c5`, `7f347a9`.
- `git diff a517da9 HEAD --name-only` lists exactly three files — the two test files and the SUMMARY. **No `STATE.md`, no `ROADMAP.md`** — the orchestrator owns those writes, and the ROADMAP backlog line owed by CP-27-02-01 is recorded above as its obligation.
- Working tree clean; all four probe restorations verified with `git diff --quiet src/main/kotlin`.
- Every task-level `<acceptance_criteria>` was executed. The one that could not be satisfied as literally written — Task 3's "failing first", which is impossible for tests that assert current behaviour with no production change — is recorded as Deviation 3 with the measurement that replaced it, not silently skipped.
- Each new test was confirmed to have actually RUN, by name, in the JUnit XML. That check is not ceremonial: it is what caught Deviation 1.

---
*Phase: 27-priv-05-gap-closure-sanitize-headers*
*Completed: 2026-08-24*
