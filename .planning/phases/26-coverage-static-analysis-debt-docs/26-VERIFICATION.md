---
phase: 26-coverage-static-analysis-debt-docs
verified: 2026-08-28T00:00:00Z
status: passed
score: 6/6 must-haves verified
behavior_unverified: 0
overrides_applied: 0
re_verification:
  round: 2
  previous_status: gaps_found
  previous_score: 5/6
  previous_verified: 2026-08-22T14:58:00Z
  gaps_closed:
    - "SC6 — the GitBook half. Closed out-of-band by the maintainer's own commit d9712b3 (2026-08-22 17:57:20 +0200) in ~/Tools/burp-ai-agent-doc, ~3h after round 1 was written. All 7 handoff changes across 6 pages are present at docs HEAD c57e720, pushed (git ls-remote origin refs/heads/main = c57e720), and serving live on burp-ai-agent.six2dez.com (HTTP 200, content asserted)."
  gaps_remaining: []
  regressions: []
  regression_method: >-
    SC1/SC3/SC4 and SC6's SPEC/DECISIONS half were re-checked by content digest rather than by
    re-running the 19-minute suite: CliBackend.kt, ShellEscapeTest.kt, detekt-baseline.xml,
    detekt.yml, ChatPanel.kt, SPEC.md and DECISIONS.md are all byte-identical between round 1's
    commit a1a6e9d and current HEAD 01f4fa7 and the working tree. README.md and SECURITY.md did
    change (the 1.0.0 release commits); both were re-read and both still carry their SC5/SC6 content.
  round_1_gap_preserved: >-
    The round-1 gap was real when recorded and is preserved verbatim in the "Round 1 — the gap as
    recorded" section below. It is closed by later work, not retracted.
warnings_still_open:
  - id: WR-02
    statement: "SettingsPanel.shellQuote (SettingsPanelActions.kt:482) is the denylist quoter 26-01 replaced, still feeding ProcessBuilder(\"sh\", \"-c\", ...) at :463-466."
    status: "Unchanged since round 1 — SettingsPanelActions.kt is byte-identical (8dfd74fbac42) at a1a6e9d and HEAD. Not currently exploitable (the literal `; exec bash` suffix forces the sound branch); round 1 ruled it does not fail SC1, which names shellEscape specifically. That ruling stands. Still worth fixing before the next release."
  - id: WR-01
    statement: "docs/mcp-hardening.md describes the non-loopback TLS diagnostic as a replacement for a message the operator still also sees."
    status: "Unchanged since round 1 — docs/mcp-hardening.md (c36c2cf52378) and McpSupervisor.kt (105743cf9e61) are byte-identical at a1a6e9d and HEAD. Touches no success criterion."
human_verification: []
---

# Phase 26: Coverage, Static-Analysis Debt & Docs — Verification Report

**Phase Goal:** The milestone's code is covered by tests, the static-analysis baseline shrinks rather than grows, and users on affected versions are told.
**Verified:** round 1 — 2026-08-22 · round 2 — 2026-08-28
**Status:** passed (6/6)
**Re-verification:** Yes — round 2, re-deciding round 1's single SC6 gap against the current state of both repositories.

## Round 2 — SC6's GitBook clause, re-measured

Round 1 found the published docs repository byte-unmodified at `3256cc9` (2026-06-26), still carrying
the uncaveated at-rest claim and documenting nothing about the confirmation flow. **That snapshot went
stale about three hours after it was taken.** Nothing in this round takes round 1's description on
trust, and nothing takes `26-GITBOOK-HANDOFF.md` as a remedy — the handoff was not applied by this
verifier and this verifier made no write of any kind to the docs repository.

### The docs repository, as it stands now

| Check | Command | Result |
|---|---|---|
| Docs HEAD | `git -C ~/Tools/burp-ai-agent-doc rev-parse HEAD` | `c57e720` |
| Working tree clean | `git status --porcelain` | empty |
| Local `main` == `origin/main` | `git rev-parse main` / `origin/main` | both `c57e720`, `0 0` ahead/behind |
| **Actually published** (network read, no fetch) | `git ls-remote origin refs/heads/main` | `c57e720` — the GitHub remote really is at this commit |
| The commit that closed it | `git show --stat d9712b3` | `six2dez`, **2026-08-22 17:57:20 +0200**, *"docs: sync the site to 1.0.0 — security accuracy, advisories, stale claims"* — 13 files, +184/−29, touching **all 6** handoff pages plus a new `security/advisories.md` |

Round 1's report is timestamped `2026-08-22T14:58:00Z` = 16:58 +0200. `d9712b3` landed **59 minutes
later**, by the maintainer's own hand. The gap was true when written and false within the hour.

### The at-rest claim — all three sites corrected, and a fourth added

| Handoff item | Path:line at `c57e720` | Verified content |
|---|---|---|
| 6a | `backends/anthropic.md:12` | *"…never written to logs or exported settings — see [Encrypted key](#notes) below for what that encryption does and does not protect against."* |
| 6b | `backends/anthropic.md:21` | table cell now reads *"master key is in Burp Preferences too — see Notes"* |
| 6c | `backends/anthropic.md:30` | *"**The master key is itself stored in Burp Preferences, Base64-encoded, beside the ciphertext** (preference `secret.master.key.v1`), so this protects against casual inspection of a preferences file or an export — not against a local attacker who can read those preferences."* |
| 7a | `mcp/external-servers.md:26` | *"The AES master key is itself stored in Burp Preferences beside the ciphertext (`secret.master.key.v1`), so this protects against casual inspection of a preferences file, not against a local attacker."* |
| 5 | `privacy/limitations.md:73-86` | New `## Secrets at Rest — What the Encryption Does Not Do` section, with the mitigation paragraph and *"Treat preference-file access as equivalent to credential access."* |

**Accuracy checked against the code, not just presence.** `SecretCipher.kt:150` declares
`MASTER_KEY_PREF_KEY = "secret.master.key.v1"` — the exact preference name the docs now print;
`:118-125` reads and writes it Base64-encoded in Burp Preferences; `:17-23` confirms AES-256-GCM with
the `ENC1:` envelope. The published claim matches the mechanism.

**Sweep for survivors.** `grep -rn "encrypt" --include='*.md'` across the whole docs repo, minus the
caveated lines, leaves four hits: `privacy/limitations.md:74` (the first line of the caveat itself),
`privacy/audit-logging.md:82` (disk encryption, unrelated), `backends/anthropic.md:31` (a scope bullet
one line below the full caveat), and `backends/overview.md:38` (a comparison-table cell reading
"encrypted key", making no at-rest protection claim, and linking to the page that carries the caveat).
**No uncaveated at-rest overstatement survives anywhere in the repo.**

### The confirmation flow — from zero hits to a documented section

Round 1's finding was zero hits repo-wide for SEC-06 / `ToolApprovalGate` / "Approve for session".
Now:

| Handoff item | Path:line | Verified content |
|---|---|---|
| 1 | `README.md:20` | Auto Tool Chaining bullet rewritten — *"only read-only tools with bounded output run without asking, everything else surfaces an approval card"*, linking `mcp/security-model.md#7-tool-call-confirmation-sec-06` |
| 2 | `README.md:32` | table row *"each subject to the SEC-06 confirmation gate — silent only for read-only, bounded-output tools"* |
| 3 | `developer/data-flow.md:155-185` | Loop prose naming `ToolApprovalGate.evaluate`, plus the rewritten mermaid with a `Gate` node branching `AUTO` → `Exec` and `CONFIRM / CONFIRM_EACH` → `Card` → `Decide`, and `Deny` → `DENIAL_RESULT` |
| 4a | `mcp/security-model.md:24-27` | §3 amended — capability switch vs. SEC-06 tier are *"independent… neither is derivable from the other"* |
| 4b | `mcp/security-model.md:78-112` | New **§7 Tool-Call Confirmation (SEC-06)** — tier table (`AUTO` / `CONFIRM` with **Approve for session** / `CONFIRM_EACH`), fails-closed resolution, the read-only-is-not-sufficient rule, card-not-modal, session scope, neutral denial |
| 7b | `mcp/external-servers.md:30` | *"**Always confirmed.** …`ext:`-namespaced tool… no 'approve for session' option."* |
| — | `security/advisories.md:69` | new page, cross-links Tool-Call Confirmation |
| — | `SUMMARY.md:30` | `security/advisories.md` wired into GitBook nav (the other pages were already in nav) |

**Accuracy checked against `ToolApprovalGate.kt`, not just presence.** `:368` returns
`SecTier.CONFIRM_EACH` for any `ext:`-prefixed name *before* the catalog is consulted; `:372` returns
`descriptor?.secTier ?: SecTier.CONFIRM_EACH`, so an unknown name fails closed; `:464-465` rejects
`APPROVE_SESSION` / `DENY_SESSION` for `CONFIRM_EACH`, matching "no session memory in either
direction"; `:421-422` runs `AUTO` with no decision. **Every tier claim on the published page is true
of the shipped code.**

### Published, not merely committed — asserted against the live site

| Behavior | Command | Result | Status |
|---|---|---|---|
| §7 is live on the site | `curl -sL https://burp-ai-agent.six2dez.com/mcp-server/security-model` | HTTP **200**, 1,166,483 bytes; `Tool-Call Confirmation` ×3, `Approve for session` ×3 | ✓ PASS |
| The at-rest caveat is live | `curl -sL https://burp-ai-agent.six2dez.com/backends/anthropic` | HTTP **200**; `secret.master.key.v1` and `beside the ciphertext` both present in the served HTML | ✓ PASS |
| Remote really holds it | `git ls-remote origin refs/heads/main` | `c57e720` | ✓ PASS |

This is the check round 1 could not make and the one that actually matters: the *user-visible*
documentation now states the guarantee correctly. **SC6's GitBook clause is CLOSED.**

### Requirements — re-decided

| Requirement | Round 1 | Round 2 | Evidence |
|---|---|---|---|
| **QUAL-06** | ✓ SATISFIED | ✓ SATISFIED | Artifacts byte-identical since round 1 (see regression digests). WR-02 caveat carried forward unchanged. |
| **QUAL-07** | ✓ SATISFIED (in-repo scope) | ✓ SATISFIED (**fully**) | Round 1's only reservation was clause 3's "user-facing docs" reaching the published site. It now does. The scope qualifier is retired. |
| **DOC-03** | ✗ BLOCKED | ✓ SATISFIED | Its named GitBook site now reflects both the confirmation flow and the corrected privacy claims. |

**On the `- [x]` ticks in `REQUIREMENTS.md`** — checked, not assumed, and not edited. The file's
sha256 is `9b3219662ec0d007c1c82d64eed3ef2698bd306ce69f01205ac9bbc3f42fcfb4`, unchanged by this run.
All three of QUAL-06 / QUAL-07 / DOC-03 read `- [x]`. DOC-03's tick traces to a single commit —
`git log -S'- [x] **DOC-03**'` returns exactly `1c52525` (**2026-08-24**, *"docs(milestone): audit
v0.10.0 — 11/12 requirements, PRIV-05 gap found"*) — a deliberate human milestone audit **two days
after** `d9712b3` landed, not a `phase.complete` side-effect. That audit found and reported a
*different* gap (PRIV-05) in the same pass, which is what a real audit looks like. **DOC-03's tick is
legitimate and this round independently agrees with it.**

**One record item for a human, not a gap:** `ROADMAP.md:48` still reads `- [ ] **Phase 26**`. That is
correct bookkeeping under round 1's verdict and stale under round 2's. Ticking it is the
orchestrator's call, not this verifier's.

---

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | `shellEscape` quotes by allowlist; `foo;id` and `$(cmd)` reach `sh -c` quoted; asserted directly on the helper | ✓ VERIFIED | `CliBackend.kt:861-884` — `SHELL_SAFE_CHARS` is exactly `[a-zA-Z0-9._/-]`, tested with `arg.all { it in SHELL_SAFE_CHARS }`, else single-quote with `'` → `'"'"'`. `ShellEscapeTest` (13 tests, 0 failures, run in round 1) asserts `"'foo;id'"` and `"'$(cmd)'"` on the helper directly, plus the joined `sh -c` argv on both macOS and Linux pty shapes. **Round 2:** both files byte-identical since round 1 (`8867a0a55f7c` / `52512e931187`), so the behavioral evidence still binds. |
| 2 | Line coverage on `redact`, `mcp` and `config` rises measurably against the recorded baseline | ✓ VERIFIED | Round 1 read `build/reports/jacoco/test/jacocoTestReport.xml` directly, not the SUMMARY. All 14 floors MET; every figure in `26-COVERAGE.md` reproduced exactly. See table below. |
| 3 | detekt baseline has fewer entries than 1096, and no Phase 20–25 finding was added to it | ✓ VERIFIED | `grep -c '<ID>'` → **1040** (re-run at round 2, same figure). `git diff -U0 ab567fb..HEAD -- detekt-baseline.xml \| grep -c '^+.*<ID>'` → **0** (re-run at round 2). `detekt.yml` byte-identical to `ab567fb` (`git diff --stat` empty, re-run at round 2). Round 1 ran `./gradlew detekt --rerun-tasks`: **0 code smells** across 312 files / 58,242 sloc. |
| 4 | `assert()`-based EDT enforcement upgraded, or explicitly documented as test-only | ✓ VERIFIED (second branch) | `ChatPanel.kt:824-825` KDoc states the check *"compiles to nothing and has no production effect at all"*; `assertEdt()` at `:844`. ADR-17 clause 2 records the disposition verbatim. Zero user-facing docs claim otherwise. `ChatPanelEdtGuardTest` (6 tests) + `ChatPanelEdtConfinementTest` (23 tests) pass. **Round 2:** `ChatPanel.kt` byte-identical (`958cd31724c4`). |
| 5 | `SECURITY.md` carries an SEC-04 / PRIV-05 advisory naming affected versions, impact, fixed version | ✓ VERIFIED | `SECURITY.md:62-108`. Both entries: **"Affected:** 0.9.0, 0.9.1, 0.9.2 · **Fixed in:** 1.0.0"** (round 1 read "0.10.0 (unreleased)"; the release commits renamed the target — the criterion asks for *the* fixed version and 1.0.0 is it). Concrete impact, observed reproduction, and a user action naming token/cookie rotation. Explicitly states no CVE/GHSA was issued rather than inventing one. `:12` also warns 0.9.x carries both unfixed. |
| 6 | README/SPEC/DECISIONS **and the GitBook repo** describe the confirmation flow and state `SecretCipher`'s guarantee accurately | ✓ VERIFIED **(round 1: ✗ FAILED)** | **In-repo half:** `SPEC.md` / `DECISIONS.md` byte-identical since round 1; `README.md` was rewritten by the 1.0.0 release commits and re-read — `:245` carries the beside-the-ciphertext caveat in full, `:63` and `:246` describe the tier flow, fail-closed resolution, card-not-modal, session scope and the neutral denial. **GitBook half:** closed by `d9712b3`, at HEAD `c57e720`, pushed, and asserted live over HTTP. See Round 2 above. |

**Score:** 6/6 truths verified (0 present-but-behavior-unverified, 0 overrides)

### Round 2 regression — the untouched-artifact proof

Rather than re-run a 19-minute suite for artifacts nobody touched, content digests were compared at
round 1's commit `a1a6e9d`, at current HEAD `01f4fa7`, and in the working tree:

| Artifact | `a1a6e9d` | HEAD `01f4fa7` | worktree |
|---|---|---|---|
| `backends/cli/CliBackend.kt` | `8867a0a55f7c` | `8867a0a55f7c` | `8867a0a55f7c` |
| `test/…/ShellEscapeTest.kt` | `52512e931187` | `52512e931187` | `52512e931187` |
| `detekt-baseline.xml` | `4c1289367be9` | `4c1289367be9` | `4c1289367be9` |
| `detekt.yml` | `ad26ea5c309d` | `ad26ea5c309d` | `ad26ea5c309d` |
| `ui/ChatPanel.kt` | `958cd31724c4` | `958cd31724c4` | `958cd31724c4` |
| `SPEC.md` | `933f3e982eb5` | `933f3e982eb5` | `933f3e982eb5` |
| `DECISIONS.md` | `fbff0801fd52` | `fbff0801fd52` | `fbff0801fd52` |

Only `README.md` and `SECURITY.md` changed, via `2e33c7a` / `e7d3fc4` / `53b771a` (the 1.0.0 release).
Both were re-read in full for their SC5 / SC6 content and both still carry it. **No regressions.**

### SC6 split — round 1 vs. round 2

| Half | Round 1 | Round 2 | Evidence |
|------|---------|---------|----------|
| `README.md` | ✓ | ✓ | Rewritten by the release, re-verified: `:245` at-rest caveat naming `secret.master.key.v1`; `:63` + `:246` the confirmation flow with ADR-15 and `docs/ui-safety-guide.md` cross-links. |
| `SPEC.md` | ✓ | ✓ | Byte-identical. `:64`, `:154` at-rest with the beside-the-ciphertext caveat; `:123`, `:124`, `:153`, `:166` the SEC-06 tiers, `ToolApprovalGate.tierFor`, `CONFIRM_EACH` fail-closed, `DENIAL_RESULT`. |
| `DECISIONS.md` | ✓ | ✓ | Byte-identical. ADR-15 (confirmation flow, threat model); ADR-17 (`:239`) clause 3 (at-rest stated as protection of a preferences file or export, explicitly *not* against a local attacker). |
| GitBook `burp-ai-agent-docs` | ✗ **OPEN** | ✓ **CLOSED** | `c57e720`, clean, `ls-remote`-confirmed on the remote, 7/7 handoff items present, content asserted live over HTTP. |

### Required Artifacts

| Artifact | Expected | Status | Details |
|---|---|---|---|
| `src/main/.../backends/cli/CliBackend.kt` | allowlist `shellEscape` | ✓ VERIFIED | `internal fun shellEscape`, `when`-expression, allowlist constant; unchanged since round 1 |
| `src/test/.../ShellEscapeTest.kt` | direct + end-to-end assertions | ✓ VERIFIED | 13 tests, all pass; unchanged since round 1 |
| `detekt-baseline.xml` | < 1096 entries, removals only | ✓ VERIFIED | 1040; −56 / +0 (both re-measured at round 2) |
| `detekt.yml` | untouched | ✓ VERIFIED | `git diff --stat ab567fb..HEAD` empty |
| `SECURITY.md` | SEC-04 + PRIV-05 advisory | ✓ VERIFIED | `:62-108`, "Fixed in: 1.0.0" |
| `DECISIONS.md` | ADR-17 + ADR-16 7th residual | ✓ VERIFIED | ADR-17 at `:239` with 3 clauses + 5 consequences; ADR-16 has 7 residuals |
| `26-COVERAGE.md` | coverage seal with provenance | ✓ VERIFIED | Every number reproduces from the jacoco XML |
| `26-GITBOOK-HANDOFF.md` | prepared out-of-repo diff | ✓ **APPLIED** (round 1: ⚠️ PREPARED, NOT APPLIED) | All 7 items across 6 pages verified present at docs `c57e720`; several verbatim. Applied by the maintainer in `d9712b3`, **not** by this verifier. |
| `~/Tools/burp-ai-agent-doc` | corrected pages | ✓ **VERIFIED** (round 1: ✗ MISSING) | HEAD `c57e720`; clean; `main` == `origin/main` == remote; live site serves the corrected text |

### Data-Flow Trace (Level 4)

| Artifact | Data | Source | Real? | Status |
|---|---|---|---|---|
| `26-COVERAGE.md` per-package table | line/branch counters | `build/reports/jacoco/test/jacocoTestReport.xml` | Yes — re-derived independently in round 1, exact match | ✓ FLOWING |
| `26-COVERAGE.md` SC3 row | `<ID>` count | `detekt-baseline.xml` at HEAD | Yes — `grep -c` → 1040, re-run at round 2 | ✓ FLOWING |
| `26-07-SUMMARY.md` 45/11 removal split | per-entry source edits | 23 source files in the trim commits | Yes — spot-verified per category | ✓ FLOWING |
| GitBook `backends/anthropic.md:30` claim | `secret.master.key.v1`, Base64, beside ciphertext | `SecretCipher.kt:118-125,150` | Yes — preference name and storage mechanism match the shipped code exactly | ✓ FLOWING |
| GitBook `mcp/security-model.md` §7 tier table | `AUTO` / `CONFIRM` / `CONFIRM_EACH` semantics | `ToolApprovalGate.kt:368,372,421-422,464-465` | Yes — fail-closed, `ext:` pre-emption and no-session-memory all match | ✓ FLOWING |
| Published site | the two pages above | docs `c57e720` on `origin` | Yes — HTTP 200 with the asserted strings in the served HTML | ✓ FLOWING |

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|---|---|---|---|
| detekt green with the shrunk baseline *(round 1)* | `./gradlew detekt --rerun-tasks` | 0 code smells, 312 kt files, 58,242 sloc | ✓ PASS |
| Baseline never appended to *(re-run round 2)* | `git diff -U0 ab567fb..HEAD -- detekt-baseline.xml \| grep -c '^+.*<ID>'` | `0` | ✓ PASS |
| `detekt.yml` unmodified *(re-run round 2)* | `git diff --stat ab567fb..HEAD -- detekt.yml` | empty | ✓ PASS |
| SC1/SC4/SC5/SC6 guard tests *(round 1)* | `./gradlew test --tests '*ShellEscapeTest' …` | 69 tests, 0 failures | ✓ PASS |
| Full suite, one run *(round 1)* | `./gradlew test jacocoTestReport` | **158 classes / 1131 tests / 0 failures / 1 skip** | ✓ PASS |
| Project coverage *(round 1)* | jacoco XML | 14567/25053 = **58.14%** line; 3881/10856 = **35.75%** branch | ✓ PASS |
| GitBook repo state *(round 2)* | `git -C ~/Tools/burp-ai-agent-doc rev-parse HEAD` + `status --porcelain` | `c57e720`, clean | ✓ PASS *(round 1: ✗ FAIL)* |
| GitBook actually pushed *(round 2)* | `git ls-remote origin refs/heads/main` | `c57e720` | ✓ PASS |
| §7 live on the site *(round 2)* | `curl -sL .../mcp-server/security-model` | 200; "Tool-Call Confirmation" ×3, "Approve for session" ×3 | ✓ PASS |
| At-rest caveat live *(round 2)* | `curl -sL .../backends/anthropic` | 200; `secret.master.key.v1`, "beside the ciphertext" | ✓ PASS |

Round 2 did **not** re-run the Gradle suite: every artifact the suite would exercise is byte-identical
to round 1's verified state (digest table above), so a re-run could produce no new evidence at a
19-minute cost. `./gradlew check` remains RED for the maintainer-accepted coverage-floor reason — a
standing accepted state, not a finding of either round.

### SC2 — all 14 floors, re-derived from the jacoco XML by the round-1 verifier

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
| **QUAL-07** | 26-03, 26-04, 26-05, 26-06, 26-07 | ✓ SATISFIED | Clause 1 baseline-only-shrinks: met and now a written rule (ADR-17 cl. 1). Clause 2 EDT: documented test-only, pinned by `ChatPanelEdtGuardTest` (ADR-17 cl. 2). Clause 3 at-rest: accurate in README/SPEC/DECISIONS **and, since `d9712b3`, on the published site**. Round 1's "(in-repo scope)" qualifier is retired — clause 3 says "user-facing docs", and the user-facing docs are now correct. |
| **DOC-03** | 26-05, 26-06 | ✓ SATISFIED (round 1: ✗ BLOCKED) | Its text is "`README.md`, `SPEC.md`, `DECISIONS.md` **and the GitBook site (`burp-ai-agent-docs`)** reflect the new tool-call confirmation flow and the corrected privacy claims." The advisory half (`SECURITY.md`) and all three in-repo docs were already complete; the named GitBook site now is too, and is published. Its `- [x]` in REQUIREMENTS.md traces to the 2026-08-24 milestone audit `1c52525`, after the docs fix — legitimate. |

No orphaned requirements: REQUIREMENTS.md maps exactly QUAL-06, QUAL-07 and DOC-03 to Phase 26, and all three are declared across the plans. **REQUIREMENTS.md was not modified by this round** — sha256 `9b3219662ec0d007c1c82d64eed3ef2698bd306ce69f01205ac9bbc3f42fcfb4`, unchanged.

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|---|---|---|---|---|
| — | — | `TBD` / `FIXME` / `XXX` across every source, doc and build file the phase changed | — | **None found.** Debt-marker gate passes. Round 2 re-confirmed by digest that none of those files changed. |
| — | — | `@Disabled` added by this phase | — | **None.** The single skip is the pre-existing live-MCP-server test in `ExternalMcpClientManagerTest:209`. |
| — | — | Assertion-free tests | — | **None** (reviewer's AWK vacuity sweep over all changed test files; one false positive on a helper). |

`26-SECURITY.md` carries four amendment markers dated 28-03, 28-06, 28-08 and 28-09. Those are
**phase 28's** work filed into phase 26's directory; they are outside this phase's scope and are
neither scored nor faulted here.

### Prohibitions (must-NOTs from PLAN frontmatter)

All 14 are judgment-tier (no `verification:` field). Twelve were checkable mechanically and hold:

| Prohibition | Plan | Disposition |
|---|---|---|
| No `<ID>` added to `detekt-baseline.xml` | 26-07 | ✓ HELD — `+0` (re-measured round 2) |
| `detekt.yml` unmodified | 26-07 | ✓ HELD — byte-identical (re-measured round 2) |
| No test deleted / renamed / `@Disabled` / narrowed | 26-01, 26-02 | ✓ HELD — no `@Disabled` added; suite grew 880 → 1131 |
| No test executes production code without asserting | 26-01, 26-02 | ✓ HELD — vacuity sweep clean |
| Weak-token notice stays advisory | 26-03 | ✓ HELD — `isTokenWeak` is a pure `Boolean`; nothing blocks save or start |
| `MCP_MIN_TOKEN_LENGTH` ≤ generated token length | 26-03 | ✓ HELD — 32 vs. the 43-char generated token |
| No invented CVE / GHSA | 26-05 | ✓ HELD — SECURITY.md states plainly that none was issued |
| Advisory must not understate impact or omit user action | 26-05 | ✓ HELD — both entries carry impact and a concrete rotation action |
| No `.kt:NNN` citation added to `DECISIONS.md` | 26-06 | ✓ HELD — 26-06's own commits add none (the citations in the milestone-wide diff come from ADR-15, phase 22) |
| Do not paraphrase the SC4 selection | 26-06 | ✓ HELD — ADR-17 block-quotes it verbatim |
| Four call sites must not end on two mechanisms | 26-04 | ✓ HELD — all four remain on `assertEdt()`; ChatPanel has exactly one `assert(` |
| **26-05 must not write outside this repository** | 26-05 | ✓ HELD — and **still held in round 2**. The docs repo was closed by the maintainer's own commit `d9712b3`, authored `six2dez`, three hours after round 1. This verifier made no write, commit, stage or push to `~/Tools/burp-ai-agent-doc`; it remains clean at `c57e720`. |
| Executor must NOT choose at the blocking checkpoint | 26-04 | ⚠️ **BREACHED AT THE GATE, RATIFIED AFTER.** The harness auto-selected Option A under `mode: yolo`. ADR-17 clause 2 discloses this in full ("No human weighed the options *at the checkpoint*") and records the user's later ratification against the probe evidence. Not re-reported as a defect — the disclosure is exactly the behaviour the prohibition was protecting. Flagged so the record stands. |

### Warnings (do not fail a named criterion) — both still open at round 2

Neither was touched between round 1 and now; the files are byte-identical, so these are carried
forward unchanged rather than re-argued.

**⚠️ WR-02 — a second, unfixed shell quoter on the same trust boundary.** `SettingsPanel.shellQuote`
(`src/main/kotlin/com/six2dez/burp/aiagent/ui/SettingsPanelActions.kt:482-486`) is the
character-for-character denylist that 26-01 replaced, and it feeds `ProcessBuilder("sh", "-c", …)` at
`:463-466`. Round 2 confirms the file is byte-identical (`8dfd74fbac42`) at `a1a6e9d` and at HEAD —
**nothing has been done about it.**

**Ruling on SC1 (unchanged from round 1):** the criterion names `shellEscape` specifically and is
**met in full** — allowlist flip, both named payloads asserted directly on the helper, and the
composed `sh -c` argv asserted end-to-end. SC1 is VERIFIED and round 2 does not downgrade it.

**Plainly, on whether the goal is undermined:** partially, yes. QUAL-06's stated purpose is "closing
the settings-import-to-command-execution path", and one of two quoters on that path was closed. The
second is **not currently exploitable** — every call site passes `shellQuote("$command; exec bash")`,
and that literal suffix always contains whitespace, so the denylist branch is unreachable and the
value always takes the sound single-quote branch. But the safety comes from an unrelated string
concatenation, not from the quoter: drop or reorder `; exec bash` and `;`, `$`, `` ` ``, `|`, `&`,
`(`, `)` all go through bare. So SC1's letter is satisfied while its *reason* is only half-served, and
an auditor grepping for the pattern will find the old denylist still shipping — **and it shipped in
1.0.0.** A latent defect and a real dent in the criterion's spirit; not a blocker on SC1, and worth a
follow-up issue rather than a re-open of this phase.

**⚠️ WR-01 — a doc-accuracy defect inside the phase chartered on doc accuracy.** `handleBindFailure`
(`McpSupervisor.kt:218`) still logs "Port appears busy and no compatible MCP server was detected for
takeover." unchanged, yet the KDoc at `:368`, `docs/mcp-hardening.md` item 3 ("gets … **instead**")
and ADR-17's first residual all describe the new non-loopback TLS diagnostic as a *replacement*. It is
an *addition*. Round 2 confirms both files byte-identical (`105743cf9e61`, `c36c2cf52378`) — unfixed.
Touches no named success criterion, so not a gap.

### Info

- **`mcp/external` and `mcp` (leaf) are essentially flat this phase** — `mcp/external` 60.89% → 60.89% line and 19.70% → 19.70% branch (untouched); `mcp` leaf 92.34% → 92.37%. SC2's "mcp" scope is satisfied at tree level (61.83% → 71.06%, +9.23 points, floor ≥65%), and no floor was set on `mcp/external`. Recorded so a reader does not infer the whole tree moved; `mcp/external`'s 19.70% branch coverage is the weakest remaining spot in the SC2 scope.
- Reviewer's IN-01 through IN-05 (dead `McpHelpPanel`, nine new experimental-API opt-in warnings, one Windows-vacuous test, two locale changes that are real behaviour deltas, a comment pointing at one of two surviving constant copies) are accurate and none affects a criterion.
- `backends/overview.md:38` on the docs site reads "encrypted key" in a comparison-table cell with no at-rest protection claim. Not the SC6 overstatement, and the page it links carries the full caveat. Recorded, not faulted.
- `ROADMAP.md:48` still shows Phase 26 as `- [ ]`, correct under round 1 and stale under round 2. Orchestrator's call.

### Ruling on the re-based SC2 premise — satisfies the intent, does not evade it

SC2's letter measures against a project-wide 34% line / 23% branch figure from 2026-08-05. Read
literally it is trivially satisfied — `redact` is at 97.95%, `config` at 97.04%, the `mcp` tree at
71.06%, all far above 34%.

The plans instead set floors against the post-Phase-25 tree at `4f0ebd7` (56.34% / 34.11%). That is
**not an executor liberty**: SC2's own text delegates the number ("the exact target is set at
`/gsd-discuss-phase` once the earlier phases' diffs are known"), and the ROADMAP's Phase-26 body
already records the delegation being exercised, including the measured finding that `redact` and
`config` were saturated before the phase began. The re-based bar is strictly harder than the one it
replaced, and the movement behind it is real rather than inherited: `mcp/schema` 48.59% → 97.89%,
`mcp/tools` 43.04% → 56.54%, the `mcp` tree +9.23 points, `backends/cli` 30.76% → 38.29%, and +251
tests, all concentrated on the code this milestone changed. Measuring against 34% would have credited
Phase 26 with every point Phases 20–25 earned, which is the opposite of what a phase gate is for.

**Verdict: the re-basing satisfies SC2's intent and is the more honest measurement.** It would be an
evasion only if a floor had been lowered to match a result; `26-COVERAGE.md` records both endpoints
with their commands and commits, all 14 floors were re-measured on the merged tree, and none was
adjusted. Round 1 reproduced every figure from the XML.

---

## Round 1 — the gap as recorded (historical, preserved)

*Preserved verbatim in substance. This finding was true when written on 2026-08-22T14:58:00Z. It was
closed by later work, not retracted, and the record should show it that way.*

> **SC6's GitBook clause is open, and it is not a formality.** `README.md`, `SPEC.md` and
> `DECISIONS.md` now state `SecretCipher`'s guarantee accurately and describe the tool-call
> confirmation flow in detail — that half is genuinely met, committed, and pinned by
> `SecurityDocsTest` (21 tests) and `DecisionsAdrTest` (6 tests). But `burp-ai-agent.six2dez.com`
> builds from a separate repository that is byte-unmodified at a commit two months older than this
> phase, and it still tells users their API key "is encrypted at rest (AES-256-GCM)" with no mention
> that the master key sits beside the ciphertext — the exact overstatement SC6 was written to remove —
> while documenting nothing at all about the confirmation flow. The published documentation is
> therefore still the inaccurate one, which is the user-visible half of the claim.
>
> `26-GITBOOK-HANDOFF.md` is a correct and complete prepared diff, and 26-05 was right to refuse to
> write outside the repo. But a prepared handoff is a plan to fix documentation, not fixed
> documentation, and the `checkpoint:human-action` that would have represented a human applying it
> was auto-confirmed by the harness under `mode: yolo`. Plans 26-05 and 26-07 and the coverage seal
> all record the clause OPEN; this verification agrees with them.

Round 1's four cited artifacts, and their state now:

| Round-1 citation | Round-1 finding | Round-2 state at `c57e720` |
|---|---|---|
| `backends/anthropic.md:12` | uncaveated at-rest sentence | ✓ now ends "see [Encrypted key](#notes) below for what that encryption does and does not protect against" |
| `backends/anthropic.md:30` | mechanism stated, storage omitted | ✓ now states the master key is stored Base64-encoded **beside the ciphertext** as `secret.master.key.v1`, and what that does not protect against |
| `mcp/external-servers.md:26` | same uncaveated claim | ✓ now carries the equivalent caveat naming `secret.master.key.v1` |
| whole repo — zero hits for SEC-06 / "Approve for session" / `ToolApprovalGate` | flow undocumented | ✓ now in `README.md:20,32`, `developer/data-flow.md:155-185`, `mcp/security-model.md:24,78-112`, `mcp/external-servers.md:30`, `security/advisories.md:69` |

Round 1's three "missing" actions are all discharged: the handoff was applied (`d9712b3`), the repo
was committed and pushed (`c57e720` on `origin`, live over HTTP), and DOC-03 was ticked afterwards by
the 2026-08-24 milestone audit `1c52525`.

---

## Verdict

**6/6. The phase goal is achieved.**

The milestone's code is covered by tests against 14 re-derived floors, the detekt baseline shrank by
56 entries with zero additions and `detekt.yml` untouched, and users on 0.9.0–0.9.2 are told — in
`SECURITY.md`, and now on the published site, which also finally describes the tool-call confirmation
gate and stops overstating what `SecretCipher` protects.

The one gap round 1 found was real, was the *user-visible* half of a correctness claim, and is now
closed — by the maintainer, in the docs repository, an hour after round 1 was written, and verified
here against the remote and against the live HTML rather than against anyone's say-so. Round 2 made no
write to that repository and none to `REQUIREMENTS.md`.

Two warnings ride forward unfixed and unchanged, and neither fails a criterion: **WR-02**, the old
denylist quoter still shipping on the same trust boundary QUAL-06 exists to close, is the one that
deserves a follow-up issue.

---

_Round 1 verified: 2026-08-22 · Round 2 verified: 2026-08-28_
_Verifier: Claude (gsd-verifier)_
