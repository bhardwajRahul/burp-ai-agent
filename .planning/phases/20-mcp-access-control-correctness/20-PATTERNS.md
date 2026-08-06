# Phase 20: MCP Access-Control Correctness - Pattern Map

**Mapped:** 2026-08-06
**Files analyzed:** 15 (4 modified main, 3 new main, 1 modified build, 1 modified doc, 6 new/extended test)
**Analogs found:** 13 / 15 (2 have no true analog — see §"No Analog Found")

---

## Corrections to Upstream Documents

Two upstream file:line references are wrong. The planner must use these instead.

| Upstream claim | Reality |
|----------------|---------|
| CONTEXT §"Reusable Assets" and RESEARCH §"Blocked-Request Logging": *"`mcp/tools/McpToolExecutorImpl.kt` `sanitizeErrorMessage`"* | `sanitizeErrorMessage` is at **`mcp/tools/McpTool.kt:229-242`** (file-private). `McpToolExecutorImpl.kt` contains no such function (`grep` returns zero hits). `MAX_ERROR_MESSAGE_LENGTH` is also in `McpTool.kt:18`. |
| RESEARCH A5 *"the registered `AuditLogger` global emitter's threading was not traced"* | **Traced.** `App.kt:69` registers `{ type, payload -> auditLogger.logEvent(type, payload) }`. `AuditLogger.logEvent` (`audit/AuditLogger.kt:54-72`) does `logFile.appendText(...)` — **blocking disk I/O**, but guarded by `if (!enabled) return` at `:58` and audit is disabled by default. P9 is real but only when the user has enabled audit. |

RESEARCH's choice of `maybeLogBackoff` over `availabilityLogged` for D-09 is **confirmed against the actual code** — see §"Shared Patterns / Rate-limited logging".

---

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|-------------------|------|-----------|----------------|---------------|
| `src/main/kotlin/.../mcp/McpAccessControlPlugin.kt` (new) | middleware | request-response | *(no Ktor plugin exists)* → `mcp/McpTls.kt` for unit shape | **none** — see §"No Analog Found" |
| `src/main/kotlin/.../mcp/McpAccessControlDecision.kt` (new, pure fn + typed result) | utility (pure) | transform | `mcp/McpServerState.kt` + `McpSupervisor.BindTakeoverOutcome` + `McpScopeFilter.rejectIfOutOfScope` | exact (composite) |
| `src/main/kotlin/.../mcp/McpBlockedRequestReporter.kt` (new) | utility (observability) | event-driven | `scanner/PassiveAiScannerAnalysis.kt::maybeLogBackoff` + `mcp/tools/McpTool.kt::emitToolTelemetry` | role-match (composite) |
| `src/main/kotlin/.../mcp/KtorMcpServerManager.kt` (modify) | service (transport bootstrap) | request-response | itself (in-place edit) | n/a |
| `src/main/kotlin/.../mcp/McpSupervisor.kt` (modify) | service (supervisor) | request-response | itself, `probeExistingServer:254-272` | n/a |
| `build.gradle.kts` (modify) | config (build) | batch | `GenerateBuildFlagsTask:72-104` (same task, add one `Property`) | exact |
| `docs/mcp-hardening.md` (modify) | docs | n/a | itself | n/a |
| `src/test/.../mcp/McpTestServerSupport.kt` (new) | test utility | n/a | `McpShutdownBoundTest.kt:33-55` + `McpTlsInJvmTest.kt:94-106` | role-match |
| `src/test/.../mcp/McpAccessControlPipelineTest.kt` (new) | test (integration, local) | request-response | `McpShutdownBoundTest.kt` | exact |
| `src/test/.../mcp/McpAccessControlExternalPipelineTest.kt` (new) | test (integration, TLS) | request-response | `McpShutdownBoundTest.kt` + `McpTlsInJvmTest.kt:94-106` | exact (composite) |
| `src/test/.../mcp/McpAccessControlDecisionTest.kt` (new) | test (pure unit) | transform | `McpRuntimeContextFactoryTest.kt` | exact |
| `src/test/.../mcp/BlockedRequestReporterTest.kt` (new) | test (pure unit) | transform | `McpRuntimeContextFactoryTest.kt` | exact |
| `src/test/.../mcp/KtorMcpServerManagerSecurityTest.kt` (extend) | test (reflection unit) | n/a | itself, `:49-68` | exact |
| `src/test/.../mcp/McpSupervisorProbeTest.kt` (new, VALIDATION row "D-02 probe") | test (reflection unit) | request-response | `McpSupervisorConnectionTest.kt:49-60` | exact |
| BuildFlags version assertion (`*BuildFlags*` per VALIDATION SC5a) | test (unit) | n/a | `McpToolCatalog.kt:3,473` (consumption side); no existing BuildFlags test | **none** |

---

## Pattern Assignments

### `src/main/kotlin/.../mcp/McpAccessControlPlugin.kt` (new — middleware, request-response)

**Analog: NONE.** `grep -rn "createApplicationPlugin\|createRouteScopedPlugin\|ApplicationPlugin" src/` returns **zero hits**. This codebase has never written a Ktor plugin — the only pipeline code in the repo is the `intercept(ApplicationCallPipeline.Call)` block being replaced (`KtorMcpServerManager.kt:176-218`). Use RESEARCH §"Code Examples" for the plugin skeleton, and the analogs below only for *file shape and conventions*.

**Closest structural analog for "a focused single-purpose unit in `mcp/`":** `mcp/McpTls.kt`

Conventions to copy from it:
- One top-level `object` (or `val` for the plugin) + one small `data class` for its inputs, in one file, no companion.
- Fail closed by returning a nullable/typed value, never by throwing.
- Multi-line `//` comments that name the requirement ID and the reason, above the load-bearing line.

`mcp/McpTls.kt:13-33`:
```kotlin
object McpTls {
    fun resolve(settings: McpSettings): McpTlsMaterial? {
        val keystorePath = settings.tlsKeystorePath.trim()
        if (keystorePath.isBlank()) return null

        val password = settings.tlsKeystorePassword.toCharArray()
        val keystoreFile = File(keystorePath)

        if (!keystoreFile.exists()) {
            if (!settings.tlsAutoGenerate) return null
            generateSelfSigned(keystoreFile, password)
        }
        ...
    }
```

**Requirement-ID comment style to match** — `mcp/McpTls.kt:48-50`:
```kotlin
            // SEC-02 / A3: pass the keystore password via the child-process environment (KS_PASS)
            // using -storepass:env / -keypass:env instead of a literal argv token, so the password
            // is never visible in a `ps aux` process listing.
```
The gate's `if (call.response.isCommitted) return@onCall` **must** carry a comment in exactly this shape citing P4/CORS, or the next reader will delete it.

**Settings-as-config analog** — `mcp/tools/ResponsePreprocessor.kt:3-16` pairs a settings `data class` with the `object` that consumes it, in the same file. `McpAccessControlConfig` (RESEARCH's shape) should live in `McpAccessControlPlugin.kt` the same way, not in a separate file.

**Where the plugin gets installed** — replace `KtorMcpServerManager.kt:176-218` and insert immediately after the CORS block that ends at `:152`, before `routing {` at `:154`. The CORS block to keep unchanged is `:130-152`.

---

### `src/main/kotlin/.../mcp/McpAccessControlDecision.kt` (new — pure decision function + typed result)

Three in-repo conventions compose into this file. Use all three.

**(1) Typed sealed result, own file, `data object` + `data class` payload** — `mcp/McpServerState.kt:1-15` (the entire file):
```kotlin
package com.six2dez.burp.aiagent.mcp

sealed class McpServerState {
    data object Starting : McpServerState()

    data object Running : McpServerState()

    data object Stopping : McpServerState()

    data object Stopped : McpServerState()

    data class Failed(
        val exception: Throwable,
    ) : McpServerState()
}
```
`GateDecision` copies this exactly: `data object Allow : GateDecision()` + `data class Deny(val status: HttpStatusCode, val reason: BlockReason, val facts: RequestFacts) : GateDecision()`. Note the blank line between members and the trailing comma on the last constructor param — ktlint 1.5.0 enforces both.

**(2) Exhaustive `when` at the single call site, no early returns** — `mcp/McpSupervisor.kt:191-224` consumes the private enum declared at `:331-335`:
```kotlin
    private fun handleBindFailure(settings: McpSettings) {
        when (attemptTakeover(settings)) {
            BindTakeoverOutcome.SHUTDOWN_REQUESTED -> { ... }

            BindTakeoverOutcome.NO_COMPATIBLE_SERVER -> {
                api.logging().logToError(
                    "MCP server failed to bind on ${settings.host}:${settings.port}. " +
                        "Port appears busy and no compatible MCP server was detected for takeover.",
                )
            }

            BindTakeoverOutcome.SHUTDOWN_REJECTED -> { ... }
        }
    }
```
```kotlin
    private enum class BindTakeoverOutcome {
        SHUTDOWN_REQUESTED,
        NO_COMPATIBLE_SERVER,
        SHUTDOWN_REJECTED,
    }
```
`BlockReason` (`origin_mismatch`, `host_mismatch`, `referer_mismatch`, `browser_no_origin`, `unauthorized`, `blank_token`) should be an `enum class` in this shape. **Deviation required:** make it `internal`, not `private` — the reporter and the tests both need it. Give each constant a `wireValue: String` property so the audit `reason` string is defined once (the enum names are `SCREAMING_SNAKE`, D-06's wire values are `snake_case`).

**(3) Pure "returns the denial, or null/Allow when permitted" helper, no logging inside** — `mcp/tools/McpScopeFilter.kt:67-74`:
```kotlin
    fun rejectIfOutOfScope(
        url: String,
        ctx: McpToolContext,
    ): String? {
        if (!ctx.scopeOnly) return null
        if (ctx.api.scope().isInScope(url)) return null
        return "Refused: $url is out of scope (mcpScopeOnly=true). Use scope_include to add it."
    }
```
Its object KDoc (`McpScopeFilter.kt:22-25`) states the invariant the gate's `evaluate` must also state:
```kotlin
 * Both helpers are pure: no logging, no audit events, no side effects beyond the necessary
 * `api.scope().isInScope(...)` call. This keeps them deterministically testable and avoids
 * doubling up on the existing tool-handler telemetry that runs in `runTool`.
```
`evaluate(facts, settings): GateDecision` must be pure in exactly this sense — the reporter is invoked by the *plugin*, from the `Deny` branch of the `when`, never from inside `evaluate`.

**Visibility / test seam.** Codebase precedent is `internal` + a KDoc note, not reflection — `redact/Redaction.kt:215-228`:
```kotlin
    // Internal test seams — expose the HKDF helpers for RFC 5869 vector assertion in
    // RedactionTest.hkdfMatchesRfc5869Vector. NOT part of the public API; only referenced
    // from the test source set.
    internal fun testHkdfExtract(
        salt: ByteArray,
        ikm: ByteArray,
    ): ByteArray = hkdfExtract(salt, ikm)
```
Make `evaluate`, `GateDecision`, `BlockReason` and `RequestFacts` all `internal` with this comment style, so `McpAccessControlDecisionTest` needs **no reflection**. (Reflection stays only in `KtorMcpServerManagerSecurityTest`, where the predicates remain `private` members of `KtorMcpServerManager`.)

**IPv6 authority helper (D-11).** Put the shared `isLoopbackAuthority(...)` next to the predicates it serves. Current buggy trio to replace, all in `KtorMcpServerManager.kt`:
```kotlin
    private fun isLoopbackHost(host: String): Boolean {          // :301-304 — the correct reference set
        val normalized = host.lowercase()
        return normalized == "localhost" || normalized == "127.0.0.1" || normalized == "::1"
    }

    private fun isValidOrigin(origin: String): Boolean =          // :306-313 — URI.toURL().host keeps brackets
        try {
            val url = URI(origin).toURL()
            val hostname = url.host.lowercase()
            hostname == "localhost" || hostname == "127.0.0.1"
        } catch (_: Exception) {
            false
        }

    private fun isValidHost(host: String, expectedPort: Int): Boolean {   // :333-347 — split(":") breaks [::1]
        return try {
            val parts = host.split(":")
            val hostname = parts[0].lowercase()
            ...
```
`isValidReferer` (`:349-356`) is byte-identical to `isValidOrigin`. The `try { … } catch (_: Exception) { false }` fail-closed wrapper is the convention — keep it.

---

### `src/main/kotlin/.../mcp/McpBlockedRequestReporter.kt` (new — utility, event-driven)

**Analog for D-09 rate limiting: `scanner/PassiveAiScannerAnalysis.kt:825-835`.** RESEARCH's pick is confirmed — this is a time-window CAS limiter; `CliBackend`'s is a one-shot latch that cannot express "N further blocks".

```kotlin
internal fun PassiveAiScanner.maybeLogBackoff(
    nowMs: Long,
    untilMs: Long,
) {
    val prev = lastBackoffLogTime.get()
    if (nowMs - prev < BACKOFF_LOG_INTERVAL_MS) return
    if (lastBackoffLogTime.compareAndSet(prev, nowMs)) {
        val seconds = ((untilMs - nowMs).coerceAtLeast(0L) / 1000L)
        api.logging().logToOutput("[PassiveAiScanner] AI backend backoff active (${seconds}s remaining)")
    }
}
```
State lives on the owning class as `internal val lastBackoffLogTime = AtomicLong(0)` (`scanner/PassiveAiScanner.kt:66`); the interval is a file-private `private const val BACKOFF_LOG_INTERVAL_MS = 10_000L` (`PassiveAiScannerAnalysis.kt:24`). Copy: `private const val BLOCK_LOG_WINDOW_MS = 60_000L`, per-reason state in a `ConcurrentHashMap<BlockReason, ReasonWindow>`, read-then-CAS (never `synchronized`).

The rejected one-shot alternative, for the record — `backends/cli/CliBackend.kt:27-28,74-79`:
```kotlin
    /** Avoid spamming "Found X CLI" on every isAvailable() call. */
    private val availabilityLogged = AtomicBoolean(false)
...
        if (available && availabilityLogged.compareAndSet(false, true)) {
```
Its one-line KDoc *stating the flooding problem being solved* is worth copying onto the reporter's window map.

**Concurrent-map + per-entry timestamp analog: `mcp/tools/ScannerTaskRegistry.kt:11-24`** (nearest in-package example of `ConcurrentHashMap` + `AtomicLong` + a small private `data class` holding `createdAtMs`):
```kotlin
object ScannerTaskRegistry {
    private data class TimedScanTask(
        val task: ScanTask,
        val createdAtMs: Long,
    )

    private val idToTask: MutableMap<String, TimedScanTask> = ConcurrentHashMap()
    private val ttlMs = AtomicLong(TimeUnit.MINUTES.toMillis(DEFAULT_TTL_MINUTES.toLong()))
```
**Do not** copy its `Executors.newSingleThreadScheduledExecutor` cleaner (`:20-32`) — the reporter must not own a thread; expire the window lazily on the next block for that reason.

**Analog for D-06 audit emission: `mcp/tools/McpTool.kt:112-136, 222-227`.**
```kotlin
    val baseTelemetry =
        linkedMapOf<String, Any?>(
            "tool" to name,
            "toolType" to toolType,
            "hasArgs" to hasArgs,
        ).also { payload ->
            if (argsSha256 != null) {
                payload["argsSha256"] = argsSha256
            }
        }

    if (!context.isToolEnabled(name)) {
        emitToolTelemetry(MCP_TOOL_EVENT_BLOCKED, baseTelemetry + mapOf("reason" to "disabled"))
```
```kotlin
private fun emitToolTelemetry(
    type: String,
    payload: Map<String, Any?>,
) {
    AuditLogger.emitGlobal(type, payload)
}
```
Copy: `linkedMapOf<String, Any?>` base + `+ mapOf("reason" to …)` overlay, and a one-line private `emitTransportTelemetry(type, payload)` wrapper. **Blocker confirmed:** `MCP_TOOL_EVENT_BLOCKED` is `private const val` at `McpTool.kt:21` — unreachable. Declare a new `private const val MCP_TRANSPORT_EVENT_BLOCKED = "mcp_transport_blocked"` in the reporter file (same file-private `const` convention as `McpTool.kt:19-21`).

**Analog for "build payload in a pure helper, emit at the call site": `mcp/McpToolContext.kt:70-72`**
```kotlin
        SecretTripwire
            .detectAndBuild(finalText, path = "mcp", sessionId = supervisor?.currentSessionId())
            ?.let { AuditLogger.emitGlobal("secret_tripwire_detect", it) }
```
and its builder `redact/SecretTripwire.kt:163-170`, whose KDoc (`:151-162`) documents *what is deliberately absent from the payload*. The reporter's payload builder needs the same KDoc discipline for D-10 (hashed by default, plaintext only under verbose).

**Analog for D-07 sanitize + truncate: `mcp/tools/McpTool.kt:229-242`** (note the corrected path):
```kotlin
private fun sanitizeErrorMessage(e: Exception): String {
    var message = e.message.orEmpty().ifBlank { "Unexpected MCP tool error" }
    message = unixAbsPathRegex.replace(message, "[path]")
    message = windowsAbsPathRegex.replace(message, "[path]")
    message = packageClassRegex.replace(message, "[internal]")
    message = message.replace(Regex("\\s+"), " ").trim()
    if (message.isBlank()) {
        message = "Unexpected MCP tool error"
    }
    if (message.length > MAX_ERROR_MESSAGE_LENGTH) {
        message = message.take(MAX_ERROR_MESSAGE_LENGTH).trimEnd() + "..."
    }
    return message
}
```
Copy the exact structure: file-private `val` regexes at the top of the file (`McpTool.kt:22-24`), a file-private `const val MAX_… = 500` cap (`:18`), sequential `replace` calls, and the `take(N).trimEnd() + "..."` truncation idiom. The gate's version substitutes a control-character strip (`Regex("[\\p{Cntrl}]")`) for the path regexes.

**Hashing helper (D-10): `audit/Hashing.kt:6-13`** — signature `Hashing.sha256Hex(value: String): String`, package `com.six2dez.burp.aiagent.audit`.
```kotlin
object Hashing {
    fun sha256Hex(value: String): String {
        val d =
            MessageDigest
                .getInstance("SHA-256")
                .digest(value.toByteArray(StandardCharsets.UTF_8))
        return d.joinToString("") { "%02x".format(it) }
    }
}
```
Call site convention for a nullable value — `mcp/tools/McpToolHandlers.kt:136`: `argsJson?.takeIf { it.isNotBlank() }?.let { Hashing.sha256Hex(it) } ?: ""`.

**Output-tab sink: `KtorMcpServerManager.kt:192`** is what exists today and is exactly the log-injection hole D-07 closes:
```kotlin
                                    api.logging().logToOutput("Blocked MCP request from origin: $origin")
```
The reporter takes `logToOutput: (String) -> Unit` (or the `MontoyaApi`) as a constructor param so `BlockedRequestReporterTest` can capture lines without a mock deep-stub.

**P9 threading, now traced:** `App.kt:69` → `AuditLogger.logEvent` → `logFile.appendText` (`AuditLogger.kt:68`) is blocking disk I/O, short-circuited by `if (!enabled) return` (`:58`). Document this in the reporter KDoc; if the planner wants a hand-off, there is no existing async-emit precedent to copy.

---

### `src/main/kotlin/.../mcp/KtorMcpServerManager.kt` (modify — service, request-response)

**Analog: itself.** Edits are confined to five sites.

| Site | Lines | Change |
|------|-------|--------|
| Server version | `:95-105` | `Implementation("burp-ai-agent", "0.6.0")` → `Implementation("burp-ai-agent", BuildFlags.VERSION)`. **Do not touch the name half (P12).** |
| CORS block | `:130-152` | unchanged — the gate installs immediately after it |
| `routing {}` | `:154-174` | health handler `:155-158` becomes mode-aware (D-02); shutdown handler `:159-173` unchanged (D-04) |
| security `intercept` | `:176-218` | **deleted**, replaced by `install(McpAccessControl) { … }` above `routing` |
| predicates | `:284-356` | `isAuthorized` blank-token guard; `isValidHost`/`isValidOrigin`/`isValidReferer` share one bracket-aware helper (D-11) |

**Import-block convention** (`:1-39`): a single alphabetised block, no wildcards, `java.*` after the third-party groups, ktlint-enforced. Adding the plugin means `+ io.ktor.server.application.install` (already at `:16`) and removing `io.ktor.server.application.ApplicationCallPipeline` (`:14`) once the `intercept` goes.

**BuildFlags consumption analog** — `mcp/McpToolCatalog.kt:3,473`:
```kotlin
import com.six2dez.burp.aiagent.BuildFlags
...
    fun available(storeBuild: Boolean = BuildFlags.STORE_BUILD): List<McpToolDescriptor> = ...
```
Same package prefix, no qualification at the use site.

**Fail-closed startup guards to extend for the blank-token check (SEC-05 5c)** — `:80-85`:
```kotlin
                if (settings.externalEnabled && !settings.tlsEnabled) {
                    throw IllegalStateException("External MCP access requires TLS. Enable TLS to continue.")
                }
                if (!settings.externalEnabled && !isLoopbackHost(settings.host)) {
                    throw IllegalStateException("MCP host must be loopback when external access is disabled.")
                }
```
RESEARCH recommends *logging* rather than throwing for a blank external token (so the port still binds and every request 401s). If the planner chooses to throw instead, it belongs here and it is a behaviour change worth calling out.

**Blank-token guard placement** — `:284-290`, one line before the comparison:
```kotlin
    private fun isAuthorized(
        authHeader: String,
        token: String,
    ): Boolean {
        val expected = "Bearer $token"
        return constantTimeEquals(authHeader, expected)
    }
```
Keep `constantTimeEquals` (`:292-299`, `MessageDigest.isEqual`) untouched.

---

### `src/main/kotlin/.../mcp/McpSupervisor.kt` (modify — service, request-response)

**Analog: itself, `:254-272`.** The assertion to make mode-aware is the second conjunct:
```kotlin
    private fun probeExistingServer(settings: McpSettings): Boolean =
        try {
            val scheme = if (settings.tlsEnabled) "https" else "http"
            val url = URI.create("$scheme://${settings.host}:${settings.port}/__mcp/health").toURL()
            val conn = openConnection(url, settings.tlsEnabled)
            try {
                conn.requestMethod = "GET"
                conn.connectTimeout = 800
                conn.readTimeout = 800
                conn.connect()
                conn.responseCode in 200..299 &&
                    conn.getHeaderField("X-Burp-AI-Agent") == "mcp"
            } finally {
                conn.disconnect()
            }
        } catch (e: Exception) {
            api.logging().logToOutput("MCP probe failed on ${settings.host}:${settings.port}: ${e.message}")
            false
        }
```
`settings` is already in scope, so the mode-mirror needs no signature change. The `try/finally { conn.disconnect() }` + outer `catch → logToOutput → false` structure is the convention; `requestRemoteShutdownWithToken` (`:274-289`) repeats it verbatim.

The `expression-body fun … = try { … }` form (no braces on the function, `=` then `try`) is used by both — match it.

---

### `build.gradle.kts` (modify — config, batch)

**Analog: `GenerateBuildFlagsTask` at `:72-104`, extended in place.**
```kotlin
abstract class GenerateBuildFlagsTask : DefaultTask() {
    @get:Input
    abstract val storeBuildFlag: Property<Boolean>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun generate() {
        val pkgDir =
            outputDir
                .get()
                .asFile
                .resolve("com/six2dez/burp/aiagent")
                .also { it.mkdirs() }
        pkgDir.resolve("BuildFlags.kt").writeText(
            """
package com.six2dez.burp.aiagent

object BuildFlags {
    const val STORE_BUILD = ${storeBuildFlag.get()}
}
            """.trimIndent() + "\n",
        )
    }
}

val generateBuildFlags by tasks.registering(GenerateBuildFlagsTask::class) {
    group = "build"
    description = "Generates BuildFlags.kt with a compile-time store-build flag"
    storeBuildFlag.set(storeBuild)
    outputDir.set(layout.buildDirectory.dir("generated/buildflags"))
}
```
Add `@get:Input abstract val version: Property<String>` and, in the registration block, `version.set(project.version.toString())` — **at configuration time**. The `@TaskAction` may only read `version.get()`; touching `project` there fails under `org.gradle.configuration-cache=true` (P11). Emit `const val VERSION = "${version.get()}"` into the same `object BuildFlags`.

No wiring changes needed: `sourceSets.main` at `:106-110` already routes the generated dir through `generateBuildFlags.flatMap { it.outputDir }`, and ktlint excludes `**/generated/**` (`:187`).

---

### `docs/mcp-hardening.md` (modify — docs, D-12)

**Analog: itself.** Structure is `## Heading` + a numbered list, one imperative sentence per item, no code fences. Two items to correct:

- §"External Access" item 4: *"Validate `Authorization: Bearer <token>` is sent on every request."*
- §"Verification" item 2: *"Test denied request (missing/invalid token) returns auth error."*

Both must state the `/__mcp/health` exemption (D-01) and the local-only `X-Burp-AI-Agent` header (D-02). §"Credential Storage" items 1-3 are the precedent for a longer, caveat-bearing item — the file already tolerates two-sentence entries when a security nuance needs explaining, so the exemption note does not need its own section.

Also worth adding per RESEARCH §"SSE and Authentication": browser `EventSource` cannot set headers, so a browser-based MCP client is unsupported in external mode.

---

### `src/test/.../mcp/McpTestServerSupport.kt` (new — test utility)

**Analog: `McpShutdownBoundTest.kt:33-55`** — this class already factors out the two helpers the support file must own:
```kotlin
    private fun freePort(): Int = ServerSocket(0).use { it.localPort }

    private fun defaultSettings(port: Int): McpSettings =
        McpSettings(
            enabled = true,
            host = "127.0.0.1",
            port = port,
            externalEnabled = false,
            stdioEnabled = false,
            token = "test-token",
            allowedOrigins = emptyList(),
            tlsEnabled = false,
            ...
        )
```
**Deviation recommended:** use the `TestSettings` builder instead of the 20-line literal, per `McpTlsInJvmTest.kt:97-102`:
```kotlin
        val settings =
            TestSettings.baselineSettings().mcpSettings.copy(
                tlsKeystorePath = ks.absolutePath,
                tlsKeystorePassword = "auto-pass",
                tlsAutoGenerate = true,
            )
```
`TestSettings` is at `src/test/kotlin/com/six2dez/burp/aiagent/TestSettings.kt`; `.mcpSettings.copy(...)` survives new `McpSettings` fields, the literal does not.

**Start-and-await-Running helper** — `McpServerIntegrationTest.kt:50-67`:
```kotlin
        val terminalState = AtomicReference<McpServerState?>()
        val started = CountDownLatch(1)
        manager.start(
            settings,
            PrivacyMode.STRICT,
            determinismMode = false,
            preprocessSettings = ResponsePreprocessorSettings(),
        ) { state ->
            if (state is McpServerState.Running || state is McpServerState.Failed) {
                terminalState.set(state)
                started.countDown()
            }
        }
```
Followed by (`:64-67`, `:91-93`):
```kotlin
        try {
            assertTrue(started.await(10, TimeUnit.SECONDS), "MCP server did not start in time.")
            val state = terminalState.get()
            assertTrue(state is McpServerState.Running, "MCP failed to start: $state")
            ...
        } finally {
            manager.shutdown()
        }
```
The `try { asserts } finally { manager.shutdown() }` wrapper is mandatory (P6 — one manager per test; `shutdown()` kills the executor).

**Deep-stub Montoya mock** — `McpServerIntegrationTest.kt:24-25`:
```kotlin
        val api = mock<MontoyaApi>(defaultAnswer = Answers.RETURNS_DEEP_STUBS)
        whenever(api.burpSuite().version().edition()).thenReturn(BurpSuiteEdition.PROFESSIONAL)
```

**Temp-dir keystore (external mode)** — `McpTlsInJvmTest.kt:94-106`:
```kotlin
    @Test
    fun resolve_autoGeneratesWhenKeystoreMissing() {
        val dir = Files.createTempDirectory("mcp-tls-resolve").toFile()
        val ks = File(dir, "auto.p12")
```
Never `~/.burp-ai-agent/certs`.

**Trust-all OkHttp client: no test analog exists.** No test in the repo builds an `OkHttpClient` with a custom `SSLSocketFactory` — the only OkHttp construction in tests is bare (`backends/perplexity/PerplexityBackendFactoryTest.kt:40`: `private val httpClient = OkHttpClient()`). The **main**-source precedent for the trust-all pieces is `mcp/McpSupervisor.kt:22-26` (`HostnameVerifier`, `HttpsURLConnection`, `SSLContext`, `TrustManager`, `X509TrustManager` imports) driving its loopback-only trust override, and `McpSupervisorConnectionTest.kt` is the test that pins that behaviour. Use RESEARCH §"Code Examples / Test client" verbatim for the new helper; it is new ground.

---

### `src/test/.../mcp/McpAccessControlPipelineTest.kt` and `McpAccessControlExternalPipelineTest.kt` (new — integration)

**Analog: `McpShutdownBoundTest.kt`.** It is the canonical "binds a real port, drives the real manager, name deliberately outside the exclusion globs" class. Copy its class KDoc verbatim in spirit — `McpShutdownBoundTest.kt:16-28`:
```kotlin
/**
 * SC5a — verifies that KtorMcpServerManager.stop() is bounded (never hangs forever)
 * and that a stop→start→stop restart cycle works correctly.
 *
 * The stop() method must:
 *  1. Complete within a bounded timeout (well under 30 s in tests).
 *  ...
 *
 * Naming note: McpShutdownBoundTest is NOT matched by the *IntegrationTest / *RestartPolicyTest
 * / *ConcurrencyTest exclusion globs under -PexcludeHeavyTests=true, so it runs in the
 * standard suite.
 */
```
That "Naming note" paragraph is the existing in-repo answer to RESEARCH Open Question 1 and to the VALIDATION naming constraint (`build.gradle.kts:149-157`). **Both new pipeline classes must carry an equivalent note**, or a future rename will silently drop the SC4 gate from the PR gate.

Also copy its per-assertion failure messages — every `assertTrue` in that file carries a diagnostic string naming the regression it guards (`:145-147`: *"If RejectedExecutionException — stop() terminated the executor (regression)."*). SC4 assertions should name the bypass they guard.

`McpServerIntegrationTest.kt` remains the analog for the **shutdown 401/200** assertions (SC6, `:77-90`) — but its `httpRequest` helper (`:102-123`, `HttpURLConnection`) must **not** be copied for the new tests (P1: it drops `Origin` and overwrites `Host`).

---

### `src/test/.../mcp/McpAccessControlDecisionTest.kt` and `BlockedRequestReporterTest.kt` (new — pure unit)

**Analog: `McpRuntimeContextFactoryTest.kt`** — plain JUnit 5, `org.junit.jupiter.api.Assertions.*` static imports (not `kotlin.test`), one behaviour per `@Test`, snake_case-ish `methodName_behaviourDescription` naming:
```kotlin
class McpRuntimeContextFactoryTest {
    @Test
    fun create_buildsContextFromSettingsAndRuntimeFlags() {
```
The same naming is used in `KtorMcpServerManagerSecurityTest` (`isAuthorized_acceptsOnlyExactBearerToken`) and `KtorMcpCorsPolicyTest` (`parseExternalCorsHosts_rejectsUnsupportedScheme`). Note `detekt.yml:15-17` excludes `**/test/**` from `FunctionNaming`, so backtick-quoted sentence names are also legal (`AnthropicBackendTransportRoutingTest` uses them) — but the `mcp` package is uniformly `camelCase_underscore`. Match the package, not the repo.

For `BlockedRequestReporterTest`'s D-09 window assertions, inject the clock: `maybeLogBackoff` takes `nowMs: Long` as a **parameter** rather than calling `System.currentTimeMillis()` internally (`PassiveAiScannerAnalysis.kt:825-828`), which is precisely what makes it testable without sleeping. The reporter must do the same.

---

### `src/test/.../mcp/KtorMcpServerManagerSecurityTest.kt` (extend — reflection unit)

**Analog: itself, `:49-68`.** Two existing invoker helpers to reuse for the new IPv6 / blank-token cases:
```kotlin
    private fun invokeBoolean(
        methodName: String,
        vararg args: String,
    ): Boolean {
        val method: Method =
            manager.javaClass.getDeclaredMethod(
                methodName,
                String::class.java,
                String::class.java,
            )
        method.isAccessible = true
        return method.invoke(manager, args[0], args[1]) as Boolean
    }
```
`isValidHost(String, Int)` needs a **new** invoker — `invokeBoolean` hardcodes two `String` params. Follow `McpSupervisorConnectionTest.kt:49-60` for the mixed-signature form, which uses `Boolean::class.javaPrimitiveType`; for `isValidHost` the second arg is `Int::class.javaPrimitiveType`:
```kotlin
        val method: Method =
            supervisor.javaClass.getDeclaredMethod(
                "openConnection",
                URL::class.java,
                Boolean::class.javaPrimitiveType,
            )
        method.isAccessible = true
```
Existing assertion style to extend (`:15-20`):
```kotlin
    @Test
    fun isAuthorized_acceptsOnlyExactBearerToken() {
        assertTrue(invokeBoolean("isAuthorized", "Bearer token-123", "token-123"))
        assertFalse(invokeBoolean("isAuthorized", "", "token-123"))
```
Add `assertFalse(invokeBoolean("isAuthorized", "Bearer ", ""))` here — one line, and it is SC4 gate assertion #9.

The class instantiates the manager with a bare `mock<MontoyaApi>()` (no deep stubs) at `:12` — sufficient because no method under test touches `api`.

---

### `src/test/.../mcp/McpSupervisorProbeTest.kt` (new — reflection unit, D-02 probe)

**Analog: `McpSupervisorConnectionTest.kt`.** It already reaches `McpSupervisor`'s private methods by reflection and, critically, shows how to feed a **fake connection** into the probe without a network:
```kotlin
@Suppress("DEPRECATION")
class McpSupervisorConnectionTest {
    private val supervisor = McpSupervisor(mock<MontoyaApi>())

    @Test
    fun openConnection_loopbackTls_setsCustomTrustAndHostnameVerifier() {
        val url = URL(null, "https://localhost:8443/test", connectionHandler())
        val connection = invokeOpenConnection(url, tlsEnabled = true) as FakeHttpsURLConnection
```
The `URLStreamHandler` + `FakeHttpsURLConnection` machinery at the bottom of that file is directly reusable for asserting "local mode requires the header, external mode accepts 2xx alone". Alternatively, drive the real `probeExistingServer` against a live `KtorMcpServerManager` from `McpTestServerSupport` — which is what VALIDATION's row actually asks for. Both are in-repo idioms; the live-server variant gives a stronger D-02 signal.

`McpSupervisorRestartPolicyTest.kt` is **not** the analog here — it fakes `McpTakeoverClient` (the interface at `McpSupervisor.kt:28-32`), which is exactly why `probeExistingServer` has zero coverage today.

---

## Shared Patterns

### Fail closed, return a typed result, never throw
**Source:** `mcp/McpServerState.kt:1-15` (sealed shape), `mcp/McpSupervisor.kt:331-335` + `:191-224` (enum + exhaustive `when`), `mcp/tools/McpScopeFilter.kt:67-74` (pure decide-only helper), `mcp/McpTls.kt:14-22` (nullable fail-closed).
**Apply to:** `McpAccessControlDecision.kt`, `McpAccessControlPlugin.kt`.
Also satisfies detekt `ReturnCount` (P8 — #2 baseline offender; QUAL-07 forbids growing `detekt-baseline.xml`).

### Rate-limited logging (window + CAS, injected clock)
**Source:** `scanner/PassiveAiScannerAnalysis.kt:825-835` + `scanner/PassiveAiScanner.kt:66` + `PassiveAiScannerAnalysis.kt:24`.
**Apply to:** `McpBlockedRequestReporter.kt`.
```kotlin
    val prev = lastBackoffLogTime.get()
    if (nowMs - prev < BACKOFF_LOG_INTERVAL_MS) return
    if (lastBackoffLogTime.compareAndSet(prev, nowMs)) { … }
```
Lock-free; the gate runs on Netty event-loop threads (P9).

### Audit event emission (flat map + `emitGlobal`, hashes not values)
**Source:** `mcp/tools/McpTool.kt:124-136` (payload build), `:222-227` (thin wrapper), `audit/AuditLogger.kt:26-31` (`emitGlobal` is a no-op with no emitter registered), `mcp/McpToolContext.kt:70-72` (build-then-emit at the call site), `audit/Hashing.kt:6-13` (`sha256Hex`).
**Apply to:** `McpBlockedRequestReporter.kt`.
Event-type constants are `private const val` at the top of the emitting file (`McpTool.kt:19-21`) — `MCP_TOOL_EVENT_BLOCKED` is unreachable from `mcp/`, so declare `mcp_transport_blocked` locally.

### Value sanitization before logging (strip → collapse → cap → `"..."`)
**Source:** `mcp/tools/McpTool.kt:229-242` with `:18` (`MAX_ERROR_MESSAGE_LENGTH = 500`) and `:22-24` (file-private regex vals).
**Apply to:** `McpBlockedRequestReporter.kt` (D-07), for `Origin`, `Host`, `Referer`, `User-Agent`, `path`.

### `internal` + KDoc note as the test seam (not reflection)
**Source:** `redact/Redaction.kt:215-228`.
**Apply to:** all new `mcp/` production symbols the new unit tests touch. Reflection stays confined to `KtorMcpServerManagerSecurityTest` / `McpSupervisorProbeTest`, where the targets are pre-existing `private` members.

### Real-server test lifecycle
**Source:** `McpServerIntegrationTest.kt:50-67, 91-93`; `McpShutdownBoundTest.kt:33-55`.
**Apply to:** `McpTestServerSupport.kt` and both pipeline tests.
`CountDownLatch` on `Running`/`Failed` → assert → `finally { manager.shutdown() }`; one manager per test.

### Test-class naming vs the heavy-test exclusion globs
**Source:** `McpShutdownBoundTest.kt:25-27` KDoc; enforced by `build.gradle.kts:149-157` / `:160-171`.
**Apply to:** every new test class in this phase.

### Requirement-ID comments on load-bearing lines
**Source:** `mcp/McpTls.kt:48-50` (`SEC-02 / A3: …`), `KtorMcpServerManager.kt:239-243` (`REL-02/SC5a: …` with an explicit "do NOT do X" rationale), `McpToolContext.kt:42-47`.
**Apply to:** the `isCommitted` guard (P4), the health-route mode branch (D-02), the probe mode-mirror (D-02), the `version.set(...)` line (P11).
The `KtorMcpServerManager.kt:239-243` comment is the model for anything whose *removal* would silently reintroduce a bug — which describes the entire gate.

---

## No Analog Found

| File | Role | Data Flow | Reason |
|------|------|-----------|--------|
| `mcp/McpAccessControlPlugin.kt` | middleware | request-response | **No Ktor `ApplicationPlugin` exists anywhere in this repo.** `grep -rn "createApplicationPlugin\|createRouteScopedPlugin\|ApplicationPlugin" src/` → 0 hits. The only pipeline code is the `intercept` block being removed. Use RESEARCH §"Code Examples / Gate plugin shape" for the skeleton and `mcp/McpTls.kt` / `mcp/tools/ResponsePreprocessor.kt:3-16` for file shape, KDoc and comment conventions only. |
| trust-all `OkHttpClient` test helper (inside `McpTestServerSupport.kt`) | test utility | request-response | No test builds an OkHttp client with a custom `SSLSocketFactory`; the only OkHttp construction in tests is bare (`PerplexityBackendFactoryTest.kt:40`). Main-source precedent for the TLS pieces is `McpSupervisor.kt:22-26`. Use RESEARCH §"Code Examples / Test client" verbatim. |
| BuildFlags version assertion (SC5a) | test | n/a | No test currently references `BuildFlags`; the only consumer is `McpToolCatalog.kt:473`. Simplest in-repo-consistent option: assert `Implementation.version == BuildFlags.VERSION` from a small unit test in the `mcp` package, styled after `McpRuntimeContextFactoryTest`. Note VALIDATION's command filter is `--tests '*BuildFlags*'`, so the class name must contain `BuildFlags`. |

---

## Metadata

**Analog search scope:**
`src/main/kotlin/com/six2dez/burp/aiagent/{mcp,mcp/tools,mcp/external,audit,scanner,backends,redact,config}`,
`src/test/kotlin/com/six2dez/burp/aiagent/{mcp,backends}`, `build.gradle.kts`, `detekt.yml`, `docs/`.

**Files read in full:** `mcp/KtorMcpServerManager.kt`, `mcp/McpToolContext.kt`, `mcp/McpTls.kt`, `mcp/McpServerState.kt`, `mcp/McpRequestLimiter.kt`, `mcp/McpServerManager.kt`, `mcp/tools/McpTool.kt`, `audit/AuditLogger.kt`, `audit/Hashing.kt`, `build.gradle.kts`, `docs/mcp-hardening.md`, `src/test/.../McpServerIntegrationTest.kt`, `src/test/.../McpShutdownBoundTest.kt`, `src/test/.../KtorMcpServerManagerSecurityTest.kt`, `src/test/.../KtorMcpCorsPolicyTest.kt`, `src/test/.../McpTlsInJvmTest.kt`, `src/test/.../McpRuntimeContextFactoryTest.kt`.

**Files read in targeted ranges:** `mcp/McpSupervisor.kt` (`1-80`, `190-290`, `331-335`), `scanner/PassiveAiScannerAnalysis.kt` (`815-835`), `backends/cli/CliBackend.kt` (`1-100`), `redact/SecretTripwire.kt` (`120-189`), `redact/Redaction.kt` (`205-230`), `mcp/tools/McpScopeFilter.kt` (`1-80`), `mcp/tools/ScannerTaskRegistry.kt` (`1-60`), `mcp/tools/ResponsePreprocessor.kt` (`1-55`), `config/McpSettings.kt` (`1-40`), `src/test/.../McpSupervisorConnectionTest.kt` (`1-60`), `src/test/.../AnthropicBackendTransportRoutingTest.kt` (`1-60`), `src/test/.../TestSettings.kt` (`1-50`).

**Pattern extraction date:** 2026-08-06
