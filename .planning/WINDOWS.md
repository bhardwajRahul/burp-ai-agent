---
schema_version: 1
open_count: 4
waived_count: 0
fixed_count: 0
total_count: 4
last_updated: 2026-08-20T20:12:38.741Z
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
  }
]
````
