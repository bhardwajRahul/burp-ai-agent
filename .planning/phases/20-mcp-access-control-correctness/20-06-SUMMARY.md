---
phase: 20-mcp-access-control-correctness
plan: 06
subsystem: mcp
tags: [security, access-control, sec-04, sec-05, acceptance-gate, red-before-green, evidence]
requires:
  - "McpAccessControlPlugin + gate install before routing (20-03)"
  - "McpAccessControlPipelineTest / McpAccessControlExternalPipelineTest (20-04)"
  - "KtorMcpServerManagerSecurityTest extensions (20-03)"
provides:
  - "SC4 — the recorded red-before-green transcript proving the Phase 20 regression tests fail against the pre-phase KtorMcpServerManager and pass against the fixed one"
  - "the one-to-one mapping from each failed @Test method to a 20-RESEARCH gate assertion"
  - "the explicit exclusion list: five tests that pass pre-fix by design and are NOT SC4 evidence"
affects:
  - "phase verification (/gsd-verify-work reads this transcript as the SC4 artifact)"
tech-stack:
  added: []
  patterns:
    - "transient rollback by plain file copy to a path OUTSIDE the repo, never git stash (refs/stash is shared across worktrees)"
    - "per-test outcomes read from build/test-results/test/TEST-*.xml, not scraped from console output"
key-files:
  created:
    - .planning/phases/20-mcp-access-control-correctness/20-06-SUMMARY.md
  modified: []
decisions:
  - "The plan's must_haves says THREE assertions pass pre-fix; the measurement is FIVE test methods. The measured five govern the exclusion list; the discrepancy is recorded in Deviations."
  - "The eight genuine SC4 gate assertions (#1-#7, #9) are covered by TWELVE failed @Test methods, not eight — several assertions have more than one covering test. Assertion count and method count are reported separately so neither inflates the other."
  - "The audit-event test failed on its 403 pre-assertion rather than on the event count. The observed failure reason is recorded as measured, not as 20-04 predicted."
metrics:
  duration: ~70 min
  tasks: 1
  commits: 1
  tests-added: 0
  completed: 2026-08-06
---

# Phase 20 Plan 06: SC4 Acceptance Gate Summary

The Phase 20 regression tests were run against the **pre-phase** `KtorMcpServerManager` and against the
fixed one. Pre-fix: **exit 1, 24 tests, 12 failures, all `org.opentest4j.AssertionFailedError`** — no
compile error. Post-fix: **exit 0, 24 tests, 0 failures**. The tree is byte-identical to `HEAD` afterwards.

## SC4 — Red-Before-Green Transcript

| Property | Value |
|----------|-------|
| Rollback baseline SHA | `b1c32704e5c475ae90106cfb17bcad7b0ec3d8d6` (last commit before any Phase 20 source work) |
| Rolled-back file | `src/main/kotlin/com/six2dez/burp/aiagent/mcp/KtorMcpServerManager.kt` (only file touched) |
| Execution base | `5b79cc8e35c98579f397e5d13241a826436adfa9` (Waves 1-3 merged) |
| Rollback diff | `1 file changed, 115 insertions(+), 75 deletions(-)` — the exact inverse of `git diff b1c3270 HEAD` on that file (`75 insertions(+), 115 deletions(-)`) |
| **Step 4 — rolled-back run exit code** | **1 (BUILD FAILED in 46s)** |
| Step 4 result line | `24 tests completed, 12 failed` |
| Compile status under rollback | **COMPILED** — `grep -c 'e: file'` on the log = 0, no `compileKotlin`/`compileTestKotlin` failure. All 12 failures are `org.opentest4j.AssertionFailedError`, so this is per-test assertion evidence, not the weaker compile-failure evidence |
| **Step 7 — restored run exit code** | **0 (BUILD SUCCESSFUL in 42s)** |
| Command (both runs) | `JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test --tests '*McpAccessControlPipelineTest' --tests '*McpAccessControlExternalPipelineTest' --tests '*KtorMcpServerManagerSecurityTest'` |

Per-class counts, read from `build/test-results/test/TEST-*.xml` (not from console output):

| Class | Rolled back (step 4) | Restored (step 7) |
|-------|----------------------|-------------------|
| `McpAccessControlPipelineTest` | `tests="9" failures="6" errors="0"` | `tests="9" failures="0" errors="0"` |
| `McpAccessControlExternalPipelineTest` | `tests="8" failures="5" errors="0"` | `tests="8" failures="0" errors="0"` |
| `KtorMcpServerManagerSecurityTest` | `tests="7" failures="1" errors="0"` | `tests="7" failures="0" errors="0"` |
| **Total** | **24 / 12 failed / 0 errors** | **24 / 0 failed / 0 errors** |

### The 12 methods that failed pre-fix, verbatim from the step-4 log

```
KtorMcpServerManagerSecurityTest > isAuthorized_acceptsOnlyExactBearerToken() FAILED
    org.opentest4j.AssertionFailedError at KtorMcpServerManagerSecurityTest.kt:35
McpAccessControlExternalPipelineTest > invalidBearerForms_areRejected() FAILED
    org.opentest4j.AssertionFailedError at McpAccessControlExternalPipelineTest.kt:171
McpAccessControlExternalPipelineTest > health_withoutAuthorization_returns200WithoutAgentHeader() FAILED
    org.opentest4j.AssertionFailedError at McpAccessControlExternalPipelineTest.kt:136
McpAccessControlExternalPipelineTest > message_withoutAuthorization_returns401() FAILED
    org.opentest4j.AssertionFailedError at McpAccessControlExternalPipelineTest.kt:97
McpAccessControlExternalPipelineTest > sse_withoutAuthorization_returns401() FAILED
    org.opentest4j.AssertionFailedError at McpAccessControlExternalPipelineTest.kt:112
McpAccessControlExternalPipelineTest > blankConfiguredToken_rejectsEveryAuthenticatedPath() FAILED
    org.opentest4j.AssertionFailedError at McpAccessControlExternalPipelineTest.kt:218
McpAccessControlPipelineTest > browserUserAgentWithoutOrigin_isRejectedOnEveryPath() FAILED
    org.opentest4j.AssertionFailedError at McpAccessControlPipelineTest.kt:285
McpAccessControlPipelineTest > health_matchedRouteCarriesAllFourSecurityHeaders() FAILED
    org.opentest4j.AssertionFailedError at McpAccessControlPipelineTest.kt:308
McpAccessControlPipelineTest > foreignReferer_isRejectedOnEveryPath() FAILED
    org.opentest4j.AssertionFailedError at McpAccessControlPipelineTest.kt:285
McpAccessControlPipelineTest > foreignHost_emitsExactlyOneTransportBlockedAuditEvent() FAILED
    org.opentest4j.AssertionFailedError at McpAccessControlPipelineTest.kt:189
McpAccessControlPipelineTest > sse_authorizedConnectCarriesSecurityHeaders() FAILED
    org.opentest4j.AssertionFailedError at McpAccessControlPipelineTest.kt:308
McpAccessControlPipelineTest > foreignHost_isRejectedOnEveryPath() FAILED
    org.opentest4j.AssertionFailedError at McpAccessControlPipelineTest.kt:285
```

### Mapping: failed method → 20-RESEARCH gate assertion → observed pre-fix value

Expected/actual pairs extracted from the step-4 `TEST-*.xml` `<failure>` elements.

| Gate | RESEARCH assertion | Failed `@Test` method | Observed pre-fix (expected → actual) |
|------|--------------------|-----------------------|--------------------------------------|
| **#1** | external `POST /message` no auth → 401 | `McpAccessControlExternalPipelineTest.message_withoutAuthorization_returns401` | `expected: <401> but was: <400>` — *"POST /message without Authorization must be 401. Pre-fix it was 400"* |
| **#1** (reinforcing) | invalid bearer forms → 401 | `McpAccessControlExternalPipelineTest.invalidBearerForms_areRejected` | `expected: <401> but was: <400>` — *"a wrong token must not authenticate"* |
| **#2** | external `GET /sse` no auth → 401 | `McpAccessControlExternalPipelineTest.sse_withoutAuthorization_returns401` | `expected: <401> but was: <200>` — *"Pre-fix it was 200 text/event-stream — a live MCP session for an unauthenticated caller"* |
| **#3** | external `/__mcp/health` carries no `X-Burp-AI-Agent` | `McpAccessControlExternalPipelineTest.health_withoutAuthorization_returns200WithoutAgentHeader` | `expected: <null> but was: <mcp>` — *"D-02: an unauthenticated scan must not be able to confirm that a Burp AI Agent sits behind this port"* |
| **#4** | local `/__mcp/health` 200 carries all four security headers | `McpAccessControlPipelineTest.health_matchedRouteCarriesAllFourSecurityHeaders` | `expected: <DENY> but was: <null>` on `X-Frame-Options` — *"GET /__mcp/health is a MATCHED route; pre-fix it returned 200 with NONE of the four headers"* |
| **#4** (streaming half) | same four headers on the SSE 200 | `McpAccessControlPipelineTest.sse_authorizedConnectCarriesSecurityHeaders` | `expected: <DENY> but was: <null>` on `X-Frame-Options` over cleartext HTTP/1.1 |
| **#5** | local foreign `Host` → 403 on all three paths | `McpAccessControlPipelineTest.foreignHost_isRejectedOnEveryPath` | `expected: <403> but was: <200>` — *"GET /__mcp/health with Host: evil.example must be denied — pre-fix: 200 / 400 / 200 — a DNS-rebinding Host reached every path"* |
| **#6** | local foreign `Referer` → 403 on all three paths | `McpAccessControlPipelineTest.foreignReferer_isRejectedOnEveryPath` | `expected: <403> but was: <200>` — *"pre-fix: 200 / 400 / 200 — isValidReferer was never reached"* |
| **#7** | local browser UA without `Origin` → 403 on all three paths | `McpAccessControlPipelineTest.browserUserAgentWithoutOrigin_isRejectedOnEveryPath` | `expected: <403> but was: <200>` for `User-Agent: Mozilla/5.0 (Macintosh) AppleWebKit/537.36 Chrome/120.0.0.0 Safari/537.36` |
| **#9** | `isAuthorized("Bearer ", "")` → false | `KtorMcpServerManagerSecurityTest.isAuthorized_acceptsOnlyExactBearerToken` (line 35) | pre-fix returned `true` — F19 confirmed |
| **#9** (pipeline level, SC5c) | blank configured token authenticates nothing | `McpAccessControlExternalPipelineTest.blankConfiguredToken_rejectsEveryAuthenticatedPath` | `expected: <401> but was: <400>` — *"SEC-05 5c / F19: a blank configured token must never authenticate"* |
| **D-06..D-10** | one blocked request → exactly one `mcp_transport_blocked` | `McpAccessControlPipelineTest.foreignHost_emitsExactlyOneTransportBlockedAuditEvent` | `expected: <403> but was: <200>` — *"a foreign Host must be denied"* |

**Eight genuine gate assertions — #1, #2, #3, #4, #5, #6, #7, #9 — are each covered by at least one method
that FAILED pre-fix and PASSED post-fix.** They are covered by **twelve** failing methods, not eight,
because #1, #4 and #9 each have two covering tests and the D-06..D-10 end-to-end row adds a twelfth.
Assertion count (8) and method count (12) are reported separately so neither inflates the other.

### Gate assertion #8 — NOT proven by this experiment (disclosed, not counted)

Gate assertion **#8** is `isLoopbackAuthority("[::1]:9876", 9876)` → `true` (RESEARCH measured the pre-fix
`isValidHost("[::1]:9876", 9876)` as `false` by reflection against the shipped class — F12).

This rollback **does not** prove it, and the measurement shows exactly why: the corrected authority parser
lives in `McpAccessControlDecision.kt`, which the rollback does not touch. Its covering test,
`KtorMcpServerManagerSecurityTest.isLoopbackAuthority_acceptsBracketedIpv6Loopback`, **PASSED in the
rolled-back run** — it is one of the 6 green methods in that class's `failures="1"` result. That is direct
measured evidence that #8 is outside this experiment's reach, not an inference.

Where #8 *is* proven:
- `KtorMcpServerManagerSecurityTest.isLoopbackAuthority_acceptsBracketedIpv6Loopback` (added in 20-03) —
  `assertTrue(isLoopbackAuthority("[::1]:9876", 9876))` plus 7 more rows including
  `[0:0:0:0:0:0:0:1]:9876`, the wrong-port negative and the unterminated-bracket negative.
- `McpAccessControlDecisionTest` (added in 20-01) — pure-function coverage of the same predicate.
- Pre-fix behaviour of record: `20-RESEARCH.md` §"Predicate probes", measured `false` by reflection.

**Residual, accepted and disclosed:** SC4's red-before-green property holds for eight of the nine
enumerated assertions. #8 is a predicate-level defect fixed in a file the rollback deliberately leaves
alone; it carries unit-level red-before-green evidence in RESEARCH instead of pipeline-level evidence here.

### Excluded from SC4 credit — the tests that PASS pre-fix by design

All five were confirmed **absent from the step-4 failure list** by subtracting the 12 failures from the 24
`<testcase>` entries in the step-4 XML:

| Test method | Class | Why it passes pre-fix | Confirmed absent from failure list |
|-------------|-------|-----------------------|------------------------------------|
| `foreignOrigin_isRejected` | `McpAccessControlPipelineTest` | Ktor's own CORS plugin already answers 403 (RESEARCH P2) | yes |
| `health_localModeCarriesAgentIdentityHeader` | `McpAccessControlPipelineTest` | pre-fix appended `X-Burp-AI-Agent: mcp` unconditionally at the health route | yes |
| `shutdownWithoutToken_stillReturns401` | `McpAccessControlPipelineTest` | in-handler token check, unchanged (SC6 non-regression) | yes |
| `shutdownWithoutToken_stillReturns401` | `McpAccessControlExternalPipelineTest` | same in-handler check over TLS (SC6) | yes |
| `message_withValidBearer_reachesHandlerAndCarriesSecurityHeaders` | `McpAccessControlExternalPipelineTest` | headers were already present over TLS/HTTP-2 pre-fix; its value is guarding P3 | yes |

Two further methods also passed pre-fix and are likewise **not** counted:
`McpAccessControlExternalPipelineTest.blankConfiguredToken_stillAllowsHealth` (a D-01-before-5c
branch-order pin — health is deliberately exempt, so 200 both before and after) and
`KtorMcpServerManagerSecurityTest.isLoopbackAuthority_acceptsBracketedIpv6Loopback` (the #8 case above).
Neither is a regression of intent; both are recorded here so the pass/fail arithmetic closes:
**24 tests = 12 SC4-credited failures + 12 by-design passes.**

`GET /nope` → 401 in external mode is the third non-gate named in RESEARCH; no plan asserts it, so there
was nothing to exclude for it.

### Proof of no trace

| Check | Result |
|-------|--------|
| `shasum -a 256` of the manager, before rollback | `2c1424e08fb8c5c5ffa007bb9085f55c15be995a99923c04f0856fa145b55a43` |
| same, after restore | `2c1424e08fb8c5c5ffa007bb9085f55c15be995a99923c04f0856fa145b55a43` — identical |
| `git status --porcelain -- src/` after restore | empty |
| `git status --porcelain` (whole tree, after the full gate) | **empty** |
| `git diff --stat HEAD` | **empty** |
| `git diff --stat -- detekt-baseline.xml` | empty (QUAL-07 holds) |
| `git log --oneline -1 -- .../KtorMcpServerManager.kt` | `d7c8feb fix(20-03): install the gate before routing and wire the blocked reporter (SEC-04, SEC-05)` — the Plan 03 commit; **the rollback was never committed** |
| `git stash list` | empty — no stash was created or consulted at any point |
| Saved-aside copies | `KtorMcpServerManager.fixed.kt` and `KtorMcpServerManager.prefix.kt`, both under the session scratchpad **outside the repository**; never inside `src/` |

### Rollback validity checks performed before the red run

The plan asserts the pre-phase manager is self-contained enough to compile beside the new Wave-1/2/3
files. Verified before running, rather than assumed:

- `grep -c 'McpAccessControl'` on the pre-phase copy → **0**: it installs no gate, so `McpAccessControlPlugin.kt`
  becomes an uncalled top-level `val` and still compiles.
- The pre-phase health route (line 155-158) appends `X-Burp-AI-Agent` **unconditionally** — the D-02
  `if (!settingsSnapshot.externalEnabled)` guard is genuinely absent, which is what makes
  `health_withoutAuthorization_returns200WithoutAgentHeader` go red. Confirmed by reading the rolled-back
  copy, per the 20-04 warning that reverting `McpAccessControlPlugin.kt` alone would not reproduce it.
- The pre-phase `intercept(ApplicationCallPipeline.Call)` block sits at line 176, **after** `routing {}` at
  line 154 — the F1 ordering bug, present as expected.
- `build.gradle.kts` has no `allWarningsAsErrors`, so the now-unused new files cannot fail the build on
  warnings.
- Stale `build/test-results/test/` was deleted before each run so no XML could be read from a prior run.

## Deviations from Plan

### Interpretation Calls (not defects)

**1. The plan says "the three assertions that pass pre-fix"; the measurement is FIVE test methods (seven
counting the two additional by-design passes).** The plan's `must_haves` and its `<action>` block inherit
the count from `20-RESEARCH.md`, which enumerates three *non-gate behaviours* (foreign `Origin` → 403,
`GET /nope` → 401 external, `/__mcp/shutdown` 401/200). `20-04-SUMMARY.md` §"Notes for Plan 06" then
measured the *test-method* population and named **five**: `foreignOrigin_isRejected`,
`health_localModeCarriesAgentIdentityHeader`, `shutdownWithoutToken_stillReturns401` **in both pipeline
classes** (two methods, not one), and `message_withValidBearer_reachesHandlerAndCarriesSecurityHeaders`.
The step-4 measurement confirms all five passed, and surfaced two more that also pass by design
(`blankConfiguredToken_stillAllowsHealth`, `isLoopbackAuthority_acceptsBracketedIpv6Loopback`). The
measured population governs the exclusion table above; the plan's "three" refers to behaviours and is not
wrong so much as counting a different thing. Recorded rather than silently reconciled, because SC4's whole
value is that its credit list is honest.

**2. Similarly, "the eight genuine gate rows in bold" is a count of gate *assertions*, not of rows.**
`20-04-SUMMARY.md`'s two tables carry bold in **eleven** rows; adding the security-test row gives the
twelve failures observed. The eight is the count of RESEARCH assertions #1-#7 and #9. Both numbers are
reported separately above so a verifier can check either without one masquerading as the other.

**3. The audit-event test failed for a different reason than 20-04 predicted.** 20-04 expected
`foreignHost_emitsExactlyOneTransportBlockedAuditEvent` to go red on the event count (0 instead of 1,
because pre-fix code emits no `mcp_transport_blocked` at all). It actually failed earlier, on its
`expected: <403> but was: <200>` pre-assertion — the request was never blocked, so the count assertion was
never reached. Same conclusion, different line; recorded as measured.

**4. `cp` is shell-aliased to `cp -i` in this environment.** The first overwrite attempt returned exit 1
with `not overwritten`, which would have silently produced a green "red run" against the unmodified tree.
Switched to `/bin/cp -f` and verified the rollback landed via `git diff --stat -- src/` showing the exact
inverse hunk counts before trusting any test result. Flagged because a prompt-driven `cp` in this repo will
fail the same way for anyone repeating the experiment.

**5. The final full gate took 45m 3s, not the ~90s recorded by earlier plans.** Those measurements were of
scoped or warm-cache runs; `test detekt ktlintCheck` with no `--tests` filter runs the whole heavy suite.
Exit code 0. No test was skipped, weakened or filtered to shorten it.

### Auto-fixed Issues

None. This plan produced no source change; the rollback was transient and fully reverted.

### Authentication Gates

None.

## Verification

| Check | Result |
|-------|--------|
| Step 4 (rolled back) exit code | **1** — BUILD FAILED in 46s, `24 tests completed, 12 failed` |
| Step 4 failures are assertions, not compile errors | **yes** — 12 × `org.opentest4j.AssertionFailedError`, `grep -c 'e: file'` = 0 |
| Step 7 (restored) exit code | **0** — BUILD SUCCESSFUL in 42s, 24 tests, 0 failures |
| External `POST /message` → 401 in failure list | yes — `message_withoutAuthorization_returns401` |
| External `GET /sse` → 401 in failure list | yes — `sse_withoutAuthorization_returns401` |
| Local foreign `Host` / `Referer` / browser-UA / security-header methods in failure list | yes — all four |
| `KtorMcpServerManagerSecurityTest.isAuthorized_acceptsOnlyExactBearerToken` in failure list | yes (line 35) |
| Foreign-`Origin` test in failure list | **no** (correctly excluded) |
| Either `/__mcp/shutdown` test in failure list | **no** (correctly excluded, both classes) |
| Gate assertion #8 disclosed as unproven here, with a pointer | yes — §"Gate assertion #8" |
| `JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test detekt ktlintCheck` | **BUILD SUCCESSFUL in 45m 3s** (exit 0) |
| `git status --porcelain` | empty |
| `git diff --stat HEAD` | empty |
| `grep -c 'git stash'` in this file | 0 |

All Gradle invocations carried the `JAVA_HOME=$(/usr/libexec/java_home -v 21)` prefix. Per-test outcomes
were read from `build/test-results/test/TEST-*.xml`; the console log was used only for the ordered FAILED
list quoted verbatim above.

## Threat Model Follow-through

| Threat ID | Disposition | Status |
|-----------|-------------|--------|
| T-20-16 (tampering, evidence) | mitigate | **implemented** — both exit codes, both per-class XML count triples, all 12 failed method names with file:line, and the expected→actual pair for every one. Nothing here is asserted without a recorded value behind it |
| T-20-20 (repudiation, inflated gate) | mitigate | **implemented** — five by-design passes named and confirmed absent from the failure list; two further by-design passes surfaced and also excluded; assertion count (8) and method count (12) reported separately; #8 disclosed as unproven here |
| T-20-21 (integrity, working tree) | mitigate | **implemented** — identical SHA-256 before and after, `git status --porcelain` empty, `git diff --stat HEAD` empty, manager's last commit still `d7c8feb`, `git stash list` empty, saved copies kept outside the repository |
| T-20-22 (denial of correctness, compile failure mistaken for proof) | mitigate | **implemented** — the rolled-back tree COMPILED; the three rollback-validity checks above were run before the test run, and the failure taxonomy (`AssertionFailedError` × 12, zero compile diagnostics) is recorded so a verifier can tell the two apart |

**Residual, accepted:** gate assertion #8, disclosed above rather than papered over.

## Known Stubs

None. This plan produced no code. Every number in this document came from a recorded command's output or
from a `TEST-*.xml` element.

## Threat Flags

None. No source change, no new endpoint, no trust-boundary change, no schema change.

## Self-Check

- `.planning/phases/20-mcp-access-control-correctness/20-06-SUMMARY.md` — FOUND
- `src/main/kotlin/com/six2dez/burp/aiagent/mcp/KtorMcpServerManager.kt` — FOUND, unmodified (`git diff --stat HEAD` empty)
- STATE.md / ROADMAP.md — deliberately NOT modified; the orchestrator owns those writes

## Self-Check: PASSED
