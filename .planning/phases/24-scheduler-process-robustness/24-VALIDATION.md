---
phase: 24
slug: scheduler-process-robustness
# status lifecycle: draft (seeded by plan-phase) → validated (set by validate-phase §6)
status: draft
nyquist_compliant: false
wave_0_complete: false  # set by execute-phase once the six suites exist
created: 2026-08-21
---

# Phase 24 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.
> Seeded by `/gsd-plan-phase` from `24-RESEARCH.md` §Validation Architecture.
> Task IDs filled by `/gsd-plan-phase` on 2026-08-21; five plans in four waves.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit Jupiter 6.0.3 (`useJUnitPlatform()`) + mockito-kotlin 5.4.0 + `kotlin("test")` |
| **Config file** | `build.gradle.kts:152-249` (`tasks.test`) — no separate config file |
| **Quick run command** | `JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test --tests '*<SuiteName>*'` |
| **Full suite command** | `JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test detekt ktlintCheck` |
| **PR-gate equivalent** | `JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test -PexcludeHeavyTests=true` |
| **Test JVM args** | `-ea -Djava.awt.headless=true` — **no `--add-opens`** |
| **Estimated runtime** | quick ~5-15 s per suite; full suite minutes |

⚠ **JDK 21 is required** — `JAVA_HOME=$(/usr/libexec/java_home -v 21)`. The default JDK 25 breaks
Gradle 8.12.1.

---

## Sampling Rate

- **After every task commit:** `./gradlew test --tests '*<the suite this task touches>*'`
- **After every plan wave:** `./gradlew test detekt ktlintCheck` — **detekt is not optional here.**
  A new file that catches `Exception`/`Throwable` fails the active detekt rule, and the baseline is
  exact-string keyed per file while QUAL-07 forbids growing it. This is the single most likely
  wave-merge break in this phase.
- **Before `/gsd-verify-work`:** BOTH `./gradlew test -PexcludeHeavyTests=true` AND the unfiltered
  `./gradlew test detekt ktlintCheck shadowJar` must be green. Running both is what proves no new
  suite was accidentally named into the exclusion list.
- **Max feedback latency:** < 30 s for a single suite.

⚠ **Suite-naming constraint.** `tasks.test` excludes five filename suffixes under
`-PexcludeHeavyTests=true`: `*IntegrationTest`, `*ConcurrencyTest`, `*BackpressureTest`,
`*RestartPolicyTest`, `*SupervisionTest`. These are exactly the natural names for a concurrency
phase — a suite named `SchedulerConcurrencyTest` would silently vanish from the PR gate. None of the
suite names below match an excluded suffix; keep it that way.

---

## Per-Task Verification Map

Task IDs assigned by `/gsd-plan-phase`; `/gsd-validate-phase` reconciles them after execution.
Task ID format is `<plan>-T<n>` — for example `24-01-T3` is task 3 of `24-01-PLAN.md`.

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 24-01-T1 | 24-01 | 1 | REL-06-A (SC1) | T-24-01 | `runGuarded` swallows a throw; the **next tick still fires** (real `ScheduledExecutorService`, 10 ms delay, throw on tick 1, `CountDownLatch` on ticks 2-3, inside `assertTimeoutPreemptively(5s)`) | unit | `./gradlew test --tests '*GuardedSchedulingTest'` | ❌ W0 | ⬜ pending |
| 24-01-T1 | 24-01 | 1 | REL-06-B (SC1) | T-24-01 | `runGuarded` catches **`Throwable`, not only `Exception`** — body throws an `Error`; ticker survives | unit | `./gradlew test --tests '*GuardedSchedulingTest'` | ❌ W0 | ⬜ pending |
| 24-01-T1 | 24-01 | 1 | REL-06-C (SC1) | T-24-01 | `runGuarded` logs with the `[Component] …` prefix and the label, so a suppressed failure is visible | unit | `./gradlew test --tests '*GuardedSchedulingTest'` | ❌ W0 | ⬜ pending |
| 24-01-T3 | 24-01 | 1 | REL-06-D (SC1) | T-24-01, T-24-11 | **Structural allowlist** — files under `src/main/kotlin` containing `scheduleAtFixedRate(`/`scheduleWithFixedDelay(` equal the chosen allowlist. Needs a `tasks.test` `inputs` declaration | structural | `./gradlew test --tests '*SchedulerGuardCoverageTest'` | ❌ W0 | ⬜ pending |
| 24-02-T1 | 24-02 | 2 | REL-06-E (SC1, optional) | T-24-02 | `ActiveAiScanner`'s scheduler survives a `RejectedExecutionException` from `exec.submit`; submit attempted on ≥2 ticks | unit (reflects into own class) | `./gradlew test --tests '*ActiveScannerFailureIsolationTest'` | ❌ W0 | ⬜ pending |
| 24-02-T1 | 24-02 | 2 | REL-06-F (SC2) | T-24-01 | `getSettings` throws for target A only; B and C still complete — `scansCompleted` reaches 3, `queueSize` reaches 0, inside `assertTimeoutPreemptively` | unit (headless, deep-stub Montoya) | `./gradlew test --tests '*ActiveScannerFailureIsolationTest'` | ❌ W0 | ⬜ pending |
| 24-02-T1 | 24-02 | 2 | REL-06-G (SC2) | T-24-01, T-24-03 | The failure log line contains `target.id` — `verify(api.logging()).logToError(argThat { contains(target.id) })` | unit | `./gradlew test --tests '*ActiveScannerFailureIsolationTest'` | ❌ W0 | ⬜ pending |
| 24-03-T1 | 24-03 | 2 | REL-07-A (SC3) | T-24-13 | Concurrent `appendLine` from N threads + concurrent `snapshot()` reads: no exception, no torn content, every line whole, counts consistent | unit | `./gradlew test --tests '*CliOutputBufferTest'` | ❌ W0 | ⬜ pending |
| 24-03-T1 | 24-03 | 2 | REL-07-B (SC4) | T-24-04 | Appending beyond the cap retains at most `maxChars`, sets `truncated`, retains the **head** | unit | `./gradlew test --tests '*CliOutputBufferTest'` | ❌ W0 | ⬜ pending |
| 24-03-T1 | 24-03 | 2 | REL-07-C (SC4) | T-24-05 | A legitimate answer (~50 000 chars) round-trips **byte-identically** — the anti-corruption assertion that catches a 2000-char cap | unit | `./gradlew test --tests '*CliOutputBufferTest'` | ❌ W0 | ⬜ pending |
| 24-03-T3 | 24-03 | 2 | REL-07-D (SC3) | T-24-13 | **Structural** — `CliBackend.kt` no longer declares `StringBuilder()` for `rawOutput`. Needs a `tasks.test` input declaration | structural | `./gradlew test --tests '*CliOutputBufferTest'` | ❌ W0 | ⬜ pending |
| 24-04-T1 | 24-04 | 3 | REL-07-E (SC5) | T-24-15 | D-02 registry: register→1; register same file twice→1; deregister→0; drain deletes a registered file; drain idempotent when file already gone | unit | `./gradlew test --tests '*CliBackendTempFileTest'` | ⚠ **rewrite** | ⬜ pending |
| 24-04-T3 | 24-04 | 3 | REL-07-F (SC5) | T-24-07, T-24-15 | **Structural** — `CliBackend.kt` contains zero occurrences of `deleteOnExit(`. Needs a `tasks.test` input declaration | structural | `./gradlew test --tests '*CliBackendTempFileTest'` | ❌ W0 | ⬜ pending |
| 24-02-T3 / 24-05-T3 | 24-02, 24-05 | 2, 4 | REL-07-G (SC6) | T-24-08, T-24-17 | Both pools bounded — structurally (comment-stripped read of `App.kt` / `ActiveAiScanner.kt` shows no unbounded cached pool) and behaviourally via `maximumPoolSize` on a locally-constructed pool of the same shape. Split across the two pool suites because the two halves land in different waves | structural + unit | `./gradlew test --tests '*ScanRequestExecutorTest' --tests '*WorkerPoolExecutorTest'` | ❌ W0 | ⬜ pending |
| 24-02-T3 / 24-05-T3 | 24-02, 24-05 | 2, 4 | REL-07-H (SC6) | T-24-08, T-24-17 | New factories' threads carry expected names and `isDaemon` — assert on `scanRequestThreadFactory()` / `workerPoolThreadFactory()` directly, no live pool needed | unit | `./gradlew test --tests '*ScanRequestExecutorTest' --tests '*WorkerPoolExecutorTest'` | ❌ W0 | ⬜ pending |
| 24-02-T3 | 24-02 | 2 | REL-07-I (SC6) | T-24-12 | `sendRequestWithTimeout` returns `null` rather than propagating when the pool rejects; plus a source-order assertion that `requestExecutor.submit(` sits AFTER `return try {` | unit + structural | `./gradlew test --tests '*ScanRequestExecutorTest'` | ❌ W0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

**Headless drivability — stated per assertion.** Every row above is headlessly drivable under
`-Djava.awt.headless=true`: none constructs a Swing component, none needs a live Burp, none needs a
real subprocess, and none reflects into `java.base`. The two rows that reflect at all (REL-06-E, and
the `maximumPoolSize` half of REL-07-G) reflect only into this project's own classes, which is
unrestricted. `ActiveAiScanner` is confirmed headlessly constructible by the existing green
`ScannerQueueBackpressureTest.kt:20-27`.

---

## Red-Before-Green Gate

Phase 20 established this as an acceptance criterion — *"a test that passes both before and after has
not tested the bypass."* Rows that genuinely go **red pre-fix**:

| Row | Why it is red today |
|-----|---------------------|
| REL-06-D | three files currently call `scheduleWithFixedDelay` directly |
| REL-06-G | the failure log line carries no target id |
| REL-07-B / C / D | no buffer class exists |
| REL-07-F | `deleteOnExit(` is present twice in `CliBackend.kt` |
| REL-07-G / H | an unbounded cached pool is constructed twice; no named factories |
| REL-07-I | `requestExecutor.submit(` sits before `return try {` in `sendRequestWithTimeout` |
| REL-07-D (source-order half) | `App.kt` has no `"CLI temp files"` shutdown step |

Rows REL-06-A/B/C and REL-07-A/E test code that does not exist yet and are red only by
non-compilation — that is weaker evidence. **Note it in the plan; do not claim them as gates.**

---

## Assertions Explicitly Ruled Out

Recorded so a later plan does not reintroduce them (Phase 23's lesson about vacuous and
false-by-construction assertions):

- ❌ **"Assert `deleteOnExit` is/is not registered via `java.io.DeleteOnExitHook` reflection"** —
  measured dead on JDK 21: the reflection raises `InaccessibleObjectException` and the existing
  helper's `catch (_: Exception) { true }` converts that into a pass. Fail-open, therefore vacuous.
- ❌ **"Assert `processQueue` survives a throw injected into its body"** — no such throw site exists
  on the scheduler thread today; everything that can throw is already inside the per-target
  `try/catch` at `ActiveAiScanner.kt:391-401`. Phrased that way the assertion is vacuous or is
  silently testing something else.
- ❌ **"Assert the scanner keeps processing after a target failure"** *as the sole SC2 assertion* —
  already true today, so it cannot fail before the fix. Pair it with the log-content assertion
  (REL-06-G), which is the half that goes red.
- ❌ **Any assertion comparing an elapsed duration to a threshold** — wall-clock flake.
- ❌ **"Assert the registry cleaner's next tick fires"** without an injected lever — 5-minute
  interval, private field, and `cleanupExpired` contains no throw site.

---

## Wave 0 Requirements

- [ ] `src/test/kotlin/com/six2dez/burp/aiagent/util/GuardedSchedulingTest.kt` — REL-06-A/B/C
- [ ] `src/test/kotlin/com/six2dez/burp/aiagent/util/SchedulerGuardCoverageTest.kt` — REL-06-D
- [ ] `src/test/kotlin/com/six2dez/burp/aiagent/scanner/ActiveScannerFailureIsolationTest.kt` — REL-06-F/G
- [ ] `src/test/kotlin/com/six2dez/burp/aiagent/backends/cli/CliOutputBufferTest.kt` — REL-07-A/B/C/D
- [ ] `src/test/kotlin/com/six2dez/burp/aiagent/backends/cli/CliBackendTempFileTest.kt` — **rewrite**; REL-07-E/F
- [ ] `src/test/kotlin/com/six2dez/burp/aiagent/scanner/ScanRequestExecutorTest.kt` — REL-07-G/H/I, scanner half (plan 24-02)
- [ ] `src/test/kotlin/com/six2dez/burp/aiagent/WorkerPoolExecutorTest.kt` — REL-07-G/H, worker-pool half (plan 24-05)

  > **Planner note.** The researcher's single `BoundedExecutorTest.kt` was split in two. The two pools
  > have different workload shapes, different rejection contracts and land in different waves (24-02 in
  > wave 2, 24-05 in wave 4); one suite would have had to assert against `App.kt` before `App.kt` was
  > changed. Neither name matches an excluded suffix — note that `WorkerPoolSupervisionTest` and
  > `WorkerPoolRestartPolicyTest` are both natural names here and both are traps.

- [ ] `build.gradle.kts` — ONE `tasks.test` input declaration covering **every** structural assertion:
      `inputs.dir("src/main/kotlin")` with property name `mainSourceTreeStructuralInputs`, created in
      plan 24-01 task 3. **Without it the guards are cache-served and never run.**

  > **Planner note (resolves RESEARCH.md assumption A6).** All seven pre-existing declarations are
  > single-file. REL-06-D walks a tree, and a tree-walking allowlist whose input declaration lists
  > known files cannot see a scheduler introduced in a sixth, undeclared file — the exact defect it
  > exists to catch. One whole-tree declaration is the honest shape and it also subsumes REL-07-D,
  > REL-07-F and REL-07-G, which is why plans 24-02 through 24-05 never touch `build.gradle.kts`.
  > Accepted cost: `tasks.test` re-runs on any main-source edit.
- [ ] Framework install: **not needed** — JUnit 6.0.3 + mockito-kotlin 5.4.0 already declared.

---

## Manual-Only Verifications

**All phase behaviors have automated verification.** The planner confirms the researcher's finding:
no success criterion in Phase 24 requires a live Burp, a real subprocess, a Swing component, or
reflection into `java.base`. All five plans declare `autonomous: true` and none contains a
`checkpoint:human-verify`.

One accepted gap is recorded rather than tested: the hard-kill temp-file residual (threat `T-24-06`,
locked as CONTEXT.md D-04). It is unreachable by any automated or manual test by construction —
neither the `finally` block nor any shutdown hook runs on SIGKILL or power loss — which is precisely
why D-04 accepts and documents it in the `CliTempFileRegistry` KDoc instead of asserting it.

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] Every structural assertion has its matching `tasks.test` `inputs` declaration
- [ ] No new suite name matches an `-PexcludeHeavyTests` excluded suffix
- [ ] No watch-mode flags
- [ ] Feedback latency < 30 s
- [ ] `nyquist_compliant: false` set in frontmatter

**Approval:** pending
