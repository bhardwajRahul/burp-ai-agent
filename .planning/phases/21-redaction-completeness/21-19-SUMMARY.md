---
phase: 21-redaction-completeness
plan: 19
subsystem: privacy-redaction
tags: [redaction, cookie-headers, header-name-matching, prompt-leak, gap-closure, kotlin]

requires:
  - phase: 21-redaction-completeness
    provides: "cookieHeaderRegex / setCookieHeaderRegex header stage and the authHeaderRegex name-preserving replacement pattern (pre-existing)"
provides:
  - "Both cookie header rules match name-contains-cookie instead of the two exact names, closing W-A"
  - "Both cookie replacements preserve the matched header NAME rather than emitting a fixed string"
  - "cookieHeaderRegex and setCookieHeaderRegex are mutually exclusive by construction, not merely by call order"
  - "cookieHeaderNameVariantsAreStripped: the five measured variants, both modes, mutation-checked in two directions"
affects: [future-redaction-rule-authors, phase-22-prompt-builders]

tech-stack:
  added: []
  patterns:
    - "A redaction rule keys on the SAME predicate the prompt builder admits on, so the two agree by construction rather than by two hand-synced lists"
    - "Header rules that can match more than one name use name-preserving replacement lambdas; fixed replacement strings are only safe for exact-name matches"
    - "Sibling regexes are made mutually exclusive with a negative lookahead so their partition survives a reordering of the call site"

key-files:
  created: []
  modified:
    - src/main/kotlin/com/six2dez/burp/aiagent/redact/Redaction.kt
    - src/test/kotlin/com/six2dez/burp/aiagent/redact/RedactionTest.kt
    - .planning/codebase/CONCERNS.md

key-decisions:
  - "W-A CLOSED rather than RECORDED: name-contains-cookie is bounded and complete, unlike the open-ended vendor auth-header list, which stays an accepted residual"
  - "Matched on the same predicate sanitizeHeadersForPrompt already admits on (lowercased name CONTAINS cookie) rather than extending an alternation of names — the gap existed precisely because two lists disagreed"
  - "Both replacements converted to name-preserving lambdas; widening the match while keeping the fixed string would have renamed X-Cookie to Cookie in the analyst's prompt (T-21-WA2)"
  - "The two regexes are kept disjoint by a negative lookahead rather than by relying on cookie-then-set-cookie call order, so the request/response distinction cannot be lost by a future reorder"
  - "Sentinel values carry no '=' at all, which is what makes the fixture unreachable by every non-cookie rule in the pipeline"

patterns-established:
  - "Mutate each sibling regex SEPARATELY: a shared test that only one mutation kills is hiding a redundant rule. Both mutations here were killed, with disjoint failure sets"
  - "Embed the whole output blob in the assertion message — the RED report then measures the FULL leak set in one run despite assertTrue being fail-fast"

requirements-completed: [PRIV-05]

duration: 35min
completed: 2026-08-13
---

# Phase 21 Plan 19: Cookie Header Name Variants (W-A) Summary

**Closed the last open finding of the phase: any header whose NAME contains `cookie` is now stripped under STRICT and BALANCED — keeping its own name — where previously only the two exact spellings `Cookie` and `Set-Cookie` were, letting five real header names reach the AI backend verbatim.**

## Performance

- **Duration:** ~35 min
- **Tasks:** 3/3
- **Files modified:** 3
- **Tests:** 668 -> 669 (+1), 0 failures, 0 errors
- **detekt-baseline.xml:** `git diff --stat` empty (QUAL-07 held)

## Commits

| Commit | Gate | Description |
|--------|------|-------------|
| `cae0791` | RED | `test(21-19)`: failing test for the five cookie header name variants |
| `f1d5a83` | GREEN | `fix(21-19)`: strip any header whose name contains `cookie`, preserving the name |
| `de7fd16` | — | `docs(21-19)`: record the cookie NAME class closed, vendor auth still accepted |

## What Shipped

`sanitizeHeadersForPrompt` admits any header whose lowercased name **contains** `cookie`. The two
redaction rules anchored on the exact names `^cookie:` / `^set-cookie:`. Everything in the gap between
those two predicates was admitted to the prompt and never stripped — measured by the phase verifier on
five real names: `Cookie2`, `X-Cookie`, `Set-Cookie2`, `X-Original-Cookie`, `X-Forwarded-Cookie`.

The fix keys the rules on **the same predicate the prompt builder admits on**, so the two sides now
agree by construction rather than by two hand-maintained lists staying in sync:

- `cookieHeaderRegex` -> `(?im)^(?!<name>set-cookie)<name>cookie<name>:\s*.+$`
- `setCookieHeaderRegex` -> `(?im)^<name>set-cookie<name>:\s*.+$`

where `<name>` is `COOKIE_NAME_PART` = `[A-Za-z0-9-]*`.

Both replacements moved from **fixed strings** to name-preserving lambdas mirroring `authHeaderRegex`.
This half is not incidental — it is trap 1, and skipping it would have turned a leak into a
falsification: widening the match while keeping `"Cookie: [STRIPPED]"` rewrites `X-Cookie: v` into
`Cookie: [STRIPPED]`, silently **renaming** a header in the analyst's view of the traffic (T-21-WA2).

The negative lookahead is trap 2's answer. Without it the widened `cookieHeaderRegex` would also match
`Set-Cookie*` — producing correct output text, but collapsing the request/response distinction and
making `setCookieHeaderRegex` dead code that only *appeared* live because it ran second. The lookahead
makes the partition a property of the regexes rather than of the call order in `apply`.

## Fixture Reachability Argument

The dominant defect of this phase is fixture vacuity (nine instances). Each sentinel is a bare
lowercase alphabetic word (`sentinelalphaone` ... `sentinelechofive`), which makes a cookie **header**
rule the only thing in the pipeline that can remove it:

| Rule | Why it cannot match |
|------|---------------------|
| `formBodyParamRegex` `(^\|[?&])KEY=...` | no `=` anywhere on the line |
| `urlTokenParamRegex` `[?&]KEY=...` | no `=`, no `?`/`&` |
| `cookieTypedParamRegex` `NAME=VALUE (type)` | no `=` |
| `jsonSecretKeyRegex` `"KEY"\s*:\s*VALUE` | key is unquoted at line start; `"` is not a header-name char |
| `bearerRegex` / `basicAuthRegex` | no `Bearer `/`Basic ` prefix |
| `jwtRegex` | no dotted `eyJ` segment |
| `authHeaderRegex` | none of the five names is in its 14-name list |
| `SENSITIVE_KEY_EXPR` consumers | only ever consulted as a KEY followed by `=` or inside quotes |
| `redactCookieSections` | blob carries no `=== COOKIES ===` header |

`X-Request-Id: benignidcontrolvalue` is the negative control: it must survive both modes, which is what
distinguishes *stripping cookie headers* from *blanket header loss*.

The argument above is stated in a comment on the test, but it is **checked by mutation, not trusted**.

## Mutation Results

Both mutations were run on top of the committed implementation and reverted with
`git checkout -- <path>`; the tree returned to a clean committed boundary after each.

| Mutation | Result | Failure set (sentinels surviving redaction) |
|----------|--------|---------------------------------------------|
| `cookieHeaderRegex` -> `^cookie:\s*.+$` | 46 tests, **1 failed** | `Cookie2`, `X-Cookie`, `X-Original-Cookie`, `X-Forwarded-Cookie` — `Set-Cookie2` still stripped |
| `setCookieHeaderRegex` -> `^set-cookie:\s*.+$` | 46 tests, **1 failed** | `Set-Cookie2` only — the other four still stripped |

The failure sets are **disjoint and exhaustive**, which is the load-bearing result: neither regex is
redundant, neither mutation is survived, and the second one specifically proves the negative lookahead
is real. Had the lookahead been absent, the widened `cookieHeaderRegex` would have caught `Set-Cookie2`
and mutation 2 would have been *survived* — a vacuous rule hiding behind a passing test.

**RED evidence (Task 1, before the fix):** 46 tests, 1 failed;
`STRICT: the value of Cookie2 must not reach the prompt`. `assertTrue` is fail-fast, so the assertion
names only the first variant — but the message embeds the whole output blob, and **all five** sentinels
are present in it. The full leak set was therefore measured in the RED run, not inferred.

## Invariance Guards Held

- `RedactionTest.strictModeStripsCookiesTokensAndHosts:366` — green **unchanged**, `Cookie: [STRIPPED]`
- `BountyPromptTagResolverTest:93` — green **unchanged**, same literal
- `balancedModeRedactsCustomAuthHeaders`, `offModePreservesBodies`, `bodyFormLeadingFieldRedacted`,
  `cookieSectionDecoyDoesNotShieldRealSection`, the two locked SC6 inversions and 21-12's 120-name
  corpus — all green, none edited

`substringBefore(":")` returns `Cookie` and `Set-Cookie` unchanged, which is why the two canonical
renderings are byte-identical to before. That invariance is the regression guard for any future edit
to these rules, and it was achieved without touching either test.

## Scope Discipline

`git diff -U0` on `Redaction.kt` is exactly **two hunks** — the regex definitions and the two
replacements. No line touches the HKDF block, `redactCookieSections`, `cookieSectionEnd`,
`cookieTypedParamRegex`, `endsInsideOpenQuotedValue`, `windowEnd`, `splitPoint`, `safeCutPoint`,
`testKeyRules` or `SENSITIVE_KEY_EXPR`.

## Deviations from Plan

**1. [Rule 3 - Blocking] ktlint `spacing-between-declarations-with-comments`**

- **Found during:** Task 2
- **Issue:** the `// W-A:` comment block was placed immediately after `basicAuthRegex` with no blank
  line, which ktlint rejects (`Redaction.kt:81:5`). Build failed at `ktlintMainSourceSetCheck`.
- **Fix:** inserted a blank line before the comment block. No semantic change.
- **Files modified:** `src/main/kotlin/com/six2dez/burp/aiagent/redact/Redaction.kt`
- **Commit:** `f1d5a83` (folded into the Task 2 commit — it was fixed before that commit was made)

No other deviation. No architectural decision was required; no auth gate was hit.

## Record Correction (Task 3)

`.planning/codebase/CONCERNS.md` §"Redaction regex coverage gaps" now states that the cookie header
**NAME** class is **closed** — recorded as fixed, not as an accepted residual — while the
`authHeaderRegex` vendor-header class remains accepted and deferred, with the reason stated explicitly:
a vendor list (`x-shopify-access-token`, `stripe-signature`, and whatever ships next quarter) is
open-ended and never complete, whereas name-contains-`cookie` is bounded and complete over a class
whose members are all cookie-bearing by convention. Both traps and both mutation directions are
recorded there too, so the next person to touch these rules inherits the reasoning rather than
rediscovering it. No other entry was modified.

## Known Stubs

None. No placeholder values, no unwired data paths, no TODO/FIXME introduced.

## Threat Flags

None. The change removes prompt-reaching surface rather than adding any; no new endpoint, auth path,
file access pattern or schema change at a trust boundary.

## Self-Check: PASSED

- `src/main/kotlin/com/six2dez/burp/aiagent/redact/Redaction.kt` — FOUND (modified)
- `src/test/kotlin/com/six2dez/burp/aiagent/redact/RedactionTest.kt` — FOUND (modified)
- `.planning/codebase/CONCERNS.md` — FOUND (modified)
- `.planning/phases/21-redaction-completeness/21-19-SUMMARY.md` — FOUND (created)
- Commits `cae0791`, `f1d5a83`, `de7fd16` — all FOUND in `git log`
- Full suite: 669 tests, 0 failures; `ktlintCheck` and `detekt` green; baseline diff empty
