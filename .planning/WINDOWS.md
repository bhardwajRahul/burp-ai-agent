---
schema_version: 1
open_count: 12
waived_count: 0
fixed_count: 0
total_count: 12
last_updated: 2026-08-24T20:05:47.651Z
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
  }
]
````
