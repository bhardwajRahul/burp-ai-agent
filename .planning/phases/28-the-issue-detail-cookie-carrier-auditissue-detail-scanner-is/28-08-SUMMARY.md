---
phase: 28-the-issue-detail-cookie-carrier-auditissue-detail-scanner-is
plan: 08
subsystem: security
tags: [record-repair, security-register, carrier-registry, append-and-amend, override, priv-05]

requires:
  - phase: 28-07
    provides: "the shipped mechanism this plan writes down — `PRIVACY_MODE_TOOLTIP`, the canonical `WRITE-TIME/READ-TIME BOUND` token at `consolidateIssues`, the four `**Payload Used:**` assertions, the named route-2 fail-open set and its two pins"
  - phase: 28-06
    provides: "the append-and-amend protocol and the two prefix digests row 315's third marker had to leave unmoved"
provides:
  - "A third supersession on `ISSUE_DETAIL_CARRIER_DISPOSITION` naming the write-time/read-time bound, its human authority, the two claims round 3 repaired, and the five things still open"
  - "A third dated marker on `26-SECURITY.md` row 315 that ADDS TO clause (d) rather than replacing it, with both measured byte-prefixes unmoved"
  - "`threats_open` recomputed from the documented awk rather than restated, with its raw output and row count"
  - "The SC1 override in `28-VERIFICATION.md` frontmatter, applied last and only after six machine-checked conditions"
  - "The re-derived row-315 anchor recorded beside the stale one the round-3 brief supplied, so round 4 inherits a measurement rather than a paste"
affects: [phase-29, 26-SECURITY, issue-detail-carrier-disposition, milestone-audit]

actuals:
  tokens: 21868
  tasks: 3
  commits: 3

tech-stack:
  added: []
  patterns:
    - "Anchor re-derivation before an anchored edit: the supplied anchor is measured for uniqueness at HEAD and BOTH the derived and the stale literal are recorded, because an amendment protocol invalidates its own anchor every round"
    - "Fixed-offset prefix digests over anchor-relative ones for append-and-amend proof: an anchor-relative prefix grows by exactly the inserted length and cannot be re-asserted, while a fixed-offset prefix is invariant under any append after it"
    - "Preconditions as the substitute for a checkpoint on an already-answered one-way decision: a CONDITIONAL human acceptance is enforced by machine-checking its conditions, not by re-asking the question"

key-files:
  created:
    - .planning/phases/28-the-issue-detail-cookie-carrier-auditissue-detail-scanner-is/28-08-SUMMARY.md
  modified:
    - src/test/kotlin/com/six2dez/burp/aiagent/redact/CookieCarrierInventoryTest.kt
    - .planning/phases/26-coverage-static-analysis-debt-docs/26-SECURITY.md
    - .planning/phases/28-the-issue-detail-cookie-carrier-auditissue-detail-scanner-is/28-VERIFICATION.md

key-decisions:
  - "The 16071-byte prefix criterion was satisfied by its FIXED-offset form, not its anchor-relative form. The anchor-relative form is unsatisfiable by any append before the separator, by construction; the fixed form proves the same property and holds. Recorded as a measurement deviation, nothing in the tree changed to accommodate it"
  - "The KDoc's marker-count amendment does NOT reuse the literal `SUPERSEDED A THIRD TIME`, because the acceptance criterion pins that literal at exactly 1 and the marker itself must own it; the KDoc paragraph opens `THIRD SUPERSESSION APPENDED` instead"
  - "The row-315 marker labels its five items `(28-08.1)`..`(28-08.5)` rather than `(a)`..`(e)`, because the cell already carries an `(a)`..`(g)` lettering from the 28-06 amendment and three adjacent dated markers must not be mergeable by a reader"
  - "`threats_open` was recomputed and found unchanged at 0, so the front-matter value was NOT edited — the counter is the awk's raw output, and writing it by hand is the anti-pattern its own comment block records"

patterns-established:
  - "An append-and-amend edit is proven by a digest taken BEFORE the edit and re-asserted after, never by one reconstructed from the post-edit file"
  - "Every offset, digest and byte-count command over a row carrying em-dashes runs under `LC_ALL=C`, paired with its byte count, so a character-vs-byte reading is caught rather than absorbed"

requirements-completed: [PRIV-05]

coverage:
  - id: D1
    description: "The write-time/read-time bound is named with the canonical literal in `ISSUE_DETAIL_CARRIER_DISPOSITION`'s STILL OPEN clause, under a third dated supersession that withdraws nothing"
    requirement: PRIV-05
    verification:
      - kind: other
        ref: "grep -o 'WRITE-TIME/READ-TIME BOUND' CookieCarrierInventoryTest.kt | wc -l  ->  1 (HEAD: 0)"
        status: pass
      - kind: other
        ref: "grep -o 'SUPERSEDED A THIRD TIME' CookieCarrierInventoryTest.kt | wc -l  ->  1 (HEAD: 0)"
        status: pass
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/redact/CookieCarrierInventoryTest.kt (whole class: failures=0 errors=0)"
        status: pass
    human_judgment: false
  - id: D2
    description: "Both prior supersessions survive byte-exact and the append is purely additive — exactly one existing source line changed"
    verification:
      - kind: other
        ref: "grep -o 'SUPERSEDED 2026-08-27 (phase 28, plan 28-01)' | wc -l -> 1; 'SUPERSEDED AGAIN 2026-08-27 (phase 28, plan 28-06)' -> 1"
        status: pass
      - kind: other
        ref: "git diff -U0 a2e83e5 -- CookieCarrierInventoryTest.kt | grep -cE '^-[^-]'  ->  1"
        status: pass
    human_judgment: false
  - id: D3
    description: "`26-SECURITY.md` row 315 carries a third dated marker that ADDS TO clause (d) and withdraws nothing, with the row still OPEN and the file still one line longer than nothing"
    requirement: PRIV-05
    verification:
      - kind: other
        ref: "LC_ALL=C awk 'NR==315' | grep -o 'by plan 28-08 (phase 28)' | wc -l  ->  1 (HEAD: 0)"
        status: pass
      - kind: other
        ref: "LC_ALL=C awk 'NR==315' | grep -o 'AMENDED 2026-08-27 by plan 28-0[36]' | wc -l  ->  2 (unchanged)"
        status: pass
      - kind: other
        ref: "LC_ALL=C awk 'NR==315{print substr($0,1,60)}'  ->  '| AR-27-08 | T-26-02-01 | **NEW, OPEN, severity MEDIUM — M'"
        status: pass
      - kind: other
        ref: "wc -l < 26-SECURITY.md  ->  1799 (unchanged); git diff -U0 a2e83e5 | grep -cE '^-[^-]'  ->  1"
        status: pass
    human_judgment: false
  - id: D4
    description: "The register edit is an append, proven by two digests taken before it and re-asserted after it"
    verification:
      - kind: other
        ref: "8693-byte prefix sha256 before AND after -> 8dc326ac23204becce687deeba867740eb2d4dde21346c58d7da9595d137ae2e"
        status: pass
      - kind: other
        ref: "16071-byte fixed prefix sha256 before AND after -> 5316a97149017ae824d162b72b99d954cb0fa25b28b0c3a8214c01e42390ed72 (16071 bytes both times)"
        status: pass
    human_judgment: false
  - id: D5
    description: "`threats_open` recomputed from the documented awk rather than restated, with the counter's population rule recorded so an OPEN `AR-` row beneath a 0 is not read as a contradiction"
    verification:
      - kind: other
        ref: "documented awk raw output -> 0; 46 T-26- rows scanned, 46 closed; grep -c '^threats_open: 0' -> 1 (unedited)"
        status: pass
    human_judgment: false
  - id: D6
    description: "The SC1 override sits in `28-VERIFICATION.md` frontmatter with the maintainer's 2026-08-28 answer named and a real timestamp; `overrides_applied` reads 1"
    verification:
      - kind: other
        ref: "frontmatter-scoped: grep -c '^overrides_applied: 1' -> 1; grep -c '^overrides:' -> 1; accepted_at '20' -> 1; accepted_by '.*2026-08-28' -> 1"
        status: pass
      - kind: other
        ref: "python yaml.safe_load over the frontmatter -> overrides_applied=1, n_overrides=1, status=gaps_found, deferred=[]"
        status: pass
    human_judgment: false
  - id: D7
    description: "The verifier's own findings survive the override byte-unchanged, and the body's fenced template block is untouched"
    verification:
      - kind: other
        ref: "git diff -U0 a2e83e5 -- 28-VERIFICATION.md | grep -E '^-[^-]'  ->  the single line '-overrides_applied: 0'"
        status: pass
      - kind: other
        ref: "grep -c '^status: gaps_found' -> 1; grep -c '^score: 5/6 must-haves verified' -> 1; grep -o 'ISO timestamp' | wc -l -> 1 (body template placeholder only)"
        status: pass
    human_judgment: false
  - id: D8
    description: "PRIV-05 stays an unchecked box and `.planning/REQUIREMENTS.md` is byte-unchanged, gated twice under distinct headings"
    requirement: PRIV-05
    verification:
      - kind: other
        ref: "RUN 1 and RUN 2 both: shasum -a 256 -> 9b3219662ec0d007c1c82d64eed3ef2698bd306ce69f01205ac9bbc3f42fcfb4; line 23 still '- [ ] **PRIV-05**'; git diff --stat empty"
        status: pass
    human_judgment: false
  - id: D9
    description: "Neither record claims a control was added, a leak was closed, or a repository-wide producer gate exists; the route-2 detail-line-(4) asymmetry is recorded as DEFENCE IN DEPTH in both"
    verification:
      - kind: other
        ref: "grep -o 'DEFENCE IN DEPTH' CookieCarrierInventoryTest.kt | wc -l -> 2 (HEAD: 1); the phrase 'ROUND 3 ADDED NO CONTROL' appears in both the constant and row 315"
        status: pass
    human_judgment: true
    rationale: "Whether the two records actually READ as an honest residual rather than as a closure to a future maintainer is a judgement about writing, not a property a grep can assert. The greps prove the words are present and that no closure claim was added; only a human can confirm the prose does the job the round-3 contract asked for. This is the same disposition 28-07 gave its own D8, and it is the phase's own named failure mode, so it should not be auto-passed."

duration: 39 min
completed: 2026-08-28
status: complete
---

# Phase 28 Plan 08: The Record Repair and the Conditional Override Summary

**The write-time/read-time bound is now named with the canonical literal in both enumerations that had drifted a step behind the mechanism — a third supersession on `ISSUE_DETAIL_CARRIER_DISPOSITION` and a third dated marker on `26-SECURITY.md` row 315 that ADDS TO clause (d) — with both measured byte-prefixes provably unmoved, `threats_open` recomputed rather than restated, and the SC1 override applied last, only after all six of its conditions measured green.**

## Performance

- **Duration:** 39 min
- **Started:** 2026-08-28T07:22Z
- **Completed:** 2026-08-28T08:01Z
- **Tasks:** 3
- **Files modified:** 3 (plus this SUMMARY)

## Accomplishments

- **The bound is named in the carrier registry.** `ISSUE_DETAIL_CARRIER_DISPOSITION` gains a third supersession in the constant's own vocabulary (` SUPERSEDED A THIRD TIME 2026-08-28 (phase 28, plan 28-08)`) whose first sentence states that it withdraws nothing and EXTENDS the 28-06 block's STILL OPEN clause. It carries the canonical `WRITE-TIME/READ-TIME BOUND` literal, `D-28-09`'s human authority with the maintainer's stated reason, `D-28-10`'s conditions mapped to their discharging artifacts, the corrected detail-line-(4) probe claim with the asymmetry left as DEFENCE IN DEPTH, the route-2 fail-open set with widening recorded as CONSIDERED AND NOT TAKEN, and the five things still open. The constant KDoc's now-stale marker-count sentence is amended by append rather than rewritten.
- **The bound is named in the security register.** Row 315 gains `**AMENDED 2026-08-28 by plan 28-08 (phase 28) — THIS AMENDMENT ADDS TO CLAUSE (d) AND WITHDRAWS NOTHING.**` and the same five items in register language, so the two records cannot drift into two accounts of one fact. The insertion is 7,659 bytes placed at byte offset 16072, immediately before the ` | Measured by plan 27-08` column separator.
- **The append is proven by digest, not by assertion.** Both prefix digests were taken BEFORE the edit and re-asserted after: the 8693-byte prefix stayed `8dc326ac…` and the 16071-byte prefix stayed `5316a971…` at exactly 16071 bytes. The row is still one line, the file is still 1799 lines, and the whole-file diff removes exactly one line.
- **The stale anchor was measured, not pasted.** The round-3 brief's anchor literal occurs **0** times at HEAD. The unique anchor was re-derived from the bytes preceding the separator and confirmed unique file-wide before any edit.
- **`threats_open` was recomputed.** The awk documented at the top of `26-SECURITY.md` was re-run: raw output `0`, 46 `T-26-` rows scanned, 46 closed. Unchanged, so the front-matter value was not edited.
- **The override was applied last, and gated.** All six `D-28-10` conditions were measured green — including a full-suite run at 1308 tests / 181 classes, 0 failures, 0 errors, 1 skipped — before a byte was written to `28-VERIFICATION.md`. `overrides_applied` moved 0 → 1 and the frontmatter gained the block, with `must_have` and `reason` verbatim from the verifier's own template.
- **The verifier's account survived intact.** The only removed line in the entire `28-VERIFICATION.md` diff is `overrides_applied: 0`.

## Task Commits

1. **Task 1: the third supersession on `ISSUE_DETAIL_CARRIER_DISPOSITION`** — `7b189e3` (docs)
2. **Task 2: row 315's third dated marker amending clause (d); `threats_open` recomputed** — `32ffc60` (docs)
3. **Task 3: the SC1 override, applied after its six conditions measured green** — `a50dfe3` (docs)

_Base pinned before any edit: `a2e83e552a501e84bb2638766f8c1926c463f22a`. Every `git diff` gate below is measured against it, not against the working tree — `execute-plan.md` commits per task, so a bare `git diff` would go empty after task 1 and every later gate would pass by measuring nothing._

## Files Created/Modified

- `src/test/kotlin/com/six2dez/burp/aiagent/redact/CookieCarrierInventoryTest.kt` (+89/-1) — third supersession block on the disposition constant; dated KDoc paragraph amending the marker count. Exactly one existing source line modified: the concatenation's former final line, which gained a `+` continuation.
- `.planning/phases/26-coverage-static-analysis-debt-docs/26-SECURITY.md` (+1/-1, one line, 7,659 bytes inserted) — row 315's third dated marker.
- `.planning/phases/28-…/28-VERIFICATION.md` (+13/-1) — frontmatter `overrides:` block for SC1; `overrides_applied` 0 → 1.

## The Row-315 Anchor: Derived vs Stale (verification item 8)

The round-3 brief supplied an anchor literal that no longer exists. Recorded side by side so round 4 inherits the measurement rather than the paste — and because this is itself an instance of the drift the round exists to repair.

| Literal | Occurrences at HEAD | Status |
|---|---|---|
| `not an omission. | Measured by plan 27-08` (the brief's) | **0** | STALE — plan 28-06's own amendment replaced the preceding sentence |
| `not a contradiction. | Measured by plan 27-08` | **1** | THE UNIQUE ANCHOR AT HEAD — re-derived and used |
| `| Measured by plan 27-08` (short form) | **2** lines (314, 315) | NOT UNIQUE — exactly as the brief warned |

Raw re-derivation, 40 bytes preceding the separator:

```
$ LC_ALL=C awk 'NR==315{i=index($0,"| Measured by plan 27-08"); print substr($0,i-40,64)}' 26-SECURITY.md
umented behaviour, not a contradiction. | Measured by plan 27-08
```

**THE ANCHOR THIS PLAN LEAVES FOR ROUND 4**, since this amendment invalidates the one it used — the same way 28-06 invalidated 28-03's. Measured after the edit, confirmed unique file-wide (`grep -o … | wc -l` → **1**):

```
the front-matter value was not edited. | Measured by plan 27-08
```

## Prefix Digests — Pre-Edit and Post-Edit, Raw

Taken BEFORE the register edit and re-asserted AFTER it. Neither post-edit value is reconstructed from the post-edit file; both are the same command re-run.

| Measurement | Pre-edit (raw) | Post-edit (raw) | Required |
|---|---|---|---|
| 8693-byte prefix, sha256 | `8dc326ac23204becce687deeba867740eb2d4dde21346c58d7da9595d137ae2e` | `8dc326ac23204becce687deeba867740eb2d4dde21346c58d7da9595d137ae2e` | unmoved — PASS |
| 16071-byte prefix, `wc -c` | `16071` | `16071` | unmoved — PASS |
| 16071-byte prefix, sha256 | `5316a97149017ae824d162b72b99d954cb0fa25b28b0c3a8214c01e42390ed72` | `5316a97149017ae824d162b72b99d954cb0fa25b28b0c3a8214c01e42390ed72` | unmoved — PASS |
| Row 315 total length | `16191` | `23850` | +7659, the inserted marker |

`LC_ALL=C` was set on every offset, digest and byte-count command in this plan. Each digest is paired with its byte count (8693, 16071) so a character-vs-byte reading would have been caught rather than absorbed — row 315 carries em-dashes and backticks, and under a UTF-8 locale `substr`/`index` count characters.

## `threats_open` — Recomputed, Not Restated

The awk documented at the top of `26-SECURITY.md`, re-run after the edit:

```
$ awk -F'|' '/^\| T-26-/ { sev=$5; st=$(NF-1); gsub(/[ *`]/,"",sev); gsub(/[ *`]/,"",st);
      if (st != "closed" && (sev == "high" || sev == "critical")) c++ } END { print c+0 }' 26-SECURITY.md
0
```

**Raw output: `0`. Rows scanned: 46 `T-26-` rows, 46 closed.** Identical to the written value, so `threats_open: 0` was NOT edited — the counter is the awk's raw output, and hand-writing it is the anti-pattern its own comment block records.

`AR-` rows sit OUTSIDE that counter's population at any severity: the population is Threat Register rows whose id begins `T-26-` and nothing else. An OPEN `AR-27-08` beneath a counter reading `0` is therefore documented behaviour, not a contradiction. This round added, amended and reclassified no `T-26-` row.

## THE OVERRIDE GATE — All Six Conditions, Raw Output

`D-28-10` made the maintainer's acceptance CONDITIONAL. These were run BEFORE a byte was written to `28-VERIFICATION.md`. Every HEAD value is `0`, so no check could have passed on an unmodified tree.

| # | Check | HEAD | Raw output | Required | Result |
|---|---|---|---|---|---|
| 1 | `grep -o "Applies from now on, not retroactively" SettingsPanelInit.kt \| wc -l` | 0 | **1** | `>= 1` | PASS |
| 2 | `grep -o "WRITE-TIME/READ-TIME BOUND" AiScanCheck.kt \| wc -l` | 0 | **1** | `>= 1` | PASS |
| 3 | `grep -o "Payload Used" AiScanCheckDetailCookieCarrierTest.kt \| wc -l` | 0 | **8** | `>= 4` | PASS |
| 4 | `grep -o "WRITE-TIME/READ-TIME BOUND" CookieCarrierInventoryTest.kt \| wc -l` | 0 | **1** | `>= 1` | PASS |
| 5 | `LC_ALL=C awk 'NR==315' 26-SECURITY.md \| grep -o "by plan 28-08 (phase 28)" \| wc -l` | 0 | **1** | `== 1` | PASS |
| 6 | full suite: `./gradlew test` | — | **1308 tests / 181 classes, 0 failures, 0 errors, 1 skipped** | 0 failures, 0 errors | PASS |

**No check failed, so no halt occurred.** Had any failed, the task would have written nothing and left `overrides_applied` at 0; that outcome is recorded here as the one that did not happen, so a reader can tell the difference between a gate that passed and a gate that was skipped.

Check 6 detail: `BUILD SUCCESSFUL in 3m 15s`; 181 result XMLs, all `failures="0" errors="0"`; exactly one carries `skipped="1"` (the pre-existing `@Disabled` in `ExternalMcpClientManagerTest`). `RedactionTest > windowedScanRedactsJsonPairAcrossEveryBoundaryAlignment()` PASSED — the known wall-clock flake did not fire this run.

## PRIV-05 GATE — RUN 1 (in-task)

```
$ shasum -a 256 .planning/REQUIREMENTS.md
9b3219662ec0d007c1c82d64eed3ef2698bd306ce69f01205ac9bbc3f42fcfb4  .planning/REQUIREMENTS.md

$ grep -n 'PRIV-05' .planning/REQUIREMENTS.md
6:Scope = the 17 findings … (SEC-04, PRIV-05) are **defects verified by running the shipped code** …
10:**Ordering constraint:** SEC-04 and PRIV-05 are live defects …
23:- [ ] **PRIV-05** (Finding 2, **high**): Cookie values do not reach an AI backend …
39:- [x] **DOC-03**: A security advisory documents SEC-04 and PRIV-05 …
47:| PRIV-05 | 2 | High | 21 |

$ git diff --stat -- .planning/REQUIREMENTS.md
(no output)
```

Digest begins `9b3219662ec0d007`. Line 23 is still an unchecked box. **PASS.**

## PRIV-05 GATE — RUN 2 (post-completion, immediately before the plan-metadata commit)

```
$ shasum -a 256 .planning/REQUIREMENTS.md
9b3219662ec0d007c1c82d64eed3ef2698bd306ce69f01205ac9bbc3f42fcfb4  .planning/REQUIREMENTS.md

$ sed -n '23p' .planning/REQUIREMENTS.md
- [ ] **PRIV-05** (Finding 2, **high**): Cookie values do not reach an AI backend in STRICT or BALANCED mode by any path. …

$ git diff --stat -- .planning/REQUIREMENTS.md
(no output)
```

Digest unmoved, line 23 still `- [ ]`, no diff. **PASS.** No revert was needed and no `WINDOWS.md` incident entry was required.

`phase.complete` was NOT run by this plan — the orchestrator owns it. RUN 2 is recorded here as the last measurement this plan takes; whoever runs `phase.complete 28` must re-run this gate after it, because that command has flipped this box once already as a side effect while its own warnings said it was skipping the write.

## Decisions Made

- **The 16071-byte criterion was satisfied by its fixed-offset form.** See the deviation below. The property the plan wanted — prior text is a byte-exact prefix — is proven; the command it named cannot prove it.
- **The KDoc amendment does not reuse the marker literal.** `SUPERSEDED A THIRD TIME` is pinned at exactly 1 occurrence by an acceptance criterion, and the marker itself must own it, so the KDoc paragraph opens `THIRD SUPERSESSION APPENDED — 2026-08-28, phase 28, plan 28-08`.
- **The row-315 marker uses `(28-08.1)`..`(28-08.5)` labels.** The cell already carries an `(a)`..`(g)` lettering owned by the 28-06 amendment; reusing those letters in a third adjacent marker is exactly the merge hazard `[edge:adjacency]` names.
- **The KDoc's stale sentence is referenced positionally, not quoted.** Quoting `THIS CONSTANT NOW CARRIES TWO SUPERSESSIONS AND ONE MEASUREMENT` into the amendment would double a grep-able literal in a file whose whole discipline is grep-able enumeration.
- **`threats_open` was not edited.** Recomputed, found identical, left alone.

## Deviations from Plan

### 1. [Rule 3 - Blocking] The 16071-byte prefix criterion names a command that no append can satisfy

- **Found during:** Task 2
- **Issue:** The criterion reads `LC_ALL=C awk 'NR==315{i=index($0,"| Measured by plan 27-08"); printf "%s", substr($0,1,i-1)}' | wc -c` prints `16071` **after** the edit. That prefix is ANCHOR-RELATIVE: it ends wherever the separator currently is. The task's own action inserts text immediately BEFORE that separator, so the anchor necessarily moves right by the inserted length and the command necessarily reports `16071 + len(insert)`. As literally written the criterion is unsatisfiable by any correct append — it would only pass if the edit landed AFTER the separator, i.e. in the wrong column. The 8693-byte criterion has no such defect because it is fixed-offset.
- **Fix:** Satisfied by INTENT using the fixed-offset form `substr($0,1,16071)`, which measures the identical bytes and IS invariant under an append at offset 16072. Verified equivalent BEFORE the edit: the fixed form and the anchor-relative form both returned `16071` bytes and both digested to `5316a971…` on the unmodified file. Nothing in the tree was changed to accommodate the command.
- **Files modified:** none (measurement-only deviation)
- **Verification:** Pre-edit fixed form → `16071` / `5316a97149017ae824d162b72b99d954cb0fa25b28b0c3a8214c01e42390ed72`. Post-edit fixed form → `16071` / the same digest. Post-edit anchor-relative form → `23730` bytes, which is exactly `16071 + 7659`; that arithmetic is itself the proof that the insertion landed at byte 16072 and that nothing before it moved.
- **Committed in:** n/a — no tree change was required.

### 2. [Rule 3 - Blocking] Three acceptance criteria state HEAD values that are stale

- **Found during:** Tasks 1 and 3
- **Issue:** Three criteria annotate a HEAD value that does not match the tree at the pinned base `a2e83e5`. All three are `>= 1` or `>= 1`-shaped, so the criterion itself still passes; only the stated baseline is wrong, and a reader comparing "HEAD → after" would be reading a fabricated delta.
- **Fix:** Measured the real HEAD values and record them here rather than repeating the plan's. No test, file or command was altered to make a stated baseline true.
- **Verification:**

  | Criterion | Plan's stated HEAD | Measured HEAD at `a2e83e5` | After this plan |
  |---|---|---|---|
  | `grep -o "DEFENCE IN DEPTH" CookieCarrierInventoryTest.kt \| wc -l` | 0 | **1** (the 28-06 block already says it) | **2** |
  | frontmatter-scoped `grep -o "SC1" 28-VERIFICATION.md \| wc -l` | 0 | **10** (`gaps_closed`, `gaps_remaining`, `gaps`) | **11** |
  | Task 3 precondition (3), `grep -o "Payload Used" …` | "HEAD value is also 0" | **8** (plan 28-07 shipped them) | **8** |

  The third is the interesting one: the plan asserts every precondition has a HEAD value of 0 "so no check can pass by accident on an unmodified tree". That is true of conditions (1), (2), (4) and (5), which were 0 at `0d2f1fe`; it is NOT true of (3), which plan 28-07 had already satisfied at the base this plan actually ran on. The gate's guarantee is therefore slightly weaker than stated, and it is recorded here rather than quietly inherited.
- **Committed in:** n/a — no tree change was required.

### 3. [Rule 3 - Blocking] The `<measured_facts>` name the marker-count sentence as living in "the class KDoc"

- **Found during:** Task 1
- **Issue:** Measured fact 6 says "The class KDoc above it contains the literal `THIS CONSTANT NOW CARRIES TWO`". It does not: that sentence is at line 692, inside the CONSTANT's own KDoc (lines 671-699), not the class KDoc at lines 1-90. The task action text is correct ("the KDoc above the constant"); only the measured fact's label is wrong.
- **Fix:** Amended the constant's own KDoc, which is what the action text and the artifact table both describe. No sentence was moved between KDocs.
- **Files modified:** `CookieCarrierInventoryTest.kt` (the intended edit, not a corrective one)
- **Verification:** `grep -n "THIS CONSTANT NOW CARRIES TWO"` → `692`; `grep -n '^\s*\*/'` → the enclosing KDoc closes at `699`, immediately above `const val ISSUE_DETAIL_CARRIER_DISPOSITION` at `700`.
- **Committed in:** `7b189e3`

---

**Total deviations:** 3, all measurement-only (3 blocking measurement errors in the plan's own criteria and facts).
**Impact on plan:** None on scope, behaviour or record content. Every criterion's INTENT was met and every artifact the plan chartered was produced. In all three cases the tree was left alone and the corrected measurement is recorded — which is the discipline this round exists to establish, applied to the round's own instructions.

## Issues Encountered

None. All three tasks' preconditions held when re-checked at execution time: the two carrier-registry anchors, all four register digests/offsets, and all six override conditions. The one genuine surprise — the stale anchor literal — was anticipated by the plan and handled by re-derivation, exactly as chartered.

## TDD Gate Compliance

Not applicable. No task in this plan carries `tdd="true"`, and the plan is `type: execute`, not `type: tdd`. All three tasks are record edits: one to a test file's documentary constants and KDoc (no assertion, fixture or test method touched), two to planning artifacts. No non-test source file was modified, so the MVP+TDD behaviour-adding predicate is `false` for every task and the gate is exempt. Commits are typed `docs(...)`, which is correct for documentation-only changes — including the one inside a test file, whose change is comment and constant prose only.

## Verification Results

| Check | Result |
|---|---|
| `./gradlew test --tests 'CookieCarrierInventoryTest'` | **BUILD SUCCESSFUL**; XML `failures="0" errors="0"` |
| `./gradlew test` (full suite, backgrounded) | **BUILD SUCCESSFUL in 3m 15s** — 1308 tests, 181 classes, 0 failures, 0 errors, 1 skipped |
| Suite vs 28-07's post-merge baseline | 1308 / 181 → 1308 / 181 — unchanged, as expected for a record-only round |
| The 1 skip | pre-existing `@Disabled` in `ExternalMcpClientManagerTest` — unchanged |
| `RedactionTest > windowedScanRedactsJsonPairAcrossEveryBoundaryAlignment()` | **PASSED** — known wall-clock flake did not fire |
| `./gradlew ktlintCheck detekt` | **BUILD SUCCESSFUL** (exit 0), run twice |
| `detekt-baseline.xml` | byte-unchanged (`git diff --stat` prints nothing) |
| `./gradlew check` | **NOT RUN** — RED at HEAD for a maintainer-accepted coverage-floor reason; the plan states it is not a gate |
| Both row-315 prefix digests, re-asserted post-edit | unmoved (see table above) |
| `wc -l < 26-SECURITY.md` | **1799** — unchanged |
| `grep -c "^threats_open: 0"` | **1** — recomputed and unchanged |
| Six override preconditions | all green, raw output recorded above |
| PRIV-05 gate RUN 1 / RUN 2 | both PASS, raw output recorded above |
| Row-315 anchor, derived vs stale | recorded above, plus the new anchor for round 4 |

## Known Stubs

None. No placeholder values, no skipped tests added, no `<verify>` left unrun. Both frontmatter placeholders in the override template (`<maintainer>`, `<ISO timestamp>`) were filled; the script that applied the block refuses to write if either literal survives, and `grep -o 'ISO timestamp'` over the whole file still returns **1**, which is the body template's own placeholder and is deliberately untouched.

No `.planning/WINDOWS.md` entry was appended: the ledger's kinds are stubs, TODO/FIXME markers, skipped tests, lint warnings, unmet truths, unrun verifies and deviations, and this plan produced none of the first six. The three deviations are measurement-only corrections to the plan's own text with no residue in the tree, so recording them as open ship-blocking defects would misrepresent them; they are recorded in full above instead.

## Threat Flags

None. This plan added no network endpoint, no auth path, no file access pattern and no schema change, and modified no production source file. The two `accept`-disposition threats it touches (`T-28-48` the write-time/read-time bound and the route-2 fail-open set, `T-28-49` the absent repository-wide detail-producer gate) are now MORE visible than before: both are named as residuals in both records, with an explicit statement in each that round 3 added no control.

## User Setup Required

None — no external service configuration required.

## Next Phase Readiness

**Phase 28's record now matches its mechanism, and `phase.complete 28` is unblocked** by the SC1 override.

What a reader of the next phase inherits, stated so nobody looks for what is not there:

- **`AR-27-08` STAYS OPEN** in `26-SECURITY.md`, narrowed to two named residuals and not closed.
- **PRIV-05 stays `- [ ]`.** `.planning/REQUIREMENTS.md` is byte-unchanged at `9b3219662ec0d007…`. Whoever runs `phase.complete 28` must re-run the PRIV-05 gate immediately after it.
- **The write-time/read-time bound is a named residual, not a fix.** A read-time pass over `AuditIssue.detail()` is new architecture on the emission path and belongs to its own phase — that is what `D-28-09` accepted.
- **The route-2 fail-open set (`HEADER`, `USER_PROVIDED`, `EXTENSION_PROVIDED`, `UNKNOWN`) is named, not closed.** Pinned by `theRouteTwoGateIsFailOpenForTheseCookieCapableTypes` and bounded by `theInsertionPointTypeEnumPopulationIsTheOneTheResidualWasMeasuredAgainst`.
- **`WR-01` stays open.** No repository-wide detail-producer gate exists; two producers are controlled and a third would still be caught by nothing.
- **Round 3 added no control and changed no runtime behaviour.** Both records say so in their own words.
- **The row-315 anchor for round 4** is `the front-matter value was not edited. | Measured by plan 27-08`, measured unique after this edit — but re-derive it rather than paste it, because that is exactly the mistake this round found.

---
*Phase: 28-the-issue-detail-cookie-carrier-auditissue-detail-scanner-is*
*Completed: 2026-08-28*

## Self-Check: PASSED

- All 3 modified files verified present on disk and modified as claimed; this SUMMARY verified written.
- All 3 commits verified in `git log a2e83e5..HEAD`: `7b189e3`, `32ffc60`, `a50dfe3`.
- All `<acceptance_criteria>` from all three tasks re-run and recorded above, with the two unsatisfiable-as-written criteria handled by intent and documented as deviations rather than quietly passed.
- All 8 plan `<verification>` items run and recorded in Verification Results.
- Working tree clean before this SUMMARY commit; no untracked files left behind.
- STATE.md and ROADMAP.md deliberately NOT modified — this plan ran in a worktree and the orchestrator owns those writes.
