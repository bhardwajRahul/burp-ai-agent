---
status: partial
phase: 27-priv-05-gap-closure-sanitize-headers
source: [27-VERIFICATION.md]
started: 2026-08-24
updated: 2026-08-24
---

## Current Test

[awaiting human testing]

## Tests

### 1. Live-Burp reproduction of the raw-serialization cookie leak (STRICT, then BALANCED)
expected: Load the fat JAR in a live Burp and set Privacy to **STRICT**. Proxy at least one request
carrying `Cookie: wibble=SENTINEL_ABC` and a response carrying
`Set-Cookie: wobble=SENTINEL_SET; Path=/`. From a real MCP client, call **`proxy_http_history`**
(then repeat with `site_map` and `scanner_issues`). Inspect the tool result as the client receives it.

**The finding is CONFIRMED if `SENTINEL_ABC` and `SENTINEL_SET` appear in the tool result.** Repeat
in **BALANCED** — the leak is expected in both modes. As a control, confirm the same cookie value IS
redacted on the prompt path (trigger a passive AI scan and inspect the outbound prompt), which
demonstrates the two paths disagree.

Also note whether `Host:` survives un-anonymised under STRICT in the same tool result — same root
cause, tracked separately from PRIV-05.

why_human: The chain was verified statically at source and measured against the shipped compiled
`Redaction` class on JDK 21 — `Serialization.kt:44` embeds `request()?.toString()` raw;
`toolJson.encodeToString` escapes CRLF to a literal `\r\n`; `redactIfNeeded`
(`McpToolExecutorImpl.kt:1037`) applies but its cookie rules are line-anchored `(?im)^…$` and cannot
match a single-line JSON payload. Confirmed independently: the same regex yields 1 match on
multi-line input and 0 on the JSON-encoded form. What static analysis cannot supply is an end-to-end
run through a real Burp session with a real MCP client and a real proxy history — which is how the
original PRIV-05 was proven (live probe against `Custom-AI-Agent-full-1.0.0.jar`), and this finding
deserves the same standard before it is written into a security register as fact.
result: [pending]

### 2. Re-test after the gap-closure fix lands
expected: Repeat test 1 against a build containing the gap-closure fix. **Neither sentinel appears in
any tool result in STRICT or BALANCED**, on `proxy_http_history`, `proxy_http_history_regex`,
`site_map`, `site_map_regex` or `scanner_issues`. Cookie NAMES may remain; values must not.
why_human: Same reason as test 1 — the fix must be proven on the real emission path, not only by
unit tests over a synthetic payload shape.
result: [pending]

## Notes

- Affected call sites (10, all via `toSerializableForm()` / `toSiteMapEntry()`):
  `McpToolExecutorImpl.kt:608,836,855,873,896` and `McpToolLegacy.kt:475,713,729,744,764`.
- `cookie_jar_get` is correctly gated and is NOT part of this finding.
- This leak is the **canonical** `Cookie:` / `Set-Cookie:` header, not a name variant — strictly
  broader than the original PRIV-05 defect, which only affected `X-Cookie`-style variants.
- `AR-27-01` was recorded in Phase 27 as an accepted residual. This finding reclassifies it: it is
  safe only where `sanitizeHeaders` runs in front of it, and on these 10 sites nothing does.
  `McpToolHelpersTest.kt:209-216` is the phase's own green test asserting exactly that gap.
