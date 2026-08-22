---
phase: 25-secondary-hardening
reviewed: 2026-08-22T00:00:00Z
depth: standard
files_reviewed: 21
files_reviewed_list:
  - src/main/kotlin/com/six2dez/burp/aiagent/mcp/McpTakeoverProof.kt
  - src/main/kotlin/com/six2dez/burp/aiagent/mcp/McpSupervisor.kt
  - src/main/kotlin/com/six2dez/burp/aiagent/mcp/KtorMcpServerManager.kt
  - src/main/kotlin/com/six2dez/burp/aiagent/mcp/McpAccessControlDecision.kt
  - src/main/kotlin/com/six2dez/burp/aiagent/mcp/McpAccessControlPlugin.kt
  - src/main/kotlin/com/six2dez/burp/aiagent/mcp/McpTls.kt
  - src/main/kotlin/com/six2dez/burp/aiagent/util/Ipv4Literal.kt
  - src/main/kotlin/com/six2dez/burp/aiagent/util/SsrfGuard.kt
  - src/test/kotlin/com/six2dez/burp/aiagent/mcp/McpTakeoverProofTest.kt
  - src/test/kotlin/com/six2dez/burp/aiagent/mcp/McpTakeoverPipelineTest.kt
  - src/test/kotlin/com/six2dez/burp/aiagent/mcp/McpTakeoverSquatterTest.kt
  - src/test/kotlin/com/six2dez/burp/aiagent/mcp/McpTakeoverCertificatePinTest.kt
  - src/test/kotlin/com/six2dez/burp/aiagent/mcp/McpSupervisorConnectionTest.kt
  - src/test/kotlin/com/six2dez/burp/aiagent/DecisionsAdrTest.kt
  - src/test/kotlin/com/six2dez/burp/aiagent/util/Ipv4LiteralTest.kt
  - src/test/kotlin/com/six2dez/burp/aiagent/util/SsrfGuardTest.kt
  - src/test/kotlin/com/six2dez/burp/aiagent/util/SsrfGuardNoResolutionTest.kt
  - src/test/kotlin/com/six2dez/burp/aiagent/util/CountingInetAddressResolverProvider.kt
  - src/test/resources/META-INF/services/java.net.spi.InetAddressResolverProvider
  - DECISIONS.md
  - docs/mcp-hardening.md
findings:
  critical: 0
  warning: 3
  info: 7
  total: 10
status: issues_found
---

# Phase 25: Code Review Report

**Reviewed:** 2026-08-22
**Depth:** standard (per-file, with targeted empirical verification)
**Files Reviewed:** 21
**Status:** issues_found (0 Critical, 3 Warning, 7 Info)

## Summary

I attacked each of the three controls rather than reading them for plausibility, and ran four
independent experiments against JDK 21 and against the compiled classes in `build/classes/kotlin/main`
to settle the questions the prompt raised. **No Critical finding survived scrutiny.** The three
hardening controls hold in the configurations they claim to cover.

What I verified positively, so the negatives below are calibrated against it:

- **`Ipv4Literal` is correct.** 500 000 randomised round-trips through the compiled `Ipv4Literal.parse`
  (single-part, dotted-quad and two-part forms) produced zero mismatches. `maxForTrailing` is right for
  all four arities (`10.16777215` → `10.255.255.255`, `10.16777216` → null, `192.168.65535` →
  `192.168.255.255`, `192.168.65536` → null). Every intermediate is a `Long`, `toLongOrNull` returns
  null on overflow rather than wrapping, and the radix-alphabet check runs before parsing, so signs,
  non-ASCII digits and `0x`-with-no-digits all reject. I found no input that turns a private address
  into a public classification.
- **SC4 (zero resolution) holds, including the branch that worried me.** The IPv6 arm's `getByName` is
  gated by `host.contains(':')` **and** `IPV6_REGEX`. Measured on JDK 21: any colon-bearing string that
  is not a valid IPv6 literal (`abcd:efab`, `1:2`, `0:0`) throws
  `invalidIPv6LiteralException` from `InetAddress.getAllByName` **without** reaching
  `PlatformResolver.lookupByName`, while a hex-only string with no colon (`abcdef`) *does* resolve in
  90 ms. Both halves of that guard are load-bearing and both are present.
- **The window arithmetic is right at the boundaries** and the replay surface is exactly the documented
  10 s + one fallback window (≤ 20 s), no wider. `constantTimeCompare` is `MessageDigest.isEqual`, which
  is content-constant-time on both the client and server sides; the `||` over the two windows leaks only
  which window matched, which is not a secret.
- **No path in `McpSupervisor.kt` puts the token on the wire.** `probeExistingServer` sends no credential,
  `requestRemoteShutdown` sends only `X-Mcp-Takeover-Proof`, and the token is read exactly once, as an
  HMAC key.
- **The pin cannot silently fall back.** In the `pin == null` branch nothing is installed at all, and the
  hostname verifier returns `false` (via `runCatching { session.peerCertificates }.getOrNull()`) rather
  than propagating `SSLPeerUnverifiedException`. `checkServerTrusted` throws on mismatch; there is no
  input for which the pin is computed and the comparison skipped.

The three Warnings are: a **new** offline-guessing property that proof-of-possession introduces and
ADR-16 does not record; a notation-evasion hole that this phase's own rewrite does **not** close and
whose absence the new KDoc arguably overstates; and a configuration (external mode on a non-loopback
bind host) in which the pin, the fail-closed diagnostic and the new operator runbook all fail to apply,
with zero test coverage.

---

## Critical Issues

None. Every candidate I raised — proof-path bypass of the access-control gate, redirect-borne credential
leak, integer overflow in the `inet_aton` parser, a resolving call on the SSRF path, a trust-manager
fallback — was disproved by inspection or by measurement. I am recording that explicitly rather than
promoting a Warning to fill the section.

## Warnings

### WR-01: The proof turns the MCP token into an offline-guessable secret, and the token field has no entropy floor

**File:** `src/main/kotlin/com/six2dez/burp/aiagent/mcp/McpTakeoverProof.kt:65-78`
(with `src/main/kotlin/com/six2dez/burp/aiagent/ui/SettingsPanelInit.kt:83`,
`src/main/kotlin/com/six2dez/burp/aiagent/ui/SettingsPanelSettingsIO.kt:27`)

**Issue:** `forTarget` computes `HMAC-SHA256(key = token, message = "burp-ai-agent/mcp-takeover|v1|<host>:<port>|<window>")`.
Every byte of that message is known to the squatter: it holds the port, it knows the host string the
client dialled, and the window index is `System.currentTimeMillis() / 10_000`. So the value the squatter
receives is a **verifier for offline brute force of the token itself** — one HMAC-SHA256 per guess, no
interaction with the victim, no rate limit, no lockout. Recovering the token restores exactly the
Finding-7 outcome the phase closed: `Authorization: Bearer <token>` grants full MCP tool access.

This is not a theoretical concern in this product because the token is free text:
`mcpToken.isEditable = true`, `token = mcpToken.text.trim()`, and the only validation anywhere is
`isBlank()` (`SettingsPanelMcpTabs.kt:580`). `McpSettings.generateToken()` (32 random bytes) is the
default and is unbreakable here, but an operator who types `burpmcp2026` hands a squatter a
seconds-to-minutes offline crack.

The disclosure is still strictly better than the pre-phase behaviour (which handed the token over
directly for any entropy), so this is not a regression — but it is a **new residual created by the SC1
design** and it is absent from ADR-16's six `Residual:` bullets, which is the very failure mode T-25-17
exists to prevent ("a residual accepted but not written down is indistinguishable from one nobody
noticed").

**Fix:** Two parts, both cheap.

1. Record it in ADR-16 as a seventh residual, in the same shape as the others, e.g.:
   `- Residual: **offline token guessing from a captured proof.** The proof's message is fully known to
   the squatter, so it is an offline verifier for the token. Infeasible against a generated 32-byte
   token; a short operator-typed token is recoverable. Mitigated by the entropy floor on the token
   field.` (Then raise `MIN_ADR16_RESIDUALS` in `DecisionsAdrTest` in the same commit — see IN-02.)
2. Put a floor under the token field so the mitigation is real rather than advisory:

```kotlin
// SettingsPanelMcpTabs.refreshMcpNotice()
val tokenWeak = mcpToken.text.trim().length < Defaults.MCP_MIN_TOKEN_LENGTH // e.g. 32
if (mcpOn && !tokenBlank && tokenWeak) {
    items += Item(
        SubtleNotice.Level.RISK,
        "<b>Weak MCP token.</b> The bind-conflict takeover proof lets a local process guess a short " +
            "token offline. Use <i>Regenerate token</i>.",
    )
}
```

### WR-02: `SsrfGuard` still misses IPv4-in-IPv6 literals, and the new KDoc reads as if it does not

**File:** `src/main/kotlin/com/six2dez/burp/aiagent/util/SsrfGuard.kt:42` (`IPV6_REGEX`), consumed at
`:66`; claim at `:18-22`

**Issue:** `IPV6_REGEX = ^[0-9a-fA-F:]+$` has no `.` in its character class, so any IPv6 literal that
carries a dotted IPv4 suffix fails the literal gate, returns `null`, and the URL is silently classified
as "not private". Measured against the compiled `SsrfGuard` in `build/classes/kotlin/main`:

```
http://[::ffff:169.254.169.254]/        -> false   (cloud metadata, NOT flagged)
http://[::ffff:192.168.1.10]/           -> false   (RFC-1918, NOT flagged)
http://[0:0:0:0:0:ffff:192.168.1.10]/   -> false
http://::ffff:192.168.1.10/             -> false
http://[::ffff:a9fe:a9fe]/              -> true    (same address, hex spelling — flagged)
http://[::ffff:c0a8:10a]/               -> true
```

The hex spelling of the identical address is flagged and the dotted spelling is not, which is precisely
the notation-evasion class SC3 set out to close. OkHttp's `HttpUrl` accepts and canonicalises
`[::ffff:192.168.1.10]`, so the extension will happily connect where it declines to warn.

This behaviour is **pre-existing** — `IPV6_REGEX` is byte-identical to the pre-phase tree
(`git show 1b234e7^:.../SsrfGuard.kt`) — so it is not a regression introduced here. I am raising it
anyway because (a) it is the same defect class the phase claims to have closed, (b) the rewritten object
KDoc now says *"'Literal' means every `inet_aton` notation, not only the dotted quad (SEC-07)"*, which a
future reader will reasonably take as "notation evasion is handled", and (c) the SC4 corpus in
`SsrfGuardNoResolutionTest` contains no IPv4-mapped form either, so nothing in the suite would notice.
Impact is bounded by the guard being advisory and non-blocking (D-01): the user is under-warned, nothing
is permitted that was previously denied.

**Fix:** Allow `.` in the literal gate — the `contains(':')` guard is what keeps hostnames away from the
resolving call, and a dot does not weaken it (measured: `1.2:3` still throws
`invalid IPv6 address literal` with no lookup). Then add the mapped forms to both test corpora.

```kotlin
private val IPV6_REGEX = Regex("""^[0-9a-fA-F:.]+$""")
```

### WR-03: The pin, its fail-closed diagnostic and the new runbook all stop at loopback — external mode on a non-loopback host is uncovered and undocumented

**File:** `src/main/kotlin/com/six2dez/burp/aiagent/mcp/McpSupervisor.kt:369` and `:390-393`;
`docs/mcp-hardening.md` §"Takeover on a Bind Conflict" item 3

**Issue:** The whole TLS branch is guarded by `isLoopbackUrlHost(url.host)`. For any other bind host the
client installs no override, the JDK default trust store rejects the extension's own `CN=burp-mcp`
self-signed certificate, `probeExistingServer` catches the handshake failure and returns `false`, and
`handleBindFailure` takes the `NO_COMPATIBLE_SERVER` limb — which logs
*"Port appears busy and no compatible MCP server was detected for takeover."* and **schedules no retry
at all** (`McpSupervisor.kt:215-220`). The MCP server stays down after every bind conflict, and the
operator is told the opposite of what happened: the listener *was* our own server.

That host gate is unchanged from the pre-phase code (`git show e6dc8a2:...` shows the same three
loopback strings), so the behaviour is not a phase-25 regression. Three things this phase *did* add
make it worth fixing now:

1. `docs/mcp-hardening.md` item 3 now tells operators *"Under TLS the takeover requires a readable
   keystore at the configured path"* and *"you will see `MCP takeover was not attempted under TLS: no
   pinned certificate could be read from <path>.`"* On a non-loopback host that line never appears, no
   matter how readable the keystore is. The documented diagnostic and the shipped diagnostic disagree.
2. External mode is the only mode that permits a non-loopback host (`KtorMcpServerManager.kt:83-85`
   enforces loopback only when external is off), and external mode requires TLS — so the uncovered
   configuration is exactly the intended external deployment.
3. Every test in the phase pins `host = "127.0.0.1"` — `localSettings`, `localTlsSettings` **and**
   `externalTlsSettings` (`McpTestServerSupport.kt:73, 93, 130`). So 25-01's claim that external-mode
   takeover still works, and 25-03's fail-closed truth table, are both proven only for a loopback bind.
   `openConnection_nonLoopbackTls_doesNotOverrideTlsVerifier` asserts the branch is skipped; nothing
   asserts what happens next.

**Fix:** Minimum — make the diagnostic honest, so the operator is not told "no compatible MCP server"
when the truth is "TLS identity could not be established for a non-loopback host":

```kotlin
if (settings.tlsEnabled && conn is HttpsURLConnection) {
    if (!isLoopbackUrlHost(url.host)) {
        api.logging().logToOutput(
            "MCP takeover was not attempted under TLS: certificate pinning is applied to loopback " +
                "hosts only and this server is bound to ${url.host}. Free the port manually.",
        )
    } else { /* existing pin / no-pin branches */ }
}
```

Better — the pin is a key-identity check, not a name check, so nothing about it actually depends on the
host being loopback. Dropping `isLoopbackUrlHost` from the condition would make external-mode TLS
takeover work for the first time while keeping the fail-closed rule intact. If that is deliberately out
of scope, say so in ADR-16 as a residual and correct item 3 of the runbook.

## Info

### IN-01: `pinnedLeafSha256` breaks the file's own password-hygiene convention

**File:** `src/main/kotlin/com/six2dez/burp/aiagent/mcp/McpTls.kt:54-71`

**Issue:** `generateSelfSigned` carries an explicit IN-01 comment about copying and zeroing the password
array in a `finally`. The new `pinnedLeafSha256` materialises
`settings.tlsKeystorePassword.toCharArray()` on every takeover attempt and never zeroes it, so a fresh
copy of the keystore password is left on the heap per call. Minor and consistent with `resolve` (which
also does not zero), but it is a new call site added to a file that documents the opposite rule.
Separately, `File(keystorePath)` is constructed on line 56 before the `keystorePath.isBlank()` test on
line 57 — harmless, but the guard reads as if it protects the construction.

**Fix:** Wrap the body in `try { … } finally { password.fill(' ') }` (build the array once, outside
`runCatching`), and move the blank check above the `File(...)` construction.

### IN-02: The ADR-16 residual guard cannot catch the deletion it exists to catch

**File:** `src/test/kotlin/com/six2dez/burp/aiagent/DecisionsAdrTest.kt:24` and `:70-83`

**Issue:** `MIN_ADR16_RESIDUALS = 5`, but ADR-16 ships six `Residual:` bullets. The test's own comment
says *"Deleting one from the ADR must be a deliberate act with a red test in front of it, not an editing
accident"* — with a bound one below the shipped count, deleting one bullet by accident leaves the suite
green. The bound needs to equal the shipped count for the guard to mean what it says.

**Fix:** `private const val MIN_ADR16_RESIDUALS = 6` (7 if WR-01 is recorded), and update the comment's
enumeration to match the bullets actually shipped.

### IN-03: The takeover client follows redirects by default

**File:** `src/main/kotlin/com/six2dez/burp/aiagent/mcp/McpSupervisor.kt:312-332` (and `:259-302`)

**Issue:** `HttpURLConnection.instanceFollowRedirects` defaults to `true` and the JDK carries
`setRequestProperty` values across the redirect, including to a different host. A squatter can therefore
answer `POST /__mcp/shutdown` with `302 Location: http://elsewhere/...` and make Burp's process issue
that request, proof header attached. **I do not think this is exploitable beyond what the squatter
already has** — it already possesses the proof and can send it anywhere itself, and a 200 from the
redirect target only produces `SHUTDOWN_REQUESTED`, i.e. the same denial of service it can produce by
answering 200 directly. It is listed because it is a one-line hardening on a path whose entire premise
is "the peer is hostile", and because it makes the takeover client a small outbound-request primitive
from inside Burp's process.

**Fix:** `conn.instanceFollowRedirects = false` in `openConnection`, for both the probe and the shutdown
request. A legitimate MCP server never redirects either route.

### IN-04: The SC4 assertion reads a process-global counter with no isolation from other suites

**File:** `src/test/kotlin/com/six2dez/burp/aiagent/util/SsrfGuardNoResolutionTest.kt:52-65`;
`src/test/kotlin/com/six2dez/burp/aiagent/util/CountingInetAddressResolverProvider.kt:56-79`

**Issue:** The vacuity control is well designed (the counter is proven to move before it is asserted not
to move, and a deleted service file turns test 1 red rather than test 2 vacuously green — I checked that
specifically). The residual risk is the opposite one: `lookupCount` is a JVM-wide static, the provider is
installed for the entire test JVM, and the assertion is `count() == 0`. Any name resolution performed by
a *background* thread of another suite — an OkHttp connection pool, a Netty resolver, anything targeting
a hostname rather than `127.0.0.1` — between `reset()` and the assertion fails this security gate for
reasons unrelated to `SsrfGuard`. The current suite runs sequentially in one JVM with
`maxParallelForks = 1` and no `junit-platform.properties`, and every in-repo fixture I checked uses
literal `127.0.0.1`, so the window is small today; it widens the moment anyone enables parallel execution
or adds a hostname-based fixture.

**Fix:** Assert on attribution rather than on the global total, e.g. keep the `count() == 0` assertion but
make the failure message decisive by filtering `recentNames()` against the corpus hosts, or record the
counter immediately before and after the corpus loop and assert the *delta* is zero with the observed
names reported either way.

### IN-05: `McpTakeoverSquatterTest` shadows the shared `SHUTDOWN_PATH` constant

**File:** `src/test/kotlin/com/six2dez/burp/aiagent/mcp/McpTakeoverSquatterTest.kt:207`

**Issue:** The test declares `private const val SHUTDOWN_PATH = "/__mcp/shutdown"` in its companion,
which shadows the `internal const val SHUTDOWN_PATH` that lives in the same package
(`McpAccessControlDecision.kt:24`) and that the test file already uses unqualified for `HEALTH_PATH`.
`KtorMcpServerManager` was changed in this phase specifically so the route and the gate cannot drift
apart on this literal; the test that proves the wire behaviour opts out of that. If production ever moves
the path, this test keeps dispatching on the old literal and silently stops covering the shutdown route.

**Fix:** Delete the private constant and use the package-level one, as the file already does for
`HEALTH_PATH`.

### IN-06: `Ipv4LiteralTest` leaves the two- and three-part trailing maxima unpinned

**File:** `src/test/kotlin/com/six2dez/burp/aiagent/util/Ipv4LiteralTest.kt:24-59`

**Issue:** The KDoc promises "every limit is pinned on BOTH sides", and the four-part (`255`/`256`) and
one-part (`4294967295`/`4294967296`) boundaries are. The `a.b` (`16 777 215`/`16 777 216`) and `a.b.c`
(`65 535`/`65 536`) boundaries are not — and `maxForTrailing` is exactly the function where an off-by-one
would let an out-of-range part fold into a wrong, possibly public-looking address. I verified the
implementation is correct on all four arities by fuzzing the compiled class, so this is a coverage gap
rather than a live defect.

**Fix:** Add to `Ipv4LiteralTest`:

```kotlin
assertArrayEquals(byteArrayOf(10, 255.toByte(), 255.toByte(), 255.toByte()), Ipv4Literal.parse("10.16777215"))
assertNull(Ipv4Literal.parse("10.16777216"))
assertArrayEquals(byteArrayOf(192.toByte(), 168.toByte(), 255.toByte(), 255.toByte()), Ipv4Literal.parse("192.168.65535"))
assertNull(Ipv4Literal.parse("192.168.65536"))
```

### IN-07: `isLoopbackUrlHost`'s `"::1"` arm is unreachable

**File:** `src/main/kotlin/com/six2dez/burp/aiagent/mcp/McpSupervisor.kt:390-393`

**Issue:** Two independent reasons this branch can never fire, both measured on JDK 21:
`java.net.URL.getHost()` returns the **bracketed** form for IPv6 literals (`new URL("https://[::1]:8443/x").getHost()`
→ `[::1]`), so the `host == "::1"` comparison never matches; and the caller builds the URL as
`URI.create("https://${settings.host}:${settings.port}/…")`, which for `host = "::1"` throws
`MalformedURLException: Error at index 0 in: ":1:8443"` long before `openConnection` is reached. The gate
in `McpAccessControlDecision.LOOPBACK_HOSTS` does accept `::1` and `0:0:0:0:0:0:0:1` as bind hosts, so an
operator can configure a bind the takeover path cannot address at all. Pre-existing (the deleted code had
the same three comparisons); noted because the new code reproduces a dead condition verbatim and because
it is the same "loopback means these three strings" assumption that WR-03 turns on.

**Fix:** Either strip brackets before comparing and bracket the host when building the URL —

```kotlin
private fun isLoopbackUrlHost(host: String): Boolean =
    host.removeSurrounding("[", "]").let {
        it.equals("localhost", ignoreCase = true) || it == "127.0.0.1" || it == "::1" || it == "0:0:0:0:0:0:0:1"
    }
```

— or reuse `isLoopbackAuthority(host, null)` from `McpAccessControlDecision.kt`, which already handles
both IPv6 spellings, and drop the local copy.

---

_Reviewed: 2026-08-22_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
