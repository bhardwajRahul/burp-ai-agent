---
phase: 25-secondary-hardening
plan: 01
subsystem: mcp
tags: [mcp, hmac, proof-of-possession, access-control, ktor, takeover, sec-07]

requires:
  - phase: 20-mcp-access-control
    provides: "McpAccessControl gate, McpAccessControlDecision pure core, constantTimeCompare, isAuthorizedBearer, the D-01 health exemption and the D-02 identity-header withholding this plan reasons against"
  - phase: 22-mcp-supervision
    provides: "McpSupervisor bind-conflict takeover path (probeExistingServer, attemptTakeover, requestRemoteShutdownWithToken) and the comment that deferred real listener identity to this phase"
  - phase: 24-reliability
    provides: "tasks.test inputs.dir(\"src/main/kotlin\"), which makes source-text structural assertions cache-correct"
provides:
  - "McpTakeoverProof — HMAC-SHA256 proof of possession keyed by the MCP token, bound to host, port and a 10s window"
  - "A takeover client that never materialises the MCP token into an outbound header value"
  - "Server-side acceptance of the proof on POST /__mcp/shutdown, in local AND external mode"
  - "Live proof that a port squatter spoofing the identity header receives no token"
affects: [25-03 certificate pinning and ADR-16, any future change to the MCP takeover wire protocol]

actuals:
  tokens: 11663
  tasks: 3
  commits: 5

tech-stack:
  added: []
  patterns:
    - "Proof of possession instead of credential disclosure when the peer's identity cannot be established"
    - "Injected clock (RequestFacts.epochMillis) to keep a time-dependent authorization decision pure"
    - "Structural source-text invariant asserted from a test to pin a security property no behavioural test can fully cover"

key-files:
  created:
    - src/main/kotlin/com/six2dez/burp/aiagent/mcp/McpTakeoverProof.kt
    - src/test/kotlin/com/six2dez/burp/aiagent/mcp/McpTakeoverProofTest.kt
    - src/test/kotlin/com/six2dez/burp/aiagent/mcp/McpTakeoverPipelineTest.kt
    - src/test/kotlin/com/six2dez/burp/aiagent/mcp/McpTakeoverSquatterTest.kt
  modified:
    - src/main/kotlin/com/six2dez/burp/aiagent/mcp/McpSupervisor.kt
    - src/main/kotlin/com/six2dez/burp/aiagent/mcp/KtorMcpServerManager.kt
    - src/main/kotlin/com/six2dez/burp/aiagent/mcp/McpAccessControlDecision.kt
    - src/main/kotlin/com/six2dez/burp/aiagent/mcp/McpAccessControlPlugin.kt

key-decisions:
  - "SC1: proof-of-possession (Option C), selected by the developer in a blocking human checkpoint with the T-25-04 replay residual stated first"
  - "WINDOW_MS stays file-private in McpTakeoverProof.kt rather than moving to config/Defaults.kt — it is one protocol's internal parameter and must change in both halves at once or not at all"
  - "The server keeps accepting the bearer form on POST /__mcp/shutdown; only the CLIENT is migrated, so an operator driving the endpoint by hand is unaffected"
  - "The access-control gate had to learn the proof form too, or external-mode takeover would have broken silently (deviation, Rule 2)"

patterns-established:
  - "Stop having a secret to leak rather than trying to identify the listener first, when identity is not establishable on a loopback port"
  - "Window-aligned base instants in tests (1_700_000_000_000) instead of System.currentTimeMillis(), so window-boundary assertions are deterministic"
  - "Attempt-bounded polling instead of fixed sleeps or wall-clock thresholds, given this repo's recorded wall-clock flake class"

requirements-completed: []  # SEC-07 is shared with plans 25-02 and 25-03 and is NOT yet fully satisfied. See "Requirements" below.

coverage:
  - id: D1
    description: "McpTakeoverProof mints and validates a window-bound HMAC proof: current window, one-window fallback, rejection beyond it, blank-token guard, host/port binding, host normalisation"
    requirement: SEC-07
    verification:
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/mcp/McpTakeoverProofTest.kt (8 tests)"
        status: pass
    human_judgment: false
  - id: D2
    description: "The bind-conflict takeover client presents the proof and never the bearer token; a blank token fails closed before any request is issued"
    requirement: SEC-07
    verification:
      - kind: integration
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/mcp/McpTakeoverSquatterTest.kt#aLocalSquatterThatSpoofsTheIdentityHeaderNeverReceivesTheToken"
        status: pass
      - kind: integration
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/mcp/McpTakeoverSquatterTest.kt#anExternalModeSquatterAlsoReceivesNoToken"
        status: pass
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/mcp/McpTakeoverSquatterTest.kt#theTakeoverClientHoldsTheTokenOnlyAsAnHmacKey"
        status: pass
      - kind: integration
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/mcp/McpTakeoverPipelineTest.kt#aBlankTokenMakesNoRequestAtAll"
        status: pass
    human_judgment: false
  - id: D3
    description: "A legitimate bind-conflict takeover against our own running MCP server still succeeds and the server actually stops — local mode and external TLS mode"
    requirement: SEC-07
    verification:
      - kind: integration
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/mcp/McpTakeoverPipelineTest.kt#theProofCredentialTakesOverOurOwnRunningServer"
        status: pass
      - kind: integration
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/mcp/McpTakeoverPipelineTest.kt#theProofCredentialAlsoTakesOverOurOwnExternalModeServer"
        status: pass
    human_judgment: false
  - id: D4
    description: "Recognising the proof did not open the shutdown route: an absent or forged proof is still refused with 401 in external mode"
    requirement: SEC-07
    verification:
      - kind: integration
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/mcp/McpTakeoverPipelineTest.kt#anExternalModeShutdownWithNoCredentialIsStillRejected"
        status: pass
      - kind: integration
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/mcp/McpTakeoverSquatterTest.kt#aListenerThatRejectsTheProofYieldsShutdownRejected"
        status: pass
    human_judgment: false
  - id: D5
    description: "The one-way wire-protocol decision (SC1 Option C) and its accepted replay residual T-25-04 are the right trade for this product"
    verification: []
    human_judgment: true
    rationale: "A one-way protocol contract between independently-installed versions, chosen with an explicitly accepted denial-of-service residual. Automation can prove the mechanism works; only a human can ratify that the residual is acceptable, and that ratification is recorded below rather than re-derived."

duration: 22min
completed: 2026-08-22
status: complete
---

# Phase 25 Plan 01: Takeover Credential Hardening Summary

**The MCP bind-conflict takeover now presents an HMAC-SHA256 proof of possession keyed by the bearer token instead of the token itself, so a local process squatting the MCP port receives nothing it can reuse — proven against a real fake listener in local and external mode.**

## Performance

- **Duration:** 22 min
- **Started:** 2026-08-22T09:40:26Z
- **Completed:** 2026-08-22T10:02:02Z
- **Tasks:** 3
- **Files modified:** 8 (4 created, 4 modified)

## SC1 decision

**Developer selection, verbatim:**

```
proof-of-possession
```

That is **Option C** — the bind-conflict takeover client presents
`HMAC-SHA256(key = token, message = "burp-ai-agent/mcp-takeover|v1|<host>:<port>|<10s window>")`
instead of the MCP bearer token.

**How the answer was obtained.** The orchestrator presented the full Task 1 decision script — the
precise statement of the problem, Option A (drop automatic takeover), Option B (server-supplied
challenge/response, rejected on analysis because any pre-auth signal only a Burp AI Agent emits
re-creates the external-mode identification oracle Phase 20's D-02 removed at
`KtorMcpServerManager.kt:200-205`), and Option C with its recommendation — to the human developer in
a blocking prompt. The checkpoint was **not** auto-approved: `.planning/config.json` carries
`mode: yolo`, which is known in this repo to auto-select blocking checkpoints, and that behaviour was
deliberately not relied on. A human answered.

**Accepted residual, stated BEFORE the selection was taken.** T-25-04 (Denial of Service,
disposition `accept`) was read out with the recommendation and before the answer was given: the
squatting process *does* receive the proof, and can replay it within its 10-second window — plus one
fallback window, so **20 seconds worst case** — to shut down the freshly-bound MCP server. That is a
denial of service by a process which is already denying the service by holding the port. Tightening
to a single-use server-side nonce cache was rejected as disproportionate.

**Developer rationale.** None beyond the selection itself. The developer chose the recommended
option and added no words of their own; nothing further is attributed to them here.

**Reversibility.** Rated `one-way` in the plan and accepted as such. The credential presented on
`POST /__mcp/shutdown` is a wire-protocol contract between two independently-installed versions of
this extension, with no negotiation step to fall back on.

## Accomplishments

- **`McpTakeoverProof`** — a new `object` with `HEADER`, `forTarget` and `accepts`. HMAC-SHA256 keyed
  by the MCP token over `burp-ai-agent/mcp-takeover|v1|<host>:<port>|<window>`, Base64-URL without
  padding (the encoder shape `McpSettings.generateToken` already uses). `WINDOW_MS = 10_000L` is
  file-private; nothing was added to `config/Defaults.kt`.
- **The token left the wire.** `McpSupervisor.requestRemoteShutdownWithToken` is renamed
  `requestRemoteShutdown` (the old name had become a lie), the `Authorization` request property is
  deleted, and a blank token now fails closed *before* a connection is opened rather than presenting a
  bare `Bearer `.
- **Server accepts both forms.** `POST /__mcp/shutdown` still honours the bearer credential unchanged
  — an operator driving the endpoint by hand is unaffected — and additionally accepts the proof.
- **`probeExistingServer` is untouched**, exactly as the Phase-22-era comment at
  `McpSupervisor.kt:265-277` requires. The comment gained one point recording that the identity
  signals it inspects are no longer load-bearing for credential disclosure and now serve only as a
  cheap filter.
- **SC2 proven against a real hostile listener.** A `MockWebServer` squatter that spoofs
  `X-Burp-AI-Agent: mcp` receives no token in local mode or in external mode.

## Task Commits

1. **Task 1: Decide and record the SC1 takeover-credential architecture** — `fbfb35c` (docs)
2. **Task 2 (RED): failing tests for the proof credential** — `aee704d` (test)
3. **Task 2 (GREEN): proof-of-possession wired end to end** — `38ceadf` (feat)
4. **Task 3: fake-listener test, the squatter receives no token** — `65ad70f` (test)
5. **Deviation fix: access-control gate learns the proof credential** — `53595d0` (fix)

_Task 2 carried `tdd="true"`, hence its test → feat pair. No REFACTOR commit was needed._

## Red-probe form achieved, per test

The plan required this to be reported honestly rather than claimed as a gate.

### Task 2 — `McpTakeoverProofTest`, `McpTakeoverPipelineTest`

| Test | Form | Evidence |
|---|---|---|
| all 8 `McpTakeoverProofTest` tests | **WEAK** | `McpTakeoverProof` did not exist, so `:compileTestKotlin` failed with `Unresolved reference 'McpTakeoverProof'`. Unavoidable for the first test of a new unit — there is no tree in which these compile and fail. |
| `McpTakeoverPipelineTest` (both original tests) | **WEAK** | Same compile failure. The reflection call itself compiles, but the file does not. |

### Task 3 — `McpTakeoverSquatterTest`

The STRONG form **was** available here and was taken. Revert command used, exactly as the plan
prescribes (never `git stash` — `refs/stash` is shared across linked worktrees):

```
git checkout HEAD~1 -- src/main/kotlin/com/six2dez/burp/aiagent/mcp/McpSupervisor.kt
```

That leaves the file compiling against the new server and the new proof object while restoring the old
credential. All four tests then **compiled and ran and failed on their assertions**:

| Test | Form | Failure observed under the revert |
|---|---|---|
| `aLocalSquatterThatSpoofsTheIdentityHeaderNeverReceivesTheToken` | **STRONG** | `T-25-01: the squatter must receive no Authorization header at all ==> expected: <null> but was: <Bearer squatter-must-never-see-this>` |
| `anExternalModeSquatterAlsoReceivesNoToken` | **STRONG** | Same failure — Finding 7 reproduced live in external mode |
| `aListenerThatRejectsTheProofYieldsShutdownRejected` | **STRONG** | Same failure. Note the *outcome-enum* half of this test is not red-capable (an old bearer client also gets 401 → `SHUTDOWN_REJECTED`); it is the shared `assertNoTokenReached` block that goes red. Reported precisely because the plan's Task 3 text predicted only a WEAK form here. |
| `theTakeoverClientHoldsTheTokenOnlyAsAnHmacKey` | **STRONG** | ``The one line reading the token must be the HMAC key argument, not a header value. Found: `conn.setRequestProperty("Authorization", "Bearer ${settings.token}")` `` |

`McpSupervisor.kt` was restored with `git checkout HEAD -- <path>` and the working tree confirmed
clean (`git status --short` and `git diff --stat` both empty for that file) before Task 3 was
committed.

### Deviation fix — `McpTakeoverPipelineTest` external-mode tests

**STRONG.** `theProofCredentialAlsoTakesOverOurOwnExternalModeServer` was written and run *before* the
gate change and failed on its assertion (`AssertionFailedError at McpTakeoverPipelineTest.kt:108`)
against a real TLS server, which is what turned a code reading into a proven regression.

## Files Created/Modified

- `src/main/kotlin/com/six2dez/burp/aiagent/mcp/McpTakeoverProof.kt` — new. The credential: `HEADER`,
  `forTarget`, `accepts`, file-private `WINDOW_MS`. Reuses `constantTimeCompare` from
  `McpAccessControlDecision.kt` rather than hand-rolling a comparison loop.
- `src/main/kotlin/com/six2dez/burp/aiagent/mcp/McpSupervisor.kt` — `requestRemoteShutdown` (renamed),
  `Authorization` deleted, new private `takeoverProof` helper, probe comment extended.
- `src/main/kotlin/com/six2dez/burp/aiagent/mcp/KtorMcpServerManager.kt` — the shutdown route accepts a
  second credential form and now names its path via `SHUTDOWN_PATH`.
- `src/main/kotlin/com/six2dez/burp/aiagent/mcp/McpAccessControlDecision.kt` — **deviation**:
  `SHUTDOWN_PATH` constant, two defaulted `RequestFacts` fields, one new `evaluateExternal` limb.
- `src/main/kotlin/com/six2dez/burp/aiagent/mcp/McpAccessControlPlugin.kt` — **deviation**: populates
  the two new facts.
- `src/test/kotlin/com/six2dez/burp/aiagent/mcp/McpTakeoverProofTest.kt` — new, 8 tests.
- `src/test/kotlin/com/six2dez/burp/aiagent/mcp/McpTakeoverPipelineTest.kt` — new, 4 tests.
- `src/test/kotlin/com/six2dez/burp/aiagent/mcp/McpTakeoverSquatterTest.kt` — new, 4 tests.

## Decisions Made

- **`accepts` evaluates both windows without short-circuiting the comparison itself.** Which of two
  equally-valid windows matched is not a secret, so `||` over two `constantTimeCompare` calls is
  correct; the comparison of each candidate is still constant-time.
- **The proof header is `X-Mcp-Takeover-Proof`, deliberately not `Authorization`.** It is not a bearer
  secret and should not be handled, logged or proxied as one.
- **`SHUTDOWN_PATH` was promoted to a named constant** once two files had to agree on it. `HEALTH_PATH`
  already existed for exactly this reason.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 2 - Missing Critical] The access-control gate 401'd the proof before the shutdown route could accept it, breaking external-mode takeover**

- **Found during:** Task 3, while reading `McpAccessControlDecision.kt` to confirm the squatter test's
  external-mode assumptions.
- **Issue:** The plan's server-side change is route-level only. But `McpAccessControl` runs in the
  Ktor `Plugins` phase — **before routing** — and in external mode `evaluateExternal` denies every
  non-`/__mcp/health` path that does not carry a valid bearer. Deleting the `Authorization` header from
  the client therefore meant a proof-only shutdown request was refused by the gate and never reached
  the route. Net effect: external-mode bind-conflict takeover would have stopped working entirely, and
  the MCP server would have stayed down after every external-mode reload — in the one configuration
  where an operator is least likely to notice. This directly contradicted the plan's own must-have
  truth ("a legitimate bind-conflict takeover … still succeeds") and made the plan's own instruction
  ("Respond 401 only when both forms fail") unachievable as written.
- **Proven, not assumed:** `theProofCredentialAlsoTakesOverOurOwnExternalModeServer` was written first
  and failed against a real TLS server before any gate change.
- **Fix:** `RequestFacts` gained `takeoverProof: String? = null` and `epochMillis: Long = 0L`, both
  defaulted so every existing construction site is unaffected. `requestFacts(call)` — the file's
  already-impure adapter — fills them, which keeps `evaluate` pure by contract; the clock is injected
  rather than read inside the decision. `evaluateExternal` gained one limb allowing `SHUTDOWN_PATH`
  when the proof validates, placed **below** the SEC-05 5c blank-token guard so that still fails closed
  first.
- **Why this opens nothing:** the proof is an HMAC keyed by the same token the bearer branch checks, so
  a caller who does not hold the token cannot produce one and falls through to the identical 401, with
  the identical `BlockReason` and the identical D-06 blocked-request report. Asserted by
  `anExternalModeShutdownWithNoCredentialIsStillRejected`, which drives both an absent proof and a
  forged one.
- **Files modified:** `McpAccessControlDecision.kt`, `McpAccessControlPlugin.kt`,
  `KtorMcpServerManager.kt`, `McpTakeoverPipelineTest.kt`
- **Verification:** full `./gradlew test` green, including the whole Phase-20 access-control suite;
  `detekt ktlintCheck` green; `detekt-baseline.xml` unchanged.
- **Committed in:** `53595d0`

**Scope note.** `McpAccessControlDecision.kt` and `McpAccessControlPlugin.kt` are **outside this plan's
declared `files_modified`**. They were changed anyway because the alternative was shipping a plan that
knowingly broke one of its own must-have truths. The change was kept to two defaulted fields and one
`when` limb, and was classified Rule 2 rather than Rule 4 (architectural) on the grounds that it
completes the plan's stated intent rather than redesigning the gate: no auth approach changed, no
control was removed, no endpoint was added, and no response signal visible to an unauthenticated
scanner changed. Flagged here so the phase verifier can second-guess that classification.

---

**Total deviations:** 1 auto-fixed (1 missing critical)
**Impact on plan:** The deviation restores a plan must-have truth that the plan's own instructions
would otherwise have violated. No scope creep beyond it.

## Verification Evidence

All plan acceptance criteria were run and passed:

| Gate | Result |
|---|---|
| `./gradlew test --tests '*McpTakeoverProofTest' --tests '*McpTakeoverPipelineTest' --tests '*McpSupervisorProbeTest'` | pass |
| `./gradlew test --tests '*McpTakeoverSquatterTest'` | pass, `tests="4"` confirmed in the JUnit XML |
| `./gradlew test` (full suite) | pass |
| `./gradlew detekt ktlintCheck` | pass |
| `grep -c 'setRequestProperty' McpSupervisor.kt` | `1`, and that line carries `McpTakeoverProof.HEADER` |
| `grep -c 'settings.token' McpSupervisor.kt` | `1`, and that line carries `McpTakeoverProof.forTarget` |
| `grep -c 'constantTimeCompare' McpTakeoverProof.kt` | `2` (≥ 1 required) |
| `grep -c 'X-Burp-AI-Agent' McpTakeoverSquatterTest.kt` | `2` (≥ 1 required) |
| `git diff --stat detekt-baseline.xml` | empty — baseline still 1096 entries |
| `git diff build.gradle.kts` | empty — no new dependency, no new `inputs` declaration |
| test-class naming vs `-PexcludeHeavyTests` globs | `0` matches |

Every Gradle invocation used `JAVA_HOME=$(/usr/libexec/java_home -v 21)`. No `RedactionTest` timing
flake was observed on any of the three full-suite runs.

## Issues Encountered

- **ktlint, twice.** `chain-method-continuation` on an inline OkHttp `Request.Builder()` chain and
  `First line of body expression fits on same line as function signature` on the new `takeoverProof`
  helper. Both fixed by reformatting; no rule was suppressed and no baseline entry was added.
- **A KDoc sentence broke a source-text gate.** The first draft of `takeoverProof`'s KDoc contained the
  literal string `settings.token` in prose, which made `grep -c 'settings.token'` return `2` and
  failed the plan's acceptance criterion. Reworded to say "the token property" instead, and the KDoc
  now warns the next editor not to name it there either. Worth recording: a structural grep gate is
  sensitive to comments, which is easy to trip and easy to misdiagnose.

## Requirements

`requirements-completed` is deliberately **empty** and `.planning/REQUIREMENTS.md` was **not edited**.

SEC-07 is claimed by all three plans in this phase (25-01, 25-02 and 25-03) and its text covers both
the takeover credential *and* `SsrfGuard`'s IPv4-notation classification, which is plan 25-02's work.
Ticking it here would be false, and — since 25-02 is executing concurrently in a sibling worktree —
both agents would edit the same checkbox line and produce a guaranteed merge conflict. The orchestrator
should mark SEC-07 complete centrally once all three plans have merged.

## Known Stubs

None. No hardcoded empty values, placeholder text, `TODO`/`FIXME`, skipped tests or unrun `<verify>`
blocks were introduced by this plan. Nothing was appended to `.planning/WINDOWS.md`, and it was left
untouched to avoid a write conflict with the sibling worktree executing plan 25-02.

## Threat Flags

None. Every file touched is inside the trust boundaries the plan's threat model already enumerates.
The one new security surface — the gate limb allowing `SHUTDOWN_PATH` on a valid proof — is covered by
T-25-05 (constant-time comparison, blank-token guard, proof bound to host/port/window) and is asserted
negatively by `anExternalModeShutdownWithNoCredentialIsStillRejected`.

## Next Phase Readiness

- **Ready for plan 25-03.** ADR-16 can be written against the selection recorded above. Note for that
  plan: `McpSupervisor.openConnection(url, tlsEnabled)` still has its original two-argument shape and
  the blanket-accept `X509TrustManager` / unconditional `HostnameVerifier` are untouched, so 25-03's
  planned signature migration to `openConnection(url, settings)` applies cleanly to the current file.
- **Note for 25-03's ADR text:** the residual set is now slightly larger than the plan anticipated —
  alongside T-25-04 (proof replay) and A-25-05 (host-string identity), ADR-16 should record that the
  external-mode access-control gate participates in the takeover credential decision, since that was
  discovered during execution rather than at planning time.
- **No blockers.**

---
*Phase: 25-secondary-hardening*
*Completed: 2026-08-22*
