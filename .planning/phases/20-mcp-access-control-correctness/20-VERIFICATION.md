---
phase: 20-mcp-access-control-correctness
verified: 2026-08-08T13:07:44Z
status: gaps_found
score: 5/6 must-haves verified
overrides_applied: 0
gaps:
  - truth: "SC2 — With externalEnabled = false, a foreign Host returns 403 on /__mcp/health, /message AND /sse"
    status: partial
    reason: >-
      Holds over HTTP/1.1 (measured 403 on all three paths). Does NOT hold over HTTP/2. Local mode
      with the independent "Enable TLS" checkbox on negotiates h2 via ALPN, and over h2 a foreign
      authority (evil.example:<port>) returns 200 on /__mcp/health and 200 on /sse — measured by a
      transient verifier probe. Root cause: McpAccessControlPlugin.requestFacts reads
      call.request.headers["Host"], but Ktor 3.1.3's NettyHttp2ApplicationRequest builds engineHeaders
      by iterating Http2Headers with no Host synthesis from :authority, so facts.host == null on every
      HTTP/2 request. evaluateLocal's guard is `facts.host != null && !isLoopbackAuthority(...)`, so a
      null host skips the DNS-rebinding branch entirely — fail-open. The same fail-open applies to a
      Host-less HTTP/1.1 request. The audit payload's `host` field is likewise always null over h2.
    artifacts:
      - path: "src/main/kotlin/com/six2dez/burp/aiagent/mcp/McpAccessControlPlugin.kt"
        issue: "Line 119 reads only the HTTP/1 `Host` header; never falls back to the HTTP/2 `:authority` pseudo-header"
      - path: "src/main/kotlin/com/six2dez/burp/aiagent/mcp/McpAccessControlDecision.kt"
        issue: "evaluateLocal line 150 skips the Host check when facts.host == null (fail-open in a file whose KDoc claims fail-closed)"
      - path: "src/test/kotlin/com/six2dez/burp/aiagent/mcp/McpAccessControlPipelineTest.kt"
        issue: "SC2 is only exercised over cleartext HTTP/1.1; no test drives the local-mode gate over TLS/h2"
      - path: "src/test/kotlin/com/six2dez/burp/aiagent/mcp/McpAccessControlDecisionTest.kt"
        issue: "No test covers facts.host == null, so the fail-open branch is uncovered by design intent as well as by assertion"
    missing:
      - "Resolve the request authority from `:authority` when `Host` is absent (Ktor exposes it as headers[\":authority\"] on the h2 path), or deny when neither is present"
      - "A local-mode pipeline test that binds a TLS connector, asserts response.protocol == h2, and asserts a foreign authority is 403 on /__mcp/health, /message and /sse"
      - "A decision-core unit test pinning the chosen behaviour for facts.host == null"
  - truth: "The phase does not regress an existing build path"
    status: failed
    reason: >-
      Independently reproduced: `JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test
      -PstoreBuild=true` FAILS with `McpBuildFlagsVersionTest > storeBuild_flagStillGenerated()
      FAILED`. The new test hard-asserts `assertFalse(BuildFlags.STORE_BUILD)`, which is true only
      when the flag is not passed. `-PstoreBuild=true` is the BApp Store artifact build path, so the
      phase made that path un-testable. Not a ROADMAP success criterion, but a regression this phase
      introduced into a path that matters (BApp Store submission #231).
    artifacts:
      - path: "src/test/kotlin/com/six2dez/burp/aiagent/mcp/McpBuildFlagsVersionTest.kt"
        issue: "Lines 36-43 assert STORE_BUILD is false unconditionally instead of asserting the flag tracks the -PstoreBuild property"
    missing:
      - "Assert the seam, not the value: e.g. assume/skip under -PstoreBuild=true, or assert BuildFlags.STORE_BUILD equals the System property the build passes through"
human_verification:
  - test: "Connect a real MCP client (Claude Desktop / Codex CLI) over local SSE with the gate active, list tools, call one read-only tool"
    expected: "Client connects, tool list returns, read-only tool executes — the gate does not deny a legitimate non-browser client"
    why_human: "Requires a live third-party MCP client and a running Burp instance; no automated seam exists"
  - test: "Connect a real MCP client in external mode with the bearer token configured"
    expected: "Client connects over TLS and authenticates; no 401 for a correctly configured client"
    why_human: "Same — live third-party client plus TLS trust configuration"
  - test: "Read docs/mcp-hardening.md §External Access items 4-6 and §Verification item 2"
    expected: "Neither still claims every request is bearer-validated; the /__mcp/health exemption and the local-only X-Burp-AI-Agent header are stated"
    why_human: "Doc accuracy is a judgement call. NOTE: items 4-6 are accurate; §Verification item 2 is NOT — see warning WR-02 below"
---

# Phase 20: MCP Access-Control Correctness — Verification Report

**Phase Goal:** Every MCP request passes through the extension's access-control checks before any handler runs, in both local and external mode — and there are tests that would have caught the current bypass.
**Verified:** 2026-08-08T13:07:44Z
**Status:** gaps_found
**Re-verification:** No — initial verification

**Verification method note.** All findings below rest on code read in the merged tree, JUnit XML from a
full `./gradlew test` run executed by this verifier (not the executor's), Ktor 3.1.3 bytecode inspected
with `javap`, and two transient probe test classes that this verifier wrote, ran, and deleted (tree
confirmed clean afterwards, `detekt-baseline.xml` byte-identical). No SUMMARY.md claim is credited
without independent evidence.

---

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| SC1 | `externalEnabled = true`: `POST /message` and `GET /sse` with no `Authorization` return 401 | ✓ VERIFIED | `evaluateExternal` (McpAccessControlDecision.kt:113-128) denies 401 before routing. `McpAccessControlExternalPipelineTest` 8 tests / 0 failures on a re-run full suite; `message_withoutAuthorization_returns401` and `sse_withoutAuthorization_returns401` assert exactly this against a real TLS Netty server. Verifier probe confirmed the connector negotiates `h2`, so SC1 is proven over HTTP/2. |
| SC2 | `externalEnabled = false`: foreign `Host`, foreign `Referer`, or browser UA with no `Origin` returns 403 on `/__mcp/health`, `/message` and `/sse` | ✗ **FAILED (partial)** | HTTP/1.1: all three vectors × three paths = 9 assertions green (`McpAccessControlPipelineTest`, 9/0). **HTTP/2: broken.** Verifier probe, local mode + TLS: `PROBE-AUTH h2 path=/__mcp/health protocol=h2 code=200` and `PROBE-AUTH h2 path=/sse protocol=h2 code=200` for authority `evil.example:<port>`, versus `h1 … code=403` for the identical request. Cause: `facts.host == null` over h2 (see gap 1). |
| SC3 | Four security headers present on routes Ktor RESOLVES — not only 404s — deterministically over HTTP/1.1 and HTTP/2 | ✓ VERIFIED (see warning) | Appended in the plugin's `Allow` branch in the pre-routing `Plugins` phase (McpAccessControlPlugin.kt:102, 129-135). HTTP/1.1: `health_matchedRouteCarriesAllFourSecurityHeaders` (matched 200) and `sse_authorizedConnectCarriesSecurityHeaders` (`text/event-stream` 200) green. HTTP/2: verifier probe `PROBE-EXT protocol=h2 … xfo=DENY xcto=nosniff rp=same-origin csp=default-src 'none'`; also covered incidentally by `message_withValidBearer_reachesHandlerAndCarriesSecurityHeaders`, which runs on the TLS connector. |
| SC4 | The SC1–SC3 regression tests FAIL against the pre-fix manager and pass after; the three pass-both assertions are not credited | ✓ VERIFIED | Rollback baseline `b1c32704e5c4…` independently confirmed pre-fix by `git show`: `Implementation("burp-ai-agent", "0.6.0")` at :97, `routing {` at :154, `intercept(ApplicationCallPipeline.Call)` at :176 — i.e. the Call-phase interceptor registered AFTER routing. Transcript records exit 1, `24 tests completed, 12 failed`, all 12 `org.opentest4j.AssertionFailedError`, `grep -c 'e: file'` = 0 (assertion evidence, not compile-failure evidence); restored run exit 0, 24/0. The 12 recorded failure line numbers exclude `foreignOrigin_isRejected` (:167) and both `shutdownWithoutToken_stillReturns401` sites (:246, :198), so the three pass-both assertions are correctly not credited. Assertion #8 disclosure judged **acceptable** — see note below. |
| SC5 | Real project version advertised; loopback check accepts `[::1]:<port>`; blank token cannot authenticate | ✓ VERIFIED (see warning) | 5a: `BuildFlags.VERSION = "0.9.2"` generated from `build.gradle.kts:15`, consumed at `KtorMcpServerManager.kt:110`; hardcoded `0.6.0` gone from the tree. 5b: `isLoopbackAuthority("[::1]:9876", 9876)` and the expanded form assert true (`KtorMcpServerManagerSecurityTest` 7/0, `McpAccessControlDecisionTest` 38/0). 5c: `isAuthorizedBearer` guards `token.isNotBlank()` BEFORE building the expected string; unit + integration (`blankConfiguredToken_rejectsEveryAuthenticatedPath`) green. |
| SC6 | `/__mcp/shutdown` still returns 401 without a token and 200 with one | ✓ VERIFIED | `McpServerIntegrationTest.startsServerAndServesHealthAndShutdownEndpoints` asserts health 200 (:74), shutdown-no-token 401 (:82), shutdown-with-token 200 (:90) — 1 test / 0 failures on the re-run. The in-handler check survives at `KtorMcpServerManager.kt:207-212` and now delegates to `isAuthorizedBearer`, inheriting the 5c guard. |

**Score:** 5/6 truths verified

**SC4 assertion-#8 disclosure — judged acceptable, not a gap.** Gate assertion #8
(`isLoopbackAuthority("[::1]:9876", 9876)` → true) cannot be turned red by rolling back
`KtorMcpServerManager.kt` alone, because it exercises a *new pure function* in
`McpAccessControlDecision.kt`, which the rollback deliberately leaves in place. SC4's own contract is
scoped to "the SC1–SC3 regression tests"; #8 is an SC5b unit assertion and therefore outside that
scope. It is covered independently by `KtorMcpServerManagerSecurityTest` and
`McpAccessControlDecisionTest`, both re-run green by this verifier. The disclosure is explicit rather
than papered over. **Caveat:** this verifier did not re-execute the rollback experiment (≈13 min plus a
transient `src/` mutation). What was independently verified is the *legitimacy of the baseline* and the
*internal consistency of the recorded failure set* — not a second execution of it.

### Deferred Items

None. Phase 25 (SEC-07) covers the takeover token leak and `SsrfGuard` notations; Phase 26 covers
coverage, the detekt baseline, and `SECURITY.md` / `README.md` / `SPEC.md` / `DECISIONS.md` / GitBook —
none of these cover the HTTP/2 authority gap, the `parseAuthority` overflow, the `-PstoreBuild=true`
regression, or `docs/mcp-hardening.md` accuracy. Nothing here is deferrable on roadmap evidence.

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `src/main/kotlin/.../mcp/McpAccessControlDecision.kt` | Pure `evaluate` + predicates, no I/O | ✓ VERIFIED | 223 lines. `internal fun evaluate` present; imports only `io.ktor.http` (engine-free) — the purity claim holds by inspection of the import list. Wired: called from the plugin. |
| `src/main/kotlin/.../mcp/McpAccessControlPlugin.kt` | `createApplicationPlugin("McpAccessControl"…)` in the `Plugins` phase | ⚠️ VERIFIED with defect | Plugin exists, `onCall` present, `isCommitted` guard present, `onBlocked` invoked in the `Deny` branch before `respond`. Defect: `requestFacts` reads only the HTTP/1 `Host` header (gap 1). |
| `src/main/kotlin/.../mcp/McpBlockedRequestReporter.kt` | D-06/D-07/D-09/D-10 observability | ✓ VERIFIED | `mcp_transport_blocked` constant, `AuditLogger.emitGlobal`, `Hashing.sha256Hex`, control-char strip + 200-char truncation, per-reason CAS window. 16/0 unit tests. |
| `src/main/kotlin/.../mcp/KtorMcpServerManager.kt` | Gate install, reporter wiring, mode-aware health, `BuildFlags.VERSION` | ✓ VERIFIED | `install(CORS)`:154 → `install(McpAccessControl)`:187 → `routing {`:196 → `mcp {`:233 confirmed by reading. The pre-fix `intercept(ApplicationCallPipeline.Call)` block is deleted and replaced with a do-not-restore comment (:224-231). `onBlocked` builds a real `reporter.report(...)` call (:191-193), not a no-op. |
| `src/main/kotlin/.../mcp/McpSupervisor.kt` | Mode-aware probe; never presents the token | ✓ VERIFIED | `probeExistingServer` (:254-285) requires the identity header only when `!externalEnabled`; degrades to a liveness check in external mode. Token appears only in `requestRemoteShutdownWithToken` (:301) — D-05 boundary intact. |
| `docs/mcp-hardening.md` | D-01/D-02/D-12 corrections | ⚠️ VERIFIED with defect | Items 4-6 are accurate and specific (health exemption, local-only header, `EventSource` unsupported). §Verification item 2 contains a false claim — see WR-02. |
| `build.gradle.kts` + generated `BuildFlags.kt` | `VERSION` seam | ✓ VERIFIED | `build/generated/buildflags/.../BuildFlags.kt` contains `const val VERSION = "0.9.2"`, matching `build.gradle.kts:15`. |
| Five new/extended test classes | SC1–SC3, SC5, D-02, D-06 coverage | ✓ VERIFIED | All present, all outside the `-PexcludeHeavyTests=true` exclusion globs (`*PipelineTest`, `*DecisionTest`, `*ProbeTest`, `*VersionTest`, `*ReporterTest` — none match `*IntegrationTest`/`*ConcurrencyTest`/`*BackpressureTest`/`*RestartPolicyTest`/`*SupervisionTest`). |

### Key Link Verification

| From | To | Via | Status | Details |
|------|-----|-----|--------|---------|
| `KtorMcpServerManager` | `McpAccessControl` | `install(McpAccessControl)` after CORS, before routing | ✓ WIRED | Line 187; order confirmed structurally AND behaviourally (foreign `Origin` still gets CORS's own 403, gate does not double-respond). |
| `McpAccessControlPlugin` | `evaluate(facts, settings)` | single `when` over `GateDecision` in `onCall` | ✓ WIRED | Line 80. |
| `McpAccessControlPlugin` | `McpBlockedRequestReporter.report` | `pluginConfig.onBlocked` in the `Deny` branch | ✓ WIRED | `onBlocked(decision)` at :84 **before** `call.respond` — and proven end-to-end, not just structurally: `foreignHost_emitsExactlyOneTransportBlockedAuditEvent` observes exactly one real `mcp_transport_blocked` event with `reason=host_mismatch`. A no-op lambda would fail that test. |
| `KtorMcpServerManager` | `McpBlockedRequestReporter` | `onBlocked = { deny -> reporter.report(...) }` | ✓ WIRED | Lines 125, 191-193; one reporter per `start()`, not per request (rate-limit windows are instance state). |
| `KtorMcpServerManager` | `BuildFlags.VERSION` | `Implementation("burp-ai-agent", BuildFlags.VERSION)` | ✓ WIRED | Line 110. |
| `McpAccessControlPlugin.requestFacts` | HTTP/2 request authority | — | ✗ **NOT WIRED** | No `:authority` read. `facts.host` is null on every h2 request; the DNS-rebinding branch has no input. This is gap 1. |

### Data-Flow Trace (Level 4)

| Artifact | Data Variable | Source | Produces Real Data | Status |
|----------|---------------|--------|--------------------|--------|
| `McpAccessControlPlugin` | `facts.origin` | `headers["Origin"]` | Yes (HTTP/1.1 + HTTP/2) | ✓ FLOWING |
| `McpAccessControlPlugin` | `facts.referer` | `headers["Referer"]` | Yes — probe: foreign `Referer` → 403 over `h2` | ✓ FLOWING |
| `McpAccessControlPlugin` | `facts.userAgent` | `headers["User-Agent"]` | Yes | ✓ FLOWING |
| `McpAccessControlPlugin` | `facts.authorization` | `headers["Authorization"]` | Yes (SC1 green over `h2`) | ✓ FLOWING |
| `McpAccessControlPlugin` | `facts.host` | `headers["Host"]` | **HTTP/1.1 yes; HTTP/2 always null** | ✗ **DISCONNECTED over h2** |
| `McpBlockedRequestReporter` | audit `host` field | `deny.facts.host` | Inherits the above — always null over h2 | ⚠️ HOLLOW over h2 |
| `KtorMcpServerManager` | `Implementation` version | `BuildFlags.VERSION` (generated) | Yes — `"0.9.2"` | ✓ FLOWING |

### Behavioural Spot-Checks

| Behaviour | Command | Result | Status |
|-----------|---------|--------|--------|
| Full suite green on the merged tree | `JAVA_HOME=…21 ./gradlew test` | `BUILD SUCCESSFUL in 12m 46s`; zero `<failure` elements across all `build/test-results/test/*.xml` | ✓ PASS |
| Phase test inventory | JUnit XML count triples | Pipeline 9/0/0, ExternalPipeline 8/0/0, SecurityTest 7/0/0, ServerIntegration 1/0/0, BuildFlagsVersion 2/0/0, SupervisorProbe 4/0/0, Decision 38/0/0, Reporter 16/0/0 | ✓ PASS |
| TLS connector protocol | transient probe, `response.protocol` | `h2` — HTTP/2 is genuinely negotiated, so h2 behaviour is in scope, not hypothetical | ✓ PASS |
| Four headers over HTTP/2 | transient probe on external TLS | `xfo=DENY xcto=nosniff rp=same-origin csp=default-src 'none'` | ✓ PASS |
| Foreign authority denied in local mode | transient probe, h2 vs h1, `/__mcp/health` and `/sse` | h1 → **403 / 403**; h2 → **200 / 200** | ✗ **FAIL** |
| Store-build artifact path | `./gradlew test -PstoreBuild=true` | `McpBuildFlagsVersionTest > storeBuild_flagStillGenerated() FAILED` | ✗ **FAIL** |
| Rollback baseline is genuinely pre-fix | `git show b1c3270:…/KtorMcpServerManager.kt` | `"0.6.0"`:97, `routing {`:154, `intercept(ApplicationCallPipeline.Call)`:176 | ✓ PASS |
| QUAL-07 baseline unchanged | `git diff --stat detekt-baseline.xml` | empty | ✓ PASS |
| Working tree clean after probes | `git status --porcelain` | empty | ✓ PASS |

### Probe Execution

| Probe | Command | Result | Status |
|-------|---------|--------|--------|
| — | `find scripts -path '*/tests/probe-*.sh'` | no matches; no PLAN/SUMMARY declares a `probe-*.sh` | N/A — SKIPPED (this repo has no shell-probe convention; SC4's process gate plays that role and is verified above) |

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|-------------|-------------|--------|----------|
| **SEC-04** | 20-01…20-06 (all declare it) | Every MCP request subject to access control before any handler; external 401 on `/message` + SSE; local 403 on foreign `Origin` / foreign `Host` / browser UA without `Origin`; four security headers on matched routes; regression tests that fail pre-fix | ⚠️ **SUBSTANTIALLY SATISFIED — one limb BLOCKED** | The structural bypass is genuinely closed: checks moved to the pre-routing `Plugins` phase, the dead Call-phase interceptor deleted, denial provably short-circuits the handler, and the whole thing is nailed down by the SC4 red-before-green gate. Satisfied limbs: external 401s, foreign `Origin` 403, foreign `Referer` 403, browser-UA-without-`Origin` 403, headers on matched routes, pre-fix-failing tests. **Not satisfied:** "a foreign `Host` is rejected with 403" over HTTP/2 (gap 1). |
| **SEC-05** | 20-01, 20-02, 20-03, 20-04, 20-06 | Real build version instead of `0.6.0`; `isValidHost` handles bracketed IPv6 consistently with `isLoopbackHost` accepting `::1`; blank bearer token cannot authenticate an external request | ✓ **SATISFIED** | All three findings (11, 12, 19) closed and asserted: `BuildFlags.VERSION = "0.9.2"` wired into `Implementation`; the three predicates unified behind one `isLoopbackAuthority` that accepts `[::1]:<port>`, `[0:0:0:0:0:0:0:1]:<port>` and bare `::1`; `isAuthorizedBearer` fails closed on a blank configured token at unit and integration level. See warning WR-01 for a residual defect in the shared parser that does not break SEC-05's stated text. |

**Orphaned requirements:** none. `.planning/REQUIREMENTS.md:45-46` maps only SEC-04 and SEC-05 to Phase 20; both are claimed by the plans and both are assessed above.

**Explicit answer to the traceability question.** No single plan delivered either ID, and none marked
them — correctly so. Taken as a whole, **SEC-05 is genuinely satisfied.** **SEC-04 is not yet fully
satisfied:** its own text names a foreign `Host` as one of three local-mode 403 vectors, and that
vector does not fire over HTTP/2 in a user-reachable configuration. Do not tick SEC-04 in
REQUIREMENTS.md until gap 1 is closed.

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| — | — | `TBD` / `FIXME` / `XXX` / `TODO` / `HACK` / `PLACEHOLDER` in any file this phase touched | — | **None found.** Debt-marker gate passes. |
| `McpAccessControlDecision.kt` | 150 | Guard-and-skip on null attacker-controlled input (`facts.host != null && !isLoopback…`) | 🛑 Blocker | Fail-open in a file whose KDoc claims "the fail-closed shape". This is the mechanism of gap 1. |
| `McpAccessControlDecision.kt` | 174 | `groupValues[2].toIntOrNull()` conflating "absent" with "unparseable" | ⚠️ Warning | WR-01, below. |
| `McpBlockedRequestReporter.kt` | 53 | Accepted-residual rationale asserts a fact that is false in the relevant mode | ⚠️ Warning | CR-01, below. |
| `McpBuildFlagsVersionTest.kt` | 39-43 | Test asserts a build-flag *value* rather than the *seam* | ⚠️ Warning | Gap 2 — breaks `-PstoreBuild=true`. |

### Warnings — code-review findings independently assessed

**WR-01 — CONFIRMED by reading the source. Warning, not a gap.**
`parseAuthority` (McpAccessControlDecision.kt:166-175) maps a port that overflows `Int` to `null`, and
`isLoopbackAuthority` (:190) skips the port comparison when `port == null`. Therefore
`isLoopbackAuthority("localhost:99999999999", 9876)` returns **true** while `"localhost:65536"` is
correctly denied. A fail-open in the one function the file's own KDoc calls "the fail-closed shape".
Why this is a warning and not an SC5 failure: SC5's stated criterion is *acceptance* of `[::1]:<port>`,
which works; and the bypass value must still be a loopback *hostname*, so it yields an attacker no
rebinding capability. It nevertheless contradicts SEC-05's "handles authorities consistently" and
should be fixed alongside gap 1 (both live in the same parser).

**WR-02 — CONFIRMED by reading the source. Warning.**
`docs/mcp-hardening.md` §Verification item 2 lists a foreign `Origin` among the local-mode 403 cases
and then states "the reason is recorded in Burp's Output tab and, when audit logging is enabled, in the
audit log". For a foreign `Origin` that is false: CORS is installed first in the same `Plugins` phase,
responds 403, and the gate's `if (!call.response.isCommitted)` guard then skips `evaluate` entirely, so
`onBlocked` never fires — no audit event, no Output line. A docs-vs-code contradiction introduced by
this phase. Fix the sentence (or route the CORS denial through the reporter).

**WR-04 — CONFIRMED, and escalated to gap 1.** See the truths table and the gaps frontmatter. Two
independent lines of evidence: `javap` on `ktor-server-netty-jvm-3.1.3` shows
`NettyHttp2ApplicationRequest.engineHeaders` is a `HeadersBuilder` populated by iterating
`Http2Headers` entries with no `Host` synthesis, and `NettyChannelInitializer` advertises `h2` +
`http/1.1` over ALPN on SSL connectors; and a live probe measured `protocol=h2` with a foreign
authority reaching `200` on `/sse`. Local mode + TLS is user-reachable — `SettingsPanel.kt:208` exposes
an independent "Enable TLS" checkbox, and `AgentSettings.kt:1285` only *forces* TLS on when external is
on, it never forces it off when external is off. **Mitigating context that keeps this out of
"critical":** a browser DNS-rebinding attempt over h2 is still caught by the
`BROWSER_NO_ORIGIN` branch (a same-origin `fetch` sends no `Origin`, and `User-Agent` *is* forwarded
over h2 — probe-confirmed via the foreign-`Referer` 403), and the default local configuration is
cleartext HTTP/1.1 where the check works. The `Host` limb is therefore defence-in-depth here — but it
is a limb SEC-04 and SC2 both name explicitly, and it fails open silently.

**CR-01 — assessed as separate follow-up work, not a phase-goal failure.**
The unauthenticated 401 path in external mode does now reach `AuditLogger.emitGlobal`, and D-09's
per-reason window covers only the Output line, so with audit logging enabled a remote unauthenticated
peer can drive one synchronous `~/.burp-ai-agent/audit.jsonl` append per request on a Netty event-loop
thread. Pre-phase, blocked requests emitted nothing, so the primitive is new. It does not compromise
the phase goal — it is a consequence of D-06, which is a locked decision — and it is disclosed in
`McpBlockedRequestReporter.kt:46-54` as accepted residual T-20-12. **However, the stated acceptance
rationale is wrong in the mode that matters:** ":53 says "Accepted because the source is
loopback-only", which is untrue precisely in external mode, the only mode with an unauthenticated 401
path. Recommend: correct that KDoc rationale now (it is load-bearing for a future reader's risk
assessment), and file the bounded/async audit sink as follow-up work rather than blocking this phase.

**SC3 coverage warning.** No test asserts `response.protocol`, so the HTTP/2 half of SC3 is
coverage-by-accident: it holds today only because the TLS connector happens to negotiate `h2` and
OkHttp happens not to be protocol-pinned. If either changes, SC3's h2 claim silently loses its evidence
with no test going red. Consider adding `assertEquals(Protocol.HTTP_2, response.protocol)` to the
external pipeline test — one line, and it converts an accident into a contract.

### Human Verification Required

Two items are genuinely unautomatable and are carried in the frontmatter: a real MCP client (Claude
Desktop / Codex CLI) connecting over local SSE, and the same in external mode with a token. Both come
from `20-VALIDATION.md` §Manual-Only Verifications and neither is closeable by grep. The third
frontmatter item (doc accuracy) is partly resolved: items 4-6 of §External Access are accurate; the
judgement call left for a human is whether to accept the §Verification item 2 inaccuracy (WR-02) or fix
it now.

These do not change the status — gaps take precedence over `human_needed`.

### Gaps Summary

The phase's central claim is true and well-proven: the access-control checks really did move from a
dead `Call`-phase interceptor registered after `routing{}` into a `Plugins`-phase
`createApplicationPlugin`, denial provably short-circuits the handler, the reporter is genuinely wired
(observed via a real emitted audit event, not a non-null lambda), and the SC4 red-before-green
experiment is legitimate — the rollback baseline was independently confirmed to contain the pre-fix
code, and the recorded failure set correctly excludes the three assertions that pass both before and
after. SC1, SC3, SC4, SC5 and SC6 are verified. SEC-05 is genuinely satisfied.

Two gaps remain.

**Gap 1 is the same class of defect the phase exists to eliminate: a check that exists but does not
run on a reachable path.** The gate reads `Host` as an HTTP/1 header. Ktor 3.1.3 does not synthesise
`Host` from HTTP/2's `:authority`, and `evaluateLocal` skips the DNS-rebinding branch when `host` is
null instead of denying. In local mode with TLS enabled — an independent, user-facing checkbox — the
connector negotiates `h2` and a foreign authority walks to `200` on `/sse`. This was measured, not
inferred. The fix is small (read `:authority` when `Host` is absent, and decide explicitly what a
missing authority means) and the missing test is small (one local-mode TLS pipeline test that asserts
`protocol == h2`). Until then SC2 is half-met and SEC-04's foreign-`Host` limb is open.

**Gap 2 is a self-inflicted build regression**, unrelated to security but real and reproduced by this
verifier: `./gradlew test -PstoreBuild=true` now fails, because a new test asserts
`BuildFlags.STORE_BUILD == false` unconditionally instead of asserting that the flag tracks the Gradle
property. That is the BApp Store artifact build path, which matters given the live submission. One-line
fix.

Neither gap is deferrable — nothing in Phases 21-26 covers them.

---

_Verified: 2026-08-08T13:07:44Z_
_Verifier: Claude (gsd-verifier)_
