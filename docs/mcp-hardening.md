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
4. Validate `Authorization: Bearer <token>` is sent on every request.

## Operational Controls

1. Set conservative MCP request concurrency.
2. Set `Max Body Bytes` to avoid oversized payload exfiltration.
3. Keep privacy mode at `STRICT` or `BALANCED` for cloud clients.
4. Keep audit logging enabled for traceability.

## Verification

1. Test local health endpoint: `GET /__mcp/health`.
2. Test denied request (missing/invalid token) returns auth error.
3. Confirm unsafe tools are blocked when master switch is off.

## Incident Response

1. Disable MCP toggle immediately.
2. Rotate token.
3. Review audit logs and extension output.
4. Re-enable with reduced scope.
