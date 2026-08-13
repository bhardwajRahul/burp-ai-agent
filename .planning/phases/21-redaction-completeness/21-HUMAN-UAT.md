---
status: partial
phase: 21-redaction-completeness
source: [21-VERIFICATION.md]
started: 2026-08-13
updated: 2026-08-13
---

## Current Test

[awaiting human testing]

## Tests

### 1. Live passive scan with session cookies, STRICT then BALANCED
expected: Load the fat JAR in a live Burp. Proxy a request carrying
`Cookie: JSESSIONID=…; PHPSESSID=…; connect.sid=…; auth_token=…; csrftoken=…; remember_me=…`,
trigger a passive AI scan in STRICT and then BALANCED, and inspect the outbound prompt via the
context preview / AI request log. **None of the six cookie values appears anywhere in the prompt;
each cookie NAME is still present as `NAME=[REDACTED]`; the `=== PARAMETERS ===` section shows
`(COOKIE)` lines with values replaced and `(URL)` / `(BODY)` lines untouched.**
why_human: `PassiveAiScannerAnalysis.doAnalysis` needs a live `MontoyaApi`, a backend session and
`ScanKnowledgeBase` state. The Wave-0 extractions moved the PRIV-05-relevant logic out of that reach
and it is verified end-to-end against the real emitter, but the surrounding `doAnalysis`
orchestration is not unit-reachable.
result: [pending]

### 2. The four D-07 OFF strings, with and without custom patterns
expected: In a live Burp set Privacy to OFF with at least one custom redaction pattern configured,
then with none. Read the ChatPanel privacy line, the ContextPreviewDialog banner, the PrivacyPill
tooltip and all four SettingsPanelActions OFF arms. **No string claims OFF means no redaction.** With
patterns configured the wording says built-in redaction is disabled but custom patterns still apply;
with none configured it says built-in redaction is disabled and no custom patterns are configured.
why_human: D-07 covers Swing label strings and this project has no UI integration-test harness
(recorded in `CONCERNS.md` — "UI layer has no integration tests").
result: [pending]

### 3. Unload during scan, then reload with a pathological persisted pattern
expected: Unload the extension in a live Burp while a passive scan is in flight, then reload it with
a hand-edited preferences file containing a pathological custom pattern. **No exception surfaces from
`Redaction.apply` during teardown, and the pathological persisted pattern is dropped at startup
rather than seeded.**
why_human: `App.shutdown()`'s `Redaction.truncationLogger = null` step and `App.initialize`'s
`isPatternSafe` seeding filter both need a live `MontoyaApi`. Plan 21-18 states this plainly and
identifies `maybeLogTruncation`'s `runCatching` as the automated defence that holds regardless; that
wrap exists and is guarded by `truncationLoggerThatThrowsDoesNotAbortRedaction`.
result: [pending]

## Summary

total: 3
passed: 0
issues: 0
pending: 3
skipped: 0
blocked: 0

## Gaps

None. `21-VERIFICATION.md` scored 6/6 must-haves verified. Its fourth human item — the W-A maintainer
disposition on cookie-header name variants — was resolved by **closing** the gap rather than recording
it (maintainer decision 2026-08-13, implemented by plan 21-19), so it is not carried here.
