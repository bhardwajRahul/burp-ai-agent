---
phase: 27-priv-05-gap-closure-sanitize-headers
plan: 16
subsystem: records
tags: [security-register, threat-model, roadmap, provenance, coverage, gates, kotlin]

requires:
  - phase: 27-priv-05-gap-closure-sanitize-headers
    provides: "27-14's PROBE D (the AR-27-11 evidence) and PROBE B (the clause (7) measurement), and 27-15's re-measured declaration-gate counts and amended clause (vi)"
provides:
  - "T-26-02-01 clause (7) — the FIFTH re-opening, with clauses (1)-(6) preserved as a byte-exact character prefix (29412 into 38299, asserted programmatically)"
  - "AR-27-11 defined for the first time, with its severity assigned from a reachability measurement taken this round rather than from intuition"
  - "standing-rule clause (vii) — a residual list must enumerate what the round INTRODUCED, not only what it INHERITED — applied to the round that wrote it"
  - "threats_open recomputed from the register rows by the documented awk command, with its population restated in full for the third time"
  - "LogicalLineBoundaryScopeTest.THIRD_OPEN_FINDING, pinning AR-27-11 present in Redaction.kt's rationale region"
  - "two RED GATES round 5 had merged unseen: detekt (FIXED) and jacocoTestCoverageVerification (measured, bisected, deliberately not fixed)"
affects: [phase-28]

actuals:
  tokens: 96000
  tasks: 3
  commits: 3

tech-stack:
  added: []
  patterns:
    - "byte-exact prefix assertion on an append-only record row: the pre-edit cell is snapshotted, the amended cell re-extracted, and `new.startswith(old)` asserted programmatically — reformatting a prior clause to read better becomes a detectable edit rather than a judgement call"
    - "severity from a source enumeration: before assigning a residual's severity, enumerate the schema that would have to carry it, and state MEASURED-zero, MEASURED-nonzero and UNMEASURED separately rather than averaging them into one adjective"
    - "gate bisection over trees: when a gate is red, measure it on the pre-round tree before attributing the regression, and report the sample count rather than implying certainty from one run"

key-files:
  created:
    - .planning/phases/27-priv-05-gap-closure-sanitize-headers/27-16-SUMMARY.md
  modified:
    - .planning/phases/26-coverage-static-analysis-debt-docs/26-SECURITY.md
    - src/test/kotlin/com/six2dez/burp/aiagent/redact/LogicalLineBoundaryScopeTest.kt
    - src/test/kotlin/com/six2dez/burp/aiagent/redact/RedactingPolicySurvivalSweepTest.kt
    - .planning/ROADMAP.md
    - .planning/phases/27-priv-05-gap-closure-sanitize-headers/27-HUMAN-UAT.md
    - .planning/phases/27-priv-05-gap-closure-sanitize-headers/COVERAGE.md
    - .planning/codebase/CONCERNS.md
    - .planning/WINDOWS.md

key-decisions:
  - "Assign AR-27-11 LOW from a MEASURED reachability enumeration, not from the plan's anticipated UNMEASURED shape. The emission schema this repository owns carries zero JSON-array-of-string fields; exactly one carrier (the D-03 external-tool argsJson path) can emit one; the remote half of that carrier is unmeasured. The row records the mixture."
  - "Fix the red detekt gate (Rule 3) and DO NOT fix the red jacoco gate. The first is three `const val`s with no behaviour change. The second can only be closed by a deterministic test for a wall-clock branch or by lowering a QUAL-06 floor, and lowering a floor to green a gate is the laundering this phase exists to prohibit."
  - "Record the narrowing's provenance as directed-and-twice-measured rather than maintainer-signed. `mode: yolo` is still configured and no human answered any checkpoint in round 5, so a signature claim would be uncorroborated — and it would collapse the exact distinction 27-HUMAN-UAT.md exists to preserve."
  - "Correct the ROADMAP note's '136 of 1779' pairing IN PLACE rather than leaving two numbers on the record, and file the correction in WINDOWS.md."
  - "Do NOT relitigate AR-27-04, AR-27-08, InjectionPointExtractor.kt:29 or T-27-06-06. All four are byte-unchanged, and the register and roadmap both say so explicitly rather than by omission."

patterns-established:
  - "Apply a new standing rule to the round that wrote it. Clause (vii)'s first worked example is round 4's list; its second is round 5's own — and running `check` surfaced two residuals round 5 had introduced and would otherwise have shipped unlisted, which is the clause validating itself on its first use."

requirements-completed: []

coverage:
  - id: D1
    description: "T-26-02-01 carries clause (7) and clauses (1)-(6) survive as an EXACT CHARACTER PREFIX, asserted programmatically rather than by eye"
    requirement: "PRIV-05"
    verification:
      - kind: other
        ref: "python3 prefix assertion over the row's Mitigation cell, extracted by unescaped-pipe position; both lengths and the boolean recorded below"
        status: pass
    human_judgment: false
  - id: D2
    description: "AR-27-11 exists exactly once with a measured severity, a stated reachability, a named owner, quoted both-column evidence, and no committed test pinning the survival"
    requirement: "PRIV-05"
    verification:
      - kind: other
        ref: "grep -c '^| AR-27-11 ' 26-SECURITY.md -> 1; reachability enumerated at source over Serialization.kt, McpToolModels.kt and the redactIfNeeded call sites"
        status: pass
    human_judgment: false
  - id: D3
    description: "threats_open is the RAW OUTPUT of the documented awk command re-run against the amended file, with the population restated in full and its forced question answered for AR-27-11"
    requirement: "PRIV-05"
    verification:
      - kind: other
        ref: "awk command quoted from the file's own frontmatter comment, re-run after every other task-1 edit; raw output and row count recorded below"
        status: pass
    human_judgment: false
  - id: D4
    description: "THIRD_OPEN_FINDING pins AR-27-11 present in Redaction.kt's rationale region and FAILS when that citation is removed"
    requirement: "PRIV-05"
    verification:
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/redact/LogicalLineBoundaryScopeTest.kt#theStatedBoundIsPresentWhereAReaderMeetsIt"
        status: pass
      - kind: other
        ref: "RED probe against a temporarily reverted Redaction.kt rationale block; failure message quoted verbatim below"
        status: pass
    human_judgment: false
  - id: D5
    description: "Standing-rule clause (vii) exists, follows the rule/lesson/worked-example shape of (iv)-(vi), and is applied to round 5's own residuals under separate INTRODUCED and INHERITED headings"
    requirement: "PRIV-05"
    verification:
      - kind: other
        ref: "26-SECURITY.md clause (vii); mirrored in ROADMAP.md's split residual paragraph"
        status: pass
    human_judgment: false
  - id: D6
    description: "REQUIREMENTS.md shows zero changes across the whole round-5 range and PRIV-05 remains unticked"
    verification:
      - kind: other
        ref: "git diff --stat -- .planning/REQUIREMENTS.md across c2d980f..HEAD -> empty; line 23 still '- [ ] **PRIV-05**'"
        status: pass
    human_judgment: false
  - id: D7
    description: "./gradlew check is green on the final tree"
    verification:
      - kind: other
        ref: "detekt GREEN (after this plan's fix), ktlintCheck GREEN, test GREEN 1245/0/0 — but jacocoTestCoverageVerification RED at 0.9278 vs a 0.930 floor"
        status: fail
    human_judgment: true
    rationale: "NOT MET, and reported as not met. The failure is measured, bisected to a pre-existing round-5 regression at Redaction.kt:1628, and deliberately left open — see 'The gate this plan could not turn green' below. It is not the RedactionTest flake, so the plan's escape hatch does not apply and is not claimed."

duration: 95min
completed: 2026-08-26
status: complete
---

# Phase 27 Plan 16: Records, fifth time — clause (7), AR-27-11, and the rule round 4 was missing Summary

**The register records its FIFTH re-opening with all six prior clauses byte-intact, defines the residual round 5 created from a measurement rather than a prediction, and adds the one rule it was missing — a residual list must enumerate what the round INTRODUCED — which then immediately caught two red gates round 5 had merged unseen, in the round that wrote it.**

## Performance

- **Duration:** ~95 min
- **Tasks:** 3 of 3
- **Files modified:** 8 (7 records, 2 test sources — `26-SECURITY.md` counted once)

## Task Commits

1. **Task 1: Clause (7), AR-27-11, and a recomputed counter with its population restated** — `406aa74` (docs)
2. **Task 2: Standing-rule clause (vii) and the source pin for AR-27-11** — `614b488` (docs)
3. **Task 3: The phase record — round-5 note, the split residual list, the answered decision with honest provenance** — `b40ad0a` (docs)

---

## MEASUREMENT 1 — the byte-exact prefix assertion

The T-26-02-01 row is one physical line whose cells contain escaped pipes, so the Mitigation cell was
extracted **by unescaped-pipe position** rather than by a naive `split('|')`. Snapshotted before the
edit, re-extracted after, and asserted programmatically:

```
OLD (pre-edit) character length: 29412
NEW (amended)  character length: 38299
OLD is an EXACT CHARACTER PREFIX of NEW: True
```

Re-run after tasks 2 and 3 and still `True`. Round 4 recorded 18352 into 29499; this is round 5's own
pair. The row still carries exactly **8** unescaped pipes, so it is still a 7-cell table row.

## MEASUREMENT 2 — the recomputed counter

The command was taken verbatim from the file's own frontmatter comment and re-run against the
**amended** file after every other task-1 edit:

```
awk -F'|' '/^\| T-26-/ { sev=$5; st=$(NF-1); gsub(/[ *`]/,"",sev); gsub(/[ *`]/,"",st);
    if (st != "closed" && (sev == "high" || sev == "critical")) c++ } END { print c+0 }' \
  .planning/phases/26-coverage-static-analysis-debt-docs/26-SECURITY.md
```

**Raw output: `0`. Rows scanned: 46. Closed: 46.** The value written to `threats_open` is that
output. It was not carried forward from the 2026-08-26 plan-27-13 run, and it was not hand-edited.
The frontmatter YAML still parses (`threats_open: 0`, `asvs_level: 1`, `status: verified`).

The population is restated **in full** in the new dated paragraph rather than cross-referenced, and
the question it forces is asked and answered in writing for `AR-27-11`: it is an `AR-` row and
therefore sits outside the counter **at any severity** — a property of the population, not of the
finding — and had it landed at or above the blocking gate, the honest options were exactly two (give
it a `T-26-` id inside the population, or amend the command and its population comment together),
with leaving the counter reading `0` not among them.

## MEASUREMENT 3 — AR-27-11's reachability, taken at source

The question the severity turns on: **which serialized fields on the MCP emission path are JSON
arrays of strings whose contents are analyst-authored or response-derived?** Enumerated by reading
the schema, not by assuming an answer:

| Where | Found | Bearing |
|---|---|---|
| `mcp/schema/Serialization.kt` (the emission schema) | **ZERO** `List<String>` fields. The only two list fields are `IssueDetails.requestResponses: List<HttpRequestResponse>` and `IssueDetails.collaboratorInteractions: List<Interaction>` | arrays of OBJECTS; their string members open at `:"`, which IS a recognised start |
| multi-item tool results | joined by `McpToolContext.limitedJoin` with a `\n\n` separator | **no JSON array wrapper is emitted at all** |
| `mcp/tools/McpToolModels.kt` | **5** `List<String>` fields — `ComparerSend.items`, `CollaboratorGenerate.options`, `StartAuditMode.requests`, `StartAuditWithRequests.requests`, `StartCrawl.seedUrls` | all INPUT models, reached only via `decode<…>` / `asInputSchema()`; **none** is ever passed to `encodeToString` |
| `McpToolExecutorImpl.routeExternalToolCall` | `context.redactIfNeeded(argsJson.orEmpty())` — D-03 outbound privacy on MODEL-authored args forwarded to a third-party external MCP server | **the one carrier that can emit an arbitrary JSON array of strings through `Redaction.apply`.** Its remote tool schemas are not owned here and are **UNMEASURED** |

**The plan anticipated that the answer would be "UNMEASURED" in those words. It is not, and the row
records what was measured instead:** reachability is MEASURED-and-ZERO for the schema this repository
owns, MEASURED-and-NONZERO for exactly one carrier, and UNMEASURED only for that carrier's remote
half. Filed as a falsified premise in `WINDOWS.md`.

## MEASUREMENT 4 — the bound on AR-27-11's severity, measured rather than reasoned

Driven against the freshly compiled classes (`build/classes/kotlin/main` + `kotlin-stdlib-2.2.21`,
JDK 21) via `Redaction.INSTANCE.apply(raw, RedactionPolicy.Companion.fromMode(mode),
"round5-probe-salt", false)`. STRICT and BALANCED identical for every case. Verbatim:

```
== D-repeat: array element, header at open / STRICT
BEFORE: {"tags":["Cookie: a=SECRET8"]}
AFTER : {"tags":["Cookie: a=SECRET8"]}
IDENTICAL: true

== E: array element, header AFTER an escaped newline / STRICT
BEFORE: {"requests":["GET / HTTP/1.1\r\nCookie: a=SECRET8\r\n\r\n"]}
AFTER : {"requests":["GET / HTTP/1.1\r\nCookie: [STRIPPED]\r\n\r\n"]}
IDENTICAL: false

== F: array element, comma-quote second element, header at open / STRICT
BEFORE: {"tags":["x","Cookie: a=SECRET8"]}
AFTER : {"tags":["x","Cookie: a=SECRET8"]}
IDENTICAL: true

== G: control - object field value open / STRICT
BEFORE: {"notes":"Cookie: a=SECRET8"}
AFTER : {"notes":"Cookie: [STRIPPED]"}
IDENTICAL: false
```

**Two positive controls fired in the same run (E and G)**, which is what makes this a statement about
the rule's reach rather than a broken fixture. The residual is **narrower than "arrays are
uncovered"**: only a header that is the FIRST content of an array-element string escapes, and it
escapes in both the bracket-quote and comma-quote spellings. A realistic raw HTTP message inside an
array element is still stripped. That measurement is what bounds the severity at LOW.

## MEASUREMENT 5 — the RED run of the `THIRD_OPEN_FINDING` assertion

`Redaction.kt`'s rationale-region citation was temporarily removed (`Filed as open finding AR-27-11
rather than absorbed` → `Filed as an open finding rather than absorbed`), leaving the second citation
at `:326` outside the region intact, so the probe tests the REGION and not merely the file.

```
LogicalLineBoundaryScopeTest > theStatedBoundIsPresentWhereAReaderMeetsIt() FAILED
    org.opentest4j.AssertionFailedError at LogicalLineBoundaryScopeTest.kt:155

org.opentest4j.AssertionFailedError: the rationale must ALSO carry `AR-27-11`, the
JSON-ARRAY-ELEMENT string open that stopped being a recognised start when 27-14 narrowed the third
start to a JSON string VALUE open. ALL THREE residuals have to stay traceable from source: the
boundary now recognises a NARROWED set of logical line starts, and a comment that stops naming what
the boundary cannot see leaves the next reader with a boundary that reads complete and is not —
which is how this requirement has been closed wrongly FOUR times already. ==> expected: <true> but
was: <false>
```

`Redaction.kt` was then restored and confirmed **byte-identical** (`git diff --numstat` empty for
that file). GREEN after restore: `LogicalLineBoundaryScopeTest` `tests="4" skipped="0" failures="0"
errors="0"`.

## MEASUREMENT 6 — the untouched-row invariants

| Check | Result |
|---|---|
| `git diff -U0 26-SECURITY.md \| grep -cE '^[-+]\| AR-27-0[0-9] '` after task 1 | **0** — no AR-27-04/05/06/07/08/09/10 row modified |
| `T-26-` rows other than T-26-02-01 changed | **0** |
| task 2's diff on `26-SECURITY.md` | **insert-only, 80 added / 0 removed** — so clause (vi), `threats_open` and every Accepted Risks Log row are byte-unchanged by it |
| task 3's diff on `26-SECURITY.md` | 20 added / **1** removed; the single removed line is the clause (vii) line task 3 extended |
| `.planning/REQUIREMENTS.md` across `c2d980f..HEAD` | **zero changes**; line 23 still `- [ ] **PRIV-05**` |
| `.planning/STATE.md` | **untouched** — owned by the orchestrator |
| `.planning/codebase/CONCERNS.md` | **1 added / 0 removed** — exactly one dated amendment |
| `grep -c '27-14-PLAN.md' / '27-15-PLAN.md' / '27-16-PLAN.md' ROADMAP.md` | **1 / 1 / 1**, each ticked `[x]`; plan count line reads `16/16` |

---

## THE GATE THIS PLAN COULD NOT TURN GREEN — reported as failed, not as passed

The plan's acceptance criterion is "`./gradlew check` is green on the final tree, **or** any failure
is identified as the known `RedactionTest` `SafeRegex` wall-clock flake". **It is not green, and the
remaining failure is NOT that flake, so the escape hatch does not apply and is not claimed.**

Running `check` at all surfaced **two red gates that round 5 had already merged.** Neither plan
27-14's nor plan 27-15's verification command ran `detekt` or `jacoco` — both gated on
`ktlintCheck test` — and neither post-merge wave gate ran `check` either.

**GATE 1 — `detekt`, RED on the base, FIXED here (deviation Rule 3).** Three `MayBeConst` findings,
all on plan 27-15's new raw-string fixtures:

```
RedactingPolicySurvivalSweepTest.kt:1372:13: DECLARATION_SHAPE_FIXTURE can be a `const val`. [MayBeConst]
RedactingPolicySurvivalSweepTest.kt:1442:13: WALK_COMPOSITION_FIXTURE can be a `const val`. [MayBeConst]
RedactingPolicySurvivalSweepTest.kt:1477:13: UNBALANCED_WALK_FIXTURE can be a `const val`. [MayBeConst]
```

Fixed by making the three `const val`. No behaviour change, no growth of `detekt-baseline.xml`
(QUAL-07). `detekt` and `ktlintCheck` are both **GREEN** on the final tree, and the full suite is
**1245 tests, 0 failures, 0 errors**.

**GATE 2 — `jacocoTestCoverageVerification`, RED, MEASURED, BISECTED, DELIBERATELY NOT FIXED.**

```
Rule violated for package com.six2dez.burp.aiagent.redact:
  branches covered ratio is 0.927, but expected minimum is 0.930
```

Bisected against the trees rather than attributed by reasoning:

| Tree | redact BRANCH | missed / covered | Result |
|---|---|---|---|
| `c2d980f` — pre-round-5 | **0.9330** | 13 / 116 | **PASSES** |
| `87c1102` — round-5 base (27-14 + 27-15 merged) | **0.9278** | 14 / 115 | FAILS |
| this plan's final tree | **0.9278** | 14 / 115 | FAILS |

**Exactly ONE branch flipped from covered to missed, and it is `if (remainingMs <= 0L)` at
`Redaction.kt:1628`** — the wall-clock budget-exhaustion guard inside the windowed redaction loop,
on the same `SafeRegex` 50 ms deadline path as the documented `RedactionTest` flake. Covered in **1
of 1** pre-round-5 runs; missed in **2 of 2** round-5 runs.

**What is NOT claimed.** Whether the cause is 27-14's narrowing making the composed regexes cheap
enough that the deadline stops firing incidentally, or ambient CPU load, is **not established by
three samples** and is not asserted. What IS established: the floor has **one branch of headroom**,
and that branch is timing-dependent — a coverage gate partly met by a race.

**Why it was not fixed here.** The two honest options are a deterministic test for the
budget-exhaustion branch, or lowering a QUAL-06 floor. **Lowering a floor to turn a red gate green is
the laundering this phase exists to prohibit**, and writing a same-day test for a wall-clock branch
without understanding whether the round made it unreachable is the closure pattern that has already
failed four times here. Filed in `WINDOWS.md`, in the ROADMAP's INTRODUCED residual group, and in
clause (vii)'s own INTRODUCED list, with the maintainer as owner.

**This is clause (vii) working on its first use.** Both gates are residuals round 5 INTRODUCED and
invisible to the rounds that introduced them. They are on the record only because this plan's
acceptance criteria happened to require `check` — a statement about luck, not method, and the record
says so.

---

## The provenance of round 5's one decision, recorded at the strength the artifacts support

The narrowing (`JSON_STRING_OPEN` bare quote → colon-quote) was:

- **DIRECTED** by the round-5 planning brief — `27-14-PLAN.md` names it as the task, so the executor
  selected nothing.
- **INDEPENDENTLY MEASURED TWICE** by records this round did not write: `27-REVIEW-2.md` CR-03 and
  `27-VERIFICATION-4.md` gap 1.
- Made in a run where **`.planning/config.json` still carries `mode: yolo`** and
  `gsd-tools query check auto-mode` reported `false`. **No human answered any checkpoint during round
  5.**

**Therefore no maintainer signature is claimed anywhere in this round's records.** The provenance is
recorded as *directed-and-twice-measured* — a statement about EVIDENCE, not AUTHORITY — and the
question of whether a person chose it is carried as its own confirmation item (`27-HUMAN-UAT.md`
item 12a), exactly as round 4's item 4 carried the equivalent question.

## Decisions Made

See `key-decisions` in the frontmatter. The two worth restating:

1. **AR-27-11's severity came from a measurement that contradicted the plan's expectation**, and the
   measured mixture (MEASURED-zero / MEASURED-nonzero / UNMEASURED) was recorded rather than
   flattened into the plan's simpler "UNMEASURED" wording.
2. **One red gate was fixed and one was not, on an explicit principle** rather than on effort:
   `const val` changes nothing anyone relies on; a coverage floor is a claim, and moving a claim to
   match a result is the defect this register exists to stop.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] `./gradlew detekt` was RED on the round-5 base and blocked this plan's own gate**
- **Found during:** Task 3 verification
- **Issue:** Three `MayBeConst` findings on plan 27-15's fixtures. Merged unseen because waves 8 and 9 gated on `ktlintCheck test`.
- **Fix:** Three `val` → `const val` in `RedactingPolicySurvivalSweepTest.kt`. No behaviour change, no baseline growth.
- **Verification:** `./gradlew detekt ktlintCheck` BUILD SUCCESSFUL; full suite 1245/0/0.
- **Committed in:** `b40ad0a`

**2. [Rule 1 - Bug] The ROADMAP round-5 note paired a measurement with the wrong population**
- **Found during:** Task 3
- **Issue:** The planner's note stated the sweep was blind to "136 of 1779 declaration lines", pairing CR-01's paren-optional invisible count with a paren-present population.
- **Fix:** Corrected IN PLACE to the re-measured `133 of 1781` / `136 of 1784`, with the 3-line extension-receiver difference named. Recorded in `WINDOWS.md` rather than leaving two numbers on the record.
- **Committed in:** `b40ad0a`

**3. [Rule 1 - Bug] The plan's line citation for the codebase's over-redaction standard was wrong**
- **Found during:** Task 1
- **Issue:** The plan cited `Redaction.kt:570-576`. Measured: that range is the `SafeRegex` fail-open reasoning for `redactCookieSections`. The passage meant is the OVER-REDACTION paragraph beside `MAX_COOKIE_SECTION_LINES` at `:543-548`.
- **Fix:** Clause (7) cites the SYMBOL first and the measured range second, per clause (6)'s own instruction. Filed in `WINDOWS.md`.
- **Committed in:** `406aa74`

### Deferred Issues

**`jacocoTestCoverageVerification` is RED at 0.9278 vs a 0.930 floor** — measured, bisected to
`Redaction.kt:1628`, owner named, deliberately not fixed. See "The gate this plan could not turn
green" above. This is the one acceptance criterion of this plan that is **NOT met**, and it is
reported as not met.

### Scope Boundary — what was deliberately NOT touched

`AR-27-04` (still OPEN at MEDIUM, still owed a human decision, provenance NOT upgraded), `AR-27-08`,
`InjectionPointExtractor.kt:29` (both still owned by Phase 28), `T-27-06-06`, clause (vi) (amended by
27-15 and byte-unchanged here), and `.planning/REQUIREMENTS.md`. Each is asserted byte-unchanged in
Measurement 6 rather than merely claimed.

---

**Total deviations:** 3 auto-fixed (1 × Rule 3, 2 × Rule 1), 1 deferred with an owner.

## Issues Encountered

- **`RedactionTest` flaked once**, on the first full `check`: `jsonPairWithBlankLineBetweenKeyAndValueIsRedacted`, failing on `shift=4: the sweep must prove the pair was REDACTED, not that the window was DROPPED` — the `REDACTION BUDGET EXCEEDED` **non-vacuity control**, not the leak assertion (`BLANK-GAP-SECRET-3` passed). That is the documented tell. Re-run alone: `tests="46" failures="0" errors="0"`. Subsequent full runs were green at 1245/0/0. **The same wall-clock mechanism is what Gate 2 above turns out to be about**, which is why the flake is described here as a mechanism rather than dismissed as noise.
- **The `cp` alias is interactive**, so the first `Redaction.kt` restore after the RED probe silently did not happen. Caught by re-checking `git diff` rather than trusting the command's exit; redone with `/bin/cp -f`. Worth recording because a silently-unrestored probe edit would have been committed.

## Known Stubs

None. No hardcoded empty value, placeholder, TODO or FIXME was introduced. The measurement harness
lives in the run scratchpad and appears in no commit.

## Threat Flags

Each `T-27-16-*` row from the plan's threat model, with its measured outcome.

| Threat ID | Category | Disposition | Outcome |
|---|---|---|---|
| T-27-16-01 | Repudiation — T-26-02-01 clause history | mitigate | **MITIGATED.** Clause (7) appended; the pre-edit Mitigation cell asserted a byte-exact prefix of the amended one PROGRAMMATICALLY — 29412 into 38299, `True` — and re-asserted after tasks 2 and 3. No prior clause reformatted. |
| T-27-16-02 | Spoofing — `threats_open` | mitigate | **MITIGATED.** The documented `awk` re-run against the amended file; raw output `0`, 46 rows scanned, 46 closed. Population restated in full; the question it forces answered in writing for AR-27-11. Not hand-edited, not carried forward. |
| T-27-16-03 | Information Disclosure — AR-27-11's severity and reachability | mitigate | **MITIGATED, and the measurement contradicted the plan.** Reachability enumerated at source before the severity was assigned: zero array-of-string fields in the owned schema, one carrier that can emit one, UNMEASURED only for that carrier's remote half. The inferred half is labelled inferred. |
| T-27-16-04 | Repudiation — provenance of the narrowing | transfer | **TRANSFERRED, as planned.** Recorded as directed-and-twice-measured; no maintainer signature claimed anywhere; the question carried as `27-HUMAN-UAT.md` item 12a on the round-4 item-4 precedent. |
| T-27-16-05 | Elevation of Privilege — the tick on PRIV-05 | mitigate | **MITIGATED.** `REQUIREMENTS.md` byte-unchanged across `c2d980f..HEAD`; PRIV-05 still `[ ]`; the not-satisfied paragraph carries a fifth dated re-confirmation with its prior text unmodified. |
| T-27-16-06 | Tampering — AR-27-04, AR-27-08, T-27-06-06 | accept | **HELD.** All three byte-unchanged, asserted by diff rather than claimed, and stated explicitly in the register, the roadmap and the human-UAT file rather than left to omission. |
| T-27-16-SC | Tampering — package-manager installs | accept | **EMPTY POPULATION, confirmed.** No dependency added, no install command run; the Gradle dependency set is byte-unchanged. |

**New threat surface:** none. This plan changed two TEST files and seven planning documents. No
production source, no network path, no schema, no dependency.

## Next Phase Readiness

- **PRIV-05 is NOT closed by this plan or by round 5.** `REQUIREMENTS.md` is untouched and PRIV-05
  stays `[ ]`. The record says so for the fifth time.
- **Two red gates are open for a maintainer:** `jacocoTestCoverageVerification` (measured, bisected,
  owner named) is the live one; `detekt` is closed. **A future wave gate in this phase should run
  `check`, not `ktlintCheck test`** — that substitution is why both were merged unseen.
- **`AR-27-11` is now filed**, cited twice in `Redaction.kt`, pinned from source by
  `THIRD_OPEN_FINDING`, and owned by the maintainer as item 12. It is pinned by no survival test
  anywhere under `src/`.
- **`AR-27-08` and `InjectionPointExtractor.kt:29` remain owned by Phase 28** and were not touched.
- **Clause (vii) is now binding on every future round**, and its first application caught two
  residuals in the round that wrote it.

## Self-Check: PASSED

Files claimed as modified, verified present on disk: `26-SECURITY.md`, `LogicalLineBoundaryScopeTest.kt`,
`RedactingPolicySurvivalSweepTest.kt`, `ROADMAP.md`, `27-HUMAN-UAT.md`, `COVERAGE.md`, `CONCERNS.md`,
`WINDOWS.md` — all FOUND.

Commits claimed, verified present in `87c1102..HEAD`: `406aa74`, `614b488`, `b40ad0a` — all FOUND.

Task-1 verification command re-run: `grep -c '^| AR-27-11 ' 26-SECURITY.md` → **1**.
Task-2 verification command re-run: `LogicalLineBoundaryScopeTest` → `tests="4" failures="0" errors="0"`.
Task-3 verification command re-run: `REQUIREMENTS.md` diff **empty**; `PRIV-05 NOT SATISFIED` → **1**;
`27-14-PLAN.md` → **1**.

**One acceptance criterion is NOT met and is recorded as failed rather than as passed:**
`./gradlew check` is not green — `jacocoTestCoverageVerification` fails at 0.9278 against a 0.930
floor, pre-existing on the round-5 base, and it is not the `RedactionTest` flake.

---
*Phase: 27-priv-05-gap-closure-sanitize-headers*
*Completed: 2026-08-26*

---

## SUPERSEDED SEVERITY — appended 2026-08-26, this summary's body is byte-unchanged

This SUMMARY records plan 27-16 as it ran, and it is not rewritten. One of its statements has since
been superseded and a reader arriving here cold would otherwise take it as current.

**`AR-27-11` is OPEN at MEDIUM over FOUR MEASURED families, not at LOW over the array element.**
The key decision above — *"Assign AR-27-11 LOW from a MEASURED reachability enumeration"* — and
MEASUREMENT 3's `List<String>`-fields table were correct for the question they asked, and
`27-REVIEW-3.md` CR-01 established that the question was the wrong one: the carrier is not a schema
FIELD but the CONTENT of the `response` string this repository copies verbatim from the target.
Corrected out-of-plan by `2ed1a12`; propagated to this file after `27-VERIFICATION-5.md` gap 2. The
mechanism, the four families and the re-derivation live in the `AR-27-11` row and the correction
section of `26-SECURITY.md`; the maintainer's decision item is `27-HUMAN-UAT.md` item 12, itself
corrected in the same change.
