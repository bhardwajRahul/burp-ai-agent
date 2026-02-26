package com.six2dez.burp.aiagent.util

object IssueUtils {
    private val aiPrefixRegex = Regex("^\\[(?:AI(?:\\s+Passive)?)\\]\\s*", RegexOption.IGNORE_CASE)

    fun canonicalIssueName(name: String): String {
        return name
            .trim()
            .replace(aiPrefixRegex, "")
            .replace(aiPrefixRegex, "")
            .trim()
            .lowercase()
    }

    fun formatIssueDetailHtml(lines: List<String>): String {
        return lines.joinToString("<br>") { line ->
            val escaped = line
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
            if (escaped.startsWith("  ")) {
                "&nbsp;&nbsp;" + escaped.drop(2)
            } else {
                escaped
            }
        }
    }

    fun hasEquivalentIssue(
        name: String,
        baseUrl: String,
        issues: Iterable<Pair<String, String>>
    ): Boolean {
        val canonicalName = canonicalIssueName(name)
        return issues.any { issue ->
            issue.second == baseUrl && canonicalIssueName(issue.first) == canonicalName
        }
    }

    fun hasExistingIssue(
        name: String,
        baseUrl: String,
        issues: Iterable<Pair<String, String>>,
        ignoreCase: Boolean = false
    ): Boolean {
        return issues.any { issue ->
            val sameName = if (ignoreCase) {
                issue.first.equals(name, ignoreCase = true)
            } else {
                issue.first == name
            }
            sameName && issue.second == baseUrl
        }
    }
}
