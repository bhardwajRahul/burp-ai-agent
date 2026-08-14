package com.six2dez.burp.aiagent

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * The sentence future tool authors inherit, declared ONCE so the two copies it guards cannot both be
 * edited to agree with each other while drifting from what was decided.
 *
 * It deliberately starts at "means" rather than at "AUTO": the repo-root `DECISIONS.md` writes the
 * subject as the markdown literal `` `AUTO` `` and the `SecTier` KDoc writes it as the KDoc link
 * `[AUTO]`, so the leading token is the one part the two copies legitimately differ on. Everything
 * after it is the rule itself and must be byte-identical once markup is normalised away.
 */
private const val AUTO_DEFINITION: String =
    "means read-only AND bounded output. A tool qualifies only if it neither mutates Burp state " +
        "nor pulls bulk attacker-controlled traffic into model context"

private const val DECISIONS_FILE = "DECISIONS.md"
private const val CATALOG_FILE = "src/main/kotlin/com/six2dez/burp/aiagent/mcp/McpToolCatalog.kt"

/**
 * SEC-06 / SC1 guard on ADR-15 — the written rule by which a tool added in a later phase inherits the
 * trust boundary instead of re-litigating it.
 *
 * **What this test can do.** It is a string-match guard. It stops ADR-15 from disappearing, stops it
 * from silently losing D-05's `AUTO` sentence in a later edit, and — the part that carries the real
 * value — asserts that the ADR's copy and the [com.six2dez.burp.aiagent.mcp.SecTier] KDoc's copy still
 * say the same thing. Two divergent copies of the inherited rule is the failure mode here; a match
 * against one copy alone would not catch it, because the drift would simply move into the other.
 *
 * **What this test cannot do.** It cannot judge whether ADR-15 is *factually accurate* — whether the
 * threat model it records matches shipped behaviour, and whether it claims only what ships (D-14).
 * That is judgement, not a string match, and it is assigned to a human in
 * `.planning/phases/22-agent-tool-call-trust-boundary/22-HUMAN-UAT.md` test 2. Neither half is
 * sufficient alone: an ADR is the one success criterion that is easy to mark done without checking,
 * which is why SC1 carries both a cheap automated guard and an explicit human reviewer.
 */
class DecisionsAdrTest {
    @Test
    fun adr15ExistsAndIsTheHighestNumberedAdr() {
        val decisions = readProjectFile(DECISIONS_FILE)

        assertTrue(
            decisions.lineSequence().any { it.startsWith("## ADR-15") },
            "$DECISIONS_FILE must contain a heading line starting `## ADR-15`. ADR-15 is SEC-06's " +
                "record of the agent tool-call trust boundary; if it was renumbered, the guard below " +
                "and 22-HUMAN-UAT.md both point at nothing.",
        )
        assertFalse(
            decisions.lineSequence().any { it.startsWith("## ADR-16") },
            "$DECISIONS_FILE contains `## ADR-16`, so ADR-15 is no longer the highest-numbered ADR. " +
                "This assertion exists to make the NEXT phase's author notice they must claim a new " +
                "number and extend this guard, rather than quietly reusing one.",
        )
    }

    @Test
    fun adr15CarriesTheAutoDefinitionVerbatim() {
        val normalised = normalise(adr15Section())

        assertTrue(
            normalised.contains(AUTO_DEFINITION),
            "ADR-15 no longer carries D-05's `AUTO` definition verbatim. This sentence is the rule " +
                "future tool authors inherit; if it changed, the classification changed, and ADR-15 " +
                "must be revised deliberately rather than incidentally. Expected to find:\n  " +
                AUTO_DEFINITION,
        )
    }

    @Test
    fun theCatalogAndTheAdrCarryTheSameAutoDefinition() {
        val kdoc = normalise(secTierKdoc())

        assertTrue(
            kdoc.contains(AUTO_DEFINITION),
            "The `SecTier` KDoc in $CATALOG_FILE no longer states the same `AUTO` definition as " +
                "ADR-15. The rule lives in two places by design — the ADR is what a contributor " +
                "reads, the KDoc is what they see at the point of writing `secTier = ` — and two " +
                "copies that disagree is worse than one, because each looks authoritative. Fix both " +
                "or neither. Expected to find:\n  " + AUTO_DEFINITION,
        )
    }

    @Test
    fun adr15RecordsTheIndependenceFromUnsafeOnly() {
        val section = adr15Section()

        assertTrue(
            section.contains("capability switch"),
            "ADR-15 must keep D-01's crux sentence — that `unsafeOnly` is a capability switch, not a " +
                "trust model. It is the answer to the question a future contributor will otherwise " +
                "get wrong: why are there two classifications?",
        )
        assertTrue(
            section.contains("unsafeOnly"),
            "ADR-15 must name `unsafeOnly` explicitly when recording that the SEC-06 tier is a " +
                "second, independent axis (D-01).",
        )
    }

    /**
     * Reads a file relative to the Gradle project directory.
     *
     * `tasks.test` runs with the project directory as its working directory, so a plain relative path
     * resolves. The existence assertion names the resolved working directory rather than letting a
     * future build-layout change surface as an unhelpful null or empty string.
     */
    private fun readProjectFile(relativePath: String): String {
        val file = File(relativePath)
        assertTrue(
            file.isFile,
            "Expected to find `$relativePath` relative to the test working directory " +
                "`${System.getProperty("user.dir")}`, resolved as `${file.absolutePath}`. " +
                "If the build layout changed, fix the path here rather than deleting this test — " +
                "it is the only automated check that ADR-15 still exists.",
        )
        return file.readText()
    }

    /**
     * ADR-15's own text, sliced from its heading to the next `## ADR` heading or end of file, so an
     * assertion cannot be satisfied by wording that belongs to a different ADR.
     */
    private fun adr15Section(): String {
        val decisions = readProjectFile(DECISIONS_FILE)
        val start = decisions.indexOf("## ADR-15")
        assertTrue(start >= 0, "$DECISIONS_FILE contains no `## ADR-15` heading to slice.")
        val next = decisions.indexOf("\n## ADR", start + 1)
        return if (next >= 0) decisions.substring(start, next) else decisions.substring(start)
    }

    /**
     * The KDoc block immediately preceding `enum class SecTier(`, sliced by locating the declaration
     * and walking back to its opening delimiter. Slicing rather than searching the whole file matters: the
     * assertion must be about the KDoc a contributor reads at the declaration, not about the sentence
     * happening to appear anywhere in a 1000-line catalog.
     */
    private fun secTierKdoc(): String {
        val catalog = readProjectFile(CATALOG_FILE)
        val declaration = catalog.indexOf("enum class SecTier(")
        assertTrue(declaration >= 0, "$CATALOG_FILE no longer declares `enum class SecTier(`.")
        val kdocStart = catalog.lastIndexOf("/**", declaration)
        assertTrue(kdocStart >= 0, "`enum class SecTier(` in $CATALOG_FILE has no preceding KDoc block.")
        return catalog.substring(kdocStart, declaration)
    }

    /**
     * Strips the two markup channels that legitimately differ between the copies — markdown blockquote
     * markers and emphasis (`>`, `*`) and KDoc leading asterisks — then collapses every whitespace run
     * to a single space so a line wrap in either file is not a false failure.
     *
     * Neither stripped character appears in [AUTO_DEFINITION], so this normalisation cannot manufacture
     * a match that the source text does not contain.
     */
    private fun normalise(raw: String): String = raw.replace(Regex("[>*]"), "").replace(Regex("\\s+"), " ").trim()
}
