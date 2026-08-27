package com.six2dez.burp.aiagent.scanner

import com.six2dez.burp.aiagent.mcp.schema.AuditIssueConfidence
import com.six2dez.burp.aiagent.mcp.schema.AuditIssueDefinition
import com.six2dez.burp.aiagent.mcp.schema.AuditIssueSeverity
import com.six2dez.burp.aiagent.mcp.schema.HttpRequestResponse
import com.six2dez.burp.aiagent.mcp.schema.HttpService
import com.six2dez.burp.aiagent.mcp.schema.IssueDetails
import com.six2dez.burp.aiagent.mcp.tools.toolJson
import com.six2dez.burp.aiagent.redact.PrivacyMode
import com.six2dez.burp.aiagent.redact.Redaction
import com.six2dez.burp.aiagent.redact.RedactionPolicy
import com.six2dez.burp.aiagent.util.IssueUtils
import kotlinx.serialization.encodeToString
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File

/**
 * (PRIV-05) Phase 28 plan 28-01 — `AR-27-08`, the issue-detail cookie carrier.
 *
 * WHAT THIS FILE MEASURES. A COOKIE-typed [InjectionPoint]'s `originalValue` is written into
 * `AuditIssue.detail()` by `ActiveAiScanner.createConfirmedIssue`, and leaves the product over MCP
 * as `IssueDetails.detail` (`mcp/schema/Serialization.kt:14`, the `scanner_issues` tool result).
 * Phase 27 MEASURED that route and applied no control: the disposition was TRANSFER, not mitigate.
 * These tests are the control's proof, and they carry their own attribution control, their own
 * positive control and their own non-vacuity guard so that a green result here cannot be produced
 * by some other rule.
 *
 * WHY THE MEASURED ROUTE IS BLIND TO THE REDACTOR (from 27-08-SUMMARY.md, not re-derived here).
 * `IssueUtils.formatIssueDetailHtml` joins the detail lines with `<br>`, so the blob carries NO
 * newline and the logical-line cookie rules have nothing to bind to. The rendered shape is
 * `Original Value: <value>`, not `name=<value> (COOKIE)`, so `Redaction.cookieTypedParamRegex`
 * cannot key on it either. The enclosing JSON key is `detail`, which is absent from
 * `SENSITIVE_WORDS`. The control therefore has to go at the WRITE site, keyed on the
 * [InjectionType] enum — the type-keyed discipline `McpToolHelpers.sanitizeParameters` states for
 * the sibling parameter carrier — because the write site is the last point in the route that still
 * holds the type at all.
 *
 * MOCKS-FREE BY CONSTRUCTION. Nothing here touches Montoya. The fixtures are built from the REAL
 * serializer types plus `toolJson`, never from a hand-typed escaped JSON envelope, so if the
 * emitted shape changes these tests move with it instead of quietly asserting against a shape that
 * is no longer emitted.
 *
 * SENTINEL DISCIPLINE. Both sentinels are low-entropy hyphenated lowercase words: no digits, no
 * `=`, no `.`, no `Bearer `/`Basic ` prefix, no regex metacharacter. So no entropy rule, no token
 * rule, no secret-key rule and no parameter rule can plausibly claim either of them. That claim is
 * not left as prose — [urlParamOriginalValueSurvivesStrict_attributionControl] PROVES it by
 * carrying the IDENTICAL sentinel on a URL_PARAM-typed point through STRICT and asserting it
 * survives. If some unrelated rule were doing the removal, that test would go red with the others.
 *
 * THE TWO SENTINELS MUST BE DIFFERENT STRINGS, not merely different variables.
 * [POSITIVE_CONTROL_COOKIE_VALUE] and [DETAIL_SENTINEL] are checked distinct and non-overlapping
 * in [BeforeEach], before any test body runs. This is load-bearing and the collision it prevents is
 * INVISIBLE IN A DIFF: `ScannerIssueSupport.INJECTION_VALUE_STRIPPED_MARKER` and the header rule's
 * marker are BOTH the literal `[STRIPPED]`, so a shared sentinel would let one substitution rewrite
 * both occurrences and leave the whole-blob difference enumeration with nothing to attribute.
 *
 * ## THE DESIGNATED RED PROBE — read this before mutating the control
 *
 * If you weaken `ScannerIssueSupport.sanitizeInjectionPointValue`'s cookie branch, the assertion
 * that is SUPPOSED to catch you is the first `assertFalse` in
 * [cookieOriginalValueIsStrippedUnderStrict]. It is the designated red probe for this control.
 * [cookieOriginalValueIsStrippedUnderBalanced] and [cookieOriginalValueSurvivesUnderOff] go red
 * alongside it and are not redundant: the OFF one catches the opposite mistake, a control that
 * fires unconditionally. MEASURED 2026-08-27 by negating the branch condition — exactly those three
 * went red, 3 of 8, and the other five stayed green because none of them reads the COOKIE carrier's
 * value under a stripping mode.
 *
 * WHAT THIS FILE CANNOT CATCH, stated so a reader does not over-read a green run. Every test here
 * calls `ScannerIssueSupport.buildActiveIssueDetailLines` DIRECTLY. Nothing here executes
 * `ActiveAiScanner.createConfirmedIssue`, so no assertion proves the operator's configured privacy
 * mode actually reaches the gate. MEASURED: hard-coding that call site's policy argument to
 * `RedactionPolicy.fromMode(PrivacyMode.OFF)` left all 8 tests GREEN. [theWriteSiteReadsTheLivePolicy]
 * is the source-TEXT pin standing in for that missing coverage, and it is weaker than an execution
 * assertion in a way the residual section of `28-01-SUMMARY.md` names explicitly.
 */
class IssueDetailCookieCarrierTest {
    @BeforeEach
    fun clearCustomPatternsAndCheckSentinels() {
        // Redaction is a singleton object: custom patterns left behind by another test class in the
        // same JVM would bleed in here and could remove a sentinel for the wrong reason, letting an
        // absence assertion pass without the new control doing anything. Precedent:
        // SerializedEmissionRedactionTest.
        Redaction.setCustomPatterns(emptyList())
        assertSentinelsAreDistinctAndNonOverlapping()
    }

    @Test
    fun sentinelsAreDistinctAndNonOverlapping() {
        // Named as a test as well as run from @BeforeEach, so the guard is visible in a test report
        // rather than only in a setup method a reader has to go looking for.
        assertSentinelsAreDistinctAndNonOverlapping()
    }

    @Test
    fun cookieOriginalValueIsStrippedUnderStrict() {
        val output = redactedBlobFor(cookiePoint(), PrivacyMode.STRICT)

        assertFalse(
            output.contains(DETAIL_SENTINEL),
            "STRICT: the COOKIE-typed injection point's originalValue must be ABSENT from the " +
                "serialized issue detail, but the sentinel '$DETAIL_SENTINEL' was present.",
        )
        assertTrue(
            output.contains("Original Value: ${ScannerIssueSupport.INJECTION_VALUE_STRIPPED_MARKER}"),
            "STRICT: the COOKIE carrier's Original Value line must render the stripped marker " +
                "'${ScannerIssueSupport.INJECTION_VALUE_STRIPPED_MARKER}', but it did not.",
        )
    }

    @Test
    fun cookieOriginalValueIsStrippedUnderBalanced() {
        val output = redactedBlobFor(cookiePoint(), PrivacyMode.BALANCED)

        assertFalse(
            output.contains(DETAIL_SENTINEL),
            "BALANCED: the COOKIE-typed injection point's originalValue must be ABSENT from the " +
                "serialized issue detail, but the sentinel '$DETAIL_SENTINEL' was present.",
        )
        assertTrue(
            output.contains("Original Value: ${ScannerIssueSupport.INJECTION_VALUE_STRIPPED_MARKER}"),
            "BALANCED: the COOKIE carrier's Original Value line must render the stripped marker " +
                "'${ScannerIssueSupport.INJECTION_VALUE_STRIPPED_MARKER}', but it did not.",
        )
    }

    @Test
    fun cookieOriginalValueSurvivesUnderOff() {
        val output = redactedBlobFor(cookiePoint(), PrivacyMode.OFF)

        assertTrue(
            output.contains(DETAIL_SENTINEL),
            "OFF: the COOKIE-typed injection point's originalValue must be PRESENT verbatim in the " +
                "serialized issue detail, but the sentinel '$DETAIL_SENTINEL' was missing — the " +
                "control is policy-driven, not an unconditional rewrite.",
        )
        assertFalse(
            output.contains(ScannerIssueSupport.INJECTION_VALUE_STRIPPED_MARKER),
            "OFF: the stripped marker must NOT appear anywhere in the blob; its presence would mean " +
                "some control fired in the mode that is defined as applying none.",
        )

        // NON-VACUITY. The sentinel must not be the last token of the blob: content has to follow it
        // so the value's tail — the region a too-greedy control eats — is genuinely exercised. This
        // is the vacuity class that let a content-destruction regression ship in phase 27 round 4.
        val tail = output.substringAfter(DETAIL_SENTINEL)
        assertTrue(
            tail.isNotEmpty() && tail.contains("requestResponses"),
            "OFF: content must follow the sentinel in the serialized blob (non-vacuity), but the " +
                "tail after '$DETAIL_SENTINEL' was ${tail.length} chars and did not reach " +
                "requestResponses.",
        )
    }

    @Test
    @Suppress("ktlint:standard:function-naming")
    fun urlParamOriginalValueSurvivesStrict_attributionControl() {
        val output = redactedBlobFor(urlParamPoint(), PrivacyMode.STRICT)

        assertTrue(
            output.contains(DETAIL_SENTINEL),
            "STRICT / ATTRIBUTION: the IDENTICAL sentinel carried by a URL_PARAM-typed injection " +
                "point must SURVIVE, proving the COOKIE result is caused by the new type gate and " +
                "not by an entropy, token or sensitive-key rule that happens to match " +
                "'$DETAIL_SENTINEL'. It was absent, so the COOKIE assertions above are not " +
                "attributable to this control.",
        )
    }

    @Test
    fun theCookieHeaderPositiveControlFiresInTheSameStrictOutput() {
        val output = redactedBlobFor(cookiePoint(), PrivacyMode.STRICT)

        assertFalse(
            output.contains(POSITIVE_CONTROL_COOKIE_VALUE),
            "STRICT / POSITIVE CONTROL: the real Cookie: header value inside requestResponses[0] " +
                "must be stripped in the SAME output, but '$POSITIVE_CONTROL_COOKIE_VALUE' was " +
                "present — the pre-existing header rule did not fire, so this output proves nothing.",
        )
        assertTrue(
            output.contains("Cookie: [STRIPPED]"),
            "STRICT / POSITIVE CONTROL: the pre-existing cookie header rule must render " +
                "'Cookie: [STRIPPED]' in the SAME output, reproducing 27-08 measurement 2's control.",
        )
    }

    @Test
    fun theCookieNameSurvivesEveryMode() {
        PrivacyMode.entries.forEach { mode ->
            val output = redactedBlobFor(cookiePoint(), mode)
            assertTrue(
                output.contains(COOKIE_POINT_NAME),
                "$mode: the cookie NAME on the Injection Point line must survive — the requirement " +
                    "forbids values, not names — but '$COOKIE_POINT_NAME' was absent.",
            )
        }
    }

    @Test
    fun theRequestResponsesListIsNotAlteredByTheControl() {
        // D-28-01's accepted trade, converted from a claim in prose into a checked invariant: the
        // operator keeps the raw value byte-for-byte in Burp's own request pane, because the control
        // rewrites the OUTBOUND rendering only and never touches the AuditIssue's requestResponses.
        // Read BEFORE serialization on purpose — this is the local-evidence question, not the
        // emission question.
        val details = issueDetailsFor(cookiePoint(), PrivacyMode.STRICT)
        val request = details.requestResponses[0].request

        assertTrue(
            request != null && request.contains(POSITIVE_CONTROL_COOKIE_VALUE),
            "LOCAL EVIDENCE: the AuditIssue's requestResponses entry must still carry " +
                "'$POSITIVE_CONTROL_COOKIE_VALUE' byte-for-byte before serialization, so the " +
                "operator retains the raw value in Burp's UI.",
        )
    }

    /**
     * The source-TEXT pin standing in for the execution coverage this file does NOT have.
     *
     * WHY IT EXISTS. Mutation B of 28-01's red probe — hard-coding the write site's policy argument
     * to a constant instead of reading the operator's setting — was MEASURED as undetected: all 8
     * behavioural tests stayed green. That is a real gap in this file's reach, not a hypothetical.
     *
     * WHAT IT CANNOT SEE, stated rather than left for a reader to discover. This asserts over source
     * TEXT. A refactor that keeps the literal `RedactionPolicy.fromMode(getSettings().privacyMode)`
     * on the page while routing a different policy object into the call passes this pin unchanged.
     * It is kept anyway because it is non-vacuous TODAY: that file's comment-stripped
     * `RedactionPolicy.fromMode(` count was DERIVED as 0 before this phase, so requiring exactly 1
     * measures a change this phase makes rather than restating a constant.
     */
    @Test
    fun theWriteSiteReadsTheLivePolicy() {
        val executableLines =
            activeScannerSource()
                .lines()
                .filterNot { it.trimStart().startsWith("//") }
                .filterNot { it.trimStart().startsWith("*") }
                .filterNot { it.trimStart().startsWith("/*") }

        val fromModeLines = executableLines.filter { it.contains("RedactionPolicy.fromMode(") }

        assertEquals(
            1,
            fromModeLines.size,
            "PIN: `$ACTIVE_SCANNER_SOURCE_PATH` must contain EXACTLY ONE executable " +
                "`RedactionPolicy.fromMode(` occurrence — the write site's policy lookup. Found " +
                "${fromModeLines.size}: $fromModeLines",
        )
        assertTrue(
            fromModeLines[0].contains("RedactionPolicy.fromMode(getSettings().privacyMode)"),
            "PIN: the write site must derive its policy from the OPERATOR'S LIVE SETTING, not from a " +
                "constant. Expected the argument text `getSettings().privacyMode`, found: " +
                "`${fromModeLines[0].trim()}`",
        )
    }

    private fun activeScannerSource(): String {
        val file = File(ACTIVE_SCANNER_SOURCE_PATH)
        assertTrue(
            file.isFile,
            "Expected `$ACTIVE_SCANNER_SOURCE_PATH` relative to the test working directory " +
                "`${System.getProperty("user.dir")}`, resolved as `${file.absolutePath}`. If the " +
                "build layout changed, fix the path here. Asserted rather than left to surface as a " +
                "bare FileNotFoundException.",
        )
        return file.readText()
    }

    private fun assertSentinelsAreDistinctAndNonOverlapping() {
        assertNotEquals(
            DETAIL_SENTINEL,
            POSITIVE_CONTROL_COOKIE_VALUE,
            "FIXTURE: the positive-control cookie header value and the detail sentinel must be " +
                "DIFFERENT STRINGS. Sharing one makes the whole-blob difference enumeration red on " +
                "arrival for a fixture reason rather than a code reason.",
        )
        assertFalse(
            DETAIL_SENTINEL.contains(POSITIVE_CONTROL_COOKIE_VALUE) ||
                POSITIVE_CONTROL_COOKIE_VALUE.contains(DETAIL_SENTINEL),
            "FIXTURE: neither sentinel may contain the other. A substring relation defeats an " +
                "absence assertion exactly as a duplicate does.",
        )
    }

    private companion object {
        /**
         * The value carried by the injection point and written into the detail blob. Low-entropy
         * hyphenated words: no digits, no `=`, no metacharacters, so only the new type gate can
         * plausibly remove it. Deliberately does NOT contain the token `cookie`, so no
         * header-name rule can bind to it even if a future boundary composer widens.
         */
        const val DETAIL_SENTINEL = "apple-orange-basket-lantern"

        /**
         * The POSITIVE CONTROL: the value of a real `Cookie:` header inside the same object's
         * `requestResponses`. Unrelated to [DETAIL_SENTINEL] by construction — see the class KDoc
         * for why sharing one would be silent.
         */
        const val POSITIVE_CONTROL_COOKIE_VALUE = "harbor-pebble-window-thistle"

        /** The injection point's NAME. Names survive every mode; values do not. */
        const val COOKIE_POINT_NAME = "pumpkin-lantern-name"

        const val HOST_SALT = "phase-28-fixed-salt"

        const val ACTIVE_SCANNER_SOURCE_PATH = "src/main/kotlin/com/six2dez/burp/aiagent/scanner/ActiveAiScanner.kt"

        val PAYLOAD =
            Payload(
                value = "benign-probe-payload",
                vulnClass = VulnClass.SQLI,
                detectionMethod = DetectionMethod.REFLECTION,
                risk = PayloadRisk.SAFE,
                expectedEvidence = "evidence-marker-present",
            )

        val RAW_REQUEST =
            "GET /basket HTTP/1.1\r\n" +
                "Host: shop.example\r\n" +
                "Cookie: wibble=$POSITIVE_CONTROL_COOKIE_VALUE\r\n" +
                "Accept: text/html\r\n\r\n"

        fun cookiePoint() = InjectionPoint(InjectionType.COOKIE, COOKIE_POINT_NAME, DETAIL_SENTINEL)

        fun urlParamPoint() = InjectionPoint(InjectionType.URL_PARAM, COOKIE_POINT_NAME, DETAIL_SENTINEL)

        fun issueDetailsFor(
            point: InjectionPoint,
            mode: PrivacyMode,
        ): IssueDetails {
            val lines =
                ScannerIssueSupport.buildActiveIssueDetailLines(
                    point,
                    VulnClass.SQLI.name,
                    PAYLOAD,
                    "evidence-marker-present",
                    "Backend: test-backend\r\nScan: Active\r\nConfidence: 90",
                    RedactionPolicy.fromMode(mode),
                )
            return IssueDetails(
                name = "[AI Active] ${VulnClass.SQLI.name}",
                detail = IssueUtils.formatIssueDetailHtml(lines),
                remediation = "remediation",
                httpService = HttpService(host = "shop.example", port = 443, secure = true),
                baseUrl = "https://shop.example/basket",
                severity = AuditIssueSeverity.HIGH,
                confidence = AuditIssueConfidence.FIRM,
                requestResponses =
                    listOf(
                        HttpRequestResponse(
                            request = RAW_REQUEST,
                            response = "HTTP/1.1 200 OK\r\n\r\n",
                            notes = null,
                        ),
                    ),
                collaboratorInteractions = emptyList(),
                definition =
                    AuditIssueDefinition(
                        id = "ai_active_confirmed",
                        background = "background",
                        remediation = "remediation",
                        typeIndex = 1,
                    ),
            )
        }

        fun redactedBlobFor(
            point: InjectionPoint,
            mode: PrivacyMode,
        ): String =
            Redaction.apply(
                toolJson.encodeToString(issueDetailsFor(point, mode)),
                RedactionPolicy.fromMode(mode),
                HOST_SALT,
            )
    }
}
