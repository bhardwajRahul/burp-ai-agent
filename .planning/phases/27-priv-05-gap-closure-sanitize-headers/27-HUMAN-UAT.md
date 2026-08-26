---
status: partial
phase: 27-priv-05-gap-closure-sanitize-headers
source: [27-VERIFICATION.md]
started: 2026-08-24
updated: 2026-08-24
---

## Current Test

[awaiting human testing]

## Tests

### 1. Live-Burp reproduction of the raw-serialization cookie leak (STRICT, then BALANCED)
expected: Load the fat JAR in a live Burp and set Privacy to **STRICT**. Proxy at least one request
carrying `Cookie: wibble=SENTINEL_ABC` and a response carrying
`Set-Cookie: wobble=SENTINEL_SET; Path=/`. From a real MCP client, call **`proxy_http_history`**
(then repeat with `site_map` and `scanner_issues`). Inspect the tool result as the client receives it.

**The finding is CONFIRMED if `SENTINEL_ABC` and `SENTINEL_SET` appear in the tool result.** Repeat
in **BALANCED** — the leak is expected in both modes. As a control, confirm the same cookie value IS
redacted on the prompt path (trigger a passive AI scan and inspect the outbound prompt), which
demonstrates the two paths disagree.

Also note whether `Host:` survives un-anonymised under STRICT in the same tool result — same root
cause, tracked separately from PRIV-05.

why_human: The chain was verified statically at source and measured against the shipped compiled
`Redaction` class on JDK 21 — `Serialization.kt:44` embeds `request()?.toString()` raw;
`toolJson.encodeToString` escapes CRLF to a literal `\r\n`; `redactIfNeeded`
(`McpToolExecutorImpl.kt:1037`) applies but its cookie rules are line-anchored `(?im)^…$` and cannot
match a single-line JSON payload. Confirmed independently: the same regex yields 1 match on
multi-line input and 0 on the JSON-encoded form. What static analysis cannot supply is an end-to-end
run through a real Burp session with a real MCP client and a real proxy history — which is how the
original PRIV-05 was proven (live probe against `Custom-AI-Agent-full-1.0.0.jar`), and this finding
deserves the same standard before it is written into a security register as fact.
result: [pending]

### 2. Re-test after the gap-closure fix lands
expected: Repeat test 1 against a build containing the gap-closure fix. **Neither sentinel appears in
any tool result in STRICT or BALANCED**, on `proxy_http_history`, `proxy_http_history_regex`,
`site_map`, `site_map_regex` or `scanner_issues`. Cookie NAMES may remain; values must not.
why_human: Same reason as test 1 — the fix must be proven on the real emission path, not only by
unit tests over a synthetic payload shape.
result: [pending]

## Notes

- Affected call sites — **14**, seven per executor file, all via `toSerializableForm()` /
  `toSiteMapEntry()`. Measured with
  `grep -rnE 'encodeToString\(it\.(toSerializableForm|toSiteMapEntry)\(' src/main/kotlin`:
  - `McpToolExecutorImpl.kt:608,740,760,836,855,873,896`
  - `McpToolLegacy.kt:475,622,639,713,729,744,764`

  **Correction:** an earlier revision of this note listed 10 sites
  (`McpToolExecutorImpl.kt:608,836,855,873,896` / `McpToolLegacy.kt:475,713,729,744,764`). That list
  was produced by a narrower grep that missed the `toSerializableForm(preprocess)` variants, and it
  is wrong — re-testing from it would exercise the wrong tools. Plan `27-05` pins the corrected
  inventory as a gated count. Not every one of the 14 carries a raw HTTP message: a subset carries
  WebSocket payloads instead, and `27-05` records the raw-HTTP / WebSocket split by tool name.
  When running the tests above, follow `27-05-PLAN.md`'s classification rather than assuming all 14
  are raw-HTTP carriers.
- `cookie_jar_get` is correctly gated and is NOT part of this finding.
- This leak is the **canonical** `Cookie:` / `Set-Cookie:` header, not a name variant — strictly
  broader than the original PRIV-05 defect, which only affected `X-Cookie`-style variants.
- `AR-27-01` was recorded in Phase 27 as an accepted residual. This finding reclassifies it: it is
  safe only where `sanitizeHeaders` runs in front of it, and on these 10 sites nothing does.
  `McpToolHelpersTest.kt:209-216` is the phase's own green test asserting exactly that gap.

---

# CARRY-FORWARD — 2026-08-25 (plan 27-09; appended, nothing above is edited)

## THESE ITEMS ARE UNANSWERED. Nothing in phase 27 should be read as having answered them.

Stated first because the reason they are here rather than at a checkpoint is itself a record a later
reader needs.

**Why no `checkpoint:decision` was raised for any of them.** `.planning/config.json` carries
`mode: yolo`, which AUTO-SELECTS blocking checkpoints. `26-SECURITY.md` already carries a worked
precedent: the `AR-27-04` disposition records, in its own bold paragraph, that it was **auto-selected
by the configured run mode and NOT maintainer-chosen**, and instructs a future auditor to read that
row as a **recorded default** rather than as a human having weighed the release posture. Raising a
checkpoint under this run mode would have produced a second artifact of that kind — an
auto-selected answer, filed as though a maintainer had made it. **So the items live HERE, legible as
open, instead.**

**The corollary a later reader must not miss: the ABSENCE of checkpoints in plans 27-07, 27-08 and
27-09 is not evidence of an absence of open questions.** It is the opposite — the questions were
routed here precisely so the run mode could not rubber-stamp them.

**A note on this file's frontmatter.** Its `updated:` and `source:` fields still read `2026-08-24`
and `[27-VERIFICATION.md]`. They are deliberately NOT corrected: plan 27-09's acceptance criterion 1
requires **zero removed lines** in this file under the robust diff form, and editing a frontmatter
value removes a line. The staleness is recorded here rather than silently left — the true state is
that this file was amended on **2026-08-25** by plan 27-09, drawing on `27-VERIFICATION-2.md`,
`27-07-SUMMARY.md` and `27-08-SUMMARY.md`.

---

## Tests, continued

### 3. CARRIED FORWARD, still unrun — does Montoya's `parameters()` really yield COOKIE entries in a live Burp?
expected: Load the fat JAR in a live Burp and set Privacy to **STRICT**. Call `params_extract`, then
`request_parse`, with a raw request carrying `Cookie: wibble=SENTINEL_ABC`.

**Expected BEFORE the wave-7 fix (the defect reproduces):** the tool result contains
`type=COOKIE name=wibble value=SENTINEL_ABC`, and `request_parse`'s JSON shows
`"headers":{"Cookie":"[STRIPPED]"}` sitting beside
`{"type":"COOKIE","name":"wibble","value":"SENTINEL_ABC"}` — the control defeated on its own output.

**Expected AFTER (this is now the live build):** no cookie VALUE in either field. See test 5 below,
which is the same run read for the fix rather than for the defect; running them as one session is
the efficient way to close both.

why_human: The redaction half is measured decisively against the shipped compiled classes. **The
MONTOYA half is not.** That `HttpRequest.parameters()` yields COOKIE-typed entries in-process is
established from the API surface (`HttpParameterType.COOKIE`, `parameters(HttpParameterType)`) and
from two independent in-repo statements that rely on it — but it **has never been executed inside a
live Burp**. `HttpRequest.httpRequest()` is a Montoya static factory requiring Burp's internal
`ObjectFactory` and cannot run in a pure-JVM test; `McpToolScopeEnforcementTest` records the same
constraint. Carried unchanged from `27-VERIFICATION-2.md` item 1, and re-carried by both
`27-07-SUMMARY.md` (coverage D5) and `27-08-SUMMARY.md` (coverage D7).
result: [pending]

### 4. CARRIED FORWARD, still unactioned — `T-27-06-06`: the user-facing STRICT host-anonymisation overclaim
expected: `README.md:247` ("STRICT privacy mode anonymizes hosts using real HKDF …") and `SPEC.md:80`
(the privacy-mode table's `anonymized (HKDF/HmacSHA256)` cell) with its paragraph at `SPEC.md:86`
state that host anonymisation applies to the prompt path and to parsed-header tool results, and
**does not** apply to the raw HTTP message or the `url` field emitted by `proxy_http_history`,
`proxy_http_history_regex`, `site_map`, `site_map_regex` and `scanner_issues`.

why_human: A user-facing documentation change to what SHIPS, not a record repair. Plans 27-06 and
27-09 both scoped themselves to record files and both recorded this item as deliberately unactioned.
**Until that edit lands, `AR-27-04` is accepted AND the documentation still overclaims.**

**THE LOSS RISK, RESTATED because it has not improved.** `.planning/BACKLOG.md` **does not exist in
this repository** — re-measured 2026-08-25, still absent. `T-27-06-06` therefore lives only inside
`26-SECURITY.md` (a phase-26 register) and phase-27 SUMMARYs: **the two documents a docs maintainer
is least likely to open.** No backlog file was created by plan 27-09 to "fix" this, because
inventing a file the project does not use would relocate the item rather than surface it. Its
visibility now rests on this file and on a `ROADMAP.md` Backlog line. If neither is read, the item
is lost — that is the standing risk, stated rather than assumed away.
result: [pending]

### 5. NEW — live-Burp confirmation that the wave-7 parameter fix holds end to end
expected: Same session as test 3. In **STRICT**, call `params_extract` and `request_parse` with a
raw request carrying `Cookie: wibble=SENTINEL_ABC`.

**Expected AFTER (this build):** `params_extract`'s line reads
`type=COOKIE name=wibble value=[STRIPPED]`, and `request_parse`'s `parameters` array carries
`{"type":"COOKIE","name":"wibble","value":"[STRIPPED]"}` **beside** its already-stripped `headers`
map. Cookie NAMES may remain; VALUES must not. Repeat in **BALANCED** — the same. Repeat in **OFF**
as a control: the value SHOULD reappear there, and if it does not, the probe is testing something
other than the policy.

Also check a NON-cookie parameter in the same call (e.g. a URL-typed `q=hello`): its line and its
JSON entry must be **byte-identical** to the pre-change output. Over-redaction here would be a
regression, not a fix.

why_human: 22 behavioural probes cover the sanitizer and a source-scan pin covers the producers, but
**the two mechanisms cannot cover each other**: the behavioural probes structurally cannot reach the
production branch (`HttpRequest.httpRequest()`, as in test 3), and the pin proves only that the call
exists in source. An end-to-end run inside a real Burp with a real MCP client is the only thing that
exercises both at once.
result: [pending]

### 6. NEW — live confirmation of the `AR-27-08` issue-detail route, with its exact scan preconditions
expected: This route was **MEASURED as surviving STRICT and BALANCED** (`27-08-SUMMARY.md`,
measurement 2) and **deliberately NOT fixed** — so unlike tests 3 and 5, this one is expected to
REPRODUCE on the current build. It is here to confirm the reachability chain in a live process
before Phase 28 fixes it, and to bound how alarming it is.

**The three preconditions its reachability statement names must ALL hold, and they are the reason
this is `medium` rather than `high`:**
1. **Active AI scanning ENABLED.** It is opt-in and defaults to `false`
   (`config/AgentSettings.kt:127`). Turn it on deliberately.
2. **A finding must reach `confirmed`.** `handleResult` calls `confirmFinding` only when
   `confirmation != null && confirmation.confirmed` (`scanner/ActiveAiScanner.kt:1172-1176`, `:1183`).
3. **A `scanner_issues` call must be made** from an MCP client.

**Steps:** in **STRICT**, with active AI scanning on, scan a target whose request carries
`Cookie: PHPSESSID=SENTINEL_XYZ`, so a COOKIE-typed injection point is selected (the target loop
filters on vuln CLASS only, never on `point.type` — `:232-246`, `:1684`). Drive it to a confirmed
finding. Then call `scanner_issues`.

**Expected TODAY (the finding reproduces):** the `detail` field contains
`Original Value: SENTINEL_XYZ` verbatim, while the SAME issue's
`requestResponses[0].request` shows `Cookie: [STRIPPED]` — **one object, one call, one output, one
field controlled and one not.** That asymmetry inside a single result is the finding.

**Expected AFTER Phase 28:** no cookie VALUE in the `detail` field either, in STRICT or BALANCED.

why_human: **This is the one finding in the series that carries BURP-HELD traffic** — a real session
cookie the operator's browser sent, which the AI backend did not previously possess — rather than
caller-echoed content, and it defeats STRICT outright. The measurement and its firing positive
control are decisive about the REDACTION behaviour on the serialized shape. What no test in this
repository establishes is that the three preconditions compose in a live Burp to put a COOKIE-typed
value on that line — which is exactly the difference between a latent finding and a live one, and
this phase has twice recorded a finding at the wrong severity for want of that distinction.
result: [pending]

### 7. NEW, a DISPOSITION rather than a test — accept `AR-27-08` at medium, or escalate it
expected: A recorded human decision. Plan 27-09 filed `AR-27-08` in `26-SECURITY.md` at the
**MEASURED medium** and opened **Phase 28** in `ROADMAP.md` to own the fix, together with the
unconverted cookie-type predicate at `scanner/InjectionPointExtractor.kt:29` that feeds it. What is
NOT recorded is a human accepting that severity and that timeline.

**The two things to weigh, both already measured so this is a judgment and not an investigation:**
it defeats STRICT outright and carries Burp-held traffic (aggravating), and it is unreachable in the
default posture behind three preconditions (mitigating). If the release posture makes "unreachable
by default" insufficient on a shipped 1.0.0, Phase 28 should be pulled forward or the active-scanner
path should carry an interim warning.

why_human: A risk/scope decision on a shipped release. Recorded here rather than raised as a
checkpoint for the reason at the head of this section.
result: [pending]

### 8. NEW, a DISPOSITION rather than a test — `AR-27-07`, widen `SENSITIVE_WORDS` or keep the residual
expected: A recorded human decision. **MEASURED** (`27-08-SUMMARY.md`, measurement 1, with a firing
attribution control): a sensitive-NAMED **non-cookie** parameter — a URL-typed `access_token`, a
BODY-typed `password` — survives `request_parse`'s JSON in STRICT and BALANCED, because
`jsonSecretKeyRegex` keys on the JSON key, which here is the literal `value`.

**Filed at the measured `low`**, on the caller-echo property and because it sits outside PRIV-05's
cookie wording. **The 27-08 plan register had authored `medium` before the measurement existed; the
disagreement is recorded in `26-SECURITY.md` rather than resolved silently, and this item is where a
human picks.**

**The cost of the fix is the reason it was not taken:** widening `SENSITIVE_WORDS` carries WR-01's
**measured 32 false positives** (`status_code`, `errorCode`, `primary_key`, `public_key`, …) across
all three consumer regexes at once. That is a real analysis-quality cost, not a hypothetical one.

why_human: `27-08-SUMMARY.md` states it directly — the measurement is complete and its control
fired, but the DISPOSITION is a maintainer judgment, not a test result.
result: [pending]

---

## The one item that IS answered, recorded with its provenance

### ANSWERED — the fix-or-accept disposition (was `27-VERIFICATION-2.md` human_verification item 2)

**The question.** Fix the four parameter-carrier sites, or accept a scoped residual that narrows
PRIV-05's "by any path" wording to the carriers actually covered.

**The answer: FIX. Date: 2026-08-24.** Acted on by plans 27-07 and 27-08 on 2026-08-25. This item is
**closed, not carried forward** — the four producers now route through
`McpToolHelpers.sanitizeParameters` and the bounty-prompt resolver is gated on the same predicate.

**PROVENANCE, cited rather than manufactured — read this before quoting the answer.** The
disposition is recorded in two places, neither of them planner-authored: `27-VERIFICATION-2.md`'s
`human_verification` item 2 (which poses it), and the phase 27 prose in `ROADMAP.md`, which states
**"the maintainer chose to fix"** — in the gap-closure round 3 note dated 2026-08-25 and, for the
previous round, at the 2026-08-24 note.

**What it is NOT: no `checkpoint:decision` was raised to obtain it.** Under `mode: yolo` a checkpoint
would have been auto-selected and then filed as if it were a maintainer choice — which is exactly
the artifact `26-SECURITY.md` warns about for `AR-27-04`. The citation above is written next to the
answer so a later reader can see where the decision actually came from, and can tell this item apart
from `AR-27-04`'s auto-selected default.

**What the answer does NOT settle.** It disposed of the PARAMETER carrier only. `AR-27-08` and
`AR-27-07` are separate dispositions and are open above, as tests 7 and 8.

---

# CARRY-FORWARD — 2026-08-26 (plan 27-13; appended, nothing above is edited)

## NINE ITEMS ARE STILL UNANSWERED. NOTHING IN ROUND 4 ANSWERED ANY OF THEM.

**Why no `checkpoint:decision` was raised for any of them — unchanged, and re-stated because it is
the reason this section exists at all.** `.planning/config.json` still carries `mode: yolo`, which
AUTO-SELECTS blocking checkpoints. Raising a checkpoint under this run mode produces an
auto-selected answer filed as though a maintainer had weighed it — which is exactly the artifact
`26-SECURITY.md` warns about for `AR-27-04`. **So the items live HERE, legible as open.** The
corollary from the 2026-08-25 section still binds: **the absence of checkpoints in plans 27-10
through 27-13 is not evidence of an absence of open questions.** It is the opposite.

**A note on this file's frontmatter, handled exactly as the 2026-08-25 section handled it.** Its
`updated:` and `source:` fields still read `2026-08-24` and `[27-VERIFICATION.md]`. They are
deliberately NOT corrected, and the reason is the same one: plan 27-13's acceptance criterion
requires **zero removed lines** in this file under the robust diff form, and editing a frontmatter
value removes a line. The staleness is recorded here rather than silently left. **THE TRUE STATE:
this file was amended on 2026-08-26 by plan 27-13, drawing on `27-VERIFICATION-3.md`,
`27-10-SUMMARY.md`, `27-11-SUMMARY.md` and `27-12-SUMMARY.md`.**

### The nine, by number, with their status after round 4

Referenced by number rather than re-opened, so nothing above is duplicated or edited.

| # | Item | Where it is stated | Status after round 4 |
|---|------|--------------------|----------------------|
| 1 | Live-Burp reproduction of the raw-serialization cookie leak (STRICT, then BALANCED) | test 1 above | **UNANSWERED.** Still `[pending]`. Round 4 ran no live Burp. |
| 2 | Re-test after the gap-closure fix lands | test 2 above | **UNANSWERED.** Still `[pending]`. |
| 3 | Does Montoya `parameters()` really yield COOKIE entries in a live Burp? | test 3 above | **UNANSWERED.** Still `[pending]`. The Montoya half remains unexecuted inside a live Burp; `HttpRequest.httpRequest()` still cannot run in a pure-JVM test. |
| 4 | `T-27-06-06` — the user-facing STRICT host-anonymisation overclaim in `README.md` and `SPEC.md` | test 4 above | **UNANSWERED and STILL UNACTIONED.** Deliberately so: a change to what SHIPS is not a record repair, and plan 27-13 scoped itself to record files for the same reason plans 27-06 and 27-09 did. **The loss risk is unchanged** — `.planning/BACKLOG.md` still does not exist, re-checked 2026-08-26. |
| 5 | Live-Burp confirmation that the wave-7 parameter fix holds end to end | test 5 above | **UNANSWERED.** Still `[pending]`. |
| 6 | Live confirmation of the `AR-27-08` issue-detail route with its three scan preconditions | test 6 above | **UNANSWERED.** Still `[pending]`, and still expected to REPRODUCE on the current build — `AR-27-08` is untouched by round 4 and owned by Phase 28. |
| 7 | DISPOSITION — accept `AR-27-08` at medium, or escalate it | test 7 above | **UNANSWERED.** Still `[pending]`. |
| 8 | DISPOSITION — `AR-27-07`, widen `SENSITIVE_WORDS` or keep the residual | test 8 above | **UNANSWERED.** Still `[pending]`. WR-01's measured 32 false positives are unchanged. |
| 9 | DECIDE `AR-27-04` with a HUMAN in the loop | `27-VERIFICATION-3.md` `human_verification` item 2 | **UNANSWERED.** See the restatement at the foot of this section — round 4 removed its two green STRICT pins and that supplied no human judgment. |

**Round 4 answered NONE of these nine.** It closed two control axes and repaired the records; it ran
no live Burp, made no risk decision on a shipped release posture, and edited no user-facing
documentation.

---

## THE TWO DECISIONS THIS ROUND — MAINTAINER-MADE, recorded WITH their provenance

Recorded here, and not merely acted on, because **a later reader must be able to tell a human
decision from a harness default at a glance.** That is the entire point of recording provenance, and
this file already carries the counter-example.

### DECISION 1 — the underscore cookie-header name class: **FIX, by widening the NAME CLASS**

**Decided by the MAINTAINER, BEFORE planning of round 4 began.**

**The question**, as posed by `27-VERIFICATION-3.md` `human_verification` item 1: either widen
`COOKIE_NAME_PART` to `[A-Za-z0-9_-]*` and flip the pinning test, or accept the leak as a numbered,
owned residual in `26-SECURITY.md` with its measured probe output.

**The answer: FIX — and the DIRECTION is part of the decision, not an implementation detail.**
**Widen the REGEX side; NEVER narrow the predicate.** Narrowing `Redaction.isCookieHeaderName` to
match the regexes would restore symmetry and would SHRINK what `McpToolHelpers.sanitizeHeaders`
strips on the MCP tool-result path — the exact direction that reopened this phase in round 1. Acted
on by plan 27-10 on 2026-08-26: one token, `[A-Za-z0-9-]*` → `[A-Za-z0-9_-]*`, the entire non-comment
production delta of that plan.

### DECISION 2 — the JSON-string-open boundary: **IN SCOPE for round 4**

**Decided by the MAINTAINER, BEFORE planning of round 4 began.**

**The question**, as posed by `27-VERIFICATION-3.md` `human_verification` item 4: decide whether the
JSON-string-open and leading-whitespace boundary blind spots in `logicalLineHeaderRule` are in scope
for a follow-up, given that a CANONICAL `Cookie:` header was measured surviving STRICT in both
shapes with the positive control firing on the same run.

**The answer: the JSON-string-open start is IN SCOPE for this round; the leading-whitespace /
obs-fold start is NOT.** Acted on by plan 27-11 on 2026-08-26, which added `JSON_STRING_OPEN` as the
third recognised logical-line start. The fourth start was measured, named, and deferred **inside**
that stated scope rather than discovered after it — it is now `AR-27-09`, and item 10 below is where
its disposition is decided.

### THE CONTRAST, WRITTEN OUT RATHER THAN LEFT TO BE INFERRED

**`AR-27-04`'s disposition is the opposite kind of artifact, and `26-SECURITY.md` says so in its own
bold paragraph:** it was **AUTO-SELECTED BY THE CONFIGURED RUN MODE (`mode: yolo`) AND NOT
MAINTAINER-CHOSEN.** That register instructs a future auditor to read it as **a recorded default,
not as a human having weighed the release posture.**

**Decisions 1 and 2 above are the opposite: made by a person, before planning, and no
`checkpoint:decision` was involved in either.** A reader comparing the two must be able to tell them
apart at a glance, and that is why both are labelled rather than merely acted on.

**THE HONEST WEIGHT OF THE CITATIONS, because provenance recorded at the wrong strength is the same
defect one level up.** The independent, non-planner-authored records of these two decisions are:
`27-VERIFICATION-3.md`, which POSES both questions with their options; `27-10-SUMMARY.md`, whose
"Decisions Made" section records **"Widen the regex, never narrow the predicate — the maintainer's
stated direction"** as an execution-time observation; and `27-11-SUMMARY.md`, which records the
fourth start as "deliberately out of this round's scope". **The round-4 note in `ROADMAP.md` is NOT
independent corroboration** — it is written by plan 27-13, the same plan writing this section, and a
record citing itself proves nothing. Compare the ANSWERED item at the foot of the 2026-08-25 section,
which could cite a `ROADMAP.md` sentence written in a PRIOR round. This one cannot, and says so.

---

## TWO NEW DISPOSITION ITEMS — both are JUDGMENTS, not investigations

Both residuals below are already MEASURED. Nothing further needs to be found out; what is needed is
a decision, and each carries the two things to weigh.

### 10. NEW, a DISPOSITION rather than a test — `AR-27-09`, accept at LOW or pull the one-token fix forward

expected: A recorded human decision. Plan 27-13 filed **`AR-27-09`** in `26-SECURITY.md` at severity
**LOW**: the FOURTH logical-line start — a header line preceded by leading horizontal whitespace, or
an obs-folded continuation line — which `logicalLineHeaderRule` still cannot recognise.

**MEASURED** (`27-11-SUMMARY.md`, "The Indented-Header Measurement", driven through a throwaway
`jshell` harness against the freshly compiled classes): `GET / HTTP/1.1\r\n Cookie: a=SECRET5\r\n\r\n`
survives **BYTE-UNCHANGED under STRICT and under BALANCED**, with the un-indented control stripped to
`Cookie: [STRIPPED]` on the same run. Note that this is **one mode WIDER than `27-VERIFICATION-3.md`
recorded** — plan 27-11 re-measured rather than copying the round-3 prediction forward.

**THE TWO THINGS TO WEIGH, both already measured, so this is a judgment and not an investigation:**

- **Aggravating:** it defeats STRICT outright and BALANCED too, and it is the CANONICAL `Cookie:`
  name — no variant spelling and no unusual character is needed to reach it.
- **Mitigating, and the reason it is filed at `low`:** **no measured emission site in this repository
  indents a header line.** The 14 pinned serialized-emission sites emit a machine-generated message
  `toString()` at column 0, and `buildScanMetadataText` `appendLine`s each admitted header at column
  0.
- **The one thing that is NOT measured, stated so the `low` is checkable rather than trusted:**
  whether an indented header line can reach the composer through analyst-authored free text in
  `HttpRequestResponse.notes` — the same field plan 27-11's own fix was measured on, and a field
  whose content this repository constrains nowhere. If the answer is yes, `low` is wrong.

**The alternative is cheap and is written down:** the one-token fix is `^[ \t]*` in place of `^` on
the real-line branch, it widens only in the OVER-redacting direction, and it is already recorded in
`Redaction.kt` beside the residual and pinned from source by `LogicalLineBoundaryScopeTest`.

why_human: A scope/risk decision on a shipped 1.0.0 release, and the `low` severity rests on an
unmeasured reachability judgment about a free-text field. Recorded here rather than raised as a
checkpoint, for the reason at the head of this section.
result: [pending]

### 11. NEW, a DISPOSITION rather than a test — `AR-27-10`, accept at LOW or widen to the full RFC 9110 tchar set

expected: A recorded human decision. Plan 27-13 filed **`AR-27-10`** in `26-SECURITY.md` at severity
**LOW**: the residual difference set between the bare-contains predicate and the widened name class.

**MEASURED** (`27-10-SUMMARY.md` §6, with the covered class read out of `Redaction.kt` at test time
rather than re-typed): `ALL_RFC9110_TCHARS` = **77**, `COVERED_TCHARS` = **64**,
`NOT_COVERED_TCHARS` = **13** — the characters `!` `#` `$` `%` `&` `'` `*` `+` `.` `^` `` ` ``
`|` `~`. A header name carrying one of those is still ADMITTED onto the outbound prompt by
`Redaction.isCookieHeaderName` and still NOT matched by either cookie regex: the same fail-open shape
the underscore class had, on a different character set.

**THE TWO THINGS TO WEIGH, both already measured, so this is a judgment and not an investigation:**

- **Aggravating:** the mechanism is not hypothetical — it was measured end-to-end on `_` (nine names,
  both modes, pre-fix and post-fix) before plan 27-10 closed that one character. The 13 remaining
  characters sit in the identical position `_` occupied for three rounds.
- **Mitigating, and the reason it is filed at `low`:** **no leak was measured for any of the
  thirteen.** What is measured is the PARTITION and the MECHANISM; the carry-over to these
  characters is INFERRED, and the row labels it as inferred. None of the thirteen appears in a header
  name this repository, its tests, or ordinary HTTP practice has been observed to carry — but that is
  a judgment about convention, not a measurement.
- **The cost of the fix, which is why widening is a judgment and not an obvious yes:** widening
  `COOKIE_NAME_PART` to the full tchar set makes both cookie regexes match names like `X.Cookie` and
  `` `cookie` ``, over-redacting in a direction nobody has measured against a benign-header corpus.
  **WR-01 in `CONCERNS.md` records a measured 32-false-positive cost from a widening that looked
  equally harmless.** The opposite direction — narrowing the predicate to the same class — is
  PROHIBITED for the reason in decision 1 above.

why_human: A scope/risk decision on a shipped 1.0.0 release, and the `low` severity rests explicitly
on an inferred half that is labelled as inferred. Recorded here rather than raised as a checkpoint,
for the reason at the head of this section.
result: [pending]

---

## RESTATED — `AR-27-04` STILL OWES A HUMAN DECISION, and round 4 did not supply one

This is item 9 of the nine above, restated in full because the thing round 4 DID do to `AR-27-04` is
easy to mistake for progress on it.

**What round 4 did:** plan 27-12 **DELETED** the two green
`assertTrue(… .contains("api.example.com"))` assertions under `PrivacyMode.STRICT` that had pinned
this residual, and re-pointed the pass-through they measured at an `assertEquals` byte-identity
fixture under `PrivacyMode.OFF` — the one policy under which pass-through is correct. They were
deleted rather than inverted because the behaviour they described **still ships**.

**What round 4 did NOT do, and this is the whole point:** it did not decide `AR-27-04`.

- The FINDING is unchanged: `Host:` and `SiteMapEntry.url` still reach an AI backend un-anonymised
  under STRICT on the serialized emission shape. `hostHeaderRegex` is still excluded from
  `logicalLineHeaderRule`; `maybeAnonymizeUrl` is still not threaded into the two serializers.
- The SEVERITY is unchanged at MEDIUM and the status is unchanged at OPEN.
- **The PROVENANCE of its disposition is unchanged and is NOT upgraded by this round's activity.** It
  was auto-selected by `mode: yolo`. **Removing a green pin is a test-artifact repair. It supplies no
  human judgment about a release posture, and nothing in round 4 should be read as having supplied
  one.**

**A privacy-control bypass on a shipped 1.0.0 accepted by the harness is not an accepted risk.** The
decision is either a fix (route `hostHeaderRegex` through the composer **and** thread
`maybeAnonymizeUrl` into `toSiteMapEntry` / `toSerializableForm` — half of that fix alone produces a
payload whose `request` is anonymised and whose `url` is not) or a maintainer-signed acceptance. It
is still owed.

why_human: A privacy-control bypass on a shipped release posture, currently accepted by the harness
rather than by a person. Carried unchanged from `27-VERIFICATION-3.md` `human_verification` item 2.
result: [pending]
