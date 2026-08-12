---
phase: 21-redaction-completeness
plan: 12
subsystem: privacy
tags: [redaction, key-vocabulary, over-redaction, kotlin, mutation-testing, regex-performance, maintainer-decision]

# Dependency graph
requires:
  - phase: 21-redaction-completeness (plan 04)
    provides: SENSITIVE_KEY_EXPR, the D-11 token-boundary rule, D-13 camelCase matching and the 31/21/8 SC3 corpora this plan narrows and re-measures
  - phase: 21-redaction-completeness (plan 11)
    provides: splitPoint / safeCutPoint and newlineFreeOversizeBodyIsScannedNotDestroyed, the budget-sensitive test that constrained this plan's implementation shape
provides:
  - WR-01 decided and implemented — 'key' and 'code' narrowed to whole-key equality or a credential-bearing prefix
  - BROAD_WORDS / CREDENTIAL_PREFIXES / BROAD_WORD_SEP, and SENSITIVE_KEY_WORDS as the compiled first-letter-factored vocabulary
  - wr01BroadWordKeysSurviveUnlessCredentialBearing — 32 must-survive + 24 must-still-redact names, driven through all three consumer contexts
  - wr01NonBroadWordOverRedactionsRemainAccepted — the five names WR-01 did NOT reach, asserted as accepted rather than assumed
  - NAIVE_KEY_EXPR_FOR_TEST + factoredKeyVocabularyMatchesItsReadableSpecification — the factored vocabulary is checked against its readable spec over 120 names
  - ADR-14 and CONCERNS.md carry the ruling, its rejected alternatives, its accepted cost and its limit
affects: [redaction, privacy, AI prompt content, MCP tool output, any future change to the key vocabulary]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "A hand-factored security-critical regex is CHECKED, not trusted: ship a naive reference built from the readable constants and assert equivalence over the whole corpus"
    - "Measure the instrumented production path, not the raw regex — an isolated Regex.replace benchmark inverted the sign of the real end-to-end result"
    - "Establish the BASELINE under the exact failing command before attributing a flaky test to your own change"

key-files:
  created: []
  modified:
    - src/main/kotlin/com/six2dez/burp/aiagent/redact/Redaction.kt
    - src/test/kotlin/com/six2dez/burp/aiagent/redact/RedactionTest.kt
    - DECISIONS.md
    - .planning/codebase/CONCERNS.md

key-decisions:
  - "option-b: narrow 'key' and 'code' to whole-key equality or one of seven credential-bearing prefixes — maintainer ruling, applied verbatim"
  - "public_key / publicKey survive, because the confirmed prefix set excludes 'public' — read off the ruling rather than decided by the executor, and pinned in the corpus"
  - "token_type and tokenType stay REDACTED, diverging from the plan's expectation: they are 'token'-driven, and freeing them needs a D-12 reversal or a narrowing of 'token'. Escalated, not silently done"
  - "The vocabulary is compiled first-letter factored, because both flat shapes exhausted the body stage's 2 s budget on the 4 MB newline-free fixture"
  - "The SC3 31/21/8 corpora are left at their measured sizes and WR-01 gets its own corpora, so a historical measurement is added to rather than rewritten"

metrics:
  duration: ~2h15m
  completed: 2026-08-12
  tasks_completed: 2
  files_modified: 4
  commits: 2
---

# Phase 21 Plan 12: WR-01 — Narrow the Broad Key Vocabulary Summary

The two broadest words in the redaction vocabulary, `key` and `code`, no longer redact on free token containment — they now require whole-key equality or one of seven credential-bearing prefixes — so `status_code`, `errorCode`, `primary_key` and 24 other names reach the analysis prompt intact while every credential name still redacts.

## Task 1 — the WR-01 decision gate

The gate was pre-answered by the maintainer and supplied in the execution brief, so no checkpoint was raised. No source file was modified by this task and no commit was made for it, which satisfies the plan's `git status --porcelain src/` criterion trivially.

**Option selected, verbatim:** *"narrow `key` and `code` to a credential-bearing context"* — i.e. `option-b`.

**Maintainer's ruling text, verbatim:**

> split the two broad words into their own alternative requiring a credential-bearing prefix (`api`, `access`, `secret`, `auth`, `private`, `signing`, `enc`) or whole-key equality. `api_key`, `access_code`, `secret_key` keep redacting; `status_code`, `zip_code`, `cache_key`, `primary_key`, `error_code`, `token_type` survive.

**Whole-key equality for bare `key` and `code`: YES, retained** — stated explicitly in the ruling and required in any case, since both are entries 24 and 30 of the 31-key SC3 must-redact corpus.

**Rejected alternatives, recorded as given:** keeping the current breadth and pinning it as accepted (rejected — a functional regression in a passive scanner, not a cosmetic over-redaction); and dropping `key`/`code` entirely (rejected — `api_key` and `access_code` would then depend wholly on enumeration).

The measured over-redaction list from `21-REVIEW.md` §WR-01 was not summarised: all 32 names were driven through the live regexes and are reproduced in full in `BROAD_WORDS`' source comment, in ADR-14 and in the test corpus.

## Task 2 — pin the decision and record it

### Mechanism

`SENSITIVE_WORDS` loses `key` and `code`; every other word is byte-identical to the v0.6.0 value. Three new constants carry the rule: `BROAD_WORDS` (`key|code`), `CREDENTIAL_PREFIXES` (the seven, verbatim) and `BROAD_WORD_SEP` (one optional `_`, `.` or `-`). `SENSITIVE_KEY_EXPR` gains a bare `(?:key|code)` alternative that carries **no `KEY_CHARS` padding** — that absence is what makes it whole-key equality, because each of the three consumers already anchors the expression with `[?&]…=`, `^…=` or `"…":`. Every internal group is non-capturing, so all three replacement expressions are untouched (Pitfall 7).

### Before/after corpus counts — measured against the live regexes, not reasoned

Every number below was produced by driving the real `Redaction.apply` over each name in query-string, form-body and JSON contexts, before and after the change.

| Corpus | Before | After | Verdict |
|---|---|---|---|
| SC3 must-redact (31) | 31 RED | **31 RED** | unchanged — no must-redact key lost |
| SC3 must-not-redact (21) | 21 GREEN | **21 GREEN** | unchanged |
| SC3 camelCase (8) | 6 RED / 2 GREEN | **4 RED / 4 GREEN** | `codeName`, `keyName` freed |
| WR-01 measured over-redactions (32) | 32 RED | **27 GREEN / 5 RED** | 27 of 32 freed |
| Accepted over-redactions (10) | 10 RED | **6 RED** | `key_size`, `code_version`, `codeName`, `keyName` freed |
| Cookie decoy sentinel `abtest_bucket` | GREEN | **GREEN** | still unreachable by the key expression |

The 31/31 result is the one that mattered, and it was verified rather than assumed: `api_key`, `apikey`, `api-key`, `api.key`, `access_token`, `access-token`, `auth_token`, `x-auth-token`, `XSRF-TOKEN`, `csrftoken`, `remember_me`, `X-Session-Id`, `JSESSIONID`, `PHPSESSID`, `connect.sid`, `ASP.NET_SessionId`, `laravel_session`, `_csrf`, `user_api_key`, the bare words and the camelCase set (`authToken`, `accessToken`, `userSessionId`) all still redact.

Small gain in the fail-safe direction: `accesscode` and `authcode` (no separator) did **not** redact before and now do, via the prefix path.

### What is pinned where

- `wr01BroadWordBenignKeys` — 32 names that must now survive, including all nine the success criteria name except `token_type` (see the divergence below), plus `public_key` and `publicKey`.
- `wr01CredentialBroadWordKeys` — 24 names that must still redact, run in **STRICT and BALANCED** with the benign-companion assertion, because under-redaction is the direction that ships a leak.
- `wr01AcceptedOverRedactions` — the five names WR-01 did not reach, asserted as accepted.

The SC3 corpora keep their measured sizes (21-key assertion untouched). ADR-14 and CONCERNS.md both cite those sizes by name, so folding WR-01's names in would have rewritten a historical measurement instead of adding to it. Both corpora go through the same `sc3Contexts` helper, so coverage is identical.

## Divergence from the plan — `token_type` (escalated, not silently resolved)

**The success criteria and the maintainer's ruling both state that `token_type` survives. It does not, and cannot, under the ruling as specified.**

`token_type` and `tokenType` are matched by the vocabulary word **`token`**, not by `key` or `code`. Narrowing the two broad words leaves them untouched. This is consistent with `21-REVIEW.md`'s own analysis — *"The driver is the two broadest words in the vocabulary, `code` and `key`. `session`, `auth` and `token` contribute a smaller tail"* — so the ruling's prose over-claims the mechanism's reach by one name.

Making `token_type` survive requires one of two things, and **both are maintainer decisions that were not put to the maintainer**:

1. A suffix denylist (`token_(type|count|…)`). D-12 rejects this on principle: *"every entry in such a list is a place where a real credential could be accidentally allowlisted."*
2. Narrowing `token` itself. `token` is the single highest-value credential word; `access-token` and `XSRF-TOKEN` in the must-redact corpus reach it only through the containment rule, so this risks the 31/31 result in the dangerous direction.

Rather than pick one, `token_type`, `tokenType`, `session_count`, `auth_type` and `auth_url` are pinned **as accepted over-redactions** with the reasoning in source, in ADR-14 and in CONCERNS.md. That keeps the plan's own principle intact — silently accepting a name is not the same as deciding it — and leaves a dated, discoverable open question. `token_type: "Bearer"` remains redacted today.

## Deviations from Plan

### Auto-fixed issues

**1. [Rule 1 - Bug] The narrowing broke `newlineFreeOversizeBodyIsScannedNotDestroyed`**

- **Found during:** Task 2, first verification run.
- **Issue:** written as its own padded top-level alternative, the prefixed broad-word rule duplicated the leading `KEY_CHARS{0,64}` scan and cost **+67%** on the dominant JSON rule (80 ms vs 48 ms on 1 MB). 21-11's 4 MB newline-free fixture crossed the body stage's budget and the window carrying the secret was dropped behind a marker — fail-closed, never a leak, but exactly the capability regression ADR-14 exists to prevent.
- **Fix:** hoisted the padding so the prefixed rule shares clause (a)'s, then folded the whole vocabulary into one first-letter-factored alternation. Measured on 1 MB, best of five: pre-WR-01 flat **50 ms**; WR-01 flat **58 ms** (+16%); prefixes-only factored **53 ms** (+6%); first-letter factored **47 ms** (-6%). The +16% and +6% shapes were both observed failing the test; the factored shape passes.
- **Files:** `Redaction.kt` (`SENSITIVE_KEY_WORDS`, `SENSITIVE_KEY_EXPR`).
- **Commit:** `0137f65`.

**2. [Rule 2 - Missing safeguard] The hand-factored vocabulary had nothing checking it**

- **Found during:** Task 2, while making fix 1.
- **Issue:** first-letter factoring a security-critical alternation is precisely the change that looks right and is subtly wrong, and it makes "add a word to the list" a non-obvious edit. Nothing would have caught a word added to `SENSITIVE_WORDS` and forgotten in the compiled form — it would have silently narrowed coverage.
- **Fix:** added the `NAIVE_KEY_EXPR_FOR_TEST` internal seam (built from the readable `SENSITIVE_WORDS` / `CREDENTIAL_PREFIXES` / `BROAD_WORDS` constants, in the established style of `testHkdfExtract` and `testSplitPoint`) and `factoredKeyVocabularyMatchesItsReadableSpecification`, which drives both expressions over all 120 corpus names and asserts identical classification. Building the reference in production from the same constants — rather than restating the vocabulary in the test — is what makes it drift-proof. It carries its own anti-vacuity guard on corpus size.
- **Files:** `Redaction.kt`, `RedactionTest.kt`.
- **Commit:** `0137f65`.

**3. [Rule 1 - Bug] Stale comments falsified by the narrowing**

Three load-bearing comments became untrue and were **updated, never deleted**: the "vocabulary is byte-identical to v0.6.0" claim (now states the single respect in which it differs); the D-13 REVERT POINT, which named `codeName`, `keyName` and `tokenCount` (now names `tokenCount` and `tokenType`, since the first two no longer depend on that revert); and the ten-item accepted-over-redaction list, which implied that was the whole class. ADR-14's original ten-item bullet is left standing and marked superseded in part, because it is the record of what the widening cost when it shipped.

## Verification

- `./gradlew test ktlintCheck detekt` — full suite, ktlint and detekt clean.
- `detekt-baseline.xml` byte-identical (`git diff --stat 98ed973..HEAD -- detekt-baseline.xml` empty) — QUAL-07 held.
- HKDF block untouched: `git diff -U0` on `Redaction.kt` returns no HKDF line other than one new comment mentioning `testHkdfExtract` by name. `hkdfMatchesRfc5869Vector` green.
- Monotonicity canaries green: `balancedModeRedactsUrlTokensInQueryStrings`, `bodyFormLeadingFieldRedacted`, `bodyJsonSecretKeysRedacted`, `offModePreservesBodies`, both locked SC6 inversions (not touched), 21-08's cookie tests, `cookieSectionDecoyDoesNotShieldRealSection`, 21-09's windowing sweeps and 21-11's seam tests.
- `cookieSectionDecoyDoesNotShieldRealSection`'s `abtest_bucket` sentinel was re-measured and is still unreachable by `SENSITIVE_KEY_EXPR`, so the decoy still isolates the section rule.
- Plan grep criteria: `status_code` in `RedactionTest.kt` = 2; `status_code` in `DECISIONS.md` = 1; `BROAD_WORDS` in `Redaction.kt` = 17 (≥ 2 required for option-b); `REVERT POINT` in `Redaction.kt` = 1.

### How the corpus was proven non-vacuous — by mutation, not inspection

Each mutation was applied on top of the **committed** implementation and reverted with `git checkout -- <path>`, per this phase's hygiene rule. Every one killed exactly the intended tests:

| Mutation | Tests killed |
|---|---|
| Append `\|key\|code` to the vocabulary (restore broad containment) | `wr01BroadWordKeysSurviveUnlessCredentialBearing`, `factoredKeyVocabularyMatchesItsReadableSpecification` |
| Delete the bare `(?:key\|code)` alternative (drop whole-key equality) | `sensitiveKeyNamesRedacted`, `wr01BroadWordKeysSurviveUnlessCredentialBearing`, `factoredKeyVocabularyMatchesItsReadableSpecification` |
| Delete the `access` prefix branch | `wr01BroadWordKeysSurviveUnlessCredentialBearing`, `factoredKeyVocabularyMatchesItsReadableSpecification` |
| Narrow `token` with a lookahead | `wr01NonBroadWordOverRedactionsRemainAccepted`, `camelCaseKeysRedactedWithAcceptedOverRedactions`, `factoredKeyVocabularyMatchesItsReadableSpecification` |

Both directions of the new corpus and the accepted-residual corpus are therefore discriminating, and the equivalence test detects every vocabulary change. The suite was re-run green after the final revert.

## Deferred Issues

**`newlineFreeOversizeBodyIsScannedNotDestroyed` remains timing-marginal — pre-existing, and measurably improved rather than worsened.**

This test failed once under `test ktlintCheck detekt` on the full suite, which sent me down a long diagnostic path. The conclusion, measured rather than assumed:

- The base was established under the **exact** failing command before attributing anything: 3/3 green on the redact subset and 1/1 green on the full suite.
- End-to-end on the identical 4 MB fixture, five runs each: base **1933-2051 ms**, this change **1857-1978 ms** — roughly **4% faster**. The body-stage budget is 2000 ms, so the fixture sits at ~95-100% of budget **on both sides**.
- Final tally with the shipped change: redact subset + lint 3/3 green; full suite without lint 1/1 green; full suite with lint 2/3 green.

The one failure was load noise on a fixture that is at the budget edge regardless of this change — the exposure the test's own source comment declares (*"If this test ever goes red in CI while `splitPointCutsNewlineFreeWindowsInsteadOfRefusing` stays green, the diagnosis is deadline pressure under instrumentation"*), and that deterministic sibling stayed green throughout. Worth a future plan giving the fixture headroom or the budget a machine-relative floor; not this plan's to fix.

**Methodological note worth carrying forward:** the isolated `Regex.replace` benchmark **inverted the sign** of the real result. It said the factored form was 6% faster while the instrumented production path (`SafeRegex.DeadlineCharSequence` instruments every `charAt`) put it 4% faster on a different basis — and the same benchmark would have justified shipping the +6% shape that demonstrably failed. Measure the production path.

**Not fixed, by design:** the four warnings the plan lists as deliberately deferred (WR-02, WR-03, WR-04, WR-07) remain open with their reasons recorded in `21-12-PLAN.md` §Deferred. None is a blocker and none was touched.

## Known Stubs

None. No placeholder values, no unwired data paths, no TODO markers introduced.

## Threat Flags

No new security-relevant surface. The change **removes** redaction from 27 key names, which is the deliberate subject of the WR-01 ruling rather than an unflagged widening; the accepted cost (`stripe_key`-class bespoke names, `public_key`) is recorded in the corpus, in `BROAD_WORDS`' source comment, in ADR-14 and in CONCERNS.md, as the success criteria require. `T-21-37` (losing a must-redact key while narrowing) was mitigated as the threat register specifies: all three corpora were re-measured against the live regexes and 31/31 held.

## Self-Check: PASSED

- `.planning/phases/21-redaction-completeness/21-12-SUMMARY.md` — created.
- `src/main/kotlin/com/six2dez/burp/aiagent/redact/Redaction.kt` — modified, present.
- `src/test/kotlin/com/six2dez/burp/aiagent/redact/RedactionTest.kt` — modified, present.
- `DECISIONS.md`, `.planning/codebase/CONCERNS.md` — modified, present.
- Commits `0137f65` and `3b1ee40` present in `git log`.
- `STATE.md`, `ROADMAP.md` and `REQUIREMENTS.md` not modified, as instructed.
