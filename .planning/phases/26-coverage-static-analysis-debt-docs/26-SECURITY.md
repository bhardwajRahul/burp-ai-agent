---
phase: 26
slug: coverage-static-analysis-debt-docs
status: verified
# threats_open = count of OPEN threats at or above workflow.security_block_on severity (the blocking gate)
threats_open: 0
asvs_level: 1
created: 2026-08-24
---

# Phase 26 — Security

> Per-phase security contract: threat register, accepted risks, and audit trail.

Phase 26 (Coverage, Static-Analysis Debt & Docs, requirements **QUAL-06**, **QUAL-07**, **DOC-03**)
is mostly a phase about making existing controls *falsifiable* rather than adding new ones. Two
exceptions carry real security weight: `shellEscape` was converted from a metacharacter denylist to
an allowlist at the `argv` → `sh -c` boundary, and the user-facing documentation of `SecretCipher`'s
at-rest guarantee was corrected from an overstatement to an accurate claim. Documentation is a
control surface in this phase, not decoration — an overstated at-rest claim causes a user to store a
credential they would otherwise have kept elsewhere.

**Register origin:** authored at plan time. All seven PLAN files (`26-01` … `26-07`) shipped a
`<threat_model>` block, so this audit **verifies that the declared mitigations exist** rather than
constructing a register retroactively. Verification depth is ASVS L1 (source-level evidence), which
the workflow declares sufficient when `threats_open: 0` and the register is plan-authored.

---

## Trust Boundaries

| Boundary | Description | Data Crossing |
|----------|-------------|---------------|
| Burp Preferences / settings import → CLI argv | A CLI command, its extras and the agent profile are user- or import-supplied strings that become process arguments. QUAL-06 records settings import as an attacker-reachable path. | Command name, flags, model id |
| CLI argv → `sh -c` (PTY path, Unix only) | The one place in the tree where argv is joined into a single shell command string and every argument is re-parsed by `/bin/sh`. This phase's highest-value boundary. | Shell command text |
| Model output → MCP tool input | A tool call parsed out of model text carries a JSON argument blob; `kotlinx.serialization` is where attacker-influenceable text becomes typed parameters. | Tool arguments |
| Burp data → AI prompt (MCP tool results) | `sanitizeHeaders` and `maybeAnonymizeUrl` are the redaction path for MCP tool **results**, independent of `Redaction.redact`'s prompt path. | Headers, URLs, cookies, bearer tokens |
| Tool input → filesystem | `resolveReportPath` turns a model-supplied string into a `java.nio.file.Path` that a report is written to. | File path |
| Tool input → Montoya `HttpService` | `toMontoyaServiceOrNull` turns model-supplied host and port into an outbound target. | Hostname, port |
| User-typed / imported backend base URL → `SsrfGuard` | The guard's verdict is what the user is warned by. Advisory and non-blocking per D-01. | URL text |
| `SsrfGuard` → name resolution | The boundary SC4 forbids crossing on the IPv4 arm; the IPv6 arm's single resolving call is gated by `host.contains(':')`. | Hostname (must not cross) |
| Operator-typed MCP token → takeover proof HMAC key | A short token turns a captured proof into an offline verifier (25-REVIEW WR-01). | Bearer token entropy |
| Burp Preferences → `SecretCipher` | Ciphertext and its master key sit side by side; the boundary this phase touches is the fail-soft decrypt contract and the accuracy of what is claimed about it. | API keys, bearer tokens |
| Background thread → `ChatPanel`'s `@GuardedBy("EDT")` session maps | A violation is a data race on the maps holding chat sessions, drafts and tool-decision records. | Session state |
| Project documentation → user's security decisions | An overstated at-rest claim causes a user to store a credential elsewhere kept. Advisory wording determines whether a user rotates a leaked session cookie. | Security claims |
| This repository → `burp-ai-agent-docs` | A separate git repository. Anything written there by this phase would be an unrequested side effect in a repository the phase does not own. | Published documentation |
| static-analysis configuration → the finding count | `detekt.yml` and `detekt-baseline.xml` together determine what is reported; either can be edited to make findings vanish without any code improving. | Reported debt |

---

## Threat Register

| Threat ID | Category | Component | Severity | Disposition | Mitigation | Status |
|-----------|----------|-----------|----------|-------------|------------|--------|
| T-26-01-01 | Elevation of Privilege | `shellEscape` → `buildPtyCommand` → `sh -c` | high | mitigate | Denylist replaced by allowlist: `SHELL_SAFE_CHARS` is exactly `a-zA-Z0-9._/-`; anything outside forces single-quoting. **Verified:** `CliBackend.kt:861-884`; `ShellEscapeTest` (13 tests) asserts `foo;id`, `$(cmd)`, backtick and newline forms quoted both on the helper and on the joined macOS/Linux `sh -c` argv. | closed |
| T-26-01-02 | Tampering | `shellEscape` single-quote escape | high | mitigate | The `'` → `'"'"'` close/escape/reopen replacement is retained unchanged. **Verified:** `CliBackend.kt:883`; asserted by `embeddedApostropheUsesPosixQuoteEscape`. | closed |
| T-26-01-03 | Denial of Service | `shellEscape` fast path | low | mitigate | The pass-through test is `arg.all { it in SHELL_SAFE_CHARS }` — a per-character scan over a `const val`, not a `Regex`. No backtracking surface on a path that runs per CLI argument. **Verified in source.** | closed |
| T-26-01-04 | Information Disclosure | widened `internal` helpers | low | **accept** | `internal` is module-scoped, single-module Gradle build, no published Kotlin API; the five widened helpers are pure transforms over data the caller already holds. Same trade-off `buildTimeoutMessage` and `buildCopilotCommand` already made. See Accepted Risks. | closed |
| T-26-01-05 | Tampering | over-quoting regression | medium | mitigate | Positive assertions that `--silent`, `/usr/local/bin/claude`, `claude-3.5` and `gemini_cli` return byte-identical, guarding against a fix that quotes everything and silently breaks working CLI backends. **Verified:** `plainFlagIsPassedThroughUnquoted`, `absolutePathIsPassedThroughUnquoted`, `versionSuffixAndUnderscoreNameArePassedThroughUnquoted`, `ptyArgvLeavesAllowlistedArgumentsByteIdentical`. | closed |
| T-26-02-01 | Information Disclosure | `sanitizeHeaders` | high | mitigate | **Three-part history — read top to bottom; none of it replaces what came before.** **(1) The original narrow claim, which holds.** Case-insensitive matching of `Cookie`, `Set-Cookie`, `Authorization`, `Proxy-Authorization`, `X-API-Key`, `Api-Key` and `Host` is asserted per privacy mode (`McpToolHelpersTest.SanitizeHeaders`). **(2) REOPENED 2026-08-24 by the v0.10.0 milestone audit**, because the broad claim did not hold: the matcher was `lowered == "cookie" \|\| lowered == "set-cookie"`, an EXACT-name test, while Phase 21 had already widened the prompt path to name-contains-`cookie`, so `X-Cookie`, `Cookie2`, `Set-Cookie2`, `X-Original-Cookie` and `X-Forwarded-Cookie` passed through unstripped via `request_parse` / `response_parse` — see the unedited reopening section dated 2026-08-24 at the foot of this file for the full narrative. **(3) CLOSED again 2026-08-24 by Phase 27 (plans 27-01, 27-02, 27-03), on source re-read in the closing task rather than on any SUMMARY's assertion.** The rule is now one symbol: `fun isCookieHeaderName(name: String): Boolean = name.lowercase(Locale.ROOT).contains(COOKIE_NAME_TOKEN)` (`Redaction.kt:158`; `COOKIE_NAME_TOKEN = "cookie"` at `:91`), and both prompt-path regexes are composed from that same token (`:107-113`), so predicate and regexes cannot drift apart by construction. `sanitizeHeaders` now carries exactly one cookie test and it is a call to that predicate: `if (policy.stripCookies && Redaction.isCookieHeaderName(name))` (`McpToolHelpers.kt:336`). **Scope of the singularity claim, stated positively so it cannot silently widen:** `isCookieHeaderName` is the single cookie-header-name rule across **the two redaction paths and the passive-scan admitter** — `Redaction.apply`'s two regexes, `McpToolHelpers.sanitizeHeaders` (`:336`) and `PassiveAiScannerFilters.sanitizeHeadersForPrompt` (`:186`) — and at no wider scope than those three sites. Four cookie-header-name matchers survive elsewhere in `src/main/kotlin`, each classified non-redacting by plan 27-01 against its consumer chain: the **passive-scan cookie-section extractor** (`PassiveAiScannerAnalysis.kt:267`) — its output reaches the prompt only through `redactScanMetadata`, which calls `Redaction.apply` unconditionally; the **local-only scanner heuristics** (`PassiveAiScannerHeuristics.kt:102` and `:117`, `ActiveAiScanner.kt:936`) — each reduces a cookie value to a boolean that never crosses the process boundary; the **bounty-prompt extractor** (`BountyPromptTagResolver.kt:144,150`) — it filters text `Redaction.apply` has already processed; and the **active-scanner request mutator** (`ActiveAiScanner.kt:1411`) — it writes an attack payload to the TARGET, not to an AI backend. **Both sweeps were run in the closing task; output quoted as observed.** Narrow: `grep -rn 'contains("cookie")' src/main/kotlin --include=*.kt \| grep -v 'isCookieHeaderName' \| wc -l` → `0`. WIDENED, over five spelling classes (exact-name `equals`, `ignoreCase` equality, `startsWith` line-prefix, Montoya `headerValue`, substring `contains`), excluding the four classified files and the owner `Redaction.kt` → `0`. The widened sweep is the one that supports the sentence above; the narrow one alone could not see the four survivors. **Guarded by three tests, each named with the narrowing it actually covers:** `McpToolHelpersTest.cookieHeaderNameVariantsAreStrippedOnTheToolResultPath` (the tool-result outcome); `CookieHeaderNameParityTest.everyNameThePromptPathStripsIsMatchedByTheSharedPredicate` (guards a narrowing of the PREDICATE — per plan 27-02's measured red probe 2 it does NOT guard a narrowing of the prompt-path REGEXES, which `RedactionTest.cookieHeaderNameVariantsAreStripped` guards instead); and `CookieHeaderRuleOwnershipTest` (3 tests, green in the closing task — a TRIPWIRE bounded to those five measured spelling classes and stated as such in its own file header, not a proof of exhaustive coverage: a matcher spelled outside them stays invisible to it). **Commits:** `02d71c2` (the fix), `fe379e5` (predicate shared with the admitter, ownership tripwire), `33b3c33` and `b7519c5` (parity test, tool-result order and collapse assertions). **What is NOT closed, named so this row cannot be read as more than it is:** **AR-27-01** — `McpToolContext.redactIfNeeded` still cannot recover a header `sanitizeHeaders` misses, because its output is single-line JSON while both cookie regexes are line-anchored `(?im)^…$`; **AR-27-02** — `cookie` remains absent from `SENSITIVE_WORDS`, so `jsonSecretKeyRegex` is not a backstop either; **AR-27-03** — byte-identically-named headers collapse to one entry in the tool-result header map (CP-27-02-01, human-decided: privacy-safe and asserted to leak no original value, but it costs analysis signal). **Locale scope, stated narrowly on purpose:** the explicit `Locale.ROOT` argument is present only at the two header-name functions this phase changed (`Redaction.isCookieHeaderName`, `McpToolHelpers.sanitizeHeaders`), and per plan 27-01's MEASURED backlog observation it is defensive documentation rather than a defect fix — Kotlin's no-argument `lowercase()` already compiles to `toLowerCase(Locale.ROOT)`, so `src/main/kotlin` holds **zero** locale-sensitive lowering call sites today (the 5 `toLowerCase(` and 1 `lowercase(Locale.getDefault())` hits are all inside comments this phase added). What ships is a guard against introducing the hazardous Java spelling, NOT the closure of an active hazard; the backlog item 27-01 records is that guard, not a migration. | closed |
| T-26-02-02 | Information Disclosure | `maybeAnonymizeUrl` | medium | mitigate | STRICT replaces the host and only the host; a malformed URL falls back to returning the input rather than throwing into the tool result. **Verified:** `McpToolHelpers.kt:335-356` (`catch (_: Exception) { rawUrl }`); `McpToolHelpersTest.MaybeAnonymizeUrl`. | closed |
| T-26-02-03 | Tampering | `resolveReportPath` | high | mitigate | Path containment above `user.home` asserted as a REJECTION for both the relative-with-parent-segments and absolute-outside-home forms, proven falsifiable by a recorded red probe that deleted the containment check. **Verified:** `McpToolHelpers.kt:374` — `require(resolved.startsWith(home))` after `normalize()`; `McpToolHelpersTest.ResolveReportPath` (7 tests). | closed |
| T-26-02-04 | Spoofing | `toMontoyaServiceOrNull` | medium | mitigate | Blank hostname and non-positive port return null, so a partially-specified model-supplied target cannot become an outbound request destination. **Verified:** `McpToolModels.kt:17-20`; `McpToolModelsTest`. | closed |
| T-26-02-05 | Tampering | tool-input deserialisation | medium | mitigate | A payload missing a required field FAILS rather than defaults, so a model-emitted call cannot acquire a parameter value the model never wrote. **Verified:** `mcp/schema/SerializationTest.kt` (new package, first coverage of the MCP wire schema). | closed |
| T-26-02-06 | Denial of Service | `truncateIfNeeded` | low | mitigate | The byte-bound is asserted on a multi-byte UTF-8 payload, so the MCP body cap cannot be defeated by character-vs-byte confusion. **Verified:** `McpToolHelpersTest.TruncateIfNeeded`. | closed |
| T-26-02-07 | Repudiation | assertion-free coverage | medium | mitigate | Prohibition: a test that executes production code without asserting is out of bounds; floors stated per FILE as well as per package so they cannot be reached by bulk data-class construction. **Verified:** `26-COVERAGE.md` — all 14 floors MET, reproduced from `jacocoTestReport.xml` by `26-VERIFICATION.md` truth 2. | closed |
| T-26-03-01 | Spoofing | `SsrfGuard.IPV6_REGEX` | medium | mitigate | The IPv4-mapped IPv6 spelling of a private or metadata address is classified identically to its hex spelling, asserted in both directions with a recorded red probe. **Verified:** `SsrfGuard.kt:55` — `^[0-9a-fA-F:.]+$`; `SsrfGuardTest`. | closed |
| T-26-03-02 | Information Disclosure | `SsrfGuard.resolveIpv6Literal` | high | mitigate | Widening the character class must not widen what reaches the one resolving call. **Verified:** the `host.contains(':')` conjunct is unchanged (`SsrfGuard.kt:79`), `resolveIpv6Literal` appears exactly twice in `src/main` (call site + declaration), and `SsrfGuardNoResolutionTest`'s JVM-wide counter still asserts zero lookups over the enlarged corpus. | closed |
| T-26-03-03 | Denial of Service | `SsrfGuard` loopback exclusion | medium | mitigate | `http://[::ffff:127.0.0.1]/` asserted false, so local Ollama and LM Studio users do not start seeing a warning they will learn to ignore. Notice fatigue is a real failure mode for a safety control. **Verified:** `SsrfGuardTest`. | closed |
| T-26-03-04 | Spoofing | weak MCP token vs takeover proof | high | mitigate | `McpSettings.isTokenWeak` plus the RISK notice make the offline-guessing residual (25-REVIEW WR-01) visible to the operator in every mode the takeover path runs in. **Verified:** `McpSettings.kt:68`, `Defaults.MCP_MIN_TOKEN_LENGTH = 32`, consumed at `SettingsPanelMcpTabs.kt:636` ungated by external mode; `McpTokenStrengthTest` asserts the notice builder actually calls the predicate. Advisory by design — a control that rewrites the operator's credential was out of bounds. Residual carried as **AR-25-05**. | closed |
| T-26-03-05 | Denial of Service | over-strict token floor | medium | mitigate | The floor never fires against `McpSettings.generateToken()`'s own output over ≥50 samples, and the relation between the two is itself asserted so raising the constant alone fails. **Verified:** `McpTokenStrengthTest`. | closed |
| T-26-03-06 | Information Disclosure | `SecretCipher.decrypt` fail-soft | medium | mitigate | The empty-string-on-authentication-failure contract is asserted as an observable outcome, so a future change returning the raw ciphertext or undecrypted payload fails loudly. **Verified:** `SecretCipher.kt:92-99` returns `""` on version mismatch and on GCM failure. | closed |
| T-26-03-07 | Information Disclosure | `RedactionPolicy.fromMode` | high | mitigate | The flag triple per privacy mode is asserted per flag — this is the table `sanitizeHeaders` and `Redaction.redact` both branch on, and a silent flip in it is the PRIV-05 shape. **Verified:** `Redaction.kt:26-46` (STRICT `true/true/true`, BALANCED `true/true/false`, OFF `false/false/false`); `RedactionPolicyTest`. | closed |
| T-26-04-01 | Tampering | `ChatPanel` session maps | medium | mitigate | The documentation option was selected (SC4). All four `assertEdt()` call sites are uniform and the source no longer claims enforcement it does not have — the KDoc states the check "compiles to nothing and has no production effect at all". **Verified:** `ChatPanel.kt:819-848`, four call sites; `ChatPanelEdtGuardTest` (6 tests) pins the wording; `ChatPanelEdtConfinementTest` (23 tests) is the actual evidence the discipline holds. | closed |
| T-26-04-02 | Denial of Service | Option B throwing guard | high | mitigate | The Option-B probe was run and its result reported BEFORE the choice was made (it broke no behavioural test), and the `shutdown()` → `cancelInFlightRequest` off-EDT entry — which runs inside Burp's unload handler — was confirmed safe. Option B was then **not** adopted, so no path began throwing. **Verified:** `26-04-SUMMARY.md` § SC4 decision; ADR-17 clause 2. | closed |
| T-26-04-03 | Information Disclosure | Option C logging | high | mitigate | Option C was **not** adopted; no enforcement helper writes to Burp's Output/Errors tab. **Verified:** `assertEdt()` (`ChatPanel.kt:844-848`) is a bare `assert` with a static message — zero logging, and no message text, prompt content, session title or tool argument is interpolated anywhere in it. | closed |
| T-26-04-04 | Denial of Service | Option C log flooding | medium | mitigate | Not reachable: Option C was not adopted and the guard emits nothing. **Verified in source** — see T-26-04-03. | closed |
| T-26-04-05 | Repudiation | a green `-ea` suite standing in for proof | high | mitigate | The Option-B upgrade that would have required an `-da` proof was not taken; the deliverable is the honest KDoc plus a structural guard that runs under either flag. `edtGuardWithoutAssertionsTest` remains in the build for the `McpToolExecutorImpl` door guard. **Verified:** `ChatPanelEdtGuardTest` asserts on source text, not on `assert` behaviour, so it cannot be green-by-`-ea`. | closed |
| T-26-04-06 | Elevation of Privilege | a bypass switch | high | mitigate | Prohibition held: no global off-switch, system property or settings flag disabling the mechanism was added. **Verified:** the only `System.getProperty`/`getBoolean` read in `ChatPanel.kt` is the unrelated `migratedKey` preference at `:1912`. ADR-15 D-09 rejected the same shape for the tool gate. | closed |
| T-26-05-01 | Repudiation | missing advisory | high | mitigate | `SECURITY.md` gains explicit **SEC-04** and **PRIV-05** entries with affected versions, impact, fixed version and a user action, pinned so a later edit cannot quietly remove them. **Verified:** `SECURITY.md:51-108` (`## Security Advisories`); `SecurityDocsTest` (49 assertions). | closed |
| T-26-05-02 | Information Disclosure | overstated at-rest claim | high | mitigate | Every absolute at-rest claim is replaced by an accurate statement naming where the master key lives, so a user's threat model for storing an API key matches what ships. **Verified in-repo:** `README.md:245`, `SECURITY.md`, `SPEC.md`, `docs/anthropic-backend.md:8,23`, `docs/external-mcp-servers.md:11,23`, `DECISIONS.md` ADR-17 clause 3 — all name `secret.master.key.v1` beside the ciphertext. **Verified out-of-repo (post-phase):** the published site now carries the caveat at `backends/anthropic.md:21,30`, `mcp/external-servers.md:26` and `privacy/limitations.md:74-79` — see the audit note below. | closed |
| T-26-05-03 | Spoofing | fabricated advisory identifier | medium | mitigate | Prohibition plus an explicit sentence stating that no CVE or GHSA has been issued; an invented identifier would make the advisory unverifiable. **Verified:** `SECURITY.md:56` — "**No CVE and no GHSA identifier has been issued for either finding.**". | closed |
| T-26-05-04 | Tampering | out-of-repo writes | high | mitigate | Prohibition plus an acceptance criterion that `git status --porcelain` in the GitBook checkout prints nothing. The site change shipped as a prepared diff and a human action, never an automated cross-repository write. **Verified:** `26-GITBOOK-HANDOFF.md` is the prepared diff; `26-VERIFICATION.md` recorded the docs repo byte-unmodified at `3256cc9` at phase close, and the later site update is its own human commit (`d9712b3`) in that repository. | closed |
| T-26-05-05 | Repudiation | cache-served documentation guard | high | mitigate | Every markdown file the guard reads is declared as a `tasks.test` input, following the existing `adrRecord` declaration, and a recorded cache probe proved the task re-runs on a documentation-only edit. Without this the guard is green in exactly the commit that breaks it. **Verified:** `build.gradle.kts:184-208` — six declarations (`securityPolicy`, `readmeClaims`, `specClaims`, `uiSafetyRunbook`, `anthropicBackendDoc`, `externalMcpDoc`). | closed |
| T-26-05-06 | Repudiation | claiming a control that does not ship | high | mitigate | Every new documentation claim names the repository symbol behind it in the SUMMARY; a claim with no symbol was deleted rather than softened. **Verified:** `26-05-SUMMARY.md`; `26-VERIFICATION.md` truth 5 checked the advisory text against the shipped fix rather than against the SUMMARY. | closed |
| T-26-06-01 | Information Disclosure | takeover proof as offline verifier | high | mitigate | Recorded as ADR-16's seventh residual with an accurate statement of its mitigation, read out of `26-03-SUMMARY.md` rather than assumed. Disclosed, bounded (infeasible against the generated 32-byte token) and paired with the advisory floor. **Verified:** 7 `Residual:` bullets under `## ADR-16` in `DECISIONS.md`. Carried as **AR-25-05**. | closed |
| T-26-06-02 | Repudiation | residual guard bounded below the shipped count | medium | mitigate | `MIN_ADR16_RESIDUALS` raised to equal the shipped count in the same commit as the new bullet, proven by a red probe deleting exactly one bullet — the deletion the old bound could not catch. **Verified:** `DecisionsAdrTest.kt:34` — `MIN_ADR16_RESIDUALS = 7`, and ADR-16 ships exactly 7. | closed |
| T-26-06-03 | Repudiation | undocumented QUAL-07 dispositions | medium | mitigate | ADR-17 records all three, with the SC4 selection quoted verbatim from the blocking checkpoint rather than paraphrased. **Verified:** `DECISIONS.md:239` — `## ADR-17: QUAL-07's three dispositions`; guarded by `DecisionsAdrTest`. | closed |
| T-26-06-04 | Information Disclosure | overstated at-rest claim in the design record | high | mitigate | ADR-17 clause 3 states that the master key sits in Burp Preferences beside its ciphertext and names what the property therefore is and is not, agreeing with the user-facing wording. **Verified:** ADR-17 clause 3 names `SecretCipher.MASTER_KEY_PREF_KEY` and states the non-property explicitly ("must not be implied anywhere in this repository's documentation"). | closed |
| T-26-06-05 | Denial of Service | non-loopback bind conflict | medium | mitigate | The operator is told the real reason and what to do, instead of being told no compatible server was found when the listener was their own. **Verified:** `McpSupervisor.kt:379-396` — the non-loopback limb is explicit and names the bound host; first test coverage plus the repo's first non-loopback fixture. | closed |
| T-26-06-06 | Spoofing | removing the loopback gate | high | **accept** | Explicitly NOT done. Dropping the gate would extend certificate-pinned takeover to non-loopback hosts — plausibly an improvement, but it changes when this extension shuts down a remote listener, on a path with no prior test coverage, inside a phase scoped to coverage and documentation. **Verified:** `isLoopbackUrlHost` gate intact at `McpSupervisor.kt:379`, `:426`. Recorded as an ADR-17 residual and a backlog item. See Accepted Risks. | closed |
| T-26-06-07 | Tampering | drifting line-number citations in ADRs | low | mitigate | Prohibition plus a criterion that the count of `.kt:NNN` citations in `DECISIONS.md` does not increase — ADR-15 records that eighteen such citations in one phase were every one of them wrong within that phase. **Verified:** 6 before the 26-06 wave, 6 after, 6 today. No increase. | closed |
| T-26-07-01 | Repudiation | `detekt.yml` weakening | high | mitigate | Prohibition plus a per-task criterion that `git diff --quiet detekt.yml` exits 0. A count that falls because a rule stopped firing is not progress and would misreport QUAL-07. **Verified:** `git log ab567fb..HEAD -- detekt.yml` is empty — byte-identical across the whole span. | closed |
| T-26-07-02 | Repudiation | a hollow shrink | medium | mitigate | Removals split into stale entries and fixed findings, with a floor of 40 fixed findings; eleven target categories named up front and five excluded ones named with reasons. **Verified:** baseline 1096 → **1040** (56 removals), 45 backed by a source fix per `26-07-SUMMARY.md`; `./gradlew detekt --rerun-tasks` reported 0 code smells over 312 files in `26-VERIFICATION.md`. | closed |
| T-26-07-03 | Repudiation | re-baselining a fixed finding | high | mitigate | `git diff -U0 ab567fb..HEAD -- detekt-baseline.xml \| grep -c '^+.*<ID>'` must return 0 across the WHOLE phase, and the `detektBaseline` probe had to be restored before any real edit. **Verified: returns 0.** Not one `<ID>` was added to the baseline across phases 20–26. | closed |
| T-26-07-04 | Tampering | deleting a load-bearing "unused" declaration | high | mitigate | Every deletion preceded by a recorded whole-tree reference-count grep; the full suite stays green at the baseline failure and skip counts and `./gradlew build` still produces the shadowJar. **Verified:** `26-VERIFICATION.md` — full suite **158 classes / 1131 tests / 0 failures / 1 skip** (grown from 880), no `@Disabled` added; `cancelCurrentRequest` has zero remaining references in `src/`, confirming it was genuinely dead rather than the neighbouring `cancelInFlightRequest`. | closed |
| T-26-07-05 | Elevation of Privilege | `UseRequire` conversion on a path-containment guard | high | mitigate | `resolveReportPath`'s two guards converted with exception type and message preserved, and 26-02's test class re-run immediately afterwards and recorded separately. **Verified:** `McpToolHelpers.kt:360,374` are `require(...)` with the original messages ("Report path is empty", "Report path must be under $home"); `McpToolHelpersTest$ResolveReportPath` — 7 tests, 0 failures. | closed |
| T-26-07-06 | Tampering | mass `@Suppress` as a shortcut | high | mitigate | Budget of at most two new `@Suppress` annotations across the plan, each on one declaration with a stated reason. **Verified:** `git diff -U0 eddd823..9f5e6a4 -- 'src/**/kotlin/*'` adds **zero** `@Suppress` annotation lines and removes zero. Budget unused. | closed |
| T-26-07-07 | Denial of Service | `ImplicitDefaultLocale` conversion | low | **accept** | Pinning `Locale.ROOT` on three `String.format` calls changes formatting under a Turkish or Arabic-digit locale — which is the defect the rule names. Recorded as a deliberate behaviour change rather than presented as cosmetic. See Accepted Risks. | closed |
| T-26-SC | Tampering | npm/pip/cargo installs | high | mitigate | **Not applicable by construction: this phase installed no package and added no Gradle dependency.** Declared identically in all seven PLAN files. **Verified:** the only `build.gradle.kts` edits in the phase are `inputs.file` declarations and one test-filter entry — the `dependencies` block is untouched; the repository has no `package.json`, `requirements.txt` or `Cargo.toml`. No `[ASSUMED]`/`[SUS]` package exists to gate. | closed |

*Status: open · closed · open — below high threshold (non-blocking)*
*Severity: critical > high > medium > low — only open threats at or above `workflow.security_block_on` (`high`) count toward `threats_open`*
*Disposition: mitigate (implementation required) · accept (documented risk) · transfer (third-party)*

---

## Accepted Risks Log

| Risk ID | Threat Ref | Rationale | Accepted By | Date |
|---------|------------|-----------|-------------|------|
| AR-26-01 | T-26-01-04 | Five CLI helpers widened from `private` to `internal` so they can be asserted without reflection. `internal` is module-scoped, this is a single-module Gradle build with no published Kotlin API, and the helpers are pure transforms over data the caller already holds. Same trade-off `buildTimeoutMessage` and `buildCopilotCommand` already made in the same file. | Plan 26-01 threat model | 2026-08-22 |
| AR-26-02 | T-26-06-06 | The `McpSupervisor` takeover path keeps its loopback gate: certificate-pinned takeover is not extended to non-loopback hosts. Plausibly an improvement, but it changes when this extension shuts down a *remote* listener, on a path with no prior test coverage, inside a phase scoped to coverage and documentation. The operator now gets an honest diagnostic naming the bound host instead of a misleading "no compatible server found". | Plan 26-06 threat model; ADR-17 residual + backlog item | 2026-08-22 |
| AR-26-03 | T-26-07-07 | Three `String.format` calls pinned to `Locale.ROOT`. This is a deliberate behaviour change, not a cosmetic edit: output differs under a Turkish or Arabic-digit locale — which is precisely the defect `ImplicitDefaultLocale` names. Accepted because stable machine-readable formatting is the correct property for these call sites. | Plan 26-07 threat model | 2026-08-22 |
| AR-26-04 | T-26-03-04 | The MCP weak-token control is **advisory**, not enforcing: an operator who ignores the RISK notice and keeps a short token remains exposed to offline recovery of that token from a captured takeover proof. Enforcing a minimum would break existing configurations on upgrade and would mean the extension rewriting the operator's credential. Same residual as **AR-25-05**; recorded as ADR-16's seventh residual. | Plan 26-03 threat model; ADR-16 seventh residual | 2026-08-22 |

---

## Security Audit Trail

| Audit Date | Threats Total | Closed | Open | Run By |
|------------|---------------|--------|------|--------|
| 2026-08-24 | 46 | 46 | 0 | `/gsd-secure-phase 26` (orchestrator, ASVS L1 source verification) |
| 2026-08-24 | 46 | 45 | **1** | `/gsd-audit-milestone` — T-26-02-01 reopened (see below) |
| 2026-08-24 | 46 | 46 | 0 | Phase 27 (27-03) — source re-verification |

**Note on the count.** 46 register rows across 46 distinct threat IDs. The seven PLAN files declare
52 rows in total, but `T-26-0N-SC` is the same supply-chain threat declared identically in all seven
and is counted once as `T-26-SC`.

---

## Sign-Off

- [x] All threats have a disposition (mitigate / accept / transfer)
- [x] Accepted risks documented in Accepted Risks Log
- [x] `threats_open: 0` confirmed — T-26-02-01 re-closed 2026-08-24 by Phase 27 (27-03)
- [x] `status: verified` set in frontmatter — restored after the Phase 27 closure

**Approval:** RE-APPROVED 2026-08-24 by Phase 27 (plan 27-03), after the 2026-08-24 withdrawal
recorded in the reopening note below. The re-approval rests on source read in the closing task —
`Redaction.kt:158`, `McpToolHelpers.kt:336`, `PassiveAiScannerFilters.kt:186` — plus both ownership
sweeps returning `0` and a green `CookieHeaderRuleOwnershipTest`, and it is scoped to the two
redaction paths and the passive-scan admitter. It is NOT a re-approval of the L1 pass that produced
the original false close; the standing rule below is what that pass was missing.

### What this audit did and did not do

**Did:** verified in source that each `mitigate` threat's declared control exists, at ASVS L1
(source-level evidence — symbol presence, absence of the removed weakness, guard branches, and
repository-state invariants such as baseline diffs and citation counts). Every "**Verified:**" claim
in the register above is a check run against the tree at `1.0.0`, not a restatement of a SUMMARY
assertion. Where a plan stated a numeric criterion (`0` baseline additions, `≤2` new `@Suppress`,
`.kt:NNN` count not increasing, 1096 → fewer entries), the criterion was re-executed rather than
read.

**Did not:** re-derive the register from the implementation, or scan for threats the plans did not
declare. The workflow constrains a plan-authored register to mitigation verification.

**One threat's status changed after the phase closed.** `26-VERIFICATION.md` (2026-08-22) recorded
SC6 as `gaps_found` because the published GitBook site at `~/Tools/burp-ai-agent-doc` still carried
the uncaveated at-rest claim — the half of T-26-05-02 that lives outside this repository. That gap
is now closed: the docs repository carries commit `d9712b3` ("docs: sync the site to 1.0.0 —
security accuracy, advisories, stale claims") and its pages now state that the master key sits in
Burp Preferences beside the ciphertext, with the tool-call confirmation flow documented across three
pages. This audit re-checked that repository directly rather than trusting the handoff document.
T-26-05-04's prohibition held throughout: the site change is a human commit in the repository that
owns it, not an automated cross-repository write.

**Known limit of L1 depth:** source-level evidence proves a control is present, not that it is
correct under every input. The behavioural proof for these threats lives in the phase's test suite
(`ShellEscapeTest`, `McpToolHelpersTest`, `SsrfGuardNoResolutionTest`, `McpTokenStrengthTest`,
`SecurityDocsTest`, `ChatPanelEdtGuardTest`) and in `26-VERIFICATION.md`, which executed the full
suite — 1131 tests, 0 failures.

### Standing rule added 2026-08-24 (Phase 27)

Two clauses, both learned in this file. They bind every future audit pass in this repository, not
only ASVS L1 ones.

**(i) Width, not only presence.** Verifying that a control is PRESENT is not sufficient to close a
threat about that control's COVERAGE. Where one rule has more than one implementation, an L1 pass
must compare the WIDTH of each implementation against the requirement's wording *and* against every
sibling implementation, and must NAME the siblings it compared. **Worked example: T-26-02-01.** The
original pass verified that `sanitizeHeaders` lowercases before comparing — true, and asserted by
tests — and closed the threat on that. It never compared the matcher against its sibling on the
prompt path, which is the entire reason the threat was written: a second, independent redaction path
narrower than the first leaves the milestone's "by ANY path" claim stronger than what ships. An
audit that cannot name the siblings it compared has not answered the width question.

**(ii) No verification narrower than its claim.** A closing note may not be verified by a search
narrower than the claim it makes. If the note says "across X", the sweep must cover every spelling a
reasonable implementer would use inside X, and the note must state the scope the sweep actually
covered. **Worked example: this phase's own first draft.** It was about to certify a claim covering
the entire source tree on the strength of a single-spelling `grep -rn 'contains("cookie")'`, while
four hand-written cookie-header-name matchers survived in other spellings that grep could not see.
Once the sweep was widened to five spelling classes, the claim was scoped down to the two redaction
paths and the passive-scan admitter, and the four survivors were named and classified. The lesson is
NOT that the code was wrong — every survivor was non-redacting and the code was fine. The lesson is
that the VERIFICATION was narrower than the SENTENCE, and that gap is precisely how a register drifts
wider than the control it describes. That drift is what produced the false close this file records.


---

## Reopening — 2026-08-24, v0.10.0 milestone audit

The ASVS L1 pass earlier today marked T-26-02-01 `closed`. The cross-phase integration check run by
`/gsd-audit-milestone` found that verdict wrong, and re-verification confirms it.

**What the L1 pass checked, and why it was not enough.** It verified that `sanitizeHeaders`
lowercases the header name before every comparison — which is true, and which is what
`McpToolHelpersTest.SanitizeHeaders` asserts. It did not compare the matcher against its SIBLING on
the prompt path. That comparison is the whole point of the threat: T-26-02-01 exists because
`sanitizeHeaders` is a *second, independent* redaction path, and a control that is narrower than its
sibling leaves the milestone's claim ("cookie values do not reach an AI backend by ANY path")
stronger than what ships. Source-level presence proved the control exists; it could not prove the
control is as wide as the requirement.

**Measured, not inferred.** `Redaction.COOKIE_NAME_PART` is `[A-Za-z0-9-]*`, so the prompt path's
`cookieHeaderRegex` matches `X-Cookie: …`. `sanitizeHeaders`' exact-name test does not. Applying
both regexes to `sanitizeHeaders`' actual single-line JSON output matches neither, and `cookie` is
not in `SENSITIVE_WORDS`, so `jsonSecretKeyRegex` does not fire either.

**Cross-reference.** `.planning/codebase/CONCERNS.md` records this exact class as **W-A CLOSED —
fixed, not accepted** (maintainer-decided 2026-08-13) for the prompt path, with the reasoning that
name-contains-`cookie` is a bounded and complete predicate. That reasoning applies unchanged here;
the fix was simply never mirrored into the sibling path added three phases later.

**Remedy.** One line: `McpToolHelpers.kt:321` becomes a name-contains test mirroring
`Redaction.COOKIE_NAME_PART`, with `set-cookie` kept mutually exclusive the way the prompt path
does it. That belongs in a closure phase with its own red probe, not in an audit commit.
