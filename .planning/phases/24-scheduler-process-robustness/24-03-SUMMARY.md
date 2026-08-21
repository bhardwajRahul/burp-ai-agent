---
phase: 24-scheduler-process-robustness
plan: 03
subsystem: backends
tags: [kotlin, jvm, concurrency, subprocess-io, structural-test, junit5, utf-16]

# Dependency graph
requires:
  - phase: 24-scheduler-process-robustness
    plan: 01
    provides: "`Defaults.MAX_CLI_OUTPUT_CHARS` and `Defaults.CLI_OUTPUT_TRUNCATION_MARKER`, plus the `tasks.test` input `mainSourceTreeStructuralInputs` (`inputs.dir(\"src/main/kotlin\")`) without which REL-07-D is cache-served and never runs"
  - phase: 23-edt-confinement-ui-responsiveness
    provides: "the comment-stripping `codeLinesOf` source reader and the ledger-message assertion style from `SettingsPersistQueueTest`"
provides:
  - "`backends/cli/CliOutputBuffer.kt` — `internal class` with one private monitor taken by `appendLine`, `snapshot`, `truncated` and `length`; constructor-injected cap defaulting to `Defaults.MAX_CLI_OUTPUT_CHARS`"
  - "Head retention with a conditional truncation marker — both `CliBackend` error paths keep their bytes, a legitimate answer round-trips byte-identically"
  - "A-EDGE-5 resolution in code: a cut that would split a UTF-16 surrogate pair moves one character earlier"
  - "All four `CliBackend` stdout-capture call sites converted (:209 declaration, :220 append, :252/:264/:275 reads)"
  - "`CliOutputBufferTest` — 8 tests; the REL-07-D structural gate turns a reintroduced unsynchronised accumulator, a dropped read-site conversion, or a 2000-character head on the success path into a red build"
affects: [24-04, any future change to CliBackend's non-interactive capture region]

actuals:
  tokens: 7061
  tasks: 3
  commits: 4

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "A bounded accumulator as a small `internal class` with the lock inside, rather than a `synchronized` block copy-pasted at each call site"
    - "Cap injected through a constructor parameter defaulting to a `Defaults` constant, so tests drive a small cap while production takes the generous one (the `CircuitBreaker.nowProvider` seam shape)"

key-files:
  created:
    - src/main/kotlin/com/six2dez/burp/aiagent/backends/cli/CliOutputBuffer.kt
    - src/test/kotlin/com/six2dez/burp/aiagent/backends/cli/CliOutputBufferTest.kt
  modified:
    - src/main/kotlin/com/six2dez/burp/aiagent/backends/cli/CliBackend.kt

key-decisions:
  - "Head retention, not tail — `String.take(n)` returns the FIRST n characters, so both CliBackend error paths already show the head; a tail buffer would have silently rewritten `buildTimeoutMessage`'s output and forced a rewrite of `CliTimeoutMessageTest` for no requirement"
  - "The truncation marker is appended by `snapshot()` ONLY when `truncatedFlag` is true; that conditional is what makes REL-07-C's byte-identical round-trip claim true rather than vacuous"
  - "The cut-back-one rule for surrogate pairs is implemented, not just documented — `isHighSurrogate()` is checked on the last character that would survive"
  - "The KDoc names the cap as `MAX_CLI_OUTPUT_CHARS` unqualified, because the acceptance criterion pins `grep -c 'Defaults.MAX_CLI_OUTPUT_CHARS'` to exactly 1 line and the constructor default is that line"
  - "Red-before-green for REL-07-D was proved with a single-file `git checkout <ref> -- <path>` instead of the plan's `git stash push`, because `refs/stash` is shared across every worktree"
  - "`take(2000)` in `CliBackend.kt` is 4 raw / 3 comment-stripped lines, not the 2 the plan's acceptance criterion predicted; the assertion was made precise instead of being forced to match a wrong number"

patterns-established:
  - "Subprocess output capture goes through `CliOutputBuffer`: construct with no argument for the production cap, `appendLine` on the reader thread, `snapshot()` on every read path. Never share a bare accumulator across a reader thread and a `join`-bounded read."

requirements-completed: []

coverage:
  - id: D1
    description: "Concurrent appends from eight threads and concurrent snapshot reads from two others produce no escaped throwable and no torn line; all 4000 lines survive whole"
    requirement: REL-07
    verification:
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/backends/cli/CliOutputBufferTest.kt#concurrentAppendsAndSnapshotReadsNeverTearALineOrThrow"
        status: pass
    human_judgment: false
  - id: D2
    description: "A legitimate ~57 000-character multi-line model answer round-trips byte-identically at the production cap with truncated == false — the anti-corruption assertion that catches a 2000-character cap"
    requirement: REL-07
    verification:
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/backends/cli/CliOutputBufferTest.kt#aFiftyThousandCharacterAnswerRoundTripsByteIdentically"
        status: pass
    human_judgment: false
  - id: D3
    description: "Appending past the cap retains at most maxChars, retains the HEAD of what was appended, sets truncated, and ends the snapshot with the explicit English truncation marker"
    requirement: REL-07
    verification:
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/backends/cli/CliOutputBufferTest.kt#appendingPastTheCapRetainsTheHeadFlagsTruncationAndMarksTheSnapshot"
        status: pass
    human_judgment: false
  - id: D4
    description: "A-EDGE-4 boundary: maxChars - 1 and exactly maxChars are retained whole with truncated == false; the first character past maxChars sets truncated and is not retained"
    requirement: REL-07
    verification:
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/backends/cli/CliOutputBufferTest.kt#theCapBoundaryBehavesAtOneBelowExactlyAtAndOneAbove"
        status: pass
    human_judgment: false
  - id: D5
    description: "A-EDGE-4 empty: a buffer never appended to snapshots as the empty string with length 0 and truncated == false; appending the empty line adds exactly one newline"
    requirement: REL-07
    verification:
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/backends/cli/CliOutputBufferTest.kt#anUntouchedBufferIsEmptyAndAnEmptyLineAddsExactlyOneNewline"
        status: pass
    human_judgment: false
  - id: D6
    description: "A-EDGE-5 precision: a cut positioned between the halves of a UTF-16 surrogate pair moves one character earlier, so the snapshot can never end with an unpaired surrogate"
    requirement: REL-07
    verification:
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/backends/cli/CliOutputBufferTest.kt#aCutThatWouldSplitASurrogatePairMovesOneCharacterEarlier"
        status: pass
    human_judgment: false
  - id: D7
    description: "The truncation marker is absent from every snapshot where nothing was truncated — the conditional that keeps D2's byte-identical claim true"
    requirement: REL-07
    verification:
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/backends/cli/CliOutputBufferTest.kt#theTruncationMarkerIsAbsentWheneverNothingWasTruncated"
        status: pass
    human_judgment: false
  - id: D8
    description: "REL-07-D: reintroducing an unsynchronised accumulator in CliBackend's capture region, dropping one of the three converted read sites, dropping the append conversion, or moving the 2000-character head onto the success path each turns the build red, and the guard cannot be served from a stale Gradle cache"
    requirement: REL-07
    verification:
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/backends/cli/CliOutputBufferTest.kt#theCliBackendCaptureRegionCannotRegressToAnUnsynchronisedAccumulator"
        status: pass
      - kind: other
        ref: "manual cache probe: baseline `:test UP-TO-DATE` -> append one comment line to CliBackend.kt -> `> Task :test` executed"
        status: pass
    human_judgment: false
  - id: D9
    description: "Both CLI error paths keep producing the same first 2000 characters they produced before the swap — CliTimeoutMessageTest needed no edit"
    requirement: REL-07
    verification:
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/backends/cli/CliTimeoutMessageTest.kt (regression control, unmodified)"
        status: pass
    human_judgment: false

# Metrics
duration: 29min
completed: 2026-08-21
status: complete
---

# Phase 24 Plan 03: Bounded Thread-Safe CLI Output Capture Summary

**`CliBackend`'s stdout capture moved from a bare unsynchronised accumulator onto `CliOutputBuffer` — one private monitor taken by every append and every read, a 256 Ki-character head-retaining cap with a conditional truncation marker, a surrogate-safe cut, and a structural gate that makes the old shape a red build.**

## Performance

- **Duration:** 29 min
- **Started:** 2026-08-21T14:05:00Z
- **Completed:** 2026-08-21T14:34:00Z
- **Tasks:** 3
- **Files modified:** 3 (2 created, 1 modified)

## Accomplishments

- **SC3 is closed at the mechanism level.** The daemon `burp-ai-agent-cli-reader` thread and the
  timeout path no longer share an unsynchronised accumulator across a `readerThread.join(2000)` that
  can expire mid-append. `appendLine`, `snapshot`, `truncated` and `length` all take the same
  `private val lock = Any()` — four `synchronized(lock)` sites, one per public member.
- **SC4 is closed without stepping on the trap the whole plan was built around.** The cap is
  `Defaults.MAX_CLI_OUTPUT_CHARS = 262_144`, not 2000. `CliBackend.kt:275` reads the FULL accumulated
  value and that value IS the model's answer; only `:252` and `:264` take a 2000-character head.
  `aFiftyThousandCharacterAnswerRoundTripsByteIdentically` is the assertion that would have caught a
  2000-character cap, and it drives the **production default**, not an injected one.
- **The truncation marker is conditional, and that conditional is load-bearing.** An unconditional
  suffix would have appended itself to every legitimate CLI answer and made the byte-identical claim
  false. The marker-suppression test exists precisely to keep that honest.
- **A-EDGE-5 is resolved in code, not only in prose.** The cap counts UTF-16 chars, so a cut at
  exactly `maxChars` can split a surrogate pair. `appendLine` checks `isHighSurrogate()` on the last
  character that would survive and cuts one earlier. Asserted with an astral-plane fixture (U+1D11E
  G clef) positioned so the naive cut lands between the two halves.
- **Both error paths are byte-identical.** `.trim().take(2000)` is untouched at both sites;
  `CliTimeoutMessageTest`, `CopilotCommandBuilderTest` and `CliBackendTempFileTest` are green and
  unedited.
- **The old shape cannot come back quietly.** `CliOutputBufferTest`'s structural test reads
  `CliBackend.kt` comment-stripped and pins six counts with ledger failure messages, and the
  cache-staleness probe shows it actually re-runs on a main-source edit.
- **The second CLI reader is untouched.** `outputQueue` / `lastLines` at `:605-622` and the guarded
  read at `:731-734` are the in-tree precedent this plan formalises, not a target. `git diff -U0`
  over `CliBackend.kt` shows exactly four one-line hunks, at 209, 252, 264 and 275.

## Task Commits

1. **Task 1 (TDD): `CliOutputBuffer` — one lock, one cap, one place** — `a89d69d` (test, RED) → `f4d6fcf` (feat, GREEN)
2. **Task 2: Convert `CliBackend`'s four capture call sites** — `640ae95` (fix)
3. **Task 3: Pin the swap structurally (REL-07-D)** — `6dd1483` (test)

## Files Created/Modified

- `src/main/kotlin/com/six2dez/burp/aiagent/backends/cli/CliOutputBuffer.kt` (new, 95 lines) —
  `internal class CliOutputBuffer(private val maxChars: Int = Defaults.MAX_CLI_OUTPUT_CHARS)` with an
  `init { require(maxChars > 0) }` in the `CircuitBreaker` style. Class KDoc states the four mandated
  things: which threads race and that the hazard is the absent happens-before edge (**not** a claimed
  exception type, per RESEARCH assumption A2); why retention is the head; the cap's derivation; and a
  `Visibility:` paragraph.
- `src/test/kotlin/com/six2dez/burp/aiagent/backends/cli/CliOutputBufferTest.kt` (new, 386 lines) —
  8 tests, all pure JVM. No subprocess, no Swing, no elapsed-duration comparison.
- `src/main/kotlin/com/six2dez/burp/aiagent/backends/cli/CliBackend.kt` — four one-line changes.
  `:220` `rawOutput.appendLine(line)` needed **no edit at all**: the member name is identical on the
  new class, so it rebound cleanly. That was confirmed by compilation, not assumed.

## Decisions Made

### The KDoc names the cap unqualified, to satisfy both halves of a self-conflicting criterion

The plan's action mandates a KDoc that states the cap's derivation, and its acceptance criterion pins
`grep -c 'Defaults.MAX_CLI_OUTPUT_CHARS'` to exactly **1**. `grep -c` counts matching *lines*, so a
KDoc line saying `Defaults.MAX_CLI_OUTPUT_CHARS` plus the constructor default would return 2 and the
criterion would be unsatisfiable alongside its own mandated prose. Resolved by writing the KDoc
reference as ``MAX_CLI_OUTPUT_CHARS`` in `Defaults`` — the full derivation prose is preserved verbatim
(including "*approximately* eight times", the true ratio 8.192, and the note that a clean eightfold of
32 000 would be 256 000 while 262 144 is exactly eight times 32 768), and the qualified token appears
on exactly the one line that consumes it. The criterion's intent — the cap is taken from `Defaults`
once and never redeclared — is met literally.

### The comment strip is measurably load-bearing, not cosmetic

The plan predicted this and it is worth recording as a measurement rather than a claim:

```
grep -cE '[^A-Za-z_][0-9]{3,}' CliOutputBuffer.kt                    -> 5
grep -v '^\s*[/*]' CliOutputBuffer.kt | grep -cE '[^A-Za-z_][0-9]{3,}' -> 0
```

Five matches live entirely in the KDoc that the same task mandates (2000, 262 144, 32 000, 8.192,
131). An unfiltered scan would match the file's own required prose and make the no-magic-number
criterion unsatisfiable by construction. Zero numeric literals exist in code.

### `take(2000)` is 4 raw / 3 comment-stripped lines in `CliBackend.kt`, not 2

See deviation 1. The assertion was made **more** precise rather than being bent to a wrong number.

## Red-Before-Green Record

The distinction the plan insists on is reported accurately, because the two forms are not equal
evidence.

**REL-07-A, REL-07-B and REL-07-C — WEAK form. Not gates. Do not score them as such.**
All three drive the same non-existent `CliOutputBuffer` class, so the suite went red only by
**non-compilation**:

```
e: .../CliOutputBufferTest.kt:44:22 Unresolved reference 'CliOutputBuffer'.
e: .../CliOutputBufferTest.kt:116:22 Unresolved reference 'CliOutputBuffer'.
e: .../CliOutputBufferTest.kt:141:22 Unresolved reference 'CliOutputBuffer'.
... (8 total)
```

`24-VALIDATION.md` §Red-Before-Green Gate classifies this explicitly: a suite that cannot compile has
not exercised a bypass. It is committed as its own RED commit (`a89d69d`) so the ordering is
auditable, and it is reported as the weaker evidence it is. "No buffer class exists" is the *reason*
these are weak, not a reason they are strong.

**REL-07-D — STRONG form. This plan's only genuine gate.**
Reverting `CliBackend.kt` alone to its pre-fix content (`git checkout f4d6fcf -- <path>`) leaves a
**compiling** tree — the pre-fix file uses a bare accumulator and `.toString()`, and the new class is
simply unused — and the structural test fails on it:

```
CliOutputBufferTest > theCliBackendCaptureRegionCannotRegressToAnUnsynchronisedAccumulator() FAILED
    org.opentest4j.AssertionFailedError at CliOutputBufferTest.kt:278
    CliBackend ledger: the capture region must not declare an unsynchronised `StringBuilder` again.
    Reintroducing it restores the data race between the `burp-ai-agent-cli-reader` daemon thread and
    the timeout path, which reads after a `readerThread.join(2000)` that can expire while the reader
    is still appending — no happens-before edge, so the read can observe a partially published state
    (REL-07 / SC3, threat T-24-13). ==> expected: <0> but was: <1>
8 tests completed, 1 failed
```

Restoring the converted file returned the suite to green (`8 tests completed`, 0 failed).

## Cache-Staleness Record

```
# baseline
> Task :compileKotlin UP-TO-DATE
> Task :test UP-TO-DATE
# after appending one comment line to CliBackend.kt
> Task :compileKotlin
> Task :test
```

`:test` **executed** rather than reporting `UP-TO-DATE` or `FROM-CACHE`, so the structural gate is
genuinely re-evaluated on a main-source edit. The probe comment was removed
(`git checkout HEAD -- <path>`) and `git status --short` confirmed the path clean. The declaration
that makes this true, `mainSourceTreeStructuralInputs`, was **verified present** (`grep -c` returns 1)
and not modified — `git diff --stat build.gradle.kts` for this plan is empty, as the plan requires.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Plan/repo drift] `take(2000)` appears on 4 lines in `CliBackend.kt`, not 2**

- **Found during:** Task 2's acceptance checks, repeated in task 3's assertion design.
- **Issue:** two acceptance criteria assert `grep -c 'take(2000)' CliBackend.kt` returns **2**. The
  measured count is **4**, and it was 4 before this plan touched anything:

  | Line | Site | In REL-07 scope? |
  |------|------|------------------|
  | 252 | timeout path error head | yes — converted to `rawOutput.snapshot().trim().take(2000)` |
  | 264 | non-zero-exit error head | yes — converted the same way |
  | 783 | `buildExitError`'s head over the persistent-session `lastOutputTail()` | **no** — that reader is the already-bounded, already-guarded `lastLines` path the plan explicitly rules out of scope |
  | 843 | `buildTimeoutMessage`'s KDoc prose | **no** — a comment; stripped by the structural reader |

  Written literally, the criterion is unsatisfiable without either deleting an out-of-scope call site
  or weakening the read.
- **Fix:** the assertion was made *more* precise, not looser. The structural test pins
  `rawOutput.snapshot().trim().take(2000)` at exactly **2** — which is the criterion's actual intent,
  since a 2000-character head reaching the success path shows up there as a 3 — and separately pins
  the comment-stripped whole-file `take(2000)` at exactly **3**, with a message naming the third as
  `buildExitError`'s out-of-scope head. Both counts are equalities, so a new truncation anywhere in
  the file is red.
- **Files modified:** `src/test/kotlin/com/six2dez/burp/aiagent/backends/cli/CliOutputBufferTest.kt`
- **Verification:** `grep -c 'rawOutput.snapshot().trim().take(2000)'` returns 2; the suite is green
  with the fix and red without it.
- **Committed in:** `6dd1483`.

### Instructed-command substitutions

**2. [Prohibition compliance] `git stash push <path>` replaced with `git checkout <ref> -- <path>`**

- **Found during:** Task 3's red-before-green acceptance criterion, which literally instructs
  `git stash push src/.../CliBackend.kt` … `git stash pop`.
- **Issue:** this plan runs inside a linked git worktree. `refs/stash` lives in the **parent** `.git/`
  and is shared across every worktree, so `git stash pop` can apply a sibling worktree's WIP into an
  isolated tree. The executor contract prohibits `git stash` in worktree mode outright, and plan 24-01
  hit and recorded the same substitution.
- **Fix:** `git checkout f4d6fcf -- src/main/kotlin/.../CliBackend.kt`, run the suite, then
  `git checkout HEAD -- <same path>`. Both are single-file operations that never touch `refs/stash`.
- **Verification:** the red output above, then `git status --short` clean of that path, then green.
  The criterion's intent — observe the assertion fail against the real pre-fix file, restore, observe
  green — was met in full, and in the **stronger** shape: the reverted tree still compiles, so this is
  a genuine failing assertion rather than a compile error.

**3. [Plan/repo drift, no action needed] `gradle/libs.versions.toml` does not exist**

- **Found during:** tasks 2 and 3, whose criteria assert `git diff --stat gradle/libs.versions.toml`
  is empty.
- **Issue:** this repository has no version catalog; dependencies are declared inline in
  `build.gradle.kts`. The command errors with `fatal: no such path in the working tree`. Same finding
  as 24-01 deviation 5.
- **Resolution:** T-24-SC is discharged the stronger way — this plan modified **no build file at
  all**. `git diff --stat build.gradle.kts detekt-baseline.xml` is empty, and the only files it
  touched are two Kotlin sources and one Kotlin test. No package-manager install ran.

### `actuals.tokens` scale — flagged, not quietly reconciled

`actuals.tokens: 7061` above is `estimateTokens` (chars / 4) over this plan's realized diff
(28 245 characters across `CliOutputBuffer.kt`, `CliOutputBufferTest.kt` and `CliBackend.kt`), which
is the scale the executor contract specifies.

**It is not the same scale `24-01-SUMMARY.md` used.** 24-01 recorded `tokens: 30587` against a
realized `src/` + `build.gradle.kts` diff of **32 796 characters** — i.e. approximately raw
characters, not characters over four. Rounding this plan's number up to match would make the two
comparable but both wrong, and would corrupt every projection built on them. Recording it correctly
and naming the discrepancy is the honest option. A calibrator reconciling Phase 24 should treat
24-01's figure as raw-chars and divide by 4 (≈ 8 200) before comparing with the 66 000 / 78 000 plan
estimates, both of which appear to be on a third scale again.

**Total deviations:** 1 auto-fixed (Rule 1, plan/repo drift on a grep count), plus 2 recorded
command/repo substitutions that changed no behaviour, plus 1 flagged metrics-scale discrepancy.
**Impact on plan:** none on scope or design. No threshold, no cap value, no call site and no
assertion's *intent* was altered; deviation 1 strengthened an assertion rather than weakening it.

## Issues Encountered

- **One transient test failure on the first `test -PexcludeHeavyTests=true` run** immediately after
  a cache-invalidating checkout. It was **not captured by name** — the result XML was overwritten by
  the following run before it was inspected, which is a process miss on my part and is recorded as
  such rather than glossed. A subsequent `cleanTest test -PexcludeHeavyTests=true` full clean run
  and an unfiltered `test detekt ktlintCheck` run were both **BUILD SUCCESSFUL** with zero failing
  result files, so it did not reproduce. The known
  `RedactionTest.windowedScanRedactsJsonPairAcrossEveryBoundaryAlignment` wall-clock flake against
  `SafeRegex`'s 50 ms deadline — already logged to this phase's `deferred-items.md` by plan 24-01 and
  seen there under identical circumstances — is the probable identity, but that is **inference, not
  evidence**, and it is stated as inference. No file this plan touched is involved either way.
- No new deferred items. `deferred-items.md` is unchanged by this plan.

## Threat Flags

**No new attack surface was introduced by this plan.** No network endpoint, no auth path, no new file
access pattern, no schema change, and no trust boundary was added or moved. The plan strictly
*narrows* an existing boundary: bytes that previously crossed from a subprocess into the extension
heap without limit and without a memory barrier now cross through one monitored, capped accumulator.
Nothing gained a public member — `CliOutputBuffer` is `internal`, the same visibility level
`buildTimeoutMessage` and `buildCopilotCommand` already use for headless testability, and
`CliBackend`'s own surface is unchanged (all four edits are inside one private connection class's
method body).

The plan's dispositioned threats are discharged as follows:

| Threat ID | Severity | Disposition | Status | Evidence |
|-----------|----------|-------------|--------|----------|
| T-24-04 | high | mitigate | **discharged** | Hard cap applied at append time, not at read time — a hostile CLI's bytes are dropped before they are retained, so the `OutOfMemoryError` path is closed at the ingest point rather than after accumulation. `appendingPastTheCapRetainsTheHeadFlagsTruncationAndMarksTheSnapshot` and the three-point boundary test assert retention never exceeds `maxChars`. |
| T-24-13 | high | mitigate | **discharged** | One `private val lock = Any()` taken by all four public members (`grep -c 'synchronized(lock)'` = 4). Asserted behaviourally by `concurrentAppendsAndSnapshotReadsNeverTearALineOrThrow` (8 writers vs 2 readers, 4000 lines, every line whole) and pinned structurally by REL-07-D, whose failure message names this threat. |
| T-24-05 | medium | mitigate | **discharged** | The 262 144 ceiling, head retention that leaves both error paths byte-identical, a truncation marker that appears **only** when the cap was hit, and REL-07-C's byte-identical round-trip at the production default. The marker-suppression test is what stops the mitigation from becoming the corruption. |
| T-24-14 | medium | mitigate | **discharged, residual named** | `stripAnsiCodes`'s four raw-`Regex` passes now run over an input bounded at 256 Ki chars, so peak transient heap is bounded at roughly five times that (~2.5 MB). That those passes use raw `Regex` rather than the project's `SafeRegex` deadline wrapper is **not fixed** — it is out of this phase's scope — and is named explicitly in `CliOutputBuffer`'s KDoc so the next reader does not assume a deadline exists. |
| T-24-SC | high | mitigate | **discharged** | No package-manager install ran. No build file was modified at all: `git diff --stat build.gradle.kts detekt-baseline.xml` is empty and the plan touched only two Kotlin sources and one Kotlin test. (`gradle/libs.versions.toml` does not exist in this repo — see deviation 3.) |

No `threat_flag:` entries. Nothing in this plan warrants a new register row.

## Verification Record

| Check | Result |
|-------|--------|
| `./gradlew test detekt ktlintCheck` (unfiltered) | **green** — 813 tests across 118 suites, 0 failures |
| `./gradlew cleanTest test -PexcludeHeavyTests=true` (PR-gate equivalent, clean) | **green** — 111 suites, 0 failures |
| `CliOutputBufferTest` present in the `-PexcludeHeavyTests=true` report | **yes** — the suite name matches none of the five excluded suffixes |
| `CliOutputBufferTest` | 8 tests, 0 failures, 0 skipped |
| `CliTimeoutMessageTest`, `CopilotCommandBuilderTest`, `CliBackendTempFileTest` | **green and unedited** — the error-path bytes did not change |
| `grep -c 'synchronized(lock)' CliOutputBuffer.kt` | 4 (≥ 4 required — one per public member) |
| `grep -c 'Defaults.MAX_CLI_OUTPUT_CHARS' CliOutputBuffer.kt` | 1 |
| `grep -v '^\s*[/*]' CliOutputBuffer.kt \| grep -cE '[^A-Za-z_][0-9]{3,}'` | 0 (5 unstripped — the strip is load-bearing) |
| `grep -c 'isHighSurrogate' CliOutputBuffer.kt` | 1 |
| `grep -c 'System.currentTimeMillis\|System.nanoTime' CliOutputBufferTest.kt` | 0 |
| `grep -c 'assertTimeoutPreemptively' CliOutputBufferTest.kt` | 3 (≥ 1 required) |
| `grep -c 'ProcessBuilder\|Runtime.getRuntime().exec' CliOutputBufferTest.kt` | 0 |
| `grep -c 'rawOutput.snapshot()' CliBackend.kt` | 3 |
| `grep -c 'rawOutput.appendLine(' CliBackend.kt` | 1 |
| `grep -c 'CliOutputBuffer()' CliBackend.kt` | 1 |
| `grep -c 'StringBuilder' CliBackend.kt` | 0 |
| `grep -c 'rawOutput.snapshot().trim().take(2000)' CliBackend.kt` | 2 (both error heads) |
| `grep -c 'take(2000)' CliBackend.kt` | 4 raw / 3 comment-stripped — unchanged from pre-plan; see deviation 1 |
| `git diff -U0 CliBackend.kt` near lines 605-622 and 728-736 | **no change** — four one-line hunks only, at 209, 252, 264, 275 |
| `grep -c 'mainSourceTreeStructuralInputs' build.gradle.kts` | 1, and `git diff --stat build.gradle.kts` empty |
| `git diff --stat detekt-baseline.xml` | empty |

## Next Phase Readiness

- **`CliBackend.kt` is now guarded by a structural gate that plan 24-04 will edit the same file
  under.** 24-04 removes both `deleteOnExit(` call sites in that file. Its edits are at `:116-123` and
  `:135-138`, far from the capture region, and none of REL-07-D's six counts is sensitive to them —
  but 24-04's own structural assertion and this one now both read `CliBackend.kt` comment-stripped
  from disk, so a merge that drops either file's changes will surface as a count mismatch rather than
  silently.
- **Do not write the tokens `CliOutputBuffer()`, `rawOutput.snapshot()`, `rawOutput.appendLine(` or
  `take(2000)` in a same-line trailing comment inside `CliBackend.kt`.** The comment strip is
  line-leading only, which is the one form naive stripping misses; a trailing comment would inflate a
  count and turn the gate red for the wrong reason.
- **`REL-07` in `REQUIREMENTS.md` is intentionally left unchecked.** Its wording covers three separate
  defects — the CLI buffer (closed here), `deleteOnExit()` accumulation (plan 24-04) and unbounded
  `newCachedThreadPool()` (plans 24-02 and 24-05). Only the first is done. Marking it complete here
  would overclaim, exactly as 24-01 declined to check REL-06.
- **`STATE.md` and `ROADMAP.md` were deliberately NOT edited by this executor.** This plan ran as one
  of two parallel wave-2 worktrees; the orchestrator is the single writer for those files after the
  merge. Wave 1's executor edited `STATE.md` and produced a merge conflict — not repeated here.

## Self-Check: PASSED

Files verified present on disk:

- `src/main/kotlin/com/six2dez/burp/aiagent/backends/cli/CliOutputBuffer.kt` — FOUND
- `src/test/kotlin/com/six2dez/burp/aiagent/backends/cli/CliOutputBufferTest.kt` — FOUND
- `src/main/kotlin/com/six2dez/burp/aiagent/backends/cli/CliBackend.kt` — FOUND (modified)
- `.planning/phases/24-scheduler-process-robustness/24-03-SUMMARY.md` — FOUND

Commits verified in `git log`: `a89d69d`, `f4d6fcf`, `640ae95`, `6dd1483` — all FOUND.

---
*Phase: 24-scheduler-process-robustness*
*Completed: 2026-08-21*
