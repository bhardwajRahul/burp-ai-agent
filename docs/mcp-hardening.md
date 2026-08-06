# MCP Hardening Runbook

Use this checklist before exposing MCP beyond loopback.

## Baseline

1. Keep bind host at `127.0.0.1` unless external access is required.
2. Keep **Unsafe Tools** disabled by default.
3. Enable only the minimum MCP tools needed for current workflow.

## External Access

1. Enable TLS.
2. Use strong bearer token and rotate it.
3. Restrict allowed origins/hosts to trusted clients.
4. Validate `Authorization: Bearer <token>` is required on every request except `GET /__mcp/health`. That one route is deliberately exempt so the extension's own bind-conflict liveness probe can tell whether something is already holding the MCP port without putting the bearer token on the wire against a listener whose identity has not been established.
5. Do not rely on the identifying response header `X-Burp-AI-Agent: mcp` for health checks outside loopback: it is emitted only while external access is disabled, so an unauthenticated scan of an externally exposed port cannot confirm which extension is listening.
6. Use an MCP client that sends ordinary HTTP request headers, such as Claude Desktop or Codex CLI, because a browser-based client is unsupported in external mode: `EventSource` cannot set request headers and therefore cannot present the bearer token on the initial `GET /sse`.
7. Restrict allowed origins explicitly if your threat model cares about origin disclosure, because CORS preflight (`OPTIONS`) is answered before authentication and, when no allowed origins are configured, the server permits any origin, so an unauthenticated peer can learn which origins are allow-listed. This behaviour is deliberately unchanged.

## Operational Controls

1. Set conservative MCP request concurrency.
2. Set `Max Body Bytes` to avoid oversized payload exfiltration.
3. Keep privacy mode at `STRICT` or `BALANCED` for cloud clients.
4. Keep audit logging enabled for traceability.

## Credential Storage

1. The TLS keystore password is persisted in Burp's preferences (`mcp.tls.keystore.password`) as plaintext. Burp preferences are stored in the user's project file and are only as protected as that file.
2. If the project file or preferences export could leak (shared backups, multi-user hosts), treat the MCP bearer token and TLS keystore as compromised and rotate both.
3. For high-assurance setups, generate the keystore offline with your own `keytool` invocation and point the extension at it via settings, so the password never touches Burp preferences.
4. The MCP bearer token is generated with `SecureRandom` (32 bytes base64). Rotate it if any external client is decommissioned.

## Verification

1. Test local health endpoint: `GET /__mcp/health`.
2. Test denied requests return the expected bare status: in external mode `POST /message` and `GET /sse` without `Authorization` return `401` with an empty body while `GET /__mcp/health` returns `200`, and in local mode a request carrying a foreign `Origin`, a foreign `Host`, a foreign `Referer`, or a browser `User-Agent` with no `Origin` returns `403` with an empty body. The responses are bare statuses with no diagnostic body by design; the reason is recorded in Burp's Output tab and, when audit logging is enabled, in the audit log.
3. Confirm unsafe tools are blocked when master switch is off.
4. Confirm responses from routes the server resolves — not only its `404` responses — carry `X-Frame-Options: DENY`, `X-Content-Type-Options: nosniff`, `Referrer-Policy: same-origin` and `Content-Security-Policy: default-src 'none'`.

## Incident Response

1. Disable MCP toggle immediately.
2. Rotate token.
3. Review audit logs and extension output.
4. Re-enable with reduced scope.
