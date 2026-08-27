---
phase: 27
round: 5
verified: 2026-08-26T20:05:00Z
status: passed
score: 30/30 must-haves verified (re-verified 2026-08-26; the two gaps below are CLOSED)
re_verified: 2026-08-26T21:05:00Z
uat_closed: 2026-08-27
behavior_unverified: 0
overrides_applied: 0
git_range: c2d980f..fb7cbd3
verified_against: HEAD (fb7cbd3), working tree clean of tracked modifications
re_verification:
  previous_status: gaps_found
  previous_score: 29/33
  gaps_closed:
    - "Round-4 gap 1 — the bare-quote logical-line start. CLOSED at the control. `JSON_STRING_OPEN = \":\\\"\"` measured on freshly compiled classes: 4 of the 5 non-JSON carriers V-4 quoted are now BYTE-IDENTICAL under STRICT and BALANCED, the 5th changes only through the unrelated `bearerRegex` (disclosed by 27-14-SUMMARY, with a clean substitute shape 5b that I measured byte-identical), and the content-destruction payload lost 0 characters and kept all 40 content markers."
    - "Round-4 gap 2 — `FUNCTION_DECLARATION` declaration-shape blindness. CLOSED. Independently re-measured on the pre-27-15 tree (5f5aeab): 151 files, 1784 declaration-ish lines, 136 invisible to the old regex, 67 backtick-named across 9 files, 61 `override` — the reviewer's numbers reproduce exactly. The widened regex leaves exactly 3 invisible (the extension receivers), which is what the source now says."
    - "Round-4 gap 3 — the blind-axis enumeration. CLOSED. THIRTEEN axes, machine-checked by `theStatedBlindAxisCountMatchesTheEnumeration` (mutation-proved: setting the constant to 12 takes it RED). Axis 9 is the declaration-shape price of the widening, axis 10 the compound-assertion negation over-fire. The walk-to-detector composition and the loud unbalanced-file failure both exist and are mutation-proved non-vacuous."
    - "Round-4 gap 4 — the residual list naming only what was INHERITED. CLOSED. `ROADMAP.md` now carries an explicit INTRODUCED-versus-INHERITED split, standing-rule clause (vii) exists, and round 5 applies it to itself — including recording that entry (1) of its own INTRODUCED list was wrong."
  gaps_remaining: []
  regressions:
    - "NEW, INTRODUCED BY THE LAST COMMIT OF THIS ROUND (fb7cbd3): standing-rule clause (vi) of `26-SECURITY.md` now states two counts that its own control falsifies — `15 tests` (measured: 16) and `returns 14` unskipped self-hits (measured: 15). fb7cbd3 changed the control and touched no record."
    - "NEW, INTRODUCED BY 2ed1a12: the AR-27-11 severity/bound correction was applied to `26-SECURITY.md`, `Redaction.kt` and the `THIRD_OPEN_FINDING` KDoc, but NOT to `.planning/ROADMAP.md` entry (1) nor to `27-HUMAN-UAT.md` item 12 — the artifact the register itself names as the owner's decision venue. Both still read LOW / one family."
gaps:
  - truth: "27-15: 'BOUND, carried with the claim, restated at BOTH places it is made. The sweep's KDoc enumeration is amended in the SAME change as the gate it describes, and `26-SECURITY.md` standing-rule clause (vi) — which cites that enumeration as the check''s stated bound — is amended in the same change too. Before this plan the register carried a claim wider than its control, which is verbatim the failure clause (vi) exists to prevent.'"
    status: failed
    reason: >-
      TRUE when plan 27-15 landed. FALSE at HEAD, and falsified by round 5's own final commit.
      Standing-rule clause (vi) describes its control in the present tense with two numbers:
      "`RedactingPolicySurvivalSweepTest` (plan 27-12, extended by plan 27-15, **15 tests**)"
      (`26-SECURITY.md:1220`) and "the same detector over the same file without the raw-string skip
      returns **14**" (`:1223`). Both were correct at the end of 27-15 and are recorded as such in
      `27-15-SUMMARY.md:290-291`.
      MEASURED BY ME AT HEAD, not read from any summary:
        `grep -c "^    @Test" RedactingPolicySurvivalSweepTest.kt` -> 16
        JUnit XML for the class                                   -> tests="16" failures="0"
        `detect(SELF_PATH, sourceFile(SELF_PATH).readLines()).size`, obtained by temporarily
        raising the floor in `theRawStringSkipIsWhyTheSelfScanIsClean` inside a throwaway
        worktree and reading the assertion message: "found 15"
      The cause is `fb7cbd3` (the out-of-plan WR-02 fix). It added a 16th `@Test`
      (`aTrailingCommentMentioningATripleQuoteDoesNotToggleTheRawStringState`) and a new
      raw-string fixture (`TRAILING_COMMENT_WALK_FIXTURE`), which is one more unskipped self-hit.
      `git show --name-only fb7cbd3` lists exactly ONE file — the test file. No record was amended
      in the same change.
      WHY THIS IS A GAP AND NOT A NIT. The clause these two numbers sit in is the clause that
      prohibits a stated bound diverging from its control, and whose own worked example
      ("Until 2026-08-26 this paragraph read ELEVEN ... Both were false when written") is that exact
      defect. Round 5 built a machine check for the ONE number in that clause that had gone stale
      before (`STATED_BLIND_AXES`, mutation-proved RED at 12) and left the two adjacent numbers
      unchecked; the round's last commit then staled both. fb7cbd3's own message reasons about the
      machine-checked number only — "the thirteen-axis enumeration is unchanged because this is a
      fix, not a deferral" — and never asks the same question of the two that are not.
      A LESSER INSTANCE, same cause, recorded rather than smoothed: `26-SECURITY.md:1318` says the
      blank-everything ablation left "the other **13** still green", a 14-test class; and `:1323`
      states "**652** lines are blanked tree-wide (352 ... 300 ...)". My own instrumented count at
      HEAD is 659 (361 self / 298 elsewhere). The 13 is framed as a historical run so it ages
      legitimately; the 652 may differ from mine by counting definition, so I do not treat either as
      load-bearing. The 15 and the 14 are unambiguous.
    artifacts:
      - path: ".planning/phases/26-coverage-static-analysis-debt-docs/26-SECURITY.md:1220"
        issue: "`extended by plan 27-15, **15 tests**` — the class has 16 `@Test` methods and JUnit runs 16."
      - path: ".planning/phases/26-coverage-static-analysis-debt-docs/26-SECURITY.md:1223"
        issue: "`the same detector over the same file without the raw-string skip returns **14**` — measured at HEAD it returns 15."
      - path: "src/test/kotlin/com/six2dez/burp/aiagent/redact/RedactingPolicySurvivalSweepTest.kt"
        issue: "`MIN_EXPECTED_UNSKIPPED_SELF_HITS = 2` is a floor, so the unskipped self-hit count the register quotes is not machine-checked; neither is the test count. The one number in clause (vi) that IS machine-checked (THIRTEEN) is the one that did not drift."
    missing:
      - "Amend clause (vi)'s `15 tests` to `16 tests` and its `returns 14` to `returns 15`, recording that the movement is fb7cbd3's WR-02 test and its fixture — appended, per the standing rules, not rewritten."
      - "Or (preferred, and the same remedy round 5 already applied to the axis count) machine-check both: a test that counts `@Test` methods in the sweep file and one that pins the unskipped self-hit count exactly rather than by floor, so a future control change turns a test red before it turns a register sentence stale."
      - "Either way, record in `WINDOWS.md` that round 5's final commit falsified two counts in the clause that prohibits exactly that, since the round's own residual list depends on which gates a change happened to run."
  - truth: "27-16: 'AR-27-11 is defined for the first time — a header at the open of a JSON ARRAY ELEMENT string is not a recognised logical-line start after the narrowing — with its severity assigned from a measurement taken this round, its reachability stated as measured or as unmeasured rather than assumed, and its owner named.'"
    status: failed
    reason: >-
      The REGISTER half is exemplary and I verified it independently. The OWNER half is stale, and
      the owner half is the half that has a consequence.
      WHAT IS RIGHT. `26-SECURITY.md`'s AR-27-11 row carries the CR-01 correction: the mechanism is
      stated as the general rule (`:"` is colon-then-quote literally), FOUR families follow, severity
      is RAISED LOW -> MEDIUM, and the re-derivation is explicit that the original LOW rested on the
      wrong question. I re-measured all four families end to end against freshly compiled classes,
      STRICT and BALANCED, with compact-shaped controls stripping in the same run, and isolating
      `authHeaderRegex` from `bearerRegex` with a low-entropy `X-Api-Key` / `X-Csrf-Token` value so
      the auth family is a statement about the composer and not about token redaction. All four
      families are genuinely lost, by all three composed rules, and the "only the FIRST CONTENT of
      its string escapes" bound holds in all four. The row's byte-exact-prefix discipline holds
      (`26-SECURITY.md` AR-27-11: earlier text preserved verbatim behind an explicit CORRECTION
      marker) and `threats_open` recomputes to 0 under the documented awk (46 rows, 46 closed).
      WHAT IS WRONG. `2ed1a12` amended `26-SECURITY.md`, `Redaction.kt` and the `THIRD_OPEN_FINDING`
      KDoc. It did not amend `.planning/ROADMAP.md` or `27-HUMAN-UAT.md`
      (`git show --name-only 2ed1a12` lists three files, neither of them these).
      `ROADMAP.md:627` still reads: "**(1) `AR-27-11`** — the JSON-ARRAY-ELEMENT logical-line
      start ... **OPEN at LOW** ... Bounded `low` because a realistic raw HTTP message inside an
      array element is STILL stripped". No correction marker, no forward pointer.
      `27-HUMAN-UAT.md` item 12 — the artifact the register itself names as the venue
      ("OWNER: unchanged — the maintainer, item 12 of `27-HUMAN-UAT.md`") — is worse than stale,
      because it is a DECISION document and it presents the superseded reasoning as the case for
      the decision:
        "Filed as `AR-27-11`, OPEN at LOW."
        "**Exactly one carrier can emit an arbitrary JSON array of strings through `Redaction.apply`**"
        "*For:* no emission field this repository owns is a JSON array of strings"
        "**OPTION A — ACCEPT at LOW.**"
      That `List<String>`-fields enumeration is precisely the question the same round measured to be
      the wrong one. The register's own correction says so in as many words: "The enumeration above
      concluded ZERO carriers on this repository's own emission schema because it looked for
      `List<String>` FIELDS — and the carrier is not a field. It is the CONTENT of the `response`
      string, which this repository copies verbatim from the target."
      AND THE OPTION IS WRONG, NOT ONLY THE SEVERITY. Item 12's OPTION B reads "WIDEN the boundary to
      recognise the array-element open. Add `[\"` and `,\"` as two further FIXED-WIDTH lookbehind
      alternatives", described as closing "the residual at the control rather than in a record".
      Under the corrected four-family mechanism that widening closes family 4 only: family 1
      (nested/escaped, `\"k\":\"`) and family 2 (pretty-printed, `": "`) interpose a backslash or a
      space and would still not be recognised. So the maintainer is being offered a binary between
      "accept at LOW" and a fix that measurably closes one of four families — on a finding the
      register rates MEDIUM and describes as reachable in the DEFAULT posture with no opt-in
      precondition.
      This is the fifth round of this phase, and the failure mode is the same one every time, one
      level smaller: the measurement was taken and was right, and an artifact downstream of it was
      not brought along.
    artifacts:
      - path: ".planning/phases/27-priv-05-gap-closure-sanitize-headers/27-HUMAN-UAT.md:558-611"
        issue: "Item 12 states AR-27-11 at LOW over one family, argues Option A from the refuted `List<String>`-fields reachability, and offers an Option B that closes 1 of the 4 measured families. It is the owner's decision artifact and the register points to it by name."
      - path: ".planning/ROADMAP.md:627-641"
        issue: "Round-5 INTRODUCED residual entry (1) still reads `OPEN at LOW` with the array-only mechanism and the array-only reachability. The correction exists only as prose inside `26-SECURITY.md` (`Entry (1) is therefore amended to read: ...`), with no marker in ROADMAP itself."
      - path: "src/test/kotlin/com/six2dez/burp/aiagent/redact/LogicalLineBoundaryScopeTest.kt:157,233"
        issue: "Two assertion FAILURE MESSAGES still describe AR-27-11 as `the JSON-ARRAY-ELEMENT string open` and as `a header at the open of a JSON ARRAY ELEMENT string ([\"Cookie: …\"]) ... That residual is the whole trade`. The corrected four-family account is 150 lines away in the same file's `THIRD_OPEN_FINDING` KDoc. A failure message is where a reader meets the rule, which is this file's own stated discipline."
    missing:
      - "Amend `27-HUMAN-UAT.md` item 12 to MEDIUM over four families, replace the `List<String>`-fields `For:` argument with the corrected emission-shape reachability, and restate Option B honestly — either as a four-lookbehind widening (`[\\\"`, `,\\\"`, and a spelling that covers the escaped and whitespace opens) or as an explicitly partial fix. Append; do not rewrite."
      - "Add a dated correction marker to `ROADMAP.md` entry (1) pointing at the `26-SECURITY.md` correction, so a ROADMAP reader is not left with the superseded LOW."
      - "Update the two assertion failure messages in `LogicalLineBoundaryScopeTest.kt` to name the mechanism (a colon-quote sequence, so any interposed character or absent colon) rather than one family."
      - "Consider a standing-rule clause (viii), since this is clause (vii)'s failure at one more remove: when a finding's severity or bound is corrected, every artifact that CITES that finding must be amended in the same change — and the register already knows which they are, because it names them."
deferred:
  - truth: "AR-27-08 and `InjectionPointExtractor.kt:29`"
    addressed_in: "Phase 28"
    evidence: "ROADMAP Phase 28 goal names both. Verified untouched by round 5: only `Redaction.kt` changed under `src/main/kotlin` (+93/-5, one functional line)."
  - truth: "AR-27-04 — `Host:` / `SiteMapEntry.url` un-anonymised under STRICT, OPEN at MEDIUM, still owed a human decision"
    addressed_in: "26-SECURITY.md AR-27-04 (open) + 27-HUMAN-UAT.md item 9"
    evidence: "Register row is BYTE-IDENTICAL across c2d980f..HEAD (sha256 6106f921...). Deliberately not relitigated, per every round-5 plan."
  - truth: "`./gradlew check` fails `jacocoTestCoverageVerification` — redact BRANCH 0.9278 vs a 0.930 floor"
    addressed_in: "WINDOWS.md #47 + ROADMAP round-5 INTRODUCED entry (4), maintainer-accepted"
    evidence: "Measured, bisected to the single `if (remainingMs <= 0L)` branch, and explicitly not laundered. Out of scope for this verification by instruction."
  - truth: "27-REVIEW-3 WR-01, WR-03 and IN-01..IN-07"
    addressed_in: "27-REVIEW-3.md (open by choice)"
    evidence: "Only CR-01 and WR-02 were authorised for out-of-plan fix. I reproduced WR-01 verbatim (`var o = {note:\"cookie: analytics\", ...}` -> `note:\"cookie: [STRIPPED]\"`; `a::before{content:\"cookie: analytics\"}` -> `content:\"cookie: [STRIPPED]\"`) and it is recorded in the AR-27-11 row as an explicit non-remedy."
behavior_unverified_items: []
coincidental_reliance_items: []
human_verification:
  - test: "Re-decide AR-27-11 from the CORRECTED record. Read the `AR-27-11` row in `26-SECURITY.md` (MEDIUM, four families, emission-shape reachability), NOT `27-HUMAN-UAT.md` item 12, which is stale at LOW over one family and whose Option B closes one family of four."
    expected: "A recorded disposition taken against a four-family MEDIUM finding reachable in the default posture with no opt-in precondition, with Option B restated at its true cost before it is weighed."
    why_human: "A release-posture privacy trade on a shipped 1.0.0, and the alternative is a widening this phase has twice measured over-firing. The decision artifact currently misstates both the severity and the fix."
  - test: "CARRIED FORWARD, unanswered: all round-1..round-4 items of `27-HUMAN-UAT.md` (items 1-11) plus item 12a (was the round-5 narrowing a maintainer's choice or a `mode: yolo` default)."
    expected: "As stated per item. `27-HUMAN-UAT.md` records them as `[pending]` and must stay legible as such."
    why_human: "Live Burp, real proxy traffic, a real MCP client, and maintainer risk decisions. `.planning/config.json` still carries `\"mode\": \"yolo\"`, so the provenance contrast with AR-27-04 rests on an assertion no artifact corroborates — which round 5 says plainly."
  - test: "DECIDE the `jacocoTestCoverageVerification` shortfall: a deterministic test for the `Redaction.kt` wall-clock budget-exhaustion branch, or an accepted red gate."
    expected: "A recorded decision. Lowering the QUAL-06 floor is the laundering this phase exists to prohibit and round 5 says so."
    why_human: "The gate is partly met by a timing race; three samples do not establish the cause, and round 5 correctly declines to claim one."
---

# Phase 27 Round 5 — Verification Report

**Phase Goal (round 5):** Close the four gaps `27-VERIFICATION-4.md` found, without closing PRIV-05.
**Verified:** 2026-08-26T20:05:00Z
**Range:** `c2d980f..fb7cbd3` (plans 27-14, 27-15, 27-16 plus the out-of-plan CR-01 / WR-02 fixes)
**Status:** `gaps_found`
**Re-verification:** Yes — fifth round.

## How this was verified

Nothing below is taken from a SUMMARY. Every measurement was re-taken:

- **Redaction behaviour** — `./gradlew compileKotlin` at HEAD, then four standalone JDK 21 probes
  compiled against `build/classes/kotlin/main` calling
  `Redaction.INSTANCE.apply(blob, RedactionPolicy.Companion.fromMode(mode), salt, false)` directly,
  STRICT and BALANCED, 30 fixtures.
- **Sweep detector arithmetic** — an independent Python re-implementation of the old and new
  `FUNCTION_DECLARATION` regexes run over the pre-27-15 tree via a throwaway `git worktree` at
  `5f5aeab`.
- **Gate non-vacuity** — five source mutations applied in a throwaway worktree at HEAD, each run
  through `./gradlew test`, to establish that the round's new gates go RED when the fix they
  describe is reverted.
- **Register arithmetic** — the documented `awk` command, `sha256` prefix comparison of register
  rows across `c2d980f..HEAD`, and `git show --name-only` on the two out-of-plan commits.
- **Suite** — `ktlintCheck detekt` BUILD SUCCESSFUL; `test` BUILD SUCCESSFUL, 175 test classes,
  1246 tests, 1 skipped, **0 failures, 0 errors**. Working tree carries no tracked modifications.

## Goal Achievement

### Observable Truths

#### Plan 27-14 — narrow the third logical-line start (8 truths)

| # | Truth | Status | Evidence I gathered |
|---|-------|--------|---------------------|
| 1 | Bare-quote carriers left BYTE-IDENTICAL; the 1589-of-1714 destruction gone | VERIFIED (deviation disclosed) | HTML attribute, JS string arg, quoted CSV and `Set-Cookie` prose: all four `UNCHANGED` under STRICT and BALANCED. Shape 5 (`"authorization: Bearer required"`) is NOT byte-identical — `bearerRegex` turns `required` into `[REDACTED]` — but the composer's over-match is gone (V-4 measured the whole tail replaced; now only the token after `Bearer`). `27-14-SUMMARY.md:139,263-264` calls the plan's expectation wrong and substitutes shape 5b; I measured 5b (`"authorization: required"`) byte-identical in both modes. A destruction payload with 40 markers: `destroyed=0, markers 40 -> 40, identical=true`. |
| 2 | The two `notes` carriers stay CLOSED; escaped-newline control fires | VERIFIED | `{"notes":"Cookie: a=S"}` and `[{"notes":"Cookie: a=S"}]` both -> `Cookie: [STRIPPED]` in STRICT and BALANCED; `\r\n`-carrier control strips in the same run. |
| 3 | Start stays FIXED-WIDTH at two characters; no new backtracking surface | VERIFIED | `JSON_STRING_OPEN = ":\""`; both characters regex-literal; `EXPECTED_JSON_STRING_OPEN_WIDTH = 2` pinned by a source-read test that decodes the literal. Value tail untouched in the diff. |
| 4 | The cost of the start that was ADDED is stated where a reader meets the rule | VERIFIED | `Redaction.kt` rationale paragraph (a) quotes the five carriers and the 1589-of-1714 measurement; the `JSON_STRING_OPEN` KDoc repeats it with the mechanism ("an escaped quote is consumed atomically ... so the tail cannot terminate on ANY escaped quote"). |
| 5 | The narrowing's own bound named, filed as AR-27-11, pinned by no test | VERIFIED (as corrected) | The bound as the PLAN stated it (array element only) is true but was incomplete; that incompleteness is `27-REVIEW-3` CR-01 and is now corrected in register and source — see the out-of-plan section. No test asserts a cookie value surviving; the sweep confirms 0 such pins tree-wide. |
| 6 | The gate that MISSED it is repaired, not supplemented | VERIFIED | `anHtmlAttributePayloadIsLeftByteIdenticalUnderBothRedactingModes` (three fixture guards + a same-context non-vacuity control + `assertEquals` byte identity) and `contentAfterTheCookieValueInTheSameJsonStringIsMeasuredNotAssumed` (fixture guard asserts the value is NOT last in its string). Both green. |
| 7 | The narrowed value cannot be silently re-widened | VERIFIED (mutation-proved) | Reverting the constant to `"\""` in a throwaway worktree took **2 tests RED**: `theJsonStringOpenIsAValueOpenAndNotABareQuote` and `anHtmlAttributePayloadIsLeftByteIdenticalUnderBothRedactingModes`. |
| 8 | Closes no requirement | VERIFIED | `REQUIREMENTS.md` sha256 identical at `c2d980f` and HEAD (`9b321966…`); `:23` still `- [ ] **PRIV-05**`. |

#### Plan 27-15 — the sweep's stated bound (9 truths)

| # | Truth | Status | Evidence I gathered |
|---|-------|--------|---------------------|
| 1 | Backtick / annotated / modified declarations are FLAGGED; 1-of-6 -> 6-of-6 | VERIFIED | Independently re-measured on the pre-27-15 tree: **151 files, 1784 declaration lines, 136 invisible to the old regex, 67 backtick-named across 9 files, 61 `override`** — the reviewer's numbers exactly. New regex leaves **3** invisible (the extension receivers), matching what the source now claims. `everyDeclarationShapeInUseInThisRepositoryIsVisibleToTheSweep` goes RED when the old regex is restored. |
| 2 | The "fails on the next such pin" claim and the gate describe the same population | VERIFIED | The KDoc sentence is scoped in place ("NOT written with an opening parenthesis somewhere other than its declaration line; that shape is blind axis 9") rather than deleted. |
| 3 | The walk->detector composition is gated in the FLAGGING direction | VERIFIED | `theWalkPreservesRealCodeWhileSkippingRawStringInteriors` asserts exactly one hit and that it is the real-code half. (27-REVIEW-3 IN-06 notes it calls `dropRawStringInteriors` rather than `fileWalk`; `fileWalk` is a one-line delegation to it. Info, left open by choice.) |
| 4 | The silent-blindness mode is converted into a loud one | VERIFIED | The `AssertionError` exists and names the source. I triggered it twice by mutation. `0 of 151 files end INSIDE` is now established by the tree scan itself. The 625-vs-652 blanked-line divergence is recorded as **NOT reconciled** rather than explained away — exactly the discipline truth 8 asks for. |
| 5 | **The bound is restated at BOTH places, in the SAME change** | **FAILED** | True at 27-15. Falsified at HEAD by `fb7cbd3`. See gap 1. |
| 6 | The stated axis count is MACHINE-CHECKED against its enumeration | VERIFIED (mutation-proved) | `STATED_BLIND_AXES = 13`; changing it to 12 takes `theStatedBlindAxisCountMatchesTheEnumeration` RED. |
| 7 | THE NEXT BLIND AXES, NAMED | VERIFIED | Axis 9 (declaration line without its opening parenthesis) names BOTH shapes with both counts — the multi-line signature the plan anticipated (0 today) and the extension receiver it did not (3, one in the sweep file itself). Axis 10 is the compound-assertion negation over-fire, with the fix written down and deliberately unapplied. |
| 8 | Every count RE-MEASURED after 27-14, divergences recorded beside the observed number | VERIFIED | 7 benign accessors re-measured and stated as measured-here-not-inherited; `133 of 1781` stated beside `136 of 1784` with the 3-line difference explained; `652` stated beside `625` as unreconciled; `WINDOWS.md` #41 records the `136 of 1779` premise as falsified. |
| 9 | Closes no requirement | VERIFIED | As above. |

#### Plan 27-16 — the records, fifth time (8 truths)

| # | Truth | Status | Evidence I gathered |
|---|-------|--------|---------------------|
| 1 | Clause (7) added; clauses (1)-(6) survive as an EXACT CHARACTER PREFIX | VERIFIED (byte-checked) | Old `T-26-02-01` row: 29499 chars. New: 38386. Common prefix: **29489** — the entire prose cell. Old's remaining 10 chars are the trailing `\| closed \|` cells, which the new row also ends with. |
| 2 | Both residuals ROUND 4 INTRODUCED are on the record, with measurements AND fixes | VERIFIED | `ROADMAP.md` INTRODUCED list + `26-SECURITY.md` clause (7) carry the 1589-of-1714 and the 136/67-across-9 numbers together with the narrowing and the widening that closed them. |
| 3 | **AR-27-11 defined with severity from a measurement, reachability measured-or-unmeasured, owner named** | **FAILED** | The register half is exemplary and independently confirmed. The OWNER half (`27-HUMAN-UAT.md` item 12) and `ROADMAP.md` entry (1) were not brought along by the correction. See gap 2. |
| 4 | Standing rule (vii) added and applied to ROUND 5 ITSELF | VERIFIED | Clause (vii) exists; `ROADMAP.md` carries a four-entry INTRODUCED list; the register even records that entry (1) was itself an instance of the defect ("Naming a residual is not the same act as measuring it"), and that two of the four introduced entries were found only because one plan happened to run a gate the others did not — "a statement about luck and not about method". |
| 5 | `threats_open` RECOMPUTED by the documented awk, population restated in full | VERIFIED | I ran the awk verbatim: **0**. `grep -c "^\| T-26-"` = **46**; closed rows = **46**. Both match the register's stated raw output. Register rows 60 -> 61 confirmed (46 `T-26-` + 15 `AR-`, up from 14). The question the population forces is asked and answered for AR-27-11, including what the honest options would have been above the gate. |
| 6 | AR-27-04 not relitigated; AR-27-08, `InjectionPointExtractor.kt:29`, `T-27-06-06` untouched | VERIFIED (byte-checked) | AR-27-04 row sha256 identical across the range (`6106f921…`); `T-27-06-06` identical (`0b12420a…`); the only `src/main/kotlin` change in the whole round is `Redaction.kt` (+93/-5, one functional line). |
| 7 | Provenance recorded without claiming what no artifact corroborates | VERIFIED | `.planning/config.json` still `"mode": "yolo"`; item 12a of `27-HUMAN-UAT.md` carries the confirmation as a human item rather than asserting a maintainer choice. |
| 8 | Phase 27 STILL closes with PRIV-05 NOT SATISFIED | VERIFIED | `ROADMAP.md` re-confirms it for the fifth round; `REQUIREMENTS.md` byte-identical; `PRIV-05` still `[ ]`. |

#### The out-of-plan fix — 2ed1a12 (CR-01) and fb7cbd3 (WR-02) (5 truths)

| # | Truth | Status | Evidence I gathered |
|---|-------|--------|---------------------|
| O1 | All four families are genuinely lost under `":\""`, and the mechanism is stated correctly | VERIFIED | All four byte-identical in and out, STRICT and BALANCED, with compact controls stripping in the same run. Verified for **all three composed rules** — and for `authHeaderRegex` I used low-entropy `X-Api-Key` / `X-Csrf-Token` values to isolate it from `bearerRegex`, which otherwise masks the result on a canonical `Authorization: Bearer …` fixture. The "only the FIRST CONTENT of its string escapes" bound holds in all four (`\r\n`-prefixed variants all strip). The mechanism sentence is exactly right: `:"` is two literal characters, so a space, a backslash, or no colon at all is not a start. |
| O2 | The MEDIUM re-rating is justified by what was measured | VERIFIED for the derivation | The load-bearing correction is real: the original LOW asked "which serialized fields are `List<String>`?", and the carrier is not a field — it is the CONTENT of `HttpRequestResponse.response`, copied verbatim from the target on the default-posture path. `NOT high` is bounded honestly ("no LIVE producer was measured"). Whether the current severity is unambiguous **to a reader** is where this breaks; see O2b / gap 2. |
| O2b | Current severity unambiguous despite the append-only prefix preservation | PARTIAL -> folded into gap 2 | Inside `26-SECURITY.md` it is unambiguous: an explicit `── CORRECTION 2026-08-26 … EVERYTHING ABOVE THIS MARKER IS LEFT BYTE-UNCHANGED, INCLUDING THE WORD LOW …` marker, plus a second statement in the recomputation comment ("`AR-27-11`'s SEVERITY was RAISED from LOW to MEDIUM"). Outside it — ROADMAP and HUMAN-UAT — the reader sees only LOW. |
| O3 | The AR-27-11 row preserves its earlier text as a byte-exact prefix; `threats_open` correct | VERIFIED | The row opens on the original `NEW, OPEN, severity LOW …` text and the correction is appended behind a marker; `threats_open: 0` reproduces under the documented awk, and the reasoning that an `AR-` row sits outside the `\| T-26-` population at any severity is correct and restated in full. |
| O4 | `Redaction.kt` and `THIRD_OPEN_FINDING` agree with the register | VERIFIED for the KDoc/rationale, WARNING for two failure messages | Rationale (b) and the `JSON_STRING_OPEN` KDoc both carry the four families and MEDIUM; `THIRD_OPEN_FINDING`'s KDoc enumerates all four. But `LogicalLineBoundaryScopeTest.kt:157` and `:233` — the two assertion FAILURE MESSAGES — still say "the JSON-ARRAY-ELEMENT string open" and "a header at the open of a JSON ARRAY ELEMENT string … That residual is the whole trade". Folded into gap 2. |
| O5 | WR-02 covers trailing comments without breaking the raw-string state machine; 27-15's loud gate intact | VERIFIED (mutation-proved, twice) | Reverting to `val scannable = line` takes `aTrailingCommentMentioningATripleQuoteDoesNotToggleTheRawStringState` RED with **exactly** the predicted failure — a thrown `unbalanced triple quotes in <fixture>` on a balanced fixture. Substituting the naive `line.substringBefore("//")` takes `noGreenTestAssertsASensitiveValueSurvivesARedactingPolicy` RED with **`unbalanced triple quotes in com/six2dez/burp/aiagent/ui/ChatPanelToolGateTest.kt`** — the exact file and exact message the KDoc and the commit message claim, independently reproduced. The three-state scan is sound; its only conservative edge (a `'"'` char literal never closes) fails in the no-strip direction. The loud-failure gate is untouched and passes; the full suite is green. |

**Score: 28/30 truths verified.**

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|---|---|---|---|
| Four AR-27-11 families survive; controls strip | standalone JDK 21 probe over `build/classes/kotlin/main` | 4/4 byte-identical both modes; compact + `\r\n` controls strip | PASS |
| `authHeaderRegex` loses the same four (isolated from `bearerRegex`) | same, with `X-Api-Key` / `X-Csrf-Token` low-entropy values | K1-K5, C1-C4 UNCHANGED; K0, C0 `[REDACTED]` | PASS |
| Round-4 destruction payload | same, 40 content markers | `destroyed=0`, `markers 40 -> 40`, `identical=true` | PASS |
| Old declaration regex blindness on the pre-plan tree | Python re-implementation over `git worktree` at `5f5aeab` | 151 files / 1784 lines / 136 invisible / 67 backtick / 9 files / 61 override | PASS |
| Constant revert is caught | mutation + `./gradlew test` | 2 tests RED | PASS |
| WR-02 revert is caught | mutation + `./gradlew test` | 1 test RED, with the predicted message | PASS |
| Naive `substringBefore("//")` breaks the tree walk | mutation + `./gradlew test` | RED, naming `ChatPanelToolGateTest.kt` | PASS |
| Declaration-gate narrowing is caught | mutation + `./gradlew test` | 1 test RED | PASS |
| Stale axis count is caught | mutation + `./gradlew test` | 1 test RED | PASS |
| `threats_open` | documented `awk` | `0` (46 scanned, 46 closed) | PASS |
| Register prefix preservation | sha256 + Python common-prefix | 29489/29499 of `T-26-02-01` preserved | PASS |
| Full suite | `./gradlew ktlintCheck detekt test` | 175 classes, 1246 tests, 0 failures | PASS |
| Sweep test count vs register | `grep -c "^    @Test"` + JUnit XML | **16** vs register's **15** | **FAIL** |
| Unskipped self-hits vs register | instrumented `detect(SELF_PATH, …)` in a throwaway worktree | **15** vs register's **14** | **FAIL** |

### Requirements Coverage

| Requirement | Status | Evidence |
|---|---|---|
| PRIV-05 | **NOT SATISFIED — by design** | `REQUIREMENTS.md` sha256 identical at `c2d980f` and HEAD; `:23` reads `- [ ] **PRIV-05**`. All three plans, `ROADMAP.md` and `26-SECURITY.md` state this. `AR-27-08` and `InjectionPointExtractor.kt:29` remain Phase 28's and are untouched. This is the designed outcome and is not scored as a gap. |

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|---|---|---|---|---|
| — | — | No `TODO` / `FIXME` / `XXX` / `TBD` / `HACK` / `PLACEHOLDER` in any of the four files round 5 modified | — | Clean |

## Gaps Summary

**Round 5 got the code right.** The narrowing is correct, the four families it costs are real and I
measured every one of them independently, the gates that catch a revert are non-vacuous under
mutation, the WR-02 fix does what it claims and its measured counter-example (`ChatPanelToolGateTest.kt`)
reproduces verbatim, the register's byte-exact prefix discipline holds to the character, and
`threats_open` recomputes to 0. That is a materially better round than the four before it, and the
records are, for the first time, honest about their own failures in the same breath as their fixes —
`27-14-SUMMARY` calling its own plan's expectation wrong, `27-15-SUMMARY` recording 652-vs-625 as
unreconciled, `ROADMAP.md` recording that two of its four introduced residuals were found by luck.

**And it happened a fifth time anyway, twice, in the same shape.** Both instances are the same
mechanism at one more remove than round 4's: *the measurement was taken, was right, and an artifact
downstream of it was not brought along.*

1. **`fb7cbd3`, the round's final commit, falsified two counts in standing-rule clause (vi) — the
   clause that prohibits exactly that, and whose own worked example is exactly that.** It added a
   16th test and a raw-string fixture and touched no record. Clause (vi) still says "15 tests"
   (measured: 16) and "returns 14" (measured: 15). Round 5 built a machine check for the one number
   in that clause that had gone stale before — `STATED_BLIND_AXES`, which I mutation-proved works —
   and left its two neighbours as prose. fb7cbd3's commit message reasons about the machine-checked
   number and never asks the same question of the other two. The number that was checked did not
   drift; both numbers that were not, did, inside the same round.

2. **`2ed1a12`'s severity correction reached the register and the source, and not the maintainer.**
   `27-HUMAN-UAT.md` item 12 — which `26-SECURITY.md` names by name as the owner's venue — still
   files AR-27-11 at LOW over one family, and still argues for acceptance from the
   `List<String>`-fields reachability enumeration that the same round measured to be the wrong
   question. Worse, its Option B ("widen … closes the residual at the control") would close family 4
   only; families 1 and 2, which are the ones that put the finding at MEDIUM and on the default-posture
   path, interpose a backslash or a space and would remain. The decision document understates the
   finding by a severity level and overstates its own fix by a factor of four. `ROADMAP.md` entry (1)
   carries the same superseded LOW with no marker at all.

Neither is a leak and neither is a shipped correctness break. Both are record defects. But this phase
has spent five rounds establishing that a record defect *is* the defect class here — four wrong
closures, every one of them a claim that outran its measurement — and clause (vii) was written in
this very round to stop the second one. The honest reading is that clause (vii) worked as written
and was still not enough: it makes a round file what it introduced, and says nothing about keeping
that filing true when the finding is later corrected, or about re-measuring a record's counts when a
follow-up commit changes the control they describe. Both gaps are cheap to close and both suggest the
same remedy the round already used successfully once — make the number machine-checked, and make the
correction reach every artifact that cites the finding.

---

_Verified: 2026-08-26T20:05:00Z_
_Verifier: Claude (gsd-verifier), round 5_

---

## Re-verification — 2026-08-26

**Scope:** the TWO gaps recorded above, and nothing else. The 28 must-haves verified in the round-5
pass are NOT re-scored; I found no evidence disturbing any of them, and they stand.
**Range re-verified:** `2a880f9..4d7ebfe` — `818cc0f` (gap 1), `6f48091` (gap 2), `4d7ebfe` (clause viii).
**Verified against:** HEAD `4d7ebfe`, working tree clean of tracked modifications before and after.
**Verdict: BOTH GAPS ARE CLOSED.** Score moves 28/30 -> 30/30. One NEW finding of the same defect
class, one level smaller, is recorded below as a WARNING with the severity call handed to the
maintainer rather than taken here.

### How this re-check was done

Nothing below is taken from a commit message or from `SUMMARY`/`WINDOWS` prose. Every number was
re-measured, and every claim of non-vacuity was re-proved by mutating the source myself:

- **Four source mutations** applied to `RedactingPolicySurvivalSweepTest.kt` at HEAD, each run
  through `./gradlew test --tests '*RedactingPolicySurvivalSweepTest*'`, each restored and the
  restore confirmed with `git diff`.
- **A standalone JDK-21 probe** compiled against `build/classes/kotlin/main` +
  `kotlin-stdlib-2.2.21`, calling
  `Redaction.INSTANCE.apply(raw, RedactionPolicy.Companion.fromMode(mode), "salt-probe", false)`
  over the four AR-27-11 families and two positive controls, STRICT and BALANCED.
- **A regex model of the composer** (`logicalLineHeaderRule`'s two fixed-width lookbehinds) with
  Option B's proposed `["` / `,"` alternatives added, to test Option B's corrected coverage claim
  rather than accept it.
- **`grep -rn 'AR-27-11'`** run by me across the repository, not read from the fixer's list.
- **Byte-level prefix comparison** (Python `SequenceMatcher` / exact slice equality) of the amended
  `27-HUMAN-UAT.md` item 12 and `CONCERNS.md` AMENDMENT 5 against their text at `2a880f9`.
- **Suite:** `ktlintCheck detekt test` BUILD SUCCESSFUL in 3m 7s — **175 classes, 1248 tests,
  1 skipped, 0 failures, 0 errors** (tallied from the JUnit XML, not from Gradle's summary line).

### Gap 1 — stale counts in `26-SECURITY.md` standing-rule clause (vi): **CLOSED**

Closed by `818cc0f`, and closed at the MECHANISM rather than at the instance.

**The amended numbers are correct against the tree.** Both re-measured here:

| What | My measurement at HEAD | Clause (vi) now states |
|---|---|---|
| `@Test` methods the class declares | **18** — `grep -cE '^[[:space:]]*@Test\b'` returns 19 over raw lines; the 19th is line 1600, inside the `DECLARATION_SHAPE_FIXTURE` raw string. JUnit XML for the class: `tests="18" failures="0"`. | **18** |
| Unskipped self-hits | **15** — read out of the mutated assertion's own failure text (`found 15 hits`) and pinned by the passing `assertEquals`. | **15** |

**The two new checks genuinely go RED when the stated number drifts — mutation-proved by me, in both
directions, not accepted from the fixer's transcript:**

| # | Mutation | Result | Failure text I read out of the JUnit XML |
|---|---|---|---|
| A | `STATED_TEST_METHODS` 18 -> 15 **and** `STATED_UNSKIPPED_SELF_HITS` 15 -> 14 (the two stale values the register carried) | **2 RED** | `this class declares 18 @Test methods and states 15` · `the detector run over THIS FILE WITHOUT the raw-string skip found 15 hits; 14 is what` |
| B | an extra `@Test` inserted **INSIDE** `DECLARATION_SHAPE_FIXTURE` (raw-string interior); constants untouched | **GREEN** | — the count is derived over `fileWalk`, so fixture text is not counted as a method |
| C | an extra **real** `@Test` method added to the class body; constants untouched | **1 RED** | `this class declares 19 @Test methods and states 18` |
| D | `MIN_EXPECTED_UNSKIPPED_SELF_HITS` 2 -> 99 | **1 RED, and only 1** | `expected at least 99 unskipped self-hits from the positive fixtures; found 15` |

- **A** proves both checks are live against exactly the drift that occurred, and independently
  reproduces my own 18 and 15.
- **B and C together** answer the question this re-check was asked to settle: the `@Test` count is
  **not fooled by the `@Test` inside `DECLARATION_SHAPE_FIXTURE`**. A fixture `@Test` moves nothing;
  a real one moves it. Deriving over `fileWalk` rather than over raw lines is doing real work — a
  raw-line count would state 19 today.
- **D** proves the pre-existing floor is **still present and still doing its own job**:
  `MIN_EXPECTED_UNSKIPPED_SELF_HITS = 2` remains at `:952`, is still asserted inside
  `theRawStringSkipIsWhyTheSelfScanIsClean`, and fires **alone** — the exact-pin test stayed green
  through it. The floor was kept beside the exact pin, not replaced by it, which is what the fix
  claimed.

All four mutations restored; `git diff` clean on the file after each.

**Clause (vi) itself** (`26-SECURITY.md:1218-1245`) now reads `18 tests` and `returns 15`, amended in
place with the drift, its cause (`fb7cbd3`) and the controlled experiment recorded at full width. The
only surviving `15 tests` / `returns 14` strings in the file (`:1234`, `:1542`) are inside dated
narratives describing the past state, which is correct. `STATED_BLIND_AXES = 13`, the one number that
never drifted, is unchanged.

**Truth 5 of plan 27-15 moves ✗ FAILED -> ✓ VERIFIED.**

### Gap 2 — the LOW -> MEDIUM correction reaching its consumers: **CLOSED**

Closed by `6f48091`. I ran my own `grep -rn 'AR-27-11'`; it returns **15 files**. Every one of them
was read.

**Nothing in the repository now states `AR-27-11` at LOW, or as array-element-only, as CURRENT and
without a supersession marker.**

| Artifact | State at HEAD | My check |
|---|---|---|
| `27-HUMAN-UAT.md` item 12 | SUPERSEDED banner under the heading; dated `CORRECTION 2026-08-26`; MEDIUM over four families; Option B corrected; Option C added and labelled derived-not-measured | Body byte-identical to `2a880f9` up to the correction marker, verified by exact slice comparison — **except the `why_human:` field**, see WARNING 2 |
| `.planning/ROADMAP.md` entry (1) | inline `[SUPERSEDED at LOW / one family …]` tag plus a dated CORRECTION after the entry; entries (2)-(4) untouched | Read in full at `:627-667` |
| `LogicalLineBoundaryScopeTest.kt` `:157`, `:233` | both failure messages carry the four-family mechanism, including `do not re-widen it to ["` / `,"` alone believing that closes AR-27-11: it closes ONE of the four` | Assertions unchanged (`text.contains(THIRD_OPEN_FINDING)`, `assertEquals(":\"", decoded)`); class green **4/4** |
| `CONCERNS.md` AMENDMENT 5 item (3) | SUPERSEDED tag; AMENDMENT 6 appended | The AMENDMENT 5 edit is a **pure insertion** of the tag — machine-verified, no deletion, no reordering. But see WARNING 1 |
| `27-14-SUMMARY.md`, `27-16-SUMMARY.md` | appended `SUPERSEDED SEVERITY` notes | Diffs are **pure appends** — every changed line is a `+` at end of file |
| `Redaction.kt` `:261/:288/:357` | MEDIUM, all four under one id, with the raised-from-LOW history | Unchanged by these three commits and already correct |

**The severity claim being propagated is factually right — re-measured at HEAD by me**, standalone
probe over freshly compiled classes:

```
SURVIVES STRICT    F1 nested/escaped   {"response":"{\"cookie_header\":\"Cookie: sid=SECRETVALUE1\"}"}
SURVIVES STRICT    F2 pretty-printed   {"notes": "Cookie: sid=SECRETVALUE1"}
SURVIVES STRICT    F3 bare top-level   "Cookie: sid=SECRETVALUE1"
SURVIVES STRICT    F4 array element    {"tags":["Cookie: sid=SECRETVALUE1"]}
STRIPPED  STRICT   C0 compact control  -> {"notes":"Cookie: [STRIPPED]"}
STRIPPED  STRICT   C1 escaped-newline  -> {"notes":"GET / HTTP/1.1\r\nCookie: [STRIPPED]"}
```

Identical results under BALANCED. All four families lost, both controls firing in the same run, so
the null result is attributable to REACH and not to a dead probe.

**Option B's correction is factually right, and I did not take it on the text.** Verified two ways.
(i) Structurally: the shipped composer carries two fixed-width lookbehinds, `(?<=\\[rn])` and
`(?<=:")`; the two characters preceding the header are `\"` in family 1, `` `"` `` (space-quote) in
family 2, nothing at all in family 3, and `["` / `,"` in family 4. (ii) Empirically, with a regex
model of the composer:

```
== SHIPPED                    F1 MISS   F2 MISS   F3 MISS   F4 MISS    C0 MATCH
== OPTION B (+ [" and ,")     F1 MISS   F2 MISS   F3 MISS   F4 MATCH   C0 MATCH
```

**Widening to `["` and `,"` closes family 4 and only family 4.** Item 12's corrected statement —
family 1 interposes a backslash, family 2 a space, family 3 has no colon — is correct, and Option C's
"three different look-back widths (0, 2, 3)" is the right derivation.

**Two citing files the propagation list did NOT name, found by my grep and cleared:**

- `27-15-SUMMARY.md:353,357` — mentions `AR-27-11` only as register work reserved for plan 27-16, and
  as clause (vi)'s axis count. States **no severity and no family**. No marker owed.
- `COVERAGE.md:80` — records that `Serialization.kt` was READ by 27-16 "for the `AR-27-11`
  reachability measurement". Records an activity, not the refuted conclusion. Info only; a reader
  cannot take a severity or a bound from it.

**The deliberate non-amendments are RIGHT, and each was checked rather than accepted:**

- `27-14-PLAN.md` / `27-16-PLAN.md` — a plan records intent before execution; both pair with a
  SUMMARY that now carries the correction. Rewriting a plan backwards would be a different defect.
- `27-REVIEW-3.md` — it is the SOURCE of the correction. `:120` already reads "`AR-27-11` states ONE
  family; the narrowing measurably gave up FOUR". Nothing to amend.
- `27-VERIFICATION*.md` — verification records of a moment; this file's own gap 2 documents the
  correction.
- `26-SECURITY.md`'s internal citations — I checked `:111`, `:295` and `:653` directly. Each sits
  ABOVE a later dated block in the same file's append-only sequence: the counter comment's
  `RE-RUN 2026-08-26 (OUT-OF-PLAN CORRECTION …)` which states "`AR-27-11`'s SEVERITY was RAISED from
  LOW to MEDIUM"; the row's own `CORRECTION` marker; and the evidence section's
  `### CORRECTION 2026-08-26 — the enumeration above asked too narrow a question`. The claim holds.
- `WINDOWS.md` #44 states the refuted `List<String>`-fields reachability, superseded by the later
  #50 in the same dated append-only log — the same shape, consistently applied.

**Truth 3 of plan 27-16 moves ✗ FAILED -> ✓ VERIFIED. Truth O2b (PARTIAL) resolves with it.**

### Standing-rule clause (viii) — assessment: a FAIR reading, and it does close the mechanism

Clause (viii) (`26-SECURITY.md:1493-1580`) is a correct diagnosis of what actually went wrong.

**It is a fair reading.** Its central claim — that clause (vii) worked exactly as written and was
still not enough, because clauses (i) through (vii) all govern *the moment a claim is authored* and
both round-5 gaps happened AFTER a filing that was correct when made — is precisely the mechanism
this report named in its own closing `missing` item. It does not overstate: it explicitly credits
(vii) with having produced the INTRODUCED heading, the self-entry and the named owner.

**It closes the mechanism, in the two places the mechanism failed.**
Part **(a)** takes the fan-out list out of the reviewer's hands and makes it mechanical —
`grep -rn '<finding-id>'` IS the list, and the register's own OWNER field names the decision venue —
which is exactly right, because the sixth artifact was found only by grepping the id and the most
consequential one was the venue the register already named by name.
Part **(b)** does not merely say "re-measure"; it says that where a number is source-derivable the
commit must make a TEST derive it, and it justifies that with the round's own controlled experiment
(the machine-checked number did not drift; both prose ones did, inside one round, by that round's
last commit). That is the correct lesson from the correct evidence.
The **out-of-plan corollary** is the load-bearing part: both gaps came from commits that ran the
suite, passed it, and touched no record. Without it the clause would have been evadable by exactly
the route that produced both gaps.

**One honest caveat, recorded rather than smoothed.** Part (b) has a gate — the two new tests. Part
**(a) has only prose**: nothing gates a correction commit on having run the grep. That is the same
class of promise about future diligence that (b) argues, with evidence, does not survive here. The
new finding below is the first data point on how well (a)'s prose form holds.

### NEW FINDING — WARNING 1: `CONCERNS.md` AMENDMENT 6 item (5) states a fan-out count its own siblings falsify

This is a fresh instance of the same defect class, one level smaller, introduced by the commit that
closed gap 2.

`.planning/codebase/CONCERNS.md` AMENDMENT 6 item (5) reads:

> Three further artifacts CITED the finding at the superseded LOW — this entry, `ROADMAP.md`'s
> round-5 INTRODUCED list, and `27-HUMAN-UAT.md` item 12, the maintainer's own decision venue — and
> none was found by the correction, only by the next verifier.

**MEASURED BY ME at `2a880f9`, the tree that sentence describes — FIVE artifacts carried the
superseded LOW, not three:**

```
27-14-SUMMARY.md:37  "Accept the array-element residual … at LOW, file it as AR-27-11"
27-16-SUMMARY.md:45  "Assign AR-27-11 LOW from a MEASURED reachability enumeration"
```

Both are `git show 2a880f9:` reads. Both were amended by `6f48091` itself, with
`SUPERSEDED SEVERITY` notes, *precisely because* they carried the LOW — so the sentence is falsified
by its own commit's other hunks.

**And the second half is wrong for "this entry".** `CONCERNS.md` was NOT found by the next verifier.
`6f48091`'s own message says so — "**FOUND BY GREP**, named by no review and no verification" — and
clause (viii) says "the sixth only by grepping the finding id". This report's gap 2 never named it.

**Three sibling records, written across these same three commits, now state the fan-out size three
different ways:** three (`CONCERNS.md` AMENDMENT 6), five (`WINDOWS.md` #50, whose item (5) names two
files under one number), and six (clause (viii) and `6f48091`'s message). **Only the six is right as
a file count.**

**Why this is a WARNING and not a third gap.** It is a lesson narrative, not the stated bound of a
control. Nothing depends on it: the severity, the four families, the mechanism, the reachability, the
mitigating bound and the Option B/C correction are all correct in that same amendment and in every
other propagated artifact — I checked each. No decision is misinformed by it.

**Why it is recorded at full width anyway, and the call handed over.** The phase's own clause (vi)
prohibits a stated bound diverging from its control; the control here is
`grep -rn 'AR-27-11'` plus a read, and it returns five. Applied consistently, the standard round 5
used on itself would make this a third gap. That call belongs to the maintainer, not to this
verifier, and it is filed as human-verification item 4 below. **The remedy is one appended sentence**
under AMENDMENT 6 — the file's own discipline, marked not rewritten — correcting "Three" to five and
"only by the next verifier" to "and this entry only by grepping the finding id".

### NEW FINDING — WARNING 2 (Info): the one non-append edit in the whole propagation

`27-HUMAN-UAT.md` item 12 is byte-identical to `2a880f9` up to the correction marker — I verified this
by exact slice comparison, not by reading the banner's claim — **with one exception**: the item's
four-line `why_human:` field was **replaced in place**, not marked and appended. Its old text
("*…with one measured carrier and one unmeasured remote half…*") now exists only in git history.

The banner's literal claim ("everything from here to the `CORRECTION` marker … is left byte-unchanged")
and the correction's ("Nothing above this marker is edited") are both **true as written** — the
replaced field sits at the marker, not above it. The commit message's looser "the original body left
BYTE-UNCHANGED" is what overstates. A stale `why_human:` would have been actively misleading, so the
replacement is defensible; it is recorded because this file's discipline is mark-never-delete and
this is the single place in three commits where text was deleted.

### Invariants — all confirmed

| Invariant | Result |
|---|---|
| `REQUIREMENTS.md` byte-unchanged; PRIV-05 still `- [ ]` | sha256 `9b321966…` **identical** at `c2d980f`, `2a880f9` and HEAD; `:23` still `- [ ] **PRIV-05**` |
| `threats_open` = 0, recomputed not hand-edited | documented `awk` run verbatim by me: **0**; `grep -c '^\| T-26-'` = **46**; closed rows = **46**; frontmatter `:175` reads `0` |
| `AR-27-04`, `AR-27-08`, `AR-27-09`, `AR-27-10`, `AR-27-11`, `T-26-02-01` rows unchanged | per-row sha256 across `2a880f9..HEAD`: `6106f921…`, `902c2ab7…`, `6df6ceb9…`, `3b057d9e…`, `c36811f9…`, `93b64145…` — **all six identical** |
| `STATE.md`, `27-REVIEW*.md`, `27-VERIFICATION*.md`, `build.gradle.kts` untouched | none appears in `git diff --name-only 2a880f9..HEAD` (9 files, all listed above) |
| No `src/main` file changed — no redaction behaviour touched | `git diff --name-only 2a880f9..HEAD -- src/main` returns **0 files**; `Redaction.kt` diff is empty |
| Suite green | `ktlintCheck detekt test` BUILD SUCCESSFUL — **175 classes, 1248 tests, 1 skipped, 0 failures, 0 errors** |
| Working tree clean after my mutations | `git status --porcelain -uno` empty at HEAD `4d7ebfe`; each of the four mutations restored and the restore confirmed |

`./gradlew check` was deliberately NOT run — its `jacocoTestCoverageVerification` red is
maintainer-accepted and filed (`WINDOWS.md` #47), and out of scope by instruction.

### Re-verification scorecard

| Round-5 gap | Status now | Basis |
|---|---|---|
| Gap 1 — clause (vi)'s two stale counts | **CLOSED** | numbers correct (18 / 15, both re-measured); both machine-checked; RED under mutation A and C; not fooled by the fixture (mutation B); floor intact and firing alone (mutation D) |
| Gap 2 — AR-27-11 correction not reaching consumers | **CLOSED** | my own grep over 15 files; nothing states LOW or array-element-only as current without a marker; four families re-measured surviving; Option B's correction proved right two ways |

**Score: 30/30 truths verified** (28 carried forward unchallenged, plus 27-15 truth 5 and 27-16
truth 3). **0 gaps. 2 warnings. 0 regressions.**

### Status: `human_needed`, not `passed`

Both gaps are genuinely closed and no gap remains. The status is **not** `passed` because the
`human_verification` block in this report's frontmatter is non-empty and every item in it is still
open — `27-HUMAN-UAT.md` item 12 still reads `result: [pending]`, and `passed` is only valid with an
empty human-verification section. Nothing here failed. Three items are carried forward verbatim; a
fourth is added by this re-check:

**4. DECIDE whether WARNING 1 is a third gap or a documentation nit.**
*Test:* read `CONCERNS.md` AMENDMENT 6 item (5) against `git show 2a880f9:` of `27-14-SUMMARY.md:37`
and `27-16-SUMMARY.md:45`, and against `6f48091`'s own message.
*Expected:* a recorded call — either an appended one-sentence correction under AMENDMENT 6 (five, not
three; and this entry was found by grepping the id, not by the verifier), or a recorded decision that
a lesson narrative is not held to clause (vi)'s standard.
*Why human:* it is a consistency judgment about how strictly this phase applies its own standing
rules to its own prose, on the one artifact that was itself the missed sixth. Nothing downstream
depends on the number, so this is a policy call rather than a correctness one.

The **AR-27-11 re-decision (item 1 above) is now materially better posed than it was at round 5**:
item 12 states MEDIUM over four families, its Option B is corrected to what it actually buys, and an
Option C exists that covers all four with its cost honestly labelled derived-not-measured. That was
the point of these three commits, and it landed.

---

_Re-verified: 2026-08-26T21:05:00Z_
_Verifier: Claude (gsd-verifier), round 5 re-check at `4d7ebfe`_

---

## UAT resolution — 2026-08-27

`status` moves `human_needed` -> `passed`. Nothing in the verification changed; the human-verification
section that held it open is now answered. `27-HUMAN-UAT.md` is `status: complete`, **14/14, zero
issues, zero pending** (commit `a7c9a73`).

**The four items this report named, and how they were answered:**

1. **`AR-27-11` re-decision (item 12)** — ACCEPTED at MEDIUM over four families, against the CORRECTED
   statement rather than the original one-family LOW. Option C and the option to measure its derived
   cost were both offered and not taken.
2. **`AR-27-04` (the restated item)** — **MAINTAINER-SIGNED ACCEPTANCE at MEDIUM**, its first human
   disposition ever. Previously auto-selected by `mode: yolo` and carried unanswered through three
   rounds. The finding, severity and status are unchanged; only the provenance is.
3. **Round-4 narrowing provenance (item 12a)** — **ENDORSED, NOT AUTHORED.** Recorded in the direction
   the artifacts supported: a directed default, endorsed retrospectively, not a choice made at the time.
4. **WARNING 1 / `CONCERNS.md` AMENDMENT 6 item (5) (item 13)** — decided as a **GAP, not a nit**. The
   one-sentence correction was appended: FIVE artifacts cited the superseded LOW, not three, and this
   entry was found by grep rather than by the verifier. **Precedent set: a lesson narrative IS held to
   standing-rule clause (vi)'s standard.** The cheaper exemption was available and not taken.

**Two decisions taken during UAT changed the tree and are not covered by this report's 30/30 score:**

- **`AR-27-09` CLOSED BY FIX** (commit `c883947`, plan 27-17). The maintainer chose the fix over
  accepting a LOW that rested on an unmeasured reachability claim. Shipped `REAL_LINE_START = "^[ \t]*+"`
  (possessive), not the anticipated bare `^[ \t]*`. Side effect: `./gradlew check` is now GREEN —
  redact BRANCH 0.9278 -> 0.93299 against the unchanged 0.930 floor, closing the coverage shortfall
  accepted as red earlier in the session. Margin is 0.003 and one branch is worth 0.005; not durable headroom.
- **`AR-27-07` and `AR-27-10` accepted at LOW**, and `T-27-06-06` accepted as-is with the README/SPEC
  overclaim still shipping.

**These commits post-date the verified range** (`c2d980f..fb7cbd3`). `c883947` in particular touches
`Redaction.kt` and adds `IndentedLogicalLineStartTest`. It is mutation-proved in both directions and
`./gradlew clean check` is green (1258 tests, 0 failures), but it has NOT been through a verifier pass.
Recorded here rather than absorbed.

**PRIV-05 is still `[ ]`.** Nothing in this UAT closed it. `AR-27-08` and
`scanner/InjectionPointExtractor.kt:29` remain owned by Phase 28; `AR-27-04`, `AR-27-07`, `AR-27-10`
and `AR-27-11` remain OPEN, now with human dispositions.
