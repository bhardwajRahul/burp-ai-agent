---
phase: 20-mcp-access-control-correctness
verified: 2026-08-10T10:15:00Z
status: human_needed
score: 6/6 must-haves verified
overrides_applied: 0
re_verification:
  previous_status: gaps_found
  previous_score: 5/6
  previous_verified: 2026-08-08T13:07:44Z
  tree_verified: cf3fa53e52362d4f54ede262b328e1f07024a3dd
  gaps_closed:
    - "SC2 — foreign Host/authority returns 403 on /__mcp/health, /message AND /sse over HTTP/2 (was 200/400/200). Re-measured by this verifier with its own transient probe: 403/403/403 at protocol=h2."
    - "`./gradlew test -PstoreBuild=true` runs the suite to a clean exit again. The store-build test now asserts the generated-flag-vs-Gradle-property seam, and this verifier observed the seam take BOTH values (STORE_BUILD=true under the flag, false without) with the class green in each direction."
  gaps_remaining: []
  regressions: []
  requirement_verdict_change:
    SEC-04: "was SUBSTANTIALLY SATISFIED — one limb BLOCKED; now SATISFIED — tick it"
    SEC-05: "was SATISFIED; still SATISFIED (WR-01, its residual parser defect, is additionally closed)"
human_verification:
  - test: "Connect a real MCP client (Claude Desktop / Codex CLI) over LOCAL SSE with the gate active — once with TLS off (HTTP/1.1) and once with the 'Enable TLS' checkbox ON (HTTP/2). List tools and call one read-only tool in each configuration."
    expected: "Client connects and the tool call succeeds in BOTH configurations. The TLS/h2 run is the new one and the one that matters: the gate now DENIES any local-mode request whose resolved authority is not the bound loopback socket, and over h2 a PORTLESS `:authority` acquires the scheme default port (443) and is therefore denied where HTTP/1.1 would allow it (documented residual 3). A client that sends a portless `:authority` over h2 would be locked out."
    why_human: "Requires a live third-party MCP client and a running Burp instance. No automated seam exists, and the h2 authority-shape a given client emits cannot be predicted from this repo."
  - test: "Connect a real MCP client in EXTERNAL mode with the bearer token configured over TLS."
    expected: "Client connects over TLS and authenticates; no 401 for a correctly configured client."
    why_human: "Same — live third-party client plus TLS trust configuration."
---

# Phase 20: MCP Access-Control Correctness — Verification Report

**Phase Goal:** Every MCP request passes through the extension's access-control checks before any handler runs, in both local and external mode — and there are tests that would have caught the current bypass.
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
