---
phase: 28-the-issue-detail-cookie-carrier-auditissue-detail-scanner-is
plan: 09
subsystem: testing
tags: [privacy, redaction, mcp, record-repair, kdoc, threat-register]

# Dependency graph
requires:
  - phase: 28-08
    provides: "The round-3 record repair whose two absolute claims this plan narrows, and the three dated markers this plan's fourth marker follows"
  - phase: 28-05
    provides: "The red probe (DETAIL_SENTINEL) whose reach this plan restates honestly"
provides:
  - "Four record sites that describe the read-time redaction pass accurately: it EXISTS and runs under the current privacy mode, but is not TYPE-keyed"
  - "The residual restated in its conditional form — the shape it always had (PRIV-05's original finding)"
  - "A fourth dated marker in both the carrier registry constant and 26-SECURITY.md row 315, recording the round-4 narrowing"
affects: [privacy, redaction, scanner, mcp, AR-27-08, PRIV-05]

actuals:
  tokens: 24621
  tasks: 3
  commits: 3

tech-stack:
  added: []
  patterns:
    - "Retired-literal discipline: a correction must not QUOTE the phrase it retires, because the greps that enforce the retirement are literal"

key-files:
  created:
    - .planning/phases/28-the-issue-detail-cookie-carrier-auditissue-detail-scanner-is/28-09-SUMMARY.md
  modified:
    - src/main/kotlin/com/six2dez/burp/aiagent/scanner/AiScanCheck.kt
    - src/main/kotlin/com/six2dez/burp/aiagent/ui/SettingsPanelInit.kt
    - src/test/kotlin/com/six2dez/burp/aiagent/redact/CookieCarrierInventoryTest.kt
    - .planning/phases/26-coverage-static-analysis-debt-docs/26-SECURITY.md

key-decisions:
  - "The read-time pass is described as EXISTING but not TYPE-keyed — neither the round-3 absolute denial nor the mirror-image overcorrection that 'the redactor handles it'"
  - "The retired phrases are deliberately NOT quoted at any site; a quotation would trip the literal greps that enforce their retirement forever"
  - "The 28-05 probe is credited with bounding the TYPE-KEYED question only; the residual's width for a JWT or base64 session value is recorded as UNMEASURED, not as zero and not as large"
  - "WR-01, WR-04, WR-05 and WR-06 deferred rather than silently fixed — each is a UI or test-scope change with its own measurement burden"

patterns-established:
  - "Fourth-marker append-and-amend: a correction marker WITHDRAWS nothing, adds no control, names the reviewing artifact, and leaves the disposition and its authority explicitly untouched"
  - "Position-guaranteed digest safety: edits to a digest-pinned row are placed past every pinned prefix, with offsets verified BEFORE the edit and both digests re-asserted after"

requirements-completed: []

coverage:
  - id: D1
    description: "The literal `no read-time pass` is retired from all four record sites and replaced with the narrower true claim citing McpToolContext.redactIfNeeded"
    requirement: PRIV-05
    verification:
      - kind: other
        ref: "LC_ALL=C grep -rc 'no read-time pass' AiScanCheck.kt SettingsPanelInit.kt CookieCarrierInventoryTest.kt 26-SECURITY.md -> 0,0,0,0"
        status: pass
      - kind: other
        ref: "LC_ALL=C grep -rc 'redactIfNeeded' across the same four sites -> 1,1,6,10 (mechanism cited at each)"
        status: pass
    human_judgment: false
  - id: D2
    description: "`provably` retired from AiScanCheck.kt and CookieCarrierInventoryTest.kt, replaced with the probe's actual measured reach"
    verification:
      - kind: other
        ref: "LC_ALL=C grep -rc 'provably' AiScanCheck.kt CookieCarrierInventoryTest.kt -> 0,0"
        status: pass
    human_judgment: false
  - id: D3
    description: "Row 315's two pinned prefix digests re-assert byte-identical after the in-place edit"
    verification:
      - kind: other
        ref: "8693-byte prefix -> 8dc326ac23204becce687deeba867740eb2d4dde21346c58d7da9595d137ae2e; 16071-byte prefix -> 5316a97149017ae824d162b72b99d954cb0fa25b28b0c3a8214c01e42390ed72"
        status: pass
    human_judgment: false
  - id: D4
    description: "Four dated markers in row 315, in date order"
    verification:
      - kind: other
        ref: "LC_ALL=C sed -n '315p' | grep -o 'AMENDED 2026-08-2[0-9] by plan 28-[0-9][0-9]|NARROWED 2026-08-28 by plan 28-09' -> 4 (28-03, 28-06, 28-08, 28-09)"
        status: pass
    human_judgment: false
  - id: D5
    description: "No runtime behaviour change — AiScanCheck.kt diff is comment-only and PRIVACY_MODE_TOOLTIP's string value is byte-unchanged"
    verification:
      - kind: other
        ref: "git diff -U0 $BASE -- AiScanCheck.kt | non-comment changed lines -> 0; tooltip literal lines in SettingsPanelInit diff -> 0"
        status: pass
    human_judgment: false
  - id: D6
    description: "Suite green at the unmoved baseline count"
    verification:
      - kind: unit
        ref: "./gradlew test -> 1308 tests / 181 classes, 0 failures, 0 errors, 1 skipped (exit 0)"
        status: pass
    human_judgment: false
  - id: D7
    description: "PRIV-05 stays open and 28-VERIFICATION.md's SC1 override stays applied"
    verification:
      - kind: other
        ref: "shasum -a 256 .planning/REQUIREMENTS.md -> 9b3219662ec0d007c1c82d64eed3ef2698bd306ce69f01205ac9bbc3f42fcfb4 (unchanged); PRIV-05 line 23 still '- [ ]'; git diff --stat $BASE HEAD -- 28-VERIFICATION.md -> empty"
        status: pass
    human_judgment: false
  - id: D8
    description: "The narrowed wording is a faithful account of the tree — a reader checking McpTool.kt:45/:78 and the redactor's cookie rules finds the citations accurate"
    verification: []
    human_judgment: true
    rationale: "Whether the replacement prose states the mechanism at the right altitude — narrow enough not to re-commit the absolute, not so narrow it overcorrects into 'the redactor handles it' — is an editorial judgment no grep can make. The mechanical gates prove the literals are gone and the citations are present; they cannot prove the sentence means the right thing."

# Metrics
duration: 20 min
completed: 2026-08-28
status: complete
---

# Phase 28 Plan 09: Narrow the Read-Time-Pass Overclaim Summary

**Retired the absolute claim `There is no read-time pass over it` from four record sites and replaced it with the true narrower one — the MCP read-time redactor DOES run under the current privacy mode, but is not type-keyed, so a bare `name=value` in a detail line survives it conditionally.**

## Performance

- **Duration:** 20 min
- **Started:** 2026-08-28T08:17:00Z
- **Completed:** 2026-08-28T08:37:31Z
- **Tasks:** 3
- **Files modified:** 4

## Accomplishments

- **The false absolute is gone from all four record sites.** `no read-time pass` occurred once each in `AiScanCheck.kt`, `SettingsPanelInit.kt` (line-wrapped), `CookieCarrierInventoryTest.kt` and row 315 of `26-SECURITY.md`. All four now state that `McpTool.kt:45`/`:78` wrap every tool result in `McpToolContext.redactIfNeeded`, which calls `Redaction.apply(raw, RedactionPolicy.fromMode(privacyMode))` unconditionally under the **current** mode.
- **The mechanism is named, not the claim merely softened.** Each site records *why* that pass does not remove this payload: the `AuditInsertionPointType` is gone by serialization time, and the redactor's three cookie rules each key on framing a detail line does not carry — `cookieHeaderRegex`/`setCookieHeaderRegex` need a logical-line `Cookie:`-style header name, `redactCookieSections` needs a `=== COOKIES ===` span, and `cookieTypedParamRegex` needs a trailing ` (COOKIE)` marker. `buildDetail` emits a bare `**Original Value:** <value>` markdown line, which carries none of the three.
- **`provably` retired at both sites** and replaced with the probe's actual reach: `DETAIL_SENTINEL = "cedar-anchor-marble-feather"` was shaped (per its own KDoc) with no digits, no `=` and no metacharacters so only the type gate could remove it. That bounds the type-keyed question; a JWT or base64 session value is the opposite shape and was **not** measured.
- **The residual is restated as conditional** — its original PRIV-05 shape. `AR-27-08` stays OPEN, `D-28-09`/`D-28-10` stand, the SC1 override in `28-VERIFICATION.md` is untouched.
- **A fourth dated marker** added to both the carrier registry constant and row 315, recording that round 3 committed the absolute form and round 4 narrowed it, naming `28-REVIEW-3.md` WR-02/WR-03.

## Task Commits

1. **Task 1: Correct the two Kotlin source KDocs** — `8ea902e` (docs)
2. **Task 2: Correct the carrier registry constant** — `978f35f` (docs)
3. **Task 3: Correct row 315 and close out** — `daaf25c` (docs)

## Files Created/Modified

- `src/main/kotlin/com/six2dez/burp/aiagent/scanner/AiScanCheck.kt` — `consolidateIssues` KDoc: read-time-pass paragraph rewritten, probe paragraph rewritten, correction-provenance paragraph added. Comment-only diff.
- `src/main/kotlin/com/six2dez/burp/aiagent/ui/SettingsPanelInit.kt` — the `WHY THE BOUND IS STATED TO THE OPERATOR AT ALL` KDoc paragraph corrected. `PRIVACY_MODE_TOOLTIP`'s string value byte-unchanged.
- `src/test/kotlin/com/six2dez/burp/aiagent/redact/CookieCarrierInventoryTest.kt` — clause (a) of `ISSUE_DETAIL_CARRIER_DISPOSITION` corrected; fourth dated marker appended.
- `.planning/phases/26-coverage-static-analysis-debt-docs/26-SECURITY.md` — row 315: both strings corrected in place at bytes >16071, fourth dated marker appended before the column separator.

## Verification Results

| Gate | Expected | Actual |
|---|---|---|
| `no read-time pass` × 4 sites | 0 each | 0, 0, 0, 0 |
| `provably` in the two in-scope files | 0 each | 0, 0 |
| `redactIfNeeded` cited × 4 sites | ≥1 each | 1, 1, 6, 10 |
| Row 315, 8693-byte prefix | `8dc326ac…ae2e` | identical |
| Row 315, 16071-byte prefix | `5316a971…ed72` | identical |
| Row 315 target offsets (pre-edit) | both >16071 | 17538, 17969 |
| Row 315 length | — | 23850 → 25949 bytes |
| Dated markers in row 315 | 4, in date order | 28-03, 28-06, 28-08, 28-09 |
| `AiScanCheck.kt` non-comment changed lines | 0 | 0 |
| `PRIVACY_MODE_TOOLTIP` literal lines in diff | 0 | 0 |
| `28-VERIFICATION.md` | byte-unchanged | empty diffstat |
| `.planning/REQUIREMENTS.md` sha256 | `9b321966…fcfb4` | identical |
| PRIV-05 (line 23) | `- [ ]` | `- [ ]` |
| `threats_open` (recomputed, not restated) | — | raw output `0`; 46 rows scanned, 46 closed |
| Full suite | 1308 / 181, 0 fail, 0 err, 1 skip | 1308 / 181, 0 fail, 0 err, 1 skip (exit 0) |
| `compileKotlin ktlintCheck detekt` | exit 0 | exit 0 |

## Decisions Made

- **Neither absolute nor overcorrection.** The plan explicitly prohibited claiming the read-time pass makes the residual smaller than measured. Each site therefore says the pass runs, says what it does not do, and ends on the conditional — "…still emits that cookie value on a later STRICT read UNLESS some generic, non-cookie rule happens to match the value's own shape", with the width of that condition marked UNMEASURED.
- **Retired phrases are described, never quoted.** The first draft of the correction-provenance sentence quoted both retired phrases verbatim; the gates then still returned 1 for each. Quoting a retired literal keeps tripping the greps that enforce its retirement forever, so all four sites now describe the old claims instead, and two of them say so explicitly.
- **All three cookie rules named, not just the header rule.** The plan's phrasing cited only the `Cookie:` header prefix. The tree has three cookie-keyed rules, and a detail line evades all three for different reasons — naming one would have been the same species of imprecise record this plan exists to remove.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Correction-provenance sentences quoted the very literals they retire**
- **Found during:** Task 1 (and pre-empted in Tasks 2–3)
- **Issue:** The first draft recorded the correction by quoting `"there is no read-time pass over it"` and `"provably"`. Both mechanical gates (`grep -c` → expect 0) still returned 1. The plan's must-have is that the literal occurs **0** times; a quotation defeats it as surely as the original claim.
- **Fix:** Rewrote the provenance text to *describe* the retired claims ("it denied that ANY read-time pass ran…", "…called the red probe's result a PROOF…") and added an explicit note that the phrases are deliberately unquoted because the enforcing greps are literal.
- **Files modified:** `AiScanCheck.kt`, `CookieCarrierInventoryTest.kt`, `26-SECURITY.md`
- **Verification:** `LC_ALL=C grep -c` → 0 at all four sites for both literals
- **Committed in:** `8ea902e`, `978f35f`, `daaf25c`

**2. [Rule 2 - Missing Critical] Plan cited only one of three cookie rules as the reason the pass does not fire**
- **Found during:** Task 1
- **Issue:** The plan's action text explained the residual as the redactor's cookie rule keying on "the `Cookie:` header prefix that a detail line does not carry". Accurate for `cookieHeaderRegex`/`setCookieHeaderRegex`, but the redactor has two further cookie-keyed rules — `redactCookieSections` (`=== COOKIES ===` span) and `cookieTypedParamRegex` (trailing ` (COOKIE)` marker). Recording only one would leave the record re-derivable wrongly a fifth time.
- **Fix:** All four sites enumerate all three rules and the framing each requires, against `buildDetail`'s actual `**Original Value:** <value>` output.
- **Verification:** Rules confirmed against `Redaction.kt:486-492`, `:643-648`, `:1011`; detail shape confirmed against `AiScanCheck.kt:411-420`
- **Committed in:** `8ea902e`, `978f35f`, `daaf25c`

---

**Total deviations:** 2 auto-fixed (1 bug, 1 missing critical)
**Impact on plan:** Both were necessary for the plan's own success criteria to hold honestly. No scope creep — every change stayed inside the four files named in `files_modified`.

## Deferred Items

Recorded here so a later round finds them named rather than lost. All four are real findings from `28-REVIEW-3.md`, deliberately **not** fixed in this plan.

| ID | Finding | Why deferred |
|---|---|---|
| **WR-01** | `PRIVACY_MODE_TOOLTIP` is 206 chars in a Swing tooltip with a 4000 ms `ToolTipManager.dismissDelay`, no `ToolTipManager` tuning anywhere in the tree and no `<html>` wrapper, so the two `D-28-10`-conditioned clauses sit in the truncation zone. A persistent `privacyNotice` (`SubtleNotice`) already sits above the same control. | Moving operator copy is a **UI change with its own measurement burden** (widget behaviour, truncation thresholds, `PrivacyModeTooltipBoundTest`'s three pinned substrings). Explicitly out of scope: the plan required `PRIVACY_MODE_TOOLTIP`'s string value be byte-unchanged. |
| **WR-04** | `theRouteTwoGateIsFailOpenForTheseCookieCapableTypes` calls a **pre-redaction** string (`detailFor`) the residual's "OBSERVABLE width", while the file's SC1 assertions deliberately use `redactedDetailFor`. | Same species of overclaim as WR-03 and one helper call from being true, but the fix edits an assertion in `AiScanCheckDetailCookieCarrierTest.kt` — a file outside this plan's `files_modified`, and a change that would move what the pinned residual measures. |
| **WR-05** | `Applies from now on, not retroactively.` is a blanket claim the product contradicts, and a test now pins it. | Operator copy — same widget and same measurement burden as WR-01, with which it should be fixed together. |
| **WR-06** | The source-scan test claims coverage it does not have: a `setToolTipText(...)` call is invisible to it. | Test-scope defect in the tooltip test surface; belongs with the WR-01/WR-05 tooltip work. |

`AR-27-08` stays **OPEN**. `PRIV-05` stays `- [ ]`. Neither is affected by these deferrals.

## Issues Encountered

None. The row-315 edit was the only high-risk operation and its two pinned digests were guaranteed by position (both targets at bytes 17538 and 17969, past both the 8693- and 16071-byte prefixes) — verified before editing and re-asserted after.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- All four record sites now describe the redactor's reach accurately. The claim that round 3 introduced is retired, and the correction is itself recorded at every site, so a future reader finds the history rather than a silent edit.
- **Outstanding for a later round:** WR-01, WR-04, WR-05, WR-06 (table above). WR-01/WR-05/WR-06 form one coherent tooltip work item; WR-04 is a one-line helper swap in `AiScanCheckDetailCookieCarrierTest.kt` plus the message text that depends on it.
- No blockers. No runtime behaviour changed; the suite count did not move.

---
*Phase: 28-the-issue-detail-cookie-carrier-auditissue-detail-scanner-is*
*Completed: 2026-08-28*

## Self-Check: PASSED

- **Commits exist:** `8ea902e`, `978f35f`, `daaf25c`, `8bc04f0` — all four found in `git log`.
- **Files exist on disk:** `28-09-SUMMARY.md`, `AiScanCheck.kt`, `SettingsPanelInit.kt`, `CookieCarrierInventoryTest.kt`, `26-SECURITY.md` — all found.
- **No file deletions** introduced across the plan (`git diff --diff-filter=D $BASE HEAD` empty).
- **Working tree clean** after the SUMMARY commit.
- **Shared orchestrator artifacts untouched:** `STATE.md` and `ROADMAP.md` were not modified (worktree mode — the orchestrator owns those writes).
