# Phase 28: The Issue-Detail Cookie Carrier - Context

**Gathered:** 2026-08-27
**Status:** Ready for planning
**Round:** GAP CLOSURE (round 2) — captured at the `/gsd-plan-phase 28 --gaps` scope gate, not at
`/gsd-discuss-phase`. Phase 28 executed 3 plans, then `28-VERIFICATION.md` returned `gaps_found`
5/6 with SC1 measured false. These decisions scope the gap round only; they do not reopen or
supersede D-28-01 through D-28-04, which stand.

<domain>
## Phase Boundary

Unchanged from ROADMAP.md: close `AR-27-08` — a COOKIE-typed injection point's value must not reach
the `scanner_issues` MCP tool result through `AuditIssue.detail()` under STRICT or BALANCED — and
resolve `InjectionPointExtractor.kt:29` in the same phase.

**What the gap round changes is the measured EXTENT of that boundary, not the boundary itself.**
Round 1 controlled exactly one detail line at one producer. `28-VERIFICATION.md` measured two
further routes carrying the same bytes to the same tool result. Both are instances of `AR-27-08` as
the ROADMAP defines it, so both are inside the existing boundary — they are not scope growth.

</domain>

<decisions>
## Implementation Decisions

### Gap round scope

- **D-28-05:** Gap closure covers **BOTH** measured leak routes:
  - **Route 1 (CR-01)** — `ScannerIssueSupport.kt:121`, the `Payload Used:` line, which re-leaks
    the value line 120 just stripped, because for a COOKIE point the payload is DERIVED FROM the
    cookie value (`PayloadGenerator.kt:782` builds `"$originalValue' AND '1'='1"`; also `:762`,
    `:771`, `:791`).
  - **Route 2 (CR-02)** — `AiScanCheck.buildDetail` (`AiScanCheck.kt:353`, `:357`), a SECOND
    active-scan detail producer that reads no privacy mode at all and therefore leaks in STRICT,
    BALANCED and OFF alike. Live-registered at `App.kt:215`; reaches the tool via
    `McpToolExecutorImpl.kt:604`.

  Rationale for including route 2 despite it appearing in no phase-28 plan: `AR-27-08` is defined
  by the ROADMAP as the cookie-value→`scanner_issues`→`AuditIssue.detail()` carrier. `AiScanCheck`
  is that carrier. Deferring it would leave `AR-27-08` open while the register says it is closed —
  the exact overclaim vocabulary phase 27 exists to correct.

- **D-28-06:** The **repo-wide single-producer gate was considered and NOT taken** as gap scope.
  `WR-01` is real — the gate at `IssueDetailCookieCarrierTest.kt:625-632` filters only the list
  `buildActiveIssueDetailLines` itself returned and is structurally incapable of seeing another
  file — but building a repo-wide enforcement mechanism is a separate concern from closing the
  measured leak. **This is a named residual, not an oversight.** Record it as such; do not let a
  plan quietly satisfy it and do not let a plan claim the gate is enforced when it is not.

### How the payload line is controlled

- **D-28-07:** `Payload Used:` is controlled **TYPE-KEYED**, exactly as `Original Value` already
  is: when `point.type == InjectionType.COOKIE` and the policy is non-`OFF`, the payload is
  replaced wholesale with the shared stripped marker. Cookie-point payload diagnostics are given up
  deliberately.

  Shape-keyed excision — substituting the embedded `originalValue` substring inside the payload to
  preserve `[STRIPPED]' AND '1'='1` — was **considered and REJECTED**. It contradicts the discipline
  `ScannerIssueSupport`'s own KDoc states in its own words ("TYPE-KEYED, never shape-keyed. The
  decision is taken on `InjectionType.COOKIE`, a member of a closed enum, so no reformatting of the
  detail line can defeat it"), and it is defeated by any payload that encodes or transforms the
  value.

  Sanitising the payload for ALL injection types was likewise **REJECTED** — it would reopen
  D-28-01, which deliberately lets every non-COOKIE type pass through.

### Prose that must be corrected, not left standing

- **D-28-08:** Three sites currently assert a closure that does not hold, and a fix that leaves them
  standing ships a record that contradicts the code:
  - `ScannerIssueSupport.kt:32-33` — "the payload is agent-authored, not operator traffic". FALSE
    for context-aware payloads; this premise is what caused route 1 to be skipped.
  - `ScannerIssueSupport.kt:74-75` — "THE ONLY PRODUCER OF THE ACTIVE-SCAN ISSUE DETAIL LINES IN
    THE REPOSITORY". FALSE; `grep -rn "Original Value" src/main/kotlin/` returns two.
  - `CookieCarrierInventoryTest.kt:407` and the amended `AR-27-08` cell in `26-SECURITY.md:315` —
    both assert closure while a third consumer was uncontrolled.

  `AR-27-08`'s register cell is amended **append-and-amend under a dated supersession marker**,
  prior text byte-prefix intact — the same protocol 28-03 used. It is never rewritten.

### Claude's Discretion

- Plan count, wave grouping and task decomposition.
- Whether route 1 and route 2 are separate plans or one — they touch different files
  (`ScannerIssueSupport.kt` vs `AiScanCheck.kt`), so they may parallelise, but the shared marker
  vocabulary must come out identical.
- The exact red-probe shape per route, subject to SC3: a NAMED assertion and its verbatim failure
  message must be recorded, never "the suite went red".

</decisions>

<constraints>
## Carried Constraints (not decisions — facts the plan must respect)

- **PRIV-05 stays `- [ ]`** in `.planning/REQUIREMENTS.md` (D-28-04). The file is byte-unchanged at
  sha256 `9b3219662ec0d007c1c82d64eed3ef2698bd306ce69f01205ac9bbc3f42fcfb4`. Do not edit it.
  Whether this gap round finally justifies ticking it is a judgement for the round's own gate, not
  a plan-authoring liberty.
- **`AdaptivePayloadEngine.kt:52` must stay byte-unchanged.** It already controls its own path
  (`[REDACTED_VALUE]` under any non-`OFF` mode); double-redacting it produces a misleading prompt.
  SC4 is verified and must not regress.
- **English only** in code and comments (AGENTS.md, non-negotiable).
- **Build requires JDK 21**: `JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew …`.
- `./gradlew check` is RED for a maintainer-accepted reason (redact BRANCH 0.92784 vs a 0.930
  floor). Do not treat it as a gate and do not adjust the floor.
- `RedactionTest > windowedScanRedactsJsonPairAcrossEveryBoundaryAlignment()` is a known wall-clock
  flake under CPU load (SafeRegex 50 ms deadline; fails fail-closed, not leak-open). Not a
  regression.

</constraints>

<already_known>
## Known and already recorded — do not re-file as new findings

- `CookieCarrierInventoryTest.kt:539` states the evidence-tail bound as a singular "capped at 80
  characters" when the derived truth is the multiset `{80, 80, 60}`, and cites
  `ActiveAiScanner.kt:1246` when the live line is `:1242`. Deferred by 28-03 as outside its
  `files_modified`. In scope to fix here only if a plan already touches that file.
- One `@Disabled` test in `ExternalMcpClientManagerTest` — pre-existing, unrelated.
- `WR-05` (`CookieRouteDispositionTest.kt:286`) mixes `"src/main/kotlin"` with `File.separator` and
  fails on Windows. Cross-platform is a stated project constraint, so this is a real defect.
- `AR-28-01` (ResponseAnalyzer evidence tail, MEDIUM, DERIVED) ships as a named residual by
  maintainer decision at 28-03's blocking checkpoint. Not reopened here.

</already_known>

---

## Round 3 addendum — the SC1 disposition (2026-08-28)

Captured at the `verify_phase_goal` gate after gap round 2. Round 2 closed all seven round-1 gaps;
`28-VERIFICATION.md` returned `gaps_found` 5/6 with SC1 adjudicated **(b) not satisfied** on a
narrower and different mechanism than round 1.

- **D-28-09:** The **write-time/read-time bound is ACCEPTED as a NAMED RESIDUAL**, by maintainer
  answer on 2026-08-28. Not an auto-advance default; the question was put and answered.

  What is accepted: both controls decide once at issue construction and bake the result into
  `AuditIssue.detail()`, an immutable string Burp stores and `scanner_issues` replays. An issue built
  while `privacyMode = OFF` emits the raw cookie value on a later STRICT read.
  `AiScanCheck.consolidateIssues` returns `KEEP_EXISTING`, so a re-scan does not repair the site map,
  and `Redaction.apply` provably cannot rescue it (28-05's own red probe recorded the sentinel
  surviving STRICT redaction verbatim when the write gate does not fire).

  Why accepted rather than fixed here: every issue *produced* under STRICT or BALANCED — the entire
  default posture, `AgentSettings.kt:493` defaults to `BALANCED` — is measurably clean across all four
  detail lines of both producers. The residual requires a deliberate OFF scan followed by a mode
  switch: the same latent, opt-in reachability profile that put `AR-27-08` at MEDIUM rather than high.
  A read-time fix is new architecture on the emission path, not a patch — `Redaction.apply` cannot
  match the rendered `Original Value: <value>` shape today (no newline for the logical-line rules to
  bind to, and `cookieTypedParamRegex` cannot key on it). That belongs in its own phase.

- **D-28-10:** The acceptance is **CONDITIONAL**, on the verifier's own terms. The override may only
  be applied to `28-VERIFICATION.md` frontmatter once ALL of the following are true. Until then
  `phase.complete 28` stays blocked:
  1. The temporal bound is named in `ISSUE_DETAIL_CARRIER_DISPOSITION`'s "STILL OPEN" clause and in
     `26-SECURITY.md` row 315 clause (d) — **append-and-amend under a dated marker, prior 8693-byte
     prefix byte-intact** (`8dc326ac23204becce687deeba867740eb2d4dde21346c58d7da9595d137ae2e`).
  2. It is noted at `AiScanCheck.consolidateIssues`, whose `KEEP_EXISTING` makes the stale issue
     sticky against re-scan.
  3. It is surfaced next to the privacy-mode selector. `SettingsPanelInit.kt:58`'s current tooltip
     ("Controls how traffic is redacted before sending to a model") invites the retroactive reading
     that is false. **Shipping a control an operator will read as retroactive is the option that is
     not available.**

- **D-28-11:** Gap 2 is **NOT** covered by the override and is not a judgement call — it is prose
  asserting something the tree does not support, which D-28-08 already governs:
  - `ISSUE_DETAIL_CARRIER_DISPOSITION` and row 315 both name `AiScanCheckDetailCookieCarrierTest` as
    the committed probe for detail line (4), the `**Payload Used:**` line. `grep -c "Payload Used"`
    on that file returns **0** — KDoc included. Prefer making the claim TRUE (add the assertions)
    over retracting it; retract only if the assertions cannot be written honestly.
  - `WR-01`'s fail-open type set is unrecorded. Measured at source by `javap` on the resolved
    `montoya-api-2026.2.jar`: `AuditInsertionPoint.type()` is a DEFAULT method whose entire body is
    `getstatic AuditInsertionPointType.EXTENSION_PROVIDED; areturn`, and the enum also carries
    `USER_PROVIDED` and `HEADER`. Unlike route 1's `InjectionType` — whose only cookie-capable member
    IS `COOKIE`, which is what made D-28-01's pass-through safe by construction — this enum has
    cookie-capable non-`PARAM_COOKIE` members. Either widen the predicate to a set or name the
    residual, but `anAbsentInsertionPointTypeDoesNotThrowAndPassesThrough`'s KDoc currently asserts
    the opposite premise ("a real Burp implementation may not override it" -> null), which is FALSE
    against the shipped jar and must be corrected either way.

- **PRIV-05 stays `- [ ]`.** D-28-04 stands and the verifier judged it *more* consistent with the code
  after gap 1, not less. `.planning/REQUIREMENTS.md` stays byte-unchanged at
  `9b3219662ec0d007c1c82d64eed3ef2698bd306ce69f01205ac9bbc3f42fcfb4`.
