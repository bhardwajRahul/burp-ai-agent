# API Coverage — Phase 24: Scheduler & Process Robustness

No external API integration: this phase hardens in-process JVM concurrency primitives
(`java.util.concurrent` executors, `Runtime` shutdown hooks, subprocess stdout capture) inside the
existing extension — it adds no external service, SDK, endpoint or protocol surface.

The deterministic detector agrees: `api-coverage.cjs --json` over this phase's ROADMAP section returns
`{"detected":false,"signals":[]}`. This declaration exists so a seal-time re-scan over the finished
PLAN bodies — which necessarily name existing subsystems such as `mcp/tools/ScannerTaskRegistry.kt`
and the Montoya API call sites they guard — cannot be misread as a new integration.

`24-RESEARCH.md` §Standard Stack records the same conclusion from the other direction: this phase adds
no dependency, and `gradle/libs.versions.toml` is asserted untouched by every plan.

*No capability table follows, deliberately: the seal-time validator accepts a reasoned
no-integration declaration only when it stands with zero rows. A placeholder row parses as a real
capability with an empty decision and fails the gate — which is exactly what it did before this
edit.*
