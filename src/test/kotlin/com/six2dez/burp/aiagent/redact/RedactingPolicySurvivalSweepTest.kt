package com.six2dez.burp.aiagent.redact

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * WHAT THIS IS FOR.
 *
 * Plan 27-08 authored a must-have that read: "No green test asserting that a cookie value SURVIVES a
 * redacting policy is committed anywhere under `src/`." Its own repository falsified it. Two green
 * `assertTrue` assertions pinning a fixture hostname as surviving the strongest privacy mode were
 * sitting in `mcp/tools/McpToolHelpersTest.kt` at the time the claim was written, and a third pinned
 * a cookie sentinel as surviving in `redact/CookieHeaderNameParityTest.kt`. Nothing swept, so
 * nothing contradicted the claim.
 *
 * A green test is the most authoritative statement a repository makes about intended behaviour. A
 * green assertion that a sensitive value SURVIVES `PrivacyMode.STRICT` or `PrivacyMode.BALANCED` is
 * therefore a leak documented as a feature — it teaches the next audit that the leak is expected.
 * Plan 27-05 prohibited exactly that artifact, and this phase committed two of them anyway. A fourth
 * prose claim would be the fourth iteration of the same failure. This file is the claim made
 * MECHANICAL: it scans `src/test/kotlin` on every CI run and fails on the next such pin.
 *
 * WHAT THE SCAN CAN SEE — read this before quoting the file as evidence.
 *
 * One shape, and only one: an `assertTrue` whose `.contains(` argument is in the MEASURED sentinel
 * vocabulary of [SENSITIVE_VALUE_VOCABULARY], appearing inside a test function whose body names one
 * of [REDACTING_POLICY_MARKERS].
 *
 * WHAT THE SCAN CANNOT SEE. Eleven axes, named because a tripwire quoted wider than the vocabulary
 * it scans reproduces, one iteration smaller, the register-wider-than-control defect this whole
 * phase exists to repair.
 *
 *  1. A survival pinned with `assertEquals` rather than `assertTrue`.
 *  2. A survival pinned with the `in` operator instead of `.contains(`.
 *  3. A survival pinned with a regex find.
 *  4. A survival pinned with `assertNotNull`.
 *  5. A survival asserted through a helper defined in ANOTHER file, so neither token is in the body.
 *  6. A sensitive value that does not follow the measured naming convention and is not in
 *     [HOST_LITERALS].
 *  7. Anything above the unit suite — an integration run, a manual probe, a shipped log.
 *  8. A survival pin split across TWO functions: the policy marker in one, the assertion in another.
 *     The ISOLATION UNIT IS A WHOLE `fun` BODY, BLANK LINES INCLUDED, from the declaration line to
 *     the first non-blank line at or above its indent (in Kotlin, the closing brace). Function-scoped
 *     isolation is the chosen bound and this is what it costs.
 *
 * The remaining three are the price of the three exclusions built into the detector. Each is stated
 * as a COST, not as a feature:
 *
 *  9. A survival pin whose text lives INSIDE a triple-quoted raw string. The FILE WALK skips those
 *     lines. That skip is not tidiness and it is not a self-file exclusion: EVERY fixture in this
 *     file is a verbatim copy of a real test function and therefore CARRIES ITS OWN `fun`
 *     DECLARATION LINE — that line is exactly what the isolation needs in order to isolate the
 *     fixture at all — and a line-based walk matches such a line inside a triple-quoted literal
 *     precisely as it matches one in code. Holding the fixtures at companion level does NOT prevent
 *     this; a line-based walk reads CONTENTS, not declarations. Without the skip this file flags
 *     ITSELF. The skip applies to every file the walk touches, and it is asserted non-vacuous in
 *     both directions by [theSweepFileItselfYieldsNoHits] and
 *     [theRawStringSkipIsWhyTheSelfScanIsClean].
 * 10. A survival pin positioned textually ABOVE the line that first names the policy. The POSITION
 *     RULE reads those as PRE-REDACTION FIXTURE GUARDS, because nothing has been redacted yet at
 *     that point in the body.
 * 11. A genuinely sensitive value reached through the one accessor named in [BENIGN_ACCESSORS].
 *
 * THE THREE EXCLUSIONS ARE CONSTRUCTED INTO THE DETECTOR, NEVER INTO [ALLOWLIST]. MEASURED at
 * execution time by running this detector over `src/test/kotlin` as shipped: the vocabulary WITHOUT
 * the three exclusions reports 9 hits, every one of them a LEGITIMATE shape — 7 benign-control
 * assertions, 1 pre-redaction fixture guard, 1 negated containment. WITH the three exclusions it
 * reports 0. Run against the PRE-ROUND contents of the two files that carried the pins, WITH the
 * exclusions, it reports EXACTLY 3, and those 3 ARE the two host pins and the underscore pin.
 *
 * The 7 is stated as MEASURED HERE and not as inherited: plan 27-12 projected 5, which was the count
 * before plan 27-11 added two more JSON-string-open probes that each carry a benign-control
 * assertion. The number is written down at what it actually is, because a stated bound wider or
 * narrower than the control it describes is the exact defect this phase exists to repair.
 *
 * Growing [ALLOWLIST] instead, or narrowing the vocabulary, is forbidden: both make an inconvenient
 * hit set empty without making the repository any safer.
 *
 * DURABLE CHECK versus ONE-TIME EVIDENCE, stated so the two are never confused. The in-file fixtures
 * below are the DURABLE check: they run on every CI run and they are what survives this phase. The
 * demonstration that this detector reports exactly three hits against the PRE-ROUND file contents
 * retrieved from git history is EXECUTION-TIME evidence, recorded in `27-12-SUMMARY.md` with its SHA,
 * its command and its raw output. It is NOT re-run by CI, deliberately: a `@Test` that shelled out to
 * git would make CI fail on repository history rather than on the code under test.
 *
 * This file is a TRIPWIRE OVER A MEASURED VOCABULARY, not a proof of coverage. A reader who quotes
 * it as proof of coverage reproduces the defect this phase exists to repair.
 */
class RedactingPolicySurvivalSweepTest {
    // ── the durable claim ─────────────────────────────────────────────────────────────────

    @Test
    fun noGreenTestAssertsASensitiveValueSurvivesARedactingPolicy() {
        val hits = testSourceFiles().flatMap { detect(relativePath(it), fileWalk(it)) }
        val identifiers = hits.map { it.identifier }.toSet()

        assertEquals(
            ALLOWLIST.keys,
            identifiers,
            "the set of tests asserting that a sensitive value SURVIVES a redacting policy has " +
                "changed.\n" +
                "  NEW (delete the assertion, or re-point it at the privacy mode where pass-through " +
                "is the CORRECT behaviour — do NOT add an ALLOWLIST key to make this green): " +
                "${identifiers - ALLOWLIST.keys}\n" +
                "  STALE (allowlisted here but no longer matched — remove the entry so the allowlist " +
                "cannot accumulate dead keys): ${ALLOWLIST.keys - identifiers}\n" +
                "  Full hits: $hits",
        )
    }

    // ── the self-scan, and the counterpart that makes it falsifiable ──────────────────────

    @Test
    fun theSweepFileItselfYieldsNoHits() {
        val self = sourceFile(SELF_PATH)
        val hits = detect(SELF_PATH, fileWalk(self))

        assertTrue(
            hits.isEmpty(),
            "this file flagged ITSELF: $hits. A self-hit means a fixture leaked OUT of a " +
                "triple-quoted literal and into real code. The fix is to move the fixture back into " +
                "the companion as a raw string — NEVER to add a self-file exclusion, which would " +
                "make the detector narrower than its stated scope.",
        )
    }

    @Test
    fun theRawStringSkipIsWhyTheSelfScanIsClean() {
        val self = sourceFile(SELF_PATH)
        val unskipped = detect(SELF_PATH, self.readLines())

        assertTrue(
            unskipped.isNotEmpty(),
            "the detector run over THIS FILE WITHOUT the raw-string skip found nothing. Either the " +
                "skip is doing nothing (and the clean self-scan is an accident), or the detector has " +
                "quietly stopped matching (and every other assertion here is vacuous). A green " +
                "self-scan with no such counterpart is unfalsifiable.",
        )
        assertTrue(
            unskipped.size >= MIN_EXPECTED_UNSKIPPED_SELF_HITS,
            "expected at least $MIN_EXPECTED_UNSKIPPED_SELF_HITS unskipped self-hits from the " +
                "positive fixtures; found ${unskipped.size}: $unskipped",
        )
    }

    // ── the detector is proven live, fixture by fixture ───────────────────────────────────

    @Test
    fun everyVocabularyEntryIsProvenLiveAgainstItsOwnPositiveFixture() {
        assertEquals(
            SENSITIVE_VALUE_VOCABULARY.size,
            VOCABULARY_FIXTURES.size,
            "every vocabulary entry needs its own positive fixture, or an entry that has stopped " +
                "matching can hide in the list and make the whole scan vacuously clean",
        )

        VOCABULARY_FIXTURES.forEachIndexed { index, fixture ->
            val hits = detect(FIXTURE_ID, fixture.lines())
            assertTrue(
                hits.any { it.vocabularyEntry == index },
                "vocabulary entry $index (/${SENSITIVE_VALUE_VOCABULARY[index].pattern}/) matched no " +
                    "hit in its own positive fixture — it can no longer detect the form it exists to " +
                    "detect. Hits found: $hits",
            )
        }
    }

    @Test
    fun theSkipHasNotDisarmedTheDetector() {
        // The file walk SKIPS this text; the detector, handed the SAME text directly, must still
        // flag it. A skip that had silenced the detector everywhere would pass the self-scan above
        // and fail here, which is the entire point of asserting both directions.
        VOCABULARY_FIXTURES.forEach { fixture ->
            assertTrue(
                detect(FIXTURE_ID, fixture.lines()).isNotEmpty(),
                "a positive fixture the file walk skips was NOT flagged when handed straight to the " +
                    "detector: the raw-string skip has disarmed the detector rather than scoped it",
            )
        }
    }

    // ── the two host pins removed this round ──────────────────────────────────────────────

    @Test
    fun theTwoHostPinsRemovedThisRoundAreFlagged() {
        val hits = detect(FIXTURE_ID, HOST_PIN_FIXTURE.lines())

        assertEquals(
            2,
            hits.size,
            "the pre-round copy of the end-to-end test carried exactly TWO survival pins on the " +
                "fixture host — one on the serialized raw-message shape, one on the header-map " +
                "shape. Found: $hits",
        )
        assertTrue(
            hits.all { it.vocabularyEntry == HOST_LITERAL_ENTRY },
            "both hits must come from the host-literal vocabulary entry, not from another entry " +
                "matching by accident. Found: $hits",
        )

        val body = isolatedBodyOf(HOST_PIN_FIXTURE)
        assertTrue(
            body.count { it.isBlank() } >= 2,
            "the isolated body carries ${body.count { it.isBlank() }} blank lines — the marker and " +
                "the pins sit on opposite sides of several, so a blank-line-terminating isolation " +
                "would return a stub containing neither pin",
        )
        assertTrue(
            REDACTING_POLICY_MARKERS.any { marker -> body.any { it.contains(marker) } },
            "the isolated body lost the redacting-policy marker, so the body was truncated",
        )
        assertEquals(
            2,
            body.count { line -> HOST_LITERALS.any { line.contains(it) && line.contains(".contains(") } },
            "the isolated body must still carry BOTH host pins — the two tokens the naive isolation " +
                "separates",
        )
    }

    // ── the blank-line hazard, machine-checked ────────────────────────────────────────────

    @Test
    fun theBlankLineHazardFixtureIsIsolatedWholeIncludingItsBlankLines() {
        val body = isolatedBodyOf(BLANK_LINE_HAZARD_FIXTURE)

        assertTrue(
            body.count { it.isBlank() } >= 2,
            "the isolated body must CONTAIN at least two blank lines, proving the walk CONSUMED " +
                "them rather than stopping at the first. Found ${body.count { it.isBlank() }} in a " +
                "body of ${body.size} lines. If this is 0, the walk was simplified back to a " +
                "blank-line-terminating shape and this whole file is silently narrower than its " +
                "stated bound.",
        )
    }

    @Test
    fun theBlankLineHazardFixtureCarriesBothTokensAndIsFlagged() {
        val body = isolatedBodyOf(BLANK_LINE_HAZARD_FIXTURE)

        assertTrue(
            REDACTING_POLICY_MARKERS.any { marker -> body.any { it.contains(marker) } },
            "the isolated body must contain the redacting-policy marker, which in this fixture sits " +
                "TWO blank lines below the declaration",
        )
        assertTrue(
            body.any { it.contains(".contains(") },
            "the isolated body must contain the flagged containment call, which sits below the " +
                "marker again",
        )
        assertTrue(
            detect(FIXTURE_ID, BLANK_LINE_HAZARD_FIXTURE.lines()).isNotEmpty(),
            "the blank-line hazard fixture must be FLAGGED. It is a verbatim copy of a real survival " +
                "pin this round removed; if it is clean, the detector no longer finds the artifact " +
                "it was written for.",
        )
    }

    // ── the benign exclusion has a floor ──────────────────────────────────────────────────

    @Test
    fun theBenignExclusionCannotSwallowARealSentinel() {
        assertTrue(
            detect(FIXTURE_ID, ANTI_SWALLOW_FIXTURE.lines()).isNotEmpty(),
            "the anti-swallow fixture — a real absence assertion with ONLY its verb flipped, so it " +
                "now asserts a real cookie sentinel SURVIVES — was not flagged. That means the " +
                "benign exclusion is subtracting the enum-accessor FORM rather than the ONE named " +
                "constant it is scoped to, and every enum-accessor survival pin is now invisible.",
        )
        assertTrue(
            detect(FIXTURE_ID, NEGATIVE_ASSERT_FALSE_FIXTURE.lines()).isEmpty(),
            "the same block UNFLIPPED is an absence assertion and must stay clean — the pair is what " +
                "proves the flip, and not something else, is what made it flag",
        )
    }

    // ── the six legitimate shapes that must stay clean ────────────────────────────────────

    @Test
    fun noLegitimateShapeOnThisTreeIsFlagged() {
        NEGATIVE_FIXTURES.forEach { (label, fixture) ->
            assertTrue(
                detect(FIXTURE_ID, fixture.lines()).isEmpty(),
                "the legitimate shape '$label' was FLAGGED. It is taken from the real tree and it is " +
                    "not a survival pin. Fix the detector — do NOT add an ALLOWLIST key, which would " +
                    "silence a shape the detector should never have claimed. Hits: " +
                    "${detect(FIXTURE_ID, fixture.lines())}",
            )
        }
        assertEquals(
            EXPECTED_NEGATIVE_FIXTURES,
            NEGATIVE_FIXTURES.size,
            "the six legitimate shapes measured on this tree are the floor of this test; a fixture " +
                "removed from the list is coverage removed without a decision",
        )
    }

    // ── non-vacuity of the walk itself ────────────────────────────────────────────────────

    @Test
    fun theTreeWalkIsNonVacuous() {
        // A repository-state test that goes green when it cannot find the repository is worse than
        // the grep it replaces. The root resolver throws rather than skips; this asserts the walk it
        // returns actually reached the tree.
        val files = testSourceFiles()

        assertTrue(
            files.size >= MIN_EXPECTED_TEST_FILES,
            "the walk found only ${files.size} .kt files under $TEST_SOURCE_ROOT — the scan is not " +
                "reaching the repository, so its other assertions prove nothing",
        )
        assertTrue(
            files.any { relativePath(it) == SELF_PATH },
            "the walk did not reach THIS file. The self-scan is only meaningful because this file is " +
                "walked like every other; there is no self-exclusion and there must never be one.",
        )
    }

    // ── the file walk: a path becomes lines ───────────────────────────────────────────────

    /**
     * Reads a file and BLANKS every line whose triple-quote state was INSIDE at the START of the
     * line, BEFORE any function-declaration matching happens.
     *
     * Blanked rather than dropped, so line numbering is preserved. The opening `val X =` line and
     * the line carrying the opening `"""` keep their content (their state at line start is OUTSIDE);
     * the literal's contents and its closing line do not.
     *
     * This is the one narrowing that makes the self-scan clean, and it is declared in the class KDoc
     * as blind axis 9 rather than sold as a feature.
     */
    private fun fileWalk(file: File): List<String> = dropRawStringInteriors(file.readLines())

    private fun dropRawStringInteriors(lines: List<String>): List<String> {
        var inside = false
        return lines.map { line ->
            val startedInside = inside
            // A COMMENT LINE NEVER OPENS OR CLOSES A RAW STRING, so it must not toggle the state.
            // This is not tidiness. MEASURED on this very file: the class KDoc above quotes a bare
            // triple quote while explaining what this walk does, which is an ODD toggle, and it
            // INVERTED the state for every line below it — the fixtures read as code and the code
            // read as fixture. The self-scan caught it loudly. The dangerous direction is the other
            // one: an odd toggle in prose can also blank REAL code and make the tree scan miss a
            // real survival pin SILENTLY. The KDoc's triple quote is deliberately left in place, so
            // this rule stays exercised by the file itself rather than by nothing.
            if (!isCommentOnly(line)) {
                var index = 0
                while (index <= line.length - TRIPLE_QUOTE.length) {
                    if (line.regionMatches(index, TRIPLE_QUOTE, 0, TRIPLE_QUOTE.length)) {
                        inside = !inside
                        index += TRIPLE_QUOTE.length
                    } else {
                        index++
                    }
                }
            }
            if (startedInside) "" else line
        }
    }

    // ── the detector: lines become hits ───────────────────────────────────────────────────

    /**
     * Deliberately a SEPARATE entry point from [fileWalk]. The positive-fixture tests drive this
     * function DIRECTLY on the same text the walk skips; the tree scan and the self-scan go through
     * the walk first. That separation is what makes the raw-string skip checkable in both
     * directions.
     */
    private fun detect(
        sourceId: String,
        lines: List<String>,
    ): List<Hit> {
        val hits = mutableListOf<Hit>()
        lines.forEachIndexed { index, line ->
            val declaration = FUNCTION_DECLARATION.find(line) ?: return@forEachIndexed
            // Comment lines are dropped AFTER isolation, never before it: dropping them first would
            // shorten bodies and move where the walk terminates, which is a second way to get a
            // silently truncated body.
            val body = functionBodyAt(lines, index).filterNot { isCommentOnly(it) }
            val normalised = body.joinToString(" ").replace(WHITESPACE_RUN, " ")
            val markerAt =
                REDACTING_POLICY_MARKERS
                    .map { normalised.indexOf(it) }
                    .filter { it >= 0 }
                    .minOrNull() ?: return@forEachIndexed

            hits +=
                candidatesIn(normalised, markerAt).mapNotNull { argument ->
                    vocabularyEntryFor(argument)?.let { entry ->
                        Hit("$sourceId#${declaration.groupValues[2]}", argument, entry)
                    }
                }
        }
        return hits
    }

    /**
     * The `.contains(` arguments in a normalised body that are asserted PRESENT by an `assertTrue`,
     * after the point at which the body first names a redacting policy.
     *
     * Two of the three constructed exclusions live here. THE POSITION RULE: an occurrence before the
     * first policy marker is a PRE-REDACTION FIXTURE GUARD asserting the value is present in the
     * INPUT — which is what makes the later absence assertion non-vacuous — so it is not a candidate.
     * THE NEGATION RULE: `assertTrue(!x.contains(v))` is an ABSENCE assertion wearing the wrong verb.
     */
    private fun candidatesIn(
        normalised: String,
        firstMarkerAt: Int,
    ): List<String> =
        containsOccurrencesIn(normalised)
            // THE POSITION RULE.
            .filter { it >= firstMarkerAt }
            // The `assertTrue` requirement, and THE NEGATION RULE.
            .filter { assertsPresenceAt(normalised, it) }
            .mapNotNull { argumentAt(normalised, it + CONTAINS_CALL.length) }

    /** Every index at which a `.contains(` call opens in the normalised body. */
    private fun containsOccurrencesIn(normalised: String): List<Int> {
        val occurrences = mutableListOf<Int>()
        var from = 0
        while (true) {
            val at = normalised.indexOf(CONTAINS_CALL, from)
            if (at < 0) break
            occurrences += at
            from = at + CONTAINS_CALL.length
        }
        return occurrences
    }

    /**
     * Whether the `.contains(` at [at] is asserted PRESENT: the nearest preceding assertion opener is
     * an `assertTrue(` rather than an `assertFalse(`, and the receiver between that opener and the
     * call is not negated.
     */
    private fun assertsPresenceAt(
        normalised: String,
        at: Int,
    ): Boolean {
        val trueOpener = normalised.lastIndexOf(ASSERT_TRUE, at)
        val falseOpener = normalised.lastIndexOf(ASSERT_FALSE, at)
        val negated =
            trueOpener >= 0 &&
                normalised.substring(trueOpener + ASSERT_TRUE.length, at).trimStart().startsWith("!")
        return trueOpener >= 0 && falseOpener < trueOpener && !negated
    }

    /** The text between a `.contains(` and its MATCHING close parenthesis, found by counting. */
    private fun argumentAt(
        normalised: String,
        start: Int,
    ): String? {
        var depth = 1
        var index = start
        while (index < normalised.length) {
            when (normalised[index]) {
                '(' -> depth++
                ')' -> {
                    depth--
                    if (depth == 0) return normalised.substring(start, index).trim()
                }
            }
            index++
        }
        return null
    }

    /**
     * The vocabulary entry an argument belongs to, or null.
     *
     * The third constructed exclusion lives here. [BENIGN_ACCESSORS] is SUBTRACTED from the
     * enum-accessor form: asserting that the negative-control constant survives is the whole point of
     * a negative control — it proves a pass was not produced by blanket destruction of the payload.
     * It subtracts ONE NAMED CONSTANT and never the form itself, which [ANTI_SWALLOW_FIXTURE]
     * asserts.
     */
    private fun vocabularyEntryFor(argument: String): Int? {
        val index = SENSITIVE_VALUE_VOCABULARY.indexOfFirst { it.matches(argument) }
        val benign = index == ENUM_ACCESSOR_ENTRY && argument.removeSuffix(ACCESSOR_SUFFIX) in BENIGN_ACCESSORS
        return index.takeIf { it >= 0 && !benign }
    }

    // ── the function-body isolation ───────────────────────────────────────────────────────

    /**
     * The declaration line plus its WHOLE body, BLANK LINES INCLUDED, terminating at the first line
     * that is BOTH non-blank AND indented at or above the declaration. In Kotlin that line is the
     * function's own closing brace.
     *
     * Written out rather than reused from `LogicalLineBoundaryScopeTest.declarationBlockOf`, whose
     * `takeWhile` STOPS AT THE FIRST BLANK LINE. That termination is correct for a regex-fragment
     * property, which has no blank lines, and measurably WRONG for a test function body, which is
     * mostly blank-line-separated paragraphs: on all three artifacts this sweep was written to find,
     * the redacting-policy marker and the pin sit on OPPOSITE SIDES of at least one blank line, so a
     * blank-line-terminating isolation gives a hit set of ZERO with every test still green.
     * [BLANK_LINE_HAZARD_FIXTURE] machine-checks that this has not been simplified back.
     */
    private fun functionBodyAt(
        lines: List<String>,
        declarationIndex: Int,
    ): List<String> {
        val declarationIndent = lines[declarationIndex].indentWidth()
        val body = mutableListOf(lines[declarationIndex])
        for (index in declarationIndex + 1 until lines.size) {
            val line = lines[index]
            if (line.isNotBlank() && line.indentWidth() <= declarationIndent) break
            body += line
        }
        return body
    }

    /** The isolated body of a fixture, for the assertions that inspect the isolation itself. */
    private fun isolatedBodyOf(fixture: String): List<String> {
        val lines = fixture.lines()
        val declarationIndex = lines.indexOfFirst { FUNCTION_DECLARATION.containsMatchIn(it) }
        assertTrue(declarationIndex >= 0, "the fixture carries no function declaration line to isolate from")
        return functionBodyAt(lines, declarationIndex)
    }

    // ── shared primitives, borrowed from the two repository-state precedents ──────────────

    private fun String.indentWidth(): Int = length - trimStart().length

    private fun isCommentOnly(line: String): Boolean {
        val trimmed = line.trimStart()
        return trimmed.startsWith("//") || trimmed.startsWith("*") || trimmed.startsWith("/*")
    }

    private fun testSourceFiles(): List<File> =
        testSourceRoot()
            .walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .toList()

    private fun sourceFile(relative: String): File = File(testSourceRoot(), relative)

    private fun relativePath(file: File): String = file.relativeTo(testSourceRoot()).invariantSeparatorsPath

    /** Resolved by walking up from the Gradle test working directory. FAILS rather than skips. */
    private fun testSourceRoot(): File {
        var candidate: File? = File(System.getProperty("user.dir")).absoluteFile
        while (candidate != null) {
            val root = File(candidate, TEST_SOURCE_ROOT)
            if (root.isDirectory) return root
            candidate = candidate.parentFile
        }
        throw AssertionError(
            "could not resolve $TEST_SOURCE_ROOT from user.dir=${System.getProperty("user.dir")}. " +
                "Resolve the path (for example from a system property set in build.gradle.kts) rather " +
                "than weakening this test into a skip: a repository-state test that goes green when " +
                "it cannot find the repository is worse than the grep it replaced.",
        )
    }

    private data class Hit(
        val identifier: String,
        val argument: String,
        val vocabularyEntry: Int,
    ) {
        override fun toString(): String = "$identifier -> $argument (vocabulary entry $vocabularyEntry)"
    }

    private companion object {
        const val TEST_SOURCE_ROOT = "src/test/kotlin"
        const val SELF_PATH = "com/six2dez/burp/aiagent/redact/RedactingPolicySurvivalSweepTest.kt"

        // Measured at execution time: 151 .kt files under src/test/kotlin, this file included. The
        // floor is deliberately well below that — it is here to catch a walk that reaches nothing,
        // not to track the file count.
        const val MIN_EXPECTED_TEST_FILES = 100

        // Without the raw-string skip this file flags itself once per survival pin its positive
        // fixtures carry. MEASURED at execution time on this file: 5 unskipped, 0 skipped — the four
        // positive fixtures carry five pins between them, because the host-pin fixture carries two.
        // The floor stays well below the measurement for the same reason the file floor is 100: it
        // is here to catch a skip that has silently disarmed the detector, not to track a count.
        const val MIN_EXPECTED_UNSKIPPED_SELF_HITS = 2

        const val EXPECTED_NEGATIVE_FIXTURES = 6

        const val FIXTURE_ID = "<fixture>"
        const val TRIPLE_QUOTE = "\"\"\""
        const val CONTAINS_CALL = ".contains("
        const val ASSERT_TRUE = "assertTrue("
        const val ASSERT_FALSE = "assertFalse("
        const val ACCESSOR_SUFFIX = ".value"

        const val ENUM_ACCESSOR_ENTRY = 1
        const val HOST_LITERAL_ENTRY = 3

        val WHITESPACE_RUN = Regex("\\s+")
        val FUNCTION_DECLARATION = Regex("^\\s*(private |internal )?fun (\\w+)\\(")

        /**
         * The tokens whose presence in a function body puts that body IN SCOPE. A body that names
         * none of these is not asserting anything about a redacting policy, and an `assertTrue` on a
         * sentinel under `PrivacyMode.OFF` is CORRECT rather than a defect.
         */
        val REDACTING_POLICY_MARKERS =
            listOf(
                "PrivacyMode.STRICT",
                "PrivacyMode.BALANCED",
                "RedactionPolicy.fromMode(",
            )

        /**
         * Sensitive fixture values that do NOT follow the sentinel naming convention but are
         * unmistakably sensitive. The fixture hostname the two pins removed in plan 27-12 asserted
         * as surviving is the only measured member.
         */
        val HOST_LITERALS = listOf("api.example.com")

        /**
         * The FOUR MEASURED forms a sensitive fixture value takes in this repository. Measured
         * against the tree, not guessed: a string literal in the `sentinel…` convention; the
         * `Sentinel.NAME.value` enum accessor used by `SerializedEmissionRedactionTest`; a bare
         * local identifier named `sentinel`; and a literal from [HOST_LITERALS].
         *
         * Every entry is paired one-for-one with an entry of [VOCABULARY_FIXTURES], so an entry that
         * has stopped matching cannot hide in the list and make the scan vacuously clean.
         */
        val SENSITIVE_VALUE_VOCABULARY =
            listOf(
                Regex("^\"sentinel[A-Za-z0-9]*\"$"),
                Regex("^Sentinel\\.[A-Z0-9_]+\\.value$"),
                Regex("^sentinel$"),
                Regex("^\"[^\"]*(" + HOST_LITERALS.joinToString("|") { Regex.escape(it) } + ")[^\"]*\"$"),
            )

        /**
         * The ONE accessor SUBTRACTED from the enum-accessor form, by construction rather than by an
         * [ALLOWLIST] key. `Sentinel.BENIGN_CONTROL` is the negative-control constant whose value is
         * the benign id in `SerializedEmissionRedactionTest`; asserting that it SURVIVES is the whole
         * point of a negative control — it proves a pass was not produced by blanket destruction of
         * the payload.
         *
         * MEASURED pre-existing count it accounts for on the tree as shipped: 7 live functions, all
         * in `SerializedEmissionRedactionTest`. Plan 27-12 projected 5; the two extra are the
         * JSON-string-open probes plan 27-11 added, each of which carries its own benign-control
         * assertion. The measured number is what is written here.
         *
         * Adding a SECOND key here is a decision requiring the same source-verified reason an
         * [ALLOWLIST] key would, and it costs blind axis 11 in the class KDoc. An eighth
         * benign-control assertion appearing later is normal; a second key is not.
         */
        val BENIGN_ACCESSORS = setOf("Sentinel.BENIGN_CONTROL")

        /**
         * Keyed by `file#function`, valued with a reason read from source.
         *
         * EMPTY, and expected to STAY empty: all three legitimate shapes measured on this tree are
         * excluded BY CONSTRUCTION in the detector — [BENIGN_ACCESSORS] (7 hits), the position rule
         * (1) and the negation rule (1) — rather than by a key here. An allowlist that quietly grows
         * entries is the tripwire failure mode `CookieCarrierInventoryTest` was warned about, and
         * [noGreenTestAssertsASensitiveValueSurvivesARedactingPolicy] reports STALE keys as loudly as
         * NEW hits so a dead key cannot accumulate either.
         */
        val ALLOWLIST = emptyMap<String, String>()

        // ── fixtures ──────────────────────────────────────────────────────────────────────
        //
        // EVERY fixture below is a VERBATIM copy of a real test function, held at COMPANION level
        // inside a triple-quoted raw string. Verbatim is the requirement: a shape invented here
        // would prove only that the detector finds shapes invented here. The single transformation
        // is that a literal `$` is written `${'$'}` — Kotlin interpolates raw strings — which is
        // value-preserving, so the fixture VALUE is byte-identical to its source. Where a fixture
        // contains a triple quote of its own, the same value-preserving escape is used.
        //
        // Nothing is inlined inside a `@Test` body, and no `@Test` body contains a literal sentinel,
        // a literal hostname or a literal policy token. That is what keeps every fixture INSIDE a raw
        // string where the file walk's skip can reach it.

        /**
         * POSITIVE, vocabulary entry 0 — a sensitive value written as a STRING LITERAL in the
         * `sentinel...` convention.
         *
         * The opening block of the pre-round `McpToolHelpersTest.cookieVariantsAreStrippedEndToEnd
         * ThroughRedactIfNeeded`, a contiguous slice, with ONE verb flipped from `assertFalse` to
         * `assertTrue` so the block now asserts the X-Cookie sentinel SURVIVES a redacting policy.
         * The tree carries no such pin in this form today, which is exactly why a fixture is needed:
         * an entry proven against nothing is an entry that can stop matching unnoticed.
         */
        val STRING_LITERAL_SENTINEL_FIXTURE =
            """
        fun cookieVariantsAreStrippedEndToEndThroughRedactIfNeeded() {
            val context = contextWith(PrivacyMode.STRICT, "end-to-end-salt")
            val rawHeaders =
                listOf(
                    stubHeader("X-Cookie", "sentinelxrayninezulu"),
                    stubHeader("Authorization", "Bearer sentineltokenoscarwhisky"),
                    stubHeader("Host", "api.example.com"),
                    stubHeader("X-Request-Id", "benignidcontrolvalue"),
                )

            val sanitizedJson = toolJson.encodeToString(parsedRequestOf(sanitizeHeaders(rawHeaders, context)))
            val finalText = context.redactIfNeeded(sanitizedJson)

            assertTrue(
                finalText.contains("sentinelxrayninezulu"),
                "the X-Cookie value must not survive the tool-result flow (got: ${'$'}finalText)",
            )
        }
            """

        /**
         * POSITIVE, vocabulary entry 1 — the `Sentinel.NAME.value` enum-accessor form, AND the floor
         * under the BENIGN_ACCESSORS exclusion.
         *
         * The real `SerializedEmissionRedactionTest.canonicalCookieDoesNotSurviveTheSerializedProxy
         * HistoryShapeUnderStrict` with ONLY its verb flipped, so it asserts a real, non-benign cookie
         * sentinel SURVIVES. It must be FLAGGED. Paired with NEGATIVE_ASSERT_FALSE_FIXTURE, which is
         * the same block UNFLIPPED and must be clean: the pair proves BENIGN_ACCESSORS subtracts ONE
         * NAMED CONSTANT and not the enum-accessor form itself. An exclusion that cannot be shown to
         * have a floor is just a quieter allowlist.
         */
        val ANTI_SWALLOW_FIXTURE =
            """
        fun canonicalCookieDoesNotSurviveTheSerializedProxyHistoryShapeUnderStrict() {
            val serialized = toolJson.encodeToString(proxyHistoryFixture())

            val redacted = contextWith(PrivacyMode.STRICT, "serialized-strict-salt").redactIfNeeded(serialized)

            assertTrue(
                redacted.contains(Sentinel.COOKIE_STRICT.value),
                "a canonical Cookie value must not survive STRICT redaction of the serialized " +
                    "proxy_http_history shape (got: ${'$'}redacted)",
            )
        }
            """

        /**
         * POSITIVE, vocabulary entry 2 — a bare local identifier named `sentinel`. ALSO the
         * machine-check on the function-body isolation, and the reason it is mandatory.
         *
         * The pre-round `CookieHeaderNameParityTest.thePredicateIsDeliberatelyWiderThanTheTwoRegexes`,
         * VERBATIM, blank lines preserved byte-for-byte. Its `fun` line is followed by two lines of
         * setup, then a BLANK LINE, then the predicate assertion, then a SECOND BLANK LINE, then the
         * mode loop carrying the redacting-policy marker, and only then the `assertTrue` on sentinel
         * survival. A blank-line-terminating isolation returns a nine-line stub containing neither the
         * marker nor the pin — a hit set of ZERO with every test still green. That is this round's
         * defect one iteration smaller, so it is asserted rather than assumed.
         *
         * Plan 27-10 has since renamed and inverted this test, which is why the copy comes out of
         * history rather than off the working tree.
         */
        val BLANK_LINE_HAZARD_FIXTURE =
            """
    fun thePredicateIsDeliberatelyWiderThanTheTwoRegexes() {
        val name = "my_cookie"
        val sentinel = PARITY_CORPUS.first { it.first == name }.second

        assertTrue(
            Redaction.isCookieHeaderName(name),
            "the shared predicate is a bare contains() and must match '${'$'}name'",
        )

        for (mode in listOf(PrivacyMode.STRICT, PrivacyMode.BALANCED)) {
            val output = redactHeaderBlob(mode, name, sentinel)
            assertTrue(
                output.contains(sentinel),
                "${'$'}mode: the prompt path must NOT strip '${'$'}name' — '_' is outside COOKIE_NAME_PART, so neither " +
                    "cookie regex can match it. This asymmetry is INTENTIONAL and fail-safe: the predicate is " +
                    "wider than the two regexes, so the redacting side over-matches rather than under-matches. " +
                    "If this assertion fails, the prompt-path regexes were widened — record the measurement, " +
                    "do not narrow the predicate to restore symmetry (output: ${'$'}output)",
            )
            assertFalse(
                output.contains("${'$'}name: [STRIPPED]"),
                "${'$'}mode: '${'$'}name' must not be rewritten to the stripped form (output: ${'$'}output)",
            )
        }
    }
            """

        /**
         * POSITIVE, vocabulary entry 3 — the host literal. Carries BOTH survival pins plan 27-12
         * removed, because in the real tree BOTH lived in ONE function and splitting them into two
         * invented functions would be less faithful, not more.
         *
         * The pre-round `McpToolHelpersTest.cookieVariantsAreStrippedEndToEndThroughRedactIfNeeded`,
         * VERBATIM. The detector must report EXACTLY 2 hits on it — one on the serialized raw-message
         * shape, one on the header-map shape — and the marker-to-pin distance spans many blank lines,
         * so this fixture carries the same two-token containment assertion as the hazard fixture.
         */
        val HOST_PIN_FIXTURE =
            """
        fun cookieVariantsAreStrippedEndToEndThroughRedactIfNeeded() {
            val context = contextWith(PrivacyMode.STRICT, "end-to-end-salt")
            val rawHeaders =
                listOf(
                    stubHeader("X-Cookie", "sentinelxrayninezulu"),
                    stubHeader("Authorization", "Bearer sentineltokenoscarwhisky"),
                    stubHeader("Host", "api.example.com"),
                    stubHeader("X-Request-Id", "benignidcontrolvalue"),
                )

            val sanitizedJson = toolJson.encodeToString(parsedRequestOf(sanitizeHeaders(rawHeaders, context)))
            val finalText = context.redactIfNeeded(sanitizedJson)

            assertFalse(
                finalText.contains("sentinelxrayninezulu"),
                "the X-Cookie value must not survive the tool-result flow (got: ${'$'}finalText)",
            )
            assertTrue(
                finalText.contains("X-Cookie"),
                "the header NAME must survive — only the VALUE is replaced (T-21-WA2) (got: ${'$'}finalText)",
            )
            assertTrue(
                finalText.contains("benignidcontrolvalue"),
                "negative control: this must be stripping, not blanket header loss (got: ${'$'}finalText)",
            )

            // (PRIV-05) 27-04 — WHAT THIS BLOCK PINS NOW, and what it deliberately no longer claims.
            //
            // AR-27-01 said redactIfNeeded could never recover a header sanitizeHeaders missed, which
            // made sanitizeHeaders the LAST line of defence rather than the first of two. Plan 27-04
            // taught the two cookie rules to recognise a JSON-ESCAPED newline as a logical line
            // boundary, so that is now SHAPE-DEPENDENT, and the two shapes are measured separately
            // below rather than generalised into one sentence.
            //
            // Shape 1, the RAW MESSAGE embedded in JSON — what proxy_http_history, site_map and
            // scanner_issues emit via Serialization.kt. Its CRLFs encode to the two literal
            // characters backslash-r / backslash-n, which the new branch keys on, so redactIfNeeded IS
            // now a genuine second control for the COOKIE-HEADER class on this shape: sanitizeHeaders
            // is defence in depth here rather than the only control. The recovery holds for the
            // cookie-header class ONLY.
            //
            // What is still NOT recoverable on this shape, stated so a reader cannot mistake the line
            // above for a general claim: the HOST header, because hostHeaderRegex still recognises
            // only the real-line boundary — recorded as open finding AR-27-04 in plan 27-06 — and any
            // VENDOR auth header outside authHeaderRegex's 16-name alternation, which no rule names at
            // all. (Names INSIDE that alternation are not listed here: plan 27-05 closes exactly those
            // one wave later using the same composer, so calling them unrecoverable would be a
            // sentence that goes false in wave 5 with nothing instructed to correct it.)
            val rawMessage =
                "GET / HTTP/1.1\r\n" +
                    rawHeaders.joinToString("") { "${'$'}{it.name()}: ${'$'}{it.value()}\r\n" } +
                    "\r\n"
            val rawMessageJson = toolJson.encodeToString(HttpRequestResponse(request = rawMessage, response = null, notes = null))
            val rawMessageFinalText = context.redactIfNeeded(rawMessageJson)

            assertFalse(
                rawMessageFinalText.contains("sentinelxrayninezulu"),
                "redactIfNeeded must now recover a cookie header sanitizeHeaders missed, on the " +
                    "serialized raw-message shape (got: ${'$'}rawMessageFinalText)",
            )
            // Non-vacuity guard, PRESERVED from the original pin: the very same call must really have
            // transformed its input, so an absence assertion cannot be passing because redactIfNeeded
            // silently no-opped on a wrong policy or a wrong context. bearerRegex is NOT line-anchored,
            // so it is the transformation that fires regardless of which line boundary the payload has.
            assertFalse(
                rawMessageFinalText.contains("sentineltokenoscarwhisky"),
                "redactIfNeeded must really have run under a redacting policy (got: ${'$'}rawMessageFinalText)",
            )
            // Measured, and the reason the guard above does NOT use the host: hostHeaderRegex is still
            // line-anchored, so STRICT host anonymisation cannot fire on this shape. AR-27-04.
            assertTrue(
                rawMessageFinalText.contains("api.example.com"),
                "measured AR-27-04: the line-anchored host rule cannot fire on the serialized " +
                    "raw-message shape (got: ${'$'}rawMessageFinalText)",
            )

            // Shape 2, the HEADER-MAP shape — what parsedRequestOf/ParsedRequest emits for
            // request_parse and response_parse, and the shape sanitizeHeaders actually guards.
            //
            // MEASURED CORRECTION to plan 27-04's own premise, which expected this assertion to invert
            // too: it does not, and inverting it would have committed a FALSE test. This payload
            // carries NO line boundary of ANY kind — the headers are JSON object members, not lines,
            // so there is neither a real newline for the shipped `^` anchor nor an ESCAPED one for the
            // new branch. The root cause is gated directly below instead of via its cookie
            // consequence, so this block contains no green assertion that a cookie value survives a
            // redacting policy — such an assertion is exactly what a later audit misreads as intent.
            //
            // The consequence, stated because it is real and bounded: for the COOKIE-HEADER class on
            // THIS shape, sanitizeHeaders remains the only control. The first half of this same test
            // is what gates that control end to end.
            val rawJson = toolJson.encodeToString(parsedRequestOf(rawHeaders.associate { it.name() to it.value() }))

            assertFalse(
                rawJson.contains("\\r") || rawJson.contains("\\n"),
                "the header-map shape must carry no line boundary at all — that, and not anything " +
                    "cookie-specific, is why neither branch of the cookie rules can fire here (got: ${'$'}rawJson)",
            )

            val rawFinalText = context.redactIfNeeded(rawJson)

            // Non-vacuity on this shape too: redactIfNeeded really ran under a redacting policy here
            // as well, so the boundary assertion above is not standing in for a call that no-opped.
            assertFalse(
                rawFinalText.contains("sentineltokenoscarwhisky"),
                "redactIfNeeded must really have run under a redacting policy (got: ${'$'}rawFinalText)",
            )
            assertTrue(
                rawFinalText.contains("api.example.com"),
                "measured AR-27-04: the line-anchored host rule cannot fire on the header-map shape " +
                    "either (got: ${'$'}rawFinalText)",
            )
        }
            """

        /**
         * NEGATIVE SHAPES 1 AND 2 — an `assertTrue` on a header NAME surviving, and an `assertTrue` on
         * the benign control value surviving, both under a redacting policy, in one real function.
         *
         * The real `SerializedEmissionRedactionTest.headerNameAndBenignControlSurviveTheSerialized
         * Shape`, VERBATIM. The header name SHOULD survive — only the VALUE is replaced (T-21-WA2) —
         * and the benign control SHOULD survive, or a pass could be produced by blanket destruction of
         * the payload. Neither is a survival pin.
         */
        val NEGATIVE_HEADER_NAME_FIXTURE =
            """
        fun headerNameAndBenignControlSurviveTheSerializedShape() {
            val serialized = toolJson.encodeToString(proxyHistoryFixture())

            val redacted = contextWith(PrivacyMode.STRICT, "serialized-control-salt").redactIfNeeded(serialized)

            assertTrue(
                redacted.contains("Cookie"),
                "the header NAME must survive — only the VALUE is replaced (T-21-WA2) (got: ${'$'}redacted)",
            )
            assertTrue(
                redacted.contains(Sentinel.BENIGN_CONTROL.value),
                "negative control: a value in a non-cookie header must survive, so a pass cannot be " +
                    "produced by blanket destruction of the payload (got: ${'$'}redacted)",
            )
        }
            """

        /**
         * NEGATIVE SHAPE 2, INDEPENDENTLY — a second benign-control survival, in a body naming BOTH
         * redacting policies through a mode loop.
         *
         * The real `SerializedEmissionRedactionTest.issueDetailsCarrierStripsCookiesInBothRedacting
         * Modes`, VERBATIM. This is one of the seven live functions the BENIGN_ACCESSORS exclusion
         * accounts for.
         */
        val NEGATIVE_BENIGN_CONTROL_FIXTURE =
            """
        fun issueDetailsCarrierStripsCookiesInBothRedactingModes() {
            val serialized = toolJson.encodeToString(issueDetailsFixture())

            listOf(PrivacyMode.STRICT, PrivacyMode.BALANCED).forEach { mode ->
                val redacted = contextWith(mode, "issue-salt-${'$'}mode").redactIfNeeded(serialized)

                assertFalse(
                    redacted.contains(Sentinel.ISSUE_DETAILS_CARRIER.value),
                    "${'$'}mode: the scanner_issues carrier must strip the cookie value (got: ${'$'}redacted)",
                )
                assertTrue(
                    redacted.contains(Sentinel.BENIGN_CONTROL.value),
                    "${'$'}mode: negative control on the scanner_issues carrier (got: ${'$'}redacted)",
                )
                assertSameJsonShape(serialized, redacted)
            }
        }
            """

        /**
         * NEGATIVE SHAPE 3 — an `assertTrue` on a sentinel inside a body naming only `PrivacyMode.OFF`.
         *
         * Derived from the real `SerializedEmissionRedactionTest.offModeLeavesTheSerializedShapeByte
         * Identical` by substituting a containment assertion for its byte-identity one. Stated as a
         * derivation rather than passed off as verbatim: the tree carries no OFF-mode containment
         * assertion today, and the shape must still be tolerated, because under OFF pass-through IS the
         * correct behaviour. The body names no redacting policy, so it never enters scope at all.
         */
        val NEGATIVE_OFF_MODE_FIXTURE =
            """
        fun offModeLeavesTheSerializedShapeByteIdentical() {
            // The negative control that proves this is a POLICY-GATED strip and not unconditional
            // mangling. Byte equality against the un-redacted input, deliberately not a `contains`
            // check: a `contains` assertion would still pass if OFF mutated some other part of the
            // payload.
            val serialized =
                toolJson.encodeToString(
                    requestOnly("Cookie: wibble=${'$'}{Sentinel.OFF_MODE_CONTROL.value}"),
                )

            val redacted = contextWith(PrivacyMode.OFF, "off-mode-salt").redactIfNeeded(serialized)

            assertTrue(
                redacted.contains(Sentinel.OFF_MODE_CONTROL.value),
                "PrivacyMode.OFF must leave the cookie value in place",
            )
        }
            """

        /**
         * NEGATIVE SHAPE 4 — an `assertFalse` on a sentinel inside a body naming a redacting policy.
         *
         * The real `canonicalCookieDoesNotSurviveTheSerializedProxyHistoryShapeUnderStrict`, VERBATIM
         * and UNFLIPPED. The counterpart of ANTI_SWALLOW_FIXTURE.
         */
        val NEGATIVE_ASSERT_FALSE_FIXTURE =
            """
        fun canonicalCookieDoesNotSurviveTheSerializedProxyHistoryShapeUnderStrict() {
            val serialized = toolJson.encodeToString(proxyHistoryFixture())

            val redacted = contextWith(PrivacyMode.STRICT, "serialized-strict-salt").redactIfNeeded(serialized)

            assertFalse(
                redacted.contains(Sentinel.COOKIE_STRICT.value),
                "a canonical Cookie value must not survive STRICT redaction of the serialized " +
                    "proxy_http_history shape (got: ${'$'}redacted)",
            )
        }
            """

        /**
         * NEGATIVE SHAPE 5 — a PRE-REDACTION FIXTURE GUARD: an `assertTrue` on a sentinel positioned
         * ABOVE the line that first names the policy. MEASURED pre-existing count on this tree: 1.
         *
         * The real `SerializedEmissionRedactionTest.theRealTruncateIfNeededOutputShapeIsStrippedAndNot
         * Lengthened`, VERBATIM. Nothing has been redacted yet at that point in the body — the guard
         * asserts the value is present in the INPUT, which is what makes the later absence assertion
         * non-vacuous. The POSITION RULE excludes it, and blind axis 10 in the class KDoc is what that
         * exclusion costs.
         */
        val NEGATIVE_PRE_REDACTION_GUARD_FIXTURE =
            """
        fun theRealTruncateIfNeededOutputShapeIsStrippedAndNotLengthened() {
            val sentinel = Sentinel.TRUNCATION_HAZARD.value
            val serialized =
                toolJson.encodeToString(
                    requestOnly(
                        "Cookie: wibble=${'$'}sentinel\r\n" + "X-Filler: 0123456789012345678901234567890123456789\r\n".repeat(6),
                    ),
                )
            // Cut immediately AFTER a backslash that begins an encoded newline well past the cookie
            // header, so the sentinel is fully present in the truncated input (an absence assertion
            // must not be able to pass because truncation removed the sentinel itself) and the cut
            // lands mid-escape.
            val afterCookie = serialized.indexOf(sentinel) + sentinel.length
            val cutIndex = serialized.indexOf("\\r", serialized.indexOf("X-Filler", afterCookie)) + 1
            val truncated = truncateIfNeeded(serialized, cutIndex)

            assertTrue(truncated.contains(sentinel), "fixture guard: the sentinel must survive truncation itself")
            assertTrue(truncated.endsWith("bytes)"), "fixture guard: this must be the real truncateIfNeeded suffix shape")
            assertTrue(
                runCatching { toolJson.parseToJsonElement(truncated) }.isFailure,
                "fixture guard: the truncated input is already invalid JSON, which is why no parse gate applies here",
            )

            val redacted = contextWith(PrivacyMode.STRICT, "truncate-salt").redactIfNeeded(truncated)

            assertFalse(
                redacted.contains(sentinel),
                "the cookie value must still be stripped on the real truncated shape (got: ${'$'}redacted)",
            )
            assertTrue(
                redacted.length <= truncated.length,
                "the match must not run away: ${'$'}{redacted.length} > ${'$'}{truncated.length}",
            )
        }
            """

        /**
         * NEGATIVE SHAPE 6 — a NEGATED containment, `assertTrue(!x.contains(v))`: an ABSENCE assertion
         * wearing the wrong verb. MEASURED pre-existing count on this tree: 1.
         *
         * The real `RedactionTest.cookieHeaderNameVariantsAreStripped`, VERBATIM. The NEGATION RULE
         * excludes it. Its own triple-quoted input literal is escaped the same value-preserving way a
         * dollar is.
         */
        val NEGATIVE_NEGATED_CONTAINMENT_FIXTURE =
            """
    fun cookieHeaderNameVariantsAreStripped() {
        val input =
            ${'"'}${'"'}${'"'}
            GET / HTTP/1.1
            Host: example.com
            Cookie2: sentinelalphaone
            X-Cookie: sentinelbravotwo
            Set-Cookie2: sentinelcharliethree
            X-Original-Cookie: sentineldeltafour
            X-Forwarded-Cookie: sentinelechofive
            X-Request-Id: benignidcontrolvalue

            ${'"'}${'"'}${'"'}.trimIndent()

        val variants =
            listOf(
                "Cookie2" to "sentinelalphaone",
                "X-Cookie" to "sentinelbravotwo",
                "Set-Cookie2" to "sentinelcharliethree",
                "X-Original-Cookie" to "sentineldeltafour",
                "X-Forwarded-Cookie" to "sentinelechofive",
            )

        for (mode in listOf(PrivacyMode.STRICT, PrivacyMode.BALANCED)) {
            val policy = RedactionPolicy.fromMode(mode)
            val output = Redaction.apply(input, policy, stableHostSalt = "salt")

            for ((header, sentinel) in variants) {
                assertTrue(
                    !output.contains(sentinel),
                    "${'$'}mode: the value of ${'$'}header must not reach the prompt (leaked: ${'$'}output)",
                )
                assertTrue(
                    output.contains("${'$'}header: [STRIPPED]"),
                    "${'$'}mode: ${'$'}header must keep its OWN name, not be renamed to Cookie/Set-Cookie (got: ${'$'}output)",
                )
            }

            assertTrue(
                output.contains("X-Request-Id: benignidcontrolvalue"),
                "${'$'}mode: a header with no 'cookie' in its name must be left untouched (got: ${'$'}output)",
            )
        }
    }
            """

        /**
         * One positive fixture per [SENSITIVE_VALUE_VOCABULARY] entry, IN THE SAME ORDER, so an
         * entry that has stopped matching cannot hide in the list. The equal-size assertion in
         * [everyVocabularyEntryIsProvenLiveAgainstItsOwnPositiveFixture] keeps the pairing honest.
         */
        val VOCABULARY_FIXTURES =
            listOf(
                STRING_LITERAL_SENTINEL_FIXTURE,
                ANTI_SWALLOW_FIXTURE,
                BLANK_LINE_HAZARD_FIXTURE,
                HOST_PIN_FIXTURE,
            )

        /**
         * The SIX legitimate shapes that must NOT be flagged, every one of them taken from the real
         * tree rather than invented, because a shape invented here would prove only that the
         * detector tolerates shapes invented here.
         *
         * Two of them — the pre-redaction fixture guard and the negated containment — were
         * MEASURABLY FLAGGING under the unqualified vocabulary. They are the reason the constructed
         * exclusion count is three and not one.
         */
        val NEGATIVE_FIXTURES =
            listOf(
                "header NAME and benign control survive the serialized shape" to NEGATIVE_HEADER_NAME_FIXTURE,
                "benign control survives the issue-details carrier in both redacting modes" to NEGATIVE_BENIGN_CONTROL_FIXTURE,
                "a sentinel asserted present in a body naming only PrivacyMode.OFF" to NEGATIVE_OFF_MODE_FIXTURE,
                "an absence assertion on a sentinel under a redacting policy" to NEGATIVE_ASSERT_FALSE_FIXTURE,
                "a pre-redaction fixture guard, positioned above the policy marker" to NEGATIVE_PRE_REDACTION_GUARD_FIXTURE,
                "a negated containment — an absence assertion wearing the wrong verb" to NEGATIVE_NEGATED_CONTAINMENT_FIXTURE,
            )
    }
}
