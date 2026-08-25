package com.six2dez.burp.aiagent.redact

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * (PRIV-05) Phase 27 plan 27-08 / D-27-22 — the CARRIER inventory.
 *
 * WHAT THIS IS FOR.
 *
 * PRIV-05 has been refuted on three axes in this phase. Waves 1-3 found header NAMES the control did
 * not spell. Waves 4-6 found emission PATHS the control never reached. `27-VERIFICATION-2.md` found a
 * field TYPE — `HttpParameterType.COOKIE` — that no prior mechanism could see. Each round built a
 * rigorous mechanism for the axis it was looking along and was structurally blind to the next,
 * because each keyed on a RENDERING of the data: `CookieHeaderRuleOwnershipTest` keys on how a
 * matcher is SPELLED, `SerializedEmissionSiteInventoryTest` on how an emission is SHAPED,
 * `Redaction.cookieTypedParamRegex` on how a parameter is FORMATTED. A rendering-keyed mechanism can
 * only see renderings it already knows.
 *
 * This file keys one axis up: on the SOURCE of the bytes. A cookie byte can only enter this extension
 * through a Montoya call that returns it. So enumerate those calls, and require every site that makes
 * one to be either ROUTED through a named control or CLASSIFIED non-carrying with a reason read from
 * that site's own CONSUMER. Adding a call site anywhere in `src/main/kotlin` fails a test until
 * somebody classifies it.
 *
 * ── THE BOUND, STATED FIRST AND NOT AS A FOOTNOTE ──
 *
 * THIS IS A TRIPWIRE OVER A MEASURED ACCESSOR SET. IT IS NOT A PROOF OF COVERAGE. Anyone who quotes
 * this test as evidence that no cookie value can reach an AI backend reproduces, one iteration
 * smaller, the defect this whole phase exists to repair: a record wider than the control it
 * describes. Four things it CANNOT see, named here so nobody has to discover them as round five:
 *
 *  1. A COOKIE BYTE THAT NEVER PASSES A MONTOYA ACCESSOR. Operator-pasted text in the chat box; a
 *     model echoing a cookie back into the transcript; a cookie this extension itself persisted to
 *     settings or a cache and later emitted. No accessor-keyed mechanism reaches any of these.
 *
 *  2. `bodyToString()`, DELIBERATELY EXCLUDED — with the reason that actually holds. Measured at 32
 *     call sites across 8 files, comment-stripped, 2026-08-25. The exclusion is NOT because an entity
 *     body cannot contain cookie bytes. It can: a body can carry a pasted raw HTTP message, a
 *     forwarded webhook envelope, or a proxied upstream request, and any of those carries a `Cookie:`
 *     header inside the body text. Writing the exclusion as "an entity body does not contain the
 *     Cookie header" would be an absolute that one pasted request falsifies — a premise authored to
 *     be refuted in round five. THE REASON THAT DOES HOLD: a body of that shape reaching a backend
 *     passes `Redaction.apply` at the `redactIfNeeded` choke point, and there the logical-line cookie
 *     rules DO fire, because such a body carries a REAL newline before the `Cookie:` token — exactly
 *     the boundary waves 4-6 taught those rules to see. The carrier is covered by an EXISTING control
 *     on a DIFFERENT axis, so enumerating its 32 sites here would add churn without adding coverage.
 *     Two contingencies travel with that reason, because it is contingent where the old one pretended
 *     to be absolute: (a) the exclusion FAILS for any body that reaches a backend on a path bypassing
 *     `Redaction.apply`, and an accessor-keyed inventory cannot see such a path — that is axis 1, one
 *     step out; and (b) a session token duplicated into a body FIELD (a JSON value, a form parameter)
 *     carries no header line and no newline discriminator, so it is the TOKEN class (PRIV-02),
 *     reachable by different rules and NOT covered here either.
 *
 *  3. TRANSITIVE CARRIERS, where a cookie byte is copied into a field whose accessor is not on the
 *     list. `AuditIssue.detail()` is the worked example, surfaced by the `InjectionPointExtractor` and
 *     `ResponseAnalyzer` entries below and carried in [ISSUE_DETAIL_CARRIER_DISPOSITION]. This
 *     inventory can point at the FIRST hop; it cannot follow a value through arbitrary copies.
 *
 *  4. A NEW MONTOYA ACCESSOR added by a future API version that returns cookie data under a name not
 *     in [COOKIE_BYTE_ACCESSORS]. The set is additive-only and a reader adding one must extend it.
 *
 * ── A FIFTH, WEAKER BOUND, ON THE GRANULARITY OF THE CLASSIFICATION ITSELF ──
 *
 * Separated from the four above rather than averaged into them, because it is a limit of the
 * BOOKKEEPING and not of the axis. A registry key is a (file, accessor) PAIR, not an individual call.
 * Where the calls behind one pair split — `McpToolExecutorImpl`'s three `parameters()` calls are two
 * `sanitizeParameters` producers plus one non-carrying `find_reflected` reader — the pair sits in
 * [ROUTED_THROUGH] and its reason enumerates the split by line. That prose is NOT machine-checked.
 * What IS machine-checked is the COUNT: adding a fourth `parameters()` call to that file turns
 * [theMeasuredPerFilePerAccessorCountsArePinned] red and forces the split to be re-read. The tripwire
 * property survives; the per-call attribution is a human record inside it.
 */
class CookieCarrierInventoryTest {
    @Test
    fun everyCookieByteCarrierSiteIsRoutedOrClassified() {
        val measured = scanCarrierSites().keys
        val declared = ROUTED_THROUGH.keys + CLASSIFIED_NON_CARRYING.keys

        val overlap = ROUTED_THROUGH.keys intersect CLASSIFIED_NON_CARRYING.keys
        assertTrue(
            overlap.isEmpty(),
            "a site cannot be both routed through a control and classified non-carrying — pick one " +
                "and say why: $overlap",
        )

        assertEquals(
            declared,
            measured,
            "the set of cookie-byte carrier sites has changed.\n" +
                "  NEW (route it through a named control, or classify it in CLASSIFIED_NON_CARRYING " +
                "with the reason you read from its CONSUMER — not from the call site): " +
                "${measured - declared}\n" +
                "  STALE (declared here but no longer measured — remove the entry so the registry " +
                "cannot accumulate dead keys): ${declared - measured}",
        )
    }

    @Test
    fun theMeasuredPerFilePerAccessorCountsArePinned() {
        val measured = scanCarrierSites()
        val measuredPerFile = measured.entries.groupBy({ it.key.path }, { it.key.accessor to it.value })

        MEASURED_CARRIER_SITES.forEach { (path, expectedPerAccessor) ->
            val actualPerAccessor = measuredPerFile[path].orEmpty().toMap()
            assertEquals(
                expectedPerAccessor,
                actualPerAccessor,
                "$path no longer carries the pinned accessor counts. The WHOLE per-file map is " +
                    "printed so a drift is diagnosable without re-running the scan by hand.\n" +
                    "  pinned:   $expectedPerAccessor\n" +
                    "  measured: $actualPerAccessor\n" +
                    "If the change is deliberate, re-read the new site's CONSUMER, classify it, and " +
                    "update this count in the same edit — never one without the other.",
            )
        }

        assertEquals(
            MEASURED_CARRIER_SITES.keys,
            measuredPerFile.keys,
            "the set of FILES carrying a measured accessor has changed. Per-file measured counts: " +
                "${measuredPerFile.mapValues { it.value.toMap() }}",
        )

        assertEquals(
            EXPECTED_TOTAL_CARRIER_SITES,
            measured.values.sum(),
            "the total measured carrier-site count has drifted. Per site: $measured",
        )
        assertEquals(
            EXPECTED_TOTAL_CARRIER_SITES,
            MEASURED_CARRIER_SITES.values.sumOf { it.values.sum() },
            "the pinned per-file map and the pinned total disagree with each other, so one of the " +
                "two was edited alone",
        )
    }

    @Test
    fun theHeaderValueArgumentMultisetIsPinned() {
        // A count alone is defeatable by a simultaneous add and remove. The ARGUMENT is the data that
        // matters for this accessor: `headerValue("Cookie")` returns cookie bytes and
        // `headerValue("Content-Type")` cannot, so a new cookie-named argument must fail here even
        // when the per-file count is unchanged.
        val measured =
            mainSourceFiles()
                .flatMap { file -> codeLines(file).flatMap { HEADER_VALUE_ARGUMENT.findAll(it).map { m -> m.groupValues[1] } } }
                .groupingBy { it }
                .eachCount()

        assertEquals(
            MEASURED_HEADER_VALUE_ARGUMENTS,
            measured,
            "the `.headerValue(\"…\")` argument multiset has changed. A NEW cookie-named argument is " +
                "a new carrier site even if the per-file count did not move.\n" +
                "  pinned:   $MEASURED_HEADER_VALUE_ARGUMENTS\n" +
                "  measured: $measured",
        )
        assertEquals(
            MEASURED_CARRIER_SITES.values.sumOf { it[SINGLE_HEADER_LOOKUP] ?: 0 },
            measured.values.sum(),
            "the argument multiset and the per-file SINGLE_HEADER_LOOKUP counts disagree, which means " +
                "a call passes something this scan cannot read as a string literal",
        )
    }

    @Test
    fun theCarrierScanIsNonVacuous() {
        // A repository-state test that goes green when it cannot find the repository is worse than
        // the grep it replaced. Both halves below FAIL rather than skip.
        val files = mainSourceFiles()
        assertTrue(
            files.size >= MIN_EXPECTED_MAIN_FILES,
            "the walk found only ${files.size} .kt files under $MAIN_SOURCE_ROOT — the scan is not " +
                "reaching the repository, so every other assertion here proves nothing",
        )

        COOKIE_BYTE_ACCESSORS.forEach { (accessor, spec) ->
            assertTrue(
                spec.pattern.containsMatchIn(spec.positiveFixture),
                "accessor '$accessor' has a pattern that matches none of its own declared positive " +
                    "fixture [${spec.positiveFixture}] — it can no longer detect the call it exists " +
                    "to detect, and every count it contributes is vacuously correct",
            )
        }
        assertTrue(
            HEADER_VALUE_ARGUMENT.containsMatchIn("""val cookieHeader = request.headerValue("Cookie")"""),
            "the headerValue argument pattern matches no known positive fixture",
        )

        assertEquals(
            MEASURED_CARRIER_SITES.size,
            MEASURED_CARRIER_SITES.keys.count { path -> sourceFile(path).isFile },
            "a pinned carrier file does not exist on disk, so its zero counts would read as agreement: " +
                "${MEASURED_CARRIER_SITES.keys.filterNot { sourceFile(it).isFile }}",
        )
    }

    // ── the scan ─────────────────────────────────────────────────────────────────────────

    private fun scanCarrierSites(): Map<CarrierSite, Int> {
        val out = mutableMapOf<CarrierSite, Int>()
        mainSourceFiles().forEach { file ->
            val lines = codeLines(file)
            COOKIE_BYTE_ACCESSORS.forEach { (accessor, spec) ->
                val count = lines.sumOf { spec.pattern.findAll(it).count() }
                if (count > 0) out[CarrierSite(relativePath(file), accessor)] = count
            }
        }
        return out
    }

    /** Non-comment lines only, so prose ABOUT an accessor can never count as a call to it. */
    private fun codeLines(file: File): List<String> = file.readLines().filterNot { isCommentOnly(it) }

    private fun isCommentOnly(line: String): Boolean {
        val trimmed = line.trimStart()
        return trimmed.startsWith("//") || trimmed.startsWith("*") || trimmed.startsWith("/*")
    }

    private fun mainSourceFiles(): List<File> =
        mainSourceRoot()
            .walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .toList()

    private fun sourceFile(relativePath: String): File = File(mainSourceRoot(), relativePath)

    private fun relativePath(file: File): String = file.relativeTo(mainSourceRoot()).invariantSeparatorsPath

    // Resolved from the Gradle test working directory, then by walking up. If it cannot be found the
    // test FAILS — it is never weakened into a skip.
    private fun mainSourceRoot(): File {
        var candidate: File? = File(System.getProperty("user.dir")).absoluteFile
        while (candidate != null) {
            val root = File(candidate, MAIN_SOURCE_ROOT)
            if (root.isDirectory) return root
            candidate = candidate.parentFile
        }
        throw AssertionError(
            "could not resolve $MAIN_SOURCE_ROOT from user.dir=${System.getProperty("user.dir")}. " +
                "Resolve the path rather than weakening this test into a skip.",
        )
    }

    /** One measured call site class: a file plus the accessor it calls. */
    private data class CarrierSite(
        val path: String,
        val accessor: String,
    ) {
        override fun toString(): String = "$path[$accessor]"
    }

    /** An accessor, its detection pattern, WHY it can return cookie bytes, and a positive fixture. */
    private data class AccessorSpec(
        val pattern: Regex,
        val whyItCarriesCookieBytes: String,
        val positiveFixture: String,
    )

    private companion object {
        const val MAIN_SOURCE_ROOT = "src/main/kotlin"

        // Measured at 172 .kt files when CookieHeaderRuleOwnershipTest was written; re-measured above
        // that here. The floor catches a walk that reaches nothing, not the file count.
        const val MIN_EXPECTED_MAIN_FILES = 150

        const val HEADER_LIST = "headerList"
        const val PARAMETER_LIST = "parameterList"
        const val SINGLE_HEADER_LOOKUP = "singleHeaderLookup"
        const val RAW_MESSAGE = "rawMessage"
        const val COOKIE_JAR = "cookieJar"

        /**
         * THE MEASURED ACCESSOR SET. Every entry states WHY that call can return cookie bytes, because
         * an inventory whose membership rule is unstated cannot be extended correctly by the next
         * reader. `bodyToString()` is excluded on the reasoning in this class's KDoc, axis 2 — read it
         * before adding it.
         */
        val COOKIE_BYTE_ACCESSORS: Map<String, AccessorSpec> =
            mapOf(
                HEADER_LIST to
                    AccessorSpec(
                        pattern = Regex("""\.headers\(\)"""),
                        whyItCarriesCookieBytes =
                            "the header list contains `Cookie` on a request and `Set-Cookie` on a response",
                        positiveFixture = """val requestHeaders = sanitizeHeadersForPrompt(request.headers(), isRequest = true)""",
                    ),
                PARAMETER_LIST to
                    AccessorSpec(
                        pattern = Regex("""\.parameters\("""),
                        whyItCarriesCookieBytes =
                            "Burp parses the `Cookie` header into HttpParameterType.COOKIE entries, so the " +
                                "parameter list carries the same bytes under a different type",
                        positiveFixture = """request.parameters().filter { it.type().name == "COOKIE" }.forEach { param ->""",
                    ),
                SINGLE_HEADER_LOOKUP to
                    AccessorSpec(
                        pattern = Regex("""\.headerValue\("""),
                        whyItCarriesCookieBytes =
                            "it returns the raw value of one named header, which is the whole cookie string " +
                                "when the argument names a cookie header",
                        positiveFixture = """val cookieHeader = request.headerValue("Cookie")""",
                    ),
                RAW_MESSAGE to
                    AccessorSpec(
                        pattern = Regex("""(request|response)\(\)\??\.toString\(\)"""),
                        whyItCarriesCookieBytes =
                            "the serialized message contains the whole header block, cookie headers included",
                        positiveFixture = """request = request()?.toString() ?: "<no request>",""",
                    ),
                COOKIE_JAR to
                    AccessorSpec(
                        pattern = Regex("""cookieJar\(\)"""),
                        whyItCarriesCookieBytes =
                            "Burp's cookie jar IS the bytes. Measured on this tree, every `.cookies()` call " +
                                "is chained onto `cookieJar()` on the same line, so this one pattern is a " +
                                "complete detector for the jar accessor here",
                        positiveFixture = """val cookies = api.http().cookieJar().cookies()""",
                    ),
            )

        /** The string-literal argument of a `.headerValue("…")` call. */
        val HEADER_VALUE_ARGUMENT = Regex("""\.headerValue\("([^"]*)"\)""")

        const val EXECUTOR_MODERN = "com/six2dez/burp/aiagent/mcp/tools/McpToolExecutorImpl.kt"
        const val EXECUTOR_LEGACY = "com/six2dez/burp/aiagent/mcp/tools/McpToolLegacy.kt"
        const val SERIALIZATION = "com/six2dez/burp/aiagent/mcp/schema/Serialization.kt"
        const val CONTEXT_COLLECTOR = "com/six2dez/burp/aiagent/context/ContextCollector.kt"
        const val BOUNTY_TAG_RESOLVER = "com/six2dez/burp/aiagent/prompts/bountyprompt/BountyPromptTagResolver.kt"
        const val ACTIVE_SCANNER = "com/six2dez/burp/aiagent/scanner/ActiveAiScanner.kt"
        const val PASSIVE_HEURISTICS = "com/six2dez/burp/aiagent/scanner/PassiveAiScannerHeuristics.kt"
        const val PASSIVE_ANALYSIS = "com/six2dez/burp/aiagent/scanner/PassiveAiScannerAnalysis.kt"
        const val PASSIVE_FILTERS = "com/six2dez/burp/aiagent/scanner/PassiveAiScannerFilters.kt"
        const val INJECTION_EXTRACTOR = "com/six2dez/burp/aiagent/scanner/InjectionPointExtractor.kt"
        const val RESPONSE_ANALYZER = "com/six2dez/burp/aiagent/scanner/ResponseAnalyzer.kt"

        /**
         * RE-MEASURED at execution time on this tree, NOT copied from plan 27-08's baseline table.
         * Totals per accessor: headerList 25, parameterList 15, singleHeaderLookup 11, rawMessage 19,
         * cookieJar 2 — 72 sites across 11 files, which is what makes this registry tractable rather
         * than open-ended.
         */
        val MEASURED_CARRIER_SITES: Map<String, Map<String, Int>> =
            mapOf(
                EXECUTOR_MODERN to mapOf(HEADER_LIST to 2, PARAMETER_LIST to 3, RAW_MESSAGE to 5, COOKIE_JAR to 1),
                EXECUTOR_LEGACY to mapOf(HEADER_LIST to 2, PARAMETER_LIST to 3, RAW_MESSAGE to 5, COOKIE_JAR to 1),
                SERIALIZATION to mapOf(RAW_MESSAGE to 5),
                CONTEXT_COLLECTOR to mapOf(RAW_MESSAGE to 2),
                BOUNTY_TAG_RESOLVER to mapOf(PARAMETER_LIST to 1, RAW_MESSAGE to 2),
                ACTIVE_SCANNER to mapOf(HEADER_LIST to 5, SINGLE_HEADER_LOOKUP to 2),
                PASSIVE_HEURISTICS to mapOf(HEADER_LIST to 5, PARAMETER_LIST to 2, SINGLE_HEADER_LOOKUP to 5),
                PASSIVE_ANALYSIS to mapOf(HEADER_LIST to 5, PARAMETER_LIST to 1, SINGLE_HEADER_LOOKUP to 2),
                PASSIVE_FILTERS to mapOf(HEADER_LIST to 2, PARAMETER_LIST to 1),
                INJECTION_EXTRACTOR to mapOf(HEADER_LIST to 2, PARAMETER_LIST to 4, SINGLE_HEADER_LOOKUP to 2),
                RESPONSE_ANALYZER to mapOf(HEADER_LIST to 2),
            )

        const val EXPECTED_TOTAL_CARRIER_SITES = 72

        /** Measured 2026-08-25. `Cookie` ×3 is the population that matters; the rest are the control. */
        val MEASURED_HEADER_VALUE_ARGUMENTS: Map<String, Int> =
            mapOf(
                "Content-Type" to 6,
                "Cookie" to 3,
                "Origin" to 1,
                "Referer" to 1,
            )

        /**
         * Sites whose bytes reach a named control. The value names that control and, where the calls
         * behind one (file, accessor) pair do not share a disposition, enumerates the split by line —
         * see the fifth bound in the class KDoc for why that prose is not machine-checked.
         */
        val ROUTED_THROUGH: Map<CarrierSite, String> =
            mapOf(
                CarrierSite(EXECUTOR_MODERN, HEADER_LIST) to
                    "McpToolHelpers.sanitizeHeaders. Consumer read: :376 and :395 pass the list straight " +
                    "into it as ParsedRequest.headers / ParsedResponse.headers.",
                CarrierSite(EXECUTOR_LEGACY, HEADER_LIST) to
                    "McpToolHelpers.sanitizeHeaders. Consumer read: :184 and :207, the same two shapes.",
                CarrierSite(EXECUTOR_MODERN, PARAMETER_LIST) to
                    "McpToolHelpers.sanitizeParameters at :360 (params_extract) and :381 (request_parse). " +
                    "THE THIRD CALL, :406, IS NON-CARRYING: consumer read at :406-411, find_reflected " +
                    "uses the value only as a needle for countOccurrences and emits " +
                    "`name=… type=… count=…`, never the value itself.",
                CarrierSite(EXECUTOR_LEGACY, PARAMETER_LIST) to
                    "McpToolHelpers.sanitizeParameters at :160 (params_extract) and :189 (request_parse). " +
                    "THE THIRD CALL, :222, IS NON-CARRYING for the same reason: consumer read at " +
                    ":222-227, find_reflected emits name, type and an occurrence count only.",
                CarrierSite(EXECUTOR_MODERN, RAW_MESSAGE) to
                    "Redaction.apply at the redactIfNeeded choke point. Consumer read: all five sites " +
                    "(:592, :593, :732, :761, :811) sit inside the dispatch `when` whose result is " +
                    "wrapped at McpToolExecutorImpl.kt:1045. The logical-line cookie header rules fire " +
                    "there; SerializedEmissionSiteInventoryTest pins the same choke point.",
                CarrierSite(EXECUTOR_LEGACY, RAW_MESSAGE) to
                    "Redaction.apply at the redactIfNeeded choke point. Consumer read: all five sites " +
                    "(:460, :461, :622, :640, :690) sit inside mcpTool/mcpPaginatedTool registrations " +
                    "whose handler wraps output at McpTool.kt:45 and :78.",
                CarrierSite(SERIALIZATION, RAW_MESSAGE) to
                    "Redaction.apply at the redactIfNeeded choke point. Consumer read: :44, :45, :50, :59 " +
                    "and :82 build HttpRequestResponse / SiteMapEntry carriers, which only the two " +
                    "executors serialize, and those emissions are wrapped at the choke point above. " +
                    "This file emits nothing itself.",
                CarrierSite(CONTEXT_COLLECTOR, RAW_MESSAGE) to
                    "Redaction.apply, called directly. Consumer read: :41 and :45 are truncated and then " +
                    "passed to Redaction.apply at :51 and :52; only the redacted strings reach HttpItem.",
                CarrierSite(BOUNTY_TAG_RESOLVER, RAW_MESSAGE) to
                    "Redaction.apply, called directly. Consumer read: :77 and :78 are redacted at :79 and " +
                    ":80 into requestRedacted / responseRedacted, and every tag branch below reads only " +
                    "those.",
                CarrierSite(BOUNTY_TAG_RESOLVER, PARAMETER_LIST) to
                    "Redaction.isCookieParameterType, added by plan 27-07 (D-27-21). Consumer read: " +
                    "buildRequestParameters at :119 renders `name=value (TYPE)` and the type gate at " +
                    ":151 writes [STRIPPED] for a COOKIE-typed value under any stripCookies policy. " +
                    "NOTE, recorded rather than hidden: this tag value never passes Redaction.apply, so " +
                    "the control here is the type gate alone — a token in a URL/BODY-typed parameter " +
                    "VALUE is NOT covered (the wider defect recorded at that site in source).",
                CarrierSite(PASSIVE_ANALYSIS, HEADER_LIST) to
                    "Redaction.apply, via redactScanMetadata. Consumer read: :257 and :258 go through " +
                    "sanitizeHeadersForPrompt; :266 selects Cookie header values for cookieSectionLines " +
                    "and the `=== COOKIES ===` span rule; :274 collects auth headers; :286 reduces " +
                    "response headers to tech hints. All five feed buildScanMetadataText at :360, whose " +
                    "output is redacted at :380 before leaving the machine.",
                CarrierSite(PASSIVE_ANALYSIS, PARAMETER_LIST) to
                    "Redaction.cookieTypedParamRegex, via redactScanMetadata. Consumer read: :248 maps each " +
                    "parameter through formatParamLine (PassiveAiScannerPrompts.kt:34) into the " +
                    "`name=value (TYPE)` shape, which the rule matches at Redaction.kt:724 after :380's " +
                    "redactScanMetadata call. This is the ONE rendered shape that rule reaches; plan " +
                    "27-08 task 1 narrowed the rule's comment to say so and pinned the behaviour.",
            )

        /**
         * Sites whose bytes do NOT cross the process boundary, each with the reason read from that
         * site's own CONSUMER rather than from the call. `26-SECURITY.md` standing rule (i): presence
         * is not width, and a classification asserted from the call site alone is not a classification.
         */
        val CLASSIFIED_NON_CARRYING: Map<CarrierSite, String> =
            mapOf(
                CarrierSite(EXECUTOR_MODERN, COOKIE_JAR) to
                    "MODE-GATED AT THE FIELD. Consumer read: :476 reads the jar, and the map at :502-523 " +
                    "emits cookie.value() ONLY when `includeValues && privacyMode == OFF`, writing " +
                    "[REDACTED] otherwise; the domain is additionally anonymised under STRICT. Under " +
                    "every redacting policy the value never enters CookieEntry.",
                CarrierSite(EXECUTOR_LEGACY, COOKIE_JAR) to
                    "MODE-GATED AT THE FIELD, byte-identical logic. Consumer read: :316 reads the jar and " +
                    ":348-353 applies the same `includeValues && privacyMode == OFF` gate.",
                CarrierSite(ACTIVE_SCANNER, HEADER_LIST) to
                    "LOCAL ANALYSIS and TARGET-BOUND MUTATION, no AI-facing emission. Consumers read: :933 " +
                    "hasAuthContext reduces to a Boolean; :943 stripAuthHeaders REMOVES the matched " +
                    "headers and the Cookie header outright; :995 buildFullResponse is used only for " +
                    "`.contains(payload.value)` tests at :873-884; :1025 responseHeaderValue serves " +
                    "isCacheable / isCacheHit Booleans; :1387 rebuilds a request sent to the TARGET, " +
                    "not to a backend.",
                CarrierSite(ACTIVE_SCANNER, SINGLE_HEADER_LOOKUP) to
                    "BOTH ARE `headerValue(\"Cookie\")` AND BOTH ARE NON-EMITTING. Consumers read: :936 " +
                    "feeds `authCookieHint.containsMatchIn(...)`, a Boolean; :1411 rewrites the cookie " +
                    "string and sends it to the TARGET via withAddedHeader. This pair is why the " +
                    "argument multiset is pinned separately: they are the only cookie-named lookups in " +
                    "the tree that a count alone would not distinguish from a new one.",
                CarrierSite(PASSIVE_HEURISTICS, HEADER_LIST) to
                    "LOCAL ANALYSIS producing LocalFinding objects with FIXED detail strings. Consumers " +
                    "read: :66 detectRequestSmuggling compares Content-Length / Transfer-Encoding " +
                    "values; :101 and :107 reduce to Booleans; :116 reduces Set-Cookie values to a " +
                    "sameSiteSecure Boolean; :186 reduces a Location header to a filename containment " +
                    "test. No header value reaches a LocalFinding.detail.",
                CarrierSite(PASSIVE_HEURISTICS, PARAMETER_LIST) to
                    "LOCAL ANALYSIS. Consumers read: :106 tests parameter NAMES against csrfTokenRegex; " +
                    ":139 tests a name and a value prefix (`rO0AB` / `aced0005`) and returns a fixed " +
                    "detail string. Neither emits a value.",
                CarrierSite(PASSIVE_HEURISTICS, SINGLE_HEADER_LOOKUP) to
                    "LOCAL ANALYSIS. Consumers read: :102 `headerValue(\"Cookie\")` reduces to " +
                    "`authCookieHint.containsMatchIn`, a Boolean; :110 and :111 (Origin, Referer) are " +
                    "null/blank tests; :149 and :173 (Content-Type) are containment tests. The one " +
                    "cookie-named lookup here never leaves the function as text.",
                CarrierSite(PASSIVE_ANALYSIS, SINGLE_HEADER_LOOKUP) to
                    "NON-CARRYING BY ARGUMENT. Consumer read: :356 and :357 both pass \"Content-Type\", " +
                    "feeding buildCompactRequestBody / buildCompactResponseBody, which select a body " +
                    "formatter. A Content-Type value cannot be a cookie.",
                CarrierSite(PASSIVE_FILTERS, HEADER_LIST) to
                    "ADMISSION AND HASHING. Consumers read: :80 hasInterestingResponseHeaders tests header " +
                    "NAMES against an allowlist and returns a Boolean; :147 buildResponseFingerprint " +
                    "passes the sanitized list into sha256Hex at :159, so only a digest survives as a " +
                    "dedup cache key. Neither emits a header value.",
                CarrierSite(PASSIVE_FILTERS, PARAMETER_LIST) to
                    "ARITY ONLY. Consumer read: :60 calls `.isNotEmpty()` inside shouldSkipAiAnalysis; the " +
                    "parameter objects are discarded.",
                CarrierSite(INJECTION_EXTRACTOR, HEADER_LIST) to
                    "ALLOWLISTED INJECTION POINTS, and the allowlist cannot admit a cookie header on the " +
                    "AI-facing path. Consumers read: :33 admits a header only when headerAllowlist " +
                    "contains its lowercased name; :185 matches a header by raw-byte offset for a " +
                    "user selection. Both produce InjectionPoint values, which share the disposition " +
                    "recorded for this file's PARAMETER_LIST entry below.",
                CarrierSite(INJECTION_EXTRACTOR, PARAMETER_LIST) to
                    "TWO CONSUMERS, BOTH READ, AND THEY DIFFER — which is precisely the kind of thing this " +
                    "inventory exists to surface. :21 (URL), :25 (BODY), :29 (COOKIE — the site with " +
                    "its own `it.type().name == \"COOKIE\"` predicate that plan 27-07 baseline B9 " +
                    "measured and deliberately left unconverted) and :162 all build " +
                    "InjectionPoint.originalValue. CONSUMER 1, the AI-facing one: " +
                    "AdaptivePayloadEngine.kt:52 substitutes `[REDACTED_VALUE]` for the value under " +
                    "ANY non-OFF privacy mode before it reaches a prompt — a control by a DIFFERENT " +
                    "mechanism than every other entry in this registry. CONSUMER 2: " +
                    "ActiveAiScanner.kt:1239 writes `Original Value: <originalValue>` UNREDACTED into " +
                    "an AuditIssue detail, which Serialization.kt:14 copies into IssueDetails.detail " +
                    "and the scanner_issues MCP tool emits. Consumer 2 is a TRANSITIVE carrier — the " +
                    "third blind axis in this class's KDoc — and it is NOT controlled here. See " +
                    "ISSUE_DETAIL_CARRIER_DISPOSITION below for its measured status. This entry is " +
                    "CLASSIFIED rather than ROUTED because a route with an uncontrolled consumer is " +
                    "not a route.",
                CarrierSite(INJECTION_EXTRACTOR, SINGLE_HEADER_LOOKUP) to
                    "NON-CARRYING BY ARGUMENT. Consumer read: :45 and :207 both pass \"Content-Type\" and " +
                    "use it to choose a JSON or XML field extractor.",
                CarrierSite(RESPONSE_ANALYZER, HEADER_LIST) to
                    "PATTERN MATCHING, with one narrow transitive tail recorded rather than waved off. " +
                    "Consumer read: :616 and :623 build modifiedHeaders / originalHeaders, which are " +
                    "concatenated with the body and consumed by isFalsePositive, analyzeErrorBased, " +
                    "analyzeReflection and analyzeContentBased — all `containsMatchIn` / `find` tests " +
                    "against per-vuln-class error and success signatures. THE TAIL: a MATCHED substring " +
                    "of such a signature, capped at 80 characters, can be written into " +
                    "VulnConfirmation.evidence, which ActiveAiScanner.kt:1246 puts in the same " +
                    "AuditIssue detail as consumer 2 above. It is the same transitive route, reachable " +
                    "only by a cookie value that matches a vuln-class error signature.",
            )

        /**
         * The disposition of the transitive issue-detail carrier, recorded HERE because this is where a
         * reader of the InjectionPointExtractor classification needs it.
         *
         * PROVISIONAL while plan 27-08 task 2 stands alone. Task 3 measures the route and replaces this
         * constant with the classification its measurement supports. A registry that guessed the answer
         * before running the probe would be the assumption this phase keeps paying for, one iteration
         * smaller.
         */
        const val ISSUE_DETAIL_CARRIER_DISPOSITION =
            "MEASURED-IN-27-08-TASK-3: whether a cookie sentinel embedded in the `Original Value:` line " +
                "survives Redaction.apply on the serialized IssueDetails shape, and under what " +
                "reachability conditions, is not yet measured. Do not read this entry as either a leak " +
                "or a clean result until task 3 replaces this text."
    }
}
