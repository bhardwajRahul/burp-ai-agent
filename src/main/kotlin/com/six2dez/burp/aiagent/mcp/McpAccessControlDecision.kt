package com.six2dez.burp.aiagent.mcp

import com.six2dez.burp.aiagent.config.McpSettings
import io.ktor.http.HttpStatusCode
import java.net.URI
import java.security.MessageDigest

// SEC-04 / SEC-05: the pure decision half of the MCP access-control gate.
//
// Everything in this file is `internal` rather than `private` so McpAccessControlDecisionTest can
// reach it without reflection — the same test-seam convention used by redact/Redaction.kt:215-228.
// Nothing here imports io.ktor.server.*: `io.ktor.http` is engine-free, which is what keeps the
// decision provable without binding a port.

/** Liveness route probed by [McpSupervisor] to identify a foreign holder of the MCP port (D-01/D-02). */
internal const val HEALTH_PATH = "/__mcp/health"

/**
 * Authorities that count as loopback. The expanded IPv6 form is listed alongside `::1` so a server
 * bound to IPv6 loopback cannot reject its own `Host` header (SEC-05 5b).
 */
private val LOOPBACK_HOSTS = setOf("localhost", "127.0.0.1", "::1", "0:0:0:0:0:0:0:1")

/** IPv6 loopback literals that are valid authorities on their own, with no brackets and no port. */
private val BARE_IPV6_LOOPBACKS = setOf("::1", "0:0:0:0:0:0:0:1")

/**
 * `[<ipv6-literal>]` with an optional `:<port>`. Anchored with `matchEntire`, so a missing `]` or any
 * trailing junk fails to match and the authority is rejected — the fail-closed shape without needing
 * an exception handler or an extra early exit.
 */
private val BRACKETED_AUTHORITY_REGEX = Regex("""\[([0-9a-f:.]+)](?::(\d+))?""")

/**
 * `<host>` with an optional `:<port>`. `:` is deliberately absent from the host character class, which
 * is what rejects an unbracketed IPv6 literal carrying a port (`::1:9876`) as ambiguous, and what
 * rejects a non-numeric port (`localhost:abc`).
 */
private val PLAIN_AUTHORITY_REGEX = Regex("""([0-9a-z.\-]+)(?::(\d+))?""")

/** Moved verbatim from KtorMcpServerManager.isBrowserRequest so the indicator list cannot drift. */
private val BROWSER_INDICATORS =
    listOf(
        "mozilla/",
        "chrome/",
        "safari/",
        "webkit/",
        "gecko/",
        "firefox/",
        "edge/",
        "opera/",
        "browser",
    )

/**
 * Why a request was denied. The wire string is defined once, here, and nowhere else — both the
 * audit payload and the Output-tab line read [wireValue] so the two sinks cannot disagree (D-06).
 */
internal enum class BlockReason(
    val wireValue: String,
) {
    ORIGIN_MISMATCH("origin_mismatch"),
    HOST_MISMATCH("host_mismatch"),
    REFERER_MISMATCH("referer_mismatch"),
    BROWSER_NO_ORIGIN("browser_no_origin"),
    UNAUTHORIZED("unauthorized"),
    BLANK_TOKEN("blank_token"),
}

/**
 * The attacker-controlled inputs the gate is allowed to look at, lifted out of the Ktor call so the
 * decision never touches an `ApplicationCall`. Every field is untrusted; nothing here has been
 * sanitized yet — sanitization belongs to [McpBlockedRequestReporter], at the log boundary.
 */
internal data class RequestFacts(
    val method: String = "GET",
    val path: String,
    val origin: String? = null,
    val host: String? = null,
    val referer: String? = null,
    val userAgent: String? = null,
    val authorization: String? = null,
)

/** Outcome of [evaluate]: either the request proceeds, or it is refused with a status and a reason. */
internal sealed class GateDecision {
    data object Allow : GateDecision()

    data class Deny(
        val status: HttpStatusCode,
        val reason: BlockReason,
        val facts: RequestFacts,
    ) : GateDecision()
}

/**
 * Decides allow-or-deny for one MCP request from [facts] plus an immutable [settings] snapshot.
 *
 * Pure by contract (D-08): no logging, no audit events, no I/O, no side effects of any kind. The
 * caller invokes [McpBlockedRequestReporter] from the [GateDecision.Deny] branch — the reporter is
 * never invoked from inside this function. That separation is what makes every row of the
 * access-control matrix assertable in a unit test with no server and no clock.
 */
internal fun evaluate(
    facts: RequestFacts,
    settings: McpSettings,
): GateDecision = if (settings.externalEnabled) evaluateExternal(facts, settings) else evaluateLocal(facts, settings)

/**
 * External mode: the bearer token is the only control the gate owns. Ktor's CORS plugin owns
 * `Origin`, so `Origin`/`Host`/`Referer`/`User-Agent` are deliberately ignored here.
 */
private fun evaluateExternal(
    facts: RequestFacts,
    settings: McpSettings,
): GateDecision =
    when {
        // D-01: the liveness probe stays token-free so a bind-conflict can be diagnosed without
        // first holding a credential.
        facts.path == HEALTH_PATH -> GateDecision.Allow
        // SEC-05 5c: external mode with a blank configured token fails closed on every route
        // rather than admitting "Bearer " as a valid credential.
        settings.token.isBlank() ->
            GateDecision.Deny(HttpStatusCode.Unauthorized, BlockReason.BLANK_TOKEN, facts)
        !isAuthorizedBearer(facts.authorization.orEmpty(), settings.token) ->
            GateDecision.Deny(HttpStatusCode.Unauthorized, BlockReason.UNAUTHORIZED, facts)
        else -> GateDecision.Allow
    }

/**
 * Local mode: DNS-rebinding and browser-CSRF denial, applied to EVERY path including [HEALTH_PATH]
 * (D-03). `Authorization` is never inspected in local mode.
 */
private fun evaluateLocal(
    facts: RequestFacts,
    settings: McpSettings,
): GateDecision =
    when {
        facts.origin != null && !isLoopbackUrlAuthority(facts.origin) ->
            GateDecision.Deny(HttpStatusCode.Forbidden, BlockReason.ORIGIN_MISMATCH, facts)
        // BEHAVIOUR CHANGE, deliberate — do NOT "restore" the pre-fix `else if`.
        // KtorMcpServerManager.kt:195 read `else if (isBrowserRequest(userAgent))`, which fired
        // whenever the Origin branch did not — including when a present-and-VALID loopback Origin
        // accompanied a browser User-Agent. That request was 403 before and is allowed now. SEC-04's
        // text mandates only the Origin-ABSENT case, and Ktor's CORS plugin already admits nothing
        // but localhost/127.0.0.1 on the MCP port in local mode while the server serves no HTML, so
        // a valid loopback Origin had to clear CORS before the gate ever saw it.
        facts.origin == null && isBrowserUserAgent(facts.userAgent) ->
            GateDecision.Deny(HttpStatusCode.Forbidden, BlockReason.BROWSER_NO_ORIGIN, facts)
        facts.host != null && !isLoopbackAuthority(facts.host, settings.port) ->
            GateDecision.Deny(HttpStatusCode.Forbidden, BlockReason.HOST_MISMATCH, facts)
        facts.referer != null && !isLoopbackUrlAuthority(facts.referer) ->
            GateDecision.Deny(HttpStatusCode.Forbidden, BlockReason.REFERER_MISMATCH, facts)
        else -> GateDecision.Allow
    }

/**
 * The ONE place an HTTP authority is parsed. Yields `host to port`, with a null port when the input
 * carried none, or null when the input is malformed.
 *
 * This function holds the whole file's budget of two early exits: detekt.yml overrides only
 * LongMethod, LongParameterList, MaxLineLength and FunctionNaming, so ReturnCount runs at its default
 * max of 2. Everything else in this file is expression-bodied on purpose, because QUAL-07 forbids
 * growing detekt-baseline.xml to work around a rule this code can simply satisfy.
 */
private fun parseAuthority(value: String): Pair<String, Int?>? {
    val trimmed = value.trim().lowercase()
    // Checked BEFORE the regexes so a bare `::1` carrying no port is accepted; the plain-authority
    // pattern cannot admit it without also admitting the ambiguous `::1:9876`.
    if (trimmed in BARE_IPV6_LOOPBACKS) return trimmed to null
    val match = BRACKETED_AUTHORITY_REGEX.matchEntire(trimmed) ?: PLAIN_AUTHORITY_REGEX.matchEntire(trimmed)
    // Group 2 is empty when no port was present, and `toIntOrNull` maps that to a null port without
    // an exception handler. An input matching neither pattern is malformed and yields null.
    return match?.let { it.groupValues[1] to it.groupValues[2].toIntOrNull() }
}

/**
 * D-11: the single shared loopback-authority predicate behind the `Host`, `Origin` and `Referer`
 * checks, so the three cannot drift apart again. A null [expectedPort] or an authority without a
 * port skips the port comparison.
 *
 * Deliberately NOT built on `URI(...).toURL().host` — that returns `[::1]` WITH brackets, which is
 * the exact defect SEC-05 5b reports.
 */
internal fun isLoopbackAuthority(
    authority: String,
    expectedPort: Int?,
): Boolean =
    parseAuthority(authority)?.let { (host, port) ->
        host in LOOPBACK_HOSTS && (expectedPort == null || port == null || port == expectedPort)
    } == true

/**
 * Loopback check for a full URL value such as an `Origin` or `Referer` header. `URI(...)` throws on
 * malformed input, so this keeps the repo's fail-closed wrapper. The port is intentionally not
 * compared: local-mode port binding is asserted through the `Host` header, and CORS already
 * constrains which loopback origins can reach the gate.
 */
internal fun isLoopbackUrlAuthority(url: String): Boolean =
    try {
        URI(url).host?.let { isLoopbackAuthority(it, null) } == true
    } catch (_: Exception) {
        false
    }

/** Moved from KtorMcpServerManager.isBrowserRequest; a null User-Agent is not a browser. */
internal fun isBrowserUserAgent(userAgent: String?): Boolean = userAgent?.lowercase()?.let { lower -> BROWSER_INDICATORS.any { lower.contains(it) } } == true

/**
 * SEC-05 5c / F19: a blank configured token can never authenticate. The `isNotBlank` guard runs
 * BEFORE the expected string is built, so `isAuthorizedBearer("Bearer ", "")` is false.
 */
internal fun isAuthorizedBearer(
    authHeader: String,
    token: String,
): Boolean = token.isNotBlank() && constantTimeCompare(authHeader, "Bearer $token")

/** Byte-for-byte the comparison from KtorMcpServerManager.constantTimeEquals — do not hand-roll a loop. */
internal fun constantTimeCompare(
    left: String,
    right: String,
): Boolean = MessageDigest.isEqual(left.toByteArray(Charsets.UTF_8), right.toByteArray(Charsets.UTF_8))
