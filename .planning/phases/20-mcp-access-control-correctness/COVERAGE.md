# API Coverage — Phase 20

No external API integration: this phase puts the extension's OWN access-control checks in front of
an MCP server that already existed and an SDK that was already a dependency. It adds a gate; it
integrates nothing. The two "APIs" named anywhere in the phase scope are the Burp Montoya host API
this extension already runs inside, and the MCP surface it had already exposed before the phase
began.

This declaration is recorded because the seal-time gate re-runs the deterministic detector over the
PLAN bodies, where it fires on two signals — `{"verb": "consumes", "noun": "rest"}` and
`{"verb": "(surface)", "noun": "sdk"}`. The `sdk` hit traces to a single phrase describing what the
gate must cover: *"every request — including SDK-registered `GET /sse` and `POST /message`"*. That is
the phase naming what it defends, not what it connects to. Same false-positive class closed with a
reasoned declaration on Phase 23, Phase 27 (three rounds) and Phase 28.

## The dependency claim, measured — and stated differently from Phases 27 and 28

Phases 27 and 28 could each declare `build.gradle.kts` byte-unchanged. **Phase 20 cannot, and saying
so would be false.** The file DID change during this phase: `+16 / -1`. Measured over the phase's
commit range (`ecc69bc^` … `4f88365`):

| Check | Result |
|---|---|
| `build.gradle.kts` changed at all | **Yes** — +16 / −1 |
| Lines added/removed inside `dependencies { }` | **Zero** — no `implementation`/`api`/`compileOnly`/`testImplementation`/`runtimeOnly` line added or removed |
| Dependency-line count, phase start → end | **24 → 24** |
| `io.modelcontextprotocol:kotlin-sdk:0.5.0` present BEFORE the phase | **Yes** — already declared at `ecc69bc^` |

What the 16 lines actually are, so the reader does not have to take "no dependency" on trust:

1. A new `version` `@get:Input` on `GenerateBuildFlagsTask`, and `const val VERSION` in the generated
   `BuildFlags.kt` — captured at CONFIGURATION time because `org.gradle.configuration-cache=true`
   makes reading `project.version` from inside `@TaskAction` a build failure (SEC-05 / P11).
2. A `storeBuild.expected` test system property, so `McpBuildFlagsVersionTest` asserts the generated
   constant TRACKS the Gradle property instead of asserting a literal `false` — which had made
   `-PstoreBuild=true`, the BApp Store artifact path, fail its own suite.

Build-flag plumbing and a test seam. No new artifact is resolved, downloaded, or linked.

## Coverage rows

None. A capability matrix enumerates the surface of an API being integrated. Phase 20 integrates no
API — it constrains access to one already integrated — so there is no surface to enumerate and every
row would be invented. Per the capability registry's own instruction for this case, the reasoned
declaration above is recorded in place of a matrix.

## What this declaration does NOT assert

- It does not claim `build.gradle.kts` is unchanged. It changed; the change adds no dependency.
- It does not claim phase 20's access-control work is complete or correct — that is
  `20-VERIFICATION.md`'s job, and at the time of writing that file reads `human_needed`.
