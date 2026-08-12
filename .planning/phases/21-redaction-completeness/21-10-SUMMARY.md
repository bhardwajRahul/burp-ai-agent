---
phase: 21-redaction-completeness
plan: 10
subsystem: scanner
gap_closure: true
tags: [PRIV-05, CR-01, SC1, D-06, D-09, D-10, T-21-32, T-21-20, T-21-06, T-21-05, kotlin, redaction, privacy, cookies, prompt-builder]
requires:
  - "Redaction.sanitizeCookieSectionEntries (exported UNCALLED by plan 21-08)"
  - "Redaction.COOKIE_SECTION_HEADER, Redaction.cookieSectionEnd span bound (plan 21-08)"
  - "buildScanMetadataText / redactScanMetadata / formatParamLine Wave 0 seam (plan 21-01)"
provides:
  - "cookieSectionLines(headerValues, maxCount) — the Montoya-free producer seam, sanitising BEFORE the display bound"
  - "buildScanMetadataText emits Redaction.sanitizeCookieSectionEntries(cookies) — the section framing is no longer forgeable from inside"
  - "poisonedCookieHeaderCannotTerminateTheCookieSection — the end-to-end guard for CR-01's second trigger"
  - "cookieSectionEntriesAreSanitizedAtTheEmitter — the structural framing guard"
  - "blankCookieElementsDoNotConsumeDisplaySlots — the producer guard"
affects:
  - "T-21-32 moves from transferred/open to CLOSED; sanitizeCookieSectionEntries is no longer dead code"
tech-stack:
  added: []
  patterns:
    - "Wave 0 seam extended: a second Montoya-free extraction (cookieSectionLines) so the producer chain is assertable rather than reproduced in the test"
    - "Mutation applied on the COMMITTED base and reverted with git checkout --, never hand-edited out (21-08's recovery discipline)"
key-files:
  created: []
  modified:
    - "src/main/kotlin/com/six2dez/burp/aiagent/scanner/PassiveAiScannerPrompts.kt"
    - "src/main/kotlin/com/six2dez/burp/aiagent/scanner/PassiveAiScannerAnalysis.kt"
    - "src/test/kotlin/com/six2dez/burp/aiagent/scanner/PassiveAiScannerPromptRedactionTest.kt"
decisions:
  - "Took the EXTRACTION route for the producer assertion, not the reproduction route — the reproduction as specified would have been provably vacuous"
  - "Test 2's slice predicate is a RAW startsWith(\"=== \"), matching Redaction.cookieSectionEnd byte for byte, not the plan's trimStart() which is self-contradictory"
  - "Task 1 was committed as two commits (refactor then test) so the RED gate is an assertion failure rather than a compile error"
  - "The sanitizer is called at BOTH sites and is idempotent, so the double application is defence in depth with no double-prefixing"
metrics:
  duration: "~35 min"
  completed: "2026-08-12"
  tasks: 2
  commits: 3
  tests_total: 635
  tests_failing: 0
---

# Phase 21 Plan 10: Wiring the Redactor-Owned Cookie Sanitizer at the Emitter Summary

Closed CR-01's second trigger — a cookie element shaped like a section header terminating the redaction span so every cookie below it leaked — by calling plan 21-08's `Redaction.sanitizeCookieSectionEntries` at both cookie-section build sites, and proved it with three red-before-green guards plus two mutations that fail the two sites independently.

## What Shipped

**The emitter.** `buildScanMetadataText` now emits `Redaction.sanitizeCookieSectionEntries(cookies)` instead of `cookies`. The `if (cookies.isNotEmpty())` guard stays keyed on the **original** list, so the framing decision — and therefore the blob's byte-identical shape — is unchanged. This is the half the redactor provably cannot close: the span terminator is derived from content *inside* the region the rule protects (in-band signalling), and the emitter is the only place a genuine `=== PARAMETERS ===` and a planted `=== FOO ===` are distinguishable.

**The producer.** The `Cookie:` split/trim/bound chain moved out of `doAnalysis` into `cookieSectionLines(headerValues, maxCount)` in `PassiveAiScannerPrompts.kt`, which sanitises **before** `take(maxCount)`. Blank elements no longer consume display slots, so `Cookie: ;;a=1; b=2; …` surfaces six real cookies where it previously surfaced four.

**T-21-32 is closed.** `sanitizeCookieSectionEntries` is no longer dead code; 21-08's `Known Stubs` entry is discharged.

## Which Route Task 1 Took, and Why — EXTRACTION

The plan offered two routes for the producer assertion and expressed a preference for extraction. Extraction was taken, and not merely on preference: **the reproduction route is provably vacuous as the plan specifies it.**

The plan's reproduction instruction is to "split a `Cookie:` value … on `;`, trim each element, apply `Redaction.sanitizeCookieSectionEntries`, then `take(6)`" *in the test*. A test that applies the sanitizer itself asserts a property of its own three lines, not of the production code. It would have been **green at Task 1** — no RED gate at all — and would have stayed green under any mutation of `PassiveAiScannerAnalysis.kt`, including deleting the producer entirely. That is exactly the vacuity class that let this phase's live leak ship under 628 green tests, so the route was rejected on evidence rather than taste.

`cookieSectionLines` mirrors the Wave 0 pattern plan 21-01 established. Two consequences worth recording:

- **The Montoya boundary was respected.** `PassiveAiScannerPrompts.kt` is documented as receiver-free and Montoya-free. `HttpHeader` is a Montoya type, so the signature takes `List<String>` header **values** and the `it.name().equals("Cookie", ignoreCase = true)` filter stays at the call site, byte-identical. The `;` split, the `trim()` and the bound moved verbatim.
- **Task 1 became two commits.** The extraction had to land before the test could reference it, otherwise Test 3's "RED" would have been a *compile error* — and 21-08 recorded, correctly, that a compile error is not evidence a defect exists. So `4e8f611` is the behaviour-preserving extraction (verified green against the whole scanner suite before the tests were written) and `10da6c3` is the RED test commit. This is the same RED-gate honesty 21-08 applied to `cookieSectionDeadlineFailsClosed`.

## RED Failure Set (Task 1, before Task 2)

Command: `./gradlew test --tests "com.six2dez.burp.aiagent.scanner.PassiveAiScannerPromptRedactionTest"` → **11 tests completed, 3 failed.**

| Test | Observed failure |
|------|------------------|
| `poisonedCookieHeaderCannotTerminateTheCookieSection` | `STRICT: a cookie element shaped like a section header must not let the value of 'abtest_bucket' reach the backend ==> expected: <false> but was: <true>` |
| `cookieSectionEntriesAreSanitizedAtTheEmitter` | `No blank line may sit between cookie entries: a blank consumes a display slot and used to collapse the redaction span ==> expected: <false> but was: <true>` |
| `blankCookieElementsDoNotConsumeDisplaySlots` | `A blank element must not consume one of the six display slots ==> expected: <false> but was: <true>` |

All **8 pre-existing tests stayed green** across the RED run, so the fixtures are right and the source is what moved.

The first test failed on **`abtest_bucket` and only `abtest_bucket`** — not on `REALSECRET`. That is the reachability argument confirming itself: `JSESSIONID=REALSECRET` is rescued by `formBodyParamRegex` from the leading-field position even with the section span truncated at the planted header. It is asserted as a deliberately **non-decisive** companion, exactly as 21-08 recorded for its own fixture.

## Sentinel Reachability — why `abtest_bucket=OPAQUE_VALUE_XYZ` proves something

The sentinel is unreachable by every rule in `Redaction.apply` except the section-scoped one. Verified against the shipped source rather than from memory:

| Stage | Rule | Why it cannot reach the sentinel |
|-------|------|----------------------------------|
| `stripCookies` | `cookieHeaderRegex` `(?im)^cookie:\s*.+$` | the line is a bare `name=value` inside the COOKIES section, with no `Cookie:` prefix — that stripping is precisely what PRIV-05 is about |
| `stripCookies` | `setCookieHeaderRegex` | no `Set-Cookie:` prefix |
| `stripCookies` | `cookieTypedParamRegex` `(?im)^([^=\r\n]+)=(.*?)(\s\(COOKIE\))\s*$` | requires the ` (COOKIE)` type suffix; a cookie **section** line carries none |
| `redactTokens` | `authHeaderRegex`, `bearerRegex`, `basicAuthRegex` | no auth header name, no `Bearer `/`Basic ` prefix |
| `redactTokens` | `jwtRegex` | value is not `eyJ…`.`…`.`…` |
| `redactTokens` | `urlTokenParamRegex` `(?i)([?&](?:SENSITIVE_KEY_EXPR)=)…` | no `?` or `&` before the key, **and** the key is not sensitive |
| `bodyStage` | `formBodyParamRegex` `(?im)(^\|[?&])(SENSITIVE_KEY_EXPR)=…` | anchors at line start, so only the key side saves it — and `abtest` / `bucket` are members of neither `SENSITIVE_WORDS` (`access_token\|api_key\|apikey\|auth\|token\|key\|secret\|password\|pwd\|session\|sid\|code`) nor `KNOWN_SESSION_KEYS`, under either the separator rule or the camelCase rule |
| `bodyStage` | `jsonSecretKeyRegex` | the value sits in no JSON key/value pair; the key is not sensitive either way |
| `bodyStage` | custom patterns | `@BeforeEach` clears `Redaction.setCustomPatterns(emptyList())` |
| `anonymizeHosts` | `hostHeaderRegex` `(?im)^host:\s*([^\s]+)\s*$` | no `Host:` prefix |

The value `OPAQUE_VALUE_XYZ` additionally contains no `=`, so it cannot be a fragment of another pair. **Only `redactCookieSections` can redact this line**, which is what makes its survival genuine evidence and its absence a genuine pass. The reasoning is repeated verbatim in the test's own comment, together with the explicit warning that swapping the name for one `SENSITIVE_KEY_EXPR` already covers would make the test pass with the defect fully present.

Non-comment sentinel references in the test file: **8** (criterion: at least 1).

## Mutation Verification

Both mutations were applied **on top of the committed Task 2 base** and reverted with `git checkout -- <path>`, never hand-edited out — 21-08's recovery discipline, adopted because that plan's executor was killed mid-mutation.

### Mutation A — the emitter call reverted to plain `cookies`

**11 tests completed, 2 failed.**

| Test | Observed failure |
|------|------------------|
| `poisonedCookieHeaderCannotTerminateTheCookieSection` | `STRICT: a cookie element shaped like a section header must not let the value of 'abtest_bucket' reach the backend ==> expected: <false> but was: <true>` |
| `cookieSectionEntriesAreSanitizedAtTheEmitter` | `No blank line may sit between cookie entries: a blank consumes a display slot and used to collapse the redaction span ==> expected: <false> but was: <true>` |

`blankCookieElementsDoNotConsumeDisplaySlots` correctly did **not** fail: it exercises `cookieSectionLines`, a different build site, and the emitter tests reach `buildScanMetadataText` directly through `metadataBlob`. That separation is the point of running two mutations.

### Mutation B — the sanitize call removed from `cookieSectionLines`

**11 tests completed, 1 failed.**

| Test | Observed failure |
|------|------------------|
| `blankCookieElementsDoNotConsumeDisplaySlots` | `A blank element must not consume one of the six display slots ==> expected: <false> but was: <true>` |

Precisely one test fails, and it is the one that exists to guard that site.

**Why both mutations were needed.** The plan's acceptance criterion names only Mutation A. Mutation A alone leaves the claim "the sanitizer is called at BOTH build sites" unverified — the producer call could have been deleted with the suite still green. Mutation B closes that, and the two mutations together show the two sites are independently guarded rather than jointly covered by one test.

After both reverts: `git status` clean, and `grep -c MUTATION` over all three files returns **0**.

## Defence in Depth, Not a Replacement

**Plan 21-08's redactor span bound remains the fix for blank entries and was NOT weakened here.** `Redaction.kt` is byte-identical to the base (`git diff --stat 2a4e711 -- Redaction.kt` is empty — this plan never opened it, which also kept it disjoint from plan 21-09 running concurrently on the same file). `cookieSectionEnd` still skips blank lines rather than terminating on them, `MAX_COOKIE_SECTION_LINES` still bounds the span, and the deadline still fails closed. `cookieSectionHeaderShapedEntryTerminatesSpan_documentedResidual` was left exactly as 21-08 wrote it: it asserts the redactor-level residual, is green before and after, and correctly continues to document the boundary of the redactor's own reach.

The two mechanisms cover different inputs and that matters: `redactCookieSections` also runs over arbitrary MCP tool output via `McpToolContext.redactIfNeeded`, which never passes through this emitter. The emitter sanitizer closes the scanner path; the span bound is what protects everything else.

The sanitizer is applied at both sites and is **idempotent** — a `" === FOO ==="` produced by the producer no longer satisfies `startsWith("===")`, so the emitter does not prefix it a second time — so the double application costs nothing and neither site depends on the other.

## Plan Defects Recorded (intent satisfied, criterion not literally satisfiable)

**1. Test 2's two slice assertions are mutually contradictory as written.** The plan requires the slice to contain "no line whose `trimStart()` begins with `===`" *and* that the `=== FOO ===` element is "still present in some form — a leading space is the expected shape". A space-prefixed `=== FOO ===` has `trimStart()` beginning with `===`, so no implementation can satisfy both. **Intent satisfied** by asserting `!it.startsWith("===")` on the **raw** line. That is not a weakening: `Redaction.cookieSectionEnd` terminates on `text.startsWith(NEXT_SECTION_PREFIX, p)` — a raw, untrimmed check at a line start — so the raw predicate is the one that actually mirrors the terminator, and asserting `"==="` without the trailing space is *stricter* than the terminator itself and matches `sanitizeCookieSectionEntries`' own contract exactly.

**2. The slice's "no blank line" criterion collides with the emitter's contractual framing blank.** The plan says to slice "from the cookie header up to the next line starting with `=== `" and assert no blank line in it — but `buildScanMetadataText` closes the section with `appendLine()`, and the same plan forbids touching those blank lines. That framing blank is always inside the slice. **Intent satisfied** by splitting the slice into entries and framing via `dropLastWhile { it.isBlank() }`, asserting no blank *among the entries* and separately `assertEquals(1, framingBlanks)`. This is stronger than the criterion as written: a blank sitting between two entries still fails (it is not trailing), and a second framing blank fails too.

**3. Task 1's `<files>` list names only the test file, but the RED gate cannot be met without touching main source.** See the extraction discussion above. Recorded as a deviation rather than silently absorbed; both files it touched are in the plan's own frontmatter `files_modified`.

No assertion was weakened and no load-bearing comment was deleted to make any criterion match.

## Deviations from Plan

### Auto-fixed Issues

None. No bug, blocking issue or missing critical functionality was encountered — the extraction compiled and the whole scanner suite stayed green on the first run, and detekt/ktlint were clean throughout. Predicted lint hazards did not materialise: `LargeClass` (the test class is ~420 lines against a 600 threshold), `TooManyFunctions` (excluded for test sources by detekt's defaults, confirmed by `RedactionTest`'s absence from the baseline), `MagicNumber` (`COOKIES_MAX_COUNT` is a named mirror constant, in the style of the file's existing `PARAM_VALUE_MAX_CHARS`) and `ReturnCount` (`cookieSectionLines` has one return).

## Verification

- `./gradlew test ktlintCheck detekt` — **BUILD SUCCESSFUL**; **635 tests / 0 failures / 0 errors** (632 at the base plus this plan's 3), ktlint clean, detekt clean
- `git diff --stat 2a4e711 -- detekt-baseline.xml` — **empty** (QUAL-07 satisfied)
- `git diff --stat 2a4e711 -- src/main/kotlin/com/six2dez/burp/aiagent/redact/Redaction.kt` — **empty** (plan 21-09's file never opened)
- `git diff -U0 2a4e711 -- PassiveAiScannerPrompts.kt | grep -c '^[-+].*appendLine()$'` — **0** across the whole plan, not only Task 2; the emitter's blank-line framing is byte-identical
- D-06 unregressed: `grep -c 'PrivacyMode.OFF' PassiveAiScannerAnalysis.kt` — **1**, identical to `git show 2a4e711:…`; `redactScanMetadata` still calls `Redaction.apply` unconditionally with no mode short-circuit
- `grep -c 'Redaction.sanitizeCookieSectionEntries' PassiveAiScannerPrompts.kt` — **2** (emitter + producer; criterion: at least 1)
- `grep -c 'sanitizeCookieSectionEntries\|cookieSectionLines' PassiveAiScannerAnalysis.kt` — **2** (criterion: at least 1)
- `grep -c 'fun poisonedCookieHeaderCannotTerminateTheCookieSection' / 'fun cookieSectionEntriesAreSanitizedAtTheEmitter' / 'fun blankCookieElementsDoNotConsumeDisplaySlots'` — **1** each
- Non-comment `abtest_bucket` references in the test file — **8** (criterion: at least 1)
- Monotonicity canaries green in the full run, including plan 21-01's Wave 0 seam tests (`redactScanMetadata_offModeIsByteIdentical`, `emittedBlobContainsTheSectionConstant_parity`, `parameterLineShape_carriesTheMontoyaTypeSuffix`, `buildScanMetadataText_emitsCookieAndParameterSections`), plan 21-05's end-to-end `emittedCookieSectionValuesAreRedacted_sc1` and `emittedCookieTypedParametersAreRedacted_sc2`, and the whole `redact` package
- `git status` clean; `grep -c MUTATION` over all three touched files — **0**

## Threat Model Outcomes

| Threat | Disposition | Outcome |
|--------|-------------|---------|
| T-21-32 (in-band section framing forged from a cookie value) | mitigate | **CLOSED.** The emitter sanitises entries; guards `poisonedCookieHeaderCannotTerminateTheCookieSection` (behaviour) and `cookieSectionEntriesAreSanitizedAtTheEmitter` (framing), both verified RED pre-fix and under Mutation A |
| T-21-20 (a future edit silently removing the sanitizer call) | mitigate | The sanitizer stays owned by `redact/`, mirroring `COOKIE_SECTION_HEADER`; both call-site comments name the leak prevented **and** the guard by name; Mutations A and B prove each site's guard actually fails without it |
| T-21-16 / PRIV-05 (cookie values reaching the backend) | mitigate | Re-asserted end to end against the REAL emitted blob from `buildScanMetadataText`, never a hand-written string |
| T-21-06 (sanitisation discarding analytic content) | mitigate | Only genuinely blank entries are dropped; a section-shaped entry is space-prefixed and still redacted downstream. `blankCookieElementsDoNotConsumeDisplaySlots` proves the change is a **net gain** — six real cookies surfaced where four were before |
| T-21-05 (a cookie value injecting a fake section header into the prompt) | mitigate | Space-prefixing removes the prompt-injection shape as well as the terminator shape |
| T-21-28 (span collapse) | mitigate | **Not regressed.** `Redaction.kt` untouched; 21-08's span bound and its guards are green and unweakened |
| T-21-SC (supply chain) | accept | Zero packages installed, no new Gradle dependency, no new import outside the already-imported `redact` package |

No new security-relevant surface was introduced: no network endpoint, no auth path, no file access, no schema at a trust boundary. The change is a pure `List<String>` transformation plus one relocated expression.

## Known Stubs

None. 21-08's single stub — `sanitizeCookieSectionEntries` shipping uncalled — is **discharged by this plan**; it now has two callers and a named guard each.

## Tasks

1. **Task 1: The poisoned-Cookie-header end-to-end guard** — `4e8f611` (refactor: extract `cookieSectionLines`) and `10da6c3` (test: three RED guards)
2. **Task 2: Call the redactor-owned sanitizer at both cookie-section build sites** — `259cf59` (fix)

## Self-Check: PASSED

- `src/main/kotlin/com/six2dez/burp/aiagent/scanner/PassiveAiScannerPrompts.kt` — FOUND, modified
- `src/main/kotlin/com/six2dez/burp/aiagent/scanner/PassiveAiScannerAnalysis.kt` — FOUND, modified
- `src/test/kotlin/com/six2dez/burp/aiagent/scanner/PassiveAiScannerPromptRedactionTest.kt` — FOUND, modified
- `.planning/phases/21-redaction-completeness/21-10-SUMMARY.md` — FOUND
- Commits `4e8f611`, `10da6c3`, `259cf59` — all FOUND in branch history
- `src/main/kotlin/com/six2dez/burp/aiagent/redact/Redaction.kt` — **not modified** (owned by plan 21-09 this wave)
- STATE.md / ROADMAP.md / REQUIREMENTS.md — **not modified** (orchestrator owns those writes)

---
*Phase: 21-redaction-completeness*
*Completed: 2026-08-12*
