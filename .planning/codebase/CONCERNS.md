# Codebase Concerns

**Analysis Date:** 2026-05-13

---

## Known Bugs

### Release pipeline regression — JAR version mismatch (#62)

- **Symptoms:** A release tagged `v0.6.1` ships a JAR named `Custom-AI-Agent-0.6.0.jar`. The published artifact contains v0.6.0 code.
- **Root cause (verified):** The version string is hardcoded in `build.gradle.kts` at line 14 (`version = "0.6.1"`). The release workflow at `.github/workflows/release.yml` reads the Git tag from `GITHUB_REF` (line 53) only to extract CHANGELOG release notes — it never sets or validates the Gradle project version from the tag. When the v0.6.1 tag was created it pointed to commit `94bc4a4a` whose `build.gradle.kts` still read `version = "0.6.0"`. `git show v0.6.1:build.gradle.kts` confirms this.
- **Files:** `build.gradle.kts:14`, `.github/workflows/release.yml:53-54`
- **No version guard exists:** The release workflow has no step that cross-checks `TAG` against the Gradle project version. The wildcard glob `Custom-AI-Agent-*.jar` in the upload step (`.github/workflows/release.yml:72`) silently accepts the wrong-version file.
- **Fix approach:** Either (a) derive the Gradle version from the tag at build time: `version = System.getenv("GITHUB_REF_NAME")?.removePrefix("v") ?: "dev"` in `build.gradle.kts`, or (b) add a release-workflow step that asserts `"${TAG#v}" == $(./gradlew properties -q --no-daemon | grep ^version: | awk '{print $2}')` and fails fast on mismatch.
- **Blocks:** v0.7.0 release — this must be resolved before the next tag is pushed.

---

## Tech Debt

### Hardcoded version string — no tag-based derivation

- **Issue:** `build.gradle.kts:14` sets `version = "0.6.1"` as a literal string. Bumping the version requires a manual file edit before tagging. The release workflow does not inject the tag version. Any tag push that forgets the bump re-introduces the #62 bug.
- **Files:** `build.gradle.kts:14`, `.github/workflows/release.yml`
- **Impact:** Release mis-naming; shipped JAR version does not match the GitHub release tag; checksums match the wrong version string.
- **Fix approach:** Replace with `System.getenv("GITHUB_REF_NAME")?.removePrefix("v") ?: "0.6.1-SNAPSHOT"` in `build.gradle.kts`, plus a CI assertion step confirming tag and Gradle version alignment.

### `ktlintCheck` is non-blocking in PR gate

- **Issue:** In `.github/workflows/build.yml:23`, the `ktlint check (non-blocking until baseline is clean)` step has `continue-on-error: true`. This means linting violations do not fail PRs.
- **Files:** `.github/workflows/build.yml:22-23`
- **Impact:** Formatting debt accumulates silently; `release.yml:29` does run ktlintCheck without `continue-on-error`, so a style violation that slips through PR review can block a release.
- **Fix approach:** Once the ktlint baseline is clean, remove `continue-on-error: true` from `build.yml` so the PR gate and release gate behave identically.

### Three separate `AgentSettingsRepository` instances with separate caches

- **Issue:** `App.kt:63`, `MainTab.kt:62`, and `SettingsPanel.kt:58` each construct their own `AgentSettingsRepository` instance, each backed by an independent `AtomicReference<AgentSettings?>` cache. The fix for the v0.6.0 stale-cache bug wires `MainTab.settingsRepo.invalidate()` via the `onSettingsChanged` callback (`MainTab.kt:492`), but `App.kt`'s instance is never invalidated after a settings save. The passive and active scanners are injected as `{ settingsRepo.load() }` lambdas (`App.kt:74,76`), so they call through `App.kt`'s potentially-stale cache.
- **Files:** `App.kt:53,63,74,76`, `MainTab.kt:62,492`, `SettingsPanel.kt:58`
- **Impact:** After a user saves settings, `PassiveAiScanner` and `ActiveAiScanner` may operate on a one-Burp-session-old settings snapshot until something clears `App.kt`'s cache. In practice this is mitigated because `App.kt:74-76` use lambdas (not a captured snapshot), so each scan invocation calls `settingsRepo.load()` which is a cache hit on a warm cache — but a previously wrong cache value persists until something writes through it.
- **Safe modification rule:** Any new settings UI component that persists settings must (1) own a `AgentSettingsRepository`, (2) call `save()` after persisting, and (3) invoke `onSettingsChanged` or otherwise invalidate all other repo instances. The safest long-term fix is a singleton `AgentSettingsRepository` or an event bus that broadcasts invalidation.

### Large monolithic files

- **Issue:** Several files exceed 2,000 lines and mix distinct concerns. `SettingsPanel.kt` (2,599 lines) handles UI layout, validation, save logic, and scanner status. `McpTools.kt` (2,582 lines) contains the legacy tool registration, schema mapping, and catalog describe functions in addition to the new handler-based system. `PassiveAiScanner.kt` (2,546 lines) covers rate limiting, dedup caching, batch analysis, AI interaction, and issue creation.
- **Files:** `src/main/kotlin/com/six2dez/burp/aiagent/ui/SettingsPanel.kt`, `src/main/kotlin/com/six2dez/burp/aiagent/mcp/tools/McpTools.kt`, `src/main/kotlin/com/six2dez/burp/aiagent/scanner/PassiveAiScanner.kt`
- **Impact:** Hard to navigate; increases review burden; raises risk of merge conflicts.
- **Fix approach:** Extract sub-panels from `SettingsPanel` into dedicated `JPanel` subclasses under `ui/panels/`; move the legacy `registerToolsLegacy` block and descriptor/schema helpers out of `McpTools.kt` into a separate file.

---

## Fragile Areas

### UTF-8 charset in `CliBackend` process stdout

- **Issue:** `CliBackend.kt:178` and `CliBackend.kt:580` construct `BufferedReader(InputStreamReader(process.inputStream))` without specifying a charset. This inherits the JVM platform default charset. On a Windows host with a non-UTF-8 system locale (common for CJK languages or older Windows versions), CLI tool output containing multibyte characters will be mojibaked. The analogous bug in `MontoyaHttpTransport` was fixed in v0.6.1 (`MontoyaHttpTransport.kt:85`); the CLI path was not addressed at the same time.
- **Files:** `src/main/kotlin/com/six2dez/burp/aiagent/backends/cli/CliBackend.kt:178`, `src/main/kotlin/com/six2dez/burp/aiagent/backends/cli/CliBackend.kt:580`
- **Pattern to enforce:** Any `InputStreamReader(stream)` reading AI tool or backend output must pass an explicit `Charsets.UTF_8` argument, matching the fix already applied at `OpenAiCompatibleBackend.kt:353`.
- **Test coverage:** `MontoyaHttpTransportUtf8Test` covers the HTTP transport path but there is no equivalent test for CLI stdout decoding.
- **Fix approach:** Change both occurrences to `InputStreamReader(process.inputStream, Charsets.UTF_8)`.

### Redaction regex coverage gaps

- **Issue:** `Redaction.kt` defines a hand-curated set of patterns. References below are anchored on **symbol names**, which are durable; the line numbers are as of Phase 21 and are a convenience only — an earlier revision of this entry cited a line range that the Phase 21 rewrite silently invalidated. Known intentional gaps: `jwtRegex` (`Redaction.kt:170`) is commented "not perfect by design" — it matches three-part base64url tokens starting with `eyJ` but misses JWTs embedded in JSON string values or HTML. `authHeaderRegex` (`Redaction.kt:62`) is an exact-name alternation and still misses vendor-specific auth headers such as `x-shopify-access-token` and `stripe-signature`; folding those in is **not** free under the Phase 21 mechanism, because `authHeaderRegex` would need the same treatment rather than inheriting it, which is why it stays deferred. **Corrected in Phase 21:** this entry previously described `urlTokenParamRegex` as matching a fixed set of parameter names, which is now obsolete — `urlTokenParamRegex` (`Redaction.kt:246`), `formBodyParamRegex` (`Redaction.kt:257`) and `jsonSecretKeyRegex` (`Redaction.kt:274`) are all driven by the shared `SENSITIVE_KEY_EXPR` (`Redaction.kt:238`), which is token-boundary containment over a word vocabulary plus a 17-entry vendor session-key list. **Plural key forms are still a gap:** `codes`, `tokens` and `keys` do not redact, because `SENSITIVE_KEY_EXPR` holds the singular vocabulary and its boundary rule requires a whole token, so `codes` cannot hit on `code`. The recipe is one character — an optional trailing `s` on the vocabulary alternation — and it was deferred because it adds a second widening axis plus six more tests and SC3 does not require it. The concrete risk accepted: a form field literally named `codes` carrying MFA backup codes would not be redacted. `codes` sits deliberately in the 21-key must-not-redact corpus, so adopting plurals later is a visible test change rather than a silent one.
- **Files:** `src/main/kotlin/com/six2dez/burp/aiagent/redact/Redaction.kt` — symbols `authHeaderRegex`, `jwtRegex`, `SENSITIVE_KEY_EXPR` and its three consumers `urlTokenParamRegex`, `formBodyParamRegex`, `jsonSecretKeyRegex`
- **Protocol for tightening:** Add new regex patterns to the `Redaction` companion object; add a corresponding test case in `src/test/kotlin/com/six2dez/burp/aiagent/redact/RedactionTest.kt`; update the v0.6.0 tightening comment at the top of that file. Do not loosen existing patterns without documenting the reason in the PR. This clause is binding and was honoured in Phase 21: the SC3 widening was verified strictly monotone across a 60-key corpus (31 must-redact, 21 must-not-redact, 8 camelCase), so no existing pattern was loosened.
- **Impact:** False negatives (data leakage) only; no false positive risk from adding patterns.

### Redaction body-stage bounds and the unbounded header stage

- **Issue:** Two deliberate residuals of Phase 21 / ADR-14. **First**, the body stage is now windowed, budgeted and fail-closed at every input size, but the eight header-stage rules — `authHeaderRegex`, `bearerRegex`, `basicAuthRegex`, `jwtRegex`, `urlTokenParamRegex`, `cookieHeaderRegex`, `setCookieHeaderRegex`, `hostHeaderRegex` — plus the two cookie rules added in Phase 21 (`redactCookieSections` and `cookieTypedParamRegex`) still run **unbounded on the full input**, with no per-pattern deadline and outside the total budget. Measured at 10 MB, each header rule costs roughly 25-51 ms, so a 200 MB input spends seconds in the header stage before the budgeted body stage even begins. This is a pre-existing condition rather than a Phase 21 regression, and decisions D-01 and D-02 scoped themselves explicitly to the body stage — which is exactly why ADR-14's title says "body stage" and does not make the unqualified claim. **Second**, a **user custom pattern whose match spans a window boundary can be missed**: there is no principled bound on a user regex's match length, so no window size and no overlap constant makes this safe (measured single matches of the built-in rules already reach 200 006 characters). `formBodyParamRegex` and `urlTokenParamRegex` are genuinely unaffected, because their value classes exclude `\s` and so their matches cannot span newlines. `jsonSecretKeyRegex` **can** span newlines — its `\s*` sits on both sides of the colon — and its boundary-safety mitigation in `windowEnd` was **found defective in the Phase 21 code review**: it pulled in exactly one following line and never re-checked, so a key/colon/value pair spread over three lines was reproduced **leaking** on the windowed path while the single-pass path redacted it. It now loops, re-checking each newly included line, with `isJsonPairBoundaryContinuation` treating a blank line as a continuation of risk and the initial test walking backward over blank lines so a cut landing inside a pair still triggers the extension — all under an eight-line cap, `MAX_JSON_BOUNDARY_LOOKAHEAD_LINES`, which stops a crafted run of colon- or quote-terminated lines from growing a window without bound. A pair spread over **more than eight lines therefore remains a false-negative window**; this entry records that residual rather than claiming closure.
- **Files:** `src/main/kotlin/com/six2dez/burp/aiagent/redact/Redaction.kt` — symbols `apply` (the header stage), `bodyStage`, `windowEnd`, `pairMayBeInFlightAt`, `isJsonPairBoundaryRisk`, `isJsonPairBoundaryContinuation` and `MAX_JSON_BOUNDARY_LOOKAHEAD_LINES`; `src/main/kotlin/com/six2dez/burp/aiagent/config/Defaults.kt`
- **Fix approach:** Extend the same budget composition over the header stage — apply each header rule through `SafeRegex.replaceAllSafeReporting` under `min(DEFAULT_TIMEOUT_MS, remaining budget)` and fail closed on timeout, exactly as the body stage does. The window-boundary limitation for custom patterns has no complete fix and should be documented for users rather than papered over. The built-in JSON case is bounded rather than solved: raising `MAX_JSON_BOUNDARY_LOOKAHEAD_LINES` narrows it further at a proportional cost in worst-case window growth, so it is a dial, not a fix. Whatever is changed here, keep `windowedScanRedactsJsonPairAcrossEveryBoundaryAlignment` green — it is the only assertion that the windowing invariant holds at all, and its absence is how the defect above shipped.
- **Impact:** Resource exhaustion on very large inputs, because the header stage is unbudgeted; a false-negative window for custom patterns at window boundaries; and a narrowed but real false-negative window for a built-in `jsonSecretKeyRegex` pair spread over more than eight lines. Neither of the first two is a new leak relative to the pre-Phase-21 code. The third was a **regression introduced by Phase 21's own windowing** and is now bounded, which is why it is named here separately rather than folded into the custom-pattern case.

### MCP unsafe-tool gate — new tools must opt in

- **Issue:** The gating mechanism works correctly for registered tools: `McpToolCatalog.kt:422` derives the set of unsafe-only tool IDs from descriptors, and `McpTool.kt:142` enforces the gate at call time. The fragility is the opt-in registration requirement. A developer adding a new tool that mutates Burp state must (a) add an `McpToolDescriptor` to `McpToolCatalog.kt` with `unsafeOnly = true` and (b) add the tool handler to `McpToolRegistrations`. If step (a) is missed, the tool executes without the unsafe check even though the runtime gate (`runTool`) tests `isUnsafeTool(name)` which checks `unsafeTools` — a set built from the catalog at context construction time in `McpRuntimeContextFactory`. The `McpToolParityTest.registeredToolIds_matchCatalog()` test (`McpToolParityTest.kt:17`) catches ID mismatches between catalog and registrations, but does not enforce that mutation tools are marked `unsafeOnly`.
- **Files:** `src/main/kotlin/com/six2dez/burp/aiagent/mcp/McpToolCatalog.kt`, `src/main/kotlin/com/six2dez/burp/aiagent/mcp/tools/McpTool.kt:142`, `src/test/kotlin/com/six2dez/burp/aiagent/mcp/tools/McpToolParityTest.kt`
- **Current unsafe tool list (verified):** `proxy_history_annotate`, `scope_include`, `scope_exclude`, `http1_request`, `http2_request`, `repeater_tab`, `repeater_tab_with_payload`, `intruder`, `intruder_prepare`, `comparer_send`, `task_engine_state`, `proxy_intercept`, `editor_set`, `project_options_set`, `user_options_set`, `scan_audit_start`, `scan_audit_start_mode`, `scan_audit_start_requests`, `scan_crawl_start`, `scan_task_delete`, `scan_report`.
- **Not marked unsafe (by design):** `issue_create`, `collaborator_generate`, `collaborator_poll` — these have side effects but are classified as safe because they do not send outbound HTTP or modify proxy/Burp state directly.
- **Fix approach:** Add a test that enumerates tools whose implementation calls `api.http().sendRequest(...)`, `api.intruder()`, `api.scanner()`, or similar mutation APIs, and asserts each is registered as `unsafeOnly = true` in the catalog. This makes the gate self-enforcing at the test layer.

### CLI backend Windows command quoting — only safe via `CliBackend`

- **Issue:** `CliBackend.kt:798-826` implements `normalizeWindowsCommand()` which handles `.exe` suffix stripping, npm `.cmd` shim resolution, and `cmd /c` fallback for Windows shell scripts. This logic lives exclusively in `CliBackend`. All current CLI factories (`ClaudeCliBackendFactory`, `GeminiCliBackendFactory`, `CopilotCliBackendFactory`, `CodexCliBackendFactory`, `OpenCodeCliBackendFactory`) delegate to `CliBackend(id, displayName)` and inherit this logic correctly.
- **Files:** `src/main/kotlin/com/six2dez/burp/aiagent/backends/cli/CliBackend.kt:798-826`, `src/main/kotlin/com/six2dez/burp/aiagent/backends/cli/ClaudeCliBackendFactory.kt`, `src/main/kotlin/com/six2dez/burp/aiagent/backends/cli/GeminiCliBackendFactory.kt`
- **Risk:** A new CLI backend that implements `AiBackend` directly (instead of delegating to `CliBackend`) will re-introduce Windows quoting bugs on npm-installed CLI tools. Process launch will silently fail or use the wrong executable.
- **Rule:** All CLI backends must be factories that construct a `CliBackend` instance. If a CLI backend needs custom behavior, extend or wrap `CliBackend`, do not reimplement `ProcessBuilder` invocation.

---

## Security Considerations

### Settings schema v3 does not migrate Perplexity fields — defaults are safe (verified)

- **Issue under investigation (#66):** The Unreleased CHANGELOG entry states that 5 new `perplexity*` fields and 1 `isFavorite` field were added to `AgentSettings` with "no `migrateIfNeeded` bump required." This was verified in code:
  - `AgentSettings.kt:57-61` — all five fields have Kotlin parameter defaults (`perplexityUrl = "https://api.perplexity.ai"`, `perplexityModel = ""`, etc.).
  - `AgentSettings.kt:257-266` — the `load()` path uses `prefs.getString(KEY_PERPLEXITY_URL) ?: defaultPerplexityUrl()` for each field, so an absent preference key falls back to the default gracefully.
  - `CURRENT_SETTINGS_SCHEMA_VERSION = 3` (`AgentSettings.kt:780`) — unchanged from the v0.6.0 bump.
  - **Conclusion:** The changelog claim is correct; no migration is needed. This is NOT a bug. Documenting here as resolved.

### API keys stored in Burp preferences (plaintext)

- **Issue:** `AgentSettings` fields (`openAiCompatibleApiKey`, `ollamaApiKey`, `nvidiaNimApiKey`, `perplexityApiKey`, `mcpSettings.token`) are stored via `prefs.setString(...)` in Burp's persistence layer, which is typically an unencrypted project file or user preferences file on disk.
- **Files:** `src/main/kotlin/com/six2dez/burp/aiagent/config/AgentSettings.kt:508-512`, `src/main/kotlin/com/six2dez/burp/aiagent/config/AgentSettings.kt:499-502`
- **Current mitigation:** Keys are not logged to Burp output; redaction is applied before sending to AI backends. The audit log does not capture raw keys.
- **Recommendations:** Document in the README that Burp project files should be treated as sensitive. Consider using the OS keychain via `java.security.KeyStore` for API keys in a future version. The MCP TLS keystore password path is already documented in `docs/mcp-hardening.md`.

### MCP bearer token in preferences

- **Issue:** `McpSettings.token` is stored as a plain string preference (`KEY_MCP_TOKEN` via `AgentSettings.kt:717`). If a project file is shared or exported, the MCP auth token is exposed.
- **Files:** `src/main/kotlin/com/six2dez/burp/aiagent/config/McpSettings.kt`, `src/main/kotlin/com/six2dez/burp/aiagent/config/AgentSettings.kt:717`
- **Current mitigation:** `mcp-hardening.md` documents rotation via regenerating the token. `KtorMcpServerManagerSecurityTest` covers origin validation and token enforcement.
- **Recommendations:** Warn users to use project-level (not user-level) persistence and never share project files containing an active MCP token.

---

## Performance Bottlenecks

### Passive scanner dedup cache — unbounded per-session growth

- **Issue:** The passive scanner maintains multiple in-memory dedup caches (endpoint dedup, response fingerprint dedup, prompt cache) sized by `passiveAiEndpointCacheEntries` (default 5,000), `passiveAiResponseFingerprintCacheEntries` (5,000), and `passiveAiPromptCacheEntries` (500). These are LRU-bounded. The persistent cache (`passiveAiPersistentCacheEnabled`, TTL 24h, max 50 MB) adds disk-backed dedup. On a long-running Burp session against a large target these caches can approach their bounds and trigger frequent evictions.
- **Files:** `src/main/kotlin/com/six2dez/burp/aiagent/scanner/PassiveAiScanner.kt:66-95`, `src/main/kotlin/com/six2dez/burp/aiagent/config/AgentSettings.kt:92-96`
- **Cause:** Configurable but the UI default of 5,000 endpoint entries may be undersized for large proxy histories.
- **Improvement path:** Allow the user to tune these values per-session; expose a "clear caches" button in the passive scanner panel.

### `MarkdownRenderer` swing-thread rendering

- **Issue:** `MarkdownRenderer.kt` renders markdown to HTML in the Swing EDT. For large AI responses (e.g., full pentest reports) this can cause brief UI freezes.
- **Files:** `src/main/kotlin/com/six2dez/burp/aiagent/ui/MarkdownRenderer.kt`
- **Improvement path:** Offload HTML generation to a background thread and push the result to `SwingUtilities.invokeLater`. A `MarkdownRendererPerformanceTest` exists in `src/test/kotlin/com/six2dez/burp/aiagent/ui/MarkdownRendererPerformanceTest.kt` that can be used to validate any fix.

---

## Test Coverage Gaps

### No test for CLI backend stdout charset handling

- **What's not tested:** Platform-charset mojibake from `InputStreamReader(process.inputStream)` at `CliBackend.kt:178` and `CliBackend.kt:580`. The HTTP transport equivalent is covered by `MontoyaHttpTransportUtf8Test`.
- **Files:** `src/main/kotlin/com/six2dez/burp/aiagent/backends/cli/CliBackend.kt:178,580`
- **Risk:** Silent data corruption of multibyte CLI output on non-UTF-8 Windows hosts.
- **Priority:** Medium — only affects Windows users with non-UTF-8 system locale.

### No release version-consistency test

- **What's not tested:** That the Gradle project version matches the Git tag version at release time.
- **Files:** `.github/workflows/release.yml`, `build.gradle.kts:14`
- **Risk:** Repeats the #62 regression on every future release.
- **Priority:** High — release-blocking category.

### No test enforcing that mutation MCP tools are marked `unsafeOnly`

- **What's not tested:** That any tool whose implementation calls `api.http().sendRequest(...)`, `api.scanner()`, `api.intruder()`, or similar Burp mutation APIs is registered with `unsafeOnly = true` in `McpToolCatalog`.
- **Files:** `src/main/kotlin/com/six2dez/burp/aiagent/mcp/McpToolCatalog.kt`, `src/test/kotlin/com/six2dez/burp/aiagent/mcp/tools/McpToolParityTest.kt`
- **Risk:** A new tool that mutates Burp state could be shipped without the unsafe gate.
- **Priority:** Medium — the parity test catches ID mismatches but not classification errors.

### UI layer has no integration tests

- **What's not tested:** `SettingsPanel`, `ChatPanel`, `MainTab` — the three largest files in the codebase — have no JUnit tests exercising their Swing interactions. Only `SettingsDefaultsPersistenceTest`, `ToolCallParserTest`, and `ChatPanelConcurrencyTest` exist.
- **Files:** `src/main/kotlin/com/six2dez/burp/aiagent/ui/SettingsPanel.kt`, `src/main/kotlin/com/six2dez/burp/aiagent/ui/ChatPanel.kt`, `src/main/kotlin/com/six2dez/burp/aiagent/ui/MainTab.kt`
- **Risk:** Settings save / invalidate / stale-cache regressions (the class of bug fixed in v0.6.0) can re-emerge undetected.
- **Priority:** Low to medium — Swing headless testing has high setup cost.

---

## Scaling Limits

### MCP concurrent request limiter

- **Current capacity:** `McpRequestLimiter` enforces `mcp.max.concurrent` (default from `McpSettings`; user-configurable). The default is intentionally conservative.
- **Limit:** Under high-throughput MCP client usage (e.g., a Claude Code agent issuing many parallel tool calls), the limiter queues or rejects excess calls with "Too many concurrent MCP requests." This is by design.
- **Scaling path:** Increase `mcp.max.concurrent` in Settings → MCP; the limiter is a semaphore and scales linearly.

---

## Dependencies at Risk

### `gradle/actions/setup-gradle@v6` Gradle build cache

- **Risk:** `gradle/actions/setup-gradle` enables Gradle's build cache and configuration cache by default. The release workflow disables the configuration cache for `cyclonedxBom` (`release.yml:39-41`) but not for the other tasks. If the Gradle cache on the Actions runner contains stale compiled outputs from a prior run on a different commit, incremental compilation could produce a JAR with mixed-source content.
- **Impact:** Rare, but could contribute to artifact contamination similar to #62.
- **Migration plan:** Add `--rerun-tasks` to the `shadowJar` step in `release.yml`, or add `cache-read-only: true` to `setup-gradle` in the release workflow to avoid cache writes that could poison future builds.

### `actions/checkout@v6`, `actions/setup-java@v5`, `softprops/action-gh-release@v3`, `actions/upload-artifact@v7`

- **Risk:** These actions are referenced by major version (`@v6`, `@v5`, etc.) without SHA pinning. A compromised or accidentally breaking update to the underlying action could affect builds silently.
- **Impact:** Supply chain risk. Not currently blocking.
- **Migration plan:** Pin actions to their commit SHAs in all three workflow files. Dependabot already monitors `github-actions` weekly (`.github/dependabot.yml`) — enable SHA pinning mode there.

---

*Concerns audit: 2026-05-13*
