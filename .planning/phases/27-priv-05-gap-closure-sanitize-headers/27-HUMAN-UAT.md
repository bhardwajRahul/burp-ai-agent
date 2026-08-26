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
