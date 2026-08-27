---
phase: 28-the-issue-detail-cookie-carrier-auditissue-detail-scanner-is
plan: 03
subsystem: scanner / privacy
tags: [PRIV-05, SC6, SC5, AR-28-01, AR-27-08, evidence-tail, residual]
status: checkpoint
requires: ["28-01", "28-02"]
provides:
  - "EvidenceTailReachTest — the derived cap set, the two-directional reach measurement, and the drift tripwire feeding AR-28-01's severity"
affects:
  - ".planning/phases/26-coverage-static-analysis-debt-docs/26-SECURITY.md (NOT YET WRITTEN — gated by task 2)"
tech-stack:
  added: []
  patterns:
    - "Source-walk derivation with a pinned tripwire: the walk is the source of truth, the pin turns drift red"
    - "Two-directional reach measurement where the NEGATIVE case still asserts the analyzer fired, so 'did not reach' cannot be confused with 'never ran'"
key-files:
  created:
    - src/test/kotlin/com/six2dez/burp/aiagent/scanner/EvidenceTailReachTest.kt
  modified: []
decisions:
  - "AR-28-01 severity DERIVED as MEDIUM from measurements A, B and C — proposed, not yet written to the register"
  - "PRIV-05 stays OPEN (D-28-04) — REQUIREMENTS.md byte-unchanged"
metrics:
  duration: ~35 min
  completed: 2026-08-27
actuals:
  tokens: 9500
  tasks: 1
  commits: 2
---

# Phase 28 Plan 03: Evidence Tail Measurement and Register Amendment Summary

`ResponseAnalyzer`'s matched-substring evidence tail measured in three dimensions — cap set,
reach, emission paths — with a MEDIUM severity derived for residual `AR-28-01`; the one-way
register write is held at the task 2 gate.

## STATUS: PAUSED AT CHECKPOINT — THIS PLAN IS NOT COMPLETE

Task 1 is done and committed. **Task 2 is a `checkpoint:decision` with `gate="blocking"` and has
NOT been decided. Task 3 has NOT been started.** No byte of `26-SECURITY.md` has been written, and
`.planning/REQUIREMENTS.md` is byte-unchanged.

`status: checkpoint` in the frontmatter is deliberate and is NOT to be flipped to `complete` by
anything other than a continuation agent that actually executes task 3. A summary that reads as
complete while its register write is unperformed is the exact "record wider than its control"
defect this plan exists to stop.

| Task | Name | Status | Commit |
| --- | --- | --- | --- |
| 1 | SC6 — measure the evidence tail | DONE | `4eaf480` |
| 2 | Gate the one-way register mutation and the PRIV-05 judgement | **AWAITING MAINTAINER** | — |
| 3 | SC5 — amend AR-27-08, append AR-28-01, recompute threats_open, gate PRIV-05 | NOT STARTED | — |

## Task 1 — SC6, measured

`src/test/kotlin/com/six2dez/burp/aiagent/scanner/EvidenceTailReachTest.kt`, two tests, both green.

### Measurement A — the cap set, derived

Derived by walking `src/main/kotlin/com/six2dez/burp/aiagent/scanner/ResponseAnalyzer.kt` with
comment lines stripped and extracting every `match.value.take(N)`. Raw measured values:

| Site | Enclosing function | Cap |
| --- | --- | --- |
| `ResponseAnalyzer.kt:682` | `analyzeErrorBased` (`:658`) | `take(80)` |
| `ResponseAnalyzer.kt:720` | `analyzeReflection` (`:689`) | `take(60)` |
| `ResponseAnalyzer.kt:791` | `analyzeContentBased` (`:762`) | `take(80)` |

- **Measured construction-site count: 3.**
- **Measured cap multiset, sorted: `[60, 80, 80]`.**

The derivation AGREES with the plan's pin (`EXPECTED_SITE_COUNT = 3`, `EXPECTED_CAPS_SORTED =
[60, 80, 80]`). Neither side moved. The pin is a drift tripwire only; the walk is the source of
truth, and a future fourth site or changed cap turns the test red rather than silently
invalidating the severity recorded below.

## Bound correction

**MANDATORY, not conditional — the prior records do not describe the control.**

- **What the roadmap states:** the evidence tail is "capped at 80 characters" — a SINGLE value.
- **What is derived from source:** the multiset `{80, 80, 60}` over THREE sites —
  `ResponseAnalyzer.kt:682` `take(80)`, `:720` `take(60)`, `:791` `take(80)`.
- **Which sites carry which cap:** `:682` (error-based) 80; `:720` (reflection) 60; `:791`
  (content-based) 80. The reflection path is the odd one out at 60.

**A SECOND record carries the same wrong bound**, and this was not in the plan's statement of the
defect. `CookieCarrierInventoryTest.kt:539` — the `RESPONSE_ANALYZER`/`HEADER_LIST` disposition
text — says "a MATCHED substring of such a signature, **capped at 80 characters**, can be written
into VulnConfirmation.evidence". Same singular 80, same mismatch. That same sentence also cites
`ActiveAiScanner.kt:1246` as the issue-detail site; **the live line is `ActiveAiScanner.kt:1242`**
(measured 2026-08-27), a four-line drift. Neither string was touched by this task — the plan's
`files_modified` does not include that file, so correcting it is out of scope here and is recorded
as a deferred item rather than fixed silently.

**The correction that matters most is not the number.** Measurement C below shows the cap does not
bound what leaves the machine at all: `ActiveAiScanner.kt:1206` re-truncates at `take(100)`, which
is larger than every construction cap, so the ENTIRE evidence string survives onto the outbound
path. Quoting "capped at 80" as reassurance is therefore doubly wrong — the number is wrong, and
the attenuation it implies does not exist.

### Measurement B — reach, both directions, verbatim

Reach mechanism, cited at source: `ResponseAnalyzer.kt:619` builds the matched-against text as
`modifiedHeaders + "\n\n" + modifiedBody`, where `modifiedHeaders` is every response header joined
as `name: value` (`:632-638`). `Set-Cookie` values are therefore INSIDE the text every vuln-class
signature is matched against.

**NEGATIVE direction.** Cookie `sid=aK9xQ2mZ7pLf4vN8tR1bY6wE3hJ0uC5d`, body carrying a genuine SQL
error. A confirmation WAS produced (the non-vacuity control: the analyzer demonstrably ran), and
its evidence measured verbatim as:

```
MySQL syntax error: 'You have an error in your SQL syntax'
```

The cookie value does not appear. "Unreachable" is thereby distinguished from "my fixture never
drove the analyzer" — the failure mode a one-directional test cannot exclude.

**POSITIVE direction.** Same machinery, benign body, cookie value shaped to match the SQLI
`ErrorPattern` `Regex("Warning.*mysql_.*query", IGNORE_CASE)` at `ResponseAnalyzer.kt:28`. Evidence
measured verbatim as:

```
PHP MySQL warning: 'Warning_mysql_fetch_query'
```

**The cookie value is carried through in full.** The vuln-class signature used by the positive
fixture is the SQLI **"PHP MySQL warning"** pattern, `Regex("Warning.*mysql_.*query")`
(`ResponseAnalyzer.kt:28`), confidence 90. `.` does not cross the newline separating header lines,
so the greedy match is confined to the `Set-Cookie` line and spans exactly the cookie value.

Both strings are pinned with `assertEquals` in the test, so this record and the tree cannot drift
apart silently.

### Measurement C — emission paths

Every consumer of `VulnConfirmation.evidence` in `src/main/kotlin`, each classified only after
reading the consumer. All six reads are in `ActiveAiScanner.kt`.

| # | Path (read of `confirmation.evidence`) | Consumer read at file:line that decides it | Classification | Modes under which it emits |
| --- | --- | --- | --- | --- |
| 1 | `:1198` DB tech-hint scan | `:1200-1204` — `dbHints.add("MySQL")` etc. add FIXED LITERALS; only those reach `recordTechStack`. The evidence string itself is discarded. | LOCAL-ONLY, non-carrying by construction | none |
| 2 | `:1206` `recordErrorPattern(host, evidence.take(100))` | `ScanKnowledgeBase.kt:111 getErrorPatterns` → `AdaptivePayloadEngine.kt:38` → **`:52` `safeErrorPatterns = if (privacyMode == STRICT) emptySet() else errorPatterns`** → `:55 buildPayloadPrompt` → sent to the configured AI backend | **LEAVES THE MACHINE** | **BALANCED and OFF.** Suppressed in STRICT. `take(100)` exceeds every construction cap, so the whole evidence string survives. |
| 3 | `:1242` issue-detail line | `ScannerIssueSupport.kt:123` — `detailLines.add("  Evidence: $evidence")`, **no redaction argument**, immediately below `:120` `Original Value: ${sanitizeInjectionPointValue(point, policy)}` which IS sanitized → `AuditIssue.detail()` → `Serialization.kt:13 detail = detail()` → `McpToolExecutorImpl.kt:615 scanner_issues` | **LEAVES THE MACHINE** | **all three modes.** MCP output passes `McpToolContext.kt:67 Redaction.apply(...)`, but the cookie rules are header-line-keyed and an `Evidence:` line is not a header line. |
| 4 | `:1269` `markResponseEvidence` | `IssueMarkerSupport.kt:52-65` — computes `respStr.indexOf(prefix)` and returns `withResponseMarkers(Marker.marker(idx, endIdx))`. OFFSETS only; no evidence bytes are copied. | LOCAL-ONLY, adds no carriage | none beyond the request/response already attached to the issue |
| 5 | `:1302` `recordVulnSignal(evidence = evidence.take(200))` | `ScanKnowledgeBase.kt:81 getVulnSignals` is read at exactly one place, `:94 hasHighPrioritySignals`, whose predicate touches only `.severity` and `.confidence`. `getSignalsByHost` (`:86`) has NO caller in `src/main/kotlin`. `VulnSignal.evidence` is written and never read. | LOCAL-ONLY (dead-stored) | none |
| 6 | `:1312` `ActiveAiFinding(detail = evidence)` | `ActiveAiScanner.kt:175 getRecentConfirmations`, called from exactly two places, both Swing dialogs: `SettingsPanelScannerTabs.kt:223` and `:271`. No MCP tool reads the buffer. | LOCAL-ONLY, UI only | none |

**The candidate that resolved differently from the plan's starting list.** The plan listed "the
audit event field" as a candidate emission path. It is **NOT A CARRIER**: the
`active_scan_confirmed` event at `ActiveAiScanner.kt:1209-1220` carries `vuln_class`, `url`,
`payload.take(100)` and `confidence`. There is no `evidence` key in that map. The candidate came
from a grep and the read retires it.

**Two paths leave the machine, and one of them does so in the DEFAULT posture.** BALANCED is the
default privacy mode, and path 2 is unsuppressed there.

### Proposed `AR-28-01` row (NOT YET WRITTEN — task 2 gates it)

- **ID:** `AR-28-01`
- **Linked threat:** `T-26-02-01`
- **Status:** OPEN
- **Severity: MEDIUM — DERIVED from measurements A, B and C, not carried in from anywhere.**
- **Owner:** the maintainer, at phase 28 human UAT — the venue that dispositioned `AR-27-04`,
  `AR-27-07`, `AR-27-10` and `AR-27-11`.
- **Filed:** 2026-08-27, phase 28 plan 28-03.

**Severity reasoning, aggravating and mitigating in one breath, reachability cited at source.**

*Aggravating.* Reach is PROVEN, not inferred — a cookie value lands verbatim in
`VulnConfirmation.evidence` under a measured positive fixture (`EvidenceTailReachTest`,
`ResponseAnalyzer.kt:619` puts `Set-Cookie` values inside the matched-against text). TWO emission
paths leave the machine, and one — `ActiveAiScanner.kt:1206` → `AdaptivePayloadEngine.kt:52` — is
unsuppressed in **BALANCED, the default posture**, which is squarely inside PRIV-05's wording. Its
`take(100)` exceeds every construction cap (80/60/80), so the truncation the prior records cite as
reassurance attenuates nothing on that path. The second path,
`ScannerIssueSupport.kt:123`, builds the `Evidence:` line with NO redaction argument directly
beneath a line that IS sanitized (`:120`), so the asymmetry is invisible to anyone reading the
rendered blob. And the tail carries no type, no name and no shape a gate could key on, which is
why it is filed rather than fixed (D-28-03).

*Mitigating.* The precondition is narrow and MEASURED rather than assumed: the cookie's OWN BYTES
must match a per-vuln-class error or success signature, and those signatures are DB/framework error
strings — `"You have an error in your SQL syntax"`, `"ORA-0[0-9]{4}:"`,
`Regex("Warning.*mysql_.*query")` (`ResponseAnalyzer.kt:21-46`). The negative direction confirms an
ordinary session token does not reach evidence at all while the analyzer still fires. STRICT closes
the prompt path outright (`AdaptivePayloadEngine.kt:52` → `emptySet()`). The shape-keyed rules
already in `Redaction` — `jwtRegex` (`Redaction.kt:1014`) and `bearerRegex` (`:114`) — are not
header-line-keyed and would catch a JWT- or bearer-shaped cookie value anywhere in MCP output,
including inside an `Evidence:` line, so the uncovered residue on path 3 is opaque-token-shaped
values only. Paths 1, 4, 5 and 6 are local-only on reads verified above, and the audit event is not
a carrier at all.

*Why MEDIUM and not either neighbour.* Not HIGH: the trigger requires the cookie value to
self-match a vuln signature, which is pathological rather than typical, and the STRICT posture that
PRIV-05 names first closes the default-posture path entirely. Not LOW: reach is proven rather than
inferred, one path emits in the DEFAULT posture, and the `ScannerIssueSupport.kt:123` asymmetry
means the gap is undetectable by inspection of the rendered output. MEDIUM places it in the same
band as `AR-27-04` and `AR-27-11` — both reachable, both measured, both maintainer-accepted — which
is the comparison a future round will make.

*Counter position.* `threats_open` scans rows matching `^| T-26-` and nothing else. `AR-` rows sit
OUTSIDE that counter's population **at any severity**, so `AR-28-01` at MEDIUM neither moves the
counter nor is hidden by it — it is recorded in the Accepted Risks Log, which is a different
population from the blocking gate. This is stated rather than implied.

**Scope clause the row must carry verbatim** (fixed, greppable form, per D-28-03):

> `SCOPE: ONE LINE, NOT THE BLOB` — plan 28-01 controls the `Original Value` line of the
> issue-detail blob (`ScannerIssueSupport.kt:120`, via `sanitizeInjectionPointValue`), NOT the
> blob. The `Evidence` line in the SAME blob, built at `ActiveAiScanner.kt:1242` and rendered at
> `ScannerIssueSupport.kt:123`, is NOT controlled and is carried as `AR-28-01`.

### `AR-27-08` amendment — baseline character counts, measured

Measured 2026-08-27 at `26-SECURITY.md:304`, the single `AR-27-08` row:

| Field | Measured |
| --- | --- |
| Whole row line length | **3544 characters** |
| Field 4 (the mechanism/description cell to be amended) | **3399 characters** |
| Field 3 (linked threat) | `T-26-02-01` |
| Field 5 (provenance) | 105 characters |
| Field 6 (date) | `2026-08-25` |

The plan cites 3545 for the cell; the measurement above gives **3544 for the whole row line** and
**3399 for field 4**. Recorded raw rather than reconciled — task 3 asserts the byte-exact-prefix
property against the field-4 figure it re-derives at write time, and reports both the before and
after counts.

The amendment is APPEND-AND-AMEND under a supersession marker: the existing 3399 characters of
field 4 must survive as a **byte-exact prefix**, verified programmatically, with the new scope
clause appended after it. Nothing is deleted.

### Pre-amendment vacuity counts, derived from `git show HEAD:`

Measured on the `AR-27-08` row as it stands at `HEAD` (`4eaf480`'s parent state for this file —
the file is untouched by this plan so far):

| Grep | Pre-amendment count on `AR-27-08` | Verdict |
| --- | --- | --- |
| `Original Value` | **1** | **VACUOUS** — already present; a post-amendment pass proves nothing |
| `SCOPE: ONE LINE, NOT THE BLOB` | **0** | **LOAD-BEARING** |
| `ActiveAiScanner.kt:1242` | **0** | **LOAD-BEARING** |
| `^\| AR-28-01 ` rows in file | **0** | new row; all three greps non-vacuous on it |

So of the three scope greps on `AR-27-08`, exactly TWO will have measured a change. This matches
the plan's prediction. Task 3 must also perform the coherence read — three greps passing over three
unrelated sentences is not the clause.

## PRIV-05 gate — RUN 1, in-task (plan 28-03 task 3)

**NOT YET RUN.** Task 3 has not started; this heading is a placeholder so its absence is visible
rather than silent.

## PRIV-05 gate — RUN 2, post-completion, pre-commit

**OUTSTANDING — the phase commit is BLOCKED until RUN 2 is recorded here.**

`gsd-tools query phase.complete 28` has not run at the time this summary was written. RUN 2 must
re-derive `.planning/REQUIREMENTS.md`'s sha256 (expected prefix `9b3219662ec0d007…`) and re-assert
that PRIV-05 on line 23 is still `- [ ]`, AFTER `phase.complete 28` and IMMEDIATELY BEFORE the
phase commit, with revert-and-record on any movement. On 2026-08-27 `phase.complete 27` flipped
PRIV-05 to `[x]` as a side effect while its own warnings said it was skipping that write; it was
caught pre-commit and reverted (WINDOWS entry 54). RUN 1 runs strictly before that tool and cannot
observe the failure.

**Current state, verified at the time of writing:** `.planning/REQUIREMENTS.md` is byte-unchanged
(not in `git status`), PRIV-05 is `- [ ]`.

## Deviations from Plan

**1. [Rule 3 - Blocking] `ActiveScanTarget` eagerly reads `request().url()`**

- **Found during:** Task 1, first run of `aCookieValueReachesEvidenceOnlyByMatchingAVulnSignature`.
- **Issue:** `NullPointerException` — `createConfirmation` (`ResponseAnalyzer.kt:1034`) wraps the
  result in an `ActiveScanTarget`, whose `id` initializer (`ActiveScanModels.kt:300`) reads
  `originalRequest.request().url()`. The plan's read-first notes stated `createConfirmation` calls
  no Montoya method; that is true of `createConfirmation` itself but not of the data class it
  constructs.
- **Fix:** stubbed `request().url()` in the fixture with a URL deliberately free of the SQLI
  false-positive indicators. Explicit three-mock fixture retained rather than deep stubs, so an
  unstubbed call throws instead of returning a null the assertion would mistake for a clean result.
- **Files modified:** `EvidenceTailReachTest.kt` (new file, pre-commit).
- **Commit:** `4eaf480`.

**2. [scope, deferred not fixed] A second record carries the wrong bound**

`CookieCarrierInventoryTest.kt:539` states the same singular "capped at 80 characters" and cites
`ActiveAiScanner.kt:1246` where the live line is `:1242`. Out of scope — that file is not in this
plan's `files_modified`, and amending a phase-27 disposition text mid-phase-28 without a gate is
the class of silent record edit this phase is guarding against. Logged as a deferred item.

## Deferred Items

- `CookieCarrierInventoryTest.kt:539` — RESPONSE_ANALYZER/HEADER_LIST disposition states a single
  cap of 80 (derived set is `{80, 80, 60}`) and cites `ActiveAiScanner.kt:1246` (live line
  `:1242`). Needs a gated amendment in a future phase.

## Verification

| Check | Result |
| --- | --- |
| `./gradlew test --tests '…EvidenceTailReachTest'` | **PASS** — 2 tests, 0 failures |
| `./gradlew ktlintCheck detekt` | **PASS** |
| `detekt-baseline.xml` byte-unchanged | **PASS** — `git diff --stat` empty |
| `26-SECURITY.md` byte-unchanged by task 1 | **PASS** — `git diff --stat` empty |
| `.planning/REQUIREMENTS.md` byte-unchanged | **PASS** — absent from `git status` |
| `STATE.md` / `ROADMAP.md` untouched | **PASS** — orchestrator owns those |
| Full suite | NOT RUN — task 3 outstanding; a continuation agent runs it |

`./gradlew check` was deliberately NOT used as a gate: it is RED for a maintainer-accepted reason
(redact BRANCH 0.92784 against a 0.930 floor, decided by a wall-clock guard). Coverage direction
for this task is NONE — zero production source modified, the only Kotlin added is a test, and
`com.six2dez.burp.aiagent.redact` gains no branches.

## Known Stubs

None. No stub, placeholder or unwired component was introduced. The two placeholder headings above
(`PRIV-05 gate — RUN 1`, `RUN 2`) are explicit OUTSTANDING markers for work this plan has not
reached, not stubs in shipped code.

## Self-Check

Performed after writing this summary; result appended below.

## Self-Check: PASSED

| Claim | Verification | Result |
| --- | --- | --- |
| `EvidenceTailReachTest.kt` exists | `ls` | FOUND (13226 bytes) |
| `28-03-SUMMARY.md` exists | `ls` | FOUND |
| Commit `4eaf480` exists | `git log --oneline` | FOUND |
| `.planning/REQUIREMENTS.md` sha256 | `shasum -a 256` | `9b3219662ec0d007c1c82d64eed3ef2698bd306ce69f01205ac9bbc3f42fcfb4` — matches the expected prefix `9b3219662ec0d007…`, byte-unchanged |
| PRIV-05 still unchecked | `sed -n '23p' .planning/REQUIREMENTS.md` | `- [ ] **PRIV-05** …` — still OPEN |
| `26-SECURITY.md` untouched | `git diff --stat` | empty |
| `STATE.md` / `ROADMAP.md` untouched | `git status --short` | absent |

No claim in this summary was found unsupported. The two OUTSTANDING markers (PRIV-05 gate RUN 1
and RUN 2) are declared outstanding rather than claimed done, and task 2 and task 3 are declared
not-executed.
