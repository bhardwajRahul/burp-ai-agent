---
schema_version: 1
open_count: 40
waived_count: 0
fixed_count: 0
total_count: 40
last_updated: 2026-08-26T11:40:14.600Z
---

# Broken Windows Ledger

> Cross-phase defect register. With `workflow.windows_enforce` enabled, `/gsd-ship` blocks while `open_count > 0`.
> Waive with `gsd-tools windows waive <id> "<reason>"` (reason required).
> Mark fixed with `gsd-tools windows fixed <id>`.

| id | phase | kind | file | line | description | status | reason | recorded_at | resolved_at |
|----|-------|------|------|------|-------------|--------|--------|-------------|-------------|
| 1 | 23 | unrun-verify | .planning/phases/23-edt-confinement-ui-responsiveness/23-01-SUMMARY.md |  | FLAG-23-04: sub-frame Send/Cancel flicker on the auto-approved chain path and the ~160-char tool-cancel line's wrap are live-UAT only; routed to 23-HUMAN-UAT.md by plan 23-05 | open |  | 2026-08-20T18:48:49.436Z |  |
| 2 | 23 | unrun-verify | src/main/kotlin/com/six2dez/burp/aiagent/ui/ChatPanel.kt |  | No committed test asserts the UI-SPEC Rule S-4 /tool transcript echo or the S3 busy-state entry on either user-originated tool path; both verified only by execution-time source-order greps (23-02 D7) | open |  | 2026-08-20T19:23:53.967Z |  |
| 3 | 23 | unrun-verify | src/main/kotlin/com/six2dez/burp/aiagent/ui/SettingsPanelActions.kt |  | The JOptionPane save-failure and restore-failure modals are not headless-testable (getRootFrame throws HeadlessException); asserted only via the inline banner, modal routed to 23-HUMAN-UAT.md | open |  | 2026-08-20T20:12:38.644Z |  |
| 4 | 23 | unrun-verify | src/main/kotlin/com/six2dez/burp/aiagent/ui/BottomTabsPanel.kt |  | FLAG-23-01: whether the recolored disabled Save button reads as inert on Burp's live L&F is unverifiable headlessly; routed to 23-HUMAN-UAT.md | open |  | 2026-08-20T20:12:38.741Z |  |
| 5 | 23 | deviation | src/main/kotlin/com/six2dez/burp/aiagent/ui/ChatPanel.kt |  | D-23-04-1: clearChatState() (teardown path 3 of 5, the one D-08 never listed) does not supersede a running tool worker, so a Clear Chat can be followed by a result row and a followup turn for the conversation just cleared. Logged in 23 deferred-items.md by plan 23-04; surfaced here by the 23-05 phase gate so it is visible at ship time. | open |  | 2026-08-20T21:40:01.047Z |  |
| 6 | 23 | deviation | src/test/kotlin/com/six2dez/burp/aiagent/ui/SettingsSaveAsyncTest.kt |  | Plan 23-08 Task 2's specified single-shape scanner test would have left the active-scanner guard unfalsifiable (sequential early returns short-circuit); rebuilt as two shapes — recorded so the pattern is not copied | open |  | 2026-08-21T10:20:38.074Z |  |
| 7 | 24 | deviation | .planning/phases/24-scheduler-process-robustness/24-01-PLAN.md |  | 24-01 task 3 red-before-green used git checkout <ref> -- <path> instead of the plan's git stash push/pop, which is prohibited in a worktree (shared refs/stash) | open |  | 2026-08-21T13:33:28.113Z |  |
| 8 | 24 | deviation | .planning/phases/24-scheduler-process-robustness/24-04-PLAN.md |  | Plan 24-04 acceptance criteria reference gradle/libs.versions.toml, which does not exist in this repo (no version catalog); same wrong reference as 24-03. Dependency-graph invariant is checked via build.gradle.kts instead. | open |  | 2026-08-21T14:37:17.711Z |  |
| 9 | 24 | deviation | src/main/kotlin/com/six2dez/burp/aiagent/backends/cli/CliBackend.kt |  | Plan 24-04 predicted 2 CliTempFileRegistry.deregister sites; 3 were required because the prompt write-failure branch returns before the outer finally. Structural gate asserts 3. Resolved in code, recorded so a later plan does not 'correct' the count back to 2. | open |  | 2026-08-21T14:37:25.254Z |  |
| 10 | 24 | deviation | build/test-results/test |  | One unidentified intermittent test failure on the first unfiltered './gradlew test detekt ktlintCheck shadowJar' run in plan 24-05; suite name lost to output tailing, not reproduced across three subsequent full runs (two forced --rerun-tasks). Probable known RedactionTest wall-clock/SafeRegex-deadline flake under CPU load, unconfirmed. | open |  | 2026-08-21T15:14:38.714Z |  |
| 11 | 27 | deviation | src/main/kotlin/com/six2dez/burp/aiagent/redact/Redaction.kt |  | Plan 27-04 task 1 acceptance criterion 3 (removed-line grep must return 0) is unsatisfiable by the implementation the same plan mandates: it counts CONSUMER lines of COOKIE_NAME_PART/COOKIE_NAME_TOKEN, and rebuilding both cookie regexes via logicalLineHeaderRule necessarily rewrites those lines. Observed 3, not 0. The intended invariant (definitions byte-identical) was measured and holds; parity/ownership tests green unedited. Criterion is wrong, code is right. | open |  | 2026-08-24T20:05:39.056Z |  |
| 12 | 27 | deviation | src/test/kotlin/com/six2dez/burp/aiagent/mcp/tools/McpToolHelpersTest.kt |  | Plan 27-04 task 3 premise falsified: the plan expected the AR-27-01 pin to turn RED after the cookie-rule fix. Measured green. The pin's fixture is the ParsedRequest HEADER-MAP shape, which carries no line boundary of any kind (no escaped newline), so neither branch can fire; inverting in place would have committed a RED, false test. Resolved by inverting on the raw-message-in-JSON shape and gating the header-map shape's root cause instead. Residual for 27-06: redactIfNeeded still cannot recover a missed cookie on the header-map shape, so sanitizeHeaders remains the only control for request_parse/response_parse. | open |  | 2026-08-24T20:05:47.651Z |  |
| 13 | 27 | deviation | .planning/phases/27-priv-05-gap-closure-sanitize-headers/27-05-PLAN.md |  | Plan 27-05 task 1 red-probe criterion falsified as written: removing a tool name from RAW_HTTP_EMISSION_TOOL_NAMES fails theMeasuredEmissionSiteCountIsPinned (via the names-vs-count cross-check), NOT everyEmissionToolNameAppearsInBothExecutors — a smaller set simply checks fewer names and stays green. The intended probe for the presence test is a name IN the set that is ABSENT from an executor; run as a rename and observed RED. Both probes recorded. | open |  | 2026-08-24T20:31:37.830Z |  |
| 14 | 27 | deviation | src/test/kotlin/com/six2dez/burp/aiagent/mcp/McpSupervisorProbeTest.kt |  | Plan 27-05 task 3 acceptance criterion 'no path containing Probe appears anywhere under src/' is unsatisfiable as written: McpSupervisorProbeTest.kt has existed since phase 20 (08e8ff8) and is unrelated to redaction measurement. The intended invariant — this plan's throwaway residual probe is not committed — was verified directly (git status --porcelain src/ clean, probe lives only in the scratchpad, its full source quoted in the SUMMARY). | open |  | 2026-08-24T20:31:45.459Z |  |
| 15 | 27 | deviation | src/test/kotlin/com/six2dez/burp/aiagent/mcp/tools/SerializedEmissionSiteInventoryTest.kt |  | Plan 27-05 pins the three addTool registration sites by PATH and COUNT rather than by file:line as the plan's must_haves wording implies. Line-number pins rot on any edit above line 34 and contradict CookieHeaderRuleOwnershipTest's stated path-keyed discipline. Measured line numbers (McpTool.kt:34, McpTool.kt:72, McpToolHandlers.kt:122) are recorded in the SUMMARY and in the test's own constant comment instead. Red probe confirms the path+count pin still fails when a path is dropped. | open |  | 2026-08-24T20:31:52.926Z |  |
| 16 | 27 | deviation | .planning/phases/27-priv-05-gap-closure-sanitize-headers/27-06-PLAN.md |  | Plan 27-06 premise falsified: AR-27-02 is NOT simply 'SUPERSEDED, not still-deferred'. Measured in 27-06 against the compiled classes: on the header-map shape {"X-API-Key":"..."} is redacted by the JSON-key rule while {"Cookie":"..."} and {"X-Cookie":"..."} are not, because cookie is absent from SENSITIVE_WORDS (Redaction.kt:663-664). AR-27-02 is superseded on the raw-message-in-JSON shape only and remains load-bearing on the header-map shape. Recorded at that scope rather than at the plan's wider wording. | open |  | 2026-08-24T20:54:31.180Z |  |
| 17 | 27 | deviation | .planning/phases/27-priv-05-gap-closure-sanitize-headers/27-06-PLAN.md |  | Plan 27-06 task 1 acceptance criteria 1-2 are unsatisfiable as written: 'no removed line falls inside clauses (1),(2),(3) of T-26-02-01'. The whole T-26-02-01 register row is ONE physical markdown line, so appending clause (4) necessarily rewrites it and it appears as a removed line. Intent verified directly instead: the splice asserted the OLD row body is an exact BYTE PREFIX of the new row (5633 -> 11320 chars, prefix check PASS), so no clause text was altered. Recorded rather than worked around. | open |  | 2026-08-24T20:54:40.048Z |  |
| 18 | 27 | deviation | .planning/phases/27-priv-05-gap-closure-sanitize-headers/27-06-PLAN.md |  | GATE DEFECT, applies to any GSD plan reusing it: the append-only gate "git diff HEAD -- <file> \| grep -c '^-[^-]'" is a FALSE ZERO on markdown BULLET lines. A removed line beginning '- ' renders in the diff as '--', which the [^-] class excludes, so a real deletion is not counted. Observed in 27-06 on CONCERNS.md: the gate returned 0 while one line was genuinely replaced. Robust form used instead: git diff HEAD --unified=0 -- <file> \| grep '^-' \| grep -v '^--- '. Both 26-SECURITY.md and v0.10.0-MILESTONE-AUDIT.md were re-checked with the robust form; the milestone audit is genuinely append-only (zero removals). | open |  | 2026-08-24T20:54:48.137Z |  |
| 19 | 27 | deviation | src/test/kotlin/com/six2dez/burp/aiagent/mcp/tools/ParameterCarrierRedactionTest.kt |  | Plan 27-07 task 1 premise falsified: the plan directs the new suite to follow SerializedEmissionRedactionTest's @Nested layout, while its OWN acceptance criterion 1 requires every test method present by name in TEST-...ParameterCarrierRedactionTest.xml. JUnit writes each @Nested class to its own TEST-<outer>$<Inner>.xml, so the criterion was unsatisfiable against the mandated layout no matter how green the suite was. Measured after flattening: tests=18 failures=0 errors=0 in the single expected XML, all 18 names present. WINDOWS 11/13/14/15 class - a criterion counting a population the artifact does not contain. | open |  | 2026-08-25T10:55:31.312Z |  |
| 20 | 27 | deviation | src/test/kotlin/com/six2dez/burp/aiagent/mcp/tools/ParameterCarrierRedactionTest.kt |  | Plan 27-07 task 1 criterion 5 unsatisfiable as written: it requires reverting the request_parse branch and confirming the task's behavioural probes go RED. They CANNOT. Every producer begins HttpRequest.httpRequest(content), a Montoya static factory needing Burp's internal ObjectFactory that cannot run in a pure-JVM test (McpToolScopeEnforcementTest records the same constraint), so the task-1 probes drive sanitizeParameters directly and never reach the production branch - the suite would stay green with the sanitizer correct and never called. Resolved by pulling the producer-ownership pin forward from task 2 into task 1. Measured: red probe 2 fails the pin with the expected message; restored green. | open |  | 2026-08-25T10:55:41.532Z |  |
| 21 | 27 | deviation | .planning/phases/27-priv-05-gap-closure-sanitize-headers/27-07-PLAN.md |  | Plan 27-07 task 2 criterion 5 prediction falsified: it expected BOTH the behavioural probe and the producer-ownership pin to go RED when a sanitizer call is dropped from ONE producer. MEASURED: only the pin did - 1 of 18 red, run twice (delegating stub, then a genuine bypass constructing ParsedParam raw), failing theProducerInventoryIsExactlyFourAndEveryOneRoutesThroughTheSanitizer with 'McpToolLegacy.kt carries 1 sanitizeParameters( calls, not 2 ==> expected: <2> but was: <1>'. The behavioural probes are structurally incapable of detecting a producer unwiring. Reported as a finding rather than assumed, per the WINDOWS 13 precedent of a probe failing a different assertion than predicted. Note also that JUnit stops a method at its first failed assertion, so the pin's HELPERS_FILE count assertion never ran. | open |  | 2026-08-25T10:55:45.682Z |  |
| 22 | 27 | deviation | src/test/kotlin/com/six2dez/burp/aiagent/mcp/tools/ParameterCarrierRedactionTest.kt |  | Plan 27-08 task 1 repeats 27-07's @Nested conflict verbatim: the action text says to add the prompt-path fixtures 'in a nested class', while the SAME task's criterion 2 requires all five fixture groups present BY NAME in TEST-...ParameterCarrierRedactionTest.xml. Unsatisfiable against a nested layout. Recorded separately from entry 19 because it is a SECOND occurrence in the same phase after the first was already logged - the pattern was not carried forward into the next plan's authoring. Measured after flattening: all seven promptPath... methods present by name, tests=25 skipped=0 failures=0 errors=0. | open |  | 2026-08-25T10:55:54.139Z |  |
| 23 | 27 | deviation | src/test/kotlin/com/six2dez/burp/aiagent/redact/CookieCarrierInventoryTest.kt |  | Plan 27-08 task 2 premise falsified: the action text declares ROUTED_THROUGH and CLASSIFIED_NON_CARRYING as maps from path -> reason. MEASURED: 6 of the 11 carrier files have accessors with DIFFERENT dispositions (McpToolExecutorImpl alone routes headers through sanitizeHeaders, parameters through sanitizeParameters, raw messages through the redactIfNeeded choke point, and mode-gates its cookie jar), so a path-only key forces four answers into one string and makes assertion 1's 'exactly one of the two maps' meaningless for exactly the files that matter most. Resolved by keying on a CarrierSite(path, accessor) PAIR; the residual granularity limit is stated in the KDoc as an explicitly weaker fifth bound. All four assertions green; red probes A, B and C each fail the assertion the plan named. | open |  | 2026-08-25T10:55:58.043Z |  |
| 24 | 27 | deviation | src/main/kotlin/com/six2dez/burp/aiagent/redact/Redaction.kt |  | Plan 27-08 baseline C4 differed from the tree: the plan measured cookieTypedParamRegex's comment at Redaction.kt:628-639 with the regex at :640 (taken at commit 389cbbd). MEASURED on the 27-08 worktree (based on a20290f, which includes 27-07's merge): comment at :678-689, regex at :690 - a +50 line shift caused by 27-07 adding isCookieParameterType and COOKIE_PARAMETER_TYPE_NAME with their KDoc ABOVE this rule. Explained BEFORE any constant depending on it was written; nothing in 27-08 is keyed on a line number. Recorded because the same +50 shift silently rotted clause (3) of T-26-02-01 in 26-SECURITY.md, whose citations Redaction.kt:158 and :91 now land inside COMMENTS (the declarations are at :293 and :125) - plan 27-09 clause (5) notes that without editing clause (3). | open |  | 2026-08-25T10:56:09.231Z |  |
| 25 | 27 | deviation | .planning/phases/27-priv-05-gap-closure-sanitize-headers/27-08-PLAN.md |  | Plan 27-08 task 2 red probe A prediction incomplete: criterion 4 asks only that assertion (1) everyCookieByteCarrierSiteIsRoutedOrClassified go RED and name the path under NEW. MEASURED failures=2, not 1 - assertion (2) theMeasuredPerFilePerAccessorCountsArePinned ALSO went red, on its 'set of FILES' limb, because the probe introduced a whole new FILE (scanner/AiScanCheck.kt) rather than merely a new call in a known one. Assertion (1) did name the path exactly as required. Reported rather than smoothed over, per the WINDOWS 13 precedent. Probe restored via git checkout; file byte-clean. | open |  | 2026-08-25T10:56:13.265Z |  |
| 26 | 27 | deviation | .planning/phases/26-coverage-static-analysis-debt-docs/26-SECURITY.md |  | Plan 27-08's authored threat register falsified by its own measurement: T-27-08-07 was assigned severity medium at plan-authoring time, BEFORE measurement 1 existed. MEASURED severity is LOW, on the decisive caller-echo property - request_parse and params_extract parse a raw request string supplied BY THE CALLER, so the AI agent already held those bytes. 27-08 recorded the disagreement rather than resolving it and routed the choice to 27-09. Plan 27-09 filed it as AR-27-07 at the MEASURED low, stated the disagreement in the register row, and routed the remaining DISPOSITION question (widen SENSITIVE_WORDS against WR-01's measured 32 false positives, or keep the residual) to 27-HUMAN-UAT.md test 8. Recorded so no later reader silently inherits either number. | open |  | 2026-08-25T10:56:22.727Z |  |
| 27 | 27 | deviation | .planning/ROADMAP.md |  | Plan 27-09 baseline R13 differed from the tree. The plan states the phase 27 plans counter was REPAIRED at plan time to '9 plans - 6 executed, 3 planned'. MEASURED at execution time: it reads '8/9 plans executed - 6 executed, 3 planned' - STILL two contradictory counters in one line, the leading figure having been advanced to 8/9 by the waves 7-8 merges while the trailing clause stayed at the plan-time value. There are 9 PLAN files on disk. Plan 27-09's criterion 6 requires exactly ONE counter matching that number, but the counter is an execution-tracking field the execute-phase orchestrator owns and overwrites after the executor returns, so the executor did not edit it. Recorded here so the contradiction is not lost between the two owners. | open |  | 2026-08-25T10:56:26.885Z |  |
| 28 | 27 | deviation | .planning/phases/26-coverage-static-analysis-debt-docs/26-SECURITY.md |  | Plan 27-09 baseline R2 conflates BYTES with CHARACTERS. It records the T-26-02-01 row's 'character length' as 11400, which is what wc -c reports - a BYTE count. MEASURED character length of the same line: 11320, because the row contains multi-byte UTF-8 (em dashes, ellipses, curly quotes). 11320 is exactly the figure 27-06's own read-back recorded ('5,633 -> 11,320 chars'), so the two rounds agree once the units are named. The byte-prefix gate was run on characters and PASSED (cell body 11231 -> 18263 chars, 7032 appended). Recorded because a gate quoting a number in the wrong unit is one edit away from a false FAIL, and because 26-SECURITY.md is a file where every count is load-bearing. | open |  | 2026-08-25T10:56:36.329Z |  |
| 29 | 27 | deviation | .planning/phases/27-priv-05-gap-closure-sanitize-headers/27-09-PLAN.md |  | Plan 27-09 baseline R4 conflates APPEARANCE with DEFINITION. It records the AR-27- ids 'defined anywhere under .planning/' as AR-27-01..AR-27-05. MEASURED: grep for AR-27-[0-9]+ under .planning/ returns AR-27-01 through AR-27-08 BEFORE any edit by this plan - AR-27-06 already appeared at ROADMAP.md:444 and in 27-09-PLAN.md itself, and AR-27-08 throughout 27-08-SUMMARY.md. None of the three was DEFINED (26-SECURITY.md contained zero occurrences of AR-27-06). The baseline as literally worded was therefore already false when written. This is 26-SECURITY.md standing rule (i) - presence is not width - applied to the register's own identifiers, and it is why AR-27-05's row opens 'if any earlier draft cited this identifier, nothing stood behind it'. AR-27-06/07/08 are now defined with evidence sections. | open |  | 2026-08-25T10:56:41.567Z |  |
| 30 | 27 | deviation | src/test/kotlin/com/six2dez/burp/aiagent/redact/CookieHeaderNameParityTest.kt |  | Plan 27-10 plan-time claim about a file's contents refuted by the tree: the file-header 'WHICH GUARD COVERS WHICH MUTATION' block says a narrowing turns 'THIS test' red, which was unambiguous while the file held ONE behavioural test. MEASURED after task 1's rename plus task 2's additions: THREE tests in the file, and no way to tell which 'this test' meant — in the comment block whose whole job is telling a maintainer which guard covers which mutation. Resolved by naming the method in each bullet and adding a third bullet for the re-narrowing of COOKIE_NAME_PART, the one mutation the one-directional implication test structurally cannot see. | open |  | 2026-08-26T11:34:48.806Z |  |
| 31 | 27 | deviation | src/test/kotlin/com/six2dez/burp/aiagent/redact/CookieHeaderNameParityTest.kt |  | Plan 27-10 task 2 acceptance criterion requires the renamed underscore test to pass 'for all three underscore names', while the test as task 1 left it hardcoded val name = 'my_cookie'. MEASURED: 1 of the 3 names actually asserted on; X_Cookie and session_cookie would have been exercised only by the ONE-DIRECTIONAL invariant test, which a narrowing of COOKIE_NAME_PART cannot falsify — two corpus entries raising the floors and asserting nothing. Resolved by iterating PARITY_CORPUS.filter { contains('_') } under an exact-count guard EXPECTED_UNDERSCORE_NAMES = 3; measured corpus size 19 against floor 18, predicate positives 14 against floor 12. | open |  | 2026-08-26T11:34:48.883Z |  |
| 32 | 27 | deviation | .planning/phases/27-priv-05-gap-closure-sanitize-headers/27-11-PLAN.md |  | Plan 27-11 task 1 premise was too NARROW and was widened by task 3 rather than left: it wrote the AR-27-09 indented-header residual as MEASURED surviving under STRICT. MEASURED against the compiled classes at the end of round 4: GET / HTTP/1.1\\r\\n Cookie: a=SECRET5\\r\\n\\r\\n survives BYTE-UNCHANGED under STRICT *and* BALANCED — one mode WIDER than both the plan and 27-VERIFICATION-3.md recorded. The shipped source sentence was widened to match and plan 27-13 filed AR-27-09 at the measured two-mode width. Recorded because understating a residual is the same failure mode as overclaiming a fix, and this ledger already carries the opposite direction as entry 26. | open |  | 2026-08-26T11:34:48.952Z |  |
| 33 | 27 | deviation | src/test/kotlin/com/six2dez/burp/aiagent/mcp/tools/SerializedEmissionRedactionTest.kt |  | Plan 27-11 task 2 premise not constructible on the carrier it named: the plan asks for a cookie header at the open of the notes value AND a sibling field after it, on HttpRequestResponse. MEASURED: HttpRequestResponse declares notes LAST, so there is no sibling field after it and the byte-identity over-match assertion would have had nothing to bite on. Resolved by moving one carrier deeper to IssueDetails, a real emission shape carrying the same notes followed by collaboratorInteractions and definition, where notes ends immediately after the cookie value so the tail's only terminator is the closing quote — the hardest form of the case. The plan's stated PROPERTY is met unchanged. | open |  | 2026-08-26T11:34:49.027Z |  |
| 34 | 27 | deviation | .planning/phases/26-coverage-static-analysis-debt-docs/26-SECURITY.md |  | Line citations in T-26-02-01 rotted AGAIN, in the same class this ledger recorded as entry 24, and are now wrong in clauses (4) and (5) as well as (3). Clause (5) recorded isCookieHeaderName moving from Redaction.kt:158 to :293; MEASURED 2026-08-26 in plan 27-13 after the wave 10-11 merges it is at :391. Clause (4) cites the ADMITTING call site as PassiveAiScannerFilters.kt:186; MEASURED :197. Full measured set recorded in clause (6): COOKIE_NAME_PART :132, COOKIE_NAME_TOKEN :138, JSON_ESCAPED_NEWLINE :266, JSON_STRING_OPEN :277 (27-11-SUMMARY recorded :271 on its own pre-merge tree), logicalLineHeaderRule :312, cookieHeaderRegex :319, setCookieHeaderRegex :324, hostHeaderRegex :1992; McpToolHelpers.kt:336 has not moved. Clauses (3), (4) and (5) are preserved verbatim and clause (6) notes the rot instead of editing them. | open |  | 2026-08-26T11:34:49.099Z |  |
| 35 | 27 | deviation | src/test/kotlin/com/six2dez/burp/aiagent/redact/RedactingPolicySurvivalSweepTest.kt |  | Plan 27-12 projection falsified: it states BENIGN_ACCESSORS accounts for 5 pre-existing hits. MEASURED on the 27-12 tree: 7 live functions. Cause identified rather than guessed — the two extra are plan 27-11's JSON-string-open probes aCanonicalCookieAtTheOpenOfAJsonStringDoesNotSurviveStrict and ...Balanced, which each carry their own assertTrue on Sentinel.BENIGN_CONTROL and which landed in 27-12's base between the plan being written and the plan executing. The measured 7 is what the KDoc records, with the projection and the reason for the gap beside it. NOTHING was narrowed to make them agree: no vocabulary entry narrowed, no ALLOWLIST key added, BENIGN_ACCESSORS still holds exactly ONE key. | open |  | 2026-08-26T11:34:49.175Z |  |
| 36 | 27 | deviation | src/test/kotlin/com/six2dez/burp/aiagent/redact/RedactingPolicySurvivalSweepTest.kt |  | Plan 27-12 projection falsified, second figure with the same cause as the BENIGN_ACCESSORS entry: it states the unqualified vocabulary reports 7 hits on the post-fix tree. MEASURED: 9. The arithmetic closes on the measured numbers — 7 (BENIGN_ACCESSORS) + 1 (POSITION RULE) + 1 (NEGATION RULE) = 9 unqualified, and 9 minus 9 = 0 qualified, the measured hit set on the tree as shipped with an EMPTY ALLOWLIST. Filed separately from the BENIGN_ACCESSORS entry because it is a separately stated plan projection, and recorded so no later reader silently inherits the projected 7. Three other plan projections matched exactly: all four red-probe boundary values, the pre-round detector count of 3, and the post-fix qualified count of 0. | open |  | 2026-08-26T11:34:49.246Z |  |
| 37 | 27 | deviation | src/test/kotlin/com/six2dez/burp/aiagent/redact/RedactingPolicySurvivalSweepTest.kt |  | Plan 27-12 threat T-27-12-09 predicted the self-scan failure MODE but not its CAUSE, and predicted the wrong number: it measured 2 self-hits on a mock. MEASURED on the real file: 5 self-hits, and noGreenTestAssertsASensitiveValueSurvivesARedactingPolicy failed with those same 5. Cause the plan's mechanism excluded — it assumed only fixture literals could toggle raw-string state, but dropRawStringInteriors toggled on EVERY line including comments, and the class KDoc quotes a bare triple quote while explaining the walk, an ODD toggle that inverted the skip for every line below it. A REAL bug, fixed by consulting isCommentOnly in the FILE WALK only. The KDoc triple quote was deliberately LEFT so the rule is not vacuous; measured after the fix: 0 self-hits with the skip, 5 without. | open |  | 2026-08-26T11:34:49.315Z |  |
| 38 | 27 | deviation | .planning/phases/27-priv-05-gap-closure-sanitize-headers/27-13-PLAN.md |  | GATE DEFECT, same family as entry 18 and applicable to any GSD plan reusing it. Plan 27-13 task 1 acceptance criterion 11 is 'git diff HEAD -- 26-SECURITY.md \| grep -c Reopening-2026-08-24 returns 0', intended to prove the 2026-08-24 reopening narrative is on no ADDED and no REMOVED line. Plain git diff emits three lines of CONTEXT, so any insertion within three lines of that heading prints it as a context line and the gate reads 1 while nothing was edited. OBSERVED 1. Both precise forms return 0: filtering to +/- lines returns 0, and git diff --unified=0 returns 0. Standing-rule clauses (v) and (vi) were anchored INSIDE the standing-rule section (after clause (iv)'s last line) rather than above the heading, which is also the structurally correct placement, and the residual 1 is a context line only. | open |  | 2026-08-26T11:34:49.404Z |  |
| 39 | 27 | deviation | .planning/phases/27-priv-05-gap-closure-sanitize-headers/COVERAGE.md |  | Plan 27-13 task 3 part B premise falsified by the tree it describes: it states the 27-10..27-13 plan bodies 'name the passive-scan prompt path, the MCP tool result shapes and the Montoya host API repeatedly'. MEASURED 2026-08-26 with grep -ohc per file: the MCP tool NAMES (request_parse, response_parse, params_extract, scanner_issues, proxy_http_history, proxy_http_history_regex, site_map, site_map_regex) appear ZERO times in all four - and those are the exact tokens that made this COVERAGE declaration necessary for 27-07 and 27-08. Montoya appears ONCE and API 3 times on 2 lines, ALL of them inside 27-13-PLAN.md and one of them being the instruction sentence itself. What IS present: HttpRequestResponse 6 (27-11 only), toolJson.encodeToString 3 (27-12 only), ParsedRequest 2, SiteMapEntry / McpToolContext.redactIfNeeded / AuditIssue.detail() 1 each. COVERAGE.md records the measured inventory as a table with the divergence stated rather than the projected 'repeatedly'; the declaration still stands, and on this evidence more easily than in 2026-08-25. | open |  | 2026-08-26T11:39:25.509Z |  |
| 40 | 27 | deviation | .planning/STATE.md |  | Plan 27-13 task 3 part C directs the executor to write STATE.md fields that the execute-phase orchestrator OWNS and overwrites in worktree mode - last_activity, last_activity_desc, the Current Position block and the progress counters. Same two-owner class as ledger entry 27 (the ROADMAP plans counter), now recurring for STATE.md, and execute-plan.md's update_current_position step explicitly says to SKIP it when running in a worktree. STATE.md was therefore left UNTOUCHED by plan 27-13. MEASURED: both halves of the task's own acceptance criterion already hold on disk with no edit - Current Position reads 'Plan: 1 of 13' (13 = the number of 27-*-PLAN.md files) and nothing claims the phase is verified (status: executing, 'Phase: 27 ... - EXECUTING'; the only two 'verified' strings in the file are about phases 16 and an unrelated coverage note). Recorded so the untouched file is not read later as an omission, and so the criterion is not 'satisfied' next round by an executor writing a field it does not own. | open |  | 2026-08-26T11:40:14.600Z |  |

````json
[
  {
    "id": 1,
    "kind": "unrun-verify",
    "phase": "23",
    "file": ".planning/phases/23-edt-confinement-ui-responsiveness/23-01-SUMMARY.md",
    "line": null,
    "description": "FLAG-23-04: sub-frame Send/Cancel flicker on the auto-approved chain path and the ~160-char tool-cancel line's wrap are live-UAT only; routed to 23-HUMAN-UAT.md by plan 23-05",
    "status": "open",
    "reason": "",
    "recorded_at": "2026-08-20T18:48:49.436Z",
    "resolved_at": null
  },
  {
    "id": 2,
    "kind": "unrun-verify",
    "phase": "23",
    "file": "src/main/kotlin/com/six2dez/burp/aiagent/ui/ChatPanel.kt",
    "line": null,
    "description": "No committed test asserts the UI-SPEC Rule S-4 /tool transcript echo or the S3 busy-state entry on either user-originated tool path; both verified only by execution-time source-order greps (23-02 D7)",
    "status": "open",
    "reason": "",
    "recorded_at": "2026-08-20T19:23:53.967Z",
    "resolved_at": null
  },
  {
    "id": 3,
    "kind": "unrun-verify",
    "phase": "23",
    "file": "src/main/kotlin/com/six2dez/burp/aiagent/ui/SettingsPanelActions.kt",
    "line": null,
    "description": "The JOptionPane save-failure and restore-failure modals are not headless-testable (getRootFrame throws HeadlessException); asserted only via the inline banner, modal routed to 23-HUMAN-UAT.md",
    "status": "open",
    "reason": "",
    "recorded_at": "2026-08-20T20:12:38.644Z",
    "resolved_at": null
  },
  {
    "id": 4,
    "kind": "unrun-verify",
    "phase": "23",
    "file": "src/main/kotlin/com/six2dez/burp/aiagent/ui/BottomTabsPanel.kt",
    "line": null,
    "description": "FLAG-23-01: whether the recolored disabled Save button reads as inert on Burp's live L&F is unverifiable headlessly; routed to 23-HUMAN-UAT.md",
    "status": "open",
    "reason": "",
    "recorded_at": "2026-08-20T20:12:38.741Z",
    "resolved_at": null
  },
  {
    "id": 5,
    "kind": "deviation",
    "phase": "23",
    "file": "src/main/kotlin/com/six2dez/burp/aiagent/ui/ChatPanel.kt",
    "line": null,
    "description": "D-23-04-1: clearChatState() (teardown path 3 of 5, the one D-08 never listed) does not supersede a running tool worker, so a Clear Chat can be followed by a result row and a followup turn for the conversation just cleared. Logged in 23 deferred-items.md by plan 23-04; surfaced here by the 23-05 phase gate so it is visible at ship time.",
    "status": "open",
    "reason": "",
    "recorded_at": "2026-08-20T21:40:01.047Z",
    "resolved_at": null
  },
  {
    "id": 6,
    "kind": "deviation",
    "phase": "23",
    "file": "src/test/kotlin/com/six2dez/burp/aiagent/ui/SettingsSaveAsyncTest.kt",
    "line": null,
    "description": "Plan 23-08 Task 2's specified single-shape scanner test would have left the active-scanner guard unfalsifiable (sequential early returns short-circuit); rebuilt as two shapes — recorded so the pattern is not copied",
    "status": "open",
    "reason": "",
    "recorded_at": "2026-08-21T10:20:38.074Z",
    "resolved_at": null
  },
  {
    "id": 7,
    "kind": "deviation",
    "phase": "24",
    "file": ".planning/phases/24-scheduler-process-robustness/24-01-PLAN.md",
    "line": null,
    "description": "24-01 task 3 red-before-green used git checkout <ref> -- <path> instead of the plan's git stash push/pop, which is prohibited in a worktree (shared refs/stash)",
    "status": "open",
    "reason": "",
    "recorded_at": "2026-08-21T13:33:28.113Z",
    "resolved_at": null
  },
  {
    "id": 8,
    "kind": "deviation",
    "phase": "24",
    "file": ".planning/phases/24-scheduler-process-robustness/24-04-PLAN.md",
    "line": null,
    "description": "Plan 24-04 acceptance criteria reference gradle/libs.versions.toml, which does not exist in this repo (no version catalog); same wrong reference as 24-03. Dependency-graph invariant is checked via build.gradle.kts instead.",
    "status": "open",
    "reason": "",
    "recorded_at": "2026-08-21T14:37:17.711Z",
    "resolved_at": null
  },
  {
    "id": 9,
    "kind": "deviation",
    "phase": "24",
    "file": "src/main/kotlin/com/six2dez/burp/aiagent/backends/cli/CliBackend.kt",
    "line": null,
    "description": "Plan 24-04 predicted 2 CliTempFileRegistry.deregister sites; 3 were required because the prompt write-failure branch returns before the outer finally. Structural gate asserts 3. Resolved in code, recorded so a later plan does not 'correct' the count back to 2.",
    "status": "open",
    "reason": "",
    "recorded_at": "2026-08-21T14:37:25.254Z",
    "resolved_at": null
  },
  {
    "id": 10,
    "kind": "deviation",
    "phase": "24",
    "file": "build/test-results/test",
    "line": null,
    "description": "One unidentified intermittent test failure on the first unfiltered './gradlew test detekt ktlintCheck shadowJar' run in plan 24-05; suite name lost to output tailing, not reproduced across three subsequent full runs (two forced --rerun-tasks). Probable known RedactionTest wall-clock/SafeRegex-deadline flake under CPU load, unconfirmed.",
    "status": "open",
    "reason": "",
    "recorded_at": "2026-08-21T15:14:38.714Z",
    "resolved_at": null
  },
  {
    "id": 11,
    "kind": "deviation",
    "phase": "27",
    "file": "src/main/kotlin/com/six2dez/burp/aiagent/redact/Redaction.kt",
    "line": null,
    "description": "Plan 27-04 task 1 acceptance criterion 3 (removed-line grep must return 0) is unsatisfiable by the implementation the same plan mandates: it counts CONSUMER lines of COOKIE_NAME_PART/COOKIE_NAME_TOKEN, and rebuilding both cookie regexes via logicalLineHeaderRule necessarily rewrites those lines. Observed 3, not 0. The intended invariant (definitions byte-identical) was measured and holds; parity/ownership tests green unedited. Criterion is wrong, code is right.",
    "status": "open",
    "reason": "",
    "recorded_at": "2026-08-24T20:05:39.056Z",
    "resolved_at": null
  },
  {
    "id": 12,
    "kind": "deviation",
    "phase": "27",
    "file": "src/test/kotlin/com/six2dez/burp/aiagent/mcp/tools/McpToolHelpersTest.kt",
    "line": null,
    "description": "Plan 27-04 task 3 premise falsified: the plan expected the AR-27-01 pin to turn RED after the cookie-rule fix. Measured green. The pin's fixture is the ParsedRequest HEADER-MAP shape, which carries no line boundary of any kind (no escaped newline), so neither branch can fire; inverting in place would have committed a RED, false test. Resolved by inverting on the raw-message-in-JSON shape and gating the header-map shape's root cause instead. Residual for 27-06: redactIfNeeded still cannot recover a missed cookie on the header-map shape, so sanitizeHeaders remains the only control for request_parse/response_parse.",
    "status": "open",
    "reason": "",
    "recorded_at": "2026-08-24T20:05:47.651Z",
    "resolved_at": null
  },
  {
    "id": 13,
    "kind": "deviation",
    "phase": "27",
    "file": ".planning/phases/27-priv-05-gap-closure-sanitize-headers/27-05-PLAN.md",
    "line": null,
    "description": "Plan 27-05 task 1 red-probe criterion falsified as written: removing a tool name from RAW_HTTP_EMISSION_TOOL_NAMES fails theMeasuredEmissionSiteCountIsPinned (via the names-vs-count cross-check), NOT everyEmissionToolNameAppearsInBothExecutors — a smaller set simply checks fewer names and stays green. The intended probe for the presence test is a name IN the set that is ABSENT from an executor; run as a rename and observed RED. Both probes recorded.",
    "status": "open",
    "reason": "",
    "recorded_at": "2026-08-24T20:31:37.830Z",
    "resolved_at": null
  },
  {
    "id": 14,
    "kind": "deviation",
    "phase": "27",
    "file": "src/test/kotlin/com/six2dez/burp/aiagent/mcp/McpSupervisorProbeTest.kt",
    "line": null,
    "description": "Plan 27-05 task 3 acceptance criterion 'no path containing Probe appears anywhere under src/' is unsatisfiable as written: McpSupervisorProbeTest.kt has existed since phase 20 (08e8ff8) and is unrelated to redaction measurement. The intended invariant — this plan's throwaway residual probe is not committed — was verified directly (git status --porcelain src/ clean, probe lives only in the scratchpad, its full source quoted in the SUMMARY).",
    "status": "open",
    "reason": "",
    "recorded_at": "2026-08-24T20:31:45.459Z",
    "resolved_at": null
  },
  {
    "id": 15,
    "kind": "deviation",
    "phase": "27",
    "file": "src/test/kotlin/com/six2dez/burp/aiagent/mcp/tools/SerializedEmissionSiteInventoryTest.kt",
    "line": null,
    "description": "Plan 27-05 pins the three addTool registration sites by PATH and COUNT rather than by file:line as the plan's must_haves wording implies. Line-number pins rot on any edit above line 34 and contradict CookieHeaderRuleOwnershipTest's stated path-keyed discipline. Measured line numbers (McpTool.kt:34, McpTool.kt:72, McpToolHandlers.kt:122) are recorded in the SUMMARY and in the test's own constant comment instead. Red probe confirms the path+count pin still fails when a path is dropped.",
    "status": "open",
    "reason": "",
    "recorded_at": "2026-08-24T20:31:52.926Z",
    "resolved_at": null
  },
  {
    "id": 16,
    "kind": "deviation",
    "phase": "27",
    "file": ".planning/phases/27-priv-05-gap-closure-sanitize-headers/27-06-PLAN.md",
    "line": null,
    "description": "Plan 27-06 premise falsified: AR-27-02 is NOT simply 'SUPERSEDED, not still-deferred'. Measured in 27-06 against the compiled classes: on the header-map shape {\"X-API-Key\":\"...\"} is redacted by the JSON-key rule while {\"Cookie\":\"...\"} and {\"X-Cookie\":\"...\"} are not, because cookie is absent from SENSITIVE_WORDS (Redaction.kt:663-664). AR-27-02 is superseded on the raw-message-in-JSON shape only and remains load-bearing on the header-map shape. Recorded at that scope rather than at the plan's wider wording.",
    "status": "open",
    "reason": "",
    "recorded_at": "2026-08-24T20:54:31.180Z",
    "resolved_at": null
  },
  {
    "id": 17,
    "kind": "deviation",
    "phase": "27",
    "file": ".planning/phases/27-priv-05-gap-closure-sanitize-headers/27-06-PLAN.md",
    "line": null,
    "description": "Plan 27-06 task 1 acceptance criteria 1-2 are unsatisfiable as written: 'no removed line falls inside clauses (1),(2),(3) of T-26-02-01'. The whole T-26-02-01 register row is ONE physical markdown line, so appending clause (4) necessarily rewrites it and it appears as a removed line. Intent verified directly instead: the splice asserted the OLD row body is an exact BYTE PREFIX of the new row (5633 -> 11320 chars, prefix check PASS), so no clause text was altered. Recorded rather than worked around.",
    "status": "open",
    "reason": "",
    "recorded_at": "2026-08-24T20:54:40.048Z",
    "resolved_at": null
  },
  {
    "id": 18,
    "kind": "deviation",
    "phase": "27",
    "file": ".planning/phases/27-priv-05-gap-closure-sanitize-headers/27-06-PLAN.md",
    "line": null,
    "description": "GATE DEFECT, applies to any GSD plan reusing it: the append-only gate \"git diff HEAD -- <file> | grep -c '^-[^-]'\" is a FALSE ZERO on markdown BULLET lines. A removed line beginning '- ' renders in the diff as '--', which the [^-] class excludes, so a real deletion is not counted. Observed in 27-06 on CONCERNS.md: the gate returned 0 while one line was genuinely replaced. Robust form used instead: git diff HEAD --unified=0 -- <file> | grep '^-' | grep -v '^--- '. Both 26-SECURITY.md and v0.10.0-MILESTONE-AUDIT.md were re-checked with the robust form; the milestone audit is genuinely append-only (zero removals).",
    "status": "open",
    "reason": "",
    "recorded_at": "2026-08-24T20:54:48.137Z",
    "resolved_at": null
  },
  {
    "id": 19,
    "kind": "deviation",
    "phase": "27",
    "file": "src/test/kotlin/com/six2dez/burp/aiagent/mcp/tools/ParameterCarrierRedactionTest.kt",
    "line": null,
    "description": "Plan 27-07 task 1 premise falsified: the plan directs the new suite to follow SerializedEmissionRedactionTest's @Nested layout, while its OWN acceptance criterion 1 requires every test method present by name in TEST-...ParameterCarrierRedactionTest.xml. JUnit writes each @Nested class to its own TEST-<outer>$<Inner>.xml, so the criterion was unsatisfiable against the mandated layout no matter how green the suite was. Measured after flattening: tests=18 failures=0 errors=0 in the single expected XML, all 18 names present. WINDOWS 11/13/14/15 class - a criterion counting a population the artifact does not contain.",
    "status": "open",
    "reason": "",
    "recorded_at": "2026-08-25T10:55:31.312Z",
    "resolved_at": null
  },
  {
    "id": 20,
    "kind": "deviation",
    "phase": "27",
    "file": "src/test/kotlin/com/six2dez/burp/aiagent/mcp/tools/ParameterCarrierRedactionTest.kt",
    "line": null,
    "description": "Plan 27-07 task 1 criterion 5 unsatisfiable as written: it requires reverting the request_parse branch and confirming the task's behavioural probes go RED. They CANNOT. Every producer begins HttpRequest.httpRequest(content), a Montoya static factory needing Burp's internal ObjectFactory that cannot run in a pure-JVM test (McpToolScopeEnforcementTest records the same constraint), so the task-1 probes drive sanitizeParameters directly and never reach the production branch - the suite would stay green with the sanitizer correct and never called. Resolved by pulling the producer-ownership pin forward from task 2 into task 1. Measured: red probe 2 fails the pin with the expected message; restored green.",
    "status": "open",
    "reason": "",
    "recorded_at": "2026-08-25T10:55:41.532Z",
    "resolved_at": null
  },
  {
    "id": 21,
    "kind": "deviation",
    "phase": "27",
    "file": ".planning/phases/27-priv-05-gap-closure-sanitize-headers/27-07-PLAN.md",
    "line": null,
    "description": "Plan 27-07 task 2 criterion 5 prediction falsified: it expected BOTH the behavioural probe and the producer-ownership pin to go RED when a sanitizer call is dropped from ONE producer. MEASURED: only the pin did - 1 of 18 red, run twice (delegating stub, then a genuine bypass constructing ParsedParam raw), failing theProducerInventoryIsExactlyFourAndEveryOneRoutesThroughTheSanitizer with 'McpToolLegacy.kt carries 1 sanitizeParameters( calls, not 2 ==> expected: <2> but was: <1>'. The behavioural probes are structurally incapable of detecting a producer unwiring. Reported as a finding rather than assumed, per the WINDOWS 13 precedent of a probe failing a different assertion than predicted. Note also that JUnit stops a method at its first failed assertion, so the pin's HELPERS_FILE count assertion never ran.",
    "status": "open",
    "reason": "",
    "recorded_at": "2026-08-25T10:55:45.682Z",
    "resolved_at": null
  },
  {
    "id": 22,
    "kind": "deviation",
    "phase": "27",
    "file": "src/test/kotlin/com/six2dez/burp/aiagent/mcp/tools/ParameterCarrierRedactionTest.kt",
    "line": null,
    "description": "Plan 27-08 task 1 repeats 27-07's @Nested conflict verbatim: the action text says to add the prompt-path fixtures 'in a nested class', while the SAME task's criterion 2 requires all five fixture groups present BY NAME in TEST-...ParameterCarrierRedactionTest.xml. Unsatisfiable against a nested layout. Recorded separately from entry 19 because it is a SECOND occurrence in the same phase after the first was already logged - the pattern was not carried forward into the next plan's authoring. Measured after flattening: all seven promptPath... methods present by name, tests=25 skipped=0 failures=0 errors=0.",
    "status": "open",
    "reason": "",
    "recorded_at": "2026-08-25T10:55:54.139Z",
    "resolved_at": null
  },
  {
    "id": 23,
    "kind": "deviation",
    "phase": "27",
    "file": "src/test/kotlin/com/six2dez/burp/aiagent/redact/CookieCarrierInventoryTest.kt",
    "line": null,
    "description": "Plan 27-08 task 2 premise falsified: the action text declares ROUTED_THROUGH and CLASSIFIED_NON_CARRYING as maps from path -> reason. MEASURED: 6 of the 11 carrier files have accessors with DIFFERENT dispositions (McpToolExecutorImpl alone routes headers through sanitizeHeaders, parameters through sanitizeParameters, raw messages through the redactIfNeeded choke point, and mode-gates its cookie jar), so a path-only key forces four answers into one string and makes assertion 1's 'exactly one of the two maps' meaningless for exactly the files that matter most. Resolved by keying on a CarrierSite(path, accessor) PAIR; the residual granularity limit is stated in the KDoc as an explicitly weaker fifth bound. All four assertions green; red probes A, B and C each fail the assertion the plan named.",
    "status": "open",
    "reason": "",
    "recorded_at": "2026-08-25T10:55:58.043Z",
    "resolved_at": null
  },
  {
    "id": 24,
    "kind": "deviation",
    "phase": "27",
    "file": "src/main/kotlin/com/six2dez/burp/aiagent/redact/Redaction.kt",
    "line": null,
    "description": "Plan 27-08 baseline C4 differed from the tree: the plan measured cookieTypedParamRegex's comment at Redaction.kt:628-639 with the regex at :640 (taken at commit 389cbbd). MEASURED on the 27-08 worktree (based on a20290f, which includes 27-07's merge): comment at :678-689, regex at :690 - a +50 line shift caused by 27-07 adding isCookieParameterType and COOKIE_PARAMETER_TYPE_NAME with their KDoc ABOVE this rule. Explained BEFORE any constant depending on it was written; nothing in 27-08 is keyed on a line number. Recorded because the same +50 shift silently rotted clause (3) of T-26-02-01 in 26-SECURITY.md, whose citations Redaction.kt:158 and :91 now land inside COMMENTS (the declarations are at :293 and :125) - plan 27-09 clause (5) notes that without editing clause (3).",
    "status": "open",
    "reason": "",
    "recorded_at": "2026-08-25T10:56:09.231Z",
    "resolved_at": null
  },
  {
    "id": 25,
    "kind": "deviation",
    "phase": "27",
    "file": ".planning/phases/27-priv-05-gap-closure-sanitize-headers/27-08-PLAN.md",
    "line": null,
    "description": "Plan 27-08 task 2 red probe A prediction incomplete: criterion 4 asks only that assertion (1) everyCookieByteCarrierSiteIsRoutedOrClassified go RED and name the path under NEW. MEASURED failures=2, not 1 - assertion (2) theMeasuredPerFilePerAccessorCountsArePinned ALSO went red, on its 'set of FILES' limb, because the probe introduced a whole new FILE (scanner/AiScanCheck.kt) rather than merely a new call in a known one. Assertion (1) did name the path exactly as required. Reported rather than smoothed over, per the WINDOWS 13 precedent. Probe restored via git checkout; file byte-clean.",
    "status": "open",
    "reason": "",
    "recorded_at": "2026-08-25T10:56:13.265Z",
    "resolved_at": null
  },
  {
    "id": 26,
    "kind": "deviation",
    "phase": "27",
    "file": ".planning/phases/26-coverage-static-analysis-debt-docs/26-SECURITY.md",
    "line": null,
    "description": "Plan 27-08's authored threat register falsified by its own measurement: T-27-08-07 was assigned severity medium at plan-authoring time, BEFORE measurement 1 existed. MEASURED severity is LOW, on the decisive caller-echo property - request_parse and params_extract parse a raw request string supplied BY THE CALLER, so the AI agent already held those bytes. 27-08 recorded the disagreement rather than resolving it and routed the choice to 27-09. Plan 27-09 filed it as AR-27-07 at the MEASURED low, stated the disagreement in the register row, and routed the remaining DISPOSITION question (widen SENSITIVE_WORDS against WR-01's measured 32 false positives, or keep the residual) to 27-HUMAN-UAT.md test 8. Recorded so no later reader silently inherits either number.",
    "status": "open",
    "reason": "",
    "recorded_at": "2026-08-25T10:56:22.727Z",
    "resolved_at": null
  },
  {
    "id": 27,
    "kind": "deviation",
    "phase": "27",
    "file": ".planning/ROADMAP.md",
    "line": null,
    "description": "Plan 27-09 baseline R13 differed from the tree. The plan states the phase 27 plans counter was REPAIRED at plan time to '9 plans - 6 executed, 3 planned'. MEASURED at execution time: it reads '8/9 plans executed - 6 executed, 3 planned' - STILL two contradictory counters in one line, the leading figure having been advanced to 8/9 by the waves 7-8 merges while the trailing clause stayed at the plan-time value. There are 9 PLAN files on disk. Plan 27-09's criterion 6 requires exactly ONE counter matching that number, but the counter is an execution-tracking field the execute-phase orchestrator owns and overwrites after the executor returns, so the executor did not edit it. Recorded here so the contradiction is not lost between the two owners.",
    "status": "open",
    "reason": "",
    "recorded_at": "2026-08-25T10:56:26.885Z",
    "resolved_at": null
  },
  {
    "id": 28,
    "kind": "deviation",
    "phase": "27",
    "file": ".planning/phases/26-coverage-static-analysis-debt-docs/26-SECURITY.md",
    "line": null,
    "description": "Plan 27-09 baseline R2 conflates BYTES with CHARACTERS. It records the T-26-02-01 row's 'character length' as 11400, which is what wc -c reports - a BYTE count. MEASURED character length of the same line: 11320, because the row contains multi-byte UTF-8 (em dashes, ellipses, curly quotes). 11320 is exactly the figure 27-06's own read-back recorded ('5,633 -> 11,320 chars'), so the two rounds agree once the units are named. The byte-prefix gate was run on characters and PASSED (cell body 11231 -> 18263 chars, 7032 appended). Recorded because a gate quoting a number in the wrong unit is one edit away from a false FAIL, and because 26-SECURITY.md is a file where every count is load-bearing.",
    "status": "open",
    "reason": "",
    "recorded_at": "2026-08-25T10:56:36.329Z",
    "resolved_at": null
  },
  {
    "id": 29,
    "kind": "deviation",
    "phase": "27",
    "file": ".planning/phases/27-priv-05-gap-closure-sanitize-headers/27-09-PLAN.md",
    "line": null,
    "description": "Plan 27-09 baseline R4 conflates APPEARANCE with DEFINITION. It records the AR-27- ids 'defined anywhere under .planning/' as AR-27-01..AR-27-05. MEASURED: grep for AR-27-[0-9]+ under .planning/ returns AR-27-01 through AR-27-08 BEFORE any edit by this plan - AR-27-06 already appeared at ROADMAP.md:444 and in 27-09-PLAN.md itself, and AR-27-08 throughout 27-08-SUMMARY.md. None of the three was DEFINED (26-SECURITY.md contained zero occurrences of AR-27-06). The baseline as literally worded was therefore already false when written. This is 26-SECURITY.md standing rule (i) - presence is not width - applied to the register's own identifiers, and it is why AR-27-05's row opens 'if any earlier draft cited this identifier, nothing stood behind it'. AR-27-06/07/08 are now defined with evidence sections.",
    "status": "open",
    "reason": "",
    "recorded_at": "2026-08-25T10:56:41.567Z",
    "resolved_at": null
  },
  {
    "id": 30,
    "kind": "deviation",
    "phase": "27",
    "file": "src/test/kotlin/com/six2dez/burp/aiagent/redact/CookieHeaderNameParityTest.kt",
    "line": null,
    "description": "Plan 27-10 plan-time claim about a file's contents refuted by the tree: the file-header 'WHICH GUARD COVERS WHICH MUTATION' block says a narrowing turns 'THIS test' red, which was unambiguous while the file held ONE behavioural test. MEASURED after task 1's rename plus task 2's additions: THREE tests in the file, and no way to tell which 'this test' meant — in the comment block whose whole job is telling a maintainer which guard covers which mutation. Resolved by naming the method in each bullet and adding a third bullet for the re-narrowing of COOKIE_NAME_PART, the one mutation the one-directional implication test structurally cannot see.",
    "status": "open",
    "reason": "",
    "recorded_at": "2026-08-26T11:34:48.806Z",
    "resolved_at": null
  },
  {
    "id": 31,
    "kind": "deviation",
    "phase": "27",
    "file": "src/test/kotlin/com/six2dez/burp/aiagent/redact/CookieHeaderNameParityTest.kt",
    "line": null,
    "description": "Plan 27-10 task 2 acceptance criterion requires the renamed underscore test to pass 'for all three underscore names', while the test as task 1 left it hardcoded val name = 'my_cookie'. MEASURED: 1 of the 3 names actually asserted on; X_Cookie and session_cookie would have been exercised only by the ONE-DIRECTIONAL invariant test, which a narrowing of COOKIE_NAME_PART cannot falsify — two corpus entries raising the floors and asserting nothing. Resolved by iterating PARITY_CORPUS.filter { contains('_') } under an exact-count guard EXPECTED_UNDERSCORE_NAMES = 3; measured corpus size 19 against floor 18, predicate positives 14 against floor 12.",
    "status": "open",
    "reason": "",
    "recorded_at": "2026-08-26T11:34:48.883Z",
    "resolved_at": null
  },
  {
    "id": 32,
    "kind": "deviation",
    "phase": "27",
    "file": ".planning/phases/27-priv-05-gap-closure-sanitize-headers/27-11-PLAN.md",
    "line": null,
    "description": "Plan 27-11 task 1 premise was too NARROW and was widened by task 3 rather than left: it wrote the AR-27-09 indented-header residual as MEASURED surviving under STRICT. MEASURED against the compiled classes at the end of round 4: GET / HTTP/1.1\\r\\n Cookie: a=SECRET5\\r\\n\\r\\n survives BYTE-UNCHANGED under STRICT *and* BALANCED — one mode WIDER than both the plan and 27-VERIFICATION-3.md recorded. The shipped source sentence was widened to match and plan 27-13 filed AR-27-09 at the measured two-mode width. Recorded because understating a residual is the same failure mode as overclaiming a fix, and this ledger already carries the opposite direction as entry 26.",
    "status": "open",
    "reason": "",
    "recorded_at": "2026-08-26T11:34:48.952Z",
    "resolved_at": null
  },
  {
    "id": 33,
    "kind": "deviation",
    "phase": "27",
    "file": "src/test/kotlin/com/six2dez/burp/aiagent/mcp/tools/SerializedEmissionRedactionTest.kt",
    "line": null,
    "description": "Plan 27-11 task 2 premise not constructible on the carrier it named: the plan asks for a cookie header at the open of the notes value AND a sibling field after it, on HttpRequestResponse. MEASURED: HttpRequestResponse declares notes LAST, so there is no sibling field after it and the byte-identity over-match assertion would have had nothing to bite on. Resolved by moving one carrier deeper to IssueDetails, a real emission shape carrying the same notes followed by collaboratorInteractions and definition, where notes ends immediately after the cookie value so the tail's only terminator is the closing quote — the hardest form of the case. The plan's stated PROPERTY is met unchanged.",
    "status": "open",
    "reason": "",
    "recorded_at": "2026-08-26T11:34:49.027Z",
    "resolved_at": null
  },
  {
    "id": 34,
    "kind": "deviation",
    "phase": "27",
    "file": ".planning/phases/26-coverage-static-analysis-debt-docs/26-SECURITY.md",
    "line": null,
    "description": "Line citations in T-26-02-01 rotted AGAIN, in the same class this ledger recorded as entry 24, and are now wrong in clauses (4) and (5) as well as (3). Clause (5) recorded isCookieHeaderName moving from Redaction.kt:158 to :293; MEASURED 2026-08-26 in plan 27-13 after the wave 10-11 merges it is at :391. Clause (4) cites the ADMITTING call site as PassiveAiScannerFilters.kt:186; MEASURED :197. Full measured set recorded in clause (6): COOKIE_NAME_PART :132, COOKIE_NAME_TOKEN :138, JSON_ESCAPED_NEWLINE :266, JSON_STRING_OPEN :277 (27-11-SUMMARY recorded :271 on its own pre-merge tree), logicalLineHeaderRule :312, cookieHeaderRegex :319, setCookieHeaderRegex :324, hostHeaderRegex :1992; McpToolHelpers.kt:336 has not moved. Clauses (3), (4) and (5) are preserved verbatim and clause (6) notes the rot instead of editing them.",
    "status": "open",
    "reason": "",
    "recorded_at": "2026-08-26T11:34:49.099Z",
    "resolved_at": null
  },
  {
    "id": 35,
    "kind": "deviation",
    "phase": "27",
    "file": "src/test/kotlin/com/six2dez/burp/aiagent/redact/RedactingPolicySurvivalSweepTest.kt",
    "line": null,
    "description": "Plan 27-12 projection falsified: it states BENIGN_ACCESSORS accounts for 5 pre-existing hits. MEASURED on the 27-12 tree: 7 live functions. Cause identified rather than guessed — the two extra are plan 27-11's JSON-string-open probes aCanonicalCookieAtTheOpenOfAJsonStringDoesNotSurviveStrict and ...Balanced, which each carry their own assertTrue on Sentinel.BENIGN_CONTROL and which landed in 27-12's base between the plan being written and the plan executing. The measured 7 is what the KDoc records, with the projection and the reason for the gap beside it. NOTHING was narrowed to make them agree: no vocabulary entry narrowed, no ALLOWLIST key added, BENIGN_ACCESSORS still holds exactly ONE key.",
    "status": "open",
    "reason": "",
    "recorded_at": "2026-08-26T11:34:49.175Z",
    "resolved_at": null
  },
  {
    "id": 36,
    "kind": "deviation",
    "phase": "27",
    "file": "src/test/kotlin/com/six2dez/burp/aiagent/redact/RedactingPolicySurvivalSweepTest.kt",
    "line": null,
    "description": "Plan 27-12 projection falsified, second figure with the same cause as the BENIGN_ACCESSORS entry: it states the unqualified vocabulary reports 7 hits on the post-fix tree. MEASURED: 9. The arithmetic closes on the measured numbers — 7 (BENIGN_ACCESSORS) + 1 (POSITION RULE) + 1 (NEGATION RULE) = 9 unqualified, and 9 minus 9 = 0 qualified, the measured hit set on the tree as shipped with an EMPTY ALLOWLIST. Filed separately from the BENIGN_ACCESSORS entry because it is a separately stated plan projection, and recorded so no later reader silently inherits the projected 7. Three other plan projections matched exactly: all four red-probe boundary values, the pre-round detector count of 3, and the post-fix qualified count of 0.",
    "status": "open",
    "reason": "",
    "recorded_at": "2026-08-26T11:34:49.246Z",
    "resolved_at": null
  },
  {
    "id": 37,
    "kind": "deviation",
    "phase": "27",
    "file": "src/test/kotlin/com/six2dez/burp/aiagent/redact/RedactingPolicySurvivalSweepTest.kt",
    "line": null,
    "description": "Plan 27-12 threat T-27-12-09 predicted the self-scan failure MODE but not its CAUSE, and predicted the wrong number: it measured 2 self-hits on a mock. MEASURED on the real file: 5 self-hits, and noGreenTestAssertsASensitiveValueSurvivesARedactingPolicy failed with those same 5. Cause the plan's mechanism excluded — it assumed only fixture literals could toggle raw-string state, but dropRawStringInteriors toggled on EVERY line including comments, and the class KDoc quotes a bare triple quote while explaining the walk, an ODD toggle that inverted the skip for every line below it. A REAL bug, fixed by consulting isCommentOnly in the FILE WALK only. The KDoc triple quote was deliberately LEFT so the rule is not vacuous; measured after the fix: 0 self-hits with the skip, 5 without.",
    "status": "open",
    "reason": "",
    "recorded_at": "2026-08-26T11:34:49.315Z",
    "resolved_at": null
  },
  {
    "id": 38,
    "kind": "deviation",
    "phase": "27",
    "file": ".planning/phases/27-priv-05-gap-closure-sanitize-headers/27-13-PLAN.md",
    "line": null,
    "description": "GATE DEFECT, same family as entry 18 and applicable to any GSD plan reusing it. Plan 27-13 task 1 acceptance criterion 11 is 'git diff HEAD -- 26-SECURITY.md | grep -c Reopening-2026-08-24 returns 0', intended to prove the 2026-08-24 reopening narrative is on no ADDED and no REMOVED line. Plain git diff emits three lines of CONTEXT, so any insertion within three lines of that heading prints it as a context line and the gate reads 1 while nothing was edited. OBSERVED 1. Both precise forms return 0: filtering to +/- lines returns 0, and git diff --unified=0 returns 0. Standing-rule clauses (v) and (vi) were anchored INSIDE the standing-rule section (after clause (iv)'s last line) rather than above the heading, which is also the structurally correct placement, and the residual 1 is a context line only.",
    "status": "open",
    "reason": "",
    "recorded_at": "2026-08-26T11:34:49.404Z",
    "resolved_at": null
  },
  {
    "id": 39,
    "kind": "deviation",
    "phase": "27",
    "file": ".planning/phases/27-priv-05-gap-closure-sanitize-headers/COVERAGE.md",
    "line": null,
    "description": "Plan 27-13 task 3 part B premise falsified by the tree it describes: it states the 27-10..27-13 plan bodies 'name the passive-scan prompt path, the MCP tool result shapes and the Montoya host API repeatedly'. MEASURED 2026-08-26 with grep -ohc per file: the MCP tool NAMES (request_parse, response_parse, params_extract, scanner_issues, proxy_http_history, proxy_http_history_regex, site_map, site_map_regex) appear ZERO times in all four - and those are the exact tokens that made this COVERAGE declaration necessary for 27-07 and 27-08. Montoya appears ONCE and API 3 times on 2 lines, ALL of them inside 27-13-PLAN.md and one of them being the instruction sentence itself. What IS present: HttpRequestResponse 6 (27-11 only), toolJson.encodeToString 3 (27-12 only), ParsedRequest 2, SiteMapEntry / McpToolContext.redactIfNeeded / AuditIssue.detail() 1 each. COVERAGE.md records the measured inventory as a table with the divergence stated rather than the projected 'repeatedly'; the declaration still stands, and on this evidence more easily than in 2026-08-25.",
    "status": "open",
    "reason": "",
    "recorded_at": "2026-08-26T11:39:25.509Z",
    "resolved_at": null
  },
  {
    "id": 40,
    "kind": "deviation",
    "phase": "27",
    "file": ".planning/STATE.md",
    "line": null,
    "description": "Plan 27-13 task 3 part C directs the executor to write STATE.md fields that the execute-phase orchestrator OWNS and overwrites in worktree mode - last_activity, last_activity_desc, the Current Position block and the progress counters. Same two-owner class as ledger entry 27 (the ROADMAP plans counter), now recurring for STATE.md, and execute-plan.md's update_current_position step explicitly says to SKIP it when running in a worktree. STATE.md was therefore left UNTOUCHED by plan 27-13. MEASURED: both halves of the task's own acceptance criterion already hold on disk with no edit - Current Position reads 'Plan: 1 of 13' (13 = the number of 27-*-PLAN.md files) and nothing claims the phase is verified (status: executing, 'Phase: 27 ... - EXECUTING'; the only two 'verified' strings in the file are about phases 16 and an unrelated coverage note). Recorded so the untouched file is not read later as an omission, and so the criterion is not 'satisfied' next round by an executor writing a field it does not own.",
    "status": "open",
    "reason": "",
    "recorded_at": "2026-08-26T11:40:14.600Z",
    "resolved_at": null
  }
]
````
