package com.six2dez.burp.aiagent.redact

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * (PRIV-05) Phase 27 plans 27-04 + 27-05 / D-27-13 — which redaction rules carry the logical-line
 * boundary, pinned from source.
 *
 * WHAT THIS IS FOR.
 *
 * Plan 27-04 taught three header rules that a JSON-escaped newline is a logical line start, by
 * routing them through ONE composer, `Redaction.logicalLineHeaderRule`. Because it is one composer,
 * a single token carries the whole claim: a rule that STOPPED using it, or a fourth rule that
 * STARTED, is a one-line edit that would otherwise be invisible in review.
 *
 * `hostHeaderRegex` is deliberately NOT in that set (D-27-13), and the reason is measured rather
 * than aesthetic: it rewrites through `anonymizeHost`, which records into the de-anonymisation map
 * that `RedactionHostMapBoundTest` exists to bound, and `SiteMapEntry.url` carries the same host
 * verbatim with no `maybeAnonymizeUrl` in front of it — so anonymising the header alone would
 * produce a control that reads as closed and is not. That exclusion is a DECISION, and a decision
 * that lives only in a SUMMARY drifts from the code the first time someone "fixes" it in passing.
 *
 * So this file asserts the composition and the record TOGETHER: the three rules that carry the
 * composer, the one that must not, and the rationale comment that has to keep naming the excluded
 * rule. A reader who finds the code and the record disagreeing has found a real regression.
 *
 * WHAT THIS IS BOUNDED BY — read this before quoting it as evidence.
 *
 * It is a tripwire over FOUR NAMED RULES in ONE NAMED FILE, `redact/Redaction.kt`. A FIFTH rule with
 * the same blind spot, a rule in another file, or a header matcher that never reaches this file at
 * all is invisible to it and leaves it GREEN. It says nothing about whether the boundary is the
 * RIGHT one — the behavioural evidence for that is `SerializedEmissionRedactionTest` and
 * `RedactionTest`, not a source scan. `CookieHeaderRuleOwnershipTest` states its own bound in the
 * same place and for the same reason.
 */
class LogicalLineBoundaryScopeTest {
    @Test
    fun theBoundaryFragmentsAreComposedIntoTheMeasuredRuleSetAndNoOther() {
        // The POSITIVES are asserted first and individually, so this test cannot pass because the
        // isolation helper quietly returned nothing. A negative-only gate over an empty block is
        // exactly the decorative tripwire T-27-05-04 exists to prevent.
        COMPOSED_RULES.forEach { rule ->
            val block = declarationBlockOf(rule)
            assertEquals(
                1,
                block.count { it.contains(COMPOSER_CALL) },
                "$rule must be built by exactly one `$COMPOSER_CALL` call. Plan 27-04 routed every " +
                    "logical-line header rule through ONE composer so the boundary cannot be edited " +
                    "for one rule and forgotten for the others — a hand-inlined rule here is that " +
                    "drift beginning.\n  Declaration block read as: $block",
            )
        }

        // The negative below is only worth anything if the helper actually FOUND the declaration.
        // The guard checks that and nothing more: probing for a particular initializer shape (say a
        // literal `Regex(`) would couple this to how the excluded rule happens to be written today,
        // and would then fire INSTEAD of the assertion that carries the claim.
        val excludedBlock = declarationBlockOf(EXCLUDED_RULE)
        assertTrue(
            excludedBlock.firstOrNull()?.contains("private val $EXCLUDED_RULE") == true,
            "the isolation helper returned no declaration for $EXCLUDED_RULE, so the negative " +
                "assertion below would prove nothing. Read as: $excludedBlock",
        )
        assertEquals(
            0,
            excludedBlock.count { it.contains(COMPOSER_CALL) },
            "$EXCLUDED_RULE must NOT be routed through `$COMPOSER_CALL` (D-27-13). Two measured " +
                "reasons: anonymizeHost writes into a de-anonymisation map bounded by " +
                "RedactionHostMapBoundTest, and firing it on every raw message of every site_map / " +
                "proxy_http_history result is an unmeasured load change on that bound; and " +
                "SiteMapEntry.url carries the same host VERBATIM with no maybeAnonymizeUrl in front " +
                "of it, so anonymising the header alone yields a payload whose `request` is " +
                "anonymised and whose `url` is not — a control that reads as closed and is not. " +
                "If this is now deliberate, close the url field too and update AR-27-04, the " +
                "rationale comment and this constant together.\n  Declaration block read as: $excludedBlock",
        )
    }

    @Test
    fun theScopeScanIsNonVacuous() {
        // Resolving the file is the first assertion: sourceFile() throws AssertionError rather than
        // returning null, so a scan that cannot find the repository FAILS instead of going green.
        val lines = sourceFile().readLines()
        assertTrue(
            lines.size >= MIN_EXPECTED_LINES,
            "$RULE_OWNER has ${lines.size} lines, below the floor of $MIN_EXPECTED_LINES — the scan " +
                "is looking at the wrong file, or the file it pins has been gutted",
        )

        // Every symbol this file reasons about must actually be declared, or an assertion that
        // "the composer appears zero times here" could be true because nothing exists at all.
        (COMPOSED_RULES + EXCLUDED_RULE).forEach { rule ->
            assertTrue(
                declarationBlockOf(rule).isNotEmpty(),
                "no declaration of `$rule` found in $RULE_OWNER — every assertion about it is vacuous",
            )
        }
        REQUIRED_DECLARATIONS.forEach { declaration ->
            assertTrue(
                lines.any { it.trimStart().startsWith(declaration) },
                "`$declaration` is no longer declared in $RULE_OWNER. The composer and its three " +
                    "fragments are what the boundary rationale is written against; if they were " +
                    "renamed or inlined, this test's constants describe a file that no longer exists.",
            )
        }

        // And comment stripping is proven live. The rationale block in that file NAMES every symbol
        // these scans look for, so an unfiltered match would read prose as composition.
        assertTrue(isCommentOnly("    // $COMPOSER_CALL"), "the comment filter no longer recognises a line comment")
        assertTrue(isCommentOnly("     * $COMPOSER_CALL"), "the comment filter no longer recognises a KDoc body line")
        assertTrue(
            (COMPOSED_RULES + EXCLUDED_RULE).flatMap { declarationBlockOf(it) }.none { isCommentOnly(it) },
            "a comment-only line reached a declaration block",
        )
    }

    @Test
    fun theStatedBoundIsPresentWhereAReaderMeetsIt() {
        // The record and the source state have to move together. This asserts the rationale block a
        // reader actually meets — the contiguous comment region immediately above the boundary
        // fragments — still names what the composition below it does and does not cover.
        val rationale = rationaleRegionAboveFragments()
        assertTrue(
            rationale.size >= MIN_RATIONALE_LINES,
            "the rationale region above `${REQUIRED_DECLARATIONS.first()}` is only ${rationale.size} " +
                "lines. The bound is supposed to be stated where a reader meets it, not in a planning " +
                "document they will not open.",
        )
        val text = rationale.joinToString("\n")

        assertTrue(
            text.contains(EXCLUDED_RULE),
            "the rationale above the boundary fragments no longer names `$EXCLUDED_RULE` as the " +
                "deliberate exclusion. The code excludes it; if the comment stops saying so, the next " +
                "reader meets a composer with no statement of what it does not cover — which is how " +
                "a record ends up wider than the control it describes.",
        )
        assertTrue(
            text.contains(OPEN_FINDING),
            "the rationale must carry the open-finding id `$OPEN_FINDING`, so the residual stays " +
                "traceable from source to the phase record rather than surviving as an aside",
        )
        assertTrue(
            text.contains(SECOND_OPEN_FINDING),
            "the rationale must ALSO carry `$SECOND_OPEN_FINDING`, the leading-whitespace / obs-fold " +
                "start the boundary still does not recognise. BOTH residuals have to stay traceable " +
                "from source: the composer now recognises three logical line starts, and a comment " +
                "that stops naming the fourth leaves the next reader with a boundary that reads " +
                "complete and is not — which is how this requirement has been closed wrongly three " +
                "times already.",
        )
        assertTrue(
            text.contains(THIRD_OPEN_FINDING),
            "the rationale must ALSO carry `$THIRD_OPEN_FINDING`, the JSON-ARRAY-ELEMENT string open " +
                "that stopped being a recognised start when 27-14 narrowed the third start to a JSON " +
                "string VALUE open. ALL THREE residuals have to stay traceable from source: the " +
                "boundary now recognises a NARROWED set of logical line starts, and a comment that " +
                "stops naming what the boundary cannot see leaves the next reader with a boundary " +
                "that reads complete and is not — which is how this requirement has been closed " +
                "wrongly FOUR times already.",
        )
        COMPOSED_RULES.forEach { rule ->
            assertTrue(
                text.contains(rule),
                "the rationale above the boundary fragments does not name `$rule`, which IS composed " +
                    "from them. The stated set and the composed set must match exactly.",
            )
        }
    }

    /**
     * (PRIV-05) 27-14 — THE ANTI-DRIFT PIN on the third start's VALUE, not just its name.
     *
     * Plan 27-11 shipped `JSON_STRING_OPEN` as a BARE double quote while calling it a JSON string
     * open, and every existing guard in this file stayed green: [REQUIRED_DECLARATIONS] asserts the
     * declaration PREFIX, and the composition scans assert which rules carry the boundary. Neither
     * looks at what the constant is worth. A one-character revert would therefore ship green a
     * SECOND time, which is the exact failure mode this test exists to remove.
     *
     * The value is READ OUT OF `Redaction.kt` at test time rather than re-typed, on the
     * `CookieHeaderNameWidthTest.theCoveredSetIsReadFromRedactionSourceNotRetyped` precedent — a
     * retyped expectation drifts silently, a source-read one cannot.
     */
    @Test
    fun theJsonStringOpenIsAValueOpenAndNotABareQuote() {
        // Comment-only lines are filtered FIRST: the rationale block above the fragments names this
        // symbol in prose several times, and an unfiltered count would read that prose as a
        // declaration and make the "exactly one" assertion wrong for the wrong reason.
        val declarations =
            sourceFile()
                .readLines()
                .filterNot { isCommentOnly(it) }
                .filter { it.trimStart().startsWith(JSON_STRING_OPEN_DECLARATION) }

        assertEquals(
            1,
            declarations.size,
            "`$JSON_STRING_OPEN_DECLARATION` must be declared EXACTLY once in $RULE_OWNER, or the " +
                "value assertion below is reading one of several and proves nothing about the " +
                "boundary the composer actually uses.\n  Read as: $declarations",
        )

        val literal = declarations.single().substringAfter("=").trim()
        assertTrue(
            literal.length >= 2 && literal.startsWith("\"") && literal.endsWith("\""),
            "the initializer of `$JSON_STRING_OPEN_DECLARATION` is not a plain string literal, so it " +
                "cannot be decoded here. Read as: $literal",
        )

        val decoded = decodeKotlinStringLiteral(literal)

        assertEquals(
            EXPECTED_JSON_STRING_OPEN_WIDTH,
            decoded.length,
            "the third logical-line start must stay FIXED-WIDTH at $EXPECTED_JSON_STRING_OPEN_WIDTH " +
                "characters. A width-one value here means the bare double quote 27-11 shipped is " +
                "back: a bare quote is NOT a JSON string open — it also opens HTML attribute values, " +
                "JS string literals and quoted CSV fields — and it was MEASURED destroying 1589 of " +
                "1714 characters of a realistic serialized tool result, removing all forty of its " +
                "content markers while leaving the JSON structurally valid, so every shape assertion " +
                "passed and the model read a truncated body. Widening this constant back is a " +
                "CORRECTNESS REGRESSION on the primary MCP emission path, not a tightening.\n" +
                "  Read as: $literal",
        )
        assertEquals(
            ":\"",
            decoded,
            "the third logical-line start must be the colon-quote sequence — a JSON string VALUE " +
                "open. The narrowing deliberately accepts one residual, recorded as AR-27-11: " +
                "a header at the open of a JSON ARRAY ELEMENT string (`[\"Cookie: …\"]`) is not a " +
                "recognised start, MEASURED as matching before 27-14 and as not matching after it. " +
                "That residual is the whole trade; do not undo it by re-widening this value.\n" +
                "  Read as: $literal",
        )
    }

    // ── the scans ─────────────────────────────────────────────────────────────────────────

    /**
     * The lines of one property's declaration, comment lines removed.
     *
     * Isolated BY NAME and BY INDENTATION rather than by counting parentheses: these declarations
     * concatenate regex fragments, and a string literal like `"(?!"` carries an unbalanced paren
     * that would derail a brace counter. A member of `object Redaction` is declared at one indent
     * level; its initializer is indented deeper; the block therefore ends at the next non-blank line
     * at or above the declaration's own indent.
     */
    private fun declarationBlockOf(propertyName: String): List<String> {
        val lines = sourceFile().readLines()
        val startIndex = lines.indexOfFirst { Regex("""^\s*private val $propertyName\s*[:=]""").containsMatchIn(it) }
        if (startIndex < 0) return emptyList()

        val declarationIndent = lines[startIndex].indentWidth()
        val initializer =
            lines
                .subList(startIndex + 1, lines.size)
                .takeWhile { it.isNotBlank() && it.indentWidth() > declarationIndent }
        return (listOf(lines[startIndex]) + initializer).filterNot { isCommentOnly(it) }
    }

    /**
     * The contiguous comment-and-blank region immediately above the first boundary fragment — the
     * prose a reader meets on their way to the composition, walking back to the first line of real
     * code above it.
     */
    private fun rationaleRegionAboveFragments(): List<String> {
        val lines = sourceFile().readLines()
        val fragmentIndex = lines.indexOfFirst { it.trimStart().startsWith(REQUIRED_DECLARATIONS.first()) }
        if (fragmentIndex < 0) return emptyList()

        var start = fragmentIndex
        while (start > 0) {
            val candidate = lines[start - 1]
            if (!candidate.isBlank() && !isCommentOnly(candidate)) break
            start--
        }
        return lines.subList(start, fragmentIndex).filter { isCommentOnly(it) }
    }

    /**
     * Decodes the body of a Kotlin string literal, covering exactly the TWO escapes a regex-fragment
     * constant of this kind can carry: an escaped double quote and an escaped backslash. Anything
     * else FAILS rather than being passed through — a decoder that silently ignores an escape it
     * does not know would compare the wrong value and go green.
     */
    private fun decodeKotlinStringLiteral(literal: String): String {
        val body = literal.removeSurrounding("\"")
        val decoded = StringBuilder()
        var index = 0
        while (index < body.length) {
            val character = body[index]
            if (character != '\\') {
                decoded.append(character)
                index++
                continue
            }
            when (val escaped = body.getOrNull(index + 1)) {
                '"' -> decoded.append('"')
                '\\' -> decoded.append('\\')
                else ->
                    throw AssertionError(
                        "unhandled Kotlin escape `\\$escaped` in $literal. Extend this decoder " +
                            "deliberately rather than loosening the value assertion that reads it.",
                    )
            }
            index += 2
        }
        return decoded.toString()
    }

    private fun String.indentWidth(): Int = length - trimStart().length

    private fun isCommentOnly(line: String): Boolean {
        val trimmed = line.trimStart()
        return trimmed.startsWith("//") || trimmed.startsWith("*") || trimmed.startsWith("/*")
    }

    /** Resolved by walking up from the Gradle test working directory. FAILS rather than skips. */
    private fun sourceFile(): File {
        var candidate: File? = File(System.getProperty("user.dir")).absoluteFile
        while (candidate != null) {
            val file = File(candidate, RULE_OWNER)
            if (file.isFile) return file
            candidate = candidate.parentFile
        }
        throw AssertionError(
            "could not resolve $RULE_OWNER from user.dir=${System.getProperty("user.dir")}. " +
                "Resolve the path rather than weakening this test into a skip.",
        )
    }

    private companion object {
        const val RULE_OWNER = "src/main/kotlin/com/six2dez/burp/aiagent/redact/Redaction.kt"

        /** Measured at 2018 lines. A floor, not a count. */
        const val MIN_EXPECTED_LINES = 1500

        const val COMPOSER_CALL = "logicalLineHeaderRule("

        /**
         * The MEASURED set of rules that carry the logical-line boundary. Plan 27-04 added the two
         * cookie rules; plan 27-05 (D-27-12) added `authHeaderRegex` on the measured evidence that a
         * plain-token `X-API-Key` value survived STRICT and BALANCED on the serialized shape.
         */
        val COMPOSED_RULES =
            listOf(
                "cookieHeaderRegex",
                "setCookieHeaderRegex",
                "authHeaderRegex",
            )

        /** The deliberate exclusion (D-27-13), carried as open finding AR-27-04 for plan 27-06. */
        const val EXCLUDED_RULE = "hostHeaderRegex"
        const val OPEN_FINDING = "AR-27-04"

        /**
         * The SECOND residual (27-11): a header line preceded by leading horizontal whitespace, or an
         * obs-folded continuation, matches none of the three recognised starts. MEASURED surviving
         * STRICT in round 3 and deliberately out of that round's scope.
         *
         * It is pinned here for the same reason `AR-27-04` is: a residual that lives only in a
         * planning document is one refactor away from disappearing, and the next reader then meets a
         * boundary that reads complete and is not.
         */
        const val SECOND_OPEN_FINDING = "AR-27-09"

        /**
         * The THIRD residual (27-14): a header at the open of a JSON ARRAY ELEMENT string. An array
         * element opens on a bracket-quote or comma-quote sequence, and since 27-14 narrowed
         * [JSON_STRING_OPEN_DECLARATION]'s value to a colon-quote sequence — a JSON string VALUE
         * open — that shape is no longer a recognised start. MEASURED in both directions: it matched
         * under the bare quote 27-11 shipped and is byte-unchanged under the narrowed value.
         *
         * It is the residual the narrowing BOUGHT, and it is pinned here for the same reason
         * `AR-27-04` and `AR-27-09` are: a residual that lives only in a planning document is one
         * refactor away from disappearing. What is pinned is the CITATION, never the survival — a
         * green assertion that this cookie value survives a redacting policy is the artifact class
         * `26-SECURITY.md` standing-rule clause (vi) prohibits.
         */
        const val THIRD_OPEN_FINDING = "AR-27-11"

        /** The declaration [theJsonStringOpenIsAValueOpenAndNotABareQuote] reads the VALUE out of. */
        const val JSON_STRING_OPEN_DECLARATION = "private const val JSON_STRING_OPEN"

        // A lookbehind wider than a FIXED width forces Java to try two look-back widths at every
        // position of every serialized emission and silently trades away the measured 2.4x — the same
        // reason JSON_ESCAPED_NEWLINE's comment in Redaction.kt gives for its own two characters.
        const val EXPECTED_JSON_STRING_OPEN_WIDTH = 2

        /**
         * The composer and the four fragments the boundary rationale is written against.
         *
         * ORDER IS LOAD-BEARING, and only for the FIRST element: `rationaleRegionAboveFragments`
         * anchors its walk-back on `REQUIRED_DECLARATIONS.first()`, so promoting a different
         * declaration to index 0 would move the anchor and change what [MIN_RATIONALE_LINES]
         * measures — silently, with nothing turning red. `private const val JSON_ESCAPED_NEWLINE`
         * stays first. New fragments are APPENDED, as `JSON_STRING_OPEN` was at 27-11.
         *
         * Nothing here may be REMOVED to make room, either. Each entry is a live guard, and the
         * composer entry — the only non-fragment in the list — is the one a "tidy-up" would reach
         * for first.
         */
        val REQUIRED_DECLARATIONS =
            listOf(
                "private const val JSON_ESCAPED_NEWLINE",
                "private const val REAL_LINE_HEADER_VALUE",
                "private const val JSON_ESCAPED_HEADER_VALUE",
                "private fun logicalLineHeaderRule",
                "private const val JSON_STRING_OPEN",
            )

        /**
         * A floor on the rationale region, so a one-line comment cannot pass as a stated bound.
         * MEASURED at 125 comment lines after plan 27-11 extended the region (78 before it). A
         * FLOOR, not a count — the same discipline as [MIN_EXPECTED_LINES] — so prose edits are free
         * while gutting the stated bounds turns red.
         */
        const val MIN_RATIONALE_LINES = 90
    }
}
