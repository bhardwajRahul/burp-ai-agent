---
phase: 27-priv-05-gap-closure-sanitize-headers
plan: 06
subsystem: security-register
tags: [security-register, threat-model, accepted-risks, privacy, redaction, record-repair, audit-trail]

requires:
  - phase: 27-priv-05-gap-closure-sanitize-headers
    plan: "04"
    provides: "the boundary fragments and the composer that closed the cookie class on the serialized emission shape, plus the measured header-map finding this plan defines as AR-27-05"
  - phase: 27-priv-05-gap-closure-sanitize-headers
    plan: "05"
    provides: "the authHeaderRegex closure, the pinned 14-site inventory, the boundary-scope tripwire and the reproducible residual probe re-run here"
provides:
  - "26-SECURITY.md T-26-02-01 clause (4) — the second reopening and its re-closure, beside clauses (1)-(3) preserved byte-identically"
  - "AR-27-01 reclassified from accepted residual to live finding, closed on the raw-message-in-JSON shape and explicitly NOT on the header-map shape"
  - "AR-27-02 re-decided on measurement: superseded on the raw-message shape ONLY, still load-bearing on the header-map shape"
  - "AR-27-04 recorded NEW/OPEN at medium with verbatim probe output and a dated disposition"
  - "AR-27-05 DEFINED — the header-map shape carries no line boundary, so sanitizeHeaders is the sole control for request_parse / response_parse"
  - "threats_open COMPUTED by a documented awk command over the register rows, never hand-written"
  - "Standing rule clause (iii) — sibling paths must be enumerated by measurement and the count recorded"
  - "A dated, strictly append-only correction qualifying the v0.10.0 milestone-audit closure note's heading"
affects: [privacy, mcp, security-register, milestone-audit, v0.10.0]

actuals:
  tokens: 19487
  tasks: 3
  commits: 3

tech-stack:
  added: []
  patterns:
    - "Append-and-amend on a one-line markdown table row, verified by a BYTE-PREFIX assertion instead of a removed-line count that no such edit can satisfy"
    - "A frontmatter counter carried with the command that derives it, so the next reader re-runs rather than trusts"
    - "Recording a checkpoint's PROVENANCE (auto-selected vs maintainer-chosen) beside its outcome, so a default cannot later read as a decision"
    - "Separating a no-backstop bound from a live leak in the record, with the call sites that make the difference re-read at source"

key-files:
  created: []
  modified:
    - .planning/phases/26-coverage-static-analysis-debt-docs/26-SECURITY.md
    - .planning/codebase/CONCERNS.md
    - .planning/v0.10.0-MILESTONE-AUDIT.md

key-decisions:
  - "AR-27-04 disposition: accept-residual — AUTO-SELECTED by the configured run mode (mode: yolo auto-selects gate=\"blocking\" checkpoints), NOT maintainer-chosen. Recorded as such in 26-SECURITY.md and here."
  - "FALSIFIED PLAN PREMISE: AR-27-02 is not simply 'SUPERSEDED'. Measured on the compiled classes — on the header-map shape the JSON-key rule redacts {\"X-API-Key\":\"...\"} while {\"Cookie\":\"...\"} and {\"X-Cookie\":\"...\"} survive, because cookie is absent from SENSITIVE_WORDS (Redaction.kt:663-664). Superseded on the raw-message shape only."
  - "AR-27-05 is the fresh id for the header-map residual, DEFINED here for the first time. It is a no-backstop bound, not a live leak: all four ParsedRequest/ParsedResponse sites pass headers = sanitizeHeaders(...), re-read at source."
  - "FALSIFIED ACCEPTANCE CRITERION (task 1): 'no removed line inside clauses (1)-(3)' is unsatisfiable — the whole register row is one physical line. Intent verified by a byte-prefix assertion instead."
  - "GATE DEFECT recorded: the plan's append-only gate grep -c '^-[^-]' is a false zero on markdown bullet lines. Robust form used for all three files."
  - "REQUIREMENTS.md deliberately untouched — PRIV-05's tick is the milestone owner's to re-derive."

requirements-completed: [PRIV-05]

coverage:
  - id: D1
    description: "T-26-02-01 carries a fourth clause and clauses (1),(2),(3) plus the 2026-08-24 reopening narrative survive unedited"
    requirement: PRIV-05
    verification:
      - kind: command
        ref: "splice byte-prefix assertion: old row body is an exact prefix of the new row (5,633 -> 11,320 chars) — PREFIX CHECK PASS"
        status: pass
      - kind: command
        ref: "git diff HEAD -- 26-SECURITY.md | grep -c 'Reopening — 2026-08-24' -> 0 (whole diff)"
        status: pass
    human_judgment: false
  - id: D2
    description: "threats_open is computed from the register rows by a documented command, and the written value equals its output"
    requirement: PRIV-05
    verification:
      - kind: command
        ref: "awk over rows matching '^| T-26-', severity in {high,critical} and status != closed -> 0; 46 rows scanned, 46 closed; written value 0"
        status: pass
    human_judgment: false
  - id: D3
    description: "AR-27-01 reclassified — closed on the raw-message-in-JSON shape, explicitly not on the header-map shape; no longer described as a residual anywhere"
    requirement: PRIV-05
    verification:
      - kind: command
        ref: "Accepted Risks Log row AR-27-01 present and opens 'RECLASSIFIED'; grep for the old residual framing of AR-27-01 -> none remains"
        status: pass
    human_judgment: false
  - id: D4
    description: "AR-27-02's disposition reflects wave 5's fix AND the header-map measurement that narrows it"
    requirement: PRIV-05
    verification:
      - kind: command
        ref: "AttribProbe on compiled classes, STRICT: {\"X-API-Key\"} -> [REDACTED]; {\"Cookie\"} and {\"X-Cookie\"} -> survive"
        status: pass
    human_judgment: false
  - id: D5
    description: "AR-27-04 recorded OPEN with re-runnable probe source and verbatim output, plus a dated disposition and its provenance"
    requirement: PRIV-05
    verification:
      - kind: command
        ref: "ResidualProbe re-run against build/classes/kotlin/main on JDK 21: STRICT HOST-HEADER SURVIVES / URL-FIELD SURVIVES, cookie+apikey controls STRIPPED"
        status: pass
      - kind: command
        ref: "grep -c 'AR-27-04' 26-SECURITY.md -> 11"
        status: pass
    human_judgment: false
  - id: D6
    description: "The new header-map residual has a defined id (AR-27-05) and is recorded with its measurement and its live-leak-vs-no-backstop distinction"
    requirement: PRIV-05
    verification:
      - kind: command
        ref: "grep -n 'headers = sanitizeHeaders' -> McpToolExecutorImpl.kt:369,387 and McpToolLegacy.kt:179,201 (all four ParsedRequest/ParsedResponse sites)"
        status: pass
    human_judgment: false
  - id: D7
    description: "The milestone-audit closure note's heading is qualified without a single line removed from the file"
    requirement: PRIV-05
    verification:
      - kind: command
        ref: "git diff HEAD --unified=0 -- v0.10.0-MILESTONE-AUDIT.md | grep '^-' | grep -v '^--- ' -> empty; git diff --stat HEAD -- REQUIREMENTS.md -> empty"
        status: pass
    human_judgment: false
  - id: D8
    description: "The full build gate passes verbatim and no source was changed by this plan"
    requirement: PRIV-05
    verification:
      - kind: command
        ref: "JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test detekt ktlintCheck -> BUILD SUCCESSFUL in 2m 49s; 170 classes, 1184 tests, 1 skipped, 0 failures, 0 errors"
        status: pass
    human_judgment: false
  - id: D9
    description: "AR-27-04's disposition is a maintainer decision on a shipped 1.0.0 release posture"
    verification: []
    human_judgment: true
    rationale: "Auto-selected by the configured run mode rather than chosen by a human. The record states that plainly and the option is re-openable at no cost, but a maintainer has still not weighed the promise-vs-behaviour gap on a shipped release, and the backlog item that pays the option's cost (README.md:247, SPEC.md:80,86) has not been actioned."

duration: 24 min
completed: 2026-08-24
status: complete
---

# Phase 27 Plan 06: Record Repair — Clause (4), Two Reclassified Residuals and Two New Findings Summary

**The three records now say what happened, twice over: `26-SECURITY.md`'s T-26-02-01 carries a fourth
clause beside its unedited first three, `AR-27-01` is reclassified from accepted residual to live
finding, `AR-27-02` is narrowed by a measurement this plan took rather than inherited, `AR-27-04` and
the newly-defined `AR-27-05` are open findings with quoted probe output, and `threats_open` is
computed by a command instead of asserted by a digit.**

## Performance

- **Duration:** 24 min
- **Started:** 2026-08-24T20:31Z
- **Completed:** 2026-08-24T20:55Z
- **Tasks:** 3
- **Files modified:** 3 (no source files — this plan changes records only)

## Accomplishments

- **T-26-02-01 gains clause (4)** — the second reopening and its re-closure, dated, with the root
  cause in one sentence and what closed it cited by symbol, test and pinned count. Clauses (1), (2),
  (3) and the `## Reopening` narrative are byte-identical.
- **`AR-27-01` is no longer a residual anywhere in the record set.** It is recorded as a live leak of
  the CANONICAL `Cookie:` / `Set-Cookie:` names for the interval between its acceptance and plan
  27-04 — strictly broader than the five variant spellings this phase was created to close — with the
  reason its acceptance was unsound (conditional on a sanitizer that existed on one path only), the
  repository's own green test that pinned the leaking behaviour, and the fact that plan 27-04
  inverted it. **Closed on the raw-message-in-JSON shape; explicitly NOT on the header-map shape.**
- **`AR-27-05` DEFINED** — the fourth residual no plan anticipated. On the header-map shape the
  payload carries no line boundary at all, so `redactIfNeeded` cannot recover a missed cookie and
  `sanitizeHeaders` is the SOLE control for `request_parse` / `response_parse`. Recorded as a
  no-backstop bound, **not** a live leak, with the four call sites that make that difference re-read
  at source.
- **`AR-27-04` recorded OPEN at medium** with the probe re-run in this plan (not copied), its output
  quoted verbatim, its two measured exclusion reasons re-read at source, and a dated disposition
  whose provenance is stated.
- **`threats_open` computed** by a documented `awk` command carried in the frontmatter itself, with a
  fourth audit-trail row making the overclaimed interval legible rather than erased.
- **Standing rule clause (iii)** — sibling paths must be enumerated BY MEASUREMENT with the count
  recorded, because clauses (i) and (ii) were both honoured and this threat was still closed wrongly
  a second time.
- **`CONCERNS.md` gains AMENDMENT 2** (names vs paths) as its own bullet, so the existing amendment
  line was not rewritten at all — zero removed lines inside it.
- **`v0.10.0-MILESTONE-AUDIT.md` gains a dated correction** qualifying the closure note's heading,
  with zero lines removed from the file.

## Task Commits

1. **Task 1: 26-SECURITY.md — clause (4), the AR reclassifications, a computed counter** — `92cc96e` (docs)
2. **Task 2: CONCERNS.md second amendment + milestone-audit correction** — `4cefc59` (docs)
3. **Task 3: AR-27-04 disposition and the item-by-item read-back** — `8f59ce0` (docs)

## Files Created/Modified

- `.planning/phases/26-coverage-static-analysis-debt-docs/26-SECURITY.md` — clause (4); five
  `AR-27-*` rows in the Accepted Risks Log; a new "Open findings on the serialized emission path"
  evidence section with verbatim probe output and the AR-27-04 disposition; a fourth audit-trail row
  plus the interval note; three post-checkpoint sign-off boxes; the read-back table; standing-rule
  clause (iii); a computed-counter comment block in the frontmatter.
- `.planning/codebase/CONCERNS.md` — AMENDMENT 2 on the W-A entry; the `- **Files:**` line extended
  with the three fragments, the composer, the excluded `hostHeaderRegex` and `Serialization.kt`; a
  note in the body-stage-bounds section that the recomposed rules still run inside the unbounded,
  un-deadlined header stage.
- `.planning/v0.10.0-MILESTONE-AUDIT.md` — a dated, append-only correction below the closure note.

## Measurements

Everything below was measured in this plan, on this checkout. Nothing was closed on a SUMMARY's
say-so (`T-27-06-05`).

### The residual probe, re-run rather than quoted

Plan 27-05's `ResidualProbe.java` was re-run against the compiled shipped classes
(`build/classes/kotlin/main`, JDK 21 temurin-21, salt `probe-salt`, `recordMapping=false`), with a
**second shape added** for the header-map case. It is deliberately not committed; it lives in the
session scratchpad and `git status --short` shows only the three record files.

```
==== SHAPE: raw-message-in-JSON (335 bytes) ====
carries an escaped newline: true
STRICT    COOKIE          STRIPPED
STRICT    SETCOOKIE       STRIPPED
STRICT    APIKEY          STRIPPED
STRICT    BEARER          STRIPPED
STRICT    HOST-HEADER     SURVIVES
STRICT    URL-FIELD       SURVIVES
STRICT    BENIGN-CONTROL  SURVIVES
---- STRICT output ----
{"url":"https://shop.example/basket","request":"GET /basket HTTP/1.1\r\nHost: shop.example\r\nCookie: [STRIPPED]\r\nSet-Cookie: [STRIPPED]\r\nX-API-Key: [REDACTED]\r\nAuthorization: [REDACTED]\r\nX-Request-Id: benignprobecontrol\r\n\r\n","response":"HTTP/1.1 200 OK\r\n\r\n"}

==== SHAPE: header-map-in-JSON (246 bytes) ====
carries an escaped newline: false
STRICT    COOKIE          SURVIVES
STRICT    APIKEY          STRIPPED
STRICT    BEARER          STRIPPED
STRICT    HOST-VALUE      SURVIVES
STRICT    BENIGN-CONTROL  SURVIVES
---- STRICT output ----
{"method":"GET","url":"https://shop.example/basket","headers":{"Host":"shop.example","X-Cookie":"probecookiesentinel","X-API-Key":"[REDACTED]","Authorization":"Bearer [REDACTED]","X-Request-Id":"benignprobecontrol"},"body":null}
```

BALANCED reproduced the same verdicts on both shapes. The cookie and API-key rows on the first shape
read `STRIPPED`, so the probe ran against classes containing both waves' fixes — the measurement is
non-vacuous.

### The attribution probe — why AR-27-02 could not be recorded as simply superseded

Bare JSON pairs, STRICT, same classes:

```
bare X-API-Key JSON pair   ->  {"X-API-Key":"[REDACTED]"}
bare X-Cookie   JSON pair   ->  {"X-Cookie":"probecookiesentinel"}
bare Cookie     JSON pair   ->  {"Cookie":"probecookiesentinel"}
bare Host       JSON pair   ->  {"Host":"shop.example"}
```

The auth class HAS an independent backstop on the header-map shape (the JSON-key rule reaches
`"X-API-Key": "…"`). **The cookie class has none**, because `cookie` is absent from `SENSITIVE_WORDS`
(`Redaction.kt:663-664`: `access_token|api_key|apikey|auth|token|secret|password|pwd|session|sid`).
That is AR-27-02, still load-bearing. See **Deviations**.

### Source re-read in-task, cited by file and line

| Claim in the record | Read at |
|---|---|
| three boundary fragments | `Redaction.kt:206`, `:210`, `:218-219` |
| the one composer | `Redaction.kt:236-240` |
| cookie rules built from it | `Redaction.kt:242-246`, `:247-248` |
| auth rule built from it, 16 names | `Redaction.kt:97-105` |
| name predicate + token untouched | `Redaction.kt:119`, `:125`, `:293` |
| `hostHeaderRegex` still real-line anchored (AR-27-04) | `Redaction.kt:1810` — `Regex("(?im)^host:\\s*([^\\s]+)\\s*$")` |
| `cookie` absent from `SENSITIVE_WORDS` (AR-27-02) | `Redaction.kt:663-664` |
| `SiteMapEntry.url` carries the host verbatim | `Serialization.kt:80` (assignment), `:159` (field) |
| raw message embedded with no sanitizer | `Serialization.kt:42-45`, `:49-63`, `:77-83` |
| `sanitizeHeaders` calls the shared predicate | `McpToolHelpers.kt:336` |
| **all four** header-map sites DO sanitize (AR-27-05 is a bound, not a leak) | `McpToolExecutorImpl.kt:369`, `:387`; `McpToolLegacy.kt:179`, `:201` |

**One citation drift noticed and not "fixed":** `Redaction.kt`'s D-27-13 comment and plan 27-05's
SUMMARY cite `Serialization.kt:79` for `SiteMapEntry.url`; the assignment is at `:80` today. The
records written here cite the observed line. No source edit was made — `files_modified` scopes this
plan to records, and `CONCERNS.md` already warns that line numbers in prose drift while symbols do
not.

### The computed counter

```
$ awk -F'|' '/^\| T-26-/ { sev=$5; st=$(NF-1); gsub(/[ *`]/,"",sev); gsub(/[ *`]/,"",st);
      if (st != "closed" && (sev == "high" || sev == "critical")) c++ } END { print c+0 }' \
    .planning/phases/26-coverage-static-analysis-debt-docs/26-SECURITY.md
0

$ awk -F'|' '/^\| T-26-/ {n++; st=$(NF-1); gsub(/[ *`]/,"",st); if (st=="closed") k++}
      END {print n+0 " rows, " k+0 " closed"}' 26-SECURITY.md
46 rows, 46 closed
```

**Written value: `0`.** It equals the output. The command was re-run after every edit in this plan,
including after task 3, and returned `0` each time. `AR-27-04` and `AR-27-05` are MEDIUM, below the
`high` blocking gate — the frontmatter says so explicitly so the `0` cannot be read as their absence.

### Removed lines, enumerated with the reason each was replaced

**`26-SECURITY.md` — 3 removed lines:**

1. `| T-26-02-01 | Information Disclosure | ...` — the whole register row is ONE physical markdown
   line; appending clause (4) necessarily rewrites it. **Verified byte-identical:** the splice
   asserted the old row body is an exact byte PREFIX of the new row (5,633 → 11,320 chars, prefix
   check PASS). No clause (1), (2) or (3) text was altered, reordered or softened.
2. `Two clauses, both learned in this file. They bind every future audit pass in this repository, not`
3. `only ASVS L1 ones.` — the standing rule's preamble said "Two clauses"; a third was added, so the
   sentence would have become false. Replaced with a preamble naming three and preserving the same
   binding statement.

**`CONCERNS.md` — 1 removed line:** the `- **Files:**` line, extended (664 → 1,128 chars) with the
old text preserved as an exact byte prefix. AMENDMENT 2 was inserted as its **own bullet** rather than
appended to the W-A entry line, so the existing `AMENDMENT 2026-08-24` block was not touched at all.

**`v0.10.0-MILESTONE-AUDIT.md` — 0 removed lines.**

### Gate results

| Gate | Result |
|---|---|
| `git diff HEAD -- v0.10.0-MILESTONE-AUDIT.md \| grep -c '^-[^-]'` | `0` |
| robust removed-line check on the milestone audit | empty |
| `git diff HEAD -- 26-SECURITY.md \| grep -c 'Reopening — 2026-08-24'` (whole diff) | `0` |
| `git diff --stat HEAD -- .planning/REQUIREMENTS.md` | no output |
| `grep -c 'AR-27-04' 26-SECURITY.md` | `11` |
| tree-wide scope phrase, all three records | `0`, and no negated form substituted |
| `threats_open` computed vs written | `0` == `0` |

### Full gate, verbatim

```
JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test detekt ktlintCheck

> Task :detekt
> Task :runKtlintCheckOverKotlinScripts
> Task :ktlintKotlinScriptCheck
> Task :runKtlintCheckOverTestSourceSet
> Task :ktlintTestSourceSetCheck
> Task :runKtlintCheckOverMainSourceSet
> Task :ktlintMainSourceSetCheck
> Task :ktlintCheck
> Task :test
> Task :jacocoTestReport

BUILD SUCCESSFUL in 2m 49s
15 actionable tasks: 10 executed, 5 up-to-date
```

Aggregated across **170 test classes: 1184 tests, 1 skipped, 0 failures, 0 errors** — identical to
wave 5's numbers, as expected for a records-only plan. The single skip is
`ExternalMcpClientManagerTest.connectAndListTools_returnsExpectedCount`, `@Disabled` since phase 16
because it needs a live MCP server. **No `SafeRegex` wall-clock flake occurred; no re-run was needed.**

Named gating tests, re-run and verified by name in the JUnit XML before the record was written:

| Class | tests | skipped | failures | errors |
|---|---|---|---|---|
| `SerializedEmissionSiteInventoryTest` | 5 | 0 | 0 | 0 |
| `LogicalLineBoundaryScopeTest` | 3 | 0 | 0 | 0 |
| `SerializedEmissionRedactionTest` (all nests) | 24 | 0 | 0 | 0 |
| `CookieHeaderRuleOwnershipTest` | 3 | 0 | 0 | 0 |
| `CookieHeaderNameParityTest` | 3 | 0 | 0 | 0 |
| `McpToolHelpersTest$SanitizeHeaders` | 17 | 0 | 0 | 0 |
| `RedactionTest` | 46 | 0 | 0 | 0 |

## Checkpoint Outcome — stated plainly

**Task 3 is a `checkpoint:decision` carrying `gate="blocking"`. It was AUTO-SELECTED by the configured
run mode, NOT maintainer-chosen.**

- Config read at execution: `workflow._auto_chain_active` = `false`, `workflow.auto_advance` = `false`,
  **`mode` = `"yolo"`**. On this project `yolo` auto-selects blocking checkpoints even though the
  auto-mode flags read false — a known behaviour, and exactly what the plan's threat `T-27-06-07`
  anticipated when it moved the substantive checks onto automated gates.
- **Option taken: `accept-residual`** (the first option; planners front-load the recommended choice).
  `close-now` was excluded by the plan's own instruction — it is plan-set revision work, not something
  to improvise inside a checkpoint. `follow-up-phase` remains open to the maintainer.
- The automated gates the planner substituted for checkpoint prose **did hold**: the diff gates, the
  computed counter and the quoted probe output are all executor-verified above.
- The disposition is recorded in `26-SECURITY.md` **with its provenance in its own bold paragraph**,
  so a future auditor reads it as a recorded default rather than as a human having weighed the
  release posture.
- **The 8 read-back items are recorded item by item in `26-SECURITY.md`, each with the check actually
  run, and each marked an EXECUTOR verification rather than a maintainer confirmation.** All 8 read
  CONFIRMED; none required a correction.
- **Cost of the chosen option, captured as a concrete backlog item naming the files** (`T-27-06-06`,
  disposition `transfer`): `README.md:247` and `SPEC.md:80` + `:86` must scope STRICT's
  host-anonymisation claim to the paths it actually covers. **Not done in this plan** — `files_modified`
  scopes 27-06 to the three record files, and a user-facing documentation edit changes what ships.
  **Until that edit lands the residual is accepted AND the documentation still overclaims**, which is
  recorded rather than left to be discovered.

## Deviations from Plan

### 1. [Rule 1 — bug in the record being written] The plan's AR-27-02 premise is falsified by measurement

**Found during:** Task 1, while re-measuring rather than inheriting.

**Issue.** The plan's `must_haves` require AR-27-02 to be recorded as *"SUPERSEDED, not
still-deferred … load-bearing only because the primary cookie rule could not fire on the serialized
shape; after plan 27-04 it can."* Measured, that is true of the raw-message-in-JSON shape and **false
in general**. On the HEADER-MAP shape the primary cookie rule still cannot fire (no line boundary of
any kind), and the attribution probe above shows the auth class has a JSON-key backstop there while
the cookie class has none — precisely because `cookie` is absent from `SENSITIVE_WORDS`. Writing the
plan's sentence unqualified would have put a claim in a security register wider than its evidence,
which is the failure class this phase exists to stop.

**Fix.** AR-27-02 is recorded as **"SUPERSEDED ON THE RAW-MESSAGE-IN-JSON SHAPE … and NOT superseded
everywhere, which corrects plan 27-06's own premise"**, with the measurement quoted and the
single-point-of-control bound stated. The plan's intent — re-decide on evidence rather than inherit —
is fully met; its wording is not, and the record says why.

**Files modified:** `26-SECURITY.md` **Commit:** `92cc96e`
**Ledger:** `.planning/WINDOWS.md` entry **16**.

### 2. [Recorded, not worked around] Task 1's acceptance criteria 1-2 are unsatisfiable as written

**Found during:** Task 1 gate execution.

The criteria require that no removed line fall inside clauses (1), (2) or (3) of T-26-02-01. **The
whole register row is ONE physical markdown line**, so appending clause (4) necessarily rewrites it
and it appears as a removed line — for any implementation the same plan mandates. Observed: 1 removed
line, not 0.

The invariant the criteria MEAN was verified directly and holds: the splice asserted that the old row
body is an exact **byte prefix** of the new row (5,633 → 11,320 chars, PREFIX CHECK PASS), so clauses
(1)-(3) survive character for character. No fix applied — the criterion is wrong, the edit is right.

**Ledger:** `.planning/WINDOWS.md` entry **17**.

### 3. [Rule 1 — bug in a shared gate] The plan's append-only gate is a FALSE ZERO on markdown bullet lines

**Found during:** Task 2, cross-checking `CONCERNS.md`.

`git diff HEAD -- <file> | grep -c '^-[^-]'` returned `0` for `CONCERNS.md` while one line had
genuinely been replaced. A removed line beginning `- ` renders in the diff as `--`, which the `[^-]`
class excludes. **This gate cannot see the deletion of any markdown bullet** — and it is the same gate
plan 27-03 met and this plan reuses on the milestone audit, so a false pass there was a live
possibility.

**Fix.** All three files were re-checked with the robust form
`git diff HEAD --unified=0 -- <file> | grep '^-' | grep -v '^--- '`. Results: `26-SECURITY.md` 3
removals (each enumerated above), `CONCERNS.md` 1, **`v0.10.0-MILESTONE-AUDIT.md` 0 — genuinely
append-only**, not a false zero. Both the plan's gate and the robust check are reported above.

**Ledger:** `.planning/WINDOWS.md` entry **18**.

### 4. [Rule 2 — missing critical] The plan's own `whole codebase` prohibition was tripped by the read-back and fixed before commit

**Found during:** Task 3 gate execution.

Read-back item 6 was drafted as *"`grep -c 'whole codebase'` returns 0 in all three files"* — a
sentence that makes itself false, and a direct hit on the phase prohibition (the literal must appear
zero times; a negated form fails it too). Caught by running the check rather than assuming it.
Rewritten to reference the phrase indirectly, and re-verified: **0 occurrences across all three
records.** Fixed before the task-3 commit, so no commit ever carried the violation.

**Files modified:** `26-SECURITY.md` **Commit:** `8f59ce0`

---

**Total deviations:** 1 falsified plan premise corrected in the record (Rule 1), 1 unsatisfiable
acceptance criterion recorded rather than worked around, 1 shared-gate defect found and routed around
with a robust check, 1 self-inflicted prohibition trip caught by its own gate and fixed pre-commit.
**Impact:** the plan's objective is fully met. No record sentence was reworded to satisfy a broken
gate, and no finding was softened to make a criterion pass.

## Issues Encountered

None beyond the deviations above. The build gate was green first time with no `SafeRegex` flake.

## Known Stubs

None. No source file was touched by this plan; no test was added, skipped or disabled; no `TODO`,
`FIXME` or placeholder was introduced into any record.

## Threat Flags

None. No new network endpoint, auth path, file access pattern or schema change. This plan changes
three markdown records only.

## Open findings this plan carries forward

Named here so the next reader inherits a measurement rather than a silence:

1. **AR-27-04** (medium, OPEN) — `Host:` and `SiteMapEntry.url` un-anonymised under STRICT on the
   serialized emission shape. Disposition `accept-residual`, **auto-selected**, with an unactioned
   documentation backlog item (`README.md:247`, `SPEC.md:80,86`).
2. **AR-27-05** (medium, OPEN, defined here) — the header-map shape carries no line boundary, so
   `sanitizeHeaders` is the sole control for `request_parse` / `response_parse`. A no-backstop bound,
   not a live leak.
3. **AR-27-02** — still load-bearing on the header-map shape; the cookie class has no independent
   backstop anywhere.
4. **The vendor auth-header gap** — `authHeaderRegex`'s 16-name alternation is exact-name;
   `X-Shopify-Access-Token` and its kind are matched by no rule at all. Unchanged, still open in
   `CONCERNS.md`.
5. **`REQUIREMENTS.md` line 23 still carries `- [x] **PRIV-05**`** — deliberately untouched. The tick
   predates the audit that refuted it and is the milestone owner's to re-derive.

## PRIV-05 — stated plainly

**On the serialized emission path, PRIV-05 holds** for the cookie-header class and the exact-name
auth-header class, bounded by the pinned 14-site inventory and gated by 24 behavioural tests.
**No remaining path was found on which a cookie value reaches an AI backend in STRICT or BALANCED.**
The header-map shape (AR-27-05) is a single-point-of-control bound, not a leak: all four
`ParsedRequest` / `ParsedResponse` construction sites sanitize, re-read at source in this task.

That said, **this plan does not mark the requirement satisfied and did not touch its tick.** Two
things stand between the measurement above and a "by any path" claim: the emission-site set is pinned
by call SHAPE, so a site written differently is invisible to the tripwire; and the `27-HUMAN-UAT.md`
test 2 live-Burp re-test has not been run against a build containing plans 27-04 and 27-05. It is now
actionable, and it is the evidence a milestone owner should want before re-deriving the tick.

## Next Phase Readiness

Phase 27's record repair is complete. Ready for phase verification / `/gsd-verify-work 27`. The
milestone owner re-derives PRIV-05's tick; `27-HUMAN-UAT.md` test 2 is actionable against a build
containing waves 4 and 5.

## Self-Check: PASSED

- `key-files.modified` all present on disk and all three appear in this plan's commits.
- All three commits resolve in `git log --oneline --all`: `92cc96e`, `4cefc59`, `8f59ce0`.
- Both throwaway probes (`ResidualProbe.java`, `AttribProbe.java`) live only in the session
  scratchpad; `git status --short` shows no untracked file and neither appears in any commit.
- Every task `<acceptance_criteria>` re-run at close-out. All pass except task 1's criteria 1-2,
  unsatisfiable as written and recorded as falsified in Deviation 2 rather than worked around.
- Plan-level `<verification>` re-run: milestone audit append-only (both gate forms), reopening-heading
  count `0` over the whole diff, `REQUIREMENTS.md` untouched, every removed line enumerated with its
  reason, the counter command and output quoted with the written value matching, the AR-27-04 probe
  output present verbatim, and the checkpoint's auto-selected provenance stated here and in the record.
- `.planning/STATE.md` and `.planning/ROADMAP.md` are unmodified — the orchestrator owns those writes.

---
*Phase: 27-priv-05-gap-closure-sanitize-headers*
*Completed: 2026-08-24*
