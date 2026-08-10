---
phase: 20-mcp-access-control-correctness
plan: 08
subsystem: mcp-access-control
gap_closure: true
tags: [security, access-control, sec-04, http2, ktor, netty, red-before-green]
requirements: [SEC-04]
requires:
  - "McpAccessControlPlugin (20-01…20-06): the Plugins-phase gate and its requestFacts adapter"
  - "McpAccessControlDecision.evaluateLocal + isLoopbackAuthority (unmodified here)"
  - "McpTestServerSupport: freePort / deepStubApi / startAndAwaitRunning / trustAllClient"
provides:
  - "Transport-aware request-authority resolution: HTTP/2 :authority reaches the local-mode gate"
  - "McpTestServerSupport.localTlsSettings — local mode + TLS, the configuration that negotiates h2"
  - "McpLocalTlsAuthorityPipelineTest — SC2 over HTTP/2 with response.protocol pinned"
affects:
  - "Audit payload host field: no longer null over HTTP/2 (was HOLLOW)"
  - "Local mode + TLS: a foreign authority is now 403 on /__mcp/health, POST /message and GET /sse"
tech-stack:
  added: []
  patterns:
    - "Read the authority from RequestConnectionPoint.local, never .origin (X-Forwarded-Host exposure)"
    - "Guard RequestConnectionPoint.serverPort reads — the h2 implementation calls Integer.parseInt unguarded"
    - "Fail-closed sentinel that the shared authority parser provably rejects, pinned by its own test"
key-files:
  created:
    - src/test/kotlin/com/six2dez/burp/aiagent/mcp/McpLocalTlsAuthorityPipelineTest.kt
  modified:
    - src/main/kotlin/com/six2dez/burp/aiagent/mcp/McpAccessControlPlugin.kt
    - src/test/kotlin/com/six2dez/burp/aiagent/mcp/McpTestServerSupport.kt
decisions:
  - "HTTP/2 detection compares RequestConnectionPoint.version against the measured literal \"HTTP/2\""
  - "The h2 authority fallback is gated to HTTP/2 only — on HTTP/1.1 it would re-open the fail-open"
  - "A malformed h2 authority resolves to the sentinel <unresolved-authority>, which parseAuthority rejects → denial, never a 500"
  - "Three residuals accepted and documented in the requestFacts KDoc rather than worked around"
metrics:
  duration: "~2h10m"
  completed: 2026-08-10
  tasks: 3
  commits: 2
  files-changed: 3
  tests-added: 7
---

# Phase 20 Plan 08: HTTP/2 Request-Authority Resolution Summary

SEC-04's foreign-authority denial now fires over HTTP/2 — `requestFacts` falls back to the h2
connection point's `:authority` (guarded, HTTP/2-only), turning a measured 200/400/200 fail-open in
local mode with TLS into 403 on all three paths.

## What Was Built

The phase's remaining BLOCKER was that `McpAccessControlPlugin.requestFacts` read only the HTTP/1
`Host` header. Ktor 3.1.3 does not synthesise `Host` from HTTP/2's `:authority`, so `facts.host` was
`null` on every h2 request and `evaluateLocal`'s guard — `facts.host != null && !isLoopbackAuthority(…)`
— was dead code. Local mode with TLS is user-reachable (the "Enable TLS" checkbox is independent of the
external-access toggle) and its `sslConnector` negotiates `h2` over ALPN, so a DNS-rebinding authority
was being served.

Three things changed: an empirical accessor measurement (Task 1, transient probe, not committed), a
red-before-green pipeline test over TLS/h2 (Task 2), and the transport-aware resolution itself
(Task 3).

## Task 1 — Measured accessor matrix (OBSERVED values)

Measured by a transient `McpAuthorityAccessorProbe` built on its own `embeddedServer(Netty)` — one
cleartext `connector`, one `sslConnector` from `McpTls.resolve(externalTlsSettings(...))` into a temp
keystore. Written, run, deleted; never committed. Every cell below is an observed value.

| Row | Setup | `response.protocol` | `headers["Host"]` | `headers[":authority"]` | `local.version` | `local.serverHost` | `local.serverPort` | `origin === local` |
|-----|-------|--------------------|-------------------|-------------------------|-----------------|--------------------|--------------------|---------------------|
| a | TLS, URL host `evil.example`, bound port | `h2` (code 200) | `null` | `null` | `HTTP/2` | `evil.example` | `58624` (the bound port) | `true` |
| b | TLS, URL host `127.0.0.1`, bound port | `h2` (code 200) | `null` | `null` | `HTTP/2` | `127.0.0.1` | `58624` | `true` |
| c | Cleartext, URL host `127.0.0.1` | `http/1.1` (code 200) | `127.0.0.1:58625` | `null` | `HTTP/1.1` | `127.0.0.1` | `58625` | `true` |
| d | Cleartext HTTP/1.1, raw socket, **NO `Host` header** | `HTTP/1.1 200 OK` (raw) | `null` | `null` | `HTTP/1.1` | **`localhost`** | `58625` | `true` |

Raw row (d) request line: `GET /probe HTTP/1.1` + `Connection: close` + CRLF CRLF. `Connection: close`
was required because keep-alive held the socket open until the read timeout; it is not a `Host` header,
so the measurement stands.

**No measurement contradicted any of the nine bytecode facts. Two were confirmed empirically:**

- **Fact 1 confirmed.** `headers[":authority"]` is `null` on both h2 rows. The accessor originally
  suggested in `20-VERIFICATION.md` genuinely does not work; it was not used.
- **Fact 5 confirmed, and it is the sharpest result here.** Row (d) shows an HTTP/1.1 request with no
  `Host` at all yielding `local.serverHost = localhost` plus the bound port. `localhost` is in
  `LOOPBACK_HOSTS` and the port matches `settings.port`, so an **ungated** fallback would have turned a
  `Host`-less HTTP/1.1 request from *denied* into *allowed* — the exact fail-open being closed. This is
  why the fallback is HTTP/2-only.

**Chosen resolution expression:**

```kotlin
host = call.request.headers["Host"] ?: http2Authority(call)

// http2Authority(call):
if (call.request.local.version != "HTTP/2") null
else try { "${call.request.local.serverHost}:${call.request.local.serverPort}" }
     catch (_: RuntimeException) { "<unresolved-authority>" }
```

**HTTP/2 detection comparison:** `call.request.local.version != "HTTP/2"`, quoting the observed
`local.version` literal from rows (a) and (b). Rows (c) and (d) observed `HTTP/1.1`, so the two
transports are cleanly separable by this string.

`local.host` / `local.port` were never read: per fact 3 both carry
`kotlin.Deprecated(level = ERROR)` and would be a hard compile failure. `serverHost` / `serverPort`
sufficed.

**Throwing-port-getter path was NOT reachable from the probe.** A malformed port inside `:authority`
cannot be produced through an OkHttp URL — the URL parser rejects it before a request is built — so
row (a) did not exercise it. The guard therefore rests on the bytecode evidence (fact 4:
`Http2LocalConnectionPoint.getServerPort()` calls `Integer.parseInt` with no exception table), not on a
runtime observation. It is not optional: a raw h2 client can send any authority Netty accepts, and an
unguarded getter turns an attacker-controlled string into a 500 on a Netty event-loop thread.

Task 1 gate: `/tmp/20-08-probe.log` → `BUILD SUCCESSFUL` present, `grep -c 'e: file'` = **0** (the probe
provably compiled and ran), probe file absent, `git status --porcelain src` empty.

## Task 2 — RED transcript

`McpTestServerSupport` gained exactly one function, `localTlsSettings(port, keystoreDir, token)`
(`externalEnabled = false`, `tlsEnabled = true`, `tlsAutoGenerate = true`, `host = "127.0.0.1"`), and
its stale `build.gradle.kts:145-157` citation was replaced with the `excludeHeavyTests`-filter-block
symbol anchor. **The edit was purely additive — no existing signature or behaviour changed**, so the
sibling gap-closure worktrees compiling against this helper stay valid after merge.

`McpLocalTlsAuthorityPipelineTest` ran against the unmodified plugin:

```
gradle exit          = 1        (the `!` in the gate inverts this)
tests="7" skipped="0" failures="3" errors="0"
grep -c 'e: file' /tmp/20-08-red.log = 0   → assertion evidence, not compile-failure evidence
grep -c 'AssertionFailedError' TEST-…McpLocalTlsAuthorityPipelineTest.xml = 3   (gate: -ge 3)
```

The three failures, all on the CODE assertion with the `Protocol.HTTP_2` assertion **passing** (so the
redness is not an ALPN downgrade artefact):

| Test | Assertion | Observed pre-fix |
|------|-----------|------------------|
| `foreignAuthorityOverHttp2_isRejectedOnHealth` | expected 403 | **200** |
| `foreignAuthorityOverHttp2_isRejectedOnMessage` | expected 403 | **400** — the SDK handler ran; that is the bypass |
| `foreignAuthorityOverHttp2_isRejectedOnSse` | expected 403 | **200** |

Observed `response.protocol` on every row: `HTTP_2`. The pre-fix codes reproduce the verifier's
measured 200/400/200 exactly.

The four non-red tests were green pre-fix as designed and contribute **zero** to the red count:
`loopbackAuthorityOverHttp2_stillReachesHealth` (positive control, 200),
`foreignRefererOverHttp2_isRejected` and `browserUserAgentWithoutOriginOverHttp2_isRejected` (SC2's
other two limbs — ordinary headers survive the pseudo-header filter, so green before *and* after), and
`unresolvableAuthoritySentinel_isRejectedByTheSharedPredicate`.

The three foreign-authority tests are **three separate `@Test` methods, not a loop**, and a comment in
the file says why: a single looping test aborts at its first failed assertion and emits one `<failure>`
element, which would fail this task's `-ge 3` gate against a correct implementation. The in-file
precedent (`McpAccessControlPipelineTest.foreignHost_isRejectedOnEveryPath`) loops; that was
deliberately not followed here.

Commit: `651172b` — a deliberately red `test(20-08)` commit.

## Task 3 — GREEN transcript

```
./gradlew test --tests '*McpLocalTlsAuthorityPipelineTest'
  → exit 0, tests="7" skipped="0" failures="0" errors="0"

./gradlew test detekt ktlintCheck
  → exit 0; full suite 100 classes / 601 tests / 0 failures / 0 errors / 1 skipped

./gradlew test --tests '*McpLocalTlsAuthorityPipelineTest' -PexcludeHeavyTests=true
  → exit 0, tests="7"  (identical count with and without the flag)

git diff --stat detekt-baseline.xml  → empty (QUAL-07 holds, baseline byte-identical)
git diff --name-only b9ee87a..HEAD   → exactly the plan's three files
```

Commit: `bb51c02`.

Implementation notes: one new top-level helper (`http2Authority`), expression-bodied with **zero**
`return` statements, so `ReturnCount`'s default max of 2 is satisfied without a baseline entry; the file
now has three top-level functions against a `TooManyFunctions` `thresholdInFiles` of 11. Two new
top-level `const val`s (`HTTP_2_VERSION`, `UNRESOLVABLE_AUTHORITY`). `catch (_: RuntimeException)` rather
than `Exception` so an `Error` still propagates, with the `_` binding satisfying detekt's
`SwallowedException` / `TooGenericExceptionCaught` allowed-name regex. The
`@file:Suppress("MatchingDeclarationName")` and the load-bearing `isCommitted` guard are both intact,
and `McpAccessControlDecision.kt` was **not** touched (plan 20-07 owns it).

## Residuals — verbatim as written into the `requestFacts` KDoc

> ## Residuals (accepted, all fail-closed except residual 1)
>
> 1. An HTTP/2 request with `:authority` ABSENT coalesces to the server's own socket and is therefore
>    ALLOWED. This is not closable with Ktor's public API, because the raw `Http2Headers` sits behind
>    a class the Ktor module declares `internal`, so absent and loopback-valued authorities are
>    indistinguishable from here. RFC 9113 makes a request carrying neither `:authority` nor `host`
>    malformed, so a conforming client and intermediary will not produce one.
> 2. A bracketed IPv6 `:authority` is split on its FIRST colon by the HTTP/2 connection point, so
>    `[::1]:9876` yields `[` and the original authority cannot be reconstructed — the request is
>    DENIED. (The HTTP/1.1 connection point splits on the LAST colon and is bracket-safe, which is
>    why this asymmetry exists at all.) `McpSettings.host` defaults to `127.0.0.1`, so this is
>    reachable only for an operator who binds MCP to `::1` AND enables TLS AND uses an h2 client; the
>    outcome is fail-closed.
> 3. A PORTLESS `:authority` acquires the scheme's default port from the connection point, whose port
>    getter falls back through `substringAfter(":", <defaultPort>)`. So HTTP/2 DENIES
>    `:authority: localhost` — resolved as `localhost:443` over TLS, a port mismatch — where HTTP/1.1
>    ALLOWS `Host: localhost` (empty port capture, null port, comparison skipped). Fail-closed, and a
>    behavioural asymmetry between the two transports rather than a defect in either.

## Gaps Closed

| Gap | Status |
|-----|--------|
| 20-VERIFICATION gap 1, transport half — `facts.host` null over h2, foreign authority reached 200 | CLOSED — 403 on all three paths, `Protocol.HTTP_2` asserted first on each |
| 20-VERIFICATION gap 1, missing test — nothing drove the local-mode gate over TLS/h2 | CLOSED — `McpLocalTlsAuthorityPipelineTest`, 7 tests, red-before-green proven |
| 20-VERIFICATION data-flow row — audit payload `host` always null over h2 (HOLLOW) | CLOSED — `facts.host` now carries the client-supplied authority on the h2 path |

## Threat Model Dispositions

`mitigate` rows T-20-08-01 (h2 host resolution), T-20-08-02 (403 on all three paths with protocol
pinned), T-20-08-03 (guarded port read → denial, never a 500), T-20-08-04 (`local` not `origin`) and
T-20-08-07 (audit `host` no longer hollow) are all implemented. `accept` rows T-20-08-05, T-20-08-06 and
T-20-08-08 map one-to-one onto residuals 1, 2 and 3 above. T-20-08-SC holds: no dependency added or
changed, `build.gradle.kts` untouched, no package-manager install step.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Task 1 probe row (d) hung on HTTP/1.1 keep-alive**

- **Found during:** Task 1, first probe run
- **Issue:** The raw-socket request for row (d) used `GET /probe HTTP/1.1` + bare CRLF CRLF. The server
  responded, but keep-alive kept the connection open, so `readBytes()` blocked to the socket read
  timeout and the probe failed with `SocketTimeoutException` — row (d) was never measured and the
  Task 1 gate could not see `BUILD SUCCESSFUL`.
- **Fix:** Added `Connection: close` to the raw request line so the server closes after responding.
  That is not a `Host` header, so the "no `Host` header" property under measurement is preserved.
- **Files modified:** transient probe only (deleted; not committed)
- **Commit:** none — probe was never committed per the plan

No other deviations. No Rule 4 (architectural) situations arose, no authentication gates, and no
checkpoints were reached.

## Known Stubs

None. No hardcoded empty value, placeholder string or unwired data path was introduced.

## Follow-ups / Notes for the Orchestrator

- `STATE.md` and `ROADMAP.md` were deliberately **not** touched, per the parallel-executor contract.
- Only the plan's three files changed, so the merge with siblings 20-07 / 20-09 / 20-10 should be
  disjoint. The one shared file, `McpTestServerSupport.kt`, was extended additively only.
- The isolation gate here does not cover the merged tree or `-PstoreBuild=true`; the orchestrator owns
  that post-merge run.

## Self-Check: PASSED
