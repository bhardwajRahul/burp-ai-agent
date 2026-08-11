---
phase: 21-redaction-completeness
plan: 05
subsystem: redact
tags: [PRIV-05, SC1, SC2, D-09, D-10, T-21-01, T-21-16, T-21-20, kotlin, redaction, privacy, cookies]

# Dependency graph
requires:
  - phase: 21-redaction-completeness
    plan: 01
    provides: "buildScanMetadataText / formatParamLine / redactScanMetadata and the PassiveAiScannerPromptRedactionTest seam"
  - phase: 21-redaction-completeness
    plan: 04
    provides: "SENSITIVE_KEY_EXPR — independent defence in depth for the five SC1 vendor names"
provides:
  - "Redaction.COOKIE_SECTION_HEADER — the single source of the cookie-section literal in src/main, imported by the emitter"
  - "Redaction.redactCookieSections — every value, every occurrence, names preserved"
  - "Redaction.cookieTypedParamRegex — context-free COOKIE-typed parameter rule keyed on the Montoya type suffix"
  - "Three RedactionTest cases (SC1 per name, SC2 by type suffix, D-10 decoy) and three end-to-end PassiveAiScannerPromptRedactionTest cases"
affects: [21-06, 21-07, redaction, passive-scanner, prompt-building]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Emitter labels, redactor enforces: a shared const val turns a section rename into a compile error, paired with a parity test that turns a silent format change into a test failure"
    - "Type-suffix discrimination: keying a rule on a Montoya semantic type label instead of a prompt-layout string makes it context-free"
    - "Mutation-verified tests: every new security assertion was re-run against a surgical mutation of the rule it guards, to prove it is not vacuous"

key-files:
  created: []
  modified:
    - src/main/kotlin/com/six2dez/burp/aiagent/redact/Redaction.kt
    - src/main/kotlin/com/six2dez/burp/aiagent/scanner/PassiveAiScannerPrompts.kt
    - src/test/kotlin/com/six2dez/burp/aiagent/redact/RedactionTest.kt
    - src/test/kotlin/com/six2dez/burp/aiagent/scanner/PassiveAiScannerPromptRedactionTest.kt

key-decisions:
  - "D-10's loop is written as while(true) + early return, because Kotlin has no assignment-in-condition; the invariant (from = bodyStart > h) is stated in an inline comment so the termination argument is not left implicit"
  - "detekt MagicNumber on groupValues[3] resolved with MatchResult.destructured, not a named constant and not a baseline entry (QUAL-07)"
  - "Both SC2 tests gained an unremarkably-named COOKIE-typed parameter after measurement proved the plan's specified inputs could not detect the defect"
  - "Comment markers follow the destination file: /** */ on the public const, // on the private regexes and the private function, matching Redaction.kt's existing split"

requirements-completed: [PRIV-05]

# Metrics
duration: 23min
completed: 2026-08-11
---

# Phase 21 Plan 05: Cookie Leak — Both Entry Points Closed Summary

**Two rules in `redact/Redaction.kt` close PRIV-05's cookie leak at both entry points: a section-scoped rule that redacts every value in every occurrence of the cookie section, and a context-free rule keyed on the Montoya ` (COOKIE)` type suffix — with all six new tests proven RED against surgical mutations of the rules they guard.**

## Performance

- **Duration:** 23 min
- **Started:** 2026-08-11T13:31:38Z
- **Completed:** 2026-08-11T13:54:18Z
- **Tasks:** 3
- **Files modified:** 4 (all modified, none created)

## Accomplishments

- **SC1 closed.** `redactCookieSections` redacts the value of every `name=value` pair inside every occurrence of the cookie section, preserving names. `JSESSIONID`, `PHPSESSID`, `connect.sid`, `auth_token`, `csrftoken` and `abtest_bucket` are each asserted per name in STRICT and BALANCED — directly, and end-to-end against the real emitted blob.
- **SC2 closed.** `cookieTypedParamRegex` strips the value from a `COOKIE`-typed parameter line while preserving both the name and the ` (COOKIE)` suffix; `(URL)` and `(BODY)` lines survive byte-for-byte.
- **D-10 mitigated and proven.** The section rule iterates every occurrence. Verified by mutation: turning the loop into a first-occurrence-only scan makes `cookieSectionDecoyDoesNotShieldRealSection` fail on exactly the `abtest_bucket` assertion — confirming the research's finding that the widened key expression rescues `JSESSIONID` even in the vulnerable case, so only the unremarkably-named cookie is decisive.
- **T-21-20 mitigated.** The section literal now exists exactly once in `src/main`, as `Redaction.COOKIE_SECTION_HEADER`. A rename is a compile error; a silent format change *around* it fails `emittedBlobContainsTheSectionConstant_parity`.
- **SC6 boundary honoured.** `git diff` of `Redaction.kt` against the base contains **zero** lines matching `hkdf`, `anonymizeHost` or `HOST_MAP_CAP`. `hkdfMatchesRfc5869Vector` green.
- detekt baseline unchanged, ktlint green, full suite green.

## Task Commits

Each task was committed atomically:

1. **Task 1: Add the two cookie rules and the shared section constant** — `e642922` (feat)
2. **Task 2: Assert SC1 per name, SC2 by type suffix, and the D-10 decoy regression** — `fd71c4b` (test)
3. **Task 3: Assert SC1 and SC2 end-to-end against the real emitted blob** — `fec3cf4` (test)

## Files Created/Modified

- `src/main/kotlin/com/six2dez/burp/aiagent/redact/Redaction.kt` — the public section constant, two private span regexes, `redactCookieSections`, `cookieTypedParamRegex`, and the two new lines inside `apply`'s `policy.stripCookies` block
- `src/main/kotlin/com/six2dez/burp/aiagent/scanner/PassiveAiScannerPrompts.kt` — `buildScanMetadataText` appends `Redaction.COOKIE_SECTION_HEADER` instead of the inline literal; the parameters-section literal is deliberately left inline
- `src/test/kotlin/com/six2dez/burp/aiagent/redact/RedactionTest.kt` — three tests added after the SC3 corpus; no existing test modified
- `src/test/kotlin/com/six2dez/burp/aiagent/scanner/PassiveAiScannerPromptRedactionTest.kt` — three tests added before the `metadataBlob` helper; the four tests from plan 21-01 are untouched and still green

## The Shipped Rules (verbatim, for ADR-14 in plan 21-07)

**The shared constant** — `Redaction.kt:94`, the only occurrence of the literal in `src/main`:

```kotlin
const val COOKIE_SECTION_HEADER = "=== COOKIES ==="
```

Consumed by the emitter at `PassiveAiScannerPrompts.kt:109` (`appendLine(Redaction.COOKIE_SECTION_HEADER)`).

**The two span regexes** — `Redaction.kt:99` and `:103`:

```kotlin
private val cookieSectionPairRegex = Regex("(?m)^([^=\\r\\n]+)=(.*)$")
private val nextSectionRegex = Regex("(?m)^=== ")
```

**The type-suffix rule** — `Redaction.kt:166`, applied at `:404-410`:

```kotlin
private val cookieTypedParamRegex = Regex("(?im)^([^=\\r\\n]+)=(.*?)(\\s\\(COOKIE\\))\\s*$")

// applied as (group 3 is written back verbatim):
val (name, _, typeSuffix) = m.destructured
"$name=[REDACTED]$typeSuffix"
```

**Exact span-termination logic** — `redactCookieSections`, `Redaction.kt:131-152`. The span runs from just past the header to the **minimum** of three bounds:

| Bound | Expression | Notes |
|---|---|---|
| end of text | `out.length` | the initial value; used when the section is last |
| the blank line that terminates the section | `out.indexOf("\n\n", bodyStart)` | applied only when `>= 0` |
| the start of the next prompt section | `nextSectionRegex.find(out, bodyStart)?.range?.first` | applied only when non-null |

The loop then rebuilds `out` as `prefix + redactedSection + suffix` and sets `from = bodyStart`. Because `bodyStart = h + COOKIE_SECTION_HEADER.length` is strictly greater than `h`, `from` increases every iteration and the loop always terminates; because only the span is rewritten, the prefix indices below `bodyStart` stay valid across iterations.

**Wiring** — `Redaction.kt:395-411`, inside `if (policy.stripCookies)`, after the two header rules and **before** the `redactTokens` body stage. `stripCookies` is true in STRICT and BALANCED and false in OFF, which is what keeps every OFF test green.

## Idempotency Under the Body Stage — Verified, Not Assumed

The plan required this be checked rather than assumed. Both rules emit `NAME=[REDACTED]`, which the widened `formBodyParamRegex` re-matches when `NAME` is sensitive: group 1 is the `^` anchor, group 2 is the whole key, and the value class `[^&\s"'<>]+` consumes `[REDACTED]` (both `[` and `]` are in the class). The replacement reproduces `NAME=[REDACTED]` byte-for-byte. Confirmed empirically — `JSESSIONID`, `connect.sid`, `auth_token`, `csrftoken`, `PHPSESSID` and `remember_me` all pass through the body stage a second time in the new tests with no change, and the `(COOKIE)` suffix survives because the value class stops at the space.

## Red-Before-Green: Every New Test Mutation-Verified

Phase 20's SC4 discipline, carried forward from plan 21-04. Each rule was surgically mutated and the guarding tests re-run; `Redaction.kt` was restored with `git checkout HEAD -- <path>` after each and the working tree confirmed clean.

| Mutation | Test | Result | What it proves |
|---|---|---|---|
| `from = bodyStart` → `return out` (first-occurrence-only, the D-10 defect) | `cookieSectionDecoyDoesNotShieldRealSection` | **FAILED** — `STRICT: a decoy section header must not shield an unremarkably-named real cookie ==> expected: <false> but was: <true>` | The decoy attack is real and the loop is the fix. It fails on `abtest_bucket`, **not** on `JSESSIONID` — the key expression from 21-04 rescues the latter even with the defect present, exactly as the research predicted |
| section rule unwired from `apply` | `cookieSectionValuesRedactedPerName` | **FAILED** — `STRICT: the value of cookie 'abtest_bucket' must be absent` | SC1's section rule is load-bearing |
| type-suffix rule unwired from `apply` | `cookieTypedParametersRedacted` | **FAILED** — `STRICT: an unremarkably-named COOKIE-typed parameter is saved by the type suffix alone` | SC2's rule is load-bearing **only after** the deviation below; see Deviation 2 |

## Decisions Made

- **`while (true)` + early `return`, not `while ((h = ...) >= 0)`.** Kotlin has no assignment-in-condition, so the research's reference shape is the idiomatic translation. The termination invariant is stated in an inline comment rather than left for the reader to reconstruct, since "does this loop terminate?" is the first question a reviewer will ask of a `while (true)` inside a security control.
- **`MatchResult.destructured` instead of `groupValues[3]`.** detekt's `MagicNumber` ignores only `-1/0/1/2`, so the existing `groupValues[1]`/`groupValues[2]` idiom does not extend to a third group. Destructuring gives named bindings (`name`, `_`, `typeSuffix`) and reads better than a bare index. Explicitly not a baseline entry (QUAL-07) and not a `MAGIC_GROUP_INDEX` constant.
- **Comment markers follow the destination file.** `/** */` on the public `COOKIE_SECTION_HEADER`, `//` on the two private regexes and on `redactCookieSections` — matching `Redaction.kt`, which today carries exactly one KDoc block (on the public `setCustomPatterns`) and `//` on every field, regex and private function. The plan's prose says "KDoc" throughout; the required *content* is present in full either way, and `21-PATTERNS.md` §"Comment-style split by file" says to match the destination file rather than a global rule.
- **The parameters-section literal stays inline.** Only the cookie-section header is shared, because only the cookie rule keys on it. Stated in a comment at the emitter so the asymmetry does not read as an oversight.
- **The parity test asserts line-exactly** (`blob.lines().contains(...)`), inheriting plan 21-01's decision so a future edit that merges the header onto another line fails rather than passing on a substring match.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] detekt `MagicNumber` on the group-3 index**

- **Found during:** Task 1 (first verification run)
- **Issue:** `./gradlew detekt` failed with `Redaction.kt:405:105: This expression contains a magic number` on `it.groupValues[3]` in the type-suffix replacement. detekt's `MagicNumber` default `ignoreNumbers` is `-1, 0, 1, 2`, so the file's existing `groupValues[1]`/`groupValues[2]` idiom does not extend to a third capture group. The plan's replacement expression is specified in exactly that form.
- **Fix:** Rewrote the replacement as a multi-line lambda using `m.destructured` with named bindings, matching the file's existing `out =` / `out.replace(regex) { m -> ... }` shape at `authHeaderRegex`. A comment names the detekt rule so the next person does not "simplify" it back.
- **Files modified:** `src/main/kotlin/com/six2dez/burp/aiagent/redact/Redaction.kt`
- **Verification:** `./gradlew test ktlintCheck detekt -q` exits 0; `git diff --stat -- detekt-baseline.xml` empty
- **Committed in:** `e642922` (Task 1 commit)

**2. [Rule 2 - Missing critical functionality] Both SC2 tests were vacuous as specified**

- **Found during:** Task 2 (mutation verification), and applied preemptively in Task 3
- **Issue:** The plan specifies `cookieTypedParametersRedacted`'s cookie inputs as `JSESSIONID=…` and `remember_me=…`. Measured against a mutation that unwires the type-suffix rule, **the test still passed**. Both names are reachable by plan 21-04's `SENSITIVE_KEY_EXPR` (`jsessionid` and `remember_me` are both in `KNOWN_SESSION_KEYS`), so `formBodyParamRegex` redacts them from the leading-field position and the assertions cannot see the defect. A test that passes with the rule it guards removed is a false green — the exact failure mode `21-VALIDATION.md` and Phase 20's SC4 discipline exist to prevent, and the same reasoning the plan itself applies to `abtest_bucket` in the D-10 test.
- **Fix:** Added `abtest_bucket=OPAQUE_PARAM_XYZ (COOKIE)` to `RedactionTest.cookieTypedParametersRedacted` and `abtest_bucket` / `OPAQUE_PARAM_XYZ` to `PassiveAiScannerPromptRedactionTest.emittedCookieTypedParametersAreRedacted_sc2`, with a positive `abtest_bucket=[REDACTED] (COOKIE)` assertion and the value added to the negative set. Every line the plan specified is still present and still asserted — this is strictly additive. A comment on each test records that the line was added after measurement and must not be removed.
- **Files modified:** `src/test/kotlin/com/six2dez/burp/aiagent/redact/RedactionTest.kt`, `src/test/kotlin/com/six2dez/burp/aiagent/scanner/PassiveAiScannerPromptRedactionTest.kt`
- **Verification:** re-ran the unwiring mutation — the test now fails with `an unremarkably-named COOKIE-typed parameter is saved by the type suffix alone ==> expected: <true> but was: <false>`, then passes with the rule restored
- **Committed in:** `fd71c4b` (Task 2) and `fec3cf4` (Task 3)

---

**Total deviations:** 2 auto-fixed (1 blocking, 1 missing critical functionality)
**Impact on plan:** Neither changed the plan's design. Deviation 1 is a lint-mechanical rewrite of one replacement expression with no behaviour change. Deviation 2 adds one input line to two tests to make them capable of detecting the defect they exist to guard; no assertion was weakened, no specified input was removed, and no production behaviour changed. No architectural decision arose, no package was installed, and nothing on the plan's do-not-touch list (`Redaction.kt` HKDF block, the size-cap branch, `PassiveAiScannerAnalysis.kt`'s cookie extraction) was modified.

## Issues Encountered

- The worktree spawned at an ancestor commit (`03f17a7`) rather than the assigned base `937c13c`. Corrected with `git reset --hard 937c13c` **after** the branch-namespace assertion passed, per the startup protocol. No work was lost — the reset ran before any edit.
- Mutation testing inside a worktree needs care: `git stash` is forbidden (shared `refs/stash` across worktrees) and `git clean` is destructive here. Each mutation was applied with `Edit` and reverted with `git checkout HEAD -- <single path>`, which is the sanctioned single-file discard. Worth reusing in plan 21-07, which must reinstate the SC4 defect with a surgical mutation for exactly this kind of red-before-green proof.

## Threat Model Verification

| Threat ID | Disposition | Status |
|---|---|---|
| T-21-01 (section-header poisoning, **proven exploitable**) | mitigate | **Satisfied** — `redactCookieSections` iterates every occurrence; `cookieSectionDecoyDoesNotShieldRealSection` is the named guard and was verified FAILING against a first-occurrence-only mutation |
| T-21-16 (cookie values reach the backend two ways) | mitigate | **Satisfied** — section-scoped all-values rule plus type-suffix rule, both gated on `policy.stripCookies`; SC1 asserted per name in both modes, directly and end-to-end; both rules verified load-bearing by mutation |
| T-21-20 (silent control drift on rename or new section) | mitigate | **Satisfied** — `grep -rn '"=== COOKIES ==="' src/main/kotlin/` returns exactly one line, in `redact/Redaction.kt`; the emitter imports the constant; `emittedBlobContainsTheSectionConstant_parity` covers the silent-format-change half; the SC2 rule survives a rename by construction |
| T-21-06 (over-redaction destroys analytic context) | accept | **Satisfied as accepted** — names preserved in both rules; `(URL)` and `(BODY)` survivors asserted byte-for-byte in four places so the parameter rule cannot degrade into a blanket line rule |
| T-21-21 (attacker-injected header causes extra redaction) | accept | **Satisfied as accepted** — documented residual in the `redactCookieSections` comment block and in the D-10 test's comment; the decoy's own value is over-redacted, which is a nuisance, never a leak |
| T-21-SC (package-install supply chain) | accept | **Satisfied** — zero packages installed; no Gradle dependency added |

No new security-relevant surface was introduced: no network endpoint, no auth path, no file access pattern, no schema change at a trust boundary. This plan adds two pure-`String` transformations inside the existing redaction trust boundary and narrows what leaves it.

## For Plan 21-07 (ADR-14 and CONCERNS.md)

- **ADR-14 citations** are all in §"The Shipped Rules" above — both regex literals, the replacement expression, and the three-bound span-termination table.
- **`.planning/codebase/CONCERNS.md` currently cites `Redaction.kt:56-79`** for the "Redaction regex coverage gaps" entry. That range is now stale. Post-plan anchors in `Redaction.kt`: header/token regexes `61-84`, cookie-section rules `83-166`, `SENSITIVE_KEY_EXPR` and its five inputs `172-240`, the three consumer regexes `242-277`, HKDF block (SC6, do not touch) `328-386`, `apply` `387-445`. Plan 21-06 also edits this file, so re-derive rather than copy these numbers verbatim.
- **Header-stage gap unchanged by this plan:** the two cookie rules run in the `stripCookies` stage, which is *outside* the body-stage size cap. That is deliberate — they are not subject to `MAX_REDACTION_BODY_CHARS` and therefore not subject to D-01's fail-open — but it also means they are unbounded, which belongs in ADR-14's Residual bullet alongside the eight header-stage rules.
- **Plural key forms** remain unhandled (recorded by plan 21-04); nothing here changes that.

## Known Stubs

None. Both rules are wired into `apply` and exercised by live `Redaction.apply` calls in six new tests; no placeholder, empty-collection or TODO path was introduced.

## Threat Flags

None.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- **Wave 2 obligations for this plan are complete.** `21-VALIDATION.md`'s four rows owned by 21-05 T2/T3 are satisfied: `cookieSectionValuesRedactedPerName`, `cookieSectionDecoyDoesNotShieldRealSection`, `cookieTypedParametersRedacted` and `emittedCookieSectionValuesAreRedacted_sc1` all execute and pass.
- **Plan 21-06 is unblocked.** It owns the `oversizeBodySkippedSafely` rewrite and the `customPatternRedactsInStrictAndBalanced` OFF-limb inversion; neither was touched here. The cookie rules sit in the `stripCookies` stage, so 21-06's body-stage windowing does not interact with them — but note the ordering: the cookie rules run **before** the body stage, so any window the body stage cuts already contains `NAME=[REDACTED]` rather than a live cookie value.
- **One coupling to carry forward:** `PassiveAiScannerPromptRedactionTest` now has 7 tests and still needs no `MontoyaApi` and no `AgentSettings`. Plan 21-06 adds `offStillAppliesCustomPatterns` to this file — keep the `@BeforeEach` custom-pattern reset, which is what makes the OFF byte-identity assertion meaningful.
- `.planning/STATE.md`, `.planning/ROADMAP.md` and `.planning/REQUIREMENTS.md` were **deliberately not modified** — worktree execution, orchestrator owns those writes. PRIV-05 is a phase-wide checkbox claimed by several plans.

## Verification Results

| Check | Result |
|---|---|
| `./gradlew test -q` | exit 0 — full suite green |
| `./gradlew ktlintCheck detekt -q` | exit 0 |
| `git diff --stat -- detekt-baseline.xml` | empty (QUAL-07 respected) |
| `grep -rn '"=== COOKIES ==="' src/main/kotlin/` | exactly 1 line, `redact/Redaction.kt:94` |
| `git diff 937c13c HEAD -- Redaction.kt \| grep -c 'hkdf\|anonymizeHost\|HOST_MAP_CAP'` | **0** — SC6 boundary honoured |
| `RedactionTest` cases executed | 21 (18 pre-existing + 3 new), 0 failures, 0 errors |
| `PassiveAiScannerPromptRedactionTest` cases executed | 7 (4 from plan 21-01 + 3 new) |

**Task 1 acceptance greps:** `const val COOKIE_SECTION_HEADER = "=== COOKIES ==="` = 1; `"=== COOKIES ==="` in the emitter = 0 and `COOKIE_SECTION_HEADER` in the emitter = 1; `private fun redactCookieSections` = 1; `while` = 6 (≥ 1, with the loop over `indexOf(COOKIE_SECTION_HEADER, from)`); `cookieTypedParamRegex` = 2 (one definition, one use); the `stripCookies` block contains both rules (= 2); `D-10\|every occurrence` = 2.

**Task 2 acceptance greps:** each of the three `fun` definitions = 1; all six cookie names present in `cookieSectionValuesRedactedPerName`; `(URL)` = 5 and `(BODY)` = 3; `REAL_SESSION_SECRET` = 2.

**Task 3 acceptance greps:** both `fun` definitions = 1; `COOKIE_SECTION_HEADER` = 1; `PrivacyMode.BALANCED` = 2; `mock<MontoyaApi>\|AgentSettings(` = **0**.

**Monotonicity canaries — all executed and green:** `strictModeStripsCookiesTokensAndHosts` (`Cookie: [STRIPPED]` unaffected by the new rules), `offModePreservesAllTokens`, `offModePreservesBodies` (byte-identity under OFF), `balancedModeRedactsUrlTokensInQueryStrings` (`name=alice` survives), `bodyFormLeadingFieldRedacted` (`user=bob` survives), `bodyJsonSecretKeysRedacted` (`"name":"alice"` survives), and SC6's named vector `hkdfMatchesRfc5869Vector`.

## Self-Check: PASSED

- `src/main/kotlin/com/six2dez/burp/aiagent/redact/Redaction.kt` — FOUND, contains `COOKIE_SECTION_HEADER`
- `src/main/kotlin/com/six2dez/burp/aiagent/scanner/PassiveAiScannerPrompts.kt` — FOUND, contains `Redaction.COOKIE_SECTION_HEADER`
- `src/test/kotlin/com/six2dez/burp/aiagent/redact/RedactionTest.kt` — FOUND, contains `cookieSectionDecoyDoesNotShieldRealSection`
- `src/test/kotlin/com/six2dez/burp/aiagent/scanner/PassiveAiScannerPromptRedactionTest.kt` — FOUND, contains `emittedCookieSectionValuesAreRedacted_sc1`
- Commit `e642922` — FOUND
- Commit `fd71c4b` — FOUND
- Commit `fec3cf4` — FOUND

---
*Phase: 21-redaction-completeness*
*Completed: 2026-08-11*
