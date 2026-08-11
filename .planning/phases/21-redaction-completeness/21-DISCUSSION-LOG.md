# Phase 21: Redaction Completeness - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-08-11
**Phase:** 21-redaction-completeness
**Areas discussed:** Oversized-body behaviour, Custom patterns × PrivacyMode.OFF

---

## Gray Area Selection

Four gray areas were presented; the maintainer selected two for discussion and delegated the other
two to Claude's discretion.

| Area | Description | Selected |
|------|-------------|----------|
| Cookie fix placement & policy (SC1, SC2) | Emitter vs redactor vs both; every cookie value vs sensitive-named only | |
| Sensitive-key matching (SC3) | Recognising `auth_token`/`api-key`/`X-Session-Id` without eating `keyboard_layout`/`codename` | |
| Oversized-body behaviour (SC4) | What happens above `MAX_REDACTION_BODY_CHARS` | ✓ |
| Custom patterns × `PrivacyMode.OFF` (SC5) | Whether user patterns survive OFF; the ADR | ✓ |

---

## Oversized-body behaviour

### Q1 — What happens above the cap?

| Option | Description | Selected |
|--------|-------------|----------|
| Chunk-and-scan the whole thing | Overlapping windows, body rules per window under the SafeRegex 50 ms deadline; nothing skipped. Cost: real CPU on a 10 MB response | ✓ |
| Truncate-and-redact | Redact the first N chars, drop the remainder with a marker. Cheap and bounded, but a silent capability loss on genuinely large JSON | |
| Refuse the payload | Sentinel/typed result; caller must not send. Safest by construction, but turns a redaction concern into a hard functional failure at five call sites | |

**User's choice:** Chunk-and-scan the whole thing (recommended option).

### Q2 — What bounds the whole operation?

| Option | Description | Selected |
|--------|-------------|----------|
| Total wall-clock deadline, then truncate the rest | One budget for the body stage; windows scanned in order until spent, remainder dropped with `[TRUNCATED — NOT REDACTED]`. Fail closed | ✓ |
| Total deadline, then refuse | Same budget, but exhaustion fails the whole call — reintroduces the per-call-site failure story | |
| Hard input ceiling above which we truncate first | A second much larger cap (e.g. 16 MB); below it, full chunk-scan with no time budget. Deterministic to test, but a pathological custom regex can still burn 50 ms × window count | |
| You decide | Leave the mechanism to research/planning | |

**User's choice:** Total wall-clock deadline, then truncate the rest (recommended option).
**Notes:** Raised because a 200 MB string × 3 built-in rules × N custom patterns × 50 ms is otherwise
unbounded wall-clock on whatever thread called `Redaction.apply`.

### Q3 — How does the user find out about truncation?

| Option | Description | Selected |
|--------|-------------|----------|
| Marker + Output-tab warning, rate-limited | Payload marker so the model sees why context stops, plus a rate-limited Output-tab line. Mirrors Phase 20 D-06/D-09 and `maybeLogBackoff` | ✓ |
| Marker only | Keeps `Redaction` a pure function with no logging dependency; user only notices in the context preview | |
| Marker + Output tab + audit event | All three destinations, but adds an `AuditLogger` dependency to `redact/`, which is deliberately AWT-free and dependency-light | |

**User's choice:** Marker + Output-tab warning, rate-limited (recommended option).
**Notes:** ADR-5 requires redaction to be pre-flight and user-visible. Audit logging is disabled by
default in this project, so an audit-only signal would be invisible to most users.

### Q4 — What does `MAX_REDACTION_BODY_CHARS` become?

| Option | Description | Selected |
|--------|-------------|----------|
| Repurpose as the window size, keep the name | 1 MB becomes the chunk width; sub-window inputs behave identically to today. One constant, one concept, no config surface | ✓ |
| Rename + add a separate window constant | `REDACTION_WINDOW_CHARS` + `REDACTION_BUDGET_MS`. Clearer, but a grep-and-replace and the old name appears in Phase 13 planning docs | |
| Make window + budget user-configurable | Maximum flexibility; adds two knobs, and a budget of 0 silently disables body redaction | |

**User's choice:** Repurpose as the window size, keep the name (recommended option).

---

## Custom patterns × PrivacyMode.OFF

### Q1 — What should custom patterns do under OFF?

| Option | Description | Selected |
|--------|-------------|----------|
| Always apply, including OFF | A "never send this, ever" list independent of privacy mode. OFF means "no built-in redaction", not "no redaction at all" | ✓ |
| OFF means off — nothing runs | Preserve today's behaviour but make it visible in `PrivacyConfigPanel`. Simplest model; risk is a user flipping to OFF for debugging and leaking their own corporate token | |
| Always apply, plus an explicit opt-out | Default always-apply with a checkbox to bind them to the mode. Covers both intents, at the cost of a third state across the STRICT/BALANCED/OFF matrix | |

**User's choice:** Always apply, including OFF (recommended option).

### Q2 — How do we make that hold at every call site?

Raised after discovering that `PassiveAiScannerAnalysis.kt:394` and `McpToolContext.kt:62` skip
`Redaction.apply` entirely under OFF rather than passing an OFF policy — so changing the
`redactTokens` branch alone would leave custom patterns inert on the exact path PRIV-05 is about.

| Option | Description | Selected |
|--------|-------------|----------|
| Delete the caller-side OFF short-circuits | Every caller passes `RedactionPolicy.fromMode(mode)` unconditionally; OFF expressed as a policy in one place. Structural — a future call site cannot reintroduce the hole by copying the `if` | ✓ |
| Add a fourth `RedactionPolicy` flag only | Smaller diff contained to `redact/`, but fixes the unit test rather than the leak | |
| Delete the short-circuits AND assert it in a test | Strongest, in the spirit of Phase 20's SC4 gate, but source-scanning tests are brittle and need an allowlist for the legitimate OFF checks | |

**User's choice:** Delete the caller-side OFF short-circuits (recommended option).

### Q3 — How far does this phase go on the user-facing OFF strings?

| Option | Description | Selected |
|--------|-------------|----------|
| Fix all three strings here | `ChatPanel.kt:1146`, `ContextPreviewDialog.kt:122`, the `PrivacyConfigPanel` notice. Phase 20's D-12 precedent | ✓ |
| Fix all three plus the GitBook privacy page | Most complete, but that is a separate repo and DOC-03 already owns it | |
| Leave the strings to Phase 26 / DOC-03 | Cleanest diff, but ships a release where the UI actively misdescribes what OFF does | |

**User's choice:** Fix all three strings here (recommended option).

### Q4 — What does the ADR cover?

| Option | Description | Selected |
|--------|-------------|----------|
| One ADR-14 covering both PRIV-06 decisions | "Redaction never fails open" — both halves answer the same question, so one principle future contributors inherit | ✓ |
| Two ADRs — one per decision | More granular and individually citable; cost is two entries that cross-reference each other constantly | |
| One ADR-14, OFF only | Exactly what SC5 asks for; the oversize reasoning lives only in code comments — which is how the bug recurs | |

**User's choice:** One ADR-14 covering both PRIV-06 decisions (recommended option).

---

## Claude's Discretion

Both delegated deliberately by the maintainer at the gray-area selection step. Recommendations are
recorded in CONTEXT.md §"Claude's Discretion" and are to be confirmed or overturned by phase research.

- **Cookie fix placement and policy (SC1, SC2)** — recommendation: fix in `Redaction` (not the
  emitter), and redact every cookie value rather than only sensitive-named ones. Rationale: the
  structural-fix framing carried from Phase 20, and the fact that name-based selectivity is what
  produced the defect.
- **Sensitive-key matching mechanism (SC3)** — recommendation: separator-aware whole-token matching
  plus an explicit known-session-cookie-name list plus a benign-key guard. Note the blast radius:
  `SENSITIVE_KEYS` feeds three regexes.

## Deferred Ideas

- GitBook privacy page and the SEC-04/PRIV-05 security advisory → DOC-03, Phase 26.
- EDT exposure of any `Redaction.apply` call site → report to Phase 23 / REL-05, do not fix here.
- A `ContextPreviewDialog` banner for budget-driven truncation → raised at the wrap-up gate and set
  aside; the payload marker plus Output-tab line is this phase's surface.
- Vendor-specific auth headers (`x-shopify-access-token`, `stripe-signature`) → known
  `authHeaderRegex` gap in `.planning/codebase/CONCERNS.md`; fold in only if the SC3 mechanism makes
  it free.
