---
phase: 20-mcp-access-control-correctness
plan: 09
subsystem: api
tags: [security, mcp, access-control, audit, rate-limiting, adr, cwe-400, cwe-779]

# Dependency graph
requires:
  - phase: 20-mcp-access-control-correctness
    provides: "McpBlockedRequestReporter (D-06/D-07/D-09/D-10 blocked-request observability) and BlockReason.UNAUTHORIZED / BLANK_TOKEN from the decision core"
provides:
  - "ADR-13 in repo-root DECISIONS.md — the recorded decision amending D-06's literal 'every occurrence' for the two pre-authentication reasons"
  - "Per-reason audit coalescing for BlockReason.UNAUTHORIZED and BlockReason.BLANK_TOKEN: one mcp_transport_blocked event per 60s window carrying a Long suppressed count"
  - "Preserved per-occurrence audit emission, with the payload key set unchanged, for the four local-mode reasons"
  - "An accurate residual-risk KDoc on McpBlockedRequestReporter, replacing the false loopback-only premise"
affects: [mcp-server, audit-logging, phase-25-sec-07, audit-file-rotation]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Two independent ReasonWindow maps — one per sink — because getAndSet(0L) consumes the suppression counter"
    - "Flood-capability as a boolean predicate over existing BlockReason constants, never an exhaustive when"

key-files:
  created: []
  modified:
    - DECISIONS.md
    - .planning/phases/20-mcp-access-control-correctness/20-CONTEXT.md
    - src/main/kotlin/com/six2dez/burp/aiagent/mcp/McpBlockedRequestReporter.kt
    - src/test/kotlin/com/six2dez/burp/aiagent/mcp/BlockedRequestReporterTest.kt

key-decisions:
  - "ADR-13: coalesce the mcp_transport_blocked audit event for UNAUTHORIZED and BLANK_TOKEN into one event per 60s per-reason window carrying a suppressed count; keep per-occurrence emission for the four local-mode reasons"
  - "Rejected adding size-capping or rotation to the shared AuditLogger — blast radius out of proportion to this gap closure; recorded as ADR-13's residual"
  - "suppressed is a Long, and means occurrences collapsed away SINCE the previous emission, so the first event of a burst always carries 0L"
  - "The audit sink gets its own auditWindows map; sharing ReasonWindow instances with D-09's windows would let one sink steal the other's count via getAndSet(0L)"

patterns-established:
  - "Per-sink rate-limit windows: same BLOCK_LOG_WINDOW_MS length, distinct ReasonWindow instances, lock-free read-then-CAS with 0L meaning 'never logged'"
  - "Amending a locked phase decision: leave the original bullet readable, add an AMENDED-by pointer beside it, and record the authorisation as an ADR"

requirements-completed: [SEC-04]

# Metrics
duration: 21min
completed: 2026-08-10
---

# Phase 20 Plan 09: Bound the MCP transport-block audit sink Summary

**Closes CR-01 by coalescing the two pre-authentication MCP denial reasons into one audit event per 60s per-reason window with a `suppressed` count, so an unauthenticated external peer no longer controls the append rate to `~/.burp-ai-agent/audit.jsonl`, and records ADR-13 as the decision authorising the D-06 amendment.**

## Performance

- **Duration:** 21 min
- **Started:** 2026-08-10T00:00:00Z (approx — worktree spawn)
- **Completed:** 2026-08-10T00:21:00Z
- **Tasks:** 2 (Task 2 was TDD: RED then GREEN, no REFACTOR needed)
- **Files modified:** 4

## Accomplishments

- **The remote unauthenticated append primitive is bounded.** In external mode every route except `/__mcp/health` answers 401, and each 401 was an `UNAUTHORIZED` / `BLANK_TOKEN` denial that drove one synchronous `logFile.appendText` on a Netty event-loop thread. Those two reasons now emit at most one audit record per reason per minute regardless of request rate (CWE-400 / CWE-779).
- **Burst detection is preserved, not traded away.** The emitted record carries `suppressed`, the count of occurrences collapsed away since the previous emission, so only per-request granularity is lost — a flood is still visible in the audit trail rather than silently dropped.
- **D-06 is preserved where it matters.** The four local-mode reasons (`ORIGIN_MISMATCH`, `HOST_MISMATCH`, `REFERER_MISMATCH`, `BROWSER_NO_ORIGIN`) require local code execution to trigger and are the diagnosable ones; they keep per-occurrence emission with the exact eight-key payload and no `suppressed` key.
- **The two sinks are provably independent.** A new `auditWindows` map holds `ReasonWindow` instances distinct from D-09's `windows`. Both sinks consume their counter with `getAndSet(0L)`, so a shared instance would have let whichever ran first steal the other's count. One test asserts both the coalesced audit event (`suppressed == 3L`) and the unchanged pair of Output lines (aggregate reading `3 further blocks`) from the *same* sequence of `report()` calls.
- **The false rationale is gone.** The KDoc no longer contains the string `loopback-only` (verified: `grep -c` returns 0) and names ADR-13 six times so a reader can find the record.
- **ADR-13 recorded.** `grep -c '^## ADR-' DECISIONS.md` returns 13; ADR-1..ADR-12 are untouched (`git diff --numstat DECISIONS.md` = `13 0`, additions only).

## Task Commits

1. **Task 1: record ADR-13 in DECISIONS.md and point D-06 at it** — `1cb9265` (docs)
2. **Task 2 RED: failing pins for the ADR-13 coalescing contract** — `e7de6d6` (test) — 22 tests, 5 failures
3. **Task 2 GREEN: coalesce the audit event for the two pre-auth reasons** — `25fa69d` (fix) — 22 tests, 0 failures

No REFACTOR commit: the GREEN implementation reused the existing `ReasonWindow` type, the existing `BLOCK_LOG_WINDOW_MS` constant and the existing read-then-CAS idiom, so there was nothing to clean up.

## Files Created/Modified

- `DECISIONS.md` — appended `## ADR-13: Coalesce the MCP transport-block audit event for pre-authentication denials (amends D-06)` after ADR-12, in the file's established `**Context.** / **Decision.** / **Consequences.**` shape (13 lines added, 0 removed).
- `.planning/phases/20-mcp-access-control-correctness/20-CONTEXT.md` — one added line after the D-06 bullet recording the amendment and pointing at repo-root `DECISIONS.md` (1 line added, 0 removed; D-06's own text left byte-identical).
- `src/main/kotlin/com/six2dez/burp/aiagent/mcp/McpBlockedRequestReporter.kt` — added the `auditWindows` map and the `consumeAuditWindow` helper; `report()` now branches on a `floodCapable` boolean; class-level and T-20-12 KDoc rewritten.
- `src/test/kotlin/com/six2dez/burp/aiagent/mcp/BlockedRequestReporterTest.kt` — new "ADR-13" section with 6 tests plus 3 deny-builder helpers (`denyUnauthorized`, `denyBlankToken`, `denyHost`).

### ADR-13 as committed

Title: `## ADR-13: Coalesce the MCP transport-block audit event for pre-authentication denials (amends D-06)`

- **Context** records the measured facts: D-06 locked an audit event on every occurrence and D-09 scoped its window to the Output tab only; `AuditLogger.logEvent` performs a synchronous `logFile.appendText` to `~/.burp-ai-agent/audit.jsonl`; `App.kt` registers the global emitter unconditionally; the reporter runs on Netty event-loop threads; in external mode every route but `/__mcp/health` answers 401 and each is an `UNAUTHORIZED` / `BLANK_TOKEN` denial. Notes the primitive is *new* in phase 20 (pre-phase, a blocked transport request emitted nothing) and that "off by default" is not a mitigation because both preconditions are supported, documented configurations.
- **Decision** states the coalescing shape verbatim (both reason names, 60s per-reason window, `suppressed` count), the retention of per-occurrence emission for the four named local-mode reasons, the explicit rejection of `AuditLogger` size-capping/rotation, and that this ADR is the authorisation for amending D-06.
- **Consequences** (5 bullets): the one-record-per-reason-per-minute ceiling; burst visibility retained via `suppressed`; Output-tab behaviour unchanged under D-09 with independent counters; local-mode fidelity intact; and the residual that `AuditLogger` still has no size cap, deliberately left to a future phase.

### `suppressed` semantics as implemented

`consumeAuditWindow(reason, nowMs): Long?` returns:
- the value of `suppressedCount.getAndSet(0L)` when the window has elapsed (`prev == 0L || nowMs - prev >= BLOCK_LOG_WINDOW_MS`) **and** the CAS on `lastLoggedAtMs` wins → the caller emits, appending `"suppressed" to <that Long>` after the eight D-06 keys via `Map.plus` (insertion order preserved);
- `null` otherwise, having first done `suppressedCount.incrementAndGet()` → the caller emits nothing.

So `suppressed` counts occurrences collapsed away **since the previous emission** and does **not** count the emitted occurrence itself — identical to the meaning `aggregateLine` already gives it for the Output tab. The first event of a burst therefore always carries `0L`. The type is `Long` deliberately: the payload's value type is `Any?`, and an `Int` would silently fail `assertEquals(3L, payload["suppressed"])`.

## Test counts for `BlockedRequestReporterTest`

| Point | tests | failures |
|-------|-------|----------|
| Before this plan (base `b9ee87a`) | 16 | 0 |
| After RED (`e7de6d6`) | 22 | 5 |
| After GREEN (`25fa69d`) | 22 | 0 |

Read from `build/test-results/test/TEST-com.six2dez.burp.aiagent.mcp.BlockedRequestReporterTest.xml`, not console output.

At RED the 5 failures were exactly the coalescing pins (`report_coalescesRepeatedUnauthorizedAuditEventsInsideOneWindow`, `report_carriesTheSuppressedCountOnTheFirstAuditEventAfterTheWindowElapses`, `report_coalescesBlankTokenInAWindowIndependentOfUnauthorized`, `report_coalescedPayloadAppendsSuppressedAfterTheExistingKeys`, `report_auditCoalescingLeavesTheOutputTabWindowUntouched`). The sixth new test, `report_keepsPerOccurrenceAuditEventsForLocalModeReasonsWithNoSuppressedKey`, passed at RED by design — it is a D-06-preserving guard, so a failure there would have meant the local-mode path was already wrong.

`McpAccessControlPipelineTest`: 9 tests, 0 failures — `foreignHost_emitsExactlyOneTransportBlockedAuditEvent` still observes exactly one real `host_mismatch` event end-to-end, confirming `host_mismatch` was not swept into the coalescing path.

## Decisions Made

None beyond the maintainer's locked shape. Two implementation choices worth recording:

- **A `Long?` return instead of the review's sketched `AuditVerdict` sealed type.** CR-01's suggested snippet used `sealed AuditVerdict.Emit(count)`. A nullable `Long` carries the same information (`null` = suppress, value = the count) without adding a type, and keeps the helper to a single `return` statement so detekt's default `ReturnCount` max of 2 is satisfied. The reporter went from 9 to 10 functions against `TooManyFunctions`' threshold of 11, so `detekt-baseline.xml` stayed byte-identical.
- **`floodCapable` is a boolean equality predicate, not an exhaustive `when`.** Sibling plan 20-07 works on the decision core concurrently. A `when` over `BlockReason` would break at compile time — or worse, silently opt a new reason into coalescing — if that plan adds a constant. A comment in `report()` states this.

## Deviations from Plan

None — plan executed exactly as written.

**Total deviations:** 0
**Impact on plan:** none. Every constraint in the plan's `<constraints_verified_against_the_real_source>` held against the real source.

## Issues Encountered

- **The worktree spawned at an older commit than the expected base**, as the plan's `<worktree_branch_check>` predicted. `git merge-base HEAD b9ee87a` returned `03f17a7`, so the mandated `git reset --hard b9ee87ae0750316a11313e497825e86231830843` fired and was verified. The HEAD assertion ran first: branch was `worktree-agent-ac2e9fce2ccc10a62`, not a protected ref, so no halt was needed.
- **Two compound Bash commands were refused by the worktree-isolation guard** (a `case`/redirect one-liner and a `for`-loop over two test XMLs). Both were re-run as plain separate commands. No workaround, no `dangerouslyDisableSandbox`.

## Verification

- `JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test detekt ktlintCheck` → `BUILD SUCCESSFUL` (1m 31s).
- `git diff --stat detekt-baseline.xml` → empty (QUAL-07 satisfied).
- `git diff b9ee87a --name-only` → exactly the four `files_modified`. `src/main/kotlin/com/six2dez/burp/aiagent/audit/AuditLogger.kt` is **not** in the list.
- `git diff b9ee87a -- src/test/.../BlockedRequestReporterTest.kt | grep -c '^-[^-]'` → `0`, proving `report_emitsExactlyOneTransportBlockedEventPerInvocation` and `report_emitsAuditEventEvenWhenTheOutputLineIsSuppressed` are byte-identical.
- `grep -c 'loopback-only' McpBlockedRequestReporter.kt` → `0`; `grep -c 'ADR-13'` → `6`.
- `grep -c '^## ADR-' DECISIONS.md` → `13`.
- No new `BlockReason` constant added; `build.gradle.kts` untouched; no dependency added.

This plan's gate proves it in **isolation**. The orchestrator owns the post-merge `test detekt ktlintCheck` plus `test -PstoreBuild=true` on the merged tree.

## Known Stubs

None. No hardcoded empty value, placeholder string, or unwired data path was introduced.

## Threat Flags

None. No new network endpoint, auth path, file-access pattern, or schema change at a trust boundary was introduced — this plan *narrows* an existing file-write path.

## User Setup Required

None — no external service configuration required.

## Next Phase Readiness

- CR-01 is closed in both halves: the audit sink is bounded for the flood-capable reasons, and the residual-risk rationale is accurate.
- **Carried forward deliberately (ADR-13's recorded residual):** `AuditLogger` still has no size cap or rotation, so a local process able to loop local-mode denials can still grow `audit.jsonl`. That is a shared-infrastructure concern touching every phase and was explicitly rejected as out of scope here. It is a candidate for its own phase.
- **Untouched by design, still open from 20-REVIEW:** IN-08 (the `nowMs == 0` sentinel), IN-09 (the Output aggregate undercounting by one and dropping the current block's detail), WR-05 (the unquoted delimiter-based Output line), IN-01..IN-04. All are in or near this file; all were left alone to keep the diff to this gap. IN-09 is worth noting for whoever picks it up — the aggregate-line arithmetic and the new `suppressed` count now share the same off-by-one convention, so a fix should address both sinks together.
- Sibling gap-closure plans 20-07, 20-08 and 20-10 touch disjoint files; no merge conflict is expected with this plan's four.

## Self-Check: PASSED

All four modified files exist on disk at the recorded paths, and all four commits (`1cb9265`, `e7de6d6`, `25fa69d`, `d8c09e9`) are present in `git log`. `git status --short` is clean. STATE.md and ROADMAP.md were deliberately NOT written — the orchestrator owns those after all worktree agents merge.

---
*Phase: 20-mcp-access-control-correctness*
*Completed: 2026-08-10*
