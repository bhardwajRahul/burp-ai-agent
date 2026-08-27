package com.six2dez.burp.aiagent.scanner

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * WHAT THIS IS FOR.
 *
 * Phase 27 (D-27-17) deliberately REFUSED to convert `InjectionPointExtractor.kt`'s hand-written
 * `it.type().name == "COOKIE"` filter to the shared [com.six2dez.burp.aiagent.redact.Redaction]
 * predicate, and wrote the reason into `Redaction.isCookieParameterType`'s KDoc: while the
 * issue-detail route the extractor feeds was still uncontrolled, swapping the predicate would have
 * produced a tidier file and an unchanged leak — a route that merely LOOKED addressed.
 *
 * Plan 28-01 controlled that route at its write site
 * ([ScannerIssueSupport.sanitizeInjectionPointValue]). Plan 28-02 then collected the conversion.
 * That commit falsifies the predicate COUNT the KDoc pinned, so this file re-derives the count from
 * the tree instead of letting a new number be restated in prose where it would go stale silently.
 *
 * WHAT THIS IS BOUNDED BY — read this before quoting the test as evidence.
 *
 * The scan sees only the four spelling classes enumerated in [COOKIE_TYPE_COMPARISONS]: literal
 * equality/inequality in either operand order, an `equals("COOKIE"` call, and equality against the
 * owner's `COOKIE_PARAMETER_TYPE_NAME` constant. Two constructs are OUTSIDE that population BY
 * DESIGN, and naming them here is what keeps the count honest:
 *
 * 1. `InjectionPointExtractor.matchInsertionPoint`'s `when (param.type().name)` maps all THREE
 *    Montoya type names (`URL`, `BODY`, `COOKIE`) to [InjectionType] values. That is a three-way
 *    dispatch, not a cookie predicate. Converting only its COOKIE arm would leave a `when` whose
 *    arms are spelled two different ways — less readable than either consistent form — so plan
 *    28-02 left it, and a bare `"COOKIE" ->` arm is therefore not counted here.
 * 2. `Redaction.COOKIE_PARAMETER_TYPE_NAME`'s own `= "COOKIE"` declaration is a DECLARATION, not a
 *    comparison. Counting the owner's constant would make the owner look like a duplicate of itself.
 *
 * Comment lines are stripped before matching. A `grep -c`-style count over unstripped source would
 * count the very supersession comments plan 28-02 writes — which is exactly how a header becomes
 * self-invalidating. Stripping is by LINE PREFIX — a line-comment marker, a KDoc continuation star,
 * or a block-comment opener — so a comparison hidden behind a TRAILING comment on a code line is
 * still seen, while a comparison spelled outside the four classes is not.
 *
 * This is a TRIPWIRE OVER MEASURED SPELLINGS, not a proof of exhaustive coverage. Anyone adding a
 * new spelling class must add it to [COOKIE_TYPE_COMPARISONS], or the tripwire silently stops
 * covering their code.
 */
class CookieRouteDispositionTest {
    @Test
    fun exactlyOneCookieTypePredicateExistsInMainSource() {
        val files = mainSourceFiles()
        assertTrue(
            files.size >= MIN_EXPECTED_MAIN_FILES,
            "the source walk reached ${files.size} .kt files under $MAIN_SOURCE_ROOT, below the " +
                "floor of $MIN_EXPECTED_MAIN_FILES. A walk that reaches nothing would report a " +
                "clean tree for the same reason a correct tree does; fix the walk before reading " +
                "the count below as evidence.",
        )

        val hits = files.flatMap { file -> cookieTypeComparisonsIn(file).map { relativePath(file) to it } }

        assertEquals(
            1,
            hits.size,
            "expected exactly ONE cookie-parameter-type comparison in $MAIN_SOURCE_ROOT — the one " +
                "inside $OWNER's isCookieParameterType. Found ${hits.size}: " +
                hits.joinToString("; ") { "${it.first} -> ${it.second.trim()}" } +
                ". A SECOND cookie-type predicate is how this control gets bypassed without anyone " +
                "editing isCookieParameterType: the new predicate silently acquires its own notion " +
                "of what a cookie parameter is, and the two drift apart exactly as the header rule " +
                "did before phase 27. Route the new call site through Redaction.isCookieParameterType " +
                "instead of widening this expectation.",
        )

        assertEquals(
            OWNER,
            hits.single().first,
            "the single cookie-parameter-type comparison must live in $OWNER, the OWNER of the rule. " +
                "Finding it elsewhere means ownership moved without the KDoc moving with it.",
        )
    }

    @Test
    fun everyCookieTypeComparisonSpellingHasAKnownPositive() {
        COOKIE_TYPE_COMPARISONS.zip(SPELLING_FIXTURES).forEach { (pattern, fixture) ->
            assertTrue(
                pattern.containsMatchIn(fixture),
                "spelling class /${pattern.pattern}/ matched none of its own fixture <$fixture>. A " +
                    "regex that matches nothing reports a clean tree for free, so the count above " +
                    "would be vacuous.",
            )
        }
        assertEquals(
            COOKIE_TYPE_COMPARISONS.size,
            SPELLING_FIXTURES.size,
            "every spelling class needs exactly one known positive, or an unfixtured class can rot " +
                "into a no-op unnoticed.",
        )
    }

    private fun cookieTypeComparisonsIn(file: File): List<String> =
        file
            .readLines()
            .filterNot { line ->
                val trimmed = line.trimStart()
                trimmed.startsWith("//") || trimmed.startsWith("*") || trimmed.startsWith("/*")
            }.filter { line -> COOKIE_TYPE_COMPARISONS.any { it.containsMatchIn(line) } }

    private fun mainSourceFiles(): List<File> = mainSourceRoot().walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()

    private fun relativePath(file: File): String = file.absolutePath.substringAfter("$MAIN_SOURCE_ROOT${File.separator}").replace(File.separatorChar, '/')

    private fun mainSourceRoot(): File {
        var candidate: File? = File(System.getProperty("user.dir")).absoluteFile
        while (candidate != null) {
            val root = File(candidate, MAIN_SOURCE_ROOT)
            if (root.isDirectory) return root
            candidate = candidate.parentFile
        }
        throw AssertionError(
            "could not resolve $MAIN_SOURCE_ROOT from user.dir=${System.getProperty("user.dir")}. " +
                "Resolve the path (for example from a system property set in build.gradle.kts) rather " +
                "than weakening this test into a skip.",
        )
    }

    private companion object {
        const val MAIN_SOURCE_ROOT = "src/main/kotlin"

        // Measured at 172 .kt files when this test was written. The floor is deliberately well below
        // that: it is here to catch a walk that reaches nothing, not to track the file count.
        const val MIN_EXPECTED_MAIN_FILES = 150

        /** The OWNER of the cookie-parameter-type rule. */
        const val OWNER = "com/six2dez/burp/aiagent/redact/Redaction.kt"

        /**
         * The ways a Montoya parameter TYPE is compared against the COOKIE constant in this codebase.
         * Case-sensitive: the Montoya enum name is uppercase, and a case-insensitive scan would also
         * pick up the lowercase HEADER-name rules, which are a different control with a different
         * owner (see `CookieHeaderRuleOwnershipTest`).
         *
         * See the class KDoc for what this population deliberately EXCLUDES.
         */
        val COOKIE_TYPE_COMPARISONS =
            listOf(
                // literal equality/inequality, predicate on the left
                Regex("[=!]= \"COOKIE\""),
                // literal equality/inequality, predicate on the right
                Regex("\"COOKIE\" [=!]="),
                // equals-call form, with or without `ignoreCase`
                Regex("equals\\(\"COOKIE\""),
                // equality against the owner's constant — the one legitimate hit
                Regex("[=!]= COOKIE_PARAMETER_TYPE_NAME"),
            )

        /** One known positive per spelling class, so a regex that matches nothing cannot hide. */
        val SPELLING_FIXTURES =
            listOf(
                """.filter { it.type().name == "COOKIE" }""",
                """if ("COOKIE" == param.type().name) return true""",
                """if (typeName.equals("COOKIE", ignoreCase = true)) return true""",
                """typeName.trim().uppercase(Locale.ROOT) == COOKIE_PARAMETER_TYPE_NAME""",
            )
    }
}
