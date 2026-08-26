---
status: partial
phase: 27-priv-05-gap-closure-sanitize-headers
source: [27-VERIFICATION.md, 27-VERIFICATION-2.md, 27-VERIFICATION-3.md, 27-VERIFICATION-4.md, 27-VERIFICATION-5.md]
started: 2026-08-24
updated: 2026-08-26
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


---

# ROUND 5 — 2026-08-26 (plans 27-14, 27-15, 27-16)

## ELEVEN ITEMS ARE STILL UNANSWERED. NOTHING IN ROUND 5 ANSWERED ANY OF THEM.

Items **1 through 11** above are carried forward **UNCHANGED**. Round 5 ran no live Burp, actioned no
shipped documentation, and made no disposition on any residual. It closed a correctness regression
this phase itself shipped and a defect in its own security record. **A round that repairs its own
mistakes has not answered a single question a human still owes an answer to**, and this heading is
here so that cannot be misread by scanning.

| # | Item | Status after round 5 |
|---|---|---|
| 1 | Live-Burp reproduction of the raw-serialization cookie leak (STRICT, then BALANCED) | **UNANSWERED.** Still `[pending]`. Round 5 ran no live Burp. |
| 2 | Re-test after the gap-closure fix lands | **UNANSWERED.** Still `[pending]`. |
| 3 | Does Montoya `parameters()` really yield COOKIE entries in a live Burp? | **UNANSWERED.** Still `[pending]`. `HttpRequest.httpRequest()` still cannot run in a pure-JVM test. |
| 4 | `T-27-06-06` — the STRICT host-anonymisation overclaim in `README.md` and `SPEC.md` | **UNANSWERED and STILL UNACTIONED.** Deliberately: a change to what SHIPS is not a record repair, and plan 27-16 scoped itself to record files for the same reason 27-06, 27-09 and 27-13 did. **The loss risk is unchanged** — `.planning/BACKLOG.md` still does not exist, re-checked 2026-08-26. |
| 5 | Live-Burp confirmation that the wave-7 parameter fix holds end to end | **UNANSWERED.** Still `[pending]`. |
| 6 | Live confirmation of the `AR-27-08` issue-detail route | **UNANSWERED.** Still `[pending]`, and still expected to REPRODUCE — `AR-27-08` is untouched by round 5 and owned by Phase 28. |
| 7 | DISPOSITION — accept `AR-27-08` at medium, or escalate it | **UNANSWERED.** Still `[pending]`. |
| 8 | DISPOSITION — `AR-27-07`, widen `SENSITIVE_WORDS` or keep the residual | **UNANSWERED.** Still `[pending]`. |
| 9 | DECIDE `AR-27-04` with a HUMAN in the loop | **UNANSWERED, and NOT relitigated by round 5.** Its disposition is unchanged, its provenance is unchanged, and no plan in round 5 touched its row. |
| 10 | DISPOSITION — `AR-27-09`, accept at LOW or pull the one-token fix forward | **UNANSWERED.** Still `[pending]`. Re-measured by the verifier and holding exactly as recorded. |
| 11 | DISPOSITION — `AR-27-10`, accept at LOW or widen to the full RFC 9110 tchar set | **UNANSWERED.** Still `[pending]`. |

---

## ANSWERED BY FIX — `27-VERIFICATION-4.md` `human_verification` item 1, the bare-quote disposition

**The question that was open.** `27-VERIFICATION-4.md` gap 1 found that the third logical-line start
round 4 shipped was a BARE DOUBLE QUOTE presented as a JSON string open, and asked whether to narrow
it or to keep it and accept the blast radius.

**It is ANSWERED BY FIX, not by a decision recorded and deferred.** Plan 27-14 narrowed
`Redaction.JSON_STRING_OPEN` from `"\""` to `":\""` — a JSON string VALUE open — at
`Redaction.kt:333`. **The measurement, quoted:** on a 1714-character `proxy_http_history`-shaped
payload, characters destroyed went from **1589 to 0** and content markers from **0 of 40 to 40 of
40**, byte-identical `false → true`, in STRICT and BALANCED alike; all five measured non-JSON false
positives went byte-identical (shape 5 excepted, for a reason external to the boundary and recorded
in `27-14-SUMMARY.md`); and round 4's own target was NOT un-fixed — all three PROBE C cases still
produce `Cookie: [STRIPPED]` in both columns and both modes.

**THE PROVENANCE OF THAT DECISION, RECORDED AT THE STRENGTH THE ARTIFACTS SUPPORT AND NO HIGHER.**
This is the paragraph that matters, and it is deliberately weaker than a signature:

- The narrowing was **DIRECTED BY THE ROUND-5 PLANNING BRIEF** — `27-14-PLAN.md` names it as the
  task, so the executor selected nothing.
- It was **INDEPENDENTLY MEASURED TWICE, by records this round did not write**: `27-REVIEW-2.md`
  CR-03 and `27-VERIFICATION-4.md` gap 1. Both measured the same over-match and both showed the
  narrowing removes every measured false positive while keeping both `notes` carriers closed. That
  is real corroboration and it is why this item is answerable at all.
- **`.planning/config.json` still carries `mode: yolo`, and `gsd-tools query check auto-mode`
  reported `false` for this run** — a combination this project has already recorded as producing
  auto-approved gates. **No human answered any checkpoint during round 5.**
- **Therefore: whether a MAINTAINER PERSONALLY CHOSE to narrow rather than to keep the bare quote is
  NOT CODEBASE-VERIFIABLE.** It is not claimed here. What is claimed is that the change was directed
  and twice independently measured — which is a statement about EVIDENCE, not about AUTHORITY.
- **The provenance question is therefore carried as a confirmation item in its own right**, exactly
  as `27-VERIFICATION-4.md` item 4 carried the equivalent question for round 4. See the confirmation
  item immediately below.

**Why this distinction is worth a paragraph rather than a footnote.** `AR-27-04`'s disposition is on
record as auto-selected by `mode: yolo` and NOT maintainer-chosen, and `26-SECURITY.md` instructs a
future auditor to read it as a recorded default rather than as a human weighing a release posture.
The whole value of this file is that a reader can tell those two kinds of artifact apart at a glance.
Claiming a signature no artifact corroborates would collapse that distinction — and it would collapse
it in the direction that flatters this round.

### 12a. CONFIRMATION ITEM — was the narrowing a maintainer's choice, or a harness default?

why_human: The fix is measured and the measurement is not in doubt. What is in doubt is the
PROVENANCE: the run was configured `mode: yolo`, no human answered any checkpoint, and no artifact
under `.planning/` can establish that a person chose narrowing over keeping the bare quote. A
maintainer either confirms the choice was theirs, or records that it was a directed default that they
now endorse — the two read identically in a diff and differently in an audit.
result: [pending]

---

## 12. NEW, a DISPOSITION rather than a test — `AR-27-11`, accept the array-element start or widen the boundary

> **STOP — EVERYTHING FROM HERE TO THE `CORRECTION 2026-08-26` MARKER BELOW IS SUPERSEDED, AND IT IS
> LEFT BYTE-UNCHANGED RATHER THAN DELETED.** It states `AR-27-11` at **LOW** over **one** family, it
> argues acceptance from a reachability enumeration that the same round measured to be the wrong
> question, and its **Option B closes ONE of the four measured families, not the residual**. The
> finding is **OPEN at MEDIUM over FOUR MEASURED families** and is reachable on the DEFAULT-posture
> emission path with no opt-in precondition. **Decide from the corrected item below**, or from the
> `AR-27-11` row in `26-SECURITY.md`, and read what follows only as the record of what this item said
> before `2ed1a12` corrected the finding without reaching this document.

This is a **JUDGMENT, not an investigation.** The residual is already measured in both directions and
its reachability has been enumerated at source. Nothing further needs to be found out.

**THE FINDING.** After round 5's narrowing, a header at the open of a JSON ARRAY ELEMENT string is
not a recognised logical-line start: an array element opens on a bracket-quote or comma-quote
sequence, and the boundary now recognises a colon-quote sequence. **Measured, both columns, STRICT
and BALANCED identical:** `{"tags":["Cookie: a=SECRET8"]}` was `{"tags":["Cookie: [STRIPPED]"]}` under
the bare quote and is **byte-unchanged** after. Filed as `AR-27-11`, OPEN at LOW.

**WHAT WAS MEASURED ABOUT ITS REACH, because that is what the two options weigh against each other.**
`mcp/schema/Serialization.kt` declares **ZERO** `List<String>` fields — its two list fields are arrays
of OBJECTS, whose string members open at `:"` and ARE covered — multi-item tool results are joined
with `\n\n` and carry no JSON array wrapper, and the five `List<String>` models under
`McpToolModels.kt` are INPUT-only. **Exactly one carrier can emit an arbitrary JSON array of strings
through `Redaction.apply`:** the D-03 outbound-privacy redaction of model-authored `argsJson` in
`McpToolExecutorImpl.routeExternalToolCall`, forwarded to a third-party external MCP server. The
remote tool schemas on that path are not owned by this repository and are **UNMEASURED**. And the
residual is narrower than "arrays are uncovered": a realistic raw HTTP message inside an array element
is STILL stripped, because its header follows an escaped newline, which IS a recognised start —
measured, with two positive controls firing in the same run.

**OPTION A — ACCEPT at LOW.** The residual stays filed, cited in `Redaction.kt`, pinned from source by
`LogicalLineBoundaryScopeTest.THIRD_OPEN_FINDING`, and owned here.
*For:* no emission field this repository owns is a JSON array of strings; the one carrier that can be
is model-authored; the realistic shape on that carrier is already covered; and no widening has been
measured against the benign corpus this quarter.
*Against:* it defeats STRICT **and** BALANCED on the plain canonical `Cookie:` name with no variant
spelling required, and the unmeasured remote half is unmeasured rather than absent.

**OPTION B — WIDEN the boundary to recognise the array-element open.** Add `[\"` and `,\"` as two
further FIXED-WIDTH lookbehind alternatives beside `:\"`, preserving the composer's measured 2.4x
fixed-width look-back argument by construction.
*For:* it closes the residual at the control rather than in a record, and the fix is the same shape
and the same cost as the one already shipped.
*Against:* it is a WIDENING, and this phase has already paid once for a widening that looked harmless
— `WR-01` records 32 measured false positives, and the bare quote itself destroyed 93% of a tool
result. **It must not be applied without its own red probe over the benign-payload corpus**, which is
precisely why plan 27-16 wrote the fix down and did not apply it.

**THE ASYMMETRY WORTH STATING PLAINLY:** option A leaves a measured under-redaction on a
model-authored carrier; option B risks an unmeasured over-redaction on every carrier. This phase's own
history contains one costly example of each, which is why this is a person's call and not an
executor's.

### CORRECTION 2026-08-26 — this item is restated at MEDIUM over FOUR families, and its Option B is corrected

**Nothing above this marker is edited.** It is the item as `2ed1a12` left it: that commit corrected
`AR-27-11` in `26-SECURITY.md`, in `Redaction.kt` and in `LogicalLineBoundaryScopeTest`'s
`THIRD_OPEN_FINDING` KDoc, and `git show --name-only 2ed1a12` lists exactly those three files. **It
did not reach this document — the one the register names BY NAME as the owner's decision venue
("OWNER: unchanged — the maintainer, item 12 of `27-HUMAN-UAT.md`").** `27-VERIFICATION-5.md` gap 2.
This correction is that propagation, and it changes what is being decided in two ways: the severity,
and what one of the two offered options actually buys.

**THE FINDING, CORRECTED — a MECHANISM, not an example.** `JSON_STRING_OPEN` is `:"`: the two
LITERAL characters colon then quote. **Any shape that interposes a character between them — a space,
or the backslash of an escaped quote — and any shape with no colon before the quote at all, is not a
recognised logical-line start.** FOUR families follow, every one MEASURED matching under the bare
quote 27-11 shipped and BYTE-UNCHANGED under the narrowed value, in STRICT and BALANCED alike, across
all three composed rules (`cookieHeaderRegex`, `setCookieHeaderRegex`, `authHeaderRegex`), with
compact-shaped positive controls stripping in the same run:

| # | Family | Shape | Why `:"` misses it |
|---|---|---|---|
| 1 | NESTED / ESCAPED string value open | `{"response":"…{\"cookie_header\":\"Cookie: …"}` | a backslash sits between the colon and the quote |
| 2 | PRETTY-PRINTED JSON | `{"notes": "Cookie: …"}` | a space sits between the colon and the quote |
| 3 | BARE TOP-LEVEL JSON string | `"Cookie: …"` | there is no colon before the quote at all |
| 4 | ARRAY ELEMENT | `{"tags":["Cookie: …"]}` and `{"tags":["x","Cookie: …"]}` | the quote is preceded by `[` or `,`, not by `:` |

**Family 4 is the ONE this item named before the correction.** Families 1 and 2 are the ones that
carry the severity, and they are the reason it is no longer LOW.

**THE REACHABILITY, RE-DERIVED — and this is where the argument above breaks, not merely its
arithmetic.** The `For:` clause of Option A rests on *"no emission field this repository owns is a
JSON array of strings"*. That enumeration is correct and it answers the wrong question. **The carrier
is not a FIELD.** `mcp/schema/Serialization.kt` emits `HttpRequestResponse(request, response, notes)`,
and the whole raw HTTP RESPONSE — **body included** — goes out as the CONTENT of the `response`
string, copied verbatim from the target. A captured body that is itself JSON arrives with its inner
quotes escaped, which is **family 1, on the primary emission path**; one that pretty-prints is family
2; one returning header lines as an array of strings is family 4 — **all three inside the `response`
string, none of them a `List<String>` field.** Measured end to end: that carrier is IDENTICAL in and
out under STRICT and BALANCED, while the same carrier with `Set-Cookie: …` on the response's own
header block became `Set-Cookie: [STRIPPED]` in the same run, so the null result is attributable to
REACH. The same three families survive `McpToolExecutorImpl.kt:1018` `redact_preview` — **the one
tool whose purpose is to answer "would this leak?" now answers "no" for three of the four** — and
`ContextCollector.kt:52-53` on the PROMPT path, which is outside the MCP schema entirely. The
model-authored `argsJson` carrier the old enumeration named is still real, still UNMEASURED on its
remote half, and **was never the most reachable one.**

**SEVERITY: MEDIUM, raised from LOW.** Reachable with BURP-HELD traffic, in the DEFAULT posture,
under STRICT and BALANCED alike, with no opt-in precondition. **Not `high`:** no LIVE producer was
measured — what would move it is a measured instance of such a response body on real proxied traffic.

**THE MITIGATING BOUND THE MEDIUM RESTS ON, checked in all four families rather than repeated: only a
header that is the FIRST CONTENT of its string escapes.** A header that follows an escaped newline is
STILL STRIPPED — re-measured in the doubly-escaped nested form, the pretty form, the array form and
the bare top-level form, all four became `Cookie: [STRIPPED]`. So a realistic raw HTTP message
carried inside any of these four shapes is still redacted; what escapes is a string whose FIRST
content is a header line.

**OPTION A — ACCEPT at MEDIUM** (supersedes "ACCEPT at LOW" above). The residual stays filed, cited
in `Redaction.kt`, pinned from source by `LogicalLineBoundaryScopeTest.THIRD_OPEN_FINDING`, and owned
here.
*For:* the mitigating bound above is real and measured — only a first-content header escapes, so the
realistic raw-message shape is still covered; no live producer of the escaping shape has been
measured; and no widening has been measured against the benign corpus this quarter.
*Against:* it is MEDIUM, not LOW. It defeats STRICT **and** BALANCED on the plain canonical `Cookie:`
name with no variant spelling required, on this repository's own primary emission path, with
Burp-held traffic, in the default posture — and `redact_preview`, the tool a user would reach for to
check exactly this, reports clean for three of the four families.

**OPTION B — CORRECTED. As written above it closes ONE family of four, not the residual.** The text
above offers *"add `[\"` and `,\"` as two further FIXED-WIDTH lookbehind alternatives"* and describes
it as closing *"the residual at the control rather than in a record"*. **That is false under the
corrected mechanism and it overstates the fix by a factor of four.** Those two alternatives recognise
the bracket-quote and comma-quote opens, which is **family 4 only**. Family 1 interposes a backslash,
family 2 interposes a space, and family 3 has no preceding character to match — **all three remain,
and they are the two-plus-one that put this finding at MEDIUM and on the default-posture path.** If
Option B is chosen as written, it must be recorded as a PARTIAL fix that leaves `AR-27-11` open at
MEDIUM over three families, not as a closure.

**OPTION C — a widening that actually closes all four.** Stated so the maintainer is not choosing
between "accept" and a fix that closes a quarter of the finding. Derived from the four recorded
family spellings by counting, **not** measured against the engine or against the benign corpus:
covering all four needs look-back alternatives at **three different widths** — 2 for `["` / `,"`
(family 4), 3 for `:\"` (family 1) and `: "` (family 2), and 0 for a start-of-input anchor (family 3,
whose open quote is a bare quote at offset 0 and is therefore narrow rather than a re-introduction of
the 1589-of-1714 destruction). **The composer's fixed-width look-back argument — the measured 2.4x
this phase preserved deliberately — does not cover a mixed-width alternation, so Option C's
performance cost is UNMEASURED and is labelled as such rather than assumed small.** Like Option B it
must not be applied without its own red probe over the benign-payload corpus.

**THE ASYMMETRY, RESTATED AT THE CORRECTED WEIGHTS:** Option A leaves a MEASURED under-redaction on
this repository's OWN primary emission path, not on a model-authored side channel. Option B buys one
family of four. Option C risks an unmeasured over-redaction on every carrier AND an unmeasured
look-back cost. This phase's own history contains one costly example of an over-firing widening
(`WR-01`'s 32 measured false positives; the bare quote's 93% payload destruction) and, now, one of an
under-measured acceptance — which is why this is a person's call and not an executor's, and why the
call should be made against these numbers rather than the ones above the marker.

why_human: A privacy-boundary residual created by this round's own fix, MEDIUM, four measured
families, reachable with Burp-held traffic in the default posture under both redacting modes.
Accepting it sets a release posture on a shipped 1.0.0; closing it widens a redaction rule this phase
has twice measured over-firing, at a look-back cost nobody has measured. Neither is a defensible
harness default, and the choice must not be made from the superseded text above.
result: [pending]

---

## `AR-27-04` IS UNCHANGED BY ROUND 5, AND ROUND 5 DID NOT TOUCH IT

Recorded because a records round is exactly where a deferral quietly gets relitigated. `AR-27-04`'s
row in `26-SECURITY.md` is **byte-unchanged** by plans 27-14, 27-15 and 27-16. The finding is
unchanged, the severity is unchanged at MEDIUM, the status is unchanged at OPEN, and **the provenance
of its disposition — auto-selected by `mode: yolo`, not maintainer-chosen — is unchanged and is NOT
upgraded by anything round 5 did.** It is still owed, as item 9 above.

`AR-27-08` and `InjectionPointExtractor.kt:29` are likewise untouched and still owned by Phase 28, and
`T-27-06-06` is untouched.
