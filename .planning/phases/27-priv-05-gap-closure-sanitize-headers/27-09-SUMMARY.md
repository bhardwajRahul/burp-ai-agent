---
phase: 27-priv-05-gap-closure-sanitize-headers
plan: 09
subsystem: security
tags: [privacy, records, threat-register, roadmap, broken-windows, priv-05, cookies]

requires:
  - phase: 27-priv-05-gap-closure-sanitize-headers
    provides: "27-07's type-keyed predicate + shared sanitizer with its pinned B1-B9 counts; 27-08's carrier inventory (5 accessors / 72 sites / 11 files), its four blind axes, and its TWO MEASURED-NOT-FIXED results (AR-27-07 low, AR-27-08 medium)"
provides:
  - "T-26-02-01 clause (5) — the THIRD refutation, with clauses (1)(2)(3)(4) preserved as an exact byte prefix"
  - "AR-27-06 DEFINED for the first time — the no-backstop bound on both MCP parameter shapes, with an evidence section mirroring AR-27-05"
  - "AR-27-07 and AR-27-08 filed at their MEASURED severities, with the 27-08 register-vs-measurement disagreement recorded rather than resolved silently"
  - "A computed threats_open whose POPULATION is stated for the first time — the bound that made a 0 readable as more than it is"
  - "Standing rule clause (iv) — rendering-keyed versus source-keyed controls, the property all three failed mechanisms shared"
  - "Security Audit Trail row 5; a second append-only milestone-audit correction; the third W-A amendment in CONCERNS.md"
  - "Phase 28 in ROADMAP.md — the NAMED OWNER for AR-27-08 and InjectionPointExtractor.kt:29"
  - "The explicit sentence in the phase 27 ROADMAP record that PRIV-05 is NOT satisfied by this phase"
  - "WINDOWS.md 18 -> 29: eleven falsified premises from waves 7-9, each with its measured evidence"
affects: [28, priv-05, milestone-audit, requirements-owner]

actuals:
  tokens: 30500
  tasks: 3
  commits: 3

tech-stack:
  added: []
  patterns:
    - "State a counter's POPULATION beside the counter: a gate that reads 0 must say what it counts, or 0 means less than a reader will take it for"
    - "Byte-prefix gate on the MITIGATION CELL, not the physical line: appending to a single-line markdown table row necessarily rewrites the line, so intent is verified by the prefix relation on the cell body"
    - "Record the provenance of a severity beside the severity: MEASURED, or AUTHORED by analogy — they do not carry equal weight"

key-files:
  created:
    - .planning/phases/27-priv-05-gap-closure-sanitize-headers/27-09-SUMMARY.md
  modified:
    - .planning/phases/26-coverage-static-analysis-debt-docs/26-SECURITY.md
    - .planning/v0.10.0-MILESTONE-AUDIT.md
    - .planning/codebase/CONCERNS.md
    - .planning/phases/27-priv-05-gap-closure-sanitize-headers/COVERAGE.md
    - .planning/phases/27-priv-05-gap-closure-sanitize-headers/27-HUMAN-UAT.md
    - .planning/WINDOWS.md
    - .planning/ROADMAP.md

key-decisions:
  - "threats_open stays 0 and the counter command is UNCHANGED, because every 27-07/27-08 finding is below the `high` blocking severity — resolved by route (c): state the population and list each finding's severity so the 0 is attributable. Routes (a) and (b) were the plan's options and both were unnecessary; the reasoning is recorded so the question is visibly ASKED"
  - "AR-27-06's MEDIUM is AUTHORED by analogy with AR-27-05, not measured — and it says so, because AR-27-07 and AR-27-08 beside it ARE measured and conflating the two weights would be this file's own error"
  - "AR-27-07 filed at the MEASURED low, against 27-08's authored medium; the disagreement is stated in the register row and the remaining disposition routed to 27-HUMAN-UAT.md test 8"
  - "Successor branch READ from 27-08-SUMMARY.md measurement 2 (REACHABLE), not chosen here — a new ROADMAP phase entry rather than a Backlog-only negative"
  - "STATE.md deliberately NOT edited: all three fields the plan names for it are execution-tracking, and state_head would have recorded a worktree SHA that never exists on the main branch"
  - "ROADMAP plan-progress checkboxes, phase status and the plans counter deliberately NOT edited (orchestrator-owned); the stale counter is recorded as WINDOWS entry 27 instead of being silently left"

patterns-established:
  - "Append-and-amend under a byte-prefix gate, verified in a script rather than by eye"
  - "A deferral is only defensible with a NAMED OWNER in a document a maintainer opens — a register plus a SUMMARY is not one"

requirements-completed: []

coverage:
  - id: D1
    description: "T-26-02-01 carries a fifth clause recording the third refutation, and clauses (1)(2)(3)(4) survive as an exact byte prefix"
    requirement: PRIV-05
    verification:
      - kind: command
        ref: "scratchpad/prefix_gate.js against base 0f75fa6 — old cell body 11231 chars, new 18263, 7032 appended, PREFIX_CHECK = PASS; robust removed-line set for the file is exactly one line (the row itself)"
        status: pass
    human_judgment: false
  - id: D2
    description: "AR-27-06 is defined for the first time with an evidence section mirroring AR-27-05; AR-27-07 and AR-27-08 are filed at their measured severities"
    requirement: PRIV-05
    verification:
      - kind: command
        ref: "grep -c 'AR-27-06' 26-SECURITY.md -> 0 before, non-zero after; new section '## Open findings on the parameter carrier — AR-27-06, AR-27-07 and AR-27-08' present beneath the AR-27-05 evidence section"
        status: pass
    human_judgment: false
  - id: D3
    description: "threats_open is COMPUTED from the rows and the counter's population is stated, with AR-27-08 explicitly accounted for"
    requirement: PRIV-05
    verification:
      - kind: command
        ref: "documented awk command re-run on disk after all edits -> threats_open=0 rows=46 closed=46; frontmatter comment states POPULATION and lists AR-27-06/07/08 + T-27-07-04 with severities"
        status: pass
    human_judgment: false
  - id: D4
    description: "Standing rule clause (iv) names the rendering-versus-source distinction and carries CookieCarrierInventoryTest's four blind axes into the clause itself"
    requirement: PRIV-05
    verification:
      - kind: command
        ref: "grep -n '^\\*\\*(i' 26-SECURITY.md -> four clauses (i)(ii)(iii)(iv); clause (iv) cites 5 accessors / 72 sites / 11 files and enumerates the four axes plus the fifth granularity bound"
        status: pass
    human_judgment: false
  - id: D5
    description: "Every prior narrative survives verbatim; the milestone audit and REQUIREMENTS.md carry zero removed lines"
    verification:
      - kind: command
        ref: "robust form on each file: MILESTONE-AUDIT 0, CONCERNS 0, COVERAGE 0, 27-HUMAN-UAT 0, ROADMAP 0, 26-SECURITY 1 (the T-26-02-01 row, covered by D1); git diff --stat REQUIREMENTS.md -> empty"
        status: pass
    human_judgment: false
  - id: D6
    description: "AR-27-08 and InjectionPointExtractor.kt:29 have a named successor in ROADMAP.md, and the phase 27 record states PRIV-05 is not satisfied"
    requirement: PRIV-05
    verification:
      - kind: command
        ref: "grep -c 'Phase 28' ROADMAP.md -> 0 before, 3 after; 'AR-27-08' 6 hits; 'InjectionPointExtractor' 5 hits; 'PRIV-05 NOT satisfied/SATISFIED' 3 hits (R17 was 0); round-one goal line byte-unchanged (0 removed lines in the file)"
        status: pass
    human_judgment: false
  - id: D7
    description: "The unanswered human items are carried forward legible as unanswered, not auto-approved into the appearance of decisions"
    verification: []
    human_judgment: true
    rationale: "Whether the carry-forward reads as genuinely open to a maintainer — and whether the four new items are the right four — is the judgment this file exists to preserve. No test can assert it. Six items are pending in 27-HUMAN-UAT.md; none was closed by argument here."

duration: 2h 15m
completed: 2026-08-25
status: complete
---

# Phase 27 Plan 09: The Third Refutation, Recorded Instead of Smoothed Over Summary

**T-26-02-01 now records a threat closed wrongly THREE times with the pattern named — all three prior mechanisms keyed on a RENDERING of the data — and phase 27 closes with PRIV-05 stated as UNSATISFIED in the ROADMAP itself, with AR-27-08 and the unconverted predicate owned by a named Phase 28 rather than deferred into two documents nobody opens.**

## Performance

- **Duration:** 2h 15m wall clock across three coordinator interruptions (weekly usage limit, then machine sleep twice); no work lost, no commit amended
- **Started:** 2026-08-25T10:43:41Z
- **Tasks:** 3
- **Files modified:** 7 (1 created, 7 record files touched)

## THE COUNTER-POPULATION QUESTION, AND HOW IT WAS RESOLVED

The sharpest must-have. Recorded first.

**The computed value: `threats_open=0`, over `rows=46 closed=46`.** Produced by re-running the `awk`
command quoted verbatim in `26-SECURITY.md`'s own frontmatter, against the file on disk **after**
every edit in this plan. The written value is that output. It was never hand-typed.

**The population, now stated in the frontmatter comment for the first time:**

> POPULATION: rows of the Threat Register beginning `| T-26-`. Nothing else.
> OUTSIDE IT: every finding recorded ONLY in the Accepted Risks Log — the whole AR-26-\* and
> AR-27-\* series. An `AR-` finding therefore CANNOT move this counter, at ANY severity.

**Where AR-27-08 falls: OUTSIDE the population.** It is an Accepted Risks Log entry, not a register
row. So the counter could not have moved for it whatever its severity — which is exactly the trap
the plan's criterion 4 was written to catch.

**How it was resolved — the plan offered routes (a) and (b); neither was needed, and the reason is
recorded rather than left as a silent `0`.** Criterion 4 says: if any 27-07/27-08 finding is open at
or above the `high` blocking severity, either (a) give it a register ROW inside the population, or
(b) amend the command AND its comment so the population matches the claim. **Neither applies here,
because every finding from those two plans is BELOW `high`:**

| Finding | Severity | Provenance |
|---|---|---|
| AR-27-06 | MEDIUM | **AUTHORED** by analogy with AR-27-05's no-backstop reasoning — not measured, and the record says so |
| AR-27-07 | LOW | **MEASURED** (`27-08-SUMMARY.md` measurement 1, attribution control fired) |
| AR-27-08 | MEDIUM | **MEASURED** (`27-08-SUMMARY.md` measurement 2, positive control fired on the same payload) |
| T-27-07-04 | MEDIUM | Re-measured by plan 27-07 and unchanged |

So the resolution is the criterion's third branch: **state it explicitly, with each severity listed,
so the `0` is attributable rather than merely asserted.** That list is now in the frontmatter comment
above `threats_open`, together with the sentence that makes the bound legible:

> a `0` here means "no OPEN register ROW at or above `high`". It does NOT mean "no open finding at or
> above `high`". Those two sentences differ, and the difference is exactly how a high finding could
> sit open beneath a counter reading 0.

And the honest counterfactual is recorded beside it: had any of the four landed at `high`, route (a)
or (b) would have been mandatory — the counter could not have stayed `0` honestly. Recording that
is what makes the question visibly **asked** rather than skipped.

**AR-27-08 is additionally flagged as the one to watch**, in the audit-trail note and in its own
register row: it is the only finding in the series carrying **Burp-held proxied traffic** rather than
caller-echoed content, it defeats STRICT outright, and it is `medium` ONLY because it is latent
behind three preconditions including an opt-in scanner that defaults to off. That is a bound carried
with the claim, not a softening of it.

## THE BYTE-PREFIX GATE

**PASS.** Re-verified against the worktree base `0f75fa6` after all edits, in a script rather than by
eye (`scratchpad/prefix_gate.js`):

```
old_full_line_chars  = 11320
new_full_line_chars  = 18352
old_cell_body_chars  = 11231
new_cell_body_chars  = 18263
PREFIX_CHECK         = PASS
appended_chars       = 7032
```

**Clauses (1), (2), (3) and (4) survive byte-for-byte as an exact prefix.** No clause text was
altered, reordered, reflowed or re-punctuated; clause (5) is appended to the tail of the mitigation
cell and nothing else moved.

**Why the gate runs on the CELL BODY and not the physical line:** the whole T-26-02-01 row is ONE
physical markdown line (`WINDOWS.md` entry 17), so appending a clause necessarily rewrites the line
and it shows as a removed line. The prefix relation on the mitigation cell is the invariant that
actually encodes "no prior clause was touched". This matches 27-06's own read-back, which recorded
`5,633 → 11,320 chars` — and **11,320 is exactly the old value measured here**, so the two rounds
agree.

**Baseline R2 discrepancy, recorded rather than reconciled away.** The plan's R2 states the row's
"character length" as **11400**. That is what `wc -c` reports — a **BYTE** count. The **character**
count is **11320**, because the row carries multi-byte UTF-8 (em dashes, ellipses, curly quotes).
Both numbers are correct measurements of different things; the plan conflated the units. Logged as
`WINDOWS.md` entry 28.

**Robust removed-line set for `26-SECURITY.md`: exactly ONE line — the T-26-02-01 row itself**,
covered by the gate above. Measured with
`git diff HEAD --unified=0 -- <file> | grep '^-' | grep -v '^--- '`. The naive
`grep -c '^-[^-]'` form was used nowhere.

## Removed-line set for every touched file (ROBUST form throughout)

| File | Removed lines | Note |
|---|---|---|
| `26-SECURITY.md` | **1** | the T-26-02-01 row; byte-prefix PASS above |
| `v0.10.0-MILESTONE-AUDIT.md` | **0** | R9 held; genuinely append-only |
| `codebase/CONCERNS.md` | **0** | better than amendment 2, which needed a bullet replacement (entry 18) |
| `COVERAGE.md` | **0** | declaration byte-unchanged |
| `27-HUMAN-UAT.md` | **0** | criterion 1 held |
| `ROADMAP.md` | **0** | see below — stronger than criterion 5 required |
| `REQUIREMENTS.md` | **0 removed, 0 added** | untouched, asserted |

`ROADMAP.md` deserves the note: criterion 5 PERMITS removed lines inside the phase 27 section and the
two progress rows. **There are none at all** — every ROADMAP edit is an addition. The round-one goal
line is therefore trivially byte-unchanged, and it still reads, at what is now `ROADMAP.md:393`
(it was `:392` before this plan's single index-line insertion — the plan's citation was correct
pre-edit):

> `Custom-AI-Agent-full-1.0.0.jar`. Closing this makes PRIV-05's "by any path" wording true and

It is qualified in place by the appended not-satisfied paragraph, never rewritten.

## THE SUCCESSOR BRANCH — READ, NOT CHOSEN

**Branch taken: REACHABLE.** Read from `27-08-SUMMARY.md` measurement 2, quoted:

> **YES. A cookie value embedded in the `Original Value:` detail line SURVIVES `Redaction.apply` on
> the serialized `IssueDetails` shape, in STRICT and in BALANCED alike.** It is emitted verbatim.

and, from its source-cited reachability table:

> | Can a COOKIE-typed injection point reach that line? | **YES.** `extractInjectionPoints` returns
> every point from `InjectionPointExtractor.extract`, and the target loop filters on vuln CLASS only
> — never on `point.type`. |

**So the REACHABLE branch's deliverable was required: a new ROADMAP phase entry.** Opened as
**Phase 28 — The Issue-Detail Cookie Carrier (`AuditIssue.detail()` → `scanner_issues`)**, carrying a
goal that names the carrier, `**Requirements**: PRIV-05`, `**Depends on:** Phase 27`, six success
criteria, and two Backlog lines.

**All three required tokens are present and were grepped:**

| Token | Before (R16 / baseline) | After |
|---|---|---|
| `Phase 28` | **0** | **3** |
| `AR-27-08` | 0 in a successor context | **6** |
| `InjectionPointExtractor` | 0 | **5** |
| sentences stating PRIV-05 NOT satisfied (R17) | **0** | **3** |

**Both halves of the deferral are named, because a successor that names the route but not the
predicate leaves half of it unowned.** The Phase 28 entry states why they are closed together:
`InjectionPointExtractor.kt:29`'s two consumers differ — `AdaptivePayloadEngine.kt:52` is CONTROLLED
(substitutes `[REDACTED_VALUE]` under any non-`OFF` mode) while `ActiveAiScanner.kt:1239` is
UNCONTROLLED and is the finding — so **converting the predicate alone would produce a tidier file and
an unchanged leak**. The deferral reason is stated too: 27-08's `T-27-08-06` was dispositioned
TRANSFER, not mitigate, and a fix without its own red probe and reachability analysis is the
same-day closure pattern that has now failed three times.

## Clause-by-clause read-back — what each added claim is BOUNDED to

Required by task 1 criterion 9 and task 2 criterion 7. **No sentence added anywhere by this plan
asserts that PRIV-05 is satisfied or that the cookie class is covered.**

| Claim added | Bounded to |
|---|---|
| Clause (5) closure | "the MCP PARAMETER CARRIER — the serialized `ParsedParam` shape emitted by `request_parse` and the `params_extract` line shape, in both executors — plus the bounty-prompt `parameters` tag, for the COOKIE-TYPED parameter class, at no wider scope", with the three things it does NOT cover named in the same clause |
| Clause (5) mitigating property | caller-echoed content on `request_parse`/`params_extract` only; stated as a property of the carrier, explicitly **not** as a reason it should have been left open |
| AR-27-06 | a **no-backstop BOUND, not a live leak** — all four producers route through the sanitizer today; what is open is the absence of a SECOND control. Severity marked AUTHORED by analogy |
| AR-27-06's producer-ownership pin | "a SOURCE SCAN, not a behavioural proof" — the division of labour is stated so a green suite is not read as covering both questions |
| AR-27-07 | the measured `low`, on the caller-echo property, outside PRIV-05's cookie wording; the authored-vs-measured disagreement stated in the row |
| AR-27-08 | medium with BOTH properties in one breath — Burp-held traffic and defeats STRICT (aggravating), latent behind three named preconditions (mitigating); "not high because unreachable in the default posture, not low because when reachable a real session cookie crosses the trust boundary in STRICT" |
| Standing rule (iv) | the four blind axes plus the fifth granularity bound carried INTO the clause; `CookieCarrierInventoryTest` described as "a TRIPWIRE over a measured accessor set and NOT a proof of coverage" |
| Milestone-audit correction | the same closure scope, plus the new observation — a record can be accurate clause by clause and still leave a requirement overstated if nobody re-derives it |
| CONCERNS W-A amendment 3 | W-A's original predicate reasoning "is STILL CORRECT, AND IT WAS NEVER THE QUESTION ON THIS CARRIER" — a parameter has no header name to match |

**Severity provenance, every value read from a SUMMARY line:** AR-27-07 `low` from
`27-08-SUMMARY.md` "**SEVERITY: LOW**, with its reasoning and the mitigating property named";
AR-27-08 `medium` from "**SEVERITY: MEDIUM**, with the aggravating and mitigating properties named in
the same breath"; T-27-07-04 `medium` from `27-07-SUMMARY.md` "**T-27-07-04 stays `medium`**". The
caller-echo property appears wherever an MCP-tool severity appears, and the Burp-held-data property
wherever the issue-detail route appears.

**Prohibited-phrase check.** The two-word tree-wide scope phrase was constructed in the shell
(`PH="whole"" ""codebase"`) rather than pasted, and `grep -ic "$PH"` returns **0** on all seven
touched files. A second search for negated/reworded forms
(`entire (source tree|codebase|repository)|across the codebase|tree-wide|no cookie value can reach|PRIV-05 is (now )?(satisfied|true)|cookie class is (now )?covered|fully covered|exhaustively`)
returned two hits in `26-SECURITY.md`, **both pre-existing and neither mine**: line 496 (27-06's
read-back item 6, describing the prohibition) and line 549 (standing rule (ii)'s worked example,
describing the historical error). Claim bounded to those two searches over the seven files.

## WINDOWS.md — 18 → 29, eleven entries, each with MEASURED evidence

Added with `gsd-tools windows append`, so frontmatter, table and JSON block stay consistent
(`open_count: 29`, `total_count: 29`, 29 table rows — all three agree).

| id | One-line description |
|---|---|
| 19 | 27-07 task 1: the plan mandates a `@Nested` layout its OWN criterion 1 makes unsatisfiable (separate XML per nested class). Measured after flattening: `tests=18 failures=0`, all names present |
| 20 | 27-07 task 1 criterion 5 unsatisfiable: behavioural probes cannot reach the production branch — `HttpRequest.httpRequest()` needs Burp's `ObjectFactory`. Ownership pin pulled forward |
| 21 | 27-07 task 2 criterion 5 predicted BOTH probe and pin red; **measured only the pin** — 1 of 18, twice, with the assertion message quoted |
| 22 | 27-08 task 1 repeats the identical `@Nested` conflict — a SECOND occurrence after the first was logged. Measured `tests=25 failures=0` |
| 23 | 27-08 task 2: path-keyed registry falsified — **6 of 11** carrier files have accessors with different dispositions; re-keyed on a `(file, accessor)` pair |
| 24 | 27-08 baseline C4: **+50 line shift** in `Redaction.kt` from 27-07's merge — and the same shift silently rotted clause (3)'s citations |
| 25 | 27-08 red probe A: criterion asked for assertion (1); **measured `failures=2`** — assertion (2) also fired on its FILES limb |
| 26 | 27-08's authored `medium` for T-27-08-07 falsified by its own measurement: **LOW** on the caller-echo property |
| 27 | 27-09 baseline R13: the phase 27 plans counter still reads two contradictory figures (`8/9 plans executed — 6 executed, 3 planned`) against **9** PLAN files on disk |
| 28 | 27-09 baseline R2 conflates BYTES with CHARACTERS: 11400 bytes vs **11320 characters** |
| 29 | 27-09 baseline R4 conflates APPEARANCE with DEFINITION: `AR-27-06`/`AR-27-08` already appeared under `.planning/` before this plan, though none was DEFINED |

Entries 27, 28 and 29 are this plan's own baselines failing against the tree — recorded rather than
quietly corrected, which is the discipline the ledger exists for.

## Task Commits

1. **Task 1: 26-SECURITY.md — clause (5), AR-27-06/07/08, the counter's population, standing rule (iv)** — `9b8e431` (docs)
2. **Task 2: milestone audit, CONCERNS, COVERAGE** — `57f0061` (docs)
3. **Task 3: human-UAT carry-forward, WINDOWS, the successor and the not-satisfied sentence** — `ab4276a` (docs)

## Deviations from Plan

### 1. [Orchestrator constraint] ROADMAP execution-tracking fields NOT edited

- **Found during:** Task 3
- **Issue:** The plan's task 3 directs the executor to tick `27-07`/`27-08`/`27-09`, confirm the
  phase 27 plans counter, and correct the progress-table rows for phases 26 and 27. The dispatching
  orchestrator's instruction explicitly reserves plan-progress checkboxes, phase status, plan counts
  and Current Position to itself and states it will overwrite them after the executor returns.
- **Resolution:** The **record-repair** half of task 3 was done in full — the successor, the Backlog
  lines and the not-satisfied sentence, which are criteria 9, 10 and 11 and the task's stated
  required deliverables. The tracking half was withheld. **The stale counter was not silently
  dropped:** it is measured and recorded as `WINDOWS.md` entry 27, so the contradiction is visible to
  whichever owner acts next rather than lost between them.
- **Bearing on criterion 6:** that criterion ("exactly ONE plans counter, matching the PLAN files on
  disk") is **NOT satisfied** and could not be satisfied by this executor. Reported as an open item
  below rather than papered over.

### 2. [Orchestrator constraint + truthfulness] STATE.md NOT edited at all

- **Found during:** Task 3
- **Issue:** The plan directs a refresh of `state_head`, `last_activity_desc` and `stopped_at`. All
  three are execution/session-tracking fields the orchestrator owns.
- **Second, independent reason — this one is about accuracy, not obedience:** `state_head` is
  specified as "the current head at the time of the edit". Inside a worktree that is the per-agent
  branch head, **a SHA that never exists on the main branch**. Writing it would put a
  permanently-unresolvable reference into the project's own state file — a plausible value standing
  in for a true one, which `<record_repair_discipline>` rule 3 forbids.
- **Resolution:** Left untouched; reported as an open item.

### 3. [Rule 1 - accuracy] Declined the plan's "three lines below" in favour of the cited artifact

- **Found during:** Task 1
- **Issue:** The plan says the `request_parse` leak "sat three lines below `sanitizeHeaders`".
  `27-VERIFICATION-2.md`'s artifacts record `sanitizeHeaders` at `McpToolExecutorImpl.kt:369` and the
  unguarded emission at `:371-373` — i.e. two lines after. Two sources, two numbers.
- **Fix:** Clause (5) states the geometry with the **cited line numbers** and the artifact reference,
  and avoids the disputed count entirely: "in the SAME JSON object as the control, immediately below
  it — `headers = sanitizeHeaders(…)` at `McpToolExecutorImpl.kt:369`, the unguarded … emission at
  `:371-373`". The rhetorical point (the control defeated on its own output) is unaffected.

### 4. [Judgment call] Phase 28 added to the top-of-file `## Phases` index

- **Found during:** Task 3
- **Issue:** Criterion 9 requires a phase ENTRY; it does not mention the top-of-file checklist. That
  checklist holds per-phase checkboxes, which sit near the orchestrator's reserved territory.
- **Decision:** Added, as a single `- [ ]` line. Creating the index line for a **brand-new** phase is
  registering the successor, not modifying tracking state for an existing one, and a phase present in
  Phase Details but absent from the index is half-registered — which is the failure mode this whole
  deliverable exists to prevent. Verified as a single-line insertion by `diff` before applying.
  Flagged here so the orchestrator can reverse it if it disagrees.

---

**Total deviations:** 4 — two forced by the orchestrator's ownership constraint (both reported as
open items rather than faked), one accuracy correction against the plan's own prose, one judgment
call flagged for review. **No scope added, no claim widened, no plan decision reopened.**

## Open items — things this plan could NOT repair truthfully from evidence on disk

Stated as open items rather than filled with plausible values.

1. **`ROADMAP.md`'s phase 27 plans counter remains self-contradictory.** It reads
   `8/9 plans executed — 6 executed, 3 planned` against **9** PLAN files on disk. Task 3 criterion 6
   is unmet. Owner: the execute-phase orchestrator. Recorded as `WINDOWS.md` entry 27.
2. **`STATE.md` is stale** (`state_head`, `last_activity_desc`, `stopped_at`). Owner: the
   orchestrator. Writing a worktree SHA would have been a false value, not a repair.
3. **The `- [x] **PRIV-05**` tick at `REQUIREMENTS.md:23` is wrong for the third time and was NOT
   corrected** — deliberately, under a standing prohibition. `REQUIREMENTS.md` has 0 added and 0
   removed lines. It is the milestone owner's to re-derive **from the clauses of T-26-02-01**, not
   from any sentence phase 27 wrote about itself. Correcting it here — in either direction — would be
   a phase grading its own homework, which is the exact artifact this closure is repairing. The
   prohibition is honoured and stated in `26-SECURITY.md`, the milestone audit and `ROADMAP.md`.
4. **`T-27-06-06` is still unactioned** (`README.md:247`, `SPEC.md:80,86` overclaim STRICT host
   anonymisation). `.planning/BACKLOG.md` still does not exist (re-measured). Now visible in three
   places instead of two: `26-SECURITY.md`, `27-HUMAN-UAT.md` test 4, and a new `ROADMAP.md` Backlog
   line. A user-facing docs change is outside a record-repair plan's scope.
5. **Six human items are pending and none was closed by argument** — `27-HUMAN-UAT.md` tests 1, 2
   (pre-existing), 3, 4, 5, 6, 7, 8. The file states plainly that they are unanswered and **why no
   checkpoint was raised**: `mode: yolo` auto-selects blocking checkpoints, and `26-SECURITY.md`
   already records one disposition (AR-27-04) as auto-selected rather than maintainer-chosen. The
   absence of checkpoints in waves 7-9 is therefore **not** evidence of an absence of open questions.

## Issues Encountered

**Three coordinator interruptions** — a weekly usage limit, then machine sleep twice. No work was
lost, no commit amended, no task restarted. The task-3 commit landed immediately before the third
interruption. Both sharp must-haves were re-verified against the bytes on disk after the second
interruption, before the task-3 commit, rather than trusted from stale in-context memory.

**`gsd-tools windows append` echoes the entire ledger** on every call, which is heavy for eleven
entries; `--pick entry.id` was used from entry 20 onward.

**`cp` is aliased to `cp -i` in this shell**, so the ROADMAP index-line splice silently refused the
first time. Re-run as `/bin/cp -f`, with the single-line insertion confirmed by `diff` beforehand.

## Known Stubs

**None.** No stub, placeholder, skipped test or unrun `<verify>` was introduced. Every `<verify>`
block in the plan was executed and its output is recorded above. This plan writes no code.

**`WINDOWS.md` entries:** eleven were appended (ids 19-29), covering every falsified premise found in
waves 7-9 — including three of this plan's own baselines.

## User Setup Required

None — no external service configuration required.

## Next Phase Readiness

**Phase 27 is complete with PRIV-05 NOT satisfied, deliberately and with an owner.** That sentence is
now in the ROADMAP phase record itself (R17: 0 → 3), not only in a SUMMARY.

**Ready for Phase 28**, which owns `AR-27-08` and `InjectionPointExtractor.kt:29` together and can
consume from `27-08-SUMMARY.md` and `26-SECURITY.md` without re-deriving anything: the measured
result, the firing positive control, the mechanism, the source-cited reachability chain, and six
stated success criteria including the `ResponseAnalyzer` evidence tail that reaches the same
`AuditIssue` detail.

**For the milestone owner:** the `REQUIREMENTS.md` PRIV-05 tick awaits re-derivation from
T-26-02-01's five clauses. `26-SECURITY.md`, `v0.10.0-MILESTONE-AUDIT.md` and `ROADMAP.md` all now
say so independently.

**For the orchestrator:** two tracking repairs are outstanding (open items 1 and 2).

## Self-Check: PASSED

- `.planning/phases/27-priv-05-gap-closure-sanitize-headers/27-09-SUMMARY.md` — created by this write
- All seven modified record files — FOUND on disk, all changes committed, working tree clean before
  this write
- Commits `9b8e431`, `57f0061`, `ab4276a` — all FOUND in `git log` on
  `worktree-agent-acf9db5ca8f124ac7`
- Byte-prefix gate re-run against base `0f75fa6` after all edits — **PASS**
- `threats_open` recomputed from the rows on disk after all edits — **0, over 46 rows, 46 closed**
- `REQUIREMENTS.md` — `git diff --stat` empty; untouched
- `STATE.md` — untouched (open item 2)

---
*Phase: 27-priv-05-gap-closure-sanitize-headers*
*Completed: 2026-08-25*
