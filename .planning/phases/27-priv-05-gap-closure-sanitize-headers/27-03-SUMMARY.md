---
phase: 27-priv-05-gap-closure-sanitize-headers
plan: 03
subsystem: security-records
tags: [threat-register, asvs, record-repair, privacy, cookies, milestone-audit]

requires:
  - phase: 27-priv-05-gap-closure-sanitize-headers
    provides: "Redaction.isCookieHeaderName, CookieHeaderRuleOwnershipTest and the per-site classification this plan cites (27-01); CookieHeaderNameParityTest, CP-27-02-01 and AR-27-03 (27-02)"
  - phase: 26-coverage-static-analysis-debt-docs
    provides: "26-SECURITY.md — the register carrying the reopened T-26-02-01 row this plan repairs"
provides:
  - "26-SECURITY.md T-26-02-01 re-closed on source read in-task, as a three-part history that preserves the reopening rather than replacing it"
  - "A standing two-clause audit rule in 26-SECURITY.md: compare a control's WIDTH against its siblings, and never verify a claim with a search NARROWER than the claim"
  - "CONCERNS.md W-A amended to name the third implementation, scoped to the two redaction paths and the passive-scan admitter"
  - "v0.10.0-MILESTONE-AUDIT.md — an append-only dated PRIV-05 closure note that does not pre-empt the next audit's verdict"
affects: [security-register, codebase-concerns, milestone-audit, privacy]

actuals:
  tokens: 5087
  tasks: 2
  commits: 4

tech-stack:
  added: []
  patterns:
    - "A closed register row written as a three-part history — original claim, reopening, closure — so the miss stays legible below the close"
    - "A closure note that names its own scope positively and lists the classified survivors, so a reader is handed the true scope instead of having to disprove a wider one"
    - "A post-mortem lesson promoted from one row into the file's audit-scope section as a standing rule, so the next pass inherits it"

key-files:
  created:
    - .planning/phases/27-priv-05-gap-closure-sanitize-headers/27-03-SUMMARY.md
  modified:
    - .planning/phases/26-coverage-static-analysis-debt-docs/26-SECURITY.md
    - .planning/codebase/CONCERNS.md
    - .planning/v0.10.0-MILESTONE-AUDIT.md

key-decisions:
  - "T-26-02-01 was re-closed on source read in this task (Redaction.kt:158, McpToolHelpers.kt:336, PassiveAiScannerFilters.kt:186) plus both sweeps and a green ownership test — never on a SUMMARY's assertion."
  - "T-27-01-02 (locale) is NOT certified as an active hazard now closed. Independently re-measured here: 5 `toLowerCase(` hits and 1 `lowercase(Locale.getDefault())` hit in src/main/kotlin, ALL inside comments. Zero real locale-sensitive lowering call sites. The records say `guard`, not `fix`."
  - "Two of Task 1's acceptance criteria are unsatisfiable as literally written (`grep -c 'threats_open: 0'` = 1, `grep -c 'status: verified'` = 1) because the file's own pre-existing sign-off and audit-scope prose already contain both strings. Recorded as falsified; the INTENT was verified directly against the frontmatter rather than manufactured into a pass by rewording the sign-off."
  - "The plan's `hostHeaderRegex` and locale premises inherited from waves 1-2 were re-checked, not restated. Nothing in this plan rests on either."
  - "Task 3 resolved `approved` by the human user. The scope was put to them explicitly and certified at the stated width — the two redaction paths and the passive-scan admitter — with the option to narrow it further offered and declined. No clause of the T-26-02-01 row was judged unsupported."

patterns-established:
  - "Before editing a security record, re-run the widened sweep AND the narrow one, and quote both in the record — a closure note verified by the narrower of two available sweeps is the defect being repaired, not the repair"
  - "When an acceptance criterion is unsatisfiable because the target file's own pre-existing prose contains the counted string, report the count and verify the intent — do not reword pre-existing content to make a grep return the number the plan guessed"

requirements-completed: [PRIV-05]

coverage:
  - id: D1
    description: "26-SECURITY.md frontmatter reads threats_open: 0 / status: verified, no gaps_found or threats_open: 1 survives, and the audit trail carries a third dated row ending in 0 open"
    requirement: PRIV-05
    verification:
      - kind: command
        ref: "grep -n 'threats_open: 0' and 'status: verified' on 26-SECURITY.md frontmatter lines 4 and 6; grep -c 'threats_open: 1' = 0; grep -c 'status: gaps_found' = 0"
        status: pass
      - kind: command
        ref: "audit trail table printed in full — exactly 3 data rows, third is `| 2026-08-24 | 46 | 46 | 0 | Phase 27 (27-03) — source re-verification |`"
        status: pass
    human_judgment: false
  - id: D2
    description: "The 2026-08-24 reopening narrative is preserved verbatim and both Sign-Off checkboxes are ticked"
    requirement: PRIV-05
    verification:
      - kind: command
        ref: "grep -c 'Reopening — 2026-08-24' = 1 (heading intact at line 206); grep -c '^- \\[ \\]' = 0"
        status: pass
    human_judgment: false
  - id: D3
    description: "The T-26-02-01 row cites isCookieHeaderName, Locale.ROOT, all three guarding tests and all three accepted residuals, and its Status cell reads closed"
    requirement: PRIV-05
    verification:
      - kind: command
        ref: "one grep per token against the row — all 8 tokens present; grep -c '^| T-26-02-01 |.*| closed |$' = 1; zero `| **open** |` cells remain in the register"
        status: pass
    human_judgment: false
  - id: D4
    description: "The standing rule is present with BOTH clauses — width-against-siblings, and no-verification-narrower-than-its-claim"
    requirement: PRIV-05
    verification:
      - kind: command
        ref: "grep -c 'Standing rule added' = 1; both clauses present under that heading with T-26-02-01 and this phase's own first draft as the two worked examples"
        status: pass
    human_judgment: false
  - id: D5
    description: "The milestone audit change is strictly additive — one insertion hunk, frontmatter/scores/gaps byte-identical, REQUIREMENTS.md untouched"
    requirement: PRIV-05
    verification:
      - kind: command
        ref: "git diff -U0 shows the single hunk `@@ -194,0 +195,51 @@`; git diff | grep -c '^-' = 1 (the diff header); frontmatter diffed byte-for-byte against HEAD = identical; git diff --quiet .planning/REQUIREMENTS.md exits 0"
        status: pass
    human_judgment: false
  - id: D6
    description: "The code the records describe is in the state the records certify — both sweeps 0, four guarding classes green, full build green"
    requirement: PRIV-05
    verification:
      - kind: command
        ref: "narrow sweep = 0 and widened sweep = 0, run before the edits and again after the build"
        status: pass
      - kind: unit
        ref: "CookieHeaderRuleOwnershipTest — tests=3 skipped=0 failures=0 errors=0"
        status: pass
      - kind: unit
        ref: "McpToolHelpersTest 76 / SanitizeHeaders 17, CookieHeaderNameParityTest 3, RedactionTest 46 — all 0 failures"
        status: pass
      - kind: command
        ref: "JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test detekt ktlintCheck — BUILD SUCCESSFUL in 2m 48s"
        status: pass
    human_judgment: false
  - id: D7
    description: "Every claim written into the three records is scoped to the two redaction paths and the passive-scan admitter, names the surviving non-redacting matchers, and confines the locale statement to the two header-name functions this phase changed"
    requirement: PRIV-05
    verification: []
    human_judgment: true
    rationale: "A grep can prove the forbidden phrasings are absent (verified: 0 in all three files, unchanged from a 0 baseline) but cannot prove a prose scope is the RIGHT scope, nor that the four classifications are convincing. That judgment was made at Task 3's blocking-human checkpoint: the human user certified the scope as stated and declined the option to narrow it further, and spot-checked two classifications at source. Verdict recorded verbatim in the checkpoint section below."
  - id: D8
    description: "T-27-01-02 is recorded as a guard against introducing a locale-sensitive spelling rather than as an active hazard now closed"
    requirement: PRIV-05
    verification:
      - kind: command
        ref: "independently re-measured in this task: 5 `toLowerCase(` hits and 1 `lowercase(Locale.getDefault())` hit in src/main/kotlin, every one inside a comment; the 114 `lowercase()` hits are the locale-agnostic Kotlin spelling"
        status: pass
    human_judgment: false

duration: 24 min
completed: 2026-08-24
status: complete
---

# Phase 27 Plan 03: Repairing the Record Summary

**`T-26-02-01` is closed again — on `Redaction.kt:158`, `McpToolHelpers.kt:336` and two sweeps read in the closing task rather than on any document's say-so — and `26-SECURITY.md` now carries the standing rule that would have caught the original false close: compare a control's width against its siblings, and never verify a claim with a search narrower than the claim.**

## Performance

- **Duration:** 24 min
- **Tasks:** 2 executed and committed; Task 3 is a `blocking-human` checkpoint, NOT yet resolved
- **Files modified:** 3 records (no production code, no tests)

## Accomplishments

- **The register is true again, and it still remembers being wrong.** T-26-02-01 is a three-part history — the original narrow claim that held, the 2026-08-24 reopening, the Phase 27 closure — so a reader meets the miss before the close. The `Reopening — 2026-08-24` section at line 206 is byte-unchanged.
- **The closure is grounded in source read in-task.** Every symbol, line number and sweep result in the row was observed here, in this task. Nothing was copied forward from a SUMMARY except commit hashes and test names, which the plan explicitly permits as pointers.
- **The lesson outlived the row.** The reopening note's best sentence — *"Source-level presence proved the control exists; it could not prove the control is as wide as the requirement"* — is now a standing rule in the audit-scope section, with a second clause the phase learned about itself.
- **The scope was narrowed, not widened, in all three records.** Each states the singularity claim as covering the two redaction paths and the passive-scan admitter, and each names the four surviving non-redacting matchers with a reason. The forbidden phrasings appear 0 times in all three files, against a 0 baseline.
- **The locale claim was NOT laundered into a closure.** Re-measured here rather than inherited: zero real locale-sensitive lowering call sites. The records say the `Locale.ROOT` work is a guard against introducing the hazardous Java spelling, not the closure of an active hazard.
- **The milestone audit was not graded by the phase it graded.** One insertion hunk, frontmatter byte-identical, `REQUIREMENTS.md` untouched.
- **A human, not an agent, certified the re-close.** The scope question was put to the user explicitly and certified at the stated width with the narrower option declined, and two classifications were spot-checked at source and both confirmed — one of them yielding a *stronger* property than the classification had claimed.

## Task Commits

1. **Task 1: re-close T-26-02-01 on source, with the standing width rule** — `ef8baf0` (docs)
2. **Task 2: amend the W-A over-claim and note the PRIV-05 closure** — `e5539f5` (docs)
3. **Task 3: human confirmation** — `blocking-human` checkpoint, **RESOLVED `approved`** by the human user. No commit (verification only); the verdict is recorded below and in the amend commit for this SUMMARY.

## The five source commands, with literal output as observed

Run at the top of Task 1, BEFORE any record was edited.

### Command 1 — the shipped predicate

```
$ grep -n 'fun isCookieHeaderName' -A 6 src/main/kotlin/com/six2dez/burp/aiagent/redact/Redaction.kt
158:    fun isCookieHeaderName(name: String): Boolean = name.lowercase(Locale.ROOT).contains(COOKIE_NAME_TOKEN)
159-
160-    /**
161-     * The passive scanner's dedicated cookie-section header.
162-     *
163-     * Public, and owned HERE in the redactor rather than in the emitter, because
164-     * scanner/PassiveAiScannerPrompts.kt imports this constant instead of writing the literal
```

### Command 2 — the single call site in `sanitizeHeaders`

```
$ grep -n 'isCookieHeaderName' src/main/kotlin/com/six2dez/burp/aiagent/mcp/tools/McpToolHelpers.kt
336:        if (policy.stripCookies && Redaction.isCookieHeaderName(name)) {
```

Exactly one hit. `sanitizeHeaders` carries no second cookie test.

### Command 3 — the NARROW sweep

```
$ grep -rn 'contains("cookie")' src/main/kotlin --include=*.kt | grep -v 'isCookieHeaderName' | wc -l
0
```

### Command 4 — the WIDENED sweep (the one that supports the claim)

```
$ grep -rnE 'equals\("(Set-)?Cookie"|startsWith\("(Set-)?Cookie:"|headerValue\("(Set-)?Cookie"\)|contains\("cookie"\)|== "(set-)?cookie"' \
    src/main/kotlin --include=*.kt | grep -v 'isCookieHeaderName' \
  | grep -vE 'PassiveAiScannerAnalysis\.kt|PassiveAiScannerHeuristics\.kt|BountyPromptTagResolver\.kt|ActiveAiScanner\.kt|Redaction\.kt' | wc -l
0
```

Both were re-run after the full build and both still returned `0`.

### Command 5 — `CookieHeaderRuleOwnershipTest`

```
$ JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test --tests '*CookieHeaderRuleOwnershipTest'
BUILD SUCCESSFUL in 8s
7 actionable tasks: 7 executed

$ grep -o 'tests="[0-9]*" skipped="[0-9]*" failures="[0-9]*" errors="[0-9]*"' \
    build/test-results/test/TEST-...CookieHeaderRuleOwnershipTest.xml
tests="3" skipped="0" failures="0" errors="0"
```

Test names confirmed present by name in the JUnit XML, not inferred from a green build (the check 27-02 made mandatory):

```
testcase name="noHandWrittenCookieMatcherSurvivesInTheRedactionPaths()"
testcase name="everyCookieHeaderNameMatcherInMainIsClassified()"
testcase name="theOwnershipScanIsNonVacuous()"
```

## The unfiltered widened sweep, paired line-by-line with its classification

Run with the file exclusions and the predicate filter DROPPED, so the full list is visible. Seven lines in five files. Every line is either a classified non-redacting site or the owner file's own composition — no line is an unclassified matcher.

| Sweep line | Classification | Why it is not a redaction path |
|---|---|---|
| `BountyPromptTagResolver.kt:144` — `.filter { it.startsWith("Cookie:", ignoreCase = true) }` | EXTRACTOR over already-redacted text | `requestRedacted` / `responseRedacted` are assigned from `Redaction.apply(...)` before `extractCookies` sees them |
| `BountyPromptTagResolver.kt:150` — `.filter { it.startsWith("Set-Cookie:", ignoreCase = true) }` | EXTRACTOR over already-redacted text | Same chain as `:144` |
| `PassiveAiScannerHeuristics.kt:102` — `request.headerValue("Cookie") ?: ""` | LOCAL-ANALYSIS | Feeds `authCookieHint.containsMatchIn` and collapses to a boolean gate; no value crosses the process boundary |
| `PassiveAiScannerHeuristics.kt:117` — `.filter { it.name().equals("Set-Cookie", ignoreCase = true) }` | LOCAL-ANALYSIS | Collapses to the `sameSiteSecure` boolean |
| `ActiveAiScanner.kt:936` — `request.headerValue("Cookie") ?: ""` | LOCAL-ANALYSIS | `hasAuthContext` reduces it to a boolean; `stripAuthHeaders` below removes the header outright |
| `ActiveAiScanner.kt:1411` — `request.headerValue("Cookie") ?: ""` | REQUEST MUTATOR | The `InjectionType.COOKIE` branch rewrites the header with an attack payload and sends it to the TARGET |
| `PassiveAiScannerAnalysis.kt:267` — `.filter { it.name().equals("Cookie", ignoreCase = true) }` | EXTRACTOR, redacted downstream | Feeds `cookieSectionLines(...)`; `redactScanMetadata` calls `Redaction.apply` unconditionally before the prompt |

`Redaction.kt` itself produces no sweep hit in this spelling set — its two regexes are composed from `COOKIE_NAME_TOKEN` rather than written as literal name comparisons. The three consumer sites (`McpToolHelpers.kt:336`, `PassiveAiScannerFilters.kt:186`, and the regexes) are filtered out by `grep -v 'isCookieHeaderName'` because they call the predicate — which is the point.

**This table was the input to step 3 of the Task 3 checkpoint.** Two of its rows — `PassiveAiScannerAnalysis.kt:267` and `ActiveAiScanner.kt:1411` — were spot-checked at source by the human and both confirmed; the orchestrator independently re-ran the unfiltered sweep and matched this table row-for-row. See the checkpoint section below.

## Which clauses of the old T-26-02-01 claim were kept, and which were corrected

| Clause of the pre-reopening row | Disposition |
|---|---|
| "case-insensitive matching of `Cookie`, `Set-Cookie`, `Authorization`, `Proxy-Authorization`, `X-API-Key`, `Api-Key` and `Host` is asserted per privacy mode" | **KEPT verbatim in substance** — it was true then and is true now, and is labelled in the row as "the original narrow claim, which holds" |
| "the PRIV-05 failure mode — a real-world header spelling that the matcher misses — cannot recur unnoticed in this second redaction path" | **CORRECTED.** It was false when written: the matcher was an exact-name test. It is now true, but only because 27-01 shipped the shared predicate and 27-02 shipped the parity test — so the row states the mechanism rather than repeating the assertion |
| The implicit scope of "cannot recur" | **NARROWED and stated positively.** The row now says the rule is singular across the two redaction paths and the passive-scan admitter, names the four survivors, and names the tripwire's five-spelling bound |
| (absent from the old row) what is NOT closed | **ADDED** — AR-27-01, AR-27-02, AR-27-03, each in half a sentence |
| (absent from the old row) the locale position | **ADDED, at the measured width** — a guard, not the closure of an active hazard |

## The exact scope sentence written into each record

Recorded here so the scope is checkable without opening all three files.

**`26-SECURITY.md`, T-26-02-01 row:**

> **Scope of the singularity claim, stated positively so it cannot silently widen:** `isCookieHeaderName` is the single cookie-header-name rule across **the two redaction paths and the passive-scan admitter** — `Redaction.apply`'s two regexes, `McpToolHelpers.sanitizeHeaders` (`:336`) and `PassiveAiScannerFilters.sanitizeHeadersForPrompt` (`:186`) — and at no wider scope than those three sites.

**`.planning/codebase/CONCERNS.md`, W-A amendment clause (3):**

> "Agree by construction" holds across **the two redaction paths and the passive-scan admitter** — `Redaction.apply`'s `cookieHeaderRegex` and `setCookieHeaderRegex`, `McpToolHelpers.sanitizeHeaders` (`:336`) and `PassiveAiScannerFilters.sanitizeHeadersForPrompt` (`:186`) — because `Redaction.isCookieHeaderName` (`Redaction.kt:158`) is the single predicate those consumer sites call, and both regexes are composed from the same `COOKIE_NAME_TOKEN` (`:91`). Read that scope literally: four other cookie-header-name matchers survive in `src/main/kotlin` […]

**`.planning/v0.10.0-MILESTONE-AUDIT.md`, closure note:**

> **Scope of the singularity claim, stated at the width the evidence supports.** `isCookieHeaderName` is the single cookie-header-name rule across **the two redaction paths and the passive-scan admitter** — `Redaction.apply`'s two regexes, `McpToolHelpers.sanitizeHeaders` and `PassiveAiScannerFilters.sanitizeHeadersForPrompt` (`:186`) — and no wider.

Scope-guard grep, all three files, against a `0` baseline taken before any edit:

```
$ grep -ci 'whole codebase\|entire codebase\|everywhere in the tree' <each of the three records>
0   26-SECURITY.md          (baseline 0)
0   CONCERNS.md             (baseline 0)
0   v0.10.0-MILESTONE-AUDIT.md (baseline 0)
```

## Upstream plan premises re-checked, not restated

The plan was authored before waves 1 and 2 ran. Three premises were flagged as falsified upstream; I re-verified each here rather than accepting the correction on trust, because taking a document's word for a fact is the exact failure this phase repairs.

**1. The locale claim — CONFIRMED FALSIFIED, by my own measurement.**

```
$ grep -rn 'lowercase()' src/main/kotlin --include=*.kt | grep -v 'Locale' | wc -l
114
$ grep -rn 'toLowerCase(' src/main/kotlin --include=*.kt
McpToolHelpers.kt:323   // ... lowercase() is already locale-agnostic (it compiles to toLowerCase(Locale.ROOT)), so this
McpToolHelpers.kt:325   // ... locale-SENSITIVE spelling — Java's toLowerCase(), or lowercase(Locale.getDefault()), both
Redaction.kt:144        //  * it compiles to `toLowerCase(Locale.ROOT)` — so with the JVM default locale set to `tr-TR`,
Redaction.kt:147        //  * default, `"COOKIE".toLowerCase()` and `toLowerCase(Locale.getDefault())` were both measured to
Redaction.kt:152        //  * `Locale.getDefault()`, or switching to Java's `toLowerCase()`, would break the control without
$ grep -rn 'lowercase(Locale.getDefault())' src/main/kotlin --include=*.kt
McpToolHelpers.kt:325   // ... (the same comment line)
```

All 6 locale-sensitive spellings are inside comments this phase added. **Zero real locale-sensitive lowering call sites**, and the 114 are the locale-agnostic Kotlin spelling. `T-27-01-02` is therefore recorded in all three records as a **guard against introducing** the hazardous spelling, never as "an active hazard, now closed". The plan's framing of 114 surviving hazards is not repeated anywhere.

**2. `hostHeaderRegex` line-anchoring — confirmed fixture-dependent.** It is `(?im)^host:\s*([^\s]+)\s*$`. No record produced by this plan states it as an absolute property; no claim here rests on it at all, so no conditional phrasing was needed.

**3. `CP-27-02-01` / `AR-27-03` — treated as resolved and discharged.** The decision was made by the human user (`keep-map-plus-backlog`), and the ROADMAP backlog entry was written by the orchestrator (visible in this branch's history as `8e2a5ad chore(27-02): record wave 2 progress and the AR-27-03 backlog entry`). AR-27-03 is cited in both the register row and the audit note as an accepted residual, and is NOT reported anywhere as an outstanding obligation.

## Deviations from Plan

### Falsified plan premises, recorded rather than manufactured into a pass

**1. [Plan defect — falsified criterion] Two of Task 1's acceptance criteria are unsatisfiable as literally written**

- **Found during:** Task 1, acceptance-criteria loop
- **Issue:** The plan asserts `grep -c 'threats_open: 0' 26-SECURITY.md` returns `1` and `grep -c 'status: verified' 26-SECURITY.md` returns `1`. Both count LINES, and both strings already appear in the file's own pre-existing prose independently of the frontmatter:
  - `threats_open: 0` — line 26 (the audit-scope paragraph: *"the workflow declares sufficient when `threats_open: 0`"*) and line 137 (the Sign-Off checkbox the plan instructs me to TICK, not reword). Baseline before my edit: **2**. After setting the frontmatter: **3**.
  - `status: verified` — line 138 (the other Sign-Off checkbox). Baseline: **1**. After setting the frontmatter: **2**.
  Making either grep return `1` would require deleting or rewording pre-existing content that the plan elsewhere tells me to preserve and tick. That is gaming a check, not passing it.
- **What I did instead:** reported the literal counts and verified the criteria's INTENT directly — the frontmatter reads `status: verified` (line 4) and `threats_open: 0` (line 6), and `grep -c 'threats_open: 1'` = **0** and `grep -c 'status: gaps_found'` = **0**, so no stale value survives anywhere in the file. The plan's `<verify><automated>` gate fails on these two clauses for the same reason and for no other; every other clause of that gate passes.
- **Files modified:** none beyond the intended edit
- **Committed in:** `ef8baf0`

**2. [Rule 1 — Bug, self-inflicted] My own row text broke the reopening-preservation guard**

- **Found during:** Task 1, acceptance-criteria loop
- **Issue:** The criterion `grep -c 'Reopening — 2026-08-24'` must return `1`, and it is a real guard: it proves the reopening heading survived. My first draft of the T-26-02-01 row cross-referenced the section by its exact heading string, which pushed the count to `2` and defeated the guard's precision — a second literal occurrence means the grep can no longer distinguish "heading intact" from "heading deleted but mentioned in a table".
- **Fix:** reworded my own cross-reference to *"see the unedited reopening section dated 2026-08-24 at the foot of this file"*. The pre-existing heading at line 206 is untouched; count back to `1`. I did not touch the reopening section to fix this — only my own new text.
- **Verification:** `grep -c 'Reopening — 2026-08-24'` → `1`, and `grep -n` confirms the single hit is the heading at line 206.
- **Committed in:** `ef8baf0`

---

**Total deviations:** 2 (1 falsified criterion reported as falsified, 1 self-inflicted bug auto-fixed).
**Impact:** No scope change and no production code touched. Deviation 1 is the one worth reading: a criterion that cannot be satisfied without rewording content the same plan protects is a plan defect, and the correct response is to report the number and prove the intent — not to edit the file until the grep agrees.

## Issues Encountered

None beyond the deviations. The known `RedactionTest` wall-clock flake did not occur — `RedactionTest` was green on every run (46 tests, 0 failures).

## Verification

```
$ JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test detekt ktlintCheck
BUILD SUCCESSFUL in 2m 48s
15 actionable tasks: 10 executed, 5 up-to-date
```

Full suite aggregated from the JUnit XML: **162 classes / 1152 tests / 0 failures / 0 errors / 1 skipped**.

The four classes the checkpoint asks about, each read by name from its XML:

| Class | tests | failures | errors |
|---|---|---|---|
| `McpToolHelpersTest` (14 nested classes) | 76 | 0 | 0 |
| — of which `$SanitizeHeaders` | 17 | 0 | 0 |
| `CookieHeaderNameParityTest` | 3 | 0 | 0 |
| `CookieHeaderRuleOwnershipTest` | 3 | 0 | 0 |
| `RedactionTest` | 46 | 0 | 0 |

Record-level checks:

- `26-SECURITY.md`: `threats_open: 0` and `status: verified` in frontmatter; `threats_open: 1` and `status: gaps_found` both `0`; audit trail exactly 3 data rows, third ending `0` open; `grep -c '^- \[ \]'` → `0`; `grep -c 'Standing rule added'` → `1`, both clauses present; `grep -c 'Reopening — 2026-08-24'` → `1`; T-26-02-01 Status cell `closed`; zero `| **open** |` cells remain.
- `CONCERNS.md`: `McpToolHelpers` → 2 lines, `isCookieHeaderName` → 2 lines (amendment + `**Files:**`), `CookieHeaderNameParityTest` → 1, `CookieHeaderRuleOwnershipTest` → 1, `PassiveAiScannerAnalysis|BountyPromptTagResolver` → 1.
- `v0.10.0-MILESTONE-AUDIT.md`: single hunk `@@ -194,0 +195,51 @@`; `git diff | grep -c '^-'` → `1` (the diff header); frontmatter diffed byte-for-byte against `HEAD` → **identical**; `AR-27-01` / `AR-27-02` / `AR-27-03` each present; `The gap: PRIV-05` heading → `1`.
- `git diff --quiet .planning/REQUIREMENTS.md` exits `0`; `git diff --quiet src/main/kotlin` exits `0`; `git diff --quiet detekt-baseline.xml` exits `0`.
- No `STATE.md` or `ROADMAP.md` modification on this branch — the orchestrator owns those writes.

## Threat register outcome (this plan's own register)

| Threat ID | Disposition | Outcome |
|---|---|---|
| `T-27-03-01` (closing a record on a document's say-so) | mitigate | **Closed.** All five source commands run before any edit; outputs quoted above and in the row. |
| `T-27-03-02` (erasing the reopening history) | mitigate | **Closed.** Heading intact at line 206; my own duplicate reference removed rather than the section. |
| `T-27-03-03` (a phase editing the verdict that graded it) | mitigate | **Closed.** One insertion hunk; frontmatter byte-identical; `REQUIREMENTS.md` untouched. |
| `T-27-03-04` (closure note overclaiming) | mitigate | **Closed.** AR-27-01/02/03 named in the row and in the audit note. |
| `T-27-03-05` (the width lesson staying a one-row post-mortem) | mitigate | **Closed.** Promoted to a standing rule in the audit-scope section. |
| `T-27-03-06` (certifying a wide claim on a narrow sweep) | mitigate | **Closed.** All four mitigations landed: widened sweep run and quoted; scope stated positively in all three records; the four survivors named; and the human ran the widened and unfiltered sweeps and spot-checked two classifications at source, with the scope put to them explicitly and certified at the stated width. |
| `T-27-03-SC` (package installs) | accept | Markdown only; no package installed, no dependency added. |

## Threat Flags

None — no new network endpoint, auth path, file access pattern or schema change at a trust boundary. This plan changed three markdown records and no code.

## Task 3 — `blocking-human` checkpoint, RESOLVED

**Verdict: `approved`.** Given by the **human user**, not by the orchestrator and not self-approved
by this executor. The phase halted here after every other task was executed and committed, so the
halt landed as late as it could.

### The human's answer to the SCOPE question (step 7)

Certified as stated, at the stated width and no wider:

> `isCookieHeaderName` is the single cookie-header-name rule across **the two redaction paths and the
> passive-scan admitter** — `Redaction.apply`'s two regexes, `McpToolHelpers.sanitizeHeaders:336`,
> and `PassiveAiScannerFilters.sanitizeHeadersForPrompt:186` — and at no wider scope. The four
> survivors stand as classified non-redacting.

The scope was put to the user **explicitly**, with the option to narrow it further offered and
**declined**. That matters for a later reader: the width in the record is the width a human chose
after seeing the alternative, not a width nobody questioned.

### The two classifications the human spot-checked, and what they found

Both were traced to their consumer by reading source in the worktree — not from this executor's
report, which is the entire point of the second pair of eyes.

**`PassiveAiScannerAnalysis.kt:267` — EXTRACTOR, non-leaking. Classification confirmed.**
`cookieHeaderValues` → `cookieSectionLines` → the scan-metadata blob → `redactScanMetadata`, which
calls `Redaction.apply` **unconditionally** at `PassiveAiScannerPrompts.kt:49`. The comment at `:41`
states there is deliberately no `if (mode == PrivacyMode.OFF)` bypass. This also confirms 27-01's
directional argument: narrowing this filter puts *fewer* cookie values into the prompt, so the site
is fail-safe **by direction** rather than by a downstream step happening to stay correct — a
stronger property than the one the classification claimed.

**`ActiveAiScanner.kt:1411` — REQUEST MUTATOR, non-leaking. Classification confirmed.**
Inside the `InjectionType.COOKIE` branch it substitutes an attack payload into the `Cookie` value and
calls `withRemovedHeader("Cookie").withAddedHeader("Cookie", newCookies)`. The mutated request goes
to the **target**. Nothing on this path reaches an AI backend.

### Orchestrator-side verification, run independently before the gate reached the user

All eight `how-to-verify` steps were re-run on this branch, independently of this executor's report:

| Check | Result |
|---|---|
| Narrow sweep | `0` |
| Widened filtered sweep | `0` |
| Widened **unfiltered** sweep | exactly **7 lines**, all inside the four classified files, matching the classification table above **row-for-row** |
| `Reopening — 2026-08-24` | `1` — the heading survived |
| The literal `whole codebase` | `0` in all three records |
| Fork base | exactly `8e2a5ad` |
| Diff scope | **four planning files, zero source files** |
| Locale finding | reproduced — the 5 `toLowerCase(` and 1 `lowercase(Locale.getDefault())` hits are all inside comments this phase added (`McpToolHelpers.kt:323,325`, `Redaction.kt:144,147,152`); **zero** real locale-sensitive lowering call sites |

### On the two unsatisfiable acceptance criteria

The handling was reviewed and confirmed correct, and is to stay recorded as a **plan defect rather
than an execution shortfall**. `grep -c 'threats_open: 0'` → `3` and `grep -c 'status: verified'` →
`2` are counts of pre-existing Sign-Off and audit-scope prose that the same plan requires be
preserved and ticked; driving either to `1` would mean deleting content the plan protects. Reporting
the counts and verifying the intent directly — frontmatter `status: verified` / `threats_open: 0`,
with `threats_open: 1` and `status: gaps_found` both `0` — is the correct resolution. The next reader
should see that the criteria were **wrong**, not that they were skipped.

The self-caught near-miss in Deviation 2 — my own cross-reference defeating the
reopening-preservation guard, fixed by rewording my text rather than touching the history — was
likewise kept in the record deliberately.

**No clause of the T-26-02-01 row was judged unsupported.** Nothing was named as over-claimed, in
either direction.

## Next Phase Readiness

- **Complete.** The Task 3 human confirmation is resolved `approved`; every task is executed and committed. Ready for phase-level verification.
- **For the next `/gsd-audit-milestone` run:** the v0.10.0 verdict is deliberately unedited. Re-derive it; do not inherit the closure note's conclusion.
- **For the next security audit in this repo:** the standing rule in `26-SECURITY.md` is binding. An L1 pass that closes a coverage threat must name the sibling implementations it compared, and a closing note must state the scope its sweep actually covered.

## Self-Check: PASSED

- Created file exists on disk: `27-03-SUMMARY.md`.
- Both task commits exist: `ef8baf0`, `e5539f5`.
- All three modified records exist and carry the intended changes, each verified by grep above.
- `git diff HEAD~2 HEAD --numstat` lists exactly the three records. **No `STATE.md`, no `ROADMAP.md`, no `REQUIREMENTS.md`, no source or test file.**
- Working tree clean apart from this SUMMARY at the moment of writing.
- Every task-level `<acceptance_criteria>` was executed. The two that could not be satisfied as literally written are recorded above as Deviation 1 with their measured counts and the reason, not silently skipped.
- Task 3 was resolved by the human user, not self-approved by this executor. Their verdict, their answer to the scope question and their two source-level spot-checks are recorded verbatim below.

---
*Phase: 27-priv-05-gap-closure-sanitize-headers*
*Completed: 2026-08-24 — Task 3 confirmed `approved` by the human user*
