---
phase: 22-agent-tool-call-trust-boundary
plan: 09
subsystem: docs
tags: [sec-06, sc1, adr, threat-model, inheritance, human-uat, claim-only-what-ships, gradle-inputs]

# Dependency graph
requires:
  - plan: "22-02"
    provides: "SecTier, its KDoc copy of D-05's AUTO sentence, and the locked 19/26/14 tier table"
  - plan: "22-03"
    provides: "ToolCallOrigin, the file-private ModelApproved variant, tierFor's ext:/unknown fail-closed resolution"
  - plan: "22-04"
    provides: "ToolApprovalGate.evaluate/resolve, DENIAL_RESULT, the monotone budget helpers"
  - plan: "22-05"
    provides: "ToolDecisionReporter and its exhaustive isDenial"
  - plan: "22-07"
    provides: "The gate in the path and the red-before-green proof ADR-15 cites as demonstrated evidence"
  - plan: "22-08"
    provides: "The pending-decision lifecycle, the SC3 record, and the three defects the honest record names"
provides:
  - "ADR-15 — the written rule by which a future tool inherits the trust boundary instead of re-litigating it"
  - "DecisionsAdrTest — a PR-gate guard on ADR-15's existence, its number, and D-05's sentence in BOTH of its two copies"
  - "tasks.test input declarations for DECISIONS.md and McpToolCatalog.kt, without which the guard is cacheable-away"
  - "22-HUMAN-UAT.md — the four verifications automation cannot reach, including SC1's factual-accuracy review"
  - "CONCERNS.md corrected against what this phase measured, including what was NOT fixed"
affects: ["phase-23", "phase-26"]

tech-stack:
  added: []
  patterns:
    - "Declare the sentence once in the test and assert it against both copies: guarding one copy alone does not catch drift, it relocates it"
    - "Normalise the two markup channels (markdown blockquote, KDoc asterisks) rather than storing two expected strings, so a line wrap in either file is not a false failure"
    - "A test that reads a file off the classpath needs that file declared as a task input, or the build cache answers for it"
    - "Assert the ADR is the HIGHEST-numbered one, so the next phase's author is told to claim a new number rather than discovering the collision later"

key-files:
  created:
    - src/test/kotlin/com/six2dez/burp/aiagent/DecisionsAdrTest.kt
    - .planning/phases/22-agent-tool-call-trust-boundary/22-HUMAN-UAT.md
  modified:
    - DECISIONS.md
    - .planning/codebase/CONCERNS.md
    - build.gradle.kts

key-decisions:
  - "The guarded constant starts at 'means', not at 'AUTO' — DECISIONS.md writes the subject as `AUTO` and the KDoc writes it as [AUTO], so the leading token is the one part the copies legitimately differ on and pinning it would have made the guard unsatisfiable"
  - "DECISIONS.md and McpToolCatalog.kt declared as tasks.test inputs (Rule 2): neither is on the compiled classpath, so a commit editing only one produced an identical cache key and the guard was restored from cache without running"
  - "ADR-15 cites McpToolExecutorImpl.kt:694 explicitly as the line AT THE PRE-GATE COMMIT; api.proxy().history() is at :695 today and an unqualified citation would have read as a stale anchor rather than as a quoted stack trace"
  - "The SecTier KDoc is sliced by walking back from the declaration to its opening delimiter, so the assertion is about the KDoc a contributor reads at the point of writing `secTier = `, not about the sentence appearing anywhere in a 1000-line catalog"
  - "CONCERNS.md records the RedactionTest flake as a TEST-assumption defect, explicitly not a product defect, with 'do not raise DEFAULT_TIMEOUT_MS' spelled out because that constant is a ReDoS bound"

requirements-completed: [SEC-06]

# Metrics
duration: ~55 min
completed: 2026-08-14
---

# Phase 22 Plan 09: ADR-15, the Written Rule Summary

**The trust boundary is now written down in a form a future contributor inherits rather than re-derives — and because an ADR is the one success criterion that is easy to mark done without checking, SC1 ships with both a PR-gate guard on the sentence and a named human reviewer for its truth.**

## Performance

- **Duration:** ~55 min
- **Tasks:** 3 of 3
- **Files created:** 2 · **Files modified:** 3
- **Commits:** 3

## Task Commits

| # | Task | Commit | Type |
|---|------|--------|------|
| 1 | Write ADR-15 in DECISIONS.md | `fbe7ab7` | docs |
| 2 | DecisionsAdrTest — the guard on the inherited sentence | `e11703a` | test |
| 3 | Correct CONCERNS.md and write 22-HUMAN-UAT.md | `9bd20bf` | docs |

## Accomplishments

- **ADR-15 was written against what was BUILT, and every claim was checked against code before it was written.** The plan's own instruction was to write the ADR last so that each sentence is checkable rather than aspirational, and that is what happened: all thirteen `file:line` anchors were re-located by symbol and verified to resolve exactly, not within tolerance. Several plan-supplied citations had drifted and were corrected (see Deviations).
- **The inheritance mechanism is stated concretely rather than exhorted.** ADR-15 does not ask future authors to be careful; it records that omitting `secTier` is a compile error at the catalog site (`No value passed for parameter 'secTier'`), that `AUTO` is enumerated rather than derived, and that `McpToolCatalogTierParityTest` pins the exact 19-tool set so a promotion is a reviewed diff.
- **The guard catches drift in BOTH copies, and that is the whole value.** D-05's sentence lives in `DECISIONS.md` and in the `SecTier` KDoc. A string match against one alone would not have caught divergence — it would have relocated it into the other. `theCatalogAndTheAdrCarryTheSameAutoDefinition` is the test that makes two-copy drift a build failure.
- **The guard was proven non-vacuous in both directions by mutation**, and the first attempt at that proof found a real defect in the guard itself (below).
- **`ADR-16` is asserted absent.** That is not pedantry: it is how the next phase's author is told to claim a new number instead of discovering the collision after writing.
- **CONCERNS.md now records what this phase measured, including what was not fixed.** The "Swing headless testing has high setup cost" premise is retired with the measurement that retired it; the `unsafeOnly` opt-in fragility is explicitly recorded as *still standing* for the capability axis; and the missing mutation/`unsafeOnly` test is explicitly recorded as *still open* rather than being allowed to look closed by an adjacent invariant.

## The mutation proofs (both reverted, tree verified clean)

| Mutation | Expected red | Observed |
|----------|--------------|----------|
| `read-only AND bounded output` → `read-only OR bounded output` in `DECISIONS.md` | `adr15CarriesTheAutoDefinitionVerbatim` | **FAILED**, as required |
| `read-only AND bounded output` → `read-only AND unbounded output` in the `SecTier` KDoc | `theCatalogAndTheAdrCarryTheSameAutoDefinition` | **FAILED**, as required |

After each, `git checkout -- <file>` restored the committed state and `git status --short` was clean before proceeding.

**The first proof attempt is the interesting one, because it did not fail.** Mutating `DECISIONS.md` and re-running `./gradlew test --tests "*DecisionsAdrTest*"` reported **BUILD SUCCESSFUL** — the test task was up-to-date and never ran. Only `cleanTest` forced it red. That is deviation 1 below, and it was a real hole in exactly the case the guard exists for.

## Verification Results

| Check | Expected | Actual |
|-------|----------|--------|
| `./gradlew ktlintCheck detekt test -q` | exit 0 | **0** |
| `git diff --stat -- detekt-baseline.xml` | empty | **empty** (QUAL-07 held) |
| `DecisionsAdrTest` | 4 tests, 0 failures | 4 selected, 0 failures |
| `test -PexcludeHeavyTests=true --tests "*DecisionsAdrTest*"` → `<testcase` count | 4 | **4** — the PR gate runs every one |
| `git diff --diff-filter=D` per commit | empty | **empty** — no deletions in any of the three |
| `git diff --numstat .planning/codebase/CONCERNS.md` | additions only | **14 insertions, 0 deletions** |
| `STATE.md` / `ROADMAP.md` | untouched | **untouched** |

### Acceptance greps

| Check | Expected | Actual |
|-------|----------|--------|
| `^## ADR-15` / `^## ADR-16` | 1 / 0 | 1 / 0 |
| `read-only AND bounded output` | 1 | 1 |
| `neither mutates Burp state nor` | 1 | 1 |
| `capability switch` | ≥1 | 1 |
| `EXTERNAL-TOOL-RESULT` | ≥2 | **2** — see deviation 3 |
| `mitigation, not a control` | ≥1 | 1 |
| `ai_analyze` | ≥1 | 1 |
| `or stage it for one click` | 1 | 1 |
| `Residual:` | ≥6 | **10** (ADR-13's 1 + ADR-14's 3 + ADR-15's 6) |
| `project_options_get` | ≥1 | 1 |
| `class DecisionsAdrTest` / `@Test` / `private const val` | 1 / 4 / ≥1 | 1 / 4 / 3 |
| `result: [pending]` / `why_human:` / `^status: partial` in the UAT | 4 / 4 / 1 | 4 / 4 / 1 |
| `ChatPanelToolGateTest` / `HeadlessException` / `secTier` / `McpToolCatalogTierParityTest` in CONCERNS.md | ≥1 each | 1 / 2 / 2 / 1 |

### Anchor resolution

Every `file:line` written into ADR-15 was re-located by symbol and confirmed **exact**, not within tolerance:

`ChatPanel.kt:1307` `MAX_AUTO_TOOL_ITERATIONS` · `:2343` `ToolApprovalGate.evaluate` · `:1102` `state.approvalMemory =` · `:1600` `sessionStates[id] = ToolSessionState()` · `ToolApprovalGate.kt:145` `private class ModelApproved` · `:319-329` `tierFor` · `ToolDecisionReporter.kt:260` `isDenial` · `McpToolExecutorImpl.kt:113-123` preamble markers · `:128-134` advisory note · `:1045` `executeTool` · `McpToolCatalog.kt:42` `unsafeOnly` default · `:45` `secTier` · `McpToolContext.kt:53-57` `isUnsafeToolAllowed`.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 2 - Missing critical] The guard could be cached away in exactly the case it exists to catch**

- **Found during:** Task 2, first mutation check
- **Issue:** `DecisionsAdrTest` reads `DECISIONS.md` and `McpToolCatalog.kt` **from disk at runtime**, not from the compiled classpath, so Gradle cannot infer them as inputs. A commit that edits only `DECISIONS.md` produces byte-identical compiled output and therefore an identical `tasks.test` cache key; the test task is restored from cache and the guard never runs. The same holds for a KDoc-only edit to `McpToolCatalog.kt`, since comments do not affect bytecode. This is precisely the shape of change the guard exists to catch — someone weakening the inherited sentence — and the guard would have been silent. **Measured, not theorised:** mutating the `AUTO` sentence left `./gradlew test` reporting BUILD SUCCESSFUL until `cleanTest` forced a re-run.
- **Fix:** Declared both files as explicit `tasks.test` inputs with `PathSensitivity.RELATIVE`, with a comment recording the measurement so the declarations are not later "tidied away" as redundant. After the fix, both mutations turn the suite red on a plain `./gradlew test`.
- **Files modified:** `build.gradle.kts` (not in the plan's `files_modified`; recorded here)
- **Committed in:** `e11703a`

**2. [Rule 3 - Blocking] `/**` inside a KDoc opens a nested comment**

- **Found during:** Task 2, first compile
- **Issue:** A KDoc explaining that `secTierKdoc()` walks back to the block's opening `/**` contained that literal. Kotlin block comments **nest**, so the literal opened a comment that was never closed: `Syntax error: Unclosed comment` plus a cascade of unresolved references.
- **Fix:** Reworded to "its opening delimiter". No behaviour change.
- **Committed in:** `e11703a`

**3. [Rule 1 - Bug] ADR-11 does not contain the literal the plan's criterion counts**

- **Found during:** Task 1 acceptance
- **Issue:** The criterion expects `grep -c 'EXTERNAL-TOOL-RESULT' DECISIONS.md` ≥ 2, described as "ADR-11's original plus ADR-15's qualification". Measured, ADR-11 contains **zero** occurrences — it says "an explicit trust-boundary marker string" and never writes the marker out. The plan forbids editing any existing ADR, so the count had to come from ADR-15 alone.
- **Fix:** ADR-15 names the marker twice, both times load-bearing rather than padding: once in `**Context.**` establishing why it is mitigation rather than a control, and once in `**Consequences.**` recording that ADR-11 is **not** superseded — the marker still wraps external output, and what changed is its standing in the argument.
- **Committed in:** `fbe7ab7`

**4. [Rule 1 - Bug] Two plan-supplied line citations had drifted**

- **Found during:** Task 1, verifying anchors before writing them
- **Issue:** The plan cites the advisory note at `McpToolExecutorImpl.kt:127-133` (actually `:128-134`) and `isUnsafeToolAllowed` at `McpToolContext.kt:49-60` (actually `:53-57`). Separately, 22-07's recorded stack trace names `McpToolExecutorImpl.kt:694` as the line the model reached, but `api.proxy().history()` sits at `:695` today — the file shifted by one line during this phase.
- **Fix:** Corrected both anchors, as the plan's own criterion directs. For the stack-trace line, ADR-15 cites it **explicitly as the line at pre-gate commit `5863de8`** rather than silently updating it, because the number is quoted evidence from a proof run, not a pointer into today's file. An unqualified `:694` would have read as a stale anchor to the next person who checked it.
- **Committed in:** `fbe7ab7`

**5. [Rule 3 - Blocking] ktlint `chain-method-continuation` on the new input declarations**

- **Found during:** Task 3 gate run
- **Issue:** `inputs.file(...).withPropertyName(...)` must break before the first `.` in a multi-line chain.
- **Fix:** Split `inputs` onto its own line at both sites.
- **Committed in:** `e11703a`

---

**Total deviations:** 5 auto-fixed (2 blocking, 2 correctness, 1 missing-critical). **No architectural deviations, no Rule 4 escalations.** One (deviation 1) was a real hole that would have shipped a guard capable of silently not running. Three were plan-text or tooling corrections. No packages added or changed, consistent with `T-22-SC`.

## Issues Encountered

**1. `executeTool` still runs on the EDT — reported, deliberately not fixed.** Unchanged from where 22-07 and 22-08 left it: three synchronous call sites on the Event Dispatch Thread. This plan is documentation and test-only and touched no production Kotlin. REL-05 / Phase 23 owns it, and ADR-15 records it as an explicit `Residual:` bullet stating that the ADR makes **no** claim about EDT behaviour.

**2. Approval memory is per-session and not persisted — a rejected alternative, not an oversight.** D-09 rejected a persisted `CONFIRM`→`AUTO` downgrade on settings-import-attack grounds, and `restoreSessions()` building a fresh `ToolSessionState` is stricter than D-10 requires. ADR-15 records this as a `Residual:` bullet that ends *"do not 'fix' it by persisting the set"* — deliberately phrased so a future reader cannot mistake it for future work.

**3. The `RedactionTest` wall-clock flake did not fire** on any of the seven full-suite runs during this plan. It is now recorded in `CONCERNS.md` as a standing entry with its cause (`SafeRegex.DEFAULT_TIMEOUT_MS = 50L` under a two-second stage budget), its fail-closed direction, and an explicit instruction **not** to raise the constant to silence it, since that constant is a ReDoS bound.

**4. The guard cannot judge truth, and says so in its own KDoc.** `DecisionsAdrTest`'s class KDoc states plainly what it can and cannot do, and points at `22-HUMAN-UAT.md` test 2 for the half it cannot cover. This is deliberate: a guard that looks like it validates an ADR, but only matches a string, is exactly how SC1 gets marked done without anyone reading the document.

## Known Stubs

None. ADR-15, the guard, the `CONCERNS.md` corrections and `22-HUMAN-UAT.md` are all complete. The four UAT items carry `result: [pending]` by design — they are the work assigned to a human, not unfinished work.

## Threat Model Coverage

| Threat ID | Disposition | How this plan discharges it |
|-----------|-------------|------------------------------|
| T-22-37 | mitigate | ADR-15 carries D-05's sentence verbatim plus the Pitfall 1 worked examples (`project_options_get` / `user_options_get` are `CONFIRM` despite being read-only). `DecisionsAdrTest` asserts the sentence survives **and** that the catalog KDoc copy still agrees — both proven red by mutation. `McpToolCatalogTierParityTest` (22-02) makes an `AUTO` promotion a reviewed diff. |
| T-22-38 | mitigate | Split coverage, as research recommended: an automated string-match guard on the PR gate for the inherited sentence, plus `22-HUMAN-UAT.md` test 2 for factual accuracy. Neither alone is sufficient and the guard's KDoc says so. |
| T-22-39 | mitigate | Six explicit `Residual:` bullets: the token cost of deny-for-session, non-persistence of approvals, the live-session-only card record, the four implicit-denial paths with no surviving surface, the unreachable compact unknown-tool string, and the untouched EDT behaviour. Every ADR-15 claim was checked against code before being written. |
| T-22-17 | mitigate | ADR-15 records the omission as deliberate, with the reason: advertising the tier beside `[unsafe]` / `[pro]` / `[external]` would hand an injected prompt a map of which tools run silently. Verified against `McpToolExecutorImpl.kt:113-123` that no tier marker is emitted. |
| T-22-40 | mitigate | ADR-15 names both rejected alternatives and their reasons — the settings-import attack path for a persisted downgrade, and "the control's own bypass shipped in the box" for a global off-switch — so neither is re-litigated from scratch. |
| T-22-SC | accept | Zero packages added or changed. |

## Threat Flags

None. This plan adds no network endpoint, no auth path, no file access and no schema change. `DecisionsAdrTest` reads two repository files at test time; it is test-only code and ships in no artifact. The `build.gradle.kts` change declares build inputs and alters no runtime behaviour.

## User Setup Required

None.

## Next Phase Readiness

- **ADR-16 is the next free number.** `DecisionsAdrTest.adr15ExistsAndIsTheHighestNumberedAdr` will fail the moment `## ADR-16` is added, which is intentional — extend the guard to the new ADR at the same time rather than deleting the assertion.
- **For Phase 23 / REL-05:** `executeTool` remains on the EDT at three call sites. ADR-15 makes no claim about EDT behaviour, so moving it requires no ADR amendment.
- **For Phase 26:** `CONCERNS.md` now names two open items precisely — the missing mutation/`unsafeOnly` classification test (adjacent invariant exists, gap unchanged), and `ChatPanelConcurrencyTest.kt:59-71`'s stale KDoc premise. The `RedactionTest` flake entry carries a fix approach and an explicit prohibition on raising the timeout.
- **For DOC-03 (Phase 26):** ADR-15 is `DECISIONS.md`'s share of the promised documentation of the confirmation flow. `README.md`, `SPEC.md` and the GitBook site are still outstanding.
- **Line citations in this summary and in ADR-15 are current as of `9bd20bf`** and were each re-located by symbol rather than trusted from plan text.
- **No blockers.**

## Success Criteria

- [x] ADR-15 records the threat model, the mitigation-not-a-control qualification of ADR-11's marker with a checkable reason, D-05's `AUTO` definition verbatim, and the deliberate independence from `unsafeOnly`
- [x] Every residual is stated explicitly; nothing is claimed that does not ship
- [x] A PR-gate test guards the inherited sentence in both of its two copies — and actually runs, which took a build-input fix
- [x] `CONCERNS.md` no longer carries the measured-false premise about Swing headless testing, and records what was NOT fixed
- [x] SC1 is assigned to both an automated guard and a human reviewer, not left unassigned

## Self-Check: PASSED

- FOUND: `DECISIONS.md` (contains `## ADR-15`)
- FOUND: `src/test/kotlin/com/six2dez/burp/aiagent/DecisionsAdrTest.kt`
- FOUND: `.planning/phases/22-agent-tool-call-trust-boundary/22-HUMAN-UAT.md`
- FOUND: `.planning/codebase/CONCERNS.md`
- FOUND: `build.gradle.kts`
- FOUND: commit `fbe7ab7`
- FOUND: commit `e11703a`
- FOUND: commit `9bd20bf`
- No file deletions in any commit (`git diff --diff-filter=D` empty for all three)
- `STATE.md` and `ROADMAP.md` untouched, as required in worktree mode
- `detekt-baseline.xml` untouched

---
*Phase: 22-agent-tool-call-trust-boundary*
*Completed: 2026-08-14*
