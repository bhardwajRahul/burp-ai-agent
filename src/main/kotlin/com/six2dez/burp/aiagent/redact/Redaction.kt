package com.six2dez.burp.aiagent.redact

import com.six2dez.burp.aiagent.config.Defaults
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

data class RedactionPolicy(
    val stripCookies: Boolean,
    val redactTokens: Boolean,
    val anonymizeHosts: Boolean,
) {
    companion object {
        fun default() =
            RedactionPolicy(
                stripCookies = true,
                redactTokens = true,
                anonymizeHosts = true,
            )

        fun fromMode(mode: PrivacyMode): RedactionPolicy =
            when (mode) {
                PrivacyMode.STRICT ->
                    RedactionPolicy(
                        stripCookies = true,
                        redactTokens = true,
                        anonymizeHosts = true,
                    )
                PrivacyMode.BALANCED ->
                    RedactionPolicy(
                        stripCookies = true,
                        redactTokens = true,
                        anonymizeHosts = false,
                    )
                PrivacyMode.OFF ->
                    RedactionPolicy(
                        stripCookies = false,
                        redactTokens = false,
                        anonymizeHosts = false,
                    )
            }
    }
}

enum class PrivacyMode {
    STRICT,
    BALANCED,
    OFF,
    ;

    companion object {
        fun fromString(raw: String?): PrivacyMode = entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: BALANCED
    }
}

object Redaction {
    private val authHeaderRegex =
        Regex(
            "(?im)^(" +
                "authorization|proxy-authorization|" +
                "x-api-key|api-key|x-api-secret|api-secret|x-client-secret|" +
                "x-auth-token|auth-token|x-access-token|access-token|" +
                "x-session-token|session-token|x-csrf-token|csrf-token|x-xsrf-token" +
                "):\\s*.+$",
        )

    // The trailing =* captures base64/base64url padding on the token. The token char-class
    // excludes '=', so =* greedily consumes ANY run of '=' immediately after the token — including
    // '=' that merely follow the credential (WR-04). This is intentional and fail-safe: it
    // over-redacts trailing '=' rather than risk leaking part of a padded token. The whole
    // Authorization header is already replaced by authHeaderRegex; bearerRegex additionally covers
    // bearer tokens embedded in bodies/JSON/free text, where a few trailing '=' being swallowed is
    // a benign over-redaction, not a leak.
    private val bearerRegex = Regex("(?i)bearer\\s+[A-Za-z0-9\\-\\._~\\+\\/]+=*")
    private val basicAuthRegex = Regex("(?i)basic\\s+[A-Za-z0-9\\+\\/=]+")
    private val cookieHeaderRegex = Regex("(?im)^cookie:\\s*.+$")
    private val setCookieHeaderRegex = Regex("(?im)^set-cookie:\\s*.+$")

    /**
     * The passive scanner's dedicated cookie-section header.
     *
     * Public, and owned HERE in the redactor rather than in the emitter, because
     * scanner/PassiveAiScannerPrompts.kt imports this constant instead of writing the literal
     * inline. A future rename of the section is then a COMPILE ERROR rather than the silent
     * disabling of a security control — which is the standing objection to any section-scoped
     * redaction rule, converted into a compile-time coupling. The parity half of that coupling is
     * PassiveAiScannerPromptRedactionTest.emittedBlobContainsTheSectionConstant_parity, which turns
     * a silent format change AROUND the constant into a test failure.
     */
    const val COOKIE_SECTION_HEADER = "=== COOKIES ==="

    // (PRIV-05) SC1 / D-09: one name=value pair inside a cookie section. [^=\r\n]+ stops at the
    // FIRST '=', so a value that itself contains '=' (base64 padding) is consumed whole by the
    // trailing .* and cannot survive as a fragment.
    private val cookieSectionPairRegex = Regex("(?m)^([^=\\r\\n]+)=(.*)$")

    // (PRIV-05) SC1: the prefix that begins the NEXT prompt section, and now the only content
    // terminator of a cookie span. It replaces the previous (?m)^===  regex, which CR-03 dropped:
    // a regex search can scan to end-of-text on every occurrence of the header, whereas a
    // startsWith at an already-known line start is O(1) and cannot.
    private const val NEXT_SECTION_PREFIX = "=== "

    // (PRIV-05) CR-01 / T-21-21: the maximum number of lines one cookie section may span.
    // PassiveAiScannerAnalysis emits at most COOKIES_MAX_COUNT = 6 entry lines, so 16 is roughly
    // 2.5x slack. It is a BOUND, not a contract: redactCookieSections also runs over arbitrary MCP
    // tool output through McpToolContext.redactIfNeeded, where ANY occurrence of the header is
    // attacker-planted, and this constant is what bounds the over-redaction blast radius of such a
    // plant to 16 lines where the rule previously ran on "to the next blank line". Named rather
    // than inline because detekt's MagicNumber ignore list stops at 2 and QUAL-07 forbids growing
    // detekt-baseline.xml.
    private const val MAX_COOKIE_SECTION_LINES = 16

    // (PRIV-06) CR-03 / D-02 / T-21-29: the wall-clock ceiling for redactCookieSections across ALL
    // occurrences within a single call. Sized so it is unreachable by any realistic input now that
    // the rule is O(n) — reaching it takes tens of megabytes, at which size bodyStage's own
    // MAX_REDACTION_BUDGET_MS is already dropping the tail — while still bounding the worst case on
    // a pathological input. Before CR-03 this was the ONLY rule this phase added that bypassed both
    // SafeRegex and MAX_REDACTION_BUDGET_MS entirely: no budget, no marker, no seam.
    private const val COOKIE_SECTION_BUDGET_MS = 250L

    // (PRIV-05) SC1 / D-09 / D-10: redacts the VALUE of every name=value pair inside EVERY
    // [COOKIE_SECTION_HEADER] span, preserving the names. The scanner splits the Cookie: header on
    // ';' into a dedicated section, dropping the prefix the two header rules above key on — that is
    // the PRIV-05 leak, and it is a pattern-reach problem, not a call-site problem.
    //
    // D-09 — EVERY value, not only sensitive-named ones. Cookies are near-universally
    // session-bearing, and name-based selectivity is precisely what produced PRIV-05. Names are
    // preserved deliberately: COOKIES_MAX_COUNT bounds the section to six lines, so this costs the
    // model at most six opaque values while keeping all six names, which is the analytically useful
    // part (ScanKnowledgeBase.recordAuthInfo already records authCookieNames separately for exactly
    // this reason). Full-line stripping in the style of the two header rules above would destroy
    // the names and is therefore wrong here.
    //
    // D-10 — EVERY occurrence of the header, never only the first. This is a SECURITY requirement
    // with a demonstrated exploit behind it, not a robustness nicety. ScanKnowledgeBase.recordTechStack
    // populates the technology list from the response's Server, X-Powered-By, X-AspNet-Version and
    // X-Generator headers — all attacker-controlled — and buildContextSummary emits that list into
    // the prior-knowledge block, which the emitter appends BEFORE the cookie section. A Server
    // header carrying the section header therefore plants a decoy section ahead of the real one,
    // and a first-occurrence-only indexOf was measured redacting the decoy while the genuine
    // JSESSIONID and abtest_bucket values leaked intact. The while loop below is that fix;
    // RedactionTest.cookieSectionDecoyDoesNotShieldRealSection is its named regression guard.
    //
    // ACCEPTED RESIDUAL: an attacker who injects the section header into a response header causes
    // EXTRA redaction of their own response content. That is an over-redaction nuisance, never a
    // leak, and it is strictly better than the alternative. MAX_COOKIE_SECTION_LINES now bounds
    // that blast radius to 16 lines, where it was previously "to the next blank line".
    //
    // CR-03 — ONE PASS, not one rebuild per occurrence. This loop previously rebuilt the entire
    // string on every occurrence of the header (substring + replaced + substring), an O(n) copy per
    // occurrence and therefore O(k*n) overall, with k attacker-controlled: buildScanMetadataText
    // emits response headers and body verbatim and McpToolContext.redactIfNeeded runs this rule
    // over raw MCP tool output. Measured 65 ms at 64 KB rising a clean 4x per doubling to 2 631 ms
    // at 512 KB, extrapolating to ~42 s at the MCP default maxBodyBytes of 2 MiB, on an
    // uninterruptible worker thread. It is now one StringBuilder and one monotone cursor.
    //
    // TERMINATION AND COMPLEXITY. bodyStart is strictly greater than the header index h, and
    // cookieSectionEnd clamps its result to at least bodyStart, so cursor advances by at least
    // COOKIE_SECTION_HEADER.length every iteration and the loop always terminates. The appended
    // spans are disjoint and cookieSectionEnd only ever reads FORWARD from the cursor, so no
    // character is examined twice: the pass is O(n) in the input length. That is the whole of CR-03,
    // and it is why the two independent terminator searches were replaced by a single line walk.
    //
    // FAIL CLOSED, NOT OPEN. On deadline expiry the remaining characters are DROPPED behind one
    // windowDroppedMarker rather than passed through. The alternative — capping the section count
    // and letting the remainder through — is fail-open, and that remainder can contain whole
    // unredacted cookie sections, which is the exact failure class this phase exists to remove.
    // Dropping the tail is the D-02 discipline the body stage already follows, and the marker is
    // reused verbatim rather than given a third shape: its four wording properties hold unchanged.
    //
    // (T-21-23) Every piece of per-call state below — builder, cursor, deadline — is a LOCAL.
    // Redaction is a stateless object called concurrently from scanner and MCP tool threads, and
    // this rule adds no object-level field.
    private fun redactCookieSections(
        text: String,
        budgetMs: Long = COOKIE_SECTION_BUDGET_MS,
    ): String {
        val sb = StringBuilder(text.length)
        val deadline = System.nanoTime() + budgetMs * NANOS_PER_MS
        var cursor = 0
        while (true) {
            // Subtraction rather than a direct comparison, so the check is immune to nanoTime
            // wraparound; ">= 0" rather than "> 0" so an injected budget of 0 ms is deterministically
            // expired on the first iteration even on a coarse clock. That determinism is what makes
            // the fail-closed path assertable without a tens-of-megabytes fixture.
            if (System.nanoTime() - deadline >= 0L) {
                val dropped = text.length - cursor
                sb.append(windowDroppedMarker(dropped))
                maybeLogTruncation(System.currentTimeMillis(), dropped.toLong())
                // Returns WITHOUT the remainder: the tail is dropped, never passed through.
                return sb.toString()
            }
            val h = text.indexOf(COOKIE_SECTION_HEADER, cursor)
            if (h < 0) break
            val bodyStart = h + COOKIE_SECTION_HEADER.length
            val end = cookieSectionEnd(text, bodyStart)
            sb.append(text, cursor, bodyStart)
            sb.append(
                text.substring(bodyStart, end).replace(cookieSectionPairRegex) { "${it.groupValues[1]}=[REDACTED]" },
            )
            cursor = end
        }
        sb.append(text, cursor, text.length)
        return sb.toString()
    }

    // (PRIV-05) CR-01 / SC1 / T-21-28: the end index of the cookie section whose header ends at
    // [bodyStart]. This function IS the CR-01 fix.
    //
    // THE DEFECT IT CLOSES. COOKIE_SECTION_HEADER carries no trailing newline while the emitter uses
    // appendLine, so [bodyStart] is the header line's OWN newline. The previous bound searched for
    // "\n\n" starting AT [bodyStart], so a blank FIRST cookie entry matched at [bodyStart] itself:
    // the span collapsed to the empty string and EVERY cookie value in the section reached the AI
    // backend unredacted. A blank entry further down truncated the span there instead, leaking every
    // cookie below it. The scanner really does produce blank entries — it splits the Cookie: header
    // on ';' with no blank filter — so this was a live leak, not a latent one. Starting the walk at
    // the line AFTER the header is what closes it.
    //
    // BLANK LINES ARE SKIPPED, NEVER TERMINATORS. That closes the mid-list half, which a fix that
    // merely moved the search start would leave leaking. It is safe precisely because a blank line
    // holds no name=value pair, so extending a span across one redacts nothing extra.
    //
    // WHY ONE FORWARD WALK, replacing the previous pair of independent searches ("\n\n" and the
    // (?m)^===  regex). Two searches were the second half of CR-03: EACH can scan to the end of the
    // text even when the other finds a near terminator, which kept the enclosing loop O(k*n) even
    // once the per-occurrence rebuild was removed. This walk is strictly monotone — it never
    // examines a character before the cursor — so total work across every occurrence is O(n). That
    // is the correctness argument behind the complexity claim; do not reintroduce the searches.
    @Suppress("ReturnCount")
    private fun cookieSectionEnd(
        text: String,
        bodyStart: Int,
    ): Int {
        val headerNewline = text.indexOf('\n', bodyStart)
        val scanFrom = if (headerNewline >= 0) headerNewline + 1 else bodyStart
        var p = scanFrom
        var lines = 0
        while (p < text.length && lines < MAX_COOKIE_SECTION_LINES) {
            if (text.startsWith(NEXT_SECTION_PREFIX, p)) return p
            val newline = text.indexOf('\n', p)
            if (newline < 0) return text.length
            // p == newline means this line is blank: advance past it WITHOUT terminating.
            p = newline + 1
            lines++
        }
        // Clamped so a span can never have negative length regardless of where the walk stopped.
        return maxOf(p, bodyStart)
    }

    // Internal test seam — runs the cookie-section rule under an INJECTED budget. Not part of the
    // public API; referenced only from the test source set, in the style of the
    // resetTruncationWindowForTest and testHkdfExtract seams.
    //
    // It exists because the deadline is otherwise reachable only with a tens-of-megabytes fixture,
    // which would make the fail-closed assertion slow and dependent on machine speed. The project's
    // established answer to that is an injected bound — the same reason maybeLogTruncation takes
    // nowMs as a parameter instead of reading the clock itself.
    internal fun testRedactCookieSections(
        text: String,
        budgetMs: Long,
    ): String = redactCookieSections(text, budgetMs)

    /**
     * (PRIV-05) CR-01 / T-21-32: makes cookie [entries] safe to emit inside a
     * [COOKIE_SECTION_HEADER] section.
     *
     * This closes the one CR-01 trigger the redactor cannot close on its own. A cookie element
     * shaped like `=== FOO ===` terminates the section span, and that is IN-BAND SIGNALLING: the
     * span terminator is necessarily derived from content sitting inside the region it protects, so
     * no redactor-side rule can distinguish a planted section header from a genuine one. Refusing
     * to terminate on `=== ` instead would hand an attacker the opposite primitive, where one
     * planted line swallows the remainder of the prompt.
     *
     * Exported from `redact/` rather than written in the scanner for the same reason
     * [COOKIE_SECTION_HEADER] is owned here: the redactor owns the invariant its own rule depends
     * on, so the coupling cannot be broken silently from another package.
     *
     * Applied per entry, in this order: an entry blank after trimming is dropped; every CR and LF
     * inside an entry becomes a single space, so one entry can never become two emitted lines; and
     * an entry still beginning with `===` receives a single leading space. That last step is the
     * point — a leading space makes the line unable to match `^=== `, and the value is PRESERVED
     * rather than dropped, so nothing analytically useful is lost.
     *
     * Removing the call in `PassiveAiScannerPrompts.buildScanMetadataText` re-opens PRIV-05; the
     * named guard is the end-to-end test plan 21-10 adds. Nothing in plan 21-08 calls this yet,
     * which is deliberate — it keeps the two `Redaction.kt` edits in separate waves.
     */
    fun sanitizeCookieSectionEntries(entries: List<String>): List<String> =
        entries
            .filter { it.isNotBlank() }
            .map { entry ->
                val flattened = entry.replace('\r', ' ').replace('\n', ' ')
                if (flattened.startsWith("===")) " $flattened" else flattened
            }

    // (PRIV-05) SC2 / D-09: a COOKIE-typed parameter line — the second entry point of the same
    // leak, reached through request.parameters(). Parameters are emitted as "name=value (TYPE)"
    // where TYPE is the Montoya HttpParameterType name (formatParamLine in
    // scanner/PassiveAiScannerPrompts.kt), so keying on the semantic type label rather than on a
    // section header makes this rule CONTEXT-FREE: it survives a section rename and works wherever
    // the shape appears. The asymmetry with the section rule above is deliberate — that section has
    // no discriminator other than its header, whereas a parameter line carries one in its own text.
    //
    // The context-free "^name=value$" alternative was rejected on CAPABILITY, not merely on
    // over-redaction: the trailing type suffix defeats the end anchor, so it cannot satisfy SC2 at
    // all — and it additionally mangles x=1, DEBUG=true and AAAA== when they stand on their own
    // line. Group 3 is the suffix and is written back verbatim, so name and type label both survive.
    private val cookieTypedParamRegex = Regex("(?im)^([^=\\r\\n]+)=(.*?)(\\s\\(COOKIE\\))\\s*$")

    // very generic JWT-like pattern (not perfect by design)
    private val jwtRegex = Regex("\\beyJ[A-Za-z0-9_\\-]+\\.[A-Za-z0-9_\\-]+\\.[A-Za-z0-9_\\-]+\\b")

    // Sensitive parameter/key name vocabulary — shared by urlTokenParamRegex, formBodyParamRegex,
    // and jsonSecretKeyRegex so query-string and body coverage stay consistent (PRIV-02).
    // (PRIV-05) SC3 / D-11: the vocabulary is byte-identical to the v0.6.0 value and is
    // deliberately NOT widened. The 31-key SC3 must-redact corpus is satisfied by the boundary
    // rule below plus the vendor list, without adding a single word; every extra word multiplies
    // the false-positive surface across all THREE consumer regexes at once (Pitfall 10). Add a
    // word only with measured evidence in query-string, form-body and JSON contexts.
    private const val SENSITIVE_WORDS =
        "access_token|api_key|apikey|auth|token|key|secret|password|pwd|session|sid|code"

    // (PRIV-05) SC3 / D-11: vendor and framework session-cookie names that NO morphological rule
    // can catch, because the sensitive word is concatenated without a separator (JSESSIONID,
    // PHPSESSID, csrftoken) or is absent from the name entirely (remember_me). Whole-key match;
    // case-insensitive via each consumer's leading (?i). Dots are escaped.
    private const val KNOWN_SESSION_KEYS =
        "jsessionid|phpsessid|asp\\.net_sessionid|\\.aspxauth|aspxauth|csrftoken|" +
            "remember_me|remember_token|laravel_session|ci_session|_session_id|sessionid|sessid|" +
            "cfid|cftoken|xsrf-token|_csrf"

    // (PRIV-05) SC3 / D-11: the character class a key name may be built from. '[' and ']' cover
    // PHP array parameters of the a[b] form; '.' covers connect.sid and ASP.NET_SessionId.
    private const val KEY_CHARS = "[A-Za-z0-9_.\\-\\[\\]]"

    // (PRIV-05) SC3 / D-13: the token-boundary constructs placed immediately before and after the
    // matched word. First alternative = the separator rule ('_', '-', '.', '[', ']' and the string
    // boundaries delimit a whole token). Second alternative = the camelCase rule, so authToken,
    // accessToken and userSessionId match.
    // NON-OBVIOUS BLOCKER: the camelCase half CANNOT be written inline under the consumers' (?i) —
    // case-insensitivity makes [A-Z] match lowercase too, so every position would qualify and the
    // boundary would degrade to a substring match. Java's inline flag-off group (?-i:...) is what
    // makes it work; verified on JDK 21.
    // REVERT POINT (one deletion each): removing the (?-i:...) alternative from WORD_BEFORE and
    // WORD_AFTER reverts D-13 entirely. That loses nothing SC3 requires and removes the three
    // accepted over-redactions recorded below (codeName, keyName, tokenCount).
    private const val WORD_BEFORE = "(?:(?<![A-Za-z0-9])|(?-i:(?<=[a-z0-9])(?=[A-Z])))"
    private const val WORD_AFTER = "(?:(?![A-Za-z0-9])|(?-i:(?<=[a-z0-9])(?=[A-Z])))"

    // (PRIV-05) SC3 / D-11: the shared sensitive-KEY expression consumed by urlTokenParamRegex,
    // formBodyParamRegex and jsonSecretKeyRegex. It replaces the old exact-word alternation, which
    // demanded the key be EXACTLY one of the twelve words — that is why auth_token missed (auth was
    // followed by '_', not '='), and why only a cookie literally named "session" was ever caught.
    //
    // (a) A key is sensitive when it IS a known vendor name, or when it CONTAINS one of the words
    //     as a whole token, treating '_', '-', '.', '[', ']', the string boundaries and
    //     lower-to-upper case transitions as delimiters. So auth_token, api-key, X-Session-Id and
    //     connect.sid match, while keyboard_layout, codename, sidebar and keychain do not.
    // (b) Prefix and suffix are BOUNDED at 64 characters: an unbounded '*' measured 2.3x slower on
    //     adversarial input (21 ms vs 9 ms on 1 MB) and reintroduces exactly the unbounded-
    //     quantifier adjacency this file already warns about above formBodyParamRegex.
    // (c) EVERY internal group is non-capturing, so the group numbering of all three consumers is
    //     unchanged and their existing replacement expressions still reproduce the key exactly
    //     (Pitfall 7).
    //
    // D-12: there is deliberately NO benign-key denylist. The recommendation proposed one;
    // measurement found nothing to guard against — all 21 must-not-redact keys pass on the
    // boundary rule alone. Do not add one: every entry in such a list is a place where a real
    // credential could be accidentally allowlisted.
    //
    // Two deliberate residuals:
    //   - PLURAL forms (codes, tokens, keys) are NOT handled. Adding 's?' to the vocabulary would
    //     catch them at the cost of a second widening axis; SC3 does not require it.
    //   - ACCEPTED over-redactions, all fail-safe and all analytically low-value: under the
    //     separator rule token_bucket_size, session_timeout_seconds, auth_provider, key_size,
    //     code_version, secret_santa, password_hint_enabled; under the camelCase rule (D-13)
    //     codeName, keyName, tokenCount. auth_provider is the only one with real analytic value
    //     and redacting it is still the correct default for a key literally named auth_*.
    private const val SENSITIVE_KEY_EXPR =
        "(?:(?:$KNOWN_SESSION_KEYS)|" +
            "$KEY_CHARS{0,64}$WORD_BEFORE(?:$SENSITIVE_WORDS)$WORD_AFTER$KEY_CHARS{0,64})"

    // Tokens/secrets in URL query strings, e.g. ?access_token=xyz or &api_key=xyz
    // (PRIV-05) SC3: the key side is the shared key expression, so compound and vendor keys
    // (auth_token, api-key, X-Session-Id, JSESSIONID) are caught here too. Group 1 is still the
    // whole "[?&]key=" prefix, so the "$1[REDACTED]" replacement in apply() is unchanged.
    private val urlTokenParamRegex =
        Regex(
            "(?i)([?&](?:$SENSITIVE_KEY_EXPR)=)[^&\\s\"'<>]+",
        )

    // (PRIV-02) x-www-form-urlencoded field ANYWHERE in a body, INCLUDING the leading field.
    // The (^|[?&]) anchor closes the documented gap: the old [?&]-only urlTokenParamRegex
    // missed "apikey=sk-abc123&user=bob" (no leading ? or &). (?im) = multiline+case-insensitive.
    // The value charclass [^&\s"'<>]+ is bounded — no trailing anchor that would backtrack (Pitfall 3).
    // (PRIV-05) SC3: group 1 is still the ^/?/& prefix and group 2 is still the WHOLE key, so the
    // "$1$2=[REDACTED]" lambda in apply() reproduces compound keys such as auth_token exactly.
    private val formBodyParamRegex =
        Regex(
            "(?im)(^|[?&])($SENSITIVE_KEY_EXPR)=[^&\\s\"'<>]+",
        )

    // (PRIV-02) JSON values for known-sensitive key names.
    // Key-scoped: only a value following a matching key name is redacted; "name":"alice" is untouched.
    // The value side (group 2) covers a quoted string OR an unquoted JSON scalar (boolean, null, or
    // number) so numeric/boolean secrets such as {"token":12345,"pin":123456} are not missed (WR-03).
    // Whatever the original value type, it is replaced with the quoted token "[REDACTED]", which keeps
    // the output valid JSON.
    // Limitation: a value containing an escaped quote (e.g. "token":"ab\"cd") will be partially
    // matched (stops at the backslash). This is an accepted limitation — real API tokens are
    // [A-Za-z0-9._-] and do not contain embedded quotes; use a JSON parser if full coverage is needed.
    // (PRIV-05) SC3: the key side is the shared key expression, which is already (?:...)-wrapped —
    // do not wrap it again here. Group 1 is still "key": including whitespace and group 2 is still
    // the value, so the "$1\"[REDACTED]\"" lambda in apply() is unchanged.
    private val jsonSecretKeyRegex =
        Regex(
            "(?i)(\"$SENSITIVE_KEY_EXPR\"\\s*:\\s*)(\"[^\"]*\"|true|false|null|-?\\d+(?:\\.\\d+)?)",
        )

    // (PRIV-02) Custom user patterns compiled by setCustomPatterns. Volatile so writes from the
    // EDT (save) are immediately visible to the redaction thread (apply) without full synchronization.
    @Volatile
    private var compiledCustomPatterns: List<Pattern> = emptyList()

    /**
     * Sets the list of user-supplied custom redaction patterns. Each string is compiled as a
     * java.util.regex.Pattern; entries that fail to compile (PatternSyntaxException) are silently
     * dropped. Passing an empty list clears all custom patterns.
     *
     * Call this from applyAndSaveSettings after the persisted list has been validated by
     * SafeRegex.isPatternSafe so the patterns in this list are already known-safe.
     */
    fun setCustomPatterns(patterns: List<String>) {
        compiledCustomPatterns =
            patterns.mapNotNull { raw ->
                try {
                    Pattern.compile(raw)
                } catch (_: PatternSyntaxException) {
                    null // silently skip uncompilable patterns
                }
            }
    }

    /**
     * (PRIV-06) D-03: optional sink for the truncation notice.
     *
     * Wired in App.initialize to api.logging()::logToOutput, beside the other diagnostics sinks. It
     * is null in tests and in headless contexts and the redaction pipeline never depends on it —
     * a missing sink costs the user visibility, never correctness.
     *
     * @Volatile because the write happens on the EDT at startup while the reads happen on scanner
     * threads and MCP tool threads. Modelled on backends/BackendDiagnostics.output, which is the
     * house idiom for a settable diagnostic sink on an otherwise stateless object.
     *
     * This callback is why `redact/` gains NO new dependency for D-03. Routing the notice through
     * the project's audit log was ruled out on two counts: this package is deliberately
     * dependency-light and free of any UI toolkit import, and audit logging is off by default here,
     * so an audit-only signal would be invisible to the very user it exists to warn.
     */
    @Volatile
    var truncationLogger: ((String) -> Unit)? = null

    // (PRIV-06) D-03: rate-limiter state for [maybeLogTruncation]. Two AtomicLongs rather than a
    // lock, so the read-then-CAS below stays allocation-free on the redaction hot path. These are
    // the ONLY object-level fields this phase adds — all window-loop state is local (T-21-23).
    private val lastTruncationLogMs = AtomicLong(0L)

    private val suppressedTruncations = AtomicLong(0L)

    // (PRIV-06) D-03: the notice window, the same length as PassiveAiScannerAnalysis's
    // BACKOFF_LOG_INTERVAL_MS so the two Output-tab limiters cannot drift apart.
    private const val TRUNCATION_LOG_INTERVAL_MS = 10_000L

    /**
     * (PRIV-06) D-03: emits at most one truncation notice per [TRUNCATION_LOG_INTERVAL_MS] window,
     * reporting how many further notices were suppressed since the previous one.
     *
     * [nowMs] is a PARAMETER and the system clock is never read inside, which is the convention
     * established by `PassiveAiScannerAnalysis.maybeLogBackoff(nowMs, untilMs)`: it makes the window
     * assertable without sleeping. That limiter is the model here, deliberately and not
     * `CliBackend.availabilityLogged`, whose semantics are once-ever rather than windowed.
     *
     * [droppedChars] is a count, never the dropped text. The emitted line carries counts only and
     * can therefore never echo attacker-controlled content into the Output tab (T-21-22).
     */
    internal fun maybeLogTruncation(
        nowMs: Long,
        droppedChars: Long,
    ) {
        val prev = lastTruncationLogMs.get()
        // Short-circuits so the CAS is only attempted once the window has actually elapsed. Losing
        // the CAS means a concurrent thread emitted for this window, so this call is a suppression.
        if (nowMs - prev < TRUNCATION_LOG_INTERVAL_MS || !lastTruncationLogMs.compareAndSet(prev, nowMs)) {
            suppressedTruncations.incrementAndGet()
            return
        }
        val suppressed = suppressedTruncations.getAndSet(0L)
        truncationLogger?.invoke(truncationLine(droppedChars, suppressed))
    }

    // (PRIV-06) D-03: the notice text. A constant sentence plus counts — no dropped content, and
    // no literal window duration that could drift away from TRUNCATION_LOG_INTERVAL_MS.
    private fun truncationLine(
        droppedChars: Long,
        suppressed: Long,
    ): String {
        val line = "[Redaction] Body redaction dropped $droppedChars characters; that content was NOT sent to the AI backend."
        return if (suppressed > 0L) "$line Further notices suppressed since the previous line: $suppressed." else line
    }

    // Internal test seam — clears the D-03 limiter window. NOT part of the public API; only
    // referenced from the test source set, in the style of the testHkdfExtract seam below. It
    // exists because Redaction is a singleton object, so the limiter state otherwise bleeds across
    // tests sharing a JVM and makes an injected-clock assertion order-dependent.
    internal fun resetTruncationWindowForTest() {
        lastTruncationLogMs.set(0L)
        suppressedTruncations.set(0L)
    }

    // (PRIV-06) D-02: nanoseconds per millisecond, used to turn System.nanoTime() deltas into the
    // millisecond deadlines SafeRegex takes. A named constant because detekt's MagicNumber rule
    // ignores only -1/0/1/2 and QUAL-07 forbids growing detekt-baseline.xml.
    private const val NANOS_PER_MS = 1_000_000L

    // (PRIV-06) D-01 / T-21-06: how many times a window that did not scan in time may be halved at
    // a line boundary and retried before it is dropped. Measured headroom at the 1 MB window width
    // is only ~2.2x on Apple Silicon (23 ms for dense form content against a 50 ms per-pattern
    // deadline), so a 2-3x slower machine would drop content that ships today. Two levels turn the
    // deadline into a pacing mechanism instead of a cliff.
    private const val WINDOW_RETRY_MAX_DEPTH = 2

    // (PRIV-06) CR-02 / T-21-08: how many following lines the JSON boundary-safety rule in
    // [windowEnd] may pull into a window. The extension exists because jsonSecretKeyRegex's \s* can
    // span newlines; capping it is what stops the fix trading a leak for a denial of service, since
    // an unbounded "keep extending until the pair closes" loop lets a crafted run of colon- or
    // quote-terminated lines grow a single window without limit. Eight covers every pretty-printer
    // shape this phase measured — key, colon and value on separate lines, with blank lines between
    // them — with room to spare.
    //
    // Reaching the cap is a DOCUMENTED RESIDUAL, not a closure: a jsonSecretKeyRegex pair spread
    // over more than this many lines can still straddle a cut and be missed. T-21-31 accepts it and
    // ADR-14 and .planning/codebase/CONCERNS.md record it, rather than pretending it away.
    //
    // The cap is nonetheless not a fail-OPEN in D-02's sense, and the distinction is worth stating
    // because the two are easy to conflate. This lookahead only ever MOVES a boundary: every byte
    // still lands in exactly one window and is still scanned, nothing is skipped and nothing is
    // emitted unscanned. Any window the extension grows past what will scan in time is still
    // dropped behind a marker by scanWindow/dropOrRetry. What remains at the cap is a rule
    // FALSE NEGATIVE across a cut — the same class as the custom-pattern residual below, not the
    // class of defect PRIV-06 exists to remove. Dropping the window at the cap instead was
    // considered and rejected: the trigger is a content heuristic that ordinary multi-line HTML
    // attributes and nested YAML both satisfy, so it would destroy up to a megabyte of analytic
    // context (T-21-06) on entirely benign input.
    //
    // detekt's MagicNumber ignore list stops at 2, so this is a named constant, not a literal.
    private const val MAX_JSON_BOUNDARY_LOOKAHEAD_LINES = 8

    // (PRIV-06) D-03 / T-21-05: the marker left in the payload when the total budget is spent and
    // the remaining tail is dropped. Exactly ONE of these is emitted per call, never one per
    // window — a 200 MB input would otherwise produce ~200 markers and bloat the very prompt this
    // stage exists to bound.
    //
    // The wording is load-bearing in four ways and must keep all four:
    //   - it says the content was REMOVED, not passed through. A "TRUNCATED / NOT SCRUBBED" phrasing
    //     reads as "what follows is unfiltered", the opposite of what actually happened.
    //   - it is distinguishable from [REDACTED], so a reader can tell "removed for size" from
    //     "removed for secrecy" — the same reason [JWT_REDACTED] and [STRIPPED] are distinct today.
    //   - it is a constant shape plus one integer, so it carries ZERO attacker-controlled substring
    //     and is not a prompt-injection vector.
    //   - it is not phrased as an instruction to the model.
    // ASCII hyphen, never an em dash: the marker is prompt content that gets hashed into
    // sha256Hex(singlePrompt) and round-trips through backend transports.
    private fun budgetExceededMarker(droppedChars: Int): String = "[REDACTION BUDGET EXCEEDED - $droppedChars CHARS DROPPED AND NOT SENT]"

    // (PRIV-06) D-02 / D-03: the marker left in place of a single window that could not be fully
    // scanned inside its deadline. Same four wording properties as the budget marker above; a
    // distinct token so "the budget ran out" and "this window would not scan" stay tellable apart
    // in a prompt.
    private fun windowDroppedMarker(droppedChars: Int): String = "[REDACTION INCOMPLETE - $droppedChars CHARS DROPPED AND NOT SENT]"

    // (PRIV-06) D-01 AMENDED / D-02 / D-04 / D-05 / D-14: the body-redaction stage.
    //
    // Before Phase 21 this was an `if (out.length <= MAX_REDACTION_BODY_CHARS)` guard INSIDE the
    // redactTokens branch: an input above the cap had the form, JSON and custom-pattern rules
    // skipped entirely and the original passed through untouched. That is redaction failing open on
    // exactly the inputs most likely to carry a secret (T-21-02), and removing it is PRIV-06.
    //
    // D-01 AMENDED — the input is cut into windows at LINE BOUNDARIES and every window is scanned;
    // nothing is skipped. A line is never split: every built-in body rule is line-anchored or
    // line-local, so a mid-line cut is the only thing that can change their semantics, and it was
    // measured doing so in BOTH directions (creating a match at an artificial line start, and
    // truncating a match that spans the cut). The original decision's OVERLAP clause is
    // deliberately dropped rather than implemented: the value side of every built-in is
    // length-unbounded (measured single matches of 200 006 characters) and user patterns are
    // unbounded by construction, so no finite overlap constant is defensible.
    // Matcher.region() has the right semantics but replaceAll() silently resets the region, so
    // region-scoped replacement is not available at all.
    //
    // WHAT LINE-BOUNDARY CUTTING ACTUALLY BUYS, stated as it ships rather than as it was first
    // claimed: it preserves the LINE-ANCHORED SEMANTICS of the built-in body rules — no (?m)^ anchor
    // is created at an artificial line start and none is destroyed mid-line — and the one built-in
    // whose match can span newlines, jsonSecretKeyRegex, is covered by a bounded lookahead of
    // MAX_JSON_BOUNDARY_LOOKAHEAD_LINES lines in windowEnd. Byte-identity with whole-document
    // processing is NOT claimed in general: a match spanning more than that lookahead, and any user
    // custom pattern spanning a cut, can still be missed. Both residuals are recorded in ADR-14 and
    // in .planning/codebase/CONCERNS.md.
    //
    // CR-02 / D-08 REFINED — why the previous wording is gone, recorded so it reads as a deliberate
    // retirement rather than something lost in an edit: this paragraph used to state that
    // line-boundary cutting had been proven equivalent, byte for byte, to whole-document
    // processing. That was FALSE as implemented, and it was falsified by a reproduction rather than
    // by argument — a three-line JSON pair straddling the cut leaked on the windowed path while the
    // single-pass path redacted it. The original claim was established without ever sweeping a
    // fixture across the cut, which is exactly why it survived review. The claim now lives in a
    // named test, windowedScanRedactsJsonPairAcrossEveryBoundaryAlignment, so the assertion and its
    // evidence cannot drift apart again. A record claims only what ships.
    //
    // The bounded lookahead is NOT the overlap clause reinstated under another name. That clause
    // stays dropped on the measured grounds above; this mechanism moves the BOUNDARY, so windows
    // remain disjoint and line-aligned and no region is ever duplicated into two windows.
    //
    // D-02 / D-14 — fail CLOSED, at EVERY size. Every rule, on both the single-pass path and the
    // windowed path, runs through SafeRegex.replaceAllSafeReporting, and a timedOut flag is never
    // ignored: above the window width the affected window is DROPPED behind a marker, and at or
    // below it the partial result is discarded and the whole input is re-scanned through the
    // windowed path. Assigning replaceAllSafe's return value anywhere in this stage would be
    // fail-OPEN at exactly the moment D-02 demands fail-closed, because on timeout it returns the
    // input unchanged — byte-identical to "the pattern matched nothing" (T-21-03). There is
    // therefore no size at which a body rule can time out and its unscanned bytes still be emitted.
    @Suppress("ReturnCount")
    private fun bodyStage(
        input: String,
        builtinsEnabled: Boolean,
    ): String {
        val rules =
            buildList {
                if (builtinsEnabled) {
                    // The $n replacement strings are byte-for-byte equivalent to the Kotlin lambdas
                    // they replace (verified); SafeRegex takes a String replacement, not a lambda.
                    add(formBodyParamRegex.toPattern() to "\$1\$2=[REDACTED]")
                    add(jsonSecretKeyRegex.toPattern() to "\$1\"[REDACTED]\"")
                }
                // PRIV-06 / D-05: the custom-pattern loop sits OUTSIDE the redactTokens branch and
                // therefore applies in EVERY privacy mode, including OFF. A user's custom list is a
                // "never send this, ever" list and is independent of the privacy mode; OFF now
                // means "no built-in redaction", not "no redaction at all".
                compiledCustomPatterns.forEach { add(it to "[REDACTED]") }
            }

        // OFF with no custom patterns must be a byte-identical passthrough, so this check comes
        // BEFORE any windowing. Any marker, normalisation or trailing-newline difference introduced
        // here fails offModePreservesBodies and offModePreservesAllTokens.
        if (rules.isEmpty()) return input

        // PRIV-06 / D-04: at or below the window width this is a single pass whose cost and
        // behaviour match the pre-Phase-21 implementation for the overwhelming majority of
        // payloads — when no rule times out, the loop below is byte-identical to a plain
        // replace-each-rule chain. One genuine change even on this path: the two built-in body
        // rules previously ran with NO deadline at all (only custom patterns went through
        // SafeRegex) and now all of them do.
        //
        // (PRIV-06) D-02 / D-14: that new deadline must not smuggle a fail-OPEN back in.
        // SafeRegex.replaceAllSafe returns its input UNCHANGED on timeout, byte-identical to "the
        // pattern matched nothing", so assigning its result here would silently skip an overrunning
        // rule and emit unredacted content on the common path — the exact failure mode this phase
        // exists to remove, reintroduced one size class lower. replaceAllSafeReporting's timedOut
        // flag is the only signal that tells the two cases apart, so it is what is branched on.
        //
        // On timeout the PARTIAL result is discarded and the ORIGINAL input is handed to
        // windowedScan, which already fails closed (halve-and-retry to WINDOW_RETRY_MAX_DEPTH, then
        // drop behind a visible marker). Restarting from the original rather than continuing from
        // the partially-processed string avoids double-marking and any partial-application ordering
        // artifact, and it reuses that machinery instead of duplicating it here.
        //
        // BUDGET CEILING — stated rather than assumed: this composition CAN exceed
        // Defaults.MAX_REDACTION_BUDGET_MS, because windowedScan starts its own budget clock. The
        // excess is bounded by construction: the single pass gives up after at most
        // rules.size * SafeRegex.DEFAULT_TIMEOUT_MS, so the worst case is
        // rules.size * DEFAULT_TIMEOUT_MS + MAX_REDACTION_BUDGET_MS — finite, and only reachable
        // when a rule is already pathological. Threading one shared deadline into windowedScan was
        // rejected: the fallthrough would then routinely arrive with the budget already spent and
        // drop the ENTIRE body behind a single marker, which is fail-closed but destroys all
        // analytic context (T-21-06) on an input small enough to scan properly.
        if (input.length <= Defaults.MAX_REDACTION_BODY_CHARS) {
            var out = input
            for ((pattern, replacement) in rules) {
                val result = SafeRegex.replaceAllSafeReporting(out, pattern, replacement)
                if (result.timedOut) return windowedScan(input, rules)
                out = result.text
            }
            return out
        }

        return windowedScan(input, rules)
    }

    // (PRIV-06) D-01 AMENDED / D-02: the windowed loop used above the window width. Windows are
    // processed IN ORDER, and each one is either fully scanned and appended or dropped behind a
    // marker. Once the total budget is spent, ALL remaining characters are coalesced into exactly
    // one tail marker and the loop breaks. Every piece of loop state is a local: apply() runs
    // concurrently on scanner threads and MCP tool threads, so object-level window state would
    // corrupt output across them (T-21-23).
    private fun windowedScan(
        input: String,
        rules: List<Pair<Pattern, String>>,
    ): String {
        val budgetDeadlineNanos = System.nanoTime() + Defaults.MAX_REDACTION_BUDGET_MS * NANOS_PER_MS
        val sink = StringBuilder(input.length)
        var index = 0
        while (index < input.length) {
            if (remainingBudgetMs(budgetDeadlineNanos) <= 0L) {
                val dropped = input.length - index
                sink.append(budgetExceededMarker(dropped))
                maybeLogTruncation(System.currentTimeMillis(), dropped.toLong())
                break
            }
            val end = windowEnd(input, index, Defaults.MAX_REDACTION_BODY_CHARS)
            scanWindow(input.substring(index, end), rules, budgetDeadlineNanos, 0, sink)
            index = end
        }
        return sink.toString()
    }

    // (PRIV-06) D-02: milliseconds left in the total body-stage budget; zero or negative once spent.
    private fun remainingBudgetMs(budgetDeadlineNanos: Long): Long = (budgetDeadlineNanos - System.nanoTime()) / NANOS_PER_MS

    // (PRIV-06) D-02 / D-14: applies every rule to one window under a bounded deadline and appends
    // the result ONLY when the whole window was scanned.
    //
    // The minOf() is what stops a per-pattern deadline outliving the total budget: a rule starting
    // with 12 ms of budget left gets a 12 ms deadline and REPORTS a timeout instead of overrunning.
    // replaceAllSafe's return value is never assigned here — on timeout it returns the input
    // unchanged, indistinguishable from "no matches".
    private fun scanWindow(
        window: String,
        rules: List<Pair<Pattern, String>>,
        budgetDeadlineNanos: Long,
        depth: Int,
        sink: StringBuilder,
    ) {
        var current = window
        for ((pattern, replacement) in rules) {
            val remainingMs = remainingBudgetMs(budgetDeadlineNanos)
            if (remainingMs <= 0L) {
                dropOrRetry(window, rules, budgetDeadlineNanos, depth, sink)
                return
            }
            val result =
                SafeRegex.replaceAllSafeReporting(
                    current,
                    pattern,
                    replacement,
                    minOf(SafeRegex.DEFAULT_TIMEOUT_MS, remainingMs),
                )
            if (result.timedOut) {
                dropOrRetry(window, rules, budgetDeadlineNanos, depth, sink)
                return
            }
            current = result.text
        }
        sink.append(current)
    }

    // (PRIV-06) D-01 / T-21-06: halve-and-retry before dropping. A window that did not scan in time
    // is split at a LINE boundary and each half retried, to WINDOW_RETRY_MAX_DEPTH levels, so a
    // slower machine paces instead of losing content outright. When the budget is already spent, or
    // the retry depth is exhausted, or the window has no interior line boundary to split on, the
    // window is dropped behind exactly one marker — fail closed, so its unscanned bytes never reach
    // a backend.
    private fun dropOrRetry(
        window: String,
        rules: List<Pair<Pattern, String>>,
        budgetDeadlineNanos: Long,
        depth: Int,
        sink: StringBuilder,
    ) {
        val retryable = depth < WINDOW_RETRY_MAX_DEPTH && remainingBudgetMs(budgetDeadlineNanos) > 0L
        val cut = if (retryable) splitPoint(window) else 0
        if (cut <= 0 || cut >= window.length) {
            sink.append(windowDroppedMarker(window.length))
            maybeLogTruncation(System.currentTimeMillis(), window.length.toLong())
            return
        }
        scanWindow(window.substring(0, cut), rules, budgetDeadlineNanos, depth + 1, sink)
        scanWindow(window.substring(cut), rules, budgetDeadlineNanos, depth + 1, sink)
    }

    // (PRIV-06) D-01: the line boundary nearest the middle of [window], or 0 when the window cannot
    // be split without cutting a line. The returned index is just past a newline, so both halves
    // stay line-aligned and (?m)^ keeps its meaning inside each of them.
    private fun splitPoint(window: String): Int {
        val mid = window.length / 2
        val backward = window.lastIndexOf('\n', mid)
        if (backward > 0) return backward + 1
        val forward = window.indexOf('\n', mid)
        return if (forward >= 0 && forward + 1 < window.length) forward + 1 else 0
    }

    // (PRIV-06) D-01 AMENDED: the end of the window starting at [start] — the index just past the
    // last newline at or before [start] + [width]. A single line longer than [width] becomes its own
    // oversized window rather than being split; the per-pattern deadline already bounds what that
    // costs, and a mid-line cut is the only thing that can change a line-anchored rule's semantics.
    //
    // JSON boundary safety: jsonSecretKeyRegex's whitespace class CAN span newlines (it matches a
    // pretty-printed key/colon/value spread over four lines), so a pair split exactly across a
    // window boundary would be missed. When the last line of the prospective window ends with a
    // colon or a double quote after a trailing-whitespace strip, following lines are pulled in ONE
    // AT A TIME, re-checking each newly included line, until a line that cannot continue a pair is
    // reached or MAX_JSON_BOUNDARY_LOOKAHEAD_LINES lines have been taken.
    //
    // CR-02 — the defect this loop replaces, recorded because the comment above it was already
    // correct and the code still did not follow it: the previous form pulled in exactly ONE line and
    // NEVER RE-CHECKED, so the "key on one line, colon on the next, value on the third" case this
    // very comment cites was unhandled. The boundary landed after the key line, the risk check
    // fired, the colon line was pulled in, and the window stopped there — leaving the value to start
    // the next window, where the rule could no longer match across the cut. The reviewer reproduced
    // a divergence at shift 7: the windowed path LEAKED a value the single-pass path redacts, which
    // is a redaction bypass keyed only on payload size. Response bodies are attacker-controlled and
    // window boundaries are deterministic given a known prefix length, so that alignment is
    // craftable. Guarded by windowedScanRedactsJsonPairAcrossEveryBoundaryAlignment, which sweeps a
    // full pad-line period rather than testing one alignment.
    //
    // The initial test and the loop test are deliberately DIFFERENT predicates, and the asymmetry is
    // the point. A blank line does NOT start an extension (isJsonPairBoundaryRisk): every window
    // that happens to end on a blank line would otherwise spend lookahead for nothing. A blank line
    // DOES continue one already in flight (isJsonPairBoundaryContinuation), because
    // "key" / ':' / blank / "value" is a shape jsonSecretKeyRegex's \s* genuinely matches — verified
    // against the real pattern — and it would otherwise still slip through even with the loop.
    //
    // formBodyParamRegex and urlTokenParamRegex cannot span newlines (their value classes exclude
    // \s) and need nothing.
    //
    // ACCEPTED RESIDUAL: a CUSTOM pattern whose match straddles a window boundary can still be
    // missed. There is no principled bound on a user regex's match length, so no window scheme can
    // close this; it is recorded rather than pretended away. A built-in JSON pair spread over more
    // than MAX_JSON_BOUNDARY_LOOKAHEAD_LINES lines falls in the same category — narrowed by the
    // loop, not eliminated by it.
    @Suppress("ReturnCount")
    private fun windowEnd(
        s: String,
        start: Int,
        width: Int,
    ): Int {
        // Written this way rather than as start + width so the sum cannot overflow on a huge input.
        val hard = if (width >= s.length - start) s.length else start + width
        if (hard == s.length) return hard
        val lastNewline = s.lastIndexOf('\n', hard)
        if (lastNewline <= start) {
            // The line starting at [start] is longer than the window: keep that line whole.
            val next = s.indexOf('\n', start)
            return if (next < 0) s.length else next + 1
        }
        var end = lastNewline + 1
        // The line immediately BEFORE the prospective boundary. lineStart/lineEnd are carried
        // through the loop rather than recomputed with lastIndexOf, so each extension step is O(1)
        // in the window size and the whole lookahead stays linear in the lines it pulls.
        var lineStart = maxOf(start, s.lastIndexOf('\n', lastNewline - 1) + 1)
        var lineEnd = lastNewline
        // The INITIAL test looks BACKWARD (see pairMayBeInFlightAt); every line pulled in afterwards
        // is tested with the forward continuation predicate, which treats a blank line as risky.
        var risky = pairMayBeInFlightAt(s, start, lineStart, lineEnd)
        var pulled = 0
        while (risky && pulled < MAX_JSON_BOUNDARY_LOOKAHEAD_LINES) {
            val following = s.indexOf('\n', end)
            // No further newline exists, so there is no next line to pull: the remainder of the
            // input becomes this window rather than being cut inside a possible pair. Every byte is
            // still scanned, and scanWindow/dropOrRetry still fail closed if it will not scan in
            // time.
            if (following < 0) return s.length
            lineStart = end
            lineEnd = following
            end = following + 1
            pulled++
            risky = isJsonPairBoundaryContinuation(s.substring(lineStart, lineEnd))
        }
        // At the cap the window is line-aligned exactly as in every other branch; only the boundary
        // moved. See MAX_JSON_BOUNDARY_LOOKAHEAD_LINES for why stopping here is a recorded residual.
        return end
    }

    // (PRIV-06) CR-02: true when a jsonSecretKeyRegex pair may be IN FLIGHT at a window boundary
    // whose last line is [lineStart, lineEnd). This is the predicate that decides whether to START
    // an extension at all.
    //
    // It has to look BACKWARD, and that is not an embellishment — it was forced by a reproduction.
    // The boundary can land ON a blank line that is itself inside a pair, i.e.
    // "key" / ':' / blank / <cut> / "value". Testing only the immediately preceding line refuses to
    // extend there, because a blank line is deliberately not a risk on its own, so the very shape
    // the loop exists to catch would still be cut in half. That is exactly what
    // jsonPairWithBlankLineBetweenKeyAndValueIsRedacted reproduced at shift 0, with the forward loop
    // already in place.
    //
    // So: walk back over blank lines, bounded by the same cap that bounds the forward extension, and
    // let the nearest line that actually carries content decide. A blank run with nothing risky
    // behind it still starts nothing, which is the property the risk/continuation asymmetry existed
    // to protect — windows that merely happen to end on a blank line do not spend forward lookahead.
    private fun pairMayBeInFlightAt(
        s: String,
        start: Int,
        lineStart: Int,
        lineEnd: Int,
    ): Boolean {
        var probeStart = lineStart
        var probeEnd = lineEnd
        var lookedBack = 0
        var line = s.substring(probeStart, probeEnd)
        // Skip back over blank lines only. Stopping at [start] keeps this window's decision
        // independent of earlier windows, and the cap keeps a run of blank lines from turning a
        // boundary decision into an unbounded backward scan.
        while (line.isBlank() && probeStart > start && lookedBack < MAX_JSON_BOUNDARY_LOOKAHEAD_LINES) {
            probeEnd = probeStart - 1
            probeStart = maxOf(start, s.lastIndexOf('\n', probeEnd - 1) + 1)
            lookedBack++
            line = s.substring(probeStart, probeEnd)
        }
        // Whichever way the walk ended, the line in hand decides. If it is still blank — the run hit
        // [start] or the cap with nothing behind it — isJsonPairBoundaryRisk is false, so a blank
        // region on its own still starts no extension.
        return isJsonPairBoundaryRisk(line)
    }

    // (PRIV-06) D-01: true when [line] ends where jsonSecretKeyRegex's newline-spanning whitespace
    // class could be sitting between a key and its value. Content-only: a blank line is handled by
    // pairMayBeInFlightAt going backward and by isJsonPairBoundaryContinuation going forward.
    private fun isJsonPairBoundaryRisk(line: String): Boolean {
        val trimmed = line.trimEnd()
        return trimmed.endsWith(":") || trimmed.endsWith("\"")
    }

    // (PRIV-06) CR-02: true when [line] can CONTINUE an extension that is already in flight. A
    // whitespace-only line qualifies: jsonSecretKeyRegex's \s* spans it, so "key" / ':' / blank /
    // "value" is one match, and a loop that stopped on the blank line would cut that shape in half
    // exactly as the pre-CR-02 single-line pull did. See windowEnd for why this is deliberately
    // wider than isJsonPairBoundaryRisk rather than the same predicate reused.
    private fun isJsonPairBoundaryContinuation(line: String): Boolean = line.isBlank() || isJsonPairBoundaryRisk(line)

    private val hostHeaderRegex = Regex("(?im)^host:\\s*([^\\s]+)\\s*$")

    // REL-02/SC5b: cap for the inner per-salt LRU maps. A few thousand entries (4096) is large
    // enough that a normal pentest session never evicts, but small enough to bound memory over
    // a long session (DoS mitigation). Forward/reverse eviction skew is benign: if forward evicts
    // host→anon but reverse still holds anon→host, de-anonymization still works (reverse is the
    // lookup path). Re-anonymizing an evicted host is deterministic (HKDF is pure, so the same
    // host+salt always produces the same anon value) and merely re-populates the forward entry.
    private const val HOST_MAP_CAP = 4096

    // Creates a bounded LRU map (synchronized, access-ordered LinkedHashMap with eldest-entry
    // eviction). Used for INNER per-salt maps only; the OUTER ConcurrentHashMap stays unbounded.
    private fun <K, V> boundedLru(maxEntries: Int): MutableMap<K, V> =
        java.util.Collections.synchronizedMap(
            // accessOrder = true (access-ordered LRU)
            object : LinkedHashMap<K, V>(16, 0.75f, true) {
                override fun removeEldestEntry(eldest: Map.Entry<K, V>): Boolean = size > maxEntries
            },
        )

    private val hostForwardMap = ConcurrentHashMap<String, MutableMap<String, String>>()
    private val hostReverseMap = ConcurrentHashMap<String, MutableMap<String, String>>()

    // HKDF constants (RFC 5869, https://www.rfc-editor.org/rfc/rfc5869).
    // App-specific context label binds the derivation to this use case (host anonymization).
    // L = 6 bytes → 12 hex chars, preserving the exact host-<12hex>.local output format.
    private const val HKDF_INFO = "burp-ai-agent:host"
    private const val HKDF_OKM_LEN = 6

    // RFC 5869 HMAC-SHA256 primitive.
    // If [key] is empty a single zero byte is substituted — SecretKeySpec rejects a 0-length key
    // (Pitfall 1: RFC 5869 allows an absent/all-zero salt but JCA requires >= 1 key byte).
    private fun hmacSha256(
        key: ByteArray,
        data: ByteArray,
    ): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        val keySpec = SecretKeySpec(if (key.isEmpty()) ByteArray(1) else key, "HmacSHA256")
        mac.init(keySpec)
        return mac.doFinal(data)
    }

    // RFC 5869 HKDF-Extract: PRK = HMAC-Hash(salt, IKM).
    private fun hkdfExtract(
        salt: ByteArray,
        ikm: ByteArray,
    ): ByteArray = hmacSha256(salt, ikm)

    // RFC 5869 HKDF-Expand: OKM = first [length] octets of T(1)|T(2)|...
    // T(i) = HMAC(PRK, T(i-1) | info | counter_byte), T(0) = empty.
    private fun hkdfExpand(
        prk: ByteArray,
        info: ByteArray,
        length: Int,
    ): ByteArray {
        val out = ByteArrayOutputStream()
        var t = ByteArray(0)
        var counter = 1
        while (out.size() < length) {
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(prk, "HmacSHA256"))
            mac.update(t)
            mac.update(info)
            mac.update(counter.toByte())
            t = mac.doFinal()
            out.write(t)
            counter++
        }
        return out.toByteArray().copyOf(length)
    }

    // Internal test seams — expose the HKDF helpers for RFC 5869 vector assertion in
    // RedactionTest.hkdfMatchesRfc5869Vector. NOT part of the public API; only referenced
    // from the test source set.
    internal fun testHkdfExtract(
        salt: ByteArray,
        ikm: ByteArray,
    ): ByteArray = hkdfExtract(salt, ikm)

    internal fun testHkdfExpand(
        prk: ByteArray,
        info: ByteArray,
        length: Int,
    ): ByteArray = hkdfExpand(prk, info, length)

    fun apply(
        raw: String,
        policy: RedactionPolicy,
        stableHostSalt: String,
        recordMapping: Boolean = true,
    ): String {
        var out = raw

        if (policy.stripCookies) {
            out = out.replace(cookieHeaderRegex, "Cookie: [STRIPPED]")
            out = out.replace(setCookieHeaderRegex, "Set-Cookie: [STRIPPED]")
            // (PRIV-05) SC1: the passive scanner re-emits cookies as bare name=value lines in a
            // dedicated section, stripped of the prefix the two header rules above key on.
            out = redactCookieSections(out)
            // (PRIV-05) SC2: the same values leak a second time as typed parameter lines. Only the
            // value is replaced; the name and the trailing type label are written back verbatim.
            // Both rules run BEFORE the body stage and are idempotent under it — re-matching
            // NAME=[REDACTED] reproduces NAME=[REDACTED] — so no ordering hazard exists.
            out =
                out.replace(cookieTypedParamRegex) { m ->
                    // Destructured rather than groupValues[3]: detekt's MagicNumber rule ignores
                    // only -1/0/1/2, and a named binding reads better than a bare group index.
                    val (name, _, typeSuffix) = m.destructured
                    "$name=[REDACTED]$typeSuffix"
                }
        }

        if (policy.redactTokens) {
            out =
                out.replace(authHeaderRegex) { m ->
                    val header = m.value.substringBefore(":")
                    "$header: [REDACTED]"
                }
            out = out.replace(bearerRegex, "Bearer [REDACTED]")
            out = out.replace(basicAuthRegex, "Basic [REDACTED]")
            out = out.replace(jwtRegex, "[JWT_REDACTED]")
            out = out.replace(urlTokenParamRegex, "$1[REDACTED]")
        }

        // (PRIV-06) D-01 AMENDED / D-02 / D-05: body-level redaction (form + JSON + custom
        // patterns), invoked UNCONDITIONALLY rather than from inside the redactTokens branch above.
        // The built-in body rules still follow policy.redactTokens, but the custom-pattern loop no
        // longer does — that move is the whole of D-05, and it is what lets a user's "never send
        // this, ever" list apply under PrivacyMode.OFF as well.
        //
        // The eight header-stage rules above deliberately stay OUTSIDE this budget: they run
        // unbounded on the full input exactly as they did before this phase. That is a pre-existing
        // condition outside D-01/D-02's scope, recorded rather than silently claimed as fixed.
        out = bodyStage(out, builtinsEnabled = policy.redactTokens)

        if (policy.anonymizeHosts) {
            out =
                out.replace(hostHeaderRegex) { m ->
                    val host = m.groupValues[1]
                    val anon = anonymizeHost(host, stableHostSalt, recordMapping)
                    "Host: $anon"
                }
        }

        return out
    }

    // Anonymizes [host] using RFC 5869 HKDF (HMAC-SHA256 extract-then-expand).
    // Signature and output format (host-<12hex>.local) are preserved from the previous
    // SHA-256 implementation so all ~10 call sites remain unchanged (Pitfall 6).
    // salt → HKDF extract salt; host → IKM (input keying material).
    fun anonymizeHost(
        host: String,
        salt: String,
        recordMapping: Boolean = true,
    ): String {
        val prk =
            hkdfExtract(
                salt.toByteArray(StandardCharsets.UTF_8),
                host.toByteArray(StandardCharsets.UTF_8),
            )
        val okm =
            hkdfExpand(
                prk,
                HKDF_INFO.toByteArray(StandardCharsets.UTF_8),
                HKDF_OKM_LEN,
            )
        val short = okm.joinToString("") { "%02x".format(it) } // 6 bytes → 12 hex chars
        val anon = "host-$short.local"
        if (recordMapping) {
            // REL-02/SC5b: inner map is bounded LRU (cap HOST_MAP_CAP); outer map + computeIfAbsent/
            // remove are unchanged so clearMappings() keeps working (Pitfall 5).
            hostForwardMap.computeIfAbsent(salt) { boundedLru(HOST_MAP_CAP) }[host] = anon
            hostReverseMap.computeIfAbsent(salt) { boundedLru(HOST_MAP_CAP) }[anon] = host
        }
        return anon
    }

    fun deAnonymizeHost(
        host: String,
        salt: String,
    ): String? = hostReverseMap[salt]?.get(host)

    fun clearMappings(salt: String? = null) {
        if (salt == null) {
            hostForwardMap.clear()
            hostReverseMap.clear()
            return
        }
        hostForwardMap.remove(salt)
        hostReverseMap.remove(salt)
    }
}
