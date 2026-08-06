package com.six2dez.burp.aiagent.mcp

import com.six2dez.burp.aiagent.config.McpSettings
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import io.ktor.server.response.respond

// SEC-04 / F1: the Ktor half of the MCP access-control gate. This file is the FIX for the bypass —
// the previous checks lived in an `intercept(ApplicationCallPipeline.Call)` block registered after
// `routing {}`, which put them behind RoutingRoot's own Call-phase interceptor, so every resolved
// route (including the SDK's /sse and /message) was served before they ran.
//
// `PluginBuilder.onCall` attaches to `ApplicationCallPipeline.ApplicationPhase.Plugins`, and
// `ApplicationCallPipeline` is constructed with the phases Setup, Monitoring, Plugins, Call,
// Fallback in that literal order. Both facts are fixed at pipeline construction, so this gate runs
// before routing REGARDLESS of where `install(McpAccessControl)` sits in the module lambda — the
// class of bug being fixed cannot recur through a future re-ordering of that lambda.
//
// All security logic lives in McpAccessControlDecision.kt and all observability lives behind
// [McpAccessControlConfig.onBlocked]; this file only adapts an ApplicationCall to RequestFacts.

/**
 * Configuration for [McpAccessControl].
 *
 * [settings] is passed through the plugin config rather than captured from an enclosing closure so
 * that the immutability of the snapshot is explicit and the gate is configurable from a test.
 *
 * [onBlocked] receives the whole [GateDecision.Deny]; the caller supplies the mode flag and the
 * clock, which keeps time-reading out of both this plugin and [McpBlockedRequestReporter]. It is
 * `lateinit` rather than defaulted to an empty lambda ON PURPOSE: a default no-op would let a caller
 * silently drop the D-06 audit event, the D-07 sanitization, the D-09 rate-limited Output line and
 * the D-10 hashing while every compile-time and static check still passed. `lateinit` turns that
 * omission into a startup crash instead. Do not give it a default value.
 */
internal class McpAccessControlConfig {
    lateinit var settings: McpSettings
    lateinit var onBlocked: (GateDecision.Deny) -> Unit
}

/**
 * SC3. Values are byte-identical to the four headers the deleted Call-phase interceptor appended.
 */
private val SECURITY_HEADERS: List<Pair<String, String>> =
    listOf(
        "X-Frame-Options" to "DENY",
        "X-Content-Type-Options" to "nosniff",
        "Referrer-Policy" to "same-origin",
        "Content-Security-Policy" to "default-src 'none'",
    )

/**
 * The MCP access-control gate. Evaluates every inbound call in the `Plugins` phase, denies with a
 * bare status (D-08), and appends the four security headers on the allow path.
 *
 * Runs on Netty event-loop threads, so there is deliberately no blocking I/O, no monitor lock, no
 * Swing access and no file write anywhere in this file.
 */
internal val McpAccessControl =
    createApplicationPlugin("McpAccessControl", ::McpAccessControlConfig) {
        val settings = pluginConfig.settings
        val onBlocked = pluginConfig.onBlocked

        onCall { call ->
            // SEC-04 / RESEARCH pitfall P4 — LOAD-BEARING, do not remove.
            // Ktor's CORS plugin is also a Plugins-phase plugin (it too registers through
            // PluginBuilder.onCall), so it shares this phase with the gate and runs first because it
            // is installed first. It was verified by execution that when a first same-phase
            // interceptor responds, the SECOND ONE STILL EXECUTES — responding does not cancel the
            // rest of the phase. Without this guard the gate would respond a second time after CORS
            // had already answered a preflight or rejected a foreign origin.
            if (!call.response.isCommitted) {
                when (val decision = evaluate(requestFacts(call), settings)) {
                    is GateDecision.Deny -> {
                        // D-06/D-07/D-09/D-10 all hang off this single call. An empty lambda here
                        // would compile, pass detekt and leave blocked requests invisible.
                        onBlocked(decision)
                        // D-08: bare status, no body, no WWW-Authenticate. The pipeline is NOT
                        // explicitly finished here, and does not need to be:
                        // RoutingNode.buildPipeline wraps every registered handle{} body in
                        // `if (call.isHandled) return`, and isHandled == response.isCommitted, so
                        // responding here provably skips the handler (verified: a route-side flag
                        // was still false 700 ms after the client had received the 401). The
                        // CallContext method that would end the pipeline early also carries
                        // Kotlin's internal name-mangling suffix and is not callable from here.
                        call.respond(decision.status)
                    }
                    // SC3: appended from the pre-routing Plugins phase, which is what makes the
                    // headers deterministic — they land on matched-route 200s and on the
                    // text/event-stream SSE response over both HTTP/1.1 and HTTP/2. Appending after
                    // a handler had committed was measured to work over TLS/HTTP-2 and silently fail
                    // over cleartext HTTP/1.1 on the same code path (RESEARCH pitfall P3). The
                    // respond-side plugin hook is the wrong seam: it attaches to
                    // ApplicationSendPipeline.Transform and would also fire for the 401/403.
                    GateDecision.Allow -> appendSecurityHeaders(call)
                }
            }
        }
    }

/**
 * Lifts the attacker-controlled inputs out of the call. Values are kept RAW here: `evaluate` must
 * see what the client actually sent, and sanitization belongs to the log boundary (D-07).
 *
 * Uses `path()` and not `uri`, so a query string cannot change the [HEALTH_PATH] comparison.
 */
private fun requestFacts(call: ApplicationCall): RequestFacts =
    RequestFacts(
        method = call.request.httpMethod.value,
        path = call.request.path(),
        origin = call.request.headers["Origin"],
        host = call.request.headers["Host"],
        referer = call.request.headers["Referer"],
        userAgent = call.request.headers["User-Agent"],
        authorization = call.request.headers["Authorization"],
    )

/**
 * `ResponseHeaders.append` does not deduplicate, so each header is appended only when absent. That
 * keeps a future route which sets one of these itself from producing a doubled header.
 */
private fun appendSecurityHeaders(call: ApplicationCall) {
    SECURITY_HEADERS.forEach { (name, value) ->
        if (call.response.headers[name] == null) {
            call.response.headers.append(name, value)
        }
    }
}
