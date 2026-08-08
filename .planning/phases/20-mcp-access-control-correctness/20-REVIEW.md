---
phase: 20-mcp-access-control-correctness
reviewed: 2026-08-08T12:41:45Z
depth: standard
files_reviewed: 15
files_reviewed_list:
  - build.gradle.kts
  - docs/mcp-hardening.md
  - src/main/kotlin/com/six2dez/burp/aiagent/mcp/KtorMcpServerManager.kt
  - src/main/kotlin/com/six2dez/burp/aiagent/mcp/McpAccessControlDecision.kt
  - src/main/kotlin/com/six2dez/burp/aiagent/mcp/McpAccessControlPlugin.kt
  - src/main/kotlin/com/six2dez/burp/aiagent/mcp/McpBlockedRequestReporter.kt
  - src/main/kotlin/com/six2dez/burp/aiagent/mcp/McpSupervisor.kt
  - src/test/kotlin/com/six2dez/burp/aiagent/mcp/BlockedRequestReporterTest.kt
  - src/test/kotlin/com/six2dez/burp/aiagent/mcp/KtorMcpServerManagerSecurityTest.kt
  - src/test/kotlin/com/six2dez/burp/aiagent/mcp/McpAccessControlDecisionTest.kt
  - src/test/kotlin/com/six2dez/burp/aiagent/mcp/McpAccessControlExternalPipelineTest.kt
  - src/test/kotlin/com/six2dez/burp/aiagent/mcp/McpAccessControlPipelineTest.kt
  - src/test/kotlin/com/six2dez/burp/aiagent/mcp/McpBuildFlagsVersionTest.kt
  - src/test/kotlin/com/six2dez/burp/aiagent/mcp/McpSupervisorProbeTest.kt
  - src/test/kotlin/com/six2dez/burp/aiagent/mcp/McpTestServerSupport.kt
findings:
  critical: 1
  warning: 13
  info: 9
  total: 23
status: issues_found
---

# Phase 20: Code Review Report

**Reviewed:** 2026-08-08T12:41:45Z
**Depth:** standard
**Files Reviewed:** 15
**Status:** issues_found

## Summary

The core SEC-04 fix is sound and I verified its load-bearing claims against the actual
dependency bytecode rather than trusting the comments:

- `PluginBuilder.onCall` in ktor-server-core 3.1.3 does resolve
  `ApplicationCallPipeline.ApplicationPhase.getPlugins()` — the gate really does run before routing
  regardless of install position.
- `RoutingNode$buildPipeline$1$1.invokeSuspend` really does call `PipelineCallKt.isHandled`, and
  `isHandled` really is `response.isCommitted` — so `call.respond(...)` from the `Plugins` phase
  provably skips every registered `handle{}` body without needing `finish()`.
- `CORSKt$buildPlugin$1` really does begin with `PipelineResponse.isCommitted()` and really does
  reject via `respondCorsFailed` **without** finishing the pipeline — the `isCommitted` guard in
  `McpAccessControlPlugin` is genuinely required.
- The MCP SDK's `mcp{}` (`KtorServerKt`) only installs `SSE` and registers `/sse` + `/message` as
  ordinary routes, so there is no sub-pipeline, upgrade path, or static-content path that escapes the
  gate. I found no remaining way to reach a handler without passing the gate.

What I did find: this phase introduces an **unauthenticated, remotely reachable primitive for
unbounded synchronous appends to `~/.burp-ai-agent/audit.jsonl`**, and the code's own written
justification for accepting that risk rests on a premise ("the source is loopback-only") that is
false in exactly the mode where it matters. Beyond that, the authority parser fails *open* in one
place where the file claims fail-closed, the `Host` predicate silently disables itself on every TLS
connector (HTTP/2 has no `Host` header), the most common browser attack (foreign `Origin`) produces
no audit record at all despite the new docs promising one, and two of the phase's own tests assert
materially less than their names and KDoc claim.

Deliberate design decisions listed in the phase brief (D-01, D-02, D-04, D-11, unhashed
`method`/`path`, the local-mode loopback-Origin + browser-UA allow, the `MatchingDeclarationName`
suppression, and the deferred SEC-07 token-to-unidentified-listener issue) are **not** reported as
defects. WR-12 touches SEC-07 only to record a new amplification angle.

## Critical Issues

### CR-01: Unauthenticated remote peers can drive unbounded audit-log growth; the accepted-risk rationale's premise is false

**File:** `src/main/kotlin/com/six2dez/burp/aiagent/mcp/McpBlockedRequestReporter.kt:46-54,80-82`

**Issue:** `report()` emits one `mcp_transport_blocked` event **per blocked request, always** (line
80), and D-09's rate-limit window deliberately covers only the Output line (lines 129-144).
`AuditLogger.emitGlobal` reaches `AuditLogger.logEvent`, which performs a synchronous
`logFile.appendText(...)` to `~/.burp-ai-agent/audit.jsonl` (`audit/AuditLogger.kt:53-70`), and
`App.kt:69` registers that emitter unconditionally.

Before this phase, a blocked MCP transport request emitted **no** audit event, so no such primitive
existed. It does now, and the KDoc's stated justification is:

> "Accepted because the source is loopback-only and async hand-off has no in-repo precedent"

That premise is false for the one configuration this phase exists to harden. In **external mode** the
server is deliberately bound off-loopback, `/__mcp/health` aside every route answers `401`, and every
one of those `401`s is a `BlockReason.UNAUTHORIZED` / `BLANK_TOKEN` denial that writes a JSON line to
the user's home directory. A peer that can reach the port — with **no credential at all** — therefore
controls the append rate to an unbounded file on the operator's disk, one record per request, on a
Netty event-loop thread. That is a remote unauthenticated resource-exhaustion / log-flooding
primitive (CWE-400 / CWE-779) against a security tool's own audit trail: the flood also buries any
genuine event under attacker-chosen noise, which is the audit trail's whole purpose.

Preconditions are the user enabling audit logging and enabling external access; both are supported,
documented configurations (`docs/mcp-hardening.md` is a checklist for exactly that mode), so "off by
default" is not a mitigation — and the KDoc itself says it is stating the audit-ENABLED case.

**Fix:** apply a bound to the audit sink too, rather than only to the Output line. D-06's "on every
occurrence" can be preserved for the *diagnosable* reasons while collapsing the flood-capable ones:

```kotlin
internal fun report(deny: GateDecision.Deny, externalMode: Boolean, nowMs: Long) {
    // Flood-capable reasons are the pre-auth ones: an unauthenticated external peer controls
    // their rate. Coalesce them into one event per window carrying a `suppressed` count; keep
    // per-occurrence emission for the local-mode reasons, which require local code execution.
    val floodCapable = deny.reason == BlockReason.UNAUTHORIZED || deny.reason == BlockReason.BLANK_TOKEN
    val suppressed = if (floodCapable) auditWindow(deny.reason, nowMs) else AuditVerdict.Emit(0L)
    if (suppressed is AuditVerdict.Emit) {
        emitTransportTelemetry(
            MCP_TRANSPORT_EVENT_BLOCKED,
            buildPayload(deny, externalMode) + ("suppressed" to suppressed.count),
        )
    }
    maybeLogBlocked(deny, nowMs)
}
```

If D-06's literal "every occurrence" must not move, the alternative is a hard cap on
`audit.jsonl` size/rotation in `AuditLogger`, plus correcting the KDoc so it no longer justifies the
residual risk with "the source is loopback-only".

## Warnings

### WR-01: `parseAuthority` fails OPEN on an out-of-range port — `toIntOrNull` maps it to "no port"

**File:** `src/main/kotlin/com/six2dez/burp/aiagent/mcp/McpAccessControlDecision.kt:174,190`

**Issue:** `it.groupValues[2].toIntOrNull()` cannot distinguish "no port was present" from "a port was
present but does not fit in an `Int`". `isLoopbackAuthority` then treats `port == null` as "skip the
port comparison" (line 190). Concretely:

```
isLoopbackAuthority("localhost:99999999999", 9876) == true   // port check silently skipped
isLoopbackAuthority("localhost:65536",       9876) == false  // port check applied
```

The KDoc at lines 27-33 and 172-174 claims this shape is "the fail-closed shape without needing an
exception handler". For this input it is fail-*open*: an authority that is malformed by RFC 3986
(port must be 0-65535) is accepted with its port assertion disabled. The existing tests only cover
`"localhost:abc"` (no regex match at all), so the overflow path is untested. Exploitability is low
because the hostname must still be in `LOOPBACK_HOSTS`, but the predicate does not do what the file
says it does, and it is the single shared predicate behind `Host`, `Origin` and `Referer`.

**Fix:** distinguish absent from unparseable, and validate the range:

```kotlin
private fun parseAuthority(value: String): Pair<String, Int?>? {
    val trimmed = value.trim().lowercase()
    if (trimmed in BARE_IPV6_LOOPBACKS) return trimmed to null
    val match = BRACKETED_AUTHORITY_REGEX.matchEntire(trimmed) ?: PLAIN_AUTHORITY_REGEX.matchEntire(trimmed)
    val raw = match?.groupValues?.get(2) ?: return null
    if (raw.isEmpty()) return match.groupValues[1] to null
    val port = raw.toIntOrNull()?.takeIf { it in 1..65_535 } ?: return null // malformed => reject
    return match.groupValues[1] to port
}
```

Add `assertFalse(isLoopbackAuthority("localhost:99999999999", 9876))` and
`assertFalse(isLoopbackAuthority("localhost:65536", null))` to `McpAccessControlDecisionTest`.

### WR-02: Foreign-`Origin` denials produce NO audit event and NO Output line — contradicting the docs this phase added

**File:** `src/main/kotlin/com/six2dez/burp/aiagent/mcp/McpAccessControlPlugin.kt:79` and
`docs/mcp-hardening.md:38`

**Issue:** I confirmed in `CORSKt$buildPlugin$1` that Ktor's CORS plugin answers a disallowed valid
`Origin` itself via `respondCorsFailed` (403) in the same `Plugins` phase, *before* the gate. The
gate's `if (!call.response.isCommitted)` guard then skips the whole body — so `onBlocked` is never
invoked, no `mcp_transport_blocked` event is emitted, and nothing reaches the Output tab. The phase's
own test acknowledges CORS owns this response (`McpAccessControlPipelineTest.kt:156-169`).

But the documentation added in this same phase states the opposite:

> `docs/mcp-hardening.md:38` — "…in local mode a request carrying a foreign `Origin`, a foreign
> `Host`, a foreign `Referer`, or a browser `User-Agent` with no `Origin` returns `403` with an empty
> body. … the reason is recorded in Burp's Output tab and, when audit logging is enabled, in the
> audit log."

For the foreign-`Origin` case — the single most common browser-driven attack, and the one an operator
is most likely to want evidence of — the reason is recorded **nowhere**. An operator following the
Verification checklist will conclude the audit trail is broken, or worse, will trust an absent record
as absence of attack. `BlockReason.ORIGIN_MISMATCH` remains reachable only for values CORS treats as
non-CORS (`Origin: null` from a sandboxed iframe, malformed origins, or multiple `Origin` headers),
which is a much narrower set than the docs imply.

**Fix:** either report from the committed path as well, or correct the doc. Minimal code fix:

```kotlin
onCall { call ->
    if (call.response.isCommitted) {
        // CORS already answered (foreign Origin / preflight). Still record the denial so the
        // audit trail is not silently blind to the most common browser attack.
        if (call.response.status() == HttpStatusCode.Forbidden) {
            onBlocked(GateDecision.Deny(HttpStatusCode.Forbidden, BlockReason.ORIGIN_MISMATCH, requestFacts(call)))
        }
        return@onCall
    }
    ...
}
```

If that is rejected as scope creep, `docs/mcp-hardening.md:38` must be amended to say that a foreign
`Origin` is rejected by CORS and is **not** recorded in the Output tab or the audit log.

### WR-03: The "ORDER IS LOAD-BEARING" CORS/gate install order and the `isCommitted` guard have zero test coverage

**File:** `src/main/kotlin/com/six2dez/burp/aiagent/mcp/KtorMcpServerManager.kt:178-194`

**Issue:** The comment states — correctly — that installing the gate *above* `install(CORS)` breaks
legitimate browser preflights (`401` instead of `200 + Access-Control-Allow-Origin`), and that the
`isCommitted` guard prevents a second `respond` after CORS has already answered. Both properties are
described as "measured" and "verified by execution", but neither is pinned by an automated assertion:

- There is **no** `OPTIONS` preflight test anywhere in the phase's four new test classes. Swapping
  the two `install(...)` calls leaves the entire suite green.
- Deleting the `if (!call.response.isCommitted)` guard also leaves the suite green:
  `foreignOrigin_isRejected` asserts only `403`, and the gate would independently produce `403` for
  the same request (`ORIGIN_MISMATCH`), so the double-respond would only surface as a logged
  `IllegalStateException` that no test inspects.

This is precisely the failure mode the phase was created to fix — a fully green suite over a broken
pipeline. The whole point of extracting `McpTestServerSupport` was to make these assertions cheap.

**Fix:** add two assertions to `McpAccessControlPipelineTest`:

```kotlin
@Test
fun preflight_fromAllowedLoopbackOrigin_isAnsweredByCorsNotTheGate() {
    withLocalServer { baseUrl, client ->
        val port = baseUrl.substringAfterLast(':')
        val req = Request.Builder().url("$baseUrl$MESSAGE")
            .method("OPTIONS", null)
            .header("Origin", "http://localhost:$port")
            .header("Access-Control-Request-Method", "POST")
            .build()
        client.newCall(req).execute().use { r ->
            // Gate-first would make this 403/401 and drop the CORS header.
            assertEquals(200, r.code, "install(CORS) must precede install(McpAccessControl)")
            assertEquals("http://localhost:$port", r.header("Access-Control-Allow-Origin"))
        }
    }
}

@Test
fun foreignOriginPreflight_isRejectedOnceByCorsWithNoGateDoubleRespond() {
    withLocalServer { baseUrl, client ->
        val req = Request.Builder().url("$baseUrl$MESSAGE")
            .method("OPTIONS", null)
            .header("Origin", FOREIGN_ORIGIN)
            .header("Access-Control-Request-Method", "POST")
            .build()
        assertEquals(403, statusOf(client, req))
        // Removing the isCommitted guard makes the gate respond a second time.
        assertTrue(capturedAudit.none { it.first == TRANSPORT_BLOCKED })
    }
}
```

### WR-04: `Host` is read as an HTTP/1 header — the rebinding check silently disables itself on every TLS connector (HTTP/2), and fails open when `Host` is absent

**File:** `src/main/kotlin/com/six2dez/burp/aiagent/mcp/McpAccessControlPlugin.kt:119` and
`src/main/kotlin/com/six2dez/burp/aiagent/mcp/McpAccessControlDecision.kt:150`

**Issue:** `requestFacts` reads `call.request.headers["Host"]`, and `evaluateLocal` gates on
`facts.host != null && !isLoopbackAuthority(...)`. Two problems:

1. **HTTP/2 carries no `Host` header** — the authority travels in the `:authority` pseudo-header. I
   checked `io.ktor.server.netty.http2.NettyHttp2ApplicationRequest` in ktor-server-netty 3.1.3: its
   header map is built directly from Netty's `Http2Headers` with no `Host` synthesis (the authority
   is surfaced separately through `Http2LocalConnectionPoint`). Ktor's Netty engine enables HTTP/2 by
   default on TLS connectors via ALPN. So for any `tlsEnabled` server — which includes the
   `tlsEnabled = true, externalEnabled = false` combination that `KtorMcpServerManager.start`
   explicitly permits — `facts.host` is `null` on every h2 request and the DNS-rebinding branch never
   evaluates. It also means the `host` field of every audit payload is `null` over h2, degrading
   forensics in external mode.
2. **A missing `Host` fails open on HTTP/1.1 too.** Neither Netty's decoder nor Ktor enforces the
   RFC 7230 requirement that HTTP/1.1 requests carry `Host`, so a raw client omitting it skips the
   check entirely. For a gate whose sibling branches are all fail-closed, "absent ⇒ allow" is
   inconsistent.

Both pipeline test classes only ever exercise the header over cleartext HTTP/1.1 (local) or in a mode
that ignores it (external), so neither gap is observable from the suite.

**Fix:** take the authority from the transport-neutral connection point and fail closed:

```kotlin
private fun requestFacts(call: ApplicationCall): RequestFacts =
    RequestFacts(
        // HTTP/2 has no Host header; origin.serverHost/serverPort is derived from :authority
        // on h2 and from Host on HTTP/1.1, so one expression covers both transports.
        host = call.request.headers["Host"] ?: "${call.request.origin.serverHost}:${call.request.origin.serverPort}",
        ...
    )
```

and in `evaluateLocal`, replace `facts.host != null && !isLoopbackAuthority(...)` with
`!isLoopbackAuthority(facts.host.orEmpty(), settings.port)` so an absent authority is a
`HOST_MISMATCH` rather than an allow. Add an external-mode h2 assertion that the audit payload's
`host` is non-null.

### WR-05: The Output line is delimiter-based, so an attacker-controlled header can forge sibling fields

**File:** `src/main/kotlin/com/six2dez/burp/aiagent/mcp/McpBlockedRequestReporter.kt:146-158,165-173`

**Issue:** `sanitize` removes C0/C1 controls (correctly defeating CWE-117 line injection) but the
Output line is an unquoted, space-delimited `key=value` record:

```
[McpAccessControl] Blocked MCP request: reason=... method=... path=... origin=... host=... referer=... ua=...
```

Spaces inside a value survive sanitization (`whitespaceRegex` *collapses* runs of whitespace to a
single space, it does not remove them). So a client sending

```
Origin: http://127.0.0.1:9876 host=127.0.0.1:9876 referer=http://127.0.0.1:9876/ ua=curl/8.4.0
```

produces an Output line in which a human — or any parser — reads a benign loopback `host`,
`referer` and `ua` that were never sent, while the real values (`host=none referer=none ua=…`) appear
later in the line and look like duplicates. The reporter's own test helper `originFieldOf` splits on
`" host="` (`BlockedRequestReporterTest.kt:257`), which demonstrates the format is delimiter-parsed.
In external mode this is reachable by any unauthenticated peer. D-07's stated goal ("a forged second
log line is impossible") is met; forged *fields within* the line are not.

**Fix:** quote and escape each value so the delimiter cannot appear inside one:

```kotlin
/** Quoted so an attacker-controlled value cannot forge a sibling key=value field. */
private fun outputValue(value: String?): String {
    val s = sanitize(value) ?: return "none"
    return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
}
```

Update `BlockedRequestReporterTest.originFieldOf` accordingly and add a case asserting that an
`Origin` containing `" host=x"` does not yield a second parseable `host=` field.

### WR-06: `/__mcp/health` is a hardcoded literal in three places; the "route match" test is a tautology

**Files:** `src/main/kotlin/com/six2dez/burp/aiagent/mcp/McpAccessControlDecision.kt:16`,
`src/main/kotlin/com/six2dez/burp/aiagent/mcp/KtorMcpServerManager.kt:197`,
`src/main/kotlin/com/six2dez/burp/aiagent/mcp/McpSupervisor.kt:257`,
`src/test/kotlin/com/six2dez/burp/aiagent/mcp/McpAccessControlDecisionTest.kt:380-383`

**Issue:** `HEALTH_PATH` exists specifically so the gate's external-mode exemption and the liveness
route cannot drift, and the file's KDoc states the wire-value-defined-once principle for
`BlockReason`. But the route registration (`get("/__mcp/health")`) and the supervisor's probe URL
(`".../__mcp/health"`) each spell the path out again — three independent copies of a string whose
agreement is a security property. Change the route literal alone and the external-mode exemption
stops matching (fail-closed, but takeover breaks silently); change `HEALTH_PATH` alone and the old
path becomes an unauthenticated 404 while the real liveness route becomes `401`-gated, silently
disabling external-mode bind-conflict takeover — the exact failure `McpSupervisorProbeTest` was
written to prevent.

The test that appears to guard this is `healthPath_matchesTheSupervisorLivenessRoute`, but it asserts
`assertEquals("/__mcp/health", HEALTH_PATH)` — a literal against a literal. It does not reference the
route registration or `McpSupervisor` at all, so it cannot detect the drift its name describes.

**Fix:** consume the constant at both remaining sites:

```kotlin
// KtorMcpServerManager.kt
get(HEALTH_PATH) { ... }

// McpSupervisor.kt
val url = URI.create("$scheme://${settings.host}:${settings.port}$HEALTH_PATH").toURL()
```

and delete the tautological test (the pipeline tests already prove the route resolves).

### WR-07: `probeExistingServer_neverSendsAuthorization` does not assert what its name claims

**File:** `src/test/kotlin/com/six2dez/burp/aiagent/mcp/McpSupervisorProbeTest.kt:110-146`

**Issue:** The test's stated purpose is to pin that Phase 20 did not introduce the SEC-07 credential
disclosure ("the probe succeeds … WITHOUT any credential"). What it actually asserts is that the
probe returns `true` and that `GET /__mcp/health` returns `200` without a credential. Both remain
true if someone adds `conn.setRequestProperty("Authorization", "Bearer ${settings.token}")` to
`probeExistingServer` — `/__mcp/health` is exempt from bearer auth by D-01, so the server answers
`200` whether a credential is present or not. The single line the comment at
`McpSupervisor.kt:275-277` most wants protected ("do not add one here") is therefore unprotected.

**Fix:** observe the request, not the response. A `com.sun.net.httpserver.HttpServer` (or an OkHttp
`MockWebServer`, already a test dependency) on the probed port lets the test assert header absence:

```kotlin
@Test
fun probeExistingServer_sendsNoAuthorizationHeader() {
    val server = MockWebServer()
    server.enqueue(MockResponse.Builder().code(200).addHeader("X-Burp-AI-Agent", "mcp").build())
    server.start()
    try {
        val settings = McpTestServerSupport.localSettings(server.port)
        assertTrue(probe(settings))
        val recorded = server.takeRequest()
        assertNull(recorded.headers["Authorization"], "SEC-07/D-05: the probe must present no credential")
    } finally {
        server.shutdown()
    }
}
```

### WR-08: `storeBuild_flagStillGenerated` breaks the BApp Store build path

**File:** `src/test/kotlin/com/six2dez/burp/aiagent/mcp/McpBuildFlagsVersionTest.kt:36-43`

**Issue:** The test hardcodes `assertFalse(BuildFlags.STORE_BUILD)`. `-PstoreBuild=true` is a
first-class, documented build mode (`build.gradle.kts:68-70`, and it selects the
`Custom-AI-Agent` artifact name at `:133-138`) — it is the mode used to produce the BApp Store
artifact currently in submission. Under that flag `BuildFlags.STORE_BUILD` is `true` by design, so
`./gradlew -PstoreBuild=true test` (or `check`, or `build`) now **fails**, in a test whose declared
purpose is merely "proves the added version Property did not disturb the pre-existing store-build
flag". The assertion encodes a build-invocation assumption as a product invariant.

**Fix:** assert the generated flag agrees with the flag actually passed, not with `false`:

```kotlin
@Test
fun storeBuild_flagStillGenerated() {
    // Reads the same property the generator read, so the test is correct under -PstoreBuild=true.
    val expected = System.getProperty("storeBuild.expected")?.equals("true", ignoreCase = true) == true
    assertEquals(expected, BuildFlags.STORE_BUILD)
}
```

with `tasks.test { systemProperty("storeBuild.expected", storeBuild.toString()) }`. If wiring a
system property is unwanted, delete the assertion — `shadowJar`'s artifact naming already covers the
flag, and the version test alone proves the generator still runs.

### WR-09: The project version is interpolated into generated Kotlin source without escaping

**File:** `build.gradle.kts:90-99` (specifically line 96)

**Issue:** `const val VERSION = "${version.get()}"` splices an externally settable string straight
into Kotlin source. `version` is settable from `gradle.properties` or `-Pversion=...`, and any `$`,
`"`, `\` or newline in it either breaks compilation with a message pointing at generated code (no
such file in the repo, so it is hard to diagnose) or injects arbitrary declarations into
`BuildFlags.kt`. A value as ordinary as `1.2.3-$branch` produces a Kotlin template expression rather
than a literal. `storeBuildFlag` is a `Boolean` and is safe; only the new `String` property is
exposed.

**Fix:** escape at generation time and reject anything unexpected:

```kotlin
@TaskAction
fun generate() {
    val raw = version.get()
    require(raw.matches(Regex("""[0-9A-Za-z.\-+]+"""))) { "Unsupported project version for codegen: '$raw'" }
    ...
    const val VERSION = "$raw"
}
```

### WR-10: The probe's new mode-awareness is asymmetric — a local-mode instance can never take over a port held by an external-mode instance

**File:** `src/main/kotlin/com/six2dez/burp/aiagent/mcp/McpSupervisor.kt:285` with
`handleBindFailure` at `:210-215`

**Issue:** The identity assertion is keyed on the **probing** instance's `settings.externalEnabled`,
while the `X-Burp-AI-Agent: mcp` header's presence is decided by the **listening** instance's mode
(`KtorMcpServerManager.kt:202-204`). The change fixes only one direction. In the mirrored case —
a local-mode instance hitting a bind conflict against a port held by an external-mode instance
(two Burp instances / two extension loads with different MCP settings) — the header is absent,
`probeExistingServer` returns `false`, `attemptTakeover` returns `NO_COMPATIBLE_SERVER`, and
`handleBindFailure` logs an error and **schedules no retry at all**. That is verbatim the
"the MCP server would stay down silently" outcome the comment at `:268-271` cites as the reason for
the change.

**Fix:** make the probe's acceptance criterion depend on the observed response rather than on the
probing side's own mode, and log the identity gap either way:

```kotlin
val identified = conn.getHeaderField("X-Burp-AI-Agent") == "mcp"
if (alive && !identified) {
    api.logging().logToOutput(
        "MCP probe on ${settings.host}:${settings.port} could not establish server identity " +
            "(the identifying header is emitted in local mode only). Proceeding on liveness alone.",
    )
}
alive
```

That collapses the two directions to one rule, matches the (2) rationale ("the header was always
trivially spoofable … takes away no guarantee that previously held") and removes the silent
stay-down. If the local-mode identity requirement must be kept, `handleBindFailure`'s
`NO_COMPATIBLE_SERVER` branch should at least schedule a bounded retry instead of giving up.

### WR-11: The docs' external-mode fingerprinting claim is not delivered by the code

**File:** `docs/mcp-hardening.md:17`

**Issue:** Item 5 asserts that because `X-Burp-AI-Agent: mcp` is local-mode-only, "an unauthenticated
scan of an externally exposed port cannot confirm which extension is listening." The code does not
support that conclusion. In external mode an unauthenticated peer observes:
`GET /__mcp/health` → `200` with body exactly `ok`; every other path → `401` with an empty body and
`X-Frame-Options: DENY`, `X-Content-Type-Options: nosniff`, `Referrer-Policy: same-origin`,
`Content-Security-Policy: default-src 'none'`. That tuple — a `/__mcp/`-prefixed health route that is
the sole unauthenticated `200 ok` behind an otherwise blanket `401` — is a decisive fingerprint. The
header removal raises the cost of confirmation slightly; it does not prevent it.

Overstating a security guarantee in the hardening checklist is a defect in its own right: an operator
may skip network-level restriction of the port on the strength of it.

**Fix:** state the residual accurately, e.g. "…so the response no longer *names* the extension.
Note that the `/__mcp/health` route itself remains an unauthenticated `200` and is a fingerprint;
restrict access to the port at the network layer if fingerprinting is in your threat model."

### WR-12: New angle on the deferred SEC-07 issue — trust-all TLS on loopback hands the bearer token to any local listener

**File:** `src/main/kotlin/com/six2dez/burp/aiagent/mcp/McpSupervisor.kt:294-347`

**Issue:** Reported only as a new angle on the explicitly deferred Finding 7 / SEC-07, not as a
re-report. External mode now proceeds to takeover on liveness alone (`:285`), and
`requestRemoteShutdownWithToken` then presents `Authorization: Bearer ${settings.token}` to whatever
holds the port. External mode forces `tlsEnabled`, so one might assume certificate validation limits
the exposure — but `openConnection` (`:319-346`) installs a **trust-all** `X509TrustManager` and a
`HostnameVerifier { _, _ -> true }` whenever `url.host` is loopback. External mode with
`host = 127.0.0.1` (fronted by a reverse proxy — the configuration this repo's own tests use) is
therefore the *worst* case, not the safest: any unprivileged local process that grabs the port and
presents any self-signed certificate receives the MCP bearer token in cleartext-equivalent form. The
new probe path makes reaching that code strictly easier than before, since the identity header no
longer has to be spoofed.

**Fix:** for Phase 25 / SEC-07, do not gate the credential on TLS. Establish listener identity before
`requestRemoteShutdownWithToken` — e.g. a challenge-response over `/__mcp/health` proving knowledge
of the token, or an OS-level owner check on the socket — and narrow the trust-all block to a keystore
pinned to the extension's own auto-generated certificate rather than accepting any chain.

### WR-13: `server` is a non-volatile `var` mutated on the executor thread and read from caller threads

**File:** `src/main/kotlin/com/six2dez/burp/aiagent/mcp/KtorMcpServerManager.kt:44,273-281,288-295`

**Issue:** Pre-existing (unchanged by this phase) but inside a submitted file and load-bearing for
port release. `server` is written from the single-thread executor (`:76,240,261`) and read/written
from the calling thread in `stop()`'s timeout branch (`server?.stop(0, 0); server = null`) and in
`shutdown()` (`server?.stop(...)`). With no `@Volatile` and no synchronization there is no
happens-before edge between the executor's write and the caller's read, so `shutdown()` can observe a
stale `null` and leave a bound Netty server — leaking the MCP port for the remainder of the JVM's
life, which then manifests as a `BindException` and drives the takeover path reviewed above.

**Fix:** `private val server = AtomicReference<EmbeddedServer<*, *>?>(null)` (or `@Volatile`), and use
`getAndSet(null)?.stop(...)` so the stop is performed exactly once by whichever thread wins.

## Info

### IN-01: `appendSecurityHeaders`'s duplicate guard is dead code and its rationale is inverted

**File:** `src/main/kotlin/com/six2dez/burp/aiagent/mcp/McpAccessControlPlugin.kt:125-135`

The KDoc says the `if (call.response.headers[name] == null)` check "keeps a future route which sets
one of these itself from producing a doubled header". Because the gate runs in `Plugins`, strictly
before routing, no handler has run when this executes, so the condition is always true — and a future
route setting one of the four headers would append it *after* the gate, producing exactly the doubled
header the guard claims to prevent. The check is dead and the comment describes the opposite of what
it does. Either drop the guard (and say so), or move deduplication to an `onCallRespond` hook.

### IN-02: `verboseAudit = true` is unreachable from production code

**File:** `src/main/kotlin/com/six2dez/burp/aiagent/mcp/McpBlockedRequestReporter.kt:58,112`

The only `src/main` construction site (`KtorMcpServerManager.kt:125`) omits the parameter, so the
plaintext branch of `auditValue` exists solely for tests. The KDoc explains why no user-facing toggle
was added, which is fine, but the seam should be marked as test-only (or the plan should record it as
a deliberate dead branch) so a future reader does not assume a wired feature.

### IN-03: Deny responses carry none of the four security headers

**File:** `src/main/kotlin/com/six2dez/burp/aiagent/mcp/McpAccessControlPlugin.kt:81-102`

`appendSecurityHeaders` is called only from the `Allow` branch, so `401`/`403` responses ship without
`X-Content-Type-Options`, `X-Frame-Options`, `Referrer-Policy` or `Content-Security-Policy`. The
bodies are empty so the practical risk is negligible, but `docs/mcp-hardening.md:40` invites the
operator to verify header presence on "responses from routes the server resolves", and a denial is
the response an operator is most likely to inspect. Moving the call above the `when` costs nothing.

### IN-04: `sanitize` gaps — Unicode line separators survive, and truncation happens before hashing

**File:** `src/main/kotlin/com/six2dez/burp/aiagent/mcp/McpBlockedRequestReporter.kt:112,165-173`

`controlCharRegex` covers C0, DEL and C1; Java's `\s` (without `UNICODE_CHARACTER_CLASS`) does not
match U+0085, U+00A0, U+2028 or U+2029, and the control regex does not match U+2028/U+2029 either, so
Unicode line/paragraph separators reach both sinks. Jackson escapes them in the JSONL record and
Swing's document model only breaks on `\n`, so this is cosmetic today — but it is the same class of
issue D-07 targets. Separately, `auditValue` truncates to 200 chars *before* hashing, so two distinct
long header values that share a 200-char prefix produce identical hashes, silently merging distinct
attacker inputs in the audit trail. Consider hashing the untruncated value and truncating only the
Output-tab rendering.

### IN-05: Only the first `Origin`/`Host` header value is inspected

**File:** `src/main/kotlin/com/six2dez/burp/aiagent/mcp/McpAccessControlPlugin.kt:118-119`

`headers["Origin"]` returns the first value. I confirmed Ktor's CORS plugin uses
`headers.getAll(Origin)?.singleOrNull()`, i.e. it treats a request with **two** `Origin` headers as
"not a CORS request" and passes it through untouched. The gate then judges only the first value, so
`Origin: http://localhost:9876` followed by `Origin: http://evil.example` is allowed. Not exploitable
(no browser emits duplicate `Origin`, and a raw client can simply omit the header for the same
outcome), but the fail-closed posture argues for `headers.getAll(name)?.singleOrNull()` plus an
explicit deny when more than one value is present.

### IN-06: `isLoopbackUrlAuthority` ignores the scheme and accepts userinfo

**File:** `src/main/kotlin/com/six2dez/burp/aiagent/mcp/McpAccessControlDecision.kt:199-204`

`URI("http://evil.example@localhost").host` is `localhost`, so an authority with userinfo is accepted;
the scheme is also unchecked, so `ftp://localhost` or any scheme passes. Browsers strip credentials
from `Origin`/`Referer` and never send a non-http(s) origin, so this is not reachable in the threat
model the predicate defends — but rejecting a non-null `URI.userInfo` and restricting the scheme to
`http`/`https` costs two lines and removes the class entirely.

### IN-07: Test hygiene — leaked temp keystores, a brittle version assertion, and a one-line wrapper

**Files:** `src/test/kotlin/com/six2dez/burp/aiagent/mcp/McpSupervisorProbeTest.kt:73,116`,
`src/test/kotlin/com/six2dez/burp/aiagent/mcp/McpBuildFlagsVersionTest.kt:29-33`,
`src/main/kotlin/com/six2dez/burp/aiagent/mcp/McpBlockedRequestReporter.kt:114-119`

`McpSupervisorProbeTest` creates `Files.createTempDirectory("mcp-probe-ks")` in two tests and never
deletes either, unlike `McpAccessControlExternalPipelineTest` which cleans up in `@AfterAll`; each run
leaves PKCS12 material in the system temp directory. `assertNotEquals("0.6.0", BuildFlags.VERSION)`
encodes a specific historical release as forbidden, which will misfire on any maintenance branch that
legitimately builds `0.6.0`; asserting the semver shape alone is sufficient. `emitTransportTelemetry`
adds a private one-line indirection over `AuditLogger.emitGlobal` with a single caller and no
behaviour of its own.

### IN-08: The rate-limit sentinel makes `nowMs == 0` never suppress

**File:** `src/main/kotlin/com/six2dez/burp/aiagent/mcp/McpBlockedRequestReporter.kt:135-138`

`0L` doubles as "never logged", and `compareAndSet(0L, 0L)` succeeds, so a caller that passes
`nowMs = 0` prints a detail line for every block instead of suppressing. Production passes
`System.currentTimeMillis()`, so this is test-facing only, but it makes `nowMs = 0` a trap for a
future test author. A separate `AtomicBoolean everLogged`, or `Long.MIN_VALUE` as the sentinel with
the subtraction reordered, removes the overload.

### IN-09: The aggregate Output line undercounts blocks by one and drops the current block's detail

**File:** `src/main/kotlin/com/six2dez/burp/aiagent/mcp/McpBlockedRequestReporter.kt:138-155`

When the window has elapsed and `suppressedCount > 0`, the aggregate line is printed *instead of*
this block's detail line, and the count it reports is the number of previously suppressed blocks —
the current block is neither detailed nor counted. For N blocks in a burst plus one after the window,
the Output tab reports `1` detail + `"N-1 further blocks"`, i.e. N of N+1. Under a sustained flood
only aggregate lines are ever printed, so an operator never sees the fields of a current denial again.
`BlockedRequestReporterTest:196-207` pins the undercount (`2` for four blocks) as expected. Either
count the current block (`getAndSet(0L) + 1`) or emit both lines.

---

_Reviewed: 2026-08-08T12:41:45Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
