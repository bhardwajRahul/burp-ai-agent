---
phase: 25-secondary-hardening
plan: 01
status: in-progress
---

# Phase 25 Plan 01: Takeover Credential Hardening Summary

_Execution in progress. Task 1 (the blocking SC1 architecture decision) is recorded below; the
remainder of this document is written when Tasks 2 and 3 complete._

## SC1 decision

**Developer selection, verbatim:**

```
proof-of-possession
```

That is **Option C** — the bind-conflict takeover client presents
`HMAC-SHA256(key = token, message = "burp-ai-agent/mcp-takeover|v1|<host>:<port>|<10s window>")`
instead of the MCP bearer token.

**How the answer was obtained.** The orchestrator presented the full Task 1 decision script — the
precise statement of the problem, Option A (drop automatic takeover), Option B (server-supplied
challenge/response, rejected on analysis because any pre-auth signal only a Burp AI Agent emits
re-creates the external-mode identification oracle Phase 20's D-02 removed at
`KtorMcpServerManager.kt:200-205`), and Option C with its recommendation — to the human developer in
a blocking prompt. The checkpoint was **not** auto-approved: `.planning/config.json` carries
`mode: yolo`, which is known in this repo to auto-select blocking checkpoints, and that behaviour was
deliberately not relied on. A human answered.

**Accepted residual, stated BEFORE the selection was taken.** T-25-04 (Denial of Service,
disposition `accept`) was read out with the recommendation and before the answer was given: the
squatting process *does* receive the proof, and can replay it within its 10-second window — plus one
fallback window, so **20 seconds worst case** — to shut down the freshly-bound MCP server. That is a
denial of service by a process which is already denying the service by holding the port. Tightening
to a single-use server-side nonce cache was rejected as disproportionate.

**Developer rationale.** None beyond the selection itself. The developer chose the recommended
option and added no words of their own; nothing further is attributed to them here.

**Reversibility.** Rated `one-way` in the plan and accepted as such. The credential presented on
`POST /__mcp/shutdown` is a wire-protocol contract between two independently-installed versions of
this extension, with no negotiation step to fall back on.

**Consequences taken forward:** execution proceeds to Task 2 (implement Option C end to end) and
Task 3 (the fake-listener SC2 proof). Plan 25-03's ADR-16 text assumes this selection.
