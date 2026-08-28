---
phase: 21-redaction-completeness
plan: 20
subsystem: planning-records
tags: [record-accuracy, attribution, gap-closure, priv-05, cookie-headers, roadmap, state]

requires:
  - phase: 21-redaction-completeness
    provides: "21-VERIFICATION.md (re-verified 2026-08-27, gaps_found, 8/9) — G-1, G-2, G-3 are this plan's input"
  - phase: 27-priv-05-sanitize-headers-mirror
    provides: "the actual closure of the W-A class (27-01, 27-04/27-11/27-14/27-17, 27-10) that the records mis-attributed to 21-19"
provides:
  - "CONCERNS.md:65 attributes the W-A class closure to Phase 27, with residuals (a)/(b)/(c) by plan id and (c)'s admitter-vs-redactor mechanism stated"
  - "21-19-SUMMARY.md's two closure claims amended to their measured scope under one dated marker, originals preserved"
  - "21-19-PLAN.md restored to ROADMAP.md as a Third gap-closure round; phase-21 plan count 18 -> 19"
  - "The record omission itself preserved as a cause, in both ROADMAP.md and STATE.md"
  - "G-3 disposed of by re-measurement (redact BRANCH 0.93299 vs 0.930 floor, PASS) rather than left as an undisposed BLOCKER"
affects: [phase-28-issue-detail-cookie-carrier, v0.10.0-milestone-audit, future-redaction-rule-authors]

actuals:
  tokens: 52485
  tasks: 3
  commits: 3

tech-stack:
  added: []
  patterns:
    - "Append-and-amend on planning records: prior wording is preserved verbatim under a dated marker naming what it supersedes and what it does NOT"
    - "A record correction amends the claim in place rather than appending a fresh amendment round that restates the prior ones"
    - "Diff gates in a per-task-commit plan are pinned to a BASE SHA captured before task 1; an unpinned git diff passes vacuously after the first commit"

key-files:
  created:
    - .planning/phases/21-redaction-completeness/21-20-SUMMARY.md
  modified:
    - .planning/codebase/CONCERNS.md
    - .planning/phases/21-redaction-completeness/21-19-SUMMARY.md
    - .planning/ROADMAP.md
    - .planning/STATE.md

key-decisions:
  - "W-A is attributed by CARRIER, not by class: 21-19 closed the passive-scan prompt carrier for hyphenated name shapes; Phase 27 closed the class"
  - "Residual (c) is recorded with its mechanism, not as a bookkeeping slip: sanitizeHeadersForPrompt is an admitter, so the admitter-vs-redactor difference set was fail-OPEN"
  - "The record omission (21-19 absent from ROADMAP/STATE) is preserved as a stated cause rather than silently repaired"
  - "PRIV-05 stays unticked and REQUIREMENTS.md byte-unchanged: AR-27-08 is open and owned by Phase 28, so 'by any path' is still stronger than what ships"
  - "21-19-SUMMARY.md's overstated `requirements-completed: [PRIV-05]` frontmatter is noted in prose but deliberately left as written, because the file is a record of what was believed on 2026-08-13"
  - "G-3 recorded as resolved by measurement, not by decision — one branch was covered between the verification and now"

patterns-established:
  - "Correcting a record must state what is NOT being corrected, or the reader infers the underlying work was defective"
  - "A residual's MECHANISM is the load-bearing part of an attribution; reducing it to 'three residuals remained' loses the reason the class reopened"

requirements-completed: []

coverage:
  - id: D1
    description: "CONCERNS.md:65 attributes the W-A class closure to Phase 27, with residuals (a)/(b)/(c) by plan id and (c)'s admitter-vs-redactor mechanism stated"
    requirement: "PRIV-05"
    verification:
      - kind: other
        ref: "grep -c '27-01' .planning/codebase/CONCERNS.md => 2; '27-10' => 3; 'admitter' => 3"
        status: pass
      - kind: other
        ref: "git diff --numstat $BASE -- .planning/codebase/CONCERNS.md => 2 additions / 1 deletion (additions > deletions)"
        status: pass
      - kind: other
        ref: "grep -c 'AMENDMENT' .planning/codebase/CONCERNS.md => 8, unchanged from pre-edit 8 (no seventh amendment block appended)"
        status: pass
    human_judgment: false
  - id: D2
    description: "21-19-SUMMARY.md's two closure claims (line 50 one-liner, the '**NAME** class is **closed**' claim) amended to their measured scope under one dated marker, both originals preserved"
    requirement: "PRIV-05"
    verification:
      - kind: other
        ref: "grep -c 'Phase 27|27-01|27-10' => 6; 'prompt carrier|prompt path' => 2; 'hyphen' => 3"
        status: pass
      - kind: other
        ref: "git diff --numstat $BASE => 46 additions / 0 deletions; both original claims still grep-present"
        status: pass
    human_judgment: false
  - id: D3
    description: "21-19-PLAN.md restored to ROADMAP.md and reflected in STATE.md; phase-21 plan count corrected 18 -> 19; the omission recorded as a cause"
    verification:
      - kind: other
        ref: "grep -c '21-19' ROADMAP.md => 4 (was 0); STATE.md => 2 (was 0); '**Plans**: 18 plans' => 0; '**Plans**: 19 plans' => 1"
        status: pass
    human_judgment: false
  - id: D4
    description: "G-3 disposed of by re-measurement: redact BRANCH missed 13 / covered 181 = 0.93299 vs the 0.930 floor (PASS); LINE 0.97978 vs 0.975 (PASS)"
    verification:
      - kind: other
        ref: "parsed build/reports/jacoco/test/jacocoTestReport.xml package com/six2dez/burp/aiagent/redact aggregate counters"
        status: pass
    human_judgment: false
  - id: D5
    description: "The plan's non-goals held: 21-VERIFICATION.md and REQUIREMENTS.md byte-unchanged, PRIV-05 still unticked, zero changes under src/"
    verification:
      - kind: other
        ref: "shasum -a 256 .planning/REQUIREMENTS.md => 9b3219662ec0d007c1c82d64eed3ef2698bd306ce69f01205ac9bbc3f42fcfb4 (run twice); git diff --stat $BASE HEAD -- 21-VERIFICATION.md => empty; git status --porcelain -- src/ => empty"
        status: pass
    human_judgment: false
  - id: D6
    description: "The corrections read as a record repair, not as an indictment of phase 21's redaction work — every edit states that SC1-SC6 remain 6/6 and that the code is not what was overstated"
    verification: []
    human_judgment: true
    rationale: "Whether the amended prose leaves a reader with the correct impression of phase 21's soundness is an editorial judgment no grep asserts. The mechanical facts (6/6 cited in both records) are verified; the reading is not."

duration: 14min
completed: 2026-08-28
status: complete
---

# Phase 21 Plan 20: Record Accuracy Repair (G-1, G-2, G-3) Summary

**Phase 21's account of itself now matches what phase 21 shipped: 21-19 closed the passive-scan prompt carrier for hyphenated cookie header names, Phase 27 closed the class, and the plan that did the former is finally in the project record that should have caught the difference.**

## Performance

- **Duration:** ~14 min
- **Started:** 2026-08-28T11:50:00Z (approx.)
- **Completed:** 2026-08-28T12:05:00Z
- **Tasks:** 3/3
- **Files modified:** 4 (+1 created)

## Accomplishments

- **G-1 closed at both sites.** `CONCERNS.md:65` no longer claims the cookie header NAME *class* was closed on 2026-08-13. It now states that plan 21-19 closed the passive-scan **prompt carrier** for **hyphenated** name shapes, and attributes the three residuals to Phase 27 by plan id — (a) `McpToolHelpers.sanitizeHeaders`'s exact-name comparison to **27-01**, (b) `Serialization.kt`'s CR/LF escaping defeating the `(?im)^` anchor to **27-04 / 27-11 / 27-14 / 27-17**, (c) `COOKIE_NAME_PART` excluding `_` to **27-10**. `21-19-SUMMARY.md`'s matching claims are amended under one dated marker.
- **Residual (c) recorded with its mechanism.** Both records state that `sanitizeHeadersForPrompt` is an **admitter** — a name it admits that the regex cannot match reaches the outbound prompt and is then not removed — so the difference set was fail-**OPEN**: the same admitter-vs-redactor asymmetry W-A itself was, reintroduced one character wide by the fix for it. That is why (c) is the sharpest of the three: 21-19 did not fully close W-A even on the single path W-A was about, and `cookieHeaderNameVariantsAreStripped` could not see it because all five fixtures use hyphens.
- **G-2 closed, with the cause preserved.** `21-19-PLAN.md` is now in `ROADMAP.md` under a *Third gap-closure round / Gap-3 Wave 1* heading, the plan count reads 19, and both `ROADMAP.md` and `STATE.md` record *why* the entry was missing: 21-19 was executed, committed and summarised but never entered in the plan record, so nothing re-verified it — which is why a fix covering one of two carriers and five of six name shapes stood as a complete closure for twelve days, surfaced only at the v0.10.0 milestone audit, and became Phase 27's five rounds of rework.
- **G-3 disposed of by measurement, not left dangling.** Re-parsed from `build/reports/jacoco/test/jacocoTestReport.xml`: redact BRANCH **missed 13 / covered 181 = 0.93299** against the `0.930` floor (**PASS**), LINE **0.97978** against `0.975` (**PASS**). The verification's `0.92784` snapshot has been overtaken — one branch was covered between the two measurements, and the standing build note it contradicted was right.
- **The 6/6 left standing.** Every edit states explicitly that `21-VERIFICATION.md` re-scored SC1–SC6 at 6/6 and that the redaction work "has survived a substantial downstream rewrite without a single guard going red". What was corrected is the claim layered on top, not the code.

## Task Commits

Each task was committed atomically:

1. **Task 1: Correct the attribution in CONCERNS.md** — `152cab5` (docs)
2. **Task 2: Correct 21-19-SUMMARY's own closure claim** — `9100a9e` (docs)
3. **Task 3: Restore 21-19 to the project record** — `78fd66f` (docs)

**Plan metadata:** see the `docs(21-20): complete record-accuracy repair plan` commit.

## Files Created/Modified

- `.planning/codebase/CONCERNS.md` — the W-A headline at line 65 amended **in place** (prior wording preserved verbatim inside it, quoted as the superseded claim), plus one non-amendment record note. +2 / −1.
- `.planning/phases/21-redaction-completeness/21-19-SUMMARY.md` — one dated CORRECTION marker after the one-liner covering both overstated claims, plus a pointer at the §Record Correction site. +46 / −0, pure append.
- `.planning/ROADMAP.md` — plan count 18 → 19 with the wave breakdown updated; *Third gap-closure round* heading, *Gap-3 Wave 1*, the `21-19-PLAN.md` entry, and the omission recorded as a cause. +16 / −1.
- `.planning/STATE.md` — dated Roadmap Evolution entry for the repair, plus two Phase 21 decisions (carrier-level attribution; why this does not tick PRIV-05). +3 / −0.
- `.planning/phases/21-redaction-completeness/21-20-SUMMARY.md` — this file.

## Decisions Made

- **Amend the headline in place; do not open an eighth amendment round.** `CONCERNS.md` already carries six Phase-27 amendment blocks under the W-A entry. A seventh restating them would bury the correction and duplicate the record. The line-65 headline was rewritten in place with the original opening claim preserved verbatim as a quoted, explicitly-superseded sentence, and the whole 2026-08-13 body left untouched below it. `grep -c 'AMENDMENT'` is **8 before and 8 after**.
- **One record-note line was added beneath the amended headline.** The plan's `git diff --numstat` gate requires additions > deletions on `CONCERNS.md`, and an in-place single-line amendment is exactly +1/−1. The added line is a pointer, not an amendment round: it names the correction, states that it is deliberately *not* an eighth amendment, and explicitly does not restate the six below. Result: +2/−1, gate satisfied, and the "do not append a seventh AMENDMENT block" instruction honoured in substance (uppercase `AMENDMENT` count unchanged).
- **`21-19-SUMMARY.md`'s frontmatter left as written.** Its `requirements-completed: [PRIV-05]` is overstated on the same ground as its prose claims. It is noted in the correction marker but deliberately not edited: the file is a record of what was believed on 2026-08-13, and rewriting the machine-readable field would destroy the evidence while `REQUIREMENTS.md` — the authoritative record — already reads `- [ ]`.
- **Diff gates pinned to a captured BASE.** `BASE=f5003d0` was captured before task 1, because this plan commits per task and an unpinned `git diff` measures nothing after the first commit and would have passed vacuously.
- **`./gradlew check` not run.** Per the plan's prohibition: it is RED for an environmental reason unrelated to this phase (`McpSupervisorProbeTest` → `java.net.BindException: Address already in use`, a live Burp MCP server holding the port). This plan changes no code, so no build signal was warranted; the G-3 number was taken from the existing JaCoCo report and independently re-parsed rather than assumed from the plan text.

## Deviations from Plan

None — plan executed exactly as written.

The one judgment call worth flagging is not a deviation but a reading of a constraint: the plan required both "amend the headline, do not append a seventh AMENDMENT block" **and** "additions > deletions". Those are jointly unsatisfiable by a pure in-place single-line edit. Resolved by adding one clearly-labelled record-note line that does not restate the prior amendments and does not raise the `AMENDMENT` count, which satisfies both the letter of the numstat gate and the intent of the no-new-round rule.

**Total deviations:** 0.
**Impact on plan:** none — all four `must_haves` artifacts produced, all prohibitions held.

## Verification Results

**REQUIREMENTS gate — run 1 (in-task, task 3):**

```
9b3219662ec0d007c1c82d64eed3ef2698bd306ce69f01205ac9bbc3f42fcfb4  .planning/REQUIREMENTS.md
- [ ] **PRIV-05** (Finding 2, **high**): Cookie values do not reach an AI backend ...
git status --porcelain .planning/REQUIREMENTS.md  ->  (empty)
sed -n '23p' ... | grep -c '^- \[ \] \*\*PRIV-05\*\*'  ->  1
```

**REQUIREMENTS gate — run 2 (immediately before the final commit):**

```
9b3219662ec0d007c1c82d64eed3ef2698bd306ce69f01205ac9bbc3f42fcfb4  .planning/REQUIREMENTS.md
- [ ] **PRIV-05** (Finding 2, **high**): Cookie values do ...
git status --porcelain .planning/REQUIREMENTS.md   ->  (empty)
git diff --stat $BASE HEAD -- .planning/REQUIREMENTS.md  ->  (empty)
git status --porcelain -- src/                     ->  (empty)
```

Both runs identical to the sealed sha. The second run is not redundant: `phase.complete` has silently ticked an unsatisfied requirement twice in this project (`WINDOWS.md` 54 and 57), always after the last task, so a single in-task run is structurally blind to it.

**Per-task gates:**

| Gate | Expected | Measured |
|---|---|---|
| `grep -c 'AMENDMENT'` CONCERNS.md | ≤ pre-edit + 1 (pre-edit 8) | **8** |
| `grep -c '27-01'` / `'27-10'` / `'admitter'` CONCERNS.md | ≥ 1 each | 2 / 3 / 3 |
| numstat CONCERNS.md vs BASE | additions > deletions | **+2 / −1** |
| numstat 21-19-SUMMARY.md vs BASE | additions > deletions | **+46 / −0** |
| `grep -c 'Phase 27\|27-01\|27-10'` 21-19-SUMMARY | ≥ 1 | 6 |
| `grep -c 'prompt carrier\|prompt path'` 21-19-SUMMARY | ≥ 1 | 2 |
| `grep -c 'hyphen'` 21-19-SUMMARY | ≥ 1 | 3 |
| `grep -c '21-19'` ROADMAP.md | ≥ 2 (was 0) | **4** |
| `grep -c '21-19'` STATE.md | ≥ 1 (was 0) | **2** |
| `grep -c '**Plans**: 18 plans'` | 0 | 0 |
| `grep -c '**Plans**: 19 plans'` | 1 | 1 |
| `git diff --stat $BASE HEAD -- 21-VERIFICATION.md` | empty | empty |
| `git status --porcelain -- src/` (every commit) | empty | empty |

**G-3 re-measurement** (parsed from `build/reports/jacoco/test/jacocoTestReport.xml`, package `com/six2dez/burp/aiagent/redact`, report written 2026-08-28 13:33):

```
BRANCH: missed 13 / covered 181 = 0.93299   floor 0.930   PASS
LINE:   missed  9 / covered 436 = 0.97978   floor 0.975   PASS
```

## Issues Encountered

None. No auth gate, no architectural decision, no verification failure.

`./gradlew check` was deliberately not run (see Decisions). Its known RED — `McpSupervisorProbeTest` failing with `java.net.BindException: Address already in use` because a live Burp MCP server holds the port — is environmental and is **not** a phase-21 finding.

## Known Stubs

None. This plan changed no code, added no test, and introduced no placeholder, TODO or FIXME.

## Threat Flags

None. No source file was touched; no endpoint, auth path, file-access pattern or trust-boundary schema changed. `git status --porcelain -- src/` was empty at every commit.

## User Setup Required

None — no external service configuration required.

## Next Phase Readiness

- **G-1 and G-2 are closed on the artifact side; a later re-verification decides whether they are closed in fact.** This plan deliberately did not touch `21-VERIFICATION.md` — a plan does not re-score the verification that found its gaps.
- **G-3 needs no work.** It is resolved by measurement against the sealed QUAL-06 floor, recorded above.
- **PRIV-05 remains open and correctly unticked.** `AR-27-08` (a COOKIE-typed value reaching `scanner_issues` via `AuditIssue.detail()`) is open and owned by Phase 28, which accepted a further residual (`D-28-09`) rather than closing it. Nothing in this plan moves that.
- **Phase 21's real deferred debt is 2 open items** (`D-21-02` retry-ladder capability ceiling, `D-21-03` boundary-sweep deadline exposure), unchanged by this plan and unchanged by Phase 27.

## Self-Check: PASSED

- `.planning/codebase/CONCERNS.md` — FOUND (modified)
- `.planning/phases/21-redaction-completeness/21-19-SUMMARY.md` — FOUND (modified)
- `.planning/ROADMAP.md` — FOUND (modified)
- `.planning/STATE.md` — FOUND (modified)
- `.planning/phases/21-redaction-completeness/21-20-SUMMARY.md` — FOUND (created)
- Commits `152cab5`, `9100a9e`, `78fd66f` — all FOUND in `git log`
- No file deletions in any commit (`git diff --diff-filter=D HEAD~1 HEAD` empty for all three)
- `21-VERIFICATION.md` and `.planning/REQUIREMENTS.md` byte-unchanged; zero changes under `src/`

---
*Phase: 21-redaction-completeness*
*Completed: 2026-08-28*
