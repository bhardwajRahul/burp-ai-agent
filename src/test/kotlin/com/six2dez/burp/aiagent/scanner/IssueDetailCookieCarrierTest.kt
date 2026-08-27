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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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

    /**
     * The fixture-shape guard. A sentinel sitting at the very END of the serialized blob would let
     * every stripping assertion above pass whether or not the value's TAIL is handled, because
     * nothing follows it to be damaged. Phase 27 round 4 shipped a regression through exactly that
     * shape, so the shape itself is asserted rather than assumed.
     */
    @Test
    @Suppress("ktlint:standard:function-naming")
    fun theSentinelIsNotTheTailOfTheSerializedBlob_nonVacuity() {
        // OFF is the mode in which the sentinel is present BY DESIGN, so it is the only mode in
        // which the fixture's shape can be measured at all.
        val output = redactedBlobFor(cookiePoint(), PrivacyMode.OFF)
        val sentinelIndex = output.indexOf(DETAIL_SENTINEL)

        assertTrue(
            sentinelIndex >= 0,
            "NON-VACUITY: the sentinel '$DETAIL_SENTINEL' must be PRESENT in the OFF blob before " +
                "its position can be measured at all. It was absent, so this guard would otherwise " +
                "pass vacuously over an empty tail.",
        )

        val tail = output.substring(sentinelIndex + DETAIL_SENTINEL.length)

        assertTrue(
            tail.isNotEmpty() &&
                tail.contains(METADATA_SECTION_MARKER) &&
                tail.contains(POSITIVE_CONTROL_HEADER_BEFORE),
            "NON-VACUITY: the fixture has degenerated into the round-4 vacuity shape. The sentinel " +
                "is at or near the TAIL of the serialized blob, so nothing follows it to be damaged " +
                "and every stripping assertion in this class is therefore UNINFORMATIVE about the " +
                "value's tail — the region a too-greedy control eats. The tail after the sentinel " +
                "was ${tail.length} chars; it must reach BOTH the metadata marker " +
                "'$METADATA_SECTION_MARKER' (found: ${tail.contains(METADATA_SECTION_MARKER)}) and " +
                "the positive-control header '$POSITIVE_CONTROL_HEADER_BEFORE' (found: " +
                "${tail.contains(POSITIVE_CONTROL_HEADER_BEFORE)}). Restore content after the " +
                "sentinel; do not relax this assertion.",
        )
    }

    /**
     * THE CONTENT-DESTRUCTION GUARD. A leak-only assertion cannot see an over-match that ate
     * content PAST its intended span — the previous round's shipped regression was exactly that —
     * so this asserts an EQUALITY under one known substitution rather than an absence.
     *
     * SCOPED TO THE `detail` FIELD, NOT TO THE WHOLE BLOB, and the scoping is load-bearing. Task 1
     * deliberately places the positive-control `Cookie:` header inside `requestResponses[0].request`
     * of the SAME serialized object, and [theCookieHeaderPositiveControlFiresInTheSameStrictOutput]
     * REQUIRES that header to be stripped under STRICT. That stripped header sits AFTER the detail
     * sentinel in the same serialized string, so the OFF blob's tail is provably NOT present in the
     * STRICT blob and a whole-blob form of this guard would be red on arrival for a SPECIFICATION
     * reason rather than a code reason. The cheapest repair for a guard that is red on arrival is to
     * relax it until it no longer detects an over-match, which is how round 4 shipped its
     * regression. Scope it; do not weaken it; do not delete it.
     */
    @Test
    fun theStrippedDetailFieldRetainsEverythingAfterTheControlPoint() {
        val offDetail = detailFieldOf(redactedBlobFor(cookiePoint(), PrivacyMode.OFF))
        val strictDetail = detailFieldOf(redactedBlobFor(cookiePoint(), PrivacyMode.STRICT))

        // EXTRACTOR PIN, BEFORE ANY COMPARISON. An extractor that silently returned "" would make
        // the equality below compare two empty strings and pass while measuring nothing at all.
        assertTrue(
            offDetail.isNotBlank(),
            "EXTRACTOR: the OFF-mode `detail` field extracted from the redacted blob was blank. The " +
                "guard below would compare two empty strings and pass while measuring nothing.",
        )
        assertTrue(
            offDetail.contains(DETAIL_SENTINEL),
            "EXTRACTOR: the OFF-mode `detail` field must contain the sentinel '$DETAIL_SENTINEL'. " +
                "It did not, so the extractor is reading the wrong field and the guard below is " +
                "vacuous. Extracted: '$offDetail'",
        )

        val expected = offDetail.replace(DETAIL_SENTINEL, ScannerIssueSupport.INJECTION_VALUE_STRIPPED_MARKER)

        assertEquals(expected, strictDetail, contentDestructionMessage(expected, strictDetail))
    }

    /**
     * At the WHOLE-blob level the STRICT and OFF outputs differ in exactly TWO places, and both are
     * named here rather than tolerated as a diff of unknown shape. A third difference is a finding.
     */
    @Test
    fun theOnlyTwoDifferencesBetweenStrictAndOffAreTheEnumeratedControls() {
        val offBlob = redactedBlobFor(cookiePoint(), PrivacyMode.OFF)
        val strictBlob = redactedBlobFor(cookiePoint(), PrivacyMode.STRICT)
        val strictDetail = detailFieldOf(strictBlob)
        val strictRequest = requestFieldOf(strictBlob)

        // DIFFERENCE 1, inside `detail` — THIS plan's control.
        assertTrue(
            detailFieldOf(offBlob).contains(DETAIL_SENTINEL) &&
                strictDetail.contains(ScannerIssueSupport.INJECTION_VALUE_STRIPPED_MARKER) &&
                !strictDetail.contains(DETAIL_SENTINEL),
            "DIFFERENCE 1 — THIS PLAN'S CONTROL, inside `detail` — did not occur as specified. The " +
                "sentinel '$DETAIL_SENTINEL' must be present in the OFF `detail` and replaced by " +
                "'${ScannerIssueSupport.INJECTION_VALUE_STRIPPED_MARKER}' in the STRICT `detail`. " +
                "The new control is the side that moved.",
        )

        // DIFFERENCE 2, inside requestResponses[0].request — the PRE-EXISTING header rule. Not this
        // plan's control; its firing is what makes the null result inside `detail` attributable.
        assertTrue(
            requestFieldOf(offBlob).contains(POSITIVE_CONTROL_HEADER_BEFORE) &&
                strictRequest.contains(POSITIVE_CONTROL_HEADER_AFTER) &&
                !strictRequest.contains(POSITIVE_CONTROL_COOKIE_VALUE),
            "DIFFERENCE 2 — THE PRE-EXISTING HEADER RULE, inside requestResponses[0].request — did " +
                "not occur as specified. '$POSITIVE_CONTROL_HEADER_BEFORE' must be present in the " +
                "OFF request and rewritten to '$POSITIVE_CONTROL_HEADER_AFTER' in the STRICT " +
                "request. The PRE-EXISTING rule is the side that moved, not this plan's control.",
        )

        val predicted =
            offBlob
                .replace(DETAIL_SENTINEL, ScannerIssueSupport.INJECTION_VALUE_STRIPPED_MARKER)
                .replace(POSITIVE_CONTROL_HEADER_BEFORE, POSITIVE_CONTROL_HEADER_AFTER)

        assertEquals(predicted, strictBlob, thirdDifferenceMessage(predicted, strictBlob))
    }

    /**
     * Standing rule (vi): where a number is source-derivable a test must DERIVE it. The two prose
     * counts that drifted last round drifted inside a single commit.
     */
    @Test
    fun theOriginalValueBoundIsDerivedFromTheConstant() {
        // A NON-cookie type on purpose: the bound is the PASS-THROUGH branch's behaviour, and
        // reading it on a cookie point under a stripping mode would measure the marker instead.
        val overshoot = ScannerIssueSupport.ORIGINAL_VALUE_MAX_CHARS + BOUND_OVERSHOOT_CHARS
        val filler = FILLER_UNIT.repeat(overshoot / FILLER_UNIT.length + 1).take(overshoot)
        val point = InjectionPoint(InjectionType.URL_PARAM, COOKIE_POINT_NAME, filler)

        val rendered = originalValueRenderedFor(point, PrivacyMode.OFF)

        assertEquals(
            ScannerIssueSupport.ORIGINAL_VALUE_MAX_CHARS,
            rendered.length,
            "BOUND: the Original Value line's value part must be truncated to exactly " +
                "ScannerIssueSupport.ORIGINAL_VALUE_MAX_CHARS " +
                "(${ScannerIssueSupport.ORIGINAL_VALUE_MAX_CHARS}) characters. The input was " +
                "$overshoot characters and rendered as ${rendered.length}. This expectation is READ " +
                "FROM the constant and must never be restated as a literal.",
        )
    }

    /**
     * The marker must not be run through the truncation bound and emerge as a fragment. A truncated
     * marker still reads as "stripped" to a human while defeating every assertion that matches the
     * whole marker.
     */
    @Test
    fun theStrippedMarkerIsNotTruncated() {
        val rendered = originalValueRenderedFor(cookiePoint(), PrivacyMode.STRICT)

        assertEquals(
            ScannerIssueSupport.INJECTION_VALUE_STRIPPED_MARKER,
            rendered,
            "MARKER INTEGRITY: the COOKIE carrier's Original Value line must render the stripped " +
                "marker EXACTLY, not a prefix of it. Rendered: '$rendered'.",
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

        /** The cookie NAME carrying [POSITIVE_CONTROL_COOKIE_VALUE] inside [RAW_REQUEST]. */
        const val POSITIVE_CONTROL_COOKIE_NAME = "wibble"

        /**
         * The marker the PRE-EXISTING header rule writes over a stripped `Cookie:` header.
         *
         * Deliberately NOT read from `ScannerIssueSupport.INJECTION_VALUE_STRIPPED_MARKER`. The two
         * are independently owned — one belongs to this plan's control inside `detail`, the other to
         * a rule that predates this phase — and they only HAPPEN to share the literal today. Reading
         * one from the other would make a future divergence in either of them invisible here, and
         * this file's whole-blob difference enumeration depends on telling the two apart.
         */
        const val HEADER_RULE_STRIPPED_MARKER = "[STRIPPED]"

        /** The positive control's header line as WRITTEN, before any rule fires. */
        const val POSITIVE_CONTROL_HEADER_BEFORE =
            "Cookie: $POSITIVE_CONTROL_COOKIE_NAME=$POSITIVE_CONTROL_COOKIE_VALUE"

        /** The positive control's header line as the PRE-EXISTING header rule rewrites it. */
        const val POSITIVE_CONTROL_HEADER_AFTER = "Cookie: $HEADER_RULE_STRIPPED_MARKER"

        /**
         * First line of the metadata section, used as the non-vacuity tail marker. Low-entropy, no
         * dots, no digits: nothing another redaction rule can plausibly claim, so its presence in
         * the tail measures the FIXTURE's shape rather than some rule's behaviour.
         */
        const val METADATA_SECTION_MARKER = "Backend: test-backend"

        /** The rendered prefix of the detail line this control owns. */
        const val ORIGINAL_VALUE_PREFIX = "Original Value: "

        /** How far past [ScannerIssueSupport.ORIGINAL_VALUE_MAX_CHARS] the bound fixture overshoots. */
        const val BOUND_OVERSHOOT_CHARS = 25

        /** Repeating filler for the bound fixture. No digits, no dots, no metacharacters. */
        const val FILLER_UNIT = "filler-"

        /** Half-width of the context window printed either side of a divergence in a failure message. */
        const val DIFF_WINDOW_CHARS = 60

        val METADATA_SECTION = "$METADATA_SECTION_MARKER\r\nScan: Active\r\nConfidence: 90"

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
                "$POSITIVE_CONTROL_HEADER_BEFORE\r\n" +
                "Accept: text/html\r\n\r\n"

        fun cookiePoint() = InjectionPoint(InjectionType.COOKIE, COOKIE_POINT_NAME, DETAIL_SENTINEL)

        fun urlParamPoint() = InjectionPoint(InjectionType.URL_PARAM, COOKIE_POINT_NAME, DETAIL_SENTINEL)

        fun detailLinesFor(
            point: InjectionPoint,
            mode: PrivacyMode,
        ): List<String> =
            ScannerIssueSupport.buildActiveIssueDetailLines(
                point,
                VulnClass.SQLI.name,
                PAYLOAD,
                "evidence-marker-present",
                METADATA_SECTION,
                RedactionPolicy.fromMode(mode),
            )

        fun issueDetailsFor(
            point: InjectionPoint,
            mode: PrivacyMode,
        ): IssueDetails {
            val lines = detailLinesFor(point, mode)
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

        /**
         * The rendered value part of the one `Original Value: ` line, read from the detail LINES
         * rather than from the serialized blob so the bound and the marker can be measured without
         * a redaction pass standing between the assertion and the thing it measures.
         */
        fun originalValueRenderedFor(
            point: InjectionPoint,
            mode: PrivacyMode,
        ): String {
            val matching = detailLinesFor(point, mode).filter { it.contains(ORIGINAL_VALUE_PREFIX) }
            assertEquals(
                1,
                matching.size,
                "SINGLE PRODUCER: exactly one detail line may carry the '$ORIGINAL_VALUE_PREFIX' " +
                    "prefix. Found ${matching.size}: $matching. A second producer is how this " +
                    "control gets bypassed without anyone editing it.",
            )
            return matching[0].substringAfter(ORIGINAL_VALUE_PREFIX)
        }

        /**
         * Extracts the `detail` FIELD from a redacted blob.
         *
         * FIELD-SCOPED ON PURPOSE, and the whole reason the content-destruction guard can exist at
         * all: a WHOLE-BLOB form of that guard is false by construction on this fixture, because the
         * positive-control `Cookie:` header inside `requestResponses` is legitimately stripped by a
         * pre-existing rule in the same STRICT output and sits AFTER the sentinel in the same
         * serialized string.
         */
        fun detailFieldOf(blob: String): String {
            val viaParse = runCatching { parsedOrNull(blob)?.get("detail")?.jsonPrimitive?.content }.getOrNull()
            return viaParse ?: fallbackStringField(blob, "detail")
        }

        /** Extracts `requestResponses[0].request` from a redacted blob. */
        fun requestFieldOf(blob: String): String {
            val viaParse =
                runCatching {
                    parsedOrNull(blob)
                        ?.get("requestResponses")
                        ?.jsonArray
                        ?.get(0)
                        ?.jsonObject
                        ?.get("request")
                        ?.jsonPrimitive
                        ?.content
                }.getOrNull()
            return viaParse ?: fallbackStringField(blob, "request")
        }

        fun parsedOrNull(blob: String): JsonObject? = runCatching { Json.parseToJsonElement(blob).jsonObject }.getOrNull()

        /**
         * Substring fallback for the case where a redaction rule produced output that no longer
         * parses as JSON. A guard that silently vanished in that case would be worse than one that
         * degrades: this returns the field's ESCAPED text rather than its decoded value, which is
         * consistent for every caller because the fallback either fires for all of them or none.
         */
        fun fallbackStringField(
            blob: String,
            key: String,
        ): String {
            val marker = "\"$key\":\""
            val start = blob.indexOf(marker)
            assertTrue(
                start >= 0,
                "EXTRACTOR: neither a JSON parse nor a substring scan located the `$key` field in " +
                    "the redacted blob. Every guard downstream of this call measures nothing.",
            )
            val valueStart = start + marker.length
            var index = valueStart
            while (index < blob.length) {
                if (blob[index] == '"' && trailingBackslashCount(blob, index) % 2 == 0) {
                    return blob.substring(valueStart, index)
                }
                index++
            }
            assertTrue(
                false,
                "EXTRACTOR: the `$key` field's opening quote was found but its closing quote was " +
                    "not. The redacted blob is truncated or malformed.",
            )
            return ""
        }

        fun trailingBackslashCount(
            text: String,
            index: Int,
        ): Int {
            var count = 0
            var cursor = index - 1
            while (cursor >= 0 && text[cursor] == '\\') {
                count++
                cursor--
            }
            return count
        }

        fun windowAround(
            text: String,
            index: Int,
        ): String {
            val start = maxOf(0, index - DIFF_WINDOW_CHARS)
            val end = minOf(text.length, index + DIFF_WINDOW_CHARS)
            return if (start >= end) "<empty>" else text.substring(start, end)
        }

        fun contentDestructionMessage(
            expected: String,
            actual: String,
        ): String {
            val divergence = expected.commonPrefixWith(actual).length
            return "CONTENT DESTRUCTION, FIELD-SCOPED to `detail`: the STRICT `detail` must equal " +
                "the OFF `detail` under EXACTLY ONE substitution — the sentinel became " +
                "'${ScannerIssueSupport.INJECTION_VALUE_STRIPPED_MARKER}' — and under no other. " +
                "They first diverge at index $divergence.\n" +
                "  expected: ...${windowAround(expected, divergence)}...\n" +
                "  actual:   ...${windowAround(actual, divergence)}...\n" +
                "The divergence is EITHER a real over-match that ate content past the control's " +
                "span, OR a second redaction rule firing on a fixture token. Diagnose WHICH and " +
                "record it in 28-01-SUMMARY.md. Relaxing this assertion is not one of the two " +
                "options: relaxing a guard that was red on arrival is exactly how phase 27 round 4 " +
                "shipped a content-destruction regression."
        }

        fun thirdDifferenceMessage(
            predicted: String,
            observed: String,
        ): String {
            val divergence = predicted.commonPrefixWith(observed).length
            return "A THIRD, UNENUMERATED DIFFERENCE between the OFF and STRICT blobs was found. " +
                "Applying the two KNOWN substitutions to the OFF blob — the sentinel becomes " +
                "'${ScannerIssueSupport.INJECTION_VALUE_STRIPPED_MARKER}' inside `detail`, and " +
                "'$POSITIVE_CONTROL_HEADER_BEFORE' becomes '$POSITIVE_CONTROL_HEADER_AFTER' inside " +
                "requestResponses — must reproduce the STRICT blob EXACTLY. A residue remains, " +
                "first differing at index $divergence.\n" +
                "  predicted: ...${windowAround(predicted, divergence)}...\n" +
                "  observed:  ...${windowAround(observed, divergence)}...\n" +
                "An unenumerated difference in this blob is either an over-match or a rule nobody " +
                "accounted for. Both are FINDINGS, not fixture noise. Diagnose which, and do not " +
                "widen the substitution list to absorb it."
        }
    }
}
