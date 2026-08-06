---
phase: 20-mcp-access-control-correctness
plan: 01
subsystem: mcp
tags: [security, access-control, ipv6, bearer-auth, observability, audit, cwe-117]
requires: []
provides:
  - "McpAccessControlDecision.evaluate — pure allow-or-deny for one MCP request"
  - "BlockReason enum owning the six D-06 wire strings"
  - "RequestFacts / GateDecision value types"
  - "isLoopbackAuthority — shared D-11 authority predicate with bracketed IPv6 support"
  - "isLoopbackUrlAuthority / isBrowserUserAgent / isAuthorizedBearer / constantTimeCompare"
  - "HEALTH_PATH constant"
  - "McpBlockedRequestReporter.report — D-06/D-07/D-09/D-10 blocked-request observability"
affects:
  - "20-03 (Ktor plugin adapter wires evaluate + report)"
  - "20-04 (regression suite asserts on these behaviours)"
tech-stack:
  added: []
  patterns:
    - "Pure decision core + separate observability class, so security logic is unit-testable without binding a port"
    - "Injected clock (nowMs parameter) for rate-limit windows, matching PassiveAiScannerAnalysis.maybeLogBackoff"
    - "Lock-free read-then-CAS window limiter (ConcurrentHashMap + AtomicLong), safe on Netty event-loop threads"
    - "Anchored matchEntire regexes as the fail-closed authority parse, avoiding both exceptions and extra early exits"
key-files:
  created:
    - src/main/kotlin/com/six2dez/burp/aiagent/mcp/McpAccessControlDecision.kt
    - src/main/kotlin/com/six2dez/burp/aiagent/mcp/McpBlockedRequestReporter.kt
    - src/test/kotlin/com/six2dez/burp/aiagent/mcp/McpAccessControlDecisionTest.kt
    - src/test/kotlin/com/six2dez/burp/aiagent/mcp/BlockedRequestReporterTest.kt
  modified: []
decisions:
  - "Local-mode browser-UA denial narrowed to the Origin-ABSENT case: a valid loopback Origin plus a browser User-Agent was 403 pre-fix and is Allow post-fix"
  - "New audit event type mcp_transport_blocked rather than reusing mcp_tool_blocked, whose payload keys have no transport meaning"
  - "method and path are sanitized but NOT hashed in the audit payload; only the four reflected header values are hashed (T-20-11 says 'reflected header values')"
  - "parseAuthority implemented with two anchored regexes instead of an imperative when-cascade, forced by detekt NestedBlockDepth"
  - "isValidHostAuthority alias dropped; evaluateLocal calls isLoopbackAuthority directly to stay under detekt TooManyFunctions (thresholdInFiles 11)"
metrics:
  duration: ~35 min
  tasks: 2
  commits: 4
  tests-added: 54
  completed: 2026-08-06
---

# Phase 20 Plan 01: MCP Access-Control Decision Core & Blocked-Request Reporter Summary

Extracted the MCP access-control gate into two pure, server-free halves — a decision function that
returns allow-or-deny, and a reporter that turns a denial into a hashed audit event plus a
rate-limited Output line — with the IPv6 and blank-token defects fixed at the predicate level.

## What Was Built

`McpAccessControlDecision.kt` holds the decision. Nothing in it imports a server-side Ktor package
(only `io.ktor.http.HttpStatusCode`, which is engine-free), so all 38 of its unit tests run without
binding a port or touching reflection. `McpBlockedRequestReporter.kt` holds the observability, with
the clock and the Output sink both injected, so all 16 of its tests are exact and none of them sleep.

Wave 2's Ktor plugin is now a thin adapter: it lifts headers into `RequestFacts`, calls `evaluate`,
and on `Deny` calls `report` then responds. No security logic needs to live in the plugin.

## Exact Signatures (Plan 03 wires against these; Plan 04 asserts on them)

```kotlin
// McpAccessControlDecision.kt — package com.six2dez.burp.aiagent.mcp
internal const val HEALTH_PATH = "/__mcp/health"

internal enum class BlockReason(val wireValue: String)

internal data class RequestFacts(
    val method: String = "GET",
    val path: String,
    val origin: String? = null,
    val host: String? = null,
    val referer: String? = null,
    val userAgent: String? = null,
    val authorization: String? = null,
)

internal sealed class GateDecision {
    data object Allow : GateDecision()
    data class Deny(
        val status: HttpStatusCode,
        val reason: BlockReason,
        val facts: RequestFacts,
    ) : GateDecision()
}

internal fun evaluate(facts: RequestFacts, settings: McpSettings): GateDecision

private fun parseAuthority(value: String): Pair<String, Int?>?      // host to port; null port when absent, null result when malformed
internal fun isLoopbackAuthority(authority: String, expectedPort: Int?): Boolean
internal fun isLoopbackUrlAuthority(url: String): Boolean
internal fun isBrowserUserAgent(userAgent: String?): Boolean
internal fun isAuthorizedBearer(authHeader: String, token: String): Boolean
internal fun constantTimeCompare(left: String, right: String): Boolean

// McpBlockedRequestReporter.kt — package com.six2dez.burp.aiagent.mcp
internal class McpBlockedRequestReporter(
    private val logToOutput: (String) -> Unit,
    private val verboseAudit: Boolean = false,
)

internal fun report(deny: GateDecision.Deny, externalMode: Boolean, nowMs: Long)
```

`RequestFacts.method` carries a `"GET"` default and every header field defaults to `null`; the plugin
in Plan 03 should pass all seven explicitly.

## BlockReason Wire Values (D-06 — defined here and nowhere else)

| Constant | `wireValue` | Emitted when |
|----------|-------------|--------------|
| `ORIGIN_MISMATCH` | `origin_mismatch` | local mode, `Origin` present and not loopback |
| `HOST_MISMATCH` | `host_mismatch` | local mode, `Host` not a loopback authority for `settings.port` |
| `REFERER_MISMATCH` | `referer_mismatch` | local mode, `Referer` present and not loopback |
| `BROWSER_NO_ORIGIN` | `browser_no_origin` | local mode, `Origin` ABSENT and a browser `User-Agent` |
| `UNAUTHORIZED` | `unauthorized` | external mode, bearer token absent or wrong |
| `BLANK_TOKEN` | `blank_token` | external mode, configured token blank (any non-health path) |

`ORIGIN_MISMATCH` / `HOST_MISMATCH` / `REFERER_MISMATCH` / `BROWSER_NO_ORIGIN` deny `403 Forbidden`;
`UNAUTHORIZED` / `BLANK_TOKEN` deny `401 Unauthorized`.

## Deliberate Behaviour Change

| Change | Pre-fix | Post-fix |
|--------|---------|----------|
| Local mode, present-and-**VALID** loopback `Origin` accompanied by a browser `User-Agent` | **403** | **Allow** |

`KtorMcpServerManager.kt:191-199` read
`if (origin != null && !isValidOrigin(origin)) { 403 } else if (isBrowserRequest(userAgent)) { 403 }`.
That `else if` fired whenever the first branch did not — which **includes** the case where a
present-and-valid loopback `Origin` accompanied a browser `User-Agent`. The new `evaluateLocal`
narrows the browser-UA denial to the `Origin`-ABSENT case, which is what SEC-04's text mandates
("a browser User-Agent **without** an Origin header").

Practical exposure is nil: Ktor's CORS plugin admits only `localhost:<mcpPort>` / `127.0.0.1:<mcpPort>`
in local mode and the server serves no HTML, so a valid loopback `Origin` already had to clear CORS
before the gate could see it. The narrowing is recorded in the plan's threat model, in a comment above
the branch itself, and in a comment on the test that pins it — so a future reader does not "restore"
the old `else if`.

## Defects Fixed

**SEC-05 5b — bracketed IPv6 (`isLoopbackAuthority("[::1]:9876", 9876)` was `false`, now `true`).**
The pre-fix `isValidHost` did `host.split(":")` and took `parts[0]` as the hostname, which yields
`"["` for `[::1]:9876` — so a server bound to IPv6 loopback rejected its own `Host` header while
`isLoopbackHost` happily accepted `::1` as a bind address. One shared `isLoopbackAuthority` now backs
the `Host`, `Origin` and `Referer` checks (D-11), so the three cannot drift apart again. It is
deliberately **not** built on `URI(...).toURL().host`, which returns `[::1]` **with** brackets — the
exact cause of the bug.

**SEC-05 5c — blank bearer token (`isAuthorizedBearer("Bearer ", "")` was `true`, now `false`).**
The `token.isNotBlank()` guard runs before the expected string is built, so a blank configured token
can never authenticate. `evaluate` also denies every non-health request with `401 BLANK_TOKEN` when
external mode is on and the token is blank, which fails closed *and* is diagnosable.
`MessageDigest.isEqual` is retained for the comparison — not hand-rolled.

## Blocked-Request Observability

- **D-06** — one `mcp_transport_blocked` audit event on **every** block, via `AuditLogger.emitGlobal`.
  Payload is a `linkedMapOf` with exactly `reason, mode, method, path, origin, host, referer, userAgent`.
- **D-07** — control characters (C0, DEL, and the C1 range) are **removed**, whitespace collapsed, and
  values capped at 200 chars with a `...` suffix, before **either** sink. `"a\r\nInjected: line"`
  becomes `"aInjected: line"`, closing CWE-117 log injection into Burp's Output tab.
- **D-09** — per-reason 60 s window, lock-free read-then-CAS over `ConcurrentHashMap` + `AtomicLong`.
  Three blocks for one reason inside a window produce one line; the next block past the window
  produces `[McpAccessControl] 2 further blocks for origin_mismatch in the last 60s` and resets the
  counter. Windows are per-reason, so a different reason still gets its own first line immediately.
  No monitor lock and no scheduled executor — this runs on Netty event-loop threads.
- **D-10** — the four reflected header values are `Hashing.sha256Hex`-hashed by default; plaintext only
  behind the `verboseAudit` constructor seam (there is no user-facing verbose-audit flag in the repo,
  and adding one is not in SEC-04/SEC-05). An absent header stays `null` rather than becoming the
  SHA-256 of the empty string. The Output tab always carries sanitized plaintext — it is where a human
  diagnoses.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] `parseAuthority` restructured from a `when`-cascade to anchored regexes**
- **Found during:** Task 1, at the detekt gate
- **Issue:** The plan's prescribed shape — a `when` expression whose bracketed-authority branch nests a
  `let` containing a second `when` containing another `let` — trips detekt `NestedBlockDepth`
  (`McpAccessControlDecision.kt:152: Function parseAuthority is nested too deeply`). Flattening it with
  early exits was not available either: the plan caps the whole file at two `return` keywords because
  `ReturnCount` runs at its default max of 2, and QUAL-07 forbids adding a baseline entry.
- **Fix:** Replaced the nested branches with two anchored `matchEntire` regexes —
  `BRACKETED_AUTHORITY_REGEX` (`\[([0-9a-f:.]+)](?::(\d+))?`) and `PLAIN_AUTHORITY_REGEX`
  (`([0-9a-z.\-]+)(?::(\d+))?`) — retaining the bare-IPv6 membership check first. `:` is deliberately
  absent from the plain host character class, which is what still rejects the ambiguous `::1:9876` and
  the non-numeric `localhost:abc`. Every behaviour row in the plan's `<behavior>` block is unchanged
  and asserted; nesting depth drops to 2 and the file still holds exactly two `return` keywords.
- **Files modified:** `src/main/kotlin/com/six2dez/burp/aiagent/mcp/McpAccessControlDecision.kt`
- **Commit:** `5df910d`

**2. [Rule 3 - Blocking] `isValidHostAuthority` / `isValidOriginValue` / `isValidRefererValue` aliases dropped**
- **Found during:** Task 1, while sizing the file against detekt
- **Issue:** detekt `TooManyFunctions` applies to **top-level functions in a file**
  (`thresholdInFiles: 11`) — confirmed by the 32 existing baseline entries, two of which are file-level
  (`Components.kt$...Components.kt`, `McpToolHelpers.kt$...McpToolHelpers.kt`). The plan's full symbol
  list came to 14 top-level functions, which would have required a new baseline entry that QUAL-07
  forbids.
- **Fix:** Dropped three pass-through aliases. `evaluateLocal` calls `isLoopbackAuthority(facts.host,
  settings.port)` directly for `Host`, and the single shared `isLoopbackUrlAuthority(url)` for both
  `Origin` and `Referer`. This is *more* faithful to D-11's "one shared helper, not three parallel
  fixes" than three named wrappers would have been. File now declares 9 top-level functions.
- **Files modified:** `src/main/kotlin/com/six2dez/burp/aiagent/mcp/McpAccessControlDecision.kt`
- **Commit:** `5df910d`

**3. [Rule 2 - Missing critical functionality] C1 control characters added to the sanitize regex**
- **Found during:** Task 2
- **Issue:** The plan prescribed `Regex("[\\p{Cntrl}]")`, but the same plan's `<behavior>` block
  requires "ESC 0x1B and other **C0/C1** control characters" to be removed. Java's `\p{Cntrl}` covers
  only `\x00-\x1F` and `\x7F` unless `UNICODE_CHARACTER_CLASS` is set, so C1 (`\u0080-\u009F`) would
  have survived into both sinks.
- **Fix:** `Regex("[\\p{Cntrl}\\u0080-\\u009F]")`, with the reason in a comment. The test drives ESC
  (`\u001B`), BEL (`\u0007`) and NEL (`\u0085`) through `report` and asserts no `Char.isISOControl()`
  survives.
- **Files modified:** `src/main/kotlin/com/six2dez/burp/aiagent/mcp/McpBlockedRequestReporter.kt`
- **Commit:** `9f451da`

### Interpretation Calls (not defects, recorded for Plan 03/04)

- **`method` and `path` are sanitized but not hashed.** The plan's action text says "each header value"
  goes through `sanitize` then `Hashing.sha256Hex`, and T-20-11 scopes the mitigation to "reflected
  header values". `method` and `path` are neither; hashing a request path would make a blocked route
  undiagnosable while adding nothing — a `GET`/`POST` hash is trivially reversible from a nine-element
  set. Only `origin`, `host`, `referer` and `userAgent` are hashed. The plan's own `<behavior>` block
  asserts hashing for `origin` only, so no stated assertion changes.
- **Constants grouped at the top of the file** rather than interleaved in the plan's declaration order,
  matching the `mcp/tools/McpTool.kt:18-24` convention the plan itself cites for Task 2.
- **Two KDoc rewordings to satisfy grep-based acceptance criteria.** The criteria
  `grep -c 'System.currentTimeMillis'` = 0, `grep -c 'synchronized'` = 0, and
  `grep -rn "io.ktor.server"` = nothing are whole-file greps that also match explanatory prose. Prose
  mentioning those identifiers was reworded ("read from the system clock inside this class", "monitor
  lock", "No server-side Ktor package is imported here") so the greps measure code, not comments. No
  behaviour change.

### Authentication Gates

None.

## Verification

| Check | Result |
|-------|--------|
| `./gradlew test --tests '*McpAccessControlDecisionTest'` | PASS — 38 tests, 0 failures |
| `./gradlew test --tests '*BlockedRequestReporterTest'` | PASS — 16 tests, 0 failures |
| `./gradlew test --tests 'com.six2dez.burp.aiagent.mcp.*'` | PASS |
| `./gradlew test detekt ktlintCheck` (full gate, run once) | PASS |
| `git diff --stat detekt-baseline.xml` | empty — baseline still 1096 entries (QUAL-07) |
| `grep -rn "createApplicationPlugin\|io.ktor.server" McpAccessControlDecision.kt` | no output — decision core is engine-free |
| whole-file `return`-keyword budget in `McpAccessControlDecision.kt` | 2 (both in `parseAuthority`) |
| `grep -c 'SHUTDOWN_PATH'` / `grep -c 'preserves current semantics'` | 0 / 0 |
| `McpBlockedRequestReporter.kt` contains `synchronized` / `Executors.` / `System.currentTimeMillis` | 0 / 0 / 0 |

All Gradle invocations carried the `JAVA_HOME=$(/usr/libexec/java_home -v 21)` prefix.

## TDD Gate Compliance

Both tasks are `tdd="true"` and both completed a full RED → GREEN cycle. No REFACTOR commit was
needed — neither implementation had duplication to remove once green.

| Task | RED (`test`) | GREEN (`feat`) |
|------|--------------|----------------|
| 1 — decision core | `7d0811f` | `5df910d` |
| 2 — blocked-request reporter | `bd381a1` | `9f451da` |

RED was verified as a genuine failure in both cases (`compileTestKotlin` failed on unresolved
references to the not-yet-written production symbols); no test passed unexpectedly before
implementation.

## Known Stubs

None. Both files are fully implemented; nothing is wired to empty or placeholder data.

## Threat Flags

None. The files introduce no new network endpoint, no file access, and no schema change — they only
decide and report on requests the existing MCP listener already accepts.

## Notes for Plan 03 (Ktor plugin adapter)

- Construct the reporter once per server start, not per request — the D-09 windows live in instance
  state and a per-request instance would print every block.
- Pass the Output sink as `{ line -> api.logging().logToOutput(line) }` and leave `verboseAudit` at its
  `false` default.
- `report` needs the clock: pass `System.currentTimeMillis()` from the plugin, which is where reading it
  is appropriate.
- `evaluate` returns `GateDecision.Deny(status, reason, facts)`; respond with `deny.status` and call
  `report(deny, settings.externalEnabled, nowMs)` before responding.
- The pre-fix `KtorMcpServerManager` predicates (`isAuthorized`, `constantTimeEquals`, `isLoopbackHost`,
  `isValidOrigin`, `isBrowserRequest`, `isValidHost`, `isValidReferer`) are still present and untouched
  in that file. Plan 03 owns removing the now-superseded ones; `/__mcp/shutdown` keeps its own
  in-handler check per D-04, and it should be repointed at `isAuthorizedBearer` so it inherits the
  blank-token guard.

## Self-Check: PASSED

All four created files exist on disk and all four commit hashes resolve in `git log`.
