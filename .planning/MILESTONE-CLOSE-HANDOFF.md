# Handoff — `/gsd-complete-milestone` paused before any writes

**Paused:** 2026-08-31, at the pre-close artifact audit. **Nothing was written.** No archive, no
`git rm`, no ROADMAP rewrite, no tag, no acknowledgements. The tree is clean.

Resume with `/gsd-complete-milestone` in a fresh session. Two decisions are already made — carry them
forward rather than re-deriving them.

---

## Decision 1 — archive as **v1.0.0**, and do NOT create a tag

`.planning/` calls this milestone `v0.10.0`. That label is wrong for the work it contains.

| Fact | Value |
|---|---|
| Already-published git tags | …`v0.9.2`, **`v1.0.0`** |
| `build.gradle.kts` | `version = "1.0.0"` |
| `.planning/STATE.md` | `milestone: v0.10.0` |

The nine phases (20–28) are the work that went into the 1.0.0 release and the hardening after it.
**Archive to `milestones/v1.0.0-*`.**

**Skip the `git_tag` step entirely.** `v1.0.0` already exists and points at the release commit. Do not
re-tag, do not move it, and do not create `v0.10.0` — that would be a version number below an
already-published tag, on commits that came after it.

---

## Decision 2 — the pre-close audit's 31 items are mostly NOT this milestone's

`gsd-tools query audit-open` reports `total: 31`, `acknowledged: 0`. Do not take that at face value —
three distinct over-counts are folded into it:

| Category | n | What it actually is |
|---|---|---|
| `uat_gaps` | 9 | Phases **1, 2, 3, 13, 14, 15, 16, 17, 19** — every one from a **previous milestone**. v1.0.0's milestone is phases 20–28. |
| `verification_gaps` | 5 | Phases 1 and 3 (previous milestones), plus `27-VERIFICATION-2/3/4.md` — **superseded rounds**. `27-VERIFICATION-5.md` is `passed`, 30/30. |
| `deferred_items` | 16 | All from ONE file, `21/deferred-items.md`, split per bullet. Several read `**Status:** **CLOSED by plan 21-16.**` |
| `quick_tasks` | 1 | `260527-f7q`, dated 2026-05-27 — predates this milestone |

**Why this matters:** the workflow's `[A] Acknowledge all` path writes every item into STATE.md's
Deferred Items table stamped with the closing milestone. Taking it would permanently attribute nine
older phases' UAT debt, and phase 27's superseded rounds, to v1.0.0.

**Recommended:** acknowledge only items belonging to phases 20–28 and leave the rest for their own
milestones. If you take `[A]` anyway, say so in the MILESTONES.md entry — the disclosure table alone
will not make the misattribution visible to a later reader.

**The split, measured 2026-08-31 (use this, don't re-derive it):**

| Scope | n | What |
|---|---|---|
| **Phases 20–28 — acknowledge these** | **19** | 3 × phase-27 superseded verification rounds + 16 × `21/deferred-items.md` bullets |
| Phases 1–19 — leave alone | 11 | Earlier milestones' UAT and verification gaps, never archived |
| `quick_tasks` — leave alone | 1 | `260527-f7q`, dated 2026-05-27 |

So it is 19, not "close to none" — the phase-21 and phase-27 items genuinely belong to this milestone
even though both are scanner over-counts in character (superseded rounds; one file split per bullet).
Acknowledging those 19 as v1.0.0 is correct attribution.

**Cost warning for the resuming session:** the `evolve_project_full_review` step says "read all phase
summaries" — that is ~86 files at 20–60 KB each. Do NOT `cat` them. Use
`gsd_run query summary-extract <path> --fields one_liner --pick one_liner` per file, which is what the
`extract_accomplishments` step already does. A naive `cat` of that set will exhaust a fresh context and
strand the close between the archive and the `git rm`.

---

## Milestone state at pause — all verified, nothing blocking

- **9/9 phases** `passed` and `phase_complete`. Note phase 27 reads `7/9` through
  `verification.status` because that verb resolves only `{PADDED}-VERIFICATION.md`; its authoritative
  round 5 is **`passed`, 30/30**.
- **11/12 requirements.** `PRIV-05` is `- [ ]` **and that is correct** — `AR-27-08` is open and phase 28
  accepted a named residual (`D-28-09`). Three independent verifications agree. `verify_readiness` will
  flag 11/12 and demand a proceed/audit/abort choice: **proceed, recording it as a known gap.**
  Do NOT tick PRIV-05. sha256 must stay `9b3219662ec0d007c1c82d64eed3ef2698bd306ce69f01205ac9bbc3f42fcfb4`.
- **Audit:** `.planning/v0.10.0-MILESTONE-AUDIT.md`, round 2, `passed_with_warnings` — 0 integration
  blockers, 3 cross-phase warnings (CP-1/2/3). The workflow's step 0 wants `passed`; this is the
  documented "proceed with warnings" case, not a gap. **Rename this file to `v1.0.0-MILESTONE-AUDIT.md`
  if you archive as v1.0.0**, or `milestone.complete` will not find it.
- **Windows ledger:** 8 open, 46 waived, 4 fixed (triaged this session). None blocks on a human.

---

## Two live traps for whoever resumes

1. **`phase.complete` silently ticks unsatisfied requirements** — three firings this session
   (`WINDOWS.md` 54, 57, 58). Trigger isolated: it ticks a cited requirement **iff that requirement is
   currently unticked**. The `"write skipped"` warning is NOT a reliable tell — it was absent on the
   third firing. **Diff `REQUIREMENTS.md` after every `phase.complete`-family call and before its
   commit.** This workflow deletes that file, so verify its content is right before the `git rm`.

2. **The ROADMAP rewrite is guarded.** It is an intentional catastrophic shrink and `gsd-write-guard`
   blocks that shape. Arm the single-use sentinel immediately before the Write:
   `printf '.planning/ROADMAP.md\n' > .planning/.gsd-allow-shrink`. It is consumed on use and expires
   in 15 minutes. **Extract the `## Backlog` section first and re-append it after** — it is not
   regenerated.

---

_Written at the pause point so the next session starts from decisions, not from re-derivation._
