---
phase: 20-mcp-access-control-correctness
plan: 05
subsystem: mcp
tags: [security, sec-04, takeover, bind-conflict, docs, d-02, d-05, d-12]
requires:
  - "mode-aware /__mcp/health — X-Burp-AI-Agent local-only (20-03)"
  - "HEALTH_PATH constant + external-mode health exemption in evaluate (20-01)"
  - "McpTestServerSupport — freePort / deepStubApi / localSettings / externalTlsSettings / startAndAwaitRunning / trustAllClient / baseUrl (20-02)"
provides:
  - "mode-aware McpSupervisor.probeExistingServer: identity header required in local mode, 2xx liveness accepted in external mode"
  - "McpSupervisorProbeTest — first-ever coverage of the real probeExistingServer against a live KtorMcpServerManager"
  - "corrected docs/mcp-hardening.md External Access + Verification checklists"
affects:
  - "Phase 25 / SEC-07 (takeover identity rework) — the probe is now explicitly documented as liveness-only in external mode"
  - "Phase 26 / DOC-03 (security advisory) — the runbook items listed below are already done"
tech-stack:
  added: []
  patterns:
    - "mode-mirroring: a client-side assertion is gated on the same settings flag as the server-side emission it depends on"
    - "reflection into a private McpSupervisor method against a REAL server, following McpSupervisorConnectionTest"
    - "anti-tautology pairing: the probe assertion is backed by an independent OkHttp assertion that the header really is absent"
key-files:
  created:
    - src/test/kotlin/com/six2dez/burp/aiagent/mcp/McpSupervisorProbeTest.kt
  modified:
    - src/main/kotlin/com/six2dez/burp/aiagent/mcp/McpSupervisor.kt
    - docs/mcp-hardening.md
decisions:
  - "The mode split is expressed as `alive && (settings.externalEnabled || header == \"mcp\")` rather than a nested if/else, keeping nesting depth at 3 and the expression-body form of probeExistingServer intact"
  - "The identity-not-established Output line omits the literal header name so the runbook, not the log, is the place a reader learns the header is local-only; keeps the file's `Authorization` grep count at its pre-task value of 1"
  - "McpSupervisorProbeTest adds an @AfterEach supervisor.shutdown() rather than following McpSupervisorConnectionTest's leaked-scheduler precedent"
  - "The external-mode tests use TLS (externalTlsSettings) because that is the only external-mode helper Wave 1 provides; openConnection's loopback trust-all branch makes the probe work against the auto-generated self-signed cert"
metrics:
  duration: ~18 min
  tasks: 2
  commits: 2
  tests-added: 4
  completed: 2026-08-06
---

# Phase 20 Plan 05: Mode-Aware Takeover Probe & Runbook Correction Summary

Made `McpSupervisor.probeExistingServer` mirror the server's D-02 mode split so external-mode
bind-conflict takeover keeps scheduling retries instead of failing silently, gave the probe its first
test against a real server, and corrected the two `docs/mcp-hardening.md` items that promised a control
D-01 deliberately removed.

## The New probeExistingServer Body, Verbatim

Only the return expression and the comment above it changed. The `expression-body fun … = try { … }`
form, the `scheme` selection, both 800 ms timeouts, the inner `try { … } finally { conn.disconnect() }`
and the outer `catch (e: Exception) → logToOutput → false` are byte-identical.

```kotlin
conn.connect()
val alive = conn.responseCode in 200..299
// SEC-04 / D-02 / D-05: the identity assertion below is mode-aware because the server
// side is. Three facts, all load-bearing:
// (1) KtorMcpServerManager appends `X-Burp-AI-Agent: mcp` to /__mcp/health ONLY when
//     external access is disabled. Requiring it unconditionally would make
//     attemptTakeover return NO_COMPATIBLE_SERVER on every external-mode bind
//     conflict, and handleBindFailure would then schedule NO retry at all — the MCP
//     server would stay down silently.
// (2) The header was always trivially spoofable by whichever process holds the port,
//     which is exactly Finding 7. Degrading external takeover to a liveness check
//     therefore takes away no guarantee that previously held.
// (3) Establishing REAL listener identity is Phase 25 / SEC-07 per D-05. Nothing in
//     this probe may present the bearer token to an unverified port holder, so it
//     sends no credential header of any kind — do not add one here.
if (alive && settings.externalEnabled) {
    api.logging().logToOutput(
        "MCP probe on ${settings.host}:${settings.port} could not establish server identity: " +
            "the identifying header is emitted in local mode only. " +
            "Proceeding with takeover on liveness alone.",
    )
}
alive && (settings.externalEnabled || conn.getHeaderField("X-Burp-AI-Agent") == "mcp")
```

The operative return expression is:

```kotlin
alive && (settings.externalEnabled || conn.getHeaderField("X-Burp-AI-Agent") == "mcp")
```

Short-circuiting is preserved: when the response is not 2xx, `getHeaderField` is never called, exactly
as before.

## The Output-Tab Line

Emitted once per probe, only when the response is 2xx **and** `externalEnabled` is true:

```
MCP probe on <host>:<port> could not establish server identity: the identifying header is emitted in local mode only. Proceeding with takeover on liveness alone.
```

## What Did NOT Change

`attemptTakeover`, `handleBindFailure`, `BindTakeoverOutcome`, `openConnection` and
`requestRemoteShutdownWithToken` are untouched — `git diff` against the wave-2 base shows the change
confined to lines 264-284 of `McpSupervisor.kt`. `grep -c 'Authorization'` on that file is **1**, its
pre-task value: the single occurrence is still `requestRemoteShutdownWithToken`'s
`setRequestProperty`, and the probe gained no credential header. The comment was worded to avoid the
literal token so that grep measures code rather than prose.

## McpSupervisorProbeTest

Four tests, each against a REAL `KtorMcpServerManager` on a `freePort()`, one manager per test with
`try { … } finally { manager.shutdown() }` (RESEARCH P6). The private method is reached by reflection
(`getDeclaredMethod("probeExistingServer", McpSettings::class.java)`), following
`McpSupervisorConnectionTest.kt:49-60`.

| Test | Asserts |
|------|---------|
| `probeExistingServer_localMode_returnsTrueAgainstRunningServer` | local probe still returns true; failure message states the probe sends no `Origin`/`Referer` and its `Java/21…` UA matches no browser indicator, so D-03's local heuristics must not fire on it |
| `probeExistingServer_localMode_returnsFalseWhenNothingIsListening` | fails closed on a dead port |
| `probeExistingServer_externalMode_returnsTrueWithoutTheIdentityHeader` | external probe returns true on liveness alone (T-20-18 guard) **and**, via an independent `trustAllClient()` `GET /__mcp/health`, that `X-Burp-AI-Agent` is absent and the status is 200 — this second assertion is what stops the first from being a tautology |
| `probeExistingServer_neverSendsAuthorization` | external server with `token = "test-token"`; the probe succeeds with no credential, and an unauthenticated OkHttp `GET /__mcp/health` also returns 200, confirming the exemption is server behaviour (D-01) not a probe artefact. Comment names Finding 7 / Phase 25 / D-05 |

`api` for the supervisor is `McpTestServerSupport.deepStubApi()`, not a bare `mock<MontoyaApi>()` — the
new external-mode `logToOutput` call would NPE on a shallow mock. An `@AfterEach` calls
`supervisor.shutdown()` so the default scheduler thread and never-started server manager are released.

Naming: `McpSupervisorProbeTest` is outside every `-PexcludeHeavyTests=true` exclusion glob
(`build.gradle.kts` lines 158-166), verified by an identical test count of **4** with and without the
flag. The class KDoc records that `McpSupervisorRestartPolicyTest` is not the analog here because it
injects a fake `McpTakeoverClient`, which is exactly why `probeExistingServer` had zero coverage.

## docs/mcp-hardening.md — Items Added or Rewritten (for Phase 26 / DOC-03)

| Section | Item | Change |
|---------|------|--------|
| External Access | 4 | **rewritten** — was "Validate `Authorization: Bearer <token>` is sent on every request"; now states the bearer is required on every request **except** `GET /__mcp/health`, and why (liveness probe must not present the token to an unidentified port holder) |
| External Access | 5 | **added** — `X-Burp-AI-Agent: mcp` is local-mode-only; do not use it for health checks outside loopback |
| External Access | 6 | **added** — browser MCP clients unsupported in external mode: `EventSource` cannot set request headers, so it cannot present the bearer on the initial `GET /sse`; use Claude Desktop or Codex CLI |
| External Access | 7 | **added** — CORS preflight (`OPTIONS`) is answered before authentication and defaults to any origin when none are configured; restrict origins explicitly. Behaviour deliberately unchanged (RESEARCH Open Question 2) |
| Verification | 2 | **rewritten** — was "Test denied request (missing/invalid token) returns auth error"; now names external-mode `401` on `POST /message` / `GET /sse` with `200` on `/__mcp/health`, and local-mode `403` for foreign `Origin`/`Host`/`Referer` or browser `User-Agent` with no `Origin`, plus the D-08 no-body rationale and the Output-tab / audit-log reason sinks |
| Verification | 4 | **added** — the four security headers must be present on resolved-route responses, not only 404s |

Untouched: `## Baseline`, `## Operational Controls`, `## Credential Storage`, `## Incident Response`.
No changelog entry, version banner or security advisory was added — that is DOC-03 in Phase 26. No
fenced code blocks were introduced (`grep -c '```'` is 0). English only.

## Deviations from Plan

### Interpretation Calls (not defects)

**1. `build.gradle.kts` exclusion-glob line numbers.** The plan cites `build.gradle.kts:145-157`; the
`tasks.test` exclusion block is actually at lines 158-166 (the `tasks.test {` block starts at 151). The
class KDoc names the real range and the plan's citation, as 20-03 did for the same discrepancy.

**2. External-mode tests use TLS.** The plan says "start a real manager with
`McpTestServerSupport.externalTlsSettings(port, tempKeystoreDir)`" — that is the only external-mode
helper Wave 1 provides, so both external tests run over HTTPS. This is not a compromise:
`openConnection`'s loopback branch installs the trust-all `SSLSocketFactory` for `127.0.0.1`, so the
probe transparently accepts the auto-generated self-signed cert, and the assertion still exercises the
production code path.

**3. Structural form of the mode check.** The plan says "change ONLY the second conjunct". The change
also introduces a local `val alive` and a guarded `logToOutput`, because the plan's own text requires a
one-time log side effect on the external accept path — an expression with no statement slot cannot carry
one. Nesting depth stays at 3 (`try` / `try` / `if`), well inside detekt's `NestedBlockDepth` default,
and the second conjunct is still literally the only part of the boolean that changed.

### Auto-fixed Issues

None. No bug, missing-critical-functionality or blocking issue was encountered.

### Authentication Gates

None.

## Verification

| Check | Result |
|-------|--------|
| `./gradlew test --tests '*McpSupervisor*'` (Task 1) | BUILD SUCCESSFUL — includes the pre-existing `McpSupervisorRestartPolicyTest` and `McpSupervisorConnectionTest` |
| `McpSupervisorProbeTest` test count | **4** |
| same with `-PexcludeHeavyTests=true` | BUILD SUCCESSFUL — **4** tests, identical count |
| `./gradlew test detekt ktlintCheck` (plan-level gate, once) | BUILD SUCCESSFUL in 55s |
| `git diff --stat detekt-baseline.xml` | empty (QUAL-07) |
| `grep -c 'externalEnabled'` in `probeExistingServer` | 2 (the log guard and the return expression) |
| `grep -c 'X-Burp-AI-Agent'` in `McpSupervisor.kt` | 2 (comment + return expression) |
| `grep -c 'Authorization'` in `McpSupervisor.kt` | 1 — unchanged from pre-task |
| `grep -c '__mcp/health'` in the runbook | 3 (>= 3 required) |
| `grep -c 'sent on every request'` in the runbook | 0 |
| `grep -c '```'` in the runbook | 0 |
| distinct literals present in the runbook | `401`, `403`, `X-Burp-AI-Agent`, `EventSource`, `X-Frame-Options`, `Content-Security-Policy` |
| `git diff --stat <wave-2 base> HEAD` | exactly 3 files: `docs/mcp-hardening.md`, `McpSupervisor.kt`, `McpSupervisorProbeTest.kt` |
| commit file deletions | none |

All Gradle invocations carried the `JAVA_HOME=$(/usr/libexec/java_home -v 21)` prefix.

## Threat Model Follow-through

| Threat ID | Disposition | Status |
|-----------|-------------|--------|
| T-20-05 (info disclosure, probe identity) | mitigate | **implemented** — mode split mirrors the server; external accept logs that identity could not be established |
| T-20-18 (DoS, no retry scheduled) | mitigate | **implemented** — the external probe returns true on liveness, so `attemptTakeover` reaches `SHUTDOWN_REQUESTED`/`SHUTDOWN_REJECTED` and `handleBindFailure` schedules a retry again; guarded by a named test |
| T-20-19 (repudiation, inaccurate runbook) | mitigate | **implemented** — External Access 4 and Verification 2 rewritten; four items added |
| T-20-10 (info disclosure, takeover token) | transfer | unchanged — `requestRemoteShutdownWithToken` untouched, probe still credential-free |
| T-20-17 (spoofing, external takeover identity) | accept | unchanged and now logged |
| T-20-09 (info disclosure, pre-auth CORS preflight) | accept | documented in External Access item 7, code unchanged |
| T-20-SC (tampering, package installs) | accept | no dependency added |

## Notes for Phase 25 / SEC-07

Recorded here rather than acted on, per the D-05 scope fence:

- External-mode takeover now proceeds on liveness alone. Listener identity is genuinely unestablished on
  that path, and `requestRemoteShutdownWithToken` still POSTs `Authorization: Bearer <token>` to
  `/__mcp/shutdown` on whatever holds the port. That sequence — unidentified holder, then credential —
  is Finding 7 and is exactly what SEC-07 must replace.
- A replacement identity mechanism should not reuse a response header: any value the server can emit,
  a squatter can emit. `McpSupervisorProbeTest` is the place to pin whatever proof-of-identity replaces
  it; its external-mode tests are the ones that will need rewriting.
- Nothing in this plan created a `SHUTDOWN_PATH` constant or altered `BindTakeoverOutcome`, so the
  takeover state machine is still in its pre-Phase-20 shape for SEC-07 to restructure.

## Known Stubs

None. Every symbol added has a live call site; `McpSupervisorProbeTest` binds real ports and asserts on
real responses rather than mock returns.

## Threat Flags

None. No new endpoint, auth path, file access or schema change — the probe's request is the same
`GET /__mcp/health` it always sent, and the docs change is prose.

## Self-Check

- `src/main/kotlin/com/six2dez/burp/aiagent/mcp/McpSupervisor.kt` — FOUND
- `src/test/kotlin/com/six2dez/burp/aiagent/mcp/McpSupervisorProbeTest.kt` — FOUND
- `docs/mcp-hardening.md` — FOUND
- commit `08e8ff8` (Task 1) — FOUND
- commit `7cf3603` (Task 2) — FOUND

## Self-Check: PASSED
