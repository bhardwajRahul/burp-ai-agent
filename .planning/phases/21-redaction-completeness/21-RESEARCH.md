# Phase 21: Redaction Completeness - Research

**Researched:** 2026-08-11
**Domain:** Hand-curated regex redaction pipeline (Kotlin/JVM 21), prompt-emitter/redactor contract, bounded regex execution
**Confidence:** HIGH — every behavioural claim below was executed against JDK 21.0.11 with the project's live regexes, not reasoned about

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

**Oversized bodies — PRIV-06 / SC4**

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

**Custom patterns × PrivacyMode.OFF — PRIV-06 / SC5**

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
  section rather than only sensitive-named ones. […] A rule keyed on the
  `=== COOKIES ===` / `=== PARAMETERS ===` section structure risks being a
  scanner-prompt-format coupling inside `redact/` — research should weigh a section-scoped rule
  against a context-free `name=value` rule (which is safer but likelier to over-redact) and say
  which it picked and why. SC2's `COOKIE`-type params carry a `(COOKIE)` type suffix in the emitted
  line, which is a usable discriminator.

- **Sensitive-key matching mechanism (SC3).**
  How to recognise `auth_token`, `api-key`, `X-Session-Id`, `remember_me`, `JSESSIONID`,
  `PHPSESSID`, `connect.sid` **without** over-redacting `keyboard_layout` or `codename`. Both
  directions are asserted by SC3.

  *Recommendation:* separator-aware token matching […] **plus** an explicit list of known
  session-cookie names for the vendor forms that no morphological rule catches […] **plus** a small
  benign-key guard for the known false positives. Note the blast radius: `SENSITIVE_KEYS` feeds
  **three** regexes (`urlTokenParamRegex`, `formBodyParamRegex`, `jsonSecretKeyRegex`) […] must be
  regression-tested in all three, and `.planning/codebase/CONCERNS.md` §"Redaction regex coverage
  gaps" sets the protocol: add the pattern, add the test, never loosen an existing pattern without
  documenting why.

### Deferred Ideas (OUT OF SCOPE)

- **GitBook privacy page + the SEC-04/PRIV-05 security advisory** — DOC-03, Phase 26. D-07 covers
  only the in-repo UI strings. The advisory needs to state that v0.9.0–v0.9.2 leaked cookie values in
  STRICT and BALANCED.
- **EDT exposure of `Redaction.apply` call sites** — Phase 23 / REL-05 owns EDT confinement. If
  D-01/D-02 reveal a call site running on the EDT, record it for Phase 23 rather than fixing it here.
- **A `ContextPreviewDialog` banner for budget-driven truncation** — raised and set aside; D-03's
  payload marker plus the Output-tab line is the agreed surface for this phase.
- **Vendor-specific auth headers** (`x-shopify-access-token`, `stripe-signature`) — documented in
  `.planning/codebase/CONCERNS.md` §"Redaction regex coverage gaps" as a known `authHeaderRegex`
  limitation. Adjacent to SC3 but not required by it; fold in only if the SC3 mechanism makes it
  free.
- Host anonymization behaviour (SC6 forbids perturbing it); `SecretShapes` / the Phase 15 tripwire.
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| **PRIV-05** | Cookie values do not reach an AI backend in STRICT or BALANCED by any path. The `=== COOKIES ===` section is redacted. Sensitive-key matching recognises real-world names (`JSESSIONID`, `PHPSESSID`, `connect.sid`, `auth_token`, `csrftoken`, `remember_me`) rather than only exact members of `SENSITIVE_KEYS`. Covered by a test asserting each name in both modes. | §"Decision 1 — Cookie fix placement" (verified rule set, 28/28 assertions green in the reference implementation); §"Decision 2 — Sensitive-key matching" (31 must-redact / 21 must-not-redact, both directions, strictly monotone widening); §"Code Examples" 1–3 |
| **PRIV-06** | Redaction never fails open. A payload above `MAX_REDACTION_BODY_CHARS` is truncated-and-redacted or refused. Custom patterns × `PrivacyMode.OFF` is an explicit documented decision. | §"Decision 3 — Chunk-and-scan mechanics" (line-boundary windowing proven byte-identical to whole-document processing; unbounded match length measured; `region()`/`replaceAll()` incompatibility proven); §"Decision 4 — Fail-soft vs fail-closed reconciliation"; §"Code Examples" 4–6 |
</phase_requirements>

---

## Summary

Every claim in this document was executed. Four probe programs were run against JDK 21.0.11 using
the project's live regexes copied verbatim from `redact/Redaction.kt`; a fifth is a complete
reference implementation of the recommended pipeline that passes **54/54** assertions covering SC1,
SC2, SC4, D-05 and every existing `RedactionTest` input. The probes are at
`/private/tmp/claude-501/-Users-six2dez-Tools-burp-ai-agent/e3aaf1ca-a71e-4705-8a1e-d122be7cbc07/scratchpad/`
(`RegexProbe.java`, `DesignProbe.java`, `WindowProbe.java`, `RefImpl.java`, `AttackProbe.java`).

**Both discretion items resolve in favour of the maintainer's recommendation, but for sharper
reasons than the ones given, and with two corrections.** The context-free `name=value` rule is not
merely "likelier to over-redact" — it is **structurally incapable of satisfying SC2**, because the
`=== PARAMETERS ===` lines carry a trailing ` (COOKIE)` that defeats any `^name=value$` anchor
(measured: R2 leaves `JSESSIONID=8F3A9C2B7E1D4A6F0B5C8E2D (COOKIE)` completely untouched while
simultaneously mangling `x=1`, `DEBUG=true` and `AAAA==`). And the section-scoped rule has a
**previously unidentified attacker-controlled bypass**: a malicious `Server:` header flows through
`ScanKnowledgeBase.recordTechStack` into the `=== PRIOR KNOWLEDGE ===` block, which is emitted
*before* the real cookie section, so a first-occurrence-only `indexOf` redacts a decoy and leaves the
real cookies intact (proven in `AttackProbe.java`). The rule must iterate **every** occurrence.

The SC3 finding that most changes the plan: **`keyboard_layout` and `codename` are not over-redacted
today** (measured — see the table in §"Decision 2"). SC3's "without over-redacting" limb is therefore
a **regression guard on a new mechanism**, not a fix for existing behaviour. The good news is that
the recommended mechanism needs **zero new sensitive words**: switching from "the key is exactly one
of 12 words" to "the key contains one of the same 12 words as a separator-delimited token" plus a
17-entry vendor-name list satisfies every SC1 and SC3 must-redact case (31/31) with zero regressions
on the must-not-redact corpus (21/21) and is provably **strictly monotone** — no input that redacts
today stops redacting.

On D-01, the phrase "overlap wide enough that a secret straddling a boundary is still matched" is not
achievable as literally written: the value side of `formBodyParamRegex` and `jsonSecretKeyRegex` is
length-unbounded (measured single matches of 200 006 and 200 010 characters), so no finite overlap
constant is defensible. **Cut windows at line boundaries instead** — proven byte-identical to
whole-document processing for all three built-ins, and it eliminates the `(?m)^` correctness trap
entirely rather than papering over it. Naive `substring()` windowing genuinely does corrupt `(?m)^`
(demonstrated: `xxxxxxxxxxkey=BENIGN` is untouched whole but becomes `key=[REDACTED]` when cut).
`Matcher.region()` is the textbook alternative and its bounds flags do behave correctly — but
`Matcher.replaceAll()` **silently resets the region** (proven: `region(1,3)` then `replaceAll` on
`"xxxxx"` yields `"YYYYY"`, not `"xYYxx"`), so `region()` cannot be combined with the replacement API
the pipeline uses. Line-boundary windowing avoids needing it.

**Primary recommendation:** Keep the fix in `redact/`. Add two cookie rules (a multi-occurrence
section-scoped all-values rule, and a context-free ` (COOKIE)`-type-suffix rule), replace the
`SENSITIVE_KEYS` alternation with a token-boundary key *expression* plus a vendor list, and rewrite
the body stage as a line-boundary window loop over `SafeRegex` with a new timeout-reporting sibling
API so fail-soft can be converted to fail-closed. All three built-in body rules can move onto
`SafeRegex` with **no new replacement API** because their Kotlin lambdas are byte-for-byte equivalent
to `$n` replacement strings (verified).

---

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Deciding *what* is sensitive (vocabulary, key shapes, cookie policy) | `redact/Redaction.kt` | — | Single point of policy; ADR-5 makes redaction a pre-flight control, not a per-caller concern |
| Bounding regex execution time | `redact/SafeRegex.kt` | `redact/Redaction.kt` (budget composition) | `SafeRegex` already owns the per-pattern deadline; the total budget is a redaction-policy concern, not a regex-utility concern |
| Producing prompt text (section headers, `name=value` shapes, ` (TYPE)` suffixes) | `scanner/PassiveAiScannerAnalysis.kt` | — | The emitter is the only tier that knows a string is cookie data |
| Deciding *whether* redaction runs | `redact/RedactionPolicy` | — | **D-06 moves this here from the callers.** Today `PassiveAiScannerAnalysis:394` and `McpToolContext:62` each hold a copy of the decision |
| Surfacing truncation to the user | `redact/Redaction.kt` (signal) | `App.kt` (wiring), Burp Output tab (sink) | `redact/` must stay AWT-free and `AuditLogger`-free; a settable callback is the established seam (`BackendDiagnostics`) |
| Persisting custom patterns | `config/AgentSettings.kt` | `ui/SettingsPanelSettingsIO.kt` | Untouched by this phase |
| Post-redaction leak detection | `redact/SecretTripwire.kt` / `SecretShapes.kt` | `ui/components/ContextPreviewDialog.kt` | Out of scope; second line of defence |

**Tier misassignment risk this phase must avoid:** putting the cookie fix in the emitter tier only.
That tier *can* fix it, but it cannot make the fix survive a new prompt section. Conversely, putting
prompt-format knowledge in `redact/` is a genuine coupling — mitigated below by making it a shared
compile-time constant plus a parity test, not a duplicated string literal.

---

## Standard Stack

### Core

**No new libraries. No new dependencies of any kind.** This phase is pure Kotlin using the JDK's
`java.util.regex` engine and the project's existing `redact/` package.

| Component | Version | Purpose | Why Standard |
|-----------|---------|---------|--------------|
| `java.util.regex` (JDK) | JDK 21.0.11 | All matching | CLAUDE.md mandates hand-curated regex; ADR-5 records the decision. A JSON/cookie parser dependency is explicitly forbidden |
| Kotlin | as configured (JVM 21) | Implementation | ADR-1 |
| JUnit Jupiter | 6.0.3 | Tests | `.planning/codebase/TESTING.md` |
| Mockito-Kotlin | 5.4.0 | Montoya mocks (only if the param-line test needs `ParsedHttpParameter`) | `.planning/codebase/TESTING.md` |

`[VERIFIED: local toolchain]` `/usr/libexec/java_home -v 21` → `/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home`, `openjdk 21.0.11 2026-04-21 LTS`. Gradle 8.12.1 per `gradle/wrapper/gradle-wrapper.properties`.

### Alternatives Considered

| Instead of | Could Use | Why rejected |
|------------|-----------|--------------|
| Hand-curated regex | A cookie/JSON parser (`kotlinx-serialization` is already on the classpath) | Forbidden by CLAUDE.md and the phase brief; also wrong for the actual data (the prompt blob is not JSON, it is a mixed free-text/structured document) |
| `Matcher.region()` windowing | Substring windowing | `replaceAll()` resets the region (proven below) — `region()` forces a manual `find()`/`appendReplacement()` rewrite of the entire body stage for no correctness gain over line-boundary cuts |
| New `SafeRegex` lambda-replacement API | `$n` replacement strings | Unnecessary — the three built-in lambdas are provably equivalent to `$1$2=[REDACTED]` and `$1"[REDACTED]"` (verified) |

## Package Legitimacy Audit

**Not applicable — this phase installs zero external packages.** No `npm install`, no `pip install`,
no new Gradle dependency. The Package Legitimacy Gate protocol was evaluated and found to have no
inputs: the phase's entire surface is edits to seven existing files plus `DECISIONS.md`. No
`checkpoint:human-verify` gate for package installs is required.

---

## Architecture Patterns

### System Architecture Diagram

```
                        ATTACKER-CONTROLLED DATA
                                  │
        ┌─────────────────────────┴──────────────────────────┐
        │                                                    │
   HTTP request                                        HTTP response
   (headers, params, cookies)                          (headers, body)
        │                                                    │
        ▼                                                    ▼
┌──────────────────────────────────────────────────────────────────────┐
│  EMITTER TIER — scanner/PassiveAiScannerAnalysis.doAnalysis()        │
│                                                                      │
│  ScanKnowledgeBase.buildContextSummary(host)  ──► "=== PRIOR KNOW…"  │
│      ▲ Server / X-Powered-By headers land here  [BYPASS VECTOR]      │
│  sanitizeHeadersForPrompt(...)                ──► "=== REQUEST HDRS" │
│  request.parameters().map { "$n=$v (${type})" } ► "=== PARAMETERS ==="│
│  headers["Cookie"].split(";").map(String::trim) ► "=== COOKIES ==="  │
│                              └─► BARE name=value  ◄── PRIV-05 LEAK   │
│  buildCompactRequest/ResponseBody(...)        ──► "=== … BODY ==="   │
│                                                                      │
│                     one metadataText blob (String)                   │
└──────────────────────────────┬───────────────────────────────────────┘
                               │
                    [D-06 DELETES THIS BRANCH]
                    if (mode == OFF) skip  ────────────► leak path
                               │
                               ▼
┌──────────────────────────────────────────────────────────────────────┐
│  REDACTOR TIER — redact/Redaction.apply(raw, policy, salt)           │
│                                                                      │
│  ┌── stripCookies ──────────────────────────────────────────────┐    │
│  │  cookieHeaderRegex      -> "Cookie: [STRIPPED]"              │    │
│  │  setCookieHeaderRegex   -> "Set-Cookie: [STRIPPED]"          │    │
│  │  redactCookieSection()  -> ALL values in EVERY  [NEW / SC1]  │    │
│  │                            "=== COOKIES ===" span            │    │
│  │  cookieTypedParamRegex  -> "$1=[REDACTED]$3"   [NEW / SC2]   │    │
│  └──────────────────────────────────────────────────────────────┘    │
│  ┌── redactTokens (header stage — unbounded, unchanged) ────────┐    │
│  │  authHeader | bearer | basicAuth | jwt | urlTokenParam       │    │
│  └──────────────────────────────────────────────────────────────┘    │
│  ┌── BODY STAGE  [REWRITTEN / D-01..D-05] ──────────────────────┐    │
│  │  rules = (redactTokens ? [form, json] : []) + customPatterns │    │
│  │  if rules.isEmpty()      -> return unchanged   (OFF fast path)│   │
│  │  if len <= WINDOW        -> single pass  (== today's cost)   │    │
│  │  else                                                        │    │
│  │    while chars remain && budget remains:                     │    │
│  │       window = next line-boundary slice (<= WINDOW)          │    │
│  │       for rule in rules:                                     │    │
│  │          SafeRegex.replaceAllSafeReporting(min(50, left))    │    │
│  │          timedOut? -> halve & retry (depth<=2) else DROP     │    │
│  │       ok    -> append redacted window                        │    │
│  │       !ok   -> append marker  [FAIL CLOSED]  + signal        │    │
│  │    budget spent -> append tail marker + signal, STOP         │    │
│  └────────────────────────┬─────────────────────────────────────┘    │
│  ┌── anonymizeHosts ──────┴─────────────────────────────────────┐    │
│  │  hostHeaderRegex -> HKDF host-<12hex>.local   [DO NOT TOUCH] │    │
│  └──────────────────────────────────────────────────────────────┘    │
└──────────────┬───────────────────────────────────┬───────────────────┘
               │                                   │
    truncationSignal callback              redacted String
               │                                   │
               ▼                                   ▼
   Burp Output tab (rate-limited)     SecretTripwire.detectAndBuild
   via App.kt-wired lambda            (MCP path) / ContextPreviewDialog
               │                                   │
               └──────────► USER          ─────────┴──► AI BACKEND
```

**Reading the diagram:** the leak is not a call-site problem — `Redaction.apply` is invoked on the
right string at the right time. It is a *reach* problem in the redactor tier plus a *bypass* in the
`if (mode == OFF)` branch. The two `[NEW]` rules and the `[D-06 DELETES]` annotation are the whole of
PRIV-05; the `[REWRITTEN]` box is the whole of PRIV-06.

### Recommended file layout (no new files required)

```
src/main/kotlin/com/six2dez/burp/aiagent/
├── redact/
│   ├── Redaction.kt                 # vocabulary, cookie rules, body stage, truncation signal
│   └── SafeRegex.kt                 # + replaceAllSafeReporting (new sibling; existing API unchanged)
├── config/Defaults.kt               # MAX_REDACTION_BODY_CHARS KDoc + MAX_REDACTION_BUDGET_MS
├── scanner/
│   ├── PassiveAiScannerAnalysis.kt  # delete OFF short-circuit; import the shared section constant
│   └── PassiveAiScannerPrompts.kt   # (candidate home for the extracted pure prompt builder)
├── mcp/McpToolContext.kt            # delete OFF short-circuit
└── ui/{ChatPanel,components/ContextPreviewDialog,SettingsPanelActions}.kt   # D-07 strings
```

### Pattern 1: Emitter labels, redactor enforces — with a compile-coupled constant

**What:** `Redaction` owns the section-header string as a `const val`; `PassiveAiScannerAnalysis`
imports it instead of writing `"=== COOKIES ==="` inline.
**When to use:** whenever a redaction rule must key on a format another tier produces.
**Why:** the maintainer's objection to a section-scoped rule is that a future rename silently
disables it. A shared constant converts that silent drift into a compile-time coupling. Add a parity
test (precedent: `McpToolParityTest.registeredToolIds_matchCatalog`) asserting the emitted blob
actually contains the constant.

### Pattern 2: Type-suffix discrimination for parameters

**What:** the `=== PARAMETERS ===` rule keys on the trailing ` (COOKIE)` produced by
`"${p.name()}=$value (${p.type().name})"` (`PassiveAiScannerAnalysis.kt:240`), not on the section
header.
**Why:** `HttpParameterType.COOKIE` is a Montoya *semantic* label, not a display choice — it is far
more stable than a section header, and the rule works wherever the shape appears. This makes the SC2
half genuinely context-free while the SC1 half stays section-scoped. Note the asymmetry is
deliberate and should be stated in the KDoc.

### Pattern 3: Line-boundary windowing

**What:** `windowEnd(s, start, width)` returns the index just past the last `'\n'` at or before
`start + width`; a single line longer than `width` becomes its own (oversized) window rather than
being split.
**Why:** proven byte-identical to whole-document processing (WindowProbe C5). It removes the `(?m)^`
hazard by construction rather than by careful flag management, and it removes the need for an overlap
constant that provably cannot be sized correctly.

### Pattern 4: Settable diagnostic callback on a stateless object

**What:** `@Volatile var truncationLogger: ((String) -> Unit)? = null` on `Redaction`, wired in
`App.kt` next to the existing `Redaction.setCustomPatterns(...)` seeding at `App.kt:88-90`.
**Why:** exactly the shape of `backends/BackendDiagnostics.output` (`@Volatile var output: ((String) -> Unit)?`),
so it is an established in-repo idiom rather than a new one. Keeps `redact/` AWT-free and
`AuditLogger`-free per D-03. A test sets a capturing lambda; production sets
`api.logging()::logToOutput`.

### Anti-Patterns to Avoid

- **First-occurrence-only section scoping** (`text.indexOf(HEADER)`): proven bypassable via an
  attacker-controlled `Server:` header. Always iterate every occurrence.
- **Naive `substring()` windowing of a single line**: proven to change `(?m)^` semantics.
- **Combining `Matcher.region()` with `Matcher.replaceAll()`**: proven to silently ignore the region.
- **Assuming `SafeRegex.replaceAllSafe`'s return value tells you anything about success**: on timeout
  it returns the input unchanged, indistinguishable from "no matches". Under D-02 this is the single
  most dangerous property in the phase.
- **Adding new words to the sensitive vocabulary to solve SC3**: unnecessary (measured) and it
  multiplies the blast radius across three regexes.
- **Emitting one marker per dropped window**: a 200 MB input would produce ~200 markers. Coalesce
  the budget-exhausted tail into exactly one marker.
- **Unbounded `*` quantifiers around the new key expression**: measured 2.3× slower than `{0,64}` on
  an adversarial input (21 ms vs 9 ms on 1 MB).

---

## Decision 1 — Cookie fix placement and policy (SC1, SC2) — **RESOLVED: confirm the recommendation, with two corrections**

### What the emitter actually produces `[VERIFIED: source read]`

`scanner/PassiveAiScannerAnalysis.kt`:

| Line | Code | Emitted shape |
|------|------|---------------|
| 247-252 | `headers.filter{name=="Cookie"}.flatMap{ it.value().split(";").map(String::trim) }.take(6)` | `JSESSIONID=8F3A9C2B7E1D4A6F0B5C8E2D` — bare pair, one per line, `Cookie:` prefix gone, max **6** cookies (`COOKIES_MAX_COUNT`) |
| 232-241 | `"${p.name()}=$value (${p.type().name})"`, value truncated to 200 chars (`PARAM_VALUE_MAX_CHARS`) via `truncateWithEllipsis` (appends literal `"..."`) | `JSESSIONID=8F3A9C2B… (COOKIE)`, `q=red running shoes (URL)`, `quantity=2 (BODY)` |
| 372-375 | `appendLine("=== COOKIES ===")` then the cookie lines then `appendLine()` | section terminated by a blank line |
| 377-381 | `appendLine("=== PARAMETERS ===")` then the param lines then `appendLine()` | same |
| 243-244 | `sanitizeHeadersForPrompt` (`PassiveAiScannerFilters.kt:162`) | `Cookie: <value truncated to 120 chars>` — **this line IS caught** by `cookieHeaderRegex`, confirming F2's diagnosis that only the dedicated section leaks |

`grep` confirms `"=== COOKIES ==="` and `"=== PARAMETERS ==="` appear **only** at
`PassiveAiScannerAnalysis.kt:373` and `:378` in the whole repo. `[VERIFIED: grep]`

### Every other emitter that could produce the same shape `[VERIFIED: grep + source read]`

The five `Redaction.apply` call sites and what they feed it:

| Call site | Input shape | Bare `name=value` lines? | Live in production? |
|-----------|-------------|--------------------------|---------------------|
| `scanner/PassiveAiScannerAnalysis.kt:397` | the `metadataText` blob | **yes** — the two sections above | **yes** (scanner threads) |
| `mcp/McpToolContext.kt:65` (`redactIfNeeded`) | arbitrary MCP tool output | no dedicated cookie section; raw `HttpRequest.toString()` keeps real `Cookie:` headers | **yes** (MCP threads) |
| `mcp/tools/McpToolExecutorImpl.kt:986` (`redact_preview` tool) | user-supplied text | whatever the user pastes | **yes** (MCP threads) |
| `context/ContextCollector.kt:52-53` | `HttpRequestResponse.toString()` — real headers | no | **NO — `fromRequestResponses` and `fromAuditIssues` have zero callers in `src/main`; only `ContextPreviewConsistencyTest` calls them** |
| `prompts/bountyprompt/BountyPromptTagResolver.kt:79-80` | `rr.request().toString()` — real headers | no | **NO — `resolve()` has zero callers in `src/main`; only `BountyPromptTagResolverTest`** |

`[VERIFIED: grep]` `grep -rn "fromRequestResponses\|fromAuditIssues\|BountyPromptTagResolver" src/`
returns only test matches outside the defining files. `ContextCapture` is constructed only inside
`ContextCollector`, and `MainTab.openChatWithContext` / `ChatPanel.startSessionFromContext` have no
production callers either.

**Two consequences for the planner.** (1) The performance/behaviour blast radius of D-01/D-02 is
**three** live call sites, not five — and none of the three is on the EDT (`PassiveAiScanner` runs on
Burp scanner threads; MCP handlers run on Ktor/tool threads). **Nothing to report to Phase 23 from
this phase.** (2) Report to the maintainer as an incidental finding, not to fix here: two public
context-capture entry points appear to be dead code. That is out of scope for Phase 21.

### The three candidate rules, measured `[VERIFIED: DesignProbe.java §B/§B2]`

| | R1 — section-scoped, all values | R2 — context-free `^name=value$` | R3 — ` (COOKIE)` type suffix |
|---|---|---|---|
| SC1 (cookie section) | ✅ all 5 names, values gone | ✅ all 5 names | ❌ n/a (no type suffix in that section) |
| SC2 (`(COOKIE)` params) | ❌ n/a (different section) | ❌ **fails — `JSESSIONID=8F3A9C2B7E1D4A6F0B5C8E2D (COOKIE)` untouched** | ✅ value gone, name + `(COOKIE)` preserved |
| `q=red running shoes (URL)` | untouched | untouched | untouched ✅ |
| `apikey=sk-abc123&user=bob` | untouched | untouched (value class excludes `&`) | untouched |
| `x=1` on its own line | untouched | **`x=[REDACTED]`** ❌ | untouched |
| `DEBUG=true` in a response body | untouched | **`DEBUG=[REDACTED]`** ❌ | untouched |
| `AAAA==` (base64 padding on a line) | untouched | **`AAAA=[REDACTED]`** ❌ | untouched |
| Couples `redact/` to prompt format | yes (section header) | no | partially (Montoya type name, not a display string) |

**R2 is not a viable option**, and not for the reason the recommendation gives. Its fatal flaw is
that it cannot satisfy SC2 at all — the ` (COOKIE)` suffix breaks the `$` anchor, so you would need
R3 *anyway*, at which point R2's only remaining contribution is the collateral damage in the last
three rows. **Decision: R1 + R3. R2 rejected on capability, not just on over-redaction.**

### Correction 1 — the section rule must iterate EVERY occurrence `[VERIFIED: AttackProbe.java]`

`ScanKnowledgeBase.buildContextSummary` emits `"Detected technologies: ${tech.joinToString(", ")}"`
into the `=== PRIOR KNOWLEDGE ===` block, which the emitter appends **first**
(`PassiveAiScannerAnalysis.kt:345-350`). `tech` is populated at `:272-274` from the response's
`Server`, `X-Powered-By`, `X-AspNet-Version` and `X-Generator` headers — fully attacker-controlled
(`"server" -> techHints.add(value.split("/").first().trim())`; a value with no `/` passes through
whole).

Executed result with `Server: === COOKIES ===`:

```
---- indexOf FIRST occurrence only (vulnerable) ----
=== PRIOR KNOWLEDGE ===
Tech stack: === COOKIES ===
decoy=[REDACTED]                     <- decoy consumed the rule

=== COOKIES ===
JSESSIONID=REAL_SESSION_SECRET       <- LEAKED
abtest_bucket=OPAQUE_VALUE_XYZ       <- LEAKED

---- every occurrence (fixed) ----
JSESSIONID=[REDACTED]
abtest_bucket=[REDACTED]
```

So: **`while ((h = out.indexOf(HEADER, from)) >= 0)`, not `if (indexOf(...) >= 0)`.** Note that the
widened key expression (Decision 2) independently catches `JSESSIONID` even in the vulnerable case —
which is a good argument for keeping *both* mechanisms rather than treating them as redundant. The
cookie with an unremarkable name (`abtest_bucket`) is what only R1 saves.

*Residual, accept and document:* an attacker who injects `=== COOKIES ===` into a header causes
**extra** redaction of their own response content. That is an over-redaction nuisance, never a leak,
and it is strictly better than the alternative.

### Correction 2 — redact every value, and say why in the KDoc

Confirmed. The measured argument: after the Decision-2 widening, name-based selectivity already
catches the five SC1 names. R1 exists for the *sixth* cookie — `abtest_bucket`, `_ga`,
`sessionRefresh`, whatever the target happens to use. Since `COOKIES_MAX_COUNT = 6` bounds the
section to six lines, redacting all of them costs the model at most six opaque values while
preserving all six **names**, which is the analytically useful part (`ScanKnowledgeBase.recordAuthInfo`
already records `authCookieNames` separately for exactly this reason). Full-line stripping
(`Cookie: [STRIPPED]`-style) would destroy the names and is therefore wrong.

### Recommended rule set

```kotlin
// Owned by redact/; PassiveAiScannerAnalysis imports this instead of an inline literal.
const val COOKIE_SECTION_HEADER = "=== COOKIES ==="

// Every name=value pair inside a cookie section. Name preserved, value always redacted —
// cookies are near-universally session-bearing and name-based selectivity is what produced
// PRIV-05. `[^=\r\n]+` stops at the first '=' so a value containing '=' (base64 padding)
// is fully consumed by `.*`.
private val cookieSectionPairRegex = Regex("(?m)^([^=\\r\\n]+)=(.*)$")

// Parameters carry the Montoya HttpParameterType name as a trailing suffix
// (PassiveAiScannerAnalysis.kt:240). Context-free by design: the discriminator is a semantic
// type label, not a prompt-layout choice, so the rule survives a section rename.
private val cookieTypedParamRegex = Regex("(?im)^([^=\\r\\n]+)=(.*?)(\\s\\(COOKIE\\))\\s*$")
```

Both gated on `policy.stripCookies` (true in STRICT and BALANCED, false in OFF) — which places them
naturally beside `cookieHeaderRegex`/`setCookieHeaderRegex` and keeps every OFF test green.

**Where does the fix live?** In `Redaction`, per the recommendation. **Should the emitter change
too?** No — verified unnecessary. Both rules are idempotent with respect to the token rules
(`JSESSIONID=[REDACTED]` re-matched yields `JSESSIONID=[REDACTED]`), so no ordering hazard exists,
and leaving the emitter alone keeps the diff to `redact/` where the tests live.

---

## Decision 2 — Sensitive-key matching mechanism (SC3) — **RESOLVED: confirm the recommendation; drop the benign-key guard; add no new words**

### Baseline: what today's regexes actually do `[VERIFIED: RegexProbe.java]`

`SENSITIVE_KEYS` (`Redaction.kt:88-89`) feeds three regexes. Executed against all three:

| Key | `formBodyParamRegex` today | `jsonSecretKeyRegex` today |
|-----|---------------------------|---------------------------|
| `session` | REDACTED | REDACTED |
| `access_token`, `api_key`, `apikey`, `auth`, `token`, `key`, `secret`, `password`, `pwd`, `sid`, `code` | REDACTED | REDACTED |
| `JSESSIONID`, `PHPSESSID`, `connect.sid`, `auth_token`, `csrftoken`, `remember_me` | **NOT REDACTED** | **NOT REDACTED** |
| `api-key`, `X-Session-Id`, `ASP.NET_SessionId`, `XSRF-TOKEN`, `_csrf`, `laravel_session`, `cf_clearance` | **NOT REDACTED** | **NOT REDACTED** |
| **`keyboard_layout`, `codename`** | **NOT redacted** | **NOT redacted** |
| `sidebar`, `token_bucket_size`, `keychain`, `passwordless_enabled`, `description`, `codes`, `session_timeout_seconds` | NOT redacted | NOT redacted |

**This overturns an assumption embedded in SC3's phrasing.** `keyboard_layout` and `codename` are
*not* over-redacted today, because `formBodyParamRegex` requires `(^|[?&])` immediately before the
word **and** `=` immediately after it — the word is already whole-token-delimited on both sides.
SC3's "without over-redacting" limb is therefore a **regression guard on the new mechanism**, not a
fix. The planner should write those assertions as guards and label them as such; a task framed as
"stop over-redacting `keyboard_layout`" would be a no-op.

### The mechanism

Replace the *word* alternation with a *key expression* that is used identically by all three regexes.
The 12 words are unchanged; only the boundary rule and a vendor list are added.

```kotlin
// UNCHANGED — the 12-word vocabulary from v0.6.0. Widening this multiplies blast radius across
// three regexes; the SC3 corpus is satisfied without adding a single word.
private const val SENSITIVE_WORDS =
    "access_token|api_key|apikey|auth|token|key|secret|password|pwd|session|sid|code"

// Vendor and framework session-cookie names that NO morphological rule can catch, because the
// sensitive word is concatenated without a separator (JSESSIONID, PHPSESSID, csrftoken) or is
// absent entirely (remember_me). Whole-key match, case-insensitive. Dots are escaped.
private const val KNOWN_SESSION_KEYS =
    "jsessionid|phpsessid|asp\\.net_sessionid|\\.aspxauth|aspxauth|csrftoken|" +
        "remember_me|remember_token|laravel_session|ci_session|_session_id|sessionid|sessid|" +
        "cfid|cftoken|xsrf-token|_csrf"

private const val KEY_CHARS = "[A-Za-z0-9_.\\-\\[\\]]"   // '[' ']' cover PHP array params a[b]

// A key name is sensitive when it IS a known vendor name, or when it CONTAINS one of the words
// as a separator-delimited whole token. `(?<![A-Za-z0-9])` / `(?![A-Za-z0-9])` treat '_', '-',
// '.', '[' , ']' and the string boundaries as delimiters, so `auth_token` and `api-key` match
// while `keyboard_layout`, `codename`, `sidebar` and `keychain` do not.
// Prefix/suffix are BOUNDED at 64 chars: an unbounded `*` measured 2.3x slower on adversarial
// input and reintroduces the unbounded-quantifier adjacency the file already warns about.
// Contains only NON-capturing groups so the group numbering of every consumer is unchanged.
private const val SENSITIVE_KEY_EXPR =
    "(?:(?:$KNOWN_SESSION_KEYS)|" +
        "$KEY_CHARS{0,64}(?<![A-Za-z0-9])(?:$SENSITIVE_WORDS)(?![A-Za-z0-9])$KEY_CHARS{0,64})"
```

The three consumers change only in the substitution and in `(...)` → `(?:...)` where the inner group
was unused:

```kotlin
private val urlTokenParamRegex = Regex("(?i)([?&](?:$SENSITIVE_KEY_EXPR)=)[^&\\s\"'<>]+")
private val formBodyParamRegex = Regex("(?im)(^|[?&])($SENSITIVE_KEY_EXPR)=[^&\\s\"'<>]+")
private val jsonSecretKeyRegex =
    Regex("(?i)(\"$SENSITIVE_KEY_EXPR\"\\s*:\\s*)(\"[^\"]*\"|true|false|null|-?\\d+(?:\\.\\d+)?)")
```

Group numbering is preserved: `formBodyParamRegex` group 1 = the `^`/`?`/`&` prefix, group 2 = the
whole key (so `$1$2=[REDACTED]` reproduces the key exactly, including compound forms);
`jsonSecretKeyRegex` group 1 = `"key":` including whitespace, group 2 = the value.

### Both directions, measured `[VERIFIED: DesignProbe.java §A/§A2/§A3]`

**MUST REDACT — 31/31 pass** (`cur` = today, `new` = proposed):

| Key | cur | new | | Key | cur | new |
|---|---|---|---|---|---|---|
| `auth_token` | ✗ | ✅ | | `connect.sid` | ✗ | ✅ |
| `api-key` | ✗ | ✅ | | `remember_me` | ✗ | ✅ |
| `X-Session-Id` | ✗ | ✅ | | `JSESSIONID` | ✗ | ✅ |
| `access_token` | ✅ | ✅ | | `PHPSESSID` | ✗ | ✅ |
| `api_key` / `apikey` | ✅ | ✅ | | `csrftoken` | ✗ | ✅ |
| `auth` / `token` / `key` | ✅ | ✅ | | `ASP.NET_SessionId` | ✗ | ✅ |
| `secret` / `password` / `pwd` | ✅ | ✅ | | `laravel_session` | ✗ | ✅ |
| `session` / `sid` / `code` | ✅ | ✅ | | `user_api_key` | ✗ | ✅ |
| `SESSION` (case) | ✅ | ✅ | | `x-auth-token` | ✗ | ✅ |
| `api.key` | ✗ | ✅ | | `auth-token` | ✗ | ✅ |
| `session_id` | ✗ | ✅ | | `access-token` | ✗ | ✅ |
| `_csrf` | ✗ | ✅ | | `XSRF-TOKEN` | ✗ | ✅ |

**MUST NOT REDACT — 21/21 pass:** `keyboard_layout`, `codename`, `sidebar`, `keychain`,
`passwordless_enabled`, `description`, `codes`, `tokenizer`, `monkey`, `broken`, `secretary`,
`authority`, `encoded`, `decode_me`, `username`, `name`, `email`, `q`, `page`, `filename`, `locale`
— all `cur=false new=false`.

**Monotonicity — verified:** across the full 60-key corpus, **zero** keys go from redacted to
not-redacted. This satisfies the CONCERNS.md protocol clause "do not loosen existing patterns"
without needing a documented exception.

**JSON context — same result:** `auth_token`, `api-key`, `X-Session-Id`, `remember_me`, `JSESSIONID`
all `false → true`; `keyboard_layout`, `codename`, `sidebar`, `name`, `balance`, `description` all
stay `false`. The `sid`/`balance`/`name` assertions in the existing
`bodyJsonUnquotedSecretValuesRedacted` test are unaffected.

### The judgement calls, decided

Newly redacted as a side effect of whole-token matching. All are **over**-redaction (fail-safe
direction) and all lose analytically low-value data:

| Key | New behaviour | Verdict | Reasoning |
|-----|---------------|---------|-----------|
| `token_bucket_size` | redacted | **accept** | A rate-limit integer. Zero analytic loss; `token` is a genuine token boundary |
| `session_timeout_seconds` | redacted | **accept** | Same class |
| `auth_provider` | redacted | **accept, note it** | The only one with real analytic value (`auth_provider=google` tells the model something). Still the correct default: a key literally named `auth_*` is far more often credential-bearing |
| `key_size`, `code_version`, `secret_santa`, `password_hint_enabled` | redacted | **accept** | Low-value config values |
| `authorized_by` | **not** redacted | correct | `authorized` is one token; `auth` is followed by `o` |
| `sidebar` | not redacted | correct | UI state; `sid` is a substring, not a token |
| `keychain` | not redacted | correct | `key` followed by `c` |
| `passwordless_enabled` | not redacted | correct | `passwordless` ≠ `password`; the value is a boolean anyway |
| `description` | not redacted | correct | |
| `codes` | **not** redacted | **accept, document as residual** | Plural forms are not handled. Adding `s?` to the vocabulary would catch `codes`/`tokens`/`keys` at the cost of a second widening axis and six more tests. **Recommend deferring**: record in `.planning/codebase/CONCERNS.md` §"Redaction regex coverage gaps" as a known plural gap with the one-character recipe. SC3 does not require it |

### Drop the benign-key guard

The recommendation proposed "a small benign-key guard for the known false positives". **Measured
result: there are none.** All 21 must-not-redact keys pass on the boundary rule alone. A denylist
would be dead code that future contributors would feel obliged to grow, and every entry is a place
where a real credential could be accidentally allowlisted. **Recommend omitting it entirely** and
saying so in the KDoc so a future reader does not re-add one.

### camelCase — verified working, recommended with an explicit veto point

`authToken`, `accessToken`, `sessionId`, `userSessionId` are extremely common modern JSON keys and
are **not** caught by the separator rule (`auth` is followed by `T`, which is alphanumeric).
`apiKey` *is* caught today only because `(?i)` folds it onto the literal `apikey`.

The non-obvious blocker: **under `(?i)`, `[A-Z]` also matches lowercase**, so camelCase boundaries
cannot be written inline. Java's inline flag-off group solves it. Verified working:

```kotlin
private const val BEFORE = "(?:(?<![A-Za-z0-9])|(?-i:(?<=[a-z0-9])(?=[A-Z])))"
private const val AFTER  = "(?:(?![A-Za-z0-9])|(?-i:(?<=[a-z0-9])(?=[A-Z])))"
// ...$KEY_CHARS{0,64}$BEFORE(?:$SENSITIVE_WORDS)$AFTER$KEY_CHARS{0,64}...
```

`[VERIFIED: DesignProbe.java §A4]` — gains `authToken`, `accessToken`, `userSessionId`
(all `false → true`). Introduces exactly three false positives in the tested corpus: `codeName`,
`keyName`, `tokenCount`. Notably `keyboardLayout` and `monkeyBars` are **not** affected (the
transition after `key`/`monkey` is lower→lower).

**Recommendation: include it**, because `authToken` is a live credential leak class and the FP set is
narrow, over-redacting, and structurally identical to the already-accepted `token_bucket_size` case.
**But flag it as an explicit veto point for the planner/maintainer**: SC3 names `codename` as
must-not-redact and `codeName` is one keystroke away, so a reviewer may reasonably read the FP as
contrary to SC3's spirit. Shipping without it costs one line (`BEFORE`/`AFTER` → the plain
lookarounds) and loses nothing SC3 requires. If it ships, the test corpus must assert `codeName`,
`keyName`, `tokenCount` as *accepted* over-redactions so the behaviour is deliberate and recorded.

### Vendor auth headers (deferred idea)

Folding `x-shopify-access-token` / `stripe-signature` into `authHeaderRegex` is **not** free under
this mechanism — `authHeaderRegex` is a separate exact-name alternation and would need the same
treatment. Leave in CONCERNS per the deferral.

---

## Decision 3 — Chunk-and-scan mechanics (D-01 / D-02)

### The overlap question: there is no principled cap `[VERIFIED: WindowProbe.java §C6]`

Measured maximum single-match lengths:

- `formBodyParamRegex` on `token=` + 200 000 `v` characters → match length **200 006**
- `jsonSecretKeyRegex` on `{"token":"` + 200 000 `v` `"}` → match length **200 010**

The value side of every built-in (`[^&\s"'<>]+`, `"[^"]*"`) is length-unbounded, and user custom
patterns are unbounded by construction (`SafeRegex.isPatternSafe` rejects zero-width matches and
bounds *time*, never *match length*). **Therefore no finite overlap constant can guarantee that a
straddling secret is matched.** D-01's overlap clause cannot be satisfied as literally written; the
question is what to do instead.

### The `(?m)^` trap is real `[VERIFIED: WindowProbe.java §C2]`

```
full line : xxxxxxxxxxkey=BENIGN_BUT_NOW_MATCHES   <- key not at a line start: NOT redacted
cut  line : key=[REDACTED]                         <- (?m)^ now matches mid-line: BEHAVIOUR CHANGE
```

Both `formBodyParamRegex` (`(?im)` + `(^|[?&])`) and `authHeaderRegex`/`cookieHeaderRegex`
(`(?im)^`) are affected. The failure is bidirectional: a mid-line cut can create matches that should
not exist, and can destroy matches that should (a match spanning the cut is truncated).

### `Matcher.region()` — correct semantics, but incompatible with the replacement API `[VERIFIED: WindowProbe.java §C1/§C3/§C4]`

| Test | Result |
|------|--------|
| `region(1,3)` then `replaceAll("Y")` on `"xxxxx"` | **`"YYYYY"`** — the region is silently discarded. `replaceAll()` calls `reset()`, which restores the default region |
| `region(midLine..)` + `useAnchoringBounds(false)` + `useTransparentBounds(true)`, `find()` | `false` ✅ — the false line start is correctly suppressed |
| same region with default anchoring bounds, `find()` | `true` ❌ — the false match occurs |
| `region(realLineStart..)` + `useAnchoringBounds(false)`, `find()` on `(?m)^line-three` | `true` ✅ — a *real* line start inside the region still matches, because with anchoring bounds off the `Caret` node reads `seq.charAt(i-1)` against the full text |
| `region(3,11)` + `find()`/`appendReplacement()`/`appendTail()` on `"AAAkey=1BBBkey=2CCC"` | `"AAARBBBkey=2CCC"` — `find()` honours the region, but `appendReplacement`/`appendTail` work in **full-input coordinates** and `appendTail` runs to the end of the input, not the end of the region |

So `region()` *is* semantically correct for mid-line splitting, but using it forces a full rewrite of
the body stage into manual `find()`/`appendReplacement()` bookkeeping with O(n) tail copying per
window, and it cannot use `SafeRegex.replaceAllSafe` at all.

### Recommendation: cut at line boundaries, do not split lines, no overlap `[VERIFIED: WindowProbe.java §C5]`

```kotlin
/**
 * Window boundary = just past the last '\n' at or before [start] + [width].
 * A single line longer than [width] becomes its own oversized window rather than being split:
 * every built-in body rule is line-anchored or line-local, so a mid-line cut is the ONLY way to
 * change their semantics (see the (?m)^ note above), and the per-pattern deadline already bounds
 * the cost of an oversized window.
 */
private fun windowEnd(s: String, start: Int, width: Int): Int { ... }
```

Executed check: windowing a 5-line document into ≤20-char line-boundary windows produced output
**byte-identical** to processing the whole document in one pass. This is the property the phase needs
and it is achieved without any overlap constant, without `region()`, and without changing
`replaceAll`.

**The one residual:** `jsonSecretKeyRegex`'s `\s*` **can** span newlines (verified: it matches
`{\n "token"\n :\n "SECRET"\n}`), so a pretty-printed JSON key/value split exactly across a window
boundary would be missed. Mitigate with a five-line boundary-safety rule — if the last line of the
prospective window ends with `:` or `"` after trailing-whitespace strip, pull in one more line.
`formBodyParamRegex` and `urlTokenParamRegex` cannot span newlines (their value classes exclude
`\s`), so they need nothing. **Custom patterns spanning a window boundary remain a genuine, permanent
limitation** — there is no principled bound on a user regex's match length. Record it in the ADR-14
consequences and in `CONCERNS.md`; do not pretend it is solved.

### Throughput: how big can a window be? `[VERIFIED: WindowProbe.java §D]`

Apple Silicon, JDK 21.0.11, warmed (2 discarded runs), ms per `replaceAll` over the whole input:

| Rule | 100 KB | **1 MB** | 10 MB |
|------|--------|----------|-------|
| `formBodyParamRegex` (today), single line | 3 | 8 | 79 ⚠ |
| `formBodyParamRegex` (new, bounded `{0,64}`), single line | 3 | **8** | 79 ⚠ |
| `formBodyParamRegex` (new), multi-line dense `param=value&other=thing` | 5 | **23** | 234 ⚠ |
| `jsonSecretKeyRegex` (new) | 0 | **4** | 47 |
| `urlTokenParamRegex` | 0 | 4 | 51 ⚠ |
| `jwtRegex` / `bearerRegex` / `basicAuthRegex` | ≤2 | ≤2 | 26-29 |
| `authHeaderRegex` / `cookieHeaderRegex` / `hostHeaderRegex` | 0 | ≤4 | ≤43 |

⚠ = exceeds the 50 ms `SafeRegex.DEFAULT_TIMEOUT_MS`.

**`MAX_REDACTION_BODY_CHARS = 1_000_000` is a well-sized window**: the slowest rule at 1 MB is 23 ms,
under half the 50 ms deadline. But the headroom is only ~2.2×, and this measurement is on fast
hardware. **A 2-3× slower machine running dense form content would time out and, under D-02's
fail-closed rule, drop a window that today passes through.** That is a user-visible capability
regression on slow hardware.

**Recommended mitigation (planner's call, ~15 lines):** on a window timeout, halve the window at a
line boundary and retry, to a depth of 2, before dropping. This turns the 50 ms deadline into a
pacing mechanism instead of a cliff, stays entirely inside D-01 (still the existing `SafeRegex`
50 ms per-pattern deadline) and D-02 (still bounded by the total budget), and was exercised in the
reference implementation. Without it, expect field reports of dropped context on slower machines.

**Also note for the planner:** the seven *header-stage* rules (`authHeaderRegex`, `bearerRegex`,
`basicAuthRegex`, `jwtRegex`, `urlTokenParamRegex`, `cookieHeaderRegex`, `setCookieHeaderRegex`,
`hostHeaderRegex`) run **unbounded on the full input** today and are outside D-01/D-02's stated
scope. At 10 MB they cost ~25-50 ms each — so a 200 MB input still spends ~4-8 seconds in the header
stage before the budgeted body stage even starts. This is a pre-existing condition, not a regression,
and D-01/D-02 as written do not cover it. **Report, do not fix here** — but the ADR-14 wording should
not claim more than the body stage actually delivers.

### Budget constant

`Defaults.MAX_REDACTION_BUDGET_MS = 2_000L` `[ASSUMED — sized from the measurements above, not from
an external source]`. Arithmetic: at ~27 ms per 1 MB window (form + JSON), 2 000 ms covers roughly
60-70 MB on this hardware and ~20 MB on a 3× slower box; the reference implementation processed a
4.16 MB input in 849 ms end-to-end. The `MAX_` prefix matches `MAX_REDACTION_BODY_CHARS` and avoids
the rename D-04 rejected. Not user-configurable, per D-04's rejection of exposing it in settings.

---

## Decision 4 — Reconciling `SafeRegex`'s fail-soft contract with D-02's fail-closed rule

**This is the subtlest interaction in the phase and the easiest thing to get silently wrong.**

`SafeRegex.replaceAllSafe` (`SafeRegex.kt:64-77`) catches `RegexTimeoutException` and returns the
**original input unchanged**, documented as "fail-open so the redaction pipeline never hangs".
`SafeRegexTest.kt:44` asserts exactly this: *"On timeout replaceAllSafe must return the original input
unchanged (fail-open)"*. That test must stay green.

The problem: **the return value is indistinguishable from "the pattern matched nothing"**. Under
D-02, a timed-out window contains unscanned bytes that must never reach a backend — but the current
API gives the caller no way to know a timeout happened. A body stage built naively on
`replaceAllSafe` would be fail-**open** in exactly the case D-02 exists to close, while looking
correct.

`[VERIFIED: grep]` `SafeRegex.replaceAllSafe` has exactly **one** production caller
(`Redaction.kt:272`) plus `SafeRegexTest`. `DeadlineCharSequence` is `private` at file scope, so
`Redaction` cannot re-implement the deadline itself; `RegexTimeoutException` is `internal` in the
same package and *is* reachable, but the deadline wrapper is not.

**Recommended seam — add a reporting sibling; keep the existing function byte-identical:**

```kotlin
/** Result of a bounded replacement. [timedOut] is the ONLY reliable signal that the pattern did
 *  not complete — [text] equals the input in both the "no matches" and the "timed out" cases. */
data class SafeReplaceResult(val text: String, val timedOut: Boolean)

fun replaceAllSafeReporting(
    input: String,
    pattern: Pattern,
    replacement: String,
    timeoutMs: Long = DEFAULT_TIMEOUT_MS,
): SafeReplaceResult =
    try {
        val deadline = System.nanoTime() + timeoutMs * 1_000_000L
        SafeReplaceResult(pattern.matcher(DeadlineCharSequence(input, deadline)).replaceAll(replacement), false)
    } catch (_: RegexTimeoutException) {
        SafeReplaceResult(input, true)
    }

/** Fail-soft façade preserved verbatim for the documented contract and SafeRegexTest. */
fun replaceAllSafe(input: String, pattern: Pattern, replacement: String, timeoutMs: Long = DEFAULT_TIMEOUT_MS): String =
    replaceAllSafeReporting(input, pattern, replacement, timeoutMs).text
```

`redact/` gains no dependency, stays AWT-free, and the existing contract is unchanged. The body stage
then reads: **`timedOut == true` ⇒ this window was not fully scanned ⇒ drop it and emit a marker.**

Composing the deadlines without double-counting or starving later windows:

```
budgetDeadlineNanos = System.nanoTime() + MAX_REDACTION_BUDGET_MS * 1_000_000
per call:  timeoutMs = min(SafeRegex.DEFAULT_TIMEOUT_MS, remainingBudgetMs)
```

`min(...)` is what prevents double-counting: the per-pattern deadline is never allowed to outlive the
total budget, so a pattern starting with 12 ms of budget left gets a 12 ms deadline and reports
`timedOut` rather than overrunning. Starvation is prevented structurally — windows are processed in
order and, once the budget is spent, **all** remaining content is dropped under exactly one marker
rather than each window individually timing out.

---

## Decision 5 — The rate-limited Output-tab line (D-03)

### The two established patterns, read

| Pattern | Location | Shape |
|---------|----------|-------|
| `maybeLogBackoff` | `scanner/PassiveAiScannerAnalysis.kt:825-835` | `AtomicLong lastBackoffLogTime`; read-then-`compareAndSet`; window `BACKOFF_LOG_INTERVAL_MS = 10_000`; **`nowMs` is a parameter**, not read inside |
| `availabilityLogged` | `backends/cli/CliBackend.kt:28,75-78` | `AtomicBoolean.compareAndSet(false, true)` — once-ever, not windowed |
| (richer precedent) | `mcp/McpBlockedRequestReporter.kt` (ADR-13) | Two independent windows + `suppressedCount.getAndSet(0L)` aggregation; its KDoc states the `nowMs`-as-parameter convention explicitly, citing `maybeLogBackoff` |

D-03 requires a *window*, so `maybeLogBackoff` is the model, not `availabilityLogged`.

### Constraints in `redact/`

- `SafeRegex.kt:18-22` header comment: *"AWT-free: no java.awt / javax.swing imports"*. `Redaction`
  has no logging dependency at all today.
- `Redaction` is a stateless `object` plus `@Volatile compiledCustomPatterns` and the two host maps —
  so adding fields is consistent with the file, contrary to the "prefer locals" note in
  `<code_context>` (which is about *body-stage* state, and that advice still holds: the window loop
  should use locals only).
- `Redaction.apply` is called from scanner threads and MCP tool threads concurrently.

### Recommended seam

```kotlin
/**
 * Optional sink for the D-03 truncation notice. Wired in App.initialize to
 * api.logging()::logToOutput. Null in tests and headless contexts; the redaction pipeline never
 * depends on it. @Volatile so a write from the EDT at startup is visible to scanner threads.
 * Modelled on backends/BackendDiagnostics.output.
 */
@Volatile
var truncationLogger: ((String) -> Unit)? = null

private val lastTruncationLogMs = AtomicLong(0L)
private val suppressedTruncations = AtomicLong(0L)
private const val TRUNCATION_LOG_INTERVAL_MS = 10_000L   // same window as maybeLogBackoff

/** Internal seam: nowMs is a parameter so the window is assertable without sleeping
 *  (the McpBlockedRequestReporter / maybeLogBackoff convention). */
internal fun maybeLogTruncation(nowMs: Long, droppedChars: Long) { ... }
```

**Why not the alternatives:**
- *Return a result object from `apply`* — changes the signature at three live call sites and both
  test-only ones, and `apply`'s `String` return is chained in `ContextCollector` and
  `BountyPromptTagResolver`. Disproportionate.
- *Call `BackendDiagnostics.log` directly* — creates a `redact/ → backends/` dependency and is
  semantically wrong (it is a backend-diagnostics channel).
- *`AuditLogger`* — explicitly excluded by D-03.

**Testability:** a unit test sets `Redaction.truncationLogger = { captured += it }`, runs an oversized
input, asserts the line fired, and resets it in `@AfterEach` (same discipline as the existing
`resetCustomPatterns`). The rate-limit window is asserted by calling `maybeLogTruncation` directly
with two synthetic `nowMs` values — no sleeping.

### Marker wording

D-03's suggested `[TRUNCATED — NOT REDACTED]` is ambiguous: "NOT REDACTED" reads as *"what follows is
unredacted"*, the opposite of what happened. Recommended:

```
[REDACTION BUDGET EXCEEDED - 12345678 CHARS DROPPED AND NOT SENT]   // budget exhausted (tail)
[REDACTION INCOMPLETE - 1000000 CHARS DROPPED AND NOT SENT]          // one window timed out
```

Properties this wording must keep, in priority order:
1. Says the content was **removed**, not passed through.
2. Distinguishable from `[REDACTED]` so a reader can tell "removed for size" from "removed for
   secrecy" — the same reason `[JWT_REDACTED]` and `[STRIPPED]` are distinct tokens today.
3. Constant shape + one integer ⇒ no attacker-controlled substring ⇒ not an injection vector.
4. Not phrased as an instruction to the model.
5. ASCII hyphen rather than an em dash inside the *payload* string (comments may keep em dashes) —
   the marker is prompt content that gets hashed into `sha256Hex(singlePrompt)` and round-trips
   through backend transports.
6. Must not itself match any `SecretShapes` entry (it does not — no high-entropy run).

---

## Don't Hand-Roll

| Problem | Don't build | Use instead | Why |
|---------|-------------|-------------|-----|
| Bounding a regex match in time | A watchdog thread / `ExecutorService` around `Matcher` | `SafeRegex.replaceAllSafeReporting` (the `DeadlineCharSequence` already in `SafeRegex.kt`) | The JDK has no `Matcher` timeout (JDK-8234713 "Won't fix"). `SafeRegex.kt:18-22` already rejected `ExecutorService` to avoid orphaned threads in Burp's long-lived JVM |
| Region-scoped replacement | `Matcher.region()` + `replaceAll()` | Line-boundary `substring()` windows | `replaceAll()` silently resets the region (verified) |
| Rate-limited Output-tab logging | A new limiter | `maybeLogBackoff`'s read-then-CAS on an `AtomicLong` | D-03 says do not invent a third pattern; `McpBlockedRequestReporter` is the richer precedent if aggregation counts are wanted |
| Injectable diagnostic sink on a stateless object | A logging façade / DI container | `@Volatile var x: ((String) -> Unit)?` | Exactly `BackendDiagnostics.output`; already the house style |
| Cookie parsing | A cookie library | Hand-curated regex | CLAUDE.md + ADR-5. Also wrong for the data — the prompt blob is a mixed free-text document, not a cookie header |
| JSON key extraction | `kotlinx-serialization` (already on the classpath) | `jsonSecretKeyRegex` | Same. The input is not guaranteed to be JSON, and `Redaction.kt:112-114` already documents the accepted escaped-quote limitation |
| Group-referencing replacement under a deadline | A new lambda-taking `SafeRegex` API | `$1$2=[REDACTED]` / `$1"[REDACTED]"` replacement strings | Verified byte-identical to the current Kotlin lambdas |
| HKDF host anonymization | Anything | Leave `Redaction.kt:167-227` and `anonymizeHost` untouched | ADR-9; SC6 forbids perturbing it |

**Key insight:** every "don't hand-roll" here points at code that already exists in `redact/` or one
package away. The phase's whole surface is *re-plumbing* existing primitives, which is why it can add
zero dependencies.

---

## Runtime State Inventory

This is a behaviour-change phase, not a rename, but three categories carry live state worth checking
before the plan is written.

| Category | Items found | Action required |
|----------|-------------|-----------------|
| **Stored data** | `PassiveAiScanner` persistent prompt cache (`PassiveAiScannerFilters.kt:200,236`) is keyed on `sha256Hex(singlePrompt)` and stores parsed `AiIssueItem`s, **not prompt text**. Changing redaction changes the hash ⇒ a one-time cache-miss wave; **no stored secrets, no stale-leak** | None — cache invalidation is automatic and benign. Worth one sentence in the plan so a reviewer does not mistake the miss wave for a regression |
| | `ScanKnowledgeBase` in-memory maps hold `authCookieNames` (cookie **names** only, never values — `PassiveAiScannerAnalysis.kt:281-299`) | None |
| | `Redaction.hostForwardMap` / `hostReverseMap` (bounded LRU, `HOST_MAP_CAP = 4096`) | **Do not touch** — SC6 |
| **Live service config** | None. This phase does not talk to any external service | None — verified: no MCP server config, no n8n/Datadog/Cloudflare analogue in this repo |
| **OS-registered state** | None. No scheduled tasks, no daemons, no OS registrations | None — verified by inspection of `App.kt` (registers Burp UI/context-menu/scan-check providers only, all in-process) |
| **Secrets / env vars** | `AgentSettings.customRedactionPatterns` (`config/AgentSettings.kt:156,428,539,703`) persists in Burp Preferences as plaintext config. D-05 changes **where the compiled list is consulted**, not the list, its key, or its format | None. `App.kt:88-90` seeding and `SettingsPanelSettingsIO.kt:475` refresh are both unchanged |
| **Build artifacts** | `build/` Gradle outputs and the JaCoCo report; the shadow JAR is rebuilt from source | None — no `egg-info`-style stale metadata in a Gradle/Kotlin project |

**One behaviour change that reads like state but is not:** after D-06, `McpToolContext.redactIfNeeded`
runs `SecretTripwire.detectAndBuild` on text that has had *custom patterns* applied even under OFF.
Tripwire hit counts under OFF may therefore drop slightly. Correct and intended (fewer secrets
survive), but a reviewer comparing audit volumes should not be surprised.

---

## Common Pitfalls

### Pitfall 1 — Testing SC3's must-not limb against the wrong baseline
**What goes wrong:** a task written as "fix over-redaction of `keyboard_layout`" produces a test that
is green before *and* after the change.
**Why:** `keyboard_layout` and `codename` are not over-redacted today (measured). The `(^|[?&])…=`
anchoring already provides both-side delimiting.
**How to avoid:** write those assertions explicitly as **regression guards on the new mechanism** and
say so in the test comment. Phase 20's SC4 discipline applies: a test that passes before and after has
not tested anything.

### Pitfall 2 — Section-scoped rule that only looks at the first occurrence
**What goes wrong:** attacker-controlled `Server:` header injects `=== COOKIES ===` into
`=== PRIOR KNOWLEDGE ===`; the real section is never reached.
**Warning sign:** `text.indexOf(HEADER)` without a loop. **Proven exploitable.**

### Pitfall 3 — Mid-line window splitting
**What goes wrong:** `(?m)^` matches at an artificial line start; matches spanning the cut are
truncated. **Proven in both directions.**
**How to avoid:** cut only at `'\n'`; let an over-long line be its own window.

### Pitfall 4 — `region()` + `replaceAll()`
**What goes wrong:** `replaceAll()` calls `reset()`, restoring the default region, so the whole input
is processed. **Silent** — no exception, no warning.
**How to avoid:** don't use `region()` (see Pitfall 3's fix). If you must, use `find()` +
`appendReplacement()` and remember `appendTail()` runs to the end of the *input*.

### Pitfall 5 — Treating `replaceAllSafe`'s return value as success
**What goes wrong:** a timed-out pattern returns the input unchanged; a body stage that just assigns
`out = replaceAllSafe(out, ...)` is fail-**open** at exactly the moment D-02 demands fail-closed.
**How to avoid:** the reporting sibling in Decision 4. Add an explicit test that a pathological custom
pattern on an oversized input produces a **marker**, not passthrough.

### Pitfall 6 — Breaking OFF's byte-identity
**What goes wrong:** `offModePreservesBodies` (`RedactionTest.kt:314-325`) asserts
`assertEquals(input, output)`. If the body stage runs its window machinery under OFF with zero custom
patterns, any marker, any normalisation, any trailing-newline difference fails it.
**How to avoid:** build the rule list first; `if (rules.isEmpty()) return input` **before** any
windowing. Verified in the reference implementation.

### Pitfall 7 — Capturing groups in the new key expression
**What goes wrong:** `SENSITIVE_KEY_EXPR` contains alternations; if any use `(...)` instead of
`(?:...)`, group numbering shifts and `$1$2=[REDACTED]` / `$1"[REDACTED]"` produce garbage.
**Warning sign:** replacement output containing fragments of the key or literal `$2`.
**How to avoid:** every internal group non-capturing; assert one exact replacement string per context
in the tests.

### Pitfall 8 — One marker per dropped window
**What goes wrong:** a 200 MB input yields ~200 markers, bloating the prompt the phase is trying to
bound.
**How to avoid:** coalesce the budget-exhausted tail into exactly one marker and `break`.

### Pitfall 9 — Assuming the 50 ms deadline has generous headroom
**What goes wrong:** dense form content at 1 MB measures 23 ms here; a 2-3× slower machine crosses
50 ms and, under fail-closed, drops content that ships today.
**How to avoid:** halve-and-retry before dropping (Decision 3). At minimum, know the number is 23 ms,
not 5 ms, before deciding.

### Pitfall 10 — Widening `SENSITIVE_KEYS` to solve SC3
**What goes wrong:** adding words multiplies the false-positive surface across three regexes for no
measured benefit.
**How to avoid:** the boundary mechanism plus the vendor list covers 31/31 must-redact cases with the
existing 12 words. Verified.

---

## Code Examples

All snippets below were executed as Java equivalents in `RefImpl.java` (54/54 assertions pass);
translate idiomatically to Kotlin.

### 1. Cookie section — every occurrence, every value

```kotlin
// redact/Redaction.kt
/** Owned here, imported by the emitter, so a rename is a compile error rather than silent drift. */
const val COOKIE_SECTION_HEADER = "=== COOKIES ==="

private val cookieSectionPairRegex = Regex("(?m)^([^=\\r\\n]+)=(.*)$")
private val nextSectionRegex = Regex("(?m)^=== ")

/**
 * Redacts the VALUE of every name=value pair inside EVERY [COOKIE_SECTION_HEADER] span,
 * preserving names. All values, not only sensitive-named ones: cookies are near-universally
 * session-bearing and name-based selectivity is precisely what produced PRIV-05.
 *
 * EVERY occurrence, not the first: an attacker-controlled `Server:` header reaches the
 * `=== PRIOR KNOWLEDGE ===` block, which is emitted BEFORE the real cookie section, so a
 * first-occurrence-only scan can be pointed at a decoy.
 */
private fun redactCookieSections(text: String): String {
    var out = text
    var from = 0
    while (true) {
        val h = out.indexOf(COOKIE_SECTION_HEADER, from)
        if (h < 0) return out
        val bodyStart = h + COOKIE_SECTION_HEADER.length
        var end = out.length
        out.indexOf("\n\n", bodyStart).let { if (it >= 0) end = minOf(end, it) }
        nextSectionRegex.find(out, bodyStart)?.let { end = minOf(end, it.range.first) }
        val section = out.substring(bodyStart, end)
        out = out.substring(0, bodyStart) +
            section.replace(cookieSectionPairRegex) { "${it.groupValues[1]}=[REDACTED]" } +
            out.substring(end)
        from = bodyStart
    }
}
```

### 2. `(COOKIE)`-typed parameter lines

```kotlin
/**
 * Parameters are emitted as "name=value (TYPE)" where TYPE is the Montoya
 * HttpParameterType name (PassiveAiScannerAnalysis.kt:240). Keying on the semantic type label
 * rather than the section header makes this rule context-free and survives a section rename.
 */
private val cookieTypedParamRegex = Regex("(?im)^([^=\\r\\n]+)=(.*?)(\\s\\(COOKIE\\))\\s*$")

// inside `if (policy.stripCookies)`:
out = out.replace(cookieTypedParamRegex) { "${it.groupValues[1]}=[REDACTED]${it.groupValues[3]}" }
```

Verified output: `remember_me=[REDACTED] (COOKIE)` while `q=red running shoes (URL)` and
`quantity=2 (BODY)` are untouched.

### 3. The key expression wired into all three consumers

```kotlin
private val urlTokenParamRegex = Regex("(?i)([?&](?:$SENSITIVE_KEY_EXPR)=)[^&\\s\"'<>]+")
private val formBodyParamRegex = Regex("(?im)(^|[?&])($SENSITIVE_KEY_EXPR)=[^&\\s\"'<>]+")
private val jsonSecretKeyRegex =
    Regex("(?i)(\"$SENSITIVE_KEY_EXPR\"\\s*:\\s*)(\"[^\"]*\"|true|false|null|-?\\d+(?:\\.\\d+)?)")
```

### 4. `$n` replacement strings are equivalent to the current lambdas `[VERIFIED: WindowProbe.java §E]`

```kotlin
// today, Redaction.kt:261-268
out = out.replace(formBodyParamRegex) { "${it.groupValues[1]}${it.groupValues[2]}=[REDACTED]" }
out = out.replace(jsonSecretKeyRegex) { "${it.groupValues[1]}\"[REDACTED]\"" }

// equivalent, and usable with SafeRegex (which takes a String replacement)
"$1$2=[REDACTED]"     // formBodyParamRegex
"$1\"[REDACTED]\""    // jsonSecretKeyRegex
"$1[REDACTED]"        // urlTokenParamRegex (already a String replacement today)
```

Executed on `"?api_key=abc&x=1\nsecret=zzz\n"`: `$`-string and lambda outputs compared `equal: true`.
Neither `[REDACTED]` nor `"` requires escaping in a `Matcher` replacement (no `$` or `\`).

### 5. Body stage skeleton (D-01 / D-02 / D-05)

```kotlin
private fun bodyStage(input: String, builtinsEnabled: Boolean): String {
    val rules = buildList {
        if (builtinsEnabled) {
            add(formBodyParamRegex.toPattern() to "$1$2=[REDACTED]")
            add(jsonSecretKeyRegex.toPattern() to "$1\"[REDACTED]\"")
        }
        // D-05: custom patterns are OUTSIDE the redactTokens branch — a user's "never send this"
        // list is independent of the privacy mode.
        compiledCustomPatterns.forEach { add(it to "[REDACTED]") }
    }
    // OFF with no custom patterns: byte-identical passthrough (keeps offModePreservesBodies green).
    if (rules.isEmpty()) return input

    // D-04: at or below the window width this is a single pass — cost and behaviour identical to today.
    if (input.length <= Defaults.MAX_REDACTION_BODY_CHARS) {
        var out = input
        for ((p, r) in rules) out = SafeRegex.replaceAllSafe(out, p, r)
        return out
    }

    val budgetDeadline = System.nanoTime() + Defaults.MAX_REDACTION_BUDGET_MS * 1_000_000L
    val sb = StringBuilder(input.length)
    var i = 0
    while (i < input.length) {
        val remainingMs = (budgetDeadline - System.nanoTime()) / 1_000_000L
        if (remainingMs <= 0) {                                   // D-02: one marker for the tail
            sb.append(budgetMarker(input.length - i))
            maybeLogTruncation(System.currentTimeMillis(), (input.length - i).toLong())
            break
        }
        val end = windowEnd(input, i, Defaults.MAX_REDACTION_BODY_CHARS)
        // scanWindow appends either the redacted window or a drop marker; halve-and-retry inside.
        scanWindow(input.substring(i, end), rules, budgetDeadline, depth = 0, sink = sb)
        i = end
    }
    return sb.toString()
}

private fun scanWindow(w: String, rules: List<Pair<Pattern, String>>, budgetDeadline: Long,
                       depth: Int, sink: StringBuilder) {
    var cur = w
    for ((p, r) in rules) {
        val remainingMs = (budgetDeadline - System.nanoTime()) / 1_000_000L
        if (remainingMs <= 0) return dropOrRetry(w, rules, budgetDeadline, depth, sink)
        // min() is what prevents double-counting: a per-pattern deadline never outlives the budget.
        val res = SafeRegex.replaceAllSafeReporting(
            cur, p, r, minOf(SafeRegex.DEFAULT_TIMEOUT_MS, remainingMs),
        )
        if (res.timedOut) return dropOrRetry(w, rules, budgetDeadline, depth, sink)
        cur = res.text
    }
    sink.append(cur)   // fully scanned
}
```

### 6. Line-boundary window with the JSON boundary-safety rule

```kotlin
private fun windowEnd(s: String, start: Int, width: Int): Int {
    val hard = minOf(s.length, start + width)
    if (hard == s.length) return hard
    val nl = s.lastIndexOf('\n', hard)
    if (nl <= start) {                       // a single line longer than the window: keep it whole
        val f = s.indexOf('\n', start)
        return if (f < 0) s.length else f + 1
    }
    var end = nl + 1
    // jsonSecretKeyRegex's \s* can span newlines, so never cut between a JSON key and its value.
    val prevLineStart = s.lastIndexOf('\n', nl - 1) + 1
    val prevLine = s.substring(maxOf(start, prevLineStart), nl).trimEnd()
    if (prevLine.endsWith(":") || prevLine.endsWith("\"")) {
        val nl2 = s.indexOf('\n', end)
        if (nl2 >= 0) end = nl2 + 1
    }
    return end
}
```

### 7. The D-06 deletions

```kotlin
// scanner/PassiveAiScannerAnalysis.kt:393-402  — BEFORE
val safeMetadataText =
    if (settings.privacyMode == PrivacyMode.OFF) metadataText
    else Redaction.apply(metadataText, redactionPolicy, stableHostSalt = settings.hostAnonymizationSalt)

// AFTER — OFF is expressed once, as a policy, inside Redaction
val safeMetadataText =
    Redaction.apply(metadataText, redactionPolicy, stableHostSalt = settings.hostAnonymizationSalt)
```

```kotlin
// mcp/McpToolContext.kt:60-66 — BEFORE
val finalText = if (privacyMode == PrivacyMode.OFF) raw
                else Redaction.apply(raw, RedactionPolicy.fromMode(privacyMode), stableHostSalt = hostSalt)
// AFTER
val finalText = Redaction.apply(raw, RedactionPolicy.fromMode(privacyMode), stableHostSalt = hostSalt)
```

`redactionPolicy` is already computed at `PassiveAiScannerAnalysis.kt:337` as
`RedactionPolicy.fromMode(settings.privacyMode)` — no new plumbing.

---

## State of the Art

| Old approach | Current approach | Why it changed |
|--------------|------------------|----------------|
| `SENSITIVE_KEYS` = exact-word alternation (v0.6.0) | Key **expression**: vendor-name list ∪ token-boundary containment | Real-world cookie/param names are compound (`auth_token`) or vendor-concatenated (`JSESSIONID`); exact matching misses both |
| Body rules skipped above 1 MB | Line-boundary windowed scan with a total budget, fail closed | PRIV-06 / ADR-14 |
| Custom patterns inside the `redactTokens` branch | Outside it — always applied | D-05; `OFF` now means "no built-in redaction" |
| Built-in body rules run with **no** deadline (`Redaction.kt:261-268` use plain `Regex.replace`; only custom patterns at `:271-273` go through `SafeRegex`) | All body rules run under `SafeRegex` | Otherwise D-02's budget is unenforceable for the built-ins — worth calling out, it is easy to assume they were already bounded |
| `RedactionTest.oversizeBodySkippedSafely` asserts the fail-open as correct | Inverted: the over-cap secret must not survive | SC4 |

**Deprecated / not usable here:**
- `Matcher.region()` in combination with `replaceAll()` — silently ineffective (JDK behaviour, all
  versions).
- JDK `Matcher` timeout — still absent; JDK-8234713 is "Won't fix" (the rationale already recorded in
  `SafeRegex.kt:8-9`).

---

## Assumptions Log

| # | Claim | Section | Risk if wrong |
|---|-------|---------|---------------|
| A1 | `MAX_REDACTION_BUDGET_MS = 2_000L` is the right total budget | Decision 3 | Too low ⇒ premature drops on large but legitimate payloads; too high ⇒ a slow `Redaction.apply` on a scanner thread. Derived from measurement, but the *acceptable stall* is a product judgement the maintainer owns |
| A2 | `TRUNCATION_LOG_INTERVAL_MS = 10_000L` (matching `maybeLogBackoff`) | Decision 5 | Only affects Output-tab noise |
| A3 | The exact marker strings | Decision 5 | Cosmetic, but they land in model context and in `ContextPreviewDialog`; the maintainer may prefer different wording |
| A4 | camelCase boundary support should ship in this phase | Decision 2 | If wrong, `codeName`/`keyName`/`tokenCount` are over-redacted for no SC3-required benefit. Explicitly flagged as a veto point |
| A5 | Halve-and-retry on window timeout is worth ~15 lines | Decision 3 | Without it, slow hardware drops content that ships today; with it, slightly more complex control flow |
| A6 | Plural key forms (`codes`, `tokens`, `keys`) stay out of scope | Decision 2 | A form field literally named `codes` carrying MFA backup codes would leak. Judged lower-probability than the FP cost; recorded in CONCERNS |
| A7 | `PrivacyPill.kt:41` and `HelpConfigPanel.kt:26` are not part of D-07's three strings | Open Question 2 | Two more user-facing claims stay false until Phase 26 |
| A8 | The vendor list contents (`cf_clearance` excluded, `laravel_session`/`ci_session` included) | Decision 2 | Under-inclusion is a leak for that specific framework; the section rule covers it in the scanner path regardless |

*Every behavioural claim about current or proposed regex behaviour is `[VERIFIED]` by execution — the
assumptions above are all tuning constants and scope judgements, not facts.*

---

## Open Questions

### 1. Should camelCase key matching ship in Phase 21?
- **What we know:** it works (`(?-i:...)` verified on JDK 21); it gains `authToken`, `accessToken`,
  `userSessionId`; it costs exactly three FPs in the tested corpus (`codeName`, `keyName`,
  `tokenCount`); `keyboardLayout` and `monkeyBars` are unaffected.
- **What's unclear:** whether the maintainer reads `codeName` being redacted as contrary to SC3,
  which names `codename` as must-not-redact.
- **Recommendation:** ship it, and assert the three FPs as *accepted* over-redactions in the test so
  the behaviour is deliberate. Surface it in `/gsd-discuss-phase` or as a plan-level decision — it is
  a one-line revert if vetoed.

### 2. Are there four user-facing OFF strings, not three?
- **What we know:** D-07 names `ChatPanel.kt:1146`, `ContextPreviewDialog.kt:122`, and "the
  `PrivacyConfigPanel` OFF notice". The third is `ui/SettingsPanelActions.kt:236-251`
  (`refreshPrivacyNotice` composes the `SubtleNotice` that `PrivacyConfigPanel` renders — the panel
  file itself holds no OFF string). Two further strings become false under D-05:
  `ui/components/PrivacyPill.kt:41` (`"OFF mode sends raw traffic without redaction."`) and
  `ui/panels/HelpConfigPanel.kt:26` (`"OFF (raw data)"`).
- **Recommendation:** include `PrivacyPill.kt:41` — same falsity, same one-line fix, and D-07's own
  rationale ("leaving a user-facing claim false for five phases is worse") applies verbatim. Leave
  `HelpConfigPanel.kt:26` to DOC-03: "raw data" is a mode summary rather than a redaction claim, and
  it sits in a help panel that Phase 26 rewrites anyway. **Note D-06 lists `PrivacyPill.kt` as
  do-not-touch — that is about its `PrivacyMode.OFF` *check*, not its tooltip string.** Worth an
  explicit confirmation before editing.

### 3. Should the total budget cover the header stage too?
- **What we know:** the eight header-stage rules run unbounded on the full input; at 10 MB each costs
  25-51 ms, so a 200 MB input spends seconds there before the budgeted body stage begins.
- **What's unclear:** D-01/D-02 scope explicitly to "the body stage".
- **Recommendation:** keep the phase scoped as decided, and word ADR-14 to claim only what ships
  ("the body stage never fails open") rather than the unqualified "redaction never fails open".
  Record the header-stage gap in `CONCERNS.md`.

### 4. What should `redact_preview` (`McpToolExecutorImpl.kt:986`) do with the marker?
- **What we know:** it is a user-facing MCP tool that returns redacted text. After D-01 an oversized
  input returns text with a drop marker.
- **Recommendation:** no special handling — the marker is the honest answer and matches what the
  scanner path would send. Add a one-line note to the tool's description if it has one.

### 5. Should the extracted prompt builder live in `PassiveAiScannerPrompts.kt`?
- **What we know:** that file already holds `truncateWithEllipsis`, `buildCompactRequestBody`,
  `buildCompactResponseBody` as top-level `internal fun`s with no `PassiveAiScanner` receiver — the
  natural home.
- **Recommendation:** yes. See §Validation Architecture for why the extraction is what makes SC1/SC2
  testable without Montoya mocks.

---

## Environment Availability

| Dependency | Required by | Available | Version | Fallback |
|------------|-------------|-----------|---------|----------|
| JDK 21 | Gradle build + tests (Gradle 8.12.1 breaks on the default JDK 25/26) | ✓ | Temurin 21.0.11 at `/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home` | none needed |
| Gradle wrapper | build/test | ✓ | 8.12.1 | — |
| ktlint plugin | `./gradlew ktlintCheck` (strict by default; `-PktlintLenient=true` is the escape hatch) | ✓ | 12.1.1 | — |
| detekt + committed baseline | `./gradlew detekt` | ✓ | 1.23.8, `detekt-baseline.xml` | — |
| JUnit Jupiter | tests | ✓ | 6.0.3 | — |
| Mockito-Kotlin | Montoya mocks (only if the `ParsedHttpParameter` line-shape test is written) | ✓ | 5.4.0 | assert on the extracted pure builder instead |
| JaCoCo | coverage report (every `Test` task is `finalizedBy(jacocoTestReport)`) | ✓ | Gradle plugin | — |
| Burp Suite (runtime) | manual UAT of D-07 strings only | not required for CI | — | screenshot-free code review of the three strings |

**Missing dependencies with no fallback:** none.
**Missing dependencies with fallback:** none.

**Build invocation (mandatory):**
```bash
JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew <task>
```
`[VERIFIED: executed]` `JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test --tests "com.six2dez.burp.aiagent.redact.*"` exits 0 today — the pre-change baseline for the whole `redact` package is green.

---

## Validation Architecture

### Test Framework

| Property | Value |
|----------|-------|
| Framework | JUnit Jupiter 6.0.3 (`useJUnitPlatform()`), Mockito-Kotlin 5.4.0 |
| Config file | `build.gradle.kts` (`tasks.test`, lines ~160-170 for `-PexcludeHeavyTests`) |
| Quick run command | `JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test --tests "com.six2dez.burp.aiagent.redact.*" -q` |
| Full suite command | `JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test -q` |
| Lint/static gate | `JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew ktlintCheck detekt` (ktlint is strict; detekt baseline must not grow — QUAL-07) |
| Suite classification | New tests must NOT be named `*IntegrationTest` / `*ConcurrencyTest` / `*BackpressureTest` / `*RestartPolicyTest`, or they are excluded from the PR gate |

### Phase Requirements → Test Map

| Req ID | Behaviour | Test type | Automated command | File exists? |
|--------|-----------|-----------|-------------------|--------------|
| PRIV-05 / SC1 | Each of `JSESSIONID`, `PHPSESSID`, `connect.sid`, `auth_token`, `csrftoken` has its value absent from the redacted prompt in STRICT **and** BALANCED; asserted **per name** | unit | `./gradlew test --tests "*RedactionTest.cookieSectionValuesRedactedPerName*"` | ✅ `RedactionTest.kt` (extend) |
| PRIV-05 / SC1 | Same, asserted against the **real emitted blob** from the extracted prompt builder, not a hand-written string | unit | `./gradlew test --tests "*PassiveAiScannerPromptRedactionTest*"` | ❌ **Wave 0** |
| PRIV-05 / SC2 | A `COOKIE`-typed param line loses its value, keeps its name and ` (COOKIE)` suffix; `(URL)` and `(BODY)` lines untouched | unit | `./gradlew test --tests "*RedactionTest.cookieTypedParametersRedacted*"` | ✅ extend |
| PRIV-05 / SC2 | `"${p.name()}=${value} (${p.type().name})"` really produces the shape the rule keys on | unit (Mockito `ParsedHttpParameter`) | `./gradlew test --tests "*PassiveAiScannerPromptRedactionTest.parameterLineShape*"` | ❌ **Wave 0** |
| PRIV-05 / SC1 | Section-header poisoning: a decoy `=== COOKIES ===` earlier in the blob does not shield the real section | unit (security regression) | `./gradlew test --tests "*RedactionTest.cookieSectionDecoyDoesNotShieldRealSection*"` | ❌ **Wave 0** |
| PRIV-05 / SC3 | 31 must-redact keys redacted in all three contexts (query, form, JSON) | unit (parameterised) | `./gradlew test --tests "*RedactionTest.sensitiveKeyNamesRedacted*"` | ✅ extend |
| PRIV-05 / SC3 | 21 must-not-redact keys untouched — **regression guard**, green before and after by design; must be labelled as such | unit (parameterised) | `./gradlew test --tests "*RedactionTest.benignKeyNamesNotRedacted*"` | ✅ extend |
| PRIV-06 / SC4 | An input > `MAX_REDACTION_BODY_CHARS` with a secret **past** the old cut-off does not retain the secret. **Red before green** — must fail against pre-fix `Redaction.kt` | unit | `./gradlew test --tests "*RedactionTest.oversizeBody*"` | ✅ **rewrite `oversizeBodySkippedSafely` (`:379-397`)** |
| PRIV-06 / SC4 | A pathological custom pattern on an oversized input yields a **marker**, never passthrough (fail-closed) | unit | `./gradlew test --tests "*RedactionTest.oversizeBodyFailsClosed*"` | ✅ extend |
| PRIV-06 / D-03 | The truncation signal fires; a second event inside the window is suppressed | unit (injected `nowMs`) | `./gradlew test --tests "*RedactionTest.truncationSignal*"` | ✅ extend |
| PRIV-06 / D-05 | A custom pattern redacts under `PrivacyMode.OFF` | unit | `./gradlew test --tests "*RedactionTest.customPattern*"` | ✅ **invert the OFF limb of `customPatternRedactsInStrictAndBalanced` (`:342-345`)** |
| PRIV-06 / D-05 | OFF **with no custom patterns** returns byte-identical output | unit | `./gradlew test --tests "*RedactionTest.offMode*"` | ✅ existing |
| PRIV-06 / D-06 | The scanner path applies custom patterns under OFF (proves the short-circuit is gone, not just the unit behaviour) | unit on an extracted seam | `./gradlew test --tests "*PassiveAiScannerPromptRedactionTest.offStillAppliesCustomPatterns*"` | ❌ **Wave 0** |
| SC6 | Whole `redact` package green, including `hkdfMatchesRfc5869Vector` | unit | `./gradlew test --tests "com.six2dez.burp.aiagent.redact.*"` | ✅ existing |
| SC5 / D-08 | ADR-14 present in `DECISIONS.md` | manual review | — | manual |
| D-07 | Three UI strings no longer claim OFF means no redaction | manual review (Swing strings, no test harness) | — | manual |

### Sampling rate

- **Per task commit:** `./gradlew test --tests "com.six2dez.burp.aiagent.redact.*" -q` (~seconds)
- **Per wave merge:** `./gradlew test -q` plus `./gradlew ktlintCheck detekt`
- **Phase gate:** full suite green + detekt baseline not grown, before `/gsd-verify-work`

All with the `JAVA_HOME=$(/usr/libexec/java_home -v 21)` prefix.

### Wave 0 gaps

- [ ] **Extract a pure prompt builder** from `PassiveAiScannerAnalysis.doAnalysis` lines 342-391 into
      `PassiveAiScannerPrompts.kt` as a top-level `internal fun buildScanMetadataText(kbSummary: String?,
      displayUrl: String, urlPath: String, method: String, statusCode: Int, mimeType: String,
      potentialIds: List<String>, requestHeaders: List<String>, responseHeaders: List<String>,
      authHeaders: List<String>, cookies: List<String>, params: List<String>, requestBody: String,
      responseBody: String): String`.
      **Every input is already a plain `String`/`List<String>` at the call site**, so the extraction is
      Montoya-free and mechanical. This is what makes SC1/SC2 testable end-to-end against the *real*
      emitter format without a live Burp.
- [ ] **Extract the param-line formatter**: `internal fun formatParamLine(name: String, value: String,
      type: String): String` (currently the inline lambda at `:238-241`), so the ` (COOKIE)` shape the
      SC2 rule depends on is asserted at its source.
- [ ] **Extract the redaction step**: `internal fun redactScanMetadata(metadataText: String, mode:
      PrivacyMode, hostSalt: String): String` wrapping the `:393-402` block, so D-06's deletion is
      *asserted* rather than only inspected.
- [ ] `src/test/kotlin/com/six2dez/burp/aiagent/scanner/PassiveAiScannerPromptRedactionTest.kt` — new
      file covering SC1/SC2 end-to-end and D-06.
- [ ] `SafeRegexTest.kt` — add coverage for `replaceAllSafeReporting`'s `timedOut` flag (the existing
      fail-open assertion at `:44` stays green unchanged).

**What is honestly NOT testable without a live Burp:** `doAnalysis` as a whole (it needs
`HttpRequestResponse`, `HttpRequest`, `HttpResponse`, `MontoyaApi`, a backend session and
`ScanKnowledgeBase` state). The three extractions above move 100% of the PRIV-05-relevant logic out of
that reach. Asserting only at the `Redaction.apply` level over a hand-written blob would be *weaker
but not dishonest* — it would leave the emitter's exact format unasserted, which is precisely the gap
that let PRIV-05 ship. **Do the extraction.**

### SC6 regression surface — exact fate of every existing `RedactionTest`

| # | Test | Line | Fate |
|---|------|------|------|
| 1 | `strictModeStripsCookiesTokensAndHosts` | 119 | **stays green** — asserts `Cookie: [STRIPPED]`; the new cookie rules must not alter header-line stripping |
| 2 | `hostAnonymizationIsStablePerSalt` | 138 | stays green |
| 3 | `balancedModeRedactsCustomAuthHeaders` | 148 | stays green (header stage untouched) |
| 4 | `balancedModeRedactsUrlTokensInQueryStrings` | 173 | stays green — **canary**: asserts `name=alice` survives |
| 5 | `offModePreservesAllTokens` | 193 | stays green — requires the OFF byte-identity fast path |
| 6 | `clearMappings_removesOnlyRequestedSaltOrAll` | 210 | stays green |
| 7 | `hostAnonymizationFormatIsStable` | 226 | stays green |
| 8 | **`hkdfMatchesRfc5869Vector`** | 239 | **stays green — SC6's named vector; do not touch `Redaction.kt:167-227`** |
| 9 | `bodyFormLeadingFieldRedacted` | 260 | stays green — **canary**: asserts `user=bob` survives |
| 10 | `bodyJsonSecretKeysRedacted` | 275 | stays green — asserts `"name":"alice"` survives |
| 11 | `bodyJsonUnquotedSecretValuesRedacted` | 294 | stays green — asserts `"balance":99.5` survives and `"sid":-42` still redacts |
| 12 | `offModePreservesBodies` | 313 | stays green — `assertEquals` byte-identity under OFF |
| 13 | `customPatternRedactsInStrictAndBalanced` | 329 | **OFF limb (`:342-345`) MUST be inverted by D-05** |
| 14 | `customPatternsFromSettingsAreActiveAfterSeeding` | 355 | stays green |
| 15 | `oversizeBodySkippedSafely` | 379 | **MUST be rewritten by D-01** — it currently asserts the fail-open as correct |

Also in the package and expected green: `SafeRegexTest` (8 tests, incl. the fail-open assertion at
`:44`), `RedactionHostMapBoundTest`, `EntropyTest`, `SecretShapesTest`, `SecretTripwireTest`,
`SecretTripwireGateTest`, `SecretTripwireHooksTest`.

**Flag for the planner:** SC6 says "the existing `RedactionTest` suite … stays green", but D-01 and
D-05 each *require* inverting one existing assertion. That is not a conflict — it is two deliberate,
decision-driven exceptions. State them explicitly in the plan and in `21-VERIFICATION.md` so the
verifier does not read them as regressions. 13 of 15 stay green untouched.

**All 15 tests' inputs were replayed against the reference implementation and behave as tabulated**
`[VERIFIED: RefImpl.java §SC6]`.

---

## Security Domain

`workflow.security_enforcement` is absent from `.planning/config.json` ⇒ enabled (ASVS L1, block on
`high`). This phase *is* a privacy-control fix, so the threat model is unusually load-bearing.

### Applicable ASVS categories

| ASVS category | Applies | Standard control in this phase |
|---------------|---------|-------------------------------|
| V2 Authentication | no | No auth surface touched |
| V3 Session management | **indirectly** | The data being protected *is* session material (cookies). No session logic changes |
| V4 Access control | no | D-06 removes a *bypass* of a data-minimisation control, not an access-control check |
| V5 Input validation | **yes** | Redaction is output sanitisation over attacker-influenced input. Custom patterns are user input, validated at save time by `SafeRegex.isPatternSafe` (unchanged) |
| V6 Cryptography | **yes — do not touch** | HKDF host anonymization (ADR-9). SC6 forbids perturbing it; `hkdfMatchesRfc5869Vector` is the guard |
| V7 Error handling & logging | **yes** | D-03's Output-tab line must not echo dropped content; the marker carries a length only |
| V8 Data protection | **yes — the core of the phase** | Data minimisation before third-party transmission |
| V12 Files & resources | **yes** | Unbounded regex work on a caller-supplied string is a resource-exhaustion vector; D-02's budget is the control |

### Threat patterns for this stack

| Pattern | STRIDE | Mitigation in this phase |
|---------|--------|--------------------------|
| **Section-header poisoning** — attacker sets `Server: === COOKIES ===`, which reaches the prompt via `ScanKnowledgeBase.recordTechStack` → `=== PRIOR KNOWLEDGE ===`, emitted *before* the real cookie section, defeating a first-occurrence section scan | Information disclosure | Iterate **every** occurrence of the header (**proven exploitable otherwise**); plus the widened key expression as independent defence in depth |
| **Fail-open above the size cap** (PRIV-06 / F5) — attacker returns a >1 MB body, all body rules skip | Information disclosure | D-01 windowed scan; D-02 fail-closed drop |
| **Fail-open via `replaceAllSafe`'s silent timeout** — a pathological *user* custom pattern (or merely a slow machine) makes a rule time out and return the input unchanged, indistinguishable from "no match" | Information disclosure | `replaceAllSafeReporting`'s `timedOut` flag ⇒ drop the window |
| **ReDoS via user custom patterns, now in more contexts** — D-05 runs custom patterns under OFF too, and D-01 runs them per window, so a slow pattern's cost multiplies by the window count | Denial of service | `min(50 ms, remainingBudget)` per call **and** the total budget; `isPatternSafe` rejection at save time is unchanged. Note the multiplication explicitly: N windows × M patterns × 50 ms is bounded only by `MAX_REDACTION_BUDGET_MS` — that constant is the real control |
| **Resource exhaustion via a huge MCP tool result** — `redact_preview` and `McpToolContext.redactIfNeeded` accept caller-sized strings | Denial of service | D-02 budget. **Residual: the eight header-stage rules remain unbounded** (Open Question 3) |
| **Marker as prompt-injection vector** — the drop marker enters model context | Tampering (of model behaviour) | Marker is a constant shape plus one integer; contains **zero** attacker-controlled substring, is not phrased as an instruction, and never echoes dropped content |
| **Over-redaction as an availability/utility issue** — an over-eager rule destroys the analytic value of the prompt and the user silently gets worse findings | Denial of service (of the product's value) | Both directions asserted (21 must-not-redact keys, `user=bob`/`name=alice`/`balance:99.5` canaries); the accepted over-redactions are enumerated and justified rather than discovered in the field |
| **False sense of security from the D-07 strings** — the UI claims OFF means "no redaction" while custom patterns run, or claims custom patterns run when the user has none configured | Repudiation / user deception | D-07 rewording, ideally conditioned on `customRedactionPatterns.isNotEmpty()` |
| **Cache poisoning of the persistent prompt cache** | Tampering | Not reachable — the cache is keyed on the SHA-256 of the **post**-redaction prompt and stores parsed issues, not text |

### Security acceptance criteria for the plan

1. No code path can emit a window to the output that was not fully scanned by every active rule.
2. The section rule iterates all header occurrences (**must have a dedicated regression test**).
3. The marker string contains no substring derived from the input.
4. `hkdfMatchesRfc5869Vector` and `anonymizeHost`'s output format are untouched.
5. The SC4 test is **red before green** against pre-fix `Redaction.kt` (Phase 20's SC4 discipline).

---

## Sources

### Primary (HIGH confidence — executed or read in this session)

- **Live execution, JDK 21.0.11 (Temurin), Apple Silicon** — five probe programs in
  `/private/tmp/claude-501/-Users-six2dez-Tools-burp-ai-agent/e3aaf1ca-a71e-4705-8a1e-d122be7cbc07/scratchpad/`:
  `RegexProbe.java` (current-behaviour baseline), `DesignProbe.java` (key mechanism + cookie-rule
  comparison), `WindowProbe.java` (region/anchoring semantics, match-length bounds, throughput,
  replacement equivalence), `RefImpl.java` (full reference pipeline, 54/54 assertions),
  `AttackProbe.java` (section-header poisoning).
- **Repository source, read in full or in the cited ranges:** `redact/Redaction.kt`,
  `redact/SafeRegex.kt`, `redact/SecretShapes.kt` (header), `config/Defaults.kt:54-58`,
  `scanner/PassiveAiScannerAnalysis.kt:200-430,815-835`, `scanner/PassiveAiScannerFilters.kt:155-200`,
  `scanner/PassiveAiScannerPrompts.kt:12-18`, `scanner/ScanKnowledgeBase.kt:127-157`,
  `mcp/McpToolContext.kt:1-80`, `mcp/McpBlockedRequestReporter.kt:60-140`,
  `mcp/tools/McpToolExecutorImpl.kt:980-990`, `context/ContextCollector.kt`,
  `prompts/bountyprompt/BountyPromptTagResolver.kt:60-100`,
  `backends/BackendDiagnostics.kt`, `backends/cli/CliBackend.kt:25-80`,
  `ui/ChatPanel.kt:1138-1152`, `ui/components/ContextPreviewDialog.kt:110-135`,
  `ui/SettingsPanelActions.kt:225-262`, `ui/panels/PrivacyConfigPanel.kt`,
  `src/test/.../redact/RedactionTest.kt` (all 398 lines), `src/test/.../redact/SafeRegexTest.kt`,
  `build.gradle.kts`, `gradle/wrapper/gradle-wrapper.properties`.
- **Executed build command:** `JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test --tests
  "com.six2dez.burp.aiagent.redact.*"` — exit 0, all eight `redact` test classes green (pre-change
  baseline).
- **Planning documents:** `.planning/phases/21-redaction-completeness/21-CONTEXT.md`,
  `.planning/REQUIREMENTS.md` §PRIV, `.planning/ROADMAP.md` §Phase 21,
  `.planning/notes/2026-08-05-code-review.md` §F2/§F5, `.planning/codebase/CONCERNS.md`
  §"Redaction regex coverage gaps", `.planning/codebase/TESTING.md`, `.planning/config.json`,
  `DECISIONS.md` (ADR-1…ADR-13; ADR-5 and ADR-9 read in full), `CLAUDE.md`.

### Secondary (MEDIUM confidence)

- `java.util.regex.Matcher` region/bounds semantics — inferred from the executed results above plus
  the JDK's documented `useAnchoringBounds`/`useTransparentBounds` contract. The specific finding that
  `replaceAll()` discards the region is asserted from **execution**, not from documentation.
- JDK-8234713 ("Matcher has no timeout", Won't Fix) — cited from the `SafeRegex.kt:8-9` header
  comment, which the Phase 13 research verified. Not independently re-verified this session.

### Tertiary (LOW confidence — flagged)

- The tuning constants in the Assumptions Log (A1-A3, A5-A6, A8) are engineering judgements informed
  by measurement, not sourced facts.
- The vendor session-cookie name list (`laravel_session`, `ci_session`, `cfid`, `cftoken`,
  `.aspxauth`) is from training knowledge of common framework defaults. `[ASSUMED]` — the names are
  well known, but the list's completeness is not verified against any authoritative corpus. Under- or
  over-inclusion here is low-risk (over-inclusion redacts a benign key; under-inclusion is covered by
  the section rule on the scanner path), but do not present the list as exhaustive.

---

## Metadata

**Confidence breakdown:**

| Area | Level | Reason |
|------|-------|--------|
| Current (pre-fix) behaviour | **HIGH** | Every claim executed against the live regexes; the `keyboard_layout`/`codename` finding contradicts a plausible assumption and is measured |
| Cookie fix placement (Decision 1) | **HIGH** | R2's SC2 failure and the section-poisoning bypass are both demonstrated, not argued |
| Sensitive-key mechanism (Decision 2) | **HIGH** | 31/31 must-redact, 21/21 must-not-redact, monotonicity all executed across all three consumer regexes |
| Windowing mechanics (Decision 3) | **HIGH** | `(?m)^` corruption, `region()`/`replaceAll()` incompatibility, byte-identical line-boundary equivalence, and unbounded match length all executed |
| Throughput numbers | **MEDIUM** | Measured, warmed, single machine (Apple Silicon). Directionally reliable; absolute headroom on other hardware is extrapolated — which is exactly why halve-and-retry is recommended |
| Fail-soft/fail-closed reconciliation (Decision 4) | **HIGH** | The `replaceAllSafe` ambiguity is provable from the source; the single-caller fact is grepped |
| D-03 seam (Decision 5) | **HIGH** | Three in-repo precedents read directly |
| Tuning constants (budget, log window, marker text) | **LOW-MEDIUM** | `[ASSUMED]` — engineering judgement, listed in the Assumptions Log |
| Vendor cookie-name list completeness | **LOW** | `[ASSUMED]` — training knowledge |

**Research date:** 2026-08-11
**Valid until:** 2026-09-10 (30 days). Nothing here depends on a fast-moving external ecosystem; it
would only be invalidated by edits to `redact/`, `scanner/PassiveAiScannerAnalysis.kt`, or a JDK major
upgrade.
