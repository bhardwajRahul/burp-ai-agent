---
phase: 25-secondary-hardening
verified: 2026-08-22T11:06:10Z
status: passed
score: 5/5 must-haves verified
behavior_unverified: 0
overrides_applied: 0
requirements:
  - id: SEC-07
    status: satisfied
    note: >-
      Both clauses of SEC-07 are satisfied by the union of 25-01, 25-02 and 25-03.
      The requirement may be ticked centrally in REQUIREMENTS.md.
warnings:
  - id: W-1
    source: 25-REVIEW WR-01
    truth: "SC1 — replacement chosen deliberately AND recorded"
    verdict: does_not_block
    summary: >-
      The proof's HMAC message is fully known to a squatter, making the value an offline
      verifier for the token, and the token field has no entropy floor (isBlank() only).
      This residual is absent from ADR-16's six Residual bullets. It is a NEWLY DISCOVERED
      residual, not an accepted-and-omitted one, and the disclosure is strictly better than
      the pre-phase behaviour it replaced. SC1's "recorded" obligation is discharged.
    follow_up: >-
      Add a seventh Residual bullet to ADR-16 and raise MIN_ADR16_RESIDUALS to 7 in the same
      commit; add an entropy floor / weak-token notice on the MCP token field.
  - id: W-2
    source: 25-REVIEW WR-02
    truth: "SC3 / SEC-07 clause 2 — notation evasion"
    verdict: does_not_block
    summary: >-
      IPv4-mapped IPv6 literals (http://[::ffff:169.254.169.254]/) are still classified false.
      Measured against the compiled SsrfGuard by this verifier. PRE-EXISTING — IPV6_REGEX is
      byte-identical to the pre-phase tree (dcd9cca). Outside SC3's and SEC-07's enumerated
      decimal/octal/hexadecimal IPv4 forms, and the guard is advisory (D-01), so the user is
      under-warned but nothing is permitted that was previously denied.
    follow_up: "Allow '.' in IPV6_REGEX and extend both test corpora; carry to phase 26 or a defect."
  - id: W-3
    source: 25-REVIEW WR-03 + IN-02
    truth: "SC5 — loopback trust-all scoped; ADR-16 residual guard"
    verdict: does_not_block
    summary: >-
      (a) The pin branch is gated on isLoopbackUrlHost, unchanged from pre-phase, so external
      mode on a non-loopback host installs no override, fails the handshake against JDK defaults
      and reports "no compatible MCP server" — while docs/mcp-hardening.md item 3, added by this
      phase, promises a different diagnostic. Documentation/reliability, not a trust weakening.
      (b) MIN_ADR16_RESIDUALS = 5 against 6 shipped bullets, so the guard cannot catch the
      deletion of exactly one.
    follow_up: "Make the non-loopback TLS diagnostic honest; raise MIN_ADR16_RESIDUALS."
---

# Phase 25: Secondary Hardening — Verification Report

**Phase Goal:** Close the two remaining findings where a control exists but can be sidestepped.
**Verified:** 2026-08-22T11:06:10Z
**Status:** passed
**Re-verification:** No — initial verification

## Goal Achievement

### Observable Truths

| # | Truth (ROADMAP Success Criterion) | Status | Evidence |
|---|---|---|---|
| 1 | Takeover no longer sends `Authorization: Bearer <token>` to a listener identified only by the spoofable `X-Burp-AI-Agent: mcp` header; the replacement is chosen deliberately and recorded | ✓ VERIFIED | `McpSupervisor.kt` contains no `Authorization` request property on any path (`setRequestProperty` appears once, with `McpTakeoverProof.HEADER`). `settings.token` appears exactly **once** in the file, on the `takeoverProof` line as the HMAC key. `git show dcd9cca:...McpSupervisor.kt:301` confirms the pre-phase line was `conn.setRequestProperty("Authorization", "Bearer ${settings.token}")`. Choice recorded in `DECISIONS.md` ADR-16 clause 1 (verbatim human selection `proof-of-possession`, options A and B named and rejected with reasons) and mirrored in `25-01-SUMMARY.md` §"SC1 decision" and `docs/mcp-hardening.md` §"Takeover on a Bind Conflict" item 2. |
| 2 | A squatter that echoes the probe header does not receive the token; asserted by a test standing up a fake listener | ✓ VERIFIED | `McpTakeoverSquatterTest` (4 tests, all green in this verifier's own run) binds a real `MockWebServer` on `127.0.0.1`, answers `/__mcp/health` `200` **with** a spoofed `X-Burp-AI-Agent: mcp`, drives the private `attemptTakeover` by reflection, and asserts on the recorded POST: `Authorization` is null, the token string appears **nowhere in the serialised header block**, and the proof header is present and `!=` the token. Covers local mode, external mode and the 401-refusal path. |
| 3 | `isPrivateOrLinkLocal` parses decimal/octal/hex IPv4 literals and classifies them identically to the dotted quad (corrected wording) | ✓ VERIFIED | Verified **independently of the test suite** by executing the compiled `build/classes/kotlin/main/…/SsrfGuard` under JDK 21: `http://2130706433/` → false, `http://0177.0.0.1/` → false, `http://0x7f.1/` → false, `http://2852039166/` → **true**, `http://169.254.169.254/` → true. Also `0x7f000001` → false, `017700000001` → false, `127.1` → false (all loopback). Mechanism: `Ipv4Literal.parse` (pure `String → ByteArray`, zero imports) feeds `InetAddress.getByAddress`. Pinned by `SsrfGuardTest` (17) + `Ipv4LiteralTest` (13). Old `IPV4_REGEX` confirmed deleted (`git show 1b234e7^` line 24). |
| 4 | Still false for loopback, and still performs no DNS resolution | ✓ VERIFIED | Loopback: runtime probe above plus `http://127.0.0.1:11434` → false and `http://localhost:11434` → false — Ollama/LM Studio users see no new warning. No resolution: `SsrfGuardNoResolutionTest` (2 tests, green) counts JVM-wide lookups through `CountingInetAddressResolverProvider`, asserts `0` across a 15-URL corpus. Non-vacuity is structural — see §"SC4 vacuity analysis". `grep -c getByName SsrfGuard.kt` = 1, on the ':'-gated IPv6 branch only. |
| 5 | The `openConnection` loopback trust-all path is scoped to exactly the certificate the extension generated (or its residual documented) | ✓ VERIFIED | The pre-phase `checkServerTrusted { }` + unconditional-true `HostnameVerifier` (`git show dcd9cca:...:326-343`) are gone. The client installs an `SSLContext` whose only trust manager compares the leaf's SHA-256 to `McpTls.pinnedLeafSha256(settings)` with `MessageDigest.isEqual`, and the verifier is replaced by the same digest check. Proven behaviourally by `McpTakeoverCertificatePinTest` (3 tests, green): a legitimate TLS takeover succeeds and the server actually stops listening; a **second real keystore** minted through the same `keytool` path is refused and the target server survives; an absent keystore installs no override, fails closed, and the keystore file is asserted still non-existent afterwards. Residuals recorded in ADR-16 (filesystem read defeats the pin; fail-closed under TLS). |

**Score:** 5/5 truths verified (0 present, behaviour-unverified)

### Required Artifacts

| Artifact | Expected | Status | Details |
|---|---|---|---|
| `src/main/kotlin/…/mcp/McpTakeoverProof.kt` | HMAC proof, token as key only | ✓ VERIFIED | 10 s window + one fallback window; blank-token guard before any computation; `constantTimeCompare`; domain-separated `v1` prefix. Wired from `McpSupervisor.requestRemoteShutdown` (client) and both `KtorMcpServerManager`'s shutdown route and `McpAccessControlDecision.evaluateExternal` (server). |
| `src/main/kotlin/…/util/Ipv4Literal.kt` | Pure inet_aton parser | ✓ VERIFIED | Zero `import` lines; no `Regex`; `MAX_LITERAL_LENGTH` cap; `Long` intermediates so 32-bit maxima hold. Consumed by `SsrfGuard.isPrivateOrLinkLocal`. |
| `src/main/kotlin/…/util/SsrfGuard.kt` | IPv4 branch resolves nothing | ✓ VERIFIED | Signature unchanged; `extractAuthorityHost` byte-identical to pre-phase (diffed). |
| `src/main/kotlin/…/mcp/McpTls.kt` (`pinnedLeafSha256`) | Read-only pin reader | ✓ VERIFIED | Returns null on blank path or missing file **before** touching KeyStore; `runCatching` fails closed; never calls `resolve`, never `mkdirs`, never writes. Asserted behaviourally by `noPinAvailableInstallsNoOverrideAndFailsClosed`. |
| `src/test/…/McpTakeoverSquatterTest.kt` | SC2 fake listener | ✓ VERIFIED | 4/4 green. |
| `src/test/…/McpTakeoverPipelineTest.kt` | Legitimate takeover still works | ✓ VERIFIED | 4/4 green — local proof takeover, external-mode proof takeover, blank token issues no request, no-credential external shutdown still 401. |
| `src/test/…/McpTakeoverProofTest.kt` | Proof algebra | ✓ VERIFIED | 8/8 green — window, previous-window, two-windows-ago rejection, blank token, raw token rejected, wrong host/port rejected. |
| `src/test/…/McpTakeoverCertificatePinTest.kt` | SC5 discrimination | ✓ VERIFIED | 3/3 green against real Netty TLS servers on loopback. |
| `src/test/…/McpSupervisorConnectionTest.kt` | `openConnection` truth table | ✓ VERIFIED | 4/4 green. |
| `src/test/…/CountingInetAddressResolverProvider.kt` | Counts, never decides | ✓ VERIFIED | Every override delegates verbatim to `configuration.builtinResolver()`; counter is a pure side effect; bounded name buffer. |
| `src/test/resources/META-INF/services/java.net.spi.InetAddressResolverProvider` | Installs the counter | ✓ VERIFIED | Contains exactly `com.six2dez.burp.aiagent.util.CountingInetAddressResolverProvider`, matching the file's `package` + class name. Present on the test runtime classpath at `build/resources/test/META-INF/services/`. |
| `DECISIONS.md` (ADR-16) | Records decision + residuals | ✓ VERIFIED | 3 decision clauses, 6 `Residual:` bullets, no `.kt:NNN` line references (ADR-15 rule satisfied — grep returns none). Explicitly records the access-control-gate deviation in its Consequences. |
| `docs/mcp-hardening.md` | Operator runbook | ✓ VERIFIED | §"Takeover on a Bind Conflict", 4 items + pointer to ADR-16. See W-3(a) for the non-loopback accuracy caveat. |

### Key Link Verification

| From | To | Via | Status | Details |
|---|---|---|---|---|
| `McpTakeoverProof.forTarget` (client) | `McpTakeoverProof.accepts` (server) | shared message construction + `WINDOW_MS` | ✓ WIRED | Same object, single `WINDOW_MS` constant, no duplication possible. Proven end-to-end by `theProofCredentialTakesOverOurOwnRunningServer` and `aLegitimateTlsTakeoverSucceedsAgainstThePinnedCertificate` — the MCP server does not stay down after a bind conflict. |
| `McpSettings.token` | HMAC key | `takeoverProof()` | ✓ WIRED | Exactly one occurrence of `settings.token` in `McpSupervisor.kt`, on the `forTarget` line. Structurally pinned by `theTakeoverClientHoldsTheTokenOnlyAsAnHmacKey`. |
| `Ipv4Literal.parse` | `SsrfGuard.isPrivateOrLinkLocal` | `InetAddress.getByAddress(bytes)` | ✓ WIRED | Runtime-confirmed by direct classification of all four SC3 forms. |
| service file | `CountingInetAddressResolverProvider` | ServiceLoader | ✓ WIRED | Control test proves installation at runtime. |
| `McpTls.pinnedLeafSha256` (reader) | `McpTls.resolve` (generator) | deliberately NOT shared | ✓ WIRED | Confirmed separate implementations; `noPinAvailableInstallsNoOverrideAndFailsClosed` asserts the missing keystore is still missing after a pin attempt. |
| `McpSupervisor.openConnection` | `KtorMcpServerManager.start` → `McpTls.resolve` | shared `settings.tlsKeystorePath` | ✓ WIRED | Both read the same setting; ADR-16 clause 3 states the identity argument this creates. |
| `McpTakeoverProof` (credential) | certificate pin (listener identity) | ADR-16 clauses 1 + 3 | ✓ WIRED | ADR-16 states the division: the proof covers cleartext local mode where pinning cannot reach; the pin restores listener identity wherever TLS is on. |

### Data-Flow Trace (Level 4)

Not applicable in the rendering sense — this phase ships no UI-rendered dynamic data. The equivalent
check (does a value flow from a real source, or is it a static stand-in?) was run on the two
credentials:

| Value | Source | Flows to real consumer | Status |
|---|---|---|---|
| Proof header value | `HMAC-SHA256(key = settings.token, …)` at request time | Yes — validated by a **real** Netty server in `McpTakeoverPipelineTest` and by a **real** hostile listener in `McpTakeoverSquatterTest` | ✓ FLOWING |
| Certificate pin | SHA-256 of the leaf in the real PKCS12 keystore at `settings.tlsKeystorePath` | Yes — compared against a **real** TLS handshake's peer chain | ✓ FLOWING |

### Behavioural Spot-Checks

Run by this verifier, not taken from any SUMMARY.

| Behaviour | Command | Result | Status |
|---|---|---|---|
| Phase-25 test classes pass on the merged tree | `JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test --tests 'com.six2dez.burp.aiagent.util.SsrfGuard*' --tests '…Ipv4LiteralTest' --tests '…mcp.McpTakeover*' --tests '…McpSupervisorConnectionTest' --tests '*DecisionsAdrTest'` | BUILD SUCCESSFUL — 9 classes, **60 tests, 0 failures, 0 skipped** | ✓ PASS |
| SC3 classification, compiled class, independent of the suite | `java -cp build/classes/kotlin/main:kotlin-stdlib Probe` | `2130706433`/`0177.0.0.1`/`0x7f.1` → false; `2852039166` → true | ✓ PASS |
| SC4 loopback exclusion | same harness | `127.0.0.1:11434`, `localhost:11434` → false | ✓ PASS |
| WR-02 premise (adversarial) | same harness | `[::ffff:169.254.169.254]` → **false**, `[::ffff:a9fe:a9fe]` → true | ✗ CONFIRMED GAP (pre-existing — see W-2) |
| No resolving call left on the IPv4 branch | `grep -c getByName SsrfGuard.kt` | `1` (IPv6 branch only) | ✓ PASS |
| Token never materialised | `grep -n "settings.token" McpSupervisor.kt` | 1 occurrence, on the `forTarget` line | ✓ PASS |
| Frozen files untouched | `git diff --name-only dcd9cca..HEAD` | `detekt-baseline.xml` and `build.gradle.kts` absent from the diff | ✓ PASS |
| Debt markers in phase-modified files | `grep -nE "TBD\|FIXME\|XXX\|TODO\|HACK\|PLACEHOLDER"` over all 15 non-`.planning` changed files | zero matches | ✓ PASS |

`RedactionTest` was not exercised (out of scope for the targeted run); its recorded wall-clock flake is
therefore not a factor in this verdict.

### Probe Execution

No `scripts/*/tests/probe-*.sh` exists in this repository and no plan declares one. Probe execution:
**N/A**. The equivalent evidence is the targeted Gradle run above, executed in this verifier's own
process.

### Requirements Coverage

| Requirement | Source plans | Description | Status | Evidence |
|---|---|---|---|---|
| SEC-07 | 25-01, 25-02, 25-03 | (Findings 7, 15) The MCP bearer token is never sent to a listener whose identity has not been established — the bind-conflict takeover path stops presenting `Authorization: Bearer <token>` to an unverified port holder. `SsrfGuard` additionally classifies IPv4 literals written in decimal, octal and hexadecimal form, so its advisory warning cannot be sidestepped by notation alone. | ✓ SATISFIED | **Clause 1** — no `Authorization` header is emitted on any takeover path (grep + `git show` of the deleted pre-phase line + `McpTakeoverSquatterTest`'s whole-header-block assertion). Under TLS the client additionally refuses to hand anything to a listener that cannot present the pinned certificate, and fails closed rather than downgrading. **Clause 2** — all three enumerated notations are parsed and classified identically to their dotted-quad equivalents, verified by direct execution of the compiled class. |

**Explicit statement for the orchestrator:** SEC-07's text is genuinely satisfied by the union of the
three plans' work. The shared-ID gate correctly left the box unticked per plan; **SEC-07 may now be
ticked centrally in `.planning/REQUIREMENTS.md`.** The one caveat that does not change this verdict is
W-2: IPv4-mapped IPv6 literals remain unclassified, which is outside the requirement's enumerated
decimal/octal/hexadecimal IPv4 forms, is byte-identically pre-existing, and is bounded by the guard
being advisory (D-01).

No orphaned requirements: `REQUIREMENTS.md`'s traceability table maps only SEC-07 to phase 25, and all
three plans declare it.

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|---|---|---|---|---|
| — | — | — | — | None. Zero `TBD`/`FIXME`/`XXX`/`TODO`/`HACK`/`PLACEHOLDER` markers across all 15 non-`.planning` files changed by this phase. No empty implementations, no static returns standing in for real data. |

## Answers to the Specific Questions

### Q1 — SC1's two halves, verified separately in the codebase

**Deliberate choice, recorded: YES.** `DECISIONS.md` carries `## ADR-16`, and it is the highest-numbered
ADR. Clause 1 quotes the human's verbatim selection (`proof-of-possession`), states the concrete
mechanism, and names both rejected alternatives with the reason each was rejected — Option A because
every ordinary reload hitting a bind conflict would leave MCP down, Option B because any
pre-authentication signal only this extension emits re-creates the external-mode identification oracle
D-02 removed. The same selection appears verbatim in `25-01-SUMMARY.md` §"SC1 decision", and the
operator-facing consequence in `docs/mcp-hardening.md` item 2.

**No token materialised into an outbound header: YES.** In `McpSupervisor.kt`, `settings.token` appears
on exactly one line, and that line passes it as the HMAC **key** argument to
`McpTakeoverProof.forTarget`. The only `setRequestProperty` call in the file sets
`McpTakeoverProof.HEADER`. `probeExistingServer` sets no credential header at all. Both the structural
invariant and the behavioural outcome are pinned by tests, and the pre-phase line that violated it
(`Authorization: Bearer …`) is confirmed deleted by `git show dcd9cca`.

### Q2 — Does WR-01's unrecorded residual leave SC1's "recorded" half incomplete?

**No. It is a follow-up item, not a blocker.** Unhedged reasoning, in order of weight:

1. **What SC1 obliges is recording the replacement, and that obligation is discharged.** SC1 says the
   replacement "is chosen deliberately and recorded". ADR-16 records the choice, the mechanism, the
   rejected alternatives and six residuals. WR-01 does not identify a missing part of the *decision*;
   it identifies a *property of the chosen mechanism* discovered after the fact.
2. **The residual was newly discovered, not accepted-and-omitted.** T-25-17's rule — "a residual
   accepted but not written down is indistinguishable from one nobody noticed" — targets residuals the
   phase weighed and accepted. I checked the plans, the checkpoint script recorded in
   `25-01-SUMMARY.md`, and the summaries: nothing shows the offline-guessing property being weighed and
   then dropped from the ADR. 25-03's own truth is scoped to "every residual **this phase accepts**",
   and all six of those are present in the ADR section.
3. **It is not a regression, and its blast radius is narrow.** The pre-phase behaviour handed the
   plaintext token to any squatter at any entropy level; the new behaviour hands an offline verifier.
   That is strictly monotonically better. I confirmed the default token is 32 `SecureRandom` bytes
   (`McpSettings.generateToken`, used by `SettingsPanelInit` and `AgentSettings`), for which the attack
   is infeasible. I also confirmed WR-01's factual premise: the only MCP-token validation in the repo
   is `isBlank()` (`SettingsPanelMcpTabs.kt:580`), and there is no `MIN_TOKEN` constant anywhere in
   `main` except `Entropy.MIN_TOKEN_LEN`, which is unrelated redaction logic. So the exposure requires
   an operator-typed weak token **and** a local process squatting the MCP port.

**Recommended follow-up (not blocking):** add the seventh `Residual:` bullet to ADR-16 and raise
`MIN_ADR16_RESIDUALS` to `7` in the same commit, and put a floor (or at minimum a `RISK` notice) under
the token field so the mitigation the bullet claims is real rather than advisory.

### Q3 — The 25-01 deviation into `McpAccessControlDecision.kt` / `McpAccessControlPlugin.kt`

**Assessment: it does NOT change an auth approach, does NOT remove a control, and does NOT weaken the
access-control gate in any mode. Rule 2 was the correct classification.** Five checks, all against the
merged code:

1. **Local mode is untouched.** `evaluateLocal` does not read `takeoverProof` or `epochMillis`. Both new
   `RequestFacts` fields are defaulted, and `epochMillis` defaults to `0L`, which is fail-closed — a
   fact constructed without a clock can never match a live proof.
2. **Ordering preserves SEC-05 5c.** The new limb sits strictly **below** `settings.token.isBlank() ->
   Deny(BLANK_TOKEN)`, so a blank configured token still fails closed on every path first, including
   the shutdown path.
3. **The limb requires the same secret the bearer branch requires.** It allows only when
   `path == SHUTDOWN_PATH` **and** `McpTakeoverProof.accepts(settings.token, host, port, clock, proof)`
   — an HMAC keyed by the token and bound to host, port and a 10 s window. A caller without the token
   cannot produce one and falls through to the unchanged `!isAuthorizedBearer` branch, yielding the same
   401, the same `BlockReason.UNAUTHORIZED` and the same D-06 report.
4. **Defence in depth is intact.** The `POST /__mcp/shutdown` route re-checks `isAuthorized(...) ||
   McpTakeoverProof.accepts(...)` independently after the gate, so the gate is not the only control.
5. **No path-matching surface was widened.** The limb uses `call.request.path()` — the same source the
   pre-existing `facts.path == HEALTH_PATH -> Allow` limb already used, and that limb allows on the path
   *alone* with no credential. The new limb is strictly stronger than a construct that already shipped.

The only direction in which the proof differs from a bearer is replay: a captured proof is usable for
≤20 s, whereas a captured bearer is reusable indefinitely — so this is not a weakening either. That
residual is recorded (ADR-16, T-25-04). The deviation is also explicitly written into ADR-16's
Consequences, which is exactly where a future reader would look for it. One scope note, carried as W-3
rather than as a gap: every test proving external-mode takeover pins `host = "127.0.0.1"`, so the
external-mode claim is proven only for a loopback bind.

### Q4 — Is the flipped `DecisionsAdrTest` guard meaningful?

**Meaningful, but one bullet looser than it should be.**

- `adr16ExistsAndIsTheHighestNumberedAdr` — **non-vacuous**. It asserts a line starting `## ADR-16`
  exists (deleting the ADR turns it red) and that `## ADR-17` does not, which forces the next phase's
  author to claim a number deliberately. Both conditions hold in the shipped `DECISIONS.md`.
- `adr16RecordsEveryResidualItAccepts` — **binds, with slack**. `adrSection("## ADR-16")` slices from the
  heading to the next `\n## ADR` or, since ADR-16 is the last heading, to EOF. I counted independently:
  the slice contains exactly **6** `Residual:` lines and no foreign matches (heading-to-EOF count is
  also 6). The threshold is `MIN_ADR16_RESIDUALS = 5`. So the guard catches wholesale deletion and the
  loss of two or more bullets, but **not** the deletion of exactly one — the review's IN-02 is confirmed
  as measured fact, not as an inference.

This does not block SC1, which asks for recording rather than for a tight guard. Recommended fix:
`MIN_ADR16_RESIDUALS = 6`, or `7` if WR-01's bullet is added in the same commit.

### Q5 — SC4 vacuity analysis

**The proof is non-vacuous by construction, and I verified each link rather than reasoning from the
test's own comments.**

- The service file `src/test/resources/META-INF/services/java.net.spi.InetAddressResolverProvider`
  exists and contains exactly one line:
  `com.six2dez.burp.aiagent.util.CountingInetAddressResolverProvider`. That string matches the
  `package com.six2dez.burp.aiagent.util` + `class CountingInetAddressResolverProvider` declared in the
  Kotlin file, and the resource is copied to `build/resources/test/META-INF/services/`, i.e. it is on
  the test runtime classpath.
- **Deletion or misnaming produces a RED test, not a green one.** `theCountingResolverIsActuallyInstalled`
  (`@Order(1)`) resolves a freshly UUID-generated `*.invalid` name — cache-proof by novelty,
  network-free by RFC 6761 — and asserts `count() > 0`. If the service file were removed or misspelled,
  the provider would never install, the counter would never move, and that assertion would fail with a
  message naming exactly that cause. The corpus assertion's pass is therefore conditional on a separate,
  independently red-able installation proof. Both tests passed in this verifier's own run.
- The provider itself cannot distort results: both `lookupByName` and `lookupByAddress` delegate verbatim
  to `configuration.builtinResolver()` and only increment a counter.
- **Residual (IN-04), non-blocking:** the counter is process-global and the corpus test asserts an
  absolute `0`. I checked `build.gradle.kts` and `src/test/resources` — there is no
  `junit-platform.properties` and no `maxParallelForks`, so JUnit runs sequentially and cross-suite
  interference is not currently possible. If parallel execution is ever enabled, the failure mode is a
  false **RED**, not a vacuous green — the safe direction.

## Deferred Items

None. No later phase in this milestone covers the three warnings above; phase 26 is scoped to shell
escaping, coverage, the detekt baseline and the advisory. W-2 and W-3(a) are natural candidates for
phase 26 or a defect record, but neither is claimed there today, so neither is filtered as deferred.

## Gaps Summary

No gaps. All five ROADMAP success criteria are met, and each behaviour-dependent claim is backed by a
test this verifier executed rather than by a SUMMARY assertion: SC1 by structural + behavioural
assertions on the header block, SC2 by a real hostile `MockWebServer`, SC3 and SC4 by direct execution
of the compiled `SsrfGuard` plus a JVM-wide resolver counter with an anti-vacuity control, and SC5 by
real Netty TLS servers and a second real keystore.

Three warnings are recorded above. None falsifies a success criterion:

- **W-1 (WR-01)** — a genuine, newly discovered residual that ADR-16 should carry, and a token field
  that should have an entropy floor. Strictly better than the behaviour it replaced; requires an
  operator-typed weak token plus a port squatter to matter.
- **W-2 (WR-02)** — a surviving notation gap (IPv4-mapped IPv6) that this verifier reproduced against
  the compiled class, but which is byte-identically pre-existing, outside the criterion's enumerated
  forms, and bounded by the guard being advisory.
- **W-3 (WR-03 + IN-02)** — the pin's loopback host gate is unchanged from pre-phase, so external mode
  on a non-loopback host gets a diagnostic that contradicts the new runbook item 3; and the ADR-16
  residual guard is one bullet too loose to catch the deletion it exists to catch.

The security posture strictly improves in every configuration this phase touches, and no path was found
in which the MCP token or a blanket trust decision survives.

---

_Verified: 2026-08-22T11:06:10Z_
_Verifier: Claude (gsd-verifier)_
