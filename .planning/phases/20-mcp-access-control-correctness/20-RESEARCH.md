# Phase 20: MCP Access-Control Correctness - Research

**Researched:** 2026-08-05
**Domain:** Ktor 3.1.3 server pipeline semantics, MCP Kotlin SDK 0.5.0 route registration, JVM HTTP test tooling
**Confidence:** HIGH — every load-bearing claim below was verified either by decompiling the exact jars in
`~/.gradle/caches/modules-2/files-2.1` or by executing probe code against a real Netty server on this machine
with this repo's classpath (JDK 21, Gradle 8.12.1). Claims that are inference are labelled inline.

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

**Route exemptions**
- **D-01:** `/__mcp/health` stays **exempt from bearer auth in external mode** so
  `McpSupervisor.probeExistingServer` keeps working without putting the token on the wire during a
  probe. It responds `200 "ok"`.
- **D-02:** The identifying header `X-Burp-AI-Agent: mcp` is emitted **only in local mode**. In
  external mode an unauthenticated scan must not be able to confirm a Burp AI Agent is behind the
  port. This is the anti-fingerprinting half of D-01 — the exemption is for liveness, not identity.
- **D-03:** In **local** mode the Origin/Host/Referer/browser-UA heuristics **do apply to
  `/__mcp/health`**. Today `Origin: http://evil.example` gets `200` there, which lets any page the
  user visits fingerprint the extension. The takeover probe is server-side and sends no `Origin`, so
  applying the checks does not break it.
- **D-04:** `/__mcp/shutdown` **keeps its in-handler token check** in addition to the corrected gate.
  Defence in depth — that check is literally what saved this endpoint from the bypass, so removing it
  now would mean relying entirely on the mechanism that just failed. Double-validation in external
  mode is accepted.

**Boundary with Phase 25**
- **D-05:** Phase 20 **preserves** takeover, Phase 25 **reworks** it. This phase does no more than
  guarantee the probe keeps functioning (via D-01). Every decision about establishing listener
  identity and about whether the token may be presented at all belongs to Phase 25 (SEC-07). Keeps
  the two security diffs separate and independently auditable.

**Blocked-request observability**
- **D-06:** A blocked request emits **both** an `AuditLogger` event and an Output-tab line. The audit
  event reuses the established `MCP_TOOL_EVENT_BLOCKED` payload shape from
  `mcp/tools/McpTool.kt:135-158` (`reason` field: `origin_mismatch`, `host_mismatch`,
  `referer_mismatch`, `browser_no_origin`, `unauthorized`). Both destinations are required because
  audit logging is **disabled by default** in this project — an audit-only event would be invisible
  to most users.
- **D-07:** Reflected header values (`Origin`, `Host`, `Referer`, `User-Agent`) are **sanitized and
  truncated** before reaching either destination: strip CR/LF and control characters (prevents log-line
  injection and ANSI escapes in Burp's Output tab), then cap at a fixed length. The current code
  interpolates the raw attacker-controlled value.
- **D-08:** The response to a blocked client is a **bare status with no body** — `401` in external
  mode without a valid token, `403` in local mode for an Origin/Host/Referer/UA violation. No reason
  string, no `WWW-Authenticate`. Diagnosis lives in the local log, not in the response. This matches
  what the code already does; it is now a deliberate decision rather than an accident.
- **D-09:** Blocked-request logging is **rate-limited by aggregation**: log the first block of each
  reason immediately, then aggregate ("N further blocks for origin_mismatch in the last 60s"). Reuse
  the existing project pattern — `maybeLogBackoff` in `scanner/PassiveAiScannerAnalysis.kt` and the
  `availabilityLogged` `AtomicBoolean` in `backends/cli/CliBackend.kt:28`. Prevents a request loop
  from flooding the Output tab and burying everything else.

### Claude's Discretion
- **Gate mechanism.** Hand-rolled interceptor moved to an earlier phase
  (`ApplicationCallPipeline.Plugins`), an `ApplicationPlugin` with an `onCall` hook, or Ktor's
  `Authentication` plugin (adds a `ktor-server-auth` dependency).
  *Recommendation:* an `ApplicationPlugin` with `onCall`. It is order-independent by construction, so
  the class of bug being fixed cannot recur through a future re-ordering of the `embeddedServer`
  block — which a simple "move the `intercept` call above `routing{}`" fix would leave possible.
  Research must confirm it composes correctly with the CORS plugin and with the MCP SDK's `mcp{}`.
  → **Research verdict: recommendation CONFIRMED, with three mandatory implementation details.
  See "Decision 1" below.**
- **Local-mode authentication.** Whether local (loopback) mode should also require the bearer token,
  not just the Origin/Host heuristics.
  *Recommendation:* do **not** add it in this phase.
  → **Research verdict: recommendation UPHELD. See "Decision 2" below.**

### Deferred Ideas (OUT OF SCOPE)
- **Local-mode bearer token** — recommended against for this phase; revisit if research shows real
  MCP clients tolerate it. Would be its own change, not a correctness fix.
- **Takeover identity / token disclosure (Finding 7)** — Phase 25, SEC-07. D-05 is the explicit boundary.
- **`openConnection` loopback trust-all** — Phase 25 SC5.
- **A test that enforces mutation tools are marked `unsafeOnly`** — Phase 26 if at all.
</user_constraints>

---

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| SEC-04 | Every MCP request is subject to access-control checks before any handler runs; 401 on unauthenticated external `POST /message` and SSE connect; 403 on local foreign Origin/Host/browser-UA; four security headers on matched-route responses; regression tests that fail pre-fix. | "Decision 1" (gate mechanism, verified), "Pipeline Mechanics" (why the bug exists), "Pre-Fix Behaviour Baseline" (exactly which assertions go red pre-fix), "Security Headers" (verified header placement). |
| SEC-05 | Real build version instead of hardcoded `0.6.0`; `isValidHost` handles bracketed IPv6 consistently with `isLoopbackHost` accepting `::1`; a blank bearer token cannot authenticate. | "SEC-05 Sub-Items" — verified reproduction of all three, the `GenerateBuildFlagsTask` seam, the correct IPv6 authority parse, and where the blank-token guard belongs. |
</phase_requirements>

---

## Summary

The defect is fully explained by three facts in Ktor 3.1.3, all confirmed by decompiling the jars on disk.
(1) `ApplicationCallPipeline`'s phases are fixed at construction — `Setup, Monitoring, Plugins, Call, Fallback`
— so the *Plugins* phase always precedes the *Call* phase regardless of registration order.
(2) `RoutingRoot.Plugin.install` registers its interceptor in the **Call** phase at the moment of the first
`routing{}` call, and that interceptor never calls `finish()`.
(3) The extension's security block is also in the **Call** phase but registered *after* `routing{}`, so it runs
after routing has already served the request. Everything the code review reported follows from this.

The fix is to move the checks into the **Plugins** phase. The cleanest way is `createApplicationPlugin(name) { onCall { … } }`
— `PluginBuilder.onCall` is hard-wired to `ApplicationCallPipeline.ApplicationPhase.Plugins`, so the plugin is
order-independent by construction, which is exactly the structural property the maintainer asked for. It was
verified end-to-end: a plugin installed *after* `routing{}` and after `mcp{}` still gated `GET /sse`, `POST /message`
and `/__mcp/health`, and the route handler provably never ran (route-side flag still `false` 700 ms later). The
mechanism that makes the short-circuit work is `RoutingNode.buildPipeline`, which wraps every `handle{}` body in
`if (call.isHandled) return`, and `isHandled == response.isCommitted`.

Three findings will change the plan materially. **First:** the MCP SDK registers `GET /sse` and `POST /message` —
**not** an SSE endpoint at the root path as ROADMAP SC1/SC2 assume. **Second:** `HttpURLConnection` — the client
used by the existing `McpServerIntegrationTest` — *silently drops the `Origin` header* and always writes its own
`Host`, so SC2's Origin/Host assertions cannot be written with it; OkHttp 5.4.0 (already on the test classpath)
must be used. **Third:** Ktor's own CORS plugin *already* returns 403 for a foreign `Origin` today, so an
"Origin → 403" test **passes pre-fix** and therefore does **not** satisfy SC4. The assertions that actually go red
pre-fix are enumerated below; the planner must build the SC4 gate from those and only those.

**Primary recommendation:** extract an `McpAccessControlPlugin` (`createApplicationPlugin` + `onCall`) into its own
file, install it *after* `install(CORS)` and *before* `routing{}`, make its first statement
`if (call.response.isCommitted) return@onCall`, respond-and-return on denial, append the four security headers on
the allow path, and drive the regression tests from a real Netty server (cleartext for local mode, TLS for external
mode) with OkHttp.

---

## Architectural Responsibility Map

Single-process JVM extension; "tiers" here are layers inside the Burp extension process.

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Transport authn/authz (bearer, Origin/Host/Referer/UA) | Ktor application pipeline, **Plugins** phase | — | Must run before any route resolves; only the application pipeline sees every request including SDK-registered routes. |
| Security response headers | Ktor application pipeline, **Plugins** phase | — | Verified: headers appended pre-routing land on the routed response *and* on the SSE streaming response. Appending post-commit is unreliable. |
| CORS / preflight | Ktor `CORS` plugin (Plugins phase) | — | Already correct; owns `Origin` allow-listing and OPTIONS. Do not reimplement in the gate. |
| MCP protocol framing, `sessionId` handshake | `kotlin-sdk` `Application.mcp` (routing) | — | SDK-owned; the gate sits in front of it, never inside it. |
| Tool enable / unsafe gating | `mcp/tools/McpTool.kt::runTool` | — | Separate control layer, verified sound in the review; **unchanged by this phase**. |
| Bind-conflict takeover / liveness probe | `mcp/McpSupervisor.kt` | `/__mcp/health` route | D-01/D-02 constrain the contract; Phase 25 owns redesign. |
| Settings snapshot (token, mode, TLS) | `config/AgentSettingsRepository` | `McpSettings` value object | The gate must treat the snapshot as immutable input; `McpSupervisor.applySettings` restarts on change. |
| Build-time constants (version, store flag) | Gradle `GenerateBuildFlagsTask` → `BuildFlags.kt` | — | Only existing seam for compile-time values; configuration-cache safe today. |
| Blocked-request observability | `audit/AuditLogger.emitGlobal` + `api.logging().logToOutput` | rate-limiter in the gate | D-06 requires both destinations because audit is off by default. |

---

## Pipeline Mechanics — Why the Bug Exists (all VERIFIED by decompilation)

Artifacts inspected (jadx 1.x / `javap`, from `~/.gradle/caches/modules-2/files-2.1`):
`ktor-server-core-jvm-3.1.3.jar`, `ktor-utils-jvm-3.1.3.jar`, `ktor-server-cors-jvm-3.1.3.jar`,
`ktor-server-sse-jvm-3.1.3.jar`, `kotlin-sdk-jvm-0.5.0.jar`.

| # | Fact | Evidence |
|---|------|----------|
| M1 | `ApplicationCallPipeline` is constructed with `super(Setup, Monitoring, Plugins, Call, Fallback)`. Phase order is fixed at construction and is independent of registration order. | `javap -c io/ktor/server/application/ApplicationCallPipeline.class` — constructor builds a 5-element `PipelinePhase[]` in that literal order. `[VERIFIED: ktor-server-core-jvm-3.1.3.jar]` |
| M2 | `PluginBuilder.onCall(block)` calls `onDefaultPhase(callInterceptions, ApplicationCallPipeline.ApplicationPhase.getPlugins(), "onCall", …)`. **`onCall` is the Plugins phase.** | decompiled `PluginBuilder.java:113-115`. `[VERIFIED]` |
| M3 | `RoutingRoot.Plugin.install(pipeline, configure)` ends with `pipeline.intercept(ApplicationCallPipeline.ApplicationPhase.getCall(), install$1)`. Registration happens at the *first* `routing{}` call. | decompiled `RoutingRoot.java:295-301`. `[VERIFIED]` |
| M4 | `RoutingRoot.interceptor` resolves the route and calls `executeResult`; there is **no** `finish()` and **no** `isHandled` check in it. | decompiled `RoutingRoot.java:106-247`. `[VERIFIED]` |
| M5 | `RoutingNode.buildPipeline` wraps every registered handler in `if (PipelineCallKt.isHandled(call)) return Unit` before invoking it. | `javap -c RoutingNode$buildPipeline$1$1.class`, bytecode offsets 93-99 (`invokestatic PipelineCallKt.isHandled` → `ifeq`). `[VERIFIED]` |
| M6 | `PipelineCallKt.isHandled(call) == call.response.isCommitted`. | `javap -c PipelineCallKt.class`. `[VERIFIED]` |
| M7 | `Application.mcp { }` calls `install(SSE)` then `routing { sse("/sse", …); post("/message", …) }`. **The SSE endpoint is `/sse`, not `/`.** (The `Routing.mcp` overload registers at the current route root — the code uses the `Application` overload.) | decompiled `KtorServerKt.java:88-103`. `[VERIFIED: kotlin-sdk-jvm-0.5.0.jar]` |
| M8 | Ktor's `CORS` is `createRouteScopedPlugin("CORS", …)` whose builder calls `PluginBuilder.onCall` → also the **Plugins** phase. So CORS and the new gate share a phase; their relative order is install order. | `javap -c io/ktor/server/plugins/cors/CORSKt.class` (offset 1317: `PluginBuilder.onCall`). `[VERIFIED]` |
| M9 | `CallContext.finish$ktor_server_core()` carries the Kotlin `internal` name-mangling suffix → **not callable from `onCall`**. `PipelineContext.finish()` is `public abstract` → callable from `intercept(...)`. | `javap -p CallContext.class`, `javap -p io/ktor/util/pipeline/PipelineContext.class`. `[VERIFIED]` |

**Consequence chain:** the extension registers `routing{}` at `KtorMcpServerManager.kt:154` (→ M3 installs the
Call-phase routing interceptor), then `intercept(ApplicationCallPipeline.Call)` at `:176` (same phase, later in
registration order → runs second), then `mcp{}` at `:220` (→ M7, adds routes to the *already installed*
`RoutingRoot`, inheriting position). Because of M4 the routing interceptor does not stop the pipeline, and because
of M5/M6 the security block's `call.respond` arrives after the response is already committed. Only unmatched paths
fall through to it — which is exactly the observed `GET /unmatched → 401`.

---

## Pre-Fix Behaviour Baseline (VERIFIED BY EXECUTION)

Measured by starting the **real** `KtorMcpServerManager` (deep-stub `MontoyaApi`, `PrivacyMode.STRICT`) on a free
port and driving it with OkHttp 5.4.0. Local mode = cleartext; external mode = TLS with an auto-generated PKCS12
in a temp dir, trust-all client. Probe code was deleted after the run; `git status` is clean.

### Local mode (`externalEnabled = false`, http)

| Request | Today | Should be after fix |
|---------|-------|---------------------|
| `GET /__mcp/health` | `200 "ok"`, **no** security headers, `X-Burp-AI-Agent: mcp` | 200 + 4 headers + agent header (local) |
| `GET /__mcp/health` + `Origin: http://evil.example` | **403** — emitted by **Ktor's CORS plugin**, not by the extension's `isValidOrigin` | 403 (now also from the gate) |
| `GET /__mcp/health` + `Host: evil.example` | **200** | **403** ← fails pre-fix |
| `GET /__mcp/health` + `Referer: http://evil.example/x` | **200** | **403** ← fails pre-fix |
| `GET /__mcp/health` + `User-Agent: Mozilla/5.0 Chrome/120` | **200** | **403** ← fails pre-fix |
| `POST /message` | `400 "sessionId query parameter is not provided"` (handler ran) | 400 is acceptable in local mode with a good UA; with a browser UA → 403 |
| `POST /message` + browser UA | **400** (handler ran) | **403** ← fails pre-fix |
| `GET /sse` + browser UA | **200 `text/event-stream`** (session created) | **403** ← fails pre-fix |
| `GET /nope` | 404 **with all four security headers** | 404 (headers optional) |
| `POST /__mcp/shutdown` no token | 401 | 401 (SC6 — must not regress) |

### External mode (`externalEnabled = true`, TLS, token set, **no** `Authorization`)

| Request | Today | Should be after fix |
|---------|-------|---------------------|
| `GET /__mcp/health` | `200 "ok"` + `X-Burp-AI-Agent: mcp` | 200 (D-01) but **without** the agent header (D-02) ← fails pre-fix |
| `POST /message` | **`400 "sessionId query parameter is not provided"`** | **401** ← fails pre-fix |
| `GET /sse` | **`200 text/event-stream`** | **401** ← fails pre-fix |
| `GET /nope` | 401 | 401 |
| `POST /message` + `Authorization: Bearer <valid>` | 400 + all four security headers (over HTTP/2; **absent** over cleartext HTTP/1.1 in the same code path — see Pitfall P3) | 400 + headers, deterministically |
| `POST /__mcp/shutdown` no token | 401 | 401 (SC6) |

### Predicate probes (reflection, real class)

| Call | Result | Meaning |
|------|--------|---------|
| `isAuthorized("Bearer ", "")` | **true** | F19 confirmed — a blank token authenticates. |
| `isAuthorized("Bearer", "")` | false | Only the trailing-space form matches. |
| `isValidHost("[::1]:9876", 9876)` | false | F12 confirmed. |
| `isValidHost("::1", 9876)` | false | Bare IPv6 also rejected. |
| `isValidHost("localhost:9876", 9876)` | true | Baseline. |
| `isValidOrigin("http://[::1]:9876")` | **false** | **Not in SEC-05's text but the same defect class** — `URI.toURL().host` returns `[::1]` *with* brackets, so the equality check fails. `isValidReferer` has the identical body and the identical bug. |

**Planner-critical consequence for SC4:** these are the *only* assertions that go red against today's code:

1. external `POST /message` no auth → 401
2. external `GET /sse` no auth → 401
3. external `GET /__mcp/health` → response must **not** carry `X-Burp-AI-Agent`
4. local `GET /__mcp/health` 200 → must carry `X-Frame-Options`, `X-Content-Type-Options`, `Referrer-Policy`, `Content-Security-Policy`
5. local foreign `Host` → 403
6. local foreign `Referer` → 403
7. local browser `User-Agent` without `Origin` → 403 (on `/__mcp/health`, `/message`, `/sse`)
8. `isValidHost("[::1]:9876", 9876)` → true after fix
9. `isAuthorized("Bearer ", "")` → false after fix

**Not valid SC4 gates (they pass pre-fix — keep the tests, do not count them):** foreign `Origin` → 403
(Ktor CORS already does this); `GET /nope` → 401 in external mode; `/__mcp/shutdown` 401/200.

---

## Decision 1 — Gate Mechanism (RECOMMENDED)

**Use `createApplicationPlugin("McpAccessControl", ::McpAccessControlConfig) { onCall { … } }`, installed after
`install(CORS)` and before `routing{}`.** CONTEXT's recommendation is confirmed.

### Why it is correct

| Property | Evidence |
|----------|----------|
| Runs before routing regardless of install order | M1 + M2. Empirically: a plugin installed **after** `routing{}` still gated `/probe`, and installed after `mcp{}` still gated `/sse` and `/message`. `[VERIFIED by execution]` |
| Responding short-circuits the route handler | M5 + M6. Empirically: route-side `AtomicBoolean` still `false` **700 ms after** the client received the 401, for both `onCall` and `intercept(Plugins)`. `[VERIFIED by execution]` |
| Composes with the MCP SDK | `GET /sse` → 401, `POST /message` → 401, `GET /` → 401, `GET /__mcp/health` + valid auth → 200 with `X-Frame-Options`. `[VERIFIED by execution]` |
| Composes with CORS | See "CORS Interaction" below — CORS first, gate second, gate bails on `isCommitted`. `[VERIFIED by execution]` |
| Headers survive SSE | Authorized `GET /sse` → `200 text/event-stream` carrying `X-Frame-Options: DENY` and `Content-Security-Policy: default-src 'none'`. `[VERIFIED by execution]` |

### Mandatory implementation details (each one is load-bearing)

1. **First statement must be `if (call.response.isCommitted) return@onCall`.**
   Verified: with two Plugins-phase interceptors where the first responds, the **second still executes**
   (`secondRan = true`). Without this guard the gate would double-respond after CORS answered a preflight or
   rejected an origin. (With `finish()` the second does not run — but see #2.)
2. **Do not attempt `finish()` inside `onCall`.** `CallContext.finish$ktor_server_core` is Kotlin-`internal`
   (M9). `call.respond(...)` + `return@onCall` is sufficient because of M5/M6.
3. **Append the four security headers on the allow path**, inside the same `onCall`, before returning. Do not
   use `onCallRespond` — per the `PluginBuilder` decompile it attaches to `ApplicationSendPipeline.Transform`,
   which is the wrong seam for static headers and would also run for the 401/403 responses.
4. **Pass the settings snapshot through the plugin config**, not via closure capture, so the gate is unit-testable
   in isolation and the `McpSettings` immutability is explicit. `createApplicationPlugin(name, ::Config) { … }`.
5. **Return a typed decision, not a cascade of early returns.** detekt's `ReturnCount` is one of the top
   baseline offenders (133 entries) and QUAL-07 forbids growing the baseline. A `sealed`/`enum` `GateDecision`
   (`Allow`, `Deny(status, reason)`) computed by a pure function, with a single `when` at the call site, is
   both testable and detekt-friendly, and matches the codebase's "fail closed and return a typed result"
   convention noted in CONTEXT.

### Alternatives evaluated

| Option | Verdict | Reason |
|--------|---------|--------|
| `intercept(ApplicationCallPipeline.Plugins) { …; finish() }` as the first statement of the module | Works, not recommended | Verified functional (401 + handler skipped + later same-phase interceptors skipped). But it is order-sensitive again relative to CORS: placed **before** CORS it returns 401 to a legitimate preflight (verified). It reintroduces exactly the fragility the maintainer wants removed. |
| Move the existing `intercept(ApplicationCallPipeline.Call)` above `routing{}` | Works, rejected | Verified it would fix the symptom, but it is a line-ordering fix — a future edit reorders the module lambda and the bug silently returns. Explicitly rejected by the CONTEXT framing. |
| `ktor-server-auth` `Authentication` plugin | Rejected | Its enforcement model is route-scoped (`authenticate { … }` wraps routes); the SDK's `mcp{}` builds its own routes on the application's `RoutingRoot` and gives no hook to wrap them. Adds a dependency for no gain. `[ASSUMED — ktor-server-auth is not in the local cache and was not decompiled; the route-scoping claim is from the plugin's public API shape, not verified this session.]` |

---

## Decision 2 — Local-Mode Bearer Token (UPHOLD "do not add")

No evidence was found that would overturn the CONTEXT recommendation, and one new argument supports it: the
transport gate now becomes the *only* thing between a local client and the tools. Adding a token requirement in
the same change would make it impossible to tell, from a user bug report, whether a broken Claude Desktop /
Codex CLI config is caused by the ordering fix or by the new auth requirement. Keep the two changes separable.
`[ASSUMED — no MCP client configuration was tested this session.]`

---

## CORS Interaction (VERIFIED BY EXECUTION)

CORS and the gate both live in the **Plugins** phase (M8); relative order = install order.

| Arrangement | Legit preflight (allowed origin + `Access-Control-Request-Method`) | Preflight, foreign origin | `GET` no auth | `GET` with auth |
|-------------|------|------|------|------|
| **Gate first**, CORS second | **401** ← breaks browser CORS | 401 | 401 | 200 |
| **CORS first**, gate second **with `isCommitted` guard** | **200 + `Access-Control-Allow-Origin`** | 403 (CORS) | 401 (gate) | 200 |

**Recommendation: keep `install(CORS)` first, install the gate immediately after it, guard on `isCommitted`.**
This preserves preflight, preserves CORS's own 403, and still gates every real request. This is also the minimal
diff to the existing module lambda.

**Open question to surface to the maintainer:** with CORS first, an **unauthenticated** `OPTIONS` preflight is
answerable in external mode, so an attacker can enumerate the allow-listed origins — and when
`allowedOrigins` is empty the code calls `anyHost()` (`KtorMcpServerManager.kt:135-136`), so *every* preflight
succeeds. That is a fingerprinting channel which partially defeats D-02's intent. Closing it means 401-ing
`OPTIONS`, which breaks any future browser-based client. Recommend documenting rather than changing in Phase 20,
but this is a decision, not an oversight.

---

## SSE and Authentication (VERIFIED)

- **Where the routes are:** `GET /sse` and `POST /message` (M7). ROADMAP SC1/SC2 say "the SSE root"; that is
  inaccurate for this SDK version. Write the tests against `/sse` and `/message`. `GET /` is unmatched today
  (it returned 401 in external mode via the fallback path).
- **How a client authenticates an SSE connection:** an `Authorization: Bearer …` header on the initial
  `GET /sse`. The SDK does not add it — it is the client's responsibility. `[ASSUMED for the client side;
  VERIFIED server-side that the header is visible to the gate and that gating works.]`
- **Does gating break session establishment?** No. The SDK creates `SseServerTransport` (and registers its
  `sessionId` in a `ConcurrentMap`) *inside* the SSE handler; `POST /message` looks the session up by
  `?sessionId=`. The gate runs strictly before the handler, so a rejected connect creates no transport and
  leaves no orphan map entry. Verified: unauthenticated `GET /sse` → 401 with no stream opened.
- **Any Ktor 3.1.3 issue responding 401 to an SSE request?** None observed. The SSE plugin only engages inside
  the route handler; responding earlier commits an ordinary short response and the connection closes cleanly.
- **Browser caveat:** `EventSource` cannot set request headers, so a browser MCP client would need the token in a
  query string. Not a supported client here (Claude Desktop / Codex CLI use ordinary HTTP clients). Worth a line
  in `docs/mcp-hardening.md`.

---

## Security Headers (SC3) — VERIFIED

- Appending in the **Plugins** phase (before routing) reliably lands the headers on the routed `200` response,
  including on the `text/event-stream` SSE response. Verified for `X-Frame-Options` and
  `Content-Security-Policy` on both a plain route and `/sse`.
- Appending in the **Call** phase *after* the handler has committed is **timing- and protocol-dependent**: in the
  measured baseline the four headers were present on `POST /message` over TLS/HTTP-2 but **absent** on the exact
  same code path over cleartext HTTP/1.1. Never rely on post-commit header mutation.
- The correct hook is `call.response.headers.append(...)` from the pre-routing gate. `onCallRespond` is the wrong
  seam (it attaches to `ApplicationSendPipeline.Transform`).
- Guard against duplicates if any route ever sets the same header — `append` does not deduplicate.

---

## D-02 vs `McpSupervisor.probeExistingServer` (Q5)

`McpSupervisor.probeExistingServer` (`mcp/McpSupervisor.kt:254-272`) returns true only when **both**
`responseCode in 200..299` **and** `getHeaderField("X-Burp-AI-Agent") == "mcp"`.

**What breaks under D-02:** in external mode the header disappears → `probe` returns `false` →
`attemptTakeover` returns `NO_COMPATIBLE_SERVER` (`:227-229`) → `handleBindFailure` logs
*"Port appears busy and no compatible MCP server was detected for takeover"* and **schedules no retry**
(`:209-214`). External-mode bind-conflict takeover silently stops working. Local mode is unaffected.

**Minimal, non-Phase-25 accommodation (recommended):** mirror the mode on both sides.
- Server: emit `X-Burp-AI-Agent: mcp` only when `!settings.externalEnabled`.
- Probe: require the header only when `!settings.externalEnabled`; in external mode accept a `2xx` from
  `/__mcp/health` alone, and log that identity could not be established.

This keeps the **local** probe contract byte-identical (the path Phase 20 must not break), degrades the external
probe to a liveness check, and does not make F7 worse — the header was trivially spoofable anyway, which is the
whole point of Finding 7. `settings` is already a parameter of `probeExistingServer`, so no signature change is
needed. Phase 25 replaces the whole mechanism.

**Do not** simply leave the probe untouched: that is a silent functional regression in a code path with no test
coverage (`McpTakeoverClient` is faked in `McpSupervisorRestartPolicyTest`; the real
`probeExistingServer` is never exercised).

---

## SEC-05 Sub-Items

### 5a — Real build version (F11)

`KtorMcpServerManager.kt:97`: `Implementation("burp-ai-agent", "0.6.0")`. The project version exists **only** in
`build.gradle.kts:15` (`version = "0.9.2"`); there is no runtime version constant anywhere in `src/main/kotlin`
(`App.kt:60` sets the extension name without a version).

**Right seam: extend `GenerateBuildFlagsTask` (`build.gradle.kts:72-110`).** It already has the correct shape —
`@get:Input Property<Boolean>` + `@get:OutputDirectory DirectoryProperty` — and `sourceSets.main` wires the
generated dir through `generateBuildFlags.flatMap { it.outputDir }` so compile/ktlint/detekt dependencies are
inferred automatically. ktlint already excludes `**/generated/**`.

Add `@get:Input abstract val version: Property<String>` and set it at *configuration* time
(`version.set(project.version.toString())`), emitting `const val VERSION = "…"` into the same `BuildFlags` object.

**Configuration-cache pitfall:** `org.gradle.configuration-cache=true` is set in `gradle.properties`. The
`@TaskAction` must **not** reference `project`, `project.version`, or any `Project` API — only the `Property`
values. Capturing the string at configuration time is safe; reading `project.version` inside `generate()` would
fail the build under the configuration cache. `[VERIFIED: gradle.properties:3 + the existing task's shape]`

**Do not change the *name* half** (`"burp-ai-agent"`) while fixing the version. It is a protocol identifier that
MCP clients may key on, and the project has a separate naming history (artifact renamed to `Custom-AI-Agent`).
Changing it is a decision, not a defect fix — raise it, do not do it silently.

### 5b — IPv6 host parsing (F12)

`isValidHost` (`:333-347`) does `host.split(":")`, takes `parts[0]` as hostname and `parts[1]` as port. For
`[::1]:9876` that yields hostname `"["`. Verified `false`. Meanwhile `isLoopbackHost` (`:301-304`) accepts `"::1"`
as a bind host — so a server bound to `::1` rejects its own `Host` header. That inconsistency is exactly what
SEC-05 names.

**Correct parse for an HTTP `Host` authority:**
1. If the value starts with `[`, the literal runs to the first `]`; a `:port` may follow the bracket. Strip the
   brackets to get the address.
2. Otherwise, if the value contains **more than one** `:`, it is a bare IPv6 literal or malformed → reject
   (a valid non-IPv6 authority has at most one `:`).
3. Otherwise split on the single `:` for host and port.
4. Lowercase and compare against `localhost`, `127.0.0.1`, `::1`, and (recommended) the expanded
   `0:0:0:0:0:0:0:1`. Reuse `isLoopbackHost` so the two functions cannot drift again.

**Pitfall:** `java.net.URI("http://$host").host` returns `[::1]` **with** brackets for IPv6 — this is precisely
why `isValidOrigin("http://[::1]:9876")` returns `false` (verified). If the planner reuses `URI`, brackets must be
stripped. `io.ktor.http.Url`/`URLBuilder` is already on the classpath as an alternative.

**Scope note:** `isValidOrigin` and `isValidReferer` have the same IPv6 bug. SEC-05's text only names
`isValidHost`, but fixing one and not the others leaves an inconsistency the next reviewer will file again.
Recommend fixing all three via a shared `isLoopbackAuthority(...)` helper and noting it in the plan.

### 5c — Blank bearer token (F19)

Verified: `isAuthorized("Bearer ", "")` → **true**.

`AgentSettingsRepository.loadMcpSettings` (`config/AgentSettings.kt:1258-1263`) does regenerate a blank token on
load, and the settings UI warns — but that is not where the guard belongs, because:
- `McpSettings` is a plain data class that can be constructed directly (tests, settings import, future call sites);
- the settings-import path does not necessarily go through `loadMcpSettings`;
- the codebase convention is that denial paths **fail closed at the point of use**.

**Recommended placement (both):**
1. In `isAuthorized`: `if (token.isBlank()) return false` before the comparison. This also protects the
   `/__mcp/shutdown` in-handler check (D-04), which calls the same function.
2. In the gate/startup: if `settings.externalEnabled && settings.token.isBlank()`, deny **every** request with 401
   and log once at startup ("external MCP enabled with a blank token — all requests will be rejected"). Fails
   closed and is diagnosable.

Keep `MessageDigest.isEqual` for the comparison — do not hand-roll.

---

## Blocked-Request Logging (D-06..D-09)

### Audit payload shape

`AuditLogger.emitGlobal(type: String, payload: Any)` (`audit/AuditLogger.kt:26-31`) forwards to a registered
global emitter and is a **no-op when no emitter is registered** — safe to call from the Netty event loop, but it
means an audit-only signal is invisible by default (D-06's rationale). `logEvent` wraps the payload as
`{ts, type, payload, payloadSha256}`.

**Blocker:** `MCP_TOOL_EVENT_BLOCKED = "mcp_tool_blocked"` is a **file-private** `const` in
`mcp/tools/McpTool.kt:21`, and `emitToolTelemetry` is a file-private function there. Phase 20 cannot reference
either.

**Recommendation:** introduce a **new** event type `mcp_transport_blocked` in the MCP package, reusing the
*shape* (`reason` key + a flat map) rather than the literal constant. Rationale: the tool payload keys
(`tool`, `toolType`, `hasArgs`, `argsSha256`) have no transport-level meaning, and emitting `mcp_tool_blocked`
records with a different key set would corrupt any downstream analysis of tool telemetry. If the planner insists
on the literal constant per D-06, it must first be promoted out of `McpTool.kt` into a shared `internal const`
— that is a bigger, cross-file diff.

**Suggested payload** (all values D-07-sanitized):

```
"reason"        -> "origin_mismatch" | "host_mismatch" | "referer_mismatch" | "browser_no_origin" | "unauthorized" | "blank_token"
"mode"          -> "local" | "external"
"method"        -> request verb
"path"          -> request path (sanitized, capped)
"origin"        -> sanitized or null
"host"          -> sanitized or null
"referer"       -> sanitized or null
"userAgent"     -> sanitized or null
```

**Open question:** CLAUDE.md records the project's audit default as *"hashes only unless verbose is on"*.
Reflecting raw (even sanitized) attacker-controlled header values into the audit log may conflict with that.
Recommend: SHA-256 the values by default and emit plaintext only when verbose audit is enabled — consistent with
`argsSha256` in the tool telemetry. Surface this to the maintainer rather than assuming.

**D-07 sanitization:** strip `\r`, `\n` and all C0/C1 control characters (CWE-117 log injection; also kills ANSI
escapes in Burp's Output tab), then cap at a fixed length. `mcp/tools/McpToolExecutorImpl.kt::sanitizeErrorMessage`
is the in-repo precedent (regex scrub + `MAX_ERROR_MESSAGE_LENGTH` cap with `"..."` suffix).

### Rate limiting (D-09) — which analog

| Pattern | Shape | Fits D-09? |
|---------|-------|-----------|
| `maybeLogBackoff` (`scanner/PassiveAiScannerAnalysis.kt:825-835`) | `AtomicLong` last-log timestamp + `nowMs - prev < INTERVAL_MS` guard + `compareAndSet` | **Yes — use this.** It is a time-window limiter, exactly D-09's "first immediately, then aggregate every 60 s". |
| `availabilityLogged` (`backends/cli/CliBackend.kt:28,75`) | one-shot `AtomicBoolean.compareAndSet(false, true)` | No — one-shot; cannot express "N further blocks". |

**Extension needed:** `maybeLogBackoff` is a single global window. D-09 wants per-reason. Use a
`ConcurrentHashMap<String, ReasonWindow>` where `ReasonWindow` holds an `AtomicLong lastLoggedAtMs` and an
`AtomicLong suppressedCount`; on the first block for a reason log immediately, otherwise increment; when the
window expires, log `"N further blocks for <reason> in the last 60s"` and reset. Keep it lock-free — the gate
runs on Netty event-loop threads and must never block.

---

## SC4 — Making the Tests Fail Pre-Fix Without Leaving a Red Repo (Q2)

### Constraints discovered

1. **External mode requires TLS.** `KtorMcpServerManager.start` throws
   `IllegalStateException("External MCP access requires TLS…")` when `externalEnabled && !tlsEnabled`
   (`:80-82`). So the SC1 tests **must** bind a real TLS connector — which rules out `testApplication {}` for
   those cases outright.
2. **TLS material is cheap in-test.** `McpTls.resolve` needs a non-blank `tlsKeystorePath`; with
   `tlsAutoGenerate = true` and a missing file it shells out to the running JDK's `keytool` and writes a PKCS12
   (`McpTls.kt:35-105`). **Verified working here** — the external-mode probe started successfully with a keystore
   generated into `Files.createTempDirectory("…")` in ~1-2 s. Precedent exists: `McpTlsInJvmTest.kt`.
   Never point a test at `~/.burp-ai-agent/certs` (the user's real keystore).
3. **Client must be OkHttp, not `HttpURLConnection`.** Verified: `HttpURLConnection` **silently drops the
   `Origin` header** (JDK restricted-header list) and always writes its own `Host`; `Referer` and `User-Agent` do
   get through. OkHttp 5.4.0 transmits `Origin` and can override `Host` (verified: the server saw
   `Host: evil.example`). OkHttp is already on the test classpath (`implementation okhttp:5.4.0` +
   `testImplementation mockwebserver:5.4.0`) — **no new dependency needed**.
   For TLS: `SSLContext.getInstance("TLS").init(null, arrayOf(trustAllManager), SecureRandom())` →
   `.sslSocketFactory(ctx.socketFactory, trustAllManager).hostnameVerifier { _, _ -> true }`.
4. **Do not read an SSE response body.** `Response.body.string()` on a `text/event-stream` 200 blocks until the
   read timeout (this cost one probe iteration). Assert on `response.code` / headers and close the response.
5. **`ktor-server-test-host` is not needed and is not recommended.** `ktor-server-test-host-jvm:3.1.3` is
   resolvable on Maven Central (verified `HTTP 200` on the POM) and is Apache-2.0 (MIT-compatible), but:
   (a) it cannot bind a TLS connector, so it cannot cover SC1 at all; (b) the SC4 gate is about the shipped
   engine. It *would* reproduce the ordering bug, because the bug lives in `ApplicationCallPipeline`, not in the
   engine — but that is **inference, not verified this session** `[ASSUMED]`. Recommendation: skip it; use the
   real Netty server, in the shape of `McpServerIntegrationTest`.

### Red-before-green without a red repo

A test that is committed red and fixed in a later commit leaves `main` broken; a `@Disabled` dance proves nothing.
Two workable options, in order of preference:

- **(A) Machine-checkable stash proof, run as a plan verification step.**
  ```
  git stash push -- src/main/kotlin/com/six2dez/burp/aiagent/mcp/
  JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test --tests '<new test class>'   # MUST FAIL
  git stash pop
  JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test --tests '<new test class>'   # MUST PASS
  ```
  Deterministic, leaves the tree green, and produces evidence a verifier can read.
  *Caveat recorded in project memory: do not wrap wave merges in `git stash push -- <paths>`. This usage is a
  local verification step inside a single task, not a wave-merge operation — but the planner should still call
  it out explicitly so the executor does not generalise it.*
- **(B) Evidence-by-transcript.** Write the tests first, run them against `HEAD`, paste the failure output into
  the plan/commit body, then commit tests + fix together. Cheaper, not machine-checkable.

Either way, the tests and the fix land in the **same commit**.

### Test-task naming trap (must be decided in the plan)

`build.gradle.kts:145-157`: passing `-PexcludeHeavyTests=true` excludes `*IntegrationTest`, `*ConcurrencyTest`,
`*BackpressureTest`, `*RestartPolicyTest`, `*SupervisionTest` from `test`; `nightlyRegressionTest` (`:160-171`)
*includes* `*IntegrationTest`. **If the SC4 gate is named `…IntegrationTest`, it will be silently skipped in any
fast PR gate that passes `excludeHeavyTests`.** Decide deliberately: either name them so they always run
(e.g. `McpAccessControlPipelineTest`) or confirm the security gate never passes `-PexcludeHeavyTests=true`.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Running a check before routing | A manual `intercept(Call)` placed "early enough" | `createApplicationPlugin { onCall { … } }` (Plugins phase) | Phase order is structural (M1/M2); line order is not. This is the whole point of the phase. |
| Stopping the pipeline after denial | Reflection into `CallContext.finish$ktor_server_core` | `call.respond(...)` + return; Ktor skips handlers via `isHandled` (M5/M6) | Reflection into an `internal` API breaks on any Ktor upgrade. |
| Origin allow-listing / preflight | Extra Origin logic in the gate | The already-installed `CORS` plugin | Verified it already returns 403 for foreign origins and handles OPTIONS correctly. |
| Constant-time token comparison | A hand-written loop | `MessageDigest.isEqual` (already used) | Review confirmed it correct; it just needs to be *called*. |
| IPv6/authority parsing | `split(":")` | Bracket-aware parse, or `io.ktor.http.Url` with bracket stripping | `URI.host` returns `[::1]` with brackets — the exact bug in `isValidOrigin`. |
| Log rate limiting | A new limiter | The `maybeLogBackoff` window+CAS pattern, keyed per reason | Existing in-repo pattern; D-09 mandates reuse. |
| HTTP test client that can set `Origin`/`Host` | `HttpURLConnection` + `-Dsun.net.http.allowRestrictedHeaders` | OkHttp 5.4.0 (already a dependency) | The system-property route is JVM-global, fragile under Gradle forked JVMs, and undocumented. |
| Runtime version string | A second hardcoded constant | `BuildFlags.VERSION` from `GenerateBuildFlagsTask` | Single source of truth is `build.gradle.kts:15`; that is what `0.6.0` drifted from. |

**Key insight:** every control this phase needs already exists in the codebase and is already correct. The phase
is about *placement and invocation*, not about writing new security logic. Any task that rewrites `isAuthorized`,
`constantTimeEquals` or the CORS policy is off-scope.

---

## Common Pitfalls

### P1 — `HttpURLConnection` silently drops `Origin`
**What goes wrong:** an SC2 test asserting "foreign `Origin` → 403" passes for the wrong reason, or fails to
reproduce at all, because the header never leaves the client.
**Why:** the JDK's restricted-header list blocks `Origin`, `Host`, `Access-Control-Request-Method`, and others
unless `-Dsun.net.http.allowRestrictedHeaders=true` is set.
**Avoid:** use OkHttp for anything touching `Origin`, `Host`, or preflight. Verified: `Referer` and `User-Agent`
*do* pass through `HttpURLConnection`, so the existing test style is fine for those two only.
**Warning sign:** a server-side header dump shows `Origin=<null>` while the test "passes".

### P2 — The Origin test passes pre-fix
**What goes wrong:** SC4 is declared satisfied by a test that Ktor's CORS plugin was already making green.
**Avoid:** build the SC4 gate only from the nine assertions listed in "Pre-Fix Behaviour Baseline".

### P3 — Header mutation after response commit is non-deterministic
**What goes wrong:** a test asserting security headers on `POST /message` passes over TLS/HTTP-2 and fails over
cleartext HTTP/1.1 (observed, same code).
**Avoid:** append headers only from the pre-routing gate; never after a handler has responded.

### P4 — A second same-phase interceptor still runs after a respond
**What goes wrong:** double-respond after CORS answered a preflight → `ResponseAlreadySent`-class behaviour and a
misleading status.
**Avoid:** `if (call.response.isCommitted) return@onCall` as the first statement.

### P5 — Reading an SSE body hangs the test
**Avoid:** assert on `response.code`, close the response, never call `body.string()` on `text/event-stream`.

### P6 — Manager lifecycle in tests
`start(...)` is asynchronous via a single-thread executor with a state callback — always await a `CountDownLatch`
on `Running`/`Failed` (see `McpServerIntegrationTest:50-67`). `stop()` blocks up to 10 s
(`KtorMcpServerManager.kt:257`); use `shutdown()` in a `finally`. `shutdown()` terminates the executor, so a
manager **cannot be restarted** — one manager instance per test, or the next `start` throws
`RejectedExecutionException` (documented at `:239-243`).

### P7 — `ServerSocket(0)` port race
Bind-and-close then reuse is a TOCTOU race in CI. It is the existing precedent (`McpServerIntegrationTest:96-100`)
so it is acceptable, but the plan should include a single retry on `Failed`/`BindException` if CI proves flaky.

### P8 — detekt baseline must not grow
QUAL-07 forbids adding this milestone's findings to `detekt-baseline.xml` (1096 entries). The module lambda in
`start()` is already near `LongMethod`'s threshold of 80 (`detekt.yml:6-7`) — **extract the gate into its own
file**, and prefer a single typed decision over many early returns (`ReturnCount` is the #2 baseline offender).
`ktlint` is strict (`ignoreFailures=false` unless `-PktlintLenient=true`); run `./gradlew ktlintFormat` before
committing. `MaxLineLength` is 250.

### P9 — The gate runs on Netty event-loop threads
No blocking I/O, no `synchronized`, no Swing, no file writes in the hot path. `AuditLogger.emitGlobal` is a
no-op when unregistered, but the registered emitter may write to disk — check the emitter's implementation before
calling it synchronously from the gate, or hand off. `[ASSUMED — the registered emitter's threading was not traced
this session.]`

### P10 — `-ea` is on for the `test` task
`build.gradle.kts:144` sets `jvmArgs("-ea")`. Harmless here, but any `assert()` added to the gate **will** fire in
tests and **will not** fire in production Burp (that is F16 / QUAL-07). Do not use `assert()` as a control.

### P11 — Configuration cache and the BuildFlags change
`org.gradle.configuration-cache=true`. Reading `project.version` inside the `@TaskAction` fails the build; set the
`Property` at configuration time.

### P12 — Do not rename the MCP server *name*
`Implementation("burp-ai-agent", …)` — clients may key on the name. Fix the version half only.

---

## Code Examples

### Gate plugin shape (recommended)

```kotlin
// src/main/kotlin/com/six2dez/burp/aiagent/mcp/McpAccessControlPlugin.kt
// Pattern source: PluginBuilder.onCall -> ApplicationCallPipeline.ApplicationPhase.Plugins
// (verified by decompiling ktor-server-core-jvm-3.1.3)

internal class McpAccessControlConfig {
    lateinit var settings: McpSettings
    lateinit var onBlocked: (BlockReason, RequestFacts) -> Unit
}

internal val McpAccessControl =
    createApplicationPlugin("McpAccessControl", ::McpAccessControlConfig) {
        val settings = pluginConfig.settings
        val onBlocked = pluginConfig.onBlocked

        onCall { call ->
            // P4: CORS shares this phase and may already have responded (preflight / foreign origin).
            if (call.response.isCommitted) return@onCall

            when (val decision = evaluate(call.request, settings)) {
                is GateDecision.Deny -> {
                    onBlocked(decision.reason, decision.facts)   // D-06/D-07/D-09 live here
                    call.respond(decision.status)                // D-08: bare status, no body
                }
                GateDecision.Allow -> {
                    // SC3: appended before routing so they land on the routed response,
                    // including on text/event-stream (verified).
                    call.response.headers.append("X-Frame-Options", "DENY")
                    call.response.headers.append("X-Content-Type-Options", "nosniff")
                    call.response.headers.append("Referrer-Policy", "same-origin")
                    call.response.headers.append("Content-Security-Policy", "default-src 'none'")
                }
            }
        }
    }
```

### Module lambda ordering (the whole diff surface)

```kotlin
install(CORS) { /* unchanged */ }

install(McpAccessControl) {                 // <-- new, immediately after CORS
    settings = settingsSnapshot
    onBlocked = blockedReporter
}

routing {
    get("/__mcp/health") { /* D-02: agent header only when !externalEnabled */ }
    post("/__mcp/shutdown") { /* D-04: keep the in-handler token check */ }
}

mcp { mcpServer }                           // registers GET /sse + POST /message
mcpServer.registerTools(api, context)
```

### Test client (OkHttp, TLS, no new dependency)

```kotlin
private fun trustAllClient(): OkHttpClient {
    val tm = object : X509TrustManager {
        override fun checkClientTrusted(c: Array<out X509Certificate>?, a: String?) = Unit
        override fun checkServerTrusted(c: Array<out X509Certificate>?, a: String?) = Unit
        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }
    val ctx = SSLContext.getInstance("TLS").apply { init(null, arrayOf<TrustManager>(tm), SecureRandom()) }
    return OkHttpClient.Builder()
        .callTimeout(4, TimeUnit.SECONDS)
        .readTimeout(2, TimeUnit.SECONDS)
        .sslSocketFactory(ctx.socketFactory, tm)
        .hostnameVerifier { _, _ -> true }
        .build()
}

// P5: never read the body of an SSE response.
client.newCall(Request.Builder().url("$base/sse").build()).execute().use { assertEquals(401, it.code) }
```

### External-mode settings for a test

```kotlin
val ks = Files.createTempDirectory("mcp-test-ks").resolve("test.p12").toString()  // never ~/.burp-ai-agent
McpSettings(
    /* … */ externalEnabled = true, tlsEnabled = true,
    tlsAutoGenerate = true, tlsKeystorePath = ks, tlsKeystorePassword = "test-pass",
    token = "test-token", allowedOrigins = emptyList(), /* … */
)
```

---

## Project Constraints (from CLAUDE.md / AGENTS.md)

| Directive | Source | Impact on this phase |
|-----------|--------|----------------------|
| English only in code and comments | AGENTS.md (non-negotiable) | All new identifiers, comments, log strings. |
| Kotlin (JVM 21) + Gradle Kotlin DSL + Montoya API | CLAUDE.md constraints | No new language/build tooling. |
| Build must run as `JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew …` | project memory | Every gradle invocation in the plan must carry this prefix; default JDK 25 breaks Gradle 8.12.1. |
| Gates green: `./gradlew test detekt ktlintCheck` | CLAUDE.md / task brief | Phase gate. |
| detekt baseline must not grow | QUAL-07 + task brief | See P8. |
| MIT licence — dependencies must be compatible | CLAUDE.md | No new dependency is recommended; OkHttp (Apache-2.0) is already present. |
| MCP binds `127.0.0.1` by default; external needs opt-in + bearer + optional TLS | CLAUDE.md | The gate must not weaken this; `McpTls.resolve` fail-closed invariant must survive. |
| Audit defaults: disabled by default, hashes only unless verbose | CLAUDE.md | Drives D-06's dual destination and the open question on hashing reflected header values. |
| Privacy modes stay user-visible and pre-flight | CLAUDE.md | Untouched by this phase. |
| Fail closed, return typed results rather than throwing | CONTEXT + codebase convention | `GateDecision` sealed type over early returns. |
| Small, testable components; favour pure functions | AGENTS.md quality bar | Extract `evaluate(request, settings): GateDecision` as a pure function — it is directly unit-testable without a server. |
| GSD workflow: no direct repo edits outside a GSD command | CLAUDE.md | Research produced only this document; probe code was deleted and `git status` is clean. |

---

## Validation Architecture

### Test Framework

| Property | Value |
|----------|-------|
| Framework | JUnit Jupiter 6.0.3 + `kotlin("test")`; mockito-kotlin 5.4.0; OkHttp/MockWebServer 5.4.0 |
| Config file | none — `useJUnitPlatform()` at `build.gradle.kts:142-158`; `jvmArgs("-ea")` |
| Quick run command | `JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test --tests 'com.six2dez.burp.aiagent.mcp.*'` |
| Full suite command | `JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test detekt ktlintCheck` |

### Success Criteria → Test Map

| SC | Behavior | Test Type | Automated Command | Machine-checkable | File Exists? |
|----|----------|-----------|-------------------|-------------------|--------------|
| SC1 | external: `POST /message` and `GET /sse` without `Authorization` → 401 | integration (TLS, Netty) | `./gradlew test --tests '*McpAccessControlExternalPipelineTest'` | yes | ❌ Wave 0 |
| SC2 | local: foreign `Origin` → 403 (CORS, passes pre-fix); foreign `Host` → 403; foreign `Referer` → 403; browser UA w/o `Origin` → 403 — on `/__mcp/health`, `/message`, `/sse` | integration (cleartext, Netty, **OkHttp**) | `./gradlew test --tests '*McpAccessControlPipelineTest'` | yes | ❌ Wave 0 |
| SC3 | all four security headers on a **matched-route** 200 (`/__mcp/health`) and on the SSE 200 | integration | same as SC2 | yes | ❌ Wave 0 |
| SC4 | the SC1–SC3 assertions **fail** against pre-fix `KtorMcpServerManager` | process gate | stash-proof recipe above (Option A) | yes | ❌ Wave 0 |
| SC5a | server advertises the real version | unit | `./gradlew test --tests '*BuildFlags*'` or assert `Implementation.version == BuildFlags.VERSION` | yes | ❌ Wave 0 |
| SC5b | `isValidHost("[::1]:9876", 9876)` → true; consistent with `isLoopbackHost` | unit (reflection, existing style) | `./gradlew test --tests '*KtorMcpServerManagerSecurityTest'` | yes | ✅ extend |
| SC5c | blank token cannot authenticate (`isAuthorized("Bearer ", "")` → false) + external-mode request with blank token → 401 | unit + integration | as above + external suite | yes | ✅ extend / ❌ Wave 0 |
| SC6 | `/__mcp/shutdown` → 401 without token, 200 with token | integration | `./gradlew test --tests '*McpServerIntegrationTest'` | yes | ✅ exists |
| — | D-02: external `/__mcp/health` carries no `X-Burp-AI-Agent`; local does | integration | external + local suites | yes | ❌ Wave 0 |
| — | `McpSupervisor` local-mode probe still succeeds after D-02 | integration or targeted unit | new test against the real `probeExistingServer` | yes | ❌ Wave 0 |
| — | Blocked-request Output line + audit event fire once then aggregate (D-06/D-09) | unit (pure limiter) | `./gradlew test --tests '*BlockedRequestReporterTest'` | yes | ❌ Wave 0 |
| — | Runbook `docs/mcp-hardening.md` reflects D-01/D-02 (health is deliberately exempt) | doc review | — | **no — human confirmation** | ✅ exists |
| — | Real Claude Desktop / Codex CLI still connect after the change | manual smoke | — | **no — human confirmation** | — |

### Sampling Rate

- **Per task commit:** `JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test --tests 'com.six2dez.burp.aiagent.mcp.*'`
- **Per wave merge:** `JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test`
- **Phase gate:** `JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test detekt ktlintCheck` green before `/gsd-verify-work`

### Wave 0 Gaps

- [ ] `src/test/kotlin/.../mcp/McpTestServerSupport.kt` — shared helpers: free port, start-and-await-`Running`,
      trust-all OkHttp client, `McpSettings` builders. Without it the same 40 lines get copied three times.
- [ ] `src/test/kotlin/.../mcp/McpAccessControlPipelineTest.kt` — local mode (SC2, SC3, D-02-local)
- [ ] `src/test/kotlin/.../mcp/McpAccessControlExternalPipelineTest.kt` — external mode + TLS (SC1, SC5c, D-02-external)
- [ ] `src/test/kotlin/.../mcp/McpAccessControlDecisionTest.kt` — pure-function unit tests for `evaluate(...)`
- [ ] `src/test/kotlin/.../mcp/BlockedRequestReporterTest.kt` — D-07 sanitization + D-09 aggregation
- [ ] Extend `KtorMcpServerManagerSecurityTest.kt` — IPv6 host, blank token
- [ ] Naming decision: keep these class names **out** of the `*IntegrationTest` pattern, or confirm the security
      gate never passes `-PexcludeHeavyTests=true` (see the naming trap above)
- [ ] No framework install needed.

---

## Security Domain

`security_enforcement` is absent from `.planning/config.json` → treated as enabled.

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | yes | Bearer token, `MessageDigest.isEqual` constant-time compare (already present); **new:** blank-token rejection; enforcement moved to the Plugins phase. |
| V3 Session Management | partial | `sessionId` is minted by the MCP SDK inside the `/sse` handler and is bearer-equivalent for `POST /message`. The extension does not manage sessions; the control is gating the connect so no session is minted for an unauthenticated peer. |
| V4 Access Control | yes | The transport gate (this phase). Separate from the tool enable/unsafe gate in `McpTool.runTool`, which stays as-is. |
| V5 Input Validation | yes | Authority parsing for `Host`/`Origin`/`Referer` (bracketed IPv6); D-07 sanitization of header values before logging (CWE-117). |
| V6 Cryptography | no new crypto | Keep `MessageDigest.isEqual`; do not hand-roll. TLS material handling in `McpTls` unchanged — its fail-closed invariant must survive. |
| V14 Configuration | yes | External mode fail-closed without TLS (`start()` throws) — must survive; blank-token-at-startup fails closed. |

### Known Threat Patterns for this stack

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| DNS rebinding / foreign `Host` against a loopback service | Spoofing | `Host` allow-list, now enforced pre-routing (bracket-aware IPv6 parse). |
| Browser-driven CSRF against a loopback MCP server | Spoofing / Elevation | `Origin` allow-list (CORS) **plus** browser-UA-without-`Origin` rejection, both pre-routing. |
| Missing authn on the transport (the phase's defect) | Elevation of Privilege | Gate in the `Plugins` phase; regression tests that fail pre-fix. |
| Log injection via reflected header values | Tampering / Repudiation | D-07: strip CR/LF + control chars, cap length (CWE-117). |
| Log flooding to bury other events | Denial of Service | D-09 per-reason window aggregation. |
| Unauthenticated fingerprinting of the listener | Information Disclosure | D-02 (no identifying header in external mode); residual: CORS preflight reveals allow-listed origins (open question). |
| Token disclosure to a port squatter | Information Disclosure | **Out of scope — Phase 25 / SEC-07** (D-05). Phase 20 must not make it worse: keep the probe token-free. |
| 401-vs-403 status oracle | Information Disclosure | Accepted by D-08 (bare status, no body). |

---

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| JDK 21 (`/usr/libexec/java_home -v 21`) | build + tests | ✓ | 21 | none — Gradle 8.12.1 breaks on default JDK 25 |
| Gradle wrapper | build | ✓ | 8.12.1 | — |
| `keytool` (from the toolchain JDK) | external-mode TLS tests via `McpTls.resolve` | ✓ | JDK 21 bundled | pre-generate a fixture PKCS12 and commit it (not recommended) |
| OkHttp 5.4.0 on the test classpath | Origin/Host/preflight/TLS test client | ✓ | 5.4.0 (`implementation` + mockwebserver `testImplementation`) | raw `Socket` + hand-written HTTP/1.1 |
| Maven Central reachability | any new dependency | ✓ | — | offline cache is warm for all current deps |
| `ktor-server-test-host:3.1.3` | not recommended | resolvable (POM `HTTP 200`) | 3.1.3, Apache-2.0 | not needed — real Netty covers everything |
| jadx (verification only) | this research | ✓ | `/opt/homebrew/bin/jadx` | not needed by the plan |

**Missing dependencies with no fallback:** none.
**Missing dependencies with fallback:** none.

---

## Package Legitimacy Audit

**No new packages are recommended by this research.** Every library needed (Ktor 3.1.3 server modules, kotlin-sdk
0.5.0, OkHttp 5.4.0, JUnit Jupiter 6.0.3, mockito-kotlin 5.4.0) is already declared in `build.gradle.kts` and
already resolved in the local Gradle cache. No install step, therefore no slopcheck gate is applicable.

If the planner nonetheless adds `io.ktor:ktor-server-test-host:3.1.3` (not recommended), note: same
`io.ktor` group as eight already-trusted coordinates, published by JetBrains, Apache-2.0, and version-locked to
the existing Ktor 3.1.3 family. `[VERIFIED: Maven Central POM returns HTTP 200]`

---

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | `ktor-server-auth`'s enforcement is route-scoped and cannot wrap the SDK's `mcp{}` routes | Decision 1, alternatives | Low — the recommended option is independently verified; this only affects the rejection rationale. |
| A2 | `testApplication {}` would reproduce the phase-ordering bug (pipeline-level, engine-independent) | SC4 constraints | Low — the recommendation is to not use it anyway. |
| A3 | MCP clients authenticate SSE by putting `Authorization` on the initial `GET /sse` | SSE section | Medium — if a real client uses a query parameter instead, external mode would break for that client. **Confirm with a real Claude Desktop / Codex CLI config before shipping.** |
| A4 | Real MCP clients would tolerate / not tolerate a local-mode token | Decision 2 | Low — the decision is to change nothing. |
| A5 | The registered `AuditLogger` global emitter is safe to call synchronously from a Netty event-loop thread | P9 | Medium — if the emitter does blocking disk I/O, the gate adds latency to every blocked request. Trace `AuditLogger.registerGlobalEmitter`'s caller before wiring D-06. |
| A6 | Hashing reflected header values by default (rather than logging them sanitized-but-plain) is the right reading of "hashes only unless verbose" | Blocked-request logging | Medium — a privacy/usability trade the maintainer should settle. |
| A7 | Changing `Implementation("burp-ai-agent", …)`'s *name* could break client configs | SEC-05 5a | Low — the recommendation is to not change it. |

---

## Open Questions

1. **Does the SC4 gate run in the fast PR gate?**
   - Known: `-PexcludeHeavyTests=true` excludes `*IntegrationTest`; `nightlyRegressionTest` includes it.
   - Unclear: which invocation CI actually uses for PRs.
   - Recommendation: name the new classes so they do **not** match `*IntegrationTest`, and state this in the plan.

2. **Unauthenticated CORS preflight in external mode.**
   - Known: with `allowedOrigins` empty the code calls `anyHost()`, so every preflight succeeds pre-auth; with a
     list, a preflight reveals which origins are allow-listed.
   - Unclear: whether the maintainer considers this acceptable given D-02's anti-fingerprinting intent.
   - Recommendation: document in `docs/mcp-hardening.md`; do not change behaviour in Phase 20.

3. **Audit payload: plaintext or hashed header values?**
   - Known: CLAUDE.md says "hashes only unless verbose is on"; D-06 says reuse the tool-blocked shape, which
     hashes args (`argsSha256`) but names the tool in plaintext.
   - Recommendation: hash by default, plaintext under verbose. Confirm with the maintainer.

4. **New event type `mcp_transport_blocked` vs literal reuse of `mcp_tool_blocked`.**
   - Known: the constant is file-private in `McpTool.kt` and the key sets differ.
   - Recommendation: new type, same shape. If D-06 must be read literally, the constant needs promoting first —
     size the task accordingly.

5. **`isValidOrigin` / `isValidReferer` IPv6 — in or out of SEC-05?**
   - Known: both have the identical bracket bug (verified). SEC-05's text names only `isValidHost`.
   - Recommendation: fix all three behind one shared helper; it is a two-line delta and prevents a repeat finding.

6. **Does `docs/mcp-hardening.md` need updating?**
   - Known: §"External Access" item 4 promises *"Validate `Authorization: Bearer <token>` is sent on every
     request"* and §"Verification" item 2 repeats it. After D-01 that is deliberately false for `/__mcp/health`.
   - Recommendation: yes — amend both, and add the SSE-header note. Cheap, and it is the user-facing contract
     CONTEXT flags.

---

## Sources

### Primary (HIGH confidence)
- Decompiled `ktor-server-core-jvm-3.1.3.jar` (`ApplicationCallPipeline`, `PluginBuilder`,
  `PluginBuilder$onDefaultPhaseWithMessage$1$1`, `CallContext`, `RoutingRoot`, `RoutingRoot$Plugin`,
  `RoutingNode`, `RoutingNode$buildPipeline$1$1`, `PipelineCallKt`) — jadx + `javap -c -p`
- Decompiled `ktor-utils-jvm-3.1.3.jar` (`PipelineContext`)
- Decompiled `ktor-server-cors-jvm-3.1.3.jar` (`routing.CORSKt`, `cors.CORSKt`)
- Decompiled `kotlin-sdk-jvm-0.5.0.jar` (`server.KtorServerKt` — `Application.mcp`, `Routing.mcp`,
  `mcpSseEndpoint`, `mcpPostEndpoint`)
- Executed probes against a real Netty server on this machine with this repo's classpath (JDK 21):
  gate placement, short-circuit, CORS ordering, preflight, SSE gating + headers, restricted-header behaviour of
  `HttpURLConnection`, and a full pre-fix baseline of `KtorMcpServerManager` in both modes (external over TLS with
  a `keytool`-generated PKCS12). Probe sources were removed; working tree verified clean.
- Repository sources read directly: `mcp/KtorMcpServerManager.kt`, `mcp/McpSupervisor.kt`, `mcp/McpTls.kt`,
  `mcp/tools/McpTool.kt`, `audit/AuditLogger.kt`, `scanner/PassiveAiScannerAnalysis.kt`,
  `backends/cli/CliBackend.kt`, `config/AgentSettings.kt`, `config/McpSettings.kt`, `build.gradle.kts`,
  `detekt.yml`, `gradle.properties`, `docs/mcp-hardening.md`, existing MCP tests.

### Secondary (MEDIUM confidence)
- `https://repo1.maven.org/maven2/io/ktor/ktor-server-test-host-jvm/3.1.3/…pom` — existence/version check only

### Tertiary (LOW confidence)
- None. No claim in this document rests on an unverified web search.

---

## Metadata

**Confidence breakdown:**
- Pipeline mechanics & gate mechanism: **HIGH** — decompiled bytecode plus executed probes; the two agree.
- Pre-fix behaviour baseline: **HIGH** — measured against the real `KtorMcpServerManager` in both modes.
- Test tooling (OkHttp vs `HttpURLConnection`, TLS in-test, SSE body): **HIGH** — measured.
- SEC-05 sub-items: **HIGH** for the reproductions and the Gradle seam; **MEDIUM** for the recommended IPv6
  parse (correct by spec, not yet implemented/tested here).
- Blocked-request logging shape: **MEDIUM** — the in-repo patterns are verified; the payload design and the
  hash-vs-plaintext choice are recommendations pending maintainer confirmation.
- `McpSupervisor` accommodation: **MEDIUM** — the breakage is verified by reading the code path; the proposed
  fix was not executed.

**Research date:** 2026-08-05
**Valid until:** 2026-09-04 (30 days — Ktor 3.1.3 and kotlin-sdk 0.5.0 are pinned; findings only expire if those
pins move)
