---
phase: 25-secondary-hardening
plan: 02
subsystem: security
tags: [kotlin, ssrf, inet-aton, ipv4, jep-418, detekt, junit5]

requires:
  - phase: 25-secondary-hardening
    provides: "ROADMAP SC3 amended 2026-08-22 (commit 7ca3e72) — parse-then-classify, three of the four named forms are loopback"
provides:
  - "Ipv4Literal — a pure, network-free inet_aton parser (decimal, octal, hexadecimal; 1-4 parts) mapping a host string to four big-endian address bytes"
  - "SsrfGuard classifies IPv4 from parsed bytes via InetAddress.getByAddress; the dotted-quad IPV4_REGEX gate is gone"
  - "A JVM-wide name-resolution counter (CountingInetAddressResolverProvider) available to any future suite that needs to assert a no-resolution property behaviourally"
affects: [ssrf-guard, settings-ui, backend-base-url-validation]

actuals:
  tokens: 7700
  tasks: 3
  commits: 4

tech-stack:
  added: []
  patterns:
    - "Parse-then-classify: a pure String->ByteArray parser feeds a classifier that cannot resolve, so 'performs no name resolution' is a property of the type signature rather than a promise in prose"
    - "Behavioural no-resolution assertion via a JEP 418 InetAddressResolverProvider that delegates every call and only counts, gated by a control lookup that proves the counter is actually installed"

key-files:
  created:
    - src/main/kotlin/com/six2dez/burp/aiagent/util/Ipv4Literal.kt
    - src/test/kotlin/com/six2dez/burp/aiagent/util/Ipv4LiteralTest.kt
    - src/test/kotlin/com/six2dez/burp/aiagent/util/CountingInetAddressResolverProvider.kt
    - src/test/kotlin/com/six2dez/burp/aiagent/util/SsrfGuardNoResolutionTest.kt
    - src/test/resources/META-INF/services/java.net.spi.InetAddressResolverProvider
  modified:
    - src/main/kotlin/com/six2dez/burp/aiagent/util/SsrfGuard.kt
    - src/test/kotlin/com/six2dez/burp/aiagent/util/SsrfGuardTest.kt

key-decisions:
  - "Hand-written inet_aton parser rather than delegating to InetAddress.getByName — measured on JDK 21, getByName('0177.0.0.1') returns /177.0.0.1, reading 0177 as decimal 177 rather than octal 127, which would classify a loopback address as public"
  - "MAX_LITERAL_LENGTH exposed as internal rather than private so Ipv4LiteralTest can build its over-long input from the constant instead of drifting from a pasted literal; the other ten grammar constants stay private"
  - "Both resolver overrides name configuration.builtinResolver() at their own delegation site rather than sharing a hoisted field, so 'counts, never decides' is visible per method"
  - "SEC-07 NOT marked complete in REQUIREMENTS.md — the shared-ID gate (#2388) blocks it until 25-01 and 25-03, which also declare it, produce their summaries"

patterns-established:
  - "Structural count gates over negative greps: `grep -c 'getByName' SsrfGuard.kt` must return exactly 1, which simultaneously proves the IPv4 branch stopped resolving and proves the IPv6 branch was not deleted along with it"
  - "A vacuity control precedes any 'nothing happened' assertion — assert the instrument moves before asserting the subject does not move it"

requirements-completed: []

coverage:
  - id: D1
    description: "Ipv4Literal parses IPv4 literals in decimal, octal and hexadecimal notation across all four arities, with every A-25-07 and A-25-08 boundary pinned on both sides"
    requirement: SEC-07
    verification:
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/util/Ipv4LiteralTest.kt (12 tests)"
        status: pass
    human_judgment: false
  - id: D2
    description: "SC3: http://2852039166/ raises the SSRF advisory warning; http://2130706433/, http://0177.0.0.1/ and http://0x7f.1/ raise none (all three are loopback per D-01)"
    requirement: SEC-07
    verification:
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/util/SsrfGuardTest.kt#decimalLiteral_cloudMetadata_isFlagged, #decimalLiteral_loopback_isNotFlagged, #octalLiteral_loopback_isNotFlagged, #hexLiteral_loopback_isNotFlagged"
        status: pass
    human_judgment: false
  - id: D3
    description: "RFC-1918 ranges are flagged in alternate notation too — decimal 3232235786, hex 0xC0A8010A and octal 0300.0250.1.10 all resolve to 192.168.1.10 and all warn"
    requirement: SEC-07
    verification:
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/util/SsrfGuardTest.kt#rfc1918_inAlternateNotations_isFlagged"
        status: pass
    human_judgment: false
  - id: D4
    description: "SC4: classifying any host string in any notation performs zero name-resolution lookups, including 256.0.0.1, which reached real DNS on the pre-plan tree"
    requirement: SEC-07
    verification:
      - kind: integration
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/util/SsrfGuardNoResolutionTest.kt#theCountingResolverIsActuallyInstalled, #classifyingEveryNotationResolvesNothing"
        status: pass
    human_judgment: false
  - id: D5
    description: "The pre-existing SsrfGuard behaviour contract still holds — hostnames, blank input and malformed URLs return false; every dotted-quad private, link-local and IPv6 ULA case still returns true"
    requirement: SEC-07
    verification:
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/util/SsrfGuardTest.kt (11 pre-existing tests, unchanged)"
        status: pass
      - kind: integration
        ref: "JAVA_HOME=<jdk21> ./gradlew test — full suite, 859 tests across 123 classes"
        status: pass
    human_judgment: false
  - id: D6
    description: "The JVM-wide resolver provider does not disturb any other suite in the repo (T-25-11)"
    requirement: SEC-07
    verification:
      - kind: integration
        ref: "JAVA_HOME=<jdk21> ./gradlew test — full suite green with the provider installed (2m 34s)"
        status: pass
    human_judgment: false

duration: 25 min
completed: 2026-08-22
status: complete
---

# Phase 25 Plan 02: SsrfGuard inet_aton Notation Parsing Summary

**`SsrfGuard` now parses IPv4 literals in decimal, octal and hexadecimal notation through a hand-written `inet_aton` parser and classifies them from raw bytes via `InetAddress.getByAddress`, closing the notation-evasion bypass (SC3) while removing the last name-resolution call from the IPv4 path (SC4) — a property that was measurably broken before this plan.**

## Performance

- **Duration:** 25 min
- **Started:** 2026-08-22T09:25:00Z (approx.)
- **Completed:** 2026-08-22T09:49:37Z
- **Tasks:** 3
- **Files modified:** 7 (5 created, 2 modified)

## Accomplishments

- **`Ipv4Literal`** — a public `object` whose single public member is `fun parse(host: String): ByteArray?`. It implements the classic `inet_aton` grammar (1-4 dot-separated parts; decimal, octal via leading `0`, hexadecimal via leading `0x`/`0X`; last part absorbs the remaining bytes) with every intermediate value a `Long`, so `2852039166` — which would wrap negative in 32-bit arithmetic — parses correctly. The file references no `java.net` type and contains no `Regex`.
- **`SsrfGuard` rewired to parse-then-classify.** The `IPV4_REGEX` dotted-quad gate is deleted. IPv4 literals are parsed locally and classified via `InetAddress.getByAddress`, an API that cannot resolve. The classification `when` and `extractAuthorityHost` are untouched, so loopback still returns false per D-01 and the ULA/metadata arms are unchanged.
- **SC3 closed.** `http://2852039166/` (the decimal spelling of the cloud-metadata address 169.254.169.254) now raises the advisory warning. `http://2130706433/`, `http://0177.0.0.1/` and `http://0x7f.1/` all denote 127.0.0.1 and raise none — no Ollama or LM Studio user sees a new warning.
- **SC4 closed and proven, not assumed.** A JEP 418 `InetAddressResolverProvider` installed JVM-wide for the test run counts every name lookup and delegates every call to the built-in resolver. A control lookup proves the counter is actually installed before the corpus assertion runs.

## Task Commits

1. **Task 1: Parse-then-classify, wired end to end on one path** — `1b234e7` (feat)
2. **Task 2: The notation matrix and both probe boundaries (SC3)** — `dfd86c9` (test)
3. **Task 3: Prove the classifier resolves nothing (SC4)** — `154c66f` (test)

**Plan metadata:** see the `docs(25-02)` commit that carries this file.

## Red-Probe Record (per task)

The plan requires each task to record which red-probe form was achieved and the observed failure text.

### Task 1 — STRONG

Both new assertions compiled against the unmodified tree (`isPrivateOrLinkLocal` already existed and already took a `String`), and the true-case assertion failed on the assertion itself, not on a compile error.

```
SsrfGuardTest > decimalLiteral_cloudMetadata_isFlagged() FAILED
    org.opentest4j.AssertionFailedError: expected: <true> but was: <false>
        at com.six2dez.burp.aiagent.util.SsrfGuardTest.decimalLiteral_cloudMetadata_isFlagged(SsrfGuardTest.kt:70)
13 tests completed, 1 failed
```

The loopback counterpart (`decimalLiteral_loopback_isNotFlagged`) passed against the unmodified tree — vacuously, since the old regex rejected `2130706433` outright — which is exactly why it is paired with the true case rather than standing alone.

### Task 2 — STRONG for the SC3 public-entry-point assertions, WEAK for the parser boundary matrix

**This is a deviation from the plan's expectation and is recorded honestly rather than rounded up.** The plan predicted "the STRONG form throughout". In practice `Ipv4Literal` was written in Task 1 directly from the A-25-07 and A-25-08 tables, so every `Ipv4LiteralTest` boundary assertion passed on its first run. Those 12 tests are a **WEAK** probe: they compile and pass immediately, and pin the contract against future regression rather than driving this implementation.

The `SsrfGuardTest` additions are genuinely **STRONG**. Reverting only `SsrfGuard.kt` to the pre-Task-1 tree (`git checkout HEAD~1 -- src/main/kotlin/com/six2dez/burp/aiagent/util/SsrfGuard.kt`, run, restore with `git checkout HEAD -- <same path>`) produced a real assertion failure:

```
SsrfGuardTest > rfc1918_inAlternateNotations_isFlagged() FAILED
    org.opentest4j.AssertionFailedError: expected: <true> but was: <false>
SsrfGuardTest > decimalLiteral_cloudMetadata_isFlagged() FAILED
    org.opentest4j.AssertionFailedError: expected: <true> but was: <false>
30 tests completed, 2 failed
```

`Ipv4LiteralTest` stayed green in that run, confirming the parser and the wiring are independently observable.

### Task 3 — STRONG

Both tests compiled against the unmodified `SsrfGuard.kt` (nothing in Task 3 depends on `Ipv4Literal`). Reverting the guard with
`git checkout HEAD~2 -- src/main/kotlin/com/six2dez/burp/aiagent/util/SsrfGuard.kt`
(restored afterwards with `git checkout HEAD -- <same path>`; **no `git stash` was used at any point**, since `refs/stash` is shared across linked worktrees) produced:

```
SsrfGuardNoResolutionTest > classifyingEveryNotationResolvesNothing() FAILED
    org.opentest4j.AssertionFailedError: SsrfGuard performed name resolution while classifying.
    Leaked host names: [256.0.0.1]. ... ==> expected: <0> but was: <1>
2 tests completed, 1 failed
```

- **Resolver counter observed against the pre-Task-1 tree: `1`.**
- **Leaked host name: `256.0.0.1`.**

This matches the plan's 2026-08-22 correction exactly: `256.0.0.1` alone carries the SC4 regression evidence. `0400.0.0.1` is in the corpus but did **not** increment the counter, because `0400` is four digits and failed the old `\d{1,3}` regex before reaching the resolver. Had the plan's original (uncorrected) claim been true, the counter would have read `2`.

Critically, `theCountingResolverIsActuallyInstalled` **passed in the same red run**, so the failure above is a real observation and not an artifact of a mis-registered provider.

**SC4 strategy used: the primary JEP 418 `InetAddressResolverProvider`.** The named `Mockito.mockStatic` fallback was NOT needed — the SPI disturbed no other suite.

## Files Created/Modified

- `src/main/kotlin/com/six2dez/burp/aiagent/util/Ipv4Literal.kt` (new, 148 lines) — the `inet_aton` parser. Ten private grammar constants plus one `internal` length cap; no `java.net`, no `Regex`.
- `src/main/kotlin/com/six2dez/burp/aiagent/util/SsrfGuard.kt` (modified) — `IPV4_REGEX` deleted; IPv4 classified from parsed bytes; the surviving `getByName` call moved into a dedicated `resolveIpv6Literal` helper; object KDoc updated to record the closed defect.
- `src/test/kotlin/com/six2dez/burp/aiagent/util/Ipv4LiteralTest.kt` (new, 12 tests) — the arity table and every boundary pinned on both sides.
- `src/test/kotlin/com/six2dez/burp/aiagent/util/SsrfGuardTest.kt` (modified) — 11 pre-existing tests untouched; 6 added through the public entry point.
- `src/test/kotlin/com/six2dez/burp/aiagent/util/CountingInetAddressResolverProvider.kt` (new) — delegating, counting JEP 418 provider.
- `src/test/resources/META-INF/services/java.net.spi.InetAddressResolverProvider` (new) — the first entry under `src/test/resources`; picked up by the Kotlin JVM plugin's default convention, no Gradle change.
- `src/test/kotlin/com/six2dez/burp/aiagent/util/SsrfGuardNoResolutionTest.kt` (new, 2 tests) — control plus 14-URL corpus.

## Decisions Made

- **Hand-written parser, not the JDK.** Re-measured on JDK 21 during execution and confirmed: `InetAddress.getByName("0177.0.0.1")` reads `0177` as decimal 177. Delegating the octal form would classify loopback as public. `Ipv4Literal.kt`'s KDoc records this so the next reader does not "simplify" the parser away.
- **`MAX_LITERAL_LENGTH` is `internal`, not `private`** (deviation, see below).
- **`SEC-07` deliberately left unchecked in `REQUIREMENTS.md`.** All three phase-25 plans declare it and neither 25-01 nor 25-03 has a SUMMARY yet, so the shared-ID gate (#2388) blocks it. `REQUIREMENTS.md` is unmodified by this plan.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] `TooManyFunctions` on the decomposed parser**

- **Found during:** Task 1
- **Issue:** The plan prescribed decomposing `parse` into small expression-bodied helpers to satisfy `ReturnCount` (max 2). Doing so literally produced 13 functions in `object Ipv4Literal`, tripping a rule the plan did not anticipate: `TooManyFunctions` at detekt's in-object threshold of 11. The baseline is frozen at 1096 entries by QUAL-07, so this was a build failure, not a baseline edit.
- **Fix:** Merged three helpers away without adding a single return to any function — `alphabetFor` folded into `digitsToLongOrNull` as a local `val`, `assemble` folded into `parseParts`, and `hasParsableLength` inlined into `parse`'s `takeIf` lambda. Ten functions remain; every one still has one or two returns.
- **Verification:** `./gradlew detekt` passes; `git diff --stat detekt-baseline.xml` is empty.
- **Committed in:** `1b234e7`

**2. [Rule 3 - Blocking] `MAX_LITERAL_LENGTH` widened from `private` to `internal`**

- **Found during:** Task 2
- **Issue:** The plan's artifacts table lists all eleven grammar constants as file-private, but its Task 2 instruction requires the over-long test input to be built from `MAX_LITERAL_LENGTH + 1` characters "so the test tracks the constant instead of drifting from it". A `private` member of an object is not visible from the test source set, so the two requirements are mutually exclusive.
- **Fix:** Widened `MAX_LITERAL_LENGTH` alone to `internal` (the Kotlin Gradle plugin makes the test source set a friend of main, so `internal` is visible there but stays module-private in the shipped JAR). The other ten constants remain `private`. A KDoc line records why.
- **Verification:** `Ipv4LiteralTest.lengthCap_isPinnedOnBothSides` pins both sides of the cap — a 45-character zero-padded input parses to `0.1.2.3`, a 46-character one is rejected — so the test isolates the length rule rather than accidentally re-testing overflow.
- **Committed in:** `1b234e7` (constant), `dfd86c9` (test)

**3. [Rule 3 - Blocking] KDoc rewording to satisfy the `getByName` count gate**

- **Found during:** Task 1
- **Issue:** The plan requires `grep -c 'getByName' SsrfGuard.kt` to return exactly `1`, and simultaneously requires the KDoc to explain the defect that was closed. The first KDoc draft named `getByName` four times in prose, so the bare grep returned `5`.
- **Fix:** Reworded the comments to say "the JDK's name-resolving lookup" / "the one surviving resolving call", leaving exactly one occurrence — the call itself. A KDoc line now warns future editors not to name it again in prose and explains why the count gate exists.
- **Verification:** `grep -c 'getByName' src/main/kotlin/com/six2dez/burp/aiagent/util/SsrfGuard.kt` returns `1`.
- **Committed in:** `1b234e7`

**4. [Rule 3 - Blocking] `builtinResolver` hoisted out of the delegation sites**

- **Found during:** Task 3
- **Issue:** The first draft hoisted `val builtin = configuration.builtinResolver()` once in `get()`. Functionally correct, but it left only one textual occurrence, failing the plan's `>= 2` gate — a gate whose intent is that each override visibly delegates rather than decides.
- **Fix:** Each override now names `configuration.builtinResolver()` at its own delegation site (a plain field read on the JDK's `Configuration`). A comment records why the hoist was rejected.
- **Verification:** the comment-filtered grep returns `2`; full suite green.
- **Committed in:** `154c66f`

### Documented Divergences from the Plan's Stated Facts

Neither required a code change; both are recorded so the next reader is not misled by the plan text.

- **The plan says `SsrfGuardTest.kt` has 12 pre-existing tests. It has 11.** Counted at execution: 11 `@Test` methods across 65 lines. The plan's Task 1 acceptance criterion "all 12 pre-existing tests plus the two new ones" was therefore read as "all pre-existing tests"; the run reported `13 tests completed`, i.e. 11 + 2, which is correct. `SsrfGuardTest` now holds 17 tests.
- **`URI("http://256.0.0.1/").host` is `null`, not `"256.0.0.1"`.** Re-measured on JDK 21 during execution. The plan's `<measured_facts>` implies `256.0.0.1` arrived via the normal `URI` path; it actually arrives via the `extractAuthorityHost` fallback at `SsrfGuard.kt:39`. **The SC4 conclusion is unaffected** — the host string still reached `IPV4_REGEX`, still matched (`256` is three digits), and still reached the resolver, which is exactly what the Task 3 red probe measured (counter `1`, leaked name `256.0.0.1`). The same re-measurement confirms `http://0300.0250.1.10/` and `http://0x7f.1/` also arrive via the fallback, which is why the plan's insistence on testing through the public entry point rather than the parser was correct and load-bearing.

---

**Total deviations:** 4 auto-fixed (all Rule 3 - blocking), plus 2 documented divergences from plan-stated facts requiring no change.
**Impact on plan:** No scope creep. Every fix was forced by a gate the plan itself specified; none altered the accepted notation set, the classification logic or the public signature `fun isPrivateOrLinkLocal(url: String): Boolean`. All prohibitions held: no name resolution and no `Regex` in `Ipv4Literal.kt`, no signature change, no `extractAuthorityHost` change, no `detekt-baseline.xml` entry, no `build.gradle.kts` change, no new dependency, no `git stash`, no non-English text.

## Verification Results

| Gate | Result |
|---|---|
| `./gradlew test` (full suite, JDK 21) | PASS — 859 tests, 123 classes, 0 failures, 2m 34s |
| `./gradlew detekt ktlintCheck` | PASS |
| `git diff --stat detekt-baseline.xml` | empty |
| `git diff --stat build.gradle.kts` | empty |
| `grep -c 'getByName' SsrfGuard.kt` | `1` (the IPv6 branch) |
| `grep -c 'IPV4_REGEX' SsrfGuard.kt` | `0` |
| `Ipv4Literal.kt` non-comment `java.net`/`InetAddress`/`getByName`/`Socket` refs | `0` |
| `Ipv4Literal.kt` non-comment `Regex` refs | `0` |
| SC3 four forms in `SsrfGuardTest.kt` | `5` (>= 4 required) |
| Probe boundaries in `Ipv4LiteralTest.kt` | `4` (>= 4 required) |
| `builtinResolver` delegations (comment-filtered) | `2` (>= 2 required) |
| `256.0.0.1` / `0400.0.0.1` in SC4 corpus | `4` / `2` |
| Timing assertions in `SsrfGuardNoResolutionTest.kt` | `0` |
| Excluded heavy-test suffixes in `util/` | `0` |

**Note on Gradle invocation.** Every Gradle command ran on JDK 21 as required. The sandbox in this worktree refuses command substitution, so `JAVA_HOME=$(/usr/libexec/java_home -v 21)` was invoked as `JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home` — the resolved output of that exact command, verified against `./gradlew -q javaToolchains`, which reports Temurin 21.0.12.1+1-LTS as the current JVM. No Gradle invocation used the default JDK.

## Issues Encountered

- **`ktlintFormat` was used once** (Task 1) to fix chain-continuation and parameter-wrapping violations in the new `Ipv4Literal.kt`. `git status` was checked immediately afterwards and confirmed it touched only that one new file — no unrelated source was reformatted.
- **No flake observed.** `RedactionTest` (the repo's recorded wall-clock flake class, via the `SafeRegex` 50 ms deadline) passed in both full-suite runs.

## Known Stubs

None. No placeholder, hardcoded-empty or TODO path was introduced.

## Threat Flags

None. No new network endpoint, auth path, file-access pattern or schema change at a trust boundary. The plan's threat register is fully discharged: T-25-07 (information disclosure via `getByName`) is closed by construction and asserted by `SsrfGuardNoResolutionTest`; T-25-08 (notation evasion) by `SsrfGuardTest`; T-25-09 (ReDoS) by the regex-free, length-capped parser; T-25-10 (loopback false-positive regression) by the four-notation loopback assertions plus the unchanged pre-existing contract; T-25-11 (resolver blast radius) by the full-suite green requirement; T-25-SC (package legitimacy) is not applicable — no package was installed.

## User Setup Required

None — no external service configuration required.

## Next Phase Readiness

- SC3 and SC4 are closed, pinned and independently verifiable.
- **`SEC-07` remains unchecked in `REQUIREMENTS.md` by design.** Plans 25-01 and 25-03 also declare it; the requirement flips to complete only once all three summaries exist.
- No blockers for 25-01 or 25-03. This plan touched only `util/Ipv4Literal.kt`, `util/SsrfGuard.kt` and four files under `src/test/`, plus one new `src/test/resources` entry — no overlap with the MCP bearer-token work those plans own.
- One cross-cutting note for reviewers of sibling plans: `CountingInetAddressResolverProvider` is now installed for the **entire test JVM**. Any suite that begins asserting on resolution behaviour should call `CountingInetAddressResolverProvider.reset()` first, and any suite that mocks `InetAddress` statically should be aware the provider sits underneath.

## Self-Check: PASSED

- All 5 created source/resource files verified present on disk, plus this SUMMARY.
- All 4 commits verified in `git log`: `1b234e7`, `dfd86c9`, `154c66f`, `5a8d3c8`.
- Full suite, `detekt` and `ktlintCheck` re-run green against the final committed tree.
- `STATE.md` and `ROADMAP.md` intentionally NOT modified — the orchestrator owns those writes after the wave merges.

---
*Phase: 25-secondary-hardening*
*Completed: 2026-08-22*
