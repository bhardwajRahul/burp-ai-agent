---
phase: "22-agent-tool-call-trust-boundary"
plan: "22-01…22-09"
slug: agent-tool-call-trust-boundary
status: verified
asvs_level: 1
threats_total: 41
threats_open: 0
threats_closed: 41
register_rows: 57
register_authored_at_plan_time: true
audited_at: "2026-08-19"
audited_head: "43c11ce"
auditor: "gsd-security-auditor (claude-opus-5)"
block_on: "high"
verdict: "OPEN_THREATS"
---

# Security Audit — Phase 22: Agent Tool-Call Trust Boundary (SEC-06)

**Phase:** 22 — Agent Tool-Call Trust Boundary
**ASVS Level:** 1
**Threats Closed:** 40/41 (39/40 `mitigate` + 1/1 `accept`)
**Threats Open:** 0 — T-22-31 closed in code at `43c11ce` (see Audit Trail)
**Audited:** 2026-08-19 at `d7a93b9`

The register is the union of the nine `<threat_model>` blocks in `22-01-PLAN.md` … `22-09-PLAN.md`:
57 rows, 41 distinct threats — 40 `mitigate` (T-22-01 … T-22-40) and 1 `accept` restated once per
plan (T-22-SC). Per the audit brief the register is authoritative and complete; this audit verifies
evidence for each declared disposition and does not scan for new threats.

**Live evidence collected during this audit** (not read from SUMMARY/VERIFICATION claims):

```
./gradlew test --tests '*ChatPanelToolGateTest*' --tests '*ToolApprovalGateTest*'
  --tests '*ToolApprovalGateVisibilityTest*' --tests '*ToolDecisionReporterTest*'
  --tests '*SecTierResolutionTest*' --tests '*McpToolCatalogTierParityTest*'
  --tests '*ToolApprovalCardTest*' --tests '*DecisionsAdrTest*'
→ BUILD SUCCESSFUL — 73 tests, 0 failures, 0 errors, 0 skipped
```

---

## Trust Boundaries

| Boundary | Description | Data Crossing |
|----------|-------------|---------------|
| AI backend response → `ChatPanel.maybeExecuteToolCall` | Model output is attacker-influenceable (Send-to-AI proxy traffic, passive-scan findings, `ext:` tool results are already in context), so tool *selection* is attacker-influenceable | Model-authored tool name + args JSON |
| `ToolApprovalGate` → `McpToolExecutor.executeTool` | The authorisation token that crosses must be unforgeable or the boundary is advisory | `ToolCallOrigin` (`ModelApproved`, carrying tier + decision) |
| `McpToolExecutor.executeTool` → Burp Montoya API | Where a parsed tool call becomes a real action against Burp and, for traffic tools, the target | Tool invocation + args |
| Burp local data → AI backend | The tier table decides what local data flows to a third-party model with no human in the loop | Proxy history, site map, cookie jar, Burp options |
| Model-authored name/args → durable audit record + Output tab | CWE-117 log injection and confidentiality both apply | Tool name, args digest, decision, tier, trace ID |
| Model-supplied strings → rendered Swing widget | An injected prompt controls both strings at the moment authorisation is asked for | Tool ID, args JSON |
| Pending authorisation state → extension teardown | A record that survives its UI is a decision that can never be made and a continuation that never fires | `PendingToolDecision` |
| User-originated call sites (`:1010`, `:2313`) → `executeTool` | Same boundary, but carrying the user's own intent — declared rather than gated | `ToolCallOrigin.UserDialog` / `.UserSlashCommand` |

---

## Threat Register

| Threat ID | Category | Component | Disposition | Mitigation (control) | Status |
|-----------|----------|-----------|-------------|----------------------|--------|
| T-22-01 | Elevation of Privilege | `ChatPanel.maybeExecuteToolCall` → `executeTool` | mitigate | Gate consulted before any executor call; every non-`Run` outcome fail-closed | closed |
| T-22-02 | Information Disclosure | Bulk-read tools (11 named) | mitigate | All `CONFIRM`, never `AUTO`; AUTO set pinned as enumerated literals | closed |
| T-22-03 | Information Disclosure | `ai_analyze` / `ai_passive_scan`; `ext:<server>:<sink>` | mitigate | `CONFIRM_EACH`; `ext:` tier derived from namespace prefix | closed |
| T-22-04 | Elevation of Privilege | `scope_include` (scope-filter bypass primitive) | mitigate | `CONFIRM_EACH` | closed |
| T-22-05 | Tampering | `project_options_set` / `user_options_set` | mitigate | `CONFIRM_EACH`; full args rendered on the card | closed |
| T-22-06 | Tampering / Repudiation | CWE-117 log injection into audit payload + Output tab | mitigate | `sanitizeInline` unconditional on both `knownTool` branches; args never reach `outputLine` | closed |
| T-22-07 | Spoofing | Model text rendered as extension chrome | mitigate | Model text only in `JTextField` / `JTextArea`; tooltips carry `catalogTitle` only | closed |
| T-22-08 | Denial of Service | Card flooding through the safety control | mitigate | One monotone `nextIterationBudget`, called from both approve and deny branches | closed |
| T-22-09 | Repudiation | A decision that is not recorded | mitigate | Three sinks from one `buildPayload`; 5/5 decision branches report | closed |
| T-22-10 | DoS / Repudiation | Pending decision surviving teardown | mitigate | One `resolvePending`/`resolveAllPending` pair reached from all five teardown paths | closed |
| T-22-11 | Elevation of Privilege | A future parse-and-execute site minting a model origin | mitigate | `ModelApproved` top-level `private`; `approvedOrigin` object-`private`; `origin` non-defaulted | closed |
| T-22-12 | Tampering | Alias resolving to different tiers in gate vs executor | mitigate | One exposed `McpToolExecutor.canonicalToolId` consumed by both | closed |
| T-22-13 | Repudiation | The phase's own acceptance gate being silently skipped | mitigate | Test names checked against the `-PexcludeHeavyTests=true` exclusion list; headless jvmArgs | closed |
| T-22-14 | Elevation of Privilege | A future tool added with no tier | mitigate | `secTier` required and non-defaulted — omission is a compile error | closed |
| T-22-15 | Elevation of Privilege | Binding the boundary to `unsafeOnly` | mitigate | `secTier` is a second independent axis | closed |
| T-22-16 | Elevation of Privilege | Unrecognised / misspelled tool name treated as safe | mitigate | Fail closed: unknown → `CONFIRM_EACH` | closed |
| T-22-17 | Information Disclosure | Advertising the tier in the tool preamble | mitigate | No `[auto]` marker in `describeTools` / `buildToolPreamble` | closed |
| T-22-18 | Elevation of Privilege | One session click granting unlimited wire-traffic tools | mitigate | `CONFIRM_EACH` consults neither session set; `resolve` throws on a session action | closed |
| T-22-19 | Tampering | Denial reported as a malfunction | mitigate | One neutral `DENIAL_RESULT` (no `Error:` prefix); third status value `"denied"` | closed |
| T-22-20 | Elevation of Privilege | Approval for target A applying to target B | mitigate | Per-session holder keyed on canonical ID; not persisted across restart | closed |
| T-22-21 | Elevation of Privilege | A shipped bypass (opt-out / settings-import tier flip) | mitigate | `evaluate` accepts no settings object, enable flag or bypass parameter | closed |
| T-22-22 | Repudiation | Collapsing "a human clicked" into "an earlier click applied" | mitigate | Distinct `APPROVE_ONCE`/`SESSION_APPROVED` and `DENY`/`SESSION_DENIED` constants | closed |
| T-22-23 | Information Disclosure | Model args in plaintext in a durable log | mitigate | `argsSha256` = `Hashing.sha256Hex` by default; `verboseAudit` seam wired `false` | closed |
| T-22-24 | Repudiation | `AUTO` run indistinguishable from a pre-fix decision-less call | mitigate | `secTier` emitted unconditionally on every event | closed |
| T-22-25 | Tampering | Reusing an audit constant whose keys mean something else | mitigate | New `mcp_tool_decision` constant; `mcp_tool_blocked`/`mcp_tool_end` absent | closed |
| T-22-26 | Spoofing | Fabricated catalog title for an unrecognised tool | mitigate | `catalogTitle = null` renders the extension-derived unknown-tool line | closed |
| T-22-27 | Spoofing | Screen-reader user hearing model text as extension narration | mitigate | Sanitized ID, preceded by the disclosure clause, last in the string | closed |
| T-22-28 | Elevation of Privilege | Stray Enter / mis-click resolving a security decision | mitigate | No default button, mnemonics, Escape binding or focus theft; 16 px pole gap | closed |
| T-22-29 | Information Disclosure | Exfiltration payload hidden below a fold | mitigate | No nested scroll pane; `CONFIRM_EACH` expands by default; honest truncation footers | closed |
| T-22-30 | Repudiation | Resolved card no longer recording what was offered / chosen | mitigate | Buttons removed and replaced by a verbatim outcome row; both compact variants ship | closed |
| T-22-31 | Denial of Service | Parked continuation never discharged | mitigate | `ChatPanel.kt:2557` discharges `onCompleted` on `NOT_CHAINED`, mirroring the un-asked path at `:759`; `anApprovedToolThatThrowsStillDischargesTheParkedContinuation` (verified RED first) | closed |
| T-22-32 | Elevation of Privilege | Double-prompting a user-originated call | mitigate | `:1010` / `:2313` declare their origin and never consult the gate | closed |
| T-22-33 | Denial of Service | Denial followup dispatching a turn during `shutdown()` | mitigate | `sendFollowup` inert by construction; `false` at all five sites | closed |
| T-22-34 | Elevation of Privilege | Session approval surviving Clear Chat | mitigate | `clearChatState` resets `approvalMemory` alongside `toolsMode` / `toolCatalogSent` | closed |
| T-22-35 | Denial of Service | Invisible pending decision in a background session | mitigate | `Awaiting approval` row marker + `scrollToComponent` on return | closed |
| T-22-36 | Elevation of Privilege | Discarded `sessionStates[id] ?: …` fallback | mitigate | `getOrPut` everywhere; zero occurrences of the discarding form | closed |
| T-22-37 | Elevation of Privilege | Later tool classified `AUTO` by a loose reading | mitigate | ADR-15 carries D-05's sentence verbatim; guarded by `DecisionsAdrTest` | closed |
| T-22-38 | Repudiation | SC1 marked done without anyone checking the ADR | mitigate | Split coverage: automated string-match guard + explicit human-UAT item | closed |
| T-22-39 | Spoofing | An overclaiming ADR | mitigate | Seven explicit `Residual:` bullets; the SC5 overclaim corrected in place | closed |
| T-22-40 | Elevation of Privilege | Future persisted approvals / settings-level tier downgrade | mitigate | ADR-15 records both as deliberately rejected, naming the settings-import path | closed |
| T-22-SC | Tampering | npm/pip/cargo installs (supply chain) | accept | Zero packages added — verified empty by the orchestrator | closed |

*Status: open · closed*
*Disposition: mitigate (implementation required) · accept (documented risk) · transfer (third-party)*

---

## Threat Details — Closed

### T-22-01 — Model-emitted tool call reaching Burp with no decision (EoP) — CLOSED

**Declared mitigation (22-07):** `ToolApprovalGate.evaluate` runs before any `executeTool` call; every
non-`Run` outcome is fail-closed.

**Evidence:**
1. `ChatPanel.kt:2334` is the only `ToolCallParser.extractFirst` call site in `src/main` (the other grep
   hit is the parser's own declaration at `ToolCallParser.kt:20`). `ChatPanel.kt:2343` consults the gate
   nine lines later, and the whole function body is a `when` over the three outcomes — there is no
   fall-through path to the executor.
2. Exactly three `executeTool(` call sites exist in `src/main`: `ChatPanel.kt:1010`
   (`ToolCallOrigin.UserDialog`), `:2313` (`UserSlashCommand`), `:2759` (`approved.origin`). Only the
   third is on the model path, and it can only be reached from `executeApprovedToolCall`, whose
   `approved: ToolApprovalOutcome.Run` argument can only be produced by `evaluate` or `resolve`.
3. `ChatPanelToolGateTest.confirmToolDoesNotReachBurpBeforeADecision:64` — ran green in this audit.

### T-22-02 — Bulk-read tools flowing to a third-party model with no human (InfoDisc) — CLOSED

**Evidence:** Mechanical scan of all 59 `McpToolCatalog` descriptors. Every tool the register names is
`CONFIRM`: `proxy_http_history`, `proxy_http_history_regex`, `response_body_search`,
`proxy_ws_history`, `proxy_ws_history_regex`, `site_map`, `site_map_regex`, `scanner_issues`,
`editor_get`, `cookie_jar_get`, `ai_findings_recent`, `ai_audit_query`. The `AUTO` set is exactly 19
stateless codec/parse utilities (`base64_*`, `url_*`, `jwt_decode`, `hash_compute`, `request_parse`,
`response_parse`, `params_extract`, `insertion_points`, `find_reflected`, `diff_requests`,
`decode_as`, `random_string`, `redact_preview`, `scope_check`, `status`, `scan_task_status`,
`ai_backends_list`). Pinned by `McpToolCatalogTierParityTest.autoTierIsExactlyTheEnumeratedNineteen:34`.
Current partition: 19 AUTO / 24 CONFIRM / 16 CONFIRM_EACH = 59.

### T-22-03 — AI-analysis tools and `ext:` exfiltration sinks (InfoDisc) — CLOSED

**Evidence:** `ai_analyze` and `ai_passive_scan` are both `CONFIRM_EACH` in the catalog.
`ToolApprovalGate.kt:360` — `if (canonical.startsWith("ext:")) return SecTier.CONFIRM_EACH`, derived
from the namespace before the catalog is consulted. `SecTierResolutionTest.kt:67-74` asserts
`ext:demo:anything` → `CONFIRM_EACH` and, specifically, that `ext:demo:scope_check` is
`CONFIRM_EACH` and `assertNotEquals(AUTO, …)` — the built-in's `AUTO` tier is not inherited.

### T-22-04 — `scope_include` neutralising the scope filter (EoP) — CLOSED

**Evidence:** Catalog scan — `scope_include` is `CONFIRM_EACH`. `scope_exclude` is `CONFIRM` with an
inline justification at `McpToolCatalog.kt:223` (narrowing scope is not a bypass primitive). Neither
is `AUTO`.

### T-22-05 — Options-set tools redirecting the proxy / disabling TLS verification (Tampering) — CLOSED

**Evidence:** Catalog scan — `project_options_set` and `user_options_set` are both `CONFIRM_EACH`, so
a session grant can never cover a later JSON body. D-07's full-args rendering is present:
`ToolApprovalCard.kt:262-266` builds `fullArgs`, `:275` sets `initiallyExpanded = tier ==
SecTier.CONFIRM_EACH`, so the args of exactly these tools are open by default when the user decides.

### T-22-06 — CWE-117 log injection into the audit payload and Output tab (Tampering/Repudiation) — CLOSED

**Declared mitigation (22-05):** `sanitizeInline` removes control characters rather than replacing
them, collapses whitespace and caps at 120 characters before either sink.

**Evidence:**
1. `ToolDecisionReporter.kt:302-306` — `outputToolName` applies `sanitizeInline` **unconditionally on
   both branches** of `knownTool`. This is the CR-01 fix (`6f352eb`); it is not conditional on
   `isKnownTool` being correct, which is what makes it the control.
2. `ToolDecisionReporter.kt:203-208` — the payload's `toolName` key is `sanitizeInline(canonicalId)` on
   the `knownTool` branch and the constant `"unknown"` on the other. No unsanitized model string
   reaches the durable payload.
3. `ToolApprovalGate.kt:54` — `inlineControlCharRegex = Regex("[\\p{Cntrl}\\u0080-\\u009F]")`, applied
   as `.replace(regex, "")` (removal, not substitution) at `:520`, then whitespace-collapsed at `:64`
   and capped at `INLINE_MAX_LENGTH = 120` (`:47`).
4. `outputLine` (`:288-291`) interpolates no args at all.
5. **Deviation from the letter of the plan, verified as strengthening:** the args do *not* go through
   `sanitizeInline` — by default they are `Hashing.sha256Hex` (safe by construction, 64 chars of
   `[0-9a-f]`), and under the verbose seam `sanitizeBlock`, which preserves `\n`/`\t` for JSON
   readability. That preservation is safe because the audit sink is Jackson-serialized
   (`AuditLogger.kt:60,68` — `mapper.writeValueAsString(record) + "\n"`), so a preserved newline is
   JSON-escaped and cannot forge a JSONL record; and neither args form reaches `outputLine`. This
   change was CR-03's fix and is documented at `DECISIONS.md:195`.
6. Tests: `ToolDecisionReporterTest.outputLineIsSingleLineAndSanitized:373` and
   `aRecognisedExtToolNameIsSanitizedBeforeReachingEitherSink:388` — both green in this audit.

### T-22-07 — Model text rendered as extension chrome (Spoofing) — CLOSED

**Evidence:**
1. The two model-supplied strings resolve to `sanitizedToolId` (`ToolApprovalCard.kt:240`) and
   `sanitizedArgs`/`previewArgs`/`fullArgs` (`:243`, `:246`, `:262`). Their only destinations are
   `toolIdField = JTextField(sanitizedToolId)` (`:304`) and `argsArea.text = …` on a `JTextArea`
   (`:306`, `:642`). Neither installs Swing's HTML renderer.
2. Zero `JEditorPane` in the file. The only `toolTipText` assignment is `:622`, fed by
   `decisionButton(label, tooltip, …)` whose two session tooltips interpolate `catalogTitle` and are
   omitted entirely when the tool is unrecognised (`:517-521`).
3. Every `JLabel` in the file is constructed from an extension-authored constant (`HEADING`,
   `UNKNOWN_TOOL_TITLE`, `TRUST_LABEL`, `SESSION_SCOPE_FOOTER`, ordinal text, outcome verbs).
4. Tests `ToolApprovalCardTest.modelSuppliedTextNeverInstallsTheHtmlRenderer:91` and
   `modelSuppliedTextIsNeverConcatenatedIntoExtensionText:124` — green.

*Coverage caveat recorded as UF-2 below; it does not affect the control, only the guard's reach.*

### T-22-08 — Card flooding through the safety control (DoS) — CLOSED

**Evidence:** `ToolApprovalGate.kt:497` — `nextIterationBudget(remaining) = (remaining -
1).coerceAtLeast(0)`; `:500` — `allowsFurtherToolCalls(remaining) = remaining > 1`. Both helpers are
called from **both** outcome branches, not two copies of the arithmetic: deny branch
`ChatPanel.kt:2725` / `:2728`, approve branch `:2819` / `:2822`. `MAX_AUTO_TOOL_ITERATIONS = 8` at
`ChatPanel.kt:1307`. Tests `ToolApprovalGateTest.iterationBudgetIsMonotoneAndTerminates:263` and
`ChatPanelToolGateTest.eightConsecutiveDenialsTerminateTheChainWithNoNinthTurn:247` — green.

### T-22-09 — A decision that is not recorded, or recorded only where nobody looks (Repudiation) — CLOSED

**Evidence:**
1. `ToolDecisionReporter.report` (`:125-157`) produces all three destinations from one `buildPayload`
   call: `AuditLogger.emitGlobal("mcp_tool_decision", payload)` at `:151`, one Output line at `:152`,
   and the null-filtered metadata map returned at `:155`.
2. **All five decision branches call it**, verified by grep on `ChatPanel.kt`: `:2530` (click with the
   transcript gone), `:2590` (implicit deny, inside `resolvePending`), `:2682` (denial), `:2781`
   (approved run, including `AUTO`), `:2853` (approved-then-threw).
3. Reported *before* the nullable `supervisor.aiRequestLogger?.log` at both `:2691` and `:2795`, so the
   audit event does not depend on the AI-Activity sink being wired.
4. `ChatPanelToolGateTest.everyDecisionEmitsTheSc3Metadata:402` — green.

### T-22-10 — Pending decision surviving teardown (DoS/Repudiation) — CLOSED

**Evidence:** `resolvePending` declared at `ChatPanel.kt:2572`; `resolveAllPending` at `:2613`
iterating a key copy. All five measured teardown paths route through it and none touch
`pendingDecisions` directly: `sendFromInput` `:523` (`NEW_MESSAGE`), `deleteSession` `:862`
(`SESSION_DELETED`), `clearChatState` `:1083` (`CHAT_CLEARED`), `shutdown` `:1448` (`UNLOAD`),
`clearInMemorySessionState` `:1473` (`PROJECT_CHANGED`). A sixth, defensive site at `:2462` also routes
through the same entry point rather than repeating its steps. Each resolution consults the gate
(`:2584`), resolves the card, writes the SC3 record and discharges `onCompleted` (`:2604`).
`ChatPanelToolGateTest.clearChatResolvesThePendingCardAndClearsApprovalMemory:311` — green.

### T-22-11 — A future parse-and-execute site minting a model origin (EoP) — CLOSED (with residual)

**Declared mitigation (22-03/22-07):** `ToolCallOrigin`'s model variant is a top-level `private class`
in `ToolApprovalGate.kt`; it cannot be constructed or named outside that file. Asserted by
`grep -rl 'ModelApproved' src/main/kotlin` returning only the gate file. `executeTool`'s `origin` has
no default value.

**Evidence:**
1. `ToolApprovalGate.kt:182` — `private class ModelApproved(...) : ToolCallOrigin`, top-level, therefore
   file-private in Kotlin.
2. `ToolApprovalGate.kt:383` — `private fun approvedOrigin(...): ToolCallOrigin`. **Object-private**, the
   CR-02 fix (`47af7e9`). Returns the interface type, never the implementing one. Both halves are now
   present; the review measured that either alone is decorative.
3. `grep -rl 'ModelApproved' src/main/kotlin` returns exactly one file — `ToolApprovalGate.kt`.
4. `McpToolExecutorImpl.kt:1052-1057` — `origin: ToolCallOrigin` is required and non-defaulted, so all
   three call sites must declare one.
5. `ToolApprovalGateVisibilityTest` (3 tests, green) pins `approvedOrigin` as `private` through two
   independent channels — source text and reflection on the compiled member name (`:100`: an `internal`
   member would be emitted as `approvedOrigin$<module>`). Added by `600b976` to close WARN-6.
   `build.gradle.kts:186-189` declares `ToolApprovalGate.kt` as a task input so a comment-only widening
   cannot be served from the build cache with the guard un-run.

**Residual — judged, not glossed.** `ToolCallOrigin` (`:153`) is `internal sealed interface`, and Kotlin
seals to package + module, not to a file. A **deliberate** bypass — a new file under
`com.six2dez.burp.aiagent.mcp` declaring its own implementor with `wireValue = "model_approved"` — is
not prevented by the type system.

**Assessment: this does not open the threat as the register states it.** T-22-11's stated component is
"a future fourth parse-and-execute call site **in `ui/`**". A file in `ui/` cannot implement a
`sealed interface` sealed to package `mcp`, and cannot name or construct `ModelApproved`. The only
origins reachable from `ui/` are the two honestly-labelled user variants, which are recorded verbatim
in the audit payload and would be a visible lie in a diff. What ships is *an accidental bypass cannot
compile*; the deliberate one is answered by code review and by the audit record. That is the strongest
guarantee available in a single-module Kotlin codebase, the codebase now states it at that strength in
three places (`ToolApprovalGate.kt:31-44`, `McpToolExecutorImpl.kt:1026-1036`, `DECISIONS.md:187`), and
the earlier overclaim was corrected rather than left standing (`ae723f7`). Closed, residual recorded.

### T-22-12 — Alias resolving to different tiers in gate vs executor (Tampering) — CLOSED

**Evidence:** `ToolApprovalGate.tierFor:357` calls `McpToolExecutor.canonicalToolId(rawToolName)` —
the executor's own function, not a copied alias table — and canonicalises *before* the `ext:` test and
the catalog lookup, reproducing `executeToolResult`'s order. `ToolApprovalMemory.key:258` re-canonicalises
every memory key through the same function. `SecTierResolutionTest.gateAndExecutorConsumeTheSameCanonicalisation:100`
— green; the summary records it going red under a skip-canonicalisation mutation.

### T-22-13 — The phase's own acceptance gate being silently skipped (Repudiation) — CLOSED

**Evidence:**
1. `build.gradle.kts:154` — `jvmArgs("-ea", "-Djava.awt.headless=true")`, the A3/A4 assumption the SC4
   harness depends on (`:155-156` records why).
2. `build.gradle.kts:190-202` — the `-PexcludeHeavyTests=true` PR-gate exclusion list is exactly five
   patterns: `*IntegrationTest`, `*ConcurrencyTest`, `*BackpressureTest`, `*RestartPolicyTest`,
   `*SupervisionTest`. None of the eight SEC-06 suite names matches any of them.
3. **Proven executably in this audit, not by name inspection:**
   `./gradlew test -PexcludeHeavyTests=true --tests '*ChatPanelToolGateTest*'
   --tests '*ToolApprovalGateVisibilityTest*' --tests '*DecisionsAdrTest*'` → 22 tests ran, 0 failures.
   The gate cannot skip the SEC-06 acceptance suites.

### T-22-14 — A future tool added with no tier (EoP) — CLOSED

**Evidence:** `McpToolCatalog.kt:44-45` — `val secTier: SecTier` carries **no default value**, with the
reason in the preceding comment. All 59 descriptors declare one (`grep -c 'secTier = SecTier\.'` → 59,
matching the catalog size). Omission is `No value passed for parameter 'secTier'` at the catalog site.
This inverts `.planning/codebase/CONCERNS.md` §"MCP unsafe-tool gate — new tools must opt in".

### T-22-15 — Binding the trust boundary to `unsafeOnly` (EoP) — CLOSED

**Evidence:** `McpToolCatalog.kt:15-20` — the `SecTier` KDoc states the two axes are independent and
names the concrete case: `ai_analyze` and `ai_passive_scan` are `CONFIRM_EACH` without being
`unsafeOnly` at all (confirmed in the catalog scan), so binding to `unsafeOnly` would leave both
ungated. `McpToolContext.isUnsafeToolAllowed` returns `true` for everything once `unsafeEnabled` is on.
Tests `McpToolCatalogTierParityTest.noUnsafeToolIsAuto:128` and `tierIsIndependentOfUnsafeOnly:149`
— green.

### T-22-16 — Unrecognised or misspelled name treated as safe (EoP) — CLOSED

**Evidence:** `ToolApprovalGate.kt:365` — `return descriptor?.secTier ?: SecTier.CONFIRM_EACH`, with
the comment naming this as the runtime fallback D-03's authoring default does not cover. `tierFor` is
total: every input resolves, none throws. `SecTierResolutionTest` covers unknown, empty and wrong-case
shapes with explicit `assertNotEquals(AUTO, …)` on each; `:80` records that wrong-case names do not
match the `ext:` prefix and fall through to the same fail-closed branch.

### T-22-17 — Advertising the tier in the tool preamble (InfoDisc) — CLOSED

**Evidence:** `McpToolExecutorImpl.kt:113-123` appends exactly three markers — `[unsafe]`, `[pro]`,
`[external]`. No `[auto]` and no tier text. `grep -rn 'secTier' src/main/kotlin` outside the three
SEC-06 files (`McpToolCatalog.kt`, `ToolApprovalGate.kt`, `ToolDecisionReporter.kt`) returns **zero**
hits — the tier is structurally unable to reach `describeTools` or `buildToolPreamble`. Recorded as a
deliberate omission at `DECISIONS.md:200`.

### T-22-18 — One session click granting unlimited wire-traffic tools (EoP) — CLOSED

**Evidence:** `ToolApprovalGate.evaluate:410-431` is a single `when`, so branch order *is* evaluation
order. The `CONFIRM_EACH` branch (`:419-420`) returns `Ask(tier, canonical, offersSessionActions =
false)` **before** either `memory.isDeniedForSession` or `memory.isApprovedForSession` is named — the
"touches no session set" property is structural, not asserted. `resolve:456-458` carries a `require`
that throws if `APPROVE_SESSION`/`DENY_SESSION` is passed for a `CONFIRM_EACH` tool. All wire-traffic
tools the register names are `CONFIRM_EACH` in the catalog: `http1_request`, `http2_request`,
`intruder`, `intruder_prepare`, `scan_audit_start` (plus `repeater_tab` / `repeater_tab_with_payload`,
promoted post-verification by `f396918` to close WARN-2). Test `confirmEachNeverGainsSessionMemory:189`
— green.

### T-22-19 — A denial reported as a malfunction (Tampering) — CLOSED

**Evidence:**
1. `ToolApprovalGate.kt:343` — one `DENIAL_RESULT` constant, deliberately not `Error:`-prefixed. It is
   returned byte-identically for all four denial decisions: `SESSION_DENIED` (`evaluate:423`), `DENY`
   (`resolve:466`), `DENY_SESSION` (`:469`), `IMPLICIT_DENY` (`:471`).
2. `ToolDecisionReporter.kt:38` — `STATUS_DENIED = "denied"`, a **third** status value, applied by
   `buildPayload:199` via `isDenial(decision)`, which at `:326-340` is an exhaustive `when` over the
   enum (a ninth constant is a compile error, not a silent `"ok"`).
3. `denyToolCall` (`ChatPanel.kt:2711-2714`) sends a denial-variant followup whose closing line
   deliberately does not point the model at a nonexistent tool result.
4. Tests `ToolApprovalGateTest.kt:238-255` (byte-identity + `assertFalse(startsWith("Error:"))`) and
   `ChatPanelToolGateTest.denyReturnsAResultThatLetsTheConversationContinue:146` — green.

### T-22-20 — Approval for target A applying to target B (EoP) — CLOSED

**Evidence:** `ToolApprovalGate` holds no state — `ToolApprovalMemory` (`:209`) is a caller-owned
per-session holder, keyed through `key() = McpToolExecutor.canonicalToolId(...)` (`:258`), so two
sessions have provably independent memories. Approvals do not survive a Burp restart: `restoreSessions`
builds a fresh `ToolSessionState` at `ChatPanel.kt:1600`. Test
`sessionMemoryIsKeyedOnCanonicalIdNotTheModelString:305` — green.

### T-22-21 — A shipped bypass (EoP) — CLOSED

**Evidence:** `ToolApprovalGate.evaluate(rawToolName: String, memory: ToolApprovalMemory)` —
`ToolApprovalGate.kt:402-405`. Two parameters, no settings object, no enable flag, no bypass
parameter; the KDoc at `:390-400` states the omission is the control. Repo-wide grep across
`config/` for `autoApprove|auto_approve|skipApproval|allowWithoutAsking|bypassGate|approvalEnabled|gateEnabled|toolApproval`
returns **zero** hits — no such setting exists to be imported maliciously. The escape hatch is
`ToolSessionState.toolsMode`, which stops tool calls entirely rather than un-gating them.

### T-22-22 — Collapsing "a human clicked" into "an earlier click applied" (Repudiation) — CLOSED

**Evidence:** `ToolApprovalGate.kt:77-103` — eight distinct `ToolDecision` constants with distinct
`wireValue`s, including the two auditor-critical pairs: `APPROVE_ONCE("approve_once")` vs
`SESSION_APPROVED("session_approved")`, and `DENY("deny")` vs `SESSION_DENIED("session_denied")`. The
gate emits `SESSION_APPROVED`, never `APPROVE_SESSION`, on the suppressed path (`evaluate:428-429`).
Test `approveForSessionSuppressesTheNextCard:125` — green.

### T-22-23 — Model args in plaintext in a durable log (InfoDisc) — CLOSED

**Evidence:** `ToolDecisionReporter.auditValue:270-278` — `Hashing.sha256Hex(it)` unless
`verboseAudit`. `verboseAudit` is a constructor seam at `:92` defaulted to `false`; the single
production construction, `ChatPanel.kt:154`, passes only `logToOutput` and takes the default. No
user-facing verbose toggle exists anywhere in `config/` (grep → 0), so hashing is the effective and
only default, per CLAUDE.md's "hashes only unless verbose is on". Tests
`argsArePlaintextOnlyUnderTheVerboseSeam:231` and `argsDigestCoversTheWholeArgumentStringNotAPrefixOfIt:255`
(CR-03 regression, asserting `Hashing.sha256Hex(args)` exactly and `assertNotEquals` against the old
truncated form) — green.

### T-22-24 — `AUTO` run indistinguishable from a pre-fix decision-less call (Repudiation) — CLOSED

**Evidence:** `ToolDecisionReporter.buildPayload:222` — `put("secTier", tier.wireValue)` is
unconditional, outside every `if`, so it is emitted on every event including `AUTO`. The comment at
`:219-221` states the reason. `ChatPanel.kt:2781`'s approved-run branch is reached for `AUTO` via
`evaluate:414-415` → `Run(…, AUTO, ToolDecision.AUTO)` → `executeApprovedToolCall`, so an `AUTO` call
does report.

### T-22-25 — Reusing an audit constant whose keys mean something else (Tampering) — CLOSED

**Evidence:** `ToolDecisionReporter.kt:27` — `private const val MCP_TOOL_DECISION_EVENT =
"mcp_tool_decision"`, new. `grep -n 'mcp_tool_blocked\|mcp_tool_end'` over `ToolDecisionReporter.kt`
returns **zero** matches. The payload's first four keys (`operation`/`status`/`traceId`/`step`) reuse
`MCP_TOOL_CALL`'s existing shape and order deliberately, extending rather than inventing.

### T-22-26 — Fabricated catalog title for an unrecognised tool (Spoofing) — CLOSED

**Evidence:** `ChatPanel.kt:2412` — `catalogTitleFor(canonicalId) = McpToolCatalog.all().firstOrNull {
it.id == canonicalId }?.title`. This is a **catalog-only** lookup with no `ext:` prefix shortcut, so an
`ext:` name correctly yields `null`. `ToolApprovalCard.kt:295` — `JLabel(catalogTitle ?: if (isCompact)
UNKNOWN_TOOL_TITLE_COMPACT else UNKNOWN_TOOL_TITLE)`; the model's own string still appears, sanitized,
only inside its `JTextField`. Test `unknownToolIsLabelledNeverShownBare:166` — green.

*Note: `catalogTitleFor` is correct where `isKnownTool` is not — the card's unknown-tool labelling is
not affected by UF-1 below.*

### T-22-27 — Screen-reader user hearing model text as extension narration (Spoofing) — CLOSED

**Evidence:** `ToolApprovalCard.kt:599` — `getAccessibleContext().accessibleDescription = "$prefix
$DISCLOSURE_CLAUSE$sanitizedToolId"`. All three conditions hold structurally: the ID is the
inline-sanitized form (`:240`), it is preceded by the disclosure clause, and it is **last in the
string with nothing after it**. `updateAccessibleDescription` is re-run on resolution (`:414`) so a
resolved card does not keep announcing a pending question. Test
`accessibleDescriptionEndsWithTheSanitizedToolId:213` — green. *Test-fixture caveat at UF-3; the
control itself is in the code.*

### T-22-28 — Stray Enter or mis-click resolving a security decision (EoP) — CLOSED

**Evidence:** grep over `ToolApprovalCard.kt` for `defaultButton`, `mnemonic`, `ESCAPE`,
`registerKeyboardAction`, `getInputMap` returns **zero** matches — none of the four accidental-activation
mechanisms is wired. `buildButtonRow:522` adds `Deny` first, so visual order equals focus order and Tab
reaches the fail-closed pole first. `:529` — `Box.createRigidArea(Dimension(DesignTokens.Spacing.lg,
0))` separates the deny and approve poles; `DesignTokens.kt:51` confirms `lg = 16`. `:621` —
`isFocusPainted = true` on every decision button, a deliberate deviation from the codebase default so a
keyboard user sees which pole is focused. No focus theft on insertion: the only focus call is
`onRequestFocusRestore` at `:394-397`, guarded on `SwingUtilities.isDescendingFrom(focusOwner, this)`.

### T-22-29 — Exfiltration payload hidden below a fold (InfoDisc) — CLOSED

**Evidence:** `grep -n 'JScrollPane' ToolApprovalCard.kt` returns **zero** — no nested scroll pane; the
transcript's own does the scrolling (rationale at `:629-632`). `:275` —
`initiallyExpanded = tier == SecTier.CONFIRM_EACH`, computed internally and deliberately *not* exposed
as a constructor parameter, so no call site can defeat it. Truncation footers at `:862`/`:865` state
the exact `$shown of $total characters` counts plus `ARGS_SENT_IN_FULL` (the display cap is not an
execution limit). Three-stage disclosure with `showAllButton` at `:649`. Tests
`confirmEachCardRendersTwoDecisionButtons:65`, `confirmCardRendersFourDecisionButtons:44` — green.

### T-22-30 — Resolved card no longer recording what was offered or chosen (Repudiation) — CLOSED

**Evidence:** `ToolApprovalCard.resolve:384-417` — buttons are **removed**, not disabled
(`remove(buttonRow)` `:408`, `decisionButtons.clear()` `:409`), and the outcome row naming the clicked
action verbatim is added in the same GridBag cell (`:410`, `:406`). The tier badge and the args region
are untouched. `resolve` is idempotent (`:388-390` early return on `resolved`), so a click racing a
teardown cannot produce two outcome rows. Both compact suppressed variants ship: factory at `:779-793`,
invoked from `ChatPanel.addSuppressedDecisionRow:2401-2408` for `SESSION_APPROVED` and
`SESSION_DENIED`. Tests `resolutionRemovesButtonsAndAddsAnOutcomeRow:184` and
`compactRowRendersBothVariantsWithNoButtons:239` — green.

### T-22-32 — Double-prompting a user-originated call (EoP) — CLOSED

**Evidence:** `ChatPanel.kt:1010` (`openToolDialog`) passes `ToolCallOrigin.UserDialog` and `:2313`
(`/tool` slash command) passes `ToolCallOrigin.UserSlashCommand`; both carry an inline SC5 comment
naming T-22-32. Neither function body references `ToolApprovalGate` or `ToolApprovalCard` — the only
gate call sites in the file are `:2343`, `:2513`, `:2584`, all on the model path. Tests
`userDialogPathIsNotDoublePrompted:207` (source-text assertion, since the modal cannot be constructed
headlessly) and `slashCommandPathIsNotDoublePrompted:229` (behavioural, `times(1)` on
`api.proxy().history()` with no card for a `CONFIRM` tool) — green.

### T-22-33 — Denial followup dispatching a turn during `shutdown()` (DoS) — CLOSED

**Evidence:** `resolvePending(sessionId, reason, sendFollowup: Boolean = false)` —
`ChatPanel.kt:2572-2575`. The parameter is inert by construction (`@Suppress("UnusedParameter")` at
`:2571`; nothing reads it) and **all five call sites take the default**: `:523`, `:862`, `:1083`,
`:1448`, `:1473` — verified by grep, none passes `sendFollowup`. The KDoc at `:2560-2570` names both
hazards it prevents. `shutdown()` calls `resolveAllPending(UNLOAD)` at `:1448`, inside the
EDT-marshalled block, before `cancelInFlightRequest()`. Only an explicit `Deny` / `Deny for session`
click reaches `denyToolCall`'s `sendMessage`. Test
`shutdownResolvesAllPendingDecisionsWithoutSendingATurn:357` — green.

### T-22-34 — Session approval surviving Clear Chat (EoP) — CLOSED

**Evidence:** `ChatPanel.clearChatState:1095-1099` — `state.toolCatalogSent = false`, `state.toolsMode =
true`, and `state.approvalMemory = ToolApprovalMemory()`. A fresh holder drops both session sets and
the repeat counter in one assignment. Recorded in ADR-15 at `DECISIONS.md:193` so it is not
re-litigated. Test `clearChatResolvesThePendingCardAndClearsApprovalMemory:311` — green.

### T-22-35 — Invisible pending decision in a background session (DoS) — CLOSED

**Evidence:** `ChatPanel.kt:173` — `sessionsList.cellRenderer = ChatSessionRenderer { sessionId ->
pendingDecisions.containsKey(sessionId) }`; the renderer (`:1779`) adds `JLabel("Awaiting approval")` at
`:1823`. `refreshSessionList()` (`:1364`) re-runs the renderer on every model element and is called at
card insertion (`:2493`), on explicit resolution (`:2519`) and on implicit resolution (`:2605`) — so the
marker appears and clears however the decision is retired. Returning to the session scrolls the card
back into view: `scrollToComponent` (`:1918`) invoked at `:1279`. Test
`sessionRowMarksAPendingDecisionAndClearsItOnResolution:432` asserts absent → present → absent through
the production renderer — green.

### T-22-36 — Discarded `sessionStates[id] ?: ToolSessionState()` fallback (EoP) — CLOSED

**Evidence:** `grep -rn '?: ToolSessionState()' src/main/kotlin` returns **zero** occurrences of the
discarding form. Nine `sessionStates.getOrPut(…) { ToolSessionState() }` sites, including the gate's own
at `ChatPanel.kt:2337` (the `:342` the plan named) and every resolution path (`:2448`, `:2511`, `:2580`).
The two remaining `sessionStates[id] = …` occurrences (`:801`, `:1600`) are creation-time assignments,
not read-with-fallback.

### T-22-37 — A later tool classified `AUTO` by a loose reading (EoP) — CLOSED

**Evidence:** `DECISIONS.md:184-185` carries D-05's sentence verbatim as a blockquote; `:187` carries
the two Pitfall-1 worked examples (`*_options_get` as credential material; `proxy_http_history` as the
load-bearing consequence) **without altering the sentence**. `DecisionsAdrTest` (4 tests, green) asserts
the ADR exists and is the highest-numbered (`:43`), that the sentence survives verbatim (`:61`), and
that the `SecTier` KDoc copy still agrees byte-for-byte after normalisation (`:74`, `:158`).
`build.gradle.kts:172-179` declares `DECISIONS.md` and `McpToolCatalog.kt` as test-task inputs, so a
doc-only edit cannot be served from cache with the guard un-run — the 22-09 defect this fixed.
`McpToolCatalogTierParityTest` pins the AUTO set as enumerated literals, making a promotion a reviewed
diff.

### T-22-38 — SC1 marked done without anyone checking the ADR (Repudiation) — CLOSED

**Evidence:** Split coverage is present on both sides. Automated: `DecisionsAdrTest`, 4 tests, green
under the PR gate (proven above). Human: `22-HUMAN-UAT.md:46` — "### 2. ADR-15 is factually accurate",
with an explicit `why_human` rationale at `:63-65` stating that the test guards the *sentence*, not the
*truth*. The item is also carried into `22-VERIFICATION.md`'s `human_verification` block and was
resolved on 2026-08-19 (`22-VERIFICATION.md:331`).

### T-22-39 — An overclaiming ADR (Spoofing) — CLOSED

**Evidence:** Seven explicit `Residual:` bullets in ADR-15 — `DECISIONS.md:198` (`isKnownTool` prefix
trust), `:201` (deny-for-session bounds prompting, not token cost), `:202` (approvals do not survive a
Burp restart), `:203` (the resolved card is a live-session record only), `:204` (four of five
implicit-denial paths leave no surviving surface), `:205` (the compact unknown-tool string is
unreachable in this release), `:206` (EDT behaviour untouched; the ADR makes no claim about it). All six
D-14 elements are covered. The one overclaim the phase did make — the file-scoped origin seal — was
corrected in place at `:187` rather than deleted or softened silently, and the correction narrative
("stated at the strength the compiler actually checks, because a claim the compiler does not check is
worse than a weaker accurate one") is itself in the record.

### T-22-40 — Future persisted approvals or a settings-level tier downgrade (EoP) — CLOSED

**Evidence:** `DECISIONS.md:187` — "A persisted per-tool `CONFIRM`→`AUTO` downgrade was rejected because
it would make a malicious settings import a gate bypass; a warned global off-switch was rejected because
it is the control's own bypass shipped in the box, and Unsafe Mode being on is precisely the state in
which the gate matters most." The settings-import attack path is named, so it is not re-litigated from
scratch. `:202` adds the explicit prohibition on the other half: "That is stricter than D-10 requires
and it is intentional; do not 'fix' it by persisting the set."

### T-22-SC — Supply chain (Tampering) — CLOSED (accepted risk, verified empty)

**Disposition:** `accept`. Restated once in each of the nine plans; one distinct threat.

**Evidence (pre-verified by the orchestrator, recorded here rather than re-run):**
`git diff 322e2cb..HEAD -- build.gradle.kts settings.gradle.kts gradle/libs.versions.toml` shows zero
added or removed dependency lines. The only change is a Gradle stdlib import (`PathSensitivity`) used
for the `tasks.test` input declarations verified under T-22-13 and T-22-37. No lockfiles, no new
package manifests. `22-RESEARCH.md` §"Package Legitimacy Audit" records the empty table. Entered in the
Accepted Risks Log below.

---

## Open Threats

### T-22-31 — A parked continuation that is never discharged (Denial of Service) — CLOSED at `43c11ce`

**Declared mitigation (22-07):** "Every branch either invokes `onCompleted` or hands it into
`sendMessage`; the one-pending-card-per-session invariant is enforced by resolving an existing pending
record as `IMPLICIT_DENY` before inserting a second card."

**Second clause — PRESENT.** `ChatPanel.kt:2453-2463` — `askForToolApproval` checks
`pendingDecisions.containsKey(sessionId)` and routes through the single `resolvePending(sessionId,
NEW_MESSAGE)` entry point (not a second inline copy) before inserting a new card, then surfaces
`showError`. Verified.

**First clause — ABSENT ON ONE BRANCH.** The click-then-tool-throws path drops the continuation:

1. `resolveToolDecision` calls `dispatchResolvedToolCall(pending, panel, resolved)` at
   `ChatPanel.kt:2603`.
2. `dispatchResolvedToolCall` (`:2617-2652`) returns `Unit`. Its `when` calls
   `executeApprovedToolCall(...)`, whose `ToolCallOutcome` return value is **discarded**.
3. `executeApprovedToolCall:2860` — when `resultOutcome.isFailure`, it returns
   `reportFailedToolCall(...)`. That function (`:2839-2875`) does not take `onCompleted`, does not
   invoke it, and returns `ToolCallOutcome.NOT_CHAINED` at `:2874`.
4. Nothing between `:2603` and `:2874` ever calls `pending.onCompleted`. The un-asked path handles
   exactly this case at `ChatPanel.kt:759` — `if (outcome == ToolCallOutcome.NOT_CHAINED) {
   onCompleted?.invoke(finalResp, null) }` — and the resolved-click path has no equivalent.

So a user who clicks `Approve once` on a tool that then throws leaves `onCompleted` parked forever:
precisely the threat as the register states it, "a parked continuation that is never discharged,
hanging the caller that supplied `onCompleted` (the 'Send to AI' launch path)".

Every *other* branch does satisfy the clause and was verified: approved-and-succeeded hands
`onCompleted` into `sendMessage` (`:2825`); denial hands it into `sendMessage` (`:2731`); transcript-gone
invokes it (`:2540`); all five implicit-denial paths invoke it (`resolvePending:2604`).

**Severity: LOW / latent — not reachable in the shipped artifact today.** `onCompleted` is non-null only
via `MainTab.openChatWithContext` (`MainTab.kt:527`, `:536`). Independently verified by grep across
`src/main` and `src/test`: **that function has zero callers**, so `onCompleted` is always `null` today
and `null?.invoke(...)` is a no-op either way. The hang is a latent defect waiting for the first caller,
not a live one.

**Under `block_on: high` this does not block the phase.** It is recorded OPEN rather than CLOSED because
the declared mitigation is measurably not present on one branch, and this phase has a documented history
of controls declared complete that were not.

**Also tracked as:** `22-REVIEW.md` WR-01 (still open), `22-VERIFICATION.md` WARN-5 (still open).

**Suggested resolution (implementation, not this audit's scope):** either give
`dispatchResolvedToolCall` a `ToolCallOutcome` return and discharge `pending.onCompleted` on
`NOT_CHAINED` the way `:759` does, or pass `onCompleted` into `reportFailedToolCall` and invoke it
there. Alternatively, document it as an accepted risk with the zero-callers rationale and a guard test
that fails if `openChatWithContext` ever gains one.

---

## Unregistered Flags

No plan summary declared a threat flag: `22-03`/`22-04`/`22-05` state "Threat flags: none" explicitly,
`22-06`/`22-07`/`22-08`/`22-09` carry a `## Threat Flags` section reading "None", and `22-01`/`22-02`
record no new security-relevant surface. The following surfaced during this audit's own reading and
map to no register threat. **None is a blocker under `block_on: high`.**

### UF-1 — `isKnownTool` prefix trust degrades audit fidelity beyond the disclosed residual (WARNING)

`ChatPanel.kt:2423` — `isKnownTool(canonicalId) = canonicalId.startsWith("ext:") ||
McpToolCatalog.all().any { it.id == canonicalId }`. Its own KDoc (`:2418-2419`) claims it checks "an
`ext:` name **belonging to a configured external server**"; it never consults
`ExternalMcpClientManager.availableTools()`.

The classification inaccuracy is disclosed as ADR-15 Residual `DECISIONS.md:198`. The **consequence
that is not disclosed** is in the durable payload: because `knownTool` is `true`, `buildPayload`
(`ToolDecisionReporter.kt:203-208`) writes `sanitizeInline(canonicalId)` — capped at 120 characters —
into `toolName`, **and omits `toolNameSha256` entirely** (`:211-227` is guarded by `if (!knownTool)`).
So two bogus `ext:` names sharing a 120-character prefix are indistinguishable in the audit record, and
the whole-name digest that commit `a323980` added to close WARN-1 is bypassed for exactly the class of
names WARN-3 misclassifies.

**Why this is a flag, not an open threat.** No register threat declares `knownTool` accuracy as its
mitigation; T-22-06's declared control *is* the 120-character cap, so the truncation is the mitigation
working as written, not its absence. The gate is unaffected: `tierFor` derives `CONFIRM_EACH` from the
same `ext:` prefix (`ToolApprovalGate.kt:360`), so such a call always prompts and always shows the user
the full sanitized name on the card. Forgery is impossible on either sink (T-22-06). This is audit
*fidelity*, not gate integrity or injection.

**Tracked as:** `22-REVIEW.md` WR-04, `22-VERIFICATION.md` WARN-3 — both still open.

### UF-2 — HTML-renderer exemption in the card is not covered by the anti-spoofing sweep (INFO)

`ToolApprovalCard.kt:557` (`JLabel(if (isCompact) "<html>${current.verb}" else current.verb)`) and
`:862` / `:865` (truncation footers on `helpLabel`) install Swing's HTML renderer. Both carry
extension-authored, integer-only-interpolated text and are safe today — T-22-07 remains CLOSED because
no model-supplied string reaches either. The gap is in the *guard*:
`modelSuppliedTextNeverInstallsTheHtmlRenderer:91` builds only a pending card with short args, so
`footerTextFor` returns `null` and the compact branch is never constructed — the exhaustive sweep never
visits either component. A future edit interpolating `catalogTitle` or the model's tool ID into a
truncation footer would land in an HTML-rendering `JLabel` with no test to catch it. **Tracked as:**
`22-REVIEW.md` WR-06 (open).

### UF-3 — `accessibleDescriptionEndsWithTheSanitizedToolId` fixture is vacuous for the sanitization claim (INFO)

`ToolApprovalCardTest.kt:213` drives the assertion with `toolId = "http1_request"`, which
`sanitizeInline` returns unchanged, so the test would pass identically if
`updateAccessibleDescription` interpolated the raw `modelSuppliedToolId`. T-22-27's control is present
in the code (`ToolApprovalCard.kt:599` uses `sanitizedToolId`), so the threat is CLOSED; only the
non-vacuity of its guard is weak for the accessible-description channel. `toolIdIsSanitizedAndCapped:147`
covers the `JTextField` channel properly. **Tracked as:** `22-REVIEW.md` WR-08 (open).

### Explicitly out of scope — not a gap

`executeTool` still runs on the EDT at three call sites. Phase 23 / REL-05 owns it; ADR-15 makes no
claim about EDT behaviour (`DECISIONS.md:206`), and the review brief excluded it. Not reported.

---

## Accepted Risks Log

| Risk ID | Threat Ref | Rationale | Accepted By | Date |
|---------|------------|-----------|-------------|------|
| AR-22-01 | T-22-SC | Phase installs zero packages. `git diff 322e2cb..HEAD` on `build.gradle.kts`, `settings.gradle.kts` and `gradle/libs.versions.toml` shows zero dependency-line changes; the only edit is a Gradle stdlib import (`PathSensitivity`) for test-input declarations. No lockfiles, no new manifests. Any future deviation requires running the Package Legitimacy Gate before the install. | Orchestrator (pre-verified), confirmed by gsd-security-auditor | 2026-08-19 |
| AR-22-02 | T-22-11 | SC5's origin guarantee is package-scoped, not file-scoped: Kotlin seals a `sealed interface` to package + module, so a deliberate bypass from a new file inside `com.six2dez.burp.aiagent.mcp` is not prevented by the type system. An *accidental* bypass cannot compile (`ModelApproved` file-private, `approvedOrigin` object-private, `origin` non-defaulted). This is the strongest available guarantee in a single-module Kotlin codebase; the alternative (unnameable-marker-member) trades a checked property for three suppressed compiler diagnostics and was rejected. Stated at the checked strength in `ToolApprovalGate.kt:31-44`, `McpToolExecutorImpl.kt:1026-1036`, `DECISIONS.md:187`. The deliberate bypass is answered by code review and by the audit record. | Maintainer via ADR-15 (`ae723f7`) | 2026-08-14 |

*Accepted risks do not resurface in future audit runs.*

---

## Security Audit Trail

| Audit Date | Threats Total | Closed | Open | Run By |
|------------|---------------|--------|------|--------|
| 2026-08-19 | 41 | 40 | 1 | gsd-security-auditor (claude-opus-5) @ `d7a93b9` |

**Verification method by disposition:**

| Disposition | Count (distinct) | Method | Result |
|-------------|------------------|--------|--------|
| `mitigate` | 40 | Grep for the declared control in the file cited by the mitigation plan; confirm it applies to **all** entry points (not one match); confirm the named acceptance test exists and executes green | 39 CLOSED, 1 OPEN |
| `accept` | 1 (T-22-SC) | Entry present in this file's Accepted Risks Log; orchestrator's empty-diff evidence recorded | 1 CLOSED |
| `transfer` | 0 | n/a | n/a |

**Prior findings re-verified in code rather than accepted from the record:**

| Finding | Claimed fix | Confirmed at |
|---------|-------------|--------------|
| CR-01 — CWE-117 log injection forging `[SEC-06]` Output lines | Sanitize on both `knownTool` branches | `ToolDecisionReporter.kt:302-306`; regression test `:388` green |
| CR-02 — `approvedOrigin` was `internal`, so SC5 was not compiler-enforced | Narrow to `private` | `ToolApprovalGate.kt:383` `private fun`; `:182` top-level `private class`; `ToolApprovalGateVisibilityTest` 3/3 green |
| CR-03 — `argsSha256` digested a 120-char prefix | Digest the whole value | `ToolDecisionReporter.kt:270-278`; regression tests `:243`, `:255` green |
| WARN-1 — `toolNameSha256` carried the same truncation | Digest the whole raw name | `ToolDecisionReporter.kt:226` `Hashing.sha256Hex(rawToolName)` — no sanitizer |
| WARN-2 — repeater tools contradicted ADR-15's own `CONFIRM_EACH` criterion | Promote both | Catalog scan: `repeater_tab` and `repeater_tab_with_payload` are `CONFIRM_EACH`; partition now 19/24/16 |
| WARN-6 — CR-02 fix had no regression guard | Add a two-channel guard | `ToolApprovalGateVisibilityTest.kt` + `build.gradle.kts:186-189` task-input declaration |

---

## Sign-Off

- [x] All threats have a disposition (mitigate / accept / transfer) — 40 mitigate, 1 accept, 0 transfer
- [x] Every threat resolved to CLOSED, OPEN, or a documented accepted risk — no threat skipped
- [x] Accepted risks documented in the Accepted Risks Log (AR-22-01, AR-22-02)
- [x] Implementation files not modified by this audit — only this file was written
- [x] `threats_open: 0` confirmed
- [x] `status: verified` set in frontmatter

**Approval:** pending — T-22-31 must be closed in code, or entered in the Accepted Risks Log with the
zero-callers rationale and a guard test, then re-run `/gsd-secure-phase`.

**Ship judgement:** `block_on: high`. T-22-31 is LOW severity (unreachable in the shipped artifact — no
caller supplies a non-null `onCompleted`), so it does not block this phase. The SEC-06 control itself —
no model-emitted tool call reaches Burp without a decision, every decision is recorded to three sinks,
and the origin is compiler-enforced against accidental bypass — is verified present at every entry
point.

---

## Security Audit 2026-08-19 (follow-up)

| Metric | Count |
|--------|-------|
| Threats found | 41 |
| Closed | 41 |
| Open | 0 |

**T-22-31 closed in code** at `43c11ce`, not accepted as risk. The maintainer chose the code fix over
the documented-risk path when both were offered.

Mitigation now present, verified against the tree at `43c11ce` rather than read from the commit
message:

- `dispatchResolvedToolCall` (`ChatPanel.kt:2643`) returns `ToolCallOutcome` instead of discarding it.
- `resolveToolDecision` (`ChatPanel.kt:2557`) discharges `pending.onCompleted` when the outcome is
  `NOT_CHAINED` — the same test, in the same position relative to the dispatch, as the un-asked path
  at `:759`. The register's declared mitigation ("every branch either invokes `onCompleted` or hands
  it into `sendMessage`") is now true of both branches.
- Double-discharge is excluded by construction: denial decisions and an approved run that returns
  normally both chain a followup and report `CHAINED`, so only the approved-then-threw path reaches
  the new discharge.
- Regression test `anApprovedToolThatThrowsStillDischargesTheParkedContinuation` was **verified RED
  against unmodified production code** (`Discharges: [] ==> expected: <1> but was: <0>`) with three
  non-vacuity clauses passing at the same time: the approved tool really ran and threw
  (`verify(proxy, times(1)).history()`), no followup was chained (so it is not the success path in
  disguise), and the SC3 record already read `approve_once` / `error`.

Invariants re-checked after the change: 5 `toolDecisionReporter.report(` sites intact (no SC3 branch
dropped), 2 `pendingDecisions.remove` sites unchanged, all five teardown paths still routed through
`resolvePending`. SEC-06 suites green.

### Carried forward, not closed here

- **AR-22-02** — package-scoped origin residual (accepted risk, unchanged).
- **UF-1** — `isKnownTool` prefix trust degrades audit fidelity (`ChatPanel.kt:2423`). Open as a code-review warning (WR-04).
- **UF-2** — HTML-renderer exemptions never visited by the anti-spoofing sweep. Now corroborated
  twice more: code review WR-06, and the UI audit's finding 2, which measured that
  `ToolApprovalCardTest`'s blanket `getClientProperty("html")` assertion is already inconsistent with
  shipped code (`ToolApprovalCard.kt:862` assigns `<html>` to a `JLabel`) and passes only because its
  fixture args are 23 characters.
- **WR-02** — `resolveToolDecision` still removes the pending record before a call that can throw.
  Explicitly neither closed nor worsened by this fix.
- **Test isolation hazard found while fixing** — `AuditLogger.registerGlobalEmitter` is a
  process-global singleton and `ChatPanelToolGateTest` panels are never shut down between tests, so
  queued `invokeLater` chains leak audit events across tests. `everyDecisionEmitsTheSc3Metadata`
  filters positionally and survives only because it drains 4 times rather than 24; reordering the
  class or raising that drain turns it red, or green for the wrong reason.

---

## Addendum — warning-pass remediation (2026-08-19, HEAD `ad46e85`)

The eleven remaining code-review and UI-audit warnings were fixed after this audit ran, in eleven
atomic commits. Two entries above are now stale as written; both are recorded here rather than edited
in place, because the audit body is a point-in-time record at `d7a93b9`.

- **T-22-33's mitigation sentence is superseded and the property is now STRONGER.** It reads
  "`sendFollowup` inert by construction; `false` at all five sites". WR-03 (`52df525`) deleted the
  parameter outright, so no teardown path can dispatch a backend turn — the guarantee is now
  structural rather than dependent on five call sites passing the right value. Only two references
  survive, both KDoc explaining the removal; verified by grep that it is a parameter nowhere.
- **UF-1 is CLOSED.** WR-04 (`ba54908`) made `isKnownTool` query the configured servers via a threaded
  `McpToolContext` instead of trusting the `ext:` prefix, and ADR-15's corresponding Residual was
  updated in the same commit. Audit fidelity for misclassified `ext:` names is restored — such a name
  is now recorded `unknown` and carries its `toolNameSha256`.
- **UF-2 is CLOSED.** WR-06 (`fbce125`) narrowed the anti-spoofing sweep from a blanket
  `JLabel`/`AbstractButton` assertion to per-row exemptions, and added a 4000-character args fixture
  so the `truncationFooter` exemption is actually exercised. The blanket form was verified to go RED
  under that fixture, reproducing the UI audit's claim.
- **UF-3 is CLOSED.** WR-08 (`51d02fe`) drives the accessible-description guard with an ID that
  genuinely requires sanitizing.

Invariants re-verified independently after all eleven commits: 5 `toolDecisionReporter.report(` sites,
2 `pendingDecisions.remove` sites, `private fun approvedOrigin`, `private class ModelApproved`, tier
split 19/24/16, and the T-22-31 discharge from `43c11ce` still present. Gate: `build test ktlintCheck
detekt` exit 0, **745 tests across 110 classes**, `detekt-baseline.xml` byte-identical.

`threats_open` remains **0**. Nothing here reopens a threat.

### Known residual introduced by the UI fix

The `<html>` wrap that makes `Approve for session` reachable has a trade the fixer documented rather
than hid: an HTML `JLabel` reports its unconstrained preferred height, so between the new 560 px floor
and the card's ~625 px preferred width, rows 2 and 10 can wrap to a second line that
`SessionPanel.addComponent`'s height cap does not allocate, and it clips. Strictly better than an
unreachable decision control, and `Deny` remains the last control lost. A width-aware
`getPreferredSize` override is the proper fix.

### Still open from the UI audit (not security-gating)

X-1 is the one worth taking next: `toolIdField` and `argsArea` have no accessible name, so a
screen-reader user tabbing into them hears the model's string with no attribution — a hole in the
card's own T-22-27 mitigation on a path the root `accessibleDescription` does not cover. Two lines,
no new copy. Also open: V-1/V-2/V-3, S-2, S-3, X-2/C-1, X-3, X-4, C-2, C-3.
