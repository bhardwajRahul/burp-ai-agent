# Phase 26 — Coverage Seal

The phase's SC2 target was derived from a measurement taken at plan time. This file records both
endpoints with the command and the commit that produced each, so the SC2 numbers have a provenance
instead of being asserted.

---

## Endpoints

| | Pre-phase | Post-phase |
|---|---|---|
| **Commit** | `4f0ebd7` | `a3f4b74` |
| **Date** | 2026-08-22 (plan time) | 2026-08-22 (this plan, merged tree) |
| **Command** | `JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test jacocoTestReport --continue` | `JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew build jacocoTestReport` |
| **Source** | `build/reports/jacoco/test/jacocoTestReport.xml` | `build/reports/jacoco/test/jacocoTestReport.xml` |
| **Suite** | 880 tests / 127 classes / 0 failures / 1 skip | 1131 tests / 158 classes / 0 failures / 1 skip |

The post-phase figure is measured on the MERGED tree — all seven plans of Phase 26 (26-01 through
26-07) are present. Per-plan figures recorded in an isolated worktree can differ by a few tenths of a
point after merge, because sibling plans change the denominator as well as the numerator. Where that
happened it is called out below.

---

## Per-package table, both endpoints

Line and branch are `covered/total`. Percentages are `covered / (covered + missed)`.

| scope | line pre | line pre % | line post | line post % | branch pre | branch pre % | branch post | branch post % |
|---|---|---|---|---|---|---|---|---|
| `redact` | 422/439 | 96.13% | **430/439** | **97.95%** | 174/194 | 89.69% | **181/194** | **93.30%** |
| `config` | 1014/1061 | 95.57% | **1017/1048** | **97.04%** | 541/608 | 88.98% | **561/610** | **91.97%** |
| `mcp` (leaf) | 1326/1436 | 92.34% | **1331/1441** | **92.37%** | 374/477 | 78.41% | **374/477** | **78.41%** |
| `mcp/tools` | 956/2221 | 43.04% | **1254/2218** | **56.54%** | 370/1089 | 33.98% | **453/1093** | **41.45%** |
| `mcp/schema` | 69/142 | 48.59% | **139/142** | **97.89%** | 35/68 | 51.47% | **52/68** | **76.47%** |
| `mcp/external` | 137/225 | 60.89% | **137/225** | **60.89%** | 13/66 | 19.70% | **13/66** | **19.70%** |
| `mcp` tree (4 packages) | 2488/4024 | 61.83% | **2861/4026** | **71.06%** | 792/1700 | 46.59% | **892/1704** | **52.35%** |
| `backends/cli` | 215/699 | 30.76% | **268/700** | **38.29%** | 75/488 | 15.37% | **127/484** | **26.24%** |
| `util` | 228/298 | 76.51% | **234/298** | **78.52%** | 146/228 | 64.04% | **149/228** | **65.35%** |
| **project** | **14138/25096** | **56.34%** | **14567/25053** | **58.14%** | **3705/10860** | **34.11%** | **3881/10856** | **35.75%** |

Two denominators moved. `config` dropped from 1061 to 1048 lines because plan 26-07 replaced fourteen
constant-returning private functions with `const val` declarations. `mcp/tools` dropped from 2221 to
2218 because 26-07 converted four guarded `throw` statements into single-expression `require` /
`check` calls. Both are smaller files, not narrowed tests.

---

## Per-floor verdict, re-verified on the merged tree

Every floor claimed by plans 26-01, 26-02 and 26-03 was re-measured at `a3f4b74`. A floor met in an
isolated worktree and missed after merge would be a floor that was not met, and would be recorded as
MISSED here regardless of what the originating plan's SUMMARY says.

| # | Plan | Scope | Metric | Floor | Measured at `a3f4b74` | Verdict |
|---|---|---|---|---|---|---|
| 1 | 26-01 | pkg `backends/cli` | line | ≥ 36.0% | 268/700 = 38.29% | **MET** |
| 2 | 26-01 | `CliBackend.kt` | line | ≥ 26.0% | 176/607 = 29.00% | **MET** |
| 3 | 26-02 | `mcp` tree (4 pkgs) | line | ≥ 65.0% | 2861/4026 = 71.06% | **MET** |
| 4 | 26-02 | pkg `mcp/tools` | line | ≥ 49.0% | 1254/2218 = 56.54% | **MET** |
| 5 | 26-02 | pkg `mcp/schema` | line | ≥ 70.0% | 139/142 = 97.89% | **MET** |
| 6 | 26-02 | `McpToolHelpers.kt` | line | ≥ 58.0% | 132/222 = 59.46% | **MET** |
| 7 | 26-02 | `McpToolModels.kt` | line | ≥ 50.0% | 271/315 = 86.03% | **MET** |
| 8 | 26-02 | `Serialization.kt` | line | ≥ 70.0% | 108/108 = 100.00% | **MET** |
| 9 | 26-03 | pkg `config` | line | ≥ 96.2% | 1017/1048 = 97.04% | **MET** |
| 10 | 26-03 | pkg `config` | branch | ≥ 91.0% | 561/610 = 91.97% | **MET** |
| 11 | 26-03 | pkg `redact` | line | ≥ 97.5% | 430/439 = 97.95% | **MET** |
| 12 | 26-03 | pkg `redact` | branch | ≥ 93.0% | 181/194 = 93.30% | **MET** |
| 13 | 26-07 (SC2) | project | line | ≥ 57.0% | 14567/25053 = 58.14% | **MET** |
| 14 | 26-07 (SC2) | project | branch | ≥ 34.8% | 3881/10856 = 35.75% | **MET** |

**14 of 14 MET. No floor was missed after merge, and no floor was adjusted to match a result.**

Three post-merge deltas are small enough to be worth naming so the margins are not read as larger
than they are:

- **`McpToolHelpers.kt` fell from 59.82% (26-02, isolated) to 59.46%.** 26-07 converted
  `resolveReportPath`'s two guards to `require`, removing two `if` lines from the denominator
  (224 → 222) along with two covered lines. Margin over the 58.0% floor is 1.46 points, down from
  1.82 — still clear.
- **`mcp/tools` fell from 56.55% to 56.54%** and **`mcp` tree from 71.02% to 71.06%** — the same
  conversion, in the other direction for the tree. Both well inside their margins.
- **`CliBackend.kt` rose from 28.95% (26-01, isolated) to 29.00%**, because 26-07 replaced a
  three-line guarded `throw` with a one-line `check`, shrinking the uncovered denominator.

---

## Where the SC2 target numbers came from

SC2's ≥ 57.0% line / ≥ 34.8% branch targets were set against the **post-Phase-25 measurement at
`4f0ebd7`** — 56.34% line (14138/25096) and 34.11% branch (3705/10860) — and not against the
34% line / 23% branch figure recorded in `STATE.md` as the 2026-08-05 milestone baseline.

That choice is deliberate. The milestone baseline is nine phases and roughly three weeks stale;
Phases 20 through 25 added substantial test coverage of their own, so measuring this phase against
34% would have credited Phase 26 with every point those phases earned. A target set one to two points
above the immediately-preceding tree is a target this phase has to actually move, which is the point
of having one. The milestone figure remains the right number for reporting progress across the whole
`v0.10.0` cycle; it is the wrong number for gating a single phase.

Both endpoints are recorded above, so the derivation can be re-checked rather than taken on trust.

---

## Phase criteria status

SC2 and SC3 are sealed by this file and by `26-07-SUMMARY.md`. One clause of another criterion is
carried forward as **OPEN** rather than met:

| Criterion | Status | Note |
|---|---|---|
| SC2 (coverage) | **MET** | This file; 14/14 floors re-verified on the merged tree. |
| SC3 (detekt baseline) | **MET** | 1096 → 1040 entries, removals only; see `26-07-SUMMARY.md`. |
| SC6 — GitBook clause | **OPEN** | Plan 26-05 prepared a diff at `26-GITBOOK-HANDOFF.md` targeting a **separate repository** (`~/Tools/burp-ai-agent-doc`). No human has confirmed applying it. The clause is NOT satisfied by the existence of the handoff document and must not be recorded as met until the change lands in that repo and is verified there. |

---

*Phase: 26-coverage-static-analysis-debt-docs*
*Sealed at commit `a3f4b74` on 2026-08-22 by plan 26-07*
