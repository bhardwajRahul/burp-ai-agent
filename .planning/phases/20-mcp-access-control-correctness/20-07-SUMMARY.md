---
phase: 20-mcp-access-control-correctness
plan: 07
subsystem: security
tags: [mcp, access-control, dns-rebinding, fail-closed, authority-parsing, detekt, kotlin]

# Dependency graph
requires:
  - phase: 20-mcp-access-control-correctness
    provides: "The extracted pure decision core (McpAccessControlDecision.kt) and its unit suite, delivered by plans 20-01..20-06"
provides:
  - "evaluateLocal denies Deny(403, host_mismatch) when a local-mode request carries no authority at all — the decision-core half of verification gap 1"
  - "parseAuthority keeps three outcomes distinct (no port / usable port / malformed), closing WR-01 where a numeric out-of-range port silently disabled the port comparison"
  - "A permanent non-vacuity contract on the D-11 Origin/Referer drift guard: at least one authority must be ALLOWED on both sides"
affects: [20-08 h2 authority transport half, future MCP transport work, any change inserting a branch ahead of the Referer check]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "MagicNumber-safe numeric limits: top-level `private const val` instead of an inline literal, because QUAL-07 forbids growing detekt-baseline.xml"
    - "ReturnCount-bounded parsing: a three-way outcome expressed as a conditional inside an existing `let` rather than a third early exit"
    - "Non-vacuity assertions: a loop-level allow-vs-deny comparison is paired with a post-loop count assertion so it cannot silently degrade into comparing two denials"

key-files:
  created: []
  modified:
    - src/main/kotlin/com/six2dez/burp/aiagent/mcp/McpAccessControlDecision.kt
    - src/test/kotlin/com/six2dez/burp/aiagent/mcp/McpAccessControlDecisionTest.kt
    - src/test/kotlin/com/six2dez/burp/aiagent/mcp/KtorMcpServerManagerSecurityTest.kt

key-decisions:
  - "An absent request authority DENIES in local mode (maintainer-locked): the DNS-rebinding limb now runs on every local-mode request instead of only those that happen to carry a parseable Host"
  - "A port that is numeric but outside 1..65535 makes the WHOLE authority malformed, rather than being treated as 'no port was present'"
  - "BlockReason.HOST_MISMATCH is reused for the absent-authority denial; no new enum constant was added, so plan 20-09's concurrent BlockReason branching cannot break at merge"
  - "The D-11 drift guard's non-vacuity is enforced by a permanent assertion, not recorded as a one-time observation"

patterns-established:
  - "Fail-closed gate branches carry a do-NOT-restore comment naming the maintainer decision, so a future reader cannot mistake the strictness for an accident"
  - "A test whose greenness depends on branch precedence carries an explicit comment saying the absent field is deliberate"

requirements-completed: [SEC-04, SEC-05]

# Metrics
duration: 21min
completed: 2026-08-10
---

# Phase 20 Plan 07: MCP Access-Control Fail-Closed Decision Core Summary

**A local-mode MCP request carrying no `Host` / `:authority` is now denied 403 `host_mismatch` instead of allowed, and an authority whose port is numeric but outside `1..65535` is rejected outright instead of having its port assertion silently skipped.**

## Performance

- **Duration:** 21 min
- **Started:** 2026-08-10T09:31:00Z
- **Completed:** 2026-08-10T09:52:36Z
- **Tasks:** 2 (both TDD — 4 commits)
- **Files modified:** 3

## Accomplishments

- Closed the decision-core half of 20-VERIFICATION gap 1: `evaluateLocal`'s DNS-rebinding limb ran only when `facts.host` happened to be non-null, so the very requests it exists to stop were the ones that bypassed it. It now calls `isLoopbackAuthority(facts.host.orEmpty(), settings.port)` unconditionally and denies on false.
- Closed 20-REVIEW WR-01: `parseAuthority` mapped an `Int`-overflowing port to "no port was present", so `isLoopbackAuthority("localhost:99999999999", 9876)` returned **true** while `isLoopbackAuthority("localhost:65536", 9876)` returned **false**. Both are now false, and an authority with genuinely no port still yields a null port and still passes.
- Repaired all SIX tests that depended on the old fail-open — including the one that would have stayed GREEN while going vacuous.
- Made the D-11 Origin/Referer drift guard's non-vacuity a permanent contract rather than a one-time observation.
- Branch precedence is provably unchanged: a foreign `Origin` still reports `origin_mismatch` and a browser UA with no `Origin` still reports `browser_no_origin`, both asserted with **no** authority present.

## Task Commits

1. **Task 1 (RED): pin a numeric port outside the usable range** — `6feaca7` (test)
2. **Task 1 (GREEN): MAX_TCP_PORT + three-outcome parseAuthority** — `246787b` (fix)
3. **Task 2 (RED): pin the absent-authority local-mode denial** — `5cd2981` (test)
4. **Task 2 (GREEN): fail-closed authority branch + six repaired tests** — `aff89b9` (fix)

No REFACTOR commit was needed on either task; both GREEN implementations landed in their final shape.

## Test Counts

`McpAccessControlDecisionTest`: **38 before → 40 after** (read from `build/test-results/test/TEST-com.six2dez.burp.aiagent.mcp.McpAccessControlDecisionTest.xml`, `tests="40" failures="0"`).

- +1 `isLoopbackAuthority_rejectsAPortThatIsNumericButOutOfRange`
- +2 replacing the single deleted `evaluate_localAllGatedHeadersAbsentIsAllowed`: `evaluate_localMatchingLoopbackAuthorityAloneIsAllowed` and `evaluate_localAbsentAuthorityIsForbiddenOnEveryPath` (net +1)

Both RED gates were observed failing for the right reason before implementation, read from the XML rather than console output:

- RED 1: `39 tests completed, 1 failed` — only `isLoopbackAuthority_rejectsAPortThatIsNumericButOutOfRange`, `expected: <false> but was: <true>` (the WR-01 fail-open, reproduced).
- RED 2: `tests="40" failures="1"` — only `evaluate_localAbsentAuthorityIsForbiddenOnEveryPath`.

`KtorMcpServerManagerSecurityTest`: 7 tests, unchanged count, 0 failures.

## The Six Repaired Tests

| # | Test | File | Edit applied |
|---|------|------|--------------|
| 1 | `evaluate_localLoopbackOriginWithBrowserUserAgentIsAllowed` | `McpAccessControlDecisionTest.kt` | Added `host = "127.0.0.1:$MCP_PORT"`. Its DELIBERATE-NARROWING comment was kept verbatim — that comment guards a separate 20-01 behaviour change. |
| 2 | `evaluate_localForeignRefererIsForbidden` | `McpAccessControlDecisionTest.kt` | Added `host = "127.0.0.1:$MCP_PORT"` plus a comment saying why it is load-bearing: without it the authority branch fires first and the test would measure `HOST_MISMATCH`, destroying the Referer coverage. |
| 3 | `evaluate_localLoopbackRefererIsAllowed` | `McpAccessControlDecisionTest.kt` | Added `host = "127.0.0.1:$MCP_PORT"`. |
| 4 | `evaluate_localAllGatedHeadersAbsentIsAllowed` | `McpAccessControlDecisionTest.kt` | **Deleted** — its premise is inverted by this change. Replaced by `evaluate_localMatchingLoopbackAuthorityAloneIsAllowed` (the ALLOW half) and `evaluate_localAbsentAuthorityIsForbiddenOnEveryPath` (the new denial, on `/message` and on `HEALTH_PATH` per D-03), the latter carrying a FAIL-OPEN-CLOSED-by-20-07 comment so nobody restores the old permissiveness. |
| 5 | `evaluate_localNeverInspectsAuthorization` | `McpAccessControlDecisionTest.kt` | Added `host = "127.0.0.1:$MCP_PORT"` so it still measures what its name says rather than tripping the authority branch. |
| 6 | `evaluate_treatsOriginAndRefererIdentically` | `KtorMcpServerManagerSecurityTest.kt` | Added `host = "127.0.0.1:9876"` to both `RequestFacts`, **and** added a permanent post-loop non-vacuity assertion. Loop values, KDoc, and the existing assertion message were left untouched. |

Case 6 is the dangerous one and the reason this plan was revised twice. It would **not** have failed. With no `host`, every one of its six iterations would deny after this change — the `referer` variants via the new `HOST_MISMATCH` branch, which sits *before* the Referer branch, so the Referer predicate would never have been consulted at all. The assertion `assertEquals(asOrigin is Allow, asReferer is Allow)` would then have compared `false` to `false` and stayed green while measuring nothing, silently killing the only above-predicate assertion guarding locked decision D-11. With the loopback `Host` restored, the outcome is genuinely mixed again: `http://[::1]:9876`, `http://localhost:9876` and `http://127.0.0.1` are Allow/Allow, while `http://evil.example`, `not-a-uri` and `""` are Deny/Deny via `ORIGIN_MISMATCH` / `REFERER_MISMATCH` — so `bothAllowedCount` is 3.

Two tests were deliberately **not** edited: `evaluate_localForeignOriginIsForbidden` and `evaluate_localBrowserUserAgentWithoutOriginIsForbidden`. Their branches precede the authority branch, so their greenness with an absent authority *is* the proof that branch precedence survived. Each now carries a one-line comment saying the absent authority is deliberate.

## Files Created/Modified

- `src/main/kotlin/com/six2dez/burp/aiagent/mcp/McpAccessControlDecision.kt` — added top-level `private const val MAX_TCP_PORT = 65_535`; `parseAuthority` now returns null for a port outside `1..MAX_TCP_PORT` (whole authority malformed) while an absent port still yields a null port; `evaluateLocal`'s authority branch is unconditional and fail-closed. KDoc on `parseAuthority` rewritten to state all three outcomes and name WR-01, keeping the note that the function holds the file's whole `ReturnCount` budget.
- `src/test/kotlin/com/six2dez/burp/aiagent/mcp/McpAccessControlDecisionTest.kt` — 38 → 40 tests; out-of-range port pins, absent-authority denial pins, four repaired ALLOW cases, two deliberate-absence comments.
- `src/test/kotlin/com/six2dez/burp/aiagent/mcp/KtorMcpServerManagerSecurityTest.kt` — D-11 drift guard repaired and given a permanent non-vacuity assertion.

## Verification

Isolation gate, run in this worktree:

- `JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test detekt ktlintCheck` → **BUILD SUCCESSFUL**. `grep -l "<failure\|<error " build/test-results/test/*.xml` returns nothing across the whole suite.
- The four real-server suites named in the plan's DONE criteria are green, which is the evidence that no legitimate client regressed (all real clients send `Host`): `McpAccessControlPipelineTest` 9/0, `McpAccessControlExternalPipelineTest` 8/0, `McpServerIntegrationTest` 1/0, `McpSupervisorProbeTest` 4/0.
- `git diff --stat detekt-baseline.xml` → empty. QUAL-07 honoured; no baseline entry was added for `MagicNumber` or anything else.
- `parseAuthority` contains exactly two `return` statements (read, not grepped whole-file: the `BARE_IPV6_LOOPBACKS` early exit and the final `match?.let { … }`).
- `65_535` appears exactly once in `McpAccessControlDecision.kt`, in the `MAX_TCP_PORT` declaration.
- `git status --porcelain` after the task commits listed nothing — only the plan's three files were touched, and nothing outside them.

The merged-tree gate (including `test -PstoreBuild=true`) is deliberately **not** run here; the orchestrator owns it after all four gap-closure plans merge.

## Decisions Made

- Reused `BlockReason.HOST_MISMATCH` for the absent-authority denial instead of introducing a new constant, so plan 20-09's concurrent `BlockReason` branching cannot break at merge. The wire value `host_mismatch` is also honest: the request's authority did not match the bound loopback socket, because there was none.
- Expressed the three parse outcomes as a `when` inside the existing `match?.let { … }` rather than as an early exit, because `ReturnCount` runs at its default max of 2 and `parseAuthority` already spends both.
- Put the `65_535` limit in a top-level `private const val` rather than inline. `MagicNumber` is active on main source, `detekt.yml` does not override it, its `ignoreNumbers` default covers only `-1, 0, 1, 2`, and there is no baseline entry for this file — an inline literal would have failed `detekt`, and the baseline escape is forbidden. `MagicNumber.ignoreConstantDeclaration` defaults to `true`, so the declaration itself is exempt. `1..MAX_TCP_PORT` is safe because `1` is in the default ignore list.
- Combined the `/message` and `HEALTH_PATH` absent-authority assertions into one test rather than two, since D-03's contract is precisely that the two paths are treated identically.

## Deviations from Plan

None — plan executed exactly as written. No deviation rule was triggered: no bug outside the two the plan targets, no missing critical functionality, nothing blocking, no architectural change.

## Issues Encountered

None. The worktree was created at an older commit than the expected base (`b9ee87a`), as the plan's prompt anticipated; `git reset --hard` to the expected base corrected it, and the presence of `McpAccessControlDecision.kt`, `McpAccessControlPlugin.kt` and `KtorMcpServerManagerSecurityTest.kt` confirmed plans 20-01..20-06 were already merged in before any file was touched.

## Known Stubs

None. No placeholder value, empty collection, or TODO was introduced; both changes are complete logic with assertions.

## Threat Flags

None. This plan adds no network endpoint, no auth path, no file access pattern, and no schema change. It only makes an existing trust-boundary check stricter. Every threat in the plan's register is either mitigated (T-20-07-01, T-20-07-02, T-20-07-03) or an accepted, evidenced risk (T-20-07-04, covered by the four real-server suites; T-20-07-SC, no dependency touched — `build.gradle.kts` was not modified and no package manager was invoked).

## Residual, Deliberately Not Closed Here

Over HTTP/2 an **absent** `:authority` is not closable at this layer: `Http2LocalConnectionPoint` coalesces an absent `:authority` into the local socket, so post-coalesce it is byte-identical to a legitimate authority equal to the bound socket. The truths this plan pins are therefore scoped to HTTP/1.1. Plan 20-08 owns the h2 transport half and carries that case as a documented residual. No attempt was made to close it here.

## User Setup Required

None — no external service configuration required.

## Next Phase Readiness

- The decision core is now genuinely fail-closed on the authority, matching what its own KDoc always claimed, and both defects (gap 1 decision half, WR-01) are pinned by unit assertions that fail against the pre-fix code.
- Plan 20-08 can wire the HTTP/2 `:authority` fallback into `McpAccessControlPlugin.kt` on top of this: with an absent authority now denying, its h2 work no longer has a fail-open underneath it.
- Downstream consumers of `BlockReason` (plan 20-09) are unaffected — the enum is byte-identical, still six constants.

---
*Phase: 20-mcp-access-control-correctness*
*Completed: 2026-08-10*
