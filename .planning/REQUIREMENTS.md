# Requirements: Burp AI Agent — v0.10.0 (Security Correctness & Agent Trust)

**Defined:** 2026-08-05
**Core Value:** Bring modern AI to a real security workflow **without** leaking sensitive traffic to third-party providers — privacy controls and an audit trail are non-negotiable.

Scope = the 17 findings of the 2026-08-05 deep code review of v0.9.2. Two of them (SEC-04, PRIV-05) are **defects verified by running the shipped code**, not theoretical concerns — they are the reason this milestone exists. Phase numbering continues from the previous milestone (Phase 20+).

**Framing:** v0.9.0 built the privacy and security *machinery*. This milestone makes sure that machinery is actually **in the request path** — several controls were found to be present in source but never executed, or executed against patterns that do not match real-world data.

**Ordering constraint:** SEC-04 and PRIV-05 are live defects in a published release and lead the milestone. SEC-06 (agent trust boundary) and REL-05 (EDT) both rewrite `ChatPanel.maybeExecuteToolCall` and must be sequential, not parallel. QUAL-06 lands last so it can cover the code the earlier phases produce.

## v0.10.0 Requirements

### Access Control & Server Security (SEC)

- [x] **SEC-04** (Finding 1, **critical**): Every MCP request is subject to the extension's access-control checks before any handler runs. In external mode an unauthenticated `POST /message` and an unauthenticated SSE connect are both rejected with 401; in local mode a request bearing a foreign `Origin`, a foreign `Host`, or a browser `User-Agent` without `Origin` is rejected with 403; `X-Frame-Options`, `X-Content-Type-Options`, `Referrer-Policy` and `Content-Security-Policy` are present on responses from matched routes. Covered by regression tests that assert the status code on `/message`, on the SSE root, and on `/__mcp/health` — the current `McpServerIntegrationTest` passes while the bypass is live, so the new tests must fail against today's `KtorMcpServerManager`.
- [x] **SEC-05** (Findings 11, 12, 19): The MCP server reports the real build version instead of a hardcoded `0.6.0`; `isValidHost` handles bracketed IPv6 authorities consistently with `isLoopbackHost` accepting `::1`; a blank bearer token cannot authenticate an external-mode request.
- [ ] **SEC-06** (Finding 3): A tool call that the extension parsed out of **model output** does not execute against Burp without an explicit user decision. The user can approve per call, approve-for-session per tool, or deny; the decision is audit-logged. The threat model that motivates this (attacker-controlled HTTP traffic reaching model context, then steering tool selection) is recorded as an ADR so future tools inherit the rule rather than re-litigating it.
- [ ] **SEC-07** (Findings 7, 15): The MCP bearer token is never sent to a listener whose identity has not been established — the bind-conflict takeover path stops presenting `Authorization: Bearer <token>` to an unverified port holder. `SsrfGuard` additionally classifies IPv4 literals written in decimal, octal and hexadecimal form, so its advisory warning cannot be sidestepped by notation alone.

### Privacy & Redaction (PRIV) — core value

- [x] **PRIV-05** (Finding 2, **high**): Cookie values do not reach an AI backend in STRICT or BALANCED mode by any path. Specifically, the passive scanner's `=== COOKIES ===` section — which today re-emits cookies as bare `name=value`, stripped of the `Cookie:` prefix that `cookieHeaderRegex` keys on — is redacted. Sensitive-key matching recognises real-world names (`JSESSIONID`, `PHPSESSID`, `connect.sid`, `auth_token`, `csrftoken`, `remember_me`) rather than only exact matches against the `SENSITIVE_KEYS` alternation. Covered by a test that asserts each of those names is redacted in both modes.
- [x] **PRIV-06** (Finding 5): Redaction never fails open. A payload above `MAX_REDACTION_BODY_CHARS` is truncated-and-redacted or refused — it is not passed through with body-level rules silently skipped. The behaviour when a user's custom patterns meet `PrivacyMode.OFF` is an explicit, documented decision rather than an emergent consequence of the `redactTokens` branch.

### Reliability & Concurrency (REL)

- [ ] **REL-05** (Findings 4, 8): No MCP tool execution, backend HTTP call, or `runBlocking` on an external MCP server happens on the Swing EDT; the auto tool-chain (up to 8 iterations) leaves the UI responsive throughout. Saving Settings does not block the EDT on `serverManager.stop()`'s 10-second bounded wait.
- [ ] **REL-06** (Finding 6): Every recurring scheduled task survives an exception in its body — `ActiveAiScanner.processQueue`, `ScannerTaskRegistry.cleanupExpired` and `CollaboratorRegistry.cleanupExpired` each keep running after a throw, matching the guard already present on `AgentSupervisor.checkHealth` and the OAST poller. Covered by a test that injects a throw and asserts the next tick still fires.
- [ ] **REL-07** (Findings 9, 13, 14): CLI output capture is thread-safe and bounded — no unsynchronised `StringBuilder` shared across the reader thread and the timeout path, and no unbounded accumulation of a chatty CLI's output. `deleteOnExit()` no longer accumulates one shutdown-hook entry per CLI invocation. Unbounded `newCachedThreadPool()` use is replaced with bounded pools so an active scan cannot spawn threads without limit.

### Quality & Maintainability (QUAL)

- [ ] **QUAL-06** (Finding 10 + coverage): `shellEscape` quotes by allowlist, so an argument containing shell metacharacters without whitespace (`foo;id`, `$(cmd)`) cannot reach `sh -c` unquoted — closing the settings-import-to-command-execution path. Line coverage on the security-relevant packages (`redact`, `mcp`, `config`) rises meaningfully from the 34% line / 23% branch project baseline, with the new tests concentrated on the paths this milestone changes.
- [ ] **QUAL-07** (Findings 16, 17): The detekt baseline shrinks rather than grows — no finding from this milestone is added to it. `assert()`-based EDT enforcement, which is a no-op in production Burp, is either upgraded to something that reports in the field or explicitly documented as test-only. `SecretCipher`'s at-rest guarantee is described accurately in user-facing docs (the master key sits beside the ciphertext in Burp Preferences).

### Docs (DOC)

- [ ] **DOC-03**: A security advisory documents SEC-04 and PRIV-05 for users running v0.9.0–v0.9.2, stating impact and the version that fixes them; `README.md`, `SPEC.md`, `DECISIONS.md` and the GitBook site (`burp-ai-agent-docs`) reflect the new tool-call confirmation flow and the corrected privacy claims.

## Traceability

| Req | Finding(s) | Severity | Phase |
|-----|-----------|----------|-------|
| SEC-04 | 1 | Critical | 20 |
| SEC-05 | 11, 12, 19 | Low | 20 |
| PRIV-05 | 2 | High | 21 |
| PRIV-06 | 5 | Medium | 21 |
| SEC-06 | 3 | High | 22 |
| REL-05 | 4, 8 | High / Medium | 23 |
| REL-06 | 6 | Medium | 24 |
| REL-07 | 9, 13, 14 | Medium / Low | 24 |
| SEC-07 | 7, 15 | Medium / Low | 25 |
| QUAL-06 | 10 + coverage | Medium | 26 |
| QUAL-07 | 16, 17 | Low | 26 |
| DOC-03 | — | — | 26 |

## Out of Scope (v0.10.0)

- Anthropic native tool-use and prompt-caching (deferred from CAP-01 in v0.9.0) — feature work, not correctness.
- Mega-file refactor of `ChatPanel.kt` (2237 lines), `ActiveAiScanner.kt` (1668), `AgentSettings.kt` (1438), `McpToolExecutorImpl.kt` (1291), `AgentSupervisor.kt` (1277). Phases 22 and 23 touch `ChatPanel` heavily; a structural split on top of that would obscure the security diffs. Carried to the backlog.
- BApp Store submission #231 — currently stalled; not treated as an ordering constraint for this milestone.
