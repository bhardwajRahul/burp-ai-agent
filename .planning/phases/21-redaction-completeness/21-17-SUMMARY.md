---
phase: 21-redaction-completeness
plan: 17
subsystem: redact
tags: [PRIV-05, W-06, IN-01, test-vacuity, mutation-testing, equivalence-guard]

requires:
  - phase: 21-redaction-completeness (plan 12)
    provides: the first-letter-factored SENSITIVE_KEY_EXPR and the single-consumer NAIVE_KEY_EXPR_FOR_TEST guard this plan strengthens
provides:
  - "Redaction.testKeyRules() — the three shipped consumer rules (urlTokenParamRegex, formBodyParamRegex, jsonSecretKeyRegex) exposed through one seam"
  - "Redaction.naiveKeyExprForTest() — the readable expression as an internal fun, no longer a production field"
  - "an equivalence guard that compares naive vs factored across ALL THREE consumer contexts, with group counts pinned"
affects: [redaction, privacy, any future edit to SENSITIVE_WORDS or the factoring]

tech-stack:
  added: []
  patterns:
    - "Expose N shipped rules through one seam so a guard iterates them rather than sampling one"
    - "internal fun over internal const val for test-only strings — a const val compiles to a public static final field in the fat JAR"

key-files:
  modified:
    - src/main/kotlin/com/six2dez/burp/aiagent/redact/Redaction.kt
    - src/test/kotlin/com/six2dez/burp/aiagent/redact/RedactionTest.kt

requirements-completed: [PRIV-05]

metrics:
  tasks: 2
  commits: 2
  tests-total: 660
  tests-failing: 0
---

# Plan 21-17 — Summary

> **Provenance note.** Tasks 1 and 2 were executed and committed by the plan executor
> (`8096650`, `cd830c5`). The executor was then killed twice by the stream watchdog — once between
> mutations M4 and M5, and again immediately after confirming M5 and beginning its revert. **This
> SUMMARY was written by the orchestrator** after verifying the worktree state directly. Every claim
> below is either quoted from the executor's own two stall messages or independently re-verified
> against the committed tree; **M1–M3 were not recorded before the first stall and are reported as
> unrecorded rather than reconstructed.**

## What shipped

**W-06 — the equivalence guard now compares like with like across all three consumers.**
`SENSITIVE_KEY_EXPR` feeds `urlTokenParamRegex`, `formBodyParamRegex` and `jsonSecretKeyRegex`. The
guard 21-12 shipped compared one rule against the whole pipeline in **one** consumer context, so a
factoring error confined to either of the other two would not have been caught.
`Redaction.testKeyRules(): List<Triple<String, Regex, String>>` (`Redaction.kt:727`) exposes all three
shipped rules through a single seam; the guard iterates them and pins group counts.

**IN-01 — the test-only expression no longer ships as a production field.**
`internal const val NAIVE_KEY_EXPR_FOR_TEST` became `internal fun naiveKeyExprForTest()`
(`Redaction.kt:649`). The in-source rationale is recorded at `Redaction.kt:633-635`: Kotlin compiles
an `internal const val` in an object to a `public static final java.lang.String`, so a 495-character
test seam was being emitted into the shipped fat JAR.

## Mutation results

| Mutation | Outcome |
|---|---|
| M1–M3 | **Not recorded.** The executor stalled before reporting them. Not reconstructed here. |
| M4 | **Caught.** Executor, verbatim: *"M4 is caught, and the message names `formBodyParamRegex` — the consumer the old JSON-only guard never touched."* |
| M5 | **Caught.** Executor, verbatim: *"M5 confirmed — caught, naming `jsonSecretKeyRegex`, exactly the single consumer predicted."* |

M4 and M5 together are what W-06 actually needed: a factoring slip confined to a **single** consumer
is detected, and the guard names which consumer diverged. M4 in particular exercises the exact blind
spot the old guard had.

## Verification (re-run by the orchestrator against the committed tree)

- `JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test ktlintCheck detekt -q` → **exit 0**
- **660 tests, 0 failures** — the suite stayed fully green
- `git diff --stat -- detekt-baseline.xml` → empty (QUAL-07 holds)
- `grep -rn NAIVE_KEY_EXPR_FOR_TEST src/main/` → only the two explanatory comment lines; no field
- M5's mutation was reverted before the second stall; `git status --porcelain` in the worktree was
  **empty**, and `git diff` showed no uncommitted change

## Interrupt handling

M5 was found **stranded uncommitted** in the worktree after the first stall — the outer `(?:…)`
wrapper stripped from the alternation so `|` rebinds. It was recoverable with a single
`git checkout -- <path>` and nothing of value was at risk, **because both real tasks had already been
committed**. That is the mutation-hygiene rule introduced after plan 21-08's identical failure working
exactly as intended, and this plan is its second live confirmation.

## Residual

`SENSITIVE_WORDS` remains the input to a hand-factored expression. The guard now covers all three
consumers, but the ordering constraint from 21-12 still applies: **add words to `SENSITIVE_WORDS`
first, then re-factor** — and note D-21-02, which records that 21-12's factoring raised
`jsonSecretKeyRegex`'s cost by roughly half and silently moved a capability ceiling. Any future change
to how these expressions are built should be measured, not assumed free.

---
*Phase: 21-redaction-completeness*
*Completed: 2026-08-13*
