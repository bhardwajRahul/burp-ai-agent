---
phase: 28-the-issue-detail-cookie-carrier-auditissue-detail-scanner-is
plan: 06
subsystem: privacy
tags: [redaction, scanner, montoya, kotlin, junit5, cookie-carrier, priv-05, ar-27-08, security-register]

requires:
  - phase: 28-the-issue-detail-cookie-carrier-auditissue-detail-scanner-is
    provides: "28-04's `ScannerIssueSupport.sanitizeRenderedPayload` (route 1's `Payload Used:` gate) and 28-05's `AiScanCheck.sanitizeCookiePointText` / `isCookieInsertionPoint` (route 2, both detail lines)"
  - phase: 27-priv-05-gap-closure-sanitize-headers
    provides: "`AR-27-08` — the measured issue-detail cookie carrier, and the `26-SECURITY.md` register row this plan amends"
provides:
  - "`AR-27-08` STAYS OPEN in `26-SECURITY.md`, narrowed to a named residual under a second append-and-amend supersession, with the 8693-byte prior prefix byte-identical"
  - "The record of WHY the 2026-08-27 (plan 28-03) closure was premature, and the control inventory that makes an OPEN row readable as 'fixed but not complete'"
  - "The 28-03 PRIV-05 gate RUN 2 recording failure DISCHARGED, with raw output and its lateness disclosed"
  - "`AuditInsertionPoint.baseValue()` registered as `COOKIE_BYTE_ACCESSORS[insertionPointBaseValue]` — the accessor whose absence hid route 2"
  - "`CookieCarrierInventoryTest.isCommentOnly` narrowed so a markdown-bold raw-string line is no longer mistaken for a KDoc continuation — the bug that made the route-2 carrier line invisible to the scan"
  - "Second supersession on `ISSUE_DETAIL_CARRIER_DISPOSITION`; amended `INJECTION_EXTRACTOR/PARAMETER_LIST` entry; corrected class KDoc axes 3 and 4"
  - "The `already_known` prose defects fixed: evidence-tail bound is the multiset {60, 80, 80}, and the stale `ActiveAiScanner.kt:1246` cite is now `:1242`"
affects: [verify-work, secure-phase, audit-milestone, AR-27-08, AR-28-01, WR-01, PRIV-05]

actuals:
  tokens: 22865
  tasks: 2
  commits: 2

tech-stack:
  added: []
  patterns:
    - "Append-and-amend with the digest taken BEFORE the edit: the pre-edit byte-prefix hash is computed and recorded first, then re-asserted after, so the invariant is proven rather than reconstructed"
    - "Plan id as the discriminator when two supersession markers share a date: same-day markers are distinguished by plan, and the marker says which one it withdraws and which it does not"
    - "An OPEN row must carry its control inventory: a register cell that stays open after real work states, by symbol and probe, what WAS fixed, or the openness reads as 'nothing happened'"
    - "Fix the scan, do not pin around it: when a tripwire's own heuristic hides the site it was extended to watch, narrow the heuristic rather than adjusting the pinned count to match the blind measurement"

key-files:
  created:
    - .planning/phases/28-the-issue-detail-cookie-carrier-auditissue-detail-scanner-is/28-06-SUMMARY.md
  modified:
    - .planning/phases/26-coverage-static-analysis-debt-docs/26-SECURITY.md
    - .planning/phases/28-the-issue-detail-cookie-carrier-auditissue-detail-scanner-is/28-03-SUMMARY.md
    - src/test/kotlin/com/six2dez/burp/aiagent/redact/CookieCarrierInventoryTest.kt

key-decisions:
  - "`AR-27-08` STAYS OPEN, narrowed. The authority is a HUMAN MAINTAINER ANSWER given interactively on 2026-08-27, NOT an auto-advance default: `check auto-mode` reported inactive and `workflow.auto_advance` / `workflow._auto_chain_active` were both confirmed `false` on disk before the question was put. The plan's resume-signal names `stay-open` as what an auto-advancing run would have been forced to pick; that coincidence is recorded as a coincidence, not as the authority."
  - "The `AR-27-08` amendment is written under the row's OWN marker vocabulary (`**AMENDED <date> by plan 28-06 (phase 28) — …**`). No synonym was invented and no token was manufactured to satisfy a grep."
  - "`threats_open` was RECOMPUTED, not restated: the documented awk was re-run and printed `0` over 46 rows, 46 closed. The front-matter counter was therefore NOT edited."
  - "`CookieCarrierInventoryTest.isCommentOnly` was FIXED rather than the new accessor's count pinned at the blind value of 1. Pinning 1 would have registered the accessor while excluding the only site it exists to watch."
  - "`.planning/REQUIREMENTS.md` was not edited and PRIV-05 stays `- [ ]` (D-28-04). The sha256 gate ran twice, under two distinct headings, with raw output both times."
  - "No artifact in this round claims a repository-wide detail-producer gate exists. `WR-01` stays open; D-28-06 keeps it a named residual."

patterns-established:
  - "Same-date supersession disambiguation: when a new marker lands on the same calendar day as the one it withdraws, the marker states BOTH dates and BOTH plan ids and names which it does not supersede"
  - "Late-gate discharge: a gate run after the commits it was meant to block is recorded with its lateness in the first sentence, and the reasoning for the gate's existence is preserved rather than retired with the pending claim"
  - "Non-vacuity by recorded red probe: a new accessor pattern is proven capable of failing by a scratch mutation whose verbatim failure message is recorded, then reverted with `git checkout HEAD --`"

requirements-completed: []

coverage:
  - id: D1
    description: "`26-SECURITY.md`'s `AR-27-08` row carries a SECOND dated supersession recording that the 2026-08-27 (plan 28-03) closure was premature, what is controlled now, and what is not — written append-and-amend with the prior 8693-byte prefix byte-identical."
    requirement: "PRIV-05"
    verification:
      - kind: other
        ref: "LC_ALL=C awk 'NR==315{printf \"%s\", substr($0,1,8693)}' .planning/phases/26-coverage-static-analysis-debt-docs/26-SECURITY.md | shasum -a 256 -> 8dc326ac23204becce687deeba867740eb2d4dde21346c58d7da9595d137ae2e"
        status: pass
      - kind: other
        ref: "LC_ALL=C awk 'NR==315{print length($0)}' -> 16191 (was 8813; strictly grew)"
        status: pass
    human_judgment: false
  - id: D2
    description: "The amended cell names, by identifier, every route and gap this round did NOT close, and states that no repository-wide detail-producer gate exists after it."
    requirement: "PRIV-05"
    verification:
      - kind: other
        ref: "grep -o -E 'AR-28-01|AR-27-04|AR-27-07|AR-27-10|AR-27-11|WR-01|D-28-06' over row 315 -> AR-27-04 2, AR-27-07 3, AR-27-10 2, AR-27-11 2, AR-28-01 3, D-28-06 1, WR-01 2"
        status: pass
    human_judgment: false
  - id: D3
    description: "The two markers in the cell are distinguishable: the new one carries the protocol form and this plan's id, names the 2026-08-27 marker it supersedes, and names the 2026-08-25 measurement it does not."
    requirement: "PRIV-05"
    verification:
      - kind: other
        ref: "row 315 | grep -oE 'AMENDED|SUPERSEDE[SD]?' | wc -l -> 3 (HEAD 1); 'AMENDED <ISO> by plan 28-06' -> 1 (HEAD 0); '2026-08-27' -> 6 (HEAD 1); '2026-08-25' -> 4 (HEAD 2)"
        status: pass
    human_judgment: false
  - id: D4
    description: "The 28-03 PRIV-05 gate RUN 2 recording failure is discharged: raw `shasum` and `grep` output replace the pre-run instruction block, the lateness is stated, and the three later mentions of 28-03's own commit state survive untouched."
    requirement: "PRIV-05"
    verification:
      - kind: other
        ref: "grep -c 'replacing this block' -> 0; block scope grep -c 'OUTSTANDING' -> 0; grep -c 'OUTSTANDING BY DESIGN' -> 2 (unchanged); grep -c 'The one remaining' -> 1 (unchanged); lines 535-600 contain a '^9b3219662ec0d007' line and a '^23:' line"
        status: pass
    human_judgment: false
  - id: D5
    description: "`AuditInsertionPoint.baseValue()` joins the measured accessor set with a `ROUTED_THROUGH` entry for `(scanner/AiScanCheck.kt, insertionPointBaseValue)` enumerating the carrying and non-carrying call by line, and the pinned map plus total move with it."
    requirement: "PRIV-05"
    verification:
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/redact/CookieCarrierInventoryTest.kt#everyCookieByteCarrierSiteIsRoutedOrClassified"
        status: pass
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/redact/CookieCarrierInventoryTest.kt#theMeasuredPerFilePerAccessorCountsArePinned"
        status: pass
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/redact/CookieCarrierInventoryTest.kt#theCarrierScanIsNonVacuous"
        status: pass
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/redact/CookieCarrierInventoryTest.kt#theHeaderValueArgumentMultisetIsPinned"
        status: pass
    human_judgment: false
  - id: D6
    description: "The new accessor is proven NON-VACUOUS by a recorded red probe: a third scratch `baseValue()` call in main source turns the pinned-count assertion red with both maps printed, then is reverted."
    requirement: "PRIV-05"
    verification:
      - kind: other
        ref: "scratch mutation in AiScanCheck.kt -> theMeasuredPerFilePerAccessorCountsArePinned FAILED, verbatim message recorded below; reverted via git checkout HEAD --, git status --porcelain src/ clean of it"
        status: pass
    human_judgment: false
  - id: D7
    description: "The carrier registry no longer asserts a closure that is false of the block: the `INJECTION_EXTRACTOR/PARAMETER_LIST` entry names its third consumer and the 28-04 control, and `ISSUE_DETAIL_CARRIER_DISPOSITION` carries a second supersession naming both producers, the four controlled lines, the defence-in-depth asymmetry, `AR-28-01` and the missing gate."
    requirement: "PRIV-05"
    verification:
      - kind: other
        ref: "prior text preserved verbatim: grep -c 'TWO CONSUMERS, BOTH READ, AND BOTH NOW CONTROLLED' -> 1; 'UNCONTROLLED, MEASURED 2026-08-25 (AR-27-08, severity MEDIUM)' -> 1; 'SUPERSEDED 2026-08-27 (phase 28, plan 28-01)' -> 1"
        status: pass
    human_judgment: false
  - id: D8
    description: "`.planning/REQUIREMENTS.md` is byte-unchanged and PRIV-05 remains unchecked, proven by a gate run TWICE under two distinct headings."
    requirement: "PRIV-05"
    verification:
      - kind: other
        ref: "RUN 1 and RUN 2 headings below; shasum 9b3219662ec0d007…, line 23 '- [ ]', git diff --stat 5c6b337..HEAD -- .planning/REQUIREMENTS.md prints nothing"
        status: pass
    human_judgment: false

duration: 22min
completed: 2026-08-27
status: complete
---

# Phase 28 Plan 06: Make the Record Match the Code Summary

**`AR-27-08` stays OPEN under a second byte-prefix-verified amendment that inventories the four controlled detail lines across both producers and names every route it does not close; the accessor that hid route 2 — `AuditInsertionPoint.baseValue()` — is now machine-checked, along with the scan bug that would have made registering it useless.**

## Performance

- **Duration:** ~22 min
- **Started:** 2026-08-27T19:06Z (approx.)
- **Completed:** 2026-08-27T19:28Z
- **Tasks:** 2 (task 1 of the plan was the blocking `checkpoint:decision`, resolved before this run)
- **Files modified:** 3

## The checkpoint decision, and its authority

The plan's first task is a blocking `checkpoint:decision` on how `AR-27-08` is dispositioned. A
prior executor reached it and correctly halted without selecting.

**DECISION: `stay-open`.**

**AUTHORITY: the human maintainer, answering interactively on 2026-08-27.** This was NOT an
auto-advance default. `check auto-mode` reported inactive; `workflow.auto_advance` and
`workflow._auto_chain_active` were both confirmed `false` on disk by the halting executor; the
question was put to the maintainer and `stay-open` came back as their answer. The plan's
resume-signal happens to name `stay-open` as the option an auto-advancing run would have been
forced to select — that coincidence is recorded here as a coincidence and nothing more. Both the
register cell and this SUMMARY record the human answer as the authority.

## Accomplishments

- **`AR-27-08` amended append-and-amend and left OPEN.** Row 315 of `26-SECURITY.md` grew from 8813
  to 16191 bytes with a 7377-byte insertion placed immediately before the ` | Measured by plan
  27-08` column separator. The prior 8693-byte prefix is byte-identical, proven by a digest computed
  BEFORE the edit and re-asserted after.
- **The record is not wider than the control.** The cell inventories the four controlled lines by
  symbol and committed probe, labels route 2's payload line as defence in depth only, and names
  `AR-28-01`, `AR-27-04`, `AR-27-07`, `AR-27-10`, `AR-27-11`, `WR-01` and `D-28-06` as not covered.
- **`threats_open` recomputed, not restated** — raw output `0`, 46 rows scanned, 46 closed. The
  front-matter counter was left at `0` and not edited.
- **The 28-03 RUN 2 recording failure discharged**, with raw gate output and the lateness stated in
  the block's first sentence.
- **`AuditInsertionPoint.baseValue()` registered and machine-checked**, with a red probe proving the
  new pattern can fail.
- **A scan bug found and fixed that would have made that registration hollow** — see Deviations.
- **The two `already_known` prose defects fixed**: the evidence-tail bound is now the multiset
  `{60, 80, 80}` pointing at `EvidenceTailReachTest`, and the stale `ActiveAiScanner.kt:1246`
  citation is corrected to the live `:1242`.

## Task Commits

1. **Task 1: `AR-27-08`'s second supersession, `threats_open` recomputed, RUN 2 discharged** —
   `8439894` (docs)
2. **Task 2: the carrier registry stops asserting a false closure and grows the hiding accessor** —
   `8200b55` (test)

**Plan metadata:** committed with this SUMMARY.

## PRIV-05 gate — RUN 1 (in-task)

Run during task 1, before any edit to `26-SECURITY.md` or `28-03-SUMMARY.md`.

```
$ shasum -a 256 .planning/REQUIREMENTS.md
9b3219662ec0d007c1c82d64eed3ef2698bd306ce69f01205ac9bbc3f42fcfb4  .planning/REQUIREMENTS.md
```

```
$ grep -n 'PRIV-05' .planning/REQUIREMENTS.md
6:Scope = the 17 findings of the 2026-08-05 deep code review of v0.9.2. Two of them (SEC-04, PRIV-05) are **defects verified by running the shipped code**, not theoretical concerns — they are the reason this milestone exists. Phase numbering continues from the previous milestone (Phase 20+).
10:**Ordering constraint:** SEC-04 and PRIV-05 are live defects in a published release and lead the milestone. SEC-06 (agent trust boundary) and REL-05 (EDT) both rewrite `ChatPanel.maybeExecuteToolCall` and must be sequential, not parallel. QUAL-06 lands last so it can cover the code the earlier phases produce.
23:- [ ] **PRIV-05** (Finding 2, **high**): Cookie values do not reach an AI backend in STRICT or BALANCED mode by any path. …
39:- [x] **DOC-03**: A security advisory documents SEC-04 and PRIV-05 for users running v0.9.0–v0.9.2 …
47:| PRIV-05 | 2 | High | 21 |
```

Digest begins `9b3219662ec0d007…`. **Line 23 is `- [ ]` — still an UNCHECKED box.** (Lines 23 and 39
are elided at the right margin for readability here only; the untruncated line 23 is pasted verbatim
in the discharged RUN 2 block of `28-03-SUMMARY.md`.)

## PRIV-05 gate — RUN 2 (post-completion, after both task commits)

Run after `8200b55`, against the tree this plan is about to hand back.

```
$ shasum -a 256 .planning/REQUIREMENTS.md
9b3219662ec0d007c1c82d64eed3ef2698bd306ce69f01205ac9bbc3f42fcfb4  .planning/REQUIREMENTS.md
```

```
$ grep -n 'PRIV-05' .planning/REQUIREMENTS.md
6:Scope = the 17 findings of the 2026-08-05 deep code review of v0.9.2 …
10:**Ordering constraint:** SEC-04 and PRIV-05 are live defects in a published release …
23:- [ ] **PRIV-05** (Finding 2, **high**): Cookie values do not reach an AI backend in STRICT or BALANCED mode by any path …
39:- [x] **DOC-03**: A security advisory documents SEC-04 and PRIV-05 for users running v0.9.0–v0.9.2 …
47:| PRIV-05 | 2 | High | 21 |
```

```
$ git diff --stat 5c6b337..HEAD -- .planning/REQUIREMENTS.md src/main/kotlin/
(no output)
```

**Nothing moved.** The digest is identical to RUN 1, line 23 is still an unchecked box, and
`.planning/REQUIREMENTS.md` is absent from the plan's diff entirely — as is all of
`src/main/kotlin/`, so `AdaptivePayloadEngine.kt:52` is byte-unchanged too. The two runs are recorded
under distinct headings deliberately: a single heading cannot tell a later reader which side of a
completion step the digest was taken on, and that ambiguity is what let the 2026-08-27 `phase.complete
27` flip reach a staged tree (`.planning/WINDOWS.md` entry 54).

## The byte-prefix gate

**Pre-edit, computed BEFORE anything was written:**

```
$ LC_ALL=C awk 'NR==315{i=index($0,"| Measured by plan 27-08"); printf "%s", substr($0,1,i-1)}' \
    .planning/phases/26-coverage-static-analysis-debt-docs/26-SECURITY.md | shasum -a 256
8dc326ac23204becce687deeba867740eb2d4dde21346c58d7da9595d137ae2e  -
```

**Post-edit:**

```
$ LC_ALL=C awk 'NR==315{printf "%s", substr($0,1,8693)}' \
    .planning/phases/26-coverage-static-analysis-debt-docs/26-SECURITY.md | shasum -a 256
8dc326ac23204becce687deeba867740eb2d4dde21346c58d7da9595d137ae2e  -
```

```
$ LC_ALL=C awk 'NR==315{print length($0)}' …   # 8813 before, 16191 after — grew, did not shrink
```

The insertion was made by a script that asserts the anchor is unique file-wide, asserts the prefix
length is 8693, asserts the pre-edit digest matches, and asserts `new_row[:8693] == prefix` before
writing. The row was never retyped and the file was never rewritten whole.

### Marker census on row 315

All run against `LC_ALL=C awk 'NR==315{print}' …`, piped — never `grep -c`, which on a single-line
row can only return 0 or 1.

| Check | HEAD | After |
|---|---|---|
| `grep -oE 'AMENDED\|SUPERSEDE[SD]?' \| wc -l` | 1 | **3** |
| `grep -oE 'AMENDED [0-9]{4}-[0-9]{2}-[0-9]{2} by plan 28-06' \| wc -l` | 0 | **1** |
| `grep -o '2026-08-27' \| wc -l` | 1 | **6** |
| `grep -o '2026-08-25' \| wc -l` | 2 | **4** |

The new marker is written in the row's own vocabulary (`**AMENDED 2026-08-27 by plan 28-06 (phase
28) — …**`). No synonym was introduced and no token was manufactured to make a check pass. Because
both markers now carry the SAME DATE, the amendment states explicitly that the plan id is the
discriminator: 2026-08-27 plan 28-03 is withdrawn, 2026-08-27 plan 28-06 is the new one, and the
2026-08-25 measurement is neither.

### Identifiers named as NOT covered

```
$ grep -o -E 'AR-28-01|AR-27-04|AR-27-07|AR-27-10|AR-27-11|WR-01|D-28-06' row315.txt | sort | uniq -c
   2 AR-27-04
   3 AR-27-07
   2 AR-27-10
   2 AR-27-11
   3 AR-28-01
   1 D-28-06
   2 WR-01
```

## `threats_open` recomputation

```
$ awk -F'|' '/^\| T-26-/ { sev=$5; st=$(NF-1); gsub(/[ *`]/,"",sev); gsub(/[ *`]/,"",st);
      if (st != "closed" && (sev == "high" || sev == "critical")) c++ } END { print c+0 }' \
    .planning/phases/26-coverage-static-analysis-debt-docs/26-SECURITY.md
0
```

```
$ grep -c '^| T-26-' …          # rows scanned
46
$ awk … (st == "closed") …      # rows closed
46
```

**Raw output `0`, 46 rows scanned, 46 closed.** The front-matter `threats_open: 0` at line 198 was
therefore NOT edited — the value on disk is already the recomputed value.

`AR-` rows sit OUTSIDE that counter's population at ANY severity: the command scans rows whose id
begins `T-26-` and nothing else. So `AR-27-08` staying OPEN beneath a counter reading `0` is the
documented behaviour of that counter, not a contradiction, and the amended cell says so in its own
sentence rather than leaving a future reader to rediscover it.

## The carrier registry

| Measurement | HEAD | After |
|---|---|---|
| `grep -c 'AccessorSpec('` | 6 | **7** (grew by exactly one) |
| `grep -c 'AiScanCheck'` | 0 | **12** |
| `grep -ci 'repository-wide'` | 0 | **3** |
| `EXPECTED_TOTAL_CARRIER_SITES` | 72 | **74** |
| Files in `MEASURED_CARRIER_SITES` | 11 | **12** |

All three `repository-wide` hits state that such a gate does NOT exist:

- `:76` — "… and NO repository-wide detail-producer gate exists to catch a third producer (D-28-06
  records building one as considered and NOT taken; `WR-01` stays open)."
- `:763` — "… NO REPOSITORY-WIDE DETAIL-PRODUCER GATE EXISTS: WR-01 measured the one this file's own
  gate implied as structurally unable to see another file …"
- `:766` — "… and D-28-06 records building a repository-wide one as CONSIDERED AND NOT TAKEN. Two
  producers are controlled and a third would be caught by nothing."

### The live line, proven against the tree rather than copied

```
$ grep -n 'confirmation.evidence' src/main/kotlin/com/six2dez/burp/aiagent/scanner/ActiveAiScanner.kt
1198:            val evidence = confirmation.evidence.lowercase()
1206:            ScanKnowledgeBase.recordErrorPattern(host, confirmation.evidence.take(100))
1242:                confirmation.evidence,
1269:                        ).let { IssueMarkerSupport.markResponseEvidence(it, confirmation.evidence) }
1302:                    evidence = confirmation.evidence.take(200),
1312:                    detail = confirmation.evidence,
```

`:1242` is the argument passed into `ScannerIssueSupport.buildActiveIssueDetailLines`, which is the
hop the entry describes. `:1246` no longer exists as that site. Prose gates:
`grep -c 'ActiveAiScanner.kt:1246'` → 0, `grep -c 'capped at 80 characters'` → 0,
`grep -c '{60, 80, 80}'` → 1.

### The `.baseValue()` census

```
$ grep -rn 'baseValue()' src/main/kotlin/
src/main/kotlin/com/six2dez/burp/aiagent/scanner/AiScanCheck.kt:116:        val baseValue = insertionPoint.baseValue()
src/main/kotlin/com/six2dez/burp/aiagent/scanner/AiScanCheck.kt:334:     * paragraph. The `**Original Value:**` line IS a measured carrier: `baseValue()` on a
src/main/kotlin/com/six2dez/burp/aiagent/scanner/AiScanCheck.kt:388:**Original Value:** ${sanitizeCookiePointText(insertionPoint, policy, insertionPoint.baseValue(), ScannerIssueSupport.ORIGINAL_VALUE_MAX_CHARS)}
```

Two code sites (`:334` is a KDoc line and carries no `.` before `baseValue`, so the pattern cannot
match it), and no other file in `src/main/kotlin/` calls it. **The plan's measured fact 4 cited
`:114` and `:353`; the live lines after plan 28-05 are `:116` and `:388`.** Re-derived rather than
copied, per the wave-carryover warning that 28-05 already invalidated one plan-era line range.

### Non-vacuity probe — recorded, not assumed

A third `baseValue()` call was added to `AiScanCheck.determineVulnClasses` as a scratch mutation.
`theMeasuredPerFilePerAccessorCountsArePinned` went red with the verbatim message:

```
org.opentest4j.AssertionFailedError: com/six2dez/burp/aiagent/scanner/AiScanCheck.kt no longer carries the pinned accessor counts. The WHOLE per-file map is printed so a drift is diagnosable without re-running the scan by hand.
  pinned:   {insertionPointBaseValue=2}
  measured: {insertionPointBaseValue=3}
If the change is deliberate, re-read the new site's CONSUMER, classify it, and update this count in the same edit — never one without the other. ==> expected: <{insertionPointBaseValue=2}> but was: <{insertionPointBaseValue=3}>
```

Reverted with `git checkout HEAD -- src/main/kotlin/.../AiScanCheck.kt`; `git status --porcelain
src/` then showed only the test file. No mutation is committed. The final
`git diff --stat 5c6b337..HEAD -- src/main/kotlin/` prints nothing.

### Supersession discipline

Nothing was deleted from the two constants the plan names. Prior text still present verbatim:

```
$ grep -c 'TWO CONSUMERS, BOTH READ, AND BOTH NOW CONTROLLED' …                 -> 1
$ grep -c 'UNCONTROLLED, MEASURED 2026-08-25 (AR-27-08, severity MEDIUM)' …     -> 1
$ grep -c 'SUPERSEDED 2026-08-27 (phase 28, plan 28-01)' …                      -> 1
```

`git diff` on both string literals shows the prior lines changed only where a terminating `",`
became `" +` to admit the appended block.

## Verification

| Gate | Result |
|---|---|
| `test --tests 'redact.CookieCarrierInventoryTest'` | **PASS** — all 4 tests, 0 failures, 0 errors |
| `test --tests 'scanner.*' --tests 'redact.*'` | **PASS** — BUILD SUCCESSFUL in 46s |
| `ktlintCheck detekt` (incl. `--rerun-tasks`) | **PASS** — both exit 0 |
| `detekt-baseline.xml` | byte-unchanged (`git status --porcelain` clean) |
| `.planning/REQUIREMENTS.md` | byte-unchanged, PRIV-05 line 23 `- [ ]` |
| `src/main/kotlin/AdaptivePayloadEngine.kt` | byte-unchanged (no main-source diff at all) |

The known `RedactionTest > windowedScanRedactsJsonPairAcrossEveryBoundaryAlignment()` wall-clock
flake did NOT appear in either full-package run, so no isolation re-run was needed.
`./gradlew check` was deliberately NOT run as a gate — it is RED at HEAD for the maintainer-accepted
coverage-floor reason.

## Decisions Made

1. **`stay-open`, on a human maintainer answer.** Recorded in the register cell and here with the
   authority named, and explicitly NOT as an auto-advance default.
2. **The counter was recomputed rather than restated,** and left unedited because the recomputed
   value matched what was on disk. Editing it to "refresh" it would have destroyed the evidence that
   it had not moved.
3. **The scan heuristic was fixed rather than the count pinned around it** (see Deviations). Pinning
   `insertionPointBaseValue=1` would have satisfied every green test in this plan while leaving the
   route-2 carrier line invisible — the exact shape of failure this phase exists to correct, one
   iteration smaller.
4. **The retired singular cap wording was rephrased rather than quoted.** The file's discipline is
   supersession-never-deletion, but a stale bound left verbatim in the text is the string a future
   reader greps and believes, so the correction states what was previously believed without
   reproducing the literal. The plan's acceptance criterion demanded the literal be gone; both are
   satisfied.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] `isCommentOnly` hid the very carrier line the new accessor was added to watch**

- **Found during:** Task 2, first run of `CookieCarrierInventoryTest` after registering the accessor.
- **Issue:** `theMeasuredPerFilePerAccessorCountsArePinned` failed with
  `pinned: {insertionPointBaseValue=2}` vs `measured: {insertionPointBaseValue=1}`. The cause is not
  a wrong pin. `CookieCarrierInventoryTest.isCommentOnly` treated ANY line whose trimmed start is
  `*` as a KDoc continuation. `AiScanCheck.kt:388` — the route-2 carrier — is a line of a Kotlin RAW
  STRING that begins with the markdown bold marker `**Original Value:**`. The scan therefore
  filtered the CARRYING call and saw only the NON-CARRYING one at `:116`.
- **Why this mattered more than the number:** pinning the count at the blind value of `1` would have
  produced a registry entry, a green suite, and a claim in this SUMMARY that route 2 is now
  machine-checked — while the scan remained structurally unable to see it. That is a record wider
  than its control, which is the defect this entire phase-27/28 series exists to correct.
- **Fix:** narrowed the heuristic to genuine block-comment continuations (a bare `*`, `*` followed
  by whitespace, or the block terminator) via a new `isBlockCommentContinuation` helper carrying the
  measurement in its KDoc. A doubled asterisk is markdown, not KDoc.
- **Blast radius, measured not assumed:** no other pinned per-file count moved.
  `theMeasuredPerFilePerAccessorCountsArePinned` cross-checks every pinned file against the live
  scan and `theHeaderValueArgumentMultisetIsPinned` re-derives the `headerValue` argument multiset
  through the same `codeLines` filter; both are green, so the narrowing admitted exactly the two
  intended sites and nothing else.
- **Files modified:** `src/test/kotlin/com/six2dez/burp/aiagent/redact/CookieCarrierInventoryTest.kt`
- **Verification:** all 4 tests green; the red probe above proves the pattern can still fail.
- **Committed in:** `8200b55` (Task 2 commit)

**2. [Rule 1 - Bug] Three plan-era line citations were stale at HEAD**

- **Found during:** Task 2, while writing the registry entries.
- **Issue:** the plan cited `.baseValue()` at `AiScanCheck.kt:114` and `:353` (measured fact 4) and
  `generateContextAwarePayloads` at `ActiveAiScanner.kt:513` and `:709`. The live lines are `:116`,
  `:388`, `:512` and `:707`.
- **Fix:** re-derived every line number against the tree before writing it, per the plan's own
  instruction not to copy its numbers blindly and the wave-carryover note that 28-05 had already
  invalidated one plan-era range. The registry entries cite the live lines.
- **Files modified:** `src/test/kotlin/com/six2dez/burp/aiagent/redact/CookieCarrierInventoryTest.kt`
- **Verification:** `grep -n` output for both symbols pasted above.
- **Committed in:** `8200b55` (Task 2 commit)

**3. [Rule 3 - Blocking] ktlint rejected the new helper's expression-body formatting**

- **Found during:** Task 2, `ktlintCheck`.
- **Issue:** `First line of body expression fits on same line as function signature` at
  `CookieCarrierInventoryTest.kt:265`.
- **Fix:** joined the expression body onto the signature line (172 chars, well under the project's
  `max_line_length = 250`).
- **Files modified:** `src/test/kotlin/com/six2dez/burp/aiagent/redact/CookieCarrierInventoryTest.kt`
- **Verification:** `ktlintCheck detekt --rerun-tasks` — 9 tasks executed, BUILD SUCCESSFUL.
- **Committed in:** `8200b55` (Task 2 commit)

---

**Total deviations:** 3 auto-fixed (2 bugs, 1 blocking).
**Impact on plan:** No scope creep. Deviation 1 is the substantive one — without it this plan would
have shipped a registry entry that measured the wrong site, and a SUMMARY claiming coverage the scan
did not have.

## Known Stubs

None. No hardcoded empty values, placeholder text, skipped tests or unrun `<verify>` blocks were
introduced. Every `<verify>` command in the plan was executed and its output is recorded above.

## Threat Flags

None. This plan modified no main source, added no dependency, and introduced no network endpoint,
auth path, file-access pattern or schema change. `git diff --stat 5c6b337..HEAD -- src/main/kotlin/`
prints nothing.

## Issues Encountered

- **The plan's unique anchor needed its full literal.** `| Measured by plan 27-08` alone is NOT
  unique in `26-SECURITY.md`; the insertion script's first assertion caught it and the script was
  corrected to anchor on `not an omission. | Measured by plan 27-08` (which `grep -c` confirms
  occurs exactly once) and offset to the separator. The prefix boundary is unchanged; the gate
  digest proves it.
- **`26-SECURITY.md` was never read whole.** All access was via `LC_ALL=C awk 'NR==315{substr…}'`
  and bounded `sed -n` ranges, per T-28-32. Line 316 (10400 bytes) was never touched.

## What this round did NOT do, restated so nobody looks for it

- **`AR-27-08` is not closed.** It is OPEN and narrowed.
- **`AR-28-01` is not reopened.** The `Evidence:` line remains a shipping residual by maintainer
  decision at 28-03's checkpoint.
- **`WR-01` is not closed and no repository-wide detail-producer gate was built.** D-28-06 keeps it
  a named residual. Two producers are controlled; a third added tomorrow is caught by nothing.
- **PRIV-05 is not ticked.** `.planning/REQUIREMENTS.md` is byte-unchanged.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

The record now matches the code and is not wider than it. The remaining PRIV-05 work is enumerated
by identifier in the amended `AR-27-08` cell, in `ISSUE_DETAIL_CARRIER_DISPOSITION` and in this
SUMMARY, so a verifier or milestone audit inherits an accurate bound instead of a closure it would
have to re-measure to trust.

The one thing a future round should weigh: `CookieCarrierInventoryTest` is now a tripwire over
SEVEN accessors and still disclaims completeness over four axes, one of which (axis 4) just cost
this phase a whole extra round. Whether the repository-wide producer gate is worth building is the
open question D-28-06 deferred, not one this plan answered.

## Self-Check: PASSED

- `.planning/phases/26-coverage-static-analysis-debt-docs/26-SECURITY.md` — FOUND
- `.planning/phases/28-.../28-03-SUMMARY.md` — FOUND
- `src/test/kotlin/com/six2dez/burp/aiagent/redact/CookieCarrierInventoryTest.kt` — FOUND
- `.planning/phases/28-.../28-06-SUMMARY.md` — FOUND
- Commit `8439894` — FOUND in `git log --all`
- Commit `8200b55` — FOUND in `git log --all`
- `.planning/REQUIREMENTS.md` sha256 re-asserted post-completion:
  `9b3219662ec0d007c1c82d64eed3ef2698bd306ce69f01205ac9bbc3f42fcfb4`, line 23 `- [ ]`
- `STATE.md` and `ROADMAP.md` not modified by this plan (orchestrator-owned)

---
*Phase: 28-the-issue-detail-cookie-carrier-auditissue-detail-scanner-is*
*Completed: 2026-08-27*
