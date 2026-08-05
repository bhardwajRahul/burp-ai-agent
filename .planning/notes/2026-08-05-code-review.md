# Deep code review — v0.9.2, 2026-08-05

Source of record for the v0.10.0 milestone. Every finding below maps to a requirement in
`.planning/REQUIREMENTS.md` and a phase in `.planning/ROADMAP.md`.

**Build state at review time:** `./gradlew test detekt ktlintCheck` — BUILD SUCCESSFUL. Nothing here
is a broken build; all of it is latent.

**Measured baselines:** ~38.9k lines Kotlin main across 253 files, 94 test files. Coverage 34% line /
23% branch (`build/reports/jacoco`). Detekt baseline 1096 entries (651 MagicNumber, 133 ReturnCount,
57 CyclomaticComplexMethod, 42 LongMethod, 40 TooGenericExceptionCaught).

---

## Verified by execution

Two findings were reproduced by running the code, not by reading it. Both have recorded evidence.

### F1 — MCP access-control checks never run on resolved routes → SEC-04, Phase 20

`mcp/KtorMcpServerManager.kt:154-225`. Registration order is `routing{}` (154) →
`intercept(ApplicationCallPipeline.Call)` (176) → `mcp{}` (220).

Ktor installs the `RoutingRoot` Call-phase interceptor at the moment of the *first* `routing{}`,
i.e. before the security block. Same-phase interceptors run in registration order, and
`RoutingRoot.interceptor` does not call `finish()` — verified by decompiling
`ktor-server-core-jvm-3.1.3`. So any route Ktor resolves is executed and answered before the checks.

`Application.mcp` was also decompiled (`kotlin-sdk-jvm-0.5.0`): it calls `install(SSE)` then
`routing{}` on the same already-installed `RoutingRoot`, so its routes inherit the same position.

Observed against a live server:

```
# local mode (externalEnabled=false)
GET  /__mcp/health  Origin: http://evil.example  -> 200 "ok"   X-Frame-Options: absent
POST /message       Origin: http://evil.example  -> 400 "sessionId query parameter is not provided"
GET  /nope          Origin: http://evil.example  -> 404        X-Frame-Options: DENY

# external mode (externalEnabled=true, TLS on, token set), NO Authorization header
GET  /__mcp/health   -> 200 "ok"
POST /message        -> 400 "sessionId query parameter is not provided"   <-- expected 401
GET  /unmatched      -> 401
```

Impact: in external mode the bearer gate does not protect the MCP endpoints; the SSE root that issues
`sessionId` is equally unprotected. In local mode the anti-DNS-rebinding / anti-browser guards and all
four security headers are dead code on the endpoints that matter.

`McpServerIntegrationTest` passes while this is live — it only exercises `/__mcp/shutdown`, which
validates the token *inside* its own handler. Phase 20 SC4 exists because of this: the new tests must
fail pre-fix.

### F2 — Cookie values reach the AI backend in STRICT and BALANCED → PRIV-05, Phase 21

`scanner/PassiveAiScannerAnalysis.kt:247-252` extracts cookies into a dedicated `=== COOKIES ===`
prompt section as bare `name=value`, dropping the `Cookie:` prefix. `Redaction.apply` then cannot
match them: `cookieHeaderRegex` is `(?im)^cookie:\s*.+$`, and `formBodyParamRegex` requires the key
to be an exact member of `SENSITIVE_KEYS` (`redact/Redaction.kt:88-89`).

Verified against the live regexes:

```
JSESSIONID=8F3A9C2B7E1D4A6F0B5C8E2D   -> NOT REDACTED
PHPSESSID=abc123def456                -> NOT REDACTED
connect.sid=s%3ARZxYqL9.opaquevalue   -> NOT REDACTED
auth_token=secretvalue123             -> NOT REDACTED   ("auth" is followed by "_", not "=")
csrftoken=abcdef                      -> NOT REDACTED
remember_me=deadbeefcafe              -> NOT REDACTED
session=plainsessionvalue             -> session=[REDACTED]   (only exact match works)
```

`Authorization` / `X-API-Key` / `X-Auth-Token` headers *are* covered by `authHeaderRegex`, and
`sanitizeHeadersForPrompt` emits `Cookie: <value>` lines that *are* stripped. The hole is specific to
the dedicated cookie section — and to `request.parameters()`, which surfaces `COOKIE`-type params
into `=== PARAMETERS ===` by the same mechanism (Phase 21 SC2).

---

## Found by reading

### F3 — Model-emitted tool calls execute with no user gate → SEC-06, Phase 22

`ui/ChatPanel.kt:2103-2164`, `MAX_AUTO_TOOL_ITERATIONS = 8` (line 1180). `maybeExecuteToolCall`
parses JSON out of model output and calls `McpToolExecutor.executeTool` directly, chaining up to 8
times. The only controls are per-tool toggles and the `unsafe` flag.

Model context contains attacker-controlled data: proxy traffic sent via "Send to AI", passive-scan
findings, and external MCP tool results. The `[EXTERNAL-TOOL-RESULT:...]` boundary marker plus its
advisory note (`McpToolExecutorImpl.kt:127-133`) is prompt-level mitigation, not a control.

### F4 — MCP tool execution on the EDT → REL-05, Phase 23

`ui/ChatPanel.kt:2121` calls `executeTool` synchronously right after `assertEdt()`. Inside:
`api.http().sendRequest(...)` (`McpToolExecutorImpl.kt:190`) and
`runBlocking { manager.callTool(...) }` for external MCP servers (`:1092`). With 8 chained
iterations the UI is frozen throughout. Same class of problem as commit 73d78a6, different path.

### F5 — Redaction fails open above 1 MB → PRIV-06, Phase 21

`redact/Redaction.kt:257`. Above `MAX_REDACTION_BODY_CHARS` all body-level rules — form, JSON, and
the user's custom patterns — are skipped silently and the original passes through.

Related: custom patterns only apply when `redactTokens` is true, so they are inactive under
`PrivacyMode.OFF`. Arguably wrong for a "always strip this" list; needs a decision either way.

### F6 — Recurring schedulers die permanently on one exception → REL-06, Phase 24

`scanner/ActiveAiScanner.kt:340-342` — `scheduleWithFixedDelay({ processQueue() }, ...)` with no
try/catch. `scheduleWithFixedDelay` cancels the task forever on an uncaught throw. One failure stops
active scanning for the rest of the session with no message.

`AgentSupervisor.kt:79-88` documents and guards exactly this; the OAST poller right below at
`:347-351` guards it too. Also unguarded: `ScannerTaskRegistry.kt:26`, `CollaboratorRegistry.kt:25`
(there the effect is a memory leak rather than a stall).

### F7 — MCP token sent to an unverified port holder → SEC-07, Phase 25

`mcp/McpSupervisor.kt:274-292`. On `BindException` the takeover path probes for
`X-Burp-AI-Agent: mcp` — trivially spoofable — then POSTs `Authorization: Bearer <token>` to whatever
answered. `openConnection` (`:294-329`) disables certificate validation for loopback, so TLS does not
help. A local process that squats the port receives the token.

### F8 — Settings save can block the EDT for 10 s → REL-05, Phase 23

`ui/SettingsPanelSettingsIO.kt:456-505` runs on the EDT and calls `mcpSupervisor.applySettings`
(465) → `stop()` → `future.get(10, TimeUnit.SECONDS)` (`KtorMcpServerManager.kt:257`). Same method
also does `settingsRepo.save()` and `backends.reload()` on the EDT.

### F9 — CLI output capture: data race and unbounded growth → REL-07, Phase 24

`backends/cli/CliBackend.kt:209-262`. `rawOutput` is a plain `StringBuilder` written by the reader
thread and read by the executor thread. On the timeout path `readerThread.join(2000)` can expire and
`rawOutput.toString()` still runs while the reader appends. No cap either — a verbose CLI accumulates
everything though only `take(2000)` is used.

### F10 — `shellEscape` misses metacharacters without whitespace → QUAL-06, Phase 26

`backends/cli/CliBackend.kt:823-827`: `if (arg.none { it.isWhitespace() || it == '"' || it == '\'' }) return arg`.
`foo;id` and `$(cmd)` pass unquoted into the PTY-mode `sh -c`. The command is user-configurable so
this is not direct escalation — but it matters on the settings-import path, where a malicious
settings JSON becomes command execution.

### F11–F19 — smaller items

| # | Location | Issue | Req |
|---|----------|-------|-----|
| F11 | `KtorMcpServerManager.kt:97` | `Implementation("burp-ai-agent", "0.6.0")` hardcoded; project is at 0.9.2 | SEC-05 |
| F12 | `KtorMcpServerManager.kt:333-347` | `isValidHost` splits on `:` — breaks on IPv6, though `isLoopbackHost` accepts `::1` | SEC-05 |
| F13 | `CliBackend.kt:123,138` | `deleteOnExit()` adds a never-removed shutdown-hook entry per invocation | REL-07 |
| F14 | `App.kt:37`, `ActiveAiScanner.kt:73` | `newCachedThreadPool()` — unbounded thread creation under scan load | REL-07 |
| F15 | `util/SsrfGuard.kt:24` | Only dotted-quad IPv4 detected; `http://2130706433/`, `0177.0.0.1` slip past | SEC-07 |
| F16 | `ui/ChatPanel.kt:705` | `assert()` is a no-op in production Burp — reads as a runtime guarantee, is not one | QUAL-07 |
| F17 | `config/SecretCipher.kt` | Master key stored in plaintext Base64 beside the ciphertext in Preferences — obfuscation, documented as a trade-off; docs should say so plainly | QUAL-07 |
| F18 | — | Coverage 34% line / 23% branch; detekt baseline 1096 entries | QUAL-06, QUAL-07 |
| F19 | `AgentSettings.kt:1259-1263` | Blank MCP token auto-regenerates on load and the UI warns, but a saved blank token would make `"Bearer "` authenticate | SEC-05 |

---

## Explicitly not findings

Checked and found sound — recorded so a future review does not re-open them:

- `runTool` gating (`mcp/tools/McpTool.kt:135-158`) — fails closed on disabled and unsafe-not-allowed
  before the limiter, with telemetry on each block.
- `AgentSettings` persistence validation — `coerceIn` on every numeric setting, both load and save.
- `SecretCipher` AES-256-GCM envelope, version byte check, fail-soft decrypt, double-checked master
  key bootstrap.
- HKDF host anonymization — RFC 5869 correct, with a test vector.
- `McpTls.resolve` fails closed when no keystore is configured, so external mode cannot start
  without TLS material.
- `AgentSupervisor` health-monitor exception guard and the OAST poller guard.
- Structural: file sizes (`ChatPanel.kt` 2237 lines and four more over 1200) are a maintainability
  concern, deliberately kept out of v0.10.0 so the security diffs stay legible. In the backlog.
