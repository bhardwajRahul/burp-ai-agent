---
phase: 25
slug: secondary-hardening
status: verified
# threats_open = count of OPEN threats at or above workflow.security_block_on severity (the blocking gate)
threats_open: 0
asvs_level: 1
created: 2026-08-24
---

# Phase 25 — Security

> Per-phase security contract: threat register, accepted risks, and audit trail.

Phase 25 (Secondary Hardening, requirement **SEC-07**) closed Findings 7 and 15 — the MCP bearer
token reaching a listener whose identity could not be established, and `SsrfGuard`'s advisory being
sidesteppable by IP notation alone.

**Register origin:** authored at plan time. All three PLAN files (`25-01`, `25-02`, `25-03`) shipped
a `<threat_model>` block, so this audit **verifies that the declared mitigations exist** rather than
constructing a register retroactively. Verification depth is ASVS L1 (source-level evidence), which
the workflow declares sufficient when `threats_open: 0` and the register is plan-authored.

---

## Trust Boundaries

| Boundary | Description | Data Crossing |
|----------|-------------|---------------|
| local process → MCP listener port | Any local user can bind `127.0.0.1:<mcp port>`. Whoever holds it is untrusted, and the extension cannot tell from the socket who they are. | Takeover credential |
| takeover client → port holder | The extension initiates this call and, before this phase, presented a secret across it. This is the Finding 7 boundary. | MCP bearer token (before) / proof of possession (after) |
| takeover client → TLS loopback listener | Before 25-03 the client accepted any certificate here, so it could not distinguish its own server from any local process holding the port. | TLS handshake, server identity |
| `settings.tlsKeystorePath` → server key material **and** client pin | One filesystem path is read by `KtorMcpServerManager` to serve TLS and by `McpTls.pinnedLeafSha256` to pin it. Whoever can write it controls both sides; whoever can read it can impersonate the server. | Private key, leaf certificate |
| user-entered / imported backend base-URL → `SsrfGuard` | Not fully trusted: arrives from the settings UI and from settings import, which QUAL-06 records as an attacker-reachable path. The classifier must be total and must never act on it. | URL text |
| `SsrfGuard` → name resolution | The boundary SC4 forbids crossing. Crossing it turns a pure classifier into an outbound network action driven by unvalidated text. | Hostname (must not cross) |
| MCP client → MCP server routes | The existing SEC-04 boundary from phase 20. Unchanged here; the shutdown route gains one accepted credential form and loses none. | Request credentials |
| ADR-16 / runbook → the operator | A documentation boundary, and a real one: a residual that is accepted but not written down is indistinguishable from a residual nobody noticed. | Accepted-risk record |

---

## Threat Register

| Threat ID | Category | Component | Severity | Disposition | Mitigation | Status |
|-----------|----------|-----------|----------|-------------|------------|--------|
| T-25-01 | Information Disclosure | `McpSupervisor` takeover client | critical | mitigate | `Authorization` request property deleted; `McpTakeoverProof.forTarget` presented instead. **Verified:** `settings.token` appears exactly once in `McpSupervisor.kt`, as the HMAC key argument; zero `setRequestProperty("Authorization"…)`. Asserted by `McpTakeoverSquatterTest`. | closed |
| T-25-02 | Spoofing | `probeExistingServer`, external mode | high | mitigate | Mitigated by removing what the spoof buys — the credential presented after a successful probe is worthless to a non-holder. Listener identity additionally restored under TLS by T-25-12's pin. | closed |
| T-25-03 | Spoofing | `X-Burp-AI-Agent: mcp` header, local mode | high | mitigate | Same mitigation as T-25-02. Header retained as a cheap filter and explicitly demoted from control to hint in ADR-16 clause 2. | closed |
| T-25-04 | Denial of Service | Replay of a captured takeover proof | low | **accept** | Valid for one 10 s window plus one fallback (20 s worst case) and authorises only shutdown of our own server on that exact host and port. The process able to capture it already holds the port and is therefore already denying the service. See Accepted Risks. | closed |
| T-25-05 | Elevation of Privilege | Server-side proof acceptance on `POST /__mcp/shutdown` | medium | mitigate | Constant-time comparison (`MessageDigest.isEqual`); blank token rejects unconditionally; proof bound to host, port and window so it cannot be lifted to another instance. **Verified** in `McpTakeoverProof.kt`. | closed |
| T-25-06 | Repudiation | A takeover shutdown leaves no audit record | low | **accept** | Pre-existing behaviour, unchanged. `handleBindFailure` writes to the Burp Output tab on every branch. Adding an audit event would widen ADR-13's flood surface for no stated requirement. See Accepted Risks. | closed |
| T-25-07 | Information Disclosure | `SsrfGuard` → `InetAddress.getByName` | medium | mitigate | A live defect, measured not theoretical: `256.0.0.1` and `0400.0.0.1` reached `getByName` and were resolved as names, leaking the string to the configured resolver. Closed by construction — the IPv4 branch classifies from `Ipv4Literal.parse` bytes via `getByAddress`. **Verified:** `grep -c getByName SsrfGuard.kt` = 1 (IPv6 arm only). Asserted by `SsrfGuardNoResolutionTest`. | closed |
| T-25-08 | Spoofing | Notation-based evasion of the classifier | medium | mitigate | Parse-then-classify per SC3: decimal, octal and hex literals are parsed and classified identically to their dotted-quad equivalent. **Verified:** `Ipv4Literal` referenced from `SsrfGuard.kt`. Asserted by `SsrfGuardTest`. | closed |
| T-25-09 | Denial of Service | Pathological input to `Ipv4Literal.parse` | low | mitigate | No regex in the parser, so no ReDoS surface. **Verified:** the two `Regex` occurrences in `Ipv4Literal.kt` are KDoc prose stating there is deliberately none — zero imports, zero instantiations. Bounded O(len) by `MAX_LITERAL_LENGTH` and a 4-part cap. | closed |
| T-25-10 | Denial of Service | False-positive regression on loopback | medium | mitigate | Widening the notation set could make loopback start warning, pushing users off local backends toward cloud providers — a privacy regression driven by a security fix. Loopback exclusion pinned in all four notations; the pre-existing contract required to pass unchanged. | closed |
| T-25-11 | Tampering | Test-JVM-wide `InetAddressResolverProvider` | low | mitigate | Delegates every call to `configuration.builtinResolver()` and only counts. Confined to `src/test`, never reaches `shadowJar`, cannot affect the shipped extension. Verified by requiring the full suite green, not the new class alone. | closed |
| T-25-12 | Spoofing | `openConnection`, loopback TLS branch | medium | mitigate | Blanket-accept `X509TrustManager` and unconditional-true `HostnameVerifier` replaced by a SHA-256 leaf pin compared with `MessageDigest.isEqual`. **Verified:** zero surviving `HostnameVerifier { _, _ -> true }`; `pinnedLeafSha256`/`pinnedSslContext` present. Proven to discriminate by `McpTakeoverCertificatePinTest`. | closed |
| T-25-13 | Elevation of Privilege | A trust-all fallback when the pin cannot be read | medium | mitigate | Pin-when-available/trust-otherwise would leave the original weakness reachable by deleting one file, with every pre-existing test still green. Absent pin installs **no TLS override at all**. **Verified:** explicit `pin == null` fail-closed branch. | closed |
| T-25-14 | Tampering | `pinnedLeafSha256` generating key material as a side effect | medium | mitigate | Reusing `McpTls.resolve` would auto-generate a keystore during a client-side probe, minting a certificate guaranteed not to match the running server. `pinnedLeafSha256` reads or returns null. **Verified:** zero generate/write/mkdir calls in its body. | closed |
| T-25-15 | Spoofing | Local attacker with read access to `tlsKeystorePath` | low | **accept** | Such an attacker can present the pinned certificate — but also reads Burp Preferences, where `SecretCipher`'s master key sits beside its ciphertext, so they already hold the MCP token. The pin was never what stood between them and the server. See Accepted Risks. | closed |
| T-25-16 | Denial of Service | Fail-closed takeover under TLS | low | **accept** | A user whose keystore moved or was deleted loses automatic takeover under TLS. Bounded and diagnosable: one Output line names the path. Preferred over trusting an unidentified listener. See Accepted Risks. | closed |
| T-25-17 | Repudiation | Accepted residuals living only in PLAN files | medium | mitigate | Residuals moved into ADR-16 as individual `Residual:` bullets, outside `.planning/`. **Verified:** 7 `Residual:` bullets in ADR-16, guarded by `DecisionsAdrTest.adr16RecordsEveryResidualItAccepts` with `MIN_ADR16_RESIDUALS = 7`. | closed |
| T-25-18 | Information Disclosure | Offline token guessing from a captured proof | medium | **accept** | **Not in the plan-time register — discovered post-phase** by the phase-26 code review (WR-01). The proof's HMAC message is fully known to a squatter, making a captured proof an offline verifier for the MCP token; the token field had no entropy floor. Partially mitigated in phase 26 by `Defaults.MCP_MIN_TOKEN_LENGTH` + `McpSettings.isTokenWeak` — **advisory only, it does not block**. Recorded as ADR-16's seventh residual. See Accepted Risks. | closed |
| T-25-SC | Tampering | npm/pip/cargo installs | high | mitigate | **Not applicable by construction: this phase installed no package.** Kotlin/Gradle only. **Verified:** zero commits touching `build.gradle.kts` across the phase (`dcd9cca..4f0ebd7`), and the repository has no `package.json`, `requirements.txt` or `Cargo.toml`. No `[ASSUMED]`/`[SUS]` package exists to gate. | closed |

*Status: open · closed · open — below high threshold (non-blocking)*
*Severity: critical > high > medium > low — only open threats at or above `workflow.security_block_on` (`high`) count toward `threats_open`*
*Disposition: mitigate (implementation required) · accept (documented risk) · transfer (third-party)*

---

## Accepted Risks Log

| Risk ID | Threat Ref | Rationale | Accepted By | Date |
|---------|------------|-----------|-------------|------|
| AR-25-01 | T-25-04 | Proof replay inside its validity window authorises only shutdown of our own server on that exact host and port, for 20 s worst case. The process able to capture it already holds the port and is therefore already denying the service. A server-side single-use nonce cache was rejected as disproportionate. Stated to the developer **before** the SC1 selection was taken. | Developer (blocking checkpoint, plan 25-01 Task 1) | 2026-08-22 |
| AR-25-02 | T-25-06 | A takeover shutdown leaves no audit event. Pre-existing behaviour; `handleBindFailure` writes to the Burp Output tab on every branch, which is the diagnosable record. Adding an audit event would widen ADR-13's flood surface for no stated requirement. | Plan 25-01 threat model | 2026-08-22 |
| AR-25-03 | T-25-15 | A local attacker who can read `tlsKeystorePath` can present the pinned certificate — but the same attacker reads Burp Preferences and therefore already holds the MCP token. The pin defends against a local process that can bind the port but cannot read the user's files, which is the boundary Finding 7 is about. | Plan 25-03 threat model; ADR-16 residual | 2026-08-22 |
| AR-25-04 | T-25-16 | Fail-closed under TLS costs automatic takeover to a user whose keystore moved or was deleted. Preferred over the alternative, which is trusting an unidentified listener. Diagnosable via a named Output line. | Plan 25-03 threat model; ADR-16 residual | 2026-08-22 |
| AR-25-05 | T-25-18 | Offline token guessing from a captured proof. The mitigation shipped in phase 26 is an **advisory** weakness notice, not an enforcing minimum — a user who ignores it and types a weak token remains exposed to offline recovery of that token. Accepted because the disclosure is still strictly better than the pre-phase behaviour of handing the plaintext token to the port holder, and because enforcing a minimum would break existing configurations on upgrade. | Phase-26 code review (WR-01); ADR-16 seventh residual | 2026-08-22 |

---

## Security Audit Trail

| Audit Date | Threats Total | Closed | Open | Run By |
|------------|---------------|--------|------|--------|
| 2026-08-24 | 19 | 19 | 0 | `/gsd-secure-phase 25` (orchestrator, ASVS L1 source verification) |

**Note on the count.** 19 register rows across 18 distinct threat IDs — `T-25-SC` is declared
identically in all three PLAN files and is counted once. `T-25-18` was added by this audit from a
post-phase finding rather than from the plan-time register.

---

## Sign-Off

- [x] All threats have a disposition (mitigate / accept / transfer)
- [x] Accepted risks documented in Accepted Risks Log
- [x] `threats_open: 0` confirmed
- [x] `status: verified` set in frontmatter

**Approval:** verified 2026-08-24

### What this audit did and did not do

**Did:** verified in source that each `mitigate` threat's declared control exists, at ASVS L1
(source-level evidence — symbol presence, absence of the removed weakness, guard branches). Every
"**Verified:**" claim in the register above is a check run against the tree at `1.0.0`, not a
restatement of a SUMMARY assertion.

**Did not:** re-derive the register from the implementation, or scan for threats the plans did not
declare. The workflow constrains a plan-authored register to mitigation verification. The one
exception is `T-25-18`, admitted because it was already a known, recorded finding — omitting it would
have let this document claim a completeness the plan-time register did not have.

**Known limit of L1 depth:** source-level evidence proves a control is present, not that it is
correct under every input. The behavioural proof for these threats lives in the phase's test suite
(`McpTakeoverSquatterTest`, `McpTakeoverCertificatePinTest`, `SsrfGuardNoResolutionTest`) and in
`25-VERIFICATION.md`, which verified 5/5 must-haves by executing the compiled classes.
