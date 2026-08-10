---
phase: 20-mcp-access-control-correctness
plan: 10
subsystem: mcp
tags: [security, sec-04, sec-05, build-path, bapp-store, docs, http2, sc3, gap-closure]
requires:
  - "BuildFlags generation with the version Property (20-05)"
  - "McpAccessControlExternalPipelineTest on a real TLS connector (20-04)"
  - "docs/mcp-hardening.md Verification section (20-05 / D-12)"
provides:
  - "a store-build test that asserts the SEAM (generated flag vs Gradle property) so `./gradlew test -PstoreBuild=true` — the BApp Store artifact build path — runs the suite to a clean exit"
  - "a `storeBuild.expected` system property on `tasks.test` as the only seam a test can compare `BuildFlags.STORE_BUILD` against"
  - "a hardening-runbook Verification item 2 that matches the code for all four local-mode vectors, including the foreign-`Origin` case that is NOT recorded"
  - "SC3's HTTP/2 half as an asserted contract (`response.protocol == Protocol.HTTP_2`) rather than coverage-by-accident"
affects:
  - "BApp Store submission #231 — the store artifact can be validated by its own test suite again"
  - "phase verification (gap 2 and WR-08 / WR-02 close here; the post-merge re-run is the orchestrator's)"
tech-stack:
  added: []
  patterns:
    - "assert the SEAM, not a fixed value: a generated constant is compared against the build input that produced it, so flipping one without the other fails"
    - "configuration-cache-safe property passing — reuse the already-resolved `val storeBuild`, never `project.findProperty` from inside a task action"
    - "transient negative probe to prove an assertion is live rather than vacuous, then revert before committing"
    - "symbol anchors (`the excludeHeavyTests filter block inside tasks.test`) instead of `build.gradle.kts:NNN-NNN` line citations, which have gone stale three times in this phase"
    - "per-test outcomes read from build/test-results/test/TEST-*.xml, never scraped from console output"
key-files:
  created:
    - .planning/phases/20-mcp-access-control-correctness/20-10-SUMMARY.md
  modified:
    - build.gradle.kts
    - src/test/kotlin/com/six2dez/burp/aiagent/mcp/McpBuildFlagsVersionTest.kt
    - src/test/kotlin/com/six2dez/burp/aiagent/mcp/McpAccessControlExternalPipelineTest.kt
    - docs/mcp-hardening.md
decisions:
  - "An absent `storeBuild.expected` (running the class from an IDE, outside Gradle) maps to `false`, matching the no-flag build. Deliberate, and recorded as such in a code comment so a future reader does not read it as an oversight."
  - "The property is scoped to `tasks.test` only, not `tasks.withType<Test>`. `nightlyRegressionTest` filters to the five heavy-suffix globs and never runs the BuildFlags test, so broadening the scope would change that task's inputs for no benefit."
  - "WR-02 is fixed in the DOC, not the code. The foreign-`Origin` CORS-before-gate behaviour is left as-is: `McpAccessControlPlugin.kt` belongs to sibling plan 20-08 and the code side was outside this gap's scope."
  - "The protocol assertion was proven live with a transient `Protocol.HTTP_1_1` probe (observed `h2`) rather than accepted on a green run alone — a green assertion is exactly what 'coverage-by-accident' also looks like."
  - "Two stale `build.gradle.kts` line citations were replaced with symbol anchors, including one in McpAccessControlExternalPipelineTest that this plan's own +6-line build edit would otherwise have re-invalidated."
metrics:
  duration: ~35 min
  tasks: 3
  commits: 4
  tests-added: 0
  completed: 2026-08-10
---

# Phase 20 Plan 10: Build Path, Runbook Accuracy and the SC3 HTTP/2 Contract Summary

`./gradlew test -PstoreBuild=true` exits 0 again — the store-build test now asserts that the generated
flag **tracks** the Gradle property instead of asserting a literal `false` — the hardening runbook no
longer promises an audit record that the code provably never writes for a foreign `Origin`, and SC3's
HTTP/2 half is pinned by a `response.protocol` assertion that was proven to fail when the transport
changes.

## What Was Built

### Task 1 — the store-build seam (gap 2 / WR-08)

`McpBuildFlagsVersionTest.storeBuild_flagStillGenerated` asserted `assertFalse(BuildFlags.STORE_BUILD)`
with the message "STORE_BUILD must default to false when -PstoreBuild is not passed" — a fact a test
cannot know. `build.gradle.kts`'s `tasks.test` now carries the already-resolved `storeBuild` `Boolean`
into the test JVM as `storeBuild.expected`, and the test asserts the generated constant equals it.

**Observed `BuildFlags.STORE_BUILD` under each invocation** (read from
`build/generated/buildflags/com/six2dez/burp/aiagent/BuildFlags.kt`, not inferred):

| Invocation | `BuildFlags.STORE_BUILD` | `storeBuild.expected` | Class result | Full-suite result |
|---|---|---|---|---|
| `./gradlew test -PstoreBuild=true` | `true` | `true` | 2 tests, 0 failures | **594 tests, 0 failures, 0 errors, exit 0** |
| `./gradlew test` (no flag) | `false` | `false` | 2 tests, 0 failures | 594 tests, 0 failures, 0 errors, exit 0 |

The seam was genuinely exercised in both directions: `true` was observed, so a run that passes without
ever seeing `true` cannot be mistaken for proof here.

**RED was real.** Before the `build.gradle.kts` wiring, the rewritten test failed under the flag with
`generated BuildFlags.STORE_BUILD must track the -PstoreBuild Gradle property (storeBuild.expected=false)`
— 2 tests, 1 failure, exit 1. Committed as a separate `test(20-10)` commit before the `fix(20-10)` that
turns it green.

Blast radius confirmed as the plan stated: only this test asserts `STORE_BUILD`;
`McpToolCatalog.available(storeBuild: Boolean = BuildFlags.STORE_BUILD)` is the sole `src/main` reader and
every test passes the argument explicitly.

### Task 2 — the runbook claim the code contradicts (WR-02)

`docs/mcp-hardening.md` Verification item 2 ended with one unconditional clause: "the reason is recorded
in Burp's Output tab and, when audit logging is enabled, in the audit log" — applied to all four
local-mode 403 vectors. For a foreign `Origin` that is false: Ktor's CORS plugin answers a disallowed
origin with 403 and commits the response in the same `Plugins` phase, before the gate, and the gate's
`if (call.response.isCommitted) return@onCall` guard means `onBlocked` never fires and no
`mcp_transport_blocked` event is emitted.

The recording claim is now split in two, as committed:

- Bullet 1 keeps the original guarantee, scoped: "For the external-mode `401`s, and for the local-mode
  foreign `Host`, foreign `Referer` and browser `User-Agent`-with-no-`Origin` cases, the reason is
  recorded in Burp's Output tab and, when audit logging is enabled, in the audit log."
- Bullet 2 states the exception: "A foreign `Origin` is the exception: that denial is **not recorded** in
  the Output tab or in the audit log, and its absence there is expected. Ktor's own CORS plugin answers a
  disallowed origin with `403` and commits the response before the extension's access-control gate
  evaluates the request, so the gate never sees the request and emits nothing for it. The `403` status is
  therefore the only observable for this vector — a missing Output-tab line or audit entry here is not
  evidence of a broken audit trail. To confirm the recording path itself is healthy, exercise one of the
  other three local-mode vectors instead."

Written for the operator running the checklist: the last sentence gives a way to distinguish "expected
silence for this vector" from "the audit trail is broken". Everything item 2 already got right is kept —
the 401/200 external behaviour, all four local-mode 403 cases, and D-08's bare-status-no-body design.
`git diff` is a **single hunk at item 2** (3 added, 1 removed); §External Access, §Credential Storage,
§Incident Response and Verification items 1, 3 and 4 are byte-identical. WR-11 (item 5's
anti-fingerprinting wording) was out of scope and left alone.

### Task 3 — SC3's HTTP/2 half becomes a contract

`message_withValidBearer_reachesHandlerAndCarriesSecurityHeaders` now asserts
`response.protocol == Protocol.HTTP_2` **before** the 400 and the four header assertions, so an ALPN
regression reports as a protocol failure rather than as a confusing header failure.

**Observed protocol: `h2`.** Established by a transient negative probe rather than by a green run — the
assertion was temporarily flipped to `Protocol.HTTP_1_1` and produced
`expected: <http/1.1> but was: <h2>`, then reverted before committing. That step matters here: a passing
`assertEquals` is indistinguishable from coverage-by-accident, which is the exact defect this task
closes.

Test count unchanged at **8, 0 failures**. Every pre-existing assertion in the class is byte-identical; no
protocol assertion was added to the 401 tests, the health test, or the blank-token tests.

## Verification Results

| Gate | Result |
|---|---|
| `./gradlew test -PstoreBuild=true` (gap-2 gate) | exit 0 — 594 tests, 0 failures, 0 errors; `STORE_BUILD` observed `true` |
| `./gradlew test detekt ktlintCheck` | exit 0 — 594 tests, 0 failures, 0 errors across 99 result XMLs |
| `git diff --stat detekt-baseline.xml` | empty (QUAL-07 — no baseline growth) |
| `McpBuildFlagsVersionTest`, with / without `-PexcludeHeavyTests=true` | 2 / 2 tests — still in the fast PR gate |
| `McpAccessControlExternalPipelineTest`, with / without `-PexcludeHeavyTests=true` | 8 / 8 tests — still in the fast PR gate |
| Files changed vs base `b9ee87a` | exactly the four `files_modified`; no `src/main` change |
| `McpTestServerSupport.kt` (sibling 20-08 owns it) | not edited |
| `git status --porcelain` | clean |

All Gradle invocations used `JAVA_HOME=$(/usr/libexec/java_home -v 21)`; per-test outcomes were read from
`build/test-results/test/TEST-*.xml`.

**Scope limit of the gap-2 proof, stated plainly:** the `-PstoreBuild=true` gate above passed in a
worktree containing neither 20-07's nor 20-08's changes. The orchestrator's post-merge re-run on the
merged tree is the real proof that gap 2 is closed for the shipped artifact.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Stale `build.gradle.kts` line citation in `McpAccessControlExternalPipelineTest`'s KDoc**

- **Found during:** Task 3
- **Issue:** The class KDoc cited the exclusion-glob block as "`:145-157`, actually `:154-166`". The real
  block was at 158-166 before this plan, so both numbers were already wrong — and Task 1's six added lines
  in `build.gradle.kts` shifted it again, to 164-172. The plan only asked for the citation in
  `McpBuildFlagsVersionTest` to be fixed, but leaving this one meant knowingly shipping a citation that my
  own commit in this plan had just re-invalidated. This citation shape has cost time three times in this
  phase.
- **Fix:** Replaced the parenthetical line range with the symbol anchor — "the `excludeHeavyTests` filter
  block inside `tasks.test` in `build.gradle.kts`" — and recorded that both prior numbers went stale, so a
  future reader does not reintroduce a number.
- **Files modified:** `src/test/kotlin/com/six2dez/burp/aiagent/mcp/McpAccessControlExternalPipelineTest.kt`
- **Commit:** `1d13ee7`
- **Scope note:** comment-only, inside a file this plan already owns and edits. No assertion touched.

### Additional Verification Beyond the Plan

Not deviations — the plan's `<done>` criteria were met as written, plus:

- **A transient negative probe on the protocol assertion.** The plan required the assertion and the
  observed value. A green `assertEquals(Protocol.HTTP_2, …)` alone would not distinguish a live assertion
  from a vacuous one, which is precisely the failure mode being fixed, so the assertion was flipped, run,
  observed to fail with the actual protocol (`h2`), and reverted.
- **`-PexcludeHeavyTests=true` count parity** was measured for both touched test classes, confirming
  neither is silently skipped in the fast PR gate.

### Not Done, Deliberately

- **The foreign-`Origin` code path is unchanged.** Only the doc was corrected. The CORS-before-gate
  ordering is real behaviour; changing it would touch `McpAccessControlPlugin.kt`, which belongs to sibling
  plan 20-08 in this wave.
- **WR-11** (§External Access item 5's anti-fingerprinting claim) — a different finding, outside the
  maintainer's scope for this gap closure.
- **Locked decisions D-01, D-02, D-04, D-08** were not re-litigated.

## Known Stubs

None. No placeholder values, empty returns, or TODO markers were introduced.

## Threat Flags

None. No new security-relevant surface: no `src/main` change, no new route, no auth path change, no schema
change, and no dependency added, removed or version-changed. `T-20-10-05` (the added system property
invalidating the test task's cache on a flag flip) remains an accepted, desirable behaviour — a flag flip
*must* re-run the tests.

## Commits

| Commit | Type | Description |
|---|---|---|
| `9e2d786` | test | RED — store-build test asserts the Gradle-property seam; fails under `-PstoreBuild=true` until wired |
| `67264d0` | fix | GREEN — `tasks.test` passes the resolved `-PstoreBuild` into the test JVM as `storeBuild.expected` |
| `dc028eb` | docs | Verification item 2 splits the recording claim; foreign `Origin` is **not recorded** and CORS is named |
| `1d13ee7` | test | `response.protocol == Protocol.HTTP_2` pins SC3's HTTP/2 half; stale line citations dropped |

No REFACTOR commit for Task 1: the GREEN implementation is a single `systemProperty` line and needed no
cleanup.

## For the Next Phase

- `storeBuild.expected` is now the seam for anything else generated from a Gradle property. If a future
  flag joins `BuildFlags`, give it the same treatment rather than asserting a literal.
- The BApp Store artifact path is testable again — worth running `./gradlew test -PstoreBuild=true` before
  the next submission-#231 touch, since that invocation was silently broken for the whole of this phase.
- If the foreign-`Origin` denial should actually be recorded, that is a code change in the access-control
  gate (ordering relative to Ktor's CORS plugin), not a doc change, and the runbook text above will need
  updating in the same commit.

## Self-Check: PASSED

Every claim above was re-verified against disk after the SUMMARY was written:

- All four `files_modified` plus this SUMMARY exist; `git diff --name-only b9ee87a..HEAD` lists exactly
  those five paths and nothing else.
- `McpTestServerSupport.kt` exists and is absent from the diff — sibling 20-08's file was not edited.
- `storeBuild.expected` present in `build.gradle.kts` (1) and `McpBuildFlagsVersionTest.kt` (3) — the
  key link is wired at both ends.
- `Protocol.HTTP_2` present once in `McpAccessControlExternalPipelineTest.kt`.
- `assertFalse` no longer appears in `McpBuildFlagsVersionTest.kt` (0 occurrences) — the fixed-value
  assertion is gone, and its import with it.
- All five commit hashes (`9e2d786`, `67264d0`, `dc028eb`, `1d13ee7`, `49816a8`) resolve in `git log`.
- `git status --porcelain` clean; `git diff --stat detekt-baseline.xml` empty.

STATE.md and ROADMAP.md were deliberately NOT touched — the orchestrator owns those writes after all
worktree agents in this wave complete.
