---
phase: 26-coverage-static-analysis-debt-docs
plan: 05
subsystem: docs
tags: [security-advisory, secretcipher, sec-06, tool-approval, gradle-cache, doc-03, qual-07, sc5, sc6]

requires:
  - phase: 20-mcp-access-control-correctness
    provides: "The SEC-04 fix and its reproduced observation — an unauthenticated POST /message returning 400 'sessionId query parameter is not provided' — which the advisory reports verbatim"
  - phase: 21-redaction-completeness
    provides: "The PRIV-05 fix and the six real-world cookie names verified against the live regexes, which the advisory enumerates"
  - phase: 22-agent-tool-call-trust-boundary
    provides: "ToolApprovalGate, ToolApprovalCard, ToolDecisionReporter and ADR-15 — the confirmation gate this plan documents for users for the first time"
  - phase: 26-coverage-static-analysis-debt-docs
    provides: "26-04's SC4 disposition (ChatPanel's EDT check is documented test-only, not a production guarantee) — honoured by not describing it as a runtime control anywhere here"
provides:
  - "SECURITY.md `## Security Advisories` — SEC-04 and PRIV-05 with affected versions, impact, fixed version and a User action line each"
  - "A corrected Supported Versions table naming the lines this project actually supports"
  - "An accurate SecretCipher at-rest statement across five in-repo documents, replacing three absolute claims"
  - "The first user-facing documentation of the SEC-06 tool-call confirmation gate (README, SPEC §6/§9/§10, docs/ui-safety-guide.md)"
  - "SecurityDocsTest — 21 assertions pinning every claim above, with six tasks.test input declarations so a documentation-only edit cannot be cache-served"
  - "26-GITBOOK-HANDOFF.md — a prepared, file-by-file diff for the out-of-repo GitBook site"
affects: [26-06 (ADR-15's stale residual and ADR-17), any phase editing SECURITY.md/README.md/SPEC.md, the v0.10.0 release cut which must revisit "not yet published"]

actuals:
  tokens: 15045
  tasks: 3
  commits: 3

tech-stack:
  added: []
  patterns:
    - "Documentation-as-control: a prose claim that stands in for a security property gets a source-reading test AND a tasks.test input declaration, because a markdown-only edit produces byte-identical bytecode and an identical cache key"
    - "Advisory entries sliced per-finding in the guard, so one finding's version range cannot satisfy an assertion about the other's"
    - "Cross-repository work is a prepared diff plus a human-action gate, never an automated write into a repository the phase does not own"

key-files:
  created:
    - src/test/kotlin/com/six2dez/burp/aiagent/SecurityDocsTest.kt
    - .planning/phases/26-coverage-static-analysis-debt-docs/26-GITBOOK-HANDOFF.md
  modified:
    - SECURITY.md
    - README.md
    - SPEC.md
    - docs/anthropic-backend.md
    - docs/external-mcp-servers.md
    - docs/ui-safety-guide.md
    - build.gradle.kts

key-decisions:
  - "The advisory states that 0.10.0 is the fixing release AND that it is not yet published, rather than implying an available fix. A reader who goes looking for a release that does not exist concludes the advisory is wrong about everything else too."
  - "No CVE or GHSA identifier invented; the advisory says explicitly that none has been issued, and a guard asserts nothing CVE-shaped appears."
  - "The overstated at-rest claim was REPLACED, not deleted. A removed claim reads to a returning user as though the property still holds and merely went undocumented."
  - "SC6's GitBook clause is recorded OPEN. The handoff document existing is not the criterion, and the checkpoint confirmation was harness-generated under yolo, not human."

patterns-established:
  - "Every new documentation claim names the repository symbol that makes it true (see `## Claim-to-symbol map`). A claim with no symbol behind it is deleted, not softened."

requirements-completed: [DOC-03, QUAL-07]

coverage:
  - id: D1
    description: "SECURITY.md carries a Security Advisories section naming SEC-04 and PRIV-05, each with affected versions 0.9.0-0.9.2, the fixed version 0.10.0, and a User action line"
    requirement: DOC-03
    verification:
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/SecurityDocsTest.kt#securityPolicyCarriesAnAdvisoriesSection"
        status: pass
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/SecurityDocsTest.kt#theAdvisoriesSectionNamesBothFindings"
        status: pass
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/SecurityDocsTest.kt#theSec04EntryNamesEveryAffectedVersionAndTheFix"
        status: pass
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/SecurityDocsTest.kt#thePriv05EntryNamesEveryAffectedVersionAndTheFix"
        status: pass
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/SecurityDocsTest.kt#theSec04EntryTellsAnAffectedUserWhatToDo"
        status: pass
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/SecurityDocsTest.kt#thePriv05EntryTellsAnAffectedUserWhatToDo"
        status: pass
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/SecurityDocsTest.kt#thePriv05EntryNamesTheCookiesThatLeaked"
        status: pass
    human_judgment: false
  - id: D2
    description: "No fabricated advisory identifier; the absence of a CVE/GHSA is stated rather than left to assumption, and the unpublished status of 0.10.0 is explicit"
    requirement: DOC-03
    verification:
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/SecurityDocsTest.kt#theAdvisorySaysNoIdentifierWasIssuedRatherThanLeavingTheReaderToAssume"
        status: pass
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/SecurityDocsTest.kt#theAdvisoryDoesNotImplyThePublishedFixExists"
        status: pass
    human_judgment: false
  - id: D3
    description: "SECURITY.md's Supported Versions table names the lines actually supported today, not the four-milestone-stale 0.5.x"
    requirement: DOC-03
    verification:
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/SecurityDocsTest.kt#theSupportedVersionsTableNoLongerNamesTheStaleLine"
        status: pass
    human_judgment: false
  - id: D4
    description: "SecretCipher's at-rest guarantee is stated accurately in five in-repo documents — the master key sits beside the ciphertext in Burp Preferences, so this is obfuscation against casual inspection, not protection against a local attacker"
    requirement: QUAL-07
    verification:
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/SecurityDocsTest.kt#readmeNoLongerCarriesTheAbsoluteAtRestClaim"
        status: pass
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/SecurityDocsTest.kt#everyDocumentThatClaimsEncryptionAtRestAlsoStatesTheCaveat"
        status: pass
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/SecurityDocsTest.kt#theFullAtRestStatementSaysWhatTheEncryptionDoesNotDo"
        status: pass
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/SecurityDocsTest.kt#theSecurityModelStatesWhereTheMasterKeyLives"
        status: pass
    human_judgment: false
  - id: D5
    description: "The SEC-06 tool-call confirmation flow is documented for users in README.md, SPEC.md and docs/ui-safety-guide.md — the decision requirement, the three tiers, the fail-closed rule, the audit record and the independence from Unsafe Mode"
    requirement: DOC-03
    verification:
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/SecurityDocsTest.kt#theConfirmationFlowIsDocumentedForUsers"
        status: pass
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/SecurityDocsTest.kt#theDocumentedFlowStatesThatAModelEmittedCallNeedsADecision"
        status: pass
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/SecurityDocsTest.kt#theDocumentedFlowNamesAllThreeTiers"
        status: pass
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/SecurityDocsTest.kt#theDocumentedFlowStatesThatAnUnrecognisedToolFailsClosed"
        status: pass
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/SecurityDocsTest.kt#theDocumentedFlowStatesThatDecisionsAreRecorded"
        status: pass
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/SecurityDocsTest.kt#theDocumentedFlowDistinguishesTheTierFromUnsafeMode"
        status: pass
      - kind: unit
        ref: "src/test/kotlin/com/six2dez/burp/aiagent/SecurityDocsTest.kt#theOperatorRunbookExplainsTheCardAndItsActions"
        status: pass
    human_judgment: false
  - id: D6
    description: "The documentation guard cannot be served from the build cache on a documentation-only edit — six markdown files declared as tasks.test inputs"
    requirement: QUAL-07
    verification:
      - kind: other
        ref: "Cache probe: mutate one asserted word, re-run `./gradlew test --tests '*SecurityDocsTest'` WITHOUT cleanTest — recorded twice (SECURITY.md, SPEC.md) in `## Cache probes`"
        status: pass
    human_judgment: false
  - id: D7
    description: "SC6's GitBook half — the five-plus pages of the out-of-repo burp-ai-agent-docs site that carry a stale or absolute claim"
    requirement: DOC-03
    verification: []
    human_judgment: true
    rationale: "OPEN, not satisfied. A prepared diff exists at 26-GITBOOK-HANDOFF.md and the separate repository is provably unmodified, but nothing in this repository can verify that the diff was applied to `burp-ai-agent-docs`. The plan's checkpoint was `gate=\"blocking-human\"`; under this project's yolo mode it was auto-confirmed by the harness, which is not evidence an operator applied anything. See `## SC6 GitBook half`."

duration: 14min
completed: 2026-08-22
status: complete
---

# Phase 26 Plan 05: Security Advisory and Documentation Accuracy Summary

**A SECURITY.md advisory for SEC-04 and PRIV-05 with per-finding user actions, an accurate `SecretCipher` at-rest statement replacing three absolute claims across five documents, the first user-facing account of the SEC-06 confirmation gate, and a 21-assertion guard that the build cannot cache away.**

## Performance

- **Duration:** 14 min
- **Started:** 2026-08-22T13:33:04Z
- **Completed:** 2026-08-22T13:47:32Z
- **Tasks:** 3
- **Files modified:** 9 (7 modified, 2 created)

## Accomplishments

- **The advisory users on 0.9.0-0.9.2 were owed.** `SECURITY.md` now names SEC-04 and PRIV-05, states the mechanism of each in one sentence, states what it actually exposed, names the reproduced observation, names all three affected releases and the fixing release, and gives each finding a **User action** line naming a concrete remediation (rotate the MCP bearer token; treat affected session cookies as disclosed and rotate them).
- **No fabricated identifier.** The advisory states in as many words that no CVE and no GHSA has been issued, and that 0.10.0 is unpublished. A guard asserts nothing CVE-shaped appears.
- **The Supported Versions table is no longer four milestones stale.** `0.5.x` is gone; `0.10.x`, `0.9.x` (security fixes until 0.10.0 ships) and `< 0.9` are named.
- **The at-rest claim is now true.** Three absolute claims (`README.md:15`'s `no plaintext in preferences`, and the unqualified assertions in `SPEC.md`, `docs/anthropic-backend.md` ×3 and `docs/external-mcp-servers.md`) are replaced by an accurate statement naming `secret.master.key.v1` as where the master key lives — beside the ciphertext it protects.
- **The SEC-06 gate is documented for users for the first time.** It shipped in Phase 22 and lived only in `DECISIONS.md` ADR-15, a design record. `README.md`, `SPEC.md` §6/§9/§10 and a new `## Tool-Call Confirmation` runbook in `docs/ui-safety-guide.md` now describe it.
- **The guard cannot be cached away.** `SecurityDocsTest` (21 tests) plus six `tasks.test` `inputs.file` declarations, with two cache probes recorded showing `:test` re-executing and turning red rather than reporting UP-TO-DATE.

## Task Commits

1. **Task 1 (tracer): SEC-04/PRIV-05 advisory + guard + build wiring** — `ebadf82` (docs)
2. **Task 2: at-rest corrections + confirmation flow across six documents** — `cbc568b` (docs)
3. **Task 3: prepared out-of-repo GitBook diff** — `3fbe347` (docs)

## Files Created/Modified

- `SECURITY.md` — new `## Security Advisories` section (SEC-04, PRIV-05); corrected Supported Versions table; corrected Security Model at-rest bullet.
- `README.md` — Highlights bullet corrected (stale absolute claim replaced, not deleted); external-servers line caveated; Privacy and Security Notes gains the full at-rest statement and the confirmation-flow bullet.
- `SPEC.md` — §4.4 caveated and pointed at §9; §6's one-axis sentence **amended** plus a new confirmation-gate bullet; §9 gains the full at-rest statement and the gate; §10 gains an acceptance test for the gate.
- `docs/anthropic-backend.md` — three at-rest claims corrected (setup step, config table, Privacy Notes).
- `docs/external-mcp-servers.md` — at-rest claim corrected; new bullet stating `ext:` tools always confirm.
- `docs/ui-safety-guide.md` — new `## Tool-Call Confirmation` runbook (11 numbered points, matching the file's existing style).
- `src/test/kotlin/com/six2dez/burp/aiagent/SecurityDocsTest.kt` — the guard, 21 tests.
- `build.gradle.kts` — six `tasks.test` `inputs.file` declarations following the `adrRecord` pattern. The `dependencies` block is unchanged.
- `.planning/phases/26-coverage-static-analysis-debt-docs/26-GITBOOK-HANDOFF.md` — the prepared out-of-repo diff.

## Claim-to-symbol map

The plan requires every new documentation claim to name the repository symbol that makes it true. A claim with no symbol behind it is a claim to delete, not to soften. Every claim below was checked against the named symbol by reading it during execution.

| New documentation claim | Symbol that makes it true |
| :--- | :--- |
| Secrets encrypted with AES-256-GCM under a per-install random master key | `SecretCipher` — `TRANSFORMATION = "AES/GCM/NoPadding"`, `KEY_LENGTH_BYTES = 32`, `loadOrCreateMasterKey()` |
| The master key is stored in Burp Preferences, Base64-encoded, beside the ciphertext | `SecretCipher.MASTER_KEY_PREF_KEY = "secret.master.key.v1"`; `prefs.setString(MASTER_KEY_PREF_KEY, Base64.getEncoder().encodeToString(keyBytes))` |
| A model-emitted tool call does not reach Burp until the user decides | `ChatPanel.maybeExecuteToolCall` → `ToolApprovalGate.evaluate` returning `Run` / `Ask` / `Denied` |
| Three tiers: automatic, confirm-with-session-approval, confirm-every-time | `enum class SecTier { AUTO, CONFIRM, CONFIRM_EACH }` in `McpToolCatalog.kt` |
| An unrecognised tool name fails closed to confirm-every-time | `ToolApprovalGate.tierFor`: `return descriptor?.secTier ?: SecTier.CONFIRM_EACH` |
| External `ext:`-namespaced tools always confirm every call | `ToolApprovalGate.tierFor`: `if (canonical.startsWith("ext:")) return SecTier.CONFIRM_EACH` — short-circuits before the catalog lookup |
| Read-only is not sufficient for automatic; bulk traffic tools still ask | `SecTier` KDoc: `proxy_http_history`, `site_map`, `scanner_issues` are `CONFIRM` |
| The four card actions | `ToolApprovalCard`: `LABEL_APPROVE_ONCE`, `LABEL_APPROVE_SESSION`, `LABEL_DENY`, `LABEL_DENY_SESSION` |
| The card is inline in the transcript, not a modal | `ToolApprovalCard` is a Swing component added to the session transcript; `ChatPanel.clearChatState`'s KDoc contrasts it with the `JOptionPane` modal path |
| Full arguments are sent even though the card truncates for display | `ToolApprovalCard.ARGS_SENT_IN_FULL` — "The full arguments are sent to the tool if you approve." |
| Approve-for-session is scoped to one chat and cleared by Clear Chat | `ChatPanel.clearChatState`: `state.approvalMemory = ToolApprovalMemory()` |
| Approvals do not survive a Burp restart | `ToolApprovalMemory` holds `mutableSetOf<String>()` in memory only; no persistence path |
| A denial returns a neutral result, not an error | `ToolApprovalGate.DENIAL_RESULT` = "This tool call was not authorised by the user. Do not retry it; continue with the information you already have." |
| Every decision is audit-logged, plus a Burp Output line | `ToolDecisionReporter.report` → `AuditLogger.emitGlobal(MCP_TOOL_DECISION_EVENT, payload)` + the Output-sink lambda |
| Audit logging is off by default, so Output is the record most users see | `ToolDecisionReporter` KDoc, "Three destinations, per Phase 20 D-06"; `AgentSettings.auditEnabled` defaults off |
| The tier is independent of Unsafe Mode | `SecTier` KDoc D-01: "a SECOND, INDEPENDENT axis from `McpToolDescriptor.unsafeOnly`"; `ai_analyze` / `ai_passive_scan` are `CONFIRM_EACH` without being `unsafeOnly` |
| SEC-04's mechanism and reproduced observation | `.planning/STATE.md` `## Milestone Origin`; `.planning/REQUIREMENTS.md` SEC-04; Phase 20 |
| PRIV-05's mechanism and the six cookie names | `.planning/REQUIREMENTS.md` PRIV-05; Phase 21 |

**Nothing was claimed that does not ship.** The 8-call chaining ceiling (`ChatPanel.MAX_AUTO_TOOL_ITERATIONS`) is preserved as accurate in the GitBook handoff; only the word *autonomously* is corrected there.

## Cache probes

Both recorded with observed output, restored afterwards with `sed` (never `git stash` — `refs/stash` is shared across linked worktrees and a sibling executor was live).

**Probe 1 — `SECURITY.md`, after declaring `inputs.file("SECURITY.md")` as `securityPolicy`:**

- Baseline run: `> Task :test UP-TO-DATE`
- Mutated `secret.master.key.v1` → `secret.master.key.vX`, re-ran WITHOUT `cleanTest`:
  ```
  > Task :test FAILED
  SecurityDocsTest > theSecurityModelStatesWhereTheMasterKeyLives() FAILED
  11 tests completed, 1 failed
  BUILD FAILED in 1s
  ```
- Restored; green again.

**Probe 2 — `SPEC.md`, after declaring the remaining five markdown inputs:**

- Baseline run: `> Task :test UP-TO-DATE`
- Mutated `casual inspection` → `CASUALX inspection`, re-ran WITHOUT `cleanTest`:
  ```
  > Task :test FAILED
  SecurityDocsTest > theFullAtRestStatementSaysWhatTheEncryptionDoesNotDo() FAILED
  21 tests completed, 1 failed
  BUILD FAILED in 1s
  ```
- Restored; green again.

The task re-executed and turned red in both cases rather than being served UP-TO-DATE — which is the whole point, since a markdown-only edit produces byte-identical compiled output and therefore an identical cache key without these declarations.

## SC6 GitBook half

**Status: OPEN — not satisfied.**

The out-of-repo site lives in a separate git repository at `~/Tools/burp-ai-agent-doc` (GitHub `burp-ai-agent-docs`), published at burp-ai-agent.six2dez.com. No plan in this repository can commit to it or push it.

**What was done:** `.planning/phases/26-coverage-static-analysis-debt-docs/26-GITBOOK-HANDOFF.md` contains a prepared, file-by-file diff covering **seven changes across six pages** — `README.md` (the Auto Tool Chaining bullet and the Key Features table row), `developer/data-flow.md` (the "executed automatically in a loop" sentence and the mermaid block under it), `mcp/security-model.md` (§3 amended for the two-axis model, plus a new §7 documenting the confirmation gate), `privacy/limitations.md` (a new secrets-at-rest section), `backends/anthropic.md` (three at-rest claims) and `mcp/external-servers.md` (the at-rest claim plus an `ext:` bullet). Each section quotes the **CURRENT** text verbatim so the operator can confirm the diff applies to what is on disk, gives the **REPLACEMENT**, and names the motivating criterion or repository symbol.

**The wording was compared, not assumed.** Each replacement was checked against what this plan landed in this repository's `README.md`, `SPEC.md` and `docs/ui-safety-guide.md`, so the site and the repo do not diverge into two slightly different accounts of the same security control.

**The separate repository is provably unmodified.** Run before any operator action, as the plan's `<ordering>` requires:

```
$ test -f .planning/phases/26-coverage-static-analysis-debt-docs/26-GITBOOK-HANDOFF.md
handoff exists
$ git -C "$HOME/Tools/burp-ai-agent-doc" status --porcelain
(no output)
$ git -C "$HOME/Tools/burp-ai-agent-doc" rev-parse --short HEAD
3256cc9
```

Same result before writing the handoff and after committing it. The executor read that repository and wrote nothing.

**Operator response: none received.** The plan made this a `checkpoint:human-action` with `gate="blocking-human"` — a gate that is never auto-approved by design. This project runs with `mode: yolo`, which auto-confirms blocking checkpoints, and the developer was told and accepted that in advance. So the confirmation on record for this checkpoint is **harness-generated, not human**.

An auto-confirmed handoff is not evidence that anyone applied the diff. Nothing in this repository can observe the state of `burp-ai-agent-docs`, and the check above shows it unchanged at `3256cc9`. Therefore SC6's GitBook clause is recorded **OPEN**, and the phase verifier should report it as an open item rather than as satisfied. The handoff document existing is not the criterion.

**What closes it:** the operator applies the seven changes in `26-GITBOOK-HANDOFF.md` (the file ends with an apply procedure and a per-page checklist), pushes, and confirms. At that point a dirty-then-committed tree in that repository is the expected and correct state.

## Decisions Made

- **State the unpublished fix plainly.** 0.10.0 is named as the fixing release *and* explicitly as not yet published. Implying an available fix sends a reader looking for a release that does not exist, and a reader who catches one false statement discounts the true ones beside it.
- **No invented identifier, and a guard against one.** `theAdvisorySaysNoIdentifierWasIssuedRatherThanLeavingTheReaderToAssume` asserts both that the absence is stated and that nothing matching `CVE-\d{4}-\d{4,}` appears.
- **Replace, never delete, the overstated claim.** Deleting `no plaintext in preferences` would have satisfied a grep while leaving a returning user believing the property still holds and simply went undocumented.
- **Correct all five in-repo documents in one pass.** Shipping a corrected `README.md` beside two uncorrected `docs/` pages leaves the overstated claim reachable, which is the same outcome as not correcting it. The guard iterates over the list rather than asserting per-file, so a sixth document added later must join the list deliberately.
- **Amend `SPEC.md` §6 rather than supplement it.** As written it named one gating axis where two now exist; adding a second bullet beside an incorrect sentence leaves the incorrect sentence.
- **Per-entry slicing in the guard.** Version assertions run against the individual advisory entry, not the whole file — a whole-file match would pass if one finding carried all three versions and the other carried none.
- **Six per-file `inputs.file` declarations rather than a wider `inputs.dir`.** The existing `inputs.dir("src/main/kotlin")` already covers source-reading guards; these six are repo-root and `docs/` markdown files, and naming them individually keeps the re-run trigger tight.

## Deviations from Plan

None — plan executed as written.

Two small judgement calls worth recording, neither a scope change:

- **One assertion phrasing was aligned to the docs, and one doc sentence to the assertion.** `theFullAtRestStatementSaysWhatTheEncryptionDoesNotDo` failed first against `README.md`, which read "— **not** as protection against a local attacker". Rather than loosen the assertion to match, the README sentence was reworded to "It does **not** protect against a local attacker…", which is both clearer and consistent with `SPEC.md` §9 and `SECURITY.md`. Caught and fixed before the Task 2 commit.
- **One `build.gradle.kts` comment was reworded** so that `grep -c 'adrRecord' build.gradle.kts` still returns exactly 1 as the acceptance criterion requires. The comment now says "the DECISIONS.md declaration above" instead of naming `adrRecord`.

**Total deviations:** 0 auto-fixed under Rules 1-4.
**Impact on plan:** none. No architectural change, no dependency added, no package installed.

## Issues Encountered

None. The full suite, `detekt` and `ktlintCheck` were green at every commit, and `detekt-baseline.xml` is byte-unchanged (`git diff --quiet detekt-baseline.xml` exits 0) — no finding from this plan was added to the baseline.

`DECISIONS.md` was read for accuracy (ADR-15) and never edited; plan 26-06 owns it. `git diff --name-only` across all three commits contains no `DECISIONS.md`, no `STATE.md` and no `ROADMAP.md`.

## Plan-level verification

| Check | Result |
| :--- | :--- |
| `./gradlew test detekt ktlintCheck` | BUILD SUCCESSFUL |
| `./gradlew test --tests '*SecurityDocsTest'` | 21 tests, 0 failures |
| `git diff --stat detekt-baseline.xml` | empty |
| `git diff --name-only` contains `DECISIONS.md` | no |
| `git -C ~/Tools/burp-ai-agent-doc status --porcelain` | no output (at `3256cc9`) |
| Two cache probes recorded with observed output | yes — `## Cache probes` |
| `dependencies` block in `build.gradle.kts` unchanged | yes — no dependency line in the diff |
| `grep -c 'no plaintext in preferences' README.md` | 0 |
| `grep -c '0.5.x' SECURITY.md` | 0 |
| `grep -c 'adrRecord' build.gradle.kts` / `securityPolicy` | 1 / 1 |
| `grep -c 'inputs' build.gradle.kts` | 9 → 15 (+6, exactly the six markdown files) |

## User Setup Required

None — no external service configuration required.

**But one human action is outstanding:** applying `26-GITBOOK-HANDOFF.md` to `burp-ai-agent-docs`. See `## SC6 GitBook half`.

## Next Phase Readiness

- **SC5 satisfied.** The advisory exists, is accurate, names affected and fixed versions, and gives both findings a concrete user action — pinned by 11 of the guard's 21 assertions.
- **SC6 in-repo half satisfied** across `README.md`, `SPEC.md` and three `docs/` pages. The `DECISIONS.md` half belongs to plan 26-06, which was running concurrently in a sibling worktree; this plan did not touch that file.
- **SC6 GitBook half OPEN.** Carry it forward as an open item.
- **For the v0.10.0 release cut:** `SECURITY.md` currently says 0.10.0 is "not yet published" and the Supported Versions table says 0.9.x receives security fixes "until 0.10.0 ships". Both are guarded assertions (`theAdvisoryDoesNotImplyThePublishedFixExists`), so shipping 0.10.0 will turn `SecurityDocsTest` red until the advisory is updated — deliberately. That red test is the reminder.

---
*Phase: 26-coverage-static-analysis-debt-docs*
*Completed: 2026-08-22*

## Self-Check: PASSED

- `src/test/kotlin/com/six2dez/burp/aiagent/SecurityDocsTest.kt` — FOUND
- `.planning/phases/26-coverage-static-analysis-debt-docs/26-GITBOOK-HANDOFF.md` — FOUND
- Commit `ebadf82` — FOUND
- Commit `cbc568b` — FOUND
- Commit `3fbe347` — FOUND
