---
phase: 22-agent-tool-call-trust-boundary
plan: 05
subsystem: mcp
tags: [sec-06, sc3, audit, telemetry, cwe-117, hash-by-default, d-06, d-10, d-12]
requires:
  - phase: 22-agent-tool-call-trust-boundary
    provides: ToolDecision, ImplicitDenyReason, sanitizeInline (plan 22-03)
  - phase: 22-agent-tool-call-trust-boundary
    provides: SecTier and its wireValue (plan 22-02)
provides:
  - ToolDecisionReporter
  - ToolDecisionReporter.report
  - mcp_tool_decision audit event
  - ToolDecisionReporterTest
affects:
  - com.six2dez.burp.aiagent.mcp
  - com.six2dez.burp.aiagent.audit
  - com.six2dez.burp.aiagent.ui
tech-stack:
  added: []
  patterns:
    - "One construction, three destinations: the reporter RETURNS the metadata map so the audit event and the AI Activity record cannot drift apart"
    - "New audit type constant per event meaning, never a reuse of a constant whose payload keys mean something different"
    - "Exhaustive `when` over the decision enum as a fail-closed classifier — a ninth constant is a compile error, not a silent success"
    - "Hash by default behind a constructor seam; the split is by provenance (model-supplied vs extension-derived), not by convenience"
key-files:
  created:
    - src/main/kotlin/com/six2dez/burp/aiagent/mcp/ToolDecisionReporter.kt
    - src/test/kotlin/com/six2dez/burp/aiagent/mcp/ToolDecisionReporterTest.kt
  modified: []
key-decisions:
  - "isDenial is an exhaustive `when` over ToolDecision rather than the analog's boolean predicate: here a new denial-shaped constant must NOT default to a success status, so the compile error is the control"
  - "The reporter drops resultChars on a denial rather than trusting the call site, because a record reading 'denied' alongside a result length is self-contradictory"
  - "Payload values are typed String? rather than Any?, which makes the returned metadata map a mechanical null-filter with no cast and keeps resultChars stringified exactly as the existing MCP_TOOL_CALL metadata already does"
  - "The test's reporter() helper deliberately does NOT pass verboseAudit, so flipping the production default is a red suite — the plan's prescribed behaviour check would otherwise have been vacuous"
patterns-established:
  - "Conditional audit keys are inserted at their ordered position, not appended, and the ordered position is itself asserted"
  - "Marker-absence assertions (SECRETMARKER / pretend_tool_MARKER) prove a hash rather than eyeballing one"
requirements-completed: [SEC-06]
duration: 16min
completed: 2026-08-14
---

# Phase 22 Plan 05: SC3 Decision Record Summary

**One `ToolDecisionReporter` turns a resolved SEC-06 decision into an ordered `mcp_tool_decision` audit event, one sanitized Output-tab line and the `MCP_TOOL_CALL` metadata map — all from a single construction, with model-supplied values hashed by default — proven by 14 tests and three mutations.**

## Performance

- **Duration:** ~16 min
- **Started:** 2026-08-14T08:14:33Z
- **Completed:** 2026-08-14T08:30:11Z
- **Tasks:** 2
- **Files modified:** 2 (2 created, 0 modified)

## Accomplishments

- **The record extends what already ships instead of inventing a shape.** The payload's first five keys are `MCP_TOOL_CALL`'s existing ones in their existing order; SEC-06 adds `secTier`, `decision`, and the three conditional keys. Nothing existing was renamed or re-typed, so no current consumer of the AI Activity metadata changes behaviour.
- **The two sinks cannot disagree, structurally.** `report` builds ONE payload, emits it, and returns the null-filtered projection of that same map for the caller to merge. A call site that hand-assembled a second metadata map is precisely the drift this shape forecloses — asserted directly by `returnedMetadataMapIsTheSameConstructionAsTheAuditPayload`.
- **Denial is a third status value, and a new denial cannot silently become a success.** `isDenial` is an exhaustive `when` over `ToolDecision`, so a ninth constant is a compile error in this file rather than an entry that quietly records as `"ok"`.
- **`secTier` rides on every event, including `AUTO`.** That is the single field that makes "which tool calls ran with no decision at all?" answerable from a historical log, and the key-order tests fail if it is dropped.
- **The audit record and the Output tab carry deliberately different things.** The durable record hashes what the model authored; the Output line carries sanitized plaintext and provably no digest, because a hash diagnoses nothing for the human reading the Output tab.

## Task Commits

1. **Task 1: Create ToolDecisionReporter with the ordered payload and both destinations** — `1994c56` (feat)
2. **Task 2: Create ToolDecisionReporterTest — pin the payload shape and the hashing rule** — `b07fe43` (test)

## Files Created/Modified

- `src/main/kotlin/com/six2dez/burp/aiagent/mcp/ToolDecisionReporter.kt` (created, 274 lines) — the `mcp_tool_decision` constant, `internal class ToolDecisionReporter(logToOutput, verboseAudit = false)`, the single `report` entry point, the ordered `buildPayload`, `auditValue`, the Output-line builders and the exhaustive `isDenial`.
- `src/test/kotlin/com/six2dez/burp/aiagent/mcp/ToolDecisionReporterTest.kt` (created, 336 lines) — 14 tests across the six SC3 properties, with the emitter-leak `@AfterEach` copied from the analog.

## The shipped payload shape

| # | Key | Present when | Plaintext or hashed |
|---|-----|--------------|---------------------|
| 1 | `operation` | always (`"tool_chain"`) | plaintext, unchanged |
| 2 | `status` | always — `"denied"` for the four denial decisions, else `runStatus ?: "ok"` | plaintext |
| 3 | `traceId` | always | plaintext, extension-derived |
| 4 | `step` | always | plaintext |
| 5 | `toolName` | always — canonical ID, or `"unknown"` | plaintext (catalog-derived) |
| 6 | `toolNameSha256` | only when the name did not resolve | **hashed** |
| 7 | `secTier` | **always, including AUTO** | plaintext |
| 8 | `decision` | always | plaintext |
| 9 | `implicitDenyReason` | only when `decision == implicit_deny` | plaintext |
| 10 | `argsSha256` | only when args are non-blank | **hashed** unless the verbose seam is on |
| 11 | `resultChars` | only when a tool produced a result, never on a denial | plaintext |

Output line, one per decision:
`[SEC-06] decision=approve_session tier=confirm tool=http1_request step=3 trace=trace-22-05`

## Verification Evidence

### Mutation testing (required by Task 2 acceptance)

**1. Production default flipped `verboseAudit = false` → `true`:** 14 tests completed, **1 failed**.

```
argsAreHashedByDefault()
AssertionFailedError: expected: <d8f96b30cc40cb0b6d2e0cddca46a6b4fb81622a695a8a7190dd17be583b54a3>
but was: <{"url":"http://evil.example/SECRETMARKER"}>
```

**2. Denial status changed `"denied"` → `"error"`:** 14 tests completed, **1 failed**.

```
deniedDecisionUsesDeniedStatusAndOmitsResultChars()
AssertionFailedError: DENY must not be recorded as anything else ==> expected: <denied> but was: <error>
```

**3. `secTier` dropped from the payload:** 14 tests completed, **3 failed**.

```
expected: <[operation, status, traceId, step, toolName, secTier, decision, argsSha256, resultChars]>
 but was: <[operation, status, traceId, step, toolName, decision, argsSha256, resultChars]>

expected: <[operation, status, traceId, step, toolName, toolNameSha256, secTier, decision]>
 but was: <[operation, status, traceId, step, toolName, toolNameSha256, decision]>

secTierIsPresentOnAnAutoEvent(): expected: <auto> but was: <null>
```

All three mutations were reverted; `git status` confirmed `ToolDecisionReporter.kt` returned byte-identical to commit `1994c56` before the test was committed.

### Acceptance greps

| Check | Expected | Actual |
|-------|----------|--------|
| `class ToolDecisionReporter` | 1 | 1 |
| `mcp_tool_decision` | 1 | 1 |
| `mcp_tool_blocked\|mcp_tool_end` | 0 | 0 |
| `linkedMapOf` | 1 | 1 |
| `Hashing.sha256Hex` | ≥1 | 2 |
| `MessageDigest` | 0 | 0 |
| `"denied"` | ≥1 | 2 |
| `argsSha256` | ≥1 | 2 |
| `verboseAudit: Boolean = false` | 1 | 1 |
| `javax.swing\|java.awt\|MontoyaApi` | 0 | 0 |
| reporter line count | ≥90 | 274 |
| `class ToolDecisionReporterTest` | 1 | 1 |
| `@Test` | ≥8 | 14 |
| `@AfterEach` | 1 | 1 |
| `registerGlobalEmitter(null)` | 1 | 1 |
| `SECRETMARKER` in test | ≥2 | 3 |

**Gates:** `JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew ktlintCheck detekt test` exits 0 with the full suite green (`ToolDecisionReporterTest` 14/14). `git diff --stat -- detekt-baseline.xml` is empty — the baseline was never touched. The `RedactionTest` flake the phase brief warns about did not fire on the final run.

## Decisions Made

- **`isDenial` is an exhaustive `when`, not the analog's boolean predicate.** `McpBlockedRequestReporter` uses a predicate deliberately, so that adding a `BlockReason` cannot silently opt it into coalescing. Here the risk points the other way: a new denial-shaped `ToolDecision` that a predicate does not name would be recorded as `"ok"` — a fail-open in the audit trail. An exhaustive `when` used as an expression makes a ninth constant a compile error at this site, which is the same mechanism D-03 applies at the catalog.
- **The reporter drops `resultChars` on a denial instead of trusting the caller.** The plan states "a denial has no result"; enforcing it here makes that a property of the reporter rather than of every future call site, and it lets the test pass `resultChars = 1500` alongside `DENY` and still assert the key is absent — a non-vacuous assertion instead of one that only re-checks its own inputs.
- **Payload values are typed `String?`, not `Any?`.** That makes the returned `Map<String, String>` a mechanical null-filter with no unchecked cast, and it keeps `resultChars` stringified exactly as the existing `MCP_TOOL_CALL` metadata already stringifies it, so the two records agree field for field rather than by coincidence.
- **`argsSha256` byte-equality with `McpTool.runTool`'s field is explicitly NOT claimed.** That one digests the trimmed raw string; this one digests the sanitized string, because sanitizing before hashing is what makes the verbose plaintext path CWE-117 safe. The KDoc records the shared key name as "a digest of the arguments" and states the non-claim rather than leaving a reader to assume equality (D-14, claim only what ships).
- **The test's `reporter()` helper does not pass `verboseAudit` at all.** Restating `false` in the helper would have made the plan's own prescribed behaviour check vacuous — flipping the production default would leave the suite green. The helper now exercises the default that CLAUDE.md actually constrains, and `verboseReporter()` covers the opt-in path.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Worktree base was behind the stated plan base**
- **Found during:** Setup, before Task 1
- **Issue:** The worktree spawned at `03f17a7` (a v0.9.2 release commit) rather than the required base `389e41c`. `git merge-base` returned `03f17a7`, so neither `SecTier` nor `ToolApprovalGate` existed — nothing this plan consumes would have compiled.
- **Fix:** `git reset --hard 389e41cd58eeafc06be57bee175e73b049440400`, exactly as the branch-check protocol prescribes, after asserting HEAD was in the `worktree-agent-*` namespace. Working tree was clean.
- **Verification:** `git log --oneline -1` showed `389e41c docs(phase-22): update tracking after wave 2`.
- **Committed in:** n/a (pre-execution correction)

**2. [Rule 3 - Blocking] The plan's cited `@Suppress` precedent shape is a ktlint violation**
- **Found during:** Task 1
- **Issue:** `PassiveAiScannerPrompts.kt:73-76` puts the `LongParameterList` rationale in an EOL comment immediately above `@Suppress`. Reproducing that after `report`'s KDoc tripped `standard:no-consecutive-comments` — "an EOL comment may not be preceded by a KDoc".
- **Fix:** Folded the rationale into the KDoc's final paragraph, keeping the precedent citation and the QUAL-07 reason verbatim. `@Suppress("LongParameterList")` still sits on the declaration; the baseline was not grown.
- **Files modified:** `src/main/kotlin/com/six2dez/burp/aiagent/mcp/ToolDecisionReporter.kt`
- **Verification:** `ktlintCheck` exits 0; `git diff --stat -- detekt-baseline.xml` empty.
- **Committed in:** `1994c56`

**3. [Rule 3 - Blocking] Plan prose and its own acceptance greps conflicted twice**
- **Found during:** Task 1
- **Issue:** Two of the plan's instructions cannot both be satisfied literally. (a) It asks the KDoc to explain that "the output sink is a lambda rather than a `MontoyaApi`", while the acceptance requires `grep -c 'MontoyaApi'` to return **0**. (b) It asks the payload KDoc to name the ordered-map construction, while requiring `grep -c 'linkedMapOf'` to return exactly **1** — the KDoc reference pushed it to 2.
- **Fix:** Said the same thing without the literals: "output sink is a lambda, not the Burp API handle" and "in an insertion-ordered map". Same conflict class as plan 22-03's deviations 1 and 2, resolved the same way — in favour of the mechanical check, with meaning preserved.
- **Files modified:** `src/main/kotlin/com/six2dez/burp/aiagent/mcp/ToolDecisionReporter.kt`
- **Verification:** both greps now return their required values; the KDoc still states both contracts.
- **Committed in:** `1994c56`

**4. [Rule 2 - Missing critical] Denial-and-result-length was representable in the record**
- **Found during:** Task 2
- **Issue:** As literally specified, `resultChars` is emitted whenever the caller passes it — including alongside `status = "denied"`. A record asserting both "the call was refused" and "it returned 1500 characters" is self-contradictory, and an auditor cannot tell which half to believe. The invariant lived only in the caller's discipline.
- **Fix:** `buildPayload` drops `resultChars` whenever the decision is a denial, with the reason in a comment. The test now supplies `resultChars` on all four denial decisions and asserts absence.
- **Files modified:** both files
- **Verification:** `deniedDecisionUsesDeniedStatusAndOmitsResultChars` passes with `resultChars = 1500` supplied on every denial.
- **Committed in:** `1994c56` / `b07fe43`

**5. [Rule 3 - Blocking] The test helper's parameter count reached detekt's threshold**
- **Found during:** Task 2
- **Issue:** A single `report(...)` test helper carrying all of the entry point's arguments has 10 parameters, which is exactly `LongParameterList`'s `functionThreshold`. `detekt` failed on the test source set.
- **Fix:** Dropped `canonicalId` from the helper (every helper-driven test uses a name that canonicalises to itself) and added `payloadNamesTheCanonicalToolNotTheAliasTheModelWrote`, which calls `report` directly with `history` → `site_map`. The constraint therefore produced a test the plan did not ask for and that the phase wants: T-22-12 reaching the audit record, proving the log names the tool that actually executed rather than the alias the model typed.
- **Files modified:** `src/test/kotlin/com/six2dez/burp/aiagent/mcp/ToolDecisionReporterTest.kt`
- **Verification:** `detekt` exits 0; the new test passes and asserts on both sinks.
- **Committed in:** `b07fe43`

### Additions beyond the plan's list

The plan asked for eight tests plus the `AUTO` assertion; 14 shipped. The five extra are `returnedMetadataMapIsTheSameConstructionAsTheAuditPayload` (T-22-09's "cannot disagree"), `runStatusIsPreservedForACallThatActuallyRan` (the third status value is additive, not a replacement), `blankArgsAreOmittedRatherThanHashedAsTheEmptyString` (the null-in-null-out rule the analog documents), `payloadNamesTheCanonicalToolNotTheAliasTheModelWrote` (above) and `outputLineCarriesTheDecisionTierStepAndTrace` (the exact Output-line format, pinned as a single string equality).

---

**Total deviations:** 5 auto-fixed (4 blocking, 1 missing-critical). No architectural deviations, no Rule 4 escalations.
**Impact on plan:** None on scope or design. Two were wording adjustments forced by the plan's own acceptance greps, one was an environment correction, one a lint-shape correction, and one turned a threshold constraint into an extra test the phase benefits from. No packages added, consistent with `T-22-SC` (accept, zero dependency changes).

## Issues Encountered

- **The reporter is not constructed anywhere yet, and that is the plan's scope boundary.** `ToolDecisionReporter` is `internal` and instantiated only by its test. detekt's unused-code rules target `private` declarations, so an `internal` class awaiting its wiring plan does not trip the gate — confirmed clean.
- **Nothing was needed from `ToolApprovalGate` that plan 22-03 had not already shipped.** `sanitizeInline`, `ToolDecision`, `ImplicitDenyReason` and `SecTier` were all present in the base; the parallel 22-04 work on `evaluate` / session memory was neither called nor waited on, and `ToolApprovalGate.kt` was not touched.

## Threat Model Coverage

| Threat ID | Disposition | How this plan discharges it |
|-----------|-------------|------------------------------|
| T-22-06 | mitigate | `sanitizeInline` is applied to the model-authored name before either sink. `outputLineIsSingleLineAndSanitized` proves `"a\r\nInjected: line"` reaches the Output tab as `"aInjected: line"` on one line — control characters removed, not replaced, so a forged second line is impossible. |
| T-22-09 | mitigate | Three destinations from one invocation: the `mcp_tool_decision` event, one Output line, and the returned metadata map. `reportEmitsExactlyOneAuditEventPerInvocation` pins the counts; `returnedMetadataMapIsTheSameConstructionAsTheAuditPayload` pins that the map is the payload, not a second one. |
| T-22-23 | mitigate | `argsSha256` is `Hashing.sha256Hex` behind a `verboseAudit` seam wired to `false` with no user-facing toggle. Proven by marker absence (`SECRETMARKER` appears in neither the payload nor the Output line) and by the flipped-default mutation going red. |
| T-22-19 | mitigate | `"denied"` is a third status value, asserted for all four denial decisions and explicitly asserted **not** to be `"error"`. Proven non-vacuous by the `"denied"` → `"error"` mutation. |
| T-22-24 | mitigate | `secTier` is emitted on every event; `secTierIsPresentOnAnAutoEvent` covers the `AUTO`/`AUTO` case, and both key-order tests fail if the field is dropped. |
| T-22-25 | mitigate | New constant `mcp_tool_decision`; `grep -c 'mcp_tool_blocked\|mcp_tool_end'` returns 0 in the reporter, and the test asserts the emitted type string directly. |
| T-22-SC | accept | Zero packages added or changed. |

**Threat flags:** none. This plan adds a pure emitter over two existing sinks — no network endpoint, no auth path, no file access it does not already reach through `AuditLogger`, and no schema change at a trust boundary beyond the additive payload keys documented above.

## Known Stubs

| Symbol | Wired by |
|--------|----------|
| `ToolDecisionReporter` construction (the `logToOutput` lambda over the Burp logging handle) | plan 22-07 / 22-08, at the `ChatPanel` call site |
| `report(...)` invocation from a resolved decision branch | plan 22-07 (approved paths) and 22-08 (the five implicit-denial paths) |

Nothing in this plan's own goal is stubbed: every field, both sinks and the returned map are fully implemented and asserted. The plan's objective states the boundary — the reporter "is invoked from the caller's already-resolved branch", and that branch is another plan's work.

## Notes for the sibling and downstream plans

- **For 22-07 / 22-08, the call contract:** `report` needs `rawToolName` **and** `canonicalId` **and** `knownTool` as three separate arguments. `knownTool` is not derivable inside the reporter without giving it a catalog dependency it deliberately does not have — compute it at the call site as "`canonicalToolId` resolved to a catalog entry, or an `ext:` name", the same test `ToolApprovalGate.tierFor` already performs.
- **For 22-04:** nothing is required of `ToolApprovalGate` by this plan. If `evaluate` ends up returning an outcome object carrying tier + decision + implicit-deny reason, `report`'s argument list maps onto it one-for-one with no adapter.
- **`runStatus` is the seam for the existing `"ok"` / `"error"` derivation.** The caller keeps computing it exactly as `ChatPanel` does today (`result.startsWith("Error:")`) and passes it through; the reporter only overrides it for a denial.

## Success Criteria

- [x] One `mcp_tool_decision` event, one Output line and one metadata map per decision, all from a single construction — counts asserted, and the map proven to be the payload's projection
- [x] Model-supplied values hashed by default, extension-derived values plaintext — proven by marker absence and by a red suite when the default flips
- [x] Denial is `status = "denied"`; `secTier` present on every event including `AUTO` — both proven non-vacuous by mutation
- [x] The reporter contains no policy, no Swing and no Burp API handle — grep-confirmed, and the whole suite runs it with no harness

## Self-Check: PASSED

- FOUND: `src/main/kotlin/com/six2dez/burp/aiagent/mcp/ToolDecisionReporter.kt`
- FOUND: `src/test/kotlin/com/six2dez/burp/aiagent/mcp/ToolDecisionReporterTest.kt`
- FOUND: commit `1994c56`
- FOUND: commit `b07fe43`

---
*Phase: 22-agent-tool-call-trust-boundary*
*Completed: 2026-08-14*
