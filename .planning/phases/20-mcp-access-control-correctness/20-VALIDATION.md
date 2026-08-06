---
phase: 20
slug: mcp-access-control-correctness
status: approved
nyquist_compliant: true
wave_0_complete: false
created: 2026-08-06
---

# Phase 20 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.
> Derived from `20-RESEARCH.md` §"Validation Architecture" (measured, not inferred).

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit Jupiter 6.0.3 + `kotlin("test")`; mockito-kotlin 5.4.0; OkHttp + MockWebServer 5.4.0 |
| **Config file** | none — `useJUnitPlatform()` at `build.gradle.kts:142-158`, `jvmArgs("-ea")` |
| **Quick run command** | `JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test --tests 'com.six2dez.burp.aiagent.mcp.*'` |
| **Full suite command** | `JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test detekt ktlintCheck` |
| **Estimated runtime** | ~20 s scoped / ~60 s full (measured: 50 s for `test detekt ktlintCheck` on 2026-08-05) |

**Client constraint (measured, load-bearing):** tests MUST use OkHttp, not `HttpURLConnection`. The JDK
silently drops the `Origin` header from `HttpURLConnection` (restricted-header list) and always writes its
own `Host`, which makes SC2's assertions unwritable with it. OkHttp 5.4.0 is already on the test classpath —
no new dependency. Never call `.body.string()` on a `text/event-stream` 200; it blocks until read timeout.

---

## Sampling Rate

- **After every task commit:** `JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test --tests 'com.six2dez.burp.aiagent.mcp.*'`
  (scoped `test` only — bundling `detekt ktlintCheck` per task costs ~50 s measured and breaks the latency
  target below; the full gate belongs at wave boundaries)
- **After every plan wave:** `JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test`
- **Before `/gsd-verify-work`:** `test detekt ktlintCheck` green
- **Max feedback latency:** ~20 seconds

---

## Per-Task Verification Map

| SC / Item | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|-----------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| SC1 | SEC-04 | T-20-01 | external: `POST /message` and `GET /sse` without `Authorization` → 401 | integration (TLS, Netty) | `--tests '*McpAccessControlExternalPipelineTest'` | ❌ W0 | ⬜ pending |
| SC2 | SEC-04 | T-20-02 | local: foreign `Host` → 403, foreign `Referer` → 403, browser UA without `Origin` → 403, on `/__mcp/health`, `/message`, `/sse` | integration (cleartext, Netty, OkHttp) | `--tests '*McpAccessControlPipelineTest'` | ❌ W0 | ⬜ pending |
| SC3 | SEC-04 | T-20-03 | all four security headers on a matched-route 200 and on the SSE 200, deterministic over HTTP/1.1 and HTTP/2 | integration | same as SC2 | ❌ W0 | ⬜ pending |
| SC4 | SEC-04 | T-20-16, T-20-20, T-20-21, T-20-22 | the SC1–SC3 assertions fail against pre-fix `KtorMcpServerManager` | process gate | `20-06` Task 1 — `cp` aside + `git show <baseline>:<path>` rollback, restore by `cp`. **No `git stash`.** | ❌ W0 | ⬜ pending |
| SC5a | SEC-05 | T-20-17 | server advertises the real build version, not `0.6.0` | unit | `--tests '*BuildFlags*'` | ❌ W0 | ⬜ pending |
| SC5b | SEC-05 (D-11) | T-20-13 | `isValidHost("[::1]:9876", 9876)` → true; `isValidOrigin`/`isValidReferer` accept `http://[::1]:9876` | unit (reflection, existing style) | `--tests '*KtorMcpServerManagerSecurityTest'` | ✅ extend | ⬜ pending |
| SC5c | SEC-05 | T-20-04 | blank token cannot authenticate: `isAuthorized("Bearer ", "")` → false, and external request with blank token → 401 | unit + integration | as above + external suite | ✅ extend / ❌ W0 | ⬜ pending |
| SC6 | SEC-04 | T-20-15 | `/__mcp/shutdown` → 401 without token, 200 with token (must not regress) | integration | `--tests '*McpServerIntegrationTest'` | ✅ exists | ⬜ pending |
| D-02 | SEC-04 | T-20-05 | external `/__mcp/health` carries no `X-Burp-AI-Agent`; local does | integration | external + local suites | ❌ W0 | ⬜ pending |
| D-02 | SEC-04 | T-20-05 | `McpSupervisor` local-mode probe still succeeds after the header change | integration / targeted unit | new test against real `probeExistingServer` | ❌ W0 | ⬜ pending |
| D-06..D-09 | SEC-04 | T-20-06, T-20-07, T-20-11, T-20-12 | blocked-request Output line + audit event fire once then aggregate; header values sanitized (D-07) and hashed by default (D-10) | unit (pure) | `--tests '*BlockedRequestReporterTest'` | ❌ W0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

**Threat Ref coverage (W-04):** `T-20-01`…`T-20-05` are phase-level and mapped above. `T-20-06`…`T-20-22`
are declared in the individual PLAN.md `<threat_model>` blocks; those carrying a `mitigate` disposition
(T-20-06 sanitization, T-20-07 rate limiting, T-20-11 hashing, T-20-14 keystore path, T-20-16 SC4
evidence, T-20-18 takeover-retry restoration, T-20-19 runbook accuracy, T-20-20…T-20-22 SC4 process
integrity) are checkable through the rows above plus each plan's own `<verification>` block. A threat with
an `accept` disposition needs no validation row by design — the acceptance and its residual are recorded
in the plan's threat table.

---

## Wave 0 Requirements

- [ ] `src/test/kotlin/.../mcp/McpTestServerSupport.kt` — shared helpers: free port, start-and-await-`Running`,
      trust-all OkHttp client, `McpSettings` builders, temp-dir keystore path. Without it the same ~40 lines get
      copied into three test classes.
- [ ] `src/test/kotlin/.../mcp/McpAccessControlPipelineTest.kt` — local mode (SC2, SC3, D-02 local)
- [ ] `src/test/kotlin/.../mcp/McpAccessControlExternalPipelineTest.kt` — external mode + TLS (SC1, SC5c, D-02 external)
- [ ] `src/test/kotlin/.../mcp/McpAccessControlDecisionTest.kt` — pure-function unit tests for the gate decision
- [ ] `src/test/kotlin/.../mcp/BlockedRequestReporterTest.kt` — D-07 sanitization, D-09 aggregation, D-10 hashing
- [ ] Extend `KtorMcpServerManagerSecurityTest.kt` — IPv6 host/origin/referer, blank token
- [ ] No framework install needed.

**Naming constraint (must be honoured, not optional):** `build.gradle.kts:145-157` excludes `*IntegrationTest`
when `-PexcludeHeavyTests=true` is passed. If the SC4 gate classes are named `…IntegrationTest` they will be
**silently skipped** in any fast PR gate. The names above deliberately end in `…PipelineTest` / `…Test`. Do not
rename them into the excluded pattern.

**TLS constraint:** external-mode tests must generate their keystore into a `Files.createTempDirectory(...)`
path. Never point a test at `~/.burp-ai-agent/certs` — that is the user's real keystore.

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| `docs/mcp-hardening.md` reflects D-01/D-02 (health deliberately exempt, agent header local-only) | D-12 / T-20-19 | Doc accuracy is a judgement call, not an assertion | Read §"External Access" item 4 and §"Verification" item 2; confirm neither still claims every request is bearer-validated |
| A real MCP client (Claude Desktop / Codex CLI) still connects over local SSE after the gate lands | SEC-04 | Requires a live third-party client and a running Burp | Enable MCP locally, connect the client, list tools, call one read-only tool |
| A real MCP client still connects in external mode with a token | SEC-04 | Same | Enable external + TLS, configure the client with the bearer token, connect |

---

## Validation Sign-Off

- [ ] All tasks have an automated verify command or a declared Wave 0 dependency
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] SC4 red-before-green experiment executed per `20-06` (cp + `git show`, never `git stash`) and its transcript recorded in `20-06-SUMMARY.md`
- [ ] Test class names confirmed outside the `*IntegrationTest` exclusion pattern
