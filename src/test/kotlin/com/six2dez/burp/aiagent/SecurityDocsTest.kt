package com.six2dez.burp.aiagent

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

private const val SECURITY_FILE = "SECURITY.md"

/** The three published releases SEC-04 and PRIV-05 are live in. */
private val AFFECTED_VERSIONS = listOf("0.9.0", "0.9.1", "0.9.2")

/** The release that carries both fixes. Unpublished at the time this guard was written. */
private const val FIXED_VERSION = "0.10.0"

/**
 * DOC-03 / SC5 guard on the security advisory — the statement owed to every user running 0.9.0
 * through 0.9.2, who has an unauthenticated MCP listener if they opted into external access and has
 * sent real session cookies to a third-party model if they ran passive AI scanning.
 *
 * **What this test can do.** It is a string-match guard over `SECURITY.md` as it sits on disk. It
 * stops the advisory from being deleted, stops either finding from losing its affected-version range
 * or its fixed version, stops the user-action line — the only part of an advisory that changes what
 * a reader *does* — from being edited away, and stops the Supported Versions table from drifting
 * back to the stale 0.5 line it carried for four milestones.
 *
 * **What this test cannot do.** It cannot judge whether the advisory's account of each defect is
 * *accurate*, whether the impact is fairly stated rather than softened, or whether a reader would
 * actually act on it. That is judgement. The plan assigns it to the phase verifier and to the
 * human reading this file; the guard below only ensures there is something there to judge.
 *
 * Note on slicing: every version assertion runs against the *individual advisory entry*, not against
 * the whole file. A whole-file match would pass if one entry carried all three versions and the
 * other carried none — which is precisely the half-finished edit worth catching.
 */
class SecurityDocsTest {
    @Test
    fun securityPolicyCarriesAnAdvisoriesSection() {
        val security = readProjectFile(SECURITY_FILE)

        assertTrue(
            security.lineSequence().any { it.trim() == "## Security Advisories" },
            "$SECURITY_FILE must carry a `## Security Advisories` heading. SEC-04 and PRIV-05 were " +
                "confirmed by RUNNING v0.9.2, so the people running it are owed a statement in the " +
                "one file a user checks for exactly that. If this section moved, point this guard " +
                "at its new home rather than deleting the assertion.",
        )
    }

    @Test
    fun theAdvisoriesSectionNamesBothFindings() {
        val advisories = advisoriesSection()

        assertTrue(
            advisories.contains("SEC-04"),
            "The advisories section does not name SEC-04 — the MCP access-control bypass. It is the " +
                "critical finding of this milestone and the reason the milestone exists.",
        )
        assertTrue(
            advisories.contains("PRIV-05"),
            "The advisories section does not name PRIV-05 — the cookie leak to AI backends. It runs " +
                "directly against the project's stated core value, so it cannot be the finding that " +
                "gets quietly dropped from the advisory.",
        )
    }

    @Test
    fun theSec04EntryNamesEveryAffectedVersionAndTheFix() {
        assertEntryCarriesVersions("SEC-04")
    }

    @Test
    fun thePriv05EntryNamesEveryAffectedVersionAndTheFix() {
        assertEntryCarriesVersions("PRIV-05")
    }

    @Test
    fun theSec04EntryTellsAnAffectedUserWhatToDo() {
        val entry = advisoryEntry("SEC-04")

        assertTrue(
            entry.contains("User action"),
            "The SEC-04 entry carries no `User action` line. An advisory without one consumes the " +
                "reader's attention without changing what they do, which is worse than no advisory.",
        )
        assertTrue(
            entry.contains("unauthenticated", ignoreCase = true),
            "The SEC-04 user action must tell an operator to treat an externally-reachable listener " +
                "on an affected version as having been UNAUTHENTICATED. Anything softer leaves them " +
                "unsure whether the tool calls they see in their logs were their own.",
        )
        assertTrue(
            entry.contains("rotate", ignoreCase = true),
            "The SEC-04 user action must name the concrete remediation — rotating the MCP bearer " +
                "token. `Be aware of` is not an action.",
        )
    }

    @Test
    fun thePriv05EntryTellsAnAffectedUserWhatToDo() {
        val entry = advisoryEntry("PRIV-05")

        assertTrue(
            entry.contains("User action"),
            "The PRIV-05 entry carries no `User action` line. The whole point of disclosing a cookie " +
                "leak is that the reader rotates the cookies.",
        )
        assertTrue(
            entry.contains("rotate", ignoreCase = true),
            "The PRIV-05 user action must say to ROTATE affected session cookies. Describing the " +
                "leak without naming the remedy leaves the exposure standing.",
        )
        assertTrue(
            entry.contains("disclosed", ignoreCase = true),
            "The PRIV-05 user action must say that affected cookies should be treated as DISCLOSED " +
                "to the configured backend. A reader who thinks this was a near-miss will not act.",
        )
    }

    @Test
    fun thePriv05EntryNamesTheCookiesThatLeaked() {
        val entry = advisoryEntry("PRIV-05")

        listOf("JSESSIONID", "PHPSESSID", "connect.sid", "auth_token", "csrftoken", "remember_me")
            .forEach { cookie ->
                assertTrue(
                    entry.contains(cookie),
                    "The PRIV-05 entry does not name `$cookie`. The six names were verified against " +
                        "the live regexes; listing them is what lets a reader decide in seconds " +
                        "whether their traffic was affected, instead of guessing.",
                )
            }
    }

    @Test
    fun theAdvisorySaysNoIdentifierWasIssuedRatherThanLeavingTheReaderToAssume() {
        val advisories = advisoriesSection()

        assertTrue(
            advisories.contains("CVE") && advisories.contains("GHSA"),
            "The advisories section must state explicitly that no CVE and no GHSA identifier has " +
                "been issued. Silence reads as `there is one and I did not find it`; a fabricated " +
                "identifier would be worse still, because it makes every true statement beside it " +
                "unverifiable.",
        )
        assertFalse(
            Regex("CVE-\\d{4}-\\d{4,}").containsMatchIn(advisories),
            "The advisories section contains something shaped like a real CVE identifier. If one " +
                "has genuinely been issued, update this guard in the same commit that adds it — " +
                "otherwise it is invented, and an invented identifier undermines the whole advisory.",
        )
    }

    @Test
    fun theAdvisoryDoesNotImplyThePublishedFixExists() {
        val advisories = advisoriesSection()

        assertTrue(
            advisories.contains("not yet published", ignoreCase = true),
            "The advisories section must say that $FIXED_VERSION is not yet published. A reader who " +
                "believes a fixed release is available will go looking for it, fail, and conclude " +
                "the advisory is wrong about everything else too.",
        )
    }

    @Test
    fun theSupportedVersionsTableNoLongerNamesTheStaleLine() {
        val security = readProjectFile(SECURITY_FILE)

        assertFalse(
            security.contains("0.5.x"),
            "$SECURITY_FILE still names `0.5.x` as the supported line. That table went unrevised " +
                "for four milestones; a user reading it cannot tell which releases actually receive " +
                "security fixes, which is the one question the table exists to answer.",
        )
        assertTrue(
            security.contains("0.9.x"),
            "$SECURITY_FILE's Supported Versions table must name `0.9.x` — the released line the " +
                "advisory above is about. Leaving it out orphans every affected reader.",
        )
    }

    @Test
    fun theSecurityModelStatesWhereTheMasterKeyLives() {
        val security = readProjectFile(SECURITY_FILE)

        assertTrue(
            security.contains("secret.master.key.v1"),
            "$SECURITY_FILE's Security Model must name `SecretCipher.MASTER_KEY_PREF_KEY` " +
                "(`secret.master.key.v1`) — the preference the AES master key is stored in, " +
                "Base64-encoded, beside the ciphertext it protects. Naming it is what makes the " +
                "caveat checkable rather than a hedge.",
        )
        assertTrue(
            security.contains("does **not** protect") || security.contains("does not protect"),
            "$SECURITY_FILE's Security Model must state what the at-rest encryption does NOT do. A " +
                "user who believes preference-file access is survivable will store a credential " +
                "there that they would otherwise have kept elsewhere.",
        )
    }

    /**
     * Reads a file relative to the Gradle project directory.
     *
     * `tasks.test` runs with the project directory as its working directory, so a plain relative path
     * resolves. The existence assertion names the resolved working directory rather than letting a
     * future build-layout change surface as an unhelpful empty string.
     */
    internal fun readProjectFile(relativePath: String): String {
        val file = File(relativePath)
        assertTrue(
            file.isFile,
            "Expected to find `$relativePath` relative to the test working directory " +
                "`${System.getProperty("user.dir")}`, resolved as `${file.absolutePath}`. " +
                "If the build layout changed, fix the path here rather than deleting this test.",
        )
        return file.readText()
    }

    /** `SECURITY.md`'s advisories section, sliced from its heading to the next `## ` heading. */
    private fun advisoriesSection(): String = slice(readProjectFile(SECURITY_FILE), "## Security Advisories", "\n## ")

    /**
     * One advisory entry, sliced from its `### ` heading to the next `### ` or `## ` heading, so a
     * version-range assertion cannot be satisfied by text belonging to the OTHER finding.
     */
    private fun advisoryEntry(finding: String): String {
        val advisories = advisoriesSection()
        val start = advisories.indexOf("### $finding")
        assertTrue(
            start >= 0,
            "$SECURITY_FILE's advisories section has no `### $finding` heading to slice. Each " +
                "finding needs its own entry — a merged paragraph lets one finding's details stand " +
                "in for the other's.",
        )
        val rest = advisories.substring(start)
        val next = rest.indexOf("\n### ", 1).let { if (it >= 0) it else rest.indexOf("\n## ", 1) }
        return if (next >= 0) rest.substring(0, next) else rest
    }

    private fun assertEntryCarriesVersions(finding: String) {
        val entry = advisoryEntry(finding)

        AFFECTED_VERSIONS.forEach { version ->
            assertTrue(
                entry.contains(version),
                "The $finding entry does not name affected version `$version`. All three published " +
                    "0.9.x releases carry this defect; hedging the range leaves a reader on the " +
                    "unnamed one assuming they are safe.",
            )
        }
        assertTrue(
            entry.contains(FIXED_VERSION),
            "The $finding entry does not name `$FIXED_VERSION` as the fixed version. An advisory " +
                "that states the problem without stating the fix gives the reader nowhere to go.",
        )
    }

    private fun slice(
        text: String,
        heading: String,
        terminator: String,
    ): String {
        val start = text.indexOf(heading)
        assertTrue(start >= 0, "Expected to find `$heading` to slice from.")
        val next = text.indexOf(terminator, start + heading.length)
        return if (next >= 0) text.substring(start, next) else text.substring(start)
    }
}
