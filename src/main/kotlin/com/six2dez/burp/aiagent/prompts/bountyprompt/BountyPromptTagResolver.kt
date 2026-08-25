package com.six2dez.burp.aiagent.prompts.bountyprompt

import burp.api.montoya.http.message.HttpRequestResponse
import com.six2dez.burp.aiagent.context.ContextOptions
import com.six2dez.burp.aiagent.redact.Redaction
import com.six2dez.burp.aiagent.redact.RedactionPolicy
import java.net.URI

class BountyPromptTagResolver {
    private val defaultMaxChunkChars = 3_000
    private val defaultMaxTagChars = 12_000
    private val sensitiveParamName =
        Regex(
            "(token|key|auth|session|jwt|cookie|password|secret|api_key|apikey)",
            RegexOption.IGNORE_CASE,
        )

    fun resolve(
        definition: BountyPromptDefinition,
        requestResponses: List<HttpRequestResponse>,
        options: ContextOptions,
    ): ResolvedBountyPrompt {
        val policy = RedactionPolicy.fromMode(options.privacyMode)
        val limits = limitsForCategory(definition.category)
        val tagValues =
            definition.tagsUsed.associateWith { tag ->
                buildTagValue(
                    tag = tag,
                    requestResponses = requestResponses,
                    policy = policy,
                    hostSalt = options.hostSalt,
                    maxChunkChars = limits.first,
                    maxTagChars = limits.second,
                )
            }

        var resolved = definition.userPrompt
        for ((tag, value) in tagValues) {
            resolved = resolved.replace(tag.token, value)
        }
        // Remove any unknown HTTP_* tokens left in the prompt.
        resolved = resolved.replace(Regex("\\[HTTP_[^\\]]+\\]"), "").trim()

        val preview =
            buildString {
                appendLine("Kind: BountyPrompt selection")
                appendLine("Items: ${requestResponses.size}")
                appendLine("Prompt ID: ${definition.id}")
                appendLine("Prompt Type: ${definition.outputType.name}")
                appendLine("Category: ${definition.category.name}")
                appendLine("Tags used: ${if (definition.tagsUsed.isEmpty()) "none" else definition.tagsUsed.joinToString { it.token }}")
                appendLine("Selective context: true")
                appendLine("Redaction:")
                appendLine("  - Cookie stripping: ${policy.stripCookies}")
                appendLine("  - Token redaction: ${policy.redactTokens}")
                appendLine("  - Host anonymization: ${policy.anonymizeHosts}")
                appendLine("Deterministic: ${options.deterministic}")
            }.trimIndent()

        return ResolvedBountyPrompt(
            resolvedUserPrompt = resolved,
            previewText = preview,
        )
    }

    private fun buildTagValue(
        tag: BountyPromptTag,
        requestResponses: List<HttpRequestResponse>,
        policy: RedactionPolicy,
        hostSalt: String,
        maxChunkChars: Int,
        maxTagChars: Int,
    ): String {
        if (requestResponses.isEmpty()) return "<no request/response selected>"
        val sections = mutableListOf<String>()
        for ((index, rr) in requestResponses.withIndex()) {
            val requestRaw = rr.request().toString()
            val responseRaw = rr.response()?.toString()
            val requestRedacted = Redaction.apply(requestRaw, policy, stableHostSalt = hostSalt)
            val responseRedacted = responseRaw?.let { Redaction.apply(it, policy, stableHostSalt = hostSalt) }
            val safeUrl = redactUrl(rr.request().url(), policy, hostSalt)
            val label = "[${index + 1}] ${rr.request().method()} $safeUrl"

            val value =
                when (tag) {
                    BountyPromptTag.HTTP_REQUESTS -> truncateChunk(requestRedacted, maxChunkChars)
                    BountyPromptTag.HTTP_REQUESTS_HEADERS -> truncateChunk(extractHeaders(requestRedacted), maxChunkChars)
                    BountyPromptTag.HTTP_REQUESTS_PARAMETERS ->
                        truncateChunk(
                            buildRequestParameters(rr, policy, hostSalt),
                            maxChunkChars,
                        )
                    BountyPromptTag.HTTP_REQUEST_BODY -> truncateChunk(extractBody(requestRedacted), maxChunkChars)
                    BountyPromptTag.HTTP_RESPONSES -> truncateChunk(responseRedacted ?: "<no response>", maxChunkChars)
                    BountyPromptTag.HTTP_RESPONSE_HEADERS ->
                        truncateChunk(
                            responseRedacted?.let { extractHeaders(it) } ?: "<no response>",
                            maxChunkChars,
                        )
                    BountyPromptTag.HTTP_RESPONSE_BODY ->
                        truncateChunk(
                            responseRedacted?.let { extractBody(it) } ?: "<no response>",
                            maxChunkChars,
                        )
                    BountyPromptTag.HTTP_STATUS_CODE -> rr.response()?.statusCode()?.toString() ?: "<no response>"
                    BountyPromptTag.HTTP_COOKIES -> truncateChunk(extractCookies(requestRedacted, responseRedacted), maxChunkChars)
                }
            sections.add("$label\n$value")
        }
        return truncateTag(sections.joinToString("\n\n----------------------------------------------------------------\n\n"), maxTagChars)
    }

    private fun buildRequestParameters(
        rr: HttpRequestResponse,
        policy: RedactionPolicy,
        hostSalt: String,
    ): String {
        val params =
            rr.request().parameters().take(80).joinToString("\n") { param ->
                val rawValue = param.value().take(500)
                // (PRIV-05) D-27-21 — the cookie TYPE gate, added ALONGSIDE the pre-existing
                // `sensitiveParamName` NAME filter, never instead of it. The two answer DIFFERENT
                // questions — "does this name look sensitive" versus "is this parameter a cookie" —
                // and the name filter does not match e.g. PHPSESSID, so removing either narrows the
                // control. This site is the one carrier in its class that would hold BURP-HELD
                // request data rather than caller-echoed content, which is why it is fixed now
                // rather than recorded as latent.
                //
                // The type gate is FIRST so it wins when both apply: a cookie is stripped, not
                // merely token-redacted, matching what sanitizeHeaders and sanitizeParameters write
                // for the same bytes on the MCP path. One vocabulary across all three carriers.
                //
                // UNADOPTED ALTERNATIVE, recorded rather than left as an omission: routing this tag
                // value through `Redaction.apply`, which is the shape every SIBLING tag above uses.
                // Rejected here because it would bring the WHOLE rule set to bear on this block — a
                // strictly larger behaviour change, on dead code, in a plan scoped to the cookie
                // class — and because buildRequestParameters already renders the `name=value (TYPE)`
                // shape that `Redaction.cookieTypedParamRegex` covers, so the two approaches would
                // produce two controls for one class at one site. That divergence is the defect this
                // phase keeps paying for.
                //
                // WIDER DEFECT AT THIS SITE, recorded and deliberately NOT fixed here: because the
                // tag value never passes `Redaction.apply`, a JWT, bearer token or secret carried in
                // a URL- or BODY-typed parameter VALUE reaches the prompt verbatim in EVERY mode —
                // the name filter keys on the parameter NAME, not the value. That is outside
                // PRIV-05's cookie wording, it is latent while this class stays uninstantiated
                // (re-measured at execution time: zero instantiations in src/main/kotlin), and plan
                // 27-09 records it as a named residual.
                val safeValue =
                    when {
                        policy.stripCookies && Redaction.isCookieParameterType(param.type().name) -> "[STRIPPED]"
                        policy.redactTokens && sensitiveParamName.containsMatchIn(param.name()) -> "[REDACTED]"
                        else -> rawValue
                    }
                "${param.name()}=$safeValue (${param.type().name})"
            }
        val safeUrl = redactUrl(rr.request().url(), policy, hostSalt)
        return buildString {
            appendLine("URL: $safeUrl")
            appendLine("Parameters:")
            append(if (params.isBlank()) "<none>" else params)
        }.trim()
    }

    private fun extractCookies(
        requestText: String,
        responseText: String?,
    ): String {
        val requestCookies =
            requestText
                .lineSequence()
                .filter { it.startsWith("Cookie:", ignoreCase = true) }
                .toList()
        val responseCookies =
            responseText
                .orEmpty()
                .lineSequence()
                .filter { it.startsWith("Set-Cookie:", ignoreCase = true) }
                .toList()
        val lines = mutableListOf<String>()
        if (requestCookies.isNotEmpty()) {
            lines.add("Request Cookies:")
            lines.addAll(requestCookies)
        }
        if (responseCookies.isNotEmpty()) {
            if (lines.isNotEmpty()) lines.add("")
            lines.add("Response Cookies:")
            lines.addAll(responseCookies)
        }
        return lines.joinToString("\n").ifBlank { "<none>" }
    }

    private fun extractHeaders(raw: String): String {
        val idx = raw.indexOf("\r\n\r\n").takeIf { it >= 0 } ?: raw.indexOf("\n\n")
        return if (idx >= 0) raw.substring(0, idx) else raw
    }

    private fun extractBody(raw: String): String {
        val idxRr = raw.indexOf("\r\n\r\n")
        if (idxRr >= 0 && idxRr + 4 <= raw.length) return raw.substring(idxRr + 4)
        val idxNn = raw.indexOf("\n\n")
        return if (idxNn >= 0 && idxNn + 2 <= raw.length) raw.substring(idxNn + 2) else ""
    }

    private fun truncateChunk(
        text: String,
        maxChunkChars: Int,
    ): String {
        if (text.length <= maxChunkChars) return text
        return text.take(maxChunkChars) + "\n...[truncated]..."
    }

    private fun truncateTag(
        text: String,
        maxTagChars: Int,
    ): String {
        if (text.length <= maxTagChars) return text
        return text.take(maxTagChars) + "\n...[tag content truncated]..."
    }

    private fun limitsForCategory(category: BountyPromptCategory): Pair<Int, Int> =
        when (category) {
            BountyPromptCategory.DETECTION -> 2_500 to 10_000
            BountyPromptCategory.RECON -> 3_500 to 14_000
            BountyPromptCategory.ADVISORY -> defaultMaxChunkChars to defaultMaxTagChars
        }

    private fun redactUrl(
        rawUrl: String,
        policy: RedactionPolicy,
        hostSalt: String,
    ): String =
        try {
            val uri = URI(rawUrl)
            val safeHost =
                if (!uri.host.isNullOrBlank() && policy.anonymizeHosts) {
                    Redaction.anonymizeHost(uri.host, hostSalt)
                } else {
                    uri.host
                }
            val safeQuery =
                when {
                    uri.query.isNullOrBlank() -> uri.query
                    !policy.redactTokens -> uri.query
                    else -> redactSensitiveQuery(uri.query)
                }
            URI(
                uri.scheme,
                uri.userInfo,
                safeHost,
                uri.port,
                uri.path,
                safeQuery,
                uri.fragment,
            ).toString()
        } catch (_: Exception) {
            rawUrl
        }

    private fun redactSensitiveQuery(query: String): String {
        return query.split("&").joinToString("&") { pair ->
            val idx = pair.indexOf('=')
            if (idx <= 0) return@joinToString pair
            val key = pair.substring(0, idx)
            val value = pair.substring(idx + 1)
            if (sensitiveParamName.containsMatchIn(key)) {
                "$key=[REDACTED]"
            } else {
                "$key=$value"
            }
        }
    }
}
