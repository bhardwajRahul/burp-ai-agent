# API Coverage — Phase 23: EDT Confinement & UI Responsiveness

No external API integration: this phase moves existing Burp Montoya and MCP calls off the Swing EDT (a threading change, not an integration) and adds no new external API, SDK, or service surface.

## Why the detector fired

The `api-coverage.verify-pre` detector matched two prose signals in the phase
scope — `wraps api` and `(surface) api`. Both come from phrasing about
`api.http().sendRequest(...)`, the **Burp Montoya host API** the extension has
consumed since Phase 1, and about the `runBlocking { manager.callTool(...) }`
path to **already-integrated** external MCP servers.

Phase 23's deliverables are:

- `OffEdtDispatch` — a helper that moves work off the EDT and marshals results back.
- A throwing door guard on `McpToolExecutor.executeTool` so the EDT path is asserted, not assumed.
- `SettingsPersistQueue` — generation-ordered, off-EDT `settingsRepo.save()`.
- `SettingsPanel.shutdown()` superseding an in-flight save worker.
- Regression evidence for the REL-01 EDT-confinement guarantees.

No capability surface was added, widened, or narrowed. There is nothing to
enumerate as `INTEGRATE` / `OPT-OUT`, so a fabricated matrix row would be a
false record — the reasoned declaration above is the correct artifact per the
ai-integration capability's own guidance for a deterministic-detector false
positive.
