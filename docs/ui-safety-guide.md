# UI Safety Guide

This runbook covers safe operation of scanner and backend controls from the UI.

## Active Scanner Safety

1. Verify scope is configured before enabling active scanning.
2. Start with `SAFE` risk level.
3. Confirm queue size and backpressure indicators before bulk actions.
4. Use targeted tests before broad scans.

## Passive Scanner Cost/Safety

1. Keep **Scope Only** enabled.
2. Tune body/header/parameter caps for cloud backends.
3. Use dedup and prompt cache controls to reduce repeated analysis.
4. Review findings before promotion into reports.

## Backend Health

1. Use **Test connection** after changing backend settings.
2. Treat `AI: Degraded` as actionable diagnostics state.
3. Inspect extension output/errors for CLI exit codes and HTTP failures.

## MCP Safety

1. Keep unsafe tools disabled unless required.
2. Disable MCP when not actively using external clients.
3. Restrict tool exposure per workflow.
