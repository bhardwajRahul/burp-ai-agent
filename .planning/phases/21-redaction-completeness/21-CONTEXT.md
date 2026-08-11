# Phase 21: Redaction Completeness - Context

**Gathered:** 2026-08-11
**Status:** Ready for planning

<domain>
## Phase Boundary

No path sends cookie values or other credentials to an AI backend under STRICT or BALANCED, and
redaction never silently declines to run. Requirements PRIV-05 (cookie leak, **high**, a defect
verified against the shipped v0.9.2) and PRIV-06 (fail-open above the size cap).

**The two defects:**

1. **PRIV-05 / F2** — `scanner/PassiveAiScannerAnalysis.kt:246-252` extracts cookies into a dedicated
   `=== COOKIES ===` prompt section (emitted at `:372-375`) as bare `name=value`, splitting the header
   on `;` and dropping the `Cookie:` prefix. `Redaction.apply` runs over the whole metadata blob at
   `:395-401`, so the **call site is correct** — the patterns simply cannot reach the data.
   `cookieHeaderRegex` is `(?im)^cookie:\s*.+$` and `formBodyParamRegex` requires the key to be an
   exact member of the `SENSITIVE_KEYS` alternation (`redact/Redaction.kt:88-89`). Verified against
   the live regexes: `JSESSIONID`, `PHPSESSID`, `connect.sid`, `auth_token`, `csrftoken`,
   `remember_me` all pass through in STRICT and BALANCED. Only a cookie literally named `session` is
   caught. `auth_token` misses because the alternation matches `auth` and then demands `=`.

   **Second entry point:** `:232-242` maps `request.parameters()` to `"${name}=${value} (${type})"`
   into `=== PARAMETERS ===` by the identical mechanism, so `COOKIE`-type params leak the same way.
   Both must be closed (SC2).

2. **PRIV-06 / F5** — `redact/Redaction.kt:257`. Above `Defaults.MAX_REDACTION_BODY_CHARS` (1 MB) the
   form, JSON **and user custom-pattern** stages are all skipped and the original passes through
   untouched. Related: custom patterns live inside the `redactTokens` branch, so they are inert under
   `PrivacyMode.OFF` — an emergent consequence rather than a decision (SC5).

**In scope:** the cookie/parameter leak at both entry points; widening sensitive-key matching without
over-redacting; oversize-body handling; the custom-patterns/OFF decision plus its ADR; the three
user-facing strings that describe OFF; removing the caller-side OFF short-circuits that bypass
`Redaction.apply` entirely.

**Out of scope:** host anonymization behaviour (SC6 requires the HKDF RFC 5869 vector and the
existing `RedactionTest` suite to stay green — do not perturb it); the GitBook privacy page and the
security advisory (DOC-03, Phase 26); `SecretShapes` / the Phase 15 tripwire.

</domain>

<decisions>
## Implementation Decisions

### Oversized bodies — PRIV-06 / SC4

- **D-01:** Above the size threshold, redaction **chunk-and-scans the whole input** rather than
  skipping it. Slice into windows with an overlap wide enough that a secret straddling a boundary is
  still matched, and run the body rules per window under the existing `SafeRegex` 50 ms per-pattern
  deadline. Nothing is skipped. This is the literal reading of "redaction never fails open".
  Rejected: truncate-and-redact (silent capability loss on large JSON), refuse (turns a redaction
  concern into a functional failure at five call sites, each needing its own failure story).

- **D-02:** The body stage carries a **total wall-clock budget**. Windows are processed in order
  until it is spent; everything past that point is **dropped with a visible marker**, not passed
  through. **Fail closed — unscanned bytes never reach a backend.** This is what bounds the worst
  case (a 200 MB string × 3 built-in rules × N custom patterns × 50 ms is otherwise unbounded on
  whatever thread called `Redaction.apply`).

- **D-03:** Truncation is surfaced to the user in **two places**: a marker in the payload itself
  (e.g. `[TRUNCATED — NOT REDACTED]`) so the model sees why context stops, and a **rate-limited
  Output-tab line** so the user knows redaction hit its ceiling. Mirrors Phase 20's D-06/D-09
  (both destinations, aggregate repeats) and reuses the established `maybeLogBackoff` /
  `availabilityLogged` patterns. **No `AuditLogger` dependency is added to `redact/`** — that package
  is deliberately AWT-free and dependency-light (see the `SafeRegex.kt` header comment), and audit
  logging is off by default in this project, so an audit-only signal would be invisible anyway.

- **D-04:** `Defaults.MAX_REDACTION_BODY_CHARS` is **repurposed as the window width** and keeps its
  name; only its KDoc changes. Consequence: an input at or below 1 MB is a single pass with behaviour
  and cost **identical to today** for the overwhelming majority of payloads. No new configuration
  surface. Rejected: renaming to `REDACTION_WINDOW_CHARS` + `REDACTION_BUDGET_MS` (grep-and-replace
  across `Defaults`, `Redaction`, `RedactionTest`, and the old name appears in Phase 13 planning
  docs), and exposing window/budget in Privacy settings (a user who sets the budget to 0 silently
  disables body redaction — exactly the class of bug this phase exists to kill).

### Custom patterns × PrivacyMode.OFF — PRIV-06 / SC5

- **D-05:** Custom patterns **always apply, including under `PrivacyMode.OFF`**. They are a user's
  "never send this, ever" list, independent of the privacy mode — which is what someone typing their
  own regex means. `OFF` therefore means *"no built-in redaction"*, not *"no redaction at all"*. The
  custom-pattern loop moves **outside** the `policy.redactTokens` branch. Rejected: OFF-means-off (a
  user who added a pattern for a corporate token and later flips to OFF for a debugging session leaks
  it with no warning), and always-apply-plus-opt-out (a third state across the STRICT/BALANCED/OFF
  matrix on a panel whose value is that privacy is simple).

- **D-06:** D-05 is enforced **structurally**: the caller-side OFF short-circuits are **deleted**, so
  every caller passes `RedactionPolicy.fromMode(mode)` unconditionally and OFF is expressed purely as
  a policy in one place. Two sites bypass `Redaction.apply` entirely today —
  `scanner/PassiveAiScannerAnalysis.kt:394` and `mcp/McpToolContext.kt:62`. Without this, D-05 would
  be true inside `Redaction.kt` and false in practice on the exact path PRIV-05 is about. A fourth
  `RedactionPolicy` flag alone would have fixed the unit test, not the leak.

  **Do NOT touch** the other `PrivacyMode.OFF` checks — they gate *value inclusion*, not redaction,
  and are legitimate: `mcp/tools/McpToolLegacy.kt:344`, `mcp/tools/McpToolExecutorImpl.kt:479`,
  `scanner/ActiveAiScanner.kt:979`, `scanner/AdaptivePayloadEngine.kt:51-53`, and the UI
  warning-banner logic in `ui/SettingsPanelActions.kt`, `ui/SettingsPanelMcpTabs.kt:607`,
  `ui/MainTab.kt:605-607`, `ui/components/PrivacyPill.kt`.

- **D-07:** The three user-facing strings that claim OFF means no redaction are **corrected in this
  phase**: `ui/ChatPanel.kt:1146` (`"Privacy: OFF (no redaction)"`),
  `ui/components/ContextPreviewDialog.kt:122` (`"  (no redaction; raw traffic will be sent)"`), and
  the `PrivacyConfigPanel` OFF notice. Reword to convey that built-in redaction is disabled but the
  user's custom patterns still apply — ideally conditioned on whether patterns are actually
  configured. Same argument as Phase 20's D-12: leaving a user-facing claim false for five phases is
  worse than a small overlap with DOC-03. **The GitBook site and the security advisory stay with
  DOC-03 / Phase 26.**

- **D-08:** Both PRIV-06 decisions are recorded as **one ADR-14** in repo-root `DECISIONS.md`
  (last existing entry is ADR-13), titled around *"redaction never fails open"*. D-01…D-04 and D-05
  answer the same question — what does the pipeline do when it cannot or will not fully redact? — and
  the answer in both cases is *fail closed and tell the user*. One principle future contributors
  inherit, rather than two cross-referencing entries. SC5's literal text asks only for the OFF half;
  this deliberately widens it, because the fail-open-above-1 MB bug is exactly what recurs when the
  reasoning is not written down.

### Claude's Discretion

Two gray areas were deliberately delegated by the maintainer. Both carry a recommendation that phase
research should confirm or overturn — treat them as **open**, not settled.

- **Cookie fix placement and policy (SC1, SC2).**
  Where does the fix live — the emitter (`PassiveAiScannerAnalysis` stops producing bare
  `name=value`), the redactor (`Redaction` gains a rule that catches cookie-shaped lines wherever
  they appear), or both? And is **every** cookie value redacted, or only sensitive-named ones?

  *Recommendation:* fix it **in `Redaction`**, and redact **every** cookie value in the cookie
  section rather than only sensitive-named ones. Rationale: (a) the maintainer's framing since
  Phase 20 is that fixes must be structural, not something a future edit can silently undo — an
  emitter-only fix re-opens the moment someone adds a new prompt section; (b) cookies are
  near-universally session-bearing, so name-based selectivity is the wrong default and it is exactly
  the selectivity that produced this defect. SC1 asserts per cookie name, which an
  all-cookie-values rule satisfies trivially and durably. A rule keyed on the
  `=== COOKIES ===` / `=== PARAMETERS ===` section structure risks being a
  scanner-prompt-format coupling inside `redact/` — research should weigh a section-scoped rule
  against a context-free `name=value` rule (which is safer but likelier to over-redact) and say
  which it picked and why. SC2's `COOKIE`-type params carry a `(COOKIE)` type suffix in the emitted
  line, which is a usable discriminator.

- **Sensitive-key matching mechanism (SC3).**
  How to recognise `auth_token`, `api-key`, `X-Session-Id`, `remember_me`, `JSESSIONID`,
  `PHPSESSID`, `connect.sid` **without** over-redacting `keyboard_layout` or `codename`. Both
  directions are asserted by SC3.

  *Recommendation:* separator-aware token matching (treat `_`, `-`, `.` and case boundaries as
  delimiters and match whole tokens, so `auth_token` hits on the `auth`/`token` tokens while
  `keyboard_layout` does not hit on the substring `key`), **plus** an explicit list of known
  session-cookie names for the vendor forms that no morphological rule catches (`JSESSIONID`,
  `PHPSESSID`, `connect.sid`, `ASP.NET_SessionId`), **plus** a small benign-key guard for the known
  false positives. Note the blast radius: `SENSITIVE_KEYS` feeds **three** regexes
  (`urlTokenParamRegex`, `formBodyParamRegex`, `jsonSecretKeyRegex`), so widening it widens
  query-string, form-body and JSON redaction simultaneously — that is desirable but must be
  regression-tested in all three, and `.planning/codebase/CONCERNS.md` §"Redaction regex coverage
  gaps" sets the protocol: add the pattern, add the test, never loosen an existing pattern without
  documenting why.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### The defects and their evidence
- `.planning/notes/2026-08-05-code-review.md` §"F2" — the cookie leak with the verified
  redacted/not-redacted table per cookie name, and the note that `request.parameters()` is the second
  entry point. **Read this first.**
- `.planning/notes/2026-08-05-code-review.md` §"F5" — the fail-open-above-1 MB finding and the
  custom-patterns-under-OFF observation that became SC5.

### Requirements and success criteria
- `.planning/REQUIREMENTS.md` §"Privacy & Redaction (PRIV) — core value" — PRIV-05, PRIV-06.
- `.planning/ROADMAP.md` §"Phase 21" — the six success criteria. SC1 and SC3 are asserted
  **per name and in both directions** (redacted names AND benign names untouched); SC6 requires the
  existing `RedactionTest` suite including the RFC 5869 HKDF vector to stay green.

### Prior locked decisions that constrain this phase
- `.planning/phases/13-privacy-redaction-hardening/13-CONTEXT.md` §decisions — the redaction design
  this phase extends: hand-curated regex style, `[REDACTED]` replacement token, custom patterns
  active in STRICT+BALANCED (which D-05 now widens to OFF), 50 ms per-pattern ReDoS deadline,
  save-time `SafeRegex.isPatternSafe` validation, patterns persisted plaintext (config, not secrets).
- `.planning/phases/20-mcp-access-control-correctness/20-CONTEXT.md` §decisions D-06/D-07/D-09 and
  §specifics — the rate-limited dual-destination logging shape D-03 mirrors, and the "structural, not
  reorderable" framing behind D-06 and the cookie-placement recommendation. D-12 is the precedent for
  D-07 (correct user-facing claims in-phase rather than deferring to the docs phase).
- `DECISIONS.md` §ADR-5 — "Privacy redaction runs pre-flight and is a user-visible mode, not a silent
  default". This is why D-03 requires user-visible truncation and why D-07 is in scope. ADR-14 is
  the next free number.
- `DECISIONS.md` §ADR-9 — real HKDF for STRICT host anonymization. SC6 forbids perturbing it.
- `.planning/codebase/CONCERNS.md` §"Redaction regex coverage gaps" — documents the known
  hand-curated-regex gaps and the **protocol for tightening** (add pattern → add test → do not loosen
  existing patterns without documenting why). Binding on the SC3 work.
- `CLAUDE.md` §Constraints — STRICT/BALANCED/OFF stay user-visible and pre-flight; redaction is
  hand-curated regex + HKDF host anonymization; audit defaults to disabled.

### Files this phase changes
- `src/main/kotlin/com/six2dez/burp/aiagent/redact/Redaction.kt` — the whole body-stage rework.
- `src/main/kotlin/com/six2dez/burp/aiagent/config/Defaults.kt:54-58` — `MAX_REDACTION_BODY_CHARS`
  KDoc, plus whatever budget constant D-02 needs.
- `src/main/kotlin/com/six2dez/burp/aiagent/scanner/PassiveAiScannerAnalysis.kt:232-252, 372-375, 394`
  — the two leak entry points and the OFF short-circuit.
- `src/main/kotlin/com/six2dez/burp/aiagent/mcp/McpToolContext.kt:62` — the other OFF short-circuit.
- `src/main/kotlin/com/six2dez/burp/aiagent/ui/ChatPanel.kt:1146`,
  `src/main/kotlin/com/six2dez/burp/aiagent/ui/components/ContextPreviewDialog.kt:122`, and the
  `PrivacyConfigPanel` OFF notice — D-07.
- `src/test/kotlin/com/six2dez/burp/aiagent/redact/RedactionTest.kt` — extend; see the inversion note
  in `<specifics>`.
- `DECISIONS.md` — ADR-14.

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `redact/SafeRegex.kt` — `replaceAllSafe(input, pattern, replacement, timeoutMs)` with a
  `DeadlineCharSequence` that throws once a nanoTime deadline passes, and a documented **fail-soft**
  contract (never throws into the pipeline; returns a safe fallback). `DEFAULT_TIMEOUT_MS = 50`. This
  is the per-pattern bound D-01 composes with; D-02's total budget wraps around it. Deliberately
  AWT-free and free of external dependencies — preserve that (it is why D-03 rules out `AuditLogger`).
- `scanner/PassiveAiScannerAnalysis.kt` `maybeLogBackoff` and `backends/cli/CliBackend.kt:28`
  `availabilityLogged` — the two established rate-limited-logging patterns. Reuse for D-03; do not
  invent a third.
- `redact/Redaction.kt:88-89` `SENSITIVE_KEYS` — one `private const val` alternation shared by
  `urlTokenParamRegex`, `formBodyParamRegex` and `jsonSecretKeyRegex`. Single point of change for
  SC3, and single point of blast radius.
- `redact/Redaction.kt:80-81` `cookieHeaderRegex` / `setCookieHeaderRegex` — correct for real header
  lines and already exercised by `sanitizeHeadersForPrompt` output. They are not the bug; the bug is
  data that never reaches them.
- `redact/SecretShapes.kt` and `redact/SecretTripwire.kt` — the Phase 13/15 curated shape set and
  pre-send tripwire. Not in scope, but they are the second line of defence and the
  `ContextPreviewDialog` banner already surfaces survivors.

### Established Patterns
- `Redaction` is a stateless `object` of pure functions over the raw message string, plus one
  `@Volatile` mutable field (`compiledCustomPatterns`) written from the EDT on save and read from the
  redaction thread. Body-stage state introduced by D-01/D-02 must not break that — prefer locals over
  new fields.
- Redaction is applied over the **whole prompt/message string**, not per-field. The passive scanner
  builds one `metadataText` blob and redacts it once (`:395-401`). That is why the SC1/SC2 fix is a
  pattern-reach problem, not a call-site problem.
- Hand-curated regex is the mandated style (CLAUDE.md, ADR-5). No JSON/cookie parser dependency.
- The whole file is `(?im)`-flag line-anchored where it deals with headers. Section-scoped rules
  should follow the same multiline discipline.

### Integration Points
- `Redaction.apply` has **five** call sites: `context/ContextCollector.kt:52-53`,
  `mcp/tools/McpToolExecutorImpl.kt:986`, `mcp/McpToolContext.kt:65`,
  `prompts/bountyprompt/BountyPromptTagResolver.kt:79-80`, `scanner/PassiveAiScannerAnalysis.kt:397`.
  D-01/D-02 change the cost and behaviour of all five — check none of them run on the EDT
  (`ContextCollector` and `BountyPromptTagResolver` are the ones to verify; note Phase 23 owns the
  EDT work generally, so **report** rather than fix anything found).
- `App.kt:90` seeds `Redaction.setCustomPatterns(settings.customRedactionPatterns)` at init;
  `ui/SettingsPanelSettingsIO.kt:475` refreshes it on save (edits take effect without restart, per
  Phase 13). D-05 does not change either — only where the compiled list is consulted.
- `config/AgentSettings.kt:156, 428, 539, 703` — `customRedactionPatterns` persistence. Untouched.

</code_context>

<specifics>
## Specific Ideas

- **`RedactionTest.oversizeBodySkippedSafely` (`:379-397`) currently asserts the fail-open as correct
  behaviour** — its comment reads *"The over-cap secret may remain (documented size-cap behaviour)"*
  and it only checks that the call returns quickly and non-empty. D-01 inverts that contract. This
  test must be rewritten, not extended, and the rewrite is effectively SC4's acceptance gate: build
  an oversized body with a secret embedded **past** the old 1 MB cut-off and assert the secret does
  not survive. A test that passes both before and after the fix has not tested the defect — same
  discipline as Phase 20's SC4.
- The maintainer's framing across Phase 20 and this discussion: a fix that only satisfies the unit
  test while the real path still leaks is not a fix. That is what drove D-06 (delete the
  short-circuits) over the smaller fourth-flag option, and it should drive the cookie-placement call
  in the discretion block too.
- `OFF` becoming "no built-in redaction" rather than "no redaction" is a **user-visible semantic
  change**, which is why D-07 travels with D-05 rather than deferring to DOC-03. The pair is the
  decision; shipping one without the other misdescribes the product.
- SC1's cookie list to test verbatim: `JSESSIONID`, `PHPSESSID`, `connect.sid`, `auth_token`,
  `csrftoken`. SC3 adds `api-key`, `X-Session-Id`, `remember_me` as must-redact and
  `keyboard_layout`, `codename` as must-NOT-redact.

</specifics>

<deferred>
## Deferred Ideas

- **GitBook privacy page + the SEC-04/PRIV-05 security advisory** — DOC-03, Phase 26. D-07 covers
  only the in-repo UI strings. The advisory needs to state that v0.9.0–v0.9.2 leaked cookie values in
  STRICT and BALANCED.
- **EDT exposure of `Redaction.apply` call sites** — Phase 23 / REL-05 owns EDT confinement. If
  D-01/D-02 reveal a call site running on the EDT, record it for Phase 23 rather than fixing it here.
- **A `ContextPreviewDialog` banner for budget-driven truncation** — raised and set aside; D-03's
  payload marker plus the Output-tab line is the agreed surface for this phase. Revisit if the
  truncation path turns out to fire often in practice.
- **Vendor-specific auth headers** (`x-shopify-access-token`, `stripe-signature`) — documented in
  `.planning/codebase/CONCERNS.md` §"Redaction regex coverage gaps" as a known `authHeaderRegex`
  limitation. Adjacent to SC3 but not required by it; fold in only if the SC3 mechanism makes it
  free.

</deferred>

---

*Phase: 21-redaction-completeness*
*Context gathered: 2026-08-11*
