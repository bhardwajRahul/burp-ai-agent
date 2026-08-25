---
schema_version: 1
open_count: 29
waived_count: 0
fixed_count: 0
total_count: 29
last_updated: 2026-08-25T10:56:41.567Z
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
  }
]
````
