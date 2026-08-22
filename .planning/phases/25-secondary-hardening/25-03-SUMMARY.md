---
phase: 25-secondary-hardening
plan: 03
subsystem: security
tags: [mcp, tls, certificate-pinning, x509, trust-manager, hostname-verifier, adr, kotlin, ktor, netty]

# Dependency graph
requires:
  - phase: 25-secondary-hardening (plan 25-01)
    provides: "McpTakeoverProof and requestRemoteShutdown — the takeover client whose transport this plan identifies, and the SC1 decision ADR-16 records"
  - phase: 20-mcp-access-control-correctness
    provides: "D-02's local-mode-only identity header and the Plugins-phase access-control gate, both of which ADR-16 must describe accurately"
provides:
  - "McpTls.pinnedLeafSha256 — a read-only SHA-256 pin of the leaf certificate at settings.tlsKeystorePath"
  - "McpSupervisor.openConnection scoped to exactly that certificate, with no trust-all fallback"
  - "Fail-closed loopback TLS takeover when no certificate can be named"
  - "ADR-16 — the shipped record of SEC-07's credential decision, header demotion, pin and six accepted residuals"
  - "docs/mcp-hardening.md §Takeover on a Bind Conflict — the operator-facing procedure"
affects: [mcp, supervisor, tls, release-notes, future-adr-authors]

actuals:
  tokens: 11738   # chars/4 over the realized diff (46,953 chars across 7 files, e6dc8a2..068fa1b)
  tasks: 3
  commits: 3

tech-stack:
  added: []
  patterns:
    - "Certificate pinning as identity REPLACEMENT, not verification disabling: the generated certificate is CN=burp-mcp and can never match a loopback name, so the identity assertion moves from the name to the key"
    - "Deliberate non-reuse: a client-side reader (pinnedLeafSha256) must not share an implementation with a server-side generator (resolve), and the KDoc says why"
    - "Fail-closed truth-table row as a regression guard: the one row that stays green under a trust-all fallback is the one worth writing"

key-files:
  created:
    - src/test/kotlin/com/six2dez/burp/aiagent/mcp/McpTakeoverCertificatePinTest.kt
  modified:
    - src/main/kotlin/com/six2dez/burp/aiagent/mcp/McpTls.kt
    - src/main/kotlin/com/six2dez/burp/aiagent/mcp/McpSupervisor.kt
    - src/test/kotlin/com/six2dez/burp/aiagent/mcp/McpSupervisorConnectionTest.kt
    - src/test/kotlin/com/six2dez/burp/aiagent/DecisionsAdrTest.kt
    - DECISIONS.md
    - docs/mcp-hardening.md

key-decisions:
  - "ROADMAP SC5 answered with its stronger option — scope the trust-all path to the certificate the extension generated — rather than documenting the residual, because 25-01's committed threat register already promises this mitigation in T-25-02 and T-25-03"
  - "Absent pin installs no TLS override at all. A pin-when-available, trust-everything-otherwise fallback would leave the original weakness reachable by deleting one file, with every pre-existing test still green (T-25-13)"
  - "pinnedLeafSha256 is deliberately NOT implemented in terms of McpTls.resolve: resolve auto-generates, which would write an unrequested file and mint a certificate guaranteed not to match the running server (T-25-14)"
  - "The hostname verifier is replaced by a key-identity check rather than disabled — CN=burp-mcp can never match localhost or 127.0.0.1, so name-based identity was never available on this path"
  - "DecisionsAdrTest was extended to guard ADR-16 rather than merely renumbered, because its own failure message demands the next author extend it"

patterns-established:
  - "ADR residual counting as an automated guard: DecisionsAdrTest.adr16RecordsEveryResidualItAccepts fails if a Residual: bullet is deleted by editing accident (T-25-17)"
  - "Attempt-bounded polling with no timing primitive at all: pacing comes from the HTTP round trip, so the file contains no Thread.sleep, currentTimeMillis or nanoTime"

requirements-completed: [SEC-07]

coverage:
  - id: D1
    description: "A legitimate TLS-mode takeover against the extension's own running MCP server still succeeds through the pin, so a bind conflict under TLS does not leave the MCP server permanently down"
    requirement: SEC-07
    verification:
      - kind: integration
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/mcp/McpTakeoverCertificatePinTest.kt#aLegitimateTlsTakeoverSucceedsAgainstThePinnedCertificate"
        status: pass
    human_judgment: false
  - id: D2
    description: "A squatter presenting any other self-signed certificate is refused at the handshake, and the freshly-bound server it was squatting on is still running afterwards"
    requirement: SEC-07
    verification:
      - kind: integration
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/mcp/McpTakeoverCertificatePinTest.kt#aForeignCertificateIsRefusedAndTheServerSurvives"
        status: pass
    human_judgment: false
  - id: D3
    description: "With no pin computable from settings.tlsKeystorePath the client installs no TLS override at all and the takeover fails closed, and computing the pin creates no key material"
    requirement: SEC-07
    verification:
      - kind: integration
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/mcp/McpTakeoverCertificatePinTest.kt#noPinAvailableInstallsNoOverrideAndFailsClosed"
        status: pass
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/mcp/McpSupervisorConnectionTest.kt#openConnection_loopbackTlsWithoutAPin_installsNoOverrideAtAll"
        status: pass
    human_judgment: false
  - id: D4
    description: "The blanket trust is gone from McpSupervisor: no unconditional-true hostname verifier and no blanket-accept trust manager survive, and the four-row truth table of the loopback TLS branch holds"
    requirement: SEC-07
    verification:
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/mcp/McpSupervisorConnectionTest.kt — 4 tests, full truth table"
        status: pass
      - kind: other
        ref: "grep -vE '^\\s*(\\*|//|/\\*)' src/main/kotlin/com/six2dez/burp/aiagent/mcp/McpSupervisor.kt | grep -cF '_, _ -> true' => 0"
        status: pass
    human_judgment: false
  - id: D5
    description: "ADR-16 records the SC1 credential decision, the identity-header demotion, the SC5 pin and every residual this phase accepts, and docs/mcp-hardening.md tells an operator what they will see and do"
    requirement: SEC-07
    verification:
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/DecisionsAdrTest.kt#adr16ExistsAndIsTheHighestNumberedAdr, #adr16RecordsEveryResidualItAccepts"
        status: pass
    human_judgment: true
    rationale: "The automated guard can only count headings and Residual: bullets. Whether ADR-16 is factually accurate — whether its threat description matches shipped behaviour and whether the runbook section is genuinely usable by an operator at a bind conflict — is judgement, exactly as ADR-15's own guard says of itself."

duration: 21 min
completed: 2026-08-22
status: complete
---

# Phase 25 Plan 03: Loopback TLS Certificate Pin and ADR-16 Summary

**The MCP takeover client now completes a loopback TLS handshake only against the leaf certificate in the keystore its own settings name — proven to refuse a real second `keytool`-minted certificate and to fail closed rather than trust anything when no certificate can be named — and ADR-16 ships the whole of SEC-07's reasoning, including six accepted residuals, outside `.planning/`.**

## Performance

- **Duration:** 21 min
- **Started:** 2026-08-22T10:14:27Z
- **Completed:** 2026-08-22T10:35:22Z
- **Tasks:** 3
- **Files modified:** 7 (1 created, 6 modified)

## Accomplishments

- **`McpTls.pinnedLeafSha256`** — reads the leaf certificate at `settings.tlsKeystorePath` and returns its SHA-256 digest, or null. It never generates, never writes and never creates a directory, and its KDoc records why it is deliberately not implemented in terms of `resolve` (which auto-generates, and would therefore mint a certificate that by construction cannot match the running server's).
- **The blanket trust is gone.** `McpSupervisor.openConnection` no longer installs an `X509TrustManager` whose `checkServerTrusted` body is `= Unit` nor a `HostnameVerifier` returning true unconditionally. It installs an `SSLContext` whose only trust manager accepts a chain if and only if its leaf digests to the pin, compared with `MessageDigest.isEqual`, plus a hostname verifier that asserts the same key identity. `checkClientTrusted` throws rather than returning `Unit`, so reusing the object server-side would fail loudly.
- **Absent pin means closed, not open.** No pin, no TLS override at all — the JDK defaults refuse the self-signed listener and one Output line names the keystore path. The signature changed from `openConnection(url, tlsEnabled)` to `openConnection(url, settings)` to make the keystore reachable from the branch.
- **The pin is proven to discriminate against a real certificate**, not a mock: `aForeignCertificateIsRefusedAndTheServerSurvives` mints a second PKCS12 through the same `keytool` path and asserts both that the takeover is refused and — independently of the return value — that the server it was aimed at still answers `/__mcp/health`.
- **`pinnedLeafSha256` never generates, asserted behaviourally.** After a failed pin attempt the non-existent keystore path is still non-existent.
- **ADR-16 and the operator runbook.** ADR-16 carries the SC1 decision in the developer's own word, the demotion of `X-Burp-AI-Agent: mcp` from control to hint, the SC5 pin with its fail-closed classification, the external-mode gate discovery from 25-01, and six `Residual:` bullets. `docs/mcp-hardening.md` gained `## Takeover on a Bind Conflict` between `## Operational Controls` and `## Credential Storage`.

## Task Commits

1. **Task 1 (tracer): Pin the loopback certificate, wired end to end on one path** — `2fdcbc1` (feat)
2. **Task 2: Prove the pin discriminates, and that absent means closed (SC5)** — `44f93d1` (test)
3. **Task 3: Record the phase — ADR-16 and the operator runbook** — `068fa1b` (docs)

**Plan metadata:** see the `docs(25-03)` commit that carries this file.

## Red-Probe Record (per test)

The plan predicted the two new pin tests would only achieve the WEAK form (failing to compile against the pre-Task-1 tree). They achieved the STRONG form instead, because neither references `McpTls.pinnedLeafSha256` directly — they drive `requestRemoteShutdown` by reflection, which compiles against either implementation.

**Revert command used (never `git stash` — `refs/stash` is shared across linked worktrees):**

```
git checkout HEAD~1 -- src/main/kotlin/com/six2dez/burp/aiagent/mcp/McpSupervisor.kt
git checkout HEAD  -- src/main/kotlin/com/six2dez/burp/aiagent/mcp/McpSupervisor.kt   # restore
```

| Test | Form | Evidence |
|---|---|---|
| `aForeignCertificateIsRefusedAndTheServerSurvives` | **STRONG** | Compiles against the reverted trust-all `McpSupervisor` and fails on its own `AssertionFailedError` — the trust-all client accepts the foreign certificate and the shutdown succeeds. |
| `noPinAvailableInstallsNoOverrideAndFailsClosed` | **STRONG** | Same revert, same form: fails on its own `AssertionFailedError`, because trust-all never reads the keystore and the takeover succeeds regardless of the missing file. |
| `aLegitimateTlsTakeoverSucceedsAgainstThePinnedCertificate` (tracer) | **No red form available, and correctly so** | It passes under trust-all too. It is a reliability guard against the fail-closed branch overreaching, not a security assertion, and a red form would mean the feature was already broken. |
| `openConnection_loopbackTlsWithoutAPin_installsNoOverrideAtAll` | **WEAK under the plan's literal recipe, STRONG under a second probe** | Under `git checkout HEAD~1 -- McpSupervisor.kt` all four connection tests fail with `NoSuchMethodException: openConnection(java.net.URL,com.six2dez.burp.aiagent.config.McpSettings)` — the reverted signature is unreachable by reflection, so the assertion never evaluates. That is the WEAK form and it is reported as such. A second probe was therefore run: the T-25-13 trust-all fallback was reintroduced **behind the new signature** (`conn.sslSocketFactory = pinnedSslContext(ByteArray(0)).socketFactory; conn.hostnameVerifier = HostnameVerifier { _, _ -> true }` in the `pin == null` branch). The fail-closed row then failed alone — `expected: <null> but was: <sun.security.ssl.SSLSocketFactoryImpl@ec67be1>` — while the other three rows stayed green, which is exactly the regression this row exists to catch. |

## A-25-11: the JDK client against a Netty TLS connector

**The assumption held. The named `SSLSocket` fallback was NOT used.** A JDK `HttpsURLConnection` offering no ALPN protocols negotiated `http/1.1` normally against the Netty `sslConnector` (which advertises `h2` first), and all three pin tests drive the full HTTP exchange against the real server. There is no handshake failure to report.

## Files Created/Modified

- `src/main/kotlin/com/six2dez/burp/aiagent/mcp/McpTls.kt` — added `pinnedLeafSha256`; `resolve` and `generateSelfSigned` untouched.
- `src/main/kotlin/com/six2dez/burp/aiagent/mcp/McpSupervisor.kt` — `openConnection` re-signed and re-implemented around the pin; new private `isLoopbackUrlHost`, `pinnedSslContext`, `leafDigestMatches`.
- `src/test/kotlin/com/six2dez/burp/aiagent/mcp/McpTakeoverCertificatePinTest.kt` — new, 3 tests against a real Netty TLS server.
- `src/test/kotlin/com/six2dez/burp/aiagent/mcp/McpSupervisorConnectionTest.kt` — migrated to the new signature, extended from 3 to 4 tests.
- `src/test/kotlin/com/six2dez/burp/aiagent/DecisionsAdrTest.kt` — the ADR guard extended to ADR-16 (see deviation 1).
- `DECISIONS.md` — ADR-16 appended.
- `docs/mcp-hardening.md` — `## Takeover on a Bind Conflict` added between `## Operational Controls` and `## Credential Storage`.

## Decisions Made

- **SC5 answered with its stronger option.** `<sc5_option_selection>` in the plan gives three checkable reasons; the decisive one is that 25-01's committed threat register already states, in T-25-02 and T-25-03, that "Real listener identity is additionally restored for TLS configurations by plan 25-03's certificate pin". Shipping documentation instead would have left two mitigations in a committed threat model describing something that does not exist.
- **`checkClientTrusted` throws instead of returning `Unit`.** The plan asked for this and the reason is worth keeping: this is a client-side manager and is never called, so `= Unit` is invisible today and a footgun the day someone reuses the object on a server.
- **ADR-16's SC1 clause was copied word for word, not paraphrased.** Verified by comparing the two texts directly. The developer's selection is the single token `proof-of-possession` (**Option C**), reproduced as a block quote, followed by 25-01-SUMMARY's own sentence: "That is Option C — the bind-conflict takeover client presents `HMAC-SHA256(key = token, message = "burp-ai-agent/mcp-takeover|v1|<host>:<port>|<10s window>")` instead of the MCP bearer token." Nothing beyond the selection is attributed to the developer, matching 25-01-SUMMARY's own note that they added no rationale of their own.
- **ADR-16 records the external-mode access-control gate discovery** from 25-01 (the `Plugins`-phase gate 401'ing a proof-only shutdown before routing), per the wave-1 handoff. A future reader would otherwise have no way to know the gate is part of the takeover credential path.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 2 - Missing Critical] `DecisionsAdrTest` extended to guard ADR-16**
- **Found during:** Task 3 (ADR-16)
- **Issue:** `DecisionsAdrTest.adr15ExistsAndIsTheHighestNumberedAdr` asserts that `## ADR-16` does NOT exist, with the message "This assertion exists to make the NEXT phase's author notice they must claim a new number and extend this guard, rather than quietly reusing one." Appending ADR-16 turned the full suite red. The plan did not list this file, but the guard's own failure message is an instruction to the author who trips it.
- **Fix:** Renamed to `adr16ExistsAndIsTheHighestNumberedAdr`; it now asserts ADR-15 still exists, ADR-16 exists, and ADR-17 does not. Added `adr16RecordsEveryResidualItAccepts`, which fails if ADR-16 carries fewer than five `Residual:` bullets — the automated half of T-25-17 (a residual accepted but not written down is indistinguishable from one nobody noticed). The ADR-15 slicer was generalised to `adrSection(heading)`; all four pre-existing ADR-15 assertions are unchanged in meaning.
- **Files modified:** `src/test/kotlin/com/six2dez/burp/aiagent/DecisionsAdrTest.kt`
- **Verification:** Full suite green (880 tests / 127 classes). The residual guard was sanity-checked against the shipped ADR: 6 bullets, bound 5.
- **Committed in:** `068fa1b` (Task 3 commit)

**2. [Rule 3 - Blocking] `McpSupervisorConnectionTest` migrated in Task 1 rather than Task 2**
- **Found during:** Task 1
- **Issue:** The plan assigns the `openConnection` signature migration to Task 2, but the Kotlin test source set compiles as a unit, so Task 1's own `<verify>` command could not run while the old reflection lookup named `Boolean::class.javaPrimitiveType`.
- **Fix:** The signature migration (and the `@BeforeAll` temp keystore the loopback-TLS row now needs) moved into Task 1. Task 2 kept what carries the meaning: the fourth, fail-closed row and its red probe.
- **Files modified:** `src/test/kotlin/com/six2dez/burp/aiagent/mcp/McpSupervisorConnectionTest.kt`
- **Verification:** Task 1's `<verify>` command ran green with the connection test included.
- **Committed in:** `2fdcbc1` (Task 1 commit)

**3. [Rule 1 - Bug] `McpSupervisorConnectionTest` used a bare `mock<MontoyaApi>()`**
- **Found during:** Task 2
- **Issue:** The new fail-closed row logs one Output line through `api.logging().logToOutput`, and a bare Mockito mock returns null from `api.logging()`, so the test failed with an NPE inside `openConnection` rather than on its assertion.
- **Fix:** Switched the supervisor under test to `McpTestServerSupport.deepStubApi()`, the in-repo deep-stub helper the other MCP tests already use, with a comment saying why. This is the correct fixture, not a workaround: the branch under test is expected to log.
- **Files modified:** `src/test/kotlin/com/six2dez/burp/aiagent/mcp/McpSupervisorConnectionTest.kt`
- **Verification:** All 4 connection tests green; the probe-B run confirms the assertion, not the fixture, is what fails when the implementation regresses.
- **Committed in:** `44f93d1` (Task 2 commit)

**4. [Rule 3 - Blocking] Two plan citations did not resolve against the tree**
- **Found during:** Tasks 1 and 2
- **Issue:** (a) The plan names `McpTestServerSupport.tlsClient()`; the helper is actually called `trustAllClient()`. (b) Task 2's acceptance criterion `grep -c 'burp-ai-agent/certs' <both test files>` must return `0`, but the in-repo convention — inherited from `McpTestServerSupport.kt`'s own KDoc — is to warn future editors against that exact path by naming it, and the criterion does not filter comment lines the way the `_, _ -> true` criterion deliberately does.
- **Fix:** (a) Used `trustAllClient()`. (b) Kept the warnings but reworded them to describe the path ("the extension's real certificate directory under the user's home") instead of reproducing the literal string, so the gate becomes meaningful rather than self-invalidating: any remaining occurrence would now be a genuine reference.
- **Files modified:** `src/test/kotlin/com/six2dez/burp/aiagent/mcp/McpTakeoverCertificatePinTest.kt`, `src/test/kotlin/com/six2dez/burp/aiagent/mcp/McpSupervisorConnectionTest.kt`
- **Verification:** `grep -c 'burp-ai-agent/certs'` returns `0` for both files; the warnings are intact.
- **Committed in:** `2fdcbc1` and `44f93d1`

**5. [Rule 3 - Blocking] Two ktlint violations in the first draft**
- **Found during:** Task 1
- **Issue:** `standard:chain-method-continuation` on `pinnedLeafSha256`'s `trim().takeIf { }?.let(::File)` chain, and `First line of body expression fits on same line as function signature` in the connection test.
- **Fix:** Rewrote the keystore-file resolution as two plain statements (which also reads closer to `resolve`'s own opening lines) and collapsed the one-line body expression.
- **Files modified:** `src/main/kotlin/com/six2dez/burp/aiagent/mcp/McpTls.kt`, `src/test/kotlin/com/six2dez/burp/aiagent/mcp/McpSupervisorConnectionTest.kt`
- **Verification:** `./gradlew detekt ktlintCheck` green.
- **Committed in:** `2fdcbc1` (Task 1 commit)

---

**Total deviations:** 5 auto-fixed (1 missing critical, 1 bug, 3 blocking)
**Impact on plan:** No scope creep. Deviation 1 is the only one that touched a file outside `files_modified`, and it was demanded in writing by the failing assertion itself. Nothing was added to `detekt-baseline.xml` and `build.gradle.kts` is byte-identical.

## Verification Results

| Check | Result |
|---|---|
| `./gradlew test` (full suite, JDK 21) | **PASS** — 880 tests, 127 classes, 0 failures, 0 errors, 1 skipped |
| `./gradlew detekt ktlintCheck` | **PASS** |
| `git diff --stat detekt-baseline.xml` | empty |
| `git diff build.gradle.kts` | empty |
| No unconditional-true hostname verifier (comment lines filtered) | `0` |
| `pinnedLeafSha256` referenced in `McpSupervisor.kt` | `1` |
| `isEqual` referenced in `McpSupervisor.kt` | `2` |
| `generateSelfSigned` in `McpTls.kt` (comment lines filtered) | `2` — declaration plus the single existing call from `resolve`; no third |
| No timing primitive in the pin test (comment lines filtered) | `0` |
| No real-keystore reference in either test file | `0` and `0` |
| `## ADR-16` headings in `DECISIONS.md` | `1` |
| `Residual:` bullets under ADR-16 | `6` (bound: at least 5) |
| `**Context.** / **Decision.** / **Consequences.**` count in `DECISIONS.md` | 45 → 48 (exactly +3) |
| No `\.kt:[0-9]` citation introduced in ADR-16 | none |
| Runbook heading order | Baseline, External Access, Operational Controls, **Takeover on a Bind Conflict**, Credential Storage, Verification, Incident Response |

The one skipped test, `ExternalMcpClientManagerTest.connectAndListTools_returnsExpectedCount`, is pre-existing (last touched in `2dba51c`, phase 16) and unrelated to this plan.

## Issues Encountered

None beyond the deviations above. In particular, the A-25-11 fallback was never needed and no wall-clock flake was observed across four full-suite runs.

## User Setup Required

None — no external service configuration required.

## Next Phase Readiness

- **SEC-07's three success criteria are closed across the phase**: SC1/SC2 (proof of possession, plan 25-01), SC3/SC4 (SSRF notation parsing, plan 25-02), SC5 (certificate pin, this plan), with all reasoning now shipped in `DECISIONS.md` ADR-16 and `docs/mcp-hardening.md` rather than living in `.planning/`.
- **`SEC-07` is deliberately left unticked in `REQUIREMENTS.md`** — it is declared by all three plans in this phase, so the shared-ID gate holds it until every declaring plan has a SUMMARY. The orchestrator marks it centrally after this plan merges.
- **One item for human review**, recorded as coverage D5 with `human_judgment: true`: whether ADR-16 is factually accurate and whether the runbook section is genuinely usable by an operator standing in front of a bind conflict. The automated guard can only count headings and bullets, exactly as ADR-15's own guard says of itself.
- **A note for whoever writes ADR-17:** `DecisionsAdrTest.adr16ExistsAndIsTheHighestNumberedAdr` will go red the moment you append it. That is deliberate — extend the guard, do not delete it.

---
*Phase: 25-secondary-hardening*
*Completed: 2026-08-22*
