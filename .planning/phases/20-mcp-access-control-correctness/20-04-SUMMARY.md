---
phase: 20-mcp-access-control-correctness
plan: 04
subsystem: mcp
tags: [security, access-control, regression-test, sec-04, sec-05, netty, tls, okhttp, audit]
requires:
  - "McpTestServerSupport (20-02) — freePort / deepStubApi / localSettings / externalTlsSettings / startAndAwaitRunning / plainClient / trustAllClient / baseUrl"
  - "McpAccessControl Plugins-phase gate + live onBlocked wiring (20-03)"
  - "McpBlockedRequestReporter + AuditLogger.registerGlobalEmitter (20-01)"
provides:
  - "McpAccessControlPipelineTest — 9 local-mode assertions against a real cleartext Netty server (SC2, SC3, D-02-local, D-06 end-to-end, SC6)"
  - "McpAccessControlExternalPipelineTest — 8 external-mode assertions against a real TLS Netty server (SC1, SC3, SC5c, D-01, D-02-external, SC6)"
  - "the single end-to-end proof that KtorMcpServerManager's onBlocked lambda is not a no-op"
affects:
  - "20-06 (SC4 transcript maps each failing @Test method name below to a RESEARCH gate assertion)"
tech-stack:
  added: []
  patterns:
    - "one KtorMcpServerManager + one free port per @Test, assertions inside try { } finally { manager.shutdown() }"
    - "AuditLogger.registerGlobalEmitter capture into a CopyOnWriteArrayList<Pair<String, Any>>, reset to null in @AfterEach"
    - "OkHttp for any test that must forge Host / Origin / Referer; status-only helper that closes the response so an SSE body is never read"
    - "class KDoc records the -PexcludeHeavyTests=true naming constraint at the site it protects"
key-files:
  created:
    - src/test/kotlin/com/six2dez/burp/aiagent/mcp/McpAccessControlPipelineTest.kt
    - src/test/kotlin/com/six2dez/burp/aiagent/mcp/McpAccessControlExternalPipelineTest.kt
  modified: []
decisions:
  - "The mandated KDoc/comment mentions of the JDK URL-connection client were paraphrased, not dropped: the plan also requires grep -c 'HttpURLConnection' == 0 on both files, so the fact is documented without the identifier (same resolution as 20-03 deviation 7)"
  - "keystoreDir cleanup uses File.deleteRecursively via Path.toFile(), because Path.deleteRecursively is still @ExperimentalPathApi and an opt-in would buy nothing"
  - "No BindException retry added around startAndAwaitRunning: 17 real server binds across 4 runs produced zero failures, and a retry would have been unverifiable speculation"
metrics:
  duration: ~35 min
  tasks: 2
  commits: 2
  tests-added: 17
  completed: 2026-08-06
---

# Phase 20 Plan 04: MCP Access-Control Pipeline Regression Gates Summary

Two new test classes drive a REAL Netty server through `KtorMcpServerManager` — cleartext for local
mode, TLS with an auto-generated PKCS12 for external mode — and assert the access-control behaviour
that `KtorMcpServerManagerSecurityTest` and `McpServerIntegrationTest` were both green for while the
F1 bypass was live.

## What Was Built

`McpAccessControlPipelineTest` (9 tests, cleartext, `externalEnabled = false`) and
`McpAccessControlExternalPipelineTest` (8 tests, TLS, `externalEnabled = true`). Both use
`McpTestServerSupport` exclusively; neither contains a hand-rolled client and neither mentions the JDK
URL-connection client by identifier (`grep -c 'HttpURLConnection'` returns 0 on both).

## Full `@Test` Method Names — Plan 06 Needs These Exactly

### `McpAccessControlPipelineTest` (local mode, cleartext, 9 tests)

| # | `@Test` method | RESEARCH gate assertion | Pre-fix result |
|---|----------------|-------------------------|----------------|
| 1 | `health_matchedRouteCarriesAllFourSecurityHeaders` | **#4** | 200 with NONE of the four headers |
| 2 | `health_localModeCarriesAgentIdentityHeader` | — (D-02 local, passes pre-fix) | 200 + `X-Burp-AI-Agent: mcp` |
| 3 | `foreignHost_isRejectedOnEveryPath` | **#5** | 200 / 400 / 200 |
| 4 | `foreignReferer_isRejectedOnEveryPath` | **#6** | 200 / 400 / 200 |
| 5 | `browserUserAgentWithoutOrigin_isRejectedOnEveryPath` | **#7** | 200 / 400 / 200 `text/event-stream` |
| 6 | `foreignOrigin_isRejected` | NOT a gate (P2 — Ktor CORS already 403) | 403 |
| 7 | `foreignHost_emitsExactlyOneTransportBlockedAuditEvent` | **D-06/D-07/D-09/D-10 end-to-end** | 0 events (no audit event existed) |
| 8 | `sse_authorizedConnectCarriesSecurityHeaders` | part of **#4** on the streaming response | 200 without the four headers over HTTP/1.1 |
| 9 | `shutdownWithoutToken_stillReturns401` | NOT a gate (SC6 non-regression) | 401 |

### `McpAccessControlExternalPipelineTest` (external mode, TLS, 8 tests)

| # | `@Test` method | RESEARCH gate assertion | Pre-fix result |
|---|----------------|-------------------------|----------------|
| 1 | `message_withoutAuthorization_returns401` | **#1** | `400 "sessionId query parameter is not provided"` |
| 2 | `sse_withoutAuthorization_returns401` | **#2** | `200 text/event-stream` (session minted) |
| 3 | `health_withoutAuthorization_returns200WithoutAgentHeader` | **#3** | 200 **with** `X-Burp-AI-Agent` |
| 4 | `message_withValidBearer_reachesHandlerAndCarriesSecurityHeaders` | SC3 over TLS/HTTP-2 | 400 + headers (passes pre-fix over TLS only) |
| 5 | `invalidBearerForms_areRejected` | reinforces **#1** | 400 for all three forms |
| 6 | `shutdownWithoutToken_stillReturns401` | NOT a gate (SC6) | 401 |
| 7 | `blankConfiguredToken_rejectsEveryAuthenticatedPath` | **#9** at pipeline level (SC5c) | `Bearer ` authenticated → 400 |
| 8 | `blankConfiguredToken_stillAllowsHealth` | branch-order pin (D-01 before 5c) | 200 |

Nine SC2 403 assertions live in tests 3, 4 and 5 of the local class (three forged headers × three
paths), routed through the private `assertForbiddenOnEveryPath` helper.

## Observed `mcp_transport_blocked` Audit Event

Captured by `foreignHost_emitsExactlyOneTransportBlockedAuditEvent` from a real
`GET /__mcp/health` carrying `Host: evil.example` against a local-mode server. Exactly ONE event was
captured for one blocked request. Payload is a `LinkedHashMap<String, Any?>` in this key order:

| Key | Observed value | Note |
|-----|----------------|------|
| `reason` | `"host_mismatch"` | asserted — the D-06 wire string, not the `BlockReason` enum name |
| `mode` | `"local"` | |
| `method` | `"GET"` | sanitized plaintext, **not hashed** |
| `path` | `"/__mcp/health"` | sanitized plaintext, **not hashed** — a hashed path makes a blocked route undiagnosable |
| `origin` | `null` | absent header stays null; never the SHA-256 of the empty string |
| `host` | `9c180de0cd699ee78897c47cfdb3e7ee1d75906e31b7746a4747dea536909837` | `sha256Hex("evil.example")`, verified independently with `shasum -a 256` |
| `referer` | `null` | |
| `userAgent` | SHA-256 hex of OkHttp's default `User-Agent` | hashed under D-10 (`verboseAudit = false` in production wiring) |

`method` and `path` being plaintext is the shipped Wave 1 behaviour and is deliberate — see the
Deviations section.

## Verification

| Check | Result |
|-------|--------|
| `./gradlew test --tests '*McpAccessControlPipelineTest'` | BUILD SUCCESSFUL — **9 tests, 0 failures** |
| same with `-PexcludeHeavyTests=true` | BUILD SUCCESSFUL — **9 tests**, identical count |
| `./gradlew test --tests '*McpAccessControlExternalPipelineTest'` | BUILD SUCCESSFUL — **8 tests, 0 failures** |
| same with `-PexcludeHeavyTests=true` | BUILD SUCCESSFUL — **8 tests**, identical count |
| `./gradlew test detekt ktlintCheck` (plan-level gate, once) | BUILD SUCCESSFUL in 1m 27s |
| `git diff --stat detekt-baseline.xml` | empty (QUAL-07) |
| `git status --porcelain -- src/` | only the two new test files, both now committed |
| `grep -c 'HttpURLConnection'` on both files | 0 / 0 |
| `grep -c 'mcp_transport_blocked'` (local class) | 1 (the `TRANSPORT_BLOCKED` constant) |
| `grep -c 'registerGlobalEmitter'` (local class) | 2 — one registration in `@BeforeEach`, one `null` reset in `@AfterEach` |
| `grep -c 'Files.createTempDirectory'` (external class) | 1 |
| `grep -c 'burp-ai-agent/certs'` (external class) | 0 |
| `.body.string()` call sites | only on the two `/__mcp/health` 200s; never on a `text/event-stream` response |

All Gradle invocations carried the `JAVA_HOME=$(/usr/libexec/java_home -v 21)` prefix. Test-count
parity under `-PexcludeHeavyTests=true` was measured from
`build/test-results/test/TEST-*.xml`, not inferred from the class names.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Two acceptance criteria contradict their own `<action>` block on `HttpURLConnection`**
- **Found during:** Task 1 acceptance-criteria check, and again in Task 2
- **Issue:** Each task's `<action>` mandates a KDoc paragraph stating "the client is OkHttp because
  `HttpURLConnection` silently drops `Origin` and always writes its own `Host`", while each task's
  acceptance criteria require `grep -c 'HttpURLConnection' <file>` to return **0**. Both cannot hold
  literally. The first draft of the local class returned 1 (a Host-header comment) and would have
  returned 2 with the KDoc as written.
- **Fix:** Kept the fact, dropped the identifier — "the JDK's built-in URL-connection client (the one
  `McpServerIntegrationTest` uses through its `httpRequest` helper)", plus a sentence pointing at
  `McpTestServerSupport`'s KDoc where the identifier IS spelled out, and stating that the paraphrase
  exists so the guard grep measures code and not prose. Identical resolution to 20-03 deviation 7.
- **Files modified:** both new test files
- **Commits:** `2a82751`, `4523776`

### Interpretation Calls (not defects)

**2. The plan's `Path.deleteRecursively` shape needed a stable API.** `kotlin.io.path.deleteRecursively`
is `@ExperimentalPathApi` and would require a file- or function-level opt-in. Swapped for
`keystoreDir.toFile().deleteRecursively()`, which is stable, with a comment recording why. The
`@BeforeAll`/`@AfterAll` once-per-class keystore requirement is unchanged: `keytool` runs on the first
external `start()` only, and all seven later starts load the existing PKCS12.

**3. `@BeforeAll`/`@AfterAll` needed a `companion object` with `@JvmStatic`.** The plan does not say
which JUnit lifecycle mechanism to use; `@TestInstance(PER_CLASS)` was rejected because it would also
turn the per-test `@BeforeEach`-free class into a shared instance and obscure the one-manager-per-test
invariant.

**4. No `BindException` retry was added.** The plan makes it conditional ("if `ServerSocket(0)` port
reuse proves flaky in CI"). 17 real server binds across four separate runs, plus a full-suite run,
produced zero bind failures, so a retry would be unverified code guarding an unobserved failure. The
condition, and the fact that `startAndAwaitRunning` already reports the terminal state in its failure
message, are left as-is.

**5. The audit assertion pins `reason` only, not the whole payload.** `BlockedRequestReporterTest`
already asserts the full key set, key order, hashing and sanitization at unit level. The end-to-end
test's job is to prove the event is EMITTED FROM A REAL REQUEST with the right reason; duplicating the
payload-shape assertions here would couple a pipeline test to a payload contract that already has a
dedicated owner. The full observed payload is recorded above for the record.

**6. `method` and `path` are asserted-adjacent as plaintext, not hashed.** Upstream reality overrides
the phase's earlier plan text here: `McpBlockedRequestReporter.buildPayload` sanitizes but does not
hash those two fields, on purpose (a hashed request path makes a blocked route undiagnosable for zero
privacy gain). Nothing in these tests asserts a hashed `method` or `path`.

### Authentication Gates

None.

## Threat Model Follow-through

| Threat ID | Disposition | Status |
|-----------|-------------|--------|
| T-20-01 (EoP, external gate) | mitigate | **implemented** — `message_withoutAuthorization_returns401`, `sse_withoutAuthorization_returns401`, `invalidBearerForms_areRejected`, against a real TLS connector |
| T-20-02 (spoofing, local gate) | mitigate | **implemented** — nine 403 assertions with OkHttp, which actually transmits the forged `Host` / `Referer` / `User-Agent` |
| T-20-03 (tampering, security headers) | mitigate | **implemented** — asserted individually on the matched-route 200, the SSE 200 and the authorized external 400 over TLS/HTTP-2 |
| T-20-04 (EoP, blank token) | mitigate | **implemented** — `blankConfiguredToken_rejectsEveryAuthenticatedPath` + `blankConfiguredToken_stillAllowsHealth` |
| T-20-05 (info disclosure, health identity) | mitigate | **implemented** — `assertNull` on external, `"mcp"` on local |
| T-20-06 (repudiation, `onBlocked` wiring) | mitigate | **implemented** — one real blocked request, exactly one captured `mcp_transport_blocked`, `reason == "host_mismatch"`. An empty `onBlocked` fails this test, which is the unfalsifiable-by-omission property the plan asked for |
| T-20-14 (tampering, test keystore path) | mitigate | **implemented** — `Files.createTempDirectory("mcp-ac-ext-ks")`, removed in `@AfterAll`, zero references to the user's real certificate directory |
| T-20-SC (package installs) | accept | no install performed; OkHttp 5.4.0 and JUnit Jupiter 6.0.3 were already declared |

## Known Stubs

None. Every assertion observes a real HTTP response from a real bound port, and the audit assertion
observes a real emitted event rather than a non-null lambda.

## Threat Flags

None. Test-only change; no new endpoint, no new trust boundary, no schema change. The only filesystem
write is a PKCS12 into a per-class temp directory that is removed in `@AfterAll`.

## Notes for Plan 06 (SC4)

- The gate assertions that must go RED against the pre-fix `KtorMcpServerManager` are the eight rows
  marked **bold** in the two tables above. `foreignOrigin_isRejected`,
  `health_localModeCarriesAgentIdentityHeader` and both `shutdownWithoutToken_stillReturns401` methods
  pass pre-fix by design and must NOT be counted as SC4 evidence.
- `foreignHost_emitsExactlyOneTransportBlockedAuditEvent` also goes red pre-fix, for a second reason:
  the pre-fix code has no audit event at all, so the count is 0 rather than 1 even before the 403
  assertion is reached.
- `message_withValidBearer_reachesHandlerAndCarriesSecurityHeaders` passes pre-fix (headers were
  present over TLS/HTTP-2) and is therefore not SC4 evidence either; its value is guarding P3.
- Reverting `McpAccessControlPlugin.kt` alone is not enough to reproduce the pre-fix state: the D-02
  `if (!settingsSnapshot.externalEnabled)` guard on the health route lives in `KtorMcpServerManager`,
  so `health_withoutAuthorization_returns200WithoutAgentHeader` needs that guard reverted too.

## Self-Check

- `src/test/kotlin/com/six2dez/burp/aiagent/mcp/McpAccessControlPipelineTest.kt` — FOUND
- `src/test/kotlin/com/six2dez/burp/aiagent/mcp/McpAccessControlExternalPipelineTest.kt` — FOUND
- commit `2a82751` (Task 1) — FOUND
- commit `4523776` (Task 2) — FOUND

## Self-Check: PASSED
