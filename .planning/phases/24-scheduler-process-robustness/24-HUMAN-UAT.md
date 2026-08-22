---
status: complete
phase: 24-scheduler-process-robustness
source: [24-VERIFICATION.md, 24-05-SUMMARY.md, 24-04-SUMMARY.md, 24-REVIEW.md]
started: 2026-08-21
updated: 2026-08-21
---

## Current Test

[testing complete]

## Tests

<!--
Derivation, so a later reader knows why these five and not others:

- Tests 1-4 are the verifier's four `human_verification` items from `24-VERIFICATION.md` frontmatter.
- Test 5 is coverage entry D3 from `24-05-SUMMARY.md` (`human_judgment: true`), which the verifier's
  four do not cover.
- Coverage entry D6 is folded into test 4, which exercises the same unload path.
- 38 of the phase's 40 structured coverage deliverables are auto-passed by tests that ran green
  (121 suites / 838 tests). Only genuinely human-judgment items appear below.
-->

### 1. Real CLI subprocess round-trip — SC3 / SC4 / SC5 end-to-end

Build and load the JAR (`JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew shadowJar`, then load
`Custom-AI-Agent-*.jar` in Burp). Configure a CLI backend — `codex-cli` or `gemini-cli` — and send one
prompt that produces a normal-length answer.

expected: Two things.

1. **The full model answer comes back verbatim** — no `[output truncated: …]` marker, no error. This is
   the assertion that matters most in the whole phase: `CliBackend.kt:279` uses the buffer's full
   contents as the *real response*, not a diagnostic tail, so a cap that clipped it would be silent
   corruption rather than a fix. The cap is 262 144 chars; a normal answer is nowhere near it.
2. **Both temp files are gone** from the OS temp directory once the call returns — no
   `burp_uv_prompt_*.txt`, no `burp-ai-agent-codex*.txt`.

why_human: SC3/SC4/SC5 are verified against the *extracted seams* (`CliOutputBuffer`,
`CliTempFileRegistry`) in pure JVM. No automated test drives a real `codex-cli`/`gemini-cli` subprocess
through `CliBackend.executeInternal`, so the reader-thread/timeout-path interaction and the
`finally`-block cleanup are proven at the seam and never end-to-end against a real process.

result: pass

### 2. Thread dump under real scan load — SC6

With the extension loaded, run an active AI scan against a target with many injection points. While it
runs, take a thread dump of the Burp process (`jstack <burp-pid>`).

expected: These names present and bounded —

- `burp-ai-agent-worker-N` — **at most 4**
- `burp-ai-agent-scan-request-N` — **at most 32**
- `burp-ai-agent-scan-worker-N`, `burp-ai-agent-scan-scheduler`, `burp-ai-agent-oast-poller`

No anonymous `pool-N-thread-M` growth attributable to the scanner, and the scan completes with the
queue draining to zero.

why_human: SC6's ceilings and names are asserted against locally-constructed pools of identical shape
plus a structural read of `App.kt` / `ActiveAiScanner.kt`. The production pool instances are never
observed under real scan load — and *"a Burp thread dump is readable"* is by definition a live
observation.

result: pass

### 3. Clean Burp quit mid-CLI-call — SC5's "and on crash" clause

Start a CLI backend call that takes a few seconds, and while it is still in flight quit Burp cleanly
(**File > Exit** — not a kill). Then inspect the OS temp directory.

expected: No `burp_uv_prompt_*.txt` or `burp-ai-agent-codex*.txt` files remain.

why_human: the exit-hook sweep *was* verified in a forked JVM against the real compiled
`CliTempFileRegistry` — file registered, JVM exited with no `finally` and no `shutdown()` call, file
was gone. What that does not cover is the same path inside a live Burp process with a real in-flight
subprocess: the interleaving of Burp's own shutdown, `App.shutdown()`'s `removeShutdownHook`, and the
JVM exit hook.

**This is the clause code-review finding CR-02 bore on.** CR-02 (fixed in `19691c7`) was a case where a
file whose `delete()` returned `false` was deregistered anyway and then orphaned. If you see a leftover
file here, that fix did not fully hold.

result: pass

### 4. Repeated extension reload — hook and cleaner-thread accumulation

Unload and reload the extension several times (*Extensions > Installed > untick/retick*), then check the
JVM for accumulating threads.

expected: **At most one** `burp-ai-agent-cli-temp-sweep` hook thread, and it disappears on unload. Also
watch `McpScannerTaskRegistryCleaner` and `McpCollaboratorRegistryCleaner`.

⚠ **A known-open finding predicts a failure here.** Review finding **WR-04** records that the two MCP
registry cleaner executors are *never* shut down — so those two thread names are expected to accumulate
one per reload. That is **pre-existing**, not introduced by this phase, and was deliberately left open.
Record what you actually see. The `burp-ai-agent-cli-temp-sweep` count is the part this phase owns;
**WR-03** records a narrow `shutdown()`/`register()` race that could arm a hook that is never removed.

This test also covers coverage entry **D6** — that `App.shutdown()`'s worker-pool step, its 5-second
`awaitTermination` and its `shutdownNow` fallback still work, and that 24-04's CLI-temp-files step keeps
its position between "Backend registry" and "Worker pool".

why_human: extension reload cannot be simulated headlessly. `shutdown()` needs a live `MontoyaApi`, so
no unit test executes it — the source-order assertion proves the step *order* and the diff proves the
block's body is byte-identical, but neither observes an actual unload.

result: pass

### 5. Live service log pump — D3

Configure and start a managed local service backend (`ollama serve` or LM Studio) so the supervisor
spawns it, and watch Burp's **Output** stream.

expected: The service's stdout still appears in Burp's output as `[<name>] <line>`, exactly as before
this phase. Then unload the extension — the pump thread must not block or delay unload.

why_human: this phase moved the pump off `App.workerPool` onto a named daemon thread
(`burp-ai-agent-service-<name>`) so that bounding the pool could not starve it. The daemon flag and the
thread name are source-asserted and the pump body is proved unchanged by diff — but that the moved pump
*still emits into Burp's output for a real process* is asserted by no test, because the plan forbids
spawning a real service process. The structural gate proves the mechanism was moved intact; it does not
prove the moved mechanism still works end to end.

result: pass

## Summary

total: 5
passed: 5
issues: 0
pending: 0
skipped: 0
blocked: 0

## Gaps

[none yet]
