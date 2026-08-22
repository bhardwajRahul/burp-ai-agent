---
phase: 26-coverage-static-analysis-debt-docs
plan: 01
subsystem: testing
tags: [kotlin, cli-backend, shell-quoting, jacoco, detekt, command-injection]

requires:
  - phase: 25-mcp-ssrf-hardening
    provides: post-Phase-25 tree that the measured coverage baseline (215/699 at 4f0ebd7) was taken against
provides:
  - "Allowlist shell quoting for every CLI argument that reaches `/bin/sh -c` on the PTY path"
  - "`shellEscape` and `buildPtyCommand` as top-level `internal` helpers, assertable without reflection"
  - "Five pure CLI command/history/ANSI helpers widened to `internal` and covered behaviourally"
  - "`backends.cli` line coverage 30.76% -> 38.23%; `CliBackend.kt` 20.30% -> 28.95%"
affects: [cli-backend, security-hardening, coverage-floors]

actuals:
  tokens: 24000
  tasks: 2
  commits: 3

tech-stack:
  added: []
  patterns:
    - "Defaulted environment parameter (`osName`, `windows`) as the testability seam for platform-branching helpers, in place of mutating a global system property"
    - "Allowlist rather than denylist at the argv-to-shell boundary"

key-files:
  created:
    - src/test/kotlin/com/six2dez/burp/aiagent/backends/cli/ShellEscapeTest.kt
    - src/test/kotlin/com/six2dez/burp/aiagent/backends/cli/CliCommandHelpersTest.kt
  modified:
    - src/main/kotlin/com/six2dez/burp/aiagent/backends/cli/CliBackend.kt

key-decisions:
  - "Quote by allowlist (`A-Za-z0-9._/-`), not by metacharacter denylist — a denylist at a shell boundary can always be under-enumerated, which is exactly how `foo;id` and `$(cmd)` got through"
  - "Per-character membership test over a `const val`, not a `Regex` — this runs per CLI argument and adds no backtracking surface"
  - "Reach the Windows branch of `normalizeWindowsCommand` through a defaulted `windows` parameter rather than by mutating global `os.name`, which would leak into every other test sharing the JVM"
  - "Fix both detekt findings in source rather than in the baseline — `isWindows()` and `resolveWindowsNpmShim()` became properties, which is behaviour-preserving because both read process-lifetime-constant inputs"
  - "Do NOT cover `resolveCommand`, `isWindowsOs` or `windowsNpmShimDirs`: they read PATH, the filesystem and JVM start-up state, so a test over them asserts the developer's machine"

patterns-established:
  - "Mutation probe as coverage evidence: a new suite is only credible once a deliberate production mutation makes it fail"
  - "KDoc `Visibility: internal so <Test> can call it directly` on every widened helper, matching the existing buildTimeoutMessage / buildCopilotCommand house style"

requirements-completed: [QUAL-06]

coverage:
  - id: D1
    description: "`shellEscape` quotes by allowlist, so `foo;id` and `$(cmd)` reach `sh -c` single-quoted instead of as shell syntax"
    requirement: QUAL-06
    verification:
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/backends/cli/ShellEscapeTest.kt#semicolonArgumentIsSingleQuoted"
        status: pass
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/backends/cli/ShellEscapeTest.kt#commandSubstitutionArgumentIsSingleQuoted"
        status: pass
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/backends/cli/ShellEscapeTest.kt#everyNonAllowlistedCharacterForcesQuoting"
        status: pass
    human_judgment: false
  - id: D2
    description: "Allowlisted arguments (`--silent`, `/usr/local/bin/claude`, `claude-3.5`, `gemini_cli`) still pass through byte-identical, so no working CLI invocation regresses"
    requirement: QUAL-06
    verification:
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/backends/cli/ShellEscapeTest.kt#plainFlagIsPassedThroughUnquoted"
        status: pass
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/backends/cli/ShellEscapeTest.kt#absolutePathIsPassedThroughUnquoted"
        status: pass
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/backends/cli/ShellEscapeTest.kt#ptyArgvLeavesAllowlistedArgumentsByteIdentical"
        status: pass
    human_judgment: false
  - id: D3
    description: "The quoting survives end-to-end into the argv `buildPtyCommand` hands to `sh -c`, on both the macOS and the Linux `script(1)` shape"
    requirement: QUAL-06
    verification:
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/backends/cli/ShellEscapeTest.kt#ptyArgvOnMacOsCarriesTheSemicolonArgumentQuoted"
        status: pass
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/backends/cli/ShellEscapeTest.kt#ptyArgvOnLinuxCarriesTheSemicolonArgumentQuoted"
        status: pass
    human_judgment: false
  - id: D4
    description: "Five pure CLI command/history/ANSI helpers are `internal` and covered by behavioural assertions"
    requirement: QUAL-06
    verification:
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/backends/cli/CliCommandHelpersTest.kt (25 tests, all pass)"
        status: pass
    human_judgment: false
  - id: D5
    description: "`backends.cli` line coverage >= 36.0% and `CliBackend.kt` >= 26.0%, computed from the jacoco XML"
    requirement: QUAL-06
    verification:
      - kind: other
        ref: "JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test jacocoTestReport — build/reports/jacoco/test/jacocoTestReport.xml: package 268/701 = 38.23%, CliBackend.kt 176/608 = 28.95%"
        status: pass
    human_judgment: false
  - id: D6
    description: "`detekt-baseline.xml` unchanged and `./gradlew detekt ktlintCheck` green"
    verification:
      - kind: other
        ref: "git diff --quiet detekt-baseline.xml (exit 0); JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew detekt ktlintCheck (BUILD SUCCESSFUL)"
        status: pass
    human_judgment: false
  - id: D7
    description: "The PTY path behaves unchanged against a real CLI backend (claude/gemini/codex/copilot) launched from Burp"
    verification: []
    human_judgment: true
    rationale: "The quoting change sits on the `usePty` branch, which only runs when a real CLI process is spawned from a running Burp instance. Nothing in this suite spawns a shell or a CLI, by design — so the end-to-end behaviour of a live backend is asserted only at the argv level, not at the process level."

duration: 34 min
completed: 2026-08-22
status: complete
---

# Phase 26 Plan 01: CLI shell-quoting allowlist and backends.cli coverage Summary

**`shellEscape` now quotes by an `A-Za-z0-9._/-` allowlist instead of a whitespace/quote denylist, closing the settings-import-to-`sh -c` path that let `foo;id` and `$(cmd)` through unquoted — proven directly on the helper and end-to-end on the PTY argv, and paired with behavioural coverage that lifts `backends.cli` from 30.76% to 38.23% line coverage.**

## Performance

- **Duration:** 34 min
- **Started:** 2026-08-22T14:36:00Z (approx., first file read)
- **Completed:** 2026-08-22T15:10:15+02:00 (last task commit)
- **Tasks:** 2
- **Files modified:** 3 (1 production, 2 new test classes)

## Accomplishments

- **SC1 closed.** `shellEscape` passes an argument through bare only when every character is an ASCII letter, an ASCII digit, or `.` `_` `/` `-`. `foo;id`, `$(cmd)`, backtick, newline, `*`, `~`, `$`, `|`, `&` and non-ASCII forms are all single-quoted. The apostrophe close/escape/reopen branch and the empty-argument `''` branch are unchanged and now asserted.
- **SC1 proven where it matters.** `buildPtyCommand` is a top-level `internal` function taking the OS name as a defaulted parameter, so both `script(1)` shapes — macOS `script -q /dev/null /bin/sh -c <cmd>` and Linux `script -q -c <cmd> /dev/null` — are asserted on one machine. The tests check the joined string the shell actually parses, not only the per-argument return value.
- **Over-quoting guarded.** `--silent`, `/usr/local/bin/claude`, `claude-3.5` and `gemini_cli` are asserted byte-identical, so a fix that quoted everything (and silently changed every working CLI invocation) would fail the suite.
- **SC2 floors cleared with room.** Package `backends.cli` line coverage **268/701 = 38.23%** against a 36.0% floor (was 215/699 = 30.76%); `CliBackend.kt` **176/608 = 28.95%** against a 26.0% floor (was 123/606 = 20.30%). Branch coverage improved as a side effect: package 75/488 = 15.37% → 127/482 = 26.35%, `CliBackend.kt` 53/464 = 11.42% → 105/458 = 22.93%.
- **Detekt baseline untouched.** `git diff --quiet detekt-baseline.xml` exits 0. Both detekt findings this work surfaced were fixed in source, not re-baselined.

## Task Commits

1. **Task 1 (RED): failing allowlist assertions + helper extraction** — `0b0959a` (test)
2. **Task 1 (GREEN): allowlist quoting + detekt fixes** — `279398c` (fix)
3. **Task 2: five widened helpers + behavioural coverage** — `9106057` (test)

## RED probe (Task 1, recorded as required)

The helpers were first extracted to top-level `internal` with their **original denylist logic intact**, then `ShellEscapeTest` was run against that unchanged behaviour:

```
ShellEscapeTest > ptyArgvOnMacOsCarriesTheSemicolonArgumentQuoted() FAILED
ShellEscapeTest > semicolonArgumentIsSingleQuoted() FAILED
ShellEscapeTest > ptyArgvOnLinuxCarriesTheSemicolonArgumentQuoted() FAILED
ShellEscapeTest > everyNonAllowlistedCharacterForcesQuoting() FAILED
ShellEscapeTest > commandSubstitutionArgumentIsSingleQuoted() FAILED
13 tests completed, 5 failed
```

The five failures are exactly the defect: the two argument forms Finding 10 records, the metacharacter table, and both end-to-end pty assertions. After the allowlist change: 13/13 green.

## Mutation probe (Task 2)

Task 2's tests are characterization tests over already-shipped behaviour, so a conventional RED was not available — the only failing state reachable before the change was a *compile* error on `private` visibility, which proves nothing about behaviour. Instead the suite was validated by mutating production code: replacing `history.takeLast(maxMessages)` with `history.take(maxMessages)` in `limitCliHistory` produced

```
CliCommandHelpersTest > exceedingTheMessageBoundDropsTheOldestTurns() FAILED
25 tests completed, 1 failed
```

so the suite catches a drop-from-the-wrong-end regression rather than merely executing the line. The mutation was reverted before the commit.

## Files Created/Modified

- `src/main/kotlin/com/six2dez/burp/aiagent/backends/cli/CliBackend.kt` — allowlist `shellEscape`; `SHELL_SAFE_CHARS`; top-level `internal` `shellEscape` / `buildPtyCommand`; five helpers widened to `internal` with KDoc; `CLI_HISTORY_MAX_MESSAGES` / `CLI_HISTORY_MAX_CHARS` constants; `isWindows()` → `isWindowsOs`, `resolveWindowsNpmShim()` → `windowsNpmShimDirs`.
- `src/test/kotlin/com/six2dez/burp/aiagent/backends/cli/ShellEscapeTest.kt` — 13 tests: SC1's direct assertions plus the end-to-end pty argv assertions.
- `src/test/kotlin/com/six2dez/burp/aiagent/backends/cli/CliCommandHelpersTest.kt` — 25 tests over the five pure helpers.

## Decisions Made

- **Allowlist over denylist.** A denylist at a shell boundary can always be under-enumerated; that is precisely how `;` and `$(...)` slipped past the whitespace/quote test.
- **`const val` scan over `Regex`.** The membership test runs once per CLI argument. A constant scan adds no backtracking surface to that path.
- **Defaulted environment parameters as the test seam.** `buildPtyCommand(cmd, osName)` and `normalizeWindowsCommand(cmd, windows)` both take their platform input as a defaulted parameter. Production calls are unchanged; the alternative — `System.setProperty("os.name", …)` in a test — mutates state shared with every other test in the JVM.
- **`resolveCommand` deliberately uncovered.** It walks `PATH` and stats the filesystem, so a test over it asserts the developer's machine rather than the code. Recorded in `CliCommandHelpersTest`'s KDoc so the gap reads as a decision, not an oversight.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Two detekt findings surfaced by the required extraction, fixed in source**

- **Found during:** Task 1 (allowlist + extraction)
- **Issue:** Moving `shellEscape` / `buildPtyCommand` to top level produced two detekt failures. (a) `ReturnCount`: the old `shellEscape` had 3 returns and was baselined under its old ID `ReturnCount:CliBackend.kt$CliBackend.CliConnection$private fun shellEscape(...)`; extraction changed the ID, so the finding resurfaced. (b) `TooManyFunctions`: two new top-level functions took the file from 10 to 12 against an inclusive threshold of 11.
- **Fix:** (a) `shellEscape` is now a single `when` expression — zero explicit returns. (b) `isWindows()` became the `isWindowsOs` property and `resolveWindowsNpmShim()` became the `windowsNpmShimDirs` property, taking the file to 10 top-level functions. Both read inputs a JVM cannot see change after start (`os.name`; `APPDATA` / `LOCALAPPDATA` / `USERPROFILE`), so reading them once is behaviour-preserving. Verified no test mutates `os.name` — `CliSupervisionTest` only reads it to skip.
- **Files modified:** `src/main/kotlin/.../CliBackend.kt`
- **Verification:** `./gradlew detekt ktlintCheck` green; `git diff --quiet detekt-baseline.xml` exits 0.
- **Committed in:** `279398c`

**2. [Rule 3 - Blocking] `normalizeWindowsCommand` refactored to a single `when` after its visibility widened**

- **Found during:** Task 2
- **Issue:** Widening `normalizeWindowsCommand` from `private` to `internal` (and adding the `windows` parameter) changed its detekt entity ID, so its baselined `ReturnCount` entry no longer matched and its 6 returns became a new finding. Adding a baseline entry is prohibited by the phase goal.
- **Fix:** The body is a single `when` expression behind one guard clause — 2 returns, inside the limit. No branch outcome changed.
- **Files modified:** `src/main/kotlin/.../CliBackend.kt`
- **Verification:** `./gradlew detekt` green with the baseline byte-identical; all nine `normalizeWindowsCommand` / `hasWindowsExeExtension` tests pass, including the `cmd /c` fallback and `.cmd`-sibling preference driven against a `@TempDir`.
- **Committed in:** `9106057`

**3. [Rule 2 - Missing Critical] `windows` testability parameter added to `normalizeWindowsCommand`**

- **Found during:** Task 2
- **Issue:** The plan asks for the Windows branch to be asserted, but on a macOS runner the helper's first line returns early. The only way in without a parameter is `System.setProperty("os.name", "Windows 10")`, which mutates JVM-global state shared with every other test.
- **Fix:** `windows: Boolean = isWindowsOs`, exactly the seam the plan itself specifies for `buildPtyCommand`'s `osName`. Production calls the one-argument form, so its behaviour is unchanged.
- **Files modified:** `src/main/kotlin/.../CliBackend.kt`, `src/test/kotlin/.../CliCommandHelpersTest.kt`
- **Verification:** `productionDefaultIsANoOpOnThisNonWindowsMachine` asserts the defaulted call itself.
- **Committed in:** `9106057`

**4. [Rule 2 - Missing Critical] History bounds extracted to named constants**

- **Found during:** Task 2
- **Issue:** The plan requires the tests to read the `limitCliHistory` bounds "from the production constants, never re-typed as literals". No such constants existed — `10` and `20_000` were inline named arguments at the `buildCliHistory` call site.
- **Fix:** Added `internal const val CLI_HISTORY_MAX_MESSAGES = 10` and `CLI_HISTORY_MAX_CHARS = 20_000`; `buildCliHistory` now passes them. The tests compute every fixture from those constants, so a future bound change fails loudly instead of leaving a stale assertion.
- **Files modified:** `src/main/kotlin/.../CliBackend.kt`, `src/test/kotlin/.../CliCommandHelpersTest.kt`
- **Verification:** Values are unchanged, so behaviour is identical; whole suite green.
- **Committed in:** `9106057`

**5. [Documentation] Plan's macOS argv element count corrected**

- **Found during:** Task 1
- **Issue:** The plan describes the macOS shape as "a five-element argv whose first four elements are the `script -q /dev/null /bin/sh -c` prefix and whose fifth element" carries the command. The prefix is itself five elements, so the argv has six.
- **Fix:** The test asserts the real production shape — `argv.subList(0, 5) == ["script", "-q", "/dev/null", "/bin/sh", "-c"]`, `argv.size == 6`, command in `argv[5]`. No production change; the plan's prose was off by one, not the code.
- **Committed in:** `0b0959a` / `279398c`

---

**Total deviations:** 5 (2 blocking static-analysis fixes, 2 missing-critical testability additions, 1 plan-prose correction)
**Impact on plan:** No scope creep. Every deviation was required to satisfy a stated acceptance criterion — the two detekt fixes to keep the baseline byte-identical, the two additions to make the plan's own required assertions possible without global-state mutation. No behaviour outside the quoting fix changed.

## Issues Encountered

None. The suite ran clean throughout; the `RedactionTest` wall-clock flake this repo records did not reproduce despite four concurrent executors (918 tests, 0 failures, 1 pre-existing skip).

## Known Stubs

None.

## Threat Flags

None. This plan adds no network endpoint, no auth path and no schema change; it narrows an existing trust boundary (`argv` → `sh -c`) rather than widening one. The threat register's `mitigate` dispositions T-26-01-01, T-26-01-02, T-26-01-03 and T-26-01-05 are all implemented and asserted.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- SC1 (QUAL-06) is closed and asserted at both the helper and the argv level.
- The `backends.cli` coverage floors are cleared with margin (38.23% vs 36.0%; 28.95% vs 26.0%), so a later plan that touches this package has headroom.
- One residual gap, deliberately left: `resolveCommand` remains uncovered because it is filesystem- and `PATH`-dependent. If a later phase wants it covered, it needs an injected filesystem seam, which is a larger change than this plan's scope allowed.
- Whole suite: 918 tests, 0 failures, 1 pre-existing skip. `detekt`, `ktlintCheck` green; `detekt-baseline.xml` and `build.gradle.kts` untouched.

## Self-Check: PASSED

- `src/test/kotlin/com/six2dez/burp/aiagent/backends/cli/ShellEscapeTest.kt` — FOUND
- `src/test/kotlin/com/six2dez/burp/aiagent/backends/cli/CliCommandHelpersTest.kt` — FOUND
- `src/main/kotlin/com/six2dez/burp/aiagent/backends/cli/CliBackend.kt` — FOUND
- Commits `0b0959a`, `279398c`, `9106057` — FOUND in `git log`
- `git diff --quiet detekt-baseline.xml` — exit 0
- `git diff --stat build.gradle.kts` — empty

---
*Phase: 26-coverage-static-analysis-debt-docs*
*Completed: 2026-08-22*
