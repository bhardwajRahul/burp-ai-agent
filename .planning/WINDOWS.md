---
schema_version: 1
open_count: 2
waived_count: 0
fixed_count: 0
total_count: 2
last_updated: 2026-08-20T19:23:53.967Z
---

# Broken Windows Ledger

> Cross-phase defect register. With `workflow.windows_enforce` enabled, `/gsd-ship` blocks while `open_count > 0`.
> Waive with `gsd-tools windows waive <id> "<reason>"` (reason required).
> Mark fixed with `gsd-tools windows fixed <id>`.

| id | phase | kind | file | line | description | status | reason | recorded_at | resolved_at |
|----|-------|------|------|------|-------------|--------|--------|-------------|-------------|
| 1 | 23 | unrun-verify | .planning/phases/23-edt-confinement-ui-responsiveness/23-01-SUMMARY.md |  | FLAG-23-04: sub-frame Send/Cancel flicker on the auto-approved chain path and the ~160-char tool-cancel line's wrap are live-UAT only; routed to 23-HUMAN-UAT.md by plan 23-05 | open |  | 2026-08-20T18:48:49.436Z |  |
| 2 | 23 | unrun-verify | src/main/kotlin/com/six2dez/burp/aiagent/ui/ChatPanel.kt |  | No committed test asserts the UI-SPEC Rule S-4 /tool transcript echo or the S3 busy-state entry on either user-originated tool path; both verified only by execution-time source-order greps (23-02 D7) | open |  | 2026-08-20T19:23:53.967Z |  |

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
  }
]
````
