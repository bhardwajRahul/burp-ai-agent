---
phase: 26-coverage-static-analysis-debt-docs
verified: 2026-08-22T14:58:00Z
status: gaps_found
score: 5/6 must-haves verified
behavior_unverified: 0
overrides_applied: 0
gaps:
  - truth: "SC6 — README.md, SPEC.md, DECISIONS.md and the GitBook repo (burp-ai-agent-docs) describe the tool-call confirmation flow and state SecretCipher's at-rest guarantee accurately"
    status: partial
    reason: >-
      The in-repo half (README.md, SPEC.md, DECISIONS.md) is fully and accurately met and is
      committed phase work. The GitBook half is NOT met and is not cosmetic: the published docs
      repository at ~/Tools/burp-ai-agent-doc is byte-unmodified (git status --porcelain empty,
      HEAD 3256cc9 dated 2026-06-26, two months before this phase). Its live pages still carry the
      exact overstatement SC6 exists to remove, and carry no description of the confirmation flow
      at all. Plan 26-05 prepared a correct diff but was prohibited from writing to that repo; a
      prepared handoff is not an applied doc change. The plan's checkpoint:human-action was
      auto-confirmed by the harness under mode:yolo, which is not evidence a human applied anything.
    artifacts:
      - path: "~/Tools/burp-ai-agent-doc/backends/anthropic.md:12"
        issue: "Still reads 'It is encrypted at rest (AES-256-GCM, ENC1:-prefixed) and never written to logs or exported settings.' — no master-key-beside-ciphertext caveat."
      - path: "~/Tools/burp-ai-agent-doc/backends/anthropic.md:30"
        issue: "'The API key is encrypted with a per-install master key' — states the mechanism, omits that the key is stored beside the ciphertext."
      - path: "~/Tools/burp-ai-agent-doc/mcp/external-servers.md:26"
        issue: "'SSE bearer tokens are stored encrypted at rest (AES-256-GCM, ENC1:-prefixed)' — same uncaveated at-rest claim."
      - path: "~/Tools/burp-ai-agent-doc/ (whole repo)"
        issue: "grep for SEC-06 / 'Approve for session' / ToolApprovalGate returns zero hits — the tool-call confirmation flow is undocumented on the published site."
    missing:
      - "Apply the 7 changes across 6 pages recorded in 26-GITBOOK-HANDOFF.md to ~/Tools/burp-ai-agent-doc"
      - "Commit and push that repo so burp-ai-agent.six2dez.com serves the corrected at-rest claim"
      - "Re-verify in that repository, then tick DOC-03 in REQUIREMENTS.md"
human_verification:
  - test: "Apply 26-GITBOOK-HANDOFF.md to ~/Tools/burp-ai-agent-doc, then re-grep that repo for the at-rest caveat and for SEC-06."
    expected: "backends/anthropic.md and mcp/external-servers.md state that the master key is stored beside the ciphertext in Burp Preferences; at least one page describes the tool-call confirmation tiers."
    why_human: "Out-of-repo write to a separate git repository the verifier must not modify; requires the maintainer's push to the docs remote."
---

# Phase 26: Coverage, Static-Analysis Debt & Docs — Verification Report

**Phase Goal:** The milestone's code is covered by tests, the static-analysis baseline shrinks rather than grows, and users on affected versions are told.
**Verified:** 2026-08-22
**Status:** gaps_found (1 gap — SC6's GitBook clause)
**Re-verification:** No — initial verification

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | `shellEscape` quotes by allowlist; `foo;id` and `$(cmd)` reach `sh -c` quoted; asserted directly on the helper | ✓ VERIFIED | `CliBackend.kt:861-884` — `SHELL_SAFE_CHARS` is exactly `[a-zA-Z0-9._/-]`, tested with `arg.all { it in SHELL_SAFE_CHARS }`, else single-quote with `'` → `'"'"'`. `ShellEscapeTest` (13 tests, 0 failures, run by me) asserts `"'foo;id'"` and `"'$(cmd)'"` on the helper directly, plus the joined `sh -c` argv on both macOS and Linux pty shapes. |
| 2 | Line coverage on `redact`, `mcp` and `config` rises measurably against the recorded baseline | ✓ VERIFIED | Read `build/reports/jacoco/test/jacocoTestReport.xml` directly, not the SUMMARY. All 14 floors MET; every figure in `26-COVERAGE.md` reproduces exactly. See table below. |
| 3 | detekt baseline has fewer entries than 1096, and no Phase 20–25 finding was added to it | ✓ VERIFIED | `grep -c '<ID>'` → **1040**. `git diff -U0 ab567fb..HEAD -- detekt-baseline.xml \| grep -c '^+.*<ID>'` → **0**. `detekt.yml` byte-identical. `./gradlew detekt --rerun-tasks` run by me: **0 code smells** across 312 files / 58,242 sloc. |
| 4 | `assert()`-based EDT enforcement upgraded, or explicitly documented as test-only | ✓ VERIFIED (second branch) | `ChatPanel.kt:819-847` KDoc states the check "compiles to nothing and has no production effect at all". ADR-17 clause 2 records the disposition verbatim. Zero user-facing docs claim otherwise (grep exit 1). ADR-17 records the `shutdown()` → `cancelInFlightRequest` residual. `ChatPanelEdtGuardTest` (6 tests) + `ChatPanelEdtConfinementTest` (23 tests) pass. |
| 5 | `SECURITY.md` carries an SEC-04 / PRIV-05 advisory naming affected versions, impact, fixed version | ✓ VERIFIED | `SECURITY.md:50-108`. Both entries: "**Affected:** 0.9.0, 0.9.1, 0.9.2 · **Fixed in:** 0.10.0 (unreleased)", concrete impact, observed reproduction, and a user action naming token/cookie rotation. Explicitly states no CVE/GHSA was issued rather than inventing one. |
| 6 | README/SPEC/DECISIONS **and the GitBook repo** describe the confirmation flow and state `SecretCipher`'s guarantee accurately | ✗ FAILED (partial) | **In-repo half VERIFIED** (see below). **GitBook half NOT MET** — `~/Tools/burp-ai-agent-doc` is unmodified at `3256cc9` (2026-06-26) and still serves the uncaveated at-rest claim. |

**Score:** 5/6 truths verified (0 present, behavior-unverified)

### SC6 split — what is met and what is not

| Half | Status | Evidence |
|------|--------|----------|
| `README.md` | ✓ | `:15` and `:211` — "The master key lives in Burp Preferences alongside the ciphertext, so this defends against casual inspection of a preferences file, not against a local attacker"; `:212` describes the three-tier confirmation flow, fail-closed resolution and the neutral denial result. Both lines confirmed present in **committed** HEAD (`git show HEAD:README.md`), untouched by the uncommitted user edit. |
| `SPEC.md` | ✓ | `:64`, `:154` (at-rest with the beside-the-ciphertext caveat and the "preference-file access is equivalent to credential access" consequence); `:123`, `:124`, `:153`, `:166` (SEC-06 tiers, `ToolApprovalGate.tierFor`, `CONFIRM_EACH` fail-closed, `DENIAL_RESULT`). |
| `DECISIONS.md` | ✓ | ADR-15 (confirmation flow, threat model); ADR-17 clause 3 (at-rest stated as protection of a preferences file or export, explicitly *not* against a local attacker). |
| GitBook `burp-ai-agent-docs` | ✗ **OPEN** | Repo clean, HEAD `3256cc9`. `backends/anthropic.md:12,30` and `mcp/external-servers.md:26` still make the uncaveated claim; zero hits for SEC-06 / "Approve for session" / `ToolApprovalGate` anywhere in the repo. |

### Required Artifacts

| Artifact | Expected | Status | Details |
|---|---|---|---|
| `src/main/.../backends/cli/CliBackend.kt` | allowlist `shellEscape` | ✓ VERIFIED | `internal fun shellEscape`, `when`-expression, allowlist constant |
| `src/test/.../ShellEscapeTest.kt` | direct + end-to-end assertions | ✓ VERIFIED | 13 tests, all pass |
| `detekt-baseline.xml` | < 1096 entries, removals only | ✓ VERIFIED | 1040; −56 / +0 |
| `detekt.yml` | untouched | ✓ VERIFIED | `git diff --stat` empty |
| `SECURITY.md` | SEC-04 + PRIV-05 advisory | ✓ VERIFIED | `:50-108` |
| `DECISIONS.md` | ADR-17 + ADR-16 7th residual | ✓ VERIFIED | ADR-17 present with 3 clauses + 5 consequences; ADR-16 has 7 residuals |
| `26-COVERAGE.md` | coverage seal with provenance | ✓ VERIFIED | Every number reproduces from the jacoco XML |
| `26-GITBOOK-HANDOFF.md` | prepared out-of-repo diff | ⚠️ PREPARED, NOT APPLIED | Correct as a handoff; does not satisfy SC6 |
| `~/Tools/burp-ai-agent-doc` | corrected pages | ✗ MISSING | Unmodified since 2026-06-26 |

### Data-Flow Trace (Level 4)

| Artifact | Data | Source | Real? | Status |
|---|---|---|---|---|
| `26-COVERAGE.md` per-package table | line/branch counters | `build/reports/jacoco/test/jacocoTestReport.xml` | Yes — re-derived independently, exact match | ✓ FLOWING |
| `26-COVERAGE.md` SC3 row | `<ID>` count | `detekt-baseline.xml` at HEAD | Yes — `grep -c` → 1040 | ✓ FLOWING |
| `26-07-SUMMARY.md` 45/11 removal split | per-entry source edits | 23 source files in the trim commits | Yes — spot-verified per category | ✓ FLOWING |

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|---|---|---|---|
| detekt green with the shrunk baseline | `./gradlew detekt --rerun-tasks` | 0 code smells, 312 kt files, 58,242 sloc | ✓ PASS |
| Baseline never appended to | `git diff -U0 ab567fb..HEAD -- detekt-baseline.xml \| grep -c '^+.*<ID>'` | `0` | ✓ PASS |
| `detekt.yml` unmodified | `git diff --stat ab567fb..HEAD -- detekt.yml` | empty | ✓ PASS |
| SC1/SC4/SC5/SC6 guard tests | `./gradlew test --tests '*ShellEscapeTest' …` | 69 tests, 0 failures | ✓ PASS |
| Full suite (one run) | `./gradlew test jacocoTestReport` | **158 classes / 1131 tests / 0 failures / 1 skip** | ✓ PASS |
| Project coverage | jacoco XML | 14567/25053 = **58.14%** line; 3881/10856 = **35.75%** branch | ✓ PASS |
| GitBook repo unmodified | `git -C ~/Tools/burp-ai-agent-doc status --porcelain` | empty; HEAD `3256cc9` (2026-06-26) | ✗ FAIL (SC6) |

### SC2 — all 14 floors, re-derived from the jacoco XML by the verifier

| # | Scope | Metric | Floor | Measured | Verdict |
|---|---|---|---|---|---|
| 1 | pkg `backends/cli` | line | ≥ 36.0% | 268/700 = 38.29% | MET |
| 2 | `CliBackend.kt` | line | ≥ 26.0% | 176/607 = 29.00% | MET |
| 3 | `mcp` tree (4 pkgs) | line | ≥ 65.0% | 2861/4026 = 71.06% | MET |
| 4 | pkg `mcp/tools` | line | ≥ 49.0% | 1254/2218 = 56.54% | MET |
| 5 | pkg `mcp/schema` | line | ≥ 70.0% | 139/142 = 97.89% | MET |
| 6 | `McpToolHelpers.kt` | line | ≥ 58.0% | 132/222 = 59.46% | MET |
| 7 | `McpToolModels.kt` | line | ≥ 50.0% | 271/315 = 86.03% | MET |
| 8 | `Serialization.kt` | line | ≥ 70.0% | 108/108 = 100.00% | MET |
| 9 | pkg `config` | line | ≥ 96.2% | 1017/1048 = 97.04% | MET |
| 10 | pkg `config` | branch | ≥ 91.0% | 561/610 = 91.97% | MET |
| 11 | pkg `redact` | line | ≥ 97.5% | 430/439 = 97.95% | MET |
| 12 | pkg `redact` | branch | ≥ 93.0% | 181/194 = 93.30% | MET |
| 13 | project | line | ≥ 57.0% | 14567/25053 = 58.14% | MET |
| 14 | project | branch | ≥ 34.8% | 3881/10856 = 35.75% | MET |

**14/14 MET, independently re-derived. `26-COVERAGE.md` is accurate, not asserted.**

### SC3 — the removal split, verified rather than taken on trust

56 removals, 0 additions, across 15 rule categories. The claimed 45-fixed / 11-stale split holds:

- **11 stale** are exactly the five categories with no corresponding source edit: `ReturnCount` ×5, `LongMethod` ×2, `TooManyFunctions` ×2, `CyclomaticComplexMethod` ×1, `MagicNumber` ×1. Three of those name files the trim never touched (`SettingsPanelActions.kt`, `MainTab.kt`, `SettingsPanelSettingsIO.kt`); the rest name symbols inside touched files that the trim did not modify.
- **45 backed by a source fix**, spot-verified per category rather than by category label:
  - `FunctionOnlyReturningConstant` ×14 — all 14 named functions are gone from `AgentSettings.kt` (`grep "fun <name>("` returns nothing for every one).
  - `UnusedPrivateProperty` / `UnusedPrivateMember` — `isTrueCondition`, `lastRequestTime`, `offColor`, `bgColor` all return 0 occurrences; `AdaptivePayloadEngine.safeHost` is gone (the 6 remaining `safeHost` hits are live symbols in three unrelated files); the three dead constants are gone from `PassiveAiScannerHeuristics.kt` while the two live copies remain.
  - `UseCheckOrError` ×6 / `UseRequire` ×4 / `ImplicitDefaultLocale` ×3 / `MayBeConst` ×1 — traced by the code reviewer per entry; the load-bearing proof is that detekt is green at 0 findings, which a removal of a still-live entry could not survive.
- **Clause 2 ("no Phase 20–25 finding was added")** is proven directly and does not depend on the summary's argument: the last commit touching the file before this phase is `ab567fb` (**2026-07-29**), and Phase 20's first commit is `5df910d` (**2026-08-06**). The file was untouched for the whole of Phases 20–25, so nothing could have been added.

### Requirements Coverage

| Requirement | Source Plans | Status | Evidence |
|---|---|---|---|
| **QUAL-06** | 26-01, 26-02, 26-03, 26-07 | ✓ SATISFIED | Allowlist `shellEscape` with direct + argv assertions (SC1); coverage risen on `redact`/`mcp`/`config` with 14/14 floors met (SC2); +251 tests (880 → 1131). Caveat: the second quoter (WR-02) is on the same trust boundary — see Warnings. |
| **QUAL-07** | 26-03, 26-04, 26-05, 26-06, 26-07 | ✓ SATISFIED (in-repo scope) | Clause 1 baseline-only-shrinks: met and now a written rule (ADR-17 cl. 1). Clause 2 EDT: documented test-only, pinned by `ChatPanelEdtGuardTest` (ADR-17 cl. 2). Clause 3 at-rest: accurate in README/SPEC/DECISIONS (ADR-17 cl. 3). **Note:** clause 3 says "user-facing docs"; the published GitBook site is user-facing and still carries the overstatement. That single gap is booked against DOC-03, which names GitBook explicitly, to avoid double-counting — but it is the same gap. |
| **DOC-03** | 26-05, 26-06 | ✗ **BLOCKED** | Its text is "`README.md`, `SPEC.md`, `DECISIONS.md` **and the GitBook site (`burp-ai-agent-docs`)** reflect the new tool-call confirmation flow and the corrected privacy claims." The advisory half (SECURITY.md) and all three in-repo docs are complete. The named GitBook site is not. **Do not tick DOC-03 until the handoff lands in `burp-ai-agent-doc` and is verified there.** |

No orphaned requirements: REQUIREMENTS.md maps exactly QUAL-06, QUAL-07 and DOC-03 to Phase 26, and all three are declared across the plans.

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|---|---|---|---|---|
| — | — | `TBD` / `FIXME` / `XXX` across every source, doc and build file the phase changed | — | **None found.** Debt-marker gate passes. |
| — | — | `@Disabled` added by this phase | — | **None.** The single skip is the pre-existing live-MCP-server test in `ExternalMcpClientManagerTest:209`. |
| — | — | Assertion-free tests | — | **None** (reviewer's AWK vacuity sweep over all changed test files; one false positive on a helper). |

### Prohibitions (must-NOTs from PLAN frontmatter)

All 14 are judgment-tier (no `verification:` field). Twelve were checkable mechanically and hold:

| Prohibition | Plan | Disposition |
|---|---|---|
| No `<ID>` added to `detekt-baseline.xml` | 26-07 | ✓ HELD — `+0` |
| `detekt.yml` unmodified | 26-07 | ✓ HELD — byte-identical |
| No test deleted / renamed / `@Disabled` / narrowed | 26-01, 26-02 | ✓ HELD — no `@Disabled` added; suite grew 880 → 1131 |
| No test executes production code without asserting | 26-01, 26-02 | ✓ HELD — vacuity sweep clean |
| Weak-token notice stays advisory | 26-03 | ✓ HELD — `isTokenWeak` is a pure `Boolean`; nothing blocks save or start |
| `MCP_MIN_TOKEN_LENGTH` ≤ generated token length | 26-03 | ✓ HELD — 32 vs. the 43-char generated token |
| No invented CVE / GHSA | 26-05 | ✓ HELD — SECURITY.md states plainly that none was issued |
| Advisory must not understate impact or omit user action | 26-05 | ✓ HELD — both entries carry impact and a concrete rotation action |
| No `.kt:NNN` citation added to `DECISIONS.md` | 26-06 | ✓ HELD — 26-06's own commits add none (the citations in the milestone-wide diff come from ADR-15, phase 22) |
| Do not paraphrase the SC4 selection | 26-06 | ✓ HELD — ADR-17 block-quotes it verbatim |
| Four call sites must not end on two mechanisms | 26-04 | ✓ HELD — all four remain on `assertEdt()`; ChatPanel has exactly one `assert(` |
| Executor must NOT choose at the blocking checkpoint | 26-04 | ⚠️ **BREACHED AT THE GATE, RATIFIED AFTER.** The harness auto-selected Option A under `mode: yolo`. ADR-17 clause 2 discloses this in full ("No human weighed the options *at the checkpoint*") and records the user's later ratification against the probe evidence. Not re-reported as a defect — the disclosure is exactly the behaviour the prohibition was protecting. Flagged so the record stands. |

### Warnings (do not fail a named criterion)

Both are unaddressed at HEAD — `a1a6e9d` merely records the review.

**⚠️ WR-02 — a second, unfixed shell quoter on the same trust boundary.** `SettingsPanel.shellQuote` (`src/main/kotlin/com/six2dez/burp/aiagent/ui/SettingsPanelActions.kt:482-486`) is the character-for-character denylist that 26-01 replaced, and it feeds `ProcessBuilder("sh", "-c", shellCmd)` at `:467`. I traced its input: `openExternalCli`'s `command` arrives from `backendConfigPanel.onOpenCli` (`SettingsPanelInit.kt:316-317`) — the configured CLI command, i.e. the *same* settings-import surface QUAL-06 names.

**Ruling on SC1:** the criterion names `shellEscape` specifically and is **met in full** — allowlist flip, both named payloads asserted directly on the helper, and the composed `sh -c` argv asserted end-to-end. SC1 is VERIFIED and I am not downgrading it.

**Plainly, on whether the goal is undermined:** partially, yes. QUAL-06's stated purpose is "closing the settings-import-to-command-execution path", and one of two quoters on that path was closed. I verified the second is **not currently exploitable** — every call site passes `shellQuote("$command; exec bash")`, and that literal suffix always contains whitespace, so the denylist branch is unreachable and the value always takes the sound single-quote branch. But the safety comes from an unrelated string concatenation, not from the quoter: drop or reorder `; exec bash` and `;`, `$`, `` ` ``, `|`, `&`, `(`, `)` all go through bare. So SC1's letter is satisfied while its *reason* is only half-served, and an auditor grepping for the pattern will find the old denylist still shipping. This is a latent defect and a real dent in the criterion's spirit — not a blocker on SC1.

**⚠️ WR-01 — a doc-accuracy defect inside the phase chartered on doc accuracy.** Independently confirmed: `handleBindFailure` (`McpSupervisor.kt:218`) still logs "Port appears busy and no compatible MCP server was detected for takeover." unchanged, yet the KDoc at `:368`, `docs/mcp-hardening.md:32` ("gets … **instead**") and ADR-17's first residual ("**instead of** being told no compatible MCP server was found") all describe the new non-loopback TLS diagnostic as a *replacement*. It is an *addition*: the operator sees both, and the misleading one is the one on the Errors tab. `docs/mcp-hardening.md` therefore contradicts itself across items 1 and 3 for this configuration. This touches no named success criterion — SC6 scopes the confirmation flow and `SecretCipher`, not the TLS diagnostic — so it is not a gap. It is worth fixing before ship because it is the same class of defect QUAL-07 exists to close, in QUAL-07's own output. The cheap fix (reword three texts from replacement to addition) closes it.

### Info

- **`mcp/external` and `mcp` (leaf) are essentially flat this phase** — `mcp/external` 60.89% → 60.89% line and 19.70% → 19.70% branch (untouched); `mcp` leaf 92.34% → 92.37%. SC2's "mcp" scope is satisfied at tree level (61.83% → 71.06%, +9.23 points, floor ≥65%), and no floor was set on `mcp/external`. Recorded so a reader does not infer the whole tree moved; `mcp/external`'s 19.70% branch coverage is the weakest remaining spot in the SC2 scope.
- Reviewer's IN-01 through IN-05 (dead `McpHelpPanel`, nine new experimental-API opt-in warnings, one Windows-vacuous test, two locale changes that are real behaviour deltas, a comment pointing at one of two surviving constant copies) are accurate and none affects a criterion.
- My filtered test run overwrote `build/reports/jacoco/test/jacocoTestReport.xml`; I re-ran the full suite with `jacocoTestReport` afterwards and the report is restored to the full-suite figures quoted above.

### Ruling on the re-based SC2 premise — satisfies the intent, does not evade it

SC2's letter measures against a project-wide 34% line / 23% branch figure from 2026-08-05. Read literally it is trivially satisfied — `redact` is at 97.95%, `config` at 97.04%, the `mcp` tree at 71.06%, all far above 34%.

The plans instead set floors against the post-Phase-25 tree at `4f0ebd7` (56.34% / 34.11%). That is **not an executor liberty**: SC2's own text delegates the number ("the exact target is set at `/gsd-discuss-phase` once the earlier phases' diffs are known"), and the ROADMAP's Phase-26 body already records the delegation being exercised, including the measured finding that `redact` and `config` were saturated before the phase began. The re-based bar is strictly harder than the one it replaced, and the movement behind it is real rather than inherited: `mcp/schema` 48.59% → 97.89%, `mcp/tools` 43.04% → 56.54%, the `mcp` tree +9.23 points, `backends/cli` 30.76% → 38.29%, and +251 tests, all concentrated on the code this milestone changed. Measuring against 34% would have credited Phase 26 with every point Phases 20–25 earned, which is the opposite of what a phase gate is for.

**Verdict: the re-basing satisfies SC2's intent and is the more honest measurement.** It would be an evasion only if a floor had been lowered to match a result; `26-COVERAGE.md` records both endpoints with their commands and commits, all 14 floors were re-measured on the merged tree, and none was adjusted. I reproduced every figure from the XML.

### Gaps Summary

One gap, and it is the one the phase itself flagged rather than one it hid.

**SC6's GitBook clause is open, and it is not a formality.** `README.md`, `SPEC.md` and `DECISIONS.md` now state `SecretCipher`'s guarantee accurately and describe the tool-call confirmation flow in detail — that half is genuinely met, committed, and pinned by `SecurityDocsTest` (21 tests) and `DecisionsAdrTest` (6 tests). But `burp-ai-agent.six2dez.com` builds from a separate repository that is byte-unmodified at a commit two months older than this phase, and it still tells users their API key "is encrypted at rest (AES-256-GCM)" with no mention that the master key sits beside the ciphertext — the exact overstatement SC6 was written to remove — while documenting nothing at all about the confirmation flow. The published documentation is therefore still the inaccurate one, which is the user-visible half of the claim.

`26-GITBOOK-HANDOFF.md` is a correct and complete prepared diff, and 26-05 was right to refuse to write outside the repo. But a prepared handoff is a plan to fix documentation, not fixed documentation, and the `checkpoint:human-action` that would have represented a human applying it was auto-confirmed by the harness under `mode: yolo`. Plans 26-05 and 26-07 and the coverage seal all record the clause OPEN; this verification agrees with them.

**Everything else holds, and holds on evidence I re-derived rather than read.** SC1, SC3, SC4 and SC5 are verified against the code and the tools, not the SUMMARYs. DOC-03 must stay unticked until the docs repo is updated; QUAL-06 and QUAL-07 are ready to tick.

The executors' self-reporting was accurate everywhere I checked it — 56-of-62 removals with four named skips, the 45/11 split, the WEAK probe, the yolo auto-selection in ADR-17, and the OPEN GitBook clause were all disclosed correctly and none was overstated.

---

_Verified: 2026-08-22_
_Verifier: Claude (gsd-verifier)_
