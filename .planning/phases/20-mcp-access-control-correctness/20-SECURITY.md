---
phase: 20
slug: mcp-access-control-correctness
status: verified
# threats_open = count of OPEN threats at or above workflow.security_block_on severity (the blocking gate)
threats_open: 0
asvs_level: 1
block_on: high
register_authored_at_plan_time: true
severity_source: auditor-retroactive
created: 2026-08-28
---

# Phase 20 — Security

> Per-phase security contract: threat register, accepted risks, and audit trail.

**Input state:** B (no prior SECURITY.md; 10 PLAN.md and 10 SUMMARY.md present).
**Register origin:** authored at plan time — **all ten** plans carry a `<threat_model>` block.
**Verification:** `gsd-security-auditor`, ASVS L1 with several rows traced deeper to the enforcement
boundary. **49 of 49 threats CLOSED. `threats_open: 0` on the merits, not by default** — the
`block_on: high` threshold never had to be applied because nothing was open.

**Requirements:** SEC-04, SEC-05.

---

## READ FIRST — two things this register is honest about

**1. The plan-time register recorded NO severities.** Confirmed: `grep -i '| severity |'` across all
ten `20-*-PLAN.md` returns nothing. The table shape is
`| Threat ID | Category | Component | Disposition | Mitigation Plan |` (plan 06 uses
`| ID | Category | Surface | Disposition | Control |`). **Every severity in this file was assigned
retroactively by the auditor on 2026-08-28. None is plan-time data and none may be cited as such.**

This is a real defect in the register, not a formatting quirk: `security_block_on: high` thresholds
on severity, so had any threat been OPEN, `threats_open` would have been uncomputable. Severity is
filled only for `accept` rows, which become standing residuals where an ongoing rating is
load-bearing; verified-closed `mitigate` rows carry `n/a`.

**2. This is the FIRST security pass on phase 20, which makes one verification rule circular.** The
standard rule for an `accept` disposition is "an entry exists in the SECURITY.md accepted-risks log"
— unsatisfiable when no SECURITY.md exists yet. The auditor therefore verified each of the 19
`accept` rows against its **durable in-tree record** instead (code KDoc, `DECISIONS.md` ADR-13,
`docs/mcp-hardening.md`, `20-RESEARCH.md`), and found every one where the plan said it would be.
**This file's Accepted Risks Log is what creates the log those rows require.** Every accept row and
the one transfer are transcribed below for exactly that reason.

---

## Trust Boundaries

| Boundary | Description | Data Crossing |
|----------|-------------|---------------|
| Remote HTTP client → Ktor pipeline | Fully untrusted: `Origin`, `Host`, `Referer`, `User-Agent`, `Authorization`, `:authority` and path are all attacker-controlled | Request headers and path |
| Ktor `Plugins` phase → route handlers | The gate boundary. `install(McpAccessControl)` attaches at `ApplicationCallPipeline.ApplicationPhase.Plugins`, which precedes `RoutingRoot`'s `Call`-phase interceptor regardless of install order | Allow / Deny decision |
| Gate → `McpBlockedRequestReporter` | Denial facts reach the Output tab and `audit.jsonl` | Sanitized, hashed header values |
| Extension → existing port holder (bind conflict) | Takeover probe; identity not establishable | Liveness + HMAC proof of possession, never the bearer token |

---

## Threat Register — 29 mitigate · 19 accept · 1 transfer

Severity column: `n/a` = verified-closed mitigate row. Bracketed `[auditor]` values are retroactive.

| Threat ID | Category | Severity | Disposition | Evidence | Status |
|---|---|---|---|---|---|
| T-20-01 | Elevation of Privilege | n/a | mitigate | `McpAccessControlDecision.kt:145` health exempt, `:163-164` 401 otherwise; `McpAccessControlExternalPipelineTest` 401 on `/message` and `/sse`. **See drift 1** | closed |
| T-20-02 | Spoofing | n/a | mitigate | `evaluateLocal` `:172-202`; foreign `Host`/`Referer`/browser-UA-without-`Origin` rejected on every path | closed |
| T-20-03 | Tampering | n/a | mitigate | `McpAccessControlPlugin.kt:51-57,:102,:226-232` — four headers in the `Plugins` phase; asserted on matched-route 200, SSE 200, and authorized external 400 over h2 | closed |
| T-20-04 | Elevation of Privilege | n/a | mitigate | `:278-281` `token.isNotBlank() &&` precedes comparison; `KtorMcpServerManager.kt:316` delegates to the same function | closed |
| T-20-05 | Information Disclosure | n/a | mitigate | `KtorMcpServerManager.kt:202-204` — `X-Burp-AI-Agent` only when `!externalEnabled` | closed |
| T-20-06 | Tampering / Repudiation | n/a | mitigate | `McpBlockedRequestReporter.kt:230` → `sanitizeInline`; `[\p{Cntrl}-]`, cap 200. **See drift 2** | closed |
| T-20-06 (wiring) | Repudiation | n/a | mitigate | `lateinit` (no default no-op) at `:45`; `onBlocked(decision)` before `respond` at `:84`; one foreign-`Host` request → exactly one `mcp_transport_blocked` with `reason == "host_mismatch"` | closed |
| T-20-07 | Denial of Service | n/a | mitigate | `:188-203` lock-free read-then-CAS, 60 s per-reason window, aggregate count; 5 window tests | closed |
| T-20-08 | Information Disclosure | low [auditor] | accept | Bare status only — no body, no `WWW-Authenticate` anywhere in `src/main` | closed |
| T-20-09 | Information Disclosure | low [auditor] | accept | `docs/mcp-hardening.md:19` documents pre-auth `OPTIONS` and the `anyHost()` fallback | closed |
| T-20-10 | Information Disclosure | medium [auditor] | **transfer** | To Phase 25 / SEC-07 (`20-CONTEXT.md:61,224` D-05 → ROADMAP:336). Not-made-worse verified: probe sends no credential. **Recipient shipped** — `McpTakeoverProof` is HMAC-SHA256 | closed |
| T-20-11 | Information Disclosure | n/a | mitigate | `:171` `Hashing.sha256Hex` unless `verboseAudit`; absent headers stay null rather than hashing `""` | closed |
| T-20-12 | Denial of Service | medium [auditor] | accept | Stated in the audit-ON form at `:48-51`; superseded in practice for the two remote-reachable reasons by ADR-13 | closed |
| T-20-13 | Information Disclosure | low [auditor] | accept | `build.gradle.kts:72-108` interpolates `project.version` — not a secret, intended SEC-05 5a behaviour | closed |
| T-20-14 | Tampering | n/a | mitigate | `McpTestServerSupport.kt:86-104,:138-139` require a caller-supplied `Path`; explicit never-`~/.burp-ai-agent/certs` comment | closed |
| T-20-15 | Spoofing | low [auditor] | accept | `trustAllClient` exists only in the test source set; no `src/main` reference | closed |
| T-20-16 | Tampering (evidence) | n/a | mitigate | `20-06-SUMMARY.md:51,54` both exit codes verbatim; `:69-91` all 12 failed methods named | closed |
| T-20-17 | Spoofing | medium [auditor] | accept | `McpSupervisor.kt:288-293` emits the "proceeding on liveness alone" Output line | closed |
| T-20-18 | Denial of Service | n/a | mitigate | `:295` mode mirror → probe true → SHUTDOWN_REQUESTED → `handleBindFailure:212` `scheduleStart` | closed |
| T-20-19 | Repudiation | n/a | mitigate | `docs/mcp-hardening.md:16` names the health exemption; `:47` states the 401/403 outcomes | closed |
| T-20-20 | Repudiation | n/a | mitigate | Five by-design passes named and confirmed ABSENT from the failure list; assertion (8) and method (12) counts reported separately | closed |
| T-20-21 | Integrity (working tree) | n/a | mitigate | `git status --porcelain` empty; manager still at `d7c8feb`. Re-confirmed at audit time | closed |
| T-20-22 | Denial of correctness | n/a | mitigate | `grep -c 'e: file'` = 0; all 12 failures `AssertionFailedError`; compile-vs-assertion taxonomy recorded | closed |
| T-20-07-01 | Spoofing | n/a | mitigate | `:197` `!isLoopbackAuthority(facts.host.orEmpty(), …)` — no null-guard in front | closed |
| T-20-07-02 | Tampering | n/a | mitigate | `:234-238` out-of-range numeric port makes the whole authority malformed | closed |
| T-20-07-03 | Elevation of Privilege | n/a | mitigate | Same unconditional branch; `:188-196` comment forbids reintroducing a presence check | closed |
| T-20-07-04 | Denial of Service | low [auditor] | accept | Four real-server suites green (98 tests) **plus** UAT test 1 pass — a real client connects over both HTTP/1.1 and h2 | closed |
| T-20-08-01 | Spoofing | n/a | mitigate | `McpAccessControlPlugin.kt:190` `headers["Host"] ?: http2Authority(call)` | closed |
| T-20-08-02 | Elevation of Privilege | n/a | mitigate | `McpLocalTlsAuthorityPipelineTest` — three methods, each asserting `Protocol.HTTP_2` **before** the 403 | closed |
| T-20-08-03 | Denial of Service | n/a | mitigate | `:217-218` catch → `UNRESOLVABLE_AUTHORITY` sentinel containing `<`/`>` so the shared parser rejects it | closed |
| T-20-08-04 | Tampering | n/a | mitigate | `:212,216` read `request.local`, never `request.origin` (which would be `X-Forwarded-Host`-controlled) | closed |
| T-20-08-05 | Spoofing | **medium** [auditor] | accept | **Residual 1 — the ONLY fail-OPEN residual in the phase.** See Accepted Risks | closed |
| T-20-08-06 | Denial of Service | low [auditor] | accept | Residual 2, bracketed IPv6 authority; fail-closed | closed |
| T-20-08-07 | Repudiation | n/a | mitigate | The `:190` fix populates `facts.host`, so the h2 audit record stops being hollow | closed |
| T-20-08-08 | Denial of Service | low [auditor] | accept | Residual 3, portless h2 `:authority`; fail-closed. **UAT narrows, does not close** — see Accepted Risks | closed |
| T-20-09-01 | Denial of Service | n/a | mitigate | `:105-114` + `consumeAuditWindow:128-141`; separate `auditWindows` map so neither sink steals the other's count | closed |
| T-20-09-02 | Repudiation | n/a | mitigate | `suppressed` appended after the eight D-06 keys | closed |
| T-20-09-03 | Denial of Service | medium [auditor] | accept | `:64-66` — async hand-off has no in-repo precedent; the volume ceiling is the applied mitigation | closed |
| T-20-09-04 | Denial of Service | low [auditor] | accept | ADR-13 verbatim: "`AuditLogger` still has no size cap or rotation" | closed |
| T-20-09-05 | Information Disclosure | low [auditor] | accept | Count exists only in the local audit record; response is a bare status | closed |
| T-20-10-01 | Tampering | n/a | mitigate | `build.gradle.kts:165` `systemProperty("storeBuild.expected", …)`; test asserts the seam, not a literal | closed |
| T-20-10-02 | Repudiation | n/a | mitigate | `docs/mcp-hardening.md:49` names CORS as the responder and states the foreign-`Origin` denial is not recorded | closed |
| T-20-10-03 | Information Disclosure | n/a | mitigate | Same bullet: "a missing Output-tab line or audit entry here is not evidence of a broken audit trail" | closed |
| T-20-10-04 | Spoofing | n/a | mitigate | `McpAccessControlExternalPipelineTest.kt:161-169` asserts `Protocol.HTTP_2` **before** status/headers | closed |
| T-20-10-05 | Denial of Service | low [auditor] | accept | `nightlyRegressionTest` carries no `storeBuild.expected`. **See drift 3** | closed |
| T-20-SC · T-20-07-SC · T-20-08-SC · T-20-09-SC · T-20-10-SC | Tampering (supply chain) | low [auditor] | accept | `20-RESEARCH.md:852` zero new deps; **independently proven** — `git diff 286d686..67264d0 -- build.gradle.kts` adds, removes and version-changes no dependency line. `build.gradle.kts` WAS edited (build-flag plumbing); the file changing and no dependency changing are both true | closed |

---

## Accepted Risks Log

This log is what the 19 `accept` rows require. It did not exist before this file.

| Risk ID | Threat Ref | Rationale | Accepted By | Date |
|---|---|---|---|---|
| **Residual 1 — fail-OPEN** | T-20-08-05 | An h2 request with `:authority` ABSENT coalesces to the server's own socket and is **ALLOWED**. Not closable through Ktor's public API — raw `Http2Headers` sits behind an `internal` class, so absent and loopback-valued authorities are indistinguishable from the plugin. RFC 9113 makes such a request malformed, so a conforming client or intermediary will not produce one. **Highest severity of the accepted set because it is the one accept row whose failure mode is "allow" rather than "deny."** Verified verbatim at `McpAccessControlPlugin.kt:168-172`. | Plan 20-08 disposition | 2026-08-10 |
| Residual 2 | T-20-08-06 | Bracketed IPv6 `:authority` split on its FIRST colon by the h2 connection point; `[::1]:9876` → `[`, unreconstructable, request DENIED. Reachable only for an operator who binds `::1` AND enables TLS AND uses an h2 client. Fail-closed. `:173-178`. | Plan 20-08 disposition | 2026-08-10 |
| Residual 3 | T-20-08-08 | A portless `:authority` acquires the scheme default port, so h2 DENIES `:authority: localhost` (resolved `localhost:443` over TLS) where HTTP/1.1 ALLOWS `Host: localhost`. Fail-closed transport asymmetry. `:179-183`. **Observed reach narrowed 2026-08-28** by UAT test 1: the tested client's h2 `:authority` carried an explicit port, so it did not fire. **NOT closed** — a client emitting a portless `:authority` would still be denied. | Plan 20-08 disposition; reach narrowed by UAT | 2026-08-10 / 2026-08-28 |
| Status oracle | T-20-08 | D-08: bare status, no body, no `WWW-Authenticate`. The 401-vs-403 difference distinguishes external from local mode; diagnosis is confined to the local log. | Plan 20-01/20-03 (D-08) | 2026-08-10 |
| Pre-auth CORS preflight | T-20-09 | CORS installs before the gate so `OPTIONS` is answerable pre-auth; with empty `allowedOrigins` the code calls `anyHost()`. Closing it would break any future browser client. Documented in the runbook rather than changed. | Plan 20-03/20-05 (RESEARCH OQ2) | 2026-08-10 |
| Audit-ON DoS surface | T-20-12 | Stated for the audit-ON case, not the default. Superseded in practice for the two remote-reachable reasons by ADR-13's coalescing. | Plan 20-01 | 2026-08-10 |
| Version disclosure | T-20-13 | `project.version` over MCP is intended SEC-05 5a behaviour — not a secret, not attacker-influenced. | Plan 20-02 | 2026-08-10 |
| Test-only trust-all TLS | T-20-15 | `trustAllClient` confined to the test source set against a loopback server the test itself starts; never reachable from `src/main`. | Plan 20-02 | 2026-08-10 |
| External takeover identity | T-20-17 | With the identity header gone, external takeover proceeds on liveness alone. The header was trivially spoofable anyway (Finding 7), so this is not a new weakness; it is logged so the operator can see it. | Plan 20-05 | 2026-08-10 |
| Fail-closed host branch | T-20-07-04 | A legitimate client denied by the new fail-closed branch. Any client speaking HTTP/1.1 or h2 correctly sends `Host` or `:authority`. Now additionally evidenced by UAT test 1. | Plan 20-07 | 2026-08-10 |
| Sync file append on event loop | T-20-09-03 | Async hand-off has no in-repo precedent; the T-20-09-01 volume ceiling is the mitigation actually applied. | Plan 20-09 (ADR-13) | 2026-08-10 |
| Unbounded `audit.jsonl` | T-20-09-04 | Requires local code execution. `AuditLogger` size-capping explicitly rejected as out of scope; recorded verbatim in ADR-13. | Plan 20-09 (ADR-13) | 2026-08-10 |
| `suppressed` counter | T-20-09-05 | Exists only in the local audit record; D-08 keeps the response a bare status. | Plan 20-09 | 2026-08-10 |
| Test-cache invalidation | T-20-10-05 | Desirable: a `storeBuild` flip must re-run the tests. Scoped so `nightlyRegressionTest` inputs are unchanged. | Plan 20-10 | 2026-08-10 |
| Supply chain (×5 rows) | T-20-SC family | No dependency added, removed or version-changed across the whole phase; independently proven by diff, not asserted. | Plan-time, all 10 plans | 2026-08-10 |

**Transfer:** T-20-10 (bind-conflict takeover token disclosure) → **Phase 25 / SEC-07** per D-05. Phase 20's obligation was only "do not make it worse", and the probe sends no credential. The recipient has since shipped: `McpTakeoverProof` presents an HMAC-SHA256 proof of possession instead of the token.

---

## Live Evidence

`./gradlew test --tests '*McpAccessControl*' --tests '*McpLocalTlsAuthority*' --tests '*BlockedRequestReporter*' --tests '*McpBuildFlags*' --tests '*KtorMcpServerManagerSecurity*' --tests '*KtorMcpCorsPolicy*'` → **98 tests, 0 failures, 0 errors, 0 skipped**, run by the auditor **in the current tree**, not the phase-20 tree. Phases 25–28 have edited these files since phase 20 closed; the mitigations survive.

Per class: Decision 40 · ExternalPipeline 8 · Pipeline 9 · LocalTlsAuthority 7 · Reporter 22 · BuildFlags 2 · CorsPolicy 3 · ManagerSecurity 7.

**Unregistered flags: none.** Not taken on trust — `git diff --stat 286d686..67264d0 -- src/main/` touches exactly five files, all inside declared trust boundaries; there is exactly one `embeddedServer` call site in `src/main` (`KtorMcpServerManager.kt:133`) and `install(McpAccessControl)` is on it, so no second listener bypasses the gate.

**Human verification:** both `20-HUMAN-UAT.md` tests passed 2026-08-28 against a live Burp with a real MCP client — local SSE over HTTP/1.1 and h2, and external mode with bearer token over TLS.

---

## Drift Observations — informational, not open threats

1. **T-20-01's mitigation text is now narrower than the code.** `evaluateExternal` gained a fourth
   `Allow` limb at `McpAccessControlDecision.kt:160-162`: `SHUTDOWN_PATH` + a valid
   `McpTakeoverProof`. **Verified independently by the orchestrator, not assumed:**
   `McpTakeoverProof.kt:71` uses `Mac.getInstance("HmacSHA256")` keyed by the same token,
   `:99` rejects a blank token, and the limb at `:160` sits **below** the `BLANK_TOKEN` deny at
   `:149` — so SEC-05 5c still fails closed first. It opens nothing, but it is Phase 25 / SEC-07
   surface that post-dates this register.
2. **T-20-06's sanitizer moved.** Phase 28's WR-05 collapsed the duplicated implementation;
   `McpBlockedRequestReporter.sanitize` now delegates to `sanitizeInline` in `ToolApprovalGate.kt`
   with cap 200 and the `"..."` marker. Control intact, evidence location moved.
3. **T-20-10-05's scope widened.** "Scoped to `tasks.test` only" is now `tasks.test` +
   `edtGuardWithoutAssertionsTest` (`build.gradle.kts:327`, a later REL-05 task). The accept's
   load-bearing claim — `nightlyRegressionTest` inputs unchanged — still holds.

---

## Recommendation

**Add a Severity column to the threat-register template before the next phase is planned.** Phase 20
shipped 49 threats with none. It cost nothing here only because nothing was open; had one been, the
`block_on: high` gate would have had no severity to threshold against and would have had to fail
closed on every unranked threat.

---

## Security Audit Trail

| Audit Date | Threats Total | Closed | Open | Run By |
|------------|---------------|--------|------|--------|
| 2026-08-28 | 49 | 49 | 0 | `gsd-security-auditor` via `/gsd-secure-phase 20` (ASVS L1 + live suite) |

---

## Sign-Off

- [x] All threats have a disposition (29 mitigate / 19 accept / 1 transfer)
- [x] Accepted risks documented in the Accepted Risks Log — created by this file, per finding 2
- [x] `threats_open: 0` confirmed on the merits, not by default
- [x] Retroactive severity provenance disclosed; register's own omission recorded as a defect
- [x] Residual 1 recorded as the one fail-OPEN accept, at the highest severity of the accepted set
- [x] Residual 3 recorded as narrowed-by-UAT, explicitly NOT closed
- [x] Drift between register text and current code recorded, with the security-relevant one re-verified
