# Security Policy

## Supported Versions

Only the latest minor release receives security updates. `0.10.0` is the current development line and
is not yet published; until it ships, security fixes are also backported to the released `0.9.x` line.

| Version | Supported                                                  |
| ------- | ---------------------------------------------------------- |
| 0.10.x  | Yes                                                        |
| 0.9.x   | Security fixes only, until 0.10.0 ships                    |
| < 0.9   | No                                                         |

## Reporting a Vulnerability

**Do not open a public GitHub issue for security vulnerabilities.**

Report privately via [GitHub Security Advisories](https://github.com/six2dez/burp-ai-agent/security/advisories/new).

### What to include

- Affected version(s).
- Reproduction steps or proof-of-concept.
- Potential impact (e.g. credential exposure, remote code execution, data exfiltration to third-party LLMs).
- Any suggested mitigations.

### Response timeline

- Acknowledgment within 5 business days.
- Initial triage within 10 business days.
- Fix or disclosure decision within 30 days for high/critical, 90 days for medium/low.

### Scope

In scope:

- The `burp-ai-agent` extension code.
- MCP server and tool dispatcher.
- Redaction pipeline.
- Backend adapters (HTTP and CLI).
- Audit logging and persistent prompt cache.

Out of scope:

- Vulnerabilities in Burp Suite itself (report to [PortSwigger](https://portswigger.net/security)).
- Vulnerabilities in third-party AI providers (Anthropic, OpenAI, Google, NVIDIA, GitHub, etc.).
- Vulnerabilities in local model runners (Ollama, LM Studio).
- Issues requiring physical access to the user's machine.

## Security Advisories

Two defects below were confirmed by **running** the shipped code during a review of `0.9.2` on
2026-08-05, not by reading it. Both affect every published `0.9.x` release.

**No CVE and no GHSA identifier has been issued for either finding.** Do not look for one — none
exists at the time of writing. Both are fixed in `0.10.0`, which is **not yet published**; the fixes
are in the development line and will be available when that release ships. If you find a further
issue, report it privately using the instructions in
[Reporting a Vulnerability](#reporting-a-vulnerability) above rather than opening a public issue.

### SEC-04 — MCP access-control checks did not run on resolved routes

**Affected:** 0.9.0, 0.9.1, 0.9.2 · **Fixed in:** 0.10.0 (unreleased) · **Severity:** critical

The access-control interceptor was registered *after* the `routing` block in Ktor's `Call` phase, and
Ktor runs same-phase interceptors in registration order, so any request whose route resolved was
served by its handler before the checks ran.

What that exposed:

- With external MCP access enabled, an unauthenticated `POST /message` and an unauthenticated SSE
  connect both reached the MCP handler instead of being rejected with `401`. The listener accepted
  unauthenticated tool calls.
- In local mode, the `Origin`, `Host` and `User-Agent` checks and the `X-Frame-Options`,
  `X-Content-Type-Options`, `Referrer-Policy` and `Content-Security-Policy` response headers did not
  apply to matched routes, leaving the browser-origin defences inert.

Reproduction actually observed: in external mode, with no `Authorization` header, `POST /message`
returned `400 "sessionId query parameter is not provided"` — the MCP handler's own error, proving the
handler ran — rather than `401`. Only unmatched paths returned `401`.

Precondition, stated honestly: the MCP server binds to `127.0.0.1` by default, so the
unauthenticated-listener exposure required the explicit external-access opt-in. The local-mode gap
required no opt-in; it applied to every install running the MCP server.

**User action:** if you enabled external MCP access on 0.9.0, 0.9.1 or 0.9.2, treat that listener as
having accepted **unauthenticated** tool calls for the entire period it was reachable beyond
loopback, and rotate the MCP bearer token. Review Burp's own logs and your audit log for tool calls
you did not initiate.

### PRIV-05 — session cookies reached AI backends unredacted in STRICT and BALANCED

**Affected:** 0.9.0, 0.9.1, 0.9.2 · **Fixed in:** 0.10.0 (unreleased) · **Severity:** high

The passive scanner emitted a dedicated cookies section into the prompt as bare `name=value` pairs,
dropping the `Cookie:` header prefix that the redaction rule keyed on. Sensitive-key matching was an
exact match against a fixed list, so real-world cookie names were not recognised.

What that exposed: cookie values named `JSESSIONID`, `PHPSESSID`, `connect.sid`, `auth_token`,
`csrftoken` and `remember_me` were included verbatim in the prompt sent to the configured AI backend
in **both STRICT and BALANCED** privacy modes. Only a cookie literally named `session` was caught.

**User action:** if you ran passive AI scanning on 0.9.0, 0.9.1 or 0.9.2 with a non-local backend
(any hosted provider — Anthropic, OpenAI, Google, NVIDIA, Perplexity, GitHub, or any
OpenAI-compatible endpoint you configured), treat every session cookie that passed through that
scanning as **disclosed to that provider** and rotate it. Cookies belonging to your clients' or
employer's applications are included; rotation is their decision to make, so tell them. If your
backend was a local runner (Ollama, LM Studio) the data did not leave your machine.

## Security Model

The extension runs inside Burp Suite on the user's machine. The threat model assumes:

- Burp Suite preferences are accessible only to the local user.
- API keys and credentials are stored via Burp's standard preferences storage, encrypted with
  AES-256-GCM under a per-install random master key (`SecretCipher`). **That master key is itself
  stored in Burp Preferences, Base64-encoded, beside the ciphertext it protects**
  (`secret.master.key.v1`). The encryption therefore protects against casual inspection of a
  preferences file or an exported project — it does **not** protect against an attacker or a process
  that can read those preferences, because such a reader has the key too. Treat preference-file
  access as equivalent to credential access.
- The MCP server binds to `127.0.0.1` by default; external access requires explicit opt-in with a bearer token and optional TLS.
- Privacy modes (STRICT / BALANCED / OFF) control what request and response data is sent to AI backends.

See [`docs/mcp-hardening.md`](docs/mcp-hardening.md) for operational hardening guidance and [`docs/ui-safety-guide.md`](docs/ui-safety-guide.md) for safe-use recommendations.
