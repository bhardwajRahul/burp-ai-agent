---
phase: 26-coverage-static-analysis-debt-docs
plan: 03
subsystem: testing
tags: [ssrf, ipv6, mcp, hmac, entropy, redaction, jacoco, detekt, kotlin]

# Dependency graph
requires:
  - phase: 25-secondary-hardening
    provides: "SsrfGuard's SEC-07 IPv4 rewiring, the CountingInetAddressResolverProvider zero-resolution proof, and the McpTakeoverProof HMAC whose message is fully attacker-known (WR-01)"
provides:
  - "Notation-independent IPv6 classification: an IPv4-mapped literal is classified identically to its hex spelling, in both directions"
  - "Defaults.MCP_MIN_TOKEN_LENGTH and McpSettings.isTokenWeak — a pure, AWT-free entropy floor for the MCP bearer token"
  - "An advisory weak-token RISK item in the MCP Server tab, ungated by external mode"
  - "config at 97.08% line / 91.97% branch and redact at 97.95% line / 93.30% branch"
affects: [26-06, adr-16, mcp-hardening-docs, ssrf-guard, redaction]

actuals:
  tokens: 13428    # chars/4 over the realized diff (53 712 chars). Whole-file scale: 31 384.
  tasks: 3
  commits: 5

tech-stack:
  added: []
  patterns:
    - "A floor constant and the value it must never exceed are asserted as a RELATION, not as two independent numbers"
    - "An accepted residual is pinned as a passing assertion that says 'still leaks', so widening it goes red"

key-files:
  created:
    - src/test/kotlin/com/six2dez/burp/aiagent/config/McpTokenStrengthTest.kt
    - src/test/kotlin/com/six2dez/burp/aiagent/redact/RedactionPolicyTest.kt
  modified:
    - src/main/kotlin/com/six2dez/burp/aiagent/util/SsrfGuard.kt
    - src/main/kotlin/com/six2dez/burp/aiagent/config/Defaults.kt
    - src/main/kotlin/com/six2dez/burp/aiagent/config/McpSettings.kt
    - src/main/kotlin/com/six2dez/burp/aiagent/ui/SettingsPanelMcpTabs.kt
    - src/test/kotlin/com/six2dez/burp/aiagent/util/SsrfGuardTest.kt
    - src/test/kotlin/com/six2dez/burp/aiagent/util/SsrfGuardNoResolutionTest.kt
    - src/test/kotlin/com/six2dez/burp/aiagent/config/McpSettingsTest.kt
    - src/test/kotlin/com/six2dez/burp/aiagent/config/SecretCipherTest.kt
    - src/test/kotlin/com/six2dez/burp/aiagent/redact/SafeRegexTest.kt
    - src/test/kotlin/com/six2dez/burp/aiagent/redact/EntropyTest.kt

key-decisions:
  - "The weak-token notice is NOT gated on external mode — the bind-conflict takeover path runs locally too, which is the mode WR-01 is actually about"
  - "refreshMcpNotice's accumulator was extracted to mcpNoticeItems rather than baselined, because the new bullet pushed it to cyclomatic complexity 15"
  - "The cookie-section under-redaction residual is pinned as a passing assertion that asserts the leak, so widening the bound turns it red"

patterns-established:
  - "Relation assertions for paired constants: MCP_MIN_TOKEN_LENGTH <= generateToken().length is asserted directly, so raising one alone fails"
  - "Corpus floors: SsrfGuardNoResolutionTest asserts MIN_CORPUS_SIZE, so a zero-lookup gate cannot be made green by deleting inputs"

requirements-completed: []  # QUAL-06 / QUAL-07 are declared by all seven plans in this phase; the shared-ID gate blocks them until the last declaring plan produces a SUMMARY.

coverage:
  - id: D1
    description: "SsrfGuard classifies IPv4-mapped IPv6 literals identically to their hex spelling, in both directions, and keeps loopback excluded under the mapped spelling"
    requirement: "QUAL-06"
    verification:
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/util/SsrfGuardTest.kt#ipv4MappedIpv6_cloudMetadata_isFlagged_andAgreesWithHexSpelling"
        status: pass
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/util/SsrfGuardTest.kt#ipv4MappedIpv6_rfc1918_isFlagged_andAgreesWithHexSpelling"
        status: pass
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/util/SsrfGuardTest.kt#ipv4MappedIpv6_loopback_isNotFlagged"
        status: pass
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/util/SsrfGuardTest.kt#ipv4MappedIpv6_unbracketed_isFlagged"
        status: pass
    human_judgment: false
  - id: D2
    description: "The widened literal gate performs zero name resolution across an enlarged 21-URL corpus (Phase 25 SC4 preserved)"
    requirement: "QUAL-06"
    verification:
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/util/SsrfGuardNoResolutionTest.kt#classifyingEveryNotationResolvesNothing"
        status: pass
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/util/SsrfGuardNoResolutionTest.kt#theCountingResolverIsActuallyInstalled"
        status: pass
      - kind: other
        ref: "grep -c 'getByName' src/main/kotlin/com/six2dez/burp/aiagent/util/SsrfGuard.kt == 1"
        status: pass
    human_judgment: false
  - id: D3
    description: "McpSettings.isTokenWeak is a pure advisory predicate that never fires against generateToken()'s own 43-character output"
    requirement: "QUAL-06"
    verification:
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/config/McpTokenStrengthTest.kt#generatedTokenIsNeverWeak"
        status: pass
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/config/McpTokenStrengthTest.kt#theFloorNeverExceedsTheGeneratedTokenLength"
        status: pass
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/config/McpTokenStrengthTest.kt#tokenAtAndAboveTheFloor_isNotWeak"
        status: pass
    human_judgment: false
  - id: D4
    description: "The MCP Server tab shows an advisory RISK bullet when MCP is on with a non-blank but weak token"
    requirement: "QUAL-06"
    verification:
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/config/McpTokenStrengthTest.kt#theNoticeConsumesThePredicateAndRaisesARiskItem"
        status: pass
    human_judgment: true
    rationale: "SettingsPanel cannot be constructed under -Djava.awt.headless=true, so the wiring is asserted structurally by reading the declaring source. Whether the rendered bullet reads clearly to an operator, and whether it points at the right control, is a visual judgment no assertion makes."
  - id: D5
    description: "config package at or above 96.2% line and 91.0% branch"
    requirement: "QUAL-07"
    verification:
      - kind: other
        ref: "build/reports/jacoco/test/jacocoTestReport.xml — config LINE 1031/1062 = 97.08%, BRANCH 561/610 = 91.97%"
        status: pass
    human_judgment: false
  - id: D6
    description: "redact package at or above 97.5% line and 93.0% branch"
    requirement: "QUAL-07"
    verification:
      - kind: other
        ref: "build/reports/jacoco/test/jacocoTestReport.xml — redact LINE 430/439 = 97.95%, BRANCH 181/194 = 93.30%"
        status: pass
    human_judgment: false
  - id: D7
    description: "No new detekt baseline entry and no new Gradle dependency"
    requirement: "QUAL-07"
    verification:
      - kind: other
        ref: "./gradlew detekt ktlintCheck green; git diff --quiet detekt-baseline.xml exits 0; git diff --stat build.gradle.kts empty"
        status: pass
    human_judgment: false

duration: 32 min
completed: 2026-08-22
status: complete
---

# Phase 26 Plan 03: SsrfGuard IPv6 Notation, MCP Token Floor, redact/config Coverage Summary

**IPv4-mapped IPv6 literals now classify identically to their hex spelling with still-zero name resolution, an advisory MCP token-entropy floor makes ADR-16's forthcoming seventh residual a real mitigation, and `redact` / `config` sit at 97.95% / 97.08% line coverage.**

## Performance

- **Duration:** 32 min
- **Started:** 2026-08-22T12:51:19Z
- **Completed:** 2026-08-22T13:23:46Z
- **Tasks:** 3
- **Files modified:** 12 (2 created, 10 modified)
- **Suite:** 937 tests / 129 classes / 0 failures / 1 pre-existing skip (was 880 / 127 / 0 / 1 pre-phase)

## Accomplishments

- **W-2 / 25-REVIEW WR-02 closed.** `IPV6_REGEX` admits the ASCII full stop, so `http://[::ffff:169.254.169.254]/` is now flagged instead of silently classified as public. Each mapped form is asserted to AGREE with its hex spelling rather than against a hardcoded `true`, so the property under test is the notation-independence the object KDoc claims. Loopback stays excluded under the mapped spelling (D-01).
- **Phase 25's SC4 preserved and re-proved over a larger corpus.** The `host.contains(':')` conjunct — the actual resolver gate — is byte-for-byte unchanged. `SsrfGuardNoResolutionTest`'s corpus grew from 14 to 21 URLs, including the five mapped forms that now reach the one resolving call for the first time plus two colon-and-dot strings that match the widened class but are not valid literals, and the JVM-wide counter still reports 0 lookups. A `MIN_CORPUS_SIZE` floor was added so the gate cannot be made green by deleting inputs.
- **W-1b / 25-REVIEW WR-01 closed.** `Defaults.MCP_MIN_TOKEN_LENGTH = 32` and `McpSettings.isTokenWeak` — pure, AWT-free, advisory. The MCP Server tab raises a RISK bullet when MCP is on with a non-blank but weak token, in every mode, because the bind-conflict takeover path runs locally too.
- **Coverage floors cleared in both packages**, closing the reachable dark branches in `McpSettings.Companion`, `SecretCipher.decrypt`, `RedactionPolicy`, `SafeRegex.isPatternSafe` and `Entropy` — the last of which is now at zero missed branches.

## Coverage measurements

Measured with `JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test jacocoTestReport`, whole suite, no filter. The pre-phase column was re-measured in this worktree at base `767776d` and matched the planner's `4f0ebd7` figures exactly.

| scope | line pre-phase | line after | floor | branch pre-phase | branch after | floor |
|---|---|---|---|---|---|---|
| `config` | 1014/1061 (95.57%) | **1031/1062 (97.08%)** | 96.2% | 541/608 (88.98%) | **561/610 (91.97%)** | 91.0% |
| `redact` | 422/439 (96.13%) | **430/439 (97.95%)** | 97.5% | 174/194 (89.69%) | **181/194 (93.30%)** | 93.0% |

## Red probes (recorded with observed output)

**1. SsrfGuard mapped-form probe.** The new `SsrfGuardTest` cases run against the unmodified `SsrfGuard.kt`:

```
SsrfGuardTest > ipv4MappedIpv6_cloudMetadata_isFlagged_andAgreesWithHexSpelling() FAILED
SsrfGuardTest > ipv4MappedIpv6_rfc1918_isFlagged_andAgreesWithHexSpelling() FAILED
SsrfGuardTest > ipv4MappedIpv6_expandedForm_isFlagged() FAILED
SsrfGuardTest > ipv4MappedIpv6_unbracketed_isFlagged() FAILED
25 tests completed, 4 failed

IPv4-mapped cloud-metadata literal must be flagged ==> expected: <true> but was: <false>
IPv4-mapped RFC-1918 literal must be flagged     ==> expected: <true> but was: <false>
```

Observed verdict `false` for every mapped form, exactly as 25-REVIEW WR-02 measured against the compiled class. Restored with the working-tree edit reapplied — `git stash` was not used at any point (`refs/stash` is shared across linked worktrees and three sibling executors were live).

**2. Token-floor relation probe.** `MCP_MIN_TOKEN_LENGTH` temporarily raised from 32 to 44:

```
McpTokenStrengthTest > theFloorNeverExceedsTheGeneratedTokenLength() FAILED
McpTokenStrengthTest > generatedTokenIsNeverWeak() FAILED
7 tests completed, 2 failed

MCP_MIN_TOKEN_LENGTH is 44 but generateToken() yields 43 characters. The floor must never exceed
the product's own default. ==> expected: <true> but was: <false>

generateToken() sample #0 produced '43' characters, which the floor of 44 classifies as weak.
The advisory must never fire against the value the product itself generates.
==> expected: <false> but was: <true>
```

The relation assertion is falsifiable, so the prohibition against raising the floor above the generated-token length is machine-checked rather than a sentence in a plan. Value restored to 32 by hand and re-run green.

## W-1b outcome

Wording for plan 26-06 to copy verbatim into ADR-16's seventh `Residual:` bullet:

> - Residual: **offline token guessing from a captured proof.** The bind-conflict takeover proof is `HMAC-SHA256(key = token, message = "burp-ai-agent/mcp-takeover|v1|<host>:<port>|<window>")`, and every byte of that message is known to a squatting local process — the port it holds, the host string the client dialled, and a 10-second window index. A captured proof is therefore an offline verifier for the token itself: one HMAC-SHA256 per guess, with no victim interaction, no rate limit and no lockout. Infeasible against `McpSettings.generateToken()`'s 32 random bytes (43 Base64URL characters); a short operator-typed token is recoverable in seconds. Mitigated by `Defaults.MCP_MIN_TOKEN_LENGTH` (32) and `McpSettings.isTokenWeak`, surfaced as a RISK item in the MCP Server tab's advisory whenever MCP is enabled with a non-blank but weak token — in local mode as well as external, because the takeover path runs in both. The control is **advisory only**: it does not block saving, does not refuse to start the server, and never rewrites the operator's token.

## Task Commits

1. **Task 1 (tracer, TDD): close the IPv4-mapped IPv6 notation gap** — `447feb9` (test, RED), `4fed7ce` (fix, GREEN)
2. **Task 2 (TDD): MCP token-strength floor and the config coverage floors** — `7f8db6b` (feat), `8e6018f` (test)
3. **Task 3 (TDD): close the reachable dark branches in the redact package** — `5ebb311` (test)

_Task 1's tracer feedback gate was run after `4fed7ce`: the tracer's own `<verify>` (`test --tests '*SsrfGuard*' --tests '*Ipv4LiteralTest' detekt ktlintCheck`) was re-run end-to-end and passed before any expansion task started._

## Files Created/Modified

- `src/main/kotlin/com/six2dez/burp/aiagent/util/SsrfGuard.kt` — `IPV6_REGEX` admits `.`; object KDoc now states the mapped-notation coverage and names why the widening is safe
- `src/main/kotlin/com/six2dez/burp/aiagent/config/Defaults.kt` — `MCP_MIN_TOKEN_LENGTH = 32`
- `src/main/kotlin/com/six2dez/burp/aiagent/config/McpSettings.kt` — `isTokenWeak`, pure and AWT-free
- `src/main/kotlin/com/six2dez/burp/aiagent/ui/SettingsPanelMcpTabs.kt` — the weak-token RISK bullet; the accumulator extracted to `mcpNoticeItems`
- `src/test/kotlin/com/six2dez/burp/aiagent/util/SsrfGuardTest.kt` — 8 mapped-notation and regression cases
- `src/test/kotlin/com/six2dez/burp/aiagent/util/SsrfGuardNoResolutionTest.kt` — corpus 14 → 21 URLs plus a `MIN_CORPUS_SIZE` floor
- `src/test/kotlin/com/six2dez/burp/aiagent/config/McpTokenStrengthTest.kt` — new; the predicate plus the structural wiring assertion
- `src/test/kotlin/com/six2dez/burp/aiagent/config/McpSettingsTest.kt` — every `parseToolToggles` payload shape, the unsafe-tool set, the allowed-origin filters
- `src/test/kotlin/com/six2dez/burp/aiagent/config/SecretCipherTest.kt` — empty envelope, undecodable payload, two encrypts on one instance
- `src/test/kotlin/com/six2dez/burp/aiagent/redact/RedactionPolicyTest.kt` — new; `RedactionPolicy`, `anonymizeHost`, `SecretShapes`, the cookie-section bound
- `src/test/kotlin/com/six2dez/burp/aiagent/redact/SafeRegexTest.kt` — the uncompilable arm, a bounded literal, a group-referencing replacement
- `src/test/kotlin/com/six2dez/burp/aiagent/redact/EntropyTest.kt` — every negative arm of the charset × threshold gate

## Decisions Made

- **The weak-token bullet is not gated on `external`.** WR-01's suggested snippet showed `mcpOn && !tokenBlank && tokenWeak`, and the plan was explicit that gating on external mode would hide the notice in the only mode most operators ever use. The bind-conflict takeover path runs locally.
- **`refreshMcpNotice` was refactored, not baselined.** The new bullet took the function to cyclomatic complexity 15, exactly the project's ceiling. QUAL-07 forbids growing `detekt-baseline.xml`, so the item accumulation was extracted to a file-private `SettingsPanel.mcpNoticeItems()` along the natural WHAT/HOW seam. Every caveat is still accumulated rather than short-circuited — the property the accumulator replaced a `when` chain to obtain.
- **The structural assertion follows the extracted seam.** It now asserts both links: `mcpNoticeItems` contains the `isTokenWeak` call and a `RISK` item, AND `refreshMcpNotice` consumes `mcpNoticeItems()`. Either link alone can rot silently.
- **The cookie-section under-redaction bound is pinned as a leak.** `MAX_COOKIE_SECTION_LINES = 16` is an accepted residual whose measurement was recorded only in a comment. The new test asserts both halves — everything inside the bound is redacted, and `ck16` is *not* — so widening the bound turns it red and forces a deliberate update.
- **`MIN_CORPUS_SIZE` added to the zero-resolution gate.** Deleting awkward inputs makes "zero lookups" trivially true; the floor closes that.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] `refreshMcpNotice` exceeded the cyclomatic-complexity ceiling**

- **Found during:** Task 2 (MCP token-strength floor)
- **Issue:** Adding the weak-token bullet took `refreshMcpNotice` to cyclomatic complexity 15 against a threshold of 15, failing `./gradlew detekt`. Baselining it was prohibited (QUAL-07 shrinks `detekt-baseline.xml`; adding an entry inverts the phase goal).
- **Fix:** Extracted the item accumulation to a file-private `SettingsPanel.mcpNoticeItems(): List<McpNoticeItem>` and lifted the local `Item` data class to a file-private `McpNoticeItem`. `refreshMcpNotice` keeps the hide/severity/render logic only.
- **Files modified:** `src/main/kotlin/com/six2dez/burp/aiagent/ui/SettingsPanelMcpTabs.kt`, `src/test/kotlin/com/six2dez/burp/aiagent/config/McpTokenStrengthTest.kt` (structural assertion follows the seam)
- **Verification:** `./gradlew detekt ktlintCheck` green; `git diff --quiet detekt-baseline.xml` exits 0
- **Committed in:** `7f8db6b`

**2. [Rule 3 - Blocking] ktlint spacing violation on the new `Defaults` constant**

- **Found during:** Task 2
- **Issue:** `standard:spacing-between-declarations-with-comments` — the new commented `MCP_MIN_TOKEN_LENGTH` needed a blank line separating it from the preceding declaration.
- **Fix:** Blank line inserted.
- **Files modified:** `src/main/kotlin/com/six2dez/burp/aiagent/config/Defaults.kt`
- **Verification:** `./gradlew ktlintCheck` green
- **Committed in:** `7f8db6b`

**3. [Rule 2 - Missing Critical] The redact branch floor needed two branches the plan did not enumerate**

- **Found during:** Task 3
- **Issue:** After the enumerated work, `redact` branch coverage stood at 179/194 (92.27%) against a 93.0% floor. The plan's remaining candidates (`splitPoint`, `windowEnd`, `pairMayBeInFlightAt`, `safeCutPoint`, `isSafeCutTerminator`, `dropOrRetry`, `windowedScan`, `endsInsideOpenQuotedValue`) are all private members of the >1 MB windowing path, reachable only through `RedactionTest`, which this plan is prohibited from touching.
- **Fix:** Two additional in-scope, genuinely meaningful assertions were added instead of reaching private members by reflection: the `Entropy` dot-joined pass's base64URL blind spot (a `-` or `_` disqualifies the whole candidate), and `cookieSectionEnd`'s malformed-input arms driven through the public `Redaction.apply`.
- **Files modified:** `src/test/kotlin/com/six2dez/burp/aiagent/redact/EntropyTest.kt`, `src/test/kotlin/com/six2dez/burp/aiagent/redact/RedactionPolicyTest.kt`
- **Verification:** `redact` branch 181/194 = 93.30%, floor cleared
- **Committed in:** `5ebb311`

---

**Total deviations:** 3 auto-fixed (2 blocking, 1 missing critical)
**Impact on plan:** No scope creep. Two were static-analysis gates the plan's own prohibitions required fixing rather than baselining; the third closed a floor gap the plan's branch enumeration under-counted. All twelve files touched are exactly the twelve in `files_modified`.

## Unreachable branches, named rather than reached

The plan asked for these to be recorded with reasons instead of reached by reflection or by widening a private symbol.

| Symbol | Missed branches | Why it is unreachable from outside the package |
|---|---|---|
| `Redaction.splitPoint` | 3 | Private; only called from the windowing loop, which `Redaction.apply` enters at bodies above `Defaults.MAX_REDACTION_BODY_CHARS` (1 MB). A 1 MB fixture belongs to `RedactionTest`, which this plan may not touch. |
| `Redaction.windowEnd` | 2 | Same windowing loop. |
| `Redaction.pairMayBeInFlightAt` | 2 | Same windowing loop. |
| `Redaction.windowedScan` / `dropOrRetry` / `safeCutPoint` / `isSafeCutTerminator` / `endsInsideOpenQuotedValue` | 1 each | Same windowing loop. |
| `Redaction.maybeLogTruncation` | 1 | The uncovered arm is the *lost* `compareAndSet` — it requires two threads racing inside the same 10-second limiter window. Not deterministically reachable without a concurrency harness, and this plan forbids wall-clock and sleep-based assertions. |
| `SafeRegex.DeadlineCharSequence.subSequence` | 1 line | JDK 21's `Matcher.appendExpandedReplacement` appends group text directly from the backing `CharSequence` with start/end indices (`result.append(text, start, end)`) rather than via `group()`, so `subSequence` is never called on the wrapper through any `SafeRegex` entry point. Measured: a `$1`-referencing replacement leaves the line uncovered. |
| `McpSettings.serializeToolToggles` catch | covered | Reached via a null map key, which Jackson refuses — asserted as the documented `{}` fail-soft outcome. |

`redact` and `config` both clear their floors with these left dark.

## Issues Encountered

None. The suite ran green on every full invocation despite four executors sharing the machine; the recorded `RedactionTest` wall-clock flake did not fire, and `RedactionTest.kt` was never opened or modified (`git diff --name-only | grep -c 'RedactionTest.kt'` returns 0 across the whole plan diff).

## Known Stubs

None. Every test added asserts on the result of the production code it executes; no placeholder, TODO, or hardcoded empty value was introduced.

## Threat Flags

None. No new network endpoint, auth path, file-access pattern or schema change was introduced. `SsrfGuard`'s change narrows what is classified as public (T-26-03-01/02/03), and the MCP change is a read-only advisory (T-26-03-04/05).

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- **Plan 26-06 can now write ADR-16's seventh residual as a real mitigation.** Copy the `## W-1b outcome` block above verbatim; `isTokenWeak` and `MCP_MIN_TOKEN_LENGTH` exist and are tested. If 26-06 raises `MIN_ADR16_RESIDUALS` in `DecisionsAdrTest`, it must do so in the same commit as the bullet.
- **`REQUIREMENTS.md` was deliberately not modified.** QUAL-06 and QUAL-07 are declared by all seven plans in this phase, so the shared-ID gate (#2388) blocks marking them complete until the last declaring plan produces its SUMMARY. The orchestrator handles this after the wave merges.
- **`STATE.md` and `ROADMAP.md` were not touched**, per the parallel-execution contract.
- No blockers.

---
*Phase: 26-coverage-static-analysis-debt-docs*
*Completed: 2026-08-22*
