---
phase: 28-the-issue-detail-cookie-carrier-auditissue-detail-scanner-is
plan: 03
subsystem: scanner / privacy
tags: [PRIV-05, SC6, SC5, AR-28-01, AR-27-08, evidence-tail, residual]
status: complete
requires: ["28-01", "28-02"]
provides:
  - "EvidenceTailReachTest — the derived cap set, the two-directional reach measurement, and the drift tripwire feeding AR-28-01's severity"
  - "AR-28-01 — the ResponseAnalyzer evidence-tail residual, MEDIUM and DERIVED, owned by the maintainer at phase 28 human UAT"
  - "AR-27-08 amended append-and-amend under a dated supersession marker, prior cell preserved as a byte-exact prefix"
  - "threats_open recomputed from the documented awk's raw output, with the counter's population stated rather than implied"
affects:
  - ".planning/phases/26-coverage-static-analysis-debt-docs/26-SECURITY.md (AR-27-08 amended, AR-28-01 appended, threats_open recomputed)"
tech-stack:
  added: []
  patterns:
    - "Source-walk derivation with a pinned tripwire: the walk is the source of truth, the pin turns drift red"
    - "Two-directional reach measurement where the NEGATIVE case still asserts the analyzer fired, so 'did not reach' cannot be confused with 'never ran'"
key-files:
  created:
    - src/test/kotlin/com/six2dez/burp/aiagent/scanner/EvidenceTailReachTest.kt
  modified:
    - .planning/phases/26-coverage-static-analysis-debt-docs/26-SECURITY.md
decisions:
  - "AR-28-01 severity DERIVED as MEDIUM from measurements A, B and C — approved as-proposed by the maintainer at a blocking checkpoint 2026-08-27, and recorded in the register as DERIVED rather than set by disposition"
  - "AR-27-08 amended, never rewritten — prior cell survives as a byte-exact prefix (3399 bytes / 3383 characters), proven by sha256 of the first 3399 bytes on both sides"
  - "PRIV-05 stays OPEN (D-28-04) — REQUIREMENTS.md byte-unchanged, sha256 re-derived as a gate rather than the plan merely refraining from editing it"
metrics:
  duration: ~55 min
  completed: 2026-08-27
actuals:
  tokens: 24500
  tasks: 2
  commits: 4
---

# Phase 28 Plan 03: Evidence Tail Measurement and Register Amendment Summary

`ResponseAnalyzer`'s matched-substring evidence tail measured in three dimensions — cap set,
reach, emission paths — with a MEDIUM severity derived for residual `AR-28-01`; the one-way
register write is held at the task 2 gate.

## STATUS: COMPLETE — with ONE named obligation outstanding for the orchestrator

All three tasks are executed and committed. `26-SECURITY.md` carries the amended `AR-27-08`, the
new `AR-28-01` and a recomputed `threats_open`. `.planning/REQUIREMENTS.md` is byte-unchanged and
PRIV-05 is still an unchecked box.

`status` was flipped from `checkpoint` to `complete` by the continuation agent that actually
executed task 3, which is the only thing permitted to flip it. **The one thing still open is
`## PRIV-05 gate — RUN 2, post-completion, pre-commit` below: the phase commit is BLOCKED until the
orchestrator records RUN 2 there, AFTER `gsd-tools query phase.complete 28`.** RUN 1 is green and
recorded, and RUN 1 being green does not discharge RUN 2 — it runs strictly before the tool that
caused the recorded failure.

| Task | Name | Status | Commit |
| --- | --- | --- | --- |
| 1 | SC6 — measure the evidence tail | DONE | `4eaf480` |
| 2 | Gate the one-way register mutation and the PRIV-05 judgement | **DECIDED — `approve-as-proposed`** | — (checkpoint) |
| 3 | SC5 — amend AR-27-08, append AR-28-01, recompute threats_open, gate PRIV-05 | DONE | see Commits |

## Task 2 — the decision, as recorded

**The maintainer chose `approve-as-proposed` at plan 28-03's `checkpoint:decision` (`gate="blocking"`)
on 2026-08-27.** The four options and their trade-offs are in `28-03-PLAN.md:275-325`.

What that commits, stated so a future round cannot re-derive a different reading from it:

- `AR-28-01` is written at **severity MEDIUM, DERIVED** by task 1's measurements. The maintainer
  ACCEPTED the derived number; they did **not** set it. `adjust-severity` was the option that would
  have set it by disposition and it was **not** taken. The register row says so in its own words —
  a maintainer-set severity and a measured one must never read alike, and the distinction is
  recorded in the row rather than only here.
- `AR-27-08` is amended **append-and-amend under a supersession marker**, never rewritten.
- `threats_open` is **recomputed** with the documented awk; it is never hand-edited.
- `.planning/REQUIREMENTS.md` stays **byte-unchanged**; PRIV-05 stays `- [ ]`. `close-priv-05` was
  rejected.
- The evidence tail **ships uncontrolled, as a named residual**, owned by the maintainer at phase 28
  human UAT. `fix-sc6-here` was rejected — D-28-03 stands.

Task 2 is RESOLVED. It is not re-opened, re-asked or re-litigated by this summary.

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

**Where the correction now lives.** Task 3 carried this into the register: the `AR-28-01` row in
`26-SECURITY.md` states `BOUND CORRECTION — the cap set is {80, 80, 60}, not 80` with all three
sites cited, so a reader who meets the residual meets the corrected bound in the same breath rather
than inheriting the roadmap's single 80.

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

### `AR-28-01` row — WRITTEN to `26-SECURITY.md` by task 3, exactly as proposed below

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

## Task 3 — SC5, the register write

One file changed: `.planning/phases/26-coverage-static-analysis-debt-docs/26-SECURITY.md`. No
production source, no test source, no `build.gradle.kts`, no `detekt-baseline.xml`.

### Pre-amendment counts, RE-DERIVED by task 3 rather than inherited

Task 1 recorded these; task 3 re-derived them independently from `git show HEAD:` before touching a
byte, because a count carried forward is a count nobody measured. The two derivations AGREE.

```
$ git show HEAD:.planning/.../26-SECURITY.md > sec_head.md
$ grep '^| AR-27-08 ' sec_head.md > ar2708_head.txt
$ grep -c 'Original Value'                  ar2708_head.txt   -> 1
$ grep -c 'SCOPE: ONE LINE, NOT THE BLOB'   ar2708_head.txt   -> 0
$ grep -c 'ActiveAiScanner\.kt:1242'        ar2708_head.txt   -> 0
$ grep -c '^| AR-28-01 '                    sec_head.md       -> 0
```

`Original Value` = **1 pre-amendment**, so that grep is **VACUOUS on `AR-27-08`** — it was already
present in the row's own mechanism paragraph, and its passing post-amendment proves nothing about
the scope clause. **It is NOT reported here as evidence.** The two LOAD-BEARING gates on that row
are the sentinel and `ActiveAiScanner.kt:1242`, both **0 → 1**. On the new `AR-28-01` row all three
are non-vacuous, because the row did not previously exist.

### The byte-exact-prefix proof — AR-27-08 amended, never rewritten

The amendment appends only. Nothing above the marker is deleted or reworded, because the prior text
is the evidence the control was needed, and a row that deletes its own reason leaves a later reader
unable to distinguish a considered deferral from an oversight.

| Measure | Before | After | Delta |
| --- | --- | --- | --- |
| `AR-27-08` field 4 (the amended cell) | **3399 bytes / 3383 Unicode characters** | **8668 bytes / 8634 Unicode characters** | +5269 bytes / +5251 characters |
| whole `AR-27-08` row line | 3544 bytes | 8813 bytes | +5269 bytes |

**The 3399 figure is BYTES, not characters** — a distinction the plan and task 1 did not draw and
task 3 does. `awk`'s `length()` on macOS counts bytes; the cell contains 16 bytes of multi-byte
punctuation (em dashes, typographic quotes), so the true count is **3399 bytes = 3383 Unicode
characters**. Both are reported rather than one being silently substituted for the other, which is
how a "character count" gate stops being a gate.

The prefix property is proven **twice, both times programmatically, never by eye**:

```
# in-process, at write time
new_cell.startswith(old_cell)                      -> True
new_cell.encode()[:3399] == old_cell.encode()      -> True

# independently, after the write, against `git show HEAD:`
$ head -c 3399 ar2708_head_field4.txt | shasum -a 256
ec5d50d0b25f151d9f6937f31b357e07a4c38d0f5bbe2d53b0c4a056f5167784  -
$ head -c 3399 ar2708_new_field4.txt  | shasum -a 256
ec5d50d0b25f151d9f6937f31b357e07a4c38d0f5bbe2d53b0c4a056f5167784  -
```

Identical digests over the first 3399 bytes. The supersession marker follows the style the file
already uses (`**SUPERSEDED 2026-08-26 by plan 27-17 — …**`), opening with
`**AMENDED 2026-08-27 by plan 28-03 (phase 28) — THE ROUTE IS NOW CONTROLLED. …**`.

What the appended text states, per the plan's enumeration: the route is CONTROLLED as of phase 28;
the control symbol by name (`ScannerIssueSupport.sanitizeInjectionPointValue`, keyed on
`InjectionType.COOKIE` and `policy.stripCookies`, substituting `INJECTION_VALUE_STRIPPED_MARKER` at
the write site); the committed probe `scanner/IssueDetailCookieCarrierTest` (14 tests) that REPLACES
phase 27's deliberately-uncommitted 27-08 probe, with the reason it is committable at all — it
asserts ABSENCE, so green means a working control rather than a green assertion that a secret
survives STRICT; the red probe's NAMED designated assertion
`IssueDetailCookieCarrierTest.cookieOriginalValueIsStrippedUnderStrict`, measured red on three
working-tree mutations with the verbatim messages in `28-01-SUMMARY.md` and no mutation committed;
the resolution of `InjectionPointExtractor.kt:29` by plan 28-02 through the shared
`Redaction.isCookieParameterType`, value-preserving in the safe direction and proven by
`InjectionPointExtractorTest` (12 tests, zero edits) plus
`CookieRouteDispositionTest.exactlyOneCookieTypePredicateExistsInMainSource`, which DERIVES the
predicate count from the tree instead of pinning a new number; and how the two consumers' DIFFERING
dispositions were preserved — the extractor still returns the RAW value and the control stays at
each CONSUMER (D-28-02), because redacting in the producer would double-redact the already-controlled
`AdaptivePayloadEngine` consumer with a foreign marker vocabulary, with
`CookieCarrierInventoryTest`'s `INJECTION_EXTRACTOR`/`PARAMETER_LIST` entry moved from
`CLASSIFIED_NON_CARRYING` to `ROUTED_THROUGH`, key byte-identical.

### The scope clause — in BOTH rows, checked IN THE FILE, then READ

All six greps run against `26-SECURITY.md` itself, not against this summary:

```
$ F=.planning/phases/26-coverage-static-analysis-debt-docs/26-SECURITY.md
$ grep -c '^| AR-28-01 ' $F                                                  -> 1
$ grep '^| AR-27-08 ' $F | grep -c 'SCOPE: ONE LINE, NOT THE BLOB'           -> 1   (0 before — LOAD-BEARING)
$ grep '^| AR-27-08 ' $F | grep -c 'ActiveAiScanner\.kt:1242'                -> 1   (0 before — LOAD-BEARING)
$ grep '^| AR-27-08 ' $F | grep -c 'Original Value'                          -> 1   (1 before — VACUOUS, not evidence)
$ grep '^| AR-28-01 ' $F | grep -c 'SCOPE: ONE LINE, NOT THE BLOB'           -> 1   (new row — non-vacuous)
$ grep '^| AR-28-01 ' $F | grep -c 'ActiveAiScanner\.kt:1242'                -> 1   (new row — non-vacuous)
$ grep '^| AR-28-01 ' $F | grep -c 'Original Value'                          -> 1   (new row — non-vacuous)
```

Five of the six measured a real change. **Two of three greps on `AR-27-08` measured a change**, as
task 1 predicted, and the third is reported as vacuous rather than as a pass.

**THE COHERENCE READ, because three greps passing over three unrelated sentences is not the clause.**
Both cells were read back out of the file and the three strings sit inside ONE contiguous clause in
each, not scattered. `AR-27-08`, verbatim from the file:

> **SCOPE: ONE LINE, NOT THE BLOB.** Plan 28-01 controls the `Original Value` LINE of the
> issue-detail blob — written at `ActiveAiScanner.kt:1239` and rendered at
> `ScannerIssueSupport.kt:120` through `sanitizeInjectionPointValue` — and it does NOT control the
> blob. The `Evidence` line in the SAME blob, built at `ActiveAiScanner.kt:1242` and rendered
> directly beneath the sanitized line at `ScannerIssueSupport.kt:123` with NO redaction argument, is
> NOT controlled: it is a matched substring of a per-vuln-class signature, it carries no type, no
> name and no shape a type-keyed gate could key on, and it is carried forward as **`AR-28-01`**
> (severity MEDIUM, DERIVED by plan 28-03 task 1). Reading this row as "the issue-detail carrier is
> closed" is therefore WRONG — one line of that blob is closed and another line of the same blob is
> open — and that precise misreading is the defect behind four wrong PRIV-05 closures.

`AR-28-01`, verbatim from the file:

> **SCOPE: ONE LINE, NOT THE BLOB.** Plan 28-01 controls the `Original Value` LINE of the
> issue-detail blob — written at `ActiveAiScanner.kt:1239` and rendered at
> `ScannerIssueSupport.kt:120` through `sanitizeInjectionPointValue` — and it does NOT control the
> blob. The `Evidence` line in the SAME blob, built at `ActiveAiScanner.kt:1242`, is NOT controlled,
> and that uncontrolled line is the entire subject of THIS row. A reader arriving at this row first
> must meet that fact here, because the row whose subject IS the uncontrolled line is the last place
> the scope clause should be missing.

Read as prose, each clause says one thing end to end: the sentinel opens it, `Original Value` names
the line plan 28-01 DOES control with both its write site and its render site, and
`ActiveAiScanner.kt:1242` names the `Evidence` line in the SAME blob that it does NOT control and
which `AR-28-01` carries. This is T-28-12, the phase's only `high` threat, and it is gated in the
FILE in both rows — not in this summary.

### `threats_open` — RECOMPUTED, raw output, never hand-edited

The command is the one documented verbatim in `26-SECURITY.md`'s own frontmatter comment
(lines 8-10). No new command was written.

```
$ awk -F'|' '/^\| T-26-/ { sev=$5; st=$(NF-1); gsub(/[ *`]/,"",sev); gsub(/[ *`]/,"",st);
      if (st != "closed" && (sev == "high" || sev == "critical")) c++ } END { print c+0 }' \
    .planning/phases/26-coverage-static-analysis-debt-docs/26-SECURITY.md
0
```

**RAW OUTPUT: `0`.** Run AFTER both register edits. Population check, independently derived:
`46 T-26- rows scanned, 46 closed`. The frontmatter value at `26-SECURITY.md:198` was set to that
output and now reads `threats_open: 0`.

**STATED, NOT IMPLIED — and stated in the FILE as well as here:** `AR-` rows sit **OUTSIDE this
counter's population at ANY severity**. The population is Threat Register rows whose id begins
`T-26-` and nothing else. So appending `AR-28-01` at MEDIUM leaves the counter unmoved, and
amending `AR-27-08` leaves it unmoved, and **the unmoved counter is not evidence that nothing was
found**. An eleven-line dated note recording exactly this was added to the frontmatter comment
immediately above `threats_open:`, in the same bracketed style the file already uses for its
2026-08-26 supersession note.

### What was deliberately NOT added to `26-SECURITY.md`

Phase 28's own plan threat models — the `T-28-NN` rows in `28-01-PLAN.md`, `28-02-PLAN.md` and
`28-03-PLAN.md` — are **not** added to this file. `26-SECURITY.md` is phase 26's register; phase 28
gets its own security artifact at verification time. Only the `AR-` rows were in scope here.
Recorded so the next reader does not read the absence as an omission; it is also stated in the
frontmatter note and in the `AR-27-08` amendment.

### `AR-27-08` propagation sweep (standing rule (viii)(a))

`grep -rn 'AR-27-08'` across the repository. **Source tree — 4 occurrences in 4 files, all already
addressed by plans 28-01 and 28-02:**

```
src/main/kotlin/.../scanner/ActiveAiScanner.kt:1234      -> 1
src/main/kotlin/.../scanner/ScannerIssueSupport.kt:38    -> 1
src/test/kotlin/.../scanner/IssueDetailCookieCarrierTest.kt:29 -> 1
src/test/kotlin/.../redact/CookieCarrierInventoryTest.kt:566   -> 1
```

- `ActiveAiScanner.kt:1234` and `ScannerIssueSupport.kt:38` — comments written BY plan 28-01 that
  already describe the control at the write site. Correct as they stand.
- `IssueDetailCookieCarrierTest.kt:29` — the committed probe's own KDoc, naming `AR-27-08` as its
  subject. Correct.
- `CookieCarrierInventoryTest.kt:566` — `ISSUE_DETAIL_CARRIER_DISPOSITION`. **Verified addressed:**
  plan 28-01 appended a dated `SUPERSEDED 2026-08-27 (phase 28, plan 28-01) — THE ROUTE IS NOW
  CONTROLLED` clause to the constant with the 2026-08-25 measurement preserved byte-exact as the
  leading prefix, and its KDoc explains why the measurement is kept rather than deleted. Same
  discipline this task applied to the register row.

**Every source-tree occurrence was addressed by plans 28-01/28-02. A correction commit that touched
only the register would have been incomplete; this one is not, because the source-tree work was
already done and was re-verified here rather than assumed.**

The remaining 30 occurrences are all under `.planning/` — `ROADMAP.md`, `WINDOWS.md`,
`v0.10.0-MILESTONE-AUDIT.md`, `codebase/CONCERNS.md`, `21-VERIFICATION.md`, the phase-27 plan /
summary / review / verification / UAT records, and this phase's own plans and summaries. Those are
**historical records of prior rounds and are correctly unchanged**: rewriting them would destroy the
evidence of what each round believed at the time, which is the exact discipline this phase enforces
on the register.

### Diff shape — additions only

```
$ git diff --numstat .planning/.../26-SECURITY.md
13      1       .planning/phases/26-coverage-static-analysis-debt-docs/26-SECURITY.md
```

Eleven added frontmatter comment lines, one added `AR-28-01` row, and one replaced line — the
`AR-27-08` row, whose replacement is an append with the prior 3399 bytes proven identical. **No
existing sentence is deleted or reworded.** `threats_open: 0` does not appear in the diff because
the recomputed raw output equals the previous value; that is reported honestly rather than the line
being touched to make the recomputation visible.

## PRIV-05 gate — RUN 1, in-task (plan 28-03 task 3)

**GREEN.** Run inside task 3, BEFORE `gsd-tools query phase.complete 28`. Raw outputs:

```
$ shasum -a 256 .planning/REQUIREMENTS.md
9b3219662ec0d007c1c82d64eed3ef2698bd306ce69f01205ac9bbc3f42fcfb4  .planning/REQUIREMENTS.md

$ grep -n 'PRIV-05' .planning/REQUIREMENTS.md | head -3
6:Scope = the 17 findings of the 2026-08-05 deep code review of v0.9.2. Two of them (SEC-04, PRIV-05) are **defects verified by running the shipped code**, not theoretical concerns — they are the reason this milestone exists. Phase numbering continues from the previous milestone (Phase 20+).
10:**Ordering constraint:** SEC-04 and PRIV-05 are live defects in a published release and lead the milestone. SEC-06 (agent trust boundary) and REL-05 (EDT) both rewrite `ChatPanel.maybeExecuteToolCall` and must be sequential, not parallel. QUAL-06 lands last so it can cover the code the earlier phases produce.
23:- [ ] **PRIV-05** (Finding 2, **high**): Cookie values do not reach an AI backend in STRICT or BALANCED mode by any path. …

$ git status --short
 M .planning/phases/26-coverage-static-analysis-debt-docs/26-SECURITY.md
```

Digest begins `9b3219662ec0d007…` as required. **Line 23 is `- [ ]` — still an UNCHECKED box.**
`.planning/REQUIREMENTS.md` is absent from `git status`, so it is byte-unchanged rather than merely
unedited-by-me.

### The argument for leaving PRIV-05 open (D-28-04), enumerated

PRIV-05's own wording is an enumeration obligation: *"Cookie values do not reach an AI backend in
STRICT or BALANCED mode **by any path**"* (`REQUIREMENTS.md:23`). "By any path" cannot be discharged
by closing one path. **Five carriers with recorded dispositions remain OPEN:**

| Carrier | Severity | State |
| --- | --- | --- |
| `AR-27-04` | MEDIUM | MAINTAINER-SIGNED ACCEPTANCE. `Host:` and `SiteMapEntry.url` reach an AI backend un-anonymised under STRICT on the serialized emission shape. Behaviour SHIPS. |
| `AR-27-07` | LOW | KEPT AT LOW. A sensitive-NAMED non-COOKIE parameter survives `request_parse`'s serialized JSON in STRICT. `SENSITIVE_WORDS` deliberately not widened (WR-01's measured 32 false positives). |
| `AR-27-10` | LOW | ACCEPTED. 13 RFC 9110 tchars still uncovered; both halves remain inferred. |
| `AR-27-11` | MEDIUM | ACCEPTED over FOUR measured families. `JSON_STRING_OPEN` is literally colon-then-quote, so any interposed character — a space in pretty-printed JSON, a backslash in an escaped nested body — or no colon at all is not a recognised logical-line start. **This is the one reachable in the DEFAULT posture.** |
| `AR-28-01` | MEDIUM | NEW, this phase. The `ResponseAnalyzer` evidence tail, severity DERIVED. |

**And the enumeration mechanism disclaims its own completeness.** `CookieCarrierInventoryTest`
states in its class KDoc that it is a tripwire over a measured accessor set, NOT a proof of
coverage, and names **four blind axes**:

1. operator-pasted text;
2. `bodyToString()` bodies reaching a backend on a path bypassing `Redaction.apply`;
3. transitive carriers beyond the first hop;
4. future Montoya accessors.

Closing `AR-27-08` closes ONE carrier. Five are open, and the mechanism that would enumerate the
rest says it cannot. **PRIV-05 cannot close.** Reversible: a future phase closes it when the
enumeration comes out clean.

## PRIV-05 gate — RUN 2, post-completion, pre-commit

**OUTSTANDING — the phase commit is BLOCKED until RUN 2 is recorded here.**

`gsd-tools query phase.complete 28` had **not** run when task 3 finished — the phase orchestrator
invokes it AFTER this plan's last task, so 28-03's executor has no reachable trigger at that moment.
This heading is therefore left OUTSTANDING deliberately, and its outstanding-ness is stated in the
closing narration so the orchestrator executes it.

**RUN 1 being green does NOT discharge this.** RUN 1 runs strictly before `phase.complete` and is
structurally incapable of observing the failure D-28-04 names: on 2026-08-27 `phase.complete 27`
flipped PRIV-05 to `[x]` **as a side effect while its own warnings said it was skipping that write**.
It was caught pre-commit and reverted (`.planning/WINDOWS.md` entry 54). Two identical-looking
digests under one heading is a record that cannot tell you which side of `phase.complete` it was
taken on, which is the exact ambiguity that let the phase-27 flip reach a staged tree — hence the
distinct heading.

**What the orchestrator must run, AFTER `gsd-tools query phase.complete 28` and IMMEDIATELY BEFORE
the phase commit:**

```bash
shasum -a 256 .planning/REQUIREMENTS.md
grep -n 'PRIV-05' .planning/REQUIREMENTS.md
```

REQUIRE: the digest still begins `9b3219662ec0d007…`, and PRIV-05 on line 23 is still an unchecked
`- [ ]` box. **If EITHER moved: DO NOT COMMIT.** Revert `.planning/REQUIREMENTS.md` to its committed
state, re-run both commands to confirm the revert, and RECORD the incident — what changed it, what
its own output claimed at the time, and the before/after of the line — as a new `.planning/WINDOWS.md`
entry in the style of entry 54. A silent revert leaves the next round with no evidence the class
recurred, and this class has recurred once already.

Paste BOTH raw outputs under this heading, replacing this block.

**State at the moment task 3 ended:** `.planning/REQUIREMENTS.md` byte-unchanged (absent from
`git status`), digest `9b3219662ec0d007c1c82d64eed3ef2698bd306ce69f01205ac9bbc3f42fcfb4`, PRIV-05
line 23 `- [ ]`.

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

**3. [measurement refinement, task 3] The "3399 characters" figure is 3399 BYTES**

- **Found during:** Task 3, re-deriving the amendment baseline before writing.
- **Issue:** the plan, task 1's record and the standing rule all say "character counts". `awk`'s
  `length()` on macOS counts BYTES. The `AR-27-08` cell contains 16 bytes of multi-byte punctuation,
  so 3399 bytes = 3383 Unicode characters. A gate that reports one number while naming the other
  stops being a gate the moment someone re-derives it with a different tool and gets a "mismatch".
- **Fix:** both numbers are reported everywhere the count appears — in this summary, in the register
  cell, and in the frontmatter. The byte-exact-prefix assertion itself is proven on BYTES (`sha256`
  of the first 3399 bytes, identical on both sides), which is the stronger of the two properties.
- **Files modified:** none beyond the register write itself.

**4. [scope, deferred not fixed] Two prior records disagreed on the cap; one is fixed, one deferred**

`ROADMAP.md`'s stated cap of 80 is corrected inside the new `AR-28-01` row under
`## Bound correction`, because that row is in this plan's scope. `CookieCarrierInventoryTest.kt:539`
carries the same wrong bound and a stale line citation, and is NOT in this plan's `files_modified` —
deferred rather than silently edited. See Deferred Items.

## Deferred Items

- `CookieCarrierInventoryTest.kt:539` — the RESPONSE_ANALYZER/HEADER_LIST disposition states a
  single cap of "80 characters" where the DERIVED set is `{80, 80, 60}`, and cites
  `ActiveAiScanner.kt:1246` where the live line is `:1242`. **Out of scope for plan 28-03** — that
  file is absent from this plan's `files_modified`, and amending a phase-27 disposition text
  mid-phase-28 without a gate is exactly the class of silent record edit this phase guards against.
  It was **logged, not silently corrected**. Needs a gated amendment in a future phase, under the
  same append-and-amend discipline applied to `AR-27-08` here.
  *(Note: this is a different constant from `ISSUE_DETAIL_CARRIER_DISPOSITION` at `:566`, which plan
  28-01 already superseded correctly — see the propagation sweep above.)*

## Verification

| Check | Result |
| --- | --- |
| `./gradlew ktlintCheck detekt test` (task 3, full run) | **PASS** — `BUILD SUCCESSFUL in 3m 34s`, 15 tasks executed |
| Full suite | **PASS** — **179 classes, 1279 tests, 0 failures, 0 errors** |
| `RedactionTest` CPU-load flake | **DID NOT OCCUR** this run — no `REDACTION INCOMPLETE` / `REDACTION BUDGET EXCEEDED` failure |
| `./gradlew test --tests '…EvidenceTailReachTest'` (task 1) | **PASS** — 2 tests, 0 failures |
| `detekt-baseline.xml` byte-unchanged | **PASS** — absent from `git status` after the full run |
| `.planning/REQUIREMENTS.md` byte-unchanged | **PASS** — absent from `git status`; sha256 re-derived, see RUN 1 |
| `STATE.md` / `ROADMAP.md` untouched | **PASS** — absent from `git status`; the orchestrator owns those |
| `build.gradle.kts` untouched | **PASS** — absent from `git status` |
| Working tree scope | **PASS** — exactly two modified files: `26-SECURITY.md` and this summary |
| `grep -c '^\| AR-28-01 ' 26-SECURITY.md` | **PASS** — `1` |
| documented awk raw output vs `threats_open:` | **PASS** — both `0` |
| `shasum -a 256 .planning/REQUIREMENTS.md` prefix | **PASS** — `9b3219662ec0d007…` |
| PRIV-05 still `- [ ]` | **PASS** — line 23 unchecked |
| `## PRIV-05 gate — RUN 2` | **OUTSTANDING BY DESIGN** — orchestrator-owned; the phase commit is blocked on it |

One test is `@Disabled` in `ExternalMcpClientManagerTest` (external MCP client). It is
**pre-existing and unrelated** to this plan — no production source was touched here at all — so it
is reported as observed rather than claimed or owned.

`./gradlew check` was deliberately NOT used as a gate: it is RED for a maintainer-accepted reason
(redact BRANCH 0.92784 against a 0.930 floor, decided by a wall-clock guard), and its colour depends
on machine load rather than on this plan's code. The floor was not adjusted. Coverage direction for
this plan is NONE — zero production source modified, the only Kotlin added is a test, task 3 touches
one Markdown record, and `com.six2dez.burp.aiagent.redact` gains no branches.

## Known Stubs

None. No stub, placeholder or unwired component was introduced — this plan added one test file and
edited one Markdown record.

**`AR-28-01` is a named residual, not a stub.** The `ResponseAnalyzer` evidence tail ships
UNCONTROLLED by deliberate decision (D-28-03, approved `approve-as-proposed` at the blocking
checkpoint), with a derived severity, an id, a named owner (the maintainer) and a named venue
(phase 28 human UAT). A deferral without an owner is round four pre-arranged; this one has both.

**`## PRIV-05 gate — RUN 2, post-completion, pre-commit` is OUTSTANDING BY DESIGN**, not a stub. It
is orchestrator-owned because `phase.complete 28` runs after this plan's last task, and the phase
commit is blocked until it is recorded.

## Commits

| Commit | Task | What |
| --- | --- | --- |
| `4eaf480` | 1 | `test(28-03): measure the evidence tail's reach, cap set and emission paths` — `EvidenceTailReachTest.kt`, 2 tests |
| `8bcd550` | 1 | `docs(28-03): record task 1's measurements and pause at the task 2 gate` — partial summary, `status: checkpoint` |
| `ffb9cce` | 3 | `docs(28-03): amend AR-27-08, append AR-28-01, recompute threats_open` — the register write |
| *(next)* | 3 | `docs(28-03): complete the summary — task 3, the PRIV-05 gate and the propagation sweep` |

Task 2 produced no commit: it is a `checkpoint:decision`, and the decision itself is recorded here
and inside the `AR-28-01` row.

## Self-Check

Performed after writing this summary; result appended below. Every claim re-verified against disk
and git rather than against the summary's own earlier text.

## Self-Check: PASSED

| Claim | Verification | Result |
| --- | --- | --- |
| `EvidenceTailReachTest.kt` exists | `ls` | FOUND (13226 bytes) |
| `28-03-SUMMARY.md` exists | `ls` | FOUND |
| Commit `4eaf480` exists | `git log --oneline` | FOUND |
| `26-SECURITY.md` modified by task 3 | `git diff --numstat` | `13 1` — 13 added, 1 replaced |
| `AR-27-08` prior cell is a byte-exact prefix | `head -c 3399 … \| shasum -a 256`, both sides | `ec5d50d0b25f151d…` on both — IDENTICAL |
| `AR-27-08` field 4 before / after | `awk length` + `wc -m` | 3399 B / 3383 chars → 8668 B / 8634 chars |
| `AR-28-01` row exists exactly once | `grep -c '^\| AR-28-01 '` | `1` |
| `AR-28-01` has the register's 5-column shape | `awk -F'\|' '{print NF}'` | `7` pipe-fields = 5 columns, same as `AR-27-08` |
| Scope clause in `AR-27-08` | 3 greps in the FILE | sentinel `1` (was 0), `:1242` `1` (was 0), `Original Value` `1` (was 1 — VACUOUS) |
| Scope clause in `AR-28-01` | 3 greps in the FILE | `1`, `1`, `1` — all non-vacuous |
| Coherence read | both cells read back from the file | ONE contiguous clause in each — recorded verbatim above |
| `threats_open` recomputed | documented awk, raw | `0`; frontmatter `26-SECURITY.md:198` reads `threats_open: 0` |
| Counter population stated in the FILE | frontmatter note + both rows | present — `AR-` rows outside the `T-26-` population at ANY severity |
| `.planning/REQUIREMENTS.md` sha256 | `shasum -a 256` | `9b3219662ec0d007c1c82d64eed3ef2698bd306ce69f01205ac9bbc3f42fcfb4` — matches prefix `9b3219662ec0d007…` |
| PRIV-05 still unchecked | `grep -n 'PRIV-05'` | line 23 `- [ ] **PRIV-05** …` — still OPEN |
| `.planning/REQUIREMENTS.md` byte-unchanged | `git status --short` | absent |
| `STATE.md` / `ROADMAP.md` untouched | `git status --short` | absent |
| `detekt-baseline.xml` byte-unchanged | `git status --short` | absent |
| `ktlintCheck detekt test` green | `./gradlew` exit 0 | `BUILD SUCCESSFUL in 3m 34s`; 179 classes, 1279 tests, 0 failures |
| `AR-27-08` propagation addressed in src | `grep -rn 'AR-27-08' src/` | 4 occurrences, 4 files, all written or superseded by 28-01/28-02 |

**No claim in this summary was found unsupported, and two claims were weakened rather than
inflated:** the `Original Value` grep on `AR-27-08` is reported as VACUOUS instead of as evidence,
and the "3399 characters" figure is corrected to 3399 BYTES / 3383 characters. The one remaining
OUTSTANDING marker — `PRIV-05 gate — RUN 2` — is declared outstanding rather than claimed done.
