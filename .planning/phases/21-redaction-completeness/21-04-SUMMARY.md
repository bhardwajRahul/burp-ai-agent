---
phase: 21-redaction-completeness
plan: 04
subsystem: redact
tags: [PRIV-05, SC3, D-11, D-12, D-13, redaction, regex]
requires:
  - "redact/Redaction.kt SENSITIVE_KEYS (v0.6.0 twelve-word alternation)"
provides:
  - "SENSITIVE_KEY_EXPR — the shared sensitive-key expression"
  - "SENSITIVE_WORDS / KNOWN_SESSION_KEYS / KEY_CHARS / WORD_BEFORE / WORD_AFTER"
  - "SC3 both-directions corpus (31 must-redact, 21 must-not-redact, camelCase set)"
affects:
  - "urlTokenParamRegex (query-string stage)"
  - "formBodyParamRegex (form-body stage)"
  - "jsonSecretKeyRegex (JSON body stage)"
tech-stack:
  added: []
  patterns:
    - "One shared const key expression interpolated into three consumer regexes"
    - "Java inline flag-off group (?-i:...) for camelCase boundaries under an outer (?i)"
    - "Bounded {0,64} prefix/suffix instead of unbounded * adjacent to a quantifier"
key-files:
  created: []
  modified:
    - src/main/kotlin/com/six2dez/burp/aiagent/redact/Redaction.kt
    - src/test/kotlin/com/six2dez/burp/aiagent/redact/RedactionTest.kt
decisions:
  - "D-11: token-boundary containment + 17-entry vendor list; vocabulary gains ZERO new words"
  - "D-12: no benign-key denylist — measurement found nothing to guard against"
  - "D-13: camelCase boundary ships via (?-i:...); three accepted over-redactions asserted as accepted"
metrics:
  duration: ~25m
  completed: 2026-08-11
---

# Phase 21 Plan 04: Sensitive-Key Matching Mechanism Summary

Replaced the exact-word `SENSITIVE_KEYS` alternation with a shared token-boundary key expression
plus a vendor-name list, so `auth_token`, `api-key`, `X-Session-Id`, `JSESSIONID`, `PHPSESSID`,
`connect.sid`, `csrftoken` and `remember_me` now redact in query-string, form-body and JSON
contexts — 18 key names that previously reached the AI backend verbatim.

## What Shipped

### Task 1 — `SENSITIVE_KEY_EXPR` and the three rewired consumers (`ef47fd8`)

Five `private const val`s replaced the single `SENSITIVE_KEYS` constant in `object Redaction`.

**The final `SENSITIVE_KEY_EXPR` value, verbatim (Kotlin source form):**

```kotlin
private const val SENSITIVE_KEY_EXPR =
    "(?:(?:$KNOWN_SESSION_KEYS)|" +
        "$KEY_CHARS{0,64}$WORD_BEFORE(?:$SENSITIVE_WORDS)$WORD_AFTER$KEY_CHARS{0,64})"
```

**Its five inputs, verbatim (Kotlin source form — regex backslashes are doubled):**

```kotlin
private const val SENSITIVE_WORDS =
    "access_token|api_key|apikey|auth|token|key|secret|password|pwd|session|sid|code"

private const val KNOWN_SESSION_KEYS =
    "jsessionid|phpsessid|asp\\.net_sessionid|\\.aspxauth|aspxauth|csrftoken|" +
        "remember_me|remember_token|laravel_session|ci_session|_session_id|sessionid|sessid|" +
        "cfid|cftoken|xsrf-token|_csrf"

private const val KEY_CHARS = "[A-Za-z0-9_.\\-\\[\\]]"

private const val WORD_BEFORE = "(?:(?<![A-Za-z0-9])|(?-i:(?<=[a-z0-9])(?=[A-Z])))"
private const val WORD_AFTER = "(?:(?![A-Za-z0-9])|(?-i:(?<=[a-z0-9])(?=[A-Z])))"
```

**Fully expanded regex form** (what the engine actually compiles, for ADR-14 reference):

```
(?:(?:jsessionid|phpsessid|asp\.net_sessionid|\.aspxauth|aspxauth|csrftoken|remember_me|remember_token|laravel_session|ci_session|_session_id|sessionid|sessid|cfid|cftoken|xsrf-token|_csrf)|[A-Za-z0-9_.\-\[\]]{0,64}(?:(?<![A-Za-z0-9])|(?-i:(?<=[a-z0-9])(?=[A-Z])))(?:access_token|api_key|apikey|auth|token|key|secret|password|pwd|session|sid|code)(?:(?![A-Za-z0-9])|(?-i:(?<=[a-z0-9])(?=[A-Z])))[A-Za-z0-9_.\-\[\]]{0,64})
```

**The three consumers after rewiring** (group numbering unchanged, so every replacement expression
in `apply()` is untouched):

| Consumer | New value | Groups |
|---|---|---|
| `urlTokenParamRegex` | `(?i)([?&](?:$SENSITIVE_KEY_EXPR)=)[^&\s"'<>]+` | 1 = `[?&]key=` prefix → `"$1[REDACTED]"` unchanged |
| `formBodyParamRegex` | `(?im)(^\|[?&])($SENSITIVE_KEY_EXPR)=[^&\s"'<>]+` | 1 = `^`/`?`/`&`, 2 = whole key → lambda unchanged |
| `jsonSecretKeyRegex` | `(?i)("$SENSITIVE_KEY_EXPR"\s*:\s*)("[^"]*"\|true\|false\|null\|-?\d+(?:\.\d+)?)` | 1 = `"key":`, 2 = value → lambda unchanged |

`jsonSecretKeyRegex` dropped the old explicit `(?:...)` wrapper because the key expression is
already `(?:...)`-wrapped; `urlTokenParamRegex` keeps its wrapper as specified (harmless,
non-capturing).

### Task 2 — the SC3 both-directions corpus (`7f1773f`)

Three tests added to `RedactionTest`, using the file's existing `listOf(...)` + inner-loop idiom
(not `@ParameterizedTest`), one shared sentinel `SENTINEL-VALUE-9F2A7C`, and a
context-label → output map so every assertion message names the exact failing cell:

| Test | Corpus | Contexts | Modes |
|---|---|---|---|
| `sensitiveKeyNamesRedacted` | 31 keys must redact | query, form, JSON | STRICT + BALANCED |
| `benignKeyNamesNotRedacted` | 21 keys must survive | query, form, JSON | STRICT |
| `camelCaseKeysRedactedWithAcceptedOverRedactions` | 3 gains + 3 accepted FPs + 2 must-survive | query, form, JSON | STRICT |

Each must-redact iteration also asserts the context's benign companion survives
(`name=alice` / `user=bob` / `"name":"alice"`), so a regression that "passes" by redacting
everything fails instead. Both corpora carry a size assertion (31 / 21) so silent shrinkage fails.

## Verified RED before GREEN (Phase 20 SC4 discipline)

The plan warns that SC3's must-not limb is a guard rather than a fix. Rather than assume, the new
tests were executed against the pre-change engine by temporarily restoring `Redaction.kt` from
`HEAD~1` (both versions committed; working tree restored and confirmed clean afterwards):

| Test | Against old engine | Against new engine | Interpretation |
|---|---|---|---|
| `sensitiveKeyNamesRedacted` | **FAILED** — `STRICT / query string: the value of sensitive key 'auth_token' must be redacted ==> expected: <false> but was: <true>` | PASSED | genuine fix |
| `camelCaseKeysRedactedWithAcceptedOverRedactions` | **FAILED** — `STRICT / query string: camelCase key 'authToken' must be redacted (D-13 gain) ==> expected: <false> but was: <true>` | PASSED | genuine fix |
| `benignKeyNamesNotRedacted` | PASSED | PASSED | **regression guard, as designed (D-12 / Pitfall 1)** — labelled as such in the test comment |

This confirms the research finding empirically: `keyboard_layout` and `codename` were never
over-redacted, so the must-not limb tests the *new mechanism*, not a defect.

## Accepted Over-Redactions (exact list, for ADR-14 consequences)

All are over-redaction — the fail-safe direction — and all lose analytically low-value data.

**Under the separator rule (D-11), 7:**
`token_bucket_size`, `session_timeout_seconds`, `auth_provider`, `key_size`, `code_version`,
`secret_santa`, `password_hint_enabled`.

**Under the camelCase rule (D-13), 3:**
`codeName`, `keyName`, `tokenCount` — asserted **as accepted** in
`camelCaseKeysRedactedWithAcceptedOverRedactions`, so the behaviour is deliberate and recorded
rather than discovered in the field.

`auth_provider` is the only entry with real analytic value (`auth_provider=google` tells the model
something); redacting a key literally named `auth_*` is still the correct default.

**Revert point for D-13 (named in both source files):** delete the `(?-i:...)` alternative from
`WORD_BEFORE` and `WORD_AFTER` in `Redaction.kt`. That loses nothing SC3 requires and removes
exactly the three camelCase over-redactions above.

## Residual for plan 21-07 / CONCERNS.md

**Plural key forms are not handled.** `codes`, `tokens`, `keys` do not redact. The one-character
recipe is adding `s?` after the vocabulary alternation, at the cost of a second widening axis and
six more tests. SC3 does not require it. Recorded in the `SENSITIVE_KEY_EXPR` comment block in
source; `codes` is in the 21-key must-not-redact corpus, so adopting plurals later is a deliberate
test change, not a silent one.

## Deviations from Plan

None — plan executed exactly as written. No auto-fixes were required; no architectural decisions
arose; no package installs were attempted (zero new dependencies).

## Verification Results

| Check | Result |
|---|---|
| `./gradlew test -q` | exit 0 — full suite green |
| `./gradlew ktlintCheck detekt -q` | exit 0 |
| `git diff --stat -- detekt-baseline.xml` | empty (QUAL-07 respected) |
| `git diff <base> HEAD -- Redaction.kt \| grep -c 'hkdf\|anonymizeHost\|HOST_MAP_CAP'` | **0** — SC6 boundary honoured |
| `RedactionTest` cases executed | 18, all passing |

**Monotonicity canaries — all green**, proving no key that redacted before stopped redacting and
no blanket over-redaction crept in:

- `balancedModeRedactsUrlTokensInQueryStrings` (`name=alice` survives)
- `bodyFormLeadingFieldRedacted` (`user=bob` survives)
- `bodyJsonSecretKeysRedacted` (`"name":"alice"` survives)
- `bodyJsonUnquotedSecretValuesRedacted` (`"balance":99.5` survives, `"sid":-42` still redacts)

**SC6 hard boundary:** `hkdfMatchesRfc5869Vector` green; `Redaction.kt:167-227` (HKDF constants
through the `testHkdfExpand` seam) is byte-identical to the base commit — the diff contains zero
lines matching `hkdf`, `anonymizeHost` or `HOST_MAP_CAP`.

**Acceptance greps (Task 1):** `SENSITIVE_KEY_EXPR` = 4 lines, `private const val SENSITIVE_KEYS`
= 0, twelve-word vocabulary = 1, `(?-i:` = 4 (2 constants + 2 mandated explanatory comments),
`jsessionid|phpsessid` = 1, `D-12` = 1.

**Acceptance greps (Task 2):** each of the three `fun` definitions = 1; `regression guard` = 1;
`D-13` = 6; every named key (`JSESSIONID`, `PHPSESSID`, `connect.sid`, `auth_token`, `csrftoken`,
`api-key`, `X-Session-Id`, `remember_me`, `keyboard_layout`, `codename`) present.

## Known Stubs

None. Both files are fully wired: the key expression is consumed by all three production regexes
and every corpus entry is asserted in live `Redaction.apply` calls.

## Threat Flags

None. No new network endpoint, auth path, file access pattern or schema change was introduced —
this plan changed one regex constant and its three consumers, all already inside the existing
redaction trust boundary.

## Commits

| Task | Commit | Description |
|---|---|---|
| 1 | `ef47fd8` | feat(21-04): replace SENSITIVE_KEYS with a token-boundary key expression |
| 2 | `7f1773f` | test(21-04): add the SC3 both-directions key corpus across query, form and JSON |

## Self-Check: PASSED

- `src/main/kotlin/com/six2dez/burp/aiagent/redact/Redaction.kt` — FOUND, contains `SENSITIVE_KEY_EXPR`
- `src/test/kotlin/com/six2dez/burp/aiagent/redact/RedactionTest.kt` — FOUND, contains `benignKeyNamesNotRedacted`
- Commit `ef47fd8` — FOUND
- Commit `7f1773f` — FOUND
