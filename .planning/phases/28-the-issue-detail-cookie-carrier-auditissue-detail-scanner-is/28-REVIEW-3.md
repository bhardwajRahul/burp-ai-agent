---
phase: 28-the-issue-detail-cookie-carrier-auditissue-detail-scanner-is
reviewed: 2026-08-28T08:17:21Z
depth: standard
diff_base: 0d2f1fea1aeef3c92eb91048ebd3c6f28b1015bf
round: 3
files_reviewed: 5
files_reviewed_list:
  - src/main/kotlin/com/six2dez/burp/aiagent/ui/SettingsPanelInit.kt
  - src/main/kotlin/com/six2dez/burp/aiagent/scanner/AiScanCheck.kt
  - src/test/kotlin/com/six2dez/burp/aiagent/ui/PrivacyModeTooltipBoundTest.kt
  - src/test/kotlin/com/six2dez/burp/aiagent/scanner/AiScanCheckDetailCookieCarrierTest.kt
  - src/test/kotlin/com/six2dez/burp/aiagent/redact/CookieCarrierInventoryTest.kt
findings:
  critical: 0
  warning: 6
  info: 3
  total: 9
status: issues_found
---

# Phase 28 (round 3): Code Review Report

**Reviewed:** 2026-08-28T08:17:21Z
**Depth:** standard
**Diff base:** `0d2f1fea1aeef3c92eb91048ebd3c6f28b1015bf..HEAD`
**Files Reviewed:** 5
**Status:** issues_found

## Summary

Round 3 is a record-repair round: one behavioural-surface change (the privacy-mode tooltip
string), one comment-only KDoc expansion, one new test class, and prose growth in two existing
test classes. Gates are green — `test` (3 classes, 20 assertions across the two new/changed
classes), `ktlintCheck` and `detekt` all pass; longest line 242 < the `.editorconfig` cap of 250.

Scope claims checked and confirmed:

- **`AiScanCheck.kt` is comment-only.** Every added line in the diff sits inside a `/** */`
  block. `isCookieInsertionPoint`'s body, `consolidateIssues`'s body and `buildDetail`'s template
  are byte-identical to base. No non-comment change found; nothing flagged for the absence of a
  code fix.
- **The `**Payload Used:**` assertions match their DEFENCE-IN-DEPTH KDoc.** The four new tests
  assert the write gate's three arms (STRICT strips, BALANCED strips, OFF survives) plus a
  `PARAM_URL` type-attribution control. None asserts a leak closure. `PAYLOAD.value = "' OR '1'='1"`
  genuinely carries no trace of `DETAIL_SENTINEL`, exactly as the fixture KDoc claims. No
  stronger-or-weaker mismatch here.
- **The measured facts are right.** `javap` on the resolved `montoya-api-2026.2.jar` confirms
  `AuditInsertionPointType` has 17 members with the pinned names in the pinned order, and confirms
  `AuditInsertionPoint.type()`'s default body is `getstatic ...EXTENSION_PROVIDED; areturn` — the
  KDoc correction is correct and the prior "may return null" claim was indeed false.
  `AgentSettings.kt:493` does default to `BALANCED`. `git show 0d2f1fe:...CookieCarrierTest.kt |
  grep -c "Payload Used"` returns `0`, so the "probe claim was false at that commit" repair is
  itself accurate.

What the round did not get right falls in two clusters.

**Cluster 1 — the operator surface the acceptance is conditioned on is the wrong widget.**
`D-28-10` made accepting the residual conditional on naming the bound "at the one surface where an
operator states an intent about redaction". The naming landed in a 206-character, 34-word Swing
tooltip. Swing's default `dismissDelay` is 4000 ms and no `ToolTipManager` tuning exists anywhere
in main source; plain-text `JToolTip` does not wrap and no `<html>` tooltip exists anywhere in the
codebase. The two clauses `D-28-10` actually requires are the *tail* of that string — the first
part to fall off the screen edge and the part an operator cannot reach before it self-dismisses.
Meanwhile a persistent, HTML, always-visible `privacyNotice` (`SubtleNotice`, `PrivacyConfigPanel`
NORTH, refreshed on every mode change) already sits directly above the same control. The new test
cannot see any of this and its own KDoc concedes as much.

**Cluster 2 — a record-repair round shipped three new overclaims.** "There is no read-time pass
over it" is false: `McpToolContext.redactIfNeeded` runs `Redaction.apply` over the final serialized
tool output under the *current* privacy mode, and this very test file's `redactedDetailFor` helper
exercises that pass. "`Redaction.apply` **provably** does not stand between a stale detail string
and its reader" rests on a sentinel whose own KDoc says it was built so "only the new type gate can
plausibly remove it". And a new assertion calls a pre-redaction string "the residual's OBSERVABLE
width" while the file's SC1 assertions deliberately use the redacted helper for exactly that
reason. Each of these is the overclaim vocabulary the phase-27/28 series exists to correct, now
committed at three record sites each.

## Narrative Findings (AI reviewer)

## Critical Issues

None.

## Warnings

### WR-01: The `D-28-10` bound is rendered in a widget that clips it and hides it after 4 seconds

**File:** `src/main/kotlin/com/six2dez/burp/aiagent/ui/SettingsPanelInit.kt:48-52`, `:90`

**Issue:** `PRIVACY_MODE_TOOLTIP` is 206 characters / 34 words. Measured against the rest of the
panel: the previous longest tooltip in this file is `mcpScopeOnly` at 145 characters, and the
string this replaced was 58. So the new copy is 42% longer than anything else in the panel and 3.5x
its own predecessor.

Two Swing defaults make that a functional problem, not a style quibble:

1. `ToolTipManager.sharedInstance().dismissDelay` defaults to **4000 ms**. `grep -rn
   "ToolTipManager" src/main/kotlin` returns zero hits, so nothing raises it. 34 words of careful
   technical prose is 10–13 seconds of reading; the tooltip disappears at 4.
2. A plain-text `JToolTip` does not wrap. `grep -rn 'toolTipText = "<html'` over main source
   returns zero hits, so this codebase has no wrapping idiom. 206 characters renders as one
   ~1200–1450 px line, which clips at the right screen edge on a 1366×768 or 1280-wide display —
   a normal Burp environment.

Both failure modes truncate the **end** of the string. The end is precisely where the two
`D-28-10`-conditioned clauses live (`Applies from now on…` and `Scanner findings already
recorded…`); the purpose sentence, which was never the condition, is the part that survives. The
acceptance of `D-28-09` is therefore conditioned on copy an operator may be unable to read.

`PrivacyModeTooltipBoundTest` cannot detect this — it asserts substrings of a constant, and its own
KDoc states "It does not prove an operator READ the tooltip." That concession is accurate and is
exactly the uncovered gap.

**Fix:** Move the bound to the surface that already exists for operator-facing privacy advisories
and is persistent, wrapping and HTML-capable:

```kotlin
// SettingsPanelInit.kt — tooltip keeps only the purpose sentence:
privacyMode.toolTipText = PRIVACY_MODE_TOOLTIP_PURPOSE

// SettingsPanelActions.kt — privacyNoticeFor(...) already composes an htmlMessage rendered by
// SubtleNotice in PrivacyConfigPanel's NORTH slot and is refreshed by refreshPrivacyNotice() on
// every privacyMode.addActionListener fire. Append the bound there, where it stays on screen:
"Applies from now on, not retroactively.<br>" +
    "Scanner findings already recorded keep the values they were built with; " +
    "re-scanning does not rewrite them."
```

If the tooltip must carry it, both defaults have to be corrected together — neither alone is
sufficient:

```kotlin
ToolTipManager.sharedInstance().dismissDelay = 30_000
privacyMode.toolTipText = "<html><body style='width:320px'>$PRIVACY_MODE_TOOLTIP</body></html>"
```

Note that the second form breaks `PrivacyModeTooltipBoundTest`'s
`assertFalse(line.contains('"'))` check, so the test needs updating alongside.

---

### WR-02: "There is no read-time pass over it" is false, and round 3 committed it at three record sites

**File:** `src/main/kotlin/com/six2dez/burp/aiagent/scanner/AiScanCheck.kt:109`;
`src/main/kotlin/com/six2dez/burp/aiagent/ui/SettingsPanelInit.kt:33-34`;
`src/test/kotlin/com/six2dez/burp/aiagent/redact/CookieCarrierInventoryTest.kt:786-787`

**Issue:** All three sites assert there is no read-time pass over `AuditIssue.detail()`. There is
one. `McpToolContext.redactIfNeeded` (`src/main/kotlin/com/six2dez/burp/aiagent/mcp/McpToolContext.kt:58-74`)
runs:

```kotlin
val finalText = Redaction.apply(raw, RedactionPolicy.fromMode(privacyMode), stableHostSalt = hostSalt)
```

over the **final serialized MCP tool output** — which is the blob containing every
`AuditIssue.detail()` the `scanner_issues` tool returns — keyed on the **current** `privacyMode`.
`AiScanCheckDetailCookieCarrierTest`'s own `redactedDetailFor` helper (`:705-708`) exists to
exercise that pass, and the file's SC1 assertions run through it. So the reviewed diff contains a
claim that the reviewed diff's sibling test disproves.

This is not pedantry: the same three sites then state the residual unconditionally — "still emits
the raw cookie value on a later STRICT read". With a read-time redactor in the path, that outcome
is *conditional* on the value evading the generic rules, which is a materially different and
narrower residual than the one now written down at four required record sites.

**Fix:** Replace the absolute claim with the accurate narrow one at all three sites, e.g.:

```
There is no read-time re-application of the TYPE-KEYED cookie gate — the insertion-point type is
gone by serialization time. The generic read-time redactor (McpToolContext.redactIfNeeded ->
Redaction.apply, current mode) DOES run over the blob, but it is not type-aware, so it cannot
substitute for the write-time gate.
```

---

### WR-03: "provably" rests on a sentinel deliberately engineered to evade `Redaction`

**File:** `src/main/kotlin/com/six2dez/burp/aiagent/scanner/AiScanCheck.kt:117-119`;
`src/test/kotlin/com/six2dez/burp/aiagent/redact/CookieCarrierInventoryTest.kt:790-793`

**Issue:** Both sites read that plan 28-05's red probe "recorded the sentinel surviving STRICT
redaction VERBATIM … so `Redaction.apply` **provably** does not stand between a stale detail string
and its reader."

The sentinel that survived is `DETAIL_SENTINEL = "cedar-anchor-marble-feather"`, and its own KDoc
(`AiScanCheckDetailCookieCarrierTest.kt:779-786`) says why it survived: *"Low-entropy hyphenated
words: no digits, no `=`, no metacharacters, so only the new type gate can plausibly remove it."*
The fixture was built to be invisible to `Redaction`.

A real cookie value is the opposite shape — a JWT, a base64 session id, a `name=value` pair — which
is exactly what the generic STRICT rules target. The probe therefore proves the redactor does not
rescue *that string*; it does not prove the redactor does not rescue *a cookie value*. "Provably"
is unearned, and it is load-bearing: it is the third leg of the argument that made `D-28-09`'s
acceptance reasonable.

**Fix:** Either downgrade the claim at both sites —

```
Plan 28-05's red probe recorded a sentinel chosen for its inability to match any generic rule
surviving STRICT redaction. That measures the absence of a TYPE-KEYED read-time control; the
residual's width for realistic high-entropy cookie values is UNMEASURED.
```

— or measure it, which is cheap given the existing helper:

```kotlin
@Test
fun aRealisticCookieValueOnAFailOpenTypeIsStillObservableAfterReadTimeRedaction() {
    val jwtShaped = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIn0.dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"
    val detail = redactedDetailFor(insertionPoint(AuditInsertionPointType.HEADER, jwtShaped), PrivacyMode.STRICT)
    // record whichever way it lands — that result IS the residual's real width
}
```

---

### WR-04: `theRouteTwoGateIsFailOpenForTheseCookieCapableTypes` calls a pre-redaction string the residual's "OBSERVABLE width"

**File:** `src/test/kotlin/com/six2dez/burp/aiagent/scanner/AiScanCheckDetailCookieCarrierTest.kt:447-454`

**Issue:** The assertion runs `detailFor(point, PrivacyMode.STRICT)` — raw `buildDetail` output —
and its message states: *"under STRICT an AuditInsertionPointType.$type point's baseValue() still
reaches the detail line VERBATIM … That is the residual's OBSERVABLE width."*

Nothing observes `buildDetail`'s return value. What a reader observes is the redacted
`scanner_issues` blob, which is why this same file's SC1 assertions
(`cookieBaseValueIsStrippedUnderStrict`, `:125`) deliberately use `redactedDetailFor` and not
`detailFor`. The new assertion adopts the weaker helper and then claims the stronger surface. It is
the same overclaim as WR-03, one helper call away from being true.

The residual itself is almost certainly real; the word "OBSERVABLE" is what is unearned, and it is
the word `ISSUE_DETAIL_CARRIER_DISPOSITION` clause (e) now relies on when it says "For those four
the gate is fail-OPEN today."

**Fix:** Swap the helper so the message becomes true, keeping the `detailFor` arm too if the
write-gate distinction is worth pinning separately:

```kotlin
val detail = redactedDetailFor(point, PrivacyMode.STRICT)
```

Or, if the write-time surface is genuinely what is being pinned, drop "OBSERVABLE" and say
"pre-redaction width — the observable width through `scanner_issues` is measured by <name> / is
UNMEASURED."

---

### WR-05: "Applies from now on, not retroactively" is a blanket claim the product contradicts, and a test now pins it

**File:** `src/main/kotlin/com/six2dez/burp/aiagent/ui/SettingsPanelInit.kt:50`;
pinned by `src/test/kotlin/com/six2dez/burp/aiagent/ui/PrivacyModeTooltipBoundTest.kt:15`

**Issue:** The sentence is unqualified and sits second in the tooltip, before the clause that
narrows it. As written it tells the operator the *whole setting* is forward-only. That is false for
the dominant data path: `McpToolContext.redactIfNeeded` applies `RedactionPolicy.fromMode(privacyMode)`
at send time to already-captured proxy history, issue blobs and context payloads. Switching to
STRICT **does** retroactively protect traffic Burp captured before the switch.

The forward-only property is specific to the four baked scanner-detail lines — which the third
clause already says correctly and specifically. The second sentence generalises a narrow residual
into a product-wide disclaimer.

For a privacy control this is a real operator-facing defect: an operator who reads sentence 2 can
reasonably conclude that STRICT gives no protection for anything already in the proxy history, and
either stop trusting the control or take an unnecessary destructive action (clearing history) to
compensate. It errs pessimistic, which is safer than the inverse, but it is still inaccurate copy
in a round chartered on accuracy.

`FORWARD_ONLY_CLAUSE` at `PrivacyModeTooltipBoundTest.kt:15` pins this exact wording as a required
substring, so the test currently defends the inaccuracy and will go red on the correction.

**Fix:** Scope the sentence to what is actually forward-only, and update the pinned clause with it:

```kotlin
internal const val PRIVACY_MODE_TOOLTIP =
    "Controls how traffic is redacted before sending to a model. " +
        "Applies to everything sent from now on, including traffic already captured. " +
        "One exception: scanner findings already recorded keep the values they were built with; " +
        "re-scanning does not rewrite them."
```

```kotlin
// PrivacyModeTooltipBoundTest.kt
private const val FORWARD_ONLY_CLAUSE = "Applies to everything sent from now on"
```

(Shortening this way also removes ~10 characters and one clause, which partially helps WR-01 but
does not resolve it — 4000 ms is still 4000 ms.)

---

### WR-06: The source-scan test claims coverage it does not have — a `setToolTipText(...)` call is invisible to it

**File:** `src/test/kotlin/com/six2dez/burp/aiagent/ui/PrivacyModeTooltipBoundTest.kt:86-93`,
`:182`

**Issue:** The needle is the literal `TOOLTIP_ASSIGNMENT = "privacyMode.toolTipText"`. The KDoc
claims the scan "closes both gaps" and justifies being a source scan rather than a symbol reference
because "a symbol reference cannot see a site that does not use the symbol, which is precisely the
case worth catching."

A source scan on that literal has the same blind spot it was built to avoid. Kotlin permits calling
the underlying Java setter directly, and it compiles cleanly:

```kotlin
privacyMode.setToolTipText("Controls how traffic is redacted before sending to a model.")
```

That line contains neither `privacyMode.toolTipText` nor `PRIVACY_MODE_TOOLTIP`, so the scan
returns 1 match (the good one), passes both follow-up assertions, and reports green while the panel
shows an un-pinned literal set later in init order.

`grep -rn "setToolTipText" src/main/kotlin` returns zero hits today, so this is latent, not an
active bug. The defect is the stated guarantee — the same category of unearned claim as WR-02/03/04,
in the file whose job is to prevent exactly that drift.

**Fix:** Match the property and the setter, and count both:

```kotlin
private val TOOLTIP_ASSIGNMENT_PATTERN = Regex("""privacyMode\s*\.\s*(set)?[Tt]oolTipText""")

private fun assignmentLinesIn(file: File): List<String> =
    file.readLines()
        .filterNot { val t = it.trimStart(); t.startsWith("//") || t.startsWith("*") || t.startsWith("/*") }
        .filter { TOOLTIP_ASSIGNMENT_PATTERN.containsMatchIn(it) }
```

If the regex form is rejected, delete the "closes both gaps" sentence rather than leave it.

---

## Info

### IN-01: `MEASURED_INSERTION_POINT_TYPE_COUNT`'s stated rationale is the opposite of what the code does

**File:** `src/test/kotlin/com/six2dez/burp/aiagent/scanner/AiScanCheckDetailCookieCarrierTest.kt:830-837`

**Issue:** The KDoc says the count is "Held as a constant rather than a magic number so the two
assertions in [theInsertionPointTypeEnumPopulationIsTheOneTheResidualWasMeasuredAgainst] cannot
drift apart from each other." A hand-typed `17` next to a hand-typed 17-element `setOf(...)` is two
independent magic numbers; naming one of them does not couple them. Only deriving the count from
the set does.

The values themselves are correct — verified against `montoya-api-2026.2.jar` via `javap`: 17
members, names and order identical to the pinned set.

**Fix:** Derive it, which delivers the coupling the KDoc claims while keeping the distinct failure
message:

```kotlin
val MEASURED_INSERTION_POINT_TYPE_COUNT = MEASURED_INSERTION_POINT_TYPE_NAMES.size
```

Or reword the KDoc to "held separately so the count assertion can carry its own WHAT-TO-DO message;
it is deliberately redundant with the name-set assertion."

---

### IN-02: `ISSUE_DETAIL_CARRIER_DISPOSITION` grew ~4 KB and no assertion reads it

**File:** `src/test/kotlin/com/six2dez/burp/aiagent/redact/CookieCarrierInventoryTest.kt:709-853`

**Issue:** The constant is now ~11.2 KB of prose compiled into the test binary. Across all of
`src/`, it is only *named* — in KDoc at `:59`, `:70`, in assertion message text at `:489` and
`:582`, and in two `AiScanCheck.kt` KDoc references. The file's four `@Test` methods (`:104`,
`:128`, `:167`, `:195`) never evaluate it. It is documentation with zero verification living in a
`const val`, which is a strange home for one of the four record sites `D-28-10` conditions the
acceptance on: nothing detects if a future edit deletes clause (a) or (e).

**Fix:** Either give it the treatment `PrivacyModeTooltipBoundTest` gives the tooltip — a test that
pins the required clause markers as substrings, so the record site is defended the way the operator
site is:

```kotlin
@Test
fun theDispositionStillCarriesEveryClauseD2810ConditionedTheAcceptanceOn() {
    listOf("(a) THE WRITE-TIME/READ-TIME BOUND", "(b) THE DISPOSITION AND ITS AUTHORITY",
           "(e) THE ROUTE-2 GATE'S FAIL-OPEN SET", "(f) WHAT IS STILL OPEN")
        .forEach { assertTrue(ISSUE_DETAIL_CARRIER_DISPOSITION.contains(it), "missing clause: $it") }
}
```

— or move the narrative to `26-SECURITY.md` (already a required record site) and leave a pointer.

---

### IN-03: `AuditInsertionPointType.values()` called three times in one assertion pair

**File:** `src/test/kotlin/com/six2dez/burp/aiagent/scanner/AiScanCheckDetailCookieCarrierTest.kt:471`,
`:475`, `:478`

**Issue:** `values()` returns a defensively-copied array on each call; three calls in adjacent
lines, one of them inside a lazily-unused failure message. Harmless, but the message and the
assertion could in principle report different arrays.

**Fix:**

```kotlin
val all = AuditInsertionPointType.values()
val observedNames = all.map { it.name }.toSet()
assertEquals(MEASURED_INSERTION_POINT_TYPE_COUNT, all.size, "... it now has ${all.size}. ...")
```

---

## Verification Performed

| Check | Command | Result |
| --- | --- | --- |
| Comment-only claim for `AiScanCheck.kt` | `git diff 0d2f1fe..HEAD -- .../AiScanCheck.kt` | Confirmed — all added lines inside `/** */` |
| Enum population (17 members, names, order) | `javap -classpath montoya-api-2026.2.jar ...AuditInsertionPointType` | Matches pinned set exactly |
| `type()` default body | `javap -c ... AuditInsertionPoint` | `getstatic EXTENSION_PROVIDED; areturn` — KDoc correction is right |
| `BALANCED` default | `sed -n '488,498p' AgentSettings.kt` | Line 493, confirmed |
| "grep returned 0 at prior commit" | `git show 0d2f1fe:...CookieCarrierTest.kt \| grep -c "Payload Used"` | `0`, confirmed |
| Single tooltip assignment site | `grep -rn "privacyMode.toolTipText" src/main/kotlin` | 1 hit, references the constant |
| Main source file floor (150) | `find src/main/kotlin -name '*.kt' \| wc -l` | 172 |
| Read-time redaction pass exists | `McpToolContext.kt:58-74` | `Redaction.apply` on final tool output — contradicts WR-02's claim |
| No `<html>` / `ToolTipManager` idiom | `grep -rn 'toolTipText = "<html' \| "ToolTipManager"` | 0 hits each |
| Tests | `./gradlew test --tests '*PrivacyModeTooltipBoundTest*' --tests '*AiScanCheckDetailCookieCarrierTest*' --tests '*CookieCarrierInventoryTest*'` | BUILD SUCCESSFUL — 4/4 and 16/16 pass |
| Lint | `./gradlew ktlintCheck detekt` | BUILD SUCCESSFUL |
| Line length vs `.editorconfig` (250) | `awk '{print length}' \| sort -rn \| head -1` | max 242 |

---

_Reviewed: 2026-08-28T08:17:21Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
