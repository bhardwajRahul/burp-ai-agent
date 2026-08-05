# Burp AI Agent

## What This Is

A production-grade Burp Suite extension (Kotlin + Montoya API) that embeds an AI reasoning agent with pluggable backends (local + cloud), enforces privacy redaction on outbound traffic, records an auditable history, and exposes Burp operations to external AI agents over the Model Context Protocol (MCP). Targets security professionals running Burp Community or Professional on macOS / Linux / Windows.

## Core Value

Bring modern AI to a real security workflow **without** leaking sensitive traffic to third-party providers — privacy controls and an audit trail are non-negotiable, AI capability is additive.

## Current State

**Shipped:** v0.9.2 (v0.9.0 milestone closed 2026-06-26; point releases 0.9.1 / 0.9.2 cut from it).
Backends: CLI agents (claude, gemini, codex, opencode, copilot, ollama), HTTP (Anthropic, OpenAI-compatible, NVIDIA NIM, Perplexity, LM Studio, Ollama, Burp AI). MCP server with 50+ tools plus an external MCP client. Passive and active AI scanners. Privacy modes STRICT / BALANCED / OFF with HKDF host anonymization, body-level redaction, and a pre-send secret tripwire. Secrets encrypted at rest (AES-256-GCM).

**Measured, 2026-08-05:** ~38.9k lines Kotlin main, 94 test files; coverage 34% line / 23% branch; detekt baseline 1096 entries; `test detekt ktlintCheck` green.

## Current Milestone: v0.10.0 — Security Correctness & Agent Trust

**Status:** ▶ PLANNING — started 2026-08-05. Derived from a deep code review of v0.9.2 (17 findings).

**Goal:** Make the security and privacy controls the project already ships actually take effect on every path, and give the agent loop a trust boundary.

The framing matters: v0.9.0 built the machinery. This milestone found that some of it is not in the request path. Two findings were reproduced by running the shipped code — the MCP access-control interceptor never executes for routes Ktor resolves (so external-mode bearer auth does not protect `POST /message`), and the passive scanner leaks session cookies to the AI backend in STRICT and BALANCED because it re-emits them without the `Cookie:` prefix the redaction regex keys on.

**Target themes (7 phases, 20–26):**
- **Access control** — MCP interceptor ordering so auth/Origin/security-headers run on every request (SEC-04); version, IPv6 host and blank-token fixes (SEC-05)
- **Privacy** — cookie-section leak, real-world sensitive key names, redaction no longer failing open above 1 MB (PRIV-05, PRIV-06)
- **Agent trust** — user decision gate for model-emitted tool calls plus the ADR recording the threat model (SEC-06)
- **Reliability** — tool execution and MCP stop() off the EDT (REL-05); recurring schedulers that survive exceptions (REL-06); CLI output race and unbounded resource use (REL-07)
- **Secondary hardening** — no MCP token to unverified port holders, SsrfGuard alternate IP notations (SEC-07)
- **Quality & docs** — allowlist shell escaping, coverage on security paths, shrinking detekt baseline, security advisory (QUAL-06, QUAL-07, DOC-03)

**Embedded decisions (resolved at each phase's discuss-phase):** SEC-06 = whether the tool-call gate prompts for all tools or only state-mutating ones; PRIV-06 = whether user custom patterns apply under `PrivacyMode.OFF`; SEC-07 = certificate pinning vs challenge/response vs dropping automatic MCP takeover.

**Predecessor:** v0.9.0 (Hardening, Quality & New Capabilities) shipped 2026-06-26, 22/22 requirements — archived to `.planning/milestones/`. v0.8.0 shipped 2026-06-02; v0.7.0 shipped 2026-05-15.

## Requirements

### Validated

<!-- Shipped through v0.6.1 and confirmed working in production. -->

- ✓ **Burp tab UI** with embedded chat, streaming responses, multi-session transcripts, project-scoped persistence — `v0.5.0`
- ✓ **Context menu actions** on Proxy / Repeater / Site Map / Scanner Issues, including site-map tree node selection — `v0.5.0`
- ✓ **11 AI backends** (Burp AI, Ollama, LM Studio, NVIDIA NIM, Perplexity, Generic OpenAI-compatible, Claude / Gemini / Codex / OpenCode / Copilot CLI) — pluggable via `ServiceLoader`, external JARs supported — `v0.6.x`
- ✓ **AgentSupervisor** for CLI backend lifecycle (launch, restart with exponential backoff, deterministic shutdown) — `v0.4.0`
- ✓ **Privacy modes** STRICT / BALANCED / OFF with pre-flight redaction; default = BALANCED for new installs — `v0.6.0`
- ✓ **Context preview dialog** showing exact redacted JSON before any auto-captured context is sent — `v0.6.0`
- ✓ **Audit logging** as append-only JSONL with SHA-256 prompt/response hashes; disabled by default; opt-in verbose mode — `v0.5.0`
- ✓ **Repro bundles** (ZIP) capturing transcript + hashes + settings; carry `promptSource` / `contextKind` / `promptId` — `v0.6.0`
- ✓ **Passive AI scanner** with LRU dedup, batch mode (3–5 reqs per call, 60–70% fewer calls), persistent prompt cache, 62 vuln classes — `v0.5.0`
- ✓ **Active AI scanner** with 200+ static payloads, adaptive payload engine, risk-level filter (SAFE/CAUTIOUS/AGGRESSIVE), 2000-deep backpressure queue — `v0.5.0`
- ✓ **403 Bypass testing** — 3 techniques: IP-spoofing headers, path manipulation, method switching — `v0.5.0`
- ✓ **AI Scan on Selected Insertion Point** — scope active scan to parameter / header / JSON field under user selection — `Unreleased`
- ✓ **JS Endpoint extractor** — 8 regex patterns, automatic on JS responses + manual context-menu action — `v0.5.0`
- ✓ **MCP server** over SSE on `127.0.0.1:9876` (+ optional stdio bridge); 53+ tools split safe / unsafe; bearer token + optional TLS; Unsafe Mode master switch — `v0.5.0`
- ✓ **MCP proxy-history preprocessing** — binary filtering, body size caps, newest-first, schema gated on settings — `v0.6.0`
- ✓ **Custom prompt library** — per-context saved prompts, favorites, search, JSON import/export, `Custom…` ad-hoc editor — `Unreleased`
- ✓ **Burp Scan Skill** — Markdown skill for terminal AI assistants (Claude Code, Gemini CLI, etc.) to drive Burp via MCP — `v0.5.0`
- ✓ **Determinism mode** — stable templates, stable context ordering, temperature clamped to 0 where supported — `v0.4.0`
- ✓ **Build & CI** — shadowJar, ktlint gate, jacoco coverage, CycloneDX SBOM on release, multi-OS test matrix — `v0.6.0`
- ✓ **Design-system module** — spacing / typography / color tokens + reusable Swing components (section headers, labeled field rows, help text, buttons, AccordionPanel) as the single styling source — `v0.8.0` (Phase 9)
- ✓ **MCP tools tab redesign** — grouped extension-native (AI) vs generic (Montoya) tools, store-build / full-build indicators, search/filter, per-group bulk toggle — `v0.8.0` (Phase 10)
- ✓ **All Settings tabs rebuilt on the design system** — consistent layout/grouping/labels, collapsible sections for long scanner tabs (UI-06), light/dark theme via tokens (UI-08), persistence preserved (UI-07) — `v0.8.0` (Phase 11)

### Active

<!-- Current scope: v0.9.0 — hardening, quality, and new capabilities. See REQUIREMENTS.md for REQ-IDs and ROADMAP.md for phases. -->

- [ ] **Privacy & redaction hardening** — host-anonymization correctness, broaden redaction coverage + user-configurable patterns, pre-send secret tripwire, redaction-coverage report
- [ ] **Secrets at rest & transport security** — encrypt stored API keys / TLS keystore password, remove keytool argv exposure, SSRF warning on backend URLs
- [ ] **Reliability & concurrency hardening** — ChatPanel EDT audit, resource cleanup, uniform HTTP timeouts/CircuitBreaker, CLI timeout bug #71
- [ ] **Quality & maintainability** — split mega-files, raise test coverage, detekt + blocking ktlint, exception-logging audit, build-wiring fix
- [ ] **New capabilities** — native Anthropic backend, external MCP servers (#41), proxy-history listener port (#70), token-budget guardrails
- [ ] **Planning reconciliation** — sync `.planning/` with shipped v0.7.0/v0.8.0 and closed issues (A7)

### Out of Scope

- **Hot-swapping backends at runtime** — SPEC non-goal; stop + restart is acceptable. *Why:* lifecycle complexity outweighs benefit for the one-AI-at-a-time workflow.
- **Replacing Burp's native scanner** — AI scanners are complementary and always secondary to Burp's own evidence. *Why:* stated SPEC non-goal; AI is for reasoning, not authoritative findings.
- **Demo-only shortcuts that bypass privacy or audit** — SPEC non-goal. *Why:* core-value violation; privacy and audit are non-negotiable.
- **Data exfiltration in STRICT or BALANCED** — never happens by design. *Why:* core-value violation.
- **JavaFX / Compose UI** — ADR-2 chose Swing for native embedding in Burp. *Why:* every alternative fights Burp's own toolkit.
- **Java / Scala rewrites** — ADR-1 chose Kotlin. *Why:* null-safety, coroutines, data classes pay for themselves on a glue-heavy plugin.

## Context

- **Mature brownfield codebase** at `v0.6.1` (released 2026-05-05). Around 90 commits in the last 6 months. Active polish/iteration phase — the recent commit stream ("Improvements 2", "UX improvements in settings", "Fixes AI blockers", "Custom library improvements") signals stabilization, not feature greenfield.
- **Ecosystem position**: bridges Burp's HTTP toolkit and the explosion of local + cloud LLMs; competes with users running ad-hoc curl-to-LLM scripts and with vendor-specific tools. Differentiator = privacy-by-default + auditability + MCP.
- **Public surface**: open-source MIT on GitHub (`six2dez/burp-ai-agent`); documentation site at `burp-ai-agent.six2dez.com`; Burp Security Advisories for vuln intake.
- **Existing ADRs** locked in `DECISIONS.md`: Kotlin/JVM, Swing UI, `ServiceLoader` plugins, split HTTP/CLI backend hierarchies, pre-flight redaction modes, embedded MCP server, hash-stamped JSONL audit.
- **AGENTS.md and AGENTS/ directory** carry Codex-style instruction files; do not conflate with this `.planning/` directory used by GSD.

## Constraints

- **Tech stack**: Kotlin (JVM 21), Gradle Kotlin DSL, Burp Montoya API — fixed by ADR-1/2/3.
- **Target**: Burp Suite Community + Professional 2023.12+, cross-platform (macOS / Linux / Windows).
- **Network**: MCP server binds to `127.0.0.1` by default; external access requires explicit opt-in + bearer token + optional TLS.
- **Distribution**: single fat JAR via `./gradlew shadowJar`; artifact name `Custom-AI-Agent-<version>.jar` (renamed from `Burp-AI-Agent-*` in `v0.6.0`).
- **Privacy**: STRICT / BALANCED / OFF must all stay user-visible and pre-flight; redaction is hand-curated regex + HKDF host anonymization in STRICT.
- **License**: MIT — keep dependencies compatible.
- **Audit defaults**: disabled by default, opt-in verbose mode; hashes only unless verbose is on.
- **Languages in code & comments**: English only (AGENTS.md non-negotiable).

## Key Decisions

| Decision | Rationale | Outcome |
|----------|-----------|---------|
| Treat the existing v0.6.1 SPEC as **Validated** baseline; scope new GSD work to stabilizing the `Unreleased` block | Brownfield with rich docs; the practical question is "what ships next?" not "what is this?" | — Pending |
| Skip codebase mapping in `/gsd-map-codebase` | `SPEC.md`, `DECISIONS.md`, `AGENTS.md`, `CHANGELOG.md` already capture architecture, ADRs, and history | — Pending |
| Active milestone = **v0.7.0 stabilization release** | Three Unreleased features (Perplexity, insertion-point scanning, prompt library UX) + recent UX/blocker fixes warrant a coordinated cut | — Pending |
| Skip domain research (`/gsd-new-project` Step 6) | Maintainer is the domain expert; SPEC and DECISIONS encode the relevant prior art | — Pending |

## Evolution

This document evolves at phase transitions and milestone boundaries.

**After each phase transition** (via `/gsd-transition`):
1. Requirements invalidated? → Move to Out of Scope with reason
2. Requirements validated? → Move to Validated with phase reference
3. New requirements emerged? → Add to Active
4. Decisions to log? → Add to Key Decisions
5. "What This Is" still accurate? → Update if drifted

**After each milestone** (via `/gsd-complete-milestone`):
1. Full review of all sections
2. Core Value check — still the right priority?
3. Audit Out of Scope — reasons still valid?
4. Update Context with current state

---
*Last updated: 2026-06-10 — Milestone v0.9.0 (Hardening, Quality & New Capabilities) started; v0.7.0 (2026-05-15) and v0.8.0 (2026-06-02) shipped and tagged*
