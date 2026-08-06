---
phase: 20-mcp-access-control-correctness
plan: 02
subsystem: mcp
tags: [build, gradle, test-infrastructure, tls, okhttp, sec-05]
requires:
  - "build.gradle.kts GenerateBuildFlagsTask (Phase 08/18 wiring)"
  - "KtorMcpServerManager.start lifecycle callback"
  - "TestSettings.baselineSettings()"
provides:
  - "BuildFlags.VERSION — compile-time project version constant"
  - "McpTestServerSupport — real-Netty lifecycle + OkHttp clients for Phase 20 pipeline tests"
affects:
  - "any future MCP Implementation() version advertisement (SEC-05 5a consumer, Plan 03+)"
  - "Wave 3 pipeline tests (Plans 04, 05) which call McpTestServerSupport directly"
tech-stack:
  added: []
  patterns:
    - "Gradle Property<String> input captured at configuration time for configuration-cache safety"
    - "internal object test-support helper (no @Test, non-Test suffix) shared across a phase's test classes"
    - "McpSettings built via .copy() on TestSettings baseline instead of a full constructor literal"
key-files:
  created:
    - src/test/kotlin/com/six2dez/burp/aiagent/mcp/McpBuildFlagsVersionTest.kt
    - src/test/kotlin/com/six2dez/burp/aiagent/mcp/McpTestServerSupport.kt
  modified:
    - build.gradle.kts
decisions:
  - "BuildFlags.VERSION asserted as a semver regex plus a not-equal-0.6.0 guard, never a literal release number, so a version bump never requires a test edit"
  - "REQUIREMENTS.md deliberately not touched: SEC-04/SEC-05 are shared by all 6 phase plans and this plan closes only SEC-05 sub-item 5a"
metrics:
  duration: ~12 min
  completed: 2026-08-06
---

# Phase 20 Plan 02: Version Constant & Shared Test-Server Support Summary

Generated `BuildFlags.VERSION` from `build.gradle.kts`'s single `version` declaration (configuration-cache safe) and added the OkHttp-based `McpTestServerSupport` helper that every Phase 20 pipeline test binds a real Netty server through.

## What Was Built

### Task 1 — `BuildFlags.VERSION` (SEC-05 5a) — commit `4ac0efe`

`GenerateBuildFlagsTask` gained a second `@get:Input`, `abstract val version: Property<String>`, and the emitted `object BuildFlags` body now declares both constants. The registration block sets `version.set(project.version.toString())` at **configuration** time, with a `SEC-05 / P11` comment recording why: `gradle.properties` sets `org.gradle.configuration-cache=true`, so reading any `Project` API from inside `@TaskAction fun generate()` would fail the build.

Generated output (`build/generated/buildflags/com/six2dez/burp/aiagent/BuildFlags.kt`):

```kotlin
object BuildFlags {
    const val STORE_BUILD = false
    const val VERSION = "0.9.2"
}
```

**Observed `BuildFlags.VERSION` value: `0.9.2`** — matches `build.gradle.kts:15` `version = "0.9.2"`. The stale MCP placeholder was `0.6.0`.

`sourceSets.main`, the `dependsOn` wiring and the ktlint `**/generated/**` exclusion were all left untouched, as instructed — `kotlin.srcDir(generateBuildFlags.flatMap { it.outputDir })` already routes the directory.

`McpBuildFlagsVersionTest` (matches the `--tests '*BuildFlags*'` filter, deliberately not an `*IntegrationTest`):
- `version_isARealSemverAndNotTheHardcodedPlaceholder` — semver regex + `assertNotEquals("0.6.0", ...)`; no literal release number asserted.
- `storeBuild_flagStillGenerated` — `STORE_BUILD` still `false` under a default build.

### Task 2 — `McpTestServerSupport` — commit `bbde4e3`

`internal object McpTestServerSupport` in `com.six2dez.burp.aiagent.mcp`. No `@Test` method, name does not end in `Test`, so no test-task filter matches it. Its KDoc records both non-negotiables: the `*IntegrationTest`/`*ConcurrencyTest`/`*BackpressureTest`/`*RestartPolicyTest`/`*SupervisionTest` exclusion trap at `build.gradle.kts:145-157`, and the OkHttp-not-`HttpURLConnection` constraint (the JDK restricted-header list drops `Origin` and overwrites `Host`, making SC2 unwritable).

**Exact function signatures — Plans 04 and 05 call these directly:**

```kotlin
internal object McpTestServerSupport {
    fun freePort(): Int
    fun deepStubApi(): MontoyaApi
    fun localSettings(port: Int, token: String = "test-token"): McpSettings
    fun externalTlsSettings(port: Int, keystoreDir: Path, token: String = "test-token"): McpSettings
    fun startAndAwaitRunning(manager: KtorMcpServerManager, settings: McpSettings, timeoutSeconds: Long = 20)
    fun plainClient(): OkHttpClient
    fun trustAllClient(): OkHttpClient
    fun baseUrl(port: Int, tls: Boolean): String
}
```

Caller contract notes for the downstream plans:

- `startAndAwaitRunning` asserts the terminal state is `Running` and includes the actual state in the failure message so a `Failed(BindException)` is diagnosable. It does **not** tear down — the caller owns `try { ... } finally { manager.shutdown() }`. `shutdown()` terminates the shared executor, so **one manager instance per test** (RESEARCH P6). Default timeout is 20 s because external mode auto-generates a PKCS12 via `keytool` on first start (measured 1-2 s).
- `externalTlsSettings` takes a caller-supplied `java.nio.file.Path`; callers pass `Files.createTempDirectory("mcp-ac-ks")`. It never computes a home-relative path, and the `tlsKeystorePath` line carries a comment that a test must never point at the user's real keystore (T-20-14 mitigation).
- Both settings builders use `.copy(...)` on `TestSettings.baselineSettings().mcpSettings`, not the 20-line `McpSettings(...)` literal, so a new `McpSettings` field does not break Phase 20 tests.
- Both clients carry the P5 caveat in KDoc: never call `.body.string()` on a `text/event-stream` 200 response — assert `response.code`/headers inside `execute().use { }`.
- `freePort()` documents the `ServerSocket(0)` TOCTOU race (P7) as accepted in-repo precedent.

No dependency was added: OkHttp 5.4.0 is already `implementation` at `build.gradle.kts:32`.

## Verification Results

| Check | Result |
|-------|--------|
| `./gradlew test --tests '*BuildFlags*'` | BUILD SUCCESSFUL |
| `./gradlew compileTestKotlin` | BUILD SUCCESSFUL |
| `./gradlew generateBuildFlags` twice in a row | 2nd run printed `Reusing configuration cache.` / `Configuration cache entry reused.` — P11 regression guard passes |
| Generated `BuildFlags.kt` contains both constants | yes; `VERSION = "0.9.2"` == `version = "0.9.2"` |
| `./gradlew test detekt ktlintCheck` (plan-level gate, run once) | BUILD SUCCESSFUL in 54s |
| `git diff --stat detekt-baseline.xml` | empty — baseline unchanged (QUAL-07) |
| `git diff build.gradle.kts` | confined to `GenerateBuildFlagsTask` + its registration block; no dependency line added or removed |
| No `HttpURLConnection` / `~/.burp-ai-agent/certs` in code | confirmed — the only occurrences are the two mandated comment lines (34, 98); no import, no code use |

All Gradle invocations used the required `JAVA_HOME=$(/usr/libexec/java_home -v 21)` prefix.

## Deviations from Plan

### Documented judgement calls (no Rule 1-4 code deviations)

**1. Contradictory acceptance criteria on the two `grep -c` checks — resolved in favour of the `<action>` block**

- **Found during:** Task 2 acceptance verification
- **Issue:** The acceptance criteria require `grep -c 'HttpURLConnection' McpTestServerSupport.kt` to return `0` and `grep -c 'burp-ai-agent/certs'` to return `0`. The same task's `<action>` block *mandates* a KDoc paragraph naming the OkHttp-not-`HttpURLConnection` constraint and a comment stating a test must never point at `~/.burp-ai-agent/certs`. Both cannot hold simultaneously with a plain substring grep.
- **Resolution:** Kept the mandated documentation (the `<action>` block is explicit, and the greps' evident intent is "no *code* usage"). Verified the stronger property instead: both strings appear only on comment lines (34 in the KDoc, 98 in an inline `//`), there is no `HttpURLConnection` import and no code reference. Line-level evidence recorded in the table above.
- **Files:** `src/test/kotlin/com/six2dez/burp/aiagent/mcp/McpTestServerSupport.kt`
- **Commit:** `bbde4e3`

**2. REQUIREMENTS.md not marked**

- **Issue:** The plan frontmatter lists `requirements: [SEC-05, SEC-04]`, but all six plans in phase 20 declare the same two IDs, and this plan delivers only SEC-05 sub-item 5a (the version seam) plus test scaffolding for the SEC-04 gate that Plans 01/03/04 implement.
- **Resolution:** Left `REQUIREMENTS.md` untouched rather than falsely closing SEC-04/SEC-05. Also avoids a merge conflict with the sibling worktree running Plan 01, which declares the same IDs. The orchestrator/verifier should mark these at phase close.

### Auto-fixed issues

None — no bugs, missing critical functionality, or blocking issues encountered. No package-manager install was attempted (zero new dependencies, per the plan's T-20-SC disposition).

## Threat Model Follow-through

| Threat ID | Disposition | Status |
|-----------|-------------|--------|
| T-20-13 (info disclosure, `version` Property) | accept | Interpolated value is the project version from `build.gradle.kts:15` — not a secret, not attacker-influenced |
| T-20-14 (tampering, test keystore path) | **mitigate — implemented** | `externalTlsSettings` requires a caller-supplied `Path`; explicit comment forbids `~/.burp-ai-agent/certs`; no home-relative path computed anywhere in the file |
| T-20-15 (spoofing, `trustAllClient`) | accept | Confined to the test source set against a loopback server the test starts; `src/main` untouched, `McpSupervisor.kt:22-26` not modified |
| T-20-SC (package installs) | accept | No install performed; `git diff build.gradle.kts` shows no dependency change |

No new security-relevant surface outside the plan's threat model. No threat flags.

## Known Stubs

None. Both deliverables are fully wired: `BuildFlags.VERSION` is generated and asserted by a passing test, and every `McpTestServerSupport` member has a real implementation (no `TODO`, no placeholder return).

Note for the verifier: `McpTestServerSupport` has no call sites yet — by design. It is a Wave 1 prerequisite consumed by the Wave 3 pipeline tests in Plans 04 and 05. It compiles under `compileTestKotlin` and is exercised end-to-end only once those plans land.

## Follow-ups for Later Plans

- **SEC-05 5a is only half done by this plan.** `KtorMcpServerManager.kt:97` still reads `Implementation("burp-ai-agent", "0.6.0")`. This plan deliberately does not touch `KtorMcpServerManager.kt` (owned by other plans in the phase); the consuming plan must replace the literal with `BuildFlags.VERSION` and add the import `com.six2dez.burp.aiagent.BuildFlags` (use-site style is unqualified, per `McpToolCatalog.kt:3,473`).

## Commits

| Task | Commit | Description |
|------|--------|-------------|
| 1 | `4ac0efe` | `feat(20-02): generate BuildFlags.VERSION from project version (SEC-05 5a)` |
| 2 | `bbde4e3` | `test(20-02): add McpTestServerSupport for real-Netty access-control tests` |

## Self-Check: PASSED

- `build.gradle.kts` — FOUND
- `src/test/kotlin/com/six2dez/burp/aiagent/mcp/McpBuildFlagsVersionTest.kt` — FOUND
- `src/test/kotlin/com/six2dez/burp/aiagent/mcp/McpTestServerSupport.kt` — FOUND
- commit `4ac0efe` — FOUND
- commit `bbde4e3` — FOUND
