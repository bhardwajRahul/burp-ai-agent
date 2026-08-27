# Phase 28 Planning Brief

Pre-extracted by the plan-phase orchestrator on 2026-08-27. The source records total ~536 KB;
this brief is the subset a planner needs. Cited paths are authoritative — re-read a specific
region if a decision turns on it, but do NOT read 26-SECURITY.md whole (246 KB, one 38 KB row).

## 1. Phase 28 ROADMAP section (verbatim)

### Phase 28: The Issue-Detail Cookie Carrier — `AuditIssue.detail()` → `scanner_issues`

**Goal:** Close `AR-27-08`. A COOKIE-typed injection point's value reaches the `scanner_issues` MCP
tool result through `AuditIssue.detail()` and **survives `Redaction.apply` in STRICT and in
BALANCED**, emitted verbatim — measured by plan 27-08 with a positive control that fired on the same
payload (a real `Cookie:` header in the same `IssueDetails` object became `Cookie: [STRIPPED]` in the
same STRICT output in which the detail-line sentinel survived). **This is the one finding in the
phase-27 series that carries BURP-HELD proxied traffic** — a real session cookie the operator's
browser sent — rather than caller-echoed content, and there is no privacy mode in which the field is
protected. **Mechanism, already measured so this phase does not have to re-derive it:**
`IssueUtils.formatIssueDetailHtml` (`util/IssueUtils.kt:51-63`) joins `detailLines` with `<br>`, so
the blob carries **no newline at all** and the logical-line cookie rules have nothing to bind to; the
rendered shape is `Original Value: <value>`, not `name=<value> (COOKIE)`, so `cookieTypedParamRegex`
cannot key on it; and the enclosing JSON key is `detail`, which is not in `SENSITIVE_WORDS`.
**Reachability, cited at source:** the write is unconditional and NOT privacy-mode gated
(`scanner/ActiveAiScanner.kt:1239`); a confirmation is required first (`:1172-1176`, `:1183`); the
mode is Active AI scanning, opt-in and defaulting to `false` (`config/AgentSettings.kt:127`); a
COOKIE-typed point CAN reach that line because the target loop filters on vuln CLASS only, never on
`point.type` (`:232-246`, `:1684`); and it leaves via `detail = detail()` at
`mcp/schema/Serialization.kt:14`.

**This phase closes `AR-27-08` AND `InjectionPointExtractor.kt:29` TOGETHER, and that pairing is the
point.** That file's line 29 writes its own cookie-parameter predicate,
`request.parameters().filter { it.type().name == "COOKIE" }`, and was left **byte-unchanged** by both
plan 27-07 (baseline B9) and plan 27-08 — deliberately, not by omission. Its two consumers differ:
`AdaptivePayloadEngine.kt:52` is CONTROLLED (it substitutes `[REDACTED_VALUE]` under any non-`OFF`
mode), while `ActiveAiScanner.kt:1239` is UNCONTROLLED and is this finding. **Converting the
predicate alone would produce a tidier file and an unchanged leak** — it would make the route LOOK
addressed, which is the exact failure mode `26-SECURITY.md` T-26-02-01 now records three times. The
predicate is only meaningful as part of the route it feeds.

**Why this was deferred out of phase 27 rather than fixed there:** plan 27-08's `T-27-08-06` was
dispositioned **TRANSFER, not mitigate** — that plan MEASURED the route and applied no control to it,
and calling a measurement a mitigation is the overclaim vocabulary phase 27 exists to correct. A fix
needs its own red probe and its own reachability analysis, which is closure-phase work.

**Requirements**: PRIV-05
**Depends on:** Phase 27
**Success Criteria** (what must be TRUE):

1. A COOKIE-typed injection point's `originalValue` does not appear in the `scanner_issues` tool
   result in STRICT or BALANCED. Cookie NAMES may remain; VALUES must not.

2. Under `OFF` the value still appears — so the fix is proven to be policy-driven and not an
   unconditional rewrite.

3. A red probe reverting the control turns a NAMED assertion red, and the specific assertion and its
   failure message are recorded — not "the suite went red".

4. `InjectionPointExtractor.kt:29` is resolved in the same phase as the route, with its two
   consumers' differing dispositions preserved (`AdaptivePayloadEngine.kt:52` already controls its
   own path and must not be double-redacted into a misleading prompt).

5. `26-SECURITY.md`'s `AR-27-08` row is amended — append-and-amend, prior text byte-prefix intact —
   and `threats_open` is recomputed rather than asserted.

6. `ResponseAnalyzer`'s narrow transitive tail is examined in the same pass: a MATCHED substring of a
   vuln-class signature, capped at 80 chars, can be written into `VulnConfirmation.evidence`, which
   `ActiveAiScanner.kt:1246` places in the SAME `AuditIssue` detail as this finding.

**Not in scope, named so it is not silently absorbed:** `AR-27-07` (non-cookie parameter types,
measured low) is a separate disposition and is routed to `27-HUMAN-UAT.md` test 8, not to this phase.

---

## Progress

## 2. AR-27-08 — the finding this phase closes (register row, verbatim)
```
| AR-27-08 | T-26-02-01 | **NEW, OPEN, severity MEDIUM — MEASURED, with a firing positive control on the SAME payload, and the one finding in this series that carries BURP-HELD DATA.** A COOKIE-typed injection point's value reaches the `scanner_issues` tool result through `AuditIssue.detail()` and **SURVIVES `Redaction.apply` in STRICT and in BALANCED alike**, emitted verbatim (`27-08-SUMMARY.md`, measurement 2, full probe output quoted there). **The positive control fired in the very same output:** a real `Cookie:` header carried in `requestResponses[0].request` of the SAME `IssueDetails` object became `Cookie: [STRIPPED]` in the same STRICT run in which the detail-line sentinel survived — one object, one call, one output, one field controlled and one not, so the two are directly comparable and the null result is attributable to REACH. **Mechanism, measured:** `IssueUtils.formatIssueDetailHtml` (`util/IssueUtils.kt:51-63`) joins `detailLines` with `<br>`, so the blob contains **no newline at all** and the logical-line cookie rules have nothing to bind to; the rendered shape is `Original Value: <value>`, not `name=<value> (COOKIE)`, so `cookieTypedParamRegex` cannot key on it; and the enclosing JSON key is `detail`, which is not in `SENSITIVE_WORDS`. **REACHABILITY, cited at source for every clause — this is the difference between a live leak and a latent one, and this phase has twice recorded a finding at the wrong severity for want of it.** The write is NOT privacy-mode gated (`scanner/ActiveAiScanner.kt:1239`, unconditional). A confirmation IS required first (`:1172-1176`, `:1183`). The mode required is Active AI scanning, which is **opt-in and defaults to `false`** (`config/AgentSettings.kt:127`, also `:391`, `:520`; wired at `App.kt:182`). A COOKIE-typed injection point CAN reach that line — the target loop filters on vuln CLASS only, never on `point.type` (`:232-246`, `:1684`; points created at `scanner/InjectionPointExtractor.kt:29`). It leaves the machine via `detail = detail()` at `mcp/schema/Serialization.kt:14`, which the `scanner_issues` MCP tool emits. **SEVERITY MEDIUM, neither rounded up nor down, with both properties in one breath.** AGGRAVATING, and strictly worse than AR-27-07: this carries **Burp-held proxied traffic** — a real session cookie the operator's browser sent — which the AI backend did not previously possess; and it defeats STRICT outright, there being no mode in which this field is protected. MITIGATING: it is **LATENT**, behind three independent preconditions — the opt-in active scanner switched on, a finding reaching `confirmed`, and a `scanner_issues` call being made. Not `high` because it is unreachable in the default posture; not `low` because when it IS reachable a real session cookie crosses the trust boundary in STRICT. **MEASURED AND DELIBERATELY NOT FIXED.** Plan 27-08 applied NO control to this route — `T-27-08-06`'s disposition stayed **TRANSFER**, not mitigate, because calling a measurement a mitigation is the overclaim vocabulary this record set exists to correct. **`scanner/InjectionPointExtractor.kt:29` is deferred WITH this route and must be closed by the SAME successor:** its cookie-type predicate is byte-unchanged, and converting it alone would produce a tidier file and an unchanged leak. The named successor is **Phase 28** in `ROADMAP.md`; a deferral without an owner is round four, pre-arranged. | Measured by plan 27-08 (disposition TRANSFER, not mitigate); filed by plan 27-09 with a named successor | 2026-08-25 |
```

## 3. Open findings that bear on the PRIV-05 judgement

| id | severity / status | one-line |
|----|-------------------|----------|
| AR-27-04 | see row |  AR-27-04 T-26-02-01 **NEW, OPEN, severity MEDIUM.** The `Host:` header value inside the raw HTTP message AND the sibling `SiteMapEntry.url` field both reach an AI backend **un-anonymised under STRICT |
| AR-27-07 | see row |  AR-27-07 T-26-02-01 **NEW, OPEN, severity LOW — MEASURED, with a firing attribution control.** A sensitive-NAMED parameter of a NON-COOKIE type survives `request_parse`'s serialized JSON in STRICT ** |
| AR-27-09 | see row |  AR-27-09 T-26-02-01 **NEW, OPEN, severity LOW — MEASURED, and MEASURED ONE MODE WIDER THAN THE ROUND-3 RECORD PREDICTED.** The FOURTH logical-line start `logicalLineHeaderRule` still cannot recognise |
| AR-27-10 | see row |  AR-27-10 T-26-02-01 **NEW, OPEN, severity LOW — the residual difference set between the bare-contains PREDICATE and the widened NAME CLASS, and the record the underscore class never had.** **MECHANIS |
| AR-27-11 | see row |  AR-27-11 T-26-02-01 **NEW, OPEN, severity LOW — the residual bought by round 5's narrowing, MEASURED in both directions and with its REACHABILITY measured this round rather than assumed.** **MECHANIS |

Full rows: `grep '^| AR-27-0X ' .planning/phases/26-coverage-static-analysis-debt-docs/26-SECURITY.md`. Sizes: AR-27-04 2.6K, AR-27-07 2.0K, AR-27-09 10.4K, AR-27-10 4.1K, AR-27-11 15.4K.

**Dispositions recorded by the maintainer during Phase 27 UAT (2026-08-26/27):**
- AR-27-04 — MAINTAINER-SIGNED ACCEPTANCE at MEDIUM. Host:/SiteMapEntry.url still reach an AI backend un-anonymised under STRICT on the serialized emission shape. Behaviour SHIPS.
- AR-27-07 — KEPT AT LOW. SENSITIVE_WORDS not widened (WR-01's measured 32-false-positive cost).
- AR-27-08 — ACCEPTED AT MEDIUM, Phase 28 owns the fix. **Chain CONFIRMED LIVE in a real Burp** (UAT item 6): all three preconditions composed, a COOKIE-typed value reached `detail` while the sibling `requestResponses[0].request` showed `Cookie: [STRIPPED]`. No interim warning was added to the active-scanner path.
- AR-27-09 — CLOSED BY FIX (commit c883947, plan 27-17). REAL_LINE_START = "^[ \t]*+".
- AR-27-10 — ACCEPTED AT LOW. 13 RFC 9110 tchars still uncovered; both halves remain inferred.
- AR-27-11 — ACCEPTED AT MEDIUM over FOUR measured families. JSON_STRING_OPEN = ":\"" is literally colon-then-quote, so any interposed character (a space in pretty-printed JSON, a backslash in an escaped nested body) or no colon at all is not a recognised logical-line start.

## 4. Standing rules that bind your plans (26-SECURITY.md)

Extracted rather than paraphrased where short. The clause block lives in `.planning/phases/26-coverage-static-analysis-debt-docs/26-SECURITY.md`; grep 'clause (vi' etc. to re-read one.

- **APPEND, NEVER REWRITE.** An amended register row must preserve its earlier text as a BYTE-EXACT PREFIX, verified programmatically with character counts reported, and carry a supersession marker rather than a deletion. (SC5 requires this for AR-27-08.)
- **`threats_open` is RECOMPUTED** by the documented awk, never hand-edited, raw output reported. `AR-` rows sit OUTSIDE that counter's population at any severity — state it rather than implying it.
- **(vi) A stated bound must match the control it describes.** Where a number is source-derivable, a TEST must derive it. Evidence: the one count in clause (vi) that was machine-checked did not drift; both that were prose did, inside a single round, in that round's last commit.
- **(vii) A residual list must separate what the round INTRODUCED from what it INHERITED**, visibly.
- **(viii) A record is not written once.** (a) correction fan-out — `grep -rn '<finding-id>'` IS the propagation list and the register's OWNER field names the decision venue; a correction commit touching only the register is incomplete. (b) count re-measurement — a commit that changes a control must re-derive any number describing it. An out-of-plan fix is NOT exempt.

## 5. The measured mechanism (from 27-08-SUMMARY.md — do NOT re-derive)

`IssueUtils.formatIssueDetailHtml` (`util/IssueUtils.kt:51-63`) joins `detailLines` with `<br>`, so the blob carries **no newline at all** and the logical-line cookie rules have nothing to bind to. The rendered shape is `Original Value: <value>`, not `name=<value> (COOKIE)`, so `cookieTypedParamRegex` cannot key on it. The enclosing JSON key is `detail`, absent from `SENSITIVE_WORDS`.

Measured by plan 27-08 with a FIRING positive control on the same payload: a real `Cookie:` header in the same `IssueDetails` object became `Cookie: [STRIPPED]` in the same STRICT output in which the detail-line sentinel survived verbatim. Dispositioned **TRANSFER, not mitigate** — 27-08 measured the route and applied no control, and calling a measurement a mitigation is the overclaim vocabulary phase 27 exists to correct.

**Reachability, cited at source:** the write is unconditional and NOT privacy-mode gated (`scanner/ActiveAiScanner.kt:1239`); a confirmation is required first (`:1172-1176`, `:1183`); the mode is Active AI scanning, opt-in defaulting to `false` (`config/AgentSettings.kt:127`); a COOKIE-typed point CAN reach that line because the target loop filters on vuln CLASS only, never on `point.type` (`:232-246`, `:1684`); it leaves via `detail = detail()` at `mcp/schema/Serialization.kt:14`.

## 6. PRIV-05 status — read before writing any requirements field

PRIV-05 is `- [ ]` in REQUIREMENTS.md and has been WRONGLY CLOSED FOUR TIMES and re-opened FIVE. Phase 27 closed with it deliberately open. `REQUIREMENTS.md` sha256 is `9b3219662ec0d007…`; it must be byte-unchanged unless a plan makes an explicit, enumerated argument that every carrier is clean.

On 2026-08-27 `gsd-tools query phase.complete 27` FLIPPED PRIV-05 to `[x]` as a side effect while its own warnings said it was skipping that write. It was caught pre-commit and reverted (WINDOWS entry 54). **Do not let a plan close PRIV-05 by default.**
