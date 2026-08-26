---
phase: 27-priv-05-gap-closure-sanitize-headers
plan: 13
subsystem: security
tags: [security-register, threat-model, accepted-risks, record-repair, priv-05, broken-windows]

requires:
  - phase: 27-priv-05-gap-closure-sanitize-headers (plan 27-10)
    provides: "The widened COOKIE_NAME_PART, the per-name red-probe output, and NOT_COVERED_TCHARS — the measured partition AR-27-10 is filed from"
  - phase: 27-priv-05-gap-closure-sanitize-headers (plan 27-11)
    provides: "JSON_STRING_OPEN, the red probe with its positive control firing in the same run, and the re-measured indented-header residual AR-27-09 is filed from"
  - phase: 27-priv-05-gap-closure-sanitize-headers (plan 27-12)
    provides: "The deletion of the two green STRICT host pins, the OFF byte-identity replacement, and RedactingPolicySurvivalSweepTest with its measured bound — the demonstrated gate standing-rule clause (vi) cites"
provides:
  - "T-26-02-01 clause (6) — the FOURTH refutation recorded with clauses (1)-(5) surviving as an exact CHARACTER prefix"
  - "AR-27-09 and AR-27-10 — two numbered, owned, severity-assigned findings, each evidenced from a round-4 MEASUREMENT rather than a projection"
  - "A recomputed threats_open with its POPULATION restated AND the at-or-above-the-gate question answered explicitly rather than left to inference"
  - "Standing-rule clauses (v) consumer polarity and (vi) green tests as evidence, the latter carrying the enforcing sweep's vocabulary bound in the same clause"
  - "The round-4 provenance record: two MAINTAINER-MADE decisions, explicitly distinguishable from AR-27-04's harness-auto-selected disposition"
  - "Eleven WINDOWS ledger entries, one per premise round 4 falsified, each carrying its OBSERVED value"
affects: [phase-28, priv-05, AR-27-04, AR-27-08, AR-27-09, AR-27-10, security-register-maintenance]

actuals:
  tokens: 47515   # chars/4 over the realized diff (190,061 chars across 6 record files)
  tasks: 3
  commits: 4

tech-stack:
  added: []
  patterns:
    - "Anchored splice with a character-prefix gate: a one-physical-line register row is amended by locating a known prefix and suffix and appending to the cell body, never by retyping the row, with the old body asserted to be an exact CHARACTER prefix of the new one"
    - "Symbol-first citation: durable symbol names carry the claim and measured line numbers are dated, because every prior clause's .kt:NNN citations have rotted"
    - "Severity with its provenance split: the measured half and the inferred half of a severity are stated separately rather than averaged into one number"

key-files:
  created:
    - .planning/phases/27-priv-05-gap-closure-sanitize-headers/27-13-SUMMARY.md
  modified:
    - .planning/phases/26-coverage-static-analysis-debt-docs/26-SECURITY.md
    - .planning/phases/27-priv-05-gap-closure-sanitize-headers/27-HUMAN-UAT.md
    - .planning/codebase/CONCERNS.md
    - .planning/WINDOWS.md
    - .planning/ROADMAP.md
    - .planning/phases/27-priv-05-gap-closure-sanitize-headers/COVERAGE.md

key-decisions:
  - "AR-27-09 filed at LOW on the measured two-mode survival PLUS the mitigating fact that no measured emission site indents a header line — with the unmeasured reachability through analyst-authored notes text named as the reason it stays OPEN rather than closed."
  - "AR-27-10 filed at LOW with its severity SPLIT: the partition and the mechanism are measured, the carry-over to the other thirteen characters is INFERRED and labelled so. No leak was measured for any of the thirteen and the row does not claim one."
  - "Standing-rule clauses (v) and (vi) added TOGETHER, because round 4 broke in two ways where rounds 1-3 broke in one — clause (iv) already names the shared pattern of the first three."
  - "STATE.md left UNTOUCHED: every field task 3 part C names is orchestrator-owned in worktree mode, and both halves of the task's own criterion already held on disk."
  - "COVERAGE.md written from a MEASURED symbol inventory that contradicts the plan's projection, rather than from the projection."

patterns-established:
  - "A width claim about a shared predicate must enumerate its consumers and label each redactor or admitter"
  - "A green test may never stand in for a measurement, and never pins a survival under a redacting policy"

requirements-completed: []  # DELIBERATE AND LOAD-BEARING. This plan closes NO requirement.
                            # PRIV-05 stays `[ ]`: AR-27-08 (the issue-detail carrier) is owned by
                            # Phase 28 and is untouched here. REQUIREMENTS.md diff verified EMPTY.

coverage:
  - id: D1
    description: "T-26-02-01 carries clause (6) recording the FOURTH refutation on the ADMITTER-POLARITY and JSON-STRING-OPEN axes, and clauses (1) through (5) survive as an exact CHARACTER prefix of the amended row"
    requirement: "PRIV-05"
    verification:
      - kind: other
        ref: "anchored splice with an in-script character-prefix assertion: cell body 18263 -> 29410 chars, PREFIX CHECK PASS; robust removed-line form isolates the single rewritten row"
        status: pass
    human_judgment: false
  - id: D2
    description: "AR-27-09 and AR-27-10 exist as OPEN rows with a severity, a named owner and evidence quoted from a round-4 SUMMARY; the Accepted Risks Log holds exactly ten AR-27 rows"
    requirement: "PRIV-05"
    verification:
      - kind: other
        ref: "grep -c '^| AR-27-' 26-SECURITY.md == 10; grep -c 'AR-27-09' == 12; grep -c 'AR-27-10' == 11"
        status: pass
    human_judgment: false
  - id: D3
    description: "threats_open is recomputed from the register rows by the documented awk command after every other edit, the written value equals that output, and the counter's population is restated with the at-or-above-the-gate question answered explicitly"
    requirement: "PRIV-05"
    verification:
      - kind: other
        ref: "documented awk command re-run post-edit: raw output 0; companion awk: rows_scanned=46 rows_closed=46; frontmatter value reads 0"
        status: pass
    human_judgment: false
  - id: D4
    description: "27-HUMAN-UAT.md carries nine outstanding items legible as unanswered, the two round-4 decisions as MAINTAINER-MADE with the AR-27-04 contrast written out, and two new dispositions — under a zero-removed-lines rule with the frontmatter byte-unchanged"
    verification:
      - kind: other
        ref: "robust removed-line count == 0; frontmatter updated:/source: byte-unchanged; grep -c AR-27-09 == 3, AR-27-10 == 2"
        status: pass
    human_judgment: false
  - id: D5
    description: "One WINDOWS ledger entry per premise round 4 falsified, each carrying its observed number or string"
    verification:
      - kind: other
        ref: "gsd-tools windows status: open_count and total_count both 29 -> 40, +11 across three appends, matching the 11 entries written"
        status: pass
    human_judgment: false
  - id: D6
    description: "ROADMAP records round 4 without rewriting round 1, names all six residuals with owners, still says PRIV-05 is not satisfied, and carries exactly one plans counter"
    verification:
      - kind: other
        ref: "grep -c '27-1[0123]-PLAN.md' == 4; round-1 goal line removed-line grep == 0; 'PRIV-05 NOT SATISFIED' == 1; AR-id grep == 16; counter figure count == 1"
        status: pass
    human_judgment: false
  - id: D7
    description: "PRIV-05 remains unsatisfied and unticked, and no record written by this plan claims otherwise"
    requirement: "PRIV-05"
    verification:
      - kind: other
        ref: "git diff <base>..HEAD -- .planning/REQUIREMENTS.md robust removed-line count == 0; REQUIREMENTS.md:23 reads '- [ ] **PRIV-05**'"
        status: pass
    human_judgment: false
  - id: D8
    description: "The amended prose actually SAYS the true thing — that the severities are honest, the bounds are carried with their claims, and no sentence implies a completeness the round does not have"
    verification: []
    human_judgment: true
    rationale: >-
      Every mechanical gate above proves a symbol is present, a count matches, or a line was not
      removed. Not one of them can tell whether clause (6) states the fourth refutation without
      softening it, whether AR-27-10's LOW is defensible once its inferred half is exposed, or
      whether the six-residual paragraph reads as a completeness claim to someone who did not write
      it. This entire phase exists because a plausible-sounding paragraph passed four audits while
      being false for one consumer in three. A maintainer must READ the amended passages.

duration: ~55 min
completed: 2026-08-26
status: complete
---

# Phase 27 Plan 13: Records, Fourth Time Summary

**T-26-02-01 now carries clause (6) — the fourth wrong closure, on CONSUMER POLARITY and the JSON-string-open start — with clauses (1) through (5) surviving as an exact character prefix; this round's two residuals are filed as `AR-27-09` and `AR-27-10` from measurements rather than projections; the counter is recomputed with its population restated and the blocking-severity question answered out loud; and the standing rule gains the two clauses the first four could not have carried.**

## PHASE 27 ROUND 4 DOES NOT CLOSE PRIV-05

Stated first, in its own sentence, because a SUMMARY that read as a closure would be the fifth
iteration of the pattern these four rounds exist to break.

Three carriers and two boundary axes are now closed. **`AR-27-08` — the transitive issue-detail
carrier, the one finding in this series carrying Burp-held proxied traffic and surviving STRICT — is
untouched by round 4 and is owned by Phase 28**, together with the unconverted cookie-type predicate
at `scanner/InjectionPointExtractor.kt:29`. Four further residuals are named with owners. **PRIV-05
is not satisfied, `REQUIREMENTS.md` was not touched, and PRIV-05 remains `[ ]`.**

## Performance

- **Duration:** ~55 min
- **Started:** 2026-08-26T11:05:00Z (approximate — required reading and the pre-edit measurements preceded the first commit at 11:26Z)
- **Completed:** 2026-08-26T12:00:00Z
- **Tasks:** 3
- **Files modified:** 6 (all record files; **zero files under `src/`**)

## Task Commits

1. **Task 1 (tracer): the register — clause (6), two findings, recomputed counter, clauses (v) and (vi)** — `e42ee7e` (docs)
2. **Task 2: the human items, the concerns amendment, the ledger entries** — `a43bfd8` (docs)
3. **Task 3: the roadmap, the coverage declaration, the state** — `0827537` (docs)

Task 1 is `type="tracer"`. Its feedback gate was run immediately after its commit: the full
`<verify>` block was re-executed end-to-end against the committed tree and passed, so the expansion
tasks proceeded. No expansion work was started before that gate cleared.

---

## THE REQUIRED VERBATIM RECORD

### 1. The T-26-02-01 prefix check — measured in CHARACTERS, explicitly NOT in bytes

`WINDOWS.md` entry 28 records a prior round of this phase quoting a `wc -c` byte count as a
character count. Both units are recorded here so the two can never be confused again, and the
**gate was run on CHARACTERS**.

| Quantity | Value |
|---|---|
| Mitigation cell body BEFORE, **characters** | **18,263** |
| Mitigation cell body AFTER, **characters** | **29,410** |
| Appended, **characters** | **11,147** |
| **PREFIX CHECK (characters)** | **PASS** |
| Cell body BEFORE, bytes (recorded only to name the unit) | 18,397 |
| Cell body AFTER, bytes (recorded only to name the unit) | 29,600 |

**The convention, stated because "row body" is ambiguous and a later round must not re-derive it.**
The row is ONE physical markdown line, so appending a clause necessarily rewrites the whole line and
the row itself appears as a removed line. The gate is therefore applied to the **MITIGATION CELL
BODY** — the text between the literal prefix
`| T-26-02-01 | Information Disclosure | \`sanitizeHeaders\` | high | mitigate | ` and the literal
suffix ` | closed |`. This is the same convention 27-06 used (5,633 → 11,320) and 27-09 used
(11,231 → 18,263). **18,263 is exactly the figure `WINDOWS.md` entry 28 recorded as the post-27-09
value, so the two rounds agree once the units are named.**

The splice was performed by an anchored script that locates the prefix and suffix and appends to the
body. **No existing clause text was read back, retyped, reflowed, re-punctuated or reordered**, and
the script refuses to write if the prefix assertion fails.

The same gate was applied to the `AR-27-04` rationale cell (Part C): **627 → 2,567 characters, 1,940
appended, PREFIX CHECK PASS.**

### 2. The recomputed `threats_open`, with its rows scanned and closed

The `awk` command written in the file's own frontmatter comment was run **verbatim and AFTER every
other edit in task 1**:

```
awk -F'|' '/^\| T-26-/ { sev=$5; st=$(NF-1); gsub(/[ *`]/,"",sev); gsub(/[ *`]/,"",st);
    if (st != "closed" && (sev == "high" || sev == "critical")) c++ } END { print c+0 }' \
  .planning/phases/26-coverage-static-analysis-debt-docs/26-SECURITY.md
```

**RAW OUTPUT: `0`**

Companion count over the same population: **rows scanned = 46, rows closed = 46.**

**The value written in the frontmatter is `0`, and it equals that output.** It was not carried
across from the 2026-08-25 run. No register row was added, amended or reclassified by plans 27-10,
27-11, 27-12 or 27-13; the one row this round touched, `T-26-02-01`, gained clause (6) and changed
neither its severity (`high`) nor its status (`closed`), so it cannot move the count either way.

**THE POPULATION, AND THE QUESTION IT FORCES — ANSWERED EXPLICITLY, NOT LEFT TO INFERENCE.** The
population is register rows beginning `| T-26-`; every `AR-` finding sits outside it at ANY
severity. **Both findings opened this round are `AR-` rows, and BOTH ARE `LOW` — neither is at or
above the `high` blocking gate.** Therefore **NO REMEDY WAS REQUIRED AND NONE WAS APPLIED**: the
`awk` command is unamended, its population definition is unamended, and neither finding was given a
`T-26-` id inside the population. Had either landed at `high`, the honest options were exactly two —
give it a register row inside the population, or amend the command and its comment together — and
leaving the counter reading `0` was not among them. That statement is in the frontmatter comment,
not only here, because "below the gate" is a conclusion a reader must be able to CHECK.

A table-integrity gate was also run, since a stray unescaped `|` in a new cell would split a row and
move a `closed` status out of the field the counter reads: Threat Register rows measure 7 columns,
Accepted Risks rows 5, Audit Trail rows 5 — all correct, including the `AR-27-10` row whose evidence
contains the vertical-bar tchar itself (escaped `\|`).

### 3. The two severities, each with the measurement it was derived from

| Finding | Severity | Derived from |
|---|---|---|
| **`AR-27-09`** — the leading-whitespace / obs-folded logical-line start | **LOW** | **`27-11-SUMMARY.md`, "The Indented-Header Measurement"** — a throwaway `jshell` harness against the freshly compiled classes. `GET / HTTP/1.1\r\n Cookie: a=SECRET5\r\n\r\n` survives **BYTE-UNCHANGED under STRICT *and* BALANCED**, with the un-indented control and the JSON-string-open fix both stripped to `Cookie: [STRIPPED]` on the same run. `LOW` because **no measured emission site in this repository indents a header line** (the 14 pinned sites emit at column 0, and `buildScanMetadataText` `appendLine`s at column 0). OPEN rather than closed because reachability through analyst-authored `HttpRequestResponse.notes` text is **UNMEASURED**. |
| **`AR-27-10`** — the RFC 9110 tchars outside the widened cookie name class | **LOW** | **`27-10-SUMMARY.md` §6**, the covered class read out of `Redaction.kt` at test time rather than re-typed: `ALL_RFC9110_TCHARS` **77**, `COVERED_TCHARS` **64**, `NOT_COVERED_TCHARS` **13** — ``!#$%&'*+.^`|~``. The **partition** and the **fail-open mechanism** are measured (the mechanism end-to-end on `_`, nine names, both modes, §3). The **carry-over to the other thirteen characters is INFERRED** and is labelled as inferred in the row. **NO LEAK WAS MEASURED FOR ANY OF THE THIRTEEN and neither the row nor this SUMMARY claims one.** |

> **[SUPERSEDED 2026-08-26 by plan 27-17 — `AR-27-09` is CLOSED BY FIX, not open at LOW.]** The
> statement above is preserved byte-for-byte as the record this plan made; none of it is withdrawn.
> The LOW rested on an explicitly UNMEASURED reachability claim, so the maintainer decided the
> finding by FIX rather than by acceptance at UAT (`27-HUMAN-UAT.md` item 10, commit `ae3371a`).
> `Redaction.logicalLineHeaderRule`'s REAL-LINE branch now starts at `REAL_LINE_START = "^[ \t]*+"`
> instead of a bare `^`, so an indented header line and an obs-folded continuation line ARE
> recognised, in STRICT and BALANCED, across all three composed rules. Measured before/after, the
> consuming-vs-zero-width hazard, the falsified variable-width-lookbehind premise and the two-way
> mutation proof are in the **"AR-27-09 — CLOSED BY FIX 2026-08-26"** section of
> `.planning/phases/26-coverage-static-analysis-debt-docs/26-SECURITY.md`. Gated by
> `IndentedLogicalLineStartTest` and `LogicalLineBoundaryScopeTest`. **PRIV-05 is still `[ ]`**;
> `AR-27-10` and `AR-27-11` are still open. `AR-27-09` is an `AR-` row and was always outside the
> `threats_open` population, which was recomputed and is unchanged at `0`.

**One mode wider than the round-3 record, recorded rather than smoothed.** `27-VERIFICATION-3.md`
carried the `AR-27-09` residual as surviving under STRICT. Plan 27-11 re-measured instead of copying
the prediction forward and found it surviving under **STRICT and BALANCED**. The register row, the
frontmatter comment, the ROADMAP paragraph and `27-HUMAN-UAT.md` all carry the **wider measured**
value. Understating a residual is the same failure mode as overclaiming a fix.

> **[SUPERSEDED 2026-08-26 by plan 27-17 — `AR-27-09` is CLOSED BY FIX, not open at LOW.]** The
> statement above is preserved byte-for-byte as the record this plan made; none of it is withdrawn.
> The LOW rested on an explicitly UNMEASURED reachability claim, so the maintainer decided the
> finding by FIX rather than by acceptance at UAT (`27-HUMAN-UAT.md` item 10, commit `ae3371a`).
> `Redaction.logicalLineHeaderRule`'s REAL-LINE branch now starts at `REAL_LINE_START = "^[ \t]*+"`
> instead of a bare `^`, so an indented header line and an obs-folded continuation line ARE
> recognised, in STRICT and BALANCED, across all three composed rules. Measured before/after, the
> consuming-vs-zero-width hazard, the falsified variable-width-lookbehind premise and the two-way
> mutation proof are in the **"AR-27-09 — CLOSED BY FIX 2026-08-26"** section of
> `.planning/phases/26-coverage-static-analysis-debt-docs/26-SECURITY.md`. Gated by
> `IndentedLogicalLineStartTest` and `LogicalLineBoundaryScopeTest`. **PRIV-05 is still `[ ]`**;
> `AR-27-10` and `AR-27-11` are still open. `AR-27-09` is an `AR-` row and was always outside the
> `threats_open` population, which was recomputed and is unchanged at `0`.

**Owners.** Both are owned by **the maintainer**, as disposition items 10 and 11 of the round-4
carry-forward section of `27-HUMAN-UAT.md` — they are judgments (accept, or apply a written-down
fix), not deferred implementation work. That is deliberately a different owner-shape from
`AR-27-08`, which is owned by **Phase 28** because a fix there needs its own red probe and
reachability analysis. The contrast is stated in the `AR-27-09` row itself.

### 4. Robust removed-line counts for all four record files

Measured with the robust form (`WINDOWS.md` entry 18: the naive `'^-[^-]'` filter is a FALSE ZERO on
markdown bullet lines), against the worktree base `66c965d`:

| File | Robust removed-line count | Verdict |
|---|---|---|
| `.planning/phases/27-priv-05-gap-closure-sanitize-headers/27-HUMAN-UAT.md` | **0** | Required 0 — **PASS** |
| `.planning/REQUIREMENTS.md` | **0** | Required 0 — **PASS** |
| `.planning/codebase/CONCERNS.md` | **0** | Required 0 — **PASS** |
| `.planning/phases/26-coverage-static-analysis-debt-docs/26-SECURITY.md` | **2** | Both deliberate, named below |

**THE TWO REMOVED LINES IN THE REGISTER, NAMED. There are no others.**

1. `| T-26-02-01 | Information Disclosure | \`sanitizeHeaders\` | high | mi…` — the T-26-02-01 row,
   rewritten in place because it is ONE physical line and clause (6) is appended to its cell body.
   Gated by the character-prefix assertion in §1 (18,263 → 29,410, PASS).
2. `| AR-27-04 | T-26-02-01 | **NEW, OPEN, severity MEDIUM.** The \`Host:\`…` — the AR-27-04 row,
   rewritten in place for the same structural reason. Gated by the same assertion (627 → 2,567,
   PASS).

**A removed line not on this list would be a failure of task 1. There is none.**

**The 2026-08-24 reopening narrative is untouched** — and the check needs its units named too. The
plan's criterion is `git diff HEAD -- <file> | grep -c 'Reopening — 2026-08-24'` returning `0`.
**Observed: `1`, and that `1` is a CONTEXT line, not an added or removed one.** Plain `git diff`
emits three lines of context, so any insertion within three lines of that heading prints it. Both
precise forms return **`0`**: filtering the diff to `+`/`-` lines returns 0, and
`git diff --unified=0` returns 0. The heading appears on no added and no removed line, which is what
the criterion exists to prove. Standing-rule clauses (v) and (vi) were deliberately anchored INSIDE
the standing-rule section (after clause (iv)'s last line) rather than above that heading — which is
also the structurally correct placement. The gate defect is filed as a ledger entry in the same
family as entry 18.

### 5. The `WINDOWS.md` counters, before and after

| Point | `open_count` | `total_count` | `waived` | `fixed` |
|---|---|---|---|---|
| BEFORE (start of this plan) | **29** | **29** | 0 | 0 |
| After task 2 (**9** entries appended) | **38** | **38** | 0 | 0 |
| After task 3 (**2** further entries) | **40** | **40** | 0 | 0 |

**Both counters increased by exactly the number of entries appended, at each of the three appends
(+9, +1, +1 = +11).** New ids are **30 through 40**. Every entry went in through the CLI so the
markdown table and the JSON block at the foot stay consistent; the new rows were checked to carry
the same 10-column shape as pre-existing row 29.

**Every description carries an OBSERVED number or string, never a predicted one** — an entry
recording a prediction is the defect entry 26 already records for this phase. The eleven, each with
its observed value:

| id | Falsified premise | Observed value it carries |
|---|---|---|
| 30 | 27-10: the "WHICH GUARD COVERS WHICH MUTATION" block says "THIS test" | **THREE** tests in the file after the rename, not one |
| 31 | 27-10 task 2 criterion requires all three underscore names; the test hardcoded one | **1 of 3** asserted on; resolved with `EXPECTED_UNDERSCORE_NAMES = 3`, corpus **19** vs floor 18 |
| 32 | 27-11 task 1 wrote the `AR-27-09` residual as STRICT-only | Survives byte-unchanged under **STRICT and BALANCED** |
| 33 | 27-11 task 2 asked for a sibling field after `notes` on `HttpRequestResponse` | `HttpRequestResponse` declares `notes` **LAST**; moved to `IssueDetails` |
| 34 | T-26-02-01 line citations rotted again, in clauses (4) and (5) too | `isCookieHeaderName` **:391** (clause 5 says :293); admitter **:197** (clause 4 says :186); `JSON_STRING_OPEN` **:277** (27-11 recorded :271 pre-merge) |
| 35 | 27-12 projected `BENIGN_ACCESSORS` accounts for 5 | **7** live functions |
| 36 | 27-12 projected the unqualified vocabulary reports 7 | **9** |
| 37 | 27-12 `T-27-12-09` predicted 2 self-hits on a mock | **5** self-hits on the real file; **0** after the fix, **5** with the skip removed |
| 38 | 27-13 criterion 11 over-counts diff CONTEXT lines | Observed **1**; both precise forms **0** |
| 39 | 27-13 part B projected the MCP tool names appear "repeatedly" | **ZERO** across all four plan bodies; `Montoya` **1** and `API` **3**, all inside `27-13-PLAN.md` |
| 40 | 27-13 part C directs the executor to write orchestrator-owned STATE.md fields | Criterion already holds untouched: `Plan: 1 of 13`, `status: executing` |

### 6. `REQUIREMENTS.md` was NOT touched, and PRIV-05 remains unticked

**`git diff 66c965d..HEAD -- .planning/REQUIREMENTS.md` has a robust removed-line count of `0` and
adds nothing.** `REQUIREMENTS.md:23` reads, unchanged:

```
- [ ] **PRIV-05** (Finding 2, **high**): Cookie values do not reach an AI backend in STRICT or
  BALANCED mode by any path. …
```

The milestone owner had already reverted the tick that clause (5) called wrong for the third time —
which is the outcome the `ROADMAP.md` tick paragraph asked for. That paragraph was **kept and
appended to**, recording the correction, rather than deleted: a paragraph that produced a correction
is worth more on the record than a silence where the correction used to be needed.

---

## Accomplishments

- **Clause (6) records the fourth wrong closure with every prior narrative intact**, its honest
  attribution stated (the underscore leak was PRE-EXISTING — phase 27 neither introduced nor widened
  it; what it did was mis-frame it and pin it green), and the changed symbols named.
- **The two residuals this round opens are numbered, owned, severity-assigned and evidenced from
  MEASUREMENTS**, with a new evidence section carrying the verbatim probe output. `AR-27-10` is
  explicitly the record the underscore class never had: for three rounds the identical residual
  lived in a source comment and a green test and appeared in no security record under `.planning/`.
- **The counter is computed, its population restated, and the blocking-severity question answered
  out loud.** Nothing high hides beneath a `0`.
- **Standing-rule clauses (v) and (vi)** name the two lessons rounds 1-3 could not have taught:
  polarity qualifies every width claim, and a green test is not a measurement.
- **Round 4's two decisions are on the record as human-made and distinguishable from a harness
  default** — with the honest note that the ROADMAP sentence is NOT independent corroboration,
  because plan 27-13 writes both.
- **A fourth `W-A` amendment scoped to exactly what changed**, with its point (4) stating that the
  vendor auth-header class did NOT move.

## Decisions Made

- **`AR-27-09` and `AR-27-10` are both `LOW`, and both stay OPEN.** Filing either at `medium` would
  have overstated it; closing either would have hidden an unmeasured axis. The severity paragraphs
  state the aggravating property, the mitigating property, and — for each — the specific thing that
  is NOT measured and would change the answer.
- **`AR-27-10`'s severity is split rather than averaged.** The measured half (partition, mechanism)
  and the inferred half (carry-over to thirteen characters) are stated separately. Averaging them
  into one confident number is how `AR-27-06`'s "authored by analogy" severity nearly went unlabelled.
- **Owners are people for `AR-27-09`/`AR-27-10` and a phase for `AR-27-08`**, and the difference is
  explained in the rows rather than left as an inconsistency.
- **Clause (6) names symbols first and line numbers second**, and records that clauses (3), (4) AND
  (5) have all now rotted — without editing any of them.
- **`STATE.md` was left untouched.** See "Deviations".
- **`COVERAGE.md` was written from the measurement, not the instruction.** See "Deviations".

> **[SUPERSEDED 2026-08-26 by plan 27-17 — `AR-27-09` is CLOSED BY FIX, not open at LOW.]** The
> statement above is preserved byte-for-byte as the record this plan made; none of it is withdrawn.
> The LOW rested on an explicitly UNMEASURED reachability claim, so the maintainer decided the
> finding by FIX rather than by acceptance at UAT (`27-HUMAN-UAT.md` item 10, commit `ae3371a`).
> `Redaction.logicalLineHeaderRule`'s REAL-LINE branch now starts at `REAL_LINE_START = "^[ \t]*+"`
> instead of a bare `^`, so an indented header line and an obs-folded continuation line ARE
> recognised, in STRICT and BALANCED, across all three composed rules. Measured before/after, the
> consuming-vs-zero-width hazard, the falsified variable-width-lookbehind premise and the two-way
> mutation proof are in the **"AR-27-09 — CLOSED BY FIX 2026-08-26"** section of
> `.planning/phases/26-coverage-static-analysis-debt-docs/26-SECURITY.md`. Gated by
> `IndentedLogicalLineStartTest` and `LogicalLineBoundaryScopeTest`. **PRIV-05 is still `[ ]`**;
> `AR-27-10` and `AR-27-11` are still open. `AR-27-09` is an `AR-` row and was always outside the
> `threats_open` population, which was recomputed and is unchanged at `0`.

## Deviations from Plan

### 1. [Rule 1 — Bug] Task 3 part B's symbol projection is false of the plan bodies on disk

- **Found during:** Task 3, while gathering the symbols the extension was to name.
- **Issue:** Part B states the four round-4 plan bodies "name the passive-scan prompt path, the MCP
  tool result shapes and the Montoya host API **repeatedly**". **MEASURED with `grep -ohc` per file:
  the MCP tool NAMES — `request_parse`, `response_parse`, `params_extract`, `scanner_issues`,
  `proxy_http_history`, `proxy_http_history_regex`, `site_map`, `site_map_regex`, the exact tokens
  that made this COVERAGE declaration necessary for plans 27-07/27-08 — appear ZERO times in all
  four.** `Montoya` appears **once** and `API` **three times on two lines**, and every one of those
  is inside `27-13-PLAN.md`, one of them being the instruction sentence itself. What IS present:
  `HttpRequestResponse` ×6 (27-11 only), `toolJson.encodeToString` ×3 (27-12 only), `ParsedRequest`
  ×2, and `SiteMapEntry` / `McpToolContext.redactIfNeeded` / `AuditIssue.detail()` once each.
- **Fix:** The extension records the MEASURED per-file inventory as a table and states the
  divergence in place. Writing "repeatedly" would have filed a projection as a measurement — the
  defect ledger entries 26 and 29 already record for this phase, in the one file whose whole job
  this round is to stop doing that.
- **Impact on the declaration:** none, and it is now better supported. The detector has strictly
  LESS to trip on across these four bodies than across 27-07 to 27-09.
- **Committed in:** `0827537`. **Ledger entry 39.**

### 2. [Rule 3 — Blocking] Task 3 part C directs the executor to write orchestrator-owned STATE.md fields

- **Found during:** Task 3.
- **Issue:** Part C names `last_activity`, `last_activity_desc`, the Current Position block and the
  progress counters. **All four are execution-tracking fields the execute-phase orchestrator owns
  and overwrites in worktree mode**, and `execute-plan.md`'s `update_current_position` step
  explicitly says to skip them under a worktree. This is ledger entry 27's two-owner class recurring
  for `STATE.md` instead of the ROADMAP counter. The plan's own part A anticipates exactly this
  shape and instructs: record the contradiction rather than fight it.
- **Fix:** `STATE.md` was left **UNTOUCHED**. **Both halves of the task's own acceptance criterion
  already hold on disk with no edit:** Current Position reads `Plan: 1 of 13` — and there are
  exactly 13 `27-*-PLAN.md` files — and nothing claims the phase is verified (`status: executing`,
  `Phase: 27 … — EXECUTING`; the file's only two `verified` strings concern phase 16 and an
  unrelated coverage note). The next artifact for this phase is `27-VERIFICATION-4.md`.
- **Committed in:** `0827537` (ledger only). **Ledger entry 40.**

### 3. [Rule 1 — Bug] Task 1 acceptance criterion 11 counts diff CONTEXT lines

- **Found during:** Task 1, on the first application of the splices.
- **Issue:** The criterion is `git diff HEAD -- <file> | grep -c 'Reopening — 2026-08-24'` returning
  `0`, intended (per 27-06's read-back) to prove the heading is on no added and no removed line.
  Plain `git diff` emits three lines of context. **Observed `1` — a context line.**
- **Fix:** The first application had placed clauses (v)/(vi) immediately above that heading, which
  was also the wrong place structurally. The file was reverted with a path-scoped
  `git checkout -- <file>` and re-applied with the clauses anchored INSIDE the standing-rule
  section, after clause (iv)'s last line. The residual `1` is still a context line; **both precise
  forms return `0`.** Same family as ledger entry 18: a gate whose naive form lies.
- **Committed in:** `e42ee7e`. **Ledger entry 38.**

---

**Total deviations:** 3 (2 bugs in the plan's own premises/criteria, 1 blocking ownership conflict).
**Impact on plan:** No scope creep and no weakening. All three were resolved by recording the
measurement rather than by adjusting a gate, an inventory or a number to make it agree.

## Issues Encountered

- **No `.planning/BACKLOG.md` exists** — re-checked 2026-08-26, still absent. `T-27-06-06`'s
  visibility therefore still rests on `27-HUMAN-UAT.md` and a `ROADMAP.md` line. No backlog file was
  invented, for the reason plan 27-09 gave: inventing a file the project does not use relocates the
  item rather than surfacing it. Carried as item 4 of the round-4 section.
- **The `ROADMAP.md` plans counter is an execution-tracking field with two owners.** It currently
  reads `**Plans:** 12/13 plans executed` — verified to carry exactly ONE figure, with the
  denominator equal to the 13 `*-PLAN.md` files on disk. It was **verified, not written**; the
  orchestrator advances it after this executor returns. If its post-write value disagrees, that is
  the ledger entry 27 contradiction and should be recorded rather than reconciled silently.

## Known Stubs

None. This plan writes no code, introduces no stub, TODO, FIXME or skipped test, and left no
`<verify>` unrun. `git diff -- src/` is **EMPTY** (0 lines) and
`git diff -- build.gradle.kts gradle/libs.versions.toml` is empty, so `T-27-13-SC` is satisfied with
no package-legitimacy checkpoint owed.

## Threat Flags

None. This plan adds no network endpoint, no auth path, no file-access pattern and no schema change
at a trust boundary. Its threats are threats to the RECORD, and each disposition was verified:

| Threat ID | Disposition | Evidence |
|---|---|---|
| T-27-13-01 | mitigate | Character-prefix gate PASS on both rewritten rows; robust removed-line form isolates exactly those two |
| T-27-13-02 | mitigate | `AR-27-09` and `AR-27-10` quote their evidence from `27-11-SUMMARY.md` and `27-10-SUMMARY.md`; `AR-27-10`'s inferred half is labelled |
| T-27-13-03 | mitigate | Counter recomputed by the documented command post-edit; population restated; both new findings stated LOW and below the gate, in the frontmatter |
| T-27-13-04 | mitigate | Six residuals named with owners in `ROADMAP.md`, followed by an explicit "six named residuals is NOT a completeness claim" |
| T-27-13-05 | mitigate | Both round-4 decisions recorded MAINTAINER-MADE with the `AR-27-04` contrast written out, plus a note that the ROADMAP sentence is not independent corroboration |
| T-27-13-06 | mitigate | Robust removed-line counts: `27-HUMAN-UAT.md` 0, `REQUIREMENTS.md` 0, `CONCERNS.md` 0 |
| T-27-13-07 | mitigate | Plans counter verified to carry exactly ONE figure; not written; two-owner contradiction pre-recorded |
| T-27-13-08 | transfer | `T-27-06-06` deliberately unactioned and re-carried as item 4 with its loss risk restated |
| T-27-13-SC | accept | Zero-line dependency diff |

## Plan-Level Verification

| # | Item | Result |
|---|---|---|
| 1 | T-26-02-01 prefix check passes, both character lengths recorded, measured as characters | **PASS** — 18,263 → 29,410, PASS |
| 2 | `threats_open` equals the raw command output, with rows scanned/closed and the population statement | **PASS** — `0`, 46/46, population restated |
| 3 | `AR-27-09` and `AR-27-10` each have a severity, an owner and round-4 quoted evidence | **PASS** — LOW/LOW, maintainer-owned, quoted |
| 4 | Standing-rule clauses (v) and (vi) exist, each with a worked example from this file's history | **PASS** |
| 5 | Robust removed lines: `27-HUMAN-UAT.md` 0, `REQUIREMENTS.md` 0, `CONCERNS.md` 0 | **PASS** — 0 / 0 / 0 |
| 6 | `WINDOWS.md` counters increased by exactly the number of entries appended | **PASS** — 29 → 40, +11 across three appends |
| 7 | `ROADMAP.md` has the four plan entries, six residuals with owners, the unchanged not-satisfied sentence with its round-4 confirmation | **PASS** — 4 / 16 AR-id hits / 1 |
| 8 | `git diff -- src/` is EMPTY | **PASS** — 0 lines |

## User Setup Required

None — no external service configuration required.

## Next Phase Readiness

- **PRIV-05 is NOT closed. `REQUIREMENTS.md` is untouched and PRIV-05 stays `[ ]`.** The next
  artifact for this phase is `27-VERIFICATION-4.md`, not a closure.
- **Phase 28 owns `AR-27-08` and `scanner/InjectionPointExtractor.kt:29`**, unchanged and untouched
  by round 4.
- **Four dispositions now await a human** in `27-HUMAN-UAT.md`: `AR-27-04` (item 9, still owed),
  `AR-27-09` (item 10), `AR-27-10` (item 11), plus `AR-27-07`/`AR-27-08` (items 7 and 8). Nine items
  in total are carried forward legible as unanswered, and round 4 answered none of them.
- **THE BOUND, CARRIED WITH THE CLAIM.** Round 4 closed TWO axes: the character class of a cookie
  header NAME, and the JSON-string-open logical-line start. **The next blind axes, named:**
  `AR-27-09`, `AR-27-10`, `AR-27-04`, `AR-27-08`, the `CONCERNS.md` vendor auth-header class, and
  `RedactingPolicySurvivalSweepTest`'s own stated vocabulary bound. **Six named residuals is not a
  completeness claim** — this threat has been refuted four times by exactly the thing no list
  contained, and nothing in this SUMMARY should be read as implying otherwise.

## Self-Check: PASSED

- `.planning/phases/27-priv-05-gap-closure-sanitize-headers/27-13-SUMMARY.md` — FOUND on disk
- `.planning/phases/26-coverage-static-analysis-debt-docs/26-SECURITY.md` — FOUND, 10 `AR-27-` rows
- `.planning/phases/27-priv-05-gap-closure-sanitize-headers/27-HUMAN-UAT.md` — FOUND, 0 removed lines
- `.planning/codebase/CONCERNS.md` — FOUND, `AMENDMENT 4` present, 0 removed lines
- `.planning/WINDOWS.md` — FOUND, `total_count: 40`
- `.planning/ROADMAP.md` — FOUND, round-4 note and six residuals present
- `.planning/phases/27-priv-05-gap-closure-sanitize-headers/COVERAGE.md` — FOUND, extension present
- Commits `e42ee7e`, `a43bfd8`, `0827537` — all FOUND in `git log`
- `.planning/STATE.md` — deliberately NOT modified (worktree mode; orchestrator owns those writes)
- `.planning/REQUIREMENTS.md` — deliberately NOT modified; PRIV-05 reads `- [ ]`

---
*Phase: 27-priv-05-gap-closure-sanitize-headers*
*Completed: 2026-08-26*
