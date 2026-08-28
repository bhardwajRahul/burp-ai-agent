---
phase: 20-mcp-access-control-correctness
verified: 2026-08-28T14:05:00Z
status: passed
score: 6/6 must-haves verified
behavior_unverified: 0
overrides_applied: 0
re_verification:
  round: 3
  previous_status: human_needed
  previous_score: 6/6
  previous_verified: 2026-08-10T10:15:00Z
  previous_tree: cf3fa53e52362d4f54ede262b328e1f07024a3dd
  tree_verified: 5f2d55ed33379a26e3162ee091ec3a2670608588
  drift_since_previous: "684 commits. 16 files / +1598 -93 under src/main/kotlin/com/six2dez/burp/aiagent/mcp/. Phases 21-28 all landed."
  reason: "The 6/6 recorded on 2026-08-10 was measured against a tree that no longer exists. This round re-establishes the same six must-haves against the current tree, and records the discharge of the two human-verification items."
  gaps_closed: []
  gaps_remaining: []
  regressions: []
  human_items_discharged:
    - "Local SSE with a real MCP client, TLS off (HTTP/1.1) AND TLS on (HTTP/2) — 20-HUMAN-UAT.md, maintainer, 2026-08-28, result: pass"
    - "External mode with bearer token over TLS — 20-HUMAN-UAT.md, maintainer, 2026-08-28, result: pass"
  history:
    - round: 1
      verified: 2026-08-08T13:07:44Z
      status: gaps_found
      score: 5/6
    - round: 2
      verified: 2026-08-10T10:15:00Z
      status: human_needed
      score: 6/6
      tree_verified: cf3fa53e52362d4f54ede262b328e1f07024a3dd
      gaps_closed:
        - "SC2 — foreign Host/authority returns 403 on /__mcp/health, /message AND /sse over HTTP/2 (was 200/400/200). Re-measured by that verifier with its own transient probe: 403/403/403 at protocol=h2."
        - "`./gradlew test -PstoreBuild=true` runs the suite to a clean exit again. The store-build test now asserts the generated-flag-vs-Gradle-property seam, and that verifier observed the seam take BOTH values (STORE_BUILD=true under the flag, false without) with the class green in each direction."
      gaps_remaining: []
      regressions: []
      requirement_verdict_change:
        SEC-04: "was SUBSTANTIALLY SATISFIED — one limb BLOCKED; now SATISFIED — tick it"
        SEC-05: "was SATISFIED; still SATISFIED (WR-01, its residual parser defect, is additionally closed)"
decision_coverage:
  honored: 13
  total: 13
  not_honored: []
human_verification: []
---

# Phase 20: MCP Access-Control Correctness — Verification Report

**Phase Goal:** Every MCP request passes through the extension's access-control checks before any handler runs, in both local and external mode — and there are tests that would have caught the current bypass.
**Verified:** 2026-08-28T14:05:00Z (round 3), tree `5f2d55e`
**Status:** passed (6/6 must-haves verified on the current tree; both human-verification items discharged)
**Re-verification:** **Yes — third pass, a drift re-verification 684 commits after the round-2 tree.**

---

# Round 3 (2026-08-28) — drift re-verification against tree `5f2d55e`

## Why this round exists, and what it is not

Round 2 scored 6/6 with `gaps_remaining: []`. It sat at `human_needed` only because two items needed a
live Burp and a real MCP client. **Both passed** on 2026-08-28 (`20-HUMAN-UAT.md`, maintainer, live Burp,
real MCP client, both TLS off/HTTP/1.1 and TLS on/HTTP/2, plus external mode with a bearer token over
TLS). That discharges the human gate.

**But the human gate is the smaller half of this round.** Round 2 recorded `tree_verified: cf3fa53e`.
HEAD is now `5f2d55e` — **684 commits later**, with **16 files changed under
`src/main/kotlin/com/six2dez/burp/aiagent/mcp/` (+1598 / −93)** since that tree, and phases 21 through 28
all landed in between. A 6/6 measured against a tree that no longer exists is not evidence about the tree
that does. This round re-establishes SC1–SC6 against the current tree and hunts for regressions
introduced by the intervening phases.

**Verdict up front: nothing regressed.** All six must-haves hold, and the strongest single fact is
structural rather than narrative — see below.

## The load-bearing fact: the round-2 regression suite is byte-identical and still green

Every test file that carries phase 20's SC evidence has **zero diff lines** across the 684-commit window
(`git diff --stat cf3fa53e..HEAD -- <path>` produces no output for each):

| Test file | Diff `cf3fa53e..HEAD` | Owns |
|---|---|---|
| `McpAccessControlPipelineTest.kt` | 0 lines | SC2 (HTTP/1.1), SC3 (HTTP/1.1) |
| `McpAccessControlExternalPipelineTest.kt` | 0 lines | SC1, SC3 (HTTP/2), SC5c |
| `McpLocalTlsAuthorityPipelineTest.kt` | 0 lines | SC2 (HTTP/2) |
| `McpAccessControlDecisionTest.kt` | 0 lines | SC5 loopback/IPv6, parser |
| `KtorMcpServerManagerSecurityTest.kt` | 0 lines | SC5c blank token |
| `BlockedRequestReporterTest.kt` | 0 lines | D-06/D-07/ADR-13 observability |
| `McpBuildFlagsVersionTest.kt` | 0 lines | SC5 version seam, store-build seam |
| `McpServerIntegrationTest.kt` | 0 lines | SC6 |
| `KtorMcpCorsPolicyTest.kt` | 0 lines | CORS/gate ordering |

This matters more than it first looks. The tests are **unchanged**, the production code **has** changed
(+31 in the decision core, +4 in the plugin, ±21 in the manager), and the tests are **green**. That is the
exact shape of a real non-regression result: nobody moved the goalposts and then declared victory. Had a
later phase weakened a control, an unchanged assertion would have gone red.

It also preserves SC4 for free. SC4 is a property of a *pair* — (regression test file, pre-fix
`KtorMcpServerManager`) — and both halves are immutable historical artifacts. The test files did not move,
so the 12 recorded pre-fix assertion failures remain the same 12 assertions.

## Test evidence produced by this verifier on the current tree

One Gradle run, JUnit XML read from `build/test-results/test/` (never console scraping). Exit 0.

| Class | tests | skipped | failures | errors |
|---|---|---|---|---|
| `McpAccessControlPipelineTest` | 9 | 0 | 0 | 0 |
| `McpAccessControlExternalPipelineTest` | 8 | 0 | 0 | 0 |
| `McpLocalTlsAuthorityPipelineTest` | 7 | 0 | 0 | 0 |
| `McpAccessControlDecisionTest` | 40 | 0 | 0 | 0 |
| `KtorMcpServerManagerSecurityTest` | 7 | 0 | 0 | 0 |
| `BlockedRequestReporterTest` | 22 | 0 | 0 | 0 |
| `McpBuildFlagsVersionTest` | 2 | 0 | 0 | 0 |
| `McpServerIntegrationTest` | 1 | 0 | 0 | 0 |
| `KtorMcpCorsPolicyTest` | 3 | 0 | 0 | 0 |
| `McpTakeoverProofTest` | 8 | 0 | 0 | 0 |
| `McpTakeoverPipelineTest` | 4 | 0 | 0 | 0 |
| `McpTakeoverSquatterTest` | 4 | 0 | 0 | 0 |
| `McpTakeoverCertificatePinTest` | 3 | 0 | 0 | 0 |

**118 tests, 0 failures, 0 errors.** The four `McpTakeover*` classes are not phase-20 artifacts; they are
included deliberately because Phase 25 added the one new limb that touches phase 20's gate, and its
containment has to be measured rather than assumed.

`McpServerIntegrationTest` is worth calling out: the `gsd-security-auditor` run of 2026-08-28 did **not**
include it (its `--tests` filter list stops at `*KtorMcpCorsPolicy*`), and it is the only end-to-end proof
of SC6 — the exact criterion the Phase 25 shutdown-route change touches. It was run here for that reason.

## Drift observation 1 — the fourth `Allow` limb in `evaluateExternal` (SEC-07 / Phase 25)

`McpAccessControlDecision.kt:160-162` now carries a fourth allow limb:

```
facts.path == SHUTDOWN_PATH &&
    McpTakeoverProof.accepts(settings.token, settings.host, settings.port, facts.epochMillis, facts.takeoverProof.orEmpty()) ->
    GateDecision.Allow
```

**Does this change the SC1 reading? No.** The limb is guarded by `facts.path == SHUTDOWN_PATH`. SC1 is
scoped to `POST /message` and `GET /sse`, neither of which can reach it. Confirmed behaviourally, not
structurally: `message_withoutAuthorization_returns401` and `sse_withoutAuthorization_returns401` are green
against a real TLS Netty server on this tree.

**Does it change the SC6 reading? Yes, in wording — not in truth, and this is worth stating plainly rather
than glossing.** SC6 says "`/__mcp/shutdown`'s existing **in-handler token check** still returns 401 without
a token and 200 with one". The in-handler check is no longer a token check alone: `KtorMcpServerManager.kt`
now accepts either `isAuthorized(authHeader, token)` **or** `McpTakeoverProof.accepts(...)`. The *observable
truth* SC6 asserts is unchanged and green; the *credential set* widened by one token-derived form. Four
independent facts contain it:

1. **The proof is keyed by the token.** `McpTakeoverProof.forTarget` runs `HmacSHA256` with
   `SecretKeySpec(token)`. A caller who does not already hold the token cannot mint an accepting value, so
   the limb admits nobody who could not already authenticate.
2. **It fails closed on a blank token.** `accepts` returns `false` on `token.isBlank() || presented.isBlank()`
   (`McpTakeoverProof.kt:99`) — *before* computing anything.
3. **It sits below the `BLANK_TOKEN` deny** (`:149` vs `:160`), so SEC-05 5c still fails closed first. Belt
   and braces with (2).
4. **Measured, not reasoned.** `McpTakeoverPipelineTest.anExternalModeShutdownWithNoCredentialIsStillRejected`
   asserts 401 for a no-credential shutdown *and* 401 for `X-Mcp-Takeover-Proof: not-a-real-proof`. Green.
   `McpServerIntegrationTest` still measures the 401-then-200 pair against a real running server. Green.

The limb also does not weaken the phase goal's "before any handler runs" clause: a valid proof produces
`GateDecision.Allow`, which lets the request *reach* the route — and the route then re-checks both
credential forms itself. Gate and handler both evaluate; neither is skipped.

## Drift observation 2 — `McpBlockedRequestReporter.sanitize` delegates to `sanitizeInline` (Phase 28 WR-05)

The D-07 log-injection control moved location: the private regex pair in the reporter was deleted and
`sanitize` now calls `sanitizeInline(value, MAX_HEADER_VALUE_LENGTH, TRUNCATION_MARKER)` in
`ToolApprovalGate.kt`. I checked the semantics rather than the delegation:

| Property | Round-2 reporter | Current `sanitizeInline` | Same? |
|---|---|---|---|
| Control-char class | `[\p{Cntrl}\u0080-\u009F]` | same class, character for character | Yes |
| Removal vs replacement | removed (CWE-117) | removed (CWE-117) | Yes |
| Whitespace collapse | `\s+` → `" "` | `\s+` → `" "` | Yes |
| Order | remove → collapse → trim → cap | remove → collapse → trim → cap | Yes |
| Cap | 200 | 200, passed as argument | Yes |
| Marker | `"..."` | `"..."`, passed as argument | Yes |

And the decisive check: `BlockedRequestReporterTest` is **byte-identical** to the round-2 version and its
22 tests are green on this tree. An unchanged D-07 test suite passing over a relocated implementation is
what proves the refactor was behaviour-preserving.

## Drift observation 3 — no second listener, no second routing block, no restored Call-phase interceptor

The failure mode this phase exists to prevent is "a control that exists but does not run on a reachable
path". Re-checked from scratch on the current tree:

- `grep -rn "embeddedServer" src/main/kotlin/` → exactly **one** call site, `KtorMcpServerManager.kt:133`.
- `grep -rn "routing\s*\{" src/main/kotlin/` outside that file → **no matches**.
- `grep -rn "ServerSocket(|HttpServer.create"` in `src/main` → **no matches**. `McpSupervisor` (+196 lines
  in this window) creates no listener; it is a client-side probe.
- `intercept(ApplicationCallPipeline.Call)` → the only hit in `src/main` is the **do-not-restore comment**
  at `McpAccessControlPlugin.kt:17`, not code.
- Install order in `KtorMcpServerManager.kt`: `install(CORS)` at :154, `install(McpAccessControl)` at :187,
  `routing {` at :196. The load-bearing order is intact.
- The gate is still a `createApplicationPlugin` with `onCall` (Plugins phase, pre-routing) and still opens
  with the `if (!call.response.isCommitted)` guard.

Only two explicit routes are registered (`/__mcp/health`, `SHUTDOWN_PATH`); `/sse` and `/message` come from
the SDK's `mcp { }` block inside the same application, behind the same Plugins-phase gate — which is what
the external pipeline test measures when it gets 401 on both.

Structural constants also survive: `HTTP_2_VERSION` (:113), `UNRESOLVABLE_AUTHORITY` (:122), the h2-only
fallback gate at `http2Authority` (:212), and `MAX_TCP_PORT = 65_535` with the `1..MAX_TCP_PORT` range check
at `McpAccessControlDecision.kt:236`. The authority branch is still unconditional (:197 — the line moved
from :166 purely because of the +31 SEC-07 additions above it).

## Gap-2 re-verified the hard way — full suite under `-PstoreBuild=true`

Round 2 closed gap 2 with the claim "`./gradlew test -PstoreBuild=true` runs the suite to a clean exit
again". That claim was **at genuine risk** in this tree, because Phase 24/26 added a *new* consumer of the
flag: `McpToolCatalog.available(storeBuild: Boolean = BuildFlags.STORE_BUILD)`, which filters the tool
catalogue to native tools only when the flag is set. A targeted `--tests '*McpBuildFlagsVersion*'` run would
not have caught a downstream class that depends on the catalogue size. So the full suite was run:

| Invocation | Generated `STORE_BUILD` | Classes | Tests | Failures | Errors | Exit |
|---|---|---|---|---|---|---|
| `./gradlew test -PstoreBuild=true --no-daemon` | `true` (read from `build/generated/`) | 181 | 1309 | 0 | 0 | **0** |
| `./gradlew test --tests '*McpBuildFlagsVersion*'` (no flag) | `false` | 1 | 2 | 0 | 0 | 0 |

The one skipped test in the store-build run is
`ExternalMcpClientManagerTest.connectAndListTools_returnsExpectedCount` — Phase 26 external-client
territory, unrelated to any phase-20 criterion.

The seam is still non-vacuous and still bidirectional: `build.gradle.kts:70` resolves `val storeBuild` once,
feeds it to the code generator and — at the line that closed gap 2 — into the test JVM as
`systemProperty("storeBuild.expected", storeBuild.toString())`. Both remain present. Under the flag the
generated constant is `true` and the assertion passes, so it compared `true == true`, not a vacuous
`false == false`.

## SC5's version seam, re-measured

The generated constant now reads `VERSION = "1.0.0"`, matching `build.gradle.kts:17` (`version = "1.0.0"`),
consumed at `KtorMcpServerManager.kt:110` as `Implementation("burp-ai-agent", BuildFlags.VERSION)`. The seam
tracked the project through `0.9.2` → `1.0.0` with **no test edit**, which is precisely what
`McpBuildFlagsVersionTest` was rewritten to allow: it asserts semver shape and `!= "0.6.0"` rather than a
literal. The stale-placeholder defect SEC-05 5a names cannot recur silently.

## Round-3 Observable Truths

| # | Truth | Status | Evidence (current tree `5f2d55e`) |
|---|---|---|---|
| SC1 | `externalEnabled = true`: `POST /message` and `GET /sse` with no `Authorization` return 401 | ✓ VERIFIED — no regression | `McpAccessControlExternalPipelineTest` byte-identical, 8/0 against a real TLS Netty server. The Phase 25 `Allow` limb is path-gated to `SHUTDOWN_PATH` and cannot be reached from either path. `invalidBearerForms_areRejected` and both blank-token tests green. |
| SC2 | `externalEnabled = false`: foreign `Host`, foreign `Referer`, or browser UA with no `Origin` returns 403 on `/__mcp/health`, `/message` and `/sse` | ✓ VERIFIED — no regression | HTTP/1.1: `McpAccessControlPipelineTest` byte-identical, 9/0 (`foreignHost_isRejectedOnEveryPath`, `foreignReferer_…`, `browserUserAgentWithoutOrigin_…`). HTTP/2: `McpLocalTlsAuthorityPipelineTest` byte-identical, 7/0 — three separate 403 methods for health/message/sse, each asserting `Protocol.HTTP_2` *before* the status, plus `loopbackAuthorityOverHttp2_stillReachesHealth` proving the fallback still serves legitimate h2 clients. `evaluateLocal` is untouched in the 684-commit diff. |
| SC3 | Four security headers on routes Ktor RESOLVES — not only 404s — deterministically over HTTP/1.1 and HTTP/2 | ✓ VERIFIED — no regression | All four (`X-Frame-Options: DENY`, `X-Content-Type-Options: nosniff`, `Referrer-Policy: same-origin`, `Content-Security-Policy: default-src 'none'`) asserted individually via `EXPECTED_SECURITY_HEADERS`, on a resolved route, with `assertEquals(Protocol.HTTP_2, …)` first. Green in both pipeline classes. Headers are still appended from the pre-routing `Plugins` phase. |
| SC4 | The SC1–SC3 regression tests FAIL against the pre-fix `KtorMcpServerManager` and pass after | ✓ VERIFIED — no regression | Both halves of the pair are intact. The test files show **zero** diff lines across 684 commits, so the recorded 12 pre-fix assertion failures are the same 12 assertions; and all of them pass on the current tree (9/0, 8/0, 7/0). No `@Disabled`, `@Ignore`, `assumeTrue` or `assumeFalse` appears in any phase-20 test file. Re-running the rollback was out of scope here — `src/` is read-only for this round — and unnecessary, since neither half of the pair moved. |
| SC5 | Real project version advertised; loopback check accepts `[::1]:<port>`; blank token cannot authenticate | ✓ VERIFIED — no regression | `BuildFlags.VERSION = "1.0.0"` = `build.gradle.kts:17`, consumed at `KtorMcpServerManager.kt:110`; `McpBuildFlagsVersionTest` 2/0 asserts semver and `!= "0.6.0"`. `McpAccessControlDecisionTest` 40/0 (IPv6 acceptances and the WR-01 out-of-range-port rejection). Blank token: `KtorMcpServerManagerSecurityTest` 7/0 plus `blankConfiguredToken_rejectsEveryAuthenticatedPath` / `…_stillAllowsHealth` green — and the new SEC-07 limb inherits the same guard. |
| SC6 | `/__mcp/shutdown` still 401 without a token and 200 with one | ✓ VERIFIED — no regression, with a wording note | `McpServerIntegrationTest` byte-identical, 1/1 green: 401 with no credential, then 200 with the token, against a real running server. Corroborated by `shutdownWithoutToken_stillReturns401` in both pipeline classes and by `anExternalModeShutdownWithNoCredentialIsStillRejected` (401 for no credential **and** for a forged proof). **Wording note:** the in-handler check now accepts a second, token-derived credential form (SEC-07). The observable truth SC6 states is unchanged; the criterion's phrase "the in-handler token check" is now narrower than the code. See Drift observation 1. |

**Score:** 6/6 truths verified (0 present-but-behaviour-unverified). Every truth here is behaviour-dependent
and every one is carried by a passing test that drives a **real Netty server** — not by symbol presence.

### Required Artifacts (round 3)

| Artifact | Expected | Status | Details on tree `5f2d55e` |
|---|---|---|---|
| `.../mcp/McpAccessControlPlugin.kt` | Pre-routing gate; transport-aware authority resolution | ✓ VERIFIED | +4 lines only (`takeoverProof` header read, `epochMillis` clock read). `requestFacts:190` still `headers["Host"] ?: http2Authority(call)`. h2-only gate at :212, `UNRESOLVABLE_AUTHORITY` sentinel at :122, `isCommitted` guard at :79 — all intact. Still reads `local`, never `origin`. |
| `.../mcp/McpAccessControlDecision.kt` | Pure decision core, fail-closed | ✓ VERIFIED | +31 lines, all SEC-07: `SHUTDOWN_PATH` const, two `RequestFacts` fields (`takeoverProof`, `epochMillis` defaulting to `0L` — fail-closed), and the fourth `Allow` limb. Authority branch still unconditional (:197). `MAX_TCP_PORT` range check intact (:236). Still engine-free; the clock is injected, so the core stays pure. |
| `.../mcp/McpBlockedRequestReporter.kt` | D-06/D-07/D-09/D-10 observability, bounded | ✓ VERIFIED | `sanitize` delegates to `sanitizeInline` with identical semantics (table above). ADR-13 machinery untouched: separate `windows` (:78) and `auditWindows` (:86) maps, `floodCapable` (:105), `consumeAuditWindow` (:128), shared `BLOCK_LOG_WINDOW_MS` (:19). 22/0. |
| `.../mcp/KtorMcpServerManager.kt` | Gate install order, reporter wiring, `BuildFlags.VERSION` | ✓ VERIFIED | ±21 lines: three `throw IllegalStateException` → `error(...)` (semantically identical), the `SHUTDOWN_PATH` constant replacing a literal, and the SEC-07 second credential form. CORS→gate→routing order unchanged; `reporter.report(..., System.currentTimeMillis())` unchanged; `BuildFlags.VERSION` unchanged. |
| `build.gradle.kts` | `VERSION` seam and the `storeBuild.expected` seam | ✓ VERIFIED | Both present. `val storeBuild` resolved once at :72; `systemProperty("storeBuild.expected", storeBuild.toString())` still in `tasks.test`. Observed carrying `true` and `false`. |
| `src/test/.../McpLocalTlsAuthorityPipelineTest.kt` | The local-mode TLS/h2 gate test | ✓ VERIFIED | Byte-identical, 7/0. Still named to miss all five `excludeHeavyTests` globs, so it still runs in the fast PR gate. |
| `src/test/.../McpBuildFlagsVersionTest.kt` | Assert the seam, not a value | ✓ VERIFIED | Byte-identical, 2/0 in both flag directions. No `assertFalse`. |
| `src/test/.../McpTestServerSupport.kt` | Shared fixtures for the pipeline tests | ✓ VERIFIED | +27 lines, **additive only** (`nonLoopbackTlsSettings` for Phase 25 WR-03). `localSettings`, `localTlsSettings` and `externalTlsSettings` — the three fixtures phase 20's tests depend on — are untouched. |
| `DECISIONS.md` | ADR-13 recording the D-06 amendment | ✓ VERIFIED | ADR-13 present at :140. Diff across the window is **additions only** (+117). `20-CONTEXT.md:73` still carries the `AMENDED by ADR-13` pointer. |
| `docs/mcp-hardening.md` | D-01/D-02/D-12 corrections (WR-02) | ✓ VERIFIED | Additions only (+9). The WR-02 split survives at :47-49 — item 2 scopes the recording guarantee to the external 401s and the three gate-visible local vectors, and the following bullet still states that a foreign `Origin` is **not recorded**, names Ktor's CORS plugin committing before the gate, and tells the operator how to distinguish expected silence from a broken audit trail. |

### Key Link Verification (round 3)

| From | To | Via | Status | Details |
|---|---|---|---|---|
| `McpAccessControlPlugin.requestFacts` | HTTP/2 request authority | `?: http2Authority(call)`, gated to `version == "HTTP/2"` | ✓ WIRED | Behavioural: foreign h2 authority → 403 on all three paths, loopback h2 authority → 200. Both directions green in `McpLocalTlsAuthorityPipelineTest`. |
| `evaluateLocal` | authority limb | unconditional `!isLoopbackAuthority(facts.host.orEmpty(), port)` | ✓ WIRED | `McpAccessControlDecision.kt:197`. Untouched by 684 commits. |
| `KtorMcpServerManager` | `McpAccessControl` | `install` after CORS, before `routing` | ✓ WIRED | :154 → :187 → :196. Sole `embeddedServer` call site. |
| `McpAccessControlPlugin` | `sanitizeInline` (relocated D-07 control) | `McpBlockedRequestReporter.sanitize` | ✓ WIRED | Same regex, same order, same cap and marker passed as arguments. Unchanged 22-test suite green. |
| `evaluateExternal` | `McpTakeoverProof.accepts` | `facts.path == SHUTDOWN_PATH && …` | ✓ WIRED, CONTAINED | New in this window. Path-gated, token-keyed, blank-fails-closed, below the `BLANK_TOKEN` deny, and pinned by a test asserting 401 for both no-credential and forged-proof. |
| `build.gradle.kts` `storeBuild.expected` | `McpBuildFlagsVersionTest` | system property | ✓ WIRED | Present at both ends; observed carrying `true` and `false` in this round. |

### Data-Flow Trace (Level 4, round 3)

| Artifact | Data variable | Source | Produces real data | Status |
|---|---|---|---|---|
| `McpAccessControlPlugin` | `facts.host` | `headers["Host"]` or the h2 connection point | Yes on both transports — value provably varies with client authority (403 foreign / 200 loopback over h2) | ✓ FLOWING |
| `McpBlockedRequestReporter` | audit `host` field | `deny.facts.host` | Inherits the above | ✓ FLOWING |
| `McpAccessControlDecision` | `facts.epochMillis` | injected by `requestFacts` from `System.currentTimeMillis()` | Yes — and defaults to `0L`, which is fail-closed for any fact built without a clock | ✓ FLOWING |
| `McpBuildFlagsVersionTest` | `expected` | `System.getProperty("storeBuild.expected")` ← `build.gradle.kts` | Yes — observed `true` and `false` | ✓ FLOWING |
| `KtorMcpServerManager` | `Implementation` version | generated `BuildFlags.VERSION` | Yes — `"1.0.0"`, tracking `build.gradle.kts:17` | ✓ FLOWING |

### Behavioural Spot-Checks (round 3)

| Behaviour | Command | Result | Status |
|---|---|---|---|
| SC1–SC6 suites on the current tree | one Gradle run, JUnit XML read | 13 classes, 118 tests, 0 failures, 0 errors | ✓ PASS |
| SC6 end-to-end shutdown pair | `McpServerIntegrationTest` (excluded from the auditor's filter; run here) | 401 then 200 against a real server, 1/0 | ✓ PASS |
| SEC-07 limb opens nothing | `McpTakeoverPipelineTest` | 401 for no credential and 401 for a forged proof | ✓ PASS |
| Store-build path, flag on, FULL suite | `./gradlew test -PstoreBuild=true --no-daemon` | 181 classes, 1309 tests, 0 failures, 0 errors, exit 0; generated `STORE_BUILD = true` | ✓ PASS |
| Store-build path, flag off | same without the flag | generated `STORE_BUILD = false`, 2/0 | ✓ PASS |
| Single listener invariant | `grep -rn "embeddedServer" src/main/kotlin/` | exactly 1 call site (`KtorMcpServerManager.kt:133`), with `install(McpAccessControl)` on it | ✓ PASS |
| No second routing block / raw listener | `grep -rnE "routing\s*\{\|ServerSocket(\|HttpServer.create" src/main/kotlin/` | no matches outside the single manager | ✓ PASS |
| Call-phase interceptor not restored | `grep -rn "intercept(ApplicationCallPipeline.Call)" src/main/kotlin/` | only the do-not-restore comment | ✓ PASS |
| Disabled-test scan on all phase-20 suites | `grep -nE "@Disabled\|@Ignore\|assumeTrue\|assumeFalse"` | no matches | ✓ PASS |
| Debt-marker gate on phase-20 files (+ the relocated control) | `grep -nE "TBD\|FIXME\|XXX\|TODO\|HACK\|PLACEHOLDER"` | 0 in all 7 files, and 0 in `ToolApprovalGate.kt` | ✓ PASS |
| `REQUIREMENTS.md` byte-frozen | `shasum -a 256` | `9b32196…fcfb4` — unchanged | ✓ PASS |
| Tree clean after verification | `git status --porcelain` | only pre-existing untracked paths; `src/` untouched | ✓ PASS |

### Probe Execution

| Probe | Command | Result | Status |
|---|---|---|---|
| — | `find scripts -path '*/tests/probe-*.sh'` | no matches; no PLAN/SUMMARY declares a `probe-*.sh` | N/A — SKIPPED (no shell-probe convention in this repo; SC4's red-before-green gate plays that role) |

### Test Quality Audit (round 3)

| Test file | Linked req | Active | Skipped | Circular | Assertion level | Verdict |
|---|---|---|---|---|---|---|
| `McpAccessControlPipelineTest` | SEC-04 | 9 | 0 | No | Behavioural (real server, status + header values) | ✓ Sound |
| `McpAccessControlExternalPipelineTest` | SEC-04, SEC-05 | 8 | 0 | No | Behavioural (real TLS server, protocol + status + 4 header values) | ✓ Sound |
| `McpLocalTlsAuthorityPipelineTest` | SEC-04 | 7 | 0 | No | Behavioural (real TLS/h2 server, protocol asserted before status) | ✓ Sound |
| `McpAccessControlDecisionTest` | SEC-05 | 40 | 0 | No | Value | ✓ Sound |
| `KtorMcpServerManagerSecurityTest` | SEC-05 | 7 | 0 | No | Value | ✓ Sound |
| `BlockedRequestReporterTest` | SEC-04 (D-06/D-07) | 22 | 0 | No | Value | ✓ Sound |
| `McpBuildFlagsVersionTest` | SEC-05 | 2 | 0 | **No** — the two sides reach the JVM by different mechanisms (codegen+classload vs system property); both observed taking both values | Value | ✓ Sound |
| `McpServerIntegrationTest` | SEC-04 | 1 | 0 | No | Behavioural (real server, 401→200 pair) | ✓ Sound |

**Disabled tests on requirements:** 0. **Circular patterns detected:** 0. **Insufficient assertions:** 0.

### Decision Coverage

`gsd-tools query check.decision-coverage-verify` over `20-CONTEXT.md`: **13 of 13** trackable decisions
honored by shipped artifacts, `not_honored: []`. Non-blocking gate; recorded for drift tracking.

### Requirements Coverage (round 3)

| Requirement | Description | Status | Evidence |
|---|---|---|---|
| **SEC-04** | Every MCP request subject to access control before any handler; external 401 on `/message` + SSE; local 403 on foreign `Origin` / `Host` / browser UA without `Origin`; four security headers on matched routes; regression tests that fail pre-fix | ✓ SATISFIED — **still**, on tree `5f2d55e` | Every limb re-measured green on the current tree. The single-listener, single-routing-block, Plugins-phase-gate invariants all re-derived from scratch. The one new gate limb in 684 commits is path-gated to `/__mcp/shutdown`, token-keyed, and pinned by a 401-on-forged-proof test. |
| **SEC-05** | Real build version instead of `0.6.0`; bracketed-IPv6 authorities handled consistently with `::1` accepted; blank bearer token cannot authenticate | ✓ SATISFIED — **still**, on tree `5f2d55e` | Version seam tracked `0.9.2` → `1.0.0` with no test edit. `McpAccessControlDecisionTest` 40/0 including the WR-01 out-of-range-port rejection. Blank-token guard green at unit and integration level, and the new SEC-07 path inherits it twice over. |

**No requirement decision is made by this round.** Both IDs were already ticked in
`.planning/REQUIREMENTS.md`; the file is byte-frozen and was verified unchanged
(`sha256 9b3219662ec0d007c1c82d64eed3ef2698bd306ce69f01205ac9bbc3f42fcfb4`). **Orphaned requirements:** none.

### Anti-Patterns Found (round 3)

| File | Line | Pattern | Severity | Impact |
|---|---|---|---|---|
| all 7 phase-20 files + `ToolApprovalGate.kt` | — | `TBD` / `FIXME` / `XXX` / `TODO` / `HACK` / `PLACEHOLDER` | — | **None found.** Debt-marker gate passes. |
| all 8 phase-20 test files | — | `@Disabled` / `@Ignore` / `assumeTrue` / `assumeFalse` | — | **None found.** |
| `KtorMcpServerManager.kt` | 80, 83, 137 | `throw IllegalStateException(...)` → `error(...)` | ℹ️ Info | Kotlin's `error()` throws `IllegalStateException` with the same message. Semantically identical; a detekt/ktlint idiom change, not a behaviour change. |

### Human Verification

**Complete — both items discharged.** `20-HUMAN-UAT.md` records `status: complete`, `total: 2, passed: 2,
issues: 0`, confirmed by the maintainer on 2026-08-28 against a live Burp with a real MCP client:

1. **Local SSE, both transports.** Connected and called a read-only tool with TLS off (HTTP/1.1) *and*
   TLS on (HTTP/2). Documented residual 3 did **not** fire: the client's h2 `:authority` carried an
   explicit port, matched the bound loopback socket, and was allowed.
2. **External mode, bearer token over TLS.** Connected and authenticated; no spurious 401. This is the
   negative control on over-rejection that complements SC1's automated "no header → 401" half.

The UAT notes are scrupulous about what these results do *not* establish, and that scoping is adopted
here verbatim rather than rounded up — see the residual below.

### Residual risks — assessed, and why none is a gap

**Residual 3 (portless h2 `:authority`) is narrowed, not closed.** A client that emits a portless
`:authority` over HTTP/2 would still acquire the scheme default port (443), fail the authority comparison
and be denied. The UAT result narrows its observed reach to "not this client", not to "never". This stays
a documented accepted residual in `McpAccessControlPlugin.requestFacts`, not a gap: **no phase-20 success
criterion asserts that a portless h2 authority must be allowed** — it is an availability trade-off of a
fail-closed control, and the only automatable half of it (that the gate denies a non-matching authority)
is exactly what SC2 requires. It is not re-raised as a human-verification item; the live check it called
for has been performed and passed.

**SC6's end-to-end proof does not run in the fast PR gate.** `McpServerIntegrationTest` matches the
`*IntegrationTest` glob in `excludeHeavyTests`, and `.github/workflows/build.yml:47` runs
`./gradlew test -PexcludeHeavyTests=true`. So a regression to the shutdown 401/200 pair would not be
caught on a PR. It **is** caught before anything ships: `nightly-regression.yml:26` and `release.yml:33`
both run the unfiltered `test` task. Recorded as Info — a deliberate fast-gate/full-gate split, plus the
reason `McpServerIntegrationTest` was run explicitly in this round rather than inherited from the security
auditor's filter list.

**Carried forward unchanged from round 2, not re-litigated:** the `Integer.parseInt` guard assessed on
failure modes rather than coverage; residuals 1 and 2 (an absent `:authority` over h2 is unclosable, and
20-07's absent-authority DENY is therefore scoped to HTTP/1.1); ADR-13 × the runbook's one-record-per-window
imprecision; and `20-07-SUMMARY.md`'s missing `Self-Check` section. All were dispositioned in round 2 and
nothing in the 684-commit window disturbs any of them.

### Gaps Summary (round 3)

**No gaps. No regressions. 684 commits and eight phases later, all six must-haves hold on the tree that
actually exists.**

The result is not a re-assertion of round 2's argument — it rests on evidence produced against tree
`5f2d55e`: 118 tests in the SC-relevant classes green, a full 1309-test suite green under the store-build
flag, the single-listener and gate-ordering invariants re-derived from scratch, and the one new gate limb
in the whole window measured to reject a forged credential.

The finding worth carrying forward is the shape of the evidence rather than its verdict. Phase 20's
regression suite is **byte-identical** across the entire window while the production files it guards grew
by +1598 lines. Unchanged assertions passing over changed code is the only form of non-regression evidence
that cannot be manufactured by editing the test — and it is the direct answer to the question this round
was convened to ask.

Two readings shifted and are recorded rather than smoothed over: SC1's is **unchanged** (the SEC-07 limb
is path-gated away from `/message` and `/sse`), while SC6's criterion **wording** is now narrower than the
code it describes (the in-handler check accepts a second, token-derived credential form). SC6's observable
truth — 401 without, 200 with — is measured green either way.

---

# Round 2 report (2026-08-10, tree `cf3fa53`) — preserved verbatim

> Recorded status at the time: `human_needed`, 6/6 must-haves verified, two unautomatable manual checks
> outstanding (both since discharged — see round 3). Preserved unedited; its gap-closure account for SC2
> and the store-build path stands as written.

**Verified:** 2026-08-10T10:15:00Z
**Status:** human_needed (6/6 must-haves verified; two genuinely unautomatable manual checks remain)
**Re-verification:** **Yes — second pass, after the 20-07…20-10 gap-closure wave.** Tree `cf3fa53`.

---

## What changed since the first pass (2026-08-08, `gaps_found`, 5/6)

The first pass verified SC1, SC3, SC4, SC5, SC6 and filed two gaps: SC2's foreign-`Host` limb did not
fire over HTTP/2 (BLOCKER), and this phase had broken `./gradlew test -PstoreBuild=true` (the BApp Store
artifact build path). Four gap-closure plans then executed: 20-07 (fail-closed decision core + WR-01),
20-08 (HTTP/2 authority fallback + a TLS/h2 pipeline test), 20-09 (CR-01 audit-flood bound + ADR-13),
20-10 (store-build seam + WR-02 doc fix + the SC3 protocol assertion).

**Both gaps are closed, and closed on evidence this verifier produced itself.** Nothing carried forward.
SEC-04 is now satisfied in full.

**Verification method note.** All findings rest on: source read in the merged tree; JUnit XML from Gradle
runs executed by this verifier (never console scraping); the generated `BuildFlags.kt` read from
`build/generated/`; and **one transient probe test class this verifier wrote, ran, and deleted**
(`VerifierGap1AuthorityProbe`). After deletion, `git status --porcelain`, `git diff --stat HEAD` and
`git diff --stat detekt-baseline.xml` were all empty. No SUMMARY.md claim is credited without independent
evidence, and the executors' own test runs are treated as claims, not proof — per the phase's own
"coverage-by-accident is not evidence" standard.

---

## Gap 1 — re-measured empirically, not inferred from green tests

A transient probe (`VerifierGap1AuthorityProbe`) drove two real Netty servers built through
`KtorMcpServerManager`: one in **local mode with TLS** (`externalEnabled = false`, `tlsEnabled = true` —
confirmed by reading `McpTestServerSupport.localTlsSettings`) and one local cleartext. The forged
authority is carried in the URL with an OkHttp `Dns` override to 127.0.0.1, because OkHttp drops an
explicit `Host` header on h2. Raw output, verbatim:

```
PROBE-AUTH authority=foreign  path=/__mcp/health protocol=h2 code=403
PROBE-AUTH authority=loopback path=/__mcp/health protocol=h2 code=200
PROBE-AUTH authority=foreign  path=/message      protocol=h2 code=403
PROBE-AUTH authority=loopback path=/message      protocol=h2 code=400
PROBE-AUTH authority=foreign  path=/sse          protocol=h2 code=403
PROBE-AUTH authority=loopback path=/sse          protocol=h2 code=200
PROBE-H1 noHost       -> HTTP/1.1 403 Forbidden
PROBE-H1 foreignHost  -> HTTP/1.1 403 Forbidden
PROBE-H1 loopbackHost -> HTTP/1.1 200 OK
```

`protocol=h2` on every row, so the transport is genuinely HTTP/2 and the measurement is in scope. The
first pass measured `h2 … code=200` on `/__mcp/health` and `/sse` for the identical vector. **Two
independent verifier measurements straddle the fix: 200 → 403.** That is stronger evidence than any
rollback of the new test would be.

Three non-obvious things this probe settles, all of which the orchestrator flagged as "verify, don't
trust":

1. **The HTTP/2-only gate on the fallback is present AND load-bearing.** `McpAccessControlPlugin.kt:208`
   reads `if (call.request.local.version != HTTP_2_VERSION) null else …`. Row `PROBE-H1 noHost -> 403`
   proves the gate works: 20-08's measured bytecode fact 5 says an HTTP/1.1 request with no `Host`
   coalesces to `serverHost = localhost` + the bound port, which **is** loopback and **does** match
   `settings.port` — so an ungated fallback would have returned **200** here. It returned 403.
2. **20-07 and 20-08 compose, and neither is redundant.** 20-07 alone (absent authority → deny) would
   have produced the foreign-authority 403 by itself — but it would also have denied *every* h2 request,
   because `facts.host` would still be null for legitimate clients. The `authority=loopback … code=200`
   and `code=400` rows are the proof that 20-08's fallback is live and resolves the *real*
   client-supplied authority: without it those rows would be 403. So the foreign-403 proves the limb
   fires and the loopback-200 proves the fallback is wired. Both halves were needed.
3. **`facts.host` is no longer hollow over h2.** The same pair of rows shows the value varies with the
   client authority (deny for `evil.example`, allow for `127.0.0.1`), so the audit payload's `host` field
   now carries real data on the h2 path. The first pass's ⚠️ HOLLOW data-flow row is resolved.

Code confirms the mechanism: `McpAccessControlDecision.kt:166` is now the unconditional
`!isLoopbackAuthority(facts.host.orEmpty(), settings.port) -> Deny(403, HOST_MISMATCH)`, sitting after
the `Origin` and browser-UA branches and before the `Referer` branch — the precedence the first pass
recorded is unchanged.

## Gap 2 — the seam, checked in both directions and for tautology

`build.gradle.kts:70` resolves `val storeBuild` once; line 106 feeds it into the code generator
(`STORE_BUILD = ${storeBuildFlag.get()}` at :95) and line 159 feeds the *same resolved Boolean* into the
test JVM as `systemProperty("storeBuild.expected", …)`. `McpBuildFlagsVersionTest:50-56` reads the system
property and asserts the **generated constant** equals it.

| Invocation (run by this verifier) | Generated `STORE_BUILD` (read from `build/generated/…/BuildFlags.kt`) | Class result | Exit |
|---|---|---|---|
| `./gradlew test --tests '*McpBuildFlagsVersionTest*' -PstoreBuild=true` | `true` | `tests="2" failures="0" errors="0"` | 0 |
| `./gradlew test --tests '*McpBuildFlagsVersionTest*'` (no flag) | `false` | `tests="2" failures="0" errors="0"` | 0 |

**Not a tautology, and not defanged — two independent arguments.**

- *It is a real seam, not a self-comparison.* The two sides reach the test JVM by different mechanisms:
  one through Kotlin code generation, compilation and classloading; the other through a JVM system
  property. Break either and the assertion fails. Delete line 159 and the test fails under the flag
  (expected `false`, generated `true`). Break the codegen wiring and it fails the same way. A **stale**
  generated file — codegen not re-running on a flag flip — also fails, which is precisely the class of
  regression worth guarding.
- *It genuinely fired in the positive direction.* The `-PstoreBuild=true` run has generated
  `STORE_BUILD = true` **and** passes. `assertEquals(expected, true)` passing forces `expected == true`,
  so that run compared `true == true` — it was not a vacuous `false == false`. Combined with the no-flag
  run, both sides of the seam were observed taking both values in lockstep. The old defanged shape
  (`assertFalse(BuildFlags.STORE_BUILD)`) is gone: `assertFalse` no longer appears in the file.

The BApp Store artifact path is testable again, which matters for live submission #231.

---

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| SC1 | `externalEnabled = true`: `POST /message` and `GET /sse` with no `Authorization` return 401 | ✓ VERIFIED (no regression) | `evaluateExternal` denies before routing. `McpAccessControlExternalPipelineTest` re-run by this verifier: `tests="8" failures="0" errors="0"`. `git diff b9ee87a..HEAD` on that file removes **only four KDoc lines** (a stale `build.gradle.kts` line citation) — zero assertions removed, so the first pass's SC1 evidence is intact. |
| SC2 | `externalEnabled = false`: foreign `Host`, foreign `Referer`, or browser UA with no `Origin` returns 403 on `/__mcp/health`, `/message` and `/sse` | ✓ **VERIFIED — gap closed** | **HTTP/2 (the half that failed): verifier probe measured 403/403/403 at `protocol=h2`, previously 200/400/200.** HTTP/1.1: `McpAccessControlPipelineTest` 9/0 and **byte-identical** across the gap wave (`git diff b9ee87a..HEAD` on it produces no output at all), plus my own raw-socket rows (foreign `Host` → 403, loopback → 200). The `Referer` and browser-UA limbs over h2 are now pinned by `McpLocalTlsAuthorityPipelineTest` (7/0) rather than being covered by accident. |
| SC3 | Four security headers on routes Ktor RESOLVES — not only 404s — deterministically over HTTP/1.1 and HTTP/2 | ✓ VERIFIED (warning cleared) | Unchanged mechanism (plugin `Allow` branch, pre-routing `Plugins` phase). The first pass's coverage-by-accident warning is closed: `McpAccessControlExternalPipelineTest:159-169` now asserts `assertEquals(Protocol.HTTP_2, response.protocol)` **before** the 400 and the four header assertions (all four still asserted, :28-31). 20-10's liveness claim sanity-checks out: my own probe independently observed `protocol=h2` on a TLS connector, so `assertEquals(Protocol.HTTP_1_1, …)` must fail — the flip 20-10 reports is arithmetically forced, not a claim I have to take on trust. |
| SC4 | The SC1–SC3 regression tests FAIL against the pre-fix `KtorMcpServerManager` and pass after | ✓ VERIFIED (no regression; discipline preserved) | The rollback evidence set is untouched: `McpAccessControlPipelineTest` has **zero** diff lines across the whole gap wave, and `McpAccessControlExternalPipelineTest` lost only KDoc. So the 12 recorded assertion failures remain the same 12 assertions. The four gap plans preserve the same discipline — `git log` shows a `test(…)` commit strictly before its `fix(…)` commit for every one: `6feaca7`→`246787b`, `5cd2981`→`aff89b9`, `651172b`→`bb51c02`, `e7de6d6`→`25fa69d`, `9e2d786`→`67264d0`. For the h2 test specifically I did better than re-running its RED: I measured the product at 200 (first pass) and at 403 (this pass) with my own probes on both sides of the fix. Assertion-#8 disclosure still judged acceptable, on the same reasoning as the first pass. |
| SC5 | Real project version advertised; loopback check accepts `[::1]:<port>`; blank token cannot authenticate | ✓ VERIFIED (no regression, plus WR-01 closed) | Generated `VERSION = "0.9.2"` observed in `build/generated/…/BuildFlags.kt`, matching `build.gradle.kts:15`, consumed at `KtorMcpServerManager.kt:110`. 20-07 rewrote `parseAuthority`, so I re-checked the IPv6 acceptances survive: `McpAccessControlDecisionTest` still asserts `isLoopbackAuthority("[::1]:$MCP_PORT", MCP_PORT)`, `("[::1]", null)`, `("[0:0:0:0:0:0:0:1]:$MCP_PORT", …)`, `("::1", null)` true and `("::1:$MCP_PORT")`, `("[::1")` false — 40/0. Blank-token guard untouched; `KtorMcpServerManagerSecurityTest` 7/0. **WR-01 is additionally fixed:** `MAX_TCP_PORT = 65_535` at :32, `parseAuthority` :203-207 now rejects an out-of-range port as a malformed authority, pinned by `isLoopbackAuthority_rejectsAPortThatIsNumericButOutOfRange` — including the non-vacuity contrast that a genuinely portless authority still passes. |
| SC6 | `/__mcp/shutdown` still 401 without a token and 200 with one | ✓ VERIFIED (no regression) | `McpServerIntegrationTest` re-run by this verifier: `tests="1" failures="0" errors="0"`. No gap plan touched `KtorMcpServerManager.kt` (absent from `git diff --name-only b9ee87a..HEAD`). |

**Score:** 6/6 truths verified. **Both prior gaps closed. No regressions found.**

### Deferred Items

None. Nothing was deferred and nothing needed to be.

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `.../mcp/McpAccessControlPlugin.kt` | Pre-routing gate; transport-aware authority resolution | ✓ **VERIFIED (first-pass defect fixed)** | 228 lines (was 133). `requestFacts:190` = `headers["Host"] ?: http2Authority(call)`. `http2Authority:207-216` gated on `local.version != "HTTP/2"`, reads `local` (never `origin` — no `X-Forwarded-Host` exposure), and wraps the port read in `catch (_: RuntimeException) -> UNRESOLVABLE_AUTHORITY`. Gate proven live by measurement (see gap 1, point 1). |
| `.../mcp/McpAccessControlDecision.kt` | Pure decision core, fail-closed | ✓ **VERIFIED (fail-open closed)** | 256 lines (was 223). Authority branch now unconditional at :166. `MAX_TCP_PORT` top-level const at :32; three-outcome `parseAuthority` at :193-209. Still engine-free. |
| `.../mcp/McpBlockedRequestReporter.kt` | D-06/D-07/D-09/D-10 observability, bounded | ✓ **VERIFIED (CR-01 closed)** | `floodCapable` predicate at :106; `auditWindows` map at :87 held **separately** from D-09's `windows`, so neither sink can steal the other's `getAndSet(0L)` count; `consumeAuditWindow:129-142`. 22/0 unit tests. |
| `.../mcp/KtorMcpServerManager.kt` | Gate install order, reporter wiring, `BuildFlags.VERSION` | ✓ VERIFIED (untouched by the gap wave) | Not in the gap-wave diff. `reporter.report(deny, …, System.currentTimeMillis())` at :192 — real wall clock, so the ADR-13 window is a real 60s in production, not a test-only construct. |
| `build.gradle.kts` | `VERSION` seam **and** the new `storeBuild.expected` seam | ✓ **VERIFIED (gap 2 fix)** | +6 lines. `systemProperty("storeBuild.expected", storeBuild.toString())` at :159, reusing the already-resolved `val storeBuild` (:70) rather than calling `findProperty` from inside a task action — configuration-cache-safe. |
| `src/test/.../McpLocalTlsAuthorityPipelineTest.kt` | The missing local-mode TLS/h2 gate test | ✓ **VERIFIED — new, 286 lines** | 7 tests, 0 failures. Asserts `Protocol.HTTP_2` **before** the status on every row, so an ALPN downgrade reports as a protocol failure instead of silently re-testing HTTP/1.1. Three separate `@Test` methods rather than a loop, with an in-file comment explaining that a loop would emit one `<failure>` and defeat the red-count gate. Named to avoid all five `excludeHeavyTests` globs — I confirmed the glob list in `tasks.test` (`*IntegrationTest`, `*ConcurrencyTest`, `*BackpressureTest`, `*RestartPolicyTest`, `*SupervisionTest`) and this class matches none, so it runs in the fast PR gate. |
| `src/test/.../McpBuildFlagsVersionTest.kt` | Assert the seam, not a value | ✓ **VERIFIED (gap 2 fix)** | `assertFalse` gone; asserts generated constant == system property. Both directions exercised (table above). |
| `DECISIONS.md` | ADR-13 recording the D-06 amendment | ✓ VERIFIED | ADR-13 at :140-151, in the file's `**Context.** / **Decision.** / **Consequences.**` shape, additions only. `20-CONTEXT.md:73` carries an `AMENDED by ADR-13` pointer while D-06's original bullet (:67) stays readable. |
| `docs/mcp-hardening.md` | D-01/D-02/D-12 corrections | ✓ **VERIFIED (WR-02 closed)** | Verification item 2 now splits the recording claim: bullet :39 scopes the guarantee to the external 401s and the three gate-visible local vectors; bullet :40 states that a foreign `Origin` is **not recorded**, names Ktor's CORS plugin committing before the gate, and tells the operator how to distinguish expected silence from a broken audit trail. §External Access items 4-6 and Verification items 1/3/4 unchanged. |

### Key Link Verification

| From | To | Via | Status | Details |
|------|-----|-----|--------|---------|
| `McpAccessControlPlugin.requestFacts` | HTTP/2 request authority | `?: http2Authority(call)` on `local`, gated to `version == "HTTP/2"` | ✓ **WIRED — was NOT WIRED** | Behavioural proof, not structural: foreign h2 authority → 403 **and** loopback h2 authority → 200. The second row is what rules out "denies everything over h2". |
| `evaluateLocal` | authority limb | unconditional `!isLoopbackAuthority(facts.host.orEmpty(), port)` | ✓ WIRED | Fires on absent authority too — `PROBE-H1 noHost -> 403`. |
| `KtorMcpServerManager` | `McpAccessControl` | `install` after CORS, before `routing` | ✓ WIRED (unchanged) | Not touched by the gap wave. |
| `McpBlockedRequestReporter` | audit sink | `consumeAuditWindow` before `emitTransportTelemetry` for the two pre-auth reasons | ✓ WIRED | Bounded at one record per reason per 60 s; per-occurrence retained for the four local reasons. |
| `build.gradle.kts:159` | `McpBuildFlagsVersionTest:50` | `storeBuild.expected` system property | ✓ WIRED | Present at both ends; observed carrying `true` and `false`. |

### Data-Flow Trace (Level 4)

| Artifact | Data Variable | Source | Produces Real Data | Status |
|----------|---------------|--------|--------------------|--------|
| `McpAccessControlPlugin` | `facts.host` | `headers["Host"]` **or** the h2 connection point | HTTP/1.1 yes; **HTTP/2 yes now** — value provably varies with the client authority | ✓ **FLOWING (was DISCONNECTED over h2)** |
| `McpBlockedRequestReporter` | audit `host` field | `deny.facts.host` | Inherits the above | ✓ **FLOWING (was HOLLOW over h2)** |
| `McpBuildFlagsVersionTest` | `expected` | `System.getProperty("storeBuild.expected")` ← `build.gradle.kts:159` | Yes — observed `true` and `false` | ✓ FLOWING |
| `KtorMcpServerManager` | `Implementation` version | generated `BuildFlags.VERSION` | Yes — `"0.9.2"` | ✓ FLOWING |
| `McpAccessControlPlugin` | `facts.origin` / `referer` / `userAgent` / `authorization` | ordinary headers (survive the h2 pseudo-header filter) | Yes | ✓ FLOWING |

### Behavioural Spot-Checks

| Behaviour | Command | Result | Status |
|-----------|---------|--------|--------|
| Foreign authority denied in local mode over h2 | transient probe, 3 paths | `403 / 403 / 403` at `protocol=h2` | ✓ **PASS (was FAIL)** |
| Legitimate h2 client not broken | transient probe, loopback authority | `200 / 400 / 200` at `protocol=h2` | ✓ PASS |
| `Host`-less HTTP/1.1 still denied (fallback correctly h2-only) | transient probe, raw socket, no `Host` | `HTTP/1.1 403 Forbidden` | ✓ PASS |
| Foreign / loopback `Host` over HTTP/1.1 | transient probe, raw socket | `403` / `200` | ✓ PASS |
| Store-build path, flag on | `./gradlew test --tests '*McpBuildFlagsVersionTest*' -PstoreBuild=true` | exit 0; generated `STORE_BUILD = true`; 2/0 | ✓ **PASS (was FAIL)** |
| Store-build path, flag off | same without the flag | exit 0; generated `STORE_BUILD = false`; 2/0 | ✓ PASS |
| Phase security suites on the merged tree | one Gradle run, 9 classes, XML read | Pipeline 9/0, ExternalPipeline 8/0, **LocalTlsAuthority 7/0**, Decision 40/0, Security 7/0, Reporter 22/0, ServerIntegration 1/0, SupervisorProbe 4/0, BuildFlagsVersion 2/0 — all `failures="0" errors="0"` | ✓ PASS |
| Debt-marker gate on every gap-wave file | `grep -nE "TBD\|FIXME\|XXX\|TODO\|HACK\|PLACEHOLDER"` over `git diff --name-only b9ee87a..HEAD` | no matches | ✓ PASS |
| QUAL-07 baseline unchanged | `git diff --stat detekt-baseline.xml` | empty | ✓ PASS |
| Tree clean after the probe | `git status --porcelain` + `git diff --stat HEAD` | both empty | ✓ PASS |

### Probe Execution

| Probe | Command | Result | Status |
|-------|---------|--------|--------|
| — | `find scripts -path '*/tests/probe-*.sh'` | no matches; no PLAN/SUMMARY declares a `probe-*.sh` | N/A — SKIPPED (no shell-probe convention in this repo; SC4's red-before-green gate plays that role, and this verifier's own transient JVM probe supplies the empirical layer) |

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|-------------|-------------|--------|----------|
| **SEC-04** | 20-01…20-10 (20-08, 20-09 declare it alone) | Every MCP request subject to access control before any handler; external 401 on `/message` + SSE; local 403 on foreign `Origin` / foreign `Host` / browser UA without `Origin`; four security headers on matched routes; regression tests that fail pre-fix | ✓ **SATISFIED — tick it** | Every limb now holds on every reachable transport. The structural bypass was already closed in the first wave (checks moved to the pre-routing `Plugins` phase, dead `Call`-phase interceptor deleted, denial short-circuits the handler, reporter wired end-to-end). The one open limb — foreign `Host` → 403 — now fires over HTTP/2 as well as HTTP/1.1, **measured** at 403 on `/__mcp/health`, `/message` and `/sse`, with a legitimate loopback h2 client still served. The regression tests fail pre-fix (unchanged 12-assertion rollback set, plus 20-08's 3-failure h2 RED, plus my own 200→403 measurement across the fix). |
| **SEC-05** | 20-01…20-04, 20-06, 20-07, 20-10 | Real build version instead of `0.6.0`; bracketed-IPv6 authorities handled consistently with `::1` accepted; blank bearer token cannot authenticate | ✓ **SATISFIED** | Unchanged from the first pass and now stronger: `BuildFlags.VERSION = "0.9.2"` wired into `Implementation`; one shared `isLoopbackAuthority` accepting `[::1]:<port>`, `[0:0:0:0:0:0:0:1]:<port>` and bare `::1`; blank-token guard fails closed at unit and integration level. The first pass's WR-01 caveat against "handles authorities consistently" (an `Int`-overflowing port silently disabling the port comparison) is now **fixed and pinned**, so the requirement's text holds without reservation. |

**Explicit traceability verdict.** No single plan marks either ID, so this is the phase-level call:
**tick BOTH SEC-04 and SEC-05.** SEC-04's foreign-`Host` limb — the one thing that blocked it on
2026-08-08 — is closed and independently re-measured. SEC-05 was already satisfied and its residual
parser defect is closed too.

**Orphaned requirements:** none. `.planning/REQUIREMENTS.md:45-46` maps only SEC-04 and SEC-05 to Phase
20; both are claimed by the plans and both are assessed above.

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| — | — | `TBD` / `FIXME` / `XXX` / `TODO` / `HACK` / `PLACEHOLDER` across all 12 gap-wave files | — | **None found.** Debt-marker gate passes. |
| `McpAccessControlDecision.kt` | 150 → 166 | Guard-and-skip on null attacker-controlled input | ✓ **RESOLVED** | Was the first pass's 🛑 blocker. Branch is unconditional and carries a do-NOT-restore comment naming the maintainer decision. |
| `McpAccessControlDecision.kt` | 174 → 203-207 | `toIntOrNull` conflating "absent" with "unparseable" | ✓ **RESOLVED** | WR-01 closed; three outcomes kept distinct. |
| `McpBlockedRequestReporter.kt` | 53 | Accepted-residual rationale false in the mode that matters | ✓ **RESOLVED** | CR-01 closed; `loopback-only` string gone, ADR-13 named. |
| `McpBuildFlagsVersionTest.kt` | 39-43 | Test asserting a build-flag *value* rather than the *seam* | ✓ **RESOLVED** | Gap 2 closed; `assertFalse` removed. |
| `McpAccessControlPlugin.kt` | 213 | `catch (_: RuntimeException)` on a path that could not be exercised at runtime | ℹ️ **Info — assessed, not a gap** | See below. |

### Residual risks — assessed, and why none is a gap

**The `Integer.parseInt` guard rests on disassembly alone — acceptable.** 20-08 was honest that it could
not exercise the throwing port getter, because OkHttp's URL parser rejects a malformed port before a
request is built. I assessed the guard on its failure modes rather than its coverage, and it is safe
either way: if `Http2LocalConnectionPoint.getServerPort()` never throws, the `catch` is unreachable and
harmless; if it does throw on a raw h2 client's malformed `:authority`, the `catch` converts what would
be a 500 on a Netty event-loop thread into a **denial** — because the sentinel resolves to
`<unresolved-authority>`, and I verified independently that `parseAuthority`'s host character classes
(`[0-9a-z.\-]+` and `\[[0-9a-f:.]+\]`) admit neither `<` nor `>`, so `matchEntire` fails, the parse
returns null, and the gate denies. There is no configuration in which the guard weakens the gate, and no
availability risk for normal clients (20-08's measured rows (a)/(b) show the getter returning the bound
port cleanly). The disclosure is honest and the residual is benign — **Info, not a gap.**

**Known accepted residuals, documented in the `requestFacts` KDoc, not re-litigated here:** (1) an absent
`:authority` over h2 is unclosable because `Http2LocalConnectionPoint` coalesces it into the local socket
and makes it byte-identical to a legitimate authority; (2) 20-07's absent-authority DENY is therefore
scoped to HTTP/1.1; (3) a portless `:authority` acquires the scheme default port, so h2 denies
`:authority: localhost` where h1 allows `Host: localhost`. All three are fail-closed or unclosable, all
three are stated in the source. **Residual 3 is the one with a user-visible cost**, so it is now folded
into human-verification item 1 rather than left as a bare note: it is the only path by which this
hardening could lock out a legitimate MCP client, and only a live client can settle it.

**CR-01, verified bounded rather than accepted on narrative.** With audit logging and external access on,
every 401 previously drove one synchronous `audit.jsonl` append from an unauthenticated peer. Now
`report()` routes `UNAUTHORIZED` and `BLANK_TOKEN` through `consumeAuditWindow`, which emits only when
the 60 s window has elapsed and the CAS wins, and otherwise increments a counter and returns null. The
ceiling is therefore **two audit records per minute** (one per reason) regardless of request rate, with
`suppressed` preserving burst visibility. `nowMs` comes from `System.currentTimeMillis()` at the real
call site (`KtorMcpServerManager.kt:192`), so this is a real 60 s in production. `AuditLogger.kt` is
**not** in the gap-wave diff — the too-broad change was genuinely avoided. The one locked decision
touched is D-06, and that amendment is authorised by ADR-13 with the original bullet left readable; no
other locked decision moved.

**ADR-13 × the runbook, one small imprecision (Info).** `docs/mcp-hardening.md:39` still promises that
the external-mode 401 reason "is recorded … in the audit log". That remains true for the checklist as
written, because the first occurrence in a window always emits (`prev == 0L` → window elapsed →
`suppressed = 0`). An operator who repeats the 401 test twice inside 60 s will see only one record. Not
worth a gap; worth a sentence if that section is edited again.

**Process note, not a defect.** `20-07-SUMMARY.md` is missing its `Self-Check` section (template
omission). The orchestrator independently verified its substantive claims and I did not re-derive them; I
did independently confirm the two behavioural outcomes that matter (unconditional authority branch,
out-of-range port rejected). 20-08, 20-09 and 20-10 all carry `Self-Check: PASSED`.

### Human Verification Required

Two items, both genuinely unautomatable, both from `20-VALIDATION.md` §Manual-Only. The first pass's
third item (doc accuracy) is now **closed** — I verified the WR-02 correction in
`docs/mcp-hardening.md:38-40` myself, so no judgement call is left there.

**1. A real MCP client over local SSE — now in BOTH transport configurations.**
**Test:** Enable MCP locally with TLS **off**, connect Claude Desktop or Codex CLI, list tools, call one
read-only tool. Then repeat with the "Enable TLS" checkbox **on** (this negotiates HTTP/2).
**Expected:** Both configurations connect and serve the tool call.
**Why human:** Requires a live third-party client and a running Burp. The h2 run is the new risk surface:
the gate now denies any local-mode request whose resolved authority is not the bound loopback socket, and
over h2 a **portless** `:authority` resolves to the scheme default port (443) and is denied where
HTTP/1.1 would allow it. Which authority shape a given client emits cannot be determined from this repo.
This is the only plausible way the phase could have broken a legitimate user, and it is worth ten minutes.

**2. A real MCP client in external mode with a bearer token over TLS.**
**Expected:** Connects and authenticates; no 401 for a correctly configured client.
**Why human:** Same — live third-party client plus TLS trust configuration.

Status is `human_needed` rather than `passed` solely because these two remain; all 6 automated must-haves
are verified and there are no gaps.

### Gaps Summary

**No gaps. Both prior gaps are closed, and closed on evidence produced by this verifier rather than
inherited from the executors' test runs.**

Gap 1 was the phase's own signature defect turned back on itself — a check that existed but did not run
on a reachable path. It is now closed on both sides, and the two fixes genuinely compose rather than
overlapping: 20-07 makes an absent authority deny (so the limb can no longer be skipped), and 20-08
supplies the real client authority over HTTP/2 (so legitimate h2 clients are still served). My probe
proves both halves are load-bearing — the foreign-authority 403 shows the limb fires, and the
loopback-authority 200 shows the fallback resolves a real value instead of denying everything. The
HTTP/2-only gate on that fallback is not merely present in the source; it is proven necessary by
measurement, because a `Host`-less HTTP/1.1 request would have flipped from denied to **allowed** without
it, and it measured 403.

Gap 2 is closed with a real seam rather than a weakened assertion. The generated flag and the Gradle
property reach the test JVM by different mechanisms, the assertion was observed comparing `true == true`
under the flag and `false == false` without it, and the old `assertFalse` is gone. The BApp Store
artifact path can validate itself again.

Along the way three code-review findings the first pass had left open are closed and independently
confirmed: WR-01 (out-of-range port fail-open in the shared parser), WR-02 (runbook promising an audit
record the code provably never writes for a foreign `Origin`), and CR-01 (unbounded unauthenticated audit
append, now ceilinged at two records per minute with the false `loopback-only` rationale replaced by
ADR-13). The SC3 coverage-by-accident warning is closed too — the HTTP/2 half is now an asserted contract.

Nothing regressed: the SC1–SC3 pipeline tests that constitute SC4's rollback evidence are byte-identical
(or comment-only) across the gap wave, all nine phase security suites are green on the merged tree, no
debt marker was introduced in any of the twelve changed files, and `detekt-baseline.xml` is untouched.

**SEC-04 and SEC-05 should both be ticked.**

---

_Verified: 2026-08-10T10:15:00Z (re-verification after gap closure; first pass 2026-08-08T13:07:44Z, 5/6 gaps_found)_
_Verifier: Claude (gsd-verifier)_
