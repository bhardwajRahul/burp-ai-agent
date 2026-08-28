---
phase: 26
slug: coverage-static-analysis-debt-docs
status: verified
# threats_open = count of OPEN threats at or above workflow.security_block_on severity (the blocking gate)
# COMPUTED, never hand-written (phase 27, plan 27-06). A hand-written counter is how the
# 2026-08-24 overclaim survived review. Re-derive it from the register rows with:
#   awk -F'|' '/^\| T-26-/ { sev=$5; st=$(NF-1); gsub(/[ *`]/,"",sev); gsub(/[ *`]/,"",st);
#       if (st != "closed" && (sev == "high" || sev == "critical")) c++ } END { print c+0 }' \
#     .planning/phases/26-coverage-static-analysis-debt-docs/26-SECURITY.md
# Output on 2026-08-24 after the plan 27-06 amendment: 0  (46 rows scanned, 46 closed).
# The value below is that output. AR-27-04 and AR-27-05 are OPEN findings at MEDIUM severity,
# which is below the `high` blocking gate and therefore correctly outside this count — they are
# recorded in the Accepted Risks Log and in the evidence section beneath it, not erased by it.
#
# ── THE COUNTER'S POPULATION, STATED EXPLICITLY (phase 27, plan 27-09) ──────────────────────
# This bound existed from the day the command was written and was NEVER STATED, which is the
# "gate counting the wrong population" anti-pattern this phase has already paid for once.
#
#   POPULATION: rows of the Threat Register beginning `| T-26-`. Nothing else.
#   OUTSIDE IT: every finding recorded ONLY in the Accepted Risks Log — the whole AR-26-* and
#   AR-27-* series. An `AR-` finding therefore CANNOT move this counter, at ANY severity.
#
# The consequence a reader must hold onto: a `0` here means "no OPEN register ROW at or above
# `high`". It does NOT mean "no open finding at or above `high`". Those two sentences differ,
# and the difference is exactly how a high finding could sit open beneath a counter reading 0.
#
# Re-run 2026-08-25 (plan 27-09) after the clause (5) amendment: output 0, 46 rows scanned,
# 46 closed. The population did not change — no register row was added, amended or reclassified
# by plans 27-07, 27-08 or 27-09.
#
# THE `0` IS ATTRIBUTABLE, NOT MERELY ASSERTED. Every finding produced by plans 27-07 and 27-08
# is below the `high` blocking severity, listed here with its severity and its provenance so the
# claim can be checked rather than trusted:
#   AR-27-06  MEDIUM  — AUTHORED by analogy with AR-27-05's no-backstop reasoning, NOT measured.
#   AR-27-07  LOW     — MEASURED (27-08-SUMMARY.md, measurement 1), against the 27-08 plan
#                       register's authored `medium`; the disagreement is recorded, not resolved
#                       silently, and the measured value is the one used here.
#   AR-27-08  MEDIUM  — MEASURED (27-08-SUMMARY.md, measurement 2), with a firing positive
#                       control and source-cited reachability. Neither rounded up nor down.
#   T-27-07-04 MEDIUM — re-measured by plan 27-07 and unchanged.
# Had ANY of these landed at `high`, this counter could not have stayed 0 honestly: the finding
# would have needed a register ROW inside the population above, or the command and this comment
# would have needed amending together. Neither was necessary; recorded so the next reader knows
# the question was ASKED rather than skipped.
#
# ── RE-RUN 2026-08-26 (phase 27, plan 27-13), after the clause (6) amendment ────────────────
# Raw output: 0.  46 rows scanned, 46 closed.  The value below is that output, not a number
# carried across from the 2026-08-25 run. No register row was added, amended or reclassified by
# plans 27-10, 27-11, 27-12 or 27-13; the one row this round TOUCHED, T-26-02-01, gained clause
# (6) and changed neither its severity (`high`) nor its status (`closed`), so it cannot move the
# count in either direction.
#
# THE POPULATION, RESTATED — unchanged from the 2026-08-25 statement above, and repeated here
# rather than cross-referenced because a bound stated once and then relied on twice is how the
# first omission happened:
#
#   POPULATION: rows of the Threat Register beginning `| T-26-`. Nothing else.
#   OUTSIDE IT: every finding recorded ONLY in the Accepted Risks Log — the whole AR-26-* and
#   AR-27-* series. An `AR-` finding CANNOT move this counter, at ANY severity.
#
# THE QUESTION THAT POPULATION FORCES, ASKED AND ANSWERED EXPLICITLY THIS ROUND. Both findings
# opened by plan 27-13 are `AR-` rows and therefore sit outside the count. Their severities:
#   AR-27-09  LOW  — MEASURED (27-11-SUMMARY.md, "The Indented-Header Measurement"): the
#                    leading-whitespace / obs-fold logical-line start, surviving BYTE-UNCHANGED
#                    under STRICT *and* BALANCED — one mode WIDER than 27-VERIFICATION-3.md
#                    recorded. Bounded LOW because no measured emission site in this repository
#                    indents a header line; open because reachability through analyst-authored
#                    `HttpRequestResponse.notes` text is UNMEASURED.
#   AR-27-10  LOW  — the thirteen RFC 9110 tchars outside the widened COOKIE_NAME_PART. The
#                    PARTITION (77 = 64 + 13) and the fail-open MECHANISM are MEASURED
#                    (27-10-SUMMARY.md §6 and §3, the covered set read from Redaction.kt at test
#                    time); the carry-over of that mechanism to the other thirteen characters is
#                    INFERRED and is labelled as inferred in the row. NO LEAK WAS MEASURED FOR
#                    ANY OF THE THIRTEEN and the row does not claim one.
# [SUPERSEDED 2026-08-26 by plan 27-17 — AR-27-09 is CLOSED BY FIX, not open at LOW. The LOW
#  above rested on an UNMEASURED reachability claim; the maintainer decided it by FIX at UAT
#  (27-HUMAN-UAT.md item 10). The sentence above is preserved as the record while it was open.
#  It is an AR- row, so it was outside the threats_open population then and now: counter unmoved.]
#
# NEITHER IS AT OR ABOVE THE `high` BLOCKING GATE, so NO REMEDY WAS REQUIRED and none was
# applied: the `awk` command is UNAMENDED, its population definition is UNAMENDED, and neither
# finding was given a `T-26-` id inside the population. Had either landed at `high`, the honest
# options were exactly two — give it a register ROW inside the population, or amend the command
# and this comment TOGETHER — and leaving the counter reading `0` was not among them. This
# paragraph exists because "below the gate" is a conclusion a reader must be able to CHECK
# rather than infer, and because a counter reading 0 beside an open high finding inside its
# population is the single overclaim this file exists to stop.
#
# The four findings carried forward from earlier rounds are unchanged and all remain below the
# gate: AR-27-04 MEDIUM (open, still owed a HUMAN decision), AR-27-06 MEDIUM (authored by
# analogy), AR-27-07 LOW (measured), AR-27-08 MEDIUM (measured, Burp-held traffic, owned by
# Phase 28). Six named residuals is NOT a completeness claim.
#
# ── RE-RUN 2026-08-26 (phase 27, plan 27-16), after the clause (7) amendment ────────────────
# Raw output: 0.  46 rows scanned, 46 closed.  The value below is that output, run against the
# AMENDED file after every other edit of plan 27-16, and NOT carried across from the 2026-08-26
# plan-27-13 run. No register row was added, amended or reclassified by plans 27-14, 27-15 or 27-16;
# the one row this round TOUCHED, T-26-02-01, gained clause (7) and changed neither its severity
# (`high`) nor its status (`closed`), so it cannot move the count in either direction. Plan 27-15
# amended standing-rule clause (vi) in prose only and changed zero register rows.
#
# THE POPULATION, RESTATED IN FULL — unchanged from the two statements above, and repeated here in
# full rather than cross-referenced, because a bound stated once and then RELIED ON three times is
# the shape of the first omission this comment exists to correct:
#
#   POPULATION: rows of the Threat Register beginning `| T-26-`. Nothing else.
#   OUTSIDE IT: every finding recorded ONLY in the Accepted Risks Log — the whole AR-26-* and
#   AR-27-* series. An `AR-` finding CANNOT move this counter, at ANY severity.
#
# THE QUESTION THAT POPULATION FORCES, ASKED AND ANSWERED EXPLICITLY FOR THE ONE FINDING ROUND 5
# OPENED. `AR-27-11` is an `AR-` row and therefore sits OUTSIDE the count at any severity — that is
# a property of the population, not of the finding, and it would hold even if the finding were
# `critical`. Its severity:
#   AR-27-11  LOW  — MEASURED in both directions (27-14-SUMMARY.md PROBE D: matching before the
#                    narrowing, BYTE-UNCHANGED after) and with its REACHABILITY measured by plan
#                    27-16 rather than assumed: `mcp/schema/Serialization.kt` declares ZERO
#                    `List<String>` fields, multi-item results are joined with `\n\n` and carry no
#                    JSON array wrapper, and the five `List<String>` models under
#                    `McpToolModels.kt` are INPUT-only. Exactly ONE carrier can emit an arbitrary
#                    JSON array of strings through `Redaction.apply` — the D-03 outbound-privacy
#                    redaction of model-authored `argsJson` in
#                    `McpToolExecutorImpl.routeExternalToolCall` — and the remote tool schemas it
#                    forwards to are NOT owned here and are UNMEASURED. Bounded LOW because a
#                    realistic raw HTTP message inside an array element is STILL redacted (its
#                    header follows an escaped newline, which IS a recognised start — measured, with
#                    two positive controls firing in the same run).
#
# IT IS NOT AT OR ABOVE THE `high` BLOCKING GATE, so NO REMEDY WAS REQUIRED and none was applied:
# the `awk` command is UNAMENDED, its population definition is UNAMENDED, and the finding was not
# given a `T-26-` id inside the population. HAD IT LANDED AT OR ABOVE THE GATE, THE HONEST OPTIONS
# WERE EXACTLY TWO — give it a register ROW inside the population above, or amend the command AND
# this population comment TOGETHER — and leaving the counter reading `0` was NOT among them. This
# paragraph exists because "below the gate" is a conclusion a reader must be able to CHECK rather
# than infer.
#
# The findings carried forward from earlier rounds are unchanged by round 5 and all remain below the
# gate: AR-27-04 MEDIUM (open, still owed a HUMAN decision, NOT relitigated by round 5), AR-27-06
# MEDIUM (authored by analogy), AR-27-07 LOW (measured), AR-27-08 MEDIUM (measured, Burp-held
# traffic, owned by Phase 28), AR-27-09 LOW (measured), AR-27-10 LOW (partition measured, carry-over
# inferred). Seven named residuals is NOT a completeness claim — see standing-rule clause (vii),
# which adds that a residual list must also enumerate what the round INTRODUCED.
# [SUPERSEDED 2026-08-26 by plan 27-17 — AR-27-09 is CLOSED BY FIX, not open at LOW. The LOW
#  above rested on an UNMEASURED reachability claim; the maintainer decided it by FIX at UAT
#  (27-HUMAN-UAT.md item 10). The sentence above is preserved as the record while it was open.
#  It is an AR- row, so it was outside the threats_open population then and now: counter unmoved.]
#
# ── RE-RUN 2026-08-26 (OUT-OF-PLAN CORRECTION, maintainer-authorised after 27-REVIEW-3 CR-01) ──────
# Raw output: 0.  46 rows scanned, 46 closed.  RECOMPUTED with the command above against the AMENDED
# file after every other edit of this correction, never hand-edited and not carried across from the
# plan-27-16 run. Command and raw output, so the number is checkable rather than trusted:
#   $ awk -F'|' '/^\| T-26-/ { sev=$5; st=$(NF-1); gsub(/[ *`]/,"",sev); gsub(/[ *`]/,"",st);
#         if (st != "closed" && (sev == "high" || sev == "critical")) c++ } END { print c+0 }' \
#       .planning/phases/26-coverage-static-analysis-debt-docs/26-SECURITY.md
#   0
#
# WHAT THIS CORRECTION CHANGED, AND WHY THE COUNTER DID NOT MOVE. `AR-27-11`'s SEVERITY was RAISED
# from LOW to MEDIUM, and its stated bound was corrected from ONE family to the FOUR that were
# measured (27-REVIEW-3 CR-01, reproduced independently before the correction was written). No
# register ROW was added, amended or reclassified: `AR-27-11` is an `AR-` row and sits OUTSIDE the
# `| T-26-` population at ANY severity — that is a property of the POPULATION, not of the finding —
# and `medium` is in any case below the `high` blocking gate. So the `awk` command is UNAMENDED, its
# population definition is UNAMENDED, and no `T-26-` id was created. THE QUESTION WAS ASKED AND IS
# ANSWERED HERE RATHER THAN SKIPPED: had the re-derivation landed at `high`, the honest options were
# exactly two — a register ROW inside the population, or amending the command AND this comment
# together — and leaving the counter reading `0` was NOT among them. It landed at `medium`, and the
# bound on that number is stated in the row: no LIVE producer was measured, and what would move it to
# `high` (a measured instance of such a response body on real proxied traffic) is named there.
#
# THE POPULATION, UNCHANGED and restated rather than cross-referenced, for the fourth time:
#
#   POPULATION: rows of the Threat Register beginning `| T-26-`. Nothing else.
#   OUTSIDE IT: every finding recorded ONLY in the Accepted Risks Log — the whole AR-26-* and
#   AR-27-* series. An `AR-` finding CANNOT move this counter, at ANY severity.
#
# The other findings are unchanged by this correction and all remain below the gate: AR-27-04 MEDIUM
# (open, still owed a HUMAN decision), AR-27-06 MEDIUM (authored by analogy), AR-27-07 LOW (measured),
# AR-27-08 MEDIUM (measured, Burp-held traffic, owned by Phase 28), AR-27-09 LOW (measured), AR-27-10
# LOW (partition measured, carry-over inferred). Two MEDIUM findings now name BURP-HELD traffic —
# AR-27-08 and AR-27-11 — and they are NOT duplicates: different carrier, different preconditions,
# and AR-27-11 is the one reachable in the DEFAULT posture. Seven named residuals is NOT a
# completeness claim.
# [SUPERSEDED 2026-08-26 by plan 27-17 — AR-27-09 is CLOSED BY FIX, not open at LOW. The LOW
#  above rested on an UNMEASURED reachability claim; the maintainer decided it by FIX at UAT
#  (27-HUMAN-UAT.md item 10). The sentence above is preserved as the record while it was open.
#  It is an AR- row, so it was outside the threats_open population then and now: counter unmoved.]
# [AMENDED 2026-08-27 (phase 28, plan 28-03). Two Accepted Risks Log changes: AR-27-08's cell was
#  APPENDED to under a dated supersession marker (the route is now CONTROLLED by
#  ScannerIssueSupport.sanitizeInjectionPointValue; prior text preserved as a byte-exact prefix),
#  and a new row AR-28-01 was added at severity MEDIUM, DERIVED from plan 28-03 task 1's
#  measurements of the ResponseAnalyzer evidence tail. BOTH ARE AR- ROWS, SO BOTH SIT OUTSIDE THIS
#  COUNTER'S POPULATION AT ANY SEVERITY: the population is Threat Register rows whose id begins
#  T-26- and nothing else. Re-run of the documented awk on 2026-08-27 AFTER both edits: output 0
#  (46 T-26- rows scanned, 46 closed). The value below IS that raw output; it is unchanged because
#  no register row was added, amended or reclassified, NOT because nothing was found. Phase 28's
#  own T-28-NN plan threat models are deliberately not added to this phase-26 register; phase 28
#  gets its own security artifact at verification time.]
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
| T-26-02-01 | Information Disclosure | `sanitizeHeaders` | high | mitigate | **Three-part history — read top to bottom; none of it replaces what came before.** **(1) The original narrow claim, which holds.** Case-insensitive matching of `Cookie`, `Set-Cookie`, `Authorization`, `Proxy-Authorization`, `X-API-Key`, `Api-Key` and `Host` is asserted per privacy mode (`McpToolHelpersTest.SanitizeHeaders`). **(2) REOPENED 2026-08-24 by the v0.10.0 milestone audit**, because the broad claim did not hold: the matcher was `lowered == "cookie" \|\| lowered == "set-cookie"`, an EXACT-name test, while Phase 21 had already widened the prompt path to name-contains-`cookie`, so `X-Cookie`, `Cookie2`, `Set-Cookie2`, `X-Original-Cookie` and `X-Forwarded-Cookie` passed through unstripped via `request_parse` / `response_parse` — see the unedited reopening section dated 2026-08-24 at the foot of this file for the full narrative. **(3) CLOSED again 2026-08-24 by Phase 27 (plans 27-01, 27-02, 27-03), on source re-read in the closing task rather than on any SUMMARY's assertion.** The rule is now one symbol: `fun isCookieHeaderName(name: String): Boolean = name.lowercase(Locale.ROOT).contains(COOKIE_NAME_TOKEN)` (`Redaction.kt:158`; `COOKIE_NAME_TOKEN = "cookie"` at `:91`), and both prompt-path regexes are composed from that same token (`:107-113`), so predicate and regexes cannot drift apart by construction. `sanitizeHeaders` now carries exactly one cookie test and it is a call to that predicate: `if (policy.stripCookies && Redaction.isCookieHeaderName(name))` (`McpToolHelpers.kt:336`). **Scope of the singularity claim, stated positively so it cannot silently widen:** `isCookieHeaderName` is the single cookie-header-name rule across **the two redaction paths and the passive-scan admitter** — `Redaction.apply`'s two regexes, `McpToolHelpers.sanitizeHeaders` (`:336`) and `PassiveAiScannerFilters.sanitizeHeadersForPrompt` (`:186`) — and at no wider scope than those three sites. Four cookie-header-name matchers survive elsewhere in `src/main/kotlin`, each classified non-redacting by plan 27-01 against its consumer chain: the **passive-scan cookie-section extractor** (`PassiveAiScannerAnalysis.kt:267`) — its output reaches the prompt only through `redactScanMetadata`, which calls `Redaction.apply` unconditionally; the **local-only scanner heuristics** (`PassiveAiScannerHeuristics.kt:102` and `:117`, `ActiveAiScanner.kt:936`) — each reduces a cookie value to a boolean that never crosses the process boundary; the **bounty-prompt extractor** (`BountyPromptTagResolver.kt:144,150`) — it filters text `Redaction.apply` has already processed; and the **active-scanner request mutator** (`ActiveAiScanner.kt:1411`) — it writes an attack payload to the TARGET, not to an AI backend. **Both sweeps were run in the closing task; output quoted as observed.** Narrow: `grep -rn 'contains("cookie")' src/main/kotlin --include=*.kt \| grep -v 'isCookieHeaderName' \| wc -l` → `0`. WIDENED, over five spelling classes (exact-name `equals`, `ignoreCase` equality, `startsWith` line-prefix, Montoya `headerValue`, substring `contains`), excluding the four classified files and the owner `Redaction.kt` → `0`. The widened sweep is the one that supports the sentence above; the narrow one alone could not see the four survivors. **Guarded by three tests, each named with the narrowing it actually covers:** `McpToolHelpersTest.cookieHeaderNameVariantsAreStrippedOnTheToolResultPath` (the tool-result outcome); `CookieHeaderNameParityTest.everyNameThePromptPathStripsIsMatchedByTheSharedPredicate` (guards a narrowing of the PREDICATE — per plan 27-02's measured red probe 2 it does NOT guard a narrowing of the prompt-path REGEXES, which `RedactionTest.cookieHeaderNameVariantsAreStripped` guards instead); and `CookieHeaderRuleOwnershipTest` (3 tests, green in the closing task — a TRIPWIRE bounded to those five measured spelling classes and stated as such in its own file header, not a proof of exhaustive coverage: a matcher spelled outside them stays invisible to it). **Commits:** `02d71c2` (the fix), `fe379e5` (predicate shared with the admitter, ownership tripwire), `33b3c33` and `b7519c5` (parity test, tool-result order and collapse assertions). **What is NOT closed, named so this row cannot be read as more than it is:** **AR-27-01** — `McpToolContext.redactIfNeeded` still cannot recover a header `sanitizeHeaders` misses, because its output is single-line JSON while both cookie regexes are line-anchored `(?im)^…$`; **AR-27-02** — `cookie` remains absent from `SENSITIVE_WORDS`, so `jsonSecretKeyRegex` is not a backstop either; **AR-27-03** — byte-identically-named headers collapse to one entry in the tool-result header map (CP-27-02-01, human-decided: privacy-safe and asserted to leak no original value, but it costs analysis signal). **Locale scope, stated narrowly on purpose:** the explicit `Locale.ROOT` argument is present only at the two header-name functions this phase changed (`Redaction.isCookieHeaderName`, `McpToolHelpers.sanitizeHeaders`), and per plan 27-01's MEASURED backlog observation it is defensive documentation rather than a defect fix — Kotlin's no-argument `lowercase()` already compiles to `toLowerCase(Locale.ROOT)`, so `src/main/kotlin` holds **zero** locale-sensitive lowering call sites today (the 5 `toLowerCase(` and 1 `lowercase(Locale.getDefault())` hits are all inside comments this phase added). What ships is a guard against introducing the hazardous Java spelling, NOT the closure of an active hazard; the backlog item 27-01 records is that guard, not a migration. **(4) REOPENED A SECOND TIME AND RE-CLOSED — 2026-08-24, by this phase's own verification (`27-VERIFICATION.md`, 7/9 truths), closed by plans 27-04 and 27-05, recorded by plan 27-06.** Clauses (1), (2) and (3) above are left exactly as they were written; this clause stands beside them because the history of this threat being closed wrongly TWICE is the most useful thing this row carries. **What was true when clause (3) was written.** The parent requirement PRIV-05 — "cookie values do not reach an AI backend in STRICT or BALANCED **by any path**" — was still REFUTED on a sibling path that clause (3) never compared itself against: the MCP tools that embed a RAW HTTP message inside a JSON string. Clause (3) is not false — it is scoped to `sanitizeHeaders` and never claims PRIV-05 — but the frontmatter consequence drawn from it (`threats_open: 0`, `status: verified`, both restored on 2026-08-24) was drawn while the parent requirement was still violated elsewhere. **The leak was strictly broader than the defect that created this phase.** It was not the five variant spellings; it was the CANONICAL `Cookie:` and `Set-Cookie:` names themselves, reaching an AI backend verbatim in STRICT **and** BALANCED through `proxy_http_history`, `proxy_http_history_regex`, `site_map`, `site_map_regex` and `scanner_issues`, plus the legacy executor's copy of each. **Root cause, one sentence.** A raw HTTP message embedded in a JSON string carries no real newline — `toolJson.encodeToString` writes every CRLF as the two characters `\r` / `\n` — and both cookie rules were line-anchored `(?im)^…$`, so `^` never landed on a header name and neither rule fired; unlike the parsed-header path, nothing runs `sanitizeHeaders` in front of this one. **What closed it, re-read at source in the closing task rather than taken from any SUMMARY's assertion.** `Redaction.kt` now composes its logical-line header rules from three named fragments — `JSON_ESCAPED_NEWLINE` (`:206`), `REAL_LINE_HEADER_VALUE` (`:210`) and `JSON_ESCAPED_HEADER_VALUE` (`:218-219`) — through one private composer, `logicalLineHeaderRule(namePattern)` (`:236-240`), whose real-line branch is the tail that shipped character for character, so multi-line behaviour is unchanged BY CONSTRUCTION rather than by hope. `cookieHeaderRegex` (`:242-246`) and `setCookieHeaderRegex` (`:247-248`) are built from that composer by plan 27-04; `authHeaderRegex` (`:97-105`) by plan 27-05. `COOKIE_NAME_PART` (`:119`), `COOKIE_NAME_TOKEN` (`:125`) and `isCookieHeaderName` (`:293`) are untouched — the BOUNDARY changed and no NAME did, which is why clauses (1) and (3) still hold as written. **Scope of this closure, bounded by a pinned measurement rather than by prose, because an unenumerated set of paths is how the first close went wrong.** It covers the SERIALIZED EMISSION PATH: the **14** measured `encodeToString(it.toSerializableForm(…) / it.toSiteMapEntry(…))` sites — 7 in `McpToolExecutorImpl.kt`, 7 in `McpToolLegacy.kt`, of which 10 carry raw HTTP across 5 tool names — and on that path it covers the COOKIE-HEADER class and the EXACT-NAME AUTH-HEADER class, at no wider scope. That count is pinned by `SerializedEmissionSiteInventoryTest` (5 tests, re-run green in the closing task) rather than enumerated in a sentence that can drift, and `LogicalLineBoundaryScopeTest` (3 tests, re-run green) pins that exactly three rules carry the composer and that `hostHeaderRegex` deliberately does not. Behaviour is gated by `SerializedEmissionRedactionTest` (**24** tests — 17 from plan 27-04, 7 from plan 27-05 — re-run green), and `RedactionTest` stayed at a zero-line diff and 46/46 green throughout, which is what makes the byte-identity claim evidence rather than assertion. **The auth-header outcome belongs in this row, because it is the same path and the same fix, and a reader of this row must not have to reconstruct it.** Plan 27-05 rebuilt `authHeaderRegex` through the same composer with its 16-name alternation byte-identical. Measured against the compiled classes on JDK 21, a plain-token `X-API-Key` value moved `STRICT APIKEY SURVIVES` → `STRICT APIKEY STRIPPED` in both redacting modes. Before that change the header rule did not fire at all on this shape: `Authorization: Bearer …` survived as `Bearer [REDACTED]` only because the un-anchored `bearerRegex` happened to claim the token, and a plain token had no such luck. What is NOT closed there is unchanged and stays open in `CONCERNS.md`: every vendor auth header outside the 16-name alternation — `X-Shopify-Access-Token`, `X-Amz-Security-Token` and their kind — is matched by no rule at all. **THIS CLAUSE SUPERSEDES CLAUSE (3)'s "What is NOT closed" LIST ON TWO OF ITS THREE ITEMS.** Clause (3) is left standing verbatim on purpose. **AR-27-01** is no longer an accepted residual anywhere in this record set — it is RECLASSIFIED as a live finding for the interval between its acceptance and plan 27-04, closed on the raw-message-in-JSON shape and explicitly NOT on the header-map shape; see the Accepted Risks Log. **AR-27-02** is SUPERSEDED on the raw-message-in-JSON shape only, re-decided on evidence and still load-bearing elsewhere; see the Accepted Risks Log. **AR-27-03** is unchanged. **AR-27-04** and **AR-27-05** are NEW, OPEN and measured, with their probe output quoted beneath the Accepted Risks Log. **The frontmatter counter above was COMPUTED from the rows of this register by the command recorded in the frontmatter comment, not re-asserted** — a hand-written counter is how the 2026-08-24 overclaim survived review, and the fourth Security Audit Trail row below keeps the interval during which it overclaimed visible instead of erased. **(5) REOPENED A THIRD TIME AND RE-CLOSED — 2026-08-25, by this phase's own re-verification (`27-VERIFICATION-2.md`, 8/9 truths), closed by plans 27-07 and 27-08, recorded by plan 27-09.** Clauses (1), (2), (3) and (4) above are left exactly as they were written. **This threat has now been closed wrongly THREE times** — on header NAMES (clause 2), on emission PATHS (clause 4), and now on field TYPES — and that fact is the most instructive thing this row carries. The standing rule at the foot of this file gained a clause after each of the first two; clause **(iv)** added below records what all three failed mechanisms had in common. **THE THIRD CARRIER: COOKIE-TYPED PARAMETERS.** Burp parses the `Cookie:` request header into `HttpParameterType.COOKIE` parameters, and `HttpRequest.parameters()` returns them — a fact this repository already stated in its own source and already relied on at `scanner/InjectionPointExtractor.kt:29`, which filters `it.type().name == "COOKIE"` off that same call. FOUR MCP producers emitted those parameter VALUES with **no control in front of them and no rule behind them**: `request_parse` and `params_extract`, in each of the two executors. **`request_parse` is the sharpest case, and the geometry is the point:** the leak sat in the SAME JSON object as the control, immediately below it — `headers = sanitizeHeaders(…)` at `McpToolExecutorImpl.kt:369`, the unguarded `ParsedParam(type, name, value = param.value())` emission at `:371-373` (`27-VERIFICATION-2.md` artifacts; the legacy executor carries the identical pair at `McpToolLegacy.kt:179` → `:181-183`). The `headers` map was cookie-stripped by clause (3)'s control while the `parameters` array beside it handed the identical cookie values straight back. **The control was defeated on its own output.** `Redaction.cookieTypedParamRegex`, the rule written for exactly this leak class, is keyed to the passive scanner's RENDERED `name=value (COOKIE)` suffix and reaches NEITHER MCP shape, so `redactIfNeeded` removed nothing — that no-backstop bound is **AR-27-06**, defined for the first time below. **The mitigating property, stated because it is real and because omitting it would overstate this clause:** `request_parse` and `params_extract` both parse a raw request string supplied BY THE CALLER in the tool arguments, so they ECHO a cookie the AI agent already possessed rather than exfiltrating Burp-held traffic (measured and stated as the decisive property in `27-08-SUMMARY.md`, measurement 1). That is the SAME mitigating property the ORIGINAL PRIV-05 finding carried, and it was treated as a blocker then. It is recorded here as a property of the carrier, not as a reason the carrier should have been closed. **WHAT CLOSED IT, cited by symbol, test and pinned count read from `27-07-SUMMARY.md` and `27-08-SUMMARY.md` rather than asserted.** The control is ONE type-keyed predicate — `Redaction.isCookieParameterType` (`Redaction.kt:335`, comparing against `COOKIE_PARAMETER_TYPE_NAME` at `:343`) — consumed by ONE shared sanitizer, `McpToolHelpers.sanitizeParameters` (`McpToolHelpers.kt:382`), which is now the sole producer of `ParsedParam` in this repository and is sited directly beneath `sanitizeHeaders` so the two controls on the two fields of one JSON object are read together. Pinned counts, each re-measured by plan 27-07 before and after rather than inherited: `isCookieParameterType` at **3** sites (B1); `sanitizeParameters` at **5** (B2 — one declaration plus the four producer calls at `McpToolLegacy.kt:160` and `:189`, `McpToolExecutorImpl.kt:360` and `:381`); `ParsedParam(` in the two executors **2 → 0** (B3) and in `McpToolHelpers.kt` **0 → 1** (B4); and `param.value()` in the two executors **6 → 2** (B5), the two survivors being `find_reflected`, which emits `name=… type=… count=…` and never a value. `BountyPromptTagResolver.buildRequestParameters` is gated on the same predicate at `BountyPromptTagResolver.kt:151`. **Guarded by:** `ParameterCarrierRedactionTest` (**25** tests — 18 from plan 27-07 plus 7 prompt-path preservation fixtures from 27-08 — `failures="0" errors="0"`), four added `BountyPromptTagResolverTest` probes, and a producer-ownership pin that goes red if any one of the four producers stops calling the sanitizer; plan 27-07 recorded FIVE red probes and plan 27-08 four more, each naming the specific assertion that went red. **SCOPE OF THIS CLOSURE, bounded by what was measured and no wider.** It covers the MCP PARAMETER CARRIER — the serialized `ParsedParam` shape emitted by `request_parse` and the `params_extract` line shape, in both executors — plus the bounty-prompt `parameters` tag, for the COOKIE-TYPED parameter class, at no wider scope. **What it does NOT cover, named so this clause cannot be read as more than it is:** the TRANSITIVE issue-detail carrier (**AR-27-08** — MEASURED, medium, and deliberately NOT fixed), the non-cookie parameter types (**AR-27-07** — measured low), and the `BountyPromptTagResolver` bypass of `Redaction.apply` for a token carried in a URL- or BODY-typed parameter VALUE (`T-27-07-04`, medium; the class is instantiated nowhere in `src/main/kotlin` today, re-measured at 0 by plan 27-07). **`scanner/InjectionPointExtractor.kt:29` keeps its own cookie-type predicate and is BYTE-UNCHANGED** by both plans (`git diff` returns **0** lines in each), deferred deliberately WITH the route its value feeds rather than converted in isolation: converting the predicate alone would produce a tidier file and an unchanged leak, which is the same-day-closure pattern that has now failed three times. **A NOTE ON CLAUSE (3)'s LINE CITATIONS, WHICH HAVE ROTTED — clause (3) is preserved verbatim in spite of it, and nothing until now told a reader so.** `Redaction.kt:158` and `:91`, cited in clause (3) for `isCookieHeaderName` and `COOKIE_NAME_TOKEN`, TODAY BOTH LAND INSIDE COMMENTS: wave 4 moved the declarations, which now sit at `:293` and `:125` — the numbers clause (4) already carries. Clause (3) is not edited; this sentence exists so a reader does not follow its citations into comment text and conclude the symbols were removed. **THE MECHANISM THAT SURFACED THIS CARRIER, with its bound in the same breath:** `CookieCarrierInventoryTest` (plan 27-08, 4 assertions, green) enumerates cookie-byte carriers by their SOURCE ACCESSOR rather than by any rendering — **5** accessors, **72** measured call sites across **11** files, each routed through a named control or classified from its own CONSUMER. Its own KDoc names FOUR things it cannot see, plus a fifth weaker bound on its own bookkeeping. It is a TRIPWIRE over a measured accessor set and NOT a proof of coverage, and it is recorded here at that weight. **`threats_open` in the frontmatter was RECOMPUTED from the rows of this register by the command quoted there, and that comment now states the command's POPULATION** — a bound which existed unstated until this clause and which is the "gate counting the wrong population" defect this phase has already paid for once. **(6) REOPENED A FOURTH TIME AND RE-CLOSED — 2026-08-26, by this phase's third re-verification (`27-VERIFICATION-3.md`, 12/15 must-haves, three failed), closed by plans 27-10, 27-11 and 27-12, recorded by plan 27-13.** Clauses (1), (2), (3), (4) and (5) above are left exactly as they were written; this clause stands beside them. **This threat has now been closed wrongly FOUR times** — on header NAMES (clause 2), on emission PATHS (clause 4), on field TYPES (clause 5), and now on CONSUMER POLARITY. That is the most instructive thing this row carries, and preserving the order is the point. Rounds 1 to 3 failed the SAME structural way and clause (iv) named it: a rendering-keyed mechanism blind to the next rendering. **Round 4 failed DIFFERENTLY, and twice**, which is why the standing rule below gains TWO clauses this time rather than one. **THE FOURTH REFUTATION, PART ONE: CONSUMER POLARITY.** `Redaction.isCookieHeaderName` has THREE consumers (D-27-01), and clause (3) named all three correctly. Two of them REDACT: `Redaction.apply`'s two cookie regexes, and `McpToolHelpers.sanitizeHeaders`, which STRIPS on a true result. The third, `PassiveAiScannerFilters.sanitizeHeadersForPrompt`, is an **ADMITTER** — a true result puts the header ONTO the outbound passive-scan prompt. Clause (3) even called it "the passive-scan admitter" by name. And yet this file, the plan prohibitions of this phase, and the `isCookieHeaderName` KDoc all went on asserting that the predicate being WIDER than the two regexes is fail-safe. **That claim was measurably false for one consumer in three.** Wider than the downstream rule is fail-safe for a REDACTOR and fail-OPEN for an ADMITTER, by construction: a name the predicate CLAIMS but neither regex can MATCH is admitted to the prompt and then never stripped. Nobody verified the WIDTH wrongly; the width was verified correctly and the POLARITY question was never asked. **THE DIFFERENCE SET WAS NON-EMPTY, REACHABLE, AND MEASURED.** `COOKIE_NAME_PART` was `[A-Za-z0-9-]*`, which excludes `_` — a legal RFC 9110 tchar, so `my_cookie` is a real header name and not a contrivance. MEASURED by plan 27-10 against the SHIPPED compiled classes (`build/classes/kotlin/main`, JDK 21, `Redaction.INSTANCE.apply(blob, RedactionPolicy.fromMode(mode), "probe-salt", false)`), one header line at column 0 — exactly the shape `buildScanMetadataText` emits. **PRE-FIX: `my_cookie`, `X_Cookie` and `session_cookie` each `leaked=true predicate=true` under STRICT and under BALANCED alike**, while the six canonical and variant names (`Cookie`, `X-Cookie`, `Cookie2`, `Set-Cookie2`, `X-Original-Cookie`, `X-Forwarded-Cookie`) all read `leaked=false predicate=true` on the same run. That is a cookie VALUE reaching a third-party AI backend in the strongest privacy mode, on the PROMPT path — the path this phase's own goal line calls the reference implementation. **AND IT APPEARED IN NO SECURITY RECORD UNDER `.planning/` AT ALL.** `27-VERIFICATION-3.md` grepped `26-SECURITY.md`, `CONCERNS.md`, `v0.10.0-MILESTONE-AUDIT.md` and `ROADMAP.md` for it and got ZERO hits. Unlike AR-27-04, AR-27-06, AR-27-07 and AR-27-08, this was **not a deferral with an owner — it was unrecorded**, living for three rounds in a source comment and in a green test whose failure message instructed the next engineer not to fix it. **HONEST ATTRIBUTION, because it changes what the fix is without changing whether this is a gap: THE LEAK WAS PRE-EXISTING.** `git show fe379e5` shows the admitter's conjunct was ALREADY a bare `name.contains("cookie")` before plan 27-01 replaced it with the identical shared predicate, and `COOKIE_NAME_PART` dates from Phase 21. **Phase 27 neither INTRODUCED this leak nor WIDENED it.** What phase 27 did was measure the asymmetry in wave 2, MIS-FRAME it as "fail-safe" without asking what the admitting consumer does with a true result, PIN IT GREEN, and then in wave 8 author a must-have — "no green test asserting that a cookie value SURVIVES a redacting policy is committed anywhere under `src/`" — that its own repository falsified. **THE FOURTH REFUTATION, PART TWO: THE JSON-STRING-OPEN LOGICAL-LINE START.** Clause (4) taught `logicalLineHeaderRule` that a JSON-ESCAPED NEWLINE is a line boundary, which closed the canonical-name leak wherever a header line FOLLOWED one. It never asked what happens when a header line is the FIRST content of a JSON string value, with no newline of either kind in front of it. MEASURED by plan 27-11's red probe on the real serialized emission shape — through `toolJson.encodeToString` and `McpToolContext.redactIfNeeded`, with `HttpRequestResponse.notes` as the carrier — `{"request":"GET /basket HTTP/1.1\r\nAccept: text/html\r\n\r\n","response":null,"notes":"Cookie: wibble=sentinelzulu\r\nX-Request-Id: benignidcontrolvalue"}` came back with the cookie value INTACT under STRICT, and the same shape under BALANCED. **That is the CANONICAL `Cookie:` name** — not a variant spelling, not an underscore name, not a typed parameter — **defeating the strongest privacy mode.** **The positive control fired in the SAME run:** the identical header placed AFTER an escaped newline in the same field was stripped, its `<testcase>` element self-closing with no `<failure>` child in the very JUnit XML that carries the two failures. Two probes red and the control green in ONE run is what makes this a statement about the rule's REACH rather than a broken fixture. **WHAT CLOSED THEM — 2026-08-26, symbols named, each against a RECORDED RED PROBE, and each re-read at source in this closing task rather than taken from a SUMMARY's assertion.** PART ONE: `Redaction.COOKIE_NAME_PART` widened from `[A-Za-z0-9-]*` to `[A-Za-z0-9_-]*` by plan 27-10 — **that one token is the ENTIRE non-comment production delta of that plan** — with `_` placed BEFORE the trailing `-` so the hyphen stays a literal rather than a range delimiter. The DIRECTION is load-bearing and was the maintainer's stated choice: **the REGEX side was widened and the PREDICATE was never narrowed**, because narrowing `isCookieHeaderName` would have shrunk what `McpToolHelpers.sanitizeHeaders` strips on the MCP path — the very direction that reopened this phase. Post-fix, all nine probed names read `leaked=false` under STRICT and BALANCED. The green pin was INVERTED rather than deleted, so the corpus entry keeps carrying its measurement, and it was generalised to iterate every corpus name containing `_` under an exact-count guard, so deleting one of the three turns the suite red instead of silently shrinking the evidence. PART TWO: `JSON_STRING_OPEN` added by plan 27-11 as a SECOND fixed-width lookbehind inside a non-capturing alternation on the composer's escaped branch — deliberately TWO separate fixed-width lookbehinds rather than one variable-width alternation, so the composer's previously measured 2.4x fixed-width argument is preserved BY CONSTRUCTION rather than re-measured. It is declared BELOW `JSON_ESCAPED_NEWLINE`, which remains `REQUIRED_DECLARATIONS.first()`, so the rationale-region floor still measures the region it measured before. The new start is bounded on BOTH sides: a match beginning at a string open STOPS at that string's closing quote (a byte-identity assertion on a sibling field of a real `IssueDetails` emission shape, chosen because `HttpRequestResponse` declares `notes` LAST and a sibling-after assertion there would have had nothing to bite on), and the header-MAP shape stays OUT of reach (gated on the ABSENCE of the `[STRIPPED]` marker, never on the survival of a value). **`RedactionTest` carried a ZERO-LINE diff and 46/46 green across both plans**, which is what makes the byte-identity claim evidence rather than assertion; `./gradlew check` exited zero at the end of each. **SCOPE OF THIS CLOSURE, bounded in the same breath, because an unbounded closure sentence is what this row has now recorded four times.** Part one closes ONE axis — the CHARACTER CLASS of a cookie header NAME — and only the **64** RFC 9110 tchars now inside `COOKIE_NAME_PART`. The remaining **13** are **AR-27-10**, defined below from a measured partition (77 total = 64 covered + 13 not), enumerated in source as `CookieHeaderNameWidthTest.NOT_COVERED_TCHARS` and pinned to the shipped constant by a SOURCE READ so the set cannot go stale in silence. Part two takes the composer from TWO recognised logical-line starts to THREE. **The FOURTH start — a leading-whitespace or obs-folded header line — is STILL UNRECOGNISED and is measured surviving BYTE-UNCHANGED under STRICT AND BALANCED**; that is **AR-27-09**, defined below, and it is ONE MODE WIDER than `27-VERIFICATION-3.md` recorded, because plan 27-11 RE-MEASURED it instead of copying the round-3 prediction forward. Understating a residual is the same failure as overclaiming a fix, and the record was widened in the direction the measurement pointed. **A THIRD ARTIFACT DEFECT WAS CLOSED IN THE SAME ROUND, and it is a defect of the RECORD rather than of the control.** `27-VERIFICATION-3.md` also refuted plan 27-08's must-have quoted above: two `assertTrue(… .contains("api.example.com"))` assertions under `PrivacyMode.STRICT`, committed by THIS phase in `09e9cae`, against plan 27-05's own high-severity prohibition. Plan 27-12 **DELETED** both — deleted and not inverted, because the behaviour they described STILL SHIPS and stays open as **AR-27-04** — and re-pointed the pass-through they measured at an `assertEquals` BYTE-IDENTITY fixture under `PrivacyMode.OFF`, the one policy under which pass-through is correct and the one form that names no sensitive value in an `assertTrue`. The prose must-have is now a machine check, `RedactingPolicySurvivalSweepTest`, and standing-rule clause (vi) below carries it TOGETHER WITH its stated bound. **A NOTE ON THE LINE CITATIONS IN CLAUSES (3), (4) AND (5), WHICH HAVE ROTTED FURTHER — those clauses are preserved verbatim in spite of it, exactly as clause (5) preserved clause (3).** Clause (5) already recorded that clause (3)'s `Redaction.kt:158` and `:91` had moved to `:293` and `:125`. **Waves 10 and 11 moved them again.** MEASURED 2026-08-26 in this closing task: `COOKIE_NAME_PART` `Redaction.kt:132`, `COOKIE_NAME_TOKEN` `:138`, `JSON_ESCAPED_NEWLINE` `:266`, `JSON_STRING_OPEN` `:277`, `logicalLineHeaderRule` `:312`, `cookieHeaderRegex` `:319`, `setCookieHeaderRegex` `:324`, `isCookieHeaderName` `:391`, `hostHeaderRegex` `:1992`; the ADMITTING call site is `PassiveAiScannerFilters.kt:197` (clause (4) cites `:186`) and the REDACTING one is still `McpToolHelpers.kt:336`, which has not moved. Read every `.kt:NNN` in clauses (3), (4) and (5) as a historical artefact of the round that wrote it, and read the SYMBOL NAMES as the durable citation — which is why this clause names symbols first and numbers second. **`threats_open` in the frontmatter was RECOMPUTED from the rows of this register by the command quoted there, AFTER every other edit of plan 27-13, and its POPULATION is restated there — for the second time, and now with an explicit statement of whether either finding opened this round sits at or above the blocking severity.** **(7) REOPENED A FIFTH TIME AND RE-CLOSED — 2026-08-26, by this phase's FOURTH re-verification (`27-VERIFICATION-4.md`, 29 of 33 must-haves, four failed), closed by plans 27-14 and 27-15, recorded by plan 27-16.** **WHAT ROUND 4 SHIPPED, IN PLAIN ENGLISH.** Plan 27-11 added a THIRD recognised logical-line start and NAMED it a JSON string open, but the constant it shipped — `Redaction.JSON_STRING_OPEN` — was a BARE DOUBLE QUOTE, composed as the lookbehind `(?<=")`. A bare quote is not a JSON string open: it also opens HTML attribute values, JavaScript string literals and quoted CSV fields. All THREE composer-built rules inherited it — `cookieHeaderRegex`, `setCookieHeaderRegex` and `authHeaderRegex` — so EVERY double quote in every payload reaching `Redaction.apply` became a logical-line start, and the value tail then ran to the JSON string's real closing quote. **THE MEASUREMENT, QUOTED VERBATIM FROM `27-14-SUMMARY.md` PROBE B** — a 1714-character `proxy_http_history`-shaped payload driven against the freshly compiled classes under JDK 21, STRICT and BALANCED identical: IN length **1714**, OUT length **125**, characters destroyed **1589**, content markers IN **40**, content markers OUT **0**, byte-identical **false**, and **output still parses as JSON: yes**. The pre-fix output in full: `{"url":"/orders/aaaaaaaaaaaaaaaaaaaaaaaaaaa","response":"<html><body><div title=\"cookie: [STRIPPED]","notes":"analyst note"}` — the sibling `notes` field byte-identical and the key set unchanged, **which is exactly why every existing shape assertion in the suite passed while 93% of the payload was gone.** **THIS IS THE FIRST DEFECT IN THIS SERIES THAT FAILED SAFE FOR PRIVACY AND BROKE CORRECTNESS INSTEAD**, and that distinction is what makes it a REGRESSION rather than a trade. The four previous refutations recorded in clauses (2) through (6) were all UNDER-redaction, where the record overclaimed a control; this one OVER-redacted and destroyed the model's input. This codebase already holds itself to a standard in that direction: the OVER-REDACTION paragraph beside `Redaction.MAX_COOKIE_SECTION_LINES` states that an over-redaction blast radius on arbitrary MCP tool output is a cost that must be BOUNDED, and names the constant that bounds it to 16 lines (`Redaction.kt:543-548`, MEASURED in this closing task — read the SYMBOL as the durable citation and the number as an artefact of the round that wrote it, exactly as clause (6) instructs). A logical-line start that destroys 93% of a tool result is that same cost, unbounded, shipped by this file against its own written standard. **THE REPAIR — plan 27-14, ONE CONSTANT.** `JSON_STRING_OPEN` narrowed from `"\""` to `":\""` at `Redaction.kt:333`: a JSON string VALUE open, TWO regex-literal characters, so the composer's previously measured 2.4x fixed-width look-back argument is preserved BY CONSTRUCTION and no quantifier, alternation or value tail changed. All five measured non-JSON false positives went byte-identical to their input; PROBE B went from **1589** characters destroyed to **0** and from **0 of 40** content markers to **40 of 40**; and **round 4's own target was NOT un-fixed** — PROBE C's three cases each produce `Cookie: [STRIPPED]` in BOTH columns and BOTH redacting modes. **THE TWO GATES PLAN 27-14 ADDED**, because the reason this shipped at all is that the gate which should have caught it used a fixture whose cookie value was the LAST content of its string, so it could not observe a blast radius: (a) `SerializedEmissionRedactionTest.JsonStringOpenBoundary.anHtmlAttributePayloadIsLeftByteIdenticalUnderBothRedactingModes` — a whole-payload `assertEquals` BYTE-IDENTITY negative gate on a non-JSON HTML-attribute carrier, RED before the narrowing and carrying the 1589-character measurement in its own failure message; and (b) `…contentAfterTheCookieValueInTheSameJsonStringIsMeasuredNotAssumed` — a blast-radius gate carrying content AFTER the value inside the same JSON string, which is the property no gate in that nest could observe before. The narrowed VALUE is additionally READ OUT OF `Redaction.kt` at test time and pinned at width two by `LogicalLineBoundaryScopeTest.theJsonStringOpenIsAValueOpenAndNotABareQuote`, so a one-character revert goes RED instead of shipping green. **The cost of the narrowing is RECORDED rather than absorbed: it is `AR-27-11`, defined below from a measurement taken this round.** **THE SECOND HALF OF THIS RE-OPENING IS A DEFECT OF THIS REGISTER, NOT OF THE CONTROL.** `RedactingPolicySurvivalSweepTest.FUNCTION_DECLARATION` — the CI gate clause (vi) below cites as what now enforces "no green survival pin" — admitted one optional modifier and a word-character name, and `detect()` returns early on a non-matching declaration line, so an unmatched declaration hid its ENTIRE body. **RE-MEASURED by plan 27-15 against the tree with 27-14 landed, and BOTH populations are recorded rather than one chosen because it reads tidier: 133 of 1781 declaration lines invisible on the population that requires the opening parenthesis to follow the identifier, and 136 of 1784 on the paren-optional population `27-REVIEW-2` CR-01 counted** — the 3-line difference being extension-receiver declarations such as `private fun String.indentWidth()`, which is itself a finding and is carried below as axis 9. **67 of the invisible were backtick-named `@Test` methods across 9 files**, one of them in the redaction package itself, and a synthetic survival pin scored **1 of 6** declaration shapes. Clause (vi) cited that check's own eleven-axis KDoc enumeration as its STATED BOUND, so **this register itself carried a claim wider than its control** — verbatim the failure clause (vi) exists to prevent, inside the clause written to prevent it. **WHAT CLOSED THAT HALF — plan 27-15, three changes, each measured in BOTH directions.** (a) The DECLARATION GATE widened to any modifier prefix, an optional same-line annotation, an optional generic parameter list and BOTH name spellings, with the identifier taken from the plain-name group falling back to the backtick group: the six-shape pin went **1 of 6 → 6 of 6**, and the PRE-ROUND historical corpus still reports **EXACTLY 3** hits under the same three identifiers — so the widening bought scope without buying noise, and nothing was narrowed to keep the hit set empty (`ALLOWLIST` is still `emptyMap()`, `BENIGN_ACCESSORS` still holds exactly one key, no self-file exclusion exists). (b) The `fileWalk` → `detect` COMPOSITION gated in the FLAGGING direction for the first time — **1 / 2 / 0** across the shipped walk and its two neutralisations; in the blank-everything run that test was **the ONLY failing test in the class, with the other 13 green**, which is precisely the silently-vacuous pass it exists to stop. (c) The UNBALANCED-FILE blindness converted from a silently blanked tail into a NAMED `AssertionError`: all **151** files now walk without throwing, so **0** files end INSIDE a raw string, established by the tree scan itself rather than by a one-off probe. And the stated bound is now MACHINE-CHECKED — `STATED_BLIND_AXES = 13` is asserted against the class KDoc's own enumeration by a source read, so **the number cited in clause (vi) can now go stale only if a test goes RED first**. Clause (vi) was amended IN THE SAME CHANGE as the control it describes, which is the discipline whose absence produced this half of the re-opening. **WHAT THIS CLOSURE DOES NOT COVER, bounded in the same breath, as every clause since (5) has been.** Round 5 closed a correctness REGRESSION and a RECORD defect. It closed no carrier and no requirement: **PRIV-05 remains `[ ]`**; `AR-27-08` and `InjectionPointExtractor.kt:29` are untouched and still owned by Phase 28; `AR-27-04` is unchanged, still OPEN at MEDIUM and still owed a HUMAN decision; `AR-27-09` and `AR-27-10` are unchanged. **TWO residuals were CREATED by round 5, and they are named HERE rather than left for the next verifier to find — which is the whole of standing-rule clause (vii) below:** (1) `AR-27-11`, the JSON-ARRAY-ELEMENT logical-line start bought by the narrowing, defined below from a measurement taken by plan 27-16 rather than from a prediction; and (2) the sweep's **axis 9**, a declaration whose opening parenthesis does not follow the identifier on its line — **3** extension receivers measured live on this tree, one of them inside the sweep file itself, and **0** multi-line signatures, which is one shape wider and one shape narrower than plan 27-15's plan anticipated and is recorded at what was MEASURED. **`threats_open` in the frontmatter was RECOMPUTED from the rows of this register by the command quoted there, AFTER every other edit of plan 27-16, and its POPULATION is restated there in full — for the THIRD time — with the question that population forces asked and answered explicitly for `AR-27-11`.** | closed |
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
| AR-27-01 | T-26-02-01 | **RECLASSIFIED 2026-08-24 (plan 27-06) — this was never a genuine accepted residual, and recording it as one WAS the miss.** As accepted by plan 27-01 it read: `McpToolContext.redactIfNeeded` cannot recover a header `sanitizeHeaders` misses, because its output is single-line JSON while both cookie regexes are line-anchored. That acceptance was **conditional on a sanitizer running in front of it**, and the condition was never enumerated. On the 14 pinned serialized-emission sites nothing runs `sanitizeHeaders` at all, so between its acceptance and plan 27-04 this was a **live leak of the CANONICAL `Cookie:` and `Set-Cookie:` header names** — strictly broader than the five variant spellings this phase was created to close. The repository's own GREEN test pinned the leaking behaviour as expected: `McpToolHelpersTest$SanitizeHeaders.cookieVariantsAreStrippedEndToEndThroughRedactIfNeeded` asserted under STRICT that `redactIfNeeded` returns the cookie sentinel intact. Plan 27-04 **inverted** that assertion on the raw-message-in-JSON shape, where the inversion is true, and gated the header-map shape's ROOT CAUSE instead, so no green test in this repository now asserts that a cookie value survives a redacting policy. **CLOSED on the raw-message-in-JSON shape by plan 27-04. Explicitly NOT closed on the header-map shape** — that remainder is **AR-27-05**, defined below, and any sentence about `redactIfNeeded` being a second control must name the shape it holds on. | Reclassified by plan 27-06 on re-measurement — NOT a maintainer acceptance | 2026-08-24 |
| AR-27-02 | T-26-02-01 | **SUPERSEDED ON THE RAW-MESSAGE-IN-JSON SHAPE 2026-08-24, re-decided on evidence rather than inherited — and NOT superseded everywhere, which corrects plan 27-06's own premise.** `cookie` remains absent from `SENSITIVE_WORDS` (`Redaction.kt:663-664` — `access_token\|api_key\|apikey\|auth\|token\|secret\|password\|pwd\|session\|sid`), so `jsonSecretKeyRegex` is a backstop for the cookie class nowhere. It was load-bearing on the raw-message-in-JSON shape ONLY because the primary cookie rule could not fire there; after plan 27-04 it can, so on that shape AR-27-02 is superseded and is **not adopted**. **The bound this leaves, stated because it is the honest cost of not widening `SENSITIVE_WORDS`:** the two cookie rules are a SINGLE POINT OF CONTROL on the serialized emission path, with no independent backstop behind them. Adopting it would mean a broad widening of the shared key vocabulary re-measured against the WR-01 corpora, which already recorded 32 false positives (`status_code`, `errorCode`, `primary_key`, `public_key`, …) from a smaller widening — that measured cost is why it stays unadopted. **Measured directly in plan 27-06 against the compiled classes (JDK 21):** on the header-map shape `{"X-API-Key":"…"}` IS redacted by the JSON-key rule while `{"Cookie":"…"}` and `{"X-Cookie":"…"}` are NOT, so the auth class has a backstop on that shape and the cookie class has none. AR-27-02 therefore remains **load-bearing on the header-map shape** and is superseded only on the shape named above. | Re-decided by plan 27-06 on its own measurement | 2026-08-24 |
| AR-27-03 | T-26-02-01 | **UNCHANGED.** Byte-identically-named headers collapse to one entry in the tool-result header map (CP-27-02-01, maintainer-decided at plan 27-02's checkpoint): privacy-safe and asserted to leak no original value, but it costs analysis signal. Nothing in plans 27-04, 27-05 or 27-06 touches this, and nothing here should be read as re-deciding it. | Checkpoint CP-27-02-01 (plan 27-02), maintainer-decided | 2026-08-24 |
| AR-27-04 | T-26-02-01 | **NEW, OPEN, severity MEDIUM.** The `Host:` header value inside the raw HTTP message AND the sibling `SiteMapEntry.url` field both reach an AI backend **un-anonymised under STRICT** on the serialized emission shape. Measured, quoted and reproducible — see **"Open findings on the serialized emission path"** below for the probe output and the two measured reasons it was excluded from this phase's code change. **Disposition:** see that section. This is outside PRIV-05's wording, which is about cookie values; it is a gap between STRICT's stated promise of host anonymisation and STRICT's shipped behaviour on a 1.0.0 release. **APPENDED 2026-08-26 (plan 27-13) — THE FINDING IS UNCHANGED; ONLY ITS TEST ARTIFACTS MOVED.** On 2026-08-26 plan 27-12 **DELETED** the two green `assertTrue(… .contains("api.example.com"))` assertions that had pinned this residual as surviving under `PrivacyMode.STRICT` (`McpToolHelpersTest.kt:249` and `:285`, committed by this phase in `09e9cae` against plan 27-05's own high-severity prohibition, and refuted as a must-have by `27-VERIFICATION-3.md`). They were DELETED rather than inverted: inverting them to `assertFalse` would assert a behaviour that DOES NOT SHIP and would turn the suite red for a finding plan 27-12 was prohibited from fixing. The pass-through those assertions measured is now `McpToolHelpersTest.offLeavesBothSerializedShapesByteIdentical`, an `assertEquals` byte-identity assertion on the SAME two payload shapes under `PrivacyMode.OFF` — the one policy under which pass-through is correct — and each deleted position carries a replacement COMMENT naming `AR-27-04` and this file as where the measurement lives. **THE FINDING ITSELF IS UNCHANGED: still OPEN, still at MEDIUM, still measured, and it STILL OWES A HUMAN DECISION.** `hostHeaderRegex` is untouched and still excluded from `logicalLineHeaderRule`; `maybeAnonymizeUrl` is still not threaded into `toSiteMapEntry` / `toSerializableForm`; STRICT still promises host anonymisation it does not deliver on the serialized emission shape. **AND ITS DISPOSITION'S PROVENANCE STANDS EXACTLY AS WRITTEN AND IS NOT UPGRADED BY THIS ROUND'S ACTIVITY.** It was AUTO-SELECTED by `mode: yolo` and NOT maintainer-chosen; removing a green pin is a test-artifact repair and supplies no human judgment about the release posture. Read this appended note as narrowing what the SUITE claims, and as changing NOTHING about what the FINDING claims. The outstanding decision is carried, legible as open, as item 9 of the round-4 carry-forward section of `27-HUMAN-UAT.md`. | See disposition in the evidence section below | 2026-08-24 |
| AR-27-05 | T-26-02-01 | **NEW, OPEN, severity MEDIUM. Defined here for the first time** — if any earlier draft cited this identifier, nothing stood behind it; this is the definition. On the **header-map** shape (`ParsedRequest` / `ParsedResponse`, emitted by `request_parse` and `response_parse`) the headers are JSON OBJECT MEMBERS, not lines, so the payload carries **no line boundary of any kind** — neither a real newline nor a JSON-escaped one. Neither branch of `logicalLineHeaderRule` can fire, so `redactIfNeeded` **cannot recover a missed cookie header on that shape**, and `McpToolHelpers.sanitizeHeaders` is the **SOLE** control for the cookie-header class on those two tools. This is a no-backstop bound, **not a live leak**: all four construction sites pass `headers = sanitizeHeaders(…)` — `McpToolExecutorImpl.kt:369` and `:387`, `McpToolLegacy.kt:179` and `:201`, each re-read at source in plan 27-06 — so a cookie value does not in fact reach a backend on this path today. What is open is the absence of any second control if `sanitizeHeaders` is ever narrowed, bypassed at a new call site, or omitted from a future `ParsedRequest` producer. See the evidence section below. | Recorded by plan 27-06 on its own measurement; carried forward as an open finding | 2026-08-24 |
| AR-27-06 | T-26-02-01 | **NEW, OPEN, severity MEDIUM. Defined here for the first time** — if any earlier draft cited this identifier, nothing stood behind it; this is the definition. **The exact mirror of AR-27-05, one field over.** On BOTH MCP PARAMETER shapes — the serialized `ParsedParam` object emitted by `request_parse`, and the `params_extract` line — `McpToolContext.redactIfNeeded` **cannot recover a missed cookie**, so `McpToolHelpers.sanitizeParameters` is the **SOLE** control on this carrier. Three rules, three separate reasons, each read at source: the serialized shape carries **no line boundary of any kind** — neither a real newline nor a JSON-escaped one — so NEITHER branch of `logicalLineHeaderRule` can fire; `jsonSecretKeyRegex` keys on the JSON KEY, which on this shape is the literal field name `value` (the parameter's own name sits in a sibling `name` key where no rule looks), and `cookie` is absent from `SENSITIVE_WORDS` in any case (AR-27-02, still load-bearing here); and `Redaction.cookieTypedParamRegex` is keyed to the PROMPT path's rendered `name=value (TYPE)` suffix, which neither MCP shape produces. The `params_extract` line shape does carry a real newline, and it still matches no cookie rule for the same third reason. **This is a NO-BACKSTOP BOUND, not a live leak, and the record must not blur the two.** After plan 27-07 all four producers route through `sanitizeParameters` — `McpToolLegacy.kt:160` and `:189`, `McpToolExecutorImpl.kt:360` and `:381` — and a producer-ownership pin goes red if any one of them stops, so a cookie value does not in fact reach a backend on these two tools today. What is OPEN is the absence of any SECOND control if `sanitizeParameters` is narrowed, bypassed at a new call site, or omitted by a future producer. Severity **MEDIUM on the same reasoning AR-27-05 carries** — and that severity is AUTHORED by analogy, not measured; recorded at that weight. See the evidence section below. | Recorded by plan 27-09 from plans 27-07 and 27-08; carried forward as an open finding | 2026-08-25 |
| AR-27-07 | T-26-02-01 | **NEW, OPEN, severity LOW — MEASURED, with a firing attribution control.** A sensitive-NAMED parameter of a NON-COOKIE type survives `request_parse`'s serialized JSON in STRICT **and** BALANCED alike: a `URL`-typed parameter named `access_token` and a `BODY`-typed parameter named `password`, each carrying a distinct sentinel value, reached the end of `Redaction.apply` byte-for-byte unchanged in all three modes (`27-08-SUMMARY.md`, measurement 1, full probe output quoted there). **The attribution control fired on the same run:** the identical two names presented as bare JSON KEYS were both rewritten to `[REDACTED]` under STRICT and BALANCED and both survived under OFF — so this is a statement about the rule's REACH and not a misconfigured probe. **Mechanism:** `jsonSecretKeyRegex` keys on the JSON key, which here is the literal `value`; the sensitive-looking name sits in a sibling `name` key. **Severity LOW, with the decisive mitigating property named:** `request_parse` and `params_extract` parse a raw request string supplied BY THE CALLER, so the AI agent already possessed those bytes — this is caller-echoed content, not Burp-held traffic. Also outside PRIV-05's cookie wording entirely (D-27-20). **DISAGREEMENT RECORDED RATHER THAN RESOLVED SILENTLY:** plan 27-08's own threat table carried this as `T-27-08-07` at `medium`, assigned at authoring time BEFORE the measurement existed. This register uses the MEASURED `low`, on the caller-echo property, and states that it is choosing between two recorded numbers rather than inheriting one. **NOT FIXED, and the cost of the fix is why:** widening `SENSITIVE_WORDS` carries WR-01's MEASURED cost of **32 false positives** (`status_code`, `errorCode`, `primary_key`, `public_key`, …) across all three consumer regexes at once. The widening is not adopted and `SENSITIVE_WORDS` is unchanged (D-27-20). | Measured by plan 27-08, filed by plan 27-09 at the measured severity | 2026-08-25 |
| AR-27-08 | T-26-02-01 | **NEW, OPEN, severity MEDIUM — MEASURED, with a firing positive control on the SAME payload, and the one finding in this series that carries BURP-HELD DATA.** A COOKIE-typed injection point's value reaches the `scanner_issues` tool result through `AuditIssue.detail()` and **SURVIVES `Redaction.apply` in STRICT and in BALANCED alike**, emitted verbatim (`27-08-SUMMARY.md`, measurement 2, full probe output quoted there). **The positive control fired in the very same output:** a real `Cookie:` header carried in `requestResponses[0].request` of the SAME `IssueDetails` object became `Cookie: [STRIPPED]` in the same STRICT run in which the detail-line sentinel survived — one object, one call, one output, one field controlled and one not, so the two are directly comparable and the null result is attributable to REACH. **Mechanism, measured:** `IssueUtils.formatIssueDetailHtml` (`util/IssueUtils.kt:51-63`) joins `detailLines` with `<br>`, so the blob contains **no newline at all** and the logical-line cookie rules have nothing to bind to; the rendered shape is `Original Value: <value>`, not `name=<value> (COOKIE)`, so `cookieTypedParamRegex` cannot key on it; and the enclosing JSON key is `detail`, which is not in `SENSITIVE_WORDS`. **REACHABILITY, cited at source for every clause — this is the difference between a live leak and a latent one, and this phase has twice recorded a finding at the wrong severity for want of it.** The write is NOT privacy-mode gated (`scanner/ActiveAiScanner.kt:1239`, unconditional). A confirmation IS required first (`:1172-1176`, `:1183`). The mode required is Active AI scanning, which is **opt-in and defaults to `false`** (`config/AgentSettings.kt:127`, also `:391`, `:520`; wired at `App.kt:182`). A COOKIE-typed injection point CAN reach that line — the target loop filters on vuln CLASS only, never on `point.type` (`:232-246`, `:1684`; points created at `scanner/InjectionPointExtractor.kt:29`). It leaves the machine via `detail = detail()` at `mcp/schema/Serialization.kt:14`, which the `scanner_issues` MCP tool emits. **SEVERITY MEDIUM, neither rounded up nor down, with both properties in one breath.** AGGRAVATING, and strictly worse than AR-27-07: this carries **Burp-held proxied traffic** — a real session cookie the operator's browser sent — which the AI backend did not previously possess; and it defeats STRICT outright, there being no mode in which this field is protected. MITIGATING: it is **LATENT**, behind three independent preconditions — the opt-in active scanner switched on, a finding reaching `confirmed`, and a `scanner_issues` call being made. Not `high` because it is unreachable in the default posture; not `low` because when it IS reachable a real session cookie crosses the trust boundary in STRICT. **MEASURED AND DELIBERATELY NOT FIXED.** Plan 27-08 applied NO control to this route — `T-27-08-06`'s disposition stayed **TRANSFER**, not mitigate, because calling a measurement a mitigation is the overclaim vocabulary this record set exists to correct. **`scanner/InjectionPointExtractor.kt:29` is deferred WITH this route and must be closed by the SAME successor:** its cookie-type predicate is byte-unchanged, and converting it alone would produce a tidier file and an unchanged leak. The named successor is **Phase 28** in `ROADMAP.md`; a deferral without an owner is round four, pre-arranged. **AMENDED 2026-08-27 by plan 28-03 (phase 28) — THE ROUTE IS NOW CONTROLLED. Everything above this sentence is the 2026-08-25 record, PRESERVED BYTE-EXACT as the leading prefix of this cell (3399 bytes / 3383 Unicode characters, re-derived at write time and asserted programmatically, not by eye) and NOT rewritten, reworded or deleted: it is the evidence the control was needed, and a row that deletes its own reason leaves a later reader unable to tell a considered deferral from an oversight. The register's discipline is supersession, never deletion.** **CONTROL SYMBOL:** `ScannerIssueSupport.sanitizeInjectionPointValue` (plan 28-01), keyed on `InjectionType.COOKIE` AND `policy.stripCookies`, substituting `ScannerIssueSupport.INJECTION_VALUE_STRIPPED_MARKER` at the WRITE SITE where the `Original Value` line is built, so the value never enters the detail blob rather than being chased through it afterwards; every other injection type passes through truncated exactly as before. A COOKIE-typed injection point's `originalValue` no longer reaches `AuditIssue.detail()` under STRICT or BALANCED. **COMMITTED PROBE:** `scanner/IssueDetailCookieCarrierTest` (14 tests) REPLACES phase 27's deliberately-UNCOMMITTED 27-08 probe, and it is committable for the reason the old one was not: it asserts the value is ABSENT, so a green run is evidence of a working control rather than a green assertion that a secret survives STRICT. It carries an attribution control, a positive control, a non-vacuity guard and a field-scoped content-destruction guard, and it drives the real serializer types rather than a hand-typed JSON envelope. **RED PROBE, MEASURED RATHER THAN CLAIMED:** the designated assertion is `IssueDetailCookieCarrierTest.cookieOriginalValueIsStrippedUnderStrict`, named in the test class KDoc so a future mutator knows which line is supposed to catch it; it was measured red on three separate working-tree mutations with every verbatim failure message recorded in `28-01-SUMMARY.md`, and no mutation is committed. The control was verified to FAIL before it was verified to pass, so this closure is not the same-day pattern this phase failed on three times. **`scanner/InjectionPointExtractor.kt:29` IS RESOLVED (plan 28-02), by the SAME successor this row named:** its hand-written cookie-parameter predicate now calls the shared `Redaction.isCookieParameterType`. The swap is value-preserving in the safe direction — the shared predicate trims and upper-cases before comparing, so it accepts everything exact equality accepted and nothing extra Montoya's closed `HttpParameterType` enum can produce — and it is proven by `InjectionPointExtractorTest` (12 tests, zero edits) and `CookieRouteDispositionTest.exactlyOneCookieTypePredicateExistsInMainSource`, which DERIVES the predicate count from the tree rather than restating a pinned number. **The two consumers' DIFFERING dispositions are preserved deliberately (D-28-02):** the extractor still returns the RAW value and the control stays at each CONSUMER, because redacting in the producer would double-redact the already-controlled `AdaptivePayloadEngine` consumer with a foreign marker vocabulary; `CookieCarrierInventoryTest`'s `INJECTION_EXTRACTOR`/`PARAMETER_LIST` entry moved from `CLASSIFIED_NON_CARRYING` to `ROUTED_THROUGH` with its key byte-identical, and its reason now names both consumers and both controls. **SCOPE: ONE LINE, NOT THE BLOB.** Plan 28-01 controls the `Original Value` LINE of the issue-detail blob — written at `ActiveAiScanner.kt:1239` and rendered at `ScannerIssueSupport.kt:120` through `sanitizeInjectionPointValue` — and it does NOT control the blob. The `Evidence` line in the SAME blob, built at `ActiveAiScanner.kt:1242` and rendered directly beneath the sanitized line at `ScannerIssueSupport.kt:123` with NO redaction argument, is NOT controlled: it is a matched substring of a per-vuln-class signature, it carries no type, no name and no shape a type-keyed gate could key on, and it is carried forward as **`AR-28-01`** (severity MEDIUM, DERIVED by plan 28-03 task 1). Reading this row as "the issue-detail carrier is closed" is therefore WRONG — one line of that blob is closed and another line of the same blob is open — and that precise misreading is the defect behind four wrong PRIV-05 closures. **PRIV-05 STILL DOES NOT CLOSE (D-28-04):** `AR-27-04` (MEDIUM, maintainer-signed), `AR-27-07` (LOW), `AR-27-10` (LOW), `AR-27-11` (MEDIUM, and the one reachable in the DEFAULT posture) and the new `AR-28-01` all remain OPEN, and `CookieCarrierInventoryTest`'s own class KDoc disclaims completeness over four named blind axes — operator-pasted text, `bodyToString()` bodies on a path bypassing `Redaction.apply`, transitive carriers beyond the first hop, and future Montoya accessors. `REQUIREMENTS.md` is byte-unchanged. **This row is outside the `threats_open` population:** that counter scans Threat Register rows whose id begins `T-26-` and nothing else, so no `AR-` row moves it at ANY severity. **Phase 28's own `T-28-NN` plan threat models are deliberately NOT added to this file** — this is phase 26's register and phase 28 gets its own security artifact at verification time; their absence here is a scope decision, not an omission. **AMENDED 2026-08-27 by plan 28-06 (phase 28) — THE FIRST CLOSURE WAS PREMATURE; THIS ROW STAYS OPEN, NARROWED.** **WHICH MARKER THIS SUPERSEDES, STATED SO THE TWO CANNOT BE MERGED:** this amendment supersedes the marker DATED 2026-08-27 written by plan 28-03 — the one immediately above, opening "THE ROUTE IS NOW CONTROLLED", whose closure claim `28-VERIFICATION.md` measured FALSE — and it does NOT supersede the original 2026-08-25 measurement, which stands unchanged and is still the evidence the control was needed. Two dated markers now sit adjacent in this cell and they carry the SAME DATE with DIFFERENT PLAN IDS, so the plan id is the discriminator: 2026-08-27 plan 28-03 is the one withdrawn, 2026-08-27 plan 28-06 is this one, and the 2026-08-25 record is neither. Everything before this sentence is preserved BYTE-EXACT as the leading 8693-byte prefix of this cell, whose sha256 `8dc326ac23204becce687deeba867740eb2d4dde21346c58d7da9595d137ae2e` was recorded BEFORE this text was written and re-asserted after; the register's discipline is supersession, never deletion. **(a) WHY THE FIRST CLOSURE WAS PREMATURE — THE MECHANISM, NOT THE MISTAKE.** The control that closure recorded was applied to ONE of the nine detail lines `ScannerIssueSupport.buildActiveIssueDetailLines` emits, and the register was then amended to say the ROUTE was closed: a record nine lines wide resting on a control one line wide. Phase 27's own probe output at `27-08-SUMMARY.md:297` had ALREADY PRINTED `Original Value:` and `Payload Used:` side by side in the same quoted JSON blob, both carrying the same sentinel, so the second line was visible in the evidence this row was filed on. **(b) THE CONTROL INVENTORY AS IT NOW STANDS — FOUR DETAIL LINES ACROSS THE TWO PRODUCERS THAT EXIST, by symbol and by committed probe.** (1) `ScannerIssueSupport.sanitizeInjectionPointValue` (plan 28-01) gates the `Original Value:` line, keyed on `InjectionType.COOKIE` and `policy.stripCookies`, substituting `ScannerIssueSupport.INJECTION_VALUE_STRIPPED_MARKER` at the write site; probe `scanner/IssueDetailCookieCarrierTest`. (2) `ScannerIssueSupport.sanitizeRenderedPayload` (plan 28-04) gates the `Payload Used:` line of that same producer — the line that re-leaked what (1) had just stripped, because for a COOKIE point the payload is DERIVED FROM the cookie value — same enum key, same marker constant referenced rather than retyped; probe `scanner/IssueDetailCookieCarrierTest`. (3) and (4) `AiScanCheck.sanitizeCookiePointText` (plan 28-05) gates BOTH detail lines of the SECOND producer, `AiScanCheck.buildDetail`: the `**Original Value:**` line and the payload line beneath it. It keys on `AiScanCheck.isCookieInsertionPoint`, an identity compare against `AuditInsertionPointType.PARAM_COOKIE` — a member of a DIFFERENT closed Montoya enum from route 1's `InjectionType`, which is why the obvious shared predicate does not cover it; probe `scanner/AiScanCheckDetailCookieCarrierTest`, the repository's first Montoya `AuditInsertionPoint` fixture, with `scanner/CookieRouteDispositionTest.exactlyOneInsertionPointCookieTypePredicateExistsInMainSource` as the tripwire over the new predicate spelling. **`AiScanCheck` WAS NAMED IN NO PHASE-28 ARTIFACT BEFORE `28-VERIFICATION.md` FOUND IT**, and it read no privacy mode at all — it emitted the cookie value identically under STRICT, BALANCED and OFF. It is live-registered at `App.kt:215` and reaches the `scanner_issues` tool result through `McpToolExecutorImpl.kt:604`, so it is the same carrier this row names, not an adjacent one. **(c) THE ASYMMETRY, STATED RATHER THAN SMOOTHED.** Only THREE of those four lines are MEASURED CARRIERS. Route 2's payload line is controlled as **DEFENCE IN DEPTH ONLY**: `AiScanCheck` sources its payloads from the static `getQuickPayloads` table and does not interpolate the insertion point's `baseValue()`, so unlike route 1's payload line it is not a carrier at HEAD. Counting it as a closed leak would be this row's own failure repeated at smaller scale. **(d) WHAT THIS AMENDMENT DOES NOT COVER, BY IDENTIFIER.** Inside the SAME detail blob: `AR-28-01` — the `Evidence:` line at `ScannerIssueSupport.kt:123`, severity MEDIUM, DERIVED by plan 28-03 — remains OPEN, accepted as a shipping residual by MAINTAINER DECISION at plan 28-03's blocking checkpoint and deliberately NOT reopened by this round. Outside this blob, and unchanged by it: `AR-27-04` (MEDIUM, maintainer-signed), `AR-27-07` (LOW), `AR-27-10` (LOW) and `AR-27-11` (MEDIUM, the one reachable in the DEFAULT posture) all remain OPEN. **(e) THE ENFORCEMENT GAP — NO REPOSITORY-WIDE DETAIL-PRODUCER GATE EXISTS AFTER THIS ROUND.** `WR-01` measured the gate this row's predecessor implied, at `IssueDetailCookieCarrierTest.kt:625-632`, as STRUCTURALLY INCAPABLE of seeing another file: it filters only the list `buildActiveIssueDetailLines` itself returned. `D-28-06` records building a repository-wide one as CONSIDERED AND NOT TAKEN — a NAMED RESIDUAL, not an oversight. After this round there are TWO CONTROLLED PRODUCERS and NOTHING THAT WOULD CATCH A THIRD; a third detail producer added tomorrow is caught by no test in this repository, and `WR-01` is not closed by anything written here. **(f) THE DISPOSITION AND ITS AUTHORITY.** `AR-27-08` **STAYS OPEN**, narrowed to the residual enumerated in (d) and (e) rather than closed. This disposition was selected at plan 28-06's blocking `checkpoint:decision`, and **THE AUTHORITY FOR IT IS A HUMAN ANSWER**: the maintainer answered `stay-open` interactively on 2026-08-27, after `check auto-mode` reported inactive and `workflow.auto_advance` and `workflow._auto_chain_active` were both confirmed `false` on disk. It was NOT an auto-advance default. It is also the disposition `28-VERIFICATION.md` recommended, on the ground that a record of why the first closure was premature is worth more than the closure would have been. An OPEN row here does NOT mean nothing was fixed: the inventory in (b) is what was fixed. It means the row's own sentence is not yet false for every line of the blob it describes, and that nothing structurally prevents a third producer. **(g) PRIV-05'S STATUS.** Still `- [ ]`, and `.planning/REQUIREMENTS.md` is BYTE-UNCHANGED at sha256 `9b3219662ec0d007c1c82d64eed3ef2698bd306ce69f01205ac9bbc3f42fcfb4` (D-28-04; the gate is run TWICE under distinct headings in `28-06-SUMMARY.md`, with raw output recorded both times). The reason is the enumeration at `28-03-SUMMARY.md:515-539` **with its own premise now repaired**: that enumeration reasoned from "Closing `AR-27-08` closes ONE carrier" while `AR-27-08` was not in fact closed either, so it UNDERSTATED its own openness rather than overstating it. The premise is repaired; the count of open carriers is unchanged, and PRIV-05's "by any path" wording is still undischarged. **THE COUNTER WAS RECOMPUTED, NOT RESTATED.** The documented awk over the Threat Register was re-run at this amendment: raw output `0`, 46 rows scanned, 46 closed. `threats_open` is unchanged at 0 and the front-matter value was therefore not edited. This is an `AR-` row and sits OUTSIDE that counter's population at ANY severity — the counter scans rows whose id begins `T-26-` and nothing else — so an OPEN `AR-27-08` beneath a counter reading 0 is the documented behaviour, not a contradiction. **AMENDED 2026-08-28 by plan 28-08 (phase 28) — THIS AMENDMENT ADDS TO CLAUSE (d) AND WITHDRAWS NOTHING.** It EXTENDS the enumeration of clause (d) "WHAT THIS AMENDMENT DOES NOT COVER" of the 2026-08-27 (plan 28-06) amendment with two named residuals, and it corrects two claims this cell made ahead of the tree. Neither the 2026-08-25 record nor either prior amendment is withdrawn, softened or rewritten: both remain byte-exact leading prefixes of this cell — 8693 bytes to the head of the second marker (sha256 `8dc326ac23204becce687deeba867740eb2d4dde21346c58d7da9595d137ae2e`) and 16071 bytes to the column separator (sha256 `5316a97149017ae824d162b72b99d954cb0fa25b28b0c3a8214c01e42390ed72`), both digests taken BEFORE this edit and re-asserted after it in `28-08-SUMMARY.md`. WHY THIS IS APPENDED AT THE TAIL RATHER THAN SPLICED INTO CLAUSE (d) IN PLACE: the byte-prefix guarantee is what makes this cell auditable, and honouring it costs one cross-reference while breaking it would cost the guarantee. ROUND 3 ADDED NO CONTROL AND CHANGED NO RUNTIME BEHAVIOUR — it repaired the record and named a residual; a reader who takes this marker as evidence of a new control has misread it. **(28-08.1) THE WRITE-TIME/READ-TIME BOUND, NAMED — NEW RESIDUAL, ADDED TO CLAUSE (d).** Both producers decide ONCE at issue construction and bake the result into an immutable `AuditIssue.detail()` that Burp stores and the `scanner_issues` MCP tool replays; there is no read-time pass. An issue built while `privacyMode` was `OFF` still emits the raw cookie value on a later STRICT read. `AiScanCheck.consolidateIssues` returns `KEEP_EXISTING` on a matching canonical name and normalized URL, so a re-scan under STRICT does not repair the site map, and plan 28-05's own red probe recorded the sentinel surviving STRICT redaction verbatim whenever the write gate does not fire — `Redaction.apply` provably cannot rescue it downstream. **(28-08.2) THE DISPOSITION AND ITS AUTHORITY.** The bound is ACCEPTED AS A NAMED RESIDUAL, and **THE AUTHORITY FOR IT IS A HUMAN ANSWER**: the maintainer answered at the `verify_phase_goal` gate on 2026-08-28, recorded as `D-28-09` in `28-CONTEXT.md`. It was NOT an auto-advance default; `workflow.auto_advance` and `workflow._auto_chain_active` were both `false` on disk. THE REASON THE MAINTAINER GAVE, recorded so it is not re-litigated: every issue PRODUCED under STRICT or BALANCED — the entire default posture, `AgentSettings.kt:493` defaults to `BALANCED` — is measurably clean across all four detail lines of both producers, and the residual requires a deliberate `OFF` scan followed by a mode switch, the same latent opt-in reachability profile that set `AR-27-08` at MEDIUM rather than high. A read-time fix is new architecture on the emission path and belongs to its own phase. THE ACCEPTANCE WAS CONDITIONAL (`D-28-10`), and each condition is discharged by a named artifact: the `AiScanCheck.consolidateIssues` KDoc and `SettingsPanelInit`'s `PRIVACY_MODE_TOOLTIP` (both plan 28-07), `ISSUE_DETAIL_CARRIER_DISPOSITION`'s third supersession (this plan's task 1), and this clause. **(28-08.3) THE PROBE CLAIM FOR DETAIL LINE (4), CORRECTED.** This row named `AiScanCheckDetailCookieCarrierTest` as the committed probe for the rendered payload line while that file asserted nothing about it — a grep for that line's literal prefix returned **0** at that commit, KDoc included. That was prose written ahead of the tree, and it is named here as a correction rather than softened. Plan 28-07 supplied the assertions: `cookiePayloadLineIsStrippedUnderStrict`, `cookiePayloadLineIsStrippedUnderBalanced`, `cookiePayloadLineSurvivesUnderOff` and `urlParamPayloadLineSurvivesStrict_attributionControl`. THE ASYMMETRY IS UNCHANGED BY THE REPAIR: that line on route 2 is DEFENCE IN DEPTH, not a measured carrier, because `AiScanCheck` sources payloads from the static `getQuickPayloads` table and interpolates no insertion-point value. Making a claim TRUE is not closing a leak. **(28-08.4) THE ROUTE-2 FAIL-OPEN SET — NEW RESIDUAL, ADDED TO CLAUSE (d), PREVIOUSLY UNRECORDED (`D-28-11`).** `AiScanCheck.isCookieInsertionPoint` is an identity compare against `AuditInsertionPointType.PARAM_COOKIE`, and FOUR members of that 17-member enum are cookie-capable while not being `PARAM_COOKIE`: `HEADER`, `USER_PROVIDED`, `EXTENSION_PROVIDED` and `UNKNOWN`. For those four the gate is fail-OPEN today. Contrast route 1, whose `InjectionType` has exactly ONE cookie-capable member and it IS `COOKIE` — the property that made `D-28-01`'s pass-through safe BY CONSTRUCTION, and which this enum lacks. WIDENING WAS CONSIDERED AND NOT TAKEN, for plan 28-07's four recorded reasons: it would strip the original-value line on every header-typed, user-provided, extension-provided and unknown insertion point, a product behaviour change nobody asked for; it contradicts `D-28-01`'s deliberate pass-through discipline that route 2 copied on purpose; it would move `CookieRouteDispositionTest`'s two pinned predicate populations and would need its own red probe and its own reachability measurement; and plan 28-07 was chartered as RECORD REPAIR, so shipping a behaviour change inside it would leave this register describing code that no longer exists. THE RESIDUAL IS PINNED by `AiScanCheckDetailCookieCarrierTest.theRouteTwoGateIsFailOpenForTheseCookieCapableTypes`, whose GREEN run records the residual's width and is NOT evidence of correct behaviour, and BOUNDED by that file's `theInsertionPointTypeEnumPopulationIsTheOneTheResidualWasMeasuredAgainst`, so a Burp release that adds a member turns the pin RED instead of widening the residual in silence. THE CORRECTED KDoc PREMISE: the claim that a real Burp implementation may return null from `type()` was FALSE against the shipped jar — `javap` on `montoya-api-2026.2.jar` shows a DEFAULT method whose entire body is `getstatic AuditInsertionPointType.EXTENSION_PROVIDED; areturn`. **(28-08.5) WHAT THIS AMENDMENT STILL DOES NOT COVER, THE ENUMERATION READ WHOLE IN ONE PLACE.** Clause (d)'s existing identifiers stand unchanged: `AR-28-01` (MEDIUM, the `Evidence:` line of the same blob, maintainer-accepted at plan 28-03's blocking checkpoint and deliberately not reopened), `AR-27-04` (MEDIUM), `AR-27-07` (LOW), `AR-27-10` (LOW) and `AR-27-11` (MEDIUM, the one reachable in the DEFAULT posture). ADDED TO THAT ENUMERATION BY THIS AMENDMENT: the write-time/read-time bound of (28-08.1) and the route-2 fail-open set of (28-08.4). Clause (e) of the 2026-08-27 (plan 28-06) amendment is UNTOUCHED: no repository-wide detail-producer gate exists, `WR-01` is not closed by anything written here, and `D-28-06` records building one as CONSIDERED AND NOT TAKEN — two producers are controlled and a third would still be caught by nothing. `AR-27-08` **STAYS OPEN**. PRIV-05 stays `- [ ]` and `.planning/REQUIREMENTS.md` stays BYTE-UNCHANGED at sha256 `9b3219662ec0d007c1c82d64eed3ef2698bd306ce69f01205ac9bbc3f42fcfb4`, with the gate run TWICE under distinct headings in `28-08-SUMMARY.md`. **THE COUNTER WAS RECOMPUTED, NOT RESTATED.** The documented awk over the Threat Register was re-run at this amendment; its raw output and the number of rows scanned are recorded in `28-08-SUMMARY.md`. This is an `AR-` row and sits OUTSIDE that counter's population at ANY severity — the counter scans rows whose id begins `T-26-` and nothing else — so an OPEN `AR-27-08` beneath a counter reading 0 is documented behaviour, not a contradiction. Round 3 added, amended and reclassified no `T-26-` row, so `threats_open` is unchanged and the front-matter value was not edited. | Measured by plan 27-08 (disposition TRANSFER, not mitigate); filed by plan 27-09 with a named successor | 2026-08-25 |
| AR-27-09 | T-26-02-01 | **NEW, OPEN, severity LOW — MEASURED, and MEASURED ONE MODE WIDER THAN THE ROUND-3 RECORD PREDICTED.** The FOURTH logical-line start `logicalLineHeaderRule` still cannot recognise: a header line preceded by LEADING HORIZONTAL WHITESPACE, or an obs-folded continuation line. The composer now recognises three starts (a real line start, a JSON-escaped newline, and — since plan 27-11 — a JSON string open); an INDENTED header line is none of them, and the real-line branch is anchored `^` with nothing permitting whitespace before the name. **EVIDENCE, quoted verbatim from `27-11-SUMMARY.md` ("The Indented-Header Measurement"), driven through a throwaway `jshell` harness against the freshly compiled classes (`build/classes/kotlin/main` plus `kotlin-stdlib-2.2.21`, `Redaction.INSTANCE.apply(raw, RedactionPolicy.Companion.fromMode(mode), "round4-measurement-salt", false)`), with `\r` and `\n` rendered as two-character escapes so the raw bytes are legible:** `== indented-header (AR-27-09) / STRICT` / `BEFORE: GET / HTTP/1.1\r\n Cookie: a=SECRET5\r\n\r\n` / `AFTER : GET / HTTP/1.1\r\n Cookie: a=SECRET5\r\n\r\n` / `SURVIVES: true`, and `== indented-header (AR-27-09) / BALANCED` / `BEFORE: GET / HTTP/1.1\r\n Cookie: a=SECRET5\r\n\r\n` / `AFTER : GET / HTTP/1.1\r\n Cookie: a=SECRET5\r\n\r\n` / `SURVIVES: true`. **Byte-unchanged in BOTH redacting modes. `27-VERIFICATION-3.md` recorded this residual as surviving under STRICT only; plan 27-11 RE-MEASURED rather than copying that forward and found it one mode WIDER, and the source sentence beside the rule was widened to match.** Understating a residual is the same failure mode as overclaiming a fix, so the wider measured value is the one recorded here. **THE POSITIVE CONTROL FIRED ON THE SAME HARNESS RUN**, which is what makes this a statement about the rule's REACH: the un-indented form `GET / HTTP/1.1\r\nCookie: a=SECRET7\r\n\r\n` became `Cookie: [STRIPPED]`, and the shape plan 27-11 fixed (`{"notes":"Cookie: a=SECRET1\r\nX: y"}`) became `Cookie: [STRIPPED]` in the same run. **DELIBERATELY NOT PINNED BY ANY COMMITTED TEST**, and that is the point rather than an omission: a committed test asserting this value SURVIVES a redacting policy is precisely the artifact class standing-rule clause (vi) below now prohibits and `RedactingPolicySurvivalSweepTest` now detects. The record carries the measurement; the suite does not. **SEVERITY LOW, ASSIGNED FROM THE MEASUREMENT AND NOT FROM INTUITION, with both properties named in one breath.** AGGRAVATING: it defeats STRICT outright, it defeats BALANCED too, and it is the SAME canonical `Cookie:` name — no variant spelling and no unusual character is required. MITIGATING, and this is the decisive property: **NO MEASURED EMISSION SITE IN THIS REPOSITORY INDENTS A HEADER LINE.** The 14 pinned serialized-emission sites emit a machine-generated `HttpRequest`/`HttpResponse` `toString()` at column 0, and `PassiveAiScannerPrompts.buildScanMetadataText` `appendLine`s each admitted header at column 0 — the shape both prior measurements used. Not `medium`, because unlike AR-27-08 no measured producer puts a value on this shape; not lower than `low` and not closed, because **the reachability of an indented header line through analyst-authored free text — `HttpRequestResponse.notes`, the same field plan 27-11's fix was measured on — is UNMEASURED, and this repository constrains that field's content nowhere.** That unmeasured gap is exactly why this row carries a HUMAN disposition rather than a closure, and it is the honest bound on the severity above. **THE ONE-TOKEN FIX IS WRITTEN DOWN HERE so a successor does not re-derive it:** allow leading horizontal whitespace on the real-line branch — `^[ \t]*` in place of `^`. It widens only in the OVER-redacting direction. It is also written into `Redaction.kt` beside the residual and asserted present from source by `LogicalLineBoundaryScopeTest.theStatedBoundIsPresentWhereAReaderMeetsIt`, which carries `SECOND_OPEN_FINDING = "AR-27-09"` as a named constant alongside `AR-27-04`. **OUT OF SCOPE BY THE MAINTAINER'S STATED SCOPE FOR ROUND 4, NOT BY OVERSIGHT.** The maintainer scoped round 4 to the underscore name class and the JSON-string-open start; this start was measured, named and deferred inside that scope rather than discovered after it. **OWNER: the maintainer, via item 10 of the round-4 carry-forward section of `27-HUMAN-UAT.md`** — a DISPOSITION (accept at `low`, or pull the one-token fix forward), not deferred implementation work, which is why the owner is a person and not a phase. Contrast AR-27-08, whose owner is Phase 28 because a fix there needs its own red probe and reachability analysis.  **── AMENDMENT 2026-08-26 (plan 27-17) — CLOSED BY FIX. Everything above this marker is the record as it stood while the finding was open and is preserved byte-for-byte; nothing in it is withdrawn, and the LOW it argues for is what was NOT accepted. ──** **DISPOSITION: CLOSED BY FIX, not accepted at LOW.** Decided by the MAINTAINER at UAT and recorded in `27-HUMAN-UAT.md` item 10 (commit `ae3371a`), which is the provenance for this amendment. **WHY FIX RATHER THAN ACCEPT, in the words of the decision:** the LOW above rests on a REACHABILITY argument that the row itself marks UNMEASURED, and a severity derived from an unmeasured reachability claim is the exact defect class that reopened this phase five times. A measured two-mode leak of a canonical `Cookie:` header was not going to be closed on an unmeasured mitigation. **THE FIX.** `Redaction.logicalLineHeaderRule`'s REAL-LINE branch now starts at the new constant `REAL_LINE_START = "^[ \\t]*+"` in place of the bare `^` (`src/main/kotlin/com/six2dez/burp/aiagent/redact/Redaction.kt`). Only that branch's START moved: `JSON_STRING_OPEN`, `JSON_ESCAPED_NEWLINE` and the escaped branch's two lookbehinds are byte-unchanged. **MEASURED BEFORE (pre-fix classes, re-measured at 27-17 and reproducing the round-4 record exactly) and AFTER (post-fix classes), through `Redaction.apply` in STRICT and in BALANCED, with the un-indented control stripping in the same run:** `GET / HTTP/1.1\r\n Cookie: a=SECRET5\r\n\r\n` BEFORE byte-unchanged in both modes -> AFTER `GET / HTTP/1.1\r\n Cookie: [STRIPPED]\r\n\r\n`; `HTTP/1.1 200 OK\r\n Set-Cookie: s=SECRET6; Path=/\r\n\r\n` BEFORE byte-unchanged in both modes -> AFTER ` Set-Cookie: [STRIPPED]`; `GET / HTTP/1.1\r\n  X-Api-Key: SECRET8\r\n\r\n` BEFORE byte-unchanged in both modes -> AFTER `  X-Api-Key: [REDACTED]`; and the obs-folded continuation `GET / HTTP/1.1\r\nX-Foo: bar\r\n Cookie: a=SECRET9\r\n\r\n` BEFORE byte-unchanged in both modes -> AFTER ` Cookie: [STRIPPED]`. All three composed rules therefore gained the start together. **ONE CORRECTION TO THE ROUND-4 RECORD, measured rather than inherited:** the auth family's pre-fix state was PARTLY MASKED. An indented `Authorization: Bearer …` line was already losing its token before the fix — to `bearerRegex`, a VALUE-level rule, which produced `Authorization: Bearer [REDACTED]` while the HEADER rule missed the line entirely. A gate built on `Authorization` would have been green before the fix and proved nothing, which is why the auth evidence above is a PLAIN-TOKEN `X-Api-Key`. Post-fix that same line reads `Authorization: [REDACTED]`. **THE HAZARD THAT WAS MEASURED BEFORE THE ONE-TOKEN FIX WAS APPLIED, because it was not one token.** The written-down fix `^[ \\t]*` is CONSUMING where `^` is ZERO-WIDTH, and `Redaction.apply`'s three replacement lambdas rebuild the header with `m.value.substringBefore(":")`. Measured: the match value becomes `" Cookie: a=SECRET5"`, `substringBefore(":")` yields `" Cookie"`, and the lambda emits `"$header: [STRIPPED]"` — so the indent is RE-EMITTED VERBATIM and round-trips byte-exact for spaces and tabs alike. No name-matching predicate sees the widened value: `isCookieHeaderName` is called on header names Burp supplies, never on a match value. **AND ONE PREMISE OF THE FIX BRIEF WAS FALSIFIED BY MEASUREMENT.** The brief stated that Java forbids a variable-width lookbehind, so the zero-width spelling `(?<=^[ \\t]*)` was unavailable. MEASURED on this toolchain (JVM 21): it COMPILES and it MATCHES the indented shapes correctly, with the match still beginning at the header name. It was rejected on COST instead, which is the measurable reason: over 2000 scans of a 60-line pretty-printed document, `^` 158 ms, `^[ \\t]*+` 155 ms, `^[ \\t]*` 164 ms, `(?<=^[ \\t]*)` 34237 ms — roughly 221x, because an unbounded look-back is retried at every position, on header rules that run with NO per-pattern deadline. **THE SHIPPED SPELLING IS POSSESSIVE, `^[ \\t]*+`,** and possessive is not a narrowing: no composed name pattern can begin with a space or a tab, so giving whitespace back could never enable a match the possessive form misses. Measured cheaper on a 4000-space line (200 scans: 29 ms vs 63 ms). **DIRECTION, PROVEN NOT ASSERTED:** `[ \\t]*+` matches the empty string and no composed name pattern can begin with horizontal whitespace, so wherever `^` matched the two spellings are position-identical — the new start is a STRICT SUPERSET and moves only in the OVER-redacting direction. **GATES:** `IndentedLogicalLineStartTest` (9 tests) carries the positives in both modes for all three rules, the obs-fold, the un-indented non-vacuity controls, a byte-identity NEGATIVE corpus over indented prose / pretty-printed JSON / indented source, and both halves of the direction proof; `LogicalLineBoundaryScopeTest.theRealLineStartRecognisesLeadingHorizontalWhitespace` reads the constant's VALUE out of `Redaction.kt` at test time so a nine-character revert cannot ship green. **MUTATION-PROVEN in both directions:** reverting `REAL_LINE_START` to `^` turns 6 of the new gates RED while the two controls stay GREEN; over-widening it to `^[^:]*` turns the byte-identity NEGATIVE gate RED. **THIS DOES NOT CLOSE PRIV-05,** and `AR-27-10` and `AR-27-11` are untouched and still OPEN. **This row is outside the `threats_open` population** — that counter scans rows beginning `| T-26-` only — so closing it cannot and did not move the counter, which was recomputed with the documented `awk` and is unchanged at 0. | Measured by plan 27-11, filed by plan 27-13 at the measured severity and the measured mode width | 2026-08-26 |
| AR-27-10 | T-26-02-01 | **NEW, OPEN, severity LOW — the residual difference set between the bare-contains PREDICATE and the widened NAME CLASS, and the record the underscore class never had.** **MECHANISM, in one sentence:** a header name carrying one of the RFC 9110 tchars outside `COOKIE_NAME_PART` is still ADMITTED onto the outbound prompt by `Redaction.isCookieHeaderName` (a bare `contains("cookie")`, which imposes no character class at all) and is still NOT MATCHED by either cookie regex — **the same fail-open shape the underscore class had, on a different character set.** **EVIDENCE, quoted verbatim from `27-10-SUMMARY.md` §6, extracted from `Redaction.kt` BY A SOURCE READ at test time rather than re-typed:** `shipped class text      : A-Za-z0-9_-` / `expands to (COVERED_TCHARS), 64 chars : A-Z  a-z  0-9  _  -` / `ALL_RFC9110_TCHARS      : 77  (15 punctuation + 10 digits + 52 letters)` / `NOT_COVERED_TCHARS      : 13`. **THE THIRTEEN UNCOVERED TCHARS, LISTED VERBATIM:** `!` `#` `$` `%` `&` `'` `*` `+` `.` `^` `` ` `` `\|` `~` — as a single string, ``!#$%&'*+.^\|~``. **ENUMERATED IN SOURCE as `CookieHeaderNameWidthTest.NOT_COVERED_TCHARS`**, DERIVED as `ALL_RFC9110_TCHARS - COVERED_TCHARS` rather than hand-listed, with `COVERED_TCHARS` PINNED TO THE SHIPPED `COOKIE_NAME_PART` by a source read (`theCoveredSetIsReadFromRedactionSourceNotRetyped`). The consequence a reader should hold onto: **if `COOKIE_NAME_PART` ever moves, that test goes RED and this row is stale BY CONSTRUCTION rather than quietly wrong** — which is the whole reason the set is filed here as a measurement instead of as a sentence. The partition is machine-checked in both directions (`theThreeCharacterSetsPartitionEachOther`, `theScanIsNonVacuous`), and the expander is proven non-vacuous by asserting that the PRE-FIX class `A-Za-z0-9-` expands to 63 characters and is NOT equal to `COVERED_TCHARS` — an assertion that fails on the unfixed tree, which is what makes it a probe rather than a restatement. **SEVERITY LOW, AND THE PROVENANCE OF THAT NUMBER IS STATED BECAUSE IT CHANGES ITS WEIGHT — this is the one number in this row that is NOT a direct measurement.** What IS measured: the partition (77 = 64 + 13, read from the shipped constant), and the fail-open MECHANISM itself, measured end-to-end on `_` before plan 27-10 closed it. What is INFERRED: that the mechanism carries over to the other 13 characters. **NO LEAK WAS MEASURED FOR ANY OF THE THIRTEEN**, and this row does not claim one. `low` rather than `medium` because, unlike `_` in `my_cookie` or `session_cookie`, none of these thirteen appears in a header name this repository, its tests, or ordinary HTTP practice has been observed to carry — but that observation is a judgment about convention and NOT a measurement, and it is recorded at that weight rather than dressed up as one. **NOT FIXED, and the cost of the fix is why it is a judgment rather than an omission:** widening `COOKIE_NAME_PART` to the full tchar set would make the two regexes match names like `X.Cookie` and `` `cookie` ``, which over-redacts in a direction nobody has measured against the benign-header corpus — and this phase has recorded, in WR-01, a 32-false-positive cost from a widening that looked equally harmless. The alternative — NARROWING the predicate to the same class — is prohibited: it would shrink what `McpToolHelpers.sanitizeHeaders` strips on the MCP path, which is the direction that reopened this phase. **OWNER: the maintainer, via item 11 of the round-4 carry-forward section of `27-HUMAN-UAT.md`** (accept at `low`, or widen the name class to the full RFC 9110 tchar set). **THIS ROW EXISTS BECAUSE THE UNDERSCORE CLASS DID NOT HAVE ONE.** For three rounds the identical residual lived in a source comment and in a green test and appeared in NO security record under `.planning/`; that is how it survived to become the fourth refutation. A difference set enumerated in source and NOWHERE ELSE is not a recorded residual. | Measured (partition and mechanism) by plan 27-10, filed by plan 27-13 with the inferred half of its severity labelled as inferred | 2026-08-26 |
| AR-27-11 | T-26-02-01 | **NEW, OPEN, severity LOW — the residual bought by round 5's narrowing, MEASURED in both directions and with its REACHABILITY measured this round rather than assumed.** **MECHANISM, IN ONE SENTENCE:** after plan 27-14 narrowed `Redaction.JSON_STRING_OPEN` from a bare double quote to the two-character colon-quote sequence, a header at the open of a JSON ARRAY ELEMENT string is NOT a recognised logical-line start, because an array element's open is a BRACKET-quote or COMMA-quote sequence and the boundary now recognises a COLON-quote sequence. **EVIDENCE, quoted verbatim from `27-14-SUMMARY.md` PROBE D, both columns, STRICT and BALANCED identical in both** (driven against the freshly compiled classes at `build/classes/kotlin/main` under JDK 21 via `Redaction.INSTANCE.apply(blob, RedactionPolicy.Companion.fromMode(mode), "round5-probe-salt", false)`, the BEFORE column taken against the pre-edit constant): input `{"tags":["Cookie: a=SECRET8"]}`; **BEFORE (bare quote): `{"tags":["Cookie: [STRIPPED]"]}`; AFTER (`:\"`): `{"tags":["Cookie: a=SECRET8"]}` — BYTE-IDENTICAL TO THE INPUT.** It matched before the narrowing and is byte-unchanged after, so this row records what was MEASURED rather than what was reasoned. **SEVERITY LOW, ASSIGNED FROM A REACHABILITY MEASUREMENT TAKEN BY PLAN 27-16 AND NOT FROM INTUITION — and the reachability is stated as MEASURED, which is a stronger claim than AR-27-09's row can make for its own case.** The MCP emission schema was enumerated at source: `mcp/schema/Serialization.kt` declares **ZERO** fields of type `List<String>` — its only two list-typed fields, `IssueDetails.requestResponses: List<HttpRequestResponse>` and `IssueDetails.collaboratorInteractions: List<Interaction>`, are arrays of OBJECTS whose string members therefore open at `:\"`, a RECOGNISED start — and multi-item tool results are joined by `McpToolContext.limitedJoin` with `\n\n`, which emits no JSON array wrapper at all. The five `List<String>` fields under `mcp/tools/McpToolModels.kt` (`ComparerSend.items`, `CollaboratorGenerate.options`, `StartAuditMode.requests`, `StartAuditWithRequests.requests`, `StartCrawl.seedUrls`) are INPUT models, reached only through `decode<…>` and `asInputSchema()` and never through `encodeToString`, so no emitted byte passes through them. **ONE CARRIER CAN CARRY AN ARBITRARY JSON ARRAY OF STRINGS THROUGH `Redaction.apply`, and it is named rather than omitted:** `McpToolExecutorImpl.routeExternalToolCall` redacts `argsJson` before forwarding it to a THIRD-PARTY external MCP server (`context.redactIfNeeded(argsJson.orEmpty())`, the D-03 outbound-privacy site). Those args are MODEL-authored and may be RESPONSE-DERIVED — a model that has read proxy history can put it there. **WHAT BOUNDS THE SEVERITY IS ALSO MEASURED, in the same probe run as the finding, with two positive controls firing:** an array element carrying a realistic raw HTTP message is STILL REDACTED, because its header sits after an escaped newline, which IS a recognised start — `{"requests":["GET / HTTP/1.1\r\nCookie: a=SECRET8\r\n\r\n"]}` became `Cookie: [STRIPPED]` under STRICT and BALANCED. Only a header that is the FIRST content of an array-element string escapes, in both the bracket-quote spelling and the comma-quote spelling (`{"tags":["x","Cookie: a=SECRET8"]}` byte-unchanged in both modes). **AGGRAVATING AND MITIGATING IN ONE BREATH:** it defeats STRICT *and* BALANCED, on the SAME canonical `Cookie:` name, with no variant spelling and no unusual character required — but no serialized field on the emission path this repository OWNS is a JSON array of strings, the one carrier that can be is model-authored rather than repository-emitted, and the realistic shape on that carrier is still caught. **WHAT IS MEASURED AND WHAT IS INFERRED, separated rather than averaged.** MEASURED: the residual itself (PROBE D, both columns); the escaped-newline bound and the comma-quote spelling (this round's probe, both modes, two controls firing); and the emission-schema enumeration above, read from source. INFERRED, and labelled as inferred: that the external-tool args path is the ONLY carrier able to emit an array of strings — a source read can see this repository's own schema, and the REMOTE tool schemas that path forwards to are not owned here and are UNMEASURED. **NOT `medium`**, because unlike `AR-27-08` no measured producer in this repository puts a value on this shape; **not closed**, because that one carrier exists and its remote half is unmeasured. **THE COST OF THE ALTERNATIVE IS WHY THIS IS A JUDGMENT RATHER THAN AN OMISSION:** the only spelling that recognises an array-element open is the bare quote, and that is MEASURED destroying 1589 of 1714 characters of a realistic tool result — trading a LOW-severity residual for a HIGH-severity correctness break on the primary path. A narrower widening (adding `[\"` and `,\"` as further fixed-width lookbehind alternatives) is written down here so a successor does not re-derive it, and is DELIBERATELY not applied this round: it has not been measured against the benign-payload corpus, and this phase has recorded in WR-01 a 32-false-positive cost from a widening that looked equally harmless. **DELIBERATELY NOT PINNED BY ANY COMMITTED TEST**, on the `AR-27-09` precedent and standing-rule clause (vi): a green assertion that this cookie value survives a redacting policy is precisely the artifact class clause (vi) prohibits and `RedactingPolicySurvivalSweepTest` now detects across BOTH declaration-name spellings. The record carries the measurement; the suite does not. What IS pinned from source is the CITATION — `LogicalLineBoundaryScopeTest.THIRD_OPEN_FINDING = "AR-27-11"` asserts the id is present in `Redaction.kt`'s rationale region, so the residual cannot be refactored out of the source record in silence. **OWNER: the maintainer, via item 12 of the round-5 section of `27-HUMAN-UAT.md`** — a DISPOSITION (accept the array-element start as a residual, or widen the boundary to recognise it), not deferred implementation work, which is why the owner is a person and not a phase. **THIS RESIDUAL WAS CREATED BY ROUND 5 AND IS FILED BY ROUND 5**, which is the distinction standing-rule clause (vii) below exists to make: round 4 created two residuals and filed neither. **── CORRECTION 2026-08-26 (out-of-plan, maintainer-authorised after 27-REVIEW-3 CR-01). EVERYTHING ABOVE THIS MARKER IS LEFT BYTE-UNCHANGED, INCLUDING THE WORD `LOW` AND THE ARRAY-ONLY MECHANISM SENTENCE, because this register APPENDS and never rewrites — read the correction as SUPERSEDING, not the earlier text as deleted. ──** **WHAT WAS WRONG: THE STATED BOUND, NOT THE NARROWING.** The row above states the cost of `JSON_STRING_OPEN = ":\""` as ONE family (a JSON ARRAY ELEMENT open) and derives LOW from a reachability enumeration that asks exactly one question — *"which serialized fields on the MCP emission path are JSON ARRAYS OF STRINGS?"*. The narrowing is CORRECT and is NOT re-widened; what was too narrow is this row's account of what it costs. **THE MECHANISM, STATED AS THE GENERAL RULE RATHER THAN AS A LIST OF ACCIDENTS:** `:"` is the two LITERAL characters colon then quote, so ANY shape that interposes a character BETWEEN the colon and the quote — a space, or the backslash of an escaped quote — and ANY shape carrying no colon before the quote at all, is not a recognised logical-line start. **FOUR families follow, all four MEASURED end-to-end through `Redaction.apply(..., STRICT, salt)` and `(..., BALANCED, salt)` against the compiled classes at `build/classes/kotlin/main` under JDK 21 on 2026-08-26, byte-identical in and out, with compact-shaped controls STRIPPING in the same run, and all three composed rules (`cookieHeaderRegex`, `setCookieHeaderRegex`, `authHeaderRegex`) losing the same four.** **EVIDENCE — the reviewer's differ table, quoted from its source `27-REVIEW-3.md` CR-01:** `BARE MATCH / COLONQ —` nested escaped json string value open (response body is JSON); `BARE MATCH / COLONQ —` pretty json, space between colon and quote; `BARE MATCH / COLONQ —` array element **← the ONE family that is named**; `BARE MATCH / COLONQ —` bare top-level json string; `BARE MATCH / COLONQ MATCH` CONTROL compact object value open; `BARE MATCH / COLONQ —` HTML attribute (must NOT match after narrowing). **RE-MEASURED INDEPENDENTLY IN THIS TASK rather than transcribed, and the four families reproduced exactly:** `{"response":"…{\"cookie_header\":\"Cookie: sess=SECRETNESTED\"}"}`, `{"notes": "Cookie: sess=SECRETPRETTY"}`, `"Cookie: sess=SECRETBARE"` and `{"tags":["Cookie: sess=SECRETARRAY"]}` are ALL byte-identical in and out under STRICT and BALANCED, while `{"cookie_header":"Cookie: sess=SECRETCOMPACT"}` → `Cookie: [STRIPPED]`, `{"notes":"X-API-Key: SECRETAUTH1"}` → `X-API-Key: [REDACTED]` and `{"notes":"Set-Cookie: s=SECRETSC2; Path=/"}` → `Set-Cookie: [STRIPPED]` in the same run; the PRETTY-PRINTED twins of the last two (`{"notes": "X-API-Key: …"}`, `{"notes": "Set-Cookie: …"}`) SURVIVE, which is what makes "all three rules lose the same four" a measurement rather than an inference. **THE REACHABILITY RE-DERIVED FROM THE CORRECTED QUESTION** — *"which `Redaction.apply` inputs can carry a header at the open of a JSON string in ANY of the four spellings?"* — **and MEASURED on the exact emission shape rather than reasoned.** `mcp/schema/Serialization.kt` declares `HttpRequestResponse(request: String?, response: String?, notes: String?)`: the whole raw HTTP RESPONSE, body included, is emitted as a JSON STRING, so a captured body that is ITSELF JSON arrives on the wire with its inner quotes escaped — `\"k\":\"…` — which is family 1; and a body that pretty-prints, or that returns header lines as an array of strings, is family 2 or family 4 INSIDE that same string. Encoding a real raw response into that exact shape and driving it through `Redaction.apply`: `{"request":"GET /api HTTP/1.1\r\nHost: t.example\r\n\r\n","response":"HTTP/1.1 200 OK\r\nContent-Type: application/json\r\n\r\n{\"cookie_header\":\"Cookie: sess=SECRETNESTED\"}","notes":null}` is **IDENTICAL in and out under STRICT and BALANCED**; so is its pretty-printed twin (`…{\r\n  \"cookie_header\": \"Cookie: sess=SECRETPRETTY\"\r\n}`); so is its array twin (`…{\"headers\":[\"Host: t.example\",\"Cookie: sess=SECRETARRAY\"]}`); and the CONTROL — the same carrier with `Set-Cookie: sess=SECRETCTRL; Path=/` on the response's own header block — became `Set-Cookie: [STRIPPED]` in the same run. **That is a materially different reachability from "JSON arrays of strings only".** The enumeration above concluded ZERO carriers on this repository's own emission schema because it looked for `List<String>` FIELDS — and the carrier is not a field. It is the CONTENT of the `response` string, which this repository copies verbatim from the target. Families 1, 2 and 4 additionally reach `redact_preview` (`McpToolExecutorImpl.kt:1018`, arbitrary caller-supplied text), `ContextCollector.kt:52-53` (the prompt path, again a target's raw response) and the passive-scan prompt blob — none of which is compact-JSON-shaped by construction. **SEVERITY CORRECTED: LOW → MEDIUM, stated plainly rather than kept at LOW for continuity.** The LOW rested on one load-bearing sentence — *"unlike AR-27-08 no measured producer in this repository puts a value on this shape"* — and for families 1, 2 and 4 that sentence does not apply: **the producer is not in this repository at all. It is the TARGET's response body**, which the default-posture `proxy_http_history` / `response_parse` path copies verbatim onto the emission path with NO opt-in precondition. AGGRAVATING AND MITIGATING IN ONE BREATH: it defeats STRICT *and* BALANCED, on the canonical `Cookie:` name, carrying BURP-HELD proxied traffic the AI backend did not previously possess — the same aggravating class as `AR-27-08` but with ONE FEWER precondition, since `AR-27-08` needs an opt-in scanner, a confirmed finding AND a `scanner_issues` call while this needs none of the three; and `redact_preview`, the one tool whose whole purpose is to answer *"would this leak?"*, now answers "no" for three of the four families. **NOT `high`, and the bound on that is stated rather than implied:** no LIVE producer was measured — a target response body must actually carry a header-line-shaped string at a string open, and how common that is on real traffic is UNMEASURED. **What would move it to `high` is named so a successor need not invent it: a measured instance of such a body on real proxied traffic.** **THE MITIGATING BOUND, RECORDED EXPLICITLY AND CHECKED RATHER THAN REPEATED.** The claim carried forward is that in every family only a header that is the FIRST CONTENT of its string escapes. Re-measured in ALL FOUR families, both modes, in this task: `{"notes": "GET / HTTP/1.1\r\nCookie: a=SECRETMIT2\r\n\r\n"}`, the doubly-escaped nested form, `{"requests":["GET / HTTP/1.1\r\nCookie: a=SECRETMIT3\r\n\r\n"]}` and the bare top-level form ALL became `Cookie: [STRIPPED]`. **THE CLAIM HOLDS. One qualification was found WHILE checking it and is recorded rather than smoothed:** in the DOUBLY-escaped spelling the strip also consumes the tail to the end of the enclosing OUTER JSON string — `{"a":"x\\r\\nCookie: a=SECRETD3\\r\\nY: z","tail":"KEEPTAIL"}` → `{"a":"x\\r\\nCookie: [STRIPPED]","tail":"KEEPTAIL"}`, losing `\\r\\nY: z` while the sibling field survives. That is OVER-redaction (fail-safe on the secret, blast radius bounded to the enclosing string), it is a property of START 2's value tail rather than of this narrowing, it PRE-DATES round 5, and it is recorded HERE rather than given its own id because the scope of this correction is `AR-27-11`'s STATED BOUND — **it is flagged for the maintainer's disposition and is NOT claimed as measured-common.** **ALSO MEASURED, and named because it bears on how far `:"` can be trusted as a JSON test:** `:"` is NOT JSON-exclusive — it is the object-literal separator in JS/JSON5 and appears in CSS — and `var o = {note:"cookie: analytics", keep:"KEEPTAIL"};` → `note:"cookie: [STRIPPED]"`, `a::before{content:"cookie: analytics"} KEEPTAIL` → `content:"cookie: [STRIPPED]"` under both redacting modes. That is `27-REVIEW-3.md` WR-01's finding, in the OVER-match direction; it is NOT addressed by this correction and is stated here only so this row's mechanism sentence cannot be read as "`:"` means JSON". **THE COUNTER IS UNMOVED, AND THAT IS A PROPERTY OF THE POPULATION RATHER THAN OF THIS SEVERITY:** `AR-27-11` is an `AR-` row and sits outside the `| T-26-` population at ANY severity, so `threats_open` stays `0`; `medium` is in any case below the `high` blocking gate, so no register ROW inside the population was created and the `awk` command is UNAMENDED. **OWNER: unchanged — the maintainer, item 12 of the round-5 section of `27-HUMAN-UAT.md`** — now a disposition over FOUR families at MEDIUM rather than one family at LOW. **PROVENANCE:** found by `27-REVIEW-3.md` CR-01 (BLOCKER), re-measured independently before being written, applied out-of-plan on maintainer authorisation; `REQUIREMENTS.md` is byte-unchanged and PRIV-05 remains `[ ]`. | Measured by plan 27-14 (the residual) and plan 27-16 (its reachability), filed by plan 27-16 at the measured severity with the inferred half labelled as inferred | 2026-08-26 |
| AR-28-01 | T-26-02-01 | **NEW, OPEN, severity MEDIUM — DERIVED from measurements this phase took (plan 28-03 task 1, `scanner/EvidenceTailReachTest`), NOT set by disposition and NOT inherited from any prior record.** At plan 28-03's blocking decision checkpoint on 2026-08-27 the maintainer chose `approve-as-proposed`, which explicitly ACCEPTS the derived severity rather than setting one; a maintainer-set severity and a measured one must never read alike, so this row states which it is. **MECHANISM (measurements A and B).** `ResponseAnalyzer` copies the matched substring of a per-vuln-class error or success signature into `VulnConfirmation.evidence`. `ResponseAnalyzer.kt:619` concatenates the response HEADERS with the body before matching, so `Set-Cookie` values sit inside the very text every signature is matched against. Reach is PROVEN in the positive direction rather than inferred: a cookie value shaped to the SQLI signature `Regex("Warning.*mysql_.*query")` (`ResponseAnalyzer.kt:28`) produced the evidence string `PHP MySQL warning: 'Warning_mysql_fetch_query'`, carrying the cookie value verbatim. The NEGATIVE direction is non-vacuous: an ordinary session token did NOT reach evidence AND a confirmation was still produced, so "did not reach" cannot be confused with "the analyzer never ran". **BOUND CORRECTION — the cap set is {80, 80, 60}, not 80.** `ROADMAP.md` states the tail is capped at 80 characters and that is FALSE OF THE CONTROL: the set DERIVED from the three evidence-construction sites is `ResponseAnalyzer.kt:682` `take(80)`, `:720` `take(60)`, `:791` `take(80)`. The caps also attenuate nothing where it counts — `ActiveAiScanner.kt:1206` re-truncates at `take(100)`, LARGER than every construction cap, so the whole evidence string survives onto the outbound path and the truncation prior records cite as reassurance is not a control. **EMISSION PATHS (measurement C) — six reads of `confirmation.evidence`, all in `ActiveAiScanner.kt`; TWO leave the machine.** (a) `:1206` goes to `ScanKnowledgeBase` and on to `AdaptivePayloadEngine.kt:52` into the adaptive-payload AI prompt, emitting in BALANCED and OFF and suppressed only in STRICT — and BALANCED is the DEFAULT posture, squarely inside PRIV-05's wording. (b) `:1242` goes to `ScannerIssueSupport.kt:123`, which builds `"  Evidence: $evidence"` with NO redaction argument directly beneath the `:120` `Original Value` line that IS sanitized, then on through `AuditIssue.detail()` to the `scanner_issues` MCP tool, emitting in ALL THREE modes. The other four reads are local-only on reads verified at source — the DB-hint scan, the response marker, `recordVulnSignal` (written and never read) and the `ActiveAiFinding` buffer — and the `active_scan_confirmed` audit event carries no evidence field at all. **SEVERITY, AGGRAVATING AND MITIGATING IN ONE BREATH.** Aggravating: reach is proven rather than inferred; one emitting path is unsuppressed in the DEFAULT posture; the `take(100)` re-truncation defeats every construction cap; and the asymmetry between the sanitized `Original Value` line at `ScannerIssueSupport.kt:120` and the unsanitized `Evidence` line at `:123` is invisible to anyone reading the rendered blob. Mitigating: the trigger is narrow and MEASURED rather than assumed — the cookie's OWN BYTES must match a DB or framework error signature such as `"You have an error in your SQL syntax"` or `"ORA-0[0-9]{4}:"` (`ResponseAnalyzer.kt:21-46`); STRICT closes the prompt path outright (`AdaptivePayloadEngine.kt:52` returns `emptySet()`); and `Redaction`'s shape-keyed `jwtRegex` (`Redaction.kt:1014`) and `bearerRegex` (`:114`) are not header-line-keyed, so they already catch a JWT- or bearer-shaped cookie value anywhere in MCP output including inside an `Evidence:` line, leaving opaque-token-shaped values as the residue. NOT `high`, because the trigger requires the cookie value to self-match a vuln signature, which is pathological rather than typical, and STRICT — the mode PRIV-05 names first — closes the default-posture path. NOT `low`, because reach is proven rather than inferred, one path emits in the DEFAULT posture, and the rendered-blob asymmetry makes the gap undetectable by inspection. MEDIUM places this in the SAME BAND as `AR-27-04` and `AR-27-11` — both reachable, both measured, both maintainer-accepted — which is the comparison a future round will make. **DISPOSITION: TRANSFER — examined and filed, deliberately NOT fixed (D-28-03).** The tail carries no type, no name and no shape a gate could key on. Both candidate control shapes are rejected: redacting `VulnConfirmation.evidence` wholesale destroys the field that is the finding's entire evidentiary value, which is round four's content-destruction class, and a shape-keyed rule is the blind mechanism D-28-01 rejects. No measurement yet exists of how often a cookie value can match a vuln signature. Calling this measurement a mitigation would be the overclaim vocabulary this record set exists to correct. **SCOPE: ONE LINE, NOT THE BLOB.** Plan 28-01 controls the `Original Value` LINE of the issue-detail blob — written at `ActiveAiScanner.kt:1239` and rendered at `ScannerIssueSupport.kt:120` through `sanitizeInjectionPointValue` — and it does NOT control the blob. The `Evidence` line in the SAME blob, built at `ActiveAiScanner.kt:1242`, is NOT controlled, and that uncontrolled line is the entire subject of THIS row. A reader arriving at this row first must meet that fact here, because the row whose subject IS the uncontrolled line is the last place the scope clause should be missing. **COUNTER POSITION, STATED AND NOT IMPLIED:** `threats_open` scans Threat Register rows whose id begins `T-26-` and nothing else, so this `AR-` row sits OUTSIDE that population at ANY severity. Appending it leaves the counter unmoved, and an unmoved counter is NOT evidence that nothing was found. **PRIV-05 DOES NOT CLOSE (D-28-04):** `AR-27-04` (MEDIUM), `AR-27-07` (LOW), `AR-27-10` (LOW), `AR-27-11` (MEDIUM, reachable in the DEFAULT posture) and this row remain OPEN, and `CookieCarrierInventoryTest`'s class KDoc disclaims completeness over four named blind axes. `.planning/REQUIREMENTS.md` is byte-unchanged and PRIV-05 stays an unchecked box. | Measured and DERIVED by plan 28-03 task 1 (disposition TRANSFER, not mitigate). OWNER: the MAINTAINER, at PHASE 28 HUMAN UAT — the same venue that dispositioned AR-27-04, AR-27-07, AR-27-10 and AR-27-11. Approved `approve-as-proposed` at plan 28-03's blocking decision checkpoint, 2026-08-27, with the derived severity accepted rather than set. | 2026-08-27 |

---

## Open findings on the serialized emission path — AR-27-04 and AR-27-05

Both were measured in plan 27-06 against the **compiled shipped classes** (`build/classes/kotlin/main`,
JDK 21 temurin-21, salt `probe-salt`, `recordMapping=false`), by re-running plan 27-05's throwaway
`ResidualProbe` with one shape added. The probe is deliberately **not committed**: a green assertion
under `src/` that a `Host` value survives STRICT is precisely the artifact this register exists to
stop producing. Its full source and exact commands are recorded in
`.planning/phases/27-priv-05-gap-closure-sanitize-headers/27-05-SUMMARY.md` and `27-06-SUMMARY.md`,
so the measurement stays re-runnable without living in the tree.

### AR-27-04 — `Host:` and `SiteMapEntry.url` un-anonymised under STRICT

Observed output, verbatim, on the raw-message-in-JSON shape after plans 27-04 and 27-05:

```
==== SHAPE: raw-message-in-JSON (335 bytes) ====
carries an escaped newline: true
STRICT    COOKIE          STRIPPED
STRICT    SETCOOKIE       STRIPPED
STRICT    APIKEY          STRIPPED
STRICT    BEARER          STRIPPED
STRICT    HOST-HEADER     SURVIVES
STRICT    URL-FIELD       SURVIVES
STRICT    BENIGN-CONTROL  SURVIVES
---- STRICT output ----
{"url":"https://shop.example/basket","request":"GET /basket HTTP/1.1\r\nHost: shop.example\r\nCookie: [STRIPPED]\r\nSet-Cookie: [STRIPPED]\r\nX-API-Key: [REDACTED]\r\nAuthorization: [REDACTED]\r\nX-Request-Id: benignprobecontrol\r\n\r\n","response":"HTTP/1.1 200 OK\r\n\r\n"}
BALANCED  COOKIE          STRIPPED
BALANCED  SETCOOKIE       STRIPPED
BALANCED  APIKEY          STRIPPED
BALANCED  BEARER          STRIPPED
BALANCED  HOST-HEADER     SURVIVES
BALANCED  URL-FIELD       SURVIVES
BALANCED  BENIGN-CONTROL  SURVIVES
```

The cookie and API-key rows read `STRIPPED`, so the probe ran against classes containing both fixes
and the measurement is valid rather than vacuous. Under BALANCED `anonymizeHosts` is `false`, so the
host is *expected* to survive there; **the finding is the STRICT row**, where the policy asks for
anonymisation and does not get it on this shape.

**Two measured reasons it was excluded from this phase's code change** — neither aesthetic, both
re-read at source in plan 27-06:

1. `hostHeaderRegex` (`Redaction.kt:1810`) is `Regex("(?im)^host:\\s*([^\\s]+)\\s*$")` — real-line
   anchored, and deliberately NOT routed through `logicalLineHeaderRule` (D-27-13). Routing it there
   makes it rewrite through `anonymizeHost`, which records into a de-anonymisation map that
   `RedactionHostMapBoundTest` exists to bound; firing that per raw message on every `site_map` and
   `proxy_http_history` result is an **unmeasured load change on that bound**.
2. `SiteMapEntry.url` (`Serialization.kt:80`, field declared at `:159`) is
   `url = req?.url() ?: "<no url>"` — the SAME host, verbatim, with no `maybeAnonymizeUrl` in front
   of it. Anonymising only the header yields a JSON object whose `request` field is anonymised and
   whose `url` field is not: **a control that reads as closed and is not.**

The exclusion is asserted from source, not merely written down: `LogicalLineBoundaryScopeTest`
(3 tests, green) fails if `hostHeaderRegex` is routed through the composer, and fails if the
rationale comment stops agreeing with the code.

### AR-27-04 — disposition, 2026-08-24

**Chosen option, recorded verbatim: `accept-residual` — "Accept as a recorded residual at medium
severity".**

**Provenance, stated plainly because it changes how much weight this disposition carries:
AUTO-SELECTED BY THE CONFIGURED RUN MODE, NOT MAINTAINER-CHOSEN.** Plan 27-06's task 3 is a
`checkpoint:decision` carrying `gate="blocking"`, and this project runs `mode: yolo`, which
auto-selects blocking checkpoints; the first option was taken. The plan anticipated exactly this
(threat `T-27-06-07`) and moved the substantive checks off the checkpoint and onto automated gates,
which did hold: the diff gates, the computed counter and the quoted probe output above are all
executor-verified. **A future auditor should read this row as a recorded default, not as a human
having weighed the release posture.** It is re-openable at no cost, and re-opening it does not
invalidate anything else in this file.

**Reason recorded for the choice.** The two competing options were `follow-up-phase` and `close-now`.
`close-now` was excluded by the plan's own instruction — fixing `hostHeaderRegex` and the `url` field
together is plan-set revision work, not something to improvise inside a checkpoint — and doing it
under time pressure is precisely how the map-load question would go unmeasured. `follow-up-phase`
remains open to the maintainer and is the option this record recommends if the promise-vs-behaviour
gap is judged unacceptable on a shipped 1.0.0. `accept-residual` keeps the phase scoped to what it
measured and gated, and leaves the next audit a measurement rather than a silence.

**The cost this option carries, and the concrete item that pays it (`T-27-06-06`, disposition
`transfer`).** Accepting the residual means STRICT's user-facing privacy claim stays broader than
STRICT's behaviour until a later phase, so the gap must be named in the user-facing documentation or
the overclaim simply relocates from this register into the docs. **Backlog item, naming the files to
change:** `README.md:247` ("STRICT privacy mode anonymizes hosts using real HKDF …") and `SPEC.md:80`
(the privacy-mode table's `anonymized (HKDF/HmacSHA256)` cell) with its accompanying paragraph at
`SPEC.md:86` must state that host anonymisation applies to the prompt path and to parsed-header tool
results, and **does not** apply to the raw HTTP message or the `url` field emitted by
`proxy_http_history`, `proxy_http_history_regex`, `site_map`, `site_map_regex` and `scanner_issues`.
Not done in this plan: `files_modified` scopes plan 27-06 to the three record files, and a
user-facing documentation edit is a change to what ships, not a record repair. **Until that edit
lands, this residual is accepted AND the documentation still overclaims** — recorded here rather than
left to be discovered.

**Severity unchanged at MEDIUM**, so `threats_open` did not need re-deriving for a severity change;
it was re-run after this section was written and still returns `0` over 46 rows, 46 closed.

### AR-27-05 — the header-map shape carries no line boundary at all

Observed output, verbatim, on the header-map shape, same run:

```
==== SHAPE: header-map-in-JSON (246 bytes) ====
carries an escaped newline: false
STRICT    COOKIE          SURVIVES
STRICT    APIKEY          STRIPPED
STRICT    BEARER          STRIPPED
STRICT    HOST-VALUE      SURVIVES
STRICT    BENIGN-CONTROL  SURVIVES
---- STRICT output ----
{"method":"GET","url":"https://shop.example/basket","headers":{"Host":"shop.example","X-Cookie":"probecookiesentinel","X-API-Key":"[REDACTED]","Authorization":"Bearer [REDACTED]","X-Request-Id":"benignprobecontrol"},"body":null}
```

Attribution probe on bare JSON pairs, same run, STRICT — this is what makes the asymmetry a
measurement rather than an inference:

```
bare X-API-Key JSON pair   ->  {"X-API-Key":"[REDACTED]"}
bare X-Cookie   JSON pair   ->  {"X-Cookie":"probecookiesentinel"}
bare Cookie     JSON pair   ->  {"Cookie":"probecookiesentinel"}
bare Host       JSON pair   ->  {"Host":"shop.example"}
```

The auth class has an independent backstop on this shape (the JSON-key rule reaches
`"X-API-Key": "…"`); **the cookie class has none, because `cookie` is absent from `SENSITIVE_WORDS`**
— which is AR-27-02, measured as still load-bearing here.

**This is a no-backstop bound, not a live leak, and the record must not blur the two.** All four
`ParsedRequest` / `ParsedResponse` construction sites pass `headers = sanitizeHeaders(…)` —
`McpToolExecutorImpl.kt:369` (`request_parse`) and `:387` (`response_parse`),
`McpToolLegacy.kt:179` and `:201` — each re-read at source in plan 27-06 rather than taken from a
SUMMARY. A cookie value therefore does **not** reach an AI backend on `request_parse` /
`response_parse` today. What is open is that `sanitizeHeaders` is the **sole** control there: if it
is narrowed, bypassed at a new call site, or omitted by a future `ParsedRequest` producer, nothing
downstream recovers the miss. **This is what bounds AR-27-01's closure** — `redactIfNeeded` is a
second control on the raw-message-in-JSON shape and on no other.

---

## Open findings on the parameter carrier — AR-27-06, AR-27-07 and AR-27-08

Added 2026-08-25 by plan 27-09, from measurements made by plans 27-07 and 27-08. Every number,
name and severity in this section is READ FROM `27-07-SUMMARY.md` or `27-08-SUMMARY.md`; none is
carried over from a PLAN's prose. The two measurement probes were run against the **compiled
shipped classes** (`build/classes/kotlin/main`, JDK 21 temurin-21, salt `probe-salt`,
`recordMapping=false`) and are deliberately **not committed**: a green assertion under `src/` that
a sensitive value survives STRICT is precisely the artifact this register exists to stop
producing. Their full source and exact invocation are reproduced in `27-08-SUMMARY.md`, so the
measurements stay re-runnable without living in the tree.

### AR-27-06 — both MCP parameter shapes are single-control, and the exact mirror of AR-27-05

AR-27-05 recorded that the header-MAP shape carries no line boundary, so `redactIfNeeded` cannot
recover a missed cookie there and `sanitizeHeaders` is the sole control. **AR-27-06 is the same
sentence one field over, and the fact that these two fields sit in the SAME JSON object is why
the third refutation was possible at all**: clause (3)'s control stripped the `headers` map while
the `parameters` array beside it, in the same object, was emitted raw.

The three rules that could have been a backstop, and why each cannot fire — each read at source
rather than assumed:

| Shape | Rule | Why it cannot fire |
|---|---|---|
| serialized `ParsedParam` (`request_parse`) | `logicalLineHeaderRule`'s two branches | The payload carries **no line boundary of any kind** — neither a real newline nor a JSON-escaped one. Neither branch has anything to bind to. This is AR-27-05's mechanism exactly. |
| serialized `ParsedParam` (`request_parse`) | `jsonSecretKeyRegex` | It keys on the JSON KEY. Here the key carrying the value is the literal `value`, which is not in `SENSITIVE_WORDS`; the parameter's own name sits in a sibling `name` key where no rule looks. AR-27-02 recorded this same mechanism one field over. |
| `params_extract` line | `logicalLineHeaderRule` | This shape DOES carry a real newline, so the boundary is not the obstacle — but the line is `type=… name=… value=…`, which carries no header name for the rule to match. |
| both | `Redaction.cookieTypedParamRegex` | Keyed to the PROMPT path's rendered `name=value (TYPE)` suffix, produced by `formatParamLine` in `scanner/PassiveAiScannerPrompts.kt` and by nothing else. Neither MCP shape produces it. Plan 27-08 narrowed this rule's own comment block so its DOCUMENTED reach now equals its ACTUAL reach — the previous "reached through `request.parameters()`" claim was true of the DATA and false of the RULE, and that gap is how two live MCP tools leaked past it for three rounds. |

**Therefore `McpToolHelpers.sanitizeParameters` is the SOLE control on this carrier.**

**This is a no-backstop BOUND, not a live leak, and the record must not blur the two.** All four
producers pass through the sanitizer after plan 27-07 — `McpToolLegacy.kt:160` (`params_extract`)
and `:189` (`request_parse`), `McpToolExecutorImpl.kt:360` and `:381` — each re-measured at
execution time rather than taken from a plan (B2 = 5 sites: one declaration plus these four). A
cookie value therefore does **not** reach an AI backend through `request_parse` or
`params_extract` today. What is open is that if `sanitizeParameters` is narrowed, bypassed at a
new producer, or omitted from a future one, **nothing downstream recovers the miss**.

**The producer-ownership pin is the control on THAT risk, and its bound is stated with it.**
`ParameterCarrierRedactionTest`'s pin asserts the producer inventory is exactly four and that
every one routes through the sanitizer; plan 27-07's red probe 3 confirmed it goes red both on a
delegating stub and on a genuine bypass constructing `ParsedParam` raw. **What the pin is NOT:**
it is a SOURCE SCAN, not a behavioural proof. Plan 27-07 recorded, as a finding rather than as an
aside, that the behavioural probes CANNOT go red on a producer unwiring — every producer begins
`HttpRequest.httpRequest(content)`, a Montoya static factory requiring Burp's internal
`ObjectFactory` that cannot run in a pure-JVM test. That is a division of labour and it is stated
here so nobody reads the green suite as covering both questions: the behavioural probes prove the
SANITIZER, the ownership pin proves the PRODUCERS are wired to it, and neither proves the other.

**Severity MEDIUM, and the provenance of that number is stated because it changes its weight:**
it is **AUTHORED by analogy** with AR-27-05's no-backstop reasoning, by plan 27-09. It is not a
measured severity, unlike AR-27-07 and AR-27-08 below.

### AR-27-07 and AR-27-08 — the two measured results

Both were measured by plan 27-08 task 3 and neither was fixed there. The full probe payloads,
per-mode verdicts, complete outputs and the probe harness source are in `27-08-SUMMARY.md` under
**THE TWO MEASURED-NOT-FIXED RESULTS**; the register rows above carry the results, mechanisms,
reachability and severities. Two properties are recorded here because they are what a reader must
not lose:

1. **Each measurement had a CONTROL, and each control FIRED.** A null result is only a measurement
   when a positive control fired on the same run; otherwise it is an inference and must be
   labelled one. For AR-27-07 the control was the same two sensitive names presented as bare JSON
   KEYS, redacted under STRICT and BALANCED and surviving under OFF. For AR-27-08 the control was
   a real `Cookie:` header inside the SAME `IssueDetails` object, which became `Cookie: [STRIPPED]`
   in the same STRICT output in which the detail-line sentinel survived.

2. **The two findings are NOT of the same kind, and averaging them would be the error.** AR-27-07
   is caller-echoed content — `request_parse` and `params_extract` parse a string the AI agent
   supplied, so it already held those bytes. AR-27-08 is **Burp-held proxied traffic**: a real
   session cookie the operator's browser sent, which the backend did not previously possess.
   That is why one is `low` and the other `medium`, and why AR-27-08 is the one that gets a named
   successor rather than a note.

**AR-27-08's disposition: TRANSFER, and the transfer has a NAMED OWNER.** Plan 27-08 applied no
control to the issue-detail route; plan 27-09 files it here and opens **Phase 28** in
`ROADMAP.md`, which owns both the route and the unconverted cookie-type predicate at
`scanner/InjectionPointExtractor.kt:29` that feeds it. The pair is deferred together deliberately:
the predicate is only meaningful as part of the route it feeds, and fixing it alone would make the
route LOOK addressed while leaving it open — the exact failure mode this row has recorded three
times. **`.planning/BACKLOG.md` does not exist in this repository**, which is why the successor is
a roadmap entry rather than a backlog line: a residual filed only in this register and a phase
SUMMARY lives in the two documents a maintainer is least likely to open.

---

## Open findings from round 4 — AR-27-09 and AR-27-10

Added 2026-08-26 by plan 27-13, from measurements made by plans 27-10 and 27-11. **Every number,
string and character in this section is READ FROM `27-10-SUMMARY.md` or `27-11-SUMMARY.md`; none is
carried over from a PLAN's prose.** That distinction is not decoration here — `WINDOWS.md` entries
26 and 29 both record this phase filing a plan-time projection as though it were a measurement, and
plan 27-13's own task precondition names writing a number from prose as the defect to avoid.

Neither probe is committed, for the reason this file has now stated four times and the reason
standing-rule clause (vi) below makes operative: **a green assertion under `src/` that a sensitive
value survives a redacting policy is precisely the artifact this register exists to stop producing.**
Their full harness source and exact invocations are reproduced in the two SUMMARYs, so the
measurements stay re-runnable without living in the tree.

### AR-27-09 — the FOURTH logical-line start, measured surviving in BOTH redacting modes

**[SUPERSEDED 2026-08-26 by plan 27-17 — `AR-27-09` is CLOSED BY FIX, not open at LOW.** The paragraph above is preserved byte-for-byte as the record made while the finding was open; it is not withdrawn. The LOW it carries rested on an explicitly UNMEASURED reachability claim, which is why the maintainer decided the finding by FIX rather than by acceptance at UAT (`27-HUMAN-UAT.md` item 10). See the amendment on its Accepted Risks Log row and the **"AR-27-09 — CLOSED BY FIX 2026-08-26"** section for the measured before/after. `AR-27-09` is an `AR-` row and was therefore always outside the `threats_open` population — closing it did not and could not move that counter.**]**

Driven through a throwaway `jshell` harness against the **freshly compiled classes**
(`build/classes/kotlin/main` plus `kotlin-stdlib-2.2.21`, JDK 21), calling
`Redaction.INSTANCE.apply(raw, RedactionPolicy.Companion.fromMode(mode), "round4-measurement-salt", false)`.
`\r` and `\n` are rendered as two-character escapes so the raw bytes are legible. Observed output,
verbatim:

```
== indented-header (AR-27-09) / STRICT
BEFORE: GET / HTTP/1.1\r\n Cookie: a=SECRET5\r\n\r\n
AFTER : GET / HTTP/1.1\r\n Cookie: a=SECRET5\r\n\r\n
SURVIVES: true

== indented-header (AR-27-09) / BALANCED
BEFORE: GET / HTTP/1.1\r\n Cookie: a=SECRET5\r\n\r\n
AFTER : GET / HTTP/1.1\r\n Cookie: a=SECRET5\r\n\r\n
SURVIVES: true
```

**The positive controls fired on the SAME harness run**, which is what makes this a statement about
the rule's REACH rather than a broken fixture:

```
== json-string-open (the fix) / STRICT
BEFORE: {"notes":"Cookie: a=SECRET1\r\nX: y"}
AFTER : {"notes":"Cookie: [STRIPPED]\r\nX: y"}

== real-line control (unchanged branch) / STRICT
BEFORE: GET / HTTP/1.1\r\nCookie: a=SECRET7\r\n\r\n
AFTER : GET / HTTP/1.1\r\nCookie: [STRIPPED]\r\n\r\n
```

**THE RECORD WAS ONE MODE TOO NARROW, AND IT WAS WIDENED RATHER THAN LEFT.**
`27-VERIFICATION-3.md` recorded this residual as surviving under **STRICT**. Plan 27-11's task 3
re-measured it against the compiled classes at the end of round 4 instead of copying the round-3
sentence forward, and found it surviving byte-unchanged under **STRICT and BALANCED** — one mode
wider. The source sentence beside the rule in `Redaction.kt` was widened to match, and this row
carries the wider value. `27-11-SUMMARY.md` records the direction of the error explicitly:
**understating a residual is the same failure mode as overclaiming a fix.**

**The one-token fix — `^[ \t]*` in place of `^` on the real-line branch — is written into
`Redaction.kt` beside the residual**, and its presence there is asserted from source by
`LogicalLineBoundaryScopeTest.theStatedBoundIsPresentWhereAReaderMeetsIt`, which carries
`SECOND_OPEN_FINDING = "AR-27-09"` as a named constant in the same shape `OPEN_FINDING` carries
`AR-27-04`. A successor does not have to re-derive it, and a successor who deletes the rationale
without replacing it turns that test red.

**[SUPERSEDED 2026-08-26 by plan 27-17 — `AR-27-09` is CLOSED BY FIX, not open at LOW.** The paragraph above is preserved byte-for-byte as the record made while the finding was open; it is not withdrawn. The LOW it carries rested on an explicitly UNMEASURED reachability claim, which is why the maintainer decided the finding by FIX rather than by acceptance at UAT (`27-HUMAN-UAT.md` item 10). See the amendment on its Accepted Risks Log row and the **"AR-27-09 — CLOSED BY FIX 2026-08-26"** section for the measured before/after. `AR-27-09` is an `AR-` row and was therefore always outside the `threats_open` population — closing it did not and could not move that counter.**]**

**What bounds the severity, stated so a reader can check it rather than infer it.** No measured
emission site in this repository indents a header line: the 14 pinned serialized-emission sites emit
a machine-generated message `toString()` at column 0, and `buildScanMetadataText` `appendLine`s each
admitted header at column 0. What is NOT measured is whether an indented header line can reach the
composer through analyst-authored free text in `HttpRequestResponse.notes` — the same field plan
27-11's own fix was measured on. That gap is why the row is `low` **and** open, rather than `low`
and closed.

### AR-27-09 — CLOSED BY FIX 2026-08-26 (plan 27-17)

**Disposition:** CLOSED BY FIX. Decided by the **maintainer at UAT**, recorded in `27-HUMAN-UAT.md`
item 10 (commit `ae3371a`). The alternative on the table was acceptance at LOW; it was rejected
because the LOW rested on a reachability claim the row itself marks UNMEASURED, and a severity
derived from an unmeasured reachability claim is the defect class that reopened this phase five
times. Everything recorded above this heading is preserved as the record made while the finding was
open; none of it is withdrawn.

**The change.** `Redaction.logicalLineHeaderRule`'s REAL-LINE branch now begins at a new named
constant instead of a bare `^`:

```kotlin
private const val REAL_LINE_START = "^[ \t]*+"
```

Only that branch's START moved. `JSON_STRING_OPEN`, `JSON_ESCAPED_NEWLINE`,
`JSON_ESCAPED_HEADER_VALUE`, `REAL_LINE_HEADER_VALUE` and the escaped branch's two lookbehinds are
byte-unchanged.

**Measured before and after**, through `Redaction.apply` on freshly compiled classes, in STRICT and
in BALANCED, with the un-indented control stripping in the same run. `\r` and `\n` are rendered as
two-character escapes so the raw bytes are legible.

```
                                                        STRICT / BALANCED
GET / HTTP/1.1\r\n Cookie: a=SECRET5\r\n\r\n
  BEFORE  GET / HTTP/1.1\r\n Cookie: a=SECRET5\r\n\r\n   byte-unchanged, both modes
  AFTER   GET / HTTP/1.1\r\n Cookie: [STRIPPED]\r\n\r\n  stripped, both modes

HTTP/1.1 200 OK\r\n Set-Cookie: s=SECRET6; Path=/\r\n\r\n
  BEFORE  HTTP/1.1 200 OK\r\n Set-Cookie: s=SECRET6; Path=/\r\n\r\n   byte-unchanged, both modes
  AFTER   HTTP/1.1 200 OK\r\n Set-Cookie: [STRIPPED]\r\n\r\n          stripped, both modes

GET / HTTP/1.1\r\n  X-Api-Key: SECRET8\r\n\r\n
  BEFORE  GET / HTTP/1.1\r\n  X-Api-Key: SECRET8\r\n\r\n     byte-unchanged, both modes
  AFTER   GET / HTTP/1.1\r\n  X-Api-Key: [REDACTED]\r\n\r\n   redacted, both modes

obs-folded continuation
GET / HTTP/1.1\r\nX-Foo: bar\r\n Cookie: a=SECRET9\r\n\r\n
  BEFORE  GET / HTTP/1.1\r\nX-Foo: bar\r\n Cookie: a=SECRET9\r\n\r\n    byte-unchanged, both modes
  AFTER   GET / HTTP/1.1\r\nX-Foo: bar\r\n Cookie: [STRIPPED]\r\n\r\n   stripped, both modes

un-indented controls, both modes, before AND after — unchanged by the fix
  GET / HTTP/1.1\r\nCookie: a=SECRET7\r\n\r\n        ->  Cookie: [STRIPPED]
  HTTP/1.1 200 OK\r\nSet-Cookie: s=SECRET7; Path=/  ->  Set-Cookie: [STRIPPED]
  GET / HTTP/1.1\r\nX-Api-Key: SECRET8\r\n\r\n       ->  X-Api-Key: [REDACTED]
```

All three composed rules — `cookieHeaderRegex`, `setCookieHeaderRegex`, `authHeaderRegex` — gained
the start together, because they share the composer.

**One correction to the round-4 record, measured rather than inherited.** The auth family's pre-fix
state was PARTLY MASKED. An indented `Authorization: Bearer …` line was already losing its token
before this fix — but to `bearerRegex`, a VALUE-level rule, which produced
`Authorization: Bearer [REDACTED]` while the HEADER rule missed the line entirely. A gate built on
`Authorization` would therefore have been green before the fix and would have proved nothing. The
auth evidence above is a plain-token `X-Api-Key` for that reason. Post-fix the same line reads
`Authorization: [REDACTED]`, which is the header rule firing.

**The hazard measured before the "one-token fix" was applied — because it was not one token.** The
fix written down in round 4 was `^[ \t]*` in place of `^`. `^` is ZERO-WIDTH; `^[ \t]*` is
CONSUMING; and `Redaction.apply`'s three replacement lambdas rebuild the header with
`m.value.substringBefore(":")`. The composer's own KDoc says the escaped branch uses a lookbehind
*precisely* to keep the match beginning at the header name. Measured: with the indent consumed the
match value becomes `" Cookie: a=SECRET5"`, `substringBefore(":")` yields `" Cookie"`, and the lambda
emits `"$header: [STRIPPED]"` — so the indent is **re-emitted verbatim** and round-trips byte-exact
for spaces and for tabs (`"\t\tCookie"` measured). No name-matching predicate receives the widened
value: `isCookieHeaderName` is called on header names Burp supplies, never on a match value. The
relaxation is therefore safe **on the real-line branch only**, and it is stated as such in source.

**A premise of the fix brief was falsified by measurement, and is recorded rather than reconciled.**
The brief stated that Java forbids a variable-width lookbehind, so the zero-width spelling
`(?<=^[ \t]*)` was unavailable. On this toolchain (JVM 21) it **compiles and matches correctly**,
with the match still beginning at the header name. It was rejected on **cost**, which is the
measurable reason rather than the repeated one:

```
2000 scans of a 60-line pretty-printed document
  ^                   158 ms
  ^[ \t]*+            155 ms      <- shipped
  ^[ \t]*             164 ms
  (?<=^[ \t]*)      34237 ms      <- ~221x, rejected

200 scans of a line of 4000 spaces followed by non-header text
  ^[ \t]*              63 ms
  ^[ \t]*+             29 ms      <- shipped
```

An unbounded look-back is retried at every position, and these header rules run in the header stage
with **no per-pattern deadline** — the same cost model that rejected a variable-width
`JSON_ESCAPED_NEWLINE` for a mere 2.4x.

**Why possessive, and why possessive is not a narrowing.** `[ \t]*+` never gives whitespace back. It
cannot lose a match: every alternative of every composed name pattern begins with `COOKIE_NAME_PART`,
the literal `set-`/`cookie`, or a letter of the auth-name alternation, none of which can match a
space or a tab, so giving back could never enable a match the possessive form misses.

**Direction, proven rather than asserted.** `[ \t]*+` matches the empty string and no composed name
pattern can begin with horizontal whitespace, so wherever `^` matched there is nothing to consume and
the two spellings are position-identical. The new start is a **strict superset** of the shipped one:
it can only ADD matches, i.e. it moves only in the OVER-redacting direction.

**Gates, and their mutation proof.** `IndentedLogicalLineStartTest` (9 tests) carries the indented
positives in both modes for all three rules, the obs-folded continuation, the un-indented
non-vacuity controls, a byte-identity NEGATIVE corpus over indented prose / pretty-printed JSON /
indented source / an indented log line / an indented prose colon-run, and both halves of the
direction proof. `LogicalLineBoundaryScopeTest.theRealLineStartRecognisesLeadingHorizontalWhitespace`
reads the constant's VALUE out of `Redaction.kt` at test time, so a nine-character revert cannot ship
green through a source scan either. Mutation-proven in BOTH directions:

```
REAL_LINE_START = "^"        (the pre-fix revert)
  RED   theIndentedCookieHeaderIsStrippedInBothRedactingModes
  RED   theIndentedSetCookieHeaderIsStrippedInBothRedactingModes
  RED   theIndentedPlainTokenAuthHeaderIsRedactedInBothRedactingModes
  RED   theIndentedAuthorizationHeaderIsRedactedByTheHeaderRuleAndNotOnlyByTheValueRule
  RED   theObsFoldedContinuationLineIsARecognisedLogicalLineStart
  RED   theWideningAddsRedactionWithoutMovingTheSurroundingBytes
  RED   theRealLineStartRecognisesLeadingHorizontalWhitespace
  GREEN theUnindentedControlsStillRedact                                  <- non-vacuity control
  GREEN theShippedAnchorsBehaviourIsPreservedWhereNoLineBeginsWithWhitespace  <- direction control
  GREEN theWidenedStartDoesNotEatIndentedNonHeaderContent                 <- wrong-direction gate

REAL_LINE_START = "^[^:]*"   (an over-widening)
  RED   theWidenedStartDoesNotEatIndentedNonHeaderContent
  RED   theRealLineStartRecognisesLeadingHorizontalWhitespace
  (all other new gates GREEN — the over-widening still strips the indented headers)
```

**What this does NOT close.** **PRIV-05 remains UNSATISFIED and UNTICKED**; `.planning/REQUIREMENTS.md`
is byte-unchanged by this amendment. `AR-27-10` and `AR-27-11` are untouched and still OPEN, as are
`AR-27-04`, `AR-27-06`, `AR-27-07` and `AR-27-08`. The boundary is not complete: it now recognises
FOUR logical line starts, and `AR-27-11` enumerates four families it still cannot see.

**An unexpected side effect, measured and reported rather than left for a reviewer to find.** The `jacocoTestCoverageVerification` gate on the `redact` package's BRANCH ratio was a maintainer-accepted RED (~0.928 against a 0.930 floor). The nine gates added by `IndentedLogicalLineStartTest` cover branches in that package, and the ratio measured on a clean `./gradlew clean check` is now **181/194 = 0.93299**, so that gate is GREEN and `./gradlew check` passes end to end. **The floor was NOT adjusted in either direction** — it is still `0.930`. This is recorded because a gate silently changing colour is exactly the kind of unstated movement this phase's standing rules exist to surface; nothing here should be read as a decision to rely on the gate staying green, since the margin is 0.003.

**The counter.** `AR-27-09` is an `AR-` row. The `threats_open` `awk` matches rows beginning
`| T-26-` **only**, so this finding sat outside that counter's population while it was open and sits
outside it now that it is closed — stated explicitly rather than left implicit. The counter was
recomputed with the documented command after this amendment and is unchanged at `0`.


### AR-27-10 — the residual character set, derived from a source-pinned partition

Observed output, verbatim from `27-10-SUMMARY.md` §6. The class TEXT is extracted from `Redaction.kt`
at test time by `theCoveredSetIsReadFromRedactionSourceNotRetyped` — **it is not a re-typed copy**,
which is what gives this table an expiry date that fails loudly instead of one that passes quietly:

```
shipped class text      : A-Za-z0-9_-
expands to (COVERED_TCHARS), 64 chars : A-Z  a-z  0-9  _  -
ALL_RFC9110_TCHARS      : 77  (15 punctuation + 10 digits + 52 letters)
NOT_COVERED_TCHARS      : 13
```

**`NOT_COVERED_TCHARS`, exact contents:**

```
! # $ % & ' * + . ^ ` | ~
```

(as a single string: ``!#$%&'*+.^|~`` — thirteen characters.)

`NOT_COVERED_TCHARS` is **derived** as `ALL_RFC9110_TCHARS - COVERED_TCHARS`, never hand-listed, so
it cannot disagree with its operands. The partition is machine-checked in both directions
(`theThreeCharacterSetsPartitionEachOther`, `theScanIsNonVacuous`), and the expander itself is proven
non-vacuous: `expandCharClass("a-c1") == {a, b, c, 1}`, and the PRE-FIX class `A-Za-z0-9-` is
asserted **NOT** equal to `COVERED_TCHARS` (63 versus 64 characters) — an assertion that fails on the
unfixed tree, which is what makes it a probe rather than a restatement.

**THE HALF OF AR-27-10 THAT IS MEASURED AND THE HALF THAT IS INFERRED, separated rather than
averaged.** MEASURED: the partition above, read from the shipped constant; and the fail-open
mechanism itself, measured end-to-end on `_` (nine names, both modes, pre-fix and post-fix — see
clause (6) and `27-10-SUMMARY.md` §3). INFERRED: that the same mechanism carries over to the other
thirteen characters. **No leak was measured for any of the thirteen, and neither the row above nor
this section claims one.** The `low` severity rests partly on a judgment about header-naming
convention, which is labelled as a judgment in the row itself.

**Why this row exists at all, which is the more useful half.** For three rounds the identical
residual — the difference set between a bare-contains predicate and a bounded character class —
lived in a source comment and in a green test, and appeared in **no** security record under
`.planning/`. `27-VERIFICATION-3.md` grepped four record files for it and got zero hits. That is how
it survived to become the fourth refutation of T-26-02-01. **A difference set enumerated in source
and nowhere else is not a recorded residual**, and this section is the correction of that pattern
rather than merely the filing of a character list.

## Open finding from round 5 — AR-27-11

Added 2026-08-26 by plan 27-16. **This is the residual round 5 CREATED, and it is filed by the round
that created it.** That sentence is the whole point of standing-rule clause (vii) below: round 4
also created two residuals, named neither in its own residual list, and both were found by the next
verifier instead of by the round that shipped them.

Its probe is not committed, for the reason this file has now stated five times and standing-rule
clause (vi) makes operative: **a green assertion under `src/` that a sensitive value survives a
redacting policy is precisely the artifact this register exists to stop producing.** The harness and
the exact invocation are reproduced below, so the measurement stays re-runnable without living in
the tree.

### AR-27-11 — the JSON-array-element logical-line start, and the cost of the narrowing

Driven against the **freshly compiled classes** (`build/classes/kotlin/main` plus
`kotlin-stdlib-2.2.21`, JDK 21), calling
`Redaction.INSTANCE.apply(raw, RedactionPolicy.Companion.fromMode(mode), "round5-probe-salt", false)`.
`\r` and `\n` are rendered as two-character escapes so the raw bytes are legible. STRICT and
BALANCED produced identical output for every case.

**THE RESIDUAL, quoted verbatim from `27-14-SUMMARY.md` PROBE D — both columns, so the row records a
measurement rather than an argument:**

```
{"tags":["Cookie: a=SECRET8"]}   BEFORE (bare quote)  ->  {"tags":["Cookie: [STRIPPED]"]}
{"tags":["Cookie: a=SECRET8"]}   AFTER  (`:"`)        ->  {"tags":["Cookie: a=SECRET8"]}   (byte-identical to input)
```

**THE BOUND ON ITS SEVERITY, MEASURED IN THIS TASK RATHER THAN REASONED — and this is the half that
distinguishes this row from a plausible one. Observed output, verbatim:**

```
== E: array element, header AFTER an escaped newline / STRICT
BEFORE: {"requests":["GET / HTTP/1.1\r\nCookie: a=SECRET8\r\n\r\n"]}
AFTER : {"requests":["GET / HTTP/1.1\r\nCookie: [STRIPPED]\r\n\r\n"]}
IDENTICAL: false

== F: array element, comma-quote second element, header at open / STRICT
BEFORE: {"tags":["x","Cookie: a=SECRET8"]}
AFTER : {"tags":["x","Cookie: a=SECRET8"]}
IDENTICAL: true

== G: control - object field value open / STRICT
BEFORE: {"notes":"Cookie: a=SECRET8"}
AFTER : {"notes":"Cookie: [STRIPPED]"}
IDENTICAL: false
```

**Two positive controls fired in the SAME harness run** (E and G), which is what makes this a
statement about the rule's REACH rather than a broken fixture. The consequence a reader should hold
onto: the residual is NARROWER than "arrays are not covered". Only a header that is the FIRST content
of an array-element string escapes. A realistic raw HTTP message inside an array element is still
stripped, because its header sits after an escaped newline — which the boundary DOES recognise. Case
F shows the residual covers the comma-quote spelling as well as the bracket-quote one, so the gap is
about the ELEMENT open and not about the first element specifically.

### The reachability enumeration, taken at source in this task

The question the severity turns on: **which serialized fields on the MCP emission path are JSON
ARRAYS OF STRINGS whose contents are analyst-authored or response-derived rather than
machine-generated?** It was answered by reading the schema, not by assuming an answer.

| Where | What was found | Bearing on AR-27-11 |
|---|---|---|
| `mcp/schema/Serialization.kt` — the tool-result emission schema | **ZERO** `List<String>` fields. Its only two list fields are `IssueDetails.requestResponses: List<HttpRequestResponse>` and `IssueDetails.collaboratorInteractions: List<Interaction>` | arrays of OBJECTS; every string member opens at `:"`, which IS a recognised start |
| multi-item tool results | joined by `McpToolContext.limitedJoin` with a `\n\n` separator | no JSON array wrapper is emitted at all |
| `mcp/tools/McpToolModels.kt` | **5** `List<String>` fields — `ComparerSend.items`, `CollaboratorGenerate.options`, `StartAuditMode.requests`, `StartAuditWithRequests.requests`, `StartCrawl.seedUrls` | all INPUT models, reached only via `decode<…>` / `asInputSchema()`; **none** is ever passed to `encodeToString`, so no emitted byte passes through them |
| `McpToolExecutorImpl.routeExternalToolCall` | `context.redactIfNeeded(argsJson.orEmpty())` — the D-03 outbound-privacy redaction of MODEL-authored args forwarded to a THIRD-PARTY external MCP server | **the one carrier that can emit an arbitrary JSON array of strings through `Redaction.apply`.** Its content may be response-derived; the REMOTE tool schemas it forwards to are not owned by this repository and are UNMEASURED |

**The honest summary, in the words the severity rests on: the reachability is MEASURED for this
repository's own emission schema and is ZERO there; it is MEASURED as NON-ZERO on exactly one
carrier, the external-tool args path; and the shape of what a remote tool schema declares on that
carrier is UNMEASURED.** That mixture is why the row is `low` **and** open rather than `low` and
closed — the same reasoning AR-27-09's row applies to its own unmeasured half, stated at the weight
the evidence supports rather than rounded in either direction.

**[SUPERSEDED 2026-08-26 by plan 27-17 — `AR-27-09` is CLOSED BY FIX, not open at LOW.** The paragraph above is preserved byte-for-byte as the record made while the finding was open; it is not withdrawn. The LOW it carries rested on an explicitly UNMEASURED reachability claim, which is why the maintainer decided the finding by FIX rather than by acceptance at UAT (`27-HUMAN-UAT.md` item 10). See the amendment on its Accepted Risks Log row and the **"AR-27-09 — CLOSED BY FIX 2026-08-26"** section for the measured before/after. `AR-27-09` is an `AR-` row and was therefore always outside the `threats_open` population — closing it did not and could not move that counter.**]**

### CORRECTION 2026-08-26 — the enumeration above asked too narrow a question

Added out-of-plan on maintainer authorisation after `27-REVIEW-3.md` CR-01 (BLOCKER). **Nothing above
this heading is edited.** The measurement in it is correct for the question it asked; the defect is
the question.

**The old question:** *"which serialized fields on the MCP emission path are JSON ARRAYS OF STRINGS
whose contents are analyst-authored or response-derived?"* It was answered correctly, and the answer
— zero on this repository's own schema, one model-authored carrier — is what carried `AR-27-11` at
LOW.

**The corrected question:** *"which `Redaction.apply` inputs can carry a header at the OPEN OF A JSON
STRING in ANY spelling `:"` does not recognise?"*

**The general rule the four families follow from, stated once so the list is not read as four
accidents:** `:"` is the two LITERAL characters colon then quote. Any shape that interposes a
character between them — a space, or the backslash of an escaped quote — and any shape with no colon
before the quote at all, is not a recognised start.

**The reviewer's measured differ table, quoted verbatim from its source `27-REVIEW-3.md` CR-01:**

```
BARE     COLONQ    case
MATCH    -         nested escaped json string value open (response body is JSON)
MATCH    -         pretty json, space between colon and quote
MATCH    -         array element                                <- the ONE family that is named
MATCH    -         bare top-level json string
MATCH    MATCH     CONTROL compact object value open            <- positive control, still works
MATCH    -         HTML attribute (must NOT match after narrowing)
```

**Re-measured independently before this correction was written** — same harness discipline as the
section above (compiled classes at `build/classes/kotlin/main` plus `kotlin-stdlib-2.2.21`, JDK 21,
`Redaction.INSTANCE.apply(raw, RedactionPolicy.Companion.fromMode(mode), "round6-probe-salt", false)`,
STRICT and BALANCED identical for every case). The four families reproduced exactly, with three
controls firing in the same run:

```
F1 nested-escaped   {"response":"…{\"cookie_header\":\"Cookie: sess=SECRETNESTED\"}"}   IDENTICAL (survives)
F2 pretty-space     {"notes": "Cookie: sess=SECRETPRETTY"}                              IDENTICAL (survives)
F3 array-element    {"tags":["Cookie: sess=SECRETARRAY"]}                               IDENTICAL (survives)
F3 comma-quote      {"tags":["x","Cookie: sess=SECRETCOMMA"]}                           IDENTICAL (survives)
F4 bare-top-level   "Cookie: sess=SECRETBARE"                                           IDENTICAL (survives)
CTRL compact        {"cookie_header":"Cookie: sess=SECRETCOMPACT"} -> Cookie: [STRIPPED]
CTRL auth compact   {"notes":"X-API-Key: SECRETAUTH1"}            -> X-API-Key: [REDACTED]
CTRL set-cookie     {"notes":"Set-Cookie: s=SECRETSC2; Path=/"}   -> Set-Cookie: [STRIPPED]
AUTH pretty         {"notes": "X-API-Key: SECRETAUTH2"}                                 IDENTICAL (survives)
SC   pretty         {"notes": "Set-Cookie: s=SECRETSC1; Path=/"}                        IDENTICAL (survives)
CTRL html attribute <div title="cookie: analytics">KEEPTAIL</div>                       IDENTICAL (the repair 27-14 bought)
```

The last two survival rows are what make *"all three composed rules lose the same four"* a
measurement: `authHeaderRegex` and `setCookieHeaderRegex` lose the pretty spelling exactly as
`cookieHeaderRegex` does, while their compact twins strip.

### The corrected reachability enumeration, taken at source and MEASURED on the emission shape

| Where | What was found | Bearing on AR-27-11 |
|---|---|---|
| `mcp/schema/Serialization.kt` — `HttpRequestResponse(request, response, notes)` | the whole raw HTTP RESPONSE, **body included**, is emitted as a JSON **string** | a captured body that is itself JSON arrives with its inner quotes ESCAPED (`\"k\":\"…`) — **family 1, on the primary emission path.** A body that pretty-prints is family 2; one returning header lines as an array of strings is family 4 — all three INSIDE the `response` string, none of them a `List<String>` FIELD |
| the same carrier, driven end-to-end | `{"request":"GET /api HTTP/1.1\r\nHost: t.example\r\n\r\n","response":"HTTP/1.1 200 OK\r\nContent-Type: application/json\r\n\r\n{\"cookie_header\":\"Cookie: sess=SECRETNESTED\"}","notes":null}` **IDENTICAL in and out, STRICT and BALANCED**; pretty twin and array twin likewise | the CONTROL — the same carrier with `Set-Cookie: sess=SECRETCTRL; Path=/` on the response's own header block — became `Set-Cookie: [STRIPPED]` in the same run, so the null result is attributable to REACH |
| `McpToolExecutorImpl.kt:1018` — `redact_preview` | arbitrary caller-supplied text, no shape constraint | families 1, 2 and 4 all survive it. **The one tool whose purpose is to answer "would this leak?" now answers "no" for three of the four** |
| `ContextCollector.kt:52-53` | `Redaction.apply` over a target's raw request/response on the PROMPT path | same three families, same Burp-held content, outside the MCP schema entirely |
| `PassiveAiScannerPrompts.kt:49` | the scan-metadata blob | not compact-JSON-shaped by construction; the pretty and bare spellings are reachable in analyst- or target-authored text |
| `McpToolExecutorImpl.routeExternalToolCall` | model-authored `argsJson` (the carrier the old enumeration named) | still true, still UNMEASURED on its remote half — but it is no longer the ONLY carrier, and it was never the most reachable one |

**The honest summary, in the words the corrected severity rests on:** the reachability is MEASURED
NON-ZERO on this repository's OWN primary emission path, with BURP-HELD traffic, in the DEFAULT
posture, under STRICT and BALANCED alike — because the carrier is not a schema FIELD but the CONTENT
of the `response` string this repository copies verbatim from the target. What remains UNMEASURED is
how often a real target returns a body carrying a header-line-shaped string at a string open. That
mixture is why the row moves to **MEDIUM** and stays **open**, rather than to `high` (no live
producer measured) or staying at `low` (its load-bearing sentence — "no measured producer in this
repository puts a value on this shape" — does not apply to a producer that is the target).

**The mitigating bound, checked rather than repeated.** Re-measured in all four families, both modes:
a header that follows an escaped newline is STILL STRIPPED (`{"notes": "GET / HTTP/1.1\r\nCookie:
a=SECRETMIT2\r\n\r\n"}`, the doubly-escaped nested form, `{"requests":["GET / HTTP/1.1\r\nCookie:
a=SECRETMIT3\r\n\r\n"]}` and the bare top-level form all became `Cookie: [STRIPPED]`). **The claim
holds.** One qualification surfaced while checking it and is recorded rather than smoothed: in the
DOUBLY-escaped spelling the strip runs to the end of the enclosing OUTER JSON string —
`{"a":"x\\r\\nCookie: a=SECRETD3\\r\\nY: z","tail":"KEEPTAIL"}` became `{"a":"x\\r\\nCookie:
[STRIPPED]","tail":"KEEPTAIL"}`, losing `\\r\\nY: z` while the sibling field survives. The mechanism
is that `JSON_ESCAPED_HEADER_VALUE`'s escape-pair alternative consumes `\\` atomically, so the tail
never sits at a position where the next two characters are `\` + `r`/`n` and cannot terminate on a
doubly-escaped newline. That is OVER-redaction — fail-safe on the secret, blast radius bounded to the
enclosing string — it belongs to START 2's value tail rather than to this narrowing, it pre-dates
round 5, and it is recorded here for the maintainer's disposition rather than given its own id,
because the scope of this correction is `AR-27-11`'s stated bound.

**What this correction deliberately does NOT do.** It does not re-widen `JSON_STRING_OPEN` — the
narrowing repaired a measured high-severity correctness break and is correct. It does not address
`27-REVIEW-3.md` WR-01 (the measured OVER-match on JS/JSON5/CSS object literals, `{note:"cookie:
…"}` → `note:"cookie: [STRIPPED]"`), which is stated in the row only so `:"` is not read as a JSON
test. It touches no other register row and closes no requirement: **PRIV-05 remains `[ ]` and
`REQUIREMENTS.md` is byte-unchanged.**

---

## Security Audit Trail

| Audit Date | Threats Total | Closed | Open | Run By |
|------------|---------------|--------|------|--------|
| 2026-08-24 | 46 | 46 | 0 | `/gsd-secure-phase 26` (orchestrator, ASVS L1 source verification) |
| 2026-08-24 | 46 | 45 | **1** | `/gsd-audit-milestone` — T-26-02-01 reopened (see below) |
| 2026-08-24 | 46 | 46 | 0 | Phase 27 (27-03) — source re-verification |
| 2026-08-24 | 46 | 46 | 0 | Phase 27 (27-04, 27-05, 27-06) — **second** reopening of T-26-02-01 and its re-closure; `threats_open` COMPUTED from the rows, not asserted |
| 2026-08-25 | 46 | 46 | 0 | Phase 27 (27-07, 27-08, 27-09) — **THIRD** reopening of T-26-02-01 and its re-closure on the COOKIE-typed PARAMETER carrier; `threats_open` recomputed from the rows AND the counter's POPULATION stated for the first time |
| 2026-08-26 | 46 | 46 | 0 | Phase 27 (27-10, 27-11, 27-12, 27-13) — **FOURTH** reopening of T-26-02-01 and its re-closure on the ADMITTER-POLARITY and JSON-STRING-OPEN axes; `threats_open` recomputed from the rows (output `0`, 46 rows scanned, 46 closed) and the counter's POPULATION restated, now with an explicit statement that neither finding opened this round is at or above the blocking severity |
| 2026-08-26 | 46 | 46 | 0 | Phase 27 (27-14, 27-15, 27-16) — **FIFTH** reopening of T-26-02-01 and its re-closure, this time on a CORRECTNESS regression this phase itself shipped (the bare-quote logical-line start) and on a defect of this REGISTER (clause (vi)'s stated bound wider than its control); `threats_open` recomputed from the rows (output `0`, 46 rows scanned, 46 closed), the counter's POPULATION restated in full for the third time with the question it forces answered for `AR-27-11`, and standing-rule clause (vii) added — a residual list must enumerate what the round INTRODUCED, not only what it INHERITED |
| 2026-08-26 | 46 | 46 | 0 | **Out-of-plan correction** (maintainer-authorised, after `27-REVIEW-3.md` CR-01) — no reopening: `T-26-02-01` stays `closed` and no register row was added, amended or reclassified. `AR-27-11` RAISED from LOW to **MEDIUM** and its stated bound corrected from ONE family to the FOUR MEASURED, with the reachability re-derived and measured on the `HttpRequestResponse` emission shape. `threats_open` RECOMPUTED from the rows against the amended file (output `0`, 46 rows scanned, 46 closed); the counter is unmoved because an `AR-` row is outside its population at any severity. PRIV-05 remains `[ ]` |
| 2026-08-26 | 46 | 46 | 0 | **Round-5 gap closure** (after `27-VERIFICATION-5.md`, 28/30) — no reopening: `T-26-02-01` stays `closed` and NO register row was added, amended or reclassified (`AR-27-04`, `AR-27-08`, `AR-27-11` and `T-26-02-01` all md5-identical to `2a880f9`). Two RECORD-DRIFT defects closed, neither a leak and neither a behaviour change. **Gap 1:** standing-rule clause (vi) stated `15 tests` and `returns 14` against a control with 16 and 15 — staled by `fb7cbd3`, the previous round's LAST commit. Re-measured here (16 and 15 at `2a880f9`), the clause amended to **18** and **15**, and both numbers made MACHINE-CHECKED from source, mutation-proved RED at the two stale values. **Gap 2:** `AR-27-11`'s LOW -> MEDIUM / one-family -> four-family correction propagated to all SIX artifacts that cite it, including `27-HUMAN-UAT.md` item 12, whose Option B was corrected from closing "the residual" to closing one family of four. **Standing-rule clause (viii) added** — a correction must fan out to every citing artifact, and a control change must re-measure every record's number about it, by a test where the number is source-derivable. `threats_open` RECOMPUTED from the rows against the amended file (output `0`, 46 rows scanned, 46 closed). PRIV-05 remains `[ ]` and `REQUIREMENTS.md` is md5-identical |

**Note on the count.** 46 register rows across 46 distinct threat IDs. The seven PLAN files declare
52 rows in total, but `T-26-0N-SC` is the same supply-chain threat declared identically in all seven
and is counted once as `T-26-SC`.

**Note on the second reopening, and the interval it makes visible rather than erases.** Row 3 above
restored `threats_open: 0` / `status: verified` on 2026-08-24 on the strength of T-26-02-01. Later
the same day, this phase's own verification (`27-VERIFICATION.md`, verified
`2026-08-24T14:12:50Z`, 7/9 truths) found the PARENT requirement PRIV-05 still refuted on a sibling
path — the raw-message-in-JSON emission shape — and recorded the frontmatter consequence of row 3 as
questionable. **For that interval the frontmatter was an overclaim**, and row 4 exists so the
interval is legible instead of edited away. It was closed later the same day by plans 27-04 (the
cookie rules) and 27-05 (the auth-header rule), and recorded by plan 27-06. The counter on row 4 is
the output of the command quoted in this file's frontmatter, run after the plan 27-06 amendment; the
two open findings this phase carries forward, **AR-27-04** and **AR-27-05**, are MEDIUM and therefore
below the `high` blocking gate — which is why the count is `0` and not because they were dropped.

**Note on the THIRD reopening, and on the counter that did not move.** Row 5 above records the
third time T-26-02-01 has been reopened and re-closed. Waves 4-6 closed the carrier they named and
`27-VERIFICATION-2.md` (2026-08-24, 8/9 truths) still found PRIV-05's "by any path" wording false:
a THIRD carrier of the same cookie bytes — COOKIE-typed HTTP parameters — had never been
enumerated. Closed on that carrier by plans 27-07 and 27-08, recorded here by 27-09; clause (5) of
T-26-02-01 carries the full narrative and its bounds.

**The counter on row 5 is the OUTPUT of the command quoted in this file's frontmatter, re-run
after the clause (5) amendment — 0, over 46 rows, 46 closed — and NOT a number carried across from
row 4.** No register row was added, amended or reclassified by plans 27-07, 27-08 or 27-09, so the
population is unchanged at 46 and the recomputation is a confirmation rather than a formality.

**What is new on this row is the POPULATION statement, and it is the more important half.** The
`awk` command matches rows beginning `| T-26-` only, so every `AR-` finding — the entire Accepted
Risks Log — has always sat OUTSIDE this count, at any severity. That bound was real from the day
the command was written and was never stated, which is the "gate counting the wrong population"
defect in its purest form: a counter can read `0` while an open finding above the blocking
threshold sits one section below it, and nothing in the file would say so. It is now stated in the
frontmatter comment.

**The three findings this round carries forward are AR-27-06 (medium, authored by analogy),
AR-27-07 (low, measured) and AR-27-08 (medium, measured) — all below the `high` blocking gate,
each listed with its severity in the frontmatter comment so the `0` is attributable rather than
merely asserted.** Had any of them landed at `high`, the honest options were to give it a register
ROW inside the population or to amend the command and its comment together — not to leave the
counter reading `0`. **AR-27-08 is the one to watch:** it is the only finding in this series
carrying Burp-held traffic rather than caller-echoed content, it defeats STRICT outright, and it
is `medium` ONLY because it is latent behind three preconditions including an opt-in scanner that
defaults to off. It is owned by Phase 28 in `ROADMAP.md`, together with the unconverted cookie-type
predicate at `scanner/InjectionPointExtractor.kt:29` whose value feeds it.

**PRIV-05 IS NOT SATISFIED BY PHASE 27, and this file does not say otherwise anywhere.** The
parameter carrier is closed and the accessor inventory exists; the issue-detail carrier is open and
owned. The `- [x] **PRIV-05**` tick at `REQUIREMENTS.md:23` is wrong for the third time and remains
the milestone owner's to re-derive from these clauses — `REQUIREMENTS.md` is untouched by this
phase, as it was by 27-03 and 27-06.

---


**Note on the FOURTH reopening, and on the two things that make it different from the first three.**
Row 6 above records the fourth time T-26-02-01 has been reopened and re-closed. Waves 7-9 closed the
carrier they named and `27-VERIFICATION-3.md` (2026-08-26, 12/15 must-haves) still found three truths
false. **The first difference: this round's headline finding was NOT a deferral with an owner — it was
UNRECORDED.** A cookie-header name containing `_` was measured leaking a cookie value to a
third-party AI backend under STRICT and BALANCED, on the PROMPT path, and it appeared in no security
record under `.planning/` at all; it lived in a source comment and in a GREEN TEST whose failure
message told the next engineer not to fix it. **The second difference: the failure mode was new.**
Clause (iv) explains rounds 1 to 3 as a single structural pattern — a rendering-keyed mechanism blind
to the next rendering. Round 4 broke in two ways neither clause (i)-(iv) could have caught: a shared
predicate's WIDTH was verified without asking what each CONSUMER does with a true result, and a GREEN
TEST was allowed to stand in for a MEASUREMENT in three separate places. Clauses (v) and (vi) below
are those two lessons, and they are added together because they were learned together.

**The counter on row 6 is the OUTPUT of the command quoted in this file's frontmatter, re-run after
every other edit of plan 27-13 — 0, over 46 rows, 46 closed — and NOT a number carried across from
row 5.** No register row was added, amended or reclassified by plans 27-10, 27-11, 27-12 or 27-13, so
the population is unchanged at 46 and the recomputation is a confirmation rather than a formality.
The one register row this round TOUCHED, T-26-02-01, gained clause (6) and did not change severity
(`high`) or status (`closed`), so it cannot move the count in either direction.

**THE POPULATION QUESTION WAS ASKED AGAIN, AND ANSWERED EXPLICITLY RATHER THAN LEFT TO INFERENCE.**
The `awk` command matches rows beginning `| T-26-` only, so the entire Accepted Risks Log — every
`AR-` finding, at any severity — sits OUTSIDE this count. **Both findings this round opens are
`AR-` rows, and both are LOW: `AR-27-09` (the leading-whitespace / obs-fold start, measured surviving
under STRICT and BALANCED, bounded by no measured emission site indenting a header line) and
`AR-27-10` (the thirteen RFC 9110 tchars outside the widened cookie name class, derived from a
source-pinned partition, with no leak measured for any of the thirteen).** Neither is at or above the
`high` blocking gate, so **no remedy to the counter or to its definition was required** — the counter
was NOT amended and no finding was given a `T-26-` id. That is stated here, and in the frontmatter
comment, because "below the gate" is a conclusion a reader must be able to CHECK rather than infer,
and because the honest alternative — had either landed at `high` — was to give it a row inside the
population or to amend the command and its comment together, never to leave the counter reading `0`.

**[SUPERSEDED 2026-08-26 by plan 27-17 — `AR-27-09` is CLOSED BY FIX, not open at LOW.** The paragraph above is preserved byte-for-byte as the record made while the finding was open; it is not withdrawn. The LOW it carries rested on an explicitly UNMEASURED reachability claim, which is why the maintainer decided the finding by FIX rather than by acceptance at UAT (`27-HUMAN-UAT.md` item 10). See the amendment on its Accepted Risks Log row and the **"AR-27-09 — CLOSED BY FIX 2026-08-26"** section for the measured before/after. `AR-27-09` is an `AR-` row and was therefore always outside the `threats_open` population — closing it did not and could not move that counter.**]**

**The residuals this phase now carries forward, all six named with owners.** `AR-27-04` (medium,
open, and STILL owed a HUMAN decision — its disposition remains auto-selected by `mode: yolo`, and
plan 27-12 removing its two green STRICT pins supplied no human judgment); `AR-27-08` (medium,
measured, Burp-held traffic, owned by **Phase 28** together with the unconverted cookie-type predicate
at `scanner/InjectionPointExtractor.kt:29`); `AR-27-09` and `AR-27-10` (low, new this round, owned by
the maintainer as dispositions in `27-HUMAN-UAT.md`); the `CONCERNS.md` vendor auth-header class
(open by prohibition — an open-ended vendor list is never complete); and the stated vocabulary bound
of `RedactingPolicySurvivalSweepTest` itself. `AR-27-06` and `AR-27-07` from round 3 are unchanged.
**Six named residuals is NOT a completeness claim**, and no sentence in this file should be read as
one — naming what is known to be open says nothing about what is not yet known.

**[SUPERSEDED 2026-08-26 by plan 27-17 — `AR-27-09` is CLOSED BY FIX, not open at LOW.** The paragraph above is preserved byte-for-byte as the record made while the finding was open; it is not withdrawn. The LOW it carries rested on an explicitly UNMEASURED reachability claim, which is why the maintainer decided the finding by FIX rather than by acceptance at UAT (`27-HUMAN-UAT.md` item 10). See the amendment on its Accepted Risks Log row and the **"AR-27-09 — CLOSED BY FIX 2026-08-26"** section for the measured before/after. `AR-27-09` is an `AR-` row and was therefore always outside the `threats_open` population — closing it did not and could not move that counter.**]**

**PRIV-05 IS NOT SATISFIED BY PHASE 27, and round 4 does not change that.** Three carriers and two
boundary axes are closed; `AR-27-08` is open and owned by Phase 28. `REQUIREMENTS.md` is untouched by
this round, as it was by 27-03, 27-06 and 27-09, and `PRIV-05` correctly reads `[ ]` there — the
milestone owner reverted the tick that clause (5)'s note called wrong for the third time, which is
the outcome that note asked for. Re-deriving it remains the milestone owner's job, from these clauses
rather than from any sentence phase 27 wrote about itself.

---
## Sign-Off

- [x] All threats have a disposition (mitigate / accept / transfer)
- [x] Accepted risks documented in Accepted Risks Log
- [x] `threats_open: 0` confirmed — T-26-02-01 re-closed 2026-08-24 by Phase 27 (27-03)
- [x] `status: verified` set in frontmatter — restored after the Phase 27 closure
- [x] **Post-second-reopening, 2026-08-24 (plan 27-06):** `threats_open` re-derived by the documented
  command in the frontmatter rather than re-asserted — output `0` over 46 rows, 46 closed, and the
  written value equals that output
- [x] **Post-second-reopening, 2026-08-24 (plan 27-06):** the two findings carried forward,
  **AR-27-04** and **AR-27-05**, are recorded as OPEN at MEDIUM severity with quoted measured
  evidence — neither is recorded as closed, moot or theoretical
- [x] **Post-second-reopening, 2026-08-24 (plan 27-06):** **AR-27-01** is no longer described as an
  accepted residual anywhere in this file, and **AR-27-02** is superseded only on the shape where the
  measurement supports it
- [x] **Post-FOURTH-reopening, 2026-08-26 (plan 27-13):** `threats_open` re-derived by the documented
  command in the frontmatter AFTER every other edit of this plan rather than re-asserted — raw output
  `0`, 46 rows scanned, 46 closed, and the written value equals that output
- [x] **Post-fourth-reopening, 2026-08-26 (plan 27-13):** the counter's POPULATION is restated AND the
  question it exists to answer is answered explicitly: **neither finding opened this round is at or
  above the `high` blocking gate** — `AR-27-09` is LOW and `AR-27-10` is LOW — so no remedy to the
  counter or to its definition was required, the command is unamended, and no finding was given a
  `T-26-` id
- [x] **Post-fourth-reopening, 2026-08-26 (plan 27-13):** the two findings opened this round,
  **AR-27-09** and **AR-27-10**, are recorded as OPEN with a severity, a NAMED OWNER and evidence
  QUOTED FROM A ROUND-4 MEASUREMENT — neither is recorded as closed, moot or theoretical, and neither
  is left in a source comment only, which is exactly how the underscore class survived three rounds
- [x] **Post-fourth-reopening, 2026-08-26 (plan 27-13):** the inferred half of `AR-27-10`'s severity is
  LABELLED as inferred, and the row states that **no leak was measured for any of the thirteen
  characters** — the partition and the mechanism are measured; the carry-over is not
- [x] **Post-fourth-reopening, 2026-08-26 (plan 27-13):** clauses (1) through (5) of T-26-02-01 survive
  as an exact CHARACTER prefix of the amended row, measured in characters and explicitly not in bytes
  (`WINDOWS.md` entry 28 records a prior round quoting a byte count as a character count)
- [x] **Post-fourth-reopening, 2026-08-26 (plan 27-13):** **AR-27-04** is NOT relitigated — still OPEN,
  still MEDIUM, still owed a HUMAN decision, its auto-selected provenance intact and NOT upgraded by
  this round; the only change is an APPENDED note recording that its two green STRICT pins were
  deleted and its pass-through re-pointed at a `PrivacyMode.OFF` byte-identity fixture
- [x] **Post-fourth-reopening, 2026-08-26 (plan 27-13):** **PRIV-05 REMAINS UNSATISFIED AND UNTICKED.**
  `REQUIREMENTS.md` is untouched by this round and reads `- [ ] **PRIV-05**`; `AR-27-08` and
  `scanner/InjectionPointExtractor.kt:29` remain owned by Phase 28 and are untouched here. This file
  claims no closure of PRIV-05 anywhere, and a SUMMARY or roadmap sentence that reads as one would be
  the fifth iteration of the pattern these four rounds exist to break

**[SUPERSEDED 2026-08-26 by plan 27-17 — `AR-27-09` is CLOSED BY FIX, not open at LOW.** The paragraph above is preserved byte-for-byte as the record made while the finding was open; it is not withdrawn. The LOW it carries rested on an explicitly UNMEASURED reachability claim, which is why the maintainer decided the finding by FIX rather than by acceptance at UAT (`27-HUMAN-UAT.md` item 10). See the amendment on its Accepted Risks Log row and the **"AR-27-09 — CLOSED BY FIX 2026-08-26"** section for the measured before/after. `AR-27-09` is an `AR-` row and was therefore always outside the `threats_open` population — closing it did not and could not move that counter.**]**

**Approval:** RE-APPROVED 2026-08-24 by Phase 27 (plan 27-03), after the 2026-08-24 withdrawal
recorded in the reopening note below. The re-approval rests on source read in the closing task —
`Redaction.kt:158`, `McpToolHelpers.kt:336`, `PassiveAiScannerFilters.kt:186` — plus both ownership
sweeps returning `0` and a green `CookieHeaderRuleOwnershipTest`, and it is scoped to the two
redaction paths and the passive-scan admitter. It is NOT a re-approval of the L1 pass that produced
the original false close; the standing rule below is what that pass was missing.

### Read-back — 2026-08-24 (plan 27-06, task 3), item by item

Each item below was checked against the SHIPPED SOURCE or against this file's own bytes, in the
closing task, with the check named. **None was confirmed by a maintainer**: the checkpoint that would
have asked for that confirmation was auto-selected by the configured run mode (see the AR-27-04
disposition above). These are executor verifications, and they are recorded at that weight.

| # | Read-back item | Verdict | Check actually run |
|---|----------------|---------|--------------------|
| 1 | T-26-02-01 clauses (1), (2) and (3) are unedited | CONFIRMED | The row is one physical line, so appending clause (4) necessarily rewrites it; the splice asserted that the OLD row body is an exact BYTE PREFIX of the new row (5,633 → 11,320 chars, prefix check PASS). No clause text was altered, reordered or softened. |
| 2 | The `## Reopening` narrative dated 2026-08-24 is unedited | CONFIRMED | `git diff HEAD -- 26-SECURITY.md \| grep -c 'Reopening — 2026-08-24'` returns `0` over the WHOLE diff — the heading appears on no added and no removed line. Clause (4) therefore refers to that section without reproducing its heading string. |
| 3 | Clause (4) says what happened without softening it | CONFIRMED | It states, in its own words, that the parent requirement was still refuted when clause (3) was written; that the leak was the CANONICAL names and strictly broader than the variant spellings; and that the frontmatter consequence drawn on 2026-08-24 was drawn while PRIV-05 was violated elsewhere. |
| 4 | AR-27-01 reads as a reclassified live finding, not as a residual | CONFIRMED | Its Accepted Risks Log row opens "RECLASSIFIED … this was never a genuine accepted residual", names the interval, names the repository's own green test that pinned the leak, and closes it on the raw-message-in-JSON shape only. `grep -c` for the old "accepted residual" framing of AR-27-01 elsewhere in this file: none remains. |
| 5 | `threats_open` matches its quoted computation | CONFIRMED | The documented `awk` command in the frontmatter was re-run against this file after every edit in this plan: output `0`, 46 rows scanned, 46 closed. The written value is `0`. |
| 6 | No sentence in any of the three records claims a scope wider than the serialized emission path, the cookie-header class and the exact-name auth-header class | CONFIRMED | Every scope sentence added here cites the pinned counts (`14` emission sites, `3` composer rules) and names its class. The tree-wide scope phrase this phase is prohibited from using appears `0` times in all three files, and no negated form was substituted for it — the phrase and its inverse are both absent. (Checked with the same grep plan 27-05 recorded; the search string is deliberately not reproduced here, because writing it down is itself a hit.) Both tripwires are described by what they measure — `SerializedEmissionSiteInventoryTest`'s registration bound is stated as WEAKER than its emission bound, and `LogicalLineBoundaryScopeTest` is bounded to the composer's rule set. |
| 7 | AR-27-04 is recorded as OPEN with measured evidence, not as closed, moot or theoretical | CONFIRMED | Recorded OPEN at MEDIUM with the probe output quoted verbatim, the probe re-run in the closing task against the compiled classes rather than copied from a SUMMARY, and its two measured exclusion reasons re-read at source (`Redaction.kt:1810`, `Serialization.kt:80`/`:159`). |
| 8 | The disposition's provenance is not passed off as a human decision | CONFIRMED | Stated in the AR-27-04 disposition in its own bold paragraph, and repeated in `27-06-SUMMARY.md`. |

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

Two clauses, both learned in this file, joined later the same day by a third (see clause (iii)
below, added by plan 27-06 after this same threat was closed wrongly a second time). They bind
every future audit pass in this repository, not only ASVS L1 ones.

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

**(iii) Sibling paths must be ENUMERATED BY MEASUREMENT, and the count recorded.** Added 2026-08-24
by plan 27-06, because clauses (i) and (ii) were both honoured and this threat was still closed
wrongly a second time. A control verified at the site it was written for must also be compared
against every SIBLING PATH that consumes the same downstream weakness — and the set of paths compared
must be produced by a MEASUREMENT whose count is written into the record, never by a prose list a
reader is trusted to have completed. **Worked example: this file's second false close.** Clause (i)
was satisfied — the cookie matcher's WIDTH was compared against its sibling on the prompt path, both
were name-contains-`cookie`, and the sweep in clause (ii) covered five spelling classes. What nobody
enumerated was the set of paths that consult a cookie rule AT ALL. Ten emission sites embedded a raw
HTTP message inside a JSON string, where the line-anchored rules could not fire and no
`sanitizeHeaders` ran in front, so the canonical `Cookie:` and `Set-Cookie:` names leaked while the
register read `closed`. The remedy is mechanical and cheap: the path set is now a measured **14**
(`SerializedEmissionSiteInventoryTest`), the rule set carrying the new boundary is a measured **3**
(`LogicalLineBoundaryScopeTest`), and both counts fail a test when they drift. **An audit that cannot
state the NUMBER of sibling paths it compared, and point at what measured that number, has not
answered the question — it has answered a narrower one and reported the answer at the wider scope.**

**(iv) State whether the control keys on the data's SOURCE or on a RENDERING of it.** Added
2026-08-25 by plan 27-09, because clauses (i), (ii) and (iii) were EACH honoured in the round that
followed them and this threat was still closed wrongly a third time. That is the fact worth
explaining, and the explanation is structural rather than a lapse of care.

**All three prior mechanisms keyed on a RENDERING of the data.** The ownership test of clause (ii)
keys on how a matcher is SPELLED — five enumerated spelling classes. The emission inventory of
clause (iii) keys on how an emission is SHAPED — a measured call shape,
`encodeToString(it.toSerializableForm(…))`. And `Redaction.cookieTypedParamRegex`, the rule written
for the parameter class itself, keys on how a parameter is FORMATTED — the passive scanner's
rendered `name=value (COOKIE)` suffix. **A rendering-keyed mechanism can only see renderings it
already knows.** Each of the three was COMPLETE for its own axis and structurally incapable of
seeing the next: the spelling sweep could not see a shape, the shape inventory could not see a
type, and the format-keyed rule could not see a JSON object whose keys are `type`, `name` and
`value`. Nobody was careless three times; the same class of instrument was reached for three times.

**The property that broke the pattern, recorded in plan 27-07's words because they are the accurate
ones: the control is TYPE-KEYED, not SHAPE-KEYED.** `Redaction.isCookieParameterType`
(`Redaction.kt:335`) reads `HttpParameterType` — a closed enum owned by the host API — and never a
rendered string, so **a change to either MCP output format cannot defeat it**. Compare the failure
mode it replaces: `cookieTypedParamRegex` is silently disabled by changing a separator or a label
in `formatParamLine`, with no compile error and no failing test naming the cause.

**THE OPERATIVE INSTRUCTION.** An audit closing a data-disclosure threat **must state whether its
control keys on the data's SOURCE or on a RENDERING of it**, and **a rendering-keyed control may not
be closed at a width wider than the renderings enumerated**. If the control is rendering-keyed, the
closure sentence names the renderings; if it is source-keyed, the closure sentence names the source
and the accessor set. Either way the reader learns which kind of instrument produced the verdict,
which is the thing none of the three false closes recorded.

**The source-keyed mechanism this round produced, cited WITH its bound — because a clause
presenting it as complete would be clause (iv) committing the very error clause (iv) describes.**
`CookieCarrierInventoryTest` (plan 27-08) enumerates cookie-byte carriers by their SOURCE ACCESSOR
rather than by any rendering: **5** accessors, **72** measured call sites across **11** files, every
one routed through a named control or classified from its own CONSUMER, with NEW and STALE both
diagnosed by name. Its own KDoc states the bound BEFORE anyone can quote it as proof, and names
**four things it cannot see**, carried over here verbatim in substance:

1. **A cookie byte that never passes a Montoya accessor** — operator-pasted text in the chat box, a
   model echoing a cookie back into the transcript, or a cookie this extension itself persisted to
   settings or a cache and later emitted. No accessor-keyed mechanism reaches any of these. **This
   is the NAMED NEXT BLIND AXIS**, and a byte arriving that way would force the primary noun to be
   re-promoted from "a carrier of cookie bytes" to "a value that entered the process", of which an
   accessor-keyed inventory is then one variant.
2. **`bodyToString()`, deliberately excluded** — measured at 32 call sites across 8 files. NOT
   because an entity body cannot contain cookie bytes (it can: a pasted raw HTTP message, a
   forwarded webhook envelope, a proxied upstream request). The reason that HOLDS is that such a
   body passes `Redaction.apply` at the `redactIfNeeded` choke point, where the logical-line cookie
   rules DO fire because it carries a real newline. Two contingencies travel with that reason: the
   exclusion FAILS for a body reaching a backend on a path that bypasses `Redaction.apply` (which is
   axis 1 one step out), and a session token duplicated into a body FIELD carries no header line and
   no newline discriminator, so it is the TOKEN class (PRIV-02) and is not covered here either.
3. **Transitive carriers**, where a cookie byte is copied into a field whose accessor is not on the
   list. `AuditIssue.detail()` is the worked example — that is **AR-27-08**, measured and open. The
   inventory can point at the FIRST hop; it cannot follow a value through arbitrary copies.
4. **A new Montoya accessor** added by a future API version returning cookie data under a name not
   in the accessor set. The set is additive-only and a reader adding one must extend it.

A **fifth, explicitly weaker bound** is stated separately rather than averaged into those four,
because it is a limit of the BOOKKEEPING and not of the axis: a registry key is a `(file, accessor)`
PAIR, not an individual call, so where the calls behind one pair split, the per-call attribution is
human prose inside a machine-checked COUNT. The count is what fails on drift; the prose is what a
reader must re-read when it does.

**Read together with clause (iii): that clause requires an audit to state the NUMBER of sibling
paths it compared. Clause (iv) adds that it must also state what KIND of thing it enumerated — and
that enumerating renderings can never answer a question about sources.**

**(v) A WIDTH CLAIM ABOUT A SHARED PREDICATE MUST STATE EACH CONSUMER'S POLARITY.** Added 2026-08-26
by plan 27-13, because clause (i) — "width, not only presence" — was HONOURED in the round that
closed this threat, the width was compared correctly, and the threat was still closed wrongly a
fourth time. Clause (i) asks how WIDE a control is. It does not ask what the control's callers DO
with a true result, and those are different questions.

**The lesson.** Verifying a shared control's WIDTH is not sufficient without stating what each
consumer DOES with a true result. "Wider" is not a safety property on its own; it is a safety
property only relative to a consumer's polarity. **Wider than the downstream rule is fail-SAFE for a
REDACTOR and fail-OPEN for an ADMITTER, by construction.**

**Worked example, from this file's own history: `Redaction.isCookieHeaderName`.** Clause (3) recorded
that it is the single cookie-header-name rule at exactly three sites, and NAMED all three correctly,
including "the passive-scan admitter". Its KDoc, and the plan prohibitions of this phase, then
asserted that the predicate being deliberately WIDER than the two cookie regexes is fail-safe — "the
cost is over-redacting a benign Cookie-Consent-style header's VALUE". That is TRUE for the two
REDACTING consumers. It is FALSE for the third. At
`PassiveAiScannerFilters.sanitizeHeadersForPrompt` the predicate is an ADMISSION test: a true result
puts the header ONTO the outbound prompt, so a name the predicate CLAIMS but neither regex can MATCH
is admitted and then never stripped. The cost there is not over-redaction — it is a cookie value
reaching a third-party AI backend under STRICT. **The audit that closed the width question never
asked the polarity question**, and the difference set (`_`, a legal RFC 9110 tchar) was measured
leaking for three rounds while a green test pinned it as expected behaviour.

**THE OPERATIVE INSTRUCTION.** A width claim about a shared predicate **must ENUMERATE ITS CONSUMERS
AND LABEL EACH ONE `redactor` or `admitter`**, and the safety direction must be stated PER LABEL
rather than once for the predicate. Wider is fail-safe only for a redactor. For an admitter, wider
than the downstream rule is fail-OPEN by construction, and the correct remedy is to widen the
DOWNSTREAM RULE — never to narrow the predicate, which would shrink what the redacting consumers
strip and reopen the gap one path over. That direction is not a preference: plan 27-10 recorded it as
the maintainer's stated constraint precisely because narrowing `isCookieHeaderName` would have
shrunk `McpToolHelpers.sanitizeHeaders`. **This clause is now enforced where a reader meets the
predicate rather than only here:** the `isCookieHeaderName` KDoc and the admitting call site each
name the consumers and their polarity, and `27-10-SUMMARY.md` records that whether those two
passages actually SAY the true thing is a reading judgment a maintainer must make — a grep can only
prove the symbols are present.

**(vi) A GREEN TEST IS NOT A MEASUREMENT, AND A GREEN TEST ASSERTING THAT A SENSITIVE VALUE SURVIVES
A REDACTING POLICY IS A DEFECT AND NOT COVERAGE.** Added 2026-08-26 by plan 27-13, alongside clause
(v), because round 4 broke in two ways and this is the second.

**The lesson.** A test asserts what its author believed. A measurement establishes what the code
does. When the belief is wrong, a green test does not merely fail to catch the defect — **it
ARGUES FOR IT**, and it argues to every future reader, including the audit that would otherwise have
found it. The special case that must never appear in this repository is an assertion that a sensitive
value SURVIVES a redacting policy: it converts a leak into an expectation, and it makes the suite's
greenness evidence for the very thing the suite exists to prevent.

**Worked example, from this file's own history.** **Every blocker in round 3 was GREEN because a test
asserted it.** `CookieHeaderNameParityTest.thePredicateIsDeliberatelyWiderThanTheTwoRegexes` asserted
`output.contains(sentinel)` for `my_cookie` under STRICT and BALANCED — and carried a FAILURE MESSAGE
INSTRUCTING THE NEXT ENGINEER NOT TO FIX IT ("record the measurement, do not narrow the predicate to
restore symmetry"). Two `assertTrue(… .contains("api.example.com"))` assertions under STRICT pinned
`AR-27-04`'s host residual, committed by this phase in `09e9cae` against plan 27-05's own
high-severity prohibition. And plan 27-08's must-have — "no green test asserting that a cookie value
SURVIVES a redacting policy is committed anywhere under `src/`" — was authored in wave 8 and falsified
by artifacts already committed in wave 2 and wave 4, because the must-have was verified by a search
narrower than the claim it made. That last part is clause (ii) recurring inside clause (vi)'s subject
matter.

**THE OPERATIVE INSTRUCTION.** A survival MAY be RECORDED here, with quoted evidence from a probe
driven against the compiled classes. Its pass-through MAY be asserted under `PrivacyMode.OFF`, where
pass-through is the correct behaviour — and preferably as an `assertEquals` byte-identity assertion,
which is a stronger claim AND names no sensitive value inside an `assertTrue`. **It may NOT be pinned
green under a redacting policy.** Where such a pin already exists, DELETE it or INVERT it — invert
when the corrected behaviour ships (plan 27-10 inverted the `my_cookie` pin, so the corpus entry
keeps carrying its measurement), delete when it does not (plan 27-12 deleted the two host pins,
because inverting them would assert a behaviour that does not ship and turn the suite red for a
finding it was prohibited from fixing).

**THE MACHINE CHECK THAT NOW ENFORCES THIS, AND — IN THE SAME CLAUSE, BECAUSE THIS FILE HAS BEEN
BURNED BY EXACTLY THIS OMISSION — ITS STATED BOUND.** `RedactingPolicySurvivalSweepTest` (plan 27-12,
extended by plan 27-15 and by the round-5 gap closure, **18 tests**) replaces plan 27-08's prose
must-have with a scan of
`src/test/kotlin`. It reports an EMPTY hit set with an EMPTY `ALLOWLIST` on the tree as shipped, it
scans its OWN file with NO self-file exclusion and comes out clean, and that zero is FALSIFIABLE —
the same detector over the same file without the raw-string skip returns **15** (plan 27-12 measured
and wrote **5**; plan 27-15 measured and wrote **14** — six declaration-shape pins, two composition
halves, one unbalanced-file pin — and the fifteenth is `fb7cbd3`'s trailing-comment fixture; the
numbers are restated at what they now ARE rather than left to go quietly stale).

**BOTH NUMBERS IN THE PARAGRAPH ABOVE ARE NOW MACHINE-CHECKED TOO, and this sentence exists because
they were not, and both went stale inside the round that wrote the check beside them.** Amended
2026-08-26 by the round-5 gap closure, after `27-VERIFICATION-5.md` gap 1. **What happened, recorded
at full width because softening it would waste the only thing it bought.** At the end of plan 27-15
this paragraph read **15 tests** and **returns 14**, and both were true. The round's LAST commit,
`fb7cbd3` — an out-of-plan WR-02 fix — added a sixteenth `@Test` and a new raw-string fixture, listed
exactly ONE file in `git show --name-only`, and amended no record. From that commit onward this
clause stated **15** where the control had **16**, and **14** where it returned **15**. **That is a
stated bound diverging from its control, in the clause written to prohibit exactly that, whose own
worked example is exactly that, committed for the second time.** `fb7cbd3`'s commit message reasons
about the thirteen-axis count — "unchanged because this is a fix, not a deferral" — and never asks
the same question of the two numbers beside it. **The number that was checked did not drift; both
numbers that were not, did, inside the same round.** The remedy is the one round 5 already applied
once successfully: `theStatedTestMethodCountMatchesThisFilesOwnDeclarations` counts anchored `@Test`
declarations over the sweep's own `fileWalk` output (so a `@Test` inside a raw-string FIXTURE is not
counted as a method, and a walk that starts blanking real code takes it red too) and compares against
`STATED_TEST_METHODS`; `theStatedUnskippedSelfHitCountMatchesThisFile` pins the unskipped self-hit
count with `assertEquals` against `STATED_UNSKIPPED_SELF_HITS` — the pre-existing
`MIN_EXPECTED_UNSKIPPED_SELF_HITS` FLOOR is kept and is not that check, because a floor catches a
disarmed detector and cannot catch a moved count. Both were mutation-proved against the two stale
values this clause carried: set to **15** and **14** they go RED with the measured **18** and **15**
in the failure text. **All three numbers this clause cites can now go stale only if a test goes red
first**, which is the property none of them had when the clause was written and only one of them had
after round 5. The 18 is the 16 measured at `2a880f9` plus those two new checks.

**IT IS DEMONSTRATED TO FIRE ON THE REAL THING, not merely on invented fixtures.** The committed
`detect()` / `fileWalk()` functions were pointed at the PRE-ROUND contents of the two files
(`git show 09e9cae:<path>`, a SHA confirmed by content rather than trusted) and reported **EXACTLY 3
hits** — the two host pins plus the underscore pin, the three real artifacts round 4 removed. That
run was performed twice, before and after a detekt-driven refactor of the detector, and produced
byte-identical output, which is what makes the refactor demonstrably behaviour-preserving rather than
assumed to be. **That tree run is EXECUTION-TIME EVIDENCE and is NOT re-run by CI**; the durable
check is the in-file fixtures.

**THE VOCABULARY BOUND, stated because a clause presenting this check as complete would be clause
(vi) committing the error clause (vi) describes.** It is a **TRIPWIRE OVER A MEASURED VOCABULARY, NOT
A PROOF OF COVERAGE.** Its own KDoc names **THIRTEEN** things it cannot see before its first
assertion — and that number is now **MACHINE-CHECKED against the enumeration it describes**, not
transcribed: `theStatedBlindAxisCountMatchesTheEnumeration` reads the sweep's own source at test
time, isolates the class-KDoc region, counts the numbered axis entries in it and fails if the count
and the stated constant `STATED_BLIND_AXES` disagree. **The number cited in this clause can therefore
go stale only if a test goes red first.**

**WORKED EXAMPLE, AND IT IS THIS CLAUSE'S OWN — recorded here because clause (vi) committed the error
clause (vi) describes, and softening that would waste the only expensive thing this round bought.**
Until 2026-08-26 this paragraph read **ELEVEN**, transcribed by hand from the sweep's KDoc. Both were
false when written. The sweep's `FUNCTION_DECLARATION` admitted one optional modifier and a
word-character name, and `detect()` returns early on a non-matching declaration line, so an unmatched
declaration hid its ENTIRE body: **MEASURED, 133 of 1781 declaration lines under `src/test/kotlin`
were invisible** — **136 of 1784** on the wider population `27-REVIEW-2` CR-01 counted, the 3-line
difference being extension-receiver declarations such as `private fun String.indentWidth()` —
**67 of them backtick-named `@Test` methods across 9 files**, one of them in the redaction package
itself, and a synthetic survival pin scored **1 of 6** declaration shapes. So this register cited an
eleven-axis bound as the check's STATED BOUND while a twelfth axis was live, undeclared, and covered
3.8% of the repository's existing test methods and the idiom a future author is most likely to reach
for. That is a stated bound wider than its control, in the clause written to prohibit exactly that,
citing a control written to prohibit exactly that. Plan 27-15 closed the declaration axis rather than
enumerating it (the axis is gone, so it is correctly absent from the thirteen), named the two axes
that remain — the declaration line whose opening parenthesis is not on it, and the compound-assertion
negation over-fire — and amended this paragraph in the SAME change, which is the discipline whose
absence produced the defect.

Three of the thirteen are the price of its three constructed exclusions — each of which is a code
path in the detector, none an `ALLOWLIST` key, each carrying the RE-MEASURED count it accounts for
(re-measured 2026-08-26 against the tree with plans 27-14 and 27-15 landed, and with the WIDENED
declaration gate in place):
`BENIGN_ACCESSORS` (exactly one key, `Sentinel.BENIGN_CONTROL`, accounting for **7** live functions,
all in `SerializedEmissionRedactionTest` — **the plan projected 5; 7 is what was MEASURED**, the two
extra being plan 27-11's JSON-string-open probes, which each carry a benign-control assertion and
which landed in this sweep's base between the plan being written and its execution; **re-measured
after plan 27-14, which added tests but no eighth benign-control function: still 7**) — **its cost: a
genuinely sensitive value reached through that one accessor is invisible**; the POSITION RULE (**1** —
a pre-redaction fixture guard) — **its cost: a pin positioned textually ABOVE the policy marker is
invisible**; and the NEGATION RULE (**1**) — **its cost, named for the first time in round 5: the
rule computes negation from the `assertTrue(` opener to the containment under test, so in a compound
assertion whose FIRST operand is negated every later containment inherits that negation and a real
pin combined with a negated noise check is invisible. The fix is written down in the sweep's axis 10
and DELIBERATELY not applied this round.** 7 + 1 + 1 = **9**, exactly the unqualified vocabulary count
on this tree — **the plan projected 7; 9 is what was MEASURED** — and 9 − 9 = 0, the qualified count.

**The arithmetic is unchanged BY the widening, which is the load-bearing half of the re-measurement.**
Plan 27-15 widened the declaration gate to 133 more declaration lines and re-ran the detector: 9 / 7 /
1 / 1 / 0 before, 9 / 7 / 1 / 1 / 0 after, and the pre-round historical run still reports **EXACTLY 3**
hits under the same three identifiers. The widening bought scope without buying noise, and **nothing
was narrowed to keep the hit set empty**: no vocabulary entry was narrowed, no `ALLOWLIST` key was
added, `BENIGN_ACCESSORS` still holds exactly one key, and no self-file exclusion exists. One number
is restated to stop a foreseeable misreading of the 9: the RAW occurrence count over the same
population is **36**, of which **27** are `assertFalse` containments that are not candidates under any
reading — the `assertTrue` requirement is not one of the three exclusions, and folding those 27 into
the 9 would overstate the exclusions' cost fourfold. The numbers written into the KDoc are the
measured ones, with the projection and the reason for the gap recorded beside them. **A stated bound
that does not match the control it describes is the exact defect this phase exists to repair, and
copying `5` forward would have been that defect one iteration smaller.**

**One further property of that check is worth carrying here, because it is the loud-versus-silent
distinction this whole file turns on.** The sweep's first self-scan found a REAL bug in the sweep:
its raw-string skip toggled triple-quote state on EVERY line, comments included, and the class KDoc
quotes a bare triple quote while explaining the walk — an ODD toggle that inverted the skip for
every line below it. It surfaced LOUDLY, as five self-hits and a red test. **The dangerous direction
is the other one:** an odd toggle anywhere in a scanned file's prose can blank REAL code and make the
tree scan miss a real survival pin SILENTLY, with every test still green. The rule now consults
`isCommentOnly` before scanning a line for triple quotes, and **the KDoc's triple quote was
DELIBERATELY LEFT IN PLACE** — removing it would make the new rule vacuous, because nothing else in
the file exercises it. The file is now its own regression fixture, and the reason is written in the
comment beside the rule.

**AMENDED 2026-08-26 (plan 27-15), because the paragraph above NAMED that dangerous direction and
then did not assert against it for a full round.** Independently found by `27-REVIEW-2` CR-02 and
`27-VERIFICATION-4` gap 3: every proof that the sweep's detector could produce a hit BYPASSED the
file walk, and the only path that used the walk expected an EMPTY result — so the composition
`fileWalk` → `detect` had no positive gate at all, and "the walk has started blanking real code"
was a failure that shipped GREEN. Round 5 closed it at both ends. (1) A composition fixture — a
raw-string block that must be blanked, followed by a real-code pin that must survive — asserts
EXACTLY ONE hit and that it is the real-code half. Measured in all three directions: **1** on the
shipped walk, **2** with the skip neutralised to a pass-through, **0** with it neutralised to
blank-everything; in that last run **it was the only failing test in the class, with the other 13
still green**, which is precisely the silently-vacuous pass it exists to stop. (2) The walk now
raises an `AssertionError` NAMING the source when a scanned file ENDS INSIDE a raw string, instead of
returning a silently blanked tail. Re-measured with that check live: **all 151 files walk without
throwing**, so 0 files end INSIDE — established by the tree scan itself now, not by a one-off probe —
and **652 lines are blanked tree-wide** (352 of them in the sweep file's own fixtures, 300 elsewhere;
`27-REVIEW-2` recorded 625 from an independent re-implementation on the round-4 tree, and the two are
**not reconciled** — the tree has since gained plan 27-14's tests and this plan's three fixtures, and
the direction that matters, 0 files ending INSIDE, agrees in both).

**WHAT ROUND 5 CHANGED ABOUT THE CHECK ITSELF — 2026-08-26, plan 27-15, three changes, each measured
in both directions.** (1) The DECLARATION GATE widened: `FUNCTION_DECLARATION` now admits any modifier
prefix, an optional same-line annotation, an optional generic parameter list and BOTH name spellings,
and `detect()` takes the identifier from the plain-name group falling back to the backtick group. A
synthetic survival pin in six declaration shapes scored **1 of 6 before and 6 of 6 after**. (2) The
WALK-TO-DETECTOR COMPOSITION gated in the FLAGGING direction for the first time — **1 / 2 / 0** across
the shipped walk and its two neutralisations. (3) The UNBALANCED-FILE blindness converted from a
silently blanked tail into a NAMED `AssertionError`. Regression evidence, quoted because the widening
is the change most able to break the check quietly: the pre-round historical run still reports
**EXACTLY 3** hits under the same three identifiers, and the current tree still reports **0**
qualified over **151** files. **Round 5 closed NO requirement — PRIV-05 remains `[ ]`** — and this
clause is amended in the SAME change as the control it describes, which is the whole of the lesson.

**Read clauses (v) and (vi) together with (i) through (iv): (i) asks how wide a control is, (ii) that
the verification not be narrower than the claim, (iii) that the sibling paths be counted, (iv) that
the KIND of key be named — SOURCE or RENDERING. (v) adds that a width answer is meaningless without
each consumer's POLARITY. (vi) adds that none of the five may be answered by pointing at a green
test.**

**(vii) A RESIDUAL LIST MUST ENUMERATE WHAT THE ROUND INTRODUCED, NOT ONLY WHAT IT INHERITED, AND
THE TWO MUST BE VISIBLY SEPARATED.** Added 2026-08-26 by plan 27-16, because clauses (i) through (vi)
were each honoured in the round that followed them and this threat was still closed wrongly a fifth
time — and because the fifth failure was not in any control this register describes. It was in the
register's own account of what remained.

**The lesson.** A round's INHERITED residuals are the ones it has already thought about, which is
exactly why they are the ones it lists: they arrived before the change, they were weighed while the
change was being planned, and naming them costs nothing but a sentence. The residuals a round CREATES
are invisible to it for the same structural reason the defect was — they arrive WITH the change, not
before it, and the author's attention at that moment is on what the change FIXES. A list of inherited
residuals therefore READS as completeness while being systematically blind in precisely the direction
the round is most likely to be wrong. This is not carelessness and it cannot be fixed by care; the
remedy is a required section heading, which is what this clause installs.

**Worked example: round 4's own residual list, quoted rather than paraphrased.** It named SIX
residuals — `AR-27-09`, `AR-27-10`, `AR-27-04`, `AR-27-08`, the `CONCERNS.md` vendor auth-header
class, and the sweep's own vocabulary bound — and closed with the sentence "SIX NAMED RESIDUALS IS
NOT A COMPLETENESS CLAIM." Every one of the six is REAL, correctly severity-assigned and correctly
owned; the verifier re-measured two of them independently and both held exactly as recorded
(`AR-27-09`'s indented header survives byte-unchanged under STRICT *and* BALANCED; all thirteen of
`AR-27-10`'s tchars are admitted and leak under STRICT). **The failure is not in what the list names.
It is that all six were INHERITED, and the two round 4 CREATED appear in none of them:** (a) the
bare-quote logical-line start, a shipped correctness regression that destroyed 1589 of 1714
characters of a realistic tool result and appeared in no source comment, no summary and no security
record, gated by no test; and (b) the sweep's declaration-shape blindness, which this register
affirmatively MISSTATED, because clause (vi) cited the sweep's eleven-axis enumeration as the check's
STATED BOUND while a twelfth axis was live and undeclared. **A round whose central lesson was "a
stated bound wider than its control is the defect" closed with a stated bound wider than its control
in two places** — and the self-aware disclaimer at the end of its own list did not help, because a
disclaimer about unknown unknowns says nothing about a residual the round itself manufactured and
could have named.

**[SUPERSEDED 2026-08-26 by plan 27-17 — `AR-27-09` is CLOSED BY FIX, not open at LOW.** The paragraph above is preserved byte-for-byte as the record made while the finding was open; it is not withdrawn. The LOW it carries rested on an explicitly UNMEASURED reachability claim, which is why the maintainer decided the finding by FIX rather than by acceptance at UAT (`27-HUMAN-UAT.md` item 10). See the amendment on its Accepted Risks Log row and the **"AR-27-09 — CLOSED BY FIX 2026-08-26"** section for the measured before/after. `AR-27-09` is an `AR-` row and was therefore always outside the `threats_open` population — closing it did not and could not move that counter.**]**

**THE OPERATIVE INSTRUCTION.** A residual list closing a round **must carry two separately headed
groups: what this round INTRODUCED, and what it INHERITED.** The INTRODUCED group is written FIRST,
because it is the one the author is structurally least likely to produce. It is populated by a
specific question asked of every change the round shipped — *what does this change now fail to see,
or newly do, that the previous state did not?* — and each entry carries the same apparatus an
inherited entry does: a measurement, a severity with its provenance, and a named owner. An empty
INTRODUCED group is permitted, but only as an explicit sentence saying the question was asked and
the answer was none. **A list with no INTRODUCED heading at all is not a short list; it is an
unanswered question presented as an answer.**

**THIS CLAUSE IS APPLIED TO THE ROUND THAT WROTE IT, because a rule whose first application is to
some future round is a rule that has not been tested.** Round 5's own residuals, in the two groups
this clause now requires:

*Residuals ROUND 5 INTRODUCED.* **(1) `AR-27-11`** — the JSON-ARRAY-ELEMENT logical-line start,
created by plan 27-14's narrowing of `JSON_STRING_OPEN` to a colon-quote sequence. OPEN at LOW,
MEASURED in both columns, with its reachability measured rather than assumed and its unmeasured half
(the remote tool schemas on the external-tool args path) labelled UNMEASURED. **Owner: the
maintainer**, item 12 of `27-HUMAN-UAT.md`. **(2) The sweep's AXIS 9** — a declaration whose opening
parenthesis does not follow the identifier on its line, created by plan 27-15's widening of
`FUNCTION_DECLARATION`, which also requires that parenthesis. **3** extension-receiver declarations
measured live on this tree, one of them inside the sweep file itself, and **0** multi-line signatures
— the plan anticipated the multi-line shape and the measurement found the other one, so the axis
names BOTH shapes with BOTH counts. **Owner: the sweep's own KDoc**, where it is enumerated inside
the machine-checked `STATED_BLIND_AXES = 13`. **(3) A RED `detekt` GATE** — plan 27-15's three new
raw-string fixtures each tripped `MayBeConst`, and neither 27-15's own verification command nor the
wave-9 post-merge gate ran `detekt`, so a red gate was merged unseen. FIXED by plan 27-16 (three
`const val`s; no behaviour change, no `detekt-baseline.xml` growth). **(4) A RED
`jacocoTestCoverageVerification` GATE** — the `redact` package's BRANCH ratio is **0.9278** against a
**0.930** floor. Bisected rather than assumed: the pre-round-5 tree passes at **0.9330** (13 missed /
116 covered), the round-5 base and the final tree fail at **0.9278** (14 / 115), and exactly ONE
branch flipped — `if (remainingMs <= 0L)` at `Redaction.kt:1628`, the WALL-CLOCK budget-exhaustion
guard on the same `SafeRegex` 50 ms deadline path as the documented `RedactionTest` flake. Covered in
1 of 1 pre-round-5 runs and missed in 2 of 2 round-5 runs; **whether the cause is 27-14's narrowing
making the composed regexes cheap enough that the deadline stops firing incidentally, or ambient CPU
load, is NOT established by three samples and is NOT claimed.** The floor has ONE branch of headroom
either way, so it is partly met by a timing-dependent branch. **DELIBERATELY NOT FIXED by a records
plan:** the honest options are a deterministic test for that branch, or lowering a QUAL-06 floor —
and lowering a floor to turn a red gate green is the laundering this register exists to stop.
**Owner: the maintainer.** **Entries (3) and (4) are on this list ONLY because plan 27-16's
acceptance criteria required `./gradlew check` to be run at all, where the two waves before it gated
on `ktlintCheck test`. A residual list whose contents depend on which gate a plan happened to run is
this clause's own blindness, observed one level up — and it is recorded rather than smoothed,
because the round that wrote clause (vii) does not get to be the exception to it.**

*Correction to the INTRODUCED list, 2026-08-26 (out-of-plan, maintainer-authorised), recorded HERE
because clause (vii) says a round's INTRODUCED residuals are the round's own to state and this is a
round-5 event.* **Entry (1) above was itself an instance of the defect clause (vii) was written to
stop, one level in.** It named `AR-27-11` as "the JSON-ARRAY-ELEMENT logical-line start" — ONE family
— and carried it at LOW. `27-REVIEW-3.md` CR-01 measured **FOUR** families, and the measurement was
reproduced independently before this correction was applied: a NESTED / ESCAPED string value open
(`\"k\":\"Cookie: …`), PRETTY-PRINTED JSON (`"k": "Cookie: …`), a BARE top-level JSON string, and the
ARRAY ELEMENT already named. **Entry (1) is therefore amended to read: `AR-27-11` — the JSON-STRING-OPEN
logical-line start in every spelling `:"` does not recognise, FOUR MEASURED families under one id,
OPEN at MEDIUM** (raised from LOW; the re-derivation, the emission-shape measurement and the
mitigating bound are in the `AR-27-11` row and in the correction section above, both APPENDED with
the superseded text left byte-unchanged). **Owner unchanged: the maintainer, item 12 of
`27-HUMAN-UAT.md`.** Entries (2), (3) and (4) are untouched and unaffected. **The lesson this
correction adds to the clause, since the clause's own worked example is now one iteration longer:**
round 5 DID produce an INTRODUCED heading and DID put its own new residual under it — clause (vii)
worked as written — and the entry was still wrong, because a residual can be *named* at the right id
and *bounded* at the wrong width. **Naming a residual is not the same act as measuring it.** A round
that files what it introduced still owes the same question of that entry that clause (i) asks of a
control: *how WIDE is it, and was that width measured or assumed?* Here it was assumed from a single
example — the array shape the probe happened to use — and one probe is a witness, never a bound.

*Residuals ROUND 5 INHERITED.* `AR-27-04` (MEDIUM, open, **still owed a HUMAN decision** and
deliberately NOT relitigated by round 5); `AR-27-08` and `InjectionPointExtractor.kt:29` (owned by
Phase 28, untouched); `AR-27-09` (LOW, open, one-token fix written down); `AR-27-10` (LOW, open,
partition measured and carry-over labelled inferred); the `CONCERNS.md` vendor auth-header class
(open by prohibition); and the sweep's vocabulary bound, now stated as **THIRTEEN** machine-checked
axes rather than eleven transcribed ones. Two of round 4's six are CLOSED by round 5 — its
declaration-shape blindness and (as a defect rather than a residual) the bare-quote start — and the
list says which, rather than letting a shrinking count imply progress it did not make.

**[SUPERSEDED 2026-08-26 by plan 27-17 — `AR-27-09` is CLOSED BY FIX, not open at LOW.** The paragraph above is preserved byte-for-byte as the record made while the finding was open; it is not withdrawn. The LOW it carries rested on an explicitly UNMEASURED reachability claim, which is why the maintainer decided the finding by FIX rather than by acceptance at UAT (`27-HUMAN-UAT.md` item 10). See the amendment on its Accepted Risks Log row and the **"AR-27-09 — CLOSED BY FIX 2026-08-26"** section for the measured before/after. `AR-27-09` is an `AR-` row and was therefore always outside the `threats_open` population — closing it did not and could not move that counter.**]**

*One residual round 5 NAMED and deliberately did not fix,* recorded here because it belongs to
neither group cleanly and hiding it in either would be this clause's own error: the
compound-assertion NEGATION OVER-FIRE in `assertsPresenceAt` (the sweep's axis 10). Its fix is
written down; applying it without a flip-pair fixture is how a detector gets quietly disarmed, which
is the failure `theBenignExclusionCannotSwallowARealSentinel` exists to prevent for the other
exclusion.

**Read clause (vii) with (i) through (vi): those six are all about the CLAIM a round makes. This one
is about the LIST a round leaves behind — and it says that a list of what a round inherited, however
accurate, answers a narrower question than the one a reader will take it for.**

**(viii) A RECORD IS NOT WRITTEN ONCE. WHEN A FINDING IS CORRECTED, THE CORRECTION MUST REACH EVERY
ARTIFACT THAT CITES IT; WHEN A COMMIT CHANGES A CONTROL, EVERY RECORD STATING A NUMBER ABOUT THAT
CONTROL MUST BE RE-MEASURED IN THE SAME CHANGE — AND WHERE THE NUMBER CAN BE READ FROM SOURCE, BY A
TEST RATHER THAN BY CARE.** Added 2026-08-26 by the round-5 gap closure, after `27-VERIFICATION-5.md`
found round 5 had closed with 28 of 30 truths verified and BOTH remaining gaps in this shape.

**Why this is a new clause and not a sentence inside (vii).** Clause (vii) worked exactly as written.
Round 5 DID produce an INTRODUCED heading, DID put its own new residual under it, and DID name the
owner. Both of round 5's gaps happened AFTER that, to filings that were correct when they were made.
Clauses (i) through (vii) are all about the moment a claim is authored. **This one is about every
moment after it** — and the two ways a record that was true on Tuesday is false on Wednesday without
anybody editing it.

**THE LESSON, FIRST HALF — a severity correction is not done when the register is amended.** A
finding's severity and bound are quoted OUTWARD: into a roadmap's residual list, into a codebase
concerns entry, into a source comment, into an assertion's failure message, and — the one that has a
consequence — into the human decision item where a maintainer will actually weigh it. Amending the
register corrects the SOURCE of the claim and leaves every COPY of it standing. Those copies are not
duplicates of the register; they are the places the claim is USED, and the decision venue is the
place it is used to decide something.

**Worked example: `AR-27-11`, quoted rather than paraphrased.** `2ed1a12` raised it from LOW over one
family to MEDIUM over four, and the correction was exemplary where it landed: byte-exact prefix
preservation, an explicit `CORRECTION` marker, all four families independently re-measured, the
reachability re-derived on the emission shape. `git show --name-only 2ed1a12` lists THREE files.
**Six artifacts cited the finding.** `.planning/ROADMAP.md`'s round-5 INTRODUCED entry (1) still read
`OPEN at LOW` with no marker at all. `.planning/codebase/CONCERNS.md` AMENDMENT 5 item (3) still read
`OPEN at LOW` — and that entry OWNS `authHeaderRegex`, one of the three composed rules that loses all
four families, so it was not a neighbouring concern. Two plan SUMMARYs still recorded the LOW as a key
decision. **And `27-HUMAN-UAT.md` item 12 — which THIS FILE names by name, in the row's own OWNER
field — was worse than stale, because it is a DECISION document and it presented the superseded
reasoning as the case for the decision:** it argued acceptance from the `List<String>`-fields
enumeration the same round had measured to be the wrong question, and its OPTION B claimed to close
"the residual at the control" while in fact closing ONE family of four. **The maintainer was being
offered a binary between accepting at the wrong severity and a fix worth a quarter of what it
claimed.** None of the six was found by the correction. All six were found by the next verifier, and
the sixth only by grepping the finding id.

**THE LESSON, SECOND HALF — a commit that changes a control has changed every record that counts it.**
A record's numbers are measurements of a control at a moment. The commit that moves the control is
the only commit that knows the number moved, and it is also the commit whose author's attention is
entirely on the fix. Care does not survive this. **A number that can be read from source must be read
from source by a test**, so that the failure arrives as a red build in the commit that caused it
rather than as a false sentence discovered a round later.

**Worked example, and it is clause (vi)'s, again.** Clause (vi) cites its control with three numbers.
Round 5 made ONE of them machine-checked — `STATED_BLIND_AXES`, because that one had already gone
stale once — and left the two beside it as prose. `fb7cbd3`, **the last commit of that same round**,
added a sixteenth `@Test` and a raw-string fixture, listed exactly ONE file, and amended no record.
From that commit the clause said **15 tests** where the control had **16**, and **returns 14** where
it returned **15**. `fb7cbd3`'s own message reasons about the machine-checked number — "the
thirteen-axis enumeration is unchanged because this is a fix, not a deferral" — and never asks the
same question of the two beside it. **The number that was checked did not drift. Both numbers that
were not, did, inside one round, in the clause written to prohibit exactly that, whose own worked
example is exactly that.** That is as clean a controlled experiment as this register is ever going to
get, and it is the whole argument for the operative instruction below.

**THE OPERATIVE INSTRUCTION, two parts.**

**(a) CORRECTION FAN-OUT.** A change that alters a finding's SEVERITY, its stated BOUND, or its
REACHABILITY must, IN THE SAME CHANGE, amend every artifact that cites that finding. The list is not
guesswork and it is not the reviewer's to supply: **`grep -rn '<finding-id>'` over the repository is
the list**, and the register's own OWNER field names the decision venue explicitly. Each cited
artifact gets a dated marker; superseded text is MARKED, never deleted, on this file's existing
byte-exact-prefix discipline. **A correction commit that touches only the register is an INCOMPLETE
correction, and the artifact it is most likely to have missed is the one where a human decides.**

**(b) COUNT RE-MEASUREMENT, and its durable form.** A commit that changes a control must re-measure
every number a record states about that control, in the same change — and where the number is
derivable from source, the commit must instead make a TEST derive it. A prose number is a promise
about future diligence; a source-read assertion is a gate. **Preferring the gate is not
belt-and-braces: it is the only version of this instruction that has been observed to work here.**
Corollary, because it was the actual failure: **an out-of-plan fix is not exempt.** Both of round 5's
gaps were introduced by out-of-plan commits that ran the test suite, passed it, and touched no record
— which is precisely the state a machine check converts into a red build.

**THIS CLAUSE IS APPLIED TO THE CHANGE THAT WROTE IT**, for the same reason clause (vii) was. Under
(a): the `AR-27-11` correction was propagated to all six citing artifacts in one change —
`27-HUMAN-UAT.md` item 12 (SUPERSEDED banner plus a restatement at MEDIUM with Option B corrected and
an Option C added), `ROADMAP.md` entry (1), `CONCERNS.md` AMENDMENT 6, the two assertion failure
messages in `LogicalLineBoundaryScopeTest.kt`, and appended notes on `27-14-SUMMARY.md` and
`27-16-SUMMARY.md`. PLAN files were deliberately left alone: a plan records intent BEFORE execution,
and rewriting one backwards is a different defect. Under (b): clause (vi)'s two prose numbers are
amended to **18** and **15** and are now read from source by
`theStatedTestMethodCountMatchesThisFilesOwnDeclarations` and
`theStatedUnskippedSelfHitCountMatchesThisFile`, both mutation-proved RED against the two stale values
this clause's own worked example describes.

**Read clause (viii) with (i) through (vii): (i)-(vi) govern the CLAIM a round makes, (vii) governs
the LIST it leaves behind, and this one governs the DECAY of both. It says that the moment a record
is written is not the last moment it can become false, and that the two ways it does — a correction
that does not fan out, and a control change that outruns the numbers describing it — are structural
rather than careless, so the remedy is a required sweep and a machine check rather than an
instruction to be careful.**

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
