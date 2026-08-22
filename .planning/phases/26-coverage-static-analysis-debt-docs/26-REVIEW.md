---
phase: 26-coverage-static-analysis-debt-docs
reviewed: 2026-08-22T00:00:00Z
depth: standard
diff_base: 4f0ebd7
files_reviewed: 55
files_reviewed_list:
  - DECISIONS.md
  - README.md
  - SECURITY.md
  - SPEC.md
  - build.gradle.kts
  - detekt-baseline.xml
  - docs/anthropic-backend.md
  - docs/external-mcp-servers.md
  - docs/mcp-hardening.md
  - docs/ui-safety-guide.md
  - src/main/kotlin/com/six2dez/burp/aiagent/backends/cli/CliBackend.kt
  - src/main/kotlin/com/six2dez/burp/aiagent/backends/ollama/OllamaBackend.kt
  - src/main/kotlin/com/six2dez/burp/aiagent/config/AgentSettings.kt
  - src/main/kotlin/com/six2dez/burp/aiagent/config/Defaults.kt
  - src/main/kotlin/com/six2dez/burp/aiagent/config/McpSettings.kt
  - src/main/kotlin/com/six2dez/burp/aiagent/mcp/KtorMcpServerManager.kt
  - src/main/kotlin/com/six2dez/burp/aiagent/mcp/McpSupervisor.kt
  - src/main/kotlin/com/six2dez/burp/aiagent/mcp/tools/McpToolExecutorImpl.kt
  - src/main/kotlin/com/six2dez/burp/aiagent/mcp/tools/McpToolHelpers.kt
  - src/main/kotlin/com/six2dez/burp/aiagent/scanner/ActiveAiScanner.kt
  - src/main/kotlin/com/six2dez/burp/aiagent/scanner/AdaptivePayloadEngine.kt
  - src/main/kotlin/com/six2dez/burp/aiagent/scanner/AiScanCheck.kt
  - src/main/kotlin/com/six2dez/burp/aiagent/scanner/PassiveAiScanner.kt
  - src/main/kotlin/com/six2dez/burp/aiagent/scanner/PassiveAiScannerHeuristics.kt
  - src/main/kotlin/com/six2dez/burp/aiagent/scanner/ResponseAnalyzer.kt
  - src/main/kotlin/com/six2dez/burp/aiagent/ui/ChatPanel.kt
  - src/main/kotlin/com/six2dez/burp/aiagent/ui/McpHelpPanel.kt
  - src/main/kotlin/com/six2dez/burp/aiagent/ui/SettingsPanelMcpTabs.kt
  - src/main/kotlin/com/six2dez/burp/aiagent/ui/components/PrivacyPill.kt
  - src/main/kotlin/com/six2dez/burp/aiagent/ui/components/ToolInvocationDialog.kt
  - src/main/kotlin/com/six2dez/burp/aiagent/ui/design/Components.kt
  - src/main/kotlin/com/six2dez/burp/aiagent/ui/panels/BackendConfigPanel.kt
  - src/main/kotlin/com/six2dez/burp/aiagent/util/SsrfGuard.kt
  - src/test/kotlin/com/six2dez/burp/aiagent/DecisionsAdrTest.kt
  - src/test/kotlin/com/six2dez/burp/aiagent/SecurityDocsTest.kt
  - src/test/kotlin/com/six2dez/burp/aiagent/audit/AiRequestLoggerTest.kt
  - src/test/kotlin/com/six2dez/burp/aiagent/backends/cli/CliCommandHelpersTest.kt
  - src/test/kotlin/com/six2dez/burp/aiagent/backends/cli/ShellEscapeTest.kt
  - src/test/kotlin/com/six2dez/burp/aiagent/config/McpSettingsTest.kt
  - src/test/kotlin/com/six2dez/burp/aiagent/config/McpTokenStrengthTest.kt
  - src/test/kotlin/com/six2dez/burp/aiagent/config/SecretCipherTest.kt
  - src/test/kotlin/com/six2dez/burp/aiagent/mcp/McpSupervisorConnectionTest.kt
  - src/test/kotlin/com/six2dez/burp/aiagent/mcp/McpTestServerSupport.kt
  - src/test/kotlin/com/six2dez/burp/aiagent/mcp/schema/SerializationTest.kt
  - src/test/kotlin/com/six2dez/burp/aiagent/mcp/tools/AiGateMcpToolTest.kt
  - src/test/kotlin/com/six2dez/burp/aiagent/mcp/tools/McpToolHelpersTest.kt
  - src/test/kotlin/com/six2dez/burp/aiagent/mcp/tools/McpToolModelsTest.kt
  - src/test/kotlin/com/six2dez/burp/aiagent/mcp/tools/ProxyHistoryListenerPortFilterTest.kt
  - src/test/kotlin/com/six2dez/burp/aiagent/redact/EntropyTest.kt
  - src/test/kotlin/com/six2dez/burp/aiagent/redact/RedactionPolicyTest.kt
  - src/test/kotlin/com/six2dez/burp/aiagent/redact/RedactionTest.kt
  - src/test/kotlin/com/six2dez/burp/aiagent/redact/SafeRegexTest.kt
  - src/test/kotlin/com/six2dez/burp/aiagent/ui/ChatPanelEdtGuardTest.kt
  - src/test/kotlin/com/six2dez/burp/aiagent/util/SsrfGuardNoResolutionTest.kt
  - src/test/kotlin/com/six2dez/burp/aiagent/util/SsrfGuardTest.kt
findings:
  critical: 0
  warning: 2
  info: 5
  total: 7
status: issues_found
---

# Phase 26: Code Review Report

**Reviewed:** 2026-08-22
**Depth:** standard (with targeted deep tracing on the five prioritised areas)
**Files Reviewed:** 55
**Status:** issues_found (0 Critical / 2 Warning / 5 Info)

## Summary

**The headline result is a negative one, and it is the useful one: I could not find a behaviour change
hidden inside the 29-file mechanical trim.** I traced every one of the 56 removed `detekt-baseline.xml`
entries back to its source edit and satisfied myself that each is behaviour-preserving, for reasons given
per category in "What I verified" below. The two Warnings I do raise are elsewhere: one is a
narrative-accuracy defect in 26-06's own deliverable (the ADR, the source KDoc and `docs/mcp-hardening.md`
all say the new non-loopback diagnostic *replaces* a misleading line that is in fact still emitted
alongside it), and one is an incomplete remediation (26-01 fixed one of two byte-identical denylist shell
quoters; the other still feeds `sh -c`).

`shellEscape`'s allowlist flip is correct and strictly stronger than what it replaced — I could not
construct an input that reaches `/bin/sh` unquoted. The `SsrfGuard.IPV6_REGEX` widening is safe, and I
verified that against the JDK 21 source rather than against the executor's prose. The `McpSupervisor`
restructure is genuinely verbatim. The function→property conversions in `CliBackend.kt` are safe *in this
codebase* for a reason the executor did not state (see IN-05).

**Build state confirmed independently, not taken on trust:** `detekt` + `ktlintCheck` green;
`./gradlew test --rerun-tasks` executed the full suite from scratch — 1131 tests across 158 result files,
zero `<failure>`/`<error>` elements. The `RedactionTest` wall-clock flake did not fire.

### What I actually examined vs. skimmed

**Read in full and traced:** `CliBackend.kt` (all shell/argv paths, plus every `ProcessBuilder` and
`sh -c` site in `src/main` via grep), `SsrfGuard.kt`, `McpSupervisor.kt` (`openConnection`,
`installLoopbackPin`, `probeExistingServer`, `requestRemoteShutdown`, `handleBindFailure`,
`attemptTakeover`), `SettingsPanelMcpTabs.kt` (both halves of the `refreshMcpNotice` split, plus the
listener wiring in `SettingsPanelInit.kt` that drives it), `PrivacyPill.kt`, `Components.toolBadge`,
`ResponseAnalyzer` around the removed `isTrueCondition`, `AdaptivePayloadEngine` around the removed
`safeHost`, `McpHelpPanel.kt`, `ChatPanelEdtGuardTest.kt`, `CliCommandHelpersTest.kt`,
`SsrfGuardNoResolutionTest.kt`, `McpSupervisorConnectionTest.kt`, `McpTokenStrengthTest.kt`,
`McpSettingsTest.kt` (new half), the `build.gradle.kts` diff, the `DECISIONS.md` diff, and the
`docs/mcp-hardening.md` diff. Extracted `java.base/java/net/InetAddress.java` from the local JDK 21
`src.zip` to settle the resolver question mechanically.

**Skimmed:** `SECURITY.md` / `SPEC.md` / `README.md` / the three other `docs/` pages (read the additions,
did not re-verify every historical claim); the bulk of the ~250 new tests in the `mcp` and `redact` trees
(`AiGateMcpToolTest`, `McpToolHelpersTest`, `SerializationTest`, `ProxyHistoryListenerPortFilterTest`,
`AiRequestLoggerTest`, `SecretCipherTest`, `EntropyTest`, `RedactionPolicyTest`, `SafeRegexTest`,
`SsrfGuardTest`) — for these I ran a mechanical vacuity sweep (see below) rather than reading each case.

### What I verified about the 29-file trim, category by category

| Category | Verified how | Result |
|---|---|---|
| `UseCheckOrError` ×6, `UseRequire` ×4 | Read every conversion; checked exception type and applied De Morgan to each inverted condition | All 10 preserve the thrown type exactly (`error`/`check` → `IllegalStateException`, `require` → `IllegalArgumentException`, same as the literal throws they replaced). All four inversions are correct, including the two non-trivial ones in `ToolInvocationDialog.readJsonValue` (`!(A && !B)` → `!A \|\| B`). No caller catches a narrower type. |
| `FunctionOnlyReturningConstant` ×14 | Read all 14; each is a `companion object` member of `AgentSettingsRepository` returning a string or int literal | None had per-call semantics. The four that *do* — `defaultOllamaTimeoutSeconds`, `defaultLmStudioTimeoutSeconds`, `defaultOpenAiCompatTimeoutSeconds`, `defaultNvidiaNimTimeoutSeconds`, plus `defaultBountyPromptDir` (reads `user.home`) — were correctly left as functions. `defaultMcpSettings()` is in a different file and was correctly left alone. |
| `MayBeConst` | Only one entry landed: `RedactionTest`'s `val l = 42` → `const val L` | Test-only, a literal, RFC-fixed. No runtime-initialised `val` became a `const`. |
| `UnusedPrivateProperty` / `UnusedPrivateMember` removals (9) | grepped every removed symbol across `src/main` + `src/test`; checked for reflection (`getDeclaredField`, `Class.forName`, `ServiceLoader`) in `src/main` | All unreachable. The only reflection in production is `ServiceLoader.load(AiBackendFactory::class.java, …)` in `BackendRegistry.kt`, and none of the removed symbols is a factory. `ResponseAnalyzer.isTrueCondition` was genuinely dead — the TRUE-condition half is implemented in `analyzeBooleanBasedDual`. `AdaptivePayloadEngine.safeHost` was dead because the prompt never carried the host (no privacy regression). |
| `ImplicitDefaultLocale` ×3 | Read all three | Two are user-visible under non-English locales (IN-04). One is a latent bug fix. |
| Stale baseline entries removed without a source edit (`ReturnCount:maybeExecuteToolCall`, `isValidHost`, `clearCurrentChat`, `LongMethod:wireActions`/`applySettingsToUi`, `CyclomaticComplexMethod:refreshPrivacyNotice`, `EmptyFunctionBlock`, `InvalidPackageDeclaration`, `TooManyFunctions:KtorMcpServerManager`) | Ran `detekt` at HEAD | Green. detekt ignores baseline entries that match nothing, so a stale entry cannot be load-bearing; a *live* one removed would have gone red. Sound. |

### Vacuity sweep on the new tests

Ran an AWK pass over every changed test file looking for `@Test` methods containing no
`assert*`/`assume*`/`verify(`/`fail(`/`assertThrows`. **Zero assertion-free tests** (the single hit was a
false positive on `McpTestServerSupport.freePort()`, a helper). Also grepped for conditionally-vacuous
bodies: exactly one (IN-03). `ChatPanelEdtGuardTest` and `McpTokenStrengthTest` — the two structural
suites most at risk of asserting against the wrong text — both carry explicit
"the declaration was found" and "the KDoc is genuinely attached" pre-assertions, so a stale lookup fails
loudly rather than passing against someone else's prose. `SsrfGuardNoResolutionTest` carries both an
installed-resolver control and a corpus-size floor.

---

## Warnings

### WR-01: The non-loopback TLS diagnostic is an *addition*, but the ADR, the source KDoc and the runbook all say it is a *replacement*

**Files:**
- `src/main/kotlin/com/six2dez/burp/aiagent/mcp/McpSupervisor.kt:366-372` (KDoc) and `:381-387` (inline comment)
- `DECISIONS.md:265` (ADR-17, first Consequences residual)
- `docs/mcp-hardening.md:32` (operator runbook, item 3)

**Issue:** All four texts state that the operator is now told the real reason *instead of* being told that
no compatible MCP server was found. The source KDoc is explicit: *"so the operator is told that, rather
than being told by `handleBindFailure` that no compatible MCP server was found when the listener was
their own."* The runbook says the non-loopback bind *"gets `MCP takeover was not attempted under TLS:
certificate pinning is applied to loopback hosts only …` **instead**"*.

That is not what happens. `handleBindFailure` (`McpSupervisor.kt:196-228`) is unchanged by this phase.
Trace one non-loopback TLS bind conflict:

1. `probeExistingServer` calls `openConnection` → the **new** diagnostic is logged (`logToOutput`).
2. `conn.connect()` fails, because the JDK default trust store refuses `CN=burp-mcp` — exactly as the ADR
   describes. The `catch` at `:298` logs `"MCP probe failed on <host>:<port>: <message>"`.
3. `probe` returns `false` → `attemptTakeover` returns `NO_COMPATIBLE_SERVER` → `handleBindFailure` logs
   **`"Port appears busy and no compatible MCP server was detected for takeover."`** to `logToError`.

The operator therefore sees three lines, the last of which is still the misleading one WR-03 was raised
about — and it is the one on the Errors tab, which is where an operator looks first. `docs/mcp-hardening.md`
item 1 additionally tells the reader that that line means "the probe found nothing usable", so the runbook
now contradicts itself across items 1 and 3 for this configuration.

**Why it matters:** This phase's stated deliverable (QUAL-07 / DOC-03) is that written claims agree with
what ships. This is the same class of defect the phase was chartered to close, in the phase's own output,
and it is load-bearing: an operator following the runbook will conclude the two outcomes are mutually
exclusive and stop reading after the "no compatible MCP server" error.

`McpSupervisorConnectionTest` cannot catch this — it invokes `openConnection` in isolation by reflection
and never reaches `handleBindFailure`, so all five of its rows are correct and none of them observes the
composed operator experience.

**Fix (choose one; the first is cheaper and closes the accuracy gap by itself):**

1. Reword the three texts from replacement to addition, e.g. in `McpSupervisor.kt`'s KDoc:
   ```
   * … so the operator is told that IN ADDITION to `handleBindFailure`'s
   * "no compatible MCP server was detected" error, which is unchanged and still fires: the probe
   * cannot complete its TLS handshake against our own certificate without the pin, so the takeover
   * genuinely does find nothing usable. The new line names the reason the probe could not succeed.
   ```
   and make `docs/mcp-hardening.md` item 3 say "in addition to the line in item 1", not "instead".
2. Or make it true: thread the non-loopback case out of `openConnection` as state (e.g. a
   `BindTakeoverOutcome.PINNING_NOT_APPLICABLE` limb) so `handleBindFailure` emits one accurate error
   rather than a contradictory pair. That is a larger change and would need its own test row asserting
   the composed output, not just the `openConnection` output.

A regression guard for either option belongs in a test that drives `handleBindFailure`, not
`openConnection`.

**Secondary, same site:** `openConnection` is called from *both* `probeExistingServer` (`:263`) and
`requestRemoteShutdown` (`:317`). If a squatter on a non-loopback host ever presents a publicly-trusted
certificate, the probe succeeds and the new diagnostic is emitted **twice** in one takeover attempt (and
`2N` times across `N` retries). `McpSupervisorConnectionTest.openConnection_nonLoopbackTls_saysWhyTheTakeoverWasNotAttempted`
asserts `diagnostics.size == 1` per *invocation*, which does not constrain this. Low impact — log noise
only — but worth a sentence in the KDoc so the next reader is not surprised.

---

### WR-02: `SettingsPanel.shellQuote` is the byte-identical twin of the denylist 26-01 replaced, and it still feeds `sh -c`

**File:** `src/main/kotlin/com/six2dez/burp/aiagent/ui/SettingsPanelActions.kt:482-486`, reached from
`openExternalCli` at `:463-467`

**Issue:** 26-01's rationale for SC1 is that a metacharacter denylist "cannot be under-enumerated the way
an allowlist can", and it replaced this exact body in `CliBackend.kt`:

```kotlin
internal fun SettingsPanel.shellQuote(value: String): String {
    if (value.isEmpty()) return "''"
    if (value.none { it.isWhitespace() || it == '"' || it == '\'' }) return value   // <-- the denylist
    return "'" + value.replace("'", "'\"'\"'") + "'"
}
```

That is character-for-character the pre-26-01 `shellEscape`. It survives because
`SettingsPanelActions.kt` was not in any 26-x plan's `files_modified`, and its output is interpolated into
a `shellCmd` string that is handed to `ProcessBuilder("sh", "-c", shellCmd)`. The threat model is
identical to the one 26-01 cites — a CLI command string is operator-typed *or settings-imported*, and this
path turns it into shell syntax.

**Current exploitability:** I traced it and it is **not currently exploitable**, but only incidentally.
Every call site passes `"$command; exec bash"`, and that suffix always contains whitespace, so the
denylist branch is never taken and the value always reaches the correct single-quote branch (whose
`'` → `'"'"'` escaping is sound). The safety comes from an unrelated string concatenation, not from the
quoter. Remove or reorder the `; exec bash` suffix — a plausible future edit — and the denylist becomes
live with `$`, `` ` ``, `;`, `|`, `&`, `*`, `(` and `)` all unquoted.

The macOS limb at `:458-459` (`osascript … do script "$escaped"`) escapes only `\` and `"` into an
AppleScript string literal, which has no interpolation, so it is sound for a different reason.

**Why it matters:** SC1 is recorded closed on the basis that the extension no longer passes a denylist
result to a shell. It still does, at a second site, on the same trust boundary. A reader auditing SC1 by
grepping for the pattern will find this and reasonably conclude the fix was not applied.

**Fix:** delete `SettingsPanel.shellQuote` and call the now-`internal`
`com.six2dez.burp.aiagent.backends.cli.shellEscape` — the two functions have identical contracts and the
new one is a superset:

```kotlin
// SettingsPanelActions.kt
import com.six2dez.burp.aiagent.backends.cli.shellEscape
…
"x-terminal-emulator -e bash -lc ${shellEscape("$command; exec bash")} " +
```

If a cross-package dependency from `ui` to `backends.cli` is unwanted under the AGENTS.md layering rule,
lift `shellEscape` and `SHELL_SAFE_CHARS` into a shared `util` file and have both call sites use it —
what must not persist is two divergent quoters for the same shell. Add a row to `ShellEscapeTest` (or a
sibling) covering the second call site so the two cannot drift apart again.

---

## Info

### IN-01: `McpHelpPanel` is now a wholly unreferenced class; the trim removed its parameter instead of the file

**File:** `src/main/kotlin/com/six2dez/burp/aiagent/ui/McpHelpPanel.kt:1-35`

**Issue:** 26-07 cleared `UnusedPrivateProperty:McpHelpPanel$api: MontoyaApi` by dropping the constructor
parameter, leaving `class McpHelpPanel { val panel = JPanel(…) }`. But
`git grep -n "McpHelpPanel" -- src` returns exactly one hit — its own declaration — both at HEAD and at
`4f0ebd7`. The class had no call sites before the phase and has none now; it is 35 lines of dead UI. It is
not a `ServiceLoader` service and nothing reflects on it.

**Why it matters:** minor, but the trim's own justification (removing code nobody reaches) applies more
strongly to the whole file than to the parameter. Leaving the class keeps a plausible-looking help panel
that no user will ever see, and the next reader must repeat this grep to find that out.

**Fix:** delete `src/main/kotlin/com/six2dez/burp/aiagent/ui/McpHelpPanel.kt`, or wire it into the MCP tab
if the help text is wanted. If it is being kept deliberately for a future phase, say so in a one-line KDoc
so the next trim does not re-litigate it.

### IN-02: Nine new `ExperimentalSerializationApi` opt-in warnings introduced by 26-02's tests

**File:** `src/test/kotlin/com/six2dez/burp/aiagent/mcp/tools/McpToolModelsTest.kt:132, 139, 142`

**Issue:** `compileTestKotlin` at HEAD emits nine `This declaration needs opt-in` warnings from
`MissingFieldException`, which is annotated `@ExperimentalSerializationApi`. These are new in phase 26 —
they arrive with the `RequiredFields` nested class.

**Why it matters:** the phase's theme is clearing static-analysis noise; adding compiler-warning noise in
the same phase works against it, and un-opted-in experimental API is the kind of thing that turns into a
compile error on a kotlinx-serialization bump.

**Fix:** annotate the nested class:
```kotlin
@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
@Nested
inner class RequiredFields { … }
```
(Note `DesignComponentsTest.kt:199`'s "Check for instance is always 'true'" warning is **pre-existing** —
that file is outside this phase's diff and is not being reported here.)

### IN-03: `productionDefaultIsANoOpOnThisNonWindowsMachine` asserts nothing on Windows

**File:** `src/test/kotlin/com/six2dez/burp/aiagent/backends/cli/CliCommandHelpersTest.kt:44-54`

**Issue:** the whole assertion sits inside `if (!runningOnWindows) { … }`. On a Windows CI agent or a
Windows contributor's machine the test executes zero assertions and reports green — the only
conditionally-vacuous test in the phase's additions, and it is the one guarding a Windows-only code path.

**Why it matters:** this is precisely the platform nobody can exercise from the macOS dev machine, so a
silent green on the one platform where it *would* mean something is the wrong failure mode. Everything
else in this suite asserts unconditionally, so this reads as an oversight rather than a decision.

**Fix:**
```kotlin
import org.junit.jupiter.api.Assumptions.assumeFalse
…
assumeFalse(System.getProperty("os.name").lowercase().contains("win"), "asserts the non-Windows default")
assertEquals(cmd, normalizeWindowsCommand(cmd), "on a non-Windows JVM the default parameter must select the no-op path")
```
A reported skip is honest; a silent pass is not.

### IN-04: Two `ImplicitDefaultLocale` fixes are real behaviour changes, not cosmetic — one is a latent bug fix worth recording

**Files:** `src/main/kotlin/com/six2dez/burp/aiagent/ui/ChatPanel.kt:1675-1676`;
`src/main/kotlin/com/six2dez/burp/aiagent/backends/ollama/OllamaBackend.kt:476`

**Issue:** all three `String.format` calls gained `Locale.ROOT`. Two of them change what the user sees or
what goes on the wire:

- `ChatPanel.formatChars` — a Burp running under a comma-decimal locale (`es`, `de`, `fr`, …) previously
  rendered `1,5M` / `1,5K` and now renders `1.5M` / `1.5K`. Deliberate and desirable given AGENTS.md's
  English-only rule, but it is a user-visible delta inside a change set described as behaviour-preserving.
- `OllamaBackend`'s JSON escaper — `String.format("\\u%04x", ch.code)` under a locale with a non-Latin
  default numbering system (e.g. `ar` with `-u-nu-arab`) could emit non-ASCII digits inside a `\uXXXX`
  escape, producing **malformed JSON** on the request to the Ollama endpoint. `Locale.ROOT` fixes that.
  This is a genuine (if narrow) bug fix that 26-07's SUMMARY files a purely mechanical trim entry.

**Why it matters:** no action needed on the code — both changes are right. But "1096 → 1040, all
behaviour-preserving" understates the second one, and a fixed-in-passing serialization bug deserves to be
findable later.

**Fix:** add a line to `26-07-SUMMARY.md` (or the phase COVERAGE seal) noting the Ollama escaper change as
a latent-defect fix rather than a lint clean-up, and noting the `formatChars` locale delta.

### IN-05: `PassiveAiScannerHeuristics.kt`'s replacement comment points at one of two surviving copies

**File:** `src/main/kotlin/com/six2dez/burp/aiagent/scanner/PassiveAiScannerHeuristics.kt:8-11`

**Issue:** the comment left in place of the three deleted constants says *"Body truncation limits live in
`PassiveAiScanner.kt`."* They live in **two** files: `PassiveAiScanner.kt:419-420` and
`PassiveAiScannerAnalysis.kt:26-27`, both `private const val`, both `3_000` / `6_000`, both live and both
used. The deletion removed the third (unused) copy; the duplication the comment was written to explain
is still there.

**Why it matters:** a reader who follows the pointer, finds one copy and edits it will silently change
only half the truncation behaviour — `PassiveAiScanner.kt:275-276` and `PassiveAiScannerAnalysis.kt:194-195`
truncate on different paths.

**Fix:** either correct the comment to name both files, or better, hoist the two constants to a single
`internal const val` in `PassiveAiScannerFilters.kt` next to `LOCAL_FINDING_SKIP_CONFIDENCE` (which is
already the single home for the confidence threshold the comment's first sentence describes) and have both
sites reference it.

---

## Appendix: prioritised items checked and found sound

Recording these explicitly, because "I looked and found nothing" is only useful if the method is visible.

**`shellEscape`'s denylist → allowlist flip (`CliBackend.kt:869-884`) — sound; I could not construct a
bypass.** `SHELL_SAFE_CHARS` is `[A-Za-z0-9._/-]` tested with `Char in String` (i.e. `indexOf`, not a
character-range or regex — no `-` range-interpretation hazard). Every character outside it, including
`;` `$` `` ` `` `|` `&` `*` `?` `~` `(` `)` `{` `<` `>` `#` `!` newline and all non-ASCII, takes the
single-quote branch, whose `'` → `'"'"'` close/escape/reopen is the correct POSIX idiom. Empty → `''`.
No character *inside* the allowlist carries shell meaning in any position, so an all-allowlisted argument
cannot be reinterpreted. `buildPtyCommand` escapes **every** element before the join, and its two platform
argv shapes are passed to `ProcessBuilder` as separate elements, so there is no second round of shell
parsing. I grepped every `ProcessBuilder`, `/bin/sh`, `sh -c`, `cmd /c`, `osascript` and
`Runtime.getRuntime` site in `src/main`: the only other shell path is WR-02's.

**`SsrfGuard.IPV6_REGEX` widening — the no-resolution property genuinely still holds, verified against the
JDK, not the prose.** I extracted `java.base/java/net/InetAddress.java` from the local JDK 21 `src.zip`.
Lines 1661-1663 read:
```java
if ((addr = IPAddressUtil.textToNumericFormatV6(host)) == null &&
        (host.contains(":") || ipv6Expected)) {
    throw invalidIPv6LiteralException(host, ipv6Expected);
}
```
So **any** host containing `:` that fails IPv6 literal parsing throws before `getAllByName0`, i.e. before
the name service. `resolveIpv6Literal` is gated behind `host.contains(':')` at `SsrfGuard.kt:79`, so the
conjunct is a hard resolver gate and admitting `.` to the character class cannot route a hostname into a
lookup. The `else` fall-through to `getAllByName0` at line 1688 is reachable only for `:`-free hosts, which
never reach that branch. I confirmed the corpus in `SsrfGuardNoResolutionTest` includes two inputs that
genuinely exercise the widened class (`http://[a.b:c]/` reaches `getByName` via `extractAuthorityHost`'s
bracket limb; note `http://1.2:3/` does **not** — `URI` parses it as host `1.2` port `3` and it takes the
IPv4 arm — so the corpus is one entry weaker than it reads, but the JDK-source argument makes that moot).

**`isWindowsOs` / `windowsNpmShimDirs` function → property (`CliBackend.kt:1112`, `:1121-1126`) — safe
here, for a reason the KDoc does not give.** The KDoc argues "a JVM cannot see these change after start",
which is true for `getenv` but **false for `System.getProperty("os.name")`** — any code can call
`System.setProperty`. What actually makes it safe is that no test in this repo mutates it: I grepped
`setProperty("os.name")` across `src/test` and found zero; the three tests that touch `os.name`
(`CliSupervisionTest:29`, `CliCommandHelpersTest:50`, and `CliCommandTokenizerTest`'s KDoc) only *read* it,
and the two Windows-branch helpers were given explicit defaulted parameters precisely so no test needs to.
Consider tightening the KDoc's justification from "cannot change" to "nothing in this module changes it,
asserted by grep" — the stronger claim is the one a future contributor would break.

`windowsNpmShimDirs` is also behaviourally identical to `resolveWindowsNpmShim`: same three variables, same
`isNotBlank` filter, same order, and `File(appData, "npm")` + `File(dir, "$name.cmd")` resolves to the same
path as `File(appData, "npm\\$name.cmd")` on Windows. The `when`-rewrite of `normalizeWindowsCommand`
preserves all four limbs including the `else -> cmd` fall-throughs.

**`McpSupervisor.openConnection` restructure — genuinely verbatim, no trust decision moved.** Before:
`if (tls && https && loopback) { pin-or-log }`, non-loopback silently returning `conn` untouched. After:
`if (tls && https) { if (loopback) installLoopbackPin(...) else log }`, non-loopback still returning `conn`
untouched. The pin body, the `HostnameVerifier` lambda (including the `runCatching { session.peerCertificates }`
guard that must yield `false` rather than propagate `SSLPeerUnverifiedException`) and the fail-closed
`pin == null` limb are byte-identical. The new limb installs nothing and cannot reach a permissive path —
`McpSupervisorConnectionTest`'s four rows assert `assignedSslSocketFactory`/`assignedHostnameVerifier` are
`null` in exactly the three cases where they must be. The only defect at this site is the narrative one in
WR-01.

**`refreshMcpNotice` / `mcpNoticeItems` split (`SettingsPanelMcpTabs.kt:576-659`) — behaviour identical.**
The `!mcpEnabled.isSelected` early return moved *above* the local computations, but every one of those
locals is a pure Swing read with no side effect, so hoisting the guard changes nothing. Accumulation
semantics (all applicable caveats, not a short-circuiting `when`) are preserved, as is the RISK-beats-WARN
level selection. The new weak-token item is correctly *not* gated on `external`, and I verified the wiring
end-to-end: `mcpToken.document`'s `DocumentListener` (`SettingsPanelInit.kt:289-300`) and the
`mcpTokenRegenerate` action (`:268-271`) both route through `updateRiskWarnings()` → `refreshMcpNotice()`,
so the advisory updates as the operator types.

**`build.gradle.kts` `inputs.file` ×6 — correct.** All six paths exist, all are read by `SecurityDocsTest`,
and declaring them on `tasks.test` is the right fix for the documented stale-cache class (a docs-only edit
produces byte-identical bytecode, so `inputs.dir("src/main/kotlin")` alone would not invalidate).

---

_Reviewed: 2026-08-22_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
