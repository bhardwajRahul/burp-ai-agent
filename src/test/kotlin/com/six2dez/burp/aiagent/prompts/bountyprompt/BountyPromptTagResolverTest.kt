package com.six2dez.burp.aiagent.prompts.bountyprompt

import burp.api.montoya.http.message.HttpRequestResponse
import burp.api.montoya.http.message.params.HttpParameterType
import burp.api.montoya.http.message.params.ParsedHttpParameter
import burp.api.montoya.http.message.requests.HttpRequest
import burp.api.montoya.http.message.responses.HttpResponse
import com.six2dez.burp.aiagent.context.ContextOptions
import com.six2dez.burp.aiagent.redact.PrivacyMode
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BountyPromptTagResolverTest {
    @Test
    fun resolve_replacesTagsAndAppliesRedaction() {
        val urlParam = mock<ParsedHttpParameter>()
        whenever(urlParam.type()).thenReturn(HttpParameterType.URL)
        whenever(urlParam.name()).thenReturn("token")
        whenever(urlParam.value()).thenReturn("abc123")

        val request = mock<HttpRequest>()
        whenever(request.toString()).thenReturn(
            """
            POST /api/login?token=abc123 HTTP/1.1
            Host: example.com
            Authorization: Bearer abc.def.ghi
            Cookie: session=supersecret
            Content-Type: application/json
            
            {"username":"alice","password":"secret"}
            """.trimIndent(),
        )
        whenever(request.method()).thenReturn("POST")
        whenever(request.url()).thenReturn("https://example.com/api/login?token=abc123&next=/home")
        whenever(request.parameters()).thenReturn(listOf(urlParam))

        val response = mock<HttpResponse>()
        whenever(response.toString()).thenReturn(
            """
            HTTP/1.1 200 OK
            Set-Cookie: sid=123; HttpOnly
            Content-Type: application/json
            
            {"status":"ok"}
            """.trimIndent(),
        )
        whenever(response.statusCode()).thenReturn(200)

        val rr = mock<HttpRequestResponse>()
        whenever(rr.request()).thenReturn(request)
        whenever(rr.response()).thenReturn(response)

        val promptText =
            """
            Check:
            [HTTP_Requests_Headers]
            [HTTP_Response_Headers]
            [HTTP_Requests_Parameters]
            [HTTP_Cookies]
            [HTTP_Status_Code]
            """.trimIndent()
        val definition =
            BountyPromptDefinition(
                id = "Security_Headers_Analysis",
                title = "Security Headers Analysis",
                category = BountyPromptCategory.DETECTION,
                outputType = BountyPromptOutputType.ISSUE,
                systemPrompt = "System",
                userPrompt = promptText,
                severity = "Information",
                confidence = BountyPromptConfidence.TENTATIVE,
                tagsUsed = BountyPromptTag.extractFrom(promptText),
            )

        val resolved =
            BountyPromptTagResolver().resolve(
                definition = definition,
                requestResponses = listOf(rr),
                options =
                    ContextOptions(
                        privacyMode = PrivacyMode.STRICT,
                        deterministic = true,
                        hostSalt = "test-salt",
                    ),
            )

        assertFalse(resolved.resolvedUserPrompt.contains("[HTTP_"))
        assertTrue(resolved.resolvedUserPrompt.contains("Host: host-"))
        assertTrue(resolved.resolvedUserPrompt.contains("Authorization: [REDACTED]"))
        assertTrue(resolved.resolvedUserPrompt.contains("Cookie: [STRIPPED]"))
        assertTrue(resolved.resolvedUserPrompt.contains("token=[REDACTED]"))
        assertTrue(resolved.resolvedUserPrompt.contains("200"))
    }

    // ── (PRIV-05) 27-07 task 3 / D-27-21 — the cookie TYPE gate on buildRequestParameters ──
    //
    // WHY THIS SITE MATTERS MORE THAN THE TWO MCP TOOLS. `request_parse` and `params_extract` both
    // call `HttpRequest.httpRequest(input.content)` on CALLER-SUPPLIED content, so they ECHO a
    // cookie the caller already holds. This resolver reads a REAL Burp-held `HttpRequestResponse`
    // and sends it to a configured AI backend. It is latent only because the class has no
    // instantiation in `src/main/kotlin` — RE-MEASURED at execution time, not inherited.
    //
    // Every assertion below is on the RESOLVED TAG OUTPUT through the public `resolve(...)`, not on
    // the private helper, so the probe measures what a prompt would actually carry.

    @Test
    fun cookieTypedParameterValueIsStrippedFromTheParametersTagUnderStrict() {
        val resolved = resolveParametersTag(PrivacyMode.STRICT, cookieParam("PHPSESSID", COOKIE_SENTINEL))

        assertFalse(
            resolved.contains(COOKIE_SENTINEL),
            "a COOKIE-typed parameter value reached the prompt under STRICT: $resolved",
        )
        assertTrue(
            resolved.contains("PHPSESSID=[STRIPPED] (COOKIE)"),
            "the line must keep its existing name=value (TYPE) shape around the marker: $resolved",
        )
    }

    @Test
    fun cookieTypedParameterValueIsStrippedFromTheParametersTagUnderBalanced() {
        val resolved = resolveParametersTag(PrivacyMode.BALANCED, cookieParam("PHPSESSID", COOKIE_BALANCED_SENTINEL))

        assertFalse(
            resolved.contains(COOKIE_BALANCED_SENTINEL),
            "a COOKIE-typed parameter value reached the prompt under BALANCED: $resolved",
        )
    }

    @Test
    fun cookieTypedParameterValuePassesThroughTheParametersTagUnderOff() {
        // The policy control. Pass-through is asserted under a NON-redacting policy only — a green
        // assertion of survival under STRICT is the artifact 26-SECURITY.md exists to stop producing.
        val resolved = resolveParametersTag(PrivacyMode.OFF, cookieParam("PHPSESSID", COOKIE_OFF_SENTINEL))

        assertTrue(
            resolved.contains("PHPSESSID=$COOKIE_OFF_SENTINEL (COOKIE)"),
            "OFF must leave the value untouched, or the two probes above prove nothing: $resolved",
        )
    }

    @Test
    fun theExistingSensitiveNameFilterAndPassThroughAreBothUnchanged() {
        // This task ADDS a control; it does not replace the one that was there. A non-cookie
        // parameter whose NAME matches `sensitiveParamName` still reads [REDACTED], and one that
        // matches nothing still carries its value.
        val resolved =
            resolveParametersTag(
                PrivacyMode.STRICT,
                paramOfType(HttpParameterType.URL, "auth_token", NAME_FILTER_SENTINEL),
                paramOfType(HttpParameterType.URL, "wibble", PASS_THROUGH_SENTINEL),
            )

        assertFalse(
            resolved.contains(NAME_FILTER_SENTINEL),
            "the pre-existing sensitiveParamName filter stopped firing — this task narrowed a control " +
                "instead of adding one: $resolved",
        )
        assertTrue(resolved.contains("auth_token=[REDACTED] (URL)"), "the name filter's marker must be unchanged: $resolved")
        assertTrue(
            resolved.contains("wibble=$PASS_THROUGH_SENTINEL (URL)"),
            "a URL-typed parameter matching neither control must keep its value (D-27-20): $resolved",
        )
    }

    private fun resolveParametersTag(
        mode: PrivacyMode,
        vararg parameters: ParsedHttpParameter,
    ): String {
        val request = mock<HttpRequest>()
        whenever(request.toString()).thenReturn("GET /a HTTP/1.1\nHost: example.com\n\n")
        whenever(request.method()).thenReturn("GET")
        whenever(request.url()).thenReturn("https://example.com/a")
        whenever(request.parameters()).thenReturn(parameters.toList())

        val rr = mock<HttpRequestResponse>()
        whenever(rr.request()).thenReturn(request)
        whenever(rr.response()).thenReturn(null)

        val promptText = "[HTTP_Requests_Parameters]"
        val definition =
            BountyPromptDefinition(
                id = "Parameter_Carrier_Probe",
                title = "Parameter Carrier Probe",
                category = BountyPromptCategory.DETECTION,
                outputType = BountyPromptOutputType.ISSUE,
                systemPrompt = "System",
                userPrompt = promptText,
                severity = "Information",
                confidence = BountyPromptConfidence.TENTATIVE,
                tagsUsed = BountyPromptTag.extractFrom(promptText),
            )

        return BountyPromptTagResolver()
            .resolve(
                definition = definition,
                requestResponses = listOf(rr),
                options = ContextOptions(privacyMode = mode, deterministic = true, hostSalt = "carrier-salt"),
            ).resolvedUserPrompt
    }

    private fun cookieParam(
        name: String,
        value: String,
    ): ParsedHttpParameter = paramOfType(HttpParameterType.COOKIE, name, value)

    private fun paramOfType(
        type: HttpParameterType,
        name: String,
        value: String,
    ): ParsedHttpParameter {
        val param = mock<ParsedHttpParameter>()
        whenever(param.type()).thenReturn(type)
        whenever(param.name()).thenReturn(name)
        whenever(param.value()).thenReturn(value)
        return param
    }

    private companion object {
        // Bare lowercase words: no `=` inside the value, no Bearer/Basic/JWT shape, no sensitive-key
        // token, so no other rule can claim them and a pass is attributable to the control under test.
        const val COOKIE_SENTINEL = "bountyalfa"
        const val COOKIE_BALANCED_SENTINEL = "bountybravo"
        const val COOKIE_OFF_SENTINEL = "bountycharlie"
        const val NAME_FILTER_SENTINEL = "bountydelta"
        const val PASS_THROUGH_SENTINEL = "bountyecho"
    }
}
