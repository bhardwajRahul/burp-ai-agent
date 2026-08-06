---
phase: 20-mcp-access-control-correctness
plan: 03
subsystem: mcp
tags: [security, access-control, ktor-plugin, sec-04, sec-05, observability, ipv6, bearer-auth]
requires:
  - "McpAccessControlDecision.evaluate / GateDecision / RequestFacts (20-01)"
  - "McpBlockedRequestReporter.report (20-01)"
  - "BuildFlags.VERSION (20-02)"
provides:
  - "McpAccessControl — Plugins-phase Ktor ApplicationPlugin enforcing the MCP access-control gate"
  - "McpAccessControlConfig — settings snapshot + mandatory onBlocked hook"
  - "live onBlocked wiring: every Deny reaches McpBlockedRequestReporter.report at runtime"
  - "mode-aware /__mcp/health (D-02)"
  - "KtorMcpServerManager predicates delegating to the corrected decision core"
affects:
  - "20-04 (McpAccessControlPipelineTest asserts the gate + the mcp_transport_blocked audit event end-to-end)"
  - "20-05 (docs/mcp-hardening.md + McpSupervisor probe change read the D-02 behaviour recorded here)"
tech-stack:
  added: []
  patterns:
    - "createApplicationPlugin + onCall as an order-independent pre-routing security seam"
    - "lateinit config hook instead of a defaulted no-op lambda, so an unwired observability sink is a startup crash"
    - "file-level @file:Suppress instead of a detekt-baseline.xml entry (QUAL-07)"
key-files:
  created:
    - src/main/kotlin/com/six2dez/burp/aiagent/mcp/McpAccessControlPlugin.kt
  modified:
    - src/main/kotlin/com/six2dez/burp/aiagent/mcp/KtorMcpServerManager.kt
    - src/test/kotlin/com/six2dez/burp/aiagent/mcp/KtorMcpServerManagerSecurityTest.kt
decisions:
  - "Blank external token LOGS via logToError and does not throw: the gate already denies every non-health request 401 BLANK_TOKEN, so failing closed is guaranteed and letting the port bind keeps the misconfiguration diagnosable"
  - "A local `settingsSnapshot` alias is required: a function parameter shadows an implicit-receiver member of the same name, so `settings = settings` inside install(McpAccessControl){} cannot compile"
  - "McpBlockedRequestReporter constructed with a named argument, not the trailing-lambda form: its LAST constructor parameter is verboseAudit"
  - "Task 3's three prescribed predicate names do not exist (dropped by 20-01); tests were remapped onto isLoopbackAuthority / isLoopbackUrlAuthority and the Origin-vs-Referer drift guard moved up to evaluate()"
  - "@file:Suppress(\"MatchingDeclarationName\") on McpAccessControlPlugin.kt rather than a new detekt-baseline.xml entry"
metrics:
  duration: ~40 min
  tasks: 3
  commits: 3
  tests-added: 4
  completed: 2026-08-06
---

# Phase 20 Plan 03: Plugins-Phase Access-Control Gate & Manager Rewiring Summary

Moved the MCP access-control checks out of the dead post-`routing{}` `Call`-phase interceptor into a
`Plugins`-phase `ApplicationPlugin`, and wired the Wave 1 blocked-request reporter so a denial actually
produces an audit event and an Output line.

## What Was Built

`McpAccessControlPlugin.kt` is a thin adapter: it lifts seven request facts out of the
`ApplicationCall`, calls `evaluate`, and on `Deny` calls `onBlocked(decision)` and then
`call.respond(decision.status)`. All security logic stays in `McpAccessControlDecision.kt`; all
observability stays behind `onBlocked`. The file contains no logging, no audit emission, no lock and no
blocking I/O — it runs on Netty event-loop threads.

`KtorMcpServerManager.start()` now installs that plugin between `install(CORS)` and `routing {}`, and
the dead `intercept(ApplicationCallPipeline.Call)` block is gone, replaced by a do-not-restore comment
naming SEC-04 / F1.

## Line Numbers Plan 04 and Plan 05 Need

Post-edit `src/main/kotlin/com/six2dez/burp/aiagent/mcp/KtorMcpServerManager.kt`:

| Site | Lines |
|------|-------|
| `import com.six2dez.burp.aiagent.BuildFlags` | 4 |
| blank-external-token diagnostic (`logToError`) | 90-100 |
| `Implementation("burp-ai-agent", BuildFlags.VERSION)` | 110 |
| `val reporter = McpBlockedRequestReporter(...)` | 125 |
| `val settingsSnapshot = settings` | 129 |
| `install(CORS) {` | 154 |
| `install(McpAccessControl) {` | 187 |
| `onBlocked = { deny -> ... }` | 191-193 |
| `routing {` | 196 |
| `get("/__mcp/health")` handler | 197-206 |
| `post("/__mcp/shutdown")` handler (unchanged) | 207-221 |
| deleted-interceptor marker comment | 224-231 |
| `mcp {` | 233 |

Install order therefore holds: `install(CORS)` (154) < `install(McpAccessControl)` (187) <
`routing {` (196) < `mcp {` (233).

## The Reporter Wiring, Verbatim

Constructed once per `start()` invocation, immediately before `applicationEnvironment { }`
(line 125):

```kotlin
val reporter = McpBlockedRequestReporter(logToOutput = { line -> api.logging().logToOutput(line) })
```

Wired at line 187-194:

```kotlin
install(McpAccessControl) {
    // Explicit `this.` because the enclosing start() parameter named
    // `settings` shadows the config property of the same name.
    this.settings = settingsSnapshot
    onBlocked = { deny ->
        reporter.report(deny, settingsSnapshot.externalEnabled, System.currentTimeMillis())
    }
}
```

One instance per server instance, never per request — the D-09 per-reason windows live in instance
state, so a per-request reporter would print every block and suppress nothing. `verboseAudit` is left
at its `false` default, so reflected header values are hashed (D-10).

## Blank External Token: LOG, Not Throw

`start()` lines 90-100 add a **non-throwing** diagnostic after the two existing fail-closed
`IllegalStateException` guards:

```kotlin
if (settings.externalEnabled && settings.token.isBlank()) {
    api.logging().logToError(
        "MCP external access is enabled with a blank bearer token — every request will be rejected " +
            "with 401. Generate a token in Settings.",
    )
}
```

Rationale, as instructed by the plan and recorded here for the verifier: `evaluate` already denies every
non-health request `401 BlockReason.BLANK_TOKEN` in this configuration, so failing closed is already
guaranteed. Throwing would turn a misconfiguration into a start failure that surfaces only as a generic
`Failed` state in the UI; letting the port bind keeps the state diagnosable and keeps `/__mcp/health`
answering for liveness (D-01).

## Behaviour Changes Landed

| Change | Before | After |
|--------|--------|-------|
| Access-control checks vs. resolved routes (`/sse`, `/message`, `/__mcp/health`) | not applied — `RoutingRoot` served the route first (F1) | applied in the `Plugins` phase, before routing resolves |
| Four security headers on matched-route 200s and the SSE 200 | protocol-dependent (present over TLS/HTTP-2, absent over cleartext HTTP/1.1) | appended pre-routing, deterministic |
| Blocked-request observability | one unstructured `logToOutput` line, no audit event | D-06 `mcp_transport_blocked` event + D-07 sanitized, D-09 rate-limited Output line, D-10 hashed header values |
| `X-Burp-AI-Agent: mcp` on `/__mcp/health` | always | local mode only (D-02) |
| Advertised MCP server version | `"0.6.0"` literal | `BuildFlags.VERSION` (`0.9.2`) |
| `isAuthorized("Bearer ", "")` | `true` | `false` (delegates to `isAuthorizedBearer`) |
| `isLoopbackHost` / `Host` with bracketed IPv6 | `[::1]:9876` rejected | accepted |

`/__mcp/shutdown`'s handler body is byte-identical to before (D-04). It still calls
`isAuthorized(authHeader, settings.token)`; only that private method's *body* changed, so the handler
inherits the blank-token guard for free. No `SHUTDOWN_PATH` constant was introduced.

Also inherited from 20-01 and deliberately NOT re-tightened: in local mode a request with a
present-and-valid loopback `Origin` plus a browser `User-Agent` was 403 pre-fix and is Allow now. The
old `else if` was not reproduced; the deleted-interceptor comment and the decision core both say so.

## Predicate Surface After This Plan

Removed from `KtorMcpServerManager`: `constantTimeEquals`, `isValidOrigin`, `isBrowserRequest`,
`isValidHost`, `isValidReferer`, plus the `java.security.MessageDigest` and
`io.ktor.server.application.ApplicationCallPipeline` imports.

Kept, now one-liners delegating to the decision core: `isAuthorized` → `isAuthorizedBearer`,
`isLoopbackHost` → `isLoopbackAuthority(host, null)`. Untouched: `parseExternalCorsHosts`,
`normalizeOrigin`, `CorsAllowedHost`.

Stale `detekt-baseline.xml` entries for the deleted `isValidHost` `ReturnCount` finding were left in
place — a stale entry is inert, and QUAL-07 requires the baseline to stay byte-identical.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] `constantTimeEquals` reflection test retargeted inside Task 2's commit**
- **Found during:** Task 2 verification
- **Issue:** Task 2 deletes the manager's private `constantTimeEquals`, which
  `KtorMcpServerManagerSecurityTest` reached by reflection. Task 2's own acceptance gate
  (`test --tests 'com.six2dez.burp.aiagent.mcp.*'` must exit 0) is therefore unsatisfiable while that
  test still exists — the run produced exactly one failure, `NoSuchMethodException` at line 54. The
  plan assigns that rewrite to Task 3.
- **Fix:** Performed the plan's own prescribed rewrite (call the `internal` `constantTimeCompare`
  directly, rename to `constantTimeCompare_handlesEqualAndDifferentLengths`, same three assertions) as
  part of Task 2 rather than Task 3, so Task 2 commits green. Task 3 then adds only new assertions.
- **Files modified:** `src/test/kotlin/com/six2dez/burp/aiagent/mcp/KtorMcpServerManagerSecurityTest.kt`
- **Commit:** `d7c8feb`

**2. [Rule 3 - Blocking] `McpBlockedRequestReporter` trailing-lambda form does not compile**
- **Found during:** Task 2, at `compileKotlin`
- **Issue:** The plan's acceptance criteria explicitly warn NOT to grep for `McpBlockedRequestReporter(`
  because "Kotlin's trailing-lambda form `McpBlockedRequestReporter { line -> … }` is idiomatic here".
  It is not valid here: the constructor's **last** parameter is `verboseAudit: Boolean`, not the lambda,
  so the trailing-lambda form binds the lambda to `verboseAudit` and fails with
  `No value passed for parameter 'logToOutput'`.
- **Fix:** `McpBlockedRequestReporter(logToOutput = { line -> api.logging().logToOutput(line) })`, with
  a comment recording why the trailing-lambda form is wrong. The `>= 1` grep criterion still passes.
- **Files modified:** `src/main/kotlin/com/six2dez/burp/aiagent/mcp/KtorMcpServerManager.kt`
- **Commit:** `d7c8feb`

**3. [Rule 3 - Blocking] `settings = settingsSnapshot` needed an explicit `this.`**
- **Found during:** Task 2, at `compileKotlin` (`'val' cannot be reassigned`)
- **Issue:** Inside `install(McpAccessControl) { … }` the bare name `settings` resolves to `start()`'s
  own `settings` parameter, because a local declaration shadows an implicit-receiver member of the same
  name. The plan's `settings = <the settings snapshot>` shape therefore attempted to reassign a `val`.
- **Fix:** `this.settings = settingsSnapshot`, with the shadowing reason in a comment. The `onBlocked`
  assignment needs no qualifier (no local of that name exists), so the `grep -c 'onBlocked = '` = 1
  criterion is unaffected.
- **Files modified:** `src/main/kotlin/com/six2dez/burp/aiagent/mcp/KtorMcpServerManager.kt`
- **Commit:** `d7c8feb`

**4. [Rule 3 - Blocking] detekt `MatchingDeclarationName` on the new plugin file**
- **Found during:** the plan-level gate
- **Issue:** `McpAccessControlPlugin.kt:37` — the file's only top-level *class* is
  `McpAccessControlConfig` (the plugin itself is a `val`), which detekt reads as a filename mismatch.
  Renaming the file is forbidden (`files_modified` pins the name) and QUAL-07 forbids a new baseline
  entry.
- **Fix:** File-level `@file:Suppress("MatchingDeclarationName")` above the `package` line, with a
  comment stating that the file's primary export is the `McpAccessControl` plugin val and that a
  baseline entry was deliberately not added.
- **Files modified:** `src/main/kotlin/com/six2dez/burp/aiagent/mcp/McpAccessControlPlugin.kt`
- **Commit:** `a2a78cb`

### Interpretation Calls (not defects)

**5. Task 3's three prescribed predicate names do not exist — tests remapped.**
Plan 20-01 deliberately dropped `isValidHostAuthority`, `isValidOriginValue` and `isValidRefererValue`
(detekt `TooManyFunctions` counts top-level functions per file, `thresholdInFiles: 11`; keeping the
aliases needed a baseline entry). Every input/expectation row the plan lists was preserved against the
real API:

| Plan test | Landed as | Predicate actually called |
|-----------|-----------|---------------------------|
| `isValidHostAuthority_acceptsBracketedIpv6Loopback` | `isLoopbackAuthority_acceptsBracketedIpv6Loopback` | `isLoopbackAuthority` — all 8 rows kept |
| `isValidOriginValue_acceptsBracketedIpv6Loopback` | `isLoopbackUrlAuthority_acceptsBracketedIpv6Loopback` | `isLoopbackUrlAuthority` — all 6 rows kept |
| `isValidRefererValue_matchesOriginBehaviour` | `evaluate_treatsOriginAndRefererIdentically` | `evaluate` — see below |
| `isAuthorizedBearer_rejectsBlankConfiguredToken` | unchanged | `isAuthorizedBearer` — all 5 rows kept |

The Origin-vs-Referer agreement test is vacuous at the predicate level now that both checks share
`isLoopbackUrlAuthority`. D-11's intent ("they cannot drift again") is preserved one level up: the same
authority is fed to `evaluate` once as `Origin` and once as `Referer` across the plan's six inputs, and
the two `GateDecision`s must agree on allow-vs-deny. The test was NOT dropped and no alias was invented.

Consequences for two acceptance criteria, which are unsatisfiable as literally written and were
resolved in favour of the real API:
- "an assertion that `isValidHostAuthority("[::1]:9876", 9876)` is true" → landed as
  `assertTrue(isLoopbackAuthority("[::1]:9876", 9876))` (SC4 gate assertion #8, same behaviour).
- "assertions that `isValidOriginValue("http://[::1]:9876")` and `isValidRefererValue("http://[::1]:9876")`
  are both true" → landed as `assertTrue(isLoopbackUrlAuthority("http://[::1]:9876"))` plus the
  `evaluate`-level agreement test.

**6. `build.gradle.kts` exclusion-glob line numbers.** The plan cites `build.gradle.kts:145-157`; the
`tasks.test` exclusion block is actually at lines 151-165. The class KDoc names both the actual range
and the plan's citation, and lists all five globs.

**7. Comment wording adjusted so two zero-count greps measure code, not prose.** The Task 1 criteria
require `grep -c 'finish()'` = 0 and `grep -c 'onCallRespond'` = 0 on a file whose `<action>` block also
mandates comments explaining why neither is used. The explanatory comments were reworded to describe
those mechanisms without naming the identifiers ("the pipeline is NOT explicitly finished here", "the
respond-side plugin hook … attaches to `ApplicationSendPipeline.Transform`"). No behaviour change; both
greps return 0 and both facts remain documented at the site.

**8. Plan `key_links` regex `McpBlockedRequestReporter\\\\(` is over-escaped and cannot match.** Not
worked around. The wiring was verified by direct grep (`reporter.report(` at line 192) and by the
`onBlocked = ` criterion; Plan 04 asserts it end-to-end.

### Authentication Gates

None.

## Verification

| Check | Result |
|-------|--------|
| `./gradlew compileKotlin` (Task 1) | BUILD SUCCESSFUL |
| `./gradlew test --tests 'com.six2dez.burp.aiagent.mcp.*'` (Task 2) | BUILD SUCCESSFUL — 170 tests, 0 failures, incl. `McpServerIntegrationTest` shutdown 401/200 (SC6 not regressed) |
| `./gradlew test --tests '*KtorMcpServerManagerSecurityTest'` | BUILD SUCCESSFUL — 7 tests, 0 failures |
| same with `-PexcludeHeavyTests=true` | BUILD SUCCESSFUL — **7 tests**, identical count (class escapes the exclusion globs) |
| `./gradlew test detekt ktlintCheck` (plan-level gate, once) | BUILD SUCCESSFUL in 48s |
| `git diff --stat detekt-baseline.xml` | empty (QUAL-07) |
| `./gradlew shadowJar` | BUILD SUCCESSFUL |
| `grep -c 'onBlocked(decision)'` in the plugin | 1 |
| `grep -c 'lateinit var onBlocked'` in the plugin | 1 |
| `grep -c 'finish()'` / `'onCallRespond'` / `'logToOutput\|AuditLogger\|synchronized\|Thread('` in the plugin | 0 / 0 / 0 |
| `grep -c 'reporter.report('` in the manager | 1 (line 192) |
| `grep -c '"0.6.0"'` / `'ApplicationCallPipeline'` / `'intercept('` / `'MessageDigest'` in the manager | 0 / 0 / 0 / 0 |
| `grep -c 'Implementation("burp-ai-agent", BuildFlags.VERSION)'` | 1 |
| `grep -c 'X-Burp-AI-Agent'` (inside `if (!settingsSnapshot.externalEnabled)`) | 1 |
| `grep -c 'fun isValidHost\|fun isValidOrigin\|fun isValidReferer\|fun isBrowserRequest\|fun constantTimeEquals'` | 0 |
| `grep -c 'fun isAuthorized\|fun isLoopbackHost\|fun parseExternalCorsHosts\|fun normalizeOrigin'` | 4 |
| `grep -c 'getDeclaredMethod'` in the test class | 2 (pre-existing helpers only; no new reflection) |

All Gradle invocations carried the `JAVA_HOME=$(/usr/libexec/java_home -v 21)` prefix.

## Threat Model Follow-through

| Threat ID | Disposition | Status |
|-----------|-------------|--------|
| T-20-01 (EoP, gate placement) | mitigate | **implemented** — `onCall` attaches to the `Plugins` phase; the `Call`-phase interceptor is deleted |
| T-20-02 (spoofing, local branch reachable) | mitigate | **implemented** — the gate now runs on `/__mcp/health`, `/sse` and `/message`, not only unmatched paths |
| T-20-03 (tampering, headers) | mitigate | **implemented** — appended pre-routing, deduplicated |
| T-20-04 (EoP, blank token) | mitigate | **implemented** — `isAuthorized` delegates to `isAuthorizedBearer`; the shutdown handler inherits it |
| T-20-05 (info disclosure, health headers) | mitigate | **implemented** — D-02 header gated on `!externalEnabled` |
| T-20-06 (repudiation, `onBlocked` wiring) | mitigate | **implemented** — `lateinit` hook, real `reporter.report` call at line 192, grep-verified |
| T-20-08 / T-20-09 / T-20-10 / T-20-SC | accept / transfer | unchanged by this plan; no dependency added, no CORS behaviour change |

## Known Stubs

None. `onBlocked` is a real call to `reporter.report`, not an empty lambda; every symbol introduced has
a live call site.

## Threat Flags

None. No new network endpoint, no new file access, no schema change — the plan only relocates and
correctly enforces checks on requests the existing MCP listener already accepted.

## Notes for Plan 04

- The gate is installed at line 187; `evaluate` is reached through `McpAccessControl`, so a pipeline
  test only needs to start a real server via `McpTestServerSupport` and assert status codes.
- `reporter.report(deny, settingsSnapshot.externalEnabled, System.currentTimeMillis())` is the only
  audit path; a single blocked request must produce exactly one `mcp_transport_blocked` event.
- `verboseAudit` is `false` in production wiring, so `origin`/`host`/`referer`/`userAgent` in that event
  are SHA-256 hex, while `method` and `path` are sanitized plaintext.
- In external mode `/__mcp/health` returns 200 **without** `X-Burp-AI-Agent`; Plan 05 owns the
  `McpSupervisor.probeExistingServer` consequence.

## Self-Check

- `src/main/kotlin/com/six2dez/burp/aiagent/mcp/McpAccessControlPlugin.kt` — FOUND
- `src/main/kotlin/com/six2dez/burp/aiagent/mcp/KtorMcpServerManager.kt` — FOUND
- `src/test/kotlin/com/six2dez/burp/aiagent/mcp/KtorMcpServerManagerSecurityTest.kt` — FOUND
- commit `ae5b828` (Task 1) — FOUND
- commit `d7c8feb` (Task 2) — FOUND
- commit `a2a78cb` (Task 3) — FOUND

## Self-Check: PASSED
